package fr.pepsgoal.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded

/*
 * Briques des pages de Paramètres : en-tête d'état (ce qui est actif, en un coup d'œil), groupes titrés dont les
 * lignes sont séparées par un trait fin, lignes avec icône colorée, interrupteurs dont toute la ligne est touchable.
 */

/** En-tête d'une catégorie : grande icône, titre, état actuel en une phrase, pastilles et contenu libre. */
@Composable
fun SetHero(icon: String, c1: Color, c2: Color, title: String, sub: String, leading: (@Composable () -> Unit)? = null,
            chips: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit = {}) {
    val p = LocalPal.current
    Glass(pad = 0.dp) {
        Box(Modifier.fillMaxWidth()) {
            // Lavis aux couleurs de la catégorie
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.linearGradient(listOf(c1.copy(alpha = if (p.dark) .2f else .16f), c2.copy(alpha = .06f), Color.Transparent), Offset.Zero, Offset(size.width, size.height)))
            }
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (leading != null) leading() else IconTile(icon, c1, c2, 52.dp, 17.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 23.sp)
                        Text(sub, fontSize = 14.sp, color = p.ink2, lineHeight = 18.sp)
                    }
                }
                FlowRowCompat(gap = 6.dp) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() } }
                content()
            }
        }
    }
}

/** Groupe de réglages : petit titre au-dessus, carte aux lignes séparées, note en dessous. */
@Composable
fun SetGroup(title: String? = null, note: String? = null, content: @Composable () -> Unit) {
    val p = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, Modifier.padding(start = 6.dp).semantics { heading() }, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
        Glass(pad = 4.dp, radius = 24.dp) { Divided(content = content) }
        if (note != null) Muted(note, Modifier.padding(horizontal = 8.dp), small = true)
    }
}

/** Colonne dont les éléments sont séparés par un trait fin (décalé après l'icône des lignes). */
@Composable
fun Divided(modifier: Modifier = Modifier, inset: androidx.compose.ui.unit.Dp = 60.dp, content: @Composable () -> Unit) {
    val line = LocalPal.current.line.let { it.copy(alpha = it.alpha * .7f) }
    val ys = remember { ArrayList<Int>() }
    Layout(content, modifier.fillMaxWidth().drawBehind {
        val x = inset.toPx(); val w = 1.dp.toPx()
        ys.forEach { y -> drawRect(line, Offset(x, y - w / 2), androidx.compose.ui.geometry.Size(size.width - x - 12.dp.toPx(), w)) }
    }) { ms, c ->
        val pls = ms.map { it.measure(c.copy(minHeight = 0)) }.filter { it.height > 0 }
        val h = pls.sumOf { it.height }
        layout(c.maxWidth, h) {
            ys.clear(); var y = 0
            pls.forEachIndexed { i, pl -> if (i > 0) ys.add(y); pl.place(0, y); y += pl.height }
        }
    }
}

/** Ligne de réglage : icône colorée, titre, explication, commande à droite ; chevron si elle ouvre autre chose. */
@Composable
fun SetItem(title: String, sub: String? = null, icon: String? = null, tint: Color? = null, onClick: (() -> Unit)? = null, chevron: Boolean = onClick != null,
            enabled: Boolean = true, danger: Boolean = false, right: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPal.current
    val c = if (danger) p.danger else tint ?: p.flame
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val half = maxWidth * .52f
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).then(if (onClick != null && enabled) Modifier.bounce(inter, .985f).clip(RoundedCornerShape(20.dp)).springClick(inter, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (icon != null) Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = if (p.dark) .2f else .14f)), contentAlignment = Alignment.Center) { Pi(icon, size = 20.dp, tint = if (enabled) fr.pepsgoal.app.ui.theme.readable(c, p.surface, 3f) else p.ink3) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (danger) p.danger else if (enabled) p.ink else p.ink3)
                if (sub != null) Text(sub, fontSize = 13.sp, color = p.ink2, lineHeight = 17.sp)
            }
            Row(Modifier.widthIn(max = half), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { right() }
            if (chevron) Chevron()
        }
    }
}

/** Interrupteur : toute la ligne est touchable. */
@Composable
fun SetToggle(title: String, sub: String? = null, icon: String? = null, tint: Color? = null, on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) =
    SetItem(title, sub, icon, tint, onClick = if (enabled) ({ onChange(!on) }) else null, chevron = false, enabled = enabled) {
        PSwitch(on, onChange, enabled)
    }

/** Contenu libre dans un groupe (champs, puces…), avec les marges des lignes. */
@Composable
fun SetBlock(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
