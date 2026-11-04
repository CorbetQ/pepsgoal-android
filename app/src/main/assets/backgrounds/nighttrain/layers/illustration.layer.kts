private fun DrawScope.nighttrain(t: Float) {
    val W = size.width; val H = size.height; val S = min(W, H); val u = S / 100f
    nighttrainSky(0f to c("#0A0F2A"), .6f to c("#1E2A5A"), 1f to c("#3A3A6A"))
    starField(91, 200, H * .55f, c("#FFFFFF", .8f), t, 4)
    val mx = W * .72f; val my = H * .18f; glow(mx, my, S * .6f, c("#FFE7B0", .2f), BlendMode.Screen); disc(mx, my, S * .07f, c("#FFF4D6")); disc(mx - S * .025f, my - S * .01f, S * .06f, c("#1E2A5A", .9f))
    val nz = Nz(13)
    ridgeFill(ridgePts(H * .6f, H * .08f, 60) { x -> .5f + .5f * nz.ridged(x * 3f, 3) }, c("#26305A"))
    // Viaduc : tablier et arches
    val deck = H * .56f; val dh = u * 3f
    rect(0f, deck, W, dh, c("#3E3A54"))
    val n = 6; val span = W / (n - 1.5f)
    val arches = Path().apply { fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd; addRect(androidx.compose.ui.geometry.Rect(0f, deck + dh, W, H * .86f))
        for (i in 0 until n) { val cx = i * span; val ar = span * .38f; addOval(androidx.compose.ui.geometry.Rect(cx - ar, deck + dh + span * .05f, cx + ar, deck + dh + span * .05f + ar * 2f)); addRect(androidx.compose.ui.geometry.Rect(cx - ar, deck + dh + span * .05f + ar, cx + ar, H * .86f)) } }
    fill(arches, vg(deck, H * .86f, 0f to c("#4A4664"), 1f to c("#1E1C30")))
    // Recessed arch soffits, voussoirs and stone courses give the viaduct a real section.
    clipPath(arches){
        for(row in 0..17){val y=deck+dh+(H*.86f-deck-dh)*row/18
            line(0f,y,W,y,max(.6f,u*.12f),c("#A59BB1",.17f))
            for(col in 0..13){val x=W*(col+(row%2)*.5f)/13;line(x,y,x,y+(H*.86f-deck)/18,max(.5f,u*.1f),c("#080F27",.28f))}}
        for(i in 0 until n){val cx=i*span;val ar=span*.38f;val centerY=deck+dh+span*.05f+ar
            drawArc(c("#151C36",.7f),180f,180f,false,Offset(cx-ar-u*.6f,centerY-ar),Size(ar*2+u*1.2f,ar*2),style=Stroke(u*1.2f))
            for(k in 0..10){val a=3.1416f+k/10f*3.1416f
                line(cx+cos(a)*(ar+u*.8f),centerY+sin(a)*(ar+u*.8f),cx+cos(a)*(ar+u*1.5f),centerY+sin(a)*(ar+u*1.5f),u*.14f,c("#C0B5C4",.45f))}
        }
    }
    line(0f,deck,W,deck,u*.25f,c("#B3B4CC",.65f))
    line(0f,deck+dh*.8f,W,deck+dh*.8f,u*.18f,c("#222A47"))
    // Vallée et rivière sous le viaduc
    ridgeFill(ridgePts(H * .84f, H * .03f, 50) { x -> .5f + .5f * nz.at(x * 4f + 3f, 3) }, c("#141A34"))
    rect(0f, H * .86f, W, H * .14f, c("#0C1026")); for (i in 0 until 14) { val y = H * (.88f + i * .008f); val x = W * .65f + sin(i * 2.1f) * u * 4f; line(x - u * 6f, y, x + u * 6f, y, max(1f, u * .3f), c("#FFE7B0", .25f - i * .015f)) }
    conifers(26, 8, u * 6f, u * 12f, c("#0E1428"), { H * .86f + u * 2f })
    // Le train : locomotive et voitures aux fenêtres chaudes, qui traverse
    val k = (t * 2f) % 1f; val tl = W * 2.4f; val x0 = W * 1.1f - k * (tl + W * 1.2f)
    val ty = deck - u * 6.5f; val th = u * 6.5f
    for (car in 0 until 7) { val cx = x0 + car * u * 33f; val cw = u * 31f
        if (cx > W || cx + cw < 0f) continue
        drawRoundRect(vg(ty, deck, 0f to c("#2A3A6A"), 1f to c("#141C3A")), Offset(cx, ty), Size(cw, th), CornerRadius(u * 1.6f))
        if (car == 0) { fill(poly(cx, ty + th, cx - u * 4f, ty + th, cx - u * 1f, ty + th * .3f, cx + u * 2f, ty), c("#2A3A6A")); glow(cx - u * 3f, ty + th * .65f, u * 10f, c("#FFF0C0", .6f), BlendMode.Screen); disc(cx - u * 2.6f, ty + th * .65f, u * .9f, c("#FFFBEA")) }
        else for (w in 0 until 6) { val wx = cx + u * (2.5f + w * 4.8f); drawRoundRect(c("#FFD27A"), Offset(wx, ty + th * .25f), Size(u * 3.2f, th * .35f), CornerRadius(u * .5f)); glow(wx + u * 1.6f, ty + th * .45f, u * 4f, c("#FFC46A", .25f), BlendMode.Screen) }
        rect(cx, deck - u * .8f, cw, u * .8f, c("#0A0E1C"))
        line(cx+u,ty+u*.5f,cx+cw-u,ty+u*.5f,u*.25f,c("#8B9CBB",.6f))
        rect(cx+cw-u*1.2f,ty+u*.8f,u*.25f,th-u*1.5f,c("#131B33"))
        for(wx in listOf(cx+u*5f,cx+cw-u*5f))drawCircle(c("#0D1427"),u*.9f,Offset(wx,deck-u*.1f))
    }
    // Reflets du train sur la rivière
    for (i in 0 until 10) { val x = ((x0 + i * u * 22f) % (W * 2f)); if (x in 0f..W) line(x, H * (.9f + i * .006f), x + u * 5f, H * (.9f + i * .006f), max(1f, u * .3f), c("#FFD27A", .25f)) }
}
