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

/** Couleur d'un type d'objectif : vive (fond, barre) et encre (texte lisible sur le fond de l'application). */
@Immutable
data class TypeColor(val c: Color, val ink: Color)

/** Jetons de couleur de PepsGoal (variables CSS de la version web : --bg, --ink, --glass, --flame…). */
@Immutable
data class Pal(
    val dark: Boolean,
    val oled: Boolean,
    val bg: Color, val ink: Color, val ink2: Color, val ink3: Color,
    val glass: Color, val glassStrong: Color, val glassEdge: Color, val line: Color, val scrim: Color,
    val flame2: Color, val flame: Color, val pink: Color, val onAccent: Color,
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
    val oneHand: Boolean = false
) {
    val accent get() = Brush.linearGradient(listOf(flame2, flame, pink))
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
    val season: List<Long>? = null
)

private fun mix(a: Color, b: Color, k: Float) = lerp(a, b, k)

/** Fait tendre une couleur vers la teinte de la palette Material You (harmonisation des types d'objectifs). */
private fun harmonize(c: Color, toward: Color, k: Float = .35f): Color {
    val hsv1 = FloatArray(3); val hsv2 = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv1); android.graphics.Color.colorToHSV(toward.toArgb(), hsv2)
    var d = hsv2[0] - hsv1[0]; if (d > 180) d -= 360; if (d < -180) d += 360
    hsv1[0] = (hsv1[0] + d * k + 360) % 360
    return Color(android.graphics.Color.HSVToColor(hsv1))
}

@Composable
fun PepsTheme(input: ThemeInput, content: @Composable () -> Unit) {
    val sysDark = isSystemInDarkTheme()
    val transp = input.transp || input.easy; val motion = input.motion || input.easy
    val dark = when (input.theme) { "light" -> false; "dark", "oled" -> true; "weather" -> input.skyDark; else -> sysDark }
    val oled = input.theme == "oled"
    val ctx = LocalContext.current
    val dyn: ColorScheme? = if (input.materialYou && Build.VERSION.SDK_INT >= 31) (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) else null

    val ac: List<Color> = when {
        dyn != null -> listOf(if (dark) dyn.primary else dyn.primaryContainer, if (dark) dyn.primaryContainer else dyn.primary, dyn.tertiary)
        validAccent(input.accentCustom) -> input.accentCustom!!.map(::hex)
        else -> (ACCENTS.getOrNull(input.accent) ?: ACCENTS[0]).c.map(::hex)
    }
    var bg = if (dark) hex("#07081A") else hex("#ECEEFF")
    var ink = if (dark) hex("#F4F5FF") else hex("#0D0E2B"); var ink2 = if (dark) hex("#C9CCF2") else hex("#34375F"); var ink3 = if (dark) hex("#9C9FD0") else hex("#575A86")
    var glass = if (dark) Color(24, 26, 66, (255 * .62).toInt()) else Color(255, 255, 255, (255 * .62).toInt())
    var glassStrong = if (dark) Color(28, 30, 76, 230) else Color(255, 255, 255, 220)
    if (dyn != null) {
        bg = dyn.background; ink = dyn.onBackground; ink2 = dyn.onSurfaceVariant; ink3 = mix(dyn.onSurfaceVariant, dyn.background, .25f)
        glass = dyn.surfaceContainerLow.copy(alpha = .78f); glassStrong = dyn.surfaceContainerHigh.copy(alpha = .92f)
    }
    if (oled) { bg = Color.Black; glass = Color(255, 255, 255, 14); glassStrong = Color(20, 20, 24, 240) }
    // Opacité du verre : réglage séparé du flou (Paramètres › Apparence)
    val alpha = when (input.glassOpacity) { "low" -> .38f; "high" -> .86f; else -> .62f }
    glass = glass.copy(alpha = if (oled) glass.alpha else minOf(.95f, alpha + if (dyn != null) .08f else 0f))
    if (!oled) glassStrong = glassStrong.copy(alpha = when (input.glassOpacity) { "low" -> .7f; "high" -> .96f; else -> glassStrong.alpha })
    if (transp || input.contrast) { glass = if (dark) (if (oled) Color(12, 12, 14) else hex("#14163A")) else Color.White; glassStrong = glass }
    if (input.contrast) { ink = if (dark) Color.White else Color.Black; ink2 = ink; ink3 = if (dark) Color(0xFFDDDDDD) else Color(0xFF222222) }
    val line = if (input.contrast) ink.copy(alpha = .7f) else ink.copy(alpha = .13f)

    val cvd = CVD[input.cvd]
    val types = TYPE_LIGHT.entries.mapIndexed { i, (k, v) ->
        var c = hex(v.first); var tink = if (dark) hex(TYPE_DARK_INK[k]!!) else hex(v.second)
        if (cvd != null) { c = hex(cvd[i % cvd.size]); tink = if (dark) mix(c, Color.White, .3f) else mix(c, Color.Black, .35f) }
        else if (dyn != null) { c = harmonize(c, dyn.primary); tink = harmonize(tink, dyn.primary) }
        k to TypeColor(c, tink)
    }.toMap()

    val pal = Pal(
        dark, oled, bg, ink, ink2, ink3, glass, glassStrong, if (dark) Color(255, 255, 255, 36) else Color(255, 255, 255, 242), line,
        if (dark) Color(3, 3, 14, 160) else Color(13, 14, 43, 97),
        ac[0], ac[1], ac[2], if (dyn != null) (if (dark) dyn.onPrimaryContainer else dyn.onPrimary) else hex("#10102A"),
        if (dark) hex("#5BE38A") else hex("#1E8E3E"), if (dark) hex("#FF6B81") else hex("#C8102E"), if (dark) hex("#6FE3FF") else hex("#0057D9"),
        if (dyn != null) listOf(dyn.primary, dyn.tertiary, dyn.secondary, dyn.primaryContainer) else input.season?.map { Color(it) } ?: listOf(ac[0], ac[2], ac[1], hex("#22D3FF")),
        if (oled) .22f else if (dark) .42f else .55f, types, dyn != null, input.contrast, transp, input.mono, motion,
        when (input.glassBlur) { "xlow" -> 20f; "low" -> 40f; "high" -> 110f; else -> 70f },
        input.easy, input.big || input.easy, input.oneHand
    )

    val base = dyn ?: if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = pal.flame, onPrimary = pal.onAccent, secondary = pal.pink, tertiary = pal.flame2,
        background = bg, onBackground = ink, surface = if (oled) Color.Black else if (dyn != null) dyn.surface else (if (dark) hex("#10122E") else hex("#F7F7FF")),
        onSurface = ink, onSurfaceVariant = ink2, outline = ink3, outlineVariant = line, error = pal.danger,
        surfaceContainerLow = glass, surfaceContainer = glassStrong, surfaceContainerHigh = glassStrong,
        surfaceContainerHighest = if (dark) hex("#1C1F4A") else hex("#FFFFFF")
    ).let { if (dyn != null) it.copy(primary = dyn.primary, onPrimary = dyn.onPrimary, primaryContainer = dyn.primaryContainer, secondary = dyn.secondary, tertiary = dyn.tertiary, surface = dyn.surface) else it }

    val t = Typography()
    val body = TextStyle(fontFamily = Manrope)
    val typo = Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold), displayMedium = t.displayMedium.copy(fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold),
        displaySmall = t.displaySmall.copy(fontFamily = Unbounded, fontWeight = FontWeight.Bold), headlineLarge = t.headlineLarge.copy(fontFamily = Unbounded, fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontFamily = Unbounded, fontWeight = FontWeight.Bold), headlineSmall = t.headlineSmall.copy(fontFamily = Unbounded, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 19.sp), titleMedium = t.titleMedium.copy(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 16.sp),
        titleSmall = t.titleSmall.merge(body).copy(fontWeight = FontWeight.ExtraBold), bodyLarge = t.bodyLarge.merge(body), bodyMedium = t.bodyMedium.merge(body).copy(fontSize = 15.sp),
        bodySmall = t.bodySmall.merge(body).copy(fontSize = 13.sp), labelLarge = t.labelLarge.merge(body).copy(fontWeight = FontWeight.ExtraBold),
        labelMedium = t.labelMedium.merge(body).copy(fontWeight = FontWeight.Bold), labelSmall = t.labelSmall.merge(body).copy(fontWeight = FontWeight.ExtraBold)
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
    // Changement de thème (clair ↔ sombre, ciel de la Météo, accent…) : fondu doux des couleurs au lieu d'une bascule
    val anim = remember { ThemeAnim() }
    val prog = remember { Animatable(1f) }
    if (anim.to != null && anim.to != pal && !pal.motion) { anim.from = anim.last; anim.fromScheme = anim.lastScheme; anim.pending = true }
    anim.to = pal; anim.toScheme = scheme
    LaunchedEffect(pal) {
        if (anim.pending) { anim.pending = false; prog.snapTo(0f); prog.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    }
    val k = if (anim.pending) 0f else prog.value
    val from = anim.from; val fromScheme = anim.fromScheme
    val shown = if (k >= 1f || from == null) pal else lerpPal(from, pal, k)
    val shownScheme = if (k >= 1f || fromScheme == null) scheme else lerpScheme(fromScheme, scheme, k)
    anim.last = shown; anim.lastScheme = shownScheme
    CompositionLocalProvider(LocalPal provides shown) { MaterialTheme(colorScheme = shownScheme, typography = typoEm, content = content) }
}

private class ThemeAnim { var from: Pal? = null; var fromScheme: ColorScheme? = null; var to: Pal? = null; var toScheme: ColorScheme? = null; var last: Pal? = null; var lastScheme: ColorScheme? = null; var pending = false }

private fun lc(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

/** Palette intermédiaire entre deux thèmes (couleurs mélangées ; réglages pris du thème d'arrivée, « sombre » à mi-chemin). */
private fun lerpPal(a: Pal, b: Pal, t: Float) = b.copy(
    dark = if (t < .5f) a.dark else b.dark,
    bg = lc(a.bg, b.bg, t), ink = lc(a.ink, b.ink, t), ink2 = lc(a.ink2, b.ink2, t), ink3 = lc(a.ink3, b.ink3, t),
    glass = lc(a.glass, b.glass, t), glassStrong = lc(a.glassStrong, b.glassStrong, t), glassEdge = lc(a.glassEdge, b.glassEdge, t), line = lc(a.line, b.line, t), scrim = lc(a.scrim, b.scrim, t),
    flame2 = lc(a.flame2, b.flame2, t), flame = lc(a.flame, b.flame, t), pink = lc(a.pink, b.pink, t), onAccent = lc(a.onAccent, b.onAccent, t),
    ok = lc(a.ok, b.ok, t), danger = lc(a.danger, b.danger, t), focus = lc(a.focus, b.focus, t),
    blobs = b.blobs.mapIndexed { i, c -> lc(a.blobs.getOrElse(i) { c }, c, t) }, blobAlpha = a.blobAlpha + (b.blobAlpha - a.blobAlpha) * t,
    types = b.types.mapValues { (k, v) -> a.types[k]?.let { o -> TypeColor(lc(o.c, v.c, t), lc(o.ink, v.ink, t)) } ?: v }
)

private fun lerpScheme(a: ColorScheme, b: ColorScheme, t: Float) = b.copy(
    primary = lc(a.primary, b.primary, t), onPrimary = lc(a.onPrimary, b.onPrimary, t), primaryContainer = lc(a.primaryContainer, b.primaryContainer, t),
    secondary = lc(a.secondary, b.secondary, t), tertiary = lc(a.tertiary, b.tertiary, t), background = lc(a.background, b.background, t), onBackground = lc(a.onBackground, b.onBackground, t),
    surface = lc(a.surface, b.surface, t), onSurface = lc(a.onSurface, b.onSurface, t), surfaceVariant = lc(a.surfaceVariant, b.surfaceVariant, t), onSurfaceVariant = lc(a.onSurfaceVariant, b.onSurfaceVariant, t),
    outline = lc(a.outline, b.outline, t), outlineVariant = lc(a.outlineVariant, b.outlineVariant, t), error = lc(a.error, b.error, t),
    surfaceContainerLow = lc(a.surfaceContainerLow, b.surfaceContainerLow, t), surfaceContainer = lc(a.surfaceContainer, b.surfaceContainer, t),
    surfaceContainerHigh = lc(a.surfaceContainerHigh, b.surfaceContainerHigh, t), surfaceContainerHighest = lc(a.surfaceContainerHighest, b.surfaceContainerHighest, t)
)
