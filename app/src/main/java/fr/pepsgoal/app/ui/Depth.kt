package fr.pepsgoal.app.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.PLUM
import fr.pepsgoal.app.ui.theme.Pal
import fr.pepsgoal.app.ui.theme.contrast
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max
import kotlin.math.min

/*
 * Matière « Braise Pop » : aplats pleins, bord de 2 dp et « marche » (ombre pleine décalée sous l'élément). Tout ce qui
 * se touche a une marche et s'y enfonce à l'appui ; rien n'est flouté, aucun halo ni reflet. Chaque élément à marche
 * coûte deux rectangles arrondis : aussi rapide qu'un fond uni.
 */

/**
 * Coins continus (« squircle ») : la courbe s'amorce plus tôt et plus doucement qu'un arrondi circulaire, comme les
 * icônes des téléphones récents. Le milieu de chaque coin passe au même endroit qu'un arrondi de rayon [radius].
 * [smooth] : lissage (0 : arrondi classique).
 */
@Immutable
data class Squircle(val radius: Dp, val smooth: Float = .6f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width; val h = size.height
        val p = min(with(density) { radius.toPx() } * (1f + smooth), min(w, h) / 2f)
        if (p <= .5f) return Outline.Rectangle(Rect(0f, 0f, w, h))
        val r = p / (1f + smooth)
        val c = ((.2929f * r - .125f * p) / .375f).coerceIn(0f, p)
        return Outline.Generic(Path().apply {
            moveTo(p, 0f); lineTo(w - p, 0f); cubicTo(w - c, 0f, w, c, w, p)
            lineTo(w, h - p); cubicTo(w, h - c, w - c, h, w - p, h)
            lineTo(p, h); cubicTo(c, h, 0f, h - c, 0f, h - p)
            lineTo(0f, p); cubicTo(0f, c, c, 0f, p, 0f); close()
        })
    }
}

/**
 * Forme des cartes, tuiles et panneaux. Coins continus à partir d'Android 9 (découpe lissée par le moteur de rendu) ;
 * arrondi classique avant, où une découpe de forme libre serait crénelée.
 */
fun smoothShape(radius: Dp): Shape = if (Build.VERSION.SDK_INT >= 28) Squircle(radius) else RoundedCornerShape(radius)

/** Teinte des ombres (gardée pour les rares éléments hors interface, ex. aperçus). */
val Pal.shade: Color get() = if (dark) Color(0xFF000000) else Color(0xFF2A1420)

/** Fond réel d'une carte, opaque : base des pastilles pleines posées dessus. */
val Pal.card: Color get() = surface

/** Trait du contour des surfaces et des commandes : le bord des éléments neutres. */
fun Pal.hairline(): Color = edge

/** Dégradé de la flamme (accent) : carte Série, bouton +, onglet actif, bouton principal. */
fun Pal.flameBrush(): Brush = if (contrast) SolidColor(ink) else Brush.linearGradient(listOf(flame2, flame, pink))

/** Aplat d'une seule couleur. */
fun solid(c: Color): Brush = SolidColor(c)

/**
 * Aplat vif sur lequel l'encre prune reste lisible (≥ 4,5:1) : la couleur est éclaircie juste ce qu'il faut si elle est
 * trop sombre (danger, accent foncé…). Jamais de texte blanc sur une couleur vive.
 */
fun vivid(c: Color): Color {
    val o = c.copy(alpha = 1f)
    if (contrast(PLUM, o) >= 4.5f) return o
    var k = .08f
    while (k < 1f) { val x = lerp(o, Color.White, k); if (contrast(PLUM, x) >= 4.5f) return x; k += .08f }
    return Color.White
}

/**
 * Volume « à marche » : bord [edge] de [border] dp et marche pleine de [step] dp sous l'aplat [fill], le tout dessiné
 * derrière le contenu (deux rectangles arrondis, sans couche hors écran). La hauteur de l'élément comprend la marche ;
 * le contenu est placé dans l'aplat. [pressed] (0 → 1, lu au dessin) : l'élément descend sur sa marche, sans
 * recomposition ni nouvelle mise en page.
 */
fun Modifier.chunk(fill: Brush, edge: Color, radius: Dp, step: Dp = 4.dp, border: Dp = 2.dp, pressed: () -> Float = { 0f }): Modifier =
    this.graphicsLayer { translationY = pressed() * step.toPx() }
        .drawBehind {
            val st = step.toPx(); val dy = pressed() * st; val b = border.toPx()
            val h = size.height - st
            val r = min(radius.toPx(), min(size.width, h) / 2f)
            // Bord et marche : une seule forme pleine, qui reste posée au sol quand l'élément s'enfonce
            if (st > 0f || b > 0f) drawRoundRect(edge, Offset.Zero, Size(size.width, size.height - dy), CornerRadius(r))
            val ri = max(0f, r - b)
            drawRoundRect(fill, Offset(b, b), Size(size.width - 2 * b, h - 2 * b), CornerRadius(ri))
        }
        .padding(start = border, end = border, top = border, bottom = border + step)

/** Même volume, d'une couleur pleine. */
fun Modifier.chunk(fill: Color, edge: Color, radius: Dp, step: Dp = 4.dp, border: Dp = 2.dp, pressed: () -> Float = { 0f }): Modifier =
    chunk(SolidColor(fill), edge, radius, step, border, pressed)

/**
 * Enfoncement à l'appui (0 : au repos, 1 : posé sur sa marche), piloté par [inter] : descente nette (ressort `press`),
 * remontée avec un léger dépassement. Animations réduites : changement immédiat. À lire au dessin (voir [chunk]).
 */
@Composable
fun rememberPress(inter: MutableInteractionSource, enabled: Boolean = true): () -> Float {
    val motionOff = LocalPal.current.motion
    val a = remember { Animatable(0f) }
    LaunchedEffect(inter, enabled, motionOff) {
        if (!enabled) { a.snapTo(0f); return@LaunchedEffect }
        inter.interactions.collectLatest { i ->
            when (i) {
                is PressInteraction.Press -> if (motionOff) a.snapTo(1f) else a.animateTo(1f, Motion.press())
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    if (motionOff) a.snapTo(0f) else {
                        // Appui très bref : on laisse le temps de voir l'élément s'enfoncer avant qu'il ne remonte
                        if (a.value < .7f) a.animateTo(1f, tween(50))
                        a.animateTo(0f, spring(dampingRatio = .45f, stiffness = 700f))
                    }
                }
            }
        }
    }
    return { a.value }
}

/** Élément dont la marche est dessinée ailleurs : il descend de [step] à l'appui. */
@Composable
fun Modifier.pressable(inter: MutableInteractionSource, step: Dp = 4.dp, enabled: Boolean = true): Modifier {
    val pr = rememberPress(inter, enabled)
    return this.graphicsLayer { translationY = pr() * step.toPx() }
}

/** Aucun relief flou : la marche remplace l'élévation. Gardé pour la compatibilité des appels. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.depth(shape: Shape, p: Pal, lift: Int = 2, tint: Color = p.shade): Modifier = this

/** Halo coloré : retiré. Gardé pour la compatibilité des appels. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.glow(shape: Shape, color: Color, p: Pal, elevation: Dp = 12.dp): Modifier = this

/** Reflet : retiré (aplats seulement). Gardé pour la compatibilité des appels. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.sheen(shape: Shape, dark: Boolean, k: Float = 1f, rim: Boolean = true): Modifier = this

/** Remplissage des éléments vifs : couleur pleine ou dégradé franc, sans reflet ni halo. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.candy(shape: Shape, colors: List<Color>, p: Pal, glow: Boolean = true, elevation: Dp = 12.dp): Modifier {
    val cs = if (colors.size == 1) listOf(colors[0], colors[0]) else colors
    return this.background(Brush.linearGradient(cs, start = Offset.Zero, end = Offset.Infinite), shape)
}

/** Couleur éclaircie (vers le blanc). */
fun Color.lift(k: Float): Color = lerp(this, Color.White, k)
/** Couleur assombrie (vers le prune, jamais un gris sale). */
fun Color.deepen(k: Float): Color = lerp(this, Color(0xFF2A1420), k)

/** Dégradé discret tiré d'une seule couleur. */
fun candyOf(c: Color): List<Color> = listOf(c.lift(.12f), c)
