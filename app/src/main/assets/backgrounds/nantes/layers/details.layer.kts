private fun DrawScope.titan(x: Float, base: Float, u: Float, col: Color, dark: Color, lit: Float) {
    val tw = u * 7f; val top = base - u * 30f; val jy = top - u * 4f
    // Pieds et pylône
    for (sx in listOf(-1f, 1f)) line(x + sx * tw * .7f, base, x + sx * tw * .45f, top, max(1f, u * .9f), col)
    var y = base; var k = 0
    while (y > top + u) { val y2 = y - u * 3.2f; val f = (base - y) / (base - top); val f2 = (base - y2) / (base - top)
        val a = tw * (.7f - .25f * f); val b = tw * (.7f - .25f * f2)
        line(x - a, y, x + b, y2, max(1f, u * .3f), col); line(x + a, y, x - b, y2, max(1f, u * .3f), col); line(x - a, y, x + a, y, max(1f, u * .35f), col); y = y2; k++ }
    // Machinerie et cabine
    rect(x - tw * .6f, top - u * 4f, tw * 1.2f, u * 4.2f, col); rect(x - tw * .55f, top - u * 3.2f, tw * .4f, u * 1.6f, mix(dark, c("#FFD27A"), lit))
    // Flèche (vers la gauche, au-dessus du fleuve) et contre-flèche avec son contrepoids
    val jl = x - u * 36f; val jr = x + u * 12f
    line(jl, jy, jr, jy, max(1f, u * .8f), col); line(jl + u * 2f, jy + u * 2.4f, x - tw * .6f, jy + u * 2.4f, max(1f, u * .6f), col)
    var jx = jl + u * 2f; while (jx < x - tw * .6f) { line(jx, jy + u * 2.4f, jx + u * 1.6f, jy, max(1f, u * .25f), col); line(jx + u * 1.6f, jy, jx + u * 3.2f, jy + u * 2.4f, max(1f, u * .25f), col); jx += u * 3.2f }
    line(x, jy - u * 6f, jl + u * 4f, jy, max(1f, u * .3f), col); line(x, jy - u * 6f, jr, jy, max(1f, u * .3f), col); line(x, jy - u * 6f, x, top - u * 4f, max(1f, u * .6f), col)
    rect(jr - u * 4f, jy, u * 4f, u * 3.2f, dark)
    // Câble et crochet
    line(jl + u * 6f, jy, jl + u * 6f, jy + u * 9f, max(1f, u * .15f), dark); rect(jl + u * 5.4f, jy + u * 9f, u * 1.2f, u * 1f, dark)
    if (lit > .01f) { glow(x, top - u * 2f, u * 22f, c("#FFE08A", .25f * lit), BlendMode.Screen); for (i in 0 until 6) disc(jl + i * u * 8f, jy, u * .5f, c("#FFF2C8", lit)) }
}

private fun DrawScope.elephant(cx: Float, ground: Float, u: Float, t: Float, night: Float) {
    val wood = mix(c("#C9A274"), c("#3A3446"), .6f * night); val woodD = mix(c("#9A7650"), c("#2A2436"), .6f * night); val steel = mix(c("#3A3632"), c("#14121C"), .5f * night)
    val leather = mix(c("#6E4E36"), c("#2A2030"), .55f * night)
    val step = t * TAU * 3f; val sway = sin(step) * u * .5f
    val bodyY = ground - u * 34f + sway
    // Pattes (pistons visibles), deux par deux en alternance
    for ((i, lx) in listOf(-14f, -6f, 8f, 16f).withIndex()) {
        val lift = max(0f, sin(step + i * 3.1416f / 2)) * u * 2.2f; val far = i % 2 == 1
        val x = cx + lx * u; val top = bodyY + u * 12f; val foot = ground - lift
        val col = if (far) woodD else wood
        rect(x - u * 3f, top, u * 6f, foot - top - u * 2f, col); for (k in 1..3) line(x - u * 3f, top + k * u * 3.4f, x + u * 3f, top + k * u * 3.4f, max(1f, u * .2f), steel.a(.6f))
        line(x + u * 1.8f, top, x + u * 1.8f, foot - u * 3f, max(1f, u * .55f), steel)
        drawOval(steel, Offset(x - u * 3.6f, foot - u * 2.6f), Size(u * 7.2f, u * 2.6f))
    }
    // Corps : coque de planches et ossature d'acier
    val body = Path().apply { moveTo(cx - u * 18f, bodyY + u * 14f); cubicTo(cx - u * 22f, bodyY - u * 4f, cx - u * 10f, bodyY - u * 10f, cx + u * 6f, bodyY - u * 9f)
        cubicTo(cx + u * 20f, bodyY - u * 8f, cx + u * 24f, bodyY + u * 4f, cx + u * 20f, bodyY + u * 14f); close() }
    fill(body, vg(bodyY - u * 10f, bodyY + u * 14f, 0f to mix(wood, c("#FFF2DC"), .15f * (1f - night)), 1f to woodD))
    clipPath(body) { for (k in -10..10) line(cx + k * u * 2.2f, bodyY - u * 12f, cx + k * u * 2.2f + u * 1f, bodyY + u * 16f, max(1f, u * .15f), woodD.a(.5f)); for (k in 0 until 4) line(cx - u * 24f, bodyY - u * 4f + k * u * 5f, cx + u * 24f, bodyY - u * 4f + k * u * 5f, max(1f, u * .35f), steel.a(.7f)) }
    drawPath(body, steel, style = Stroke(max(1f, u * .5f)))
    // Queue
    drawPath(Path().apply { moveTo(cx + u * 21f, bodyY + u * 2f); quadraticTo(cx + u * 25f, bodyY + u * 6f + sway, cx + u * 23.5f, bodyY + u * 11f) }, steel, style = Stroke(max(1f, u * .5f)))
    // Terrasse sur le dos : plancher, garde-corps, auvent et passagers
    val deck = bodyY - u * 9.5f
    rect(cx - u * 12f, deck - u * .8f, u * 26f, u * 1.2f, woodD)
    for (k in 0..13) line(cx - u * 12f + k * u * 2f, deck - u * .8f, cx - u * 12f + k * u * 2f, deck - u * 3.4f, max(1f, u * .18f), steel)
    line(cx - u * 12f, deck - u * 3.4f, cx + u * 14f, deck - u * 3.4f, max(1f, u * .3f), steel)
    fill(poly(cx - u * 4f, deck - u * 8f, cx + u * 10f, deck - u * 8f, cx + u * 11.5f, deck - u * 6.6f, cx - u * 5.5f, deck - u * 6.6f), mix(c("#B23A2E"), c("#3A2030"), .5f * night)); for (k in listOf(-4f, 10f)) line(cx + k * u, deck - u * 6.6f, cx + k * u, deck - u * .8f, max(1f, u * .3f), steel)
    val r = Mul(7); for (k in 0 until 7) { val px = cx - u * 10f + k * u * 3.4f + r() * u; val col = listOf(c("#E8412C"), c("#2E6FD8"), c("#F2C230"), c("#3AA060"), c("#8A4FC0"))[k % 5]
        rect(px - u * .45f, deck - u * 3f, u * .9f, u * 2.2f, mix(col, c("#1A1A22"), .5f * night)); disc(px, deck - u * 3.5f, u * .55f, mix(c("#E8C4A0"), c("#2A2430"), .5f * night)) }
    // Tête, oreille qui bat, œil
    val hx = cx - u * 21f; val hy = bodyY - u * 1f
    fill(Path().apply { addOval(androidx.compose.ui.geometry.Rect(hx - u * 7f, hy - u * 8f, hx + u * 7f, hy + u * 8f)) }, vg(hy - u * 8f, hy + u * 8f, 0f to mix(wood, c("#FFF2DC"), .12f * (1f - night)), 1f to woodD))
    drawOval(steel, Offset(hx - u * 7f, hy - u * 8f), Size(u * 14f, u * 16f), style = Stroke(max(1f, u * .5f)))
    val flap = sin(t * TAU * 5f) * 8f
    rotate(flap, Offset(hx + u * 4f, hy - u * 5f)) {
        val ear = Path().apply { moveTo(hx + u * 4f, hy - u * 6f); cubicTo(hx + u * 14f, hy - u * 10f, hx + u * 16f, hy + u * 6f, hx + u * 6f, hy + u * 9f); cubicTo(hx + u * 3f, hy + u * 4f, hx + u * 2f, hy - u * 2f, hx + u * 4f, hy - u * 6f); close() }
        fill(ear, leather); drawPath(ear, steel, style = Stroke(max(1f, u * .4f)))
        for (k in 1..3) drawPath(Path().apply { moveTo(hx + u * 5f, hy - u * 4f + k * u * 2.5f); quadraticTo(hx + u * 10f, hy - u * 4f + k * u * 2.5f, hx + u * 12f, hy - u * 2f + k * u * 3f) }, steel.a(.5f), style = Stroke(max(1f, u * .15f)))
    }
    disc(hx - u * 3f, hy - u * 2.5f, u * 1.1f, c("#1A1410")); disc(hx - u * 3.3f, hy - u * 2.8f, u * .35f, c("#FFFFFF", .7f * (1f - night)))
    // Trompe articulée (anneaux), qui se relève et souffle de l'eau de temps en temps
    val lift = (sin(t * TAU) * .5f + .5f)
    var px = hx - u * 5f; var py = hy + u * 4f; var ang = 1.75f - lift * 1.5f; var w = u * 4.2f
    for (k in 0 until 11) {
        val nx = px + cos(ang) * u * 2.3f; val ny = py + sin(ang) * u * 2.3f
        line(px, py, nx, ny, w, if (k % 2 == 0) wood else woodD, StrokeCap.Round); line(px, py, nx, ny, max(1f, u * .2f), steel.a(.5f))
        px = nx; py = ny; ang += (.12f + lift * .16f) * if (lift > .5f) 1f else -.2f; w *= .9f
    }
    if (lift > .8f) { val a = (lift - .8f) / .2f; for (k in 0 until 14) { val f = k / 14f; disc(px - f * u * 6f + sin(k * 2f) * u, py - f * u * 10f, u * (.4f + f * 1.6f), c("#EAF4FF", .35f * a * (1f - f))) } }
    if (night > .3f) { glow(cx, deck - u * 6f, u * 16f, c("#FFC46A", .25f * night), BlendMode.Screen); for (k in 0 until 4) disc(cx - u * 2f + k * u * 3.6f, deck - u * 6.9f, u * .45f, c("#FFE2A0", night)) }
}
