package fr.pepsgoal.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.SHOP_BGS
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Arrière-plans illustrés de la boutique (réglage « bg » = « s:<clé> ») : des scènes vectorielles dessinées sur
 * Canvas, animées très lentement (horloge économe de Backdrop.kt, lue dans la phase de dessin). Le même composable sert
 * d'aperçu miniature dans la boutique : tout est proportionnel à la taille disponible.
 */

private const val TAU = 6.2831855f

/** Générateur pseudo-aléatoire stable (mulberry32) : la même scène à chaque image, sans rien stocker. */
private class Mul(seed: Int) {
    private var a = seed
    operator fun invoke(): Float {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }
}

/** Scène illustrée [key] en plein cadre ; [still] fige l'animation (aperçus de la boutique). */
@Composable
fun SceneBg(key: String, modifier: Modifier = Modifier, still: Boolean = false) {
    val phase: State<Float> = if (still) remember { mutableFloatStateOf(.3f) } else slowPhaseState(SCENE_DUR[key] ?: 40000, 12)
    Canvas(modifier.fillMaxSize()) {
        val t = phase.value
        when (key) {
            "nebula" -> nebula(t); "deepsea" -> deepsea(t); "mountains" -> mountains(t); "citynight" -> citynight(t); "tram" -> tram(t)
            "mistforest" -> mistforest(t); "dunes" -> dunes(t); "sakura" -> sakura(t); "magma" -> magma(t); "coderain" -> coderain(t)
            "borealis" -> borealis(t); "rainwindow" -> rainwindow(t); "planet" -> planet(t); "lagoon" -> lagoon(t); "haunted" -> haunted(t)
            "snowvillage" -> snowvillage(t); "fireworks" -> fireworks(t); "snowpeak" -> snowpeak(t); "heartsky" -> heartsky(t)
            "eggmeadow" -> eggmeadow(t); "flowerfield" -> flowerfield(t); "summerbeach" -> summerbeach(t); "autumnwood" -> autumnwood(t)
            else -> {
                val sky = SHOP_BGS.firstOrNull { it.key == key }?.sky ?: listOf("#1A1240", "#07081A")
                drawRect(Brush.verticalGradient(sky.map(::hex)))
            }
        }
        finish(SHOP_BGS.firstOrNull { it.key == key }?.dark ?: true)
    }
}

/** Finition commune : vignettage doux (profondeur, regard vers le centre) et grain très léger (matière). */
private val SCENE_GRAIN = Mul(17).let { r -> List(1800) { Offset(r(), r()) } }
private fun DrawScope.finish(dark: Boolean) {
    val W = size.width; val H = size.height; val R = max(W, H) * .78f
    drawRect(Brush.radialGradient(0f to Color.Transparent, .62f to Color.Transparent, 1f to Color.Black.copy(alpha = if (dark) .34f else .12f), center = Offset(W * .5f, H * .45f), radius = R))
    drawPoints(SCENE_GRAIN.map { Offset(it.x * W, it.y * H) }, androidx.compose.ui.graphics.PointMode.Points, if (dark) Color.White.copy(alpha = .045f) else Color.Black.copy(alpha = .035f), 1.4f)
}

/** Durée d'un cycle d'animation par scène (ms) : les scènes à mouvement visible (tram, poissons) tournent plus vite. */
private val SCENE_DUR = mapOf("tram" to 30000, "deepsea" to 60000, "coderain" to 20000, "fireworks" to 24000, "rainwindow" to 50000)

/* ------------------------------------------------------------------ primitives (mêmes noms que le prototype) */

private fun c(h: String, a: Float = 1f): Color = hex(h).copy(alpha = a.coerceIn(0f, 1f))
private fun DrawScope.vgrad(vararg stops: Pair<Float, Color>, y0: Float = 0f, y1: Float = size.height) =
    drawRect(Brush.verticalGradient(*stops, startY = y0, endY = y1), Offset(0f, y0), Size(size.width, y1 - y0))
private fun DrawScope.disc(x: Float, y: Float, r: Float, col: Color) = drawCircle(col, max(r, .01f), Offset(x, y))
private fun DrawScope.ring(x: Float, y: Float, r: Float, w: Float, col: Color) = drawCircle(col, max(r, .01f), Offset(x, y), style = Stroke(w))
private fun DrawScope.glow(x: Float, y: Float, r: Float, col: Color) {
    if (r <= 0f) return
    drawCircle(Brush.radialGradient(listOf(col, col.copy(alpha = 0f)), Offset(x, y), r), r, Offset(x, y))
}
private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, col: Color) = drawOval(col, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))
private fun DrawScope.rect(x: Float, y: Float, w: Float, h: Float, col: Color) = drawRect(col, Offset(x, y), Size(w, h))
private fun DrawScope.rrect(x: Float, y: Float, w: Float, h: Float, r: Float, col: Color) = drawRoundRect(col, Offset(x, y), Size(w, h), CornerRadius(r, r))
private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, col: Color) = drawLine(col, Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round)
/** Polygone fermé : [pts] = x1, y1, x2, y2… */
private fun DrawScope.poly(col: Color, vararg pts: Float) {
    val p = Path(); p.moveTo(pts[0], pts[1]); var i = 2
    while (i < pts.size) { p.lineTo(pts[i], pts[i + 1]); i += 2 }
    p.close(); drawPath(p, col)
}
/** Relief : courbe somme de sinus ([waves] = amplitude, fréquence, phase…), rempli jusqu'en bas. */
private fun DrawScope.ridgePath(y0: Float, waves: FloatArray): Path {
    val W = size.width; val H = size.height; val p = Path(); p.moveTo(0f, H)
    var x = 0f; val step = max(2f, W / 120f)
    while (x <= W + 4) { var y = y0; var i = 0; while (i < waves.size) { y += waves[i] * sin(x / W * waves[i + 1] * TAU + waves[i + 2]); i += 3 }; p.lineTo(x, y); x += step }
    p.lineTo(W, H); p.close(); return p
}
private fun DrawScope.ridge(y0: Float, waves: FloatArray, col: Color) = drawPath(ridgePath(y0, waves), col)
private fun DrawScope.ridge(y0: Float, waves: FloatArray, yTop: Float, yBot: Float, vararg stops: Pair<Float, Color>) =
    drawPath(ridgePath(y0, waves), Brush.verticalGradient(*stops, startY = yTop, endY = yBot))
private fun DrawScope.peaks(col: Color, vararg pts: Float) = poly(col, 0f, size.height, *pts, size.width, size.height)
private fun DrawScope.pine(x: Float, yb: Float, h: Float, col: Color) {
    val w = h * .42f
    poly(col, x, yb - h, x + w * .5f, yb - h * .45f, x + w * .25f, yb - h * .45f, x + w * .62f, yb, x - w * .62f, yb, x - w * .25f, yb - h * .45f, x - w * .5f, yb - h * .45f)
}
private fun DrawScope.heart(x: Float, y: Float, s: Float, col: Color) {
    val p = Path(); p.moveTo(x, y + s * .9f)
    p.cubicTo(x - s * 1.4f, y, x - s * .6f, y - s * 1.1f, x, y - s * .35f)
    p.cubicTo(x + s * .6f, y - s * 1.1f, x + s * 1.4f, y, x, y + s * .9f)
    drawPath(p, col)
}
private fun DrawScope.sparkle(x: Float, y: Float, r: Float, col: Color) =
    poly(col, x, y - r, x + r * .22f, y - r * .22f, x + r, y, x + r * .22f, y + r * .22f, x, y + r, x - r * .22f, y + r * .22f, x - r, y, x - r * .22f, y - r * .22f)
private fun DrawScope.cloud(x: Float, y: Float, w: Float, col: Color) {
    oval(x, y, w * .5f, w * .16f, col); disc(x - w * .18f, y - w * .08f, w * .17f, col); disc(x + w * .08f, y - w * .13f, w * .22f, col); disc(x + w * .3f, y - w * .04f, w * .13f, col)
}
private fun DrawScope.rot(deg: Float, px: Float, py: Float, block: DrawScope.() -> Unit) = rotate(deg, Offset(px, py), block)
/** Bande verticale en dégradé transparent → couleur → transparent (voiles d'aurore). */
private fun DrawScope.veil(x: Float, y: Float, w: Float, h: Float, col: Color, peak: Float, at: Float = .25f) {
    if (h <= 0f) return
    drawRect(Brush.verticalGradient(0f to col.copy(alpha = 0f), at to col.copy(alpha = peak), 1f to col.copy(alpha = 0f), startY = y, endY = y + h), Offset(x, y), Size(w, h))
}

/* ------------------------------------------------------------------ scènes */

private fun DrawScope.nebula(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(11)
    vgrad(0f to c("#0B0628"), .55f to c("#24104F"), 1f to c("#3B1D6E"))
    glow(W * .25f, H * .62f, S * 1f, c("#C14BA8", .55f)); glow(W * .85f, H * .45f, S * .8f, c("#5B3DFF", .45f)); glow(W * .6f, H * .85f, S * .9f, c("#FF6FB5", .35f))
    glow(W * .45f, H * .7f, S * .5f, c("#FFD1F0", .18f))
    for (i in 0 until 140) { val x = r() * W; val y = r() * H; val s = r(); val tw = .5f + .5f * sin(t * TAU * 2 + i); disc(x, y, (.5f + s * 1.6f) * S / 400, c("#FFFFFF", (.25f + .6f * s) * (.6f + .4f * tw))) }
    for (i in 0 until 5) { val x = r() * W; val y = r() * H; sparkle(x, y, S * (.012f + r() * .012f), c("#FFFFFF", .8f)) }
    val k = (t * 3) % 1f
    if (k < .25f) { val p = k / .25f; val x = W * (.9f - p * .7f); val y = H * (.12f + p * .18f); line(x, y, x + S * .16f, y - S * .08f, S / 300, c("#FFFFFF", (1 - p) * .9f)) }
}

private fun DrawScope.deepsea(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(21)
    vgrad(0f to c("#1C88BE"), .35f to c("#0C5C8A"), .75f to c("#063A5C"), 1f to c("#021829"))
    for (i in 0 until 5) { val x = W * (.1f + i * .22f) + sin(t * TAU + i) * W * .03f; poly(c("#BFF3FF", .06f), x - W * .03f, 0f, x + W * .05f, 0f, x + W * .25f, H * .8f, x - W * .05f, H * .8f) }
    ridge(H * .9f, floatArrayOf(H * .02f, 2f, 1f, H * .01f, 7f, 2f), H * .85f, H, 0f to c("#0B3A4E"), 1f to c("#04161F"))
    for (i in 0 until 7) {
        val x = W * (.05f + r() * .9f); val h = H * (.08f + r() * .1f); val sw = sin(t * TAU + i) * S * .02f
        line(x, H, x + sw, H - h, S * .012f, c("#2BD9A0", .55f)); line(x + S * .02f, H, x + S * .02f + sw * 1.4f, H - h * .7f, S * .01f, c("#1FB39A", .5f))
    }
    val fish = listOf(floatArrayOf(.55f, 1f), floatArrayOf(.4f, -1f), floatArrayOf(.7f, 1f), floatArrayOf(.32f, -1f))
    val fc = listOf("#FFB347", "#FF7A9A", "#FFD166", "#7DF9FF")
    fish.forEachIndexed { i, (fy, dir) ->
        val k = (t + i * .27f) % 1f; val x = if (dir > 0) (-.1f + k * 1.2f) * W else (1.1f - k * 1.2f) * W
        val y = H * fy + sin(k * TAU * 2) * S * .02f; val s = S * (.03f + i * .006f); val col = c(fc[i], .9f)
        oval(x, y, s, s * .55f, col); poly(col, x - dir * s * .9f, y, x - dir * s * 1.6f, y - s * .5f, x - dir * s * 1.6f, y + s * .5f)
        disc(x + dir * s * .5f, y - s * .1f, s * .1f, c("#04161F", .9f))
    }
    for (i in 0 until 26) {
        val x = r() * W; val sp = .6f + r(); val k = (t * sp + r()) % 1f; val y = H * (1 - k)
        ring(x + sin(k * 12) * 6, y, S * (.004f + r() * .008f), S / 500, c("#DFFBFF", .45f * (1 - k * .5f)))
    }
}

private fun DrawScope.mountains(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    vgrad(0f to c("#FFE9C9"), .35f to c("#FFD7A8"), .6f to c("#F59E8B"), 1f to c("#6C5B9E"))
    glow(W * .7f, H * .52f, S * .6f, c("#FFF4D6", .9f)); disc(W * .7f, H * .52f, S * .09f, c("#FFF7E6", .95f))
    cloud(W * .25f + t * W * .1f, H * .3f, S * .4f, c("#FFFFFF", .35f))
    peaks(c("#B98AA8", .75f), 0f, H * .62f, W * .18f, H * .5f, W * .34f, H * .6f, W * .55f, H * .46f, W * .78f, H * .58f, W, H * .5f)
    peaks(c("#8C6BA0", .9f), 0f, H * .7f, W * .22f, H * .58f, W * .42f, H * .7f, W * .63f, H * .57f, W * .86f, H * .7f, W, H * .64f)
    poly(c("#FFFFFF", .7f), W * .63f, H * .57f, W * .58f, H * .615f, W * .61f, H * .605f, W * .63f, H * .62f, W * .66f, H * .6f, W * .69f, H * .615f)
    poly(c("#FFFFFF", .7f), W * .22f, H * .58f, W * .18f, H * .615f, W * .21f, H * .607f, W * .235f, H * .62f, W * .26f, H * .605f)
    ridge(H * .8f, floatArrayOf(H * .03f, 1.3f, .5f, H * .012f, 4f, 1f), H * .74f, H, 0f to c("#5B4A86"), 1f to c("#2E2550"))
    ridge(H * .9f, floatArrayOf(H * .025f, 1.1f, 2f, H * .01f, 5f, .3f), H * .85f, H, 0f to c("#3A2D63"), 1f to c("#1C1535"))
    for (i in 0 until 3) {
        val k = (t + i / 3f) % 1f; val x = W * (.1f + k * .8f); val y = H * (.35f + i * .04f) + sin(k * TAU) * 4
        line(x - S * .015f, y, x, y + S * .008f, S / 260, c("#4A3A6A", .7f)); line(x, y + S * .008f, x + S * .015f, y, S / 260, c("#4A3A6A", .7f))
    }
}

private fun DrawScope.citynight(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(31)
    vgrad(0f to c("#060A22"), .5f to c("#141E4A"), .8f to c("#2A2560"), 1f to c("#3D2B6B"))
    for (i in 0 until 80) disc(r() * W, r() * H * .55f, S / 500 * (1 + r() * 2), c("#FFFFFF", .2f + r() * .5f))
    glow(W * .78f, H * .2f, S * .3f, c("#FFF3C4", .25f)); disc(W * .78f, H * .2f, S * .07f, c("#FFF6D6", .95f))
    val layers = listOf(Triple(.62f, "#1E2550", .55f), Triple(.7f, "#151A3C", .8f), Triple(.78f, "#0C1028", 1f))
    layers.forEachIndexed { li, (base, col, wf) ->
        val rr = Mul(40 + li); var x = -S * .05f
        while (x < W) {
            val w = S * (.08f + rr() * .1f); val h = H * (.08f + rr() * .2f) * (1 - li * .2f) + (if (li == 2) H * .05f else 0f); val top = H * base - h
            rect(x, top, w - 2, H - top, c(col))
            if (li > 0) {
                val cols = floor(w / (S * .022f)).toInt(); val rows = floor(h / (S * .03f)).toInt()
                for (a in 0 until cols) for (b in 0 until rows) {
                    val on = rr() < .32f; val fl = sin(t * TAU + a * 3 + b * 7) > .95f
                    if (on && !fl) rect(x + S * .008f + a * S * .022f, top + S * .012f + b * S * .03f, S * .01f, S * .014f, c(if (rr() < .7f) "#FFD27A" else "#9FD8FF", .55f * wf))
                }
            }
            if (rr() < .25f) { rect(x + w * .45f, top - h * .15f, S * .006f, h * .15f, c(col)); if (sin(t * TAU * 4) > 0) disc(x + w * .45f + S * .003f, top - h * .15f, S * .006f, c("#FF4D4D", .9f)) }
            x += w
        }
    }
    rect(0f, H * .92f, W, H * .08f, c("#07091A"))
    glow(W * .5f, H * .8f, S * 1.1f, c("#7A5CFF", .18f)); glow(W * .2f, H * .78f, S * .6f, c("#FFB36B", .12f))
    vgradRect(0f, H * .9f, W, H * .1f, 0f to c("#0B1030"), 1f to c("#04061A"))
    val rf = Mul(5)
    for (i in 0 until 40) {
        val x = rf() * W; val y = H * (.91f + rf() * .08f); val w = S * (.02f + rf() * .06f); val sh = sin(t * TAU * 2 + i) * S * .01f
        val col = if (rf() < .7f) "#FFD27A" else "#9FD8FF"; line(x + sh, y, x + w + sh, y, S * .004f, c(col, .18f + rf() * .25f))
    }
    line(0f, H * .9f, W, H * .9f, S * .003f, c("#FFFFFF", .12f))
}

/* ------------------------------------------------------------------ Ville et tramway : Angers */

private fun DrawScope.hgradRect(x: Float, y: Float, w: Float, h: Float, vararg stops: Pair<Float, Color>) {
    if (w <= 0f || h <= 0f) return
    drawRect(Brush.horizontalGradient(*stops, startX = x, endX = x + w), Offset(x, y), Size(w, h))
}
private fun DrawScope.vgradRect(x: Float, y: Float, w: Float, h: Float, vararg stops: Pair<Float, Color>) {
    if (w <= 0f || h <= 0f) return
    drawRect(Brush.verticalGradient(*stops, startY = y, endY = y + h), Offset(x, y), Size(w, h))
}
/** Dessin limité au polygone [pts] (x1, y1, x2, y2…). */
private fun DrawScope.clipPoly(pts: FloatArray, block: DrawScope.() -> Unit) {
    val p = Path(); p.moveTo(pts[0], pts[1]); var i = 2
    while (i < pts.size) { p.lineTo(pts[i], pts[i + 1]); i += 2 }
    p.close(); clipPath(p) { block() }
}
private val TEXT_PAINT = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD }
private fun DrawScope.text(s: String, x: Float, y: Float, sz: Float, col: Color) {
    TEXT_PAINT.textSize = sz; TEXT_PAINT.color = col.toArgb()
    drawContext.canvas.nativeCanvas.drawText(s, x, y, TEXT_PAINT)
}

/**
 * Angers : château aux tours de schiste rayées de tuffeau, flèches de la cathédrale Saint-Maurice, maisons de tuffeau
 * aux toits d'ardoise, résidences crème à balcons, et une rame Citadis à la livrée arc-en-ciel d'Irigo qui arrive en
 * station, s'arrête puis repart, sur une plateforme en dalles d'ardoise bordée d'un quai à bande podotactile.
 */
private fun DrawScope.tram(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(31)
    // Ciel d'Anjou : bleu tendre, nuages doux
    vgrad(0f to c("#8EC3EE"), .38f to c("#C4DFF5"), .62f to c("#EAF1F6"), 1f to c("#F2EEE6"))
    glow(W * .82f, H * .1f, S * .9f, c("#FFF6DD", .55f))
    fun cl(x: Float, y: Float, w: Float, a: Float) { cloud(x, y, w, c("#FFFFFF", a)); oval(x + w * .05f, y + w * .1f, w * .42f, w * .05f, c("#B9C9DA", a * .35f)) }
    cl(W * .18f + t * W * .2f, H * .12f, S * .62f, .92f); cl(W * .78f - t * W * .12f, H * .2f, S * .5f, .85f); cl(W * .45f + t * W * .08f, H * .31f, S * .34f, .6f)
    // Plan lointain : château et cathédrale, dans la brume
    val fb = H * .705f; val schist = "#55505C"; val tuf = "#E6DAC2"
    ridge(fb, floatArrayOf(S * .01f, 1.3f, .4f), c("#8E9A8A", .9f))
    val cw0 = W * .56f
    rect(-W * .02f, fb - S * .2f, cw0, S * .21f, c(schist))
    for (i in 0 until 5) {
        val tw = S * .12f; val tx = -W * .01f + i * (cw0 - tw) / 4; val th = S * (.31f + (i % 2) * .03f)
        hgradRect(tx, fb - th, tw, th + S * .01f, 0f to c("#3A3641"), .3f to c("#6C6672"), .55f to c("#5A5560"), 1f to c("#34303A"))
        for (b in 0 until 7) { val by = fb - th + th * (.1f + b * .13f); hgradRect(tx, by, tw, th * .035f, 0f to c(tuf, .6f), .35f to c(tuf), 1f to c(tuf, .55f)) }
        rect(tx - tw * .05f, fb - th - S * .01f, tw * 1.1f, S * .014f, c("#46424D"))
        for (cr in 0 until 4) rect(tx - tw * .05f + cr * tw * .3f, fb - th - S * .022f, tw * .16f, S * .014f, c("#46424D"))
    }
    rect(-W * .02f, fb - S * .215f, cw0, S * .016f, c("#46424D"))
    // Cathédrale Saint-Maurice : façade de tuffeau, deux flèches d'ardoise et tour-lanterne
    val cx = W * .78f; val cw = S * .22f; val cb = fb + S * .01f
    rect(cx - cw * .5f, cb - S * .22f, cw, S * .22f, c(tuf))
    rrect(cx - cw * .1f, cb - S * .16f, cw * .2f, S * .1f, cw * .1f, c("#B7AC98"))
    disc(cx, cb - S * .19f, cw * .07f, c("#B7AC98"))
    for (dx in floatArrayOf(-.3f, .3f)) {
        val sx = cx + dx * cw
        rect(sx - cw * .15f, cb - S * .34f, cw * .3f, S * .14f, c("#ECE2CD"))
        rect(sx - cw * .06f, cb - S * .32f, cw * .12f, S * .07f, c("#8E8576", .7f))
        poly(c("#76808E"), sx - cw * .17f, cb - S * .34f, sx, cb - S * .52f, sx + cw * .17f, cb - S * .34f)
        poly(c("#5C6573", .9f), sx, cb - S * .52f, sx + cw * .17f, cb - S * .34f, sx + cw * .05f, cb - S * .34f)
    }
    rect(cx - cw * .1f, cb - S * .38f, cw * .2f, S * .08f, c("#E8DDC6")); oval(cx, cb - S * .38f, cw * .11f, S * .03f, c("#76808E")); line(cx, cb - S * .41f, cx, cb - S * .44f, S * .004f, c("#76808E"))
    vgradRect(0f, fb - S * .55f, W, S * .58f, 0f to c("#E6EEF5", 0f), 1f to c("#E6EEF5", .5f))
    // Plan moyen : maisons de tuffeau aux toits d'ardoise, lucarnes et cheminées
    val mb = H * .755f; var x = -S * .05f; var k = 0
    while (x < W) {
        val w = S * (.2f + r() * .1f); val h = S * (.1f + r() * .04f); val roofH = S * (.055f + r() * .03f)
        val wall = listOf("#F3EADB", "#EDE2CC", "#F6EFE3")[k % 3]
        rect(x, mb - h, w, h, c(wall))
        poly(c("#5D6675"), x - S * .008f, mb - h, x + w * .2f, mb - h - roofH, x + w * .8f, mb - h - roofH, x + w + S * .008f, mb - h)
        poly(c("#788292"), x + w * .2f, mb - h - roofH, x + w * .8f, mb - h - roofH, x + w * .82f, mb - h - roofH * .9f, x + w * .18f, mb - h - roofH * .9f)
        if (r() > .3f) {
            val lx = x + w * (.3f + r() * .3f)
            rect(lx, mb - h - roofH * .6f, w * .14f, roofH * .5f, c("#F3EADB"))
            poly(c("#5D6675"), lx - w * .02f, mb - h - roofH * .6f, lx + w * .07f, mb - h - roofH * .95f, lx + w * .16f, mb - h - roofH * .6f)
            rect(lx + w * .035f, mb - h - roofH * .5f, w * .07f, roofH * .3f, c("#5B7491"))
        }
        if (r() > .5f) rect(x + w * .75f, mb - h - roofH * 1.15f, S * .018f, roofH * .5f, c("#C9B9A0"))
        for (fx in 0 until 3) for (fy in 0 until 2) {
            val wx = x + w * (.12f + fx * .3f); val wy = mb - h + h * (.14f + fy * .44f)
            rect(wx, wy, w * .14f, h * .3f, c("#55718F")); rect(wx, wy, w * .14f, h * .05f, c("#FFFFFF", .5f)); rect(wx - w * .015f, wy + h * .3f, w * .17f, h * .03f, c("#FFFFFF"))
        }
        x += w + S * .01f; k++
    }
    vgradRect(0f, mb - S * .3f, W, S * .3f, 0f to c("#E6EEF5", 0f), 1f to c("#E6EEF5", .22f))
    // Premier plan : résidences crème à balcons et toits plats
    val nb = H * .805f
    fun block(bx: Float, bw: Float, floors: Int) {
        val fh = S * .075f; val bh = fh * floors; val top = nb - bh
        hgradRect(bx, top, bw, bh, 0f to c("#F4ECDC"), .7f to c("#EFE5D1"), 1f to c("#E2D5BC"))
        rect(bx - S * .01f, top - S * .015f, bw + S * .02f, S * .018f, c("#D8CDB8"))
        for (f in 0 until floors) {
            val fy = top + f * fh
            var wx = bx + S * .02f
            while (wx < bx + bw - S * .05f) { rect(wx, fy + fh * .22f, S * .06f, fh * .55f, c("#6E7F93")); rect(wx, fy + fh * .22f, S * .06f, fh * .1f, c("#FFFFFF", .35f)); wx += S * .1f }
            rect(bx - S * .012f, fy + fh * .8f, bw + S * .024f, fh * .09f, c("#FBF8F1")); rect(bx - S * .012f, fy + fh * .89f, bw + S * .024f, fh * .05f, c("#B8A98E", .6f))
            var rx = bx
            while (rx < bx + bw) { line(rx, fy + fh * .55f, rx, fy + fh * .8f, S * .0025f, c("#FFFFFF", .7f)); rx += S * .012f }
            line(bx - S * .012f, fy + fh * .55f, bx + bw + S * .012f, fy + fh * .55f, S * .004f, c("#FFFFFF"))
        }
        rect(bx, nb - fh * .7f, bw, fh * .7f, c("#E6DAC2")); rect(bx + bw * .1f, nb - fh * .6f, bw * .25f, fh * .6f, c("#8A5A44"))
    }
    block(W * .48f, W * .62f, 4); block(-W * .06f, W * .34f, 3)
    // Arbres d'alignement
    fun tree(tx: Float, ty: Float, s: Float) {
        rect(tx - s * .05f, ty - s * .6f, s * .1f, s * .6f, c("#6B5444"))
        disc(tx, ty - .95f * s, .5f * s, c("#5E9A4A")); disc(tx - .3f * s, ty - .75f * s, .38f * s, c("#6FAE58")); disc(tx + .32f * s, ty - .78f * s, .4f * s, c("#5A9346"))
        disc(tx, ty - 1.15f * s, .34f * s, c("#7DBE63")); disc(tx - .12f * s, ty - 1f * s, .2f * s, c("#9AD07A"))
    }
    tree(W * .4f, nb + S * .01f, S * .2f); tree(W * .97f, nb + S * .01f, S * .18f); tree(W * .12f, nb + S * .01f, S * .15f)
    // Pelouse, puis voie en dalles d'ardoise
    rect(0f, nb, W, S * .02f, c("#7FB05E")); rect(0f, nb + S * .02f, W, S * .005f, c("#6A9A4C"))
    val gy = H * .87f
    vgradRect(0f, nb + S * .025f, W, gy - nb + S * .06f, 0f to c("#7A7F87"), 1f to c("#5A5F67"))
    var sy = nb + S * .04f
    while (sy < gy + S * .05f) { line(0f, sy, W, sy, S * .002f, c("#4A4E55", .5f)); sy += S * .03f }
    var sx0 = 0f
    while (sx0 < W) { val o = (sx0 * 7) % 13; line(sx0 + o, nb + S * .03f, sx0 + o - S * .03f, gy + S * .05f, S * .002f, c("#4A4E55", .35f)); sx0 += S * .09f }
    // Poteaux de ligne aérienne (tronçon à gauche), réverbère, poteau d'arrêt « T »
    for (p in 0 until 2) { val px = W * (.06f + p * .3f); line(px, nb + S * .02f, px, nb - S * .5f, S * .012f, c("#7A7F88")); line(px, nb - S * .47f, px + S * .2f, nb - S * .45f, S * .006f, c("#7A7F88")) }
    line(0f, nb - S * .44f, W * .6f, nb - S * .43f, S * .003f, c("#3B3F4A", .8f))
    val lx = W * .82f
    line(lx, nb + S * .02f, lx, nb - S * .42f, S * .01f, c("#3A3D44")); line(lx, nb - S * .42f, lx - S * .08f, nb - S * .45f, S * .008f, c("#3A3D44"))
    oval(lx - S * .095f, nb - S * .45f, S * .03f, S * .01f, c("#2C2F35")); glow(lx - S * .095f, nb - S * .44f, S * .03f, c("#FFE7A8", .35f))
    val sx = W * .9f
    line(sx, gy + S * .1f, sx, nb - S * .2f, S * .009f, c("#4A4E56")); ring(sx, nb - S * .23f, S * .03f, S * .007f, c("#4A4E56"))
    line(sx - S * .015f, nb - S * .245f, sx + S * .015f, nb - S * .245f, S * .006f, c("#4A4E56")); line(sx, nb - S * .245f, sx, nb - S * .215f, S * .006f, c("#4A4E56"))
    // Rails
    rect(0f, gy - S * .002f, W, S * .007f, c("#B9B4AC")); rect(0f, gy + S * .005f, W, S * .003f, c("#3E4248"))
    // Rame : arrivée (freinage), arrêt en station, départ (accélération)
    val th = S * .3f; val L = S * 2.4f; val stop = W * .94f - L
    val tx = when {
        t < .35f -> { val q = 1 - t / .35f; -L - S * .1f + (stop + L + S * .1f) * (1 - q * q) }
        t < .65f -> stop
        else -> { val q = (t - .65f) / .35f; stop + (W + S * .1f - stop) * q * q }
    }
    citadis(tx, gy, L, th)
    // Reflets de la livrée sur l'ardoise mouillée
    val rb = listOf("#E8402E", "#F28B22", "#F7CF2B", "#5BB54A", "#2C9AD6", "#7C4DB5")
    for (i in 0 until 18) { val rx = tx + L * (i / 18f); if (rx < -S * .1f || rx > W) continue; rect(rx, gy + S * .012f, S * .05f, S * .04f, c(rb[i % 6], .16f)) }
    vgradRect(0f, gy + S * .008f, W, S * .05f, 0f to c("#FFFFFF", .08f), 1f to c("#FFFFFF", 0f))
    // Quai : bordure, bande podotactile noire à pastilles, béton clair moucheté
    val qy = gy + S * .055f
    rect(0f, qy, W, S * .035f, c("#D9D6CF")); rect(0f, qy + S * .035f, W, S * .006f, c("#9C9890"))
    val by = qy + S * .041f; val bh = S * .07f
    rect(0f, by, W, bh, c("#2D2F33"))
    var dx = S * .012f
    while (dx < W) {
        var dy = by + S * .01f
        while (dy < by + bh - S * .006f) {
            val off = ((dy - by) / (S * .02f) % 2) * S * .012f
            disc(dx + off, dy, S * .0045f, c("#C7CBD1")); disc(dx + off - S * .001f, dy - S * .001f, S * .0018f, c("#FFFFFF"))
            dy += S * .02f
        }
        dx += S * .024f
    }
    vgradRect(0f, by + bh, W, H - by - bh, 0f to c("#CFCCC6"), 1f to c("#BDBAB4"))
    val rs = Mul(77); val n = (W * (H - by) / 260).toInt().coerceAtMost(4000)
    for (i in 0 until n) { val px = rs() * W; val py = by + bh + rs() * (H - by - bh); val rr = S * (.001f + rs() * .002f); disc(px, py, rr, c(if (rs() > .5f) "#FFFFFF" else "#7D7A75", .6f)) }
    for (j in 0 until 5) { val yy = by + bh + (H - by - bh) * (j / 4.2f); line(0f, yy, W, yy, S * .002f, c("#9A968F", .7f)) }
    for (j in -3 until 8) line(W * j * .25f, by + bh, W * j * .25f - W * .3f, H, S * .002f, c("#9A968F", .6f))
}

/** Rame Citadis d'Angers : avant à droite (x + L), bas de caisse sur [yb]. */
private fun DrawScope.citadis(x: Float, yb: Float, L: Float, th: Float) {
    val top = yb - th; val cab = th * 1.25f; val xf = x + L - cab; val xe = x + L; val skirt = th * .05f; val bodyBot = yb - skirt
    oval(x + L * .5f, yb + th * .03f, L * .52f, th * .06f, c("#000000", .25f))
    for (m in 0 until 4) rrect(x + L * (.08f + m * .2f), top - th * .06f, L * .1f, th * .08f, th * .02f, c("#C9CED6"))
    val body = floatArrayOf(x, top + th * .1f, x + th * .1f, top, xf, top, xf + cab * .55f, top + th * .03f, xe - cab * .12f, top + th * .2f, xe - cab * .02f, top + th * .5f, xe, top + th * .78f, xe - cab * .05f, bodyBot, x, bodyBot)
    poly(c("#F7F8FA"), *body)
    clipPoly(body) {
        vgradRect(x, top, L, th, 0f to c("#FFFFFF", .7f), .12f to c("#FFFFFF", 0f), .85f to c("#9AA3AE", 0f), 1f to c("#9AA3AE", .45f))
        val wy = top + th * .2f; val wh = th * .42f; val mods = 5; val ml = (L - cab) / mods
        for (m in 0 until mods) {
            val mx = x + m * ml
            rect(mx + ml * .06f, wy, ml * .5f, wh, c("#25303C")); rect(mx + ml * .06f, wy, ml * .5f, wh * .18f, c("#7F93A8", .55f))
            val dx = mx + ml * .62f
            rect(dx, top + th * .14f, ml * .3f, th * .76f, c("#DDE1E6"))
            rect(dx + ml * .02f, top + th * .17f, ml * .12f, th * .5f, c("#2A3541")); rect(dx + ml * .16f, top + th * .17f, ml * .12f, th * .5f, c("#2A3541"))
            rect(dx + ml * .02f, top + th * .17f, ml * .12f, th * .07f, c("#8EA2B6", .5f)); rect(dx + ml * .16f, top + th * .17f, ml * .12f, th * .07f, c("#8EA2B6", .5f))
            // Livrée Irigo : bandes arc-en-ciel en diagonale sur le bas de caisse, et sur le haut un module sur deux
            rainbow(mx + ml * (if (m % 2 == 1) .02f else .3f), top + th * .66f, ml * .55f, th * .3f)
            if (m % 2 == 0) rainbow(mx + ml * .1f, top + th * .06f, ml * .4f, th * .14f)
            rect(mx + ml - th * .025f, top + th * .08f, th * .05f, th * .84f, c("#B8BEC6"))
            for (s in 0 until 6) rect(mx + ml - th * .025f, top + th * (.12f + s * .13f), th * .05f, th * .02f, c("#9AA1AA"))
        }
        // Cabine : pare-brise noir enveloppant, casquette avec numéro de rame, girouette « A Avrillé »
        poly(c("#1C222B"), xf - cab * .02f, top + th * .14f, xf + cab * .55f, top + th * .1f, xe - cab * .14f, top + th * .24f, xe - cab * .05f, top + th * .5f, xe - cab * .1f, top + th * .6f, xf + cab * .2f, top + th * .64f, xf - cab * .02f, top + th * .62f)
        poly(c("#B9D3DD"), xf + cab * .08f, top + th * .24f, xf + cab * .55f, top + th * .21f, xe - cab * .18f, top + th * .3f, xe - cab * .12f, top + th * .5f, xf + cab * .25f, top + th * .55f)
        poly(c("#FFFFFF", .35f), xf + cab * .08f, top + th * .24f, xf + cab * .55f, top + th * .21f, xe - cab * .18f, top + th * .3f, xf + cab * .3f, top + th * .34f)
        poly(c("#1C222B"), xf + cab * .05f, top + th * .02f, xf + cab * .55f, top + th * .03f, xe - cab * .14f, top + th * .2f, xf + cab * .05f, top + th * .2f)
        text("1004", xf + cab * .38f, top + th * .1f, th * .075f, c("#FFFFFF"))
        rect(xf + cab * .22f, top + th * .14f, cab * .14f, th * .055f, c("#E8402E")); text("A", xf + cab * .245f, top + th * .187f, th * .05f, c("#FFFFFF"))
        text("Avrillé", xf + cab * .4f, top + th * .188f, th * .055f, c("#FFA21F"))
        line(xe - cab * .3f, top + th * .56f, xe - cab * .42f, top + th * .32f, th * .012f, c("#1C222B"))
        rainbow(xf - cab * .02f, top + th * .66f, cab * .7f, th * .3f)
        // Feux, logo et reflet de carrosserie
        disc(xe - cab * .28f, top + th * .74f, th * .022f, c("#F2F2EE")); disc(xe - cab * .2f, top + th * .74f, th * .022f, c("#F2F2EE"))
        disc(xe - cab * .12f, top + th * .745f, th * .022f, c("#E83A2A")); glow(xe - cab * .12f, top + th * .745f, th * .06f, c("#FF5A3C", .4f))
        ring(xe - cab * .08f, top + th * .58f, th * .025f, th * .008f, c("#FFFFFF"))
        vgradRect(x, top + th * .62f, L, th * .06f, 0f to c("#FFFFFF", .35f), 1f to c("#FFFFFF", 0f))
    }
    rect(x + th * .04f, bodyBot, L - cab * .3f, skirt, c("#3A3F47"))
    for (b in 0 until 4) rrect(x + L * (.1f + b * .26f), yb - skirt * .8f, th * .35f, skirt * 1.1f, th * .02f, c("#2A2E34"))
}

/** Bloc de bandes diagonales arc-en-ciel (livrée Irigo), avec cercles en transparence. */
private fun DrawScope.rainbow(x: Float, y: Float, w: Float, h: Float) {
    val cols = listOf("#E8402E", "#F28B22", "#F7CF2B", "#8CC63F", "#2C9AD6", "#7C4DB5")
    val bw = w / 6; val sk = h * .45f
    clipPoly(floatArrayOf(x, y, x + w, y, x + w, y + h, x, y + h)) {
        cols.forEachIndexed { i, col -> val bx = x + i * bw; poly(c(col), bx + sk, y, bx + bw + sk, y, bx + bw, y + h, bx, y + h) }
        poly(c(cols[5]), x - bw + sk, y, x + sk, y, x, y + h, x - bw, y + h)
        disc(x + w * .3f, y + h * .7f, h * .55f, c("#FFFFFF", .14f)); disc(x + w * .78f, y + h * .2f, h * .45f, c("#FFFFFF", .12f))
    }
}

private fun DrawScope.mistforest(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    vgrad(0f to c("#EEF4EF"), .5f to c("#DDE8E0"), 1f to c("#9DB8A7"))
    glow(W * .3f, H * .3f, S * .7f, c("#FFFFFF", .7f))
    val rows = listOf(Triple(.62f, "#A9C2B3", .16f), Triple(.72f, "#7FA391", .2f), Triple(.84f, "#4F7563", .26f), Triple(1f, "#2C4A3E", .32f))
    rows.forEachIndexed { li, (yb, col, hf) ->
        val r = Mul(21 + li); var x = -S * .05f
        while (x < W + S * .1f) { val h = H * hf * (.7f + r() * .5f); pine(x, H * yb, h, c(col)); x += S * (.07f + r() * .06f) * (1 + li * .25f) }
        val mist = sin(t * TAU + li) * W * .05f; oval(W * .5f + mist, H * yb - H * .03f, W * .9f, H * .035f, c("#F4F8F5", .45f))
    }
}

private fun DrawScope.dunes(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    vgrad(0f to c("#FFD9A8"), .4f to c("#FFB07A"), .7f to c("#F28C5B"), 1f to c("#C9663F"))
    glow(W * .5f, H * .5f, S * .8f, c("#FFE7B0", .8f)); disc(W * .5f, H * .5f, S * .14f, c("#FFF1C7", .95f))
    ridge(H * .62f, floatArrayOf(H * .04f, .8f, 1f, H * .015f, 2.3f, .4f), H * .55f, H, 0f to c("#F6A56A"), 1f to c("#E07E4C"))
    ridge(H * .72f, floatArrayOf(H * .05f, .7f, 3f, H * .02f, 1.8f, 1f), H * .65f, H, 0f to c("#E58A50"), 1f to c("#C66437"))
    ridge(H * .85f, floatArrayOf(H * .05f, .9f, 5f, H * .02f, 2.2f, 2f), H * .78f, H, 0f to c("#C8673A"), 1f to c("#8E3F22"))
    // Caravane de dromadaires sur la crête
    for (i in 0 until 3) {
        val k = (t * .5f + .1f) % 1f; val x = W * (.15f + k * .7f) + i * S * .07f; val y = H * .7f - sin((x / W) * .7f * TAU + 3) * H * .05f - S * .02f
        val col = c("#5E2E1A")
        oval(x, y, S * .025f, S * .014f, col); rect(x - S * .02f, y, S * .005f, S * .03f, col); rect(x + S * .016f, y, S * .005f, S * .03f, col); disc(x + S * .032f, y - S * .015f, S * .009f, col)
    }
}

private fun DrawScope.sakura(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(51)
    vgrad(0f to c("#FFF2F7"), .5f to c("#FFE0EC"), 1f to c("#FFC9DD"))
    glow(W * .2f, H * .25f, S * .7f, c("#FFFFFF", .7f))
    ridge(H * .88f, floatArrayOf(H * .02f, 1f, 2f), H * .85f, H, 0f to c("#F6B8CE"), 1f to c("#EFA0BC"))
    val bark = c("#6B3F4A")
    line(W * 1.05f, H * .08f, W * .55f, H * .22f, S * .03f, bark); line(W * .75f, H * .15f, W * .62f, H * .05f, S * .015f, bark)
    line(W * .6f, H * .2f, W * .35f, H * .3f, S * .018f, bark); line(W * .55f, H * .22f, W * .48f, H * .36f, S * .012f, bark)
    line(-W * .05f, H * .9f, W * .18f, H * .7f, S * .03f, bark); line(W * .1f, H * .78f, W * .3f, H * .74f, S * .014f, bark)
    val blos = listOf(.55f to .22f, .62f to .06f, .36f to .3f, .48f to .36f, .72f to .16f, .42f to .27f, .85f to .12f, .18f to .7f, .3f to .74f, .08f to .8f)
    blos.forEach { (bx, by) ->
        for (j in 0 until 7) {
            val x = W * bx + (r() - .5f) * S * .14f; val y = H * by + (r() - .5f) * S * .12f; val s = S * (.016f + r() * .012f)
            for (p in 0 until 5) { val a = p / 5f * TAU; disc(x + cos(a) * s * .7f, y + sin(a) * s * .7f, s * .62f, c(if (j % 2 == 1) "#FFB3CD" else "#FFD3E2", .95f)) }
            disc(x, y, s * .3f, c("#E2557F", .9f))
        }
    }
    for (i in 0 until 22) {
        val k = (t * (.6f + r() * .5f) + r()) % 1f; val x = r() * W + sin(k * TAU * 2 + i) * S * .04f; val y = k * H
        rot(k * 400 + i * 40, x, y) { oval(x, y, S * .011f, S * .006f, c("#FF9EC0", .85f)) }
    }
}

private fun DrawScope.magma(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(61)
    vgrad(0f to c("#0E0605"), .55f to c("#220B07"), 1f to c("#4A1308"))
    glow(W * .5f, H * .5f, S * .7f, c("#FF5A1F", .35f))
    // Volcan, cratère incandescent et coulées sur les flancs
    poly(c("#2A0F0A"), -W * .1f, H, W * .38f, H * .52f, W * .62f, H * .52f, W * 1.1f, H)
    oval(W * .5f, H * .52f, W * .12f, H * .012f, c("#FFB000")); glow(W * .5f, H * .5f, S * .25f, c("#FF7A00", .6f + .2f * sin(t * TAU * 2)))
    listOf(-.9f to .02f, .5f to -.03f, 1.4f to .04f).forEachIndexed { i, (dir, off) ->
        var x = W * (.5f + off); var y = H * .53f
        for (j in 0 until 26) {
            val nx = x + dir * W * .012f + sin(j * .7f + i) * W * .01f; val ny = y + H * .018f; val w = S * (.008f + j * .0012f)
            line(x, y, nx, ny, w * 3, c("#FF3D00", .3f)); line(x, y, nx, ny, w, c(if (j % 2 == 1) "#FFB000" else "#FFD45C", .95f)); x = nx; y = ny
        }
    }
    for (i in 0 until 6) { val k = (t * .5f + i / 6f) % 1f; disc(W * .5f + sin(k * 5 + i) * S * .06f, H * (.5f - k * .3f), S * (.04f + k * .08f), c("#3A2A28", .45f * (1 - k))) }
    ridge(H * .9f, floatArrayOf(H * .02f, 1.4f, 4f, H * .01f, 5f, 1f), H * .86f, H, 0f to c("#1E0B07"), 1f to c("#0A0403"))
    for (i in 0 until 30) {
        val k = (t * (.5f + r()) + r()) % 1f; val x = W * .5f + (r() - .5f) * W * .5f + sin(k * 8 + i) * S * .03f; val y = H * (.52f - k * .45f)
        disc(x, y, S * (.003f + r() * .005f), c("#FFC14D", (1 - k) * .9f))
    }
}

private val CODE_PAINT = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.MONOSPACE; isFakeBoldText = true }
private const val GLYPHS = "0123456789ABCDEF<>/{}=+*#%$"

private fun DrawScope.coderain(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(71)
    vgrad(0f to c("#000A04"), .6f to c("#001F0E"), 1f to c("#003B1C"))
    val cs = S * .045f; val cols = kotlin.math.ceil(W / cs).toInt()
    CODE_PAINT.textSize = cs * .9f
    val nc = drawContext.canvas.nativeCanvas
    for (col in 0 until cols) {
        val sp = .5f + r(); val off = r(); val len = 6 + floor(r() * 14).toInt(); val head = ((t * sp + off) % 1f) * (H / cs + len); val depth = .35f + r() * .65f
        for (k in 0 until len) {
            val row = floor(head).toInt() - k; if (row < 0) continue
            val y = row * cs; if (y > H) continue
            val g = GLYPHS[(col * 7 + row * 13) % GLYPHS.length]
            val a = (if (k == 0) 1f else (1 - k / len.toFloat()) * .8f) * depth
            CODE_PAINT.color = android.graphics.Color.argb((a * 255).toInt().coerceIn(0, 255), if (k == 0) 216 else 34, 255, if (k == 0) 230 else 122)
            nc.drawText(g.toString(), col * cs, y, CODE_PAINT)
        }
    }
    glow(W * .5f, H * .5f, S * .9f, c("#000A04", .35f))
}

private fun DrawScope.borealis(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(81)
    vgrad(0f to c("#020B1A"), .5f to c("#06203A"), 1f to c("#0E3B3A"))
    for (i in 0 until 120) disc(r() * W, r() * H * .7f, S / 500 * (1 + r() * 2), c("#FFFFFF", .2f + r() * .6f))
    val veils = listOf(Triple("#3DFF9E", .2f, 0f), Triple("#5BFFD8", .28f, 1.7f), Triple("#B06BFF", .16f, 3.1f))
    val step = max(2f, W / 180f)
    veils.forEach { (col, yc, ph) ->
        var x = 0f
        while (x < W) {
            val y = H * yc + sin(x / W * 1.5f * TAU + ph + t * TAU) * H * .05f + sin(x / W * 7 + ph) * H * .02f
            val h = H * (.18f + .08f * sin(x / W * 5 + ph + t * TAU))
            veil(x, y, step + 1, h, c(col), .32f); x += step
        }
    }
    ridge(H * .8f, floatArrayOf(H * .03f, 1.2f, 1f, H * .01f, 4f, 2f), H * .75f, H, 0f to c("#0B2430"), 1f to c("#05121A"))
    rect(0f, H * .86f, W, H * .14f, c("#DDE9F0")); ridge(H * .86f, floatArrayOf(H * .01f, 2f, 1f), c("#E8F1F6"))
    val rr = Mul(9); for (i in 0 until 9) pine(W * rr(), H * .9f, H * (.07f + rr() * .06f), c("#0A1A22"))
}

private fun DrawScope.rainwindow(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(91)
    vgrad(0f to c("#2A3142"), .5f to c("#3E4A63"), 1f to c("#5A6B88"))
    val bok = listOf("#FFB347" to .3f, "#FF6B6B" to .25f, "#9FD8FF" to .25f, "#FFD27A" to .3f, "#C59BFF" to .2f)
    for (i in 0 until 18) { val (col, a) = bok[i % 5]; glow(r() * W, H * (.4f + r() * .6f), S * (.06f + r() * .12f), c(col, a)) }
    for (i in 0 until 70) { val x = r() * W; val y = r() * H; val s = S * (.004f + r() * .008f); oval(x, y, s * .8f, s, c("#DDE8FF", .35f)); disc(x - s * .25f, y - s * .3f, s * .25f, c("#FFFFFF", .5f)) }
    for (i in 0 until 9) {
        val x = r() * W; val sp = .3f + r() * .5f; val off = r(); val k = (t * sp + off) % 1f; val y = k * H * 1.2f - H * .1f; val s = S * (.009f + r() * .006f)
        line(x, y - H * .15f, x + sin(i.toFloat()) * 3, y, s * .8f, c("#DDE8FF", .18f)); oval(x, y, s, s * 1.3f, c("#E8F0FF", .55f)); disc(x - s * .3f, y - s * .4f, s * .3f, c("#FFFFFF", .7f))
    }
}

private fun DrawScope.planet(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(101)
    vgrad(0f to c("#05030F"), .6f to c("#140A2E"), 1f to c("#2A1554"))
    for (i in 0 until 150) disc(r() * W, r() * H, S / 500 * (1 + r() * 2.2f), c("#FFFFFF", .2f + r() * .6f))
    glow(W * .3f, H * .3f, S * .6f, c("#7B5CFF", .25f))
    val px = W * .62f; val py = H * .72f; val pr = S * .42f
    glow(px, py, pr * 1.5f, c("#FF8A5B", .25f))
    val ringTl = Offset(px - pr * 1.75f, py - pr * .42f); val ringSz = Size(pr * 3.5f, pr * .84f)
    // Anneau arrière (moitié haute), planète, bandes, ombre, puis anneau avant (moitié basse)
    rot(-20f, px, py) { drawArc(c("#F5D0A9", .5f), 180f, 180f, false, ringTl, ringSz, style = Stroke(pr * .07f)) }
    drawCircle(Brush.linearGradient(listOf(c("#FFC08A"), c("#E2725B"), c("#5B2A6E")), Offset(px - pr, py - pr), Offset(px + pr, py + pr)), pr, Offset(px, py))
    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(px - pr, py - pr, px + pr, py + pr)) }
    clipPath(clip) { for (b in 0 until 5) rect(px - pr, py - pr * .8f + b * pr * .35f, pr * 2, pr * .12f, c(if (b % 2 == 1) "#FFE0C2" else "#9C3D5C", .22f)) }
    disc(px + pr * .3f, py + pr * .25f, pr * .55f, c("#2A1554", .35f))
    rot(-20f, px, py) { drawArc(c("#F5D0A9", .75f), 0f, 180f, false, ringTl, ringSz, style = Stroke(pr * .07f)) }
    val a = t * TAU
    disc(W * .2f + cos(a) * S * .05f, H * .55f + sin(a) * S * .02f, S * .035f, c("#C9C3E8"))
    disc(W * .2f + cos(a) * S * .05f + S * .01f, H * .55f + sin(a) * S * .02f + S * .008f, S * .035f, c("#140A2E", .45f))
}

private fun DrawScope.lagoon(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    vgrad(0f to c("#5CC8FF"), .45f to c("#BFEFFF"), .55f to c("#7FE3E0"), .7f to c("#4FD1C5"), .72f to c("#F7E3B5"), 1f to c("#F1D49A"))
    disc(W * .8f, H * .14f, S * .08f, c("#FFFBE0", .95f)); glow(W * .8f, H * .14f, S * .3f, c("#FFFFFF", .5f))
    cloud(W * .3f + t * W * .1f, H * .2f, S * .5f, c("#FFFFFF", .85f))
    for (i in 0 until 4) { val y = H * (.57f + i * .035f); val sh = sin(t * TAU + i) * 10; line(W * .1f + sh, y, W * .5f + sh, y, S / 300, c("#FFFFFF", .5f)) }
    ridge(H * .715f, floatArrayOf(H * .008f, 3f, t * TAU), c("#FFFFFF", .6f)); ridge(H * .725f, floatArrayOf(H * .006f, 4f, 1f), c("#F7E3B5"))
    // Palmier : tronc en anneaux, palmes qui ondulent
    val bx = W * .18f; val by = H * .82f; val tx = W * .32f; val ty = H * .48f
    fun pt(k: Float) = Offset(bx + (tx - bx) * k + sin(k * 3) * S * .03f, by + (ty - by) * k)
    for (i in 0 until 16) {
        val a = pt(i / 16f); val b = pt((i + 1) / 16f); val w = S * (.034f - i / 16f * .014f)
        line(a.x, a.y, b.x, b.y, w, c(if (i % 2 == 1) "#9A6A3A" else "#7E5126")); line(a.x - w * .25f, a.y, b.x - w * .25f, b.y, w * .25f, c("#C08A55", .5f))
    }
    val sway = sin(t * TAU) * 6
    listOf(-1.2f to .8f, -.6f to 1f, 0f to 1.1f, .6f to 1f, 1.2f to .8f, 2.6f to .7f, -2.6f to .7f).forEach { (ang, l) ->
        val ex = tx + cos(ang - 1.57f) * S * .3f * l + sway; val ey = ty + sin(ang - 1.57f) * S * .3f * l + S * .12f
        @Suppress("DEPRECATION")
        val p = Path().apply { moveTo(tx, ty); quadraticBezierTo((tx + ex) / 2, min(ty, ey) - S * .08f, ex, ey) }
        drawPath(p, c("#2E9E5B"), style = Stroke(S * .03f, cap = StrokeCap.Round))
    }
    disc(tx, ty + S * .02f, S * .025f, c("#7A4A1F")); disc(tx + S * .03f, ty + S * .03f, S * .022f, c("#7A4A1F"))
}

private fun DrawScope.haunted(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(111)
    vgrad(0f to c("#0A0618"), .5f to c("#1E0F3A"), .85f to c("#4A1F3A"), 1f to c("#2C1550"))
    for (i in 0 until 70) disc(r() * W, r() * H * .6f, S / 500 * (1 + r() * 2), c("#FFFFFF", .2f + r() * .5f))
    glow(W * .7f, H * .28f, S * .55f, c("#FFE9A8", .35f)); disc(W * .7f, H * .28f, S * .14f, c("#FFF1C2"))
    disc(W * .66f, H * .26f, S * .03f, c("#E8D59A", .6f)); disc(W * .74f, H * .31f, S * .02f, c("#E8D59A", .6f))
    ridge(H * .78f, floatArrayOf(H * .03f, 1f, 2f), c("#1A0E2A"))
    // Manoir et sa tour
    val mx = W * .2f; val my = H * .78f; val dark = c("#120A20")
    rect(mx, my - H * .18f, S * .45f, H * .18f, dark); poly(dark, mx - S * .03f, my - H * .18f, mx + S * .225f, my - H * .27f, mx + S * .48f, my - H * .18f)
    rect(mx + S * .33f, my - H * .3f, S * .07f, H * .12f, dark); poly(dark, mx + S * .32f, my - H * .3f, mx + S * .365f, my - H * .35f, mx + S * .41f, my - H * .3f)
    for (i in 0 until 4) rect(mx + S * (.05f + i * .1f), my - H * .13f, S * .05f, S * .07f, c("#FFC14D", .45f + .45f * max(0f, sin(t * TAU * 2 + i * 2))))
    rect(0f, H * .86f, W, H * .14f, c("#0E0818")); ridge(H * .86f, floatArrayOf(H * .01f, 3f, 1f), c("#0E0818"))
    // Arbre mort
    val tree = c("#0A0612")
    line(W * .88f, H * .88f, W * .86f, H * .62f, S * .025f, tree); line(W * .865f, H * .7f, W * .78f, H * .63f, S * .012f, tree); line(W * .86f, H * .66f, W * .95f, H * .58f, S * .012f, tree)
    // Citrouilles sculptées
    listOf(.12f to .9f, .5f to .92f, .68f to .89f).forEachIndexed { i, (px, py) ->
        val x = W * px; val y = H * py; val s = S * (.05f + i * .01f); val lit = c("#FFE066", .9f)
        oval(x, y, s * 1.2f, s * .9f, c("#FF8A00")); oval(x - s * .5f, y, s * .5f, s * .85f, c("#FF9F2E")); rect(x - s * .08f, y - s * 1.1f, s * .16f, s * .3f, c("#3A6B2A"))
        poly(lit, x - s * .5f, y - s * .2f, x - s * .25f, y - s * .45f, x - s * .05f, y - s * .2f); poly(lit, x + s * .05f, y - s * .2f, x + s * .25f, y - s * .45f, x + s * .5f, y - s * .2f)
        poly(lit, x - s * .55f, y + s * .15f, x + s * .55f, y + s * .15f, x + s * .3f, y + s * .45f, x, y + s * .3f, x - s * .3f, y + s * .45f)
    }
    // Chauves-souris qui battent des ailes
    for (i in 0 until 4) {
        val k = (t + i * .25f) % 1f; val x = W * (.2f + k * .6f) + sin(k * TAU * 3) * S * .05f; val y = H * (.2f + i * .06f) + cos(k * TAU * 2) * S * .03f
        val s = S * .03f; val f = sin(t * TAU * 8 + i) * .3f
        poly(tree, x, y, x - s, y - s * (.4f + f), x - s * .6f, y, x - s * 1.2f, y + s * .1f, x - s * .5f, y + s * .3f, x, y + s * .15f, x + s * .5f, y + s * .3f, x + s * 1.2f, y + s * .1f, x + s * .6f, y, x + s, y - s * (.4f + f))
    }
}

private fun DrawScope.snowvillage(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(121)
    vgrad(0f to c("#14203E"), .5f to c("#2B3C66"), .8f to c("#3B4F7A"), 1f to c("#DCE6F5"))
    for (i in 0 until 60) disc(r() * W, r() * H * .5f, S / 500 * (1 + r() * 2), c("#FFFFFF", .2f + r() * .5f))
    ridge(H * .7f, floatArrayOf(H * .04f, .8f, 1f, H * .01f, 3f, 2f), c("#C9D6EA")); ridge(H * .8f, floatArrayOf(H * .02f, 1.2f, 3f), c("#EEF3FB"))
    val rr = Mul(7); for (i in 0 until 8) pine(W * rr(), H * (.72f + rr() * .05f), H * (.06f + rr() * .05f), c("#2E5A4A"))
    val houses = listOf(.08f to "#B5463C", .34f to "#3E6E9E", .6f to "#C98A3C", .82f to "#7E4A8C")
    val bulbs = listOf("#FF4D4D", "#FFD166", "#4DFF9E")
    houses.forEachIndexed { i, (hx, col) ->
        val x = W * hx; val y = H * (.85f + (i % 2) * .02f); val w = S * .2f; val h = S * .15f
        rect(x, y - h, w, h, c(col)); poly(c("#F4F7FC"), x - S * .02f, y - h, x + w * .5f, y - h - S * .1f, x + w + S * .02f, y - h); rect(x + w * .7f, y - h - S * .1f, S * .03f, S * .06f, c(col))
        val g = .7f + .3f * sin(t * TAU * 2 + i); glow(x + w * .3f, y - h * .55f, S * .07f, c("#FFD27A", .5f * g))
        rect(x + w * .2f, y - h * .7f, w * .2f, h * .3f, c("#FFD27A", .95f)); rect(x + w * .58f, y - h * .5f, w * .16f, h * .5f, c("#4A2E22"))
        for (b in 0 until 6) disc(x + w * (b / 5f), y - h - S * .005f - abs(b - 2.5f) * S * .016f + S * .04f, S * .006f, c(bulbs[(b + i) % 3], .9f))
    }
    rect(0f, H * .9f, W, H * .1f, c("#F4F7FC"))
}

private fun DrawScope.fireworks(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(131)
    vgrad(0f to c("#03041A"), .6f to c("#10153A"), 1f to c("#241A4A"))
    for (i in 0 until 70) disc(r() * W, r() * H * .7f, S / 600 * (1 + r() * 2), c("#FFFFFF", .2f + r() * .4f))
    val bursts = listOf(Triple(.3f to .25f, "#FFD166", 0f), Triple(.72f to .2f, "#FF4F8B", .33f), Triple(.55f to .4f, "#4DD8FF", .66f), Triple(.18f to .45f, "#B98CFF", .5f), Triple(.85f to .42f, "#7CFF6B", .15f))
    bursts.forEach { (pos, col, off) ->
        val (bx, by) = pos; val k = (t * 2 + off) % 1f; val R = S * .2f * min(1f, k * 1.6f); val a = 1 - k; val n = 22
        for (j in 0 until n) {
            val an = j / n.toFloat() * TAU
            val x0 = W * bx + cos(an) * R * .55f; val y0 = H * by + sin(an) * R * .55f + k * k * S * .04f
            val x1 = W * bx + cos(an) * R; val y1 = H * by + sin(an) * R + k * k * S * .06f
            line(x0, y0, x1, y1, S * .006f, c(col, a)); disc(x1, y1, S * .005f, c("#FFFFFF", a))
        }
        glow(W * bx, H * by, R * 1.2f, c(col, a * .25f))
    }
    val rr = Mul(9); var x = 0f
    while (x < W) {
        val w = S * (.06f + rr() * .08f); val h = H * (.06f + rr() * .14f)
        rect(x, H - h - H * .05f, w - 1, h + H * .05f, c("#070818"))
        for (k in 0 until 6) if (rr() < .4f) rect(x + rr() * (w - S * .02f), H - h + rr() * h * .8f, S * .01f, S * .012f, c("#FFD27A", .6f))
        x += w
    }
    rect(0f, H * .95f, W, H * .05f, c("#FFD166", .08f))
    listOf(Triple(.42f to .14f, "#FFFFFF", .8f), Triple(.1f to .3f, "#FF9F43", .25f), Triple(.9f to .3f, "#4DD8FF", .58f)).forEach { (pos, col, off) ->
        val (bx, by) = pos; val k = (t * 2 + off) % 1f; val R = S * .16f * min(1f, k * 1.8f); val a = 1 - k; val n = 28
        for (j in 0 until n) {
            val an = j / n.toFloat() * TAU + .1f
            for (q in 1..4) { val f = q / 4f; disc(W * bx + cos(an) * R * f, H * by + sin(an) * R * f + k * k * S * .05f * f, S * .004f * f, c(if (q == 4) "#FFFFFF" else col, a * f)) }
        }
        glow(W * bx, H * by, R * 1.4f, c(col, a * .2f))
    }
    vgradRect(0f, H * .95f, W, H * .05f, 0f to c("#241A4A", .9f), 1f to c("#0A0820"))
    listOf("#FFD166" to .3f, "#FF4F8B" to .72f, "#4DD8FF" to .55f).forEachIndexed { i, (col, x) -> glow(W * x, H * .975f, S * .12f, c(col, .25f + .2f * sin(t * TAU * 2 + i))) }
}

private fun DrawScope.snowpeak(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    vgrad(0f to c("#BFD8FF"), .45f to c("#DDEBFF"), 1f to c("#F4F8FF"))
    glow(W * .25f, H * .2f, S * .6f, c("#FFFFFF", .8f)); disc(W * .25f, H * .2f, S * .06f, c("#FFFFFF", .95f))
    peaks(c("#A9C6EE"), 0f, H * .58f, W * .2f, H * .4f, W * .38f, H * .52f, W * .62f, H * .34f, W * .85f, H * .5f, W, H * .44f)
    poly(c("#FFFFFF"), W * .62f, H * .34f, W * .52f, H * .43f, W * .57f, H * .41f, W * .6f, H * .45f, W * .64f, H * .41f, W * .68f, H * .44f, W * .72f, H * .4f)
    poly(c("#FFFFFF"), W * .2f, H * .4f, W * .12f, H * .47f, W * .17f, H * .455f, W * .2f, H * .48f, W * .24f, H * .45f, W * .29f, H * .46f)
    peaks(c("#8FB1E0"), 0f, H * .7f, W * .28f, H * .55f, W * .5f, H * .68f, W * .75f, H * .56f, W, H * .66f)
    poly(c("#F4F8FF"), W * .28f, H * .55f, W * .21f, H * .61f, W * .26f, H * .6f, W * .3f, H * .62f, W * .35f, H * .59f)
    poly(c("#F4F8FF"), W * .75f, H * .56f, W * .68f, H * .62f, W * .73f, H * .61f, W * .77f, H * .63f, W * .82f, H * .6f)
    ridge(H * .82f, floatArrayOf(H * .02f, 1f, 2f), H * .78f, H, 0f to c("#F4F8FF"), 1f to c("#DDE9F7"))
    val rr = Mul(4); for (i in 0 until 10) { val x = W * rr(); val y = H * (.8f + rr() * .06f); pine(x, y, H * (.05f + rr() * .04f), c("#5C7FA8")) }
    // Légère brise de neige
    for (i in 0 until 3) { val k = (t + i / 3f) % 1f; oval(W * (k * 1.2f - .1f), H * (.36f + i * .05f), S * .08f, S * .008f, c("#FFFFFF", .35f * sin(k * 3.1416f))) }
}

private fun DrawScope.heartsky(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(141)
    vgrad(0f to c("#FFE3EE"), .5f to c("#FFC4DB"), 1f to c("#E9A6FF"))
    glow(W * .5f, H * .35f, S * .7f, c("#FFFFFF", .6f))
    cloud(W * .2f, H * .75f, S * .7f, c("#FFFFFF", .6f)); cloud(W * .8f, H * .82f, S * .8f, c("#FFFFFF", .55f)); cloud(W * .5f, H * .92f, S * 1.1f, c("#FFF4F8", .8f))
    cloud(W * .75f - t * W * .05f, H * .2f, S * .45f, c("#FFFFFF", .5f))
    val hc = listOf("#FF4F8B", "#FF8FB8", "#E85BFF", "#FF6B8B")
    for (i in 0 until 16) {
        val sp = .4f + r() * .6f; val off = r(); val k = (t * sp + off) % 1f; val x = r() * W + sin(k * TAU * 2 + i) * S * .04f; val y = H * (1 - k)
        heart(x, y, S * (.015f + r() * .025f), c(hc[i % 4], .85f * min(1f, (1 - k) * 2)))
    }
}

private fun DrawScope.eggmeadow(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(151)
    vgrad(0f to c("#BCEBFF"), .55f to c("#E3F9FF"), 1f to c("#E8FFD6"))
    disc(W * .82f, H * .15f, S * .07f, c("#FFF3B0")); glow(W * .82f, H * .15f, S * .25f, c("#FFFFFF", .5f))
    cloud(W * .25f + t * W * .08f, H * .18f, S * .45f, c("#FFFFFF", .9f))
    ridge(H * .66f, floatArrayOf(H * .04f, .7f, 1f), H * .6f, H, 0f to c("#B8E68C"), 1f to c("#9AD86A"))
    ridge(H * .76f, floatArrayOf(H * .03f, .9f, 3f), H * .72f, H, 0f to c("#9AD86A"), 1f to c("#79C24A"))
    val eggs = listOf(Triple(.15f to .79f, "#FFB3CD", "#FFFFFF"), Triple(.38f to .84f, "#A8E6FF", "#FFD166"), Triple(.62f to .8f, "#FFE08A", "#B98CFF"), Triple(.83f to .86f, "#C8B6FF", "#FF8FB8"), Triple(.5f to .9f, "#B5F2C8", "#FF9F5A"))
    eggs.forEach { (pos, col, s2) ->
        val x = W * pos.first; val y = H * pos.second; val s = S * .05f
        oval(x, y, s * .75f, s, c(col)); for (z in -1..1) rect(x - s * .7f, y + z * s * .4f, s * 1.4f, s * .1f, c(s2, .8f)); oval(x - s * .25f, y - s * .4f, s * .12f, s * .2f, c("#FFFFFF", .6f))
    }
    for (i in 0 until 60) { val x = r() * W; val y = H * (.74f + r() * .26f); val h = S * (.02f + r() * .03f); line(x, y, x + sin(t * TAU + i) * 2, y - h, S / 220, c("#4E9E2E", .8f)) }
    for (i in 0 until 14) {
        val x = r() * W; val y = H * (.75f + r() * .23f)
        for (p in 0 until 5) { val a = p / 5f * TAU; disc(x + cos(a) * S * .008f, y + sin(a) * S * .008f, S * .007f, c("#FFFFFF")) }
        disc(x, y, S * .005f, c("#FFD166"))
    }
}

private fun DrawScope.flowerfield(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(161)
    vgrad(0f to c("#9ED8FF"), .5f to c("#D6F1FF"), 1f to c("#E6FFD9"))
    cloud(W * .3f + t * W * .1f, H * .15f, S * .5f, c("#FFFFFF", .9f)); cloud(W * .8f - t * W * .06f, H * .25f, S * .35f, c("#FFFFFF", .8f))
    ridge(H * .6f, floatArrayOf(H * .05f, .6f, 2f), c("#B5E39A")); ridge(H * .7f, floatArrayOf(H * .04f, .8f, 0f), c("#94D277"))
    ridge(H * .8f, floatArrayOf(H * .03f, 1f, 4f), H * .76f, H, 0f to c("#7CC45B"), 1f to c("#5FA843"))
    val cols = listOf("#FF5C8A", "#FFD166", "#B98CFF", "#FF8A3D", "#FFFFFF")
    for (i in 0 until 70) {
        val y = H * (.64f + r() * .36f); val depth = (y - H * .62f) / (H * .38f); val x = r() * W; val s = S * (.005f + depth * .018f); val sw = sin(t * TAU + i) * s * .5f
        line(x, y, x + sw, y - s * 3, max(1f, s * .3f), c("#3E8A2E"))
        for (p in 0 until 5) { val a = p / 5f * TAU; disc(x + sw + cos(a) * s, y - s * 3 + sin(a) * s, s * .75f, c(cols[i % 5])) }
        disc(x + sw, y - s * 3, s * .45f, c("#FFC300"))
    }
    val bf = listOf("#FFB347", "#7DD3FC", "#FF8FC7")
    for (i in 0 until 3) {
        val k = (t + i / 3f) % 1f; val x = W * (.1f + k * .8f); val y = H * (.55f + i * .05f) + sin(k * TAU * 3) * S * .03f; val f = abs(sin(t * TAU * 10 + i))
        oval(x - S * .012f, y, S * .012f, S * .018f * f + S * .004f, c(bf[i], .9f)); oval(x + S * .012f, y, S * .012f, S * .018f * f + S * .004f, c(bf[i], .9f))
    }
}

private fun DrawScope.summerbeach(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    vgrad(0f to c("#FFD98A"), .3f to c("#FFE7A3"), .5f to c("#9EE6FF"), .66f to c("#3FC1E0"), .68f to c("#F5D79E"), 1f to c("#EFC982"))
    glow(W * .5f, H * .44f, S * .7f, c("#FFF4C4", .8f)); disc(W * .5f, H * .44f, S * .12f, c("#FFF3B0", .95f))
    for (i in 0 until 5) { val y = H * (.52f + i * .03f); line(W * .2f, y, W * .8f, y, S / 260, c("#FFFFFF", .45f)) }
    ridge(H * .675f, floatArrayOf(H * .008f, 3f, t * TAU, H * .004f, 9f, t * TAU * 2), c("#FFFFFF", .85f)); ridge(H * .685f, floatArrayOf(H * .006f, 4f, 1f), c("#F5D79E"))
    // Parasols à quartiers
    listOf(Triple(.25f, "#FF5C5C", "#FFFFFF"), Triple(.72f, "#22B8D6", "#FFE45C")).forEachIndexed { i, (px, c1, c2) ->
        val x = W * px; val y = H * (.86f + i * .03f); val R = S * .13f
        line(x, y, x - S * .02f, y - H * .14f, S * .008f, c("#8B6A4A"))
        val cx = x - S * .02f; val cy = y - H * .14f
        for (s in 0 until 6) drawArc(c(if (s % 2 == 1) c1 else c2), 180f + s * 30f, 30f, true, Offset(cx - R, cy - R), Size(R * 2, R * 2))
        rect(x - S * .1f, y - S * .005f, S * .16f, S * .02f, c(c1, .8f))
    }
    disc(W * .5f, H * .93f, S * .02f, c("#FF8A65")); oval(W * .55f, H * .9f, S * .03f, S * .015f, c("#FFFFFF"))
    for (i in 0 until 2) {
        val k = (t + i * .5f) % 1f; val x = W * (.1f + k * .8f); val y = H * (.25f + i * .05f)
        line(x - S * .015f, y, x, y + S * .008f, S / 260, c("#6A5A4A", .7f)); line(x, y + S * .008f, x + S * .015f, y, S / 260, c("#6A5A4A", .7f))
    }
}

private fun DrawScope.autumnwood(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val r = Mul(171)
    vgrad(0f to c("#FFE7C2"), .5f to c("#FFD19A"), 1f to c("#F2A65A"))
    glow(W * .7f, H * .35f, S * .6f, c("#FFF1D6", .8f))
    ridge(H * .72f, floatArrayOf(H * .03f, .9f, 1f), c("#E39A55"))
    val trees = listOf(Triple(.1f, .82f, "#E2711D"), Triple(.32f, .78f, "#C9481A"), Triple(.55f, .83f, "#F2A23A"), Triple(.78f, .79f, "#B5451B"), Triple(.95f, .84f, "#E88A2E"), Triple(.2f, .9f, "#D9601E"), Triple(.68f, .92f, "#C14A1A"))
    trees.forEachIndexed { i, (tx, ty, col) ->
        val x = W * tx; val y = H * ty; val h = H * (.18f + (i % 3) * .03f); val leaf = c(col)
        line(x, y, x, y - h * .6f, S * .025f, c("#5E3A22")); line(x, y - h * .4f, x - S * .06f, y - h * .6f, S * .012f, c("#5E3A22"))
        disc(x, y - h * .75f, S * .12f, leaf); disc(x - S * .08f, y - h * .62f, S * .09f, leaf); disc(x + S * .08f, y - h * .65f, S * .1f, leaf); disc(x - S * .03f, y - h * .8f, S * .07f, c("#FFD27A", .35f))
    }
    ridge(H * .93f, floatArrayOf(H * .01f, 2f, 1f), c("#9C4A1E"))
    val lc = listOf("#E2711D", "#FFB347", "#B5451B", "#D9A441")
    for (i in 0 until 26) {
        val k = (t * (.5f + r() * .5f) + r()) % 1f; val x = r() * W + sin(k * TAU * 2 + i) * S * .05f; val y = k * H
        rot(k * 500 + i * 30, x, y) { oval(x, y, S * .014f, S * .007f, c(lc[i % 4], .9f)) }
    }
}
