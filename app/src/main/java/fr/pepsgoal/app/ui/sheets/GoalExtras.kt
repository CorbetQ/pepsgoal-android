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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
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
import fr.pepsgoal.core.model.unit
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
fun ChainRow(ui: AppState, g: Goal, divider: Boolean = true) {
    val p = LocalPal.current; val pg = ui.pg; val prev = pg.goalById(g.after)
    var open by remember { mutableStateOf(false) }
    OptionRow("repeat", p.type("routine").c, p.type("timed").c, "Enchaînement",
        prev?.let { "Après ${it.icon} ${it.title}" } ?: "Après une habitude existante : « café → méditation »", open, { open = !open }, divider) {
        val cands = pg.chainCandidates(g)
        if (cands.isEmpty()) Muted("Aucun autre objectif disponible.", small = true)
        else Muted("Quand celui-ci sera validé, PepsGoal te proposera « ${g.title} » :", small = true)
        FlowRowCompat(gap = 6.dp) {
            cands.forEach { c -> FChip("${c.icon} ${c.title.take(24)}", c.id == g.after, { g.after = c.id; pg.store.commit(); open = false; ui.toast("Enchaîné après « ${c.title} ».", "repeat") }) }
        }
        if (prev != null) PButton("Retirer l’enchaînement", { g.after = null; pg.store.commit(); open = false }, small = true, kind = "ghost")
    }
}

/** Galerie des preuves (note, photo) et ajout de celle du jour. */
@Composable
fun ProofsBlock(ui: AppState, g: Goal, own: Boolean) {
    val ctx = LocalContext.current; val p = LocalPal.current; val pg = ui.pg; val tc = p.type(g.type)
    val rev = Proofs.rev
    val list = remember(rev, g.id) { Proofs.of(ctx, g.id) }
    val k = stepDateOrToday(pg, g); val doneNow = pg.db.checkins.any { it.goalId == g.id && it.date == k && it.userId == pg.meId() && pg.isWin(it) }
    if (list.isEmpty() && !(own && doneNow)) return
    val todayProof = list.firstOrNull { it.date == k }
    var editing by remember { mutableStateOf(false) }
    var note by remember(todayProof?.id) { mutableStateOf(todayProof?.note ?: "") }
    var photo by remember { mutableStateOf<android.net.Uri?>(null) }
    var big by remember { mutableStateOf<Proof?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) photo = u }
    SectionTitle(if (list.isEmpty()) "Preuve du jour" else "Preuves · ${list.size}")
    if (list.isEmpty() && !editing) Muted("Garde une trace de ta réussite : une photo ou quelques mots, gardés sur ton téléphone.", small = true)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Tuile d'ajout de la preuve du jour
        if (own && doneNow && todayProof == null && !editing) Column(Modifier.width(104.dp).height(128.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(tc.c.copy(alpha = .22f), p.pink.copy(alpha = .12f))))
            .dashedBorder(tc.c.copy(alpha = .7f), 18.dp).clickable { editing = true },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(50)).background(tc.c), contentAlignment = Alignment.Center) { Pi("plus", size = 20.dp, tint = tc.ink) }
            Text("Ajouter", Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text("photo ou note", fontSize = 11.sp, color = p.ink2)
        }
        list.take(60).forEach { pr -> ProofTile(ctx, pr, pr.id == big?.id) { big = if (big?.id == pr.id) null else pr } }
    }
    big?.let { pr ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.glassStrong).border(1.dp, p.line, RoundedCornerShape(20.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val bmp = remember(pr.id, pr.photo) { Proofs.photoFile(ctx, pr)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
            if (bmp != null) Image(bmp, "Photo du ${fmtDate(pr.date)}", Modifier.fillMaxWidth().heightIn(max = 380.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cap(weekdayLongDate(parseYmd(pr.date))), Modifier.weight(1f), fontWeight = FontWeight.ExtraBold)
                if (own && pr.date == k) PButton("Modifier", { note = pr.note; editing = true; big = null }, icon = "edit", small = true, kind = "ghost")
            }
            if (pr.note.isNotBlank()) Text(pr.note, color = p.ink2)
        }
    }
    if (editing) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.ink.copy(alpha = if (p.dark) .05f else .035f)).border(1.dp, p.line.copy(alpha = .6f), RoundedCornerShape(20.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(note, { note = it }, "", placeholder = "Ce que tu as fait, comment tu t’es senti…", singleLine = false, minLines = 2, max = 500, icon = "edit")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val has = photo != null || todayProof?.photo != null
            IconTile("image", if (has) p.ok else p.ink3, if (has) tc.c else p.ink3.copy(alpha = .6f), 36.dp, 12.dp)
            Text(if (photo != null) "Nouvelle photo prête" else if (todayProof?.photo != null) "Photo enregistrée" else "Aucune photo", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            PButton(if (has) "Changer" else "Choisir", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, small = true)
        }
        FlowRowCompat(gap = 6.dp) {
            PButton("Enregistrer", { Proofs.put(ctx, g.id, k, note, photo); photo = null; editing = false; ui.toast("Preuve enregistrée.", "check") }, icon = "check", small = true, kind = "primary")
            if (todayProof != null) PButton("Supprimer", { Proofs.remove(ctx, g.id, k); note = ""; editing = false }, icon = "trash", small = true, kind = "danger")
            PButton("Annuler", { photo = null; editing = false }, small = true, kind = "ghost")
        }
    }
}

/** Bordure en pointillés (tuile d'ajout). */
private fun Modifier.dashedBorder(c: Color, r: androidx.compose.ui.unit.Dp) = drawBehind {
    val px = r.toPx()
    drawRoundRect(c, cornerRadius = androidx.compose.ui.geometry.CornerRadius(px), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))))
}

private fun stepDateOrToday(pg: Pg, g: Goal) = if (g.type == "timed") pg.timedDate(g) else today()

@Composable
private fun ProofTile(ctx: android.content.Context, pr: Proof, on: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    val bmp = remember(pr.id, pr.photo) { Proofs.photoFile(ctx, pr)?.let { f ->
        val o = BitmapFactory.Options().apply { inSampleSize = 4 }; BitmapFactory.decodeFile(f.path, o)?.asImageBitmap() } }
    Box(Modifier.width(104.dp).height(128.dp).clip(RoundedCornerShape(18.dp)).background(p.glassStrong)
        .border(if (on) 2.dp else 1.dp, if (on) p.focus else p.line, RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        if (bmp != null) Image(bmp, "Preuve du ${fmtDate(pr.date)}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("“${pr.note.take(80)}”", Modifier.padding(10.dp), fontSize = 12.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, color = p.ink2, lineHeight = 16.sp)
        // Date sur un dégradé, lisible sur la photo
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (bmp != null) .55f else 0f)))).padding(8.dp)) {
            Text(fmtDateShort(pr.date), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (bmp != null) Color.White else p.ink)
        }
    }
}

/** Duo : inviter un ami, suivre la flamme commune, quitter le duo. */
@Composable
fun DuoRow(ui: AppState, g: Goal, divider: Boolean = true) {
    val pg = ui.pg; val p = LocalPal.current; val ctx = LocalContext.current; val app = ctx.peps
    if (!pg.socialOn() || !duoOk(g)) return
    var open by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
    val peer = pg.userById(g.duoPeer)
    val st = if (g.duoId != null && peer != null) remember(ui.rev) { Duo.state(app, g) } else null
    val n = if (st == Duo.State.JOINED) remember(ui.rev) { Duo.streak(app, g) } else 0
    val sub = when {
        peer == null || st == null -> "Une flamme commune avec un ami"
        st == Duo.State.PENDING -> "Invitation envoyée à ${first(peer.name)}"
        st == Duo.State.LEFT -> "${first(peer.name)} a quitté le duo"
        else -> "Avec ${first(peer.name)}"
    }
    OptionRow("users", p.type("group").c, p.pink, "En duo", sub, open, { open = !open }, divider,
        right = if (st == Duo.State.JOINED) ({ Text("🔥 $n", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = p.flame) }) else null) {
        if (peer != null && st != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Av(peer, 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(peer.name, fontWeight = FontWeight.Bold)
                    if (st == Duo.State.JOINED) Muted(if (today() in remember(ui.rev) { Duo.peerDays(app, g) }) "✓ A validé aujourd’hui" else "N’a pas encore validé aujourd’hui", small = true)
                }
            }
            Muted("La flamme commune ne grandit que les jours où vous validez tous les deux.", small = true)
            PButton("Quitter le duo", { ui.confirm("Quitter le duo ?", "L’objectif reste à toi, sans la flamme commune. ${first(peer.name)} en est informé.", "Quitter", back = fr.pepsgoal.app.Sheet.Goal(g.id)) { ui.scope.launch { Duo.leave(app, g); ui.bump() } } }, small = true, kind = "ghost")
        } else {
            val friends = pg.friendsOf(pg.meId()).mapNotNull { pg.userById(it) }.filter { it.role == null }
            Muted(if (friends.isEmpty()) "Ajoute d’abord des amis pour lancer un duo." else "Choisis l’ami à inviter : il reçoit l’objectif dans vos messages et garde le sien.", small = true)
            FlowRowCompat(gap = 6.dp) {
                friends.forEach { f -> FChip(first(f.name), false, {
                    if (busy) return@FChip
                    busy = true
                    ui.scope.launch {
                        val ok = try { Duo.invite(app, g, f.id) } catch (e: Exception) { ui.toast(e.message ?: "Invitation impossible.", "x"); false }
                        busy = false
                        if (ok) { open = false; ui.toast("Invitation envoyée à ${first(f.name)}.", "users") }
                    }
                }, leading = { Av(f, 20.dp) }) }
            }
        }
    }
}

/** Créneau récurrent dans l'agenda du téléphone. */
@Composable
fun AgendaRow(ui: AppState, g: Goal, divider: Boolean = true) {
    val ctx = LocalContext.current; val p = LocalPal.current
    if (!Agenda.supported(g)) return
    var slot by remember(g.id) { mutableStateOf(Agenda.slot(ctx, g.id)) }
    var open by remember { mutableStateOf(false) }
    val def = Agenda.defaults(g)
    var time by remember { mutableStateOf(slot?.time ?: def.first) }
    var mins by remember { mutableIntStateOf(slot?.minutes ?: def.second) }
    var cals by remember { mutableStateOf<List<Agenda.Cal>>(emptyList()) }
    var cal by remember { mutableStateOf(slot?.calId) }
    val show = { cals = Agenda.calendars(ctx); if (cal == null) cal = cals.firstOrNull()?.id; open = true }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) show() else ui.toast("Autorise l’agenda pour y bloquer un créneau.", "cal")
    }
    OptionRow("cal", p.type("deadline").c, p.type("project").c, "Agenda",
        slot?.let { "Créneau à ${it.time} · ${it.minutes} min" } ?: "Bloquer un créneau récurrent", open,
        { if (open) open = false else if (Agenda.granted(ctx)) show() else perm.launch(Agenda.PERMS) }, divider) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Heure", Modifier.weight(1f), fontWeight = FontWeight.Bold); TimeButton(time, { time = it }, "Heure du créneau")
        }
        Text("Durée", fontWeight = FontWeight.Bold)
        FlowRowCompat(gap = 6.dp) { listOf(15, 30, 45, 60, 90).forEach { m -> FChip("$m min", mins == m, { mins = m }) } }
        if (cals.size > 1) { Text("Agenda", fontWeight = FontWeight.Bold); FlowRowCompat(gap = 6.dp) { cals.forEach { c -> FChip(c.name.take(22), cal == c.id, { cal = c.id }) } } }
        if (cals.isEmpty()) Muted("Aucun agenda modifiable sur ce téléphone.", small = true)
        Muted("Un événement récurrent suit le rythme de l’objectif ; il disparaît si tu archives ou supprimes l’objectif.", small = true)
        FlowRowCompat(gap = 6.dp) {
            PButton("Enregistrer", {
                val c = cal ?: return@PButton ui.toast("Choisis un agenda.", "cal")
                Agenda.set(ctx, g, c, time, mins)?.let { ui.toast(it, "x") } ?: run { slot = Agenda.slot(ctx, g.id); open = false; ui.toast("Créneau ajouté à ton agenda.", "cal") }
            }, icon = "check", small = true, kind = "primary", enabled = cal != null)
            if (slot != null) PButton("Retirer", { Agenda.remove(ctx, g.id); slot = null; open = false; ui.toast("Créneau retiré de l’agenda.", "cal") }, small = true, kind = "ghost")
        }
    }
}

/** Carte qui regroupe les options d'un objectif, séparées par de fins traits. */
@Composable
fun OptionsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    SectionTitle(title)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.ink.copy(alpha = if (p.dark) .05f else .035f))
        .border(1.dp, p.line.copy(alpha = .6f), RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 4.dp), content = content)
}

/** Ligne d'option : pastille d'icône, titre, état en sous-titre ; l'éditeur se déplie dessous. */
@Composable
fun OptionRow(icon: String, c1: Color, c2: Color, title: String, sub: String?, open: Boolean, onClick: () -> Unit, divider: Boolean = true,
              right: (@Composable () -> Unit)? = null, body: @Composable ColumnScope.() -> Unit = {}) {
    val p = LocalPal.current
    val rot by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, label = "chev")
    Column {
        if (divider) Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(p.line.copy(alpha = .5f)))
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = if (open) "Replier" else "Modifier", onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(icon, c1, c2, 38.dp, 12.dp)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                sub?.let { Text(it, fontSize = 13.sp, color = p.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp) }
            }
            right?.invoke()
            Text("›", Modifier.graphicsLayer { rotationZ = rot }, color = p.ink3, fontSize = 22.sp)
        }
        androidx.compose.animation.AnimatedVisibility(open, enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(start = 52.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = body)
        }
    }
}

/* ---------------------------------------------------------------- Compteur d'économies */

private fun money(v: Double) = String.format(java.util.Locale.FRANCE, if (v >= 100) "%.0f €" else "%.2f €", v)
private fun duration(min: Double): String { val m = Math.round(min); val h = m / 60; val d = h / 24
    return when { d >= 2 -> "$d j ${h % 24} h"; h >= 1 -> "$h h ${(m % 60).toString().padStart(2, '0')}"; else -> "$m min" } }

/** Ce que l'objectif a déjà fait économiser : argent, unités évitées, temps. */
@Composable
fun SavingsCard(ui: AppState, g: Goal) {
    val p = LocalPal.current; val sv = ui.pg.savings(g) ?: return
    SectionTitle("Économies")
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOfNotNull(
            if (g.saveCost > 0) Triple(money(sv.money), "économisés", p.type("routine").c) else null,
            Triple(fmtNum(Math.round(sv.units * 10) / 10.0), "${g.unit.ifBlank { "unités" }} évité${if (sv.units > 1) "s" else ""}", p.type("abstinence").c),
            if (g.saveMin > 0) Triple(duration(sv.minutes), "de temps gagné", p.type("duration").c) else null
        ).forEach { (v, l, c) ->
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(c.copy(alpha = .28f), c.copy(alpha = .12f)))).padding(12.dp)) {
                Text(v, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1)
                Text(l, fontSize = 12.sp, color = p.ink2, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (sv.days >= 7 && g.saveCost > 0) Muted("Soit environ ${money(sv.money / sv.days * 365)} par an à ce rythme.", small = true)
}

/** Réglage du compteur : consommation habituelle avant l'objectif, coût et temps par unité. */
@Composable
fun SavingsRow(ui: AppState, g: Goal, divider: Boolean = true) {
    val p = LocalPal.current; val pg = ui.pg
    if (g.type !in SAVING_TYPES) return
    var open by remember { mutableStateOf(false) }
    var base by remember { mutableStateOf(if (g.saveBase > 0) fmtNum(g.saveBase) else "") }
    var cost by remember { mutableStateOf(if (g.saveCost > 0) fmtNum(g.saveCost) else "") }
    var mins by remember { mutableStateOf(if (g.saveMin > 0) fmtNum(g.saveMin) else "") }
    val num = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
    fun d(s: String) = s.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
    OptionRow("trend", p.type("routine").c, p.type("objective").c, "Compteur d’économies",
        if (g.saveBase > 0) "${fmtNum(g.saveBase)} ${g.unit.ifBlank { "unités" }}/jour avant${if (g.saveCost > 0) " · ${money(g.saveCost)} l’unité" else ""}" else "Argent et temps gagnés en t’en passant", open, { open = !open }, divider) {
        Field(base, { base = it }, "Avant, par jour", placeholder = "Ex. 10 cigarettes, 2 sodas…", keyboard = num, max = 8)
        Field(cost, { cost = it }, "Coût d’une unité (€)", placeholder = "Ex. 0,60", keyboard = num, max = 8)
        Field(mins, { mins = it }, "Temps d’une unité (min, facultatif)", placeholder = "Ex. 7", keyboard = num, max = 6)
        FlowRowCompat(gap = 6.dp) {
            PButton("Enregistrer", {
                if (d(base) <= 0) return@PButton ui.toast("Indique ta consommation habituelle par jour.", "x")
                g.saveBase = d(base); g.saveCost = d(cost); g.saveMin = d(mins); pg.store.commit(); open = false
                ui.toast("Compteur d’économies activé.", "trend")
            }, icon = "check", small = true, kind = "primary")
            if (g.saveBase > 0) PButton("Retirer", { g.saveBase = 0.0; g.saveCost = 0.0; g.saveMin = 0.0; pg.store.commit(); open = false }, small = true, kind = "ghost")
        }
    }
}

/* ---------------------------------------------------------------- Statistiques d'un objectif */

@Composable
fun GoalStatsBlock(ui: AppState, g: Goal) {
    val pg = ui.pg
    if (!pg.hasCalendar(g.type) || g.type in setOf("objective", "monthly")) return
    val st = remember(ui.rev, g.id) { pg.goalStats(g) }
    if (st.total < 3) return
    SectionTitle("Statistiques")
    fun pct(v: Double?) = v?.let { "${Math.round(it * 100)} %" } ?: "—"
    StatGrid(listOf(
        pct(st.rate30) to "réussite sur 30 jours", pct(st.rate90) to "réussite sur 90 jours",
        (st.bestWd?.let { cap(WD_LONG[it]) } ?: "—") to "meilleur jour", (st.hour?.let { "${it} h" } ?: "—") to "heure habituelle",
        "${st.longest} j" to "plus longue série", "${st.total}" to "jours validés"
    ))
}
