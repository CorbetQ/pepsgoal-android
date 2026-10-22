package fr.pepsgoal.app.ui.sheets

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.data.Proof
import fr.pepsgoal.app.data.Proofs
import fr.pepsgoal.app.peps
import fr.pepsgoal.app.sys.Agenda
import fr.pepsgoal.app.sys.Duo
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.screens.TimeButton
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.Goal
import kotlinx.coroutines.launch

/* Blocs de la fiche d'un objectif : sous-tâches, enchaînement, preuves, duo et créneau d'agenda. */

/** Sous-tâches : cochées une à une, la dernière valide l'objectif. */
@Composable
fun StepsBlock(ui: AppState, g: Goal, own: Boolean) {
    val p = LocalPal.current; val pg = ui.pg; val tc = p.type(g.type); val steps = g.steps
    if (steps.isEmpty() && !own) return
    var nt by remember { mutableStateOf("") }; var editing by remember { mutableStateOf(false) }
    val done = pg.stepsDone(g)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SectionTitle(if (steps.isEmpty()) "Sous-tâches" else "Sous-tâches · ${done.size}/${steps.size}") }
        if (own && steps.isNotEmpty()) PButton(if (editing) "Terminé" else "Modifier", { editing = !editing }, small = true, kind = "ghost")
    }
    if (steps.isEmpty()) Muted("Découpe cet objectif en petites étapes (brossage, lecture, écran coupé…) : il est validé quand tout est coché.", small = true)
    else Bar(done.size * 100.0 / steps.size, color = tc.c)
    steps.forEachIndexed { i, st ->
        val on = st.id in done
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) tc.c.copy(alpha = .14f) else Color.Transparent)
            .clickable(enabled = own && !editing) { pg.stepToggle(g.id, st.id); Feedback.tick() }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(on, { pg.stepToggle(g.id, st.id); Feedback.tick() }, enabled = own && !editing)
            Text(st.t, Modifier.weight(1f), textDecoration = if (on) TextDecoration.LineThrough else null, color = if (on) p.ink2 else p.ink)
            if (editing) {
                if (steps.size > 1) { IconBtn("up", "Monter « ${st.t} »", { pg.stepMove(g.id, st.id, i - 1) }, plain = true, size = 32.dp); IconBtn("down", "Descendre « ${st.t} »", { pg.stepMove(g.id, st.id, i + 1) }, plain = true, size = 32.dp) }
                IconBtn("x", "Supprimer « ${st.t} »", { pg.stepDel(g.id, st.id) }, plain = true, size = 32.dp)
            }
        }
    }
    if (own && (editing || steps.isEmpty()) && steps.size < 20) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(nt, { nt = it }, "", Modifier.weight(1f), placeholder = "Ajouter une sous-tâche…", max = 80, icon = "plus")
        PButton("Ajouter", { if (nt.isNotBlank()) { pg.stepAdd(g.id, nt); nt = "" } }, small = true)
    }
}

/** Enchaînement : « après tel objectif → celui-ci ». */
@Composable
fun ChainRow(ui: AppState, g: Goal) {
    val pg = ui.pg; val prev = pg.goalById(g.after)
    var open by remember { mutableStateOf(false) }
    InfoRow("Enchaînement") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (prev != null) Text("Après ${prev.icon} ${prev.title}", fontWeight = FontWeight.Bold)
            else Muted("Associe-le à une habitude existante : « après mon café → méditation ».", small = true)
            if (!open) FlowRowCompat(gap = 6.dp) {
                PButton(if (prev == null) "Enchaîner après…" else "Changer", { open = true }, icon = "repeat", small = true)
                if (prev != null) PButton("Retirer", { g.after = null; pg.store.commit() }, small = true, kind = "ghost")
            } else {
                val cands = pg.chainCandidates(g)
                if (cands.isEmpty()) Muted("Aucun autre objectif disponible.", small = true)
                FlowRowCompat(gap = 6.dp) {
                    cands.forEach { c -> FChip("${c.icon} ${c.title.take(24)}", c.id == g.after, { g.after = c.id; pg.store.commit(); open = false; ui.toast("Quand « ${c.title} » sera validé, PepsGoal te proposera « ${g.title} ».", "repeat") }) }
                }
                PButton("Annuler", { open = false }, small = true, kind = "ghost")
            }
        }
    }
}

/** Galerie des preuves (note, photo) et ajout de celle du jour. */
@Composable
fun ProofsBlock(ui: AppState, g: Goal, own: Boolean) {
    val ctx = LocalContext.current; val p = LocalPal.current; val pg = ui.pg
    val rev = Proofs.rev
    val list = remember(rev, g.id) { Proofs.of(ctx, g.id) }
    val k = stepDateOrToday(pg, g); val doneNow = pg.hasCheck(g.id, k) || pg.db.checkins.any { it.goalId == g.id && it.date == k && it.userId == pg.meId() && pg.isWin(it) }
    if (list.isEmpty() && !(own && doneNow)) return
    val todayProof = list.firstOrNull { it.date == k }
    var editing by remember { mutableStateOf(false) }
    var note by remember(todayProof?.id) { mutableStateOf(todayProof?.note ?: "") }
    var photo by remember { mutableStateOf<android.net.Uri?>(null) }
    var big by remember { mutableStateOf<Proof?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) photo = u }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SectionTitle(if (list.isEmpty()) "Preuve du jour" else "Preuves · ${list.size}") }
        if (own && doneNow && !editing) PButton(if (todayProof == null) "Ajouter" else "Modifier", { editing = true }, icon = if (todayProof == null) "plus" else "edit", small = true, kind = "ghost")
    }
    if (editing) {
        Field(note, { note = it }, "", placeholder = "Une note : ce que tu as fait, comment tu t’es senti…", singleLine = false, minLines = 2, max = 500, icon = "edit")
        FlowRowCompat(gap = 6.dp) {
            PButton(if (photo != null || todayProof?.photo != null) "Changer la photo" else "Ajouter une photo", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = "image", small = true)
            PButton("Enregistrer", { Proofs.put(ctx, g.id, k, note, photo); photo = null; editing = false; ui.toast("Preuve enregistrée sur ton téléphone.", "check") }, icon = "check", small = true, kind = "primary")
            if (todayProof != null) PButton("Supprimer", { Proofs.remove(ctx, g.id, k); note = ""; editing = false }, small = true, kind = "danger")
            PButton("Annuler", { photo = null; editing = false }, small = true, kind = "ghost")
        }
        if (photo != null) Muted("Photo prête à être enregistrée.", small = true)
    } else if (list.isEmpty()) Muted("Garde une trace de ta réussite : une photo ou quelques mots. Les preuves restent sur ton téléphone.", small = true)
    if (list.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.take(60).forEach { pr -> ProofTile(ctx, pr, pr.id == big?.id) { big = if (big?.id == pr.id) null else pr } }
    }
    big?.let { pr ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.glassStrong).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val bmp = remember(pr.id) { Proofs.photoFile(ctx, pr)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
            if (bmp != null) Image(bmp, "Photo du ${fmtDate(pr.date)}", Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
            Text(cap(weekdayLongDate(parseYmd(pr.date))), fontWeight = FontWeight.ExtraBold)
            if (pr.note.isNotBlank()) Text(pr.note)
        }
    }
}

private fun stepDateOrToday(pg: Pg, g: Goal) = if (g.type == "timed") pg.timedDate(g) else today()

@Composable
private fun ProofTile(ctx: android.content.Context, pr: Proof, on: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    val bmp = remember(pr.id, pr.photo) { Proofs.photoFile(ctx, pr)?.let { f ->
        val o = BitmapFactory.Options().apply { inSampleSize = 4 }; BitmapFactory.decodeFile(f.path, o)?.asImageBitmap() } }
    Column(Modifier.width(96.dp).clip(RoundedCornerShape(14.dp)).background(p.glassStrong).border(if (on) 2.dp else 1.dp, if (on) p.focus else p.line, RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(80.dp).background(p.ink.copy(alpha = .05f)), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(pr.note.take(60), Modifier.padding(6.dp), fontSize = 11.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, color = p.ink2)
        }
        Text(fmtDateShort(pr.date), Modifier.padding(6.dp), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Duo : inviter un ami, suivre la flamme commune, quitter le duo. */
@Composable
fun DuoRow(ui: AppState, g: Goal) {
    val pg = ui.pg; val p = LocalPal.current; val ctx = LocalContext.current; val app = ctx.peps
    if (!pg.socialOn() || !duoOk(g)) return
    var open by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
    val peer = pg.userById(g.duoPeer)
    InfoRow("En duo") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (g.duoId != null && peer != null) {
                val st = remember(ui.rev) { Duo.state(app, g) }
                val n = remember(ui.rev) { Duo.streak(app, g) }
                val peerToday = remember(ui.rev) { today() in Duo.peerDays(app, g) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Av(peer, 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(when (st) { Duo.State.PENDING -> "Invitation envoyée à ${first(peer.name)}"; Duo.State.LEFT -> "${first(peer.name)} a quitté le duo"; else -> "Avec ${first(peer.name)}" }, fontWeight = FontWeight.Bold)
                        if (st == Duo.State.JOINED) Muted("${if (peerToday) "✓ ${first(peer.name)} a validé aujourd’hui" else "${first(peer.name)} n’a pas encore validé aujourd’hui"}", small = true)
                    }
                    if (st == Duo.State.JOINED) Text("🔥 $n", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = p.flame)
                }
                if (st == Duo.State.JOINED) Muted("Flamme commune : elle ne grandit que les jours où vous validez tous les deux.", small = true)
                PButton("Quitter le duo", { ui.confirm("Quitter le duo ?", "L’objectif reste à toi, sans la flamme commune. ${first(peer.name)} en est informé.", "Quitter", back = fr.pepsgoal.app.Sheet.Goal(g.id)) { ui.scope.launch { Duo.leave(app, g); ui.bump() } } }, small = true, kind = "ghost")
            } else if (!open) {
                Muted("Invite un ami à le faire avec toi : vous gardez chacun votre objectif, avec une flamme commune.", small = true)
                PButton("Inviter un ami", { open = true }, icon = "users", small = true)
            } else {
                val friends = pg.friendsOf(pg.meId()).mapNotNull { pg.userById(it) }.filter { it.role == null }
                if (friends.isEmpty()) Muted("Ajoute d’abord des amis pour lancer un duo.", small = true)
                FlowRowCompat(gap = 6.dp) {
                    friends.forEach { f -> FChip(first(f.name), false, {
                        if (busy) return@FChip
                        busy = true
                        ui.scope.launch {
                            val ok = try { Duo.invite(app, g, f.id) } catch (e: Exception) { ui.toast(e.message ?: "Invitation impossible.", "x"); false }
                            busy = false
                            if (ok) { open = false; ui.toast("Invitation envoyée à ${first(f.name)} dans vos messages.", "users") }
                        }
                    }, leading = { Av(f, 20.dp) }) }
                }
                PButton("Annuler", { open = false }, small = true, kind = "ghost")
            }
        }
    }
}

/** Créneau récurrent dans l'agenda du téléphone. */
@Composable
fun AgendaRow(ui: AppState, g: Goal) {
    val ctx = LocalContext.current
    if (!Agenda.supported(g)) return
    var slot by remember(g.id) { mutableStateOf(Agenda.slot(ctx, g.id)) }
    var open by remember { mutableStateOf(false) }
    val def = Agenda.defaults(g)
    var time by remember { mutableStateOf(slot?.time ?: def.first) }
    var mins by remember { mutableIntStateOf(slot?.minutes ?: def.second) }
    var cals by remember { mutableStateOf<List<Agenda.Cal>>(emptyList()) }
    var cal by remember { mutableStateOf(slot?.calId) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) { cals = Agenda.calendars(ctx); if (cal == null) cal = cals.firstOrNull()?.id; open = true }
        else ui.toast("Autorise l’agenda pour y bloquer un créneau.", "cal")
    }
    InfoRow("Agenda") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val s = slot
            if (s != null && !open) {
                Text("Créneau à ${s.time} (${s.minutes} min), ${pg(ui).metaLine(g).lowercase()}", fontSize = 14.sp)
                FlowRowCompat(gap = 6.dp) {
                    PButton("Modifier", { if (Agenda.granted(ctx)) { cals = Agenda.calendars(ctx); open = true } else perm.launch(Agenda.PERMS) }, small = true)
                    PButton("Retirer", { Agenda.remove(ctx, g.id); slot = null; ui.toast("Créneau retiré de l’agenda.", "cal") }, small = true, kind = "ghost")
                }
            } else if (!open) PButton("Bloquer un créneau", { if (Agenda.granted(ctx)) { cals = Agenda.calendars(ctx); if (cal == null) cal = cals.firstOrNull()?.id; open = true } else perm.launch(Agenda.PERMS) }, icon = "cal", small = true)
            else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Heure", fontSize = 14.sp); TimeButton(time, { time = it }, "Heure du créneau")
                }
                FlowRowCompat(gap = 6.dp) { listOf(15, 30, 45, 60, 90).forEach { m -> FChip("$m min", mins == m, { mins = m }) } }
                if (cals.size > 1) FlowRowCompat(gap = 6.dp) { cals.forEach { c -> FChip(c.name.take(22), cal == c.id, { cal = c.id }) } }
                if (cals.isEmpty()) Muted("Aucun agenda modifiable sur ce téléphone.", small = true)
                Muted("Un événement récurrent suit le rythme de l’objectif. Il disparaît si tu archives ou supprimes l’objectif.", small = true)
                FlowRowCompat(gap = 6.dp) {
                    PButton("Enregistrer", {
                        val c = cal ?: return@PButton ui.toast("Choisis un agenda.", "cal")
                        Agenda.set(ctx, g, c, time, mins)?.let { ui.toast(it, "x") } ?: run { slot = Agenda.slot(ctx, g.id); open = false; ui.toast("Créneau ajouté à ton agenda.", "cal") }
                    }, icon = "check", small = true, kind = "primary", enabled = cal != null)
                    PButton("Annuler", { open = false }, small = true, kind = "ghost")
                }
            }
        }
    }
}

private fun pg(ui: AppState) = ui.pg

@Composable
internal fun InfoRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(110.dp).padding(top = 4.dp), color = LocalPal.current.ink2, fontSize = 14.sp)
        Box(Modifier.weight(1f)) { content() }
    }
}
