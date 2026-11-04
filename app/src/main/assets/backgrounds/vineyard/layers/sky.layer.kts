// Sky primitives belonging to vineyard; shared lighting math is in _shared.
private fun DrawScope.vineyardSky(vararg stops: Pair<Float, Color>, y0: Float=0f, y1: Float=size.height) = sky(*stops,y0=y0,y1=y1)
private fun DrawScope.vineyardLiveSky(d: Tod,t: Float,horizon: Float) = cartoonSky(d,t,horizon,"vineyard")
