private fun DrawScope.snowpeak(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H)
    if (lighting == null) {
    snowpeakSky(0f to c("#7FA8DE"), .3f to c("#A9C6EE"), .55f to c("#DDEBFF"), .7f to c("#F4F8FF"))
    glow(W * .8f, H * .12f, S * .9f, c("#FFFFFF", .55f), BlendMode.Screen)
    softCloud(W * .3f + t * W * .05f, H * .2f, W * .8f, c("#FFFFFF", .5f), c("#FFFFFF", .5f), 4)
    } else snowpeakLiveSky(lighting, t, H * .6f)
    // Grand sommet : faces au soleil blanches, faces à l'ombre bleutées, arêtes nettes
    val peak = listOf(-.1f to .62f, .12f to .5f, .25f to .42f, .38f to .28f, .5f to .17f, .58f to .25f, .66f to .31f, .78f to .37f, .9f to .45f, 1.1f to .55f)
    val p = Path(); p.moveTo(-W * .1f, H); for ((u, v) in peak) p.lineTo(W * u, H * v); p.lineTo(W * 1.1f, H); p.close()
    drawPath(p, vg(H * .17f, H * .7f, 0f to c("#FFFFFF"), 1f to c("#DCE8F8")))
    // ombres (versant droit de chaque arête + couloirs)
    val sh = c("#9DB7E0")
    fill(poly(W * .5f, H * .17f, W * .58f, H * .25f, W * .62f, H * .4f, W * .55f, H * .55f, W * .5f, H * .7f, W * .47f, H * .45f, W * .49f, H * .3f), vg(H * .17f, H * .7f, 0f to sh, 1f to sh.a(.4f)))
    fill(poly(W * .66f, H * .31f, W * .78f, H * .37f, W * .8f, H * .5f, W * .72f, H * .62f, W * .7f, H * .45f), sh.a(.7f))
    fill(poly(W * .38f, H * .28f, W * .43f, H * .38f, W * .4f, H * .55f, W * .34f, H * .6f, W * .36f, H * .42f), sh.a(.5f))
    fill(poly(W * .12f, H * .5f, W * .2f, H * .52f, W * .18f, H * .64f, W * .1f, H * .66f), sh.a(.5f))
    mist(H * .62f, H * .08f, c("#FFFFFF", .7f))
    // Collines et sapins chargés de neige
    for (k in 0 until 2) {
        val nz = Nz(12 + k); val base = H * (.72f + k * .1f)
        val ln = { x: Float -> base - (nz.at(x / W * 2.5f, 3) * .5f + .5f) * H * .04f }
        conifers(16 - k * 5, 30 + k, H * (.06f + k * .05f), H * (.1f + k * .08f), mix(c("#4E6E8E"), c("#21405A"), k.toFloat()), ln)
        ridgePts(base, H * .04f, 60) { u -> nz.at(u * 2.5f, 3) * .5f + .5f }.let { ridgeFill(it, vg(base - H * .04f, H, 0f to c("#FFFFFF"), 1f to c("#C9DAF2"))) }
    }
    // Paillettes et flocons
    val sp = Mul(3); for (i in 0 until 50) { val x = sp() * W; val y = H * (.75f + sp() * .25f); disc(x, y, S * .002f, c("#FFFFFF", .5f + .5f * sin(t * TAU * 4 + i * 2.1f))) }
    val r = Mul(5); val pts = ArrayList<Offset>()
    for (i in 0 until 90) { val u = (r() + t * (.4f + r() * .4f)) % 1f; pts.add(Offset(r() * W + sin(u * TAU * 2 + i) * S * .02f, u * H)) }
    drawPoints(pts, PointMode.Points, c("#FFFFFF", .85f), strokeWidth = S * .006f, cap = StrokeCap.Round)
}
