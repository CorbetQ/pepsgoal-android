private fun DrawScope.rooftops(t: Float, d: Tod? = null) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val night = d?.night ?: .1f; val hz = H * .6f
    val sunA = if (d == null) 1f else (1f - d.night).coerceIn(0f, 1f)
    rooftopsLiveSky(d ?: Tod(.1f, 0f, .3f, .6f, .93f), t, hz)
    val haze = d?.col("#B9C8DC", "#D8B8C8", "#E8A890", "#2A3460") ?: c("#E8A890")
    // Paris au loin : toits et dôme des Invalides dans la brume
    val r = Mul(11); var x = -u * 2f
    while (x < W) { val w = u * (3f + r() * 6f); val h = u * (2.5f + r() * 4f); val col = mix(c("#7A6E92"), haze, .55f)
        rect(x, hz - h, w, h + H * .12f, col); fill(poly(x, hz - h, x + u * .8f, hz - h - u * 1.4f, x + w - u * .8f, hz - h - u * 1.4f, x + w, hz - h), mix(col, haze, .2f))
        if (r() < .35f) rect(x + w * .7f, hz - h - u * 2.4f, u * .9f, u * 1.2f, col)
        if (night > .3f && r() < .5f) disc(x + w * r(), hz - h * r(), u * .25f, c("#FFD27A", .7f * night))
        x += w }
    run { val ix = W * .2f; val iy = hz - u * 5f; val gold = mix(c("#D9A441"), haze, .35f); val st = mix(c("#8E8098"), haze, .5f)
        rect(ix - u * 4f, iy, u * 8f, u * 5f, st); rect(ix - u * 3f, iy - u * 3f, u * 6f, u * 3f, st)
        drawArc(gold, 180f, 180f, true, Offset(ix - u * 3f, iy - u * 6.6f), Size(u * 6f, u * 7f)); rect(ix - u * .5f, iy - u * 8.5f, u * 1f, u * 2f, gold); line(ix, iy - u * 8.5f, ix, iy - u * 10.5f, max(1f, u * .2f), gold) }
    // Tour Eiffel, voilée par la distance
    val lit = sm(.35f, .7f, night)
    val towerCol = mix(d?.col("#6A5A52", "#6A5068", "#4A3446", "#2A2236")?.let { mix(it, haze, .3f) } ?: c("#4E3A4E"), c("#E0A048"), .75f * lit)
    eiffel(W * .64f, hz + u * 2f, H * .43f, towerCol, mix(haze, c("#3A2A2A"), .5f * lit).a(.55f), lit, t)
    mist(hz - u * 1f, u * 6f, haze.a(.35f))
    // Plan intermédiaire : derniers étages et combles des rues voisines, cheminées et boisseaux, dans la brume du soir
    run { val rr = Mul(29); var mx = -u * 3f; val mb = H * .675f
        while (mx < W) { val w = u * (9f + rr() * 8f); val top = mb - u * (2f + rr() * 3f)
            val st = mix(mix(c("#D9C8AC"), c("#232A44"), .6f * night), haze, .38f); val zc = mix(mix(c("#7D8AA2"), c("#232A44"), .6f * night), haze, .3f)
            rect(mx, top, w, H * .72f - top, st)
            fill(poly(mx - u * .3f, top, mx + u * .6f, top - u * 3f, mx + w - u * .6f, top - u * 3f, mx + w + u * .3f, top), zc)
            for (k in 0 until (w / (u * 3f)).toInt()) { val wx = mx + u * (1.2f + k * 3f); val on = rr() < .2f + .5f * night
                rect(wx, top + u * 1.2f, u * 1.2f, u * 2f, if (on) c("#FFD27A", .9f) else mix(c("#3A4258"), haze, .3f)); rect(wx, top - u * 2.2f, u * 1.1f, u * 1.5f, if (rr() < .3f + .4f * night) c("#FFD27A", .85f) else mix(c("#3A4258"), haze, .3f)) }
            if (rr() < .7f) { val cxx = mx + w * (.2f + rr() * .6f); rect(cxx, top - u * 5.2f, u * 2.4f, u * 2.6f, st); for (q in 0 until 3) rect(cxx + u * (.2f + q * .75f), top - u * 6f, u * .5f, u * .9f, mix(c("#C2683E"), haze, .3f)) }
            mx += w + u * .2f }
        mist(mb - u * 2f, u * 5f, haze.a(.25f)) }
    // Rangée d'immeubles haussmanniens
    val cornice = H * .7f
    var bx = -u * 6f; var seed = 3
    while (bx < W) { val w = u * (24f + (seed * 7 % 9)); haussmann(bx, w, cornice + ((seed % 3) - 1) * u * 1.4f, H, u, seed, night, sunA); bx += w + u * .3f; seed++ }
    // Toit de zinc au premier plan : tasseaux, verrière, cheminée à boisseaux et chat sur le faîtage
    val ridge = H * .86f
    fill(poly(-u, ridge, W + u, ridge - u * 3f, W + u, H, -u, H), vg(ridge - u * 3f, H, 0f to c("#9AA6BA"), 1f to c("#5E6A80")))
    for (k in 0 until 40) { val fx = W * k / 39f; line(fx, ridge - u * 3f * fx / W, W * .5f + (fx - W * .5f) * 1.6f, H, max(1f, u * .2f), c("#4A5468", .5f)) }
    rect(-u, ridge - u * 3f * 0f - u * .8f, W + 2 * u, u * .8f, c("#6E7A90"))
    // Verrière d'atelier : châssis en fer, carreaux qui reflètent le ciel (ou éclairés la nuit)
    run { val gx0 = W * .56f; val gx1 = W * .8f; val gy0 = ridge + u * 3f; val gy1 = ridge + u * 11f
        val glass = Path().apply { moveTo(gx0 + u, gy0); lineTo(gx1 - u, gy0 - u * .4f); lineTo(gx1 + u, gy1); lineTo(gx0 - u, gy1 + u * .4f); close() }
        fill(Path().apply { moveTo(gx0 + u * .2f, gy0 - u * .8f); lineTo(gx1 - u * .2f, gy0 - u * 1.2f); lineTo(gx1 + u * 1.9f, gy1 + u * .7f); lineTo(gx0 - u * 1.9f, gy1 + u * 1.1f); close() }, c("#3A3E48"))
        fill(glass, vg(gy0, gy1, 0f to mix(c("#B8D2EC"), c("#FFD98A"), night), 1f to mix(c("#6A8AAA"), c("#E8A040"), night)))
        clipPath(glass) { for (k in 1 until 6) { val f = k / 6f; line(gx0 + u + (gx0 - u - gx0 - u) * 0f + (gx1 - gx0) * f, gy0 - u * .4f * f, gx0 - u + (gx1 - gx0 + u * 2f) * f, gy1 + u * .4f * (1 - f), max(1f, u * .3f), c("#2A2E36")) }
            for (k in 1 until 3) { val f = k / 3f; line(gx0, gy0 + (gy1 - gy0) * f, gx1, gy0 + (gy1 - gy0) * f - u * .4f, max(1f, u * .3f), c("#2A2E36")) }
            line(gx0 + (gx1 - gx0) * .2f, gy0, gx0 + (gx1 - gx0) * .45f, gy1, max(1f, u * 1.2f), c("#FFFFFF", .18f * (1f - night))) }
        if (night > .05f) glow((gx0 + gx1) / 2, (gy0 + gy1) / 2, u * 14f, c("#FFB04A", .3f * night), BlendMode.Screen) }
    run { val cx = W * .16f; val top = ridge - u * 16f
        rect(cx - u * 6f, top, u * 12f, ridge - top + u * 2f, c("#C9B496")); rect(cx + u * 2f, top, u * 4f, ridge - top + u * 2f, c("#A89478")); rect(cx - u * 6.6f, top, u * 13.2f, u * 1.2f, c("#8E7E68"))
        for (p in 0 until 6) { val px = cx - u * 5.4f + p * u * 1.9f; fill(poly(px, top, px + u * .3f, top - u * 3.4f, px + u * 1.3f, top - u * 3.4f, px + u * 1.6f, top), vg(top - u * 3.4f, top, 0f to c("#D27A4A"), 1f to c("#A8542E"))); rect(px + u * .2f, top - u * 3.8f, u * 1.2f, u * .5f, c("#8E4426")) } }
    run { val kx = W * .42f; val ky = ridge - u * 1.4f * .42f - u * .8f; val cat = c("#1A1820")
        drawOval(cat, Offset(kx - u * 2f, ky - u * 4.4f), Size(u * 4f, u * 4.6f)); disc(kx, ky - u * 5.2f, u * 1.6f, cat)
        fill(poly(kx - u * 1.5f, ky - u * 6f, kx - u * 1.1f, ky - u * 7.6f, kx - u * .3f, ky - u * 6.5f), cat); fill(poly(kx + u * .3f, ky - u * 6.5f, kx + u * 1.1f, ky - u * 7.6f, kx + u * 1.5f, ky - u * 6f), cat)
        val tail = sin(t * TAU * 3f) * u * .8f
        drawPath(Path().apply { moveTo(kx + u * 1.6f, ky - u * .4f); cubicTo(kx + u * 4.5f, ky, kx + u * 4.5f + tail, ky - u * 3f, kx + u * 3.2f + tail, ky - u * 4f) }, cat, style = Stroke(u * .7f, cap = StrokeCap.Round)) }
    // Pigeons
    for (i in 0 until 3) { val k = (t * 1.5f + i * .33f) % 1f; val px = W * (k * 1.2f - .1f); val py = H * (.24f + i * .05f); val s = u * 1.3f; val fl = sin(t * TAU * 18 + i) * .4f
        drawPath(Path().apply { moveTo(px - s, py - s * (.4f + fl)); quadraticTo(px - s * .4f, py - s * .2f, px, py + s * .2f); quadraticTo(px + s * .4f, py - s * .2f, px + s, py - s * (.4f + fl)) }, c("#2A2340", .75f), style = Stroke(max(1f, u * .3f))) }
    if (d == null) return
    if (d.dawn > .01f) drawRect(vg(0f, H, 0f to c("#7F8FE0", .1f * d.dawn), .5f to c("#FFB0C0", .12f * d.dawn), 1f to Color.Transparent))
    if (d.night > .01f) drawRect(vg(hz, H, 0f to c("#1A2550", .25f * d.night), 1f to c("#0A1030", .45f * d.night)), Offset(0f, hz), Size(W, H - hz), blendMode = BlendMode.Multiply)
}
