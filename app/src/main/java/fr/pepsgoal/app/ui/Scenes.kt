package fr.pepsgoal.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import fr.pepsgoal.app.peps

/*
 * Arrière-plans illustrés de la boutique (réglage « bg » = « s:<clé> ») et fonds gratuits (Backdrop.kt) : des scènes
 * peintes sur Canvas en couches — perspective atmosphérique, lumière directionnelle, brume, grain — animées très
 * lentement (horloge économe de Backdrop.kt, lue dans la phase de dessin). Le même dessin sert d'aperçu miniature dans
 * la boutique : tout est proportionnel à la taille disponible.
 */

/** Scène illustrée [key] en plein cadre ; [still] fige l'animation (aperçus de la boutique). */
@Composable
fun SceneBg(key: String, modifier: Modifier = Modifier, still: Boolean = false) {
    // Dégradé du ciel, affiché le temps du premier rendu hors écran
    val sky = remember(key) { (SHOP_BGS.firstOrNull { it.key == key }?.sky ?: listOf("#1A1240", "#07081A")).map { c(it) } }
    val placeholder: DrawScope.() -> Unit = { drawRect(Brush.verticalGradient(sky)) }
    // Aperçu figé : rendu une seule fois, puis repris du cache à chaque défilement
    if (still) { StillCanvas("scene:$key", modifier.fillMaxSize(), placeholder) { scene(key, .3f) }; return }
    // Fond animé : dessiné hors du fil principal, au rythme plafonné par les réglages de performance
    val phase = bgPhaseState(SCENE_DUR[key] ?: 40000, SCENE_FPS[key] ?: 12)
    // Heure réelle (réglage « Suivre l'heure ») : la scène change d'éclairage au fil de la journée, redessinée au quart d'heure
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val follow = ctx.peps.prefs.bgClock
    val hour = androidx.compose.runtime.produceState(nowHour()) { while (true) { kotlinx.coroutines.delay(60_000); value = nowHour() } }
    val h = if (follow) hour.value else null
    OffscreenCanvas("scene:$key:${h?.let { (it * 4).toInt() }}", phase, modifier.fillMaxSize(), placeholder = placeholder) { t -> scene(key, t, h) }
}

/** Heure locale en heures décimales (14,5 = 14 h 30). */
private fun nowHour(): Float { val n = java.time.LocalTime.now(); return n.hour + n.minute / 60f }

/**
 * Moment de la journée à l'heure [h] : poids de la nuit, de l'aube, du jour et du crépuscule (somme 1), et course du
 * soleil [sun] (0 au lever, 1 au coucher). Lever et coucher suivent la saison (heures légales en France).
 */
private class Tod(val night: Float, val dawn: Float, val day: Float, val dusk: Float, val sun: Float) {
    val light get() = day + (dawn + dusk) * .6f
}
private fun tod(h: Float, doy: Int = java.time.LocalDate.now().dayOfYear): Tod {
    val k = cos((doy - 172) / 365f * TAU)
    val sr = 7.25f - 1.25f * k; val ss = 19.75f + 2.1f * k
    fun bump(c: Float, w: Float) = (1f - abs(h - c) / w).coerceIn(0f, 1f)
    var dawn = bump(sr, 1.3f); var dusk = bump(ss, 1.3f)
    var day = sm(sr, sr + 1.3f, h) * (1f - sm(ss - 1.3f, ss, h))
    var night = (1f - sm(sr - 1.3f, sr, h)) + sm(ss, ss + 1.3f, h)
    val n = dawn + dusk + day + night; dawn /= n; dusk /= n; day /= n; night /= n
    return Tod(night, dawn, day, dusk, ((h - sr) / (ss - sr)).coerceIn(0f, 1f))
}
/** Éclairage générique d'une scène de jour selon l'heure : bleu nuit, rose de l'aube, orangé du crépuscule. */
private fun DrawScope.grade(d: Tod) {
    val H = size.height
    if (d.dawn > .01f) drawRect(vg(0f, H, 0f to c("#7F8FE0", .2f * d.dawn), .6f to c("#FFB0C0", .2f * d.dawn), 1f to c("#FFD0B0", .06f * d.dawn)))
    if (d.dusk > .01f) drawRect(vg(0f, H, 0f to c("#5A2E8A", .28f * d.dusk), .55f to c("#FF7A3A", .24f * d.dusk), 1f to c("#FFB070", .1f * d.dusk)))
    if (d.night > .01f) { drawRect(c("#1A2550"), alpha = .7f * d.night, blendMode = BlendMode.Multiply); drawRect(c("#0A1030", .22f * d.night)) }
}
/** Couleur pondérée par le moment de la journée (jour, aube, crépuscule, nuit). */
private fun Tod.col(day: String, dawn: String, dusk: String, night: String): Color {
    val cs = listOf(c(day) to this.day, c(dawn) to this.dawn, c(dusk) to this.dusk, c(night) to this.night)
    return Color(cs.sumOf { (x, w) -> (x.red * w).toDouble() }.toFloat(), cs.sumOf { (x, w) -> (x.green * w).toDouble() }.toFloat(), cs.sumOf { (x, w) -> (x.blue * w).toDouble() }.toFloat())
}
/** Scènes qui gèrent elles-mêmes l'heure (au lieu du voile générique). */
private val OWN_TOD = setOf("loire", "lighthouse", "angers", "rooftops")

/** Une image de la scène [key] à la phase [t] ; [h] : heure réelle (null : éclairage d'origine, aperçus). */
internal fun DrawScope.scene(key: String, t: Float, h: Float? = null) {
    val td = h?.let { tod(it) }
    when (key) {
        "nebula" -> nebula(t); "deepsea" -> deepsea(t); "mountains" -> mountains(t); "citynight" -> citynight(t); "tram" -> tram(t)
        "mistforest" -> mistforest(t); "dunes" -> dunes(t); "sakura" -> sakura(t); "magma" -> magma(t); "coderain" -> coderain(t)
        "borealis" -> borealis(t); "rainwindow" -> rainwindow(t); "planet" -> planet(t); "lagoon" -> lagoon(t); "haunted" -> haunted(t)
        "snowvillage" -> snowvillage(t); "fireworks" -> fireworks(t); "snowpeak" -> snowpeak(t); "heartsky" -> heartsky(t)
        "eggmeadow" -> eggmeadow(t); "flowerfield" -> flowerfield(t); "summerbeach" -> summerbeach(t); "autumnwood" -> autumnwood(t)
        "tunnel" -> tunnel(t); "loire" -> loire(t, td); "lighthouse" -> lighthouse(t, td); "vineyard" -> vineyard(t); "balloons" -> balloons(t); "jellyfish" -> jellyfish(t)
        "bamboo" -> bamboo(t); "nighttrain" -> nighttrain(t); "rooftops" -> rooftops(t, td); "canyon" -> canyon(t)
        "savanna" -> savanna(t); "lanterns" -> lanterns(t); "icefield" -> icefield(t)
        "angers" -> angers(t, td); "angevine" -> angevine(t)
        else -> {
            val sky = SHOP_BGS.firstOrNull { it.key == key }?.sky ?: listOf("#1A1240", "#07081A")
            drawRect(Brush.verticalGradient(sky.map { c(it) }))
        }
    }
    val dark = SHOP_BGS.firstOrNull { it.key == key }?.dark ?: true
    if (td != null && !dark && key !in OWN_TOD) grade(td)
    finish(dark || (td?.night ?: 0f) > .5f, if (key == "tram") .6f else 1f)
}

/** Durée d'un cycle d'animation par scène (ms) : les scènes à mouvement visible (poissons, pluie) tournent plus vite. */
private val SCENE_DUR = mapOf("tram" to 40000, "deepsea" to 60000, "coderain" to 20000, "fireworks" to 24000, "rainwindow" to 50000, "autumnwood" to 30000, "sakura" to 30000,
    "tunnel" to 20000, "lighthouse" to 30000, "nighttrain" to 40000, "lanterns" to 60000, "jellyfish" to 50000, "savanna" to 60000, "angers" to 60000, "angevine" to 40000)
/** Images par seconde : les scènes très détaillées et presque immobiles se contentent de moins (batterie). */
private val SCENE_FPS = mapOf("tram" to 8, "mountains" to 6, "mistforest" to 8, "dunes" to 6, "planet" to 6, "citynight" to 8, "tunnel" to 15, "rooftops" to 6, "canyon" to 6, "vineyard" to 8, "angers" to 12, "angevine" to 8)

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
    '1' to intArrayOf(4, 12, 4, 4, 4, 4, 14), '0' to intArrayOf(14, 17, 19, 21, 25, 17, 14), '4' to intArrayOf(2, 6, 10, 18, 31, 2, 2), '6' to intArrayOf(6, 8, 16, 30, 17, 17, 14), ' ' to intArrayOf(0, 0, 0, 0, 0, 0, 0),
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

/* ================================================================== Arrière-plans 5.4 */

/* ------------------------------------------------------------------ Tunnel Express : le métro de Rennes dans un tunnel */

/** Profil du tunnel (l, h) : sol, paroi gauche, voûte, paroi droite, banquette d'évacuation à droite. */
private val TUN: FloatArray = run {
    val pts = ArrayList<Float>()
    pts += listOf(1.2f, 0f, -3.6f, 0f, -3.6f, 3.3f)
    for (i in 1 until 12) { val th = 3.1416f * (1f - i / 12f); pts += listOf(-.5f + 3.1f * cos(th), 3.3f + 1.3f * sin(th)) }
    pts += listOf(2.6f, 3.3f, 2.6f, .9f, 1.2f, .9f)
    pts.toFloatArray()
}

private fun DrawScope.tunnel(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    val cam = Cam(0f, -.25f, 1.35f, 0f, W * 1.12f, W * .6f, H * .38f)
    val vp = Offset(W * .6f, H * .38f); val R = max(W, H) * 1.1f
    // Fond : nuit bleutée, lueur au bout du tunnel
    drawRect(c("#0B0F17"))
    // Faces du tunnel : dégradé radial centré sur le point de fuite (sombre au loin, éclairé près de nous)
    fun face(i: Int, near: Color, far: Color) {
        val l0 = TUN[i * 2]; val h0 = TUN[i * 2 + 1]; val j = (i + 1) % (TUN.size / 2); val l1 = TUN[j * 2]; val h1 = TUN[j * 2 + 1]
        fill(cam.poly3(.35f, l0, h0, .35f, l1, h1, 500f, l1, h1, 500f, l0, h0), Brush.radialGradient(0f to c("#9FD8FF", .9f), .025f to far, .3f to mix(far, near, .45f), 1f to near, center = vp, radius = R))
    }
    val n = TUN.size / 2
    for (i in 0 until n) {
        val l0 = TUN[i * 2]; val h0 = TUN[i * 2 + 1]; val j = (i + 1) % n; val l1 = TUN[j * 2]; val h1 = TUN[j * 2 + 1]
        when {
            h0 == 0f && h1 == 0f -> face(i, c("#3A4250"), c("#0E121A"))                      // sol
            h0 >= 3.3f && h1 >= 3.3f -> face(i, c("#2A3242"), c("#0C1018"))                 // voûte
            abs(h0 - .9f) < .01f && abs(h1 - .9f) < .01f -> face(i, c("#56606E"), c("#121720")) // banquette
            else -> face(i, c("#3B4556"), c("#0F141D"))                                       // parois
        }
    }
    // Lueur au bout du tunnel
    glow(vp.x, vp.y, S * .5f, c("#5FA8FF", .35f), BlendMode.Screen); glow(vp.x, vp.y, S * .1f, c("#D6EEFF", .9f), BlendMode.Screen)
    // Bandes LED continues le long des parois (fuyantes)
    fun strip(l: Float, h: Float, col: Color, w: Float) {
        val a = cam.p(.6f, l, h); val b = cam.p(400f, l, h)
        drawLine(Brush.linearGradient(0f to col, .6f to col.a(col.alpha * .5f), 1f to col.a(0f), start = a, end = b), a, b, strokeWidth = w)
    }
    strip(-3.58f, 2.55f, c("#7FD3FF", .55f), S * .012f); strip(2.58f, 2.55f, c("#7FD3FF", .45f), S * .01f)
    strip(-3.58f, .55f, c("#FFB04A", .35f), S * .008f); strip(2.58f, 1.35f, c("#5BE38A", .3f), S * .006f)
    // Chemins de câbles sur la paroi gauche
    for (k in 0 until 3) strip(-3.55f, 1.6f + k * .18f, c("#1A2130", .9f), S * .01f)
    // Anneaux du tunnel et luminaires qui défilent vers nous (le métro file)
    val run = t * 162f
    for (k in 0 until 28) {
        val a = 6f * k + 6f - (run % 6f); if (a < .8f) continue
        val fade = sm(140f, 40f, a)
        val pr = Path(); for (i in 0 until n) { val o = cam.p(a, TUN[i * 2], TUN[i * 2 + 1]); if (i == 0) pr.moveTo(o.x, o.y) else pr.lineTo(o.x, o.y) }
        drawPath(pr, c("#05070B", .55f * fade), style = Stroke(max(1f, cam.k(a, 0f) * .05f)))
        // Luminaires : voûte et paroi gauche, halo sur la paroi
        val lamp = c("#FFF1D6")
        fill(cam.poly3(a, -.85f, 4.54f, a + 1.2f, -.85f, 4.54f, a + 1.2f, -.15f, 4.54f, a, -.15f, 4.54f), lamp.a(.92f * fade))
        val lc = cam.p(a + .7f, -.5f, 4.4f); glow(lc.x, lc.y, cam.k(a, 0f) * 1.6f, c("#FFE3B0", .28f * fade), BlendMode.Screen)
        fill(cam.poly3(a, -3.58f, 3.0f, a + 1.1f, -3.58f, 3.0f, a + 1.1f, -3.58f, 3.12f, a, -3.58f, 3.12f), c("#BFE6FF", .9f * fade))
        val wl = cam.p(a + .55f, -3.5f, 3.0f); glow(wl.x, wl.y, cam.k(a, 0f) * 1.2f, c("#9FD8FF", .22f * fade), BlendMode.Screen)
        // Traînées de vitesse derrière les luminaires de paroi
        val t0 = cam.p(a + 1.1f, -3.57f, 3.06f); val t1 = cam.p(a + 5f, -3.57f, 3.06f)
        drawLine(lg(t0.x, t0.y, t1.x, t1.y, 0f to c("#BFE6FF", .5f * fade), 1f to c("#BFE6FF", 0f)), t0, t1, strokeWidth = max(1f, cam.k(a, 0f) * .08f))
        // Balise verte de l'issue de secours, un anneau sur trois
        if (k % 3 == 0) { val g = cam.p(a, 2.58f, 1.8f); disc(g.x, g.y, cam.k(a, 0f) * .06f, c("#5BE38A", fade)); glow(g.x, g.y, cam.k(a, 0f) * .4f, c("#5BE38A", .3f * fade), BlendMode.Screen) }
    }
    // Garde-corps de la banquette
    strip(1.25f, 1.9f, c("#8A94A3", .7f), S * .006f)
    for (k in 0 until 40) { val a = 3f * k + 3f - (run % 3f); if (a < 1f) continue; val p0 = cam.p(a, 1.25f, .9f); val p1 = cam.p(a, 1.25f, 1.9f); line(p0.x, p0.y, p1.x, p1.y, max(1f, cam.k(a, 0f) * .04f), c("#7C8696", .8f * sm(120f, 30f, a))) }

    // Voie du VAL : pistes de roulement en béton et barres de guidage latérales
    val tl = -1.55f
    for (s in listOf(-1f, 1f)) {
        fill(cam.ground(.5f, 500f, tl + s * .55f, tl + s * .98f, .02f), Brush.radialGradient(0f to c("#151920"), .3f to c("#3A414C"), 1f to c("#555D6A"), center = vp, radius = R))
        strip(tl + s * .55f, .025f, c("#8A94A3", .35f), S * .004f); strip(tl + s * .98f, .025f, c("#8A94A3", .35f), S * .004f)
        strip(tl + s * 1.22f, .32f, c("#B8C2CF", .9f), S * .01f); strip(tl + s * 1.22f, .26f, c("#2A303A"), S * .012f)
    }
    // Supports des barres de guidage (défilent)
    for (k in 0 until 90) {
        val a = 1.5f * k + 1.5f - (run % 1.5f); if (a < 1.6f) continue
        val f0 = sm(90f, 20f, a)
        for (s in listOf(-1f, 1f)) { val o = cam.p(a, tl + s * 1.22f, .29f); val kk = cam.k(a, 0f); rect(o.x - kk * .04f, o.y - kk * .03f, kk * .08f, kk * .1f, c("#11151C", .85f * f0)) }
        val m0 = cam.p(a, tl - .4f, 0f); val m1 = cam.p(a, tl + .4f, 0f); line(m0.x, m0.y, m1.x, m1.y, max(1f, cam.k(a, 0f) * .03f), c("#0A0D12", .5f * f0))
    }

    // Faisceaux des phares sur la voie, devant la rame
    val A0 = 5.6f
    for (s in listOf(-.66f, .66f)) {
        val p0 = cam.p(A0, tl + s, 0f); val p1 = cam.p(2.2f, tl + s * 1.6f, 0f)
        fill(cam.poly3(A0 - .05f, tl + s - .12f, .03f, A0 - .05f, tl + s + .12f, .03f, 2.2f, tl + s * 1.6f + .7f, .03f, 2.2f, tl + s * 1.6f - .7f, .03f),
            lg(p0.x, p0.y, p1.x, p1.y, 0f to c("#FFE6B0", .32f), .45f to c("#FFE6B0", .1f), 1f to c("#FFE6B0", 0f)))
        ellGlow(p0.x, p0.y + cam.k(A0, 0f) * .25f, cam.k(A0, 0f) * 1.1f, cam.k(A0, 0f) * .35f, c("#FFE2A0", .4f), BlendMode.Screen)
    }

    /* --- La rame (VAL 208 de Rennes) : flanc droit puis face avant --- */
    val hw = 1.04f; val sl = tl + hw; val L = 26f
    fun side(a0: Float, a1: Float, h0: Float, h1: Float) = cam.poly3(a0, sl, h0, a1, sl, h0, a1, sl, h1, a0, sl, h1)
    // Caisse argentée, plus claire en haut (lumière des luminaires)
    fill(side(A0 + .1f, A0 + L, .34f, 3.22f), Brush.verticalGradient(0f to c("#E9EDF2"), .55f to c("#C3C9D2"), 1f to c("#7E8592"), startY = cam.p(A0, sl, 3.22f).y, endY = cam.p(A0, sl, .34f).y))
    // Bande des baies : verre fumé, intérieur chaud, plafonnier
    for (car in 0 until 2) {
        val c0 = A0 + car * 13f
        val segs = listOf(.5f to 2.6f, 4.3f to 7.9f, 9.6f to 12.4f)
        for ((u0, u1) in segs) {
            fill(side(c0 + u0, c0 + u1, 1.32f, 2.6f), Brush.verticalGradient(0f to c("#FFE2A0", .9f), .18f to c("#3A3426"), 1f to c("#141820"), startY = cam.p(c0 + u0, sl, 2.6f).y, endY = cam.p(c0 + u0, sl, 1.32f).y))
            fill(side(c0 + u0, c0 + u1, 1.32f, 1.62f), c("#E8C14A", .55f)) // dossiers jaunes
        }
        // Portes louvoyantes : cadre sombre et vitrage haut
        for (d in listOf(2.75f, 8.05f)) {
            fill(side(c0 + d, c0 + d + 1.4f, .5f, 2.75f), c("#B5BCC6")); fill(side(c0 + d + .08f, c0 + d + 1.32f, 1.25f, 2.62f), c("#1B2029"))
            val q0 = cam.p(c0 + d + .7f, sl, .5f); val q1 = cam.p(c0 + d + .7f, sl, 2.75f); line(q0.x, q0.y, q1.x, q1.y, max(1f, cam.k(c0 + d, sl) * .025f), c("#5A616C"))
        }
        // Soufflet d'intercirculation
        if (car == 0) fill(side(c0 + 12.85f, c0 + 13.15f, .5f, 3.0f), c("#20242C"))
    }
    // Filet sombre sous le pavillon, jupe et roues à pneus
    fill(side(A0 + .1f, A0 + L, 2.86f, 2.94f), c("#2A2F38", .9f))
    fill(side(A0 + .1f, A0 + L, .34f, .7f), c("#5D6470"))
    for (b in listOf(1.6f, 11.4f, 14.6f, 24.4f)) fill(side(A0 + b - .5f, A0 + b + .5f, .1f, .55f), c("#0C0F14"))
    // Reflets des luminaires qui glissent sur le flanc
    for (k in 0 until 6) { val a = A0 + 6f * k + 6f - (run % 6f); if (a > A0 + L - 1f) continue
        fill(side(a, a + 1.6f, 2.95f, 3.2f), c("#FFFFFF", .35f)); fill(side(a + .2f, a + 1.1f, .72f, .9f), c("#FFFFFF", .12f)) }

    // Face avant (plan a = A0)
    fun F(u: Float, h: Float) = cam.p(A0, tl + u, h)
    val kf = cam.k(A0, tl)
    fun rr(u0: Float, h0: Float, u1: Float, h1: Float, r: Float, b: Brush) { val a = F(u0, h1); val z = F(u1, h0); drawRoundRect(b, Offset(a.x, a.y), Size(z.x - a.x, z.y - a.y), CornerRadius(r * kf)) }
    fun rr(u0: Float, h0: Float, u1: Float, h1: Float, r: Float, col: Color) = rr(u0, h0, u1, h1, r, androidx.compose.ui.graphics.SolidColor(col))
    val top = F(0f, 3.3f).y; val bot = F(0f, .3f).y
    // Ombre portée et caisse
    glow(F(0f, .3f).x, bot, kf * 1.6f, c("#000000", .55f))
    rr(-hw, .3f, hw, 3.3f, .28f, Brush.verticalGradient(0f to c("#F4F6F9"), .5f to c("#D5DAE1"), 1f to c("#9AA1AC"), startY = top, endY = bot))
    rr(-hw, 3.0f, hw, 3.3f, .28f, Brush.verticalGradient(0f to c("#FFFFFF", .7f), 1f to c("#FFFFFF", 0f), startY = top, endY = F(0f, 3.0f).y))
    // Masque noir : girouette, pare-brise, phares
    rr(-.88f, .8f, .88f, 3.12f, .2f, Brush.verticalGradient(0f to c("#1B1E25"), 1f to c("#0A0B0F"), startY = F(0f, 3.12f).y, endY = F(0f, .8f).y))
    // Girouette : numéro de la rame
    val g0 = F(-.3f, 3.06f); val g1 = F(.3f, 2.8f)
    ledText("64", floatArrayOf(g0.x, g0.y, g1.x, g0.y, g0.x, g1.y, g1.x, g1.y), c("#FFFFFF"), max(1.2f, kf * .045f))
    // Pare-brise : intérieur éclairé (sièges jaunes, plafonnier), verre fumé, reflet
    rr(-.76f, 1.28f, .76f, 2.7f, .1f, Brush.verticalGradient(0f to c("#FFE9B8"), .35f to c("#D9B76A"), 1f to c("#6B5A32"), startY = F(0f, 2.7f).y, endY = F(0f, 1.28f).y))
    // Intérieur en perspective : plafond, parois, sol, sièges jaunes, porte du fond (rame sans conducteur)
    fun fp(vararg q: Float): Path { val pts = FloatArray(q.size); var i = 0; while (i < q.size) { val o = F(q[i], q[i + 1]); pts[i] = o.x; pts[i + 1] = o.y; i += 2 }; return poly(*pts) }
    fill(fp(-.76f, 2.7f, .76f, 2.7f, .22f, 2.2f, -.22f, 2.2f), c("#F6E3B4"))
    fill(fp(-.12f, 2.7f, .12f, 2.7f, .04f, 2.2f, -.04f, 2.2f), c("#FFFBEF"))
    fill(fp(-.76f, 2.7f, -.22f, 2.2f, -.22f, 1.76f, -.76f, 1.28f), c("#C9A45E")); fill(fp(.76f, 2.7f, .22f, 2.2f, .22f, 1.76f, .76f, 1.28f), c("#B8934F"))
    fill(fp(-.68f, 2.5f, -.3f, 2.14f, -.3f, 1.95f, -.68f, 2.15f), c("#2A3040")); fill(fp(.68f, 2.5f, .3f, 2.14f, .3f, 1.95f, .68f, 2.15f), c("#2A3040"))
    fill(fp(-.76f, 1.28f, .76f, 1.28f, .22f, 1.76f, -.22f, 1.76f), c("#5A4A2C"))
    for (sgn in listOf(-1f, 1f)) for ((u, h, w) in listOf(Triple(.62f, 1.35f, .2f), Triple(.44f, 1.55f, .12f), Triple(.33f, 1.66f, .07f)))
        rr(sgn * u - w / 2, h, sgn * u + w / 2, h + w * 2.6f, w * .25f, c("#F2C230"))
    rr(-.22f, 1.76f, .22f, 2.2f, .02f, c("#FFF4D6")); rr(-.08f, 1.78f, .08f, 2.12f, .02f, c("#3A3426", .6f))
    rr(-.76f, 1.28f, .76f, 2.7f, .1f, c("#0D1118", .38f))
    val w0 = F(-.76f, 2.7f); val w1 = F(.76f, 1.28f)
    clipRect(w0.x, w0.y, w1.x, w1.y) {
        fill(poly(w0.x + (w1.x - w0.x) * .55f, w0.y, w0.x + (w1.x - w0.x) * .75f, w0.y, w0.x + (w1.x - w0.x) * .35f, w1.y, w0.x + (w1.x - w0.x) * .15f, w1.y), c("#FFFFFF", .13f))
        fill(poly(w0.x + (w1.x - w0.x) * .8f, w0.y, w0.x + (w1.x - w0.x) * .86f, w0.y, w0.x + (w1.x - w0.x) * .46f, w1.y, w0.x + (w1.x - w0.x) * .4f, w1.y), c("#FFFFFF", .1f))
    }
    // Logo blanc sous le pare-brise
    rr(.3f, 1.12f, .62f, 1.19f, .03f, c("#FFFFFF", .85f))
    // Phares (lumière chaude), feux de position
    for (s in listOf(-1f, 1f)) {
        val o = F(s * .66f, 1.0f)
        glow(o.x, o.y, kf * 1.3f, c("#FFE2A0", .45f), BlendMode.Screen); glow(o.x, o.y, kf * .35f, c("#FFF6E0", .95f), BlendMode.Screen)
        disc(o.x, o.y, kf * .075f, c("#FFFBEE"))
    }
    // Jupe, attelage
    rr(-.3f, .42f, .3f, .74f, .08f, c("#1A1D23")); val cp = F(0f, .58f); disc(cp.x, cp.y, kf * .07f, c("#3A3F48")); disc(cp.x, cp.y, kf * .035f, c("#0A0B0E"))
    // Liseré lumineux du contour (luminaires derrière)
    drawRoundRect(c("#FFFFFF", .5f), F(-hw, 3.3f), Size(F(hw, .3f).x - F(-hw, 3.3f).x, bot - top), CornerRadius(.28f * kf), style = Stroke(max(1f, kf * .012f)))
    // Brume légère et lueur des phares dans l'air
    mist(H * .62f, H * .2f, c("#9FB7D6", .06f))
    for (s in listOf(-1f, 1f)) { val o = F(s * .66f, 1.0f); ellGlow(o.x, o.y + kf * .4f, kf * 1.8f, kf * .6f, c("#FFE2A0", .14f), BlendMode.Screen) }
}

/* ------------------------------------------------------------------ Loire : bords de Loire en Anjou, fin d'après-midi */

/** Village de tuffeau et château (Saumur) sur la rive d'en face, posé sur la ligne [y]. */
private fun DrawScope.loireTown(y: Float, a: Float, lightsOnly: Boolean = false) {
    val W = size.width; val S = min(W, size.height); val u = S / 100f
    val tuf = c("#F4EBDD", a); val tufSh = c("#D9C9B0", a); val slate = c("#5E6E86", a); val slateLit = c("#8A9AB4", a); val win = c("#6A5A48", a)
    // Maisons : murs de tuffeau, toits d'ardoise à deux pans, quelques lucarnes
    val r = Mul(31)
    var x = W * .02f
    while (x < W * .98f) {
        val w = u * (5f + r() * 5f); val h = u * (3.2f + r() * 2.5f); val rh = u * (2.4f + r() * 1.6f)
        if (x > W * .52f && x < W * .86f) { x += w; continue }
        if (!lightsOnly) {
            rect(x, y - h, w, h, tuf); rect(x + w * .62f, y - h, w * .38f, h, tufSh)
            fill(poly(x - u * .3f, y - h, x + w * .5f, y - h - rh, x + w + u * .3f, y - h), slate)
            fill(poly(x - u * .3f, y - h, x + w * .5f, y - h - rh, x + w * .5f, y - h), slateLit)
        }
        // La nuit : fenêtres allumées (une sur deux environ), avec leur halo
        for (k in 0 until (w / (u * 2.4f)).toInt()) { val wx = x + u * (1f + k * 2.4f); val wy = y - h * .65f
            if (!lightsOnly) rect(wx, wy, u * .8f, u * 1.1f, win) else if ((k + (x / u).toInt()) % 2 == 0) { rect(wx, wy, u * .8f, u * 1.1f, c("#FFD27A", a)); glow(wx + u * .4f, wy + u * .5f, u * 2.5f, c("#FFB04A", .35f * a), BlendMode.Screen) } }
        if (r() < .4f && !lightsOnly) rect(x + w * .7f, y - h - rh * .7f, u * .6f, rh * .5f, tufSh)
        x += w + u * (.2f + r() * 1.2f)
    }
    if (lightsOnly) {
        // Château illuminé (projecteurs sur le tuffeau)
        val cx = W * .69f; glow(cx, y - u * 12f, u * 22f, c("#FFD9A0", .3f * a), BlendMode.Screen)
        for (k in 0 until 6) rect(cx - u * 26f * .38f + k * u * 26f * .15f, y - u * 4f - u * 13f * .62f, u * 1.1f, u * 2f, c("#FFD27A", a))
        return
    }
    // Église et son clocher d'ardoise
    val ex = W * .3f; rect(ex - u * 3f, y - u * 9f, u * 6f, u * 9f, tuf); fill(poly(ex - u * 3.6f, y - u * 9f, ex, y - u * 22f, ex + u * 3.6f, y - u * 9f), slate)
    fill(poly(ex - u * 3.6f, y - u * 9f, ex, y - u * 22f, ex, y - u * 9f), slateLit); rect(ex - u * .9f, y - u * 7f, u * 1.8f, u * 2.6f, win)
    // Château de Saumur : logis blanc, quatre tours à toits en poivrière, lanternons
    val cx = W * .69f; val cw = u * 26f; val ch = u * 13f; val by = y - u * 4f
    rect(cx - cw * .62f, by, cw * 1.24f, u * 4f, tufSh) // terrasse
    rect(cx - cw / 2, by - ch, cw, ch, tuf); rect(cx + cw * .15f, by - ch, cw * .35f, ch, tufSh)
    fill(poly(cx - cw / 2, by - ch, cx - cw * .38f, by - ch - u * 6f, cx + cw * .38f, by - ch - u * 6f, cx + cw / 2, by - ch), slate)
    for (k in 0 until 6) rect(cx - cw * .38f + k * cw * .15f, by - ch * .62f, u * 1.1f, u * 2f, win)
    for ((tx, th) in listOf(-.5f to 1.3f, -.17f to 1.5f, .17f to 1.45f, .5f to 1.25f)) {
        val px = cx + cw * tx; val tw = u * 4.2f; val top = by - ch * th
        rect(px - tw / 2, top, tw, by - top, tuf); rect(px + tw * .1f, top, tw * .4f, by - top, tufSh)
        rect(px - tw * .62f, top - u * .9f, tw * 1.24f, u * 1.2f, tufSh) // mâchicoulis
        fill(poly(px - tw * .62f, top - u * .9f, px, top - u * 11f, px + tw * .62f, top - u * .9f), slate)
        fill(poly(px - tw * .62f, top - u * .9f, px, top - u * 11f, px, top - u * .9f), slateLit)
        line(px, top - u * 11f, px, top - u * 13.5f, max(1f, u * .35f), c("#C9A45E", a)); disc(px, top - u * 13.6f, u * .45f, c("#E8C46A", a))
        rect(px - u * .5f, top + u * 2.5f, u * 1f, u * 1.6f, win)
    }
}

private fun DrawScope.loire(t: Float, d: Tod? = null) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val bank = H * .47f; val near = H * .8f
    // Ciel : fin d'après-midi par défaut ; à l'heure réelle, aube rose, jour bleu, crépuscule orangé ou nuit
    if (d == null) sky(0f to c("#5E93CF"), .35f to c("#9DC0E4"), .72f to c("#F2D3B6"), 1f to c("#FCE4C2"), y1 = bank)
    else sky(0f to d.col("#3F86D6", "#6A7FC0", "#3A4A8A", "#070C22"), .35f to d.col("#8EC0EC", "#C9A8D0", "#B87A9A", "#142048"),
        .72f to d.col("#D6E8F6", "#FFC9B0", "#FFB07A", "#24345E"), 1f to d.col("#EAF2F8", "#FFE2C8", "#FFD49A", "#33446C"), y1 = bank)
    val sun = d?.sun ?: .88f; val sunA = if (d == null) 1f else (1f - d.night).coerceIn(0f, 1f)
    val sx = W * (.12f + .76f * sun); val sy = if (d == null) H * .33f else bank - H * .04f - (bank - H * .14f) * sin(sun * 3.1416f)
    val warm = if (d == null) 1f else (d.dawn + d.dusk + .4f * d.day)
    if (sunA > .01f) {
        glow(sx, sy, S * 1.1f, mix(c("#FFF4DA"), c("#FFD9A0"), warm).a(.45f * sunA), BlendMode.Screen); glow(sx, sy, S * .22f, c("#FFF2D6", .9f * sunA), BlendMode.Screen); disc(sx, sy, S * .04f, c("#FFF8EA", sunA))
    }
    for (i in 0 until 4) { val dx = ((t * (.6f + i * .15f) + i * .27f) % 1.3f - .15f) * W * 1.1f; softCloud(dx, H * (.08f + i * .07f), W * (.5f + i * .08f), c("#FFFFFF", (.5f - i * .06f) * (1f - .8f * (d?.night ?: 0f))), c("#FFF1DE", .45f * (1f - .8f * (d?.night ?: 0f))), 40 + i) }
    // Coteaux au loin, puis coteau de vignes
    val nz = Nz(21)
    ridgeFill(ridgePts(bank - H * .03f, H * .045f, 80) { x -> .5f + .5f * nz.at(x * 4f, 3) }, vg(bank - H * .08f, bank, 0f to c("#A9AACB"), 1f to c("#C9BFC9")), bank)
    val vine = ridgePts(bank - H * .005f, H * .03f, 80) { x -> .4f + .4f * nz.at(x * 6f + 3f, 3) }
    ridgeFill(vine, vg(bank - H * .04f, bank, 0f to c("#9DB078"), 1f to c("#7E9560")), bank)
    clipPath(Path().apply { moveTo(0f, bank); var i = 0; while (i < vine.size) { lineTo(vine[i], vine[i + 1]); i += 2 }; lineTo(W, bank); close() }) {
        for (k in -20..40) line(W * k / 24f, bank, W * k / 24f + W * .3f, bank - H * .06f, max(1f, u * .25f), c("#5E7448", .5f))
    }
    // Village et château de tuffeau
    val town = bank + u * 1.5f
    canopy(14, 7, u * 2f, u * 3.6f, c("#6E8A55"), c("#9FB574"), false, { town }, 0f, W * .5f)
    loireTown(town, 1f)
    canopy(10, 9, u * 1.6f, u * 3f, c("#5E7A4A"), c("#93AE6A"), false, { town + u * .5f }, W * .5f, W)
    // La Loire : large, claire, reflets du ciel
    rect(0f, town, W, near - town + u * 4f, c("#9CC0DE"))
    if (d == null) drawRect(vg(town, near, 0f to c("#F2DCC2"), .25f to c("#BFD3E6"), 1f to c("#6F9CC8")), topLeft = Offset(0f, town), size = Size(W, near - town + u * 4f))
    else drawRect(vg(town, near, 0f to d.col("#DCEAF6", "#F2D2C8", "#F7C9A0", "#2A3A60"), .25f to d.col("#B4D2EC", "#C8C0DA", "#C49AB0", "#1C2A50"), 1f to d.col("#3F7DC0", "#6F8FC8", "#4A5E9A", "#0A1430")), topLeft = Offset(0f, town), size = Size(W, near - town + u * 4f))
    // Reflet du village (inversé, étiré, adouci)
    clipRect(0f, town, W, near) { scale(1f, -.7f, Offset(0f, town)) { loireTown(town, .28f) } }
    mist(town + u * 1.5f, u * 5f, c("#FFF1DE", .45f))
    // Chemin de lumière du soleil (ou de la lune) : paillettes qui scintillent
    val r = Mul(5)
    val gx = if (d != null && d.night > .5f) W * .25f else sx
    for (i in 0 until 90) { val v = r(); val y = town + (near - town) * (.05f + v * .9f); val spread = W * (.04f + v * .22f); val x = gx + (r() - .5f) * 2f * spread
        val tw = .5f + .5f * sin(t * TAU * 6 + i * 1.7f); val len = u * (1f + v * 4f); line(x - len, y, x + len, y, max(1f, u * (.15f + v * .3f)), c("#FFF6E2", (.25f + .55f * tw) * (1f - v * .5f))) }
    // Rides sur l'eau
    for (i in 0 until 40) { val v = r(); val y = town + (near - town) * (.1f + v * .9f); val x = ((r() + t * (.2f + v * .3f)) % 1f) * W; val len = u * (2f + v * 9f)
        line(x, y, x + len, y, max(1f, u * .2f), c("#FFFFFF", .18f + v * .12f)) }
    // Bancs de sable blonds (et leur ombre)
    for ((bx, byk, bw) in listOf(Triple(.2f, .3f, .38f), Triple(.78f, .55f, .3f), Triple(.45f, .7f, .2f))) {
        val by = town + (near - town) * byk; val w = W * bw; val h = u * (2f + byk * 3f)
        val sd = (bx * 100).toInt()
        fill(lobed(W * bx + w * .02f, by + h * .25f, w * .54f, h * .62f, sd, 5, .12f), c("#6F8FB0", .35f))
        fill(lobed(W * bx, by, w * .5f, h * .5f, sd, 5, .12f, .3f), vg(by - h / 2, by + h / 2, 0f to c("#FAEBCB"), .6f to c("#E6CB98"), 1f to c("#C9A874")))
        fill(lobed(W * bx - w * .05f, by - h * .12f, w * .32f, h * .2f, sd + 3, 4, .15f), c("#FFF6E0", .5f))
    }
    // Héron cendré sur le banc de sable
    run { val hx = W * .3f; val hy = town + (near - town) * .3f - u * .4f; val g = c("#5A6272")
        scale(1.5f, 1.5f, Offset(hx, hy)) {
        line(hx, hy, hx - u * .3f, hy - u * 3f, max(1f, u * .25f), g); line(hx + u * .5f, hy, hx + u * .2f, hy - u * 3f, max(1f, u * .25f), g)
        drawOval(c("#8E97A6"), Offset(hx - u * 1.8f, hy - u * 5.2f), Size(u * 4.2f, u * 2.4f))
        line(hx + u * 1.6f, hy - u * 4.6f, hx + u * 2.4f, hy - u * 7.4f, max(1f, u * .45f), c("#C9CFD8")); disc(hx + u * 2.5f, hy - u * 7.6f, u * .55f, c("#DDE2E8"))
        line(hx + u * 2.8f, hy - u * 7.6f, hx + u * 4.2f, hy - u * 7.2f, max(1f, u * .25f), c("#D9A441"))
        line(hx + u * 2.2f, hy - u * 7.9f, hx + u * 1.2f, hy - u * 8.4f, max(1f, u * .2f), c("#2A2E36")) } }
    // Toue cabanée qui dérive lentement
    run { val bx = W * (.62f + .06f * sin(t * TAU)); val by = town + (near - town) * .48f; val L = u * 22f; val hh = u * 2f
        drawOval(c("#3A4A5E", .25f), Offset(bx - L * .55f, by + hh * .4f), Size(L * 1.1f, hh * 1.2f))
        for (k in 0 until 5) line(bx - L * (.45f - k * .02f), by + hh * (1.2f + k * .9f), bx + L * (.45f - k * .06f), by + hh * (1.2f + k * .9f), max(1f, u * .35f), c("#4A3A2E", .22f - k * .04f))
        fill(Path().apply { moveTo(bx - L / 2, by - hh * .4f); quadraticTo(bx - L * .62f, by - hh * 1.6f, bx - L * .66f, by - hh * 2f); lineTo(bx - L * .5f, by + hh * .4f); lineTo(bx + L * .48f, by + hh * .4f); lineTo(bx + L * .52f, by - hh * .5f); close() }, vg(by - hh, by + hh, 0f to c("#7A5236"), 1f to c("#3E2A1C")))
        val cl = bx - L * .1f; val cr = bx + L * .3f; val ct = by - hh * 3.4f
        fill(Path().apply { moveTo(cl, by - hh * .4f); lineTo(cl, ct + hh); quadraticTo((cl + cr) / 2, ct - hh * .8f, cr, ct + hh); lineTo(cr, by - hh * .4f); close() }, vg(ct, by, 0f to c("#3A3632"), 1f to c("#1E1C1A")))
        line(bx - L * .3f, by - hh * .4f, bx - L * .3f, by - hh * 9f, max(1f, u * .3f), c("#4A3424"))
        line(bx + L * .5f, by - hh * .2f, bx + L * .7f, by + hh * .2f, max(1f, u * .2f), c("#FFFFFF", .5f)) }
    // Sternes dans le ciel
    for (i in 0 until 4) { val k = (t * 2f + i * .23f) % 1f; val x = W * (1.1f - k * 1.3f); val y = H * (.18f + i * .04f) + sin(k * TAU * 3 + i) * u * 1.5f; val s = u * (1.2f + i * .2f); val fl = sin(t * TAU * 20 + i) * .5f
        drawPath(Path().apply { moveTo(x - s * 1.4f, y - s * (.4f + fl)); quadraticTo(x - s * .5f, y - s * .2f, x, y + s * .2f); quadraticTo(x + s * .5f, y - s * .2f, x + s * 1.4f, y - s * (.4f + fl)) }, c("#3A3F4A", .8f), style = Stroke(max(1f, u * .3f))) }
    // Rive proche : levée, herbes hautes, roseaux
    val shore = ridgePts(near, H * .02f, 60) { x -> .5f + .5f * nz.at(x * 5f + 9f, 3) }
    ridgeFill(shore, vg(near - H * .03f, H, 0f to c("#8FAE5E"), .25f to c("#5E7F3E"), 1f to c("#2E4424")))
    // Levée de la Loire à Vélo et son cycliste
    val ly = near + H * .045f
    fill(poly(0f, ly, W, ly - H * .01f, W, ly + H * .018f, 0f, ly + H * .028f), c("#E8D8B8"))
    run { val k = (t * 3f) % 1f; val x = W * (-.1f + k * 1.2f); val y = ly + H * .006f - k * H * .01f; val s = u * 2.2f; val sp = t * TAU * 30
        for (wx in listOf(-s * .9f, s * .9f)) { drawCircle(c("#2A2E36"), s * .55f, Offset(x + wx, y - s * .5f), style = Stroke(max(1f, u * .25f))); line(x + wx, y - s * .5f, x + wx + cos(sp) * s * .5f, y - s * .5f + sin(sp) * s * .5f, max(1f, u * .12f), c("#2A2E36")) }
        line(x - s * .9f, y - s * .5f, x, y - s * .55f, max(1f, u * .3f), c("#E8412C")); line(x, y - s * .55f, x + s * .9f, y - s * .5f, max(1f, u * .3f), c("#E8412C")); line(x - s * .2f, y - s * 1.2f, x, y - s * .55f, max(1f, u * .3f), c("#E8412C"))
        line(x - s * .2f, y - s * 1.2f, x + s * .5f, y - s * 2.1f, max(1f, u * .55f), c("#2E6FD8")); disc(x + s * .65f, y - s * 2.5f, s * .32f, c("#F2C9A0")); disc(x + s * .62f, y - s * 2.65f, s * .3f, c("#FFB000"))
        line(x + s * .5f, y - s * 2.1f, x + s * .8f, y - s * 1.4f, max(1f, u * .3f), c("#2E6FD8")); line(x - s * .2f, y - s * 1.2f, x + s * .2f, y - s * .5f + sin(sp) * s * .3f, max(1f, u * .35f), c("#1E2A44")) }
    // Herbes et roseaux au premier plan, qui ondulent
    val g = Mul(9)
    for (i in 0 until 140) { val x = g() * W; val yb = H * (.9f + g() * .1f); val h = u * (4f + g() * 9f); val sw = sin(t * TAU * 2 + x * .01f) * u * 1.2f
        line(x, yb, x + sw, yb - h, max(1f, u * (.2f + g() * .2f)), mix(c("#3E5A2A"), c("#B7C77A"), g() * .7f)) }
    for (i in 0 until 7) { val x = W * (.05f + g() * .3f); val yb = H * (.92f + g() * .05f); val h = u * (10f + g() * 6f); val sw = sin(t * TAU * 2 + i) * u
        line(x, yb, x + sw, yb - h, max(1f, u * .3f), c("#4A5E30")); drawOval(c("#6B4A2A"), Offset(x + sw - u * .5f, yb - h - u * 1.2f), Size(u * 1f, u * 3.4f)) }
    // Peupliers à gauche, qui encadrent la scène
    for ((px, ph) in listOf(.04f to .5f, .14f to .44f)) { val base = near + H * .03f; val top = base - H * ph
        rect(W * px - u * .5f, top + H * ph * .3f, u * 1f, H * ph * .7f, c("#4A4034"))
        crown(W * px, top + H * ph * .38f, u * (5f + ph * 4f), H * ph * .4f, (px * 100).toInt(), c("#9DBA6A"), c("#5E7E3E"), c("#2E4424"), -.6f, -.8f) }
    if (d == null) return
    // Lumière de l'heure : voile du crépuscule ou de l'aube, puis la nuit (lune, étoiles, fenêtres et château illuminés)
    if (d.dusk > .01f) drawRect(vg(0f, H, 0f to c("#5A2E8A", .18f * d.dusk), .5f to c("#FF7A3A", .16f * d.dusk), 1f to c("#FFB070", .06f * d.dusk)))
    if (d.dawn > .01f) drawRect(vg(0f, H, 0f to c("#7F8FE0", .12f * d.dawn), .5f to c("#FFB0C0", .14f * d.dawn), 1f to Color.Transparent))
    if (d.night > .01f) {
        val n = d.night
        drawRect(vg(bank - H * .16f, bank - H * .04f, 0f to c("#1A2550", 0f), 1f to c("#1A2550", .82f * n)), topLeft = Offset(0f, bank - H * .16f), size = Size(W, H), blendMode = BlendMode.Multiply)
        starField(52, 160, bank - H * .1f, c("#FFFFFF", .85f * n), t, 3)
        val mx = W * .25f; val my = H * .12f
        glow(mx, my, S * .55f, c("#BFD6FF", .25f * n), BlendMode.Screen); disc(mx, my, S * .045f, c("#F4F7FF", n)); disc(mx + S * .016f, my - S * .01f, S * .04f, c("#D6E0F2", .45f * n))
        loireTown(town, n, lightsOnly = true)
        clipRect(0f, town, W, near) { scale(1f, -.7f, Offset(0f, town)) { loireTown(town, .35f * n, lightsOnly = true) } }
        // Réverbères le long de la levée
        for (k in 0 until 6) { val lx = W * (.1f + k * .17f); val ly2 = near + H * .045f - (lx / W) * H * .01f
            line(lx, ly2, lx, ly2 - u * 6f, max(1f, u * .3f), c("#1A1A22", n)); disc(lx, ly2 - u * 6f, u * .6f, c("#FFE2A0", n)); glow(lx, ly2 - u * 5.5f, u * 7f, c("#FFC46A", .35f * n), BlendMode.Screen) }
    }
}

/* ------------------------------------------------------------------ Phare dans la nuit */
private fun DrawScope.lighthouse(t: Float, d: Tod? = null) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val hz = H * .58f
    // Nuit par défaut ; à l'heure réelle, ciel et mer de jour, d'aube ou de crépuscule, et phare allumé seulement le soir et la nuit
    val night = d?.night ?: 1f; val lamp = if (d == null) 1f else (d.night + .7f * d.dusk + .4f * d.dawn).coerceIn(0f, 1f)
    if (d == null) sky(0f to c("#060B1E"), .55f to c("#15244A"), 1f to c("#2E4670"), y1 = hz)
    else sky(0f to d.col("#3E7FCC", "#5A6AB0", "#2A2A6A", "#060B1E"), .55f to d.col("#8EC0EC", "#E0A8C0", "#D0708A", "#15244A"), 1f to d.col("#DCEBF7", "#FFD0B0", "#FFA070", "#2E4670"), y1 = hz)
    if (night > .01f) starField(61, 220, hz * .9f, c("#FFFFFF", .85f * night), t, 5)
    val mx = W * .22f; val my = H * .14f
    if (night > .01f) { glow(mx, my, S * .5f, c("#BFD6FF", .25f * night), BlendMode.Screen); disc(mx, my, S * .05f, c("#F4F7FF", night)); disc(mx + S * .018f, my - S * .01f, S * .045f, c("#D6E0F2", .5f * night)) }
    if (d != null && night < .99f) { val sx = W * (.15f + .7f * d.sun); val sy = hz - (hz - H * .1f) * sin(d.sun * 3.1416f) - H * .01f; val a = 1f - night
        glow(sx, sy, S * .9f, c("#FFE8C0", .5f * a), BlendMode.Screen); disc(sx, sy, S * .045f, c("#FFF8E6", a))
        for (i in 0 until 2) softCloud(((t * .5f + i * .5f) % 1.3f - .15f) * W * 1.1f, H * (.12f + i * .1f), W * .6f, c("#FFFFFF", .5f * a), c("#FFFFFF", .4f * a), 30 + i) }
    // Mer : houle, reflets de lune (ou de soleil)
    if (d == null) drawRect(vg(hz, H, 0f to c("#1E3358"), 1f to c("#050A18")), topLeft = Offset(0f, hz), size = Size(W, H - hz))
    else drawRect(vg(hz, H, 0f to d.col("#5E9CD0", "#8A8AC0", "#8A5A8A", "#1E3358"), 1f to d.col("#1E4A80", "#2A3A6A", "#2A2050", "#050A18")), topLeft = Offset(0f, hz), size = Size(W, H - hz))
    val r = Mul(3)
    for (i in 0 until 120) { val v = r(); val y = hz + (H - hz) * v * v; val x = ((r() + t * (.05f + v * .1f)) % 1f) * W; val len = u * (1f + v * 8f)
        line(x, y, x + len, y, max(1f, u * (.15f + v * .3f)), c("#9FB7E0", .12f + v * .18f)) }
    for (i in 0 until 50) { val v = r(); val y = hz + (H - hz) * (.02f + v * .5f); val x = mx + (r() - .5f) * u * (6f + v * 30f); val tw = .5f + .5f * sin(t * TAU * 5 + i)
        line(x - u * 1.5f, y, x + u * 1.5f, y, max(1f, u * .3f), c("#E6EEFF", .5f * tw)) }
    // Falaise et rochers
    // Promontoire rocheux : arête bosselée, face éclairée par la lune, base dans l'écume
    val nzc = Nz(77); val cp = Path(); val cb = hz + H * .1f
    cp.moveTo(W * .4f, cb)
    for (i in 0..40) { val f = i / 40f; val x = W * (.4f + .6f * f); val top = cb + (hz - H * .03f - cb) * sm(0f, .4f, f) + nzc.at(f * 9f, 4) * H * .012f * sm(0f, .2f, f); cp.lineTo(x, top) }
    cp.lineTo(W, cb); cp.close()
    fill(cp, vg(hz - H * .04f, cb, 0f to c("#3A4154"), 1f to c("#141824")))
    clipPath(cp) { for (i in 0 until 9) { val x = W * (.52f + i * .055f); fill(poly(x, hz - H * .03f, x + W * .03f, hz - H * .03f, x - W * .01f, cb, x - W * .04f, cb), c("#5A6680", .18f)) } }
    for (i in 0 until 8) { val y = cb - H * .004f * i; val x0 = W * (.48f + i * .01f) + sin(t * TAU * 2 + i) * u; line(x0, y, x0 + W * (.55f - i * .02f), y, max(1f, u * .4f), c("#DDE8FF", .25f - i * .025f)) }
    // Phare rayé blanc et rouge
    val px = W * .78f; val pb = hz - H * .026f; val ph = H * .2f; val wb = u * 9f; val wt = u * 6f
    for (k in 0 until 5) { val y0 = pb - ph * k / 5f; val y1 = pb - ph * (k + 1) / 5f; val w0 = wb + (wt - wb) * k / 5f; val w1 = wb + (wt - wb) * (k + 1) / 5f
        fill(poly(px - w0 / 2, y0, px + w0 / 2, y0, px + w1 / 2, y1, px - w1 / 2, y1), if (k % 2 == 0) c("#E9EDF4") else c("#C8323A"))
        fill(poly(px + w0 * .1f, y0, px + w0 / 2, y0, px + w1 / 2, y1, px + w1 * .1f, y1), c("#000000", .25f)) }
    val lt = pb - ph; rect(px - wt * .7f, lt - u * 1f, wt * 1.4f, u * 1f, c("#20242E"))
    rect(px - wt * .45f, lt - u * 5f, wt * .9f, u * 4f, mix(c("#8A9AB4"), c("#FFE9A8"), lamp)); fill(poly(px - wt * .6f, lt - u * 5f, px, lt - u * 9f, px + wt * .6f, lt - u * 5f), c("#20242E"))
    // Faisceau tournant
    val ang = t * TAU * 3f; val bx = px; val by = lt - u * 3f; val dir = cos(ang); val len = W * 1.3f * abs(dir) + u * 10f
    val spread = H * .05f
    if (lamp > .01f) {
        fill(poly(bx, by - u, bx + dir * len, by - spread, bx + dir * len, by + spread, bx, by + u), lg(bx, by, bx + dir * len, by, 0f to c("#FFF2C2", .55f * abs(dir) * lamp), 1f to c("#FFF2C2", 0f)))
        glow(bx, by, S * .35f, c("#FFE9A8", .5f * lamp), BlendMode.Screen); glow(bx, by, S * .08f, c("#FFFFFF", .9f * lamp), BlendMode.Screen)
    }
    // Écume au pied de la falaise
}

/* ------------------------------------------------------------------ Vignes d'Anjou au couchant */
private fun DrawScope.vineyard(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val hz = H * .42f
    sky(0f to c("#5A6FA8"), .45f to c("#E8A27A"), .8f to c("#FFD49A"), 1f to c("#FFE8C0"), y1 = hz)
    val sx = W * .3f; val sy = hz - H * .03f
    glow(sx, sy, S * 1.2f, c("#FFB86B", .5f), BlendMode.Screen); glow(sx, sy, S * .2f, c("#FFF0D0", .95f), BlendMode.Screen); disc(sx, sy, S * .06f, c("#FFF6E2"))
    for (i in 0 until 3) softCloud(((t * .5f + i * .37f) % 1.3f - .15f) * W * 1.1f, H * (.12f + i * .07f), W * .6f, c("#F7B48E", .35f), c("#FFE2C2", .35f), 70 + i)
    val nz = Nz(41)
    ridgeFill(ridgePts(hz, H * .04f, 60) { x -> .5f + .5f * nz.at(x * 3f, 3) }, c("#B07C8E"), hz + 2f)
    ridgeFill(ridgePts(hz + H * .02f, H * .03f, 60) { x -> .5f + .5f * nz.at(x * 4f + 5f, 3) }, c("#8E6A78"), hz + H * .03f)
    // Chai de tuffeau et son cyprès sur la colline
    val cx = W * .72f; val cy = hz + H * .02f
    rect(cx - u * 7f, cy - u * 6f, u * 14f, u * 6f, c("#F1E3CC")); fill(poly(cx - u * 8f, cy - u * 6f, cx, cy - u * 11f, cx + u * 8f, cy - u * 6f), c("#5E6E86"))
    rect(cx - u * 1.2f, cy - u * 3.6f, u * 2.4f, u * 3.6f, c("#6A4A30")); rect(cx + u * 3f, cy - u * 4.5f, u * 1.6f, u * 1.6f, c("#FFD27A"))
    fill(poly(cx + u * 11f, cy, cx + u * 12.5f, cy - u * 14f, cx + u * 14f, cy), c("#3E5A3A"))
    // Rangs de vigne en perspective, vers le soleil
    val base = hz + H * .03f; val vx = sx
    drawRect(vg(base, H, 0f to c("#B9925A"), 1f to c("#5A3E22")), topLeft = Offset(0f, base), size = Size(W, H - base))
    for (i in -14..14) {
        val xb = vx + i * W * .16f; val xt = vx + i * W * .006f
        drawLine(vg(base, H, 0f to c("#4E6A2E", .6f), 1f to c("#2E4A1A")), Offset(xt, base), Offset(xb, H), strokeWidth = u * 4.5f)
        drawLine(vg(base, H, 0f to c("#A9C26A", .5f), 1f to c("#7FA04A", .7f)), Offset(xt - u * .2f, base), Offset(xb - u * 1.2f, H), strokeWidth = u * 1.6f)
        // Grappes près de nous
        val r = Mul(i + 30)
        for (k in 0 until 5) { val v = .55f + r() * .45f; val x = xt + (xb - xt) * v + u * 1.5f; val y = base + (H - base) * v; disc(x, y, u * (.6f + v * 1.6f), c("#5A2A4A", .9f)); disc(x - u * .3f, y - u * .3f, u * (.25f + v * .6f), c("#9A5A86", .7f)) }
    }
    // Poteaux et fils de palissage
    for (i in -14..14) { val xb = vx + i * W * .16f; val xt = vx + i * W * .006f; for (k in 1..4) { val v = (k / 4f).pow(2); val x = xt + (xb - xt) * v; val y = base + (H - base) * v; line(x, y, x, y - u * (2f + v * 10f), max(1f, u * (.2f + v * .5f)), c("#6A4A30")) } }
    // Lumière rasante et poussière dorée
    drawRect(lg(sx, sy, W, H, 0f to c("#FFD49A", .35f), 1f to c("#FFD49A", 0f)), topLeft = Offset(0f, base), size = Size(W, H - base))
    val g = Mul(8); for (i in 0 until 40) { val x = ((g() + t * .3f) % 1f) * W; val y = base + g() * (H - base) * .6f - sin(t * TAU + i) * u * 2f; disc(x, y, u * (.2f + g() * .3f), c("#FFF0C8", .4f + .4f * sin(t * TAU * 3 + i))) }
}

/* ------------------------------------------------------------------ Montgolfières au lever du jour */
private fun DrawScope.balloon(x: Float, y: Float, r: Float, cols: List<Color>) {
    val env = Path().apply { moveTo(x, y + r * 1.25f); cubicTo(x - r * 1.35f, y + r * .2f, x - r * 1.05f, y - r * 1.1f, x, y - r * 1.1f); cubicTo(x + r * 1.05f, y - r * 1.1f, x + r * 1.35f, y + r * .2f, x, y + r * 1.25f); close() }
    clipPath(env) {
        val n = cols.size * 2
        for (i in 0 until n) { val a0 = -1f + 2f * i / n; val a1 = -1f + 2f * (i + 1) / n
            fill(poly(x + r * a0 * .35f, y - r * 1.2f, x + r * a1 * .35f, y - r * 1.2f, x + r * a1 * 1.5f, y + r * .1f, x + r * a1 * .2f, y + r * 1.3f, x + r * a0 * .2f, y + r * 1.3f, x + r * a0 * 1.5f, y + r * .1f), cols[i % cols.size]) }
        drawRect(Brush.horizontalGradient(0f to Color.White.copy(alpha = .35f), .45f to Color.Transparent, 1f to Color.Black.copy(alpha = .3f), startX = x - r, endX = x + r), Offset(x - r * 1.4f, y - r * 1.2f), Size(r * 2.8f, r * 2.6f))
    }
    line(x - r * .2f, y + r * 1.25f, x - r * .16f, y + r * 1.55f, max(1f, r * .03f), c("#4A3A2A")); line(x + r * .2f, y + r * 1.25f, x + r * .16f, y + r * 1.55f, max(1f, r * .03f), c("#4A3A2A"))
    drawRoundRect(c("#8A6A44"), Offset(x - r * .18f, y + r * 1.55f), Size(r * .36f, r * .26f), CornerRadius(r * .05f))
}
private fun DrawScope.balloons(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    sky(0f to c("#7FB2E6"), .45f to c("#CFE2F2"), .75f to c("#FFE2C8"), 1f to c("#FFD2B0"))
    val sx = W * .5f; val sy = H * .72f; glow(sx, sy, S * 1.2f, c("#FFD8A8", .5f), BlendMode.Screen); glow(sx, sy, S * .2f, c("#FFF4E0", .9f), BlendMode.Screen)
    val nz = Nz(52)
    ridgeFill(ridgePts(H * .74f, H * .06f, 60) { x -> .5f + .5f * nz.at(x * 3f, 3) }, c("#B9A6C6", .9f))
    mist(H * .76f, H * .06f, c("#FFF1E6", .7f))
    ridgeFill(ridgePts(H * .82f, H * .05f, 60) { x -> .5f + .5f * nz.at(x * 4f + 7f, 3) }, c("#8C86A8"))
    mist(H * .84f, H * .05f, c("#FFF1E6", .6f))
    ridgeFill(ridgePts(H * .9f, H * .04f, 60) { x -> .5f + .5f * nz.at(x * 5f + 2f, 3) }, vg(H * .86f, H, 0f to c("#6E7A6A"), 1f to c("#3E4A3E")))
    conifers(30, 4, u * 4f, u * 9f, c("#2E3A30"), { x -> H * .93f + sin(x / W * 9f) * u * 2f })
    val pal = listOf(listOf(c("#FF5A5F"), c("#FFD166")), listOf(c("#3DB2FF"), c("#FFFFFF")), listOf(c("#7B5CFF"), c("#FF7AD9")), listOf(c("#2EC27E"), c("#F6F5F4")), listOf(c("#FF8A3D"), c("#7A3DFF"), c("#FFE45C")))
    val spots = listOf(floatArrayOf(.25f, .32f, 10f), floatArrayOf(.7f, .22f, 7f), floatArrayOf(.52f, .48f, 13f), floatArrayOf(.85f, .5f, 5f), floatArrayOf(.12f, .6f, 4.5f), floatArrayOf(.4f, .14f, 4f))
    spots.forEachIndexed { i, q -> val bob = sin(t * TAU + i * 1.3f) * u * 1.5f; val drift = ((t * .5f + i * .2f) % 1f - .5f) * u * 6f
        balloon(W * q[0] + drift, H * q[1] + bob, u * q[2], pal[i % pal.size]) }
    // Oiseaux
    for (i in 0 until 3) { val k = (t * 2f + i * .3f) % 1f; val x = W * (k * 1.2f - .1f); val y = H * (.1f + i * .05f); val s = u * 1.2f
        drawPath(Path().apply { moveTo(x - s, y - s * .4f); quadraticTo(x - s * .4f, y - s * .2f, x, y + s * .2f); quadraticTo(x + s * .4f, y - s * .2f, x + s, y - s * .4f) }, c("#4A4A5A", .7f), style = Stroke(max(1f, u * .3f))) }
}

/* ------------------------------------------------------------------ Méduses lumineuses */
private fun DrawScope.jellyfish(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    sky(0f to c("#0E2A5A"), .5f to c("#071838"), 1f to c("#020818"))
    rays(W * .5f, -H * .05f, H * .8f, 7, 90f, 50f, c("#6FC3FF", .08f), 12, t)
    val r = Mul(14)
    for (i in 0 until 90) { val x = r() * W; val y = ((r() - t * (.05f + r() * .1f)) % 1f + 1f) % 1f * H; disc(x, y, u * (.1f + r() * .25f), c("#BFE6FF", .3f + r() * .3f)) }
    val cols = listOf(c("#FF7AD9"), c("#7FE7FF"), c("#B48CFF"), c("#FFB86B"), c("#7CFFB2"))
    val jel = listOf(floatArrayOf(.3f, .3f, 12f), floatArrayOf(.72f, .2f, 8f), floatArrayOf(.6f, .55f, 15f), floatArrayOf(.18f, .7f, 7f), floatArrayOf(.85f, .75f, 9f), floatArrayOf(.45f, .85f, 5f))
    jel.forEachIndexed { i, q ->
        val pulse = sin(t * TAU * 4 + i * 1.7f); val col = cols[i % cols.size]; val rr = u * q[2]
        val x = W * q[0] + sin(t * TAU + i) * u * 3f; val y = ((q[1] - t * .25f * (1f + i * .1f)) % 1.1f + 1.1f) % 1.1f * H * 1.05f
        glow(x, y, rr * 3f, col.a(.25f), BlendMode.Screen)
        // Tentacules ondulantes
        for (k in 0 until 7) { val tx = x + (k - 3) * rr * .22f; val p = Path(); p.moveTo(tx, y + rr * .2f)
            for (j in 1..10) { val f = j / 10f; p.lineTo(tx + sin(t * TAU * 3 + k + f * 5f) * rr * .25f * f, y + rr * .2f + rr * 2.6f * f) }
            drawPath(p, col.a(.45f), style = Stroke(max(1f, rr * .05f))) }
        val w = rr * (1f + .08f * pulse); val h = rr * (.75f - .08f * pulse)
        val bell = Path().apply { moveTo(x - w, y + h * .3f); cubicTo(x - w, y - h * 1.2f, x + w, y - h * 1.2f, x + w, y + h * .3f); quadraticTo(x, y + h * .05f, x - w, y + h * .3f); close() }
        drawPath(bell, Brush.radialGradient(0f to Color.White.copy(alpha = .8f), .4f to col.a(.6f), 1f to col.a(.15f), center = Offset(x - w * .2f, y - h * .5f), radius = w * 1.4f))
        drawPath(bell, col.a(.8f), style = Stroke(max(1f, rr * .05f)))
    }
}

/* ------------------------------------------------------------------ Bambouseraie */
private fun DrawScope.bamboo(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    sky(0f to c("#E6F4D8"), .5f to c("#BFE0A6"), 1f to c("#7FB86A"))
    glow(W * .65f, H * .12f, S * 1.1f, c("#FFF8D6", .7f), BlendMode.Screen)
    rays(W * .65f, -H * .05f, H * 1.1f, 8, 100f, 40f, c("#FFFBE6", .14f), 22, t)
    // Trois plans de tiges : lointain pâle, moyen, proche foncé
    val layers = listOf(Triple(16, .35f, c("#A9CF8E")), Triple(9, .65f, c("#6FA65A")), Triple(5, 1f, c("#3E7A3A")))
    layers.forEachIndexed { li, (n, depth, col) ->
        val r = Mul(100 + li)
        for (i in 0 until n) {
            val x = W * (i + r() * .8f) / n; val w = u * (1.2f + depth * 3.2f) * (.8f + r() * .4f); val sway = sin(t * TAU + i * .7f + li) * u * depth * 1.2f
            drawLine(Brush.horizontalGradient(0f to mix(col, Color.White, .25f), .45f to col, 1f to mix(col, Color.Black, .25f), startX = x - w / 2, endX = x + w / 2), Offset(x, H), Offset(x + sway, -u * 5f), strokeWidth = w)
            // Nœuds
            var y = H - r() * u * 10f; while (y > 0f) { val k = 1f - y / H; line(x + sway * k - w * .55f, y, x + sway * k + w * .55f, y, max(1f, w * .16f), mix(col, Color.Black, .3f)); y -= u * (9f + depth * 9f) * (.8f + r() * .4f) }
            // Feuilles fines
            for (k in 0 until (1 + depth * 3).toInt()) { val yy = H * (.05f + r() * .45f); val kk = 1f - yy / H; val dir = if (r() < .5f) -1f else 1f
                for (j in 0 until 3) leaf(x + sway * kk, yy + j * u * 1.5f, u * (4f + depth * 4f), dir * (25f + j * 18f) + sin(t * TAU * 2 + i) * 6f, mix(col, c("#2E5A2A"), .2f), .22f) }
        }
        if (li == 0) mist(H * .5f, H * .6f, c("#EAF6DE", .45f))
    }
    // Feuilles qui tombent
    val g = Mul(7)
    for (i in 0 until 10) { val x = (g() * W + sin(t * TAU * 2 + i) * u * 6f); val y = ((g() + t * (.3f + g() * .2f)) % 1f) * H; leaf(x, y, u * 3.4f, t * 720f + i * 40f, c("#9BC46A", .9f), .3f) }
    drawRect(vg(H * .85f, H, 0f to c("#2E5A2A", 0f), 1f to c("#1E3A1E", .6f)), topLeft = Offset(0f, H * .85f), size = Size(W, H * .15f))
}

/* ------------------------------------------------------------------ Train de nuit sur le viaduc */
private fun DrawScope.nighttrain(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    sky(0f to c("#0A0F2A"), .6f to c("#1E2A5A"), 1f to c("#3A3A6A"))
    starField(91, 200, H * .55f, c("#FFFFFF", .8f), t, 4)
    val mx = W * .72f; val my = H * .18f; glow(mx, my, S * .6f, c("#FFE7B0", .2f), BlendMode.Screen); disc(mx, my, S * .07f, c("#FFF4D6")); disc(mx - S * .025f, my - S * .01f, S * .06f, c("#1E2A5A", .9f))
    val nz = Nz(13)
    ridgeFill(ridgePts(H * .6f, H * .08f, 60) { x -> .5f + .5f * nz.ridged(x * 3f, 3) }, c("#26305A"))
    // Viaduc : tablier et arches
    val deck = H * .56f; val dh = u * 3f
    rect(0f, deck, W, dh, c("#3E3A54"))
    val n = 6; val span = W / (n - 1.5f)
    val arches = Path().apply { fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd; addRect(androidx.compose.ui.geometry.Rect(0f, deck + dh, W, H * .86f))
        for (i in 0 until n) { val cx = i * span; val ar = span * .38f; addOval(androidx.compose.ui.geometry.Rect(cx - ar, deck + dh + span * .05f, cx + ar, deck + dh + span * .05f + ar * 2f)); addRect(androidx.compose.ui.geometry.Rect(cx - ar, deck + dh + span * .05f + ar, cx + ar, H * .86f)) } }
    fill(arches, vg(deck, H * .86f, 0f to c("#4A4664"), 1f to c("#1E1C30")))
    // Vallée et rivière sous le viaduc
    ridgeFill(ridgePts(H * .84f, H * .03f, 50) { x -> .5f + .5f * nz.at(x * 4f + 3f, 3) }, c("#141A34"))
    rect(0f, H * .86f, W, H * .14f, c("#0C1026")); for (i in 0 until 14) { val y = H * (.88f + i * .008f); val x = W * .65f + sin(i * 2.1f) * u * 4f; line(x - u * 6f, y, x + u * 6f, y, max(1f, u * .3f), c("#FFE7B0", .25f - i * .015f)) }
    conifers(26, 8, u * 6f, u * 12f, c("#0E1428"), { H * .86f + u * 2f })
    // Le train : locomotive et voitures aux fenêtres chaudes, qui traverse
    val k = (t * 2f) % 1f; val tl = W * 2.4f; val x0 = W * 1.1f - k * (tl + W * 1.2f)
    val ty = deck - u * 6.5f; val th = u * 6.5f
    for (car in 0 until 7) { val cx = x0 + car * u * 33f; val cw = u * 31f
        if (cx > W || cx + cw < 0f) continue
        drawRoundRect(vg(ty, deck, 0f to c("#2A3A6A"), 1f to c("#141C3A")), Offset(cx, ty), Size(cw, th), CornerRadius(u * 1.6f))
        if (car == 0) { fill(poly(cx, ty + th, cx - u * 4f, ty + th, cx - u * 1f, ty + th * .3f, cx + u * 2f, ty), c("#2A3A6A")); glow(cx - u * 3f, ty + th * .65f, u * 10f, c("#FFF0C0", .6f), BlendMode.Screen); disc(cx - u * 2.6f, ty + th * .65f, u * .9f, c("#FFFBEA")) }
        else for (w in 0 until 6) { val wx = cx + u * (2.5f + w * 4.8f); drawRoundRect(c("#FFD27A"), Offset(wx, ty + th * .25f), Size(u * 3.2f, th * .35f), CornerRadius(u * .5f)); glow(wx + u * 1.6f, ty + th * .45f, u * 4f, c("#FFC46A", .25f), BlendMode.Screen) }
        rect(cx, deck - u * .8f, cw, u * .8f, c("#0A0E1C"))
    }
    // Reflets du train sur la rivière
    for (i in 0 until 10) { val x = ((x0 + i * u * 22f) % (W * 2f)); if (x in 0f..W) line(x, H * (.9f + i * .006f), x + u * 5f, H * (.9f + i * .006f), max(1f, u * .3f), c("#FFD27A", .25f)) }
}

/* ------------------------------------------------------------------ Toits de Paris */

/** Profil de la tour Eiffel : demi-largeur (en fraction de la hauteur) selon la hauteur relative (0 au sol, 1 au sommet). */
private val EIF_H = floatArrayOf(0f, .06f, .19f, .3f, .39f, .5f, .62f, .75f, .86f, .92f, 1f)
private val EIF_W = floatArrayOf(.208f, .175f, .118f, .088f, .066f, .052f, .041f, .031f, .022f, .017f, .004f)

/** Tour Eiffel : quatre piliers et grande arche, trois étages, fût effilé en treillis, antenne. [gap] : couleur vue à travers le treillis. */
private fun DrawScope.eiffel(cx: Float, base: Float, eh: Float, col: Color, gap: Color, lit: Float, t: Float) {
    fun y(h: Float) = base - eh * h
    fun hw(h: Float) = eh * tab(h, EIF_H, EIF_W)
    fun leg(h: Float) = eh * (.07f - .036f * (h / .39f).coerceAtMost(1f))
    val sw = max(1f, eh * .0022f)
    // Piliers jusqu'au deuxième étage : deux montants qui se rejoignent
    for (side in listOf(-1f, 1f)) {
        val n = 26; val pth = Path()
        for (i in 0..n) { val h = .39f * i / n; val x = cx + side * hw(h); if (i == 0) pth.moveTo(x, y(h)) else pth.lineTo(x, y(h)) }
        for (i in n downTo 0) { val h = .39f * i / n; pth.lineTo(cx + side * (hw(h) - leg(h)), y(h)) }
        pth.close(); fill(pth, col)
        // Treillis : croix de Saint-André entre les arêtes du pilier
        var h = .01f
        while (h < .37f) { val h2 = h + .035f
            line(cx + side * hw(h), y(h), cx + side * (hw(h2) - leg(h2)), y(h2), sw, gap); line(cx + side * (hw(h) - leg(h)), y(h), cx + side * hw(h2), y(h2), sw, gap); h = h2 }
    }
    // Grande arche décorative sous le premier étage
    val ax = hw(.02f) - leg(.02f)
    drawPath(Path().apply { moveTo(cx - ax, y(.02f)); quadraticTo(cx, y(.3f), cx + ax, y(.02f)) }, col, style = Stroke(eh * .011f))
    // Fût au-dessus du deuxième étage
    val n = 30; val shaft = Path()
    for (i in 0..n) { val h = .39f + .53f * i / n; if (i == 0) shaft.moveTo(cx - hw(h), y(h)) else shaft.lineTo(cx - hw(h), y(h)) }
    for (i in n downTo 0) { val h = .39f + .53f * i / n; shaft.lineTo(cx + hw(h), y(h)) }
    shaft.close(); fill(shaft, col)
    var h = .4f
    while (h < .9f) { val h2 = h + .045f; line(cx - hw(h), y(h), cx + hw(h2), y(h2), sw, gap); line(cx + hw(h), y(h), cx - hw(h2), y(h2), sw, gap); h = h2 }
    line(cx, y(.39f), cx, y(.92f), sw, gap.a(gap.alpha * .6f))
    // Étages : plateformes et galeries
    for ((ph, th, ext) in listOf(Triple(.19f, .022f, .014f), Triple(.39f, .014f, .008f))) {
        rect(cx - hw(ph) - eh * ext, y(ph) - eh * th, (hw(ph) + eh * ext) * 2, eh * th, col)
        var gx = cx - hw(ph) - eh * ext + eh * .006f
        while (gx < cx + hw(ph) + eh * ext - eh * .006f) { rect(gx, y(ph) - eh * th * .7f, eh * .004f, eh * th * .45f, gap); gx += eh * .011f }
    }
    // Sommet : campanile, phare et antenne
    rect(cx - hw(.9f) * 1.5f, y(.935f), hw(.9f) * 3f, eh * .02f, col)
    fill(poly(cx - hw(.93f) * 1.1f, y(.935f), cx, y(.975f), cx + hw(.93f) * 1.1f, y(.935f)), col)
    line(cx, y(.97f), cx, y(1.04f), max(1f, eh * .004f), col)
    // Illumination dorée le soir, phare et scintillement
    if (lit > .01f) {
        glow(cx, y(.35f), eh * .5f, c("#FFB04A", .18f * lit), BlendMode.Screen)
        val r = Mul(88)
        for (i in 0 until 70) { val hh = r() * .95f; val x = cx + (r() * 2f - 1f) * hw(hh) * .9f; val tw = sin(t * TAU * 40 + i * 2.3f)
            if (tw > .6f) disc(x, y(hh), max(1f, eh * .003f), c("#FFFFFF", lit * (tw - .6f) * 2.5f)) }
        val beam = t * TAU * 6f
        for (k in 0..1) { val a = beam + k * 3.1416f; val bx = cos(a)
            drawPath(Path().apply { moveTo(cx, y(1f)); lineTo(cx + bx * eh * .9f, y(1f) - eh * .025f); lineTo(cx + bx * eh * .9f, y(1f) + eh * .025f); close() }, c("#FFF2C8", .05f * lit * abs(bx)), blendMode = BlendMode.Screen) }
        glow(cx, y(1f), eh * .04f, c("#FFF2C8", .8f * lit), BlendMode.Screen)
    }
}

/** Immeuble haussmannien vu de face : pierre de taille, balcons filants, corniche, combles en zinc et lucarnes, souche de cheminée. */
private fun DrawScope.haussmann(x: Float, w: Float, cornice: Float, bottom: Float, u: Float, seed: Int, night: Float, light: Float) {
    val r = Mul(seed)
    // La nuit, pierre et zinc passent au bleu (les fenêtres allumées restent chaudes)
    val nt = { col: Color -> mix(col, c("#232A44"), .62f * night) }
    val stone = nt(mix(c("#E9DCC4"), c("#D8C8AA"), r())); val stoneSh = mix(stone, c("#8E7E68"), .35f)
    val zinc = nt(c("#7D8AA2")); val zincLit = nt(c("#A8B4C8")); val iron = c("#1E1E26")
    val fl = u * 6.2f
    // Façade et ombre portée côté droit (lumière du couchant à gauche)
    rect(x, cornice, w, bottom - cornice, stone)
    drawRect(lg(x, 0f, x + w, 0f, 0f to c("#FFF4DE", .12f * light), .6f to Color.Transparent, 1f to c("#3A2E2A", .18f)), Offset(x, cornice), Size(w, bottom - cornice))
    // Refends (joints horizontaux) des étages bas
    for (k in 0 until 3) line(x, cornice + fl * (4f + k * .25f), x + w, cornice + fl * (4f + k * .25f), max(1f, u * .1f), stoneSh.a(.35f))
    // Fenêtres : hautes portes-fenêtres à encadrement mouluré, linteaux sculptés aux étages nobles
    val cols = max(2, (w / (u * 5.4f)).toInt()); val gapX = w / cols
    for (f in 0 until 5) { val fy = cornice + u * 1.6f + f * fl
        for (k in 0 until cols) { val wx = x + gapX * (k + .5f) - u * 1.05f
            rect(wx - u * .35f, fy - u * .3f, u * 2.8f, u * 4.3f, mix(stone, c("#FFFFFF"), .15f))
            val litW = r() < (.25f + .45f * night)
            drawRect(vg(fy, fy + u * 3.7f, 0f to if (litW) c("#FFE2A0") else c("#3A4258"), 1f to if (litW) c("#F2B05A") else c("#1E2232")), Offset(wx, fy), Size(u * 2.1f, u * 3.7f))
            if (litW && night > .05f) glow(wx + u * 1.05f, fy + u * 1.8f, u * 4f, c("#FFC46A", .25f * night), BlendMode.Screen)
            line(wx + u * 1.05f, fy, wx + u * 1.05f, fy + u * 3.7f, max(1f, u * .15f), mix(stone, c("#FFFFFF"), .2f))
            line(wx, fy + u * 1.4f, wx + u * 2.1f, fy + u * 1.4f, max(1f, u * .12f), mix(stone, c("#FFFFFF"), .2f))
            if (f == 3) fill(poly(wx - u * .6f, fy - u * .4f, wx + u * 1.05f, fy - u * 1.5f, wx + u * 2.7f, fy - u * .4f), stoneSh.a(.8f))
            // Garde-corps individuels (étages sans balcon filant)
            if (f == 1 || f == 2) for (b in 0 until 6) line(wx + b * u * .42f, fy + u * 2.6f, wx + b * u * .42f, fy + u * 3.7f, max(1f, u * .1f), iron)
        }
        // Balcons filants au 5e (f = 0) et au 2e (f = 3) : dalle, rambarde et volutes de fer forgé
        if (f == 0 || f == 3) { val by = fy + u * 3.8f
            rect(x, by, w, u * .55f, stoneSh); rect(x, by - u * 1.5f, w, u * .22f, iron)
            var bx = x; while (bx < x + w) { line(bx, by - u * 1.5f, bx, by, max(1f, u * .1f), iron); drawCircle(iron, u * .32f, Offset(bx + u * .45f, by - u * .75f), style = Stroke(max(1f, u * .08f))); bx += u * .9f }
            drawRect(c("#000000", .12f), Offset(x, by + u * .55f), Size(w, u * .6f)) }
    }
    // Corniche saillante
    rect(x - u * .6f, cornice - u * 1.2f, w + u * 1.2f, u * 1.3f, mix(stone, c("#FFFFFF"), .2f)); rect(x - u * .6f, cornice + u * .1f, w + u * 1.2f, u * .5f, c("#000000", .15f))
    // Combles à la Mansart : brisis presque vertical, terrasson en pente douce, lucarnes
    val brisis = u * 5.2f; val terr = u * 2.2f
    fill(poly(x - u * .4f, cornice - u * 1.2f, x + u * .8f, cornice - u * 1.2f - brisis, x + w - u * .8f, cornice - u * 1.2f - brisis, x + w + u * .4f, cornice - u * 1.2f), zinc)
    fill(poly(x + u * .8f, cornice - u * 1.2f - brisis, x + u * 3f, cornice - u * 1.2f - brisis - terr, x + w - u * 3f, cornice - u * 1.2f - brisis - terr, x + w - u * .8f, cornice - u * 1.2f - brisis), zincLit)
    var sx = x + u * 1.2f; while (sx < x + w - u) { line(sx, cornice - u * 1.2f, sx + u * .2f, cornice - u * 1.2f - brisis, max(1f, u * .08f), c("#5E6A80", .6f)); sx += u * 1.1f }
    for (k in 0 until cols) { val lx = x + gapX * (k + .5f) - u * 1.1f; val ly = cornice - u * 1.2f - brisis + u * .6f
        val litL = r() < (.2f + .5f * night)
        rect(lx - u * .4f, ly, u * 3f, u * 3.8f, if (k % 2 == 0) mix(stone, c("#FFFFFF"), .1f) else zincLit)
        if (k % 2 == 0) fill(Path().apply { moveTo(lx - u * .6f, ly); quadraticTo(lx + u * 1.1f, ly - u * 2f, lx + u * 2.8f, ly); close() }, mix(stone, c("#FFFFFF"), .1f))
        else fill(poly(lx - u * .6f, ly, lx + u * 1.1f, ly - u * 1.6f, lx + u * 2.8f, ly), zinc)
        rect(lx, ly + u * .5f, u * 2.2f, u * 3f, if (litL) c("#FFD27A") else c("#2E3448"))
        if (litL && night > .05f) glow(lx + u * 1.1f, ly + u * 2f, u * 3.5f, c("#FFB04A", .3f * night), BlendMode.Screen) }
    // Souche de cheminée sur le mur mitoyen, rangée de boisseaux en terre cuite
    val cxs = x + w - u * 2f; val ctop = cornice - u * 1.2f - brisis - terr - u * 2.5f
    rect(cxs - u * 1.6f, ctop, u * 3.6f, cornice - ctop - u * 2f, mix(stone, c("#B8A488"), .5f)); rect(cxs - u * 1.9f, ctop, u * 4.2f, u * .6f, stoneSh)
    for (p in 0 until 4) { val px = cxs - u * 1.4f + p * u * .9f; fill(poly(px, ctop, px + u * .15f, ctop - u * 1.6f, px + u * .65f, ctop - u * 1.6f, px + u * .8f, ctop), c("#C2683E")); rect(px + u * .1f, ctop - u * 1.8f, u * .6f, u * .25f, c("#A85432")) }
}

private fun DrawScope.rooftops(t: Float, d: Tod? = null) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val night = d?.night ?: .1f; val hz = H * .6f
    // Ciel du soir par défaut ; à l'heure réelle, aube, jour, couchant ou nuit
    if (d == null) sky(0f to c("#34407A"), .35f to c("#8A6A9E"), .7f to c("#F0A27E"), 1f to c("#FFD6A6"), y1 = hz)
    else sky(0f to d.col("#3F86D6", "#5E6EB0", "#34407A", "#060A1E"), .35f to d.col("#8EC0EC", "#B89AC8", "#9A6A9E", "#121A40"),
        .7f to d.col("#D6E8F6", "#FFC2B0", "#F0A27E", "#232E58"), 1f to d.col("#EEF4F8", "#FFDCC4", "#FFD6A6", "#2E3A62"), y1 = hz)
    val sun = d?.sun ?: .93f; val sunA = if (d == null) 1f else (1f - d.night).coerceIn(0f, 1f)
    val sx = W * (.85f - .7f * sun); val sy = if (d == null) H * .5f else hz - H * .04f - (hz - H * .14f) * sin(sun * 3.1416f)
    if (sunA > .01f) { glow(sx, sy, S * 1.2f, c("#FFC08A", .45f * sunA), BlendMode.Screen); glow(sx, sy, S * .18f, c("#FFF0D0", .9f * sunA), BlendMode.Screen); disc(sx, sy, S * .035f, c("#FFF8EA", sunA)) }
    if (night > .2f) { starField(19, 120, hz - H * .15f, c("#FFFFFF", .8f * night), t, 2); val mx = W * .82f; val my = H * .1f; glow(mx, my, S * .45f, c("#BFD6FF", .22f * night), BlendMode.Screen); disc(mx, my, S * .035f, c("#F4F7FF", night)) }
    for (i in 0 until 3) { val dx = ((t * (.4f + i * .1f) + i * .37f) % 1.3f - .15f) * W * 1.1f; softCloud(dx, H * (.1f + i * .08f), W * (.55f + i * .1f), c("#FFE6D6", (.35f - i * .05f) * (1f - .8f * night)), c("#FFD0B0", .3f * (1f - .8f * night)), 21 + i) }
    val haze = d?.col("#B9C8DC", "#D8B8C8", "#E8A890", "#2A3460") ?: c("#E8A890")
    // Paris au loin : toits et dôme des Invalides dans la brume
    val r = Mul(11); var x = -u * 2f
    while (x < W) { val w = u * (3f + r() * 6f); val h = u * (2.5f + r() * 4f); val col = mix(c("#7A6E92"), haze, .55f)
        rect(x, hz - h, w, h + H * .12f, col); fill(poly(x, hz - h, x + u * .8f, hz - h - u * 1.4f, x + w - u * .8f, hz - h - u * 1.4f, x + w, hz - h), mix(col, haze, .2f))
        if (r() < .35f) rect(x + w * .7f, hz - h - u * 2.4f, u * .9f, u * 1.2f, col)
        if (night > .3f && r() < .5f) disc(x + w * r(), hz - h * r(), u * .25f, c("#FFD27A", .7f * night))
        x += w }
    run { val ix = W * .2f; val iy = hz - u * 5f; val gold = mix(c("#D9A441"), haze, .35f); val st = mix(c("#8E8098"), haze, .5f)
        rect(ix - u * 4f, iy, u * 8f, u * 5f, st); rect(ix - u * 3f, iy - u * 3f, u * 6f, u * 3f, st)
        drawArc(gold, 180f, 180f, true, Offset(ix - u * 3f, iy - u * 6.6f), Size(u * 6f, u * 7f)); rect(ix - u * .5f, iy - u * 8.5f, u * 1f, u * 2f, gold); line(ix, iy - u * 8.5f, ix, iy - u * 10.5f, max(1f, u * .2f), gold) }
    // Tour Eiffel, voilée par la distance
    val lit = sm(.35f, .7f, night)
    val towerCol = mix(d?.col("#6A5A52", "#6A5068", "#4A3446", "#2A2236")?.let { mix(it, haze, .3f) } ?: c("#4E3A4E"), c("#E0A048"), .75f * lit)
    eiffel(W * .64f, hz + u * 2f, H * .43f, towerCol, mix(haze, c("#3A2A2A"), .5f * lit).a(.55f), lit, t)
    mist(hz - u * 1f, u * 6f, haze.a(.35f))
    // Plan intermédiaire : derniers étages et combles des rues voisines, cheminées et boisseaux, dans la brume du soir
    run { val rr = Mul(29); var mx = -u * 3f; val mb = H * .675f
        while (mx < W) { val w = u * (9f + rr() * 8f); val top = mb - u * (2f + rr() * 3f)
            val st = mix(mix(c("#D9C8AC"), c("#232A44"), .6f * night), haze, .38f); val zc = mix(mix(c("#7D8AA2"), c("#232A44"), .6f * night), haze, .3f)
            rect(mx, top, w, H * .72f - top, st)
            fill(poly(mx - u * .3f, top, mx + u * .6f, top - u * 3f, mx + w - u * .6f, top - u * 3f, mx + w + u * .3f, top), zc)
            for (k in 0 until (w / (u * 3f)).toInt()) { val wx = mx + u * (1.2f + k * 3f); val on = rr() < .2f + .5f * night
                rect(wx, top + u * 1.2f, u * 1.2f, u * 2f, if (on) c("#FFD27A", .9f) else mix(c("#3A4258"), haze, .3f)); rect(wx, top - u * 2.2f, u * 1.1f, u * 1.5f, if (rr() < .3f + .4f * night) c("#FFD27A", .85f) else mix(c("#3A4258"), haze, .3f)) }
            if (rr() < .7f) { val cxx = mx + w * (.2f + rr() * .6f); rect(cxx, top - u * 5.2f, u * 2.4f, u * 2.6f, st); for (q in 0 until 3) rect(cxx + u * (.2f + q * .75f), top - u * 6f, u * .5f, u * .9f, mix(c("#C2683E"), haze, .3f)) }
            mx += w + u * .2f }
        mist(mb - u * 2f, u * 5f, haze.a(.25f)) }
    // Rangée d'immeubles haussmanniens
    val cornice = H * .7f
    var bx = -u * 6f; var seed = 3
    while (bx < W) { val w = u * (24f + (seed * 7 % 9)); haussmann(bx, w, cornice + ((seed % 3) - 1) * u * 1.4f, H, u, seed, night, sunA); bx += w + u * .3f; seed++ }
    // Toit de zinc au premier plan : tasseaux, verrière, cheminée à boisseaux et chat sur le faîtage
    val ridge = H * .86f
    fill(poly(-u, ridge, W + u, ridge - u * 3f, W + u, H, -u, H), vg(ridge - u * 3f, H, 0f to c("#9AA6BA"), 1f to c("#5E6A80")))
    for (k in 0 until 40) { val fx = W * k / 39f; line(fx, ridge - u * 3f * fx / W, W * .5f + (fx - W * .5f) * 1.6f, H, max(1f, u * .2f), c("#4A5468", .5f)) }
    rect(-u, ridge - u * 3f * 0f - u * .8f, W + 2 * u, u * .8f, c("#6E7A90"))
    // Verrière d'atelier : châssis en fer, carreaux qui reflètent le ciel (ou éclairés la nuit)
    run { val gx0 = W * .56f; val gx1 = W * .8f; val gy0 = ridge + u * 3f; val gy1 = ridge + u * 11f
        val glass = Path().apply { moveTo(gx0 + u, gy0); lineTo(gx1 - u, gy0 - u * .4f); lineTo(gx1 + u, gy1); lineTo(gx0 - u, gy1 + u * .4f); close() }
        fill(Path().apply { moveTo(gx0 + u * .2f, gy0 - u * .8f); lineTo(gx1 - u * .2f, gy0 - u * 1.2f); lineTo(gx1 + u * 1.9f, gy1 + u * .7f); lineTo(gx0 - u * 1.9f, gy1 + u * 1.1f); close() }, c("#3A3E48"))
        fill(glass, vg(gy0, gy1, 0f to mix(c("#B8D2EC"), c("#FFD98A"), night), 1f to mix(c("#6A8AAA"), c("#E8A040"), night)))
        clipPath(glass) { for (k in 1 until 6) { val f = k / 6f; line(gx0 + u + (gx0 - u - gx0 - u) * 0f + (gx1 - gx0) * f, gy0 - u * .4f * f, gx0 - u + (gx1 - gx0 + u * 2f) * f, gy1 + u * .4f * (1 - f), max(1f, u * .3f), c("#2A2E36")) }
            for (k in 1 until 3) { val f = k / 3f; line(gx0, gy0 + (gy1 - gy0) * f, gx1, gy0 + (gy1 - gy0) * f - u * .4f, max(1f, u * .3f), c("#2A2E36")) }
            line(gx0 + (gx1 - gx0) * .2f, gy0, gx0 + (gx1 - gx0) * .45f, gy1, max(1f, u * 1.2f), c("#FFFFFF", .18f * (1f - night))) }
        if (night > .05f) glow((gx0 + gx1) / 2, (gy0 + gy1) / 2, u * 14f, c("#FFB04A", .3f * night), BlendMode.Screen) }
    run { val cx = W * .16f; val top = ridge - u * 16f
        rect(cx - u * 6f, top, u * 12f, ridge - top + u * 2f, c("#C9B496")); rect(cx + u * 2f, top, u * 4f, ridge - top + u * 2f, c("#A89478")); rect(cx - u * 6.6f, top, u * 13.2f, u * 1.2f, c("#8E7E68"))
        for (p in 0 until 6) { val px = cx - u * 5.4f + p * u * 1.9f; fill(poly(px, top, px + u * .3f, top - u * 3.4f, px + u * 1.3f, top - u * 3.4f, px + u * 1.6f, top), vg(top - u * 3.4f, top, 0f to c("#D27A4A"), 1f to c("#A8542E"))); rect(px + u * .2f, top - u * 3.8f, u * 1.2f, u * .5f, c("#8E4426")) } }
    run { val kx = W * .42f; val ky = ridge - u * 1.4f * .42f - u * .8f; val cat = c("#1A1820")
        drawOval(cat, Offset(kx - u * 2f, ky - u * 4.4f), Size(u * 4f, u * 4.6f)); disc(kx, ky - u * 5.2f, u * 1.6f, cat)
        fill(poly(kx - u * 1.5f, ky - u * 6f, kx - u * 1.1f, ky - u * 7.6f, kx - u * .3f, ky - u * 6.5f), cat); fill(poly(kx + u * .3f, ky - u * 6.5f, kx + u * 1.1f, ky - u * 7.6f, kx + u * 1.5f, ky - u * 6f), cat)
        val tail = sin(t * TAU * 3f) * u * .8f
        drawPath(Path().apply { moveTo(kx + u * 1.6f, ky - u * .4f); cubicTo(kx + u * 4.5f, ky, kx + u * 4.5f + tail, ky - u * 3f, kx + u * 3.2f + tail, ky - u * 4f) }, cat, style = Stroke(u * .7f, cap = StrokeCap.Round)) }
    // Pigeons
    for (i in 0 until 3) { val k = (t * 1.5f + i * .33f) % 1f; val px = W * (k * 1.2f - .1f); val py = H * (.24f + i * .05f); val s = u * 1.3f; val fl = sin(t * TAU * 18 + i) * .4f
        drawPath(Path().apply { moveTo(px - s, py - s * (.4f + fl)); quadraticTo(px - s * .4f, py - s * .2f, px, py + s * .2f); quadraticTo(px + s * .4f, py - s * .2f, px + s, py - s * (.4f + fl)) }, c("#2A2340", .75f), style = Stroke(max(1f, u * .3f))) }
    if (d == null) return
    if (d.dawn > .01f) drawRect(vg(0f, H, 0f to c("#7F8FE0", .1f * d.dawn), .5f to c("#FFB0C0", .12f * d.dawn), 1f to Color.Transparent))
    if (d.night > .01f) drawRect(vg(hz, H, 0f to c("#1A2550", .25f * d.night), 1f to c("#0A1030", .45f * d.night)), Offset(0f, hz), Size(W, H - hz), blendMode = BlendMode.Multiply)
}

/* ------------------------------------------------------------------ Canyon */
private fun DrawScope.canyon(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    sky(0f to c("#3E8AD8"), .5f to c("#9CCBF0"), 1f to c("#FFE1B8"), y1 = H * .45f)
    val sx = W * .22f; val sy = H * .12f; glow(sx, sy, S * .8f, c("#FFF2D0", .6f), BlendMode.Screen); disc(sx, sy, S * .05f, c("#FFFBEE"))
    softCloud(W * .7f + sin(t * TAU) * u * 4f, H * .15f, W * .6f, c("#FFFFFF", .55f), c("#FFFFFF", .5f), 4)
    // Buttes lointaines dans la brume chaude
    for ((bx, bw, bh) in listOf(Triple(.12f, .14f, .05f), Triple(.45f, .1f, .07f), Triple(.8f, .18f, .045f))) {
        val by = H * .45f; fill(poly(W * (bx - bw / 2), by, W * (bx - bw * .38f), by - H * bh, W * (bx + bw * .38f), by - H * bh, W * (bx + bw / 2), by), c("#D9A79A")) }
    rect(0f, H * .45f, W, H * .55f, c("#E2A884"))
    drawRect(vg(H * .45f, H * .62f, 0f to c("#F2C6A0"), 1f to c("#D98A62")), topLeft = Offset(0f, H * .45f), size = Size(W, H * .17f))
    // Parois en gradins, de part et d'autre de la rivière ; strates horizontales
    fun wall(pts: FloatArray, lit: Boolean) {
        val p = poly(*pts)
        fill(p, vg(H * .3f, H, 0f to c("#D9744A"), .5f to c("#B8552E"), 1f to c("#7A3418")))
        clipPath(p) {
            for (b in 0 until 60) { val y = H * .3f + b * u * 1.9f; rect(0f, y, W, u * (.5f + (b % 4) * .35f), if (b % 3 == 0) c("#FFD9B0", .16f) else c("#5A2410", .14f)) }
            // Faces verticales des gradins : à l'ombre (gauche) ou au soleil (droite)
            var k = 0
            while (k < pts.size - 2) { val x0 = pts[k]; val y0 = pts[k + 1]; val x1 = pts[k + 2]; val y1 = pts[k + 3]
                if (y1 - y0 > u * 3f) { val fp = poly(x0, y0, x1, y1, x1, H, x0, H); if (lit) fill(fp, lg(x0, y0, x0, y1, 0f to c("#FFD2A0", .35f), 1f to c("#FFD2A0", .05f))) else fill(fp, c("#4A1A0A", .28f)) }
                k += 2 }
        }
    }
    wall(floatArrayOf(0f, H * .36f, W * .2f, H * .36f, W * .22f, H * .46f, W * .3f, H * .47f, W * .33f, H * .58f, W * .41f, H * .6f, W * .43f, H * .74f, W * .47f, H * .76f, W * .44f, H, 0f, H), false)
    wall(floatArrayOf(W, H * .33f, W * .84f, H * .33f, W * .81f, H * .44f, W * .7f, H * .46f, W * .67f, H * .57f, W * .59f, H * .6f, W * .57f, H * .73f, W * .53f, H * .76f, W * .58f, H, W, H), true)
    mist(H * .6f, H * .05f, c("#FFE1C0", .25f))
    // Rivière turquoise qui serpente au fond
    val rp = Path().apply { moveTo(W * .47f, H * .76f); cubicTo(W * .4f, H * .84f, W * .58f, H * .9f, W * .44f, H); lineTo(W * .6f, H); cubicTo(W * .66f, H * .9f, W * .48f, H * .84f, W * .53f, H * .76f); close() }
    fill(rp, vg(H * .76f, H, 0f to c("#9FEDE4"), 1f to c("#2EA6B8")))
    for (i in 0 until 14) { val y = H * (.79f + i * .015f); val x = W * (.51f + sin(i * 1.3f + t * TAU) * .03f); line(x - u * 1.5f, y, x + u * 1.5f, y, max(1f, u * .3f), c("#FFFFFF", .4f)) }
    // Aigle qui plane
    run { val a = t * TAU; val x = W * .5f + cos(a) * W * .25f; val y = H * .28f + sin(a) * H * .04f; val s = u * 3f
        drawPath(Path().apply { moveTo(x - s * 1.6f, y - s * .2f); quadraticTo(x - s * .6f, y - s * .5f, x, y); quadraticTo(x + s * .6f, y - s * .5f, x + s * 1.6f, y - s * .2f) }, c("#3A2A2A", .8f), style = Stroke(max(1f, u * .45f))) }
}

/* ------------------------------------------------------------------ Savane au couchant */
private fun DrawScope.savanna(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val hz = H * .66f
    sky(0f to c("#4A2A6A"), .35f to c("#C8507A"), .65f to c("#FF8A4A"), 1f to c("#FFD27A"), y1 = hz)
    val sx = W * .5f; val sy = hz - H * .08f
    glow(sx, sy, S * 1.2f, c("#FF9A4A", .55f), BlendMode.Screen); disc(sx, sy, S * .22f, c("#FFC46A", .95f)); disc(sx, sy, S * .2f, c("#FFE09A"))
    for (b in 0 until 4) rect(0f, sy + S * (.04f + b * .045f), W, S * (.006f + b * .003f), c("#FF8A4A", .5f))
    for (i in 0 until 3) softCloud(((t * .4f + i * .4f) % 1.3f - .15f) * W * 1.1f, H * (.14f + i * .08f), W * .7f, c("#8A3A6A", .35f), c("#FFB07A", .3f), 90 + i)
    val nz = Nz(8)
    ridgeFill(ridgePts(hz, H * .02f, 60) { x -> .5f + .5f * nz.at(x * 4f, 3) }, c("#7A3A3A"))
    drawRect(vg(hz, H, 0f to c("#5A2A22"), 1f to c("#1E0E0C")), topLeft = Offset(0f, hz), size = Size(W, H - hz))
    val dark = c("#1A0C10")
    // Acacias en ombrelle
    fun acacia(x: Float, yb: Float, h: Float) {
        line(x, yb, x - h * .05f, yb - h * .6f, max(1f, h * .05f), dark); line(x - h * .05f, yb - h * .6f, x - h * .3f, yb - h * .85f, max(1f, h * .03f), dark); line(x - h * .05f, yb - h * .6f, x + h * .25f, yb - h * .88f, max(1f, h * .03f), dark)
        drawOval(dark, Offset(x - h * .55f, yb - h * 1.02f), Size(h * 1.1f, h * .22f)); drawOval(dark, Offset(x - h * .35f, yb - h * 1.1f), Size(h * .7f, h * .16f))
    }
    acacia(W * .2f, hz + u * 2f, H * .18f); acacia(W * .88f, hz + u, H * .1f); acacia(W * .42f, hz, H * .06f)
    // Girafes qui marchent lentement
    fun giraffe(x: Float, yb: Float, s: Float, ph: Float) {
        val st = sin(ph) * s * .08f
        for ((lx, o) in listOf(-.25f to 0f, -.1f to 1f, .2f to 2f, .32f to 3f)) { val sw = sin(ph + o) * s * .06f; line(x + lx * s, yb - s * .55f, x + lx * s + sw, yb, max(1f, s * .045f), dark) }
        drawOval(dark, Offset(x - s * .35f, yb - s * .72f), Size(s * .75f, s * .28f))
        line(x + s * .3f, yb - s * .62f, x + s * .55f + st, yb - s * 1.45f, max(1f, s * .08f), dark)
        drawOval(dark, Offset(x + s * .5f + st, yb - s * 1.55f), Size(s * .26f, s * .12f))
        line(x + s * .58f + st, yb - s * 1.55f, x + s * .56f + st, yb - s * 1.63f, max(1f, s * .025f), dark)
    }
    val k = (t * 1f) % 1f
    giraffe(W * (1.15f - k * 1.5f), hz + u * 3f, H * .12f, t * TAU * 12f); giraffe(W * (1.4f - k * 1.5f), hz + u * 3.5f, H * .09f, t * TAU * 12f + 1.6f)
    // Herbes hautes au premier plan
    val g = Mul(4)
    for (i in 0 until 160) { val x = g() * W; val yb = H * (.84f + g() * .16f); val h = u * (5f + g() * 10f); val sw = sin(t * TAU * 2 + x * .01f) * u
        line(x, yb, x + sw, yb - h, max(1f, u * .3f), mix(c("#2A140E"), c("#C8743A"), g() * .4f)) }
    // Oiseaux
    for (i in 0 until 4) { val kk = (t * 2f + i * .25f) % 1f; val x = W * (kk * 1.2f - .1f); val y = H * (.3f + i * .03f); val s = u * 1.1f
        drawPath(Path().apply { moveTo(x - s, y - s * .4f); quadraticTo(x - s * .4f, y - s * .2f, x, y + s * .2f); quadraticTo(x + s * .4f, y - s * .2f, x + s, y - s * .4f) }, dark.a(.8f), style = Stroke(max(1f, u * .3f))) }
}

/* ------------------------------------------------------------------ Lanternes célestes sur le lac */
private fun DrawScope.lanterns(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val hz = H * .7f
    sky(0f to c("#0A0A24"), .6f to c("#241A4A"), 1f to c("#4A2A5A"), y1 = hz)
    starField(27, 160, hz, c("#FFFFFF", .7f), t, 3)
    val nz = Nz(17)
    ridgeFill(ridgePts(hz, H * .06f, 60) { x -> .5f + .5f * nz.at(x * 3f, 3) }, c("#1A1430"), hz + 2f)
    conifers(40, 5, u * 3f, u * 7f, c("#100C22"), { x -> hz - H * .01f })
    drawRect(vg(hz, H, 0f to c("#2A1E48"), 1f to c("#0A0818")), topLeft = Offset(0f, hz), size = Size(W, H - hz))
    // Lanternes qui montent, avec leur reflet dans le lac
    val r = Mul(23)
    for (i in 0 until 26) {
        val x0 = r() * W; val sp = .25f + r() * .3f; val z = .35f + r() * .65f
        val k = ((r() + t * sp) % 1f); val y = hz - (hz + u * 10f) * k
        val x = x0 + sin(t * TAU * 2 + i) * u * 2f; val w = u * 3.4f * z; val h = w * 1.25f; val fl = .85f + .15f * sin(t * TAU * 9 + i * 2f)
        glow(x, y, w * 4f, c("#FFB04A", .35f * z * fl), BlendMode.Screen)
        fill(poly(x - w / 2, y - h / 2, x + w / 2, y - h / 2, x + w * .38f, y + h / 2, x - w * .38f, y + h / 2), vg(y - h / 2, y + h / 2, 0f to c("#FFD27A", z), 1f to c("#FF7A2A", z)))
        disc(x, y + h * .4f, w * .14f, c("#FFF6D0", z * fl))
        // Reflet
        val ry = hz + (hz - y) * .35f
        if (ry < H) { val rw = w * .7f; for (j in 0 until 3) line(x - rw, ry + j * u * .8f, x + rw, ry + j * u * .8f, max(1f, u * .3f * z), c("#FFB04A", (.35f - j * .1f) * z)) }
    }
    // Barques au premier plan avec leurs lanternes
    for ((bx, by) in listOf(.25f to .85f, .7f to .92f)) { val x = W * bx; val y = H * by; val L = u * 18f
        fill(Path().apply { moveTo(x - L / 2, y); quadraticTo(x, y + u * 4f, x + L / 2, y); lineTo(x + L * .4f, y + u * 1.5f); quadraticTo(x, y + u * 4.5f, x - L * .4f, y + u * 1.5f); close() }, c("#0C0816"))
        disc(x - L * .1f, y - u * 2.5f, u * 1.4f, c("#2A1A30")); rect(x - L * .14f, y - u * 1.5f, u * 2.6f, u * 1.6f, c("#2A1A30"))
        glow(x + L * .2f, y - u * 2f, u * 6f, c("#FFB04A", .5f), BlendMode.Screen); disc(x + L * .2f, y - u * 2f, u * .9f, c("#FFE09A")) }
}

/* ------------------------------------------------------------------ Banquise */
private fun DrawScope.icefield(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val hz = H * .5f
    sky(0f to c("#9EC9F2"), .6f to c("#D6EAFA"), 1f to c("#FCE8E0"), y1 = hz)
    glow(W * .7f, hz - H * .04f, S * .9f, c("#FFE8D6", .7f), BlendMode.Screen)
    softCloud(W * .3f + sin(t * TAU) * u * 3f, H * .16f, W * .7f, c("#FFFFFF", .7f), c("#FFFFFF", .6f), 6)
    // Mer froide
    drawRect(vg(hz, H, 0f to c("#5E9CC8"), 1f to c("#1E4A78")), topLeft = Offset(0f, hz), size = Size(W, H - hz))
    val r = Mul(31)
    for (i in 0 until 70) { val v = r(); val y = hz + (H - hz) * v * v; val x = ((r() + t * (.04f + v * .08f)) % 1f) * W; line(x, y, x + u * (1f + v * 6f), y, max(1f, u * (.15f + v * .25f)), c("#FFFFFF", .15f + v * .2f)) }
    // Icebergs : faces éclairées et faces à l'ombre, partie immergée turquoise
    fun berg(x: Float, w: Float, h: Float, seed: Int) {
        val rr = Mul(seed); val n = 7; val top = FloatArray((n + 1) * 2)
        for (i in 0..n) { val f = i / n.toFloat(); top[i * 2] = x - w / 2 + w * f; top[i * 2 + 1] = hz - h * (sin(f * 3.1416f).coerceAtLeast(0f).pow(.6f)) * (.7f + rr() * .3f) }
        val p = Path(); p.moveTo(x - w / 2, hz); var i = 0; while (i < top.size) { p.lineTo(top[i], top[i + 1]); i += 2 }; p.lineTo(x + w / 2, hz); p.close()
        drawOval(c("#7FD6E0", .45f), Offset(x - w * .55f, hz - u), Size(w * 1.1f, h * .5f))
        fill(p, vg(hz - h, hz, 0f to c("#FFFFFF"), 1f to c("#CFE6F5")))
        clipPath(p) { var j = 0; while (j < top.size - 2) { if (top[j + 3] > top[j + 1]) fill(poly(top[j], top[j + 1], top[j + 2], top[j + 3], top[j + 2], hz, top[j], hz), c("#8FB8DE", .55f)); j += 2 } }
        line(x - w / 2, hz, x + w / 2, hz, max(1f, u * .4f), c("#FFFFFF", .7f))
    }
    berg(W * .2f, W * .5f, H * .14f, 3); berg(W * .78f, W * .32f, H * .08f, 5); berg(W * .55f, W * .14f, H * .035f, 9)
    // Plaques de glace dérivantes au premier plan
    for (i in 0 until 6) { val x = ((r() + t * .05f) % 1.2f - .1f) * W; val y = H * (.7f + r() * .25f); val w = u * (8f + r() * 16f)
        fill(lobed(x, y, w, w * .22f, 50 + i, 6, .14f), c("#F4FAFF")); fill(lobed(x, y + w * .1f, w * .95f, w * .12f, 60 + i, 6, .14f), c("#9FD0E8", .6f)) }
    // Phoque sur une plaque, sterne qui passe
    run { val x = W * .32f; val y = H * .78f; fill(lobed(x, y, u * 14f, u * 3f, 81, 6, .1f), c("#F4FAFF")); drawOval(c("#4A5262"), Offset(x - u * 5f, y - u * 4f), Size(u * 10f, u * 3.6f)); disc(x + u * 4.6f, y - u * 4f, u * 1.8f, c("#4A5262")); disc(x + u * 5.2f, y - u * 4.3f, u * .3f, c("#FFFFFF")) }
    run { val k = (t * 2f) % 1f; val x = W * (1.1f - k * 1.2f); val y = H * .3f; val s = u * 1.6f; val fl = sin(t * TAU * 16f) * .5f
        drawPath(Path().apply { moveTo(x - s, y - s * (.4f + fl)); quadraticTo(x - s * .4f, y - s * .2f, x, y + s * .2f); quadraticTo(x + s * .4f, y - s * .2f, x + s, y - s * (.4f + fl)) }, c("#3A4A5A", .7f), style = Stroke(max(1f, u * .3f))) }
}

/* ------------------------------------------------------------------ Tramway d'Angers */

/** Château d'Angers : courtines et tours rondes de schiste rayées de tuffeau, sans toits, jardins au sommet. [a] : opacité (reflets). */
private fun DrawScope.angersCastle(base: Float, a: Float, lit: Float) {
    val W = size.width; val u = min(W, size.height) / 100f
    // La nuit, la pierre et l'ardoise passent au bleu nuit (les lumières restent vives)
    fun nc(h: String, al: Float = a) = mix(c(h), c("#1C2238"), .72f * lit).a(al)
    val schist = nc("#4C4754", a); val schistLit = nc("#6A6472", a); val tuf = nc("#E4DACA", .85f * a); val tufSh = nc("#B9AF9E", a)
    val wallTop = base - u * 17f; val x0 = W * .5f; val x1 = W * 1.06f
    // Logis et chapelle derrière l'enceinte : toits d'ardoise et clocheton
    fill(poly(W * .66f, wallTop, W * .7f, wallTop - u * 7f, W * .84f, wallTop - u * 7f, W * .88f, wallTop), nc("#56657E", a))
    rect(W * .74f, wallTop - u * 13f, u * 1.6f, u * 6f, tuf); fill(poly(W * .74f - u * .6f, wallTop - u * 13f, W * .74f + u * .8f, wallTop - u * 18f, W * .74f + u * 2.2f, wallTop - u * 13f), nc("#56657E", a))
    // Jardins au sommet des remparts
    canopy(9, 77, u * 1.4f, u * 2.4f, nc("#5E7A4A", a), nc("#93AE6A", a), true, { wallTop + u * .6f }, x0 + u * 2f, x1)
    // Courtine : schiste sombre et fines bandes de tuffeau
    fill(poly(x0, base, x0 + u * 1.5f, wallTop, x1, wallTop, x1, base), schist)
    var y = wallTop + u * 3f; while (y < base) { rect(x0 + u * 1.5f, y, x1 - x0, u * .45f, tuf); y += u * 5f }
    drawRect(vg(wallTop, base, 0f to c("#FFFFFF", .06f * a), 1f to c("#000000", .18f * a)), Offset(x0, wallTop), Size(x1 - x0, base - wallTop))
    // Tours rondes : talus élargi à la base, ombrage cylindrique, bandes de tuffeau, couronnement plat
    for ((k, tx) in listOf(.53f, .65f, .77f, .89f, 1.01f).withIndex()) {
        val cx = W * tx; val tw = u * 9.5f; val top = wallTop - u * (4.5f + (k % 2) * 1.2f)
        fill(poly(cx - tw * .72f, base, cx - tw / 2, base - u * 6f, cx + tw / 2, base - u * 6f, cx + tw * .72f, base), schist)
        rect(cx - tw / 2, top, tw, base - u * 6f - top, schist)
        var by = top + u * 2.4f; while (by < base - u * 1f) { val wb = if (by > base - u * 6f) tw * (.5f + .22f * (by - (base - u * 6f)) / (u * 6f)) else tw / 2; rect(cx - wb, by, wb * 2, u * .5f, tuf); by += u * 5f }
        drawRect(lg(cx - tw / 2, 0f, cx + tw / 2, 0f, 0f to c("#FFFFFF", .16f * a), .35f to c("#FFFFFF", 0f), .7f to c("#000000", .12f * a), 1f to c("#000000", .32f * a)), Offset(cx - tw * .72f, top), Size(tw * 1.44f, base - top))
        rect(cx - tw * .56f, top - u * 1.2f, tw * 1.12f, u * 1.4f, schistLit); rect(cx - tw * .56f, top - u * .2f, tw * 1.12f, u * .5f, tufSh)
        for (m in 0 until 4) rect(cx - tw * .5f + m * tw * .28f, top - u * 2.2f, tw * .14f, u * 1f, schistLit)
        rect(cx - u * .4f, top + u * 5f, u * .8f, u * 1.4f, nc("#1A1A22", a))
    }
    // Projecteurs la nuit : le château s'illumine par le bas
    if (lit > .01f) drawRect(vg(wallTop - u * 8f, base, 0f to c("#FFC46A", 0f), 1f to c("#FFB04A", .42f * lit * a)), Offset(x0, wallTop - u * 8f), Size(x1 - x0, base - wallTop + u * 8f), blendMode = BlendMode.Screen)
}

/** Cathédrale Saint-Maurice et maisons de la ville haute, sur la colline. */
private fun DrawScope.angersCity(base: Float, a: Float, lit: Float) {
    val W = size.width; val u = min(W, size.height) / 100f
    // La nuit, la pierre et l'ardoise passent au bleu nuit (les lumières restent vives)
    fun nc(h: String, al: Float = a) = mix(c(h), c("#1C2238"), .72f * lit).a(al)
    val tuf = nc("#F1E8D8", a); val tufSh = nc("#CFC2AC", a); val slate = nc("#56657E", a); val slateLit = nc("#7E8EA8", a); val win = nc("#6A5A48", a)
    // Colline et maisons
    fill(Path().apply { moveTo(-u, base); quadraticTo(W * .2f, base - u * 7f, W * .55f, base - u * 1f); lineTo(W * .55f, base + u); lineTo(-u, base + u); close() }, nc("#7E9A62", a))
    val r = Mul(57); var x = -u
    while (x < W * .52f) {
        val w = u * (4f + r() * 4f); val gy = base - u * 7f * (1f - ((x / (W * .55f)) - .38f).let { it * it } * 2.2f).coerceIn(0f, 1f) * .9f
        val h = u * (3f + r() * 2.5f); val rh = u * (2.2f + r() * 1.5f)
        rect(x, gy - h, w, h + u * 2f, tuf); rect(x + w * .6f, gy - h, w * .4f, h + u * 2f, tufSh)
        fill(poly(x - u * .3f, gy - h, x + w * .5f, gy - h - rh, x + w + u * .3f, gy - h), slate); fill(poly(x - u * .3f, gy - h, x + w * .5f, gy - h - rh, x + w * .5f, gy - h), slateLit)
        for (k in 0 until (w / (u * 2.2f)).toInt()) { val wx = x + u * (.8f + k * 2.2f); val wy = gy - h * .6f
            if (lit > .3f && (k + (x / u).toInt()) % 2 == 0) { rect(wx, wy, u * .7f, u, c("#FFD27A", a)); glow(wx + u * .35f, wy + u * .5f, u * 2.2f, c("#FFB04A", .3f * a * lit), BlendMode.Screen) } else rect(wx, wy, u * .7f, u, win) }
        x += w + u * (.1f + r())
    }
    // Cathédrale : nef, façade à deux flèches et tour-lanterne Renaissance entre elles
    val cx = W * .25f; val cb = base - u * 6.5f
    rect(cx + u * 3f, cb - u * 10f, u * 14f, u * 10f, tufSh); fill(poly(cx + u * 2.5f, cb - u * 10f, cx + u * 10f, cb - u * 15f, cx + u * 17.5f, cb - u * 10f), slate)
    rect(cx - u * 6f, cb - u * 17f, u * 12f, u * 17f, tuf); rect(cx + u * 1.5f, cb - u * 17f, u * 4.5f, u * 17f, tufSh)
    for (sx in listOf(cx - u * 4.6f, cx + u * 4.6f)) {
        rect(sx - u * 1.7f, cb - u * 27f, u * 3.4f, u * 10f, tuf); rect(sx + u * .2f, cb - u * 27f, u * 1.5f, u * 10f, tufSh)
        fill(poly(sx - u * 1.9f, cb - u * 27f, sx, cb - u * 42f, sx + u * 1.9f, cb - u * 27f), slate); fill(poly(sx - u * 1.9f, cb - u * 27f, sx, cb - u * 42f, sx, cb - u * 27f), slateLit)
        rect(sx - u * .4f, cb - u * 24f, u * .8f, u * 2.4f, win)
    }
    rect(cx - u * 2f, cb - u * 31f, u * 4f, u * 14f, tuf); rect(cx + u * .4f, cb - u * 31f, u * 1.6f, u * 14f, tufSh)
    drawArc(slate, 180f, 180f, true, Offset(cx - u * 2.2f, cb - u * 33.2f), Size(u * 4.4f, u * 4.4f)); line(cx, cb - u * 33f, cx, cb - u * 35.5f, max(1f, u * .3f), nc("#C9A45E", a))
    disc(cx, cb - u * 13f, u * 1.8f, nc("#7A6A58", a)); drawCircle(nc("#A8987E", a), u * 1.8f, Offset(cx, cb - u * 13f), style = Stroke(max(1f, u * .35f)))
    for (k in 0..2) rect(cx - u * 4.6f + k * u * 4.6f - u * .9f, cb - u * 5f, u * 1.8f, u * 5f, nc("#5A4A3A", a))
    if (lit > .01f) glow(cx, cb - u * 20f, u * 20f, c("#FFD9A0", .22f * lit * a), BlendMode.Screen)
}

/** Rame de tramway d'Angers (sans caténaire : alimentation par le sol), vue de profil, [L] : longueur. */
private fun DrawScope.angersTram(cx: Float, rail: Float, L: Float, night: Float, a: Float = 1f) {
    val u = min(size.width, size.height) / 100f
    val top = rail - u * 10.5f; val bot = rail - u * 1.4f; val x0 = cx - L / 2; val x1 = cx + L / 2
    // Caisse blanche, nez arrondi vitré à droite, arrière arrondi
    val body = Path().apply {
        moveTo(x0 + u * 2f, bot); lineTo(x1 - u * 4f, bot); quadraticTo(x1 + u * .8f, bot, x1 + u * .8f, bot - u * 4f)
        quadraticTo(x1 + u * .6f, top + u * .6f, x1 - u * 5f, top); lineTo(x0 + u * 2.5f, top); quadraticTo(x0, top, x0, top + u * 2.5f); lineTo(x0, bot - u * 1.5f); quadraticTo(x0, bot, x0 + u * 2f, bot); close() }
    val nb = { h: String -> mix(c(h), c("#2A3248"), .55f * night).a(a) }
    fill(body, vg(top, bot, 0f to nb("#FFFFFF"), .55f to nb("#EEF0F3"), 1f to nb("#C9CDD4")))
    // Équipements de toiture (pas de pantographe)
    for (k in 0 until 4) { val bx = x0 + L * (.12f + k * .22f); fill(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(bx, top - u * 1.3f, bx + L * .11f, top + u * .2f, CornerRadius(u * .6f))) }, c("#D5D9E0", a)) }
    // Bandeau vitré continu (éclairé la nuit), séparé par les modules et les portes
    val wt = top + u * 2f; val wb = top + u * 6.4f
    fill(Path().apply { moveTo(x0 + u * 1.2f, wb); lineTo(x0 + u * 1.2f, wt + u * 1f); quadraticTo(x0 + u * 1.2f, wt, x0 + u * 2.4f, wt); lineTo(x1 - u * 6f, wt); lineTo(x1 - u * 6f, wb); close() },
        vg(wt, wb, 0f to mix(c("#3A4A62"), c("#FFE8B4"), night).a(a), 1f to mix(c("#161B24"), c("#F2C986"), night).a(a)))
    // Reflets du ciel sur les vitres (le jour)
    if (night < .9f) clipRect(x0 + u * 1.2f, wt, x1 - u * 6f, wb) { var rx = x0; while (rx < x1) { fill(poly(rx, wb, rx + u * 3f, wt, rx + u * 4.2f, wt, rx + u * 1.2f, wb), c("#BFD6F0", .16f * (1f - night) * a)); rx += u * 9f } }
    if (night > .01f) for (k in 0 until 10) glow(x0 + L * (.06f + k * .085f), (wt + wb) / 2, u * 4f, c("#FFC46A", .18f * night * a), BlendMode.Screen)
    // Pare-brise bombé
    fill(Path().apply { moveTo(x1 - u * 5.4f, wt); quadraticTo(x1 - u * .2f, wt + u * .2f, x1 + u * .3f, wb + u * 1.4f); lineTo(x1 - u * 5.4f, wb + u * 1.4f); close() }, vg(wt, wb, 0f to c("#2A3240", a), 1f to c("#0E1218", a)))
    line(x1 - u * 4.6f, wt + u * .8f, x1 - u * 2.4f, wb, max(1f, u * .25f), c("#FFFFFF", .25f * a))
    // Girouette : ligne A
    rect(x1 - u * 4.8f, wt + u * .5f, u * 2.4f, u * 1.2f, c("#10141A", a)); rect(x1 - u * 4.5f, wt + u * .75f, u * .8f, u * .7f, c("#FF9A2A", a))
    // Modules et portes
    val mods = 5
    for (k in 1 until mods) { val mx = x0 + L * k / mods; line(mx, top + u * .4f, mx, bot - u * .4f, max(1f, u * .22f), c("#8E96A2", a)) }
    for (k in 0 until mods) { val dx = x0 + L * (k + .32f) / mods; fill(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(dx, wt - u * .4f, dx + u * 3.4f, bot - u * .6f, CornerRadius(u * .6f))) }, c("#DCE0E6", a))
        line(dx + u * 1.7f, wt, dx + u * 1.7f, bot - u * .8f, max(1f, u * .2f), c("#9AA2AE", a)); rect(dx + u * .4f, wt + u * .2f, u * 2.6f, u * 3.6f, mix(c("#2A3240"), c("#FFE2A6"), night).a(a)) }
    // Bande arc-en-ciel sur la jupe
    RAINBOW.forEachIndexed { i, h -> rect(x0 + u * 1.5f, bot - u * 2.8f + i * u * .38f, L - u * 3.5f, u * .38f, c(h, .9f * a)) }
    // Bogies et roues
    // (Citadis à 5 modules : bogies sous les modules 1, 3 et 5, en retrait des extrémités, à moitié cachés par la jupe)
    for (k in listOf(0, 2, 4)) {
        val bx = x0 + L * (k + .5f) / mods + (if (k == 0) u * 1.5f else if (k == mods - 1) -u * 1.5f else 0f)
        fill(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(bx - u * 3f, bot - u * .3f, bx + u * 3f, rail - u * .2f, CornerRadius(u * .5f))) }, c("#2A2E36", a))
        for (wx in listOf(-1.6f, 1.6f)) { disc(bx + u * wx, rail - u * .6f, u * .95f, c("#16181D", a)); disc(bx + u * wx, rail - u * .6f, u * .35f, c("#6A707A", a)) }
        rect(bx - u * 3.2f, bot - u * .6f, u * 6.4f, u * .5f, c("#3A3F48", a))
    }
    // Phares (et leur halo la nuit)
    disc(x1 - u * .4f, bot - u * 2.2f, u * .6f, c("#FFFBEA", a))
    if (night > .01f) { glow(x1 + u * 2f, bot - u * 2.2f, u * 12f, c("#FFF0C8", .35f * night * a), BlendMode.Screen); ellGlow(x1 + u * 14f, rail + u * .2f, u * 14f, u * 1.4f, c("#FFE8B0", .25f * night * a), BlendMode.Screen) }
}

private fun DrawScope.angers(t: Float, d: Tod? = null) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val bank = H * .5f; val deck = H * .64f; val quay = H * .84f
    val night = d?.night ?: 0f
    // Ciel
    if (d == null) sky(0f to c("#4F86C6"), .4f to c("#9CC2E6"), .8f to c("#F6D6B8"), 1f to c("#FFE6C6"), y1 = bank)
    else sky(0f to d.col("#3F86D6", "#6A7FC0", "#3A4A8A", "#070C22"), .4f to d.col("#8EC0EC", "#C9A8D0", "#C27A92", "#142048"),
        .8f to d.col("#D6E8F6", "#FFC9B0", "#FFAE72", "#24345E"), 1f to d.col("#EAF2F8", "#FFE2C8", "#FFD08E", "#33446C"), y1 = bank)
    val sun = d?.sun ?: .2f; val sunA = if (d == null) 1f else (1f - night).coerceIn(0f, 1f)
    val sx = W * (.1f + .8f * sun); val sy = if (d == null) H * .3f else bank - H * .05f - (bank - H * .14f) * sin(sun * 3.1416f)
    if (sunA > .01f) { glow(sx, sy, S * 1f, c("#FFE2B0", .4f * sunA), BlendMode.Screen); glow(sx, sy, S * .2f, c("#FFF2D6", .9f * sunA), BlendMode.Screen); disc(sx, sy, S * .04f, c("#FFF8EA", sunA)) }
    if (night > .01f) {
        starField(71, 150, bank - H * .12f, c("#FFFFFF", .85f * night), t, 3)
        val mx = W * .82f; val my = H * .1f; glow(mx, my, S * .5f, c("#BFD6FF", .25f * night), BlendMode.Screen); disc(mx, my, S * .042f, c("#F4F7FF", night)); disc(mx - S * .014f, my - S * .008f, S * .037f, d!!.col("#8EC0EC", "#6A7FC0", "#3A4A8A", "#0B1430").a(.9f * night))
    }
    for (i in 0 until 4) { val dx = ((t * (.5f + i * .12f) + i * .31f) % 1.3f - .15f) * W * 1.1f; softCloud(dx, H * (.07f + i * .07f), W * (.45f + i * .08f), c("#FFFFFF", (.5f - i * .06f) * (1f - .85f * night)), c("#FFF1DE", .4f * (1f - .85f * night)), 60 + i) }
    // Ville haute et cathédrale, château au bord de la Maine
    val lit = night
    angersCity(bank, 1f, lit)
    angersCastle(bank + u * 3f, 1f, lit)
    // La Maine : reflets de la ville et du château
    val water = bank + u * 3f
    if (d == null) drawRect(vg(water, quay, 0f to c("#E8D8C2"), .3f to c("#9EBCD6"), 1f to c("#4E7EAE")), Offset(0f, water), Size(W, quay - water + u * 4f))
    else drawRect(vg(water, quay, 0f to d.col("#DCEAF6", "#F2D2C8", "#F2B98E", "#2A3A60"), .3f to d.col("#A8C8E6", "#C8C0DA", "#B88AA6", "#1C2A50"), 1f to d.col("#3F74AE", "#6F8FC8", "#4A5E9A", "#0A1430")), Offset(0f, water), Size(W, quay - water + u * 4f))
    clipRect(0f, water, W, quay) { scale(1f, -.6f, Offset(0f, water)) { angersCity(bank, .3f, lit); angersCastle(bank + u * 3f, .3f, lit) } }
    mist(water + u * 1f, u * 4f, c("#FFF1DE", .35f * (1f - night)))
    val r = Mul(13)
    for (i in 0 until 50) { val v = r(); val y = water + (quay - water) * (.05f + v * .95f); val x = ((r() + t * (.15f + v * .3f)) % 1f) * W; val len = u * (2f + v * 8f)
        line(x, y, x + len, y, max(1f, u * .2f), c("#FFFFFF", (.14f + v * .12f) * (1f - .5f * night))) }
    if (night > .01f) for (k in 0 until 5) { val lx = W * (.55f + k * .11f); for (j in 0 until 6) line(lx - u * (1f + j * .3f), water + u * (2f + j * 2.4f), lx + u * (1f + j * .3f), water + u * (2f + j * 2.4f), max(1f, u * .3f), c("#FFC46A", .3f * night * (1f - j / 6f))) }
    // Pont du tramway : tablier fin, piles en V, garde-corps blanc
    val dk = u * 2.4f
    val pierFoot = deck + (quay - deck) * .55f
    for (px in listOf(.18f, .5f, .82f)) { val x = W * px
        fill(poly(x - u * 3.2f, deck + dk, x - u * .9f, pierFoot, x + u * .9f, pierFoot, x + u * 3.2f, deck + dk), vg(deck, pierFoot, 0f to mix(c("#E2DED6"), c("#2A3248"), .6f * night), 1f to mix(c("#A9A49C"), c("#1A2238"), .6f * night)))
        fill(poly(x, deck + dk, x + u * .9f, pierFoot, x + u * 3.2f, deck + dk), c("#000000", .12f))
        drawOval(c("#2A3A5A", .22f), Offset(x - u * 2.6f, pierFoot - u * .5f), Size(u * 5.2f, u * 1.1f)) }
    rect(-u, deck, W + 2 * u, dk, mix(c("#E9E6E0"), c("#2E3650"), .6f * night)); rect(-u, deck + dk * .6f, W + 2 * u, dk * .4f, mix(c("#B9B4AC"), c("#1E2640"), .6f * night))
    line(-u, deck - u * 3.4f, W + u, deck - u * 3.4f, max(1f, u * .35f), c("#FFFFFF", .9f))
    for (k in 0..24) { val x = W * k / 24f; line(x, deck, x, deck - u * 3.4f, max(1f, u * .18f), c("#FFFFFF", .7f)) }
    // Mâts d'éclairage du pont (côté amont, derrière la rame)
    for (k2 in 0 until 4) { val lx = W * (.06f + k2 * .3f); line(lx, deck, lx, deck - u * 12f, max(1f, u * .35f), c("#C9CDD4")); line(lx, deck - u * 12f, lx + u * 2.4f, deck - u * 12.6f, max(1f, u * .35f), c("#C9CDD4"))
        if (night > .01f) { disc(lx + u * 2.4f, deck - u * 12.4f, u * .6f, c("#FFF2C8", night)); glow(lx + u * 2.4f, deck - u * 12f, u * 8f, c("#FFE2A0", .3f * night), BlendMode.Screen) } }
    // Rame qui traverse lentement la Maine
    val k = (t + .5f) % 1f; val L = W * .92f
    angersTram(W * (-.6f + 2.2f * k), deck, L, night)
    // Reflet de la rame
    clipRect(0f, deck + dk, W, quay) { scale(1f, -.45f, Offset(0f, deck + dk)) { angersTram(W * (-.6f + 2.2f * k), deck, L, night, .25f) } }
    // Quai : mur de schiste et tuffeau, promenade, platanes
    rect(0f, quay - u * 3f, W, u * 3f, c("#6A6878")); for (k2 in 0 until 30) rect(W * k2 / 30f, quay - u * 3f, u * .25f, u * 3f, c("#4A4858"))
    rect(0f, quay - u * 3.6f, W, u * .7f, c("#E6DCCA"))
    drawRect(vg(quay, H, 0f to c("#D9CDB4"), .2f to c("#BBAF96"), 1f to c("#8E8470")), Offset(0f, quay), Size(W, H - quay))
    for (k2 in 0 until 14) line(0f, quay + (H - quay) * (k2 / 14f).pow(1.6f), W, quay + (H - quay) * (k2 / 14f).pow(1.6f), max(1f, u * .15f), c("#7A705E", .25f))
    // Massif fleuri le long du quai et banc
    val fb = quay + (H - quay) * .32f
    fill(Path().apply { moveTo(0f, fb); quadraticTo(W * .5f, fb - u * 2f, W, fb); lineTo(W, fb + u * 5f); quadraticTo(W * .5f, fb + u * 3f, 0f, fb + u * 5f); close() }, c("#5E7E3E"))
    val fr = Mul(23); for (i in 0 until 90) { val x = fr() * W; val y = fb - u * 1.5f + fr() * u * 5f; disc(x, y, u * (.5f + fr() * .5f), listOf(c("#E8607A"), c("#FFD25A"), c("#F49AAE"), c("#FFFFFF"), c("#B48CFF"))[(fr() * 5).toInt().coerceAtMost(4)]) }
    run { val bx = W * .58f; val by = fb + u * 9f; rect(bx, by - u * 3f, u * 12f, u * 1f, c("#7A5A3A")); rect(bx, by - u * 1.6f, u * 12f, u * .9f, c("#8A6A48")); line(bx + u * 1f, by - u * 1f, bx + u * 1f, by + u * 1.5f, max(1f, u * .5f), c("#2A2E36")); line(bx + u * 11f, by - u * 1f, bx + u * 11f, by + u * 1.5f, max(1f, u * .5f), c("#2A2E36")) }
    // Réverbères du quai
    for (k2 in 0 until 3) { val lx = W * (.2f + k2 * .32f); val ly = quay + H * .02f
        line(lx, ly, lx, ly - u * 14f, max(1f, u * .5f), c("#2A2E36")); fill(poly(lx - u * 1.4f, ly - u * 14f, lx, ly - u * 16.5f, lx + u * 1.4f, ly - u * 14f), c("#2A2E36"))
        disc(lx, ly - u * 14.6f, u * .8f, mix(c("#8E96A2"), c("#FFF2C8"), night)); if (night > .01f) glow(lx, ly - u * 14f, u * 12f, c("#FFC46A", .35f * night), BlendMode.Screen) }
    // Platanes qui encadrent la scène
    for ((px, py, sd) in listOf(Triple(-.02f, .79f, 3), Triple(1.02f, .81f, 5))) { val x = W * px; val y = H * py
        rect(x - u * 1.2f, y - u * 26f, u * 2.4f, u * 30f, c("#8E8A6E")); for (m in 0 until 6) disc(x + (m % 2 - .5f) * u * 1.4f, y - u * (4f + m * 4f), u * .7f, c("#C9C29A", .6f))
        crown(x, y - u * 34f, u * 16f, u * 13f, sd, mix(c("#A9C27A"), c("#2A3A3A"), .7f * night), mix(c("#6E8E4A"), c("#1E2A2E"), .7f * night), mix(c("#3A5428"), c("#121A20"), .7f * night), if (px < .5f) -.3f else -.9f, -.8f) }
    // Lumière de l'heure
    if (d == null) return
    if (d.dusk > .01f) drawRect(vg(0f, H, 0f to c("#5A2E8A", .16f * d.dusk), .5f to c("#FF7A3A", .14f * d.dusk), 1f to c("#FFB070", .05f * d.dusk)))
    if (d.dawn > .01f) drawRect(vg(0f, H, 0f to c("#7F8FE0", .1f * d.dawn), .5f to c("#FFB0C0", .12f * d.dawn), 1f to Color.Transparent))
    if (night > .01f) drawRect(vg(bank, H, 0f to c("#1A2550", .25f * night), 1f to c("#0A1030", .55f * night)), Offset(0f, bank), Size(W, H - bank), blendMode = BlendMode.Multiply)
}

/* ------------------------------------------------------------------ Douceur angevine (Layon) */

/** Moulin cavier : cône de maçonnerie en tuffeau, hucherolle de bois et ailes qui tournent. */
private fun DrawScope.cavier(x: Float, base: Float, s: Float, rot: Float) {
    val u = s
    fill(poly(x - u * 5f, base, x - u * 2.8f, base - u * 10f, x + u * 2.8f, base - u * 10f, x + u * 5f, base), lg(x - u * 5f, 0f, x + u * 5f, 0f, 0f to c("#FBF4E6"), .6f to c("#E6D8BE"), 1f to c("#C2B091")))
    for (k in 1..3) line(x - u * (5f - k * .55f), base - u * k * 2.5f, x + u * (5f - k * .55f), base - u * k * 2.5f, max(1f, u * .12f), c("#B8A888", .6f))
    fill(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(x - u * .9f, base - u * 3.4f, x + u * .9f, base, CornerRadius(u * .9f, u * .9f))) }, c("#5A4A3A"))
    drawRect(vg(base - u * 15f, base - u * 10f, 0f to c("#8A6A4A"), 1f to c("#5A4230")), Offset(x - u * 3.4f, base - u * 15f), Size(u * 6.8f, u * 5f))
    fill(poly(x - u * 3.9f, base - u * 15f, x, base - u * 18.5f, x + u * 3.9f, base - u * 15f), c("#5E6E86")); fill(poly(x - u * 3.9f, base - u * 15f, x, base - u * 18.5f, x, base - u * 15f), c("#8A9AB4"))
    rect(x - u * .7f, base - u * 13.6f, u * 1.4f, u * 1.6f, c("#FFE2A0"))
    // Ailes : quatre bras à claire-voie
    val hx = x + u * 3.6f; val hy = base - u * 13f
    for (k in 0 until 4) {
        val a = rot + k * 1.5708f; val ca = cos(a); val sa = sin(a); val nx = -sa; val ny = ca; val L = u * 11f
        val p0 = Offset(hx + ca * u * 1.2f, hy + sa * u * 1.2f)
        val q = listOf(p0, Offset(hx + ca * L, hy + sa * L), Offset(hx + ca * L + nx * u * 2.4f, hy + sa * L + ny * u * 2.4f), Offset(p0.x + nx * u * 2.4f, p0.y + ny * u * 2.4f))
        fill(Path().apply { moveTo(q[0].x, q[0].y); q.drop(1).forEach { lineTo(it.x, it.y) }; close() }, c("#F4EBDD", .85f))
        for (m in 1..5) { val f = m / 6f; line(p0.x + ca * L * f, p0.y + sa * L * f, p0.x + ca * L * f + nx * u * 2.4f, p0.y + sa * L * f + ny * u * 2.4f, max(1f, u * .14f), c("#6A5236")) }
        line(hx, hy, hx + ca * L, hy + sa * L, max(1f, u * .35f), c("#5A4632"))
    }
    disc(hx, hy, u * .7f, c("#3A2E22"))
}

/** Logis de tuffeau : toit d'ardoise très pentu, lucarnes à fronton, hautes cheminées, tour à toit en poivrière. */
private fun DrawScope.manoir(x: Float, base: Float, u: Float, lit: Float) {
    val tuf = c("#F5EDDD"); val tufSh = c("#D9CAB0"); val slate = c("#56657E"); val slateLit = c("#8394AE"); val win = mix(c("#5A6A7E"), c("#FFD27A"), lit)
    val w = u * 24f; val h = u * 9f; val roof = u * 9f
    // Tour ronde à droite
    val tx = x + w * .5f + u * 2f; rect(tx - u * 2.6f, base - h - u * 4f, u * 5.2f, h + u * 4f, tuf); rect(tx + u * .6f, base - h - u * 4f, u * 2f, h + u * 4f, tufSh)
    fill(poly(tx - u * 3f, base - h - u * 4f, tx, base - h - u * 15f, tx + u * 3f, base - h - u * 4f), slate); fill(poly(tx - u * 3f, base - h - u * 4f, tx, base - h - u * 15f, tx, base - h - u * 4f), slateLit)
    rect(tx - u * .5f, base - h + u * 1f, u * 1f, u * 1.6f, win)
    // Logis
    rect(x - w / 2, base - h, w, h, tuf); rect(x + w * .3f, base - h, w * .2f, h, tufSh)
    fill(poly(x - w / 2 - u * .5f, base - h, x - w / 2 + u * 4f, base - h - roof, x + w / 2 - u * 4f, base - h - roof, x + w / 2 + u * .5f, base - h), slate)
    fill(poly(x - w / 2 - u * .5f, base - h, x - w / 2 + u * 4f, base - h - roof, x - u * 2f, base - h - roof, x - u * 4f, base - h), slateLit)
    for (cxk in listOf(-.36f, .32f)) { val cx = x + w * cxk; rect(cx - u * .9f, base - h - roof - u * 3.5f, u * 1.8f, u * 5f, tufSh); rect(cx - u * 1.1f, base - h - roof - u * 3.8f, u * 2.2f, u * .6f, tuf) }
    for (lx in listOf(-.18f, .12f)) { val dx = x + w * lx; rect(dx - u * 1.6f, base - h - u * 4.6f, u * 3.2f, u * 4.8f, tuf)
        fill(poly(dx - u * 2f, base - h - u * 4.6f, dx, base - h - u * 7.6f, dx + u * 2f, base - h - u * 4.6f), tuf); disc(dx, base - h - u * 6.8f, u * .35f, tufSh)
        rect(dx - u * .8f, base - h - u * 3.8f, u * 1.6f, u * 2.6f, win) }
    for (k in 0 until 5) { val wx = x - w / 2 + u * 2f + k * (w - u * 5f) / 4f; rect(wx, base - h + u * 2f, u * 1.6f, u * 2.6f, win); rect(wx - u * .2f, base - h + u * 1.8f, u * 2f, u * .3f, tufSh)
        if (k != 2) rect(wx, base - h + u * 5.6f, u * 1.6f, u * 2.4f, win) }
    rect(x - u * 1f, base - h + u * 5f, u * 2f, u * 4f, c("#6A4A32"))
    if (lit > .01f) glow(x, base - h * .5f, u * 18f, c("#FFB04A", .2f * lit), BlendMode.Screen)
}

private fun DrawScope.angevine(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val hz = H * .46f
    sky(0f to c("#7FA8D8"), .45f to c("#BCD4EC"), .8f to c("#F6DCC0"), 1f to c("#FBE8CE"), y1 = hz)
    val sx = W * .22f; val sy = H * .26f
    glow(sx, sy, S * 1.1f, c("#FFE4B8", .45f), BlendMode.Screen); glow(sx, sy, S * .2f, c("#FFF4DC", .9f), BlendMode.Screen); disc(sx, sy, S * .04f, c("#FFF9EC"))
    for (i in 0 until 4) { val dx = ((t * (.4f + i * .1f) + i * .29f) % 1.3f - .15f) * W * 1.1f; softCloud(dx, H * (.06f + i * .065f), W * (.5f + i * .07f), c("#FFFFFF", .5f - i * .06f), c("#FFEBD6", .45f), 80 + i) }
    val nz = Nz(44)
    // Coteaux du Layon au loin, en plusieurs plans
    ridgeFill(ridgePts(hz - H * .02f, H * .035f, 80) { x -> .5f + .5f * nz.at(x * 3f, 3) }, vg(hz - H * .06f, hz, 0f to c("#AFB4CF"), 1f to c("#C9C3CC")), hz + H * .02f)
    val mid = ridgePts(hz + H * .02f, H * .05f, 80) { x -> .45f + .55f * nz.at(x * 4f + 5f, 3) }
    ridgeFill(mid, vg(hz - H * .03f, hz + H * .1f, 0f to c("#B6C089"), 1f to c("#8FA060")), hz + H * .12f)
    clipPath(Path().apply { moveTo(0f, hz + H * .12f); var i = 0; while (i < mid.size) { lineTo(mid[i], mid[i + 1]); i += 2 }; lineTo(W, hz + H * .12f); close() }) {
        // Parcelles de vigne : rangs obliques, quelques parcelles déjà dorées
        for (p in 0 until 6) { val x0 = W * (p / 6f - .05f); val gold = p % 3 == 1
            clipRect(x0, 0f, x0 + W / 6f + u, H) { for (k in -10..30) line(x0 + k * u * 1.6f, hz + H * .12f, x0 + k * u * 1.6f + W * .14f, hz - H * .04f, max(1f, u * .3f), if (gold) c("#C9A24A", .55f) else c("#5E7448", .45f)) } }
    }
    // Le Layon, ses peupliers et le moulin cavier sur la butte
    val river = hz + H * .1f
    conifers(26, 12, u * 7f, u * 11f, c("#6E8A4E"), { river }, 0f, W)
    fill(Path().apply { moveTo(0f, river); cubicTo(W * .3f, river - u * 1.5f, W * .55f, river + u * 2.5f, W, river + u * .5f); lineTo(W, river + u * 1.6f); cubicTo(W * .55f, river + u * 3.6f, W * .3f, river - u * .4f, 0f, river + u * 1.2f); close() }, c("#CFE2F0"))
    cavier(W * .74f, hz + H * .02f, u * .9f, t * TAU * 4f)
    // Logis de tuffeau au milieu des vignes, cèdre derrière
    val mb = hz + H * .14f
    crown(W * .14f, mb - u * 16f, u * 10f, u * 9f, 33, c("#6E8A5A"), c("#3E5A3A"), c("#22382A"), -.6f, -.8f)
    manoir(W * .36f, mb, u, 0f)
    canopy(6, 51, u * 2f, u * 3.6f, c("#6E8A55"), c("#9FB574"), false, { mb + u * .5f }, W * .02f, W * .2f)
    canopy(5, 52, u * 2f, u * 3.6f, c("#6E8A55"), c("#9FB574"), false, { mb + u * .5f }, W * .56f, W * .98f)
    // Vignoble au premier plan : rangs qui fuient vers le logis, feuilles vertes, dorées et rousses
    val vx = W * .5f; val vy = mb + u * 1f
    drawRect(vg(vy, H, 0f to c("#C9A87A"), 1f to c("#9A7A52")), Offset(0f, vy), Size(W, H - vy))
    val g = Mul(3)
    val rowCol = listOf(c("#7E9A44"), c("#9AA84E"), c("#C9A24A"), c("#6E8A3E"), c("#B8762E"), c("#8FA048"), c("#D9AE4A"))
    for (k in -12..12) {
        val bx = vx + k * W * .16f
        val steps = 70
        for (j in 0 until steps) {
            val f0 = j / steps.toFloat(); val f = f0 * f0
            val y = vy + (H - vy) * f; val x = vx + (bx - vx) * f
            val sz = u * (.4f + 5.5f * f)
            if (x < -sz * 2 || x > W + sz * 2) continue
            val base0 = rowCol[(k + 12) % rowCol.size]; val col = mix(base0, c("#E8C25A"), g() * .25f)
            val sway = sin(t * TAU * 2 + j * .3f + k) * sz * .08f
            // Haie de feuillage : ombre au pied, masse, puis touffes éclairées par le soleil (à gauche)
            ellGlow(x, y, sz * 1.1f, sz * .25f, c("#4A3A22", .35f))
            disc(x + sway, y - sz * .6f, sz * .8f, mix(col, c("#2E3E1E"), .35f))
            disc(x + sway - sz * .25f, y - sz * .85f, sz * .55f, col)
            disc(x + sway - sz * .4f, y - sz * 1.05f, sz * .28f, mix(col, c("#FFF2C0"), .3f))
            if (j % 5 == 0 && f > .2f) line(x, y, x, y - sz * 1.5f, max(1f, sz * .08f), c("#6A5A48"))
            if (f > .35f && g() < .12f) { val gx = x + (g() - .5f) * sz; val gy = y - sz * .2f; for (m in 0 until 6) disc(gx + (m % 3 - 1) * sz * .12f, gy + (m / 3) * sz * .12f, sz * .08f, c("#E8D27A")) }
        }
        // Rosier au bout du rang
        val ex = vx + (bx - vx); if (ex > -u * 6f && ex < W + u * 6f) { for (m in 0 until 7) disc(ex + (g() - .5f) * u * 5f, H - u * (3f + g() * 5f), u * (1.2f + g()), if (m % 2 == 0) c("#E8607A") else c("#F49AAE")); disc(ex, H - u * 2f, u * 2f, c("#4E6A32")) }
    }
    // Étourneaux en vol
    for (i in 0 until 7) { val k = (t * 1.5f + i * .13f) % 1f; val x = W * (1.1f - k * 1.3f); val y = H * (.16f + (i % 3) * .03f) + sin(k * TAU * 2 + i) * u; val s = u * .9f; val fl = sin(t * TAU * 18 + i) * .5f
        drawPath(Path().apply { moveTo(x - s * 1.4f, y - s * (.4f + fl)); quadraticTo(x - s * .5f, y - s * .2f, x, y + s * .2f); quadraticTo(x + s * .5f, y - s * .2f, x + s * 1.4f, y - s * (.4f + fl)) }, c("#3A3F4A", .7f), style = Stroke(max(1f, u * .28f))) }
    mist(hz + H * .1f, u * 4f, c("#FFF4E6", .35f))
}
