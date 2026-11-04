private fun DrawScope.vineyard(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val hz = H * .42f
    val sx = lighting?.let { size.width * (.12f + .76f * it.sun) } ?: W * .3f; val sy = hz - H * .03f
    if (lighting == null) {
    vineyardSky(0f to c("#5A6FA8"), .45f to c("#E8A27A"), .8f to c("#FFD49A"), 1f to c("#FFE8C0"), y1 = hz)

    glow(sx, sy, S * 1.2f, c("#FFB86B", .5f), BlendMode.Screen); glow(sx, sy, S * .2f, c("#FFF0D0", .95f), BlendMode.Screen); disc(sx, sy, S * .06f, c("#FFF6E2"))
    for (i in 0 until 3) softCloud(((t * .5f + i * .37f) % 1.3f - .15f) * W * 1.1f, H * (.12f + i * .07f), W * .6f, c("#F7B48E", .35f), c("#FFE2C2", .35f), 70 + i)
    } else vineyardLiveSky(lighting, t, H * .42f)
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
