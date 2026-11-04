private fun DrawScope.freeBlob(fp:FreePal,t:Float) {
    drawRect(Brush.verticalGradient(listOf(fp.base0,fp.base1)))
    val drift=sin(t*TAU)*.004f
    scale(size.width,size.height,pivot=Offset.Zero) {
        fun sculpture(shape:Path,col:Color,shade:Path,shine:Path,depth:Float) {
            translate(.004f,depth){drawPath(shape,mix(col,fp.ink,.62f))}
            drawPath(shape,col)
            clipPath(shape) {
                drawPath(shine,mix(col,Color.White,.35f))
                drawPath(shade,mix(col,fp.ink,.38f))
            }
        }
        translate(0f,drift) {
            val far=Path().apply {moveTo(.62f,.22f);cubicTo(.57f,.16f,.72f,.13f,.76f,.18f);cubicTo(.88f,.15f,.94f,.24f,.84f,.28f);cubicTo(.75f,.33f,.66f,.28f,.62f,.22f);close()}
            sculpture(far,fp.c[2],
                Path().apply {moveTo(.59f,.22f);cubicTo(.7f,.26f,.83f,.25f,.9f,.2f);lineTo(.95f,.34f);lineTo(.58f,.34f);close()},
                Path().apply {moveTo(.58f,.1f);lineTo(.88f,.1f);lineTo(.85f,.2f);cubicTo(.76f,.25f,.65f,.17f,.58f,.23f);close()},.012f)
        }
        val middle=Path().apply {
            moveTo(-.1f,.49f);cubicTo(-.04f,.37f,.17f,.36f,.31f,.41f)
            cubicTo(.51f,.41f,.58f,.53f,.39f,.56f)
            cubicTo(.32f,.61f,.45f,.67f,.27f,.7f)
            cubicTo(.06f,.75f,-.12f,.67f,-.1f,.49f);close()
        }
        sculpture(middle,fp.c[1],
            Path().apply {moveTo(-.12f,.55f);cubicTo(.14f,.7f,.25f,.53f,.5f,.51f);lineTo(.55f,.78f);lineTo(-.15f,.78f);close()},
            Path().apply {moveTo(-.2f,.28f);lineTo(.6f,.28f);lineTo(.58f,.44f);cubicTo(.27f,.41f,.23f,.57f,-.2f,.51f);close()},.018f)
        val near=Path().apply {
            moveTo(.28f,.76f);cubicTo(.2f,.69f,.32f,.62f,.5f,.68f)
            cubicTo(.61f,.57f,.83f,.62f,.88f,.7f)
            cubicTo(1.16f,.67f,1.17f,.9f,.94f,.95f)
            cubicTo(.72f,1f,.4f,.96f,.28f,.88f)
            cubicTo(.23f,.84f,.22f,.79f,.28f,.76f);close()
        }
        sculpture(near,fp.c[0],
            Path().apply {moveTo(.21f,.82f);cubicTo(.43f,.8f,.64f,.93f,1.12f,.77f);lineTo(1.2f,1.06f);lineTo(.18f,1.06f);close()},
            Path().apply {moveTo(.2f,.57f);lineTo(1.1f,.57f);lineTo(1.08f,.73f);cubicTo(.85f,.71f,.76f,.82f,.57f,.77f);cubicTo(.35f,.71f,.37f,.82f,.2f,.78f);close()},.022f)
    }
}
