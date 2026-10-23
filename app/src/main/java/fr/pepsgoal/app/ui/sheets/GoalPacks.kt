package fr.pepsgoal.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.target

/** Modèle d'objectif : type, intitulé, emoji et réglages (mêmes clés que le formulaire). */
class PackGoal(val type: String, val title: String, val icon: String, val fields: Map<String, String> = emptyMap())
class Pack(val key: String, val name: String, val emoji: String, val desc: String, val goals: List<PackGoal>)

private val ALL = "0,1,2,3,4,5,6"

/** Packs prêts à l'emploi, créés en un geste. */
val PACKS = listOf(
    Pack("morning", "Matinée parfaite", "🌅", "Bien démarrer chaque journée", listOf(
        PackGoal("timed", "Debout avant 7 h 30", "⏰", mapOf("tmode" to "before", "time" to "07:30", "tdays" to "0,1,2,3,4")),
        PackGoal("routine", "Un grand verre d’eau au réveil", "💧", mapOf("mode" to "days", "days" to ALL)),
        PackGoal("duration", "Méditer 10 minutes", "🧘", mapOf("minutes" to "10")),
        PackGoal("routine", "Faire son lit", "🛏️", mapOf("mode" to "days", "days" to ALL)))),
    Pack("health", "Santé", "💚", "Bouger, boire, bien manger, bien dormir", listOf(
        PackGoal("cap", "10 000 pas par jour", "👟", mapOf("target" to "10000", "unit" to "pas", "health" to "steps")),
        PackGoal("cap", "Boire 1,5 L d’eau", "💧", mapOf("target" to "1.5", "unit" to "L", "health" to "water")),
        PackGoal("routine", "5 fruits et légumes", "🥗", mapOf("mode" to "days", "days" to ALL)),
        PackGoal("timed", "Au lit avant 23 h", "😴", mapOf("tmode" to "before", "time" to "23:00", "tdays" to ALL)))),
    Pack("reading", "Lecture", "📚", "Lire un peu chaque jour, beaucoup chaque année", listOf(
        PackGoal("duration", "Lire 20 minutes", "📖", mapOf("minutes" to "20")),
        PackGoal("cap", "20 pages par jour", "📄", mapOf("target" to "20", "unit" to "pages")),
        PackGoal("objective", "Lire 12 livres cette année", "📚", mapOf("target" to "12", "unit" to "livres", "endDate" to "${todayDate().year}-12-31")))),
    Pack("screens", "Zéro écran le soir", "📵", "Déconnecter avant de dormir", listOf(
        PackGoal("timed", "Pas d’écran après 22 h", "📱", mapOf("tmode" to "notafter", "time" to "22:00", "tdays" to ALL)),
        PackGoal("limit", "Max 1 h de réseaux sociaux", "🤳", mapOf("max" to "1", "unit" to "h", "period" to "day")),
        PackGoal("routine", "Lire au lit plutôt que le téléphone", "📖", mapOf("mode" to "days", "days" to ALL)))),
    Pack("sport", "Sport", "💪", "Une routine sportive qui tient", listOf(
        PackGoal("routine", "Séance de sport", "💪", mapOf("mode" to "count", "perWeek" to "3")),
        PackGoal("cap", "30 minutes d’activité", "🏃", mapOf("target" to "30", "unit" to "min", "health" to "exercise")),
        PackGoal("challenge", "30 jours de gainage", "🧱", mapOf("length" to "30")))),
    Pack("calm", "Sérénité", "🧘", "Moins de stress, plus de calme", listOf(
        PackGoal("duration", "Méditation", "🧘", mapOf("minutes" to "10")),
        PackGoal("routine", "3 gratitudes du jour", "✍️", mapOf("mode" to "days", "days" to ALL)),
        PackGoal("routine", "Marcher dehors sans téléphone", "🌳", mapOf("mode" to "count", "perWeek" to "3"))))
)

/** Étape « Modèles » de l'assistant de création : choix d'un pack, puis des objectifs à créer. */
@Composable
fun PacksStep(ui: AppState, onBack: () -> Unit) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    var pack by remember { mutableStateOf<Pack?>(null) }
    val cur = pack
    if (cur == null) {
        Muted("Choisis un pack : tu pourras décocher ce qui ne te va pas.")
        PACKS.chunked(2).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    Column(Modifier.weight(1f).fillMaxHeight().bounce(inter, .96f).clip(RoundedCornerShape(18.dp)).background(p.ink.copy(alpha = .05f)).border(1.dp, p.line, RoundedCornerShape(18.dp))
                        .springClick(inter, label = k.name) { pack = k }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Emoji(k.emoji, 32.dp); Text(k.name, fontWeight = FontWeight.ExtraBold); Text(k.desc, fontSize = 12.sp, color = p.ink2)
                        Text(pl(k.goals.size, "objectif", "objectifs"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.flameInk)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        SheetFoot { PButton("Retour", onBack, kind = "ghost", icon = "chevronleft") }
        return
    }
    val pick = remember(cur.key) { mutableStateListOf<Int>().apply { addAll(cur.goals.indices) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Emoji(cur.emoji, 40.dp); Column { Text(cur.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp); Muted(cur.desc, small = true) }
    }
    val built = cur.goals.map { x -> buildGoal0(ui, u, null, x.type, x.title, x.icon, "", u.settings.defaultVis, x.fields, emptyList()).first }
    cur.goals.forEachIndexed { i, x ->
        val g = built[i]; val on = i in pick; val tc = p.type(x.type)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(tc.c.copy(alpha = if (on) .18f else .06f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Checkbox(on, { if (it) pick.add(i) else pick.remove(i) }, colors = CheckboxDefaults.colors(checkedColor = p.flame))
            Emoji(x.icon, 28.dp)
            Column(Modifier.weight(1f)) { Text(x.title, fontWeight = FontWeight.ExtraBold); Muted("${typeInfo(x.type).label}${g?.let { " · " + pg.metaLine(it) } ?: ""}", small = true, maxLines = 2) }
        }
    }
    SheetFoot {
        PButton("Autres packs", { pack = null }, kind = "ghost", icon = "chevronleft")
        PButton("Créer ${pl(pick.size, "objectif", "objectifs")}", {
            val goals = pick.sorted().mapNotNull { i -> built[i]?.let { it to cur.goals[i] } }
            if (goals.isEmpty()) { ui.toast("Coche au moins un objectif.", "x"); return@PButton }
            val ctx = fr.pepsgoal.app.PepsApp.instance
            var links = fr.pepsgoal.app.sys.Health.links(ctx)
            goals.forEach { (g, x) ->
                pg.db.goals.add(g)
                x.fields["health"]?.let { h -> if (fr.pepsgoal.app.sys.Health.available(ctx)) links = links + fr.pepsgoal.app.sys.Health.Link(g.id, h, g.target, ">=") }
            }
            fr.pepsgoal.app.sys.Health.saveLinks(ctx, links)
            pg.store.commit(); pg.queueBadgeCheck(); pg.hooks.liveChanged(); ui.close()
            if (ui.tab != "goals") ui.tab = "today"
            ui.toast("Pack « ${cur.name} » : ${pl(goals.size, "objectif créé", "objectifs créés")}. À toi de jouer !", "flame")
        }, icon = "check", kind = "primary", enabled = pick.isNotEmpty())
    }
}
