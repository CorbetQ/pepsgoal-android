package fr.pepsgoal.core

import fr.pepsgoal.core.model.*

/* ---------------------------------------------------------------- Boutique */

/**
 * Thème de la boutique : un trio de couleurs (accent) et un arrière-plan. Les flammes dépensées ne font pas baisser
 * le niveau : le porte-monnaie est la différence entre les flammes gagnées et celles déjà dépensées.
 */
class ShopTheme(val key: String, val name: String, val desc: String, val colors: List<String>, val bg: String, val price: Int, val dark: Boolean? = null,
                /** Effet animé par-dessus l'arrière-plan : neige, pétales, feuilles, cœurs, braises, bulles, confettis, étoiles. */
                val fx: String = "",
                /** Édition limitée : achetable seulement pendant sa saison (gardée ensuite pour toujours). */
                val season: String? = null)

val SHOP_THEMES = listOf(
    ShopTheme("synthwave", "Synthwave", "Néon rose et cyan sur une grille rétro", listOf("#FF71CE", "#B967FF", "#01CDFE"), "grid", 150),
    ShopTheme("galaxy", "Galaxie", "Violet profond et poussière d’étoiles", listOf("#C6B6FF", "#7B5CFF", "#FF5CD6"), "stars", 200, dark = true),
    ShopTheme("lava", "Lave", "Rouge braise et orange incandescent", listOf("#FFD23F", "#FF5E1A", "#D7263D"), "mesh", 150),
    ShopTheme("forest", "Forêt", "Mousse, sapin et lumière du matin", listOf("#D4FF8A", "#4CC38A", "#1E8E6E"), "aurora", 120),
    ShopTheme("gold", "Or", "Or brossé, élégant et chaleureux", listOf("#FFF1B8", "#E8B730", "#B8860B"), "mesh", 350),
    ShopTheme("abyss", "Abysses", "Bleu nuit, turquoise et bioluminescence", listOf("#7DF9FF", "#1FB6FF", "#3C3CFF"), "waves", 200, dark = true),
    ShopTheme("candy", "Bonbon", "Pastels acidulés et sucrés", listOf("#FFD6E8", "#FF8FC7", "#9AE6FF"), "blob", 120),
    ShopTheme("sunset", "Coucher de soleil", "Pêche, corail et lavande", listOf("#FFE0A3", "#FF8A65", "#9C6BFF"), "aurora", 150),
    ShopTheme("matcha", "Matcha", "Thé vert, crème et sérénité", listOf("#E8F5C8", "#9BC53D", "#5B8C2A"), "plain", 120),
    ShopTheme("cyberpunk", "Cyberpunk", "Jaune électrique et magenta sur la nuit", listOf("#FCEE0A", "#FF2A6D", "#05D9E8"), "grid", 250, dark = true),
    ShopTheme("midnight", "Minuit", "Bleu encre et étoiles qui scintillent", listOf("#A9C7FF", "#4D6BFF", "#1B1F5E"), "stars", 200, dark = true, fx = "stars"),
    ShopTheme("cloud", "Nuage", "Bleu ciel et blanc cotonneux", listOf("#FFFFFF", "#BFE3FF", "#8CB8FF"), "blob", 120, fx = "bubbles"),
    ShopTheme("blaze", "Brasier", "Braises qui s’envolent sur fond sombre", listOf("#FFC14D", "#FF5A1F", "#B3122E"), "mesh", 250, dark = true, fx = "embers"),
    ShopTheme("zen", "Zen", "Sable, sauge et calme", listOf("#F3E9D2", "#C6B38E", "#88A096"), "plain", 120),
    ShopTheme("retro", "Rétro 80", "Coucher de soleil néon sur la grille", listOf("#FFB86C", "#FF5E9C", "#7A5CFF"), "grid", 200, dark = true),
    // Éditions limitées de saison
    ShopTheme("t-halloween", "Nuit d’Halloween", "Citrouille, violet sorcière et braises", listOf("#FF8A00", "#8B3DFF", "#3DFF8A"), "stars", 200, dark = true, fx = "embers", season = "halloween"),
    ShopTheme("t-noel", "Noël enneigé", "Rouge, sapin et neige qui tombe", listOf("#FF4D4D", "#1E8E3E", "#FFD166"), "blob", 200, fx = "snow", season = "noel"),
    ShopTheme("t-newyear", "Nouvel An", "Or, champagne et confettis", listOf("#FFE7A1", "#FFC300", "#B98CFF"), "stars", 200, dark = true, fx = "confetti", season = "newyear"),
    ShopTheme("t-winter", "Hiver", "Givre bleuté et flocons", listOf("#E4F4FF", "#9FD8FF", "#B6B4FF"), "aurora", 150, fx = "snow", season = "winter"),
    ShopTheme("t-love", "Saint-Valentin", "Rose tendre et cœurs qui flottent", listOf("#FFC2D9", "#FF4F8B", "#E85BFF"), "blob", 150, fx = "hearts", season = "love"),
    ShopTheme("t-easter", "Pâques", "Pastels printaniers et confettis", listOf("#FFE8A3", "#A8E6CF", "#DCC6FF"), "blob", 150, fx = "confetti", season = "easter"),
    ShopTheme("t-spring", "Printemps", "Bourgeons, cerisiers et pétales", listOf("#D9FF8A", "#FF9AD5", "#7DD3FC"), "aurora", 150, fx = "petals", season = "spring"),
    ShopTheme("t-summer", "Été", "Soleil, mer turquoise et bulles", listOf("#FFE45C", "#FF7A3D", "#22D3FF"), "waves", 150, fx = "bubbles", season = "summer"),
    ShopTheme("t-autumn", "Automne", "Feuilles rousses qui tombent", listOf("#FFC36B", "#E2711D", "#8B3A1E"), "mesh", 150, fx = "leaves", season = "autumn"),
)

/* ---------------------------------------------------------------- Arrière-plans illustrés */

/**
 * Arrière-plan illustré de la boutique : une scène animée en douceur (réglage « bg » = « s:<clé> »).
 * [sky] : couleurs indicatives (haut → bas) pour l'aperçu en liste et le repli si l'animation est coupée.
 */
class ShopBg(val key: String, val name: String, val desc: String, val price: Int, val sky: List<String>, val dark: Boolean = false, val season: String? = null)

val SHOP_BGS = listOf(
    ShopBg("nebula", "Nébuleuse", "Nuages cosmiques et étoiles filantes", 200, listOf("#0B0628", "#3B1D6E", "#C14BA8"), dark = true),
    ShopBg("deepsea", "Fonds marins", "Rayons de lumière, bulles et poissons", 200, listOf("#0C5C8A", "#063A5C", "#021829"), dark = true),
    ShopBg("mountains", "Montagnes", "Sommets en couches au lever du jour", 150, listOf("#FFD7A8", "#F59E8B", "#6C5B9E")),
    ShopBg("citynight", "Ville la nuit", "Gratte-ciel, fenêtres allumées et lune", 200, listOf("#0A0F2C", "#1E2A5A", "#3D2B6B"), dark = true),
    ShopBg("tram", "Ville et tramway", "Le tram arc-en-ciel d’Angers au crépuscule", 200, listOf("#4D4A86", "#EE9C86", "#FFDDA8")),
    ShopBg("mistforest", "Forêt brumeuse", "Sapins en couches dans la brume", 150, listOf("#DDE8E0", "#9DB8A7", "#36574A")),
    ShopBg("dunes", "Dunes", "Désert doré au soleil couchant", 150, listOf("#FFB07A", "#F28C5B", "#C9663F")),
    ShopBg("sakura", "Cerisiers", "Branches en fleurs et pétales", 200, listOf("#FFE6EF", "#FFC9DD", "#F6A6C1")),
    ShopBg("magma", "Magma", "Roche sombre et rivières de lave", 250, listOf("#1A0A08", "#4A1308", "#FF5A1F"), dark = true),
    ShopBg("coderain", "Pluie de code", "Caractères verts qui tombent", 150, listOf("#000A04", "#001F0E", "#003B1C"), dark = true),
    ShopBg("borealis", "Aurore boréale", "Voiles verts et violets sur la toundra", 250, listOf("#020B1A", "#08263A", "#0E3B3A"), dark = true),
    ShopBg("rainwindow", "Pluie sur la vitre", "Gouttes qui glissent, lumières floues", 150, listOf("#2A3142", "#3E4A63", "#5A6B88"), dark = true),
    ShopBg("planet", "Planète", "Géante aux anneaux et lunes", 250, listOf("#05030F", "#140A2E", "#2A1554"), dark = true),
    ShopBg("lagoon", "Lagon", "Plage, palmiers et eau turquoise", 150, listOf("#7FD8FF", "#BFF3F0", "#F7E3B5")),
    // Éditions limitées de saison
    ShopBg("haunted", "Manoir hanté", "Pleine lune, chauves-souris et citrouilles", 200, listOf("#120A24", "#2C1550", "#4A1F3A"), dark = true, season = "halloween"),
    ShopBg("snowvillage", "Village enneigé", "Maisons illuminées sous la neige", 200, listOf("#1B2A4A", "#3B4F7A", "#DCE6F5"), dark = true, season = "noel"),
    ShopBg("fireworks", "Feux d’artifice", "Gerbes colorées au-dessus de la ville", 200, listOf("#05061A", "#10153A", "#241A4A"), dark = true, season = "newyear"),
    ShopBg("snowpeak", "Montagne d’hiver", "Sommets enneigés et ciel givré", 150, listOf("#DDEBFF", "#A9C6EE", "#F4F8FF"), season = "winter"),
    ShopBg("heartsky", "Ciel de cœurs", "Nuages roses et cœurs qui s’envolent", 150, listOf("#FFD6E6", "#FFB3CF", "#E9A6FF"), season = "love"),
    ShopBg("eggmeadow", "Prairie de Pâques", "Œufs cachés dans l’herbe fleurie", 150, listOf("#CFF1FF", "#E8FFD6", "#A6DE7A"), season = "easter"),
    ShopBg("flowerfield", "Champ fleuri", "Collines vertes et fleurs des champs", 150, listOf("#BFE8FF", "#E6FFD9", "#8ED16B"), season = "spring"),
    ShopBg("summerbeach", "Plage d’été", "Soleil, vagues et parasols", 150, listOf("#FFE7A3", "#7FD8FF", "#F5D79E"), season = "summer"),
    ShopBg("autumnwood", "Bois d’automne", "Arbres roux et feuilles qui tombent", 150, listOf("#FFE1B3", "#F2A65A", "#9C4A1E"), season = "autumn"),
)

/** Applique un arrière-plan illustré (acheté ou gratuit). */
fun Pg.applyBg(b: ShopBg) {
    val u = me() ?: return
    u.settings.bg = "s:${b.key}"; store.commit()
}

/* ---------------------------------------------------------------- Saisons de la boutique */

val SEASON_LABELS = mapOf("halloween" to "Halloween 🎃", "noel" to "Noël 🎄", "newyear" to "Nouvel An 🎆", "winter" to "Hiver ❄️", "love" to "Saint-Valentin 💕",
    "easter" to "Pâques 🐣", "spring" to "Printemps 🌸", "summer" to "Été ☀️", "autumn" to "Automne 🍂")

/** Dimanche de Pâques (calcul grégorien de Meeus). */
fun easter(year: Int): java.time.LocalDate {
    val a = year % 19; val b = year / 100; val c = year % 100; val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30; val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31; val day = (h + l - 7 * m + 114) % 31 + 1
    return java.time.LocalDate.of(year, month, day)
}

/** Fenêtre d'une saison contenant [d] (début, fin incluse), ou null si la saison n'est pas en cours. */
fun seasonWindow(season: String, d: java.time.LocalDate = todayDate()): Pair<java.time.LocalDate, java.time.LocalDate>? {
    val y = d.year
    fun md(m: Int, dd: Int, yy: Int = y) = java.time.LocalDate.of(yy, m, dd)
    fun wrap(s: java.time.LocalDate, e: java.time.LocalDate) = if (!d.isBefore(s) && !d.isAfter(e)) s to e else null
    return when (season) {
        "halloween" -> wrap(md(10, 15), md(11, 5))
        "noel" -> wrap(md(12, 1), md(12, 31))
        "newyear" -> wrap(md(12, 26), md(1, 10, y + 1)) ?: wrap(md(12, 26, y - 1), md(1, 10))
        "winter" -> wrap(md(12, 21), md(3, 19, y + 1)) ?: wrap(md(12, 21, y - 1), md(3, 19))
        "love" -> wrap(md(2, 1), md(2, 16))
        "easter" -> easter(y).let { wrap(it.minusDays(10), it.plusDays(7)) }
        "spring" -> wrap(md(3, 20), md(6, 20))
        "summer" -> wrap(md(6, 21), md(9, 22))
        "autumn" -> wrap(md(9, 23), md(12, 20))
        else -> null
    }
}

/** Prochaine ouverture d'une saison (pour « revient le … »). */
fun seasonNext(season: String, from: java.time.LocalDate = todayDate()): java.time.LocalDate? =
    (0L..400L).asSequence().map { from.plusDays(it) }.firstOrNull { seasonWindow(season, it) != null }

/** Un article saisonnier ne s'achète que pendant sa saison ; déjà acheté, il reste disponible. */
fun Pg.buyable(key: String, price: Int, season: String?): Boolean = owns(key, price) || season == null || seasonWindow(season) != null

/** Flammes disponibles à dépenser. */
fun Pg.wallet(u: User? = me()): Int = if (u == null) 0 else maxOf(0, points(u.id) + u.shopBonus - u.shopSpent)

fun Pg.owns(key: String, price: Int, u: User? = me()): Boolean = price <= 0 || (u?.shopOwned?.contains(key) == true)

/** Achète un article. Renvoie un message d'erreur, ou null si l'achat a réussi. */
fun Pg.buy(key: String, price: Int): String? {
    val u = me() ?: return "Connecte-toi pour acheter."
    if (owns(key, price, u)) return null
    (SHOP_THEMES.firstOrNull { "theme:${it.key}" == key }?.season ?: SHOP_BGS.firstOrNull { "bg:${it.key}" == key }?.season)
        ?.let { if (seasonWindow(it) == null) return "Édition limitée : elle revient pendant sa saison." }
    val w = wallet(u)
    if (w < price) return "Il te manque ${price - w} flammes. Valide tes objectifs pour en gagner !"
    u.shopOwned = (u.shopOwned + key).toMutableList(); u.shopSpent += price
    store.commit()
    return null
}

/** Applique un thème acheté : couleurs d'accent personnalisées et arrière-plan. */
fun Pg.applyTheme(t: ShopTheme) {
    val u = me() ?: return
    u.settings.accentCustom = t.colors; u.settings.bg = t.bg; u.settings.fx = t.fx
    t.dark?.let { if (it) u.settings.theme = "dark" }
    store.commit()
}
