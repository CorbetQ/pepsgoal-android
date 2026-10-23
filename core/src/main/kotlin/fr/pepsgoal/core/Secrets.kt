package fr.pepsgoal.core

import java.time.DayOfWeek

/**
 * Succès cachés : des badges secrets, affichés « ??? » tant qu'ils ne sont pas obtenus (un indice flou seulement).
 * Chacun rapporte [SECRET_BONUS] flammes la première fois.
 */
const val SECRET_BONUS = 20

class Secret(val key: String, val name: String, val emoji: String, val desc: String, val hint: String)

val SECRETS = listOf(
    Secret("mirror", "Heure miroir", "🪞", "Valider à 4 h 44 pile.", "Une heure qui se regarde dans la glace"),
    Secret("owl10", "Noctambule", "🦉", "10 validations entre minuit et 4 h.", "Quand tout le monde dort…"),
    Secret("century", "Centurion", "💯", "100 validations dans un même mois.", "Un mois très, très chargé"),
    Secret("marathon", "Marathon du jour", "🏃", "10 validations le même jour.", "Une journée bien remplie"),
    Secret("twin", "Date jumelle", "👯", "Valider un jour où le jour et le mois sont identiques (6/6, 11/11…).", "Quand le calendrier voit double"),
    Secret("firstday", "Premier de l’an", "🎆", "Valider le 1er janvier avant 10 h.", "Les bonnes résolutions, tout de suite"),
    Secret("leap", "Saut de grenouille", "🐸", "Valider un 29 février.", "Un jour qui ne revient pas souvent"),
    Secret("comeback", "Grand retour", "🦅", "Revenir après 30 jours sans validation.", "Partir… pour mieux revenir"),
    Secret("rainbow", "Touche-à-tout", "🎨", "Valider 6 types d’objectifs différents.", "Goûter à tout"),
    Secret("seven7", "Sept sur sept", "🎰", "Atteindre 77 jours de série.", "Un chiffre porte-bonheur, deux fois"),
    Secret("friday13", "Pas superstitieux", "🐈‍⬛", "Valider un vendredi 13.", "Un jour qui fait peur à certains"),
    Secret("dawn5", "Aurore", "🌄", "5 validations avant 6 h du matin.", "Avant le lever du soleil"),
)

fun Pg.secretsFound(u: fr.pepsgoal.core.model.User? = me()): Set<String> =
    u?.shopClaimed?.filter { it.startsWith("secret:") }?.map { it.removePrefix("secret:") }?.toSet() ?: emptySet()

/** Succès cachés obtenus d'après l'historique (pur : ne modifie rien). */
fun Pg.secretsEarned(id: String = meId()): Set<String> {
    val wins = userCheckins(id).filter { isWin(it) }
    if (wins.isEmpty()) return emptySet()
    val out = HashSet<String>()
    val timed = wins.filter { it.at > 0 }.map { it to localDateTime(it.at) }
    if (timed.any { (_, t) -> t.hour == 4 && t.minute == 44 }) out += "mirror"
    if (timed.count { (_, t) -> t.hour in 0..3 } >= 10) out += "owl10"
    if (wins.groupingBy { it.date.substring(0, 7) }.eachCount().values.any { it >= 100 }) out += "century"
    if (wins.groupingBy { it.date }.eachCount().values.any { it >= 10 }) out += "marathon"
    if (wins.any { it.date.substring(5, 7) == it.date.substring(8, 10) }) out += "twin"
    if (timed.any { (c, t) -> c.date.endsWith("-01-01") && t.hour < 10 }) out += "firstday"
    if (wins.any { it.date.endsWith("-02-29") }) out += "leap"
    val days = wins.map { it.date }.toSortedSet().toList()
    if (days.zipWithNext().any { (a, b) -> daysBetween(parseYmd(a), parseYmd(b)) > 30 }) out += "comeback"
    if (wins.mapNotNull { goalById(it.goalId)?.type }.toSet().size >= 6) out += "rainbow"
    if (bestStreak(id) >= 77) out += "seven7"
    if (wins.any { val d = parseYmd(it.date); d.dayOfMonth == 13 && d.dayOfWeek == DayOfWeek.FRIDAY }) out += "friday13"
    if (timed.count { (_, t) -> t.hour in 4..5 } >= 5) out += "dawn5"
    return out
}

/** Note les nouveaux succès cachés : flammes et notification la première fois. Renvoie ceux qui viennent d'être obtenus. */
fun Pg.checkSecrets(): List<Secret> {
    val u = me() ?: return emptyList()
    val have = secretsFound(u); val fresh = SECRETS.filter { it.key in secretsEarned(u.id) && it.key !in have }
    if (fresh.isEmpty()) return fresh
    for (s in fresh) {
        u.shopClaimed = (u.shopClaimed + "secret:${s.key}").takeLast(400).toMutableList(); u.shopBonus += SECRET_BONUS
        notify(u.id, "Succès caché débloqué : « ${s.name} » ${s.emoji} +$SECRET_BONUS flammes.", "badge")
    }
    store.commit(); return fresh
}
