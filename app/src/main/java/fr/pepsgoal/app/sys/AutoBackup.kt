package fr.pepsgoal.app.sys

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import fr.pepsgoal.app.data.Journal
import fr.pepsgoal.app.data.JournalEntry
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import fr.pepsgoal.core.crypto.E2e
import fr.pepsgoal.core.model.Db
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import java.util.concurrent.TimeUnit

/**
 * Sauvegarde automatique chiffrée : chaque jour, une copie des données (objectifs, validations, journal) est écrite
 * dans le dossier choisi (stockage du téléphone, Google Drive…), chiffrée par le mot de passe de sauvegarde.
 * La clé dérivée est gardée dans le Keystore d'Android : le mot de passe lui-même n'est jamais enregistré.
 */
object AutoBackup {
    private const val K_KEY = "backup.key"; private const val K_SALT = "backup.salt"
    private const val KEEP = 7

    fun configured(ctx: Context) = ctx.peps.prefs.backupUri.isNotEmpty() && ctx.peps.secrets.get(K_KEY) != null

    /** Mise en place : mot de passe, dossier (accès conservé), première sauvegarde et planification quotidienne. */
    suspend fun setup(ctx: Context, tree: Uri, password: String): Boolean {
        val app = ctx.peps
        try { ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (e: Exception) {}
        val salt = Backup.newSalt(); val key = withContext(Dispatchers.Default) { Backup.key(password, salt) }
        app.secrets.set(K_KEY, E2e.b64(key)); app.secrets.set(K_SALT, salt)
        app.prefs.backupUri = tree.toString()
        schedule(ctx)
        return run(ctx)
    }

    fun disable(ctx: Context) {
        val app = ctx.peps
        app.secrets.set(K_KEY, null); app.secrets.set(K_SALT, null); app.prefs.backupUri = ""
        WorkManager.getInstance(ctx).cancelUniqueWork("pepsgoal-backup")
    }

    fun schedule(ctx: Context) {
        if (!configured(ctx)) return
        val req = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("pepsgoal-backup", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Contenu sauvegardé : la base de l'appareil et le journal. */
    private fun payload(ctx: Context): String {
        val app = ctx.peps
        return buildJsonObject {
            put("app", APP_VERSION); put("account", app.store.acct)
            put("db", JSON.encodeToJsonElement(Db.serializer(), app.pg.db))
            put("journal", JSON.encodeToJsonElement(ListSerializer(JournalEntry.serializer()), Journal.all(ctx)))
        }.toString()
    }

    /** Écrit la sauvegarde du jour (remplace celle du même jour) et ne garde que les 7 dernières. */
    suspend fun run(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val app = ctx.peps
        val key = app.secrets.get(K_KEY)?.let { E2e.unb64(it) } ?: return@withContext false
        val salt = app.secrets.get(K_SALT) ?: return@withContext false
        val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(app.prefs.backupUri)) ?: return@withContext false
        if (app.pg.db.sandbox || app.pg.me() == null) return@withContext false
        try {
            val name = "PepsGoal-sauvegarde-${today()}.pgbak"
            dir.findFile(name)?.delete()
            val f = dir.createFile("application/octet-stream", name) ?: return@withContext false
            val raw = withContext(Dispatchers.Main) { payload(ctx) }
            ctx.contentResolver.openOutputStream(f.uri, "wt")?.use { it.write(Backup.seal(raw, key, salt).toByteArray(Charsets.UTF_8)) } ?: return@withContext false
            dir.listFiles().filter { it.name?.startsWith("PepsGoal-sauvegarde-") == true }.sortedByDescending { it.name }.drop(KEEP).forEach { it.delete() }
            app.prefs.backupLast = nowMs(); app.prefs.backupError = ""
            true
        } catch (e: Exception) { app.prefs.backupError = e.message ?: "Écriture impossible"; false }
    }

    /** Restaure un fichier de sauvegarde (fusion : rien n'est effacé). Renvoie le nombre d'objectifs ajoutés, ou null si le mot de passe est faux. */
    suspend fun restore(ctx: Context, file: Uri, password: String): Int? {
        val app = ctx.peps
        val raw = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(file)?.use { it.readBytes().toString(Charsets.UTF_8) } } ?: return null
        val plain = withContext(Dispatchers.Default) { Backup.open(raw, password) } ?: return null
        val o = JSON.parseToJsonElement(plain).jsonObject
        val db = JSON.decodeFromJsonElement(Db.serializer(), o["db"] ?: return null)
        val n = Backup.merge(app.pg, db)
        (o["journal"] as? JsonArray)?.let { arr ->
            val have = Journal.all(ctx).map { it.date }.toSet()
            JSON.decodeFromJsonElement(ListSerializer(JournalEntry.serializer()), arr).filter { it.date !in have }.forEach { Journal.put(ctx, it.date, it.mood, it.text) }
        }
        app.pg.hooks.liveChanged()
        return n
    }
}

class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = if (AutoBackup.run(applicationContext)) Result.success() else Result.retry()
}
