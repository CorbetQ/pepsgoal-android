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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import fr.pepsgoal.app.ui.theme.PLUM
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.heading
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.launch

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
    // Défi lancé depuis la fiche d'un ami : adversaire déjà choisi
    var members by remember { mutableStateOf(g0?.members?.filter { it != u.id } ?: s.preFields?.get("opponent")?.let { listOf(it) } ?: emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var showIcons by remember { mutableStateOf(false) }
    val social = pg.socialOn(u)
    val today0 = today(); val in7 = ymd(todayDate().plusDays(7))

    val ctx = androidx.compose.ui.platform.LocalContext.current; val scope = rememberCoroutineScope()
    val editing = g0 != null
    // Assistant en étapes : méthode (coach ou à la main), puis demande au coach ou type + réglages, puis résumé
    var fromAi by remember { mutableStateOf(!editing && s.preTitle != null) }
    var byAi by remember { mutableStateOf(s.preTitle != null) }
    var step by remember { mutableStateOf(when { editing -> "values"; s.preTitle != null && s.preType != null -> "summary"; s.preType != null -> "values"; else -> "mode" }) }
    var ask by remember { mutableStateOf("") }; var thinking by remember { mutableStateOf(false) }
    if (editing && t["health"] == null) fr.pepsgoal.app.sys.Health.links(ctx).firstOrNull { it.goalId == g0!!.id }?.let { t["health"] = it.metric }
    val steps = when { editing -> listOf("values" to "Réglages", "summary" to "Résumé"); step == "packs" -> listOf("mode" to "Méthode", "packs" to "Modèles"); fromAi -> listOf("mode" to "Méthode", "ai" to "Ta demande", "summary" to "Résumé"); else -> listOf("mode" to "Méthode", "type" to "Type", "values" to "Réglages", "summary" to "Résumé") }
    val go = { k: String -> err = null; step = k }

    SheetHead(ui, if (editing) "Modifier l’objectif" else "Nouvel objectif")
    StepBar(steps, step)
    if (step == "mode") {
        Muted("Comment veux-tu créer ton objectif ?")
        val aiOn = fr.pepsgoal.app.ai.LocalAi.on(ctx, ui.prefs.aiCreate)
        ModeCard("sparkle", "Avec le coach", if (aiOn) "Décris ton objectif en une phrase : le coach IA choisit le type, l’intitulé, l’emoji et les valeurs." else "Décris ton objectif en une phrase : PepsGoal le comprend et prépare tout (installe le coach IA dans Paramètres pour des propositions plus fines).", true) { fromAi = true; go("ai") }
        ModeCard("edit", "Manuellement", "Choisis le type d’objectif, puis règle toi-même l’intitulé, l’emoji et les valeurs.", false) { fromAi = false; go("type") }
        ModeCard("star", "À partir d’un modèle", "Des packs prêts à l’emploi : Matinée parfaite, Santé, Lecture, Zéro écran le soir, Sport, Sérénité.", false) { fromAi = false; go("packs") }
        SheetFoot { PButton("Annuler", { ui.close() }, kind = "ghost") }
    }
    if (step == "ai") {
        Field(ask, { ask = it }, "Ton objectif en une phrase", placeholder = "Ex. Faire 10 000 pas par jour", singleLine = false, minLines = 2, max = 200)
        FlowRowCompat(gap = 6.dp) { listOf("Faire 10 000 pas par jour", "Courir 3 fois par semaine", "Lire 12 livres cette année", "Arrêter le sucre", "Méditer 10 minutes par jour").forEach { ex -> FChip(ex, false, { ask = ex }) } }
        if (thinking) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pi("sparkle", size = 16.dp, tint = p.flameInk); Muted("Le coach prépare ton objectif…", Modifier.alpha(fr.pepsgoal.app.ui.screens.thinkingAlpha()), small = true)
        }
        Err(err)
        SheetFoot {
            PButton("Retour", { go("mode") }, kind = "ghost", icon = "chevronleft")
            PButton("Créer avec le coach", {
                val q = ask.trim()
                if (q.length < 4) { err = "Décris ton objectif en quelques mots."; return@PButton }
                if (thinking) return@PButton
                thinking = true; err = null
                scope.launch {
                    val dr = try { fr.pepsgoal.app.ai.Coach.createGoal(ctx, q) } catch (e: Exception) { fr.pepsgoal.app.ai.Coach.rules(q) }
                    type = dr.type; d.type = dr.type; title = dr.title; icon = dr.icon; t.clear(); t.putAll(dr.fields); byAi = dr.byAi
                    thinking = false; step = "summary"
                }
            }, icon = "sparkle", kind = "primary", enabled = !thinking)
        }
    }
    if (step == "packs") PacksStep(ui) { go("mode") }
    if (step == "type") {
        Muted("Quel genre d’objectif ?")
        TYPE_GROUPS.forEach { (lab, ks) ->
            val list = ks.filter { !(it in SOCIAL_TYPES && !social) }
            if (list.isNotEmpty()) {
                Eyebrow(lab)
                list.chunked(2).forEach { row -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { k -> TypeTile(k, k == type, Modifier.weight(1f).fillMaxHeight()) { type = k; d.type = k; go("values") } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } }
            }
        }
        SheetFoot { PButton("Retour", { go("mode") }, kind = "ghost", icon = "chevronleft"); PButton("Continuer", { go("values") }, kind = "primary") }
    }
    if (step == "summary") {
        val (g, e) = buildGoal(ui, u, g0, type, title, icon, note, vis, t, members, buddy, bVerify, bMiss)
        if (fromAi) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pi("sparkle", size = 16.dp, tint = p.flameInk)
            Muted(if (byAi) "Voici ce que le coach a préparé à partir de ta demande." else "Voici ce que PepsGoal a compris de ta demande.", small = true)
        } else Muted("Vérifie ton objectif avant de ${if (editing) "l’enregistrer" else "le créer"}.", small = true)
        if (g != null) GoalSummary(ui, u, g, grp, t["health"])
        Err(err ?: e)
        SheetFoot {
            PButton("Modifier", { fromAi = false; go("values") }, icon = "edit", kind = "ghost")
            PButton(if (editing) "Enregistrer" else "Valider et créer", { err = submitGoal(ui, u, g0, type, title, icon, note, vis, t, members, buddy, bVerify, bMiss, grp) }, icon = "check", kind = "primary", enabled = e == null)
        }
    }
    if (step == "values") {
    if (editing) TypeTile(type, true, Modifier.fillMaxWidth()) {}
    else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TypeChip(type); Spacer(Modifier.weight(1f)); PButton("Changer de type", { go("type") }, kind = "ghost", small = true)
    }
    Field(title, { title = it }, "Intitulé", placeholder = "Ex. Courir 30 minutes", max = 80)
    SetRow("Icône") {
        Box(Modifier.size(48.dp).clip(smoothShape(14.dp)).background(p.type(type).c.copy(alpha = .22f)).clickable { showIcons = !showIcons }, contentAlignment = Alignment.Center) { Emoji(icon, 30.dp) }
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
        "cap" -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(txt("target") { num(g0?.targetOrNull ?: 10000.0) }, { t["target"] = it }, "Cap par jour", Modifier.weight(1f), keyboard = NUM, max = 12)
                Field(txt("unit") { g0?.unit ?: "pas" }, { t["unit"] = it }, "Unité", Modifier.weight(1f), placeholder = "pas, L d'eau, pages…", max = 20)
            }
            Muted("Ajoute ta quantité au fil de la journée : l’objectif se valide tout seul dès que le cap est atteint, et la série compte les jours réussis.", small = true)
            if (fr.pepsgoal.app.sys.Health.available(ctx)) {
                SectionTitle("Remplir avec Santé (optionnel)")
                val h = t["health"].orEmpty()
                FlowRowCompat(gap = 6.dp) {
                    FChip("Non, à la main", h.isEmpty(), { t["health"] = "" })
                    fr.pepsgoal.app.sys.Health.METRICS.filter { it.kind == "num" && it.op == ">=" && it.key != "sleep" }.forEach { m ->
                        FChip("${m.emoji} ${m.label}", h == m.key, { t["health"] = m.key; if (t["unit"].isNullOrBlank() || t["unit"] == "pas" || fr.pepsgoal.app.sys.Health.METRICS.any { x -> x.unit == t["unit"] }) t["unit"] = m.unit })
                    }
                }
                if (h.isNotEmpty()) Muted("La mesure du jour lue par Health Connect remplit le compteur (accès à autoriser dans Paramètres › Santé).", small = true)
            }
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
        if (editing) PButton("Annuler", { ui.close() }, kind = "ghost") else PButton("Retour", { go("type") }, kind = "ghost", icon = "chevronleft")
        PButton("Voir le résumé", {
            err = buildGoal(ui, u, g0, type, title, icon, note, vis, t, members, buddy, bVerify, bMiss).second
            if (err == null) step = "summary"
        }, kind = "primary")
    }
    }
}

/** Indicateur d'étapes de l'assistant de création. */
@Composable
private fun StepBar(steps: List<Pair<String, String>>, cur: String) {
    val p = LocalPal.current; val i = steps.indexOfFirst { it.first == cur }.coerceAtLeast(0)
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "Étape ${i + 1} sur ${steps.size} : ${steps[i].second}" }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { k, _ -> Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(if (k <= i) p.flame else p.ink.copy(alpha = .12f))) }
        }
        Text("Étape ${i + 1}/${steps.size} · ${steps[i].second}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
    }
}

/** Grande carte de choix (coach ou création manuelle). */
@Composable
private fun ModeCard(icon: String, title: String, desc: String, ai: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().bounce(inter, .97f).then(if (ai) Modifier.border(2.dp, p.flame, smoothShape(20.dp)) else Modifier).clip(smoothShape(20.dp))
        .background(if (ai) p.flame.copy(alpha = .12f) else p.ink.copy(alpha = .05f)).border(1.dp, if (ai) p.flame.copy(alpha = .5f) else p.line, smoothShape(20.dp))
        .springClick(inter, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconTile(icon, if (ai) p.type("program").c else p.type("routine").c, if (ai) p.type("measure").c else p.type("objective").c, 48.dp, 16.dp)
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp); Muted(desc, small = true) }
        Text("›", color = p.ink3, fontSize = 24.sp)
    }
}

/** Résumé de l'objectif avant sa création. */
@Composable
private fun GoalSummary(ui: AppState, u: User, g: Goal, grp: String?, health: String?) {
    val p = LocalPal.current; val pg = ui.pg; val tc = p.type(g.type)
    Column(Modifier.fillMaxWidth().clip(smoothShape(22.dp)).background(tc.c.copy(alpha = .16f)).border(1.dp, tc.c.copy(alpha = .5f), smoothShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalIcon(g, 54.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(g.title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp); TypeChip(g.type) }
        }
        val lines = buildList {
            add("target" to summaryLine(pg, g))
            add("eye" to "Visible : ${VIS[g.visibility]?.first?.lowercase() ?: g.visibility}")
            g.buddy?.let { b -> add("shield" to "Binôme : ${pg.userById(b)?.name ?: "un ami"}${if (g.buddyVerify) ", qui confirme tes validations" else ""}") }
            g.members?.filter { it != u.id }?.takeIf { it.isNotEmpty() }?.let { m -> add("users" to "Avec ${m.mapNotNull { pg.userById(it)?.let { x -> first(x.name) } }.joinToString(", ")}") }
            u.goalGroups.firstOrNull { it.id == grp }?.let { add("folder" to "Groupe : ${it.icon} ${it.name}") }
            health?.takeIf { it.isNotEmpty() }?.let { h -> fr.pepsgoal.app.sys.Health.metric(h)?.let { add("heart" to "Rempli automatiquement avec Santé : ${it.label.lowercase()}") } }
            if (g.note.isNotBlank()) add("star" to "« ${g.note} »")
        }
        lines.forEach { (ic, l) -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pi(ic, size = 16.dp, tint = tc.ink); Text(l, fontSize = 14.sp) } }
    }
}

/** Phrase qui décrit le fonctionnement de l'objectif. */
private fun summaryLine(pg: Pg, g: Goal): String = when (g.type) {
    "cap" -> "Chaque jour, atteindre ${fmtNum(g.target)}${pg.unitSfx(g)} : la journée se valide dès que le cap est atteint."
    "objective" -> "Atteindre ${fmtNum(g.target)}${pg.unitSfx(g)} au total${if (g.endDate.isNotEmpty()) " d’ici le ${fmtDate(g.endDate)}" else ""}."
    "monthly" -> "${fmtNum(g.target)}${pg.unitSfx(g)} chaque mois${if (g.cumulate) ", avec un total cumulé" else ""}."
    "deadline" -> "À terminer avant le ${fmtDate(g.due)}."
    "routine" -> if (g.mode == "days") "À faire ${pg.daysLabel(g.days)}." else "${g.perWeek} fois par semaine, les jours que tu veux."
    "abstinence" -> "Tenir sans, jour après jour, depuis le ${fmtDate(g.since)}."
    "limit" -> "Pas plus de ${fmtNum(g.max)}${pg.unitSfx(g)} par ${if (g.period == "week") "semaine" else "jour"}."
    "duration" -> "${fmtNum(g.minutes)} minutes par jour, avec le minuteur."
    "challenge" -> "${g.length} jours d’affilée à partir du ${fmtDate(g.start)}."
    "program" -> "${fmtNum(g.base)}${pg.unitSfx(g)} la première semaine, puis +${fmtNum(g.step)} chaque semaine pendant ${g.weeks} semaines."
    "project" -> "${pl(g.tasks.size, "étape", "étapes")} à cocher${if (g.due.isNotEmpty()) " avant le ${fmtDate(g.due)}" else ""}."
    else -> pg.metaLine(g)
}

private fun dayList(s: String?): List<Int>? = s?.let { if (it.isEmpty()) emptyList() else it.split(",").mapNotNull { x -> x.toIntOrNull() } }
private fun numOf(s: String?): Double = s?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: Double.NaN

/** Valide le formulaire et crée (ou met à jour) l'objectif. Renvoie un message d'erreur, sinon null. */
private fun submitGoal(ui: AppState, u: User, g0: Goal?, type: String, title0: String, icon: String, note: String, vis: String, t: Map<String, String>,
                       members: List<String>, buddy: String?, bVerify: Boolean, bMiss: Boolean, grp: String?): String? {
    val pg = ui.pg; val title = title0.trim()
    val (g, e) = buildGoal(ui, u, g0, type, title0, icon, note, vis, t, members, buddy, bVerify, bMiss)
    if (e != null || g == null) return e
    if (type !in SOCIAL_TYPES && buddy != null && buddy != g0?.buddy) pg.notify(buddy, "${u.name} compte sur toi comme binôme pour « $title ».", "verify")
    // Cap quotidien relié à Santé : la mesure du jour remplit la quantité
    if (type == "cap") {
        val ctx = fr.pepsgoal.app.PepsApp.instance; val h = t["health"].orEmpty()
        val links = fr.pepsgoal.app.sys.Health.links(ctx).filter { it.goalId != g.id } + (if (h.isNotEmpty()) listOf(fr.pepsgoal.app.sys.Health.Link(g.id, h, g.target, ">=")) else emptyList())
        fr.pepsgoal.app.sys.Health.saveLinks(ctx, links)
        if (h.isNotEmpty()) ui.scope.launch { fr.pepsgoal.app.sys.Health.sync(ctx, force = true); ui.bump() }
    }
    return saveGoal(ui, u, g0, g, type, title, grp)
}

/** Construit l'objectif à partir du formulaire, sans l'enregistrer (résumé, vérification). */
private fun buildGoal(ui: AppState, u: User, g0: Goal?, type: String, title0: String, icon: String, note: String, vis: String, t: Map<String, String>,
                      members: List<String>, buddy: String?, bVerify: Boolean, bMiss: Boolean): Pair<Goal?, String?> {
    val (g, e) = buildGoal0(ui, u, g0, type, title0, icon, note, vis, t, members)
    if (g != null && type !in SOCIAL_TYPES) { g.buddy = buddy; g.buddyVerify = buddy != null && bVerify; g.buddyMiss = buddy != null && bMiss }
    return g to e
}

internal fun buildGoal0(ui: AppState, u: User, g0: Goal?, type: String, title0: String, icon: String, note: String, vis: String, t: Map<String, String>,
                       members: List<String>): Pair<Goal?, String?> {
    val pg = ui.pg; val title = title0.trim()
    fun bad(m: String) = null to m
    if (title.isEmpty()) return bad("Donne un intitulé à ton objectif.")
    val g = Goal(g0?.id ?: uid(), g0?.userId ?: u.id, type, title, icon, note.trim(), vis, g0?.createdAt ?: nowMs())
    g.data = g0?.let { LinkedHashMap(it.data) } ?: linkedMapOf()
    if (g0 == null) { g.milestones = emptyList(); g.monthsDone = emptyList(); g.days = emptyList(); g.perWeek = 3 }
    fun v(k: String, def: String) = t[k] ?: def
    when (type) {
        "monthly", "objective" -> {
            g.target = numOf(v("target", fmtNum(g0?.targetOrNull ?: if (type == "monthly") 20.0 else 10.0))); g.unit = v("unit", g0?.unit ?: "").trim()
            if (!(g.target > 0)) return bad(if (type == "monthly") "La cible mensuelle doit être un nombre supérieur à zéro." else "La cible doit être un nombre supérieur à zéro.")
            if (type == "monthly") g.cumulate = v("cumulate", if (g0 == null || g0.cumulate) "1" else "") == "1" else g.endDate = v("endDate", g0?.endDate ?: "")
        }
        "deadline" -> { g.due = v("due", g0?.due?.ifEmpty { null } ?: ymd(todayDate().plusDays(7))); if (g.due.isEmpty()) return bad("Choisis une date limite.") }
        "routine" -> {
            g.mode = v("mode", g0?.mode ?: "days"); g.days = dayList(t["days"]) ?: g0?.days ?: listOf(0, 2, 4); g.perWeek = numOf(v("perWeek", "${g0?.perWeek ?: 3}")).let { if (it.isNaN()) 0 else it.toInt() }
            if (g.mode == "days" && g.days.isEmpty()) return bad("Choisis au moins un jour de la semaine.")
            if (g.mode == "count" && g.perWeek !in 1..7) return bad("Choisis entre 1 et 7 fois par semaine.")
        }
        "abstinence" -> g.since = v("since", g0?.since?.ifEmpty { null } ?: today())
        "limit" -> {
            val raw = v("max", fmtNum(g0?.max ?: 2.0)); g.max = numOf(raw); g.unit = v("unit", g0?.unit ?: "").trim().ifEmpty { "unités" }; g.period = v("period", g0?.period ?: "day")
            if (raw.isBlank() || !(g.max >= 0)) return bad("Indique la limite à ne pas dépasser.")
        }
        "cap" -> {
            g.target = numOf(v("target", fmtNum(g0?.targetOrNull ?: 10000.0))); g.unit = v("unit", g0?.unit ?: "pas").trim()
            if (!(g.target > 0)) return bad("Le cap quotidien doit être un nombre supérieur à zéro.")
        }
        "duration" -> { g.minutes = numOf(v("minutes", fmtNum(g0?.minutes ?: 30.0))); if (!(g.minutes > 0)) return bad("Indique une durée quotidienne en minutes.") }
        "measure" -> {
            g.timeFmt = v("timeFmt", if (g0?.timeFmt == true) "1" else "") == "1"; g.unit = v("unit", g0?.unit ?: "").trim(); g.dir = v("dir", g0?.dir ?: "down")
            val tr = v("mtarget", g0?.targetOrNull?.let { pg.measureFmt(g0, it) } ?: "").trim()
            if (tr.isEmpty()) g.data["target"] = kotlinx.serialization.json.JsonNull
            else { val x = pg.parseProgress(g, tr); if (!(x > 0)) return bad(if (g.timeFmt) "Cible invalide : écris un temps comme 50:00." else "La cible doit être un nombre."); g.target = x }
            if (!g.timeFmt && g.unit.isEmpty()) return bad("Indique une unité (kg, mots/min…), ou coche « valeur en temps ».")
        }
        "timed" -> { g.mode = v("tmode", g0?.mode ?: "before"); g.time = v("time", g0?.time ?: "07:00"); g.days = dayList(t["tdays"]) ?: g0?.days ?: (0..6).toList(); if (g.days.isEmpty()) return bad("Choisis au moins un jour.") }
        "project" -> {
            g.due = v("due", g0?.due ?: "")
            if (g0 == null) { g.tasks = v("tasks", "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(60).map { Task(uid(), it, false) }; if (g.tasks.isEmpty()) return bad("Ajoute au moins une étape (une par ligne).") }
        }
        "challenge" -> {
            g.length = numOf(v("length", "${g0?.length ?: 30}")).let { if (it.isNaN()) 0 else Math.round(it).toInt() }; g.start = v("start", g0?.start?.ifEmpty { null } ?: today())
            if (g.length !in 2..365) return bad("La durée doit être comprise entre 2 et 365 jours.")
        }
        "program" -> {
            g.base = numOf(v("base", fmtNum(g0?.base ?: 60.0))); g.step = numOf(v("step", fmtNum(g0?.step ?: 15.0))).let { if (it.isNaN()) 0.0 else it }
            g.unit = v("unit", g0?.unit ?: "min").trim().ifEmpty { "unités" }; g.weeks = numOf(v("weeks", "${g0?.weeks ?: 8}")).let { if (it.isNaN()) 0 else Math.round(it).toInt() }; g.start = v("start", g0?.start?.ifEmpty { null } ?: today())
            if (!(g.base > 0)) return bad("La cible de la semaine 1 doit être supérieure à zéro.")
            if (g.weeks !in 2..52) return bad("Choisis entre 2 et 52 semaines.")
        }
        "group" -> {
            g.target = numOf(v("target", fmtNum(g0?.targetOrNull ?: 500.0))); g.unit = v("unit", g0?.unit ?: "km").trim().ifEmpty { "unités" }; g.endDate = v("endDate", g0?.endDate ?: "")
            g.members = (listOf(g0?.userId ?: u.id) + (g0?.members ?: emptyList()) + members).distinct().toMutableList()
            if (!(g.target > 0)) return bad("Le total commun doit être supérieur à zéro.")
            if (g.members!!.size < 2) return bad("Choisis au moins un ami pour former le groupe.")
        }
        "duel" -> {
            g.unit = v("unit", g0?.unit ?: "pompes").trim().ifEmpty { "points" }; g.endDate = v("endDate", g0?.endDate?.ifEmpty { null } ?: ymd(todayDate().plusDays(7)))
            if (g0 == null) { val o = members.firstOrNull() ?: return bad("Choisis un adversaire parmi tes amis."); g.members = mutableListOf(u.id, o) } else g.members = g0.members
            if (g.endDate.isEmpty() || g.endDate < today()) return bad("Choisis une date de fin à partir d’aujourd’hui.")
        }
        "period" -> {
            g.freq = if (v("freq", g0?.freq ?: "week") == "month") "month" else "week"
            g.days = dayList(t["pdays"]) ?: g0?.days?.takeIf { g0.freq == "week" } ?: listOf(0, 2, 4); g.mdays = dayList(t["pmdays"]) ?: g0?.mdays ?: listOf(1)
            g.start = v("pstart", g0?.start?.ifEmpty { null } ?: "18:00"); g.end = v("pend", g0?.end?.ifEmpty { null } ?: "19:00")
            if (if (g.freq == "week") g.days.isEmpty() else g.mdays.isEmpty()) return bad(if (g.freq == "week") "Choisis au moins un jour de la semaine." else "Choisis au moins un jour du mois.")
            if (g.end <= g.start) return bad("L’heure de fin doit être après l’heure de début.")
        }
    }
    return g to null
}

private fun saveGoal(ui: AppState, u: User, g0: Goal?, g: Goal, type: String, title: String, grp: String?): String? {
    val pg = ui.pg
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
    Column(modifier.clip(smoothShape(18.dp)).background(tc.c.copy(alpha = if (on) .32f else .12f)).border(if (on) 2.dp else 1.dp, if (on) tc.c else p.line, smoothShape(18.dp))
        .clickable(onClick = onClick).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Pi(info.icon, tint = tc.ink); Text(info.label, fontWeight = FontWeight.ExtraBold); Text(info.desc, fontSize = 12.sp, color = p.ink2, lineHeight = 15.sp)
    }
}

/* ================================================================== fiche d'un objectif */
@Composable
fun GoalSheet(ui: AppState, id: String) {
    ui.watch()
    val pg = ui.pg
    val g = pg.goalById(id) ?: run { LaunchedEffect(Unit) { ui.close() }; return }
    // Bandeau néon jusqu'aux bords, puis le contenu avec les marges de 16 dp
    GoalBanner(ui, g)
    Column(Modifier.fillMaxWidth().padding(horizontal = if (LocalInSheet.current) 16.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { GoalSheetBody(ui, g) }
}

/**
 * Bandeau de la fiche : 176 dp en aplat du néon du type, deux cercles blancs translucides, poignée prune, pilule
 * (type · moment), titre en Unbounded 28 prune et emoji Fluent de 96 dp incliné de 8°. Favori et fermer à droite.
 */
@Composable
private fun GoalBanner(ui: AppState, g: Goal) {
    val p = LocalPal.current; val pg = ui.pg
    val c = vivid(p.type(g.type).c)
    val moment = mapOf("morning" to "Matin", "afternoon" to "Après-midi", "evening" to "Soir", "day" to "Dans la journée")[momentOf(pg, g)]
    // L'emoji « arrive » avec un rebond
    val pop = remember { androidx.compose.animation.core.Animatable(if (p.motion) 1f else .55f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, Motion.pop()) }
    Box(Modifier.fillMaxWidth().heightIn(min = 176.dp).background(c).drawBehind {
        drawCircle(Color.White.copy(alpha = .22f), size.height * .7f, androidx.compose.ui.geometry.Offset(size.width * .86f, size.height * .55f))
        drawCircle(Color.White.copy(alpha = .16f), size.height * .22f, androidx.compose.ui.geometry.Offset(size.width * .55f, size.height * .1f))
    }) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(40.dp, 5.dp).clip(CircleShape).background(PLUM.copy(alpha = .35f)))
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 22.dp, top = 24.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = 8f - (1f - pop.value) * 25f }) { Emoji(g.shownIcon(), 96.dp) }
        Row(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 8.dp)) {
            IconBtn("star", if (pg.isFav(g)) "Retirer des favoris" else "Ajouter aux favoris", { pg.toggleFav(g.id) }, tint = if (pg.isFav(g)) p.flameInk else p.ink3)
            if (LocalInSheet.current) IconBtn("x", "Fermer", { ui.close() })
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(.66f).padding(start = 20.dp, bottom = 20.dp, top = 64.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = .5f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text("${typeInfo(g.type).label}${moment?.let { " · $it" } ?: ""}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PLUM, maxLines = 1)
            }
            Text(g.title, Modifier.semantics { heading() }, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 31.sp, color = PLUM, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ColumnScope.GoalSheetBody(ui: AppState, g: Goal) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val id = g.id
    val tc = p.type(g.type); val running = pg.timerOf(g) != null; tickingNow(running)
    val s = pg.statBig(g); val t = today(); val doneToday = pg.hasCheck(g.id, t); val own = g.userId == u.id
    // Carte d'action : grande valeur en Unbounded 48, barre de 12 dp, puis les boutons du type
    Glass {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FitText(s.v, Modifier.weight(1f, fill = false), size = 48, min = 22, weight = FontWeight.ExtraBold, family = Unbounded, color = p.ink)
            Text(s.l, Modifier.padding(bottom = 8.dp), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = p.ink2, maxLines = 2)
        }
        pg.barPct(g)?.let { Bar(it, Modifier.padding(top = 10.dp), tc.c, 12.dp) }
        if (own) PauseBanner(ui, g)
        if (!g.archived) FlowRowCompat(Modifier.padding(top = 12.dp), gap = 8.dp) {
        GoalActions(ui, g, doneToday, running)
        // Version mini : garde la série les jours sans énergie
        if (own && g.miniLabel != null && !pg.hasCheck(g.id, pg.stepDate(g)) && pg.todayPlan().due.any { it.g.id == g.id }) PButton("Mini : ${g.miniLabel}", { pg.validateMini(g.id)?.let { ui.toast(it, "x") } ?: ui.toast("Version mini validée : ta série continue 💪", "check") }, icon = "check")
        if (own && pg.isMiniDone(g)) Chip("Version mini", "check")
        // « Je n'ai pas pu » : noter la raison d'un jour qui ne se fera pas
        if (own && pg.todayPlan().due.any { it.g.id == g.id && !it.done }) PButton(g.missWhy()[today()]?.let { k -> MISS_REASONS.firstOrNull { it.first == k }?.second } ?: "Je n’ai pas pu", { ui.open(Sheet.Miss(g.id, today())) }, kind = "ghost")
        }
    }
    // Informations : carte à marche
    Glass {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    }
    GoalDetail(ui, g, own)
    if (g.type in STEP_TYPES && !pg.isClosedGoal(g)) StepsBlock(ui, g, own)
    if (own) SavingsCard(ui, g)
    GoalStatsBlock(ui, g)
    ProofsBlock(ui, g, own)
    FriendsRankCard(ui, g)
    if (own && !g.archived && !pg.isClosedGoal(g)) OptionsCard("Options") {
        RemindRow(ui, g, divider = false)
        MiniRow(ui, g)
        TrialRow(ui, g)
        PauseRow(ui, g)
        LadderRow(ui, g)
        ChainRow(ui, g)
        AutoPlaceRow(ui, g)
        NfcRow(ui, g)
        OutdoorRow(ui, g)
        PlanBRow(ui, g)
        SavingsRow(ui, g)
        fr.pepsgoal.app.ui.screens.GoalExtras61(ui, g)
        DuoRow(ui, g)
        AgendaRow(ui, g)
        GeoRow(ui, g)
        PinRow(ui, g)
    }
    if (g.type == "objective") Bar(minOf(100.0, pg.objProgress(g) / g.target * 100), color = tc.c)
    if (g.type != "deadline" && pg.hasCalendar(g.type)) Glass { CardHead("5 dernières semaines", emoji = "📅"); GoalCalendar(pg, g) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FlowRowCompat(Modifier.weight(1f), gap = 6.dp) {
            PButton("Partager", { ui.open(Sheet.ShareHub(goalId = g.id)) }, icon = "share", small = true)
            if (own && g.type !in setOf("group", "duel")) PButton("QR code", { ui.open(Sheet.GoalQr(g.id)) }, icon = "link", small = true)
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
        Text(label, Modifier.width(118.dp).padding(end = 8.dp), color = LocalPal.current.ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
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
        "duration" -> { PButton(if (running) "Mettre en pause" else "Démarrer le minuteur", { pg.toggleTimer(g) }, icon = if (running) "pause" else "play", kind = "primary"); PButton("Ajouter des minutes", { ui.open(Sheet.Progress(id)) }, icon = "plus"); PButton("Intervalles", { ui.close(); ui.fs = fr.pepsgoal.app.Fs.Intervals(id) }, icon = "timer") }
        "measure" -> PButton("Nouvelle mesure", { doValidate(ui, id) }, icon = "plus", kind = "primary")
        "cap" -> PButton("Ajouter", { ui.open(Sheet.Progress(id)) }, icon = "plus", kind = "primary")
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
        "cap" -> {
            val v = pg.capDay(g)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(fmtNum(v), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, color = tc.ink)
                Muted("sur ${fmtNum(g.target)}${pg.unitSfx(g)} aujourd'hui")
                Bar(minOf(100.0, v / g.target * 100), color = tc.c)
            }
            SectionTitle("Historique")
            QtyChart(g, g.target, g.unit) { k -> pg.capDay(g, k) }
        }
        "duration" -> {
            val m = pg.durationLiveMins(g)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(fmtClock(m * 60), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, color = tc.ink)
                Muted("sur ${fmtNum(g.minutes)} min aujourd'hui")
                Bar(minOf(100.0, m / g.minutes * 100), color = tc.c)
            }
            SectionTitle("Historique")
            QtyChart(g, g.minutes, "min") { k -> pg.durationMins(g, k) }
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

/** Fiche d'un objectif : rappel en arrivant à un lieu (ou en le quittant). */
@Composable
private fun GeoRow(ui: AppState, g: Goal) {
    val ctx = androidx.compose.ui.platform.LocalContext.current; val p = LocalPal.current; val scope = rememberCoroutineScope()
    var cur by remember(g.id) { mutableStateOf(fr.pepsgoal.app.sys.GeoReminders.of(ctx, g.id)) }
    var open by remember { mutableStateOf(false) }
    var radius by remember { mutableIntStateOf(cur?.radius ?: 150) }; var exit by remember { mutableStateOf(cur?.exit ?: false) }
    var label by remember { mutableStateOf(cur?.label ?: "") }; var busy by remember { mutableStateOf(false) }
    var bgOk by remember { mutableStateOf(fr.pepsgoal.app.sys.GeoReminders.hasBackground(ctx)) }
    val save: () -> Unit = {
        busy = true
        scope.launch {
            val loc = fr.pepsgoal.app.sys.GeoReminders.here(ctx)
            busy = false
            if (loc == null) { ui.toast("Position introuvable : active la localisation et réessaie.", "pin"); return@launch }
            val pl = fr.pepsgoal.app.sys.GeoReminders.Place(g.id, loc.latitude, loc.longitude, radius, exit, label.trim().take(40))
            fr.pepsgoal.app.sys.GeoReminders.set(ctx, pl); cur = pl; open = false
            ui.toast("Rappel enregistré pour ce lieu.", "pin")
        }
    }
    val bgPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        bgOk = ok; if (ok) fr.pepsgoal.app.sys.GeoReminders.register(ctx) else ui.toast("Choisis « Toujours autoriser » pour la position, sinon le rappel ne peut pas se déclencher.", "pin")
    }
    val finePerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) save() else ui.toast("Autorise la localisation pour créer un rappel par lieu.", "pin")
    }
    val c = cur
    OptionRow("pin", p.type("measure").c, p.type("routine").c, "Rappel par lieu",
        c?.let { "${if (it.exit) "En partant de" else "En arrivant à"} ${it.label.ifBlank { "l’endroit enregistré" }} · ${it.radius} m" } ?: "En arrivant à un endroit, ou en le quittant",
        open, { open = !open }) {
        if (c != null && !bgOk && android.os.Build.VERSION.SDK_INT >= 29) PButton("Autoriser la position en arrière-plan", { bgPerm.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) }, icon = "pin", small = true, kind = "primary")
        Seg(listOf(false to "En arrivant", true to "En partant"), exit, { exit = it })
        // Emplacements enregistrés (Paramètres › Emplacements) : un geste suffit
        val saved = ui.pg.me()?.places.orEmpty()
        Text("Mes emplacements", fontWeight = FontWeight.Bold)
        if (saved.isEmpty()) Muted("Enregistre ta maison, ton travail… sur une carte pour les choisir ici d’un geste.", small = true)
        FlowRowCompat(gap = 6.dp) {
            saved.forEach { pl -> FChip("${pl.icon} ${pl.name}", c != null && c.label == pl.name && kotlin.math.abs(c.lat - pl.lat) < 1e-5, {
                val gp = fr.pepsgoal.app.sys.GeoReminders.Place(g.id, pl.lat, pl.lon, pl.radius, exit, pl.name)
                fr.pepsgoal.app.sys.GeoReminders.set(ctx, gp); cur = gp; open = false
                ui.toast("Rappel ${if (exit) "en partant de" else "en arrivant à"} ${pl.name}.", "pin")
                if (!bgOk && android.os.Build.VERSION.SDK_INT >= 29) bgPerm.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }) }
            FChip("＋ Gérer", false, { ui.close(); ui.push(fr.pepsgoal.app.Screen.Settings("places")) })
        }
        Text("Ou ma position actuelle", Modifier.padding(top = 4.dp), fontWeight = FontWeight.Bold)
        Field(label, { label = it }, "Nom du lieu", placeholder = "Salle de sport, maison, bureau…", max = 40)
        FlowRowCompat(gap = 6.dp) { listOf(100, 150, 300, 500).forEach { r -> FChip("$r m", radius == r, { radius = r }) } }
        Muted("Place-toi à l’endroit voulu : PepsGoal enregistre ta position actuelle. Le rappel n’arrive qu’une fois par jour, si l’objectif reste à faire.", small = true)
        FlowRowCompat(gap = 6.dp) {
            PButton(if (busy) "Localisation…" else if (c != null) "Mettre à jour ici" else "Utiliser ma position", {
                if (fr.pepsgoal.app.sys.GeoReminders.hasFine(ctx)) save()
                else finePerm.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
            }, icon = "pin", small = true, kind = "primary", enabled = !busy)
            if (c != null) PButton("Retirer", { fr.pepsgoal.app.sys.GeoReminders.remove(ctx, g.id); cur = null; open = false }, small = true, kind = "ghost")
        }
    }
}
