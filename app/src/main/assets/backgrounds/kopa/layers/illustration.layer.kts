private fun DrawScope.kopa(t: Float) {
    stadiumBowl(t,StadiumPalette("#141A22","#B2C2CA",wet=false,quiet=false))
    val w=size.width;val h=size.height;val unit=min(w,h)
    // Striped SCO shield fixed to the roof fascia.
    val x=w*.5f;val y=h*.401f;val r=unit*.025f
    val badge=poly(x-r,y-r,x+r,y-r,x+r*.84f,y+r*.5f,x,y+r*1.2f,x-r*.84f,y+r*.5f)
    fill(badge,c("#E7E9E5"))
    clipPath(badge){for(i in 0..4)if(i%2==0)rect(x-r+2*r*i/5,y-r,2*r/5,r*2.3f,c("#141B25"))}
    drawPath(badge,c("#A7B8C4"),style=Stroke(unit*.002f))
    // Cloth flags, rather than a second row of identical spectators.
    for(i in 0..2){val fx=w*(.24f+i*.26f);val fy=h*(.515f+(i%2)*.035f);val fw=unit*.055f;val fh=unit*.036f
        line(fx,fy,fx,fy+unit*.12f,unit*.002f,c("#7B909C"))
        val wave=sin(t*TAU*2+i)*fh*.16f
        fill(poly(fx,fy,fx+fw,fy+wave,fx+fw,fy+fh+wave,fx,fy+fh),c("#EBECE5"))
        fill(poly(fx+fw*.48f,fy+wave*.48f,fx+fw,fy+wave,fx+fw,fy+fh+wave,fx+fw*.48f,fy+fh+wave*.48f),c("#111B24"))
    }
    val u=.5f+.28f*sin(t*TAU);val v=.63f+.06f*sin(t*TAU*2)
    val bx=w*(.17f-.25f*v+(.66f+.5f*v)*u);val by=h*(.615f+.435f*v);val br=unit*.012f
    drawOval(c("#0E302E",.5f),Offset(bx-br*1.3f,by+br*.45f),Size(br*2.9f,br*.8f))
    disc(bx,by,br,c("#E8ECE0"));fill(star5(bx,by,br*.54f),c("#1B2531"))
    disc(bx-br*.25f,by-br*.3f,br*.19f,c("#FFFFFF",.8f))
}
