private fun DrawScope.kopaForeground(t: Float, light: Tod?) {
    val key="kopa"

    val w = size.width; val h = size.height; val u = min(w, h)
    val ink = surfaceColor("#273E43", 1f, light)



            val flag=if(key=="kopa")c("#F6F2E7") else c("#DA4D53")
            for(side in 0..1) {
                val x=w*(if(side==0).075f else .925f);val y=h*.96f
                line(x,y,x,y-u*.065f,u*.0025f,c("#E5E3D6"))
                fill(poly(x,y-u*.065f,x+u*.027f,y-u*.054f,x,y-u*.041f),flag)
            }

}
