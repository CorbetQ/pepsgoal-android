private fun DrawScope.fireworksForeground(t: Float, light: Tod?) {
    val key="fireworks"

    val w = size.width; val h = size.height; val u = min(w, h)
    val ink = surfaceColor("#273E43", 1f, light)



            val color=c(if(key=="heartsky"||key=="valentine")"#FFE5B6" else "#D9E8EA",.7f)
            for(i in 0..5)sceneSparkle(w*(.08f+(i*.173f)% .9f),h*(.06f+(i*.113f)%.4f),u*(.005f+i%2*.004f),color)

}
