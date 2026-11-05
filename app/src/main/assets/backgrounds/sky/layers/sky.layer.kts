/** Live sky art direction: sculpted cumulus, broad colour planes, no painted solar asset. */
internal fun DrawScope.weatherSky(environment: fr.pepsgoal.core.SceneEnvironment, t: Float, horizon: Float = size.height) {
    withBackgroundStyle("sky",environment.sun,environment.night) {
        val d=Tod(environment.night,environment.dawn,environment.day,environment.dusk,environment.sun,
            environment.clouds,environment.condition,environment.wind)
        val w=size.width;val h=horizon;val unit=min(w,size.height)
        val cover=max(d.clouds/100f,when(d.condition){"storm"->.95f;"rain","snow"->.75f;"fog"->.8f;else->0f}).coerceIn(0f,1f)
        val top=mix(d.col("#63BEEB","#8E8EC7","#6761A0","#111932"),d.col("#8EAFBE","#9C93A8","#786C91","#1B2943"),cover*.72f)
        val low=mix(d.col("#D1EFE7","#FFD6B1","#FFAA82","#344A70"),d.col("#C1D0D0","#C3B5B2","#A191AC","#3F5166"),cover*.75f)
        drawRect(Brush.verticalGradient(listOf(top,low),0f,h.coerceAtLeast(1f)))
        val warm=d.dawn+d.dusk
        if(warm>.02f)glow(w*(.15f+.7f*d.sun),h*.85f,w*.85f,c("#FFAB70",warm*(1-cover)*.35f),BlendMode.Screen)
        if(d.night>.05f) {
            starField(621,26,h*.8f,c("#FFF3D5",d.night*(1-cover)*.8f),0f,0)
            val moon=Offset(w*.78f,h*.23f);val radius=unit*.043f
            glow(moon.x,moon.y,radius*4f,c("#A9CBF1",d.night*(1-cover)*.12f),BlendMode.Screen)
            disc(moon.x,moon.y,radius,c("#F4EAC6",d.night*(1-cover*.9f)))
            disc(moon.x+radius*.24f,moon.y+radius*.15f,radius*.18f,c("#BEC8C2",d.night*(1-cover)*.65f))
            disc(moon.x-radius*.3f,moon.y-radius*.3f,radius*.12f,c("#CED2C4",d.night*(1-cover)*.6f))
        }
        val sunshine=(1-d.night)*(1-cover*.96f)
        if(sunshine>.03f) {
            val x=w*(.12f+.76f*d.sun);val y=h*(.87f-.65f*sin(d.sun*3.14159f));val r=unit*.047f
            glow(x,y,r*5f,c("#FFE2A4",sunshine*.22f),BlendMode.Screen)
            disc(x,y,r,c("#FFD272",sunshine))
            disc(x-r*.13f,y-r*.13f,r*.77f,c("#FFF0BA",sunshine))
        }
        fun cloud(x:Float,y:Float,cw:Float,opacity:Float) {
            val ch=cw*.27f
            val silhouette=Path().apply {
                moveTo(x,y);cubicTo(x-cw*.04f,y-ch*.4f,x+cw*.08f,y-ch*.7f,x+cw*.22f,y-ch*.57f)
                cubicTo(x+cw*.27f,y-ch*1.12f,x+cw*.55f,y-ch*1.12f,x+cw*.64f,y-ch*.62f)
                cubicTo(x+cw*.8f,y-ch*.9f,x+cw*.97f,y-ch*.54f,x+cw*.92f,y-ch*.21f)
                cubicTo(x+cw*1.07f,y-ch*.03f,x+cw*.96f,y+ch*.21f,x+cw*.8f,y+ch*.18f)
                lineTo(x+cw*.14f,y+ch*.18f);cubicTo(x+cw*.03f,y+ch*.18f,x-cw*.02f,y+ch*.1f,x,y);close()
            }
            val shade=mix(d.col("#A9CCD9","#BBADB9","#A78BAA","#344A68"),top,cover*.3f).copy(alpha=opacity)
            val body=mix(d.col("#F9F5DE","#FFE4CF","#F6C2B5","#65768B"),shade,cover*.35f).copy(alpha=opacity)
            drawPath(silhouette,shade)
            clipPath(silhouette) {
                val light=Path().apply {
                    moveTo(x-cw*.1f,y-ch*1.3f);lineTo(x+cw*1.1f,y-ch*1.3f);lineTo(x+cw*1.1f,y-ch*.2f)
                    cubicTo(x+cw*.65f,y-ch*.1f,x+cw*.64f,y-ch*.42f,x+cw*.44f,y-ch*.23f)
                    cubicTo(x+cw*.25f,y-ch*.08f,x+cw*.11f,y-ch*.2f,x-cw*.1f,y-ch*.15f);close()
                }
                drawPath(light,body)
            }
        }
        val count=2+(cover*5).toInt()
        for(i in 0 until count) {
            val distance=i%3;val cw=w*(.35f+distance*.16f)
            val x=w*((i*.371f+t*(.035f+d.wind/2400f))%1.5f-.25f)
            cloud(x,h*(.28f+distance*.19f+(i%2)*.04f),cw,.65f+distance*.12f)
        }
    }
}

/** A quiet lower landscape belongs only to the Sky theme, never to another scene's under-sky. */
internal fun DrawScope.livingSky(environment: fr.pepsgoal.core.SceneEnvironment,t:Float) {
    weatherSky(environment,t)
    withBackgroundStyle("sky",environment.sun,environment.night) {
        val d=Tod(environment.night,environment.dawn,environment.day,environment.dusk,environment.sun)
        val w=size.width;val h=size.height
        for(i in 0..2) {
            val y=h*(.84f+i*.065f);val col=d.col(
                listOf("#8ACAA8","#61AD8B","#3C806F")[i],
                listOf("#A8B6A0","#8D9F91","#648E7D")[i],
                listOf("#B4AC99","#92878B","#626880")[i],
                listOf("#344F60","#294153","#213348")[i])
            val p=Path().apply {moveTo(0f,h);lineTo(0f,y);cubicTo(w*.27f,y-w*.14f,w*.4f,y+w*.1f,w*.62f,y-w*.02f);cubicTo(w*.82f,y-w*.18f,w*.98f,y-w*.04f,w,y);lineTo(w,h);close()}
            drawPath(p,col)
        }
        val flower=d.col("#FFE2A3","#F3CDAC","#EAB8A5","#92A7B9")
        for(i in 0..4) {
            val x=w*(.04f+i*.045f);val y=h*(.965f+(i%2)*.018f);val r=w*.008f
            line(x,y,x-w*.004f,y+w*.035f,w*.003f,d.col("#347765","#657964","#666674","#213447"))
            for(k in 0..4){val a=k*TAU/5;disc(x+cos(a)*r,y+sin(a)*r,r*.65f,flower)}
            disc(x,y,r*.45f,c("#ECA447",1-d.night*.6f))
        }
    }
}
