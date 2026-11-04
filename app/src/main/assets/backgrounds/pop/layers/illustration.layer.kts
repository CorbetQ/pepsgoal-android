fun DrawScope.popHills(dark: Boolean,t: Float=.3f,environment: fr.pepsgoal.core.SceneEnvironment?=null) {
    withBackgroundStyle("pop",environment?.sun?:.25f,environment?.night?:if(dark)1f else 0f) { paintPopHills(dark,t,environment) }
}
private fun DrawScope.paintPopHills(dark: Boolean,t: Float,environment: fr.pepsgoal.core.SceneEnvironment?) {
    val dark = environment?.let { it.night > .5f } ?: dark
    val w = size.width; val h = size.height
    fun hx(v: Long) = pigment(Color(v),currentBackgroundStyle())
    drawRect(if (dark) hx(0xFF1E1328) else hx(0xFFFFF1E6))
    drawRect(if (dark) hx(0xFF261832) else hx(0xFFFFE6D6), Offset(0f, h * .18f), androidx.compose.ui.geometry.Size(w, h * .82f))
    val sc = Offset(w * .80f, h * .16f)
    drawCircle(if (dark) hx(0xFF3A2648) else hx(0xFFFFE08A), w * .17f, sc)
    drawCircle(if (dark) hx(0xFFFFE07A) else hx(0xFFFFC23D), w * .11f, sc)
    if (dark) drawCircle(hx(0xFF261832), w * .09f, sc + Offset(w * .05f, -w * .03f))
    val dots = HILL_DOTS; val conf = HILL_CONF
    dots.forEachIndexed { i, (x, y) ->
        if (dark) drawCircle(hx(0xB3FFF4EC), 1.6.dp.toPx() + (i % 3) * .6f.dp.toPx(), Offset(w * x, h * y))
        else drawRoundRect(conf[i % 4], Offset(w * x, h * y), androidx.compose.ui.geometry.Size(5.dp.toPx(), 9.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
    }
    fun cloud(x: Float, y: Float, k: Float) {
        val c = if (dark) hx(0xFF2F2040) else Color.White
        drawRoundRect(c, Offset(x, y), androidx.compose.ui.geometry.Size(w * .26f * k, w * .07f * k), androidx.compose.ui.geometry.CornerRadius(w))
        drawRoundRect(c, Offset(x + w * .05f * k, y - w * .04f * k), androidx.compose.ui.geometry.Size(w * .13f * k, w * .09f * k), androidx.compose.ui.geometry.CornerRadius(w))
    }
    cloud(w * (.06f + .04f * sin(t * 6.283f)), h * .14f, 1f); cloud(w * (.5f - .03f * sin(t * 6.283f)), h * .28f, .8f)
    if (environment != null) weatherSky(environment, t, size.height * .4f)
    fun hill(base: Float, amp: Float, ph: Float, col: Color, k: Float) {
        val path = Path().apply {
            moveTo(0f, h); lineTo(0f, base)
            var x = 0f
            while (x <= w + 8f) { lineTo(x, base - amp * (.5f + .5f * sin(x / w * k * 2f * PI.toFloat() + ph + t * 2f * PI.toFloat() * .15f))); x += 8f }
            lineTo(w, h); close()
        }
        drawPath(path,Brush.verticalGradient(listOf(mix(col,Color.White,.16f),col,mix(col,c(currentBackgroundStyle().shadowColor),.28f)),base-amp,h))
    }
    if (dark) { hill(h * .40f, h * .06f, 1f, hx(0xFF3A1F3E), 1.3f); hill(h * .50f, h * .05f, 2.6f, hx(0xFF4A2440), 1.7f); hill(h * .62f, h * .045f, .4f, hx(0xFF2E1B3A), 1.1f) }
    else { hill(h * .40f, h * .06f, 1f, hx(0xFFFFD0B8), 1.3f); hill(h * .50f, h * .05f, 2.6f, hx(0xFFFFB8CF), 1.7f); hill(h * .62f, h * .045f, .4f, hx(0xFFFFC79E), 1.1f) }
    val fl = HILL_FLOWERS
    for (i in 0 until 14) {
        val x = w * ((i * 0.137f + .05f) % 1f); val y = h * (.70f + ((i * 37) % 23) / 100f)
        if (dark) drawCircle(hx(0x8CFFE07A), 2.2.dp.toPx(), Offset(x, y))
        else { for (k in 0 until 5) { val a = k * 1.2566f; drawCircle(fl[i % 4], 3.dp.toPx(), Offset(x + cos(a) * 4.dp.toPx(), y + sin(a) * 4.dp.toPx())) }; drawCircle(hx(0xFFFFD23F), 2.6.dp.toPx(), Offset(x, y)) }
    }
    finish(dark,.55f)
}

private val HILL_DOTS = listOf(.08f to .08f, .22f to .2f, .4f to .1f, .55f to .25f, .62f to .06f, .93f to .3f, .3f to .32f, .12f to .36f)
private val HILL_CONF = listOf(Color(0xFFFF7AD9), Color(0xFF22D3FF), Color(0xFFFFB000), Color(0xFF7CFF6B))
private val HILL_FLOWERS = listOf(Color(0xFFFF7AD9), Color(0xFFFFB000), Color.White, Color(0xFF7CFF6B))
