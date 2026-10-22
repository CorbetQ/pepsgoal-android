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
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
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
import fr.pepsgoal.app.Screen
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
        item("challenges") { ChallengesCard(ui) }
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
fun PostCard(ui: AppState, post: Post, inPage: Boolean = false) {
    // Les encouragements, commentaires et votes modifient la publication sur place (même objet) : sans lecture de la
    // révision des données, Compose sauterait la carte et le compteur ne bougerait qu'au prochain rafraîchissement
    ui.rev
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me()!!; val a = pg.userById(post.userId) ?: return
    val liked = post.likes.contains(u.id); val adm = pg.isAdmin(u); val pend = post.status == "pending"
    var photo by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    // Saveur de la publication : palier, badge, sondage… (bandeau coloré en tête de carte)
    val flavor = when {
        pend -> null
        post.kind == "milestone" -> Triple("medal", "Palier atteint", p.flame)
        post.text.startsWith("Nouveau badge") -> Triple("trophy", "Badge débloqué", androidx.compose.ui.graphics.Color(0xFFFFB000))
        post.poll != null -> Triple("poll", "Sondage", p.type("objective").c)
        else -> null
    }
    Glass(border = if (pend) p.type("limit").c else null, pad = 0.dp) {
        // Bandeau de saveur : dégradé discret sur toute la largeur
        if (flavor != null) Row(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(flavor.third.copy(alpha = .28f), flavor.third.copy(alpha = .04f))))
            .padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pi(flavor.first, size = 15.dp, tint = flavor.third)
            Text(flavor.second.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = p.ink2)
        }
        Column(Modifier.padding(start = 18.dp, end = 10.dp, top = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { ui.open(Sheet.User(a.id)) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Av(a, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(if (a.id == u.id) "Toi" else a.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Muted("@${a.username} · ${ago(post.at)}", small = true, maxLines = 1)
                            VIS[post.visibility]?.let { (_, ic) -> Pi(ic, size = 12.dp, tint = p.ink3) }
                        }
                    }
                }
                if (adm) IconBtn(if (post.commentsLocked) "comment" else "lock", if (post.commentsLocked) "Rouvrir les commentaires" else "Fermer les commentaires", { ui.run(if (post.commentsLocked) "Commentaires rouverts." else "Commentaires fermés sur cette publication.", "lock") { ui.cloud.modPost(post.id, if (post.commentsLocked) "unlock" else "lock") } }, plain = true, size = 36.dp)
                when {
                    post.userId == u.id -> IconBtn("trash", "Supprimer la publication", { ui.confirm("Supprimer la publication ?", "Elle disparaîtra du feed de tes amis.", "Supprimer") { ui.run { ui.cloud.deletePost(post.id) } } }, plain = true, size = 36.dp)
                    adm -> IconBtn("trash", "Supprimer la publication (modération)", { modDeletePost(ui, post) }, plain = true, size = 36.dp)
                    else -> IconBtn("flag", "Signaler la publication", { ui.open(Sheet.Report(post.id)) }, plain = true, size = 36.dp)
                }
            }
        }
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pend) Chip("En attente de validation par la modération", "clock", p.type("limit").c, p.type("limit").ink)
            // Texte court sans pièce jointe : mis en valeur, comme une citation
            val short = post.text.length <= 90 && post.photo == null && post.poll == null
            if (post.text.isNotEmpty()) Text(post.text, fontSize = if (short) 18.sp else 15.5.sp, lineHeight = if (short) 25.sp else 22.sp, fontWeight = if (short) FontWeight.Bold else FontWeight.Medium)
            post.poll?.let { PollView(ui, post) }
            post.photo?.let { url ->
                AsyncImage(url, "Photo partagée par ${a.name}", Modifier.fillMaxWidth().heightIn(max = 440.dp).clip(RoundedCornerShape(20.dp)).clickable { photo = true }, contentScale = ContentScale.Crop)
                if (photo) PhotoViewer(url) { photo = false }
            }
            post.goalId?.let { GoalAttach(ui, pg.goalById(it)) }
            val mine = if (liked) post.reactions[u.id] ?: "fire" else null
            val react = { k: String ->
                if (!pg.socialBlocked("d'encourager")) {
                    if (mine == null && !p.motion) ui.sparks(1)
                    picker = false
                    ui.run { ui.cloud.react(post, k) }
                }
            }
            // Qui a réagi : pile d'emojis et total, ouvre la liste
            val sum = reactionSummary(post)
            val nc = post.comments.count { !pg.isBlockedPair(it.userId, u.id) }
            if (sum.isNotEmpty() || nc > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                if (sum.isNotEmpty()) Row(Modifier.clip(CircleShape).clickable { ui.open(Sheet.Likers(post.id)) }.padding(end = 6.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    sum.take(3).forEachIndexed { i, (e, _) ->
                        Box(Modifier.offset(x = (-6 * i).dp).size(24.dp).clip(CircleShape).background(p.glassStrong).border(1.5.dp, p.bg, CircleShape), contentAlignment = Alignment.Center) { Text(e, fontSize = 12.sp) }
                    }
                    Text(pl(post.likes.size, "réaction", "réactions"), Modifier.offset(x = (-6 * (sum.size.coerceAtMost(3) - 1)).dp).padding(start = 6.dp), fontSize = 13.sp, color = p.ink2, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                if (nc > 0 && !inPage) Text(pl(nc, "commentaire", "commentaires"), Modifier.clip(RoundedCornerShape(10.dp)).clickable { ui.push(Screen.Comments(post.id)) }.padding(4.dp), fontSize = 13.sp, color = p.ink2, fontWeight = FontWeight.Bold)
            }
            // Barre d'actions : encourager (réaction choisie), autres réactions, commenter
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line.copy(alpha = .6f)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CheerButton(liked, post.likes.size, reactionEmoji(mine).takeIf { mine != null && mine != "fire" }, Modifier.weight(1f)) { react(mine ?: "fire") }
                ActionPill("smile", if (picker) "Fermer" else "Réagir", picker, Modifier.weight(1f)) { picker = !picker }
                if (!pend && !inPage) ActionPill("comment", "Commenter", false, Modifier.weight(1f)) { ui.push(Screen.Comments(post.id)) }
            }
            if (picker) Row(Modifier.fillMaxWidth().clip(CircleShape).background(p.glassStrong).border(1.dp, p.line, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                val cur = if (post.likes.contains(u.id)) post.reactions[u.id] ?: "fire" else null
                REACTIONS.forEach { (k, e) ->
                    val sel = cur == k
                    val sc by androidx.compose.animation.core.animateFloatAsState(if (sel) 1.25f else 1f, label = "reac")
                    Box(Modifier.size(46.dp).scale(sc).clip(CircleShape).background(if (sel) p.flame.copy(alpha = .2f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { react(k) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 24.sp) }
                }
            }
            // Réponses rapides sous la réussite d'un ami : encouragement et commentaire en un geste
            if (!pend && post.userId != u.id && (post.goalId != null || post.kind != "manual") && pg.canComment(u, post) && post.comments.none { it.userId == u.id } && pg.store.online)
                FlowRowCompat(gap = 6.dp) {
                    QUICK_CHEERS.forEach { q ->
                        FChip(q, false, {
                            if (pg.socialBlocked("d'encourager")) return@FChip
                            ui.run("Encouragement envoyé à ${first(a.name)}.", "flame") { if (!post.likes.contains(u.id)) ui.cloud.toggleLike(post); ui.cloud.comment(post, q) }
                        })
                    }
                }
            // Aperçu : les deux derniers commentaires
            val shown = if (inPage) emptyList() else post.comments.filter { !pg.isBlockedPair(it.userId, u.id) }
            shown.takeLast(2).forEach { c -> CommentBubble(ui, c.userId, c.text, c.at, compact = true) }
            if (shown.size > 2) Text("Voir les ${shown.size} commentaires", Modifier.clip(RoundedCornerShape(10.dp)).clickable { ui.push(Screen.Comments(post.id)) }.padding(4.dp), color = p.ink2, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            if (post.commentsLocked && !inPage) Muted("🔒 Les commentaires sont fermés sur cette publication.", small = true)
        }
    }
}

/** Bouton de la barre d'actions d'une publication : icône et libellé, pleine largeur de sa part. */
@Composable
private fun ActionPill(icon: String, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(modifier.bounce(inter, .94f).height(40.dp).clip(CircleShape).background(if (on) p.ink.copy(alpha = .1f) else p.ink.copy(alpha = .05f))
        .springClick(inter, label = label, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        Pi(icon, size = 17.dp, tint = p.ink2)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink2, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

fun modDeletePost(ui: AppState, post: Post) {
    ui.confirm("Supprimer cette publication ?", "« ${post.text.take(80)}${if (post.text.length > 80) "…" else ""} » sera supprimée pour tout le monde. L'auteur sera prévenu.", "Supprimer") {
        ui.run("Publication supprimée.", "trash") { ui.cloud.modPost(post.id, "delete") }
    }
}

@Composable
fun CheerButton(on: Boolean, n: Int, emoji: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    // Rebond uniquement quand on encourage (pas à l'affichage d'une publication déjà encouragée)
    val pop = remember { Animatable(1f) }
    var was by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on && !was && !p.motion) { pop.snapTo(.75f); pop.animateTo(1f, spring(dampingRatio = .35f, stiffness = 500f)) } else pop.snapTo(1f)
        was = on
    }
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(modifier.bounce(inter, .9f).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.height(40.dp).clip(CircleShape)
        .background(if (on) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(p.flame.copy(alpha = .3f), p.pink.copy(alpha = .22f))) else androidx.compose.ui.graphics.SolidColor(p.ink.copy(alpha = .05f)))
        .border(1.dp, if (on) p.flame.copy(alpha = .5f) else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
        .springClick(inter, label = "Encourager", onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        if (emoji != null) Text(emoji, fontSize = 16.sp) else Flame(18.dp, dim = !on)
        Text(if (n > 0) "$n" else "Bravo", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = if (on) p.ink else p.ink2, maxLines = 1)
    }
}

@Composable
fun PollView(ui: AppState, post: Post) {
    ui.rev
    val p = LocalPal.current; val pg = ui.pg; val poll = post.poll ?: return
    val closed = nowMs() >= poll.endsAt; val voted = poll.options.any { it.mine }; val showRes = closed || voted || post.userId == pg.meId()
    val top = poll.options.maxOfOrNull { it.n } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        poll.options.forEach { o ->
            PollOption(o.t, if (poll.voters > 0) o.n.toFloat() / poll.voters else 0f, showRes, o.mine, o.n == top && o.n > 0, !closed,
                { if (!pg.socialBlocked("de voter")) ui.run { ui.cloud.vote(post, o.id) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pi("poll", size = 14.dp, tint = p.ink2); Muted("${pl(poll.voters, "votant", "votants")} · ${if (poll.multi) "plusieurs réponses" else "une réponse"} · ${if (closed) "sondage terminé" else "se termine ${fmtLeft(poll.endsAt - nowMs())}"}${if (!closed && !voted && post.userId != pg.meId()) " · vote pour voir les résultats" else ""}", small = true) }
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

/** Un commentaire : avatar, nom, texte et date, dans une bulle de verre. */
@Composable
fun CommentBubble(ui: AppState, userId: String, text: String, at: Long, compact: Boolean = false) {
    val p = LocalPal.current; val pg = ui.pg; val cu = pg.userById(userId)
    Row(Modifier.fillMaxWidth().then(if (compact) Modifier else Modifier.enter()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Av(cu, if (compact) 28.dp else 36.dp, modifier = Modifier.clickable { cu?.let { ui.open(Sheet.User(it.id)) } })
        Column(Modifier.weight(1f, fill = false).clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).background(if (compact) p.ink.copy(alpha = .06f) else p.glassStrong)
            .then(if (compact) Modifier else Modifier.border(1.dp, p.glassEdge, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (cu?.id == pg.meId()) "Toi" else cu?.name ?: "Membre", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Muted(ago(at), small = true, maxLines = 1)
            }
            Text(text, fontSize = if (compact) 14.sp else 15.sp)
        }
    }
}

/** Page dédiée aux commentaires d'une publication : la publication, tous les commentaires et la saisie en bas. */
@Composable
fun CommentsScreen(ui: AppState, postId: String) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val post = pg.db.posts.firstOrNull { it.id == postId }
    if (post == null) { LaunchedEffect(Unit) { ui.back() }; return }
    val list = post.comments.filter { !pg.isBlockedPair(it.userId, u.id) }.sortedBy { it.at }
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(list.size) { if (list.isNotEmpty()) state.animateScrollToItem(list.size + 1) }
    var comment by remember { mutableStateOf("") }
    val canWrite = !post.commentsLocked && pg.canComment(u, post) && post.status != "pending"
    ScreenFrame(ui, "Commentaires", "Feed", { ui.back() }, icon = "comment", color = p.type("measure").c, color2 = p.flame2, state = state, bottom = {
        val shape = RoundedCornerShape(26.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).shadow(if (p.transp) 0.dp else 14.dp, shape, ambientColor = Color(0x4428186E), spotColor = Color(0x4428186E))
            .clip(shape).background(p.glassStrong).border(1.dp, p.glassEdge, shape).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                post.commentsLocked -> Muted("🔒 Les commentaires sont fermés sur cette publication.", Modifier.padding(8.dp), small = true)
                !canWrite -> Muted("Tu ne peux pas commenter pour le moment.", Modifier.padding(8.dp), small = true)
                else -> {
                    Field(comment, { comment = it }, "Écrire un encouragement…", Modifier.weight(1f), max = 280)
                    IconBtn("send", "Envoyer le commentaire", {
                        val t = comment.trim(); if (t.isEmpty() || pg.socialBlocked("de commenter")) return@IconBtn
                        comment = ""; ui.run { ui.cloud.comment(post, t) }
                    }, size = 46.dp, tint = p.flame)
                }
            }
        }
    }) {
        item("post") { PostCard(ui, post, inPage = true) }
        item("head") { Row(verticalAlignment = Alignment.CenterVertically) { Eyebrow(pl(list.size, "commentaire", "commentaires")) } }
        if (list.isEmpty()) item("empty") { Muted("Aucun commentaire pour l’instant. Sois le premier à encourager !", Modifier.fillMaxWidth().padding(vertical = 20.dp), center = true) }
        list.forEach { c -> item("c:${c.id}") { CommentBubble(ui, c.userId, c.text, c.at) } }
        item("end") { Spacer(Modifier.height(4.dp)) }
    }
}

/** Réponses rapides aux réussites des amis (feed et notifications). */
val QUICK_CHEERS = listOf("🔥 Bravo !", "💪 Continue comme ça", "👏 Chapeau", "🚀 Tu gères")
