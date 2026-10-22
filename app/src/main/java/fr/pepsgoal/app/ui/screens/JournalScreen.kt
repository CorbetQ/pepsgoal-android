package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.data.Journal
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*

/** Sélecteur d'humeur : cinq visages, celui choisi grandit. */
@Composable
fun MoodPicker(value: Int, onPick: (Int) -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Journal.MOODS.forEachIndexed { i, (l, e) ->
            val on = value == i + 1
            val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Column(Modifier.bounce(inter, .85f).clip(RoundedCornerShape(16.dp)).background(if (on) p.flame.copy(alpha = .18f) else Color.Transparent)
                .springClick(inter, label = l) { onPick(i + 1) }.padding(horizontal = 6.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Emoji(e, if (on) 40.dp else 32.dp)
                Text(l, fontSize = 11.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, color = if (on) p.ink else p.ink2)
            }
        }
    }
}

/** Carte « Ton humeur du jour » de l'accueil. */
@Composable
fun MoodCard(ui: AppState) {
    val ctx = LocalContext.current; Journal.rev
    val t = today(); val e = remember(Journal.rev) { Journal.get(ctx, t) }
    var note by remember(e) { mutableStateOf(e?.text ?: "") }
    var open by remember { mutableStateOf(false) }
    Glass {
        CardHead("Ton humeur du jour") { IconBtn("book", "Ouvrir le journal", { ui.push(Screen.Journal) }, plain = true, size = 36.dp) }
        MoodPicker(e?.mood ?: 0) { m -> Journal.put(ctx, t, m, note); open = true; if (e == null) ui.toast("Humeur notée.", "check") }
        // Étiquettes : ce qui colore la journée (reprises dans les corrélations de Stats)
        if (e != null) FlowRowCompat(Modifier.padding(top = 8.dp), gap = 6.dp) {
            Journal.TAGS.forEach { (tg, em) -> FChip("$em $tg", tg in e.tags, { Journal.toggleTag(ctx, t, tg) }) }
        }
        if (open || (e?.text?.isNotEmpty() == true)) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(note, { note = it }, "Un mot sur ta journée (facultatif)", Modifier.weight(1f), max = 1000, singleLine = false)
                IconBtn("check", "Enregistrer la note", { Journal.put(ctx, t, e?.mood ?: 3, note); open = false; ui.toast("Journal mis à jour.", "book") }, size = 44.dp)
            }
        }
    }
}

/** Page Journal : humeur et notes des derniers jours, lien entre humeur et validations. */
@Composable
fun JournalScreen(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current; Journal.rev
    val list = remember(Journal.rev) { Journal.all(ctx).sortedByDescending { it.date } }
    val insight = remember(Journal.rev) { Journal.insight(ctx) }
    ScreenFrame(ui, "Journal", "Retour", { ui.back() }, icon = "book", color = p.type("measure").c, color2 = p.type("period").c) {
        item("today") { MoodCard(ui) }
        if (list.size >= 3) item("chart") {
            Glass {
                CardHead("Humeur des 30 derniers jours")
                val last = list.take(30).reversed()
                Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    last.forEach { e -> Box(Modifier.weight(1f).fillMaxHeight(e.mood / 5f).clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.lerp(p.type("deadline").c, p.type("routine").c, (e.mood - 1) / 4f))) }
                }
                insight?.let { Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pi("sparkle", size = 15.dp, tint = p.flame); Muted(it, small = true) } }
            }
        }
        if (list.isEmpty()) item("empty") { Glass { Empty("Ton journal est vide", "Note ton humeur chaque jour : tu verras ce qui t’aide à avancer.", icon = "book") } }
        list.forEach { e -> item("e-${e.date}") {
            Glass(pad = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Emoji(Journal.MOODS.getOrNull(e.mood - 1)?.second ?: "😐", 34.dp)
                    Column(Modifier.weight(1f)) {
                        Text(cap(weekdayLongDate(parseYmd(e.date))), fontWeight = FontWeight.ExtraBold)
                        val r = Journal.dayRate(ctx, e.date)
                        Muted("${Journal.MOODS.getOrNull(e.mood - 1)?.first ?: ""}${r?.let { " · ${Math.round(it * 100)} % des objectifs validés" } ?: ""}", small = true)
                    }
                    IconBtn("trash", "Supprimer cette journée du journal", { ui.confirm("Supprimer cette page ?", "L’humeur et la note du ${fmtDate(e.date)} seront effacées.", "Supprimer") { Journal.remove(ctx, e.date) } }, plain = true, size = 36.dp)
                }
                if (e.tags.isNotEmpty()) FlowRowCompat(Modifier.padding(top = 8.dp), gap = 4.dp) { e.tags.forEach { tg -> Chip("${Journal.TAGS.firstOrNull { it.first == tg }?.second ?: "🏷️"} $tg") } }
                if (e.text.isNotBlank()) Text(e.text, Modifier.padding(top = 8.dp), fontSize = 14.sp)
            }
        } }
    }
}
