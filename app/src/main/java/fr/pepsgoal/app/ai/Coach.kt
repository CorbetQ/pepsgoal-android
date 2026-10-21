package fr.pepsgoal.app.ai

import android.content.Context
import fr.pepsgoal.app.peps
import fr.pepsgoal.app.ui.Wx
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.Goal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Fonctions du coach IA : création d'un objectif à partir d'une phrase, recommandations du moment (heure, météo) et
 * conseil du jour. Chaque fonction a une version « sans IA » (règles simples) utilisée si le modèle ne répond pas
 * ou répond de travers : le résultat reste toujours utilisable.
 */
object Coach {
    private const val ROLE = "Tu es le coach bienveillant de PepsGoal, une application de suivi d’objectifs et d’habitudes. Tu réponds toujours en français, très brièvement."

    private fun memory(ctx: Context) = AiMemory.context(ctx).let { if (it.isBlank()) "" else "\nCe que tu sais de l’utilisateur :\n$it\n" }

    /* ------------------------------------------------------------------ créer un objectif */

    /** Brouillon d'objectif proposé par l'IA, à vérifier dans le formulaire avant l'enregistrement. */
    class Draft(val type: String, val title: String, val icon: String, val fields: Map<String, String>, val byAi: Boolean)

    private val TYPES = setOf("routine", "objective", "abstinence", "limit", "duration", "deadline", "challenge")

    suspend fun createGoal(ctx: Context, request: String): Draft {
        val prompt = """$ROLE${memory(ctx)}
Transforme la demande en UN objectif PepsGoal. Réponds uniquement par une ligne au format TYPE|TITRE|EMOJI|PARAMETRE
TYPE parmi : routine, objective, abstinence, limit, duration, deadline, challenge.
PARAMETRE : routine = jours (lun,mer,ven) ou nombre de fois par semaine (3x) ; objective = cible et unité (12 livres) ; limit = maximum et unité (2 cafés) ; duration = minutes par jour (20) ; deadline = date AAAA-MM-JJ ; challenge = nombre de jours (30) ; abstinence = rien.
Demande : courir trois fois par semaine
routine|Courir 30 minutes|🏃|3x
Demande : lire 12 livres cette année
objective|Lire 12 livres|📚|12 livres
Demande : arrêter de fumer
abstinence|Arrêter la cigarette|🚭|
Demande : ${request.trim().take(200)}
"""
        val out = if (LocalAi.on(ctx, ctx.peps.prefs.aiCreate)) LocalAi.generate(ctx, prompt, 60) else null
        val ai = out?.lines()?.map { it.trim().trim('`') }?.firstOrNull { it.count { c -> c == '|' } >= 2 }?.let { parseLine(it, request) }
        val d = ai ?: rules(request)
        AiMemory.add(ctx, "goal", "A demandé « ${request.trim().take(120)} » → objectif « ${d.title} » (${typeInfo(d.type).label.lowercase()}).")
        return d
    }

    private fun parseLine(line: String, request: String): Draft? {
        val p = line.split('|').map { it.trim() }
        val type = p.getOrNull(0)?.lowercase()?.takeIf { it in TYPES } ?: return null
        val title = p.getOrNull(1)?.takeIf { it.length in 2..80 } ?: return null
        val icon = p.getOrNull(2)?.takeIf { it.isNotBlank() && it.length <= 12 && it.any { c -> Character.isSurrogate(c) || c.code > 0x2000 } } ?: iconFor("$title $request")
        return Draft(type, title.replaceFirstChar { it.uppercase() }, icon, fieldsFor(type, p.getOrNull(3).orEmpty(), request), true)
    }

    private val DAYS = listOf("lun", "mar", "mer", "jeu", "ven", "sam", "dim")

    private fun fieldsFor(type: String, param: String, request: String): Map<String, String> {
        val num = Regex("(\\d+(?:[.,]\\d+)?)").find(param)?.value?.replace(',', '.')
        val unit = param.replace(Regex("^\\s*\\d+(?:[.,]\\d+)?\\s*"), "").trim().take(20)
        return when (type) {
            "routine" -> {
                val days = DAYS.indices.filter { i -> Regex("\\b${DAYS[i]}").containsMatchIn((param + " " + request).lowercase()) }
                val per = Regex("(\\d)\\s*(x|fois)").find((param + " " + request).lowercase())?.groupValues?.get(1)
                when { days.isNotEmpty() -> mapOf("mode" to "days", "days" to days.joinToString(","))
                    per != null -> mapOf("mode" to "count", "perWeek" to per)
                    else -> mapOf("mode" to "days", "days" to "0,1,2,3,4,5,6") }
            }
            "objective" -> buildMap { num?.let { put("target", it) }; if (unit.isNotEmpty()) put("unit", unit) }
            "limit" -> buildMap { num?.let { put("max", it) }; if (unit.isNotEmpty()) put("unit", unit); put("period", if (param.contains("semaine")) "week" else "day") }
            "duration" -> mapOf("minutes" to (num ?: "20"))
            "challenge" -> mapOf("length" to (num ?: "30"))
            "deadline" -> Regex("\\d{4}-\\d{2}-\\d{2}").find(param)?.value?.let { mapOf("due" to it) } ?: emptyMap()
            else -> emptyMap()
        }
    }

    /** Version sans IA : mots-clés de la demande. */
    fun rules(request: String): Draft {
        val r = request.lowercase()
        val type = when {
            Regex("arr[êe]ter|sans |plus de |ne plus|stop").containsMatchIn(r) -> "abstinence"
            Regex("max|moins de|limiter|pas plus").containsMatchIn(r) -> "limit"
            Regex("minutes? par jour|\\bmin\\b par jour").containsMatchIn(r) -> "duration"
            Regex("avant le|d'ici|d’ici|terminer|finir").containsMatchIn(r) -> "deadline"
            Regex("défi|challenge|jours de suite|jours d'affilée").containsMatchIn(r) -> "challenge"
            Regex("\\d+\\s*(livres|km|kilos|kg|€|euros|pages|séances)").containsMatchIn(r) && !r.contains("par semaine") -> "objective"
            else -> "routine"
        }
        val title = request.trim().trimEnd('.', '!').replaceFirstChar { it.uppercase() }.take(80)
        val param = Regex("\\d+(?:[.,]\\d+)?\\s*[\\p{L}€]*").find(r)?.value.orEmpty()
        return Draft(type, title, iconFor(r), fieldsFor(type, param, request), false)
    }

    private val ICONS = listOf(
        "cour|run|jogging" to "🏃", "march|pas\\b" to "🚶", "vélo|velo" to "🚴", "nag|piscine" to "🏊", "sport|muscu|gym|pompes" to "💪", "yoga|médit|medit|respir" to "🧘",
        "lire|livre|lecture|pages" to "📚", "eau|boire|hydrat" to "💧", "dorm|sommeil|coucher" to "😴", "sucre|bonbon|gâteau" to "🍬", "fum|cigarette|tabac" to "🚭",
        "alcool|bière|vin" to "🍷", "café|cafe" to "☕", "écran|ecran|téléphone|réseaux" to "📱", "code|program|développ" to "💻", "langue|anglais|espagnol|apprendre" to "🗣️",
        "économ|argent|épargn|€" to "💰", "fruit|légume|manger|cuisine" to "🥗", "ménage|ranger|range" to "🧹", "écrire|journal|écriture" to "✍️", "musique|guitare|piano" to "🎸",
        "dent" to "🪥", "ami|famille|appeler" to "📞", "jardin|plante" to "🌱"
    )
    private fun iconFor(text: String): String { val t = text.lowercase(); return ICONS.firstOrNull { Regex(it.first).containsMatchIn(t) }?.second ?: "🎯" }

    /* ------------------------------------------------------------------ recommandations du moment */

    class Pick(val goal: Goal, val reason: String)

    private fun weather(ctx: Context): Pair<String, Double?>? {
        val w = Wx.cached(ctx, ctx.peps.pg.me()?.settings?.weather)?.data ?: return null
        return Weather.wmo(w.current.code, w.current.isDay).label to w.current.temp
    }

    private val OUTDOOR = Regex("cour|march|vélo|velo|run|jogging|promen|jardin|dehors|footing|randonn|sport")

    /** Jusqu'à 3 objectifs restants du jour, choisis selon l'heure et la météo, avec une courte raison. */
    suspend fun recommend(ctx: Context): List<Pick> {
        val pg = ctx.peps.pg; val u = pg.me() ?: return emptyList()
        val left = pg.todayPlan(u.id).due.filter { !it.done }.map { it.g }
        if (left.isEmpty()) return emptyList()
        val hour = LocalTime.now().hour; val wx = weather(ctx)
        val wxTxt = wx?.let { "${it.first.lowercase()}${it.second?.let { t -> ", ${Math.round(t)} °C" } ?: ""}" } ?: "inconnue"
        val list = left.take(9).mapIndexed { i, g -> "${i + 1}. ${g.title} (${typeInfo(g.type).label.lowercase()})" }.joinToString("\n")
        val prompt = """$ROLE${memory(ctx)}
Il est $hour h, météo : $wxTxt. Objectifs qu’il reste à valider aujourd’hui :
$list
Choisis les ${minOf(3, left.size)} objectifs les plus adaptés à ce moment (heure, météo). Réponds uniquement par des lignes au format NUMERO|RAISON (moins de 12 mots).
"""
        val out = if (LocalAi.on(ctx, ctx.peps.prefs.aiRecommend)) LocalAi.generate(ctx, prompt, 120) else null
        val ai = out?.lines()?.mapNotNull { l ->
            val m = Regex("^\\D*(\\d+)\\s*[|.:)-]\\s*(.+)$").find(l.trim()) ?: return@mapNotNull null
            val g = left.take(9).getOrNull(m.groupValues[1].toInt() - 1) ?: return@mapNotNull null
            Pick(g, m.groupValues[2].trim().trim('"').take(90))
        }?.distinctBy { it.goal.id }?.take(3)
        return ai?.takeIf { it.isNotEmpty() } ?: rulesRecommend(left, hour, wx)
    }

    private fun rulesRecommend(left: List<Goal>, hour: Int, wx: Pair<String, Double?>?): List<Pick> {
        val wet = wx?.first?.lowercase()?.let { Regex("pluie|averse|orage|neige|bruine|grêle").containsMatchIn(it) } == true
        val cold = (wx?.second ?: 15.0) < 3; val hot = (wx?.second ?: 15.0) > 30
        return left.map { g ->
            val t = g.title.lowercase(); val out = OUTDOOR.containsMatchIn(t)
            var score = 0.0; var why = "C’est le bon moment pour t’y mettre."
            when {
                out && (wet || cold) -> { score -= 3; why = "Météo peu favorable : tente-le en intérieur." }
                out && hot && hour in 11..17 -> { score -= 2; why = "Il fait chaud : préfère la fraîcheur du soir." }
                out && hour in 6..10 -> { score += 3; why = "Le matin est idéal pour bouger dehors." }
                out && hour in 17..20 && !wet -> { score += 2; why = "Belle fin de journée pour sortir." }
                Regex("lire|lecture|journal|écrire|médit").containsMatchIn(t) && hour >= 20 -> { score += 3; why = "Parfait pour finir la journée au calme." }
                Regex("eau|boire|vitamin|dent").containsMatchIn(t) -> { score += 2; why = "Rapide : fais-le tout de suite." }
                g.type == "deadline" -> { score += 2; why = "Une échéance approche : avance un peu." }
                hour >= 21 -> { score += 1; why = "Encore un effort avant minuit pour ta flamme." }
            }
            Pick(g, why) to score
        }.sortedByDescending { it.second }.take(3).map { it.first }
    }

    /* ------------------------------------------------------------------ conseil du jour */

    /** Conseil court pour l'accueil (gardé en cache quelques heures). */
    suspend fun tip(ctx: Context, force: Boolean = false): String {
        val app = ctx.peps; val pg = app.pg; val u = pg.me() ?: return ""
        val st = pg.globalStreak(u.id); val plan = pg.todayPlan(u.id); val done = plan.due.count { it.done }
        val hour = LocalTime.now().hour
        val key = "${LocalDate.now()}|${hour / 4}|$done/${plan.due.size}|${st.n}"
        if (!force && app.prefs.aiTipKey == key && app.prefs.aiTip.isNotBlank()) return app.prefs.aiTip
        val wx = weather(ctx)
        val prompt = """$ROLE${memory(ctx)}
Contexte : il est $hour h${wx?.let { ", météo ${it.first.lowercase()}${it.second?.let { t -> " (${Math.round(t)} °C)" } ?: ""}" } ?: ""}, série de ${st.n} jour(s)${if (st.lit) " déjà allumée aujourd’hui" else " pas encore allumée aujourd’hui"}, $done validation(s) sur ${plan.due.size} prévue(s).
Objectifs du jour : ${plan.due.joinToString(", ") { it.g.title }.ifEmpty { "aucun" }}.
Donne UN conseil concret et motivant pour maintenant, en 2 phrases maximum, sans liste ni titre.
"""
        val out = (if (LocalAi.on(ctx, app.prefs.aiTips)) LocalAi.generate(ctx, prompt, 90) else null)?.lines()?.filter { it.isNotBlank() }?.joinToString(" ")?.trim()?.trim('"')?.take(260)
        val t = out?.takeIf { it.length > 15 } ?: rulesTip(st.n, st.lit, done, plan.due.size, hour)
        app.prefs.aiTip = t; app.prefs.aiTipKey = key
        return t
    }

    private fun rulesTip(n: Int, lit: Boolean, done: Int, total: Int, hour: Int): String = when {
        total == 0 -> "Rien de prévu aujourd’hui : profites-en pour préparer ton prochain objectif, même tout petit."
        done == total -> "Tout est validé, bravo ! Note ce qui t’a aidé aujourd’hui pour le refaire demain."
        !lit && n > 0 && hour >= 18 -> "Ta série de $n jours t’attend : une seule validation avant minuit suffit pour la garder."
        hour < 11 -> "Commence par l’objectif le plus facile : une première victoire le matin lance toute la journée."
        hour in 12..14 -> "Profite de la pause de midi pour avancer sur un objectif rapide."
        else -> "Il te reste ${total - done} objectif${if (total - done > 1) "s" else ""} : choisis-en un et fais juste les 5 premières minutes."
    }
}
