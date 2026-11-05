private fun DrawScope.sakura(t: Float, lighting: Tod? = null) {
    fun c(h: String, a: Float = 1f) = surfaceColor(h, a, lighting)
    val W = size.width; val H = size.height; val S = min(W, H)
    if (lighting == null) {
    sakuraSky(0f to c("#F9D6E3"), .35f to c("#FFE6EF"), .6f to c("#FFF1F2"), 1f to c("#F6C9D8"))
    glow(W * .5f, H * .4f, S * .9f, c("#FFFFFF", .5f), BlendMode.Screen)
    } else sakuraLiveSky(lighting, t, H * .62f)
    // Collines et pagode dans la brume
    val n1 = Nz(3); ridgePts(H * .62f, H * .08f, 80) { u -> n1.at(u * 1.5f, 3) * .5f + .5f }.let { ridgeFill(it, c("#E6BCD0")) }
    mist(H * .6f, H * .06f, c("#FFF0F4", .7f))
    run {
        val px = W * .3f; val py = H * .63f; val u = S * .044f; val col = c("#B88AA6")
        for (i in 0 until 5) { val w = u * (3.2f - i * .45f); val y = py - i * u * 1.6f
            drawRect(lg(px-w*.35f,y,px+w*.35f,y,0f to mix(col,c("#F2D6CB"),.25f),1f to mix(col,c("#634D73"),.4f)),Offset(px-w*.35f,y-u*1.1f),Size(w*.7f,u*1.1f))
            for(j in -1..1)rect(px+j*w*.16f-w*.035f,y-u*.9f,w*.07f,u*.35f,if((lighting?.night?:0f)>.3f)Color(0xFFFFD5A0) else c("#6C4E66"))
            fill(poly(px - w * .75f, y - u * 1.1f, px + w * .75f, y - u * 1.1f, px + w * .45f, y - u * 1.6f, px - w * .45f, y - u * 1.6f), mix(col, c("#7E5A78"), .3f)) }
        line(px, py - 5 * u * 1.6f, px, py - 5 * u * 1.6f - u * 2.5f, max(1f, S * .003f), col)
    }
    val n2 = Nz(4); ridgePts(H * .7f, H * .06f, 80) { u -> n2.at(u * 2f + 3f, 3) * .5f + .5f }.let { ridgeFill(it, c("#D7A3BC")) }
    mist(H * .7f, H * .05f, c("#FFF0F4", .6f))
    // Étang : reflets doux
    drawRect(vg(H * .72f, H, 0f to c("#EBC4D6"), 1f to c("#C995B4")), topLeft = Offset(0f, H * .72f), size = Size(W, H * .28f))
    for (i in 0 until 12) { val y = H * (.74f + i * .022f); val x = W * (.5f + .3f * sin(i * 1.7f + t * TAU)); line(x - S * .15f, y, x + S * .15f, y, max(1f, S * .003f), c("#FFFFFF", .25f)) }
    // Curved stone bridge: vaulted opening, thickness, parapet and masonry courses.
    val bridgeY=H*.865f;val x0=W*.035f;val x1=W*.8f;val span=x1-x0
    val bridge=Path().apply {
        fillType=androidx.compose.ui.graphics.PathFillType.EvenOdd
        moveTo(x0,bridgeY);cubicTo(x0+span*.25f,bridgeY-H*.1f,x1-span*.25f,bridgeY-H*.1f,x1,bridgeY)
        lineTo(x1,bridgeY+H*.035f);lineTo(x0,bridgeY+H*.035f);close()
        moveTo(x0+span*.15f,bridgeY+H*.04f)
        cubicTo(x0+span*.3f,bridgeY-H*.055f,x1-span*.3f,bridgeY-H*.055f,x1-span*.15f,bridgeY+H*.04f);close()
    }
    ellGlow(W*.42f,bridgeY+H*.035f,span*.5f,H*.025f,c("#674C77",.24f))
    drawPath(bridge,vg(bridgeY-H*.09f,bridgeY+H*.04f,0f to c("#D9B3B7"),1f to c("#8C718E")))
    clipPath(bridge){for(i in 0..9){val x=x0+span*i/9f;line(x,bridgeY-H*.1f,x,bridgeY+H*.04f,S*.002f,c("#735D79",.4f))}
        line(x0,bridgeY-H*.015f,x1,bridgeY-H*.015f,S*.002f,c("#F1D2C5",.5f))}
    val rail=Path().apply{moveTo(x0,bridgeY-H*.035f);cubicTo(x0+span*.25f,bridgeY-H*.12f,x1-span*.25f,bridgeY-H*.12f,x1,bridgeY-H*.035f)}
    drawPath(rail,c("#D9B6BB"),style=Stroke(S*.008f))
    for(i in 0..8){val f=i/8f;val x=x0+span*f;val y=bridgeY-H*.09f*4*f*(1-f)
        line(x,y,x,y-H*.035f,S*.006f,c("#AD8C9F"))}
    for(x in listOf(x0+span*.13f,x1-span*.13f)){
        val y=bridgeY-H*.055f
        line(x,y,x,y-H*.05f,S*.004f,c("#62546B"))
        rect(x-S*.012f,y-H*.06f,S*.024f,H*.022f,c("#8E6579"))
        if((lighting?.night?:0f)>.1f)glow(x,y-H*.05f,S*.09f,Color(0xFFFFD7A2).copy(alpha=(lighting?.night?:0f)*.5f),BlendMode.Screen)
        rect(x-S*.007f,y-H*.057f,S*.014f,H*.015f,if((lighting?.night?:0f)>.3f)Color(0xFFFFD7A2)else c("#D9AD99"))
    }
    // Reeds and an irregular near shore break the previously empty pink water plane.
    val bank=Path().apply{moveTo(0f,H);lineTo(0f,H*.94f);cubicTo(W*.25f,H*.9f,W*.6f,H*.99f,W,H*.945f);lineTo(W,H);close()}
    drawPath(bank,vg(H*.92f,H,0f to c("#987A8C"),1f to c("#574D6C")))
    // Branches fleuries (coins hauts)
    fun blossomBranch(x: Float, y: Float, ang: Float, seed: Int, len: Float) {
        val p = Path(); val tips = ArrayList<Offset>(); val r = Mul(seed)
        branches(p, x, y, len, ang, S * .04f, 6, r, tips, 24f, 4f)
        drawPath(p, c("#4A2A34"))
        val fl = Mul(seed + 1)
        for (o in tips) { glow(o.x, o.y, S * .07f, c("#FFC9DC", .5f)); for (j in 0 until 5) {
            val fx = o.x + (fl() - .5f) * S * .08f; val fy = o.y + (fl() - .5f) * S * .06f; val fs = S * (.008f + fl() * .009f)
            for (k in 0 until 5) { val a = k / 5f * TAU + fl(); leaf(fx + cos(a) * fs * .5f, fy + sin(a) * fs * .5f, fs, a * 360f / TAU, if (fl() < .5f) c("#FFB7CF", .95f) else c("#FFE6EE", .95f), .6f) }
            disc(fx, fy, fs * .2f, c("#D9467A"))
        } }
    }
    blossomBranch(-W * .05f, H * .12f, -18f, 7, S * .2f)
    blossomBranch(W * 1.05f, H * .06f, 200f, 9, S * .17f)
    // Bokeh et pétales qui tombent
    val b = Mul(12)
    for (i in 0 until 10) glow(b() * W, b() * H * .6f, S * (.03f + b() * .05f), c("#FFFFFF", .25f), BlendMode.Screen)
    for (i in 0 until 26) {
        val sp = .5f + b() * .6f; val u = (b() + t * sp) % 1f; val x = b() * W + sin(u * TAU * 2 + i) * S * .06f + u * W * .15f; val y = -H * .05f + u * H * 1.1f
        val s = S * (.012f + b() * .01f)
        leaf(x, y, s, u * 540f + i * 30f, c("#FFB3CC", .9f), .55f)
    }
}
