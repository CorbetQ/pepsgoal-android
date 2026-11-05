// Sky primitives belonging to snowpeak; shared lighting math is in _shared.
private fun DrawScope.snowpeakSky(vararg stops: Pair<Float, Color>, y0: Float=0f, y1: Float=size.height) = sky(*stops,y0=y0,y1=y1)
private fun DrawScope.snowpeakLiveSky(d: Tod,t: Float,horizon: Float) = cartoonSky(d,t,horizon,"snowpeak")
