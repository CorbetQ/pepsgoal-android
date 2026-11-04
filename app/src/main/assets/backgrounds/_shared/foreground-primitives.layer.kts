private fun DrawScope.sceneSparkle(x: Float, y: Float, r: Float, color: Color) {
        fill(poly(x, y-r, x+r*.23f, y-r*.23f, x+r, y, x+r*.23f, y+r*.23f,
            x, y+r, x-r*.23f, y+r*.23f, x-r, y, x-r*.23f, y-r*.23f), color)
    }

private fun DrawScope.sceneFern(t: Float, x: Float, y: Float, direction: Float, color: Color) {
        val w=size.width;val u=min(w,size.height)
        val sway = sin(t * TAU + x / w * 3) * .025f
        for (branch in 0 until 3) {
            val reach = u * (.085f + branch * .025f) * direction
            val top = y - u * (.12f + branch * .045f)
            val tipX = x + reach * (1 + sway)
            line(x, y, tipX, top, u*.003f, color)
            for (i in 1..5) {
                val f = i / 6f; val bx = x + (tipX-x)*f; val by = y + (top-y)*f
                val len = u*.026f*(1-f*.6f)
                fill(poly(bx,by,bx-len,by-len*.1f,bx-len*.45f,by-len*.5f,bx+u*.002f,by-u*.004f),color)
                fill(poly(bx,by,bx+len*.9f,by-len*.5f,bx+len*.3f,by-len*.65f,bx-u*.002f,by-u*.004f),color)
            }
        }
    }
