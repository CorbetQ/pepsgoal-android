package fr.pepsgoal.app.ai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.pepsgoal.app.data.Journal
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalTime

/** Un message de la conversation avec le coach (« user » ou « coach »), éventuellement avec un objectif proposé. */
@Serializable
class ChatMsg(val at: Long, val role: String, val text: String, val goal: String? = null)

/**
 * Conversation avec le coach IA, 100 % locale : le coach connaît tes objectifs, ta série, ton humeur récente et sa
 * mémoire ; il peut proposer un objectif prêt à créer (ligne « OBJECTIF: type|titre|emoji|paramètre »).
 */
object CoachChat {
    private fun file(ctx: Context) = File(ctx.filesDir, "ai/chat.json")
    private val json = Json { ignoreUnknownKeys = true }
    var rev by mutableStateOf(0)

    fun all(ctx: Context): List<ChatMsg> = try { json.decodeFromString(ListSerializer(ChatMsg.serializer()), file(ctx).readText()) } catch (e: Exception) { emptyList() }
    private fun save(ctx: Context, l: List<ChatMsg>) { file(ctx).parentFile?.mkdirs(); file(ctx).writeText(json.encodeToString(ListSerializer(ChatMsg.serializer()), l.takeLast(80))); rev++ }
    fun clear(ctx: Context) { file(ctx).delete(); rev++ }

    /** Résumé de la situation de l'utilisateur donné au coach. */
    private fun situation(ctx: Context, health: String = ""): String {
        val pg = ctx.peps.pg; val u = pg.me() ?: return ""
        val st = pg.globalStreak(u.id); val plan = pg.todayPlan(u.id)
        val goals = pg.myGoals(u.id).filter { !pg.isClosedGoal(it) }.take(12).joinToString("\n") { g ->
            val d = plan.due.firstOrNull { it.g.id == g.id }
            "- ${g.title} (${typeInfo(g.type).label.lowercase()}, série ${pg.goalStreak(g)}${if (d != null) if (d.done) ", validé aujourd’hui" else ", à faire aujourd’hui" else ""})"
        }
        val mood = Journal.recent(ctx, 7).let { l -> if (l.isEmpty()) "" else "\nHumeur des derniers jours : " + l.joinToString(", ") { "${it.date.takeLast(5)} ${Journal.MOODS.getOrNull(it.mood - 1)?.second ?: ""}" } }
        val wx = Coach.weather(ctx)?.let { "\nMétéo : ${it.first.lowercase()}${it.second?.let { t -> ", ${Math.round(t)} °C" } ?: ""}" } ?: ""
        val hs = if (health.isNotBlank()) "\n$health" else ""
        return "Il est ${LocalTime.now().hour} h. Série globale : ${st.n} jour(s)${if (st.lit) " (flamme allumée aujourd’hui)" else ""}. ${plan.due.count { it.done }}/${plan.due.size} validations aujourd’hui.\nObjectifs :\n${goals.ifEmpty { "aucun" }}$mood$wx$hs"
    }

    /** Envoie un message et renvoie la réponse du coach (ajoutées à la conversation). */
    suspend fun send(ctx: Context, text: String): ChatMsg {
        val health = try { fr.pepsgoal.app.sys.Health.summary(ctx) } catch (e: Exception) { "" }
        val hist = all(ctx) + ChatMsg(System.currentTimeMillis(), "user", text.trim().take(600))
        save(ctx, hist)
        val turns = hist.takeLast(8).joinToString("\n") { (if (it.role == "user") "Utilisateur : " else "Coach : ") + it.text }
        val mem = AiMemory.context(ctx).let { if (it.isBlank()) "" else "\nCe que tu sais de l’utilisateur :\n$it\n" }
        val prompt = """Tu es le coach bienveillant de PepsGoal, une application de suivi d’objectifs et d’habitudes. Tu tutoies, tu réponds en français, de façon concrète et courte (5 phrases maximum).$mem
Situation actuelle :
${situation(ctx, health)}
Si l’utilisateur veut créer un objectif, ajoute à la fin une ligne exactement au format : OBJECTIF: type|titre|emoji|paramètre (type parmi routine, objective, abstinence, limit, duration, deadline, challenge).
Conversation :
$turns
Coach :"""
        val out = LocalAi.generate(ctx, prompt, 260)?.trim()?.removePrefix("Coach :")?.trim()
        val reply = out?.takeIf { it.isNotBlank() } ?: "Je n’arrive pas à répondre pour le moment. Vérifie que le coach IA est bien installé et activé dans Paramètres › Assistant IA."
        val lines = reply.lines()
        val goalLine = lines.lastOrNull { it.trim().startsWith("OBJECTIF", ignoreCase = true) }?.substringAfter(':')?.trim()
        val body = lines.filterNot { it.trim().startsWith("OBJECTIF", ignoreCase = true) }.joinToString("\n").trim().ifEmpty { "Voici ma proposition :" }
        val draftOk = goalLine?.let { Coach.parseLine(it, text) } != null
        val msg = ChatMsg(System.currentTimeMillis(), "coach", body, if (draftOk) goalLine else null)
        save(ctx, hist + msg)
        AiMemory.add(ctx, "chat", "A demandé au coach : « ${text.trim().take(100)} »")
        return msg
    }
}
