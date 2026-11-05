private fun DrawScope.eggmeadow(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H)
    if (lighting == null) {
    eggmeadowSky(0f to c("#7FCBF2"), .35f to c("#CFF1FF"), .55f to c("#F2FCFF"))
    glow(W * .2f, H * .12f, S * .8f, c("#FFF8DC", .6f), BlendMode.Screen)
    softCloud(W * .6f + t * W * .05f, H * .2f, W * .7f, c("#FFFFFF", .7f), c("#FFFFFF", .6f), 3)
    softCloud(W * .2f + t * W * .03f, H * .33f, W * .5f, c("#FFFFFF", .6f), c("#FFFFFF", .5f), 8)
    } else eggmeadowLiveSky(lighting, t, H * .52f)
    // Collines (perspective atmosphérique)
    val tones = listOf(c("#BFE3B0"), c("#9CD484"), c("#7CC25E"), c("#5FA846"))
    for ((k, col) in tones.withIndex()) {
        val nz = Nz(30 + k); val base = H * (.52f + k * .08f)
        ridgePts(base, H * .07f, 80) { u -> nz.at(u * 1.6f + k, 3) * .5f + .5f }.let { ridgeFill(it, vg(base - H * .07f, H, 0f to mix(col, Color.White, .15f), 1f to mix(col, c("#2E6A2A"), .2f))) }
        if (k == 1) { crown(W * .78f, base - H * .1f, S * .1f, S * .085f, 44, c("#C8EAA0"), c("#7CC25E"), c("#3E7A34"), groundY=base-H*.02f); rect(W * .78f - S * .006f, base - H * .07f, S * .012f, H * .05f, c("#6A4A34")) }
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
