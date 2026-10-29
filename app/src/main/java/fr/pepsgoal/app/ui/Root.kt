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
import androidx.compose.ui.draw.drawBehind
import fr.pepsgoal.app.ui.screens.SocialScreen
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Quatre onglets : Aujourd'hui, Objectifs, Social (Feed, Amis, Messages) et Profil ; Modération en plus pour les admins. */
val NAV = listOf(Triple("today", "Aujourd’hui", "sun"), Triple("goals", "Objectifs", "target"), Triple("social", "Social", "users"), Triple("profile", "Profil", "user"))
val EASY_NAV = mapOf("today" to "Aujourd’hui", "social" to "Amis", "goals" to "Objectifs")
val NAV_TITLES = mapOf("today" to "Aujourd’hui", "goals" to "Objectifs", "feed" to "Social", "friends" to "Social", "profile" to "Profil", "mod" to "Modération")

/** Onglets internes de Social : le Feed (« feed ») et les Amis (« friends », messages compris). */
val SOCIAL_TABS = setOf("feed", "friends")

/** Effets ponctuels partagés : confettis des moments forts, défilement de la page (barre du haut qui se réduit). */
object Fx {
    /** Incrémenté pour lancer une gerbe de confettis (journée parfaite). */
    var confetti by mutableIntStateOf(0)
    /** Défilement de la page ouverte (px), lu au dessin par la barre du haut. */
    var scroll by mutableFloatStateOf(0f)
    /** Dernier onglet ouvert dans Social. */
    var lastSocial = "feed"
}

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
    // Animations coupées dans Android (Options pour les développeurs ou Accessibilité) : « Animations réduites »
    val sysNoAnim = remember { try { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (e: Exception) { false } }
    val input = ThemeInput(theme, s?.accent ?: 0, s?.accentCustom, s?.glassBlur ?: "mid", ui.prefs.materialYou,
        ui.prefs.contrast, ui.prefs.cvd, ui.prefs.mono, ui.prefs.motion || sysNoAnim, ui.prefs.transp, Weather.skyDark(wx?.data), ui.prefs.glassOpacity,
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
            // Arrière-plan : fond uni (crème pêche / prune nuit) ; le décor et le fond animé confiné sont dessinés par la coquille
            Box(Modifier.fillMaxSize().background(p.bg))
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
            CompositionLocalProvider(LocalContentColor provides p.ink, LocalHaze provides null, LocalFlame provides flame, LocalFlameStyle provides FlameStyle(ui.prefs.flameShape, ui.prefs.flameFx), LocalStatuses provides statuses) {
                Box(Modifier.fillMaxSize()) { Reachable(ui) {
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
                    val sp = spring<IntOffset>(dampingRatio = 1f, stiffness = 450f)
                    val sc = spring<Float>(dampingRatio = 1f, stiffness = 450f)
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
                // Confettis des moments forts (journée parfaite) : un seul Canvas, retiré à la fin
                var fxKey by remember { mutableIntStateOf(0) }
                if (Fx.confetti > fxKey) key(Fx.confetti) { Confetti(24) { fxKey = Fx.confetti } }
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

/** Fond animé en haut de l'accueil (Apparence › Fond animé) ; par défaut, actif seulement si un fond a été choisi. */
fun bgTopOn(ui: AppState): Boolean {
    val s = ui.pg.me()?.settings
    return when (ui.prefs.bgTop) {
        "on" -> true; "off" -> false
        else -> s?.theme == "weather" || fr.pepsgoal.core.shopMode(s?.theme ?: "") != null || (s?.bg != null && s.bg !in setOf("blob", "plain"))
    }
}

/**
 * Fond animé choisi (fonds gratuits, scènes, ciel de la Météo, effets de saison et de météo, fond réactif). Dessiné
 * confiné en haut de l'accueil, ou en plein écran dans les aperçus.
 */
@Composable
fun AppBackdrop(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current; val pg = ui.pg; val u = pg.me(); val s = u?.settings
    val wx = Wx.cached(ctx, s?.weather)
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
        val prog = remember(ui.rev, u.id) { val pl = pg.todayPlan(u.id); if (pl.due.isEmpty()) 0f else pl.due.count { it.done }.toFloat() / pl.due.size }
        ReactiveBg(prog, prog >= 1f)
    }
}

@Composable
private fun Shell(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me()!!; val d = LocalDensity.current
    val social = pg.socialOn(u)
    val nav = (if (social) NAV else NAV.filter { it.first != "social" }) + if (pg.isAdmin(u)) listOf(Triple("mod", "Modération", "shield")) else emptyList()
    val navTab = if (ui.tab in SOCIAL_TABS) "social" else ui.tab
    if (nav.none { it.first == navTab }) ui.tab = "today"
    val badges = mapOf("social" to pg.db.friendships.count { it.to == u.id && it.status == "pending" } + Social.unreadMsgs(ui), "mod" to if (pg.isAdmin(u)) Mod.count(ui) else 0)
    var topH by remember { mutableIntStateOf(0) }; var botH by remember { mutableIntStateOf(0) }
    // Bouton « + » : se cache en glissant quand on descend dans la page, revient dès qu'on remonte. Le défilement
    // cumulé sert aussi à la barre du haut (titre réduit, fond plein)
    var fabShown by remember { mutableStateOf(true) }
    LaunchedEffect(ui.tab) { fabShown = true; Fx.scroll = 0f }
    val fabConn = remember { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
            if (consumed.y < -6f) fabShown = false else if (consumed.y > 6f || available.y > 0f) fabShown = true
            Fx.scroll = (Fx.scroll - consumed.y).coerceAtLeast(0f); if (available.y > 0f) Fx.scroll = 0f
            return Offset.Zero
        }
    } }
    Box(Modifier.fillMaxSize()) {
        // Décor : deux cercles en aplat derrière l'en-tête, ou le fond animé confiné en haut de l'accueil
        val scrolled by remember { derivedStateOf { Fx.scroll > 4f } }
        Box(Modifier.fillMaxWidth().height(360.dp)) {
            if (ui.tab == "today" && bgTopOn(ui) && !p.contrast) ConfinedBackdrop(frozen = scrolled) { AppBackdrop(ui) }
            else Box(Modifier.fillMaxSize().decoCircles(p))
        }
        // Grand écran (tablette, pliable ouvert) : la fiche d'un objectif s'ouvre à côté de la liste, et la colonne
        // de contenu reste à une largeur confortable
        val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 900
        val side = (ui.sheet as? Sheet.Goal)?.takeIf { wide && ui.screens.isEmpty() && ui.fs == null }
        // Contenu des onglets : il défile sous la barre d'onglets flottante (opaque, à marche)
        CompositionLocalProvider(LocalBars provides PaddingValues(top = with(d) { topH.toDp() }, bottom = with(d) { botH.toDp() })) {
            Box(Modifier.fillMaxSize().padding(end = if (side != null) 452.dp else 0.dp).nestedScroll(fabConn), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.fillMaxHeight().then(if (wide) Modifier.widthIn(max = 640.dp) else Modifier.fillMaxWidth())) {
                remember(ui.tab) { Stagger.reset() }
                // Changement d'onglet : fondu de 150 ms et glissement de 12 dp
                AnimatedContent(navTab, Modifier.fillMaxSize(), transitionSpec = {
                    val dy = with(d) { 12.dp.roundToPx() }
                    if (p.motion || !Perf.flags.rich) fadeIn(tween(100)) togetherWith fadeOut(tween(80))
                    else (fadeIn(tween(150)) + slideInVertically(Motion.move()) { dy }) togetherWith fadeOut(tween(150))
                }, label = "tab") { tab ->
                    // Tirer pour actualiser sur l'accueil et Social
                    if (tab in setOf("today", "social")) PullRefresh(ui, with(d) { topH.toDp() }) {
                        if (tab == "social") SocialScreen(ui) else TodayScreen(ui)
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
            .chunk(p.bg, p.edge, 28.dp, 4.dp, 2.dp).clip(RoundedCornerShape(26.dp))) {
            CompositionLocalProvider(LocalHaze provides null, LocalInSheet provides true) {
                Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    key(side.id) { fr.pepsgoal.app.ui.sheets.GoalSheet(ui, side.id) }
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topH = it.height }.drawBehind {
            // Au défilement, la barre prend un fond plein (aucun verre)
            val k = (Fx.scroll / (24.dp.toPx())).coerceIn(0f, 1f)
            if (k > 0f) drawRect(p.bg.copy(alpha = k))
        }) { TopBar(ui) }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { botH = it.height }) {
            NavBar(nav, navTab, badges, { ui.nav(it) }, if (p.easy) EASY_NAV else emptyMap(), Modifier.navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 18.dp))
        }
        val fabMod = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = with(d) { botH.toDp() } + 18.dp)
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
        }.size(48.dp, 52.dp).chunk(Color.White, p.flameStep.copy(alpha = .55f), 24.dp, 4.dp, 0.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.graphicsLayer {
                // Flamme qui vacille pendant le chargement
                val w = if (refreshing) kotlin.math.sin(spin.value * 2f * kotlin.math.PI.toFloat()) else 0f
                scaleX = 1f + .08f * w; scaleY = 1f - .06f * w; rotationZ = 6f * w
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
            }) { Flame(26.dp, dim = !refreshing && st.distanceFraction < 1f) }
        }
    }) { content() }
}

/**
 * Barre du haut, opaque et sans filet : date (Aujourd'hui) puis titre en Unbounded 30 ; à droite, les boutons ronds de
 * l'onglet (notifications et avatar sur l'accueil, partager et réglages sur le profil…). Au défilement, le titre se
 * réduit à 20 sp (échelle, sans nouvelle mise en page) et la barre prend un fond plein.
 */
@Composable
private fun TopBar(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me()!!; val ctx = LocalContext.current
    val tab = ui.tab
    Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f)) {
                if (tab == "today") Text(cap(weekdayLongDate(todayDate())), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
                Box(Modifier.graphicsLayer {
                    val k = (Fx.scroll / 120.dp.toPx()).coerceIn(0f, 1f); val s = 1f - k * (1f - 20f / 30f)
                    scaleX = s; scaleY = s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f)
                }.semantics { heading() }) {
                    AnimatedContent(NAV_TITLES[tab] ?: "PepsGoal", transitionSpec = { if (p.motion) EnterTransition.None togetherWith ExitTransition.None else (fadeIn(tween(150)) + slideInVertically(Motion.move()) { it / 3 }) togetherWith fadeOut(tween(100)) }, label = "title") { t ->
                        TabTitle(t)
                    }
                }
                // Hors ligne : modifications en attente d'envoi (touchée : explication)
                if (!ui.pg.store.online) fr.pepsgoal.app.ui.sheets.OfflineChip(ui)
                fr.pepsgoal.app.ui.sheets.SyncedChip(ui)
            }
            val bell = @Composable { RoundGlass("Notifications", { ui.open(Sheet.Notifs); Social.readNotifs(ui) }, badge = pg.unread()) { Pi("bell", size = 20.dp, tint = p.ink) } }
            val search = @Composable { RoundGlass("Rechercher", { ui.push(Screen.Search()) }) { Pi("search", size = 20.dp, tint = p.ink) } }
            when (tab) {
                "today" -> { bell(); AvatarButton(u, remember(ui.rev, u.id) { pg.level(pg.points(u.id)).idx }, { ui.nav("profile") }) }
                "profile" -> {
                    RoundGlass("Partager mon profil", { shareProfile(ctx, ui, u, pg.level(pg.points(u.id))) }) { Pi("share", size = 20.dp, tint = p.ink) }
                    RoundGlass("Paramètres", { ui.push(Screen.Settings()) }) { Pi("gear", size = 20.dp, tint = p.ink) }
                }
                else -> { search(); bell() }
            }
        }
        if (pg.db.sandbox) Note("**Mode démonstration.** Données d’exemple : rien n’est enregistré. Déconnecte-toi pour revenir à ton profil.", "star")
        val now = pg.maintNow(); val next = pg.maintNext()
        if (now != null) Note("**Maintenance en cours**${now.until?.let { " jusqu'au ${fmtDT(it)}" } ?: ""} · ${MAINT_MODE[now.mode]}${if (now.message.isNotEmpty()) ". ${now.message}" else ""}${if (pg.isAdmin(u)) " (en tant qu’admin, tu n’es pas bloqué)" else ""}", "gauge")
        else if (next != null && next.start - nowMs() < 7 * DAY_MS) Note("Maintenance prévue le ${fmtDT(next.start)}${next.until?.let { " jusqu'au ${fmtDT(it)}" } ?: ""} · ${MAINT_MODE[next.mode]}.", "clock")
    }
}

/** Emoji d'un message, d'après son icône. */
private fun toastEmoji(icon: String, action: String?): String = when {
    action == "Annuler" -> "↩️"
    icon == "flame" -> "🔥"
    icon in setOf("check", "star", "trophy", "medal", "sparkle") -> "🎉"
    icon in setOf("x", "ban", "lock") -> "⚠️"
    icon == "cloudoff" -> "📴"; icon == "cloud" -> "☁️"; icon == "clock" -> "⏰"; icon == "bell" -> "🔔"; icon == "trash" -> "🗑️"
    icon == "archive" -> "📦"; icon == "gift" -> "🎁"; icon == "heart" -> "💛"; icon == "users" || icon == "userplus" -> "👋"
    else -> "💬"
}

/**
 * Message en bas de l'écran : capsule prune à marche (identique en clair et en sombre), emoji, texte clair et action en
 * pilule or. Un seul à la fois, 16 dp au-dessus de la barre d'onglets ; entrée en ressort `pop`, sortie en fondu,
 * balayage latéral pour fermer. Annoncé par TalkBack (région « live »).
 */
@Composable
private fun Toasts(ui: AppState) {
    val p = LocalPal.current
    val t = ui.toasts.lastOrNull() ?: return
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 18.dp + 68.dp + 16.dp, start = 14.dp, end = 14.dp), contentAlignment = Alignment.BottomCenter) {
        key(t.id) {
            val dur = if (t.action != null || p.easy) 6000 else 4000
            val appear = remember { Animatable(if (p.motion) 1f else 0f) }
            val dx = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            var wpx by remember { mutableIntStateOf(1) }
            LaunchedEffect(Unit) {
                if (!p.motion) launch { appear.animateTo(1f, Motion.pop()) }
                kotlinx.coroutines.delay(dur - 170L)
                appear.animateTo(0f, tween(150))
            }
            val drag = androidx.compose.foundation.gestures.rememberDraggableState { d -> scope.launch { dx.snapTo(dx.value + d) } }
            val plum = Color(0xFF2A1420)
            Row(Modifier.onSizeChanged { wpx = it.width.coerceAtLeast(1) }.graphicsLayer {
                val a = appear.value
                alpha = (a.coerceIn(0f, 1f) * (1f - kotlin.math.abs(dx.value) / (wpx * 1.1f))).coerceIn(0f, 1f)
                translationY = (1f - a) * 24.dp.toPx(); translationX = dx.value
                val sc = .9f + .1f * a; scaleX = sc; scaleY = sc
            }.then(if (p.motion) Modifier else Modifier.draggable(drag, androidx.compose.foundation.gestures.Orientation.Horizontal, onDragStopped = { v ->
                if (kotlin.math.abs(dx.value) > wpx * .35f || kotlin.math.abs(v) > 1400f) {
                    val dir = if (dx.value + v / 10f >= 0f) 1f else -1f
                    dx.animateTo(dir * wpx * 1.3f, tween(150)); ui.toasts.remove(t)
                } else dx.animateTo(0f, Motion.pop())
            })).fillMaxWidth().chunk(plum, Color(0xFF120810), 20.dp, 4.dp, 0.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .then(if (t.action == null) Modifier.clickable(onClickLabel = "Fermer") { ui.toasts.remove(t) } else Modifier)
                .heightIn(min = 56.dp).padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Emoji(toastEmoji(t.icon, t.action), 26.dp)
                Text(t.text, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = if (p.easy) 16.sp else 15.sp, color = Color(0xFFFFF4EC), lineHeight = 19.sp)
                if (t.action != null) Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.height(36.dp).clip(CircleShape).background(p.gold).clickable(onClickLabel = t.action, role = Role.Button) { ui.toasts.remove(t); t.onAction?.invoke() }
                        .padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(t.action, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = plum)
                    }
                }
            }
        }
    }
}

/**
 * Retour d'une validation : « +N » flammes qui monte en rebondissant à l'endroit touché puis s'estompe (900 ms). Pas de
 * confettis à chaque coche : ils sont réservés aux moments forts.
 */
@Composable
private fun Sparks(ui: AppState) {
    val p = LocalPal.current
    val sp = ui.sparks ?: return
    val prog = remember(sp) { Animatable(0f) }
    val label = remember(sp) { Animatable(0f) }
    LaunchedEffect(sp) {
        launch { label.animateTo(1f, Motion.pop()) }
        prog.animateTo(1f, tween(900, easing = LinearEasing)); if (ui.sparks === sp) ui.sparks = null
    }
    val l = label.value; val fade = (1f - ((prog.value - .6f) / .4f).coerceIn(0f, 1f))
    Text("+${sp.second}", Modifier.offset { IntOffset(sp.first.x.roundToInt() - 24, (sp.first.y - 30 - l * 70 - prog.value * 30).roundToInt()) }
        .graphicsLayer { alpha = fade; val sc = .6f + .4f * l; scaleX = sc; scaleY = sc },
        fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = p.flameInk)
}
