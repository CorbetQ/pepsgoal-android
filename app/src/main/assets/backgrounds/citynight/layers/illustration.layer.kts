private fun DrawScope.citynight(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val hz = H * .72f
    citynightSky(0f to c("#060A1F"), .3f to c("#0E1840"), .55f to c("#22306A"), .66f to c("#4A3576"), .72f to c("#7A4A7E"), y1 = hz)
    starField(51, 160, hz * .6f, c("#DDE6FF", .7f), t, 2)
    val mx = W * .74f; val my = H * .17f; val mr = S * .075f
    glow(mx, my, S * .7f, c("#9DB4FF", .18f), BlendMode.Screen); glow(mx, my, S * .22f, c("#E9EEFF", .3f), BlendMode.Screen)
    disc(mx, my, mr, c("#F3EEDD"))
    for ((dx, dy, rr) in listOf(Triple(-.3f, -.2f, .28f), Triple(.25f, .15f, .36f), Triple(-.1f, .4f, .2f), Triple(.35f, -.35f, .16f))) glow(mx + dx * mr, my + dy * mr, rr * mr, c("#C9BFA8", .5f))
    drawCircle(Brush.radialGradient(0f to Color.Transparent, .7f to Color.Transparent, 1f to c("#B9AE98", .5f), center = Offset(mx - mr * .2f, my - mr * .2f), radius = mr * 1.25f), radius = mr, center = Offset(mx, my))
    // Nuages fins éclairés par la lune
    val dr = t * W * .06f
    for (k in 0 until 3) { val x = (W * (.2f + k * .35f) + dr) % (W * 1.3f) - W * .15f; ellGlow(x, H * (.22f + k * .07f), W * .3f, H * .012f, c("#5C6AA8", .35f)); ellGlow(x, H * (.22f + k * .07f) - H * .004f, W * .22f, H * .006f, c("#C9D2FF", .18f), BlendMode.Screen) }
    // Halo de la ville sur l'horizon
    ellGlow(W * .5f, hz, W * .9f, H * .08f, c("#FF9C7A", .25f), BlendMode.Screen)
    // Trois plans d'immeubles
    fun skyline(seed: Int, base: Float, hMin: Float, hMax: Float, col: Color, winA: Float, near: Boolean) {
        val r = Mul(seed); var x = -S * .05f
        val wins = ArrayList<Offset>(); val wins2 = ArrayList<Offset>(); val reds = ArrayList<Offset>()
        while (x < W) {
            val w = S * (.06f + r() * .1f) * (if (near) 1.4f else 1f); val h = H * (hMin + r() * (hMax - hMin)); val top = base - h
            drawRect(vg(top, base, 0f to mix(col, c("#3A3A7A"), .25f), 1f to col), topLeft = Offset(x, top), size = Size(w + 1f, h))
            if(near){
                fill(poly(x+w*.83f,top,x+w,top+w*.1f,x+w,base,x+w*.83f,base),c("#090F27",.6f))
                line(x+w*.82f,top,x+w*.82f,base,S*.0015f,c("#6D79AC",.45f))
                rect(x+w*.2f,top-S*.01f,w*.32f,S*.01f,c("#5B658D",.5f))
            }
            // Couronnements : flèche, gradins ou antenne
            when ((r() * 4).toInt()) {
                0 -> { rect(x + w * .2f, top - h * .05f, w * .6f, h * .05f + 1f, col); rect(x + w * .35f, top - h * .09f, w * .3f, h * .04f + 1f, col) }
                1 -> { line(x + w * .5f, top, x + w * .5f, top - h * .18f, max(1f, S * .003f), col); reds.add(Offset(x + w * .5f, top - h * .18f)) }
                2 -> fill(poly(x, top, x + w * .5f, top - w * .5f, x + w, top), col)
                else -> {}
            }
            // Fenêtres
            val cw = S * (if (near) .014f else .009f); val chh = cw * 1.5f; var wy = top + chh
            while (wy < base - chh) { var wx = x + cw * .8f; while (wx < x + w - cw * 1.5f) { val v = r(); if (v < winA) wins.add(Offset(wx, wy)) else if (v < winA * 1.4f) wins2.add(Offset(wx, wy)); wx += cw * 1.8f }; wy += chh * 1.6f }
            x += w + S * .005f
        }
        val ws = S * (if (near) .009f else .006f)
        drawPoints(wins, PointMode.Points, c("#FFD08A", if (near) .9f else .6f), strokeWidth = ws, cap = StrokeCap.Square)
        drawPoints(wins2, PointMode.Points, c("#9FD0FF", if (near) .6f else .4f), strokeWidth = ws, cap = StrokeCap.Square)
        for ((i, o) in reds.withIndex()) { val on = .5f + .5f * sin(t * TAU * 6 + i); glow(o.x, o.y, S * .02f, c("#FF3B3B", .6f * on), BlendMode.Screen); disc(o.x, o.y, S * .003f, c("#FF6060")) }
    }
    skyline(7, hz, .06f, .16f, c("#3A3270"), .25f, false)
    mist(hz - H * .01f, H * .04f, c("#B0709A", .35f))
    skyline(8, hz + H * .01f, .09f, .26f, c("#211F4C"), .3f, false)
    skyline(9, hz + H * .025f, .05f, .2f, c("#12112E"), .35f, true)
    // Quai et fleuve
    val q = hz + H * .03f
    rect(0f, q - H * .006f, W, H * .01f, c("#0B0A1E"))
    val lamps = ArrayList<Offset>(); for (i in 0..14) lamps.add(Offset(W * (i + .5f) / 15f, q - H * .012f))
    for (o in lamps) { glow(o.x, o.y, S * .035f, c("#FFC27A", .45f), BlendMode.Screen); disc(o.x, o.y, S * .004f, c("#FFE7BF")) }
    drawRect(vg(q, H, 0f to c("#1A1A46"), .4f to c("#0C0E2A"), 1f to c("#05061A")), topLeft = Offset(0f, q), size = Size(W, H - q))
    // Reflets : traits horizontaux scintillants sous les lumières
    val r = Mul(77)
    for (i in 0 until 160) {
        val x = r() * W; val y = q + (H - q) * r().pow(1.6f) * .9f + H * .005f; val wl = S * (.01f + r() * .05f) * (1f + (y - q) / H * 3f)
        val col = if (r() < .7f) c("#FFC27A") else c("#9FD0FF"); val a = (.15f + .35f * (.5f + .5f * sin(t * TAU * 3 + i * 1.7f))) * (1f - (y - q) / (H - q) * .7f)
        line(x - wl / 2, y, x + wl / 2, y, max(1f, S * .003f), col.a(a))
    }
    // Reflet de la lune
    for (i in 0 until 14) { val y = q + (H - q) * (.05f + i * .06f); val wl = S * (.04f + i * .012f) * (.8f + .2f * sin(t * TAU * 2 + i)); line(mx - wl, y, mx + wl, y, S * .004f, c("#F3EEDD", .35f - i * .02f)) }    // Nearest embankment: paving, railing and pools of reflected lamplight.
    fill(poly(0f,H*.955f,W,H*.93f,W,H,0f,H),vg(H*.93f,H,0f to c("#3E4564"),1f to c("#121A30")))
    line(0f,H*.955f,W,H*.93f,S*.005f,c("#8994AD",.65f))
    for(i in 0..6){val x=W*i/6f;val y=H*(.955f-.025f*i/6)
        line(x,y,x,y-H*.045f,S*.003f,c("#17223B"))}
    line(0f,H*.91f,W,H*.885f,S*.003f,c("#6B7998",.7f))
    for(x in listOf(W*.12f,W*.88f)){val y=H*(.955f-.025f*x/W)
        line(x,y,x,y-H*.095f,S*.005f,c("#1E2840"))
        disc(x,y-H*.096f,S*.006f,c("#FFE7B1"));glow(x,y-H*.096f,S*.07f,c("#FFD09A",.35f),BlendMode.Screen)
        ellGlow(x,y,S*.11f,H*.014f,c("#C5AC89",.19f),BlendMode.Screen)
    }

}
