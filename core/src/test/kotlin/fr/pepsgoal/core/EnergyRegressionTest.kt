package fr.pepsgoal.core

import com.sun.net.httpserver.HttpServer
import fr.pepsgoal.core.cloud.*
import fr.pepsgoal.core.crypto.E2e
import kotlinx.serialization.json.*
import fr.pepsgoal.core.model.*
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

private class MemoryFiles : DbFiles, Secrets {
    val data = ConcurrentHashMap<String, String>()
    val writes = AtomicInteger()
    override fun read(name: String) = data[name]
    override fun write(name: String, content: String) { data[name] = content; writes.incrementAndGet() }
    override fun delete(name: String) { data.remove(name) }
    override fun get(key: String) = data[key]
    override fun set(key: String, value: String?) { if (value == null) data.remove(key) else data[key] = value }
}

private class TestServer : AutoCloseable {
    val counts = ConcurrentHashMap<String, AtomicInteger>()
    val responses = ConcurrentHashMap<String, String>()
    val codes = ConcurrentHashMap<String, Int>()
    /** ETag renvoyé par chemin ; une requête qui le présente dans If-None-Match reçoit 304 sans corps. */
    val etags = ConcurrentHashMap<String, String>()
    val conditional = ConcurrentHashMap<String, AtomicInteger>()
    @Volatile var gate: CountDownLatch? = null
    val entered = CountDownLatch(1)
    val bodyStarted = CountDownLatch(1)
    @Volatile var gatePath: String? = null
    private val threads = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = threads
        createContext("/") { ex ->
            val path = ex.requestURI.toString()
            counts.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            entered.countDown()
            try {
                if (path == "/partial") {
                    ex.sendResponseHeaders(200, 1024)
                    ex.responseBody.write(32); ex.responseBody.flush(); bodyStarted.countDown()
                    gate?.await(5, TimeUnit.SECONDS)
                    ex.responseBody.write(ByteArray(1023))
                    return@createContext
                }
                if (gatePath == null || gatePath == path) gate?.await(5, TimeUnit.SECONDS)
                val inm = ex.requestHeaders.getFirst("If-None-Match")
                if (inm != null) conditional.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
                etags[path]?.let { tag ->
                    ex.responseHeaders.add("ETag", tag)
                    if (inm == tag) { ex.sendResponseHeaders(304, -1); return@createContext }
                }
                val body = (responses[path] ?: when (path) {
                    "/v1/me" -> """{"id":"me","username":"test","displayName":"Test"}"""
                    "/v1/feed?tab=friends", "/v1/feed?tab=public" -> """{"posts":[]}"""
                    "/v1/friends" -> """{"friends":[],"incoming":[],"outgoing":[]}"""
                    "/v1/status" -> """{"ok":true}"""
                    else -> if (path.startsWith("/v1/sync/pull")) """{"cursor":1,"hasMore":false}""" else "[]"
                }).toByteArray()
                ex.sendResponseHeaders(codes[path] ?: 200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            } catch (_: Exception) {} finally { ex.close() }
        }
        start()
    }
    val url get() = "http://127.0.0.1:${server.address.port}"
    val calls get() = counts.values.sumOf { it.get() }
    fun count(path: String) = counts[path]?.get() ?: 0
    override fun close() { gate?.countDown(); server.stop(0); threads.shutdownNow() }
}

class EnergyRegressionTest {
    private suspend fun cloudTest(block: suspend (Cloud, MemoryFiles, TestServer) -> Unit) {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val job = SupervisorJob()
        try {
            withContext(dispatcher) {
                TestServer().use { server ->
                    val mem = MemoryFiles(); val store = Store(mem)
                    store.use(Db(session = "me", users = mutableListOf(User("me", "test", "Test")), cloud = CloudState(pulled = true, socialPulled = true)), true)
                    val cloud = Cloud(store, Pg(store), Api(server.url, mem), mem, CoroutineScope(job + dispatcher))
                    block(cloud, mem, server)
                }
            }
        } finally { job.cancelAndJoin(); dispatcher.close() }
    }

    @Test fun pollReusesFourResponsesAndSkipsSuccessfulWindow() = runBlocking {
        cloudTest { c, _, server ->
            assertTrue(c.poll())
            assertEquals(11, server.calls, "A full changed cycle must not redownload its first four resources")
            assertFalse(c.poll())
            assertEquals(11, server.calls)
            assertFalse(c.poll(0))
            assertEquals(15, server.calls, "An unchanged cycle performs only four GETs")
        }
    }

    @Test fun pollCancellationReleasesTheGateAndDoesNotMarkSuccess() = runBlocking {
        cloudTest { c, _, server ->
            server.gate = CountDownLatch(1)
            val poll = launch { c.poll() }
            withContext(Dispatchers.IO) { assertTrue(server.entered.await(2, TimeUnit.SECONDS)) }
            poll.cancelAndJoin()
            assertTrue(c.api.cancelledCalls.get() > 0)
            server.gate!!.countDown(); server.gate = null
            assertTrue(c.poll(), "Cancellation must not suppress the next attempt")
        }
    }

    @Test fun concurrentPollersDoNotDuplicateRequests() = runBlocking {
        cloudTest { c, _, server ->
            server.gate = CountDownLatch(1)
            val first = async { c.poll() }
            withContext(Dispatchers.IO) { assertTrue(server.entered.await(2, TimeUnit.SECONDS)) }
            assertFalse(c.poll())
            server.gate!!.countDown(); server.gate = null
            assertTrue(first.await())
            assertEquals(11, server.calls)
        }
    }

    @Test fun failedPollCanRetryImmediately() = runBlocking {
        cloudTest { c, _, server ->
            server.codes["/v1/notifications"] = 500
            assertFalse(c.poll())
            server.codes.clear()
            assertTrue(c.poll())
        }
    }

    @Test fun chatUnchangedDoesNotSaveAndReadReceiptsPersist() = runBlocking {
        cloudTest { c, mem, server ->
            server.responses["/v1/conversations/peer"] = """[{"id":"message","mine":false,"text":"Bonjour","createdAt":"2026-11-03T12:00:00Z","read":false}]"""
            assertTrue(c.openChat("peer"))
            assertTrue(c.db.messages.single().read)
            val count = mem.writes.get()
            assertFalse(c.openChat("peer"))
            assertEquals(count, mem.writes.get())
            server.responses["/v1/conversations/peer"] = "[]"
            assertTrue(c.openChat("peer"))
            assertTrue(c.db.messages.isEmpty(), "Deleted history must not be retained by an incremental approximation")
        }
    }

    @Test fun inboxChangeDoesNotReloadFeedOrProfiles() = runBlocking {
        cloudTest { c, _, server ->
            c.poll()
            val before = server.calls
            server.responses["/v1/conversations"] = """[{"peer":{"id":"peer","username":"peer","displayName":"Peer"},"unread":1}]"""
            server.responses["/v1/conversations/peer"] = """[{"id":"new","text":"Salut","createdAt":"2026-11-03T12:00:00Z"}]"""
            assertTrue(c.poll(0))
            assertEquals(5, server.calls - before, "Four probes plus the changed conversation")
            assertEquals(1, server.counts["/v1/me"]?.get())
            assertEquals("new", c.db.messages.single().id)
        }
    }

    @Test fun openConversationProbesSummaryWithoutRedownloadingStableHistory() = runBlocking {
        cloudTest { c, mem, server ->
            server.responses["/v1/conversations"] = """[{"peer":{"id":"peer"},"lastAt":"2026-11-03T12:00:00Z","unread":0}]"""
            server.responses["/v1/conversations/peer"] = """[{"id":"first","text":"Bonjour","createdAt":"2026-11-03T12:00:00Z"}]"""
            assertTrue(c.refreshOpenConversation("peer", null, force = true))
            c.refreshOpenConversation("peer", null) // establish summary
            val saved = mem.writes.get()
            val historyCalls = server.counts["/v1/conversations/peer"]!!.get()
            repeat(3) { assertFalse(c.refreshOpenConversation("peer", null)) }
            assertEquals(historyCalls, server.counts["/v1/conversations/peer"]!!.get())
            assertEquals(saved, mem.writes.get())
            server.responses["/v1/conversations"] = """[{"peer":{"id":"peer"},"lastAt":"2026-11-03T12:00:06Z","unread":1}]"""
            server.responses["/v1/conversations/peer"] = """[{"id":"first","text":"Bonjour","createdAt":"2026-11-03T12:00:00Z"},{"id":"second","text":"Encore","createdAt":"2026-11-03T12:00:06Z"}]"""
            assertTrue(c.refreshOpenConversation("peer", null))
            assertEquals(listOf("first", "second"), c.db.messages.map { it.id })
            assertTrue(c.db.messages.all { it.read })
        }
    }

    @Test fun rotatedTokenIsPersistedBeforeRetry() = runBlocking {
        TestServer().use { server ->
            val mem = MemoryFiles(); val api = Api(server.url, mem)
            api.tokens = Tokens("old", "single-use")
            server.codes["/protected"] = 401
            server.responses["/v1/auth/refresh"] = """{"accessToken":"new","refreshToken":"successor"}"""
            assertFailsWith<ApiError> { api.get("/protected") }
            assertEquals("new", api.tokens!!.accessToken)
            assertEquals("successor", api.tokens!!.refreshToken)
            assertEquals(1, server.counts["/v1/auth/refresh"]!!.get())
        }
    }

    @Test fun networkCancellationIsNotReportedAsServerOutage() = runBlocking {
        TestServer().use { server ->
            server.gate = CountDownLatch(1)
            val api = Api(server.url, MemoryFiles())
            var outages = 0; api.onServerDown = { outages++ }
            val request = launch { api.get("/slow") }
            withContext(Dispatchers.IO) { assertTrue(server.entered.await(2, TimeUnit.SECONDS)) }
            request.cancelAndJoin()
            assertEquals(0, outages)
            assertEquals(1, api.cancelledCalls.get())
        }
    }

    @Test fun cancellationAlsoStopsAnInProgressResponseBody() = runBlocking {
        TestServer().use { server ->
            server.gate = CountDownLatch(1)
            val api = Api(server.url, MemoryFiles())
            val request = launch { api.bytes("/partial") }
            withContext(Dispatchers.IO) { assertTrue(server.bodyStarted.await(2, TimeUnit.SECONDS)) }
            withTimeout(1_000) { request.cancelAndJoin() }
            assertEquals(1, api.cancelledCalls.get())
        }
    }

    @Test fun backgroundLocalBootDoesNotStartNetworkOrPingLoop() = runBlocking {
        TestServer().use { server ->
            val mem = MemoryFiles(); val store = Store(mem)
            store.writeLocal(Db(session = "guest", users = mutableListOf(User("guest", "guest", "Guest", guest = true))))
            val job = SupervisorJob()
            try {
                val c = Cloud(store, Pg(store), Api(server.url, mem), mem, CoroutineScope(job + Dispatchers.Default))
                c.setForeground(false); c.boot()
                delay(50)
                assertEquals("guest", c.db.session)
                assertEquals(0, server.calls)
            } finally { job.cancelAndJoin() }
        }
    }

    @Test fun demoConversationDoesNotContactTheServer() = runBlocking {
        cloudTest { c, _, server ->
            c.store.use(Db(sandbox = true), false)
            assertFalse(c.refreshOpenConversation("demo-peer", null, force = true))
            assertEquals(0, server.calls)
        }
    }

    @Test fun encryptedStableChatPreservesPlaintextWithoutRepeatedSaves() = runBlocking {
        cloudTest { c, mem, server ->
            val key = E2e.newPairKey(); val keyId = uid()
            mem.set(E2eKeys.DEV_KEY, JSON.encodeToString(mapOf("me" to KeyStore(pairs = mutableMapOf(keyId to key)))))
            val payload = E2e.encrypt("Message privé", key, keyId)
            server.responses["/v1/conversations/peer"] = buildJsonArray {
                add(buildJsonObject { put("id", "encrypted"); put("createdAt", "2026-11-03T12:00:00Z"); put("e2e", JSON.encodeToJsonElement(payload)) })
            }.toString()
            assertTrue(c.openChat("peer"))
            assertEquals("Message privé", c.e2e.cache["encrypted"])
            val saved = mem.writes.get()
            assertFalse(c.openChat("peer"))
            assertEquals(saved, mem.writes.get())
            assertEquals("Message privé", c.e2e.cache["encrypted"])
        }
    }

    @Test fun vaultUploadIsCancelledWithItsParentAndDurableKeysRemainRetryable() = runBlocking {
        cloudTest { c, mem, server ->
            val keyId = uid()
            mem.set(E2eKeys.DEV_KEY, JSON.encodeToString(mapOf("me" to KeyStore(pairs = mutableMapOf(keyId to E2e.newPairKey()), vault = E2e.newPairKey()))))
            server.responses["/v1/e2e/devices"] = """{"deviceId":"device"}"""
            server.gatePath = "/v1/e2e/vault/keys"; server.gate = CountDownLatch(1)
            val operation = launch { c.e2e.device() }
            withTimeout(2_000) { while (server.counts["/v1/e2e/vault/keys"]?.get() != 1) delay(10) }
            assertEquals("device", c.e2e.store().device!!.id)
            assertTrue(keyId in c.e2e.store().pairs)
            withTimeout(1_000) { operation.cancelAndJoin() }
            assertFalse(keyId in c.e2e.store().vaulted)
            assertTrue(c.api.cancelledCalls.get() > 0)
            server.gate!!.countDown(); server.gate = null
            c.e2e.vaultPush()
            assertEquals(2, server.counts["/v1/e2e/vault/keys"]!!.get())
            assertTrue(keyId in c.e2e.store().vaulted)
        }
    }

    @Test fun thermalPolicyPreservesNormalQualityAndProtectsHotDevices() {
        assertEquals(0, EnergyPolicy.renderLevel(0))
        assertEquals(0, EnergyPolicy.renderLevel(1))
        assertTrue(EnergyPolicy.renderLevel(2) > 0)
        assertFalse(EnergyPolicy.allowInteractiveAi(3))
        assertTrue(EnergyPolicy.allowInteractiveAi(2))
        assertTrue(EnergyPolicy.allowBackgroundAi(true, false, false, 0))
        assertFalse(EnergyPolicy.allowBackgroundAi(false, false, false, 0))
        assertFalse(EnergyPolicy.allowBackgroundAi(true, true, false, 0))
        assertFalse(EnergyPolicy.allowBackgroundAi(true, false, true, 0))
        assertFalse(EnergyPolicy.allowBackgroundAi(true, false, false, 2))
    }

    /* ------------------------------------------------------------------ optimisation batterie et réseau (9.0.x) */

    private fun fakeClock(c: Cloud): LongArray { val t = longArrayOf(System.nanoTime() / 1_000_000); c.clock = { t[0] }; return t }

    @Test fun backgroundPollChangedDoesNotReloadFullSocial() = runBlocking {
        cloudTest { c, _, server ->
            var notified = 0
            c.events = object : CloudEvents { override fun serverNotifications(fresh: List<Notif>) { notified += fresh.size } }
            assertTrue(c.poll(0, foreground = false))
            assertEquals(4, server.calls, "Première relève d'arrière-plan : les quatre GET, rien d'autre")
            server.responses["/v1/notifications"] = """[{"id":"n1","type":"official","text":"Bonjour","createdAt":"2026-11-03T12:00:00Z"}]"""
            val before = server.calls
            assertTrue(c.poll(0, foreground = false))
            assertTrue(server.calls - before <= 4, "Un changement en arrière-plan ne déclenche aucun rechargement complet")
            assertEquals(0, server.count("/v1/me")); assertEquals(0, server.count("/v1/feed?tab=public")); assertEquals(0, server.count("/v1/stories"))
            assertEquals(1, notified)
            assertTrue(c.socialStale)
            assertEquals("n1", c.db.notifs.single().id)
        }
    }

    @Test fun adminBackgroundPollSkipsModeration() = runBlocking {
        cloudTest { c, _, server ->
            c.db.users.single().role = "admin"
            assertTrue(c.poll(0, foreground = false))
            server.responses["/v1/notifications"] = """[{"id":"m","type":"mod","text":"Signalement"}]"""
            assertTrue(c.poll(0, foreground = false))
            assertEquals(0, server.counts.keys.count { it.startsWith("/v1/admin") }, "Aucune requête d'administration en arrière-plan")
            // Au premier plan, l'administration revient (rechargement complet d'un compte administrateur)
            server.responses["/v1/me"] = """{"id":"me","username":"test","displayName":"Test","role":"admin"}"""
            c.refreshSocial()
            assertTrue(server.count("/v1/admin/users") == 1)
        }
    }

    @Test fun unreadConversationNotReloadedWhenUnchanged() = runBlocking {
        cloudTest { c, _, server ->
            server.responses["/v1/conversations"] = """[{"peer":{"id":"a"},"lastAt":"2026-11-03T12:00:00Z","unread":1},{"peer":{"id":"b"},"lastAt":"2026-11-03T12:00:00Z","unread":0}]"""
            server.responses["/v1/conversations/a"] = """[{"id":"a1","text":"Salut","createdAt":"2026-11-03T12:00:00Z"}]"""
            server.responses["/v1/conversations/b"] = """[{"id":"b1","text":"Hey","createdAt":"2026-11-03T12:00:00Z"}]"""
            assertTrue(c.poll(0, foreground = false))
            assertEquals(1, server.count("/v1/conversations/a"))
            // Seule la conversation b bouge : a (toujours non lue, mais inchangée) n'est pas retéléchargée
            server.responses["/v1/conversations"] = """[{"peer":{"id":"a"},"lastAt":"2026-11-03T12:00:00Z","unread":1},{"peer":{"id":"b"},"lastAt":"2026-11-03T12:05:00Z","unread":1}]"""
            server.responses["/v1/conversations/b"] = """[{"id":"b1","text":"Hey","createdAt":"2026-11-03T12:00:00Z"},{"id":"b2","text":"Tu es là ?","createdAt":"2026-11-03T12:05:00Z"}]"""
            assertTrue(c.poll(0, foreground = false))
            assertEquals(1, server.count("/v1/conversations/a"))
            assertEquals(2, server.count("/v1/conversations/b"))
            c.refreshSocial()
            assertEquals(1, server.count("/v1/conversations/a"), "Le rechargement complet n'y touche pas non plus")
        }
    }

    @Test fun pollAfterRestartUsesPersistedDigests() = runBlocking {
        cloudTest { c, mem, server ->
            assertTrue(c.poll())
            assertEquals(11, server.calls)
            // « Redémarrage du processus » : nouvelle couche connectée sur la base enregistrée
            val restarted = Cloud(c.store, Pg(c.store), Api(server.url, mem), mem, c.scope)
            assertFalse(restarted.poll(0, foreground = false))
            assertEquals(15, server.calls, "Quatre GET, aucun rechargement complet")
            assertFalse(restarted.poll(0))
            assertEquals(19, server.calls)
            assertEquals(1, server.count("/v1/me"))
        }
    }

    @Test fun failedPollBacksOff() = runBlocking {
        cloudTest { c, _, server ->
            server.codes["/v1/notifications"] = 500
            val delays = (1..3).map { assertFalse(c.poll(0, foreground = false)); PollPolicy.backoffMs(5 * 60_000L, c.pollFailures, kotlin.random.Random(0)) }
            assertEquals(3, c.pollFailures)
            assertTrue(delays[1] > delays[0] && delays[2] > delays[1], "Le délai double à chaque échec")
            assertTrue(delays.all { it <= 60 * 60_000L })
            server.codes.clear()
            assertTrue(c.poll(0, foreground = false))
            assertEquals(0, c.pollFailures, "Remis à zéro au premier succès")
        }
    }

    @Test fun foregroundAfterBackgroundRunsFullRefresh() = runBlocking {
        cloudTest { c, _, server ->
            c.refreshSocial()
            assertEquals(1, server.count("/v1/me"))
            server.responses["/v1/notifications"] = """[{"id":"n1","type":"friend","text":"Demande"}]"""
            assertTrue(c.poll(0, foreground = false))
            assertTrue(c.socialStale)
            c.foregroundRefresh()
            withTimeout(3_000) { while (c.socialStale) delay(20) }
            assertEquals(2, server.count("/v1/me"))
            c.foregroundRefresh(); delay(900)
            assertEquals(2, server.count("/v1/me"), "Un seul rechargement complet au retour")
        }
    }

    @Test fun conditionalRequestsReuseTheMemorisedBody() = runBlocking {
        cloudTest { c, _, server ->
            c.api.account = "me"
            server.responses["/v1/notifications"] = """[{"id":"n1","type":"official","text":"Bonjour"}]"""
            server.etags["/v1/notifications"] = "\"v1\""
            assertEquals(1, c.api.get("/v1/notifications").list().size)
            server.responses["/v1/notifications"] = "[]" // ignoré : le serveur répond 304
            assertEquals(1, c.api.get("/v1/notifications").list().size, "Corps mémorisé réutilisé sur 304")
            assertEquals(1, server.conditional["/v1/notifications"]?.get())
            c.api.get("/v1/conversations"); c.api.get("/v1/conversations")
            assertNull(server.conditional["/v1/conversations"], "Pas de requête conditionnelle sans ETag")
            c.api.account = "autre"
            c.api.get("/v1/notifications")
            assertEquals(1, server.conditional["/v1/notifications"]?.get(), "Changement de compte : cache vidé")
        }
    }

    @Test fun friendFeedUsesTheSlowTierInBackground() = runBlocking {
        cloudTest { c, _, server ->
            val t = fakeClock(c)
            repeat(6) { c.poll(0, foreground = false); t[0] += 5 * 60_000L }
            assertEquals(2, server.count("/v1/feed?tab=friends"), "Six cycles d'arrière-plan : deux relevés du flux d'amis")
            server.responses["/v1/notifications"] = """[{"id":"like1","type":"like","text":"Léa t'encourage","data":{"postId":"p"}}]"""
            c.poll(0, foreground = false); t[0] += 60_000L
            val before = server.count("/v1/feed?tab=friends")
            c.poll(0, foreground = false)
            assertEquals(before + 1, server.count("/v1/feed?tab=friends"), "Le cycle qui suit un encouragement relève le flux")
            c.feedEveryCycle = true
            c.poll(0, foreground = false)
            assertEquals(before + 2, server.count("/v1/feed?tab=friends"), "Niveau Rapide : à chaque cycle")
        }
    }

    @Test fun statusIsProbedAtMostEveryTenMinutesInBackground() = runBlocking {
        cloudTest { c, _, server ->
            val t = fakeClock(c)
            repeat(15) { c.poll(0, foreground = false); t[0] += 2 * 60_000L }
            assertTrue(server.count("/v1/status") <= 3, "30 min de relèves à 2 min : au plus trois statuts (${server.count("/v1/status")})")
            assertTrue(server.count("/v1/status") >= 2)
        }
    }

    @Test fun socialRefreshHasAValidityWindow() = runBlocking {
        cloudTest { c, _, server ->
            val t = fakeClock(c)
            c.scheduleSocial(0)
            withTimeout(3_000) { while (server.count("/v1/me") < 1) delay(10) }
            delay(300)
            t[0] += 10_000; c.scheduleSocial(0); delay(300)
            assertEquals(1, server.count("/v1/me"), "Deux demandes à 10 s : un seul rechargement")
            c.scheduleSocial(0, force = true)
            withTimeout(3_000) { while (server.count("/v1/me") < 2) delay(10) }
            delay(300)
            t[0] += 2 * 60_000; c.scheduleSocial(0, maxAgeMs = 5 * 60_000L); delay(300)
            assertEquals(2, server.count("/v1/me"), "Aujourd'hui : validité de 5 min")
        }
    }

    @Test fun coldStartRunsASingleFullRefresh() = runBlocking {
        cloudTest { c, _, server ->
            c.api.tokens = Tokens("access", "refresh"); c.store.rawSave()
            c.boot(); c.foregroundRefresh()
            delay(1_500)
            assertEquals(1, server.count("/v1/me"), "Démarrage à froid : un seul rechargement complet")
        }
    }

    @Test fun syncWithoutChangesDoesNotPullAgain() = runBlocking {
        cloudTest { c, _, server ->
            val t = fakeClock(c)
            val pulls = { server.counts.filterKeys { it.startsWith("/v1/sync/pull") }.values.sumOf { it.get() } }
            c.syncNow(); t[0] += 10_000; c.syncNow()
            assertEquals(1, pulls(), "Deux synchronisations sans opération à 10 s : un seul tirage")
            c.syncNow(force = true)
            assertEquals(2, pulls())
            // Arrière-plan : fenêtre de 30 min
            c.setForeground(false)
            t[0] += 20 * 60_000; c.syncNow()
            assertEquals(2, pulls())
            assertFalse(c.syncDue())
            t[0] += 11 * 60_000; assertTrue(c.syncDue()); c.syncNow()
            assertEquals(3, pulls())
        }
    }

    @Test fun stuckPullCursorStopsQuickly() = runBlocking {
        cloudTest { c, _, server ->
            server.responses["/v1/sync/pull?cursor=0"] = """{"hasMore":true,"cursor":0}"""
            c.syncNow(force = true)
            assertTrue(server.counts.filterKeys { it.startsWith("/v1/sync/pull") }.values.sumOf { it.get() } <= 2)
        }
    }

    @Test fun unchangedCycleDoesNotWriteAndNewMessageWritesOnce() = runBlocking {
        cloudTest { c, mem, server ->
            c.refreshSocial()
            val afterFirst = mem.writes.get()
            c.refreshSocial()
            assertEquals(afterFirst, mem.writes.get(), "Rechargement complet identique : aucune écriture")
            c.poll(0, foreground = false)
            assertEquals(afterFirst, mem.writes.get(), "Relève sans changement : aucune écriture")
            server.responses["/v1/conversations"] = """[{"peer":{"id":"peer"},"lastAt":"2026-11-03T12:00:00Z","unread":1}]"""
            server.responses["/v1/conversations/peer"] = """[{"id":"new","text":"Salut","createdAt":"2026-11-03T12:00:00Z"}]"""
            assertTrue(c.poll(0, foreground = false))
            assertEquals(afterFirst + 1, mem.writes.get(), "Un nouveau message : une écriture")
            assertEquals("new", c.db.messages.single().id)
        }
    }
}
