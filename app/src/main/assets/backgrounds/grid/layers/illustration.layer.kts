private fun DrawScope.freeGrid(fp:FreePal,t:Float) {
    val w=size.width;val h=size.height;val vx=w*.5f;val vy=h*.43f
    drawRect(Brush.verticalGradient(listOf(fp.base0,fp.base1)))
    val rows=floatArrayOf(.57f,.61f,.67f,.76f,.89f,1.08f)
    for(row in 0 until rows.size-1) {
        val y0=h*rows[row];val y1=h*rows[row+1]
        fun xAt(col:Int,y:Float)=vx+(col-3)*w*.38f*(y-vy)/(h-vy)
        for(col in -1..6) {
            val raised=(row==2&&col==1)||(row==3&&col==4)
            val lift=if(raised)w*.035f else 0f;val gutter=w*.008f
            val a=Offset(xAt(col,y0)+gutter,y0-lift);val b=Offset(xAt(col+1,y0)-gutter,y0-lift)
            val c0=Offset(xAt(col+1,y1)-gutter,y1-lift-gutter);val d=Offset(xAt(col,y1)+gutter,y1-lift-gutter)
            val color=fp.c[((col+row)%4+4)%4];val depth=w*(.009f+row*.005f)+lift
            if(raised)drawPath(poly(a.x,a.y+lift,b.x,b.y+lift,c0.x+w*.02f,c0.y+lift+w*.02f,d.x+w*.02f,d.y+lift+w*.02f),fp.ink.copy(alpha=.25f))
            drawPath(poly(d.x,d.y,c0.x,c0.y,c0.x,c0.y+depth,d.x,d.y+depth),mix(color,fp.ink,.57f))
            drawPath(poly(a.x,a.y,b.x,b.y,c0.x,c0.y,d.x,d.y),mix(color,Color.White,.12f))
            drawLine(mix(color,Color.White,.4f),a,b,w*.004f)
        }
    }
}
