/** A coherent perspective, shared by the two football illustrations; team identity stays in its own folder. */
private data class StadiumPalette(val accent: String, val roof: String, val wet: Boolean, val quiet: Boolean)
private fun DrawScope.stadiumBowl(t: Float, palette: StadiumPalette) {
    val w=size.width;val h=size.height;val unit=min(w,h)
    val accent=c(palette.accent);val steel=c("#45566C");val fog=c(if(palette.wet)"#463546" else "#516D92")
    sky(0f to c("#070E24"),.45f to c(if(palette.wet)"#302239" else "#203C64"),1f to fog)
    starField(78,54,h*.34f,c("#D7E2ED",.55f),t,2)
    if(!palette.wet){glow(w*.78f,h*.13f,unit*.2f,c("#B7CFEF",.25f),BlendMode.Screen);disc(w*.78f,h*.13f,unit*.027f,c("#F6F0D6"))}
    for(i in 0..2)softCloud(w*(.1f+i*.4f)+sin(t*TAU+i)*unit*.025f,h*(.17f+i*.045f),w*.55f,
        fog.copy(alpha=.18f),c("#9BAAC0",.12f),840+i)
    // Angers behind the stadium, with alternating schist/tuffeau towers.
    if(!palette.wet){
        val base=h*.355f;val tw=unit*.027f
        rect(w*.04f,base-h*.03f,w*.2f,h*.04f,c("#33445B"))
        for(i in 0..3){val x=w*(.045f+i*.052f);val th=h*(.045f+i%2*.012f)
            for(k in 0..5)rect(x,base-th+k*th/6,tw,th/6+1,c(if(k%2==0)"#4A566A" else "#68768A"))
            drawOval(c("#778497"),Offset(x-tw*.08f,base-th-tw*.1f),Size(tw*1.16f,tw*.22f))
        }
    }
    val far=h*.615f
    fun ground(u: Float,v: Float): Offset {
        val depth=v.coerceIn(-.1f,1.2f);val y=far+(h*1.05f-far)*depth
        val left=w*(.17f-.25f*depth);val right=w*(.83f+.25f*depth)
        return Offset(left+(right-left)*u,y)
    }
    fun patch(u0: Float,v0: Float,u1: Float,v1: Float): Path {
        val a=ground(u0,v0);val b=ground(u1,v0);val c=ground(u1,v1);val d=ground(u0,v1)
        return poly(a.x,a.y,b.x,b.y,c.x,c.y,d.x,d.y)
    }
    // Back stand and its thick cantilevered roof. Side stands converge toward the field.
    val back=poly(-w*.06f,h*.425f,w*1.06f,h*.425f,w*.86f,far,w*.14f,far)
    fill(back,vg(h*.425f,far,0f to c("#152239"),1f to c("#060F1C")))
    val leftStand=poly(-w*.08f,h*.45f,w*.16f,h*.48f,w*.17f,far,-w*.08f,h*.89f)
    val rightStand=poly(w*1.08f,h*.45f,w*.84f,h*.48f,w*.83f,far,w*1.08f,h*.89f)
    fill(leftStand,lg(0f,h*.5f,w*.22f,h*.7f,0f to steel,1f to c("#101C31")))
    fill(rightStand,lg(w,h*.5f,w*.78f,h*.7f,0f to steel,1f to c("#101C31")))
    // Side terraces contain seats and spectators too; their rows recede in perspective.
    for(side in 0..1){
        val q=if(side==0)floatArrayOf(-w*.08f,h*.45f,w*.16f,h*.48f,-w*.08f,h*.89f,w*.17f,far)
            else floatArrayOf(w*1.08f,h*.45f,w*.84f,h*.48f,w*1.08f,h*.89f,w*.83f,far)
        val shape=if(side==0)leftStand else rightStand
        clipPath(shape){
            for(row in 0..7){val v=(row+.5f)/8;val a=quadPt(q,0f,v);val b=quadPt(q,1f,v)
                drawLine(c("#7B899A",.32f),a,b,unit*.003f)
                val random=Mul(660+row+side*30)
                for(col in 0..12){if(random()<(if(palette.quiet).58f else .1f))continue
                    val p=quadPt(q,(col+.5f)/13,v);val size=unit*(.004f+(1-v)*.003f)
                    val shirt=if((row+col)%3==0)accent else if((row+col)%2==0)c("#C9D2D3")else c("#12202D")
                    drawRoundRect(shirt,Offset(p.x-size*.5f,p.y),Size(size,size*1.3f),CornerRadius(size*.2f))
                    disc(p.x,p.y-size*.35f,size*.35f,c("#B49C87"))
                }
            }
        }
    }
    clipPath(back){
        for(row in 0..7){
            val f=row/7f;val y=h*(.445f+f*.15f);val margin=w*(.025f+f*.105f)
            line(margin,y,w-margin,y,unit*(.006f+f*.003f),c("#3F526B",.65f))
            val people=Mul(190+row)
            val count=38
            for(col in 0 until count){
                val seat=(w-2*margin)/count;val x=margin+seat*(col+.5f)
                if(people()<(if(palette.quiet).52f else .06f))continue
                val wave=max(0f,sin(t*TAU*2-col*.24f-row*.3f))
                val lift=if(palette.quiet)0f else wave*unit*.006f
                val size=unit*(.005f+f*.003f);val yy=y-lift
                val shirt=if((row+col)%3==0)accent else if((row+col)%2==0)c("#DEE4E4") else c("#15202C")
                drawRoundRect(shirt,Offset(x-size*.55f,yy),Size(size*1.1f,size*1.2f),CornerRadius(size*.2f))
                disc(x,yy-size*.4f,size*.4f,c(if((col+row)%3==0)"#A77C64" else "#DBB798"))
                if(wave>.65f&&!palette.quiet){line(x-size,yy-size*.15f,x+size,yy-size*.4f,unit*.002f,shirt)}
            }
        }
    }
    // Aisles, rails and recessed access tunnels separate the crowd into architectural sections.
    for(i in 0..4){val x=w*(.1f+i*.2f)
        fill(poly(x,h*.43f,x+w*.015f,h*.43f,w*.5f+(x-w*.5f)*.72f+w*.01f,far,w*.5f+(x-w*.5f)*.72f,far),c("#8092A1",.32f))
    }
    for(x in listOf(.27f,.73f)){
        drawRoundRect(c("#040B16"),Offset(w*x-unit*.034f,far-unit*.066f),Size(unit*.068f,unit*.066f),CornerRadius(unit*.014f))
        line(w*x-unit*.028f,far-unit*.057f,w*x+unit*.028f,far-unit*.057f,unit*.003f,c("#C0D4D9",.6f))
    }
    val roof=poly(-w*.09f,h*.365f,w*.5f,h*.335f,w*1.09f,h*.365f,w*1.04f,h*.418f,w*.5f,h*.385f,-w*.04f,h*.418f)
    fill(roof,vg(h*.33f,h*.43f,0f to c(palette.roof),.55f to c("#506378"),1f to c("#101D32")))
    for(i in 0..18){val x=w*(i/18f)
        line(x,h*(.365f-abs(i/18f-.5f)*.02f),x-w*.012f,h*(.405f-abs(i/18f-.5f)*.025f),unit*.002f,c("#B4C5CC",.52f))
    }
    line(0f,h*.418f,w*.5f,h*.385f,unit*.004f,c("#BFCED5",.65f))
    line(w*.5f,h*.385f,w,h*.418f,unit*.004f,c("#BFCED5",.65f))
    // Trusses and dark roof underside give the canopy visible thickness.
    for(i in 0..9){val x=w*i/9f
        line(x,h*.418f,x+w*.032f,h*.46f,unit*.003f,c("#182738"))
        line(x+w*.032f,h*.46f,x+w*.066f,h*.418f,unit*.002f,c("#6F8091",.6f))
    }
    // Team banner, deliberately below the crowd rather than a flat stripe across the sky.
    for(i in 0 until 24){val x=w*(.14f+.72f*i/24f)
        rect(x,far-unit*.023f,w*.72f/24+1,unit*.022f,if(i%2==0)c("#E7E9E4") else accent)
    }
    line(w*.14f,far,w*.86f,far,unit*.004f,c("#C3D2CD"))
    // Perspective turf, projected mowing bands and light falling onto the field.
    val pitch=patch(0f,0f,1f,1.12f)
    fill(pitch,vg(far,h,0f to c(if(palette.wet)"#2A654D" else "#438655"),1f to c("#123F38")))
    clipPath(pitch){
        for(i in 0..10)if(i%2==0)fill(patch(0f,i/11f,1f,(i+1)/11f),c("#73A365",.18f))
        ellGlow(w*.36f,far+h*.1f,w*.31f,h*.12f,c("#D8F4C3",.23f),BlendMode.Screen)
        ellGlow(w*.72f,far+h*.25f,w*.4f,h*.13f,c("#B7DACF",.2f),BlendMode.Screen)
        val grass=Mul(642)
        for(i in 0 until 130){val uv=grass();val v=grass();val p=ground(uv,v)
            line(p.x,p.y,p.x+unit*.0015f,p.y-unit*(.0015f+v*.003f),max(.6f,unit*.001f),c(if(i%3==0)"#B5CF9C" else "#0D443A",.22f))
        }
        if(palette.wet)for(i in 0 until 32){val p=ground((i*.618f)%1f,(i*.414f)%1f)
            line(p.x-unit*.009f,p.y,p.x+unit*.009f,p.y,unit*.0018f,c("#ABCCD1",.24f))
        }
    }
    val paint=c("#E5EEDB",.88f);val stroke=max(1.1f,unit*.0028f)
    fun pitchLine(u0: Float,v0: Float,u1: Float,v1: Float){val a=ground(u0,v0);val b=ground(u1,v1);drawLine(paint,a,b,stroke)}
    pitchLine(0f,0f,1f,0f);pitchLine(0f,0f,0f,1.1f);pitchLine(1f,0f,1f,1.1f)
    pitchLine(0f,.5f,1f,.5f)
    val circle=Path()
    for(i in 0..64){val a=i/64f*TAU;val p=ground(.5f+cos(a)*.17f,.5f+sin(a)*.16f)
        if(i==0)circle.moveTo(p.x,p.y)else circle.lineTo(p.x,p.y)}
    drawPath(circle,paint,style=Stroke(stroke))
    for(v in listOf(0f,1f)){
        val end=if(v==0f).19f else .81f
        pitchLine(.24f,v,.24f,end);pitchLine(.76f,v,.76f,end);pitchLine(.24f,end,.76f,end)
    }
    // Far goal: rigid frame, net receding behind it, and its contact shadow.
    val gx=w*.5f;val gy=far-h*.004f;val gw=w*.19f;val gh=h*.041f
    fill(poly(gx-gw*.6f,gy,gx+gw*.6f,gy,gx+gw*.8f,gy+h*.018f,gx-gw*.25f,gy+h*.018f),c("#0D2E31",.5f))
    for(i in 0..8){val x=gx-gw/2+gw*i/8
        line(x,gy-gh,x+gw*.07f,gy,unit*.001f,c("#A7C0BD",.42f))}
    for(i in 0..4){val y=gy-gh+gh*i/4;line(gx-gw/2,y,gx+gw/2,y,unit*.001f,c("#A7C0BD",.42f))}
    line(gx-gw/2,gy,gx-gw/2,gy-gh,stroke,paint);line(gx+gw/2,gy,gx+gw/2,gy-gh,stroke,paint)
    line(gx-gw/2,gy-gh,gx+gw/2,gy-gh,stroke,paint)
    // Four side-mounted projector banks. Their beams originate at the lenses, not at the sky.
    for(side in 0..1)for(bank in 0..1){
        val x=w*(if(side==0).055f+bank*.11f else .945f-bank*.11f)
        val y=h*(.275f+bank*.045f);val bw=unit*.1f;val bh=unit*.031f
        line(x,y,x,h*.45f,unit*.006f,steel)
        for(j in 0..4){val yy=y+(h*.45f-y)*j/5;line(x-unit*.008f,yy,x+unit*.008f,yy+unit*.012f,unit*.0018f,c("#8494A2"))}
        fill(poly(x-bw*.38f,y+bh,x+bw*.38f,y+bh,w*(if(side==0).92f else .08f),h*.95f,w*(if(side==0).45f else .55f),far),
            lg(x,y,w*.5f,h*.9f,0f to c("#E3F0F2",.13f),1f to c("#D2EAE3",0f)))
        drawRoundRect(c("#1C293C"),Offset(x-bw/2,y),Size(bw,bh),CornerRadius(unit*.004f))
        for(r in 0..1)for(col in 0..4){val xx=x-bw*.39f+col*bw*.195f;val yy=y+bh*(.22f+r*.48f)
            disc(xx,yy,unit*.005f,c("#FFF9DE"))}
        glow(x,y+bh*.5f,bw*1.4f,c("#D7EEFF",.31f),BlendMode.Screen)
    }
    // Low perimeter apron and foreground railing add a near plane without hiding the pitch.
    val rail=h*.985f
    fill(poly(0f,rail-h*.025f,w,rail-h*.01f,w,h,0f,h),c("#12272A",.9f))
    line(0f,rail-h*.025f,w,rail-h*.01f,unit*.004f,c("#A8B9B8",.65f))
}
