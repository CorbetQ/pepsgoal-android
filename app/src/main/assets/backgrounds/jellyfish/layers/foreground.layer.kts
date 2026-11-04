private fun DrawScope.jellyfishForeground(t: Float, light: Tod?) {
    val key="jellyfish"

    val w = size.width; val h = size.height; val u = min(w, h)
    val ink = surfaceColor("#273E43", 1f, light)



            for(side in 0..1) {
                val x=if(side==0)w*.03f else w*.97f
                val coral=c(if(key=="deepsea")"#DA7D92" else "#7795C6",.65f)
                for(i in 0..4) {
                    val tip=x+u*(i-2)*.018f;val y=h-u*(.045f+i%3*.022f)
                    line(x,h,tip,y,u*.009f,coral)
                    line(tip,y+u*.02f,tip+u*.016f,y-u*.014f,u*.006f,coral)
                    disc(tip,y,u*.006f,c("#F5B4AA",.7f))
                }
            }

}
