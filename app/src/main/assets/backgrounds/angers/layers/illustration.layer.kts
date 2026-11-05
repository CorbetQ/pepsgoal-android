private fun DrawScope.angers(t: Float, d: Tod? = null) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val bank = H * .5f; val deck = H * .64f; val quay = H * .84f
    val night = d?.night ?: 0f
    angersLiveSky(d ?: Tod(0f, 0f, 1f, 0f, .2f), t, bank)
    // Ville haute et cathédrale, château au bord de la Maine
    val lit = night
    angersCity(bank, 1f, lit)
    angersCastle(bank + u * 3f, 1f, lit)
    // La Maine : reflets de la ville et du château
    val water = bank + u * 3f
    if (d == null) drawRect(vg(water, quay, 0f to c("#E8D8C2"), .3f to c("#9EBCD6"), 1f to c("#4E7EAE")), Offset(0f, water), Size(W, quay - water + u * 4f))
    else drawRect(vg(water, quay, 0f to d.col("#DCEAF6", "#F2D2C8", "#F2B98E", "#2A3A60"), .3f to d.col("#A8C8E6", "#C8C0DA", "#B88AA6", "#1C2A50"), 1f to d.col("#3F74AE", "#6F8FC8", "#4A5E9A", "#0A1430")), Offset(0f, water), Size(W, quay - water + u * 4f))
    clipRect(0f, water, W, quay) { scale(1f, -.6f, Offset(0f, water)) { angersCity(bank, .3f, lit); angersCastle(bank + u * 3f, .3f, lit) } }
    mist(water + u * 1f, u * 4f, c("#FFF1DE", .35f * (1f - night)))
    val r = Mul(13)
    for (i in 0 until 50) { val v = r(); val y = water + (quay - water) * (.05f + v * .95f); val x = ((r() + t * (.15f + v * .3f)) % 1f) * W; val len = u * (2f + v * 8f)
        line(x, y, x + len, y, max(1f, u * .2f), c("#FFFFFF", (.14f + v * .12f) * (1f - .5f * night))) }
    if (night > .01f) for (k in 0 until 5) { val lx = W * (.55f + k * .11f); for (j in 0 until 6) line(lx - u * (1f + j * .3f), water + u * (2f + j * 2.4f), lx + u * (1f + j * .3f), water + u * (2f + j * 2.4f), max(1f, u * .3f), c("#FFC46A", .3f * night * (1f - j / 6f))) }
    // Pont du tramway : tablier fin, piles en V, garde-corps blanc
    val dk = u * 2.4f
    val pierFoot = deck + (quay - deck) * .55f
    for (px in listOf(.18f, .5f, .82f)) { val x = W * px
        fill(poly(x - u * 3.2f, deck + dk, x - u * .9f, pierFoot, x + u * .9f, pierFoot, x + u * 3.2f, deck + dk), vg(deck, pierFoot, 0f to mix(c("#E2DED6"), c("#2A3248"), .6f * night), 1f to mix(c("#A9A49C"), c("#1A2238"), .6f * night)))
        fill(poly(x, deck + dk, x + u * .9f, pierFoot, x + u * 3.2f, deck + dk), c("#000000", .12f))
        drawOval(c("#2A3A5A", .22f), Offset(x - u * 2.6f, pierFoot - u * .5f), Size(u * 5.2f, u * 1.1f)) }
    rect(-u, deck, W + 2 * u, dk, mix(c("#E9E6E0"), c("#2E3650"), .6f * night)); rect(-u, deck + dk * .6f, W + 2 * u, dk * .4f, mix(c("#B9B4AC"), c("#1E2640"), .6f * night))
    line(-u, deck - u * 3.4f, W + u, deck - u * 3.4f, max(1f, u * .35f), c("#FFFFFF", .9f))
    for (k in 0..24) { val x = W * k / 24f; line(x, deck, x, deck - u * 3.4f, max(1f, u * .18f), c("#FFFFFF", .7f)) }
    // Mâts d'éclairage du pont (côté amont, derrière la rame)
    for (k2 in 0 until 4) { val lx = W * (.06f + k2 * .3f); line(lx, deck, lx, deck - u * 12f, max(1f, u * .35f), c("#C9CDD4")); line(lx, deck - u * 12f, lx + u * 2.4f, deck - u * 12.6f, max(1f, u * .35f), c("#C9CDD4"))
        if (night > .01f) { disc(lx + u * 2.4f, deck - u * 12.4f, u * .6f, c("#FFF2C8", night)); glow(lx + u * 2.4f, deck - u * 12f, u * 8f, c("#FFE2A0", .3f * night), BlendMode.Screen) } }
    // Rame qui traverse lentement la Maine
    val k = (t + .5f) % 1f; val L = W * .92f
    angersTram(W * (-.6f + 2.2f * k), deck, L, night)
    // Reflet de la rame
    clipRect(0f, deck + dk, W, quay) { scale(1f, -.45f, Offset(0f, deck + dk)) { angersTram(W * (-.6f + 2.2f * k), deck, L, night, .25f) } }
    // Quai : mur de schiste et tuffeau, promenade, platanes
    rect(0f, quay - u * 3f, W, u * 3f, c("#6A6878")); for (k2 in 0 until 30) rect(W * k2 / 30f, quay - u * 3f, u * .25f, u * 3f, c("#4A4858"))
    rect(0f, quay - u * 3.6f, W, u * .7f, c("#E6DCCA"))
    drawRect(vg(quay, H, 0f to c("#D9CDB4"), .2f to c("#BBAF96"), 1f to c("#8E8470")), Offset(0f, quay), Size(W, H - quay))
    for (k2 in 0 until 14) line(0f, quay + (H - quay) * (k2 / 14f).pow(1.6f), W, quay + (H - quay) * (k2 / 14f).pow(1.6f), max(1f, u * .15f), c("#7A705E", .25f))
    // Massif fleuri le long du quai et banc
    val fb = quay + (H - quay) * .32f
    fill(Path().apply { moveTo(0f, fb); quadraticTo(W * .5f, fb - u * 2f, W, fb); lineTo(W, fb + u * 5f); quadraticTo(W * .5f, fb + u * 3f, 0f, fb + u * 5f); close() }, c("#5E7E3E"))
    val fr = Mul(23); for (i in 0 until 90) { val x = fr() * W; val y = fb - u * 1.5f + fr() * u * 5f; disc(x, y, u * (.5f + fr() * .5f), listOf(c("#E8607A"), c("#FFD25A"), c("#F49AAE"), c("#FFFFFF"), c("#B48CFF"))[(fr() * 5).toInt().coerceAtMost(4)]) }
    run { val bx = W * .58f; val by = fb + u * 9f; rect(bx, by - u * 3f, u * 12f, u * 1f, c("#7A5A3A")); rect(bx, by - u * 1.6f, u * 12f, u * .9f, c("#8A6A48")); line(bx + u * 1f, by - u * 1f, bx + u * 1f, by + u * 1.5f, max(1f, u * .5f), c("#2A2E36")); line(bx + u * 11f, by - u * 1f, bx + u * 11f, by + u * 1.5f, max(1f, u * .5f), c("#2A2E36")) }
    // Réverbères du quai
    for (k2 in 0 until 3) { val lx = W * (.2f + k2 * .32f); val ly = quay + H * .02f
        line(lx, ly, lx, ly - u * 14f, max(1f, u * .5f), c("#2A2E36")); fill(poly(lx - u * 1.4f, ly - u * 14f, lx, ly - u * 16.5f, lx + u * 1.4f, ly - u * 14f), c("#2A2E36"))
        disc(lx, ly - u * 14.6f, u * .8f, mix(c("#8E96A2"), c("#FFF2C8"), night)); if (night > .01f) glow(lx, ly - u * 14f, u * 12f, c("#FFC46A", .35f * night), BlendMode.Screen) }
    // Platanes qui encadrent la scène
    for ((px, py, sd) in listOf(Triple(-.02f, .79f, 3), Triple(1.02f, .81f, 5))) { val x = W * px; val y = H * py
        rect(x - u * 1.2f, y - u * 26f, u * 2.4f, u * 30f, c("#8E8A6E")); for (m in 0 until 6) disc(x + (m % 2 - .5f) * u * 1.4f, y - u * (4f + m * 4f), u * .7f, c("#C9C29A", .6f))
        crown(x, y - u * 34f, u * 16f, u * 13f, sd, mix(c("#A9C27A"), c("#2A3A3A"), .7f * night), mix(c("#6E8E4A"), c("#1E2A2E"), .7f * night), mix(c("#3A5428"), c("#121A20"), .7f * night), if (px < .5f) -.3f else -.9f, -.8f, groundY=y+u*4f) }
    // Lumière de l'heure
    if (d == null) return
    if (d.dusk > .01f) drawRect(vg(0f, H, 0f to c("#5A2E8A", .16f * d.dusk), .5f to c("#FF7A3A", .14f * d.dusk), 1f to c("#FFB070", .05f * d.dusk)))
    if (d.dawn > .01f) drawRect(vg(0f, H, 0f to c("#7F8FE0", .1f * d.dawn), .5f to c("#FFB0C0", .12f * d.dawn), 1f to Color.Transparent))
    if (night > .01f) drawRect(vg(bank, H, 0f to c("#1A2550", .25f * night), 1f to c("#0A1030", .55f * night)), Offset(0f, bank), Size(W, H - bank), blendMode = BlendMode.Multiply)
}
