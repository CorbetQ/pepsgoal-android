package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.data.Journal
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.step
import fr.pepsgoal.app.ui.theme.PLUM
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*

/* ------------------------------------------------------------------ Frise de la journée */

/**
 * Ta journée heure par heure : les objectifs du jour placés à l'heure où tu les valides d'habitude (médiane des
 * 30 derniers jours), sur une ligne de 6 h à minuit, avec un repère « maintenant ». Touche un objectif pour l'ouvrir.
 */
@Composable
fun TimelineCard(ui: AppState, plan: Plan) {
    val p = LocalPal.current; val pg = ui.pg
    if (plan.due.isEmpty()) return
    val items = remember(ui.rev) { plan.due.map { it to pg.usualHour(it.g).coerceIn(6f, 23.8f) }.sortedBy { it.second } }
    val nowH = remember(ui.rev) { java.time.LocalTime.now().let { it.hour + it.minute / 60f } }
    Glass {
        CardHead("Ta journée heure par heure", emoji = "🕐") { Muted(nowHM(), small = true) }
        BoxWithConstraints(Modifier.fillMaxWidth().height(132.dp)) {
            val w = maxWidth
            fun x(h: Float) = w * ((h - 6f) / 18f).coerceIn(0f, 1f)
            // Axe, créneaux et repère « maintenant »
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height - 22.dp.toPx(); val W = size.width
                val slots = listOf(6f to 11f, 11f to 14f, 14f to 18f, 18f to 24f)
                // Créneaux en aplats alternés, rail creusé, heure courante = ligne de la flamme de 2 dp
                slots.forEachIndexed { i, (a, b) -> if (i % 2 == 1) drawRoundRect(p.surface2, Offset(W * (a - 6f) / 18f, 0f), androidx.compose.ui.geometry.Size(W * (b - a) / 18f, y), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())) }
                drawRoundRect(p.rail, Offset(0f, y - 3.dp.toPx()), androidx.compose.ui.geometry.Size(W, 6.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                if (nowH in 6f..24f) { val nx = W * (nowH - 6f) / 18f
                    drawLine(p.focusEdge, Offset(nx, 0f), Offset(nx, y), strokeWidth = 2.dp.toPx())
                    drawCircle(p.focusEdge, 6.dp.toPx(), Offset(nx, y)) }
            }
            for (h in listOf(6, 9, 12, 15, 18, 21)) Text("$h h", Modifier.offset(x = x(h.toFloat()) - (if (h == 6) 0.dp else 10.dp), y = 114.dp), fontSize = 12.sp, color = p.ink3, fontWeight = FontWeight.Bold)
            // Pastilles des objectifs, sur trois rangs pour éviter les chevauchements
            val lanes = FloatArray(3) { -99f }
            items.forEach { (d, h) ->
                val lane = (0 until 3).firstOrNull { h - lanes[it] >= 1.6f } ?: (0 until 3).minByOrNull { lanes[it] }!!
                lanes[lane] = h
                val tc = p.type(d.g.type)
                // Pastille néon à marche (pleine si faite), qui s'enfonce
                val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val press = rememberPress(inter)
                val c = vivid(tc.c)
                Box(Modifier.offset(x = x(h) - 17.dp, y = (6 + lane * 34).dp).size(34.dp, 37.dp)
                    .chunk(if (d.done) c else p.surface, if (d.done) c.step() else c, 17.dp, 3.dp, if (d.done) 0.dp else 2.dp, press).clip(CircleShape)
                    .springClick(inter, label = d.g.title) { ui.open(Sheet.Goal(d.g.id)) }, contentAlignment = Alignment.Center) {
                    Emoji(d.g.icon, 18.dp)
                    if (d.done) Box(Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(PLUM), contentAlignment = Alignment.Center) { Pi("check", size = 10.dp, tint = c) }
                }
            }
        }
        val next = items.firstOrNull { !it.first.done && it.second >= nowH - .5f } ?: items.firstOrNull { !it.first.done }
        if (next != null) Muted("Prochain : ${next.first.g.icon} ${next.first.g.title} vers ${next.second.toInt()} h", small = true)
        else Muted("Tout est validé pour aujourd’hui 🔥", small = true)
    }
}

/* ------------------------------------------------------------------ Rattrapage de la veille */

/** « Tu l'as fait hier ? » : le matin, les objectifs prévus hier mais oubliés se valident d'une touche, sans casser la série. */
@Composable
fun CatchUpCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    val y = yday(); val key = "catchupNo:$y"
    var no by remember { mutableStateOf(ui.prefs.raw.getStringSet(key, emptySet()) ?: emptySet()) }
    val missed = remember(ui.rev, no) { pg.missedYesterday().filter { it.id !in no } }
    if (missed.isEmpty()) return
    Glass(border = p.flame) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Emoji("🤔", 30.dp)
            Column(Modifier.weight(1f)) { Text("Tu l’as fait hier ?", fontWeight = FontWeight.ExtraBold); Muted("Oubli de validation ? Rattrape-le ce matin, ta série reste intacte.", small = true) }
        }
        Spacer(Modifier.height(8.dp))
        missed.take(5).forEach { g ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${g.icon} ${g.title}", Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1)
                PButton("Non", { no = no + g.id; ui.prefs.raw.edit().putStringSet(key, no).apply() }, small = true, kind = "ghost")
                PButton("Oui, fait", { if (pg.catchUp(g)) { Feedback.success(); ui.toast("« ${g.title} » rattrapé pour hier. Série intacte !", "check") }; ui.bump() }, small = true, kind = "type", color = p.type(g.type).c)
            }
        }
    }
}

/* ------------------------------------------------------------------ Bilan express du soir */

/** Bilan du soir en 30 secondes, directement sur l'accueil (à partir de 20 h 30) : humeur, une phrase, reporter ou passer. */
@Composable
fun EveningQuickCard(ui: AppState, plan: Plan) {
    val p = LocalPal.current; val pg = ui.pg; val ctx = LocalContext.current
    val t = today()
    var closed by remember { mutableStateOf(ui.prefs.raw.getString("eveningQuick", "") == t) }
    if (closed || nowHM() < "20:30") return
    Journal.rev
    val e = remember(Journal.rev) { Journal.get(ctx, t) }
    var note by remember(e?.date) { mutableStateOf(e?.text ?: "") }
    val left = plan.due.filter { !it.done }
    Glass {
        CardHead("Bilan express", emoji = "🌙") { IconBtn("x", "Fermer le bilan", { closed = true; ui.prefs.raw.edit().putString("eveningQuick", t).apply() }, plain = true, size = 34.dp) }
        Muted("Ta journée en trois touches. Le bilan complet reste dans « Ta journée ».", small = true)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Journal.MOODS.forEachIndexed { i, (l, em) ->
                val on = e?.mood == i + 1
                val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val press = rememberPress(inter)
                val mc = vivid(p.type("monthly").c)
                Column(Modifier.chunk(if (on) mc else p.surface, if (on) mc.step() else p.edge, 17.dp, 3.dp, if (on) 0.dp else 2.dp, press).clip(RoundedCornerShape(15.dp))
                    .springClick(inter, label = l) { Journal.put(ctx, t, i + 1, note) }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Emoji(em, 28.dp); Text(l, fontSize = 12.sp, color = if (on) PLUM else p.ink2, fontWeight = FontWeight.Bold)
                }
            }
        }
        Field(note, { note = it }, "", placeholder = "Une phrase sur ta journée", max = 300, icon = "book")
        if (left.isNotEmpty()) {
            Spacer(Modifier.height(6.dp)); Eyebrow("Pas encore fait")
            left.take(4).forEach { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${d.g.icon} ${d.g.title}", Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1)
                    PButton("Reporter", { pg.skipToday(d.g)?.let { ui.toast(it, "x") } ?: ui.toast("« ${d.g.title} » reporté : aujourd’hui ne compte pas pour sa série.", "repeat") }, small = true, kind = "ghost")
                    PButton("Passer", { pg.setMissReason(d.g, t, "Pas aujourd’hui"); ui.toast("Noté. Demain est un autre jour.", "moon"); ui.bump() }, small = true)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PButton("Terminer", {
            if (e != null || note.isNotBlank()) Journal.put(ctx, t, e?.mood ?: 3, note)
            closed = true; ui.prefs.raw.edit().putString("eveningQuick", t).apply(); Feedback.success()
            ui.toast(if (left.isEmpty()) "Belle journée ! À demain." else "Bilan noté. Bonne soirée !", "moon")
        }, Modifier.fillMaxWidth(), icon = "check", kind = "primary")
    }
}
