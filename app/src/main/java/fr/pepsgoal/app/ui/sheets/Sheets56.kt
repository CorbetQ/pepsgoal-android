package fr.pepsgoal.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.PLUM
import fr.pepsgoal.app.ui.theme.step
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.*

/* ------------------------------------------------------------------ Pause d'un objectif */

private val PAUSE_REASONS = listOf("🤕" to "Blessure", "✈️" to "Voyage", "🤒" to "Malade", "💼" to "Rush", "🌙" to "Autre")

/** Bandeau de la fiche quand l'objectif est en pause : date de reprise et reprise immédiate. */
@Composable
fun PauseBanner(ui: AppState, g: Goal) {
    val p = LocalPal.current; val pg = ui.pg
    if (!pg.isPaused(g)) return
    Row(Modifier.fillMaxWidth().chunk(vivid(p.type("period").c), p.type("period").c.step(), 24.dp, 4.dp, 0.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Emoji("⏸️", 28.dp)
        Column(Modifier.weight(1f)) {
            Text("En pause jusqu’au ${g.pausedUntil?.let { fmtDate(it) } ?: "?"}", fontWeight = FontWeight.ExtraBold, color = PLUM)
            Text("${g.pauseReason.ifEmpty { "Pause" }} · la série est protégée, rappel le jour de la reprise.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PLUM)
        }
        PButton("Reprendre", { pg.resumeGoal(g); ui.toast("« ${g.title} » reprend aujourd’hui.", "play") }, small = true, icon = "play")
    }
}

/** Option « Mettre en pause » : durée, raison, date de reprise. */
@Composable
fun PauseRow(ui: AppState, g: Goal) {
    val p = LocalPal.current; val pg = ui.pg
    var open by remember { mutableStateOf(false) }
    var days by remember { mutableIntStateOf(7) }; var reason by remember { mutableStateOf("Voyage") }
    val paused = pg.isPaused(g)
    OptionRow("pause", p.focus, p.type("period").c, "Pause", if (paused) "Jusqu’au ${g.pausedUntil?.let { fmtDate(it) }}" else "Blessure, voyage… sans perdre la série", open, { open = !open }) {
        if (paused) PButton("Reprendre maintenant", { pg.resumeGoal(g); open = false }, small = true, icon = "play", color = p.type("period").c)
        else {
            Muted("L’objectif disparaît de ta journée et sa série est gelée jusqu’à la reprise. Un rappel t’attend le jour du retour.", small = true)
            FlowRowCompat(gap = 6.dp) { listOf(3 to "3 jours", 7 to "1 semaine", 14 to "2 semaines", 30 to "1 mois", 60 to "2 mois").forEach { (n, l) -> FChip(l, days == n, { days = n }) } }
            FlowRowCompat(gap = 6.dp) { PAUSE_REASONS.forEach { (e, l) -> FChip("$e $l", reason == l, { reason = l }) } }
            val until = ymd(todayDate().plusDays(days.toLong() - 1))
            PButton("Mettre en pause jusqu’au ${fmtDate(until)}", { pg.pauseGoal(g, until, reason)?.let { ui.toast(it, "x") } ?: run { open = false; ui.toast("« ${g.title} » en pause jusqu’au ${fmtDate(until)}. Prends soin de toi.", "pause") } }, icon = "pause", small = true, color = p.type("period").c)
        }
    }
}

/* ------------------------------------------------------------------ Objectif par paliers */

/** Option « Progression par paliers » (durée, cap quotidien) : pas, plafond et plancher. */
@Composable
fun LadderRow(ui: AppState, g: Goal) {
    val p = LocalPal.current; val pg = ui.pg
    if (g.type !in LADDER_TYPES) return
    var open by remember { mutableStateOf(false) }
    val unit = if (g.type == "duration") "min" else g.unit.ifEmpty { "" }
    val cur = ladderValue(g)
    OptionRow("stairs", p.type("challenge").c, p.flame, "Paliers", if (g.ladder) "${fmtNum(cur)} $unit · +${fmtNum(g.ladderStep)} après une semaine parfaite" else "L’objectif monte tout seul quand tu tiens", open, { open = !open },
        right = { PSwitch(g.ladder, { on -> g.ladder = on; if (on) { if (g.ladderAt.isEmpty()) g.ladderAt = today(); if (g.numOrNull("ladderMax") == null) g.ladderMax = cur * 3; if (g.numOrNull("ladderMin") == null) g.ladderMin = maxOf(1.0, cur / 2) }; pg.store.commit(); ui.bump() }) }) {
        Muted("Une semaine réussie à 100 % : +1 cran. Deux semaines sous 50 % : −1 cran. Jamais au-delà du plafond ni sous le plancher.", small = true)
        @Composable fun Stepper(label: String, v: Double, step: Double, set: (Double) -> Unit) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                PStepper(fmtNum(v), unit, { set(maxOf(step, v - step)); pg.store.commit(); ui.bump() }, { set(v + step); pg.store.commit(); ui.bump() }, minusEnabled = v > step)
            }
        }
        val unitStep = if (g.type == "duration") 5.0 else 1.0
        Stepper("Cran", g.ladderStep, unitStep) { g.ladderStep = it }
        Stepper("Plafond", g.ladderMax, unitStep) { g.ladderMax = maxOf(it, cur) }
        Stepper("Plancher", g.ladderMin, unitStep) { g.ladderMin = minOf(it, cur) }
    }
}

/* ------------------------------------------------------------------ Raccourci sur l'écran d'accueil */

@Composable
fun PinRow(ui: AppState, g: Goal) {
    val p = LocalPal.current; val ctx = LocalContext.current
    if (g.type !in setOf("routine", "abstinence", "timed", "period", "challenge", "limit")) return
    OptionRow("bolt", p.type("project").c, p.type("objective").c, "Raccourci", "Valider d’une touche depuis l’écran d’accueil", false, {
        if (fr.pepsgoal.app.sys.AssistantShortcuts.pin(ctx, g)) ui.toast("Confirme l’ajout du raccourci « ${g.title} ».", "bolt") else ui.toast("Ton lanceur ne permet pas d’épingler de raccourci.", "x")
    })
}
