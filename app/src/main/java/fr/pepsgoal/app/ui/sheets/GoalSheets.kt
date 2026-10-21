package fr.pepsgoal.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.screens.TimeButton
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.*

private val NUM = KeyboardOptions(keyboardType = KeyboardType.Decimal)

/* ================================================================== formulaire d'objectif */
@Composable
fun GoalFormSheet(ui: AppState, s: Sheet.GoalForm) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val g0 = s.editId?.let { pg.goalById(it) }
    var type by remember { mutableStateOf(g0?.type ?: s.preType ?: "routine") }
    // Brouillon : copie des réglages du type (les champs texte gardent la saisie telle quelle)
    val d = remember { Goal(g0?.id ?: "draft", u.id, type, g0?.title ?: s.preTitle ?: "", g0?.icon ?: s.preIcon ?: EMOJIS[0], g0?.note ?: "", g0?.visibility ?: u.settings.defaultVis).also { x -> g0?.let { x.data = LinkedHashMap(it.data) } } }
    var rev by remember { mutableIntStateOf(0) }; rev.hashCode()
    // Champs préremplis (objectif proposé par l'assistant IA)
    val t = remember { mutableStateMapOf<String, String>().apply { s.preFields?.let { putAll(it) } } }
    fun txt(k: String, def: () -> String) = t.getOrPut(k) { def() }
    fun num(v: Double?) = v?.let { fmtNum(it) } ?: ""
    fun upd(f: Goal.() -> Unit) { d.f(); rev++ }
    var title by remember { mutableStateOf(d.title) }; var icon by remember { mutableStateOf(d.icon) }; var note by remember { mutableStateOf(d.note) }; var vis by remember { mutableStateOf(d.visibility) }
    var buddy by remember { mutableStateOf(g0?.buddy) }; var bVerify by remember { mutableStateOf(g0?.buddyVerify ?: false) }; var bMiss by remember { mutableStateOf(g0?.buddyMiss ?: true) }
    var grp by remember { mutableStateOf(g0?.let { pg.groupOfGoal(u, it.id)?.id }) }
    var members by remember { mutableStateOf(g0?.members?.filter { it != u.id } ?: emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var showIcons by remember { mutableStateOf(false) }
    val social = pg.socialOn(u)
    val today0 = today(); val in7 = ymd(todayDate().plusDays(7))

    SheetHead(ui, if (g0 != null) "Modifier" else "Nouvel objectif")
    // Types
    if (g0 != null) TypeTile(type, true, Modifier.fillMaxWidth()) {}
    else TYPE_GROUPS.forEach { (lab, ks) ->
        val list = ks.filter { !(it in SOCIAL_TYPES && !social) }
        if (list.isNotEmpty()) {
            Eyebrow(lab)
            list.chunked(2).forEach { row -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { k -> TypeTile(k, k == type, Modifier.weight(1f).fillMaxHeight()) { type = k; d.type = k; err = null } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } }
        }
    }
    Field(title, { title = it }, "Intitulé", placeholder = "Ex. Courir 30 minutes", max = 80)
    SetRow("Icône") {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(p.type(type).c.copy(alpha = .22f)).clickable { showIcons = !showIcons }, contentAlignment = Alignment.Center) { Emoji(icon, 30.dp) }
        PButton(if (showIcons) "Fermer" else "Changer", { showIcons = !showIcons }, small = true)
    }
    if (showIcons) EmojiPicker(icon) { icon = it; showIcons = false }

    // Réglages du type
    when (type) {
        "objective" -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(txt("target") { num(g0?.targetOrNull ?: 10.0) }, { t["target"] = it }, "Cible", Modifier.weight(1f), keyboard = NUM, max = 12)
                Field(txt("unit") { g0?.unit ?: "" }, { t["unit"] = it }, "Unité", Modifier.weight(1f), placeholder = "livres, km, €…", max = 20)
            }
            DateField("Date cible (optionnelle)", txt("endDate") { g0?.endDate ?: "" }, { t["endDate"] = it }, optional = true)
        }
        "monthly" -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(txt("target") { num(g0?.targetOrNull ?: 20.0) }, { t["target"] = it }, "Cible par mois", Modifier.weight(1f), keyboard = NUM, max = 12)
                Field(txt("unit") { g0?.unit ?: "" }, { t["unit"] = it }, "Unité", Modifier.weight(1f), placeholder = "km, séances, €…", max = 20)
            }
            val cum = txt("cumulate") { if (g0 == null || g0.cumulate) "1" else "" } == "1"
            SetRow("Cumuler les scores de chaque mois", "Le compteur du mois repart à zéro le 1er, et un total cumulé de tous les mois s'affiche en plus.") { PSwitch(cum, { t["cumulate"] = if (it) "1" else "" }) }
        }
        "deadline" -> DateField("Date limite", txt("due") { g0?.due?.ifEmpty { null } ?: in7 }, { t["due"] = it })
        "routine" -> {
            val mode = txt("mode") { g0?.mode ?: "days" }
            Seg(listOf("days" to "Jours précis", "count" to "X fois par semaine"), mode, { t["mode"] = it })
            if (mode == "days") { SectionTitle("Jours de la semaine"); DayChips(dayList(t["days"]) ?: g0?.days ?: listOf(0, 2, 4)) { t["days"] = it.joinToString(",") } }
            else Field(txt("perWeek") { "${g0?.perWeek ?: 3}" }, { t["perWeek"] = it }, "Nombre de fois par semaine", keyboard = NUM, max = 1)
        }
        "abstinence" -> DateField("Début", txt("since") { g0?.since?.ifEmpty { null } ?: today0 }, { t["since"] = it })
        "limit" -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(txt("max") { num(g0?.max ?: 2.0) }, { t["max"] = it }, "Maximum", Modifier.weight(1f), keyboard = NUM, max = 12)
                Field(txt("unit") { g0?.unit ?: "" }, { t["unit"] = it }, "Unité", Modifier.weight(1f), placeholder = "cafés, h d'écran…", max = 20)
            }
            Seg(listOf("day" to "Par jour", "week" to "Par semaine"), txt("period") { g0?.period ?: "day" }, { t["period"] = it })
            Muted("Note chaque consommation avec « +1 », puis valide ta journée si tu es resté sous la limite.", small = true)
        }
        "duration" -> {
            Field(txt("minutes") { num(g0?.minutes ?: 30.0) }, { t["minutes"] = it }, "Minutes par jour", keyboard = NUM, max = 5)
            Muted("Lance le minuteur depuis l'accueil, la notification ou la Now Bar : la journée se valide toute seule quand le temps est atteint.", small = true)
        }
        "measure" -> {
            val tf = txt("timeFmt") { if (g0?.timeFmt == true) "1" else "" } == "1"
            SetRow("Valeur en temps (mm:ss)", "Pour un chrono : temps au 10 km, gainage…") { PSwitch(tf, { t["timeFmt"] = if (it) "1" else "" }) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(txt("unit") { g0?.unit ?: "" }, { t["unit"] = it }, "Unité", Modifier.weight(1f), placeholder = "kg, mots/min…", max = 20)
                Field(txt("mtarget") { g0?.targetOrNull?.let { pg.measureFmt(g0, it) } ?: "" }, { t["mtarget"] = it }, "Cible (optionnelle)", Modifier.weight(1f), placeholder = "50:00 ou 70", max = 12)
            }
            Seg(listOf("down" to "Plus bas = mieux", "up" to "Plus haut = mieux"), txt("dir") { if (g0 != null) g0.dir else "down" }, { t["dir"] = it })
        }
        "timed" -> {
            Seg(listOf("before" to "Faire avant", "after" to "Faire après", "notafter" to "Rien après"), txt("tmode") { g0?.mode ?: "before" }, { t["tmode"] = it })
            SetRow("Heure") { TimeButton(txt("time") { g0?.time ?: "07:00" }, { t["time"] = it }, "Heure") }
            SectionTitle("Jours concernés")
            DayChips(dayList(t["tdays"]) ?: g0?.days ?: (0..6).toList()) { t["tdays"] = it.joinToString(",") }
            Muted("« Ne rien faire après » (pas d'écran après 22 h…) se confirme le lendemain matin.", small = true)
        }
        "project" -> {
            if (g0 != null) Muted("Ajoute, coche ou supprime les étapes depuis la fiche du projet.", small = true)
            else Field(txt("tasks") { "" }, { t["tasks"] = it }, "Étapes (une par ligne)", placeholder = "Choisir un nom de domaine\nMaquetter la page d'accueil\nMettre en ligne", singleLine = false, minLines = 4, max = 4000)
            DateField("Échéance (optionnelle)", txt("due") { g0?.due ?: "" }, { t["due"] = it }, optional = true)
        }
        "challenge" -> {
            Field(txt("length") { "${g0?.length ?: 30}" }, { t["length"] = it }, "Durée (jours)", keyboard = NUM, max = 3)
            DateField("Début", txt("start") { g0?.start?.ifEmpty { null } ?: today0 }, { t["start"] = it })
        }
        "program" -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(txt("base") { num(g0?.base ?: 60.0) }, { t["base"] = it }, "Cible semaine 1", Modifier.weight(1f), keyboard = NUM, max = 10)
                Field(txt("step") { num(g0?.step ?: 15.0) }, { t["step"] = it }, "Hausse par semaine", Modifier.weight(1f), keyboard = NUM, max = 10)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(txt("unit") { g0?.unit ?: "min" }, { t["unit"] = it }, "Unité", Modifier.weight(1f), placeholder = "min, km, pompes…", max = 20)
                Field(txt("weeks") { "${g0?.weeks ?: 8}" }, { t["weeks"] = it }, "Nombre de semaines", Modifier.weight(1f), keyboard = NUM, max = 2)
            }
            DateField("Début", txt("start") { g0?.start?.ifEmpty { null } ?: today0 }, { t["start"] = it })
        }
        "group" -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(txt("target") { num(g0?.targetOrNull ?: 500.0) }, { t["target"] = it }, "Total commun", Modifier.weight(1f), keyboard = NUM, max = 12)
                Field(txt("unit") { g0?.unit ?: "km" }, { t["unit"] = it }, "Unité", Modifier.weight(1f), max = 20)
            }
            DateField("Date de fin (optionnelle)", txt("endDate") { g0?.endDate ?: "" }, { t["endDate"] = it }, optional = true)
            SectionTitle("Membres du groupe")
            FriendPicker(ui, members, false) { members = it }
        }
        "duel" -> {
            if (g0 == null) { SectionTitle("Adversaire"); FriendPicker(ui, members, true) { members = it } }
            Field(txt("unit") { g0?.unit ?: "pompes" }, { t["unit"] = it }, "Ce que vous comptez", max = 20)
            DateField("Fin du duel", txt("endDate") { g0?.endDate?.ifEmpty { null } ?: in7 }, { t["endDate"] = it }, min = today0)
        }
        "period" -> {
            val freq = txt("freq") { g0?.freq ?: "week" }
            Seg(listOf("week" to "Hebdomadaire", "month" to "Mensuelle"), freq, { t["freq"] = it })
            if (freq == "week") { SectionTitle("Jours de la semaine"); DayChips(dayList(t["pdays"]) ?: g0?.days?.takeIf { g0.freq == "week" } ?: listOf(0, 2, 4)) { t["pdays"] = it.joinToString(",") } }
            else {
                SectionTitle("Jours du mois")
                val md = dayList(t["pmdays"]) ?: g0?.mdays ?: listOf(1)
                FlowRowCompat(gap = 4.dp) { (1..31).forEach { n -> FChip("$n", n in md, { t["pmdays"] = (if (n in md) md - n else (md + n).sorted()).joinToString(",") }) } }
                Muted("Le 29, 30 ou 31 tombe le dernier jour des mois plus courts.", small = true)
            }
            SetRow("De") { TimeButton(txt("pstart") { g0?.start?.ifEmpty { null } ?: "18:00" }, { t["pstart"] = it }, "Début") }
            SetRow("À") { TimeButton(txt("pend") { g0?.end?.ifEmpty { null } ?: "19:00" }, { t["pend"] = it }, "Fin") }
            Muted("Valide pendant la tranche horaire (ou jusqu’à une heure après) ; un rappel arrive au début de chaque période, et la période en cours s’affiche en direct dans la barre d’état.", small = true)
        }
    }
    // Binôme
    val friends = pg.friendsOf(u.id).mapNotNull { pg.userById(it) }
    if (type !in SOCIAL_TYPES && social && friends.isNotEmpty()) {
        SectionTitle("Binôme de motivation (optionnel)")
        FlowRowCompat(gap = 6.dp) {
            FChip("Aucun binôme", buddy == null, { buddy = null })
            friends.forEach { f -> FChip(f.name, buddy == f.id, { buddy = f.id }, leading = { Av(f, 18.dp) }) }
        }
        if (buddy != null) {
            SetRow("Doit confirmer mes validations", "Tes flammes arrivent une fois la réussite confirmée.") { PSwitch(bVerify, { bVerify = it }) }
            SetRow("Reçoit une alerte si je rate une journée") { PSwitch(bMiss, { bMiss = it }) }
        }
    }
    // Groupe d'objectifs
    SectionTitle("Groupe")
    if (u.goalGroups.isEmpty()) Muted("Crée des groupes depuis l'onglet Objectifs pour ranger tes objectifs par thème.", small = true)
    else FlowRowCompat(gap = 6.dp) { FChip("Sans groupe", grp == null, { grp = null }); u.goalGroups.forEach { gr -> FChip("${gr.icon} ${gr.name}", grp == gr.id, { grp = gr.id }) } }
    Field(note, { note = it }, "Pourquoi c'est important pour toi (optionnel)", singleLine = false, minLines = 2, max = 240)
    if (social) {
        SectionTitle("Qui peut le voir")
        Seg(VIS.keys.map { it to VIS.getValue(it).first }, vis, { vis = it }, icons = VIS.values.map { it.second })
        Muted("Les paliers atteints sont publiés automatiquement dans le feed, sauf en privé.", small = true)
    }
    Err(err)
    SheetFoot {
        PButton("Annuler", { ui.close() }, kind = "ghost")
        PButton(if (g0 != null) "Enregistrer" else "Créer", {
            err = submitGoal(ui, u, g0, type, title, icon, note, vis, t, members, buddy, bVerify, bMiss, grp)
        }, kind = "primary")
    }
}

private fun dayList(s: String?): List<Int>? = s?.let { if (it.isEmpty()) emptyList() else it.split(",").mapNotNull { x -> x.toIntOrNull() } }
private fun numOf(s: String?): Double = s?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: Double.NaN

/** Valide le formulaire et crée (ou met à jour) l'objectif. Renvoie un message d'erreur, sinon null. */
private fun submitGoal(ui: AppState, u: User, g0: Goal?, type: String, title0: String, icon: String, note: String, vis: String, t: Map<String, String>,
                       members: List<String>, buddy: String?, bVerify: Boolean, bMiss: Boolean, grp: String?): String? {
    val pg = ui.pg; val title = title0.trim()
    if (title.isEmpty()) return "Donne un intitulé à ton objectif."
    val g = Goal(g0?.id ?: uid(), g0?.userId ?: u.id, type, title, icon, note.trim(), vis, g0?.createdAt ?: nowMs())
    g.data = g0?.let { LinkedHashMap(it.data) } ?: linkedMapOf()
    if (g0 == null) { g.milestones = emptyList(); g.monthsDone = emptyList(); g.days = emptyList(); g.perWeek = 3 }
    fun v(k: String, def: String) = t[k] ?: def
    when (type) {
        "monthly", "objective" -> {
            g.target = numOf(v("target", fmtNum(g0?.targetOrNull ?: if (type == "monthly") 20.0 else 10.0))); g.unit = v("unit", g0?.unit ?: "").trim()
            if (!(g.target > 0)) return if (type == "monthly") "La cible mensuelle doit être un nombre supérieur à zéro." else "La cible doit être un nombre supérieur à zéro."
            if (type == "monthly") g.cumulate = v("cumulate", if (g0 == null || g0.cumulate) "1" else "") == "1" else g.endDate = v("endDate", g0?.endDate ?: "")
        }
        "deadline" -> { g.due = v("due", g0?.due?.ifEmpty { null } ?: ymd(todayDate().plusDays(7))); if (g.due.isEmpty()) return "Choisis une date limite." }
        "routine" -> {
            g.mode = v("mode", g0?.mode ?: "days"); g.days = dayList(t["days"]) ?: g0?.days ?: listOf(0, 2, 4); g.perWeek = numOf(v("perWeek", "${g0?.perWeek ?: 3}")).let { if (it.isNaN()) 0 else it.toInt() }
            if (g.mode == "days" && g.days.isEmpty()) return "Choisis au moins un jour de la semaine."
            if (g.mode == "count" && g.perWeek !in 1..7) return "Choisis entre 1 et 7 fois par semaine."
        }
        "abstinence" -> g.since = v("since", g0?.since?.ifEmpty { null } ?: today())
        "limit" -> {
            val raw = v("max", fmtNum(g0?.max ?: 2.0)); g.max = numOf(raw); g.unit = v("unit", g0?.unit ?: "").trim().ifEmpty { "unités" }; g.period = v("period", g0?.period ?: "day")
            if (raw.isBlank() || !(g.max >= 0)) return "Indique la limite à ne pas dépasser."
        }
        "duration" -> { g.minutes = numOf(v("minutes", fmtNum(g0?.minutes ?: 30.0))); if (!(g.minutes > 0)) return "Indique une durée quotidienne en minutes." }
        "measure" -> {
            g.timeFmt = v("timeFmt", if (g0?.timeFmt == true) "1" else "") == "1"; g.unit = v("unit", g0?.unit ?: "").trim(); g.dir = v("dir", g0?.dir ?: "down")
            val tr = v("mtarget", g0?.targetOrNull?.let { pg.measureFmt(g0, it) } ?: "").trim()
            if (tr.isEmpty()) g.data["target"] = kotlinx.serialization.json.JsonNull
            else { val x = pg.parseProgress(g, tr); if (!(x > 0)) return if (g.timeFmt) "Cible invalide : écris un temps comme 50:00." else "La cible doit être un nombre."; g.target = x }
            if (!g.timeFmt && g.unit.isEmpty()) return "Indique une unité (kg, mots/min…), ou coche « valeur en temps »."
        }
        "timed" -> { g.mode = v("tmode", g0?.mode ?: "before"); g.time = v("time", g0?.time ?: "07:00"); g.days = dayList(t["tdays"]) ?: g0?.days ?: (0..6).toList(); if (g.days.isEmpty()) return "Choisis au moins un jour." }
        "project" -> {
            g.due = v("due", g0?.due ?: "")
            if (g0 == null) { g.tasks = v("tasks", "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(60).map { Task(uid(), it, false) }; if (g.tasks.isEmpty()) return "Ajoute au moins une étape (une par ligne)." }
        }
        "challenge" -> {
            g.length = numOf(v("length", "${g0?.length ?: 30}")).let { if (it.isNaN()) 0 else Math.round(it).toInt() }; g.start = v("start", g0?.start?.ifEmpty { null } ?: today())
            if (g.length !in 2..365) return "La durée doit être comprise entre 2 et 365 jours."
        }
        "program" -> {
            g.base = numOf(v("base", fmtNum(g0?.base ?: 60.0))); g.step = numOf(v("step", fmtNum(g0?.step ?: 15.0))).let { if (it.isNaN()) 0.0 else it }
            g.unit = v("unit", g0?.unit ?: "min").trim().ifEmpty { "unités" }; g.weeks = numOf(v("weeks", "${g0?.weeks ?: 8}")).let { if (it.isNaN()) 0 else Math.round(it).toInt() }; g.start = v("start", g0?.start?.ifEmpty { null } ?: today())
            if (!(g.base > 0)) return "La cible de la semaine 1 doit être supérieure à zéro."
            if (g.weeks !in 2..52) return "Choisis entre 2 et 52 semaines."
        }
        "group" -> {
            g.target = numOf(v("target", fmtNum(g0?.targetOrNull ?: 500.0))); g.unit = v("unit", g0?.unit ?: "km").trim().ifEmpty { "unités" }; g.endDate = v("endDate", g0?.endDate ?: "")
            g.members = (listOf(g0?.userId ?: u.id) + (g0?.members ?: emptyList()) + members).distinct().toMutableList()
            if (!(g.target > 0)) return "Le total commun doit être supérieur à zéro."
            if (g.members!!.size < 2) return "Choisis au moins un ami pour former le groupe."
        }
        "duel" -> {
            g.unit = v("unit", g0?.unit ?: "pompes").trim().ifEmpty { "points" }; g.endDate = v("endDate", g0?.endDate?.ifEmpty { null } ?: ymd(todayDate().plusDays(7)))
            if (g0 == null) { val o = members.firstOrNull() ?: return "Choisis un adversaire parmi tes amis."; g.members = mutableListOf(u.id, o) } else g.members = g0.members
            if (g.endDate.isEmpty() || g.endDate < today()) return "Choisis une date de fin à partir d’aujourd’hui."
        }
        "period" -> {
            g.freq = if (v("freq", g0?.freq ?: "week") == "month") "month" else "week"
            g.days = dayList(t["pdays"]) ?: g0?.days?.takeIf { g0.freq == "week" } ?: listOf(0, 2, 4); g.mdays = dayList(t["pmdays"]) ?: g0?.mdays ?: listOf(1)
            g.start = v("pstart", g0?.start?.ifEmpty { null } ?: "18:00"); g.end = v("pend", g0?.end?.ifEmpty { null } ?: "19:00")
            if (if (g.freq == "week") g.days.isEmpty() else g.mdays.isEmpty()) return if (g.freq == "week") "Choisis au moins un jour de la semaine." else "Choisis au moins un jour du mois."
            if (g.end <= g.start) return "L’heure de fin doit être après l’heure de début."
        }
    }
    if (type !in SOCIAL_TYPES) {
        g.buddy = buddy; g.buddyVerify = buddy != null && bVerify; g.buddyMiss = buddy != null && bMiss
        if (buddy != null && buddy != g0?.buddy) pg.notify(buddy, "${u.name} compte sur toi comme binôme pour « $title ».", "verify")
    }
    if (g0 != null) {
        pg.setGoalGroup(u, g0.id, grp)
        g0.title = g.title; g0.icon = g.icon; g0.note = g.note; g0.visibility = g.visibility; g0.data = g.data
        g0.members = g.members; g0.buddy = g.buddy; g0.buddyVerify = g.buddyVerify; g0.buddyMiss = g.buddyMiss
        if (g0.type == "objective" && g0.completedAt != null && pg.objProgress(g0) < g0.target) g0.completedAt = null
        ui.sheet = Sheet.Goal(g0.id); ui.toast("Objectif mis à jour.")
    } else {
        pg.db.goals.add(g); pg.setGoalGroup(u, g.id, grp); ui.close()
        (g.members ?: emptyList()).filter { it != u.id }.forEach { pg.notify(it, "${u.name} t'a ajouté au ${if (type == "duel") "duel" else "défi de groupe"} « $title ».", "friend", g.id) }
        if (ui.tab != "goals") ui.tab = "today"
        ui.toast("« $title » est prêt. À toi de jouer !", "flame")
    }
    pg.store.commit(); pg.queueBadgeCheck(); pg.hooks.liveChanged()
    return null
}

@Composable
private fun TypeTile(k: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current; val tc = p.type(k); val info = typeInfo(k)
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(tc.c.copy(alpha = if (on) .32f else .12f)).border(if (on) 2.dp else 1.dp, if (on) tc.c else p.line, RoundedCornerShape(18.dp))
        .clickable(onClick = onClick).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Pi(info.icon, tint = tc.ink); Text(info.label, fontWeight = FontWeight.ExtraBold); Text(info.desc, fontSize = 12.sp, color = p.ink2, lineHeight = 15.sp)
    }
}

/* ================================================================== fiche d'un objectif */
@Composable
fun GoalSheet(ui: AppState, id: String) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val g = pg.goalById(id) ?: run { LaunchedEffect(Unit) { ui.close() }; return }
    val tc = p.type(g.type); val running = pg.timerOf(g) != null; tickingNow(running)
    val s = pg.statBig(g); val t = today(); val doneToday = pg.hasCheck(g.id, t); val own = g.userId == u.id
    SheetHead(ui, g.title) { IconBtn("star", if (pg.isFav(g)) "Retirer des favoris" else "Ajouter aux favoris", { pg.toggleFav(g.id) }, plain = true, tint = if (pg.isFav(g)) p.flame2 else p.ink3) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(tc.c.copy(alpha = .2f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Emoji(g.icon, 46.dp)
        Column { Text(s.v, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, color = tc.ink); Text(s.l, fontWeight = FontWeight.Bold, color = p.ink2) }
    }
    if (!g.archived) FlowRowCompat(gap = 8.dp) { GoalActions(ui, g, doneToday, running) }
    // Informations
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Info("Type") { TypeChip(g.type) }
        if (own) Info("Groupe") {
            FlowRowCompat(gap = 4.dp) {
                val cur = pg.groupOfGoal(u, g.id)?.id
                FChip("Sans groupe", cur == null, { pg.setGoalGroup(u, g.id, null); pg.store.commit() })
                u.goalGroups.forEach { gr -> FChip("${gr.icon} ${gr.name}", cur == gr.id, { pg.setGoalGroup(u, g.id, gr.id); pg.store.commit() }) }
            }
        }
        Info("Rythme") { Text(pg.metaLine(g), fontWeight = FontWeight.Bold) }
        if (g.type == "routine" && g.mode == "count") Info("Cette semaine") { Text("${pg.weekCount(g, weekStart(todayDate()))} / ${g.perWeek}", fontWeight = FontWeight.Bold) }
        if (g.type == "objective" && g.endDate.isNotEmpty()) Info("Date cible") { Text(fmtDate(g.endDate), fontWeight = FontWeight.Bold) }
        if (g.type == "abstinence") Info("Écarts") { Text("${pg.db.checkins.count { it.goalId == g.id && it.kind == "relapse" }}", fontWeight = FontWeight.Bold) }
        if (g.members != null) Info("Membres") { Text(g.members!!.joinToString(", ") { if (it == u.id) "Toi" else first(pg.userById(it)?.name) }, fontWeight = FontWeight.Bold) }
        else Info("Visibilité") { VIS[g.visibility]?.let { Chip(it.first, it.second) } }
        g.buddy?.let { b -> Info("Binôme") { Text("${if (b == u.id) "Toi" else pg.userById(b)?.name ?: ""}${if (g.buddyVerify) " · confirme les validations" else ""}${if (g.buddyMiss) " · alerté en cas d’oubli" else ""}", fontWeight = FontWeight.Bold) } }
        if (g.note.isNotEmpty()) Info("Pourquoi") { Text(g.note) }
    }
    GoalDetail(ui, g, own)
    if (g.type == "objective") Bar(minOf(100.0, pg.objProgress(g) / g.target * 100), color = tc.c)
    if (g.type != "deadline" && pg.hasCalendar(g.type)) { SectionTitle("5 dernières semaines"); GoalCalendar(pg, g) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FlowRowCompat(Modifier.weight(1f), gap = 6.dp) {
            PButton("Partager", { ui.open(Sheet.ShareHub(goalId = g.id)) }, icon = "share", small = true)
            if (own) PButton("Modifier", { ui.open(Sheet.GoalForm(editId = g.id)) }, icon = "edit", small = true)
            if (own) PButton(if (g.archived) "Désarchiver" else "Archiver", { archiveWithUndo(ui, g.id) }, icon = "archive", kind = "ghost", small = true)
            if (own) PButton("Supprimer", { ui.confirm("Supprimer cet objectif ?", "« ${g.title} » et toutes ses validations seront supprimés définitivement.", "Supprimer", back = Sheet.Goal(g.id)) { deleteWithUndo(ui, g.id) } }, icon = "trash", kind = "danger", small = true)
        }
    }
    if (!own) Muted("Créé par ${pg.userById(g.userId)?.name ?: ""}", small = true)
}

@Composable
private fun Info(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(110.dp), color = LocalPal.current.ink2, fontSize = 14.sp)
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun GoalActions(ui: AppState, g: Goal, doneToday: Boolean, running: Boolean) {
    val pg = ui.pg; val id = g.id
    val vbtn = @Composable { label: String -> PButton(if (doneToday) "Annuler la validation" else label, { doValidate(ui, id) }, icon = "check", kind = if (doneToday) "normal" else "primary") }
    when (g.type) {
        "routine" -> vbtn("Valider aujourd'hui")
        "abstinence" -> { vbtn("J'ai tenu aujourd'hui"); PButton("J'ai craqué", { ui.confirm("Noter un écart ?", "Ton compteur repart de zéro, mais ton historique reste. Un écart n'efface pas le chemin parcouru.", "Noter l'écart", back = Sheet.Goal(id)) { pg.relapse(id); ui.open(Sheet.Goal(id)) } }, kind = "danger") }
        "monthly" -> PButton("Ajouter au score de ${monthName(pg.curMonth())}", { doValidate(ui, id) }, icon = "plus", kind = "primary")
        "objective" -> if (g.completedAt == null) PButton("Ajouter un progrès", { doValidate(ui, id) }, icon = "plus", kind = "primary")
        "deadline" -> if (g.completedAt != null) PButton("Rouvrir", { pg.reopen(id) }) else PButton("Marquer comme terminé", { doValidate(ui, id) }, icon = "check", kind = "primary")
        "limit" -> { PButton("Noter 1 ${g.unit}", { pg.limitAdd(id) }, icon = "plus"); PButton("Retirer 1", { pg.limitSub(id) }, kind = "ghost"); vbtn("Valider ma journée") }
        "duration" -> { PButton(if (running) "Mettre en pause" else "Démarrer le minuteur", { pg.toggleTimer(g) }, icon = if (running) "pause" else "play", kind = "primary"); PButton("Ajouter des minutes", { ui.open(Sheet.Progress(id)) }, icon = "plus") }
        "measure" -> PButton("Nouvelle mesure", { doValidate(ui, id) }, icon = "plus", kind = "primary")
        "timed" -> { val k = pg.timedDate(g); if (g.days.contains(wdIdx(parseYmd(k)))) PButton(if (pg.hasCheck(id, k)) "Annuler la validation" else if (g.mode == "notafter") "J'ai tenu hier soir" else "Valider aujourd'hui", { doValidate(ui, id) }, icon = "check", kind = if (pg.hasCheck(id, k)) "normal" else "primary") }
        "challenge" -> if (g.failedAt != null || g.completedAt != null) PButton(if (g.failedAt != null) "Recommencer à partir d'aujourd'hui" else "Relancer un nouveau challenge", { pg.challengeRestart(id) }, icon = "repeat", kind = "primary") else if (pg.challengeDay(g) >= 1) vbtn("Valider le jour")
        "program" -> if (g.completedAt == null) PButton("Ajouter un progrès", { doValidate(ui, id) }, icon = "plus", kind = "primary")
        "group" -> if (g.completedAt == null && g.failedAt == null) PButton("Ajouter ma contribution", { doValidate(ui, id) }, icon = "plus", kind = "primary")
        "duel" -> if (g.completedAt == null) PButton("Ajouter à mon score", { doValidate(ui, id) }, icon = "plus", kind = "primary")
        "period" -> if (pg.periodOn(g, todayDate())) vbtn("Valider cette période")
    }
}

/** Détails propres au type (historiques, étapes, minuteur, duel…). */
@Composable
private fun GoalDetail(ui: AppState, g: Goal, own: Boolean) {
    val p = LocalPal.current; val pg = ui.pg; val tc = p.type(g.type); val me = pg.meId()
    when (g.type) {
        "monthly" -> {
            if (g.cumulate) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("${fmtNum(pg.objProgress(g))} ${g.unit}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp); Muted("cumulés sur ${pl(pg.monthKeys(g).size, "mois", "mois")}", small = true) }
            SectionTitle("Scores par mois")
            pg.monthKeys(g).reversed().take(6).forEach { k -> val v = pg.monthProgress(g, k); ProgLine(cap(monthName(k)), v / g.target * 100, "${fmtNum(v)}/${fmtNum(g.target)}", tc.c, hit = v >= g.target) }
        }
        "limit" -> {
            if (g.period == "week") Text(rich("Cette semaine : **${fmtNum(pg.limitUsed(g))}/${fmtNum(g.max)} ${g.unit}**"))
            SectionTitle("7 derniers jours")
            for (i in 6 downTo 0) { val k = ymd(todayDate().minusDays(i.toLong())); val v = pg.sumKind(g, "use") { it.date == k }
                ProgLine(WD_SHORT[wdIdx(parseYmd(k))], v / maxOf(g.max, 1.0) * 100, "${fmtNum(v)}/${fmtNum(g.max)}", tc.c, cur = i == 0, over = g.period == "day" && v > g.max) }
        }
        "duration" -> {
            val m = pg.durationLiveMins(g)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(fmtClock(m * 60), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, color = tc.ink)
                Muted("sur ${fmtNum(g.minutes)} min aujourd'hui")
                Bar(minOf(100.0, m / g.minutes * 100), color = tc.c)
            }
        }
        "measure" -> {
            SectionTitle("Tendance"); MeasureChart(pg, g)
            val pts = pg.measurePts(g).takeLast(5).reversed()
            if (pts.isNotEmpty()) { SectionTitle("Dernières mesures"); pts.forEach { c -> Row { Text(fmtDate(c.date), Modifier.weight(1f)); Text(pg.measureFmt(g, c.value) + pg.unitSfx(g), fontWeight = FontWeight.ExtraBold) } } }
        }
        "project" -> ProjectTasks(ui, g, own)
        "challenge" -> {
            SectionTitle("Les ${g.length} jours")
            val t = today()
            FlowRowCompat(gap = 4.dp) {
                for (i in 0 until g.length) { val k = ymd(parseYmd(g.start).plusDays(i.toLong())); val on = pg.hasCheck(g.id, k)
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(if (on) tc.c else if (k > t) p.ink.copy(alpha = .05f) else Color.Transparent)
                        .border(if (k == t) 2.dp else 1.dp, if (k == t) p.ink else if (!on && k < t) p.ink3 else p.line, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (on) Color(0xFF10102A) else p.ink2) } }
            }
            Muted("Un jour manqué interrompt le challenge : la régularité, c'est tout le jeu.", small = true)
        }
        "program" -> {
            SectionTitle("Les paliers"); val cw = pg.programWeek(g)
            for (w in 1..g.weeks) { val tg = pg.programTarget(g, w); val sm = if (w <= cw) pg.programSum(g, w) else 0.0; val hit = g.weeksDone.contains(w)
                ProgLine("Semaine $w", sm / tg * 100, if (w > cw) fmtNum(tg) else "${fmtNum(sm)}/${fmtNum(tg)}", tc.c, hit = hit, cur = w == cw, fut = w > cw) }
        }
        "group" -> {
            val tot = pg.groupTotal(g)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmtNum(round1(tot)), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, color = tc.ink)
                Muted("sur ${fmtNum(g.target)} ${g.unit} ensemble${if (g.endDate.isNotEmpty()) " · ${pg.endText(g.endDate)}" else ""}")
                Bar(minOf(100.0, tot / g.target * 100), Modifier.padding(top = 6.dp), tc.c)
            }
            SectionTitle("Contributions")
            pg.groupByUser(g).forEach { (id, v) -> val x = pg.userById(id); ProgLine(if (id == me) "Toi" else first(x?.name), if (tot > 0) v / tot * 100 else 0.0, fmtNum(round1(v)), tc.c, cur = id == me, lead = { Av(x, 22.dp) }) }
        }
        "duel" -> {
            val m = if (pg.isMember(g, me)) me else g.members?.firstOrNull() ?: g.userId; val o = pg.userById(pg.duelOpp(g, m)); val mu = pg.userById(m)
            val a = pg.duelScore(g, m); val b = pg.duelScore(g, o?.id ?: "")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                DuelSide(mu, if (m == me) "Toi" else first(mu?.name), a, a > b)
                Text("VS", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = tc.ink)
                DuelSide(o, first(o?.name), b, b > a)
            }
            Bar(if (a + b > 0) a / (a + b) * 100 else 50.0, color = tc.c)
            Muted("${g.unit} · ${if (g.completedAt != null) "duel terminé" else "fin le ${fmtDate(g.endDate)} (${pg.endText(g.endDate)})"}", small = true)
            val hist = pg.db.checkins.filter { it.goalId == g.id && it.kind == "progress" }.sortedByDescending { it.at }.take(5)
            if (hist.isNotEmpty()) { SectionTitle("Derniers coups"); hist.forEach { c -> Row { Text(if (c.userId == me) "Toi" else first(pg.userById(c.userId)?.name), Modifier.weight(1f), fontWeight = FontWeight.Bold); Muted(fmtDate(c.date), small = true); Spacer(Modifier.width(10.dp)); Text("+${fmtNum(c.value)}", fontWeight = FontWeight.ExtraBold) } } }
        }
        "period" -> {
            val next = pg.nextPeriods(g)
            if (next.isNotEmpty()) { SectionTitle("Prochaines périodes"); next.forEach { d -> Row { Text(cap(weekdayLongDate(d)), Modifier.weight(1f), fontWeight = FontWeight.ExtraBold); Text("${g.start}–${g.end}", color = p.ink2) } } }
        }
    }
}

@Composable
private fun DuelSide(u: User?, name: String, v: Double, lead: Boolean) {
    val p = LocalPal.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(if (lead) 1f else .75f)) {
        Box(Modifier.then(if (lead) Modifier.border(3.dp, p.flame, CircleShape) else Modifier).padding(3.dp)) { Av(u, 52.dp) }
        Text(name, fontWeight = FontWeight.Bold); Text(fmtNum(round1(v)), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
    }
}

@Composable
private fun ProjectTasks(ui: AppState, g: Goal, own: Boolean) {
    val p = LocalPal.current; val pg = ui.pg; val ts = g.tasks; val tc = p.type(g.type)
    var nt by remember { mutableStateOf("") }
    SectionTitle("Étapes")
    if (own && ts.size > 1) Muted("Utilise les flèches pour réordonner les étapes.", small = true)
    ts.forEachIndexed { i, task ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (task.done) tc.c.copy(alpha = .14f) else Color.Transparent).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(task.done, { pg.taskToggle(g.id, task.id) }, enabled = own)
            Text(task.t, Modifier.weight(1f), textDecoration = if (task.done) TextDecoration.LineThrough else null, color = if (task.done) p.ink2 else p.ink)
            if (own) {
                if (ts.size > 1) { IconBtn("up", "Monter l'étape ${task.t}", { pg.taskMove(g.id, task.id, i - 1) }, plain = true, size = 32.dp); IconBtn("down", "Descendre l'étape ${task.t}", { pg.taskMove(g.id, task.id, i + 1) }, plain = true, size = 32.dp) }
                IconBtn("x", "Supprimer l'étape ${task.t}", { pg.taskDel(g.id, task.id) }, plain = true, size = 32.dp)
            }
        }
    }
    if (own) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(nt, { nt = it }, "Ajouter une étape…", Modifier.weight(1f), max = 120)
        PButton("Ajouter", { if (nt.isNotBlank()) { pg.taskAdd(g.id, nt.trim()); nt = "" } }, small = true)
    }
}

/* ================================================================== saisie d'un progrès */
@Composable
fun ProgressSheet(ui: AppState, id: String) {
    val pg = ui.pg; val g = pg.goalById(id) ?: run { LaunchedEffect(Unit) { ui.close() }; return }
    val x = pg.progressInfo(g)
    var v by remember { mutableStateOf(x.value) }
    SheetHead(ui, x.title)
    Muted("${g.icon} ${g.title} : ${x.info}")
    Field(v, { v = it }, x.label, keyboard = if (x.text) KeyboardOptions(keyboardType = KeyboardType.Text) else NUM, max = 12)
    if (x.quick.isNotEmpty()) FlowRowCompat(gap = 6.dp) { x.quick.forEach { n -> PButton(if (x.quickAbsolute) fmtNum(n) else "+${fmtNum(n)}", { v = fmtNum(n) }, small = true) } }
    SheetFoot {
        PButton("Annuler", { ui.close() }, kind = "ghost")
        PButton("Valider", {
            val n = pg.parseProgress(g, v)
            if (!(n > 0)) { ui.toast(if (g.type == "measure") (if (g.timeFmt) "Temps invalide : écris par exemple 52:30." else "Valeur invalide : écris un nombre, par exemple 72,5.") else "Indique une quantité supérieure à zéro.", "x"); return@PButton }
            ui.close(); pg.addProgress(g.id, n)
        }, kind = "primary")
    }
}
