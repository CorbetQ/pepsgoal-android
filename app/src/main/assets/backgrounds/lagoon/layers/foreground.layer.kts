private fun DrawScope.lagoonForeground(t: Float, light: Tod?) {
    val key="lagoon"

    val w = size.width; val h = size.height; val u = min(w, h)
    val ink = surfaceColor("#273E43", 1f, light)



            for(i in 0..2) {
                val x=w*(.09f+i*.39f);val y=h*(.94f+(i%2)*.025f);val r=u*.016f
                drawArc(surfaceColor("#F7E3BE",1f,light),185f,170f,true,Offset(x-r,y-r*.6f),Size(r*2,r*1.2f))
                for(k in 0..3)line(x,y,x-r+r*k*.66f,y-r*.5f,u*.0015f,surfaceColor("#CBA77D",.7f,light))
            }

}
