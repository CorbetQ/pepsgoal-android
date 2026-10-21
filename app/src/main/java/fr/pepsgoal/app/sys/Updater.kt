package fr.pepsgoal.app.sys

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import fr.pepsgoal.app.BuildConfig
import fr.pepsgoal.app.MainActivity
import fr.pepsgoal.app.R
import fr.pepsgoal.app.peps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Mises à jour intégrées : l'application consulte la dernière release GitHub de PepsGoal, télécharge l'APK
 * et lance son installation (les APK des releases sont signés avec la même clé : ils s'installent par-dessus).
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/CorbetQ/pepsgoal-android/releases/latest"
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    class Release(val version: String, val notes: String, val url: String, val size: Long, val page: String)

    /** Compare deux versions « 3.0.7 » : > 0 si a est plus récente que b. */
    fun compare(a: String, b: String): Int {
        val x = a.trimStart('v').split('.', '-').map { it.toIntOrNull() ?: 0 }; val y = b.trimStart('v').split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d }
        return 0
    }

    val current: String get() = BuildConfig.VERSION_NAME.removeSuffix("-debug")

    /** Dernière release publiée (null si GitHub est injoignable). */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").header("User-Agent", "PepsGoal-Android/${BuildConfig.VERSION_NAME}").build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val o = Json.parseToJsonElement(r.body!!.string()).jsonObject
                val apk = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it.str("name").endsWith(".apk") } ?: return@withContext null
                Release(o.str("tag_name").trimStart('v'), o.str("body").substringBefore("\n\nAPK ").trim(), apk.str("browser_download_url"),
                    apk["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L, o.str("html_url"))
            }
        } catch (e: Exception) { null }
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content ?: ""

    /** Télécharge l'APK de la release dans le cache de l'application, avec la progression (0 → 1). */
    suspend fun download(ctx: Context, r: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = File(dir, "PepsGoal-${r.version}.apk")
        http.newCall(Request.Builder().url(r.url).header("User-Agent", "PepsGoal-Android/${BuildConfig.VERSION_NAME}").build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Téléchargement impossible (${resp.code})")
            val body = resp.body!!; val total = body.contentLength().takeIf { it > 0 } ?: r.size
            body.byteStream().use { input -> f.outputStream().use { out ->
                val buf = ByteArray(64 * 1024); var done = 0L
                while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); done += n; if (total > 0) withContext(Dispatchers.Main) { progress((done.toFloat() / total).coerceIn(0f, 1f)) } }
            } }
        }
        f
    }

    /** Android autorise-t-il PepsGoal à installer des applications ? (sinon : ouvrir le réglage correspondant) */
    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Lance l'installateur d'Android sur l'APK téléchargé. */
    fun install(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Vérification automatique (au plus toutes les 6 heures) : notification si une nouvelle version est publiée. */
    suspend fun autoCheck(ctx: Context) {
        val prefs = ctx.peps.prefs
        if (!prefs.autoUpdate || System.currentTimeMillis() - prefs.updateChecked < 6 * 3_600_000L) return
        val r = latest() ?: return
        prefs.updateChecked = System.currentTimeMillis()
        if (compare(r.version, current) <= 0 || prefs.updateNotified == r.version) return
        prefs.updateNotified = r.version
        if (!Notifier.allowed(ctx)) return
        val pi = Notifier.open(ctx, 12) { putExtra(MainActivity.EXTRA_SETTINGS, "help") }
        val n = NotificationCompat.Builder(ctx, Notifier.CH_OFFICIAL).setSmallIcon(R.drawable.ic_notif).setColor(0xFFFF4A12.toInt())
            .setContentTitle("PepsGoal ${r.version} est disponible").setContentText("Touche pour mettre à jour l’application en un instant.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(r.notes.take(600))).setContentIntent(pi).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(12, n) } catch (e: SecurityException) {}
    }
}
