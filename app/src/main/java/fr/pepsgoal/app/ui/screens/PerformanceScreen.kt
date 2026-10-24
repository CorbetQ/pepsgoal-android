package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.sys.Perf
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val MODE_DESC = mapOf(
    "auto" to "PepsGoal choisit d’après l’analyse de ton téléphone",
    "quality" to "Tous les effets, fonds animés et verre givré",
    "balanced" to "Effets allégés, fonds animés plus lents",
    "saver" to "Fonds figés, sans flou ni effets : le plus fluide et le plus économe",
)

/** Paramètres › Performance : mode d'ensemble, options une à une et analyse du téléphone. */
@Composable
fun PerformancePage(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current
    val f = Perf.flags
    var testing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Analyse
        Glass {
            CardHead("Analyse du téléphone")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ScoreRing(Perf.score)
                Column(Modifier.weight(1f)) {
                    Text(if (Perf.score < 0) "Pas encore analysé" else "Recommandé : ${Perf.MODES.first { it.first == Perf.recommended }.second}", fontWeight = FontWeight.ExtraBold)
                    Muted(Perf.report.ifEmpty { "Une estimation d’après le matériel est déjà appliquée. L’analyse complète mesure aussi la fluidité réelle de l’affichage (3 secondes)." }, small = true)
                }
            }
            if (testing) Bench { fps, jank, hz ->
                Perf.saveAnalysis(ctx, fps, jank, hz); testing = false; ui.themeTick++
                ui.toast("Analyse terminée : mode ${Perf.MODES.first { it.first == Perf.recommended }.second} recommandé.", "gauge")
            }
            else PButton(if (Perf.score < 0) "Analyser mon téléphone" else "Relancer l’analyse", { testing = true }, Modifier.padding(top = 12.dp), icon = "gauge", kind = "primary", small = true)
        }
        // Mode
        Glass {
            CardHead("Mode")
            Perf.MODES.forEach { (k, l) ->
                val on = Perf.mode == k
                Row(Modifier.fillMaxWidth().clip(smoothShape(16.dp)).background(if (on) p.flame.copy(alpha = .14f) else Color.Transparent)
                    .clickable { Perf.setMode(ctx, k); ui.themeTick++ }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(if (on) p.flame else p.ink.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(p.bg))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (k == "auto") "Auto · ${Perf.MODES.first { it.first == Perf.recommended }.second}" else l, fontWeight = FontWeight.ExtraBold)
                        Muted(MODE_DESC[k] ?: "", small = true)
                    }
                }
            }
        }
        // Options une à une
        Glass {
            CardHead("Options")
            Perf.OPTIONS.forEach { (k, l, d) ->
                val on = when (k) { "bgAnim" -> f.bgAnim; "blur" -> f.blur; "fx" -> f.fx; "rich" -> f.rich; else -> f.shadows }
                SetRow(l + if (Perf.isOverridden(k)) " ·  personnalisé" else "", d) { PSwitch(on, { v -> Perf.setOption(ctx, k, v); ui.themeTick++ }) }
            }
            PButton("Revenir aux réglages du mode", { Perf.setMode(ctx, Perf.mode); ui.themeTick++ }, Modifier.padding(top = 6.dp), small = true, kind = "ghost")
            Muted("Ces réglages sont propres à cet appareil. Les animations réduites (Accessibilité) coupent aussi toutes les animations.", Modifier.padding(top = 8.dp), small = true)
        }
    }
}

/** Anneau du score (0 à 100), gris tant qu'aucune analyse n'a été faite. */
@Composable
private fun ScoreRing(score: Int) {
    val p = LocalPal.current
    val col = when { score < 0 -> p.ink3; score >= 70 -> p.ok; score >= 45 -> Color(0xFFFFB000); else -> p.danger }
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 7.dp.toPx()
            drawArc(p.ink.copy(alpha = .1f), -90f, 360f, false, style = Stroke(w), topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w))
            if (score > 0) drawArc(col, -90f, 360f * score / 100f, false, style = Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round), topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w))
        }
        Text(if (score < 0) "?" else "$score", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
    }
}

/**
 * Test d'affichage : une scène chargée (dégradés, chemins, centaines de formes) redessinée à chaque image pendant
 * 3 secondes. On mesure les images par seconde et la part d'images en retard (plus de 1,5 fois l'intervalle attendu).
 */
@Composable
private fun Bench(done: (fps: Double, jankPct: Double, refreshHz: Float) -> Unit) {
    val p = LocalPal.current
    val view = LocalView.current
    val hz = remember { (view.display?.refreshRate ?: 60f).coerceIn(30f, 165f) }
    var frame by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val times = ArrayList<Long>(600); val t0 = withFrameNanos { it }; var last = t0
        while (isActive) {
            val t = withFrameNanos { it }
            times += t - last; last = t; frame++
            progress = ((t - t0) / 3e9f).coerceAtMost(1f)
            if (t - t0 >= 3_000_000_000L) break
        }
        val total = (last - t0) / 1e9
        val fps = times.size / total
        val budget = 1e9 / hz * 1.5
        val jank = times.count { it > budget } * 100.0 / times.size.coerceAtLeast(1)
        done(fps, jank, hz)
    }
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(140.dp).clip(smoothShape(18.dp))) {
            val fr = frame.toFloat()
            drawRect(Brush.linearGradient(listOf(p.flame, p.pink, Color(0xFF7B5CFF)), Offset(0f, 0f), Offset(size.width * (.5f + .5f * sin(fr / 30f)), size.height)))
            for (i in 0 until 260) {
                val a = i * 0.618f * 2 * PI.toFloat() + fr / 40f
                val r = (i % 40) / 40f * size.minDimension * .6f
                val c = Offset(size.width / 2 + cos(a) * r * 1.4f, size.height / 2 + sin(a) * r * .6f)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .5f), Color.Transparent), c, 18f + i % 7 * 3f), 18f + i % 7 * 3f, c)
            }
            val path = Path(); path.moveTo(0f, size.height)
            var x = 0f
            while (x <= size.width) { path.lineTo(x, size.height * .7f + sin(x / 40f + fr / 8f) * 18f); x += 6f }
            path.lineTo(size.width, size.height); path.close()
            drawPath(path, Color.White.copy(alpha = .35f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(p.ink.copy(alpha = .1f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(p.flame))
            }
            Muted("Test en cours…", small = true)
        }
    }
}
