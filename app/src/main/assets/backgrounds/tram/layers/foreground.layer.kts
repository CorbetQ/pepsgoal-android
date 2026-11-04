private fun DrawScope.tramForeground(t: Float, light: Tod?) {
    val key="tram"

    val w = size.width; val h = size.height; val u = min(w, h)
    val ink = surfaceColor("#273E43", 1f, light)



            for(side in 0..1){val x=if(side==0)w*.015f else w*.97f;val y=h*.99f
                rect(x-u*.02f,y-u*.035f,u*.06f,u*.05f,surfaceColor("#A77966",1f,light))
                for(i in 0..3){val xx=x+u*(i-1)*.012f;line(xx,y-u*.025f,xx,y-u*.07f,u*.002f,ink)
                    flower(xx,y-u*.07f,u*.007f,5,surfaceColor("#F3B6A8",1f,light),surfaceColor("#E4B861",1f,light))}}

}
