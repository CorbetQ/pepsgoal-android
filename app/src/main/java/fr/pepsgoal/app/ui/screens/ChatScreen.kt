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
import fr.pepsgoal.app.ui.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import fr.pepsgoal.app.ui.theme.hex
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
import fr.pepsgoal.app.peps
import fr.pepsgoal.app.sys.Notifier
import fr.pepsgoal.app.sys.Scheduled
import fr.pepsgoal.app.sys.Transcriber
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.*
import fr.pepsgoal.core.model.Message
import fr.pepsgoal.core.model.User
import fr.pepsgoal.core.model.unit
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.layout.onSizeChanged
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
    val edits = Social.edits(ui, all); val gone = Social.deleted(ui, all)
    var hidden by remember { mutableStateOf(ui.prefs.hiddenMsgs) }
    val list = all.filter { (Social.body(ui, it)?.str("k").let { k -> k !in Msg.SILENT || k == "shot" } || Social.pending(ui, it)) && it.id !in hidden && !Social.expired(ui, it) }
    val pinId = Social.pinned(ui, all)?.takeIf { id -> id !in gone && list.any { it.id == id } }
    var ttl by remember(key) { mutableStateOf(ui.prefs.chatTtl[key] ?: 0) }

    var e2eOpen by remember { mutableStateOf(false) }
    var safety by remember(key) { mutableStateOf<String?>(null) }
    // Sélection (appui long), modification, suppression, couleur de la conversation
    var sel by remember(key) { mutableStateOf<Set<String>>(emptySet()) }
    var editing by remember(key) { mutableStateOf<Message?>(null) }
    var askDel by remember(key) { mutableStateOf(false) }
    var palette by remember { mutableStateOf(false) }
    var colorIdx by remember(key) { mutableStateOf(ui.prefs.chatColors[key]) }
    val menu = if (sel.size == 1) sel.first() else null
    BackHandler { when { askDel -> askDel = false; sel.isNotEmpty() -> sel = emptySet(); editing != null -> editing = null; palette -> palette = false; else -> ui.back() } }
    var more by remember { mutableStateOf(false) }
    // Recherche dans la conversation : résultats du plus récent au plus ancien
    var searching by remember(key) { mutableStateOf(false) }; var sq by remember(key) { mutableStateOf("") }; var hit by remember(key) { mutableIntStateOf(0) }
    val hits = remember(sq, list.size, Transcriber.texts.size) { val n = VoiceCmd.norm(sq); if (n.length < 2) emptyList() else list.filter { VoiceCmd.norm(searchText(ui, it)).contains(n) }.map { it.id }.reversed() }
    var replyTo by remember(key) { mutableStateOf<Message?>(null) }
    var photoView by remember { mutableStateOf<ByteArray?>(null) }
    val state = rememberLazyListState()
    fun itemIndex(id: String): Int { var i = 0; var day = ""; for (m in list) { val d = ymd(m.at); if (d != day) { i++; day = d }; if (m.id == id) break; i++ }; return i }
    LaunchedEffect(hit, hits) { hits.getOrNull(hit)?.let { state.animateScrollToItem((itemIndex(it) - 1).coerceAtLeast(0)) } }
    LaunchedEffect(list.size) { if (list.isNotEmpty()) state.animateScrollToItem(list.size + 1) }
    LaunchedEffect(e2eOpen) { if (e2eOpen && safety == null) safety = try { ui.cloud.e2e.safetyCode(if (g != null) Conv(group = g.id) else Conv(peer = x!!.id)) } catch (e: Exception) { null } }

    val target: () -> Target? = {
        if (pg.socialBlocked("d'envoyer des messages")) null
        else if (g != null) { if (!g.members.contains(u.id)) { ui.toast("Tu ne fais plus partie de ce groupe.", "comment"); null } else Target(g.id, null) }
        else if (x!!.role != null || !pg.areFriends(u.id, x.id) || pg.isBlockedPair(u.id, x.id)) { ui.toast("Ce compte ne reçoit pas de messages.", "comment"); null }
        else Target(null, x.id)
    }
    val send: (JsonElement, ByteArray?, String?) -> Unit = { body0, blob, mime ->
        // Messages éphémères : date de fin de vie ajoutée au contenu chiffré (jamais aux messages de service)
        val body = if (ttl > 0 && (body0 as? JsonObject)?.str("k") !in Msg.SILENT) {
            val o = if (body0 is JsonObject) LinkedHashMap(body0) else linkedMapOf<String, JsonElement>("k" to JsonPrimitive("text"), "t" to body0)
            o["x"] = JsonPrimitive(nowMs() + ttl * 3_600_000L); JsonObject(o)
        } else body0
        target()?.let { t -> ui.scope.launch {
            try { val m = ui.cloud.e2e.deliver(t.chat, t.to, body, blob, mime); if (m != null && blob != null) Att.put(m.id, blob) }
            catch (e: ApiError) { ui.toast(e.message ?: "Envoi impossible, réessaie.", "x") } catch (e: Exception) { ui.toast(e.message ?: "Envoi impossible, réessaie.", "x") }
        } }
    }

    // Fonds de la conversation : chacun partage (sans bruit) les fonds qu'il a débloqués, le fond choisi est commun à tous
    val bgKey = Social.chatBg(ui, all)?.takeIf { k -> SHOP_BGS.any { it.key == k } }
    val shared = Social.sharedBgs(ui, all)
    val members = g?.members ?: listOf(u.id, x!!.id)
    val canWrite = if (g != null) g.members.contains(u.id) else x!!.role == null && pg.areFriends(u.id, x.id) && !pg.isBlockedPair(u.id, x.id)
    var bgsSent by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key, all.size) {
        if (bgsSent || !canWrite || pg.isBanned(u)) return@LaunchedEffect
        delay(2500)
        val mine = pg.ownedBgs().toSet()
        if (shared[u.id] != mine) send(buildJsonObject { put("k", "bgs"); put("l", JsonArray(mine.sorted().map(::JsonPrimitive))) }, null, null)
        bgsSent = true
    }
    // Fond aux couleurs choisies pour la conversation, sinon de l'interlocuteur (couleur de son avatar) ou de PepsGoal pour un groupe
    val chosen = colorIdx?.let { fr.pepsgoal.core.ACCENTS.getOrNull(it) }?.c?.map(::hex)
    val cc = chosen?.get(1) ?: x?.let { hex((fr.pepsgoal.core.ACCENTS.getOrNull(it.avatar.color) ?: fr.pepsgoal.core.ACCENTS[0]).c[1]) } ?: p.flame
    // Les messages défilent sous un en-tête et une zone de saisie flottants, sur un voile givré progressif
    val chatHaze = dev.chrisbanes.haze.rememberHazeState()
    val dn = androidx.compose.ui.platform.LocalDensity.current
    var headH by remember { mutableIntStateOf(0) }; var footH by remember { mutableIntStateOf(0) }
    remember(key) { Stagger.reset() }
    Box(Modifier.fillMaxSize()) { CompositionLocalProvider(LocalHaze provides null) {
        Box(Modifier.fillMaxSize().then(if (!p.transp && fr.pepsgoal.app.sys.Perf.flags.blur) Modifier.hazeSource(chatHaze, zIndex = 0f) else Modifier)) {
            if (bgKey != null) { SceneBg(bgKey, Modifier.fillMaxSize()); Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = .18f))) }
            else PageBackdrop(cc, chosen?.get(2) ?: p.pink)
        }
        Box(Modifier.fillMaxSize().imePadding()) {
            // Messages
            LazyColumn(Modifier.fillMaxSize().then(if (!p.transp && fr.pepsgoal.app.sys.Perf.flags.blur) Modifier.hazeSource(chatHaze, zIndex = 1f) else Modifier), state,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = with(dn) { headH.toDp() } + 14.dp, bottom = with(dn) { footH.toDp() } + 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (list.isEmpty()) item("empty") { Muted(if (g != null) "Aucun message pour l’instant. Dis bonjour au groupe !" else "Aucun message pour l'instant. Lance la conversation !", Modifier.fillMaxWidth().padding(top = 30.dp), small = true, center = true) }
                var lastDay = ""; var lastFrom = ""
                list.forEach { m ->
                    val d = ymd(m.at)
                    if (d != lastDay) { val dd = d; item("d:$d") { Muted(if (dd == today()) "Aujourd'hui" else fmtDate(dd), Modifier.fillMaxWidth().padding(vertical = 8.dp), small = true, center = true) }; lastFrom = ""; lastDay = d }
                    if (Social.body(ui, m)?.str("k") == "shot") { item("m:${m.id}") { ShotNotice(ui, m) }; lastFrom = ""; return@forEach }
                    val mine = m.from == u.id
                    val who = if (g != null && !mine && m.from != lastFrom) (pg.userById(m.from)?.name ?: "Ancien membre") else null
                    lastFrom = m.from
                    item("m:${m.id}") {
                        MessageRow(ui, m, mine, who, g?.members, reacts[m.id], menu == m.id, more,
                            selected = m.id in sel || (searching && hits.getOrNull(hit) == m.id), selecting = sel.isNotEmpty(), editedText = edits[m.id], gone = m.id in gone, accent = chosen,
                            blur = if (!p.transp && !p.contrast && fr.pepsgoal.app.sys.Perf.flags.blur) chatHaze else null,
                            onMenu = { sel = if (m.id in sel) sel - m.id else sel + m.id; more = false; Feedback.tick() }, onMore = { more = !more },
                            onTap = { if (sel.isNotEmpty()) sel = if (m.id in sel) sel - m.id else sel + m.id },
                            onReact = { e -> val on = reacts[m.id]?.get(e)?.contains(u.id) != true; sel = emptySet(); more = false; send(buildJsonObject { put("k", "react"); put("p", m.id); put("e", e); put("on", on) }, null, null) },
                            onReply = { replyTo = m; sel = emptySet(); more = false },
                            onPhoto = { photoView = it }, onVote = { body, oid ->
                                val mineVotes = Social.pollVotes(ui, m, all)[u.id] ?: emptyList()
                                val next = if (body.bool("multi")) (if (oid in mineVotes) mineVotes - oid else mineVotes + oid) else (if (oid in mineVotes) emptyList() else listOf(oid))
                                send(buildJsonObject { put("k", "vote"); put("p", m.id); put("o", JsonArray(next.map(::JsonPrimitive))) }, null, null)
                            }, votes = if (Social.body(ui, m)?.str("k") == "poll") Social.pollVotes(ui, m, all) else null)
                    }
                }
                item("end") { Spacer(Modifier.height(4.dp)) }
            }
            EdgeFrost(Modifier.align(Alignment.TopCenter), top = true, cover = with(dn) { headH.toDp() }, haze = if (p.transp) null else chatHaze)
            EdgeFrost(Modifier.align(Alignment.BottomCenter), top = false, cover = with(dn) { footH.toDp() }, haze = if (p.transp) null else chatHaze)
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { headH = it.height }) {
            // En-tête (ou barre d'actions pendant une sélection)
            if (sel.isNotEmpty()) {
                val picked = list.filter { it.id in sel }
                val allMine = picked.all { it.from == u.id && it.id !in gone && !it.official }
                val one = picked.singleOrNull()
                val editable = one != null && one.from == u.id && one.id !in gone && Social.body(ui, one)?.str("k") == "text" && pg.store.online
                val clip = androidx.compose.ui.platform.LocalClipboardManager.current
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp).shadow((if (p.transp) 0.dp else 12.dp).ifShadows(), RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp)).background(p.glassStrong).border(1.dp, p.glassEdge, RoundedCornerShape(26.dp)).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconBtn("x", "Annuler la sélection", { sel = emptySet(); askDel = false }, plain = true, size = 44.dp)
                    Text(pl(sel.size, "sélectionné", "sélectionnés"), Modifier.weight(1f).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }, fontWeight = FontWeight.ExtraBold)
                    if (one != null && one.id !in gone) IconBtn("reply", "Répondre", { replyTo = one; sel = emptySet() }, plain = true, size = 44.dp)
                    IconBtn("copy", "Copier", {
                        val txt = picked.filter { it.id !in gone }.joinToString("\n") { m -> edits[m.id] ?: Social.preview(ui, m) }
                        clip.setText(androidx.compose.ui.text.AnnotatedString(txt)); sel = emptySet(); ui.toast(if (picked.size > 1) "Messages copiés." else "Message copié.", "copy")
                    }, plain = true, size = 44.dp)
                    if (editable) IconBtn("edit", "Modifier", { editing = one; sel = emptySet(); replyTo = null }, plain = true, size = 44.dp)
                    if (one != null && one.id !in gone && pg.store.online) IconBtn("pinned", if (pinId == one.id) "Désépingler" else "Épingler", {
                        send(buildJsonObject { put("k", "pin"); put("p", one.id); put("on", pinId != one.id) }, null, null); sel = emptySet()
                        ui.toast(if (pinId == one.id) "Message désépinglé." else "Message épinglé pour tout le monde.", "pinned")
                    }, plain = true, size = 44.dp)
                    IconBtn("trash", "Supprimer", { askDel = true }, plain = true, size = 44.dp, tint = p.danger)
                }
                AnimatedVisibility(askDel) {
                    Glass(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), strong = true, pad = 14.dp) {
                        Text("Supprimer ${pl(sel.size, "message", "messages")} ?", fontWeight = FontWeight.ExtraBold)
                        Muted(if (allMine) "« Pour tout le monde » remplace le message par « Message supprimé » chez chacun. « Pour moi » le masque seulement sur ce téléphone." else "Tu ne peux supprimer pour tout le monde que tes propres messages. Ils seront masqués sur ce téléphone.", Modifier.padding(vertical = 6.dp), small = true)
                        FlowRowCompat(gap = 6.dp) {
                            if (allMine && pg.store.online) PButton("Pour tout le monde", {
                                picked.forEach { m -> send(buildJsonObject { put("k", "del"); put("p", m.id) }, null, null) }
                                sel = emptySet(); askDel = false; ui.toast(if (picked.size > 1) "Messages supprimés." else "Message supprimé.", "trash")
                            }, icon = "trash", kind = "danger", small = true)
                            PButton("Pour moi", {
                                val ids = picked.map { it.id }.toSet(); hidden = hidden + ids; ui.prefs.hiddenMsgs = hidden; sel = emptySet(); askDel = false
                                ui.undoable(if (ids.size > 1) "Messages masqués." else "Message masqué.", "trash") { hidden = hidden - ids; ui.prefs.hiddenMsgs = hidden }
                            }, small = true)
                            PButton("Annuler", { askDel = false }, kind = "ghost", small = true)
                        }
                    }
                }
            } else
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBtn("chevronleft", "Retour", { ui.back() }, size = 44.dp)
                val headInter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val pill = RoundedCornerShape(26.dp)
                Row(Modifier.weight(1f).bounce(headInter, .97f).shadow((if (p.transp) 0.dp else 12.dp).ifShadows(), pill, ambientColor = Color(0x4428186E), spotColor = Color(0x4428186E))
                    .clip(pill).background(p.glassStrong).border(1.dp, p.glassEdge, pill)
                    .springClick(headInter) { if (g != null) ui.open(Sheet.GroupInfo(g.id)) else ui.open(Sheet.User(x!!.id)) }.padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                IconBtn("search", "Rechercher dans la conversation", { searching = !searching; if (!searching) sq = "" }, size = 44.dp, tint = if (searching) p.flame else null)
                if (g != null || !pg.isOfficial(x)) IconBtn(if (ttl > 0) "timer" else "palette", "Fond, couleur et messages éphémères", { palette = !palette }, size = 44.dp, tint = if (palette) p.flame else null)
                if (g != null || !pg.isOfficial(x)) IconBtn("lock", "Chiffrement de bout en bout : en savoir plus", { e2eOpen = !e2eOpen }, size = 44.dp, tint = if (e2eOpen) p.flame else null)
            }
            AnimatedVisibility(palette && sel.isEmpty()) {
                Glass(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), strong = true, pad = 12.dp) {
                    Text("Messages éphémères", fontWeight = FontWeight.ExtraBold)
                    Muted("Les messages que tu envoies disparaissent pour tout le monde après ce délai.", Modifier.padding(bottom = 8.dp), small = true)
                    Seg(listOf(0 to "Désactivés", 24 to "24 h", 168 to "7 jours"), ttl, { h -> ttl = h; ui.prefs.chatTtl = ui.prefs.chatTtl.toMutableMap().apply { if (h == 0) remove(key) else put(key, h) } })
                    Spacer(Modifier.height(12.dp))
                    ChatBgPicker(ui, bgKey, pg.chatBgChoices(shared, members), canWrite) { k -> send(buildJsonObject { put("k", "bg"); put("b", k ?: "") }, null, null); Feedback.tick() }
                    Spacer(Modifier.height(12.dp))
                    Text("Couleur de la conversation", fontWeight = FontWeight.ExtraBold)
                    Muted("Le fond et tes bulles prennent cette couleur, sur ce téléphone.", Modifier.padding(bottom = 8.dp), small = true)
                    FlowRowCompat(gap = 8.dp) {
                        val pick = { i: Int? -> colorIdx = i; ui.prefs.chatColors = ui.prefs.chatColors.toMutableMap().apply { if (i == null) remove(key) else put(key, i) }; Feedback.tick() }
                        Box(Modifier.size(40.dp).clip(CircleShape).background(p.ink.copy(alpha = .08f)).border(if (colorIdx == null) 3.dp else 1.dp, if (colorIdx == null) p.ink else p.line, CircleShape)
                            .clickable(onClickLabel = "Couleur par défaut") { pick(null) }.semantics { contentDescription = "Couleur par défaut"; selected = colorIdx == null }, contentAlignment = Alignment.Center) { Pi("x", size = 16.dp, tint = p.ink2) }
                        fr.pepsgoal.core.ACCENTS.forEachIndexed { i, a ->
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(a.c.map(::hex))).border(if (colorIdx == i) 3.dp else 1.dp, if (colorIdx == i) p.ink else Color.White.copy(alpha = .5f), CircleShape)
                                .clickable(onClickLabel = a.name) { pick(i) }.semantics { contentDescription = a.name; selected = colorIdx == i })
                        }
                    }
                }
            }
            // Duel en cours avec cet ami : mini tableau des scores en haut de la conversation
            if (x != null && sel.isEmpty()) pg.db.goals.firstOrNull { it.type == "duel" && it.completedAt == null && it.members?.containsAll(listOf(u.id, x.id)) == true }?.let { dg ->
                val a = pg.duelScore(dg, u.id); val b = pg.duelScore(dg, x.id)
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.glassStrong).border(1.dp, p.glassEdge, RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Ouvrir le duel") { ui.open(Sheet.Goal(dg.id)) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("⚔️", fontSize = 18.sp)
                    Column(Modifier.weight(1f)) {
                        Text("${dg.title} · ${dg.unit}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Toi ${fmtNum(round1(a))} – ${fmtNum(round1(b))} ${first(x.name)}", fontWeight = FontWeight.ExtraBold, color = if (a >= b) p.ok else p.ink)
                    }
                    Text(if (a > b) "En tête" else if (a < b) "À rattraper" else "Égalité", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink3)
                }
            }
            if (pinId != null && sel.isEmpty()) {
                val pm = list.first { it.id == pinId }
                val scope = rememberCoroutineScope()
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.glassStrong).border(1.dp, p.glassEdge, RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Aller au message épinglé") {
                        // Position dans la liste : chaque changement de jour ajoute un séparateur
                        var i = 0; var day = ""
                        for (m in list) { val d = ymd(m.at); if (d != day) { i++; day = d }; if (m.id == pm.id) break; i++ }
                        scope.launch { state.animateScrollToItem(i) }
                    }.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pi("pinned", size = 16.dp, tint = p.flameInk)
                    Column(Modifier.weight(1f)) {
                        Text("Épinglé", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = p.flameInk)
                        Text(edits[pm.id] ?: Social.preview(ui, pm), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (pg.store.online) IconBtn("x", "Désépingler", { send(buildJsonObject { put("k", "pin"); put("p", pm.id); put("on", false) }, null, null) }, plain = true, size = 36.dp)
                }
            }
            AnimatedVisibility(searching && sel.isEmpty()) {
                ChatSearchBar(sq, { sq = it; hit = 0 }, hit, hits.size, { if (hits.isNotEmpty()) hit = (hit + 1) % hits.size }, { if (hits.isNotEmpty()) hit = (hit - 1 + hits.size) % hits.size }, { searching = false; sq = "" })
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
            }
            // Pluie d'emojis : le dernier message en contient (à son arrivée, ou à l'ouverture s'il a moins de 3 minutes)
            val last = list.lastOrNull()
            val firstLast = remember(key) { last?.id }
            val burstKey = last?.takeIf { it.id != firstLast || nowMs() - it.at < 180_000 }?.id
            val burstEmojis = remember(burstKey) { last?.let { m -> Social.body(ui, m)?.let { b -> if (b.str("k") == "stk") listOf(b.str("t", "🔥")) else Emo.extract(b.str("t")) } }?.distinct()?.take(6) ?: emptyList() }
            EmojiBurst(burstKey, burstEmojis, with(dn) { footH.toDp() })
            // Bas : saisie ou statut
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { footH = it.height }) {
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
            if (status != null) Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp).shadow((if (p.transp) 0.dp else 14.dp).ifShadows(), RoundedCornerShape(22.dp), ambientColor = Color(0x4428186E), spotColor = Color(0x4428186E))
                .clip(RoundedCornerShape(22.dp)).background(p.glassStrong).border(1.dp, p.glassEdge, RoundedCornerShape(22.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (status == "blocked") { Muted("Tu as bloqué ce compte.", Modifier.weight(1f), small = true); PButton("Débloquer", { ui.run("${x!!.name} est débloqué.") { ui.cloud.unblock(x.id) } }, kind = "ghost", small = true) }
                else Muted(status, small = true)
            } else Column { ScheduledStrip(key); Composer(ui, u, key, g?.members, replyTo, { replyTo = null }, send, editing, edits[editing?.id ?: ""], { editing = null }) }
            }
        }
    } }
    photoView?.let { b -> PhotoViewer(b) { photoView = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    ui: AppState, m: Message, mine: Boolean, who: String?, members: List<String>?, reacts: Map<String, Set<String>>?, menuOpen: Boolean, more: Boolean,
    onMenu: () -> Unit, onMore: () -> Unit, onReact: (String) -> Unit, onReply: () -> Unit, onPhoto: (ByteArray) -> Unit, onVote: (JsonObject, String) -> Unit,
    votes: Map<String, List<String>>?, selected: Boolean = false, selecting: Boolean = false, editedText: String? = null, gone: Boolean = false,
    accent: List<Color>? = null, onTap: () -> Unit = {},
    /** Verre givré : la bulle floute le fond de la conversation qui passe derrière elle. */
    blur: dev.chrisbanes.haze.HazeState? = null
) {
    val p = LocalPal.current; val pg = ui.pg; val me = pg.meId()
    val pending = Social.pending(ui, m)
    val b0 = if (pending) null else Social.body(ui, m)
    // Texte modifié par son auteur
    val b = if (b0 != null && editedText != null && b0.str("k") in setOf("text", "photo", "goal")) JsonObject(b0 + ("t" to JsonPrimitive(editedText))) else b0
    val kind = if (gone) "gone" else b?.str("k") ?: "text"
    // Un à quatre emojis seuls, ou un sticker : affichés en grand, sans bulle
    val jumbo = if (kind == "text" && b?.get("r") == null) Emo.only(b?.str("t").orEmpty()) else null
    val bare = jumbo != null || kind == "stk"
    val canAct = !m.official && b != null && !pg.isBanned(pg.me()) && !gone
    val mentioned = b?.strs("mn")?.contains(me) == true
    // Glisser un message vers la droite pour y répondre (icône qui apparaît sous le doigt, clic net au seuil)
    val sw = rememberSwipe()
    Box(Modifier.fillMaxWidth()) {
    if (sw.dx > 0f) {
        val k = sw.progress(.2f); val armed = sw.armed != 0
        val pop by androidx.compose.animation.core.animateFloatAsState(if (armed) 1.12f else .55f + .4f * k, androidx.compose.animation.core.spring(dampingRatio = .45f, stiffness = 600f), label = "replyPop")
        Box(Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(34.dp).graphicsLayer { scaleX = pop; scaleY = pop; alpha = (k * 1.4f).coerceAtMost(1f) }
            .clip(CircleShape).background(if (armed) p.flame else p.glassStrong), contentAlignment = Alignment.Center) {
            Pi("reply", size = 18.dp, tint = if (armed) p.onAccent else p.ink)
        }
    }
    Column(Modifier.fillMaxWidth().swipeable(sw, left = false, right = true, threshold = .2f, enabled = canAct && !selecting) { onReply() }
        .clip(RoundedCornerShape(16.dp)).background(if (selected) p.flame.copy(alpha = .16f) else Color.Transparent)
        .semantics { this.selected = selected; customActions = if (canAct && !selecting) listOf(androidx.compose.ui.semantics.CustomAccessibilityAction("Répondre") { onReply(); true }) else emptyList() },
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        who?.let { Text(it, fontSize = 12.sp, color = p.ink2, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 10.dp, top = 6.dp)) }
        val shape = RoundedCornerShape(20.dp, 20.dp, if (mine) 6.dp else 20.dp, if (mine) 20.dp else 6.dp)
        // Bulles en relief : ombre douce, dégradé (les miennes aux couleurs de PepsGoal), reflet en haut ; arrivée à ressort
        val bg: Brush = when {
            m.official -> Brush.linearGradient(listOf(p.type("objective").c.copy(alpha = .3f), p.type("period").c.copy(alpha = .2f)))
            gone -> SolidColor(p.ink.copy(alpha = .06f))
            mine && accent != null -> Brush.linearGradient(listOf(accent[0].copy(alpha = .5f), accent[2].copy(alpha = .42f)))
            mine -> Brush.linearGradient(listOf(p.flame2.copy(alpha = if (p.materialYou) .5f else .38f), p.pink.copy(alpha = if (p.materialYou) .45f else .32f)))
            // Bulle reçue : verre plus léger quand le fond est flouté derrière, pour laisser voir le givre
            else -> SolidColor(if (blur != null) p.glass.copy(alpha = maxOf(p.glass.alpha, .5f)) else p.glassStrong)
        }
        // Flou de l'arrière-plan sous la bulle (les calques de messages ne sont jamais floutés, seulement le fond)
        val frost = if (blur == null || gone || bare) Modifier else Modifier.hazeEffect(blur, HazeStyle(backgroundColor = p.bg, tints = emptyList(), blurRadius = 22.dp, noiseFactor = 0f, fallbackTint = HazeTint(p.glassStrong))) {
            inputScale = HazeInputScale.Fixed(.5f)
        }
        Column(Modifier.widthIn(max = 300.dp).msgEnter(mine).shadow((if (p.transp || bare) 0.dp else 6.dp).ifShadows(), shape, ambientColor = Color(0x3328186E), spotColor = if (mine) p.flame.copy(alpha = .5f) else Color(0x4428186E))
            .clip(shape).then(frost).then(if (bare) Modifier else Modifier.background(if (p.contrast) SolidColor(p.glassStrong) else bg))
            .then(if (p.contrast || p.transp || bare) Modifier else Modifier.background(Brush.verticalGradient(0f to Color.White.copy(alpha = if (p.dark) .06f else .22f), .5f to Color.Transparent))).then(if (mentioned) Modifier.border(2.dp, p.flame2, shape) else Modifier)
            .then(if (selected) Modifier.border(2.dp, p.flame, shape) else Modifier)
            .combinedClickable(enabled = canAct || selecting || gone, onClick = onTap, onLongClick = onMenu, onLongClickLabel = "Sélectionner (réagir, répondre, copier, modifier, supprimer)", onClickLabel = if (selecting) (if (selected) "Désélectionner" else "Sélectionner") else null).padding(if (kind == "photo" || bare) 4.dp else 12.dp, if (kind == "photo" || bare) 4.dp else 9.dp)) {
            if (m.official) OFFICIAL_TAGS[m.tag]?.let { (l, ic) -> Chip(l, ic, p.type("objective").c, modifier = Modifier.padding(bottom = 6.dp)) }
            when {
                gone -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pi("ban", size = 15.dp, tint = p.ink3); Text(if (mine) "Tu as supprimé ce message" else "Message supprimé", fontSize = 14.sp, color = p.ink3, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) }
                pending -> Text("🔒 …", color = p.ink2)
                b == null -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pi("lock", size = 16.dp, tint = p.ink2); Text("Message chiffré : sa clé arrive dès qu’un appareil qui la possède est en ligne${if (pg.store.acct && !ui.cloud.e2e.hasVault()) ", ou déverrouille tes clés avec ton mot de passe ci-dessus" else ""}.", fontSize = 13.sp, color = p.ink2) }
                kind == "photo" -> {
                    val data = Att.get(ui, m, b.obj("a"))
                    val a = b.obj("a"); val ratio = (a.int("w").takeIf { it > 0 }?.toFloat() ?: 4f) / (a.int("h").takeIf { it > 0 }?.toFloat() ?: 3f)
                    val bmp = rememberDecoded(data) { data?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                    Box(Modifier.width(240.dp).aspectRatio(ratio.coerceIn(.5f, 2f)).clip(RoundedCornerShape(16.dp)).background(p.glass).clickable(enabled = data != null) { data?.let(onPhoto) }, contentAlignment = Alignment.Center) {
                        if (bmp != null) androidx.compose.foundation.Image(bmp, "Photo envoyée", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Pi("image", tint = p.ink3)
                    }
                    b.strN("t")?.takeIf { it.isNotEmpty() }?.let { Text(it, Modifier.padding(6.dp)) }
                }
                kind == "voice" -> { VoicePlayer(ui, m, b); VoiceTranscript(ui, m, Att.get(ui, m, b.obj("a"))) }
                kind == "stk" -> StickerView(b.str("s"), b.str("t"), 132.dp)
                jumbo != null -> JumboEmojis(jumbo)
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
                kind == "duo" -> DuoInvite(ui, m, b, mine)
                else -> MsgText(ui, b, members)
            }
            Text("${if (b?.get("x") != null && !gone) "⏳ " else ""}${if (editedText != null && !gone) "modifié · " else ""}${hm(m.at)}", Modifier.align(Alignment.End).padding(top = 2.dp, end = if (kind == "photo") 6.dp else 0.dp), fontSize = 11.sp, color = p.ink3)
        }
        val items = reacts?.filter { it.value.isNotEmpty() }
        if (!items.isNullOrEmpty()) Row(Modifier.padding(top = 2.dp, start = 6.dp, end = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { (e, set) ->
                val on = set.contains(me)
                Row(Modifier.clip(CircleShape).background(if (on) p.flame.copy(alpha = .3f) else p.glassStrong).border(1.dp, p.line, CircleShape).clickable(enabled = canAct) { onReact(e) }.padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) { Emoji(e, 16.dp); Text("${set.size}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
        if (menuOpen && canAct && b != null) Column(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(20.dp)).background(p.glassStrong).border(1.dp, p.line, RoundedCornerShape(20.dp)).padding(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                QUICK_REACTS.forEach { e -> val on = reacts?.get(e)?.contains(me) == true; Box(Modifier.size(38.dp).clip(CircleShape).background(if (on) p.flame.copy(alpha = .3f) else Color.Transparent).clickable(onClickLabel = "Réagir $e") { onReact(e) }, contentAlignment = Alignment.Center) { Emoji(e, 24.dp) } }
                IconBtn(if (more) "minus" else "plus", if (more) "Revenir aux emojis de base" else "Plus d’emojis", onMore, plain = true, size = 36.dp)
            }
            if (more) FlowRowCompat(Modifier.widthIn(max = 300.dp), gap = 2.dp) { MORE_REACTS.forEach { e -> Box(Modifier.size(36.dp).clip(CircleShape).clickable { onReact(e) }, contentAlignment = Alignment.Center) { Emoji(e, 22.dp) } } }
            PButton("Répondre", onReply, icon = "reply", kind = "ghost", small = true)
        }
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
    Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { IconTile("poll", p.flame2, p.pink, 28.dp, 9.dp); Text(b.str("q", "Sondage"), fontWeight = FontWeight.ExtraBold) }
        opts.forEach { o ->
            val n = counts[o.str("id")] ?: 0; val pct = if (voters > 0) n.toFloat() / voters else 0f; val on = o.str("id") in mine
            val names = votes.filter { o.str("id") in it.value }.keys.joinToString(", ") { if (it == me) "toi" else first(pg.userById(it)?.name ?: "?") }
            val topN = counts.values.maxOrNull() ?: 0
            PollOption(o.str("t"), pct, voters > 0, on, n == topN && n > 0, true, { onVote(b, o.str("id")) }, sub = names, count = "$n")
        }
        Muted("${pl(voters, "votant", "votants")} · ${if (b.bool("multi")) "plusieurs réponses" else "une réponse"}", small = true)
    }
}

@Composable
private fun Composer(ui: AppState, u: User, key: String, members: List<String>?, replyTo: Message?, clearReply: () -> Unit, send: (JsonElement, ByteArray?, String?) -> Unit,
                     editing: Message? = null, editedText: String? = null, clearEdit: () -> Unit = {}) {
    val p = LocalPal.current; val pg = ui.pg; val ctx = LocalContext.current
    // Message prérempli (bravo pour la série d’un ami) : le texte est prêt, il reste à l’envoyer
    var tv by remember { val d = ui.chatDraft.orEmpty(); ui.chatDraft = null; mutableStateOf(TextFieldValue(d, TextRange(d.length))) }
    // Modification d'un message : la zone de saisie reprend son texte
    LaunchedEffect(editing?.id) { if (editing != null) { val t0 = editedText ?: Social.body(ui, editing)?.str("t").orEmpty(); tv = TextFieldValue(t0, TextRange(t0.length)) } }
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

    // Zone de saisie : carte de verre flottante au-dessus des messages
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)
        .shadow((if (p.transp) 0.dp else 16.dp).ifShadows(), RoundedCornerShape(26.dp), ambientColor = Color(0x5528186E), spotColor = Color(0x4428186E))
        .clip(RoundedCornerShape(26.dp)).background(p.glassStrong).border(1.dp, p.glassEdge, RoundedCornerShape(26.dp)).padding(8.dp)
        .animateContentSize(spring(dampingRatio = .75f, stiffness = 500f)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (Voice.recording) {
            VoiceRecorderBar(
                onCancel = { Voice.stop(true) },
                onSend = { Voice.stop(false)?.let { (d, dur, w) -> sendVoice(send, d, dur, w) } })
            return@Column
        }
        when (panel) {
            "tools" -> FlowRowCompat(gap = 6.dp) {
                PButton("Photo", { panel = null; pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = "image", small = true)
                PButton("Objectif", { panel = "goal" }, icon = "target", small = true)
                PButton("Sondage", { panel = "poll" }, icon = "poll", small = true)
            }
            "emoji", "stk" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FChip("Emojis", panel == "emoji", { panel = "emoji" }); FChip("Stickers", panel == "stk", { panel = "stk" }) }
                if (panel == "stk") Box(Modifier.heightIn(max = 220.dp).verticalScrollCompat()) { StickerPicker { d -> panel = null; Feedback.tick(); send(buildJsonObject { put("k", "stk"); put("s", d.id); put("t", d.emoji) }, null, null) } }
                else EmojiPicker(height = 200) { e -> val s = tv.selection; val t = tv.text.replaceRange(s.min, s.max, e); if (t.length <= 500) tv = TextFieldValue(t, TextRange(s.min + e.length)) }
            }
            "sched" -> SchedulePanel({ at ->
                val text = tv.text.trim()
                if (text.isNotEmpty()) { Scheduled.add(ctx, key, JsonPrimitive(text), at, text); tv = TextFieldValue(""); panel = null
                    ui.toast("Message programmé : ${if (ymd(at) == today()) "aujourd’hui" else if (ymd(at) == ymd(todayDate().plusDays(1))) "demain" else fmtDate(ymd(at))} à ${hm(at)}.", "clock") }
            }, { panel = null })
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
        editing?.let { e ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.flame.copy(alpha = .12f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pi("edit", size = 16.dp, tint = p.flameInk)
                Column(Modifier.weight(1f)) { Text("Modifier le message", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold); Text(editedText ?: Social.preview(ui, e), fontSize = 12.sp, color = p.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                IconBtn("x", "Annuler la modification", { clearEdit(); tv = TextFieldValue("") }, plain = true, size = 32.dp)
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
                val opts = members.filter { it != u.id }.mapNotNull { pg.userById(it) }.filter { it.username.startsWith(q) || it.name.lowercase().contains(q) || pg.realName(it).lowercase().contains(q) }.take(6)
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
            IconBtn("smile", "Emojis et stickers", { panel = if (panel == "emoji" || panel == "stk") null else "emoji" }, plain = true, size = 40.dp)
            val composeInter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val composeFocus by composeInter.collectIsFocusedAsState()
            TextField(tv, { if (it.text.length <= 500) tv = it }, Modifier.weight(1f).border(if (composeFocus) 1.5.dp else 1.dp, if (composeFocus) p.focus else ctlEdge(p), RoundedCornerShape(24.dp)),
                placeholder = { Text("Écrire un message…", color = p.ink3) }, maxLines = 5, shape = RoundedCornerShape(24.dp), interactionSource = composeInter,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                colors = TextFieldDefaults.colors(focusedContainerColor = p.glassStrong, unfocusedContainerColor = p.glassStrong, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = p.flame, focusedTextColor = p.ink, unfocusedTextColor = p.ink))
            if (tv.text.isBlank()) IconBtn("mic", "Enregistrer un message vocal", {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRec() else micPerm.launch(Manifest.permission.RECORD_AUDIO)
            }, plain = true, size = 40.dp)
            else {
            // Programmer l'envoi (texte simple, hors modification et réponse)
            if (editing == null && replyTo == null) IconBtn("clock", "Programmer l’envoi", { panel = if (panel == "sched") null else "sched" }, plain = true, size = 40.dp, tint = if (panel == "sched") p.flame else null)
            IconBtn(if (editing != null) "check" else "send", if (editing != null) "Enregistrer la modification" else "Envoyer", {
                val text = tv.text.trim(); if (text.isEmpty()) return@IconBtn
                if (editing != null) {
                    if (text != (editedText ?: Social.body(ui, editing)?.str("t"))) send(buildJsonObject { put("k", "edit"); put("p", editing.id); put("t", text) }, null, null)
                    tv = TextFieldValue(""); clearEdit(); return@IconBtn
                }
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

/** Arrivée d'une bulle : elle glisse depuis son côté et grandit avec un rebond. */
@Composable
private fun Modifier.msgEnter(mine: Boolean): Modifier {
    if (!fr.pepsgoal.app.sys.Perf.flags.rich) return this
    val motionOff = LocalPal.current.motion
    val a = remember { androidx.compose.animation.core.Animatable(if (motionOff) 1f else 0f) }
    LaunchedEffect(Unit) { if (a.value < 1f) { delay(Stagger.next() / 2); a.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = .55f, stiffness = 320f)) } }
    return this.graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f); translationX = (1f - v) * (if (mine) 56.dp else (-56).dp).toPx()
        val sc = .82f + .18f * v; scaleX = sc; scaleY = sc
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (mine) 1f else 0f, 1f)
    }
}

/** Barre d'enregistrement d'un vocal : point rouge pulsant, chronomètre, onde en direct, temps restant, annuler / envoyer. */
@Composable
private fun VoiceRecorderBar(onCancel: () -> Unit, onSend: () -> Unit) {
    val p = LocalPal.current
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "rec")
    val pulse by inf.animateFloat(1f, if (p.motion) 1f else 1.9f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Restart), label = "pulse")
    Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Point rouge avec halo qui s'étend
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(12.dp).graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = (1.9f - pulse) / .9f }.clip(CircleShape).background(p.danger.copy(alpha = .45f)))
                Box(Modifier.size(12.dp).clip(CircleShape).background(p.danger))
            }
            Text(fmtDur(Voice.secs.toDouble()), fontFamily = fr.pepsgoal.app.ui.theme.Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            // Onde en direct : barres arrondies, les plus récentes à droite
            androidx.compose.foundation.Canvas(Modifier.weight(1f).height(34.dp)) {
                val n = 36; val gap = size.width / n; val bw = (gap * .55f).coerceAtLeast(2f)
                val lv = Voice.live.toList()
                for (i in 0 until n) {
                    val v = lv.getOrNull(lv.size - n + i) ?: 0f
                    val bh = (size.height * (.12f + .88f * v)).coerceAtLeast(bw)
                    drawRoundRect(Brush.verticalGradient(listOf(p.flame2, p.pink)), androidx.compose.ui.geometry.Offset(i * gap + (gap - bw) / 2, (size.height - bh) / 2),
                        androidx.compose.ui.geometry.Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(bw / 2), alpha = if (i < n - lv.size) .25f else 1f)
                }
            }
        }
        // Temps restant
        val left = (Voice.MAX_SECS - Voice.secs).coerceAtLeast(0f)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Bar(Voice.secs / Voice.MAX_SECS * 100.0, height = 5.dp, color = if (left < 15) p.danger else null)
            Muted("Enregistrement en cours · encore ${fmtDur(left.toDouble())}", small = true, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBtn("trash", "Annuler l’enregistrement", onCancel, size = 46.dp, tint = p.danger)
            PButton("Envoyer le vocal", onSend, Modifier.weight(1f), icon = "send", kind = "primary")
        }
    }
}

/** Invitation à un objectif en duo : l'ami la rejoint d'un geste, une copie de l'objectif est créée chez lui. */
@Composable
private fun DuoInvite(ui: AppState, m: Message, b: JsonObject, mine: Boolean) {
    val p = LocalPal.current; val pg = ui.pg; val app = LocalContext.current.peps
    val sg = b.obj("g"); val type = sg.str("type").takeIf { it in TYPES } ?: "routine"; val tc = p.type(type); val id = b.str("id")
    val local = pg.db.goals.firstOrNull { it.duoId == id && it.userId == pg.meId() }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(tc.c.copy(alpha = .2f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🤝 Objectif en duo", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = tc.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Emoji(sg.str("icon", "🎯"), 30.dp)
            Column { Text(sg.str("title", "Objectif"), fontWeight = FontWeight.ExtraBold); Text(TYPES.getValue(type).label, fontSize = 12.sp, color = tc.ink) }
        }
        when {
            mine -> Muted(if (local != null && fr.pepsgoal.app.sys.Duo.state(app, local) == fr.pepsgoal.app.sys.Duo.State.JOINED) "Invitation acceptée : votre flamme commune est lancée." else "Une flamme commune, qui ne brûle que si vous validez tous les deux.", small = true)
            local != null -> PButton("Ouvrir l’objectif", { ui.open(Sheet.Goal(local.id)) }, icon = "target", small = true)
            else -> PButton(if (busy) "…" else "Rejoindre le duo", {
                if (busy) return@PButton
                busy = true
                ui.scope.launch {
                    val g = try { fr.pepsgoal.app.sys.Duo.join(app, m.from, b) } catch (e: Exception) { null }
                    busy = false
                    if (g != null) { ui.toast("Duo lancé : « ${g.title} » est dans tes objectifs.", "users"); ui.open(Sheet.Goal(g.id)) } else ui.toast("Impossible de rejoindre ce duo.", "x")
                }
            }, icon = "users", small = true, kind = "primary", enabled = !busy)
        }
    }
}
