// Sky primitives belonging to jellyfish; shared lighting math is in _shared.
private fun DrawScope.jellyfishSky(vararg stops: Pair<Float, Color>, y0: Float=0f, y1: Float=size.height) = sky(*stops,y0=y0,y1=y1)
private fun DrawScope.jellyfishLiveSky(d: Tod,t: Float,horizon: Float) = cartoonSky(d,t,horizon,"jellyfish")
