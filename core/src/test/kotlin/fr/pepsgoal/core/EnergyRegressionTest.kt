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
                val body = (responses[path] ?: when (path) {
                    "/v1/me" -> """{"id":"me","username":"test","displayName":"Test"}"""
                    "/v1/feed?tab=friends", "/v1/feed?tab=public" -> """{"posts":[]}"""
                    "/v1/friends" -> """{"friends":[],"incoming":[],"outgoing":[]}"""
                    "/v1/status" -> """{"ok":true}"""
                    else -> "[]"
                }).toByteArray()
                ex.sendResponseHeaders(codes[path] ?: 200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            } catch (_: Exception) {} finally { ex.close() }
        }
        start()
    }
    val url get() = "http://127.0.0.1:${server.address.port}"
    val calls get() = counts.values.sumOf { it.get() }
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
}
