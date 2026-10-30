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

/** Fond réel d'une carte, opaque : base des pastilles pleines posées dessus. */
val Pal.card: Color get() = surface

/** Dégradé de la flamme (accent) : carte Série, bouton +, onglet actif, bouton principal. */
fun Pal.flameBrush(): Brush = if (contrast) SolidColor(ink) else Brush.linearGradient(listOf(flame2, flame, pink))

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

/** Couleur éclaircie (vers le blanc). */
fun Color.lift(k: Float): Color = lerp(this, Color.White, k)

