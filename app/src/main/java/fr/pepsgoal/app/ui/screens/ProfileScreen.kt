package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Fs
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.str
import fr.pepsgoal.core.model.Story
import fr.pepsgoal.core.model.User

@Composable
fun ProfileScreen(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val lv = pg.level(pg.points(u.id)); val st = pg.globalStreak(u.id)
    val done = pg.db.goals.count { it.userId == u.id && it.completedAt != null }
    ScreenList {
        item("head") {
            Glass {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Pas de découpe sur le conteneur : le badge « modifier » déborde légèrement de l'anneau, comme sur le web
                    Box {
                        Box(Modifier.clip(CircleShape).clickable(onClickLabel = "Changer mon icône de profil") { ui.open(Sheet.Avatar) }) { LevelRing(lv.idx, 76.dp) { Av(u, 76.dp) } }
                        Box(Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).size(28.dp).clip(CircleShape).background(p.ink).border(2.dp, p.bg, CircleShape)
                            .clickable(onClickLabel = "Changer mon icône de profil") { ui.open(Sheet.Avatar) }, contentAlignment = Alignment.Center) { Pi("edit", size = 14.dp, tint = p.bg) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.name, Modifier.weight(1f), fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            IconBtn("gear", "Paramètres", { ui.push(Screen.Settings()) })
                        }
                        Muted("${if (pg.isGuest(u)) "Sans compte · données sur cet appareil" else "@" + u.username} · Niveau ${lv.idx}, ${lv.name}${if (pg.isAdmin(u)) " · Administrateur" else ""}", small = true)
                        if (pg.isGuest(u)) PButton("Créer un compte", { ui.push(Screen.Settings("account")) }, icon = "user", kind = "primary", small = true)
                        if (u.bio.isNotEmpty()) Text(u.bio, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Bar(lv.pct * 100)
                Muted("${lv.pts} flammes${lv.next?.let { " · encore ${it - lv.pts} pour « ${lv.nextName} »" } ?: ""}", Modifier.padding(top = 6.dp), small = true)
            }
        }
        item("stats") {
            StatGrid(listOf("${st.n}" to "Série actuelle", "${pg.bestStreak(u.id)}" to "Meilleure série", "${pg.userCheckins(u.id).count { pg.isWin(it) }}" to "Validations", "$done" to "Caps atteints"))
        }
        item("recap") { RecapCard(ui) }
        item("gel") { GelCard(ui, u, false) }
        item("stories") { StoryArchiveCard(ui, u) }
        item("badges") { BadgesCard(ui, u) }
    }
}

@Composable
private fun StoryArchiveCard(ui: AppState, u: User) {
    val pg = ui.pg; val p = LocalPal.current
    if (pg.isGuest(u) || !pg.socialOn(u)) return
    val list = pg.myStoryArchive(u.id)
    Glass(onClick = if (list.size > 3) ({ ui.push(Screen.Stories) }) else null) {
        CardHead("Mes stories") { Count(list.size) }
        if (list.isEmpty()) Muted("Après leurs 24 heures en ligne, tes stories restent ici : toi seul peux les revoir.", small = true)
        else StoryRow(ui, u, list, 0, list.take(3))
        if (list.size > 3) PButton("Voir tout l’historique (${list.size})", { ui.push(Screen.Stories) }, icon = "clock", small = true, modifier = Modifier.padding(top = 12.dp).fillMaxWidth())
    }
}

/** Une rangée de trois miniatures de même taille (les places vides gardent la grille alignée). */
@Composable
private fun StoryRow(ui: AppState, u: User, all: List<Story>, from: Int, row: List<Story>) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (k in 0 until 3) {
            val s = row.getOrNull(k)
            if (s == null) { Spacer(Modifier.weight(1f)); continue }
            val live = nowMs() - s.at < STORY_TTL
            val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Column(Modifier.weight(1f).bounce(inter, .94f).clip(RoundedCornerShape(14.dp))
                .springClick(inter) { ui.fs = Fs.Stories(listOf(Pg.StoryGroup(u, all, false, all.firstOrNull()?.at ?: 0L)), 0, from + k, archive = true) },
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box { StoryThumb(s, Modifier.fillMaxWidth().aspectRatio(.62f)); if (live) Chip("En ligne", color = p.flame, ink = p.ink, modifier = Modifier.padding(6.dp)) }
                Muted((if (live) ago(s.at) else fmtDate(ymd(s.at))) + (s.likeCount?.takeIf { it > 0 }?.let { " · $it 🔥" } ?: ""), small = true, maxLines = 1)
            }
        }
    }
}

/** Page « Historique des stories » : toutes tes stories, regroupées par mois, en grille de trois. */
@Composable
fun StoryHistoryScreen(ui: AppState) {
    ui.watch()
    val pg = ui.pg; val u = pg.me() ?: return
    val list = pg.myStoryArchive(u.id)
    val fmt = remember { java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", java.util.Locale.FRENCH) }
    val months = list.withIndex().groupBy { java.time.Instant.ofEpochMilli(it.value.at).atZone(java.time.ZoneId.systemDefault()).format(fmt).replaceFirstChar { c -> c.titlecase() } }
    val likes = list.sumOf { it.likeCount ?: 0 }
    fr.pepsgoal.app.ui.screens.ScreenFrame(ui, "Mes stories", "Profil", { ui.back() }, icon = "clock", color = LocalPal.current.pink, color2 = LocalPal.current.flame2) {
        item("sum") {
            StatGrid(listOf("${list.size}" to "stories", "$likes" to "flammes reçues", "${months.size}" to "mois"), columns = 3)
        }
        if (list.isEmpty()) item("empty") { Glass { Muted("Après leurs 24 heures en ligne, tes stories restent ici : toi seul peux les revoir.") } }
        for ((m, items) in months) item("m-$m") {
            Glass(pad = 14.dp) {
                CardHead(m) { Count(items.size) }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items.chunked(3).forEach { row -> StoryRow(ui, u, list, row.first().index, row.map { it.value }) }
                }
            }
        }
    }
}

/** Miniature d'une story : photo, récap ou carte de progression colorée par type. */
@Composable
fun StoryThumb(s: Story, modifier: Modifier) {
    val p = LocalPal.current; val shape = RoundedCornerShape(14.dp)
    val recap = s.kind == "recap"; val type = s.data.str("type").takeIf { it in TYPES } ?: "routine"
    val bg = if (recap) Brush.linearGradient(SBG[0].map(::hex)) else Brush.linearGradient(listOf(p.type(type).c, p.type(type).c.copy(alpha = .55f)))
    Box(modifier.clip(shape).background(bg), contentAlignment = Alignment.Center) {
        if (s.kind == "photo") AsyncImage(s.photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
            Emoji(if (recap) s.data.str("e") else s.data.str("icon"), 30.dp)
            Text(s.data.str("title"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun BadgeMedal(b: BadgeState, size: Int) {
    val tier = maxOf(0, b.tier); val c = hex(BADGE_TIERS[tier].second)
    Box(Modifier.size(size.dp).alpha(if (b.tier < 0) .45f else 1f).clip(CircleShape)
        .background(Brush.radialGradient(listOf(c.copy(alpha = if (b.tier < 0) .2f else .9f), c.copy(alpha = if (b.tier < 0) .08f else .45f))))
        .border(3.dp, c.copy(alpha = if (b.tier < 0) .3f else 1f), CircleShape), contentAlignment = Alignment.Center) { Emoji(b.f.e, (size * .5f).dp) }
}

@Composable
fun BadgesCard(ui: AppState, u: User) {
    val p = LocalPal.current
    val st = remember(ui.rev) { Badges.state(ui.pg, u.id) }
    Glass {
        CardHead("Badges") { Count("${Badges.keys(st).size} / ${Badges.total()}") }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            st.forEach { b ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BadgeMedal(b, 52)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(b.f.n, fontWeight = FontWeight.ExtraBold)
                            Muted((if (b.tier >= 0) BADGE_TIERS[b.tier].first else "À débloquer") + if (b.f.t.size > 1) " · ${b.tier + 1}/${b.f.t.size}" else "", small = true)
                            Spacer(Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { b.f.t.indices.forEach { i -> Box(Modifier.size(7.dp).clip(CircleShape).background(if (i <= b.tier) hex(BADGE_TIERS[i].second) else p.line)) } }
                        }
                        if (b.next != null) { Bar(b.pct * 100, height = 6.dp); Muted("${fmtNum(b.v)} / ${fmtNum(b.next!!)} · ${b.f.d(b.next!!)}", small = true) }
                        else Muted("Palier maximal atteint : ${b.f.d(b.f.t.last())}", small = true)
                    }
                }
            }
        }
    }
}
