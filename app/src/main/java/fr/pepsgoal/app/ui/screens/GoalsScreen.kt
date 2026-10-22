package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.TYPES
import fr.pepsgoal.core.*
import fr.pepsgoal.core.TYPE_GROUPS
import fr.pepsgoal.core.model.Goal

@Composable
fun ChipsRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/**
 * Onglet Objectifs : vue d'ensemble, recherche, filtres par état et par type, tri, regroupement et deux affichages
 * (liste compacte avec validation directe, ou cartes détaillées).
 */
@Composable
fun GoalsScreen(ui: AppState) {
    ui.watch(); ui.themeTick
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val all = pg.myGoals(u.id)
    val open = all.filter { !pg.isClosedGoal(it) }
    val plan = pg.todayPlan(u.id); val dueIds = plan.due.associate { it.g.id to it.done }
    val doneToday = plan.due.count { it.done }
    // État de l'écran (gardé pendant la session ; affichage et regroupement mémorisés)
    if (ui.goalFilter !in setOf("active", "today", "fav", "done")) ui.goalFilter = "active"
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf("fav") }
    var compact by remember { mutableStateOf(ui.prefs.goalView != "cards") }
    var grouped by remember { mutableStateOf(ui.prefs.goalGroupBy) }
    var collapsed by remember { mutableStateOf(ui.prefs.collapsed) }
    var aiOpen by remember { mutableStateOf(false) }
    val aiOn = fr.pepsgoal.app.ai.LocalAi.on(androidx.compose.ui.platform.LocalContext.current, ui.prefs.aiCreate)

    val base = when (ui.goalFilter) {
        "today" -> open.filter { it.id in dueIds }
        "fav" -> open.filter { pg.isFav(it) }
        "done" -> all.filter { pg.isClosedGoal(it) }
        else -> open
    }
    val types = TYPE_GROUPS.flatMap { it.second }.filter { t -> base.any { it.type == t } }
    if (type != "all" && type !in types) type = "all"
    val q = query.trim().lowercase()
    val list = base.filter { (type == "all" || it.type == type) && (q.isEmpty() || it.title.lowercase().contains(q) || it.note.lowercase().contains(q)) }
        .let { l -> when (sort) {
            "az" -> l.sortedBy { it.title.lowercase() }
            "progress" -> l.sortedByDescending { pg.barPct(it) ?: -1.0 }
            "recent" -> l.sortedByDescending { it.createdAt }
            else -> l.sortedWith(compareByDescending<Goal> { pg.isFav(it) }.thenBy { dueIds[it.id] ?: true }.thenBy { it.title.lowercase() })
        } }

    ScreenList {
        // Vue d'ensemble : trois raccourcis
        item("overview") {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverTile("${open.size}", "en cours", "target", ui.goalFilter == "active", Modifier.weight(1f)) { ui.goalFilter = "active" }
                OverTile("$doneToday/${plan.due.size}", "aujourd’hui", "sun", ui.goalFilter == "today", Modifier.weight(1f)) { ui.goalFilter = "today" }
                OverTile("${open.count { pg.isFav(it) }}", "favoris", "star", ui.goalFilter == "fav", Modifier.weight(1f)) { ui.goalFilter = "fav" }
            }
        }
        // Recherche, affichage et tri
        item("tools") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(query, { query = it }, "", Modifier.weight(1f), placeholder = "Rechercher un objectif", max = 60, icon = "search")
                IconBtn(if (compact) "list" else "grip", if (compact) "Afficher en cartes" else "Afficher en liste", { compact = !compact; ui.prefs.goalView = if (compact) "list" else "cards" }, size = 46.dp)
            }
        }
        item("filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipsRow {
                    FChip("Terminés et archivés", ui.goalFilter == "done", { ui.goalFilter = if (ui.goalFilter == "done") "active" else "done" }, leading = { Pi("archive", size = 15.dp) })
                    SORTS.forEach { (k, l) -> FChip(l, sort == k, { sort = k }) }
                    if (u.goalGroups.isNotEmpty()) FChip("Par groupe", grouped, { grouped = !grouped; ui.prefs.goalGroupBy = grouped }, leading = { Pi("folder", size = 15.dp) })
                    FChip(if (u.goalGroups.isNotEmpty()) "Gérer les groupes" else "Créer des groupes", false, { ui.open(Sheet.GoalGroups) }, leading = { Pi(if (u.goalGroups.isNotEmpty()) "edit" else "plus", size = 15.dp) }, dashed = true)
                }
                if (types.size > 1) ChipsRow {
                    FChip("Tous les types", type == "all", { type = "all" })
                    types.forEach { t -> FChip(TYPES.getValue(t).label, type == t, { type = t }, leading = { Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(p.type(t).c)) }) }
                }
            }
        }
        // Créer avec le coach : pastille compacte qui s'ouvre à la demande
        if (aiOn) item("aicreate") {
            if (aiOpen) AiCreateCard(ui)
            else Glass(pad = 12.dp, onClick = { aiOpen = true }, ai = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pi("sparkle", size = 18.dp, tint = p.flame); Text("Créer un objectif avec le coach…", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = p.ink2)
                    Text("›", color = p.ink3, fontSize = 20.sp)
                }
            }
        }
        when {
            all.isEmpty() -> item("start") { Glass { CardHead("Que veux-tu suivre ?"); Starters(ui) } }
            list.isEmpty() -> item("empty") { Glass { Empty(if (q.isNotEmpty()) "Aucun résultat pour « ${query.trim()} »" else "Rien ici pour l’instant", if (q.isNotEmpty()) "Essaie un autre mot ou change de filtre." else "Change de filtre ou crée un nouvel objectif.") { PButton("Nouvel objectif", { ui.open(Sheet.GoalForm(preType = type.takeIf { it in TYPES })) }, icon = "plus", kind = "primary") } } }
            else -> {
                val secs = if (grouped && u.goalGroups.isNotEmpty()) u.goalGroups.map { gr -> Triple(gr.id, "${gr.icon}|${gr.name}", list.filter { pg.groupOfGoal(u, it.id)?.id == gr.id }) } +
                    Triple("none", "🗂️|Sans groupe", list.filter { pg.groupOfGoal(u, it.id) == null }) else listOf(Triple("all", "", list))
                secs.filter { it.third.isNotEmpty() }.forEach { (key, label, items) ->
                    val c = key in collapsed
                    if (label.isNotEmpty()) item("sec-$key") {
                        val (ic, name) = label.split("|", limit = 2)
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { collapsed = if (c) collapsed - key else collapsed + key; ui.prefs.collapsed = collapsed }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(p.glassStrong).border(1.dp, p.line, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { Emoji(ic, 20.dp) }
                            Text(name, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Count(items.size)
                            Spacer(Modifier.weight(1f))
                            Pi(if (c) "chevronleft" else "down", size = 18.dp, tint = p.ink3)
                        }
                    }
                    if (!c) {
                        if (compact) item("list-$key") {
                            // Liste compacte : une carte par section, une ligne par objectif, validation en un geste
                            Glass(pad = 8.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    items.forEach { g -> key(g.id) { GoalRow(ui, g, dueIds[g.id] ?: false) } }
                                }
                            }
                        } else items(items, key = { "g-" + it.id }) { g -> GoalCard(ui, g) }
                    }
                }
            }
        }
    }
}

private val SORTS = listOf("fav" to "Pertinence", "az" to "A → Z", "progress" to "Progression", "recent" to "Récents")

/** Tuile de la vue d'ensemble : chiffre, libellé, et filtre correspondant (enfoncée quand active). */
@Composable
private fun OverTile(v: String, l: String, icon: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    Glass(modifier.fillMaxHeight(), pad = 12.dp, fill = false, onClick = onClick, border = if (on) p.flame else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pi(icon, size = 16.dp, tint = if (on) p.flame else p.ink2)
            Text(v, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1)
        }
        Text(l, color = if (on) p.ink else p.ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(list: List<Goal>, key: ((Goal) -> Any)? = { it.id }, content: @Composable (Goal) -> Unit) {
    items(list.size, key = key?.let { k -> { i: Int -> k(list[i]) } }) { i -> content(list[i]) }
}
