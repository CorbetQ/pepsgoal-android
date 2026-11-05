private fun DrawScope.autumnwood(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H); val vy = H * .6f
    val sun = Offset(W * .55f, H * .5f)
    if (lighting == null) {
    autumnwoodSky(0f to c("#F2A65A"), .3f to c("#FFCB8A"), .55f to c("#FFE6BC"), 1f to c("#F6C27E"))
    glow(sun.x, sun.y, S * 1.2f, c("#FFE7B0", .7f), BlendMode.Screen); glow(sun.x, sun.y, S * .25f, c("#FFF6DE", .8f), BlendMode.Screen)
    } else autumnwoodLiveSky(lighting, t, H * .6f)
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
            crown(x, base - h, rx, rx * (.9f + r() * .3f), 700 + k * 30 + i, mix(tc, c("#FFE9A8"), .5f), tc, mix(tc, c("#5A1A08"), .35f + d * .2f), -.5f, -.85f, groundY=base)
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
