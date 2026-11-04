/** Summer shares the detailed river architecture with Loire, with its own warm pigments and holiday foreground. */
private fun DrawScope.loiresummer(t: Float, lighting: Tod? = null) {
    loire(t,lighting?:Tod(0f,0f,1f,0f,.68f))
    val w=size.width;val h=size.height;val unit=min(w,h)
    // Striped parasol on the near levee, with fabric volume and its cast shadow.
    val x=w*.83f;val y=h*.91f;val r=unit*.105f
    castShadow(x,y,r*.75f,r*1.3f)
    line(x,y,x,y-unit*.16f,unit*.004f,c("#D2B693"))
    val canopy=Path().apply{moveTo(x-r,y-unit*.14f);quadraticTo(x,y-unit*.24f,x+r,y-unit*.14f)
        quadraticTo(x+r*.5f,y-unit*.16f,x,y-unit*.14f);quadraticTo(x-r*.5f,y-unit*.16f,x-r,y-unit*.14f);close()}
    drawPath(canopy,lg(x-r,y-unit*.23f,x+r,y-unit*.12f,0f to surfaceColor("#FFF1CD",1f,lighting),1f to surfaceColor("#D88A57",1f,lighting)))
    clipPath(canopy){for(i in -2..2)if(i%2==0)fill(poly(x,y-unit*.24f,x+r*i*.48f,y-unit*.13f,x+r*(i+1)*.48f,y-unit*.13f),surfaceColor("#D46D5E",.78f,lighting))}
    fill(poly(w*.66f,h*.956f,w*.81f,h*.946f,w*.88f,h*.977f,w*.69f,h*.987f),surfaceColor("#E6C69F",1f,lighting))
    for(i in 0..3)line(w*(.69f+i*.043f),h*.95f,w*(.72f+i*.043f),h*.98f,unit*.006f,surfaceColor("#759EAD",.85f,lighting))
}
