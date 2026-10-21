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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.sys.Focus
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import kotlinx.coroutines.delay
import fr.pepsgoal.core.model.minutes
import androidx.compose.foundation.horizontalScroll

/** Mode Focus plein écran : choix de la durée et de l'objectif, puis grand compte à rebours circulaire. */
@Composable
fun FocusFs(ui: AppState, preGoal: String?) {
    val ctx = LocalContext.current; val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    BackHandler { ui.closeFs() }
    var running by remember { mutableStateOf(Focus.running(ctx)) }
    var left by remember { mutableLongStateOf(Focus.left(ctx)) }
    var total by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) { while (running) { left = Focus.left(ctx); if (left <= 0) { running = false; ui.bump() }; delay(500) } }
    var dndOk by remember { mutableStateOf(Focus.canDnd(ctx)) }
    LifecycleResumeEffect(Unit) { dndOk = Focus.canDnd(ctx); running = Focus.running(ctx); onPauseOrDispose {} }
    val goals = pg.myGoals(u.id).filter { !pg.isClosedGoal(it) && it.type in setOf("duration", "routine", "challenge", "project", "deadline") }
    var goal by remember { mutableStateOf(preGoal ?: ui.prefs.focusGoal.ifEmpty { null }) }
    var minutes by remember { mutableIntStateOf(25) }
    Box(Modifier.fillMaxSize()) {
        PageBackdrop(p.type("program").c, p.type("period").c)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Focus", Modifier.weight(1f), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                IconBtn("x", "Fermer", { ui.closeFs() }, size = 44.dp)
            }
            val g = (if (running) ui.prefs.focusGoal.ifEmpty { null } else goal)?.let { pg.goalById(it) }
            // Grand anneau : temps restant
            val frac = if (running) (left.toFloat() / (if (total > 0) total else (minutes * 60_000L)).toFloat()).coerceIn(0f, 1f) else 1f
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(260.dp)) {
                    val sw = 18.dp.toPx(); val r = Size(size.width - sw, size.height - sw)
                    drawArc(p.ink.copy(alpha = .1f), 0f, 360f, false, Offset(sw / 2, sw / 2), r, style = Stroke(sw))
                    drawArc(Brush.sweepGradient(listOf(p.flame2, p.flame, p.pink, p.flame2)), -90f, 360f * frac, false, Offset(sw / 2, sw / 2), r, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val shown = if (running) left else minutes * 60_000L
                    Text("%02d:%02d".format(shown / 60_000, (shown / 1000) % 60), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp)
                    Muted(if (running) (g?.let { "${it.icon} ${it.title}" } ?: "Session libre") else "prêt à démarrer", small = true)
                }
            }
            if (!running) {
                Glass {
                    Text("Durée", fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    FlowRowCompat(gap = 6.dp) { listOf(15, 25, 45, 60, 90).forEach { m -> FChip("$m min", minutes == m, { minutes = m }) } }
                    Text("Objectif lié", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                    ChipsRowFs {
                        FChip("Session libre", goal == null, { goal = null })
                        goals.forEach { x -> FChip("${x.icon} ${x.title}", goal == x.id, { goal = x.id; if (x.type == "duration") minutes = x.minutes.toInt().coerceIn(5, 180) }) }
                    }
                    SetRow("Ne pas déranger pendant la session", if (dndOk) "Les notifications non prioritaires sont mises en sourdine." else "Autorise PepsGoal à gérer « Ne pas déranger ».") {
                        if (dndOk) PSwitch(ui.prefs.focusDnd, { ui.prefs.focusDnd = it; ui.bump() }) else PButton("Autoriser", { Focus.askDnd(ctx) }, small = true)
                    }
                }
                PButton("Démarrer la session", { total = minutes * 60_000L; Focus.start(ctx, minutes, goal); running = true; left = Focus.left(ctx) }, Modifier.fillMaxWidth(), icon = "play", kind = "primary")
            } else {
                Muted("Le compte à rebours reste visible dans la barre d’état. ${if (g?.type == "duration") "Le minuteur de l’objectif tourne en même temps." else if (g != null) "L’objectif sera validé à la fin de la session." else ""}", center = true, small = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PButton("Arrêter", { Focus.stop(ctx, false); running = false }, icon = "x", kind = "danger")
                    PButton("Terminer maintenant", { Focus.stop(ctx, true); running = false; ui.toast("Session terminée, bravo !", "check") }, icon = "check", kind = "primary")
                }
            }
        }
    }
}

@Composable
private fun ChipsRowFs(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
