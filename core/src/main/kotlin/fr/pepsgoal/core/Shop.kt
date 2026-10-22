package fr.pepsgoal.core

import fr.pepsgoal.core.model.*

/* ---------------------------------------------------------------- Boutique */

/**
 * Thème de la boutique : un trio de couleurs (accent) et un arrière-plan. Les flammes dépensées ne font pas baisser
 * le niveau : le porte-monnaie est la différence entre les flammes gagnées et celles déjà dépensées.
 */
class ShopTheme(val key: String, val name: String, val desc: String, val colors: List<String>, val bg: String, val price: Int, val dark: Boolean? = null)

val SHOP_THEMES = listOf(
    ShopTheme("synthwave", "Synthwave", "Néon rose et cyan sur une grille rétro", listOf("#FF71CE", "#B967FF", "#01CDFE"), "grid", 400),
    ShopTheme("galaxy", "Galaxie", "Violet profond et poussière d’étoiles", listOf("#C6B6FF", "#7B5CFF", "#FF5CD6"), "stars", 500, dark = true),
    ShopTheme("lava", "Lave", "Rouge braise et orange incandescent", listOf("#FFD23F", "#FF5E1A", "#D7263D"), "mesh", 400),
    ShopTheme("forest", "Forêt", "Mousse, sapin et lumière du matin", listOf("#D4FF8A", "#4CC38A", "#1E8E6E"), "aurora", 300),
    ShopTheme("gold", "Or", "Or brossé, élégant et chaleureux", listOf("#FFF1B8", "#E8B730", "#B8860B"), "mesh", 800),
    ShopTheme("abyss", "Abysses", "Bleu nuit, turquoise et bioluminescence", listOf("#7DF9FF", "#1FB6FF", "#3C3CFF"), "waves", 500, dark = true),
    ShopTheme("candy", "Bonbon", "Pastels acidulés et sucrés", listOf("#FFD6E8", "#FF8FC7", "#9AE6FF"), "blob", 300),
    ShopTheme("sunset", "Coucher de soleil", "Pêche, corail et lavande", listOf("#FFE0A3", "#FF8A65", "#9C6BFF"), "aurora", 400),
)

/** Flammes disponibles à dépenser. */
fun Pg.wallet(u: User? = me()): Int = if (u == null) 0 else maxOf(0, points(u.id) - u.shopSpent)

fun Pg.owns(key: String, price: Int, u: User? = me()): Boolean = price <= 0 || (u?.shopOwned?.contains(key) == true)

/** Achète un article. Renvoie un message d'erreur, ou null si l'achat a réussi. */
fun Pg.buy(key: String, price: Int): String? {
    val u = me() ?: return "Connecte-toi pour acheter."
    if (owns(key, price, u)) return null
    val w = wallet(u)
    if (w < price) return "Il te manque ${price - w} flammes. Valide tes objectifs pour en gagner !"
    u.shopOwned = (u.shopOwned + key).toMutableList(); u.shopSpent += price
    store.commit()
    return null
}

/** Applique un thème acheté : couleurs d'accent personnalisées et arrière-plan. */
fun Pg.applyTheme(t: ShopTheme) {
    val u = me() ?: return
    u.settings.accentCustom = t.colors; u.settings.bg = t.bg
    t.dark?.let { if (it) u.settings.theme = "dark" }
    store.commit()
}
