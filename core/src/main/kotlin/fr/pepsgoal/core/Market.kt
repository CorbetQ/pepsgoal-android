package fr.pepsgoal.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/* ---------------------------------------------------------------- Boutique des créateurs (10.0) */

/*
 * Chacun peut publier ses packs de thème dans la boutique PepsGoal et fixer un prix en flammes : 100 % du prix revient
 * au créateur. Le serveur enregistre les achats ; l'application du créateur relève ses ventes et crédite son
 * porte-monnaie une seule fois par vente (repère « mkt:<id>:<prix> » dans shopClaimed, synchronisé entre appareils).
 * Toutes les images (« PepsGoal Style ») sont calculées par le serveur : créer un pack demande une connexion.
 */

/** Prix maximal d'un pack, en flammes (comme le serveur). */
const val CREATOR_PRICE_MAX = 5000
/** Prix proposés dans la feuille de publication (un prix libre reste possible). */
val CREATOR_PRICES = listOf(0, 50, 100, 200, 350, 500, 1000)

/** Une vente vue par le créateur. */
@Serializable
data class Sale(val id: String, val contentId: String = "", val name: String = "", val price: Int = 0, val buyer: String = "", val createdAt: String? = null)

@Serializable
data class SalesPage(val sales: List<Sale> = emptyList(), val cursor: String? = null, val total: Int = 0, val count: Int = 0)

/** Un pack du créateur, avec son statut (`pending`, `published`, `rejected`) et ce qu'il lui a rapporté. */
@Serializable
data class CreatorItem(
    val id: String, val name: String, val description: String = "", val price: Int = 0, val status: String = "pending",
    val version: Int = 1, val sales: Int = 0, val earned: Int = 0, val reviewNote: String = "", val preview: String? = null,
    val content: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()), val files: List<ContentFileRef> = emptyList(),
    val updatedAt: String? = null,
)

/** Résultat de « PepsGoal Style » : image (stylisée ou non), premier plan sans le ciel, couleurs proposées. */
@Serializable
data class StyleResult(
    val image: ContentFileRef, val foreground: ContentFileRef? = null, val colors: List<String> = emptyList(),
    val skyRatio: Double = 0.0, val width: Int = 0, val height: Int = 0, val stylized: Boolean = false, val weather: Boolean = false,
)

/** Achat enregistré par le serveur (rejouer l'achat renvoie le même, `fresh` faux). */
@Serializable
data class Purchase(val id: String, val contentId: String, val price: Int = 0, val createdAt: String? = null, val fresh: Boolean = true)

@Serializable
data class OwnedContent(val contentId: String, val price: Int = 0, val createdAt: String? = null)

/** Fichiers distants d'un pack personnel déjà envoyés au serveur (évite de renvoyer une photo inchangée). */
@Serializable
data class PackRemote(val image: ContentFileRef? = null, val foreground: ContentFileRef? = null, val contentId: String? = null)

val MARKET_JSON = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; encodeDefaults = true }

object Market {
    fun statusLabel(s: String): String = when (s) {
        "published" -> "En vente"; "pending" -> "En attente de validation"; "rejected" -> "Refusé"; "retired" -> "Retiré"; else -> s
    }

    /** Statut d'un prix : « Gratuit » ou « 120 flammes ». */
    fun priceLabel(p: Int): String = if (p <= 0) "Gratuit" else "${fmtNum(p)} flammes"

    /** Prix saisi ramené dans les bornes (multiple de 5). */
    fun clampPrice(p: Int): Int = (p.coerceIn(0, CREATOR_PRICE_MAX) / 5) * 5

    /**
     * Pack prêt à publier : un pack valide dont le fond n'est pas une scène achetée (elle ne serait pas à l'acheteur)
     * ni une photo absente. Renvoie le problème, ou null.
     */
    fun publishProblem(spec: ThemePackSpec, hasImage: Boolean): String? {
        PackFormat.validate(spec, checkCatalog = false).firstOrNull()?.let { return it }
        if (spec.wallpaper.kind == "scene") return "Les scènes de la boutique ne peuvent pas être revendues : choisis un fond gratuit, un dégradé, une photo ou PepsGoal Style."
        if (spec.wallpaper.kind == "image" && !hasImage) return "La photo du pack est introuvable : choisis-la à nouveau."
        if (spec.name.isBlank()) return "Donne un nom au pack."
        return null
    }

    /**
     * Pack tel qu'envoyé à la boutique : sans dates ni icône ou effet (achetés par le créateur, pas par l'acheteur),
     * fichiers renommés comme les fichiers joints.
     */
    fun publishable(spec: ThemePackSpec, imageName: String?, fgName: String?): ThemePackSpec = spec.copy(
        createdAt = 0, updatedAt = 0, icon = null, fx = null, author = null,
        wallpaper = if (spec.wallpaper.kind == "image") spec.wallpaper.copy(file = imageName, foreground = fgName) else spec.wallpaper,
    )
}

/** Ventes déjà créditées sur ce compte (repères « mkt:<id>:<prix> »). */
fun Pg.creditedSales(): Set<String> = me()?.shopClaimed?.filter { it.startsWith("mkt:") }?.map { it.removePrefix("mkt:").substringBefore(':') }?.toSet() ?: emptySet()

/** Flammes reçues de ses ventes. */
fun Pg.marketEarned(): Int = me()?.shopClaimed?.filter { it.startsWith("mkt:") }?.sumOf { it.substringAfterLast(':').toIntOrNull() ?: 0 } ?: 0

/**
 * Crédite les ventes pas encore comptées : 100 % du prix au porte-monnaie, une seule fois par vente (même rejouée,
 * même sur un autre appareil). Renvoie les ventes nouvellement créditées.
 */
fun Pg.creditSales(sales: List<Sale>): List<Sale> {
    val u = me() ?: return emptyList()
    val done = creditedSales().toMutableSet()
    val fresh = sales.filter { it.id.isNotBlank() && done.add(it.id) }
    if (fresh.isEmpty()) return fresh
    fresh.forEach { s -> u.shopBonus += s.price.coerceAtLeast(0); u.shopClaimed.add("mkt:${s.id}:${s.price.coerceAtLeast(0)}") }
    store.commit()
    return fresh
}

/** Collection retrouvée (nouvel appareil) : les contenus achetés deviennent possédés, sans nouvelle dépense. */
fun Pg.restorePurchases(owned: List<OwnedContent>): Int {
    val u = me() ?: return 0
    val add = owned.filter { !ownsContent(it.contentId, u) }.map { contentKey(it.contentId, it.price) }
    if (add.isEmpty()) return 0
    u.shopOwned = (u.shopOwned + add).toMutableList(); store.commit()
    return add.size
}

/** Ses propres packs en vente lui appartiennent (sans les acheter). */
fun Pg.ownsOwnPack(item: CatalogItem): Boolean = item.creatorId != null && item.creatorId == me()?.id

/** Teinte du ciel d'une photo « PepsGoal Style » selon le temps et l'heure (0 : ciel de la photo, 0,9 : ciel redessiné). */
object PhotoSky {
    fun tint(e: SceneEnvironment): Float {
        val weather = when (e.condition) { "storm" -> .8f; "rain", "snow" -> .7f; "fog" -> .6f; else -> 0f }
        val clouds = (e.clouds / 100f).coerceIn(0f, 1f) * .5f
        return maxOf(e.night * .85f, (e.dawn + e.dusk).coerceAtMost(1f) * .35f, weather, clouds).coerceIn(0f, .9f)
    }
}
