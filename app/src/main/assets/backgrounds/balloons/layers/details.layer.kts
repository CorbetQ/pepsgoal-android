private fun DrawScope.balloon(x: Float, y: Float, r: Float, cols: List<Color>) {
    val env = Path().apply { moveTo(x, y + r * 1.25f); cubicTo(x - r * 1.35f, y + r * .2f, x - r * 1.05f, y - r * 1.1f, x, y - r * 1.1f); cubicTo(x + r * 1.05f, y - r * 1.1f, x + r * 1.35f, y + r * .2f, x, y + r * 1.25f); close() }
    clipPath(env) {
        val n = cols.size * 2
        for (i in 0 until n) { val a0 = -1f + 2f * i / n; val a1 = -1f + 2f * (i + 1) / n
            fill(poly(x + r * a0 * .35f, y - r * 1.2f, x + r * a1 * .35f, y - r * 1.2f, x + r * a1 * 1.5f, y + r * .1f, x + r * a1 * .2f, y + r * 1.3f, x + r * a0 * .2f, y + r * 1.3f, x + r * a0 * 1.5f, y + r * .1f), cols[i % cols.size]) }
        drawRect(Brush.horizontalGradient(0f to Color.White.copy(alpha = .35f), .45f to Color.Transparent, 1f to Color.Black.copy(alpha = .3f), startX = x - r, endX = x + r), Offset(x - r * 1.4f, y - r * 1.2f), Size(r * 2.8f, r * 2.6f))
    }
    line(x - r * .2f, y + r * 1.25f, x - r * .16f, y + r * 1.55f, max(1f, r * .03f), c("#4A3A2A")); line(x + r * .2f, y + r * 1.25f, x + r * .16f, y + r * 1.55f, max(1f, r * .03f), c("#4A3A2A"))
    drawRoundRect(c("#8A6A44"), Offset(x - r * .18f, y + r * 1.55f), Size(r * .36f, r * .26f), CornerRadius(r * .05f))
}
