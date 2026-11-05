/** Alpine amphitheatre: large faceted peaks, glacial lake and a dark spruce foreground. */
private fun DrawScope.mountains(t: Float, lighting: Tod? = null) {
    fun c(hex: String,alpha: Float=1f)=surfaceColor(hex,alpha,lighting)
    val w=size.width;val h=size.height;val u=min(w,h)
    val light=lighting?:Tod(0f,0f,1f,0f,.32f)
    mountainsLiveSky(light,t,h*.69f)
    // Far chain behind the two main peaks, with cooler atmospheric shadows.
    ridgeFill(floatArrayOf(-w*.05f,h*.59f,w*.15f,h*.39f,w*.3f,h*.53f,w*.46f,h*.36f,w*.67f,h*.53f,w*.87f,h*.35f,w*1.05f,h*.57f),
        vg(h*.35f,h*.78f,0f to c("#859DC6"),1f to c("#C0B0C7")))
    val leftLit=light.sun<.5f
    val warm=c("#F8D7BA");val cool=c("#6C72A7");val deep=c("#353B75")
    val left=if(leftLit)warm else cool;val right=if(leftLit)cool else warm
    // Tall near summit with broad sun-facing facets and narrow blue fracture planes.
    val px=w*.52f;val py=h*.245f
    fill(poly(-w*.15f,h*.79f,w*.17f,h*.53f,px,py,w*.87f,h*.6f,w*1.15f,h*.82f),
        vg(py,h*.82f,0f to c("#E0C7BD"),.5f to c("#9284AA"),1f to deep))
    fill(poly(px,py,w*.17f,h*.53f,-w*.15f,h*.79f,w*.39f,h*.7f,w*.46f,h*.46f),
        lg(px,py,w*.1f,h*.77f,0f to mix(left,c("#FFFFFF"),.18f),1f to mix(left,deep,.42f)))
    fill(poly(px,py,w*.57f,h*.47f,w*.78f,h*.74f,w*1.15f,h*.82f,w*.87f,h*.6f),
        lg(px,py,w*.94f,h*.8f,0f to right,1f to mix(right,deep,.48f)))
    fill(poly(px,py,w*.46f,h*.46f,w*.39f,h*.7f,w*.59f,h*.59f,w*.57f,h*.47f),deep.copy(alpha=.65f))
    // Snowfields follow the ridge and break into fingers on the rock faces.
    fill(poly(px,py,w*.41f,h*.386f,w*.46f,h*.365f,w*.47f,h*.405f,w*.5f,h*.36f,w*.55f,h*.4f,w*.57f,h*.36f,w*.65f,h*.41f),
        vg(py,h*.42f,0f to c("#FFF5E7"),1f to c("#B9D6EE")))
    line(px,py,w*.43f,h*.39f,u*.004f,c("#FFF4DF",.7f))
    // Secondary peak creates a layered, asymmetric skyline.
    fill(poly(w*.7f,h*.73f,w*.84f,h*.425f,w*1.12f,h*.8f),c("#6378A6"))
    fill(poly(w*.84f,h*.425f,w*.87f,h*.66f,w*1.12f,h*.8f),c("#394E7D"))
    fill(poly(w*.84f,h*.425f,w*.795f,h*.52f,w*.83f,h*.49f,w*.86f,h*.53f,w*.9f,h*.52f),c("#DBE8EE"))
    // Sparse rock strata catch the grazing light rather than a uniform grain wash.
    for(i in 0 until 22){val f=i/22f;val y=h*(.49f+f*.2f);val x=px-w*(.09f+f*.27f)
        line(x,y,x+w*(.025f+f*.08f),y-h*.012f,max(1f,u*.0018f),mix(left,deep,.45f).copy(alpha=.45f))}
    mist(h*.735f,h*.055f,c("#B8CBDD",.2f))
    val lake=h*.755f
    rect(0f,lake,w,h-lake,c("#225E83"))
    drawRect(vg(lake,h,0f to c("#6DA3C0"),.32f to c("#4387A9"),1f to c("#174B70")),Offset(0f,lake),Size(w,h-lake))
    // Broken reflections, shaped like the mirrored mountain and interrupted by the breeze.
    for(i in 0 until 32){val f=i/32f;val y=lake+h*.22f*f;val half=w*(.3f*(1-f)+.025f)
        val ripple=sin(t*TAU*2+i*.7f)*u*.012f
        line(px-half+ripple,y,px+half*.7f+ripple,y,max(1f,u*.0028f),c(if(i%4==0)"#F2DECD" else "#A8BED1",.11f+(1-f)*.16f))}
    // Spruces frame the lake; the middle remains open for the vista.
    conifers(8,910,h*.07f,h*.17f,c("#142F43"),{h*.94f},-w*.04f,w*.2f)
    conifers(7,911,h*.06f,h*.13f,c("#193B50"),{h*.96f},w*.82f,w*1.06f)
    ridgeFill(floatArrayOf(0f,h*.955f,w*.15f,h*.975f,w*.32f,h*.97f,w*.58f,h*.99f,w*.85f,h*.97f,w,h*.955f),c("#203C4B"))
}
