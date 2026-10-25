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
 * Case à valider : cercle fin de la couleur du type ; validée, elle se remplit, la coche se dessine au trait et une
 * onde légère s'élargit autour.
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
            launch { pop.snapTo(.82f); pop.animateTo(1f, spring(dampingRatio = .6f, stiffness = 500f)) }
            launch { wave.animateTo(1f, tween(480, easing = Motion.Emphasized)) }
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
                drawCircle(color.copy(alpha = (1f - w) * .4f), size.minDimension / 2f * (1f + w * .6f), style = androidx.compose.ui.graphics.drawscope.Stroke((1f - w) * 3.dp.toPx() + 1f))
            }
        }.bounce(inter, .9f).scale(pop.value).size(40.dp)
            .then(when {
                filled -> Modifier.background(color, CircleShape)
                else -> Modifier.border(2.dp, if (disabled) p.ink3.copy(alpha = .4f) else line, CircleShape)
            })
            .clip(CircleShape).springClick(inter, label = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (done && !running && icon == "check") DrawnCheck(draw.value, onC, 22.dp, 2.6.dp)
        else if (icon != "check" || filled) Pi(icon, size = 20.dp, tint = if (filled) onC else if (disabled) p.ink3.copy(alpha = .55f) else line)
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
    Box(Modifier.bounce(inter).background(color.copy(alpha = if (p.dark) .2f else .14f), CircleShape).clip(CircleShape).springClick(inter, onClick = onClick)
        .heightIn(min = 34.dp).padding(horizontal = 12.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = fr.pepsgoal.app.ui.theme.readable(readable(color), lerp(base, color, .14f), 4.5f), maxLines = 1)
    }
}

/* ------------------------------------------------------------------ habillage : barre de navigation, bouton +, barre du haut */

/**
 * Barre d'onglets en verre (Liquid Glass) : capsule flottante floutée ; l'onglet actif est repéré par une pastille
 * plus claire qui glisse d'un onglet à l'autre, icône et libellé à la couleur de l'accent.
 */
@Composable
fun NavBar(items: List<Triple<String, String, String>>, tab: String, badges: Map<String, Int>, onNav: (String) -> Unit, labels: Map<String, String> = emptyMap(), modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val idx = items.indexOfFirst { it.first == tab }.coerceAtLeast(0)
    val shape = CircleShape; val pill = CircleShape
    Box(modifier.fillMaxWidth().depth(shape, p, 3).glassBg(shape, strong = true)
        .then(if (p.contrast) Modifier.border(1.dp, p.line, shape) else Modifier.sheen(shape, p.dark, .8f)).padding(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth / items.size
            val prev = remember { intArrayOf(idx) }
            val right = idx >= prev[0]; SideEffect { prev[0] = idx }
            val lead = spring<Dp>(dampingRatio = .85f, stiffness = 700f); val lag = spring<Dp>(dampingRatio = .9f, stiffness = 380f)
            val l by animateDpAsState(w * idx, if (p.motion) snap() else if (right) lag else lead, label = "navL")
            val r by animateDpAsState(w * (idx + 1), if (p.motion) snap() else if (right) lead else lag, label = "navR")
            // Onglet actif : pastille de verre plus claire (Liquid Glass), l'icône et le libellé prennent l'accent
            Box(Modifier.offset(x = l + 2.dp).width((r - l - 4.dp).coerceAtLeast(w * .55f)).height(54.dp)
                .background(if (p.contrast) p.ink else if (p.dark) Color.White.copy(alpha = .12f) else p.ink.copy(alpha = .07f), pill))
            Row(Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, (k, l0, ic) ->
                    val label = labels[k] ?: l0
                    val sel = i == idx
                    val col by animateColorAsState(if (sel) (if (p.contrast) p.bg else p.flameInk) else p.ink2, tween(if (p.motion) 0 else 180), label = "navc")
                    val pop = 1f
                    val n = badges[k] ?: 0
                    val inter = remember { MutableInteractionSource() }
                    Column(Modifier.weight(1f).bounce(inter).height(54.dp).clip(pill).springClick(inter, label = label) { onNav(k) }
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

/** Bouton d'action flottant : rond plein de l'accent ; il s'efface quand on descend dans la page et revient quand on remonte. */
@Composable
fun Fab(desc: String, shown: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String = "plus") {
    val p = LocalPal.current
    val k by animateFloatAsState(if (shown) 1f else 0f, if (p.motion) snap() else spring(dampingRatio = .9f, stiffness = 500f), label = "fab")
    if (k <= .02f) return
    val inter = remember { MutableInteractionSource() }
    val shape = CircleShape
    Box(modifier.graphicsLayer { alpha = k.coerceIn(0f, 1f); val s = .8f + .2f * k; scaleX = s; scaleY = s }
        .bounce(inter).size(56.dp).depth(shape, p, 3).then(if (p.contrast) Modifier.background(p.ink, shape) else Modifier.candy(shape, listOf(p.flame2, p.flame), p))
        .clip(shape).springClick(inter, label = desc, onClick = onClick).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        Pi(icon, size = 26.dp, tint = if (p.contrast) p.bg else p.onAccent)
    }
}

/** Titre de l'onglet : sa taille s'adapte à la place laissée par les boutons. */
@Composable
fun TabTitle(text: String) {
    val p = LocalPal.current
    FitText(text, size = 28, min = 14, color = p.ink, weight = FontWeight.Bold, family = Unbounded)
}

/** Pastille de la série (barre du haut) : flamme sur un halo chaud, nombre de jours, gels de série. */
@Composable
fun StreakPill(n: Int, freezes: Int, desc: String, onClick: () -> Unit, bump: Float = 1f) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    Row(Modifier.bounce(inter, .88f).height(46.dp).depth(CircleShape, p, 1).glassBg(CircleShape, strong = true)
        .then(if (p.contrast) Modifier.border(1.dp, p.line, CircleShape) else Modifier.sheen(CircleShape, p.dark, .7f))
        .clip(CircleShape).springClick(inter, label = "Ouvrir le profil", onClick = onClick).semantics(mergeDescendants = true) { contentDescription = desc }.padding(start = 10.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.graphicsLayer { scaleX = bump; scaleY = bump }, verticalAlignment = Alignment.CenterVertically) {
            Flame(22.dp, dim = n == 0)
            Text("$n", fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
