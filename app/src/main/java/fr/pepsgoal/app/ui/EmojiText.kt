package fr.pepsgoal.app.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage

/**
 * Fluent Emoji partout : chaque emoji d'un texte est remplacé, en ligne, par le dessin Fluent de Microsoft
 * (le même que dans la version web), à la taille du texte qui l'entoure.
 */
object FluentEmoji {
    @Volatile private var keys: Set<String> = emptySet()
    private val cache = android.util.LruCache<String, Any>(600)
    private val NONE = Any()

    /** Liste des dessins disponibles (dossier assets/emoji), chargée au démarrage de l'application. */
    fun load(ctx: Context) { keys = ctx.assets.list("emoji")?.map { it.removeSuffix(".webp") }?.toSet() ?: emptySet() }

    // Symboles du plan de base affichés en emoji même sans sélecteur de variante (FE0F)
    private val PRESENTATION = setOf(0x231A, 0x231B, 0x23E9, 0x23EA, 0x23EB, 0x23EC, 0x23F0, 0x23F3, 0x25FD, 0x25FE, 0x2614, 0x2615, 0x267F, 0x2693, 0x26A1, 0x26AA, 0x26AB,
        0x26BD, 0x26BE, 0x26C4, 0x26C5, 0x26CE, 0x26D4, 0x26EA, 0x26F2, 0x26F3, 0x26F5, 0x26FA, 0x26FD, 0x2705, 0x270A, 0x270B, 0x2728, 0x274C, 0x274E, 0x2753, 0x2754,
        0x2755, 0x2757, 0x2795, 0x2796, 0x2797, 0x27B0, 0x27BF, 0x2B1B, 0x2B1C, 0x2B50, 0x2B55) + (0x2648..0x2653)

    /** Clé du dessin Fluent d'un caractère « visuel » (graphème), ou null s'il ne s'agit pas d'un emoji connu. */
    private fun keyOf(g: String): String? {
        val cps = g.codePoints().toArray()
        val first = cps.firstOrNull() ?: return null
        val emojiLike = first >= 0x1F000 || 0xFE0F in cps || 0x20E3 in cps || first in PRESENTATION
        if (!emojiLike) return null
        return emojiKey(g)?.takeIf { it in keys }
    }

    private fun mayHave(s: String) = s.any { Character.isSurrogate(it) || it.code in 0x2190..0x2BFF || it.code == 0xFE0F || it.code == 0x20E3 }

    /** Texte converti (avec emplacements pour les dessins) ou null s'il ne contient aucun emoji. */
    fun convert(text: AnnotatedString): Pair<AnnotatedString, Map<String, InlineTextContent>>? {
        val s = text.text
        if (keys.isEmpty() || !mayHave(s)) return null
        val cacheable = text.spanStyles.isEmpty() && text.paragraphStyles.isEmpty()
        if (cacheable) cache.get(s)?.let { @Suppress("UNCHECKED_CAST") return if (it === NONE) null else it as Pair<AnnotatedString, Map<String, InlineTextContent>> }
        val it = android.icu.text.BreakIterator.getCharacterInstance(); it.setText(s)
        val found = mutableListOf<Triple<Int, Int, String>>()
        var start = it.first(); var end = it.next()
        while (end != android.icu.text.BreakIterator.DONE) {
            val g = s.substring(start, end)
            if (mayHave(g)) keyOf(g)?.let { k -> found.add(Triple(start, end, k)) }
            start = end; end = it.next()
        }
        if (found.isEmpty()) { if (cacheable) cache.put(s, NONE); return null }
        var prev = 0
        val out = buildAnnotatedString {
            for ((a, b, k) in found) {
                if (a > prev) append(text.subSequence(prev, a))
                appendInlineContent("fe:$k", s.substring(a, b))
                prev = b
            }
            if (prev < s.length) append(text.subSequence(prev, s.length))
        }
        val inline = found.map { it.third }.distinct().associate { k ->
            "fe:$k" to InlineTextContent(Placeholder(1.18.em, 1.18.em, PlaceholderVerticalAlign.TextCenter)) {
                AsyncImage("file:///android_asset/emoji/$k.webp", null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        val r = out to inline
        if (cacheable) cache.put(s, r)
        return r
    }
}

/** Texte de l'application : identique au texte Material, avec les emojis remplacés par les Fluent Emoji. */
@Composable
fun Text(
    text0: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null, fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified, overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE, minLines: Int = 1, onTextLayout: ((TextLayoutResult) -> Unit)? = null, style: TextStyle = LocalTextStyle.current
) {
    // Interface en anglais : traduction à l'affichage (I18n.rev relu pour suivre un changement de langue)
    val lang = I18n.rev
    val text = remember(text0, lang) { I18n.tr(text0) }
    val r = remember(text) { FluentEmoji.convert(AnnotatedString(text)) }
    if (r == null) androidx.compose.material3.Text(text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style)
    else androidx.compose.material3.Text(text = r.first, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines, inlineContent = r.second, onTextLayout = onTextLayout ?: {}, style = style)
}

@Composable
fun Text(
    text: AnnotatedString, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null, fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified, overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE, minLines: Int = 1, inlineContent: Map<String, InlineTextContent> = mapOf(), onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val r = remember(text) { FluentEmoji.convert(text) }
    androidx.compose.material3.Text(text = r?.first ?: text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines, inlineContent = if (r != null) inlineContent + r.second else inlineContent, onTextLayout = onTextLayout, style = style)
}
