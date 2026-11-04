private fun DrawScope.savanna(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val hz = H * .66f
    val sx = lighting?.let { size.width * (.12f + .76f * it.sun) } ?: W * .5f; val sy = hz - H * .08f
    if (lighting == null) {
    savannaSky(0f to c("#4A2A6A"), .35f to c("#C8507A"), .65f to c("#FF8A4A"), 1f to c("#FFD27A"), y1 = hz)

    glow(sx, sy, S * 1.2f, c("#FF9A4A", .55f), BlendMode.Screen); disc(sx, sy, S * .22f, c("#FFC46A", .95f)); disc(sx, sy, S * .2f, c("#FFE09A"))
    for (b in 0 until 4) rect(0f, sy + S * (.04f + b * .045f), W, S * (.006f + b * .003f), c("#FF8A4A", .5f))
    for (i in 0 until 3) softCloud(((t * .4f + i * .4f) % 1.3f - .15f) * W * 1.1f, H * (.14f + i * .08f), W * .7f, c("#8A3A6A", .35f), c("#FFB07A", .3f), 90 + i)
    } else savannaLiveSky(lighting, t, H * .66f)
    val nz = Nz(8)
    ridgeFill(ridgePts(hz, H * .02f, 60) { x -> .5f + .5f * nz.at(x * 4f, 3) }, c("#7A3A3A"))
    drawRect(vg(hz, H, 0f to c("#5A2A22"), 1f to c("#1E0E0C")), topLeft = Offset(0f, hz), size = Size(W, H - hz))
    val dark = if(lighting==null)c("#1A0C10") else c("#594D37")
    // Acacias en ombrelle
    fun acacia(x: Float, yb: Float, h: Float) {
        castShadow(x,yb,h*.18f,h*.8f)
        line(x, yb, x - h * .05f, yb - h * .6f, max(1f, h * .05f), dark); line(x - h * .05f, yb - h * .6f, x - h * .3f, yb - h * .85f, max(1f, h * .03f), dark); line(x - h * .05f, yb - h * .6f, x + h * .25f, yb - h * .88f, max(1f, h * .03f), dark)
        drawOval(dark, Offset(x - h * .55f, yb - h * 1.02f), Size(h * 1.1f, h * .22f)); drawOval(dark, Offset(x - h * .35f, yb - h * 1.1f), Size(h * .7f, h * .16f))
        drawArc(c("#A39958",.48f),185f,150f,false,Offset(x-h*.48f,yb-h*1.02f),Size(h*.95f,h*.16f),style=Stroke(h*.018f))
    }
    acacia(W * .2f, hz + u * 2f, H * .18f); acacia(W * .88f, hz + u, H * .1f); acacia(W * .42f, hz, H * .06f)
    // Girafes qui marchent lentement
    fun giraffe(x: Float, yb: Float, s: Float, ph: Float) {
        castShadow(x,yb,s*.3f,s*.9f)
        val st = sin(ph) * s * .08f
        for ((lx, o) in listOf(-.25f to 0f, -.1f to 1f, .2f to 2f, .32f to 3f)) { val sw = sin(ph + o) * s * .06f; line(x + lx * s, yb - s * .55f, x + lx * s + sw, yb, max(1f, s * .045f), dark) }
        drawOval(dark, Offset(x - s * .35f, yb - s * .72f), Size(s * .75f, s * .28f))
        for(i in 0..4)disc(x+s*(-.2f+i*.1f),yb-s*(.6f+(i%2)*.04f),s*.025f,c("#BD925D",.55f))
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
