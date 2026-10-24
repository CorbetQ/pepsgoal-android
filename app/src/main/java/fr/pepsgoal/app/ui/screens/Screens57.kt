package fr.pepsgoal.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.data.Proof
import fr.pepsgoal.app.data.Proofs
import fr.pepsgoal.app.sys.Speaker
import fr.pepsgoal.app.sys.WeekImage
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.sheets.SheetHead
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.clearStatus
import fr.pepsgoal.core.cloud.setStatus
import kotlinx.coroutines.delay

/* ------------------------------------------------------------------ Séries des amis à fêter */

/** Amis qui viennent d'atteindre un palier de série : un bravo en un geste (message prérempli dans la conversation). */
@Composable
fun FriendCheersCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    if (!pg.socialOn()) return
    var hidden by remember { mutableStateOf(ui.prefs.cheeredMilestones) }
    val list = remember(ui.rev, hidden) { pg.friendMilestones().filter { "${it.u.id}:${it.n}" !in hidden } }
    if (list.isEmpty()) return
    fun mark(m: FriendMilestone) { hidden = hidden + "${m.u.id}:${m.n}"; ui.prefs.cheeredMilestones = hidden.toList().takeLast(200).toSet() }
    Glass(border = p.flame.copy(alpha = .4f)) {
        CardHead("Séries de tes amis", emoji = "👏") { Count(list.size) }
        list.take(4).forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Profil de ${m.u.name}") { ui.open(Sheet.User(m.u.id)) }) { Av(m.u, 42.dp) }
                Column(Modifier.weight(1f)) {
                    Text("${first(m.u.name)} atteint ${pl(m.n, "jour", "jours")} !", fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Muted(if (m.n >= 100) "Un palier rare : fais-lui signe 🔥" else "Un petit mot fait toujours plaisir.", small = true, maxLines = 1)
                }
                PButton("Bravo", { mark(m); Feedback.tick(); ui.chatDraft = milestoneCheer(m.n); ui.push(Screen.Chat(with = m.u.id)) }, icon = "heart", small = true, kind = "primary")
                IconBtn("x", "Masquer", { mark(m) }, plain = true, size = 32.dp)
            }
        }
    }
}

/* ------------------------------------------------------------------ Statut du jour */

/** Ton statut du jour (Amis, Profil) : l'emoji s'affiche sur ton avatar chez tes amis pendant 24 h. */
@Composable
fun StatusRow(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    if (!pg.socialOn() || !pg.store.acct) return
    val st = remember(ui.rev) { pg.statusOf(pg.meId()) }
    Row(Modifier.fillMaxWidth().clip(smoothShape(18.dp)).background(p.glassStrong).clickable(onClickLabel = "Statut du jour") { ui.open(Sheet.Status) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Emoji(st?.e ?: "💬", 26.dp)
        Column(Modifier.weight(1f)) {
            Text(if (st != null) st.l.ifBlank { "Ton statut du jour" } else "Ajoute ton statut du jour", fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Muted(if (st != null) "Visible par tes amis encore ${maxOf(1, ((STORY_TTL - (nowMs() - st.at)) / 3_600_000).toInt())} h" else "Ton humeur en un emoji, visible 24 h par tes amis", small = true, maxLines = 1)
        }
        Pi("edit", size = 18.dp, tint = p.ink2)
    }
}

@Composable
fun StatusSheet(ui: AppState) {
    val pg = ui.pg; val cur = remember { pg.statusOf(pg.meId()) }
    var e by remember { mutableStateOf(cur?.e ?: "😊") }
    var l by remember { mutableStateOf(cur?.l ?: "En forme") }
    SheetHead(ui, "Statut du jour")
    Muted("Un emoji et quelques mots, affichés sur ton avatar chez tes amis pendant 24 h. Il ne s’affiche pas dans les stories.", small = true)
    FlowRowCompat(gap = 8.dp) { STATUS_PRESETS.forEach { (pe, pl0) -> FChip("$pe ${tr(pl0)}", e == pe && l == tr(pl0), { e = pe; l = tr(pl0) }) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Emoji(e, 34.dp)
        Field(l, { l = it }, "Quelques mots", Modifier.weight(1f), placeholder = "En forme", max = 40)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PButton("Publier", { ui.close(); ui.run("Statut publié : tes amis le voient pendant 24 h.", "check") { ui.cloud.setStatus(e, l) } }, Modifier.weight(1f), icon = "check", kind = "primary")
        if (cur != null) PButton("Retirer", { ui.close(); ui.run("Statut retiré.", "x") { ui.cloud.clearStatus() } }, kind = "ghost")
    }
}

/* ------------------------------------------------------------------ Briefing du matin */

/** Briefing du matin (avant midi) : le texte du jour et un bouton pour l'écouter, lecture automatique en option. */
@Composable
fun BriefingCard(ui: AppState) {
    val p = LocalPal.current; val ctx = LocalContext.current; val t = today()
    var closed by remember { mutableStateOf(ui.prefs.raw.getString("briefClosed", "") == t) }
    if (closed || nowHM() >= "12:00") return
    val text = remember(ui.rev, I18n.rev) { Speaker.briefingText(ctx) }
    if (text.isBlank()) return
    var playing by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(ui.prefs.briefAuto) }
    LaunchedEffect(playing) { if (playing) { delay(400L + text.length * 70L); playing = false } }
    Glass {
        CardHead("Ton briefing du matin", emoji = "🎧") { IconBtn("x", "Masquer le briefing", { closed = true; Speaker.stop(); ui.prefs.raw.edit().putString("briefClosed", t).apply() }, plain = true, size = 34.dp) }
        Text(text, color = p.ink2, maxLines = 5)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PButton(if (playing) "Arrêter" else "Écouter", { if (playing) { Speaker.stop(); playing = false } else { Speaker.say(ctx, text); playing = true } }, icon = if (playing) "pause" else "music", small = true, kind = "primary")
            Spacer(Modifier.weight(1f))
            Muted("Chaque matin", small = true)
            PSwitch(auto, { auto = it; ui.prefs.briefAuto = it })
        }
    }
}

/* ------------------------------------------------------------------ Récap du dimanche */

/** Le dimanche (dès 17 h) et le lundi : ta semaine résumée en une image à partager. */
@Composable
fun WeekImageCard(ui: AppState) {
    val p = LocalPal.current; val ctx = LocalContext.current
    val wd = wdIdx(todayDate())
    if (!((wd == 6 && nowHM() >= "17:00") || wd == 0)) return
    val st = remember(ui.rev) { WeekImage.stats(ui.pg) }
    if (st.n == 0) return
    var preview by remember { mutableStateOf(false) }
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile("image", p.type("objective").c, p.pink, 46.dp, 15.dp)
            Column(Modifier.weight(1f)) {
                Text(if (st.r.past) "Ta semaine passée en image" else "Ta semaine en image", fontWeight = FontWeight.ExtraBold)
                Muted("${pl(st.n, "validation", "validations")} · ${st.reg} % de régularité · prête pour ta story", small = true)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton("Aperçu", { preview = true }, icon = "eye", small = true)
            PButton("Partager l’image", { WeekImage.share(ctx, ui.pg) }, icon = "share", small = true, kind = "primary")
        }
    }
    if (preview) androidx.compose.ui.window.Dialog({ preview = false }) {
        val bmp = remember(st.r.s, st.n) { WeekImage.render(ctx, st).asImageBitmap() }
        Column(Modifier.clip(smoothShape(24.dp)).background(p.glassStrong.copy(alpha = 1f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(bmp, "Ta semaine en image", Modifier.fillMaxWidth().aspectRatio(1080f / 1920f).clip(smoothShape(16.dp)), contentScale = ContentScale.Fit)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton("Fermer", { preview = false }, Modifier.weight(1f))
                PButton("Partager", { WeekImage.share(ctx, ui.pg) }, Modifier.weight(1f), icon = "share", kind = "primary")
            }
        }
    }
}

/* ------------------------------------------------------------------ Ton meilleur moment */

/** Statistiques : jour de la semaine où tu réussis le mieux, créneau où tu valides le plus, heure habituelle de chaque objectif. */
@Composable
fun MomentsCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    val m = remember(ui.rev) { pg.moments() }
    if (m.total < 5) return
    Glass {
        CardHead("Ton meilleur moment", emoji = "⏰") { Muted("90 jours", small = true) }
        m.bestWd?.let { b -> Text("Tu réussis le mieux le ${WD_LONG[b]} (${Math.round((m.wd[b] ?: 0.0) * 100)} %).", fontWeight = FontWeight.ExtraBold) }
        m.hardWd?.let { h -> Muted("Le ${WD_LONG[h]} est plus difficile (${Math.round((m.wd[h] ?: 0.0) * 100)} %) : allège-le ou prévois un rappel.", small = true) }
        // Réussite par jour de la semaine
        Row(Modifier.fillMaxWidth().height(96.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            m.wd.forEachIndexed { i, v ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    val f = (v ?: 0.0).toFloat().coerceIn(.04f, 1f)
                    Box(Modifier.fillMaxWidth().fillMaxHeight(f * .78f).clip(RoundedCornerShape(8.dp)).background(if (i == m.bestWd) p.flame else p.ink.copy(alpha = if (v == null) .08f else .22f)))
                    Text(WD[i], fontSize = 11.sp, fontWeight = FontWeight.Bold, color = p.ink3, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        // Validations par heure
        m.slot?.let { (_, name, share) -> Text("Tes validations tombent surtout $name (${Math.round(share * 100)} %).", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
        val mx = maxOf(1, m.hours.maxOrNull() ?: 1); val flame = p.flame; val track = p.ink.copy(alpha = .1f)
        Canvas(Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp)) {
            val bw = size.width / 24f
            for (h in 0 until 24) {
                val f = m.hours[h].toFloat() / mx; val bh = maxOf(2.dp.toPx(), size.height * f)
                drawRoundRect(if (m.hours[h] == 0) track else flame.copy(alpha = .35f + .65f * f), Offset(h * bw + bw * .15f, size.height - bh), Size(bw * .7f, bh), CornerRadius(3.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth()) { listOf("0 h", "6 h", "12 h", "18 h", "24 h").forEachIndexed { i, l -> Text(l, Modifier.weight(1f), fontSize = 10.sp, color = p.ink3) } }
        if (m.goals.isNotEmpty()) {
            Spacer(Modifier.height(8.dp)); Eyebrow("Tes heures habituelles")
            m.goals.forEach { (g, h) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${g.icon} ${g.title}", Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Bold)
                    Text("vers ${hourLabel(h)}", color = p.ink2, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ Galerie : avant / après et accéléré */

/** Avant / après : la première et la dernière photo, séparées par un curseur à faire glisser. */
@Composable
fun BeforeAfterDialog(first: Proof, last: Proof, onClose: () -> Unit) {
    val ctx = LocalContext.current; val p = LocalPal.current
    val a = rememberDecoded(first.id, first.photo) { Proofs.photoFile(ctx, first)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    val b = rememberDecoded(last.id, last.photo) { Proofs.photoFile(ctx, last)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    var cut by remember { mutableFloatStateOf(.5f) }
    androidx.compose.ui.window.Dialog(onClose) {
        Column(Modifier.clip(smoothShape(24.dp)).background(p.glassStrong.copy(alpha = 1f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(.8f).clip(smoothShape(16.dp)).background(Color.Black)
                .pointerInput(Unit) { detectDragGestures { ch, _ -> cut = (ch.position.x / size.width).coerceIn(0f, 1f) } }) {
                if (b != null) Image(b, "Après", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (a != null) Image(a, "Avant", Modifier.fillMaxSize().drawWithContent { clipRect(right = size.width * cut) { this@drawWithContent.drawContent() } }, contentScale = ContentScale.Crop)
                Box(Modifier.offset(x = maxWidth * cut - 1.5.dp).width(3.dp).fillMaxHeight().background(Color.White))
                Text(fmtDateShort(first.date), Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .5f)).padding(horizontal = 8.dp, vertical = 3.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(fmtDateShort(last.date), Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .5f)).padding(horizontal = 8.dp, vertical = 3.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            Muted("${pl(daysBetween(parseYmd(first.date), parseYmd(last.date)).toInt(), "jour", "jours")} d’écart · fais glisser pour comparer", small = true, center = true)
            PButton("Fermer", onClose, Modifier.fillMaxWidth())
        }
    }
}

/** Lecture accélérée : toutes les photos de l'objectif l'une après l'autre, avec la date. */
@Composable
fun TimelapseDialog(list: List<Proof>, onClose: () -> Unit) {
    val ctx = LocalContext.current; val p = LocalPal.current
    var i by remember { mutableIntStateOf(0) }; var playing by remember { mutableStateOf(true) }; var fast by remember { mutableStateOf(false) }
    LaunchedEffect(playing, fast, i) { if (playing) { delay(if (fast) 280L else 700L); i = if (i + 1 >= list.size) 0 else i + 1 } }
    androidx.compose.ui.window.Dialog(onClose) {
        Column(Modifier.clip(smoothShape(24.dp)).background(p.glassStrong.copy(alpha = 1f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(.8f).clip(smoothShape(16.dp)).background(Color.Black)) {
                Crossfade(list[i], animationSpec = tween(if (fast) 120 else 260), label = "lapse") { pr ->
                    val bmp = rememberDecoded(pr.id, pr.photo) { Proofs.photoFile(ctx, pr)?.let { f -> val o = BitmapFactory.Options().apply { inSampleSize = 2 }; BitmapFactory.decodeFile(f.path, o)?.asImageBitmap() } }
                    if (bmp != null) Image(bmp, "Photo du ${fmtDate(pr.date)}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Text(cap(fmtDate(list[i].date)), Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .55f)).padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text("${i + 1}/${list.size}", Modifier.align(Alignment.TopEnd).padding(10.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            Bar((i + 1) * 100.0 / list.size)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(if (playing) "Pause" else "Lecture", { playing = !playing }, Modifier.weight(1f), icon = if (playing) "pause" else "play")
                PButton(if (fast) "×1" else "×3", { fast = !fast }, small = true)
                PButton("Fermer", onClose, small = true, kind = "ghost")
            }
        }
    }
}
