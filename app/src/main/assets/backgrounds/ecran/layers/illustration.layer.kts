private fun DrawScope.ecran(t: Float) {
    stadiumBowl(t,StadiumPalette("#953D4D","#677789",wet=true,quiet=true))
    val w=size.width;val h=size.height;val unit=min(w,h)
    // Preserve the original secret's OL 4 / SRFC 0 scoreboard and reserved top-right position.
    val sw=w*.34f;val ch=sw*.88f/4.8f;val sh=ch*3.1f;val x=w*.61f;val y=h*.075f
    for(px in listOf(x+sw*.22f,x+sw*.78f)){
        line(px,y+sh,px,h*.36f,unit*.009f,c("#253342"))
        line(px-unit*.01f,y+sh,px+unit*.01f,h*.36f,unit*.002f,c("#85909A"))
    }
    glow(x+sw*.5f,y+sh*.5f,sw*.9f,c("#E38569",.15f),BlendMode.Screen)
    scoreBoard2(x,y,sw,sh,ch,t)
    // A subtle repeated score on the LED boards, with its reflection in the wet apron.
    scoreBoard(w*.14f,h*.578f,w*.72f,h*.035f,t,frame=false)
    val rain=Mul(7)
    for(i in 0 until 52){val px=rain()*w;val py=((rain()+t*(4f+rain()*2f))%1f)*h
        line(px,py,px-unit*.005f,py+unit*.025f,unit*.0013f,c("#B7CCD7",.27f))}
}
