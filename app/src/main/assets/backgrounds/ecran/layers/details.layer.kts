private val SEG7 = mapOf('0' to "abcdef", 'O' to "abcdef", '4' to "bcfg", '9' to "abcdfg", 'L' to "def", 'S' to "acdfg", 'r' to "eg", 'F' to "aefg", 'C' to "adef")

/** The score follows the painted blank panel, including the master image's crop and perspective. */
internal fun DrawScope.ecranArtworkScore(left:Float,top:Float,width:Float,height:Float,t:Float) {
    val x=left+width*.405f;val y=top+height*.525f
    val w=width*.225f;val h=height*.042f
    val ch=min(w*.88f/4.8f,h/3.1f)
    rotate(3f,Offset(x,y)){scoreBoard2(x,y,w,h,ch,t)}
}

private fun DrawScope.seg7(ch: Char, x: Float, y: Float, w: Float, h: Float, on: Color, off: Color) {
    val k = max(1.5f, w * .16f); val m = SEG7[ch] ?: ""
    fun s(id: Char, x1: Float, y1: Float, x2: Float, y2: Float) = line(x1, y1, x2, y2, k, if (id in m) on else off)
    val r = x + w; val mid = y + h / 2; val b = y + h; val g = k * .9f
    s('a', x + g, y, r - g, y); s('b', r, y + g, r, mid - g); s('c', r, mid + g, r, b - g)
    s('d', x + g, b, r - g, b); s('e', x, mid + g, x, b - g); s('f', x, y + g, x, mid - g); s('g', x + g, mid, r - g, mid)
}

private fun DrawScope.scoreBoard2(x: Float, y: Float, w: Float, h: Float, ch: Float, t: Float) {
    val fr = ch * .14f
    rect(x - fr, y - fr, w + fr * 2, h + fr * 2, c("#3A3640")); rect(x - fr, y + h + fr * .5f, w + fr * 2, fr * .5f, c("#1E1B22"))
    rect(x, y, w, h, c("#050304"))
    val pitch = ch / 5f
    var gy = y + pitch / 2
    while (gy < y + h) { var gx = x + pitch / 2; while (gx < x + w) { disc(gx, gy, pitch * .16f, c("#1A1113")); gx += pitch }; gy += pitch }
    val off = c("#22171A"); val amber = c("#FFB000"); val cw = ch * .55f; val step = cw * 1.55f
    val left = x + w * .06f; val right = x + w * .94f - cw
    val r1 = y + ch * .35f; val r2 = r1 + ch * 1.4f
    var cx = left; for (c0 in "OL") { seg7(c0, cx, r1, cw, ch, amber, off); cx += step }
    seg7('4', right, r1, cw, ch, c("#FFFFFF"), off)
    rect(x + w * .05f, r1 + ch * 1.18f, w * .9f, max(1.5f, ch * .05f), c("#2A1E22"))
    cx = left; for (c0 in "SrFC") { seg7(c0, cx, r2, cw, ch, amber, off); cx += step }
    seg7('0', right, r2, cw, ch, if ((t * 20f).toInt() % 4 == 0) off else c("#FF3B3B"), off)
}

private fun DrawScope.scoreBoard(x: Float, y: Float, w: Float, h: Float, t: Float, frame: Boolean) {
    if (frame) { val fr = h * .1f; rect(x - fr, y - fr, w + fr * 2, h + fr * 2, c("#3A3640")); rect(x - fr, y + h + fr * .5f, w + fr * 2, fr * .5f, c("#1E1B22")) }
    rect(x, y, w, h, c("#050304"))
    val pitch = h / 7f
    var gy = y + pitch / 2
    while (gy < y + h) { var gx = x + pitch / 2; while (gx < x + w) { disc(gx, gy, pitch * .16f, c("#1A1113")); gx += pitch }; gy += pitch }
    val off = c("#22171A"); val amber = c("#FFB000"); val white = c("#FFFFFF"); val red = c("#FF3B3B")
    // Taille des caractères : la plus grande qui tient (11 cases de 1,55 largeur)
    val ch = min(h * .64f, w * .9f / (17f * .55f)); val cw = ch * .55f; val step = cw * 1.55f
    var cx = x + (w - step * 11f + cw * .55f) / 2; val cy = y + (h - ch) / 2
    for (c0 in "OL 4-0 SrFC") {
        when {
            c0 == ' ' -> {}
            c0 == '-' -> line(cx + cw * .2f, cy + ch / 2, cx + cw * .8f, cy + ch / 2, max(1.5f, cw * .16f), white)
            c0 == '4' -> seg7(c0, cx, cy, cw, ch, white, off)
            c0 == '0' -> seg7(c0, cx, cy, cw, ch, if ((t * 20f).toInt() % 4 == 0) off else red, off)
            else -> seg7(c0, cx, cy, cw, ch, amber, off)
        }
        cx += step
    }
}
