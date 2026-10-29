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
import fr.pepsgoal.app.ui.theme.PLUM

/*
 * Briques des pages de Paramètres « Braise Pop » : en-tête de section illustré (emoji + titre en Unbounded), groupes à
 * marche dont les lignes sont séparées par un trait de 2 dp, lignes de 60 dp avec tuile d'icône néon de 38 dp,
 * interrupteurs dont toute la ligne est touchable.
 */

/** Emoji des en-têtes de groupes, d'après leur titre. */
private val GROUP_EMOJI = mapOf(
    "Personnalité" to "🎭", "Sauvegarde" to "💾", "Modèle" to "🧠", "Fonctions" to "🧰", "Informations légales" to "⚖️", "PepsGoal" to "🔥",
    "Découvrir" to "🧭", "Zone sensible" to "⚠️", "Exports" to "📤", "Synchronisation" to "🔄", "Comptes bloqués" to "🚫", "Découverte" to "🔍",
    "Visibilité" to "👀", "Espace social" to "👥", "Affichage" to "🖼️", "Ville" to "📍", "Messages chiffrés" to "🔐", "Mot de passe" to "🔑",
    "Session" to "📱", "Profil public" to "🪪", "Activités en cours" to "⏳", "Application fermée" to "📲", "Notifications" to "🔔",
    "Rappels quotidiens" to "⏰", "Vibrations et sons" to "📳", "Gestes et prise en main" to "👆", "Lecture" to "📖", "Mémoire de l’IA" to "🧠",
    "Apparence" to "🎨", "Fond et effets" to "🌈", "Mouvement" to "💫", "Compte" to "🔐"
)

/**
 * En-tête d'une catégorie : tuile d'icône néon, titre en Unbounded, état actuel en une phrase, pastilles et contenu
 * libre. Plat (aucune carte) : c'est l'en-tête illustré de la page.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SetHero(icon: String, c1: Color, c2: Color, title: String, sub: String, leading: (@Composable () -> Unit)? = null,
            chips: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit = {}) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (leading != null) leading() else EmojiTileBox(c2, 56.dp) { Pi(icon, size = 28.dp, tint = PLUM) }
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp)
                Text(sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink2)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
        content()
    }
}

/** Groupe de réglages : en-tête de section illustré (emoji + titre), groupe à marche aux lignes séparées, note en dessous. */
@Composable
fun SetGroup(title: String? = null, note: String? = null, emoji: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title != null) SectionTitle(title, emoji ?: GROUP_EMOJI[title])
        Glass(pad = 0.dp, radius = 24.dp) { Divided(content = content) }
        if (note != null) Muted(note, Modifier.padding(horizontal = 8.dp), small = true)
    }
}

/** Colonne dont les éléments sont séparés par un trait de 2 dp (creux), décalé après la tuile d'icône des lignes. */
@Composable
fun Divided(modifier: Modifier = Modifier, inset: androidx.compose.ui.unit.Dp = 66.dp, content: @Composable () -> Unit) {
    val line = LocalPal.current.surface2
    val ys = remember { ArrayList<Int>() }
    Layout(content, modifier.fillMaxWidth().drawBehind {
        val x = inset.toPx(); val w = 2.dp.toPx()
        ys.forEach { y -> drawRect(line, Offset(x, y - w / 2), androidx.compose.ui.geometry.Size(size.width - x, w)) }
    }) { ms, c ->
        val pls = ms.map { it.measure(c.copy(minHeight = 0)) }.filter { it.height > 0 }
        val h = pls.sumOf { it.height }
        layout(c.maxWidth, h) {
            ys.clear(); var y = 0
            pls.forEachIndexed { i, pl -> if (i > 0) ys.add(y); pl.place(0, y); y += pl.height }
        }
    }
}

/**
 * Ligne de réglage (60 dp) : tuile d'icône néon de 38 dp (couleur fixe de l'entrée), titre, explication, commande à
 * droite ; chevron si elle ouvre autre chose.
 */
@Composable
fun SetItem(title: String, sub: String? = null, icon: String? = null, tint: Color? = null, onClick: (() -> Unit)? = null, chevron: Boolean = onClick != null,
            enabled: Boolean = true, danger: Boolean = false, right: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPal.current
    val c = if (danger) p.type("duel").c else tint ?: p.flame
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val half = maxWidth * .52f
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).then(if (onClick != null && enabled) Modifier.springClick(inter, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Tuile néon de la couleur de l'entrée (grisée quand la ligne est inactive)
            if (icon != null) { if (enabled) IconTile(icon, c, c, 38.dp, 12.dp) else Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(p.surface2), contentAlignment = Alignment.Center) { Pi(icon, size = 20.dp, tint = p.ink3) } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = if (danger) p.danger else if (enabled) p.ink else p.ink3)
                if (sub != null) Text(sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink2, lineHeight = 17.sp)
            }
            Row(Modifier.widthIn(max = half), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { right() }
            if (chevron) Pi("chevronright", size = 18.dp, tint = p.ink3)
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
