package fr.pepsgoal.app.ui

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.pepsgoal.app.sys.Perf
import fr.pepsgoal.app.peps
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Pal
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.Weather
import kotlinx.coroutines.isActive
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Image d'arrière-plan personnalisée, gardée sur l'appareil. */
fun bgFile(ctx: android.content.Context) = File(ctx.filesDir, "bg.jpg")

/**
 * Scène du fond (couche 1 de [AppBg], aperçus d'Apparence) : scène illustrée, fond gratuit, ciel de la Météo, image
 * personnelle ou « Collines braise ». Rien d'opaque autour : le `bg` uni et le voile sont posés par l'appelant.
 */
@Composable
fun Backdrop(kind: String, sky: Weather.Sky?, snow: Boolean, bgStamp: Long, fx: String = "", weather: String? = null) {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize()) {
        if (p.contrast) return@Box
        if (sky != null) { SkyCanvas(sky); return@Box }
        when (kind) {
            "custom" -> CustomBg(bgStamp)
            "plain" -> Canvas(Modifier.fillMaxSize()) { drawRect(if (PLAIN_BACKGROUND_SOURCE == "theme.bg") p.bg else hex(PLAIN_BACKGROUND_SOURCE)) }
            "grid", "stars", "waves", "aurora", "mesh" -> FreeBg(kind)
            else -> if (kind.startsWith("s:")) SceneBg(kind.removePrefix("s:")) else PopBg()
        }
        // Effets décoratifs : coupés par Paramètres › Performance (ou le mode Économie)
        if (Perf.flags.fx) {
            val realPrecipitation = weather in setOf("rain", "snow", "storm")
            if ((snow || fx == "snow") && !realPrecipitation) SnowFx()
            else if (fx.isNotEmpty() && fx != "snow" && !p.motion) ThemeFx(fx)
        }
        // Temps qu'il fait dehors, par-dessus le fond (réglage Apparence › Fond à la météo)
        if (weather != null && kind != "s:rainwindow") {
            val environment = LocalSceneEnvironment.current
            SceneWeather(weather, environment?.clouds ?: 0f, environment?.wind ?: 0f,
                environment?.freshness ?: 0f, animate = Perf.flags.fx && !p.motion)
        }
    }
}

/** Weather stays legible when motion is reduced; rain/snow are a single overlay, never doubled. */
@Composable
internal fun SceneWeather(cond: String, clouds: Float, wind: Float, freshness: Float, animate: Boolean) {
    if (freshness <= 0f || cond !in setOf("rain", "snow", "storm", "fog")) return
    val count = particles(if (cond == "snow") 46 else 64)
    val night = LocalSceneEnvironment.current?.night ?: 0f
    val phase = if (animate) bgPhaseState(30000, fps = 12) else remember { mutableFloatStateOf(.3f) }
    Canvas(Modifier.fillMaxSize()) { drawSceneWeather(cond, clouds, wind, freshness, night, phase.value, count) }
}



/**
 * Vrai quand l'horloge du fond doit être figée : défilement (et 500 ms après), feuille ou dialogue ouverts, économie
 * d'énergie, fond animé coupé, calque masqué. Figé = dernière image (aucun redessin).
 */
val LocalBgPaused = compositionLocalOf { false }

/** Nombre de particules à dessiner selon le réglage « particules » (au moins une). */
fun particles(n: Int): Int = if (n <= 0) 0 else (n * Perf.flags.particles).roundToInt().coerceIn(1, n)

/**
 * Horloge des fonds (scènes, fonds gratuits, effets) : suit les réglages de performance (fond figé, plafond [cap]
 * d'images par seconde) et s'arrête quand le fond est caché. Elle reprend là où elle s'était arrêtée.
 */
@Composable
fun bgPhaseState(dur: Int, fps: Int = 12, cap: Int = Perf.flags.bgFps, initial: Float = .3f): State<Float> =
    slowPhaseState(dur, min(fps, cap.coerceAtLeast(1)), running = Perf.flags.bgAnim, initial = initial)

/**
 * Phase 0 → 1 sur [dur] ms, mise à jour [fps] fois par seconde seulement. Entre deux images, aucune demande de
 * rafraîchissement n'est faite (simple attente) : l'écran peut rester au repos, ce qui économise beaucoup de batterie
 * par rapport aux animations infinies à 60/120 images par seconde. En arrière-plan, l'attente de l'image suspend tout.
 */
@Composable
fun slowPhase(dur: Int, fps: Int = 12, offset: Int = 0): Float = slowPhaseState(dur, fps, offset).value

/**
 * Même horloge, à lire dans la phase de dessin (graphicsLayer, drawWithContent) : pas de recomposition à chaque image.
 * [running] faux : l'horloge s'arrête (fond figé ou caché) puis reprend là où elle en était ; [initial] : phase de départ.
 */
@Composable
fun slowPhaseState(dur: Int, fps: Int = 12, offset: Int = 0, running: Boolean = true, initial: Float = 0f, followBgPause: Boolean = true): State<Float> {
    val p = LocalPal.current
    // Temps écoulé (ms), gardé entre deux arrêts pour reprendre sans saut
    val acc = remember { longArrayOf(offset + (initial * dur).toLong()) }
    val v = remember { mutableFloatStateOf((acc[0] % dur) / dur.toFloat()) }
    // Caché sous une page opaque (LocalBgPaused) : rien à animer
    // [followBgPause] faux : animation de premier plan (autocollant, cadeau) qui reste visible au-dessus d'une page opaque
    if (p.motion || !running || (followBgPause && LocalBgPaused.current)) return v
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(dur, fps, lifecycle) {
      lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
        val step = 1000L / fps.coerceAtLeast(1); val base = acc[0]; var t0 = -1L
        while (isActive) {
            withFrameNanos { n -> if (t0 < 0) t0 = n; acc[0] = base + (n - t0) / 1_000_000L; v.floatValue = (acc[0] % dur) / dur.toFloat() }
            kotlinx.coroutines.delay(step)
        }
      }
    }
    return v
}

/** Intensité des couleurs : renforcée en clair (sinon le fond paraît terne derrière le verre des cartes). */
private fun Pal.al(): Float = if (dark) blobAlpha else min(1f, blobAlpha * 1.55f)
/** Durée d'un cycle des fonds gratuits (ms). */

/**
 * Fonds gratuits (Blob, Aurore, Vagues, Constellation, Dégradé, Grille) : dégradés « mesh » aux couleurs du thème,
 * dessinés dans Scenes.kt. L'horloge est lue dans la phase de dessin : pas de recomposition à chaque image.
 */
@Composable
fun FreeBg(kind: String) {
    val p = LocalPal.current
    val artwork = rememberSceneArtwork(kind)
    val t = bgPhaseState(backgroundStyle(kind).cycleMs, backgroundStyle(kind).fps, initial = backgroundStyle(kind).restPhase)
    val fp = remember(kind, p.dark, p.bg, p.ink, p.blobs, p.blobAlpha) { FreePal(p.dark, p.bg, p.ink, p.blobs, p.al(), backgroundStyle(kind)) }
    Canvas(Modifier.fillMaxSize()) {
        if (artwork != null) paintSceneArtwork(kind, artwork, t.value, null)
        else freeBg(kind, fp, t.value)
    }
}



/** Effets des thèmes de la boutique : pétales, feuilles, cœurs et confettis tombent ; braises et bulles montent ; étoiles scintillent. */




/* ------------------------------------------------------------------ thème Météo : ciel en direct */
/** Le ciel météo et les scènes utilisent le même instant et le même peintre cartoon. */
@Composable
fun SkyCanvas(s: Weather.Sky) {
    val artwork = rememberSceneArtwork("sky")
    val live = LocalSceneEnvironment.current
    val phase = bgPhaseState(backgroundStyle("sky").cycleMs, backgroundStyle("sky").fps, initial = backgroundStyle("sky").restPhase)
    val environment = live ?: fr.pepsgoal.core.SceneAtmosphere.resolve(java.time.Instant.now()).copy(
        condition = s.cond, clouds = s.clouds, code = s.code, sun = s.prog)
    val placeholder: DrawScope.() -> Unit = { drawRect(Color(0xFF63B6EB)) }
    OffscreenCanvas("weather:$environment", phase, Modifier.fillMaxSize(), scale = Perf.flags.sceneScale, placeholder = placeholder) {
        if (artwork != null) paintSceneArtwork("sky", artwork, it, environment)
        else livingSky(environment, it)
    }
    SceneWeather(environment.condition, environment.clouds, environment.wind, environment.freshness,
        animate = Perf.flags.fx && !LocalPal.current.motion)
}

/* ------------------------------------------------------------------ 8.1 : Collines braise, marée de braise */

/** Fond gratuit par défaut « Collines braise » : aplats seulement, collines qui ondulent très lentement (6 images/s). */
@Composable
fun PopBg(still: Boolean = false) {
    val p = LocalPal.current
    val artwork = rememberSceneArtwork("pop", still)
    val t = if (still) null else bgPhaseState(backgroundStyle("pop").cycleMs, backgroundStyle("pop").fps, initial = backgroundStyle("pop").restPhase)
    val context = LocalContext.current
    val environment = if (still) null else LocalSceneEnvironment.current?.let {
        sceneLighting(it, context.peps.prefs.bgLight, p.dark, context.peps.prefs.bgWeather)
    }
    Canvas(Modifier.fillMaxSize()) {
        val phase = t?.value ?: backgroundStyle("pop").restPhase
        if (artwork != null) paintSceneArtwork("pop", artwork, phase, environment)
        else popHills(p.dark, phase, environment)
    }
}

/** Constantes de « Collines braise » (créées une fois, pas à chaque image). */




/**
 * « Collines braise » : ciel crème (prune la nuit), soleil (lune) en aplat avec anneau plein, confettis (étoiles), nuages
 * en capsules, trois collines pleines et fleurs (lucioles) jusqu'en bas. Coût d'un fond uni.
 */


/**
 * Fond qui réagit : « marée de braise ». Deux vagues en aplats translucides montent du bas de l'écran avec l'avancement
 * du jour (8 % → 34 % de la hauteur), en ressort à chaque validation ; aucune animation infinie, aucun dégradé radial.
 */
@Composable
fun Tide(progress: Float) {
    val p = LocalPal.current
    val k by androidx.compose.animation.core.animateFloatAsState(progress.coerceIn(0f, 1f), if (p.motion) snap() else tween(1200, easing = fr.pepsgoal.app.ui.Motion.Emphasized), label = "tide")
    val wavePath = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val top = h * (1f - (.08f + .26f * k))
        fun wave(y: Float, amp: Float, ph: Float, col: Color) {
            val path = wavePath.apply { reset(); moveTo(0f, h); lineTo(0f, y); var x = 0f; while (x <= w + 6f) { lineTo(x, y + amp * sin(x / w * 2.2f * PI.toFloat() + ph)); x += 6f }; lineTo(w, h); close() }
            drawPath(path, col)
        }
        wave(top, 16.dp.toPx(), 0f, Color(0xFFFF8A3D).copy(alpha = if (p.dark) .20f else .16f))
        wave(top + 30.dp.toPx(), 12.dp.toPx(), 2f, Color(0xFFFF3D96).copy(alpha = if (p.dark) .18f else .13f))
    }
}
