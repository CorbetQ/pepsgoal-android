internal fun DrawScope.drawSceneWeather(cond: String, clouds: Float, wind: Float, freshness: Float, night: Float, t: Float, count: Int) {
    if (cond !in setOf("rain", "storm", "snow", "fog")) return
    val w = size.width; val h = size.height; val unit = min(w, h)
    val alpha = freshness.coerceIn(0f, 1f)
    if (cond == "fog") {
        drawRect(Color(0xFFCADAD9).copy(alpha = alpha * (.075f - night * .025f)))
        for (i in 0 until 4) {
            val drift = sin(t * 6.283f + i) * h * .015f
            val y = h * (.24f + i * .18f) + drift
            val bank = Path().apply {
                moveTo(-w*.1f, y)
                cubicTo(w*.18f, y-h*.025f, w*.45f, y+h*.035f, w*1.1f, y-h*.015f)
                lineTo(w*1.1f, y+h*.11f)
                cubicTo(w*.67f, y+h*.07f, w*.23f, y+h*.15f, -w*.1f, y+h*.09f)
                close()
            }
            drawPath(bank, Color(0xFFD4E2DD).copy(alpha = alpha * (.08f + i * .012f)))
        }
        return
    }
    val cold = Color(0xFF536780).copy(alpha = alpha * (if (cond == "storm") .14f else .055f))
    drawRect(cold)
    val tilt = .12f + wind.coerceIn(0f, 80f) / 240f
    for (i in 0 until count) {
        val x = ((i * .618034f + t * .17f) % 1.2f) * w
        val y = ((i * .414214f + t * (if (cond == "snow") 1.2f else 8f)) % 1f) * h
        if (cond == "snow") {
            val r = unit * (.002f + (i % 3) * .001f)
            drawCircle(Color(0xFFF3F6EB).copy(alpha = alpha * .78f), r, Offset(x + sin(t * 6.28f + i) * unit * .02f, y))
        } else {
            val len = unit * (.018f + (i % 3) * .009f)
            drawLine(Color(0xFFDCEBF1).copy(alpha = alpha * (.35f + night * .12f)), Offset(x, y), Offset(x - len * tilt, y + len),
                unit * .003f, cap = StrokeCap.Round)
            if (i % 8 == 0) drawArc(Color(0xFFDBE9EB).copy(alpha = alpha * .25f), 190f, 150f, false,
                Offset(x, h * (.82f + (i % 5) * .035f)), androidx.compose.ui.geometry.Size(unit * .018f, unit * .006f), style = Stroke(unit * .002f))
        }
    }
    // Deliberately no fullscreen lightning flash, including with Android animation reduction enabled.
}

@Composable
internal fun ThemeFx(kind: String) {
    val rnd = remember(kind) { Random(kind.hashCode()) }
    val n = particles(if (kind == "stars") 60 else 34)
    val parts = remember(kind, n) { List(n) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), .6f + rnd.nextFloat() * .9f, .35f + rnd.nextFloat() * .5f, 10 + rnd.nextFloat() * 16, rnd.nextFloat() * 6.28f, rnd.nextInt(4).toFloat()) } }
    // Horloge lue dans la phase de dessin : pas de recomposition à chaque image
    val clock = bgPhaseState(30000, fps = 24, cap = if (fr.pepsgoal.app.sys.EnergyState.thermalStatus >= 2) Perf.flags.bgFps else Perf.flags.bgFps * 2)
    val cols = remember(kind) { when (kind) {
        "petals" -> listOf(0xFFFFB7D5, 0xFFFF8FC7, 0xFFFFD6E8, 0xFFFFFFFF)
        "leaves" -> listOf(0xFFE2711D, 0xFFFFB347, 0xFFB5451B, 0xFFD9A441)
        "hearts" -> listOf(0xFFFF4F8B, 0xFFFF9AC1, 0xFFE85BFF, 0xFFFF6B8B)
        "embers" -> listOf(0xFFFFB000, 0xFFFF6A00, 0xFFFF3D00, 0xFFFFD27F)
        "bubbles" -> listOf(0xFFFFFFFF, 0xFFBFE3FF, 0xFF9AE6FF, 0xFFFFFFFF)
        "confetti" -> listOf(0xFFFFC300, 0xFFFF4F8B, 0xFF22D3FF, 0xFF2EF2B0)
        else -> listOf(0xFFFFFFFF, 0xFFFFF3C4, 0xFFC6D4FF, 0xFFFFFFFF)
    }.map { Color(it) } }
    // Un seul tracé réutilisé pour tous les cœurs (aucune allocation par image)
    val heartPath = remember { androidx.compose.ui.graphics.Path() }
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value
        for (f in parts) {
            val c = cols[f[6].toInt()]; val r = 6f * f[2] * density / 2.6f
            if (kind == "stars") {
                // Scintillement sur place
                val a = f[3] * (.35f + .65f * ((sin(t * 6.28f * 6 / f[4] * 10 + f[5]) + 1) / 2))
                drawCircle(c.copy(alpha = a), r * .5f, Offset(f[0] * size.width, f[1] * size.height))
                continue
            }
            val up = kind == "embers" || kind == "bubbles"
            val k = (f[1] + t * 30f / f[4]) % 1f
            val y = (if (up) 1f - k else k) * (size.height + 40f) - 20f
            val x = f[0] * size.width + sin(k * 12f + f[5]) * 26f * density / 2.6f
            val a = f[3] * (if (up) (1f - k).coerceIn(0f, 1f) * 1.2f else 1f).coerceAtMost(1f)
            val rot = (k * 720f + f[5] * 57f)
            when (kind) {
                "bubbles" -> drawCircle(c.copy(alpha = a * .7f), r * 1.4f, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * density / 2.6f))
                "embers" -> drawCircle(c.copy(alpha = a), r * .55f, Offset(x, y))
                "hearts" -> run {
                    val s = r * 1.6f
                    val path = heartPath.apply {
                        reset()
                        moveTo(x, y + s * .9f)
                        cubicTo(x - s * 1.4f, y, x - s * .6f, y - s * 1.1f, x, y - s * .35f)
                        cubicTo(x + s * .6f, y - s * 1.1f, x + s * 1.4f, y, x, y + s * .9f)
                    }
                    drawPath(path, c.copy(alpha = a))
                }
                else -> rotate(rot, Offset(x, y)) {
                    // Pétales et feuilles : ovales ; confettis : petits rectangles
                    if (kind == "confetti") drawRect(c.copy(alpha = a), Offset(x - r * .5f, y - r), androidx.compose.ui.geometry.Size(r, r * 2f))
                    else drawOval(c.copy(alpha = a), Offset(x - r, y - r * .6f), androidx.compose.ui.geometry.Size(r * 2f, r * 1.2f))
                }
            }
        }
    }
}

@Composable
internal fun SnowFx() {
    val n = particles(46)
    val flakes = remember(n) { List(n) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), 2 + Random.nextFloat() * 5, .45f + Random.nextFloat() * .5f, 9 + Random.nextFloat() * 14) } }
    val clock = bgPhaseState(20000, fps = 24, cap = if (fr.pepsgoal.app.sys.EnergyState.thermalStatus >= 2) Perf.flags.bgFps else Perf.flags.bgFps * 2)
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value
        for (f in flakes) {
            val y = ((f[1] + t * 20f / f[4]) % 1f) * size.height
            val x = f[0] * size.width + sin(y / 80f) * 20
            drawCircle(Color.White.copy(alpha = f[3]), f[2], Offset(x, y))
        }
    }
}
