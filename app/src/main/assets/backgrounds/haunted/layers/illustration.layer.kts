private fun DrawScope.haunted(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    hauntedSky(0f to c("#07040F"), .3f to c("#150A2C"), .55f to c("#2E1650"), .72f to c("#4A1F4A"), 1f to c("#1A0B1E"))
    starField(71, 180, H * .5f, c("#E9DCFF", .6f), t)
    val mx = W * .6f; val my = H * .25f; val mr = S * .17f
    glow(mx, my, S * 1.1f, c("#B48CFF", .18f), BlendMode.Screen); glow(mx, my, mr * 1.7f, c("#FFF2D6", .35f), BlendMode.Screen)
    drawCircle(Brush.radialGradient(0f to c("#FFF8E6"), .8f to c("#F2E2C0"), 1f to c("#D9C39C"), center = Offset(mx - mr * .2f, my - mr * .25f), radius = mr * 1.3f), radius = mr, center = Offset(mx, my))
    for ((dx, dy, rr) in listOf(Triple(-.35f, -.1f, .3f), Triple(.2f, .25f, .36f), Triple(.3f, -.4f, .18f), Triple(-.15f, .45f, .16f))) glow(mx + dx * mr, my + dy * mr, rr * mr, c("#BFA47C", .45f))
    // Nuages effilochés devant la lune
    val dr = t * W * .1f
    for (k in 0 until 3) { val x = (W * (.1f + k * .4f) + dr) % (W * 1.4f) - W * .2f; val y = H * (.22f + k * .05f); ellGlow(x, y, W * .35f, H * .015f, c("#2A1640", .7f)); ellGlow(x, y - H * .005f, W * .28f, H * .006f, c("#E6D2FF", .2f), BlendMode.Screen) }
    // Colline et manoir
    val nz = Nz(13); val hill = ridgePts(H * .68f, H * .1f, 80) { u -> sm(.15f, .65f, u) * (1f - sm(.85f, 1.1f, u) * .4f) + .08f * nz.at(u * 4f, 3) }
    ridgeFill(hill, c("#1A0F2A"))
    mist(H * .66f, H * .06f, c("#7B4FA0", .35f))
    val bx = W * .56f; val by = H * .605f; val u = S * .05f; val col = c("#0E0818")
    fun box(x0: Float, y0: Float, x1: Float, y1: Float) = rect(bx + x0 * u, by - y1 * u, (x1 - x0) * u, (y1 - y0) * u, col)
    fun roof(x0: Float, x1: Float, y0: Float, y1: Float) = fill(poly(bx + x0 * u, by - y0 * u, bx + (x0 + x1) / 2 * u, by - y1 * u, bx + x1 * u, by - y0 * u), col)
    box(-3f, 0f, 3.2f, 3.2f); roof(-3.4f, 3.6f, 3.2f, 5.2f)
    box(-4.6f, 0f, -2.6f, 5.4f); roof(-5f, -2.2f, 5.4f, 9f)
    box(2.4f, 0f, 4.2f, 4.4f); roof(2.1f, 4.5f, 4.4f, 7.2f)
    box(-.6f, 4f, .2f, 6.4f) // cheminées
    box(1.4f, 4f, 2f, 5.8f)
    line(bx - 3.6f * u, by - 9f * u, bx - 3.6f * u, by - 10.2f * u, max(1f, S * .003f), col)
    // Fenêtres éclairées (vacillement)
    val r = Mul(5)
    for ((wx, wy) in listOf(-4.1f to 4.2f, -3.4f to 4.2f, -4.1f to 2.6f, -1.8f to 2.1f, -.6f to 2.1f, .8f to 2.1f, 2f to 2.1f, 2.9f to 3.2f, 3.6f to 3.2f, -1.2f to .8f, 1.5f to .8f, -3.7f to 7f)) {
        val on = .7f + .3f * sin(t * TAU * (3f + r() * 4f) + r() * 6f)
        rect(bx + wx * u, by - wy * u, u * .38f, u * .62f, c("#FFB347", on)); glow(bx + wx * u + u * .2f, by - wy * u + u * .3f, u * 1.3f, c("#FF8A2A", .25f * on), BlendMode.Screen)
    }
    // Liseré de lune sur les toits
    line(bx - 5f * u, by - 5.4f * u, bx - 3.6f * u, by - 9f * u, max(1f, S * .003f), c("#E9D9FF", .35f))
    line(bx - 3.4f * u, by - 3.2f * u, bx + .1f * u, by - 5.2f * u, max(1f, S * .003f), c("#E9D9FF", .3f))
    // Cimetière
    val gy = H * .8f
    ridgePts(gy, H * .02f, 50) { u2 -> nz.at(u2 * 5f, 3) * .5f + .5f }.let { ridgeFill(it, vg(gy - H * .02f, H, 0f to c("#1E1030"), 1f to c("#0A0510"))) }
    mist(gy, H * .07f, c("#9A7BC0", .28f + .06f * sin(t * TAU)))
    val g = Mul(11)
    for (i in 0 until 7) {
        val x = W * (.08f + i * .13f + g() * .04f); val y = gy + H * (.01f + g() * .03f); val w = S * (.05f + g() * .03f); val h = w * (1.2f + g() * .5f)
        rotate((g() - .5f) * 14f, Offset(x, y)) {
            if (g() < .3f) { rect(x - w * .12f, y - h * 1.3f, w * .24f, h * 1.3f, c("#2A2238")); rect(x - w * .45f, y - h, w * .9f, w * .22f, c("#2A2238")) }
            else { drawRoundRect(c("#2B2340"), topLeft = Offset(x - w / 2, y - h), size = Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2)); line(x - w / 2, y - h * .6f, x - w / 2 + w * .2f, y - h, max(1f, S * .002f), c("#B9A3E0", .25f)) }
        }
    }
    // Arbre mort au premier plan (silhouette noueuse)
    run {
        val p = Path(); val tips = ArrayList<Offset>()
        branches(p, W * .04f, H * 1.02f, H * .14f, -72f, S * .07f, 6, Mul(23), tips, 22f)
        drawPath(p, c("#050208"))
    }
    // Grille en fer forgé
    val fy = H * .93f
    rect(0f, fy, W, S * .006f, c("#050208")); rect(0f, fy + H * .035f, W, S * .006f, c("#050208"))
    var fx = 0f; while (fx < W) { rect(fx, fy - H * .03f, S * .006f, H * .08f, c("#050208")); fill(poly(fx - S * .006f, fy - H * .03f, fx + S * .003f, fy - H * .045f, fx + S * .012f, fy - H * .03f), c("#050208")); fx += S * .045f }
    // Citrouilles lumineuses
    for ((px, ps) in listOf(W * .78f to S * .1f, W * .64f to S * .07f, W * .9f to S * .06f)) {
        val py = H * .965f
        glow(px, py - ps * .3f, ps * 3f, c("#FF8A1F", .25f + .05f * sin(t * TAU * 5)), BlendMode.Screen)
        for (k in -2..2) drawOval(Brush.radialGradient(0f to c("#FFB347"), 1f to c("#C44E0E"), center = Offset(px + k * ps * .18f - ps * .1f, py - ps * .8f), radius = ps), topLeft = Offset(px + k * ps * .26f - ps * .38f, py - ps * .78f), size = Size(ps * .76f, ps * .78f))
        rect(px - ps * .06f, py - ps * .95f, ps * .12f, ps * .2f, c("#3C4A1E"))
        val fl = c("#FFE27A", .9f + .1f * sin(t * TAU * 7))
        fill(poly(px - ps * .32f, py - ps * .48f, px - ps * .12f, py - ps * .48f, px - ps * .22f, py - ps * .62f), fl)
        fill(poly(px + ps * .12f, py - ps * .48f, px + ps * .32f, py - ps * .48f, px + ps * .22f, py - ps * .62f), fl)
        fill(poly(px - ps * .3f, py - ps * .3f, px + ps * .3f, py - ps * .3f, px + ps * .2f, py - ps * .16f, px, py - ps * .22f, px - ps * .2f, py - ps * .16f), fl)
    }
    // Chauves-souris
    val b = Mul(3)
    for (i in 0 until 7) {
        val sp = .5f + b() * .5f; val u = ((b() + t) % 1f); val x = W * (1.15f - u * 1.3f); val y = H * (.18f + b() * .25f) + sin(u * TAU * 3 + i) * H * .02f
        bat(x, y, S * (.025f + b() * .025f), sin(t * TAU * 40 * sp + i), c("#0A0612"))
    }
}
