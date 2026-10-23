package fr.pepsgoal.app.ai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.pepsgoal.app.data.Journal
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * Un message de la conversation avec le coach (« user » ou « coach »).
 * [goal] : objectif proposé (« type|titre|emoji|paramètre », ou « ?demande » pour un brouillon sans IA) ;
 * [acts] : actions proposées (« validate:<id> ») ; [fact] : ce que le coach a retenu ; [simple] : réponse sans IA.
 */
@Serializable
class ChatMsg(val at: Long, val role: String, val text: String, val goal: String? = null, val acts: List<String> = emptyList(),
              val fact: String? = null, val simple: Boolean = false)

/**
 * Conversation avec le coach, 100 % locale. La génération tourne hors de l'écran (la réponse arrive même si on le
 * quitte) et s'affiche au fil de l'écriture. Le coach connaît la situation du jour, la semaine et sa mémoire ; il peut
 * proposer un objectif, la validation d'un objectif fait, et retenir un fait durable. Sans modèle (ou s'il échoue), un
 * coach « simple » à base de règles répond quand même.
 */
object CoachChat {
    private fun file(ctx: Context) = File(ctx.filesDir, "ai/chat.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    var rev by mutableStateOf(0)
    /** Une réponse est en cours ; [partial] : son texte au fil de l'écriture (null tant que rien n'est écrit). */
    var busy by mutableStateOf(false); private set
    var partial by mutableStateOf<String?>(null); private set

    fun all(ctx: Context): List<ChatMsg> = try {
        json.decodeFromString(ListSerializer(ChatMsg.serializer()), file(ctx).readText()).map { m -> fixMojibake(m.text).let { if (it == m.text) m else ChatMsg(m.at, m.role, it, m.goal, m.acts, m.fact, m.simple) } }
    } catch (e: Exception) { emptyList() }
    private fun save(ctx: Context, l: List<ChatMsg>) { file(ctx).parentFile?.mkdirs(); file(ctx).writeText(json.encodeToString(ListSerializer(ChatMsg.serializer()), l.takeLast(120))); rev++ }
    fun clear(ctx: Context) { stop(); file(ctx).delete(); rev++ }

    /** Envoie un message ; la réponse s'ajoute à la conversation quand elle est prête. */
    fun send(ctx: Context, text: String) {
        val t = text.trim().take(600); if (t.isEmpty() || busy) return
        val app = ctx.applicationContext
        save(app, all(app) + ChatMsg(System.currentTimeMillis(), "user", t))
        reply(app, t)
    }

    /** Nouvelle réponse au dernier message (la précédente est retirée). */
    fun regenerate(ctx: Context) {
        if (busy) return
        val app = ctx.applicationContext; val l = all(app)
        val lastUser = l.lastOrNull { it.role == "user" } ?: return
        save(app, l.dropLastWhile { it.role == "coach" })
        reply(app, lastUser.text)
    }

    private var stopped = false
    /** Arrête la réponse en cours : le moteur s'interrompt proprement et ce qui est déjà écrit est gardé. */
    fun stop() { if (!busy) return; stopped = true; if (LocalAi.busy) LocalAi.stopGeneration() else job?.cancel() }

    private fun reply(ctx: Context, text: String) {
        busy = true; partial = null; stopped = false
        job = scope.launch {
            var out: String? = null
            try {
                if (LocalAi.on(ctx)) {
                    // Données Santé lues avant de construire la consigne
                    val health = try { fr.pepsgoal.app.sys.Health.summary(ctx) } catch (e: Exception) { "" }
                    out = LocalAi.generate(ctx, maxTokens(ctx), .75f, onPartial = { partial = visible(it) }) { lvl -> prompt(ctx, text, lvl, health) }
                }
            } catch (e: CancellationException) { out = partial }
            // Arrêtée avant d'avoir rien écrit : pas de réponse
            if (stopped && out.isNullOrBlank()) { busy = false; partial = null; return@launch }
            if (stopped) out = out?.trimEnd() + "…"
            val msg = withContext(NonCancellable) { build(ctx, text, out) }
            save(ctx, all(ctx) + msg)
            busy = false; partial = null
        }
    }

    private fun maxTokens(ctx: Context) = when (ctx.peps.prefs.aiLength) { "short" -> 120; "long" -> 420; else -> 260 }

    /** Lignes d'action retirées du texte affiché pendant l'écriture. */
    private val ACT = Regex("^\\s*(OBJECTIF|GOAL|VALIDER|CHECK|SOUVENIR|MEMORY)\\s*:", RegexOption.IGNORE_CASE)
    private fun visible(s: String) = s.lines().filterNot { ACT.containsMatchIn(it) }.joinToString("\n").trimEnd()

    /** Message du coach : texte, actions repérées dans la réponse du modèle et dans la phrase de l'utilisateur. */
    private suspend fun build(ctx: Context, text: String, out: String?): ChatMsg {
        val now = System.currentTimeMillis()
        val left = todo(ctx)
        if (out.isNullOrBlank()) {
            val f = Fallback.reply(ctx, text)
            return ChatMsg(now, "coach", f.text, f.goal, f.acts, null, simple = true)
        }
        val lines = fixName(ctx, out).lines()
        fun tail(key: String) = lines.lastOrNull { Regex("^\\s*$key\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(it) }?.substringAfter(':')?.trim()
        val goalLine = (tail("OBJECTIF") ?: tail("GOAL"))?.takeIf { Coach.parseLine(it, text) != null }
        val validate = lines.filter { Regex("^\\s*(VALIDER|CHECK)\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            .flatMap { l -> Regex("\\d+").findAll(l.substringAfter(':')).mapNotNull { m -> left.getOrNull(m.value.toInt() - 1)?.id }.toList() }
        val fact = (tail("SOUVENIR") ?: tail("MEMORY"))?.trim('"', '«', '»', ' ')?.takeIf { it.length in 6..200 }
        val saved = fact?.let { AiMemory.addFact(ctx, it) } == true
        val body = lines.filterNot { ACT.containsMatchIn(it) }.joinToString("\n").trim().ifEmpty { if (goalLine != null) "Voici ma proposition :" else "D’accord !" }
        val acts = (validate + Intents.doneGoals(ctx, text)).distinct().take(3).map { "validate:$it" }
        return ChatMsg(now, "coach", body, goalLine, acts, if (saved) fact else null)
    }

    /** Objectifs encore à faire aujourd'hui, numérotés dans la consigne (pour « VALIDER: n »). */
    fun todo(ctx: Context) = ctx.peps.pg.let { pg -> pg.me()?.let { u -> pg.todayPlan(u.id).due.filter { !it.done }.map { it.g } } ?: emptyList() }

    /** Situation donnée au coach ; [lvl] 0 : complète, 1 : réduite, 2 : minimale. */
    fun situation(ctx: Context, lvl: Int = 0, health: String = ""): String {
        val pg = ctx.peps.pg; val u = pg.me() ?: return ""
        val st = pg.globalStreak(u.id); val plan = pg.todayPlan(u.id); val today = todayDate()
        val left = plan.due.filter { !it.done }; val done = plan.due.filter { it.done }
        val sb = StringBuilder()
        sb.append("Nous sommes ${weekdayLongDate(today)}, il est ${LocalTime.now().hour} h. ")
        sb.append("Série globale : ${st.n} jour(s)${if (st.lit) ", flamme déjà allumée aujourd’hui" else ", flamme pas encore allumée aujourd’hui"}. ")
        sb.append("${done.size}/${plan.due.size} validations aujourd’hui.")
        if (pg.isRest()) sb.append(" C’est un jour de repos.")
        if (pg.isHardDay()) sb.append(" L’utilisateur a signalé une journée difficile : sois doux, propose la version minimale.")
        if (left.isNotEmpty()) sb.append("\nÀ faire aujourd’hui :\n" + left.take(if (lvl == 2) 4 else 10).mapIndexed { i, d -> "${i + 1}. ${d.g.title} (${typeInfo(d.g.type).label.lowercase()}${if (lvl == 0) ", série ${pg.goalStreak(d.g)}" else ""})" }.joinToString("\n"))
        if (done.isNotEmpty() && lvl < 2) sb.append("\nDéjà validé : " + done.take(8).joinToString(", ") { it.g.title })
        if (lvl == 0) {
            val others = pg.myGoals(u.id).filter { g -> !pg.isClosedGoal(g) && plan.due.none { it.g.id == g.id } }.take(8)
            if (others.isNotEmpty()) sb.append("\nAutres objectifs : " + others.joinToString(", ") { "${it.title} (${typeInfo(it.type).label.lowercase()})" })
            // La semaine en chiffres
            val wins = pg.userCheckins(u.id).filter { pg.isWin(it) }
            val w0 = weekStart(today); val w1 = w0.minusWeeks(1)
            val thisW = wins.count { parseYmd(it.date) >= w0 }; val lastW = wins.count { val d = parseYmd(it.date); d >= w1 && d < w0 }
            sb.append("\nCette semaine : $thisW validations (semaine dernière : $lastW). Meilleure série : ${pg.bestStreak(u.id)} jours.")
            pg.missStats().firstOrNull()?.let { (k, n) -> sb.append(" Raison la plus fréquente des jours manqués : ${MISS_REASONS.firstOrNull { it.first == k }?.second ?: k} ($n fois).") }
            val mood = Journal.recent(ctx, 5)
            if (mood.isNotEmpty()) sb.append("\nHumeur des derniers jours : " + mood.joinToString(", ") { "${it.date.takeLast(5)} ${Journal.MOODS.getOrNull(it.mood - 1)?.first?.lowercase() ?: ""}" })
            if (health.isNotBlank()) sb.append("\n$health")
        }
        if (lvl < 2) Coach.weather(ctx)?.let { sb.append("\nMétéo : ${it.first.lowercase()}${it.second?.let { t -> ", ${Math.round(t)} °C" } ?: ""}") }
        return sb.toString()
    }

    private fun prompt(ctx: Context, text: String, lvl: Int, health0: String): String {
        val health = if (lvl == 0) health0 else ""
        val hist = all(ctx)
        // Le dernier message (celui de l'utilisateur) est déjà dans l'historique enregistré
        val turns = hist.takeLast(when (lvl) { 0 -> 8; 1 -> 4; else -> 1 }).joinToString("\n") { (if (it.role == "user") "Utilisateur : " else "${Personas.coachName(ctx)} : ") + it.text.take(if (lvl == 0) 500 else 250) }
        return """${Brain.system(ctx)}${if (lvl == 0) Brain.memory(ctx) else "\n"}
Situation actuelle :
${situation(ctx, lvl, health)}

Actions facultatives, chacune sur sa propre ligne à la toute fin de ta réponse :
OBJECTIF: type|titre|emoji|paramètre — seulement si l’utilisateur veut créer un objectif (types : routine, objective, cap, abstinence, limit, duration, deadline, challenge)
VALIDER: numéro — seulement si l’utilisateur dit avoir fait un des objectifs « à faire » (numéro de la liste)
SOUVENIR: un fait durable qu’il vient de partager (contrainte, préférence, habitude), en une phrase courte
Conversation :
$turns
${Personas.coachName(ctx)} :"""
    }

    /** Questions proposées sous la conversation, selon le moment et la situation. */
    fun suggestions(ctx: Context): List<String> {
        val pg = ctx.peps.pg; val u = pg.me() ?: return emptyList()
        val st = pg.globalStreak(u.id); val plan = pg.todayPlan(u.id); val left = plan.due.count { !it.done }; val h = LocalTime.now().hour
        return listOfNotNull(
            if (h < 12) "Mon briefing du jour" else null,
            if (left > 0) "Par quoi je commence ?" else null,
            if (!st.lit && st.n > 0 && h >= 17) "Comment sauver ma série ce soir ?" else null,
            if (todayDate().dayOfWeek.value >= 6 || todayDate().dayOfWeek.value == 1) "Fais le point sur ma semaine" else null,
            if (left == 0 && plan.due.isNotEmpty()) "Tout est fait, et maintenant ?" else null,
            "Je manque de motivation",
            "Propose-moi un nouvel objectif",
            if (h >= 18) "Prépare ma journée de demain" else null,
        ).distinct().take(5)
    }
}

/** Repérage d'intentions dans une phrase, sans IA. */
object Intents {
    private val DONE = Regex("\\b(j[’'] ?ai|c[’'] ?est fait|fini|termin|valid|fait|done|i did|i have)\\b", RegexOption.IGNORE_CASE)
    private val VERBS = mapOf("couru" to "cour", "lu" to "lire", "médité" to "médit", "marché" to "march", "nagé" to "nag", "bu" to "eau", "écrit" to "écri", "dormi" to "dorm", "rangé" to "rang", "cuisiné" to "cuisin", "révisé" to "révis", "pédalé" to "vélo")
    private fun norm(s: String) = s.lowercase().replace('’', '\'')

    /** Objectifs à faire aujourd'hui que la phrase dit avoir faits (« j'ai couru 5 km » → « Courir »). */
    fun doneGoals(ctx: Context, text: String): List<String> {
        val t = norm(text); if (!DONE.containsMatchIn(t)) return emptyList()
        val left = CoachChat.todo(ctx)
        val stems = t.split(Regex("[^\\p{L}0-9]+")).filter { it.length >= 2 }.flatMap { w -> listOfNotNull(VERBS[w], w.take(5).takeIf { w.length >= 4 }) }.toSet()
        val hit = left.filter { g ->
            val words = norm(g.title).split(Regex("[^\\p{L}0-9]+")).filter { it.length >= 4 }.map { it.take(5) }
            words.any { it in stems } || stems.any { s -> s.length >= 3 && norm(g.title).contains(s) }
        }
        return hit.map { it.id }.take(3)
    }
}
