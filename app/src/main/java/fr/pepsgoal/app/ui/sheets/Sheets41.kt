package fr.pepsgoal.app.ui.sheets

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.data.Proof
import fr.pepsgoal.app.data.Proofs
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.step
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------ Qui a réagi */

/** Liste des personnes qui ont réagi à une publication, avec leur réaction. */
@Composable
fun LikersSheet(ui: AppState, postId: String) {
    ui.watch()
    val pg = ui.pg; val post = pg.db.posts.firstOrNull { it.id == postId } ?: run { LaunchedEffect(Unit) { ui.close() }; return }
    var filter by remember { mutableStateOf<String?>(null) }
    val sum = reactionSummary(post)
    SheetHead(ui, "Réactions · ${post.likes.size}")
    if (sum.size > 1) FlowRowCompat(gap = 6.dp) {
        FChip("Toutes", filter == null, { filter = null })
        REACTIONS.forEach { (k, e) -> val n = post.likes.count { (post.reactions[it] ?: "fire") == k }; if (n > 0) FChip("$e $n", filter == k, { filter = k }) }
    }
    val list = post.likes.filter { filter == null || (post.reactions[it] ?: "fire") == filter }.mapNotNull { pg.userById(it) }
    if (list.isEmpty()) Muted("Personne pour l’instant.", small = true)
    list.forEach { u ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable { ui.open(Sheet.User(u.id)) }.heightIn(min = 60.dp).padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Av(u, 40.dp)
            Column(Modifier.weight(1f)) { Text(if (u.id == pg.meId()) "Toi" else u.name, fontWeight = FontWeight.ExtraBold); Muted("@${u.username}", small = true) }
            Emoji(reactionEmoji(post.reactions[u.id]), 24.dp)
        }
    }
}

/* ------------------------------------------------------------------ Carte des heures */

/** Quand tu valides vraiment : jours de la semaine × heures, sur 90 jours. */
@Composable
fun HourHeatCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    val grid = remember(ui.rev) { pg.hourHeat() }
    val peak = hourPeak(grid)
    val max = grid.maxOf { r -> r.maxOrNull() ?: 0 }.coerceAtLeast(1)
    Glass {
        CardHead("Tes heures de validation", emoji = "🕐")
        if (peak == null) { Muted("Encore quelques validations et tu verras ici à quelle heure tu es le plus régulier.", small = true); return@Glass }
        Muted("Ton créneau fort : le ${WD_LONG[peak.first].lowercase()} vers ${peak.second} h (${pl(peak.third, "validation", "validations")} en 90 jours).", small = true)
        Spacer(Modifier.height(10.dp))
        Row {
            Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { WD.forEach { Box(Modifier.height(14.dp), contentAlignment = Alignment.Center) { Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = p.ink3) } } }
            Canvas(Modifier.weight(1f).height((7 * 16).dp)) {
                val cw = size.width / 24f; val ch = size.height / 7f
                for (d in 0..6) for (h in 0..23) {
                    val n = grid[d][h]; val lvl = if (n == 0) 0 else (1 + 3 * n / max).coerceIn(1, 4)
                    drawRoundRect(p.heat(lvl), Offset(h * cw + 1f, d * ch + 1f), Size(cw - 2f, ch - 2f), CornerRadius(3.dp.toPx(), 3.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { listOf("0 h", "6 h", "12 h", "18 h", "23 h").forEach { Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = p.ink3) } }
    }
}

/* ------------------------------------------------------------------ Défi de N jours */

/** Option de la fiche : défi de 7 à 100 jours sur cet objectif, avec son calendrier. */
@Composable
fun TrialRow(ui: AppState, g: Goal, divider: Boolean = true) {
    val p = LocalPal.current; val pg = ui.pg
    val st = pg.trialState(g)
    var open by remember { mutableStateOf(false) }
    OptionRow("trophy", p.flame, p.pink, "Défi", st?.let { "Jour ${minOf(it.idx + 1, it.total)}/${it.total} · ${it.done} validés" } ?: "Relève un défi de 7, 21, 30 ou 100 jours", open, { open = !open }, divider) {
        if (st == null) {
            Muted("Un calendrier à cocher et une célébration à la fin si tu tiens au moins 80 % des jours.", small = true)
            FlowRowCompat(gap = 6.dp) { TRIAL_LENGTHS.forEach { n -> PButton("$n jours", { pg.startTrial(g, n); ui.toast("Défi de $n jours lancé : c’est parti !", "trophy") }, small = true, color = p.type("challenge").c) } }
        } else {
            TrialCalendar(st, p.type(g.type).c)
            PButton("Arrêter le défi", { pg.startTrial(g, 0); open = false }, small = true, kind = "ghost")
        }
    }
}

@Composable
fun TrialCalendar(st: TrialState, color: androidx.compose.ui.graphics.Color) {
    val p = LocalPal.current
    FlowRowCompat(gap = 4.dp) {
        st.days.forEachIndexed { i, (d, ok) ->
            val past = i < st.idx; val now = i == st.idx
            val c = vivid(color)
            Box(Modifier.size(30.dp, 33.dp).chunk(when { ok -> c; past -> p.selTint(p.danger); else -> p.surface }, when { now -> p.focusEdge; ok -> c.step(); else -> p.edge }, 6.dp, 3.dp, if (ok) 0.dp else if (now) 3.dp else 2.dp),
                contentAlignment = Alignment.Center) {
                Text(if (ok) "✓" else "${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (ok) fr.pepsgoal.app.ui.theme.PLUM else if (now) p.ink else p.ink3)
            }
        }
    }
}

/* ------------------------------------------------------------------ Classement entre amis */

/** Toi et tes amis qui suivent le même objectif : série et régularité sur 30 jours. */
@Composable
fun FriendsRankCard(ui: AppState, g: Goal) {
    val p = LocalPal.current; val pg = ui.pg
    if (g.userId != pg.meId() || !pg.socialOn()) return
    val ranks = remember(ui.rev, g.id) { pg.friendsRanking(g) }
    if (ranks.size < 2) return
    SectionTitle("Entre amis · même objectif")
    ranks.take(8).forEachIndexed { i, r ->
        val me = r.user.id == pg.meId()
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (me) p.selTint(p.flame) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = !me) { ui.open(Sheet.User(r.user.id)) }.heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { listOf("🥇", "🥈", "🥉").getOrNull(i)?.let { Emoji(it, 22.dp) } ?: Text("${i + 1}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = p.ink2) }
            Av(r.user, 32.dp)
            Column(Modifier.weight(1f)) { Text(if (me) "Toi" else r.user.name, fontWeight = FontWeight.ExtraBold, maxLines = 1); Muted("${r.rate30} % sur 30 jours", small = true) }
            Row(verticalAlignment = Alignment.CenterVertically) { Flame(16.dp); Text(" ${r.streak}", fontWeight = FontWeight.ExtraBold) }
        }
    }
}

/* ------------------------------------------------------------------ Journal photo */

/** Avant / après et diaporama des photos de preuve d'un objectif. */
@Composable
fun ProofStory(proofs: List<Proof>) {
    val ctx = LocalContext.current
    val photos = remember(proofs) { proofs.filter { Proofs.photoFile(ctx, it) != null }.sortedBy { it.date } }
    if (photos.size < 2) return
    var mode by remember { mutableStateOf<String?>(null) }
    FlowRowCompat(gap = 6.dp) {
        PButton("Avant / après", { mode = "compare" }, icon = "image", small = true)
        PButton("Diaporama · ${photos.size}", { mode = "slides" }, icon = "play", small = true)
    }
    if (mode != null) Dialog({ mode = null }, DialogProperties(usePlatformDefaultWidth = false)) {
        val p = LocalPal.current
        Box(Modifier.fillMaxSize().background(p.mediaBg)) {
            if (mode == "compare") Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                listOf(photos.first() to "Avant", photos.last() to "Après").forEach { (pr, l) ->
                    ProofImage(pr, Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)))
                    MiniPlaque("$l · ${fmtDate(pr.date)}")
                }
            } else {
                var i by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) { while (true) { delay(1400); i = (i + 1) % photos.size } }
                ProofImage(photos[i], Modifier.fillMaxSize())
                MiniPlaque("${fmtDate(photos[i].date)} · ${i + 1}/${photos.size}", Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp))
            }
            MediaBtn("x", "Fermer", { mode = null }, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp))
        }
    }
}

@Composable
private fun ProofImage(pr: Proof, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp = rememberDecoded(pr.id) { Proofs.photoFile(ctx, pr)?.let { f ->
        // Réduite à l'affichage : un diaporama de dizaines de photos reste léger en mémoire
        val o = BitmapFactory.Options().apply { inSampleSize = 2 }
        BitmapFactory.decodeFile(f.path, o)?.asImageBitmap()
    } }
    if (bmp != null) Image(bmp, "Photo du ${fmtDate(pr.date)}", modifier, contentScale = ContentScale.Crop)
}

/* ------------------------------------------------------------------ Hors ligne */

/** Pastille « hors ligne » de l'en-tête, avec le nombre de modifications en attente d'envoi. */
@Composable
fun OfflineChip(ui: AppState) {
    val pg = ui.pg
    if (pg.store.online) return
    val n = remember(ui.rev) { if (pg.store.acct) ui.cloud.pendingCount() else 0 }
    // Hors ligne : petite puce à marche (posée sur la scène) qui ouvre les modifications en attente
    Puce(if (n > 0) "Hors ligne · $n en attente" else "Hors ligne", { ui.open(Sheet.Pending) }, emoji = "📴")
}

/** « Synchronisé » : mini-plaque qui apparaît quelques secondes dans le héros quand les modifications faites hors ligne sont parties. */
@Composable
fun SyncedChip(ui: AppState) {
    val p = LocalPal.current
    var show by remember { mutableStateOf(false) }
    val at = ui.syncedAt
    LaunchedEffect(at) { if (at > 0 && System.currentTimeMillis() - at < 5000) { show = true; delay(3600); show = false } }
    if (show && ui.pg.store.online) MiniPlaque(if (ui.syncedN > 1) "Synchronisé · ${ui.syncedN} envoyées" else "Synchronisé", Modifier.popIn(), emoji = "✅", color = p.ok)
}

/** Modifications faites hors ligne, en attente d'envoi, et bouton pour les envoyer tout de suite. */
@Composable
fun PendingSheet(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val cloud = ui.cloud
    val list = remember(ui.rev) { cloud.pendingList() }
    var busy by remember { mutableStateOf(false) }
    SheetHead(ui, "Modifications en attente")
    if (!pg.store.acct) { Note("Sans compte, tout est enregistré sur ce téléphone : rien n’attend d’être envoyé.", "lock"); return }
    // État de la connexion et action possible
    Glass(strong = true, pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(if (pg.store.online) "cloud" else "cloudoff", if (pg.store.online) p.ok else p.ink3, if (pg.store.online) p.type("objective").c else p.ink2, 42.dp, 14.dp)
            Column(Modifier.weight(1f)) {
                Text(when { cloud.forceOffline -> "Mode hors ligne activé"; !pg.store.online -> "Pas de connexion au serveur"; list.isEmpty() -> "Tout est synchronisé"; else -> "En ligne" }, fontWeight = FontWeight.ExtraBold)
                Muted(when {
                    list.isEmpty() -> cloud.lastSync?.let { "Dernier envoi ${ago(it)}." } ?: "Rien n’attend d’être envoyé."
                    !pg.store.online -> "${pl(list.size, "modification partira", "modifications partiront")} dès le retour du réseau."
                    else -> "${pl(list.size, "modification", "modifications")} à envoyer."
                }, small = true)
            }
        }
        if (list.isNotEmpty() || cloud.forceOffline) {
            Spacer(Modifier.height(12.dp))
            val scope = rememberCoroutineScope()
            when {
                cloud.forceOffline -> PButton("Repasser en ligne", { cloud.forceOffline = false; ui.bump(); scope.launch { busy = true; try { cloud.syncNow() } finally { busy = false; ui.bump() } } }, Modifier.fillMaxWidth(), icon = "cloud", kind = "primary")
                !pg.store.online -> PButton("Réessayer la connexion", { cloud.pingSoon(); ui.toast("Nouvelle tentative de connexion…", "cloud") }, Modifier.fillMaxWidth(), icon = "repeat")
                else -> PButton(if (busy) "Envoi…" else "Envoyer maintenant", { if (!busy) scope.launch { busy = true; try { cloud.syncNow() } finally { busy = false; ui.bump() }
                    if (cloud.syncError.isEmpty()) { Feedback.success(); ui.toast("Tout est synchronisé.", "check") } else ui.toast("Envoi impossible : ${cloud.syncError}", "x") } }, Modifier.fillMaxWidth(), icon = "send", kind = "primary", enabled = !busy)
            }
        }
    }
    if (list.isEmpty()) { Empty("Tout est à jour", "Tes objectifs, validations et réglages sont déjà sur le serveur.", icon = "cloud"); return }
    // Liste, regroupée : objectifs, validations, réglages
    for ((kind, title) in listOf("goal" to "Objectifs", "checkin" to "Validations", "settings" to "Réglages")) {
        val items = list.filter { it.kind == kind }; if (items.isEmpty()) continue
        Eyebrow("$title · ${items.size}")
        Glass(pad = 6.dp) {
            items.take(60).forEach { it0 ->
                val (col, ic) = when (it0.action) { "new" -> p.ok to "plus"; "delete" -> p.danger to "trash"; else -> p.flame to "edit" }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).background(vivid(col), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) { Pi(if (kind == "checkin" && it0.action == "new") "check" else if (kind == "settings") "gear" else ic, size = 18.dp, tint = fr.pepsgoal.app.ui.theme.PLUM) }
                    Column(Modifier.weight(1f)) {
                        Text(it0.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Muted(it0.sub, small = true, maxLines = 1)
                    }
                }
            }
            if (items.size > 60) Muted("… et ${items.size - 60} autres", Modifier.padding(8.dp), small = true)
        }
    }
    Note("Les modifications restent enregistrées sur ce téléphone, même si tu fermes l’application. Elles partent automatiquement dès que la connexion revient.", "lock")
}
