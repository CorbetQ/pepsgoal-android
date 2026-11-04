private fun DrawScope.angersCastle(base: Float, a: Float, lit: Float) {
    val W = size.width; val u = min(W, size.height) / 100f
    // La nuit, la pierre et l'ardoise passent au bleu nuit (les lumières restent vives)
    fun nc(h: String, al: Float = a) = mix(c(h), c("#1C2238"), .72f * lit).a(al)
    val schist = nc("#4C4754", a); val schistLit = nc("#6A6472", a); val tuf = nc("#E4DACA", .85f * a); val tufSh = nc("#B9AF9E", a)
    val wallTop = base - u * 17f; val x0 = W * .5f; val x1 = W * 1.06f
    // Logis et chapelle derrière l'enceinte : toits d'ardoise et clocheton
    fill(poly(W * .66f, wallTop, W * .7f, wallTop - u * 7f, W * .84f, wallTop - u * 7f, W * .88f, wallTop), nc("#56657E", a))
    rect(W * .74f, wallTop - u * 13f, u * 1.6f, u * 6f, tuf); fill(poly(W * .74f - u * .6f, wallTop - u * 13f, W * .74f + u * .8f, wallTop - u * 18f, W * .74f + u * 2.2f, wallTop - u * 13f), nc("#56657E", a))
    // Jardins au sommet des remparts
    canopy(9, 77, u * 1.4f, u * 2.4f, nc("#5E7A4A", a), nc("#93AE6A", a), true, { wallTop + u * .6f }, x0 + u * 2f, x1)
    // Courtine : schiste sombre et fines bandes de tuffeau
    fill(poly(x0, base, x0 + u * 1.5f, wallTop, x1, wallTop, x1, base), schist)
    var y = wallTop + u * 3f; while (y < base) { rect(x0 + u * 1.5f, y, x1 - x0, u * .45f, tuf); y += u * 5f }
    drawRect(vg(wallTop, base, 0f to c("#FFFFFF", .06f * a), 1f to c("#000000", .18f * a)), Offset(x0, wallTop), Size(x1 - x0, base - wallTop))
    // Tours rondes : talus élargi à la base, ombrage cylindrique, bandes de tuffeau, couronnement plat
    for ((k, tx) in listOf(.53f, .65f, .77f, .89f, 1.01f).withIndex()) {
        val cx = W * tx; val tw = u * 9.5f; val top = wallTop - u * (4.5f + (k % 2) * 1.2f)
        fill(poly(cx - tw * .72f, base, cx - tw / 2, base - u * 6f, cx + tw / 2, base - u * 6f, cx + tw * .72f, base), schist)
        rect(cx - tw / 2, top, tw, base - u * 6f - top, schist)
        var by = top + u * 2.4f; while (by < base - u * 1f) { val wb = if (by > base - u * 6f) tw * (.5f + .22f * (by - (base - u * 6f)) / (u * 6f)) else tw / 2; rect(cx - wb, by, wb * 2, u * .5f, tuf); by += u * 5f }
        drawRect(lg(cx - tw / 2, 0f, cx + tw / 2, 0f, 0f to c("#FFFFFF", .16f * a), .35f to c("#FFFFFF", 0f), .7f to c("#000000", .12f * a), 1f to c("#000000", .32f * a)), Offset(cx - tw * .72f, top), Size(tw * 1.44f, base - top))
        rect(cx - tw * .56f, top - u * 1.2f, tw * 1.12f, u * 1.4f, schistLit); rect(cx - tw * .56f, top - u * .2f, tw * 1.12f, u * .5f, tufSh)
        for (m in 0 until 4) rect(cx - tw * .5f + m * tw * .28f, top - u * 2.2f, tw * .14f, u * 1f, schistLit)
        rect(cx - u * .4f, top + u * 5f, u * .8f, u * 1.4f, nc("#1A1A22", a))
    }
    // Projecteurs la nuit : le château s'illumine par le bas
    if (lit > .01f) drawRect(vg(wallTop - u * 8f, base, 0f to c("#FFC46A", 0f), 1f to c("#FFB04A", .42f * lit * a)), Offset(x0, wallTop - u * 8f), Size(x1 - x0, base - wallTop + u * 8f), blendMode = BlendMode.Screen)
}

private fun DrawScope.angersCity(base: Float, a: Float, lit: Float) {
    val W = size.width; val u = min(W, size.height) / 100f
    // La nuit, la pierre et l'ardoise passent au bleu nuit (les lumières restent vives)
    fun nc(h: String, al: Float = a) = mix(c(h), c("#1C2238"), .72f * lit).a(al)
    val tuf = nc("#F1E8D8", a); val tufSh = nc("#CFC2AC", a); val slate = nc("#56657E", a); val slateLit = nc("#7E8EA8", a); val win = nc("#6A5A48", a)
    // Colline et maisons
    fill(Path().apply { moveTo(-u, base); quadraticTo(W * .2f, base - u * 7f, W * .55f, base - u * 1f); lineTo(W * .55f, base + u); lineTo(-u, base + u); close() }, nc("#7E9A62", a))
    val r = Mul(57); var x = -u
    while (x < W * .52f) {
        val w = u * (4f + r() * 4f); val gy = base - u * 7f * (1f - ((x / (W * .55f)) - .38f).let { it * it } * 2.2f).coerceIn(0f, 1f) * .9f
        val h = u * (3f + r() * 2.5f); val rh = u * (2.2f + r() * 1.5f)
        rect(x, gy - h, w, h + u * 2f, tuf); rect(x + w * .6f, gy - h, w * .4f, h + u * 2f, tufSh)
        fill(poly(x - u * .3f, gy - h, x + w * .5f, gy - h - rh, x + w + u * .3f, gy - h), slate); fill(poly(x - u * .3f, gy - h, x + w * .5f, gy - h - rh, x + w * .5f, gy - h), slateLit)
        for (k in 0 until (w / (u * 2.2f)).toInt()) { val wx = x + u * (.8f + k * 2.2f); val wy = gy - h * .6f
            if (lit > .3f && (k + (x / u).toInt()) % 2 == 0) { rect(wx, wy, u * .7f, u, c("#FFD27A", a)); glow(wx + u * .35f, wy + u * .5f, u * 2.2f, c("#FFB04A", .3f * a * lit), BlendMode.Screen) } else rect(wx, wy, u * .7f, u, win) }
        x += w + u * (.1f + r())
    }
    // Cathédrale : nef, façade à deux flèches et tour-lanterne Renaissance entre elles
    val cx = W * .25f; val cb = base - u * 6.5f
    rect(cx + u * 3f, cb - u * 10f, u * 14f, u * 10f, tufSh); fill(poly(cx + u * 2.5f, cb - u * 10f, cx + u * 10f, cb - u * 15f, cx + u * 17.5f, cb - u * 10f), slate)
    rect(cx - u * 6f, cb - u * 17f, u * 12f, u * 17f, tuf); rect(cx + u * 1.5f, cb - u * 17f, u * 4.5f, u * 17f, tufSh)
    for (sx in listOf(cx - u * 4.6f, cx + u * 4.6f)) {
        rect(sx - u * 1.7f, cb - u * 27f, u * 3.4f, u * 10f, tuf); rect(sx + u * .2f, cb - u * 27f, u * 1.5f, u * 10f, tufSh)
        fill(poly(sx - u * 1.9f, cb - u * 27f, sx, cb - u * 42f, sx + u * 1.9f, cb - u * 27f), slate); fill(poly(sx - u * 1.9f, cb - u * 27f, sx, cb - u * 42f, sx, cb - u * 27f), slateLit)
        rect(sx - u * .4f, cb - u * 24f, u * .8f, u * 2.4f, win)
    }
    rect(cx - u * 2f, cb - u * 31f, u * 4f, u * 14f, tuf); rect(cx + u * .4f, cb - u * 31f, u * 1.6f, u * 14f, tufSh)
    drawArc(slate, 180f, 180f, true, Offset(cx - u * 2.2f, cb - u * 33.2f), Size(u * 4.4f, u * 4.4f)); line(cx, cb - u * 33f, cx, cb - u * 35.5f, max(1f, u * .3f), nc("#C9A45E", a))
    disc(cx, cb - u * 13f, u * 1.8f, nc("#7A6A58", a)); drawCircle(nc("#A8987E", a), u * 1.8f, Offset(cx, cb - u * 13f), style = Stroke(max(1f, u * .35f)))
    for (k in 0..2) rect(cx - u * 4.6f + k * u * 4.6f - u * .9f, cb - u * 5f, u * 1.8f, u * 5f, nc("#5A4A3A", a))
    if (lit > .01f) glow(cx, cb - u * 20f, u * 20f, c("#FFD9A0", .22f * lit * a), BlendMode.Screen)
}

private fun DrawScope.angersTram(cx: Float, rail: Float, L: Float, night: Float, a: Float = 1f) {
    val u = min(size.width, size.height) / 100f
    val top = rail - u * 10.5f; val bot = rail - u * 1.4f; val x0 = cx - L / 2; val x1 = cx + L / 2
    // Caisse blanche, nez arrondi vitré à droite, arrière arrondi
    val body = Path().apply {
        moveTo(x0 + u * 2f, bot); lineTo(x1 - u * 4f, bot); quadraticTo(x1 + u * .8f, bot, x1 + u * .8f, bot - u * 4f)
        quadraticTo(x1 + u * .6f, top + u * .6f, x1 - u * 5f, top); lineTo(x0 + u * 2.5f, top); quadraticTo(x0, top, x0, top + u * 2.5f); lineTo(x0, bot - u * 1.5f); quadraticTo(x0, bot, x0 + u * 2f, bot); close() }
    val nb = { h: String -> mix(c(h), c("#2A3248"), .55f * night).a(a) }
    fill(body, vg(top, bot, 0f to nb("#FFFFFF"), .55f to nb("#EEF0F3"), 1f to nb("#C9CDD4")))
    // Équipements de toiture (pas de pantographe)
    for (k in 0 until 4) { val bx = x0 + L * (.12f + k * .22f); fill(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(bx, top - u * 1.3f, bx + L * .11f, top + u * .2f, CornerRadius(u * .6f))) }, c("#D5D9E0", a)) }
    // Bandeau vitré continu (éclairé la nuit), séparé par les modules et les portes
    val wt = top + u * 2f; val wb = top + u * 6.4f
    fill(Path().apply { moveTo(x0 + u * 1.2f, wb); lineTo(x0 + u * 1.2f, wt + u * 1f); quadraticTo(x0 + u * 1.2f, wt, x0 + u * 2.4f, wt); lineTo(x1 - u * 6f, wt); lineTo(x1 - u * 6f, wb); close() },
        vg(wt, wb, 0f to mix(c("#3A4A62"), c("#FFE8B4"), night).a(a), 1f to mix(c("#161B24"), c("#F2C986"), night).a(a)))
    // Reflets du ciel sur les vitres (le jour)
    if (night < .9f) clipRect(x0 + u * 1.2f, wt, x1 - u * 6f, wb) { var rx = x0; while (rx < x1) { fill(poly(rx, wb, rx + u * 3f, wt, rx + u * 4.2f, wt, rx + u * 1.2f, wb), c("#BFD6F0", .16f * (1f - night) * a)); rx += u * 9f } }
    if (night > .01f) for (k in 0 until 10) glow(x0 + L * (.06f + k * .085f), (wt + wb) / 2, u * 4f, c("#FFC46A", .18f * night * a), BlendMode.Screen)
    // Pare-brise bombé
    fill(Path().apply { moveTo(x1 - u * 5.4f, wt); quadraticTo(x1 - u * .2f, wt + u * .2f, x1 + u * .3f, wb + u * 1.4f); lineTo(x1 - u * 5.4f, wb + u * 1.4f); close() }, vg(wt, wb, 0f to c("#2A3240", a), 1f to c("#0E1218", a)))
    line(x1 - u * 4.6f, wt + u * .8f, x1 - u * 2.4f, wb, max(1f, u * .25f), c("#FFFFFF", .25f * a))
    // Girouette : ligne A
    rect(x1 - u * 4.8f, wt + u * .5f, u * 2.4f, u * 1.2f, c("#10141A", a)); rect(x1 - u * 4.5f, wt + u * .75f, u * .8f, u * .7f, c("#FF9A2A", a))
    // Modules et portes
    val mods = 5
    for (k in 1 until mods) { val mx = x0 + L * k / mods; line(mx, top + u * .4f, mx, bot - u * .4f, max(1f, u * .22f), c("#8E96A2", a)) }
    for (k in 0 until mods) { val dx = x0 + L * (k + .32f) / mods; fill(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(dx, wt - u * .4f, dx + u * 3.4f, bot - u * .6f, CornerRadius(u * .6f))) }, c("#DCE0E6", a))
        line(dx + u * 1.7f, wt, dx + u * 1.7f, bot - u * .8f, max(1f, u * .2f), c("#9AA2AE", a)); rect(dx + u * .4f, wt + u * .2f, u * 2.6f, u * 3.6f, mix(c("#2A3240"), c("#FFE2A6"), night).a(a)) }
    // Bande arc-en-ciel sur la jupe
    RAINBOW.forEachIndexed { i, h -> rect(x0 + u * 1.5f, bot - u * 2.8f + i * u * .38f, L - u * 3.5f, u * .38f, c(h, .9f * a)) }
    // Bogies et roues
    // (Citadis à 5 modules : bogies sous les modules 1, 3 et 5, en retrait des extrémités, à moitié cachés par la jupe)
    for (k in listOf(0, 2, 4)) {
        val bx = x0 + L * (k + .5f) / mods + (if (k == 0) u * 1.5f else if (k == mods - 1) -u * 1.5f else 0f)
        fill(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(bx - u * 3f, bot - u * .3f, bx + u * 3f, rail - u * .2f, CornerRadius(u * .5f))) }, c("#2A2E36", a))
        for (wx in listOf(-1.6f, 1.6f)) { disc(bx + u * wx, rail - u * .6f, u * .95f, c("#16181D", a)); disc(bx + u * wx, rail - u * .6f, u * .35f, c("#6A707A", a)) }
        rect(bx - u * 3.2f, bot - u * .6f, u * 6.4f, u * .5f, c("#3A3F48", a))
    }
    // Phares (et leur halo la nuit)
    disc(x1 - u * .4f, bot - u * 2.2f, u * .6f, c("#FFFBEA", a))
    if (night > .01f) { glow(x1 + u * 2f, bot - u * 2.2f, u * 12f, c("#FFF0C8", .35f * night * a), BlendMode.Screen); ellGlow(x1 + u * 14f, rail + u * .2f, u * 14f, u * 1.4f, c("#FFE8B0", .25f * night * a), BlendMode.Screen) }
}
