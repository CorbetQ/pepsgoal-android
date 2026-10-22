package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.*
import fr.pepsgoal.core.model.Message
import fr.pepsgoal.core.model.User
import kotlinx.coroutines.delay

@Composable
fun FriendsScreen(ui: AppState) {
    ui.watch()
    val pg = ui.pg; val u = pg.me() ?: return
    val incoming = pg.db.friendships.filter { it.to == u.id && it.status == "pending" }
    val outgoing = pg.db.friendships.filter { it.from == u.id && it.status == "pending" }
    val friends = pg.friendsOf(u.id).mapNotNull { pg.userById(it) }.sortedByDescending { pg.globalStreak(it.id).n }
    ScreenList {
        item("msgs") { MessagesCard(ui, u) }
        if (incoming.isNotEmpty()) item("in") {
            Glass {
                CardHead("Demandes reçues") { Count(incoming.size) }
                incoming.forEach { f -> pg.userById(f.from)?.let { x -> PersonRow(ui, x) {
                    PButton("Accepter", { ui.run("Vous êtes maintenant amis.", "users") { ui.cloud.acceptRequest(f.id) } }, kind = "primary", small = true)
                    PButton("Refuser", { ui.run { ui.cloud.declineRequest(f.id) } }, small = true)
                } } }
            }
        }
        item("friends") {
            Glass {
                CardHead("Mes amis") { Count(friends.size) }
                if (friends.isEmpty()) Empty("Pas encore d'amis", "Cherche des personnes ci-dessous pour suivre vos séries ensemble.", icon = "users")
                friends.forEach { x -> PersonRow(ui, x) {
                    val n = Social.unreadFrom(ui, x.id)
                    PButton(if (n > 0) "Message · $n" else "Message", { ui.push(Screen.Chat(with = x.id)) }, icon = "comment", small = true)
                    IconBtn("x", "Retirer ${x.name}", { ui.confirm("Retirer ${first(x.name)} de tes amis ?", "Vous ne verrez plus vos objectifs réservés aux amis. Tu pourras l'ajouter à nouveau plus tard.", "Retirer") { ui.close(); ui.run { ui.cloud.unfriend(x.id) } } }, plain = true, size = 36.dp)
                } }
            }
        }
        if (outgoing.isNotEmpty()) item("out") {
            Glass {
                CardHead("Demandes envoyées")
                outgoing.forEach { f -> pg.userById(f.to)?.let { x -> PersonRow(ui, x) { PButton("Annuler", { ui.run { ui.cloud.declineRequest(f.id) } }, kind = "ghost", small = true) } } }
            }
        }
        item("sugg") { SuggestCard(ui, u) }
        item("search") { SearchCard(ui, u) }
    }
}

/** Ligne d'une personne : avatar, nom, série, et actions à droite. */
@Composable
fun PersonRow(ui: AppState, x: User, size: Int = 44, acts: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPal.current; val st = ui.pg.globalStreak(x.id).n
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { ui.open(Sheet.User(x.id)) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Av(x, size.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(x.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (ui.pg.isOfficial(x)) Verified()
                    if (x.demo) Chip("démo")
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Muted("@${x.username} · ", small = true); Flame(14.dp); Muted("$st", small = true) }
            }
        }
        acts()
    }
}

@Composable
fun Verified() { val p = LocalPal.current; Pi("check", Modifier.clip(RoundedCornerShape(50)).background(p.type("objective").c).padding(2.dp), size = 14.dp, tint = p.onAccent) }

@Composable
private fun MessagesCard(ui: AppState, u: User) {
    val p = LocalPal.current; val pg = ui.pg
    class Row0(val dm: User?, val grp: fr.pepsgoal.core.model.Chat?, val last: Message?, val n: Int)
    val dms = pg.db.messages.filter { it.chat == null && (it.from == u.id || it.to == u.id) }.map { if (it.from == u.id) it.to ?: "" else it.from }.distinct()
        .filter { pg.userById(it) != null && !pg.blockedBy(u.id, it) }
    val rows = (dms.map { id -> Row0(pg.userById(id), null, Social.convo(ui, u.id, id).lastOrNull(), Social.unreadFrom(ui, id)) } +
        pg.db.chats.filter { it.members.contains(u.id) }.map { g -> Row0(null, g, Social.groupConvo(ui, g.id).lastOrNull(), Social.unreadGroup(ui, g.id)) })
        .sortedWith(compareByDescending<Row0> { it.dm != null && pg.isOfficial(it.dm) }.thenByDescending { it.last?.at ?: it.grp?.at ?: 0L })
    val canGroup = pg.socialOn(u) && !pg.isGuest(u) && !pg.isBanned(u) && pg.friendsOf(u.id).isNotEmpty()
    if (rows.isEmpty() && !canGroup) return
    val total = Social.unreadMsgs(ui)
    val cut = { s: String -> if (s.length > 60) s.take(60) + "…" else s }
    Glass {
        if (ui.cloud.e2e.hasVault().not() && pg.store.acct && ui.cloud.e2e.keyCount() > 0)
            Note("**Messages chiffrés de bout en bout.** Protège tes clés avec ton mot de passe pour les retrouver sur tes autres appareils (Paramètres › Compte).", "lock", Modifier.padding(bottom = 10.dp))
        CardHead("Messages") {
            if (total > 0) Chip("$total non lus", color = p.type("deadline").c, ink = p.type("deadline").ink)
            if (canGroup) PButton("Nouveau groupe", { ui.open(Sheet.GroupNew) }, icon = "group", kind = "ghost", small = true)
        }
        if (rows.isEmpty()) Muted("Écris à un ami depuis la liste ci-dessous, ou crée un groupe de discussion.", small = true)
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { ui.push(if (r.dm != null) Screen.Chat(with = r.dm.id) else Screen.Chat(group = r.grp!!.id)) }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (r.dm != null) Av(r.dm, 44.dp) else GroupAv(r.grp!!.emoji, 44)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(r.dm?.name ?: r.grp!!.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (r.dm != null && pg.isOfficial(r.dm)) Verified()
                    }
                    val sub = when {
                        r.last == null -> "Groupe · ${pl(r.grp?.members?.size ?: 0, "membre", "membres")}"
                        r.dm != null -> "${if (r.last.from == u.id) "Toi : " else ""}${cut(Social.preview(ui, r.last))} · ${ago(r.last.at)}"
                        else -> "${if (r.last.from == u.id) "Toi" else first(pg.userById(r.last.from)?.name ?: "?")} : ${cut(Social.preview(ui, r.last))} · ${ago(r.last.at)}"
                    }
                    Text(sub, fontSize = 13.sp, color = if (r.n > 0) p.ink else p.ink2, fontWeight = if (r.n > 0) FontWeight.ExtraBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (r.n > 0) Badge(r.n)
            }
        }
    }
}

@Composable
fun GroupAv(emoji: String, size: Int) {
    val p = LocalPal.current
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * .32f).dp)).background(p.glassStrong), contentAlignment = Alignment.Center) { Emoji(emoji, (size * .55f).dp) }
}

@Composable
private fun SuggestCard(ui: AppState, u: User) {
    val pg = ui.pg; val p = LocalPal.current
    if (!pg.socialOn(u) || pg.isGuest(u)) return
    val hidden = u.hiddenSuggest.toSet()
    val list = ui.cloud.suggestions.mapNotNull { s -> pg.userById(s.obj("user").str("id"))?.let { it to s } }
        .filter { (x, _) -> x.id !in hidden && pg.friendship(u.id, x.id) == null && !pg.isBlockedPair(u.id, x.id) }.take(8)
    if (list.isEmpty()) return
    // Coach IA : suggestions classées et raison personnalisée selon tes objectifs (mémorisées tant que la liste ne change pas)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ai = fr.pepsgoal.app.ai.LocalAi.on(ctx, ui.prefs.aiFriends)
    val fk = list.joinToString(",") { it.first.id }
    var aiPicks by remember(fk) { mutableStateOf(if (ai) fr.pepsgoal.app.ai.CoachExtras.cachedFriends(ctx, fk) else null) }
    if (ai) LaunchedEffect(fk) {
        if (aiPicks == null) aiPicks = fr.pepsgoal.app.ai.CoachExtras.friends(ctx, fk, list.map { (x, s) ->
            Triple(x.id, x.name, buildString { append(pl(s.int("mutual"), "ami en commun", "amis en commun")); if (s.strs("via").isNotEmpty()) append(" dont " + s.strs("via").take(2).joinToString(", ") { first(it) }); if (s.int("groups") > 0) append(", même groupe") })
        })
    }
    val aiWhy = aiPicks?.associate { it.userId to it.reason } ?: emptyMap()
    val aiOrder = aiPicks?.map { it.userId } ?: emptyList()
    val shown = list.sortedBy { if (it.first.id in aiOrder) aiOrder.indexOf(it.first.id) else 99 }
    Glass(ai = ai) {
        CardHead("Suggestions d’amis") { IconBtn("gear", "Réglages des suggestions", { ui.push(Screen.Settings("privacy")) }, plain = true, size = 36.dp) }
        if (ai) Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pi("sparkle", size = 15.dp, tint = p.flame); Muted(if (aiPicks == null) "Le coach cherche les meilleurs partenaires pour toi…" else "Classées par ton coach selon tes objectifs", small = true)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.forEach { (x, s) ->
                val mutual = s.int("mutual"); val via = s.strs("via")
                val why = aiWhy[x.id] ?: if (mutual > 0) pl(mutual, "ami en commun", "amis en commun") + (if (via.isNotEmpty()) " · " + via.take(2).joinToString(", ") { first(it) } else "") else if (s.int("groups") > 0) "Dans un de tes groupes" else "Nouveau sur PepsGoal"
                Box(Modifier.width(150.dp).clip(RoundedCornerShape(20.dp)).background(p.glassStrong).padding(12.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.clickable { ui.open(Sheet.User(x.id)) }) { Av(x, 56.dp) }
                        Text(x.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Muted(why, small = true, center = true, maxLines = 2)
                        PButton("Ajouter", { ui.run("Demande envoyée à ${first(x.name)}.", "userplus") { ui.cloud.sendFriendRequest(x.id) } }, icon = "userplus", kind = "primary", small = true)
                    }
                    IconBtn("x", "Ne plus suggérer ${x.name}", { u.hiddenSuggest.add(x.id); pg.store.commit() }, Modifier.align(Alignment.TopEnd), plain = true, size = 30.dp)
                }
            }
        }
    }
}

@Composable
private fun SearchCard(ui: AppState, u: User) {
    val pg = ui.pg
    var results by remember { mutableStateOf<List<User>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val q = ui.friendQuery.trim().lowercase()
    LaunchedEffect(q, pg.store.acct) {
        err = null
        if (!pg.store.acct) { results = null; return@LaunchedEffect }
        if (q.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        results = try { ui.cloud.searchUsers(q) } catch (e: Exception) { err = e.message; emptyList() }
    }
    val list = (results ?: pg.db.users)
        .filter { x -> x.id != u.id && !x.guest && !pg.isAdmin(x) && !pg.isOfficial(x) && pg.friendship(u.id, x.id) == null && !pg.isBlockedPair(u.id, x.id) && (results != null || q.isEmpty() || x.name.lowercase().contains(q) || x.username.contains(q)) }
    Glass {
        CardHead("Trouver des amis")
        Field(ui.friendQuery, { ui.friendQuery = it }, "Rechercher", placeholder = "Nom ou @utilisateur", max = 40)
        Spacer(Modifier.height(8.dp))
        Err(err)
        if (list.isEmpty()) Muted(when { pg.store.acct && q.length < 2 -> "Tape au moins 2 caractères pour chercher une personne."; q.isNotEmpty() -> "Aucun résultat pour cette recherche."; else -> "Tu connais déjà tout le monde ici." }, small = true)
        list.take(30).forEach { x -> PersonRow(ui, x) { PButton("Ajouter", { ui.run("Demande envoyée à ${first(x.name)}.", "userplus") { ui.cloud.sendFriendRequest(x.id) } }, icon = "userplus", small = true) } }
    }
}
