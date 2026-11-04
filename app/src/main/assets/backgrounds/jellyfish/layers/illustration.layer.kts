private fun DrawScope.jellyfish(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    jellyfishSky(0f to c("#0E2A5A"), .5f to c("#071838"), 1f to c("#020818"))
    rays(W * .5f, -H * .05f, H * .8f, 7, 90f, 50f, c("#6FC3FF", .08f), 12, t)
    val r = Mul(14)
    for (i in 0 until 90) { val x = r() * W; val y = ((r() - t * (.05f + r() * .1f)) % 1f + 1f) % 1f * H; disc(x, y, u * (.1f + r() * .25f), c("#BFE6FF", .3f + r() * .3f)) }
    val cols = listOf(c("#FF7AD9"), c("#7FE7FF"), c("#B48CFF"), c("#FFB86B"), c("#7CFFB2"))
    val jel = listOf(floatArrayOf(.3f, .3f, 12f), floatArrayOf(.72f, .2f, 8f), floatArrayOf(.6f, .55f, 15f), floatArrayOf(.18f, .7f, 7f), floatArrayOf(.85f, .75f, 9f), floatArrayOf(.45f, .85f, 5f))
    jel.forEachIndexed { i, q ->
        val pulse = sin(t * TAU * 4 + i * 1.7f); val col = cols[i % cols.size]; val rr = u * q[2]
        val x = W * q[0] + sin(t * TAU + i) * u * 3f; val y = ((q[1] - t * .25f * (1f + i * .1f)) % 1.1f + 1.1f) % 1.1f * H * 1.05f
        glow(x, y, rr * 3f, col.a(.25f), BlendMode.Screen)
        // Tentacules ondulantes
        for (k in 0 until 7) { val tx = x + (k - 3) * rr * .22f; val p = Path(); p.moveTo(tx, y + rr * .2f)
            for (j in 1..10) { val f = j / 10f; p.lineTo(tx + sin(t * TAU * 3 + k + f * 5f) * rr * .25f * f, y + rr * .2f + rr * 2.6f * f) }
            drawPath(p, col.a(.45f), style = Stroke(max(1f, rr * .05f))) }
        val w = rr * (1f + .08f * pulse); val h = rr * (.75f - .08f * pulse)
        val bell = Path().apply { moveTo(x - w, y + h * .3f); cubicTo(x - w, y - h * 1.2f, x + w, y - h * 1.2f, x + w, y + h * .3f); quadraticTo(x, y + h * .05f, x - w, y + h * .3f); close() }
        drawPath(bell, Brush.radialGradient(0f to Color.White.copy(alpha = .8f), .4f to col.a(.6f), 1f to col.a(.15f), center = Offset(x - w * .2f, y - h * .5f), radius = w * 1.4f))
        drawPath(bell, col.a(.8f), style = Stroke(max(1f, rr * .05f)))
    }
}
