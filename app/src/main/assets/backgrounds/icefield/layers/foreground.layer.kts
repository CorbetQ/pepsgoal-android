private fun DrawScope.icefieldForeground(t: Float, light: Tod?) {
    val key="icefield"

    val w = size.width; val h = size.height; val u = min(w, h)
    val ink = surfaceColor("#273E43", 1f, light)



            val base=when(key){"snowpeak","icefield"->"#BDD7E5";"dunes"->"#CB8552";"canyon"->"#A6523B";"magma"->"#392B35";else->"#475C68"}
            for(i in 0..3) {
                val x=w*(.03f+i*.31f);val y=h*(.965f+(i%2)*.018f);val r=u*(.027f+(i%3)*.012f)
                castShadow(x,y,r*.7f,r*2.5f)
                fill(poly(x-r,y,x-r*.55f,y-r*.65f,x+r*.2f,y-r,x+r,y-r*.2f,x+r*.8f,y+r*.23f,x-r*.75f,y+r*.2f),surfaceColor(base,1f,light))
                fill(poly(x-r*.55f,y-r*.65f,x+r*.2f,y-r,x+r*.1f,y-r*.15f,x-r*.75f,y+r*.2f),surfaceColor(if(key=="magma")"#5B3F46" else "#E2BE9E",.38f,light))
            }

}
