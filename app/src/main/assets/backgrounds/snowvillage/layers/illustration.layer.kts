private fun DrawScope.snowvillage(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    snowvillageSky(0f to c("#0E1630"), .35f to c("#1B2A4A"), .6f to c("#3B4F7A"), .72f to c("#6F7FA8"), y1 = H * .75f)
    starField(91, 140, H * .45f, c("#E6EEFF", .7f), t, 2)
    glow(W * .25f, H * .14f, S * .5f, c("#CFE0FF", .25f), BlendMode.Screen); disc(W * .25f, H * .14f, S * .045f, c("#F4F6FF"))
    // Collines enneigées et sapins lointains
    val n1 = Nz(6); val hill = { x: Float -> H * .6f - (n1.at(x / W * 2f, 3) * .5f + .5f) * H * .06f }
    conifers(30, 4, H * .03f, H * .06f, c("#2A3A5E"), hill)
    ridgePts(H * .6f, H * .06f, 80) { u -> n1.at(u * 2f, 3) * .5f + .5f }.let { ridgeFill(it, vg(H * .54f, H, 0f to c("#B9C8E6"), 1f to c("#8FA3CC"))) }
    // Maisons : murs colorés sourds, toits épais de neige, fenêtres chaudes
    data class Hs(val x: Float, val y: Float, val w: Float, val h: Float, val wall: Color)
    val houses = listOf(Hs(.1f, .7f, .2f, .12f, c("#7A4A5A")), Hs(.32f, .69f, .16f, .14f, c("#4A5F7A")), Hs(.66f, .7f, .22f, .12f, c("#7A6248")), Hs(.86f, .71f, .18f, .11f, c("#5A4A70")),
        Hs(.2f, .8f, .26f, .14f, c("#8A5040")), Hs(.78f, .82f, .3f, .15f, c("#4F6A62")))
    // clocher
    run { val x = W * .5f; val y = H * .7f; val w = S * .1f
        rect(x - w / 2, y - H * .2f, w, H * .2f, c("#D9D2C8")); fill(poly(x - w * .65f, y - H * .2f, x, y - H * .3f, x + w * .65f, y - H * .2f), c("#3A4A6A"))
        fill(poly(x - w * .45f, y - H * .215f, x, y - H * .29f, x + w * .1f, y - H * .275f, x - w * .25f, y - H * .2f), c("#F4F8FF", .85f))
        drawRoundRect(c("#FFC870"), topLeft = Offset(x - w * .2f, y - H * .16f), size = Size(w * .4f, H * .05f), cornerRadius = CornerRadius(w * .2f, w * .2f))
        line(x, y - H * .3f, x, y - H * .325f, max(1f, S * .004f), c("#3A4A6A")); line(x - S * .01f, y - H * .315f, x + S * .01f, y - H * .315f, max(1f, S * .004f), c("#3A4A6A")) }
    for ((i, h) in houses.withIndex()) {
        val x = W * h.x; val y = H * h.y; val w = W * h.w; val hh = H * h.h * .55f; val rh = H * h.h * .45f
        drawRect(Brush.horizontalGradient(0f to h.wall, 1f to mix(h.wall, c("#1A1A30"), .35f), startX = x - w / 2, endX = x + w / 2), topLeft = Offset(x - w / 2, y - hh), size = Size(w, hh))
        // cheminée et fumée
        rect(x + w * .2f, y - hh - rh * .8f, w * .1f, rh * .6f, mix(h.wall, c("#1A1A30"), .3f))
        for (k in 0 until 5) { val u = (t * 1.5f + k / 5f + i * .13f) % 1f; glow(x + w * .25f + u * w * .3f + sin(u * 5f + i) * w * .05f, y - hh - rh * (.9f + u * 1.4f), w * (.06f + u * .12f), c("#C9D3EA", .2f * (1f - u))) }
        // toit + neige épaisse débordante
        fill(poly(x - w * .58f, y - hh, x, y - hh - rh, x + w * .58f, y - hh), c("#3A3848"))
        val snow = Path(); snow.moveTo(x - w * .62f, y - hh + rh * .05f); snow.lineTo(x, y - hh - rh * 1.08f); snow.lineTo(x + w * .62f, y - hh + rh * .05f)
        snow.cubicTo(x + w * .5f, y - hh + rh * .2f, x + w * .4f, y - hh + rh * .05f, x + w * .3f, y - hh - rh * .2f); snow.lineTo(x, y - hh - rh * .8f)
        snow.lineTo(x - w * .3f, y - hh - rh * .2f); snow.cubicTo(x - w * .42f, y - hh + rh * .1f, x - w * .52f, y - hh + rh * .22f, x - w * .62f, y - hh + rh * .05f); snow.close()
        drawPath(snow, Brush.linearGradient(0f to c("#FFFFFF"), 1f to c("#B9C8EA"), start = Offset(x - w * .3f, y - hh - rh), end = Offset(x + w * .4f, y - hh)))
        // fenêtres
        val nw = if (w > W * .2f) 3 else 2
        for (k in 0 until nw) { val wx = x - w * .38f + k * w * .76f / (nw - 1) - w * .07f; val wy = y - hh * .72f; val on = .85f + .15f * sin(t * TAU * 3 + i + k)
            glow(wx + w * .07f, wy + hh * .2f, w * .35f, c("#FFB04A", .3f * on), BlendMode.Screen)
            rect(wx, wy, w * .14f, hh * .42f, c("#FFC870", on)); line(wx + w * .07f, wy, wx + w * .07f, wy + hh * .42f, max(1f, S * .002f), mix(h.wall, Color.Black, .3f))
            rect(wx - w * .015f, wy + hh * .42f, w * .17f, hh * .05f, c("#F4F8FF")) }
        // flaque de lumière sur la neige
        ellGlow(x, y + H * .01f, w * .6f, H * .015f, c("#FFC27A", .3f), BlendMode.Screen)
    }
    // Guirlande lumineuse
    val g = Path(); val gx0 = W * .02f; val gx1 = W * .98f; val gy = H * .63f; g.moveTo(gx0, gy)
    for (i in 1..40) { val u = i / 40f; g.lineTo(gx0 + (gx1 - gx0) * u, gy + sin(u * 3.1416f * 4).let { abs(it) } * H * .025f) }
    drawPath(g, c("#1A1A30", .6f), style = Stroke(max(1f, S * .002f)))
    val bulbs = listOf(c("#FF5A5A"), c("#FFD25A"), c("#5AE0A0"), c("#5AB4FF"))
    for (i in 0..40 step 2) { val u = i / 40f; val bx = gx0 + (gx1 - gx0) * u; val by = gy + abs(sin(u * 3.1416f * 4)) * H * .025f + S * .006f; val on = .6f + .4f * sin(t * TAU * 4 + i)
        glow(bx, by, S * .02f, bulbs[(i / 2) % 4].a(.5f * on), BlendMode.Screen); disc(bx, by, S * .004f, bulbs[(i / 2) % 4]) }
    // Premier plan : congère bleutée, reflets scintillants
    val n2 = Nz(9); ridgePts(H * .88f, H * .03f, 60) { u -> n2.at(u * 3f, 3) * .5f + .5f }.let { ridgeFill(it, vg(H * .85f, H, 0f to c("#E8EEFF"), 1f to c("#9FB2DA"))) }
    val sp = Mul(3); for (i in 0 until 40) { val x = sp() * W; val y = H * (.88f + sp() * .1f); val a = .5f + .5f * sin(t * TAU * 5 + i * 2.3f); disc(x, y, S * .002f, c("#FFFFFF", a)) }
    // Neige qui tombe (deux profondeurs)
    for (layer in 0 until 2) {
        val r = Mul(50 + layer); val pts = ArrayList<Offset>()
        for (i in 0 until 140) { val sp2 = .5f + r() * .5f; val u = (r() + t * sp2 * (1 + layer)) % 1f; pts.add(Offset(r() * W + sin(u * TAU * 2 + i) * S * .02f, u * H)) }
        drawPoints(pts, PointMode.Points, c("#FFFFFF", .55f + layer * .3f), strokeWidth = S * (.004f + layer * .004f), cap = StrokeCap.Round)
    }
}
