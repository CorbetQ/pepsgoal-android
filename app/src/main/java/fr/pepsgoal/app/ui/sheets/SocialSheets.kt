package fr.pepsgoal.app.ui.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.screens.GoalAttach
import fr.pepsgoal.app.ui.screens.GroupAv
import fr.pepsgoal.app.ui.screens.StatusChips
import fr.pepsgoal.app.ui.screens.signOut
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.*
import fr.pepsgoal.core.model.Goal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Dictionnaire Kotlin → objet JSON (instantanés de story). */
fun jsonOf(m: Map<String, Any?>): JsonObject = buildJsonObject {
    m.forEach { (k, v) -> when (v) { null -> put(k, JsonNull); is Number -> put(k, v); is Boolean -> put(k, v); else -> put(k, v.toString()) } }
}

fun copyText(ctx: Context, ui: AppState, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("PepsGoal", text))
    ui.toast("Texte copié : colle-le où tu veux.", "share")
}

/** Feuille de partage d'Android (autres applications). */
fun shareExternal(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "$text https://app.pepsgoal.fr"); putExtra(Intent.EXTRA_TITLE, "PepsGoal") }
    ctx.startActivity(Intent.createChooser(i, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/* ================================================================== profil d'une personne */
@Composable
fun UserSheet(ui: AppState, id: String) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val x = pg.userById(id) ?: run { LaunchedEffect(Unit) { ui.close() }; return }
    LaunchedEffect(id) { if (pg.store.acct && id != u.id && !ui.cloud.profileLoaded.contains(id)) try { ui.cloud.loadProfile(id) } catch (e: Exception) {} }
    val self = x.id == u.id; val f = pg.friendship(u.id, x.id)
    val iBlocked = !self && pg.blockedBy(u.id, x.id); val theyBlocked = !self && pg.blockedBy(x.id, u.id)
    SheetHead(ui, if (self) "Ton profil public" else "Profil")
    if (iBlocked || theyBlocked) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) { Av(x, 64.dp); Column { Text(x.name, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp); Muted("@${x.username}") } }
        if (iBlocked) { Muted("Tu as bloqué ce compte : vous ne voyez plus vos contenus respectifs et vous ne pouvez plus vous écrire."); PButton("Débloquer", { ui.run("${x.name} est débloqué.") { ui.cloud.unblock(x.id) } }, icon = "check") }
        else Muted("Ce profil n’est pas disponible.")
        return
    }
    val lv = pg.level(pg.points(x.id)); val st = pg.globalStreak(x.id)
    var renaming by remember(id) { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LevelRing(lv.tier, 64.dp) { Av(x, 64.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(x.name, Modifier.weight(1f, fill = false), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); if (x.demo) Chip("démo")
                if (!self && x.role != "official") IconBtn("edit", "Renommer ${x.name}", { renaming = !renaming }, plain = true, size = 34.dp)
            }
            // Surnom : on rappelle le nom que la personne s'est choisi
            if (pg.nickname(x.id) != null) Muted("Surnom · son nom : ${pg.realName(x)}", small = true)
            Muted("@${x.username} · ${lv.name}", small = true)
            pg.statusOf(x.id)?.let { st -> Muted("${st.e} ${st.l.ifBlank { tr("Statut du jour") }} · ${ago(st.at)}", small = true) }
            if (x.bio.isNotEmpty()) Text(x.bio)
        }
    }
    if (renaming) NicknameEditor(ui, x) { renaming = false }
    if (!self) FlowRowCompat(gap = 8.dp) {
        when {
            f == null -> PButton("Ajouter", { if (!pg.socialBlocked("d'ajouter des amis")) ui.run("Demande envoyée à ${first(x.name)}.", "userplus") { ui.cloud.sendFriendRequest(x.id) } }, icon = "userplus", kind = "primary", small = true)
            f.status == "accepted" -> { Chip("Amis", "check", p.type("routine").c); PButton("Message", { ui.push(Screen.Chat(with = x.id)) }, icon = "comment", small = true)
                // Défi en un geste : duel prérempli contre cet ami
                PButton("Défier", { ui.open(Sheet.GoalForm(preType = "duel", preTitle = "Duel avec ${first(x.name)}", preIcon = "⚔️", preFields = mapOf("opponent" to x.id))) }, icon = "duel", small = true)
                PButton("Offrir", { ui.open(Sheet.Gift(x.id)) }, icon = "gift", small = true) }
            f.to == u.id -> PButton("Accepter la demande", { ui.run("Vous êtes maintenant amis.", "users") { ui.cloud.acceptRequest(f.id) } }, kind = "primary", small = true)
            else -> Chip("Demande envoyée")
        }
        if (x.role == null && !x.guest) PButton("Bloquer", {
            ui.confirm("Bloquer ${x.name} ?", "Vous ne verrez plus vos publications, commentaires et stories respectifs, vous ne pourrez plus vous écrire ni vous ajouter en ami, et votre amitié sera retirée. Tu pourras le débloquer dans Paramètres › Social et confidentialité.", "Bloquer", back = Sheet.User(x.id)) {
                ui.run("${x.name} est bloqué.", "ban") { ui.cloud.block(x.id) }
            }
        }, icon = "ban", kind = "ghost", small = true)
        if (pg.isAdmin(u)) PButton("Modérer", { ui.open(Sheet.ModUser(x.id)) }, icon = "shield", kind = "danger", small = true)
    }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("${st.n}" to "Série", "${lv.pts}" to "Flammes", "${pg.friendsOf(x.id).size}" to "Amis").forEach { (v, l) ->
            Glass(Modifier.weight(1f).fillMaxHeight(), pad = 10.dp, radius = 16.dp) { Text(v, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp); Text(l, fontSize = 12.sp, color = p.ink2, fontWeight = FontWeight.Bold) }
        }
    }
    // Fiche privée (note, anniversaire) : pour les amis seulement
    if (!self && f?.status == "accepted") fr.pepsgoal.app.ui.screens.FriendPrivateCard(ui, x)
    SectionTitle("Objectifs visibles")
    val goals = pg.db.goals.filter { it.userId == x.id && !it.archived && pg.canSeeGoal(it, u.id) }
    if (goals.isEmpty()) Muted(if (pg.areFriends(u.id, x.id) || self) "Aucun objectif partagé." else "Ajoute cette personne en ami pour voir ses objectifs partagés.", small = true)
    goals.forEach { g -> val s = pg.statBig(g)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoalIcon(g, 40.dp)
            Column(Modifier.weight(1f)) { Text(g.title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { TypeChip(g.type); Muted("${s.v} ${s.l}", small = true) } }
        }
    }
    val posts = pg.db.posts.filter { it.userId == x.id && pg.canSeePost(it, u.id) }.sortedByDescending { it.at }.take(3)
    if (posts.isNotEmpty()) {
        SectionTitle("Dernières publications")
        posts.forEach { post -> Glass(pad = 12.dp, radius = 16.dp) { Muted("${ago(post.at)} · ${VIS[post.visibility]?.first ?: ""}${if (post.photo != null) " · 📷 photo" else ""}", small = true); if (post.text.isNotEmpty()) Text(post.text) } }
    }
}

/** Renommer un ami : un surnom que toi seul vois, partout dans l'app (fil, messages, classements, duels). */
@Composable
fun NicknameEditor(ui: AppState, x: fr.pepsgoal.core.model.User, done: () -> Unit) {
    val pg = ui.pg; val has = pg.nickname(x.id) != null
    var v by remember(x.id) { mutableStateOf(pg.nickname(x.id) ?: "") }
    Glass(pad = 12.dp, radius = 16.dp) {
        Field(v, { v = it }, "Surnom", placeholder = pg.realName(x), max = 40, icon = "edit")
        Muted("Toi seul le vois : ${first(pg.realName(x))} garde son nom pour les autres.", small = true)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton("Enregistrer", {
                val n = v.trim(); pg.setNickname(x.id, n); ui.bump(); done(); Feedback.tick()
                ui.toast(if (n.isEmpty()) "Surnom retiré : ${pg.realName(x)} retrouve son nom." else "${pg.realName(x)} s’appelle maintenant « $n » pour toi.", "check")
            }, icon = "check", kind = "primary", small = true)
            if (has) PButton("Retirer le surnom", { pg.setNickname(x.id, ""); ui.bump(); done(); ui.toast("Surnom retiré : ${pg.realName(x)} retrouve son nom.", "x") }, kind = "ghost", small = true)
            else PButton("Annuler", done, kind = "ghost", small = true)
        }
    }
}

/* ================================================================== notifications */
@Composable
fun NotifsSheet(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg
    val list = pg.db.notifs.filter { it.userId == pg.meId() }.sortedByDescending { it.at }.take(40)
    val ic = mapOf("period" to "clock", "mention" to "comment", "story" to "image", "group" to "group", "friend" to "userplus", "like" to "feed", "comment" to "comment", "reminder" to "bell",
        "deadline" to "cal", "badge" to "medal", "freeze" to "snow", "mod" to "shield", "official" to "flameo", "verify" to "shield")
    SheetHead(ui, "Notifications")
    if (list.isEmpty()) Empty("Tout est calme", "Tes rappels et l'activité de tes amis apparaîtront ici.", icon = "bell")
    list.forEach { n ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (n.read) Color.Transparent else p.flame.copy(alpha = .1f)).clickable {
            when {
                n.goalId != null && pg.goalById(n.goalId) != null -> ui.sheet = Sheet.Goal(n.goalId!!)
                n.view == "profile" -> ui.nav("profile")
                n.view == "evening" -> ui.open(Sheet.Evening)
                n.view == "feed" || n.type in setOf("like", "comment") -> if (pg.socialOn()) ui.nav("feed")
                n.type == "friend" -> if (pg.socialOn()) ui.nav("friends")
                n.type == "story" -> if (pg.socialOn()) ui.nav("feed")
            }
        }.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(p.glass), contentAlignment = Alignment.Center) { if (n.type == "like") Flame(19.dp) else Pi(ic[n.type] ?: "bell", size = 18.dp) }
            Column(Modifier.weight(1f)) { Text(n.text, fontSize = 14.sp, fontWeight = if (n.read) FontWeight.Medium else FontWeight.Bold); Muted(ago(n.at), small = true) }
        }
    }
}

/* ================================================================== partage */
@Composable
fun ShareHubSheet(ui: AppState, m: Sheet.ShareHub) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return; val ctx = LocalContext.current
    val g = m.goalId?.let { pg.goalById(it) }
    val text = m.text ?: g?.let { pg.shareText(it) } ?: "Je suis sur PepsGoal !"
    val social = pg.socialOn(u) && !pg.isGuest(u) && !pg.isBanned(u)
    if (m.step == "msg") {
        SheetHead(ui, "Envoyer en message")
        if (g != null) GoalAttach(ui, g) else Quote(text)
        Muted("Choisis une conversation : le partage est chiffré de bout en bout, comme tes messages.", small = true)
        val friends = pg.friendsOf(u.id).mapNotNull { pg.userById(it) }.filter { !pg.isBlockedPair(u.id, it.id) }
        val groups = pg.db.chats.filter { it.members.contains(u.id) }
        if (friends.isEmpty() && groups.isEmpty()) Muted("Ajoute des amis pour leur envoyer tes réussites.")
        val send = { chat: String?, to: String?, name: String ->
            val body: JsonElement = g?.let { gg -> val s = pg.statBig(gg); buildJsonObject { put("k", "goal"); putJsonObject("g") { put("id", gg.id); put("owner", gg.userId); put("title", gg.title); put("icon", gg.icon); put("type", gg.type); put("v", s.v); put("l", s.l) } } } ?: JsonPrimitive(text)
            ui.close(); ui.run { if (ui.cloud.e2e.deliver(chat, to, body) != null) ui.toast("Partagé avec $name.", "send") }
        }
        groups.forEach { c -> ShareTarget({ GroupAv(c.emoji, 36) }, c.name, pl(c.members.size, "membre", "membres")) { send(c.id, null, c.name) } }
        friends.forEach { x -> ShareTarget({ Av(x, 36.dp) }, x.name, "@${x.username}") { send(null, x.id, first(x.name)) } }
        SheetFoot { PButton("Retour", { ui.sheet = Sheet.ShareHub(m.goalId, m.text, m.period, null) }, kind = "ghost") }
        return
    }
    SheetHead(ui, "Partager")
    if (g != null) GoalAttach(ui, g) else Quote(text)
    val canStory = social && (g != null || m.period != null)
    @Composable fun Opt(ic: String, l: String, d: String, ok: Boolean, mod: Modifier, onClick: () -> Unit) {
        Column(mod.clip(RoundedCornerShape(18.dp)).background(p.glass).border(1.dp, p.line, RoundedCornerShape(18.dp)).clickable(enabled = ok, onClick = onClick).padding(14.dp).then(if (ok) Modifier else Modifier.background(Color.Transparent)),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (ok) p.accent else Brush.linearGradient(listOf(p.line, p.line))), contentAlignment = Alignment.Center) { Pi(ic, tint = p.onAccent) }
            Text(l, fontWeight = FontWeight.ExtraBold, color = if (ok) p.ink else p.ink3); Text(d, fontSize = 12.sp, color = p.ink2, lineHeight = 15.sp)
        }
    }
    val off = "Fonctions sociales désactivées"
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Opt("feed", "Feed", if (social) "Une publication pour tes amis ou tout le monde" else off, social, Modifier.weight(1f).fillMaxHeight()) { ui.sheet = Sheet.ShareFeed(g?.id, if (g != null) "${pg.statBig(g).v} ${pg.statBig(g).l} pour « ${g.title} »." else text) }
        Opt("image", "Story", if (canStory) (if (g != null) "Ta progression, 24 h pour tes amis" else "Ton récap, 24 h pour tes amis") else if (social) "Pas de story pour ce partage" else off, canStory, Modifier.weight(1f).fillMaxHeight()) {
            if (!pg.socialBlocked("de publier une story")) ui.sheet = Sheet.StoryNew(if (g != null) "progress" else "recap", m.period ?: "week", g?.id)
        }
    }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Opt("comment", "Message", if (social) "À un ami ou à un groupe, chiffré" else off, social, Modifier.weight(1f).fillMaxHeight()) { ui.sheet = Sheet.ShareHub(m.goalId, m.text, m.period, "msg") }
        Opt("ext", "Externe", "Autres applications de ton téléphone", true, Modifier.weight(1f).fillMaxHeight()) { shareExternal(ctx, text) }
    }
    // Stories Instagram et WhatsApp : image verticale de la progression (ou de la série)
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Opt("image", "Instagram", "Image verticale pour ta story", true, Modifier.weight(1f).fillMaxHeight()) { fr.pepsgoal.app.sys.StoryShare.send(ctx, fr.pepsgoal.app.sys.StoryShare.INSTAGRAM, g, text) }
        Opt("send", "WhatsApp", "Image et texte, en statut ou à un contact", true, Modifier.weight(1f).fillMaxHeight()) { fr.pepsgoal.app.sys.StoryShare.send(ctx, fr.pepsgoal.app.sys.StoryShare.WHATSAPP, g, text) }
    }
}

@Composable
private fun Quote(text: String) {
    val p = LocalPal.current
    Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.glass).border(1.dp, p.line, RoundedCornerShape(14.dp)).padding(14.dp), fontWeight = FontWeight.Bold)
}

@Composable
private fun ShareTarget(av: @Composable () -> Unit, name: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        av(); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.ExtraBold); Muted(sub, small = true) }; Pi("send")
    }
}

@Composable
fun ShareFeedSheet(ui: AppState, goalId: String?, text0: String) {
    val pg = ui.pg; val g = goalId?.let { pg.goalById(it) }
    var text by remember { mutableStateOf(text0) }; var vis by remember { mutableStateOf("friends") }; var busy by remember { mutableStateOf(false) }
    SheetHead(ui, "Partager dans le feed")
    if (g != null) GoalAttach(ui, g)
    Field(text, { text = it }, "Ton message", singleLine = false, minLines = 3, max = 500)
    Seg(listOf("friends" to "Amis", "public" to "Public"), vis, { vis = it }, icons = listOf("users", "globe"))
    SheetFoot {
        PButton("Annuler", { ui.close() }, kind = "ghost")
        PButton("Publier", { publish(ui, text.trim(), null, null, vis, g?.id) { busy = it } }, kind = "primary", enabled = !busy)
    }
}

private fun publish(ui: AppState, text: String, photo: ByteArray?, poll: PollDraft?, vis: String, goalId: String?, setBusy: (Boolean) -> Unit) {
    val pg = ui.pg
    if (poll != null && text.isEmpty()) { ui.toast("Écris la question du sondage dans ta publication.", "x"); return }
    if (text.isEmpty() && photo == null) { ui.toast("Écris quelque chose ou ajoute une photo avant de publier.", "x"); return }
    if (pg.socialBlocked("de publier")) return
    setBusy(true)
    ui.scope.launch {
        try {
            val st = ui.cloud.publish(text, photo, poll, vis, goalId)
            ui.close(); ui.nav("feed"); ui.feedTab = if (vis == "public" && ui.feedTab == "public") "public" else "friends"
            ui.toast(if (st == "pending") "Envoyé à la modération : ta publication sera visible après validation." else "Publié.", "feed")
        } catch (e: Exception) { ui.toast(e.message ?: "Publication impossible.", "x") } finally { setBusy(false) }
    }
}

@Composable
fun ShareTextSheet(ui: AppState, text0: String) {
    val ctx = LocalContext.current
    if (ui.pg.socialOn()) { ShareFeedSheet(ui, null, text0); return }
    SheetHead(ui, "Copier le résumé")
    Muted("Les fonctions sociales sont désactivées : copie ce résumé pour le partager où tu veux.")
    Quote(text0)
    SheetFoot { PButton("Partager", { shareExternal(ctx, text0) }, icon = "ext"); PButton("Copier", { copyText(ctx, ui, text0) }, kind = "primary") }
}

/* ================================================================== nouvelle publication */
@Composable
fun ComposeSheet(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return; val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }; var vis by remember { mutableStateOf("friends") }; var goal by remember { mutableStateOf<String?>(null) }
    var photo by remember { mutableStateOf<Pair<ByteArray, Bitmap>?>(null) }; var panel by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    val opts = remember { mutableStateListOf("", "") }; var multi by remember { mutableStateOf(false) }; var hours by remember { mutableIntStateOf(24) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ui.scope.launch { val r = withContext(Dispatchers.IO) { Media.decode(ctx, uri, 1600)?.let { Media.jpegUnder(it, 900_000) to it } }; if (r == null) ui.toast("Impossible de lire cette image. Essaie un fichier JPG ou PNG.", "image") else photo = r }
    }
    SheetHead(ui, "Nouvelle publication")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Av(u, 42.dp); Field(text, { text = it }, "Ta publication", Modifier.weight(1f), placeholder = "Partage une victoire, un doute, un cap franchi…", singleLine = false, minLines = 3, max = 500) }
    photo?.let { (_, b) -> Box { Image(b.asImageBitmap(), "Aperçu de la photo", Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop); IconBtn("x", "Retirer la photo", { photo = null }, Modifier.align(Alignment.TopEnd).padding(6.dp)) } }
    FlowRowCompat(gap = 6.dp) {
        PButton("Photo", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = "image", small = true)
        PButton("Emojis", { panel = if (panel == "emoji") null else "emoji" }, icon = "smile", small = true, kind = if (panel == "emoji") "primary" else "normal")
        PButton("Sondage", { panel = if (panel == "poll") null else "poll" }, icon = "poll", small = true, kind = if (panel == "poll") "primary" else "normal")
    }
    if (panel == "emoji") EmojiPicker(height = 180) { e -> if (text.length + e.length <= 500) text += e }
    if (panel == "poll") {
        Muted("La question du sondage est le texte de ta publication.", small = true)
        opts.forEachIndexed { i, o -> Field(o, { opts[i] = it }, "Choix ${i + 1}", max = 80) }
        if (opts.size < 6) PButton("Ajouter un choix", { opts.add("") }, icon = "plus", kind = "ghost", small = true)
        SetRow("Plusieurs réponses possibles") { PSwitch(multi, { multi = it }) }
        Seg(listOf(24 to "1 jour", 72 to "3 jours", 168 to "1 semaine"), hours, { hours = it })
    }
    Seg(listOf("friends" to "Amis", "public" to "Public"), vis, { vis = it }, icons = listOf("users", "globe"))
    val goals = pg.myGoals(u.id).filter { it.userId == u.id && !it.archived }
    if (goals.isNotEmpty()) {
        SectionTitle("Lier un objectif (optionnel)")
        GoalChips(goals, goal, { goal = it }, none = "Aucun", bleed = 20.dp)
    }
    SheetFoot {
        PButton("Annuler", { ui.close() }, kind = "ghost")
        PButton("Publier", {
            var poll: PollDraft? = null
            if (panel == "poll") {
                val list = opts.map { it.trim() }.filter { it.isNotEmpty() }
                if (list.size < 2) { ui.toast("Ajoute au moins deux choix au sondage.", "x"); return@PButton }
                if (list.map { it.lowercase() }.toSet().size != list.size) { ui.toast("Deux choix du sondage sont identiques.", "x"); return@PButton }
                poll = PollDraft(list.take(6), multi, hours)
            }
            publish(ui, text.trim(), photo?.first, poll, vis, goal) { busy = it }
        }, kind = "primary", enabled = !busy)
    }
}

/* ================================================================== nouvelle story */
@Composable
fun StoryNewSheet(ui: AppState, s0: Sheet.StoryNew) {
    val pg = ui.pg; val u = pg.me() ?: return; val ctx = LocalContext.current
    var kind by remember { mutableStateOf(s0.kind) }; var period by remember { mutableStateOf(s0.period) }
    val goals = pg.myGoals(u.id).filter { !it.archived }
    var goalId by remember { mutableStateOf(s0.goalId ?: goals.firstOrNull()?.id) }
    var photo by remember { mutableStateOf<Pair<ByteArray, Bitmap>?>(null) }; var text by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ui.scope.launch { val r = withContext(Dispatchers.IO) { Media.decode(ctx, uri, 1440)?.let { Media.jpegUnder(it, 900_000) to it } }; if (r == null) ui.toast("Impossible de lire cette image. Essaie un fichier JPG ou PNG.", "image") else photo = r }
    }
    LaunchedEffect(kind) { if (kind == "photo" && photo == null) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val g = goalId?.let { pg.goalById(it) }
    val data: JsonObject? = when (kind) { "recap" -> jsonOf(Recaps.snap(pg, u.id, period)); "progress" -> g?.let { jsonOf(pg.progressSnap(it)) }; else -> null }
    val ready = if (kind == "photo") photo != null else data != null
    SheetHead(ui, "Nouvelle story")
    Seg(listOf("photo" to "Photo", "recap" to "Récap", "progress" to "Progression"), kind, { kind = it }, icons = listOf("image", "star", "trend"))
    when (kind) {
        "photo" -> PButton(if (photo != null) "Changer de photo" else "Choisir une photo", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = "image")
        "recap" -> Seg(listOf("week" to "Semaine", "month" to "Mois", "year" to "Année"), period, { period = it })
        else -> if (goals.isEmpty()) Muted("Crée d'abord un objectif pour partager ta progression.", small = true)
            else GoalChips(goals, goalId, { it?.let { id -> goalId = id } }, bleed = 20.dp)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(220.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(22.dp))) {
            if (kind == "photo") photo?.let { Image(it.second.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Box(Modifier.fillMaxSize().background(LocalPal.current.glass), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Pi("image"); Muted("Choisis une photo à partager", small = true) } }
            else if (data != null) StoryCard(kind, data, compact = true)
        }
    }
    Field(text, { text = it }, "Description (optionnelle)", placeholder = "Une phrase pour accompagner ta story", max = 140)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pi("users", size = 16.dp); Muted("Visible uniquement par tes amis, pendant 24 heures.", small = true) }
    SheetFoot {
        PButton("Annuler", { ui.close() }, kind = "ghost")
        PButton("Publier la story", {
            if (pg.socialBlocked("de publier une story")) return@PButton
            busy = true
            ui.scope.launch {
                try { ui.cloud.publishStory(kind, data, photo?.first, text.trim()); ui.close(); ui.toast("Story publiée : visible par tes amis pendant 24 h.", "image") }
                catch (e: Exception) { ui.toast(e.message ?: "Publication impossible.", "x") } finally { busy = false }
            }
        }, kind = "primary", enabled = ready && !busy)
    }
}

/** Carte d'une story « progression » ou « récap » (aperçu et visionneuse). */
@Composable
fun StoryCard(kind: String, d: JsonObject, compact: Boolean) {
    val p = LocalPal.current
    val k = if (compact) .62f else 1f
    if (kind == "recap") {
        Column(Modifier.fillMaxSize().background(Brush.linearGradient(SBG[0].map(::hex))).padding((24 * k).dp), verticalArrangement = Arrangement.spacedBy((10 * k).dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("RÉCAP PEPSGOAL", fontSize = (12 * k).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, color = Color(0xFF10102A))
            Text(d.str("title"), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = (28 * k).sp, color = Color(0xFF10102A), textAlign = TextAlign.Center)
            Emoji(d.str("e"), (64 * k).dp)
            Text("Profil : ${d.str("persona")}", fontWeight = FontWeight.ExtraBold, color = Color(0xFF10102A), fontSize = (15 * k).sp)
            listOf(listOf("n" to "validations", "pts" to "flammes"), listOf("best" to "meilleure série", "reg" to "régularité")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy((8 * k).dp)) {
                    row.forEach { (key, l) -> Column(Modifier.weight(1f).clip(RoundedCornerShape((14 * k).dp)).background(Color.White.copy(alpha = .55f)).padding((8 * k).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${d.int(key)}${if (key == "reg") "%" else ""}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = (20 * k).sp, color = Color(0xFF10102A)); Text(l, fontSize = (11 * k).sp, color = Color(0xFF10102A)) } }
                }
            }
        }
        return
    }
    val type = d.str("type").takeIf { it in TYPES } ?: "routine"; val c = p.type(type).c
    Column(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(androidx.compose.ui.graphics.lerp(c, Color.White, .45f), c))).padding((24 * k).dp),
        verticalArrangement = Arrangement.spacedBy((8 * k).dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(d.str("label").uppercase(), fontSize = (12 * k).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, color = Color(0xFF10102A))
        Emoji(d.str("icon"), (60 * k).dp)
        Text(d.str("title"), fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = (20 * k).sp, color = Color(0xFF10102A), textAlign = TextAlign.Center)
        Text(d.str("v"), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = (64 * k).sp, color = Color(0xFF10102A))
        Text(d.str("l"), fontWeight = FontWeight.ExtraBold, color = Color(0xFF10102A), fontSize = (15 * k).sp)
        (d["pct"] as? JsonPrimitive)?.doubleOrNull?.let { pct -> Box(Modifier.fillMaxWidth(.8f).height((10 * k).dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .5f))) { Box(Modifier.fillMaxHeight().fillMaxWidth(pct.toFloat().coerceIn(0f, 1f)).background(Color(0xFF10102A))) } }
        Text(d.str("meta"), fontSize = (12 * k).sp, color = Color(0xFF10102A), textAlign = TextAlign.Center)
    }
}

/* ================================================================== signalement, contestation */
@Composable
fun ReportSheet(ui: AppState, postId: String) {
    var reason by remember { mutableStateOf("spam") }
    SheetHead(ui, "Signaler la publication")
    SectionTitle("Motif")
    FlowRowCompat(gap = 6.dp) { REASON_LBL.forEach { (k, l) -> FChip(l, reason == k, { reason = k }) } }
    Muted("La modération examinera la publication. La personne ne saura pas qui l'a signalée.", small = true)
    SheetFoot { PButton("Annuler", { ui.close() }, kind = "ghost"); PButton("Signaler", { ui.close(); ui.run("Merci : la modération va examiner cette publication.", "flag") { ui.cloud.report(postId, reason) } }, kind = "danger") }
}

@Composable
fun AppealSheet(ui: AppState) {
    var text by remember { mutableStateOf("") }
    SheetHead(ui, "Demander un débannissement")
    Muted("Explique en quelques mots pourquoi ton accès devrait être rétabli. La modération te répondra par notification.")
    Field(text, { text = it }, "Ton message", singleLine = false, minLines = 4, max = 500)
    SheetFoot {
        PButton("Annuler", { ui.close() }, kind = "ghost")
        PButton("Envoyer la demande", { if (text.isBlank()) { ui.toast("Écris ton message.", "x"); return@PButton }; ui.close(); ui.run("Demande envoyée à la modération.", "send") { ui.cloud.appeal(text.trim()) } }, kind = "primary")
    }
}

/* ================================================================== modération d'un compte */
@Composable
fun ModUserSheet(ui: AppState, id: String) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val x = pg.userById(id) ?: run { LaunchedEffect(Unit) { ui.close() }; return }
    val self = x.id == u.id
    var reason by remember { mutableStateOf("") }; var dur by remember { mutableStateOf("7") }; var msg by remember { mutableStateOf("") }
    SheetHead(ui, "Modération du compte")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Av(x, 56.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(x.name, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp); Muted("@${x.username} · inscrit ${ago(x.createdAt)}", small = true); FlowRowCompat(gap = 4.dp) { StatusChips(ui, x) } }
    }
    if (pg.isAdmin(x)) { Muted("Les comptes administrateurs ne peuvent pas être modérés ici."); return }
    Field(reason, { reason = it }, "Motif (envoyé à la personne)", placeholder = "Ex. propos insultants dans les commentaires", max = 160)
    @Composable fun Block(title: String, danger: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, if (danger) p.danger.copy(alpha = .5f) else p.line, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, fontWeight = FontWeight.ExtraBold); content() }
    }
    val body = { buildJsonObject { put("reason", reason.trim()) } }
    Block("Avertissement") {
        if (x.warned != null) { Muted("Averti ${ago(x.warned!!.at)}${x.warned!!.reason.takeIf { it.isNotEmpty() }?.let { " : $it" } ?: ""}. Ses publications passent par la validation.", small = true); PButton("Lever l'avertissement", { ui.run("Avertissement levé.") { ui.cloud.modUser(x.id, "unwarn") } }, small = true) }
        else { Muted("Ses prochaines publications devront être validées avant d'être visibles.", small = true); PButton("Avertir", { ui.run("${x.name} est sous avertissement.", "bell") { ui.cloud.modUser(x.id, "warn", body()) } }, icon = "bell", small = true) }
    }
    Block("Plateforme sociale") {
        if (pg.isBanned(x)) { Muted("Banni ${pg.banText(x)}${x.ban?.reason?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""}.", small = true); PButton("Lever le ban", { ui.run("Ban levé.") { ui.cloud.modUser(x.id, "unban") } }, small = true) }
        else {
            Muted("Bloque les publications, commentaires, encouragements, demandes d'amis et messages. Ses objectifs restent accessibles.", small = true)
            FlowRowCompat(gap = 6.dp) { listOf("1" to "24 heures", "7" to "7 jours", "30" to "30 jours", "perm" to "Définitif").forEach { (k, l) -> FChip(l, dur == k, { dur = k }) } }
            PButton("Bannir", { ui.run("${x.name} est banni.", "ban") { ui.cloud.modUser(x.id, "ban", buildJsonObject { put("days", if (dur == "perm") JsonNull else JsonPrimitive(dur.toInt())); put("reason", reason.trim()) }) } }, icon = "ban", kind = "danger", small = true)
        }
    }
    Block("Restreindre les commentaires") {
        SetRow("Ne peut plus commenter les publications.") { PSwitch(x.noComments, { on -> ui.run(if (on) "Commentaires restreints." else "Commentaires autorisés.", "comment") { ui.cloud.modUser(x.id, "comments", buildJsonObject { put("restricted", on) }) } }) }
    }
    Block("Message de PepsGoal Officiel") {
        Field(msg, { msg = it }, "Écrire à ${first(x.name)} au nom de PepsGoal Officiel…", singleLine = false, minLines = 3, max = 600)
        PButton("Envoyer", { if (msg.isBlank()) { ui.toast("Écris le message à envoyer.", "send"); return@PButton }; val t = msg.trim(); ui.run("Message envoyé à ${x.name} au nom de PepsGoal Officiel.", "send") { ui.cloud.modUser(x.id, "message", buildJsonObject { put("text", t) }); msg = "" } }, icon = "send", small = true)
    }
    Block("Zone dangereuse", danger = true) {
        Muted("Supprime le compte, ses objectifs, ses publications, ses commentaires et ses messages. Action définitive.", small = true)
        if (!self) PButton("Supprimer le compte", { ui.confirm("Supprimer le compte de ${x.name} ?", "Le compte, ses objectifs, ses publications, ses commentaires et ses messages seront supprimés définitivement.", "Supprimer le compte", back = Sheet.ModUser(x.id)) { ui.run("Compte de ${x.name} supprimé.", "trash") { ui.cloud.modDeleteUser(x.id) } } }, icon = "trash", kind = "danger", small = true)
    }
}

/* ================================================================== groupes de discussion */
@Composable
fun GroupNewSheet(ui: AppState) {
    val pg = ui.pg; val u = pg.me() ?: return
    var name by remember { mutableStateOf("") }; var emoji by remember { mutableStateOf(GROUP_EMOJIS[0]) }; var members by remember { mutableStateOf(listOf<String>()) }
    var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }; var pickE by remember { mutableStateOf(false) }
    SheetHead(ui, "Nouveau groupe")
    Field(name, { name = it }, "Nom du groupe", placeholder = "Ex. Team running du dimanche", max = 40)
    SetRow("Icône") { GroupAv(emoji, 44); PButton(if (pickE) "Fermer" else "Changer", { pickE = !pickE }, small = true) }
    if (pickE) EmojiPicker(emoji) { emoji = it; pickE = false }
    SectionTitle("Membres")
    FriendPicker(ui, members, false) { members = it }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pi("lock", size = 16.dp); Muted("Les messages du groupe sont chiffrés de bout en bout.", small = true) }
    Err(err)
    SheetFoot {
        PButton("Annuler", { ui.close() }, kind = "ghost")
        PButton("Créer le groupe", {
            err = when { name.isBlank() -> "Donne un nom au groupe."; members.isEmpty() -> "Choisis au moins un ami."; else -> null }
            if (err != null || pg.socialBlocked("de créer un groupe")) return@PButton
            busy = true
            ui.scope.launch {
                try { val gid = ui.cloud.createGroup(name.trim(), emoji, members); ui.close(); ui.push(Screen.Chat(group = gid)); ui.toast("Groupe « ${name.trim()} » créé.", "group") }
                catch (e: Exception) { err = e.message ?: "Création impossible." } finally { busy = false }
            }
        }, kind = "primary", enabled = !busy)
    }
}

@Composable
fun GroupInfoSheet(ui: AppState, gid: String) {
    ui.watch()
    val pg = ui.pg; val u = pg.me() ?: return
    val g = pg.db.chats.firstOrNull { it.id == gid }
    if (g == null || !g.members.contains(u.id)) { LaunchedEffect(Unit) { ui.close() }; return }
    val owner = g.owner == u.id
    var name by remember { mutableStateOf(g.name) }; var emoji by remember { mutableStateOf(g.emoji) }; var pickE by remember { mutableStateOf(false) }; var add by remember { mutableStateOf(listOf<String>()) }
    SheetHead(ui, "${g.emoji} ${g.name}")
    if (owner) {
        Field(name, { name = it }, "Nom du groupe", max = 40)
        SetRow("Icône") { GroupAv(emoji, 44); PButton(if (pickE) "Fermer" else "Changer", { pickE = !pickE }, small = true) }
        if (pickE) EmojiPicker(emoji) { emoji = it; pickE = false }
        PButton("Enregistrer", { if (name.isBlank()) { ui.toast("Donne un nom au groupe.", "x"); return@PButton }; ui.run("Groupe mis à jour.") { ui.cloud.updateGroup(g.id, name.trim(), emoji) } }, icon = "check", small = true)
    }
    SectionTitle("Membres (${g.members.size})")
    g.members.forEach { id ->
        val x = pg.userById(id)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Av(x, 36.dp)
            Column(Modifier.weight(1f)) { Text(if (id == u.id) "Toi" else x?.name ?: "Compte supprimé", fontWeight = FontWeight.ExtraBold); Muted("@${x?.username ?: "—"}${if (g.owner == id) " · créateur" else ""}", small = true) }
            if (owner && id != u.id) PButton("Retirer", { ui.run("${first(x?.name)} a été retiré du groupe.") { ui.cloud.removeGroupMember(g.id, id) } }, kind = "ghost", small = true)
        }
    }
    if (owner && pg.friendsOf(u.id).any { it !in g.members }) {
        SectionTitle("Ajouter des amis")
        FriendPicker(ui, add, false, exclude = g.members) { add = it }
        if (add.isNotEmpty()) PButton("Ajouter", { val list = add; add = emptyList(); ui.run(pl(list.size, "membre ajouté", "membres ajoutés"), "userplus") { ui.cloud.addGroupMembers(g.id, list) } }, icon = "userplus", small = true)
    }
    FlowRowCompat(gap = 8.dp) {
        PButton("Discussion", { ui.close(); if ((ui.screens.lastOrNull() as? Screen.Chat)?.group != g.id) ui.push(Screen.Chat(group = g.id)) }, icon = "comment", kind = "ghost")
        PButton("Quitter", { ui.confirm("Quitter « ${g.name} » ?", "Tu ne recevras plus les messages de ce groupe.", "Quitter", back = Sheet.GroupInfo(g.id)) { ui.screens.removeAll { it is Screen.Chat && it.group == g.id }; ui.run("Tu as quitté le groupe.", "logout") { ui.cloud.removeGroupMember(g.id, u.id) } } }, icon = "logout")
        if (owner) PButton("Supprimer le groupe", { ui.confirm("Supprimer « ${g.name} » ?", "Le groupe et tous ses messages seront supprimés pour tous les membres.", "Supprimer", back = Sheet.GroupInfo(g.id)) { ui.screens.removeAll { it is Screen.Chat && it.group == g.id }; ui.run("Groupe supprimé.", "trash") { ui.cloud.deleteGroup(g.id) } } }, icon = "trash", kind = "danger")
    }
}

/* ================================================================== suppression du compte */
@Composable
fun DeleteAccountSheet(ui: AppState) {
    var pw by remember { mutableStateOf("") }; var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    SheetHead(ui, "Supprimer ton compte ?")
    Muted("Ton profil, tes objectifs, tes publications, tes stories et tes messages seront supprimés définitivement du serveur PepsGoal et de cet appareil.")
    Field(pw, { pw = it }, "Confirme avec ton mot de passe", password = true)
    Err(err)
    SheetFoot {
        PButton("Annuler", { ui.close() }, kind = "ghost")
        PButton("Supprimer définitivement", {
            if (pw.isEmpty()) { err = "Indique ton mot de passe."; return@PButton }
            busy = true
            ui.scope.launch { try { ui.cloud.deleteAccount(pw) } catch (e: ApiError) { err = if (e.status == 401 || e.status == 403) "Mot de passe incorrect." else e.message } catch (e: Exception) { err = e.message } finally { busy = false } }
        }, icon = "trash", kind = "danger", enabled = !busy)
    }
}
