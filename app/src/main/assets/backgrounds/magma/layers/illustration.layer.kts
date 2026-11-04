private fun DrawScope.magma(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    magmaSky(0f to c("#0C0406"), .35f to c("#1A0A08"), .6f to c("#3A1006"), .7f to c("#6A1E08"), y1 = H * .72f)
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
