package fr.pepsgoal.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.sys.Notifier
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.*
import fr.pepsgoal.core.model.Message
import fr.pepsgoal.core.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Conversation ouverte (pour ne pas notifier ses messages). */
object ChatState { var key: String? = null }

private class Target(val chat: String?, val to: String?)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(ui: AppState, with: String?, group: String?) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val ctx = LocalContext.current
    val u = pg.me() ?: return
    val g = group?.let { gid -> pg.db.chats.firstOrNull { it.id == gid } }
    val x = if (g == null) pg.userById(with) else null
    if (if (g != null) !g.members.contains(u.id) else x == null) { LaunchedEffect(Unit) { ui.back() }; return }
    val key = if (g != null) "g:${g.id}" else "u:${x!!.id}"
    BackHandler { ui.back() }

    // Chargement, déchiffrement, lecture : à l'ouverture puis toutes les 6 secondes
    DisposableEffect(key) { ChatState.key = key; Notifier.clearChat(ctx, key); onDispose { if (ChatState.key == key) ChatState.key = null; Voice.release() } }
    LaunchedEffect(key) {
        while (true) {
            try { if (g != null) ui.cloud.openGroup(g.id) else ui.cloud.openChat(x!!.id) } catch (e: Exception) {}
            delay(6000)
        }
    }
    val all = if (g != null) Social.groupConvo(ui, g.id) else Social.convo(ui, u.id, x!!.id)
    LaunchedEffect(all.size, ui.rev) {
        var ch = false
        all.forEach { m -> if (g != null) { if (Social.isUnread(ui, m)) { m.readBy.add(u.id); ch = true } } else if (m.to == u.id && !m.read) { m.read = true; ch = true } }
        if (ch) pg.store.rawSave()
    }
    val reacts = Social.reactions(ui, all)
    val list = all.filter { Social.body(ui, it)?.str("k") !in Msg.SILENT || Social.pending(ui, it) }

    var e2eOpen by remember { mutableStateOf(false) }
    var safety by remember(key) { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    var replyTo by remember(key) { mutableStateOf<Message?>(null) }
    var photoView by remember { mutableStateOf<ByteArray?>(null) }
    val state = rememberLazyListState()
    LaunchedEffect(list.size) { if (list.isNotEmpty()) state.animateScrollToItem(list.size + 1) }
    LaunchedEffect(e2eOpen) { if (e2eOpen && safety == null) safety = try { ui.cloud.e2e.safetyCode(if (g != null) Conv(group = g.id) else Conv(peer = x!!.id)) } catch (e: Exception) { null } }

    val target: () -> Target? = {
        if (pg.socialBlocked("d'envoyer des messages")) null
        else if (g != null) { if (!g.members.contains(u.id)) { ui.toast("Tu ne fais plus partie de ce groupe.", "comment"); null } else Target(g.id, null) }
        else if (x!!.role != null || !pg.areFriends(u.id, x.id) || pg.isBlockedPair(u.id, x.id)) { ui.toast("Ce compte ne reçoit pas de messages.", "comment"); null }
        else Target(null, x.id)
    }
    val send: (JsonElement, ByteArray?, String?) -> Unit = { body, blob, mime ->
        target()?.let { t -> ui.scope.launch {
            try { val m = ui.cloud.e2e.deliver(t.chat, t.to, body, blob, mime); if (m != null && blob != null) Att.put(m.id, blob) }
            catch (e: ApiError) { ui.toast(e.message ?: "Envoi impossible, réessaie.", "x") } catch (e: Exception) { ui.toast(e.message ?: "Envoi impossible, réessaie.", "x") }
        } }
    }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            // En-tête
            Row(Modifier.fillMaxWidth().background(p.glassStrong).statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBtn("chevronleft", "Retour", { ui.back() }, plain = true)
                Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { if (g != null) ui.open(Sheet.GroupInfo(g.id)) else ui.open(Sheet.User(x!!.id)) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (g != null) GroupAv(g.emoji, 40) else Av(x, 40.dp)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(g?.name ?: x!!.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (x != null && pg.isOfficial(x)) Verified()
                            if (x?.demo == true) Chip("démo")
                        }
                        Muted(if (g != null) "${pl(g.members.size, "membre", "membres")} · infos et membres" else if (pg.isOfficial(x)) "Compte officiel" else "@${x!!.username}", small = true, maxLines = 1)
                    }
                }
                if (g != null || !pg.isOfficial(x)) IconBtn("lock", "Chiffrement de bout en bout : en savoir plus", { e2eOpen = !e2eOpen }, plain = true, tint = if (e2eOpen) p.flame else null)
            }
            AnimatedVisibility(e2eOpen) {
                Note((if (g != null) "Groupe chiffré de bout en bout : une clé propre au groupe, présente uniquement sur les appareils de ses membres." else "Chiffrée de bout en bout : une clé propre à votre paire, présente uniquement sur vos appareils.") +
                    " Personne d’autre, pas même PepsGoal, ne peut lire vos messages.${safety?.let { " Code de sécurité : **$it** (comparez-le de vive voix)." } ?: ""}", "lock", Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            if (pg.store.acct && !ui.cloud.e2e.hasVault() && (g != null || !pg.isOfficial(x))) Glass(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), pad = 12.dp) {
                Text(rich("**Retrouve tes messages sur tous tes appareils**"), fontSize = 14.sp)
                Muted("Confirme ton mot de passe une seule fois : tes clés de conversation seront synchronisées, chiffrées, entre tes appareils.", Modifier.padding(bottom = 8.dp), small = true)
                VaultForm(ui)
            }
            // Messages
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (list.isEmpty()) item("empty") { Muted(if (g != null) "Aucun message pour l’instant. Dis bonjour au groupe !" else "Aucun message pour l'instant. Lance la conversation !", Modifier.fillMaxWidth().padding(top = 30.dp), small = true, center = true) }
                var lastDay = ""; var lastFrom = ""
                list.forEach { m ->
                    val d = ymd(m.at)
                    if (d != lastDay) { val dd = d; item("d:$d") { Muted(if (dd == today()) "Aujourd'hui" else fmtDate(dd), Modifier.fillMaxWidth().padding(vertical = 8.dp), small = true, center = true) }; lastFrom = ""; lastDay = d }
                    val mine = m.from == u.id
                    val who = if (g != null && !mine && m.from != lastFrom) (pg.userById(m.from)?.name ?: "Ancien membre") else null
                    lastFrom = m.from
                    item("m:${m.id}") {
                        MessageRow(ui, m, mine, who, g?.members, reacts[m.id], menu == m.id, more,
                            onMenu = { menu = if (menu == m.id) null else m.id; more = false }, onMore = { more = !more },
                            onReact = { e -> val on = reacts[m.id]?.get(e)?.contains(u.id) != true; menu = null; more = false; send(buildJsonObject { put("k", "react"); put("p", m.id); put("e", e); put("on", on) }, null, null) },
                            onReply = { replyTo = m; menu = null; more = false },
                            onPhoto = { photoView = it }, onVote = { body, oid ->
                                val mineVotes = Social.pollVotes(ui, m, all)[u.id] ?: emptyList()
                                val next = if (body.bool("multi")) (if (oid in mineVotes) mineVotes - oid else mineVotes + oid) else (if (oid in mineVotes) emptyList() else listOf(oid))
                                send(buildJsonObject { put("k", "vote"); put("p", m.id); put("o", JsonArray(next.map(::JsonPrimitive))) }, null, null)
                            }, votes = if (Social.body(ui, m)?.str("k") == "poll") Social.pollVotes(ui, m, all) else null)
                    }
                }
                item("end") { Spacer(Modifier.height(4.dp)) }
            }
            // Bas : saisie ou statut
            val banned = pg.isBanned(u)
            val status = when {
                x != null && pg.isOfficial(x) -> "Compte officiel : il ne reçoit pas de réponses. Pour contester une décision de modération, utilise la demande de débannissement dans le Feed."
                banned -> "Ta messagerie est suspendue ${pg.banText(u)}."
                x != null && pg.blockedBy(u.id, x.id) -> "blocked"
                x != null && pg.blockedBy(x.id, u.id) -> "Cette conversation n’est plus disponible."
                x != null && !pg.areFriends(u.id, x.id) -> "Vous n'êtes plus amis : la conversation est en lecture seule."
                !pg.store.online -> "Hors ligne : la messagerie revient avec la connexion."
                else -> null
            }
            if (status != null) Row(Modifier.fillMaxWidth().background(p.glassStrong).navigationBarsPadding().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (status == "blocked") { Muted("Tu as bloqué ce compte.", Modifier.weight(1f), small = true); PButton("Débloquer", { ui.run("${x!!.name} est débloqué.") { ui.cloud.unblock(x.id) } }, kind = "ghost", small = true) }
                else Muted(status, small = true)
            } else Composer(ui, u, g?.members, replyTo, { replyTo = null }, send)
        }
    }
    photoView?.let { b -> PhotoViewer(b) { photoView = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    ui: AppState, m: Message, mine: Boolean, who: String?, members: List<String>?, reacts: Map<String, Set<String>>?, menuOpen: Boolean, more: Boolean,
    onMenu: () -> Unit, onMore: () -> Unit, onReact: (String) -> Unit, onReply: () -> Unit, onPhoto: (ByteArray) -> Unit, onVote: (JsonObject, String) -> Unit,
    votes: Map<String, List<String>>?
) {
    val p = LocalPal.current; val pg = ui.pg; val me = pg.meId()
    val pending = Social.pending(ui, m); val b = if (pending) null else Social.body(ui, m)
    val kind = b?.str("k") ?: "text"
    val canAct = !m.official && b != null && !pg.isBanned(pg.me())
    val mentioned = b?.strs("mn")?.contains(me) == true
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        who?.let { Text(it, fontSize = 12.sp, color = p.ink2, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 10.dp, top = 6.dp)) }
        val shape = RoundedCornerShape(20.dp, 20.dp, if (mine) 6.dp else 20.dp, if (mine) 20.dp else 6.dp)
        val bg = when { m.official -> p.type("objective").c.copy(alpha = .22f); mine -> p.flame.copy(alpha = if (p.materialYou) .45f else .32f); else -> p.glassStrong }
        Column(Modifier.widthIn(max = 300.dp).clip(shape).background(bg).then(if (mentioned) Modifier.border(2.dp, p.flame2, shape) else Modifier)
            .combinedClickable(enabled = canAct, onClick = {}, onLongClick = onMenu, onLongClickLabel = "Réagir ou répondre").padding(if (kind == "photo") 4.dp else 12.dp, if (kind == "photo") 4.dp else 9.dp)) {
            if (m.official) OFFICIAL_TAGS[m.tag]?.let { (l, ic) -> Chip(l, ic, p.type("objective").c, modifier = Modifier.padding(bottom = 6.dp)) }
            when {
                pending -> Text("🔒 …", color = p.ink2)
                b == null -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pi("lock", size = 16.dp, tint = p.ink2); Text("Message chiffré : sa clé arrive dès qu’un appareil qui la possède est en ligne${if (pg.store.acct && !ui.cloud.e2e.hasVault()) ", ou déverrouille tes clés avec ton mot de passe ci-dessus" else ""}.", fontSize = 13.sp, color = p.ink2) }
                kind == "photo" -> {
                    val data = Att.get(ui, m, b.obj("a"))
                    val a = b.obj("a"); val ratio = (a.int("w").takeIf { it > 0 }?.toFloat() ?: 4f) / (a.int("h").takeIf { it > 0 }?.toFloat() ?: 3f)
                    val bmp = remember(data) { data?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                    Box(Modifier.width(240.dp).aspectRatio(ratio.coerceIn(.5f, 2f)).clip(RoundedCornerShape(16.dp)).background(p.glass).clickable(enabled = data != null) { data?.let(onPhoto) }, contentAlignment = Alignment.Center) {
                        if (bmp != null) androidx.compose.foundation.Image(bmp, "Photo envoyée", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Pi("image", tint = p.ink3)
                    }
                    b.strN("t")?.takeIf { it.isNotEmpty() }?.let { Text(it, Modifier.padding(6.dp)) }
                }
                kind == "voice" -> VoicePlayer(ui, m, b)
                kind == "poll" -> ChatPoll(ui, m, b, votes ?: emptyMap(), onVote)
                kind == "goal" -> {
                    val sg = b.obj("g"); val type = sg.str("type").takeIf { it in TYPES } ?: "routine"; val tc = p.type(type)
                    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(tc.c.copy(alpha = .2f)).clickable {
                        val gg = pg.goalById(sg.str("id"))
                        if (gg != null && gg.userId == me) ui.open(Sheet.Goal(gg.id)) else if (pg.userById(sg.str("owner")) != null) ui.open(Sheet.User(sg.str("owner"))) else ui.toast("Cet objectif n’est plus visible.", "target")
                    }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Emoji(sg.str("icon", "🎯"), 30.dp)
                        Column { Text(sg.str("title", "Objectif"), fontWeight = FontWeight.ExtraBold); Text("${TYPES.getValue(type).label}${sg.str("v").takeIf { it.isNotEmpty() }?.let { " · $it ${sg.str("l")}" } ?: ""}", fontSize = 12.sp, color = tc.ink) }
                    }
                    b.strN("t")?.takeIf { it.isNotEmpty() }?.let { Text(it, Modifier.padding(top = 6.dp)) }
                }
                else -> MsgText(ui, b, members)
            }
            Text(hm(m.at), Modifier.align(Alignment.End).padding(top = 2.dp, end = if (kind == "photo") 6.dp else 0.dp), fontSize = 11.sp, color = p.ink3)
        }
        val items = reacts?.filter { it.value.isNotEmpty() }
        if (!items.isNullOrEmpty()) Row(Modifier.padding(top = 2.dp, start = 6.dp, end = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { (e, set) ->
                val on = set.contains(me)
                Row(Modifier.clip(CircleShape).background(if (on) p.flame.copy(alpha = .3f) else p.glassStrong).border(1.dp, p.line, CircleShape).clickable(enabled = canAct) { onReact(e) }.padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) { Emoji(e, 16.dp); Text("${set.size}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
        if (menuOpen && canAct) Column(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(20.dp)).background(p.glassStrong).border(1.dp, p.line, RoundedCornerShape(20.dp)).padding(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                QUICK_REACTS.forEach { e -> val on = reacts?.get(e)?.contains(me) == true; Box(Modifier.size(38.dp).clip(CircleShape).background(if (on) p.flame.copy(alpha = .3f) else Color.Transparent).clickable(onClickLabel = "Réagir $e") { onReact(e) }, contentAlignment = Alignment.Center) { Emoji(e, 24.dp) } }
                IconBtn(if (more) "minus" else "plus", if (more) "Revenir aux emojis de base" else "Plus d’emojis", onMore, plain = true, size = 36.dp)
            }
            if (more) FlowRowCompat(Modifier.widthIn(max = 300.dp), gap = 2.dp) { MORE_REACTS.forEach { e -> Box(Modifier.size(36.dp).clip(CircleShape).clickable { onReact(e) }, contentAlignment = Alignment.Center) { Emoji(e, 22.dp) } } }
            PButton("Répondre", onReply, icon = "reply", kind = "ghost", small = true)
        }
    }
}

@Composable
private fun MsgText(ui: AppState, b: JsonObject, members: List<String>?) {
    val p = LocalPal.current; val pg = ui.pg; val me = pg.meId()
    b.obj("r")?.let { r ->
        Column(Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(10.dp)).background(p.ink.copy(alpha = .07f)).padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(if (r.str("f") == me) "Toi" else first(pg.userById(r.str("f"))?.name ?: "Message"), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text(r.str("s", "…"), fontSize = 12.sp, color = p.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    val t = b.str("t")
    if (members == null) { Text(t); return }
    val rx = Regex("(^|\\s)@([\\w.]{2,30})")
    Text(buildAnnotatedString {
        var last = 0
        for (mt in rx.findAll(t)) {
            val un = mt.groupValues[2]; val x = pg.db.users.firstOrNull { it.username == un.lowercase() && members.contains(it.id) } ?: continue
            val st = mt.range.first + mt.groupValues[1].length
            append(t.substring(last, st))
            withStyle(SpanStyle(color = if (x.id == me) p.flame2 else p.type("objective").ink, fontWeight = FontWeight.ExtraBold)) { append("@$un") }
            last = mt.range.last + 1
        }
        append(t.substring(last))
    })
}

@Composable
private fun VoicePlayer(ui: AppState, m: Message, b: JsonObject) {
    val p = LocalPal.current; val ctx = LocalContext.current
    val data = Att.get(ui, m, b.obj("a"))
    val dur = b.obj("a")?.get("dur")?.jsonPrimitive?.doubleOrNull ?: 0.0
    val w = (b.obj("a")?.get("wave") as? JsonArray)?.mapNotNull { it.jsonPrimitive.intOrNull }
    val bars = remember(m.id) {
        if (!w.isNullOrEmpty()) List(Voice.BARS) { i -> w[i * w.size / Voice.BARS].coerceIn(1, 9) }
        else { var h = 0L; m.id.forEach { h = (h * 31 + it.code) and 0xffffffffL }; List(Voice.BARS) { i -> h = (h * 1103515245 + 12345) and 0xffffffffL; (2 + (h ushr 16) % 7 - (if (i < 3 || i > Voice.BARS - 4) 1 else 0)).toInt() } }
    }
    val active = Voice.playing == m.id
    LaunchedEffect(active) { while (active) { Voice.update(); delay(100) } }
    var rate by remember { mutableFloatStateOf(ui.prefs.voiceRate) }
    Row(Modifier.width(250.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(p.ink).clickable(enabled = data != null) { Voice.toggle(ctx, m.id, data!!, rate) }, contentAlignment = Alignment.Center) {
            if (data == null) androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = p.bg, strokeWidth = 2.dp)
            else Pi(if (active && !Voice.paused) "pause" else "play", size = 18.dp, tint = p.bg)
        }
        val prog = if (active) Voice.progress else 0f
        Row(Modifier.weight(1f).height(30.dp).pointerInput(m.id) { detectTapGestures { o -> Voice.seek(m.id, o.x / size.width) } }, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            bars.forEachIndexed { i, v -> Box(Modifier.weight(1f).height((v * 3).dp).clip(RoundedCornerShape(2.dp)).background(if ((i + .5f) / bars.size <= prog) p.flame else p.ink.copy(alpha = .35f))) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(fmtDur(if (active && Voice.position > 0) Voice.position.toDouble() else dur), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("${if (rate == 1f) "1" else fmtNum(rate.toDouble())}×", Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                rate = when (rate) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }; ui.prefs.voiceRate = rate; Voice.setRate(rate)
            }.padding(horizontal = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
        }
    }
}

@Composable
private fun ChatPoll(ui: AppState, m: Message, b: JsonObject, votes: Map<String, List<String>>, onVote: (JsonObject, String) -> Unit) {
    val p = LocalPal.current; val pg = ui.pg; val me = pg.meId()
    val mine = votes[me] ?: emptyList(); val opts = b.arr("o").take(6)
    val voters = votes.values.count { it.isNotEmpty() }
    val counts = HashMap<String, Int>(); votes.values.forEach { v -> v.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
    Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pi("poll", size = 16.dp); Text(b.str("q", "Sondage"), fontWeight = FontWeight.ExtraBold) }
        opts.forEach { o ->
            val n = counts[o.str("id")] ?: 0; val pct = if (voters > 0) n.toFloat() / voters else 0f; val on = o.str("id") in mine
            val names = votes.filter { o.str("id") in it.value }.keys.joinToString(", ") { if (it == me) "toi" else first(pg.userById(it)?.name ?: "?") }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.5.dp, if (on) p.flame else p.line, RoundedCornerShape(12.dp)).clickable { onVote(b, o.str("id")) }) {
                Box(Modifier.matchParentSize().fillMaxWidth(pct).background(p.flame.copy(alpha = .22f)))
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (on) Pi("check", size = 14.dp, tint = p.flame)
                    Column(Modifier.weight(1f).padding(start = if (on) 6.dp else 0.dp)) { Text(o.str("t"), fontWeight = FontWeight.Bold); if (names.isNotEmpty()) Text(names, fontSize = 11.sp, color = p.ink2) }
                    Text("$n", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Muted("${pl(voters, "votant", "votants")} · ${if (b.bool("multi")) "plusieurs réponses" else "une réponse"}", small = true)
    }
}

@Composable
private fun Composer(ui: AppState, u: User, members: List<String>?, replyTo: Message?, clearReply: () -> Unit, send: (JsonElement, ByteArray?, String?) -> Unit) {
    val p = LocalPal.current; val pg = ui.pg; val ctx = LocalContext.current
    var tv by remember { mutableStateOf(TextFieldValue("")) }
    var panel by remember { mutableStateOf<String?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ui.scope.launch {
            val r = withContext(Dispatchers.IO) { Media.decode(ctx, uri, 1600)?.let { Triple(Media.jpegUnder(it, 900_000), it.width, it.height) } }
            if (r == null) { ui.toast("Impossible de lire cette image. Essaie un fichier JPG ou PNG.", "image"); return@launch }
            send(buildJsonObject { put("k", "photo"); put("t", ""); putJsonObject("a") { put("w", r.second); put("h", r.third) } }, r.first, "image/jpeg")
        }
    }
    val startRec = { if (!Voice.start(ctx)) ui.toast("L’enregistrement audio n’est pas disponible sur cet appareil.", "x") }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startRec() else ui.toast("Autorise le micro pour envoyer des messages vocaux.", "mic") }
    LaunchedEffect(Voice.recording) { while (Voice.recording) { if (!Voice.tick()) { Voice.stop(false)?.let { (d, dur, w) -> sendVoice(send, d, dur, w) } }; delay(100) } }
    DisposableEffect(Unit) { onDispose { Voice.stop(true) } }

    Column(Modifier.fillMaxWidth().background(p.glassStrong).navigationBarsPadding().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (Voice.recording) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(p.danger))
                Text(fmtDur(Voice.secs.toDouble()), fontWeight = FontWeight.ExtraBold)
                Muted("Enregistrement · ${fmtDur(Voice.MAX_SECS.toDouble())} max", Modifier.weight(1f), small = true)
                PButton("Annuler", { Voice.stop(true) }, kind = "ghost", small = true)
                PButton("Envoyer", { Voice.stop(false)?.let { (d, dur, w) -> sendVoice(send, d, dur, w) } }, icon = "send", kind = "primary", small = true)
            }
            return@Column
        }
        when (panel) {
            "tools" -> FlowRowCompat(gap = 6.dp) {
                PButton("Photo", { panel = null; pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = "image", small = true)
                PButton("Objectif", { panel = "goal" }, icon = "target", small = true)
                PButton("Sondage", { panel = "poll" }, icon = "poll", small = true)
            }
            "emoji" -> EmojiPicker(height = 200) { e -> val s = tv.selection; val t = tv.text.replaceRange(s.min, s.max, e); if (t.length <= 500) tv = TextFieldValue(t, TextRange(s.min + e.length)) }
            "poll" -> ChatPollForm({ panel = null }) { q, opts, multi -> panel = null; send(buildJsonObject { put("k", "poll"); put("q", q); put("multi", multi); putJsonArray("o") { opts.forEachIndexed { i, t -> addJsonObject { put("id", "o${i + 1}"); put("t", t) } } } }, null, null) }
            "goal" -> {
                val goals = pg.db.goals.filter { it.userId == u.id && !it.archived }
                Muted("Choisis l’objectif à partager :", small = true)
                if (goals.isEmpty()) Muted("Tu n’as pas encore d’objectif à partager.", small = true)
                Column(Modifier.heightIn(max = 240.dp).verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    goals.forEach { g ->
                        val s = pg.statBig(g); val tc = p.type(g.type)
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tc.c.copy(alpha = .18f)).clickable {
                            panel = null; send(buildJsonObject { put("k", "goal"); putJsonObject("g") { put("id", g.id); put("owner", g.userId); put("title", g.title); put("icon", g.icon); put("type", g.type); put("v", s.v); put("l", s.l) } }, null, null)
                        }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Emoji(g.icon, 26.dp); Column { Text(g.title, fontWeight = FontWeight.ExtraBold); Text("${typeInfo(g.type).label} · ${s.v} ${s.l}", fontSize = 12.sp, color = tc.ink) }
                        }
                    }
                }
            }
        }
        replyTo?.let { r ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.ink.copy(alpha = .06f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pi("reply", size = 16.dp)
                Column(Modifier.weight(1f)) { Text("Réponse à ${if (r.from == u.id) "toi-même" else first(pg.userById(r.from)?.name)}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold); Text(Social.preview(ui, r), fontSize = 12.sp, color = p.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                IconBtn("x", "Annuler la réponse", clearReply, plain = true, size = 32.dp)
            }
        }
        // Mentions dans un groupe
        if (members != null) {
            val before = tv.text.substring(0, tv.selection.min.coerceAtMost(tv.text.length))
            Regex("(^|\\s)@([\\w.]{0,30})$").find(before)?.let { mt ->
                val q = mt.groupValues[2].lowercase()
                val opts = members.filter { it != u.id }.mapNotNull { pg.userById(it) }.filter { it.username.startsWith(q) || it.name.lowercase().contains(q) }.take(6)
                opts.forEach { x ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        val pre = before.replace(Regex("@([\\w.]{0,30})$"), "@${x.username} "); val t = pre + tv.text.substring(before.length)
                        tv = TextFieldValue(t, TextRange(pre.length))
                    }.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Av(x, 28.dp); Text(x.name, fontWeight = FontWeight.ExtraBold); Muted("@${x.username}", small = true) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconBtn("plus", "Joindre une photo, un objectif ou un sondage", { panel = if (panel == "tools" || panel == "goal" || panel == "poll") null else "tools" }, plain = true, size = 40.dp)
            IconBtn("smile", "Emojis", { panel = if (panel == "emoji") null else "emoji" }, plain = true, size = 40.dp)
            TextField(tv, { if (it.text.length <= 500) tv = it }, Modifier.weight(1f), placeholder = { Text("Écrire un message…") }, maxLines = 5, shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = p.glass, unfocusedContainerColor = p.glass, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
            if (tv.text.isBlank()) IconBtn("mic", "Enregistrer un message vocal", {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRec() else micPerm.launch(Manifest.permission.RECORD_AUDIO)
            }, plain = true, size = 40.dp)
            else IconBtn("send", "Envoyer", {
                val text = tv.text.trim(); if (text.isEmpty()) return@IconBtn
                val o = LinkedHashMap<String, JsonElement>(); o["k"] = JsonPrimitive("text"); o["t"] = JsonPrimitive(text)
                replyTo?.let { r -> o["r"] = buildJsonObject { put("id", r.id); put("f", r.from); put("s", Social.preview(ui, r).take(90)) } }
                if (members != null) {
                    val mn = members.filter { id -> id != u.id && pg.userById(id)?.let { x -> Regex("(^|\\s)@${Regex.escape(x.username)}(?![\\w.])", RegexOption.IGNORE_CASE).containsMatchIn(text) } == true }
                    if (mn.isNotEmpty()) o["mn"] = JsonArray(mn.map(::JsonPrimitive))
                }
                send(if (o.size > 2) JsonObject(o) else JsonPrimitive(text), null, null)
                tv = TextFieldValue(""); clearReply(); panel = null
            }, size = 40.dp)
        }
    }
}

private fun sendVoice(send: (JsonElement, ByteArray?, String?) -> Unit, data: ByteArray, dur: Double, wave: List<Int>) {
    send(buildJsonObject { put("k", "voice"); putJsonObject("a") { put("dur", Math.round(dur * 10) / 10.0); putJsonArray("wave") { wave.forEach { add(it) } } } }, data, "audio/mp4")
}

@Composable
private fun Modifier.verticalScrollCompat() = this.then(Modifier.verticalScroll(rememberScrollState()))

@Composable
private fun ChatPollForm(cancel: () -> Unit, submit: (String, List<String>, Boolean) -> Unit) {
    var q by remember { mutableStateOf("") }; val opts = remember { mutableStateListOf("", "") }; var multi by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(q, { q = it }, "Question", placeholder = "Ex. On court samedi ou dimanche ?", max = 140)
        opts.forEachIndexed { i, o -> Field(o, { opts[i] = it }, "Choix ${i + 1}", max = 80) }
        if (opts.size < 6) PButton("Ajouter un choix", { opts.add("") }, icon = "plus", kind = "ghost", small = true)
        SetRow("Plusieurs réponses possibles") { PSwitch(multi, { multi = it }) }
        Err(err)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton("Annuler", cancel, kind = "ghost", small = true)
            PButton("Envoyer le sondage", {
                val list = opts.map { it.trim() }.filter { it.isNotEmpty() }
                err = when { q.isBlank() -> "Écris la question du sondage."; list.size < 2 -> "Ajoute au moins deux choix au sondage."; list.map { it.lowercase() }.toSet().size != list.size -> "Deux choix du sondage sont identiques."; else -> null }
                if (err == null) submit(q.trim(), list, multi)
            }, icon = "send", kind = "primary", small = true)
        }
    }
}
