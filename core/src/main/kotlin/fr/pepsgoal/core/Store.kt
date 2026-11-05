package fr.pepsgoal.core

import fr.pepsgoal.core.model.Db
import fr.pepsgoal.core.model.snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import java.util.UUID

val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false; coerceInputValues = true }

fun uid(): String = UUID.randomUUID().toString()

/**
 * Persistance de la base de l'appareil : une base pour le profil sans compte (et la démonstration),
 * une autre pour le compte connecté (comme `pepsgoal.v1` et `pepsgoal.account.v1` de la version web).
 */
interface DbFiles {
    fun read(name: String): String?
    fun write(name: String, content: String)
    fun delete(name: String)
}

class Store(private val files: DbFiles, writeScope: CoroutineScope? = null) {
    private val writer = writeScope?.let { DbWriter(files, it) }
    // Owner-thread cache: reads cannot race pending account writes/deletions.
    private val snapshots = mutableMapOf<String, Db?>()
    val persistenceError get() = writer?.error
    val diskWrites get() = writer?.writes?.get() ?: 0L
    val diskBytes get() = writer?.bytes?.get() ?: 0L
    var db: Db = Db()
        private set
    /** Compte du serveur connecté (sinon : profil sans compte, entièrement local). */
    var acct = false
    /** En ligne = connexion de l'appareil ET serveur joignable ET mode hors ligne non forcé. */
    var online = true
    /** Révision de la base : incrémentée à chaque modification (l'interface s'y abonne). */
    var rev = 0L
        private set
    var onChange: (() -> Unit)? = null
    /** Appelé après chaque enregistrement d'un compte connecté : planifie la synchronisation. */
    var onSaved: (() -> Unit)? = null
    var bootWarning: String? = null

    private val fileName get() = if (acct) ACCOUNT else LOCAL

    fun load(name: String): Db? {
        if (snapshots.containsKey(name)) return snapshots[name]?.snapshot()
        val raw = files.read(name) ?: return null
        return try { JSON.decodeFromString(Db.serializer(), raw) } catch (e: Exception) {
            files.write("$name.corrompu.${nowMs()}", raw)
            bootWarning = "Tes données locales étaient illisibles : une copie a été mise de côté et une base vierge a été créée."
            null
        }
    }

    fun use(newDb: Db, account: Boolean) { db = newDb; acct = account; changed() }

    /** Enregistre la base et prévient l'interface. `sync` : planifie une synchronisation (compte). */
    fun commit(sync: Boolean = true) {
        if (!db.sandbox) save(fileName, db)
        changed()
        if (acct && sync) onSaved?.invoke()
    }

    /** Enregistrement sans planifier de synchronisation (données reçues du serveur). */
    fun rawSave() = commit(sync = false)

    fun changed() { rev++; onChange?.invoke() }

    fun readLocal(): Db? = load(LOCAL)
    private fun save(name: String, value: Db) {
        if (writer == null) files.write(name, JSON.encodeToString(Db.serializer(), value))
        else {
            val snapshot = value.snapshot()
            snapshots[name] = snapshot
            writer.save(name, snapshot)
        }
    }

    /** Wait for all preceding saves before completing a background operation. */
    suspend fun flush() { writer?.flush() }

    fun writeLocal(d: Db) = save(LOCAL, d)
    fun readAccount(): Db? = load(ACCOUNT)
    fun deleteAccount() {
        if (writer == null) files.delete(ACCOUNT)
        else { snapshots[ACCOUNT] = null; writer.delete(ACCOUNT) }
    }

    companion object {
        const val LOCAL = "pepsgoal.v1.json"
        const val ACCOUNT = "pepsgoal.account.v1.json"
    }
}
