package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.*
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
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Muted("${lv.pts} flammes${lv.next?.let { " · encore ${it - lv.pts} pour « ${lv.nextName} »" } ?: ""}", Modifier.weight(1f), small = true)
                    // Boutique : icônes et thèmes avec les flammes à dépenser
                    PButton("${pg.wallet(u)} 🔥", { ui.push(Screen.Shop) }, icon = "star", small = true, kind = "ghost")
                }
            }
        }
        item("stats") {
            StatGrid(listOf("${st.n}" to "Série actuelle", "${pg.bestStreak(u.id)}" to "Meilleure série", "${pg.userCheckins(u.id).count { pg.isWin(it) }}" to "Validations", "$done" to "Caps atteints"))
        }
        item("recap") { RecapCard(ui) }
        item("gel") { GelCard(ui, u, false) }
        item("stories") { StoryArchiveCard(ui, u) }
        item("rewards") { fr.pepsgoal.app.ui.sheets.RewardsCard(ui) }
        item("flames") { FlameSkinsCard(ui, u) }
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
fun BadgeMedal(b: BadgeState, size: Int) = Medal(b.f.e, maxOf(0, b.tier), b.tier < 0, size, b.f.k)

/** Couleurs d'un métal de palier (clair, base, foncé, très foncé) ; gris acier pour un palier verrouillé. */
private fun metal(tier: Int, locked: Boolean): List<Color> {
    val base = if (locked) hex("#8E94A6") else hex(BADGE_TIERS[tier.coerceIn(0, BADGE_TIERS.lastIndex)].second)
    return listOf(androidx.compose.ui.graphics.lerp(base, Color.White, .62f), base, androidx.compose.ui.graphics.lerp(base, Color.Black, .45f), androidx.compose.ui.graphics.lerp(base, Color.Black, .68f))
}

/**
 * Médaille en métal : anneau biseauté (dégradé circulaire), disque bombé, reflet qui balaie la médaille de temps en
 * temps, et l'emoji gravé dans le métal (monochrome, teinte du métal, liseré clair décalé comme une gravure).
 */
@Composable
fun Medal(e: String, tier: Int, locked: Boolean, size: Int, seed: String = e) {
    val p = LocalPal.current
    val (light, base, dark, deep) = metal(tier, locked)
    // Reflet qui passe de temps en temps, à 20 images par seconde (les grilles de médailles en comptent beaucoup)
    val delay = 1200 + (seed.hashCode() and 0x7FF)
    val ph = if (locked) 0f else slowPhase(1500 + delay, 20)
    val tt = ph * (1500 + delay)
    val shine = if (tt < 1500) -.6f + 2.2f * FastOutSlowInEasing.transform(tt / 1500f) else 1.6f
    // Gravure : luminosité de l'emoji convertie en nuances du métal (du très foncé à la base)
    val engrave = remember(tier, locked) {
        fun row(lo: Float, hi: Float, i: Int) = floatArrayOf(.299f * (hi - lo), .587f * (hi - lo), .114f * (hi - lo), 0f, lo * 255f)
        val lo = deep; val hi = base
        ColorFilter.colorMatrix(ColorMatrix(row(lo.red, hi.red, 0) + row(lo.green, hi.green, 1) + row(lo.blue, hi.blue, 2) + floatArrayOf(0f, 0f, 0f, 1f, 0f)))
    }
    val edge = remember(tier, locked) { ColorFilter.tint(light, BlendMode.SrcIn) }
    Box(Modifier.size(size.dp).shadow(if (p.transp) 0.dp else (size / 9).dp, CircleShape, ambientColor = dark, spotColor = dark).alpha(if (locked) .7f else 1f), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2; val c = this.center
            // Anneau : reflets métalliques tout autour
            drawCircle(Brush.sweepGradient(listOf(light, base, dark, base, light, base, dark, base, light), c), r)
            drawCircle(dark.copy(alpha = .55f), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(r * .03f))
            // Gorge entre l'anneau et le disque, puis disque bombé éclairé en haut à gauche
            val ri = r * .78f
            drawCircle(Brush.linearGradient(listOf(deep, light), Offset(c.x - ri, c.y - ri), Offset(c.x + ri, c.y + ri)), ri + r * .05f, c)
            drawCircle(Brush.radialGradient(listOf(light, base, dark), Offset(c.x - ri * .35f, c.y - ri * .45f), ri * 1.55f), ri, c)
            // Reflet qui balaie la médaille
            if (!locked && !p.motion && shine in -.4f..1.4f) {
                val x = shine * this.size.width
                clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }) {
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .65f), Color.Transparent), Offset(x - r * .45f, 0f), Offset(x + r * .45f, r * .9f)))
                }
            }
            // Brillance fixe en haut du disque
            drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = .4f), Color.Transparent), c.y - ri, c.y - ri * .1f), Offset(c.x - ri * .7f, c.y - ri * .9f), Size(ri * 1.4f, ri * .8f))
        }
        val es = (size * .44f).dp; val off = (size / 70f).coerceAtLeast(1f).dp
        Box(contentAlignment = Alignment.Center) {
            Emoji(e, es, Modifier.offset(off, off).alpha(.7f), colorFilter = edge)
            Emoji(e, es, colorFilter = engrave)
        }
    }
}

@Composable
fun BadgesCard(ui: AppState, u: User) {
    val p = LocalPal.current
    val st = remember(ui.rev) { Badges.state(ui.pg, u.id) }
    Glass {
        CardHead("Badges") { Count("${Badges.keys(st).size} / ${Badges.total()}") }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            st.forEach { b ->
                val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Row(Modifier.fillMaxWidth().bounce(inter, .97f).clip(RoundedCornerShape(16.dp)).springClick(inter, label = "Voir le badge ${b.f.n}") { ui.push(Screen.Badge(b.f.k)) }.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
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

/** Page d'un badge : grande médaille, progression vers le palier suivant et toutes les étapes, de Bronze à Diamant. */
@Composable
fun BadgeScreen(ui: AppState, key: String) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val b = remember(ui.rev) { Badges.state(pg, u.id) }.firstOrNull { it.f.k == key }
    if (b == null) { LaunchedEffect(Unit) { ui.back() }; return }
    val (light, base) = metal(maxOf(0, b.tier), b.tier < 0)
    val pop = remember { Animatable(.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = .45f, stiffness = 260f)) }
    ScreenFrame(ui, b.f.n, "Profil", { ui.back() }, color = base, color2 = light) {
        item("hero") {
            Glass {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(vertical = 10.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) { BadgeMedal(b, 168) }
                    Text(if (b.tier >= 0) "Badge ${BADGE_TIERS[b.tier].first}" else "À débloquer", fontFamily = fr.pepsgoal.app.ui.theme.Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Muted(if (b.tier >= 0) b.f.d(b.f.t[b.tier]) else "Premier palier : ${b.f.d(b.f.t.first())}", center = true)
                    if (b.next != null) {
                        Spacer(Modifier.height(4.dp))
                        Bar(b.pct * 100, height = 10.dp)
                        Row(Modifier.fillMaxWidth()) {
                            Muted("${fmtNum(b.v)} / ${fmtNum(b.next!!)}", Modifier.weight(1f), small = true)
                            Muted("Prochain : ${BADGE_TIERS[b.tier + 1].first}", small = true)
                        }
                    } else Chip("Palier maximal atteint", "check", p.ok)
                }
            }
        }
        item("steps") {
            Glass {
                CardHead("Étapes") { Count("${b.tier + 1} / ${b.f.t.size}") }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    b.f.t.forEachIndexed { i, th ->
                        val got = i <= b.tier; val cur = i == b.tier + 1
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Medal(b.f.e, i, !got, 50, b.f.k + i)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(BADGE_TIERS[i].first, fontWeight = FontWeight.ExtraBold, color = if (got || cur) p.ink else p.ink2)
                                Muted(b.f.d(th), small = true)
                                if (cur) Bar(((b.v.toDouble() / th).coerceIn(0.0, 1.0)) * 100, height = 6.dp)
                            }
                            when {
                                got -> Chip("Obtenu", "check", p.ok)
                                cur -> Text("${fmtNum(b.v)} / ${fmtNum(th)}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                else -> Pi("lock", size = 18.dp, tint = p.ink3)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Flammes de collection : débloquées par la meilleure série, choisies d'un geste. */
@Composable
private fun FlameSkinsCard(ui: AppState, u: fr.pepsgoal.core.model.User) {
    val p = LocalPal.current; val pg = ui.pg
    val best = remember(ui.rev) { pg.bestStreak(u.id) }
    Glass {
        CardHead("Flammes de collection")
        Muted("Ta meilleure série débloque de nouvelles couleurs de flamme : $best ${if (best > 1) "jours" else "jour"} pour l’instant.", small = true)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FLAME_SKINS.forEach { sk ->
                val open = best >= sk.min; val on = u.flameSkin == sk.key || (sk.key == "classic" && FLAME_SKINS.none { it.key == u.flameSkin })
                Column(Modifier.width(84.dp).clip(RoundedCornerShape(18.dp)).background(if (on) p.flame.copy(alpha = .16f) else p.ink.copy(alpha = .04f))
                    .border(if (on) 2.dp else 1.dp, if (on) p.flame else p.line, RoundedCornerShape(18.dp))
                    .clickable(enabled = open) { u.flameSkin = sk.key; pg.store.commit(); ui.toast("Flamme « ${sk.name} » choisie.", "flame") }
                    .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Flame(40.dp, dim = !open, colors = sk.colors?.map(::hex) ?: listOf(p.pink, p.flame, p.flame2))
                        if (!open) Pi("lock", size = 16.dp, tint = p.ink2)
                    }
                    Text(sk.name, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (open) (if (on) "Choisie" else "Débloquée") else "${sk.min} j", fontSize = 11.sp, color = p.ink2)
                }
            }
        }
    }
}
