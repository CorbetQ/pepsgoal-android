private fun DrawScope.lagoon(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H); val hz = H * .52f
    if (lighting == null) {
    lagoonSky(0f to c("#2E9FE3"), .25f to c("#5CC3F2"), .45f to c("#A6E6FA"), .52f to c("#E1F8F6"), y1 = hz + 2f)
    glow(W * .8f, H * .1f, S * .9f, c("#FFFBE8", .45f), BlendMode.Screen); glow(W * .8f, H * .1f, S * .12f, c("#FFFFFF", .9f), BlendMode.Screen)
    // Cumulus doux (corps lumineux, base légèrement bleutée)
    val dr = t * W * .05f
    for ((cx, cy, cw, sd) in listOf(arrayOf(.22f, .3f, .55f, 1f), arrayOf(.7f, .4f, .45f, 2f), arrayOf(.45f, .46f, .35f, 3f))) {
        val r = Mul((sd as Float).toInt() * 13); val x0 = W * (cx as Float) + dr; val y0 = H * (cy as Float); val w = W * (cw as Float)
        for (i in 0 until 14) { val u = (r() - .5f) * w; val v = -abs(r() - .5f) * w * .25f; ellGlow(x0 + u, y0 + v, w * (.16f + r() * .14f), w * (.1f + r() * .08f), c("#FFFFFF", .5f)) }
        ellGlow(x0, y0 + w * .03f, w * .5f, w * .05f, c("#9CCBE6", .35f))
    }
    } else lagoonLiveSky(lighting, t, H * .52f)
    // Mer : bleu profond à l'horizon → turquoise du lagon
    drawRect(vg(hz, H * .78f, 0f to c("#1D74B0"), .2f to c("#2299C2"), .55f to c("#33C4CC"), 1f to c("#8CE9DA")), topLeft = Offset(0f, hz), size = Size(W, H * .78f - hz))
    // Îlot et cocotiers lointains
    fill(poly(W * .05f, hz + 1f, W * .1f, hz - H * .008f, W * .22f, hz - H * .01f, W * .3f, hz + 1f), c("#3F7F6B"))
    for (i in 0 until 3) palm(W * (.12f + i * .05f), hz - H * .006f, H * .04f, .2f - i * .15f, t, 50 + i, c("#56715F"), c("#3E7A5E"), c("#4F8E6D"))
    // Reflets du soleil, scintillants
    val r = Mul(3)
    for (i in 0 until 120) {
        val y = hz + (H * .26f) * r().pow(2f); val x = W * .8f + (r() - .5f) * W * (.15f + (y - hz) / H * 2f); val wl = S * (.005f + r() * .02f)
        line(x - wl, y, x + wl, y, max(1f, S * .002f), c("#FFFFFF", (.3f + .5f * (.5f + .5f * sin(t * TAU * 4 + i))) * (1f - (y - hz) / (H * .3f))))
    }
    // Hauts-fonds plus clairs
    ellGlow(W * .4f, H * .72f, W * .6f, H * .04f, c("#B6F5E4", .5f))
    // Sable : estran mouillé, écume qui va et vient
    val sw = sin(t * TAU) * H * .008f
    val shore = Nz(8)
    val sp = ridgePts(H * .76f + sw, H * .012f, 60) { u -> shore.at(u * 3f, 3) }
    ridgeFill(sp, vg(H * .74f, H, 0f to c("#E6D6A8"), .15f to c("#F2E2B6"), 1f to c("#E9CD94")))
    ridgeFill(sp, vg(H * .75f, H * .8f, 0f to c("#BFAE86", .6f), 1f to c("#BFAE86", 0f)))
    val foam = Path(); var i = 0; while (i < sp.size) { if (i == 0) foam.moveTo(sp[i], sp[i + 1]) else foam.lineTo(sp[i], sp[i + 1]); i += 2 }
    drawPath(foam, c("#FFFFFF", .85f), style = Stroke(S * .006f, cap = StrokeCap.Round))
    val foam2 = Path(); i = 0; while (i < sp.size) { val y = sp[i + 1] - H * .012f - sw * .5f; if (i == 0) foam2.moveTo(sp[i], y) else foam2.lineTo(sp[i], y); i += 2 }
    drawPath(foam2, c("#FFFFFF", .4f), style = Stroke(S * .003f, cap = StrokeCap.Round))
    val g = Mul(9); val gp = ArrayList<Offset>(); for (k in 0 until 500) gp.add(Offset(g() * W, H * .78f + g() * H * .22f))
    drawPoints(gp, PointMode.Points, c("#B8986A", .35f), strokeWidth = max(1f, S / 450f))
    // Cocotiers au premier plan (cadrage à droite) et leur ombre
    fill(poly(W * .93f, H * .99f, W * .45f, H * .93f, W * .5f, H * .955f, W * .98f, H), c("#A88A5E", .3f))
    palm(W * .98f, H * .99f, H * .3f, -.3f, t, 7, c("#8A6F55"), c("#246B45"), c("#7CC46E"))
    palm(W * .03f, H * .93f, H * .2f, .18f, t, 8, c("#937A5F"), c("#21633F"), c("#6DB766"))
}
