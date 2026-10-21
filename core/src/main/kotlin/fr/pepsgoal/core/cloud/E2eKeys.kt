package fr.pepsgoal.core.cloud

import fr.pepsgoal.core.JSON
import fr.pepsgoal.core.crypto.E2e
import fr.pepsgoal.core.crypto.E2ePayload
import fr.pepsgoal.core.crypto.Nacl
import fr.pepsgoal.core.model.Message
import fr.pepsgoal.core.msOf
import fr.pepsgoal.core.nowMs
import fr.pepsgoal.core.uid
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
class Device(val id: String, val pk: String, val sk: String)

/** Clés de cet appareil pour un compte : appareil X25519, clés de conversation, clé du coffre. */
@Serializable
class KeyStore(var device: Device? = null, var pairs: MutableMap<String, String> = mutableMapOf(), var vault: String? = null, var vaulted: MutableMap<String, Int> = mutableMapOf())

/** Conversation : paire d'amis (`peer`) ou groupe de discussion (`group`). */
data class Conv(val peer: String? = null, val group: String? = null) {
    val base get() = if (group != null) "/v1/e2e/groups/$group" else "/v1/e2e/peers/$peer"
}

class ConvKey(val keyId: String, val key: String)
class OpenedConv(val current: ConvKey?, val waiting: Boolean)

/**
 * Chiffrement de bout en bout (docs/E2E.md), identique à la version web : enveloppes `crypto_box` par appareil,
 * coffre de clés chiffré avec une clé dérivée du mot de passe, re-chiffrement automatique pour les nouveaux appareils.
 */
class E2eKeys(private val c: Cloud) {
    private val api get() = c.api
    /** Messages déchiffrés (identifiant du message → texte ; null : clé absente pour l'instant). */
    val cache = HashMap<String, String?>()
    private val keyTried = HashMap<String, Long>()
    /** Coffre créé avec un ancien mot de passe : la messagerie demande l'ancien mot de passe. */
    var vaultIssue: String? = null

    private fun all(): MutableMap<String, KeyStore> = c.secrets.get(DEV_KEY)?.let { try { JSON.decodeFromString<MutableMap<String, KeyStore>>(it) } catch (e: Exception) { null } } ?: mutableMapOf()
    fun store(uid: String = c.meId()): KeyStore = all()[uid] ?: KeyStore()
    private fun saveRaw(k: KeyStore, uid: String = c.meId()) { val a = all(); a[uid] = k; c.secrets.set(DEV_KEY, JSON.encodeToString(a)) }
    private fun save(k: KeyStore) { saveRaw(k); c.scope.launch { try { vaultPush() } catch (e: Exception) {} } }
    fun forgetLocal() { val a = all(); a.remove(c.meId()); c.secrets.set(DEV_KEY, JSON.encodeToString(a)) }
    fun hasVault() = store().vault != null
    fun keyCount() = store().pairs.size

    /* ---------------------------------------------------------------- coffre */
    private fun vaultEntries(k: KeyStore, key: ByteArray, onlyNew: Boolean) = k.pairs.filter { Mapping.UUID_RE.matches(it.key) && (!onlyNew || k.vaulted[it.key] == null) }
        .map { (keyId, v) -> val s = E2e.seal(E2e.unb64(v), key); buildJsonObject { put("keyId", keyId); put("nonce", s.nonce); put("box", s.box) } }

    private fun vaultImport(k: KeyStore, key: ByteArray, list: List<JsonObject>): Int {
        var n = 0
        for (x in list) {
            val id = x.str("keyId")
            if (k.pairs[id] != null) { k.vaulted[id] = 1; continue }
            val out = E2e.open(x.str("nonce"), x.str("box"), key)
            if (out != null && out.size == 32) { k.pairs[id] = E2e.b64(out); k.vaulted[id] = 1; n++ }
        }
        return n
    }

    private fun openVault(v: JsonObject, password: String): ByteArray? {
        val derived = E2e.deriveVault(password, v.str("salt"), v.int("iterations", E2e.VAULT_ITER))
        if (!E2e.checkOk(derived, v.strN("check"))) return null
        val wrapped = v.strN("wrappedKey") ?: return derived
        return E2e.unpack(wrapped, derived)?.takeIf { it.size == 32 }
    }

    private fun kcvOk(vk: ByteArray, v: JsonObject) = E2e.checkOk(vk, v.strN("kcv") ?: if (v.strN("wrappedKey") == null) v.strN("check") else null)

    suspend fun vaultCreate(password: String, reset: Boolean): Boolean {
        val k = store(); val vk = Nacl.randomBytes(32); val salt = E2e.b64(Nacl.randomBytes(16)); val derived = E2e.deriveVault(password, salt, E2e.VAULT_ITER)
        val keys = vaultEntries(k, vk, false)
        try {
            api.put("/v1/e2e/vault", buildJsonObject {
                put("mode", if (reset) "reset" else "create"); put("salt", salt); put("iterations", E2e.VAULT_ITER); put("check", E2e.checkOf(derived))
                put("wrappedKey", E2e.pack(vk, derived)); put("kcv", E2e.checkOf(vk)); put("keys", JsonArray(keys))
            })
        } catch (e: ApiError) { if (e.status == 409 && !reset) return false; throw e }
        val k2 = store(); k2.vault = E2e.b64(vk); k2.vaulted = keys.associate { it.str("keyId") to 1 }.toMutableMap(); saveRaw(k2)
        vaultIssue = null
        return true
    }

    /** Connexion (mot de passe connu) : ouvre le coffre et récupère toutes les clés, ou le crée. */
    suspend fun vaultUnlock(password: String): String {
        repeat(2) {
            val r = api.get("/v1/e2e/vault")
            val v = r.obj("vault")
            if (v == null) { if (vaultCreate(password, false)) return "created"; return@repeat }
            val vk = openVault(v, password)
            if (vk == null) { vaultIssue = "password"; return "locked" }
            val k = store(); k.vault = E2e.b64(vk); vaultImport(k, vk, r.arr("keys")); saveRaw(k)
            vaultIssue = null
            vaultPush()
            return "opened"
        }
        return "locked"
    }

    suspend fun vaultPull(): Int {
        val k = store(); val vkS = k.vault ?: return 0
        val r = api.get("/v1/e2e/vault"); val vk = E2e.unb64(vkS); val v = r.obj("vault")
        if (v == null || !kcvOk(vk, v)) { k.vault = null; k.vaulted.clear(); saveRaw(k); return 0 }
        val n = vaultImport(k, vk, r.arr("keys")); saveRaw(k); return n
    }

    suspend fun vaultPush() {
        val k = store(); if (!c.store.acct) return; val vkS = k.vault ?: return
        val keys = vaultEntries(k, E2e.unb64(vkS), true); if (keys.isEmpty()) return
        for (i in keys.indices step 500) api.post("/v1/e2e/vault/keys", buildJsonObject { put("keys", JsonArray(keys.subList(i, minOf(keys.size, i + 500)))) })
        val k2 = store(); keys.forEach { k2.vaulted[it.str("keyId")] = 1 }; saveRaw(k2)
    }

    /** Nouveau mot de passe : seule la clé du coffre est rechiffrée. */
    suspend fun vaultRewrap(oldPassword: String, newPassword: String) {
        if (store().vault == null) try { vaultUnlock(oldPassword) } catch (e: Exception) {}
        val k = store()
        val vkS = k.vault ?: run { try { vaultUnlock(newPassword) } catch (e: Exception) {}; return }
        val vk = E2e.unb64(vkS); val salt = E2e.b64(Nacl.randomBytes(16)); val derived = E2e.deriveVault(newPassword, salt, E2e.VAULT_ITER)
        api.put("/v1/e2e/vault", buildJsonObject { put("mode", "rewrap"); put("salt", salt); put("iterations", E2e.VAULT_ITER); put("check", E2e.checkOf(derived)); put("wrappedKey", E2e.pack(vk, derived)); put("kcv", E2e.checkOf(vk)) })
    }

    /** Déverrouillage depuis la messagerie : vérifie le mot de passe auprès du serveur, puis ouvre (ou crée) le coffre. */
    suspend fun unlockWithPassword(password: String, reset: Boolean = false): String {
        if (vaultIssue != "password" || reset) {
            val me = c.pg.me() ?: return "locked"
            val r = api.post("/v1/auth/login", buildJsonObject { put("username", me.username); put("password", password) })
            val old = api.tokens?.refreshToken
            if (old != null) try { api.post("/v1/auth/logout", buildJsonObject { put("refreshToken", old) }) } catch (e: Exception) {}
            api.tokens = Tokens(r.str("accessToken"), r.str("refreshToken"))
        }
        if (reset) { vaultCreate(password, true); decryptAll(); return "created" }
        val res = vaultUnlock(password)
        if (res != "locked") decryptAll()
        return res
    }

    /* ---------------------------------------------------------------- appareils et enveloppes */
    suspend fun device(force: Boolean = false): Device {
        val k = store()
        if (k.device != null && !force) return k.device!!
        val kp = Nacl.boxKeyPair()
        val r = api.post("/v1/e2e/devices", buildJsonObject { put("publicKey", E2e.b64(kp.publicKey)) })
        val k2 = store(); k2.device = Device(r.str("deviceId"), E2e.b64(kp.publicKey), E2e.b64(kp.secretKey)); save(k2)
        return k2.device!!
    }

    private suspend fun <T> withDevice(fn: suspend (Device) -> T): T = try { fn(device()) } catch (e: ApiError) { if (e.code != "unknown_device") throw e; fn(device(true)) }

    private suspend fun uploadEnvelopes(conv: Conv, keyId: String, key: String, dev: Device, targets: List<Pair<String, String>>) {
        if (targets.isEmpty()) return
        api.post("${conv.base}/keys", buildJsonObject {
            put("keyId", keyId)
            put("envelopes", JsonArray(targets.map { (id, pk) -> val s = E2e.wrapKey(key, pk, dev.sk); buildJsonObject { put("deviceId", id); put("senderDeviceId", dev.id); put("nonce", s.nonce); put("box", s.box) } }))
        })
    }

    suspend fun openConv(conv: Conv, forWriting: Boolean = false): OpenedConv = withDevice { dev ->
        var st = api.get("${conv.base}?deviceId=${dev.id}")
        val k = store()
        for (x in st.arr("keys")) { val env = x.obj("envelope") ?: continue; val id = x.str("keyId"); if (k.pairs[id] == null) E2e.unwrapKey(env.str("nonce"), env.str("box"), env.str("senderPublicKey"), dev.sk)?.let { k.pairs[id] = it } }
        val keys = st.arr("keys")
        val usable = if (conv.group != null) keys.firstOrNull()?.let { k.pairs[it.str("keyId")] != null } ?: false else keys.any { k.pairs[it.str("keyId")] != null }
        if ((if (conv.group != null) st.bool("rotate") else keys.isEmpty()) || (forWriting && !usable)) {
            val keyId = uid(); val key = E2e.newPairKey()
            uploadEnvelopes(conv, keyId, key, dev, st.arr("devices").map { it.str("id") to it.str("publicKey") })
            k.pairs[keyId] = key
            st = api.get("${conv.base}?deviceId=${dev.id}")
        }
        save(k)
        val byId = st.arr("devices").associate { it.str("id") to it.str("publicKey") }
        var current: ConvKey? = null
        val keys2 = st.arr("keys")
        for ((i, x) in keys2.withIndex()) {
            val key = k.pairs[x.str("keyId")] ?: continue
            if (current == null && (conv.group == null || i == 0)) current = ConvKey(x.str("keyId"), key)
            val missing = x.strs("missingDevices").mapNotNull { id -> byId[id]?.let { id to it } }
            try { uploadEnvelopes(conv, x.str("keyId"), key, dev, missing) } catch (e: ApiError) {}
        }
        OpenedConv(current, current == null && keys2.isNotEmpty())
    }

    /** Re-chiffre pour les appareils (les miens et ceux de mes amis) les clés qu'ils n'ont pas encore. */
    suspend fun shareMissing() {
        if (store().device == null) return
        withDevice { dev ->
            val pend = api.get("/v1/e2e/pending?deviceId=${dev.id}").list(); val k = store()
            for (p in pend) {
                val env = p.obj("envelope")
                val key = k.pairs[p.str("keyId")] ?: env?.let { E2e.unwrapKey(it.str("nonce"), it.str("box"), it.str("senderPublicKey"), dev.sk) } ?: continue
                k.pairs[p.str("keyId")] = key
                val conv = p.strN("groupId")?.let { Conv(group = it) } ?: Conv(peer = p.str("peerId"))
                try { uploadEnvelopes(conv, p.str("keyId"), key, dev, p.arr("missingDevices").map { it.str("id") to it.str("publicKey") }) } catch (e: ApiError) {}
            }
            save(k)
        }
    }

    /** Code de sécurité de la conversation (clés publiques de tous les appareils des participants). */
    suspend fun safetyCode(conv: Conv): String = withDevice { dev -> E2e.safetyCode(api.get("${conv.base}?deviceId=${dev.id}").arr("devices").map { it.str("publicKey") }) }

    fun decryptLocal(m: Message): String? {
        val p = m.e2e ?: return m.text
        val key = store().pairs[p.keyId] ?: return null
        return E2e.decrypt(p, key)
    }

    suspend fun decryptAll() {
        val k = store(); val mine = c.meId(); val convs = LinkedHashMap<String, Conv>()
        for (m in c.db.messages) {
            val p = m.e2e ?: continue
            if (k.pairs[p.keyId] != null || nowMs() - (keyTried[p.keyId] ?: 0) < 20_000) continue
            keyTried[p.keyId] = nowMs()
            val conv = if (m.chat != null) Conv(group = m.chat) else Conv(peer = if (m.from == mine) m.to else m.from)
            convs[(conv.group ?: "") + (conv.peer ?: "")] = conv
        }
        if (convs.isNotEmpty()) try { vaultPull() } catch (e: Exception) {}
        for (conv in convs.values) try { openConv(conv) } catch (e: Exception) {}
        for (m in c.db.messages) if (m.e2e != null && cache[m.id] == null) cache[m.id] = decryptLocal(m)
    }

    /* ---------------------------------------------------------------- envoi */
    /**
     * Chiffre et envoie un message (texte ou objet JSON). Une pièce jointe (photo, vocal) est chiffrée avec une clé
     * aléatoire transmise dans le message chiffré, puis téléversée sur /v1/attachments.
     */
    suspend fun deliver(chat: String?, to: String?, body: JsonElement, blob: ByteArray? = null, mime: String? = null, retried: Boolean = false): Message? {
        val conv = openConv(if (chat != null) Conv(group = chat) else Conv(peer = to), true)
        val cur = conv.current ?: run {
            c.pg.hooks.toast(if (chat != null) "La clé du groupe arrive dès qu’un membre ouvre PepsGoal. Réessaie dans un instant." else "La clé de cette conversation arrive dès que ton ami ouvre PepsGoal. Réessaie dans un instant.", "lock")
            return null
        }
        var b = body
        val attachments = mutableListOf<String>()
        if (blob != null && b is JsonObject && b.obj("a")?.strN("id") == null) {
            val key = Nacl.randomBytes(32); val n = Nacl.randomBytes(24)
            val up = api.upload("/v1/attachments", Nacl.secretbox(blob, n, key), "piece-jointe.bin", "application/octet-stream")
            val a = LinkedHashMap(b.obj("a") ?: JsonObject(emptyMap()))
            a["alg"] = JsonPrimitive("nacl"); a["id"] = JsonPrimitive(up.str("id")); a["k"] = JsonPrimitive(E2e.b64(key)); a["n"] = JsonPrimitive(E2e.b64(n)); a["mime"] = JsonPrimitive(mime ?: "application/octet-stream")
            b = JsonObject(b + ("a" to JsonObject(a)))
        }
        (b as? JsonObject)?.obj("a")?.strN("id")?.let { attachments.add(it) }
        val raw = if (b is JsonPrimitive) b.content else Msg.pack(b as JsonObject)
        val p = E2e.encrypt(raw, cur.key, cur.keyId)
        val kind = (b as? JsonObject)?.str("k")
        val silent = kind in Msg.SILENT
        val mentions = if (chat != null) (b as? JsonObject)?.strs("mn") ?: emptyList() else emptyList()
        return try {
            val r = api.post(if (chat != null) "/v1/groups/$chat/messages" else "/v1/conversations/$to", buildJsonObject {
                put("e2e", JSON.encodeToJsonElement(E2ePayload.serializer(), p)); put("attachments", JsonArray(attachments.map(::JsonPrimitive))); put("silent", silent); put("mentions", JsonArray(mentions.map(::JsonPrimitive)))
            })
            val msg = Message(r.str("id"), c.meId(), to, chat, msOf(r.strN("createdAt")) ?: nowMs(), true, if (chat != null) mutableListOf(c.meId()) else mutableListOf(), p)
            cache[msg.id] = raw
            c.db.messages.add(msg); c.store.rawSave()
            msg
        } catch (e: ApiError) {
            if (e.code == "key_rotation" && !retried) deliver(chat, to, b, blob, mime, true) else throw e
        }
    }

    /** Pièce jointe déchiffrée d'un message. */
    suspend fun attachment(a: JsonObject): ByteArray? {
        if (a.str("alg") != "nacl") return null
        return Nacl.secretboxOpen(api.bytes("/v1/attachments/${a.str("id")}"), E2e.unb64(a.str("n")), E2e.unb64(a.str("k")))
    }

    companion object { const val DEV_KEY = "pepsgoal.e2e.v1" }
}

