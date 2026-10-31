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
    /** Défilement de l'onglet ouvert (px), lu au dessin par la barre compacte et la parallaxe du fond. */
    var scroll by mutableFloatStateOf(0f)
    /** Défilement de la page poussée au-dessus des onglets (px). */
    var pageScroll by mutableFloatStateOf(0f)
    /** Une liste défile (et 500 ms après) : l'horloge de la scène est figée. */
    var scrolling by mutableStateOf(false)
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
    val input = ThemeInput(theme, s?.accent ?: 0, s?.accentCustom, ui.prefs.materialYou,
        ui.prefs.contrast, ui.prefs.cvd, ui.prefs.mono, ui.prefs.motion || sysNoAnim, ui.prefs.transp, Weather.skyDark(wx?.data),
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
            // Page poussée (sans fond : la scène reste dessous) ou plein écran de fête : les onglets s'effacent en fondu
            // court, puis sont masqués et leurs horloges s'arrêtent
            val covered = ui.screens.isNotEmpty() || ui.sceneFs()
            val shellA by animateFloatAsState(if (covered) 0f else 1f, tween(if (p.motion) 0 else 200), label = "shell")
            val hidden = shellA <= .001f
            val hideLayer = Modifier.graphicsLayer { alpha = shellA }
            // Arrière-plan unique, sous tout le reste (onglets, pages, derrière les feuilles), hors du calque masqué
            AppBg(ui)
            // Migration 8.1 (une fois) : fond partout, « Collines braise » au lieu du Blob
            LaunchedEffect(u?.id) {
                if (u != null && u.settings.bg == "blob") { u.settings.bg = "pop"; pg.store.commit() }
                if (ui.prefs.migrate81(Perf.recommended == "saver") && u != null)
                    ui.toast("Ton fond s’affiche maintenant partout", "image", "Réglages") { ui.push(Screen.Settings("appearance")) }
            }
            // Flamme de collection choisie (si elle est toujours débloquée par la meilleure série)
            val skinKey = u?.flameSkin ?: "classic"
            // Jour de fête (Halloween, 1er avril) : la flamme change de couleurs pour la journée
            val flame = remember(skinKey, u?.id, rev) { fr.pepsgoal.core.Eggs.flameColors()?.map(::hex) ?: FLAME_SKINS.firstOrNull { it.key == skinKey && it.colors != null && u != null && pg.bestStreak(u.id) >= it.min }?.colors?.map(::hex) }
            // Surprise « Six Seven » : série de 67 jours ou plus, la flamme fait le geste ; débloquée la première fois
            val sixSeven = remember(rev, u?.id) { u != null && fr.pepsgoal.core.Eggs.sixSeven(pg.globalStreak(u.id).n) }
            LaunchedEffect(sixSeven, u?.id) {
                if (sixSeven && u != null && "sixseven" !in pg.eggsFound(u)) {
                    kotlinx.coroutines.delay(1200)
                    ui.egg("sixseven", "6️⃣7️⃣ Six Seven ! ${fr.pepsgoal.core.Eggs.SIX_SEVEN} jours de série : ta flamme ne tient plus en place 🤲", "confetti")
                }
            }
            // Statuts du jour (moins de 24 h) : emoji sur les avatars
            val statuses = remember(rev) { pg.db.stories.filter { it.kind == "status" && fr.pepsgoal.core.nowMs() - it.at < fr.pepsgoal.core.STORY_TTL }.sortedBy { it.at }
                .mapNotNull { s -> (s.data?.get("e") as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { s.userId to it } }.toMap() }
            // Briefing du matin lu à voix haute à la première ouverture de la journée (option du briefing)
            val bctx = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(u?.id) {
                val t = fr.pepsgoal.core.today(); val h = java.time.LocalTime.now().hour
                if (u != null && ui.prefs.briefAuto && ui.prefs.briefDay != t && h in 5..11) { ui.prefs.briefDay = t; kotlinx.coroutines.delay(1200); fr.pepsgoal.app.sys.Speaker.briefing(bctx) }
            }
            CompositionLocalProvider(LocalContentColor provides p.ink, LocalFlame provides flame, LocalSixSeven provides sixSeven, LocalFlameStyle provides FlameStyle(ui.prefs.flameShape, ui.prefs.flameFx), LocalStatuses provides statuses,
                LocalSceneTint provides rememberSceneTint(ui)) {
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
                        is Screen.Season -> SeasonScreen(ui)
                        is Screen.Month -> MonthScreen(ui, sc.key)
                        null -> {}
                    }
                }
                } }
                SheetHost(ui)
                FullscreenHost(ui)
                // Dialogues et sélecteurs (date, heure) : au-dessus de tout ; Retour les ferme en premier
                OverlayHost()
                if (Overlays.open) androidx.activity.compose.BackHandler { Overlays.back() }
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
                MiniPlaque("Touche ici pour remonter", emoji = "👆")
            }
        }
    }
}

private fun Modifier.grayscale() = this.drawWithContent {
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    drawIntoCanvas { c -> c.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), paint); drawContent(); c.restore() }
}

/** En-tête de l'onglet ouvert, premier élément de sa liste (héros, notes) : fourni par la coquille à [ScreenList]. */
val LocalTabHeader = compositionLocalOf<(@Composable () -> Unit)?> { null }

/** Emoji de la tuile de chaque onglet dans la barre compacte. */
private val TAB_EMOJI = mapOf("today" to "☀️", "goals" to "🎯", "social" to "👥", "feed" to "👥", "friends" to "👥", "profile" to "🙂", "mod" to "🛡️")

@Composable
private fun Shell(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me()!!; val d = LocalDensity.current
    val social = pg.socialOn(u)
    val nav = (if (social) NAV else NAV.filter { it.first != "social" }) + if (pg.isAdmin(u)) listOf(Triple("mod", "Modération", "shield")) else emptyList()
    val navTab = if (ui.tab in SOCIAL_TABS) "social" else ui.tab
    if (nav.none { it.first == navTab }) ui.tab = "today"
    val badges = mapOf("social" to pg.db.friendships.count { it.to == u.id && it.status == "pending" } + Social.unreadMsgs(ui), "mod" to if (pg.isAdmin(u)) Mod.count(ui) else 0)
    var botH by remember { mutableIntStateOf(0) }
    // Bouton « + » : se cache en glissant quand on descend dans la page, revient dès qu'on remonte
    var fabShown by remember { mutableStateOf(true) }
    LaunchedEffect(ui.tab) { fabShown = true; Fx.scroll = 0f }
    val fabConn = remember { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
            if (consumed.y < -6f) fabShown = false else if (consumed.y > 6f || available.y > 0f) fabShown = true
            return Offset.Zero
        }
    } }
    val sb = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        // Grand écran (tablette, pliable ouvert) : la fiche d'un objectif s'ouvre à côté de la liste, et la colonne
        // de contenu reste à une largeur confortable ; la scène reste visible autour
        val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 900
        val side = (ui.sheet as? Sheet.Goal)?.takeIf { wide && ui.screens.isEmpty() && ui.fs == null }
        // Contenu des onglets : le héros (scène visible, titre en plaque) est le premier élément de la liste ; tout
        // défile sous la barre d'onglets opaque
        CompositionLocalProvider(LocalBars provides PaddingValues(top = sb, bottom = with(d) { botH.toDp() }),
            LocalTabHeader provides { TabHeader(ui, navTab) }) {
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
                    if (tab in setOf("today", "social")) PullRefresh(ui, sb) {
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
        if (side != null) Box(Modifier.align(Alignment.TopEnd).padding(top = sb + 8.dp, bottom = with(d) { botH.toDp() } + 8.dp, end = 12.dp).width(440.dp).fillMaxHeight()
            .chunk(p.bg, p.edge, 28.dp, 4.dp, 2.dp).clip(RoundedCornerShape(26.dp))) {
            CompositionLocalProvider(LocalInSheet provides true) {
                Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    key(side.id) { fr.pepsgoal.app.ui.sheets.GoalSheet(ui, side.id) }
                }
            }
        }
        // Barre compacte : apparaît (fondu + glissement de 8 dp) quand le héros est passé ; aucune animation de hauteur
        val heroPx = with(d) { (if (navTab == "today") 150.dp else 120.dp).toPx() - 56.dp.toPx() }
        val passed by remember(heroPx) { derivedStateOf { Fx.scroll > heroPx } }
        val k by animateFloatAsState(if (passed && !p.oneHand) 1f else 0f, if (p.motion) snap() else tween(180), label = "compact")
        if (k > .01f) StatusScrim { k }
        if (k > .01f) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().then(if (wide) Modifier.widthIn(max = 640.dp) else Modifier).statusBarsPadding().padding(horizontal = 8.dp, vertical = 2.dp)) {
            CompactBar(shown = { k }) {
                CompactTitle(NAV_TITLES[ui.tab] ?: "PepsGoal", p.flame2, null, null, emoji = TAB_EMOJI[ui.tab])
                TabActions(ui)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { botH = it.height }) {
            NavBar(nav, navTab, badges, { ui.nav(it) }, if (p.easy) EASY_NAV else emptyMap(), Modifier.navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 18.dp))
        }
        // Bouton + : remonte au-dessus d'un toast (toast de 56 dp + 12)
        val fabMod = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = with(d) { botH.toDp() } + 18.dp)
        val lift = if (ui.toasts.isNotEmpty()) 68.dp else 0.dp
        if (ui.tab in setOf("today", "goals")) Fab("Nouvel objectif", fabShown, { ui.open(Sheet.GoalForm()) }, fabMod, lift = lift)
        else if (ui.tab == "feed" && !pg.isBanned(u)) Fab("Nouvelle publication", fabShown, { if (!pg.socialBlocked("de publier")) ui.open(Sheet.Compose) }, fabMod, icon = "edit", lift = lift)
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
        }.size(48.dp, 52.dp).chunk(Color.White, p.flameStep, 24.dp, 4.dp, 0.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.graphicsLayer {
                // Flamme qui vacille pendant le chargement
                val w = if (refreshing) kotlin.math.sin(spin.value * 2f * kotlin.math.PI.toFloat()) else 0f
                scaleX = 1f + .08f * w; scaleY = 1f - .06f * w; rotationZ = 6f * w
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
            }) { Flame(26.dp, dim = !refreshing && st.distanceFraction < 1f) }
        }
    }) { content() }
}

/** Boutons ronds de l'onglet (notifications et avatar sur l'accueil, partager et réglages sur le profil…). */
@Composable
fun TabActions(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return; val ctx = LocalContext.current
    val bell = @Composable { RoundGlass("Notifications", { ui.open(Sheet.Notifs); Social.readNotifs(ui) }, badge = pg.unread()) { Pi("bell", size = 20.dp, tint = p.ink) } }
    val search = @Composable { RoundGlass("Rechercher", { ui.push(Screen.Search()) }) { Pi("search", size = 20.dp, tint = p.ink) } }
    when (ui.tab) {
        "today" -> { bell(); AvatarButton(u, remember(ui.rev, u.id) { pg.level(pg.points(u.id)).idx }, { ui.nav("profile") }) }
        "profile" -> {
            RoundGlass("Partager mon profil", { shareProfile(ctx, ui, u, pg.level(pg.points(u.id))) }) { Pi("share", size = 20.dp, tint = p.ink) }
            RoundGlass("Paramètres", { ui.push(Screen.Settings()) }) { Pi("gear", size = 20.dp, tint = p.ink) }
        }
        else -> { search(); bell() }
    }
}

/**
 * Héros de l'onglet ouvert : la scène se voit en grand (150 dp sur Aujourd'hui, 96 sur le Profil, 120 ailleurs), date et
 * statut de synchronisation en mini-plaques, titre en plaque, boutons ronds à droite ; notes de démonstration et de
 * maintenance en cartes juste dessous.
 */
@Composable
fun TabHeader(ui: AppState, tab: String, extra: @Composable ColumnScope.() -> Unit = {}) {
    val pg = ui.pg; val u = pg.me() ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TabHero(NAV_TITLES[if (tab == "social") "feed" else tab] ?: "PepsGoal", if (tab == "today") cap(weekdayLongDate(todayDate())) else null,
            when (tab) { "today" -> 150.dp; "profile" -> 96.dp; else -> 120.dp },
            chips = { fr.pepsgoal.app.ui.sheets.OfflineChip(ui); fr.pepsgoal.app.ui.sheets.SyncedChip(ui) }) { TabActions(ui) }
        if (pg.db.sandbox) Note("**Mode démonstration.** Données d’exemple : rien n’est enregistré. Déconnecte-toi pour revenir à ton profil.", "star")
        val now = pg.maintNow(); val next = pg.maintNext()
        if (now != null) Note("**Maintenance en cours**${now.until?.let { " jusqu'au ${fmtDT(it)}" } ?: ""} · ${MAINT_MODE[now.mode]}${if (now.message.isNotEmpty()) ". ${now.message}" else ""}${if (pg.isAdmin(u)) " (en tant qu’admin, tu n’es pas bloqué)" else ""}", "gauge")
        else if (next != null && next.start - nowMs() < 7 * DAY_MS) Note("Maintenance prévue le ${fmtDT(next.start)}${next.until?.let { " jusqu'au ${fmtDT(it)}" } ?: ""} · ${MAINT_MODE[next.mode]}.", "clock")
        extra()
    }
}

/** Emoji d'un message, d'après son icône. */
private fun toastEmoji(icon: String, action: String?): String = when {
    action == "Annuler" -> "↩️"
    icon == "flame" -> "🔥"
    icon in setOf("check", "star", "trophy", "medal", "sparkle") -> "🎉"
    icon in setOf("x", "ban", "lock") -> "⚠️"
    icon == "cloudoff" -> "📴"; icon == "cloud" -> "☁️"; icon == "clock" -> "⏰"; icon == "bell" -> "🔔"; icon == "trash" -> "🗑️"
    icon == "image" -> "🏞️"; icon == "archive" -> "📦"; icon == "gift" -> "🎁"; icon == "heart" -> "💛"; icon == "users" || icon == "userplus" -> "👋"
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
            val plum = fr.pepsgoal.app.ui.theme.PLUM
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
            })).fillMaxWidth().chunk(plum, fr.pepsgoal.app.ui.theme.PLUM_DEEP, 20.dp, 4.dp, 0.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .then(if (t.action == null) Modifier.clickable(onClickLabel = "Fermer") { ui.toasts.remove(t) } else Modifier)
                .heightIn(min = 56.dp).padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Emoji(toastEmoji(t.icon, t.action), 26.dp)
                Text(t.text, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = if (p.easy) 16.sp else 15.sp, color = fr.pepsgoal.app.ui.theme.CREAM, lineHeight = 19.sp)
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
