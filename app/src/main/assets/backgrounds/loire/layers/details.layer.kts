private fun DrawScope.loireTown(y: Float, a: Float, lightsOnly: Boolean = false) {
    val W = size.width; val S = min(W, size.height); val u = S / 100f
    val tuf = c("#F4EBDD", a); val tufSh = c("#D9C9B0", a); val slate = c("#5E6E86", a); val slateLit = c("#8A9AB4", a); val win = c("#6A5A48", a)
    // Maisons : murs de tuffeau, toits d'ardoise à deux pans, quelques lucarnes
    val r = Mul(31)
    var x = W * .02f
    while (x < W * .98f) {
        val w = u * (5f + r() * 5f); val h = u * (3.2f + r() * 2.5f); val rh = u * (2.4f + r() * 1.6f)
        if (x > W * .52f && x < W * .86f) { x += w; continue }
        if (!lightsOnly) {
            rect(x, y - h, w, h, tuf); rect(x + w * .62f, y - h, w * .38f, h, tufSh)
            fill(poly(x - u * .3f, y - h, x + w * .5f, y - h - rh, x + w + u * .3f, y - h), slate)
            fill(poly(x - u * .3f, y - h, x + w * .5f, y - h - rh, x + w * .5f, y - h), slateLit)
        }
        // La nuit : fenêtres allumées (une sur deux environ), avec leur halo
        for (k in 0 until (w / (u * 2.4f)).toInt()) { val wx = x + u * (1f + k * 2.4f); val wy = y - h * .65f
            if (!lightsOnly) rect(wx, wy, u * .8f, u * 1.1f, win) else if ((k + (x / u).toInt()) % 2 == 0) { rect(wx, wy, u * .8f, u * 1.1f, c("#FFD27A", a)); glow(wx + u * .4f, wy + u * .5f, u * 2.5f, c("#FFB04A", .35f * a), BlendMode.Screen) } }
        if (r() < .4f && !lightsOnly) rect(x + w * .7f, y - h - rh * .7f, u * .6f, rh * .5f, tufSh)
        x += w + u * (.2f + r() * 1.2f)
    }
    if (lightsOnly) {
        // Château illuminé (projecteurs sur le tuffeau)
        val cx = W * .69f; glow(cx, y - u * 12f, u * 22f, c("#FFD9A0", .3f * a), BlendMode.Screen)
        for (k in 0 until 6) rect(cx - u * 26f * .38f + k * u * 26f * .15f, y - u * 4f - u * 13f * .62f, u * 1.1f, u * 2f, c("#FFD27A", a))
        return
    }
    // Église et son clocher d'ardoise
    val ex = W * .3f; rect(ex - u * 3f, y - u * 9f, u * 6f, u * 9f, tuf); fill(poly(ex - u * 3.6f, y - u * 9f, ex, y - u * 22f, ex + u * 3.6f, y - u * 9f), slate)
    fill(poly(ex - u * 3.6f, y - u * 9f, ex, y - u * 22f, ex, y - u * 9f), slateLit); rect(ex - u * .9f, y - u * 7f, u * 1.8f, u * 2.6f, win)
    // Château de Saumur : logis blanc, quatre tours à toits en poivrière, lanternons
    val cx = W * .69f; val cw = u * 26f; val ch = u * 13f; val by = y - u * 4f
    rect(cx - cw * .62f, by, cw * 1.24f, u * 4f, tufSh) // terrasse
    rect(cx - cw / 2, by - ch, cw, ch, tuf); rect(cx + cw * .15f, by - ch, cw * .35f, ch, tufSh)
    fill(poly(cx - cw / 2, by - ch, cx - cw * .38f, by - ch - u * 6f, cx + cw * .38f, by - ch - u * 6f, cx + cw / 2, by - ch), slate)
    for (k in 0 until 6) rect(cx - cw * .38f + k * cw * .15f, by - ch * .62f, u * 1.1f, u * 2f, win)
    for ((tx, th) in listOf(-.5f to 1.3f, -.17f to 1.5f, .17f to 1.45f, .5f to 1.25f)) {
        val px = cx + cw * tx; val tw = u * 4.2f; val top = by - ch * th
        rect(px - tw / 2, top, tw, by - top, tuf); rect(px + tw * .1f, top, tw * .4f, by - top, tufSh)
        rect(px - tw * .62f, top - u * .9f, tw * 1.24f, u * 1.2f, tufSh) // mâchicoulis
        fill(poly(px - tw * .62f, top - u * .9f, px, top - u * 11f, px + tw * .62f, top - u * .9f), slate)
        fill(poly(px - tw * .62f, top - u * .9f, px, top - u * 11f, px, top - u * .9f), slateLit)
        line(px, top - u * 11f, px, top - u * 13.5f, max(1f, u * .35f), c("#C9A45E", a)); disc(px, top - u * 13.6f, u * .45f, c("#E8C46A", a))
        rect(px - u * .5f, top + u * 2.5f, u * 1f, u * 1.6f, win)
    }
}
