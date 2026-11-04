private fun DrawScope.lanterns(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val hz = H * .7f
    lanternsSky(0f to c("#0A0A24"), .6f to c("#241A4A"), 1f to c("#4A2A5A"), y1 = hz)
    starField(27, 160, hz, c("#FFFFFF", .7f), t, 3)
    val nz = Nz(17)
    ridgeFill(ridgePts(hz, H * .06f, 60) { x -> .5f + .5f * nz.at(x * 3f, 3) }, c("#1A1430"), hz + 2f)
    conifers(40, 5, u * 3f, u * 7f, c("#100C22"), { x -> hz - H * .01f })
    drawRect(vg(hz, H, 0f to c("#2A1E48"), 1f to c("#0A0818")), topLeft = Offset(0f, hz), size = Size(W, H - hz))
    // Lanternes qui montent, avec leur reflet dans le lac
    val r = Mul(23)
    for (i in 0 until 26) {
        val x0 = r() * W; val sp = .25f + r() * .3f; val z = .35f + r() * .65f
        val k = ((r() + t * sp) % 1f); val y = hz - (hz + u * 10f) * k
        val x = x0 + sin(t * TAU * 2 + i) * u * 2f; val w = u * 3.4f * z; val h = w * 1.25f; val fl = .85f + .15f * sin(t * TAU * 9 + i * 2f)
        glow(x, y, w * 4f, c("#FFB04A", .35f * z * fl), BlendMode.Screen)
        fill(poly(x - w / 2, y - h / 2, x + w / 2, y - h / 2, x + w * .38f, y + h / 2, x - w * .38f, y + h / 2), vg(y - h / 2, y + h / 2, 0f to c("#FFD27A", z), 1f to c("#FF7A2A", z)))
        disc(x, y + h * .4f, w * .14f, c("#FFF6D0", z * fl))
        // Reflet
        val ry = hz + (hz - y) * .35f
        if (ry < H) { val rw = w * .7f; for (j in 0 until 3) line(x - rw, ry + j * u * .8f, x + rw, ry + j * u * .8f, max(1f, u * .3f * z), c("#FFB04A", (.35f - j * .1f) * z)) }
    }
    // Barques au premier plan avec leurs lanternes
    for ((bx, by) in listOf(.25f to .85f, .7f to .92f)) { val x = W * bx; val y = H * by; val L = u * 18f
        fill(Path().apply { moveTo(x - L / 2, y); quadraticTo(x, y + u * 4f, x + L / 2, y); lineTo(x + L * .4f, y + u * 1.5f); quadraticTo(x, y + u * 4.5f, x - L * .4f, y + u * 1.5f); close() }, c("#0C0816"))
        disc(x - L * .1f, y - u * 2.5f, u * 1.4f, c("#2A1A30")); rect(x - L * .14f, y - u * 1.5f, u * 2.6f, u * 1.6f, c("#2A1A30"))
        glow(x + L * .2f, y - u * 2f, u * 6f, c("#FFB04A", .5f), BlendMode.Screen); disc(x + L * .2f, y - u * 2f, u * .9f, c("#FFE09A")) }
}
