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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.Weather
import kotlinx.coroutines.isActive
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Image d'arrière-plan personnalisée, gardée sur l'appareil. */
fun bgFile(ctx: android.content.Context) = File(ctx.filesDir, "bg.jpg")

/** Arrière-plan derrière toute l'application (calque `.backdrop` de la version web). */
@Composable
fun Backdrop(kind: String, sky: Weather.Sky?, snow: Boolean, bgStamp: Long) {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize().background(p.bg)) {
        if (p.transp || p.contrast) return@Box
        if (sky != null) { SkyCanvas(sky); return@Box }
        when (kind) {
            "custom" -> CustomBg(bgStamp)
            "plain" -> {}
            "grid" -> GridBg()
            "stars" -> StarsBg()
            "waves" -> WavesBg()
            "aurora" -> AuroraBg()
            "mesh" -> MeshBg()
            else -> Blobs()
        }
        if (snow) SnowFx()
    }
}

/**
 * Horloge des fonds animés (0 → 1 sur [dur] ms). Les fonds bougent lentement : quelques images par seconde
 * suffisent, et le verre givré des cartes n'a pas à être recalculé à chaque image (fluidité du défilement).
 */
@Composable
private fun anim(dur: Int, fps: Int = 12): Float = slowPhase(dur, fps)

/**
 * Phase 0 → 1 sur [dur] ms, mise à jour [fps] fois par seconde seulement. Entre deux images, aucune demande de
 * rafraîchissement n'est faite (simple attente) : l'écran peut rester au repos, ce qui économise beaucoup de batterie
 * par rapport aux animations infinies à 60/120 images par seconde. En arrière-plan, l'attente de l'image suspend tout.
 */
@Composable
fun slowPhase(dur: Int, fps: Int = 12, offset: Int = 0): Float = slowPhaseState(dur, fps, offset).value

/** Même horloge, à lire dans la phase de dessin (graphicsLayer, drawWithContent) : pas de recomposition à chaque image. */
@Composable
fun slowPhaseState(dur: Int, fps: Int = 12, offset: Int = 0): State<Float> {
    val p = LocalPal.current
    val v = remember { mutableFloatStateOf(0f) }
    if (p.motion) return v
    LaunchedEffect(dur, fps) {
        val step = 1000L / fps; var t0 = -1L
        while (isActive) {
            withFrameNanos { n -> if (t0 < 0) t0 = n; v.floatValue = (((n - t0) / 1_000_000L + offset) % dur) / dur.toFloat() }
            kotlinx.coroutines.delay(step)
        }
    }
    return v
}

private fun blurMod() = if (Build.VERSION.SDK_INT >= 31) Modifier.blur(60.dp) else Modifier

@Composable
private fun Blobs() {
    val p = LocalPal.current; val t = anim(26000)
    Canvas(Modifier.fillMaxSize().then(blurMod())) {
        val m = maxOf(size.width, size.height); val a = (t * 2 * PI).toFloat()
        fun blob(c: Color, cx: Float, cy: Float, r: Float, alpha: Float) = drawCircle(Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        blob(p.blobs[0], -.02f * m + sin(a) * 30, .06f * m + cos(a) * 40, .6f * m, p.blobAlpha)
        blob(p.blobs[1], size.width + .02f * m + cos(a) * 30, .12f * size.height + .27f * m + sin(a) * 50, .54f * m, p.blobAlpha)
        blob(p.blobs[2], .1f * size.width + .26f * m + sin(a + 1) * 40, size.height + .02f * m, .52f * m, p.blobAlpha)
        blob(p.blobs[3], .4f * size.width + .15f * m + cos(a + 2) * 50, .02f * size.height + .15f * m, .3f * m, p.blobAlpha * .6f)
    }
}

@Composable
private fun AuroraBg() {
    val p = LocalPal.current; val t = anim(18000)
    Canvas(Modifier.fillMaxSize().then(blurMod())) {
        for (i in 0 until 3) {
            val y = size.height * (.18f + i * .17f) + sin((t * 2 * PI + i).toFloat()) * 60
            val path = Path().apply {
                moveTo(-100f, y)
                cubicTo(size.width * .3f, y - 160, size.width * .6f, y + 160, size.width + 100, y - 40)
                lineTo(size.width + 100, y + 120); cubicTo(size.width * .6f, y + 260, size.width * .3f, y - 40, -100f, y + 140); close()
            }
            drawPath(path, Brush.horizontalGradient(listOf(p.blobs[i % 4].copy(alpha = p.blobAlpha), p.blobs[(i + 1) % 4].copy(alpha = p.blobAlpha * .7f), Color.Transparent)))
        }
    }
}

@Composable
private fun WavesBg() {
    val p = LocalPal.current; val t = anim(14000)
    Canvas(Modifier.fillMaxSize()) {
        for (i in 0 until 3) {
            val base = size.height * (.62f + i * .12f); val amp = 46f - i * 8; val ph = (t * 2 * PI).toFloat() * (1 + i * .3f)
            val path = Path().apply {
                moveTo(0f, base)
                var x = 0f
                while (x <= size.width + 20) { lineTo(x, base + sin(x / size.width * 4 * PI.toFloat() + ph) * amp); x += 12f }
                lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(path, p.blobs[i].copy(alpha = p.blobAlpha * (.5f + i * .15f)))
        }
    }
}

@Composable
private fun StarsBg() {
    val p = LocalPal.current; val t = anim(6000)
    val stars = remember { List(120) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(p.blobs[2].copy(alpha = p.blobAlpha * .4f), Color.Transparent, p.blobs[1].copy(alpha = p.blobAlpha * .3f))))
        stars.forEachIndexed { i, (x, y, s) ->
            val tw = .5f + .5f * sin((t * 2 * PI).toFloat() * (1 + s) + i)
            drawCircle(p.ink.copy(alpha = .25f + .55f * tw * s), 1f + 2.2f * s, Offset(x * size.width, y * size.height))
        }
        for (i in 0 until stars.size - 1 step 9) {
            val a = stars[i]; val b = stars[i + 1]
            drawLine(p.ink.copy(alpha = .08f), Offset(a.first * size.width, a.second * size.height), Offset(b.first * size.width, b.second * size.height), 1f)
        }
    }
}

@Composable
private fun MeshBg() {
    val p = LocalPal.current; val t = anim(30000)
    Canvas(Modifier.fillMaxSize()) {
        val a = (t * 2 * PI).toFloat()
        drawRect(Brush.linearGradient(listOf(p.blobs[0].copy(alpha = p.blobAlpha), p.blobs[1].copy(alpha = p.blobAlpha * .8f), p.blobs[2].copy(alpha = p.blobAlpha)),
            start = Offset(size.width * (.5f + .5f * cos(a)), 0f), end = Offset(size.width * (.5f - .5f * cos(a)), size.height)))
    }
}

@Composable
private fun GridBg() {
    val p = LocalPal.current
    Canvas(Modifier.fillMaxSize()) {
        val step = 34.dp.toPx(); var x = 0f
        while (x < size.width) { drawLine(p.ink.copy(alpha = .07f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(p.ink.copy(alpha = .07f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
        drawCircle(Brush.radialGradient(listOf(p.flame.copy(alpha = p.blobAlpha * .5f), Color.Transparent)), size.minDimension * .7f, Offset(size.width * .8f, size.height * .15f))
    }
}

@Composable
private fun CustomBg(stamp: Long) {
    val ctx = LocalContext.current; val p = LocalPal.current
    val bmp = remember(stamp) { bgFile(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    if (bmp == null) { Blobs(); return }
    // Flou et assombrissement réglables (Paramètres › Apparence) ; par défaut, un léger voile aux couleurs du thème
    val raw = ctx.getSharedPreferences("pepsgoal.device", android.content.Context.MODE_PRIVATE)
    val blur = remember(stamp) { raw.getInt("bgBlur", 0) }; val dim = remember(stamp) { raw.getFloat("bgDim", -1f) }
    Image(bmp, null, Modifier.fillMaxSize().then(if (blur > 0 && Build.VERSION.SDK_INT >= 31) Modifier.blur(blur.dp) else Modifier), contentScale = ContentScale.Crop)
    Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = if (dim >= 0f) dim else if (p.dark) .45f else .3f)))
}

@Composable
private fun SnowFx() {
    val flakes = remember { List(46) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), 2 + Random.nextFloat() * 5, .45f + Random.nextFloat() * .5f, 9 + Random.nextFloat() * 14) } }
    val t = anim(20000, fps = 24)
    Canvas(Modifier.fillMaxSize()) {
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
    LaunchedEffect(s.phase, s.cond, p.motion) {
        if (p.motion) return@LaunchedEffect
        var last = 0L
        // 12 images par seconde : nuages et pluie restent fluides, l'écran et le flou des cartes travaillent deux fois moins
        while (isActive) { withFrameNanos { n -> frame = n }; kotlinx.coroutines.delay(80) }
    }
    val seed = remember(s.phase, s.cond, s.code) { Random(s.code * 31 + s.phase.hashCode()) }
    val stars = remember(seed) { List(180) { floatArrayOf(seed.nextFloat(), seed.nextFloat() * .78f, .35f + seed.nextFloat() * 1.1f, seed.nextFloat() * 6.28f, .5f + seed.nextFloat() * 1.2f) } }
    val clouds = remember(seed) {
        val n = when (s.cond) { "clear" -> if (s.clouds > 30) 3 else 1; "fog" -> 3; "clouds" -> 4 + (s.clouds / 22).toInt(); else -> 9 }
        List(n) { i -> floatArrayOf(seed.nextFloat(), -.05f + seed.nextFloat() * .47f, (i % 3).toFloat(), .8f + seed.nextFloat() * .5f, .6f + seed.nextFloat() * .5f) }
    }
    val drops = remember(seed) { if (s.cond == "rain" || s.cond == "storm") List((160 * precip(s.code)).toInt()) { floatArrayOf(seed.nextFloat() * 1.2f, seed.nextFloat(), 10 + seed.nextFloat() * 12, 650 + seed.nextFloat() * 300) } else emptyList() }
    val flakes = remember(seed) { if (s.cond == "snow") List((110 * precip(s.code)).toInt()) { floatArrayOf(seed.nextFloat(), seed.nextFloat(), 1 + seed.nextFloat() * 2.3f, 16 + seed.nextFloat() * 26, seed.nextFloat() * 6.28f) } else emptyList() }
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
