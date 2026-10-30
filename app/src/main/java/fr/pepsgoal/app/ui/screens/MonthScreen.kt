package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.sys.Exports
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Pal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*

/*
 * Tableau de bord « Mon mois » : sélecteur de mois, chiffres comparés au mois précédent (flèches), carte de chaleur
 * touchable, objectif star, tendances par objectif, meilleur jour et heure moyenne, partage en image. Calculs dans le
 * cœur (MonthReport.kt). Ouvert depuis Statistiques et depuis le récap du mois.
 */

private fun monthLabel(key: String) = cap(monthName(key))

@Composable
fun MonthScreen(ui: AppState, key0: String?) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val ctx = LocalContext.current
    val months = remember(ui.rev) { pg.reportMonths() }
    var key by remember { mutableStateOf(key0?.takeIf { it in months } ?: months.last()) }
    val r = remember(ui.rev, key) { pg.monthReport(key) }
    val idx = months.indexOf(key)
    val tone = p.type("objective").c
    ScreenFrame(ui, "Mon mois", "Retour", { ui.back() }, icon = "cal", color = tone, color2 = p.type("monthly").c, emoji = "🗓️", right = {
        IconBtn("share", "Partager mon mois en image", { Exports.shareImage(ctx, monthBitmap(r, p), "pepsgoal-mois-$key") }, plain = true)
    }) {
        // Sélecteur de mois : précédent / suivant, puis les derniers mois en puces
        item("picker") {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconBtn("chevronleft", "Mois précédent", { if (idx > 0) key = months[idx - 1] }, tint = if (idx > 0) p.ink else p.ink3)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(monthLabel(key), fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                        Muted(if (r.last.isBefore(r.first.withDayOfMonth(r.first.lengthOfMonth()))) "En cours · du 1er au ${r.last.dayOfMonth}" else "Mois complet", small = true)
                    }
                    IconBtn("chevronright", "Mois suivant", { if (idx < months.lastIndex) key = months[idx + 1] }, tint = if (idx < months.lastIndex) p.ink else p.ink3)
                }
                if (months.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    ChipScroller { months.takeLast(12).reversed().forEach { m -> FChip(monthLabel(m), m == key, { key = m }) } }
                }
            }
        }
        // Chiffres clés comparés au mois précédent
        item("kpi") {
            Glass {
                CardHead("En chiffres", emoji = "📊") { Muted("vs ${monthName(ymd(r.first.minusMonths(1)).substring(0, 7))}", small = true) }
                val kpis = listOf(
                    Triple("validations", r.n, r.prevN), Triple("jours actifs", r.active, r.prevActive),
                    Triple("flammes", r.pts, r.prevPts), Triple("journées parfaites", r.perfect, r.prevPerfect),
                )
                kpis.chunked(2).forEach { row ->
                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (l, a, b) -> Kpi(l, a, b, Modifier.weight(1f)) }
                    }
                }
            }
        }
        item("heat") { MonthHeat(r) }
        r.star?.let { s -> item("star") {
            Glass(onClick = { ui.open(Sheet.Goal(s.goal.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    GoalIcon(s.goal, 56.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Eyebrow("Objectif star ⭐")
                        Text(s.goal.title, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2)
                        Muted(listOfNotNull(pl(s.done, "jour validé", "jours validés"), s.rate?.let { "${Math.round(it * 100)} % de réussite" }).joinToString(" · "), small = true)
                    }
                }
            }
        } }
        item("moments") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Glass(Modifier.weight(1f)) {
                    Eyebrow("Meilleur jour")
                    Text(r.bestDay?.let { cap(weekdayLongDate(parseYmd(it.first))) } ?: "—", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Muted(listOfNotNull(r.bestDay?.let { pl(it.second, "validation", "validations") }, r.bestWd?.let { "surtout le ${WD_LONG[it]}" }).joinToString(" · ").ifEmpty { "Pas encore de validation" }, small = true)
                }
                Glass(Modifier.weight(1f)) {
                    Eyebrow("Heure moyenne")
                    Text(r.avgTime ?: "—", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Muted(r.prevAvgTime?.let { "Mois précédent : $it" } ?: "Heure moyenne de tes validations", small = true)
                }
            }
        }
        item("trends") {
            Glass {
                CardHead("Tendances par objectif", emoji = "📈") { Count(r.goals.size) }
                if (r.goals.isEmpty()) Muted("Aucun objectif ce mois-ci.", small = true)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    r.goals.forEach { t -> TrendRow(ui, t) }
                }
                Spacer(Modifier.height(6.dp))
                Muted("↑ en hausse, ↓ en baisse, = stable par rapport au mois précédent.", small = true)
            }
        }
        item("share") { PButton("Partager mon mois en image", { Exports.shareImage(ctx, monthBitmap(r, p), "pepsgoal-mois-$key") }, Modifier.fillMaxWidth(), icon = "image", kind = "primary") }
    }
}

/** Chiffre comparé : valeur, flèche (couleur selon le sens) et écart en pourcentage. */
@Composable
private fun Kpi(label: String, a: Int, b: Int, modifier: Modifier) {
    val p = LocalPal.current; val t = trendOf(a.toDouble(), b.toDouble())
    val c = when (t) { 1 -> p.ok; -1 -> p.danger; else -> p.ink2 }
    Column(modifier.chunk(p.surface2, p.edge, 18.dp, 0.dp, 2.dp).padding(12.dp).semantics(mergeDescendants = true) {
        contentDescription = "$a ${tr(label)}, ${tr(if (t == 1) "en hausse" else if (t == -1) "en baisse" else "stable")}"
    }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$a", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(arrowOf(t), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = c)
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink2)
        Text(pctDelta(a, b) ?: "avant : $b", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = c)
    }
}

/** Carte de chaleur du mois, en calendrier (lundi → dimanche) ; toucher un jour affiche ses validations. */
@Composable
private fun MonthHeat(r: MonthReport) {
    val p = LocalPal.current
    val max = (r.perDay.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val lead = wdIdx(r.first); val len = r.first.lengthOfMonth(); val rows = (lead + len + 6) / 7
    var sel by remember(r.key) { mutableStateOf<Int?>(null) }
    Glass {
        CardHead("Carte de chaleur", emoji = "🔥") { Count(pl(r.active, "jour actif", "jours actifs")) }
        Row(Modifier.fillMaxWidth()) { WD.forEach { Text(it, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2, textAlign = TextAlign.Center) } }
        Spacer(Modifier.height(4.dp))
        val td = today()
        Canvas(Modifier.fillMaxWidth().aspectRatio(7f / rows).pointerInput(r.key) {
            detectTapGestures { o -> val cw = size.width / 7f; val ch = size.height / rows.toFloat()
                val i = (o.y / ch).toInt() * 7 + (o.x / cw).toInt() - lead + 1; sel = i.takeIf { it in 1..len } }
        }.semantics { contentDescription = "${tr("Carte de chaleur")} : ${pl(r.active, "jour actif", "jours actifs")}" }) {
            val cw = size.width / 7f; val ch = size.height / rows.toFloat(); val g = 4.dp.toPx(); val rad = CornerRadius(8.dp.toPx())
            for (d in 1..len) {
                val i = lead + d - 1; val k = ymd(r.first.withDayOfMonth(d))
                val o = Offset((i % 7) * cw + g / 2, (i / 7) * ch + g / 2); val s = Size(cw - g, ch - g)
                val n = r.perDay[k] ?: 0
                drawRoundRect(if (k > td) p.surface else p.heat(heatLevel(n, max)), o, s, rad)
                if (k == td || d == sel) drawRoundRect(if (d == sel) p.focusEdge else p.ink, o, s, rad, style = Stroke(2.dp.toPx()))
            }
        }
        Spacer(Modifier.height(8.dp))
        val s = sel
        if (s != null) { val k = ymd(r.first.withDayOfMonth(s)); Text("${cap(weekdayLongDate(parseYmd(k)))} : ${pl(r.perDay[k] ?: 0, "validation", "validations")}", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Muted("Moins", small = true)
            (0..4).forEach { i -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).chunk(p.heat(i), p.heat(i), 6.dp, 0.dp, 0.dp)) }
            Muted("Plus", small = true)
            Spacer(Modifier.weight(1f)); Muted("Touche un jour", small = true)
        }
    }
}

/** Une ligne de tendance : objectif, réussite (ou validations), flèche par rapport au mois précédent. */
@Composable
private fun TrendRow(ui: AppState, t: GoalTrend) {
    val p = LocalPal.current; val c = when (t.trend) { 1 -> p.ok; -1 -> p.danger; else -> p.ink2 }
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).springClick(inter, label = t.goal.title) { ui.open(Sheet.Goal(t.goal.id)) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GoalIcon(t.goal, 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.goal.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1)
                Text(t.rate?.let { "${Math.round(it * 100)} %" } ?: "${t.done}", fontWeight = FontWeight.ExtraBold)
                Text(" ${arrowOf(t.trend)}", fontWeight = FontWeight.ExtraBold, color = c)
            }
            Bar((t.rate ?: if (t.done > 0) 1.0 else 0.0) * 100, height = 6.dp, color = p.type(t.goal.type).c)
            Muted(listOfNotNull(pl(t.done, "jour validé", "jours validés"), t.prevRate?.let { "avant : ${Math.round(it * 100)} %" } ?: "avant : ${t.prevDone}").joinToString(" · "), small = true)
        }
    }
}

/** Image partageable du mois (format story) : titre, chiffres comparés, calendrier de chaleur, objectif star. */
private fun monthBitmap(r: MonthReport, p: Pal): android.graphics.Bitmap {
    val w = 1080; val h = 1920; val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawPaint(android.graphics.Paint().apply { color = 0xFF170F1C.toInt() })
    val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 70f; isFakeBoldText = true }
    c.drawText(tr("Mon mois PepsGoal"), 80f, 170f, tp)
    tp.textSize = 44f; tp.color = 0xFFFFB000.toInt(); c.drawText(monthLabel(r.key), 80f, 240f, tp)
    // Chiffres
    tp.isFakeBoldText = true
    listOf(Triple("validations", r.n, r.prevN), Triple("jours actifs", r.active, r.prevActive), Triple("flammes", r.pts, r.prevPts), Triple("journées parfaites", r.perfect, r.prevPerfect)).forEachIndexed { i, (l, a, b) ->
        val x = 80f + (i % 2) * 470f; val y = 360f + (i / 2) * 170f; val t = trendOf(a.toDouble(), b.toDouble())
        tp.textSize = 76f; tp.color = android.graphics.Color.WHITE; c.drawText("$a", x, y, tp)
        tp.color = (when (t) { 1 -> Color(0xFF3DDC84); -1 -> Color(0xFFFF6B6B); else -> Color(0xFFD9C4CF) }).toArgb(); c.drawText(arrowOf(t), x + tp.measureText("$a") + 16f, y, tp)
        tp.textSize = 34f; tp.color = 0xFFD9C4CF.toInt(); c.drawText(tr(l), x, y + 48f, tp)
    }
    // Calendrier de chaleur
    val heat = listOf(Color(0xFF33263A), Color(0xFF6A2E2A), Color(0xFFA8401E), Color(0xFFD9481A), Color(0xFFFF4A12))
    val max = (r.perDay.values.maxOrNull() ?: 1).coerceAtLeast(1); val lead = wdIdx(r.first); val len = r.first.lengthOfMonth()
    val left = 80f; val top = 760f; val cell = (w - 160f) / 7f
    tp.textSize = 30f; tp.textAlign = android.graphics.Paint.Align.CENTER
    WD.forEachIndexed { i, d -> c.drawText(d, left + (i + .5f) * cell, top - 20f, tp) }
    for (d in 1..len) {
        val i = lead + d - 1; val k = ymd(r.first.withDayOfMonth(d))
        val col = if (k > today()) Color(0xFF241A2B) else heat[heatLevel(r.perDay[k] ?: 0, max)]
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = col.toArgb() }
        val x = left + (i % 7) * cell; val y = top + (i / 7) * cell
        c.drawRoundRect(x + 6, y + 6, x + cell - 6, y + cell - 6, 18f, 18f, paint)
        tp.color = 0xCCFFFFFF.toInt(); c.drawText("$d", x + cell / 2, y + cell / 2 + 11f, tp)
    }
    // Objectif star et heure moyenne
    tp.textAlign = android.graphics.Paint.Align.LEFT; val y0 = top + 6 * cell + 80f
    r.star?.let { s -> tp.textSize = 36f; tp.color = 0xFFFFB000.toInt(); c.drawText(tr("Objectif star ⭐"), 80f, y0, tp)
        tp.textSize = 48f; tp.color = android.graphics.Color.WHITE; c.drawText("${s.goal.icon} ${s.goal.title}".take(34), 80f, y0 + 64f, tp) }
    r.avgTime?.let { tp.textSize = 34f; tp.color = 0xFFD9C4CF.toInt(); c.drawText("${tr("Heure moyenne")} : $it", 80f, y0 + 130f, tp) }
    tp.textAlign = android.graphics.Paint.Align.CENTER; tp.textSize = 32f; tp.color = 0x99FFFFFF.toInt()
    c.drawText("PepsGoal 🔥", w / 2f, h - 90f, tp)
    return bmp
}

/** Ouvre « Mon mois » sur [key] (« AAAA-MM »), par défaut le mois en cours. */
fun openMonth(ui: AppState, key: String? = null) = ui.push(fr.pepsgoal.app.Screen.Month(key))
