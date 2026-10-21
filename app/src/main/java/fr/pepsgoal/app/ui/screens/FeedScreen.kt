package fr.pepsgoal.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Fs
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.*
import fr.pepsgoal.core.model.Goal
import fr.pepsgoal.core.model.Post
import fr.pepsgoal.core.model.User

@Composable
fun FeedScreen(ui: AppState) {
    ui.watch()
    val pg = ui.pg; val u = pg.me() ?: return
    val fr = (listOf(u.id) + pg.friendsOf(u.id)).toSet()
    val posts = pg.db.posts.filter { pg.postVisible(it, u.id) && if (ui.feedTab == "friends") it.userId in fr && pg.canSeePost(it, u.id) else it.visibility == "public" }.sortedByDescending { it.at }
    val banned = pg.isBanned(u)
    ScreenList {
        if (banned) item("ban") { BanBanner(ui, u) }
        if (u.warned != null && !banned) item("warn") { Note("Ton compte est sous avertissement : tes publications sont vérifiées par la modération avant d'être visibles.") }
        if (!banned) item("stories") { StoriesBar(ui, u) }
        item("tabs") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Seg(listOf("friends" to "Mes amis", "public" to "Découvrir"), ui.feedTab, { ui.feedTab = it; ui.postLimit = 20 }, icons = listOf("users", "globe"))
                Spacer(Modifier.weight(1f))
                if (!banned) PButton("Story", { if (!pg.socialBlocked("de publier une story")) ui.open(Sheet.StoryNew()) }, icon = "image", small = true)
            }
        }
        item("gel") { GelCard(ui, u, true) }
        if (posts.isEmpty()) item("empty") { Glass { Empty(if (ui.feedTab == "friends") "Ton fil est encore calme" else "Aucune publication publique", if (ui.feedTab == "friends") "Ajoute des amis ou publie ta première victoire." else "Sois la première personne à partager.") } }
        posts.take(ui.postLimit).forEach { p -> item(p.id) { PostCard(ui, p) } }
        if (posts.size > ui.postLimit) item("more") { PButton("Voir plus de publications (${posts.size - ui.postLimit})", { ui.postLimit += 20 }, Modifier.fillMaxWidth()) }
    }
}

@Composable
fun BanBanner(ui: AppState, u: User) {
    val p = LocalPal.current; val pg = ui.pg
    val ap = pg.db.appeals.filter { it.userId == u.id }.maxByOrNull { it.at }
    Glass(border = p.danger.copy(alpha = .45f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pi("ban", tint = p.danger); Text("Accès social suspendu", fontWeight = FontWeight.ExtraBold, color = p.danger, fontSize = 17.sp) }
        Spacer(Modifier.height(8.dp))
        Text("Tu ne peux plus publier, commenter, encourager ni envoyer de messages ${pg.banText(u)}.${u.ban?.reason?.takeIf { it.isNotEmpty() }?.let { " Motif : $it." } ?: ""} Tes objectifs et ta série restent intacts.")
        if (ap?.status == "pending") Muted("Demande de débannissement envoyée ${ago(ap.at)}, en attente de réponse.", Modifier.padding(top = 8.dp), small = true)
        else {
            if (ap?.status == "refused") Muted("Ta dernière demande a été refusée${ap.answer.takeIf { it.isNotEmpty() }?.let { " : « $it »" } ?: ""}.", Modifier.padding(top = 8.dp), small = true)
            PButton("Demander un débannissement", { ui.open(Sheet.Appeal) }, Modifier.padding(top = 10.dp), small = true)
        }
    }
}

@Composable
fun GelCard(ui: AppState, u: User, compact: Boolean) {
    val p = LocalPal.current; val social = ui.pg.socialOn(u)
    val c = u.cheersIn % FREEZE_EVERY; val left = FREEZE_EVERY - c
    val txt = if (!social) "Un gel protège automatiquement ta série un jour où tu n'as rien validé. Active les fonctions sociales pour en gagner d'autres."
    else if (compact) "Encore ${pl(left, "encouragement reçu", "encouragements reçus")} sur tes publications pour gagner un gel."
    else "$c/$FREEZE_EVERY encouragements reçus avant ton prochain gel. Un gel protège automatiquement ta série un jour où tu n'as rien validé."
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(p.type("objective").c.copy(alpha = .22f)), contentAlignment = Alignment.Center) { Pi("snow", size = 26.dp, tint = p.type("objective").ink) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pl(u.freezes, "gel de série", "gels de série"), fontWeight = FontWeight.ExtraBold)
                if (social) Bar(c * 100.0 / FREEZE_EVERY, color = p.type("objective").c)
                Muted(txt, small = true)
            }
        }
    }
}

@Composable
fun StoriesBar(ui: AppState, u: User) {
    val p = LocalPal.current; val pg = ui.pg
    val groups = pg.storyUsers(u.id); val mine = groups.firstOrNull { it.u.id == u.id }; val others = groups.filter { it.u.id != u.id }
    Glass(pad = 14.dp) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StoryBubble("Ta story", u, if (mine != null) "ring" else "none", { if (mine != null) openStories(ui, u.id) else if (!pg.socialBlocked("de publier une story")) ui.open(Sheet.StoryNew()) }) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(p.ink).border(2.dp, p.bg, CircleShape).clickable { if (!pg.socialBlocked("de publier une story")) ui.open(Sheet.StoryNew()) }, contentAlignment = Alignment.Center) { Pi("plus", size = 14.dp, tint = p.bg) }
            }
            others.forEach { g -> StoryBubble(first(g.u.name), g.u, if (g.unseen) "ring" else "seen", { openStories(ui, g.u.id) }) }
            if (others.isEmpty()) Muted("Les stories de tes amis s'affichent ici pendant 24 h.", Modifier.width(220.dp).align(Alignment.CenterVertically), small = true)
        }
    }
}

fun openStories(ui: AppState, startId: String) {
    val groups = ui.pg.storyUsers(); val idx = groups.indexOfFirst { it.u.id == startId }; if (idx < 0) return
    val g = groups[idx]; val si = if (g.u.id == ui.pg.meId()) 0 else maxOf(0, g.list.indexOfFirst { !it.seenBy.contains(ui.pg.meId()) })
    ui.fs = Fs.Stories(groups, idx, si)
}

@Composable
private fun StoryBubble(label: String, u: User, ring: String, onClick: () -> Unit, add: (@Composable () -> Unit)? = null) {
    val p = LocalPal.current
    Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box {
            Box(Modifier.size(66.dp).clip(CircleShape).background(when (ring) { "ring" -> Brush.sweepGradient(listOf(p.flame2, p.flame, p.pink, p.flame2)); "seen" -> androidx.compose.ui.graphics.SolidColor(p.line); else -> androidx.compose.ui.graphics.SolidColor(Color.Transparent) })
                .then(if (ring == "none") Modifier.border(2.dp, p.line, CircleShape) else Modifier).clickable(onClick = onClick).padding(3.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.border(3.dp, p.bg, CircleShape)) { Av(u, 58.dp) }
            }
            if (add != null) Box(Modifier.align(Alignment.BottomEnd)) { add() }
        }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GoalAttach(ui: AppState, g: Goal?) {
    val pg = ui.pg; if (g == null || !pg.canSeeGoal(g, pg.meId())) return
    val p = LocalPal.current; val tc = p.type(g.type); val s = pg.statBig(g)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(tc.c.copy(alpha = .16f)).clickable { if (g.userId == pg.meId()) ui.open(Sheet.Goal(g.id)) else ui.open(Sheet.User(g.userId)) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GoalIcon(g, 38.dp)
        Column { Text(g.title, fontWeight = FontWeight.ExtraBold); Muted("${typeInfo(g.type).label} · ${s.v} ${s.l}", small = true) }
    }
}

@Composable
fun PostCard(ui: AppState, post: Post) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me()!!; val a = pg.userById(post.userId) ?: return
    val liked = post.likes.contains(u.id); val adm = pg.isAdmin(u); val pend = post.status == "pending"
    var photo by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }
    Glass(border = if (pend) p.type("limit").c else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.weight(1f).clickable { ui.open(Sheet.User(a.id)) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Av(a, 42.dp)
                Column {
                    Text(if (a.id == u.id) "Toi" else a.name, fontWeight = FontWeight.ExtraBold)
                    Muted("@${a.username} · ${ago(post.at)} · ${VIS[post.visibility]?.first ?: ""}", small = true, maxLines = 1)
                }
            }
            if (adm) IconBtn(if (post.commentsLocked) "comment" else "lock", if (post.commentsLocked) "Rouvrir les commentaires" else "Fermer les commentaires", { ui.run(if (post.commentsLocked) "Commentaires rouverts." else "Commentaires fermés sur cette publication.", "lock") { ui.cloud.modPost(post.id, if (post.commentsLocked) "unlock" else "lock") } }, plain = true, size = 38.dp)
            when {
                post.userId == u.id -> IconBtn("trash", "Supprimer la publication", { ui.confirm("Supprimer la publication ?", "Elle disparaîtra du feed de tes amis.", "Supprimer") { ui.run { ui.cloud.deletePost(post.id) } } }, plain = true, size = 38.dp)
                adm -> IconBtn("trash", "Supprimer la publication (modération)", { modDeletePost(ui, post) }, plain = true, size = 38.dp)
                else -> IconBtn("flag", "Signaler la publication", { ui.open(Sheet.Report(post.id)) }, plain = true, size = 38.dp)
            }
        }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (pend) Chip("En attente de validation par la modération", "clock", p.type("limit").c, p.type("limit").ink)
            if (post.kind == "milestone") Chip("Palier atteint", "medal", p.flame, p.ink)
            if (post.text.isNotEmpty()) Text(post.text, fontSize = 15.5.sp)
            post.poll?.let { PollView(ui, post) }
            post.photo?.let { url ->
                AsyncImage(url, "Photo partagée par ${a.name}", Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(18.dp)).clickable { photo = true }, contentScale = ContentScale.Crop)
                if (photo) PhotoViewer(url) { photo = false }
            }
            post.goalId?.let { GoalAttach(ui, pg.goalById(it)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CheerButton(liked, post.likes.size) {
                    if (pg.socialBlocked("d'encourager")) return@CheerButton
                    if (!liked && !p.motion) ui.sparks(1)
                    ui.run { ui.cloud.toggleLike(post) }
                }
                Muted(pl(post.comments.size, "commentaire", "commentaires"), small = true)
            }
            post.comments.filter { !pg.isBlockedPair(it.userId, u.id) }.forEach { c ->
                val cu = pg.userById(c.userId)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Av(cu, 28.dp)
                    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(p.ink.copy(alpha = .06f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(if (cu?.id == u.id) "Toi" else cu?.name ?: "", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        Text("${c.text}  ·  ${ago(c.at)}", fontSize = 14.sp)
                    }
                }
            }
            when {
                post.commentsLocked -> Muted("🔒 Les commentaires sont fermés sur cette publication.", small = true)
                !pg.canComment(u, post) -> Muted("Tu ne peux pas commenter pour le moment.", small = true)
                pend -> {}
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(comment, { comment = it }, "Écrire un encouragement…", Modifier.weight(1f), max = 280)
                    PButton("Envoyer", {
                        val t = comment.trim(); if (t.isEmpty() || pg.socialBlocked("de commenter")) return@PButton
                        comment = ""; ui.run { ui.cloud.comment(post, t) }
                    }, small = true)
                }
            }
        }
    }
}

fun modDeletePost(ui: AppState, post: Post) {
    ui.confirm("Supprimer cette publication ?", "« ${post.text.take(80)}${if (post.text.length > 80) "…" else ""} » sera supprimée pour tout le monde. L'auteur sera prévenu.", "Supprimer") {
        ui.run("Publication supprimée.", "trash") { ui.cloud.modPost(post.id, "delete") }
    }
}

@Composable
fun CheerButton(on: Boolean, n: Int, onClick: () -> Unit) {
    val p = LocalPal.current
    val pop = remember { Animatable(1f) }
    LaunchedEffect(on) { if (on && !p.motion) { pop.snapTo(.8f); pop.animateTo(1f, spring(dampingRatio = .35f)) } }
    Row(Modifier.scale(pop.value).clip(CircleShape).background(if (on) p.flame.copy(alpha = .22f) else p.ink.copy(alpha = .06f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Flame(18.dp, dim = !on); Text("$n", fontWeight = FontWeight.ExtraBold); Text("Encourager", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun PollView(ui: AppState, post: Post) {
    val p = LocalPal.current; val pg = ui.pg; val poll = post.poll ?: return
    val closed = nowMs() >= poll.endsAt; val voted = poll.options.any { it.mine }; val showRes = closed || voted || post.userId == pg.meId()
    val top = poll.options.maxOfOrNull { it.n } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        poll.options.forEach { o ->
            val pct = if (poll.voters > 0) Math.round(o.n * 100.0 / poll.voters).toInt() else 0
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.5.dp, if (o.mine) p.flame else p.line, RoundedCornerShape(14.dp))
                .clickable(enabled = !closed) { if (!pg.socialBlocked("de voter")) ui.run { ui.cloud.vote(post, o.id) } }) {
                if (showRes) Box(Modifier.matchParentSize().fillMaxWidth(pct / 100f).background(if (o.n == top && o.n > 0) p.flame.copy(alpha = .28f) else p.ink.copy(alpha = .08f)))
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (o.mine) Pi("check", size = 16.dp, tint = p.flame)
                    Text(o.t, Modifier.weight(1f).padding(start = if (o.mine) 6.dp else 0.dp), fontWeight = FontWeight.Bold)
                    if (showRes) Text("$pct %", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Muted("${pl(poll.voters, "votant", "votants")} · ${if (poll.multi) "plusieurs réponses" else "une réponse"} · ${if (closed) "sondage terminé" else "se termine ${fmtLeft(poll.endsAt - nowMs())}"}${if (!closed && !voted && post.userId != pg.meId()) " · vote pour voir les résultats" else ""}", small = true)
    }
}

@Composable
fun PhotoViewer(model: Any, onClose: () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .94f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            AsyncImage(model, "Photo", Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) { IconBtn("x", "Fermer la photo", onClose) }
        }
    }
}
