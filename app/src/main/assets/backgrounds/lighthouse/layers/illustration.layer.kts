private fun DrawScope.lighthouse(t: Float, d: Tod? = null) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val hz = H * .58f
    // Nuit par défaut ; à l'heure réelle, ciel et mer de jour, d'aube ou de crépuscule, et phare allumé seulement le soir et la nuit
    val night = d?.night ?: 1f; val lamp = if (d == null) 1f else (d.night + .7f * d.dusk + .4f * d.dawn).coerceIn(0f, 1f)
    val mx = if (d == null) W * .22f else W * .78f
    lighthouseLiveSky(d ?: Tod(1f, 0f, 0f, 0f, .55f), t, hz)
    // Mer : houle, reflets de lune (ou de soleil)
    if (d == null) drawRect(vg(hz, H, 0f to c("#1E3358"), 1f to c("#050A18")), topLeft = Offset(0f, hz), size = Size(W, H - hz))
    else drawRect(vg(hz, H, 0f to d.col("#5E9CD0", "#8A8AC0", "#8A5A8A", "#1E3358"), 1f to d.col("#1E4A80", "#2A3A6A", "#2A2050", "#050A18")), topLeft = Offset(0f, hz), size = Size(W, H - hz))
    val r = Mul(3)
    for (i in 0 until 120) { val v = r(); val y = hz + (H - hz) * v * v; val x = ((r() + t * (.05f + v * .1f)) % 1f) * W; val len = u * (1f + v * 8f)
        line(x, y, x + len, y, max(1f, u * (.15f + v * .3f)), c("#9FB7E0", .12f + v * .18f)) }
    for (i in 0 until 50) { val v = r(); val y = hz + (H - hz) * (.02f + v * .5f); val x = mx + (r() - .5f) * u * (6f + v * 30f); val tw = .5f + .5f * sin(t * TAU * 5 + i)
        line(x - u * 1.5f, y, x + u * 1.5f, y, max(1f, u * .3f), c("#E6EEFF", .5f * tw)) }
    // Falaise et rochers
    // Promontoire rocheux : arête bosselée, face éclairée par la lune, base dans l'écume
    val nzc = Nz(77); val cp = Path(); val cb = hz + H * .1f
    cp.moveTo(W * .4f, cb)
    for (i in 0..40) { val f = i / 40f; val x = W * (.4f + .6f * f); val top = cb + (hz - H * .03f - cb) * sm(0f, .4f, f) + nzc.at(f * 9f, 4) * H * .012f * sm(0f, .2f, f); cp.lineTo(x, top) }
    cp.lineTo(W, cb); cp.close()
    fill(cp, vg(hz - H * .04f, cb, 0f to c("#3A4154"), 1f to c("#141824")))
    clipPath(cp) { for (i in 0 until 9) { val x = W * (.52f + i * .055f); fill(poly(x, hz - H * .03f, x + W * .03f, hz - H * .03f, x - W * .01f, cb, x - W * .04f, cb), c("#5A6680", .18f)) } }
    for (i in 0 until 8) { val y = cb - H * .004f * i; val x0 = W * (.48f + i * .01f) + sin(t * TAU * 2 + i) * u; line(x0, y, x0 + W * (.55f - i * .02f), y, max(1f, u * .4f), c("#DDE8FF", .25f - i * .025f)) }
    // Phare rayé blanc et rouge
    val px = W * .78f; val pb = hz - H * .026f; val ph = H * .2f; val wb = u * 9f; val wt = u * 6f
    for (k in 0 until 5) { val y0 = pb - ph * k / 5f; val y1 = pb - ph * (k + 1) / 5f; val w0 = wb + (wt - wb) * k / 5f; val w1 = wb + (wt - wb) * (k + 1) / 5f
        fill(poly(px - w0 / 2, y0, px + w0 / 2, y0, px + w1 / 2, y1, px - w1 / 2, y1), if (k % 2 == 0) c("#E9EDF4") else c("#C8323A"))
        fill(poly(px + w0 * .1f, y0, px + w0 / 2, y0, px + w1 / 2, y1, px + w1 * .1f, y1), c("#000000", .25f)) }
    val lt = pb - ph; rect(px - wt * .7f, lt - u * 1f, wt * 1.4f, u * 1f, c("#20242E"))
    rect(px - wt * .45f, lt - u * 5f, wt * .9f, u * 4f, mix(c("#8A9AB4"), c("#FFE9A8"), lamp)); fill(poly(px - wt * .6f, lt - u * 5f, px, lt - u * 9f, px + wt * .6f, lt - u * 5f), c("#20242E"))
    // Faisceau tournant
    val ang = t * TAU * 3f; val bx = px; val by = lt - u * 3f; val dir = cos(ang); val len = W * 1.3f * abs(dir) + u * 10f
    val spread = H * .05f
    if (lamp > .01f) {
        fill(poly(bx, by - u, bx + dir * len, by - spread, bx + dir * len, by + spread, bx, by + u), lg(bx, by, bx + dir * len, by, 0f to c("#FFF2C2", .55f * abs(dir) * lamp), 1f to c("#FFF2C2", 0f)))
        glow(bx, by, S * .35f, c("#FFE9A8", .5f * lamp), BlendMode.Screen); glow(bx, by, S * .08f, c("#FFFFFF", .9f * lamp), BlendMode.Screen)
    }
    // Écume au pied de la falaise
}
