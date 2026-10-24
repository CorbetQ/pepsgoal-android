package fr.pepsgoal.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.model.Goal
import fr.pepsgoal.core.shownIcon
import kotlinx.coroutines.launch

/* Lignes d'objectif et leurs commandes : pastille d'icône, bouton de validation, petit bouton d'action. */

@Composable
fun GoalIcon(g: Goal, size: Dp = 44.dp, masked: Boolean = false) {
    val p = LocalPal.current; val tc = p.type(g.type)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .32f)).background(tc.c.copy(alpha = if (p.dark) .22f else .18f)), contentAlignment = Alignment.Center) { Emoji(if (masked) g.shownIcon() else g.icon, size * .56f) }
}

@Composable
fun CheckButton(done: Boolean, just: Boolean, label: String, color: Color, disabled: Boolean = false, icon: String = "check", running: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    val pop = remember { Animatable(1f) }
    // Coche dessinée au trait et onde de choc qui s'élargit autour du bouton au moment de la validation
    val draw = remember { Animatable(if (done) 1f else 0f) }
    val wave = remember { Animatable(1f) }
    LaunchedEffect(just, done) {
        if (just && done && !p.motion) {
            draw.snapTo(0f); wave.snapTo(0f)
            launch { pop.snapTo(.72f); pop.animateTo(1f, spring(dampingRatio = .38f, stiffness = 520f)) }
            launch { wave.animateTo(1f, tween(520, easing = Motion.Emphasized)) }
            kotlinx.coroutines.delay(70); draw.animateTo(1f, tween(300, easing = Motion.Emphasized))
        } else { draw.snapTo(if (done) 1f else 0f); wave.snapTo(1f) }
    }
    val filled = done || running
    // Couleurs du type lisibles dans les deux thèmes : foncées sur fond clair, vives sur fond sombre
    val line = readable(color)
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // Cercle à cocher : simple anneau tant que ce n'est pas fait, pastille pleine une fois validé
    Box(
        Modifier.drawBehind {
            val w = wave.value
            if (w < 1f) drawCircle(color.copy(alpha = (1f - w) * .55f), size.minDimension / 2f * (1f + w * .75f), style = androidx.compose.ui.graphics.drawscope.Stroke((1f - w) * 4.dp.toPx() + 1f))
        }.bounce(inter, .82f).scale(pop.value).size(44.dp).clip(CircleShape).background(if (filled) color else Color.Transparent)
            .border(2.dp, if (disabled) p.ink3.copy(alpha = .4f) else if (filled) Color.Transparent else line.copy(alpha = .75f), CircleShape)
            .springClick(inter, label = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (done && !running && icon == "check") DrawnCheck(draw.value, Color(0xFF10102A), 24.dp, 3.dp)
        else Pi(icon, size = 20.dp, tint = if (filled) Color(0xFF10102A) else if (disabled) p.ink3.copy(alpha = .55f) else line)
    }
}

/** Couleur d'accent assombrie en thème clair (contraste suffisant sur le verre), inchangée en thème sombre. */
@Composable
fun readable(c: Color): Color {
    val p = LocalPal.current
    if (p.dark) return c
    return androidx.compose.ui.graphics.lerp(c, Color(0xFF10102A), if (p.contrast) .7f else .42f)
}

/** Petit bouton d'action d'une ligne (« + Progrès », « Ouvrir »…), teinté de la couleur du type. */
@Composable
fun MiniBtn(text: String, color: Color, onClick: () -> Unit) {
    val p = LocalPal.current
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(Modifier.bounce(inter, .9f).heightIn(min = 38.dp).clip(CircleShape).background(color.copy(alpha = if (p.dark) .24f else .2f)).springClick(inter, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = p.ink, maxLines = 1)
    }
}

/**
 * Mise en page d'une ligne d'objectif : pastille d'icône, titre, ligne d'état (type, progression, échéance), barre de
 * progression facultative et commande à droite. Ligne validée : fond teinté de la couleur du type.
 */
@Composable
fun GoalRowLayout(
    icon: @Composable () -> Unit, title: String, typeLabel: String, typeColor: Color, meta: String, done: Boolean,
    modifier: Modifier = Modifier, urgent: Boolean = false, bar: Double? = null, star: Boolean = false, easyState: String? = null,
    extra: (@Composable () -> Unit)? = null, right: @Composable () -> Unit
) {
    val p = LocalPal.current
    Row(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        icon()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (star) Pi("star", size = 14.dp, tint = p.flame2)
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = p.ink, maxLines = if (p.easy) 3 else 1, overflow = TextOverflow.Ellipsis)
            }
            // Type (pastille de couleur), puis l'état : une seule ligne de texte, sans puces
            FlowRowCompat(gap = 6.dp) {
                if (easyState != null) Chip(easyState, if (done) "check" else "clock", if (done) p.ok else p.ink3)
                // Le type puis l'état dans un seul texte : il passe à la ligne naturellement
                Text(androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = typeColor, fontSize = 15.sp)) { append("● ") }
                    withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = p.ink2)) { append(tr(typeLabel)) }
                    if (meta.isNotBlank()) withStyle(androidx.compose.ui.text.SpanStyle(color = if (urgent) p.danger else p.ink2, fontWeight = if (urgent) FontWeight.Bold else FontWeight.Medium)) { append(" · " + tr(meta)) }
                }, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                extra?.invoke()
            }
            bar?.let { Bar(it, Modifier.padding(top = 4.dp), typeColor, 5.dp) }
        }
        right()
    }
}
