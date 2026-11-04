private val TUN: FloatArray = run {
    val pts = ArrayList<Float>()
    pts += listOf(1.2f, 0f, -3.6f, 0f, -3.6f, 3.3f)
    for (i in 1 until 12) { val th = 3.1416f * (1f - i / 12f); pts += listOf(-.5f + 3.1f * cos(th), 3.3f + 1.3f * sin(th)) }
    pts += listOf(2.6f, 3.3f, 2.6f, .9f, 1.2f, .9f)
    pts.toFloatArray()
}
