private fun DrawScope.loire(t: Float, d: Tod? = null) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val bank = H * .47f; val near = H * .8f
    val sx = W * (.12f + .76f * (d?.sun ?: .88f))
    loireLiveSky(d ?: Tod(0f, 0f, 1f, 0f, .88f), t, bank)
    // Coteaux au loin, puis coteau de vignes
    val nz = Nz(21)
    ridgeFill(ridgePts(bank - H * .03f, H * .045f, 80) { x -> .5f + .5f * nz.at(x * 4f, 3) }, vg(bank - H * .08f, bank, 0f to c("#A9AACB"), 1f to c("#C9BFC9")), bank)
    val vine = ridgePts(bank - H * .005f, H * .03f, 80) { x -> .4f + .4f * nz.at(x * 6f + 3f, 3) }
    ridgeFill(vine, vg(bank - H * .04f, bank, 0f to c("#9DB078"), 1f to c("#7E9560")), bank)
    clipPath(Path().apply { moveTo(0f, bank); var i = 0; while (i < vine.size) { lineTo(vine[i], vine[i + 1]); i += 2 }; lineTo(W, bank); close() }) {
        for (k in -20..40) line(W * k / 24f, bank, W * k / 24f + W * .3f, bank - H * .06f, max(1f, u * .25f), c("#5E7448", .5f))
    }
    // Village et château de tuffeau
    val town = bank + u * 1.5f
    canopy(14, 7, u * 2f, u * 3.6f, c("#6E8A55"), c("#9FB574"), false, { town }, 0f, W * .5f)
    loireTown(town, 1f)
    canopy(10, 9, u * 1.6f, u * 3f, c("#5E7A4A"), c("#93AE6A"), false, { town + u * .5f }, W * .5f, W)
    // La Loire : large, claire, reflets du ciel
    rect(0f, town, W, near - town + u * 4f, c("#9CC0DE"))
    if (d == null) drawRect(vg(town, near, 0f to c("#F2DCC2"), .25f to c("#BFD3E6"), 1f to c("#6F9CC8")), topLeft = Offset(0f, town), size = Size(W, near - town + u * 4f))
    else drawRect(vg(town, near, 0f to d.col("#DCEAF6", "#F2D2C8", "#F7C9A0", "#2A3A60"), .25f to d.col("#B4D2EC", "#C8C0DA", "#C49AB0", "#1C2A50"), 1f to d.col("#3F7DC0", "#6F8FC8", "#4A5E9A", "#0A1430")), topLeft = Offset(0f, town), size = Size(W, near - town + u * 4f))
    // Reflet du village (inversé, étiré, adouci)
    clipRect(0f, town, W, near) { scale(1f, -.7f, Offset(0f, town)) { loireTown(town, .28f) } }
    mist(town + u * 1.5f, u * 5f, c("#FFF1DE", .45f))
    // Chemin de lumière du soleil (ou de la lune) : paillettes qui scintillent
    val r = Mul(5)
    val gx = if (d != null && d.night > .5f) W * .78f else sx
    for (i in 0 until 90) { val v = r(); val y = town + (near - town) * (.05f + v * .9f); val spread = W * (.04f + v * .22f); val x = gx + (r() - .5f) * 2f * spread
        val tw = .5f + .5f * sin(t * TAU * 6 + i * 1.7f); val len = u * (1f + v * 4f); line(x - len, y, x + len, y, max(1f, u * (.15f + v * .3f)), c("#FFF6E2", (.25f + .55f * tw) * (1f - v * .5f))) }
    // Rides sur l'eau
    for (i in 0 until 40) { val v = r(); val y = town + (near - town) * (.1f + v * .9f); val x = ((r() + t * (.2f + v * .3f)) % 1f) * W; val len = u * (2f + v * 9f)
        line(x, y, x + len, y, max(1f, u * .2f), c("#FFFFFF", .18f + v * .12f)) }
    // Bancs de sable blonds (et leur ombre)
    for ((bx, byk, bw) in listOf(Triple(.2f, .3f, .38f), Triple(.78f, .55f, .3f), Triple(.45f, .7f, .2f))) {
        val by = town + (near - town) * byk; val w = W * bw; val h = u * (2f + byk * 3f)
        val sd = (bx * 100).toInt()
        fill(lobed(W * bx + w * .02f, by + h * .25f, w * .54f, h * .62f, sd, 5, .12f), c("#6F8FB0", .35f))
        fill(lobed(W * bx, by, w * .5f, h * .5f, sd, 5, .12f, .3f), vg(by - h / 2, by + h / 2, 0f to c("#FAEBCB"), .6f to c("#E6CB98"), 1f to c("#C9A874")))
        fill(lobed(W * bx - w * .05f, by - h * .12f, w * .32f, h * .2f, sd + 3, 4, .15f), c("#FFF6E0", .5f))
    }
    // Héron cendré sur le banc de sable
    run { val hx = W * .3f; val hy = town + (near - town) * .3f - u * .4f; val g = c("#5A6272")
        scale(1.5f, 1.5f, Offset(hx, hy)) {
        line(hx, hy, hx - u * .3f, hy - u * 3f, max(1f, u * .25f), g); line(hx + u * .5f, hy, hx + u * .2f, hy - u * 3f, max(1f, u * .25f), g)
        drawOval(c("#8E97A6"), Offset(hx - u * 1.8f, hy - u * 5.2f), Size(u * 4.2f, u * 2.4f))
        line(hx + u * 1.6f, hy - u * 4.6f, hx + u * 2.4f, hy - u * 7.4f, max(1f, u * .45f), c("#C9CFD8")); disc(hx + u * 2.5f, hy - u * 7.6f, u * .55f, c("#DDE2E8"))
        line(hx + u * 2.8f, hy - u * 7.6f, hx + u * 4.2f, hy - u * 7.2f, max(1f, u * .25f), c("#D9A441"))
        line(hx + u * 2.2f, hy - u * 7.9f, hx + u * 1.2f, hy - u * 8.4f, max(1f, u * .2f), c("#2A2E36")) } }
    // Toue cabanée qui dérive lentement
    run { val bx = W * (.62f + .06f * sin(t * TAU)); val by = town + (near - town) * .48f; val L = u * 22f; val hh = u * 2f
        drawOval(c("#3A4A5E", .25f), Offset(bx - L * .55f, by + hh * .4f), Size(L * 1.1f, hh * 1.2f))
        for (k in 0 until 5) line(bx - L * (.45f - k * .02f), by + hh * (1.2f + k * .9f), bx + L * (.45f - k * .06f), by + hh * (1.2f + k * .9f), max(1f, u * .35f), c("#4A3A2E", .22f - k * .04f))
        fill(Path().apply { moveTo(bx - L / 2, by - hh * .4f); quadraticTo(bx - L * .62f, by - hh * 1.6f, bx - L * .66f, by - hh * 2f); lineTo(bx - L * .5f, by + hh * .4f); lineTo(bx + L * .48f, by + hh * .4f); lineTo(bx + L * .52f, by - hh * .5f); close() }, vg(by - hh, by + hh, 0f to c("#7A5236"), 1f to c("#3E2A1C")))
        val cl = bx - L * .1f; val cr = bx + L * .3f; val ct = by - hh * 3.4f
        fill(Path().apply { moveTo(cl, by - hh * .4f); lineTo(cl, ct + hh); quadraticTo((cl + cr) / 2, ct - hh * .8f, cr, ct + hh); lineTo(cr, by - hh * .4f); close() }, vg(ct, by, 0f to c("#3A3632"), 1f to c("#1E1C1A")))
        line(bx - L * .3f, by - hh * .4f, bx - L * .3f, by - hh * 9f, max(1f, u * .3f), c("#4A3424"))
        line(bx + L * .5f, by - hh * .2f, bx + L * .7f, by + hh * .2f, max(1f, u * .2f), c("#FFFFFF", .5f)) }
    // Sternes dans le ciel
    for (i in 0 until 4) { val k = (t * 2f + i * .23f) % 1f; val x = W * (1.1f - k * 1.3f); val y = H * (.18f + i * .04f) + sin(k * TAU * 3 + i) * u * 1.5f; val s = u * (1.2f + i * .2f); val fl = sin(t * TAU * 20 + i) * .5f
        drawPath(Path().apply { moveTo(x - s * 1.4f, y - s * (.4f + fl)); quadraticTo(x - s * .5f, y - s * .2f, x, y + s * .2f); quadraticTo(x + s * .5f, y - s * .2f, x + s * 1.4f, y - s * (.4f + fl)) }, c("#3A3F4A", .8f), style = Stroke(max(1f, u * .3f))) }
    // Rive proche : levée, herbes hautes, roseaux
    val shore = ridgePts(near, H * .02f, 60) { x -> .5f + .5f * nz.at(x * 5f + 9f, 3) }
    ridgeFill(shore, vg(near - H * .03f, H, 0f to c("#8FAE5E"), .25f to c("#5E7F3E"), 1f to c("#2E4424")))
    // Levée de la Loire à Vélo et son cycliste
    val ly = near + H * .045f
    fill(poly(0f, ly, W, ly - H * .01f, W, ly + H * .018f, 0f, ly + H * .028f), c("#E8D8B8"))
    run { val k = (t * 3f) % 1f; val x = W * (-.1f + k * 1.2f); val y = ly + H * .006f - k * H * .01f; val s = u * 2.2f; val sp = t * TAU * 30
        for (wx in listOf(-s * .9f, s * .9f)) { drawCircle(c("#2A2E36"), s * .55f, Offset(x + wx, y - s * .5f), style = Stroke(max(1f, u * .25f))); line(x + wx, y - s * .5f, x + wx + cos(sp) * s * .5f, y - s * .5f + sin(sp) * s * .5f, max(1f, u * .12f), c("#2A2E36")) }
        line(x - s * .9f, y - s * .5f, x, y - s * .55f, max(1f, u * .3f), c("#E8412C")); line(x, y - s * .55f, x + s * .9f, y - s * .5f, max(1f, u * .3f), c("#E8412C")); line(x - s * .2f, y - s * 1.2f, x, y - s * .55f, max(1f, u * .3f), c("#E8412C"))
        line(x - s * .2f, y - s * 1.2f, x + s * .5f, y - s * 2.1f, max(1f, u * .55f), c("#2E6FD8")); disc(x + s * .65f, y - s * 2.5f, s * .32f, c("#F2C9A0")); disc(x + s * .62f, y - s * 2.65f, s * .3f, c("#FFB000"))
        line(x + s * .5f, y - s * 2.1f, x + s * .8f, y - s * 1.4f, max(1f, u * .3f), c("#2E6FD8")); line(x - s * .2f, y - s * 1.2f, x + s * .2f, y - s * .5f + sin(sp) * s * .3f, max(1f, u * .35f), c("#1E2A44")) }
    // Herbes et roseaux au premier plan, qui ondulent
    val g = Mul(9)
    for (i in 0 until 140) { val x = g() * W; val yb = H * (.9f + g() * .1f); val h = u * (4f + g() * 9f); val sw = sin(t * TAU * 2 + x * .01f) * u * 1.2f
        line(x, yb, x + sw, yb - h, max(1f, u * (.2f + g() * .2f)), mix(c("#3E5A2A"), c("#B7C77A"), g() * .7f)) }
    for (i in 0 until 7) { val x = W * (.05f + g() * .3f); val yb = H * (.92f + g() * .05f); val h = u * (10f + g() * 6f); val sw = sin(t * TAU * 2 + i) * u
        line(x, yb, x + sw, yb - h, max(1f, u * .3f), c("#4A5E30")); drawOval(c("#6B4A2A"), Offset(x + sw - u * .5f, yb - h - u * 1.2f), Size(u * 1f, u * 3.4f)) }
    // Peupliers à gauche, qui encadrent la scène
    for ((px, ph) in listOf(.04f to .5f, .14f to .44f)) { val base = near + H * .03f; val top = base - H * ph
        rect(W * px - u * .5f, top + H * ph * .3f, u * 1f, H * ph * .7f, c("#4A4034"))
        crown(W * px, top + H * ph * .38f, u * (5f + ph * 4f), H * ph * .4f, (px * 100).toInt(), c("#9DBA6A"), c("#5E7E3E"), c("#2E4424"), -.6f, -.8f, groundY=base) }
    if (d == null) return
    // Lumière de l'heure : voile du crépuscule ou de l'aube, puis la nuit (lune, étoiles, fenêtres et château illuminés)
    if (d.dusk > .01f) drawRect(vg(0f, H, 0f to c("#5A2E8A", .18f * d.dusk), .5f to c("#FF7A3A", .16f * d.dusk), 1f to c("#FFB070", .06f * d.dusk)))
    if (d.dawn > .01f) drawRect(vg(0f, H, 0f to c("#7F8FE0", .12f * d.dawn), .5f to c("#FFB0C0", .14f * d.dawn), 1f to Color.Transparent))
    if (d.night > .01f) {
        val n = d.night
        drawRect(vg(bank - H * .16f, bank - H * .04f, 0f to c("#1A2550", 0f), 1f to c("#1A2550", .82f * n)), topLeft = Offset(0f, bank - H * .16f), size = Size(W, H), blendMode = BlendMode.Multiply)
        loireTown(town, n, lightsOnly = true)
        clipRect(0f, town, W, near) { scale(1f, -.7f, Offset(0f, town)) { loireTown(town, .35f * n, lightsOnly = true) } }
        // Réverbères le long de la levée
        for (k in 0 until 6) { val lx = W * (.1f + k * .17f); val ly2 = near + H * .045f - (lx / W) * H * .01f
            line(lx, ly2, lx, ly2 - u * 6f, max(1f, u * .3f), c("#1A1A22", n)); disc(lx, ly2 - u * 6f, u * .6f, c("#FFE2A0", n)); glow(lx, ly2 - u * 5.5f, u * 7f, c("#FFC46A", .35f * n), BlendMode.Screen) }
    }
}
