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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import fr.pepsgoal.core.SHOP_BGS

/*
 * Arrière-plans illustrés de la boutique (réglage « bg » = « s:<clé> ») et fonds gratuits (Backdrop.kt) : des scènes
 * peintes sur Canvas en couches — perspective atmosphérique, lumière directionnelle, brume, grain — animées très
 * lentement (horloge économe de Backdrop.kt, lue dans la phase de dessin). Le même dessin sert d'aperçu miniature dans
 * la boutique : tout est proportionnel à la taille disponible.
 */

/** Scène illustrée [key] en plein cadre ; [still] fige l'animation (aperçus de la boutique). */
@Composable
fun SceneBg(key: String, modifier: Modifier = Modifier, still: Boolean = false) {
    val phase: State<Float> = if (still) remember { mutableFloatStateOf(.3f) } else slowPhaseState(SCENE_DUR[key] ?: 40000, SCENE_FPS[key] ?: 12)
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
                drawRect(Brush.verticalGradient(sky.map { c(it) }))
            }
        }
        finish(SHOP_BGS.firstOrNull { it.key == key }?.dark ?: true, if (key == "tram") .6f else 1f)
    }
}

/** Durée d'un cycle d'animation par scène (ms) : les scènes à mouvement visible (poissons, pluie) tournent plus vite. */
private val SCENE_DUR = mapOf("tram" to 40000, "deepsea" to 60000, "coderain" to 20000, "fireworks" to 24000, "rainwindow" to 50000, "autumnwood" to 30000, "sakura" to 30000)
/** Images par seconde : les scènes très détaillées et presque immobiles se contentent de moins (batterie). */
private val SCENE_FPS = mapOf("tram" to 8, "mountains" to 6, "mistforest" to 8, "dunes" to 6, "planet" to 6, "citynight" to 8)

/*
 * Arrière-plans illustrés : peints sur Canvas en couches (perspective atmosphérique, lumière directionnelle, brume,
 * grain), animés très lentement. Tout est proportionnel à la taille : le même dessin sert d'aperçu miniature.
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

/** Bruit 1D lissé (octaves de sinus aux fréquences et phases tirées au sort) : reliefs, lisières, nuages. */
private class Nz(seed: Int) {
    private val ph: FloatArray; private val fr: FloatArray
    init { val r = Mul(seed); ph = FloatArray(8) { r() * TAU }; fr = FloatArray(8) { .8f + r() * .5f } }
    /** Bruit doux ≈ [-1, 1]. */
    fun at(u: Float, oct: Int = 5, rough: Float = .5f): Float {
        var a = 1f; var f = 1f; var s = 0f; var n = 0f
        for (i in 0 until oct) { s += a * sin(u * f * fr[i] + ph[i]); n += a; a *= rough; f *= 2.07f }
        return s / n
    }
    /** Bruit « arêtes » ≈ [0, 1] : crêtes vives (montagnes). */
    fun ridged(u: Float, oct: Int = 5, rough: Float = .5f): Float {
        var a = 1f; var f = 1f; var s = 0f; var n = 0f
        for (i in 0 until oct) { val v = 1f - abs(sin(u * f * fr[i] + ph[i])); s += a * v * v; n += a; a *= rough; f *= 2.13f }
        return s / n
    }
}

private fun c(h: String, a: Float = 1f): Color {
    val v = h.removePrefix("#").toLong(16)
    return Color(((v shr 16) and 0xFF) / 255f, ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f, a.coerceIn(0f, 1f))
}
private fun Color.a(v: Float) = copy(alpha = v.coerceIn(0f, 1f))
private fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t.coerceIn(0f, 1f))
private fun sm(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

/* ------------------------------------------------------------------ primitives */

private fun DrawScope.sky(vararg stops: Pair<Float, Color>, y0: Float = 0f, y1: Float = size.height) =
    drawRect(Brush.verticalGradient(*stops, startY = y0, endY = y1), topLeft = Offset(0f, y0), size = Size(size.width, y1 - y0))
/** Halo radial (couleur → transparent) ; [blend] Screen pour une lumière qui s'ajoute. */
private fun DrawScope.glow(x: Float, y: Float, r: Float, col: Color, blend: BlendMode = BlendMode.SrcOver) {
    if (r <= 1f || col.alpha <= 0f) return
    drawCircle(Brush.radialGradient(0f to col, .35f to col.a(col.alpha * .55f), .7f to col.a(col.alpha * .16f), 1f to col.a(0f), center = Offset(x, y), radius = r), radius = r, center = Offset(x, y), blendMode = blend)
}
/** Halo elliptique (étiré) : nuages, brumes, voiles. */
private fun DrawScope.ellGlow(x: Float, y: Float, rx: Float, ry: Float, col: Color, blend: BlendMode = BlendMode.SrcOver) {
    if (rx <= 1f || ry <= 1f) return
    scale(1f, ry / rx, Offset(x, y)) { glow(x, y, rx, col, blend) }
}
private fun DrawScope.disc(x: Float, y: Float, r: Float, col: Color) = drawCircle(col, radius = max(r, .01f), center = Offset(x, y))
private fun DrawScope.rect(x: Float, y: Float, w: Float, h: Float, col: Color) = drawRect(col, topLeft = Offset(x, y), size = Size(w, h))
private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, col: Color, cap: StrokeCap = StrokeCap.Round) =
    drawLine(col, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = w, cap = cap)
private fun poly(vararg pts: Float): Path {
    val p = Path(); p.moveTo(pts[0], pts[1]); var i = 2
    while (i < pts.size) { p.lineTo(pts[i], pts[i + 1]); i += 2 }
    p.close(); return p
}
private fun DrawScope.fill(p: Path, col: Color) = drawPath(p, col)
private fun DrawScope.fill(p: Path, b: Brush) = drawPath(p, b)
/** Dégradé vertical entre [y0] et [y1]. */
private fun vg(y0: Float, y1: Float, vararg stops: Pair<Float, Color>) = Brush.verticalGradient(*stops, startY = y0, endY = y1)
private fun lg(x0: Float, y0: Float, x1: Float, y1: Float, vararg stops: Pair<Float, Color>) = Brush.linearGradient(*stops, start = Offset(x0, y0), end = Offset(x1, y1))

/** Bande de brume horizontale (transparente → couleur → transparente). */
private fun DrawScope.mist(y: Float, h: Float, col: Color) {
    if (h <= 0f) return
    drawRect(vg(y - h / 2, y + h / 2, 0f to col.a(0f), .5f to col, 1f to col.a(0f)), topLeft = Offset(0f, y - h / 2), size = Size(size.width, h))
}

/** Rayons de lumière (god rays) partant de (x, y) vers [dir] (degrés), éventail [spread]. */
private fun DrawScope.rays(x: Float, y: Float, len: Float, n: Int, dir: Float, spread: Float, col: Color, seed: Int, t: Float = 0f) {
    val r = Mul(seed)
    for (i in 0 until n) {
        val a = (dir + (r() - .5f) * spread) * TAU / 360f; val w = (.012f + r() * .03f) * len * (1f + .25f * sin(t * TAU + i))
        val ex = x + cos(a) * len; val ey = y + sin(a) * len; val nx = -sin(a) * w; val ny = cos(a) * w
        fill(poly(x, y, ex + nx, ey + ny, ex - nx, ey - ny), lg(x, y, ex, ey, 0f to col, 1f to col.a(0f)))
    }
}

/** Points d'un relief : hauteur = base − amp × f(u). */
private fun DrawScope.ridgePts(base: Float, amp: Float, n: Int, f: (Float) -> Float): FloatArray {
    val W = size.width; val pts = FloatArray((n + 1) * 2)
    for (i in 0..n) { val x = W * i / n; pts[i * 2] = x; pts[i * 2 + 1] = base - amp * f(i.toFloat() / n) }
    return pts
}
/** Relief rempli jusqu'en bas de l'écran (ou jusqu'à [bottom]). */
private fun DrawScope.ridgeFill(pts: FloatArray, b: Brush, bottom: Float = size.height) {
    val p = Path(); p.moveTo(pts[0], bottom); var i = 0
    while (i < pts.size) { p.lineTo(pts[i], pts[i + 1]); i += 2 }
    p.lineTo(pts[pts.size - 2], bottom); p.close(); drawPath(p, b)
}
private fun DrawScope.ridgeFill(pts: FloatArray, col: Color, bottom: Float = size.height) = ridgeFill(pts, androidx.compose.ui.graphics.SolidColor(col), bottom)
/** Faces éclairées d'un relief : pentes tournées vers la lumière (soleil à gauche si [fromLeft]), en dégradé vers le bas. */
private fun DrawScope.litFaces(pts: FloatArray, fromLeft: Boolean, depth: Float, col: Color) {
    val p = Path(); var i = 0
    while (i < pts.size - 2) {
        val x0 = pts[i]; val y0 = pts[i + 1]; val x1 = pts[i + 2]; val y1 = pts[i + 3]
        val faces = if (fromLeft) y1 > y0 else y1 < y0
        if (faces) { val k = min(1f, abs(y1 - y0) / max(1f, x1 - x0) * 1.6f); val d = depth * (.35f + .65f * k); p.moveTo(x0, y0); p.lineTo(x1, y1); p.lineTo(x1, y1 + d); p.lineTo(x0, y0 + d); p.close() }
        i += 2
    }
    var top = Float.MAX_VALUE; i = 1; while (i < pts.size) { top = min(top, pts[i]); i += 2 }
    drawPath(p, vg(top, top + depth * 3f, 0f to col, 1f to col.a(0f)))
}
/** Neige sur les sommets : couche claire au-dessus de [line], épaisseur selon la hauteur. */
private fun DrawScope.snowCaps(pts: FloatArray, line: Float, col: Color) {
    val p = Path(); var started = false; var i = 0
    val lower = FloatArray(pts.size)
    while (i < pts.size) { val y = pts[i + 1]; lower[i] = pts[i]; lower[i + 1] = if (y < line) y + (line - y) * .55f else y; i += 2 }
    i = 0; while (i < pts.size) { if (!started) { p.moveTo(pts[i], pts[i + 1]); started = true } else p.lineTo(pts[i], pts[i + 1]); i += 2 }
    i = lower.size - 2; while (i >= 0) { p.lineTo(lower[i], lower[i + 1]); i -= 2 }
    p.close(); drawPath(p, col)
}

/** Lisière de conifères le long de la ligne [base](x) : silhouettes fines à étages, densité [n] arbres. */
private fun DrawScope.conifers(n: Int, seed: Int, h0: Float, h1: Float, col: Color, base: (Float) -> Float, x0: Float = 0f, x1: Float = size.width) {
    val r = Mul(seed); val p = Path()
    for (i in 0 until n) {
        val x = x0 + (x1 - x0) * (i + r() * .9f) / n; val h = h0 + (h1 - h0) * r(); val yb = base(x) + h * .05f; val w = h * (.2f + r() * .08f)
        p.moveTo(x, yb - h)
        for (k in 1..4) { val f = k / 4f; p.lineTo(x + w * f * .55f, yb - h + h * f * .8f - h * .06f); p.lineTo(x + w * f * .22f, yb - h + h * f * .8f) }
        p.lineTo(x + w * .08f, yb); p.lineTo(x - w * .08f, yb)
        for (k in 4 downTo 1) { val f = k / 4f; p.lineTo(x - w * f * .22f, yb - h + h * f * .8f); p.lineTo(x - w * f * .55f, yb - h + h * f * .8f - h * .06f) }
        p.close()
    }
    drawPath(p, col)
}
/** Lisière de feuillus : houppiers en grappes de cercles (base sombre, côté éclairé plus clair). */
private fun DrawScope.canopy(n: Int, seed: Int, r0: Float, r1: Float, col: Color, lit: Color?, fromLeft: Boolean, base: (Float) -> Float, x0: Float = 0f, x1: Float = size.width) {
    val r = Mul(seed)
    for (i in 0 until n) {
        val x = x0 + (x1 - x0) * (i + r() * .8f) / n; val rr = r0 + (r1 - r0) * r(); val yb = base(x)
        val cy = yb - rr * 1.2f
        rect(x - rr * .06f, cy, rr * .12f, yb - cy + rr * .2f, col)
        for (k in 0 until 6) { val a = k / 6f * TAU + r(); disc(x + cos(a) * rr * .55f, cy + sin(a) * rr * .45f - rr * .1f, rr * (.5f + r() * .25f), col) }
        if (lit != null) { val sx = if (fromLeft) -1f else 1f; for (k in 0 until 3) disc(x + sx * rr * (.3f + r() * .3f), cy - rr * (.2f + r() * .4f), rr * (.25f + r() * .2f), lit) }
    }
}

/**
 * Houppier peint en touffes : chaque touffe = masse sombre, demi-teinte décalée vers la lumière, reflet discret ;
 * le bord tourné vers la lumière reçoit un liseré chaud. [lx], [ly] : direction de la lumière (unitaire, écran).
 */
private fun DrawScope.foliage(x: Float, y: Float, rx: Float, ry: Float, seed: Int, dark: Color, mid: Color, lit: Color, lx: Float = -.7f, ly: Float = -.7f, rim: Color? = null) {
    val r = Mul(seed); val n = 22
    val cl = Array(n) { val u = r() * TAU; val d = sqrt(r()) * .72f; floatArrayOf(x + cos(u) * rx * d, y + sin(u) * ry * d, rx * (.2f + r() * .14f), cos(u) * d, sin(u) * d) }
    cl.sortBy { it[1] }
    for (k in cl) disc(k[0], k[1], k[2], dark)
    for (k in cl) { val f = (k[3] * lx + k[4] * ly + .4f).coerceIn(0f, 1f); if (f > 0f) disc(k[0] + lx * k[2] * .2f, k[1] + ly * k[2] * .2f, k[2] * .72f, mix(dark, mid, f)) }
    for (k in cl) { val f = (k[3] * lx + k[4] * ly - .1f).coerceIn(0f, 1f); if (f > 0f) disc(k[0] + lx * k[2] * .42f, k[1] + ly * k[2] * .42f, k[2] * .34f, lit.a(lit.alpha * f)) }
    if (rim != null) for (i in 0 until 4) { val u = kotlin.math.atan2(ly, lx) + (r() - .5f) * 1.4f; glow(x + cos(u) * rx * .85f, y + sin(u) * ry * .85f, rx * (.3f + r() * .2f), rim, BlendMode.Screen) }
}

/** Contour lobé (houppier, buisson, nuage) : un seul tracé, bord en festons irréguliers. */
private fun lobed(x: Float, y: Float, rx: Float, ry: Float, seed: Int, lobes: Int = 7, depth: Float = .14f, flatBottom: Float = .0f): Path {
    val r = Mul(seed); val n = lobes * 8; val ph = r() * TAU; val amp = FloatArray(lobes) { .7f + r() * .6f }
    val p = Path()
    for (i in 0..n) {
        val a = i / n.toFloat() * TAU; val li = ((i / 8) % lobes); val f = (i % 8) / 8f
        val bump = sqrt(sin(f * 3.1416f).coerceAtLeast(0f)) * depth * amp[li]
        var px = x + cos(a + ph) * rx * (1f - depth + bump); var py = y + sin(a + ph) * ry * (1f - depth + bump)
        if (flatBottom > 0f && py > y + ry * (1f - flatBottom)) py = y + ry * (1f - flatBottom) + (py - y - ry * (1f - flatBottom)) * .3f
        if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
    }
    p.close(); return p
}
/** Houppier en volume : silhouette lobée en dégradé, ombre propre côté opposé à la lumière, touffes éclairées. */
private fun DrawScope.crown(x: Float, y: Float, rx: Float, ry: Float, seed: Int, lit: Color, mid: Color, dark: Color, lx: Float = -.6f, ly: Float = -.8f) {
    val p = lobed(x, y, rx, ry, seed, 7, .16f)
    drawPath(p, Brush.linearGradient(0f to lit, .5f to mid, 1f to dark, start = Offset(x + lx * rx, y + ly * ry), end = Offset(x - lx * rx, y - ly * ry)))
    clipPath(p) {
        drawPath(lobed(x - lx * rx * .45f, y - ly * ry * .5f, rx * .8f, ry * .7f, seed + 1, 6, .2f), dark.a(.4f))
        val r = Mul(seed + 2)
        for (i in 0 until 4) { val u = r() * TAU; val d = r() * .5f; val cx = x + cos(u) * rx * d + lx * rx * .2f; val cy = y + sin(u) * ry * d + ly * ry * .2f
            drawPath(lobed(cx, cy, rx * (.28f + r() * .15f), ry * (.24f + r() * .12f), seed + 10 + i, 5, .22f), Brush.linearGradient(0f to lit.a(.9f), 1f to lit.a(0f), start = Offset(cx + lx * rx * .3f, cy + ly * ry * .3f), end = Offset(cx - lx * rx * .3f, cy - ly * ry * .3f))) }
    }
}

/** Étoiles stables, scintillement léger ; [bright] : quelques étoiles à aigrettes. */
private fun DrawScope.starField(seed: Int, n: Int, yMax: Float, col: Color, t: Float, bright: Int = 0) {
    val r = Mul(seed); val W = size.width; val S = min(W, size.height); val pts = ArrayList<Offset>(n)
    for (i in 0 until n) pts.add(Offset(r() * W, r() * yMax))
    drawPoints(pts, PointMode.Points, col.a(col.alpha * .7f), strokeWidth = max(1f, S / 420f), cap = StrokeCap.Round)
    for (i in 0 until n / 6) { val x = r() * W; val y = r() * yMax; val tw = .55f + .45f * sin(t * TAU * 3 + i); disc(x, y, S * (.0016f + r() * .0016f), col.a(col.alpha * tw)) }
    for (i in 0 until bright) {
        val x = r() * W; val y = r() * yMax * .8f; val s = S * (.012f + r() * .01f); val tw = .7f + .3f * sin(t * TAU * 2 + i * 1.7f)
        glow(x, y, s * 2.2f, col.a(.35f * tw), BlendMode.Screen)
        line(x - s, y, x + s, y, max(1f, S / 500f), col.a(.6f * tw)); line(x, y - s, x, y + s, max(1f, S / 500f), col.a(.6f * tw)); disc(x, y, S * .003f, col)
    }
}

/** Nuage moderne : nappe allongée de halos doux, ventre légèrement ombré, bord éclairé. */
private fun DrawScope.softCloud(x: Float, y: Float, w: Float, body: Color, light: Color, seed: Int) {
    val r = Mul(seed)
    for (i in 0 until 9) { val u = (r() - .5f) * w; val v = (r() - .5f) * w * .12f; ellGlow(x + u, y + v, w * (.22f + r() * .2f), w * (.07f + r() * .05f), body) }
    for (i in 0 until 5) { val u = (r() - .5f) * w * .8f; ellGlow(x + u, y - w * .04f, w * (.15f + r() * .1f), w * .035f, light, BlendMode.Screen) }
}

/** Finition : grain fin (matière) et vignettage doux. */
private val GRAIN = Mul(17).let { r -> List(2400) { Offset(r(), r()) } }
private fun DrawScope.finish(dark: Boolean, vignette: Float = 1f) {
    val W = size.width; val H = size.height; val R = max(W, H) * .8f
    drawRect(Brush.radialGradient(0f to Color.Transparent, .6f to Color.Transparent, 1f to Color.Black.copy(alpha = (if (dark) .38f else .14f) * vignette), center = Offset(W * .5f, H * .42f), radius = R))
    drawPoints(GRAIN.map { Offset(it.x * W, it.y * H) }, PointMode.Points, if (dark) Color.White.copy(alpha = .05f) else Color.Black.copy(alpha = .045f), strokeWidth = max(1f, min(W, H) / 400f))
}

/* ------------------------------------------------------------------ Montagnes à l'heure dorée */
private fun DrawScope.mountains(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#2B3A67"), .28f to c("#5B6B9A"), .5f to c("#C79AA8"), .62f to c("#F2B48A"), .7f to c("#FFD9A0"), 1f to c("#FFE7BE"))
    val sx = W * .68f; val sy = H * .64f
    glow(sx, sy, S * 1.3f, c("#FFC37A", .55f), BlendMode.Screen); glow(sx, sy, S * .35f, c("#FFE9C2", .9f), BlendMode.Screen)
    rays(sx, sy, H * .7f, 9, -100f, 70f, c("#FFE2B0", .10f), 4, t)
    disc(sx, sy, S * .055f, c("#FFF6E2"))
    softCloud(W * .25f, H * .33f, W * .7f, c("#F6C9B8", .30f), c("#FFE6CF", .25f), 3)
    softCloud(W * .8f, H * .44f, W * .55f, c("#FBC9A7", .28f), c("#FFF0D8", .3f), 5)
    softCloud(W * .4f, H * .52f, W * .5f, c("#FFD3B0", .24f), c("#FFF2DD", .25f), 8)
    // Cinq chaînes, de la plus lointaine (brumeuse, chaude) à la plus proche (froide, sombre)
    val far = c("#E9B7A6"); val near = c("#1E2A47")
    val layers = 5
    for (k in 0 until layers) {
        val d = k / (layers - 1f); val n = Nz(31 + k * 7)
        val base = H * (.66f + d * .1f); val amp = H * (.13f - d * .045f)
        val pts = ridgePts(base, amp, 140) { u -> .65f * n.ridged(u * (2.2f + k * .7f), 5, .5f) + .35f * (n.at(u * 1.3f, 3) * .5f + .5f) }
        val col = mix(far, near, d * d * .9f + d * .1f)
        ridgeFill(pts, vg(base - amp, H, 0f to col, 1f to mix(col, c("#0E1428"), .35f)))
        if (k < 4) litFaces(pts, false, H * (.03f - d * .015f), mix(c("#FFD8B0"), col, .25f + d * .5f).a(.55f - d * .25f))
        if (k == 0) snowCaps(pts, base - amp * .78f, c("#FFF1E4", .7f))
        mist(base + H * .02f, H * .05f, mix(c("#FFD6B5"), c("#B7A2C4"), d).a(.45f - d * .2f))
    }
    // Premier plan : forêt de conifères en contre-jour, lisière éclairée
    val fg = Nz(77)
    val baseF = { x: Float -> H * .9f + fg.at(x / W * 3f, 3) * H * .015f }
    conifers(26, 9, H * .05f, H * .11f, c("#141B30"), baseF)
    ridgePts(H * .93f, H * .01f, 60) { u -> fg.at(u * 4f, 3) }.let { ridgeFill(it, c("#0F1526")) }
    // Oiseaux au loin
    val r = Mul(5)
    for (i in 0 until 5) {
        val x = W * (.3f + r() * .3f) + t * W * .05f; val y = H * (.42f + r() * .06f); val s = S * (.008f + r() * .006f)
        val p = Path(); p.moveTo(x - s, y - s * .3f); p.cubicTo(x - s * .5f, y - s * .7f, x - s * .2f, y - s * .5f, x, y); p.cubicTo(x + s * .2f, y - s * .5f, x + s * .5f, y - s * .7f, x + s, y - s * .3f)
        drawPath(p, c("#2A2F4A", .55f), style = androidx.compose.ui.graphics.drawscope.Stroke(max(1f, S / 450f), cap = StrokeCap.Round))
    }
}

/* ------------------------------------------------------------------ Tramway d'Angers (Citadis 302, voie engazonnée) */

/** Points d'une matrice LED 5 × 7 (girouette) : une lettre = 7 lignes de 5 bits. */
private val LED = mapOf(
    'A' to intArrayOf(14, 17, 17, 31, 17, 17, 17), 'v' to intArrayOf(0, 0, 17, 17, 17, 10, 4), 'r' to intArrayOf(0, 0, 22, 25, 16, 16, 16),
    'i' to intArrayOf(4, 0, 12, 4, 4, 4, 14), 'l' to intArrayOf(12, 4, 4, 4, 4, 4, 14), 'é' to intArrayOf(2, 4, 14, 17, 31, 16, 14),
    '1' to intArrayOf(4, 12, 4, 4, 4, 4, 14), '0' to intArrayOf(14, 17, 19, 21, 25, 17, 14), '4' to intArrayOf(2, 6, 10, 18, 31, 2, 2), ' ' to intArrayOf(0, 0, 0, 0, 0, 0, 0),
)
/** Texte en points LED dans le quadrilatère (p00 haut-gauche, p10 haut-droit, p01 bas-gauche, p11 bas-droit). */
private fun DrawScope.ledText(s: String, q: FloatArray, col: Color, dot: Float) {
    val cols = s.length * 6 - 1; val pts = ArrayList<Offset>()
    s.forEachIndexed { ci, ch -> val g = LED[ch] ?: return@forEachIndexed
        for (row in 0 until 7) for (bit in 0 until 5) if ((g[row] shr (4 - bit)) and 1 == 1) {
            val u = (ci * 6 + bit + .5f) / cols; val v = (row + .5f) / 7f
            pts.add(quadPt(q, u, v))
        }
    }
    drawPoints(pts, androidx.compose.ui.graphics.PointMode.Points, col, strokeWidth = dot, cap = StrokeCap.Round)
}
/** Point bilinéaire d'un quadrilatère [x00,y00, x10,y10, x01,y01, x11,y11]. */
private fun quadPt(q: FloatArray, u: Float, v: Float): Offset {
    val tx = q[0] + (q[2] - q[0]) * u; val ty = q[1] + (q[3] - q[1]) * u
    val bx = q[4] + (q[6] - q[4]) * u; val by = q[5] + (q[7] - q[5]) * u
    return Offset(tx + (bx - tx) * v, ty + (by - ty) * v)
}
private fun quadPath(q: FloatArray, u0: Float, v0: Float, u1: Float, v1: Float): Path {
    val a = quadPt(q, u0, v0); val b = quadPt(q, u1, v0); val cc = quadPt(q, u1, v1); val d = quadPt(q, u0, v1)
    return poly(a.x, a.y, b.x, b.y, cc.x, cc.y, d.x, d.y)
}

/* ------------------------------------------------------------------ Tramway d'Angers : Citadis 302 arc-en-ciel, voie engazonnée */

/** Caméra sténopé. Monde en mètres : a le long de la voie (vers le fond), l latéral (+ à droite), h hauteur. */
private class Cam(private val ca: Float, private val cl: Float, private val ch: Float, yaw: Float, val f: Float, val cx: Float, val hy: Float) {
    private val cs = cos(yaw / 360f * TAU); private val sn = sin(yaw / 360f * TAU)
    fun z(a: Float, l: Float) = max(.08f, (a - ca) * cs + (l - cl) * sn)
    fun p(a: Float, l: Float, h: Float): Offset {
        val ra = a - ca; val rl = l - cl; val z = max(.08f, ra * cs + rl * sn)
        return Offset(cx + f * (rl * cs - ra * sn) / z, hy - f * (h - ch) / z)
    }
    /** Pixels par mètre à la profondeur de (a, l). */
    fun k(a: Float, l: Float) = f / z(a, l)
    /** Polygone à partir de triplets (a, l, h). */
    fun poly3(vararg q: Float): Path {
        val path = Path(); var i = 0
        while (i < q.size) { val o = p(q[i], q[i + 1], q[i + 2]); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y); i += 3 }
        path.close(); return path
    }
    /** Quadrilatère au sol (hauteur [h]). */
    fun ground(a0: Float, a1: Float, l0: Float, l1: Float, h: Float = 0f) = poly3(a0, l0, h, a1, l0, h, a1, l1, h, a0, l1, h)
}

/** Interpolation linéaire dans une table (xs croissants). */
private fun tab(x: Float, xs: FloatArray, ys: FloatArray): Float {
    if (x <= xs[0]) return ys[0]
    for (i in 1 until xs.size) if (x <= xs[i]) { val u = (x - xs[i - 1]) / (xs[i] - xs[i - 1]); return ys[i - 1] + (ys[i] - ys[i - 1]) * u }
    return ys[ys.size - 1]
}

// Gabarit du Citadis 302 : profil du nez (recul du centre selon la hauteur), creux des coins, demi-largeur.
private val NH = floatArrayOf(.25f, .4f, .6f, .9f, 1.1f, 1.6f, 2.1f, 2.6f, 2.95f, 3.2f, 3.32f)
private val NP = floatArrayOf(.2f, .05f, 0f, 0f, .05f, .22f, .5f, .86f, 1.08f, 1.38f, 1.7f)
private val ND = floatArrayOf(1.0f, 1.02f, 1.05f, 1.05f, 1.02f, .92f, .8f, .66f, .54f, .4f, .22f)
private const val TL = 32.8f
private fun nP(h: Float) = tab(h, NH, NP)
private fun nD(h: Float) = tab(h, NH, ND)
private fun hw(h: Float) = when { h > 2.8f -> 1.2f - .26f * ((h - 2.8f) / .52f).pow(2); h < .4f -> 1.17f; else -> 1.2f }
/** Abscisse du coin avant (raccord nez / flanc) à la hauteur h. */
private fun corner(h: Float) = nP(h) + nD(h)
/** Point de la face avant : q ∈ [-1, 1] de gauche à droite. */
private fun Cam.front(q: Float, h: Float): Offset {
    val e = 1f - (1f - abs(q).pow(2.1f)).coerceAtLeast(0f).pow(1f / 2.1f)
    return p(nP(h) + nD(h) * e, q * hw(h), h)
}
/** Point du flanc gauche (celui qu'on voit). */
private fun Cam.side(a: Float, h: Float) = p(a, -hw(h), h)
/** Zone du flanc (a0..a1, h0..h1), bornée par le nez à l'avant. */
private fun Cam.sideRect(a0: Float, a1: Float, h0: Float, h1: Float): Path {
    val path = Path(); val n = 6
    for (i in 0..n) { val h = h0 + (h1 - h0) * i / n; val o = side(max(a0, corner(h)), h); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
    for (i in n downTo 0) { val h = h0 + (h1 - h0) * i / n; val o = side(min(a1, TL - corner(h)), h); path.lineTo(o.x, o.y) }
    path.close(); return path
}
/** Parallélogramme oblique sur le flanc (bande de livrée). */
private fun Cam.sideBand(a0: Float, w: Float, h0: Float, h1: Float, slant: Float): Path {
    val d = slant * (h1 - h0)
    val o1 = side(a0, h0); val o2 = side(a0 + w, h0); val o3 = side(a0 + w + d, h1); val o4 = side(a0 + d, h1)
    return poly(o1.x, o1.y, o2.x, o2.y, o3.x, o3.y, o4.x, o4.y)
}
private fun Cam.sideCircle(a: Float, h: Float, r: Float): Path {
    val path = Path()
    for (i in 0..24) { val t = i / 24f * TAU; val o = side(a + cos(t) * r, h + sin(t) * r); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
    path.close(); return path
}
/** Contour apparent à droite du nez : q le plus à droite à l'écran pour la hauteur h (au-delà, la face fuit). */
private fun Cam.qSil(h: Float): Float {
    var best = 0f; var bx = -1e9f
    for (i in 0..32) { val q = i / 32f; val x = front(q, h).x; if (x > bx) { bx = x; best = q } }
    return best
}
/** Zone de la face avant (q0..q1, h de hb(q) à ht), rognée au contour apparent. */
private fun Cam.frontZone(q0: Float, q1: Float, hb: (Float) -> Float, ht: (Float) -> Float): Path {
    val path = Path(); val n = 28
    fun pt(q: Float, hf: (Float) -> Float): Offset { val h0 = hf(q); val qc = min(q, qSil(h0)); return front(qc, hf(qc)) }
    for (i in 0..n) { val o = pt(q0 + (q1 - q0) * i / n, hb); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
    val hs = 10
    if (q1 >= .99f) for (j in 0..hs) { val h = hb(q1) + (ht(q1) - hb(q1)) * j / hs; val o = front(qSil(h), h); path.lineTo(o.x, o.y) }
    for (i in n downTo 0) { val o = pt(q0 + (q1 - q0) * i / n, ht); path.lineTo(o.x, o.y) }
    path.close(); return path
}

private val RAINBOW = listOf("#E8412C", "#F47B20", "#F9C623", "#A9C93A", "#2DA9E1", "#7B4FA0")

private fun DrawScope.tram(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    val cam = Cam(-6.2f, -4.5f, 1.9f, 15f, W * 1.05f, W * .5f, H * .67f)
    val hy = cam.hy; val vp = cam.p(5000f, 0f, 1.9f)
    val sun = Offset(vp.x - W * .1f, hy - H * .045f)

    // --- Ciel de crépuscule
    sky(0f to c("#2C3264"), .16f to c("#4D4A86"), .3f to c("#86649C"), .42f to c("#C97C98"), .5f to c("#EE9C86"), .56f to c("#FFC08C"), .6f to c("#FFDDA8"), 1f to c("#FFE7BE"), y1 = hy + H * .02f)
    glow(sun.x, sun.y, S * 1.25f, c("#FF9F72", .45f), BlendMode.Screen)
    glow(sun.x, sun.y, S * .42f, c("#FFE2B0", .85f), BlendMode.Screen)
    val drift = t * W * .08f
    softCloud(W * .15f + drift, H * .17f, W * .9f, c("#B58AB4", .32f), c("#FFC7B5", .35f), 21)
    softCloud(W * .85f - drift * .6f, H * .27f, W * .7f, c("#D98FA0", .3f), c("#FFD4B8", .4f), 22)
    for ((cy, cw, sd) in listOf(Triple(.34f, 1.2f, 31), Triple(.44f, 1f, 32))) {
        val r = Mul(sd)
        for (i in 0 until 7) { val x = W * (r() * 1.2f - .1f) + drift * (.5f + r()); val y = H * cy + (r() - .5f) * H * .03f; val w = W * cw * (.25f + r() * .25f)
            ellGlow(x, y, w, w * .1f, c("#9C6E9E", .22f)); ellGlow(x, y + w * .05f, w * .8f, w * .045f, c("#FFC2A0", .3f), BlendMode.Screen) }
    }
    softCloud(W * .35f + drift * .8f, H * .4f, W * .6f, c("#F2A68E", .28f), c("#FFE6C8", .45f), 23)
    softCloud(W * .7f - drift * .4f, H * .5f, W * .45f, c("#FFC09A", .25f), c("#FFF0D8", .4f), 24)

    // --- Cathédrale Saint-Maurice, sur sa butte au bout de la rue (silhouette brumeuse)
    run {
        val ka = 400f; val base = cam.p(ka, -3f, 22f); val k = cam.k(ka, -3f)
        val col = c("#A9798F"); val rim = c("#FFD3A0", .7f)
        fun r(x0: Float, y0: Float, x1: Float, y1: Float) = rect(base.x + x0 * k, base.y - y1 * k, (x1 - x0) * k, (y1 - y0) * k, col)
        fun spire(xc: Float, w: Float, y0: Float, y1: Float) {
            fill(poly(base.x + (xc - w) * k, base.y - y0 * k, base.x + xc * k, base.y - y1 * k, base.x + (xc + w) * k, base.y - y0 * k), col)
            line(base.x + (xc - w) * k, base.y - y0 * k, base.x + xc * k, base.y - y1 * k, max(1f, k * .5f), rim)
        }
        r(-22f, -30f, 22f, 2f) // butte et maisons basses
        r(-14f, 0f, -5f, 44f); r(5f, 0f, 14f, 44f)          // tours
        r(-5f, 0f, 5f, 40f); r(-3.6f, 40f, 3.6f, 51f)       // tour centrale (Renaissance)
        fill(poly(base.x - 3.6f * k, base.y - 51f * k, base.x, base.y - 57f * k, base.x + 3.6f * k, base.y - 51f * k), col)
        line(base.x, base.y - 57f * k, base.x, base.y - 61f * k, max(1f, k * .35f), col)
        spire(-9.5f, 4.6f, 44f, 77f); spire(9.5f, 4.6f, 44f, 77f)
        r(14f, 0f, 60f, 22f); fill(poly(base.x + 14f * k, base.y - 22f * k, base.x + 37f * k, base.y - 32f * k, base.x + 60f * k, base.y - 22f * k), col) // nef
        for (sx in listOf(-9.5f, 9.5f)) for (j in 0 until 3) { val yy = 50f + j * 7f; rect(base.x + (sx - .5f) * k, base.y - (yy + 2f) * k, k, 2f * k, c("#8C6378")) }
        mist(base.y + 2f * k, H * .05f, c("#FFC8A0", .45f))
    }

    // --- Immeubles : tuffeau et ardoise, combles à la Mansart et lucarnes
    fun buildings(left: Boolean) {
        val fl = if (left) -12f else 11.5f; val sgn = if (left) -1f else 1f
        val r = Mul(if (left) 101 else 202)
        val lots = ArrayList<FloatArray>()
        var a = -9f
        while (a < 330f) { val w = 8f + r() * 6f; lots.add(floatArrayOf(a, a + w, 12.5f + r() * 5f, 3.2f + r() * 1.2f, r(), r())); a += w }
        val lit = if (left) c("#C9A9A6") else c("#F4C994"); val shade = if (left) c("#8E7C93") else c("#C49A92")
        val slate = c("#4F5470"); val slateLit = c("#8D86A0")
        for (b in lots.asReversed()) {
            val a0 = max(b[0], -5.5f); val a1 = b[1]; val hc = b[2]; val hr = b[3]
            if (a1 < a0 + .5f) continue
            val z = cam.z(a1, fl); val haze = sm(25f, 260f, z)
            val skyHaze = c("#E9A99A")
            // Pignon côté caméra (visible au-dessus d'un voisin plus bas)
            fill(cam.poly3(a0, fl, 0f, a0, fl + sgn * 12f, 0f, a0, fl + sgn * 12f, hc + hr, a0, fl, hc + hr), mix(mix(shade, c("#7A6A83"), .3f), skyHaze, haze * .8f))
            // Comble à la Mansart (plan incliné), faîtage plat
            val back = fl + sgn * 2.6f
            val roofP = cam.poly3(a0, fl, hc, a1, fl, hc, a1, back, hc + hr, a0, back, hc + hr)
            val rt = cam.p(a0, back, hc + hr); val rb = cam.p(a0, fl, hc)
            fill(roofP, vg(rt.y, rb.y, 0f to mix(slateLit, skyHaze, haze * .7f), 1f to mix(slate, skyHaze, haze * .7f)))
            // Façade
            val top = cam.p((a0 + a1) / 2, fl, hc); val bot = cam.p((a0 + a1) / 2, fl, 0f)
            val tone = .9f + b[4] * .2f
            val fTop = mix(lit, Color.White, (tone - 1f).coerceAtLeast(0f)); val fBot = shade
            fill(cam.poly3(a0, fl, 0f, a1, fl, 0f, a1, fl, hc, a0, fl, hc), vg(top.y, bot.y, 0f to mix(fTop, skyHaze, haze * .75f), .6f to mix(mix(fTop, fBot, .55f), skyHaze, haze * .75f), 1f to mix(fBot, skyHaze, haze * .75f)))
            // Corniche
            fill(cam.poly3(a0, fl, hc - .35f, a1, fl, hc - .35f, a1, fl, hc, a0, fl, hc), mix(c("#FFF0D8", .55f), skyHaze, haze))
            // Fenêtres, balcons, lucarnes (seulement à portée de vue)
            if (z < 140f) {
                val w = a1 - a0; val n = max(2, (w / 2.5f).toInt()); val step = w / n
                val floors = ((hc - 4.6f) / 3.1f).toInt()
                val wr = Mul((b[0] * 13f).toInt())
                for (i in 0 until n) {
                    val wa = a0 + step * (i + .5f) - .55f
                    for (fy in 0 until floors) {
                        val h0 = 4.6f + fy * 3.1f + .5f
                        val on = wr() < .3f
                        val glass = if (on) mix(c("#FFC874"), c("#FF9E5C"), wr()) else mix(c("#6B6286"), c("#3E3D5C"), wr())
                        fill(cam.poly3(wa, fl, h0, wa + 1.1f, fl, h0, wa + 1.1f, fl, h0 + 2f, wa, fl, h0 + 2f), mix(glass, skyHaze, haze * .8f))
                        if (fy == 0) { // balcon filant en fer forgé
                            val l0 = cam.p(wa - .15f, fl, h0 + .05f); val l1 = cam.p(wa + 1.25f, fl, h0 + .05f)
                            line(l0.x, l0.y, l1.x, l1.y, max(1f, cam.k(wa, fl) * .08f), c("#2B2838", .8f - haze * .6f))
                        }
                    }
                    // Lucarne : petite façade en retrait sur le comble, fronton en tuffeau
                    val dl = fl + sgn * .9f; val db = hc + .3f; val dt = hc + 2.1f
                    fill(cam.poly3(wa - .15f, dl, db, wa + 1.25f, dl, db, wa + 1.25f, dl, dt, wa + .55f, dl, dt + .8f, wa - .15f, dl, dt), mix(mix(fTop, fBot, .3f), skyHaze, haze * .75f))
                    fill(cam.poly3(wa + .1f, dl, db + .2f, wa + 1f, dl, db + .2f, wa + 1f, dl, dt - .1f, wa + .1f, dl, dt - .1f), mix(if (wr() < .25f) c("#FFC070") else c("#4A4766"), skyHaze, haze * .8f))
                }
                // Rez-de-chaussée : vitrines éclairées et stores
                val sh = cam.poly3(a0 + .6f, fl, .2f, a1 - .6f, fl, .2f, a1 - .6f, fl, 3.2f, a0 + .6f, fl, 3.2f)
                fill(sh, mix(c("#FFB766", .85f), skyHaze, haze * .6f))
                val aw = listOf(c("#9C3B3B"), c("#2F5D50"), c("#3B4D7A"), c("#B07A2A"))[(b[5] * 4).toInt().coerceIn(0, 3)]
                fill(cam.poly3(a0 + .5f, fl, 3.3f, a1 - .5f, fl, 3.3f, a1 - .5f, fl - sgn * 1.2f, 2.9f, a0 + .5f, fl - sgn * 1.2f, 2.9f), mix(aw, skyHaze, haze * .6f))
            }
            // Cheminée
            if (b[4] > .4f) { val ca0 = a0 + (a1 - a0) * .3f; fill(cam.poly3(ca0, back, hc + hr, ca0 + .9f, back, hc + hr, ca0 + .9f, back, hc + hr + 1.4f, ca0, back, hc + hr + 1.4f), mix(c("#6E5E6E"), skyHaze, haze * .8f)) }
        }
    }
    buildings(true); buildings(false)
    // Lumière rasante dans l'axe de la rue
    glow(sun.x, hy, S * .7f, c("#FFC48A", .55f), BlendMode.Screen)
    rays(sun.x, sun.y, H * .55f, 7, 75f, 60f, c("#FFE0B0", .08f), 9, t)

    // --- Sol : chaussée, trottoirs
    val far = 600f
    fill(cam.ground(-5f, far, -12f, 12f), vg(hy, H, 0f to c("#C9989A"), .25f to c("#8D7280"), 1f to c("#5B4C5E")))
    fill(cam.ground(-5f, far, 8.5f, 11.5f, .12f), vg(hy, H, 0f to c("#D5A7A0"), .3f to c("#A3878F"), 1f to c("#7C6B78")))
    fill(cam.ground(-5f, far, -12f, -6.2f, .12f), vg(hy, H, 0f to c("#D5A7A0"), .3f to c("#9C8290"), 1f to c("#6D5F72")))

    // --- Voies engazonnées (deux voies), rails
    for (tc in listOf(0f, 3.5f)) {
        fill(cam.ground(-5f, far, tc - 1.45f, tc + 1.45f, .02f), vg(hy, H, 0f to c("#B2A68A"), .12f to c("#7F8B5C"), .4f to c("#5E7A45"), 1f to c("#3F5A33")))
    }
    // Brins d'herbe (en trois teintes), ondulation légère
    run {
        val r = Mul(61); val sway = sin(t * TAU)
        val tones = listOf(c("#2F4A2A", .8f), c("#6E8F4A", .8f), c("#A7B56A", .7f))
        val paths = List(3) { Path() }
        for (i in 0 until 2600) {
            val a = -5f + 40f * r().pow(1.8f); val tc = if (r() < .5f) 0f else 3.5f; val l = tc - 1.4f + r() * 2.8f
            if (abs(abs(l - tc) - .72f) < .1f) continue
            if (tc == 0f && a > -.1f) continue
            val hb = .05f + r() * .09f; val o = cam.p(a, l, .02f); val k = cam.k(a, l)
            if (o.y > H + 4f || o.x < -4f || o.x > W + 4f) continue
            val lean = (r() - .5f) * .05f + sway * .012f
            val pp = paths[(r() * 3).toInt().coerceIn(0, 2)]; pp.moveTo(o.x, o.y); pp.lineTo(o.x + lean * k, o.y - hb * k)
        }
        paths.forEachIndexed { i, pp -> drawPath(pp, tones[i], style = Stroke(max(1f, S / 500f), cap = StrokeCap.Round)) }
    }
    for (tc in listOf(0f, 3.5f)) for (rl in listOf(-.72f, .72f)) {
        val l = tc + rl
        fill(cam.ground(-5f, far, l - .07f, l + .07f, .03f), c("#4B4650"))
        fill(cam.ground(-5f, far, l - .025f, l + .025f, .035f), vg(hy, H, 0f to c("#FFE3C2"), .3f to c("#F4C6B0"), 1f to c("#C7A6B2")))
    }

    // --- Ombre portée du tram (soleil bas dans l'axe de la rue)
    run {
        val sh = c("#3A2E4A", .32f); val dx = -19f; val dl = 1.2f
        fill(cam.poly3(0f, -1.2f, .03f, TL, -1.2f, .03f, TL, 1.2f, .03f, TL + dx, 1.2f + dl, .03f, dx, 1.2f + dl, .03f, dx, -1.2f + dl, .03f), sh)
    }

    // --- Arbres côté droit (contre-jour, liseré doré)
    fun tree(a: Float, l: Float, seed: Int, hTop: Float) {
        val r = Mul(seed); val k = cam.k(a, l); val b = cam.p(a, l, .1f); val top = cam.p(a, l, hTop)
        val fork = cam.p(a, l, hTop * .45f)
        line(b.x, b.y, fork.x, fork.y, k * .3f, c("#3B3040"), StrokeCap.Butt)
        for (j in 0 until 3) { val e = cam.p(a, l + (j - 1) * 1.2f, hTop * (.62f + r() * .1f)); line(fork.x, fork.y, e.x, e.y, k * .14f, c("#3B3040")) }
        val cy = top.y + (b.y - top.y) * .22f; val rx = k * hTop * .34f
        crown(top.x, cy, rx, rx * .85f, seed * 7, c("#B8A06A"), c("#4D5843"), c("#262E2E"), -.75f, -.65f)
        glow(top.x - rx * .6f, cy - rx * .5f, rx * .8f, c("#FFC27A", .18f), BlendMode.Screen)
    }
    for (i in 6 downTo 0) tree(6f + i * 12f, 10f, 300 + i, 9.5f)

    // --- Réverbères contemporains (mât anthracite, tête en disque, lumière chaude)
    fun lamp(a: Float, l: Float, hh: Float = 8f) {
        val k = cam.k(a, l); val b = cam.p(a, l, .3f); val tp = cam.p(a, l, hh); val arm = cam.p(a, l + .9f, hh + .2f)
        line(b.x, b.y, tp.x, tp.y, max(1.5f, k * .14f), c("#2E2B36"), StrokeCap.Butt)
        line(tp.x, tp.y, arm.x, arm.y, max(1f, k * .08f), c("#2E2B36"))
        glow(arm.x, arm.y + k * .2f, k * 1.8f, c("#FFC982", .4f), BlendMode.Screen)
        drawOval(c("#3A3642"), topLeft = Offset(arm.x - k * .4f, arm.y - k * .06f), size = Size(k * .8f, k * .13f))
        drawOval(c("#FFF1D0"), topLeft = Offset(arm.x - k * .28f, arm.y + k * .03f), size = Size(k * .56f, k * .06f))
    }
    for (a in listOf(80f, 52f, 30f)) lamp(a, 1.75f)

    // ======== La rame ========
    // dessous (bogies dans l'ombre)
    fill(cam.poly3(corner(.25f), -1.1f, .02f, TL - corner(.25f), -1.1f, .02f, TL - corner(.25f), -1.1f, .3f, corner(.25f), -1.1f, .3f), c("#1E1A24"))
    // Flanc : caisse blanche, dégradé (bas plus sombre, reflet chaud du couchant vers le fond)
    val sideAll = cam.sideRect(0f, TL, .25f, 3.32f)
    val fA = cam.side(corner(1.5f), 1.5f); val fB = cam.side(TL, 1.5f)
    val sTop = cam.side(5f, 3.3f); val sBot = cam.side(5f, .3f)
    fill(sideAll, vg(sTop.y, sBot.y, 0f to c("#FFF6EC"), .45f to c("#F1E8E6"), .8f to c("#D9CBD4"), 1f to c("#A79AAE")))
    fill(sideAll, lg(fA.x, fA.y, fB.x, fB.y, 0f to c("#7E6E96", .18f), .5f to c("#FFB98A", .0f), 1f to c("#FFC796", .5f)))
    clipPath(sideAll) {
        // Livrée arc-en-ciel : bandes obliques + cercles translucides
        fun band(a0: Float, w: Float, h0: Float, h1: Float, rev: Boolean, slant: Float = .22f) {
            for (i in 0 until 6) { val ci = if (rev) 5 - i else i; fill(cam.sideBand(a0 + i * w, w + .02f, h0, h1, slant), c(RAINBOW[ci])) }
        }
        band(corner(.8f) - .1f, .42f, .25f, 1.12f, true, .55f)   // grand dièdre sous la fenêtre de cabine
        band(2.4f, .5f, 2.45f, 3.3f, true)                    // bandeau haut au-dessus de la 1re porte
        band(9.0f, .26f, .25f, 3.3f, false); band(18.6f, .3f, .25f, 3.3f, true); band(26.0f, .28f, .25f, 3.3f, false)
        band(13.2f, .45f, 2.45f, 3.3f, false); band(21.4f, .4f, .25f, 1.12f, true)
        for ((ac, hc, rr) in listOf(Triple(2.3f, .55f, .5f), Triple(3.6f, .9f, .38f), Triple(4.2f, 2.9f, .45f), Triple(9.6f, 1.6f, .5f), Triple(19.4f, .8f, .55f), Triple(26.6f, 2.6f, .5f), Triple(14.6f, 2.95f, .42f), Triple(22.1f, .6f, .4f)))
            fill(cam.sideCircle(ac, hc, rr), Color.White.copy(alpha = .16f))
        // Vitrages (verre sombre reflétant le ciel, intérieur chaud)
        val win = listOf(4.6f to 8.35f, 8.95f to 11.75f, 12.3f to 14.4f, 16.2f to 18.3f, 18.45f to 20.5f, 21.05f to 23.85f, 24.4f to 28.2f)
        val doors = listOf(3.0f, 14.6f, 28.45f)
        fun glass(p: Path, a0: Float) {
            val gt = cam.side(a0, 2.45f); val gb = cam.side(a0, 1.05f)
            fill(p, vg(gt.y, gb.y, 0f to c("#4B3F5E"), .35f to c("#241F33"), 1f to c("#15131D")))
            fill(p, c("#FFB86A", .16f))
        }
        val cabWin = cam.sideRect(0f, 2.7f, 1.12f, 2.62f); glass(cabWin, 1f)
        for ((a0, a1) in win) {
            val p = cam.sideRect(a0, a1, 1.08f, 2.45f); glass(p, a0)
            val mid = (a0 + a1) / 2; val m0 = cam.side(mid, 1.08f); val m1 = cam.side(mid, 2.45f)
            line(m0.x, m0.y, m1.x, m1.y, max(1f, cam.k(mid, -1.2f) * .07f), c("#E9E1E4"), StrokeCap.Butt)
        }
        for (a0 in doors) {
            val p = cam.sideRect(a0, a0 + 1.35f, .45f, 2.42f); glass(p, a0)
            val m0 = cam.side(a0 + .675f, .45f); val m1 = cam.side(a0 + .675f, 2.42f)
            line(m0.x, m0.y, m1.x, m1.y, max(1f, cam.k(a0, -1.2f) * .05f), c("#0D0C12"), StrokeCap.Butt)
            drawPath(p, c("#2B2733"), style = Stroke(max(1f, cam.k(a0, -1.2f) * .06f)))
            val lb = cam.side(a0 - .12f, 2.25f); disc(lb.x, lb.y, max(1f, cam.k(a0, -1.2f) * .04f), c("#FFB547"))
        }
        // Livrée par-dessus le verre (vinyle micro-perforé, en transparence)
        for (i in 0 until 6) fill(cam.sideBand(9.0f + i * .26f, .28f, 1.08f, 2.45f, .22f), c(RAINBOW[i], .35f))
        // Soufflets d'intercirculation
        for (aj in listOf(8.6f, 11.95f, 20.7f, 24.0f)) fill(cam.sideRect(aj, aj + .2f, .3f, 3.32f), c("#2A2630"))
        // Jupe basse et liseré
        fill(cam.sideRect(0f, TL, .25f, .44f), c("#3E3A46"))
        val j0 = cam.side(corner(.46f), .46f); val j1 = cam.side(TL, .46f)
        line(j0.x, j0.y, j1.x, j1.y, max(1f, S / 600f), c("#FFFFFF", .35f))
        // Liseré lumineux du toit (rim light du couchant)
        val r0 = cam.side(corner(3.2f), 3.22f); val r1 = cam.side(TL - 1f, 3.22f)
        line(r0.x, r0.y, r1.x, r1.y, max(1.5f, S / 300f), c("#FFE2BE", .9f))
    }

    // Face avant : bombée, à contre-jour (douce), liseré chaud côté gauche
    val face = cam.frontZone(-1f, 1f, { .25f }, { 3.32f })
    val fl = cam.front(-1f, 1.5f); val fr = cam.front(1f, 1.5f); val ft = cam.front(0f, 3.32f); val fb = cam.front(0f, .25f)
    fill(face, lg(fl.x, 0f, fr.x, 0f, 0f to c("#FFF1E4"), .3f to c("#ECE4EA"), .75f to c("#CFC8D6"), 1f to c("#A99FB6")))
    fill(face, vg(ft.y, fb.y, 0f to c("#FFFFFF", .25f), .55f to c("#FFFFFF", 0f), .85f to c("#6E5F80", .12f), 1f to c("#4E425E", .35f)))
    // Bas de caisse avant
    fill(cam.frontZone(-1f, 1f, { .25f }, { .42f }), c("#45404E"))
    // Pare-brise noir enveloppant, girouette, bandeau du numéro
    val wsb = { q: Float -> 1.13f + .32f * q * q }
    val ws = cam.frontZone(-.96f, .96f, wsb, { 3.08f })
    val wt = cam.front(0f, 3.08f); val wb = cam.front(0f, 1.13f)
    fill(ws, vg(wt.y, wb.y, 0f to c("#16141C"), .28f to c("#1B1824"), .45f to c("#5B4A68"), .7f to c("#2A2536"), 1f to c("#121018")))
    // Reflet glissant du ciel dans la vitre
    clipPath(ws) {
        val sx = (fl.x + (fr.x - fl.x) * (.25f + .2f * sin(t * TAU))); val k = cam.k(0f, 0f)
        fill(poly(sx - k * .2f, wt.y, sx + k * .35f, wt.y, sx - k * .05f, wb.y, sx - k * .6f, wb.y), c("#FFD9C2", .14f))
        fill(poly(sx + k * .45f, wt.y, sx + k * .55f, wt.y, sx + k * .15f, wb.y, sx + k * .05f, wb.y), c("#FFE6D2", .12f))
    }
    // Séparation bandeau / vitre
    run { val path = Path(); for (i in 0..24) { val q = -.9f + 1.8f * i / 24; val o = cam.front(q, 2.68f); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        drawPath(path, c("#55505E"), style = Stroke(max(1f, cam.k(0f, 0f) * .02f))) }
    // Girouette « A Avrillé » (LED orange) et numéro 1004 (blanc)
    run {
        fun q4(q0: Float, q1: Float, h0: Float, h1: Float): FloatArray {
            val a = cam.front(q0, h1); val b = cam.front(q1, h1); val cc = cam.front(q0, h0); val d = cam.front(q1, h0)
            return floatArrayOf(a.x, a.y, b.x, b.y, cc.x, cc.y, d.x, d.y)
        }
        val k = cam.k(0f, 0f)
        val box = q4(-.5f, -.38f, 2.4f, 2.6f)
        fill(quadPath(box, 0f, 0f, 1f, 1f), c("#C2381E"))
        ledText("A", q4(-.48f, -.4f, 2.43f, 2.57f), c("#FFE3B0"), max(1f, k * .018f))
        val led = q4(-.32f, .42f, 2.43f, 2.57f)
        glow(quadPt(led, .5f, .5f).x, quadPt(led, .5f, .5f).y, k * .55f, c("#FF9A2E", .35f), BlendMode.Screen)
        ledText("Avrillé", led, c("#FFA62E"), max(1f, k * .022f))
        ledText("1004", q4(-.17f, .17f, 2.8f, 2.96f), c("#F4F1F4"), max(1f, k * .026f))
    }
    // Pointe noire sous le pare-brise et logo
    run {
        val a = cam.front(-.14f, wsb(-.14f)); val b = cam.front(.14f, wsb(.14f)); val cc = cam.front(0f, .92f)
        fill(poly(a.x, a.y, b.x, b.y, cc.x, cc.y), c("#16141C"))
        val o = cam.front(0f, 1.02f); val k = cam.k(0f, 0f)
        drawCircle(c("#EDE7EE"), radius = k * .045f, center = o, style = Stroke(max(1f, k * .012f)))
    }
    // Essuie-glace
    run { val a = cam.front(.05f, 1.2f); val b = cam.front(-.35f, 2.15f); line(a.x, a.y, b.x, b.y, max(1f, cam.k(0f, 0f) * .02f), c("#0B0A0F")) }
    // Pli du bouclier et feux ronds bas (blancs allumés + feu rouge)
    run {
        val path = Path(); for (i in 0..24) { val q = -.98f + 1.96f * i / 24; val o = cam.front(q, .52f); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        drawPath(path, c("#FFFFFF", .5f), style = Stroke(max(1f, S / 500f)))
        val k = cam.k(0f, 0f)
        for (sq in listOf(-1f, 1f)) {
            for ((q, col) in listOf(.36f to c("#FFF8E8"), .5f to c("#FFF8E8"), .63f to c("#E2322C"))) {
                if (sq > 0f && q > cam.qSil(.78f) - .04f) continue
                val o = cam.front(sq * q, .78f); val rr = k * (if (col.red > .95f && col.green > .9f) .055f else .045f)
                disc(o.x, o.y, rr * 1.35f, c("#3A3442")); disc(o.x, o.y, rr, col)
                glow(o.x, o.y, rr * (if (col.green > .9f) 9f else 4f), if (col.green > .9f) c("#FFF0D0", .5f) else c("#FF4030", .35f), BlendMode.Screen)
            }
        }
        // Halo des phares au sol, devant la rame
        val g = cam.p(-2.5f, 0f, 0f); drawOval(Brush.radialGradient(0f to c("#FFE9C8", .35f), 1f to c("#FFE9C8", 0f), center = g, radius = k * 1.6f), topLeft = Offset(g.x - k * 1.6f, g.y - k * .35f), size = Size(k * 3.2f, k * .7f), blendMode = BlendMode.Screen)
    }

    // --- Quai (béton clair, bande d'éveil de vigilance, bordure)
    val qa0 = -6.2f; val qa1 = 40f
    fill(cam.poly3(qa0, -1.5f, 0f, qa1, -1.5f, 0f, qa1, -1.5f, .3f, qa0, -1.5f, .3f), c("#6E6170"))
    val qTop = cam.p(qa1, -4f, .3f)
    fill(cam.ground(qa0, qa1, -6.2f, -1.5f, .3f), vg(qTop.y, H, 0f to c("#D8B9B2"), .3f to c("#BCA7AD"), 1f to c("#9F919F")))
    fill(cam.ground(qa0, qa1, -1.8f, -1.5f, .3f), vg(qTop.y, H, 0f to c("#F2D8CC"), 1f to c("#D9CDD2")))
    fill(cam.ground(qa0, qa1, -2.4f, -1.95f, .3f), c("#403C48"))
    // Dalles légèrement nuancées
    run {
        val r = Mul(91); var a = qa0 + .5f
        while (a < 22f) { var l = -6.2f; while (l < -2.4f) { val l1 = min(-2.4f, l + 1.3f); fill(cam.ground(a, a + 1.2f, l, l1, .3f), if (r() < .5f) c("#FFFFFF", r() * .07f) else c("#5A4A66", r() * .07f)); l = l1 }; a += 1.2f }
    }
    // Ombres longues des mâts (soleil bas au bout de la rue)
    fun poleShadow(a: Float, l: Float, hh: Float, w: Float) {
        val len = min(hh * 5.8f, a + 5f); val o0 = cam.p(a, l, .3f); val o1 = cam.p(a - len, l + len * .062f, .3f)
        val k0 = cam.k(a, l) * w; val k1 = cam.k(a - len, l) * w
        fill(poly(o0.x - k0, o0.y, o0.x + k0, o0.y, o1.x + k1, o1.y, o1.x - k1, o1.y), lg(o0.x, o0.y, o1.x, o1.y, 0f to c("#3D2F4D", .3f), 1f to c("#3D2F4D", .12f)))
    }
    poleShadow(4f, -5.6f, 7f, .09f); poleShadow(11f, -5.4f, 4f, .06f); poleShadow(9f, -8.2f, 4f, .2f)
    // Joints des dalles
    run {
        val path = Path()
        var a = qa0 + .5f; while (a < qa1) { val o0 = cam.p(a, -6.2f, .3f); val o1 = cam.p(a, -2.4f, .3f); path.moveTo(o0.x, o0.y); path.lineTo(o1.x, o1.y); a += 1.2f }
        for (l in listOf(-3.6f, -4.9f)) { val o0 = cam.p(qa0, l, .3f); val o1 = cam.p(qa1, l, .3f); path.moveTo(o0.x, o0.y); path.lineTo(o1.x, o1.y) }
        drawPath(path, c("#7C6E7C", .45f), style = Stroke(max(1f, S / 420f)))
    }
    // Plots podotactiles (taille selon la distance)
    run {
        var a = qa0 + .05f
        while (a < qa1) {
            val k = cam.k(a, -2.2f); val step = .085f
            if (k * step < 2.2f) break
            val pts = ArrayList<Offset>(); val a1 = a + 1f
            while (a < a1) { for (j in 0 until 5) pts.add(cam.p(a, -2.35f + .09f * j + .045f, .3f)); a += step }
            drawPoints(pts, PointMode.Points, c("#E8E2E6"), strokeWidth = k * .035f, cap = StrokeCap.Round)
        }
    }
    // Granulats du béton (premier plan)
    run {
        val r = Mul(88); val pts = ArrayList<Offset>()
        for (i in 0 until 900) { val a = qa0 + 12f * r().pow(2f); val l = -6.2f + r() * 3.8f; if (l > -2.4f) continue; pts.add(cam.p(a, l, .3f)) }
        drawPoints(pts, PointMode.Points, c("#FFFFFF", .35f), strokeWidth = max(1f, S / 360f), cap = StrokeCap.Round)
    }
    lamp(4f, -5.6f, 7f)
    // Poteau d'arrêt « Ⓣ » sur le quai
    run {
        val a = 11f; val l = -5.4f; val k = cam.k(a, l); val b = cam.p(a, l, .3f); val tp = cam.p(a, l, 3.6f)
        line(b.x, b.y, tp.x, tp.y, max(1.5f, k * .1f), c("#3A3642"), StrokeCap.Butt)
        val ring = cam.p(a, l, 3.95f)
        drawCircle(c("#F1EAF0"), radius = k * .32f, center = ring, style = Stroke(k * .07f))
        line(ring.x - k * .16f, ring.y - k * .12f, ring.x + k * .16f, ring.y - k * .12f, k * .07f, c("#F1EAF0"), StrokeCap.Butt)
        line(ring.x, ring.y - k * .12f, ring.x, ring.y + k * .2f, k * .07f, c("#F1EAF0"), StrokeCap.Butt)
    }
    // Arbre en contre-jour à gauche (cadrage)
    tree(9f, -8.2f, 401, 10f)
    // Brume chaude de l'heure dorée dans l'axe
    mist(hy - H * .01f, H * .06f, c("#FFD2A8", .3f))
}

/* ------------------------------------------------------------------ petites formes partagées */

/** Feuille pointue (axe horizontal, pétiole à gauche), centrée en (x, y), tournée de [rot] degrés. */
private fun DrawScope.leaf(x: Float, y: Float, len: Float, rot: Float, col: Color, wide: Float = .42f) {
    val p = Path(); val w = len * wide
    p.moveTo(-len * .5f, 0f); p.cubicTo(-len * .2f, -w, len * .25f, -w * .8f, len * .5f, 0f); p.cubicTo(len * .25f, w * .8f, -len * .2f, w, -len * .5f, 0f); p.close()
    translate(x, y) { rotate(rot, Offset.Zero) { drawPath(p, col) } }
}
/** Branche récursive (arbre nu, cerisier) : renvoie les extrémités pour y poser fleurs ou feuilles. */
private fun branches(p: Path, x: Float, y: Float, len: Float, ang: Float, w: Float, depth: Int, r: Mul, tips: MutableList<Offset>, spread: Float = 26f, droop: Float = 0f) {
    val a = (ang + droop) * TAU / 360f
    val ex = x + cos(a) * len; val ey = y + sin(a) * len
    val bend = (r() - .5f) * len * .35f; val mx = (x + ex) / 2 - sin(a) * bend; val my = (y + ey) / 2 + cos(a) * bend
    val nx = -sin(a) * w / 2; val ny = cos(a) * w / 2; val w2 = w * .62f; val nx2 = -sin(a) * w2 / 2; val ny2 = cos(a) * w2 / 2
    p.moveTo(x + nx, y + ny); p.cubicTo(mx + nx, my + ny, mx + nx2, my + ny2, ex + nx2, ey + ny2); p.lineTo(ex - nx2, ey - ny2)
    p.cubicTo(mx - nx2, my - ny2, mx - nx, my - ny, x - nx, y - ny); p.close()
    if (depth == 0 || len < 2f) { tips.add(Offset(ex, ey)); return }
    val n = if (r() < .3f) 3 else 2
    for (i in 0 until n) {
        val da = (i - (n - 1) / 2f) * spread * 1.4f + (r() - .5f) * spread
        branches(p, ex, ey, len * (.66f + r() * .16f), ang + da, w2, depth - 1, r, tips, spread, droop)
    }
}
/** Chauve-souris (ailes battantes selon [flap] ∈ [-1, 1]). */
private fun DrawScope.bat(x: Float, y: Float, s: Float, flap: Float, col: Color) {
    val p = Path(); val up = flap * s * .55f
    p.moveTo(x, y - s * .12f)
    p.cubicTo(x - s * .3f, y - s * .2f - up, x - s * .7f, y - s * .25f - up, x - s, y - up * 1.1f)
    p.cubicTo(x - s * .8f, y + s * .05f - up * .5f, x - s * .65f, y + s * .1f - up * .3f, x - s * .5f, y + s * .05f)
    p.cubicTo(x - s * .35f, y + s * .15f, x - s * .2f, y + s * .1f, x, y + s * .22f)
    p.cubicTo(x + s * .2f, y + s * .1f, x + s * .35f, y + s * .15f, x + s * .5f, y + s * .05f)
    p.cubicTo(x + s * .65f, y + s * .1f - up * .3f, x + s * .8f, y + s * .05f - up * .5f, x + s, y - up * 1.1f)
    p.cubicTo(x + s * .7f, y - s * .25f - up, x + s * .3f, y - s * .2f - up, x, y - s * .12f)
    p.close(); drawPath(p, col)
    disc(x, y, s * .13f, col)
}
/** Poisson stylisé orienté vers la droite (ou la gauche si [dir] < 0). */
private fun DrawScope.fish(x: Float, y: Float, s: Float, dir: Float, body: Color, belly: Color, wag: Float) {
    val p = Path(); val d = if (dir < 0) -1f else 1f
    p.moveTo(x + d * s, y)
    p.cubicTo(x + d * s * .6f, y - s * .42f, x - d * s * .3f, y - s * .38f, x - d * s * .62f, y)
    p.cubicTo(x - d * s * .3f, y + s * .34f, x + d * s * .6f, y + s * .36f, x + d * s, y); p.close()
    drawPath(p, Brush.verticalGradient(0f to body, 1f to belly, startY = y - s * .4f, endY = y + s * .36f))
    val tx = x - d * s * .58f
    drawPath(poly(tx, y, tx - d * s * .42f, y - s * (.3f + wag * .08f), tx - d * s * .34f, y + wag * s * .05f, tx - d * s * .42f, y + s * (.3f - wag * .08f)), body)
    disc(x + d * s * .62f, y - s * .06f, s * .06f, Color(0f, 0f, 0f, .6f))
}

/* ------------------------------------------------------------------ Nébuleuse */
private fun DrawScope.nebula(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#04020C"), .35f to c("#0B0628"), .7f to c("#160A36"), 1f to c("#08041A"))
    starField(41, 520, H, c("#E8E4FF", .75f), t)
    val n = Nz(5); val r = Mul(7); val sw = sin(t * TAU) * S * .01f
    fun arm(u: Float) = Offset(W * (.05f + .9f * u + .1f * sin(u * 6f)), H * (.16f + .62f * u + .05f * n.at(u * 3f)))
    val cols = listOf(c("#C14BA8"), c("#6A3FE0"), c("#2BB3C0"), c("#FF8A6B"), c("#E0559A"))
    // Grands voiles colorés, allongés dans l'axe du bras
    for (i in 0 until 90) {
        val u = r(); val o = arm(u); val sz = S * (.08f + r() * .3f) * (1.1f - abs(u - .5f) * .8f)
        val col = cols[(r() * cols.size).toInt().coerceIn(0, cols.size - 1)]
        val px = o.x + (r() - .5f) * S * .35f + sw; val py = o.y + (r() - .5f) * S * .3f
        rotate(50f + (r() - .5f) * 40f, Offset(px, py)) { ellGlow(px, py, sz, sz * (.35f + r() * .4f), col.a(.08f + r() * .1f), BlendMode.Screen) }
    }
    // Cœur lumineux
    val core = arm(.48f)
    glow(core.x, core.y, S * .5f, c("#FF9ACB", .25f), BlendMode.Screen); glow(core.x, core.y, S * .2f, c("#FFE6F2", .45f), BlendMode.Screen)
    // Volutes fines, très étirées
    for (i in 0 until 22) {
        val o = arm(r()); val px = o.x + (r() - .5f) * S * .4f; val py = o.y + (r() - .5f) * S * .3f
        rotate(50f + (r() - .5f) * 50f, Offset(px, py)) { ellGlow(px, py, S * (.12f + r() * .14f), S * (.02f + r() * .025f), cols[i % cols.size].a(.16f), BlendMode.Screen) }
    }
    // Poussière sombre (bandes d'absorption)
    for (i in 0 until 16) {
        val u = .15f + r() * .7f; val o = arm(u); val px = o.x + S * .06f + (r() - .5f) * S * .1f; val py = o.y - S * .02f + (r() - .5f) * S * .08f
        rotate(50f + (r() - .5f) * 30f, Offset(px, py)) { ellGlow(px, py, S * (.06f + r() * .14f), S * (.02f + r() * .04f), c("#07031A", .35f)) }
    }
    // Étoiles jeunes dans le nuage, quelques aigrettes
    starField(43, 160, H, c("#FFE9F7", .9f), t, 7)
    // Étoiles filantes (deux passages par cycle)
    for (k in 0 until 2) {
        val ph = (t * 2f + k * .5f) % 1f
        if (ph < .18f) {
            val e = ph / .18f; val sx = W * (.85f - k * .4f); val sy = H * (.08f + k * .2f)
            val hx = sx - e * W * .5f; val hy = sy + e * W * .25f; val tl = W * .16f
            drawLine(Brush.linearGradient(0f to c("#FFFFFF", 0f), 1f to c("#FFFFFF", .9f * (1f - e)), start = Offset(hx + tl, hy - tl * .5f), end = Offset(hx, hy)), Offset(hx + tl, hy - tl * .5f), Offset(hx, hy), strokeWidth = S * .004f, cap = StrokeCap.Round)
            glow(hx, hy, S * .02f, c("#FFFFFF", .8f * (1f - e)), BlendMode.Screen)
        }
    }
}

/* ------------------------------------------------------------------ Fonds marins */
private fun DrawScope.deepsea(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#2FA3CF"), .1f to c("#137BAE"), .38f to c("#0A4F7A"), .7f to c("#052C4A"), 1f to c("#02152A"))
    // Surface : soleil diffus et ondulations (caustiques)
    ellGlow(W * .55f, -H * .02f, W * .9f, H * .12f, c("#CFF6FF", .55f), BlendMode.Screen)
    for (j in 0 until 7) {
        val p = Path(); val y0 = H * (.012f + j * .013f)
        for (i in 0..40) { val x = W * i / 40f; val y = y0 + sin(x / W * (14f + j * 3f) + t * TAU + j) * H * .004f; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
        drawPath(p, c("#E6FBFF", .16f - j * .018f), style = Stroke(S * .004f))
    }
    rays(W * .52f, -H * .06f, H * 1.05f, 11, 90f, 46f, c("#B8F0FF", .09f), 12, t)
    // Raie manta dans le lointain
    run {
        val mx = W * (1.2f - ((t + .3f) % 1f) * 1.5f); val my = H * .3f + sin(t * TAU * 2) * H * .01f; val s = S * .16f; val f = sin(t * TAU * 3) * .2f
        val p = Path(); p.moveTo(mx - s * .3f, my); p.cubicTo(mx - s * .1f, my - s * (.4f + f), mx + s * .4f, my - s * (.6f + f), mx + s * .2f, my - s * (.05f)); p.lineTo(mx + s * .6f, my + s * .05f)
        p.cubicTo(mx + s * .25f, my + s * .1f, mx + s * .4f, my + s * (.6f + f), mx - s * .1f, my + s * (.35f + f)); p.close()
        drawPath(p, c("#0B4467", .55f)); line(mx + s * .5f, my + s * .04f, mx + s * 1.2f, my + s * .1f, S * .003f, c("#0B4467", .5f))
    }
    // Fonds rocheux en trois plans (brume bleue)
    val tones = listOf(c("#0C4F72"), c("#083553"), c("#041D30"))
    for (k in 0 until 3) {
        val nz = Nz(80 + k); val base = H * (.74f + k * .08f); val amp = H * (.12f - k * .025f)
        val pts = ridgePts(base, amp, 90) { u -> .55f * nz.ridged(u * (2f + k), 4) + .45f * (nz.at(u * 1.4f, 3) * .5f + .5f) }
        ridgeFill(pts, vg(base - amp, H, 0f to tones[k], 1f to mix(tones[k], c("#010A14"), .5f)))
        litFaces(pts, true, H * .02f, c("#58C6E8", .18f - k * .04f))
        // Algues qui ondulent
        val r = Mul(90 + k)
        for (i in 0 until 9 + k * 3) {
            val x = r() * W; val idx = ((x / W) * 90).toInt().coerceIn(0, 89); val yb = pts[idx * 2 + 1] + H * .01f
            val h = H * (.08f + r() * .14f) * (1f + k * .25f); val w = S * (.012f + r() * .01f) * (1f + k * .3f); val ph = r() * TAU
            val p = Path(); val m = 12
            for (j in 0..m) { val u = j / m.toFloat(); val sx = x + sin(u * 3f + t * TAU + ph) * w * 2.2f * u; val y = yb - h * u; if (j == 0) p.moveTo(sx - w, y) else p.lineTo(sx - w * (1f - u * .8f), y) }
            for (j in m downTo 0) { val u = j / m.toFloat(); val sx = x + sin(u * 3f + t * TAU + ph) * w * 2.2f * u; val y = yb - h * u; p.lineTo(sx + w * (1f - u * .8f), y) }
            p.close(); drawPath(p, mix(mix(c("#0E5A4E"), tones[k], .55f), c("#031220"), k * .25f))
        }
    }
    // Bancs de poissons (un passage par cycle)
    val r = Mul(31)
    for (sch in 0 until 2) {
        val cy = H * (.42f + sch * .14f); val sz = S * (.018f + sch * .01f); val sp = if (sch == 0) 1f else -1f
        for (i in 0 until 26) {
            val off = r(); val dy = (r() - .5f) * H * .09f; val u = ((off * .25f + t * sp) % 1f + 1f) % 1f
            val x = -W * .2f + u * W * 1.4f; val y = cy + dy + sin(u * TAU * 2 + off * 6f) * H * .01f
            fish(x, y, sz * (.8f + r() * .4f), sp, mix(c("#9FD8F0"), c("#2C7AA8"), sch * .5f), c("#E8F8FF"), sin(t * TAU * 12 + i))
        }
    }
    // Bulles qui montent
    for (i in 0 until 24) {
        val x0 = W * (.15f + .7f * ((i * 37) % 23) / 23f); val sp = .6f + (i % 5) * .2f; val u = ((i * .137f) + t * sp) % 1f
        val y = H * (1.02f - u * 1.1f); val x = x0 + sin(u * 18f + i) * S * .01f; val rr = S * (.004f + (i % 4) * .003f)
        drawCircle(c("#DDF7FF", .5f), radius = rr, center = Offset(x, y), style = Stroke(max(1f, rr * .25f))); disc(x - rr * .35f, y - rr * .35f, rr * .25f, c("#FFFFFF", .7f))
    }
    // Neige marine
    val m = Mul(5); val pts = ArrayList<Offset>()
    for (i in 0 until 160) { val x = m() * W; val y = ((m() + t * .3f) % 1f) * H; pts.add(Offset(x + sin(t * TAU + i) * S * .005f, y)) }
    drawPoints(pts, PointMode.Points, c("#CFEFFF", .35f), strokeWidth = max(1f, S / 360f), cap = StrokeCap.Round)
}

/* ------------------------------------------------------------------ Ville la nuit (au bord du fleuve) */
private fun DrawScope.citynight(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val hz = H * .72f
    sky(0f to c("#060A1F"), .3f to c("#0E1840"), .55f to c("#22306A"), .66f to c("#4A3576"), .72f to c("#7A4A7E"), y1 = hz)
    starField(51, 160, hz * .6f, c("#DDE6FF", .7f), t, 2)
    val mx = W * .74f; val my = H * .17f; val mr = S * .075f
    glow(mx, my, S * .7f, c("#9DB4FF", .18f), BlendMode.Screen); glow(mx, my, S * .22f, c("#E9EEFF", .3f), BlendMode.Screen)
    disc(mx, my, mr, c("#F3EEDD"))
    for ((dx, dy, rr) in listOf(Triple(-.3f, -.2f, .28f), Triple(.25f, .15f, .36f), Triple(-.1f, .4f, .2f), Triple(.35f, -.35f, .16f))) glow(mx + dx * mr, my + dy * mr, rr * mr, c("#C9BFA8", .5f))
    drawCircle(Brush.radialGradient(0f to Color.Transparent, .7f to Color.Transparent, 1f to c("#B9AE98", .5f), center = Offset(mx - mr * .2f, my - mr * .2f), radius = mr * 1.25f), radius = mr, center = Offset(mx, my))
    // Nuages fins éclairés par la lune
    val dr = t * W * .06f
    for (k in 0 until 3) { val x = (W * (.2f + k * .35f) + dr) % (W * 1.3f) - W * .15f; ellGlow(x, H * (.22f + k * .07f), W * .3f, H * .012f, c("#5C6AA8", .35f)); ellGlow(x, H * (.22f + k * .07f) - H * .004f, W * .22f, H * .006f, c("#C9D2FF", .18f), BlendMode.Screen) }
    // Halo de la ville sur l'horizon
    ellGlow(W * .5f, hz, W * .9f, H * .08f, c("#FF9C7A", .25f), BlendMode.Screen)
    // Trois plans d'immeubles
    fun skyline(seed: Int, base: Float, hMin: Float, hMax: Float, col: Color, winA: Float, near: Boolean) {
        val r = Mul(seed); var x = -S * .05f
        val wins = ArrayList<Offset>(); val wins2 = ArrayList<Offset>(); val reds = ArrayList<Offset>()
        while (x < W) {
            val w = S * (.06f + r() * .1f) * (if (near) 1.4f else 1f); val h = H * (hMin + r() * (hMax - hMin)); val top = base - h
            drawRect(vg(top, base, 0f to mix(col, c("#3A3A7A"), .25f), 1f to col), topLeft = Offset(x, top), size = Size(w + 1f, h))
            // Couronnements : flèche, gradins ou antenne
            when ((r() * 4).toInt()) {
                0 -> { rect(x + w * .2f, top - h * .05f, w * .6f, h * .05f + 1f, col); rect(x + w * .35f, top - h * .09f, w * .3f, h * .04f + 1f, col) }
                1 -> { line(x + w * .5f, top, x + w * .5f, top - h * .18f, max(1f, S * .003f), col); reds.add(Offset(x + w * .5f, top - h * .18f)) }
                2 -> fill(poly(x, top, x + w * .5f, top - w * .5f, x + w, top), col)
                else -> {}
            }
            // Fenêtres
            val cw = S * (if (near) .014f else .009f); val chh = cw * 1.5f; var wy = top + chh
            while (wy < base - chh) { var wx = x + cw * .8f; while (wx < x + w - cw * 1.5f) { val v = r(); if (v < winA) wins.add(Offset(wx, wy)) else if (v < winA * 1.4f) wins2.add(Offset(wx, wy)); wx += cw * 1.8f }; wy += chh * 1.6f }
            x += w + S * .005f
        }
        val ws = S * (if (near) .009f else .006f)
        drawPoints(wins, PointMode.Points, c("#FFD08A", if (near) .9f else .6f), strokeWidth = ws, cap = StrokeCap.Square)
        drawPoints(wins2, PointMode.Points, c("#9FD0FF", if (near) .6f else .4f), strokeWidth = ws, cap = StrokeCap.Square)
        for ((i, o) in reds.withIndex()) { val on = .5f + .5f * sin(t * TAU * 6 + i); glow(o.x, o.y, S * .02f, c("#FF3B3B", .6f * on), BlendMode.Screen); disc(o.x, o.y, S * .003f, c("#FF6060")) }
    }
    skyline(7, hz, .06f, .16f, c("#3A3270"), .25f, false)
    mist(hz - H * .01f, H * .04f, c("#B0709A", .35f))
    skyline(8, hz + H * .01f, .09f, .26f, c("#211F4C"), .3f, false)
    skyline(9, hz + H * .025f, .05f, .2f, c("#12112E"), .35f, true)
    // Quai et fleuve
    val q = hz + H * .03f
    rect(0f, q - H * .006f, W, H * .01f, c("#0B0A1E"))
    val lamps = ArrayList<Offset>(); for (i in 0..14) lamps.add(Offset(W * (i + .5f) / 15f, q - H * .012f))
    for (o in lamps) { glow(o.x, o.y, S * .035f, c("#FFC27A", .45f), BlendMode.Screen); disc(o.x, o.y, S * .004f, c("#FFE7BF")) }
    drawRect(vg(q, H, 0f to c("#1A1A46"), .4f to c("#0C0E2A"), 1f to c("#05061A")), topLeft = Offset(0f, q), size = Size(W, H - q))
    // Reflets : traits horizontaux scintillants sous les lumières
    val r = Mul(77)
    for (i in 0 until 160) {
        val x = r() * W; val y = q + (H - q) * r().pow(1.6f) * .9f + H * .005f; val wl = S * (.01f + r() * .05f) * (1f + (y - q) / H * 3f)
        val col = if (r() < .7f) c("#FFC27A") else c("#9FD0FF"); val a = (.15f + .35f * (.5f + .5f * sin(t * TAU * 3 + i * 1.7f))) * (1f - (y - q) / (H - q) * .7f)
        line(x - wl / 2, y, x + wl / 2, y, max(1f, S * .003f), col.a(a))
    }
    // Reflet de la lune
    for (i in 0 until 14) { val y = q + (H - q) * (.05f + i * .06f); val wl = S * (.04f + i * .012f) * (.8f + .2f * sin(t * TAU * 2 + i)); line(mx - wl, y, mx + wl, y, S * .004f, c("#F3EEDD", .35f - i * .02f)) }
}

/* ------------------------------------------------------------------ Aurore boréale sur la toundra */
private fun DrawScope.auroraCurtains(t: Float, alpha: Float) {
    val W = size.width; val H = size.height; val ph = sin(t * TAU) * .6f; val ph2 = cos(t * TAU) * .4f
    val specs = listOf(Triple(.24f, .2f, 3), Triple(.34f, .16f, 4), Triple(.42f, .12f, 5))
    for ((k, sp) in specs.withIndex()) {
        val (yb0, hh, seed) = sp; val n1 = Nz(seed); val n2 = Nz(seed + 10); val n3 = Nz(seed + 20); val N = 140
        for (i in 0 until N) {
            val u = i / (N - 1f); val x = W * u
            val yb = H * (yb0 + .06f * n1.at(u * 2.2f + ph + k, 3)); val h = H * hh * (.6f + .5f * (n2.at(u * 3f + ph2, 3) * .5f + .5f))
            val ray = (.45f + .55f * (n3.at(u * 38f + ph * 3f, 2) * .5f + .5f)) * (.35f + .65f * sm(0f, .25f, u) * sm(1f, .7f, u)) * alpha
            val g = if (k == 1) c("#7CFFC4") else c("#45F0A0"); val v = if (k == 2) c("#C76BFF") else c("#8A5CFF")
            drawRect(Brush.verticalGradient(0f to v.a(0f), .45f to v.a(.12f * ray), .8f to g.a(.38f * ray), .95f to g.a(.55f * ray), 1f to g.a(0f), startY = yb - h, endY = yb + H * .01f),
                topLeft = Offset(x - W / N, yb - h), size = Size(W / N * 2.2f, h + H * .01f), blendMode = BlendMode.Screen)
        }
    }
}
private fun DrawScope.borealis(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val hz = H * .66f
    sky(0f to c("#01040C"), .35f to c("#03111F"), .6f to c("#06223A"), 1f to c("#0B3A44"), y1 = hz)
    starField(61, 460, hz, c("#E6F2FF", .8f), t, 4)
    auroraCurtains(t, 1f)
    ellGlow(W * .5f, hz, W, H * .1f, c("#38E0A0", .12f), BlendMode.Screen)
    // Montagnes enneigées, rebord teinté par l'aurore
    for (k in 0 until 2) {
        val nz = Nz(70 + k); val base = hz + H * k * .015f; val amp = H * (.09f - k * .03f)
        val pts = ridgePts(base, amp, 100) { u -> .6f * nz.ridged(u * (2.5f + k), 4) + .4f * (nz.at(u, 2) * .5f + .5f) }
        ridgeFill(pts, vg(base - amp, base + H * .02f, 0f to mix(c("#2A4B63"), c("#0B1A2A"), k * .6f), 1f to c("#08131F")), base + H * .02f)
        litFaces(pts, true, H * .02f, c("#9FFFD8", .25f - k * .1f))
        if (k == 0) snowCaps(pts, base - amp * .55f, c("#CFE9F2", .35f))
    }
    // Lac gelé : reflet de l'aurore
    val ly = hz + H * .02f; val lb = H * .8f
    drawRect(vg(ly, lb, 0f to c("#0A2A3A"), 1f to c("#051622")), topLeft = Offset(0f, ly), size = Size(W, lb - ly))
    clipRect(0f, ly, W, lb) { scale(1f, -1f, Offset(0f, ly)) { auroraCurtains(t, .55f) } }
    for (i in 0 until 10) { val y = ly + (lb - ly) * (i + .5f) / 10f; line(0f, y, W, y, max(1f, S * .002f), c("#CFF6FF", .05f)) }
    // Rive enneigée, sapins et cabane
    val nz = Nz(91); val shore = { x: Float -> lb + nz.at(x / W * 3f, 3) * H * .01f }
    conifers(22, 12, H * .05f, H * .12f, c("#04101A"), shore)
    ridgePts(lb, H * .012f, 60) { u -> nz.at(u * 3f, 3) }.let { ridgeFill(it, vg(lb, H, 0f to c("#2B4A5E"), .3f to c("#1A3042"), 1f to c("#0A1622"))) }
    val cx = W * .68f; val cy = lb + H * .005f; val cw = S * .12f
    fill(poly(cx, cy, cx + cw, cy, cx + cw, cy - cw * .45f, cx, cy - cw * .45f), c("#0C1520"))
    fill(poly(cx - cw * .1f, cy - cw * .42f, cx + cw * .5f, cy - cw * .85f, cx + cw * 1.1f, cy - cw * .42f), c("#0A111A"))
    fill(poly(cx - cw * .1f, cy - cw * .42f, cx + cw * .5f, cy - cw * .85f, cx + cw * .5f, cy - cw * .78f, cx, cy - cw * .4f), c("#DDEFFF", .5f))
    rect(cx + cw * .2f, cy - cw * .3f, cw * .2f, cw * .17f, c("#FFC36E")); rect(cx + cw * .6f, cy - cw * .3f, cw * .2f, cw * .17f, c("#FFB25A"))
    glow(cx + cw * .5f, cy - cw * .2f, cw * 1.2f, c("#FFB25A", .3f), BlendMode.Screen)
    for (i in 0 until 6) { val u = ((t * 2f + i / 6f) % 1f); glow(cx + cw * .8f + u * cw * .4f + sin(u * 6f) * cw * .05f, cy - cw * (.9f + u * 1.2f), cw * (.08f + u * .15f), c("#9FB4C8", .18f * (1f - u))) }
    val snow = Mul(4); val pts = ArrayList<Offset>(); for (i in 0 until 200) { val y = lb + (H - lb) * snow(); pts.add(Offset(snow() * W, y)) }
    drawPoints(pts, PointMode.Points, c("#DDF2FF", .3f), strokeWidth = max(1f, S / 450f), cap = StrokeCap.Round)
}

/* ------------------------------------------------------------------ Lagon */
private fun DrawScope.palm(x: Float, y: Float, h: Float, lean: Float, t: Float, seed: Int, trunk: Color, frond: Color, frondLit: Color) {
    val r = Mul(seed); val S = min(size.width, size.height)
    val tx = x + lean * h; val ty = y - h; val w = h * .04f
    val p = Path(); p.moveTo(x - w, y); p.cubicTo(x - w, y - h * .45f, tx - w * .5f - lean * h * .25f, ty + h * .25f, tx - w * .45f, ty)
    p.lineTo(tx + w * .45f, ty); p.cubicTo(tx + w * .5f - lean * h * .25f, ty + h * .25f, x + w, y - h * .45f, x + w, y); p.close()
    drawPath(p, Brush.horizontalGradient(0f to mix(trunk, Color.White, .2f), .45f to trunk, 1f to mix(trunk, Color.Black, .4f), startX = x - w, endX = x + w))
    val sway = sin(t * TAU + seed) * 3f
    // Palmes : rachis arqué, folioles fines qui retombent (dessous sombre puis dessus clair)
    for (pass in 0 until 2) for (i in 0 until 11) {
        val r2 = Mul(seed * 31 + i)
        val ang = -170f + i * 16f + (r2() - .5f) * 8f + sway; val len = h * (.4f + r2() * .14f) * (if (abs(ang + 90f) < 30f) .75f else 1f); val a = ang * TAU / 360f
        val ex = tx + cos(a) * len; val ey = ty + sin(a) * len * .35f + len * .45f
        val mx = tx + cos(a) * len * .55f; val my = ty + sin(a) * len * .55f - len * .05f
        val n = 22; val leafP = Path()
        for (j in 2..n) {
            val u = j / n.toFloat(); val bx = (1 - u) * (1 - u) * tx + 2 * (1 - u) * u * mx + u * u * ex; val by = (1 - u) * (1 - u) * ty + 2 * (1 - u) * u * my + u * u * ey
            val dx = 2 * (1 - u) * (mx - tx) + 2 * u * (ex - mx); val dy = 2 * (1 - u) * (my - ty) + 2 * u * (ey - my); val dl = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val ll = len * .3f * sin(u * 3.1f).coerceAtLeast(.12f)
            for (sd in listOf(-1f, 1f)) { val nx = -dy / dl * sd; val ny = dx / dl * sd; leafP.moveTo(bx, by); leafP.lineTo(bx + nx * ll * .55f + dx / dl * ll * .45f, by + ny * ll * .55f + ll * .6f) }
        }
        val spine = Path(); spine.moveTo(tx, ty); spine.cubicTo(mx, my, mx, my, ex, ey)
        if (pass == 0) drawPath(leafP, frond, style = Stroke(max(1f, len * .03f), cap = StrokeCap.Round))
        else { drawPath(leafP, frondLit.a(.55f), style = Stroke(max(1f, len * .012f), cap = StrokeCap.Round)); drawPath(spine, mix(frond, Color.Black, .25f), style = Stroke(max(1f, len * .02f), cap = StrokeCap.Round)) }
    }
    disc(tx, ty + h * .015f, h * .03f, mix(trunk, Color.Black, .35f))
}
private fun DrawScope.lagoon(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val hz = H * .52f
    sky(0f to c("#2E9FE3"), .25f to c("#5CC3F2"), .45f to c("#A6E6FA"), .52f to c("#E1F8F6"), y1 = hz + 2f)
    glow(W * .8f, H * .1f, S * .9f, c("#FFFBE8", .45f), BlendMode.Screen); glow(W * .8f, H * .1f, S * .12f, c("#FFFFFF", .9f), BlendMode.Screen)
    // Cumulus doux (corps lumineux, base légèrement bleutée)
    val dr = t * W * .05f
    for ((cx, cy, cw, sd) in listOf(arrayOf(.22f, .3f, .55f, 1f), arrayOf(.7f, .4f, .45f, 2f), arrayOf(.45f, .46f, .35f, 3f))) {
        val r = Mul((sd as Float).toInt() * 13); val x0 = W * (cx as Float) + dr; val y0 = H * (cy as Float); val w = W * (cw as Float)
        for (i in 0 until 14) { val u = (r() - .5f) * w; val v = -abs(r() - .5f) * w * .25f; ellGlow(x0 + u, y0 + v, w * (.16f + r() * .14f), w * (.1f + r() * .08f), c("#FFFFFF", .5f)) }
        ellGlow(x0, y0 + w * .03f, w * .5f, w * .05f, c("#9CCBE6", .35f))
    }
    // Mer : bleu profond à l'horizon → turquoise du lagon
    drawRect(vg(hz, H * .78f, 0f to c("#1D74B0"), .2f to c("#2299C2"), .55f to c("#33C4CC"), 1f to c("#8CE9DA")), topLeft = Offset(0f, hz), size = Size(W, H * .78f - hz))
    // Îlot et cocotiers lointains
    fill(poly(W * .05f, hz + 1f, W * .1f, hz - H * .008f, W * .22f, hz - H * .01f, W * .3f, hz + 1f), c("#3F7F6B"))
    for (i in 0 until 3) palm(W * (.12f + i * .05f), hz - H * .006f, H * .04f, .2f - i * .15f, t, 50 + i, c("#56715F"), c("#3E7A5E"), c("#4F8E6D"))
    // Reflets du soleil, scintillants
    val r = Mul(3)
    for (i in 0 until 120) {
        val y = hz + (H * .26f) * r().pow(2f); val x = W * .8f + (r() - .5f) * W * (.15f + (y - hz) / H * 2f); val wl = S * (.005f + r() * .02f)
        line(x - wl, y, x + wl, y, max(1f, S * .002f), c("#FFFFFF", (.3f + .5f * (.5f + .5f * sin(t * TAU * 4 + i))) * (1f - (y - hz) / (H * .3f))))
    }
    // Hauts-fonds plus clairs
    ellGlow(W * .4f, H * .72f, W * .6f, H * .04f, c("#B6F5E4", .5f))
    // Sable : estran mouillé, écume qui va et vient
    val sw = sin(t * TAU) * H * .008f
    val shore = Nz(8)
    val sp = ridgePts(H * .76f + sw, H * .012f, 60) { u -> shore.at(u * 3f, 3) }
    ridgeFill(sp, vg(H * .74f, H, 0f to c("#E6D6A8"), .15f to c("#F2E2B6"), 1f to c("#E9CD94")))
    ridgeFill(sp, vg(H * .75f, H * .8f, 0f to c("#BFAE86", .6f), 1f to c("#BFAE86", 0f)))
    val foam = Path(); var i = 0; while (i < sp.size) { if (i == 0) foam.moveTo(sp[i], sp[i + 1]) else foam.lineTo(sp[i], sp[i + 1]); i += 2 }
    drawPath(foam, c("#FFFFFF", .85f), style = Stroke(S * .006f, cap = StrokeCap.Round))
    val foam2 = Path(); i = 0; while (i < sp.size) { val y = sp[i + 1] - H * .012f - sw * .5f; if (i == 0) foam2.moveTo(sp[i], y) else foam2.lineTo(sp[i], y); i += 2 }
    drawPath(foam2, c("#FFFFFF", .4f), style = Stroke(S * .003f, cap = StrokeCap.Round))
    val g = Mul(9); val gp = ArrayList<Offset>(); for (k in 0 until 500) gp.add(Offset(g() * W, H * .78f + g() * H * .22f))
    drawPoints(gp, PointMode.Points, c("#B8986A", .35f), strokeWidth = max(1f, S / 450f))
    // Cocotiers au premier plan (cadrage à droite) et leur ombre
    fill(poly(W * .93f, H * .99f, W * .45f, H * .93f, W * .5f, H * .955f, W * .98f, H), c("#A88A5E", .3f))
    palm(W * .98f, H * .99f, H * .3f, -.3f, t, 7, c("#8A6F55"), c("#246B45"), c("#7CC46E"))
    palm(W * .03f, H * .93f, H * .2f, .18f, t, 8, c("#937A5F"), c("#21633F"), c("#6DB766"))
}

/* ------------------------------------------------------------------ Manoir hanté */
private fun DrawScope.haunted(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#07040F"), .3f to c("#150A2C"), .55f to c("#2E1650"), .72f to c("#4A1F4A"), 1f to c("#1A0B1E"))
    starField(71, 180, H * .5f, c("#E9DCFF", .6f), t)
    val mx = W * .6f; val my = H * .25f; val mr = S * .17f
    glow(mx, my, S * 1.1f, c("#B48CFF", .18f), BlendMode.Screen); glow(mx, my, mr * 1.7f, c("#FFF2D6", .35f), BlendMode.Screen)
    drawCircle(Brush.radialGradient(0f to c("#FFF8E6"), .8f to c("#F2E2C0"), 1f to c("#D9C39C"), center = Offset(mx - mr * .2f, my - mr * .25f), radius = mr * 1.3f), radius = mr, center = Offset(mx, my))
    for ((dx, dy, rr) in listOf(Triple(-.35f, -.1f, .3f), Triple(.2f, .25f, .36f), Triple(.3f, -.4f, .18f), Triple(-.15f, .45f, .16f))) glow(mx + dx * mr, my + dy * mr, rr * mr, c("#BFA47C", .45f))
    // Nuages effilochés devant la lune
    val dr = t * W * .1f
    for (k in 0 until 3) { val x = (W * (.1f + k * .4f) + dr) % (W * 1.4f) - W * .2f; val y = H * (.22f + k * .05f); ellGlow(x, y, W * .35f, H * .015f, c("#2A1640", .7f)); ellGlow(x, y - H * .005f, W * .28f, H * .006f, c("#E6D2FF", .2f), BlendMode.Screen) }
    // Colline et manoir
    val nz = Nz(13); val hill = ridgePts(H * .68f, H * .1f, 80) { u -> sm(.15f, .65f, u) * (1f - sm(.85f, 1.1f, u) * .4f) + .08f * nz.at(u * 4f, 3) }
    ridgeFill(hill, c("#1A0F2A"))
    mist(H * .66f, H * .06f, c("#7B4FA0", .35f))
    val bx = W * .56f; val by = H * .605f; val u = S * .05f; val col = c("#0E0818")
    fun box(x0: Float, y0: Float, x1: Float, y1: Float) = rect(bx + x0 * u, by - y1 * u, (x1 - x0) * u, (y1 - y0) * u, col)
    fun roof(x0: Float, x1: Float, y0: Float, y1: Float) = fill(poly(bx + x0 * u, by - y0 * u, bx + (x0 + x1) / 2 * u, by - y1 * u, bx + x1 * u, by - y0 * u), col)
    box(-3f, 0f, 3.2f, 3.2f); roof(-3.4f, 3.6f, 3.2f, 5.2f)
    box(-4.6f, 0f, -2.6f, 5.4f); roof(-5f, -2.2f, 5.4f, 9f)
    box(2.4f, 0f, 4.2f, 4.4f); roof(2.1f, 4.5f, 4.4f, 7.2f)
    box(-.6f, 4f, .2f, 6.4f) // cheminées
    box(1.4f, 4f, 2f, 5.8f)
    line(bx - 3.6f * u, by - 9f * u, bx - 3.6f * u, by - 10.2f * u, max(1f, S * .003f), col)
    // Fenêtres éclairées (vacillement)
    val r = Mul(5)
    for ((wx, wy) in listOf(-4.1f to 4.2f, -3.4f to 4.2f, -4.1f to 2.6f, -1.8f to 2.1f, -.6f to 2.1f, .8f to 2.1f, 2f to 2.1f, 2.9f to 3.2f, 3.6f to 3.2f, -1.2f to .8f, 1.5f to .8f, -3.7f to 7f)) {
        val on = .7f + .3f * sin(t * TAU * (3f + r() * 4f) + r() * 6f)
        rect(bx + wx * u, by - wy * u, u * .38f, u * .62f, c("#FFB347", on)); glow(bx + wx * u + u * .2f, by - wy * u + u * .3f, u * 1.3f, c("#FF8A2A", .25f * on), BlendMode.Screen)
    }
    // Liseré de lune sur les toits
    line(bx - 5f * u, by - 5.4f * u, bx - 3.6f * u, by - 9f * u, max(1f, S * .003f), c("#E9D9FF", .35f))
    line(bx - 3.4f * u, by - 3.2f * u, bx + .1f * u, by - 5.2f * u, max(1f, S * .003f), c("#E9D9FF", .3f))
    // Cimetière
    val gy = H * .8f
    ridgePts(gy, H * .02f, 50) { u2 -> nz.at(u2 * 5f, 3) * .5f + .5f }.let { ridgeFill(it, vg(gy - H * .02f, H, 0f to c("#1E1030"), 1f to c("#0A0510"))) }
    mist(gy, H * .07f, c("#9A7BC0", .28f + .06f * sin(t * TAU)))
    val g = Mul(11)
    for (i in 0 until 7) {
        val x = W * (.08f + i * .13f + g() * .04f); val y = gy + H * (.01f + g() * .03f); val w = S * (.05f + g() * .03f); val h = w * (1.2f + g() * .5f)
        rotate((g() - .5f) * 14f, Offset(x, y)) {
            if (g() < .3f) { rect(x - w * .12f, y - h * 1.3f, w * .24f, h * 1.3f, c("#2A2238")); rect(x - w * .45f, y - h, w * .9f, w * .22f, c("#2A2238")) }
            else { drawRoundRect(c("#2B2340"), topLeft = Offset(x - w / 2, y - h), size = Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2)); line(x - w / 2, y - h * .6f, x - w / 2 + w * .2f, y - h, max(1f, S * .002f), c("#B9A3E0", .25f)) }
        }
    }
    // Arbre mort au premier plan (silhouette noueuse)
    run {
        val p = Path(); val tips = ArrayList<Offset>()
        branches(p, W * .04f, H * 1.02f, H * .14f, -72f, S * .07f, 6, Mul(23), tips, 22f)
        drawPath(p, c("#050208"))
    }
    // Grille en fer forgé
    val fy = H * .93f
    rect(0f, fy, W, S * .006f, c("#050208")); rect(0f, fy + H * .035f, W, S * .006f, c("#050208"))
    var fx = 0f; while (fx < W) { rect(fx, fy - H * .03f, S * .006f, H * .08f, c("#050208")); fill(poly(fx - S * .006f, fy - H * .03f, fx + S * .003f, fy - H * .045f, fx + S * .012f, fy - H * .03f), c("#050208")); fx += S * .045f }
    // Citrouilles lumineuses
    for ((px, ps) in listOf(W * .78f to S * .1f, W * .64f to S * .07f, W * .9f to S * .06f)) {
        val py = H * .965f
        glow(px, py - ps * .3f, ps * 3f, c("#FF8A1F", .25f + .05f * sin(t * TAU * 5)), BlendMode.Screen)
        for (k in -2..2) drawOval(Brush.radialGradient(0f to c("#FFB347"), 1f to c("#C44E0E"), center = Offset(px + k * ps * .18f - ps * .1f, py - ps * .8f), radius = ps), topLeft = Offset(px + k * ps * .26f - ps * .38f, py - ps * .78f), size = Size(ps * .76f, ps * .78f))
        rect(px - ps * .06f, py - ps * .95f, ps * .12f, ps * .2f, c("#3C4A1E"))
        val fl = c("#FFE27A", .9f + .1f * sin(t * TAU * 7))
        fill(poly(px - ps * .32f, py - ps * .48f, px - ps * .12f, py - ps * .48f, px - ps * .22f, py - ps * .62f), fl)
        fill(poly(px + ps * .12f, py - ps * .48f, px + ps * .32f, py - ps * .48f, px + ps * .22f, py - ps * .62f), fl)
        fill(poly(px - ps * .3f, py - ps * .3f, px + ps * .3f, py - ps * .3f, px + ps * .2f, py - ps * .16f, px, py - ps * .22f, px - ps * .2f, py - ps * .16f), fl)
    }
    // Chauves-souris
    val b = Mul(3)
    for (i in 0 until 7) {
        val sp = .5f + b() * .5f; val u = ((b() + t) % 1f); val x = W * (1.15f - u * 1.3f); val y = H * (.18f + b() * .25f) + sin(u * TAU * 3 + i) * H * .02f
        bat(x, y, S * (.025f + b() * .025f), sin(t * TAU * 40 * sp + i), c("#0A0612"))
    }
}

/* ------------------------------------------------------------------ Bois d'automne */
private fun DrawScope.autumnwood(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val vy = H * .6f
    sky(0f to c("#F2A65A"), .3f to c("#FFCB8A"), .55f to c("#FFE6BC"), 1f to c("#F6C27E"))
    val sun = Offset(W * .55f, H * .5f)
    glow(sun.x, sun.y, S * 1.2f, c("#FFE7B0", .7f), BlendMode.Screen); glow(sun.x, sun.y, S * .25f, c("#FFF6DE", .8f), BlendMode.Screen)
    // Lisières successives : houppiers arrondis, troncs fins, brume dorée entre les plans
    val layers = listOf(c("#F2BE8C"), c("#E9A065"), c("#D27838"), c("#A9501F"), c("#7A3416"))
    for ((k, col) in layers.withIndex()) {
        val d = k / 4f; val base = vy + H * (.005f + k * .012f); val r = Mul(600 + k); val cx = W * .53f
        val n = 10 - k; val gap = if (k == 0) 0f else W * (.03f + d * .17f)
        for (i in 0 until n) {
            val x = W * (i + .2f + r() * .6f) / n * 1.1f - W * .05f; if (abs(x - cx) < gap) continue
            val h = H * (.14f + d * .3f) * (.8f + r() * .4f); val rx = S * (.09f + d * .16f) * (.8f + r() * .4f); val tw = S * (.006f + d * .025f)
            val trunkC = mix(col, c("#2E140A"), .3f + d * .4f)
            fill(poly(x - tw, base, x + tw, base, x + tw * .5f, base - h * .7f, x - tw * .5f, base - h * .7f), trunkC)
            line(x, base - h * .55f, x - rx * .4f, base - h * .8f, tw * .6f, trunkC); line(x, base - h * .6f, x + rx * .35f, base - h * .85f, tw * .55f, trunkC)
            val hue = listOf(c("#E8742A"), c("#D9461C"), c("#F2AE2E"), c("#C95A1A"), c("#B83A1A"))[(r() * 5).toInt().coerceIn(0, 4)]
            val tc = mix(hue, c("#F6CB98"), (1f - d) * .62f)
            crown(x, base - h, rx, rx * (.9f + r() * .3f), 700 + k * 30 + i, mix(tc, c("#FFE9A8"), .5f), tc, mix(tc, c("#5A1A08"), .35f + d * .2f), -.5f, -.85f)
        }
        mist(base, H * .05f, c("#FFE0B0", .4f - d * .06f))
    }
    rays(sun.x, sun.y - H * .08f, H * .85f, 11, 105f, 90f, c("#FFF0C8", .12f), 6, t)
    // Sous-bois et sentier tapissé de feuilles
    drawRect(vg(vy, H, 0f to c("#E8B47A"), .3f to c("#C98A4E"), 1f to c("#6E3216")), topLeft = Offset(0f, vy + H * .005f), size = Size(W, H))
    fill(poly(W * .52f, vy + H * .005f, W * .54f, vy + H * .005f, W * .98f, H, W * .12f, H), vg(vy, H, 0f to c("#FFE3B0"), .35f to c("#EDB271"), 1f to c("#C17A40")))
    val lr = Mul(14); val lc = listOf(c("#D2561E"), c("#E8892A"), c("#F2B33D"), c("#9C3A16"), c("#B5651D"))
    for (i in 0 until 320) { val y = vy + H * .01f + (H * .4f) * lr().pow(.8f); val x = lr() * W; val sz = S * (.006f + (y - vy) / H * .055f); leaf(x, y, sz, lr() * 360f, lc[(lr() * 5).toInt().coerceIn(0, 4)].a(.8f), .38f) }
    // Grands troncs au premier plan (écorce, liseré lumineux côté soleil)
    for ((x, w) in listOf(W * .04f to S * .14f, W * .93f to S * .17f, W * .2f to S * .05f, W * .8f to S * .045f)) {
        drawRect(Brush.horizontalGradient(0f to c("#24110A"), .65f to c("#4A2616"), 1f to c("#2A140C"), startX = x - w / 2, endX = x + w / 2), topLeft = Offset(x - w / 2, -2f), size = Size(w, H + 4f))
        val br = Mul((x * 7).toInt()); for (j in 0 until 18) { val bx = x - w / 2 + br() * w; val by = br() * H; line(bx, by, bx + (br() - .5f) * w * .1f, by + H * (.03f + br() * .06f), max(1f, w * .03f), c("#1A0B05", .45f)) }
        val sx = if (x < sun.x) x + w / 2 - w * .1f else x - w / 2
        drawRect(vg(0f, H, 0f to c("#FFC36E", .15f), .55f to c("#FFC36E", .6f), 1f to c("#FFC36E", .1f)), topLeft = Offset(sx, 0f), size = Size(w * .1f, H))
    }
    // Voûte de feuillage dans les coins hauts : bord festonné
    for (side in listOf(-1f, 1f)) {
        val nz = Nz(if (side < 0) 801 else 802); val p = Path(); val x0 = if (side < 0) -2f else W + 2f
        p.moveTo(x0, -2f)
        for (i in 0..40) { val u = i / 40f; val x = if (side < 0) u * W * .55f else W - u * W * .55f; val y = H * (.16f - .14f * u) * (1f - u * .3f) + abs(sin(u * 22f)).pow(.6f) * H * .025f + nz.at(u * 4f, 2) * H * .01f; p.lineTo(x, max(-2f, y)) }
        p.lineTo(if (side < 0) W * .55f else W * .45f, -2f); p.close()
        drawPath(p, vg(0f, H * .2f, 0f to c("#5E200C"), 1f to c("#B8481A")))
        val r = Mul(if (side < 0) 900 else 901)
        for (i in 0 until 90) { val u = r() * .5f; val x = if (side < 0) u * W else W - u * W; val y = r() * H * (.16f - .2f * u).coerceAtLeast(.02f); leaf(x, y, S * (.03f + r() * .03f), r() * 360f, lc[(r() * 5).toInt().coerceIn(0, 4)].a(.9f), .45f) }
    }
    // Feuilles qui tombent
    val fr = Mul(33)
    for (i in 0 until 22) {
        val x0 = fr() * W; val sp = .6f + fr() * .6f; val u = (fr() + t * sp) % 1f
        val x = x0 + sin(u * TAU * 2 + i) * S * .05f; val y = -H * .05f + u * H * 1.1f; val sz = S * (.018f + fr() * .02f)
        leaf(x, y, sz, u * 720f * (if (i % 2 == 0) 1f else -1f) + i * 40f, lc[i % 5], .4f)
    }
}

/* ------------------------------------------------------------------ Forêt brumeuse */
private fun DrawScope.mistforest(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#C9D9D2"), .3f to c("#DDE8E0"), .55f to c("#EEF2E8"), 1f to c("#B9CDBF"))
    val sun = Offset(W * .7f, H * .2f)
    glow(sun.x, sun.y, S * 1.1f, c("#FFF7E0", .7f), BlendMode.Screen); glow(sun.x, sun.y, S * .1f, c("#FFFFFF", .9f), BlendMode.Screen)
    val tones = listOf(c("#BFD1C5"), c("#A2BCAB"), c("#7E9E8A"), c("#5B7F6A"), c("#3C5E4C"), c("#1F3A30"))
    for ((k, col) in tones.withIndex()) {
        val d = k / 5f; val nz = Nz(40 + k); val base = H * (.42f + d * .42f); val amp = H * (.06f - d * .02f)
        val line = { x: Float -> base - amp * (nz.at(x / W * 2.2f, 3) * .5f + .5f) }
        conifers((24 - k * 2.5f).toInt(), 50 + k, H * (.05f + d * .1f), H * (.1f + d * .16f), col, line)
        ridgePts(base, amp, 80) { u -> nz.at(u * 2.2f, 3) * .5f + .5f }.let { ridgeFill(it, col) }
        mist(base - amp * .3f, H * (.08f - d * .03f), c("#F4F8F0", .65f - d * .35f))
        if (k == 1) rays(sun.x, sun.y, H * .9f, 8, 115f, 50f, c("#FFFBEA", .14f), 3, t)
    }
    // Brume qui dérive
    for (i in 0 until 4) { val x = ((i * .31f + t) % 1f) * W * 1.6f - W * .3f; ellGlow(x, H * (.55f + i * .08f), W * .5f, H * .03f, c("#F6FAF2", .35f)) }
    // Oiseaux
    val r = Mul(5)
    for (i in 0 until 4) {
        val x = W * (.25f + r() * .3f) + t * W * .08f; val y = H * (.22f + r() * .06f); val s = S * (.01f + r() * .006f); val f = sin(t * TAU * 18 + i) * .4f
        val p = Path(); p.moveTo(x - s, y - s * (.3f + f)); p.cubicTo(x - s * .5f, y - s * .7f, x - s * .2f, y - s * .5f, x, y); p.cubicTo(x + s * .2f, y - s * .5f, x + s * .5f, y - s * .7f, x + s, y - s * (.3f + f))
        drawPath(p, c("#2E4A3E", .6f), style = Stroke(max(1f, S / 450f), cap = StrokeCap.Round))
    }
}

/* ------------------------------------------------------------------ Dunes au couchant */
private fun DrawScope.dunes(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#6E4A8C"), .25f to c("#C7678A"), .45f to c("#F28C5B"), .56f to c("#FFB07A"), .62f to c("#FFD9A0"), y1 = H * .64f)
    val sun = Offset(W * .32f, H * .56f)
    glow(sun.x, sun.y, S * 1.3f, c("#FFB46A", .55f), BlendMode.Screen); glow(sun.x, sun.y, S * .3f, c("#FFE7B8", .8f), BlendMode.Screen)
    disc(sun.x, sun.y, S * .07f, c("#FFF3D6"))
    softCloud(W * .7f + t * W * .05f, H * .3f, W * .8f, c("#B8608A", .3f), c("#FFC09A", .4f), 4)
    // Dunes : versant au soleil clair, versant à l'ombre plus froid, arête nette
    val specs = listOf(arrayOf(.6f, .06f, "#F2B07E", "#C9785E"), arrayOf(.66f, .08f, "#EE9E64", "#B65E46"), arrayOf(.74f, .1f, "#E58A4E", "#9C4A36"), arrayOf(.88f, .17f, "#D9773C", "#7E3A2C"))
    for ((k, sp) in specs.withIndex()) {
        val base = H * (sp[0] as Float); val amp = H * (sp[1] as Float); val lit = c(sp[2] as String); val sh = c(sp[3] as String); val nz = Nz(20 + k)
        val n = 120; val pts = FloatArray((n + 1) * 2)
        for (i in 0..n) { val u = i / n.toFloat(); pts[i * 2] = W * u; val v = nz.at(u * (1.6f + k * .4f), 3) * .5f + .5f; pts[i * 2 + 1] = base - amp * v.pow(1.6f) }
        ridgeFill(pts, vg(base - amp, H, 0f to lit, 1f to mix(lit, sh, .3f)))
        // versants à l'ombre (pentes qui descendent vers la droite, dos au soleil) et rides de sable
        if (k < 3) run { // bande d'ombre : entre la crête f(x) et la crête décalée f(x − dx) + dy, là où elle est plus basse
            val dx = amp * 1.3f; val dy = amp * .2f
            fun f(x: Float): Float { val u = (x / W * n).coerceIn(0f, n.toFloat()); val i = u.toInt().coerceAtMost(n - 1); val fr = u - i; return pts[i * 2 + 1] * (1 - fr) + pts[i * 2 + 3] * fr }
            val band = Path(); for (i in 0..n) { val x = pts[i * 2]; if (i == 0) band.moveTo(x, f(x)) else band.lineTo(x, f(x)) }
            for (i in n downTo 0) { val x = pts[i * 2]; band.lineTo(x, if (x < dx) f(x) else max(f(x), f(x - dx) + dy)) }
            band.close(); drawPath(band, vg(base - amp, base + amp * .5f, 0f to sh, 1f to mix(sh, lit, .35f)))
        }
        if (k >= 2) clipPath(Path().apply { moveTo(0f, H); var i = 0; while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }; lineTo(W, H); close() }) {
            val r = Mul(30 + k); for (j in 0 until 14) { val y = base + amp * (.1f + j * .12f); val q = Path(); q.moveTo(0f, y); for (i in 1..20) { val x = W * i / 20f; q.lineTo(x, y + sin(x / W * 9f + j + r()) * amp * .08f) }
                drawPath(q, sh.a(.22f), style = Stroke(max(1f, S * .002f))) }
        }
        val crest = Path(); crest.moveTo(pts[0], pts[1]); var i = 2; while (i < pts.size) { crest.lineTo(pts[i], pts[i + 1]); i += 2 }
        drawPath(crest, c("#FFE6C0", .35f), style = Stroke(max(1f, S * .003f)))
        if (k == 1) { // caravane de chameaux sur la crête
            val r = Mul(3); val cx0 = W * (.55f + .08f * sin(t * TAU))
            for (j in 0 until 4) { val x = cx0 + j * S * .035f; val idx = ((x / W) * n).toInt().coerceIn(0, n); val y = pts[idx * 2 + 1]; val s = S * .016f
                fill(Path().apply { moveTo(x - s, y); lineTo(x - s * .8f, y - s * .9f); cubicTo(x - s * .4f, y - s * 1.6f, x + s * .2f, y - s * 1.4f, x + s * .5f, y - s); lineTo(x + s * .9f, y - s * 1.5f); lineTo(x + s * 1.1f, y - s * 1.4f); lineTo(x + s * .8f, y - s * .7f); lineTo(x + s * .7f, y); close() }, c("#4A2430", .8f)); r() }
        }
    }
}

/* ------------------------------------------------------------------ Cerisiers en fleurs */
private fun DrawScope.sakura(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#F9D6E3"), .35f to c("#FFE6EF"), .6f to c("#FFF1F2"), 1f to c("#F6C9D8"))
    glow(W * .5f, H * .4f, S * .9f, c("#FFFFFF", .5f), BlendMode.Screen)
    // Collines et pagode dans la brume
    val n1 = Nz(3); ridgePts(H * .62f, H * .08f, 80) { u -> n1.at(u * 1.5f, 3) * .5f + .5f }.let { ridgeFill(it, c("#E6BCD0")) }
    mist(H * .6f, H * .06f, c("#FFF0F4", .7f))
    run {
        val px = W * .3f; val py = H * .63f; val u = S * .03f; val col = c("#B88AA6")
        for (i in 0 until 5) { val w = u * (3.2f - i * .45f); val y = py - i * u * 1.6f
            rect(px - w * .35f, y - u * 1.1f, w * .7f, u * 1.1f, col)
            fill(poly(px - w * .75f, y - u * 1.1f, px + w * .75f, y - u * 1.1f, px + w * .45f, y - u * 1.6f, px - w * .45f, y - u * 1.6f), mix(col, c("#7E5A78"), .3f)) }
        line(px, py - 5 * u * 1.6f, px, py - 5 * u * 1.6f - u * 2.5f, max(1f, S * .003f), col)
    }
    val n2 = Nz(4); ridgePts(H * .7f, H * .06f, 80) { u -> n2.at(u * 2f + 3f, 3) * .5f + .5f }.let { ridgeFill(it, c("#D7A3BC")) }
    mist(H * .7f, H * .05f, c("#FFF0F4", .6f))
    // Étang : reflets doux
    drawRect(vg(H * .72f, H, 0f to c("#EBC4D6"), 1f to c("#C995B4")), topLeft = Offset(0f, H * .72f), size = Size(W, H * .28f))
    for (i in 0 until 12) { val y = H * (.74f + i * .022f); val x = W * (.5f + .3f * sin(i * 1.7f + t * TAU)); line(x - S * .15f, y, x + S * .15f, y, max(1f, S * .003f), c("#FFFFFF", .25f)) }
    // Branches fleuries (coins hauts)
    fun blossomBranch(x: Float, y: Float, ang: Float, seed: Int, len: Float) {
        val p = Path(); val tips = ArrayList<Offset>(); val r = Mul(seed)
        branches(p, x, y, len, ang, S * .04f, 6, r, tips, 24f, 4f)
        drawPath(p, c("#4A2A34"))
        val fl = Mul(seed + 1)
        for (o in tips) { glow(o.x, o.y, S * .07f, c("#FFC9DC", .5f)); for (j in 0 until 5) {
            val fx = o.x + (fl() - .5f) * S * .08f; val fy = o.y + (fl() - .5f) * S * .06f; val fs = S * (.008f + fl() * .009f)
            for (k in 0 until 5) { val a = k / 5f * TAU + fl(); leaf(fx + cos(a) * fs * .5f, fy + sin(a) * fs * .5f, fs, a * 360f / TAU, if (fl() < .5f) c("#FFB7CF", .95f) else c("#FFE6EE", .95f), .6f) }
            disc(fx, fy, fs * .2f, c("#D9467A"))
        } }
    }
    blossomBranch(-W * .05f, H * .12f, -18f, 7, S * .2f)
    blossomBranch(W * 1.05f, H * .06f, 200f, 9, S * .17f)
    // Bokeh et pétales qui tombent
    val b = Mul(12)
    for (i in 0 until 10) glow(b() * W, b() * H * .6f, S * (.03f + b() * .05f), c("#FFFFFF", .25f), BlendMode.Screen)
    for (i in 0 until 26) {
        val sp = .5f + b() * .6f; val u = (b() + t * sp) % 1f; val x = b() * W + sin(u * TAU * 2 + i) * S * .06f + u * W * .15f; val y = -H * .05f + u * H * 1.1f
        val s = S * (.012f + b() * .01f)
        leaf(x, y, s, u * 540f + i * 30f, c("#FFB3CC", .9f), .55f)
    }
}

/* ------------------------------------------------------------------ Magma */
private fun DrawScope.magma(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#0C0406"), .35f to c("#1A0A08"), .6f to c("#3A1006"), .7f to c("#6A1E08"), y1 = H * .72f)
    val cx = W * .55f; val cy = H * .5f
    glow(cx, cy, S * 1.1f, c("#FF5A1F", .4f), BlendMode.Screen)
    // Panache de cendres éclairé par en dessous
    val r = Mul(9)
    for (i in 0 until 26) { val u = i / 26f; val x = cx + sin(u * 4f + t * TAU * .5f) * S * .08f * u + u * S * .25f; val y = cy - u * H * .42f; val rr = S * (.08f + u * .2f)
        ellGlow(x, y, rr, rr * .7f, mix(c("#5A2016"), c("#1A0A0A"), u).a(.55f)); if (u < .5f) ellGlow(x, y + rr * .3f, rr * .7f, rr * .3f, c("#FF7A2E", .25f * (1f - u * 2f)), BlendMode.Screen) }
    // Volcan
    val vp = Path(); vp.moveTo(-W * .1f, H * .74f); vp.cubicTo(W * .2f, H * .7f, W * .42f, H * .56f, cx - S * .07f, cy + H * .005f)
    vp.lineTo(cx + S * .07f, cy + H * .005f); vp.cubicTo(W * .7f, H * .58f, W * .85f, H * .68f, W * 1.1f, H * .72f); vp.lineTo(W * 1.1f, H); vp.lineTo(-W * .1f, H); vp.close()
    drawPath(vp, vg(cy, H * .8f, 0f to c("#2A0E0A"), 1f to c("#12060A")))
    glow(cx, cy, S * .12f, c("#FFB347", .9f), BlendMode.Screen)
    // Coulées sur les flancs (lueur pulsée)
    val pulse = .75f + .25f * sin(t * TAU * 2)
    for ((sx, dir) in listOf(-.02f to -1f, .03f to 1f)) {
        val p = Path(); var x = cx + sx * W; var y = cy + H * .01f; p.moveTo(x, y)
        val nz = Nz(if (dir < 0) 3 else 4)
        for (i in 1..20) { val u = i / 20f; x = cx + sx * W + dir * u * W * .38f + nz.at(u * 4f, 2) * S * .03f; y = cy + H * .01f + u * H * .2f; p.lineTo(x, y) }
        drawPath(p, c("#FF6A1F", .5f * pulse), style = Stroke(S * .02f, cap = StrokeCap.Round), blendMode = BlendMode.Screen)
        drawPath(p, c("#FFD27A", .9f), style = Stroke(S * .005f, cap = StrokeCap.Round))
    }
    // Plateau de basalte craquelé : rivière de lave au premier plan
    val gy = H * .74f
    drawRect(vg(gy, H, 0f to c("#1C0A0A"), 1f to c("#0A0406")), topLeft = Offset(0f, gy), size = Size(W, H - gy))
    val river = Path(); val nz = Nz(7)
    river.moveTo(W * .48f, gy)
    for (i in 1..30) { val u = i / 30f; river.lineTo(W * (.48f + nz.at(u * 3f, 3) * .25f * u) - u * u * W * .1f, gy + u * (H - gy)) }
    for (i in 30 downTo 0) { val u = i / 30f; river.lineTo(W * (.48f + nz.at(u * 3f, 3) * .25f * u) - u * u * W * .1f + S * (.01f + u * u * .25f), gy + u * (H - gy)) }
    river.close()
    drawPath(river, vg(gy, H, 0f to c("#FF8A2A"), .5f to c("#FF5A1F"), 1f to c("#E0360E")))
    clipPath(river) {
        val cr = Mul(4) // croûte qui dérive
        for (i in 0 until 40) { val y = gy + ((cr() + t * .25f) % 1f) * (H - gy); val x = W * .3f + cr() * W * .5f; val s = S * (.01f + (y - gy) / H * .1f)
            drawPath(lobed(x, y, s, s * .5f, i, 5, .3f), c("#3A1008", .7f)) }
        glow(W * .5f, H * .9f, S * .5f, c("#FFE08A", .4f * pulse), BlendMode.Screen)
    }
    glow(W * .55f, H * .95f, S * .8f, c("#FF5A1F", .25f), BlendMode.Screen)
    // Fissures incandescentes
    val fr = Mul(11); val cracks = Path()
    for (i in 0 until 12) { val x = fr() * W; val y = gy + fr() * (H - gy); val l = S * (.1f + (y - gy) / H * .5f); cracks.moveTo(x, y)
        cracks.cubicTo(x + l * .3f, y + (fr() - .5f) * l * .2f, x + l * .6f, y + (fr() - .5f) * l * .2f, x + l * (fr() - .5f) * 2f, y + l * .15f) }
    for (i in 0 until 7) { val x = fr() * W; val y = gy + fr() * (H - gy) * .8f; glow(x, y, S * (.05f + fr() * .08f), c("#FF5A1F", .18f * pulse), BlendMode.Screen) }
    // Roches au premier plan, liserés orangés
    for ((x, w) in listOf(W * .1f to S * .35f, W * .92f to S * .3f)) {
        val p = lobed(x, H * .98f, w, w * .5f, (x * 3).toInt(), 6, .25f, .3f)
        drawPath(p, c("#0E0507"))
        clipPath(p) { glow(W * .5f, H * .9f, S * .6f, c("#FF6A1F", .25f), BlendMode.Screen) }
    }
    // Braises qui montent
    val e = Mul(21); val pts = ArrayList<Offset>(); val big = ArrayList<Offset>()
    for (i in 0 until 90) { val u = (e() + t * (.5f + e())) % 1f; val x = e() * W + sin(u * TAU * 2 + i) * S * .03f; val y = H * (1f - u * .9f); if (i % 6 == 0) big.add(Offset(x, y)) else pts.add(Offset(x, y)) }
    drawPoints(pts, PointMode.Points, c("#FFB347", .8f), strokeWidth = max(1.5f, S / 320f), cap = StrokeCap.Round)
    for (o in big) glow(o.x, o.y, S * .012f, c("#FFC56A", .7f), BlendMode.Screen)
}

/* ------------------------------------------------------------------ Pluie de code */
private fun DrawScope.coderain(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#000502"), .5f to c("#00140A"), 1f to c("#002412"))
    glow(W * .5f, H * .45f, S * 1.1f, c("#0F6B36", .25f), BlendMode.Screen)
    // Trois profondeurs de colonnes : glyphes en points 3 × 5
    for (layer in 0 until 3) {
        val px = S * (.0045f + layer * .0028f); val gw = px * 4.2f; val gh = px * 6.4f; val cols = (W / gw).toInt() + 1; val rows = (H / gh).toInt() + 2
        val r = Mul(100 + layer); val trail = ArrayList<Offset>(); val dim = ArrayList<Offset>(); val heads = ArrayList<Offset>()
        for (cIdx in 0 until cols) {
            if (r() < .35f - layer * .1f) { r(); r(); continue }
            val speed = (1 + (r() * 3).toInt()).toFloat(); val off = r(); val len = 8 + (r() * 18).toInt()
            val head = ((off + t * speed) % 1f) * (rows + len) - len * .2f
            for (row in 0 until rows) {
                val dist = head - row; if (dist < 0f || dist > len) continue
                val seed = (cIdx * 131 + row * 17 + ((t * speed * 6f).toInt() * (if ((cIdx + row) % 5 == 0) 1 else 0))) * 2654435761L.toInt()
                val bits = (seed ushr 7) and 0x7FFF
                val x0 = cIdx * gw; val y0 = row * gh
                val target = if (dist < 1f) heads else if (dist < len * .5f) trail else dim
                for (b in 0 until 15) if ((bits shr b) and 1 == 1) target.add(Offset(x0 + (b % 3) * px * 1.1f, y0 + (b / 3) * px * 1.1f))
            }
        }
        val a = .35f + layer * .3f
        drawPoints(dim, PointMode.Points, c("#1FA352", a * .45f), strokeWidth = px, cap = StrokeCap.Square)
        drawPoints(trail, PointMode.Points, c("#39E07A", a * .85f), strokeWidth = px, cap = StrokeCap.Square)
        drawPoints(heads, PointMode.Points, c("#D9FFE6", min(1f, a * 1.3f)), strokeWidth = px, cap = StrokeCap.Square)
        if (layer == 2) for (i in 0 until heads.size step 9) glow(heads[i].x, heads[i].y, px * 6f, c("#7DFFB0", .18f), BlendMode.Screen)
    }
    // Lignes de balayage très discrètes
    var y = 0f; while (y < H) { rect(0f, y, W, max(1f, S / 900f), c("#000000", .18f)); y += S * .008f }
}

/* ------------------------------------------------------------------ Pluie sur la vitre */
private fun DrawScope.rainwindow(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#1E2434"), .5f to c("#2E3850"), 1f to c("#465676"))
    // Ville floue derrière la vitre (bokeh)
    val r = Mul(3)
    val warm = listOf(c("#FFB86A"), c("#FF8A5C"), c("#FFD58A")); val cool = listOf(c("#7FC8FF"), c("#9AA8FF"), c("#7FFFE0"))
    for (i in 0 until 46) {
        val x = r() * W; val y = H * (.35f + r() * .55f); val rr = S * (.03f + r() * .08f); val col = if (r() < .65f) warm[(r() * 3).toInt().coerceIn(0, 2)] else cool[(r() * 3).toInt().coerceIn(0, 2)]
        val a = .12f + r() * .22f
        drawCircle(Brush.radialGradient(0f to col.a(a), .8f to col.a(a * .8f), 1f to col.a(0f), center = Offset(x, y), radius = rr), radius = rr, center = Offset(x, y), blendMode = BlendMode.Screen)
    }
    ellGlow(W * .5f, H * .75f, W * .8f, H * .15f, c("#FF9E6A", .12f), BlendMode.Screen)
    // Buée en bas de vitre
    drawRect(vg(H * .65f, H, 0f to c("#C9D6EA", 0f), 1f to c("#C9D6EA", .22f)))
    // Gouttes immobiles (réfraction : bord sombre, reflet clair en haut)
    val d = Mul(8)
    fun drop(x: Float, y: Float, rr: Float, elong: Float = 1f) {
        drawOval(Brush.verticalGradient(0f to c("#0E1420", .55f), .6f to c("#5E6E8E", .25f), 1f to c("#DDE6FF", .5f), startY = y - rr * elong, endY = y + rr * elong), topLeft = Offset(x - rr, y - rr * elong), size = Size(rr * 2, rr * 2 * elong))
        disc(x - rr * .3f, y - rr * elong * .45f, rr * .22f, c("#FFFFFF", .7f))
    }
    for (i in 0 until 150) drop(d() * W, d() * H, S * (.003f + d().pow(3f) * .012f), 1f + d() * .2f)
    // Gouttes qui glissent et leur traînée
    for (i in 0 until 9) {
        val x = W * (.06f + i * .11f + d() * .04f); val sp = 1f + (i % 3); val u = (d() + t * sp) % 1f; val y = -H * .05f + u * H * 1.1f; val rr = S * (.009f + d() * .007f)
        val wob = sin(u * 30f + i) * rr * .3f
        drawLine(Brush.verticalGradient(0f to c("#C9D6EA", 0f), 1f to c("#C9D6EA", .3f), startY = y - H * .25f, endY = y), Offset(x + wob * .5f, y - H * .25f), Offset(x + wob, y), strokeWidth = rr * .5f, cap = StrokeCap.Round)
        for (k in 1..5) drop(x + sin(k * 2.1f + i) * rr * .3f, y - k * H * .045f, rr * .3f)
        drop(x + wob, y, rr, 1.25f)
    }
}

/* ------------------------------------------------------------------ Planète géante à anneaux */
private fun DrawScope.planet(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#04020C"), .5f to c("#0E0824"), 1f to c("#1E1040"))
    starField(81, 420, H, c("#E9E4FF", .75f), t, 5)
    ellGlow(W * .2f, H * .3f, W * .7f, H * .2f, c("#5A3FA8", .18f), BlendMode.Screen)
    val px = W * .6f; val py = H * .64f; val pr = S * .44f
    val tilt = -18f
    fun rings(front: Boolean) {
        rotate(tilt, Offset(px, py)) {
            clipRect(px - pr * 2.4f, if (front) py else py - pr * 2f, px + pr * 2.4f, if (front) py + pr * 2f else py) {
                val r = Mul(77); var rr = 1.3f
                while (rr < 2.2f) { val w = .015f + r() * .05f; val a = if (rr in 1.78f..1.84f) 0f else .15f + r() * .45f
                    val col = mix(c("#E8D2B0"), c("#9C7E68"), r())
                    drawOval(col.a(a), topLeft = Offset(px - pr * rr, py - pr * rr * .3f), size = Size(pr * rr * 2, pr * rr * .6f), style = Stroke(pr * w)); rr += w * .95f }
            }
        }
    }
    rings(false)
    // Globe à bandes
    val globe = Path().apply { val n = 64; for (i in 0..n) { val a = i / n.toFloat() * TAU; val x = px + cos(a) * pr; val y = py + sin(a) * pr; if (i == 0) moveTo(x, y) else lineTo(x, y) }; close() }
    glow(px, py, pr * 1.25f, c("#FFB98A", .18f), BlendMode.Screen)
    clipPath(globe) {
        drawRect(vg(py - pr, py + pr, 0f to c("#F2D3A6"), .5f to c("#D99A6C"), 1f to c("#9C5A48")), topLeft = Offset(px - pr, py - pr), size = Size(pr * 2, pr * 2))
        rotate(tilt, Offset(px, py)) {
            val r = Mul(5); val sh = t * pr * .4f
            for (i in 0 until 16) { val y = py - pr + i * pr * .13f + r() * pr * .05f; val h = pr * (.03f + r() * .07f)
                val col = listOf(c("#FFF0D6"), c("#C97A52"), c("#E6B282"), c("#8A4A3E"))[(r() * 4).toInt().coerceIn(0, 3)]
                val p = Path(); p.moveTo(px - pr * 1.3f, y); for (k in 0..24) { val x = px - pr * 1.3f + k * pr * .11f; p.lineTo(x, y + sin(x / pr * 6f + i + sh / pr * 3f) * h * .3f) }
                for (k in 24 downTo 0) { val x = px - pr * 1.3f + k * pr * .11f; p.lineTo(x, y + h + sin(x / pr * 5f + i * 2f + sh / pr * 3f) * h * .3f) }
                p.close(); drawPath(p, col.a(.35f)) }
            drawOval(c("#B5503A", .6f), topLeft = Offset(px + pr * .15f + sh * .3f, py + pr * .25f), size = Size(pr * .22f, pr * .1f)) // grande tache
        }
        // Terminateur et limbe
        drawCircle(Brush.radialGradient(0f to Color.Transparent, .55f to c("#000000", .1f), 1f to c("#05020C", .92f), center = Offset(px - pr * .45f, py - pr * .5f), radius = pr * 2f), radius = pr * 2f, center = Offset(px, py))
        drawCircle(Brush.radialGradient(0f to Color.Transparent, .86f to Color.Transparent, 1f to c("#FFD9B0", .35f), center = Offset(px, py), radius = pr), radius = pr, center = Offset(px, py), blendMode = BlendMode.Screen)
    }
    // Ombre de l'anneau sur le globe
    rings(true)
    // Lunes
    for ((mx, my, mr, col) in listOf(arrayOf(W * .2f, H * .2f, S * .05f, c("#C9C3D9")), arrayOf(W * .82f, H * .14f, S * .025f, c("#E6B89A")))) {
        val x = mx as Float; val y = my as Float; val rr = mr as Float; val cl = col as Color
        drawCircle(Brush.radialGradient(0f to mix(cl, Color.White, .3f), .6f to cl, 1f to mix(cl, Color.Black, .8f), center = Offset(x - rr * .4f, y - rr * .5f), radius = rr * 1.8f), radius = rr, center = Offset(x, y))
    }
    // Étoile filante discrète
    val ph = (t * 1.5f) % 1f
    if (ph < .12f) { val e = ph / .12f; val sx = W * (.9f - e * .5f); val sy = H * (.05f + e * .15f); drawLine(Brush.linearGradient(0f to c("#FFFFFF", 0f), 1f to c("#FFFFFF", .8f * (1f - e)), start = Offset(sx + W * .1f, sy - H * .03f), end = Offset(sx, sy)), Offset(sx + W * .1f, sy - H * .03f), Offset(sx, sy), strokeWidth = S * .003f, cap = StrokeCap.Round) }
}

/** Cœur (pointe en bas), centré en (x, y), largeur ≈ 2s. */
private fun heart(x: Float, y: Float, s: Float): Path {
    val p = Path(); p.moveTo(x, y + s * .9f)
    p.cubicTo(x - s * 1.25f, y + s * .05f, x - s * .95f, y - s * 1.05f, x, y - s * .38f)
    p.cubicTo(x + s * .95f, y - s * 1.05f, x + s * 1.25f, y + s * .05f, x, y + s * .9f); p.close(); return p
}
/** Papillon (ailes qui battent selon [flap] ∈ [0, 1]). */
private fun DrawScope.butterfly(x: Float, y: Float, s: Float, flap: Float, a: Color, b: Color) {
    val k = .25f + .75f * abs(flap)
    for (sd in listOf(-1f, 1f)) {
        drawOval(a, topLeft = Offset(x + (if (sd < 0) -s * k else 0f), y - s * .8f), size = Size(s * k, s * .8f))
        drawOval(b, topLeft = Offset(x + (if (sd < 0) -s * .7f * k else 0f), y - s * .05f), size = Size(s * .7f * k, s * .55f))
    }
    line(x, y - s * .6f, x, y + s * .45f, max(1f, s * .12f), c("#2B2130"))
}
/** Fleur à pétales (marguerite, coquelicot…) vue de face. */
private fun DrawScope.flower(x: Float, y: Float, s: Float, petals: Int, petal: Color, heart: Color, rot: Float = 0f) {
    for (i in 0 until petals) { val a = i / petals.toFloat() * 360f + rot; val ar = a / 360f * TAU; leaf(x + cos(ar) * s * .55f, y + sin(ar) * s * .55f, s * 1.0f, a, petal, .45f) }
    disc(x, y, s * .28f, heart)
}

/* ------------------------------------------------------------------ Village enneigé */
private fun DrawScope.snowvillage(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#0E1630"), .35f to c("#1B2A4A"), .6f to c("#3B4F7A"), .72f to c("#6F7FA8"), y1 = H * .75f)
    starField(91, 140, H * .45f, c("#E6EEFF", .7f), t, 2)
    glow(W * .25f, H * .14f, S * .5f, c("#CFE0FF", .25f), BlendMode.Screen); disc(W * .25f, H * .14f, S * .045f, c("#F4F6FF"))
    // Collines enneigées et sapins lointains
    val n1 = Nz(6); val hill = { x: Float -> H * .6f - (n1.at(x / W * 2f, 3) * .5f + .5f) * H * .06f }
    conifers(30, 4, H * .03f, H * .06f, c("#2A3A5E"), hill)
    ridgePts(H * .6f, H * .06f, 80) { u -> n1.at(u * 2f, 3) * .5f + .5f }.let { ridgeFill(it, vg(H * .54f, H, 0f to c("#B9C8E6"), 1f to c("#8FA3CC"))) }
    // Maisons : murs colorés sourds, toits épais de neige, fenêtres chaudes
    data class Hs(val x: Float, val y: Float, val w: Float, val h: Float, val wall: Color)
    val houses = listOf(Hs(.1f, .7f, .2f, .12f, c("#7A4A5A")), Hs(.32f, .69f, .16f, .14f, c("#4A5F7A")), Hs(.66f, .7f, .22f, .12f, c("#7A6248")), Hs(.86f, .71f, .18f, .11f, c("#5A4A70")),
        Hs(.2f, .8f, .26f, .14f, c("#8A5040")), Hs(.78f, .82f, .3f, .15f, c("#4F6A62")))
    // clocher
    run { val x = W * .5f; val y = H * .7f; val w = S * .1f
        rect(x - w / 2, y - H * .2f, w, H * .2f, c("#D9D2C8")); fill(poly(x - w * .65f, y - H * .2f, x, y - H * .3f, x + w * .65f, y - H * .2f), c("#3A4A6A"))
        fill(poly(x - w * .45f, y - H * .215f, x, y - H * .29f, x + w * .1f, y - H * .275f, x - w * .25f, y - H * .2f), c("#F4F8FF", .85f))
        drawRoundRect(c("#FFC870"), topLeft = Offset(x - w * .2f, y - H * .16f), size = Size(w * .4f, H * .05f), cornerRadius = CornerRadius(w * .2f, w * .2f))
        line(x, y - H * .3f, x, y - H * .325f, max(1f, S * .004f), c("#3A4A6A")); line(x - S * .01f, y - H * .315f, x + S * .01f, y - H * .315f, max(1f, S * .004f), c("#3A4A6A")) }
    for ((i, h) in houses.withIndex()) {
        val x = W * h.x; val y = H * h.y; val w = W * h.w; val hh = H * h.h * .55f; val rh = H * h.h * .45f
        drawRect(Brush.horizontalGradient(0f to h.wall, 1f to mix(h.wall, c("#1A1A30"), .35f), startX = x - w / 2, endX = x + w / 2), topLeft = Offset(x - w / 2, y - hh), size = Size(w, hh))
        // cheminée et fumée
        rect(x + w * .2f, y - hh - rh * .8f, w * .1f, rh * .6f, mix(h.wall, c("#1A1A30"), .3f))
        for (k in 0 until 5) { val u = (t * 1.5f + k / 5f + i * .13f) % 1f; glow(x + w * .25f + u * w * .3f + sin(u * 5f + i) * w * .05f, y - hh - rh * (.9f + u * 1.4f), w * (.06f + u * .12f), c("#C9D3EA", .2f * (1f - u))) }
        // toit + neige épaisse débordante
        fill(poly(x - w * .58f, y - hh, x, y - hh - rh, x + w * .58f, y - hh), c("#3A3848"))
        val snow = Path(); snow.moveTo(x - w * .62f, y - hh + rh * .05f); snow.lineTo(x, y - hh - rh * 1.08f); snow.lineTo(x + w * .62f, y - hh + rh * .05f)
        snow.cubicTo(x + w * .5f, y - hh + rh * .2f, x + w * .4f, y - hh + rh * .05f, x + w * .3f, y - hh - rh * .2f); snow.lineTo(x, y - hh - rh * .8f)
        snow.lineTo(x - w * .3f, y - hh - rh * .2f); snow.cubicTo(x - w * .42f, y - hh + rh * .1f, x - w * .52f, y - hh + rh * .22f, x - w * .62f, y - hh + rh * .05f); snow.close()
        drawPath(snow, Brush.linearGradient(0f to c("#FFFFFF"), 1f to c("#B9C8EA"), start = Offset(x - w * .3f, y - hh - rh), end = Offset(x + w * .4f, y - hh)))
        // fenêtres
        val nw = if (w > W * .2f) 3 else 2
        for (k in 0 until nw) { val wx = x - w * .38f + k * w * .76f / (nw - 1) - w * .07f; val wy = y - hh * .72f; val on = .85f + .15f * sin(t * TAU * 3 + i + k)
            glow(wx + w * .07f, wy + hh * .2f, w * .35f, c("#FFB04A", .3f * on), BlendMode.Screen)
            rect(wx, wy, w * .14f, hh * .42f, c("#FFC870", on)); line(wx + w * .07f, wy, wx + w * .07f, wy + hh * .42f, max(1f, S * .002f), mix(h.wall, Color.Black, .3f))
            rect(wx - w * .015f, wy + hh * .42f, w * .17f, hh * .05f, c("#F4F8FF")) }
        // flaque de lumière sur la neige
        ellGlow(x, y + H * .01f, w * .6f, H * .015f, c("#FFC27A", .3f), BlendMode.Screen)
    }
    // Guirlande lumineuse
    val g = Path(); val gx0 = W * .02f; val gx1 = W * .98f; val gy = H * .63f; g.moveTo(gx0, gy)
    for (i in 1..40) { val u = i / 40f; g.lineTo(gx0 + (gx1 - gx0) * u, gy + sin(u * 3.1416f * 4).let { abs(it) } * H * .025f) }
    drawPath(g, c("#1A1A30", .6f), style = Stroke(max(1f, S * .002f)))
    val bulbs = listOf(c("#FF5A5A"), c("#FFD25A"), c("#5AE0A0"), c("#5AB4FF"))
    for (i in 0..40 step 2) { val u = i / 40f; val bx = gx0 + (gx1 - gx0) * u; val by = gy + abs(sin(u * 3.1416f * 4)) * H * .025f + S * .006f; val on = .6f + .4f * sin(t * TAU * 4 + i)
        glow(bx, by, S * .02f, bulbs[(i / 2) % 4].a(.5f * on), BlendMode.Screen); disc(bx, by, S * .004f, bulbs[(i / 2) % 4]) }
    // Premier plan : congère bleutée, reflets scintillants
    val n2 = Nz(9); ridgePts(H * .88f, H * .03f, 60) { u -> n2.at(u * 3f, 3) * .5f + .5f }.let { ridgeFill(it, vg(H * .85f, H, 0f to c("#E8EEFF"), 1f to c("#9FB2DA"))) }
    val sp = Mul(3); for (i in 0 until 40) { val x = sp() * W; val y = H * (.88f + sp() * .1f); val a = .5f + .5f * sin(t * TAU * 5 + i * 2.3f); disc(x, y, S * .002f, c("#FFFFFF", a)) }
    // Neige qui tombe (deux profondeurs)
    for (layer in 0 until 2) {
        val r = Mul(50 + layer); val pts = ArrayList<Offset>()
        for (i in 0 until 140) { val sp2 = .5f + r() * .5f; val u = (r() + t * sp2 * (1 + layer)) % 1f; pts.add(Offset(r() * W + sin(u * TAU * 2 + i) * S * .02f, u * H)) }
        drawPoints(pts, PointMode.Points, c("#FFFFFF", .55f + layer * .3f), strokeWidth = S * (.004f + layer * .004f), cap = StrokeCap.Round)
    }
}

/* ------------------------------------------------------------------ Feux d'artifice */
private fun DrawScope.fireworks(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val q = H * .78f
    sky(0f to c("#03040F"), .5f to c("#0A0E2A"), .78f to c("#241A4A"), y1 = q)
    starField(13, 120, q * .8f, c("#E6EEFF", .5f), t)
    val cols = listOf(c("#FF5A7A"), c("#FFD25A"), c("#5AD8FF"), c("#B98AFF"), c("#7DFFB0"), c("#FF9A4A"))
    // Gerbes : départ, éclosion, retombée (chacune décalée dans le cycle)
    fun bursts(mirror: Boolean) {
        val r = Mul(5)
        for (b in 0 until 7) {
            val cx = W * (.15f + r() * .7f); val cy = H * (.12f + r() * .35f); val col = cols[b % cols.size]; val col2 = cols[(b + 2) % cols.size]
            val rad = S * (.18f + r() * .14f); val ph = (t * 1.4f + b / 7f) % 1f; val n = 36 + (r() * 20).toInt()
            val yy = { y: Float -> if (mirror) 2 * q - y else y }
            if (ph < .18f) { // fusée qui monte
                val u = ph / .18f; val x = cx + (1 - u) * S * .02f; val y = q - (q - cy) * (1 - (1 - u) * (1 - u))
                drawLine(Brush.verticalGradient(0f to col.a(.9f), 1f to col.a(0f), startY = yy(y), endY = yy(y + S * .08f)), Offset(x, yy(y)), Offset(x, yy(y + S * .08f)), strokeWidth = S * .004f, cap = StrokeCap.Round)
            } else {
                val u = (ph - .18f) / .82f; val e = 1 - (1 - u).pow(3f); val fade = (1 - u).pow(1.5f)
                if (!mirror) glow(cx, cy, rad * 1.6f, col.a(.3f * fade), BlendMode.Screen)
                for (i in 0 until n) {
                    val a = i / n.toFloat() * TAU + b; val sp = .75f + ((i * 7919) % 13) / 13f * .35f
                    val x = cx + cos(a) * rad * e * sp; val y = cy + sin(a) * rad * e * sp + u * u * rad * .55f
                    val x0 = cx + cos(a) * rad * max(0f, e - .18f) * sp; val y0 = cy + sin(a) * rad * max(0f, e - .18f) * sp + max(0f, u - .1f).pow(2f) * rad * .55f
                    val cc = if (i % 3 == 0) col2 else col
                    drawLine(Brush.linearGradient(0f to cc.a(0f), 1f to cc.a(fade), start = Offset(x0, yy(y0)), end = Offset(x, yy(y))), Offset(x0, yy(y0)), Offset(x, yy(y)), strokeWidth = S * .007f, cap = StrokeCap.Round, blendMode = BlendMode.Screen)
                    disc(x, yy(y), S * .004f, mix(cc, Color.White, .6f).a(fade))
                    if (u > .5f && (i + (t * 60).toInt()) % 3 == 0) disc(x, yy(y), S * .0025f, Color.White.copy(alpha = fade))
                }
            }
        }
    }
    bursts(false)
    // Fumée colorée
    for (i in 0 until 5) ellGlow(W * (.1f + i * .2f), H * (.42f + (i % 2) * .05f), W * .3f, H * .04f, c("#6A5A9A", .12f))
    // Ville au bord de l'eau
    val r = Mul(8); var x = 0f
    val wins = ArrayList<Offset>()
    while (x < W) { val w = S * (.05f + r() * .09f); val h = H * (.04f + r() * .14f); rect(x, q - h, w + 1f, h, c("#0B0A1E")); var wy = q - h + S * .015f
        while (wy < q - S * .01f) { var wx = x + S * .01f; while (wx < x + w - S * .01f) { if (r() < .25f) wins.add(Offset(wx, wy)); wx += S * .014f }; wy += S * .02f }
        if (r() < .2f) { line(x + w / 2, q - h, x + w / 2, q - h - H * .03f, max(1f, S * .003f), c("#0B0A1E")) }
        x += w }
    drawPoints(wins, PointMode.Points, c("#FFD08A", .7f), strokeWidth = S * .006f, cap = StrokeCap.Square)
    // Fleuve : reflets
    drawRect(vg(q, H, 0f to c("#141838"), 1f to c("#05061A")), topLeft = Offset(0f, q), size = Size(W, H - q))
    clipRect(0f, q, W, H) { bursts(true) }
    drawRect(vg(q, H, 0f to c("#141838", .45f), 1f to c("#05061A", .85f)), topLeft = Offset(0f, q), size = Size(W, H - q))
    for (i in 0 until 30) { val y = q + (H - q) * (i + .5f) / 30f; line(0f, y, W, y, max(1f, S * .0015f), c("#05061A", .5f)) }
}

/* ------------------------------------------------------------------ Montagne d'hiver */
private fun DrawScope.snowpeak(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#7FA8DE"), .3f to c("#A9C6EE"), .55f to c("#DDEBFF"), .7f to c("#F4F8FF"))
    glow(W * .8f, H * .12f, S * .9f, c("#FFFFFF", .55f), BlendMode.Screen)
    softCloud(W * .3f + t * W * .05f, H * .2f, W * .8f, c("#FFFFFF", .5f), c("#FFFFFF", .5f), 4)
    // Grand sommet : faces au soleil blanches, faces à l'ombre bleutées, arêtes nettes
    val peak = listOf(-.1f to .62f, .12f to .5f, .25f to .42f, .38f to .28f, .5f to .17f, .58f to .25f, .66f to .31f, .78f to .37f, .9f to .45f, 1.1f to .55f)
    val p = Path(); p.moveTo(-W * .1f, H); for ((u, v) in peak) p.lineTo(W * u, H * v); p.lineTo(W * 1.1f, H); p.close()
    drawPath(p, vg(H * .17f, H * .7f, 0f to c("#FFFFFF"), 1f to c("#DCE8F8")))
    // ombres (versant droit de chaque arête + couloirs)
    val sh = c("#9DB7E0")
    fill(poly(W * .5f, H * .17f, W * .58f, H * .25f, W * .62f, H * .4f, W * .55f, H * .55f, W * .5f, H * .7f, W * .47f, H * .45f, W * .49f, H * .3f), vg(H * .17f, H * .7f, 0f to sh, 1f to sh.a(.4f)))
    fill(poly(W * .66f, H * .31f, W * .78f, H * .37f, W * .8f, H * .5f, W * .72f, H * .62f, W * .7f, H * .45f), sh.a(.7f))
    fill(poly(W * .38f, H * .28f, W * .43f, H * .38f, W * .4f, H * .55f, W * .34f, H * .6f, W * .36f, H * .42f), sh.a(.5f))
    fill(poly(W * .12f, H * .5f, W * .2f, H * .52f, W * .18f, H * .64f, W * .1f, H * .66f), sh.a(.5f))
    mist(H * .62f, H * .08f, c("#FFFFFF", .7f))
    // Collines et sapins chargés de neige
    for (k in 0 until 2) {
        val nz = Nz(12 + k); val base = H * (.72f + k * .1f)
        val ln = { x: Float -> base - (nz.at(x / W * 2.5f, 3) * .5f + .5f) * H * .04f }
        conifers(16 - k * 5, 30 + k, H * (.06f + k * .05f), H * (.1f + k * .08f), mix(c("#4E6E8E"), c("#21405A"), k.toFloat()), ln)
        ridgePts(base, H * .04f, 60) { u -> nz.at(u * 2.5f, 3) * .5f + .5f }.let { ridgeFill(it, vg(base - H * .04f, H, 0f to c("#FFFFFF"), 1f to c("#C9DAF2"))) }
    }
    // Paillettes et flocons
    val sp = Mul(3); for (i in 0 until 50) { val x = sp() * W; val y = H * (.75f + sp() * .25f); disc(x, y, S * .002f, c("#FFFFFF", .5f + .5f * sin(t * TAU * 4 + i * 2.1f))) }
    val r = Mul(5); val pts = ArrayList<Offset>()
    for (i in 0 until 90) { val u = (r() + t * (.4f + r() * .4f)) % 1f; pts.add(Offset(r() * W + sin(u * TAU * 2 + i) * S * .02f, u * H)) }
    drawPoints(pts, PointMode.Points, c("#FFFFFF", .85f), strokeWidth = S * .006f, cap = StrokeCap.Round)
}

/* ------------------------------------------------------------------ Ciel de cœurs */
private fun DrawScope.heartsky(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#C9A6F2"), .3f to c("#E9B6E6"), .55f to c("#FFC6DC"), .8f to c("#FFD6E6"), 1f to c("#FFE6EF"))
    glow(W * .5f, H * .62f, S * 1.1f, c("#FFF2E6", .6f), BlendMode.Screen)
    // Mer de nuages pastel (couches)
    for (k in 0 until 4) {
        val d = k / 3f; val y0 = H * (.62f + d * .3f); val r = Mul(70 + k)
        val col = mix(c("#F6D2F0"), c("#FFE3EE"), d); val shade = mix(c("#D9A6D8"), c("#F2B6CE"), d)
        for (i in 0 until 14) { val x = W * (i / 13f) + (r() - .5f) * W * .1f + sin(t * TAU + k + i) * S * .01f; val rx = W * (.14f + r() * .1f); val ry = rx * (.5f + r() * .2f)
            drawPath(lobed(x, y0, rx, ry, 1000 + k * 30 + i, 6, .18f), vg(y0 - ry, y0 + ry, 0f to Color.White, .6f to col, 1f to shade)) }
    }
    // Cœurs qui s'envolent
    val r = Mul(9)
    for (i in 0 until 16) {
        val sp = .4f + r() * .5f; val u = (r() + t * sp) % 1f; val x = W * (.08f + r() * .84f) + sin(u * TAU * 1.5f + i) * S * .04f; val y = H * (1.05f - u * 1.15f); val s = S * (.025f + r() * .05f)
        val col = listOf(c("#FF5C8A"), c("#FF8AB0"), c("#E0559A"), c("#FFB0C8"))[i % 4]; val fade = sm(0f, .1f, u) * sm(1f, .85f, u)
        rotate(sin(u * TAU + i) * 12f, Offset(x, y)) {
            val hp = heart(x, y, s)
            drawPath(hp, Brush.linearGradient(0f to mix(col, Color.White, .45f).a(fade), 1f to col.a(fade), start = Offset(x - s, y - s), end = Offset(x + s, y + s)))
            drawOval(Color.White.copy(alpha = .55f * fade), topLeft = Offset(x - s * .62f, y - s * .5f), size = Size(s * .35f, s * .22f))
        }
    }
    val b = Mul(4); for (i in 0 until 14) glow(b() * W, b() * H * .6f, S * (.02f + b() * .04f), c("#FFFFFF", .3f), BlendMode.Screen)
}

/* ------------------------------------------------------------------ Prairie de Pâques */
private fun DrawScope.eggmeadow(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#7FCBF2"), .35f to c("#CFF1FF"), .55f to c("#F2FCFF"))
    glow(W * .2f, H * .12f, S * .8f, c("#FFF8DC", .6f), BlendMode.Screen)
    softCloud(W * .6f + t * W * .05f, H * .2f, W * .7f, c("#FFFFFF", .7f), c("#FFFFFF", .6f), 3)
    softCloud(W * .2f + t * W * .03f, H * .33f, W * .5f, c("#FFFFFF", .6f), c("#FFFFFF", .5f), 8)
    // Collines (perspective atmosphérique)
    val tones = listOf(c("#BFE3B0"), c("#9CD484"), c("#7CC25E"), c("#5FA846"))
    for ((k, col) in tones.withIndex()) {
        val nz = Nz(30 + k); val base = H * (.52f + k * .08f)
        ridgePts(base, H * .07f, 80) { u -> nz.at(u * 1.6f + k, 3) * .5f + .5f }.let { ridgeFill(it, vg(base - H * .07f, H, 0f to mix(col, Color.White, .15f), 1f to mix(col, c("#2E6A2A"), .2f))) }
        if (k == 1) { crown(W * .78f, base - H * .1f, S * .1f, S * .085f, 44, c("#C8EAA0"), c("#7CC25E"), c("#3E7A34")); rect(W * .78f - S * .006f, base - H * .07f, S * .012f, H * .05f, c("#6A4A34")) }
    }
    // Œufs décorés, à moitié dans l'herbe
    val eggs = listOf(Triple(.2f, .86f, c("#FF9AB8")), Triple(.42f, .9f, c("#8AD0FF")), Triple(.66f, .85f, c("#FFD25A")), Triple(.84f, .91f, c("#B98AFF")), Triple(.08f, .95f, c("#7DE0B0")))
    for ((i, e) in eggs.withIndex()) {
        val x = W * e.first; val y = H * e.second; val ew = S * .07f; val eh = ew * 1.3f; val col = e.third
        val ep = Path(); ep.moveTo(x, y - eh); ep.cubicTo(x + ew * .9f, y - eh, x + ew, y + eh * .55f, x, y + eh * .55f); ep.cubicTo(x - ew, y + eh * .55f, x - ew * .9f, y - eh, x, y - eh); ep.close()
        rotate((i - 2) * 8f, Offset(x, y)) {
            drawPath(ep, Brush.radialGradient(0f to mix(col, Color.White, .5f), 1f to mix(col, c("#5A3A6A"), .25f), center = Offset(x - ew * .3f, y - eh * .4f), radius = eh * 1.4f))
            clipPath(ep) {
                val acc = listOf(c("#FFFFFF", .8f), c("#FF5C8A", .7f), c("#3A7AD9", .6f))[i % 3]
                rect(x - ew, y - eh * .3f, ew * 2, eh * .14f, acc)
                for (k in -3..3) { val zx = x + k * ew * .3f; fill(poly(zx - ew * .15f, y + eh * .05f, zx, y - eh * .08f, zx + ew * .15f, y + eh * .05f, zx, y + eh * .18f), acc.a(.6f)) }
                for (k in -2..2) disc(x + k * ew * .4f, y - eh * .6f, ew * .08f, acc)
            }
        }
    }
    // Herbes hautes et fleurs (premier plan)
    val r = Mul(11); val g1 = Path(); val g2 = Path(); val sway = sin(t * TAU) * S * .006f
    for (i in 0 until 420) { val x = r() * W; val y = H * (.8f + r() * .22f); val h = S * (.03f + r() * .06f) * (y / H); val gp = if (r() < .5f) g1 else g2
        gp.moveTo(x, y); gp.cubicTo(x, y - h * .5f, x + sway + (r() - .5f) * h * .3f, y - h * .8f, x + sway * 1.5f + (r() - .5f) * h * .4f, y - h) }
    drawPath(g1, c("#3E8A3A"), style = Stroke(max(1f, S * .004f), cap = StrokeCap.Round)); drawPath(g2, c("#7CC25E"), style = Stroke(max(1f, S * .003f), cap = StrokeCap.Round))
    val fr = Mul(13)
    for (i in 0 until 26) { val x = fr() * W; val y = H * (.8f + fr() * .2f); val s = S * (.008f + fr() * .01f) * (y / H)
        if (fr() < .6f) flower(x, y, s, 8, c("#FFFFFF"), c("#FFC83A"), fr() * 40f) else flower(x, y, s, 5, c("#FF9AC8"), c("#FFE07A"), fr() * 40f) }
    // Papillons
    for (i in 0 until 3) { val u = (t + i / 3f) % 1f; val x = W * (.1f + u * .8f); val y = H * (.6f + .05f * sin(u * TAU * 3 + i)); butterfly(x, y, S * .025f, sin(t * TAU * 30 + i), c("#FFB347", .9f), c("#FF7A5A", .9f)) }
}

/* ------------------------------------------------------------------ Champ fleuri */
private fun DrawScope.flowerfield(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    sky(0f to c("#6FC0F0"), .3f to c("#BFE8FF"), .5f to c("#EAF8FF"))
    glow(W * .7f, H * .1f, S * .9f, c("#FFF8DC", .6f), BlendMode.Screen)
    softCloud(W * .3f + t * W * .05f, H * .18f, W * .8f, c("#FFFFFF", .7f), c("#FFFFFF", .6f), 5)
    softCloud(W * .75f + t * W * .03f, H * .3f, W * .5f, c("#FFFFFF", .6f), c("#FFFFFF", .5f), 9)
    val tones = listOf(c("#C7E6B8"), c("#A8D98A"), c("#86C866"), c("#68B04C"))
    for ((k, col) in tones.withIndex()) {
        val nz = Nz(60 + k); val base = H * (.5f + k * .07f)
        val pts = ridgePts(base, H * .08f, 80) { u -> nz.at(u * 1.4f + k * 2f, 3) * .5f + .5f }
        ridgeFill(pts, vg(base - H * .08f, H, 0f to mix(col, Color.White, .2f), 1f to mix(col, c("#2E6A2A"), .25f)))
        if (k == 1) { // arbre solitaire sur la colline
            val x = W * .3f; val idx = (.3f * 80).toInt(); val y = pts[idx * 2 + 1]
            rect(x - S * .008f, y - H * .06f, S * .016f, H * .06f, c("#5A4030")); crown(x, y - H * .075f, S * .09f, S * .075f, 77, c("#C8EAA0"), c("#6FB04E"), c("#2F6A30"))
        }
        // taches de fleurs lointaines
        val r = Mul(70 + k); val pp = ArrayList<Offset>(); val pr = ArrayList<Offset>()
        for (i in 0 until 160 + k * 60) { val x = r() * W; val idx = ((x / W) * 80).toInt().coerceIn(0, 80); val y = pts[idx * 2 + 1] + r() * H * .08f; if (r() < .6f) pr.add(Offset(x, y)) else pp.add(Offset(x, y)) }
        drawPoints(pr, PointMode.Points, c("#F2463A", .8f), strokeWidth = S * (.003f + k * .002f), cap = StrokeCap.Round)
        drawPoints(pp, PointMode.Points, c("#FFFFFF", .8f), strokeWidth = S * (.003f + k * .002f), cap = StrokeCap.Round)
    }
    // Premier plan : herbes, coquelicots, bleuets, marguerites
    val r = Mul(17); val sway = sin(t * TAU) * S * .008f
    val g = Path(); for (i in 0 until 300) { val x = r() * W; val y = H * (.82f + r() * .2f); val h = S * (.04f + r() * .07f); g.moveTo(x, y); g.cubicTo(x, y - h * .5f, x + sway, y - h * .8f, x + sway * 1.6f + (r() - .5f) * h * .3f, y - h) }
    drawPath(g, c("#4E9A3E"), style = Stroke(max(1f, S * .0035f), cap = StrokeCap.Round))
    for (i in 0 until 34) {
        val x = r() * W; val y = H * (.8f + r() * .2f); val s = S * (.012f + r() * .02f) * (.6f + (y - H * .8f) / H * 3f); val kind = r()
        line(x, y + s * 3f, x + sway, y, max(1f, s * .12f), c("#3E7A30"))
        when { kind < .45f -> { for (k in 0 until 4) { val a = k * 90f + 45f + i * 10f; val ar = a / 360f * TAU; drawOval(c("#E8322A"), topLeft = Offset(x + sway + cos(ar) * s * .35f - s * .5f, y + sin(ar) * s * .35f - s * .5f), size = Size(s, s)) }
                    disc(x + sway, y, s * .25f, c("#2A1A20")) }
            kind < .7f -> flower(x + sway, y, s * .8f, 8, c("#4A7AE8"), c("#2A3A8A"), i * 13f)
            else -> flower(x + sway, y, s * .8f, 12, c("#FFFFFF"), c("#FFC83A"), i * 7f) }
    }
    for (i in 0 until 3) { val u = (t + i / 3f) % 1f; val x = W * (.1f + u * .8f); val y = H * (.66f + .04f * sin(u * TAU * 3 + i)); butterfly(x, y, S * .022f, sin(t * TAU * 30 + i), c("#FFFFFF", .9f), c("#FFE07A", .9f)) }
}

/* ------------------------------------------------------------------ Plage d'été */
private fun DrawScope.summerbeach(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val hz = H * .5f
    sky(0f to c("#3FA8EA"), .55f to c("#8FD3F7"), .9f to c("#D6F1F6"), 1f to c("#FFF2CC"), y1 = hz + 2f)
    val sun = Offset(W * .7f, H * .2f)
    glow(sun.x, sun.y, S * 1f, c("#FFF2B0", .55f), BlendMode.Screen); glow(sun.x, sun.y, S * .12f, c("#FFFFFF", .95f), BlendMode.Screen)
    softCloud(W * .2f + t * W * .05f, H * .3f, W * .6f, c("#FFFFFF", .7f), c("#FFFFFF", .6f), 2)
    // Mer et voilier
    drawRect(vg(hz, H * .7f, 0f to c("#2A8FC8"), .4f to c("#3FB4D8"), 1f to c("#7FD8E0")), topLeft = Offset(0f, hz), size = Size(W, H * .2f))
    val bx = W * (.2f + .1f * sin(t * TAU)); val by = hz + H * .01f
    fill(poly(bx - S * .03f, by, bx + S * .03f, by, bx + S * .025f, by + S * .008f, bx - S * .025f, by + S * .008f), c("#FFFFFF"))
    fill(poly(bx, by - S * .002f, bx, by - S * .07f, bx + S * .035f, by - S * .004f), c("#FFFFFF")); fill(poly(bx - S * .003f, by - S * .002f, bx - S * .003f, by - S * .055f, bx - S * .03f, by - S * .004f), c("#FF7A5A"))
    val r = Mul(4)
    for (i in 0 until 90) { val y = hz + H * .2f * r().pow(1.5f); val x = sun.x + (r() - .5f) * W * (.2f + (y - hz) / H * 2f); val wl = S * (.006f + r() * .02f)
        line(x - wl, y, x + wl, y, max(1f, S * .002f), c("#FFFFFF", (.3f + .5f * (.5f + .5f * sin(t * TAU * 4 + i))))) }
    // Vagues qui déferlent (deux lignes d'écume)
    for (k in 0 until 3) {
        val ph = (t + k / 3f) % 1f; val y = H * (.6f + ph * .1f); val a = sm(0f, .2f, ph) * (1f - sm(.75f, 1f, ph))
        val p = Path(); p.moveTo(0f, y); for (i in 1..30) { val x = W * i / 30f; p.lineTo(x, y + sin(x / W * 10f + k * 2f) * H * .004f) }
        drawPath(p, c("#FFFFFF", .8f * a), style = Stroke(S * (.004f + ph * .006f), cap = StrokeCap.Round))
    }
    // Sable
    val shore = Nz(3); val sp = ridgePts(H * .71f + sin(t * TAU) * H * .005f, H * .01f, 60) { u -> shore.at(u * 3f, 3) }
    ridgeFill(sp, vg(H * .7f, H, 0f to c("#F2DCA8"), 1f to c("#E6C27E")))
    ridgeFill(sp, vg(H * .7f, H * .74f, 0f to c("#C9AE7E", .6f), 1f to c("#C9AE7E", 0f)))
    val g = Mul(9); val gp = ArrayList<Offset>(); for (k in 0 until 400) gp.add(Offset(g() * W, H * .74f + g() * H * .26f))
    drawPoints(gp, PointMode.Points, c("#B8986A", .35f), strokeWidth = max(1f, S / 450f))
    // Parasols rayés, serviettes, ballon (ombres portées)
    fun parasol(x: Float, y: Float, s: Float, a: Color, b: Color) {
        ellGlow(x - s * .2f, y, s * 1.1f, s * .25f, c("#8A6A3E", .35f))
        line(x, y, x + s * .1f, y - s * 1.6f, max(1f, s * .05f), c("#EDE3D0"))
        val top = Offset(x + s * .1f, y - s * 1.6f); val n = 8
        for (i in 0 until n) { val a0 = 180f + i * 180f / n; val a1 = a0 + 180f / n
            val p0 = Offset(top.x + cos(a0 / 360f * TAU) * s, top.y + sin(a0 / 360f * TAU) * s * .35f + s * .25f); val p1 = Offset(top.x + cos(a1 / 360f * TAU) * s, top.y + sin(a1 / 360f * TAU) * s * .35f + s * .25f)
            val path = Path(); path.moveTo(top.x, top.y - s * .1f); path.lineTo(p0.x, p0.y); path.cubicTo(p0.x + (p1.x - p0.x) * .3f, p0.y + s * .06f, p0.x + (p1.x - p0.x) * .7f, p1.y + s * .06f, p1.x, p1.y); path.close()
            drawPath(path, if (i % 2 == 0) a else b) }
        drawPath(Path().apply { moveTo(top.x, top.y - s * .1f); lineTo(top.x - s, top.y + s * .25f); lineTo(top.x + s, top.y + s * .25f); close() }, Brush.horizontalGradient(0f to c("#FFFFFF", .25f), 1f to c("#000000", .15f), startX = top.x - s, endX = top.x + s))
    }
    rotate(-8f, Offset(W * .3f, H * .86f)) { drawRoundRect(c("#3FA9E0"), topLeft = Offset(W * .18f, H * .84f), size = Size(W * .22f, H * .05f), cornerRadius = CornerRadius(S * .01f, S * .01f)); for (k in 0 until 4) rect(W * .18f + k * W * .055f, H * .84f, W * .02f, H * .05f, c("#FFFFFF", .7f)) }
    parasol(W * .3f, H * .87f, S * .27f, c("#FF5A5A"), c("#FFFFFF"))
    parasol(W * .78f, H * .8f, S * .18f, c("#3FB4D8"), c("#FFE07A"))
    val ball = Offset(W * .62f, H * .92f); val br = S * .04f
    ellGlow(ball.x + br * .5f, ball.y + br * .9f, br * 1.2f, br * .3f, c("#8A6A3E", .35f))
    clipPath(Path().apply { val n = 24; for (i in 0..n) { val a = i / n.toFloat() * TAU; val x = ball.x + cos(a) * br; val y = ball.y + sin(a) * br; if (i == 0) moveTo(x, y) else lineTo(x, y) }; close() }) {
        rect(ball.x - br, ball.y - br, br * 2, br * 2, c("#FFFFFF"))
        fill(poly(ball.x, ball.y, ball.x - br, ball.y - br * .4f, ball.x - br, ball.y + br * .5f), c("#FF5A5A")); fill(poly(ball.x, ball.y, ball.x + br, ball.y - br * .6f, ball.x + br, ball.y + br * .3f), c("#3F8AE0"))
        fill(poly(ball.x, ball.y, ball.x - br * .3f, ball.y + br, ball.x + br * .5f, ball.y + br), c("#FFD25A"))
        drawCircle(Brush.radialGradient(0f to Color.Transparent, .6f to Color.Transparent, 1f to c("#000000", .3f), center = Offset(ball.x - br * .3f, ball.y - br * .3f), radius = br * 1.5f), radius = br * 1.2f, center = ball)
    }
    // Mouettes
    val m = Mul(5)
    for (i in 0 until 3) { val x = W * (.3f + m() * .4f) + t * W * .1f; val y = H * (.22f + m() * .1f); val s = S * (.015f + m() * .008f); val f = sin(t * TAU * 10 + i) * .3f
        val p = Path(); p.moveTo(x - s, y - s * (.2f + f)); p.cubicTo(x - s * .5f, y - s * .7f, x - s * .2f, y - s * .5f, x, y); p.cubicTo(x + s * .2f, y - s * .5f, x + s * .5f, y - s * .7f, x + s, y - s * (.2f + f))
        drawPath(p, c("#3A4A5A", .7f), style = Stroke(max(1f, S / 400f), cap = StrokeCap.Round)) }
}

/* ------------------------------------------------------------------ Fonds gratuits : dégradés « mesh » aux couleurs du thème */

/**
 * Palette d'un fond gratuit, dérivée du thème : [cols] = 4 couleurs d'ambiance (souvent très saturées), adoucies ici
 * (pastel en clair, profondes en sombre) pour un rendu mesh contemporain ; [al] = intensité voulue par le thème.
 */
internal class FreePal(val dark: Boolean, val bg: Color, val ink: Color, cols: List<Color>, val al: Float) {
    private fun soft(col: Color): Color {
        val l = col.red * .3f + col.green * .59f + col.blue * .11f
        val gray = Color(l, l, l)
        val desat = mix(col, gray, if (dark) .1f else .22f)
        return if (dark) mix(desat, bg, .1f) else mix(desat, Color.White, .28f)
    }
    /** En sombre, les teintes froides prennent les grandes surfaces (les chaudes, sur fond nuit, virent au brun). */
    val c: List<Color> = List(4) { soft(cols[it % cols.size]) }.let { l -> if (dark) l.sortedBy { it.red - it.blue * .8f - it.green * .2f } else l }
    /** Intensité effective : en sombre, les couleurs doivent rester lumineuses (sinon elles se ternissent). */
    val k = if (dark) min(1f, al * 1.75f) else min(1f, al * 1.3f)
    /** Fond de base : léger lavis des couleurs sur la teinte du thème. */
    val base0 = if (dark) mix(bg, c[0], .14f) else mix(bg, c[0], .14f)
    val base1 = if (dark) mix(bg, c[1], .08f) else mix(bg, c[3], .12f)
}

/** Champ de couleur radial très doux (cœur dense, décroissance longue) : la brique du dégradé mesh. */
private fun DrawScope.field(x: Float, y: Float, r: Float, col: Color, a: Float, blend: BlendMode = BlendMode.SrcOver) {
    if (r <= 1f || a <= 0f) return
    drawCircle(Brush.radialGradient(0f to col.a(a), .3f to col.a(a * .78f), .6f to col.a(a * .32f), .85f to col.a(a * .08f), 1f to col.a(0f), center = Offset(x, y), radius = r), radius = r, center = Offset(x, y), blendMode = blend)
}
/** Finition des fonds gratuits : reflet nacré (clair) ou lueur haute (sombre), grain fin, vignettage. */
private fun DrawScope.freeFinish(fp: FreePal) {
    val W = size.width; val H = size.height; val m = max(W, H)
    if (!fp.dark) drawRect(Brush.linearGradient(0f to Color.White.copy(alpha = .38f), .45f to Color.White.copy(alpha = .06f), 1f to Color.White.copy(alpha = 0f), start = Offset.Zero, end = Offset(W * .8f, H * .6f)))
    else drawRect(Brush.radialGradient(0f to Color.Transparent, .55f to Color.Transparent, 1f to Color.Black.copy(alpha = .4f), center = Offset(W * .5f, H * .4f), radius = m * .85f))
    finish(fp.dark, 0f)
}

internal fun DrawScope.freeBg(kind: String, fp: FreePal, t: Float) {
    when (kind) {
        "aurora" -> freeAurora(fp, t); "waves" -> freeWaves(fp, t); "stars" -> freeStars(fp, t)
        "mesh" -> freeMesh(fp, t); "grid" -> freeGrid(fp, t); else -> freeBlob(fp, t)
    }
    freeFinish(fp)
}

/** Blob : sphères de couleur au bord fondu (reflet en haut à gauche, cœur plus dense) qui dérivent sur un dégradé mesh. */
private fun DrawScope.freeBlob(fp: FreePal, t: Float) {
    val W = size.width; val H = size.height; val m = max(W, H); val a = t * TAU; val k = fp.k
    val bl = if (fp.dark) BlendMode.Screen else BlendMode.SrcOver
    drawRect(Brush.linearGradient(0f to fp.base0, 1f to fp.base1, start = Offset(0f, 0f), end = Offset(W, H)))
    val dk = if (fp.dark) .55f else 1f
    field(W * (.1f + .05f * sin(a)), H * (.08f + .03f * cos(a)), m * .7f, fp.c[0], .7f * k * dk)
    field(W * (.95f + .04f * cos(a)), H * (.4f + .04f * sin(a)), m * .6f, fp.c[1], .6f * k * dk)
    field(W * (.25f + .06f * sin(a + 2f)), H * .95f, m * .7f, fp.c[if (fp.dark) 0 else 2], .6f * k * dk)
    val orbs = listOf(floatArrayOf(.72f, .2f, .26f, 1f), floatArrayOf(.22f, .48f, .2f, 3f), floatArrayOf(.68f, .7f, .32f, 2f))
    for ((i, o) in orbs.withIndex()) {
        val cx = W * (o[0] + .04f * sin(a + i * 2f)); val cy = H * (o[1] + .025f * cos(a + i)); val r = m * o[2] * .5f
        val ci = if (fp.dark) listOf(1, 3, 0)[i] else o[3].toInt(); val col = fp.c[ci]; val col2 = fp.c[(ci + 1) % 4]
        drawCircle(Brush.radialGradient(0f to mix(col, Color.White, if (fp.dark) .25f else .55f).a(.95f * k), .45f to col.a(.85f * k), .8f to col2.a(.55f * k), 1f to col2.a(0f), center = Offset(cx - r * .35f, cy - r * .4f), radius = r * 1.45f), radius = r * 1.1f, center = Offset(cx, cy), blendMode = bl)
    }
    field(W * .35f, H * .25f, m * .3f, Color.White, if (fp.dark) .04f else .25f)
}

/** Aurore : voiles verticaux lumineux sur un ciel mesh. */
private fun DrawScope.freeAurora(fp: FreePal, t: Float) {
    val W = size.width; val H = size.height; val m = max(W, H); val k = fp.k
    drawRect(Brush.verticalGradient(0f to (if (fp.dark) mix(fp.bg, Color.Black, .3f) else mix(fp.bg, fp.c[3], .2f)), .6f to fp.base0, 1f to fp.base1))
    field(W * .5f, H * 1.05f, m * .7f, fp.c[2], .5f * k)
    val ph = sin(t * TAU) * .5f; val ph2 = cos(t * TAU) * .4f
    for (b in 0 until 3) {
        val n1 = Nz(11 + b); val n2 = Nz(21 + b); val n3 = Nz(31 + b); val N = 110; val col = fp.c[b]; val col2 = fp.c[(b + 1) % 4]
        for (i in 0 until N) {
            val u = i / (N - 1f); val x = W * u
            val yb = H * (.3f + b * .1f + .07f * n1.at(u * 1.8f + ph + b, 3)); val h = H * (.22f + .1f * (n2.at(u * 2.5f + ph2, 3) * .5f + .5f))
            val ray = (.5f + .5f * (n3.at(u * 30f + ph * 2f, 2) * .5f + .5f)) * k * (if (fp.dark) 1f else .85f)
            drawRect(Brush.verticalGradient(0f to col2.a(0f), .5f to col2.a(.18f * ray), .85f to col.a(.55f * ray), .97f to col.a(.75f * ray), 1f to col.a(0f), startY = yb - h, endY = yb + H * .01f),
                topLeft = Offset(x - W / N, yb - h), size = Size(W / N * 2.2f, h + H * .01f), blendMode = if (fp.dark) BlendMode.Screen else BlendMode.SrcOver)
        }
    }
    if (fp.dark) starField(5, 140, H * .6f, fp.ink.a(.6f), t)
    // Reflet de l'aurore au sol : dégradé bas
    drawRect(Brush.verticalGradient(0f to Color.Transparent, .75f to Color.Transparent, 1f to fp.c[0].a(.3f * k)))
}

/** Vagues : bandes superposées, ombres portées douces, crêtes nacrées. */
private fun DrawScope.freeWaves(fp: FreePal, t: Float) {
    val W = size.width; val H = size.height; val m = max(W, H); val a = t * TAU; val k = fp.k
    drawRect(Brush.verticalGradient(0f to fp.base1, 1f to fp.base0))
    field(W * .8f, H * .15f, m * .55f, fp.c[if (fp.dark) 0 else 3], .6f * k); field(W * .1f, H * .3f, m * .5f, fp.c[1], .4f * k)
    for (i in 0 until 5) {
        val base = H * (.42f + i * .12f); val col = fp.c[i % 4]; val col2 = fp.c[(i + 1) % 4]
        fun yAt(x: Float) = base + H * (.035f - i * .004f) * sin(x / W * (1.2f + i * .15f) * TAU + a + i * 1.3f) + H * .012f * sin(x / W * 2.7f * TAU - a * 1.3f + i)
        val p = Path(); p.moveTo(0f, H); var x = 0f; while (x <= W + 4f) { p.lineTo(x, yAt(x)); x += 4f }; p.lineTo(W, H); p.close()
        // ombre portée vers le haut (profondeur), puis la bande
        val sh = Path(); sh.moveTo(0f, H); x = 0f; while (x <= W + 4f) { sh.lineTo(x, yAt(x) - H * .012f); x += 4f }; sh.lineTo(W, H); sh.close()
        drawPath(sh, Brush.verticalGradient(0f to (if (fp.dark) Color.Black.copy(alpha = .25f) else mix(col, Color.Black, .4f).a(.12f)), 1f to Color.Transparent, startY = base - H * .05f, endY = base + H * .05f))
        drawPath(p, Brush.linearGradient(0f to mix(col, if (fp.dark) Color.White else Color.White, if (fp.dark) .08f else .25f).a(.92f * k + .08f), 1f to mix(col2, fp.bg, .35f).a(.92f * k + .08f), start = Offset(0f, base - H * .05f), end = Offset(W, base + H * .2f)))
        val crest = Path(); x = 0f; while (x <= W + 4f) { if (x == 0f) crest.moveTo(x, yAt(x)) else crest.lineTo(x, yAt(x)); x += 4f }
        drawPath(crest, Color.White.copy(alpha = if (fp.dark) .16f else .55f), style = Stroke(max(1f, min(W, H) / 360f)))
    }
}

/** Constellation : ciel profond (ou pastel), voie lactée, figures reliées par des traits fins. */
private fun DrawScope.freeStars(fp: FreePal, t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val m = max(W, H); val k = fp.k
    drawRect(Brush.verticalGradient(0f to (if (fp.dark) mix(fp.bg, Color.Black, .35f) else fp.base1), 1f to fp.base0))
    field(W * .75f, H * .25f, m * .6f, fp.c[1], .45f * k); field(W * .2f, H * .7f, m * .6f, fp.c[3], .4f * k); field(W * .5f, H * .45f, m * .35f, fp.c[0], .25f * k)
    // Voie lactée diagonale
    val r = Mul(19); val pts = ArrayList<Offset>()
    for (i in 0 until 500) { val u = r(); val v = (r() + r() + r() - 1.5f) * .1f; pts.add(Offset(W * (u * 1.2f - .1f), H * (.85f - u * .7f + v))) }
    val starCol = if (fp.dark) Color(.95f, .95f, 1f) else mix(fp.ink, fp.c[2], .3f)
    drawPoints(pts, PointMode.Points, starCol.a(if (fp.dark) .35f else .22f), strokeWidth = max(1f, S / 500f), cap = StrokeCap.Round)
    starField(13, 200, H, starCol.a(if (fp.dark) .85f else .55f), t, 3)
    // Figures : étoiles principales reliées
    val figs = listOf(listOf(.15f to .18f, .27f to .14f, .36f to .2f, .45f to .16f, .52f to .26f, .44f to .33f), listOf(.62f to .55f, .7f to .48f, .8f to .52f, .86f to .62f, .76f to .68f, .7f to .48f), listOf(.18f to .7f, .26f to .78f, .34f to .74f, .3f to .86f))
    for ((fi, f) in figs.withIndex()) {
        val p = Path(); f.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(W * x, H * y) else p.lineTo(W * x, H * y) }
        drawPath(p, starCol.a(if (fp.dark) .22f else .2f), style = Stroke(max(1f, S / 500f), cap = StrokeCap.Round))
        f.forEachIndexed { i, (x, y) -> val tw = .7f + .3f * sin(t * TAU * 2 + i + fi); glow(W * x, H * y, S * .025f, fp.c[(i + fi) % 4].a(.5f * tw), if (fp.dark) BlendMode.Screen else BlendMode.SrcOver); disc(W * x, H * y, S * .0045f, starCol.a(tw)) }
    }
    val ph = (t * 2f) % 1f
    if (ph < .14f) { val q = ph / .14f; val x = W * (.85f - q * .5f); val y = H * (.1f + q * .2f)
        drawLine(Brush.linearGradient(0f to starCol.a(0f), 1f to starCol.a((1 - q) * .8f), start = Offset(x + S * .12f, y - S * .06f), end = Offset(x, y)), Offset(x + S * .12f, y - S * .06f), Offset(x, y), strokeWidth = S * .003f, cap = StrokeCap.Round) }
}

/** Dégradé : mesh pur, six foyers de couleur qui dérivent lentement. */
private fun DrawScope.freeMesh(fp: FreePal, t: Float) {
    val W = size.width; val H = size.height; val m = max(W, H); val a = t * TAU; val k = fp.k
    drawRect(Brush.linearGradient(0f to fp.base0, 1f to fp.base1, start = Offset(W, 0f), end = Offset(0f, H)))
    val pts = listOf(floatArrayOf(.0f, .0f, 0f, 0f), floatArrayOf(1f, .1f, 1f, 1.3f), floatArrayOf(.5f, .4f, 3f, 2.1f), floatArrayOf(.0f, .7f, 2f, 3.4f), floatArrayOf(1f, .75f, 0f, 4.4f), floatArrayOf(.45f, 1.05f, 1f, 5.2f))
    for ((i, q) in pts.withIndex()) field(W * (q[0] + .14f * cos(a + q[3])), H * (q[1] + .08f * sin(a * (if (i % 2 == 0) 1f else -1f) + q[3])), m * (if (i == 2) .45f else .7f), fp.c[if (fp.dark) listOf(0, 1, 3, 1, 0, 1)[i] else q[2].toInt()], (if (i == 2) .55f else .9f) * k * (if (fp.dark) .6f else 1f))
    // Voile soyeux diagonal
    drawRect(Brush.linearGradient(0f to Color.Transparent, .48f to Color.White.copy(alpha = if (fp.dark) .03f else .14f), .52f to Color.White.copy(alpha = if (fp.dark) .05f else .2f), .6f to Color.Transparent, start = Offset(W * (.1f + .1f * sin(a)), 0f), end = Offset(W, H)))
}

/** Grille : sol quadrillé en perspective, horizon lumineux, soleil strié (rétro, version douce). */
private fun DrawScope.freeGrid(fp: FreePal, t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val m = max(W, H); val hz = H * .55f; val k = fp.k
    drawRect(Brush.verticalGradient(0f to (if (fp.dark) mix(fp.bg, Color.Black, .3f) else fp.base1), .55f to mix(fp.base0, fp.c[1], .25f * k), .551f to (if (fp.dark) mix(fp.bg, Color.Black, .2f) else mix(fp.bg, fp.c[2], .1f)), 1f to fp.base0))
    field(W * .5f, hz, m * .5f, fp.c[1], .7f * k)
    // Soleil en dégradé, coupé de bandes
    val sr = S * .22f; val sy = hz - sr * .45f
    drawCircle(Brush.verticalGradient(0f to fp.c[0], 1f to fp.c[1], startY = sy - sr, endY = sy + sr), radius = sr, center = Offset(W * .5f, sy))
    for (b in 0 until 6) { val y = sy + sr * (.05f + b * .16f); val hh = sr * .025f * (b + 1); rect(W * .5f - sr, y, sr * 2, hh, mix(fp.base0, fp.c[1], .25f * k)) }
    rect(0f, hz, W, H - hz, if (fp.dark) mix(fp.bg, Color.Black, .25f) else mix(fp.bg, fp.c[2], .08f))
    drawRect(Brush.verticalGradient(0f to fp.c[1].a(.45f * k), .35f to fp.c[3].a(.12f * k), 1f to Color.Transparent, startY = hz, endY = H), topLeft = Offset(0f, hz), size = Size(W, H - hz))
    // Lignes : fuyantes et transversales (qui défilent), plus lumineuses près de l'horizon en sombre
    val lc = if (fp.dark) fp.c[3] else mix(fp.ink, fp.c[2], .4f)
    for (i in -16..16) drawLine(Brush.verticalGradient(0f to lc.a(.05f), 1f to lc.a(if (fp.dark) .45f else .3f), startY = hz, endY = H), Offset(W * .5f + i * S * .02f, hz), Offset(W * .5f + i * S * .32f, H), strokeWidth = max(1f, S / 450f))
    for (j in 0 until 16) { val q = ((j + t) % 16f) / 16f; val y = hz + (H - hz) * q * q; drawLine(lc.a((.05f + .4f * q) * (if (fp.dark) 1f else .7f)), Offset(0f, y), Offset(W, y), strokeWidth = max(1f, S / 450f)) }
    drawLine(Brush.horizontalGradient(0f to fp.c[3].a(0f), .5f to fp.c[3].a(.9f), 1f to fp.c[3].a(0f)), Offset(0f, hz), Offset(W, hz), strokeWidth = max(1.5f, S / 300f))
    glow(W * .5f, hz, S * .6f, fp.c[3].a(.2f * k), if (fp.dark) BlendMode.Screen else BlendMode.SrcOver)
    // Montagnes filaires au loin
    val nz = Nz(4); val pts = ridgePts(hz, H * .06f, 60) { u -> nz.ridged(u * 3f, 3) * sm(.0f, .3f, abs(u - .5f)) }
    ridgeFill(pts, mix(fp.base0, fp.c[2], .3f * k).a(.8f), hz + 1f)
}
