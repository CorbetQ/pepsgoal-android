private fun DrawScope.tram(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H)
    val cam = Cam(-6.2f, -4.5f, 1.9f, 15f, W * 1.05f, W * .5f, H * .67f)
    val hy = cam.hy; val vp = cam.p(5000f, 0f, 1.9f)
    val sun = Offset(lighting?.let { W * (.12f + .76f * it.sun) } ?: (vp.x - W * .1f), hy - H * .045f)

    if (lighting == null) {
    // --- Ciel de crépuscule
    tramSky(0f to c("#2C3264"), .16f to c("#4D4A86"), .3f to c("#86649C"), .42f to c("#C97C98"), .5f to c("#EE9C86"), .56f to c("#FFC08C"), .6f to c("#FFDDA8"), 1f to c("#FFE7BE"), y1 = hy + H * .02f)
    glow(sun.x, sun.y, S * 1.25f, c("#FF9F72", .45f), BlendMode.Screen)
    glow(sun.x, sun.y, S * .42f, c("#FFE2B0", .85f), BlendMode.Screen)
    val drift = t * W * .08f
    softCloud(W * .15f + drift, H * .17f, W * .9f, c("#B58AB4", .32f), c("#FFC7B5", .35f), 21)
    softCloud(W * .85f - drift * .6f, H * .27f, W * .7f, c("#D98FA0", .3f), c("#FFD4B8", .4f), 22)
    for ((cy, cw, sd) in listOf(Triple(.34f, 1.2f, 31), Triple(.44f, 1f, 32))) {
        val r = Mul(sd)
        for (i in 0 until 7) { val x = W * (r() * 1.2f - .1f) + drift * (.5f + r()); val y = H * cy + (r() - .5f) * H * .03f; val w = W * cw * (.25f + r() * .25f)
            ellGlow(x, y, w, w * .1f, c("#9C6E9E", .22f)); ellGlow(x, y + w * .05f, w * .8f, w * .045f, c("#FFC2A0", .3f), BlendMode.Screen) }
    }
    softCloud(W * .35f + drift * .8f, H * .4f, W * .6f, c("#F2A68E", .28f), c("#FFE6C8", .45f), 23)
    softCloud(W * .7f - drift * .4f, H * .5f, W * .45f, c("#FFC09A", .25f), c("#FFF0D8", .4f), 24)

    } else tramLiveSky(lighting, t, H * .67f)
    // --- Cathédrale Saint-Maurice, sur sa butte au bout de la rue (silhouette brumeuse)
    run {
        val ka = 400f; val base = cam.p(ka, -3f, 22f); val k = cam.k(ka, -3f)
        val col = c("#A9798F"); val rim = c("#FFD3A0", .7f)
        fun r(x0: Float, y0: Float, x1: Float, y1: Float) = rect(base.x + x0 * k, base.y - y1 * k, (x1 - x0) * k, (y1 - y0) * k, col)
        fun spire(xc: Float, w: Float, y0: Float, y1: Float) {
            fill(poly(base.x + (xc - w) * k, base.y - y0 * k, base.x + xc * k, base.y - y1 * k, base.x + (xc + w) * k, base.y - y0 * k), col)
            line(base.x + (xc - w) * k, base.y - y0 * k, base.x + xc * k, base.y - y1 * k, max(1f, k * .5f), rim)
        }
        r(-22f, -30f, 22f, 2f) // butte et maisons basses
        r(-14f, 0f, -5f, 44f); r(5f, 0f, 14f, 44f)          // tours
        r(-5f, 0f, 5f, 40f); r(-3.6f, 40f, 3.6f, 51f)       // tour centrale (Renaissance)
        fill(poly(base.x - 3.6f * k, base.y - 51f * k, base.x, base.y - 57f * k, base.x + 3.6f * k, base.y - 51f * k), col)
        line(base.x, base.y - 57f * k, base.x, base.y - 61f * k, max(1f, k * .35f), col)
        spire(-9.5f, 4.6f, 44f, 77f); spire(9.5f, 4.6f, 44f, 77f)
        r(14f, 0f, 60f, 22f); fill(poly(base.x + 14f * k, base.y - 22f * k, base.x + 37f * k, base.y - 32f * k, base.x + 60f * k, base.y - 22f * k), col) // nef
        for (sx in listOf(-9.5f, 9.5f)) for (j in 0 until 3) { val yy = 50f + j * 7f; rect(base.x + (sx - .5f) * k, base.y - (yy + 2f) * k, k, 2f * k, c("#8C6378")) }
        mist(base.y + 2f * k, H * .05f, c("#FFC8A0", .45f))
    }

    // --- Immeubles : tuffeau et ardoise, combles à la Mansart et lucarnes
    fun buildings(left: Boolean) {
        val fl = if (left) -12f else 11.5f; val sgn = if (left) -1f else 1f
        val r = Mul(if (left) 101 else 202)
        val lots = ArrayList<FloatArray>()
        var a = -9f
        while (a < 330f) { val w = 8f + r() * 6f; lots.add(floatArrayOf(a, a + w, 12.5f + r() * 5f, 3.2f + r() * 1.2f, r(), r())); a += w }
        val lit = if (left) c("#C9A9A6") else c("#F4C994"); val shade = if (left) c("#8E7C93") else c("#C49A92")
        val slate = c("#4F5470"); val slateLit = c("#8D86A0")
        for (b in lots.asReversed()) {
            val a0 = max(b[0], -5.5f); val a1 = b[1]; val hc = b[2]; val hr = b[3]
            if (a1 < a0 + .5f) continue
            val z = cam.z(a1, fl); val haze = sm(25f, 260f, z)
            val skyHaze = c("#E9A99A")
            // Pignon côté caméra (visible au-dessus d'un voisin plus bas)
            fill(cam.poly3(a0, fl, 0f, a0, fl + sgn * 12f, 0f, a0, fl + sgn * 12f, hc + hr, a0, fl, hc + hr), mix(mix(shade, c("#7A6A83"), .3f), skyHaze, haze * .8f))
            // Comble à la Mansart (plan incliné), faîtage plat
            val back = fl + sgn * 2.6f
            val roofP = cam.poly3(a0, fl, hc, a1, fl, hc, a1, back, hc + hr, a0, back, hc + hr)
            val rt = cam.p(a0, back, hc + hr); val rb = cam.p(a0, fl, hc)
            fill(roofP, vg(rt.y, rb.y, 0f to mix(slateLit, skyHaze, haze * .7f), 1f to mix(slate, skyHaze, haze * .7f)))
            // Façade
            val top = cam.p((a0 + a1) / 2, fl, hc); val bot = cam.p((a0 + a1) / 2, fl, 0f)
            val tone = .9f + b[4] * .2f
            val fTop = mix(lit, Color.White, (tone - 1f).coerceAtLeast(0f)); val fBot = shade
            fill(cam.poly3(a0, fl, 0f, a1, fl, 0f, a1, fl, hc, a0, fl, hc), vg(top.y, bot.y, 0f to mix(fTop, skyHaze, haze * .75f), .6f to mix(mix(fTop, fBot, .55f), skyHaze, haze * .75f), 1f to mix(fBot, skyHaze, haze * .75f)))
            // Corniche
            fill(cam.poly3(a0, fl, hc - .35f, a1, fl, hc - .35f, a1, fl, hc, a0, fl, hc), mix(c("#FFF0D8", .55f), skyHaze, haze))
            // Fenêtres, balcons, lucarnes (seulement à portée de vue)
            if (z < 140f) {
                val w = a1 - a0; val n = max(2, (w / 2.5f).toInt()); val step = w / n
                val floors = ((hc - 4.6f) / 3.1f).toInt()
                val wr = Mul((b[0] * 13f).toInt())
                for (i in 0 until n) {
                    val wa = a0 + step * (i + .5f) - .55f
                    for (fy in 0 until floors) {
                        val h0 = 4.6f + fy * 3.1f + .5f
                        val on = wr() < .3f
                        val glass = if (on) mix(c("#FFC874"), c("#FF9E5C"), wr()) else mix(c("#6B6286"), c("#3E3D5C"), wr())
                        fill(cam.poly3(wa, fl, h0, wa + 1.1f, fl, h0, wa + 1.1f, fl, h0 + 2f, wa, fl, h0 + 2f), mix(glass, skyHaze, haze * .8f))
                        if (fy == 0) { // balcon filant en fer forgé
                            val l0 = cam.p(wa - .15f, fl, h0 + .05f); val l1 = cam.p(wa + 1.25f, fl, h0 + .05f)
                            line(l0.x, l0.y, l1.x, l1.y, max(1f, cam.k(wa, fl) * .08f), c("#2B2838", .8f - haze * .6f))
                        }
                    }
                    // Lucarne : petite façade en retrait sur le comble, fronton en tuffeau
                    val dl = fl + sgn * .9f; val db = hc + .3f; val dt = hc + 2.1f
                    fill(cam.poly3(wa - .15f, dl, db, wa + 1.25f, dl, db, wa + 1.25f, dl, dt, wa + .55f, dl, dt + .8f, wa - .15f, dl, dt), mix(mix(fTop, fBot, .3f), skyHaze, haze * .75f))
                    fill(cam.poly3(wa + .1f, dl, db + .2f, wa + 1f, dl, db + .2f, wa + 1f, dl, dt - .1f, wa + .1f, dl, dt - .1f), mix(if (wr() < .25f) c("#FFC070") else c("#4A4766"), skyHaze, haze * .8f))
                }
                // Rez-de-chaussée : vitrines éclairées et stores
                val sh = cam.poly3(a0 + .6f, fl, .2f, a1 - .6f, fl, .2f, a1 - .6f, fl, 3.2f, a0 + .6f, fl, 3.2f)
                fill(sh, mix(c("#FFB766", .85f), skyHaze, haze * .6f))
                val aw = listOf(c("#9C3B3B"), c("#2F5D50"), c("#3B4D7A"), c("#B07A2A"))[(b[5] * 4).toInt().coerceIn(0, 3)]
                fill(cam.poly3(a0 + .5f, fl, 3.3f, a1 - .5f, fl, 3.3f, a1 - .5f, fl - sgn * 1.2f, 2.9f, a0 + .5f, fl - sgn * 1.2f, 2.9f), mix(aw, skyHaze, haze * .6f))
            }
            // Cheminée
            if (b[4] > .4f) { val ca0 = a0 + (a1 - a0) * .3f; fill(cam.poly3(ca0, back, hc + hr, ca0 + .9f, back, hc + hr, ca0 + .9f, back, hc + hr + 1.4f, ca0, back, hc + hr + 1.4f), mix(c("#6E5E6E"), skyHaze, haze * .8f)) }
        }
    }
    buildings(true); buildings(false)
    // Lumière rasante dans l'axe de la rue
    if ((lighting?.night ?: 0f) < .1f) glow(sun.x, hy, S * .7f, c("#FFC48A", .55f), BlendMode.Screen)
    if ((lighting?.night ?: 0f) < .1f) rays(sun.x, sun.y, H * .55f, 7, 75f, 60f, c("#FFE0B0", .08f), 9, t)

    // --- Sol : chaussée, trottoirs
    val far = 600f
    fill(cam.ground(-5f, far, -12f, 12f), vg(hy, H, 0f to c("#C9989A"), .25f to c("#8D7280"), 1f to c("#5B4C5E")))
    fill(cam.ground(-5f, far, 8.5f, 11.5f, .12f), vg(hy, H, 0f to c("#D5A7A0"), .3f to c("#A3878F"), 1f to c("#7C6B78")))
    fill(cam.ground(-5f, far, -12f, -6.2f, .12f), vg(hy, H, 0f to c("#D5A7A0"), .3f to c("#9C8290"), 1f to c("#6D5F72")))

    // --- Voies engazonnées (deux voies), rails
    for (tc in listOf(0f, 3.5f)) {
        fill(cam.ground(-5f, far, tc - 1.45f, tc + 1.45f, .02f), vg(hy, H, 0f to c("#B2A68A"), .12f to c("#7F8B5C"), .4f to c("#5E7A45"), 1f to c("#3F5A33")))
    }
    // Brins d'herbe (en trois teintes), ondulation légère
    run {
        val r = Mul(61); val sway = sin(t * TAU)
        val tones = listOf(c("#2F4A2A", .8f), c("#6E8F4A", .8f), c("#A7B56A", .7f))
        val paths = List(3) { Path() }
        for (i in 0 until 2600) {
            val a = -5f + 40f * r().pow(1.8f); val tc = if (r() < .5f) 0f else 3.5f; val l = tc - 1.4f + r() * 2.8f
            if (abs(abs(l - tc) - .72f) < .1f) continue
            if (tc == 0f && a > -.1f) continue
            val hb = .05f + r() * .09f; val o = cam.p(a, l, .02f); val k = cam.k(a, l)
            if (o.y > H + 4f || o.x < -4f || o.x > W + 4f) continue
            val lean = (r() - .5f) * .05f + sway * .012f
            val pp = paths[(r() * 3).toInt().coerceIn(0, 2)]; pp.moveTo(o.x, o.y); pp.lineTo(o.x + lean * k, o.y - hb * k)
        }
        paths.forEachIndexed { i, pp -> drawPath(pp, tones[i], style = Stroke(max(1f, S / 500f), cap = StrokeCap.Round)) }
    }
    for (tc in listOf(0f, 3.5f)) for (rl in listOf(-.72f, .72f)) {
        val l = tc + rl
        fill(cam.ground(-5f, far, l - .07f, l + .07f, .03f), c("#4B4650"))
        fill(cam.ground(-5f, far, l - .025f, l + .025f, .035f), vg(hy, H, 0f to c("#FFE3C2"), .3f to c("#F4C6B0"), 1f to c("#C7A6B2")))
    }

    // --- Ombre portée du tram (soleil bas dans l'axe de la rue)
    run {
        val sh = c("#3A2E4A", .32f); val dx = -19f; val dl = 1.2f
        fill(cam.poly3(0f, -1.2f, .03f, TL, -1.2f, .03f, TL, 1.2f, .03f, TL + dx, 1.2f + dl, .03f, dx, 1.2f + dl, .03f, dx, -1.2f + dl, .03f), sh)
    }

    // --- Arbres côté droit (contre-jour, liseré doré)
    fun tree(a: Float, l: Float, seed: Int, hTop: Float) {
        val r = Mul(seed); val k = cam.k(a, l); val b = cam.p(a, l, .1f); val top = cam.p(a, l, hTop)
        val fork = cam.p(a, l, hTop * .45f)
        line(b.x, b.y, fork.x, fork.y, k * .3f, c("#3B3040"), StrokeCap.Butt)
        for (j in 0 until 3) { val e = cam.p(a, l + (j - 1) * 1.2f, hTop * (.62f + r() * .1f)); line(fork.x, fork.y, e.x, e.y, k * .14f, c("#3B3040")) }
        val cy = top.y + (b.y - top.y) * .22f; val rx = k * hTop * .34f
        crown(top.x, cy, rx, rx * .85f, seed * 7, c("#B8A06A"), c("#4D5843"), c("#262E2E"), -.75f, -.65f, groundY=b.y)
        glow(top.x - rx * .6f, cy - rx * .5f, rx * .8f, c("#FFC27A", .18f), BlendMode.Screen)
    }
    for (i in 6 downTo 0) tree(6f + i * 12f, 10f, 300 + i, 9.5f)

    // --- Réverbères contemporains (mât anthracite, tête en disque, lumière chaude)
    fun lamp(a: Float, l: Float, hh: Float = 8f) {
        val k = cam.k(a, l); val b = cam.p(a, l, .3f); val tp = cam.p(a, l, hh); val arm = cam.p(a, l + .9f, hh + .2f)
        line(b.x, b.y, tp.x, tp.y, max(1.5f, k * .14f), c("#2E2B36"), StrokeCap.Butt)
        line(tp.x, tp.y, arm.x, arm.y, max(1f, k * .08f), c("#2E2B36"))
        glow(arm.x, arm.y + k * .2f, k * 1.8f, c("#FFC982", .4f), BlendMode.Screen)
        drawOval(c("#3A3642"), topLeft = Offset(arm.x - k * .4f, arm.y - k * .06f), size = Size(k * .8f, k * .13f))
        drawOval(c("#FFF1D0"), topLeft = Offset(arm.x - k * .28f, arm.y + k * .03f), size = Size(k * .56f, k * .06f))
    }
    for (a in listOf(80f, 52f, 30f)) lamp(a, 1.75f)

    // ======== La rame ========
    // dessous (bogies dans l'ombre)
    fill(cam.poly3(corner(.25f), -1.1f, .02f, TL - corner(.25f), -1.1f, .02f, TL - corner(.25f), -1.1f, .3f, corner(.25f), -1.1f, .3f), c("#1E1A24"))
    // Flanc : caisse blanche, dégradé (bas plus sombre, reflet chaud du couchant vers le fond)
    val sideAll = cam.sideRect(0f, TL, .25f, 3.32f)
    val fA = cam.side(corner(1.5f), 1.5f); val fB = cam.side(TL, 1.5f)
    val sTop = cam.side(5f, 3.3f); val sBot = cam.side(5f, .3f)
    fill(sideAll, vg(sTop.y, sBot.y, 0f to c("#FFF6EC"), .45f to c("#F1E8E6"), .8f to c("#D9CBD4"), 1f to c("#A79AAE")))
    fill(sideAll, lg(fA.x, fA.y, fB.x, fB.y, 0f to c("#7E6E96", .18f), .5f to c("#FFB98A", .0f), 1f to c("#FFC796", .5f)))
    clipPath(sideAll) {
        // Livrée arc-en-ciel : bandes obliques + cercles translucides
        fun band(a0: Float, w: Float, h0: Float, h1: Float, rev: Boolean, slant: Float = .22f) {
            for (i in 0 until 6) { val ci = if (rev) 5 - i else i; fill(cam.sideBand(a0 + i * w, w + .02f, h0, h1, slant), c(RAINBOW[ci])) }
        }
        band(corner(.8f) - .1f, .42f, .25f, 1.12f, true, .55f)   // grand dièdre sous la fenêtre de cabine
        band(2.4f, .5f, 2.45f, 3.3f, true)                    // bandeau haut au-dessus de la 1re porte
        band(9.0f, .26f, .25f, 3.3f, false); band(18.6f, .3f, .25f, 3.3f, true); band(26.0f, .28f, .25f, 3.3f, false)
        band(13.2f, .45f, 2.45f, 3.3f, false); band(21.4f, .4f, .25f, 1.12f, true)
        for ((ac, hc, rr) in listOf(Triple(2.3f, .55f, .5f), Triple(3.6f, .9f, .38f), Triple(4.2f, 2.9f, .45f), Triple(9.6f, 1.6f, .5f), Triple(19.4f, .8f, .55f), Triple(26.6f, 2.6f, .5f), Triple(14.6f, 2.95f, .42f), Triple(22.1f, .6f, .4f)))
            fill(cam.sideCircle(ac, hc, rr), Color.White.copy(alpha = .16f))
        // Vitrages (verre sombre reflétant le ciel, intérieur chaud)
        val win = listOf(4.6f to 8.35f, 8.95f to 11.75f, 12.3f to 14.4f, 16.2f to 18.3f, 18.45f to 20.5f, 21.05f to 23.85f, 24.4f to 28.2f)
        val doors = listOf(3.0f, 14.6f, 28.45f)
        fun glass(p: Path, a0: Float) {
            val gt = cam.side(a0, 2.45f); val gb = cam.side(a0, 1.05f)
            fill(p, vg(gt.y, gb.y, 0f to c("#4B3F5E"), .35f to c("#241F33"), 1f to c("#15131D")))
            fill(p, c("#FFB86A", .16f))
        }
        val cabWin = cam.sideRect(0f, 2.7f, 1.12f, 2.62f); glass(cabWin, 1f)
        for ((a0, a1) in win) {
            val p = cam.sideRect(a0, a1, 1.08f, 2.45f); glass(p, a0)
            val mid = (a0 + a1) / 2; val m0 = cam.side(mid, 1.08f); val m1 = cam.side(mid, 2.45f)
            line(m0.x, m0.y, m1.x, m1.y, max(1f, cam.k(mid, -1.2f) * .07f), c("#E9E1E4"), StrokeCap.Butt)
        }
        for (a0 in doors) {
            val p = cam.sideRect(a0, a0 + 1.35f, .45f, 2.42f); glass(p, a0)
            val m0 = cam.side(a0 + .675f, .45f); val m1 = cam.side(a0 + .675f, 2.42f)
            line(m0.x, m0.y, m1.x, m1.y, max(1f, cam.k(a0, -1.2f) * .05f), c("#0D0C12"), StrokeCap.Butt)
            drawPath(p, c("#2B2733"), style = Stroke(max(1f, cam.k(a0, -1.2f) * .06f)))
            val lb = cam.side(a0 - .12f, 2.25f); disc(lb.x, lb.y, max(1f, cam.k(a0, -1.2f) * .04f), c("#FFB547"))
        }
        // Livrée par-dessus le verre (vinyle micro-perforé, en transparence)
        for (i in 0 until 6) fill(cam.sideBand(9.0f + i * .26f, .28f, 1.08f, 2.45f, .22f), c(RAINBOW[i], .35f))
        // Soufflets d'intercirculation
        for (aj in listOf(8.6f, 11.95f, 20.7f, 24.0f)) fill(cam.sideRect(aj, aj + .2f, .3f, 3.32f), c("#2A2630"))
        // Jupe basse et liseré
        fill(cam.sideRect(0f, TL, .25f, .44f), c("#3E3A46"))
        val j0 = cam.side(corner(.46f), .46f); val j1 = cam.side(TL, .46f)
        line(j0.x, j0.y, j1.x, j1.y, max(1f, S / 600f), c("#FFFFFF", .35f))
        // Liseré lumineux du toit (rim light du couchant)
        val r0 = cam.side(corner(3.2f), 3.22f); val r1 = cam.side(TL - 1f, 3.22f)
        line(r0.x, r0.y, r1.x, r1.y, max(1.5f, S / 300f), c("#FFE2BE", .9f))
    }

    // Face avant : bombée, à contre-jour (douce), liseré chaud côté gauche
    val face = cam.frontZone(-1f, 1f, { .25f }, { 3.32f })
    val fl = cam.front(-1f, 1.5f); val fr = cam.front(1f, 1.5f); val ft = cam.front(0f, 3.32f); val fb = cam.front(0f, .25f)
    fill(face, lg(fl.x, 0f, fr.x, 0f, 0f to c("#FFF1E4"), .3f to c("#ECE4EA"), .75f to c("#CFC8D6"), 1f to c("#A99FB6")))
    fill(face, vg(ft.y, fb.y, 0f to c("#FFFFFF", .25f), .55f to c("#FFFFFF", 0f), .85f to c("#6E5F80", .12f), 1f to c("#4E425E", .35f)))
    // Bas de caisse avant
    fill(cam.frontZone(-1f, 1f, { .25f }, { .42f }), c("#45404E"))
    // Pare-brise noir enveloppant, girouette, bandeau du numéro
    val wsb = { q: Float -> 1.13f + .32f * q * q }
    val ws = cam.frontZone(-.96f, .96f, wsb, { 3.08f })
    val wt = cam.front(0f, 3.08f); val wb = cam.front(0f, 1.13f)
    fill(ws, vg(wt.y, wb.y, 0f to c("#16141C"), .28f to c("#1B1824"), .45f to c("#5B4A68"), .7f to c("#2A2536"), 1f to c("#121018")))
    // Reflet glissant du ciel dans la vitre
    clipPath(ws) {
        val sx = (fl.x + (fr.x - fl.x) * (.25f + .2f * sin(t * TAU))); val k = cam.k(0f, 0f)
        fill(poly(sx - k * .2f, wt.y, sx + k * .35f, wt.y, sx - k * .05f, wb.y, sx - k * .6f, wb.y), c("#FFD9C2", .14f))
        fill(poly(sx + k * .45f, wt.y, sx + k * .55f, wt.y, sx + k * .15f, wb.y, sx + k * .05f, wb.y), c("#FFE6D2", .12f))
    }
    // Séparation bandeau / vitre
    run { val path = Path(); for (i in 0..24) { val q = -.9f + 1.8f * i / 24; val o = cam.front(q, 2.68f); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        drawPath(path, c("#55505E"), style = Stroke(max(1f, cam.k(0f, 0f) * .02f))) }
    // Girouette « A Avrillé » (LED orange) et numéro 1004 (blanc)
    run {
        fun q4(q0: Float, q1: Float, h0: Float, h1: Float): FloatArray {
            val a = cam.front(q0, h1); val b = cam.front(q1, h1); val cc = cam.front(q0, h0); val d = cam.front(q1, h0)
            return floatArrayOf(a.x, a.y, b.x, b.y, cc.x, cc.y, d.x, d.y)
        }
        val k = cam.k(0f, 0f)
        val box = q4(-.5f, -.38f, 2.4f, 2.6f)
        fill(quadPath(box, 0f, 0f, 1f, 1f), c("#C2381E"))
        ledText("A", q4(-.48f, -.4f, 2.43f, 2.57f), c("#FFE3B0"), max(1f, k * .018f))
        val led = q4(-.32f, .42f, 2.43f, 2.57f)
        glow(quadPt(led, .5f, .5f).x, quadPt(led, .5f, .5f).y, k * .55f, c("#FF9A2E", .35f), BlendMode.Screen)
        ledText("Avrillé", led, c("#FFA62E"), max(1f, k * .022f))
        ledText("1004", q4(-.17f, .17f, 2.8f, 2.96f), c("#F4F1F4"), max(1f, k * .026f))
    }
    // Pointe noire sous le pare-brise et logo
    run {
        val a = cam.front(-.14f, wsb(-.14f)); val b = cam.front(.14f, wsb(.14f)); val cc = cam.front(0f, .92f)
        fill(poly(a.x, a.y, b.x, b.y, cc.x, cc.y), c("#16141C"))
        val o = cam.front(0f, 1.02f); val k = cam.k(0f, 0f)
        drawCircle(c("#EDE7EE"), radius = k * .045f, center = o, style = Stroke(max(1f, k * .012f)))
    }
    // Essuie-glace
    run { val a = cam.front(.05f, 1.2f); val b = cam.front(-.35f, 2.15f); line(a.x, a.y, b.x, b.y, max(1f, cam.k(0f, 0f) * .02f), c("#0B0A0F")) }
    // Pli du bouclier et feux ronds bas (blancs allumés + feu rouge)
    run {
        val path = Path(); for (i in 0..24) { val q = -.98f + 1.96f * i / 24; val o = cam.front(q, .52f); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        drawPath(path, c("#FFFFFF", .5f), style = Stroke(max(1f, S / 500f)))
        val k = cam.k(0f, 0f)
        for (sq in listOf(-1f, 1f)) {
            for ((q, col) in listOf(.36f to c("#FFF8E8"), .5f to c("#FFF8E8"), .63f to c("#E2322C"))) {
                if (sq > 0f && q > cam.qSil(.78f) - .04f) continue
                val o = cam.front(sq * q, .78f); val rr = k * (if (col.red > .95f && col.green > .9f) .055f else .045f)
                disc(o.x, o.y, rr * 1.35f, c("#3A3442")); disc(o.x, o.y, rr, col)
                glow(o.x, o.y, rr * (if (col.green > .9f) 9f else 4f), if (col.green > .9f) c("#FFF0D0", .5f) else c("#FF4030", .35f), BlendMode.Screen)
            }
        }
        // Halo des phares au sol, devant la rame
        val g = cam.p(-2.5f, 0f, 0f); drawOval(Brush.radialGradient(0f to c("#FFE9C8", .35f), 1f to c("#FFE9C8", 0f), center = g, radius = k * 1.6f), topLeft = Offset(g.x - k * 1.6f, g.y - k * .35f), size = Size(k * 3.2f, k * .7f), blendMode = BlendMode.Screen)
    }

    // --- Quai (béton clair, bande d'éveil de vigilance, bordure)
    val qa0 = -6.2f; val qa1 = 40f
    fill(cam.poly3(qa0, -1.5f, 0f, qa1, -1.5f, 0f, qa1, -1.5f, .3f, qa0, -1.5f, .3f), c("#6E6170"))
    val qTop = cam.p(qa1, -4f, .3f)
    fill(cam.ground(qa0, qa1, -6.2f, -1.5f, .3f), vg(qTop.y, H, 0f to c("#D8B9B2"), .3f to c("#BCA7AD"), 1f to c("#9F919F")))
    fill(cam.ground(qa0, qa1, -1.8f, -1.5f, .3f), vg(qTop.y, H, 0f to c("#F2D8CC"), 1f to c("#D9CDD2")))
    fill(cam.ground(qa0, qa1, -2.4f, -1.95f, .3f), c("#403C48"))
    // Dalles légèrement nuancées
    run {
        val r = Mul(91); var a = qa0 + .5f
        while (a < 22f) { var l = -6.2f; while (l < -2.4f) { val l1 = min(-2.4f, l + 1.3f); fill(cam.ground(a, a + 1.2f, l, l1, .3f), if (r() < .5f) c("#FFFFFF", r() * .07f) else c("#5A4A66", r() * .07f)); l = l1 }; a += 1.2f }
    }
    // Ombres longues des mâts (soleil bas au bout de la rue)
    fun poleShadow(a: Float, l: Float, hh: Float, w: Float) {
        val len = min(hh * 5.8f, a + 5f); val o0 = cam.p(a, l, .3f); val o1 = cam.p(a - len, l + len * .062f, .3f)
        val k0 = cam.k(a, l) * w; val k1 = cam.k(a - len, l) * w
        fill(poly(o0.x - k0, o0.y, o0.x + k0, o0.y, o1.x + k1, o1.y, o1.x - k1, o1.y), lg(o0.x, o0.y, o1.x, o1.y, 0f to c("#3D2F4D", .3f), 1f to c("#3D2F4D", .12f)))
    }
    poleShadow(4f, -5.6f, 7f, .09f); poleShadow(11f, -5.4f, 4f, .06f); poleShadow(9f, -8.2f, 4f, .2f)
    // Joints des dalles
    run {
        val path = Path()
        var a = qa0 + .5f; while (a < qa1) { val o0 = cam.p(a, -6.2f, .3f); val o1 = cam.p(a, -2.4f, .3f); path.moveTo(o0.x, o0.y); path.lineTo(o1.x, o1.y); a += 1.2f }
        for (l in listOf(-3.6f, -4.9f)) { val o0 = cam.p(qa0, l, .3f); val o1 = cam.p(qa1, l, .3f); path.moveTo(o0.x, o0.y); path.lineTo(o1.x, o1.y) }
        drawPath(path, c("#7C6E7C", .45f), style = Stroke(max(1f, S / 420f)))
    }
    // Plots podotactiles (taille selon la distance)
    run {
        var a = qa0 + .05f
        while (a < qa1) {
            val k = cam.k(a, -2.2f); val step = .085f
            if (k * step < 2.2f) break
            val pts = ArrayList<Offset>(); val a1 = a + 1f
            while (a < a1) { for (j in 0 until 5) pts.add(cam.p(a, -2.35f + .09f * j + .045f, .3f)); a += step }
            drawPoints(pts, PointMode.Points, c("#E8E2E6"), strokeWidth = k * .035f, cap = StrokeCap.Round)
        }
    }
    // Granulats du béton (premier plan)
    run {
        val r = Mul(88); val pts = ArrayList<Offset>()
        for (i in 0 until 900) { val a = qa0 + 12f * r().pow(2f); val l = -6.2f + r() * 3.8f; if (l > -2.4f) continue; pts.add(cam.p(a, l, .3f)) }
        drawPoints(pts, PointMode.Points, c("#FFFFFF", .35f), strokeWidth = max(1f, S / 360f), cap = StrokeCap.Round)
    }
    lamp(4f, -5.6f, 7f)
    // Poteau d'arrêt « Ⓣ » sur le quai
    run {
        val a = 11f; val l = -5.4f; val k = cam.k(a, l); val b = cam.p(a, l, .3f); val tp = cam.p(a, l, 3.6f)
        line(b.x, b.y, tp.x, tp.y, max(1.5f, k * .1f), c("#3A3642"), StrokeCap.Butt)
        val ring = cam.p(a, l, 3.95f)
        drawCircle(c("#F1EAF0"), radius = k * .32f, center = ring, style = Stroke(k * .07f))
        line(ring.x - k * .16f, ring.y - k * .12f, ring.x + k * .16f, ring.y - k * .12f, k * .07f, c("#F1EAF0"), StrokeCap.Butt)
        line(ring.x, ring.y - k * .12f, ring.x, ring.y + k * .2f, k * .07f, c("#F1EAF0"), StrokeCap.Butt)
    }
    // Arbre en contre-jour à gauche (cadrage)
    tree(9f, -8.2f, 401, 10f)
    // Brume chaude de l'heure dorée dans l'axe
    mist(hy - H * .01f, H * .06f, c("#FFD2A8", .3f))
}
