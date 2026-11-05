private fun DrawScope.angevine(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    val hz = H * .46f
    if (lighting == null) {
    angevineSky(0f to c("#7FA8D8"), .45f to c("#BCD4EC"), .8f to c("#F6DCC0"), 1f to c("#FBE8CE"), y1 = hz)
    val sx = W * .22f; val sy = H * .26f
    glow(sx, sy, S * 1.1f, c("#FFE4B8", .45f), BlendMode.Screen); glow(sx, sy, S * .2f, c("#FFF4DC", .9f), BlendMode.Screen); disc(sx, sy, S * .04f, c("#FFF9EC"))
    for (i in 0 until 4) { val dx = ((t * (.4f + i * .1f) + i * .29f) % 1.3f - .15f) * W * 1.1f; softCloud(dx, H * (.06f + i * .065f), W * (.5f + i * .07f), c("#FFFFFF", .5f - i * .06f), c("#FFEBD6", .45f), 80 + i) }
    } else angevineLiveSky(lighting, t, H * .46f)
    val nz = Nz(44)
    // Coteaux du Layon au loin, en plusieurs plans
    ridgeFill(ridgePts(hz - H * .02f, H * .035f, 80) { x -> .5f + .5f * nz.at(x * 3f, 3) }, vg(hz - H * .06f, hz, 0f to c("#AFB4CF"), 1f to c("#C9C3CC")), hz + H * .02f)
    val mid = ridgePts(hz + H * .02f, H * .05f, 80) { x -> .45f + .55f * nz.at(x * 4f + 5f, 3) }
    ridgeFill(mid, vg(hz - H * .03f, hz + H * .1f, 0f to c("#B6C089"), 1f to c("#8FA060")), hz + H * .12f)
    clipPath(Path().apply { moveTo(0f, hz + H * .12f); var i = 0; while (i < mid.size) { lineTo(mid[i], mid[i + 1]); i += 2 }; lineTo(W, hz + H * .12f); close() }) {
        // Parcelles de vigne : rangs obliques, quelques parcelles déjà dorées
        for (p in 0 until 6) { val x0 = W * (p / 6f - .05f); val gold = p % 3 == 1
            clipRect(x0, 0f, x0 + W / 6f + u, H) { for (k in -10..30) line(x0 + k * u * 1.6f, hz + H * .12f, x0 + k * u * 1.6f + W * .14f, hz - H * .04f, max(1f, u * .3f), if (gold) c("#C9A24A", .55f) else c("#5E7448", .45f)) } }
    }
    // Le Layon, ses peupliers et le moulin cavier sur la butte
    val river = hz + H * .1f
    conifers(26, 12, u * 7f, u * 11f, c("#6E8A4E"), { river }, 0f, W)
    fill(Path().apply { moveTo(0f, river); cubicTo(W * .3f, river - u * 1.5f, W * .55f, river + u * 2.5f, W, river + u * .5f); lineTo(W, river + u * 1.6f); cubicTo(W * .55f, river + u * 3.6f, W * .3f, river - u * .4f, 0f, river + u * 1.2f); close() }, c("#CFE2F0"))
    cavier(W * .74f, hz + H * .02f, u * .9f, t * TAU * 4f)
    // Logis de tuffeau au milieu des vignes, cèdre derrière
    val mb = hz + H * .14f
    crown(W * .14f, mb - u * 16f, u * 10f, u * 9f, 33, c("#6E8A5A"), c("#3E5A3A"), c("#22382A"), -.6f, -.8f, groundY=mb)
    manoir(W * .36f, mb, u, 0f)
    canopy(6, 51, u * 2f, u * 3.6f, c("#6E8A55"), c("#9FB574"), false, { mb + u * .5f }, W * .02f, W * .2f)
    canopy(5, 52, u * 2f, u * 3.6f, c("#6E8A55"), c("#9FB574"), false, { mb + u * .5f }, W * .56f, W * .98f)
    // Vignoble au premier plan : rangs qui fuient vers le logis, feuilles vertes, dorées et rousses
    val vx = W * .5f; val vy = mb + u * 1f
    drawRect(vg(vy, H, 0f to c("#C9A87A"), 1f to c("#9A7A52")), Offset(0f, vy), Size(W, H - vy))
    val g = Mul(3)
    val rowCol = listOf(c("#7E9A44"), c("#9AA84E"), c("#C9A24A"), c("#6E8A3E"), c("#B8762E"), c("#8FA048"), c("#D9AE4A"))
    for (k in -12..12) {
        val bx = vx + k * W * .16f
        val steps = 70
        for (j in 0 until steps) {
            val f0 = j / steps.toFloat(); val f = f0 * f0
            val y = vy + (H - vy) * f; val x = vx + (bx - vx) * f
            val sz = u * (.4f + 5.5f * f)
            if (x < -sz * 2 || x > W + sz * 2) continue
            val base0 = rowCol[(k + 12) % rowCol.size]; val col = mix(base0, c("#E8C25A"), g() * .25f)
            val sway = sin(t * TAU * 2 + j * .3f + k) * sz * .08f
            // Haie de feuillage : ombre au pied, masse, puis touffes éclairées par le soleil (à gauche)
            ellGlow(x, y, sz * 1.1f, sz * .25f, c("#4A3A22", .35f))
            disc(x + sway, y - sz * .6f, sz * .8f, mix(col, c("#2E3E1E"), .35f))
            disc(x + sway - sz * .25f, y - sz * .85f, sz * .55f, col)
            disc(x + sway - sz * .4f, y - sz * 1.05f, sz * .28f, mix(col, c("#FFF2C0"), .3f))
            if (j % 5 == 0 && f > .2f) line(x, y, x, y - sz * 1.5f, max(1f, sz * .08f), c("#6A5A48"))
            if (f > .35f && g() < .12f) { val gx = x + (g() - .5f) * sz; val gy = y - sz * .2f; for (m in 0 until 6) disc(gx + (m % 3 - 1) * sz * .12f, gy + (m / 3) * sz * .12f, sz * .08f, c("#E8D27A")) }
        }
        // Rosier au bout du rang
        val ex = vx + (bx - vx); if (ex > -u * 6f && ex < W + u * 6f) { for (m in 0 until 7) disc(ex + (g() - .5f) * u * 5f, H - u * (3f + g() * 5f), u * (1.2f + g()), if (m % 2 == 0) c("#E8607A") else c("#F49AAE")); disc(ex, H - u * 2f, u * 2f, c("#4E6A32")) }
    }
    // Étourneaux en vol
    for (i in 0 until 7) { val k = (t * 1.5f + i * .13f) % 1f; val x = W * (1.1f - k * 1.3f); val y = H * (.16f + (i % 3) * .03f) + sin(k * TAU * 2 + i) * u; val s = u * .9f; val fl = sin(t * TAU * 18 + i) * .5f
        drawPath(Path().apply { moveTo(x - s * 1.4f, y - s * (.4f + fl)); quadraticTo(x - s * .5f, y - s * .2f, x, y + s * .2f); quadraticTo(x + s * .5f, y - s * .2f, x + s * 1.4f, y - s * (.4f + fl)) }, c("#3A3F4A", .7f), style = Stroke(max(1f, u * .28f))) }
    mist(hz + H * .1f, u * 4f, c("#FFF4E6", .35f))
}
