private fun DrawScope.nantes(t: Float, d: Tod? = null) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val night = d?.night ?: 0f; val hz = H * .5f; val quay = H * .64f
    val sunA = if (d == null) 1f else (1f - night).coerceIn(0f, 1f)
    val sx = W * (.12f + .76f * (d?.sun ?: .78f))
    nantesLiveSky(d ?: Tod(0f, 0f, 1f, 0f, .78f), t, hz)
    val haze = d?.col("#B9C8DC", "#D8B8C8", "#E8B090", "#2A3460") ?: c("#C8D4E4")
    // Rive nord : la ville, la tour Bretagne et la cathédrale Saint-Pierre dans la brume
    val r = Mul(41); var x = -u
    while (x < W) { val w = u * (3f + r() * 5f); val h = u * (2f + r() * 4f); val col = mix(c("#8A8098"), haze, .5f)
        rect(x, hz - h, w, h + u * 3f, col); if (night > .3f && r() < .6f) disc(x + w * r(), hz - h * r(), u * .25f, c("#FFD27A", .7f * night)); x += w }
    run { val col = mix(c("#7A7090"), haze, .45f); val tx = W * .3f
        rect(tx - u * 2.6f, hz - u * 26f, u * 5.2f, u * 26f, col); for (k in 0 until 12) rect(tx - u * 2.2f, hz - u * 25f + k * u * 2f, u * 4.4f, u * .5f, mix(col, if (night > .3f) c("#FFD27A") else c("#B8C4D8"), .35f))
        rect(tx - u * .2f, hz - u * 29f, u * .4f, u * 3f, col)
        val cx = W * .52f; rect(cx - u * 5f, hz - u * 14f, u * 3.6f, u * 14f, col); rect(cx + u * 1.4f, hz - u * 14f, u * 3.6f, u * 14f, col); rect(cx - u * 1.4f, hz - u * 9f, u * 2.8f, u * 9f, col)
        rect(cx - u * 5.4f, hz - u * 14.6f, u * 4.4f, u * .8f, col); rect(cx + u * 1f, hz - u * 14.6f, u * 4.4f, u * .8f, col) }
    // La Loire, large, et ses reflets
    val water = hz + u * 2f
    if (d == null) drawRect(vg(water, quay, 0f to c("#E8D8C2"), .35f to c("#9EBCD6"), 1f to c("#4E7EAE")), Offset(0f, water), Size(W, quay - water))
    else drawRect(vg(water, quay, 0f to d.col("#DCEAF6", "#F2D2C8", "#F2B98E", "#2A3A60"), .35f to d.col("#A8C8E6", "#C8C0DA", "#B88AA6", "#1C2A50"), 1f to d.col("#3F74AE", "#6F8FC8", "#4A5E9A", "#0A1430")), Offset(0f, water), Size(W, quay - water))
    val g = Mul(17); for (i in 0 until 50) { val v = g(); val y = water + (quay - water) * (.05f + v * .95f); val xx = ((g() + t * (.12f + v * .3f)) % 1f) * W; val len = u * (2f + v * 8f)
        line(xx, y, xx + len, y, max(1f, u * .2f), c("#FFFFFF", (.14f + v * .12f) * (1f - .5f * night))) }
    if (sunA > .01f) for (i in 0 until 60) { val v = g(); val y = water + (quay - water) * (.05f + v * .9f); val xx = sx + (g() - .5f) * W * (.06f + v * .2f); val tw = .5f + .5f * sin(t * TAU * 6 + i)
        line(xx - u, y, xx + u, y, max(1f, u * .2f), c("#FFF6E2", (.2f + .5f * tw) * sunA)) }
    // Quai des Antilles : grue Titan jaune et hangar à bananes
    val titCol = mix(c("#F2C230"), c("#8A6A20"), .45f * night)
    titan(W * .82f, quay - u * 1f, u, titCol, mix(c("#5A4A20"), c("#1A1A22"), .5f * night), night)
    rect(W * .48f, quay - u * 6f, W * .3f, u * 6f, mix(c("#C9B8A0"), c("#2A2A3A"), .6f * night)); fill(poly(W * .48f, quay - u * 6f, W * .63f, quay - u * 9f, W * .78f, quay - u * 6f), mix(c("#8A8090"), c("#1E2236"), .6f * night))
    for (k in 0 until 8) rect(W * .5f + k * W * .034f, quay - u * 4.2f, u * 1.6f, u * 2f, if (night > .3f && k % 3 != 1) c("#FFD27A", night) else mix(c("#6A6070"), c("#1A1A22"), .5f * night))
    // Quai et Anneaux de Buren (rayures, éclairés en couleur la nuit)
    drawRect(vg(quay, H, 0f to mix(c("#B8AE9C"), c("#2A2A36"), .55f * night), .25f to mix(c("#9C9282"), c("#20202C"), .55f * night), 1f to mix(c("#6E6658"), c("#121218"), .55f * night)), Offset(0f, quay), Size(W, H - quay))
    rect(0f, quay, W, u * 1.2f, mix(c("#E6DCCA"), c("#3A3A48"), .55f * night))
    val ringCols = listOf(c("#E8412C"), c("#3AB060"), c("#2E7FE8"))
    for ((k, rx) in listOf(.06f, .26f, .46f).withIndex()) { val cx = W * rx; val cy = quay - u * 7.5f; val rr = u * 7f
        drawOval(mix(c("#F4F4F4"), ringCols[k], .15f + .7f * night), Offset(cx - rr * .45f, cy - rr), Size(rr * .9f, rr * 2f), style = Stroke(u * 1.1f))
        for (j in 0 until 6) { val a = j / 6f * TAU; drawLine(mix(c("#1A1A22"), ringCols[k], night), Offset(cx + cos(a) * rr * .45f, cy + sin(a) * rr), Offset(cx + cos(a + .25f) * rr * .45f, cy + sin(a + .25f) * rr), u * .5f) }
        if (night > .2f) glow(cx, cy, rr * 2.4f, ringCols[k].a(.35f * night), BlendMode.Screen)
        line(cx, quay, cx, cy + rr, max(1f, u * .4f), c("#8A8A96")) }
    // Le Grand Éléphant marche sur l'esplanade
    val ground = H * .95f
    // Pavés de l'esplanade, en perspective
    for (k in 0 until 12) { val yy = quay + u * 1.2f + (H - quay) * (k / 12f).pow(1.5f); line(0f, yy, W, yy, max(1f, u * .15f), c("#000000", .1f)) }
    for (k in -8..8) line(W * .5f + k * W * .08f, quay + u * 1.2f, W * .5f + k * W * .3f, H, max(1f, u * .15f), c("#000000", .08f))
    drawOval(c("#000000", .2f), Offset(W * .5f - u * 38f, ground - u * 2.4f), Size(u * 76f, u * 5f))
    elephant(W * .54f, ground, u * 1.25f, t, night)
    // Promeneurs qui regardent passer l'éléphant
    val pr = Mul(23); for (k in 0 until 9) { val px = W * (.04f + pr() * .92f); if (px > W * .25f && px < W * .78f) continue; val py = H * (.9f + pr() * .06f); val h = u * (4.5f + pr() * 1.5f)
        val col = mix(listOf(c("#2E4A7A"), c("#B23A2E"), c("#3A3A44"), c("#2E7A5A"))[k % 4], c("#101018"), .5f * night)
        rect(px - h * .14f, py - h * .62f, h * .28f, h * .62f, col); disc(px, py - h * .74f, h * .13f, mix(c("#E8C4A0"), c("#2A2430"), .6f * night)) }
    if (d == null) return
    if (d.dusk > .01f) drawRect(vg(0f, H, 0f to c("#5A2E8A", .14f * d.dusk), .5f to c("#FF7A3A", .12f * d.dusk), 1f to c("#FFB070", .05f * d.dusk)))
    if (d.dawn > .01f) drawRect(vg(0f, H, 0f to c("#7F8FE0", .1f * d.dawn), .5f to c("#FFB0C0", .12f * d.dawn), 1f to Color.Transparent))
}
