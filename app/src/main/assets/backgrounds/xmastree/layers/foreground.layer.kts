private fun DrawScope.xmastreeForeground(t: Float, light: Tod?) {
    val key="xmastree"

    val w = size.width; val h = size.height; val u = min(w, h)
    val ink = surfaceColor("#273E43", 1f, light)



            fill(lobed(w*.15f,h*1.025f,w*.45f,u*.07f,314,6,.07f),c("#DDE9EE"))
            fill(lobed(w*.85f,h*1.04f,w*.55f,u*.08f,315,6,.07f),c("#F5F4ED"))
            for(i in 0..5)drawOval(c("#91A8BB",.26f),Offset(w*(.42f+i*.012f),h*(.93f+i*.01f)),Size(u*.009f,u*.017f))

}
