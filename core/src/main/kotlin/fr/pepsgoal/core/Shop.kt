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
    // Thèmes spéciaux : un paysage illustré exclusif et ses couleurs
    ShopTheme("tunnel", "Tunnel Express", "Le métro de Rennes file dans son tunnel : phares chauds, néons bleutés", listOf("#FFE2A0", "#FFB000", "#5FA8FF"), "s:tunnel", 300, dark = true),
    ShopTheme("loire", "Loire", "Les bords de Loire en Anjou : tuffeau, ardoise et sable blond", listOf("#FFD49A", "#E9873A", "#3E6E8E"), "s:loire", 300, dark = false),
    ShopTheme("angers", "Tramway d’Angers", "Le tram traverse la Maine au pied du château aux tours rayées, à l’heure réelle", listOf("#FFE2A6", "#E8412C", "#2DA9E1"), "s:angers", 300, dark = false),
    ShopTheme("angevine", "Douceur angevine", "Ardoise, tuffeau et vignes du Layon, au pied d’un moulin cavier", listOf("#F3E2C4", "#C9A24A", "#5E6E86"), "s:angevine", 300, dark = false),
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
    ShopBg("loire", "Loire", "Bords de Loire en Anjou : tuffeau, bancs de sable et toue", 250, listOf("#5E93CF", "#F2D3B6", "#9CC0DE")),
    ShopBg("lighthouse", "Phare", "Nuit étoilée, faisceau qui balaie la mer", 200, listOf("#060B1E", "#15244A", "#2E4670"), dark = true),
    ShopBg("vineyard", "Vignes d’Anjou", "Rangs de vigne et chai au soleil couchant", 200, listOf("#5A6FA8", "#E8A27A", "#B9925A")),
    ShopBg("balloons", "Montgolfières", "Ballons colorés au-dessus de la brume", 200, listOf("#7FB2E6", "#CFE2F2", "#FFE2C8")),
    ShopBg("jellyfish", "Méduses", "Méduses lumineuses dans les profondeurs", 200, listOf("#0E2A5A", "#071838", "#020818"), dark = true),
    ShopBg("bamboo", "Bambouseraie", "Tiges vertes, rayons de lumière et feuilles", 150, listOf("#E6F4D8", "#BFE0A6", "#7FB86A")),
    ShopBg("nighttrain", "Train de nuit", "Un train illuminé traverse le viaduc sous la lune", 250, listOf("#0A0F2A", "#1E2A5A", "#3A3A6A"), dark = true),
    ShopBg("rooftops", "Toits de Paris", "Zinc, cheminées et lucarnes au crépuscule", 200, listOf("#3A3F7A", "#A06A9A", "#F2A07A")),
    ShopBg("canyon", "Canyon", "Mesas rouges, strates et rivière turquoise", 200, listOf("#3E8AD8", "#D98A6A", "#A84A2A")),
    ShopBg("savanna", "Savane", "Acacias et girafes devant le soleil couchant", 200, listOf("#4A2A6A", "#FF8A4A", "#5A2A22")),
    ShopBg("lanterns", "Lanternes célestes", "Des lanternes s’envolent au-dessus du lac", 250, listOf("#0A0A24", "#241A4A", "#4A2A5A"), dark = true),
    ShopBg("icefield", "Banquise", "Icebergs, plaques de glace et ciel polaire", 150, listOf("#9EC9F2", "#D6EAFA", "#5E9CC8")),
    ShopBg("tunnel", "Tunnel Express", "Le métro de Rennes file dans son tunnel", 250, listOf("#0B0F17", "#1A2230", "#3A4250"), dark = true),
    ShopBg("angers", "Tramway d’Angers", "La ligne A sur la Maine, le château et la cathédrale", 250, listOf("#4F86C6", "#9CC2E6", "#F6D6B8")),
    ShopBg("angevine", "Douceur angevine", "Vignes du Layon, logis de tuffeau et moulin cavier", 250, listOf("#7FA8D8", "#F6DCC0", "#C9A87A")),
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
    // Thème de couleurs pensé pour le sombre : on passe en sombre, sauf si une interface sombre est déjà choisie
    t.dark?.let { if (it && u.settings.theme in setOf("light", "system")) u.settings.theme = "dark" }
    store.commit()
}

/* ---------------------------------------------------------------- Ambiances (thèmes d'interface) */

/**
 * Ambiance : thème d'interface complet, au même niveau que Clair, Sombre ou OLED (réglage « theme » = « m:<clé> ») :
 * couleur du fond, du texte, du verre des cartes, et teintes des halos d'arrière-plan. La couleur d'accent reste celle
 * choisie par l'utilisateur ; [accent] est l'accent conseillé, proposé à l'achat.
 */
class ShopMode(val key: String, val name: String, val desc: String, val price: Int, val dark: Boolean,
               val bg: String, val ink: String, val glass: String, val blobs: List<String>, val accent: List<String>)

val SHOP_MODES = listOf(
    ShopMode("midnight", "Minuit", "Bleu nuit profond, étoiles froides", 180, true, "#0A1024", "#EAF0FF", "#1A2448", listOf("#2B4BFF", "#7AA2FF", "#00C2FF"), listOf("#7AA2FF", "#3D6BFF", "#B36BFF")),
    ShopMode("forest", "Forêt nocturne", "Vert sapin et sous-bois", 180, true, "#08140F", "#E6F5EC", "#17302A", listOf("#1F7A4D", "#5BD39A", "#C6FF5C"), listOf("#B6FF2E", "#2EF2B0", "#22D3A0")),
    ShopMode("wine", "Bordeaux", "Velours rouge sombre et or", 200, true, "#1A070D", "#FBE9EE", "#3A1220", listOf("#8C1F3F", "#FF4F7B", "#FFB000"), listOf("#FFC14D", "#FF4F7B", "#C2185B")),
    ShopMode("graphite", "Graphite", "Gris neutre, sobre et net", 150, true, "#121315", "#ECEDEF", "#24262A", listOf("#5A5F6A", "#8E95A3", "#C9CED8"), listOf("#E6E9EF", "#9AA3B5", "#5C6476")),
    ShopMode("cocoa", "Cacao", "Brun chaud, chocolat et caramel", 180, true, "#160E09", "#F5E9DF", "#33231A", listOf("#8A5A3B", "#D9925B", "#FFC27A"), listOf("#FFC27A", "#E07B39", "#A0522D")),
    ShopMode("abyss", "Abysses", "Bleu-vert des grands fonds", 200, true, "#031417", "#E0FAFB", "#0F3138", listOf("#007C89", "#00D1D1", "#6FFFE9"), listOf("#6FFFE9", "#00C2D1", "#2E7DFF")),
    ShopMode("plum", "Aubergine", "Violet profond et mûre", 180, true, "#120A1E", "#F1E9FF", "#2A1B45", listOf("#6B2FD1", "#B36BFF", "#FF7AD9"), listOf("#D4A8FF", "#9B5CFF", "#FF7AD9")),
    ShopMode("neon", "Néon", "Nuit électrique, cyan et magenta", 250, true, "#05040C", "#F3F0FF", "#1A1530", listOf("#00F0FF", "#FF00E5", "#7B2BFF"), listOf("#00F0FF", "#FF00E5", "#FFE600")),
    ShopMode("paper", "Papier", "Crème doux, comme un carnet", 150, false, "#F6F1E7", "#2B2620", "#FFFDF8", listOf("#E8DCC4", "#F2C9A0", "#D6E3D0"), listOf("#E07B39", "#C2553D", "#8A5A3B")),
    ShopMode("sepia", "Sépia", "Tons chauds de vieille photo", 180, false, "#EFE2CC", "#3A2A18", "#FAF1E2", listOf("#D9B98C", "#C98E5A", "#E8D3B0"), listOf("#B5651D", "#8A4B12", "#D2A15E")),
    ShopMode("mint", "Menthe", "Vert frais et lumineux", 180, false, "#E6F6EE", "#0F2C22", "#F6FFFA", listOf("#9EF0C8", "#7DD3FC", "#C6FF9E"), listOf("#00B37E", "#00A3C4", "#7BD88F")),
    ShopMode("lavender", "Lavande", "Mauve tendre et apaisant", 180, false, "#EEE9FA", "#241A3D", "#FBF9FF", listOf("#C9B8FF", "#FFC2E8", "#A8D8FF"), listOf("#8C6BFF", "#C35BD9", "#5A8DFF")),
    ShopMode("peach", "Pêche", "Abricot et corail du matin", 180, false, "#FCEBE1", "#3B1E14", "#FFF7F2", listOf("#FFC2A8", "#FF9E80", "#FFD6A5"), listOf("#FF7A59", "#FF4F7B", "#FFB000")),
    ShopMode("nordic", "Nordique", "Gris bleuté, épuré et calme", 150, false, "#E8EDF2", "#1A2633", "#F8FAFC", listOf("#BFD3E6", "#D8E2EC", "#A7C4D9"), listOf("#3D7EA6", "#5A9BC2", "#2E5A88")),
    ShopMode("sakura", "Sakura", "Rose cerisier et pétales", 200, false, "#FBE8EF", "#3A1626", "#FFF6F9", listOf("#FFB7CE", "#FFD6E5", "#E8B4FF"), listOf("#FF5C8A", "#E8559D", "#B85CFF")),
    ShopMode("sand", "Sable", "Beige doré des dunes", 150, false, "#F3EBDD", "#2E271C", "#FFFBF3", listOf("#EBD3A8", "#F5C98A", "#DDE6C8"), listOf("#C98A2E", "#E0A43A", "#8A6A2E")),
    // 5.4.0
    ShopMode("ink", "Encre", "Bleu-noir profond, reflets saphir", 180, true, "#070B14", "#E8EEFA", "#141C2E", listOf("#2E5BFF", "#00B3FF", "#5A3DFF"), listOf("#8FB4FF", "#3D7BFF", "#00C2FF")),
    ShopMode("basalt", "Basalte", "Roche volcanique et braises", 200, true, "#111012", "#F2ECE8", "#262224", listOf("#FF5A1F", "#7A2A1A", "#FFB04A"), listOf("#FFB04A", "#FF5A1F", "#E8412C")),
    ShopMode("emerald", "Émeraude", "Vert précieux et lagon nocturne", 200, true, "#04140F", "#E2FAF0", "#0F2E24", listOf("#00A86B", "#2EF2B0", "#0E6B5A"), listOf("#7CFFC8", "#00C98A", "#00A3C4")),
    ShopMode("cosmos", "Cosmos", "Violet galactique et poussière rose", 220, true, "#0A0820", "#EEE8FF", "#1E1844", listOf("#7B2BFF", "#FF4FD8", "#2E6BFF"), listOf("#C9A8FF", "#8C5CFF", "#FF5CD6")),
    ShopMode("steel", "Acier", "Gris bleuté, métal brossé", 150, true, "#0E1318", "#E6ECF2", "#1E2832", listOf("#4A6A8A", "#8FB0CC", "#2E4A66"), listOf("#BFD6EA", "#7FA6C9", "#4D7FA8")),
    ShopMode("metro", "Métro", "Tunnel de nuit, phares chauds et signaux bleus", 220, true, "#0B0F17", "#EEF2F8", "#1C2430", listOf("#FFD27A", "#5FA8FF", "#E8412C"), listOf("#FFE2A0", "#FFB000", "#5FA8FF")),
    ShopMode("cotton", "Coton", "Blanc doux et pastels poudrés", 150, false, "#F4F6FA", "#1C2230", "#FFFFFF", listOf("#DCE4F2", "#F2DCE8", "#DCF2EA"), listOf("#7FA6FF", "#5A7DFF", "#B47CFF")),
    ShopMode("pistachio", "Pistache", "Vert tendre et gourmand", 180, false, "#EEF6E2", "#1E2A14", "#FAFFF2", listOf("#C8E6A0", "#E6F2B8", "#A8D8B0"), listOf("#8CC63F", "#5EA82E", "#2E8A5A")),
    ShopMode("lemon", "Citron", "Jaune soleil et zeste vert", 180, false, "#FFF9DC", "#2A2410", "#FFFEF4", listOf("#FFE98A", "#FFD25A", "#C8F0A0"), listOf("#FFD000", "#FFB000", "#7CC43A")),
    ShopMode("sky", "Ciel", "Bleu azur et nuages de lait", 180, false, "#E6F3FF", "#102438", "#F8FCFF", listOf("#A8D8FF", "#CDE9FF", "#FFE0C8"), listOf("#3DA5FF", "#1E7FE0", "#7FD3FF")),
    ShopMode("coral", "Corail", "Corail vif et rose saumon", 200, false, "#FFEDE8", "#3A1612", "#FFF8F6", listOf("#FFB8A8", "#FFD6C8", "#FFC2D9"), listOf("#FF7A59", "#FF4F5E", "#FFB000")),
    ShopMode("linen", "Lin", "Lin naturel, bois clair et sauge", 150, false, "#F2EEE6", "#2A2620", "#FCFAF6", listOf("#E2D8C6", "#D8E0D0", "#EAD6CC"), listOf("#A8865A", "#8A6A44", "#5E7A6A")),
)

fun shopMode(theme: String?): ShopMode? = if (theme != null && theme.startsWith("m:")) (SHOP_MODES + Eggs.SECRET_MODES).firstOrNull { "m:${it.key}" == theme } else null

/** Nom lisible d'un thème : thème de base ou ambiance. */
fun themeName(theme: String): String = shopMode(theme)?.name ?: THEMES.firstOrNull { it.first == theme }?.second ?: "Auto"

/** Thème de base équivalent (pour le serveur et la version web, qui ne connaissent que les thèmes de base). */
fun baseTheme(theme: String): String = shopMode(theme)?.let { if (it.dark) "dark" else "light" } ?: theme.takeIf { it in setOf("system", "light", "dark", "oled", "weather") } ?: "system"
