package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.step
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.*
import java.time.LocalDate

/** Planning de la semaine : chaque jour, les objectifs prévus et leur état (fait, manqué, à venir, au repos). */
@Composable
fun WeekScreen(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    var off by rememberSaveable { mutableIntStateOf(0) }
    val ws = weekStart(todayDate()).plusWeeks(off.toLong())
    val goals = remember(ui.rev, u.id) { pg.myGoals(u.id).filter { !it.archived && it.userId == u.id } }
    val counts = remember(goals) { goals.filter { it.type == "routine" && it.mode == "count" } }
    // Validations de la semaine affichée, indexées une fois (au lieu de parcourir toutes les validations pour chaque
    // objectif de chaque jour)
    val wins = remember(ui.rev, ws) {
        val from = ymd(ws); val to = ymd(ws.plusDays(6)); val me = pg.meId()
        pg.db.checkins.filter { it.userId == me && it.date >= from && it.date <= to && pg.isWin(it) }.mapTo(HashSet()) { it.goalId + "|" + it.date }
    }
    ScreenFrame(ui, "Ma semaine", "Retour", { ui.back() }, icon = "cal", color = p.type("period").c, color2 = p.type("routine").c) {
        item("nav") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBtn("chevronleft", "Semaine précédente", { off-- }, size = 40.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (off == 0) "Cette semaine" else if (off == -1) "Semaine dernière" else if (off == 1) "Semaine prochaine" else "Semaine du ${fmtDateShort(ymd(ws))}", fontWeight = FontWeight.ExtraBold)
                    Muted("${fmtDateShort(ymd(ws))} – ${fmtDateShort(ymd(ws.plusDays(6)))}", small = true)
                }
                // Flèche « suivant » : la flèche « précédent » retournée
                Box(Modifier.rotate(180f)) { IconBtn("chevronleft", "Semaine suivante", { off++ }, size = 40.dp) }
            }
        }
        // Routines « X fois par semaine » : un compteur pour toute la semaine
        if (counts.isNotEmpty()) item("counts") {
            Glass(pad = 14.dp) {
                Text("À répartir dans la semaine", fontWeight = FontWeight.ExtraBold)
                counts.forEach { g -> val n = pg.weekCount(g, ws)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoalIcon(g, 30.dp); Text(g.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text("$n/${g.perWeek}", fontWeight = FontWeight.ExtraBold, color = if (n >= g.perWeek) p.ok else p.ink2)
                    }
                }
            }
        }
        for (i in 0..6) { val d = ws.plusDays(i.toLong()); item("d${ymd(d)}", contentType = "day") { DayCard(ui, d, goals, wins) } }
    }
}

@Composable
private fun DayCard(ui: AppState, d: LocalDate, goals: List<Goal>, wins: Set<String>) {
    val p = LocalPal.current; val pg = ui.pg; val k = ymd(d); val t = today()
    val list = goals.filter { g -> ymd(g.createdAt) <= k && !(g.type == "routine" && g.mode == "count") && (pg.scheduled(g, d) || (g.type == "deadline" && g.due == k && g.completedAt == null)) }
    val isToday = k == t; val past = k < t
    val rest = k in pg.restSet()
    Glass(pad = 14.dp, border = if (isToday) p.focusEdge else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp).then(if (isToday) Modifier.chunk(p.flameBrush(), p.flameStep, 17.dp, 3.dp, 0.dp) else Modifier.background(p.surface2, RoundedCornerShape(17.dp))), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(WD_SHORT[wdIdx(d)], fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (isToday) p.onAccent else p.ink2)
                    Text("${d.dayOfMonth}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = if (isToday) p.onAccent else p.ink)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(if (isToday) "Aujourd’hui" else cap(weekdayLongDate(d)), fontWeight = FontWeight.ExtraBold)
                val done = list.count { g -> "${g.id}|$k" in wins }
                Muted(when { rest && k in (pg.me()?.restDays ?: emptyList<String>()) -> "😴 Jour de repos"; rest -> "🏖️ Vacances"; list.isEmpty() -> "Rien de prévu"; else -> "$done/${list.size} ${if (past || isToday) "validés" else "prévus"}" }, small = true)
            }
        }
        if (list.isNotEmpty()) Spacer(Modifier.height(8.dp))
        list.forEach { g ->
            val done = "${g.id}|$k" in wins
            val skipped = k in pg.goalRest(g)
            val (ic, col) = when { done -> "✓" to p.ok; skipped -> "–" to p.ink3; past -> "✕" to p.danger; else -> "○" to p.ink3 }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable { ui.open(Sheet.Goal(g.id)) }.heightIn(min = 44.dp).padding(vertical = 5.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(24.dp).background(if (done) vivid(col) else p.surface, CircleShape).border(2.dp, if (done) col.step() else if (past && !skipped) col else p.control, CircleShape), contentAlignment = Alignment.Center) {
                    Text(ic, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (done) fr.pepsgoal.app.ui.theme.PLUM else col)
                }
                Emoji(g.icon, 20.dp)
                Text(g.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (done || skipped) p.ink2 else p.ink, fontSize = 14.sp)
                val why = g.missWhy()[k]
                if (past && !done && why != null) Text(MISS_REASONS.firstOrNull { it.first == why }?.second?.take(2) ?: "", fontSize = 14.sp)
                if (g.type == "timed") Muted(g.time, small = true) else if (g.type == "period") Muted(g.start, small = true) else g.remindAt?.let { Muted(it, small = true) }
            }
        }
    }
}
