package fr.pepsgoal.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import fr.pepsgoal.core.Lang
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/**
 * Interface en anglais : l'application est écrite en français, et chaque texte affiché est traduit à la volée grâce à
 * un dictionnaire (assets/i18n/en.json). Les textes fixes sont cherchés tels quels ; les textes à variables
 * (« Encore 3 flammes… ») passent par des gabarits dont les valeurs sont elles-mêmes traduites si besoin.
 */
object I18n {
    /** Change quand la langue change : les textes se redessinent. */
    var rev by mutableIntStateOf(0)
    val en get() = Lang.en

    private var exact: Map<String, String> = emptyMap()
    private class T(val re: Regex, val out: String)
    private var byWord: Map<String, List<T>> = emptyMap()
    private var loose: List<T> = emptyList()
    private val cache = object : LinkedHashMap<String, String>(512, .75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, String>?) = size > 4000 }
    private val PH = Regex("\\{(\\d+)\\}")

    /** Choix : « auto » (langue du téléphone), « fr » ou « en ». */
    fun apply(ctx: Context, choice: String) {
        val on = when (choice) { "en" -> true; "fr" -> false; else -> Locale.getDefault().language != "fr" }
        if (on && exact.isEmpty()) load(ctx)
        if (Lang.en != on) { Lang.en = on; synchronized(cache) { cache.clear() }; rev++ }
    }

    private fun load(ctx: Context) {
        try {
            val o = Json.parseToJsonElement(ctx.assets.open("i18n/en.json").bufferedReader().readText()).jsonObject
            exact = (o["exact"] as JsonObject).mapValues { it.value.jsonPrimitive.content }; upperIndex = null
            val words = HashMap<String, MutableList<T>>(); val lo = mutableListOf<T>()
            for (e in (o["tmpl"] as JsonArray)) {
                val (fr, en) = e.jsonArray.map { it.jsonPrimitive.content }
                // Gabarit → expression : texte littéral échappé, chaque {n} devient un groupe
                val sb = StringBuilder("^"); var last = 0; val order = mutableListOf<Int>()
                for (m in PH.findAll(fr)) { sb.append(Regex.escape(fr.substring(last, m.range.first))); sb.append("(.+?)"); order += m.groupValues[1].toInt(); last = m.range.last + 1 }
                sb.append(Regex.escape(fr.substring(last))).append("$")
                // Dans la traduction, {n} désigne la n-ième valeur du texte français : on la relie au bon groupe
                val out = PH.replace(en) { m -> val i = order.indexOf(m.groupValues[1].toInt()); if (i >= 0) "\u0000${i + 1}\u0000" else m.value }
                val t = T(Regex(sb.toString(), RegexOption.DOT_MATCHES_ALL), out)
                val head = fr.substringBefore('{').trim().substringBefore(' ')
                if (head.isEmpty()) lo += t else words.getOrPut(head) { mutableListOf() }.add(t)
            }
            byWord = words; loose = lo
        } catch (e: Exception) {}
    }

    /** Texte traduit (ou le texte lui-même en français, ou s'il n'est pas dans le dictionnaire). */
    fun tr(s: String): String {
        if (!Lang.en || s.isBlank()) return s
        synchronized(cache) { cache[s] }?.let { return it }
        val r = exact[s] ?: exact[s.trim()]?.let { s.replace(s.trim(), it) } ?: template(s) ?: prefixed(s) ?: upper(s) ?: s
        synchronized(cache) { cache[s] = r }
        return r
    }

    /** « 🧘 Bien-être », « • Groupe » : emoji ou symbole devant un texte connu. */
    private val PREFIX = Regex("^([^\\p{L}\\p{N}«]+)(\\p{L}.*)$", RegexOption.DOT_MATCHES_ALL)
    private fun prefixed(s: String): String? {
        val m = PREFIX.find(s) ?: return null
        val rest = m.groupValues[2]
        val t = exact[rest] ?: template(rest) ?: return null
        return m.groupValues[1] + t
    }

    /** Texte déjà mis en majuscules (« NIVEAU 5 ») : on cherche sa version normale. */
    private var upperIndex: Map<String, String>? = null
    private fun upper(s: String): String? {
        if (s.length > 80 || s != s.uppercase() || s == s.lowercase()) return null
        val idx = upperIndex ?: exact.entries.associate { it.key.uppercase() to it.value }.also { upperIndex = it }
        idx[s]?.let { return it.uppercase() }
        // Gabarit : le premier mot sert d'appui (« NIVEAU 5 » → « Niveau 5 »)
        val cap = s.lowercase().replaceFirstChar { it.titlecase() }
        return template(cap)?.uppercase()
    }

    private fun template(s: String, depth: Int = 0): String? {
        if (depth > 2) return null
        val head = s.trim().substringBefore(' ')
        for (t in (byWord[head].orEmpty() + loose)) {
            val m = t.re.find(s) ?: continue
            return Regex("\u0000(\\d+)\u0000").replace(t.out) { g ->
                val v = m.groupValues[g.groupValues[1].toInt()]
                exact[v] ?: template(v, depth + 1) ?: v
            }
        }
        return null
    }
}

/** Raccourci : texte à afficher dans la langue de l'interface. */
fun tr(s: String) = I18n.tr(s)
