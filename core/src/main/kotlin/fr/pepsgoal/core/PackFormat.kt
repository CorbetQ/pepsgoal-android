package fr.pepsgoal.core

import fr.pepsgoal.core.model.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/* ---------------------------------------------------------------- Packs de thème personnels (format versionné) */

/**
 * Format d'échange d'un pack de thème (9.2) : le même JSON sert à l'export et à l'import (copier/coller, fichier) et
 * au contenu « theme_pack » téléchargé depuis le serveur (docs/content-api.md côté serveur). Champs inconnus ignorés,
 * [schemaVersion] plus récente que [PACK_SCHEMA_VERSION] refusée : l'application doit être mise à jour.
 */
const val PACK_SCHEMA_VERSION = 1
const val PACK_TYPE = "theme_pack"

/** Couleurs du pack, toutes en « #RRGGBB ». [accent] : les trois arrêts de la flamme ; [deco] : formes décoratives du fond. */
@Serializable
data class PackColors(
    val bg: String, val ink: String, val surface: String, val surface2: String, val edge: String,
    val accent: List<String>, val deco: List<String>,
)

/**
 * Fond d'écran : [kind] « scene » (scène illustrée [key]), « free » (fond gratuit [key] : pop, aurora…),
 * « gradient » (dégradé vertical de [colors]), « image » (image personnelle ou fichier [file] du contenu téléchargé).
 */
@Serializable
data class PackWallpaper(
    val kind: String, val key: String? = null, val colors: List<String>? = null, val file: String? = null,
    /** 10.0 « PepsGoal Style » : premier plan détouré (WebP avec alpha) ; la météo passe entre le ciel et lui. */
    val foreground: String? = null,
    /** Adaptation météo voulue (ciel teinté selon le temps, pluie et neige derrière le premier plan). */
    val weather: Boolean? = null,
    /** Image stylisée par le serveur. */
    val styled: Boolean? = null,
)

@Serializable
data class ThemePackSpec(
    val schemaVersion: Int = PACK_SCHEMA_VERSION,
    val type: String = PACK_TYPE,
    val id: String,
    val name: String,
    val emoji: String = "🎨",
    val dark: Boolean = false,
    val colors: PackColors,
    val wallpaper: PackWallpaper = PackWallpaper("free", "pop"),
    /** Icône de l'application (clé de la boutique), facultative. */
    val icon: String? = null,
    /** Effet animé (neige, pétales…), facultatif. */
    val fx: String? = null,
    val author: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Pack invalide : la liste des problèmes, en français. */
class PackFormatError(val errors: List<String>) : Exception(errors.joinToString(" "))

/** Fonds gratuits utilisables par un pack (sans « Mon image », qui passe par le type « image »). */
val PACK_FREE_BGS: List<String> = BGS.map { it.first }.filter { it != "custom" }
/** Effets animés connus. */
val PACK_FX: List<String> = listOf("snow", "petals", "leaves", "hearts", "embers", "bubbles", "confetti", "stars")
val PACK_WALLPAPER_KINDS = listOf("scene", "free", "gradient", "image")

private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
private val PACK_ID = Regex("^[a-z0-9][a-z0-9-]{1,63}$")
const val PACK_NAME_MAX = 40
const val PACK_EMOJI_MAX = 16
/** Taille maximale d'un pack collé ou importé (le JSON seul, sans image). */
const val PACK_JSON_MAX = 16 * 1024

private val PACK_JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
private val PACK_JSON_PRETTY = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false; prettyPrint = true }

/** Nom de fichier d'un pack : sans chemin, 128 caractères au plus. */
fun safePackFile(n: String): Boolean = n.isNotBlank() && n.length <= 128 && !n.contains("..") && !n.contains('/') && !n.contains('\\')

object PackFormat {
    fun isHex(s: String?): Boolean = s != null && HEX.matches(s)

    /** Problèmes bloquants d'un pack (liste vide : pack valide). [checkCatalog] : scène, icône et effet doivent exister. */
    fun validate(p: ThemePackSpec, checkCatalog: Boolean = true): List<String> {
        val e = mutableListOf<String>()
        if (p.schemaVersion < 1) e += "Version du format invalide."
        if (p.schemaVersion > PACK_SCHEMA_VERSION) e += "Ce pack utilise un format plus récent : mets PepsGoal à jour pour l’ouvrir."
        if (p.type != PACK_TYPE) e += "Ce fichier n’est pas un pack de thème."
        if (!PACK_ID.matches(p.id)) e += "Identifiant du pack invalide."
        val name = p.name.trim()
        if (name.isEmpty()) e += "Donne un nom au pack."
        if (name.length > PACK_NAME_MAX) e += "Le nom du pack dépasse $PACK_NAME_MAX caractères."
        if (p.emoji.isBlank() || p.emoji.length > PACK_EMOJI_MAX) e += "Emoji du pack invalide."
        val c = p.colors
        listOf("fond" to c.bg, "encre" to c.ink, "surface" to c.surface, "creux" to c.surface2, "bord" to c.edge).forEach { (label, v) ->
            if (!isHex(v)) e += "Couleur « $label » invalide : $v."
        }
        if (c.accent.size != 3 || !c.accent.all(::isHex)) e += "L’accent doit compter trois couleurs « #RRGGBB »."
        if (c.deco.isEmpty() || c.deco.size > 4 || !c.deco.all(::isHex)) e += "Les couleurs décoratives doivent compter une à quatre couleurs « #RRGGBB »."
        val w = p.wallpaper
        when (w.kind) {
            "scene" -> if (w.key.isNullOrBlank() || (checkCatalog && ALL_BGS.none { it.key == w.key })) e += "Scène inconnue : ${w.key}."
            "free" -> if (w.key == null || w.key !in PACK_FREE_BGS) e += "Fond gratuit inconnu : ${w.key}."
            "gradient" -> { val g = w.colors.orEmpty(); if (g.size !in 2..4 || !g.all(::isHex)) e += "Le dégradé doit compter deux à quatre couleurs « #RRGGBB »." }
            "image" -> {
                if (w.file != null && !safePackFile(w.file)) e += "Nom de fichier d’image invalide."
                if (w.foreground != null && !safePackFile(w.foreground)) e += "Nom de fichier du premier plan invalide."
            }
            else -> e += "Type de fond inconnu : ${w.kind}."
        }
        if (p.icon != null && (p.icon.isBlank() || (checkCatalog && p.icon !in SHOP_ICONS))) e += "Icône inconnue : ${p.icon}."
        if (p.fx != null && p.fx.isNotEmpty() && p.fx !in PACK_FX) e += "Effet animé inconnu : ${p.fx}."
        if ((p.author?.length ?: 0) > 60) e += "Le nom de l’auteur dépasse 60 caractères."
        return e
    }

    /** Couleurs en majuscules, nom et emoji rognés, effet vide retiré. */
    fun normalize(p: ThemePackSpec): ThemePackSpec = p.copy(
        name = p.name.trim(), emoji = p.emoji.trim(), fx = p.fx?.takeIf { it.isNotEmpty() },
        colors = p.colors.copy(bg = p.colors.bg.uppercase(), ink = p.colors.ink.uppercase(), surface = p.colors.surface.uppercase(),
            surface2 = p.colors.surface2.uppercase(), edge = p.colors.edge.uppercase(), accent = p.colors.accent.map { it.uppercase() }, deco = p.colors.deco.map { it.uppercase() }),
        wallpaper = p.wallpaper.copy(colors = p.wallpaper.colors?.map { it.uppercase() }),
    )

    /** Lit un pack (export, fichier, contenu du serveur). Lève [PackFormatError] avec des messages lisibles. */
    fun parse(text: String, checkCatalog: Boolean = true): ThemePackSpec {
        if (text.length > PACK_JSON_MAX) throw PackFormatError(listOf("Ce pack est trop volumineux."))
        val obj = try { PACK_JSON.parseToJsonElement(text.trim()) as? JsonObject } catch (e: Exception) { null }
            ?: throw PackFormatError(listOf("Ce texte n’est pas un pack de thème PepsGoal (JSON illisible)."))
        // Version et type vérifiés avant le décodage : un format futur donne un message clair, pas une erreur de champ
        val version = try { obj["schemaVersion"]?.jsonPrimitive?.intOrNull } catch (e: Exception) { null }
            ?: throw PackFormatError(listOf("Version du format absente (schemaVersion)."))
        if (version > PACK_SCHEMA_VERSION) throw PackFormatError(listOf("Ce pack utilise un format plus récent : mets PepsGoal à jour pour l’ouvrir."))
        val type = try { obj["type"]?.jsonPrimitive?.contentOrNull } catch (e: Exception) { null }
        if (type != PACK_TYPE) throw PackFormatError(listOf("Ce fichier n’est pas un pack de thème."))
        val spec = try { PACK_JSON.decodeFromJsonElement(ThemePackSpec.serializer(), obj) } catch (e: Exception) {
            throw PackFormatError(listOf("Pack incomplet ou mal formé."))
        }
        val n = normalize(spec)
        validate(n, checkCatalog).takeIf { it.isNotEmpty() }?.let { throw PackFormatError(it) }
        return n
    }

    fun parseOrNull(text: String, checkCatalog: Boolean = true): ThemePackSpec? = try { parse(text, checkCatalog) } catch (e: PackFormatError) { null }

    fun encode(p: ThemePackSpec, pretty: Boolean = true): String =
        (if (pretty) PACK_JSON_PRETTY else PACK_JSON).encodeToString(ThemePackSpec.serializer(), p)

    /** Export partageable : sans dates locales ; une image personnelle n'est pas jointe et devient un dégradé de ses couleurs. */
    fun exportable(p: ThemePackSpec): ThemePackSpec = p.copy(createdAt = 0, updatedAt = 0,
        wallpaper = if (p.wallpaper.kind == "image") PackWallpaper("gradient", colors = listOf(p.colors.deco.first(), p.colors.bg)) else p.wallpaper)

    fun encodeList(list: List<ThemePackSpec>): String = PACK_JSON.encodeToString(kotlinx.serialization.builtins.ListSerializer(ThemePackSpec.serializer()), list)
    fun decodeList(text: String): List<ThemePackSpec> = try {
        PACK_JSON.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ThemePackSpec.serializer()), text)
            .map(::normalize).filter { validate(it, checkCatalog = false).isEmpty() }
    } catch (e: Exception) { emptyList() }
}

/** Nouvel identifiant de pack personnel (« perso-… »). */
fun newPackId(random: kotlin.random.Random = kotlin.random.Random.Default): String =
    "perso-" + (1..10).map { "abcdefghijkmnpqrstuvwxyz23456789"[random.nextInt(32)] }.joinToString("")

/** Point de départ de l'éditeur : les couleurs Braise Pop d'origine, claires ou sombres. */
fun starterPack(dark: Boolean, id: String = newPackId(), now: Long = nowMs()): ThemePackSpec = ThemePackSpec(
    id = id, name = "Mon pack", emoji = if (dark) "🌙" else "🔥", dark = dark,
    colors = if (dark) PackColors("#170F1C", "#FFF4EC", "#241A2B", "#2F2337", "#3A2D43", ACCENTS[0].c.map { it.uppercase() }, listOf("#3A1F2E", "#2E1B3A"))
    else PackColors("#FFF7F0", "#2A1420", "#FFFFFF", "#FFEDE1", "#F2DED1", ACCENTS[0].c.map { it.uppercase() }, listOf("#FFE2CC", "#FFD6E8")),
    wallpaper = PackWallpaper("free", "pop"), createdAt = now, updatedAt = now,
)

/** Copie d'un pack sous un nouvel identifiant (« Dupliquer », import). */
fun ThemePackSpec.duplicate(id: String = newPackId(), now: Long = nowMs(), suffix: String = " (copie)"): ThemePackSpec =
    copy(id = id, name = (name.trim() + suffix).take(PACK_NAME_MAX), createdAt = now, updatedAt = now)

/* ---------------------------------------------------------------- Contraste (WCAG 2.x) */

/** Un contrôle de lisibilité : [ratio] mesuré, [min] exigé. */
data class ContrastCheck(val label: String, val ratio: Double, val min: Double = 4.5) { val ok: Boolean get() = ratio >= min - 1e-9 }

/** Rapport de contraste affiché « 4,4:1 » : arrondi à l'inférieur, un 4,48 ne s'affiche jamais « 4,5 ». */
fun fmtContrast(r: Double): String = (Math.floor(r * 10) / 10).let { v -> (if (v % 1.0 == 0.0) "${v.toInt()}" else "%.1f".format(java.util.Locale.FRANCE, v)) + ":1" }

object PackContrast {
    const val PLUM_HEX = "#2A1420"
    const val WHITE_HEX = "#FFFFFF"

    private fun rgb(hex: String): IntArray = hex.removePrefix("#").chunked(2).map { it.toInt(16) }.toIntArray()
    private fun hexOf(r: Int, g: Int, b: Int) = "#%02X%02X%02X".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    private fun lin(c: Int): Double { val s = c / 255.0; return if (s <= .03928) s / 12.92 else Math.pow((s + .055) / 1.055, 2.4) }

    /** Luminance relative (0 : noir, 1 : blanc). */
    fun luminance(hex: String): Double { val (r, g, b) = rgb(hex).let { Triple(it[0], it[1], it[2]) }; return .2126 * lin(r) + .7152 * lin(g) + .0722 * lin(b) }
    /** Rapport de contraste entre deux couleurs opaques (1 à 21). */
    fun ratio(a: String, b: String): Double { val x = luminance(a); val y = luminance(b); return (maxOf(x, y) + .05) / (minOf(x, y) + .05) }

    /** Mélange [a] → [b] de la fraction [k]. */
    fun mix(a: String, b: String, k: Double): String {
        val x = rgb(a); val y = rgb(b)
        fun ch(i: Int) = Math.round(x[i] + (y[i] - x[i]) * k).toInt()
        return hexOf(ch(0), ch(1), ch(2))
    }

    /**
     * Garde la teinte de [fg] mais la pousse vers le noir ou le blanc jusqu'à [min] contre chacun des [bgs]
     * (même algorithme que `readable` de l'interface, par pas de 5 %). Repli : noir ou blanc pur.
     */
    fun readableOn(fg: String, bgs: List<String>, min: Double = 4.5): String {
        if (bgs.all { ratio(fg, it) >= min }) return fg
        val towardBlack = bgs.map(::luminance).average() > .18
        val target = if (towardBlack) "#000000" else WHITE_HEX
        var k = .05
        while (k <= 1.0001) { val c = mix(fg, target, k); if (bgs.all { ratio(c, it) >= min }) return c; k += .05 }
        // Fonds trop différents (un clair, un sombre) : la meilleure des deux encres pures
        return listOf("#000000", WHITE_HEX).maxBy { c -> bgs.minOf { ratio(c, it) } }
    }

    /** Texte posé sur la flamme : prune ou blanc, celui qui se lit le mieux sur les trois arrêts (comme l'interface). */
    fun onAccent(accent: List<String>): String =
        if (accent.minOf { ratio(PLUM_HEX, it) } >= accent.minOf { ratio(WHITE_HEX, it) }) PLUM_HEX else WHITE_HEX

    /** Les vérifications de l'éditeur, dans l'ordre d'affichage. */
    fun checks(p: ThemePackSpec): List<ContrastCheck> {
        val c = p.colors
        if (!listOf(c.bg, c.ink, c.surface, c.surface2).all(PackFormat::isHex) || c.accent.size != 3 || !c.accent.all(PackFormat::isHex)) return emptyList()
        val on = onAccent(c.accent)
        return listOf(
            ContrastCheck("Texte sur le fond", ratio(c.ink, c.bg)),
            ContrastCheck("Texte sur les cartes", ratio(c.ink, c.surface)),
            ContrastCheck("Texte sur les creux", ratio(c.ink, c.surface2)),
            ContrastCheck("Texte sur la flamme", c.accent.minOf { ratio(on, it) }),
        )
    }

    fun allOk(p: ThemePackSpec): Boolean = checks(p).all { it.ok }

    /** Correction proposée : l'encre poussée jusqu'à 4,5:1 sur le fond et les surfaces, la flamme ajustée pour son texte. */
    fun fix(p: ThemePackSpec): ThemePackSpec {
        val c = p.colors
        val ink = readableOn(c.ink, listOf(c.bg, c.surface, c.surface2))
        // Fond et surfaces trop différents (un clair, un sombre) : l'encre seule ne suffit pas, ils s'éloignent d'elle
        fun away(x: String) = if (ratio(ink, x) >= 4.5) x else readableOn(x, listOf(ink))
        val bg = away(c.bg); val surface = away(c.surface); val surface2 = away(c.surface2)
        val on = onAccent(c.accent)
        val away = if (on == PLUM_HEX) WHITE_HEX else "#000000"
        val accent = c.accent.map { stop ->
            var s = stop; var k = .05
            while (ratio(on, s) < 4.5 && k <= 1.0001) { s = mix(stop, away, k); k += .05 }
            s
        }
        return p.copy(colors = c.copy(bg = bg, ink = ink, surface = surface, surface2 = surface2, accent = accent))
    }
}

/* ---------------------------------------------------------------- Bibliothèque et application */

/** Clé de réglage « theme » d'un pack personnel ou téléchargé : une ambiance « m:@<id> ». */
fun ThemePackSpec.themeSetting(): String = "m:@$id"

/** L'ambiance équivalente : fond, encre, surfaces, décor et accent du pack, sans suivre le jour et la nuit. */
fun ThemePackSpec.toMode(): ShopMode = ShopMode("@$id", name, "Pack personnel", 0, dark, colors.bg, colors.ink, colors.surface,
    colors.deco, colors.accent, surface = colors.surface, surface2 = colors.surface2, edge = colors.edge)

/** Réglage « bg » du fond d'écran du pack (« custom » pour une image : l'application la copie d'abord en place). */
fun PackWallpaper.bgSetting(): String = when (kind) {
    "scene" -> "s:$key"
    "free" -> key ?: "pop"
    "gradient" -> "g:" + colors.orEmpty().joinToString(",")
    "image" -> "custom"
    else -> "pop"
}

/** Couleurs d'un fond « g:#AAAAAA,#BBBBBB » (null si ce n'en est pas un). */
fun gradientColors(bg: String): List<String>? = if (!bg.startsWith("g:")) null else bg.removePrefix("g:").split(',').filter(PackFormat::isHex).takeIf { it.size >= 2 }

/**
 * Packs connus de l'appareil : personnels (éditeur, import) et téléchargés (validés). L'application la remplit au
 * démarrage puis à chaque changement ; le thème « m:@<id> » s'y résout.
 */
object PackLibrary {
    @Volatile var personal: List<ThemePackSpec> = emptyList()
    @Volatile var online: List<ThemePackSpec> = emptyList()
    fun find(id: String): ThemePackSpec? = personal.firstOrNull { it.id == id } ?: online.firstOrNull { it.id == id }
    fun forTheme(theme: String?): ThemePackSpec? = if (theme != null && theme.startsWith("m:@")) find(theme.removePrefix("m:@")) else null
}

/** Effets animés possédés : ceux des thèmes achetés (comme Apparence). */
fun Pg.ownedFx(u: User? = me()): Set<String> = SHOP_THEMES.filter { it.fx.isNotEmpty() && owns("theme:${it.key}", it.price, u) }.map { it.fx }.toSet()

/** Scènes illustrées possédées (ou offertes en ce moment). */
fun Pg.ownedScenes(u: User? = me()): List<ShopBg> = ALL_BGS.filter { ownsBg(it, u) }

/** Résultat d'une application : éléments non possédés ignorés, icône à poser par l'application. */
data class PackApply(val skipped: List<String>, val icon: String?)

/**
 * Applique un pack : ambiance du pack, accent, fond et effet. Une scène, une icône ou un effet non possédés sont
 * ignorés (le pack ne contourne pas la boutique). [imageReady] : l'application a copié l'image du pack en place.
 */
fun Pg.applyPackSpec(p: ThemePackSpec, imageReady: Boolean = false): PackApply {
    val u = me() ?: return PackApply(listOf("Connecte-toi pour appliquer un pack."), null)
    val skipped = mutableListOf<String>()
    val s = u.settings
    s.theme = p.themeSetting()
    s.accentCustom = p.colors.accent
    val w = p.wallpaper
    s.bg = when {
        w.kind == "scene" -> ALL_BGS.firstOrNull { it.key == w.key }?.takeIf { ownsBg(it, u) }?.let { "s:${it.key}" }
            ?: run { skipped += "scène non possédée"; gradientSetting(p) }
        w.kind == "image" -> if (imageReady) "custom" else { skipped += "image absente"; gradientSetting(p) }
        else -> w.bgSetting()
    }
    s.fx = p.fx?.takeIf { it in ownedFx(u) } ?: run { if (!p.fx.isNullOrEmpty()) skipped += "effet non possédé"; "" }
    val icon = p.icon?.let { k -> SHOP_ICONS[k]?.takeIf { owns("icon:$k", it.price, u) }?.let { k } ?: run { skipped += "icône non possédée"; null } }
    store.commit()
    return PackApply(skipped, icon)
}

private fun gradientSetting(p: ThemePackSpec) = "g:" + listOf(p.colors.deco.first(), p.colors.bg).joinToString(",")

/** Le pack est l'ambiance en cours. */
fun Pg.packSpecActive(p: ThemePackSpec, u: User? = me()): Boolean = u?.settings?.theme == p.themeSetting()

/* ---------------------------------------------------------------- Contenus achetés en ligne */

/**
 * Clé de possession d'un contenu téléchargé : « content:<id>@<prix> ». Le prix payé voyage avec la clé : le
 * recalcul des prix de la boutique (repriceShop) le compte, même sans catalogue en mémoire.
 */
fun contentKey(id: String, price: Int): String = "content:$id@${price.coerceAtLeast(0)}"

/** Prix payé lu dans une clé de contenu. */
fun contentPaid(key: String): Int? = if (key.startsWith("content:")) key.substringAfterLast('@', "").toIntOrNull() else null

fun Pg.ownsContent(id: String, u: User? = me()): Boolean = u?.shopOwned?.any { it == "content:$id" || it.startsWith("content:$id@") } == true

/** Obtient un contenu en ligne : gratuit, il est ajouté à la collection ; payant, il passe par l'achat habituel. */
fun Pg.obtainContent(id: String, price: Int): String? {
    val u = me() ?: return "Connecte-toi pour obtenir ce contenu."
    if (ownsContent(id, u)) return null
    val key = contentKey(id, price)
    if (price <= 0) { u.shopOwned = (u.shopOwned + key).toMutableList(); store.commit(); return null }
    return buy(key, price)
}

/* ---------------------------------------------------------------- Sélecteur de couleur de l'éditeur */

object PackColor {
    /** Teinte (0–360), saturation et luminosité (0–1) d'une couleur « #RRGGBB ». */
    fun toHsl(hex: String): DoubleArray {
        val v = hex.removePrefix("#").chunked(2).map { it.toInt(16) / 255.0 }
        val mx = v.max(); val mn = v.min(); val l = (mx + mn) / 2; val d = mx - mn
        if (d == 0.0) return doubleArrayOf(0.0, 0.0, l)
        val s = if (l > .5) d / (2 - mx - mn) else d / (mx + mn)
        val h = when (mx) { v[0] -> ((v[1] - v[2]) / d + (if (v[1] < v[2]) 6 else 0)); v[1] -> (v[2] - v[0]) / d + 2; else -> (v[0] - v[1]) / d + 4 } * 60
        return doubleArrayOf(h, s, l)
    }

    fun fromHsl(h: Double, s: Double, l: Double): String {
        val hh = ((h % 360) + 360) % 360; val ss = s.coerceIn(0.0, 1.0); val ll = l.coerceIn(0.0, 1.0)
        val a = ss * minOf(ll, 1 - ll)
        fun f(n: Int): Int { val k = (n + hh / 30) % 12; return Math.round((ll - a * maxOf(-1.0, minOf(k - 3, 9 - k, 1.0))) * 255).toInt() }
        return "#%02X%02X%02X".format(f(0), f(8), f(4))
    }
}

/** Nuancier de l'éditeur : neutres clairs et sombres, puis néons Braise Pop. */
val PACK_PALETTE: List<String> = listOf(
    "#FFFFFF", "#FFF7F0", "#FFEDE1", "#F2DED1", "#E8EDF2", "#F6F1E7", "#2A1420", "#170F1C", "#241A2B", "#0A1024", "#121315", "#000000",
    "#FFB000", "#FF4A12", "#FF3D96", "#FFD23F", "#B6FF2E", "#2EF2B0", "#22D3FF", "#4D9BFF", "#A98BFF", "#FF7AD9", "#FF5A5A", "#00D1B2",
)
