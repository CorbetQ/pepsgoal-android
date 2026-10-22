package fr.pepsgoal.core

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/** Caractère de continuation UTF-8 (octets 0x80–0xBF) tel qu'il apparaît une fois décodé en Windows-1252 ou Latin-1. */
private const val CONT = "[\u0080-¿ŒœŠšŸŽžƒˆ˜–—‘-‚“-„†-•…‰‹›€™]"
private val MOJIBAKE = Regex("[Â-ß]$CONT|[à-ï]$CONT{2}|[ð-ô]$CONT{3}")
private val CP1252_HIGH = mapOf(
    '€' to 0x80, '‚' to 0x82, 'ƒ' to 0x83, '„' to 0x84, '…' to 0x85, '†' to 0x86, '‡' to 0x87,
    'ˆ' to 0x88, '‰' to 0x89, 'Š' to 0x8A, '‹' to 0x8B, 'Œ' to 0x8C, 'Ž' to 0x8E, '‘' to 0x91,
    '’' to 0x92, '“' to 0x93, '”' to 0x94, '•' to 0x95, '–' to 0x96, '—' to 0x97, '˜' to 0x98,
    '™' to 0x99, 'š' to 0x9A, '›' to 0x9B, 'œ' to 0x9C, 'ž' to 0x9E, 'Ÿ' to 0x9F
)

/** Redécode une séquence « Ã© » en « é » ; null si ce n'est pas de l'UTF-8 valide. */
private fun redecode(seq: String): String? {
    val bytes = ByteArray(seq.length)
    for (i in seq.indices) bytes[i] = (CP1252_HIGH[seq[i]] ?: seq[i].code.takeIf { it <= 0xFF } ?: return null).toByte()
    return try { StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() } catch (e: Exception) { null }
}

/**
 * Répare le texte UTF-8 décodé à tort en Latin-1 / Windows-1252 (« validÃ© »), même quand il est mêlé à des accents
 * corrects : chaque séquence fautive est redécodée isolément, le reste du texte est laissé tel quel.
 */
fun fixMojibake(s: String): String {
    if (s.none { it in 'Â'..'ô' }) return s
    // « à » (C3 A0) : l'espace insécable finit souvent en espace normale (« Ã  »)
    var cur = s.replace("\u00C3 ", "\u00C3\u00A0")
    // Deux passes : un texte doublement mal décodé (« ÃƒÂ© ») est réparé en entier
    repeat(2) {
        val next = MOJIBAKE.replace(cur) { m -> redecode(m.value) ?: m.value }
        if (next == cur) return cur.replace("\u00C3\u00A0", "\u00C3 ")
        cur = next
    }
    return cur.replace("\u00C3\u00A0", "\u00C3 ")
}
