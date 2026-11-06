package fr.pepsgoal.app.sys

import android.content.Context
import android.net.ConnectivityManager
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.*
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.ApiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Contenus téléchargeables (9.2) : catalogue du serveur (GET /v1/content/catalog), contenus complets et fichiers,
 * gardés dans filesDir/content. Relève à l'ouverture de la boutique ou d'Apparence (au plus toutes les 15 minutes)
 * et une fois par jour en arrière-plan sur réseau non limité ; requêtes conditionnelles (ETag), sha256 vérifié,
 * contenus validés avant usage. Hors ligne, le dernier catalogue reste affiché. Aucune boucle.
 */
object ContentSync {
    /** Un contenu prêt à afficher : entrée du catalogue, détail validé, pack (s'il en est un) et image locale. */
    class Entry(val item: CatalogItem, val detail: ContentDetail?, val spec: ThemePackSpec?, val image: File?)

    private val lock = Mutex()
    var entries by mutableStateOf<List<Entry>>(emptyList())
        private set
    /** Relève en cours. */
    var busy by mutableStateOf(false)
        private set
    /** Dernière relève impossible (pas de réseau, serveur injoignable) : le cache est affiché. */
    var offline by mutableStateOf(false)
        private set
    var lastCheck by mutableStateOf(0L)
        private set

    private fun root(ctx: Context) = File(ctx.filesDir, "content").apply { mkdirs() }
    private fun stateFile(ctx: Context) = AtomicFile(File(root(ctx), "catalog.json"))
    private fun itemDir(ctx: Context, id: String) = File(root(ctx), "items/${id.filter { it.isLetterOrDigit() || it == '-' }}")
    private fun detailFile(ctx: Context, id: String) = File(itemDir(ctx, id), "detail.json")
    fun file(ctx: Context, id: String, name: String): File? = if (ContentCatalog.safeFileName(name)) File(itemDir(ctx, id), name) else null

    private fun readState(ctx: Context): ContentCacheState = try {
        stateFile(ctx).let { if (it.baseFile.exists()) ContentCatalog.decodeState(String(it.readFully(), Charsets.UTF_8)) else ContentCacheState() }
    } catch (e: Exception) { ContentCacheState() }

    private fun writeState(ctx: Context, s: ContentCacheState) {
        val f = stateFile(ctx); val out = f.startWrite()
        try { out.write(ContentCatalog.encodeState(s).toByteArray(Charsets.UTF_8)); f.finishWrite(out) } catch (e: Exception) { f.failWrite(out) }
    }

    /** Lecture du cache au démarrage (sans réseau) : les packs obtenus restent applicables hors ligne. */
    fun load(ctx: Context) { publish(ctx, readState(ctx)) }

    /** Recalcule la vitrine et les packs en ligne connus du thème (« m:@<id> »), depuis le disque. */
    private fun publish(ctx: Context, s: ContentCacheState) {
        val list = s.items.filter { it.type in CONTENT_SUPPORTED_TYPES && s.local[it.id] == it.version }.mapNotNull { item ->
            try {
                val d = ContentCatalog.parseDetail(detailFile(ctx, item.id).readText())
                val spec = ContentCatalog.validate(d)
                val main = ContentCatalog.mainFile(d, spec)
                val img = main?.let { file(ctx, item.id, it.name) }?.takeIf { it.exists() && it.length() == main.size }
                Entry(item, d, spec, img)
            } catch (e: Exception) { null }
        }
        entries = list
        lastCheck = s.lastCheck
        PackLibrary.online = list.mapNotNull { it.spec }
    }

    private fun unmetered(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered.not()
    } catch (e: Exception) { false }

    /**
     * Relève du catalogue si elle est due ([background] : une fois par jour). [force] : bouton « Actualiser » (garde
     * tout de même 1 minute entre deux relèves). Les fichiers sont téléchargés tout de suite sur réseau non limité, sinon
     * à la demande (Obtenir, Appliquer).
     */
    suspend fun refresh(ctx: Context, background: Boolean = false, force: Boolean = false): Boolean = lock.withLock {
        val app = ctx.peps
        val api = app.cloud.api
        var s = readState(ctx)
        val now = nowMs()
        if (!force && !ContentCatalog.due(now, s.lastCheck, background)) { publish(ctx, s); return@withLock true }
        if (force && now - s.lastCheck in 0 until 60_000L) { publish(ctx, s); return@withLock true }
        if (api.forceOffline) { offline = true; publish(ctx, s); return@withLock false }
        busy = true
        try {
            withContext(Dispatchers.IO + NetSource("content")) {
                val full = s.cursor == null || s.items.isEmpty()
                val base = "/v1/content/catalog" + if (full) "" else "?since=" + URLEncoder.encode(s.cursor ?: "", "UTF-8")
                val pages = mutableListOf<CatalogPage>()
                var offset = 0; var etag: String? = null; var notModified = false
                for (i in 0 until ContentCatalog.MAX_PAGES) {
                    val path = if (offset == 0) base else base + (if ('?' in base) "&" else "?") + "offset=$offset"
                    val r = api.publicGet(path, if (offset == 0) s.etag else null)
                    if (r.code == 304 && offset == 0) { notModified = true; break }
                    if (r.code !in 200..299) throw ApiError(r.code, "error", "Catalogue indisponible.")
                    if (offset == 0) etag = r.etag
                    val page = ContentCatalog.parsePage(String(r.bytes, Charsets.UTF_8))
                    pages += page
                    offset = page.next ?: break
                }
                val before = s
                s = if (notModified) s.copy(lastCheck = now) else ContentCatalog.merge(s, pages, full, now).copy(etag = etag)
                ContentCatalog.removedSince(before, s).forEach { itemDir(ctx, it).deleteRecursively() }
                val wifi = unmetered(ctx)
                // Connexion perdue en cours de route : ce qui est déjà téléchargé est gardé, le reste attend la prochaine relève
                for (item in ContentCatalog.toDownload(s)) {
                    s = try { fetchDetail(ctx, s, item, files = wifi) } catch (e: ApiError) { break }
                }
                writeState(ctx, s)
            }
            offline = false
            true
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            offline = true
            false
        } finally { busy = false; publish(ctx, s) }
    }

    /** Télécharge et valide un contenu (et son image si [files]). Un contenu invalide est rejeté pour cette version. */
    private suspend fun fetchDetail(ctx: Context, s0: ContentCacheState, item: CatalogItem, files: Boolean): ContentCacheState {
        val api = ctx.peps.cloud.api
        var s = s0
        val r = api.publicGet("/v1/content/${item.id}", s.etags[item.id].takeIf { s.local[item.id] == item.version })
        if (r.code == 404) return s
        if (r.code !in 200..299) throw ApiError(r.code, "error", "Contenu indisponible.")
        val text = String(r.bytes, Charsets.UTF_8)
        val ok = try {
            val d = ContentCatalog.parseDetail(text)
            if (d.id != item.id || d.version != item.version) throw PackFormatError(listOf("Version inattendue."))
            val spec = ContentCatalog.validate(d)
            if (d.files.any { !ContentCatalog.safeUrl(it.url, api.base) }) throw PackFormatError(listOf("Adresse de fichier refusée."))
            itemDir(ctx, item.id).mkdirs()
            detailFile(ctx, item.id).writeText(ContentCatalog.encodeDetail(d))
            if (files) ContentCatalog.mainFile(d, spec)?.let { download(ctx, item.id, it) }
            true
        } catch (e: PackFormatError) { false } catch (e: kotlinx.serialization.SerializationException) { false } catch (e: IllegalArgumentException) { false }
        s = if (ok) s.copy(local = s.local + (item.id to item.version), etags = s.etags + listOfNotNull(r.etag?.let { item.id to it }))
            else s.copy(rejected = (s.rejected + "${item.id}@${item.version}").distinct())
        return s
    }

    /** Fichier vérifié (taille, sha256) avant d'être gardé ; refusé sinon. */
    private suspend fun download(ctx: Context, id: String, ref: ContentFileRef): Boolean {
        val target = file(ctx, id, ref.name) ?: return false
        if (target.exists() && target.length() == ref.size) return true
        val api = ctx.peps.cloud.api
        if (!ContentCatalog.safeUrl(ref.url, api.base) || ref.size > ContentCatalog.MAX_FILE) return false
        val r = api.publicGet(ref.url, long = true)
        if (r.code !in 200..299 || !ContentCatalog.verify(r.bytes, ref)) return false
        val tmp = File(target.parentFile, target.name + ".part")
        tmp.writeBytes(r.bytes)
        return tmp.renameTo(target)
    }

    /** Image d'un contenu, téléchargée à la demande (Obtenir, Appliquer) si elle manque encore. */
    suspend fun ensureImage(ctx: Context, e: Entry): File? {
        if (e.image != null) return e.image
        val d = e.detail ?: return null
        val ref = ContentCatalog.mainFile(d, e.spec) ?: return null
        val ok = try { withContext(Dispatchers.IO + NetSource("content")) { download(ctx, e.item.id, ref) } } catch (x: CancellationException) { throw x } catch (x: Exception) { false }
        if (ok) publish(ctx, readState(ctx))
        return if (ok) file(ctx, e.item.id, ref.name) else null
    }

    /** Packs téléchargés que l'utilisateur possède (Apparence › Mes packs). */
    fun ownedPacks(pg: Pg): List<Pair<ThemePackSpec, File?>> =
        entries.filter { it.spec != null && pg.ownsContent(it.item.id) }.map { it.spec!! to it.image }

    /** Fonds téléchargés possédés. */
    fun ownedBackgrounds(pg: Pg): List<Entry> = entries.filter { it.item.type == "background" && pg.ownsContent(it.item.id) }

    /* ---------- arrière-plan : une fois par jour, réseau non limité, batterie correcte ---------- */

    private const val WORK = "pepsgoal-content"

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<ContentWorker>(24, TimeUnit.HOURS, 6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

/** Relève quotidienne du catalogue ; jamais de nouvelle tentative rapprochée (le prochain passage suffit). */
class ContentWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try { ContentSync.refresh(applicationContext, background = true) } catch (e: CancellationException) { throw e } catch (e: Exception) {}
        return Result.success()
    }
}
