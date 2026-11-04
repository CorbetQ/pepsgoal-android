private fun DrawScope.icefield(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val hz = H * .5f
    if (lighting == null) {
    icefieldSky(0f to c("#9EC9F2"), .6f to c("#D6EAFA"), 1f to c("#FCE8E0"), y1 = hz)
    glow(W * .7f, hz - H * .04f, S * .9f, c("#FFE8D6", .7f), BlendMode.Screen)
    softCloud(W * .3f + sin(t * TAU) * u * 3f, H * .16f, W * .7f, c("#FFFFFF", .7f), c("#FFFFFF", .6f), 6)
    } else icefieldLiveSky(lighting, t, H * .5f)
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
