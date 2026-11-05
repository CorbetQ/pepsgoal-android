private fun DrawScope.fireworks(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val q = H * .78f
    fireworksSky(0f to c("#03040F"), .5f to c("#0A0E2A"), .78f to c("#241A4A"), y1 = q)
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
