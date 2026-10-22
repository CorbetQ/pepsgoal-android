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
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.*
import kotlinx.coroutines.delay

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
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { ui.open(Sheet.User(u.id)) }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Av(u, 40.dp)
            Column(Modifier.weight(1f)) { Text(if (u.id == pg.meId()) "Toi" else u.name, fontWeight = FontWeight.ExtraBold); Muted("@${u.username}", small = true) }
            Text(reactionEmoji(post.reactions[u.id]), fontSize = 22.sp)
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
        CardHead("Tes heures de validation")
        if (peak == null) { Muted("Encore quelques validations et tu verras ici à quelle heure tu es le plus régulier.", small = true); return@Glass }
        Muted("Ton créneau fort : le ${WD_LONG[peak.first].lowercase()} vers ${peak.second} h (${pl(peak.third, "validation", "validations")} en 90 jours).", small = true)
        Spacer(Modifier.height(10.dp))
        Row {
            Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { WD.forEach { Box(Modifier.height(14.dp), contentAlignment = Alignment.Center) { Text(it, fontSize = 10.sp, color = p.ink3) } } }
            Canvas(Modifier.weight(1f).height((7 * 16).dp)) {
                val cw = size.width / 24f; val ch = size.height / 7f
                for (d in 0..6) for (h in 0..23) {
                    val n = grid[d][h]; val a = if (n == 0) .07f else .2f + .8f * n / max
                    drawRoundRect((if (n == 0) p.ink else p.flame).copy(alpha = a), Offset(h * cw + 1f, d * ch + 1f), Size(cw - 2f, ch - 2f), CornerRadius(4f, 4f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { listOf("0 h", "6 h", "12 h", "18 h", "23 h").forEach { Text(it, fontSize = 10.sp, color = p.ink3) } }
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
            FlowRowCompat(gap = 6.dp) { TRIAL_LENGTHS.forEach { n -> FChip("$n jours", false, { pg.startTrial(g, n); ui.toast("Défi de $n jours lancé : c’est parti !", "trophy") }) } }
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
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(when { ok -> color; past -> p.danger.copy(alpha = .18f); else -> p.ink.copy(alpha = .06f) })
                .then(if (now) Modifier.background(androidx.compose.ui.graphics.Color.Transparent) else Modifier), contentAlignment = Alignment.Center) {
                Text(if (ok) "✓" else "${i + 1}", fontSize = 10.sp, fontWeight = if (now) FontWeight.ExtraBold else FontWeight.Medium, color = if (ok) p.bg else if (now) p.ink else p.ink3)
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
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (me) p.flame.copy(alpha = .12f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { if (!me) ui.open(Sheet.User(r.user.id)) }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(listOf("🥇", "🥈", "🥉").getOrNull(i) ?: "${i + 1}", fontSize = 16.sp, modifier = Modifier.width(26.dp))
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
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).clickable { mode = null }) {
            if (mode == "compare") Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                listOf(photos.first() to "Avant", photos.last() to "Après").forEach { (pr, l) ->
                    ProofImage(pr, Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)))
                    Text("$l · ${fmtDate(pr.date)}", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.ExtraBold, fontFamily = Unbounded)
                }
            } else {
                var i by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) { while (true) { delay(1400); i = (i + 1) % photos.size } }
                ProofImage(photos[i], Modifier.fillMaxSize())
                Text("${fmtDate(photos[i].date)} · ${i + 1}/${photos.size}", Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp)
                    .clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .5f)).padding(horizontal = 14.dp, vertical = 6.dp),
                    color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProofImage(pr: Proof, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp = remember(pr.id) { Proofs.photoFile(ctx, pr)?.let { f ->
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
    val p = LocalPal.current; val pg = ui.pg
    if (pg.store.online) return
    val n = remember(ui.rev) { if (pg.store.acct) ui.cloud.pendingCount() else 0 }
    Row(Modifier.clip(CircleShape).clickable { ui.toast(if (n > 0) "Hors ligne : ${pl(n, "modification", "modifications")} en attente, envoyées dès le retour du réseau." else "Hors ligne : tout est enregistré sur le téléphone.", "cloudoff") }
        .padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Pi("cloudoff", size = 14.dp, tint = p.ink2)
        Muted(if (n > 0) "Hors ligne · $n en attente" else "Hors ligne", small = true)
    }
}
