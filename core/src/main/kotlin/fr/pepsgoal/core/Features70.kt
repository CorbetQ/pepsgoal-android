package fr.pepsgoal.core

import fr.pepsgoal.core.model.*

/* ---------------------------------------------------------------- Offrir un article de la boutique */

/** Article de la boutique qu'on peut offrir : clé (« theme:… », « bg:… », « mode:… », « icon:… »), nom, rayon, prix. */
class ShopItem(val key: String, val name: String, val kind: String, val price: Int, val season: String? = null, val desc: String = "")

/** Noms des icônes d'application (connus de l'application seulement), pour le catalogue des cadeaux. */
var shopExtraNames: Map<String, String> = emptyMap()

/** Rayons de la boutique, dans l'ordre d'affichage : clé → (nom, emoji). */
val SHOP_KINDS = linkedMapOf("theme" to ("Thèmes" to "🎨"), "mode" to ("Ambiances" to "🌗"), "bg" to ("Fonds" to "🏞️"), "icon" to ("Icônes" to "📱"))

/** Tous les articles payants de la boutique. */
fun shopCatalog(): List<ShopItem> =
    SHOP_THEMES.map { ShopItem("theme:${it.key}", it.name, "theme", it.price, it.season, it.desc) } +
    SHOP_MODES.map { ShopItem("mode:${it.key}", it.name, "mode", it.price, desc = it.desc) } +
    SHOP_BGS.map { ShopItem("bg:${it.key}", it.name, "bg", it.price, it.season, it.desc) } +
    shopExtraPrices.filter { it.value > 0 }.map { (k, v) -> ShopItem(k, shopExtraNames[k] ?: k.substringAfter(':'), "icon", v) }

fun shopItem(key: String): ShopItem? = shopCatalog().firstOrNull { it.key == key }

/** Vérifie qu'on peut offrir l'article [key] ; renvoie un message d'erreur, ou null. */
fun Pg.giftItemError(key: String): String? {
    val u = me() ?: return "Connecte-toi pour offrir un cadeau."
    val it = shopItem(key) ?: return "Cet article n’existe plus dans la boutique."
    if (it.price <= 0) return "Cet article est gratuit pour tout le monde."
    if (it.season != null && seasonWindow(it.season) == null) return "Édition limitée : elle s’offre seulement pendant sa saison."
    val w = wallet(u)
    return if (w < it.price) "Il te manque ${it.price - w} flammes dans ton porte-monnaie." else null
}

/** Retire le prix de l'article offert du porte-monnaie (le niveau, lui, ne baisse pas). */
fun Pg.spendItemGift(key: String, price: Int) { val u = me() ?: return; u.shopSpent += price; u.shopClaimed.add("giftiout:${today()}:$key:$price"); store.commit() }
/** Rend les flammes si l'envoi du cadeau a échoué. */
fun Pg.refundItemGift(key: String, price: Int) {
    val u = me() ?: return
    if (u.shopClaimed.remove("giftiout:${today()}:$key:$price")) { u.shopSpent = maxOf(0, u.shopSpent - price); store.commit() }
}

/** Total des flammes dépensées en articles offerts. */
fun Pg.itemsGiven(): Int = me()?.shopClaimed?.filter { it.startsWith("giftiout:") }?.sumOf { it.substringAfterLast(':').toIntOrNull() ?: 0 } ?: 0
/** Articles offerts à des amis (nombre). */
fun Pg.itemsGivenCount(): Int = me()?.shopClaimed?.count { it.startsWith("giftiout:") } ?: 0
/** Articles reçus en cadeau (nombre). */
fun Pg.itemsReceivedCount(): Int = me()?.shopClaimed?.count { it.startsWith("giftiin:") } ?: 0

/** Résultat de l'ouverture d'un article offert : ajouté à la collection, ou déjà possédé et remboursé en flammes. */
enum class ItemGift { ADDED, CONVERTED }

/**
 * Ouvre un article reçu en cadeau (une seule fois, sur tous les appareils) : il rejoint la collection ; s'il était
 * déjà possédé, son prix rejoint le porte-monnaie en flammes. Null si le cadeau était déjà ouvert.
 */
fun Pg.openItemGift(msgId: String, key: String, price: Int): ItemGift? {
    val u = me() ?: return null
    if (giftOpened(msgId)) return null
    val k = price.coerceIn(0, 2000)
    u.shopClaimed.add("gift:$msgId")
    val r = if (owns(key, k, u) || shopItem(key) == null) {
        u.shopBonus += k; u.shopClaimed.add("giftin:$msgId:$k"); ItemGift.CONVERTED
    } else {
        u.shopOwned = (u.shopOwned + key).toMutableList(); u.shopClaimed.add("giftiin:$msgId:$key"); ItemGift.ADDED
    }
    store.commit()
    return r
}

/** Flammes parties en cadeaux (flammes et articles) : elles ne doivent jamais être « remboursées » par le recalcul des prix. */
fun Pg.giftsOut(): Int = flamesGiven() + itemsGiven()

/** Garde les marques de `shopClaimed` des cadeaux, qui servent aux comptes du porte-monnaie, et les [n] plus récentes des autres. */
fun trimClaimed(l: List<String>, n: Int = 400): MutableList<String> {
    // Cadeaux (comptes du porte-monnaie) et niveau déjà récompensé : jamais retirés
    val kept = { s: String -> s.startsWith("gift") || s.startsWith("lvl:") }
    return (l.filter(kept) + l.filterNot(kept).takeLast(n)).toMutableList()
}
