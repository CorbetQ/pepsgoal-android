package fr.pepsgoal.app.ui.sheets

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.*

/* ------------------------------------------------------------------ Plan B météo */

private val PLAN_B_IDEAS = listOf("Vélo d’appartement", "Tapis de course", "Yoga à la maison", "Corde à sauter", "Escaliers de l’immeuble", "Séance de renforcement")

/** Option « Plan B s'il pleut » : l'alternative proposée sur l'accueil et dans le briefing quand la météo s'y prête mal. */
@Composable
fun PlanBRow(ui: AppState, g: Goal) {
    val p = LocalPal.current; val pg = ui.pg
    if (g.type !in setOf("routine", "timed", "period", "duration", "challenge")) return
    var open by remember { mutableStateOf(false) }
    var v by remember(g.id) { mutableStateOf(g.planB) }
    OptionRow("cloud", p.type("measure").c, p.type("objective").c, "Plan B s’il pleut", g.planB.ifBlank { null } ?: "Une alternative quand la météo s’y prête mal", open, { open = !open }) {
        Muted("Pluie, orage ou canicule : l’accueil et le briefing du matin te proposent ce plan B, qui compte comme une validation normale.", small = true)
        FlowRowCompat(gap = 6.dp) { PLAN_B_IDEAS.forEach { idea -> FChip(idea, v == tr(idea), { v = tr(idea) }) } }
        Field(v, { v = it }, "Ton plan B", placeholder = "Vélo d’appartement", max = 60, icon = "cloud")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton("Enregistrer", { g.planB = v.trim(); pg.store.commit(); open = false; ui.bump(); ui.toast(if (g.planB.isEmpty()) "Plan B retiré." else "Plan B enregistré : « ${g.planB} ».", "cloud") }, icon = "check", small = true, kind = "primary")
            if (g.planB.isNotEmpty()) PButton("Retirer", { v = ""; g.planB = ""; pg.store.commit(); ui.bump() }, small = true, kind = "ghost")
        }
        if (pg.me()?.settings?.weather == null) Muted("Choisis ta ville dans Paramètres › Météo pour que PepsGoal sache quand il pleut.", small = true)
    }
}
