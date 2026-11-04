package fr.pepsgoal.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Fs
import fr.pepsgoal.app.sys.Perf
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.SHOP_BGS
import fr.pepsgoal.core.ownsBg
import fr.pepsgoal.core.seasonalScene
import fr.pepsgoal.core.Weather

/*
 * Arrière-plan « fond partout » (8.1) : une seule couche, dessinée une fois à la racine sous tout le reste (onglets,
 * pages poussées, derrière les feuilles). De bas en haut : `bg` uni, scène calée sur son point focal (+ parallaxe lue au
 * dessin), voile dégradé de `bg` (présence du fond), marée du fond réactif, voile de la barre d'état.
 */

/** Scène imposée par l'écran ouvert (fond d'une conversation) : remplace celle des réglages tant qu'il est affiché. */
object SceneOverride { var key by mutableStateOf<String?>(null) }

/** Plein écran dont la scène est le décor (fête, focus, fractionné, tutoriel) : voile nul, interface masquée dessous. */
fun AppState.sceneFs(): Boolean = fs is Fs.Celebrate || fs is Fs.Focus || fs is Fs.Intervals || fs is Fs.Tuto

/** Clé de la scène affichée : « s:<scène> », fond gratuit, « custom », « sky » (thème Météo) ou « pop ». */
fun AppState.sceneKind(): String {
    SceneOverride.key?.let { return "s:$it" }
    val s = pg.me()?.settings ?: return "pop"
    if (s.theme == "weather") return "sky"
    // Scène de saison animée, proposée d'office à sa période (option d'Apparence), sauf sur une image perso
    if (prefs.bgSeasonAuto && s.bg != "custom") seasonalScene()?.let { return "s:${it.key}" }
    // Scène de saison choisie pendant sa période gratuite, puis ni achetée ni en saison : retour au fond par défaut
    if (s.bg.startsWith("s:")) fr.pepsgoal.core.ALL_BGS.firstOrNull { "s:${it.key}" == s.bg }?.let { b -> if (b.freeIn != null && !pg.ownsBg(b)) return "pop" }
    return when (s.bg) { "blob", "" -> "pop"; else -> s.bg }
}

/** Point focal vertical de la scène (fraction de la hauteur), calé dans le héros. */
private fun focusOf(kind: String): Float = backgroundStyle(kind).focusY

/** Couleur dominante de la scène : teinte le bord et la marche des barres. null : aucune scène visible. */
@Composable
fun rememberSceneTint(ui: AppState): Color? {
    val p = LocalPal.current
    val kind = ui.sceneKind(); val where = ui.prefs.bgWhere
    if (p.contrast || where == "off" || kind == "plain") return null
    return remember(kind, p.dark, ui.prefs.bgStamp, p.deco) {
        when {
            kind.startsWith("s:") -> fr.pepsgoal.core.ALL_BGS.firstOrNull { it.key == kind.removePrefix("s:") }?.let { hex(if (p.dark) it.tintDark else it.tint) }
            kind == "pop" -> if (p.dark) Color(0xFF4A2440) else Color(0xFFFFB59A)
            kind == "custom" -> ui.prefs.bgPalette.firstOrNull()?.let { runCatching { hex(it) }.getOrNull() }
            kind == "sky" -> if (p.dark) Color(0xFF1C2A5A) else Color(0xFF5AA8EF)
            else -> p.deco[0]
        }
    }
}

/** Économie d'énergie d'Android, suivie en direct (le fond se fige). */
@Composable
fun rememberPowerSave(): Boolean {
    val ctx = LocalContext.current
    val pm = remember { ctx.getSystemService(PowerManager::class.java) }
    var on by remember { mutableStateOf(pm?.isPowerSaveMode == true) }
    DisposableEffect(pm) {
        val r = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { on = pm?.isPowerSaveMode == true } }
        androidx.core.content.ContextCompat.registerReceiver(ctx, r, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { try { ctx.unregisterReceiver(r) } catch (e: Exception) {} }
    }
    return on
}

/** Hauteur du héros de l'écran ouvert (sans la barre d'état) : la scène y place son point focal. */
private fun heroOf(ui: AppState): Dp = when {
    ui.screens.isNotEmpty() -> if (ui.screens.last() is fr.pepsgoal.app.Screen.Settings) 150.dp else 176.dp
    ui.tab == "today" -> 150.dp
    else -> 120.dp
}

/**
 * Fond de l'application, unique, sous toute l'interface. [where] : « all » (partout), « home » (haut de l'accueil) ou
 * « off » (`bg` + cercles de décor + marée). Contraste élevé : `bg` uni ; « Réduire la transparence » : scène limitée au
 * haut de l'écran. L'horloge est figée pendant un défilement, sous une feuille ou un dialogue, en économie d'énergie.
 */
@Composable
fun AppBg(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current; val pg = ui.pg; val u = pg.me(); val s = u?.settings
    val fsScene = ui.sceneFs()
    val kind = ui.sceneKind()
    val where = if (u == null || fsScene || SceneOverride.key != null) "all" else ui.prefs.bgWhere
    val onHome = ui.tab == "today" && ui.screens.isEmpty()
    val mode = when {
        p.contrast -> "none"
        where == "off" || (where == "home" && !onHome) -> "off"
        where == "home" || (p.transp && !fsScene) -> "top"
        else -> "full"
    }
    val powerSave = rememberPowerSave()
    val paused = Fx.scrolling || ui.sheet != null || Overlays.open || (ui.fs != null && !fsScene) || powerSave || !ui.prefs.bgAnim
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val sb = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val heroY = sb + heroOf(ui)
    // Voile : présence choisie, ou réglage de l'image perso ; nul sous les écrans de fête
    val custom = kind == "custom" && ui.prefs.bgDim >= 0f
    val (a, b, c) = when {
        fsScene -> veilStops(-1, p.dark)
        custom -> ui.prefs.bgDim.coerceIn(0f, .8f).let { v -> Triple(.2f * v, .7f * v, v) }
        else -> veilStops(ui.prefs.bgLevel, p.dark)
    }
    Box(Modifier.fillMaxSize().background(p.bg).clipToBounds()) {
        CompositionLocalProvider(LocalBgPaused provides paused) {
            if (mode == "off") Box(Modifier.fillMaxSize().decoCircles(p))
            else if (mode != "none") BoxWithConstraints(Modifier.fillMaxSize()) {
                val h = maxHeight
                // Painted portrait scenes keep their landmarks inside the mobile crop.
                val illustrated = hasSceneArtwork(kind)
                val extra = h * if (illustrated) .08f else .3f
                val base = if (illustrated) -extra * .5f else (heroY - (h + extra) * focusOf(kind)).coerceIn(-extra, 0.dp)
                val basePx = with(dens) { base.toPx() }
                val extraPx = with(dens) { extra.toPx() }
                val capPx = with(dens) { if (illustrated) extra.toPx() * .5f else 96.dp.toPx() }
                Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).height(h + extra).graphicsLayer {
                    // Parallaxe : la scène suit le défilement à 0,35, plafonnée à 96 dp (lu au dessin : aucune recomposition)
                    val sc = if (ui.screens.isNotEmpty()) Fx.pageScroll else Fx.scroll
                    translationY = (basePx - (sc * .35f).coerceIn(0f, capPx)).coerceIn(-extraPx, 0f)
                }) { SceneLayer(ui, kind) }
                // Voile dégradé de bg : calme visuel sous les listes (le contraste du texte, lui, vient des plaques)
                val bg = p.bg
                Box(Modifier.fillMaxSize().drawBehind {
                    drawRect(Brush.verticalGradient(0f to bg.copy(alpha = a), .26f to bg.copy(alpha = a), .5f to bg.copy(alpha = b), 1f to bg.copy(alpha = c)))
                })
                // Accueil seulement / transparence réduite : la scène s'arrête sous le héros, fondue vers bg
                if (mode == "top") {
                    val stopPx = with(dens) { (heroY + 120.dp).toPx() }; val fadePx = with(dens) { 140.dp.toPx() }
                    Box(Modifier.fillMaxSize().drawBehind {
                        val sc = if (ui.screens.isNotEmpty()) Fx.pageScroll else Fx.scroll
                        val y0 = (stopPx - sc).coerceAtLeast(0f)
                        drawRect(Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg), startY = y0, endY = y0 + fadePx), Offset(0f, y0), androidx.compose.ui.geometry.Size(size.width, fadePx))
                        drawRect(bg, Offset(0f, y0 + fadePx - 1f), androidx.compose.ui.geometry.Size(size.width, (size.height - y0 - fadePx + 1f).coerceAtLeast(0f)))
                    })
                }
            }
        }
        // Marée de braise : visible même fond coupé (« Nulle part »)
        if (u != null && ui.prefs.bgReactive && mode != "none" && !fsScene) {
            val prog = remember(ui.rev, u.id) { val pl = pg.todayPlan(u.id); if (pl.due.isEmpty()) 0f else pl.due.count { it.done }.toFloat() / pl.due.size }
            Tide(prog)
        }
        // Voile de la barre d'état : icônes système lisibles sur n'importe quelle scène (bg 80 % → 0 sur 44 dp)
        if (mode != "none") {
            val bg = p.bg
            Box(Modifier.fillMaxWidth().height(sb + 20.dp).drawBehind { drawRect(Brush.verticalGradient(listOf(bg.copy(alpha = .8f), bg.copy(alpha = 0f)))) })
        }
    }
}

/** Couche 1 : la scène (fondu enchaîné quand elle change : autre fond, ciel qui passe du jour au crépuscule…). */
@Composable
private fun SceneLayer(ui: AppState, kind: String) {
    val p = LocalPal.current; val pg = ui.pg; val s = pg.me()?.settings
    val environment = LocalSceneEnvironment.current
    val sky = if (kind == "sky") environment?.sky() else null
    val snow = pg.eggWord() == "Cold" || (ui.prefs.seasonal && fr.pepsgoal.app.sys.Season.now() == "noel")
    // Only scene identity triggers a crossfade: live solar/weather updates do not retain another bitmap set.
    Crossfade(kind, animationSpec = tween(if (p.motion) 0 else 600), label = "scene") { current ->
        val capability = backgroundStyle(current).capability
        val weather = if (ui.prefs.bgWeather && capability.weather && current != "sky") environment?.condition else null
        Backdrop(current, if (current == "sky") sky else null, snow, ui.prefs.bgStamp, s?.fx ?: "", weather)
    }
}
