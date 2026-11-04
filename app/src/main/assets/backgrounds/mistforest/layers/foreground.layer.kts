private fun DrawScope.mistforestForeground(t: Float, light: Tod?) {
    val key="mistforest"

    val w = size.width; val h = size.height; val u = min(w, h)
    val ink = surfaceColor("#273E43", 1f, light)



            sceneFern(t,w*.025f,h*1.01f,1f,ink)
            sceneFern(t,w*.98f,h*1.025f,-1f,surfaceColor(if(key=="autumnwood")"#8A482E" else "#396657",1f,light))

}
