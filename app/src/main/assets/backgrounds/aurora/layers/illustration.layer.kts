private fun DrawScope.freeAurora(fp:FreePal,t:Float) {
    drawRect(Brush.verticalGradient(listOf(fp.base0,fp.base1)))
    scale(size.width,size.height,pivot=Offset.Zero) {
        val drift=sin(t*TAU)*.006f
        val back=Path().apply {
            moveTo(-.12f,.33f);cubicTo(.2f,.2f,.3f,.55f,.6f,.54f)
            cubicTo(.93f,.54f,.79f,.8f,1.2f,.79f);lineTo(1.2f,.91f)
            cubicTo(.7f,.9f,.84f,.64f,.57f,.65f)
            cubicTo(.28f,.65f,.13f,.32f,-.12f,.4f);close()
        }
        translate(0f,drift) {
            translate(0f,.015f){drawPath(back,mix(fp.c[1],fp.ink,.62f))}
            drawPath(back,fp.c[1])
            clipPath(back) {
                val light=Path().apply {moveTo(-.14f,.3f);cubicTo(.22f,.17f,.38f,.54f,.62f,.53f);cubicTo(.9f,.53f,.83f,.8f,1.2f,.78f);lineTo(1.2f,.82f);cubicTo(.8f,.85f,.85f,.59f,.6f,.59f);cubicTo(.3f,.6f,.19f,.26f,-.14f,.37f);close()}
                drawPath(light,mix(fp.c[1],Color.White,.4f))
                drawPath(poly(.61f,.52f,.75f,.6f,.76f,.72f,.59f,.65f),mix(fp.c[1],fp.ink,.35f))
            }
        }
        val front=Path().apply {
            moveTo(-.15f,.57f);cubicTo(.18f,.41f,.48f,.75f,.6f,.55f)
            cubicTo(.72f,.34f,.87f,.38f,1.18f,.19f);lineTo(1.18f,.28f)
            cubicTo(.89f,.46f,.8f,.45f,.69f,.65f)
            cubicTo(.49f,.88f,.13f,.57f,-.15f,.7f);close()
        }
        translate(0f,-drift) {
            translate(.005f,.017f){drawPath(front,mix(fp.c[0],fp.ink,.65f))}
            drawPath(front,fp.c[0])
            clipPath(front) {
                val edge=Path().apply {moveTo(-.15f,.55f);cubicTo(.17f,.4f,.48f,.73f,.6f,.55f);cubicTo(.73f,.34f,.87f,.38f,1.18f,.19f);lineTo(1.18f,.23f);cubicTo(.88f,.42f,.77f,.38f,.64f,.59f);cubicTo(.48f,.8f,.17f,.48f,-.15f,.62f);close()}
                drawPath(edge,mix(fp.c[0],Color.White,.4f))
                val reverse=Path().apply {moveTo(.47f,.68f);cubicTo(.59f,.72f,.66f,.62f,.72f,.51f);cubicTo(.71f,.63f,.68f,.73f,.6f,.77f);cubicTo(.55f,.8f,.5f,.75f,.47f,.68f);close()}
                drawPath(reverse,mix(fp.c[0],fp.ink,.43f))
            }
        }
    }
}
