private fun DrawScope.valentine(t: Float) {
    val w=size.width;val h=size.height;val unit=min(w,h)
    valentineSky(0f to c("#9478B1"),.42f to c("#EAB4C5"),.72f to c("#FFE1CA"),1f to c("#F8CDCD"))
    glow(w*.74f,h*.28f,unit*.48f,c("#FFF0CE",.48f),BlendMode.Screen)
    disc(w*.74f,h*.28f,unit*.066f,c("#FFF0D3"))
    for(i in 0..2)softCloud(w*(.13f+i*.36f)+sin(t*TAU+i)*unit*.015f,h*(.24f+i*.07f),w*.47f,c("#C99AB8",.48f),c("#FFE9D7",.8f),991+i)
    val noise=Nz(909)
    for(layer in 0..3){val y=h*(.62f+layer*.09f)
        ridgeFill(ridgePts(y,h*(.055f-layer*.007f),60){noise.at(it*2.5f+layer*5f,3)*.5f+.5f},
            vg(y-h*.05f,h,0f to c(listOf("#D3A0B4","#BF7C9C","#AE678B","#934E79")[layer]),1f to c("#5F3E66")))}
    // A winding garden path creates a near/far relationship behind the rising hearts.
    val path=Path().apply{moveTo(w*.49f,h*.68f);cubicTo(w*.85f,h*.77f,w*.14f,h*.83f,w*.22f,h)
        lineTo(w*.72f,h);cubicTo(w*.41f,h*.85f,w*.99f,h*.78f,w*.51f,h*.68f);close()}
    drawPath(path,vg(h*.68f,h,0f to c("#F0C4B8"),1f to c("#D89C98")))
    // Blossoming tree and a small garden bench ground the romantic scene.
    val treeX=w*.14f;val treeY=h*.81f
    line(treeX,treeY,treeX+unit*.015f,treeY-unit*.26f,unit*.018f,c("#654355"))
    crown(treeX,treeY-unit*.3f,unit*.19f,unit*.15f,331,c("#FADBC8"),c("#D996B2"),c("#995C89"),groundY=treeY)
    val bx=w*.76f;val by=h*.835f;val bw=unit*.16f
    for(i in 0..2)line(bx-bw*.5f,by-unit*(.03f+i*.013f),bx+bw*.5f,by-unit*(.03f+i*.013f),unit*.009f,c("#7A4E5D"))
    for(x in listOf(bx-bw*.35f,bx+bw*.35f))line(x,by,x,by-unit*.07f,unit*.005f,c("#503A56"))
    val hearts=Mul(19)
    for(i in 0 until 17){val p=(hearts()+t*(.3f+hearts()*.3f))%1f;val x=w*(.09f+hearts()*.82f)+sin(p*TAU+i)*unit*.025f
        val y=h*(1.04f-p*1.12f);val r=unit*(.018f+hearts()*.037f);val col=c(listOf("#DF7897","#EAA9B7","#C96490")[i%3])
        val shape=heart(x,y,r)
        drawPath(shape,lg(x-r,y-r,x+r,y+r,0f to mix(col,c("#FFF5DA"),.4f),.5f to col,1f to mix(col,c("#713F72"),.45f)))
        clipPath(shape){drawOval(c("#FFEAD8",.6f),Offset(x-r*.65f,y-r*.57f),Size(r*.48f,r*.26f))}
    }
    for(side in 0..1)for(i in 0..4){val x=w*(if(side==0).02f+i*.033f else .98f-i*.033f);val y=h*(.94f+(i%2)*.02f)
        line(x,y,x+unit*.008f,y-unit*.055f,unit*.002f,c("#5E5961"))
        flower(x+unit*.008f,y-unit*.055f,unit*.013f,6,c("#E8B2BC"),c("#F5D59D"))}
}
