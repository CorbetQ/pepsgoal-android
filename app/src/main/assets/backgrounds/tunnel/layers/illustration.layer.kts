private fun DrawScope.tunnel(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H)
    val cam = Cam(0f, -.25f, 1.35f, 0f, W * 1.12f, W * .6f, H * .38f)
    val vp = Offset(W * .6f, H * .38f); val R = max(W, H) * 1.1f
    // Fond : nuit bleutée, lueur au bout du tunnel
    drawRect(c("#0B0F17"))
    // Faces du tunnel : dégradé radial centré sur le point de fuite (sombre au loin, éclairé près de nous)
    fun face(i: Int, near: Color, far: Color) {
        val l0 = TUN[i * 2]; val h0 = TUN[i * 2 + 1]; val j = (i + 1) % (TUN.size / 2); val l1 = TUN[j * 2]; val h1 = TUN[j * 2 + 1]
        fill(cam.poly3(.35f, l0, h0, .35f, l1, h1, 500f, l1, h1, 500f, l0, h0), Brush.radialGradient(0f to c("#9FD8FF", .9f), .025f to far, .3f to mix(far, near, .45f), 1f to near, center = vp, radius = R))
    }
    val n = TUN.size / 2
    for (i in 0 until n) {
        val l0 = TUN[i * 2]; val h0 = TUN[i * 2 + 1]; val j = (i + 1) % n; val l1 = TUN[j * 2]; val h1 = TUN[j * 2 + 1]
        when {
            h0 == 0f && h1 == 0f -> face(i, c("#3A4250"), c("#0E121A"))                      // sol
            h0 >= 3.3f && h1 >= 3.3f -> face(i, c("#2A3242"), c("#0C1018"))                 // voûte
            abs(h0 - .9f) < .01f && abs(h1 - .9f) < .01f -> face(i, c("#56606E"), c("#121720")) // banquette
            else -> face(i, c("#3B4556"), c("#0F141D"))                                       // parois
        }
    }
    // Lueur au bout du tunnel
    glow(vp.x, vp.y, S * .5f, c("#5FA8FF", .35f), BlendMode.Screen); glow(vp.x, vp.y, S * .1f, c("#D6EEFF", .9f), BlendMode.Screen)
    // Bandes LED continues le long des parois (fuyantes)
    fun strip(l: Float, h: Float, col: Color, w: Float) {
        val a = cam.p(.6f, l, h); val b = cam.p(400f, l, h)
        drawLine(Brush.linearGradient(0f to col, .6f to col.a(col.alpha * .5f), 1f to col.a(0f), start = a, end = b), a, b, strokeWidth = w)
    }
    strip(-3.58f, 2.55f, c("#7FD3FF", .55f), S * .012f); strip(2.58f, 2.55f, c("#7FD3FF", .45f), S * .01f)
    strip(-3.58f, .55f, c("#FFB04A", .35f), S * .008f); strip(2.58f, 1.35f, c("#5BE38A", .3f), S * .006f)
    // Chemins de câbles sur la paroi gauche
    for (k in 0 until 3) strip(-3.55f, 1.6f + k * .18f, c("#1A2130", .9f), S * .01f)
    // Anneaux du tunnel et luminaires qui défilent vers nous (le métro file)
    val run = t * 162f
    for (k in 0 until 28) {
        val a = 6f * k + 6f - (run % 6f); if (a < .8f) continue
        val fade = sm(140f, 40f, a)
        val pr = Path(); for (i in 0 until n) { val o = cam.p(a, TUN[i * 2], TUN[i * 2 + 1]); if (i == 0) pr.moveTo(o.x, o.y) else pr.lineTo(o.x, o.y) }
        drawPath(pr, c("#05070B", .55f * fade), style = Stroke(max(1f, cam.k(a, 0f) * .05f)))
        // Luminaires : voûte et paroi gauche, halo sur la paroi
        val lamp = c("#FFF1D6")
        fill(cam.poly3(a, -.85f, 4.54f, a + 1.2f, -.85f, 4.54f, a + 1.2f, -.15f, 4.54f, a, -.15f, 4.54f), lamp.a(.92f * fade))
        val lc = cam.p(a + .7f, -.5f, 4.4f); glow(lc.x, lc.y, cam.k(a, 0f) * 1.6f, c("#FFE3B0", .28f * fade), BlendMode.Screen)
        fill(cam.poly3(a, -3.58f, 3.0f, a + 1.1f, -3.58f, 3.0f, a + 1.1f, -3.58f, 3.12f, a, -3.58f, 3.12f), c("#BFE6FF", .9f * fade))
        val wl = cam.p(a + .55f, -3.5f, 3.0f); glow(wl.x, wl.y, cam.k(a, 0f) * 1.2f, c("#9FD8FF", .22f * fade), BlendMode.Screen)
        // Traînées de vitesse derrière les luminaires de paroi
        val t0 = cam.p(a + 1.1f, -3.57f, 3.06f); val t1 = cam.p(a + 5f, -3.57f, 3.06f)
        drawLine(lg(t0.x, t0.y, t1.x, t1.y, 0f to c("#BFE6FF", .5f * fade), 1f to c("#BFE6FF", 0f)), t0, t1, strokeWidth = max(1f, cam.k(a, 0f) * .08f))
        // Balise verte de l'issue de secours, un anneau sur trois
        if (k % 3 == 0) { val g = cam.p(a, 2.58f, 1.8f); disc(g.x, g.y, cam.k(a, 0f) * .06f, c("#5BE38A", fade)); glow(g.x, g.y, cam.k(a, 0f) * .4f, c("#5BE38A", .3f * fade), BlendMode.Screen) }
    }
    // Garde-corps de la banquette
    strip(1.25f, 1.9f, c("#8A94A3", .7f), S * .006f)
    for (k in 0 until 40) { val a = 3f * k + 3f - (run % 3f); if (a < 1f) continue; val p0 = cam.p(a, 1.25f, .9f); val p1 = cam.p(a, 1.25f, 1.9f); line(p0.x, p0.y, p1.x, p1.y, max(1f, cam.k(a, 0f) * .04f), c("#7C8696", .8f * sm(120f, 30f, a))) }

    // Voie du VAL : pistes de roulement en béton et barres de guidage latérales
    val tl = -1.55f
    for (s in listOf(-1f, 1f)) {
        fill(cam.ground(.5f, 500f, tl + s * .55f, tl + s * .98f, .02f), Brush.radialGradient(0f to c("#151920"), .3f to c("#3A414C"), 1f to c("#555D6A"), center = vp, radius = R))
        strip(tl + s * .55f, .025f, c("#8A94A3", .35f), S * .004f); strip(tl + s * .98f, .025f, c("#8A94A3", .35f), S * .004f)
        strip(tl + s * 1.22f, .32f, c("#B8C2CF", .9f), S * .01f); strip(tl + s * 1.22f, .26f, c("#2A303A"), S * .012f)
    }
    // Supports des barres de guidage (défilent)
    for (k in 0 until 90) {
        val a = 1.5f * k + 1.5f - (run % 1.5f); if (a < 1.6f) continue
        val f0 = sm(90f, 20f, a)
        for (s in listOf(-1f, 1f)) { val o = cam.p(a, tl + s * 1.22f, .29f); val kk = cam.k(a, 0f); rect(o.x - kk * .04f, o.y - kk * .03f, kk * .08f, kk * .1f, c("#11151C", .85f * f0)) }
        val m0 = cam.p(a, tl - .4f, 0f); val m1 = cam.p(a, tl + .4f, 0f); line(m0.x, m0.y, m1.x, m1.y, max(1f, cam.k(a, 0f) * .03f), c("#0A0D12", .5f * f0))
    }

    // Faisceaux des phares sur la voie, devant la rame
    val A0 = 5.6f
    for (s in listOf(-.66f, .66f)) {
        val p0 = cam.p(A0, tl + s, 0f); val p1 = cam.p(2.2f, tl + s * 1.6f, 0f)
        fill(cam.poly3(A0 - .05f, tl + s - .12f, .03f, A0 - .05f, tl + s + .12f, .03f, 2.2f, tl + s * 1.6f + .7f, .03f, 2.2f, tl + s * 1.6f - .7f, .03f),
            lg(p0.x, p0.y, p1.x, p1.y, 0f to c("#FFE6B0", .32f), .45f to c("#FFE6B0", .1f), 1f to c("#FFE6B0", 0f)))
        ellGlow(p0.x, p0.y + cam.k(A0, 0f) * .25f, cam.k(A0, 0f) * 1.1f, cam.k(A0, 0f) * .35f, c("#FFE2A0", .4f), BlendMode.Screen)
    }

    /* --- La rame (VAL 208 de Rennes) : flanc droit puis face avant --- */
    val hw = 1.04f; val sl = tl + hw; val L = 26f
    fun side(a0: Float, a1: Float, h0: Float, h1: Float) = cam.poly3(a0, sl, h0, a1, sl, h0, a1, sl, h1, a0, sl, h1)
    // Caisse argentée, plus claire en haut (lumière des luminaires)
    fill(side(A0 + .1f, A0 + L, .34f, 3.22f), Brush.verticalGradient(0f to c("#E9EDF2"), .55f to c("#C3C9D2"), 1f to c("#7E8592"), startY = cam.p(A0, sl, 3.22f).y, endY = cam.p(A0, sl, .34f).y))
    // Bande des baies : verre fumé, intérieur chaud, plafonnier
    for (car in 0 until 2) {
        val c0 = A0 + car * 13f
        val segs = listOf(.5f to 2.6f, 4.3f to 7.9f, 9.6f to 12.4f)
        for ((u0, u1) in segs) {
            fill(side(c0 + u0, c0 + u1, 1.32f, 2.6f), Brush.verticalGradient(0f to c("#FFE2A0", .9f), .18f to c("#3A3426"), 1f to c("#141820"), startY = cam.p(c0 + u0, sl, 2.6f).y, endY = cam.p(c0 + u0, sl, 1.32f).y))
            fill(side(c0 + u0, c0 + u1, 1.32f, 1.62f), c("#E8C14A", .55f)) // dossiers jaunes
        }
        // Portes louvoyantes : cadre sombre et vitrage haut
        for (d in listOf(2.75f, 8.05f)) {
            fill(side(c0 + d, c0 + d + 1.4f, .5f, 2.75f), c("#B5BCC6")); fill(side(c0 + d + .08f, c0 + d + 1.32f, 1.25f, 2.62f), c("#1B2029"))
            val q0 = cam.p(c0 + d + .7f, sl, .5f); val q1 = cam.p(c0 + d + .7f, sl, 2.75f); line(q0.x, q0.y, q1.x, q1.y, max(1f, cam.k(c0 + d, sl) * .025f), c("#5A616C"))
        }
        // Soufflet d'intercirculation
        if (car == 0) fill(side(c0 + 12.85f, c0 + 13.15f, .5f, 3.0f), c("#20242C"))
    }
    // Filet sombre sous le pavillon, jupe et roues à pneus
    fill(side(A0 + .1f, A0 + L, 2.86f, 2.94f), c("#2A2F38", .9f))
    fill(side(A0 + .1f, A0 + L, .34f, .7f), c("#5D6470"))
    for (b in listOf(1.6f, 11.4f, 14.6f, 24.4f)) fill(side(A0 + b - .5f, A0 + b + .5f, .1f, .55f), c("#0C0F14"))
    // Reflets des luminaires qui glissent sur le flanc
    for (k in 0 until 6) { val a = A0 + 6f * k + 6f - (run % 6f); if (a > A0 + L - 1f) continue
        fill(side(a, a + 1.6f, 2.95f, 3.2f), c("#FFFFFF", .35f)); fill(side(a + .2f, a + 1.1f, .72f, .9f), c("#FFFFFF", .12f)) }

    // Face avant (plan a = A0)
    fun F(u: Float, h: Float) = cam.p(A0, tl + u, h)
    val kf = cam.k(A0, tl)
    fun rr(u0: Float, h0: Float, u1: Float, h1: Float, r: Float, b: Brush) { val a = F(u0, h1); val z = F(u1, h0); drawRoundRect(b, Offset(a.x, a.y), Size(z.x - a.x, z.y - a.y), CornerRadius(r * kf)) }
    fun rr(u0: Float, h0: Float, u1: Float, h1: Float, r: Float, col: Color) = rr(u0, h0, u1, h1, r, androidx.compose.ui.graphics.SolidColor(col))
    val top = F(0f, 3.3f).y; val bot = F(0f, .3f).y
    // Ombre portée et caisse
    glow(F(0f, .3f).x, bot, kf * 1.6f, c("#000000", .55f))
    rr(-hw, .3f, hw, 3.3f, .28f, Brush.verticalGradient(0f to c("#F4F6F9"), .5f to c("#D5DAE1"), 1f to c("#9AA1AC"), startY = top, endY = bot))
    rr(-hw, 3.0f, hw, 3.3f, .28f, Brush.verticalGradient(0f to c("#FFFFFF", .7f), 1f to c("#FFFFFF", 0f), startY = top, endY = F(0f, 3.0f).y))
    // Masque noir : girouette, pare-brise, phares
    rr(-.88f, .8f, .88f, 3.12f, .2f, Brush.verticalGradient(0f to c("#1B1E25"), 1f to c("#0A0B0F"), startY = F(0f, 3.12f).y, endY = F(0f, .8f).y))
    // Girouette : numéro de la rame
    val g0 = F(-.3f, 3.06f); val g1 = F(.3f, 2.8f)
    ledText("64", floatArrayOf(g0.x, g0.y, g1.x, g0.y, g0.x, g1.y, g1.x, g1.y), c("#FFFFFF"), max(1.2f, kf * .045f))
    // Pare-brise : intérieur éclairé (sièges jaunes, plafonnier), verre fumé, reflet
    rr(-.76f, 1.28f, .76f, 2.7f, .1f, Brush.verticalGradient(0f to c("#FFE9B8"), .35f to c("#D9B76A"), 1f to c("#6B5A32"), startY = F(0f, 2.7f).y, endY = F(0f, 1.28f).y))
    // Intérieur en perspective : plafond, parois, sol, sièges jaunes, porte du fond (rame sans conducteur)
    fun fp(vararg q: Float): Path { val pts = FloatArray(q.size); var i = 0; while (i < q.size) { val o = F(q[i], q[i + 1]); pts[i] = o.x; pts[i + 1] = o.y; i += 2 }; return poly(*pts) }
    fill(fp(-.76f, 2.7f, .76f, 2.7f, .22f, 2.2f, -.22f, 2.2f), c("#F6E3B4"))
    fill(fp(-.12f, 2.7f, .12f, 2.7f, .04f, 2.2f, -.04f, 2.2f), c("#FFFBEF"))
    fill(fp(-.76f, 2.7f, -.22f, 2.2f, -.22f, 1.76f, -.76f, 1.28f), c("#C9A45E")); fill(fp(.76f, 2.7f, .22f, 2.2f, .22f, 1.76f, .76f, 1.28f), c("#B8934F"))
    fill(fp(-.68f, 2.5f, -.3f, 2.14f, -.3f, 1.95f, -.68f, 2.15f), c("#2A3040")); fill(fp(.68f, 2.5f, .3f, 2.14f, .3f, 1.95f, .68f, 2.15f), c("#2A3040"))
    fill(fp(-.76f, 1.28f, .76f, 1.28f, .22f, 1.76f, -.22f, 1.76f), c("#5A4A2C"))
    for (sgn in listOf(-1f, 1f)) for ((u, h, w) in listOf(Triple(.62f, 1.35f, .2f), Triple(.44f, 1.55f, .12f), Triple(.33f, 1.66f, .07f)))
        rr(sgn * u - w / 2, h, sgn * u + w / 2, h + w * 2.6f, w * .25f, c("#F2C230"))
    rr(-.22f, 1.76f, .22f, 2.2f, .02f, c("#FFF4D6")); rr(-.08f, 1.78f, .08f, 2.12f, .02f, c("#3A3426", .6f))
    rr(-.76f, 1.28f, .76f, 2.7f, .1f, c("#0D1118", .38f))
    val w0 = F(-.76f, 2.7f); val w1 = F(.76f, 1.28f)
    clipRect(w0.x, w0.y, w1.x, w1.y) {
        fill(poly(w0.x + (w1.x - w0.x) * .55f, w0.y, w0.x + (w1.x - w0.x) * .75f, w0.y, w0.x + (w1.x - w0.x) * .35f, w1.y, w0.x + (w1.x - w0.x) * .15f, w1.y), c("#FFFFFF", .13f))
        fill(poly(w0.x + (w1.x - w0.x) * .8f, w0.y, w0.x + (w1.x - w0.x) * .86f, w0.y, w0.x + (w1.x - w0.x) * .46f, w1.y, w0.x + (w1.x - w0.x) * .4f, w1.y), c("#FFFFFF", .1f))
    }
    // Logo blanc sous le pare-brise
    rr(.3f, 1.12f, .62f, 1.19f, .03f, c("#FFFFFF", .85f))
    // Phares (lumière chaude), feux de position
    for (s in listOf(-1f, 1f)) {
        val o = F(s * .66f, 1.0f)
        glow(o.x, o.y, kf * 1.3f, c("#FFE2A0", .45f), BlendMode.Screen); glow(o.x, o.y, kf * .35f, c("#FFF6E0", .95f), BlendMode.Screen)
        disc(o.x, o.y, kf * .075f, c("#FFFBEE"))
    }
    // Jupe, attelage
    rr(-.3f, .42f, .3f, .74f, .08f, c("#1A1D23")); val cp = F(0f, .58f); disc(cp.x, cp.y, kf * .07f, c("#3A3F48")); disc(cp.x, cp.y, kf * .035f, c("#0A0B0E"))
    // Liseré lumineux du contour (luminaires derrière)
    drawRoundRect(c("#FFFFFF", .5f), F(-hw, 3.3f), Size(F(hw, .3f).x - F(-hw, 3.3f).x, bot - top), CornerRadius(.28f * kf), style = Stroke(max(1f, kf * .012f)))
    // Brume légère et lueur des phares dans l'air
    mist(H * .62f, H * .2f, c("#9FB7D6", .06f))
    for (s in listOf(-1f, 1f)) { val o = F(s * .66f, 1.0f); ellGlow(o.x, o.y + kf * .4f, kf * 1.8f, kf * .6f, c("#FFE2A0", .14f), BlendMode.Screen) }
}
