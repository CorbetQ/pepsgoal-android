private fun DrawScope.xmastree(t: Float) {
    val w=size.width;val h=size.height;val unit=min(w,h)
    xmastreeSky(0f to c("#101D39"),.6f to c("#355473"),1f to c("#7390A5"))
    starField(312,64,h*.5f,c("#E2E7DB",.7f),t,2)
    glow(w*.18f,h*.16f,unit*.24f,c("#B9D3F3",.24f),BlendMode.Screen)
    disc(w*.18f,h*.16f,unit*.041f,c("#F5EBCF"))
    val hill=Nz(89)
    ridgeFill(ridgePts(h*.64f,h*.07f,70){hill.at(it*3f,3)*.5f+.5f},vg(h*.58f,h,0f to c("#7F9AAF"),1f to c("#C9DAE0")))
    conifers(21,313,h*.04f,h*.1f,c("#3D6269"),{h*.64f},-w*.04f,w*1.04f)
    ridgeFill(ridgePts(h*.76f,h*.035f,60){hill.at(it*4f+9f,3)*.5f+.5f},vg(h*.7f,h,0f to c("#E7E9DE"),1f to c("#8CAEBF")))
    // A warm distant cabin anchors the scale of the clearing.
    val cx=w*.15f;val cy=h*.7f;val cw=unit*.17f;val ch=unit*.11f
    fill(poly(cx-cw*.55f,cy,cx+cw*.55f,cy,cx+cw*.8f,cy+unit*.025f,cx-cw*.3f,cy+unit*.035f),c("#5F8096",.4f))
    rect(cx-cw/2,cy-ch,cw,ch,c("#8F6861"))
    fill(poly(cx+cw*.5f,cy,cx+cw*.66f,cy-unit*.035f,cx+cw*.66f,cy-ch-unit*.025f,cx+cw*.5f,cy-ch),c("#624F5B"))
    fill(poly(cx-cw*.63f,cy-ch,cx,cy-ch-unit*.075f,cx+cw*.67f,cy-ch),c("#E8EBE1"))
    rect(cx-cw*.3f,cy-ch*.7f,cw*.19f,ch*.38f,c("#FFD492"));rect(cx+cw*.11f,cy-ch*.7f,cw*.19f,ch*.38f,c("#FFD492"))
    ellGlow(cx,cy+unit*.02f,cw*.9f,unit*.04f,c("#FFD28B",.32f),BlendMode.Screen)
    // Main tree is built from overlapping bough masses, rather than stacked flat triangles.
    val tx=w*.61f;val base=h*.865f;val th=h*.48f;val tw=min(w*.67f,th*.8f)
    drawOval(c("#52778D",.4f),Offset(tx-tw*.56f,base-unit*.006f),Size(tw*1.2f,unit*.065f))
    rect(tx-tw*.045f,base-th*.12f,tw*.09f,th*.12f,c("#72564B"))
    for(tier in 0..5){
        val f=tier/5f;val y=base-th*(.11f+f*.67f);val width=tw*(1f-f*.75f)
        val p=Path().apply{moveTo(tx,y-th*.29f);cubicTo(tx-width*.08f,y-th*.16f,tx-width*.38f,y-th*.03f,tx-width*.53f,y+th*.065f)
            cubicTo(tx-width*.32f,y+th*.09f,tx-width*.1f,y+th*.055f,tx,y+th*.078f)
            cubicTo(tx+width*.2f,y+th*.065f,tx+width*.42f,y+th*.09f,tx+width*.53f,y+th*.065f)
            cubicTo(tx+width*.3f,y-th*.04f,tx+width*.1f,y-th*.17f,tx,y-th*.29f);close()}
        drawPath(p,lg(tx-width*.5f,y-th*.2f,tx+width*.5f,y+th*.1f,0f to c("#559780"),.4f to c("#2B6C60"),1f to c("#123F45")))
        clipPath(p){
            fill(lobed(tx+width*.18f,y+th*.055f,width*.55f,th*.035f,600+tier,5,.15f),c("#0D3240",.4f))
            for(i in 0..5){val x=tx+width*(-.4f+i*.16f);val sy=y+sin(i*.9f+tier)*th*.014f
                drawOval(c("#DCE9DE",.88f),Offset(x-width*.08f,sy-th*.015f),Size(width*.2f,th*.036f))}
        }
    }
    val colors=listOf(c("#FFD08A"),c("#EA8B87"),c("#80DCD3"))
    for(row in 0..3){val y=base-th*(.18f+row*.18f);val width=tw*(.92f-row*.19f)
        val cable=Path().apply{moveTo(tx-width*.45f,y-th*.035f);quadraticTo(tx,y+th*.07f,tx+width*.45f,y-th*.035f)}
        drawPath(cable,c("#D7BE89",.8f),style=Stroke(unit*.002f))
        for(i in 0..7){val f=i/7f;val x=tx-width*.45f+width*.9f*f;val yy=y-th*.035f+th*.2f*f*(1-f)
            val col=colors[(i+row)%colors.size];val level=.76f+.16f*sin(t*TAU*2+i+row)
            glow(x,yy,unit*.026f,col.a(.4f*level),BlendMode.Screen)
            disc(x,yy,unit*.005f,col);disc(x-unit*.001f,yy-unit*.001f,unit*.0016f,c("#FFF8DC"))
        }
    }
    val starY=base-th*1.08f
    glow(tx,starY,unit*.12f,c("#FFDCA0",.52f),BlendMode.Screen)
    fill(star5(tx,starY,unit*.034f),c("#FFE3A0"));line(tx,starY-unit*.022f,tx,starY+unit*.018f,unit*.002f,c("#FFF4CE"))
    // Wrapped gifts have a lid, side face, ribbon and a grounded shadow.
    for(i in 0..3){val x=tx+tw*(i*.22f-.38f);val y=base+unit*(.013f+i%2*.015f);val gw=unit*(.08f+i%2*.018f);val gh=gw*(.65f+i%3*.16f)
        val col=colors[i%colors.size]
        drawOval(c("#4C748D",.35f),Offset(x-gw*.08f,y-gh*.06f),Size(gw*1.3f,gh*.3f))
        rect(x,y-gh,gw,gh,col);fill(poly(x+gw,y,x+gw*1.2f,y-gh*.2f,x+gw*1.2f,y-gh*1.2f,x+gw,y-gh),mix(col,c("#203A52"),.4f))
        fill(poly(x,y-gh,x+gw*.2f,y-gh*1.2f,x+gw*1.2f,y-gh*1.2f,x+gw,y-gh),mix(col,c("#FFFFFF"),.28f))
        rect(x+gw*.42f,y-gh,gw*.12f,gh,c("#FFF0CA"));rect(x,y-gh*.6f,gw,gh*.12f,c("#FFF0CA"))
        drawOval(c("#FFF0CA"),Offset(x+gw*.28f,y-gh*1.2f-gh*.11f),Size(gw*.2f,gh*.14f),style=Stroke(unit*.002f))
    }
    ellGlow(tx,base,tw*.8f,unit*.09f,c("#E7CC94",.18f),BlendMode.Screen)
    val snow=Mul(720)
    for(i in 0 until 65){val x=snow()*w;val y=((snow()+t*(.5f+snow()*.5f))%1f)*h
        disc(x+sin(t*TAU+i)*unit*.008f,y,unit*(.0015f+snow()*.003f),c("#F4F5E9",.65f))}
}
