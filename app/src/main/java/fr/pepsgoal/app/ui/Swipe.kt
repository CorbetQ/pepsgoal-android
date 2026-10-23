package fr.pepsgoal.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import fr.pepsgoal.app.ui.theme.LocalPal
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/**
 * Geste de balayage partagé (objectifs, messages) :
 * - le contenu suit le doigt, puis résiste comme un élastique passé le seuil ;
 * - vibrations progressives en approchant du seuil, clic net quand l'action est armée ;
 * - un geste rapide (lancer) déclenche l'action même avant le seuil ;
 * - au lâcher, élan dans la direction de l'action puis retour en ressort.
 */
@Stable
class SwipeState {
    /** Décalage affiché (px). */
    var dx by mutableFloatStateOf(0f); internal set
    /** -1 : action de gauche armée, 1 : action de droite, 0 : aucune. */
    var armed by mutableIntStateOf(0); internal set
    var width by mutableIntStateOf(1); internal set
    internal var raw = 0f
    internal var step = 0
    /** Avancement vers le seuil (0 à 1), dans la direction du geste. */
    fun progress(threshold: Float) = (abs(raw) / (width * threshold)).coerceIn(0f, 1f)
}

@Composable
fun rememberSwipe() = remember { SwipeState() }

/**
 * Rend un élément balayable. [left] / [right] : directions permises (vers la gauche / vers la droite).
 * [onCommit] reçoit la direction (-1 ou 1) au lâcher.
 */
@Composable
fun Modifier.swipeable(s: SwipeState, left: Boolean, right: Boolean, threshold: Float = .26f, enabled: Boolean = true, onCommit: (Int) -> Unit): Modifier {
    if (!enabled || (!left && !right)) return this.onSizeChanged { s.width = it.width.coerceAtLeast(1) }
    val motionOff = LocalPal.current.motion
    val drag = rememberDraggableState { d ->
        var r = s.raw + d
        if (!right && r > 0) r = 0f
        if (!left && r < 0) r = 0f
        s.raw = r
        val th = s.width * threshold; val a = abs(r)
        // Élastique : au-delà du seuil, le contenu n'avance plus qu'à 35 % du doigt
        val shown = if (a <= th) a else th + (a - th) * .35f
        s.dx = sign(r) * shown.coerceAtMost(s.width * .62f)
        // Crans progressifs (4 avant le seuil), plus nets à mesure qu'on approche
        val k = (a / th).coerceAtMost(1f); val st = (k * 4).toInt()
        if (st > s.step && st < 4) Feedback.ramp(k)
        s.step = st
        val arm = if (a >= th) sign(r).toInt() else 0
        if (arm != s.armed) { s.armed = arm; if (arm != 0) Feedback.armed() else Feedback.disarmed() }
    }
    return this.onSizeChanged { s.width = it.width.coerceAtLeast(1) }
        .draggable(drag, Orientation.Horizontal, onDragStopped = { v ->
            val th = s.width * threshold
            // Lancer : geste rapide dans le sens du décalage, même avant le seuil
            val fling = abs(v) > 1800f && abs(s.raw) > th * .3f && sign(v) == sign(s.raw)
            val dir = if (s.armed != 0) s.armed else if (fling) sign(v).toInt() else 0
            s.armed = 0; s.step = 0; s.raw = 0f
            if (dir != 0) {
                Feedback.commit()
                // Petit élan vers l'action avant le retour (rien si les animations sont coupées)
                if (!motionOff) animate(s.dx, dir * maxOf(abs(s.dx), th * 1.05f), animationSpec = tween(90)) { x, _ -> s.dx = x }
                onCommit(dir)
            }
            if (motionOff) s.dx = 0f
            else animate(s.dx, 0f, initialVelocity = if (dir == 0) v * .25f else 0f, animationSpec = spring(dampingRatio = .68f, stiffness = 420f)) { x, _ -> s.dx = x }
        })
        .offsetX(s)
}

private fun Modifier.offsetX(s: SwipeState) = this.then(Modifier.offset { IntOffset(s.dx.toInt(), 0) })

/**
 * Fond révélé sous un élément balayé : teinte qui se renforce avec le geste, icône qui grossit, et cercle de couleur
 * qui remplit le fond quand l'action est armée.
 */
@Composable
fun SwipeReveal(s: SwipeState, shape: Shape, threshold: Float = .26f, right: SwipeAction?, left: SwipeAction?, modifier: Modifier = Modifier) {
    if (s.dx == 0f) return
    val toRight = s.dx > 0
    val a = (if (toRight) right else left) ?: return
    val k = s.progress(threshold)
    val armed = s.armed != 0
    val fill by animateFloatAsState(if (armed) 1f else 0f, spring(dampingRatio = .8f, stiffness = 520f), label = "swipeFill")
    val pop by animateFloatAsState(if (armed) 1.18f else .78f + .22f * k, spring(dampingRatio = .45f, stiffness = 600f), label = "swipePop")
    val ink = if (fill > .5f) readable(a.color) else a.color
    Box(modifier.fillMaxSize().clip(shape)) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(a.color.copy(alpha = .08f + .14f * k))
            // Cercle qui part de l'icône et couvre tout le fond une fois l'action armée
            if (fill > 0f) {
                val cx = if (toRight) 34.dp.toPx() else size.width - 34.dp.toPx()
                val r = hypot(size.width, size.height) * fill
                drawCircle(a.color, r, Offset(cx, size.height / 2))
            }
        }
        Row(Modifier.align(if (toRight) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 18.dp)
            .graphicsLayer { scaleX = pop; scaleY = pop; alpha = (.35f + k).coerceAtMost(1f) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!toRight && a.label != null) Text(a.label, fontWeight = FontWeight.ExtraBold, color = ink)
            Pi(a.icon, size = 22.dp, tint = ink)
            if (toRight && a.label != null) Text(a.label, fontWeight = FontWeight.ExtraBold, color = ink)
        }
    }
}

class SwipeAction(val icon: String, val label: String?, val color: Color)

/**
 * Place un élément d'une liste (Column) en douceur quand sa position change : un objectif validé descend en glissant
 * au lieu de sauter. Coupé avec les animations réduites ou les transitions simples (Performance).
 */
@Composable
fun Modifier.animatePlacement(): Modifier {
    if (LocalPal.current.motion || !fr.pepsgoal.app.sys.Perf.flags.rich) return this
    val scope = rememberCoroutineScope()
    val anim = remember { Animatable(0f) }
    var target by remember { mutableFloatStateOf(Float.NaN) }
    return this.then(Modifier.onPlaced { c ->
        val y = c.positionInParent().y
        val prev = target
        target = y
        if (!prev.isNaN() && abs(prev - y) > 1f) {
            // On repart de l'ancienne position (décalage) et on revient à 0 ; lancé tout de suite pour éviter une image au mauvais endroit
            val from = anim.value + (prev - y)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { anim.snapTo(from); anim.animateTo(0f, spring(dampingRatio = .82f, stiffness = 380f)) }
        }
    }).graphicsLayer { translationY = anim.value }
}

/** Légère inclinaison de l'élément balayé, comme une carte qu'on fait glisser du doigt. */
fun Modifier.swipeTilt(s: SwipeState): Modifier = this.graphicsLayer {
    if (s.dx != 0f) {
        val t = (abs(s.dx) / s.width).coerceAtMost(.6f)
        rotationZ = sign(s.dx) * t * 1.6f
        val sc = 1f - t * .03f; scaleX = sc; scaleY = sc
    }
}
