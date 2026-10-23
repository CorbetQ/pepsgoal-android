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

private val Flame = Color(0xFFFF4A12); private val Gold = Color(0xFFFFB000); private val Mint = Color(0xFF00D1B2)
private val Ink2 = Color(0xFFC9CCF2)

/** Écran de la montre : série, avancement du jour, validation d'un geste et « + » des caps quotidiens. */
class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colors = Colors(primary = Flame, secondary = Gold)) { Screen() } }
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
                        Text("🔥 ${d?.streak ?: "–"}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (d?.lit == true) Gold else Color.White)
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
                            label = { Text("${g.icon} ${g.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                            secondaryLabel = { Text(if (cap) "${fmt(g.v)} / ${fmt(g.target)} ${g.unit} · +${fmt(g.step)}" else if (g.done) "Validé ✓" else g.meta, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = ChipDefaults.chipColors(backgroundColor = if (g.done) (if (cap) Mint else Flame).copy(alpha = .45f) else MaterialTheme.colors.surface),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } }
                }
            }
        }
    }

    private fun fmt(v: Double) = if (v == Math.floor(v)) "%,d".format(v.toLong()).replace(',', ' ') else "%.1f".format(v).replace('.', ',')
}
