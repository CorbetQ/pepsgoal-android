package fr.pepsgoal.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.R
import fr.pepsgoal.core.ACCENTS
import fr.pepsgoal.core.validAccent

val Unbounded = FontFamily(Font(R.font.unbounded_medium, FontWeight.Medium), Font(R.font.unbounded_bold, FontWeight.Bold), Font(R.font.unbounded_extrabold, FontWeight.ExtraBold))
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal), Font(R.font.manrope_medium, FontWeight.Medium), Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold), Font(R.font.manrope_extrabold, FontWeight.ExtraBold)
)

fun hex(s: String): Color = Color(android.graphics.Color.parseColor(s))

/** Encre prune : seule couleur de texte posée sur un aplat vif (néons, flamme, or, rose). */
val PLUM = Color(0xFF2A1420)

/** Couleur d'un type d'objectif : néon en aplat (tuile, case faite, barre) et encre (texte du type sur une carte). */
@Immutable
data class TypeColor(val c: Color, val ink: Color) {
    /** « Marche » de l'aplat : le néon assombri vers le prune. */
    val step: Color get() = c.step()
}

/** Teinte de la marche d'un aplat : la même couleur, assombrie vers le prune (32 %). */
fun Color.step(): Color = lerp(this, Color(0xFF5A1030), .32f)

/** Jetons de couleur de PepsGoal (variables CSS de la version web : --bg, --ink, --glass, --flame…). */
@Immutable
data class Pal(
    val dark: Boolean,
    val oled: Boolean,
    val bg: Color, val ink: Color, val ink2: Color, val ink3: Color,
    val glass: Color, val glassStrong: Color, val glassEdge: Color, val line: Color, val scrim: Color,
    val flame2: Color, val flame: Color, val pink: Color, val onAccent: Color,
    /** Accent utilisable comme couleur de texte (assombri sur fond clair, éclairci sur fond sombre si besoin). */
    val flameInk: Color,
    val ok: Color, val danger: Color, val focus: Color,
    val blobs: List<Color>, val blobAlpha: Float,
    val types: Map<String, TypeColor>,
    val materialYou: Boolean,
    val contrast: Boolean, val transp: Boolean, val mono: Boolean, val motion: Boolean,
    val blur: Float,
    /** Mode Lecture facile : statuts en toutes lettres, mots simples, sans effets. */
    val easy: Boolean = false,
    /** Grandes cibles tactiles (48 dp) : TalkBack actif ou Lecture facile. */
    val big: Boolean = false,
    /** Mode une main. */
    val oneHand: Boolean = false,
    /* Braise Pop : surfaces opaques, bord et marche, encre prune sur les aplats */
    /** Cartes, lignes, barre d'onglets, boutons secondaires. */
    val surface: Color = Color.White,
    /** Creux : rails, pastilles de compteur, séparateurs épais. */
    val surface2: Color = Color(0xFFFFEDE1),
    /** Bord (2 dp) et marche des éléments neutres. */
    val edge: Color = Color(0xFFF2DED1),
    /** Contour d'une case « à faire », interrupteur éteint (≥ 3:1). */
    val control: Color = Color(0xFF9C8273),
    /** Texte et icônes sur tout aplat vif. */
    val plum: Color = PLUM,
    /** Marche des éléments en dégradé de la flamme. */
    val flameStep: Color = Color(0xFFC2185B),
    val gold: Color = Color(0xFFFFD23F),
    val ice: Color = Color(0xFF22D3FF),
    /** Formes décoratives du fond (aplats immobiles). */
    val deco: List<Color> = listOf(Color(0xFFFFE2CC), Color(0xFFFFD6E8))
) {
    val accent get() = Brush.linearGradient(listOf(flame2, flame, pink))
    /** Rail des barres et anneaux. */
    val rail: Color get() = if (dark && !contrast) Color(0xFF120B16) else surface2
    fun type(t: String) = types[t] ?: types.getValue("routine")
}

val LocalPal = staticCompositionLocalOf<Pal> { error("Pal") }

internal val TYPE_LIGHT = mapOf(
    "objective" to ("#22D3FF" to "#00627E"), "deadline" to ("#FF4FA3" to "#B0005A"), "routine" to ("#B6FF2E" to "#3A7300"),
    "abstinence" to ("#A98BFF" to "#5530CF"), "monthly" to ("#FFD23F" to "#7A4E00"), "limit" to ("#FF8A3D" to "#A33D00"),
    "duration" to ("#2EF2B0" to "#00704F"), "project" to ("#4D9BFF" to "#0B4FC2"), "measure" to ("#FF7AD9" to "#A3007A"),
    "group" to ("#7CFF6B" to "#1F7A10"), "duel" to ("#FF5A5A" to "#B3121F"), "challenge" to ("#FFB000" to "#8A5300"),
    "program" to ("#7C8CFF" to "#2F3FBF"), "timed" to ("#E3B98A" to "#7A4B12"), "period" to ("#7DD3FC" to "#035C8A"),
    "cap" to ("#00D1B2" to "#006B5B")
)
private val TYPE_DARK_INK = mapOf(
    "objective" to "#5FE0FF", "deadline" to "#FF7DBB", "routine" to "#C6FF5C", "abstinence" to "#BFA8FF", "monthly" to "#FFE07A",
    "limit" to "#FFB07A", "duration" to "#5FF5C4", "project" to "#8CBDFF", "measure" to "#FF9FE4", "group" to "#9DFF90",
    "duel" to "#FF8A8A", "challenge" to "#FFC94D", "program" to "#A9B3FF", "timed" to "#F0CFA6", "period" to "#9DDCFF", "cap" to "#4FE8CF"
)
// Modes daltoniens : palettes distinguables (bleu / orange / gris, sans opposition rouge-vert ou bleu-jaune selon le cas)
private val CVD = mapOf(
    "deuter" to listOf("#0072B2", "#E69F00", "#56B4E9", "#CC79A7", "#F0E442", "#D55E00", "#009E73", "#999999"),
    "prot" to listOf("#0072B2", "#E69F00", "#56B4E9", "#CC79A7", "#F0E442", "#D55E00", "#5A5AE6", "#999999"),
    "trit" to listOf("#D55E00", "#009E73", "#CC79A7", "#E69F00", "#0072B2", "#882255", "#44AA99", "#999999")
)

class ThemeInput(
    val theme: String, val accent: Int, val accentCustom: List<String>?, val glassBlur: String,
    val materialYou: Boolean, val contrast: Boolean, val cvd: String, val mono: Boolean, val motion: Boolean, val transp: Boolean,
    /** Thème Météo : sombre la nuit et au crépuscule. */
    val skyDark: Boolean,
    /** Opacité du verre givré (réglage de l'appareil) : low, mid, high. */
    val glassOpacity: String = "mid",
    val easy: Boolean = false, val big: Boolean = false, val oneHand: Boolean = false,
    /** Couleurs de saison de l'arrière-plan (null : aucune). */
    val season: List<Long>? = null,
    /** Thème automatique qui suit le soleil (null : suit le téléphone). */
    val sunDark: Boolean? = null
)

private fun mix(a: Color, b: Color, k: Float) = lerp(a, b, k)

/* ------------------------------------------------------------------ lisibilité (contraste WCAG) */
private fun lin(c: Float) = if (c <= .03928f) c / 12.92f else Math.pow(((c + .055f) / 1.055f).toDouble(), 2.4).toFloat()
/** Luminance relative (0 : noir, 1 : blanc). */
fun lum(c: Color): Float = .2126f * lin(c.red) + .7152f * lin(c.green) + .0722f * lin(c.blue)
/** Rapport de contraste entre deux couleurs opaques (1 à 21). */
fun contrast(a: Color, b: Color): Float { val x = lum(a); val y = lum(b); return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f) }
/** Couleur translucide posée sur un fond opaque. */
fun Color.over(bg: Color): Color = if (alpha >= 1f) this else lerp(bg, copy(alpha = 1f), alpha)
/** Texte à poser sur un dégradé de [cols] : foncé ou blanc, celui qui se lit le mieux sur toutes les teintes. */
fun onColors(cols: List<Color>): Color {
    val d = PLUM
    return if (cols.minOf { contrast(d, it) } >= cols.minOf { contrast(Color.White, it) }) d else Color.White
}
/**
 * Rend [fg] lisible sur [bg] : garde la teinte, mais la pousse vers le blanc ou le noir (selon le fond) jusqu'à atteindre
 * le contraste [min] (4,5 : texte courant ; 3 : gros texte et icônes).
 */
fun readable(fg: Color, bg: Color, min: Float = 4.5f): Color {
    val f = fg.copy(alpha = 1f); val b = bg.copy(alpha = 1f)
    if (contrast(f, b) >= min) return fg
    val to = if (lum(b) > .18f) Color.Black else Color.White
    var k = .1f
    while (k < 1f) { val c = lerp(f, to, k); if (contrast(c, b) >= min) return c.copy(alpha = fg.alpha); k += .1f }
    return to.copy(alpha = fg.alpha)
}

@Composable
fun PepsTheme(input: ThemeInput, content: @Composable () -> Unit) {
    val sysDark = isSystemInDarkTheme()
    val transp = input.transp || input.easy; val motion = input.motion || input.easy
    // Ambiance achetée (« m:<clé> ») : thème d'interface complet, clair ou sombre
    val mode = fr.pepsgoal.core.shopMode(input.theme)
    val dark = if (mode != null) mode.dark else when (input.theme) { "light" -> false; "dark", "oled" -> true; "weather" -> input.skyDark; else -> input.sunDark ?: sysDark }
    val oled = input.theme == "oled"
    val ctx = LocalContext.current
    val dyn: ColorScheme? = if (input.materialYou && Build.VERSION.SDK_INT >= 31) (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) else null

    val ac: List<Color> = when {
        dyn != null -> listOf(if (dark) dyn.primary else dyn.primaryContainer, if (dark) dyn.primaryContainer else dyn.primary, dyn.tertiary)
        validAccent(input.accentCustom) -> input.accentCustom!!.map(::hex)
        else -> (ACCENTS.getOrNull(input.accent) ?: ACCENTS[0]).c.map(::hex)
    }
    // Texte posé sur l'accent : prune ou blanc, celui qui se lit le mieux sur les trois teintes ; si aucun n'atteint
    // 4,5, les teintes trop proches du texte sont éclaircies (ou assombries) juste ce qu'il faut
    val inkDark = PLUM
    val onAc: Color = if (dyn != null) onColors(listOf(dyn.primary, dyn.tertiary)) else {
        val d = ac.minOf { contrast(inkDark, it) }; val w = ac.minOf { contrast(Color.White, it) }
        if (d >= w) inkDark else Color.White
    }
    val ac2 = ac.map { stop ->
        if (contrast(onAc, stop) >= 4.5f) stop
        else { val to = if (onAc == Color.White) Color.Black else Color.White; var c = stop; var k = .08f; while (contrast(onAc, c) < 4.5f && k < 1f) { c = lerp(stop, to, k); k += .08f }; c }
    }
    // Neutres « Braise Pop » : crème pêche / prune nuit, surfaces opaques, bord et marche pleins
    var bg = if (dark) hex("#170F1C") else hex("#FFF7F0")
    var surface = if (dark) hex("#241A2B") else Color.White
    var surface2 = if (dark) hex("#2F2337") else hex("#FFEDE1")
    var edge = if (dark) hex("#3A2D43") else hex("#F2DED1")
    var control = if (dark) hex("#8C7584") else hex("#9C8273")
    var ink = if (dark) hex("#FFF4EC") else PLUM; var ink2 = if (dark) hex("#D9C4CF") else hex("#6B4F5C"); var ink3 = if (dark) hex("#A8929E") else hex("#7D6170")
    var deco = if (dark) listOf(hex("#3A1F2E"), hex("#2E1B3A")) else listOf(hex("#FFE2CC"), hex("#FFD6E8"))
    if (dyn != null) {
        // Material You : neutres et accent tirés de la palette dynamique ; les néons et la flamme de la série restent
        bg = dyn.background; ink = dyn.onBackground; ink2 = dyn.onSurfaceVariant; ink3 = mix(dyn.onSurfaceVariant, dyn.background, .25f)
        surface = if (dark) dyn.surfaceContainer else dyn.surfaceContainerLowest; surface2 = dyn.surfaceContainerHigh
        edge = dyn.outlineVariant; control = dyn.outline
        deco = listOf(mix(bg, dyn.primaryContainer, .7f), mix(bg, dyn.tertiaryContainer, .6f))
    }
    if (oled) { bg = Color.Black; surface = hex("#170F1C"); surface2 = hex("#241A2B"); edge = hex("#2F2337") }
    if (mode != null) {
        // Ambiance de la boutique : teinte le fond et le décor, jamais les surfaces ni les néons
        bg = hex(mode.bg); ink = readable(hex(mode.ink), surface, 7f); ink2 = mix(ink, surface, .25f); ink3 = mix(ink, surface, if (dark) .4f else .35f)
        deco = mode.blobs.take(2).map { mix(bg, hex(it), if (dark) .35f else .3f) }
    } else if (input.season != null && dyn == null) deco = input.season.take(2).map { mix(bg, Color(it).copy(alpha = 1f), if (dark) .3f else .25f) }
    if (input.contrast) { ink = if (dark) Color.White else Color.Black; ink2 = ink; ink3 = if (dark) Color(0xFFDDDDDD) else Color(0xFF222222); edge = ink; control = ink; deco = listOf(bg, bg) }
    // Compatibilité : l'ancien verre est désormais une surface opaque
    val glass = surface; val glassStrong = surface
    val line = edge
    // Base de tous les calculs de lisibilité : la surface des cartes
    val card = surface
    ink2 = readable(ink2, card, 7f); ink3 = readable(ink3, card, 4.6f)
    val braise = dyn == null && !validAccent(input.accentCustom) && input.accent == 0
    val flameInk = if (input.contrast) ink else if (braise) (if (dark) hex("#FF8A55") else hex("#B8350A")) else readable(ac2[1], card, 4.6f)
    val flameStep = if (input.contrast) ink else if (braise) hex("#C2185B") else ac2[0].step()

    val cvd = CVD[input.cvd]
    val types = TYPE_LIGHT.entries.mapIndexed { i, (k, v) ->
        var c = hex(v.first); var tink = if (dark) hex(TYPE_DARK_INK[k]!!) else hex(v.second)
        if (cvd != null) { c = hex(cvd[i % cvd.size]); tink = if (dark) mix(c, Color.White, .3f) else mix(c, Color.Black, .35f) }
        // Néons identiques en clair et en sombre (texte prune posé dessus) ; l'encre du type reste lisible sur les cartes
        k to TypeColor(c, readable(tink, card, 4.6f))
    }.toMap()

    val pal = Pal(
        dark, oled, bg, ink, ink2, ink3, glass, glassStrong, edge, line,
        if (dark) Color(0xB3000000) else Color(0x802A1420),
        ac2[0], ac2[1], ac2[2], onAc, flameInk,
        if (dark) hex("#4FD08A") else hex("#1E7F45"), if (dark) hex("#FF7A84") else hex("#C0392B"), if (dark) hex("#6FE3FF") else hex("#0057D9"),
        if (dyn != null) listOf(dyn.primary, dyn.tertiary, dyn.secondary, dyn.primaryContainer) else input.season?.map { Color(it) } ?: mode?.let { m -> m.blobs.map(::hex) + ac[1] } ?: listOf(ac[0], ac[2], ac[1], hex("#22D3FF")),
        if (oled) .22f else if (dark) .42f else .55f, types, dyn != null, input.contrast, transp, input.mono, motion,
        when (input.glassBlur) { "xlow" -> 20f; "low" -> 40f; "high" -> 110f; else -> 70f },
        input.easy, input.big || input.easy, input.oneHand,
        surface = surface, surface2 = surface2, edge = edge, control = control, flameStep = flameStep, deco = deco
    )

    val base = dyn ?: if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        // Composants Material (boutons texte des dialogues, champs, curseur) : l'accent lisible, pas l'accent brut
        primary = pal.flameInk, onPrimary = onColors(listOf(pal.flameInk)), secondary = pal.pink, tertiary = pal.flame2,
        background = bg, onBackground = ink, surface = surface,
        onSurface = ink, onSurfaceVariant = ink2, outline = control, outlineVariant = edge, error = pal.danger,
        surfaceContainerLow = surface, surfaceContainer = surface2, surfaceContainerHigh = surface, surfaceContainerHighest = surface2
    )

    val t = Typography()
    // Chiffres à largeur fixe partout : compteurs et scores ne « dansent » plus quand ils changent
    val body = TextStyle(fontFamily = Manrope, fontFeatureSettings = "tnum")
    val head = TextStyle(fontFamily = Unbounded, fontFeatureSettings = "tnum", letterSpacing = (-.01).em)
    // Échelle « Braise Pop » : Unbounded pour les titres et les chiffres, Manrope (jamais en Regular) pour le reste
    val typo = Typography(
        displayLarge = t.displayLarge.merge(head).copy(fontWeight = FontWeight.ExtraBold, fontSize = 60.sp), displayMedium = t.displayMedium.merge(head).copy(fontWeight = FontWeight.ExtraBold, fontSize = 48.sp),
        displaySmall = t.displaySmall.merge(head).copy(fontWeight = FontWeight.ExtraBold, fontSize = 36.sp), headlineLarge = t.headlineLarge.merge(head).copy(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp),
        headlineMedium = t.headlineMedium.merge(head).copy(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp), headlineSmall = t.headlineSmall.merge(head).copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
        titleLarge = t.titleLarge.merge(head).copy(fontWeight = FontWeight.Bold, fontSize = 18.sp), titleMedium = t.titleMedium.merge(head).copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
        titleSmall = t.titleSmall.merge(body).copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp), bodyLarge = t.bodyLarge.merge(body).copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        bodyMedium = t.bodyMedium.merge(body).copy(fontWeight = FontWeight.Bold, fontSize = 15.sp), bodySmall = t.bodySmall.merge(body).copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
        labelLarge = t.labelLarge.merge(body).copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp), labelMedium = t.labelMedium.merge(body).copy(fontWeight = FontWeight.ExtraBold, fontSize = 13.sp),
        labelSmall = t.labelSmall.merge(body).copy(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
    )
    // Interlignes proportionnels à la taille du texte : une taille imposée (fontSize = 13.sp…) garde un interligne serré
    fun TextStyle.lh(k: Float) = copy(lineHeight = k.em)
    val typoEm = typo.copy(
        displayLarge = typo.displayLarge.lh(1.12f), displayMedium = typo.displayMedium.lh(1.12f), displaySmall = typo.displaySmall.lh(1.15f),
        headlineLarge = typo.headlineLarge.lh(1.18f), headlineMedium = typo.headlineMedium.lh(1.18f), headlineSmall = typo.headlineSmall.lh(1.2f),
        titleLarge = typo.titleLarge.lh(1.22f), titleMedium = typo.titleMedium.lh(1.25f), titleSmall = typo.titleSmall.lh(1.3f),
        bodyLarge = typo.bodyLarge.lh(1.38f), bodyMedium = typo.bodyMedium.lh(1.38f), bodySmall = typo.bodySmall.lh(1.35f),
        labelLarge = typo.labelLarge.lh(1.25f), labelMedium = typo.labelMedium.lh(1.25f), labelSmall = typo.labelSmall.lh(1.25f)
    )
    // Changement de thème (clair ↔ sombre, ciel de la Météo, accent…) : fondu enchaîné au lieu d'une bascule.
    // L'écran est enregistré dans un calque ; au changement, une image de l'ancien thème est posée par-dessus le nouveau
    // puis s'efface. Seul le dessin est animé (aucune recomposition par image), le fondu reste fluide.
    var curPal by remember { mutableStateOf(pal) }
    var curScheme by remember { mutableStateOf(scheme) }
    val layer = rememberGraphicsLayer()
    var shot by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(pal) {
        if (curPal == pal) { curScheme = scheme; return@LaunchedEffect }
        val img = if (pal.motion || layer.size.width <= 0) null else try { layer.toImageBitmap() } catch (e: Exception) { null }
        shot = img; fade.snapTo(1f)
        curPal = pal; curScheme = scheme
        if (img != null) try { fade.animateTo(0f, tween(650, easing = FastOutSlowInEasing)) } finally { shot = null }
    }
    CompositionLocalProvider(LocalPal provides curPal) {
        MaterialTheme(colorScheme = curScheme, typography = typoEm) {
            Box(Modifier.fillMaxSize().drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                shot?.let { drawImage(it, alpha = fade.value.coerceIn(0f, 1f)) }
            }) { content() }
        }
    }
}
