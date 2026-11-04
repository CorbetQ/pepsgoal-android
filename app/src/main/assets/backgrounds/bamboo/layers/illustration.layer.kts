private fun DrawScope.bamboo(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    if (lighting == null) {
    bambooSky(0f to c("#E6F4D8"), .5f to c("#BFE0A6"), 1f to c("#7FB86A"))
    glow(W * .65f, H * .12f, S * 1.1f, c("#FFF8D6", .7f), BlendMode.Screen)
    rays(W * .65f, -H * .05f, H * 1.1f, 8, 100f, 40f, c("#FFFBE6", .14f), 22, t)
    } else bambooLiveSky(lighting, t, H * .6f)
    // Receding earth path under the stems; roots and shadows have a ground plane.
    drawRect(vg(H*.55f,H,0f to c("#9AB68A"),1f to c("#3C6452")),Offset(0f,H*.55f),Size(W,H*.45f))
    val path=Path().apply{moveTo(W*.46f,H*.55f);cubicTo(W*.64f,H*.69f,W*.3f,H*.84f,W*.32f,H)
        lineTo(W*.76f,H);cubicTo(W*.49f,H*.82f,W*.72f,H*.67f,W*.49f,H*.55f);close()}
    drawPath(path,vg(H*.55f,H,0f to c("#B7BE94"),1f to c("#928E65")))
    // Trois plans de tiges : lointain pâle, moyen, proche foncé
    val layers = listOf(Triple(16, .35f, c("#A9CF8E")), Triple(9, .65f, c("#6FA65A")), Triple(5, 1f, c("#3E7A3A")))
    layers.forEachIndexed { li, (n, depth, col) ->
        val r = Mul(100 + li)
        for (i in 0 until n) {
            val x = W * (i + r() * .8f) / n; val w = u * (1.2f + depth * 3.2f) * (.8f + r() * .4f); val sway = sin(t * TAU + i * .7f + li) * u * depth * 1.2f
            drawLine(Brush.horizontalGradient(0f to mix(col, Color.White, .25f), .45f to col, 1f to mix(col, Color.Black, .25f), startX = x - w / 2, endX = x + w / 2), Offset(x, H), Offset(x + sway, -u * 5f), strokeWidth = w)
            if(li>0){
                line(x-w*.18f,H,x-w*.18f+u*2f,H-u*.045f, w*.3f,c("#263E38",.55f))
                line(x+w*.18f,H,x+w*.18f-u*2f,H-u*.045f, w*.25f,c("#263E38",.55f))
            }
            // Nœuds
            var y = H - r() * u * 10f; while (y > 0f) { val k = 1f - y / H; line(x + sway * k - w * .55f, y, x + sway * k + w * .55f, y, max(1f, w * .16f), mix(col, Color.Black, .3f)); y -= u * (9f + depth * 9f) * (.8f + r() * .4f) }
            // Feuilles fines
            for (k in 0 until (1 + depth * 3).toInt()) { val yy = H * (.05f + r() * .45f); val kk = 1f - yy / H; val dir = if (r() < .5f) -1f else 1f
                for (j in 0 until 3) leaf(x + sway * kk, yy + j * u * 1.5f, u * (4f + depth * 4f), dir * (25f + j * 18f) + sin(t * TAU * 2 + i) * 6f, mix(col, c("#2E5A2A"), .2f), .22f) }
        }
        if (li == 0) mist(H * .5f, H * .6f, c("#EAF6DE", .45f))
    }
    // Feuilles qui tombent
    val g = Mul(7)
    for (i in 0 until 10) { val x = (g() * W + sin(t * TAU * 2 + i) * u * 6f); val y = ((g() + t * (.3f + g() * .2f)) % 1f) * H; leaf(x, y, u * 3.4f, t * 720f + i * 40f, c("#9BC46A", .9f), .3f) }
    drawRect(vg(H * .85f, H, 0f to c("#2E5A2A", 0f), 1f to c("#1E3A1E", .6f)), topLeft = Offset(0f, H * .85f), size = Size(W, H * .15f))
}
