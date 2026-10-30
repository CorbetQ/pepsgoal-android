package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
            val press = rememberPress(inter)
            Column(Modifier.chunk(if (on) p.selTint(p.flame) else p.surface, if (on) p.focusEdge else p.edge, 17.dp, 3.dp, if (on) 3.dp else 2.dp, press).clip(RoundedCornerShape(17.dp))
                .springClick(inter, label = l) { onPick(i + 1) }.semantics { selected = on }.padding(horizontal = 6.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Emoji(e, if (on) 40.dp else 32.dp)
                Text(l, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (on) p.ink else p.ink2)
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
        CardHead("Ton humeur du jour", emoji = "😊") { IconBtn("book", "Ouvrir le journal", { ui.push(Screen.Journal) }, plain = true) }
        MoodPicker(e?.mood ?: 0) { m -> Journal.put(ctx, t, m, note); open = true; if (e == null) ui.toast("Humeur notée.", "check") }
        // Étiquettes : ce qui colore la journée (reprises dans les corrélations de Stats)
        if (e != null) FlowRowCompat(Modifier.padding(top = 8.dp), gap = 6.dp) {
            Journal.TAGS.forEach { (tg, em) -> FChip("$em $tg", tg in e.tags, { Journal.toggleTag(ctx, t, tg) }) }
        }
        if (open || (e?.text?.isNotEmpty() == true)) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(note, { note = it }, "Un mot sur ta journée (facultatif)", Modifier.weight(1f), max = 1000, singleLine = false)
                IconBtn("check", "Enregistrer la note", { Journal.put(ctx, t, e?.mood ?: 3, note); open = false; ui.toast("Journal mis à jour.", "book") }, primary = true)
            }
        }
    }
}

/** Page Journal : humeur et notes des derniers jours, lien entre humeur et validations. */
@Composable
fun JournalScreen(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current; Journal.rev
    val all = remember(Journal.rev) { Journal.all(ctx).sortedByDescending { it.date } }
    var q by remember { mutableStateOf("") }
    val n = VoiceCmd.norm(q.trim())
    // Recherche dans les notes et les étiquettes
    val list = if (n.length < 2) all else all.filter { e -> VoiceCmd.norm(e.text + " " + e.tags.joinToString(" ")).contains(n) }
    val insight = remember(Journal.rev) { Journal.insight(ctx) }
    ScreenFrame(ui, "Journal", "Retour", { ui.back() }, icon = "book", color = p.type("measure").c, color2 = p.type("period").c) {
        item("today") { MoodCard(ui) }
        if (all.size >= 2) item("cal") { MoodCalendar(all) }
        if (all.size >= 3) item("search") { Field(q, { q = it }, "", placeholder = "Rechercher dans le journal…", max = 40, icon = "search") }
        if (n.length >= 2 && list.isEmpty()) item("none") { Glass { Empty("Rien trouvé", "Aucune note ne contient « ${q.trim()} ».", icon = "search") } }
        if (list.size >= 3 && n.length < 2) item("chart") {
            Glass {
                CardHead("Humeur des 30 derniers jours", emoji = "🌈")
                val last = list.take(30).reversed()
                Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    last.forEach { e -> Box(Modifier.weight(1f).fillMaxHeight(e.mood / 5f).clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.lerp(p.type("deadline").c, p.type("routine").c, (e.mood - 1) / 4f))) }
                }
                insight?.let { Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pi("sparkle", size = 15.dp, tint = p.flameInk); Muted(it, small = true) } }
            }
        }
        if (all.isEmpty()) item("empty") { Glass { Empty("Ton journal est vide", "Note ton humeur chaque jour : tu verras ce qui t’aide à avancer.", icon = "book") } }
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

/** Calendrier d'humeur : un mois, chaque jour coloré selon l'humeur notée. */
@Composable
private fun MoodCalendar(all: List<fr.pepsgoal.app.data.JournalEntry>) {
    val p = LocalPal.current
    var off by remember { mutableIntStateOf(0) }
    val m0 = todayDate().withDayOfMonth(1).plusMonths(off.toLong())
    val byDay = remember(all) { all.associateBy { it.date } }
    val lead = wdIdx(m0); val days = m0.lengthOfMonth()
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn("chevronleft", "Mois précédent", { off-- }, plain = true, size = 36.dp)
            Text(cap(monthName("${m0.year}-${pad(m0.monthValue)}")) + " ${m0.year}", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Box(Modifier.rotate(180f)) { IconBtn("chevronleft", "Mois suivant", { if (off < 0) off++ }, plain = true, size = 36.dp) }
        }
        Spacer(Modifier.height(6.dp))
        Row { WD.forEach { Text(it, Modifier.weight(1f), fontSize = 11.sp, color = p.ink3, fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
        val cells = lead + days; val rows = (cells + 6) / 7
        for (r in 0 until rows) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (c in 0 until 7) {
                val i = r * 7 + c - lead + 1
                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    if (i in 1..days) {
                        val k = ymd(m0.withDayOfMonth(i)); val e = byDay[k]
                        val col = e?.let { androidx.compose.ui.graphics.lerp(p.type("deadline").c, p.type("routine").c, (it.mood - 1) / 4f) }
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(col?.let { vivid(it) } ?: p.surface2)
                            .then(if (k == today()) Modifier.border(3.dp, p.focusEdge, RoundedCornerShape(6.dp)) else Modifier), contentAlignment = Alignment.Center) {
                            if (e != null) Text(Journal.MOODS.getOrNull(e.mood - 1)?.second ?: "", fontSize = 14.sp)
                            else Text("$i", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = p.ink3)
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Journal.MOODS.forEachIndexed { i, (l, _) -> Box(Modifier.size(12.dp).background(vivid(androidx.compose.ui.graphics.lerp(p.type("deadline").c, p.type("routine").c, i / 4f)), RoundedCornerShape(3.dp))) }
            Muted("de « Difficile » à « Super »", small = true)
        }
    }
}
