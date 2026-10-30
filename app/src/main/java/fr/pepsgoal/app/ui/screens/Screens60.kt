package fr.pepsgoal.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.step
import androidx.compose.ui.draw.drawBehind
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.sheets.SheetHead
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.*
import fr.pepsgoal.core.model.Message
import fr.pepsgoal.core.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/* ------------------------------------------------------------------ Offrir des flammes */

/** Envoie un cadeau de [n] flammes dans la conversation privée avec [to] (les flammes sont rendues si l'envoi échoue). */
fun sendGift(ui: AppState, to: User, n: Int, note: String) {
    val pg = ui.pg
    pg.giftError(n)?.let { ui.toast(it, "x"); return }
    pg.spendGift(n); ui.bump()
    ui.scope.launch {
        val ok = try {
            ui.cloud.e2e.deliver(null, to.id, buildJsonObject { put("k", "gift"); put("n", n); put("t", "🎁 $n flammes"); if (note.isNotBlank()) put("m", note.trim().take(140)) }) != null
        } catch (e: Exception) { false }
        if (ok) { Feedback.success(); ui.toast("🎁 $n flammes offertes à ${first(to.name)} !", "gift") }
        else { pg.refundGift(n); ui.bump(); ui.toast("Envoi impossible : tes flammes te sont rendues.", "x") }
    }
}

@Composable
fun GiftSheet(ui: AppState, id: String, item0: String? = null) {
    val p = LocalPal.current; val pg = ui.pg
    val x = pg.userById(id) ?: run { LaunchedEffect(Unit) { ui.close() }; return }
    var n by remember { mutableIntStateOf(25) }; var note by remember { mutableStateOf("") }
    // Flammes ou article de la boutique
    var mode by remember { mutableStateOf(if (item0 != null) "item" else "flames") }
    var item by remember { mutableStateOf(item0?.let { shopItem(it) }) }
    val w = pg.wallet()
    SheetHead(ui, if (mode == "item") "Offrir un article" else "Offrir des flammes")
    Seg(listOf("flames" to "Des flammes", "item" to "Un article de la boutique"), mode, { mode = it }, icons = listOf("flameo", "gift"))
    if (mode == "item") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Av(x, 44.dp)
            Column(Modifier.weight(1f)) { Text("Pour ${x.name}", fontWeight = FontWeight.ExtraBold); Muted("Ton porte-monnaie : ${fmtNum(w)} flammes", small = true) }
        }
        ShopItemPicker(ui, item?.key) { item = it }
        Field(note, { note = it }, "Petit mot (facultatif)", placeholder = "Il t’ira bien !", max = 140, icon = "comment")
        Muted("S’il possède déjà l’article, ${first(x.name)} reçoit sa valeur en flammes.", small = true)
        val it0 = item; val err = it0?.let { pg.giftItemError(it.key) }
        PButton(when { it0 == null -> "Choisis un article"; err != null -> err; else -> "Offrir « ${it0.name} » (🔥 ${it0.price})" }, { it0?.let { ui.close(); sendItemGift(ui, x, it, note) } },
            Modifier.fillMaxWidth(), icon = "gift", kind = "primary", enabled = it0 != null && err == null && pg.store.online)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Av(x, 52.dp)
        Column(Modifier.weight(1f)) { Text("Pour ${x.name}", fontWeight = FontWeight.ExtraBold); Muted("Ton porte-monnaie : ${fmtNum(w)} flammes", small = true) }
        Emoji("🎁", 40.dp)
    }
    Muted("Les flammes offertes quittent ton porte-monnaie (ton niveau ne baisse pas) et rejoignent celui de ${first(x.name)} quand il ouvre le cadeau.", small = true)
    FlowRowCompat(gap = 8.dp) { GIFT_AMOUNTS.forEach { a -> FChip("🔥 $a", n == a, { n = a }) } }
    Field(note, { note = it }, "Petit mot (facultatif)", placeholder = "Bravo pour ta série !", max = 140, icon = "comment")
    PButton(if (w < n) "Il te manque ${n - w} flammes" else "Offrir $n flammes", { ui.close(); sendGift(ui, x, n, note) }, Modifier.fillMaxWidth(), icon = "gift", kind = "primary", enabled = w >= n && pg.store.online)
}

/** Cadeau dans la conversation : à ouvrir par le destinataire, les flammes rejoignent son porte-monnaie. */
@Composable
fun GiftCard(ui: AppState, m: Message, b: JsonObject, mine: Boolean) {
    val p = LocalPal.current; val pg = ui.pg
    val n = b.int("n").coerceIn(0, GIFT_MAX)
    var opened by remember(m.id, ui.rev) { mutableStateOf(pg.giftOpened(m.id)) }
    val wiggle = if (!mine && !opened && !p.motion) rememberInfiniteTransition(label = "gift").animateFloat(-6f, 6f, infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "giftW").value else 0f
    // Carte cadeau : aplat or à marche, ruban néon, encre prune
    val gold = vivid(p.gold)
    Column(Modifier.widthIn(min = 200.dp, max = 260.dp).chunk(gold, gold.step(), 24.dp, 4.dp, 0.dp).clip(RoundedCornerShape(24.dp))
        .drawBehind { drawRect(vivid(p.type("deadline").c), androidx.compose.ui.geometry.Offset(size.width * .46f, 0f), androidx.compose.ui.geometry.Size(size.width * .08f, size.height)) }
        .padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.graphicsLayer { rotationZ = wiggle }) { Emoji(if (opened || mine) "🔥" else "🎁", 48.dp) }
        Text("$n flammes", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = fr.pepsgoal.app.ui.theme.PLUM)
        b.strN("m")?.takeIf { it.isNotBlank() }?.let { Text("« $it »", fontStyle = FontStyle.Italic, color = fr.pepsgoal.app.ui.theme.PLUM, textAlign = TextAlign.Center) }
        when {
            mine -> Text("Cadeau offert", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fr.pepsgoal.app.ui.theme.PLUM)
            opened -> Text("Ouvert · +$n flammes", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fr.pepsgoal.app.ui.theme.PLUM)
            else -> PButton("Ouvrir le cadeau", {
                if (pg.openGift(m.id, n)) { opened = true; Feedback.success(); ui.sparks(n); ui.toast("+$n flammes dans ton porte-monnaie !", "gift"); ui.bump() }
            }, icon = "gift", small = true)
        }
    }
}

/* ------------------------------------------------------------------ Fiche privée d'un ami */

/** Note et anniversaire d'un ami, visibles par toi seul (synchronisés entre tes appareils). */
@Composable
fun FriendPrivateCard(ui: AppState, x: User) {
    val p = LocalPal.current; val pg = ui.pg; val ctx = LocalContext.current
    var note by remember(x.id) { mutableStateOf(pg.friendNote(x.id)) }
    var bday by remember(x.id, ui.rev) { mutableStateOf(pg.friendBday(x.id)) }
    var editing by remember(x.id) { mutableStateOf(false) }
    var pickDay by remember { mutableStateOf(false) }
    Glass(pad = 14.dp, radius = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔒 Fiche privée", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold)
            IconBtn("edit", "Modifier la fiche privée", { editing = !editing }, plain = true, size = 34.dp)
        }
        if (!editing) {
            Muted(if (pg.friendNote(x.id).isNotBlank()) pg.friendNote(x.id) else "Ajoute une note (goûts, projets, idées de cadeau) et son anniversaire : toi seul les vois.", small = true)
            bday?.let { b -> val (m, d) = b.split("-").map { it.toInt() }; Muted("🎂 Anniversaire : $d ${Recaps.MONTHS_LONG[m - 1]}", small = true) }
        } else {
            // Anniversaire : sélecteur de date Braise Pop (l'année n'est pas gardée)
            if (pickDay) { val t = todayDate()
                PDatePicker(bday?.let { "2000-$it" } ?: "2000-%02d-%02d".format(t.monthValue, t.dayOfMonth), { v -> bday = v.substring(5) }, { pickDay = false }, title = "Anniversaire de ${first(x.name)}") }
            PTextArea(note, { note = it }, "Note", placeholder = "Aime le vélo, prépare un marathon…", max = 300)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PSelect("Anniversaire", bday?.let { b -> val (m, d) = b.split("-").map { it.toInt() }; "$d ${Recaps.MONTHS_LONG[m - 1]}" } ?: "Ajouter son anniversaire", { pickDay = true }, Modifier.weight(1f), emoji = "🎂", open = pickDay)
                if (bday != null) Box(Modifier.padding(bottom = 4.dp)) { RoundBtn("x", "Effacer l’anniversaire", { bday = null }) }
            }
            PButton("Enregistrer", { pg.setFriendNote(x.id, note); pg.setFriendBday(x.id, bday); editing = false; ui.bump(); ui.toast("Fiche privée enregistrée.", "check") }, icon = "check", small = true)
        }
    }
}

/* ------------------------------------------------------------------ Cartes de l'accueil */

/** Anniversaires d'amis du jour : un mot ou quelques flammes. */
@Composable
fun BirthdayCard(ui: AppState) {
    val pg = ui.pg; val p = LocalPal.current
    val list = remember(ui.rev) { pg.birthdaysToday() }
    if (list.isEmpty()) return
    Glass(border = vivid(p.pink)) {
        list.forEach { x ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box { Av(x, 46.dp); Box(Modifier.align(Alignment.TopEnd)) { Emoji("🎂", 20.dp) } }
                Column(Modifier.weight(1f)) { Text("C’est l’anniversaire de ${first(x.name)} !", fontWeight = FontWeight.ExtraBold); Muted("Un petit mot fait toujours plaisir.", small = true) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton("Lui écrire", { ui.chatDraft = "Joyeux anniversaire ${first(x.name)} ! 🎂🎉"; ui.push(Screen.Chat(with = x.id)) }, icon = "comment", small = true, kind = "type", color = p.pink)
                PButton("Offrir des flammes", { ui.open(Sheet.Gift(x.id)) }, icon = "gift", small = true)
            }
        }
    }
}

/** Souvenirs : ce que tu validais il y a un an, six mois, un mois. */
@Composable
fun MemoriesCard(ui: AppState) {
    val pg = ui.pg; val p = LocalPal.current
    val list = remember(ui.rev) { pg.memories() }
    if (list.isEmpty()) return
    Glass {
        CardHead("Souvenirs", emoji = "📸") { Emoji("📸", 22.dp) }
        list.forEach { mm ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(mm.label, fontWeight = FontWeight.ExtraBold)
                    Muted("${cap(weekdayLongDate(parseYmd(mm.date)))} : ${pl(mm.count, "validation", "validations")}", small = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) { mm.goals.take(4).forEach { g -> GoalIcon(g, 30.dp) } }
            }
        }
    }
}

/** Citation du jour. */
@Composable
fun QuoteCard(ui: AppState) {
    val p = LocalPal.current
    val (q, a) = remember(today(), ui.rev) { ui.pg.quoteToday() }
    Glass {
        Text("“", fontFamily = Unbounded, fontSize = 48.sp, color = p.flameInk, modifier = Modifier.height(30.dp))
        Text(q, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Muted("— $a", Modifier.weight(1f), small = true)
            PButton("Mes citations", { ui.open(fr.pepsgoal.app.Sheet.Quotes) }, icon = "edit", small = true, kind = "ghost")
        }
    }
}

/** Défi du jour : une petite action différente chaque jour, +5 flammes. */
@Composable
fun ChallengeCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    var skip by remember(today()) { mutableIntStateOf(ui.prefs.raw.getInt("defiSkip:${today()}", 0)) }
    val (e, txt) = remember(skip, today()) { pg.dailyChallenge(skip) }
    val done = remember(ui.rev) { pg.challengeDone() }
    Glass(border = if (done) vivid(p.type("routine").c).step() else null) {
        CardHead("Défi du jour", emoji = "⚡") { Muted("+$CHALLENGE_BONUS 🔥", small = true) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Emoji(e, 34.dp)
            Text(txt, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        if (done) Muted("Défi relevé ! À demain pour le suivant.", small = true)
        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton("C’est fait !", { if (pg.completeChallenge()) { Feedback.success(); ui.sparks(CHALLENGE_BONUS); ui.toast("Défi relevé : +$CHALLENGE_BONUS flammes !", "check"); ui.bump() } }, icon = "check", small = true, kind = "type", color = p.type("challenge").c)
            if (skip < 2) PButton("Un autre", { skip++; ui.prefs.raw.edit().putInt("defiSkip:${today()}", skip).apply() }, icon = "repeat", small = true, kind = "ghost")
        }
    }
}

/** Calendrier de l'Avent (1er au 24 décembre) : une case par jour. */
@Composable
fun AdventCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    val d = todayDate(); if (!adventOpen(d)) return
    Glass(border = vivid(p.type("duel").c)) {
        CardHead("Calendrier de l’Avent", emoji = "🎄") { Emoji("🎄", 22.dp) }
        FlowRowCompat(gap = 6.dp) {
            for (day in 1..24) {
                val opened = pg.adventOpened(day); val isToday = day == d.dayOfMonth; val past = day < d.dayOfMonth
                // Cases de 44 dp à marche : aujourd'hui = bord or 3 dp, ouvertes = aplat lime et coche prune
                val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val press = rememberPress(inter, isToday && !opened)
                val okc = vivid(p.type("routine").c)
                Box(Modifier.size(44.dp, 48.dp).graphicsLayer { alpha = if (past && !opened) .45f else 1f }
                    .chunk(if (opened) okc else p.surface, if (opened) okc.step() else if (isToday) p.gold else p.edge, 17.dp, 4.dp, if (isToday) 3.dp else if (opened) 0.dp else 2.dp, press).clip(RoundedCornerShape(15.dp))
                    .springClick(inter, enabled = isToday && !opened, label = "Ouvrir la case du $day") { pg.openAdvent()?.let { n -> Feedback.success(); ui.sparks(n); ui.toast("Case du $day décembre : +$n flammes ! 🎁", "gift"); ui.bump() } },
                    contentAlignment = Alignment.Center) {
                    if (opened) Pi("check", size = 18.dp, tint = fr.pepsgoal.app.ui.theme.PLUM) else Text("$day", fontWeight = FontWeight.ExtraBold, color = p.ink)
                }
            }
        }
        Muted(if (pg.adventOpened(d.dayOfMonth)) "Reviens demain pour la case suivante." else "Ouvre la case du jour : des flammes, et une grosse surprise le 24 !", Modifier.padding(top = 8.dp), small = true)
    }
}

/** Record en vue : combien de jours avant de battre ta meilleure série. */
@Composable
fun RecordCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    val r = remember(ui.rev) { pg.recordChase() } ?: return
    Glass(border = p.focusEdge) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Emoji(if (r.left == 0) "🏆" else "🎯", 34.dp)
            Column(Modifier.weight(1f)) {
                Text(if (r.left == 0) "Record personnel : ${pl(r.cur, "jour", "jours")} !" else "Plus que ${pl(r.left, "jour", "jours")} pour battre ton record", fontWeight = FontWeight.ExtraBold)
                Muted(if (r.left == 0) "Chaque validation repousse ton meilleur score. Continue !" else "Série actuelle ${r.cur} · record ${r.best}", small = true)
            }
        }
        Spacer(Modifier.height(8.dp)); Bar(minOf(100.0, r.cur * 100.0 / maxOf(1, r.best + if (r.left == 0) 0 else 1)))
    }
}

/** Météo de la semaine : les meilleurs jours pour tes objectifs d'extérieur. */
@Composable
fun WeekWeatherCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val ctx = LocalContext.current
    val u = pg.me() ?: return
    val f = u.settings.weather?.let { Wx.cached(ctx, it)?.data } ?: return
    val outdoor = remember(ui.rev) { pg.myGoals(u.id).filter { !it.archived && it.userId == u.id && (it.outdoor || it.planB.isNotBlank()) } }
    if (outdoor.isEmpty() || f.daily.size < 3) return
    val good = goodOutdoorDays(f).map { it.day }.toSet()
    Glass {
        CardHead("Météo de la semaine", emoji = "🌤️") { Muted(outdoor.take(3).joinToString(" ") { it.icon }, small = true) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            f.daily.take(7).forEach { dd ->
                val d = parseYmd(dd.day); val ok = dd.day in good
                Column(Modifier.clip(RoundedCornerShape(17.dp)).background(if (ok) vivid(p.type("routine").c) else Color.Transparent).padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val ink = if (ok) fr.pepsgoal.app.ui.theme.PLUM else p.ink2
                    Text(WD[wdIdx(d)], fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = ink)
                    Emoji(Weather.wmo(dd.code).emoji, 22.dp)
                    Text(Weather.temp(dd.max), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (ok) ink else p.ink)
                    Text("${Math.round(dd.pop ?: 0.0)} %", fontSize = 12.sp, color = if (ok) ink else p.ink3)
                }
            }
        }
        val names = f.daily.take(7).filter { it.day in good && it.day >= today() }.map { WD_LONG[wdIdx(parseYmd(it.day))] }
        Muted(if (names.isEmpty()) "Semaine humide : pense à tes plans B." else "Meilleurs jours pour sortir : ${names.take(3).joinToString(", ")}.", Modifier.padding(top = 8.dp), small = true)
    }
}

/* ------------------------------------------------------------------ Respiration guidée */

/** Une minute de cohérence cardiaque : 5 s d'inspiration, 5 s d'expiration, six cycles. */
@Composable
fun BreathSheet(ui: AppState) {
    val p = LocalPal.current
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("Prêt ?") }; var cycle by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(.45f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        for (i in 1..6) {
            cycle = i; phase = "Inspire…"; Feedback.tick(); scale.animateTo(1f, tween(5000, easing = FastOutSlowInEasing))
            phase = "Expire…"; Feedback.tick(); scale.animateTo(.45f, tween(5000, easing = FastOutSlowInEasing))
        }
        phase = "Bravo, une minute de calme 🌿"; running = false; Feedback.success()
    }
    SheetHead(ui, "Respiration guidée")
    Muted("Une minute de cohérence cardiaque : suis le cercle, inspire quand il grandit, expire quand il rétrécit.", small = true)
    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
        val c1 = vivid(p.type("period").c); val c2 = p.selTint(p.type("period").c)
        Canvas(Modifier.size(240.dp)) {
            val r = size.minDimension / 2 * scale.value
            // Cercle de respiration en aplats néon « période » (anneau plein, disque plus clair)
            drawCircle(c2, r * 1.15f)
            drawCircle(c1, r)
            drawCircle(c1.step(), r, style = Stroke(4.dp.toPx()))
        }
        // Phase en plaque, lisible sur le cercle
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Plaque { Text(phase, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center) }
            if (running) MiniPlaque("Cycle $cycle/6")
        }
    }
    PButton(if (running) "Arrêter" else "Commencer", { running = !running; if (!running) phase = "Prêt ?" }, Modifier.fillMaxWidth(), icon = if (running) "pause" else "play", kind = "primary")
}
