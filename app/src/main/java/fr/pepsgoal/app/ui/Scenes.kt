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
    }
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
}

private fun DrawScope.tram(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    vgrad(0f to c("#9FD3FF"), .45f to c("#DDEEFF"), 1f to c("#F6E8D8"))
    cloud(W * .2f + t * W * .15f, H * .14f, S * .5f, c("#FFFFFF", .8f)); cloud(W * .8f - t * W * .1f, H * .22f, S * .38f, c("#FFFFFF", .65f))
    // Façades de tuffeau et toits d'ardoise, comme en Anjou
    val fac = listOf("#F4E7CF" to "#5B6576", "#EAD9BC" to "#4E5768", "#F7EEDC" to "#626B7C", "#EBDDC4" to "#535C6D", "#F2E3C8" to "#5B6576")
    val gy = H * .78f; var x = -S * .02f; var i = 0; val r = Mul(7)
    while (x < W) {
        val w = S * (.26f + r() * .08f); val h = H * (.24f + r() * .08f); val (wall, roof) = fac[i % 5]
        rect(x, gy - h, w, h, c(wall)); poly(c(roof), x - S * .01f, gy - h, x + w * .5f, gy - h - S * .09f, x + w + S * .01f, gy - h)
        for (fy in 0 until 3) for (fx in 0 until 3) {
            val wx = x + w * (.14f + fx * .29f); val wy = gy - h + h * (.12f + fy * .27f)
            rrect(wx, wy, w * .16f, h * .16f, S * .006f, c("#7EA6C9", .85f)); rect(wx, wy + h * .16f, w * .16f, S * .006f, c("#FFFFFF", .7f))
        }
        disc(x + w * .5f, gy - h - S * .045f, S * .012f, c("#7EA6C9", .9f)); x += w; i++
    }
    // Trottoir, voie engazonnée, rails
    rect(0f, gy, W, H * .03f, c("#D9D2C5")); rect(0f, gy + H * .03f, W, H * .08f, c("#8CC870")); rect(0f, gy + H * .11f, W, H * .11f, c("#BDB6AA"))
    line(0f, gy + H * .095f, W, gy + H * .095f, S * .006f, c("#6E6A63"))
    // Poteaux et ligne aérienne de contact
    for (p in 0 until 4) { val px = W * (.12f + p * .27f); line(px, gy + H * .03f, px, gy - H * .2f, S * .008f, c("#4B5160")); line(px, gy - H * .19f, px + S * .1f, gy - H * .19f, S * .005f, c("#4B5160")) }
    line(0f, gy - H * .185f, W, gy - H * .185f, S * .003f, c("#3B3F4A"))
    // Tramway (type Citadis) qui traverse lentement : caisse blanche, vitrage continu, bandeaux de couleur, pantographe
    val k = t % 1f; val L = S * 1.6f; val th = H * .09f; val tx = -L + k * (W + L * 2) - L * .2f; val ty = gy + H * .01f - th
    rrect(tx, ty, L, th, S * .03f, c("#F5F7FA")); rect(tx + S * .02f, ty + th * .12f, L - S * .04f, th * .38f, c("#2C3E50"))
    rect(tx, ty + th * .62f, L, th * .12f, c("#E2007A")); rect(tx, ty + th * .74f, L, th * .06f, c("#00A19A"))
    for (s in 1 until 4) rect(tx + L * s / 4 - S * .004f, ty, S * .008f, th, c("#C7CDD6"))
    line(tx + L * .4f, ty, tx + L * .5f, ty - H * .045f, S * .005f, c("#3B3F4A")); line(tx + L * .5f, ty - H * .045f, tx + L * .6f, ty, S * .005f, c("#3B3F4A"))
    disc(tx + L - S * .02f, ty + th * .8f, S * .01f, c("#FFF3B0", .95f))
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
    for (i in 0..10) { val k = i / 10f; disc(bx + (tx - bx) * k + sin(k * 3) * S * .03f, by + (ty - by) * k, S * (.03f - k * .012f), c("#8B5A2B")) }
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
