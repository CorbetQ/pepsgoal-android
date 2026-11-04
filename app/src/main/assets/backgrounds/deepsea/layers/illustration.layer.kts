private fun DrawScope.deepsea(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    deepseaSky(0f to c("#2FA3CF"), .1f to c("#137BAE"), .38f to c("#0A4F7A"), .7f to c("#052C4A"), 1f to c("#02152A"))
    // Surface : soleil diffus et ondulations (caustiques)
    ellGlow(W * .55f, -H * .02f, W * .9f, H * .12f, c("#CFF6FF", .55f), BlendMode.Screen)
    for (j in 0 until 7) {
        val p = Path(); val y0 = H * (.012f + j * .013f)
        for (i in 0..40) { val x = W * i / 40f; val y = y0 + sin(x / W * (14f + j * 3f) + t * TAU + j) * H * .004f; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
        drawPath(p, c("#E6FBFF", .16f - j * .018f), style = Stroke(S * .004f))
    }
    rays(W * .52f, -H * .06f, H * 1.05f, 11, 90f, 46f, c("#B8F0FF", .09f), 12, t)
    // Raie manta dans le lointain
    run {
        val mx = W * (1.2f - ((t + .3f) % 1f) * 1.5f); val my = H * .3f + sin(t * TAU * 2) * H * .01f; val s = S * .16f; val f = sin(t * TAU * 3) * .2f
        val p = Path(); p.moveTo(mx - s * .3f, my); p.cubicTo(mx - s * .1f, my - s * (.4f + f), mx + s * .4f, my - s * (.6f + f), mx + s * .2f, my - s * (.05f)); p.lineTo(mx + s * .6f, my + s * .05f)
        p.cubicTo(mx + s * .25f, my + s * .1f, mx + s * .4f, my + s * (.6f + f), mx - s * .1f, my + s * (.35f + f)); p.close()
        drawPath(p, c("#0B4467", .55f)); line(mx + s * .5f, my + s * .04f, mx + s * 1.2f, my + s * .1f, S * .003f, c("#0B4467", .5f))
    }
    // Fonds rocheux en trois plans (brume bleue)
    val tones = listOf(c("#0C4F72"), c("#083553"), c("#041D30"))
    for (k in 0 until 3) {
        val nz = Nz(80 + k); val base = H * (.74f + k * .08f); val amp = H * (.12f - k * .025f)
        val pts = ridgePts(base, amp, 90) { u -> .55f * nz.ridged(u * (2f + k), 4) + .45f * (nz.at(u * 1.4f, 3) * .5f + .5f) }
        ridgeFill(pts, vg(base - amp, H, 0f to tones[k], 1f to mix(tones[k], c("#010A14"), .5f)))
        litFaces(pts, true, H * .02f, c("#58C6E8", .18f - k * .04f))
        // Algues qui ondulent
        val r = Mul(90 + k)
        for (i in 0 until 9 + k * 3) {
            val x = r() * W; val idx = ((x / W) * 90).toInt().coerceIn(0, 89); val yb = pts[idx * 2 + 1] + H * .01f
            val h = H * (.08f + r() * .14f) * (1f + k * .25f); val w = S * (.012f + r() * .01f) * (1f + k * .3f); val ph = r() * TAU
            val p = Path(); val m = 12
            for (j in 0..m) { val u = j / m.toFloat(); val sx = x + sin(u * 3f + t * TAU + ph) * w * 2.2f * u; val y = yb - h * u; if (j == 0) p.moveTo(sx - w, y) else p.lineTo(sx - w * (1f - u * .8f), y) }
            for (j in m downTo 0) { val u = j / m.toFloat(); val sx = x + sin(u * 3f + t * TAU + ph) * w * 2.2f * u; val y = yb - h * u; p.lineTo(sx + w * (1f - u * .8f), y) }
            p.close(); drawPath(p, mix(mix(c("#0E5A4E"), tones[k], .55f), c("#031220"), k * .25f))
        }
    }
    // Bancs de poissons (un passage par cycle)
    val r = Mul(31)
    for (sch in 0 until 2) {
        val cy = H * (.42f + sch * .14f); val sz = S * (.018f + sch * .01f); val sp = if (sch == 0) 1f else -1f
        for (i in 0 until 26) {
            val off = r(); val dy = (r() - .5f) * H * .09f; val u = ((off * .25f + t * sp) % 1f + 1f) % 1f
            val x = -W * .2f + u * W * 1.4f; val y = cy + dy + sin(u * TAU * 2 + off * 6f) * H * .01f
            fish(x, y, sz * (.8f + r() * .4f), sp, mix(c("#9FD8F0"), c("#2C7AA8"), sch * .5f), c("#E8F8FF"), sin(t * TAU * 12 + i))
        }
    }
    // Bulles qui montent
    for (i in 0 until 24) {
        val x0 = W * (.15f + .7f * ((i * 37) % 23) / 23f); val sp = .6f + (i % 5) * .2f; val u = ((i * .137f) + t * sp) % 1f
        val y = H * (1.02f - u * 1.1f); val x = x0 + sin(u * 18f + i) * S * .01f; val rr = S * (.004f + (i % 4) * .003f)
        drawCircle(c("#DDF7FF", .5f), radius = rr, center = Offset(x, y), style = Stroke(max(1f, rr * .25f))); disc(x - rr * .35f, y - rr * .35f, rr * .25f, c("#FFFFFF", .7f))
    }
    // Neige marine
    val m = Mul(5); val pts = ArrayList<Offset>()
    for (i in 0 until 160) { val x = m() * W; val y = ((m() + t * .3f) % 1f) * H; pts.add(Offset(x + sin(t * TAU + i) * S * .005f, y)) }
    drawPoints(pts, PointMode.Points, c("#CFEFFF", .35f), strokeWidth = max(1f, S / 360f), cap = StrokeCap.Round)
}
