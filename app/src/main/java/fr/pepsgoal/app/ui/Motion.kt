package fr.pepsgoal.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * Courbes d'animation communes : toute l'application bouge avec les mêmes ressorts, ce qui donne une sensation
 * cohérente (rapide pour les retours d'appui, standard pour les déplacements, emphase pour les moments forts).
 */
object Motion {
    /** Accélération douce puis long freinage (entrées d'écran, apparitions). */
    val Emphasized = CubicBezierEasing(.2f, 0f, 0f, 1f)
    /** Sortie : départ franc, fin rapide. */
    val Exit = CubicBezierEasing(.3f, 0f, .8f, .15f)

    /** Retour d'appui, petits éléments : ressort raide, à peine rebondi. */
    fun <T> quick(): FiniteAnimationSpec<T> = spring(dampingRatio = .78f, stiffness = 900f)
    /** Déplacements, changements d'état : ressort naturel, sans rebond visible. */
    fun <T> standard(): FiniteAnimationSpec<T> = spring(dampingRatio = .86f, stiffness = Spring.StiffnessMediumLow)
    /** Moments forts (validation, apparition d'une pastille) : rebond franc et court. */
    fun <T> emphasis(): FiniteAnimationSpec<T> = spring(dampingRatio = .5f, stiffness = 520f)
    /** Fondu d'entrée / de sortie. */
    fun <T> fadeIn(ms: Int = 220): FiniteAnimationSpec<T> = tween(ms, easing = Emphasized)
    fun <T> fadeOut(ms: Int = 140): FiniteAnimationSpec<T> = tween(ms, easing = Exit)
}

/** Coche dessinée progressivement ([progress] 0 → 1) : le trait suit la forme de la coche. */
@Composable
fun DrawnCheck(progress: Float, color: Color, size: Dp, stroke: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val path = Path().apply { moveTo(w * .2f, h * .53f); lineTo(w * .42f, h * .74f); lineTo(w * .8f, h * .3f) }
        val pm = PathMeasure().apply { setPath(path, false) }
        val seg = Path()
        pm.getSegment(0f, pm.length * progress.coerceIn(0f, 1f), seg, true)
        drawPath(seg, color, style = Stroke(stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
