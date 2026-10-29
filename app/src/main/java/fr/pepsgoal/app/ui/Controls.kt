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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import fr.pepsgoal.app.ui.theme.PLUM
import fr.pepsgoal.app.ui.theme.step
import fr.pepsgoal.core.model.Goal
import fr.pepsgoal.core.model.User
import fr.pepsgoal.core.shownIcon
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------ objectifs : tuile, case à valider */

/** Tuile d'un objectif : aplat du néon de son type, emoji Fluent en grand. */
@Composable
fun GoalIcon(g: Goal, size: Dp = 52.dp, masked: Boolean = false) {
    val tc = LocalPal.current.type(g.type)
    EmojiTile(if (masked) g.shownIcon() else g.icon, tc.c, size)
}

/**
 * Case à valider (44 dp visibles, 48 tactiles), selon l'état :
 * - à faire : surface, contour et marche de 2,5 / 3 dp ;
 * - faite : aplat du néon du type, coche prune dessinée qui « pop » (0,6 → 1,25 → 1) ;
 * - progrès ([progress] non nul) ou minuteur ([running], icône play / pause) : anneau néon et icône ;
 * - fermée ([disabled]) : cercle pointillé et cadenas, sans marche.
 * [onLongClick] : saisie détaillée (types chiffrés).
 */
@Composable
fun CheckButton(done: Boolean, just: Boolean, label: String, color: Color, disabled: Boolean = false, icon: String = "check", running: Boolean = false,
                progress: Float? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val p = LocalPal.current
    val pop = remember { Animatable(1f) }
    val draw = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(just, done) {
        if (just && done && !p.motion) {
            draw.snapTo(0f)
            launch { pop.snapTo(.6f); pop.animateTo(1.25f, tween(180, easing = Motion.Emphasized)); pop.animateTo(1f, Motion.pop()) }
            kotlinx.coroutines.delay(90); draw.animateTo(1f, tween(200, easing = Motion.Emphasized))
        } else { draw.snapTo(if (done) 1f else 0f); pop.snapTo(1f) }
    }
    val c = vivid(color)
    val lock = disabled && !done
    val timer = !done && (running || icon == "play" || icon == "pause")
    val prog = !done && !timer && progress != null
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter, !lock)
    Box(
        Modifier.size(48.dp).clip(CircleShape).springClick(inter, label = label, onLongClick = onLongClick, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        when {
            lock -> Box(Modifier.size(44.dp).drawBehind {
                drawCircle(p.control, size.minDimension / 2 - 1.25.dp.toPx(), style = Stroke(2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))))
            }, contentAlignment = Alignment.Center) { Pi("lock", size = 18.dp, tint = p.ink3) }
            done -> Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.size(44.dp, 47.dp).chunk(c, if (p.contrast) p.ink else c.step(), 22.dp, 3.dp, 0.dp, press), contentAlignment = Alignment.Center) {
                if (icon == "check" || icon == "play" || icon == "pause") DrawnCheck(draw.value, PLUM, 26.dp, 3.4.dp) else Pi(icon, size = 20.dp, tint = PLUM)
            }
            timer || prog -> {
                val f = (progress ?: 0f).coerceIn(0f, 1f)
                Box(Modifier.size(44.dp, 47.dp).chunk(p.surface, if (p.contrast) p.ink else p.edge, 22.dp, 3.dp, 0.dp, press).drawBehind {
                    val sw = 5.dp.toPx(); val o = Offset(sw / 2 + 2f, sw / 2 + 2f); val s = Size(size.width - sw - 4f, size.height - sw - 4f)
                    drawArc(p.edge, 0f, 360f, false, o, s, style = Stroke(sw))
                    if (f > .001f) drawArc(c, -90f, 360f * f, false, o, s, style = Stroke(sw, cap = StrokeCap.Round))
                }, contentAlignment = Alignment.Center) {
                    Pi(if (timer) (if (running) "pause" else "play") else if (icon == "check") "plus" else icon, size = 18.dp, tint = p.ink)
                }
            }
            else -> Box(Modifier.size(44.dp, 47.dp).chunk(p.surface, if (p.contrast) p.ink else p.control, 22.dp, 3.dp, 2.5.dp, press), contentAlignment = Alignment.Center) {
                if (icon != "check") Pi(icon, size = 18.dp, tint = p.ink)
            }
        }
    }
}

/** Couleur d'accent assombrie en thème clair (contraste suffisant sur les cartes), inchangée en thème sombre. */
@Composable
fun readable(c: Color): Color {
    val p = LocalPal.current
    if (p.dark) return c
    return lerp(c, PLUM, if (p.contrast) .7f else .45f)
}

/** Petit bouton d'action (« +1 », « + Mesure »…) : bouton de type, aplat du néon et marche. */
@Composable
fun MiniBtn(text: String, color: Color, onClick: () -> Unit) = PButton(text, onClick, small = true, kind = "type", color = color)

/* ------------------------------------------------------------------ habillage : barre d'onglets, bouton +, barre du haut */

/**
 * Barre d'onglets : capsule flottante opaque à marche. L'onglet actif est une pilule du dégradé de la flamme, 1,7 fois
 * plus large qu'un onglet, avec icône et libellé prune ; elle glisse d'un onglet à l'autre (ressort `move`) et l'icône
 * active fait un petit saut. Au repos : icône seule (libellé lu par TalkBack) ; Lecture facile : tous les libellés.
 */
@Composable
fun NavBar(items: List<Triple<String, String, String>>, tab: String, badges: Map<String, Int>, onNav: (String) -> Unit, labels: Map<String, String> = emptyMap(), modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val idx = items.indexOfFirst { it.first == tab }.coerceAtLeast(0)
    val n = items.size.coerceAtLeast(1)
    val wide = if (p.easy) 1f else 1.7f
    val pos by animateFloatAsState(idx.toFloat(), if (p.motion) snap() else Motion.move(), label = "navPill")
    Row(modifier.fillMaxWidth().chunk(p.surface, if (p.contrast) p.ink else p.edge, 30.dp, 4.dp, 2.dp).height(60.dp).padding(horizontal = 4.dp).drawBehind {
        // Pilule de l'onglet actif, dessinée à la position animée : aucune nouvelle mise en page pendant le glissement
        val unit = size.width / (n - 1 + wide); val l = pos * unit; val w = unit * wide
        val v = 6.dp.toPx(); val h = size.height - 2 * v
        val br = if (p.contrast) SolidColor(p.ink) else Brush.linearGradient(listOf(p.flame2, p.flame, p.pink), Offset(l, v), Offset(l + w, v + h))
        drawRoundRect(br, Offset(l, v), Size(w, h), CornerRadius(h / 2))
    }, verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, (k, l0, ic) ->
            val label = labels[k] ?: l0
            val sel = i == idx
            val cnt = badges[k] ?: 0
            val inter = remember { MutableInteractionSource() }
            val fg = if (sel) (if (p.contrast) p.bg else p.onAccent) else p.ink2
            Box(Modifier.weight(if (sel) wide else 1f).fillMaxHeight().clip(CircleShape).springClick(inter, label = label) { onNav(k) }
                .semantics(mergeDescendants = true) { selected = sel; role = Role.Tab; contentDescription = if (cnt > 0) "$label, $cnt" else label },
                contentAlignment = Alignment.Center) {
                if (p.easy) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box { Pi(ic, size = 22.dp, tint = fg); if (cnt > 0 && !sel) NavDot(Modifier.align(Alignment.TopEnd)) }
                    FitText(label, size = 12, min = 8, color = fg, weight = FontWeight.ExtraBold)
                } else if (sel) Row(Modifier.popOnChange(tab, 1.12f).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    Pi(ic, size = 22.dp, tint = fg)
                    FitText(label, Modifier.weight(1f, fill = false), size = 13, min = 9, color = fg, weight = FontWeight.ExtraBold)
                } else Box { Pi(ic, size = 24.dp, tint = fg); if (cnt > 0) NavDot(Modifier.align(Alignment.TopEnd)) }
            }
        }
    }
}

/** Point de notification d'un onglet (10 dp, rose). */
@Composable
private fun NavDot(modifier: Modifier) = Box(modifier.offset(4.dp, (-2).dp).size(10.dp).background(vivid(LocalPal.current.pink), CircleShape))

/**
 * Bouton + : rond de 64 dp en dégradé de la flamme, marche de 5 dp, icône prune. Il se cache en glissant vers le bas
 * quand on descend dans la page (ressort `move`) et revient avec un rebond (`pop`).
 */
@Composable
fun Fab(desc: String, shown: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String = "plus") {
    val p = LocalPal.current
    val k by animateFloatAsState(if (shown) 1f else 0f, if (p.motion) snap() else if (shown) Motion.pop() else Motion.move(), label = "fab")
    if (k <= .02f) return
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter)
    Box(modifier.graphicsLayer { alpha = k.coerceIn(0f, 1f); translationY = (1f - k) * 96.dp.toPx(); val s = .6f + .4f * k; scaleX = s; scaleY = s }
        .size(64.dp, 69.dp).chunk(p.flameBrush(), p.flameStep, 32.dp, 5.dp, 0.dp, press)
        .clip(CircleShape).springClick(inter, label = desc, onClick = onClick).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        Pi(icon, size = 30.dp, tint = if (p.contrast) p.bg else p.onAccent)
    }
}

/** Titre de l'onglet : Unbounded 30 ExtraBold ; la taille s'adapte à la place laissée par les boutons. */
@Composable
fun TabTitle(text: String) {
    val p = LocalPal.current
    FitText(text, size = 30, min = 16, color = p.ink, weight = FontWeight.ExtraBold, family = Unbounded)
}

/** Pastille de la série (ancienne barre du haut) : flamme, nombre de jours, jokers. Bouton à marche. */
@Composable
fun StreakPill(n: Int, freezes: Int, desc: String, onClick: () -> Unit, bump: Float = 1f) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter)
    Row(Modifier.height(47.dp).chunk(p.surface, p.edge, 22.dp, 3.dp, 2.dp, press)
        .clip(CircleShape).springClick(inter, label = "Ouvrir le profil", onClick = onClick).semantics(mergeDescendants = true) { contentDescription = desc }.padding(start = 10.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.graphicsLayer { scaleX = bump; scaleY = bump }, verticalAlignment = Alignment.CenterVertically) {
            Flame(22.dp, dim = n == 0)
            Text("$n", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        if (freezes > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Emoji("🧊", 16.dp); Text("$freezes", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

/** Bouton rond à marche (barre du haut) avec contenu libre et pastille de compteur. */
@Composable
fun RoundGlass(desc: String, onClick: () -> Unit, badge: Int = 0, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter)
    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(44.dp, 47.dp).chunk(p.surface, if (p.contrast) p.ink else p.edge, 22.dp, 3.dp, 2.dp, press)
            .clip(CircleShape).springClick(inter, label = desc, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = if (badge > 0) "$desc, $badge non lus" else desc; role = Role.Button },
            contentAlignment = Alignment.Center, content = content)
        if (badge > 0) Badge(badge, Modifier.align(Alignment.TopEnd).offset(2.dp, 0.dp))
    }
}

/**
 * Avatar de la barre du haut (40 dp) : anneau de la flamme de 3 dp, fond or et initiales prune (ou la photo, l'emoji
 * choisi), badge du niveau prune et or. Ouvre le profil.
 */
@Composable
fun AvatarButton(u: User, level: Int?, onClick: () -> Unit, size: Dp = 40.dp) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    // Pas de découpe : le badge du niveau dépasse du cercle
    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).bounce(inter, .92f)
        .springClick(inter, label = "Ouvrir le profil", onClick = onClick).semantics(mergeDescendants = true) { contentDescription = "Profil${level?.let { ", niveau $it" } ?: ""}"; role = Role.Button },
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(size + 8.dp)) {
            AvatarFace(u, size, Modifier.align(Alignment.Center))
            if (level != null) Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 2.dp).background(PLUM, CircleShape).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text("$level", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = p.gold, lineHeight = 13.sp)
            }
        }
    }
}

/** Visage d'un avatar dans l'anneau de la flamme : photo ou emoji choisis, sinon initiales prune sur fond or. */
@Composable
fun AvatarFace(u: User?, size: Dp, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val kind = u?.avatar?.kind ?: ""
    Box(modifier.size(size + 8.dp).background(p.flameBrush(), CircleShape).padding(3.dp).background(p.bg, CircleShape).padding(2.dp).clip(CircleShape)
        .then(if (kind in setOf("photo", "emoji", "picto")) Modifier else Modifier.background(p.gold)), contentAlignment = Alignment.Center) {
        if (kind in setOf("photo", "emoji", "picto")) Av(u, size - 2.dp)
        else {
            val ini = (u?.name ?: u?.username ?: "?").trim().split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
            Text(ini, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * .36f).sp, color = PLUM, maxLines = 1)
        }
    }
}
