package fr.pepsgoal.core

/**
 * Emojis dans un texte : découpage en graphèmes (séquences avec ZWJ, variantes, couleurs de peau, drapeaux, touches),
 * détection des messages faits uniquement d'emojis (affichés en grand, sans bulle) et extraction (pluie d'emojis).
 */
object Emo {
    private fun joins(cp: Int) = cp == 0x200D || cp == 0xFE0F || cp == 0xFE0E || cp == 0x20E3 || cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F
    private fun regional(cp: Int) = cp in 0x1F1E6..0x1F1FF

    /** Graphèmes du texte (approximation suffisante pour les emojis). */
    fun clusters(s: String): List<String> {
        val out = mutableListOf<String>(); val cur = StringBuilder(); var prevZwj = false; var ri = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i); val n = Character.charCount(cp)
            val glue = cur.isNotEmpty() && (joins(cp) || prevZwj || (regional(cp) && ri % 2 == 1))
            if (!glue && cur.isNotEmpty()) { out += cur.toString(); cur.setLength(0); ri = 0 }
            cur.appendCodePoint(cp); prevZwj = cp == 0x200D; if (regional(cp)) ri++
            i += n
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    /** Le graphème est un emoji (pictogramme, drapeau ou touche « 1️⃣ »). */
    fun isEmoji(g: String): Boolean {
        if (g.isEmpty()) return false
        val cp = g.codePointAt(0)
        if (g.contains('\u20E3')) return true
        if (regional(cp)) return true
        if (cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF || cp in 0x2300..0x23FF) return true
        if (cp in 0x2190..0x21FF || cp == 0x00A9 || cp == 0x00AE || cp == 0x203C || cp == 0x2049 || cp == 0x2122 || cp == 0x2139 || cp == 0x3030 || cp == 0x303D) return g.contains('\uFE0F')
        return false
    }

    /** Emojis seuls (1 à [max], espaces permis), ou null si le texte contient autre chose. */
    fun only(s: String, max: Int = 4): List<String>? {
        val gs = clusters(s.trim()).filter { it.isNotBlank() }
        if (gs.isEmpty() || gs.size > max || !gs.all(::isEmoji)) return null
        return gs
    }

    /** Emojis présents dans le texte, dans l'ordre. */
    fun extract(s: String): List<String> = clusters(s).filter(::isEmoji)
}
