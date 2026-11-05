private fun DrawScope.freeMesh(fp:FreePal,t:Float) {
    drawRect(Brush.verticalGradient(listOf(fp.base0,fp.base1)))
    scale(size.width,size.height,pivot=Offset.Zero) {
        val col=fp.c[0];val second=fp.c[1];val shade=mix(col,fp.ink,.5f)
        val support=Path().apply {moveTo(-.1f,.54f);cubicTo(.2f,.38f,.6f,.52f,1.1f,.27f);lineTo(1.1f,1.1f);lineTo(-.1f,1.1f);close()}
        drawPath(support,col)
        val left=Path().apply {moveTo(.12f,.42f);cubicTo(-.04f,.61f,.22f,.88f,.1f,1.1f);lineTo(.42f,1.1f);cubicTo(.52f,.8f,.21f,.55f,.12f,.42f);close()}
        drawPath(left,second)
        val leftFace=Path().apply {moveTo(.12f,.42f);cubicTo(.04f,.61f,.3f,.86f,.22f,1.1f);lineTo(.12f,1.1f);cubicTo(.23f,.86f,-.02f,.61f,.12f,.42f);close()}
        drawPath(leftFace,mix(second,Color.White,.35f))
        val right=Path().apply {moveTo(.75f,.38f);cubicTo(.98f,.58f,.66f,.85f,1.1f,.98f);lineTo(1.1f,1.1f);lineTo(.63f,1.1f);cubicTo(.63f,.84f,.75f,.58f,.75f,.38f);close()}
        drawPath(right,fp.c[2])
        val rightValley=Path().apply {moveTo(.75f,.38f);cubicTo(.92f,.64f,.6f,.94f,1.1f,1.02f);lineTo(1.1f,1.1f);cubicTo(.47f,.96f,.85f,.61f,.75f,.38f);close()}
        drawPath(rightValley,mix(fp.c[2],fp.ink,.45f))
        val central=Path().apply {moveTo(.32f,.3f);cubicTo(.17f,.52f,.5f,.64f,.47f,.87f);lineTo(.47f,1.1f);lineTo(.92f,1.1f);cubicTo(1.01f,.62f,.37f,.58f,.32f,.3f);close()}
        translate(-.015f,.015f){drawPath(central,shade)}
        drawPath(central,col)
        val crest=Path().apply {moveTo(.32f,.3f);cubicTo(.23f,.51f,.61f,.68f,.57f,1.1f);lineTo(.48f,1.1f);cubicTo(.52f,.68f,.18f,.53f,.32f,.3f);close()}
        drawPath(crest,mix(col,Color.White,.4f))
        val valley=Path().apply {moveTo(.38f,.5f);cubicTo(.55f,.64f,.89f,.65f,.8f,1.1f);lineTo(.93f,1.1f);cubicTo(1.02f,.67f,.52f,.59f,.38f,.5f);close()}
        drawPath(valley,shade)
        val lip=Path().apply {moveTo(.36f,.86f);cubicTo(.58f,.78f,.98f,.78f,1.1f,.96f);lineTo(1.1f,1.1f);cubicTo(.83f,1.04f,.57f,1.05f,.5f,.96f);cubicTo(.46f,.92f,.41f,.88f,.36f,.86f);close()}
        translate(0f,.026f){drawPath(lip,mix(second,fp.ink,.65f))}
        drawPath(lip,second)
        clipPath(lip) {
            val top=Path().apply {moveTo(.34f,.83f);cubicTo(.58f,.76f,1.01f,.77f,1.14f,.94f);lineTo(1.14f,.98f);cubicTo(.97f,.85f,.66f,.83f,.46f,.91f);close()}
            drawPath(top,mix(second,Color.White,.38f))
            val underside=Path().apply {moveTo(.46f,.93f);cubicTo(.71f,.89f,.96f,.95f,1.14f,1.05f);lineTo(1.14f,1.13f);lineTo(.43f,1.13f);close()}
            drawPath(underside,mix(second,fp.ink,.4f))
        }
    }
}
