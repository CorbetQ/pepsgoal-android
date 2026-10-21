package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import fr.pepsgoal.core.TYPE_GROUPS
import fr.pepsgoal.core.model.Goal

@Composable
fun ChipsRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun GoalsScreen(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val all = pg.myGoals(u.id); val present = all.map { it.type }.toSet()
    val filters = mutableListOf("all" to "Tous")
    if (all.any { pg.isFav(it) }) filters.add("fav" to "★ Favoris")
    TYPE_GROUPS.flatMap { it.second }.filter { it in present }.forEach { filters.add(it to TYPES.getValue(it).label) }
    filters.add("done" to "Terminés et archivés")
    if (ui.goalFilter !in filters.map { it.first }) ui.goalFilter = "all"
    if (ui.groupFilter != "all" && ui.groupFilter != "none" && u.goalGroups.none { it.id == ui.groupFilter }) ui.groupFilter = "all"
    val inGroup = { g: Goal -> ui.groupFilter == "all" || (pg.groupOfGoal(u, g.id)?.id ?: "none") == ui.groupFilter }
    val list = all.filter { g -> (if (ui.goalFilter == "done") pg.isClosedGoal(g) else !pg.isClosedGoal(g) && (ui.goalFilter == "all" || g.type == ui.goalFilter || (ui.goalFilter == "fav" && pg.isFav(g)))) && inGroup(g) }
        .sortedByDescending { pg.isFav(it) }
    var collapsed by remember { mutableStateOf(ui.prefs.collapsed) }
    ScreenList {
        item("filters") { ChipsRow { filters.forEach { (k, l) -> FChip(l, ui.goalFilter == k, { ui.goalFilter = k }) } } }
        if (all.isNotEmpty()) item("groups") {
            val open = all.filter { !pg.isClosedGoal(it) }
            val cnt = { id: String -> open.count { (pg.groupOfGoal(u, it.id)?.id ?: "none") == id } }
            ChipsRow {
                FChip("Tous les groupes", ui.groupFilter == "all", { ui.groupFilter = "all" }, leading = { Pi("folder", size = 15.dp) })
                u.goalGroups.forEach { gr -> FChip("${gr.icon} ${gr.name} ${cnt(gr.id)}", ui.groupFilter == gr.id, { ui.groupFilter = gr.id }) }
                if (u.goalGroups.isNotEmpty()) FChip("Sans groupe ${cnt("none")}", ui.groupFilter == "none", { ui.groupFilter = "none" })
                FChip(if (u.goalGroups.isNotEmpty()) "Gérer" else "Créer des groupes", false, { ui.open(Sheet.GoalGroups) }, leading = { Pi(if (u.goalGroups.isNotEmpty()) "edit" else "plus", size = 15.dp) }, dashed = true)
            }
        }
        when {
            list.isEmpty() && all.isNotEmpty() -> item("empty") { Glass { Empty("Rien ici pour l'instant", "Change de filtre ou crée un nouvel objectif.") { PButton("Nouvel objectif", { ui.open(Sheet.GoalForm(preType = ui.goalFilter.takeIf { it in TYPES })) }, icon = "plus", kind = "primary") } } }
            list.isEmpty() -> item("start") { Glass { CardHead("Que veux-tu suivre ?"); Starters(ui) } }
            ui.groupFilter != "all" || u.goalGroups.isEmpty() -> items(list) { g -> GoalCard(ui, g) }
            else -> {
                val secs = u.goalGroups.map { gr -> Triple(gr.id, "${gr.icon}|${gr.name}", list.filter { pg.groupOfGoal(u, it.id)?.id == gr.id }) } +
                    Triple("none", "🗂️|Sans groupe", list.filter { pg.groupOfGoal(u, it.id) == null })
                secs.filter { it.third.isNotEmpty() }.forEach { (key, label, items) ->
                    val (ic, name) = label.split("|", limit = 2)
                    val c = key in collapsed
                    item("sec-$key") {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { collapsed = if (c) collapsed - key else collapsed + key; ui.prefs.collapsed = collapsed }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(p.glassStrong).border(1.dp, p.line, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { Emoji(ic, 20.dp) }
                            Text(name, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Count(items.size)
                            Spacer(Modifier.weight(1f))
                            Text(if (c) "▸" else "▾", color = p.ink3)
                        }
                    }
                    if (!c) items(items, key = { "g-" + it.id }) { g -> GoalCard(ui, g) }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(list: List<Goal>, key: ((Goal) -> Any)? = { it.id }, content: @Composable (Goal) -> Unit) {
    items(list.size, key = key?.let { k -> { i: Int -> k(list[i]) } }) { i -> content(list[i]) }
}
