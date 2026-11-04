private fun DrawScope.mistforest(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H)
    val sun = Offset(W * .7f, H * .2f)
    if (lighting == null) {
    mistforestSky(0f to c("#C9D9D2"), .3f to c("#DDE8E0"), .55f to c("#EEF2E8"), 1f to c("#B9CDBF"))
    glow(sun.x, sun.y, S * 1.1f, c("#FFF7E0", .7f), BlendMode.Screen); glow(sun.x, sun.y, S * .1f, c("#FFFFFF", .9f), BlendMode.Screen)
    } else mistforestLiveSky(lighting, t, H * .5f)
    val tones = listOf(c("#BFD1C5"), c("#A2BCAB"), c("#7E9E8A"), c("#5B7F6A"), c("#3C5E4C"), c("#1F3A30"))
    for ((k, col) in tones.withIndex()) {
        val d = k / 5f; val nz = Nz(40 + k); val base = H * (.42f + d * .42f); val amp = H * (.06f - d * .02f)
        val line = { x: Float -> base - amp * (nz.at(x / W * 2.2f, 3) * .5f + .5f) }
        conifers((24 - k * 2.5f).toInt(), 50 + k, H * (.05f + d * .1f), H * (.1f + d * .16f), col, line)
        ridgePts(base, amp, 80) { u -> nz.at(u * 2.2f, 3) * .5f + .5f }.let { ridgeFill(it, col) }
        mist(base - amp * .3f, H * (.08f - d * .03f), c("#F4F8F0", .65f - d * .35f))
        if (k == 1 && (lighting?.night ?: 0f) < .1f) rays(sun.x, sun.y, H * .9f, 8, 115f, 50f, c("#FFFBEA", .14f), 3, t)
    }
    // A narrow trail, mossy stones and near trunks establish scale in the forest clearing.
    val trail=Path().apply{moveTo(W*.54f,H*.785f);cubicTo(W*.7f,H*.84f,W*.25f,H*.88f,W*.33f,H)
        lineTo(W*.73f,H);cubicTo(W*.52f,H*.88f,W*.76f,H*.84f,W*.57f,H*.785f);close()}
    drawPath(trail,vg(H*.78f,H,0f to c("#94AA8D"),1f to c("#667D66")))
    for(i in 0..6){val x=W*(.22f+(i*.17f)%.7f);val y=H*(.89f+(i%3)*.035f);val r=S*(.018f+(i%2)*.011f)
        fill(poly(x-r,y,x-r*.4f,y-r*.6f,x+r*.5f,y-r*.7f,x+r,y,x,y+r*.2f),c("#526A60"))
        line(x-r*.4f,y-r*.6f,x+r*.4f,y-r*.65f,S*.003f,c("#A3B18A",.6f))}
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
