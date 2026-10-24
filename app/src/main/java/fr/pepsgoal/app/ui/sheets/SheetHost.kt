package fr.pepsgoal.app.ui.sheets

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import fr.pepsgoal.app.ui.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.fmtDate
import fr.pepsgoal.core.parseYmd
import fr.pepsgoal.core.today
import fr.pepsgoal.core.todayDate
import fr.pepsgoal.core.ymd
import fr.pepsgoal.core.vacationOf
import fr.pepsgoal.core.setVacation
import fr.pepsgoal.core.endVacation
import fr.pepsgoal.core.VACATION_MAX

/** Fenêtres du bas (feuilles modales Material 3), une à la fois comme dans la version web. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(ui: AppState) {
    val s = ui.sheet ?: return
    val p = LocalPal.current
    // Grand écran : la fiche d'un objectif s'affiche dans le volet latéral (voir Shell), pas en fenêtre du bas
    if (s is Sheet.Goal && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 900 && ui.screens.isEmpty() && ui.fs == null) return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // La fenêtre s'arrête sous la barre d'état ; ses cartes n'ont pas de flou (autre fenêtre que l'arrière-plan)
    val slot = remember(s) { FootSlot() }
    ModalBottomSheet(onDismissRequest = { ui.back() }, modifier = Modifier.statusBarsPadding(), sheetState = state, containerColor = if (p.transp || p.contrast) p.bg else p.glassStrong.copy(alpha = 1f), contentColor = p.ink,
        scrimColor = p.scrim, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        // Poignée bien visible : on comprend qu'un glissement vers le bas ferme la fenêtre
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(50)).background(p.ink.copy(alpha = .2f))) }) {
        key(s) { CompositionLocalProvider(LocalHaze provides null, LocalInSheet provides true, LocalOnCard provides true, LocalFootSlot provides slot) {
            val foot = slot.content
            // Contenu d'une fenêtre (ou de l'étape suivante dans la même fenêtre) : fondu et léger glissement vers le haut
            val shown = remember { androidx.compose.animation.core.Animatable(if (p.motion) 1f else 0f) }
            LaunchedEffect(Unit) { if (shown.value < 1f) shown.animateTo(1f, androidx.compose.animation.core.tween(260, easing = Motion.Emphasized)) }
            Column(Modifier.fillMaxWidth().imePadding().graphicsLayer { alpha = shown.value; translationY = (1f - shown.value) * 14.dp.toPx() }) {
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).then(if (foot == null) Modifier.navigationBarsPadding() else Modifier)
                    .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = if (foot == null) 26.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SheetBody(ui, s)
                }
                // Boutons d'action toujours visibles en bas, même quand le contenu défile
                if (foot != null) Row(Modifier.fillMaxWidth().drawBehind { drawRect(p.line, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) }
                    .navigationBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) { foot() }
            }
        } }
    }
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
    }
}

/** En-tête d'une fenêtre : titre et bouton fermer. */
@Composable
fun SheetHead(ui: AppState, title: String, right: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f).semantics { heading() }, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        right()
        IconBtn("x", "Fermer", { ui.close() }, size = 38.dp)
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

/** Choix d'une date (AAAA-MM-JJ) avec le sélecteur d'Android ; `optional` permet de l'effacer. */
@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, optional: Boolean = false, min: String? = null) {
    val ctx = LocalContext.current
    SetRow(label, if (value.isEmpty()) "Aucune date" else fmtDate(value)) {
        PButton(if (value.isEmpty()) "Choisir" else "Changer", {
            val d = if (value.isEmpty()) parseYmd(today()) else parseYmd(value)
            DatePickerDialog(ctx, { _, y, m, dd -> onChange(ymd(java.time.LocalDate.of(y, m + 1, dd))) }, d.year, d.monthValue - 1, d.dayOfMonth).apply {
                min?.let { datePicker.minDate = java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
            }.show()
        }, icon = "cal", small = true)
        if (optional && value.isNotEmpty()) PButton("Retirer", { onChange("") }, kind = "ghost", small = true)
    }
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        fr.forEach { x ->
            val on = x.id in selected
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Av(x, 34.dp); Text(x.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                if (single) androidx.compose.material3.RadioButton(on, { onChange(listOf(x.id)) })
                else androidx.compose.material3.Checkbox(on, { onChange(if (on) selected - x.id else selected + x.id) })
            }
        }
    }
}

@Composable
fun ConfirmSheet(ui: AppState, s: Sheet.Confirm) {
    SheetHead(ui, s.title)
    Muted(s.text)
    SheetFoot {
        PButton("Annuler", { if (s.back != null) ui.sheet = s.back else ui.close() }, kind = "ghost")
        PButton(s.ok, { ui.close(); s.run() }, kind = if (s.danger) "danger" else "primary")
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
        }, icon = "check", kind = "primary", small = true)
    }
}
