package fr.pepsgoal.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

/* ---------------------------------------------------------------- Contenus téléchargeables (9.2) */

/*
 * Catalogue des contenus publiés sur le serveur (GET /v1/content/catalog, docs/content-api.md du serveur) : logique
 * pure ici (fusion, choix des téléchargements, vérification sha256, validation), réseau et disque dans l'application
 * (ContentSync). Aucune relève en boucle : à l'ouverture de la boutique ou d'Apparence, au plus toutes les 15 minutes,
 * et une fois par jour en arrière-plan sur réseau non limité.
 */

/** Types que cette version sait afficher ; les autres (autocollants, icônes) sont ignorés sans erreur. */
val CONTENT_SUPPORTED_TYPES = setOf("theme_pack", "background")

@Serializable
data class ContentFileRef(val name: String, val url: String, val sha256: String, val size: Long, val mime: String = "image/jpeg")

@Serializable
data class CatalogItem(
    val id: String, val type: String, val slug: String = "", val version: Int = 1, val name: String, val description: String = "",
    val author: String = "", val price: Int = 0, val size: Long = 0, val updatedAt: String? = null, val publishedAt: String? = null,
)

@Serializable
data class CatalogPage(val schemaVersion: Int = 1, val items: List<CatalogItem> = emptyList(), val removed: List<String> = emptyList(),
                       val cursor: String? = null, val next: Int? = null)

@Serializable
data class ContentDetail(
    val id: String, val type: String, val version: Int = 1, val name: String, val description: String = "", val author: String = "",
    val price: Int = 0, val content: JsonObject = JsonObject(emptyMap()), val files: List<ContentFileRef> = emptyList(),
)

/** État gardé sur le disque (filesDir/content/catalog.json) : catalogue, curseur, ETag et versions déjà téléchargées. */
@Serializable
data class ContentCacheState(
    val cursor: String? = null,
    val etag: String? = null,
    val items: List<CatalogItem> = emptyList(),
    /** Version téléchargée et validée de chaque contenu. */
    val local: Map<String, Int> = emptyMap(),
    /** ETag du contenu complet de chaque contenu. */
    val etags: Map<String, String> = emptyMap(),
    /** Contenus rejetés (« id@version ») : pas retéléchargés tant que leur version ne change pas. */
    val rejected: List<String> = emptyList(),
    val lastCheck: Long = 0,
)

private val CONTENT_JSON = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

object ContentCatalog {
    /** Intervalle minimal entre deux relèves au premier plan (ouverture de la boutique, d'Apparence). */
    const val FOREGROUND_MIN_MS = 15 * 60_000L
    /** Arrière-plan : au plus une fois par jour (marge pour le placement flexible de WorkManager). */
    const val BACKGROUND_MIN_MS = 20 * 3_600_000L
    /** Pages lues au plus par relève (catalogue borné, jamais de boucle infinie). */
    const val MAX_PAGES = 10
    /** Taille maximale d'un fichier téléchargé. */
    const val MAX_FILE = 15L * 1024 * 1024

    fun due(now: Long, lastCheck: Long, background: Boolean): Boolean =
        lastCheck <= 0 || now - lastCheck >= (if (background) BACKGROUND_MIN_MS else FOREGROUND_MIN_MS) || now < lastCheck

    fun parsePage(text: String): CatalogPage = CONTENT_JSON.decodeFromString(CatalogPage.serializer(), text)
    fun parseDetail(text: String): ContentDetail = CONTENT_JSON.decodeFromString(ContentDetail.serializer(), text)
    fun encodeState(s: ContentCacheState): String = CONTENT_JSON.encodeToString(ContentCacheState.serializer(), s)
    fun decodeState(text: String?): ContentCacheState = try { if (text.isNullOrBlank()) ContentCacheState() else CONTENT_JSON.decodeFromString(ContentCacheState.serializer(), text) } catch (e: Exception) { ContentCacheState() }
    fun encodeDetail(d: ContentDetail): String = CONTENT_JSON.encodeToString(ContentDetail.serializer(), d)

    /**
     * Fusionne les pages reçues. [full] : relève complète (sans `since`) : la liste remplace l'ancienne. Sinon les
     * changements s'ajoutent et les retraits disparaissent. Le curseur est le plus grand des pages (jamais remis à null).
     */
    fun merge(state: ContentCacheState, pages: List<CatalogPage>, full: Boolean, now: Long): ContentCacheState {
        val fresh = pages.flatMap { it.items }.filter { it.id.isNotBlank() }
        val removed = pages.flatMap { it.removed }.toSet()
        val items = if (full) fresh.associateBy { it.id } else (state.items.associateBy { it.id } + fresh.associateBy { it.id }) - removed
        val gone = state.items.map { it.id }.toSet() - items.keys
        val cursor = (pages.mapNotNull { it.cursor } + listOfNotNull(state.cursor)).maxOrNull()
        return state.copy(cursor = cursor, items = items.values.sortedByDescending { it.publishedAt ?: it.updatedAt ?: "" },
            local = state.local - gone, etags = state.etags - gone, lastCheck = now,
            rejected = state.rejected.filter { r -> r.substringBefore('@') in items.keys })
    }

    /** Contenus à (re)télécharger : types connus, version différente de la version locale, pas déjà rejetés. */
    fun toDownload(state: ContentCacheState): List<CatalogItem> = state.items.filter {
        it.type in CONTENT_SUPPORTED_TYPES && state.local[it.id] != it.version && "${it.id}@${it.version}" !in state.rejected
    }

    /** Identifiants retirés du catalogue depuis l'état [before] : leurs fichiers locaux sont effacés. */
    fun removedSince(before: ContentCacheState, after: ContentCacheState): Set<String> = before.items.map { it.id }.toSet() - after.items.map { it.id }.toSet()

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Le fichier reçu est-il bien celui annoncé (taille et empreinte) ? */
    fun verify(bytes: ByteArray, ref: ContentFileRef): Boolean = bytes.size.toLong() == ref.size && sha256Hex(bytes).equals(ref.sha256, ignoreCase = true)

    /** Adresse de téléchargement acceptée : HTTPS, ou la même origine que le serveur PepsGoal (développement). */
    fun safeUrl(url: String, apiBase: String): Boolean {
        val u = url.trim()
        if (u.startsWith("https://") && u.length > 8 && !u.contains(' ')) return true
        val origin = apiBase.trimEnd('/').split('/').take(3).joinToString("/")
        return origin.isNotEmpty() && u.startsWith("$origin/")
    }

    /** Nom de fichier local sûr (aucun chemin). */
    fun safeFileName(name: String): Boolean = Regex("^[\\w.-]{1,128}$").matches(name) && !name.contains("..")

    /**
     * Valide un contenu téléchargé avant usage. Pack de thème : le format de [PackFormat] (même validation qu'un
     * import), identifiant = celui du contenu, image citée parmi les fichiers. Fond : une image jointe.
     * Lève [PackFormatError] si le contenu est invalide.
     */
    fun validate(d: ContentDetail): ThemePackSpec? {
        val bad = mutableListOf<String>()
        if (d.type !in CONTENT_SUPPORTED_TYPES) bad += "Type de contenu non pris en charge."
        if (d.files.any { !safeFileName(it.name) || it.size !in 1..MAX_FILE || !Regex("^[0-9a-fA-F]{64}$").matches(it.sha256) }) bad += "Fichier joint invalide."
        if (d.files.map { it.name }.toSet().size != d.files.size) bad += "Fichiers en double."
        if (bad.isNotEmpty()) throw PackFormatError(bad)
        return when (d.type) {
            "theme_pack" -> {
                val content = JsonObject(d.content + ("id" to JsonPrimitive(d.id.lowercase())))
                val spec = PackFormat.parse(content.toString()).copy(author = d.author.ifBlank { null } ?: (d.content["author"] as? JsonPrimitive)?.content)
                if (spec.wallpaper.kind == "image" && d.files.none { it.name == spec.wallpaper.file }) throw PackFormatError(listOf("Image du pack absente."))
                spec
            }
            else -> {
                val file = (d.content["file"] as? JsonPrimitive)?.content
                if (file == null || d.files.none { it.name == file }) throw PackFormatError(listOf("Image du fond absente."))
                null
            }
        }
    }

    /** Fichier à télécharger pour afficher ou appliquer un contenu (image du pack ou du fond), s'il y en a un. */
    fun mainFile(d: ContentDetail, spec: ThemePackSpec?): ContentFileRef? {
        val name = if (spec != null) spec.wallpaper.file.takeIf { spec.wallpaper.kind == "image" } else (d.content["file"] as? JsonPrimitive)?.content
        return name?.let { n -> d.files.firstOrNull { it.name == n } }
    }
}
