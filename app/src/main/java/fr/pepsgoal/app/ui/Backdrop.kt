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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import kotlin.math.sin
import kotlin.random.Random

/** Image d'arrière-plan personnalisée, gardée sur l'appareil. */
fun bgFile(ctx: android.content.Context) = File(ctx.filesDir, "bg.jpg")

/** Arrière-plan derrière toute l'application (calque `.backdrop` de la version web). */
@Composable
fun Backdrop(kind: String, sky: Weather.Sky?, snow: Boolean, bgStamp: Long, fx: String = "") {
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
            else -> if (kind.startsWith("s:")) {
                // Scène illustrée de la boutique, sous un léger voile aux couleurs du thème (lisibilité des cartes)
                SceneBg(kind.removePrefix("s:"))
                Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = if (p.dark) .28f else .18f)))
            } else Blobs()
        }
        if (snow || fx == "snow") SnowFx() else if (fx.isNotEmpty() && !p.motion) ThemeFx(fx)
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

/** Grain très léger (une seule commande de dessin) : donne de la matière aux fonds unis et dégradés. */
private val GRAIN = Random(3).let { r -> List(2600) { Offset(r.nextFloat(), r.nextFloat()) } }
private fun DrawScope.grain(ink: Color) {
    val pts = GRAIN.map { Offset(it.x * size.width, it.y * size.height) }
    drawPoints(pts, androidx.compose.ui.graphics.PointMode.Points, ink.copy(alpha = .035f), 1.5f)
}
private fun DrawScope.halo(c: Color, x: Float, y: Float, r: Float) { if (r > 0f) drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), Offset(x, y), r), r, Offset(x, y)) }
/** Courbe somme de sinus (amplitude, fréquence, phase…) ; [closed] : remplie jusqu'en bas. */
private fun DrawScope.wavePath(base: Float, w: FloatArray, closed: Boolean): Path {
    val W = size.width; val p = Path(); var x = 0f; var first = true
    while (x <= W + 3) {
        var y = base; var i = 0; while (i < w.size) { y += w[i] * sin(x / W * w[i + 1] * 2 * PI.toFloat() + w[i + 2]); i += 3 }
        if (first) { if (closed) { p.moveTo(0f, size.height); p.lineTo(x, y) } else p.moveTo(x, y); first = false } else p.lineTo(x, y)
        x += 3f
    }
    if (closed) { p.lineTo(W, size.height); p.close() }
    return p
}

/** Intensité des couleurs : renforcée en clair (sinon le fond paraît terne derrière le verre des cartes). */
private fun Pal.al(): Float = if (dark) blobAlpha else min(1f, blobAlpha * 1.55f)
private fun Color.withA(v: Float) = copy(alpha = v.coerceIn(0f, 1f))

/** Lumière d'ensemble : en clair, lavis coloré et reflet nacré en diagonale ; en sombre, lueur haute et coins assombris. */
private fun DrawScope.sheen(p: Pal) {
    val W = size.width; val H = size.height
    if (!p.dark) {
        drawRect(Brush.verticalGradient(0f to p.blobs[2].withA(.07f), .5f to p.blobs[1].withA(.03f), 1f to p.blobs[0].withA(.07f)))
        drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .42f), Color.White.copy(alpha = 0f)), start = Offset.Zero, end = Offset(W * .7f, H * .5f)))
    } else {
        halo(p.blobs[3].withA(.07f), W * .5f, -H * .1f, maxOf(W, H) * .6f)
        drawRect(Brush.radialGradient(0f to Color.Transparent, .6f to Color.Transparent, 1f to Color.Black.copy(alpha = .35f), center = Offset(W * .5f, H * .45f), radius = maxOf(W, H) * .8f))
    }
}

@Composable
private fun Blobs() {
    val p = LocalPal.current; val t = anim(26000)
    Canvas(Modifier.fillMaxSize()) {
        val m = maxOf(size.width, size.height); val a = (t * 2 * PI).toFloat(); val W = size.width; val H = size.height; val al = p.al()
        // Grandes nappes de couleur qui dérivent, petites nappes plus denses devant (profondeur), reflet clair
        halo(p.blobs[0].withA(al), -.02f * m + sin(a) * 30, .06f * m + cos(a) * 40, .62f * m)
        halo(p.blobs[1].withA(al), W + .02f * m + cos(a) * 30, .12f * H + .27f * m + sin(a) * 50, .56f * m)
        halo(p.blobs[2].withA(al), .1f * W + .26f * m + sin(a + 1) * 40, H + .02f * m, .55f * m)
        halo(p.blobs[3].withA(al * .6f), .4f * W + .15f * m + cos(a + 2) * 50, .02f * H + .15f * m, .32f * m)
        halo(p.blobs[1].withA(al * .35f), .5f * W + sin(a * 2) * W * .1f, .55f * H, .3f * m)
        halo(p.blobs[3].withA(al * .7f), .72f * W + cos(a * 1.5f) * W * .08f, .38f * H + sin(a) * H * .04f, .16f * m)
        halo(p.blobs[2].withA(al * .6f), .22f * W + sin(a * 1.3f) * W * .06f, .7f * H, .14f * m)
        halo(Color.White.withA(al * if (p.dark) .12f else .3f), .3f * W, .25f * H, .25f * m)
        sheen(p); grain(p.ink)
    }
}

@Composable
private fun AuroraBg() {
    val p = LocalPal.current; val t = anim(18000)
    val dust = remember { val r = Random(21); List(90) { floatArrayOf(r.nextFloat(), r.nextFloat() * .7f, .4f + r.nextFloat() * 1.2f, .15f + r.nextFloat() * .35f) } }
    Canvas(Modifier.fillMaxSize()) {
        val W = size.width; val H = size.height; val a = (t * 2 * PI).toFloat(); val al = p.al()
        drawRect(Brush.verticalGradient(0f to p.blobs[2].withA(al * .3f), .6f to Color.Transparent))
        if (p.dark) for (d in dust) drawCircle(p.ink.withA(d[3]), d[2] * density / 2.6f, Offset(d[0] * W, d[1] * H))
        // Voiles fins : bandes verticales lumineuses en haut, qui s'estompent vers le bas
        for (i in 0 until 4) {
            val yc = H * (.14f + i * .13f); val col = p.blobs[i % 4]; var x = 0f
            while (x < W) {
                val y = yc + sin(x / W * 3 + i * 1.7f + a) * H * .04f + sin(x / W * 8 + i) * H * .012f
                val h = H * (.14f + .06f * sin(x / W * 5 + i + a))
                drawRect(Brush.verticalGradient(0f to col.withA(0f), .18f to col.withA(al * .7f), .3f to col.withA(al * .3f), 1f to col.withA(0f), startY = y, endY = y + h),
                    Offset(x, y), androidx.compose.ui.geometry.Size(4f, h))
                x += 3f
            }
        }
        halo(p.blobs[2].withA(al * .25f), W * .5f, H * .95f, maxOf(W, H) * .5f)
        sheen(p); grain(p.ink)
    }
}

@Composable
private fun WavesBg() {
    val p = LocalPal.current; val t = anim(14000)
    val glints = remember { val r = Random(8); List(40) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    Canvas(Modifier.fillMaxSize()) {
        val W = size.width; val H = size.height; val S = minOf(W, H); val al = p.al()
        halo(p.blobs[3].withA(al * .45f), W * .8f, H * .2f, S * .8f); halo(p.blobs[2].withA(al * .35f), W * .1f, H * .35f, S * .7f)
        drawCircle(p.blobs[3].withA(al * .8f), S * .07f, Offset(W * .8f, H * .2f))
        for (i in 0 until 4) {
            // Houle à trois fréquences, crête soulignée d'un reflet clair
            val base = H * (.58f + i * .1f); val ph = (t * 2 * PI).toFloat() * (1 + i * .3f); val col = p.blobs[i % 4]
            val w = floatArrayOf(H * (.03f - i * .004f), 1.1f, ph + i, H * .012f, 2.6f, ph * 1.3f + i * 2, H * .004f, 5.3f, -ph)
            drawPath(wavePath(base, w, true), Brush.verticalGradient(0f to col.withA(al * (.6f + i * .1f)), 1f to col.withA(al * .14f), startY = base - H * .04f, endY = H))
            drawPath(wavePath(base, w, false), Color.White.copy(alpha = if (p.dark) .22f else .5f), style = Stroke(1.5f))
        }
        // Éclats du soleil sur la houle
        glints.forEachIndexed { i, g ->
            val y = H * (.6f + g[0] * .38f); val x = W * .8f + (g[1] - .5f) * S * .3f * (y / H); val w = S * (.01f + g[2] * .03f)
            drawLine(Color.White.withA((.15f + g[3] * .3f) * (.5f + .5f * sin((t * 2 * PI).toFloat() * 3 + i))), Offset(x, y), Offset(x + w, y), 1.5f)
        }
        sheen(p); grain(p.ink)
    }
}

@Composable
private fun StarsBg() {
    val p = LocalPal.current; val t = anim(12000)
    val stars = remember { val r = Random(13); List(180) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat().let { it * it * kotlin.math.sqrt(it) }) } }
    // Voie lactée : poussière d'étoiles le long d'une bande diagonale
    val milky = remember { val r = Random(19); List(260) { val u = r.nextFloat(); val v = (r.nextFloat() + r.nextFloat() + r.nextFloat() - 1.5f) * .12f; floatArrayOf(u * 1.2f - .1f, .85f - u * .7f + v, .35f + r.nextFloat() * .7f, .12f + r.nextFloat() * .25f) } }
    val tints = listOf(Color(0xFFFFE7B0), Color(0xFFB9CCFF))
    Canvas(Modifier.fillMaxSize()) {
        val W = size.width; val H = size.height; val S = minOf(W, H); val a = (t * 2 * PI).toFloat(); val al = p.al()
        halo(p.blobs[2].withA(al * .5f), W * .7f, H * .3f, S * .9f); halo(p.blobs[1].withA(al * .4f), W * .2f, H * .7f, S * .8f)
        halo(p.blobs[3].withA(al * .18f), W * .5f, H * .5f, S * .4f)
        for (m in milky) drawCircle(p.ink.withA(m[3]), m[2] * density / 2.6f, Offset(m[0] * W, m[1] * H))
        halo(p.ink.withA(.04f), W * .5f, H * .5f, S * .5f)
        stars.forEachIndexed { i, (x, y, s) ->
            val tw = .5f + .5f * sin(a * (1 + i % 3) + i)
            val col = if (p.dark && i % 3 > 0) tints[i % 3 - 1] else p.ink
            drawCircle(col.withA((.2f + .7f * s) * (.55f + .45f * tw)), .6f + 2.4f * s * density / 2.6f, Offset(x * W, y * H))
            if (s > .6f) halo(col.withA(.15f * tw), x * W, y * H, (6 + 10 * s) * density / 2.6f)
        }
        val k = (t * 2) % 1f
        if (k < .15f && !p.motion) { val q = k / .15f; val x = W * (.85f - q * .5f); val y = H * (.1f + q * .2f); drawLine(p.ink.withA((1 - q) * .8f), Offset(x, y), Offset(x + S * .12f, y - S * .06f), 1.5f, StrokeCap.Round) }
        sheen(p); grain(p.ink)
    }
}

@Composable
private fun MeshBg() {
    val p = LocalPal.current; val t = anim(30000)
    Canvas(Modifier.fillMaxSize()) {
        val W = size.width; val H = size.height; val m = maxOf(W, H); val a = (t * 2 * PI).toFloat(); val al = p.al()
        // Dégradé « mesh » : quatre foyers de couleur qui dérivent et se mélangent, et un foyer central plus doux
        listOf(Triple(0, .2f to .2f, 0f), Triple(1, .85f to .25f, 1.6f), Triple(2, .25f to .8f, 3.1f), Triple(3, .8f to .75f, 4.7f), Triple(1, .5f to .5f, 2.4f)).forEachIndexed { i, (ci, pos, ph) ->
            halo(p.blobs[ci].withA(al * if (i == 4) .5f else 1f), W * (pos.first + .12f * cos(a + ph)), H * (pos.second + .08f * sin(a + ph)), m * if (i == 4) .45f else .75f)
        }
        sheen(p); grain(p.ink)
    }
}

@Composable
private fun GridBg() {
    val p = LocalPal.current; val t = anim(16000)
    Canvas(Modifier.fillMaxSize()) {
        val W = size.width; val H = size.height; val S = minOf(W, H); val hz = H * .42f; val al = p.al()
        // Grille en perspective jusqu'à l'horizon, soleil couchant rayé et ligne d'horizon lumineuse
        drawRect(Brush.verticalGradient(0f to p.bg, .42f to p.blobs[1].withA(al * .4f), .43f to p.blobs[2].withA(al * .25f), 1f to p.bg))
        halo(p.blobs[0].withA(al * .65f), W * .5f, hz, S * .5f)
        val sr = S * .14f; val sy = hz - S * .02f
        drawCircle(Brush.verticalGradient(listOf(p.blobs[3].withA(al * 1.2f), p.blobs[1].withA(al * 1.1f)), startY = sy - sr, endY = sy + sr), sr, Offset(W * .5f, sy))
        for (b in 0 until 5) {
            val y = sy + sr * (.1f + b * .18f); val hh = sr * .035f * (b + 1); val half = kotlin.math.sqrt(maxOf(0f, sr * sr - (y - sy) * (y - sy)))
            drawRect(p.bg, Offset(W * .5f - half, y), androidx.compose.ui.geometry.Size(half * 2, hh))
        }
        for (i in -14..14) drawLine(p.ink.withA(if (p.dark) .1f else .12f), Offset(W * .5f + i * S * .02f, hz), Offset(W * .5f + i * S * .3f, H), 1f)
        for (k in 0 until 14) { val q = ((k + t) % 14f) / 14f; val y = hz + (H - hz) * q * q; drawLine(p.ink.withA(.03f + .1f * q), Offset(0f, y), Offset(W, y), 1f) }
        drawLine(p.blobs[3].withA(al), Offset(0f, hz), Offset(W, hz), 2f); halo(p.blobs[1].withA(al * .12f), W * .5f, hz, S * .9f)
        sheen(p); grain(p.ink)
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

/** Effets des thèmes de la boutique : pétales, feuilles, cœurs et confettis tombent ; braises et bulles montent ; étoiles scintillent. */
@Composable
private fun ThemeFx(kind: String) {
    val rnd = remember(kind) { Random(kind.hashCode()) }
    val parts = remember(kind) { List(if (kind == "stars") 60 else 34) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), .6f + rnd.nextFloat() * .9f, .35f + rnd.nextFloat() * .5f, 10 + rnd.nextFloat() * 16, rnd.nextFloat() * 6.28f, rnd.nextInt(4).toFloat()) } }
    val t = anim(30000, fps = 24)
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
