package fr.pepsgoal.app.ui.sheets

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.screens.TimeButton
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.Goal

/*
 * Objectifs « si… alors… » (plans d'implémentation) : un déclencheur facultatif dans le formulaire (heure, arrivée ou
 * départ d'un emplacement enregistré, objectif validé avant) et la phrase « alors… ». La logique est dans le cœur
 * (IfThen.kt) ; ici, la saisie et l'affichage de la phrase.
 */

/**
 * Bloc du formulaire. Les valeurs vivent dans le brouillon [t] du formulaire (« ifKind », « ifTime », « ifPlace »,
 * « ifGoal », « ifThen ») et sont reportées sur l'objectif par [applyIfThenFields].
 */
@Composable
fun IfThenForm(ui: AppState, g0: Goal?, title: String, t: MutableMap<String, String>) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    fun txt(k: String, def: () -> String) = t.getOrPut(k) { def() }
    val kind = txt("ifKind") { g0?.ifKind ?: "" }
    SectionTitle("Si… alors… (facultatif)", "⚡")
    Muted("Un plan précis tient mieux qu’une bonne résolution : choisis le moment qui déclenche l’objectif, le rappel part à cet instant avec ta phrase.", small = true)
    FlowRowCompat(gap = 6.dp) {
        FChip("Aucun", kind.isEmpty(), { t["ifKind"] = "" })
        IF_KINDS.forEach { (k, l, e) -> FChip("$e $l", kind == k, { t["ifKind"] = k }) }
    }
    when (kind) {
        "time" -> SetRow("Heure") { TimeButton(txt("ifTime") { g0?.ifTime ?: "07:30" }, { t["ifTime"] = it }, "Heure du déclencheur") }
        "arrive", "leave" -> {
            val places = u.places
            if (places.isEmpty()) {
                Muted("Enregistre d’abord un emplacement (maison, salle de sport, bureau…) dans Paramètres › Emplacements.", small = true)
                PButton("Ouvrir les emplacements", { ui.close(); ui.push(Screen.Settings("places")) }, icon = "pin", small = true)
            } else {
                val cur = txt("ifPlace") { g0?.ifPlace ?: places.first().id }
                FlowRowCompat(gap = 6.dp) { places.forEach { pl -> FChip("${pl.icon} ${pl.name}", pl.id == cur, { t["ifPlace"] = pl.id }) } }
                Muted(if (kind == "arrive") "Le rappel part quand tu arrives (position « Toujours autorisée » nécessaire), seulement si l’objectif reste à faire."
                    else "Le rappel part quand tu pars (position « Toujours autorisée » nécessaire), seulement si l’objectif reste à faire.", small = true)
            }
        }
        "after" -> {
            val cands = pg.myGoals().filter { it.id != g0?.id && !pg.isClosedGoal(it) && it.userId == u.id }
            if (cands.isEmpty()) Muted("Aucun autre objectif disponible.", small = true)
            else GoalChips(cands, txt("ifGoal") { g0?.ifGoal ?: "" }.ifEmpty { null }, { id -> t["ifGoal"] = id ?: "" }, bleed = 20.dp)
        }
    }
    if (kind.isNotEmpty()) {
        Field(txt("ifThen") { g0?.ifThen ?: "" }, { t["ifThen"] = it }, "Alors…", placeholder = "je fais 10 minutes d’étirements", max = 90)
        ifThenDraft(pg, t, title)?.let { ph ->
            Row(Modifier.fillMaxWidth().chunk(p.selTint(p.type("timed").c), p.edge, 18.dp, 3.dp, 2.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Emoji("⚡", 22.dp); Text(ph, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = p.ink)
            }
        }
    }
}

/** Phrase du brouillon (aperçu du formulaire et du résumé). */
fun ifThenDraft(pg: Pg, t: Map<String, String>, title: String): String? {
    val k = t["ifKind"]?.ifEmpty { null } ?: return null
    val place = t["ifPlace"]?.let { id -> pg.me()?.places?.firstOrNull { it.id == id }?.name }
    return ifThenPhrase(k, t["ifTime"] ?: "07:30", place, pg.goalById(t["ifGoal"])?.title, t["ifThen"].orEmpty(), title)
}

/** Reporte le brouillon sur l'objectif construit. Renvoie un message d'erreur, ou null. */
fun applyIfThenFields(g: Goal, g0: Goal?, t: Map<String, String>): String? {
    val k = (t["ifKind"] ?: g0?.ifKind ?: "").ifEmpty { null }
    if (k == null || g.type !in IF_TYPES) { if (g.data.containsKey("ifKind")) g.ifKind = null; return null }
    g.ifKind = k
    when (k) {
        "time" -> g.ifTime = t["ifTime"] ?: g0?.ifTime ?: "07:30"
        "arrive", "leave" -> { g.ifPlace = t["ifPlace"] ?: g0?.ifPlace; if (g.ifPlace == null) return "Choisis l’emplacement qui déclenche l’objectif." }
        "after" -> { g.ifGoal = (t["ifGoal"] ?: g0?.ifGoal)?.ifEmpty { null }; if (g.ifGoal == null) return "Choisis l’objectif à valider avant." }
    }
    g.ifThen = (t["ifThen"] ?: g0?.ifThen ?: "").trim().take(90)
    return null
}

/** Encart de la fiche : la phrase complète, en tête des informations. */
@Composable
fun IfThenCard(ui: AppState, g: Goal) {
    val p = LocalPal.current; val ph = ui.pg.ifThenOf(g) ?: return
    Row(Modifier.fillMaxWidth().chunk(p.selTint(p.type("timed").c), p.edge, 24.dp, 4.dp, 2.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EmojiTile(IF_KINDS.firstOrNull { it.first == g.ifKind }?.third ?: "⚡", p.type("timed").c, 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Eyebrow("Si… alors…")
            Text(ph, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = p.ink, lineHeight = 21.sp)
        }
    }
}
