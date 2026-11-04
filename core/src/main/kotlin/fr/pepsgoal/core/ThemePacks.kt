package fr.pepsgoal.core

import fr.pepsgoal.core.model.User
import java.time.LocalDate

/**
 * Four coordinated dimensions, using existing item keys only. Ownership is derived
 * from components, so old purchases and synced accounts need no new schema.
 * When themeKey is null, the matching colours are included in the ambiance.
 */
data class ThemePack(
    val key: String,
    val name: String,
    val iconKey: String,
    val bgKey: String,
    val modeKey: String,
    val themeKey: String? = null,
    val eggKey: String? = null,
)

val THEME_PACKS: List<ThemePack> = listOf(
    ThemePack("synthwave", "Synthwave", "neon", "citynight", "neon", themeKey = "synthwave"),
    ThemePack("galaxy", "Galaxie", "galaxy", "nebula", "cosmos", themeKey = "galaxy"),
    ThemePack("lava", "Lave", "lave", "magma", "basalt", themeKey = "lava"),
    ThemePack("forest", "Forêt", "foret", "mistforest", "forest", themeKey = "forest"),
    ThemePack("gold", "Or", "gold", "dunes", "sand", themeKey = "gold"),
    ThemePack("abyss", "Abysses", "ocean", "deepsea", "abyss", themeKey = "abyss"),
    ThemePack("candy", "Bonbon", "kawaii", "sakura", "cotton", themeKey = "candy"),
    ThemePack("sunset", "Coucher de soleil", "campfire", "mountains", "peach", themeKey = "sunset"),
    ThemePack("matcha", "Matcha", "bambou", "bamboo", "pistachio", themeKey = "matcha"),
    ThemePack("cyberpunk", "Cyberpunk", "eclair", "coderain", "neon", themeKey = "cyberpunk"),
    ThemePack("midnight", "Minuit", "lune", "lighthouse", "midnight", themeKey = "midnight"),
    ThemePack("cloud", "Nuage", "arcenciel", "balloons", "sky", themeKey = "cloud"),
    ThemePack("blaze", "Brasier", "phenix", "magma", "basalt", themeKey = "blaze"),
    ThemePack("zen", "Zen", "bougie", "dunes", "linen", themeKey = "zen"),
    ThemePack("retro", "Rétro 80", "vinyle", "citynight", "plum", themeKey = "retro"),
    ThemePack("tunnel", "Tunnel Express", "metro", "tunnel", "metro", themeKey = "tunnel"),
    ThemePack("loire", "Loire", "loire", "loire", "paper", themeKey = "loire"),
    ThemePack("angers", "Tramway d’Angers", "chateau", "angers", "nordic", themeKey = "angers"),
    ThemePack("nantes", "Nantes", "tramway", "nantes", "sky", themeKey = "nantes"),
    ThemePack("angevine", "Douceur angevine", "angevine", "angevine", "linen", themeKey = "angevine"),
    ThemePack("halloween", "Halloween", "halloween", "haunted", "plum", themeKey = "t-halloween"),
    ThemePack("noel", "Noël", "noel", "snowvillage", "forest", themeKey = "t-noel"),
    ThemePack("newyear", "Nouvel An", "nouvelan", "fireworks", "cosmos", themeKey = "t-newyear"),
    ThemePack("winter", "Hiver", "hiver", "snowpeak", "nordic", themeKey = "t-winter"),
    ThemePack("love", "Saint-Valentin", "valentin", "heartsky", "sakura", themeKey = "t-love"),
    ThemePack("easter", "Pâques", "paques", "eggmeadow", "mint", themeKey = "t-easter"),
    ThemePack("spring", "Printemps", "printemps", "flowerfield", "pistachio", themeKey = "t-spring"),
    ThemePack("summer", "Été", "ete", "summerbeach", "sky", themeKey = "t-summer"),
    ThemePack("autumn", "Automne", "automne", "autumnwood", "cocoa", themeKey = "t-autumn"),
    ThemePack("borealis", "Aurore boréale", "aurore", "borealis", "emerald"),
    ThemePack("rain", "Pluie sur la vitre", "vague", "rainwindow", "ink"),
    ThemePack("planet", "Planète", "fusee", "planet", "cosmos"),
    ThemePack("lagoon", "Lagon", "ocean", "lagoon", "mint"),
    ThemePack("lighthouse", "Phare", "phare", "lighthouse", "ink"),
    ThemePack("vineyard", "Vignes d’Anjou", "angevine", "vineyard", "linen"),
    ThemePack("balloons", "Montgolfières", "montgolfiere", "balloons", "sky"),
    ThemePack("jellyfish", "Méduses", "meduse", "jellyfish", "abyss"),
    ThemePack("bamboo", "Bambouseraie", "bambou", "bamboo", "pistachio"),
    ThemePack("nighttrain", "Train de nuit", "metro", "nighttrain", "steel"),
    ThemePack("paris", "Toits de Paris", "eiffel", "rooftops", "plum"),
    ThemePack("canyon", "Canyon", "boussole", "canyon", "coral"),
    ThemePack("savanna", "Savane", "boussole", "savanna", "sand"),
    ThemePack("lanterns", "Lanternes célestes", "lanterne", "lanterns", "wine"),
    ThemePack("icefield", "Banquise", "sommet", "icefield", "nordic"),
    ThemePack("xmastree", "Sapin de Noël", "sapin", "xmastree", "forest"),
    ThemePack("valentine", "Cœurs ardents", "coeur", "valentine", "sakura"),
    ThemePack("loiresummer", "Été sur la Loire", "velo", "loiresummer", "mint"),
    ThemePack("graphite", "Graphite", "mono", "citynight", "graphite"),
    ThemePack("cocoa", "Cacao", "cafe", "rainwindow", "cocoa"),
    ThemePack("lavender", "Lavande", "cristal", "sakura", "lavender"),
    ThemePack("sepia", "Sépia", "vinyle", "rooftops", "sepia"),
    ThemePack("lemon", "Citron", "eclair", "summerbeach", "lemon"),
    ThemePack("pixel", "Pixel", "pixel", "coderain", "neon"),
    ThemePack("dragon", "Dragon d’émeraude", "dragon", "bamboo", "emerald"),
    ThemePack("sakura", "Sakura", "sakura", "sakura", "sakura"),
    ThemePack("glace", "Glace", "glace", "summerbeach", "peach"),
    ThemePack("chat", "Chat", "chat", "sakura", "lavender"),
    ThemePack("sablier", "Sablier", "sablier", "dunes", "sepia"),
    ThemePack("trophee", "Trophée", "trophee", "mountains", "wine"),
    ThemePack("pousse", "Pousse", "pousse", "bamboo", "mint"),
    ThemePack("citrouille", "Citrouille", "citrouille", "haunted", "plum", themeKey = "t-halloween"),
    ThemePack("tram", "Ville et tramway", "tramway", "tram", "sky"),
    ThemePack("sco", "SCO · Noir & Blanc", "sco", "kopa", "sco", eggKey = "sco"),
    ThemePack("srfc", "SRFC · Rouge & Noir", "srfc", "ecran", "srfc", eggKey = "srfc"),
    ThemePack("angers-secret", "Anjou secret", "angevine", "angevine", "angers", eggKey = "angers"),
    ThemePack("psg", "PSG · Bleu & Rouge", "psg", "psg", "psg", eggKey = "psg"),
    ThemePack("fcb", "FC Barcelone · Blaugrana", "fcb", "fcb", "fcb", eggKey = "fcb"),
    ThemePack("fcn", "FC Nantes · Jaune & Vert", "fcn", "fcn", "fcn", eggKey = "fcn"),
)

fun themePack(key: String): ThemePack? = THEME_PACKS.firstOrNull { it.key == key }

val ThemePack.mode: ShopMode get() = (SHOP_MODES + Eggs.SECRET_MODES).first { it.key == modeKey }
val ThemePack.background: ShopBg get() = ALL_BGS.first { it.key == bgKey }
val ThemePack.colourTheme: ShopTheme? get() = themeKey?.let { key -> SHOP_THEMES.first { it.key == key } }
val ThemePack.colors: List<String> get() = colourTheme?.colors ?: mode.accent
val ThemePack.fx: String get() = colourTheme?.fx.orEmpty()

data class PackComponent(val key: String, val price: Int, val season: String? = null)

fun ThemePack.components(): List<PackComponent> {
    val icon = SHOP_ICONS.getValue(iconKey)
    return listOf(
        PackComponent("icon:$iconKey", icon.price, icon.season),
        PackComponent("bg:$bgKey", background.price, background.season),
        PackComponent("mode:$modeKey", mode.price),
    ) + listOfNotNull(colourTheme?.let { PackComponent("theme:${it.key}", it.price, it.season) })
}

/** A discovery grants the whole pack only when every paid component is exclusive. */
val ThemePack.discoveryCompletesPack: Boolean get() = eggKey != null &&
    components().all { it.price <= 0 || secretPackItem(it.key) }

/** Older users may retain the reward even if its discovery claim was trimmed. */
fun Pg.packUnlocked(pack: ThemePack, u: User? = me()): Boolean =
    pack.eggKey == null || (u != null && (pack.eggKey in eggsFound(u) || "mode:${pack.modeKey}" in u.shopOwned))

private fun Pg.ownsPackComponent(pack: ThemePack, item: PackComponent, u: User?): Boolean {
    if (owns(item.key, item.price, u)) return true
    if (pack.eggKey != null && packUnlocked(pack, u) && secretPackItem(item.key)) return true
    // Earlier illustrated colour themes already included their own scene: do not sell that scene twice.
    return item.key.startsWith("bg:") && SHOP_THEMES.any {
        it.bg == "s:${pack.bgKey}" && owns("theme:${it.key}", it.price, u)
    }
}

internal fun secretPackItem(key: String): Boolean =
    Eggs.SECRET_MODES.any { "mode:${it.key}" == key } || Eggs.SECRET_BGS.any { "bg:${it.key}" == key } ||
        (key.startsWith("icon:") && SHOP_ICONS[key.removePrefix("icon:")]?.eggKey != null)

/** An illustrated theme already includes its scene. Allocate that overlap once, even for a new buyer. */
private fun ThemePack.componentCost(item: PackComponent): Int {
    val theme = colourTheme ?: return item.price
    if (theme.bg != "s:$bgKey") return item.price
    return when (item.key) {
        "bg:$bgKey" -> minOf(background.price, theme.price)
        "theme:${theme.key}" -> maxOf(0, theme.price - background.price)
        else -> item.price
    }
}

data class PackQuote(
    val fullPrice: Int,
    val remainingPrice: Int,
    val price: Int,
    val missingKeys: List<String>,
    val unlocked: Boolean,
    val available: Boolean,
) {
    val owned: Boolean get() = unlocked && missingKeys.isEmpty()
    val saving: Int get() = remainingPrice - price
}

/** 25% off missing permanent entitlements; a free seasonal preview is not permanent ownership. */
fun Pg.packQuote(pack: ThemePack, u: User? = me(), date: LocalDate = todayDate()): PackQuote {
    val parts = pack.components()
    val missing = parts.filterNot { ownsPackComponent(pack, it, u) }
    val remaining = missing.filterNot { secretPackItem(it.key) }.sumOf { pack.componentCost(it) }
    val unlocked = packUnlocked(pack, u)
    return PackQuote(
        fullPrice = parts.filterNot { secretPackItem(it.key) }.sumOf { pack.componentCost(it) },
        remainingPrice = remaining,
        price = ((remaining.toLong() * 75 + 99) / 100).toInt(),
        missingKeys = missing.map { it.key },
        unlocked = unlocked,
        available = u != null && unlocked && missing.all { it.season == null || seasonWindow(it.season, date) != null },
    )
}

/** Grant only the original secret rewards. Never grant public items or bonus flames during reconciliation. */
fun Pg.reconcileThemePacks(): Boolean {
    val u = me() ?: return false
    val rewardKeys = THEME_PACKS.filter { it.eggKey != null && packUnlocked(it, u) }
        .flatMap { pack -> pack.components().map { it.key } }
        .filter(::secretPackItem)
    val missing = rewardKeys.filterNot { it in u.shopOwned }.distinct()
    if (missing.isEmpty()) return false
    u.shopOwned = (u.shopOwned + missing).distinct().toMutableList()
    store.commit()
    return true
}

/** Atomic in the model: validate everything before charging once and granting all components. */
fun Pg.buyPack(key: String, maximumPrice: Int? = null, date: LocalDate = todayDate()): String? {
    val u = me() ?: return "Connecte-toi pour acheter."
    val pack = themePack(key) ?: return "Ce pack n’existe pas."
    val quote = packQuote(pack, u, date)
    if (!quote.unlocked) return "Ce pack se débloque en découvrant sa surprise."
    if (!quote.available) return "Un élément manquant est une édition limitée : ce pack revient à sa saison."
    if (maximumPrice != null && quote.price > maximumPrice) return "Le prix du pack a changé. Vérifie son nouveau prix avant de confirmer."
    val balance = wallet(u)
    if (quote.price > balance) return "Il te manque ${quote.price - balance} flammes."
    val keys = pack.components().filter { it.price > 0 }.map { it.key }
    val owned = (u.shopOwned + keys).distinct()
    if (quote.price == 0 && owned == u.shopOwned) return null
    u.shopOwned = owned.toMutableList()
    u.shopSpent += quote.price
    store.commit()
    return null
}

fun Pg.packMatches(pack: ThemePack, u: User? = me()): Boolean = u != null &&
    u.settings.theme == "m:${pack.modeKey}" && u.settings.bg == "s:${pack.bgKey}" &&
    u.settings.accentCustom == pack.colors && u.settings.fx == pack.fx

/** Core settings only; Android applies the validated launcher icon and device overrides together. */
fun Pg.applyPack(key: String): String? {
    val u = me() ?: return "Connecte-toi pour appliquer un pack."
    val pack = themePack(key) ?: return "Ce pack n’existe pas."
    if (!packQuote(pack, u).owned) return "Tu dois posséder tous les éléments de ce pack avant de l’appliquer."
    u.shopOwned = (u.shopOwned + pack.components().filter { it.price > 0 }.map { it.key }).distinct().toMutableList()
    u.settings.theme = "m:${pack.modeKey}"
    u.settings.bg = "s:${pack.bgKey}"
    u.settings.accentCustom = pack.colors
    u.settings.fx = pack.fx
    store.commit()
    return null
}
