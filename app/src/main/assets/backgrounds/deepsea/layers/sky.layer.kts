// Sky primitives belonging to deepsea; shared lighting math is in _shared.
private fun DrawScope.deepseaSky(vararg stops: Pair<Float, Color>, y0: Float=0f, y1: Float=size.height) = sky(*stops,y0=y0,y1=y1)
private fun DrawScope.deepseaLiveSky(d: Tod,t: Float,horizon: Float) = cartoonSky(d,t,horizon,"deepsea")
