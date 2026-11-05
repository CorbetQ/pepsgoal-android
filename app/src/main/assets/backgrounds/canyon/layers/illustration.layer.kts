private fun DrawScope.canyon(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val sx = lighting?.let { size.width * (.12f + .76f * it.sun) } ?: W * .22f; val sy = H * .12f
    if (lighting == null) {
    canyonSky(0f to c("#3E8AD8"), .5f to c("#9CCBF0"), 1f to c("#FFE1B8"), y1 = H * .45f)
    glow(sx, sy, S * .8f, c("#FFF2D0", .6f), BlendMode.Screen); disc(sx, sy, S * .05f, c("#FFFBEE"))
    softCloud(W * .7f + sin(t * TAU) * u * 4f, H * .15f, W * .6f, c("#FFFFFF", .55f), c("#FFFFFF", .5f), 4)
    } else canyonLiveSky(lighting, t, H * .45f)
    // Buttes lointaines dans la brume chaude
    for ((bx, bw, bh) in listOf(Triple(.12f, .14f, .05f), Triple(.45f, .1f, .07f), Triple(.8f, .18f, .045f))) {
        val by = H * .45f; fill(poly(W * (bx - bw / 2), by, W * (bx - bw * .38f), by - H * bh, W * (bx + bw * .38f), by - H * bh, W * (bx + bw / 2), by), c("#D9A79A")) }
    rect(0f, H * .45f, W, H * .55f, c("#E2A884"))
    drawRect(vg(H * .45f, H * .62f, 0f to c("#F2C6A0"), 1f to c("#D98A62")), topLeft = Offset(0f, H * .45f), size = Size(W, H * .17f))
    // Parois en gradins, de part et d'autre de la rivière ; strates horizontales
    fun wall(pts: FloatArray, lit: Boolean) {
        val p = poly(*pts)
        fill(p, vg(H * .3f, H, 0f to c("#D9744A"), .5f to c("#B8552E"), 1f to c("#7A3418")))
        clipPath(p) {
            for (b in 0 until 60) { val y = H * .3f + b * u * 1.9f; rect(0f, y, W, u * (.5f + (b % 4) * .35f), if (b % 3 == 0) c("#FFD9B0", .16f) else c("#5A2410", .14f)) }
            // Faces verticales des gradins : à l'ombre (gauche) ou au soleil (droite)
            var k = 0
            while (k < pts.size - 2) { val x0 = pts[k]; val y0 = pts[k + 1]; val x1 = pts[k + 2]; val y1 = pts[k + 3]
                if (y1 - y0 > u * 3f) { val fp = poly(x0, y0, x1, y1, x1, H, x0, H); if (lit) fill(fp, lg(x0, y0, x0, y1, 0f to c("#FFD2A0", .35f), 1f to c("#FFD2A0", .05f))) else fill(fp, c("#4A1A0A", .28f)) }
                k += 2 }
        }
    }
    wall(floatArrayOf(0f, H * .36f, W * .2f, H * .36f, W * .22f, H * .46f, W * .3f, H * .47f, W * .33f, H * .58f, W * .41f, H * .6f, W * .43f, H * .74f, W * .47f, H * .76f, W * .44f, H, 0f, H), false)
    wall(floatArrayOf(W, H * .33f, W * .84f, H * .33f, W * .81f, H * .44f, W * .7f, H * .46f, W * .67f, H * .57f, W * .59f, H * .6f, W * .57f, H * .73f, W * .53f, H * .76f, W * .58f, H, W, H), true)
    mist(H * .6f, H * .05f, c("#FFE1C0", .25f))
    // Rivière turquoise qui serpente au fond
    val rp = Path().apply { moveTo(W * .47f, H * .76f); cubicTo(W * .4f, H * .84f, W * .58f, H * .9f, W * .44f, H); lineTo(W * .6f, H); cubicTo(W * .66f, H * .9f, W * .48f, H * .84f, W * .53f, H * .76f); close() }
    fill(rp, vg(H * .76f, H, 0f to c("#9FEDE4"), 1f to c("#2EA6B8")))
    for (i in 0 until 14) { val y = H * (.79f + i * .015f); val x = W * (.51f + sin(i * 1.3f + t * TAU) * .03f); line(x - u * 1.5f, y, x + u * 1.5f, y, max(1f, u * .3f), c("#FFFFFF", .4f)) }
    // Aigle qui plane
    run { val a = t * TAU; val x = W * .5f + cos(a) * W * .25f; val y = H * .28f + sin(a) * H * .04f; val s = u * 3f
        drawPath(Path().apply { moveTo(x - s * 1.6f, y - s * .2f); quadraticTo(x - s * .6f, y - s * .5f, x, y); quadraticTo(x + s * .6f, y - s * .5f, x + s * 1.6f, y - s * .2f) }, c("#3A2A2A", .8f), style = Stroke(max(1f, u * .45f))) }
}
