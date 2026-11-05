package fr.pepsgoal.core.cloud

import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

fun JsonElement?.str(k: String, d: String = ""): String = ((this as? JsonObject)?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: d
fun JsonElement?.strN(k: String): String? = ((this as? JsonObject)?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
fun JsonElement?.int(k: String, d: Int = 0): Int = ((this as? JsonObject)?.get(k) as? JsonPrimitive)?.doubleOrNull?.toInt() ?: d
fun JsonElement?.intN(k: String): Int? = ((this as? JsonObject)?.get(k) as? JsonPrimitive)?.doubleOrNull?.toInt()
fun JsonElement?.bool(k: String): Boolean = ((this as? JsonObject)?.get(k) as? JsonPrimitive)?.booleanOrNull ?: false
fun JsonElement?.obj(k: String): JsonObject? = (this as? JsonObject)?.get(k) as? JsonObject
fun JsonElement?.arr(k: String): List<JsonObject> = ((this as? JsonObject)?.get(k) as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
fun JsonElement.list(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
fun JsonElement?.strs(k: String): List<String> = ((this as? JsonObject)?.get(k) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content } ?: emptyList()

/** Ce que la couche connectée signale à l'application. */
interface CloudEvents {
    fun onlineChanged(online: Boolean) {}
    fun sessionChanged() {}
    /** Nouvelles notifications reçues du serveur (pour les notifications système). */
    fun serverNotifications(fresh: List<Notif>) {}
    /** Nouveaux messages reçus (déchiffrés si possible) pour les notifications de conversation. */
    fun incomingMessages(fresh: List<Message>) {}
    /** Nouvelles publications d'amis apparues dans le feed depuis la dernière relève. */
    fun newPosts(fresh: List<Post>) {}
    fun error(message: String) {}
    /** Envoi terminé : [pushed] modifications locales arrivées sur le serveur. */
    fun synced(pushed: Int) {}
}

/**
 * Couche connectée (port de apps/web/src/cloud.js) : comptes, synchronisation hors ligne d'abord, fonctions sociales,
 * messagerie chiffrée de bout en bout et modération, reliées au serveur PepsGoal.
 */
class Cloud(val store: Store, val pg: Pg, val api: Api, val secrets: Secrets, val scope: CoroutineScope, var events: CloudEvents = object : CloudEvents {}) : CloudBridge {
    val db get() = store.db
    val e2e = E2eKeys(this)
    /** Horloge monotone en ms (validités, statut, tirages, palier du flux) : injectable dans les tests. */
    var clock: () -> Long = { System.nanoTime() / 1_000_000 }

    var navOnline = true
        set(v) { field = v; updateOnline() }
    var serverOk = true
        private set
    var forceOffline: Boolean
        get() = secrets.get(OFF_KEY) == "1"
        set(v) { secrets.set(OFF_KEY, if (v) "1" else null); api.forceOffline = v; updateOnline() }

    var syncing: Deferred<Unit>? = null
    var lastSync: Long? = null
    var syncError = ""
    private var pulledOnce = false
    private var failures = 0
    var maintenanceStatus: JsonObject? = null
    var minAppVersion: String? = null
    var suggestions: List<JsonObject> = emptyList()
    var stats: JsonObject? = null
    var statsError: String? = null
    var statsAt = 0L
    val profileLoaded = HashSet<String>()
    val viewed = HashSet<String>()
    private val socialLock = Mutex()
    private val pollLock = Mutex()
    private var lastPollSuccess: Long? = null
    private var pingEnabled = true
    private var lastStatusAt = clock()
    /** Niveau de réactivité « Rapide » : le flux d'amis est relevé à chaque cycle d'arrière-plan. */
    var feedEveryCycle = false
    /** Onglet Modération affiché : la modération suit les rechargements du premier plan. */
    var moderationVisible = false
    /** Échecs consécutifs de relève : la prochaine est espacée (voir [PollPolicy.backoffMs]). */
    var pollFailures = 0
        private set
    /** Dernier rechargement social complet réussi, dernière modération, dernier tirage de synchronisation (horloge [clock]). */
    private var lastSocialAt: Long? = null
    private var lastModAt: Long? = null
    private var lastPullAt: Long? = null
    /** Palier lent du flux d'amis en arrière-plan : cycles depuis le dernier relevé, et relevé forcé après une notification sociale. */
    private var feedCycles = 0
    private var lastFeedAt: Long? = null
    private var feedHint = false
    /** Empreinte des réponses du dernier rechargement complet et nombre d'enregistrements à ce moment (pas d'écriture inutile). */
    private var socialInputs: Int? = null
    private var savesAtSocial = -1L
    private var socialForcePending = false
    private var syncForcePending = false
    /** Minuteurs encore dans leur délai (remplaçables) ; une fois lancés, ils vont jusqu'au bout. */
    private var socialDelaying = false
    private var syncDelaying = false
    /** Relève allégée en arrière-plan depuis le dernier rechargement complet (persisté dans [CloudState]). */
    val socialStale get() = db.cloud?.socialStale == true
    private var syncTimer: Job? = null
    private var socialTimer: Job? = null
    private var pingJob: Job? = null
    private var bootKeysJob: Job? = null
    private var bootKeysPending = false

    init {
        api.forceOffline = forceOffline
        api.onServerDown = { serverDown() }
        api.onSessionLost = {
            val refusedToken = api.tokens?.refreshToken
            scope.launch { if (api.tokens?.refreshToken == refusedToken) sessionLost() }
        }
        pg.cloud = this
    }

    fun meId() = pg.meId()
    override val pulledBefore get() = pulledOnce || db.cloud?.pulled == true

    /* ------------------------------------------------------------------ réseau */
    private fun updateOnline() {
        val was = store.online
        store.online = navOnline && serverOk && !forceOffline
        if (was != store.online) {
            store.changed(); events.onlineChanged(store.online)
            if (store.online) onReconnect()
        }
    }
    private fun setServerOk(v: Boolean) { serverOk = v; updateOnline() }
    private fun serverDown() { if (serverOk) { setServerOk(false); failures = 0; if (pingEnabled) schedulePing(RETRY[0]) } }
    private fun onReconnect() { if (store.acct && pingEnabled) { scheduleSync(); scheduleSocial() } }

    private fun schedulePing(ms: Long) {
        pingJob?.cancel()
        if (!pingEnabled || !navOnline || forceOffline) { pingJob = null; return }
        pingJob = scope.launch {
            delay(ms + Random.nextLong(0, (ms / 5).coerceAtLeast(1)))
            ping() // ping() never cancels the job at entry; lifecycle can still cancel an in-flight probe
        }
    }

    /** Stop heartbeat work in the background; alarm/service/worker still probe when needed. */
    fun setForeground(visible: Boolean) {
        pingEnabled = visible
        if (!visible) { pingJob?.cancel(); pingJob = null; bootKeysJob?.cancel() }
        else if (bootKeysPending && store.acct && !forceOffline && navOnline) {
            bootKeysPending = false
            bootKeysJob = scope.launch {
                try { e2e.device(); e2e.shareMissing() }
                catch (e: CancellationException) { bootKeysPending = true; throw e }
                catch (e: Exception) { bootKeysPending = true }
            }
        }
    }

    suspend fun ping() {
        if (forceOffline || !navOnline) return
        try {
            val s = api.status()
            if (!s.bool("ok")) throw ApiError(503, "down", "")
            failures = 0; maintenanceStatus = s.obj("maintenance"); minAppVersion = s.strN("minAppVersion"); applyMaintenanceStatus()
            lastStatusAt = clock()
            setServerOk(true)
            // Healthy status refreshes piggyback on poll(); only outages have a retry timer.
        } catch (e: ApiError) {
            setServerOk(false)
            if (pingEnabled) schedulePing(RETRY[minOf(failures++, RETRY.size - 1)])
        }
    }
    fun pingSoon() {
        if (!pingEnabled || !navOnline || forceOffline) return
        failures = 0; schedulePing(0)
    }

    /* ------------------------------------------------------------------ session */
    private fun cloudState(): CloudState { if (db.cloud == null) db.cloud = CloudState(); return db.cloud!! }

    fun upsertUser(p: JsonObject?, apply: (User) -> Unit = {}): User? {
        if (p == null) return null
        val id = p.str("id"); if (id.isEmpty()) return null
        var u = pg.userById(id)
        if (u == null) {
            val hue = p.obj("avatar").int("color")
            u = User(id, p.str("username"), p.str("displayName"), hue, createdAt = nowMs()); db.users.add(u)
        }
        u.username = p.str("username", u.username)
        val real = p.str("displayName", u.realName ?: u.name); val nick = if (id == pg.meId()) null else pg.nickname(id)
        u.realName = if (nick != null) real else null; u.name = nick ?: real
        u.avatar = Mapping.mapAvatar(p.obj("avatar")); u.srv = true; u.stub = false
        val role = p.str("role"); u.role = if (role == "admin" || role == "official") role else null
        if (role == "official") { u.tutorialDone = true; u.avatar = Avatar("picto", 0, "", "flameo", "") }
        apply(u)
        return u
    }

    fun ensureUserStub(id: String?) {
        if (id.isNullOrEmpty() || pg.userById(id) != null) return
        db.users.add(User(id, "membre", "Membre PepsGoal", stub = true))
    }

    private fun newAccountDb(userId: String) = Db(session = userId, cloud = CloudState())

    private fun enterAccount(payload: JsonObject, newDb: Db) {
        api.tokens = Tokens(payload.str("accessToken"), payload.str("refreshToken"))
        val user = payload.obj("user")!!
        api.account = user.str("id")
        resetPollState()
        openConversationSummary.clear(); openConversationFullAt.clear()
        store.use(newDb, true)
        val u = upsertUser(user) { it.bio = user.str("bio"); it.freezes = user.int("freezes") }!!
        Mapping.applyServerSettings(u, user.obj("settings"))
        db.users.removeAll { it.role == "official" && !it.srv }
        cloudState(); db.session = u.id; pg.applyNicknames()
        store.rawSave(); events.sessionChanged()
    }

    /** Démarrage : reprend la session enregistrée sur l'appareil. */
    fun boot() {
        api.forceOffline = forceOffline
        val cached = store.readAccount()
        if (api.tokens != null && cached?.session != null) { store.use(cached, true); cloudState(); api.account = cached.session }
        else store.use(store.readLocal() ?: Db(), false)
        bootKeysPending = store.acct && !pingEnabled
        // An Application can be created solely for an offline/local worker. Do not start network work then.
        if (pingEnabled) scope.launch {
            ping()
            if (store.acct) {
                launch { try { e2e.device(); e2e.shareMissing() } catch (e: CancellationException) { throw e } catch (e: Exception) {} }
                syncNow(); guardedSocial { refreshSocial(emptyMap(), false) }
            }
        }
    }

    suspend fun login(username: String, password: String) {
        val r = api.post("/v1/auth/login", buildJsonObject { put("username", username.trim().lowercase()); put("password", password) }).jsonObject
        connect(r, false, password)
    }

    suspend fun register(username: String, name: String, password: String) {
        val r = api.post("/v1/auth/register", buildJsonObject { put("username", username); put("displayName", name); put("password", password); put("acceptTerms", true) }).jsonObject
        connect(r, true, password)
    }

    private fun connect(payload: JsonObject, created: Boolean, password: String) {
        val uidNew = payload.obj("user").str("id")
        val cached = store.readAccount()
        enterAccount(payload, if (cached?.session == uidNew) cached else newAccountDb(uidNew))
        if (created) pg.hooks.toast("Bienvenue sur PepsGoal !", "flame")
        afterConnect(password)
    }

    /** Le profil sans compte devient un compte : objectifs, validations, groupes et réglages sont conservés puis envoyés. */
    suspend fun upgradeGuest(username: String, name: String, password: String) {
        val guest = pg.me() ?: return; val guestDb = db
        val payload = api.post("/v1/auth/register", buildJsonObject { put("username", username); put("displayName", name); put("password", password); put("acceptTerms", true) }).jsonObject
        val nid = payload.obj("user").str("id"); val gid = guest.id
        val nd = newAccountDb(nid)
        nd.goals = guestDb.goals.filter { it.userId == gid }.onEach { it.userId = nid; it.members = it.members?.map { m -> if (m == gid) nid else m }?.toMutableList() }.toMutableList()
        nd.checkins = guestDb.checkins.filter { it.userId == gid }.onEach { it.userId = nid }.toMutableList()
        enterAccount(payload, nd)
        val nu = pg.me()!!
        nu.goalGroups = guest.goalGroups; nu.goalGroupOf = guest.goalGroupOf; nu.tutorialDone = guest.tutorialDone; nu.frozenDays = guest.frozenDays; nu.milestones = guest.milestones
        nu.favGoals = guest.favGoals; nu.perfectDays = guest.perfectDays; nu.badges = guest.badges; nu.badgeFams = guest.badgeFams
        nu.settings = guest.settings.also { it.social = true }
        guestDb.users.removeAll { it.id == gid }; guestDb.goals.removeAll { it.userId == nid }; guestDb.checkins.removeAll { it.userId == nid }; guestDb.session = null
        store.writeLocal(guestDb)
        store.commit()
        pg.hooks.toast("Compte créé : toutes tes données sont conservées et sauvegardées.", "check")
        afterConnect(password)
    }

    private fun afterConnect(password: String?) {
        scope.launch {
            try { if (password != null) e2e.vaultUnlock(password) } catch (e: Exception) {}
            try { e2e.device(); e2e.shareMissing(); e2e.decryptAll() } catch (e: Exception) {}
            store.changed()
        }
        scope.launch {
            syncNow()
            pg.checkReminders()
            refreshSocial()
        }
    }

    /** Déconnexion : refusée tant que le serveur n'a pas confirmé la synchronisation. */
    suspend fun logout(): Boolean {
        if (!store.online) { pg.hooks.toast("Hors ligne : la déconnexion et le changement de compte reviendront avec la connexion.", "cloudoff"); return false }
        if (pg.runningTimer() != null) pg.stopTimer(true)
        syncNow(force = true)
        if (!store.online || syncError.isNotEmpty()) { pg.hooks.toast("Hors ligne : la déconnexion et le changement de compte reviendront avec la connexion.", "cloudoff"); return false }
        if (collectOps().first.isNotEmpty()) { pg.hooks.toast("Des modifications ne sont pas encore synchronisées : réessaie dans un instant.", "cloudoff"); return false }
        leaveAccount(true)
        return true
    }

    fun leaveAccount(callServer: Boolean) {
        resetPollState(); api.account = null
        openConversationSummary.clear(); openConversationFullAt.clear()
        val rt = api.tokens?.refreshToken
        if (callServer) {
            val dev = e2e.store().device
            scope.launch {
                if (rt != null) try { api.post("/v1/auth/logout", buildJsonObject { put("refreshToken", rt) }) } catch (e: Exception) {}
                if (dev != null) try { api.delete("/v1/e2e/devices/${dev.id}") } catch (e: Exception) {}
            }
        }
        e2e.forgetLocal()
        api.tokens = null; store.deleteAccount()
        bootKeysJob?.cancel(); bootKeysPending = false
        e2e.cache.clear(); profileLoaded.clear(); pulledOnce = false
        store.use(store.readLocal() ?: Db(), false); db.session = null
        events.sessionChanged()
    }

    private suspend fun sessionLost() {
        if (!store.acct) return
        pg.hooks.toast("Ta session a expiré : reconnecte-toi.", "lock")
        leaveAccount(false)
    }

    /** Suppression définitive du compte (mot de passe requis). */
    suspend fun deleteAccount(password: String) {
        api.delete("/v1/me", buildJsonObject { put("password", password) })
        e2e.forgetLocal(); pg.hooks.toast("Ton compte a été supprimé.", "trash"); leaveAccount(false)
    }

    /* ------------------------------------------------------------------ synchronisation */
    private fun normalizeIds(): Boolean {
        val mine = meId(); var changed = false; val gmap = HashMap<String, String>()
        for (g in db.goals) if (g.userId == mine && !Mapping.UUID_RE.matches(g.id)) gmap[g.id] = uid()
        if (gmap.isNotEmpty()) {
            changed = true; val u = pg.me()
            db.goals.forEach { g -> gmap[g.id]?.let { g.id = it } }
            db.checkins.forEach { c -> gmap[c.goalId]?.let { c.goalId = it } }
            db.posts.forEach { p -> p.goalId?.let { gmap[it] }?.let { p.goalId = it } }
            u?.let { uu -> uu.goalGroupOf = uu.goalGroupOf.mapKeys { gmap[it.key] ?: it.key }.toMutableMap(); uu.favGoals = uu.favGoals.map { gmap[it] ?: it }.toMutableList() }
            db.timers[mine]?.let { t -> gmap[t.goalId]?.let { t.goalId = it } }
        }
        for (c in db.checkins) if (c.userId == mine && !Mapping.UUID_RE.matches(c.id)) { c.id = uid(); changed = true }
        return changed
    }

    private fun stableGoal(g: Goal) = Mapping.toServerGoal(g).toString()
    private fun stableCheckin(c: Checkin) = Mapping.toServerCheckin(c).toString()

    private class Mark(val kind: String, val id: String?, val value: String?)

    private fun collectOps(): Pair<List<JsonObject>, List<Mark>> {
        val mine = meId(); val cs = cloudState(); val now = nowIso()
        val ops = mutableListOf<Pair<JsonObject, Mark>>()
        fun op(entity: String, action: String, id: String, data: JsonObject?) = buildJsonObject {
            put("opId", uid()); put("entity", entity); put("action", action); put("id", id)
            if (data != null) put("data", JsonObject(data + ("updatedAt" to JsonPrimitive(now))))
            put("clientUpdatedAt", now)
        }
        val own = db.goals.filter { it.userId == mine }; val ownIds = own.map { it.id }.toSet()
        for (g in own) { val s = stableGoal(g); if (cs.snapGoals[g.id] != s) ops.add(op("goal", "upsert", g.id, JSON.parseToJsonElement(s).jsonObject) to Mark("goals", g.id, s)) }
        for (id in cs.snapGoals.keys.toList()) if (id !in ownIds && db.goals.none { it.id == id && it.userId != mine }) ops.add(op("goal", "delete", id, null) to Mark("goals", id, null))
        val goalIds = db.goals.map { it.id }.toSet()
        val ownC = db.checkins.filter { it.userId == mine && it.goalId in goalIds }; val ownCIds = ownC.map { it.id }.toSet()
        for (c in ownC) { val s = stableCheckin(c); if (cs.snapCheckins[c.id] != s) ops.add(op("checkin", "upsert", c.id, JSON.parseToJsonElement(s).jsonObject) to Mark("checkins", c.id, s)) }
        for (id in cs.snapCheckins.keys.toList()) if (id !in ownCIds) ops.add(op("checkin", "delete", id, null) to Mark("checkins", id, null))
        pg.me()?.let { u -> val s = Mapping.toServerSettings(u).toString(); if (cs.snapSettings != s) ops.add(op("settings", "upsert", "settings", JSON.parseToJsonElement(s).jsonObject) to Mark("settings", null, s)) }
        val order = mapOf("goal" to 0, "settings" to 1, "checkin" to 2)
        val sorted = ops.sortedWith(compareBy({ order[it.first.str("entity")] }, { if (it.first.str("action") == "delete") 1 else 0 }))
        return sorted.map { it.first } to sorted.map { it.second }
    }
    fun pendingCount() = try { if (store.acct) collectOps().first.size else 0 } catch (e: Exception) { 0 }

    /** Modification locale pas encore envoyée : [kind] goal / checkin / settings, [action] new / edit / delete. */
    class Pending(val kind: String, val action: String, val label: String, val sub: String)

    /** Ce qui partira au prochain envoi, en clair (page « Modifications en attente »). */
    fun pendingList(): List<Pending> = try {
        if (!store.acct) emptyList() else {
            val cs = cloudState()
            fun snapField(json: String?, k: String) = try { json?.let { (JSON.parseToJsonElement(it).jsonObject[k] as? JsonPrimitive)?.contentOrNull } } catch (e: Exception) { null }
            collectOps().first.map { o ->
                val id = o.str("id"); val del = o.str("action") == "delete"
                when (o.str("entity")) {
                    "goal" -> {
                        val g = db.goals.firstOrNull { it.id == id }
                        val title = g?.title ?: snapField(cs.snapGoals[id], "title") ?: "Objectif"
                        Pending("goal", if (del) "delete" else if (cs.snapGoals[id] == null) "new" else "edit", title,
                            if (del) "Objectif supprimé" else if (cs.snapGoals[id] == null) "Nouvel objectif" else "Objectif modifié")
                    }
                    "checkin" -> {
                        val c = db.checkins.firstOrNull { it.id == id }
                        val gid = c?.goalId ?: snapField(cs.snapCheckins[id], "goalId"); val date = c?.date ?: snapField(cs.snapCheckins[id], "date") ?: ""
                        val title = db.goals.firstOrNull { it.id == gid }?.title ?: "Objectif"
                        Pending("checkin", if (del) "delete" else if (cs.snapCheckins[id] == null) "new" else "edit", title,
                            (if (del) "Validation annulée" else if (cs.snapCheckins[id] == null) "Validation" else "Validation modifiée") + if (date.isNotEmpty()) " · ${fmtDate(date)}" else "")
                    }
                    else -> Pending("settings", "edit", "Réglages", "Thème, rappels et préférences")
                }
            }
        }
    } catch (e: Exception) { emptyList() }

    private fun markSynced(m: Mark) {
        val cs = cloudState()
        when (m.kind) {
            "settings" -> cs.snapSettings = m.value ?: ""
            "goals" -> if (m.value == null) cs.snapGoals.remove(m.id) else cs.snapGoals[m.id!!] = m.value
            else -> if (m.value == null) cs.snapCheckins.remove(m.id) else cs.snapCheckins[m.id!!] = m.value
        }
    }

    /** Mémoires de relève liées au compte : vidées au changement de compte. */
    private fun resetPollState() {
        lastPollSuccess = null; lastSocialAt = null; lastModAt = null; lastPullAt = null
        lastFeedAt = null; feedCycles = 0; feedHint = false; socialInputs = null; savesAtSocial = -1; pollFailures = 0
    }

    /** [force] : tirage même si rien n'est à envoyer et que le dernier tirage est récent (ouverture, demande explicite). */
    fun scheduleSync(ms: Long = 1500, force: Boolean = false) {
        if (!store.acct) return
        syncForcePending = syncForcePending || force
        if (syncDelaying) syncTimer?.cancel()
        syncDelaying = true
        syncTimer = scope.launch { delay(ms); syncDelaying = false; val f = syncForcePending; syncForcePending = false; syncNow(f) }
    }

    /** Fenêtre pendant laquelle un tirage récent suffit : 2 min au premier plan, 30 min en arrière-plan. */
    private fun pullWindowMs() = if (pingEnabled) 2 * 60_000L else 30 * 60_000L

    /** Une synchronisation est-elle utile ? Modifications locales en attente, ou dernier tirage plus ancien que la fenêtre. */
    fun syncDue(): Boolean {
        if (!store.acct) return false
        val at = lastPullAt ?: return true
        if (clock() - at >= pullWindowMs()) return true
        return try { collectOps().first.isNotEmpty() } catch (e: Exception) { true }
    }

    suspend fun syncNow(force: Boolean = false) {
        if (!store.acct || !store.online) return
        // Rien à envoyer et tirage récent : aucune requête (les écritures locales sans donnée synchronisée n'en coûtent plus)
        if (!force && syncing?.isActive != true && !syncDue()) return
        syncing?.let { if (it.isActive) { it.await(); return } }
        coroutineScope {
            val d = async {
                try {
                    if (normalizeIds()) store.rawSave()
                    val (ops, marks) = collectOps()
                    for (i in ops.indices step 100) {
                        val batch = ops.subList(i, minOf(ops.size, i + 100)); val bm = marks.subList(i, minOf(marks.size, i + 100))
                        api.post("/v1/sync/push", buildJsonObject { put("deviceId", deviceId()); put("ops", JsonArray(batch)) })
                        // Opération refusée (version plus récente ailleurs, données invalides) : la version du serveur fera foi au prochain tirage
                        bm.forEach { markSynced(it) }
                        store.rawSave()
                    }
                    pull()
                    lastSync = nowMs(); syncError = ""
                    if (ops.isNotEmpty()) events.synced(ops.size)
                } catch (e: ApiError) { syncError = e.message ?: "Erreur de synchronisation" }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { syncError = e.message ?: "Erreur de synchronisation" }
                finally { store.changed() }
            }
            syncing = d
            try { d.await() } finally { if (syncing === d) syncing = null }
        }
    }

    private suspend fun pull() {
        val cs = cloudState(); var changed = false
        for (guard in 0 until 50) {
            val before = cs.cursor
            val r = api.get("/v1/sync/pull?cursor=${cs.cursor}")
            val mine = meId()
            for (sg in r.arr("goals")) {
                val g = Mapping.fromServerGoal(sg)
                ensureUserStub(g.userId); g.members?.forEach { ensureUserStub(it) }; ensureUserStub(g.buddy)
                val i = db.goals.indexOfFirst { it.id == g.id }
                if (i >= 0) db.goals[i] = g else db.goals.add(g)
                if (g.userId == mine) cs.snapGoals[g.id] = stableGoal(g)
                changed = true
            }
            val ck = db.checkins.toMutableList()
            for (sc in r.arr("checkins")) {
                val c = Mapping.fromServerCheckin(sc); ensureUserStub(c.userId)
                val i = ck.indexOfFirst { it.id == c.id }
                if (i >= 0) ck[i] = c else ck.add(c)
                if (c.userId == mine) cs.snapCheckins[c.id] = stableCheckin(c)
                changed = true
            }
            db.checkins = ck
            for (d in r.arr("deleted")) {
                val id = d.str("id")
                if (d.str("entity") == "goal") { db.goals.removeAll { it.id == id }; db.checkins = db.checkins.filter { it.goalId != id }.toMutableList(); cs.snapGoals.remove(id); changed = true }
                if (d.str("entity") == "checkin") { db.checkins = db.checkins.filter { it.id != id }.toMutableList(); cs.snapCheckins.remove(id); changed = true }
            }
            val st = r.obj("settings")
            if (st != null) pg.me()?.let { Mapping.applyServerSettings(it, st); pg.applyNicknames(); cs.snapSettings = Mapping.toServerSettings(it).toString(); changed = true }
            cs.cursor = (r as JsonObject)["cursor"]?.jsonPrimitive?.longOrNull ?: cs.cursor
            if (!r.bool("hasMore")) break
            // Curseur bloqué alors que le serveur annonce une suite : on s'arrête au lieu d'émettre 50 requêtes
            if (cs.cursor == before) { syncError = ""; NetStats.count("sync.stuck"); break }
        }
        pulledOnce = true; cs.pulled = true; lastPullAt = clock()
        if (changed) store.rawSave()
    }

    fun deviceId(): String = secrets.get("pepsgoal.device.v1") ?: uid().also { secrets.set("pepsgoal.device.v1", it) }

    /* ------------------------------------------------------------------ fonctions sociales */
    /**
     * Rechargement social différé. Sans [force], il est ignoré si un rechargement complet a réussi il y a moins de
     * [maxAgeMs] (navigation entre onglets, retour au premier plan) ; les actions sociales et le retour après une relève
     * d'arrière-plan ([socialStale]) le forcent.
     */
    fun scheduleSocial(ms: Long = 400, force: Boolean = false, maxAgeMs: Long = SOCIAL_FRESH_MS) {
        if (!store.acct) return
        socialForcePending = socialForcePending || force
        // Seul un minuteur encore en attente est remplacé : un rechargement en cours n'est jamais interrompu
        if (socialDelaying) socialTimer?.cancel()
        socialDelaying = true
        socialTimer = scope.launch {
            delay(ms)
            socialDelaying = false
            val f = socialForcePending; socialForcePending = false
            guardedSocial { refreshSocial(emptyMap(), f, maxAgeMs) }
        }
    }

    /** Passage au premier plan : synchronisation et rechargement social, complet si une relève allégée a eu lieu entre-temps. */
    fun foregroundRefresh() {
        if (!store.acct) return
        scheduleSync(300, force = true)
        scheduleSocial(600, force = socialStale)
    }

    /** Rechargement social demandé explicitement (tirer pour rafraîchir, action sociale) : jamais ignoré. */
    suspend fun refreshSocial() = guardedSocial { refreshSocial(emptyMap(), true) }

    private suspend fun guardedSocial(block: suspend () -> Unit) {
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: ApiError) { pollStatus = "erreur serveur ${e.status}"; if (e.status != 0) events.error(e.message ?: "") }
        catch (e: Exception) { pollStatus = "erreur : ${e::class.simpleName}" }
    }

    private suspend fun refreshSocial(seed: Map<String, JsonElement>, force: Boolean, maxAgeMs: Long = SOCIAL_FRESH_MS) {
        if (!store.acct || !store.online) return
        // Structured concurrency: cancellation and the poll budget cover all refresh requests.
        socialLock.withLock {
            // Validité vérifiée sous le verrou : un rechargement en cours (démarrage à froid) sert aussi l'appel suivant
            val now = clock()
            val fresh = lastSocialAt?.let { now - it < maxAgeMs } == true
            val modDue = moderationVisible && pingEnabled && pg.isAdmin(pg.me()) && lastModAt?.let { now - it < maxAgeMs } != true
            if (!force && !socialStale && fresh && !modDue) { NetStats.count("social.skipped"); return@withLock }
            doRefreshSocial(seed, force)
        }
    }

    /** Résultat de la dernière relève (diagnostic affiché dans les réglages). */
    var pollStatus = ""

    /**
     * Shared exclusion and monotonic successful-window check for every caller.
     * [foreground] faux : relève allégée (les quatre réponses seules, sans rechargement complet ni modération), statut au
     * plus toutes les 10 min, flux d'amis au palier lent (un cycle sur trois, au moins 10 min d'écart).
     */
    suspend fun poll(minIntervalMs: Long = 40_000, foreground: Boolean = true): Boolean {
        if (!pollLock.tryLock()) return false
        try {
            val now = clock()
            if (lastPollSuccess?.let { now - it < minIntervalMs } == true) return false
            if (!store.acct) { pollStatus = "pas de compte"; return false }
            // Statut : 60 s au premier plan, 10 min en arrière-plan (les 502/504 signalent déjà une panne)
            if (store.online && now - lastStatusAt >= (if (foreground) 60_000L else STATUS_BG_MS)) ping()
            if (!store.online) { if (!navOnline) { pollStatus = "pas de réseau"; return false }; failures = 0; ping() }
            if (!store.online) { pollStatus = "serveur injoignable"; pollFailures++; return false }
            val ownerDb = db
            val previous = cloudState().pollDigests.takeIf { it.size == POLL_PATHS.size }
            val withFeed = foreground || feedEveryCycle || previous == null || feedHint ||
                lastFeedAt.let { it == null || (feedCycles >= 2 && now - it >= FEED_BG_MS) }
            val paths = if (withFeed) POLL_PATHS else POLL_PATHS.take(3)
            val values = coroutineScope {
                paths.map { path -> async {
                    try { api.get(path) } catch (e: ApiError) {
                        if (path == "/v1/groups" && e.status == 404) JsonArray(emptyList()) else throw e
                    }
                } }.map { it.await() }
            }
            if (db !== ownerDb || !store.acct) return false
            val digests = values.map { digest(it) } + if (withFeed) emptyList() else listOf(previous?.getOrNull(3) ?: "")
            if (withFeed) { feedCycles = 0; lastFeedAt = now; feedHint = false } else feedCycles++
            val changed = digests != previous
            if (changed) {
                val seed = paths.zip(values).toMap()
                if (!foreground) refreshSocialBackground(seed, previous, digests)
                else if (previous != null && digests[0] == previous[0] && digests[3] == previous[3]) refreshInbox(values, digests)
                else refreshSocial(seed, true)
            }
            if (db !== ownerDb || !store.acct) return false
            lastPollSuccess = clock(); pollFailures = 0
            pollStatus = if (changed) "nouveautés relevées" else "à jour"
            return changed
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { pollFailures++; pollStatus = "erreur : ${e.message ?: e::class.simpleName}"; return false
        } finally { pollLock.unlock() }
    }

    /**
     * Relève d'arrière-plan allégée : seules les quatre réponses déjà reçues sont exploitées (notifications, conversations
     * et groupes, publications d'amis fusionnées). Flux public, stories, profil, amis, blocages, suggestions et modération
     * attendent le retour au premier plan ([socialStale]).
     */
    private suspend fun refreshSocialBackground(seed: Map<String, JsonElement>, previous: List<String>?, digests: List<String>) = socialLock.withLock {
        val ownerDb = db; val mine = meId()
        fun moved(i: Int) = previous == null || previous[i] != digests[i]
        var dirty = false
        seed["/v1/notifications"]?.takeIf { moved(0) }?.let { n ->
            val fresh = applyNotifs(n)
            // Un encouragement, un commentaire ou une demande d'ami : le flux d'amis est relevé au cycle suivant
            if (fresh.any { it.type in FEED_HINTS }) feedHint = true
            dirty = true
        }
        if (moved(1) || moved(2)) {
            val known = db.messages.map { it.id }.toSet()
            applyConversations(seed.getValue("/v1/conversations"), seed.getValue("/v1/groups"))
            if (db !== ownerDb || !store.acct) return@withLock
            e2e.decryptAll()
            val fresh = db.messages.filter { it.id !in known && it.from != mine && !it.read && mine !in it.readBy }
            if (fresh.isNotEmpty() && db.cloud?.socialPulled == true) events.incomingMessages(fresh)
            dirty = true
        }
        seed["/v1/feed?tab=friends"]?.takeIf { moved(3) }?.let { f ->
            val oldPosts = db.posts.map { it.id }.toSet()
            val freshP = mergeFriendPosts(f)
            if (freshP.isNotEmpty() && (oldPosts.isNotEmpty() || db.cloud?.socialPulled == true) && pulledBefore) events.newPosts(freshP)
            dirty = true
        }
        if (db !== ownerDb || !store.acct) return@withLock
        val cs = cloudState(); cs.pollDigests = digests; cs.socialStale = true
        NetStats.count("social.light")
        if (dirty) store.rawSave()
    }

    /** Publications d'amis fusionnées dans le cache (les publications publiques et de modération restent) ; renvoie les fraîches. */
    private fun mergeFriendPosts(feed: JsonElement): List<Post> {
        val mine = meId()
        val incoming = feed.arr("posts").map { postFromServer(it) }
        val byId = incoming.associateBy { it.id }
        val oldIds = db.posts.map { it.id }.toSet()
        val added = incoming.filter { it.id !in oldIds }
        db.posts = (added + db.posts.map { byId[it.id] ?: it }).toMutableList()
        return added.filter { it.userId != mine && it.userId.isNotEmpty() && it.at > nowMs() - 86_400_000L }
    }

    /** Notifications du serveur appliquées (les notifications locales restent) ; renvoie les nouvelles non lues, déjà signalées. */
    private fun applyNotifs(notifs: JsonElement): List<Notif> {
        val mine = meId()
        val known = db.notifs.filter { it.srv }.map { it.id }.toSet()
        val srvN = notifs.list().map { n -> val d = n.obj("data")
            Notif(n.str("id"), mine, n.str("type"), n.str("text"), msOf(n.strN("createdAt")) ?: 0, n.bool("read"), true, NOTIF_VIEW[n.str("type")], d.strN("goalId"), d.strN("postId"), d.strN("checkinId"), d.strN("groupId"), d.strN("from")) }
        db.notifs = (db.notifs.filter { !it.srv && it.userId == mine } + srvN).toMutableList()
        val freshN = srvN.filter { it.id !in known && !it.read }
        if (freshN.isNotEmpty() && pulledBefore) events.serverNotifications(freshN)
        return freshN
    }

    private suspend fun doRefreshSocial(seed: Map<String, JsonElement>, force: Boolean) = coroutineScope {
        val ownerDb = db
        suspend fun fetch(path: String): JsonElement = seed[path] ?: api.get(path)
        val meR = async { fetch("/v1/me") }; val fr = async { fetch("/v1/friends") }
        val feedF = async { fetch("/v1/feed?tab=friends") }; val feedP = async { fetch("/v1/feed?tab=public") }
        val stories = async { fetch("/v1/stories") }; val notifs = async { fetch("/v1/notifications") }; val convs = async { fetch("/v1/conversations") }
        val groups = async { try { fetch("/v1/groups") } catch (e: ApiError) { if (e.status == 404) JsonArray(emptyList()) else throw e } }
        val blocks = async { try { fetch("/v1/blocks") } catch (e: ApiError) { if (e.status == 404) JsonArray(emptyList()) else throw e } }
        val myStories = async { try { fetch("/v1/me/stories") } catch (e: ApiError) { if (e.status == 404) JsonArray(emptyList()) else throw e } }
        val sugg = async { try { fetch("/v1/friends/suggestions") } catch (e: ApiError) { if (e.status == 404) null else throw e } }
        // Fetch all resources before mutating the model; a failed response must not leave a partial refresh.
        val inputs = listOf(meR, fr, feedF, feedP, stories, notifs, convs, groups, blocks, myStories, sugg).map { it.await() }
        if (db !== ownerDb || !store.acct) return@coroutineScope
        val mine = meId()
        val m = meR.await()
        val u = upsertUser(m.jsonObject) { it.bio = m.str("bio"); it.freezes = m.int("freezes"); it.createdAt = msOf(m.strN("createdAt")) ?: it.createdAt }!!
        val mod = m.obj("moderation")
        u.ban = Mapping.banOf(mod.bool("banPermanent"), mod.strN("banUntil"), mod.strN("banReason"))
        u.warned = if (mod.bool("warned")) Warn(nowMs()) else null; u.noComments = mod.bool("commentsRestricted")
        u.tutoBonus = m.bool("welcomeBonus") || u.tutoBonus
        (m as JsonObject)["cheersReceived"]?.jsonPrimitive?.intOrNull?.let { u.cheersIn = it }
        db.appeals.removeAll { it.userId == mine }
        mod.obj("lastAppeal")?.let { ap -> db.appeals.add(Appeal("appeal-$mine", mine, "", msOf(ap.strN("createdAt")) ?: 0, ap.str("status"), ap.str("answer"))) }
        // Amis et demandes
        val f = fr.await(); val fs = mutableListOf<Friendship>()
        for (x in f.arr("friends")) { val uu = upsertUser(x.obj("user")) { it.days = x.strs("days"); it.pts = x.int("points") } ?: continue; fs.add(Friendship(x.str("friendshipId"), mine, uu.id, "accepted", msOf(x.strN("since")) ?: 0)) }
        for (x in f.arr("incoming")) { val uu = upsertUser(x.obj("user")) ?: continue; fs.add(Friendship(x.str("friendshipId"), uu.id, mine, "pending", nowMs())) }
        for (x in f.arr("outgoing")) { val uu = upsertUser(x.obj("user")) ?: continue; fs.add(Friendship(x.str("friendshipId"), mine, uu.id, "pending", nowMs())) }
        db.friendships = fs
        // Feed
        val posts = LinkedHashMap<String, Post>()
        val oldPosts = db.posts.map { it.id }.toSet()
        val friendPosts = feedF.await().arr("posts")
        for (p in friendPosts + feedP.await().arr("posts")) posts[p.str("id")] = postFromServer(p)
        val freshP = friendPosts.mapNotNull { posts[it.str("id")] }.filter { it.id !in oldPosts && it.userId != mine && it.userId.isNotEmpty() && it.at > nowMs() - 86_400_000L }
        if (pg.isAdmin(u)) for (p in db.posts) if (p.mod && !posts.containsKey(p.id)) posts[p.id] = p
        db.posts = posts.values.toMutableList()
        // Stories (en ligne) et archive personnelle
        val st = mutableListOf<Story>()
        for (grp in stories.await().list()) {
            val a = upsertUser(grp.obj("author")) ?: continue
            for (s in grp.arr("stories")) st.add(Story(s.str("id"), a.id, s.str("kind"), s.obj("data"), s.strN("mediaUrl"), s.str("text"), msOf(s.strN("createdAt")) ?: 0,
                if (s.bool("seen")) mutableListOf(mine) else mutableListOf(), s.intN("viewCount"), s.bool("liked"), s.intN("likeCount")))
        }
        val old = db.stories.associateBy { it.id }
        st.filter { it.userId == mine }.forEach { s -> old[s.id]?.let { o -> if (o.viewsLoaded && o.views == s.views) { s.seenBy = o.seenBy; s.likers = o.likers; s.viewsLoaded = true } } }
        for (s in myStories.await().list()) if (st.none { it.id == s.str("id") })
            st.add(Story(s.str("id"), mine, s.str("kind"), s.obj("data"), s.strN("mediaUrl"), s.str("text"), msOf(s.strN("createdAt")) ?: 0, MutableList(s.int("viewCount")) { "" }, s.intN("viewCount"), false, s.int("likeCount")))
        db.stories = st
        // Notifications du serveur + notifications locales (rappels)
        applyNotifs(notifs.await())
        val before = db.messages.map { it.id }.toSet()
        applyConversations(convs.await(), groups.await())
        if (db !== ownerDb || !store.acct) return@coroutineScope
        // Comptes bloqués
        db.blocks = blocks.await().list().mapNotNull { b -> val uu = upsertUser(b.obj("user")) ?: return@mapNotNull null; Block(mine, uu.id, msOf(b.strN("blockedAt")) ?: 0) }.toMutableList()
        sugg.await()?.let { s -> suggestions = s.list().onEach { upsertUser(it.obj("user")) } }
        // Modération : premier plan seulement, onglet affiché, demande explicite, ou données de plus de 5 min (badge de l'onglet)
        val withMod = pg.isAdmin(u) && pingEnabled && (force || moderationVisible || lastModAt.let { it == null || clock() - it >= MOD_FRESH_MS })
        if (withMod) { refreshModeration(); lastModAt = clock(); NetStats.count("social.admin") }
        repairRefs()
        e2e.decryptAll()
        val freshM = db.messages.filter { it.id !in before && it.from != mine && !it.read && !it.readBy.contains(mine) }
        // Premier chargement social du compte sur cet appareil : l'historique ne déclenche pas de notifications
        val seenBefore = db.cloud?.socialPulled == true || before.isNotEmpty()
        if (freshM.isNotEmpty() && seenBefore) events.incomingMessages(freshM)
        if (freshP.isNotEmpty() && (oldPosts.isNotEmpty() || db.cloud?.socialPulled == true) && pulledBefore) events.newPosts(freshP)
        db.cloud?.socialPulled = true
        val cs = cloudState()
        cs.pollDigests = listOf(notifs.await(), convs.await(), groups.await(), feedF.await()).map { digest(it) }
        cs.socialStale = false; lastSocialAt = clock()
        NetStats.count("social.full")
        // Écriture et réveil de l'interface seulement si les réponses ont changé, ou si la base a été modifiée entre-temps
        val sig = inputs.hashCode()
        if (withMod || sig != socialInputs || store.saves != savesAtSocial || freshM.isNotEmpty()) {
            store.rawSave(); socialInputs = sig; savesAtSocial = store.saves
        }
        pg.queueBadgeCheck()
    }

    /** Signature du résumé d'une conversation (sans le profil du correspondant) ou d'un groupe (sans la liste des membres). */
    private fun convSig(c: JsonObject) = JsonObject(c - "peer").toString()
    private fun groupSig(g: JsonObject) = JsonObject(g - "members").toString()

    /**
     * Une conversation n'est rechargée que si son résumé (lastAt, unread, lastE2e…) a changé depuis le dernier
     * chargement, ou si aucun message local n'existe ; signature absente (mise à jour) : seulement si non lue.
     */
    private fun needsLoad(key: String, sig: String, unread: Int, noLocal: Boolean): Boolean {
        val old = cloudState().convSigs[key]
        return if (noLocal) old != sig else if (old == null) unread > 0 else old != sig
    }

    private suspend fun applyConversations(conversations: JsonElement, groups: JsonElement) {
        val sigs = cloudState().convSigs; val live = HashSet<String>()
        // Conversations : rechargées seulement si leur résumé a changé (ou jamais chargées)
        for (c in conversations.list()) {
            val peer = upsertUser(c.obj("peer")) ?: continue
            val key = "c:${peer.id}"; val sig = convSig(c); live.add(key)
            if (needsLoad(key, sig, c.int("unread"), db.messages.none { it.from == peer.id || it.to == peer.id })) loadConversation(peer.id, true)
            sigs[key] = sig
        }
        // Groupes de discussion
        val gl = groups.list()
        db.chats = gl.map { g -> g.arr("members").forEach { upsertUser(it) }
            Chat(g.str("id"), g.str("name"), g.str("emoji"), g.strN("ownerId"), g.arr("members").map { it.str("id") }.toMutableList(), msOf(g.strN("createdAt")) ?: nowMs()) }.toMutableList()
        val liveChats = db.chats.map { it.id }.toSet()
        db.messages = db.messages.filter { it.chat == null || it.chat in liveChats }.toMutableList()
        for (g in gl) {
            val key = "g:${g.str("id")}"; val sig = groupSig(g); live.add(key)
            if (needsLoad(key, sig, g.int("unread"), g.obj("last") != null && db.messages.none { it.chat == g.str("id") })) loadGroup(g.str("id"), true)
            sigs[key] = sig
        }
        sigs.keys.retainAll(live)
    }

    private suspend fun refreshInbox(values: List<JsonElement>, digests: List<String>) = socialLock.withLock {
        val ownerDb = db
        val mine = meId()
        val known = db.messages.map { it.id }.toSet()
        applyConversations(values[1], values[2])
        if (db !== ownerDb || !store.acct) return@withLock
        e2e.decryptAll()
        val fresh = db.messages.filter { it.id !in known && it.from != mine && !it.read && mine !in it.readBy }
        if (fresh.isNotEmpty() && db.cloud?.socialPulled == true) events.incomingMessages(fresh)
        cloudState().pollDigests = digests
        store.rawSave()
    }

    fun postFromServer(p: JsonObject): Post {
        val a = upsertUser(p.obj("author"))
        p.arr("likers").forEach { upsertUser(it) }; p.arr("comments").forEach { upsertUser(it.obj("author")) }
        val goalId = p.strN("goalId")?.takeIf { gid -> db.goals.any { it.id == gid } }
        return Post(p.str("id"), a?.id ?: "", p.str("text"), if (p.str("visibility") == "public") "public" else "friends", goalId, p.str("kind", "manual"), msOf(p.strN("createdAt")) ?: 0,
            p.arr("likers").map { it.str("id") }.toMutableList(),
            p.arr("comments").map { c -> Comment(c.str("id"), c.obj("author").str("id"), c.str("text"), msOf(c.strN("createdAt")) ?: 0) }.toMutableList(),
            p.strN("photoUrl"), p.obj("poll")?.let { Mapping.pollFromServer(it) }, if (p.str("status") == "pending") "pending" else null, p.bool("commentsLocked"),
            reactions = p.arr("likers").associate { it.str("id") to it.str("reaction", "fire") }.toMutableMap())
    }

    private val openConversationSummary = mutableMapOf<Conv, JsonElement?>()
    private val openConversationFullAt = mutableMapOf<Conv, Long>()

    /** Existing list endpoints expose lastAt/lastE2e/unread; no invented `since` parameter.
     * New messages keep the six-second probe. A full reconciliation every 30 seconds also
     * covers server changes not represented in a summary (including history corrections).
     */
    suspend fun refreshOpenConversation(peer: String?, group: String?, force: Boolean = false): Boolean {
        if (!store.acct || !store.online) return false
        val key = Conv(peer = peer, group = group)
        val now = System.nanoTime() / 1_000_000
        if (!force) {
            val rows = api.get(if (group != null) "/v1/groups" else "/v1/conversations").list()
            val summary = rows.firstOrNull { if (group != null) it.str("id") == group else it.obj("peer").str("id") == peer }
            val previous = openConversationSummary[key]
            openConversationSummary[key] = summary
            // Le résumé vu ici sert aussi à la relève : pas de second chargement de cette conversation au prochain cycle
            (summary as? JsonObject)?.let { cloudState().convSigs[if (group != null) "g:$group" else "c:$peer"] = if (group != null) groupSig(it) else convSig(it) }
            if (summary == previous && now - (openConversationFullAt[key] ?: -30_000L) < 30_000L) return false
        }
        val changed = if (group != null) openGroup(group) else openChat(requireNotNull(peer))
        openConversationFullAt[key] = now
        return changed
    }

    suspend fun loadConversation(peerId: String, quiet: Boolean): Boolean {
        val ownerDb = db; val mine = meId()
        val list = api.get("/v1/conversations/$peerId").list()
        if (db !== ownerDb || !store.acct) return false
        val old = db.messages.filter { it.chat == null && ((it.from == peerId && it.to == mine) || (it.from == mine && it.to == peerId)) }
        val incoming = list.map { m ->
            val e2eP = m.obj("e2e")?.let { try { JSON.decodeFromJsonElement(fr.pepsgoal.core.crypto.E2ePayload.serializer(), it) } catch (e: Exception) { null } }
            Message(m.str("id"), if (m.bool("mine")) mine else peerId, if (m.bool("mine")) peerId else mine, null, msOf(m.strN("createdAt")) ?: 0,
                m.bool("mine") || m.bool("read") || !quiet, mutableListOf(), e2eP, if (e2eP == null) m.str("text") else null, m.bool("official"), m.strN("tag") ?: if (m.bool("official")) "info" else null)
        }
        if (sameMessages(old, incoming)) return false
        db.messages = (db.messages.filterNot { it in old } + incoming).toMutableList()
        return true
    }

    private fun sameMessages(a: List<Message>, b: List<Message>): Boolean = a.size == b.size && a.zip(b).all { (x, y) ->
        x.id == y.id && x.from == y.from && x.to == y.to && x.chat == y.chat && x.at == y.at &&
            x.read == y.read && x.readBy == y.readBy && x.e2e == y.e2e && x.text == y.text && x.official == y.official && x.tag == y.tag
    }

    suspend fun loadGroup(gid: String, quiet: Boolean): Boolean {
        val ownerDb = db; val mine = meId()
        val list = api.get("/v1/groups/$gid/messages").list()
        if (db !== ownerDb || !store.acct) return false
        val old = db.messages.filter { it.chat == gid }
        val incoming = list.map { m ->
            ensureUserStub(m.str("senderId"))
            Message(m.str("id"), m.str("senderId"), null, gid, msOf(m.strN("createdAt")) ?: 0, true,
                if (m.bool("mine") || m.bool("read") || !quiet) mutableListOf(mine) else mutableListOf(),
                m.obj("e2e")?.let { try { JSON.decodeFromJsonElement(fr.pepsgoal.core.crypto.E2ePayload.serializer(), it) } catch (e: Exception) { null } })
        }
        if (sameMessages(old, incoming)) return false
        db.messages = (db.messages.filter { it.chat != gid } + incoming).toMutableList()
        return true
    }

    suspend fun loadStoryViews(s: Story) {
        try {
            val v = api.get("/v1/stories/${s.id}/views").list()
            v.forEach { upsertUser(it) }
            s.seenBy = v.map { it.str("id") }.toMutableList(); s.likers = v.filter { it.bool("liked") }.map { it.str("id") }.toMutableList(); s.viewsLoaded = true
        } catch (e: ApiError) {}
    }

    /** Profil d'une personne : objectifs visibles, leurs validations, jours actifs et flammes. */
    suspend fun loadProfile(id: String) {
        profileLoaded.add(id)
        try {
            val p = api.get("/v1/users/$id")
            val pu = p.obj("user")
            upsertUser(pu) { it.bio = pu.str("bio"); it.createdAt = msOf(pu.strN("createdAt")) ?: it.createdAt; it.days = p.strs("days"); it.pts = p.int("points"); it.friendCount = p.int("friendCount") }
            for (sg in p.arr("goals")) {
                val g = Mapping.fromServerGoal(sg); g.members?.forEach { ensureUserStub(it) }
                if (g.userId == meId()) continue
                val i = db.goals.indexOfFirst { it.id == g.id }; if (i >= 0) db.goals[i] = g else db.goals.add(g)
            }
            val known = db.checkins.map { it.id }.toSet()
            val add = p.arr("checkins").filter { it.str("id") !in known && it.str("userId") != meId() }.map { ensureUserStub(it.str("userId")); Mapping.fromServerCheckin(it) }
            if (add.isNotEmpty()) db.checkins = (db.checkins + add).toMutableList()
            store.rawSave()
        } catch (e: ApiError) { profileLoaded.remove(id) }
    }

    private suspend fun refreshModeration() = coroutineScope {
        val users = async { api.get("/v1/admin/users") }; val pending = async { api.get("/v1/admin/posts?scope=pending") }
        val reported = async { api.get("/v1/admin/posts?scope=reported") }; val recent = async { api.get("/v1/admin/posts?scope=recent") }
        val apP = async { api.get("/v1/admin/appeals?status=pending") }; val apD = async { api.get("/v1/admin/appeals?status=done") }
        val log = async { api.get("/v1/admin/modlog") }; val maint = async { api.get("/v1/admin/maintenance") }; val news = async { api.get("/v1/admin/announcements") }
        try { upsertUser(api.get("/v1/admin/official").jsonObject) } catch (e: ApiError) {}
        val mine = meId()
        for (x in users.await().list()) upsertUser(x) {
            it.createdAt = msOf(x.strN("createdAt")) ?: it.createdAt; it.noComments = x.bool("commentsRestricted")
            it.ban = Mapping.banOf(x.bool("banPermanent"), x.strN("banUntil"), x.strN("banReason"))
            it.warned = x.strN("warnedAt")?.let { w -> Warn(msOf(w) ?: 0, x.str("warnReason")) }
        }
        val byId = db.posts.associateBy { it.id }.toMutableMap(); val reports = mutableListOf<Report>()
        for (p in pending.await().list() + reported.await().list() + recent.await().list()) {
            val a = upsertUser(p.obj("author"))
            var cur = byId[p.str("id")]
            if (cur == null) {
                cur = Post(p.str("id"), a?.id ?: "", p.str("text"), if (p.str("visibility") == "public") "public" else "friends", null, "manual", msOf(p.strN("createdAt")) ?: 0, mod = true)
                p.strN("photoUrl")?.let { cur.photo = it }
                p.obj("poll")?.let { po -> cur.poll = Poll(po.bool("multi"), msOf(po.strN("endsAt")) ?: 0, 0, po.arr("options").map { PollOption(it.str("id"), it.str("text")) }.toMutableList()) }
                db.posts.add(cur); byId[cur.id] = cur
            }
            cur.status = if (p.str("status") == "pending") "pending" else null; cur.commentsLocked = p.bool("commentsLocked")
            val reasons = p.strs("reasons")
            repeat(p.int("reportCount")) { i -> reports.add(Report("${cur.id}:$i", cur.id, mine, reasons.getOrNull(i % maxOf(1, reasons.size)) ?: "other", cur.at)) }
        }
        db.reports = reports
        db.appeals = (apP.await().list() + apD.await().list()).mapNotNull { a ->
            val uu = upsertUser(a.obj("user")) ?: return@mapNotNull null
            if (a.str("status") == "pending") { val b = a.obj("ban"); Mapping.banOf(b.bool("permanent"), b.strN("until"), b.strN("reason"))?.let { uu.ban = it } }
            Appeal(a.str("id"), uu.id, a.str("text"), msOf(a.strN("createdAt")) ?: 0, a.str("status"), a.str("answer"), msOf(a.strN("decidedAt")))
        }.toMutableList()
        db.modlog = log.await().list().map { l -> ModLog(l.str("id"), msOf(l.strN("createdAt")) ?: 0, l.str("action"), l.strN("targetUserId"), listOf(l.str("detail"), l.strN("adminName")?.let { "par $it" } ?: "").filter { it.isNotEmpty() }.joinToString(" · ")) }.toMutableList()
        val ml = maint.await().list()
        val active = ml.firstOrNull { it.strN("endedAt") == null && (it.strN("endsAt") == null || (msOf(it.strN("endsAt")) ?: 0) > nowMs()) }
        db.maintenance = active?.let { Maintenance(it.str("id"), it.str("mode"), msOf(it.strN("startsAt")) ?: 0, msOf(it.strN("endsAt")), it.str("message")) }
        db.maintLog = ml.reversed().map { Maintenance(it.str("id"), it.str("mode"), msOf(it.strN("startsAt")) ?: 0, msOf(it.strN("endsAt")), it.str("message"), msOf(it.strN("endedAt"))) }.toMutableList()
        db.news = news.await().list().reversed().map { News(it.str("id"), it.str("text"), msOf(it.strN("createdAt")) ?: 0) }.toMutableList()
    }

    private fun applyMaintenanceStatus() {
        if (!store.acct || pg.isAdmin(pg.me())) return
        val m = maintenanceStatus
        val next = m?.let { Maintenance(null, it.str("mode"), nowMs() - 1000, msOf(it.strN("endsAt")), it.str("message")) }
        val cur = db.maintenance
        val same = (next == null && cur == null) || (next != null && cur != null && next.mode == cur.mode && next.until == cur.until && next.message == cur.message)
        if (!same) { db.maintenance = next; store.rawSave() }
    }

    suspend fun loadStats() {
        try { stats = api.get("/v1/admin/stats").jsonObject; statsError = null } catch (e: ApiError) { statsError = e.message ?: "Statistiques indisponibles." }
        statsAt = nowMs(); store.changed()
    }

    /** Supprime les références vers des comptes, objectifs ou publications qui n'existent plus. */
    fun repairRefs() {
        val ids = db.users.map { it.id }.toSet()
        db.goals.removeAll { it.userId !in ids }
        val goalIds = db.goals.map { it.id }.toSet()
        db.checkins = db.checkins.filter { it.userId in ids && it.goalId in goalIds }.toMutableList()
        db.posts.removeAll { it.userId !in ids }
        db.posts.forEach { p -> p.likes.retainAll { it in ids }; p.comments.retainAll { it.userId in ids }; if (p.goalId != null && p.goalId !in goalIds) p.goalId = null }
        db.stories.removeAll { it.userId !in ids }
        pg.me()?.let { u -> val gg = u.goalGroups.map { it.id }.toSet(); u.goalGroupOf = u.goalGroupOf.filter { it.key in goalIds && it.value in gg }.toMutableMap() }
    }

    /* ------------------------------------------------------------------ pont avec la logique */
    override fun notifyOther(to: String, text: String, type: String, goalId: String?) {
        if (!Mapping.UUID_RE.matches(to)) return
        scope.launch { try { api.post("/v1/notify", buildJsonObject { put("to", to); put("text", text); put("type", type); put("goalId", goalId?.takeIf { Mapping.UUID_RE.matches(it) }?.let(::JsonPrimitive) ?: JsonNull) }) } catch (e: ApiError) {} }
    }
    override fun autoPost(g: Goal, text: String) {
        scope.launch {
            try { api.post("/v1/posts", buildJsonObject { put("text", text.take(500)); put("visibility", if (g.visibility == "public") "public" else "friends"); put("goalId", if (Mapping.UUID_RE.matches(g.id)) JsonPrimitive(g.id) else JsonNull); put("kind", "milestone") }); scheduleSocial(800, force = true) } catch (e: ApiError) {}
        }
    }
    override fun confirmCheck(checkinId: String, ok: Boolean) {
        scope.launch { try { api.post("/v1/checkins/$checkinId/${if (ok) "confirm" else "refuse"}"); syncNow(force = true); pg.hooks.toast(if (ok) "Réussite confirmée." else "Validation refusée.", if (ok) "check" else "x") } catch (e: ApiError) { pg.hooks.toast(e.message ?: "Action impossible.", "x") } }
    }
    override fun freezesUsed(count: Int) {
        scope.launch { try { val r = api.post("/v1/me/freezes/use", buildJsonObject { put("count", count) }); pg.me()?.freezes = r.int("freezes"); store.rawSave() } catch (e: ApiError) {} }
    }
    override fun welcomeBonus() {
        scope.launch {
            try {
                val r = api.post("/v1/me/welcome-bonus"); pg.me()?.freezes = r.int("freezes"); store.rawSave()
                if (r.bool("granted")) { pg.hooks.sparks(TUTO_GELS); pg.hooks.toast("$TUTO_GELS gels de série ajoutés !", "snow") }
            } catch (e: ApiError) {}
        }
    }

    companion object {
        val RETRY = listOf(5000L, 10000L, 20000L, 40000L, 60000L)
        /** Les quatre ressources de la relève. */
        val POLL_PATHS = listOf("/v1/notifications", "/v1/conversations", "/v1/groups", "/v1/feed?tab=friends")
        /** Durée de validité d'un rechargement social complet, et de la modération (premier plan). */
        const val SOCIAL_FRESH_MS = 60_000L
        const val MOD_FRESH_MS = 5 * 60_000L
        /** Statut du serveur et flux d'amis : écarts minimaux en arrière-plan. */
        const val STATUS_BG_MS = 10 * 60_000L
        const val FEED_BG_MS = 10 * 60_000L
        /** Notifications après lesquelles le flux d'amis est relevé au cycle suivant. */
        val FEED_HINTS = setOf("like", "comment", "friend")
        private val NOTIF_VIEW = mapOf("like" to "feed", "comment" to "feed", "friend" to "friends", "official" to "friends", "mod" to "feed", "group" to "friends")

        /** Empreinte SHA-1 de la forme canonique (clés triées) d'une réponse JSON. */
        fun digest(e: JsonElement): String {
            val sb = StringBuilder()
            fun canon(x: JsonElement) {
                when (x) {
                    is JsonObject -> { sb.append('{'); x.keys.sorted().forEach { k -> sb.append(JsonPrimitive(k)).append(':'); canon(x.getValue(k)); sb.append(',') }; sb.append('}') }
                    is JsonArray -> { sb.append('['); x.forEach { canon(it); sb.append(',') }; sb.append(']') }
                    else -> sb.append(x.toString())
                }
            }
            canon(e)
            return java.security.MessageDigest.getInstance("SHA-1").digest(sb.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        }
        const val OFF_KEY = "pepsgoal.offline.v1"
    }
}
