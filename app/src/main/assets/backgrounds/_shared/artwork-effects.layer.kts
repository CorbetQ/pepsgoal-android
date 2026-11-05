/** Small native movement above original art; budgets are authored in each scene's animations.json. */
internal fun DrawScope.artworkMotion(key:String,t:Float,environment:fr.pepsgoal.core.SceneEnvironment?,artLeft:Float,artTop:Float,artWidth:Float,artHeight:Float) {
    withBackgroundStyle(key) {
        val style=backgroundStyle(key);val count=particles(style.overlayCount)
        val w=size.width;val h=size.height;val unit=min(w,h);val random=Mul(713)
        when(style.overlay) {
            "stellar","bubbles","embers","snow","petals","hearts","lanterns" -> {
                for(i in 0 until count) {
                    val start=random();val x0=random();val radius=unit*(.002f+random()*.004f)
                    val u=(start+t)%1f;val rising=style.overlay !in setOf("snow","petals")
                    val x=w*x0+sin(u*TAU+i)*unit*.018f
                    val y=h*(if(rising)1.04f-u*1.12f else u*1.12f-.04f)
                    val fade=sm(0f,.12f,u)*(1-sm(.85f,1f,u))
                    when(style.overlay) {
                        "hearts" -> {
                            val p=heart(x,y,unit*.028f)
                            drawPath(p,c("#ED789C",fade*.75f))
                            translate(-unit*.004f,-unit*.004f){drawPath(p,c("#FFD1D6",fade*.35f))}
                        }
                        "lanterns" -> {
                            val rw=unit*.018f;val rh=rw*1.5f
                            glow(x,y,rw*3f,c("#FFAA62",fade*.1f),BlendMode.Screen)
                            val paper=Path().apply {
                                moveTo(x-rw*.65f,y-rh)
                                cubicTo(x-rw*1.2f,y-rh*.6f,x-rw*.95f,y+rh*.65f,x-rw*.55f,y+rh)
                                cubicTo(x-rw*.15f,y+rh*1.14f,x+rw*.2f,y+rh*1.14f,x+rw*.55f,y+rh)
                                cubicTo(x+rw*.95f,y+rh*.65f,x+rw*1.2f,y-rh*.6f,x+rw*.65f,y-rh)
                                cubicTo(x+rw*.2f,y-rh*1.15f,x-rw*.2f,y-rh*1.15f,x-rw*.65f,y-rh);close()
                            }
                            drawPath(paper,Brush.linearGradient(listOf(c("#A95547",fade),c("#FFD791",fade),c("#F39A66",fade)),Offset(x-rw,y),Offset(x+rw,y)))
                            drawOval(c("#FFD7A3",fade),Offset(x-rw*.65f,y-rh*1.06f),Size(rw*1.3f,rh*.18f))
                            drawOval(c("#A85C47",fade),Offset(x-rw*.55f,y+rh*.87f),Size(rw*1.1f,rh*.25f))
                            disc(x,y+rh*.95f,rw*.24f,c("#FFF0B4",fade))
                        }
                        "bubbles" -> drawCircle(c("#99DCEC",fade*.35f),radius*1.6f,Offset(x,y),style=Stroke(unit*.0015f))
                        "petals" -> rotate(sin(u*TAU+i)*45,Offset(x,y)) {drawOval(c("#F7A3BC",fade*.75f),Offset(x-radius,y-radius),Size(radius*2,radius*4))}
                        else -> disc(x,y,radius,c(when(style.overlay){"embers"->"#FFD280";"snow"->"#EBF4EF";else->"#D9D9EB"},fade*if(style.overlay=="stellar").45f else .7f))
                    }
                }
            }
            "glyphs" -> for(i in 0 until count) {
                val x=w*(.06f+i*.88f/max(1,count-1));val u=(i*.173f+t)%1f
                for(j in 0..5) {
                    val y=(u*h+j*unit*.04f)%h
                    val col=c(if(j==0)"#BFF5BD" else "#55BD91",.4f*(1-j*.12f))
                    line(x,y,x+unit*.012f,y,unit*.002f,col)
                    line(x+unit*.006f,y-unit*.009f,x+unit*.006f,y+unit*.009f,unit*.002f,col)
                    if((i+j)%2==0)line(x+unit*.012f,y,x+unit*.012f,y+unit*.009f,unit*.002f,col)
                }
            }
            "aurora" -> for(i in 0 until count) {
                val y=h*(.27f+i*.075f);val bend=sin(t*TAU+i)*h*.025f
                val p=Path().apply {moveTo(-w*.1f,y);cubicTo(w*.3f,y-h*.12f+bend,w*.6f,y+h*.13f,w*1.1f,y-h*.08f);lineTo(w*1.1f,y-h*.06f);cubicTo(w*.6f,y+h*.15f,w*.3f,y-h*.1f+bend,-w*.1f,y+h*.025f);close()}
                drawPath(p,c(if(i%2==0)"#68D6B6" else "#B1A0E5",.12f))
            }
            "glass" -> {
                val e=environment
                if(e?.condition in setOf("snow","fog"))clipRect(w*.07f,h*.02f,w*.93f,h*.84f) {drawSceneWeather(e!!.condition,e.clouds,e.wind,e.freshness,e.night,t,count)}
                val wet=if(e==null)1f else if(e.condition in setOf("rain","storm"))e.freshness else 0f
                for(i in 0 until (count*wet).toInt()) {
                    val x=w*(.09f+random()*.82f);val y=h*.84f*((random()+t*(if(i%6==0)1f else 0f))%1f)
                    if(x<w*.35f && y>h*.34f)continue // The foreground houseplant stays inside, dry.
                    val r=unit*(.003f+random()*.004f)
                    drawOval(c("#B3D1DD",.3f),Offset(x-r,y-r*1.5f),Size(r*2,r*3))
                    drawOval(c("#E6EFF3",.65f),Offset(x-r*.4f,y-r),Size(r*.6f,r*.7f))
                    if(i%6==0)line(x,y-r*12,x,y-r*2,r*.3f,c("#C6DDE7",.2f))
                }
            }
            "stadium" -> {
                val x=w*.85f;val y=h*.72f;val length=w*.08f;val bend=sin(t*TAU)*length*.12f
                line(x,y,x,y+length*1.7f,w*.003f,c("#E0D8C2"))
                drawPath(poly(x,y,x+length,y+bend,x+length,y+length*.5f+bend,x,y+length*.5f),c("#ECE7D8"))
                drawPath(poly(x,y,x+length*.5f,y+bend*.5f,x+length*.5f,y+length*.5f+bend*.5f,x,y+length*.5f),c("#251B2A"))
            }
            "scoreboard" -> {
                ecranArtworkScore(artLeft,artTop,artWidth,artHeight,t)
                for(i in 0 until count) {
                    val x=random()*w;val y=((random()+t*2)%1f)*h
                    line(x,y,x-unit*.005f,y+unit*.022f,unit*.0012f,c("#BDD4E1",.24f))
                }
            }
            "fireworks" -> for(i in 0 until count) {
                val u=(t+i*.31f)%1f;val cx=w*(.2f+i*.3f);val cy=h*(.25f+(i%2)*.12f)
                val r=unit*(.02f+u*.22f);val a=(1-sm(.3f,.9f,u))*.7f
                for(k in 0 until 12) {
                    val angle=k*TAU/12;val dx=cos(angle);val dy=sin(angle)
                    line(cx+dx*r*.7f,cy+dy*r*.7f,cx+dx*r,cy+dy*r,w*.0025f,c(if(i%2==0)"#FFD88B" else "#F8A0B7",a))
                }
            }
        }
    }
}
