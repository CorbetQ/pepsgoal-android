package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*
import java.time.LocalDate

/**
 * Statistiques avancées : carte de chaleur de l'année, réussite par jour de la semaine et par heure, records,
 * comparaison avec le mois précédent et taux de réussite de chaque objectif.
 */
@Composable
fun StatsScreen(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val wins = remember(ui.rev) { pg.userCheckins(u.id).filter { pg.isWin(it) } }
    val perDay = remember(wins) { wins.groupingBy { it.date }.eachCount() }
    val today = todayDate()
    ScreenFrame(ui, "Statistiques", "Retour", { ui.back() }, icon = "trend", color = p.type("project").c, color2 = p.type("objective").c) {
        // Records et chiffres clés
        item("records") {
            val best = perDay.maxByOrNull { it.value }
            val m0 = today.withDayOfMonth(1); val m1 = m0.minusMonths(1)
            val thisMonth = wins.count { parseYmd(it.date) >= m0 }; val lastMonth = wins.count { val d = parseYmd(it.date); d >= m1 && d < m0 }
            StatGrid(listOf(
                "${wins.size}" to "validations au total", "${pg.bestStreak(u.id)}" to "meilleure série (jours)",
                "${perDay.size}" to "jours actifs", (best?.let { "${it.value}" } ?: "0") to (best?.let { "record le ${fmtDate(it.key)}" } ?: "meilleure journée"),
                "$thisMonth" to "ce mois-ci", (if (lastMonth == 0) "—" else "${if (thisMonth >= lastMonth) "+" else ""}${Math.round((thisMonth - lastMonth) * 100.0 / lastMonth)} %") to "vs mois dernier",
            ))
        }
        item("health") { HealthStatsCard(ui) }
        item("insights") { InsightsCard(ui) }
        item("pixels") { YearPixels(ui) }
        item("hourheat") { fr.pepsgoal.app.ui.sheets.HourHeatCard(ui) }
        // Raisons notées les jours manqués (« Je n'ai pas pu ») — calcul mémorisé dans l'élément (contexte Compose)
        item("why") {
            val why = remember(ui.rev) { pg.missStats() }
            if (why.isNotEmpty()) Glass {
                CardHead("Pourquoi ça coince")
                val tot = why.sumOf { it.second }
                why.take(5).forEach { (k, n) ->
                    ProgLine(MISS_REASONS.firstOrNull { it.first == k }?.second ?: k, n * 100.0 / tot, "$n", p.type("deadline").c)
                }
                Muted(when (why.first().first) {
                    "fatigue" -> "La fatigue revient souvent : un objectif plus tôt dans la journée, ou une version plus légère, peut aider."
                    "temps" -> "Le manque de temps domine : bloque un créneau dans ton agenda depuis la fiche de l’objectif."
                    "oubli" -> "Surtout des oublis : un rappel à heure fixe ou un enchaînement après une habitude existante peut aider."
                    "motivation" -> "La motivation flanche parfois : une récompense personnelle ou un duo avec un ami peut relancer."
                    else -> "Noter la raison d’un oubli aide à repérer ce qui coince, sans culpabiliser."
                }, Modifier.padding(top = 8.dp), small = true)
            }
        }
        item("export") { Glass { ExportRow(ui) } }
        // Carte de chaleur des 53 dernières semaines
        item("heat") {
            Glass {
                CardHead("Ton année") { Count("${perDay.filterKeys { parseYmd(it) > today.minusDays(365) }.size} jours actifs") }
                val max = (perDay.values.maxOrNull() ?: 1).coerceAtLeast(1)
                // 371 cases dessinées d'un seul Canvas (et non 371 boîtes découpées, chacune avec son calque)
                val cells = remember(perDay, today) { val start = weekStart(today).minusWeeks(52); IntArray(53 * 7) { i -> val day = start.plusDays(i.toLong()); if (day > today) -1 else perDay[ymd(day)] ?: 0 } }
                val empty = p.ink.copy(alpha = .07f); val hot = p.flame
                Row(Modifier.horizontalScroll(rememberScrollState(Int.MAX_VALUE))) {
                    Canvas(Modifier.size(width = 13.dp * 53 + 3.dp * 52, height = 13.dp * 7 + 3.dp * 6)) {
                        val c = 13.dp.toPx(); val g = 3.dp.toPx(); val r = CornerRadius(3.dp.toPx())
                        for (i in cells.indices) {
                            val n = cells[i]; if (n < 0) continue
                            drawRoundRect(if (n == 0) empty else hot.copy(alpha = .25f + .75f * n / max), Offset((i / 7) * (c + g), (i % 7) * (c + g)), Size(c, c), r)
                        }
                    }
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Muted("Moins", small = true)
                    listOf(.07f, .3f, .55f, .8f, 1f).forEachIndexed { i, a -> Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(if (i == 0) p.ink.copy(alpha = a) else p.flame.copy(alpha = a))) }
                    Muted("Plus", small = true)
                }
            }
        }
        // Ton meilleur moment : réussite par jour de la semaine, créneau, heures habituelles
        item("moments") { MomentsCard(ui) }
        // Réussite de chaque objectif (30 derniers jours)
        item("goals") {
            Glass {
                CardHead("Tes objectifs (30 jours)")
                // Calcul (30 jours × objectifs) fait une fois par changement de données, pas à chaque recomposition
                val rows = remember(wins, today) {
                    val since = today.minusDays(30)
                    pg.myGoals(u.id).filter { !pg.isClosedGoal(it) }.map { g ->
                        var planned = 0; var d = maxOf(since, parseYmd(ymd(g.createdAt)))
                        while (d <= today) { if (pg.scheduled(g, d)) planned++; d = d.plusDays(1) }
                        val done = wins.count { it.goalId == g.id && parseYmd(it.date) > since }
                        Triple(g, done, planned)
                    }.sortedByDescending { if (it.third > 0) it.second.toDouble() / it.third else 0.0 }
                }
                if (rows.isEmpty()) Muted("Aucun objectif en cours.", small = true)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { (g, done, planned) ->
                        val pct = if (planned > 0) (done.toDouble() / planned).coerceAtMost(1.0) else 0.0
                        Row(Modifier.clip(RoundedCornerShape(12.dp)).springClick(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { ui.open(Sheet.Goal(g.id)) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GoalIcon(g, 34.dp)
                            Column(Modifier.weight(1f)) {
                                Row { Text(g.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1); Text(if (planned > 0) "${Math.round(pct * 100)} %" else "$done", fontWeight = FontWeight.ExtraBold) }
                                Bar(pct * 100, height = 6.dp, color = p.type(g.type).c)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val WD_LONG = listOf("le lundi", "le mardi", "le mercredi", "le jeudi", "le vendredi", "le samedi", "le dimanche")

/** Histogramme simple : barres arrondies, la plus haute en couleur flamme. */
@Composable
@Suppress("unused")
private fun Bars(values: List<Int>, labels: List<String>, hi: Int, height: Int = 110) {
    val p = LocalPal.current; val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    Column {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            val n = values.size; val gap = size.width / n; val bw = gap * .62f
            values.forEachIndexed { i, v ->
                val h = (size.height * v / max).coerceAtLeast(3f)
                drawRoundRect(if (i == hi) p.flame else p.ink.copy(alpha = .18f), Offset(i * gap + (gap - bw) / 2, size.height - h), Size(bw, h), CornerRadius(bw / 3))
            }
        }
        Row(Modifier.fillMaxWidth()) { labels.forEach { Text(it, Modifier.weight(1f), fontSize = 10.sp, color = p.ink3, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1) } }
    }
}

/** Santé sur 7 jours (Health Connect) : valeur du jour, moyenne et mini-histogramme pour chaque mesure disponible. */
@Composable
private fun HealthStatsCard(ui: AppState) {
    val ctx = androidx.compose.ui.platform.LocalContext.current; val p = LocalPal.current
    var data by remember { mutableStateOf<Map<String, List<Double?>>?>(null) }
    var ok by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        ok = fr.pepsgoal.app.sys.Health.available(ctx) && fr.pepsgoal.app.sys.Health.granted(ctx).containsAll(fr.pepsgoal.app.sys.Health.PERMS)
        if (ok) data = fr.pepsgoal.app.sys.Health.METRICS.associate { it.key to fr.pepsgoal.app.sys.Health.series(ctx, it.key, 7) }.filterValues { l -> l.any { it != null && it > 0 } }
    }
    if (!ok) {
        Glass(onClick = { ui.push(fr.pepsgoal.app.Screen.Settings("health")) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pi("heart", size = 20.dp, tint = p.flameInk); Column(Modifier.weight(1f)) { Text("Ajoute tes données Santé", fontWeight = FontWeight.ExtraBold); Muted("Pas, distances, sommeil, coucher, réveil, poids… avec Health Connect.", small = true) }; Chevron()
            }
        }
        return
    }
    Glass {
        CardHead("Santé · 7 derniers jours")
        val d = data
        if (d == null) Muted("Lecture de tes données Santé…", small = true)
        else if (d.isEmpty()) Muted("Aucune donnée Santé sur les 7 derniers jours.", small = true)
        else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            d.forEach { (k, vals) ->
                val m = fr.pepsgoal.app.sys.Health.metric(k) ?: return@forEach
                val nn = vals.filterNotNull().filter { it > 0 }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Emoji(m.emoji, 26.dp)
                    Column(Modifier.weight(1f)) {
                        Text(m.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Muted("Aujourd’hui ${fr.pepsgoal.app.sys.Health.format(k, vals.last())} · moy. ${fr.pepsgoal.app.sys.Health.format(k, nn.average().takeIf { !it.isNaN() })}", small = true, maxLines = 1)
                    }
                    // Mini-histogramme (les heures et le poids sont tracés entre leur minimum et leur maximum)
                    val lo = if (m.kind == "time" || k == "weight") (nn.minOrNull() ?: 0.0) * .98 else 0.0
                    val hi = (nn.maxOrNull() ?: 1.0).coerceAtLeast(lo + .01)
                    Canvas(Modifier.width(84.dp).height(30.dp)) {
                        val gap = size.width / 7; val bw = gap * .6f
                        vals.forEachIndexed { i, v ->
                            val h = if (v == null || v <= 0) 2f else (size.height * ((v - lo) / (hi - lo)).toFloat()).coerceIn(3f, size.height)
                            drawRoundRect(if (i == vals.lastIndex) p.flame else p.ink.copy(alpha = .22f), Offset(i * gap + (gap - bw) / 2, size.height - h), Size(bw, h), CornerRadius(bw / 3))
                        }
                    }
                }
            }
        }
    }
}

/** Résultat des corrélations, gardé pour la journée (le calcul lit 60 jours de Santé). */
private var insightsCache: Pair<String, List<Insights.Finding>>? = null

/** Corrélations : sommeil, pas, sport, humeur et jour de la semaine face à la réussite du jour. */
@Composable
private fun InsightsCard(ui: AppState) {
    val ctx = androidx.compose.ui.platform.LocalContext.current; val p = LocalPal.current; val pg = ui.pg
    var found by remember { mutableStateOf(insightsCache?.takeIf { it.first == today() }?.second) }
    LaunchedEffect(Unit) {
        if (found != null) return@LaunchedEffect
        // Taux calculés sur le fil principal (la base n'est pas partagée entre fils), le reste en arrière-plan
        val rates = Insights.dayRates(pg, 60)
        found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val values = HashMap<String, Map<String, Double>>()
            values["mood"] = fr.pepsgoal.app.data.Journal.all(ctx).associate { it.date to it.mood.toDouble() }
            val H = fr.pepsgoal.app.sys.Health
            if (H.available(ctx) && H.granted(ctx).isNotEmpty()) for (k in listOf("sleep", "steps", "exercise")) {
                val m = HashMap<String, Double>()
                for (d in rates.keys) try { H.value(ctx, k, parseYmd(d))?.let { m[d] = it } } catch (e: Exception) {}
                if (m.isNotEmpty()) values[k] = m
            }
            Insights.findings(rates, values, fr.pepsgoal.app.data.Journal.tagDays(ctx)).take(5)
        }
        insightsCache = today() to (found ?: emptyList())
    }
    Glass {
        CardHead("Ce qui t’aide à réussir")
        val f = found
        when {
            f == null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(2) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Skeleton(Modifier.width(36.dp), 36.dp, 12.dp); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Skeleton(Modifier.fillMaxWidth(), 12.dp); Skeleton(Modifier.fillMaxWidth(.6f), 12.dp) } } } }
            f.isEmpty() -> Muted("Pas encore de tendance nette. Continue à valider, à noter ton humeur et, si tu veux, relie Health Connect : les liens avec ton sommeil, tes pas et ton sport apparaîtront ici.", small = true)
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                f.forEach { x ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(x.icon, if (x.diff >= 0) p.ok else p.flame2, if (x.diff >= 0) p.type("routine").c else p.pink, 36.dp, 12.dp)
                        Text(x.text, Modifier.weight(1f), fontSize = 14.sp)
                    }
                }
                Muted("Des tendances, pas des certitudes : calculées sur tes données, sur ce téléphone.", small = true)
            }
        }
    }
}

/** Couleur d'un jour selon sa réussite : du rouge doux (rien) au vert (tout), gris s'il n'y avait rien de prévu. */
private fun pixelColor(r: Double?, p: fr.pepsgoal.app.ui.theme.Pal): androidx.compose.ui.graphics.Color = when {
    r == null -> p.ink.copy(alpha = .06f)
    r <= 0.0 -> p.danger.copy(alpha = .45f)
    r < .5 -> androidx.compose.ui.graphics.lerp(p.danger, p.flame2, (r * 2).toFloat()).copy(alpha = .8f)
    else -> androidx.compose.ui.graphics.lerp(p.flame2, p.ok, ((r - .5) * 2).toFloat())
}

/** Année en pixels : un carré par jour (colonnes = mois), coloré selon la part d'objectifs validés ; à partager en image. */
@Composable
private fun YearPixels(ui: AppState) {
    val p = LocalPal.current; val ctx = androidx.compose.ui.platform.LocalContext.current; val pg = ui.pg
    val year = todayDate().year
    val rates = remember(ui.rev) { val d0 = LocalDate.of(year, 1, 1); Insights.dayRates(pg, daysBetween(d0, todayDate()).toInt() + 1) }
    val withToday = remember(rates) { val t = todayDate(); val due = pg.todayPlan().due; if (due.isEmpty()) rates else rates + (ymd(t) to due.count { it.done }.toDouble() / due.size) }
    Glass {
        CardHead("Année en pixels") {
            // Livre de l'année : PDF complet (chiffres, mois, objectifs phares, journal, photos)
            PButton("Livre", { ui.run {
                val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fr.pepsgoal.app.sys.Exports.yearBook(ctx, year) }
                if (f != null) fr.pepsgoal.app.sys.Exports.share(ctx, listOf(f), "application/pdf")
            } }, icon = "book", small = true, kind = "ghost")
            PButton("Partager", {
                val bmp = yearBitmap(year, withToday, p)
                fr.pepsgoal.app.sys.Exports.shareImage(ctx, bmp, "pepsgoal-annee-$year")
            }, icon = "share", small = true)
        }
        Muted("$year · chaque carré est un jour, chaque colonne un mois", small = true)
        Spacer(Modifier.height(10.dp))
        val td = today()
        Canvas(Modifier.fillMaxWidth().aspectRatio(12f / 31f * 1.25f)) {
            val cw = size.width / 12f; val ch = size.height / 31f; val gap = 2.dp.toPx()
            for (m in 1..12) {
                val len = LocalDate.of(year, m, 1).lengthOfMonth()
                for (d in 1..len) {
                    val k = ymd(LocalDate.of(year, m, d))
                    val c = if (k > td) p.ink.copy(alpha = .03f) else pixelColor(withToday[k], p)
                    drawRoundRect(c, Offset((m - 1) * cw + gap / 2, (d - 1) * ch + gap / 2), Size(cw - gap, ch - gap), CornerRadius(3.dp.toPx()))
                    if (k == td) drawRoundRect(p.ink, Offset((m - 1) * cw + gap / 2, (d - 1) * ch + gap / 2), Size(cw - gap, ch - gap), CornerRadius(3.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Muted("Rien", small = true)
            listOf(0.0, .3, .6, .85, 1.0).forEach { r -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(pixelColor(r, p))) }
            Muted("Tout validé", small = true)
        }
    }
}

/** Image partageable de l'année en pixels (fond sombre, titre, grille, légende). */
private fun yearBitmap(year: Int, rates: Map<String, Double>, p: fr.pepsgoal.app.ui.theme.Pal): android.graphics.Bitmap {
    val w = 1080; val h = 1920; val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawPaint(android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF141638.toInt(), 0xFF07081A.toInt(), android.graphics.Shader.TileMode.CLAMP) })
    val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 64f; isFakeBoldText = true }
    c.drawText(tr("Mon année PepsGoal") + " $year", 80f, 170f, tp)
    tp.textSize = 34f; tp.isFakeBoldText = false; tp.color = 0xBBFFFFFF.toInt()
    val done = rates.values.count { it >= 1.0 }
    c.drawText(tr("Journées parfaites") + " : $done", 80f, 230f, tp)
    val left = 110f; val top = 300f; val cw = (w - left - 60f) / 12f; val ch = (h - top - 200f) / 31f
    val dark = p.copy(ink = androidx.compose.ui.graphics.Color.White)
    val months = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    tp.textSize = 30f; tp.textAlign = android.graphics.Paint.Align.CENTER
    for (m in 1..12) {
        c.drawText(months[m - 1], left + (m - 0.5f) * cw, top - 16f, tp)
        val len = LocalDate.of(year, m, 1).lengthOfMonth()
        for (d in 1..len) {
            val k = ymd(LocalDate.of(year, m, d))
            val col = if (k > today()) dark.ink.copy(alpha = .05f) else pixelColor(rates[k], dark)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = col.toArgb() }
            c.drawRoundRect(left + (m - 1) * cw + 4, top + (d - 1) * ch + 4, left + m * cw - 4, top + d * ch - 4, 8f, 8f, paint)
        }
    }
    tp.textAlign = android.graphics.Paint.Align.RIGHT
    for (d in listOf(1, 10, 20, 31)) c.drawText("$d", left - 16f, top + (d - .5f) * ch + 10f, tp)
    tp.textAlign = android.graphics.Paint.Align.CENTER; tp.color = 0x99FFFFFF.toInt()
    c.drawText("PepsGoal 🔥", w / 2f, h - 90f, tp)
    return bmp
}
