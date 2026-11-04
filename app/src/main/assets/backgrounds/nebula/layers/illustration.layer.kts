private fun DrawScope.nebula(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    nebulaSky(0f to c("#04020C"), .35f to c("#0B0628"), .7f to c("#160A36"), 1f to c("#08041A"))
    starField(41, 520, H, c("#E8E4FF", .75f), t)
    val n = Nz(5); val r = Mul(7); val sw = sin(t * TAU) * S * .01f
    fun arm(u: Float) = Offset(W * (.05f + .9f * u + .1f * sin(u * 6f)), H * (.16f + .62f * u + .05f * n.at(u * 3f)))
    val cols = listOf(c("#C14BA8"), c("#6A3FE0"), c("#2BB3C0"), c("#FF8A6B"), c("#E0559A"))
    // Grands voiles colorés, allongés dans l'axe du bras
    for (i in 0 until 90) {
        val u = r(); val o = arm(u); val sz = S * (.08f + r() * .3f) * (1.1f - abs(u - .5f) * .8f)
        val col = cols[(r() * cols.size).toInt().coerceIn(0, cols.size - 1)]
        val px = o.x + (r() - .5f) * S * .35f + sw; val py = o.y + (r() - .5f) * S * .3f
        rotate(50f + (r() - .5f) * 40f, Offset(px, py)) { ellGlow(px, py, sz, sz * (.35f + r() * .4f), col.a(.08f + r() * .1f), BlendMode.Screen) }
    }
    // Cœur lumineux
    val core = arm(.48f)
    glow(core.x, core.y, S * .5f, c("#FF9ACB", .25f), BlendMode.Screen); glow(core.x, core.y, S * .2f, c("#FFE6F2", .45f), BlendMode.Screen)
    // Volutes fines, très étirées
    for (i in 0 until 22) {
        val o = arm(r()); val px = o.x + (r() - .5f) * S * .4f; val py = o.y + (r() - .5f) * S * .3f
        rotate(50f + (r() - .5f) * 50f, Offset(px, py)) { ellGlow(px, py, S * (.12f + r() * .14f), S * (.02f + r() * .025f), cols[i % cols.size].a(.16f), BlendMode.Screen) }
    }
    // Poussière sombre (bandes d'absorption)
    for (i in 0 until 16) {
        val u = .15f + r() * .7f; val o = arm(u); val px = o.x + S * .06f + (r() - .5f) * S * .1f; val py = o.y - S * .02f + (r() - .5f) * S * .08f
        rotate(50f + (r() - .5f) * 30f, Offset(px, py)) { ellGlow(px, py, S * (.06f + r() * .14f), S * (.02f + r() * .04f), c("#07031A", .35f)) }
    }
    // Étoiles jeunes dans le nuage, quelques aigrettes
    starField(43, 160, H, c("#FFE9F7", .9f), t, 7)
    // Étoiles filantes (deux passages par cycle)
    for (k in 0 until 2) {
        val ph = (t * 2f + k * .5f) % 1f
        if (ph < .18f) {
            val e = ph / .18f; val sx = W * (.85f - k * .4f); val sy = H * (.08f + k * .2f)
            val hx = sx - e * W * .5f; val hy = sy + e * W * .25f; val tl = W * .16f
            drawLine(Brush.linearGradient(0f to c("#FFFFFF", 0f), 1f to c("#FFFFFF", .9f * (1f - e)), start = Offset(hx + tl, hy - tl * .5f), end = Offset(hx, hy)), Offset(hx + tl, hy - tl * .5f), Offset(hx, hy), strokeWidth = S * .004f, cap = StrokeCap.Round)
            glow(hx, hy, S * .02f, c("#FFFFFF", .8f * (1f - e)), BlendMode.Screen)
        }
    }
}
