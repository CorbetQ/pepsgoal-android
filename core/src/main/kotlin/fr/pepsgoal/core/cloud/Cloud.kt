package fr.pepsgoal.core.cloud

import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
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
}

/**
 * Couche connectée (port de apps/web/src/cloud.js) : comptes, synchronisation hors ligne d'abord, fonctions sociales,
 * messagerie chiffrée de bout en bout et modération, reliées au serveur PepsGoal.
 */
class Cloud(val store: Store, val pg: Pg, val api: Api, val secrets: Secrets, val scope: CoroutineScope, var events: CloudEvents = object : CloudEvents {}) : CloudBridge {
    val db get() = store.db
    val e2e = E2eKeys(this)

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
    private var refreshing: Job? = null
    private var refreshAgain = false
    private var syncTimer: Job? = null
    private var socialTimer: Job? = null
    private var pingJob: Job? = null

    init {
        api.forceOffline = forceOffline
        api.onServerDown = { serverDown() }
        api.onSessionLost = { scope.launch { sessionLost() } }
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
    private fun serverDown() { if (serverOk) { setServerOk(false); failures = 0; schedulePing(RETRY[0]) } }
    private fun onReconnect() { if (store.acct) { scope.launch { syncNow(); refreshSocial() } } }

    private fun schedulePing(ms: Long) { pingJob?.cancel(); pingJob = scope.launch { delay(ms); ping() } }

    /** Le serveur répond-il ? (nouvel essai à 5 s, 10 s, 20 s, 40 s puis chaque minute) */
    suspend fun ping() {
        pingJob?.cancel()
        if (forceOffline) return
        try {
            val s = api.status()
            if (!s.bool("ok")) throw ApiError(503, "down", "")
            failures = 0; maintenanceStatus = s.obj("maintenance"); minAppVersion = s.strN("minAppVersion"); applyMaintenanceStatus()
            setServerOk(true)
            schedulePing(60_000)
        } catch (e: ApiError) {
            setServerOk(false)
            schedulePing(RETRY[minOf(failures++, RETRY.size - 1)])
        }
    }
    fun pingSoon() { failures = 0; scope.launch { ping() } }

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
        u.username = p.str("username", u.username); u.name = p.str("displayName", u.name); u.avatar = Mapping.mapAvatar(p.obj("avatar")); u.srv = true; u.stub = false
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
        store.use(newDb, true)
        val u = upsertUser(user) { it.bio = user.str("bio"); it.freezes = user.int("freezes") }!!
        Mapping.applyServerSettings(u, user.obj("settings"))
        db.users.removeAll { it.role == "official" && !it.srv }
        cloudState(); db.session = u.id
        store.rawSave(); events.sessionChanged()
    }

    /** Démarrage : reprend la session enregistrée sur l'appareil. */
    fun boot() {
        api.forceOffline = forceOffline
        val cached = store.readAccount()
        if (api.tokens != null && cached?.session != null) { store.use(cached, true); cloudState() }
        else store.use(store.readLocal() ?: Db(), false)
        scope.launch {
            ping()
            if (store.acct) {
                launch { try { e2e.device(); e2e.shareMissing() } catch (e: Exception) {} }
                syncNow(); refreshSocial()
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
        syncNow()
        if (!store.online || syncError.isNotEmpty()) { pg.hooks.toast("Hors ligne : la déconnexion et le changement de compte reviendront avec la connexion.", "cloudoff"); return false }
        if (collectOps().first.isNotEmpty()) { pg.hooks.toast("Des modifications ne sont pas encore synchronisées : réessaie dans un instant.", "cloudoff"); return false }
        leaveAccount(true)
        return true
    }

    fun leaveAccount(callServer: Boolean) {
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

    private fun markSynced(m: Mark) {
        val cs = cloudState()
        when (m.kind) {
            "settings" -> cs.snapSettings = m.value ?: ""
            "goals" -> if (m.value == null) cs.snapGoals.remove(m.id) else cs.snapGoals[m.id!!] = m.value
            else -> if (m.value == null) cs.snapCheckins.remove(m.id) else cs.snapCheckins[m.id!!] = m.value
        }
    }

    fun scheduleSync(ms: Long = 1500) {
        if (!store.acct) return
        syncTimer?.cancel(); syncTimer = scope.launch { delay(ms); syncNow() }
    }

    suspend fun syncNow() {
        if (!store.acct || !store.online) return
        syncing?.let { if (it.isActive) { it.await(); return } }
        val d = scope.async {
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
            } catch (e: ApiError) { syncError = e.message ?: "Erreur de synchronisation" }
            catch (e: Exception) { syncError = e.message ?: "Erreur de synchronisation" }
            finally { store.changed() }
        }
        syncing = d
        d.await()
    }

    private suspend fun pull() {
        val cs = cloudState(); var changed = false
        for (guard in 0 until 50) {
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
            if (st != null) pg.me()?.let { Mapping.applyServerSettings(it, st); cs.snapSettings = Mapping.toServerSettings(it).toString(); changed = true }
            cs.cursor = (r as JsonObject)["cursor"]?.jsonPrimitive?.longOrNull ?: cs.cursor
            if (!r.bool("hasMore")) break
        }
        pulledOnce = true; cs.pulled = true
        if (changed) store.rawSave()
    }

    fun deviceId(): String = secrets.get("pepsgoal.device.v1") ?: uid().also { secrets.set("pepsgoal.device.v1", it) }

    /* ------------------------------------------------------------------ fonctions sociales */
    fun scheduleSocial(ms: Long = 400) {
        if (!store.acct) return
        socialTimer?.cancel(); socialTimer = scope.launch { delay(ms); refreshSocial() }
    }

    suspend fun refreshSocial() {
        if (!store.acct || !store.online) return
        // Un rechargement est déjà en cours : on attend sa fin (les relèves en arrière-plan doivent finir avant de rendre la main)
        if (refreshing?.isActive == true) { refreshAgain = true; refreshing?.join(); return }
        val job = scope.launch {
            try { doRefreshSocial() } catch (e: ApiError) { pollStatus = "erreur serveur ${e.status}"; if (e.status != 0) events.error(e.message ?: "") }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { pollStatus = "erreur : ${e::class.simpleName} ${e.message ?: ""}".trim() }
        }
        refreshing = job
        job.join()
        if (refreshAgain) { refreshAgain = false; scheduleSocial(150) }
    }

    private var pollSig: Int? = null
    /** Résultat de la dernière relève (diagnostic affiché dans les réglages). */
    var pollStatus = ""

    /**
     * Relève légère (toutes les minutes, y compris en arrière-plan) : notifications, conversations, groupes et
     * publications des amis. Si quelque chose a changé depuis la dernière relève, tout le social est rechargé
     * (ce qui déclenche les notifications système des nouveautés). Renvoie true si un rechargement a eu lieu.
     */
    suspend fun poll(): Boolean {
        if (!store.acct) { pollStatus = "pas de compte"; return false }
        // Application réveillée après un long sommeil : l'état « hors ligne » peut dater (réseau coupé pendant la veille,
        // nouvel essai de connexion jamais exécuté car le processus était gelé) : on revérifie tout de suite
        if (!store.online) { if (!navOnline) { pollStatus = "pas de réseau"; return false }; failures = 0; ping() }
        if (!store.online) { pollStatus = "serveur injoignable"; return false }
        val sig = try {
            coroutineScope {
                val n = async { api.get("/v1/notifications") }; val c = async { api.get("/v1/conversations") }
                val g = async { try { api.get("/v1/groups") } catch (e: ApiError) { JsonArray(emptyList()) } }
                val f = async { api.get("/v1/feed?tab=friends") }
                listOf(
                    n.await().list().map { it.str("id") + it.str("read") },
                    c.await().list().map { it.obj("peer").str("id") + ":" + it.int("unread") + ":" + (it["last"]?.toString() ?: "") },
                    g.await().list().map { it.str("id") + ":" + it.int("unread") + ":" + (it["last"]?.toString() ?: "") },
                    // Encouragements compris : ceux des amis apparaissent sans rafraîchir le feed
                    f.await().arr("posts").map { it.str("id") + ":" + it.arr("comments").size + ":" + it.arr("likers").size },
                ).hashCode()
            }
        } catch (e: Exception) { pollStatus = "erreur : ${e.message ?: e::class.simpleName}"; return false }
        if (sig == pollSig) { pollStatus = "à jour"; return false }
        pollSig = sig
        pollStatus = "nouveautés relevées"
        refreshSocial()
        return true
    }

    private suspend fun doRefreshSocial() = coroutineScope {
        val meR = async { api.get("/v1/me") }; val fr = async { api.get("/v1/friends") }
        val feedF = async { api.get("/v1/feed?tab=friends") }; val feedP = async { api.get("/v1/feed?tab=public") }
        val stories = async { api.get("/v1/stories") }; val notifs = async { api.get("/v1/notifications") }; val convs = async { api.get("/v1/conversations") }
        val groups = async { try { api.get("/v1/groups") } catch (e: ApiError) { JsonArray(emptyList()) } }
        val blocks = async { try { api.get("/v1/blocks") } catch (e: ApiError) { JsonArray(emptyList()) } }
        val myStories = async { try { api.get("/v1/me/stories") } catch (e: ApiError) { JsonArray(emptyList()) } }
        val sugg = async { try { api.get("/v1/friends/suggestions") } catch (e: ApiError) { null } }
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
        val VIEW = mapOf("like" to "feed", "comment" to "feed", "friend" to "friends", "official" to "friends", "mod" to "feed", "group" to "friends")
        val known = db.notifs.filter { it.srv }.map { it.id }.toSet()
        val srvN = notifs.await().list().map { n -> val d = n.obj("data")
            Notif(n.str("id"), mine, n.str("type"), n.str("text"), msOf(n.strN("createdAt")) ?: 0, n.bool("read"), true, VIEW[n.str("type")], d.strN("goalId"), d.strN("postId"), d.strN("checkinId"), d.strN("groupId"), d.strN("from")) }
        db.notifs = (db.notifs.filter { !it.srv && it.userId == mine } + srvN).toMutableList()
        val freshN = srvN.filter { it.id !in known && !it.read }
        if (freshN.isNotEmpty() && pulledBefore) events.serverNotifications(freshN)
        // Conversations : conversations non lues (ou jamais chargées) chargées en entier
        val before = db.messages.map { it.id }.toSet()
        for (c in convs.await().list()) {
            val peer = upsertUser(c.obj("peer")) ?: continue
            if (c.int("unread") > 0 || db.messages.none { it.from == peer.id || it.to == peer.id }) loadConversation(peer.id, true)
        }
        // Groupes de discussion
        val gl = groups.await().list()
        db.chats = gl.map { g -> g.arr("members").forEach { upsertUser(it) }
            Chat(g.str("id"), g.str("name"), g.str("emoji"), g.strN("ownerId"), g.arr("members").map { it.str("id") }.toMutableList(), msOf(g.strN("createdAt")) ?: nowMs()) }.toMutableList()
        val live = db.chats.map { it.id }.toSet()
        db.messages = db.messages.filter { it.chat == null || it.chat in live }.toMutableList()
        for (g in gl) if (g.int("unread") > 0 || (g.obj("last") != null && db.messages.none { it.chat == g.str("id") })) loadGroup(g.str("id"), true)
        // Comptes bloqués
        db.blocks = blocks.await().list().mapNotNull { b -> val uu = upsertUser(b.obj("user")) ?: return@mapNotNull null; Block(mine, uu.id, msOf(b.strN("blockedAt")) ?: 0) }.toMutableList()
        sugg.await()?.let { s -> suggestions = s.list().onEach { upsertUser(it.obj("user")) } }
        if (pg.isAdmin(u)) refreshModeration()
        repairRefs()
        store.rawSave()
        e2e.decryptAll()
        val freshM = db.messages.filter { it.id !in before && it.from != mine && !it.read && !it.readBy.contains(mine) }
        // Premier chargement social du compte sur cet appareil : l'historique ne déclenche pas de notifications
        val seenBefore = db.cloud?.socialPulled == true || before.isNotEmpty()
        if (freshM.isNotEmpty() && seenBefore) events.incomingMessages(freshM)
        if (freshP.isNotEmpty() && (oldPosts.isNotEmpty() || db.cloud?.socialPulled == true) && pulledBefore) events.newPosts(freshP)
        db.cloud?.let { if (!it.socialPulled) { it.socialPulled = true; store.rawSave() } }
        store.changed()
        pg.queueBadgeCheck()
    }

    fun postFromServer(p: JsonObject): Post {
        val a = upsertUser(p.obj("author"))
        p.arr("likers").forEach { upsertUser(it) }; p.arr("comments").forEach { upsertUser(it.obj("author")) }
        val goalId = p.strN("goalId")?.takeIf { gid -> db.goals.any { it.id == gid } }
        return Post(p.str("id"), a?.id ?: "", p.str("text"), if (p.str("visibility") == "public") "public" else "friends", goalId, p.str("kind", "manual"), msOf(p.strN("createdAt")) ?: 0,
            p.arr("likers").map { it.str("id") }.toMutableList(),
            p.arr("comments").map { c -> Comment(c.str("id"), c.obj("author").str("id"), c.str("text"), msOf(c.strN("createdAt")) ?: 0) }.toMutableList(),
            p.strN("photoUrl"), p.obj("poll")?.let { Mapping.pollFromServer(it) }, if (p.str("status") == "pending") "pending" else null, p.bool("commentsLocked"))
    }

    suspend fun loadConversation(peerId: String, quiet: Boolean) {
        val list = api.get("/v1/conversations/$peerId").list(); val mine = meId()
        db.messages = (db.messages.filter { !((it.from == peerId && it.to == mine) || (it.from == mine && it.to == peerId)) } + list.map { m ->
            val e2eP = m.obj("e2e")?.let { try { JSON.decodeFromJsonElement(fr.pepsgoal.core.crypto.E2ePayload.serializer(), it) } catch (e: Exception) { null } }
            Message(m.str("id"), if (m.bool("mine")) mine else peerId, if (m.bool("mine")) peerId else mine, null, msOf(m.strN("createdAt")) ?: 0,
                m.bool("mine") || m.bool("read") || !quiet, mutableListOf(), e2eP, if (e2eP == null) m.str("text") else null, m.bool("official"), m.strN("tag") ?: if (m.bool("official")) "info" else null)
        }).toMutableList()
    }

    suspend fun loadGroup(gid: String, quiet: Boolean) {
        val list = api.get("/v1/groups/$gid/messages").list(); val mine = meId()
        db.messages = (db.messages.filter { it.chat != gid } + list.map { m ->
            ensureUserStub(m.str("senderId"))
            Message(m.str("id"), m.str("senderId"), null, gid, msOf(m.strN("createdAt")) ?: 0, true,
                if (m.bool("mine") || m.bool("read") || !quiet) mutableListOf(mine) else mutableListOf(),
                m.obj("e2e")?.let { try { JSON.decodeFromJsonElement(fr.pepsgoal.core.crypto.E2ePayload.serializer(), it) } catch (e: Exception) { null } })
        }).toMutableList()
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
            try { api.post("/v1/posts", buildJsonObject { put("text", text.take(500)); put("visibility", if (g.visibility == "public") "public" else "friends"); put("goalId", if (Mapping.UUID_RE.matches(g.id)) JsonPrimitive(g.id) else JsonNull); put("kind", "milestone") }); scheduleSocial(800) } catch (e: ApiError) {}
        }
    }
    override fun confirmCheck(checkinId: String, ok: Boolean) {
        scope.launch { try { api.post("/v1/checkins/$checkinId/${if (ok) "confirm" else "refuse"}"); syncNow(); pg.hooks.toast(if (ok) "Réussite confirmée." else "Validation refusée.", if (ok) "check" else "x") } catch (e: ApiError) { pg.hooks.toast(e.message ?: "Action impossible.", "x") } }
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
        const val OFF_KEY = "pepsgoal.offline.v1"
    }
}
