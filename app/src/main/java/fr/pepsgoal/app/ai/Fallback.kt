package fr.pepsgoal.app.ai

import android.content.Context
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import java.time.LocalTime

/**
 * Coach « simple », sans modèle : il comprend les demandes les plus courantes (série, programme du jour, bilan de la
 * semaine, créer un objectif, motivation, « j'ai fait… ») et répond avec les vrais chiffres, dans le ton de la
 * personnalité choisie. Utilisé quand l'IA n'est pas installée, désactivée, ou ne répond pas.
 */
object Fallback {
    class Reply(val text: String, val goal: String? = null, val acts: List<String> = emptyList())

    private fun has(t: String, re: String) = Regex(re, RegexOption.IGNORE_CASE).containsMatchIn(t)

    suspend fun reply(ctx: Context, text: String): Reply {
        val pg = ctx.peps.pg; val u = pg.me() ?: return Reply("…")
        val p = Personas.current(ctx); val who = firstName(ctx)
        val t = text.lowercase().replace('’', '\'')
        val st = pg.globalStreak(u.id); val plan = pg.todayPlan(u.id)
        val left = plan.due.filter { !it.done }; val done = plan.due.count { it.done }
        val h = LocalTime.now().hour
        val cheer = p.cheer.random()
        val done0 = Intents.doneGoals(ctx, text)
        return when {
            done0.isNotEmpty() -> {
                val names = done0.mapNotNull { pg.goalById(it)?.title }.joinToString(", ") { "« $it »" }
                Reply("Bravo${who?.let { " $it" } ?: ""} ! Je te propose de valider $names tout de suite. $cheer", acts = done0.map { "validate:$it" })
            }
            has(t, "créer|crée|ajoute|nouvel objectif|nouvelle habitude|je veux|j'aimerais|je voudrais|commencer à|me mettre à") && !has(t, "propose") -> {
                val d = Coach.rules(text)
                Reply("Voici un objectif prêt à créer à partir de ta phrase. Tu pourras tout ajuster avant de l’enregistrer.", goal = "?" + text.trim().take(200))
                    .let { if (d.title.length < 3) Reply("Dis-moi en une phrase ce que tu veux suivre, par exemple « Courir 3 fois par semaine ».") else it }
            }
            has(t, "propose.*objectif|nouvel objectif|quel objectif|idée") -> {
                val ideas = listOf("Boire 1,5 L d’eau par jour" to "💧", "Marcher 20 minutes" to "🚶", "Lire 10 pages par jour" to "📚", "Méditer 5 minutes" to "🧘", "Se coucher avant 23 h" to "😴", "Pas d’écran au lit" to "📵")
                    .filter { (title, _) -> pg.myGoals(u.id).none { g -> g.title.lowercase().contains(title.lowercase().take(6)) } }
                val (title, _) = ideas.randomOrNull() ?: ("Marcher 20 minutes" to "🚶")
                Reply("Une idée facile à tenir : **$title**. Petit, régulier, et ça compte chaque jour. Tu veux l’essayer ?", goal = "?$title")
            }
            has(t, "briefing|programme|planning|aujourd'hui|journée|par quoi|commence|reste|à faire|quoi faire") -> {
                val wx = Coach.weather(ctx)?.let { " Météo : ${it.first.lowercase()}${it.second?.let { x -> ", ${Math.round(x)} °C" } ?: ""}." } ?: ""
                if (plan.due.isEmpty()) Reply("Rien n’est prévu aujourd’hui.$wx Profites-en pour souffler, ou ajoute une petite routine. $cheer")
                else if (left.isEmpty()) Reply("Tout est validé aujourd’hui ($done/${plan.due.size}) ! Ta série est à ${st.n} jour${if (st.n > 1) "s" else ""}.$wx $cheer")
                else {
                    val first = Coach.recommend(ctx).firstOrNull()
                    Reply("Il te reste ${left.size} objectif${if (left.size > 1) "s" else ""} : ${left.take(4).joinToString(", ") { it.g.title }}${if (left.size > 4) "…" else ""}.$wx" +
                        (first?.let { " Je commencerais par **${it.goal.title}** : ${it.reason.replaceFirstChar { c -> c.lowercase() }}" } ?: "") + " $cheer")
                }
            }
            has(t, "série|flamme|streak|sauver") -> Reply(when {
                st.lit -> "Ta flamme est déjà allumée aujourd’hui : série de ${st.n} jour${if (st.n > 1) "s" else ""}. $cheer"
                st.n > 0 -> "Ta série de ${st.n} jour${if (st.n > 1) "s" else ""} t’attend : une seule validation avant minuit suffit.${left.firstOrNull()?.let { " Le plus simple : **${it.g.title}**." } ?: ""} Un gel de série peut aussi la protéger."
                else -> "Une validation aujourd’hui et ta série démarre. Choisis l’objectif le plus facile, juste pour allumer la flamme."
            })
            has(t, "semaine|bilan|point|progr|statist") -> {
                val wins = pg.userCheckins(u.id).filter { pg.isWin(it) }; val w0 = weekStart(todayDate()); val w1 = w0.minusWeeks(1)
                val a = wins.count { parseYmd(it.date) >= w0 }; val b = wins.count { val d = parseYmd(it.date); d >= w1 && d < w0 }
                Reply("Cette semaine : **$a validation${if (a > 1) "s" else ""}**, contre $b la semaine dernière. Meilleure série : ${pg.bestStreak(u.id)} jours. " +
                    (if (a >= b) "Tu progresses, garde ce rythme ! " else "Un peu moins que la semaine passée : vise une validation de plus par jour. ") + cheer)
            }
            has(t, "fatigu|crevé|épuis|motiv|flemme|pas envie|découragé|nul|dur|stress|déprim|triste|malade") -> Reply(
                "C’est normal d’avoir des jours sans. Aujourd’hui, vise la **version minimale** : 5 minutes, ou la moitié de d’habitude, ça compte quand même. " +
                    "Tu peux aussi activer « Journée difficile » sur l’accueil pour alléger ta journée sans perdre ta série.${if (has(t, "malade|déprim")) " Et si ça dure, parles-en à un professionnel." else ""} ${p.cheer.first()}")
            has(t, "demain|soir|préparer") -> Reply("Pour demain : choisis dès ce soir ton objectif prioritaire et le moment où tu le feras. Prépare ce qu’il faut (tenue, livre, gourde) pour n’avoir plus qu’à t’y mettre. $cheer")
            has(t, "merci|cool|top|super|génial|parfait") -> Reply("Avec plaisir${who?.let { " $it" } ?: ""} ! $cheer")
            has(t, "salut|bonjour|hello|coucou|bonsoir|hey") -> Reply(p.hello.replace("{name}", who ?: "toi") + " " +
                (if (left.isEmpty()) "Tout est fait pour aujourd’hui." else "Il te reste ${left.size} objectif${if (left.size > 1) "s" else ""} aujourd’hui."))
            else -> Reply((if (LocalAi.installed(ctx)) "Je n’ai pas réussi à formuler une réponse complète cette fois. " else "Je fonctionne en mode simple (sans le modèle d’IA). ") +
                "Je peux te dire par quoi commencer, faire le point sur ta semaine, t’aider à sauver ta série ou te proposer un objectif. $cheer")
        }
    }
}
