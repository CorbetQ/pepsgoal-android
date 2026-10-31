package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.sys.FpsMonitor
import fr.pepsgoal.app.sys.Perf
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.BRAISE
import fr.pepsgoal.app.ui.theme.CREAM
import fr.pepsgoal.app.ui.theme.PLUM
import fr.pepsgoal.app.ui.theme.step
import fr.pepsgoal.core.QualitySteps
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val MODE_DESC = mapOf(
    "auto" to "PepsGoal choisit d’après l’analyse de ton téléphone",
    "quality" to "Tous les effets et les fonds animés",
    "balanced" to "Effets allégés, fonds animés plus lents",
    "saver" to "Fonds figés, sans effets : le plus fluide et le plus économe",
)

/** Nom du niveau maximal appliqué (mode choisi, ou recommandation de l'analyse en Auto). */
private fun ceilingName(): String = Perf.MODES.first { it.first == (if (Perf.mode == "auto") Perf.recommended else Perf.mode) }.second

/**
 * Paramètres › Performances : état en direct (images par seconde mesurées, niveau appliqué, score de l'analyse),
 * historique récent, régulateur de fluidité, analyse du téléphone, plafond de qualité et options une à une.
 */
@Composable
fun PerformancePage(ui: AppState) {
    val ctx = LocalContext.current
    val f = Perf.flags
    var testing by remember { mutableStateOf(false) }
    // Mesure en direct tant que la page est ouverte (même régulateur coupé)
    DisposableEffect(Unit) { FpsMonitor.want(FpsMonitor.PAGE, true); onDispose { FpsMonitor.want(FpsMonitor.PAGE, false); Perf.benchRunning = false } }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LiveCard()
        // Régulateur de fluidité
        Glass {
            CardHead("Régulateur de fluidité", emoji = "🎚️")
            SetRow("Ajuster la qualité tout seul", "Quand l’affichage ralentit, les effets s’allègent par paliers, puis reviennent quand tout est fluide") {
                PSwitch(Perf.autoReg, { v -> Perf.setAutoReg(ctx, v) })
            }
            if (Perf.autoReg) {
                Spacer(Modifier.height(6.dp))
                StepsList(Perf.regLevel)
                Muted("Sous 85 % de la fréquence de l’écran pendant 3 secondes : un palier de moins. Fluide au-dessus de 95 % pendant 10 secondes : un palier de plus. Le régulateur ne dépasse jamais le niveau maximal choisi plus bas.",
                    Modifier.padding(top = 10.dp), small = true)
            } else Muted("Coupé : la qualité reste toujours au niveau maximal choisi plus bas.", Modifier.padding(top = 6.dp), small = true)
        }
        // Analyse du téléphone (score du test d'affichage)
        Glass {
            CardHead("Analyse du téléphone", emoji = "🔬")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ScoreRing(Perf.score)
                Column(Modifier.weight(1f)) {
                    Text(if (Perf.score < 0) "Pas encore analysé" else "Recommandé : ${Perf.MODES.first { it.first == Perf.recommended }.second}", fontWeight = FontWeight.ExtraBold)
                    Muted(Perf.report.ifEmpty { "Une estimation d’après le matériel est déjà appliquée. L’analyse complète mesure aussi la fluidité réelle de l’affichage (3 secondes)." }, small = true)
                }
            }
            if (testing) Bench { fps, jank, hz ->
                Perf.benchRunning = false
                Perf.saveAnalysis(ctx, fps, jank, hz); testing = false; ui.themeTick++
                ui.toast("Analyse terminée : mode ${Perf.MODES.first { it.first == Perf.recommended }.second} recommandé.", "gauge")
            }
            else PButton(if (Perf.score < 0) "Analyser mon téléphone" else "Relancer l’analyse", { Perf.benchRunning = true; testing = true }, Modifier.padding(top = 12.dp), icon = "gauge", kind = "primary")
        }
        // Plafond : mode d'ensemble
        Glass {
            CardHead("Niveau maximal", emoji = "🏔️")
            Muted("Le plafond de qualité : le régulateur peut descendre en dessous, jamais au-dessus.", Modifier.padding(bottom = 6.dp), small = true)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Perf.MODES.forEach { (k, l) ->
                    PRadioRow(if (k == "auto") "Auto · ${Perf.MODES.first { it.first == Perf.recommended }.second}" else l, MODE_DESC[k] ?: "", Perf.mode == k,
                        { Perf.setMode(ctx, k); ui.themeTick++ })
                }
            }
        }
        // Options une à une
        Glass {
            CardHead("Options", emoji = "🧩")
            Perf.OPTIONS.forEach { (k, l, d) ->
                val on = when (k) { "bgAnim" -> Perf.ceiling.bgAnim; "blur" -> Perf.ceiling.blur; "fx" -> Perf.ceiling.fx; "rich" -> Perf.ceiling.rich; else -> Perf.ceiling.shadows }
                SetRow(l + if (Perf.isOverridden(k)) " ·  personnalisé" else "", d) { PSwitch(on, { v -> Perf.setOption(ctx, k, v); ui.themeTick++ }) }
            }
            PButton("Revenir aux réglages du mode", { Perf.setMode(ctx, Perf.mode); ui.themeTick++ }, Modifier.padding(top = 6.dp), small = true, kind = "ghost")
            Muted("Ces réglages sont propres à cet appareil. Les animations réduites (Accessibilité) coupent aussi toutes les animations." +
                if (f != Perf.ceiling) " Le régulateur allège en ce moment certains effets." else "", Modifier.padding(top = 8.dp), small = true)
        }
    }
}

/**
 * État en direct : images par seconde mesurées (une mesure par seconde), fréquence de l'écran, état du régulateur,
 * niveau maximal et score, puis historique de la dernière minute. Seule cette carte se recompose à chaque mesure ; le
 * graphique, lui, ne fait que se redessiner.
 */
@Composable
private fun LiveCard() {
    val p = LocalPal.current
    val fps = Perf.liveFps; val hz = Perf.liveHz
    val ratio = if (fps < 0f) 1f else fps / hz
    val col = when { fps < 0f -> p.ink3; ratio >= .95f -> p.ok; ratio >= .85f -> p.type("challenge").c; else -> p.danger }
    val reduced = Perf.regLevel > 0
    Glass {
        CardHead("En ce moment", emoji = "📈")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = if (fps < 0f) "Mesure en cours" else "${fps.roundToInt()} images par seconde sur ${hz.roundToInt()}" }) {
            // Tuile du chiffre : aplat de la couleur d'état, à marche
            Column(Modifier.chunk(col, if (p.contrast) p.ink else col.step(), 22.dp, 4.dp, 0.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (fps < 0f) "…" else "${fps.roundToInt()}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, color = PLUM)
                Text("images/s", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = PLUM)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (fps < 0f) "Mesure en cours…" else "Écran à ${hz.roundToInt()} Hz", fontWeight = FontWeight.ExtraBold)
                Chip(if (!Perf.autoReg) "Régulateur coupé" else Perf.regState(), icon = if (reduced) "gauge" else "check", color = if (!Perf.autoReg) null else if (reduced) p.type("challenge").c else p.ok)
                if (reduced) Muted("Palier ${Perf.regLevel} sur ${QualitySteps.MAX} : ${QualitySteps.LABELS[Perf.regLevel].replaceFirstChar { it.lowercase() }}", small = true)
            }
        }
        Spacer(Modifier.height(12.dp))
        FpsChart()
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Muted("Il y a 1 min", small = true); Spacer(Modifier.weight(1f)); Muted("Maintenant", small = true)
        }
        Spacer(Modifier.height(12.dp))
        StatGrid(listOf(ceilingName() to "niveau maximal", (if (Perf.score < 0) "—" else "${Perf.score}/100") to "score de l’analyse",
            (if (reduced) "${Perf.regLevel}/${QualitySteps.MAX}" else "0") to "palier du régulateur"), columns = 3, emojis = listOf("🏔️", "🔬", "🎚️"))
    }
}

/**
 * Historique des 60 dernières mesures : une barre en aplat par seconde, colorée selon la fluidité (vert ≥ 95 %, jaune ≥
 * 85 %, rouge en dessous), et la ligne des 85 % en pointillés. Lu dans la phase de dessin : aucune recomposition.
 */
@Composable
private fun FpsChart() {
    val p = LocalPal.current
    val ok = p.ok; val warn = p.type("challenge").c; val bad = p.danger; val track = p.surface2; val ink = p.ink3
    val dash = remember { androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) }
    Canvas(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(16.dp)).background(track)
        .semantics { contentDescription = "Historique des images par seconde de la dernière minute" }) {
        if (Perf.histTick < 0) return@Canvas // lecture de l'état : redessin à chaque mesure
        val n = Perf.history.size; val count = Perf.histCount; val head = Perf.histHead
        val hz = Perf.liveHz.coerceAtLeast(30f)
        val pad = 6.dp.toPx(); val h = size.height - pad * 2
        val slot = (size.width - pad * 2) / n; val bw = (slot * .7f).coerceAtLeast(2f)
        val r = CornerRadius(bw / 2, bw / 2)
        for (i in 0 until count) {
            // Plus ancienne à gauche, plus récente à droite
            val v = Perf.history[(head - count + i + n) % n]
            val k = (v / hz).coerceIn(0f, 1f)
            val bh = (h * k).coerceAtLeast(bw)
            val x = pad + (n - count + i) * slot + (slot - bw) / 2
            drawRoundRect(if (k >= .95f) ok else if (k >= .85f) warn else bad, Offset(x, pad + h - bh), Size(bw, bh), r)
        }
        val y85 = pad + h * (1f - .85f)
        drawLine(ink, Offset(pad, y85), Offset(size.width - pad, y85), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
    }
}

/** Les paliers du régulateur, l'actuel mis en avant. */
@Composable
private fun StepsList(level: Int) {
    val p = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        QualitySteps.LABELS.forEachIndexed { i, l ->
            val on = i == level
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) p.selTint(p.flame) else Color.Transparent).padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(if (on) p.flame else if (i < level) p.surface2 else p.surface2), contentAlignment = Alignment.Center) {
                    Text("$i", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (on) PLUM else p.ink2)
                }
                Text(l, Modifier.weight(1f), fontSize = 14.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold, color = if (on || i <= level) p.ink else p.ink2)
                if (on) Text(if (i == 0) "actuel" else "en ce moment", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
            }
        }
    }
}

/** Anneau du score (0 à 100), gris tant qu'aucune analyse n'a été faite. */
@Composable
private fun ScoreRing(score: Int) {
    val p = LocalPal.current
    val col = when { score < 0 -> p.ink3; score >= 70 -> p.ok; score >= 45 -> p.type("challenge").c; else -> p.danger }
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 8.dp.toPx()
            drawCircle(p.control, size.minDimension / 2 - w / 2, style = Stroke(w + 4.dp.toPx()))
            drawArc(p.surface2, -90f, 360f, false, style = Stroke(w), topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w))
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
        Canvas(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(22.dp))) {
            val fr = frame.toFloat()
            drawRect(Brush.linearGradient(BRAISE, Offset(0f, 0f), Offset(size.width * (.5f + .5f * sin(fr / 30f)), size.height)))
            for (i in 0 until 260) {
                val a = i * 0.618f * 2 * PI.toFloat() + fr / 40f
                val r = (i % 40) / 40f * size.minDimension * .6f
                val c = Offset(size.width / 2 + cos(a) * r * 1.4f, size.height / 2 + sin(a) * r * .6f)
                drawCircle(if (i % 3 == 0) CREAM else BRAISE[i % 3], 10f + i % 7 * 3f, c)
            }
            // Test volontairement lourd : un tracé neuf à chaque image (ne pas « optimiser », le score en dépend)
            val path = Path(); path.moveTo(0f, size.height)
            var x = 0f
            while (x <= size.width) { path.lineTo(x, size.height * .7f + sin(x / 40f + fr / 8f) * 18f); x += 6f }
            path.lineTo(size.width, size.height); path.close()
            drawPath(path, PLUM)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Bar(progress * 100.0, Modifier.weight(1f), height = 12.dp)
            Muted("Test en cours…", small = true)
        }
    }
}
