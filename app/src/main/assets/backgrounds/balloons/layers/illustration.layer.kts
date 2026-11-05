private fun DrawScope.balloons(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val sx = lighting?.let { size.width * (.12f + .76f * it.sun) } ?: W * .5f; val sy = H * .72f
    if (lighting == null) {
    balloonsSky(0f to c("#7FB2E6"), .45f to c("#CFE2F2"), .75f to c("#FFE2C8"), 1f to c("#FFD2B0"))
    glow(sx, sy, S * 1.2f, c("#FFD8A8", .5f), BlendMode.Screen); glow(sx, sy, S * .2f, c("#FFF4E0", .9f), BlendMode.Screen)
    } else balloonsLiveSky(lighting, t, H * .74f)
    val nz = Nz(52)
    ridgeFill(ridgePts(H * .74f, H * .06f, 60) { x -> .5f + .5f * nz.at(x * 3f, 3) }, c("#B9A6C6", .9f))
    mist(H * .76f, H * .06f, c("#FFF1E6", .7f))
    ridgeFill(ridgePts(H * .82f, H * .05f, 60) { x -> .5f + .5f * nz.at(x * 4f + 7f, 3) }, c("#8C86A8"))
    mist(H * .84f, H * .05f, c("#FFF1E6", .6f))
    ridgeFill(ridgePts(H * .9f, H * .04f, 60) { x -> .5f + .5f * nz.at(x * 5f + 2f, 3) }, vg(H * .86f, H, 0f to c("#6E7A6A"), 1f to c("#3E4A3E")))
    conifers(30, 4, u * 4f, u * 9f, c("#2E3A30"), { x -> H * .93f + sin(x / W * 9f) * u * 2f })
    val pal = listOf(listOf(c("#FF5A5F"), c("#FFD166")), listOf(c("#3DB2FF"), c("#FFFFFF")), listOf(c("#7B5CFF"), c("#FF7AD9")), listOf(c("#2EC27E"), c("#F6F5F4")), listOf(c("#FF8A3D"), c("#7A3DFF"), c("#FFE45C")))
    val spots = listOf(floatArrayOf(.25f, .32f, 10f), floatArrayOf(.7f, .22f, 7f), floatArrayOf(.52f, .48f, 13f), floatArrayOf(.85f, .5f, 5f), floatArrayOf(.12f, .6f, 4.5f), floatArrayOf(.4f, .14f, 4f))
    spots.forEachIndexed { i, q -> val bob = sin(t * TAU + i * 1.3f) * u * 1.5f; val drift = ((t * .5f + i * .2f) % 1f - .5f) * u * 6f
        balloon(W * q[0] + drift, H * q[1] + bob, u * q[2], pal[i % pal.size]) }
    // Oiseaux
    for (i in 0 until 3) { val k = (t * 2f + i * .3f) % 1f; val x = W * (k * 1.2f - .1f); val y = H * (.1f + i * .05f); val s = u * 1.2f
        drawPath(Path().apply { moveTo(x - s, y - s * .4f); quadraticTo(x - s * .4f, y - s * .2f, x, y + s * .2f); quadraticTo(x + s * .4f, y - s * .2f, x + s, y - s * .4f) }, c("#4A4A5A", .7f), style = Stroke(max(1f, u * .3f))) }
}
