private fun DrawScope.rainwindow(t: Float, environment: fr.pepsgoal.core.SceneEnvironment? = null) {
    val W = size.width; val H = size.height; val S = min(W, H)
    rainwindowSky(0f to c("#1E2434"), .5f to c("#2E3850"), 1f to c("#465676"))
    val live = environment
    if (live != null) {
        val shade = when (live.condition) { "storm" -> "#222C44"; "fog" -> "#8B9CA7"; "clouds", "rain", "snow" -> "#586D83"; else -> "#465676" }
        drawRect(c(shade, .65f * live.freshness))
    }
    // Tall blue silhouettes and warm windows form an actual street beyond the glass.
    val city=Mul(47)
    for(layer in 0..1) {
        var x=-S*.06f
        while(x<W) {
            val bw=S*(.06f+city()*.13f);val top=H*(.3f+city()*.34f+layer*.15f)
            rect(x,top,bw,H-top,c(if(layer==0)"#283C61" else "#142E4B",.84f))
            for(row in 0 until 9)for(col in 0 until 3) {
                val wx=x+bw*(.15f+col*.25f);val wy=top+S*.018f+row*S*.027f
                if(city()>.32f)rect(wx,wy,bw*.12f,S*.011f,c(if((row+col)%4==0)"#66D5E5" else "#FFC77E",.25f+city()*.5f))
            }
            x+=bw+S*.012f
        }
    }
    // Soft luminous pools behind the rain; the frame and droplets stay crisp.
    val r=Mul(3)
    for(i in 0 until 22) {
        val x=r()*W;val y=H*(.48f+r()*.45f);val rr=S*(.02f+r()*.055f)
        val col=c(if(i%3==0)"#64DBE9" else "#FFC17C")
        glow(x,y,rr*2.6f,col.copy(alpha=.38f),BlendMode.Screen)
        disc(x,y,rr*.36f,col.copy(alpha=.76f))
        drawLine(Brush.verticalGradient(listOf(col.copy(alpha=0f),col.copy(alpha=.38f)),y-rr*3f,y+rr*4f),
            Offset(x,y-rr*3f),Offset(x,y+rr*4f),rr*.34f,cap=StrokeCap.Round)
    }
    // Oblique glass sheen and condensation add texture without blurring the whole scene.
    fill(poly(W*.03f,0f,W*.12f,0f,W*.72f,H*.96f,W*.65f,H*.96f),c("#A1C9DC",.055f))
    drawRect(vg(H*.7f,H,0f to Color.Transparent,1f to c("#8DB5CB",.18f)))
    if(live!=null && live.condition in setOf("snow","fog"))drawSceneWeather(live.condition,live.clouds,live.wind,live.freshness,live.night,t,36)
    // Gouttes immobiles (réfraction : bord sombre, reflet clair en haut)
    val d = Mul(8)
    fun drop(x: Float, y: Float, rr: Float, elong: Float = 1f) {
        drawOval(Brush.verticalGradient(0f to c("#0E1420", .55f), .6f to c("#5E6E8E", .25f), 1f to c("#DDE6FF", .5f), startY = y - rr * elong, endY = y + rr * elong), topLeft = Offset(x - rr, y - rr * elong), size = Size(rr * 2, rr * 2 * elong))
        disc(x - rr * .3f, y - rr * elong * .45f, rr * .22f, c("#FFFFFF", .7f * (live?.freshness ?: 1f)))
    }
    val wet = live == null || live.condition in setOf("rain", "storm")
    val wetness = if (wet) live?.freshness ?: 1f else 0f
    for (i in 0 until (80 * wetness).toInt()) drop(d() * W, d() * H, S * (.003f + d().pow(3f) * .012f), 1f + d() * .2f)
    // Gouttes qui glissent et leur traînée
    for (i in 0 until (9 * wetness).toInt()) {
        val x = W * (.06f + i * .11f + d() * .04f); val sp = 1f + (i % 3); val u = (d() + t * sp) % 1f; val y = -H * .05f + u * H * 1.1f; val rr = S * (.009f + d() * .007f)
        val wob = sin(u * 30f + i) * rr * .3f
        drawLine(Brush.verticalGradient(0f to c("#C9D6EA", 0f), 1f to c("#C9D6EA", .3f), startY = y - H * .25f, endY = y), Offset(x + wob * .5f, y - H * .25f), Offset(x + wob, y), strokeWidth = rr * .5f, cap = StrokeCap.Round)
        for (k in 1..5) drop(x + sin(k * 2.1f + i) * rr * .3f, y - k * H * .045f, rr * .3f)
        drop(x + wob, y, rr, 1.25f)
    }
    val frame = c("#242B3B")
    rect(0f,0f,W*.04f,H,frame);rect(W*.96f,0f,W*.04f,H,frame)
    rect(W*.03f,0f,W*.01f,H,c("#7099B5",.55f));rect(W*.96f,0f,W*.007f,H,c("#07172A"))
    rect(0f,H*.96f,W,H*.04f,frame)
    rect(0f,H*.96f,W,H*.009f,c("#B5967E"))
    glow(W*.8f,H,S*.7f,c("#F6AF70",.2f),BlendMode.Screen)

}
