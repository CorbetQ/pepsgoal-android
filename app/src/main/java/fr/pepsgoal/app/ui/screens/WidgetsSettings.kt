package fr.pepsgoal.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.R
import fr.pepsgoal.app.sys.WidgetUpdater
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*

/** Paramètres › Widgets : aperçu de chaque widget, ajout à l'écran d'accueil et apparence. */
@Composable
fun WidgetsSettings(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current
    var theme by remember { mutableStateOf(ui.prefs.widgetTheme) }
    var hide by remember { mutableStateOf(ui.prefs.widgetHideDone) }
    val sysDark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val dark = when (theme) { "light" -> false; "dark" -> true; "you" -> sysDark; else -> p.dark }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Glass {
            Text("Apparence des widgets", fontWeight = FontWeight.ExtraBold)
            Muted("Le style des widgets suit celui de PepsGoal : verre clair ou nuit, flamme et couleurs des types d’objectifs.", Modifier.padding(top = 2.dp, bottom = 10.dp), small = true)
            Seg(listOf("app" to "Comme l’app", "light" to "Clair", "dark" to "Sombre", "you" to "Material You"), theme, { theme = it; ui.prefs.widgetTheme = it; WidgetUpdater.update(ctx); ui.toast("Widgets mis à jour.", "palette") })
            SetRow("Masquer les objectifs validés", "Dans « Ta journée », ne garder que ce qu’il reste à faire") {
                PSwitch(hide, { hide = it; ui.prefs.widgetHideDone = it; WidgetUpdater.update(ctx) })
            }
        }
        WidgetUpdater.ALL.forEach { w ->
            val cls = w[0] as Class<*>; val name = w[1] as String; val desc = w[2] as String; val size = w[3] as String; val kind = w[4] as String
            Glass {
                // Aperçu du widget sur un morceau de fond d'écran
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(p.type("project").c.copy(alpha = .55f), p.type("measure").c.copy(alpha = .45f), p.type("challenge").c.copy(alpha = .45f)))).padding(16.dp), contentAlignment = Alignment.Center) {
                    when (kind) {
                        "today" -> PreviewToday(dark, hide)
                        "streak" -> PreviewStreak(dark)
                        "progress" -> PreviewProgress(dark)
                        else -> PreviewQuick(dark)
                    }
                }
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(name, fontWeight = FontWeight.ExtraBold); Chip(size) }
                        Muted(desc, small = true)
                    }
                    PButton("Ajouter", {
                        if (!WidgetUpdater.pin(ctx, cls)) ui.toast("Appui long sur l’écran d’accueil › Widgets › PepsGoal.", "plus")
                    }, icon = "plus", kind = "primary", small = true)
                }
            }
        }
        Muted("Les widgets se mettent à jour à chaque validation, au changement de jour et toutes les 30 minutes. Tu peux aussi les ajouter avec un appui long sur l’écran d’accueil › Widgets › PepsGoal.", small = true)
    }
}

/* ------------------------------------------------------------------ aperçus (mêmes couleurs que les widgets) */

private class WCol(dark: Boolean) {
    val bg = if (dark) Brush.linearGradient(listOf(Color(0xFF22245A), Color(0xFF15173C), Color(0xFF0B0C24))) else Brush.linearGradient(listOf(Color.White, Color(0xFFF6F2FF), Color(0xFFE9ECFF)))
    val card = if (dark) Color(0x1FFFFFFF) else Color(0xC8FFFFFF)
    val ink = if (dark) Color(0xFFF4F5FF) else Color(0xFF0D0E2B)
    val ink2 = if (dark) Color(0xFFC9CCF2) else Color(0xFF34375F)
    val track = if (dark) Color(0x26FFFFFF) else Color(0x170D0E2B)
    val edge = if (dark) Color(0x33FFFFFF) else Color.White
}

private val FLAME = Brush.linearGradient(listOf(Color(0xFFFFB000), Color(0xFFFF4A12), Color(0xFFFF3D8B)))

@Composable
private fun Frame(dark: Boolean, w: Dp, content: @Composable ColumnScope.(WCol) -> Unit) {
    val c = WCol(dark); val shape = RoundedCornerShape(26.dp)
    Column(Modifier.width(w).shadow(14.dp, shape).clip(shape).background(c.bg).border(1.dp, c.edge, shape).padding(12.dp)) { content(c) }
}

@Composable
private fun PBar(c: WCol, pct: Float, h: Dp) = Box(Modifier.fillMaxWidth().height(h).clip(CircleShape).background(c.track)) { Box(Modifier.fillMaxWidth(pct).fillMaxHeight().clip(CircleShape).background(Color(0xFFFF4A12))) }

@Composable
private fun PreviewToday(dark: Boolean, hide: Boolean) = Frame(dark, 250.dp) { c ->
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_flame), null, Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp)) { Text("11 jours de série", color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp); Text("2/4 validés", color = c.ink2, fontSize = 11.sp) }
        Box(Modifier.size(34.dp).clip(CircleShape).background(FLAME), contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
    }
    Spacer(Modifier.height(8.dp)); PBar(c, .5f, 5.dp); Spacer(Modifier.height(6.dp))
    listOf(Triple("💪", "Faire du sport", "routine"), Triple("🚶", "Marcher 1 h", "duration"), Triple("🍬", "Limiter le sucre", "limit")).forEachIndexed { k, (e, t, ty) ->
        val done = k == 2; if (done && hide) return@forEachIndexed
        val tc = p.type(ty).c
        Row(Modifier.padding(vertical = 2.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(tc))
            Text(e, Modifier.padding(start = 6.dp), fontSize = 14.sp)
            Text(t, Modifier.weight(1f).padding(start = 6.dp), color = if (done) c.ink2 else c.ink, fontSize = 12.sp, fontWeight = if (done) FontWeight.Normal else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.size(24.dp).clip(CircleShape).background(if (done) tc else c.card).border(if (done) 0.dp else 1.5.dp, c.ink2.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) { if (done) Text("✓", color = Color(0xFF10102A), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun PreviewStreak(dark: Boolean) = Frame(dark, 160.dp) { c ->
    Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_flame), null, Modifier.size(34.dp)); Text("11", color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
    }
    Text("jours de série", Modifier.align(Alignment.CenterHorizontally), color = c.ink2, fontSize = 11.sp)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card).padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        WD.forEachIndexed { k, d -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Image(painterResource(if (k == 3) R.drawable.ic_snow else R.drawable.ic_flame), null, Modifier.size(12.dp)); Text(d, color = c.ink2, fontSize = 8.sp) } }
    }
}

@Composable
private fun PreviewProgress(dark: Boolean) = Frame(dark, 230.dp) { c ->
    Row { Text("Aujourd’hui", Modifier.weight(1f), color = c.ink2, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("3/4", color = c.ink2, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
    Text("75 %", color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
    PBar(c, .75f, 7.dp)
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Image(painterResource(R.drawable.ic_flame), null, Modifier.size(14.dp)); Text("Niveau Flamme", Modifier.weight(1f).padding(start = 4.dp), color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold); Text("420", color = c.ink2, fontSize = 11.sp) }
        Spacer(Modifier.height(5.dp)); PBar(c, .3f, 5.dp)
        Text("Encore 280 flammes avant Brasier", Modifier.padding(top = 3.dp), color = c.ink2, fontSize = 10.sp)
    }
}

@Composable
private fun PreviewQuick(dark: Boolean) = Frame(dark, 280.dp) { c ->
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("+" to "Objectif", "☀️" to "Journée", "💬" to "Messages", "📰" to "Feed").forEachIndexed { k, (e, l) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (k == 0) FLAME else Brush.linearGradient(listOf(c.card, c.card))), contentAlignment = Alignment.Center) { Text(e, color = if (k == 0) Color.White else c.ink, fontWeight = FontWeight.ExtraBold, fontSize = if (k == 0) 20.sp else 16.sp) }
                Text(l, Modifier.padding(top = 3.dp), color = c.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
