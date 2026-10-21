package fr.pepsgoal.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.*
import kotlinx.coroutines.delay

/** Valider (ou ouvrir la saisie adaptée au type). */
fun doValidate(ui: AppState, id: String) {
    when (val r = ui.pg.validate(id)) {
        is Pg.VResult.AskProgress -> ui.open(Sheet.Progress(r.goalId))
        is Pg.VResult.OpenGoal -> ui.open(Sheet.Goal(r.goalId))
        else -> {}
    }
}

/** Seconde courante (minuteur en marche). */
@Composable
fun tickingNow(active: Boolean): Long {
    var now by remember { mutableLongStateOf(nowMs()) }
    LaunchedEffect(active) { while (active) { delay(1000); now = nowMs() } }
    return now
}

@Composable
fun GoalIcon(g: Goal, size: Dp = 44.dp) {
    val tc = LocalPal.current.type(g.type)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .3f)).background(tc.c.copy(alpha = .22f)), contentAlignment = Alignment.Center) { Emoji(g.icon, size * .58f) }
}

@Composable
fun CheckButton(done: Boolean, just: Boolean, label: String, color: Color, disabled: Boolean = false, icon: String = "check", running: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    val pop = remember { Animatable(1f) }
    LaunchedEffect(just) { if (just && !p.motion) { pop.snapTo(.7f); pop.animateTo(1f, spring(dampingRatio = .35f, stiffness = 500f)) } }
    val filled = done || running
    // Couleurs du type lisibles dans les deux thèmes : foncées sur fond clair, vives sur fond sombre
    val line = readable(color)
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier.bounce(inter, .8f).scale(pop.value).size(44.dp).clip(CircleShape).background(if (filled) color else color.copy(alpha = if (disabled) .06f else .16f))
            .border(2.5.dp, if (disabled) p.ink3.copy(alpha = .45f) else if (filled) line.copy(alpha = .35f) else line, CircleShape)
            .springClick(inter, label = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { Pi(icon, size = 22.dp, tint = if (filled) Color(0xFF10102A) else if (disabled) p.ink3.copy(alpha = .55f) else line) }
}

/** Couleur d'accent assombrie en thème clair (contraste suffisant sur le verre), inchangée en thème sombre. */
@Composable
fun readable(c: Color): Color {
    val p = LocalPal.current
    if (p.dark) return c
    return androidx.compose.ui.graphics.lerp(c, Color(0xFF10102A), if (p.contrast) .7f else .42f)
}

@Composable
fun MiniBtn(text: String, color: Color, onClick: () -> Unit) {
    val p = LocalPal.current
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(Modifier.bounce(inter, .88f).clip(CircleShape).background(color.copy(alpha = .22f)).border(1.5.dp, readable(color).copy(alpha = .55f), CircleShape).springClick(inter, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = p.ink, maxLines = 1)
    }
}

@Composable
fun AvStack(pg: Pg, ids: List<String>, size: Dp = 22.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) { ids.mapNotNull { pg.userById(it) }.take(4).forEach { Av(it, size, modifier = Modifier.border(2.dp, LocalPal.current.glassStrong, CircleShape)) } }
}

@Composable
fun FavStar(pg: Pg, g: Goal) { if (pg.isFav(g)) Pi("star", size = 14.dp, tint = LocalPal.current.flame2) }

/** Ligne d'un objectif à valider ou en cours (accueil). */
@Composable
fun GoalRow(ui: AppState, g: Goal, done: Boolean, metaOverride: String? = null) {
    val p = LocalPal.current; val pg = ui.pg; val tc = p.type(g.type)
    val running = pg.timerOf(g) != null
    val now = tickingNow(running)
    val just = done && pg.justDone == g.id
    var meta = ""; var urgent = false; var bar: Double? = null; var extra: @Composable (() -> Unit)? = null
    var right: @Composable () -> Unit = { CheckButton(done, just, if (done) "Annuler la validation : ${g.title}" else "Valider : ${g.title}", tc.c) { doValidate(ui, g.id) } }
    when (g.type) {
        "limit" -> { val used = pg.limitUsed(g); val over = used > g.max
            meta = "${fmtNum(used)}/${fmtNum(g.max)} ${g.unit} ${if (g.period == "week") "cette semaine" else "aujourd'hui"}${if (over) " · limite dépassée" else ""}"; urgent = over; bar = used / maxOf(g.max, 1.0) * 100
            right = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { MiniBtn("+1", tc.c) { pg.limitAdd(g.id) }; CheckButton(done, just, "Valider ma journée sous la limite", tc.c, over && !done) { doValidate(ui, g.id) } } } }
        "duration" -> { val m = pg.durationLiveMins(g).also { now.hashCode() }; meta = "${fmtClock(m * 60)} / ${fmtNum(g.minutes)} min"; bar = m / g.minutes * 100
            right = { CheckButton(done && !running, just, if (running) "Mettre en pause : ${g.title}" else "Démarrer le minuteur : ${g.title}", tc.c, icon = if (running) "pause" else if (done) "check" else "play", running = running) { pg.toggleTimer(g) } } }
        "measure" -> { meta = "Dernière : ${pg.measureStat(g).v}"; right = { MiniBtn("+ Mesure", tc.c) { doValidate(ui, g.id) } } }
        "timed" -> { val w = pg.timedWindow(g); meta = w.txt; urgent = !w.open && !done
            right = { CheckButton(done, just, if (g.mode == "notafter") "J'ai tenu hier soir" else "Valider", tc.c, !w.open && !done) { doValidate(ui, g.id) } } }
        "period" -> { val w = pg.periodWindow(g); meta = w.txt; urgent = !w.open && !done
            right = { CheckButton(done, just, "Valider", tc.c, !w.open && !done) { doValidate(ui, g.id) } } }
        "project" -> { val (d, n) = pg.projectCounts(g); meta = "$d/$n étapes"; bar = if (n > 0) d * 100.0 / n else 0.0; right = { MiniBtn("Ouvrir", tc.c) { ui.open(Sheet.Goal(g.id)) } } }
        "challenge" -> { meta = "Jour ${pg.challengeDay(g)}/${g.length}"; bar = pg.challengeDone(g) * 100.0 / g.length }
        "program" -> { val w = pg.programWeek(g); val s = pg.programSum(g, w); val t = pg.programTarget(g, w); meta = "Semaine $w/${g.weeks} : ${fmtNum(s)}/${fmtNum(t)} ${g.unit}"; bar = s / t * 100
            right = { MiniBtn("+ Progrès", tc.c) { doValidate(ui, g.id) } } }
        "group" -> { val t = pg.groupTotal(g); meta = "${fmtNum(round1(t))}/${fmtNum(g.target)} ${g.unit} ensemble"; bar = t / g.target * 100; extra = { AvStack(pg, g.members ?: emptyList()) }
            right = { MiniBtn("+ Ma part", tc.c) { doValidate(ui, g.id) } } }
        "duel" -> { val o = pg.userById(pg.duelOpp(g)); val a = pg.duelScore(g, pg.meId()); val b = pg.duelScore(g, o?.id ?: "")
            meta = "Toi ${fmtNum(round1(a))} – ${fmtNum(round1(b))} ${first(o?.name)} · ${g.unit}"; bar = if (a + b > 0) a / (a + b) * 100 else 50.0
            right = { MiniBtn("+ Score", tc.c) { doValidate(ui, g.id) } } }
        "deadline" -> { val n = daysUntil(g.due); meta = pg.dueText(g.due); urgent = n <= 2; right = { MiniBtn("Terminé", tc.c) { doValidate(ui, g.id) } } }
        "objective", "monthly" -> { val mo = g.type == "monthly"; val pr = if (mo) pg.monthProgress(g) else pg.objProgress(g)
            meta = "${fmtNum(pr)} / ${fmtNum(g.target)} ${g.unit}${if (mo) " · ${if (pr >= g.target) "atteint ce mois-ci" else pl(pg.daysLeftInMonth(), "jour restant", "jours restants")}" else ""}"; bar = minOf(100.0, pr / g.target * 100)
            right = { MiniBtn("+ Progrès", tc.c) { doValidate(ui, g.id) } } }
        else -> { val s = pg.statBig(g); meta = "${metaOverride ?: pg.metaLine(g)} · ${s.v} ${s.l}" }
    }
    val pend = pg.db.checkins.any { it.goalId == g.id && it.pending && it.userId == pg.meId() }
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier.bounce(inter, .97f).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (done) tc.c.copy(alpha = .14f) else Color.Transparent).springClick(inter) { ui.open(Sheet.Goal(g.id)) }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GoalIcon(g)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FavStar(pg, g)
                Text(g.title, fontWeight = FontWeight.ExtraBold, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = null)
            }
            FlowRowCompat(gap = 6.dp) {
                TypeChip(g.type)
                Text(meta, fontSize = 13.sp, color = if (urgent) p.danger else p.ink2, fontWeight = if (urgent) FontWeight.Bold else FontWeight.Medium)
                if (pend) Chip("En attente de ${first(pg.userById(g.buddy)?.name)}", "clock")
                extra?.invoke()
            }
            bar?.let { Bar(it, Modifier.padding(top = 3.dp), tc.c, 6.dp) }
        }
        right()
    }
}

/** Points des 7 derniers jours (validé, écart). */
@Composable
fun Last7(pg: Pg, g: Goal) {
    val p = LocalPal.current; val tc = p.type(g.type)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 6 downTo 0) {
            val k = ymd(todayDate().minusDays(i.toLong()))
            val on = pg.db.checkins.any { it.goalId == g.id && it.date == k && pg.isWin(it) }; val rel = pg.hasCheck(g.id, k, "relapse")
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (rel) p.danger else if (on) tc.c else p.ink.copy(alpha = .12f)))
        }
    }
}

/** Carte d'un objectif (onglet Objectifs). */
@Composable
fun GoalCard(ui: AppState, g: Goal, modifier: Modifier = Modifier) {
    val p = LocalPal.current; val pg = ui.pg; val tc = p.type(g.type); val s = pg.statBig(g)
    Glass(modifier, pad = 16.dp, onClick = { ui.open(Sheet.Goal(g.id)) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalIcon(g, 46.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { FavStar(pg, g); Text(g.title, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(6.dp))
                FlowRowCompat(gap = 6.dp) {
                    TypeChip(g.type)
                    if (g.members != null) Chip("${g.members!!.size} membres", "group") else VIS[g.visibility]?.let { Chip(it.first, it.second) }
                    if (g.buddy != null) Chip("binôme", "shield")
                }
            }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.v, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = tc.ink)
            Text(s.l, fontWeight = FontWeight.Bold, color = p.ink2, modifier = Modifier.padding(bottom = 4.dp))
        }
        pg.barPct(g)?.takeIf { g.type !in setOf("limit", "duration") }?.let { Bar(it, Modifier.padding(top = 8.dp), tc.c) }
        if (g.type == "group") Box(Modifier.padding(top = 6.dp)) { AvStack(pg, g.members ?: emptyList()) }
        if (g.type == "measure") MeasureSpark(pg, g)
        if (g.type == "monthly" && g.cumulate) Muted("Cumul : ${fmtNum(pg.objProgress(g))} ${g.unit} · ${pl(pg.goalStreak(g), "mois réussi d’affilée", "mois réussis d’affilée")}", small = true)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Muted(pg.metaLine(g), Modifier.weight(1f), small = true, maxLines = 2)
            if (g.type in setOf("routine", "abstinence", "limit", "duration", "timed", "period", "challenge")) Last7(pg, g)
        }
    }
}

/* ------------------------------------------------------------------ fiches : calendrier, courbes, listes */
@Composable
fun GoalCalendar(pg: Pg, g: Goal) {
    val p = LocalPal.current; val tc = p.type(g.type)
    val ws = weekStart(todayDate()); val start = ws.minusDays(28); val t = today(); val created = ymd(g.createdAt)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { WD.forEach { Text(it, Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = p.ink3, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
        for (w in 0 until 5) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 0 until 7) {
                val d = start.plusDays((w * 7 + i).toLong()); val k = ymd(d); val fut = k > t
                val on = pg.db.checkins.any { it.goalId == g.id && it.date == k && pg.isWin(it) }; val rel = pg.hasCheck(g.id, k, "relapse")
                val cls = when { rel -> "relapse"; on -> "on"; fut -> "fut"; k >= created && pg.scheduled(g, d) && !(g.type == "abstinence" && k < g.since) -> if (k == t) "pending" else "miss"; else -> "rest" }
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(9.dp))
                    .background(when (cls) { "on" -> tc.c; "relapse" -> p.danger; "rest", "fut" -> p.ink.copy(alpha = .05f); else -> Color.Transparent })
                    .then(if (cls == "miss" || cls == "pending") Modifier.border(1.5.dp, if (cls == "pending") tc.c else p.ink3.copy(alpha = .6f), RoundedCornerShape(9.dp)) else Modifier)
                    .then(if (k == t) Modifier.border(2.dp, p.ink, RoundedCornerShape(9.dp)) else Modifier), contentAlignment = Alignment.Center) {
                    Text("${d.dayOfMonth}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (cls == "on") p.onAccent else if (fut) p.ink3 else p.ink2)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
            Legend(tc.c, "Validé")
            if (g.type != "objective") Legend(Color.Transparent, "Manqué", p.ink3)
            if (g.type == "abstinence") Legend(p.danger, "Écart")
        }
    }
}

@Composable
private fun Legend(c: Color, l: String, border: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(c).then(if (border != null) Modifier.border(1.5.dp, border, RoundedCornerShape(4.dp)) else Modifier))
        Text(l, fontSize = 12.sp, color = LocalPal.current.ink2, fontWeight = FontWeight.Bold)
    }
}

/** Ligne « mois / semaine / jour » : libellé, barre, valeur. */
@Composable
fun ProgLine(label: String, pct: Double, value: String, color: Color, hit: Boolean = false, cur: Boolean = false, fut: Boolean = false, over: Boolean = false, lead: (@Composable () -> Unit)? = null) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().then(if (cur) Modifier.border(2.dp, color, RoundedCornerShape(8.dp)) else Modifier).padding(horizontal = 6.dp, vertical = 5.dp).alpha(if (fut) .5f else 1f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.width(110.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { lead?.invoke(); Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Bar(pct, Modifier.weight(1f), if (over) p.danger else color, 7.dp)
        Text(value + if (hit) " ✓" else "", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = if (over) p.danger else p.ink2)
    }
}


@Composable
fun MeasureSpark(pg: Pg, g: Goal) {
    val pts = pg.measurePts(g); if (pts.size < 2) return
    val c = LocalPal.current.type(g.type).ink
    Canvas(Modifier.padding(top = 8.dp).width(110.dp).height(32.dp)) {
        val vs = pts.map { it.value }; val lo = vs.min(); val hi = vs.max()
        val xy = vs.mapIndexed { i, v -> Offset(i / (vs.size - 1f) * (size.width - 6) + 3, if (hi == lo) size.height / 2 else (3 + (hi - v) / (hi - lo) * (size.height - 6)).toFloat()) }
        val path = Path().apply { xy.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
        drawPath(path, c, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(c, 3.5.dp.toPx(), xy.last())
    }
}

/** Courbe des mesures (avec la cible en pointillés). */
@Composable
fun MeasureChart(pg: Pg, g: Goal) {
    val p = LocalPal.current; val tc = p.type(g.type); val pts = pg.measurePts(g)
    if (pts.size < 2) { Muted("Ajoute au moins deux mesures pour voir ta courbe.", small = true); return }
    Column {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val L = 50f; val R = 14f; val T = 14f; val B = 26f
            val vs = pts.map { it.value }; val dMin = vs.min(); val dMax = vs.max(); val tg = g.targetOrNull
            var lo = minOf(dMin, tg ?: dMin); var hi = maxOf(dMax, tg ?: dMax); if (lo == hi) { lo -= 1; hi += 1 }
            val padv = (hi - lo) * .12; lo -= padv; hi += padv
            val t0 = parseYmd(pts.first().date).toEpochDay(); val t1 = parseYmd(pts.last().date).toEpochDay()
            val X = { d: String -> (L + if (t1 == t0) (size.width - L - R) / 2 else (parseYmd(d).toEpochDay() - t0).toFloat() / (t1 - t0) * (size.width - L - R)) }
            val Y = { v: Double -> (T + (hi - v) / (hi - lo) * (size.height - T - B)).toFloat() }
            val xy = pts.map { Offset(X(it.date), Y(it.value)) }
            for (v in setOf(dMin, dMax)) drawLine(p.line, Offset(L, Y(v)), Offset(size.width - R, Y(v)), 1f)
            if (tg != null) drawLine(p.ink2, Offset(L, Y(tg)), Offset(size.width - R, Y(tg)), 1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            val line = Path().apply { xy.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            val area = Path().apply { addPath(line); lineTo(xy.last().x, size.height - B); lineTo(xy.first().x, size.height - B); close() }
            drawPath(area, Brush.verticalGradient(listOf(tc.c.copy(alpha = .45f), Color.Transparent)))
            drawPath(line, tc.ink, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            xy.dropLast(1).forEach { drawCircle(tc.ink, 2.6.dp.toPx(), it) }
            drawCircle(tc.c.copy(alpha = .35f), 9.dp.toPx(), xy.last()); drawCircle(tc.ink, 4.5.dp.toPx(), xy.last())
        }
        Row(Modifier.fillMaxWidth()) {
            Muted(fmtDateShort(pts.first().date), Modifier.weight(1f), small = true)
            g.targetOrNull?.let { Muted("cible ${pg.measureFmt(g, it)}", small = true) }
            Spacer(Modifier.weight(1f)); Muted(fmtDateShort(pts.last().date), small = true)
        }
    }
}

/** Semaine en cours : flammes allumées, jours gelés, jours à venir. */
@Composable
fun WeekStrip(pg: Pg, id: String) {
    val p = LocalPal.current
    val ws = weekStart(todayDate()); val days = pg.activeDays(id); val frozen = (pg.userById(id)?.frozenDays ?: emptyList()).toSet(); val t = today()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0 until 7) {
            val k = ymd(ws.plusDays(i.toLong())); val fut = k > t; val on = days.contains(k); val fz = !on && frozen.contains(k)
            Column(Modifier.clip(RoundedCornerShape(14.dp)).background(if (k == t) p.ink.copy(alpha = .07f) else Color.Transparent).padding(horizontal = 6.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (fz) Pi("snow", size = 22.dp, tint = p.type("objective").ink) else Flame(22.dp, dim = !on)
                Text(WD[i], fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (fut) p.ink3 else p.ink2)
            }
        }
    }
}
