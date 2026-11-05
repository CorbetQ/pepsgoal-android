private fun DrawScope.summerbeach(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H); val hz = H * .5f
    val sun = Offset(W * .7f, H * .2f)
    if (lighting == null) {
    summerbeachSky(0f to c("#3FA8EA"), .55f to c("#8FD3F7"), .9f to c("#D6F1F6"), 1f to c("#FFF2CC"), y1 = hz + 2f)
    glow(sun.x, sun.y, S * 1f, c("#FFF2B0", .55f), BlendMode.Screen); glow(sun.x, sun.y, S * .12f, c("#FFFFFF", .95f), BlendMode.Screen)
    softCloud(W * .2f + t * W * .05f, H * .3f, W * .6f, c("#FFFFFF", .7f), c("#FFFFFF", .6f), 2)
    } else summerbeachLiveSky(lighting, t, H * .5f)
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
