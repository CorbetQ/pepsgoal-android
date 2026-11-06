package fr.pepsgoal.app.sys

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ui.Media
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Boutique des créateurs et « PepsGoal Style » (10.0), côté appareil : envoi des photos au serveur (qui fait tous les
 * calculs), fichiers du pack (image, premier plan détouré), publication, relève des ventes (100 % au créateur) et
 * collection retrouvée. Rien ne se calcule sur le téléphone : créer un pack demande une connexion et un compte.
 */
object Creator {
    /** Côté le plus long de la photo envoyée (le serveur travaille à 1600 px au plus). */
    private const val UPLOAD_SIDE = 1600

    var styleBusy by mutableStateOf(false)
        private set
    var publishing by mutableStateOf(false)
        private set
    /** Mes packs en boutique (dernière relève). */
    var items by mutableStateOf<List<CreatorItem>>(emptyList())
        private set
    var itemsError by mutableStateOf<String?>(null)
        private set
    var itemsLoaded by mutableStateOf(false)
        private set

    private fun dir(ctx: Context) = File(ctx.filesDir, "packs").apply { mkdirs() }
    /** Premier plan détouré d'un pack personnel. */
    fun fgFile(ctx: Context, id: String) = File(dir(ctx), "$id.fg.webp")
    private fun remoteFile(ctx: Context, id: String) = File(dir(ctx), "$id.remote.json")

    fun remote(ctx: Context, id: String): PackRemote = try {
        remoteFile(ctx, id).takeIf { it.exists() }?.readText()?.let { MARKET_JSON.decodeFromString(PackRemote.serializer(), it) } ?: PackRemote()
    } catch (e: Exception) { PackRemote() }

    private fun saveRemote(ctx: Context, id: String, r: PackRemote) {
        try { remoteFile(ctx, id).writeText(MARKET_JSON.encodeToString(PackRemote.serializer(), r)) } catch (e: Exception) {}
    }

    /** Copie et suppression des fichiers annexes d'un pack (premier plan, fichiers déjà envoyés). */
    fun copyExtras(ctx: Context, from: String, to: String) {
        fgFile(ctx, from).takeIf { it.exists() }?.let { try { it.copyTo(fgFile(ctx, to), overwrite = true) } catch (e: Exception) {} }
    }
    fun deleteExtras(ctx: Context, id: String) { fgFile(ctx, id).delete(); remoteFile(ctx, id).delete() }

    /** Création possible : compte connecté et réseau disponible (tous les calculs sont faits par le serveur). */
    fun blocker(ui: AppState): String? = when {
        !ui.pg.store.acct -> "Connecte-toi à ton compte PepsGoal : la création de thème passe par le serveur."
        ui.cloud.forceOffline -> "Mode hors ligne activé : repasse en ligne dans Paramètres › Données pour créer un thème."
        !ui.pg.store.online -> "Pas de connexion : la création de thème a besoin d’internet, les calculs sont faits par le serveur PepsGoal."
        else -> null
    }

    /** Téléchargement vérifié (taille, sha256) d'un fichier produit par le serveur. */
    private suspend fun fetch(ui: AppState, ref: ContentFileRef, target: File): Boolean {
        val api = ui.cloud.api
        if (!ContentCatalog.safeUrl(ref.url, api.base) || ref.size > ContentCatalog.MAX_FILE) return false
        val r = api.publicGet(ref.url, long = true)
        if (r.code !in 200..299 || !ContentCatalog.verify(r.bytes, ref)) return false
        val tmp = File(target.parentFile, target.name + ".part")
        tmp.writeBytes(r.bytes)
        return tmp.renameTo(target) || run { target.delete(); tmp.renameTo(target) }
    }

    /**
     * « PepsGoal Style » : la photo [uri] est réduite, envoyée au serveur, qui la stylise ([stylize]) et découpe son
     * ciel ([weather]). Le résultat devient l'image du pack [packId] (et son premier plan). Renvoie le résultat ou
     * lève une erreur lisible.
     */
    suspend fun style(ui: AppState, packId: String, uri: Uri, stylize: Boolean, weather: Boolean, strength: Float): StyleResult {
        blocker(ui)?.let { throw ApiError(0, "offline", it) }
        val ctx = ui.app
        styleBusy = true
        try {
            val jpeg = withContext(Dispatchers.IO) { Media.decode(ctx, uri, UPLOAD_SIDE)?.let { Media.jpeg(it, 88) } }
                ?: throw ApiError(0, "image", "Impossible de lire cette photo. Essaie un fichier JPG ou PNG.")
            val r = withContext(Dispatchers.IO + NetSource("style")) { ui.cloud.styleImage(jpeg, stylize, weather, strength) }
            val ok = withContext(Dispatchers.IO + NetSource("style")) {
                val img = fetch(ui, r.image, Packs.imageFile(ctx, packId))
                val fg = r.foreground?.let { fetch(ui, it, fgFile(ctx, packId)) } ?: run { fgFile(ctx, packId).delete(); true }
                img && fg
            }
            if (!ok) throw ApiError(0, "download", "Le résultat n’a pas pu être téléchargé : réessaie.")
            saveRemote(ctx, packId, remote(ctx, packId).copy(image = r.image, foreground = r.foreground))
            return r
        } finally { styleBusy = false }
    }

    /** Photo simple (onglet « Photo ») : sa trace sur le serveur est oubliée, elle sera envoyée à la publication. */
    fun photoChanged(ctx: Context, packId: String) {
        fgFile(ctx, packId).delete()
        saveRemote(ctx, packId, remote(ctx, packId).copy(image = null, foreground = null))
    }

    /** Fichier déjà sur le serveur (même empreinte) ou envoyé maintenant. */
    private suspend fun ensureUploaded(ui: AppState, f: File, known: ContentFileRef?, mime: String): ContentFileRef {
        val bytes = withContext(Dispatchers.IO) { f.readBytes() }
        if (known != null && ContentCatalog.verify(bytes, known)) return known
        return withContext(Dispatchers.IO + NetSource("creator")) { ui.cloud.uploadPackMedia(bytes, mime) }
    }

    /**
     * Publie (ou met à jour) un pack personnel dans la boutique : photo et premier plan envoyés s'il le faut, pack sans
     * icône ni effet (achetés par le créateur, pas par l'acheteur). Renvoie le pack en boutique.
     */
    suspend fun publish(ui: AppState, spec: ThemePackSpec, description: String, price: Int): CreatorItem {
        blocker(ui)?.let { throw ApiError(0, "offline", it) }
        val ctx = ui.app
        val img = Packs.imageFile(ctx, spec.id).takeIf { spec.wallpaper.kind == "image" && it.exists() }
        Market.publishProblem(spec, img != null)?.let { throw ApiError(0, "invalid", it) }
        publishing = true
        try {
            var rem = remote(ctx, spec.id)
            val files = mutableListOf<ContentFileRef>()
            var imgName: String? = null; var fgName: String? = null
            if (img != null) {
                val ref = ensureUploaded(ui, img, rem.image, "image/jpeg"); rem = rem.copy(image = ref)
                imgName = "fond." + ref.url.substringAfterLast('.', "jpg"); files += ref.copy(name = imgName)
                val fg = fgFile(ctx, spec.id).takeIf { it.exists() && spec.wallpaper.foreground != null }
                if (fg != null) {
                    val fref = ensureUploaded(ui, fg, rem.foreground, "image/webp"); rem = rem.copy(foreground = fref)
                    fgName = "premier-plan." + fref.url.substringAfterLast('.', "webp"); files += fref.copy(name = fgName)
                }
            }
            val content = Market.publishable(spec, imgName, fgName)
            val existing = rem.contentId?.let { id -> items.firstOrNull { it.id == id } ?: if (itemsLoaded) null else CreatorItem(id, spec.name) }
            val out = withContext(Dispatchers.IO + NetSource("creator")) {
                if (existing != null) try { ui.cloud.updatePublishedPack(existing.id, spec.name, description, price, content, files) }
                    catch (e: ApiError) { if (e.status == 404) ui.cloud.publishPack(spec.name, description, price, content, files) else throw e }
                else ui.cloud.publishPack(spec.name, description, price, content, files)
            }
            saveRemote(ctx, spec.id, rem.copy(contentId = out.id))
            items = listOf(out) + items.filterNot { it.id == out.id }
            return out
        } finally { publishing = false }
    }

    /** Pack en boutique correspondant à un pack personnel, s'il a été publié depuis cet appareil. */
    fun publishedOf(ctx: Context, packId: String): CreatorItem? = remote(ctx, packId).contentId?.let { id -> items.firstOrNull { it.id == id } }

    suspend fun refreshItems(ui: AppState) {
        if (blocker(ui) != null) { itemsError = blocker(ui); return }
        try {
            items = withContext(Dispatchers.IO + NetSource("creator")) { ui.cloud.myCreatorItems() }
            itemsError = null; itemsLoaded = true
        } catch (e: ApiError) { itemsError = e.message }
    }

    suspend fun retire(ui: AppState, id: String) {
        withContext(Dispatchers.IO + NetSource("creator")) { ui.cloud.retirePack(id) }
        items = items.filterNot { it.id == id }
    }

    private const val SALES_CURSOR = "mktSalesCursor"
    private const val RESTORE_AT = "mktRestoreAt"

    /**
     * Relève des ventes : chaque vente est créditée une seule fois (100 % du prix). Renvoie les flammes nouvellement
     * reçues. Sans compte ou hors ligne : rien.
     */
    suspend fun syncSales(ui: AppState): Int {
        if (blocker(ui) != null) return 0
        val raw = ui.prefs.raw
        var since = raw.getString(SALES_CURSOR, null)
        var got = 0
        try {
            withContext(Dispatchers.IO + NetSource("creator")) {
                repeat(5) {
                    val page = ui.cloud.creatorSales(since)
                    got += ui.pg.creditSales(page.sales).sumOf { it.price }
                    if (page.cursor != null) since = page.cursor
                    if (page.sales.size < 200) return@withContext
                }
            }
        } catch (e: ApiError) { return got }
        since?.let { raw.edit().putString(SALES_CURSOR, it).apply() }
        if (got > 0) { ui.bump(); ui.toast("Tes packs se vendent : +${fmtNum(got)} flammes !", "star") }
        return got
    }

    /** Collection retrouvée (nouvel appareil, réinstallation) : une fois par jour au plus. */
    suspend fun restorePurchases(ui: AppState, force: Boolean = false) {
        if (blocker(ui) != null) return
        val raw = ui.prefs.raw; val now = nowMs()
        if (!force && now - raw.getLong(RESTORE_AT, 0L) in 0 until 86_400_000L) return
        try {
            val owned = withContext(Dispatchers.IO + NetSource("creator")) { ui.cloud.myContentPurchases() }
            raw.edit().putLong(RESTORE_AT, now).apply()
            if (ui.pg.restorePurchases(owned) > 0) ui.bump()
        } catch (e: ApiError) {}
    }

    /**
     * Achat dans la boutique en ligne : le serveur enregistre l'achat (100 % au créateur), puis le porte-monnaie de
     * l'appareil est débité. Un contenu officiel reste achetable hors ligne (ancien fonctionnement) ; celui d'un
     * créateur demande une connexion. Renvoie un message d'erreur, ou null.
     */
    suspend fun buy(ui: AppState, item: CatalogItem): String? {
        val pg = ui.pg
        if (pg.ownsContent(item.id) || pg.ownsOwnPack(item)) return null
        if (item.price > 0 && pg.wallet() < item.price) return "Il te manque ${item.price - pg.wallet()} flammes. Valide tes objectifs pour en gagner !"
        val block = blocker(ui)
        if (block != null && item.creatorId != null) return "Connexion nécessaire pour acheter le pack d’un créateur."
        if (block == null) try {
            withContext(Dispatchers.IO + NetSource("creator")) { ui.cloud.purchaseContent(item.id) }
        } catch (e: ApiError) {
            if (e.status == 409) return null
            return e.message ?: "Achat impossible pour le moment."
        }
        return pg.obtainContent(item.id, item.price)
    }
}
