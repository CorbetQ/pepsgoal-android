private fun DrawScope.hauntedForeground(t: Float, light: Tod?) {
    val key="haunted"

    val w = size.width; val h = size.height; val u = min(w, h)
    val ink = surfaceColor("#273E43", 1f, light)



            for(i in 0..7) {
                val x=w*((i*.173f+t*.02f)%.95f);val y=h*(.78f+(i%3)*.05f)
                disc(x,y,u*.0025f,c("#F8CE79",.65f))
            }

}
