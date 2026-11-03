package fr.pepsgoal.core

import fr.pepsgoal.core.model.Db
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/** Ordered persistence of detached snapshots. Never touches the live model or secrets. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class DbWriter(private val files: DbFiles, scope: CoroutineScope) {
    private sealed interface Command
    private class Write(val name: String, val db: Db) : Command
    private class Delete(val name: String) : Command
    private class Flush(val done: CompletableDeferred<Unit>) : Command
    private val commands = Channel<Command>(Channel.UNLIMITED)
    val writes = AtomicLong()
    val bytes = AtomicLong()
    @Volatile var error: Exception? = null; private set

    init {
        scope.launch(Dispatchers.IO) {
            val pending = linkedMapOf<String, Db?>()
            val written = mutableMapOf<String, String>()
            fun persist() {
                for ((name, db) in pending.toMap()) {
                    try {
                        val raw = db?.let { JSON.encodeToString(Db.serializer(), it) }
                        if (raw == null) { files.delete(name); written.remove(name) }
                        else if (written[name] != raw) {
                            files.write(name, raw)
                            written[name] = raw; writes.incrementAndGet(); bytes.addAndGet(raw.toByteArray().size.toLong())
                        }
                        pending.remove(name)
                    } catch (e: Exception) { error = e }
                }
                if (pending.isEmpty()) error = null
            }
            for (command in commands) {
                when (command) {
                    is Write -> pending[command.name] = command.db
                    is Delete -> pending[command.name] = null
                    is Flush -> {
                        persist()
                        val failure = error
                        if (failure == null) command.done.complete(Unit) else command.done.completeExceptionally(failure)
                    }
                }
                // Coalesce only work already queued. No debounce or starvation while edits continue.
                if (commands.isEmpty) persist()
            }
            persist()
        }
    }

    fun save(name: String, db: Db) { check(commands.trySend(Write(name, db)).isSuccess) }
    fun delete(name: String) { check(commands.trySend(Delete(name)).isSuccess) }
    suspend fun flush() { val done = CompletableDeferred<Unit>(); commands.send(Flush(done)); done.await() }
}
