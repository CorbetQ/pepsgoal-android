package fr.pepsgoal.core

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Easter Eggs : petites surprises cachées dans l'application. Chacune se trouve une seule fois et rapporte quelques
 * flammes ; la collection (Profil › Collection) montre celles déjà trouvées et un indice pour les autres.
 */
object Eggs {
    const val BONUS = 15

    /** Surprise : clé, nom une fois trouvée, indice tant qu'elle reste cachée. */
    class Egg(val key: String, val name: String, val hint: String, val emoji: String)

    val ALL = listOf(
        Egg("angers", "Douceur angevine", "Cherche une douceur bien de chez nous", "🏰"),
        Egg("tram", "Terminus : ta série", "Trois lignes, une seule direction", "🚋"),
        Egg("answer", "La grande réponse", "La question sur la vie, l’univers et le reste", "🌌"),
        Egg("konami", "Code secret", "Un vieux code de jeu vidéo, dans la recherche", "🎮"),
        Egg("version", "Curieux de nature", "Insiste un peu sur le numéro de version", "🔍"),
        Egg("tickle", "Chatouilles", "La grande flamme de l’accueil est chatouilleuse", "🔥"),
        Egg("joke", "Coach farceur", "Demande une blague à ton coach", "😄"),
        Egg("owl", "Oiseau de nuit", "Ouvre l’application à l’heure où tout le monde dort", "🦉"),
        Egg("calendar", "Jour spécial", "Reviens un jour de fête", "📅"),
    )

    /** Ambiance secrète débloquée par la surprise « angers » (jamais vendue dans la boutique). */
    val SECRET_MODES = listOf(
        ShopMode("angers", "Douceur angevine", "Tuffeau, ardoise et Loire · ambiance secrète", 1, false,
            "#F1ECE1", "#222932", "#FFFCF5", listOf("#CDBF9F", "#5E7A8C", "#E9873A"), listOf("#F2A65A", "#E9873A", "#3E6E8E"))
    )

    /** Réponse de la recherche : texte, surprise trouvée, et effet visuel (« confetti », « fish »…). */
    class Hit(val text: String, val egg: String?, val fx: String? = null)

    private fun norm(q: String) = q.trim().lowercase().replace('’', '\'').replace(Regex("\\s+"), " ")

    /** Surprise cachée derrière une recherche (null : recherche ordinaire). */
    fun search(q0: String): Hit? {
        val q = norm(q0)
        return when {
            q in setOf("angers", "anjou", "angevin", "angevine", "douceur angevine", "49000", "49100") ->
                Hit("Douceur angevine ! Tuffeau, ardoise et bords de Maine : une ambiance secrète t’attend dans Apparence.", "angers", "confetti")
            q in setOf("tram", "tramway", "ligne a", "ligne b", "ligne c") ->
                Hit("Ligne A, B ou C : ici, toutes les lignes mènent à ta série. Prochain arrêt : ta prochaine validation 🚋", "tram")
            q == "42" || q.contains("vie, l'univers") || q == "la grande question" ->
                Hit("42. C’est la réponse. Pour tes objectifs, c’est plus simple : une validation à la fois.", "answer")
            q in setOf("konami", "↑↑↓↓←→←→ba", "uuddlrlrba", "haut haut bas bas gauche droite gauche droite b a") ->
                Hit("Code accepté. +30 vies… ah non, ici ce sont des flammes.", "konami", "confetti")
            q in setOf("easter egg", "easter eggs", "œuf", "oeuf", "surprise", "surprises", "secret") ->
                Hit("Il y a bien des surprises cachées dans PepsGoal… Ta collection est dans Profil › Collection.", null)
            q == "pepsgoal" -> Hit("Oui, c’est moi ! Merci d’avoir pensé à me chercher 🔥", null)
            else -> null
        }
    }

    /** Message d'un jour de fête (montré une fois dans la journée) et effet visuel associé. */
    class Day(val key: String, val text: String, val fx: String? = null)

    fun day(d: LocalDate = todayDate()): Day? {
        val md = d.monthValue * 100 + d.dayOfMonth
        return when {
            md == 101 -> Day("newyear", "Bonne année ! Nouvelle année, nouvelle série 🎆", "confetti")
            md == 314 -> Day("pi", "Joyeux jour de π ! 3,14159… objectifs ? Un seul suffit pour aujourd’hui.")
            md == 401 -> Day("fish", "Poisson d’avril ! 🐟 Celui-là, tu ne l’as pas vu venir.", "fish")
            md == 504 -> Day("force", "May the 4th be with you : que la Force soit avec ta série.")
            md == 621 -> Day("music", "Fête de la musique 🎶 : un objectif validé, c’est une note de plus.")
            md == 1031 -> Day("halloween", "Joyeux Halloween 🎃 Ta flamme a pris des couleurs étranges…", "spooky")
            md == 1225 -> Day("xmas", "Joyeux Noël 🎄 Même aujourd’hui, une petite validation fait plaisir.", "confetti")
            d.dayOfMonth == 13 && d.dayOfWeek == DayOfWeek.FRIDAY -> Day("friday13", "Vendredi 13 : ta série ne craint rien, elle.")
            else -> null
        }
    }

    /** Couleurs de la flamme un jour spécial (null : couleurs habituelles). */
    fun flameColors(d: LocalDate = todayDate()): List<String>? = when (d.monthValue * 100 + d.dayOfMonth) {
        1031 -> listOf("#B6FF2E", "#7B2BFF", "#FF7A00")
        401 -> listOf("#7DD3FC", "#22D3FF", "#4D9BFF")
        else -> null
    }

    /** Heure de l'oiseau de nuit (3 h du matin). */
    fun owl(hour: Int) = hour == 3

    private val JOKES = listOf(
        "Pourquoi les objectifs n’ont jamais froid ? Parce qu’ils ont une série de flammes 🔥",
        "J’ai voulu courir un marathon… j’ai validé « mettre mes baskets ». C’est un début !",
        "Quel est le sport préféré des habitudes ? Le saut de lit… à l’heure.",
        "Ma série et moi, c’est comme le tram : on ne s’arrête qu’aux arrêts prévus.",
        "Le secret de la motivation ? Commencer. Le secret de la flemme ? Commencer… demain.",
        "Pourquoi le réveil est-il si motivé ? Parce qu’il sonne toujours ses objectifs.",
    )

    /** Réponse amusante du coach (null : rien de spécial), et surprise trouvée. */
    fun coach(text: String): Pair<String, String?>? {
        val t = norm(text)
        return when {
            Regex("blague|fais[- ]moi rire|raconte.*(drôle|histoire)|une vanne").containsMatchIn(t) -> JOKES.random() to "joke"
            Regex("qui t'a (créé|cree|fait|développé)|qui es[- ]tu").containsMatchIn(t) -> "Je suis le coach de PepsGoal, né à Angers entre une tranche de pâté aux prunes et un trajet en tram. Ma mission : t’aider à tenir tes objectifs, un jour après l’autre." to null
            t == "42" -> "42, bien sûr. Mais la vraie question, c’est : quel objectif valides-tu aujourd’hui ?" to "answer"
            else -> null
        }
    }
}

/** Surprises déjà trouvées. */
fun Pg.eggsFound(u: fr.pepsgoal.core.model.User? = me()): Set<String> =
    u?.shopClaimed?.filter { it.startsWith("egg:") }?.map { it.removePrefix("egg:") }?.toSet() ?: emptySet()

/**
 * Note une surprise trouvée : la première fois, quelques flammes pour la boutique (et l'ambiance secrète pour
 * « angers »). Renvoie vrai si elle vient d'être trouvée.
 */
fun Pg.findEgg(key: String): Boolean {
    val u = me() ?: return false
    if (Eggs.ALL.none { it.key == key } || "egg:$key" in u.shopClaimed) return false
    u.shopClaimed = (u.shopClaimed + "egg:$key").takeLast(400).toMutableList()
    u.shopBonus += Eggs.BONUS
    if (key == "angers" && "mode:angers" !in u.shopOwned) u.shopOwned = (u.shopOwned + "mode:angers").toMutableList()
    val e = Eggs.ALL.first { it.key == key }
    notify(u.id, "Surprise trouvée : « ${e.name} » ${e.emoji} +${Eggs.BONUS} flammes.", "badge")
    store.commit()
    return true
}
