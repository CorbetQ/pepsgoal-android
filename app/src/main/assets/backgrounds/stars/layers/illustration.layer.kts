private fun DrawScope.freeStars(fp:FreePal,t:Float) {
    val w=size.width;val h=size.height
    drawRect(Brush.verticalGradient(listOf(fp.base0,fp.base1)))
    val points=listOf(Offset(w*.18f,h*.2f),Offset(w*.7f,h*.32f),Offset(w*.85f,h*.57f),Offset(w*.28f,h*.64f))
    for(i in 0..2)drawLine(fp.c[2].copy(alpha=.22f),points[i],points[i+1],w*.004f)
    points.forEachIndexed {i,p->drawCircle(fp.c[i%4].copy(alpha=.55f),w*.012f,p)}
    fun paperStar(x:Float,y:Float,r:Float,col:Color) {
        val shape=star5(x,y,r)
        translate(r*.06f,r*.14f){drawPath(shape,mix(col,fp.ink,.64f))}
        drawPath(shape,col)
        clipPath(shape) {
            drawPath(poly(x,y,x-r*1.1f,y-r*1.1f,x+r*.25f,y-r*1.1f,x+r*.7f,y+r*.75f),mix(col,Color.White,.4f))
            drawPath(poly(x,y,x+r*.7f,y+r*.75f,x,y+r*.4f,x-r*.65f,y+r*.9f),mix(col,fp.ink,.2f))
        }
    }
    paperStar(w*.83f,h*.46f,w*.14f,fp.c[2])
    paperStar(w*.18f,h*.71f,w*.2f,fp.c[1])
    paperStar(w*.66f,h*.9f,w*.37f,fp.c[0])
    for(i in 0 until 9) {
        val x=w*((i*.337f+.1f)%1);val y=h*(.06f+(i*.197f)% .46f)
        val alpha=.3f+.2f*sin(t*TAU+i)
        drawCircle(fp.c[i%4].copy(alpha=alpha),w*.004f,Offset(x,y))
    }
}
