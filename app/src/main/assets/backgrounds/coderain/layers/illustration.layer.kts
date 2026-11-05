private fun DrawScope.coderain(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    coderainSky(0f to c("#000502"), .5f to c("#00140A"), 1f to c("#002412"))
    glow(W * .5f, H * .45f, S * 1.1f, c("#0F6B36", .25f), BlendMode.Screen)
    // Trois profondeurs de colonnes : glyphes en points 3 × 5
    for (layer in 0 until 3) {
        val px = S * (.0045f + layer * .0028f); val gw = px * 4.2f; val gh = px * 6.4f; val cols = (W / gw).toInt() + 1; val rows = (H / gh).toInt() + 2
        val r = Mul(100 + layer); val trail = ArrayList<Offset>(); val dim = ArrayList<Offset>(); val heads = ArrayList<Offset>()
        for (cIdx in 0 until cols) {
            if (r() < .35f - layer * .1f) { r(); r(); continue }
            val speed = (1 + (r() * 3).toInt()).toFloat(); val off = r(); val len = 8 + (r() * 18).toInt()
            val head = ((off + t * speed) % 1f) * (rows + len) - len * .2f
            for (row in 0 until rows) {
                val dist = head - row; if (dist < 0f || dist > len) continue
                val seed = (cIdx * 131 + row * 17 + ((t * speed * 6f).toInt() * (if ((cIdx + row) % 5 == 0) 1 else 0))) * 2654435761L.toInt()
                val bits = (seed ushr 7) and 0x7FFF
                val x0 = cIdx * gw; val y0 = row * gh
                val target = if (dist < 1f) heads else if (dist < len * .5f) trail else dim
                for (b in 0 until 15) if ((bits shr b) and 1 == 1) target.add(Offset(x0 + (b % 3) * px * 1.1f, y0 + (b / 3) * px * 1.1f))
            }
        }
        val a = .35f + layer * .3f
        drawPoints(dim, PointMode.Points, c("#1FA352", a * .45f), strokeWidth = px, cap = StrokeCap.Square)
        drawPoints(trail, PointMode.Points, c("#39E07A", a * .85f), strokeWidth = px, cap = StrokeCap.Square)
        drawPoints(heads, PointMode.Points, c("#D9FFE6", min(1f, a * 1.3f)), strokeWidth = px, cap = StrokeCap.Square)
        if (layer == 2) for (i in 0 until heads.size step 9) glow(heads[i].x, heads[i].y, px * 6f, c("#7DFFB0", .18f), BlendMode.Screen)
    }
    // Lignes de balayage très discrètes
    var y = 0f; while (y < H) { rect(0f, y, W, max(1f, S / 900f), c("#000000", .18f)); y += S * .008f }
}
