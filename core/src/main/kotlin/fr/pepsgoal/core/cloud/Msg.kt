package fr.pepsgoal.core.cloud

import fr.pepsgoal.core.JSON
import fr.pepsgoal.core.fmtDur
import fr.pepsgoal.core.model.Message
import kotlinx.serialization.json.*

/**
 * Contenu d'un message (chiffré en entier) : texte simple, ou objet JSON `{ "pg": 1, "k": … }` pour une photo,
 * un message vocal, un objectif partagé, un sondage, un vote, une réaction ou un texte avec réponse / mentions.
 */
object Msg {
    private const val RICH = "{\"pg\":1,"
    /** Messages de service, jamais affichés ni notifiés : votes, réactions, modification et suppression d'un message. */
    val SILENT = setOf("vote", "react", "edit", "del", "pin")
    private val KINDS = setOf("photo", "voice", "goal", "text", "poll", "vote", "react", "edit", "del", "pin")

    fun pack(o: JsonObject): String = JsonObject(mapOf("pg" to JsonPrimitive(1)) + o.filterKeys { it != "pg" }).toString()

    fun text(t: String) = JsonPrimitive(t)

    fun body(raw: String?): JsonObject? {
        if (raw == null) return null
        if (raw.startsWith(RICH)) try { val o = JSON.parseToJsonElement(raw).jsonObject; if (o.str("k") in KINDS) return o } catch (e: Exception) {}
        return buildJsonObject { put("pg", 1); put("k", "text"); put("t", raw) }
    }

    fun preview(m: Message, raw: String?, pending: Boolean): String {
        if (m.e2e == null) return m.text ?: ""
        if (pending) return "🔒 …"
        val b = body(raw) ?: return "🔒 Message chiffré : clé en attente"
        return when (b.str("k")) {
            "photo" -> "📷 Photo" + (b.strN("t")?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
            "voice" -> "🎤 Message vocal (${fmtDur(b.obj("a")?.get("dur")?.jsonPrimitive?.doubleOrNull ?: 0.0)})"
            "goal" -> "🎯 ${b.obj("g").str("title", "Objectif partagé")}"
            "poll" -> "📊 ${b.str("q", "Sondage")}"
            "vote" -> "📊 A voté au sondage"
            "react" -> "A réagi ${b.str("e")}"
            "edit" -> "✏️ ${b.str("t")}"
            "del" -> "Message supprimé"
            "pin" -> if (b["on"]?.toString() == "false") "A désépinglé un message" else "📌 A épinglé un message"
            else -> b.str("t")
        }
    }
}
