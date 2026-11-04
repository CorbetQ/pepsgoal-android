private val EIF_W = floatArrayOf(.208f, .175f, .118f, .088f, .066f, .052f, .041f, .031f, .022f, .017f, .004f)

private val EIF_H = floatArrayOf(0f, .06f, .19f, .3f, .39f, .5f, .62f, .75f, .86f, .92f, 1f)

private fun DrawScope.eiffel(cx: Float, base: Float, eh: Float, col: Color, gap: Color, lit: Float, t: Float) {
    fun y(h: Float) = base - eh * h
    fun hw(h: Float) = eh * tab(h, EIF_H, EIF_W)
    fun leg(h: Float) = eh * (.07f - .036f * (h / .39f).coerceAtMost(1f))
    val sw = max(1f, eh * .0022f)
    // Piliers jusqu'au deuxième étage : deux montants qui se rejoignent
    for (side in listOf(-1f, 1f)) {
        val n = 26; val pth = Path()
        for (i in 0..n) { val h = .39f * i / n; val x = cx + side * hw(h); if (i == 0) pth.moveTo(x, y(h)) else pth.lineTo(x, y(h)) }
        for (i in n downTo 0) { val h = .39f * i / n; pth.lineTo(cx + side * (hw(h) - leg(h)), y(h)) }
        pth.close(); fill(pth, col)
        // Treillis : croix de Saint-André entre les arêtes du pilier
        var h = .01f
        while (h < .37f) { val h2 = h + .035f
            line(cx + side * hw(h), y(h), cx + side * (hw(h2) - leg(h2)), y(h2), sw, gap); line(cx + side * (hw(h) - leg(h)), y(h), cx + side * hw(h2), y(h2), sw, gap); h = h2 }
    }
    // Grande arche décorative sous le premier étage
    val ax = hw(.02f) - leg(.02f)
    drawPath(Path().apply { moveTo(cx - ax, y(.02f)); quadraticTo(cx, y(.3f), cx + ax, y(.02f)) }, col, style = Stroke(eh * .011f))
    // Fût au-dessus du deuxième étage
    val n = 30; val shaft = Path()
    for (i in 0..n) { val h = .39f + .53f * i / n; if (i == 0) shaft.moveTo(cx - hw(h), y(h)) else shaft.lineTo(cx - hw(h), y(h)) }
    for (i in n downTo 0) { val h = .39f + .53f * i / n; shaft.lineTo(cx + hw(h), y(h)) }
    shaft.close(); fill(shaft, col)
    var h = .4f
    while (h < .9f) { val h2 = h + .045f; line(cx - hw(h), y(h), cx + hw(h2), y(h2), sw, gap); line(cx + hw(h), y(h), cx - hw(h2), y(h2), sw, gap); h = h2 }
    line(cx, y(.39f), cx, y(.92f), sw, gap.a(gap.alpha * .6f))
    // Étages : plateformes et galeries
    for ((ph, th, ext) in listOf(Triple(.19f, .022f, .014f), Triple(.39f, .014f, .008f))) {
        rect(cx - hw(ph) - eh * ext, y(ph) - eh * th, (hw(ph) + eh * ext) * 2, eh * th, col)
        var gx = cx - hw(ph) - eh * ext + eh * .006f
        while (gx < cx + hw(ph) + eh * ext - eh * .006f) { rect(gx, y(ph) - eh * th * .7f, eh * .004f, eh * th * .45f, gap); gx += eh * .011f }
    }
    // Sommet : campanile, phare et antenne
    rect(cx - hw(.9f) * 1.5f, y(.935f), hw(.9f) * 3f, eh * .02f, col)
    fill(poly(cx - hw(.93f) * 1.1f, y(.935f), cx, y(.975f), cx + hw(.93f) * 1.1f, y(.935f)), col)
    line(cx, y(.97f), cx, y(1.04f), max(1f, eh * .004f), col)
    // Illumination dorée le soir, phare et scintillement
    if (lit > .01f) {
        glow(cx, y(.35f), eh * .5f, c("#FFB04A", .18f * lit), BlendMode.Screen)
        val r = Mul(88)
        for (i in 0 until 70) { val hh = r() * .95f; val x = cx + (r() * 2f - 1f) * hw(hh) * .9f; val tw = sin(t * TAU * 40 + i * 2.3f)
            if (tw > .6f) disc(x, y(hh), max(1f, eh * .003f), c("#FFFFFF", lit * (tw - .6f) * 2.5f)) }
        val beam = t * TAU * 6f
        for (k in 0..1) { val a = beam + k * 3.1416f; val bx = cos(a)
            drawPath(Path().apply { moveTo(cx, y(1f)); lineTo(cx + bx * eh * .9f, y(1f) - eh * .025f); lineTo(cx + bx * eh * .9f, y(1f) + eh * .025f); close() }, c("#FFF2C8", .05f * lit * abs(bx)), blendMode = BlendMode.Screen) }
        glow(cx, y(1f), eh * .04f, c("#FFF2C8", .8f * lit), BlendMode.Screen)
    }
}

private fun DrawScope.haussmann(x: Float, w: Float, cornice: Float, bottom: Float, u: Float, seed: Int, night: Float, light: Float) {
    val r = Mul(seed)
    // La nuit, pierre et zinc passent au bleu (les fenêtres allumées restent chaudes)
    val nt = { col: Color -> mix(col, c("#232A44"), .62f * night) }
    val stone = nt(mix(c("#E9DCC4"), c("#D8C8AA"), r())); val stoneSh = mix(stone, c("#8E7E68"), .35f)
    val zinc = nt(c("#7D8AA2")); val zincLit = nt(c("#A8B4C8")); val iron = c("#1E1E26")
    val fl = u * 6.2f
    // Façade et ombre portée côté droit (lumière du couchant à gauche)
    rect(x, cornice, w, bottom - cornice, stone)
    drawRect(lg(x, 0f, x + w, 0f, 0f to c("#FFF4DE", .12f * light), .6f to Color.Transparent, 1f to c("#3A2E2A", .18f)), Offset(x, cornice), Size(w, bottom - cornice))
    // Refends (joints horizontaux) des étages bas
    for (k in 0 until 3) line(x, cornice + fl * (4f + k * .25f), x + w, cornice + fl * (4f + k * .25f), max(1f, u * .1f), stoneSh.a(.35f))
    // Fenêtres : hautes portes-fenêtres à encadrement mouluré, linteaux sculptés aux étages nobles
    val cols = max(2, (w / (u * 5.4f)).toInt()); val gapX = w / cols
    for (f in 0 until 5) { val fy = cornice + u * 1.6f + f * fl
        for (k in 0 until cols) { val wx = x + gapX * (k + .5f) - u * 1.05f
            rect(wx - u * .35f, fy - u * .3f, u * 2.8f, u * 4.3f, mix(stone, c("#FFFFFF"), .15f))
            val litW = r() < (.25f + .45f * night)
            drawRect(vg(fy, fy + u * 3.7f, 0f to if (litW) c("#FFE2A0") else c("#3A4258"), 1f to if (litW) c("#F2B05A") else c("#1E2232")), Offset(wx, fy), Size(u * 2.1f, u * 3.7f))
            if (litW && night > .05f) glow(wx + u * 1.05f, fy + u * 1.8f, u * 4f, c("#FFC46A", .25f * night), BlendMode.Screen)
            line(wx + u * 1.05f, fy, wx + u * 1.05f, fy + u * 3.7f, max(1f, u * .15f), mix(stone, c("#FFFFFF"), .2f))
            line(wx, fy + u * 1.4f, wx + u * 2.1f, fy + u * 1.4f, max(1f, u * .12f), mix(stone, c("#FFFFFF"), .2f))
            if (f == 3) fill(poly(wx - u * .6f, fy - u * .4f, wx + u * 1.05f, fy - u * 1.5f, wx + u * 2.7f, fy - u * .4f), stoneSh.a(.8f))
            // Garde-corps individuels (étages sans balcon filant)
            if (f == 1 || f == 2) for (b in 0 until 6) line(wx + b * u * .42f, fy + u * 2.6f, wx + b * u * .42f, fy + u * 3.7f, max(1f, u * .1f), iron)
        }
        // Balcons filants au 5e (f = 0) et au 2e (f = 3) : dalle, rambarde et volutes de fer forgé
        if (f == 0 || f == 3) { val by = fy + u * 3.8f
            rect(x, by, w, u * .55f, stoneSh); rect(x, by - u * 1.5f, w, u * .22f, iron)
            var bx = x; while (bx < x + w) { line(bx, by - u * 1.5f, bx, by, max(1f, u * .1f), iron); drawCircle(iron, u * .32f, Offset(bx + u * .45f, by - u * .75f), style = Stroke(max(1f, u * .08f))); bx += u * .9f }
            drawRect(c("#000000", .12f), Offset(x, by + u * .55f), Size(w, u * .6f)) }
    }
    // Corniche saillante
    rect(x - u * .6f, cornice - u * 1.2f, w + u * 1.2f, u * 1.3f, mix(stone, c("#FFFFFF"), .2f)); rect(x - u * .6f, cornice + u * .1f, w + u * 1.2f, u * .5f, c("#000000", .15f))
    // Combles à la Mansart : brisis presque vertical, terrasson en pente douce, lucarnes
    val brisis = u * 5.2f; val terr = u * 2.2f
    fill(poly(x - u * .4f, cornice - u * 1.2f, x + u * .8f, cornice - u * 1.2f - brisis, x + w - u * .8f, cornice - u * 1.2f - brisis, x + w + u * .4f, cornice - u * 1.2f), zinc)
    fill(poly(x + u * .8f, cornice - u * 1.2f - brisis, x + u * 3f, cornice - u * 1.2f - brisis - terr, x + w - u * 3f, cornice - u * 1.2f - brisis - terr, x + w - u * .8f, cornice - u * 1.2f - brisis), zincLit)
    var sx = x + u * 1.2f; while (sx < x + w - u) { line(sx, cornice - u * 1.2f, sx + u * .2f, cornice - u * 1.2f - brisis, max(1f, u * .08f), c("#5E6A80", .6f)); sx += u * 1.1f }
    for (k in 0 until cols) { val lx = x + gapX * (k + .5f) - u * 1.1f; val ly = cornice - u * 1.2f - brisis + u * .6f
        val litL = r() < (.2f + .5f * night)
        rect(lx - u * .4f, ly, u * 3f, u * 3.8f, if (k % 2 == 0) mix(stone, c("#FFFFFF"), .1f) else zincLit)
        if (k % 2 == 0) fill(Path().apply { moveTo(lx - u * .6f, ly); quadraticTo(lx + u * 1.1f, ly - u * 2f, lx + u * 2.8f, ly); close() }, mix(stone, c("#FFFFFF"), .1f))
        else fill(poly(lx - u * .6f, ly, lx + u * 1.1f, ly - u * 1.6f, lx + u * 2.8f, ly), zinc)
        rect(lx, ly + u * .5f, u * 2.2f, u * 3f, if (litL) c("#FFD27A") else c("#2E3448"))
        if (litL && night > .05f) glow(lx + u * 1.1f, ly + u * 2f, u * 3.5f, c("#FFB04A", .3f * night), BlendMode.Screen) }
    // Souche de cheminée sur le mur mitoyen, rangée de boisseaux en terre cuite
    val cxs = x + w - u * 2f; val ctop = cornice - u * 1.2f - brisis - terr - u * 2.5f
    rect(cxs - u * 1.6f, ctop, u * 3.6f, cornice - ctop - u * 2f, mix(stone, c("#B8A488"), .5f)); rect(cxs - u * 1.9f, ctop, u * 4.2f, u * .6f, stoneSh)
    for (p in 0 until 4) { val px = cxs - u * 1.4f + p * u * .9f; fill(poly(px, ctop, px + u * .15f, ctop - u * 1.6f, px + u * .65f, ctop - u * 1.6f, px + u * .8f, ctop), c("#C2683E")); rect(px + u * .1f, ctop - u * 1.8f, u * .6f, u * .25f, c("#A85432")) }
}
