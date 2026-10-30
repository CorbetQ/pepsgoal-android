package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import fr.pepsgoal.app.ui.theme.step
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.TYPES
import fr.pepsgoal.core.*
import fr.pepsgoal.core.TYPE_GROUPS
import fr.pepsgoal.core.model.Goal
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

@Composable
fun ChipsRow(content: @Composable RowScope.() -> Unit) = ChipScroller(bleed = 16.dp, content = content)

/**
 * Onglet Objectifs : vue d'ensemble, recherche, filtres par état et par type, tri, regroupement et deux affichages
 * (liste compacte avec validation directe, ou cartes détaillées).
 */
/** Résultat des filtres de l'onglet Objectifs (partagé par l'écran et la fenêtre Filtrer). */
class GoalView(val all: List<Goal>, val open: List<Goal>, val types: List<String>, val type: String, val group: String, val list: List<Goal>, val active: Int)

/** Applique l'état, le dossier, le type, la recherche et le tri choisis. */
fun goalView(ui: AppState, query: String = ""): GoalView? {
    val pg = ui.pg; val u = pg.me() ?: return null
    val all = pg.myGoals(u.id)
    val open = all.filter { !pg.isClosedGoal(it) }
    val dueIds = pg.todayPlan(u.id).due.associate { it.g.id to it.done }
    if (ui.goalFilter !in setOf("active", "today", "fav", "done")) ui.goalFilter = "active"
    val base = when (ui.goalFilter) {
        "today" -> open.filter { it.id in dueIds }
        "fav" -> open.filter { pg.isFav(it) }
        "done" -> all.filter { pg.isClosedGoal(it) }
        else -> open
    }
    val types = TYPE_GROUPS.flatMap { it.second }.filter { t -> base.any { it.type == t } }
    val type = ui.goalType.takeIf { it in types } ?: "all"
    val gf = ui.groupFilter.takeIf { f -> f == "none" || u.goalGroups.any { it.id == f } } ?: "all"
    val q = query.trim().lowercase()
    val list = base.filter { (type == "all" || it.type == type) && (q.isEmpty() || it.title.lowercase().contains(q) || it.note.lowercase().contains(q)) &&
        (gf == "all" || (gf == "none" && pg.groupOfGoal(u, it.id) == null) || pg.groupOfGoal(u, it.id)?.id == gf) }
        .let { l -> when (ui.goalSort) {
            "az" -> l.sortedBy { it.title.lowercase() }
            "progress" -> l.sortedByDescending { pg.barPct(it) ?: -1.0 }
            "recent" -> l.sortedByDescending { it.createdAt }
            else -> l.sortedWith(compareByDescending<Goal> { pg.isFav(it) }.thenBy { dueIds[it.id] ?: true }.thenBy { it.title.lowercase() })
        } }
    val active = (if (ui.goalFilter == "done") 1 else 0) + (if (gf != "all") 1 else 0) + (if (type != "all") 1 else 0)
    return GoalView(all, open, types, type, gf, list, active)
}

/**
 * Onglet Objectifs : vue d'ensemble, une barre (recherche, Filtrer, Trier, autres actions), les filtres actifs sur une
 * ligne qu'on peut effacer, puis la liste. Filtres et tri vivent dans leurs fenêtres pour laisser la place aux objectifs.
 */
@Composable
fun GoalsScreen(ui: AppState) {
    ui.watch(); ui.themeTick
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    var query by remember { mutableStateOf("") }
    val v = goalView(ui, query) ?: return
    val all = v.all; val open = v.open; val list = v.list; val type = v.type; val gf = v.group; val q = query.trim().lowercase()
    val plan = pg.todayPlan(u.id); val dueIds = plan.due.associate { it.g.id to it.done }
    val doneToday = plan.due.count { it.done }
    val compact = ui.prefs.goalView != "cards"
    val grouped = ui.prefs.goalGroupBy
    var collapsed by remember { mutableStateOf(ui.prefs.collapsed) }
    var aiOpen by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val aiOn = fr.pepsgoal.app.ai.LocalAi.on(androidx.compose.ui.platform.LocalContext.current, ui.prefs.aiCreate)
    val sctx = androidx.compose.ui.platform.LocalContext.current

    ScreenList {
        // Vue d'ensemble : trois raccourcis
        item("overview") {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverTile("${open.size}", "en cours", "🎯", p.type("limit").c, ui.goalFilter == "active", Modifier.weight(1f)) { ui.goalFilter = "active" }
                OverTile("$doneToday/${plan.due.size}", "aujourd’hui", "☀️", p.type("monthly").c, ui.goalFilter == "today", Modifier.weight(1f)) { ui.goalFilter = "today" }
                OverTile("${open.count { pg.isFav(it) }}", "favoris", "⭐", p.type("deadline").c, ui.goalFilter == "fav", Modifier.weight(1f)) { ui.goalFilter = "fav" }
            }
        }
        // Une seule barre : recherche, Filtrer (pastille du nombre de filtres actifs), Trier, et le reste dans « Plus »
        item("tools") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(query, { query = it }, "", Modifier.weight(1f), placeholder = "Rechercher", max = 60, icon = "search")
                IconBtn("filter", if (v.active > 0) "Filtrer (${v.active} actif${if (v.active > 1) "s" else ""})" else "Filtrer", { ui.open(Sheet.GoalFilter) }, badge = v.active)
                IconBtn("sort", "Trier et afficher", { ui.open(Sheet.GoalSort) })
                Box {
                    IconBtn("more", "Plus d’actions", { more = true })
                    PMenu(more, { more = false }, listOf(
                        MenuItem("Ma semaine", icon = "cal") { more = false; ui.push(fr.pepsgoal.app.Screen.Week) },
                        MenuItem("Scanner un objectif partagé", icon = "link") { more = false; fr.pepsgoal.app.ui.sheets.scanGoal(ui, sctx) },
                        MenuItem(if (u.goalGroups.isNotEmpty()) "Gérer les dossiers" else "Créer des dossiers", icon = "folder") { more = false; ui.open(Sheet.GoalGroups) }
                    ), title = "Plus d’actions")
                }
            }
        }
        // Filtres actifs : une seule ligne, chaque puce (croix en tête) s'efface d'un toucher
        if (v.active > 0) item("active") {
            ChipsRow {
                if (ui.goalFilter == "done") FChip("Terminés et archivés", true, { ui.goalFilter = "active" }, leading = { Pi("x", size = 15.dp) })
                if (gf != "all") {
                    val gr = u.goalGroups.firstOrNull { it.id == gf }
                    FChip(if (gr != null) "${gr.icon} ${gr.name}" else "Sans dossier", true, { ui.groupFilter = "all" }, leading = { Pi("x", size = 15.dp) }, color = gr?.color?.let { fr.pepsgoal.app.ui.theme.hex(it) })
                }
                if (type != "all") FChip(TYPES.getValue(type).label, true, { ui.goalType = "all" }, leading = { Pi("x", size = 15.dp) }, color = p.type(type).c)
                if (v.active > 1) FChip("Tout effacer", false, { ui.goalFilter = "active"; ui.groupFilter = "all"; ui.goalType = "all" }, dashed = true)
            }
        }
        // Créer avec le coach : pastille compacte qui s'ouvre à la demande
        if (aiOn) item("aicreate") {
            if (aiOpen) AiCreateCard(ui)
            else Glass(pad = 12.dp, onClick = { aiOpen = true }, ai = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconTile("sparkle", p.flame2, p.pink, 32.dp); Text("Créer un objectif avec le coach…", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = p.ink2)
                    Pi("chevronleft", Modifier.graphicsLayer { rotationZ = 180f }, size = 18.dp, tint = p.ink3)
                }
            }
        }
        when {
            all.isEmpty() -> item("start") { Glass { CardHead("Que veux-tu suivre ?", emoji = "🎯"); Starters(ui) } }
            list.isEmpty() -> item("empty") { Glass { Empty(if (q.isNotEmpty()) "Aucun résultat pour « ${query.trim()} »" else "Rien ici pour l’instant", if (q.isNotEmpty()) "Essaie un autre mot ou change de filtre." else "Change de filtre ou crée un nouvel objectif.") { PButton("Nouvel objectif", { ui.open(Sheet.GoalForm(preType = type.takeIf { it in TYPES })) }, icon = "plus", kind = "primary") } } }
            else -> {
                val secs = if (grouped && u.goalGroups.isNotEmpty()) u.goalGroups.map { gr -> Triple(gr.id, "${gr.icon}|${gr.name}", list.filter { pg.groupOfGoal(u, it.id)?.id == gr.id }) } +
                    Triple("none", "🗂️|Sans groupe", list.filter { pg.groupOfGoal(u, it.id) == null }) else listOf(Triple("all", "", list))
                secs.filter { it.third.isNotEmpty() }.forEach { (key, label, items) ->
                    val c = key in collapsed
                    if (label.isNotEmpty()) item("sec-$key") {
                        val (ic, name) = label.split("|", limit = 2)
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface, RoundedCornerShape(22.dp)).border(2.dp, p.edge, RoundedCornerShape(22.dp)).clickable { collapsed = if (c) collapsed - key else collapsed + key; ui.prefs.collapsed = collapsed }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val gc = u.goalGroups.firstOrNull { it.id == key }?.color?.let { fr.pepsgoal.app.ui.theme.hex(it) }
                            EmojiTile(ic, gc ?: p.flame, 38.dp)
                            Text(name, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Count(items.size)
                            Spacer(Modifier.weight(1f))
                            Pi(if (c) "chevronleft" else "down", size = 18.dp, tint = p.ink3)
                        }
                    }
                    if (!c) {
                        // Liste : une ligne à marche par objectif (avec son étiquette de type), validation en un geste
                        if (compact) items(items, key = { "r-" + it.id }) { g -> Box(Modifier.animateItem()) { GoalRow(ui, g, dueIds[g.id] ?: false, tag = true) } }
                        else items(items, key = { "g-" + it.id }) { g -> GoalCard(ui, g, Modifier.animateItem()) }
                    }
                }
            }
        }
    }
}

private val SORTS = listOf("fav" to "Pertinence", "az" to "A → Z", "progress" to "Progression", "recent" to "Récents")
private val SORT_SUBS = listOf("Favoris d’abord, puis ce qui reste à faire aujourd’hui", "Par ordre alphabétique", "Les plus avancés d’abord", "Les derniers créés d’abord")

/** Fenêtre Filtrer : état, dossier et type, avec le nombre d'objectifs trouvés sur le bouton du bas. */
@Composable
fun GoalFilterSheet(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val u = ui.pg.me() ?: return
    val v = goalView(ui) ?: return
    fr.pepsgoal.app.ui.sheets.SheetHead(ui, "Filtrer")
    SetGroup("État", emoji = "🎯") {
        SetRadioGroup(listOf("active" to "En cours", "today" to "À faire aujourd’hui", "fav" to "Favoris", "done" to "Terminés et archivés"), ui.goalFilter, { ui.goalFilter = it },
            emojis = listOf("🎯", "☀️", "⭐", "🗄️"))
    }
    if (u.goalGroups.isNotEmpty()) SetGroup("Dossier", emoji = "📁") {
        Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PRadioRow("Tous les dossiers", null, v.group == "all", { ui.groupFilter = "all" }, emoji = "🗂️")
            u.goalGroups.forEach { gr ->
                PRadioRow(gr.name, null, v.group == gr.id, { ui.groupFilter = gr.id }, emoji = gr.icon, color = gr.color?.let { fr.pepsgoal.app.ui.theme.hex(it) })
            }
            PRadioRow("Sans dossier", null, v.group == "none", { ui.groupFilter = "none" }, emoji = "📄")
        }
    }
    if (v.types.size > 1 || v.type != "all") SetGroup("Type", emoji = "🧩") {
        Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PRadioRow("Tous les types", null, v.type == "all", { ui.goalType = "all" }, emoji = "✨")
            v.types.forEach { t ->
                PRadioRow(TYPES.getValue(t).label, null, v.type == t, { ui.goalType = t }, color = p.type(t).c,
                    leading = { Box(Modifier.size(14.dp).clip(CircleShape).background(p.type(t).c).border(1.5.dp, p.type(t).step, CircleShape)) })
            }
        }
    }
    fr.pepsgoal.app.ui.sheets.SheetFoot {
        PButton("Réinitialiser", { ui.goalFilter = "active"; ui.groupFilter = "all"; ui.goalType = "all" }, kind = "ghost", enabled = v.active > 0 || ui.goalFilter != "active")
        PButton(when (v.list.size) { 0 -> "Aucun objectif"; 1 -> "Voir 1 objectif"; else -> "Voir ${v.list.size} objectifs" }, { ui.close() }, kind = "primary")
    }
}

/** Fenêtre Trier : ordre de la liste, affichage (liste ou cartes) et regroupement par dossier. */
@Composable
fun GoalSortSheet(ui: AppState) {
    ui.watch()
    val u = ui.pg.me() ?: return
    fr.pepsgoal.app.ui.sheets.SheetHead(ui, "Trier et afficher")
    SetGroup("Trier par", emoji = "↕️") {
        SetRadioGroup(SORTS, ui.goalSort, { ui.goalSort = it }, subs = SORT_SUBS, emojis = listOf("✨", "🔤", "📈", "🆕"))
    }
    SetGroup("Affichage", emoji = "👀") {
        SetRadioGroup(listOf("list" to "Liste", "cards" to "Cartes"), if (ui.prefs.goalView != "cards") "list" else "cards", { ui.prefs.goalView = it; ui.bump() },
            subs = listOf("Une ligne par objectif, à valider d’un geste", "Une carte détaillée par objectif"), emojis = listOf("📋", "🗂️"))
        if (u.goalGroups.isNotEmpty()) SetToggle("Regrouper par dossier", "Une section repliable par dossier", "folder", on = ui.prefs.goalGroupBy, onChange = { ui.prefs.goalGroupBy = it; ui.bump() })
    }
    fr.pepsgoal.app.ui.sheets.SheetFoot { PButton("Fermer", { ui.close() }, kind = "primary") }
}

/**
 * Tuile de la vue d'ensemble : aplat néon à marche (emoji, chiffre en Unbounded, libellé prune). Choisie, elle porte
 * une coche et un bord prune (jamais la couleur seule) ; au repos, elle est teintée.
 */
@Composable
private fun OverTile(v: String, l: String, emoji: String, c: Color, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val press = rememberPress(inter)
    val fill = if (on) vivid(c) else androidx.compose.ui.graphics.lerp(p.surface, c, if (p.dark) .25f else .3f)
    val ink = if (on) fr.pepsgoal.app.ui.theme.PLUM else p.ink
    Column(modifier.fillMaxHeight().chunk(fill, if (on) (if (p.contrast) p.ink else vivid(c).step()) else androidx.compose.ui.graphics.lerp(p.edge, c, .55f), 22.dp, 4.dp, if (on) 0.dp else 2.dp, press)
        .clip(RoundedCornerShape(22.dp)).springClick(inter, role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick).semantics { selected = on }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Emoji(emoji, 24.dp); Spacer(Modifier.weight(1f))
            if (on) Pi("check", size = 16.dp, tint = ink)
        }
        FitText(v, size = 22, min = 12, weight = FontWeight.ExtraBold, family = Unbounded, color = ink)
        // Libellé qui rétrécit pour tenir sur une ligne (jamais « aujourd’hu » coupé)
        FitText(l, size = 13, min = 9, weight = FontWeight.ExtraBold, color = ink)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(list: List<Goal>, key: ((Goal) -> Any)? = { it.id }, content: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Goal) -> Unit) {
    items(list.size, key = key?.let { k -> { i: Int -> k(list[i]) } }) { i -> content(list[i]) }
}
