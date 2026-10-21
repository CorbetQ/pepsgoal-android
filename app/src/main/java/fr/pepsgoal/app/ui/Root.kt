package fr.pepsgoal.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.peps
import fr.pepsgoal.app.ui.screens.*
import fr.pepsgoal.app.ui.sheets.SheetHost
import fr.pepsgoal.app.ui.fullscreen.FullscreenHost
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.PepsTheme
import fr.pepsgoal.app.ui.theme.ThemeInput
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

val NAV = listOf(Triple("today", "Jour", "sun"), Triple("goals", "Objectifs", "target"), Triple("feed", "Feed", "feed"), Triple("friends", "Amis", "users"), Triple("profile", "Profil", "user"))
val NAV_TITLES = mapOf("today" to "Aujourd’hui", "goals" to "Objectifs", "feed" to "Feed", "friends" to "Amis", "profile" to "Profil", "mod" to "Modération")

/** Lecture de la révision de la base : l'appelant est redessiné à chaque changement. */
@Composable
fun AppState.watch(): Long = rev

@Composable
fun PepsRoot(ui: AppState) {
    val ctx = LocalContext.current
    val rev = ui.watch(); ui.themeTick
    val pg = ui.pg; val u = pg.me(); val s = u?.settings
    val wx = Wx.cached(ctx, s?.weather)
    val input = ThemeInput(s?.theme ?: "system", s?.accent ?: 0, s?.accentCustom, s?.glassBlur ?: "mid", ui.prefs.materialYou,
        ui.prefs.contrast, ui.prefs.cvd, ui.prefs.mono, ui.prefs.motion, ui.prefs.transp, Weather.skyDark(wx?.data))
    LaunchedEffect(s?.weather?.lat, s?.weather?.lon) { if (s?.weather != null) Wx.ensure(ctx, ui, false) }
    PepsTheme(input) {
        val p = LocalPal.current
        Box(Modifier.fillMaxSize().then(if (p.mono) Modifier.grayscale() else Modifier)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) { val e = awaitPointerEvent(PointerEventPass.Initial); e.changes.firstOrNull()?.let { ui.touch = it.position } } } }) {
            Backdrop(if (s?.theme == "weather") "sky" else s?.bg ?: "blob", if (s?.theme == "weather") Weather.sky(wx?.data) else null, pg.eggWord() == "Cold", ui.prefs.bgStamp)
            CompositionLocalProvider(LocalContentColor provides p.ink) {
                when {
                    u == null -> AuthScreen(ui)
                    pg.maintNow()?.mode == "full" && !pg.isAdmin(u) -> MaintenanceScreen(ui)
                    else -> Shell(ui)
                }
                val top = ui.screens.lastOrNull()
                AnimatedContent(top, transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (fadeOut()) }, label = "screen") { sc ->
                    when (sc) {
                        is Screen.Settings -> SettingsScreen(ui, sc.cat)
                        is Screen.Chat -> ChatScreen(ui, sc.with, sc.group)
                        null -> {}
                    }
                }
                SheetHost(ui)
                FullscreenHost(ui)
                Toasts(ui)
                Sparks(ui)
            }
        }
    }
    rev.hashCode()
}

private fun Modifier.grayscale() = this.drawWithContent {
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    drawIntoCanvas { c -> c.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), paint); drawContent(); c.restore() }
}

@Composable
private fun Shell(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me()!!
    val social = pg.socialOn(u)
    val nav = (if (social) NAV else NAV.filter { it.first != "feed" && it.first != "friends" }) + if (pg.isAdmin(u)) listOf(Triple("mod", "Modo", "shield")) else emptyList()
    if (nav.none { it.first == ui.tab }) ui.tab = "today"
    val badges = mapOf("friends" to pg.db.friendships.count { it.to == u.id && it.status == "pending" } + Social.unreadMsgs(ui), "mod" to if (pg.isAdmin(u)) Mod.count(ui) else 0)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { TopBar(ui) },
        bottomBar = {
            NavigationBar(containerColor = p.glassStrong, tonalElevation = 0.dp) {
                nav.forEach { (k, l, ic) ->
                    NavigationBarItem(selected = ui.tab == k, onClick = { ui.nav(k) }, icon = {
                        BadgedBox(badge = { badges[k]?.takeIf { it > 0 }?.let { Badge { Text("$it") } } }) { Pi(ic, size = 22.dp) }
                    }, label = { Text(l, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = p.flame.copy(alpha = if (p.materialYou) .35f else .28f), selectedIconColor = p.ink, selectedTextColor = p.ink, unselectedIconColor = p.ink2, unselectedTextColor = p.ink2))
                }
            }
        },
        floatingActionButton = {
            if (ui.tab in setOf("today", "goals")) Fab("Nouvel objectif") { ui.open(Sheet.GoalForm()) }
            else if (ui.tab == "feed" && !pg.isBanned(u)) Fab("Nouvelle publication") { if (!pg.socialBlocked("de publier")) ui.open(Sheet.Compose) }
        }
    ) { pad ->
        AnimatedContent(ui.tab, Modifier.padding(pad), transitionSpec = { if (p.motion) EnterTransition.None togetherWith ExitTransition.None else (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 24 }) togetherWith fadeOut(tween(120)) }, label = "tab") { tab ->
            when (tab) {
                "goals" -> GoalsScreen(ui)
                "feed" -> FeedScreen(ui)
                "friends" -> FriendsScreen(ui)
                "profile" -> ProfileScreen(ui)
                "mod" -> ModerationScreen(ui)
                else -> TodayScreen(ui)
            }
        }
    }
}

@Composable
private fun Fab(desc: String, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(p.accent).clickable(onClickLabel = desc, onClick = onClick), contentAlignment = Alignment.Center) { Pi("plus", size = 28.dp, tint = p.onAccent) }
}

@Composable
private fun TopBar(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me()!!; val st = pg.globalStreak(u.id)
    Column(Modifier.statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { Logo(pg.eggWord(), 19) }
            if (!ui.pg.store.online) Pi("cloudoff", size = 20.dp, tint = p.ink2)
            val bump = remember { Animatable(1f) }
            LaunchedEffect(st.n) { if (!p.motion) { bump.snapTo(1.35f); bump.animateTo(1f, spring(dampingRatio = .4f)) } }
            Row(Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value }, verticalAlignment = Alignment.CenterVertically) { Flame(22.dp); Text("${st.n}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
            Row(Modifier.clickable { ui.nav("profile") }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) { Pi("snow", size = 18.dp, tint = p.type("objective").ink); Text("${u.freezes}", fontWeight = FontWeight.ExtraBold) }
            IconBtn("bell", "Notifications", { ui.open(Sheet.Notifs); Social.readNotifs(ui) }, badge = pg.unread())
        }
        if (!ui.pg.store.online) Note(if (ui.cloud.forceOffline && pg.store.acct) "**Mode hors ligne activé.** Tes objectifs restent disponibles ; tes modifications partiront quand tu repasseras en ligne." else "**Hors ligne.** Tes objectifs restent disponibles sur cet appareil ; les fonctions sociales reviennent avec la connexion.", "cloudoff", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (pg.db.sandbox) Note("**Mode démonstration.** Données d’exemple : rien n’est enregistré. Déconnecte-toi pour revenir à ton profil.", "star", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        val now = pg.maintNow(); val next = pg.maintNext()
        if (now != null) Note("**Maintenance en cours**${now.until?.let { " jusqu'au ${fmtDT(it)}" } ?: ""} · ${MAINT_MODE[now.mode]}${if (now.message.isNotEmpty()) ". ${now.message}" else ""}${if (pg.isAdmin(u)) " (en tant qu’admin, tu n’es pas bloqué)" else ""}", "gauge", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        else if (next != null && next.start - nowMs() < 7 * DAY_MS) Note("Maintenance prévue le ${fmtDT(next.start)}${next.until?.let { " jusqu'au ${fmtDT(it)}" } ?: ""} · ${MAINT_MODE[next.mode]}.", "clock", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
}

@Composable
private fun Toasts(ui: AppState) {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 92.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            for (t in ui.toasts) key(t.id) {
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                AnimatedVisibility(shown, enter = fadeIn() + slideInVertically { it / 2 }) {
                    Row(Modifier.clip(RoundedCornerShape(18.dp)).background(p.glassStrong).border(1.dp, p.line, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (t.icon == "flame") Flame(22.dp) else Pi(t.icon, size = 20.dp, tint = p.ink)
                        Text(t.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = p.ink)
                    }
                }
            }
        }
    }
}

/** Étincelles et « +N » à l'endroit touché après une validation. */
@Composable
private fun Sparks(ui: AppState) {
    val p = LocalPal.current
    val sp = ui.sparks ?: return
    val prog = remember(sp) { Animatable(0f) }
    LaunchedEffect(sp) { prog.animateTo(1f, tween(800, easing = FastOutSlowInEasing)); if (ui.sparks === sp) ui.sparks = null }
    val parts = remember(sp) { List(16) { Triple(Random.nextFloat() * 6.283f, 40 + Random.nextFloat() * 70, it) } }
    val cols = listOf(p.flame, p.flame2, p.pink, p.type("objective").c, p.type("routine").c)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val k = prog.value
            for ((a, d, i) in parts) drawCircle(cols[i % cols.size].copy(alpha = 1 - k), 5.dp.toPx() * (1 - k * .5f), Offset(sp.first.x + cos(a) * d * k * density, sp.first.y + (sin(a) * d - 30) * k * density))
        }
        Text("+${sp.second}", Modifier.offset { IntOffset(sp.first.x.roundToInt() - 20, (sp.first.y - 40 - prog.value * 60).roundToInt()) }.graphicsLayer { alpha = 1 - prog.value },
            fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = p.flame)
    }
}
