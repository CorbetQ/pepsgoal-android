private fun DrawScope.dunes(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H)
    val sun = Offset(W * .32f, H * .56f)
    if (lighting == null) {
    dunesSky(0f to c("#6E4A8C"), .25f to c("#C7678A"), .45f to c("#F28C5B"), .56f to c("#FFB07A"), .62f to c("#FFD9A0"), y1 = H * .64f)
    glow(sun.x, sun.y, S * 1.3f, c("#FFB46A", .55f), BlendMode.Screen); glow(sun.x, sun.y, S * .3f, c("#FFE7B8", .8f), BlendMode.Screen)
    disc(sun.x, sun.y, S * .07f, c("#FFF3D6"))
    softCloud(W * .7f + t * W * .05f, H * .3f, W * .8f, c("#B8608A", .3f), c("#FFC09A", .4f), 4)
    } else dunesLiveSky(lighting, t, H * .64f)
    // Dunes : versant au soleil clair, versant à l'ombre plus froid, arête nette
    val specs = listOf(arrayOf<Any>(.6f, .06f, "#F2B07E", "#C9785E"), arrayOf<Any>(.66f, .08f, "#EE9E64", "#B65E46"), arrayOf<Any>(.74f, .1f, "#E58A4E", "#9C4A36"), arrayOf<Any>(.88f, .17f, "#D9773C", "#7E3A2C"))
    for ((k, sp) in specs.withIndex()) {
        val base = H * (sp[0] as Float); val amp = H * (sp[1] as Float); val lit = c(sp[2] as String); val sh = c(sp[3] as String); val nz = Nz(20 + k)
        val n = 120; val pts = FloatArray((n + 1) * 2)
        for (i in 0..n) { val u = i / n.toFloat(); pts[i * 2] = W * u; val v = nz.at(u * (1.6f + k * .4f), 3) * .5f + .5f; pts[i * 2 + 1] = base - amp * v.pow(1.6f) }
        ridgeFill(pts, vg(base - amp, H, 0f to lit, 1f to mix(lit, sh, .3f)))
        // versants à l'ombre (pentes qui descendent vers la droite, dos au soleil) et rides de sable
        if (k < 3) run { // bande d'ombre : entre la crête f(x) et la crête décalée f(x − dx) + dy, là où elle est plus basse
            val dx = amp * 1.3f; val dy = amp * .2f
            fun f(x: Float): Float { val u = (x / W * n).coerceIn(0f, n.toFloat()); val i = u.toInt().coerceAtMost(n - 1); val fr = u - i; return pts[i * 2 + 1] * (1 - fr) + pts[i * 2 + 3] * fr }
            val band = Path(); for (i in 0..n) { val x = pts[i * 2]; if (i == 0) band.moveTo(x, f(x)) else band.lineTo(x, f(x)) }
            for (i in n downTo 0) { val x = pts[i * 2]; band.lineTo(x, if (x < dx) f(x) else max(f(x), f(x - dx) + dy)) }
            band.close(); drawPath(band, vg(base - amp, base + amp * .5f, 0f to sh, 1f to mix(sh, lit, .35f)))
        }
        if (k >= 2) clipPath(Path().apply { moveTo(0f, H); var i = 0; while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }; lineTo(W, H); close() }) {
            val r = Mul(30 + k); for (j in 0 until 14) { val y = base + amp * (.1f + j * .12f); val q = Path(); q.moveTo(0f, y); for (i in 1..20) { val x = W * i / 20f; q.lineTo(x, y + sin(x / W * 9f + j + r()) * amp * .08f) }
                drawPath(q, sh.a(.22f), style = Stroke(max(1f, S * .002f))) }
        }
        val crest = Path(); crest.moveTo(pts[0], pts[1]); var i = 2; while (i < pts.size) { crest.lineTo(pts[i], pts[i + 1]); i += 2 }
        drawPath(crest, c("#FFE6C0", .35f), style = Stroke(max(1f, S * .003f)))
        if (k == 1) { // caravane de chameaux sur la crête
            val r = Mul(3); val cx0 = W * (.55f + .08f * sin(t * TAU))
            for (j in 0 until 4) { val x = cx0 + j * S * .035f; val idx = ((x / W) * n).toInt().coerceIn(0, n); val y = pts[idx * 2 + 1]; val s = S * .016f
                fill(Path().apply { moveTo(x - s, y); lineTo(x - s * .8f, y - s * .9f); cubicTo(x - s * .4f, y - s * 1.6f, x + s * .2f, y - s * 1.4f, x + s * .5f, y - s); lineTo(x + s * .9f, y - s * 1.5f); lineTo(x + s * 1.1f, y - s * 1.4f); lineTo(x + s * .8f, y - s * .7f); lineTo(x + s * .7f, y); close() }, c("#4A2430", .8f)); r() }
        }
    }
}
