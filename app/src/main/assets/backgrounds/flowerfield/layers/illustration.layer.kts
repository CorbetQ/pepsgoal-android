private fun DrawScope.flowerfield(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H)
    if (lighting == null) {
    flowerfieldSky(0f to c("#6FC0F0"), .3f to c("#BFE8FF"), .5f to c("#EAF8FF"))
    glow(W * .7f, H * .1f, S * .9f, c("#FFF8DC", .6f), BlendMode.Screen)
    softCloud(W * .3f + t * W * .05f, H * .18f, W * .8f, c("#FFFFFF", .7f), c("#FFFFFF", .6f), 5)
    softCloud(W * .75f + t * W * .03f, H * .3f, W * .5f, c("#FFFFFF", .6f), c("#FFFFFF", .5f), 9)
    } else flowerfieldLiveSky(lighting, t, H * .5f)
    val tones = listOf(c("#C7E6B8"), c("#A8D98A"), c("#86C866"), c("#68B04C"))
    for ((k, col) in tones.withIndex()) {
        val nz = Nz(60 + k); val base = H * (.5f + k * .07f)
        val pts = ridgePts(base, H * .08f, 80) { u -> nz.at(u * 1.4f + k * 2f, 3) * .5f + .5f }
        ridgeFill(pts, vg(base - H * .08f, H, 0f to mix(col, Color.White, .2f), 1f to mix(col, c("#2E6A2A"), .25f)))
        if (k == 1) { // arbre solitaire sur la colline
            val x = W * .3f; val idx = (.3f * 80).toInt(); val y = pts[idx * 2 + 1]
            rect(x - S * .008f, y - H * .06f, S * .016f, H * .06f, c("#5A4030")); crown(x, y - H * .075f, S * .09f, S * .075f, 77, c("#C8EAA0"), c("#6FB04E"), c("#2F6A30"), groundY=y)
        }
        // taches de fleurs lointaines
        val r = Mul(70 + k); val pp = ArrayList<Offset>(); val pr = ArrayList<Offset>()
        for (i in 0 until 160 + k * 60) { val x = r() * W; val idx = ((x / W) * 80).toInt().coerceIn(0, 80); val y = pts[idx * 2 + 1] + r() * H * .08f; if (r() < .6f) pr.add(Offset(x, y)) else pp.add(Offset(x, y)) }
        drawPoints(pr, PointMode.Points, c("#F2463A", .8f), strokeWidth = S * (.003f + k * .002f), cap = StrokeCap.Round)
        drawPoints(pp, PointMode.Points, c("#FFFFFF", .8f), strokeWidth = S * (.003f + k * .002f), cap = StrokeCap.Round)
    }
    // Premier plan : herbes, coquelicots, bleuets, marguerites
    val r = Mul(17); val sway = sin(t * TAU) * S * .008f
    val g = Path(); for (i in 0 until 300) { val x = r() * W; val y = H * (.82f + r() * .2f); val h = S * (.04f + r() * .07f); g.moveTo(x, y); g.cubicTo(x, y - h * .5f, x + sway, y - h * .8f, x + sway * 1.6f + (r() - .5f) * h * .3f, y - h) }
    drawPath(g, c("#4E9A3E"), style = Stroke(max(1f, S * .0035f), cap = StrokeCap.Round))
    for (i in 0 until 34) {
        val x = r() * W; val y = H * (.8f + r() * .2f); val s = S * (.012f + r() * .02f) * (.6f + (y - H * .8f) / H * 3f); val kind = r()
        line(x, y + s * 3f, x + sway, y, max(1f, s * .12f), c("#3E7A30"))
        when { kind < .45f -> { for (k in 0 until 4) { val a = k * 90f + 45f + i * 10f; val ar = a / 360f * TAU; drawOval(c("#E8322A"), topLeft = Offset(x + sway + cos(ar) * s * .35f - s * .5f, y + sin(ar) * s * .35f - s * .5f), size = Size(s, s)) }
                    disc(x + sway, y, s * .25f, c("#2A1A20")) }
            kind < .7f -> flower(x + sway, y, s * .8f, 8, c("#4A7AE8"), c("#2A3A8A"), i * 13f)
            else -> flower(x + sway, y, s * .8f, 12, c("#FFFFFF"), c("#FFC83A"), i * 7f) }
    }
    for (i in 0 until 3) { val u = (t + i / 3f) % 1f; val x = W * (.1f + u * .8f); val y = H * (.66f + .04f * sin(u * TAU * 3 + i)); butterfly(x, y, S * .022f, sin(t * TAU * 30 + i), c("#FFFFFF", .9f), c("#FFE07A", .9f)) }
}
