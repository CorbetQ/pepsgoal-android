package fr.pepsgoal.core

import fr.pepsgoal.core.model.Db
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

class Store(private val files: DbFiles) {
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
        if (!db.sandbox) files.write(fileName, JSON.encodeToString(Db.serializer(), db))
        changed()
        if (acct && sync) onSaved?.invoke()
    }

    /** Enregistrement sans planifier de synchronisation (données reçues du serveur). */
    fun rawSave() = commit(sync = false)

    fun changed() { rev++; onChange?.invoke() }

    fun readLocal(): Db? = load(LOCAL)
    fun writeLocal(d: Db) = files.write(LOCAL, JSON.encodeToString(Db.serializer(), d))
    fun readAccount(): Db? = load(ACCOUNT)
    fun deleteAccount() = files.delete(ACCOUNT)

    companion object {
        const val LOCAL = "pepsgoal.v1.json"
        const val ACCOUNT = "pepsgoal.account.v1.json"
    }
}
