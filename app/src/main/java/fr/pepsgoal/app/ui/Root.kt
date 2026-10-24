package fr.pepsgoal.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Fs
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.peps
import fr.pepsgoal.app.sys.Perf
import fr.pepsgoal.app.ui.screens.*
import fr.pepsgoal.app.ui.sheets.SheetHost
import fr.pepsgoal.app.ui.fullscreen.FullscreenHost
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.hex
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
    // Thème qui suit le soleil : réévalué chaque minute
    val sunTick by androidx.compose.runtime.produceState(0L, ui.prefs.sunTheme) { while (ui.prefs.sunTheme) { kotlinx.coroutines.delay(60_000); value++ } }
    // Ambiance choisie mais pas (ou plus) possédée : on retombe sur le thème de base équivalent
    val theme0 = ui.themePreview ?: s?.theme ?: "system"
    val theme = fr.pepsgoal.core.shopMode(theme0)?.let { m -> if (ui.themePreview != null || pg.owns("mode:${m.key}", m.price, u)) theme0 else fr.pepsgoal.core.baseTheme(theme0) } ?: theme0
    val input = ThemeInput(theme, s?.accent ?: 0, s?.accentCustom, s?.glassBlur ?: "mid", ui.prefs.materialYou,
        ui.prefs.contrast, ui.prefs.cvd, ui.prefs.mono, ui.prefs.motion, ui.prefs.transp, Weather.skyDark(wx?.data), ui.prefs.glassOpacity,
        ui.prefs.easy, rememberTalkBack(), ui.prefs.oneHand,
        if (ui.prefs.seasonal) fr.pepsgoal.app.sys.Season.now()?.let { fr.pepsgoal.app.sys.Season.COLORS[it] } else null,
        if (ui.prefs.sunTheme && theme == "system") sunTick.let { fr.pepsgoal.core.sunDark(wx?.data) } else null)
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
            // Page secondaire ouverte (fond opaque) : une fois la transition finie, l'arrière-plan et l'accueil dessous
            // sont masqués et leurs horloges s'arrêtent. Sinon chaque image du fond animé forcerait à recalculer tous
            // les flous de l'accueil, invisible, pendant qu'on fait défiler la page du dessus
            val covered = ui.screens.isNotEmpty()
            val settled = remember { mutableStateOf(false) }
            LaunchedEffect(covered) { if (covered) { kotlinx.coroutines.delay(700); settled.value = true } else settled.value = false }
            val hidden = covered && settled.value
            val hideLayer = Modifier.graphicsLayer { alpha = if (hidden) 0f else 1f }
            // Fenêtre du bas ouverte : l'écran derrière se floute en douceur (verre dépoli), la fenêtre passe au premier plan
            val sheetOpen = ui.sheet != null && !(ui.sheet is Sheet.Goal && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 900 && ui.screens.isEmpty() && ui.fs == null)
            val backBlur by animateDpAsState(if (sheetOpen && Perf.flags.blur && !p.transp && !p.contrast && !p.motion) 14.dp else 0.dp, tween(if (sheetOpen) 320 else 220, easing = Motion.Emphasized), label = "backBlur")
            val blurLayer = if (backBlur > .3.dp) Modifier.blur(backBlur) else Modifier
            // Arrière-plan : source du flou des panneaux en verre (niveau 0)
            Box(Modifier.fillMaxSize().then(blurLayer).background(p.bg).hazeSource(haze, zIndex = 0f)) { Box(Modifier.fillMaxSize().then(hideLayer)) { CompositionLocalProvider(LocalBgPaused provides hidden) {
                // Changement d'arrière-plan ou de ciel (jour → crépuscule → nuit, pluie…) : fondu enchaîné
                val kind = if (s?.theme == "weather") "sky" else s?.bg ?: "blob"
                val sky = if (s?.theme == "weather") Weather.sky(wx?.data) else null
                val snow = pg.eggWord() == "Cold" || (ui.prefs.seasonal && fr.pepsgoal.app.sys.Season.now() == "noel"); val stamp = ui.prefs.bgStamp
                // Fond à la météo réelle : pluie, neige, orage ou brouillard par-dessus le fond choisi (hors thème Météo)
                val wxCond = if (sky == null && ui.prefs.bgWeather) wx?.data?.current?.let { c -> Weather.wmo(c.code, c.isDay).cond }?.takeIf { it in setOf("rain", "snow", "storm", "fog") } else null
                val key = listOf(kind, sky?.phase, sky?.cond, sky?.code, snow, stamp, p.dark, s?.fx ?: "", wxCond)
                val skies = remember { mutableMapOf<List<Any?>, Weather.Sky?>() }
                skies[key] = sky
                Crossfade(key, animationSpec = tween(if (p.motion) 0 else 900), label = "backdrop") { k ->
                    Backdrop(k[0] as String, skies[k], k[4] as Boolean, k[5] as Long, k[7] as String, k[8] as String?)
                }
                LaunchedEffect(key) { kotlinx.coroutines.delay(1500); skies.keys.retainAll { it == key } }
                // Fond qui réagit à l'avancement du jour (réglage Apparence › Effets et saisons)
                if (u != null && ui.prefs.bgReactive) {
                    val prog = remember(rev, u.id) { val pl = pg.todayPlan(u.id); if (pl.due.isEmpty()) 0f else pl.due.count { it.done }.toFloat() / pl.due.size }
                    ReactiveBg(prog, prog >= 1f)
                }
            } } }
            // Flamme de collection choisie (si elle est toujours débloquée par la meilleure série)
            val skinKey = u?.flameSkin ?: "classic"
            // Jour de fête (Halloween, 1er avril) : la flamme change de couleurs pour la journée
            val flame = remember(skinKey, u?.id, rev) { fr.pepsgoal.core.Eggs.flameColors()?.map(::hex) ?: FLAME_SKINS.firstOrNull { it.key == skinKey && it.colors != null && u != null && pg.bestStreak(u.id) >= it.min }?.colors?.map(::hex) }
            // Statuts du jour (moins de 24 h) : emoji sur les avatars
            val statuses = remember(rev) { pg.db.stories.filter { it.kind == "status" && fr.pepsgoal.core.nowMs() - it.at < fr.pepsgoal.core.STORY_TTL }.sortedBy { it.at }
                .mapNotNull { s -> (s.data?.get("e") as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { s.userId to it } }.toMap() }
            // Briefing du matin lu à voix haute à la première ouverture de la journée (option du briefing)
            val bctx = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(u?.id) {
                val t = fr.pepsgoal.core.today(); val h = java.time.LocalTime.now().hour
                if (u != null && ui.prefs.briefAuto && ui.prefs.briefDay != t && h in 5..11) { ui.prefs.briefDay = t; kotlinx.coroutines.delay(1200); fr.pepsgoal.app.sys.Speaker.briefing(bctx) }
            }
            CompositionLocalProvider(LocalContentColor provides p.ink, LocalHaze provides haze, LocalFlame provides flame, LocalFlameStyle provides FlameStyle(ui.prefs.flameShape, ui.prefs.flameFx), LocalStatuses provides statuses) {
                Box(Modifier.fillMaxSize().then(blurLayer)) { Reachable(ui) {
                Box(Modifier.fillMaxSize().then(hideLayer)) { CompositionLocalProvider(LocalBgPaused provides hidden) {
                    when {
                        u == null -> AuthScreen(ui)
                        pg.maintNow()?.mode == "full" && !pg.isAdmin(u) -> MaintenanceScreen(ui)
                        else -> Shell(ui)
                    }
                } }
                val top = ui.screens.lastOrNull()
                // Transitions dans le sens de la navigation : on avance vers la droite, on revient vers la gauche
                val depth = ui.screens.size; val prevDepth = remember { intArrayOf(depth) }
                val forward = depth >= prevDepth[0]; SideEffect { prevDepth[0] = depth }
                AnimatedContent(top, transitionSpec = {
                    // Page qui avance : arrive de la droite en grandissant légèrement, la précédente recule et s'estompe ;
                    // au retour, le mouvement inverse. Ressorts sans rebond pour un glissement net.
                    val sp = spring<IntOffset>(dampingRatio = .9f, stiffness = 420f)
                    val sc = spring<Float>(dampingRatio = .9f, stiffness = 420f)
                    when {
                        p.motion -> fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                        // Transitions simples (Paramètres › Performance) : fondu court, sans glissement ni mise à l'échelle
                        !Perf.flags.rich -> fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                        forward -> (slideInHorizontally(sp) { it / 4 } + fadeIn(Motion.fadeIn(220)) + scaleIn(sc, initialScale = .97f)) togetherWith
                            (slideOutHorizontally(tween(260, easing = Motion.Emphasized)) { -it / 10 } + fadeOut(Motion.fadeOut(180)) + scaleOut(tween(260, easing = Motion.Emphasized), targetScale = .96f))
                        else -> (slideInHorizontally(sp) { -it / 10 } + fadeIn(Motion.fadeIn(200)) + scaleIn(sc, initialScale = .96f)) togetherWith
                            (slideOutHorizontally(tween(240, easing = Motion.Emphasized)) { it / 4 } + fadeOut(Motion.fadeOut(180)) + scaleOut(tween(240, easing = Motion.Emphasized), targetScale = .98f))
                    }
                }, label = "screen") { sc ->
                    when (sc) {
                        is Screen.Search -> SearchScreen(ui, sc.q)
                        is Screen.Challenges -> ChallengesScreen(ui)
                        is Screen.Settings -> SettingsScreen(ui, sc.cat)
                        is Screen.Chat -> ChatScreen(ui, sc.with, sc.group)
                        is Screen.Stories -> StoryHistoryScreen(ui)
                        is Screen.Comments -> CommentsScreen(ui, sc.postId)
                        is Screen.Badge -> BadgeScreen(ui, sc.key)
                        is Screen.CoachChat -> CoachChatScreen(ui)
                        is Screen.Journal -> JournalScreen(ui)
                        is Screen.Stats -> StatsScreen(ui)
                        is Screen.Week -> WeekScreen(ui)
                        is Screen.Shop -> ShopScreen(ui)
                        is Screen.League -> LeagueScreen(ui)
                        is Screen.Gallery -> GalleryScreen(ui, sc.goalId)
                        is Screen.Pet -> PetScreen(ui)
                        is Screen.Quest -> QuestScreen(ui)
                        null -> {}
                    }
                }
                } }
                SheetHost(ui)
                FullscreenHost(ui)
                EggOverlay(ui)
                Toasts(ui)
                Sparks(ui)
                DailyEggs(ui, u)
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
    // Bouton « + » : s'efface en glissant quand on descend dans la page (il ne cache plus le contenu), revient dès qu'on remonte
    var fabShown by remember { mutableStateOf(true) }
    LaunchedEffect(ui.tab) { fabShown = true }
    val fabConn = remember { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
            if (consumed.y < -6f) fabShown = false else if (consumed.y > 6f || available.y > 0f) fabShown = true
            return Offset.Zero
        }
    } }
    Box(Modifier.fillMaxSize()) {
        // Grand écran (tablette, pliable ouvert) : la fiche d'un objectif s'ouvre à côté de la liste, et la colonne
        // de contenu reste à une largeur confortable
        val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 900
        val side = (ui.sheet as? Sheet.Goal)?.takeIf { wide && ui.screens.isEmpty() && ui.fs == null }
        // Contenu des onglets : il défile sous les barres en verre (niveau 1 du flou)
        CompositionLocalProvider(LocalBars provides PaddingValues(top = with(d) { topH.toDp() }, bottom = with(d) { botH.toDp() })) {
            Box(Modifier.fillMaxSize().padding(end = if (side != null) 452.dp else 0.dp).nestedScroll(fabConn).then(if (haze != null) Modifier.hazeSource(haze, zIndex = 1f) else Modifier), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.fillMaxHeight().then(if (wide) Modifier.widthIn(max = 760.dp) else Modifier.fillMaxWidth())) {
                remember(ui.tab) { Stagger.reset() }
                AnimatedContent(ui.tab, Modifier.fillMaxSize(), transitionSpec = { if (p.motion || !Perf.flags.rich) fadeIn(tween(100)) togetherWith fadeOut(tween(80)) else (fadeIn(Motion.fadeIn(200)) + scaleIn(spring(dampingRatio = .86f, stiffness = 460f), initialScale = .965f) + slideInVertically(spring(dampingRatio = .88f, stiffness = 460f)) { it / 28 }) togetherWith (fadeOut(Motion.fadeOut(110)) + scaleOut(tween(140, easing = Motion.Exit), targetScale = 1.015f)) }, label = "tab") { tab ->
                    // Tirer pour actualiser sur l'accueil, le feed et les amis
                    if (tab in setOf("today", "feed", "friends")) PullRefresh(ui, with(d) { topH.toDp() }) {
                        when (tab) { "feed" -> FeedScreen(ui); "friends" -> FriendsScreen(ui); else -> TodayScreen(ui) }
                    } else when (tab) {
                        "goals" -> GoalsScreen(ui)
                        "profile" -> ProfileScreen(ui)
                        "mod" -> ModerationScreen(ui)
                        else -> TodayScreen(ui)
                    }
                }
                }
            }
        }
        // Volet latéral de la fiche (grand écran)
        if (side != null) Box(Modifier.align(Alignment.TopEnd).padding(top = with(d) { topH.toDp() } + 8.dp, bottom = with(d) { botH.toDp() } + 8.dp, end = 12.dp).width(440.dp).fillMaxHeight()
            .shadow(18.dp.ifShadows(), RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(p.glassStrong.copy(alpha = 1f))) {
            CompositionLocalProvider(LocalHaze provides null, LocalInSheet provides true) {
                Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    key(side.id) { fr.pepsgoal.app.ui.sheets.GoalSheet(ui, side.id) }
                }
            }
        }
        // Bandeaux givrés sous la barre d'état et la barre de gestes : le contenu qui défile dessous reste lisible
        EdgeFrost(Modifier.align(Alignment.TopCenter), top = true, cover = with(d) { topH.toDp() })
        EdgeFrost(Modifier.align(Alignment.BottomCenter), top = false, cover = with(d) { botH.toDp() })
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topH = it.height }) { TopBar(ui) }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { botH = it.height }) {
            NavBar(nav, ui.tab, badges, { ui.nav(it) }, if (p.easy) EASY_NAV else emptyMap(), Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp))
        }
        val fabMod = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = with(d) { botH.toDp() } + 12.dp)
        if (ui.tab in setOf("today", "goals")) Fab("Nouvel objectif", fabShown, { ui.open(Sheet.GoalForm()) }, fabMod)
        else if (ui.tab == "feed" && !pg.isBanned(u)) Fab("Nouvelle publication", fabShown, { if (!pg.socialBlocked("de publier")) ui.open(Sheet.Compose) }, fabMod, icon = "edit")
    }
}

/**
 * Tirer pour actualiser : une flamme grandit pendant qu'on tire, puis s'anime le temps de recharger
 * (synchronisation du compte, social et météo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullRefresh(ui: AppState, top: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    val p = LocalPal.current; val ctx = androidx.compose.ui.platform.LocalContext.current
    var refreshing by remember { mutableStateOf(false) }
    val st = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val spin = slowPhaseState(900, 20)
    // Vibrations progressives en tirant, clic net au seuil (lu hors composition : aucun coût de recomposition)
    LaunchedEffect(st) {
        var step = 0; var armed = false
        snapshotFlow { st.distanceFraction }.collect { f ->
            if (refreshing) return@collect
            val sNow = (f.coerceIn(0f, 1f) * 4).toInt()
            if (sNow > step && sNow < 4) fr.pepsgoal.app.ui.Feedback.ramp(f)
            step = sNow
            val a = f >= 1f
            if (a != armed) { armed = a; if (a) fr.pepsgoal.app.ui.Feedback.armed() else fr.pepsgoal.app.ui.Feedback.disarmed() }
        }
    }
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(refreshing, {
        refreshing = true; fr.pepsgoal.app.ui.Feedback.commit()
        ui.scope.launch {
            try {
                if (ui.pg.store.acct && ui.pg.store.online) { ui.cloud.syncNow(); ui.cloud.refreshSocial() }
                Wx.ensure(ctx, ui, true)
            } catch (e: Exception) {}
            kotlinx.coroutines.delay(500); refreshing = false; ui.bump()
        }
    }, Modifier.fillMaxSize(), state = st, indicator = {
        Box(Modifier.align(Alignment.TopCenter).padding(top = top + 6.dp).graphicsLayer {
            val f = if (refreshing) 1f else st.distanceFraction.coerceIn(0f, 1.3f)
            alpha = f.coerceAtMost(1f); translationY = (f - 1f) * 24.dp.toPx(); val sc = .6f + .4f * f.coerceAtMost(1f); scaleX = sc; scaleY = sc
        }.size(46.dp).shadow(10.dp.ifShadows(), CircleShape, spotColor = p.flame.copy(alpha = .5f)).clip(CircleShape).background(p.glassStrong.copy(alpha = 1f)), contentAlignment = Alignment.Center) {
            Box(Modifier.graphicsLayer {
                // Flamme qui vacille pendant le chargement
                val w = if (refreshing) kotlin.math.sin(spin.value * 2f * kotlin.math.PI.toFloat()) else 0f
                scaleX = 1f + .08f * w; scaleY = 1f - .06f * w; rotationZ = 6f * w
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
            }) { Flame(26.dp, dim = !refreshing && st.distanceFraction < 1f) }
        }
    }) { content() }
}

@Composable
private fun TopBar(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me()!!; val st = pg.globalStreak(u.id)
    // Barre du haut posée sur le flou progressif : titre de la page, puis pastilles et boutons ronds en verre
    Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(ui.tab, transitionSpec = { if (p.motion) EnterTransition.None togetherWith ExitTransition.None else (fadeIn(Motion.fadeIn(200)) + slideInVertically(spring(dampingRatio = .8f, stiffness = 520f)) { it / 2 }) togetherWith (fadeOut(Motion.fadeOut(100)) + slideOutVertically(tween(140, easing = Motion.Exit)) { -it / 3 }) }, label = "title") { t ->
                    TabTitle(NAV_TITLES[t] ?: "PepsGoal")
                }
                // Hors ligne : modifications en attente d'envoi (touchée : explication)
                if (!ui.pg.store.online) fr.pepsgoal.app.ui.sheets.OfflineChip(ui)
                fr.pepsgoal.app.ui.sheets.SyncedChip(ui)
            }
            val bump = remember { Animatable(1f) }
            LaunchedEffect(st.n) { if (!p.motion) { bump.snapTo(1.35f); bump.animateTo(1f, spring(dampingRatio = .4f)) } }
            StreakPill(st.n, u.freezes, "Série de ${pl(st.n, "jour", "jours")}${if (u.freezes > 0) ", ${u.freezes} gel${if (u.freezes > 1) "s" else ""} de série" else ""}", { ui.nav("profile") }, bump.value)
            RoundGlass("Rechercher", { ui.push(Screen.Search()) }) { Pi("search", size = 20.dp, tint = p.ink) }
            RoundGlass("Notifications", { ui.open(Sheet.Notifs); Social.readNotifs(ui) }, badge = pg.unread()) { Pi("bell", size = 20.dp, tint = p.ink) }
        }
        if (pg.db.sandbox) Note("**Mode démonstration.** Données d’exemple : rien n’est enregistré. Déconnecte-toi pour revenir à ton profil.", "star")
        val now = pg.maintNow(); val next = pg.maintNext()
        if (now != null) Note("**Maintenance en cours**${now.until?.let { " jusqu'au ${fmtDT(it)}" } ?: ""} · ${MAINT_MODE[now.mode]}${if (now.message.isNotEmpty()) ". ${now.message}" else ""}${if (pg.isAdmin(u)) " (en tant qu’admin, tu n’es pas bloqué)" else ""}", "gauge")
        else if (next != null && next.start - nowMs() < 7 * DAY_MS) Note("Maintenance prévue le ${fmtDT(next.start)}${next.until?.let { " jusqu'au ${fmtDT(it)}" } ?: ""} · ${MAINT_MODE[next.mode]}.", "clock")
    }
}

@Composable
private fun Toasts(ui: AppState) {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 92.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            for (t in ui.toasts) key(t.id) {
                // Entrée en ressort, sortie en fondu juste avant l'expiration, et balayage latéral pour fermer
                val dur = if (t.action != null || p.easy) 5000 else 3600
                val appear = remember { Animatable(if (p.motion) 1f else 0f) }
                val dx = remember { Animatable(0f) }
                val scope = rememberCoroutineScope()
                var wpx by remember { mutableIntStateOf(1) }
                LaunchedEffect(Unit) {
                    if (!p.motion) launch { appear.animateTo(1f, spring(dampingRatio = .62f, stiffness = 520f)) }
                    kotlinx.coroutines.delay(dur - 260L)
                    appear.animateTo(0f, tween(240, easing = Motion.Exit))
                }
                val drag = androidx.compose.foundation.gestures.rememberDraggableState { d -> scope.launch { dx.snapTo(dx.value + d) } }
                Box(Modifier.onSizeChanged { wpx = it.width.coerceAtLeast(1) }.graphicsLayer {
                    val a = appear.value
                    alpha = (a * (1f - kotlin.math.abs(dx.value) / (wpx * 1.1f))).coerceIn(0f, 1f)
                    translationY = (1f - a) * 40.dp.toPx(); translationX = dx.value
                    val sc = .86f + .14f * a; scaleX = sc; scaleY = sc
                    rotationZ = dx.value / wpx * 6f
                }.then(if (p.motion) Modifier else Modifier.draggable(drag, androidx.compose.foundation.gestures.Orientation.Horizontal, onDragStopped = { v ->
                    if (kotlin.math.abs(dx.value) > wpx * .35f || kotlin.math.abs(v) > 1400f) {
                        val dir = if (dx.value + v / 10f >= 0f) 1f else -1f
                        dx.animateTo(dir * wpx * 1.3f, tween(180, easing = Motion.Exit)); ui.toasts.remove(t)
                    } else dx.animateTo(0f, spring(dampingRatio = .6f, stiffness = 600f))
                }))) {
                    // Pilule flottante : pastille colorée selon le type de message, fine barre du temps restant en bas.
                    // Annoncée par TalkBack dès son apparition (région « live ») ; un appui la ferme.
                    val kind = when (t.icon) { "x", "ban", "lock", "cloudoff" -> p.danger; "check", "flame", "star", "trophy", "medal", "sparkle" -> p.ok; else -> p.focus }
                    val tint = if (t.icon == "flame") p.flame else kind
                    val left = remember { Animatable(1f) }
                    LaunchedEffect(Unit) { left.animateTo(0f, tween(if (t.action != null || p.easy) 5000 else 3600, easing = LinearEasing)) }
                    val shape = smoothShape(26.dp)
                    Row(Modifier.depth(shape, p, 3, tint = androidx.compose.ui.graphics.lerp(p.shade, tint, .35f)).clip(shape)
                        .background(p.glassStrong.copy(alpha = 1f)).then(if (p.contrast) Modifier.border(1.dp, tint.copy(alpha = .6f), shape) else Modifier.sheen(shape, p.dark, .8f))
                        .drawWithContent { drawContent(); drawRect(tint.copy(alpha = .85f), topLeft = Offset(0f, size.height - 3.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width * left.value, 3.dp.toPx())) }
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .then(if (t.action == null) Modifier.clickable(onClickLabel = "Fermer") { ui.toasts.remove(t) } else Modifier)
                        .padding(start = 8.dp, end = if (t.action != null) 6.dp else 16.dp, top = 6.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (t.icon == "flame") Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Flame(22.dp) }
                        else IconTile(t.icon, tint.lift(.15f), tint, 36.dp, 18.dp)
                        Text(t.text, Modifier.weight(1f, fill = false).padding(vertical = 4.dp), fontWeight = FontWeight.Bold, fontSize = if (p.easy) 16.sp else 14.sp, color = p.ink)
                        if (t.action != null) {
                            val inter = remember { MutableInteractionSource() }
                            Box(Modifier.bounce(inter, .9f).heightIn(min = 40.dp).candy(CircleShape, candyOf(tint), p, glow = false).clip(CircleShape)
                                .springClick(inter, label = t.action, role = Role.Button) { ui.toasts.remove(t); t.onAction?.invoke() }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                Text(t.action, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = fr.pepsgoal.app.ui.theme.onColors(candyOf(tint)))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Particule des étincelles : angle, vitesse, taille, couleur, forme (0 rond, 1 losange, 2 trait), rotation. */
private class SparkBit(val a: Float, val v: Float, val r: Float, val c: Int, val shape: Int, val spin: Float)

/**
 * Étincelles à l'endroit touché après une validation : onde de choc, gerbe de particules aux couleurs de la flamme
 * qui retombent avec la gravité, et « +N » qui monte en rebondissant puis s'estompe. Une seule animation de 900 ms.
 */
@Composable
private fun Sparks(ui: AppState) {
    val p = LocalPal.current
    val sp = ui.sparks ?: return
    val prog = remember(sp) { Animatable(0f) }
    val label = remember(sp) { Animatable(0f) }
    LaunchedEffect(sp) {
        launch { label.animateTo(1f, spring(dampingRatio = .45f, stiffness = 380f)) }
        prog.animateTo(1f, tween(900, easing = LinearEasing)); if (ui.sparks === sp) ui.sparks = null
    }
    // Effets décoratifs coupés : seule l'onde et le « +N » restent ; sinon la gerbe suit le réglage des particules
    val bits = remember(sp) { List(if (Perf.flags.fx) particles(26) else 0) { SparkBit(Random.nextFloat() * 6.283f, 120f + Random.nextFloat() * 170f, 2.5f + Random.nextFloat() * 3.5f, it, Random.nextInt(3), Random.nextFloat() * 8f - 4f) } }
    val cols = listOf(p.flame, p.flame2, p.pink, p.flame2, p.type("objective").c, p.type("routine").c)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val k = prog.value; val o = sp.first
            // Onde de choc : anneau qui s'élargit en s'affinant (premier tiers)
            val w = (k * 3f).coerceAtMost(1f)
            if (w < 1f) drawCircle(p.flame.copy(alpha = (1f - w) * .6f), (10f + 46f * FastOutSlowInEasing.transform(w)) * density, o, style = androidx.compose.ui.graphics.drawscope.Stroke((1f - w) * 5f * density + .5f))
            // Gerbe : départ rapide, freinage, puis chute (gravité) ; fondu dans le dernier tiers
            val t = k * .9f
            for (b in bits) {
                val dx = cos(b.a) * b.v * t * (1f - t * .45f)
                val dy = sin(b.a) * b.v * t * (1f - t * .45f) - 70f * t + 260f * t * t
                val c = Offset(o.x + dx * density / 2.4f * 1.6f, o.y + dy * density / 2.4f * 1.6f)
                val a = (1f - ((k - .55f) / .45f).coerceIn(0f, 1f))
                val col = cols[b.c % cols.size].copy(alpha = a)
                val r = b.r * density * (1f - k * .35f)
                when (b.shape) {
                    0 -> drawCircle(col, r, c)
                    1 -> rotate(b.spin * 90f * k, c) { drawRect(col, Offset(c.x - r * .8f, c.y - r * .8f), androidx.compose.ui.geometry.Size(r * 1.6f, r * 1.6f)) }
                    else -> drawLine(col, c, Offset(c.x - cos(b.a) * r * 2.6f, c.y - sin(b.a) * r * 2.6f), strokeWidth = r * .7f, cap = StrokeCap.Round)
                }
            }
        }
        val l = label.value; val fade = (1f - ((prog.value - .6f) / .4f).coerceIn(0f, 1f))
        Text("+${sp.second}", Modifier.offset { IntOffset(sp.first.x.roundToInt() - 24, (sp.first.y - 30 - l * 70 - prog.value * 30).roundToInt()) }
            .graphicsLayer { alpha = fade; val sc = .6f + .4f * l; scaleX = sc; scaleY = sc },
            fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = p.flameInk)
    }
}
