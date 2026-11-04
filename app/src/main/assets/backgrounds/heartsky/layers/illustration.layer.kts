private fun DrawScope.heartsky(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    heartskySky(0f to c("#C9A6F2"), .3f to c("#E9B6E6"), .55f to c("#FFC6DC"), .8f to c("#FFD6E6"), 1f to c("#FFE6EF"))
    glow(W * .5f, H * .62f, S * 1.1f, c("#FFF2E6", .6f), BlendMode.Screen)
    // Mer de nuages pastel (couches)
    for (k in 0 until 4) {
        val d = k / 3f; val y0 = H * (.62f + d * .3f); val r = Mul(70 + k)
        val col = mix(c("#F6D2F0"), c("#FFE3EE"), d); val shade = mix(c("#D9A6D8"), c("#F2B6CE"), d)
        for (i in 0 until 14) { val x = W * (i / 13f) + (r() - .5f) * W * .1f + sin(t * TAU + k + i) * S * .01f; val rx = W * (.14f + r() * .1f); val ry = rx * (.5f + r() * .2f)
            drawPath(lobed(x, y0, rx, ry, 1000 + k * 30 + i, 6, .18f), vg(y0 - ry, y0 + ry, 0f to Color.White, .6f to col, 1f to shade)) }
    }
    // Cœurs qui s'envolent
    val r = Mul(9)
    for (i in 0 until 16) {
        val sp = .4f + r() * .5f; val u = (r() + t * sp) % 1f; val x = W * (.08f + r() * .84f) + sin(u * TAU * 1.5f + i) * S * .04f; val y = H * (1.05f - u * 1.15f); val s = S * (.025f + r() * .05f)
        val col = listOf(c("#FF5C8A"), c("#FF8AB0"), c("#E0559A"), c("#FFB0C8"))[i % 4]; val fade = sm(0f, .1f, u) * sm(1f, .85f, u)
        rotate(sin(u * TAU + i) * 12f, Offset(x, y)) {
            val hp = heart(x, y, s)
            drawPath(hp, Brush.linearGradient(0f to mix(col, Color.White, .45f).a(fade), 1f to col.a(fade), start = Offset(x - s, y - s), end = Offset(x + s, y + s)))
            drawOval(Color.White.copy(alpha = .55f * fade), topLeft = Offset(x - s * .62f, y - s * .5f), size = Size(s * .35f, s * .22f))
        }
    }
    val b = Mul(4); for (i in 0 until 14) glow(b() * W, b() * H * .6f, S * (.02f + b() * .04f), c("#FFFFFF", .3f), BlendMode.Screen)
}
