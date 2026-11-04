private fun DrawScope.freeWaves(fp:FreePal,t:Float) {
    val w=size.width;val h=size.height
    drawRect(Brush.verticalGradient(listOf(fp.base0,fp.base1)))
    for(i in 0..2) {
        val y=h*(.53f+i*.16f);val swell=sin(t*TAU+i)*w*.025f
        val col=if(i==0)mix(fp.c[2],fp.bg,.2f) else fp.c[(i+1)%4]
        val crest=Path().apply {
            moveTo(-w*.1f,y+w*.16f)
            cubicTo(w*.12f,y-w*.26f+swell,w*.28f,y-w*.28f,w*.45f,y-w*.02f)
            cubicTo(w*.63f,y+w*.24f,w*.88f,y-w*.25f-swell,w*1.1f,y-w*.04f)
            lineTo(w*1.1f,h*1.1f);lineTo(-w*.1f,h*1.1f);close()
        }
        translate(0f,w*.045f){drawPath(crest,mix(col,fp.ink,.6f))}
        drawPath(crest,mix(col,Color.White,.32f))
        translate(0f,w*.042f){drawPath(crest,col)}
        clipPath(crest) {
            val trough=Path().apply {moveTo(-w*.1f,y+w*.23f);cubicTo(w*.25f,y+w*.28f,w*.28f,y-w*.12f,w*.5f,y+w*.15f);cubicTo(w*.7f,y+w*.34f,w*.95f,y-w*.08f,w*1.1f,y+w*.12f);lineTo(w*1.1f,h);lineTo(-w*.1f,h);close()}
            drawPath(trough,mix(col,fp.ink,.28f))
            if(i==2)for(j in 0..3)drawRoundRect(mix(col,Color.White,.6f),Offset(w*(.1f+j*.22f),y+w*(.05f+(j%2)*.04f)),Size(w*.08f,w*.015f),CornerRadius(w*.02f))
        }
    }
}
