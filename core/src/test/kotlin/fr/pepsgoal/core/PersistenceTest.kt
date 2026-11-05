package fr.pepsgoal.core

import fr.pepsgoal.core.model.*
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

@OptIn(ExperimentalSerializationApi::class)
class PersistenceTest {
    // Fill every serialized constructor field, including nullable/default fields. A newly added
    // field omitted by Snapshot.kt must fail equality rather than silently take its default.
    private fun fixture(d: SerialDescriptor): JsonElement = when {
        d.serialName.removeSuffix("?") == "kotlinx.serialization.json.JsonElement" -> buildJsonObject { put("nested", buildJsonArray { add("value") }) }
        d.serialName.removeSuffix("?") == "kotlinx.serialization.json.JsonObject" -> buildJsonObject { put("nested", buildJsonArray { add("value") }) }
        d.kind == PrimitiveKind.BOOLEAN -> JsonPrimitive(true)
        d.kind == PrimitiveKind.STRING || d.kind == PrimitiveKind.CHAR -> JsonPrimitive("fixture")
        d.kind is PrimitiveKind -> JsonPrimitive(7)
        d.kind == StructureKind.LIST -> JsonArray(listOf(fixture(d.getElementDescriptor(0))))
        d.kind == StructureKind.MAP -> JsonObject(mapOf("key" to fixture(d.getElementDescriptor(1))))
        d.kind == StructureKind.CLASS -> JsonObject((0 until d.elementsCount).associate { d.getElementName(it) to fixture(d.getElementDescriptor(it)) })
        else -> error("Unhandled model shape: ${d.serialName}")
    }

    @Test fun snapshotCoversEntireSchemaAndDoesNotAliasMutableFields() {
        val db = JSON.decodeFromJsonElement(Db.serializer(), fixture(Db.serializer().descriptor))
        val copy = db.snapshot()
        val expected = JSON.encodeToString(Db.serializer(), db)
        assertEquals(expected, JSON.encodeToString(Db.serializer(), copy))
        db.users.single().name = "changed"
        db.users.single().settings.morning.on = false
        db.users.single().places.single().name = "changed"
        db.users.single().rewards.single().target = 100
        db.users.single().goalGroupOf.clear()
        db.goals.single().data.clear()
        db.goals.single().members!!.clear()
        db.messages.single().readBy.clear()
        db.posts.single().poll!!.options.single().n = 100
        db.cloud!!.snapGoals.clear()
        db.timers.values.single().goalId = "changed"
        assertEquals(expected, JSON.encodeToString(Db.serializer(), copy))
    }

    private class Files : DbFiles {
        val data = ConcurrentHashMap<String, String>()
        val writes = AtomicInteger()
        @Volatile var fail = false
        @Volatile var writeThread = ""
        override fun read(name: String) = data[name]
        override fun write(name: String, content: String) {
            if (fail) throw IOException("synthetic disk failure")
            writeThread = Thread.currentThread().name
            data[name] = content; writes.incrementAndGet()
        }
        override fun delete(name: String) { if (fail) throw IOException("synthetic delete failure"); data.remove(name) }
    }

    @Test fun writerPersistsDetachedValuesInOrderAndDeduplicates() = runBlocking {
        val job = SupervisorJob(); val scope = CoroutineScope(job + Dispatchers.Default)
        try {
            val files = Files(); val store = Store(files, scope)
            store.db.session = "first"; store.commit()
            store.db.session = "second" // not committed; cannot leak into the queued snapshot
            store.flush()
            assertEquals("first", JSON.decodeFromString(Db.serializer(), files.read(Store.LOCAL)!!).session)
            store.commit(); store.flush()
            val count = files.writes.get()
            store.rawSave(); store.flush()
            assertEquals(count, files.writes.get())
            assertEquals("second", JSON.decodeFromString(Db.serializer(), files.read(Store.LOCAL)!!).session)
            assertNotEquals(Thread.currentThread().name, files.writeThread)
        } finally { job.cancelAndJoin() }
    }

    @Test fun accountReadsSeeQueuedSaveAndDeleteNeverResurrectsIt() = runBlocking {
        val job = SupervisorJob(); val scope = CoroutineScope(job + Dispatchers.Default)
        try {
            val files = Files(); val store = Store(files, scope)
            store.db.session = "local"; store.commit()
            store.use(Db(session = "account"), true); store.commit()
            val cached = store.readAccount()!!
            assertEquals("account", cached.session)
            cached.session = "mutated copy"
            assertEquals("account", store.readAccount()!!.session)
            store.deleteAccount(); assertNull(store.readAccount())
            store.flush()
            assertNull(files.read(Store.ACCOUNT))
            assertEquals("local", JSON.decodeFromString(Db.serializer(), files.read(Store.LOCAL)!!).session)
            store.use(Db(session = "new account"), true); store.commit(); store.flush()
            assertEquals("new account", JSON.decodeFromString(Db.serializer(), files.read(Store.ACCOUNT)!!).session)
        } finally { job.cancelAndJoin() }
    }

    @Test fun failedWriteAndDeleteRemainRetryableAndFlushReportsFailure() = runBlocking {
        val job = SupervisorJob(); val scope = CoroutineScope(job + Dispatchers.Default)
        try {
            val files = Files(); val store = Store(files, scope)
            files.fail = true
            store.use(Db(session = "account"), true); store.commit()
            assertFailsWith<IOException> { store.flush() }
            assertNotNull(store.persistenceError)
            files.fail = false; store.flush()
            assertNull(store.persistenceError)
            assertNotNull(files.read(Store.ACCOUNT))
            files.fail = true; store.deleteAccount()
            assertFailsWith<IOException> { store.flush() }
            files.fail = false; store.flush()
            assertNull(files.read(Store.ACCOUNT))
        } finally { job.cancelAndJoin() }
    }

    @Test fun sandboxStillNeverWritesToDisk() = runBlocking {
        val job = SupervisorJob()
        try {
            val files = Files(); val store = Store(files, CoroutineScope(job + Dispatchers.Default))
            store.use(Db(sandbox = true), false); store.commit(); store.flush()
            assertEquals(0, files.writes.get())
        } finally { job.cancelAndJoin() }
    }
}
