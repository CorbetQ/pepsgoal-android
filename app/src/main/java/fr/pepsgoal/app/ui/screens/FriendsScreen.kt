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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val incoming = remember(ui.rev) { pg.db.friendships.filter { it.to == u.id && it.status == "pending" } }
    val outgoing = remember(ui.rev) { pg.db.friendships.filter { it.from == u.id && it.status == "pending" } }
    // Amis et leur série, calculés une fois par changement de la base
    val friends = remember(ui.rev, u.id) { pg.friendsOf(u.id).mapNotNull { pg.userById(it) }.map { it to pg.globalStreak(it.id) }.sortedByDescending { it.second.n } }
    val unread = Social.unreadMsgs(ui)
    val searching = ui.friendQuery.isNotBlank()
    ScreenList {
        item("search") { Field(ui.friendQuery, { ui.friendQuery = it }, "", placeholder = "Rechercher un nom ou un @pseudo", max = 40, icon = "search") }
        if (searching) { item("results") { SearchCard(ui, u) }; return@ScreenList }
        if (incoming.isNotEmpty()) item("in") { RequestsCard(ui, incoming) }
        item("tabs") {
            Seg(listOf("friends" to (if (friends.isEmpty()) "Amis" else "Amis · ${friends.size}"), "msgs" to (if (unread > 0) "Messages · $unread" else "Messages"), "discover" to "Découvrir"),
                ui.friendsTab, { ui.friendsTab = it }, Modifier.fillMaxWidth(), icons = listOf("users", "comment", "compass"))
        }
        when (ui.friendsTab) {
            "msgs" -> item("msgs") { MessagesCard(ui, u, always = true) }
            "discover" -> {
                item("invite") { InviteCard(ui, u) }
                item("sugg") { SuggestCard(ui, u) }
                if (outgoing.isNotEmpty()) item("out") {
                    Glass {
                        CardHead("Demandes envoyées") { Count(outgoing.size) }
                        outgoing.forEach { f -> pg.userById(f.to)?.let { x -> PersonRow(ui, x) { PButton("Annuler", { ui.run { ui.cloud.declineRequest(f.id) } }, kind = "ghost", small = true) } } }
                    }
                }
            }
            else -> {
                if (friends.isEmpty()) item("empty") {
                    Glass { Empty("Pas encore d’amis", "Ajoute des amis pour suivre vos séries ensemble, vous encourager et vous défier.", icon = "users") { PButton("Trouver des amis", { ui.friendsTab = "discover" }, icon = "userplus", kind = "primary", small = true) } }
                } else {
                    item("today") { FriendsTodayCard(ui, u, friends) }
                    if (friends.size >= 2) item("podium") { PodiumCard(ui, u, friends) }
                    item("league") { LeagueCard(ui) }
                    item("list") {
                        Glass(pad = 12.dp) {
                            CardHead("Mes amis", Modifier.padding(start = 6.dp, top = 4.dp)) { Count(friends.size) }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { friends.forEach { (x, st) -> FriendRow(ui, x, st) } }
                        }
                    }
                }
            }
        }
    }
}

/** Demandes d'amis reçues : cartes avec accepter / refuser. */
@Composable
private fun RequestsCard(ui: AppState, incoming: List<fr.pepsgoal.core.model.Friendship>) {
    val p = LocalPal.current; val pg = ui.pg
    Glass(border = p.flame.copy(alpha = .45f)) {
        CardHead("Demandes reçues") { Count(incoming.size) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            incoming.forEach { f -> pg.userById(f.from)?.let { x ->
                Column(Modifier.width(148.dp).clip(RoundedCornerShape(20.dp)).background(p.glassStrong).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clip(CircleShape).clickable { ui.open(Sheet.User(x.id)) }) { Av(x, 56.dp) }
                    Text(x.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Muted("@${x.username}", small = true, maxLines = 1)
                    PButton("Accepter", { ui.run("Vous êtes maintenant amis.", "users") { ui.cloud.acceptRequest(f.id) } }, Modifier.fillMaxWidth(), kind = "primary", small = true)
                    PButton("Refuser", { ui.run { ui.cloud.declineRequest(f.id) } }, Modifier.fillMaxWidth(), small = true, kind = "ghost")
                }
            } }
        }
    }
}

/** Qui a validé aujourd'hui : avatars cerclés de flamme (ou de gris), toi en premier. */
@Composable
private fun FriendsTodayCard(ui: AppState, u: User, friends: List<Pair<User, Streak>>) {
    val p = LocalPal.current; val pg = ui.pg
    val me = remember(ui.rev) { pg.globalStreak(u.id) }
    val all = listOf(u to me) + friends.sortedByDescending { it.second.lit }
    val lit = friends.count { it.second.lit }
    Glass {
        CardHead("Aujourd’hui") { Count("$lit / ${friends.size}") }
        Muted(when { lit == 0 -> "Personne n’a encore validé : montre l’exemple !"; lit == friends.size -> "Tous tes amis ont validé aujourd’hui 🔥"; else -> "${pl(lit, "ami a", "amis ont")} déjà validé aujourd’hui." }, small = true)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            all.forEach { (x, st) ->
                Column(Modifier.width(64.dp).clip(RoundedCornerShape(16.dp)).clickable { if (x.id == u.id) ui.nav("profile") else ui.open(Sheet.User(x.id)) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(62.dp).clip(CircleShape).background(if (st.lit) Brush.sweepGradient(listOf(p.flame2, p.flame, p.pink, p.flame2)) else Brush.linearGradient(listOf(p.line, p.line))).padding(3.dp)
                        .clip(CircleShape).background(p.bg).padding(2.dp), contentAlignment = Alignment.Center) {
                        Av(x, 52.dp, modifier = if (st.lit) Modifier else Modifier.alpha(.55f))
                    }
                    Text(if (x.id == u.id) "Toi" else first(x.name), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) { Flame(12.dp, dim = !st.lit); Text("${st.n}", fontSize = 11.sp, color = p.ink2, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}

/** Podium des séries entre amis (toi compris). */
@Composable
private fun PodiumCard(ui: AppState, u: User, friends: List<Pair<User, Streak>>) {
    val p = LocalPal.current
    val me = remember(ui.rev) { ui.pg.globalStreak(u.id) }
    val top = (friends + (u to me)).sortedByDescending { it.second.n }.take(3)
    if (top.size < 2) return
    val order = if (top.size == 3) listOf(1, 0, 2) else listOf(1, 0)
    val heights = listOf(84.dp, 62.dp, 48.dp)
    val medals = listOf("🥇", "🥈", "🥉")
    Glass {
        CardHead("Podium des séries") { Muted("jours d’affilée", small = true) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            order.forEach { i ->
                val (x, st) = top[i]
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { if (x.id == u.id) ui.nav("profile") else ui.open(Sheet.User(x.id)) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Av(x, if (i == 0) 58.dp else 48.dp)
                    Text(if (x.id == u.id) "Toi" else first(x.name), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.fillMaxWidth().height(heights[i]).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                        .background(Brush.verticalGradient(if (i == 0) listOf(p.flame, p.pink) else listOf(p.flame.copy(alpha = .45f - i * .1f), p.pink.copy(alpha = .3f - i * .08f)))), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(medals[i], fontSize = 18.sp)
                            Text("${st.n}", fontFamily = fr.pepsgoal.app.ui.theme.Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = if (i == 0) Color.White else p.ink)
                        }
                    }
                }
            }
        }
    }
}

/** Ligne d'un ami : avatar avec pastille de série, état du jour et bouton message. */
@Composable
private fun FriendRow(ui: AppState, x: User, st: Streak) {
    val p = LocalPal.current; val pg = ui.pg
    val n = Social.unreadFrom(ui, x.id)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { ui.open(Sheet.User(x.id)) }.padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box {
            Av(x, 50.dp)
            Row(Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp).clip(CircleShape).background(p.bg).padding(horizontal = 5.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Flame(12.dp, dim = !st.lit); Text("${st.n}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(x.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (pg.isOfficial(x)) Verified()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (st.lit) p.ok else p.ink3.copy(alpha = .5f)))
                Text(if (st.lit) "A validé aujourd’hui" else "Pas encore validé", fontSize = 13.sp, color = if (st.lit) p.ok else p.ink2, maxLines = 1)
            }
        }
        IconBtn("comment", "Écrire à ${x.name}", { ui.push(Screen.Chat(with = x.id)) }, badge = n, size = 40.dp)
        IconBtn("x", "Retirer ${x.name}", { ui.confirm("Retirer ${first(x.name)} de tes amis ?", "Vous ne verrez plus vos objectifs réservés aux amis. Tu pourras l'ajouter à nouveau plus tard.", "Retirer") { ui.close(); ui.run { ui.cloud.unfriend(x.id) } } }, plain = true, size = 32.dp)
    }
}

/** Inviter des amis : partage d'un message avec ton pseudo. */
@Composable
private fun InviteCard(ui: AppState, u: User) {
    val p = LocalPal.current; val pg = ui.pg; val ctx = androidx.compose.ui.platform.LocalContext.current
    if (pg.isGuest(u)) return
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile("userplus", p.flame, p.pink, 44.dp, 14.dp)
            Column(Modifier.weight(1f)) {
                Text("Invite tes proches", fontWeight = FontWeight.ExtraBold)
                Muted("À plusieurs, les séries tiennent plus longtemps.", small = true)
            }
        }
        PButton("Partager une invitation", {
            val text = tr("Rejoins-moi sur PepsGoal pour tenir nos objectifs ensemble 🔥") + "\n" + tr("Mon pseudo") + " : @${u.username}\nhttps://github.com/CorbetQ/pepsgoal-android/releases/latest"
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
            try { ctx.startActivity(android.content.Intent.createChooser(i, tr("Inviter des amis")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
        }, Modifier.padding(top = 12.dp).fillMaxWidth(), icon = "share", kind = "primary", small = true)
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
private fun MessagesCard(ui: AppState, u: User, always: Boolean = false) {
    val p = LocalPal.current; val pg = ui.pg
    class Row0(val dm: User?, val grp: fr.pepsgoal.core.model.Chat?, val last: Message?, val n: Int)
    val dms = pg.db.messages.filter { it.chat == null && (it.from == u.id || it.to == u.id) }.map { if (it.from == u.id) it.to ?: "" else it.from }.distinct()
        .filter { pg.userById(it) != null && !pg.blockedBy(u.id, it) }
    val rows = (dms.map { id -> Row0(pg.userById(id), null, Social.convo(ui, u.id, id).lastOrNull(), Social.unreadFrom(ui, id)) } +
        pg.db.chats.filter { it.members.contains(u.id) }.map { g -> Row0(null, g, Social.groupConvo(ui, g.id).lastOrNull(), Social.unreadGroup(ui, g.id)) })
        .sortedWith(compareByDescending<Row0> { it.dm != null && pg.isOfficial(it.dm) }.thenByDescending { it.last?.at ?: it.grp?.at ?: 0L })
    val canGroup = pg.socialOn(u) && !pg.isGuest(u) && !pg.isBanned(u) && pg.friendsOf(u.id).isNotEmpty()
    if (rows.isEmpty() && !canGroup && !always) return
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
        CardHead("Résultats") { if (results != null || !pg.store.acct) Count(list.size.coerceAtMost(30)) }
        Err(err)
        if (list.isEmpty()) Muted(when { pg.store.acct && q.length < 2 -> "Tape au moins 2 caractères pour chercher une personne."; q.isNotEmpty() -> "Aucun résultat pour cette recherche."; else -> "Tu connais déjà tout le monde ici." }, small = true)
        list.take(30).forEach { x -> PersonRow(ui, x) { PButton("Ajouter", { ui.run("Demande envoyée à ${first(x.name)}.", "userplus") { ui.cloud.sendFriendRequest(x.id) } }, icon = "userplus", small = true) } }
    }
}
