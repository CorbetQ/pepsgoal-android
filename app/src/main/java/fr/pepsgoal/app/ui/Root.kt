package fr.pepsgoal.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import fr.pepsgoal.app.ui.Text
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Fs
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.shadow
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

val NAV = listOf(Triple("today", "Jour", "sun"), Triple("goals", "Objectifs", "target"), Triple("feed", "Feed", "feed"), Triple("friends", "Amis", "users"), Triple("profile", "Profil", "user"))
val EASY_NAV = mapOf("today" to "Aujourd’hui", "feed" to "Actus", "friends" to "Amis", "goals" to "Objectifs")
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
        ui.prefs.contrast, ui.prefs.cvd, ui.prefs.mono, ui.prefs.motion, ui.prefs.transp, Weather.skyDark(wx?.data), ui.prefs.glassOpacity,
        ui.prefs.easy, rememberTalkBack(), ui.prefs.oneHand)
    LaunchedEffect(s?.weather?.lat, s?.weather?.lon) { if (s?.weather != null) Wx.ensure(ctx, ui, false) }
    // Taille du texte et densité propres à l'application (en plus des réglages d'Android)
    val d0 = LocalDensity.current
    val zoom = when (ui.prefs.density) { "compact" -> .9f; "large" -> 1.1f; else -> 1f }
    val fontK = ui.prefs.textScale * (if (ui.prefs.easy) 1.1f else 1f)
    CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(d0.density * zoom, d0.fontScale * fontK)) {
    PepsTheme(input) {
        val p = LocalPal.current
        val haze = rememberHazeState()
        // Icônes de la barre d'état et de la barre système : foncées sur les thèmes clairs (et le ciel de jour),
        // claires sur les thèmes sombres et par-dessus les stories plein écran
        val view = androidx.compose.ui.platform.LocalView.current
        val lightBars = !p.dark && ui.fs !is Fs.Stories
        SideEffect {
            val w = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = lightBars; isAppearanceLightNavigationBars = lightBars }
        }
        Box(Modifier.fillMaxSize().then(if (p.mono) Modifier.grayscale() else Modifier)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) { val e = awaitPointerEvent(PointerEventPass.Initial); e.changes.firstOrNull()?.let { ui.touch = it.position } } } }) {
            // Arrière-plan : source du flou des panneaux en verre (niveau 0)
            Box(Modifier.fillMaxSize().hazeSource(haze, zIndex = 0f)) {
                // Changement d'arrière-plan ou de ciel (jour → crépuscule → nuit, pluie…) : fondu enchaîné
                val kind = if (s?.theme == "weather") "sky" else s?.bg ?: "blob"
                val sky = if (s?.theme == "weather") Weather.sky(wx?.data) else null
                val snow = pg.eggWord() == "Cold"; val stamp = ui.prefs.bgStamp
                val key = listOf(kind, sky?.phase, sky?.cond, sky?.code, snow, stamp, p.dark)
                val skies = remember { mutableMapOf<List<Any?>, Weather.Sky?>() }
                skies[key] = sky
                Crossfade(key, animationSpec = tween(if (p.motion) 0 else 900), label = "backdrop") { k ->
                    Backdrop(k[0] as String, skies[k], k[4] as Boolean, k[5] as Long)
                }
                LaunchedEffect(key) { kotlinx.coroutines.delay(1500); skies.keys.retainAll { it == key } }
            }
            CompositionLocalProvider(LocalContentColor provides p.ink, LocalHaze provides haze) {
                Reachable(ui) {
                when {
                    u == null -> AuthScreen(ui)
                    pg.maintNow()?.mode == "full" && !pg.isAdmin(u) -> MaintenanceScreen(ui)
                    else -> Shell(ui)
                }
                val top = ui.screens.lastOrNull()
                AnimatedContent(top, transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (fadeOut()) }, label = "screen") { sc ->
                    when (sc) {
                        is Screen.Search -> SearchScreen(ui, sc.q)
                        is Screen.Settings -> SettingsScreen(ui, sc.cat)
                        is Screen.Chat -> ChatScreen(ui, sc.with, sc.group)
                        is Screen.Stories -> StoryHistoryScreen(ui)
                        is Screen.Comments -> CommentsScreen(ui, sc.postId)
                        is Screen.Badge -> BadgeScreen(ui, sc.key)
                        is Screen.CoachChat -> CoachChatScreen(ui)
                        is Screen.Journal -> JournalScreen(ui)
                        is Screen.Stats -> StatsScreen(ui)
                        null -> {}
                    }
                }
                }
                SheetHost(ui)
                FullscreenHost(ui)
                Toasts(ui)
                Sparks(ui)
            }
        }
    }
    }
    rev.hashCode()
}

/** TalkBack (exploration au toucher) est-il actif ? Suivi en direct. */
@Composable
private fun rememberTalkBack(): Boolean {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager }
    var on by remember { mutableStateOf(am.isTouchExplorationEnabled) }
    DisposableEffect(am) {
        val l = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        am.addTouchExplorationStateChangeListener(l)
        onDispose { am.removeTouchExplorationStateChangeListener(l) }
    }
    return on
}

/**
 * Mode une main : tirer l'écran vers le bas en haut d'une liste fait descendre toute l'interface sous le pouce
 * (comme le mode une main d'Android). Toucher la zone libérée en haut, ou faire défiler, la remonte.
 */
@Composable
private fun Reachable(ui: AppState, content: @Composable () -> Unit) {
    val p = LocalPal.current
    if (!p.oneHand) { content(); return }
    val reach = remember { Animatable(0f) }
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(on) { reach.animateTo(if (on) 1f else 0f, if (p.motion) snap() else spring(dampingRatio = .78f, stiffness = 380f)) }
    LaunchedEffect(ui.tab, ui.screens.size, ui.sheet, ui.fs) { on = false }
    val pullPx = with(LocalDensity.current) { 80.dp.toPx() }
    val conn = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            var pull = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0f && consumed.y <= 0f) { pull += available.y; if (pull > pullPx && !on) { on = true; Feedback.tick() } }
                else { pull = 0f; if (on && consumed.y < -6f) on = false }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity { pull = 0f; return androidx.compose.ui.unit.Velocity.Zero }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().nestedScroll(conn)) {
        val shift = maxHeight * .4f
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = shift.toPx() * reach.value }) { content() }
        if (reach.value > .01f) Box(Modifier.fillMaxWidth().height(shift * reach.value).clickable(onClickLabel = "Remonter l’écran") { on = false }
            .semantics { contentDescription = "Mode une main : touche pour remonter l’écran" }, contentAlignment = Alignment.Center) {
            Row(Modifier.graphicsLayer { alpha = reach.value }.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pi("up", size = 18.dp, tint = p.ink2); Muted("Touche ici pour remonter", small = true)
            }
        }
    }
}

private fun Modifier.grayscale() = this.drawWithContent {
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    drawIntoCanvas { c -> c.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), paint); drawContent(); c.restore() }
}

@Composable
private fun Shell(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me()!!; val d = LocalDensity.current
    val social = pg.socialOn(u)
    val nav = (if (social) NAV else NAV.filter { it.first != "feed" && it.first != "friends" }) + if (pg.isAdmin(u)) listOf(Triple("mod", "Modo", "shield")) else emptyList()
    if (nav.none { it.first == ui.tab }) ui.tab = "today"
    val badges = mapOf("friends" to pg.db.friendships.count { it.to == u.id && it.status == "pending" } + Social.unreadMsgs(ui), "mod" to if (pg.isAdmin(u)) Mod.count(ui) else 0)
    var topH by remember { mutableIntStateOf(0) }; var botH by remember { mutableIntStateOf(0) }
    val haze = LocalHaze.current
    Box(Modifier.fillMaxSize()) {
        // Contenu des onglets : il défile sous les barres en verre (niveau 1 du flou)
        CompositionLocalProvider(LocalBars provides PaddingValues(top = with(d) { topH.toDp() }, bottom = with(d) { botH.toDp() })) {
            Box(Modifier.fillMaxSize().then(if (haze != null) Modifier.hazeSource(haze, zIndex = 1f) else Modifier)) {
                remember(ui.tab) { Stagger.reset() }
                AnimatedContent(ui.tab, Modifier.fillMaxSize(), transitionSpec = { if (p.motion) EnterTransition.None togetherWith ExitTransition.None else (fadeIn(tween(140)) + scaleIn(spring(dampingRatio = .68f, stiffness = 420f), initialScale = .93f) + slideInVertically(spring(dampingRatio = .72f, stiffness = 420f)) { it / 18 }) togetherWith (fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 1.02f)) }, label = "tab") { tab ->
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
        // Bandeaux givrés sous la barre d'état et la barre de gestes : le contenu qui défile dessous reste lisible
        EdgeFrost(Modifier.align(Alignment.TopCenter), top = true, cover = with(d) { topH.toDp() })
        EdgeFrost(Modifier.align(Alignment.BottomCenter), top = false, cover = with(d) { botH.toDp() })
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topH = it.height }) { TopBar(ui) }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { botH = it.height }) { BottomNav(ui, nav, badges) }
        val fabMod = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = with(d) { botH.toDp() } + 14.dp)
        if (ui.tab in setOf("today", "goals")) Fab("Nouvel objectif", fabMod) { ui.open(Sheet.GoalForm()) }
        else if (ui.tab == "feed" && !pg.isBanned(u)) Fab("Nouvelle publication", fabMod) { if (!pg.socialBlocked("de publier")) ui.open(Sheet.Compose) }
    }
}

/** Barre de navigation PepsGoal : pilule en verre flottante, onglet actif repéré par une pastille qui glisse. */
@Composable
private fun BottomNav(ui: AppState, items: List<Triple<String, String, String>>, badges: Map<String, Int>) {
    val p = LocalPal.current
    val idx = items.indexOfFirst { it.first == ui.tab }.coerceAtLeast(0)
    val shape = RoundedCornerShape(24.dp)
    Box(Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp).fillMaxWidth()
        .shadow(if (p.oled || p.transp) 0.dp else 14.dp, shape, ambientColor = Color(0x6628186E), spotColor = Color(0x4428186E))
        .glassBg(shape, strong = true).border(1.dp, if (p.contrast) p.line else p.glassEdge, shape).padding(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth / items.size
            val x by animateDpAsState(w * idx, if (p.motion) snap() else spring(dampingRatio = .58f, stiffness = 480f), label = "nav")
            Box(Modifier.offset(x = x).width(w).height(56.dp).shadow(if (p.transp) 0.dp else 6.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(p.ink))
            Row(Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, (k, l0, ic) ->
                    // Lecture facile : libellés en mots courants
                    val l = if (p.easy) EASY_NAV[k] ?: l0 else l0
                    val sel = i == idx
                    val col by animateColorAsState(if (sel) p.bg else p.ink2, tween(if (p.motion) 0 else 200), label = "navc")
                    val pop by animateFloatAsState(if (sel && !p.motion) 1.14f else 1f, spring(dampingRatio = .35f, stiffness = 520f), label = "navi")
                    val n = badges[k] ?: 0
                    val inter = remember { MutableInteractionSource() }
                    Column(Modifier.weight(1f).bounce(inter, .86f).height(56.dp).clip(RoundedCornerShape(18.dp)).springClick(inter, label = l) { ui.nav(k) }
                        .semantics { selected = sel; role = Role.Tab; contentDescription = if (n > 0) "$l, $n" else l },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.graphicsLayer { scaleX = pop; scaleY = pop }) {
                            Pi(ic, size = 22.dp, tint = col)
                            if (n > 0) Badge(n, Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-6).dp))
                        }
                        Spacer(Modifier.height(2.dp))
                        FitText(l, size = 11, min = 8, color = col, weight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Fab(desc: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    Box(modifier.bounce(inter, .84f).size(58.dp).shadow(if (p.transp) 0.dp else 16.dp, CircleShape, ambientColor = p.flame, spotColor = p.flame).clip(CircleShape).background(p.accent)
        .springClick(inter, label = desc, onClick = onClick).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) { Pi("plus", size = 28.dp, tint = p.onAccent) }
}

@Composable
private fun TopBar(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me()!!; val st = pg.globalStreak(u.id)
    // Barre du haut posée sur le flou progressif : titre de la page, puis pastilles et boutons ronds en verre
    Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(ui.tab, transitionSpec = { if (p.motion) EnterTransition.None togetherWith ExitTransition.None else (fadeIn(tween(160)) + slideInVertically(spring(dampingRatio = .6f, stiffness = 500f)) { it / 2 }) togetherWith (fadeOut(tween(90)) + slideOutVertically(tween(120)) { -it / 2 }) }, label = "title") { t ->
                    // Titre entier : sa taille s'adapte à la place laissée par les boutons
                    FitText(NAV_TITLES[t] ?: "PepsGoal", size = 24, min = 14, color = p.ink, weight = FontWeight.ExtraBold, family = Unbounded)
                }
                if (!ui.pg.store.online) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Pi("cloudoff", size = 14.dp, tint = p.ink2); Muted("Hors ligne", small = true) }
            }
            val bump = remember { Animatable(1f) }
            LaunchedEffect(st.n) { if (!p.motion) { bump.snapTo(1.35f); bump.animateTo(1f, spring(dampingRatio = .4f)) } }
            val inter = remember { MutableInteractionSource() }
            val pill = RoundedCornerShape(50)
            Row(Modifier.bounce(inter, .9f).height(46.dp).shadow(if (p.transp || p.oled) 0.dp else 10.dp, pill, ambientColor = Color(0x4428186E), spotColor = Color(0x3328186E))
                .glassBg(pill, strong = true).border(1.dp, if (p.contrast) p.line else p.glassEdge, pill).springClick(inter, label = "Ouvrir le profil") { ui.nav("profile") }.semantics(mergeDescendants = true) { contentDescription = "Série de ${pl(st.n, "jour", "jours")}${if (u.freezes > 0) ", ${u.freezes} gel${if (u.freezes > 1) "s" else ""} de série" else ""}" }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value }, verticalAlignment = Alignment.CenterVertically) { Flame(22.dp); Text("${st.n}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
                if (u.freezes > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) { Pi("snow", size = 16.dp, tint = p.type("objective").ink); Text("${u.freezes}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
            }
            RoundGlass("Rechercher", { ui.push(Screen.Search()) }) { Pi("search", size = 21.dp, tint = p.ink) }
            RoundGlass("Notifications", { ui.open(Sheet.Notifs); Social.readNotifs(ui) }, badge = pg.unread()) { Pi("bell", size = 21.dp, tint = p.ink) }
            RoundGlass("Profil", { ui.nav("profile") }) { Av(u, 38.dp) }
        }
        if (!ui.pg.store.online) Note(if (ui.cloud.forceOffline && pg.store.acct) "**Mode hors ligne activé.** Tes objectifs restent disponibles ; tes modifications partiront quand tu repasseras en ligne." else "**Hors ligne.** Tes objectifs restent disponibles sur cet appareil ; les fonctions sociales reviennent avec la connexion.", "cloudoff")
        if (pg.db.sandbox) Note("**Mode démonstration.** Données d’exemple : rien n’est enregistré. Déconnecte-toi pour revenir à ton profil.", "star")
        val now = pg.maintNow(); val next = pg.maintNext()
        if (now != null) Note("**Maintenance en cours**${now.until?.let { " jusqu'au ${fmtDT(it)}" } ?: ""} · ${MAINT_MODE[now.mode]}${if (now.message.isNotEmpty()) ". ${now.message}" else ""}${if (pg.isAdmin(u)) " (en tant qu’admin, tu n’es pas bloqué)" else ""}", "gauge")
        else if (next != null && next.start - nowMs() < 7 * DAY_MS) Note("Maintenance prévue le ${fmtDT(next.start)}${next.until?.let { " jusqu'au ${fmtDT(it)}" } ?: ""} · ${MAINT_MODE[next.mode]}.", "clock")
    }
}

/** Bouton rond en verre clair avec ombre portée (barre du haut). */
@Composable
private fun RoundGlass(desc: String, onClick: () -> Unit, badge: Int = 0, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    Box {
        Box(Modifier.bounce(inter, .86f).size(46.dp).shadow(if (p.transp || p.oled) 0.dp else 12.dp, CircleShape, ambientColor = Color(0x5528186E), spotColor = Color(0x4428186E))
            .glassBg(CircleShape, strong = true).border(1.dp, if (p.contrast) p.line else p.glassEdge, CircleShape).springClick(inter, label = desc, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = if (badge > 0) "$desc, $badge non lus" else desc; role = Role.Button },
            contentAlignment = Alignment.Center, content = content)
        if (badge > 0) Badge(badge, Modifier.align(Alignment.TopEnd).offset(4.dp, (-2).dp))
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
                AnimatedVisibility(shown, enter = fadeIn(tween(120)) + scaleIn(spring(dampingRatio = .45f, stiffness = 500f), initialScale = .8f) + slideInVertically(spring(dampingRatio = .55f, stiffness = 500f)) { it / 2 }) {
                    // Annoncé par TalkBack dès son apparition (région « live »)
                    Row(Modifier.clip(RoundedCornerShape(18.dp)).background(p.glassStrong).border(1.dp, p.line, RoundedCornerShape(18.dp)).semantics { liveRegion = LiveRegionMode.Polite }
                        .padding(start = 14.dp, end = if (t.action != null) 6.dp else 14.dp, top = if (t.action != null) 6.dp else 11.dp, bottom = if (t.action != null) 6.dp else 11.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (t.icon == "flame") Flame(22.dp) else Pi(t.icon, size = 20.dp, tint = p.ink)
                        Text(t.text, Modifier.weight(1f, fill = false), fontWeight = FontWeight.Bold, fontSize = if (p.easy) 16.sp else 14.sp, color = p.ink)
                        if (t.action != null) {
                            // Compte à rebours discret autour du bouton : le temps restant pour annuler
                            val left = remember { Animatable(1f) }
                            LaunchedEffect(Unit) { left.animateTo(0f, tween(5000, easing = LinearEasing)) }
                            val inter = remember { MutableInteractionSource() }
                            Box(Modifier.bounce(inter, .9f).heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(p.ink)
                                .drawWithContent { drawContent(); drawRect(p.flame.copy(alpha = .9f), topLeft = Offset(0f, size.height - 3.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width * left.value, 3.dp.toPx())) }
                                .springClick(inter, label = t.action, role = Role.Button) { ui.toasts.remove(t); t.onAction?.invoke() }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                Text(t.action, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = p.bg)
                            }
                        }
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
