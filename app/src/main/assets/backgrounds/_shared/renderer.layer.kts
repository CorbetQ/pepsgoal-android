/*
 * Arrière-plans illustrés de la boutique (réglage « bg » = « s:<clé> ») et fonds gratuits (Backdrop.kt) : des scènes
 * peintes sur Canvas en couches — perspective atmosphérique, lumière directionnelle, brume, grain — animées très
 * lentement (horloge économe de Backdrop.kt, lue dans la phase de dessin). Le même dessin sert d'aperçu miniature dans
 * la boutique : tout est proportionnel à la taille disponible.
 */

/** Scène illustrée [key] en plein cadre ; [still] fige l'animation (aperçus de la boutique). */


/**
 * Moment de la journée à l'heure [h] : poids de la nuit, de l'aube, du jour et du crépuscule (somme 1), et course du
 * soleil [sun] (0 au lever, 1 au coucher). Lever et coucher suivent la saison (heures légales en France).
 */
private class Tod(val night: Float, val dawn: Float, val day: Float, val dusk: Float, val sun: Float, val clouds: Float = 0f, val condition: String = "clear", val wind: Float = 0f) {
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
/** Fallback lighting for future catalog entries; existing natural scenes paint their own lit surfaces. */
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
/** Palette changes are applied to each surface before drawing, preserving the scene's layered silhouettes. */
private fun surfaceColor(hex: String, alpha: Float, light: Tod?): Color {
    val base = c(hex, alpha)
    if (light == null) return base
    val night = Color(base.red * .30f + .035f, base.green * .36f + .045f, base.blue * .58f + .1f, base.alpha)
    val warm = mix(base, c("#FFAA81", alpha), light.dawn * .12f + light.dusk * .2f)
    val lit = mix(warm, night, light.night * .78f)
    val gray = (lit.red + lit.green + lit.blue) / 3
    return mix(lit, Color(gray * .9f, gray * .96f, gray, alpha), light.clouds / 100f * .07f)
}

/** A layered cartoon sky; deterministic shapes, no radial shaders, sun and moon cannot coexist at noon. */
private fun DrawScope.cartoonSky(d: Tod, t: Float, horizon: Float, mood: String = "") {
    val w = size.width; val h = horizon.coerceAtLeast(size.height * .15f); val unit = min(w, size.height)
    val accent = when(mood) {
        "sakura" -> "#F6D1E4"
        "dunes", "savanna", "canyon" -> "#E9BE9F"
        "mistforest", "bamboo" -> "#BBD9CA"
        "snowpeak", "icefield" -> "#CFDEEF"
        "autumnwood", "vineyard", "angevine" -> "#F4DCAC"
        else -> "#C6E8EB"
    }
    val top = mix(d.col("#62AFE3", "#827DBA", "#514F91", "#10182F"), c(accent), d.day * .1f)
    val low = mix(d.col("#D8F0EE", "#FFD6B6", "#FFBE83", "#30456A"), c(accent), d.day * .35f)
    val cover = max((d.clouds / 100f).coerceIn(0f,1f), when(d.condition){"storm"->.86f;"rain","snow"->.6f;"fog"->.65f;else->0f})
    val cloudy = d.col("#91A8BD", "#A78CA8", "#84608D", "#172943")
    drawRect(Brush.verticalGradient(listOf(mix(top,cloudy,cover*.55f),mix(low,cloudy,cover*.45f))), size=Size(w,h))
    val warm = d.dawn + d.dusk
    if (warm > .02f) glow(w * (.12f + .76f*d.sun),h*.9f,w*.9f,c("#FF974B",warm*.55f),BlendMode.Screen)
    if (d.night > .05f) {
        starField(93, 42, h * .75f, c("#FFF5D8", d.night * (1 - cover) * .85f), 0f, 0)
        val moon = Offset(w * .78f, h * .23f); val r = unit * .038f
        glow(moon.x,moon.y,r*4f,c("#80B7FF",d.night*(1-cover)*.25f),BlendMode.Screen)
        disc(moon.x, moon.y, r, c("#F1EBCD", d.night * (1 - cover * .9f)))
        disc(moon.x - r * .22f, moon.y + r * .2f, r * .18f, c("#C1C7C7", d.night * .5f * (1 - cover)))
        disc(moon.x + r * .3f, moon.y - r * .25f, r * .12f, c("#C1C7C7", d.night * .4f * (1 - cover)))
    }
    val sunAlpha = (1 - d.night) * (1 - cover * .95f)
    if (sunAlpha > .02f) {
        val x = w * (.12f + .76f * d.sun)
        val y = h * (.88f - .65f * sin(d.sun * 3.14159f))
        val r = unit * .045f
        glow(x,y,r*9f,c("#FFD086",sunAlpha*.27f),BlendMode.Screen)
        glow(x,y,r*2f,c("#FFF1C7",sunAlpha*.7f),BlendMode.Screen)
        if (cover < .6f) rays(x,y,h*.95f,9,95f,100f,c("#FFE1A8",sunAlpha*.12f),91,t)
        // Sun rays and a luminous, sharply drawn centre.
        if (cover < .35f) for (i in 0 until 8) {
            val a = i * TAU / 8
            line(x + cos(a) * r * 1.45f, y + sin(a) * r * 1.45f, x + cos(a) * r * 1.7f, y + sin(a) * r * 1.7f, r * .12f, c("#FFD077", sunAlpha * .7f))
        }
        disc(x, y, r, c("#FFD077", sunAlpha))
        disc(x - r * .1f, y - r * .12f, r * .78f, c("#FFF0BC", sunAlpha))
    }
    val count = 2 + (cover * 6).toInt()
    for (i in 0 until count) {
        val depth = i % 3
        val width = w * (.32f + depth * .13f)
        val speed = .03f + d.wind / 1800f
        val x = ((i * .371f + t * speed + .15f) % 1.3f - .15f) * w
        val y = h * (.22f + depth * .18f + (i % 2) * .055f)
        val gray = d.col("#9AAAB5", "#A899AC", "#927E96", "#35455E")
        val body = mix(d.col("#F8FAEE", "#F3C4BD", "#DBA3B4", "#4C6080"), gray, cover * .5f)
        val shade = mix(d.col("#BDD6DF", "#C5A6BE", "#A07F9D", "#354966"), gray, cover * .6f)
        softCloud(x, y, width, shade.copy(alpha = .75f + cover * .2f), body.copy(alpha = .85f), 700 + i)
    }
}

/** Scènes qui gèrent elles-mêmes l'heure (au lieu du voile générique). */
private val OWN_TOD = setOf("mountains", "mistforest", "dunes", "sakura", "lagoon", "snowpeak", "eggmeadow", "flowerfield", "summerbeach", "autumnwood", "vineyard", "balloons", "bamboo", "canyon", "savanna", "icefield", "angevine", "loiresummer", "tram", "loire", "lighthouse", "angers", "rooftops", "nantes")

/** Une image de la scène [key] à la phase [t] ; [h] : heure réelle (null : éclairage d'origine, aperçus). */
internal fun DrawScope.scene(key: String, t: Float, h: Float? = null, environment: fr.pepsgoal.core.SceneEnvironment? = null) {
    withBackgroundStyle(key, environment?.sun ?: .25f, environment?.night ?: 0f) { paintScene(key, t, h, environment) }
}
private fun DrawScope.paintScene(key: String, t: Float, h: Float?, environment: fr.pepsgoal.core.SceneEnvironment?) {
    val td = if (!backgroundStyle(key).capability.light) null else environment?.let {
        Tod(it.night, it.dawn, it.day, it.dusk, it.sun, it.clouds, it.condition, it.wind)
    } ?: h?.let { tod(it) }
    when (key) {
        "nebula" -> nebula(t); "deepsea" -> deepsea(t); "mountains" -> mountains(t, td); "citynight" -> citynight(t); "tram" -> tram(t, td)
        "mistforest" -> mistforest(t, td); "dunes" -> dunes(t, td); "sakura" -> sakura(t, td); "magma" -> magma(t); "coderain" -> coderain(t)
        "borealis" -> borealis(t); "rainwindow" -> rainwindow(t, environment); "planet" -> planet(t); "lagoon" -> lagoon(t, td); "haunted" -> haunted(t)
        "snowvillage" -> snowvillage(t); "fireworks" -> fireworks(t); "snowpeak" -> snowpeak(t, td); "heartsky" -> heartsky(t)
        "eggmeadow" -> eggmeadow(t, td); "flowerfield" -> flowerfield(t, td); "summerbeach" -> summerbeach(t, td); "autumnwood" -> autumnwood(t, td)
        "tunnel" -> tunnel(t); "loire" -> loire(t, td); "lighthouse" -> lighthouse(t, td); "vineyard" -> vineyard(t, td); "balloons" -> balloons(t, td); "jellyfish" -> jellyfish(t)
        "bamboo" -> bamboo(t, td); "nighttrain" -> nighttrain(t); "rooftops" -> rooftops(t, td); "canyon" -> canyon(t, td)
        "savanna" -> savanna(t, td); "lanterns" -> lanterns(t); "icefield" -> icefield(t, td)
        "angers" -> angers(t, td); "angevine" -> angevine(t, td); "nantes" -> nantes(t, td)
        "xmastree" -> xmastree(t); "valentine" -> valentine(t); "loiresummer" -> loiresummer(t, td)
        "kopa" -> kopa(t); "ecran" -> ecran(t)
        else -> {
            val sky = ALL_BGS.firstOrNull { it.key == key }?.sky ?: listOf("#1A1240", "#07081A")
            drawRect(Brush.verticalGradient(sky.map { c(it) }))
        }
    }
    val dark = ALL_BGS.firstOrNull { it.key == key }?.dark ?: true
    if (td != null && !dark && key !in OWN_TOD) grade(td)
    sceneAccents(key, t, td)
    finish(dark || (td?.night ?: 0f) > .5f, if (key == "tram") .6f else 1f)
}

/** Small scene-specific foregrounds frame the illustration without covering the central interface. */


/** Durée d'un cycle d'animation par scène (ms) : les scènes à mouvement visible (poissons, pluie) tournent plus vite. */
/** Images par seconde : les scènes très détaillées et presque immobiles se contentent de moins (batterie). */

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

// Only catalog/literal colors enter this cache. RGB parsing is shared across frames and render workers.
private val SCENE_RGB = java.util.concurrent.ConcurrentHashMap<String, Color>()
private fun c(h: String, a: Float = 1f): Color {
    val raw = SCENE_RGB.getOrPut(h) {
        val v = h.removePrefix("#").toLong(16)
        Color(((v shr 16) and 0xFF) / 255f, ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f)
    }
    val style = currentBackgroundStyle()
    return pigment(raw, style).copy(alpha=a.coerceIn(0f,1f))
}
private fun pigment(raw: Color,style: BackgroundStyle): Color {
    val luma=raw.red*.2126f+raw.green*.7152f+raw.blue*.0722f
    fun channel(v: Float)=((luma+(v-luma)*style.saturation-.5f)*style.contrast+.5f).coerceIn(0f,1f)
    return Color(channel(raw.red),channel(raw.green),channel(raw.blue),raw.alpha)
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
    val strength = currentBackgroundStyle().lightStrength
    val alpha = (col.alpha * strength).coerceIn(0f, 1f)
    val col = mix(col,c(currentBackgroundStyle().lightColor,col.alpha),.08f)
    drawCircle(Brush.radialGradient(0f to col.copy(alpha=alpha), .28f to col.copy(alpha=alpha*.58f),
        .68f to col.copy(alpha=alpha*.17f), 1f to col.copy(alpha=0f), center=Offset(x,y),radius=r),
        r, Offset(x,y), blendMode=blend)
}
/** Colored ground contact and long cast shadow, following the live sun position. */
private fun DrawScope.castShadow(x: Float,y: Float,width: Float,length: Float) {
    val style=currentBackgroundStyle();val dir=currentShadowDirection()
    val dx=dir*length*3f;val dy=length*(.18f+abs(dir)*.22f)
    val alpha=(.3f*style.shadowStrength*currentShadowOpacity()).coerceIn(0f,.6f)
    val shape=Path().apply {
        moveTo(x-width,y);lineTo(x+width,y)
        cubicTo(x+dx+width*.75f,y+dy*.5f,x+dx+width*.8f,y+dy,x+dx,y+dy)
        cubicTo(x+dx-width*.8f,y+dy,x+dx-width,y+dy*.5f,x-width,y);close()
    }
    drawPath(shape,Brush.linearGradient(listOf(c(style.shadowColor,alpha),c(style.shadowColor,alpha*.12f)),Offset(x,y),Offset(x+dx,y+dy)))
    drawOval(c(style.shadowColor,alpha*.65f),Offset(x-width*.7f,y-width*.08f),Size(width*1.4f,width*.25f))
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
private fun DrawScope.conifers(n: Int,seed: Int,h0: Float,h1: Float,col: Color,base: (Float)->Float,x0: Float=0f,x1: Float=size.width) {
    val random=Mul(seed);val silhouettes=Path();val faces=Path();val trunks=Path()
    for(i in 0 until n){
        val x=x0+(x1-x0)*(i+random()*.85f)/n;val height=h0+(h1-h0)*random();val y=base(x)+height*.05f
        val width=height*(.19f+random()*.09f);val lean=(random()-.5f)*width*.12f
        val tree=silhouettes;tree.moveTo(x+lean,y-height)
        val right=faces;right.moveTo(x+lean,y-height)
        for(tier in 1..5){val f=tier/5f;val yy=y-height+height*f*.86f
            val spread=width*f*(.48f+random()*.07f)
            tree.lineTo(x+spread,yy-height*.055f);tree.lineTo(x+spread*.45f,yy)
            right.lineTo(x+spread,yy-height*.055f);right.lineTo(x+spread*.45f,yy)}
        tree.lineTo(x+width*.08f,y);tree.lineTo(x-width*.08f,y)
        for(tier in 5 downTo 1){val f=tier/5f;val yy=y-height+height*f*.86f
            tree.lineTo(x-width*f*.25f,yy);tree.lineTo(x-width*f*.54f,yy-height*.06f)}
        tree.close()
        right.lineTo(x,y);right.close()
        trunks.moveTo(x,y-height*.12f);trunks.lineTo(x,y)
    }
    drawPath(silhouettes,Brush.verticalGradient(listOf(mix(col,Color(0xFFB9CFB4),.12f),col,mix(col,Color(0xFF152B3A),.24f))))
    drawPath(faces,mix(col,Color(0xFF172D46),.35f).copy(alpha=.42f))
    drawPath(trunks,mix(col,Color(0xFFB9A68A),.25f),style=Stroke(max(.6f,min(size.width,size.height)*.0012f)))
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
private fun DrawScope.crown(x: Float, y: Float, rx: Float, ry: Float, seed: Int, lit: Color, mid: Color, dark: Color, lx: Float = -.6f, ly: Float = -.8f, groundY: Float? = null) {
    if (groundY != null) castShadow(x,groundY,rx*.45f,max(ry,groundY-y)*1.2f)
    val p = lobed(x, y, rx, ry, seed, 7, .16f)
    drawPath(p, Brush.linearGradient(0f to mix(lit,Color(0xFFFFE8B0),.2f), .38f to mid, 1f to mix(dark,Color(0xFF1C2356),.3f), start = Offset(x + lx * rx, y + ly * ry), end = Offset(x - lx * rx, y - ly * ry)))
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

/** Nuage cartoon à silhouette lobée : base ombrée et dessus clair, sans flou. */
private fun DrawScope.softCloud(x: Float, y: Float, w: Float, body: Color, light: Color, seed: Int) {
    val h = w * .14f
    val outline = lobed(x, y, w * .48f, h, seed, 7, .16f, .35f)
    val style=currentBackgroundStyle()
    drawPath(outline, Brush.verticalGradient(listOf(light.copy(alpha=.92f),body.copy(alpha=.9f)),y-h,y+h))
    clipPath(outline) {
        drawOval(Color(0xFF343B73).copy(alpha=.19f*style.shadowStrength),Offset(x-w*.45f,y+h*.2f),Size(w,h*1.3f))
        drawOval(light.copy(alpha=.7f),Offset(x-w*.4f,y-h*1.35f),Size(w*.75f,h*1.1f))
    }
}

/** Finition : grain fin (matière) et vignettage doux. */
private val GRAIN = Mul(17).let { r -> List(220) { Offset(r(), r()) } }
private fun DrawScope.finish(dark: Boolean,vignette: Float=1f) {
    when(currentBackgroundStyle().id) {
        "nebula" -> nebulaFinish(dark,vignette)
        "deepsea" -> deepseaFinish(dark,vignette)
        "mountains" -> mountainsFinish(dark,vignette)
        "citynight" -> citynightFinish(dark,vignette)
        "tram" -> tramFinish(dark,vignette)
        "mistforest" -> mistforestFinish(dark,vignette)
        "dunes" -> dunesFinish(dark,vignette)
        "sakura" -> sakuraFinish(dark,vignette)
        "magma" -> magmaFinish(dark,vignette)
        "coderain" -> coderainFinish(dark,vignette)
        "borealis" -> borealisFinish(dark,vignette)
        "rainwindow" -> rainwindowFinish(dark,vignette)
        "planet" -> planetFinish(dark,vignette)
        "lagoon" -> lagoonFinish(dark,vignette)
        "loire" -> loireFinish(dark,vignette)
        "lighthouse" -> lighthouseFinish(dark,vignette)
        "vineyard" -> vineyardFinish(dark,vignette)
        "balloons" -> balloonsFinish(dark,vignette)
        "jellyfish" -> jellyfishFinish(dark,vignette)
        "bamboo" -> bambooFinish(dark,vignette)
        "nighttrain" -> nighttrainFinish(dark,vignette)
        "rooftops" -> rooftopsFinish(dark,vignette)
        "canyon" -> canyonFinish(dark,vignette)
        "savanna" -> savannaFinish(dark,vignette)
        "lanterns" -> lanternsFinish(dark,vignette)
        "icefield" -> icefieldFinish(dark,vignette)
        "tunnel" -> tunnelFinish(dark,vignette)
        "angers" -> angersFinish(dark,vignette)
        "nantes" -> nantesFinish(dark,vignette)
        "angevine" -> angevineFinish(dark,vignette)
        "haunted" -> hauntedFinish(dark,vignette)
        "snowvillage" -> snowvillageFinish(dark,vignette)
        "fireworks" -> fireworksFinish(dark,vignette)
        "snowpeak" -> snowpeakFinish(dark,vignette)
        "heartsky" -> heartskyFinish(dark,vignette)
        "eggmeadow" -> eggmeadowFinish(dark,vignette)
        "flowerfield" -> flowerfieldFinish(dark,vignette)
        "summerbeach" -> summerbeachFinish(dark,vignette)
        "autumnwood" -> autumnwoodFinish(dark,vignette)
        "xmastree" -> xmastreeFinish(dark,vignette)
        "valentine" -> valentineFinish(dark,vignette)
        "loiresummer" -> loiresummerFinish(dark,vignette)
        "kopa" -> kopaFinish(dark,vignette)
        "ecran" -> ecranFinish(dark,vignette)
        "pop" -> popFinish(dark,vignette)
        "sky" -> skyFinish(dark,vignette)
        "blob" -> blobFinish(dark,vignette)
        "aurora" -> auroraFinish(dark,vignette)
        "waves" -> wavesFinish(dark,vignette)
        "stars" -> starsFinish(dark,vignette)
        "mesh" -> meshFinish(dark,vignette)
        "grid" -> gridFinish(dark,vignette)
        else -> materialFinish(dark,vignette)
    }
}
private fun DrawScope.materialFinish(dark: Boolean, vignette: Float) {
    val style=currentBackgroundStyle();val w=size.width;val h=size.height;val unit=min(w,h)
    val shadow=c(style.shadowColor,.24f*style.shadowStrength*vignette)
    drawRect(Brush.radialGradient(0f to Color.Transparent,.48f to Color.Transparent,1f to shadow,
        center=Offset(w*.48f,h*.35f),radius=max(w,h)*.82f))
    val texture=(style.textureStrength*.12f).coerceIn(0f,.18f)
    for ((i,p) in GRAIN.withIndex()) {
        val x=p.x*w;val y=p.y*h
        val col=if(i%3==0)Color.White.copy(alpha=texture*.7f)else c(style.shadowColor,texture)
        when(style.texture) {
            "water" -> line(x,y,x+unit*(.004f+(i%4)*.003f),y,max(.5f,unit*.0012f),col)
            "stone","sand","wood" -> line(x,y,x+unit*.006f,y-unit*.003f,max(.5f,unit*.0012f),col)
            else -> disc(x,y,max(.5f,unit*(.0008f+(i%3)*.0003f)),col)
        }
    }
}

/* ------------------------------------------------------------------ Montagnes à l'heure dorée */


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


/* ------------------------------------------------------------------ Fonds marins */


/* ------------------------------------------------------------------ Ville la nuit (au bord du fleuve) */


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


/* ------------------------------------------------------------------ Lagon */
private fun DrawScope.palm(x: Float, y: Float, h: Float, lean: Float, t: Float, seed: Int, trunk: Color, frond: Color, frondLit: Color) {
    castShadow(x,y,h*.09f,h*.65f)
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


/* ------------------------------------------------------------------ Manoir hanté */


/* ------------------------------------------------------------------ Bois d'automne */


/* ------------------------------------------------------------------ Forêt brumeuse */


/* ------------------------------------------------------------------ Dunes au couchant */


/* ------------------------------------------------------------------ Cerisiers en fleurs */


/* ------------------------------------------------------------------ Magma */


/* ------------------------------------------------------------------ Pluie de code */


/* ------------------------------------------------------------------ Pluie sur la vitre */


/* ------------------------------------------------------------------ Planète géante à anneaux */


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


/* ------------------------------------------------------------------ Feux d'artifice */


/* ------------------------------------------------------------------ Montagne d'hiver */


/* ------------------------------------------------------------------ Ciel de cœurs */


/* ------------------------------------------------------------------ Prairie de Pâques */


/* ------------------------------------------------------------------ Champ fleuri */


/* ------------------------------------------------------------------ Plage d'été */


/* ------------------------------------------------------------------ Fonds gratuits : dégradés « mesh » aux couleurs du thème */

/**
 * Palette d'un fond gratuit, dérivée du thème : [cols] = 4 couleurs d'ambiance (souvent très saturées), adoucies ici
 * (pastel en clair, profondes en sombre) pour un rendu mesh contemporain ; [al] = intensité voulue par le thème.
 */
internal class FreePal(val dark: Boolean, val bg: Color, val ink: Color, cols: List<Color>, val al: Float, private val style: BackgroundStyle = backgroundStyle("pop")) {
    private fun soft(col: Color): Color {
        val l = col.red * .3f + col.green * .59f + col.blue * .11f
        val gray = Color(l, l, l)
        val desat = pigment(mix(col, gray, if (dark) .02f else .03f),style)
        return if (dark) mix(desat, bg, .1f) else mix(desat, Color.White, .06f)
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
private fun DrawScope.freeFinish(fp: FreePal) { finish(fp.dark, 0f) }

internal fun DrawScope.freeBg(kind: String, fp: FreePal, t: Float) {
    withBackgroundStyle(kind) { paintFreeBg(kind,fp,t) }
}
private fun DrawScope.paintFreeBg(kind: String, fp: FreePal, t: Float) {
    when (kind) {
        "aurora" -> freeAurora(fp, t); "waves" -> freeWaves(fp, t); "stars" -> freeStars(fp, t)
        "mesh" -> freeMesh(fp, t); "grid" -> freeGrid(fp, t); else -> freeBlob(fp, t)
    }
    freeFinish(fp)
}

/** Bold paper-cut shapes; six distinct compositions, all still derived from the user's palette. */







/** Profil du tunnel (l, h) : sol, paroi gauche, voûte, paroi droite, banquette d'évacuation à droite. */




/* ------------------------------------------------------------------ Loire : bords de Loire en Anjou, fin d'après-midi */

/** Village de tuffeau et château (Saumur) sur la rive d'en face, posé sur la ligne [y]. */




/* ------------------------------------------------------------------ Phare dans la nuit */


/* ------------------------------------------------------------------ Vignes d'Anjou au couchant */


/* ------------------------------------------------------------------ Montgolfières au lever du jour */



/* ------------------------------------------------------------------ Méduses lumineuses */


/* ------------------------------------------------------------------ Bambouseraie */


/* ------------------------------------------------------------------ Train de nuit sur le viaduc */


/* ------------------------------------------------------------------ Toits de Paris */

/** Profil de la tour Eiffel : demi-largeur (en fraction de la hauteur) selon la hauteur relative (0 au sol, 1 au sommet). */



/** Tour Eiffel : quatre piliers et grande arche, trois étages, fût effilé en treillis, antenne. [gap] : couleur vue à travers le treillis. */


/** Immeuble haussmannien vu de face : pierre de taille, balcons filants, corniche, combles en zinc et lucarnes, souche de cheminée. */




/* ------------------------------------------------------------------ Canyon */


/* ------------------------------------------------------------------ Savane au couchant */


/* ------------------------------------------------------------------ Lanternes célestes sur le lac */


/* ------------------------------------------------------------------ Banquise */


/* ------------------------------------------------------------------ Tramway d'Angers */

/** Château d'Angers : courtines et tours rondes de schiste rayées de tuffeau, sans toits, jardins au sommet. [a] : opacité (reflets). */


/** Cathédrale Saint-Maurice et maisons de la ville haute, sur la colline. */


/** Rame de tramway d'Angers (sans caténaire : alimentation par le sol), vue de profil, [L] : longueur. */




/* ------------------------------------------------------------------ Douceur angevine (Layon) */

/** Moulin cavier : cône de maçonnerie en tuffeau, hucherolle de bois et ailes qui tournent. */


/** Logis de tuffeau : toit d'ardoise très pentu, lucarnes à fronton, hautes cheminées, tour à toit en poivrière. */




/* ------------------------------------------------------------------ Nantes : l'Éléphant, la grue Titan et la Loire */

/** Grue Titan jaune du quai des Antilles : pylône en treillis, flèche horizontale, contre-flèche, cabine. */


/** Le Grand Éléphant des Machines de l'île : bois et acier, oreilles de cuir, trompe articulée, terrasse et passagers. */




/* ------------------------------------------------------------------ Scènes de saison animées (8.4) : en aplats */

/** Ciel en bandes d'aplats (haut → bas), sans dégradé. */
private fun DrawScope.bands(vararg cols: String, y1: Float = size.height) {
    val n = cols.size; val h = y1 / n
    cols.forEachIndexed { i, col -> rect(0f, i * h - 1f, size.width, h + 2f, c(col)) }
}

/** Étoile à cinq branches pleine. */
private fun star5(x: Float, y: Float, r: Float): Path {
    val p = Path()
    for (i in 0 until 10) { val a = -TAU / 4 + i * TAU / 10; val rr = if (i % 2 == 0) r else r * .45f
        val px = x + cos(a) * rr; val py = y + sin(a) * rr; if (i == 0) p.moveTo(px, py) else p.lineTo(px, py) }
    p.close(); return p
}

/* ------------------------------------------------------------------ Noël : sapin, guirlandes et neige */


/* ------------------------------------------------------------------ Saint-Valentin : cœurs qui montent */


/* ------------------------------------------------------------------ Été sur la Loire : soleil, vagues et coteaux */


/* ------------------------------------------------------------------ Stade Raymond-Kopa : soir de match à Angers (fond secret) */

/**
 * Un soir de match au stade d'Angers, en aplats : ciel d'encre et croissant de lune, tours rayées du château au loin,
 * quatre pylônes de projecteurs et leurs faisceaux pleins (translucides, sans halo), tribunes noir et blanc où la foule
 * fait la ola et agite écharpes et drapeaux, banderole rayée, pelouse tondue en bandes et ballon qui roule. Ni logo ni
 * inscription.
 */


/* ------------------------------------------------------------------ Écran géant : OL 4 – 0 SRFC (fond secret) */

/** Segments allumés (a à g, dans l'ordre haut, haut droit, bas droit, bas, bas gauche, haut gauche, milieu) par caractère. */


/** Un caractère d'afficheur à sept segments (aplats arrondis), de largeur [w] et de hauteur [h]. */


/** Écran géant sur deux lignes : « OL » et 4 (blanc), « SrFC » et 0 (rouge qui clignote), noms en ambre. */


/**
 * Afficheur à LED sur une ligne : « OL 4 – 0 SrFC » centré, noms en ambre, 4 en blanc, 0 en rouge qui clignote,
 * sur une trame de diodes éteintes. [frame] : cadre épais de l'écran géant.
 */


/**
 * Un soir de défaite à l'extérieur, en aplats : ciel d'orage, petite pluie, tribunes sombres et clairsemées, et un
 * écran géant à sept segments qui affiche sans pitié « OL 4 – 0 SRFC » et le temps écoulé. Ni logo ni inscription
 * autre que le score.
 */



/** Same sky painter used by the live theme and the deterministic development gallery. */




private fun DrawScope.sceneAccents(key: String,t: Float,light: Tod?) {
    when(key) {
        "nebula" -> nebulaForeground(t,light)
        "deepsea" -> deepseaForeground(t,light)
        "mountains" -> mountainsForeground(t,light)
        "citynight" -> citynightForeground(t,light)
        "tram" -> tramForeground(t,light)
        "mistforest" -> mistforestForeground(t,light)
        "dunes" -> dunesForeground(t,light)
        "sakura" -> sakuraForeground(t,light)
        "magma" -> magmaForeground(t,light)
        "coderain" -> coderainForeground(t,light)
        "borealis" -> borealisForeground(t,light)
        "rainwindow" -> rainwindowForeground(t,light)
        "planet" -> planetForeground(t,light)
        "lagoon" -> lagoonForeground(t,light)
        "loire" -> loireForeground(t,light)
        "lighthouse" -> lighthouseForeground(t,light)
        "vineyard" -> vineyardForeground(t,light)
        "balloons" -> balloonsForeground(t,light)
        "jellyfish" -> jellyfishForeground(t,light)
        "bamboo" -> bambooForeground(t,light)
        "nighttrain" -> nighttrainForeground(t,light)
        "rooftops" -> rooftopsForeground(t,light)
        "canyon" -> canyonForeground(t,light)
        "savanna" -> savannaForeground(t,light)
        "lanterns" -> lanternsForeground(t,light)
        "icefield" -> icefieldForeground(t,light)
        "tunnel" -> tunnelForeground(t,light)
        "angers" -> angersForeground(t,light)
        "nantes" -> nantesForeground(t,light)
        "angevine" -> angevineForeground(t,light)
        "haunted" -> hauntedForeground(t,light)
        "snowvillage" -> snowvillageForeground(t,light)
        "fireworks" -> fireworksForeground(t,light)
        "snowpeak" -> snowpeakForeground(t,light)
        "heartsky" -> heartskyForeground(t,light)
        "eggmeadow" -> eggmeadowForeground(t,light)
        "flowerfield" -> flowerfieldForeground(t,light)
        "summerbeach" -> summerbeachForeground(t,light)
        "autumnwood" -> autumnwoodForeground(t,light)
        "xmastree" -> xmastreeForeground(t,light)
        "valentine" -> valentineForeground(t,light)
        "loiresummer" -> loiresummerForeground(t,light)
        "kopa" -> kopaForeground(t,light)
        "ecran" -> ecranForeground(t,light)
        "pop" -> popForeground(t,light)
        "sky" -> skyForeground(t,light)
        "blob" -> blobForeground(t,light)
        "aurora" -> auroraForeground(t,light)
        "waves" -> wavesForeground(t,light)
        "stars" -> starsForeground(t,light)
        "mesh" -> meshForeground(t,light)
        "grid" -> gridForeground(t,light)
    }
}
