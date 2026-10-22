package fr.pepsgoal.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Fs
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.screens.TimeButton
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.*

/* ---------------------------------------------------------------- Options de la fiche */

/** Rappel propre à l'objectif, à son heure. */
@Composable
fun RemindRow(ui: AppState, g: Goal, divider: Boolean = true) {
    val p = LocalPal.current; val pg = ui.pg
    if (g.type in setOf("objective", "monthly", "measure", "project", "program", "group", "duel", "deadline")) return
    var open by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf(g.remindAt ?: if (g.type == "timed") g.str("time", "08:00") else "08:00") }
    OptionRow("bell", p.type("period").c, p.type("project").c, "Rappel", g.remindAt?.let { "Chaque jour prévu à $it, s’il reste à faire" } ?: "À l’heure de ton choix, avec « Valider »", open, { open = !open }, divider) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Heure", Modifier.weight(1f), fontWeight = FontWeight.Bold); TimeButton(time, { time = it }, "Heure du rappel")
        }
        FlowRowCompat(gap = 6.dp) {
            PButton("Enregistrer", { g.remindAt = time; pg.store.commit(); open = false; fr.pepsgoal.app.sys.Reminders.reschedule(ui.app); ui.toast("Rappel à $time.", "bell") }, icon = "check", small = true, kind = "primary")
            if (g.remindAt != null) PButton("Retirer", { g.remindAt = null; pg.store.commit(); open = false }, small = true, kind = "ghost")
        }
    }
}

/** Validation automatique en restant à un emplacement enregistré. */
@Composable
fun AutoPlaceRow(ui: AppState, g: Goal, divider: Boolean = true) {
    val p = LocalPal.current; val pg = ui.pg; val ctx = LocalContext.current
    if (g.type !in AUTO_TYPES || g.steps.isNotEmpty()) return
    val places = pg.me()?.places.orEmpty()
    val cur = places.firstOrNull { it.id == g.autoPlace }
    var open by remember { mutableStateOf(false) }
    val bgPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) fr.pepsgoal.app.sys.GeoReminders.register(ctx) else ui.toast("Choisis « Toujours autoriser » pour la position, sinon la validation automatique ne peut pas se faire.", "pin")
    }
    val fine = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it } && android.os.Build.VERSION.SDK_INT >= 29 && !fr.pepsgoal.app.sys.GeoReminders.hasBackground(ctx)) bgPerm.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    fun perms() {
        if (!fr.pepsgoal.app.sys.GeoReminders.hasFine(ctx)) fine.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
        else if (android.os.Build.VERSION.SDK_INT >= 29 && !fr.pepsgoal.app.sys.GeoReminders.hasBackground(ctx)) bgPerm.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        else fr.pepsgoal.app.sys.GeoReminders.register(ctx)
    }
    OptionRow("check", p.type("routine").c, p.type("measure").c, "Validation sur place", cur?.let { "${it.icon} ${it.name} · après ${g.autoMin} min sur place" } ?: "Valider tout seul en restant à un emplacement", open, { open = !open }, divider) {
        if (places.isEmpty()) {
            Muted("Enregistre d’abord un emplacement (salle de sport, piscine…) dans Paramètres › Emplacements.", small = true)
            PButton("Ouvrir les emplacements", { ui.close(); ui.push(fr.pepsgoal.app.Screen.Settings("places")) }, icon = "pin", small = true)
        } else {
            Text("Emplacement", fontWeight = FontWeight.Bold)
            FlowRowCompat(gap = 6.dp) { places.forEach { pl -> FChip("${pl.icon} ${pl.name}", pl.id == g.autoPlace, { g.autoPlace = pl.id; pg.store.commit(); perms() }) } }
            Text("Temps sur place", fontWeight = FontWeight.Bold)
            FlowRowCompat(gap = 6.dp) { listOf(15, 30, 45, 60, 90).forEach { m -> FChip("$m min", g.autoMin == m, { g.autoMin = m; pg.store.commit(); fr.pepsgoal.app.sys.GeoReminders.register(ctx) }) } }
            Muted("Une notification te prévient, avec un bouton pour annuler si ce n’était pas pour ça.", small = true)
            if (cur != null) PButton("Retirer", { g.autoPlace = null; pg.store.commit(); fr.pepsgoal.app.sys.GeoReminders.register(ctx); open = false }, small = true, kind = "ghost")
        }
    }
}

/** Objectif d'extérieur : la météo peut proposer de le décaler. */
@Composable
fun OutdoorRow(ui: AppState, g: Goal, divider: Boolean = true) {
    val p = LocalPal.current; val pg = ui.pg
    if (pg.me()?.settings?.weather == null || g.type !in setOf("routine", "timed", "period", "duration", "challenge")) return
    var on by remember(g.id, ui.rev) { mutableStateOf(g.outdoor) }
    OptionRow("cloud", p.type("objective").c, p.type("period").c, "En extérieur", if (on) "Pluie, orage ou canicule : PepsGoal te proposera de le décaler" else "Non concerné par la météo", false, { on = !on; g.outdoor = on; pg.store.commit() }, divider,
        right = { PSwitch(on, { on = it; g.outdoor = it; pg.store.commit() }) })
}

/* ---------------------------------------------------------------- « Je n'ai pas pu » */

@Composable
fun MissSheet(ui: AppState, id: String, date: String) {
    val p = LocalPal.current; val pg = ui.pg; val g = pg.goalById(id) ?: run { LaunchedEffect(Unit) { ui.close() }; return }
    val cur = g.missWhy()[date]
    SheetHead(ui, "Je n’ai pas pu")
    Muted("${g.icon} ${g.title} · ${if (date == today()) "aujourd’hui" else fmtDate(date)}. Noter la raison t’aide à voir ce qui coince, sans culpabiliser.", small = true)
    FlowRowCompat(gap = 8.dp) {
        MISS_REASONS.forEach { (k, l) -> FChip(l, cur == k, { pg.setMissReason(g, date, k); ui.close(); ui.toast("C’est noté. Demain est un autre jour 💪", "heart") }) }
    }
    if (cur != null) PButton("Effacer la raison", { pg.setMissReason(g, date, null); ui.close() }, small = true, kind = "ghost")
}

/* ---------------------------------------------------------------- Récompenses */

/** Carte des récompenses (profil) : progression vers chacune, célébration quand elle est atteinte. */
@Composable
fun RewardsCard(ui: AppState, compact: Boolean = false) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val list = u.rewards.filter { it.claimedAt == null }
    if (compact && list.isEmpty()) return
    Glass {
        CardHead("Mes récompenses") { PButton("Ajouter", { ui.open(Sheet.Reward()) }, icon = "plus", small = true) }
        if (list.isEmpty()) Empty("Offre-toi quelque chose", "Fixe ta récompense : « un resto à 30 jours de série ». Elle t’attend au bout de l’effort.", icon = "star")
        list.forEach { r ->
            val n = pg.rewardProgress(r); val done = n >= r.target; val g = r.goalId?.let { pg.goalById(it) }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { ui.open(Sheet.Reward(r.id)) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(if (done) p.flame.copy(alpha = .25f) else p.ink.copy(alpha = .05f)), contentAlignment = Alignment.Center) { Emoji(r.emoji, 30.dp) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.title, fontWeight = FontWeight.ExtraBold)
                    Bar(minOf(100.0, n * 100.0 / r.target), color = if (done) p.ok else p.flame)
                    Muted(if (done) "Atteinte ! Tu l’as bien méritée." else "${minOf(n, r.target)}/${r.target} jours de série${g?.let { " · ${it.icon} ${it.title}" } ?: ""}", small = true)
                }
                if (done) PButton("Je me l’offre", { r.claimedAt = nowMs(); pg.store.commit(); Feedback.success(); ui.toast("Profite bien de « ${r.title} » 🎉", "star") }, small = true, kind = "primary")
            }
        }
        val claimed = u.rewards.filter { it.claimedAt != null }
        if (claimed.isNotEmpty() && !compact) Muted("Déjà offertes : ${claimed.takeLast(5).joinToString(" ") { it.emoji }}", small = true)
    }
}

private val REWARD_EMOJIS = listOf("🎁", "🍽️", "🍕", "🎬", "📚", "👟", "🎮", "💆", "🍰", "✈️", "🎧", "🌿")

@Composable
fun RewardSheet(ui: AppState, id: String?) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val edit = u.rewards.firstOrNull { it.id == id }
    var title by remember { mutableStateOf(edit?.title ?: "") }
    var emoji by remember { mutableStateOf(edit?.emoji ?: "🎁") }
    var target by remember { mutableIntStateOf(edit?.target ?: 30) }
    var goal by remember { mutableStateOf(edit?.goalId) }
    val goals = pg.myGoals(u.id).filter { !pg.isClosedGoal(it) && it.userId == u.id && it.type in setOf("routine", "abstinence", "limit", "duration", "cap", "timed", "period") }
    SheetHead(ui, if (edit == null) "Nouvelle récompense" else "Récompense")
    Field(title, { title = it }, "Ma récompense", placeholder = "Un resto, une nouvelle paire de baskets…", max = 60, icon = "star")
    FlowRowCompat(gap = 6.dp) { REWARD_EMOJIS.forEach { e -> FChip(e, emoji == e, { emoji = e }) } }
    SectionTitle("Quand ?")
    FlowRowCompat(gap = 6.dp) { listOf(7, 14, 21, 30, 60, 100, 365).forEach { n -> FChip("$n jours", target == n, { target = n }) } }
    SectionTitle("Série comptée")
    FlowRowCompat(gap = 6.dp) {
        FChip("🔥 Ma série globale", goal == null, { goal = null })
        goals.forEach { g -> FChip("${g.icon} ${g.title.take(20)}", goal == g.id, { goal = g.id }) }
    }
    SheetFoot {
        if (edit != null) PButton("Supprimer", { u.rewards.remove(edit); pg.store.commit(); ui.close() }, kind = "danger")
        PButton("Enregistrer", {
            if (title.isBlank()) return@PButton ui.toast("Donne un nom à ta récompense.", "x")
            if (edit != null) { edit.title = title.trim(); edit.emoji = emoji; edit.target = target; edit.goalId = goal; edit.reachedAt = null }
            else u.rewards.add(Reward(uid(), title.trim(), emoji, goal, target, nowMs()))
            pg.store.commit(); pg.checkRewards(); ui.close(); ui.toast("Récompense enregistrée : tiens bon !", "star")
        }, icon = "check", kind = "primary")
    }
}
