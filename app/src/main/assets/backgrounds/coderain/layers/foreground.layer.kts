private fun DrawScope.coderainForeground(t: Float, light: Tod?) {
    val key="coderain"

    val w = size.width; val h = size.height; val u = min(w, h)
    val ink = surfaceColor("#273E43", 1f, light)



            for(i in 0..4){val x=w*(.04f+i*.22f);val y=h*.97f
                rect(x,y,u*.012f,u*.004f,c("#91E6B4",.7f));rect(x+u*.012f,y-u*.009f,u*.004f,u*.013f,c("#91E6B4",.7f))}

}
