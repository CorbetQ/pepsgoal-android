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
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.app.ui.theme.PLUM
import fr.pepsgoal.app.ui.theme.BRAISE
import androidx.compose.ui.graphics.SolidColor
import fr.pepsgoal.core.*

/** Paramètres › Widgets : aperçu de chaque widget, ajout à l'écran d'accueil et apparence. */
@Composable
fun WidgetsSettings(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current
    var theme by remember { mutableStateOf(ui.prefs.widgetTheme) }
    var hide by remember { mutableStateOf(ui.prefs.widgetHideDone) }
    val sysDark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val dark = when (theme) { "light" -> false; "dark" -> true; "you" -> sysDark; else -> p.dark }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        CatHero("widgets", "${WidgetUpdater.ALL.size} widgets et 3 tuiles de réglages rapides, aux couleurs de PepsGoal", chips = {
            Chip(when (theme) { "light" -> "Clair"; "dark" -> "Sombre"; "you" -> "Material You"; else -> "Comme l’app" }, "palette", p.type("challenge").c)
            if (hide) Chip("Validés masqués", "eye", p.type("deadline").c)
        })
        Glass {
            Text("Apparence des widgets", fontWeight = FontWeight.ExtraBold)
            Muted("Le style des widgets suit celui de PepsGoal : crème ou prune nuit, cartes à marche, flamme et couleurs des types d’objectifs.", Modifier.padding(top = 2.dp, bottom = 10.dp), small = true)
            Seg(listOf("app" to "Comme l’app", "light" to "Clair", "dark" to "Sombre", "you" to "Material You"), theme, { theme = it; ui.prefs.widgetTheme = it; WidgetUpdater.update(ctx); ui.toast("Widgets mis à jour.", "palette") })
            SetRow("Masquer les objectifs validés", "Dans « Ta journée », ne garder que ce qu’il reste à faire") {
                PSwitch(hide, { hide = it; ui.prefs.widgetHideDone = it; WidgetUpdater.update(ctx) })
            }
        }
        QuickTilesCard(ui)
        WidgetUpdater.ALL.forEach { w ->
            val cls = w[0] as Class<*>; val name = w[1] as String; val desc = w[2] as String; val size = w[3] as String; val kind = w[4] as String
            Glass {
                // Aperçu du widget sur un morceau de fond d'écran
                // Aperçu posé sur un morceau de fond d'écran (aplats)
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.selTint(p.type("project").c)).padding(16.dp), contentAlignment = Alignment.Center) {
                    when (kind) {
                        "today" -> PreviewToday(dark, hide)
                        "streak" -> PreviewStreak(dark)
                        "progress" -> PreviewProgress(dark)
                        "cap" -> PreviewCap(dark)
                        "week" -> PreviewWeek(dark)
                        "flame" -> PreviewFlame(dark)
                        "timeline" -> PreviewTimeline(dark)
                        "quote" -> PreviewQuote(dark)
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
                    }, icon = "plus", small = true)
                }
            }
        }
        Hint("Les widgets se mettent à jour à chaque validation, au changement de jour et toutes les 30 minutes. Tu peux aussi les ajouter avec un appui long sur l’écran d’accueil › Widgets › PepsGoal.")
    }
}

/* ------------------------------------------------------------------ aperçus (mêmes couleurs que les widgets) */

/** Palette Braise Pop des widgets : fond crème ou prune nuit, cartes blanches ou prune à marche, encre prune. */
private class WCol(dark: Boolean) {
    val bg = SolidColor(hex(if (dark) "#170F1C" else "#FFF7F0"))
    val card = hex(if (dark) "#241A2B" else "#FFFFFF")
    val ink = hex(if (dark) "#FFF4EC" else "#2A1420")
    val ink2 = hex(if (dark) "#D9C4CF" else "#6B4F5C")
    val track = hex(if (dark) "#2F2337" else "#FFEDE1")
    val edge = hex(if (dark) "#3A2D43" else "#F2DED1")
}

private val FLAME = Brush.linearGradient(BRAISE)

@Composable
private fun Frame(dark: Boolean, w: Dp, content: @Composable ColumnScope.(WCol) -> Unit) {
    val c = WCol(dark)
    Column(Modifier.width(w).chunk(c.bg, c.edge, 24.dp, 3.dp, 2.dp).padding(12.dp)) { content(c) }
}

/** Carte d'un widget : blanche (ou prune nuit), bord et marche de 3 dp dessinés comme dans le layer-list. */
private fun Modifier.wcard(c: WCol) = this.chunk(c.card, c.edge, 18.dp, 3.dp, 2.dp)

@Composable
private fun PBar(c: WCol, pct: Float, h: Dp) = Box(Modifier.fillMaxWidth().height(h).clip(CircleShape).background(c.track)) { Box(Modifier.fillMaxWidth(pct).fillMaxHeight().clip(CircleShape).background(FLAME)) }

@Composable
private fun PreviewToday(dark: Boolean, hide: Boolean) = Frame(dark, 250.dp) { c ->
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_flame), null, Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp)) { Text("11 jours de série", color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp); Text("2/4 validés", color = c.ink2, fontSize = 12.sp) }
        Box(Modifier.size(34.dp).clip(CircleShape).background(FLAME), contentAlignment = Alignment.Center) { Text("+", color = PLUM, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
    }
    Spacer(Modifier.height(8.dp)); PBar(c, .5f, 5.dp); Spacer(Modifier.height(6.dp))
    listOf(Triple("💪", "Faire du sport", "routine"), Triple("🚶", "Marcher 1 h", "duration"), Triple("🍬", "Limiter le sucre", "limit")).forEachIndexed { k, (e, t, ty) ->
        val done = k == 2; if (done && hide) return@forEachIndexed
        val tc = p.type(ty).c
        Row(Modifier.padding(vertical = 2.dp).fillMaxWidth().wcard(c).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(tc))
            Text(e, Modifier.padding(start = 6.dp), fontSize = 14.sp)
            Text(t, Modifier.weight(1f).padding(start = 6.dp), color = if (done) c.ink2 else c.ink, fontSize = 12.sp, fontWeight = if (done) FontWeight.Medium else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.size(24.dp).clip(CircleShape).background(if (done) tc else c.card).border(if (done) 0.dp else 2.dp, c.ink2, CircleShape), contentAlignment = Alignment.Center) { if (done) Text("✓", color = PLUM, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun PreviewStreak(dark: Boolean) = Frame(dark, 160.dp) { c ->
    Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_flame), null, Modifier.size(34.dp)); Text("11", color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
    }
    Text("jours de série", Modifier.align(Alignment.CenterHorizontally), color = c.ink2, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().wcard(c).padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        WD.forEachIndexed { k, d -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Image(painterResource(if (k == 3) R.drawable.ic_snow else R.drawable.ic_flame), null, Modifier.size(12.dp)); Text(d, color = c.ink2, fontSize = 12.sp) } }
    }
}

@Composable
private fun PreviewFlame(dark: Boolean) = Frame(dark, 76.dp) { c ->
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.ic_flame), null, Modifier.size(26.dp))
        Text("23", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.ink)
    }
}

@Composable
private fun PreviewQuote(dark: Boolean) = Frame(dark, 250.dp) { c ->
    Text("“ Le succès, c’est tomber sept fois, se relever huit. ”", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink)
    Text("— Proverbe japonais", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = c.ink2)
}

@Composable
private fun PreviewWeek(dark: Boolean) = Frame(dark, 280.dp) { c ->
    Text("Ma semaine", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(1f, 1f, .6f, 1f, .3f, 0f, 0f).forEachIndexed { i, v ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
                    drawCircle(c.track)
                    if (v > 0f) drawArc(if (v >= 1f) BRAISE[1] else BRAISE[0], -90f, 360f * v, true)
                    if (i == 4) drawCircle(c.ink, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
                Text(fr.pepsgoal.core.WD[i], fontSize = 12.sp, color = c.ink2)
            }
        }
    }
}

@Composable
private fun PreviewTimeline(dark: Boolean) = Frame(dark, 280.dp) { c ->
    Text("Ta journée heure par heure", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(70.dp).padding(top = 6.dp)) {
        val w = maxWidth
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val y = size.height - 6.dp.toPx()
            drawLine(c.track, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 4.dp.toPx())
            val nx = size.width * .45f
            drawLine(BRAISE[1], androidx.compose.ui.geometry.Offset(nx, 0f), androidx.compose.ui.geometry.Offset(nx, y), 2.dp.toPx())
        }
        listOf(Triple("🧘", .08f, 0), Triple("🏃", .2f, 1), Triple("📚", .55f, 0), Triple("💧", .7f, 1), Triple("🛏️", .9f, 0)).forEachIndexed { i, (e, f, lane) ->
            Box(Modifier.offset(x = w * f - 13.dp, y = (4 + lane * 26).dp).size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (i < 2) BRAISE[0] else c.track), contentAlignment = Alignment.Center) { Text(e, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun PreviewCap(dark: Boolean) = Frame(dark, 150.dp) { c ->
    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(92.dp)) {
            val sw = 10.dp.toPx()
            drawArc(c.track, 0f, 360f, false, androidx.compose.ui.geometry.Offset(sw / 2, sw / 2), androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            drawArc(hex("#00D1B2"), -90f, 360f * .72f, false, androidx.compose.ui.geometry.Offset(sw / 2, sw / 2), androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw), style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("👟", fontSize = 14.sp); Text("7 200", color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp); Text("/ 10 000 pas", color = c.ink2, fontSize = 12.sp) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) { Text("10 000 pas", Modifier.weight(1f), color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold); Box(Modifier.size(28.dp).clip(CircleShape).background(FLAME), contentAlignment = Alignment.Center) { Text("+", color = PLUM, fontWeight = FontWeight.ExtraBold) } }
}

@Composable
private fun PreviewProgress(dark: Boolean) = Frame(dark, 230.dp) { c ->
    Row { Text("Aujourd’hui", Modifier.weight(1f), color = c.ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text("3/4", color = c.ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    Text("75 %", color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
    PBar(c, .75f, 7.dp)
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().wcard(c).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Image(painterResource(R.drawable.ic_flame), null, Modifier.size(14.dp)); Text("Niveau Flamme", Modifier.weight(1f).padding(start = 4.dp), color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold); Text("420", color = c.ink2, fontSize = 12.sp) }
        Spacer(Modifier.height(5.dp)); PBar(c, .3f, 5.dp)
        Text("Encore 280 flammes avant Brasier", Modifier.padding(top = 3.dp), color = c.ink2, fontSize = 12.sp)
    }
}

@Composable
private fun PreviewQuick(dark: Boolean) = Frame(dark, 280.dp) { c ->
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("+" to "Objectif", "☀️" to "Journée", "💬" to "Messages", "📰" to "Feed").forEachIndexed { k, (e, l) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (k == 0) FLAME else SolidColor(c.card)).border(2.dp, if (k == 0) Color.Transparent else c.edge, CircleShape), contentAlignment = Alignment.Center) { Text(e, color = if (k == 0) PLUM else c.ink, fontWeight = FontWeight.ExtraBold, fontSize = if (k == 0) 20.sp else 16.sp) }
                Text(l, Modifier.padding(top = 3.dp), color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Tuiles des réglages rapides (volet de notifications) : valider l'objectif suivant, compléter son cap, lancer un focus. */
@Composable
private fun QuickTilesCard(ui: AppState) {
    val ctx = LocalContext.current
    val tiles = listOf(
        Triple(fr.pepsgoal.app.sys.ValidateTile::class.java, "Valider", "Valide l’objectif suivant d’un geste (ou l’ouvre s’il demande une saisie)."),
        Triple(fr.pepsgoal.app.sys.CapTile::class.java, "Cap quotidien", "Ajoute la quantité habituelle à ton cap du jour (pas, verres d’eau…)."),
        Triple(fr.pepsgoal.app.sys.FocusTile::class.java, "Focus", "Lance ou arrête une session de 25 minutes.")
    )
    Glass {
        Text("Réglages rapides", fontWeight = FontWeight.ExtraBold)
        Muted("Des tuiles PepsGoal dans le volet de notifications, sans ouvrir l’application.", Modifier.padding(top = 2.dp, bottom = 8.dp), small = true)
        tiles.forEach { (cls, name, desc) ->
            SetRow(name, desc) {
                if (android.os.Build.VERSION.SDK_INT >= 33) PButton("Ajouter", {
                    val sbm = ctx.getSystemService(android.app.StatusBarManager::class.java)
                    try {
                        sbm.requestAddTileService(android.content.ComponentName(ctx, cls), "$name (PepsGoal)", android.graphics.drawable.Icon.createWithResource(ctx, fr.pepsgoal.app.R.drawable.ic_notif), ctx.mainExecutor) { r ->
                            if (r == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) ui.toast("Tuile « $name » ajoutée.", "check")
                            else if (r == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) ui.toast("Cette tuile est déjà dans tes réglages rapides.", "check")
                        }
                    } catch (e: Exception) { ui.toast("Ajoute la tuile depuis le crayon des réglages rapides.", "x") }
                }, small = true)
            }
        }
        if (android.os.Build.VERSION.SDK_INT < 33) Muted("Ouvre les réglages rapides, touche le crayon et fais glisser les tuiles PepsGoal.", small = true)
    }
}
