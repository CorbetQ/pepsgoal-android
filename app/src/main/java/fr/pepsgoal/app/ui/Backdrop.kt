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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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

/** Arrière-plan derrière toute l'application (calque `.backdrop` de la version web). */
@Composable
fun Backdrop(kind: String, sky: Weather.Sky?, snow: Boolean, bgStamp: Long, fx: String = "", weather: String? = null) {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize().background(p.bg)) {
        if (p.transp || p.contrast) return@Box
        if (sky != null) { SkyCanvas(sky); return@Box }
        when (kind) {
            "custom" -> CustomBg(bgStamp)
            "plain" -> {}
            "grid", "stars", "waves", "aurora", "mesh" -> FreeBg(kind)
            else -> if (kind.startsWith("s:")) {
                // Scène illustrée de la boutique, sous un léger voile aux couleurs du thème (lisibilité des cartes)
                SceneBg(kind.removePrefix("s:"))
                Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = if (p.dark) .28f else .18f)))
            } else FreeBg("blob")
        }
        // Effets décoratifs : coupés par Paramètres › Performance (ou le mode Économie)
        if (Perf.flags.fx) { if (snow || fx == "snow") SnowFx() else if (fx.isNotEmpty() && !p.motion) ThemeFx(fx) }
        // Temps qu'il fait dehors, par-dessus le fond (réglage Apparence › Fond à la météo)
        if (weather != null && Perf.flags.fx && !p.motion) WeatherFx(weather)
    }
}

/**
 * Fond animé confiné (option Apparence › Fond animé) : dessiné seulement dans les 360 dp du haut de l'accueil, à 70 %
 * (clair) ou 55 % (sombre) d'opacité, et fondu vers le fond à partir de la moitié. [frozen] : horloges arrêtées (page
 * défilée, économie d'énergie, animations réduites). Les cartes, opaques, passent par-dessus.
 */
@Composable
fun ConfinedBackdrop(frozen: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPal.current
    Box(modifier.fillMaxSize().graphicsLayer { alpha = if (p.dark) .55f else .7f; compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent { drawContent(); drawRect(Brush.verticalGradient(0f to Color.Black, .5f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn) }) {
        CompositionLocalProvider(LocalBgPaused provides (frozen || LocalBgPaused.current)) { content() }
    }
}

/** Pluie qui tombe en biais, neige, orage (pluie et éclairs) ou brouillard qui dérive, selon la météo du moment. */
@Composable
private fun WeatherFx(cond: String) {
    if (cond == "snow") { SnowFx(); return }
    val n = particles(if (cond == "fog") 0 else if (cond == "storm") 150 else 110)
    val drops = remember(n) { List(n) { floatArrayOf(Random.nextFloat() * 1.2f, Random.nextFloat(), 14 + Random.nextFloat() * 16, .7f + Random.nextFloat() * .6f, .25f + Random.nextFloat() * .35f) } }
    val clock = bgPhaseState(if (cond == "fog") 40000 else 6000, fps = 24, cap = Perf.flags.bgFps * 2)
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value; val d = density / 2.6f
        if (cond == "fog") {
            // Bancs de brume en trois couches qui glissent lentement
            for (i in 0 until 3) { val y = size.height * (.35f + i * .2f); val x = ((t + i * .33f) % 1f) * size.width * 2 - size.width
                drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent), Offset(x + size.width, y), size.width * .9f), Offset(x, y - size.height * .12f), androidx.compose.ui.geometry.Size(size.width * 2, size.height * .24f)) }
            drawRect(Color.White.copy(alpha = .08f))
            return@Canvas
        }
        drawRect(Color(0xFF1A2236).copy(alpha = if (cond == "storm") .22f else .1f))
        for (f in drops) {
            val k = (f[1] + t * f[3] * 3f) % 1f
            val y = k * (size.height + 80f) - 40f; val x = f[0] * size.width - k * 60f * d
            val len = f[2] * d * 2f
            drawLine(Color(0xFFCFE2FF).copy(alpha = f[4]), Offset(x, y), Offset(x - len * .25f, y + len), strokeWidth = 1.6f * d)
        }
        // Éclairs : un flash bref de temps en temps
        if (cond == "storm") { val ph = (t * 6f) % 1f; if (ph < .03f) drawRect(Color.White.copy(alpha = .35f * (1f - ph / .03f))) }
    }
}

/**
 * Vrai quand le fond est entièrement caché (sous un écran opaque poussé par-dessus) : ses horloges s'arrêtent.
 * Sinon chaque image du fond invaliderait la source du verre givré et forcerait le recalcul de tous les flous.
 */
val LocalBgPaused = compositionLocalOf { false }

/** Nombre de particules à dessiner selon le réglage « particules » (au moins une). */
fun particles(n: Int): Int = if (n <= 0) 0 else (n * Perf.flags.particles).roundToInt().coerceIn(1, n)

/**
 * Horloge des fonds (scènes, fonds gratuits, effets) : suit les réglages de performance (fond figé, plafond [cap]
 * d'images par seconde) et s'arrête quand le fond est caché. Elle reprend là où elle s'était arrêtée.
 */
@Composable
fun bgPhaseState(dur: Int, fps: Int = 12, cap: Int = Perf.flags.bgFps): State<Float> =
    slowPhaseState(dur, min(fps, cap.coerceAtLeast(1)), running = Perf.flags.bgAnim, initial = .3f)

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
fun slowPhaseState(dur: Int, fps: Int = 12, offset: Int = 0, running: Boolean = true, initial: Float = 0f): State<Float> {
    val p = LocalPal.current
    // Temps écoulé (ms), gardé entre deux arrêts pour reprendre sans saut
    val acc = remember { longArrayOf(offset + (initial * dur).toLong()) }
    val v = remember { mutableFloatStateOf((acc[0] % dur) / dur.toFloat()) }
    // Caché sous une page opaque (LocalBgPaused) : rien à animer
    if (p.motion || !running || LocalBgPaused.current) return v
    LaunchedEffect(dur, fps) {
        val step = 1000L / fps.coerceAtLeast(1); val base = acc[0]; var t0 = -1L
        while (isActive) {
            withFrameNanos { n -> if (t0 < 0) t0 = n; acc[0] = base + (n - t0) / 1_000_000L; v.floatValue = (acc[0] % dur) / dur.toFloat() }
            kotlinx.coroutines.delay(step)
        }
    }
    return v
}

/** Intensité des couleurs : renforcée en clair (sinon le fond paraît terne derrière le verre des cartes). */
private fun Pal.al(): Float = if (dark) blobAlpha else min(1f, blobAlpha * 1.55f)
/** Durée d'un cycle des fonds gratuits (ms). */
private val FREE_DUR = mapOf("blob" to 26000, "aurora" to 18000, "waves" to 14000, "stars" to 12000, "mesh" to 30000, "grid" to 16000)

/**
 * Fonds gratuits (Blob, Aurore, Vagues, Constellation, Dégradé, Grille) : dégradés « mesh » aux couleurs du thème,
 * dessinés dans Scenes.kt. L'horloge est lue dans la phase de dessin : pas de recomposition à chaque image.
 */
@Composable
private fun FreeBg(kind: String) {
    val p = LocalPal.current
    val t = bgPhaseState(FREE_DUR[kind] ?: 26000)
    val fp = remember(p.dark, p.bg, p.ink, p.blobs, p.blobAlpha) { FreePal(p.dark, p.bg, p.ink, p.blobs, p.al()) }
    Canvas(Modifier.fillMaxSize()) { freeBg(kind, fp, t.value) }
}

@Composable
private fun CustomBg(stamp: Long) {
    val ctx = LocalContext.current; val p = LocalPal.current
    // Décodée hors du fil principal, réduite à la taille de l'écran (une photo de 12 Mpx pèserait près de 50 Mo)
    val res by produceState<Pair<Boolean, ImageBitmap?>>(false to null, stamp) {
        value = true to kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                bgFile(ctx).takeIf { it.exists() }?.let { f ->
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, o)
                    val dm = ctx.resources.displayMetrics; val want = max(dm.widthPixels, dm.heightPixels).coerceAtLeast(1)
                    var sample = 1; while (max(o.outWidth, o.outHeight) / (sample * 2) >= want) sample *= 2
                    BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }
    if (!res.first) return // décodage en cours : fond uni
    val bmp = res.second
    if (bmp == null) { FreeBg("blob"); return }
    // Flou et assombrissement réglables (Paramètres › Apparence) ; par défaut, un léger voile aux couleurs du thème
    val raw = ctx.getSharedPreferences("pepsgoal.device", android.content.Context.MODE_PRIVATE)
    val blur = remember(stamp) { raw.getInt("bgBlur", 0) }; val dim = remember(stamp) { raw.getFloat("bgDim", -1f) }
    Image(bmp, null, Modifier.fillMaxSize().then(if (blur > 0 && Build.VERSION.SDK_INT >= 31 && Perf.flags.blur) Modifier.blur(blur.dp) else Modifier), contentScale = ContentScale.Crop)
    Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = if (dim >= 0f) dim else if (p.dark) .45f else .3f)))
}

/** Effets des thèmes de la boutique : pétales, feuilles, cœurs et confettis tombent ; braises et bulles montent ; étoiles scintillent. */
@Composable
private fun ThemeFx(kind: String) {
    val rnd = remember(kind) { Random(kind.hashCode()) }
    val n = particles(if (kind == "stars") 60 else 34)
    val parts = remember(kind, n) { List(n) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), .6f + rnd.nextFloat() * .9f, .35f + rnd.nextFloat() * .5f, 10 + rnd.nextFloat() * 16, rnd.nextFloat() * 6.28f, rnd.nextInt(4).toFloat()) } }
    // Horloge lue dans la phase de dessin : pas de recomposition à chaque image
    val clock = bgPhaseState(30000, fps = 24, cap = Perf.flags.bgFps * 2)
    val cols = when (kind) {
        "petals" -> listOf(0xFFFFB7D5, 0xFFFF8FC7, 0xFFFFD6E8, 0xFFFFFFFF)
        "leaves" -> listOf(0xFFE2711D, 0xFFFFB347, 0xFFB5451B, 0xFFD9A441)
        "hearts" -> listOf(0xFFFF4F8B, 0xFFFF9AC1, 0xFFE85BFF, 0xFFFF6B8B)
        "embers" -> listOf(0xFFFFB000, 0xFFFF6A00, 0xFFFF3D00, 0xFFFFD27F)
        "bubbles" -> listOf(0xFFFFFFFF, 0xFFBFE3FF, 0xFF9AE6FF, 0xFFFFFFFF)
        "confetti" -> listOf(0xFFFFC300, 0xFFFF4F8B, 0xFF22D3FF, 0xFF2EF2B0)
        else -> listOf(0xFFFFFFFF, 0xFFFFF3C4, 0xFFC6D4FF, 0xFFFFFFFF)
    }.map { Color(it) }
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value
        for (f in parts) {
            val c = cols[f[6].toInt()]; val r = 6f * f[2] * density / 2.6f
            if (kind == "stars") {
                // Scintillement sur place
                val a = f[3] * (.35f + .65f * ((sin(t * 6.28f * 6 / f[4] * 10 + f[5]) + 1) / 2))
                drawCircle(c.copy(alpha = a), r * .5f, Offset(f[0] * size.width, f[1] * size.height))
                continue
            }
            val up = kind == "embers" || kind == "bubbles"
            val k = (f[1] + t * 30f / f[4]) % 1f
            val y = (if (up) 1f - k else k) * (size.height + 40f) - 20f
            val x = f[0] * size.width + sin(k * 12f + f[5]) * 26f * density / 2.6f
            val a = f[3] * (if (up) (1f - k).coerceIn(0f, 1f) * 1.2f else 1f).coerceAtMost(1f)
            val rot = (k * 720f + f[5] * 57f)
            when (kind) {
                "bubbles" -> drawCircle(c.copy(alpha = a * .7f), r * 1.4f, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * density / 2.6f))
                "embers" -> drawCircle(c.copy(alpha = a), r * .55f, Offset(x, y))
                "hearts" -> run {
                    val s = r * 1.6f
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x, y + s * .9f)
                        cubicTo(x - s * 1.4f, y, x - s * .6f, y - s * 1.1f, x, y - s * .35f)
                        cubicTo(x + s * .6f, y - s * 1.1f, x + s * 1.4f, y, x, y + s * .9f)
                    }
                    drawPath(path, c.copy(alpha = a))
                }
                else -> rotate(rot, Offset(x, y)) {
                    // Pétales et feuilles : ovales ; confettis : petits rectangles
                    if (kind == "confetti") drawRect(c.copy(alpha = a), Offset(x - r * .5f, y - r), androidx.compose.ui.geometry.Size(r, r * 2f))
                    else drawOval(c.copy(alpha = a), Offset(x - r, y - r * .6f), androidx.compose.ui.geometry.Size(r * 2f, r * 1.2f))
                }
            }
        }
    }
}

@Composable
private fun SnowFx() {
    val n = particles(46)
    val flakes = remember(n) { List(n) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), 2 + Random.nextFloat() * 5, .45f + Random.nextFloat() * .5f, 9 + Random.nextFloat() * 14) } }
    val clock = bgPhaseState(20000, fps = 24, cap = Perf.flags.bgFps * 2)
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value
        for (f in flakes) {
            val y = ((f[1] + t * 20f / f[4]) % 1f) * size.height
            val x = f[0] * size.width + sin(y / 80f) * 20
            drawCircle(Color.White.copy(alpha = f[3]), f[2], Offset(x, y))
        }
    }
}

/* ------------------------------------------------------------------ thème Météo : ciel en direct */
private val PAL = mapOf("day" to listOf("#2C7BD4", "#5AA8EF", "#AFD9FF"), "dawn" to listOf("#28367A", "#B66B92", "#FFC08A"), "dusk" to listOf("#211F4E", "#874579", "#F48E5C"), "night" to listOf("#050A1D", "#0D1636", "#1C2A5A"))
private val GRAY = mapOf("day" to "#9AA6B4", "dawn" to "#6E6976", "dusk" to "#5E5868", "night" to "#1E222E")
private val STORM = mapOf("day" to "#3E4552", "dawn" to "#2F313B", "dusk" to "#2A2933", "night" to "#0B0D14")

private fun cover(s: Weather.Sky) = when (s.cond) { "storm" -> .86f; "rain" -> .66f; "fog" -> .62f; "snow" -> .66f; "clouds" -> .22f + .45f * min(1f, s.clouds / 100f); else -> .2f * min(1f, s.clouds / 100f) }
private fun precip(c: Int) = when (c) { 51, 56, 61, 80 -> .55f; 53, 57, 63, 66, 71, 73, 81, 85 -> 1f; 55, 65, 67, 75, 82, 86, 95, 96, 99 -> 1.6f; else -> 1f }

/** Ciel dessiné en continu : soleil ou lune, étoiles et étoiles filantes, nuages sur trois profondeurs, pluie, neige, brouillard, éclairs. */
@Composable
fun SkyCanvas(s: Weather.Sky) {
    val p = LocalPal.current
    var frame by remember { mutableLongStateOf(0L) }
    val run = Perf.flags.bgAnim && !LocalBgPaused.current
    val step = 1000L / Perf.flags.bgFps.coerceIn(1, 12)
    LaunchedEffect(s.phase, s.cond, p.motion, run, step) {
        if (p.motion || !run) return@LaunchedEffect
        // 12 images par seconde au plus : nuages et pluie restent fluides, l'écran et le flou des cartes travaillent moins
        while (isActive) { withFrameNanos { n -> frame = n }; kotlinx.coroutines.delay(step) }
    }
    val seed = remember(s.phase, s.cond, s.code) { Random(s.code * 31 + s.phase.hashCode()) }
    val stars = remember(seed) { List(180) { floatArrayOf(seed.nextFloat(), seed.nextFloat() * .78f, .35f + seed.nextFloat() * 1.1f, seed.nextFloat() * 6.28f, .5f + seed.nextFloat() * 1.2f) } }
    val clouds = remember(seed) {
        val n = when (s.cond) { "clear" -> if (s.clouds > 30) 3 else 1; "fog" -> 3; "clouds" -> 4 + (s.clouds / 22).toInt(); else -> 9 }
        List(n) { i -> floatArrayOf(seed.nextFloat(), -.05f + seed.nextFloat() * .47f, (i % 3).toFloat(), .8f + seed.nextFloat() * .5f, .6f + seed.nextFloat() * .5f) }
    }
    val drops = remember(seed) { if (s.cond == "rain" || s.cond == "storm") List(particles((160 * precip(s.code)).toInt())) { floatArrayOf(seed.nextFloat() * 1.2f, seed.nextFloat(), 10 + seed.nextFloat() * 12, 650 + seed.nextFloat() * 300) } else emptyList() }
    val flakes = remember(seed) { if (s.cond == "snow") List(particles((110 * precip(s.code)).toInt())) { floatArrayOf(seed.nextFloat(), seed.nextFloat(), 1 + seed.nextFloat() * 2.3f, 16 + seed.nextFloat() * 26, seed.nextFloat() * 6.28f) } else emptyList() }
    Canvas(Modifier.fillMaxSize()) {
        val t = frame / 1e9f
        val cov = cover(s); val night = s.phase == "night"
        val sky = PAL.getValue(s.phase).map(::hex)
        val tint = hex(when (s.cond) { "storm" -> STORM.getValue(s.phase); "snow" -> if (night) "#2A3142" else "#AEB9C7"; else -> GRAY.getValue(s.phase) })
        val k = when (s.cond) { "storm" -> .85f; "snow" -> .82f; else -> cov }
        drawRect(Brush.verticalGradient(listOf(lerp(sky[0], tint, k), lerp(sky[1], tint, k), lerp(sky[2], if (s.cond == "snow") hex("#D5DCE4") else tint, k * .9f))))
        if ((night || s.phase == "dusk") && cov < .7f) {
            val base = (if (night) .95f else .4f) * (1 - cov)
            for (st in stars) drawCircle(Color.White.copy(alpha = (base * (.55f + .45f * sin(t * st[4] + st[3]))).coerceIn(0f, 1f)), st[2], Offset(st[0] * size.width, st[1] * size.height))
            if (night && cov < .3f) {
                val ph = (t % 9f) / .7f
                if (ph < 1f) { val sx = size.width * .85f - ph * 260; val sy = size.height * .12f + ph * 120
                    drawLine(Brush.linearGradient(listOf(Color.White.copy(alpha = .9f * (1 - ph)), Color.Transparent), Offset(sx, sy), Offset(sx + 90, sy - 42)), Offset(sx, sy), Offset(sx + 90, sy - 42), 1.6f) }
            }
        }
        val r = min(size.width, size.height) * .085f
        if (!night) {
            val a = (1 - cov * 1.15f).coerceAtLeast(0f)
            // Lueur diffuse : reste visible derrière un voile nuageux, même quand le disque est caché
            val glow = (1 - cov * .8f).coerceAtLeast(0f)
            val sx = size.width * (.08f + s.prog * .84f); val sy = size.height * (.6f - sin(PI.toFloat() * s.prog) * .46f)
            val warm = s.phase != "day"; val low = (sy / size.height - .3f).coerceIn(0f, 1f)
            val c = Offset(sx, sy); val pulse = 1f + .035f * sin(t * .7f)
            val hot = if (warm) Color(255, 214, 160) else Color(255, 246, 214)
            val gold = if (warm) Color(255, 150, 70) else Color(255, 212, 110)
            if (glow > .02f) {
                // Grand voile coloré du ciel autour du soleil, puis halo resserré
                drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = (if (warm) .42f else .26f) * glow), gold.copy(alpha = .1f * glow), Color.Transparent), c, r * 10), r * 10, c)
                drawCircle(Brush.radialGradient(listOf(hot.copy(alpha = .7f * glow), hot.copy(alpha = .22f * glow), Color.Transparent), c, r * 3.4f * pulse), r * 3.4f * pulse, c)
                // Soleil bas : bande chaude le long de l'horizon
                if (warm || low > .2f) drawRect(Brush.verticalGradient(listOf(Color.Transparent, gold.copy(alpha = .22f * glow * max(low, if (warm) .7f else 0f)), Color.Transparent), sy - r * 2, sy + r * 3), Offset(0f, sy - r * 2), androidx.compose.ui.geometry.Size(size.width, r * 5))
            }
            if (a > .02f) {
                // Rayons effilés qui tournent lentement, fondus vers l'extérieur
                if (s.cond == "clear") {
                    val rays = Brush.radialGradient(listOf(hot.copy(alpha = .2f * a), hot.copy(alpha = .06f * a), Color.Transparent), c, r * 7.5f)
                    for (i in 0 until 14) {
                        val ang = t * .02f + i * PI.toFloat() / 7; val len = r * (if (i % 2 == 0) 7.5f else 5f) * (1 + .06f * sin(t * .5f + i)); val wd = if (i % 2 == 0) .06f else .04f
                        val path = Path().apply {
                            moveTo(sx + cos(ang - wd * 3) * r * .9f, sy + sin(ang - wd * 3) * r * .9f)
                            lineTo(sx + cos(ang) * len, sy + sin(ang) * len)
                            lineTo(sx + cos(ang + wd * 3) * r * .9f, sy + sin(ang + wd * 3) * r * .9f); close()
                        }
                        drawPath(path, rays)
                    }
                }
                // Couronne lumineuse, puis disque chauffé à blanc au centre, doré au bord
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .85f * a), hot.copy(alpha = .5f * a), Color.Transparent), c, r * 1.6f), r * 1.6f, c)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a), Color(255, 251, 236).copy(alpha = a), hot.copy(alpha = a), gold.copy(alpha = .96f * a)), c, r), r, c)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .9f * a), Color.Transparent), Offset(sx - r * .3f, sy - r * .32f), r * .55f), r * .55f, Offset(sx - r * .3f, sy - r * .32f))
                // Reflets d'objectif le long de l'axe soleil → centre de l'écran
                if (s.cond == "clear" && a > .4f) {
                    val cx = size.width / 2; val cy = size.height * .55f; val f = (a - .4f) / .6f
                    for ((d, rr, col) in listOf(Triple(.55f, .5f, Color(255, 220, 150)), Triple(.9f, .28f, Color(170, 220, 255)), Triple(1.25f, .8f, Color(255, 190, 210)), Triple(1.6f, .35f, Color(200, 255, 220)))) {
                        val o = Offset(sx + (cx - sx) * d, sy + (cy - sy) * d)
                        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = .16f * f), col.copy(alpha = .05f * f), Color.Transparent), o, r * rr), r * rr, o)
                    }
                }
            }
        } else {
            val a = (1 - cov).coerceAtLeast(0f); val mx = size.width * .78f; val my = size.height * .15f; val mr = r * .62f
            if (a > .02f) {
                drawCircle(Brush.radialGradient(listOf(Color(200, 215, 255).copy(alpha = .28f * a), Color.Transparent), Offset(mx, my), mr * 5), mr * 5, Offset(mx, my))
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a), Color(196, 206, 230).copy(alpha = a)), Offset(mx - mr * .35f, my - mr * .35f), mr), mr, Offset(mx, my))
                for (c in listOf(floatArrayOf(-.3f, -.1f, .22f), floatArrayOf(.25f, .2f, .16f), floatArrayOf(.05f, -.38f, .1f), floatArrayOf(-.05f, .35f, .12f)))
                    drawCircle(Color(120, 132, 170).copy(alpha = .22f * a), c[2] * mr, Offset(mx + c[0] * mr, my + c[1] * mr))
            }
        }
        val warm = s.phase == "dawn" || s.phase == "dusk"
        val cc = when { s.cond == "storm" -> Color(84, 92, 106); s.cond == "rain" -> if (night) Color(70, 78, 96) else Color(138, 148, 164); night -> Color(92, 104, 140); warm -> Color(255, 214, 206); else -> Color.White }
        val calpha = if (night) .55f else if (s.cond == "clear") .75f else .92f
        for (c in clouds) {
            val depth = c[2]; val w = min(size.width * .9f, (200 + 180 * c[4]) * (1 + depth * .35f)); val h = w * .33f
            val speed = (5 + depth * 6) * c[3]
            val x = ((c[0] * (size.width + w) + t * speed) % (size.width + w * 1.3f)) - w
            val y = c[1] * size.height + depth * 18
            cloud(x, y, w, h, cc.copy(alpha = calpha * (.7f + depth * .15f)))
        }
        if (s.cond == "fog") for (i in 0 until 3) {
            val y = size.height * (.3f + i * .22f); val off = sin(t * .05f + i) * size.width * .08f
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(230, 236, 242).copy(alpha = if (night) .16f else .32f), Color.Transparent), y - size.height * .12f, y + size.height * .12f), Offset(off - size.width * .1f, y - size.height * .12f), androidx.compose.ui.geometry.Size(size.width * 1.2f, size.height * .24f))
        }
        if (drops.isNotEmpty()) {
            val col = if (night) Color(170, 190, 230, 82) else Color(225, 235, 250, 128)
            for (d in drops) {
                val y = ((d[1] * size.height + t * d[3]) % (size.height + 30)) - 20; val x = ((d[0] * size.width - t * d[3] * .26f) % (size.width * 1.2f) + size.width * 1.2f) % (size.width * 1.2f)
                drawLine(col, Offset(x, y), Offset(x + d[2] * .26f, y - d[2]), 1.1f)
            }
        }
        for (f in flakes) {
            val y = (f[1] * size.height + t * f[3]) % (size.height + 8) - 4; val x = f[0] * size.width + sin(t * .9f + f[4]) * 14
            drawCircle(if (night) Color(235, 240, 255, 190) else Color(255, 255, 255, 235), f[2], Offset(x, y))
        }
        if (s.cond == "storm") {
            val e = (t * 1000 % 9000)
            if (e < 420) {
                val a = if (e < 80) .38f else if (e < 140) .08f else if (e < 200) .26f else .26f * (1 - (e - 200) / 220)
                drawRect(Color(235, 240, 255).copy(alpha = a.coerceIn(0f, 1f)))
                if (e < 200) {
                    val rnd = Random((t / 9).toInt()); var bx = size.width * (.15f + rnd.nextFloat() * .7f); var by = 0f
                    val path = Path().apply { moveTo(bx, by); while (by < size.height * .55f) { bx += rnd.nextFloat() * 56 - 28; by += 18 + rnd.nextFloat() * 24; lineTo(bx, by) } }
                    drawPath(path, Color.White.copy(alpha = .95f), style = Stroke(2f))
                }
            }
        }
    }
}

private fun DrawScope.cloud(x: Float, y: Float, w: Float, h: Float, c: Color) {
    for (i in 0 until 8) {
        val px = x + w * (.18f + .64f * i / 7f); val rr = h * (.6f + .25f * sin(i * 1.7f)) * (1 - abs(i / 7f - .5f) * .6f); val py = y + h * .58f - rr * .3f
        drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = c.alpha * .7f), Color.Transparent), Offset(px, py - rr * .2f), rr), rr, Offset(px, py))
    }
}

/**
 * Fond qui réagit à la journée : une lueur chaude monte du bas de l'écran au fil des validations ([progress] de 0 à 1),
 * et des confettis tombent doucement au fond les journées parfaites.
 */
@Composable
fun ReactiveBg(progress: Float, perfect: Boolean) {
    val p = LocalPal.current
    if (p.transp || p.contrast) return
    val k by androidx.compose.animation.core.animateFloatAsState(progress.coerceIn(0f, 1f), androidx.compose.animation.core.tween(if (p.motion) 0 else 1200), label = "reactive")
    if (k > .01f) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val c = androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * 1.05f); val r = size.height * (.35f + .45f * k)
        drawRect(androidx.compose.ui.graphics.Brush.radialGradient(0f to p.flame.copy(alpha = .26f * k), .5f to p.pink.copy(alpha = .1f * k), 1f to Color.Transparent, center = c, radius = r))
    }
    if (perfect && Perf.flags.fx && !p.motion) ThemeFx("confetti")
}
