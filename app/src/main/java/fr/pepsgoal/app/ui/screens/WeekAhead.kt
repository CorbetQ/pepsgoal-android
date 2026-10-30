package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.PLUM
import fr.pepsgoal.core.*

/*
 * Prévisions de série : carte d'accueil « Semaine à venir ». Les 7 prochains jours en pastilles (repos, calme, risque
 * moyen, risque élevé), puis les jours à risque avec leur raison et le plan B proposé à l'avance. Calcul dans le
 * cœur (StreakForecast.kt), avec la météo de la ville si elle est choisie.
 */

/** Prévisions des 7 prochains jours, recalculées quand la base change (ou le jour). */
@Composable
fun rememberForecast(ui: AppState): List<RiskDay> {
    val ctx = LocalContext.current
    val wx = ui.pg.me()?.settings?.weather?.let { Wx.cached(ctx, it)?.data }
    return remember(ui.rev, today(), wx) { ui.pg.streakForecast(wx) }
}

@Composable
fun WeekAheadCard(ui: AppState) {
    val p = LocalPal.current
    val f = rememberForecast(ui).takeIf { it.isNotEmpty() } ?: return
    val risky = f.filter { it.level == "high" || it.level == "medium" }.sortedByDescending { it.risk }.take(3).sortedBy { it.date }
    Glass {
        CardHead("Semaine à venir", emoji = "🔮") { if (risky.isNotEmpty()) Count(pl(risky.size, "jour à risque", "jours à risque")) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            f.forEach { r ->
                val d = parseYmd(r.date)
                val (fill, ink) = when (r.level) {
                    "high" -> vivid(p.type("duel").c) to PLUM
                    "medium" -> vivid(p.type("monthly").c) to PLUM
                    "rest" -> p.surface2 to p.ink2
                    else -> p.selTint(p.type("routine").c) to p.ink
                }
                val lbl = when (r.level) { "high" -> "risque élevé"; "medium" -> "risque moyen"; "rest" -> "repos"; else -> "calme" }
                Column(Modifier.weight(1f).chunk(fill, p.edge, 17.dp, 3.dp, 2.dp).padding(vertical = 6.dp).semantics(mergeDescendants = true) { contentDescription = "${weekdayLongDate(d)} : ${tr(lbl)}" },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(WD[wdIdx(d)], fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = ink)
                    Text("${d.dayOfMonth}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ink)
                    Emoji(when (r.level) { "high" -> "⚠️"; "medium" -> "🌥️"; "rest" -> "😴"; else -> "✅" }, 16.dp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (risky.isEmpty()) Muted("Aucun jour à risque repéré : garde ton rythme, ta série est bien partie.", small = true)
        else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            risky.forEach { r ->
                val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val press = rememberPress(inter)
                Column(Modifier.fillMaxWidth().chunk(p.surface, p.edge, 18.dp, 3.dp, 2.dp, press).clip(RoundedCornerShape(18.dp))
                    .springClick(inter, label = "Ouvrir l’objectif") { r.goal?.let { ui.open(Sheet.Goal(it.id)) } }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(cap(weekdayLongDate(parseYmd(r.date))), Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Chip(if (r.level == "high") "Risque élevé" else "Risque moyen", "flag", if (r.level == "high") p.type("duel").c else p.type("monthly").c)
                    }
                    r.reasons.firstOrNull()?.let { Muted(it, small = true) }
                    r.idea?.let { Text("💡 $it", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink) }
                }
            }
        }
        if (f.any { it.level == "rest" }) { Spacer(Modifier.height(6.dp)); Muted("Les jours de repos et de vacances protègent ta série.", small = true) }
    }
}
