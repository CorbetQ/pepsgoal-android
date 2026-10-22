package fr.pepsgoal.app.ui.fullscreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.fmtNum
import kotlinx.coroutines.delay

/**
 * Minuteur d'intervalles (effort / repos × séries) pour un objectif de durée : bips et vibrations à chaque changement,
 * décompte 3-2-1, écran gardé allumé ; le temps de la séance s'ajoute à l'objectif à la fin.
 */
@Composable
fun IntervalsFs(ui: AppState, goalId: String) {
    val p = LocalPal.current; val pg = ui.pg; val g = pg.goalById(goalId)
    var work by remember { mutableIntStateOf(ui.prefs.raw.getInt("ivWork", 30)) }
    var rest by remember { mutableIntStateOf(ui.prefs.raw.getInt("ivRest", 15)) }
    var rounds by remember { mutableIntStateOf(ui.prefs.raw.getInt("ivRounds", 8)) }
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("prep") }   // prep, work, rest, done
    var round by remember { mutableIntStateOf(1) }
    var left by remember { mutableLongStateOf(0L) }; var span by remember { mutableLongStateOf(1L) }
    var paused by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(running) { view.keepScreenOn = running; onDispose { view.keepScreenOn = false } }
    val total = work * rounds + rest * (rounds - 1)
    fun finish() {
        running = false; phase = "done"; Feedback.success()
        if (g != null) pg.addTimeMinutes(g, total / 60.0, false)
    }
    BackHandler { if (running) { running = false; phase = "prep" } else ui.closeFs() }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        ui.prefs.raw.edit().putInt("ivWork", work).putInt("ivRest", rest).putInt("ivRounds", rounds).apply()
        // Enchaînement des phases ; le temps est compté sur l'horloge (pas de dérive), la pause le fige
        suspend fun run(ph: String, secs: Int): Boolean {
            phase = ph; span = secs * 1000L; var end = System.currentTimeMillis() + span; var lastSec = -1L
            Feedback.beep(ph == "work")
            while (true) {
                if (!running) return false
                if (paused) { val rem = end - System.currentTimeMillis(); while (paused && running) delay(100); end = System.currentTimeMillis() + rem }
                left = (end - System.currentTimeMillis()).coerceAtLeast(0)
                val s = (left + 999) / 1000
                if (s in 1..3 && s != lastSec) { lastSec = s; Feedback.tick() }
                if (left <= 0) return true
                delay(50)
            }
        }
        if (!run("prep", 5)) return@LaunchedEffect
        for (r in 1..rounds) {
            round = r
            if (!run("work", work)) return@LaunchedEffect
            if (r < rounds && !run("rest", rest)) return@LaunchedEffect
        }
        finish()
    }
    val col = when (phase) { "work" -> p.flame; "rest" -> p.type("routine").c; "done" -> p.ok; else -> p.type("period").c }
    Box(Modifier.fillMaxSize()) {
        PageBackdrop(col, p.pink)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Intervalles", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                    g?.let { Muted("${it.icon} ${it.title}", small = true) }
                }
                IconBtn("x", "Fermer", { running = false; ui.closeFs() }, size = 44.dp)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val frac = if (running) (left.toFloat() / span).coerceIn(0f, 1f) else 1f
                Canvas(Modifier.size(270.dp)) {
                    val sw = 20.dp.toPx(); val r = Size(size.width - sw, size.height - sw)
                    drawArc(col.copy(alpha = .15f), 0f, 360f, false, Offset(sw / 2, sw / 2), r, style = Stroke(sw))
                    drawArc(Brush.sweepGradient(listOf(col, p.pink, col)), -90f, 360f * frac, false, Offset(sw / 2, sw / 2), r, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(when (phase) { "work" -> "EFFORT"; "rest" -> "REPOS"; "done" -> "TERMINÉ"; else -> if (running) "PRÊT ?" else "SÉANCE" }, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = col, letterSpacing = 2.sp)
                    Text(if (running) "${(left + 999) / 1000}" else if (phase == "done") "🎉" else "%d:%02d".format(total / 60, total % 60), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp)
                    if (running && phase != "prep") Muted("Série $round / $rounds", small = true)
                    if (phase == "done") Muted("${fmtNum(Math.round(total / 6.0) / 10.0)} min ajoutées à l’objectif", small = true)
                }
            }
            if (!running) {
                Glass {
                    Text("Effort", fontWeight = FontWeight.ExtraBold)
                    FlowRowCompat(Modifier.padding(top = 6.dp), gap = 6.dp) { listOf(20, 30, 40, 45, 60, 90).forEach { s -> FChip("$s s", work == s, { work = s }) } }
                    Text("Repos", Modifier.padding(top = 12.dp), fontWeight = FontWeight.ExtraBold)
                    FlowRowCompat(Modifier.padding(top = 6.dp), gap = 6.dp) { listOf(10, 15, 20, 30, 45, 60).forEach { s -> FChip("$s s", rest == s, { rest = s }) } }
                    Text("Séries", Modifier.padding(top = 12.dp), fontWeight = FontWeight.ExtraBold)
                    FlowRowCompat(Modifier.padding(top = 6.dp), gap = 6.dp) { listOf(4, 6, 8, 10, 12, 16).forEach { n -> FChip("× $n", rounds == n, { rounds = n }) } }
                }
                PButton(if (phase == "done") "Recommencer" else "Démarrer", { phase = "prep"; paused = false; running = true }, Modifier.fillMaxWidth(), icon = "play", kind = "primary")
            } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PButton(if (paused) "Reprendre" else "Pause", { paused = !paused }, icon = if (paused) "play" else "pause")
                PButton("Arrêter", { running = false; phase = "prep" }, icon = "x", kind = "ghost")
            }
        }
    }
}
