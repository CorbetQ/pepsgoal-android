package fr.pepsgoal.app.ui.sheets

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import fr.pepsgoal.core.parseYmd
import fr.pepsgoal.core.today
import fr.pepsgoal.core.ymd

/** Fenêtres du bas (feuilles modales Material 3), une à la fois comme dans la version web. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(ui: AppState) {
    val s = ui.sheet ?: return
    val p = LocalPal.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // La fenêtre s'arrête sous la barre d'état ; ses cartes n'ont pas de flou (autre fenêtre que l'arrière-plan)
    ModalBottomSheet(onDismissRequest = { ui.back() }, modifier = Modifier.statusBarsPadding(), sheetState = state, containerColor = if (p.transp || p.contrast) p.bg else p.glassStrong.copy(alpha = 1f), contentColor = p.ink,
        scrimColor = p.scrim, dragHandle = null) {
        key(s) { CompositionLocalProvider(LocalHaze provides null) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetBody(ui, s)
            }
        } }
    }
}

@Composable
private fun SheetBody(ui: AppState, s: Sheet) {
    when (s) {
        is Sheet.GoalForm -> GoalFormSheet(ui, s)
        is Sheet.Goal -> GoalSheet(ui, s.id)
        is Sheet.Progress -> ProgressSheet(ui, s.id)
        is Sheet.User -> UserSheet(ui, s.id)
        is Sheet.Notifs -> NotifsSheet(ui)
        is Sheet.Confirm -> ConfirmSheet(ui, s)
        is Sheet.ShareHub -> ShareHubSheet(ui, s)
        is Sheet.ShareFeed -> ShareFeedSheet(ui, s.goalId, s.text)
        is Sheet.ShareText -> ShareTextSheet(ui, s.text)
        is Sheet.Avatar -> AvatarSheet(ui)
        is Sheet.Tuto -> TutoSheet(ui, s.step)
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
        is Sheet.GroupNew -> GroupNewSheet(ui)
        is Sheet.GroupInfo -> GroupInfoSheet(ui, s.gid)
        is Sheet.DeleteAccount -> DeleteAccountSheet(ui)
    }
}

/** En-tête d'une fenêtre : titre et bouton fermer. */
@Composable
fun SheetHead(ui: AppState, title: String, right: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, Modifier.weight(1f), fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        right()
        IconBtn("x", "Fermer", { ui.close() }, plain = true)
    }
}

/** Pied de fenêtre : boutons alignés à droite. */
@Composable
fun SheetFoot(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, content = content)
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
