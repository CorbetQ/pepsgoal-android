package fr.pepsgoal.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.data.Journal
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.screens.MoodPicker
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.Goal
import fr.pepsgoal.core.model.due
import fr.pepsgoal.core.model.mode

/** Ligne compacte d'un objectif avec sa case (bilan du soir, journée difficile). */
@Composable
private fun MiniGoal(ui: AppState, g: Goal, done: Boolean, right: (@Composable () -> Unit)? = null) {
    val p = LocalPal.current; val tc = p.type(g.type)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (done) tc.c.copy(alpha = .14f) else p.ink.copy(alpha = .035f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GoalIcon(g, 36.dp)
        Text(g.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (done) p.ink2 else p.ink)
        right?.invoke() ?: CheckButton(done, false, if (done) "Annuler : ${g.title}" else "Valider : ${g.title}", tc.c) { doValidate(ui, g.id) }
    }
}

/**
 * Bilan du soir : ce qu'il reste à faire, humeur et note du jour, puis aperçu de demain. Trois petites étapes pour
 * clore la journée sans rien oublier.
 */
@Composable
fun EveningSheet(ui: AppState) {
    ui.watch()
    val ctx = LocalContext.current; val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val plan = pg.todayPlan(u.id); val left = plan.due.filter { !it.done }; val done = plan.due.count { it.done }
    val t = today(); Journal.rev
    val e = remember(Journal.rev) { Journal.get(ctx, t) }
    var note by remember(e?.date) { mutableStateOf(e?.text ?: "") }
    val tomorrow = todayDate().plusDays(1)
    val next = remember(ui.rev) {
        pg.myGoals(u.id).filter { !pg.isClosedGoal(it) && (pg.scheduled(it, tomorrow) || (it.type == "routine" && it.mode == "count") || (it.type == "deadline" && it.due == ymd(tomorrow))) }
    }
    SheetHead(ui, "Bilan du soir")
    Muted(cap(weekdayLongDate(todayDate())) + " · $done/${plan.due.size} validés", small = true)
    // 1. Ce qu'il reste
    SectionTitle(if (left.isEmpty()) "Journée bouclée" else "Il te reste ${pl(left.size, "objectif", "objectifs")}")
    if (left.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Flame(30.dp); Text(if (plan.due.isEmpty()) "Rien n’était prévu aujourd’hui." else "Tout est validé, bravo !", fontWeight = FontWeight.Bold) }
    left.forEach { x -> MiniGoal(ui, x.g, false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PButton(if (x.g.missWhy()[t] != null) "Noté" else "Pas pu", { ui.open(Sheet.Miss(x.g.id, t)) }, small = true, kind = "ghost")
            CheckButton(false, false, "Valider : ${x.g.title}", p.type(x.g.type).c) { doValidate(ui, x.g.id) }
        }
    } }
    // 2. Humeur, étiquettes et note
    SectionTitle("Ta journée")
    MoodPicker(e?.mood ?: 0) { m -> Journal.put(ctx, t, m, note) }
    if (e != null) FlowRowCompat(gap = 6.dp) { Journal.TAGS.forEach { (tg, em) -> FChip("$em $tg", tg in e.tags, { Journal.toggleTag(ctx, t, tg) }) } }
    Field(note, { note = it }, "", placeholder = "Une ligne sur ta journée (facultatif)", singleLine = false, max = 1000, icon = "book")
    // 3. Demain
    SectionTitle("Demain · ${cap(weekdayLongDate(tomorrow).substringBefore(' '))}")
    if (next.isEmpty()) Muted("Rien de prévu demain : profite !", small = true)
    else FlowRowCompat(gap = 6.dp) { next.take(12).forEach { g -> Chip("${g.icon} ${g.title.take(22)}") } }
    SheetFoot {
        PButton("Plus tard", { ui.close() }, kind = "ghost")
        PButton("Terminer", {
            if (e != null || note.isNotBlank()) Journal.put(ctx, t, e?.mood ?: 3, note)
            ui.close(); ui.toast(if (left.isEmpty()) "Belle journée ! À demain." else "Bilan enregistré. Bonne soirée !", "moon")
        }, icon = "check", kind = "primary")
    }
}

/** Journée difficile : choix des essentiels (3 au plus) et activation pour aujourd'hui. */
@Composable
fun HardDaySheet(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val on = pg.isHardDay()
    val cands = pg.myGoals(u.id).filter { !pg.isClosedGoal(it) && it.userId == u.id && pg.daily(it.type) || (it.type in setOf("routine", "abstinence") && !pg.isClosedGoal(it) && it.userId == u.id) }.distinctBy { it.id }
    val month = today().substring(0, 7)
    SheetHead(ui, "Journée difficile")
    Muted("Les jours où tout est trop, garde seulement l’essentiel : seuls ces objectifs comptent aujourd’hui, les autres ne cassent pas leur série. Jusqu’à $HARD_PER_MONTH fois par mois (${u.hardDays.count { it.startsWith(month) }} utilisée${if (u.hardDays.count { it.startsWith(month) } > 1) "s" else ""}).", small = true)
    SectionTitle("Mes essentiels · ${u.essentials.count { id -> cands.any { it.id == id } }}/$ESSENTIALS_MAX")
    if (cands.isEmpty()) Muted("Aucun objectif quotidien pour l’instant.", small = true)
    cands.forEach { g ->
        val ess = g.id in u.essentials
        MiniGoal(ui, g, ess) { PSwitch(ess, { pg.toggleEssential(g.id)?.let { ui.toast(it, "x") } }) }
    }
    SheetFoot {
        PButton("Fermer", { ui.close() }, kind = "ghost")
        PButton(if (on) "Revenir à une journée normale" else "Activer pour aujourd’hui", {
            pg.toggleHardDay()?.let { ui.toast(it, "x") } ?: run { ui.close(); ui.toast(if (on) "Journée normale : tous tes objectifs comptent." else "Journée difficile : seuls tes essentiels comptent aujourd’hui. Prends soin de toi.", if (on) "check" else "heart") }
        }, icon = if (on) "x" else "heart", kind = "primary")
    }
}
