package fr.pepsgoal.app.ui.sheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.fmtDate
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import fr.pepsgoal.core.parseYmd
import fr.pepsgoal.core.today
import fr.pepsgoal.core.todayDate
import fr.pepsgoal.core.ymd
import fr.pepsgoal.core.vacationOf
import fr.pepsgoal.core.setVacation
import fr.pepsgoal.core.endVacation
import fr.pepsgoal.core.VACATION_MAX

/**
 * Fenêtres du bas : feuilles Braise Pop ([PSheet]), une à la fois comme dans la version web. Ouverture rebondissante,
 * voile allégé (la scène reste visible au-dessus), fermeture en glissant, par le voile ou Retour (prédictif : la feuille
 * suit le geste). Une confirmation courte s'affiche en dialogue ([PDialog]) par-dessus la feuille d'où elle vient.
 */
@Composable
fun SheetHost(ui: AppState) {
    val cur = ui.sheet
    // Grand écran : la fiche d'un objectif s'affiche dans le volet latéral (voir Shell), pas en fenêtre du bas
    val side = cur is Sheet.Goal && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 900 && ui.screens.isEmpty() && ui.fs == null
    val confirm = cur as? Sheet.Confirm
    val dialog = confirm != null && confirm.text.length <= 160
    val target = when { side -> null; dialog -> confirm!!.back; else -> cur }
    // Dernière feuille montrée : gardée le temps de sa sortie animée
    val last = remember { mutableStateOf<Sheet?>(null) }
    SideEffect { if (target != null) last.value = target }
    var backK by remember { mutableFloatStateOf(0f) }
    androidx.activity.compose.PredictiveBackHandler(enabled = target != null && !dialog) { events ->
        try { events.collect { e -> backK = e.progress }; ui.back() } catch (c: kotlin.coroutines.cancellation.CancellationException) { backK = 0f }
    }
    LaunchedEffect(target) { if (target != null) backK = 0f }
    val s = target ?: last.value
    if (s != null) {
        val slot = remember(s) { FootSlot() }
        PSheet(visible = target != null, onDismiss = { if (ui.sheet === s || ui.sheet === confirm) ui.back() }, onGone = { if (last.value === s) last.value = null },
            title = sheetTitle(s), handle = s !is Sheet.Goal, back = { backK }) {
            key(s) { CompositionLocalProvider(LocalInSheet provides true, LocalFootSlot provides slot) {
                val p = LocalPal.current
                val foot = slot.content
                // Contenu d'une fenêtre (ou de l'étape suivante dans la même fenêtre) : fondu et léger glissement vers le haut
                val shown = remember { androidx.compose.animation.core.Animatable(if (p.motion) 1f else 0f) }
                LaunchedEffect(Unit) { if (shown.value < 1f) shown.animateTo(1f, Motion.sheet()) }
                Column(Modifier.fillMaxWidth().imePadding().graphicsLayer { alpha = shown.value.coerceIn(0f, 1f); translationY = (1f - shown.value) * 18.dp.toPx() }) {
                    // Fiche d'objectif : le bandeau néon va jusqu'aux bords ; son contenu garde les marges de 16 dp
                    val goal = s is Sheet.Goal
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).then(if (foot == null) Modifier.navigationBarsPadding() else Modifier)
                        .padding(start = if (goal) 0.dp else 16.dp, end = if (goal) 0.dp else 16.dp, top = if (goal) 0.dp else 4.dp, bottom = if (foot == null) 16.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SheetBody(ui, s)
                    }
                    // Pied collé en bas, fond bg et filet de 2 dp : le bouton principal reste visible quand le contenu défile
                    if (foot != null) Row(Modifier.fillMaxWidth().background(p.bg).drawBehind { drawRect(p.edge, size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())) }
                        .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) { foot() }
                }
            } }
        }
    }
    if (dialog) {
        val c = confirm!!
        val cancel = { if (ui.sheet === c) { if (c.back != null) ui.sheet = c.back else ui.close() } }
        PDialog(cancel, if (c.danger) "⚠️" else "🤔", c.title, c.text, c.ok, { ui.sheet = c.back; if (c.back == null) ui.close(); c.run() }, danger = c.danger)
    }
}

/** Titre annoncé par TalkBack à l'ouverture d'une feuille. */
private fun sheetTitle(s: Sheet): String = when (s) {
    is Sheet.GoalForm -> "Nouvel objectif"; is Sheet.Goal -> "Fiche de l’objectif"; is Sheet.Notifs -> "Notifications"; is Sheet.Confirm -> s.title
    is Sheet.Avatar -> "Avatar"; is Sheet.Compose -> "Nouvelle publication"; is Sheet.Progress -> "Saisir une quantité"; else -> "Fenêtre"
}

/** Emplacement des boutons du pied de fenêtre, rendu hors de la zone qui défile. */
class FootSlot { var content by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null) }
val LocalFootSlot = staticCompositionLocalOf<FootSlot?> { null }

@Composable
private fun SheetBody(ui: AppState, s: Sheet) {
    when (s) {
        is Sheet.GoalForm -> GoalFormSheet(ui, s)
        is Sheet.Quick -> QuickSheet(ui, s.id)
        is Sheet.Rest -> RestSheet(ui)
        is Sheet.Evening -> EveningSheet(ui)
        is Sheet.Hard -> HardDaySheet(ui)
        is Sheet.Miss -> MissSheet(ui, s.id, s.date)
        is Sheet.Reward -> RewardSheet(ui, s.id)
        is Sheet.GoalQr -> GoalQrSheet(ui, s.id)
        is Sheet.Likers -> LikersSheet(ui, s.postId)
        is Sheet.ImportGoal -> ImportGoalSheet(ui, s.link)
        is Sheet.Goal -> GoalSheet(ui, s.id)
        is Sheet.Progress -> ProgressSheet(ui, s.id)
        is Sheet.User -> UserSheet(ui, s.id)
        is Sheet.Notifs -> NotifsSheet(ui)
        is Sheet.Confirm -> ConfirmSheet(ui, s)
        is Sheet.ShareHub -> ShareHubSheet(ui, s)
        is Sheet.ShareFeed -> ShareFeedSheet(ui, s.goalId, s.text)
        is Sheet.ShareText -> ShareTextSheet(ui, s.text)
        is Sheet.Avatar -> AvatarSheet(ui)
        is Sheet.Tuto -> LaunchedEffect(Unit) { ui.sheet = null; ui.fs = fr.pepsgoal.app.Fs.Tuto(s.step) }
        is Sheet.Legal -> LegalSheet(ui, s.doc)
        is Sheet.A11y -> { SheetHead(ui, "Accessibilité"); fr.pepsgoal.app.ui.screens.A11yRows(ui); Muted("Ces réglages s’appliquent à cet appareil, pour tous les comptes, dès l’écran de connexion.", small = true) }
        is Sheet.GoalGroups -> GoalGroupsSheet(ui)
        is Sheet.ModUser -> ModUserSheet(ui, s.id)
        is Sheet.Appeal -> AppealSheet(ui)
        is Sheet.Report -> ReportSheet(ui, s.postId)
        is Sheet.Compose -> ComposeSheet(ui)
        is Sheet.StoryNew -> StoryNewSheet(ui, s)
        is Sheet.Export -> ExportSheet(ui, s.json)
        is Sheet.Notes -> NotesSheet(ui)
        is Sheet.Pending -> PendingSheet(ui)
        is Sheet.Status -> fr.pepsgoal.app.ui.screens.StatusSheet(ui)
        is Sheet.Gift -> fr.pepsgoal.app.ui.screens.GiftSheet(ui, s.id, s.item)
        is Sheet.GiftItem -> fr.pepsgoal.app.ui.screens.GiftItemSheet(ui, s.key)
        is Sheet.Breath -> fr.pepsgoal.app.ui.screens.BreathSheet(ui)
        is Sheet.GroupNew -> GroupNewSheet(ui)
        is Sheet.GroupInfo -> GroupInfoSheet(ui, s.gid)
        is Sheet.DeleteAccount -> DeleteAccountSheet(ui)
        is Sheet.HomeCard -> fr.pepsgoal.app.ui.screens.HomeCardSheet(ui, s.key)
        is Sheet.QuickCmd -> fr.pepsgoal.app.ui.screens.QuickCmdSheet(ui)
        is Sheet.Quotes -> fr.pepsgoal.app.ui.screens.QuotesSheet(ui)
        is Sheet.Wallet -> fr.pepsgoal.app.ui.screens.WalletSheet(ui)
        is Sheet.Levels -> fr.pepsgoal.app.ui.screens.LevelsSheet(ui)
        is Sheet.Streak -> fr.pepsgoal.app.ui.screens.StreakSheet(ui)
    }
}

/** En-tête d'une fenêtre : titre en Unbounded 22 (2 lignes au plus) et bouton rond fermer. */
@Composable
fun SheetHead(ui: AppState, title: String, right: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, Modifier.weight(1f).semantics { heading() }, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        right()
        RoundBtn("x", "Fermer", { ui.close() })
    }
}

/** Pied de fenêtre : boutons alignés à droite. */
@Composable
fun SheetFoot(content: @Composable RowScope.() -> Unit) {
    val slot = LocalFootSlot.current
    if (slot == null) { Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, content = content); return }
    // Dans une fenêtre : les boutons sont affichés par la fenêtre elle-même, collés en bas
    SideEffect { slot.content = content }
    DisposableEffect(slot) { onDispose { slot.content = null } }
}

/** Choix d'une date (AAAA-MM-JJ) : liste qui ouvre le sélecteur Braise Pop ; `optional` permet de l'effacer. */
@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, optional: Boolean = false, min: String? = null, max: String? = null) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PSelect(label, if (value.isEmpty()) "Aucune date" else fmtDate(value), { open = true }, Modifier.weight(1f), icon = "cal", open = open)
        if (optional && value.isNotEmpty()) Box(Modifier.padding(bottom = 4.dp)) { RoundBtn("x", "Retirer la date", { onChange("") }) }
    }
    if (open) PDatePicker(value.ifEmpty { null }, onChange, { open = false }, min, max, label)
}

/** Puces des jours de la semaine (L M M J V S D). */
@Composable
fun DayChips(days: List<Int>, onChange: (List<Int>) -> Unit) {
    FlowRowCompat(gap = 6.dp) {
        fr.pepsgoal.core.WD.forEachIndexed { i, d -> FChip(d, i in days, { onChange(if (i in days) days - i else (days + i).sorted()) }) }
    }
}

/** Sélection d'amis (cases à cocher ou choix unique). */
@Composable
fun FriendPicker(ui: AppState, selected: List<String>, single: Boolean, exclude: List<String> = emptyList(), onChange: (List<String>) -> Unit) {
    val pg = ui.pg
    val fr = pg.friendsOf(pg.meId()).mapNotNull { pg.userById(it) }.filter { it.id !in exclude && !pg.isBlockedPair(pg.meId(), it.id) }
    if (fr.isEmpty()) { Muted("Ajoute d'abord des amis depuis l'onglet Amis.", small = true); return }
    Column(Modifier.then(if (single) Modifier.selectableGroup() else Modifier), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        fr.forEach { x ->
            val on = x.id in selected
            if (single) PRadioRow(x.name, "@${x.username}", on, { onChange(listOf(x.id)) }, leading = { Av(x, 36.dp) })
            else PCheckRow(x.name, "@${x.username}", on, { onChange(if (on) selected - x.id else selected + x.id) }, leading = { Av(x, 36.dp) })
        }
    }
}

@Composable
fun ConfirmSheet(ui: AppState, s: Sheet.Confirm) {
    SheetHead(ui, s.title)
    Muted(s.text)
    SheetFoot {
        PButton("Annuler", { if (s.back != null) ui.sheet = s.back else ui.close() }, kind = "ghost")
        if (s.danger) PButton(s.ok, { ui.close(); s.run() }, kind = "type", color = LocalPal.current.type("duel").c, icon = "trash")
        else PButton(s.ok, { ui.close(); s.run() }, kind = "primary")
    }
}

fun Sheet.isOpen(ui: AppState) = ui.sheet === this

/** Jours de repos : planifiés à l'avance, ils protègent la série sans dépenser de gel. */
@Composable
fun RestSheet(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val days = (0 until 28).map { todayDate().plusDays(it.toLong()) }
    val month = today().substring(0, 7)
    SheetHead(ui, "Jours de repos")
    Muted("Un jour de repos ne compte pas comme réussi, mais il ne casse ni ta série ni celles de tes objectifs, et ne dépense aucun gel. Pas de rappel ce jour-là. Au plus ${pg.REST_PER_MONTH} par mois, à planifier à l’avance.", small = true)
    Text("${u.restDays.count { it.startsWith(month) }}/${pg.REST_PER_MONTH} ce mois-ci", fontWeight = FontWeight.ExtraBold)
    FlowRowCompat(gap = 6.dp) {
        days.forEach { d ->
            val k = ymd(d); val on = k in u.restDays
            val lbl = if (k == today()) "Aujourd’hui" else "${d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, fr.pepsgoal.core.FR).removeSuffix(".")} ${d.dayOfMonth}"
            FChip(if (on) "😴 $lbl" else lbl, on, { pg.toggleRest(k)?.let { ui.toast(it, "x") } })
        }
    }
    val later = u.restDays.filter { it >= today() }
    if (later.isNotEmpty()) Muted("Prévus : ${later.joinToString(", ") { fmtDate(it) }}", small = true)
    VacationBlock(ui)
    SheetFoot { PButton("Terminé", { ui.close() }, kind = "primary") }
}

/** Mode vacances : une pause datée de toutes les séries, sans entamer les jours de repos, rappels coupés. */
@Composable
private fun VacationBlock(ui: AppState) {
    val pg = ui.pg; val vac = pg.vacationOf()
    var from by remember { mutableStateOf(vac?.first ?: today()) }
    var to by remember { mutableStateOf(vac?.second ?: ymd(todayDate().plusDays(6))) }
    SectionTitle("🏖️ Mode vacances")
    if (vac != null) {
        val now = today() >= vac.first
        Text(if (now) "En vacances jusqu’au ${fmtDate(vac.second)}" else "Vacances prévues du ${fmtDate(vac.first)} au ${fmtDate(vac.second)}", fontWeight = FontWeight.ExtraBold)
        Muted("Tes séries sont en pause et les rappels coupés pendant cette période. Valider reste possible si l’envie te prend.", small = true)
        PButton(if (now) "Revenir de vacances" else "Annuler ces vacances", {
            pg.endVacation(); ui.toast(if (now) "Bon retour ! Tes séries reprennent aujourd’hui." else "Vacances annulées.", "flame")
        }, icon = "x", small = true)
    } else {
        Muted("Pars l’esprit libre : jusqu’à $VACATION_MAX jours de pause d’un coup, sans toucher à tes ${pg.REST_PER_MONTH} jours de repos du mois.", small = true)
        DateField("Début", from, { from = it; if (to < it) to = it }, min = today())
        DateField("Fin", to, { to = it }, min = from)
        PButton("Partir en vacances", {
            pg.setVacation(from, to)?.let { ui.toast(it, "x") } ?: ui.toast("Bonnes vacances ! Tes séries t’attendront.", "sun")
        }, icon = "check", color = LocalPal.current.type("period").c, small = true)
    }
}
