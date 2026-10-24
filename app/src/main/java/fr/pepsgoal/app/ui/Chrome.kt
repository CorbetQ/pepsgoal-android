package fr.pepsgoal.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded

/*
 * Habillage de l'application (barre de navigation, barre du haut, bouton d'action, en-têtes de page) : composants
 * sans état, pilotés par la racine et les écrans. Ils ne dépendent pas de l'état de l'application, ce qui permet de
 * les afficher aussi dans les aperçus.
 */

/** Onglet de la barre de navigation : clé, libellé, icône. */
typealias NavItem = Triple<String, String, String>

/**
 * Barre de navigation flottante : pilule en verre, libellés toujours visibles, onglet actif repéré par une pastille
 * aux couleurs de l'accent derrière l'icône. La pastille glisse d'un onglet à l'autre en s'étirant légèrement.
 */
@Composable
fun NavBar(items: List<NavItem>, selected: String, badges: Map<String, Int>, onTap: (String) -> Unit, labels: Map<String, String> = emptyMap(), modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val idx = items.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val shape = RoundedCornerShape(30.dp)
    Box(modifier.fillMaxWidth()
        .shadow((if (p.oled || p.transp) 0.dp else 12.dp).ifShadows(), shape, ambientColor = p.shadow, spotColor = p.shadow)
        .glassBg(shape, strong = true).border(1.dp, if (p.contrast) SolidColor(p.line) else Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.dark) .14f else .9f), Color.White.copy(alpha = if (p.dark) .05f else .4f))), shape)
        .padding(horizontal = 6.dp, vertical = 6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth / items.size; val iw = minOf(64.dp, w - 8.dp)
            // Bord avant puis bord arrière : la pastille s'étire pendant le glissement (effet « goutte »)
            val prev = remember { intArrayOf(idx) }
            val right = idx >= prev[0]; SideEffect { prev[0] = idx }
            val lead = spring<Dp>(dampingRatio = .72f, stiffness = 620f); val lag = spring<Dp>(dampingRatio = .82f, stiffness = 280f)
            val x0 = w * idx + (w - iw) / 2
            val l by animateDpAsState(x0, if (p.motion) snap() else if (right) lag else lead, label = "navL")
            val r by animateDpAsState(x0 + iw, if (p.motion) snap() else if (right) lead else lag, label = "navR")
            Box(Modifier.padding(top = 6.dp).offset(x = l).width((r - l).coerceAtLeast(iw * .7f)).height(32.dp).clip(CircleShape)
                .background(if (p.contrast) SolidColor(p.ink) else Brush.horizontalGradient(listOf(p.flame2.copy(alpha = if (p.dark) .3f else .22f), p.pink.copy(alpha = if (p.dark) .26f else .18f)))))
            Row(Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, (k, l0, ic) ->
                    val label = labels[k] ?: l0
                    val sel = i == idx
                    val col by animateColorAsState(if (sel) (if (p.contrast) p.bg else p.flameInk) else p.ink2, tween(if (p.motion) 0 else 200), label = "navc")
                    val pop by animateFloatAsState(if (sel && !p.motion) 1.08f else 1f, Motion.emphasis(), label = "navi")
                    val n = badges[k] ?: 0
                    val inter = remember { MutableInteractionSource() }
                    Column(Modifier.weight(1f).bounce(inter, .9f).height(60.dp).clip(RoundedCornerShape(20.dp)).springClick(inter, label = label) { onTap(k) }
                        .semantics { this.selected = sel; role = Role.Tab; contentDescription = if (n > 0) "$label, $n" else label },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
                        Box(Modifier.padding(top = 11.dp).graphicsLayer { scaleX = pop; scaleY = pop }) {
                            Pi(ic, size = 22.dp, tint = col)
                            if (n > 0) Badge(n, Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-7).dp))
                        }
                        Spacer(Modifier.height(9.dp))
                        FitText(label, size = 11, min = 8, color = if (sel) p.ink else p.ink2, weight = if (sel) FontWeight.ExtraBold else FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Bouton rond en verre (barre du haut), avec pastille de compteur facultative. */
@Composable
fun RoundGlass(desc: String, onClick: () -> Unit, badge: Int = 0, size: Dp = 44.dp, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    Box {
        Box(Modifier.bounce(inter, .88f).size(size).shadow((if (p.transp || p.oled) 0.dp else 8.dp).ifShadows(), CircleShape, ambientColor = p.shadow, spotColor = p.shadow)
            .glassBg(CircleShape, strong = true).border(1.dp, if (p.contrast) p.line else p.glassEdge, CircleShape).springClick(inter, label = desc, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = if (badge > 0) "$desc, $badge non lus" else desc; role = Role.Button },
            contentAlignment = Alignment.Center, content = content)
        if (badge > 0) Badge(badge, Modifier.align(Alignment.TopEnd).offset(4.dp, (-2).dp))
    }
}

/** Pastille de la série (barre du haut) : flamme, nombre de jours et gels de série. */
@Composable
fun StreakPill(n: Int, freezes: Int, desc: String, onClick: () -> Unit, bump: Float = 1f) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val pill = RoundedCornerShape(50)
    Row(Modifier.bounce(inter, .9f).height(44.dp).shadow((if (p.transp || p.oled) 0.dp else 8.dp).ifShadows(), pill, ambientColor = p.shadow, spotColor = p.shadow)
        .glassBg(pill, strong = true).border(1.dp, if (p.contrast) p.line else p.glassEdge, pill).springClick(inter, label = desc, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = desc }.padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.graphicsLayer { scaleX = bump; scaleY = bump }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Flame(22.dp); Text("$n", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        if (freezes > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Pi("snow", size = 15.dp, tint = p.type("objective").ink); Text("$freezes", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

/** Titre d'onglet (barre du haut) : grand, sur une ligne, sa taille s'adapte à la place laissée par les boutons. */
@Composable
fun TabTitle(title: String) = FitText(title, Modifier.semantics { heading() }, size = 27, min = 15, color = LocalPal.current.ink, weight = FontWeight.ExtraBold, family = Unbounded)

/**
 * Bouton d'action principal : rectangle arrondi aux couleurs de l'accent, avec son libellé quand on est en haut de la
 * page ; replié en simple « + » quand on fait défiler vers le bas.
 */
@Composable
fun Fab(label: String, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String = "plus") {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Row(modifier.bounce(inter, .9f).height(58.dp).defaultMinSize(minWidth = 58.dp)
        .shadow((if (p.transp) 0.dp else 12.dp).ifShadows(), shape, ambientColor = p.flame.copy(alpha = .5f), spotColor = p.flame.copy(alpha = .6f))
        .clip(shape).background(if (p.contrast) SolidColor(p.ink) else p.accent).springClick(inter, label = label, onClick = onClick).semantics { contentDescription = label }
        .padding(horizontal = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        val fg = if (p.contrast) p.bg else p.onAccent
        Pi(icon, size = 24.dp, tint = fg)
        AnimatedVisibility(expanded, enter = if (p.motion) EnterTransition.None else fadeIn(tween(160)) + expandHorizontally(Motion.standard()),
            exit = if (p.motion) ExitTransition.None else fadeOut(tween(100)) + shrinkHorizontally(Motion.standard())) {
            Text(label, Modifier.padding(start = 9.dp, end = 3.dp), color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
        }
    }
}

/** Bouton « retour » des pages : chevron et nom de la page précédente, dans une pilule tonale. */
@Composable
fun BackPill(label: String, onClick: () -> Unit) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    Row(Modifier.bounce(inter, .92f).height(40.dp).clip(CircleShape).background(ctlFill()).ctlEdgeIfPage(CircleShape).springClick(inter, label = "Retour : $label", role = Role.Button, onClick = onClick)
        .padding(start = 8.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Pi("chevronleft", size = 20.dp, tint = p.ink)
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
    }
}

/** Grand titre d'une page : pastille d'icône aux couleurs de la page et titre. */
@Composable
fun PageTitle(title: String, icon: String?, c1: Color, c2: Color, modifier: Modifier = Modifier) {
    Row(modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (icon != null) IconTile(icon, c1, c2, 50.dp, 16.dp)
        Text(title, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp)
    }
}
