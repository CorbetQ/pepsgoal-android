private fun DrawScope.vineyardForeground(t: Float, light: Tod?) {
    val key="vineyard"

    val w = size.width; val h = size.height; val u = min(w, h)
    val ink = surfaceColor("#273E43", 1f, light)



            for(side in 0..1) {
                val x=if(side==0)w*.025f else w*.98f;val y=h*.995f
                sceneFern(t,x,y,if(side==0)1f else -1f,surfaceColor("#496C47",.9f,light))
                if(key in setOf("flowerfield","eggmeadow","angevine")) {
                    for(i in 0..2)flower(x+u*(if(side==0)1 else -1)*(.025f+i*.025f),y-u*(.08f+i*.025f),u*.012f,5,
                        surfaceColor(if(i%2==0)"#F5C8C1" else "#F3D994",1f,light),surfaceColor("#D6AA54",1f,light))
                }
            }

}
