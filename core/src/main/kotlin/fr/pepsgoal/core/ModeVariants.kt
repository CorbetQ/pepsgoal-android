package fr.pepsgoal.core

/** Ambiances fixes : clubs et packs personnels (« @… »), qui gardent leurs propres couleurs. */
val ShopMode.followsDayNight: Boolean get() = !key.startsWith("@") && key !in setOf("sco", "srfc", "psg", "fcb", "fcn")

/** Resolve both daylight variants without changing the owned ambiance key or its price. */
fun ShopMode.forDark(targetDark: Boolean): ShopMode {
    if (!followsDayNight || targetDark == dark) return this
    fun tint(hex: String, white: Boolean, amount: Double): String {
        val rgb = hex.removePrefix("#").chunked(2).map { it.toInt(16) }
        return "#" + rgb.joinToString("") { "%02X".format((it + ((if (white) 255 else 0) - it) * amount).toInt()) }
    }
    val seed = if (dark) bg else blobs.firstOrNull() ?: bg
    return ShopMode(key, name, desc, price, targetDark,
        tint(seed, !targetDark, if (targetDark) .9 else .94),
        if (targetDark) "#FFF7F0" else "#2A1420",
        tint(seed, !targetDark, if (targetDark) .82 else .98), blobs, accent,
        surface = tint(seed, !targetDark, if (targetDark) .82 else .98),
        surface2 = tint(seed, !targetDark, if (targetDark) .72 else .88),
        edge = tint(seed, !targetDark, if (targetDark) .55 else .70))
}
