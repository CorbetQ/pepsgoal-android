private fun DrawScope.cavier(x: Float, base: Float, s: Float, rot: Float) {
    val u = s
    fill(poly(x - u * 5f, base, x - u * 2.8f, base - u * 10f, x + u * 2.8f, base - u * 10f, x + u * 5f, base), lg(x - u * 5f, 0f, x + u * 5f, 0f, 0f to c("#FBF4E6"), .6f to c("#E6D8BE"), 1f to c("#C2B091")))
    for (k in 1..3) line(x - u * (5f - k * .55f), base - u * k * 2.5f, x + u * (5f - k * .55f), base - u * k * 2.5f, max(1f, u * .12f), c("#B8A888", .6f))
    fill(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(x - u * .9f, base - u * 3.4f, x + u * .9f, base, CornerRadius(u * .9f, u * .9f))) }, c("#5A4A3A"))
    drawRect(vg(base - u * 15f, base - u * 10f, 0f to c("#8A6A4A"), 1f to c("#5A4230")), Offset(x - u * 3.4f, base - u * 15f), Size(u * 6.8f, u * 5f))
    fill(poly(x - u * 3.9f, base - u * 15f, x, base - u * 18.5f, x + u * 3.9f, base - u * 15f), c("#5E6E86")); fill(poly(x - u * 3.9f, base - u * 15f, x, base - u * 18.5f, x, base - u * 15f), c("#8A9AB4"))
    rect(x - u * .7f, base - u * 13.6f, u * 1.4f, u * 1.6f, c("#FFE2A0"))
    // Ailes : quatre bras à claire-voie
    val hx = x + u * 3.6f; val hy = base - u * 13f
    for (k in 0 until 4) {
        val a = rot + k * 1.5708f; val ca = cos(a); val sa = sin(a); val nx = -sa; val ny = ca; val L = u * 11f
        val p0 = Offset(hx + ca * u * 1.2f, hy + sa * u * 1.2f)
        val q = listOf(p0, Offset(hx + ca * L, hy + sa * L), Offset(hx + ca * L + nx * u * 2.4f, hy + sa * L + ny * u * 2.4f), Offset(p0.x + nx * u * 2.4f, p0.y + ny * u * 2.4f))
        fill(Path().apply { moveTo(q[0].x, q[0].y); q.drop(1).forEach { lineTo(it.x, it.y) }; close() }, c("#F4EBDD", .85f))
        for (m in 1..5) { val f = m / 6f; line(p0.x + ca * L * f, p0.y + sa * L * f, p0.x + ca * L * f + nx * u * 2.4f, p0.y + sa * L * f + ny * u * 2.4f, max(1f, u * .14f), c("#6A5236")) }
        line(hx, hy, hx + ca * L, hy + sa * L, max(1f, u * .35f), c("#5A4632"))
    }
    disc(hx, hy, u * .7f, c("#3A2E22"))
}

private fun DrawScope.manoir(x: Float, base: Float, u: Float, lit: Float) {
    val tuf = c("#F5EDDD"); val tufSh = c("#D9CAB0"); val slate = c("#56657E"); val slateLit = c("#8394AE"); val win = mix(c("#5A6A7E"), c("#FFD27A"), lit)
    val w = u * 24f; val h = u * 9f; val roof = u * 9f
    // Tour ronde à droite
    val tx = x + w * .5f + u * 2f; rect(tx - u * 2.6f, base - h - u * 4f, u * 5.2f, h + u * 4f, tuf); rect(tx + u * .6f, base - h - u * 4f, u * 2f, h + u * 4f, tufSh)
    fill(poly(tx - u * 3f, base - h - u * 4f, tx, base - h - u * 15f, tx + u * 3f, base - h - u * 4f), slate); fill(poly(tx - u * 3f, base - h - u * 4f, tx, base - h - u * 15f, tx, base - h - u * 4f), slateLit)
    rect(tx - u * .5f, base - h + u * 1f, u * 1f, u * 1.6f, win)
    // Logis
    rect(x - w / 2, base - h, w, h, tuf); rect(x + w * .3f, base - h, w * .2f, h, tufSh)
    fill(poly(x - w / 2 - u * .5f, base - h, x - w / 2 + u * 4f, base - h - roof, x + w / 2 - u * 4f, base - h - roof, x + w / 2 + u * .5f, base - h), slate)
    fill(poly(x - w / 2 - u * .5f, base - h, x - w / 2 + u * 4f, base - h - roof, x - u * 2f, base - h - roof, x - u * 4f, base - h), slateLit)
    for (cxk in listOf(-.36f, .32f)) { val cx = x + w * cxk; rect(cx - u * .9f, base - h - roof - u * 3.5f, u * 1.8f, u * 5f, tufSh); rect(cx - u * 1.1f, base - h - roof - u * 3.8f, u * 2.2f, u * .6f, tuf) }
    for (lx in listOf(-.18f, .12f)) { val dx = x + w * lx; rect(dx - u * 1.6f, base - h - u * 4.6f, u * 3.2f, u * 4.8f, tuf)
        fill(poly(dx - u * 2f, base - h - u * 4.6f, dx, base - h - u * 7.6f, dx + u * 2f, base - h - u * 4.6f), tuf); disc(dx, base - h - u * 6.8f, u * .35f, tufSh)
        rect(dx - u * .8f, base - h - u * 3.8f, u * 1.6f, u * 2.6f, win) }
    for (k in 0 until 5) { val wx = x - w / 2 + u * 2f + k * (w - u * 5f) / 4f; rect(wx, base - h + u * 2f, u * 1.6f, u * 2.6f, win); rect(wx - u * .2f, base - h + u * 1.8f, u * 2f, u * .3f, tufSh)
        if (k != 2) rect(wx, base - h + u * 5.6f, u * 1.6f, u * 2.4f, win) }
    rect(x - u * 1f, base - h + u * 5f, u * 2f, u * 4f, c("#6A4A32"))
    if (lit > .01f) glow(x, base - h * .5f, u * 18f, c("#FFB04A", .2f * lit), BlendMode.Screen)
}
