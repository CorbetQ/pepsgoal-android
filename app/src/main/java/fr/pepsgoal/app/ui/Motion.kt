package fr.pepsgoal.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import kotlin.random.Random
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

    /* Jetons « Braise Pop » : tout bouge par l'échelle, la position, la rotation ou l'opacité (graphicsLayer). */
    /** Enfoncement sur la marche : net. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = .6f, stiffness = 1500f)
    /** Rebond franc : case validée, pastille, onglet actif, interrupteur, toast. */
    fun <T> pop(): FiniteAnimationSpec<T> = spring(dampingRatio = .45f, stiffness = 500f)
    /** Souple, léger dépassement : indicateurs, barres, anneaux, bouton + qui se cache. */
    fun <T> move(): FiniteAnimationSpec<T> = spring(dampingRatio = .75f, stiffness = 380f)
    /** Ample : feuilles du bas, écrans poussés. */
    fun <T> sheet(): FiniteAnimationSpec<T> = spring(dampingRatio = .82f, stiffness = 300f)
    /** Ouverture rebondissante des feuilles (dépassement d'une douzaine de points puis retour). */
    fun <T> sheetIn(): FiniteAnimationSpec<T> = spring(dampingRatio = .72f, stiffness = 380f)
    /** Entrée en cascade des lignes (ouverture de l'app, changement de jour). */
    fun <T> enter(): FiniteAnimationSpec<T> = spring(dampingRatio = .7f, stiffness = 420f)

    /** Retour d'appui, petits éléments (ancien jeton, aligné sur `press`). */
    fun <T> quick(): FiniteAnimationSpec<T> = press()
    /** Déplacements, changements d'état (ancien jeton, aligné sur `move`). */
    fun <T> standard(): FiniteAnimationSpec<T> = move()
    /** Moments forts (ancien jeton, aligné sur `pop`). */
    fun <T> emphasis(): FiniteAnimationSpec<T> = pop()
    /** Fondu d'entrée / de sortie. */
    fun <T> fadeIn(ms: Int = 220): FiniteAnimationSpec<T> = tween(ms, easing = Emphasized)
    fun <T> fadeOut(ms: Int = 140): FiniteAnimationSpec<T> = tween(ms, easing = Exit)
}

/** Coche dessinée progressivement (200 ms à la validation) ([progress] 0 → 1) : le trait suit la forme de la coche. */
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

/**
 * Nombre dont les chiffres roulent verticalement quand il change (série qui monte, compteur) : l'ancien part vers le
 * haut, le nouveau arrive d'en bas (ressort `move`), avec un petit cran haptique. Animations réduites : changement direct.
 */
@Composable
fun RollingNumber(value: Int, modifier: Modifier = Modifier, size: TextUnit, color: Color, weight: FontWeight = FontWeight.ExtraBold) {
    val p = LocalPal.current
    AnimatedContent(value, modifier, transitionSpec = {
        if (p.motion) fadeIn(tween(100)) togetherWith fadeOut(tween(100))
        else {
            val up = targetState > initialState
            (slideInVertically(Motion.move<IntOffset>()) { if (up) it else -it } + fadeIn(tween(120))) togetherWith
                (slideOutVertically(Motion.move<IntOffset>()) { if (up) -it else it } + fadeOut(tween(120)))
        }
    }, label = "roll") { v ->
        LaunchedEffect(v) { if (!p.motion) Feedback.tick() }
        Text("$v", fontFamily = Unbounded, fontWeight = weight, fontSize = size, color = color, maxLines = 1, lineHeight = size * 1.05f)
    }
}

/** « Pop » quand [key] change : échelle 1 → 1,15 → 1 (ressort `pop`). */
@Composable
fun Modifier.popOnChange(key: Any?, to: Float = 1.15f): Modifier {
    val motionOff = LocalPal.current.motion
    val a = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(key) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        if (!motionOff) { a.snapTo(to); a.animateTo(1f, Motion.pop()) }
    }
    return this.graphicsLayer { scaleX = a.value; scaleY = a.value }
}

/** Secousse horizontale de ±6 dp (3 oscillations en 300 ms) quand [trigger] change : refus, erreur. */
@Composable
fun Modifier.shake(trigger: Int): Modifier {
    val motionOff = LocalPal.current.motion
    val a = remember { Animatable(0f) }
    LaunchedEffect(trigger) { if (trigger != 0 && !motionOff) { a.snapTo(0f); a.animateTo(1f, tween(300, easing = LinearEasing)); a.snapTo(0f) } }
    return this.graphicsLayer { val t = a.value; translationX = if (t <= 0f || t >= 1f) 0f else kotlin.math.sin(t * 6f * Math.PI.toFloat()) * (1f - t) * 6.dp.toPx() }
}

/** Confetti : position de départ, vitesse, couleur, rotation. */
private class Bit(val x: Float, val vx: Float, val vy: Float, val c: Int, val spin: Float)

/**
 * Confettis des moments forts (journée parfaite, palier, niveau, badge) : [count] rectangles arrondis en aplats
 * (néons et flamme), trajectoire balistique de 1,2 s dans un seul Canvas, puis [onEnd]. Rien en animations réduites.
 */
@Composable
fun Confetti(count: Int = 24, onEnd: () -> Unit = {}) {
    val p = LocalPal.current
    if (p.motion) { LaunchedEffect(Unit) { onEnd() }; return }
    val n = particles(count.coerceAtMost(40))
    val bits = remember { List(n) { Bit(.2f + Random.nextFloat() * .6f, (Random.nextFloat() - .5f) * 1.1f, -(1.1f + Random.nextFloat() * .9f), it, (Random.nextFloat() - .5f) * 900f) } }
    val cols = listOf(p.flame2, p.flame, p.pink, p.type("objective").c, p.type("routine").c, p.type("monthly").c, p.type("abstinence").c)
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(1200, easing = LinearEasing)); onEnd() }
    Canvas(Modifier.fillMaxSize()) {
        val k = t.value; val w = size.width; val h = size.height
        val bw = 4.dp.toPx(); val bh = 7.dp.toPx()
        for (b in bits) {
            // Gerbe qui part du bas du tiers supérieur, freinée puis rattrapée par la gravité
            val x = w * b.x + b.vx * w * .5f * k
            val y = h * .38f + b.vy * h * .55f * k + h * 1.1f * k * k
            val a = 1f - ((k - .75f) / .25f).coerceIn(0f, 1f)
            rotate(b.spin * k, Offset(x, y)) { drawRoundRect(cols[b.c % cols.size].copy(alpha = a), Offset(x - bw / 2, y - bh / 2), Size(bw, bh), CornerRadius(1.5f * density)) }
        }
    }
}

/**
 * Étiquette qui monte de 16 dp en s'effaçant (500 ms) chaque fois que [trigger] change : « +250 ml » au-dessus de la case
 * d'un cap. Lu dans la phase de dessin (graphicsLayer) ; invisible au repos. Animations réduites : rien.
 */
@Composable
fun Modifier.floatUp(trigger: Any?): Modifier {
    val motionOff = LocalPal.current.motion
    val a = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(trigger) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        if (!motionOff) { a.snapTo(0f); a.animateTo(1f, tween(520, easing = Motion.Emphasized)) }
    }
    return this.graphicsLayer { val v = a.value; alpha = if (v >= 1f) 0f else (1f - v).coerceIn(0f, 1f); translationY = -16.dp.toPx() * v }
}

/** Apparition d'un menu ou d'un dialogue : échelle 0,92 → 1 et fondu (ressort `pop`). Animations réduites : fondu seul. */
@Composable
fun Modifier.popIn(origin: androidx.compose.ui.graphics.TransformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center): Modifier {
    val motionOff = LocalPal.current.motion
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { if (motionOff) a.animateTo(1f, tween(120)) else a.animateTo(1f, Motion.pop()) }
    return this.graphicsLayer {
        val v = a.value; alpha = v.coerceIn(0f, 1f); transformOrigin = origin
        if (!motionOff) { val sc = .92f + .08f * v; scaleX = sc; scaleY = sc }
    }
}
