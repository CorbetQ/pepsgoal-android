package fr.pepsgoal.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.onColors
import fr.pepsgoal.core.model.Goal
import fr.pepsgoal.core.shownIcon
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------ objectifs : pastille, case à valider, mini-bouton */

@Composable
fun GoalIcon(g: Goal, size: Dp = 44.dp, masked: Boolean = false) {
    val tc = LocalPal.current.type(g.type)
    EmojiTile(if (masked) g.shownIcon() else g.icon, tc.c, size)
}

/**
 * Case à valider : bulle de verre cerclée de la couleur du type ; validée, elle devient une sphère brillante qui
 * s'écrase puis rebondit, la coche se dessine au trait et une onde s'élargit autour.
 */
@Composable
fun CheckButton(done: Boolean, just: Boolean, label: String, color: Color, disabled: Boolean = false, icon: String = "check", running: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    val pop = remember { Animatable(1f) }
    val draw = remember { Animatable(if (done) 1f else 0f) }
    val wave = remember { Animatable(1f) }
    LaunchedEffect(just, done) {
        if (just && done && !p.motion) {
            draw.snapTo(0f); wave.snapTo(0f)
            launch { pop.snapTo(.68f); pop.animateTo(1f, spring(dampingRatio = .34f, stiffness = 480f)) }
            launch { wave.animateTo(1f, tween(560, easing = Motion.Emphasized)) }
            kotlinx.coroutines.delay(70); draw.animateTo(1f, tween(300, easing = Motion.Emphasized))
        } else { draw.snapTo(if (done) 1f else 0f); wave.snapTo(1f) }
    }
    val filled = done || running
    // Couleurs du type lisibles dans les deux thèmes : foncées sur fond clair, vives sur fond sombre
    val line = readable(color)
    val onC = onColors(listOf(color))
    val inter = remember { MutableInteractionSource() }
    val base = if (LocalInSheet.current) p.bg else p.card
    Box(
        Modifier.drawBehind {
            val w = wave.value
            if (w < 1f) {
                drawCircle(color.copy(alpha = (1f - w) * .5f), size.minDimension / 2f * (1f + w * .9f), style = androidx.compose.ui.graphics.drawscope.Stroke((1f - w) * 5.dp.toPx() + 1f))
                drawCircle(color.copy(alpha = (1f - w) * .18f), size.minDimension / 2f * (1f + w * .55f))
            }
        }.bounce(inter, .78f).scale(pop.value).size(46.dp)
            .then(when {
                filled -> Modifier.candy(CircleShape, listOf(color.lift(.4f), color, color.deepen(.1f)), p, glow = !disabled, elevation = 10.dp)
                p.contrast -> Modifier.background(base, CircleShape).border(2.5.dp, if (disabled) p.ink3 else line, CircleShape)
                else -> Modifier.depth(CircleShape, p, 1).background(lerp(base, color, if (disabled) .03f else if (p.dark) .16f else .1f), CircleShape)
                    .border(2.5.dp, if (disabled) SolidBrush(p.ink3.copy(alpha = .4f)) else Brush.linearGradient(listOf(color.lift(.25f), line)), CircleShape)
                    .sheen(CircleShape, p.dark, .7f, rim = false)
            })
            .clip(CircleShape).springClick(inter, label = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (done && !running && icon == "check") DrawnCheck(draw.value, onC, 26.dp, 3.2.dp)
        else Pi(icon, size = 22.dp, tint = if (filled) onC else if (disabled) p.ink3.copy(alpha = .55f) else line)
    }
}

private fun SolidBrush(c: Color) = androidx.compose.ui.graphics.SolidColor(c)

/** Couleur d'accent assombrie en thème clair (contraste suffisant sur le verre), inchangée en thème sombre. */
@Composable
fun readable(c: Color): Color {
    val p = LocalPal.current
    if (p.dark) return c
    return lerp(c, Color(0xFF10102A), if (p.contrast) .7f else .42f)
}

/** Petit bouton d'action d'une ligne d'objectif (« + Progrès », « +1 »…) : pastille teintée de la couleur du type. */
@Composable
fun MiniBtn(text: String, color: Color, onClick: () -> Unit) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val base = if (LocalInSheet.current) p.bg else p.card
    Box(Modifier.bounce(inter, .86f).depth(CircleShape, p, 1).background(Brush.verticalGradient(listOf(lerp(base, color.lift(.3f), if (p.dark) .26f else .3f), lerp(base, color, if (p.dark) .2f else .24f))), CircleShape)
        .border(1.5.dp, readable(color).copy(alpha = .5f), CircleShape).clip(CircleShape).springClick(inter, onClick = onClick).heightIn(min = 38.dp).padding(horizontal = 13.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = p.ink, maxLines = 1)
    }
}

/* ------------------------------------------------------------------ habillage : barre de navigation, bouton +, barre du haut */

/**
 * Barre de navigation : capsule de verre flottante. L'onglet actif est repéré par une pastille « bonbon » de l'accent
 * qui glisse en s'étirant comme une goutte (le bord avant part le premier), puis rebondit en place.
 */
@Composable
fun NavBar(items: List<Triple<String, String, String>>, tab: String, badges: Map<String, Int>, onNav: (String) -> Unit, labels: Map<String, String> = emptyMap(), modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val idx = items.indexOfFirst { it.first == tab }.coerceAtLeast(0)
    val shape = smoothShape(30.dp); val pill = smoothShape(23.dp)
    Box(modifier.fillMaxWidth().depth(shape, p, 3).glassBg(shape, strong = true)
        .then(if (p.contrast) Modifier.border(1.dp, p.line, shape) else Modifier.sheen(shape, p.dark, .8f)).padding(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth / items.size
            val prev = remember { intArrayOf(idx) }
            val right = idx >= prev[0]; SideEffect { prev[0] = idx }
            val lead = spring<Dp>(dampingRatio = .6f, stiffness = 640f); val lag = spring<Dp>(dampingRatio = .74f, stiffness = 240f)
            val l by animateDpAsState(w * idx, if (p.motion) snap() else if (right) lag else lead, label = "navL")
            val r by animateDpAsState(w * (idx + 1), if (p.motion) snap() else if (right) lead else lag, label = "navR")
            Box(Modifier.offset(x = l + 2.dp).width((r - l - 4.dp).coerceAtLeast(w * .55f)).height(58.dp)
                .then(if (p.contrast) Modifier.background(p.ink, pill) else Modifier.candy(pill, listOf(p.flame2, p.flame, p.pink), p, elevation = 10.dp)))
            Row(Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, (k, l0, ic) ->
                    val label = labels[k] ?: l0
                    val sel = i == idx
                    val col by animateColorAsState(if (sel) (if (p.contrast) p.bg else p.onAccent) else p.ink2, tween(if (p.motion) 0 else 180), label = "navc")
                    val pop by animateFloatAsState(if (sel && !p.motion) 1.14f else 1f, spring(dampingRatio = .4f, stiffness = 520f), label = "navi")
                    val n = badges[k] ?: 0
                    val inter = remember { MutableInteractionSource() }
                    Column(Modifier.weight(1f).bounce(inter, .84f).height(58.dp).clip(pill).springClick(inter, label = label) { onNav(k) }
                        .semantics { selected = sel; role = Role.Tab; contentDescription = if (n > 0) "$label, $n" else label },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.graphicsLayer { scaleX = pop; scaleY = pop; translationY = (1f - pop) * 6.dp.toPx() }) {
                            Pi(ic, size = 22.dp, tint = col)
                            if (n > 0) Badge(n, Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-7).dp))
                        }
                        Spacer(Modifier.height(3.dp))
                        FitText(label, size = 11, min = 8, color = col, weight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

/**
 * Bouton d'action flottant : bonbon de l'accent aux coins continus, avec halo. Il s'efface en glissant quand on
 * descend dans la page et revient en rebondissant (en tournant d'un quart de tour) dès qu'on remonte.
 */
@Composable
fun Fab(desc: String, shown: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String = "plus") {
    val p = LocalPal.current
    val k by animateFloatAsState(if (shown) 1f else 0f, if (p.motion) snap() else spring(dampingRatio = .52f, stiffness = 380f), label = "fab")
    if (k <= .02f) return
    val inter = remember { MutableInteractionSource() }
    val shape = smoothShape(22.dp)
    Box(modifier.graphicsLayer { alpha = k.coerceIn(0f, 1f); translationX = (1f - k) * 110.dp.toPx(); val s = .6f + .4f * k; scaleX = s; scaleY = s; rotationZ = (1f - k) * -90f }
        .bounce(inter, .82f).size(60.dp).then(if (p.contrast) Modifier.background(p.ink, shape) else Modifier.candy(shape, listOf(p.flame2, p.flame, p.pink), p, elevation = 16.dp))
        .clip(shape).springClick(inter, label = desc, onClick = onClick).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        Pi(icon, size = 28.dp, tint = if (p.contrast) p.bg else p.onAccent)
    }
}

/** Titre de l'onglet : sa taille s'adapte à la place laissée par les boutons. */
@Composable
fun TabTitle(text: String) {
    val p = LocalPal.current
    FitText(text, size = 27, min = 14, color = p.ink, weight = FontWeight.ExtraBold, family = Unbounded)
}

/** Pastille de la série (barre du haut) : flamme sur un halo chaud, nombre de jours, gels de série. */
@Composable
fun StreakPill(n: Int, freezes: Int, desc: String, onClick: () -> Unit, bump: Float = 1f) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    Row(Modifier.bounce(inter, .88f).height(46.dp).depth(CircleShape, p, 1).glassBg(CircleShape, strong = true)
        .then(if (p.contrast) Modifier.border(1.dp, p.line, CircleShape) else Modifier.sheen(CircleShape, p.dark, .7f))
        .clip(CircleShape).springClick(inter, label = "Ouvrir le profil", onClick = onClick).semantics(mergeDescendants = true) { contentDescription = desc }.padding(start = 8.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.graphicsLayer { scaleX = bump; scaleY = bump }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).drawBehind {
                if (!p.contrast) drawCircle(Brush.radialGradient(listOf(p.flame.copy(alpha = if (p.dark) .45f else .32f), Color.Transparent), radius = size.minDimension * .62f), size.minDimension * .62f)
            }, contentAlignment = Alignment.Center) { Flame(24.dp, dim = n == 0) }
            Text("$n", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
        if (freezes > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Pi("snow", size = 16.dp, tint = p.type("objective").ink); Text("$freezes", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

/** Bouton rond en verre (barre du haut) : reflet, liseré lumineux et ombre douce. */
@Composable
fun RoundGlass(desc: String, onClick: () -> Unit, badge: Int = 0, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    Box {
        Box(Modifier.bounce(inter, .84f).size(46.dp).depth(CircleShape, p, 1).glassBg(CircleShape, strong = true)
            .then(if (p.contrast) Modifier.border(1.dp, p.line, CircleShape) else Modifier.sheen(CircleShape, p.dark, .7f))
            .clip(CircleShape).springClick(inter, label = desc, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = if (badge > 0) "$desc, $badge non lus" else desc; role = Role.Button },
            contentAlignment = Alignment.Center, content = content)
        if (badge > 0) Badge(badge, Modifier.align(Alignment.TopEnd).offset(4.dp, (-2).dp))
    }
}
