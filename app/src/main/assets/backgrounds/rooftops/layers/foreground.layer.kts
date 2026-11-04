private fun DrawScope.rooftopsForeground(t: Float, light: Tod?) {
    val key="rooftops"

    val w = size.width; val h = size.height; val u = min(w, h)
    val ink = surfaceColor("#273E43", 1f, light)



            for(i in 0..3)sceneSparkle(w*(.07f+i*.26f),h*(.06f+(i%2)*.08f),u*.006f,c("#E9DCBC",.55f))

}
