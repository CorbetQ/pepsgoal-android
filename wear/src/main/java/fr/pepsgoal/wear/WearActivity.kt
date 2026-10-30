package fr.pepsgoal.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import kotlinx.coroutines.launch

// Palette Braise Pop (sombre) : braise, or, nuit prune, surface, encres crème et prune
private val Flame = Color(0xFFFF4A12); private val Gold = Color(0xFFFFB000)
private val Night = Color(0xFF170F1C); private val Surface = Color(0xFF241A2B); private val Control = Color(0xFF8C7584)
private val Cream = Color(0xFFFFF4EC); private val Ink2 = Color(0xFFD9C4CF); private val Plum = Color(0xFF2A1420)
/** Néon de chaque type (aplat d'un objectif fait, texte prune). */
private val NEON = mapOf("objective" to 0xFF22D3FF, "deadline" to 0xFFFF4FA3, "routine" to 0xFFB6FF2E, "abstinence" to 0xFFA98BFF, "monthly" to 0xFFFFD23F,
    "limit" to 0xFFFF8A3D, "duration" to 0xFF2EF2B0, "project" to 0xFF4D9BFF, "measure" to 0xFFFF7AD9, "group" to 0xFF7CFF6B, "duel" to 0xFFFF5A5A,
    "challenge" to 0xFFFFB000, "program" to 0xFF7C8CFF, "timed" to 0xFFE3B98A, "period" to 0xFF7DD3FC, "cap" to 0xFF00D1B2)
private fun neon(t: String) = Color(NEON[t] ?: 0xFFB6FF2E)

/** Écran de la montre : série, avancement du jour, validation d'un geste et « + » des caps quotidiens. */
class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colors = Colors(primary = Flame, secondary = Gold, background = Night, surface = Surface, onPrimary = Plum, onSecondary = Plum, onSurface = Cream, onBackground = Cream)) { Screen() } }
    }

    @Composable
    private fun Screen() {
        val ctx = this; val scope = rememberCoroutineScope()
        val day by Today.state.collectAsState()
        var note by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) { Today.load(ctx); Today.send(ctx, "/pepsgoal/refresh") }
        val listState = rememberScalingLazyListState()
        // Couronne (ou lunette) rotative : fait défiler la liste ; l'objectif amené au centre se valide d'une touche
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(listState) }) {
            ScalingLazyColumn(Modifier.fillMaxSize()
                .onRotaryScrollEvent { e -> scope.launch { listState.scrollBy(e.verticalScrollPixels) }; true }
                .focusRequester(focus).focusable(), state = listState, horizontalAlignment = Alignment.CenterHorizontally) {
                val d = day
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔥 ${d?.streak ?: "–"}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (d?.lit == true) Gold else Cream)
                        Text(when { d == null -> "Ouvre PepsGoal sur ton téléphone"; !d.signedIn -> "Connecte-toi sur le téléphone"; d.rest -> "Jour de repos 😴"; else -> "${if (d.streak > 1) "jours" else "jour"} de série · ${d.done}/${d.total}" },
                            fontSize = 12.sp, color = Ink2, textAlign = TextAlign.Center)
                    }
                }
                note?.let { n -> item { Text(n, fontSize = 11.sp, color = Gold, textAlign = TextAlign.Center) } }
                if (d != null && d.signedIn) {
                    if (d.items.isEmpty()) item { Text("Rien de prévu aujourd’hui", fontSize = 13.sp, color = Ink2) }
                    d.items.forEach { g -> item(g.id) {
                        val cap = g.type == "cap"
                        Chip(
                            onClick = {
                                scope.launch {
                                    val ok = when { cap -> Today.send(ctx, "/pepsgoal/cap", g.id); g.simple -> Today.send(ctx, "/pepsgoal/validate", g.id); else -> { note = "À saisir sur le téléphone"; return@launch } }
                                    note = if (ok) null else "Téléphone injoignable"
                                    // Vibration de réussite (double impulsion) quand la validation part vers le téléphone
                                    if (ok && !g.done) try { ctx.getSystemService(android.os.Vibrator::class.java)?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 40, 70, 60), intArrayOf(0, 180, 0, 255), -1)) } catch (e: Exception) {}
                                }
                            },
                            label = { Text("${g.icon} ${g.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, color = if (g.done) Plum else Cream) },
                            secondaryLabel = { Text(if (cap) "${fmt(g.v)} / ${fmt(g.target)} ${g.unit} · +${fmt(g.step)}" else if (g.done) "Validé ✓" else g.meta, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (g.done) Plum else Ink2) },
                            // À faire : surface cerclée de control ; fait : aplat néon du type, texte prune (aucune transparence)
                            colors = ChipDefaults.chipColors(backgroundColor = if (g.done) neon(g.type) else Surface, contentColor = if (g.done) Plum else Cream),
                            border = ChipDefaults.chipBorder(borderStroke = if (g.done) null else androidx.compose.foundation.BorderStroke(2.dp, Control)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } }
                }
            }
        }
    }

    private fun fmt(v: Double) = if (v == Math.floor(v)) "%,d".format(v.toLong()).replace(',', ' ') else "%.1f".format(v).replace('.', ',')
}
