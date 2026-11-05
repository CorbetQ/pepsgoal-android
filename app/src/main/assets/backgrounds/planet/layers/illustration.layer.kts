private fun DrawScope.planet(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    planetSky(0f to c("#04020C"), .5f to c("#0E0824"), 1f to c("#1E1040"))
    starField(81, 420, H, c("#E9E4FF", .75f), t, 5)
    ellGlow(W * .2f, H * .3f, W * .7f, H * .2f, c("#5A3FA8", .18f), BlendMode.Screen)
    val px = W * .6f; val py = H * .64f; val pr = S * .44f
    val tilt = -18f
    fun rings(front: Boolean) {
        rotate(tilt, Offset(px, py)) {
            clipRect(px - pr * 2.4f, if (front) py else py - pr * 2f, px + pr * 2.4f, if (front) py + pr * 2f else py) {
                val r = Mul(77); var rr = 1.3f
                while (rr < 2.2f) { val w = .015f + r() * .05f; val a = if (rr in 1.78f..1.84f) 0f else .15f + r() * .45f
                    val col = mix(c("#E8D2B0"), c("#9C7E68"), r())
                    drawOval(col.a(a), topLeft = Offset(px - pr * rr, py - pr * rr * .3f), size = Size(pr * rr * 2, pr * rr * .6f), style = Stroke(pr * w)); rr += w * .95f }
            }
        }
    }
    rings(false)
    // Globe à bandes
    val globe = Path().apply { val n = 64; for (i in 0..n) { val a = i / n.toFloat() * TAU; val x = px + cos(a) * pr; val y = py + sin(a) * pr; if (i == 0) moveTo(x, y) else lineTo(x, y) }; close() }
    glow(px, py, pr * 1.25f, c("#FFB98A", .18f), BlendMode.Screen)
    clipPath(globe) {
        drawRect(vg(py - pr, py + pr, 0f to c("#F2D3A6"), .5f to c("#D99A6C"), 1f to c("#9C5A48")), topLeft = Offset(px - pr, py - pr), size = Size(pr * 2, pr * 2))
        rotate(tilt, Offset(px, py)) {
            val r = Mul(5); val sh = t * pr * .4f
            for (i in 0 until 16) { val y = py - pr + i * pr * .13f + r() * pr * .05f; val h = pr * (.03f + r() * .07f)
                val col = listOf(c("#FFF0D6"), c("#C97A52"), c("#E6B282"), c("#8A4A3E"))[(r() * 4).toInt().coerceIn(0, 3)]
                val p = Path(); p.moveTo(px - pr * 1.3f, y); for (k in 0..24) { val x = px - pr * 1.3f + k * pr * .11f; p.lineTo(x, y + sin(x / pr * 6f + i + sh / pr * 3f) * h * .3f) }
                for (k in 24 downTo 0) { val x = px - pr * 1.3f + k * pr * .11f; p.lineTo(x, y + h + sin(x / pr * 5f + i * 2f + sh / pr * 3f) * h * .3f) }
                p.close(); drawPath(p, col.a(.35f)) }
            drawOval(c("#B5503A", .6f), topLeft = Offset(px + pr * .15f + sh * .3f, py + pr * .25f), size = Size(pr * .22f, pr * .1f)) // grande tache
        }
        // Terminateur et limbe
        drawCircle(Brush.radialGradient(0f to Color.Transparent, .55f to c("#000000", .1f), 1f to c("#05020C", .92f), center = Offset(px - pr * .45f, py - pr * .5f), radius = pr * 2f), radius = pr * 2f, center = Offset(px, py))
        drawCircle(Brush.radialGradient(0f to Color.Transparent, .86f to Color.Transparent, 1f to c("#FFD9B0", .35f), center = Offset(px, py), radius = pr), radius = pr, center = Offset(px, py), blendMode = BlendMode.Screen)
    }
    // Ombre de l'anneau sur le globe
    rings(true)
    // Lunes
    for ((mx, my, mr, col) in listOf(arrayOf(W * .2f, H * .2f, S * .05f, c("#C9C3D9")), arrayOf(W * .82f, H * .14f, S * .025f, c("#E6B89A")))) {
        val x = mx as Float; val y = my as Float; val rr = mr as Float; val cl = col as Color
        drawCircle(Brush.radialGradient(0f to mix(cl, Color.White, .3f), .6f to cl, 1f to mix(cl, Color.Black, .8f), center = Offset(x - rr * .4f, y - rr * .5f), radius = rr * 1.8f), radius = rr, center = Offset(x, y))
    }
    // Étoile filante discrète
    val ph = (t * 1.5f) % 1f
    if (ph < .12f) { val e = ph / .12f; val sx = W * (.9f - e * .5f); val sy = H * (.05f + e * .15f); drawLine(Brush.linearGradient(0f to c("#FFFFFF", 0f), 1f to c("#FFFFFF", .8f * (1f - e)), start = Offset(sx + W * .1f, sy - H * .03f), end = Offset(sx, sy)), Offset(sx + W * .1f, sy - H * .03f), Offset(sx, sy), strokeWidth = S * .003f, cap = StrokeCap.Round) }
}
