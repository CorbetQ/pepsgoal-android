private fun DrawScope.borealis(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val hz = H * .66f
    borealisSky(0f to c("#01040C"), .35f to c("#03111F"), .6f to c("#06223A"), 1f to c("#0B3A44"), y1 = hz)
    starField(61, 460, hz, c("#E6F2FF", .8f), t, 4)
    auroraCurtains(t, 1f)
    ellGlow(W * .5f, hz, W, H * .1f, c("#38E0A0", .12f), BlendMode.Screen)
    // Montagnes enneigées, rebord teinté par l'aurore
    for (k in 0 until 2) {
        val nz = Nz(70 + k); val base = hz + H * k * .015f; val amp = H * (.09f - k * .03f)
        val pts = ridgePts(base, amp, 100) { u -> .6f * nz.ridged(u * (2.5f + k), 4) + .4f * (nz.at(u, 2) * .5f + .5f) }
        ridgeFill(pts, vg(base - amp, base + H * .02f, 0f to mix(c("#2A4B63"), c("#0B1A2A"), k * .6f), 1f to c("#08131F")), base + H * .02f)
        litFaces(pts, true, H * .02f, c("#9FFFD8", .25f - k * .1f))
        if (k == 0) snowCaps(pts, base - amp * .55f, c("#CFE9F2", .35f))
    }
    // Lac gelé : reflet de l'aurore
    val ly = hz + H * .02f; val lb = H * .8f
    drawRect(vg(ly, lb, 0f to c("#0A2A3A"), 1f to c("#051622")), topLeft = Offset(0f, ly), size = Size(W, lb - ly))
    clipRect(0f, ly, W, lb) { scale(1f, -1f, Offset(0f, ly)) { auroraCurtains(t, .55f) } }
    for (i in 0 until 10) { val y = ly + (lb - ly) * (i + .5f) / 10f; line(0f, y, W, y, max(1f, S * .002f), c("#CFF6FF", .05f)) }
    // Rive enneigée, sapins et cabane
    val nz = Nz(91); val shore = { x: Float -> lb + nz.at(x / W * 3f, 3) * H * .01f }
    conifers(22, 12, H * .05f, H * .12f, c("#04101A"), shore)
    ridgePts(lb, H * .012f, 60) { u -> nz.at(u * 3f, 3) }.let { ridgeFill(it, vg(lb, H, 0f to c("#2B4A5E"), .3f to c("#1A3042"), 1f to c("#0A1622"))) }
    val cx = W * .68f; val cy = lb + H * .005f; val cw = S * .12f
    fill(poly(cx, cy, cx + cw, cy, cx + cw, cy - cw * .45f, cx, cy - cw * .45f), c("#0C1520"))
    fill(poly(cx - cw * .1f, cy - cw * .42f, cx + cw * .5f, cy - cw * .85f, cx + cw * 1.1f, cy - cw * .42f), c("#0A111A"))
    fill(poly(cx - cw * .1f, cy - cw * .42f, cx + cw * .5f, cy - cw * .85f, cx + cw * .5f, cy - cw * .78f, cx, cy - cw * .4f), c("#DDEFFF", .5f))
    rect(cx + cw * .2f, cy - cw * .3f, cw * .2f, cw * .17f, c("#FFC36E")); rect(cx + cw * .6f, cy - cw * .3f, cw * .2f, cw * .17f, c("#FFB25A"))
    glow(cx + cw * .5f, cy - cw * .2f, cw * 1.2f, c("#FFB25A", .3f), BlendMode.Screen)
    for (i in 0 until 6) { val u = ((t * 2f + i / 6f) % 1f); glow(cx + cw * .8f + u * cw * .4f + sin(u * 6f) * cw * .05f, cy - cw * (.9f + u * 1.2f), cw * (.08f + u * .15f), c("#9FB4C8", .18f * (1f - u))) }
    val snow = Mul(4); val pts = ArrayList<Offset>(); for (i in 0 until 200) { val y = lb + (H - lb) * snow(); pts.add(Offset(snow() * W, y)) }
    drawPoints(pts, PointMode.Points, c("#DDF2FF", .3f), strokeWidth = max(1f, S / 450f), cap = StrokeCap.Round)
}
