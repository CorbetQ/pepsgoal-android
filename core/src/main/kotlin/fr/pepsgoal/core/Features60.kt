package fr.pepsgoal.core

import fr.pepsgoal.core.model.*

/* ---------------------------------------------------------------- Flammes offertes */

/** Montants proposés et plafond d'un cadeau. */
val GIFT_AMOUNTS = listOf(10, 25, 50, 100)
const val GIFT_MAX = 200

/** Vérifie qu'un cadeau de [n] flammes est possible ; renvoie un message d'erreur, ou null. */
fun Pg.giftError(n: Int): String? {
    val u = me() ?: return "Connecte-toi pour offrir des flammes."
    return when {
        n <= 0 -> "Choisis combien de flammes offrir."
        n > GIFT_MAX -> "$GIFT_MAX flammes au plus par cadeau."
        wallet(u) < n -> "Il te manque ${n - wallet(u)} flammes dans ton porte-monnaie."
        else -> null
    }
}

/** Retire les flammes offertes du porte-monnaie (le niveau, lui, ne baisse pas). */
fun Pg.spendGift(n: Int) { val u = me() ?: return; u.shopSpent += n; u.shopClaimed.add("giftout:${today()}:$n"); store.commit() }
/** Rend les flammes si l'envoi du cadeau a échoué. */
fun Pg.refundGift(n: Int) { val u = me() ?: return; u.shopSpent = maxOf(0, u.shopSpent - n); u.shopClaimed.remove("giftout:${today()}:$n"); store.commit() }

fun Pg.giftOpened(msgId: String) = me()?.shopClaimed?.contains("gift:$msgId") == true

/** Ouvre un cadeau reçu : les flammes rejoignent le porte-monnaie (une seule fois, sur tous les appareils). */
fun Pg.openGift(msgId: String, n: Int): Boolean {
    val u = me() ?: return false
    if (giftOpened(msgId)) return false
    val k = n.coerceIn(1, GIFT_MAX); u.shopBonus += k; u.shopClaimed.add("gift:$msgId"); u.shopClaimed.add("giftin:$msgId:$k"); store.commit()
    return true
}

/** Total des flammes offertes à des amis. */
fun Pg.flamesGiven(): Int = me()?.shopClaimed?.filter { it.startsWith("giftout:") }?.sumOf { it.substringAfterLast(':').toIntOrNull() ?: 0 } ?: 0

/* ---------------------------------------------------------------- Fiche privée d'un ami */

fun Pg.friendNote(id: String): String = me()?.friendNotes?.get(id).orEmpty()
fun Pg.friendBday(id: String): String? = me()?.friendBdays?.get(id)
fun Pg.setFriendNote(id: String, note: String) { val u = me() ?: return; val n = note.trim().take(300); if (n.isEmpty()) u.friendNotes.remove(id) else u.friendNotes[id] = n; store.commit() }
/** Anniversaire au format « MM-JJ », ou null pour l'effacer. */
fun Pg.setFriendBday(id: String, mmdd: String?) { val u = me() ?: return; if (mmdd == null) u.friendBdays.remove(id) else u.friendBdays[id] = mmdd; store.commit() }

/** Amis dont c'est l'anniversaire aujourd'hui (29 février fêté le 28 les années non bissextiles). */
fun Pg.birthdaysToday(): List<User> {
    val u = me() ?: return emptyList(); val d = todayDate()
    val k = "%02d-%02d".format(d.monthValue, d.dayOfMonth)
    val alsoLeap = d.monthValue == 2 && d.dayOfMonth == 28 && !d.isLeapYear
    return u.friendBdays.filter { (_, v) -> v == k || (alsoLeap && v == "02-29") }.keys.mapNotNull { userById(it) }
}

/** Prochains anniversaires (dans [days] jours au plus), du plus proche au plus lointain. */
fun Pg.nextBirthdays(days: Int = 30): List<Pair<User, Int>> {
    val u = me() ?: return emptyList(); val t = todayDate()
    return u.friendBdays.mapNotNull { (id, v) ->
        val x = userById(id) ?: return@mapNotNull null
        val (m, dd) = v.split("-").map { it.toInt() }
        var next = runCatching { java.time.LocalDate.of(t.year, m, dd) }.getOrElse { java.time.LocalDate.of(t.year, 2, 28) }
        if (next < t) next = runCatching { java.time.LocalDate.of(t.year + 1, m, dd) }.getOrElse { java.time.LocalDate.of(t.year + 1, 2, 28) }
        val n = daysBetween(t, next).toInt(); if (n <= days) x to n else null
    }.sortedBy { it.second }
}

/** Notification le jour de l'anniversaire d'un ami (une fois par an, même avec plusieurs appareils). */
fun Pg.checkBirthdays() {
    val u = me() ?: return; val y = todayDate().year
    for (x in birthdaysToday()) {
        val mark = "bday:${x.id}:$y"; if (mark in u.shopClaimed) continue
        u.shopClaimed.add(mark); store.commit()
        notify(u.id, "🎂 C’est l’anniversaire de ${first(x.name)} aujourd’hui ! Un petit mot ou quelques flammes ?", "friend")
    }
}

/* ---------------------------------------------------------------- Souvenirs */

class Memory(val label: String, val date: String, val goals: List<Goal>, val count: Int)

/** Ce que tu validais il y a un an, six mois et un mois (le même jour), s'il y a quelque chose. */
fun Pg.memories(): List<Memory> {
    val me = meId(); val t = todayDate()
    return listOf("Il y a un an" to t.minusYears(1), "Il y a six mois" to t.minusMonths(6), "Il y a un mois" to t.minusMonths(1)).mapNotNull { (l, d) ->
        val k = ymd(d)
        val cks = userCheckins(me).filter { it.date == k && isWin(it) }
        if (cks.isEmpty()) null else Memory(l, k, cks.mapNotNull { goalById(it.goalId) }.distinctBy { it.id }, cks.size)
    }
}

/* ---------------------------------------------------------------- Citation du jour */

val QUOTES = listOf(
    "Le succès, c’est tomber sept fois, se relever huit." to "Proverbe japonais",
    "Ce n’est pas parce que les choses sont difficiles que nous n’osons pas, c’est parce que nous n’osons pas qu’elles sont difficiles." to "Sénèque",
    "Le seul moyen de faire du bon travail est d’aimer ce que vous faites." to "Steve Jobs",
    "La motivation vous sert de départ. L’habitude vous fait continuer." to "Jim Ryun",
    "Un voyage de mille lieues commence toujours par un premier pas." to "Lao Tseu",
    "Nous sommes ce que nous répétons chaque jour." to "Aristote",
    "Il n’est pas nécessaire d’aller vite, l’important est de ne pas s’arrêter." to "Confucius",
    "Le meilleur moment pour planter un arbre était il y a vingt ans. Le deuxième meilleur moment est maintenant." to "Proverbe chinois",
    "La persévérance, c’est ce qui rend l’impossible possible, le possible probable et le probable certain." to "Robert Half",
    "Petit à petit, l’oiseau fait son nid." to "Proverbe français",
    "Rien ne sert de courir, il faut partir à point." to "Jean de La Fontaine",
    "Celui qui déplace une montagne commence par déplacer de petites pierres." to "Confucius",
    "Fais de ta vie un rêve, et d’un rêve, une réalité." to "Antoine de Saint-Exupéry",
    "Le courage n’est pas l’absence de peur, mais la capacité de la vaincre." to "Nelson Mandela",
    "La discipline est le pont entre les objectifs et leur accomplissement." to "Jim Rohn",
    "On ne voit bien qu’avec le cœur. L’essentiel est invisible pour les yeux." to "Antoine de Saint-Exupéry",
    "Chaque jour est une nouvelle chance de changer ta vie." to "Proverbe",
    "La vie, c’est comme une bicyclette : il faut avancer pour ne pas perdre l’équilibre." to "Albert Einstein",
    "Le génie est fait d’un pour cent d’inspiration et de quatre-vingt-dix-neuf pour cent de transpiration." to "Thomas Edison",
    "Tout ce que tu peux faire ou rêver de faire, commence-le." to "Goethe",
    "Ils ne savaient pas que c’était impossible, alors ils l’ont fait." to "Mark Twain",
    "Agis comme s’il était impossible d’échouer." to "Dorothea Brande",
    "Le bonheur n’est pas une destination, c’est une façon de voyager." to "Margaret Lee Runbeck",
    "Ce que l’on fait chaque jour compte plus que ce que l’on fait de temps en temps." to "Gretchen Rubin",
    "La patience est amère, mais son fruit est doux." to "Jean-Jacques Rousseau",
    "Soyez le changement que vous voulez voir dans le monde." to "Gandhi",
    "Le plus grand risque est de n’en prendre aucun." to "Mark Zuckerberg",
    "Il faut toujours viser la lune, car même en cas d’échec, on atterrit dans les étoiles." to "Oscar Wilde",
    "La flamme qui brûle deux fois plus fort dure deux fois moins longtemps : garde un rythme qui te ressemble." to "PepsGoal",
    "Une petite validation par jour vaut mieux qu’un grand plan jamais commencé." to "PepsGoal",
)

fun quoteOfDay(d: java.time.LocalDate = todayDate()) = QUOTES[(d.dayOfYear + d.year * 7) % QUOTES.size]

/* ---------------------------------------------------------------- Défi du jour */

val DAILY_CHALLENGES = listOf(
    "💧" to "Bois deux verres d’eau de plus qu’hier.", "🚶" to "Fais 10 minutes de marche sans ton téléphone.", "📵" to "Pas d’écran pendant le premier quart d’heure de la journée.",
    "🧘" to "Prends une minute pour respirer calmement.", "📖" to "Lis 10 pages d’un livre.", "🙏" to "Écris trois choses pour lesquelles tu es reconnaissant.",
    "🪜" to "Prends les escaliers à chaque fois aujourd’hui.", "🍎" to "Mange un fruit ou un légume de plus.", "📞" to "Appelle quelqu’un que tu n’as pas eu depuis longtemps.",
    "🧹" to "Range un tiroir ou une étagère.", "🌳" to "Passe 15 minutes dehors, à la lumière du jour.", "😴" to "Couche-toi 30 minutes plus tôt ce soir.",
    "🤸" to "Fais 20 squats ou étirements.", "✍️" to "Écris une phrase sur ta journée.", "🎵" to "Écoute un album en entier, sans rien faire d’autre.",
    "💬" to "Fais un compliment sincère à quelqu’un.", "🥗" to "Cuisine un repas maison.", "🧠" to "Apprends un mot ou une expression d’une autre langue.",
    "🚲" to "Fais un trajet à pied ou à vélo plutôt qu’en voiture.", "🔕" to "Coupe les notifications pendant une heure.", "🫶" to "Envoie un encouragement à un ami.",
    "🧊" to "Termine ta douche par 30 secondes d’eau fraîche.", "📅" to "Prépare ta journée de demain en 5 minutes.", "🍵" to "Remplace un café par un thé ou une infusion.",
    "🪴" to "Arrose tes plantes, ou installe-en une.", "🎨" to "Dessine quelque chose, même tout petit.", "🧺" to "Fais une tâche que tu repousses depuis des jours.",
    "🌅" to "Regarde le coucher ou le lever du soleil.", "🦶" to "Atteins 8 000 pas aujourd’hui.", "😊" to "Souris à trois inconnus.",
)
const val CHALLENGE_BONUS = 5

/** Défi du jour (le même toute la journée, différent pour chacun) ; [skip] : défis passés aujourd'hui. */
fun Pg.dailyChallenge(skip: Int = 0): Pair<String, String> {
    val h = (meId() + today()).hashCode().let { if (it < 0) -it else it }
    return DAILY_CHALLENGES[(h + skip) % DAILY_CHALLENGES.size]
}
fun Pg.challengeDone(): Boolean = me()?.shopClaimed?.contains("defi:${today()}") == true
/** Défi relevé : +5 flammes, une fois par jour. */
fun Pg.completeChallenge(): Boolean {
    val u = me() ?: return false; if (challengeDone()) return false
    u.shopClaimed.add("defi:${today()}"); u.shopBonus += CHALLENGE_BONUS; store.commit(); return true
}

/* ---------------------------------------------------------------- Calendrier de l'Avent */

/** Du 1er au 24 décembre : une case par jour, à ouvrir le jour même. */
fun adventOpen(d: java.time.LocalDate = todayDate()) = d.monthValue == 12 && d.dayOfMonth <= 24
/** Récompense de la case [day] : quelques flammes, et une grosse surprise le 24. */
fun adventReward(day: Int): Int = when (day) { 24 -> 100; 6, 12, 18 -> 30; else -> 5 + (day * 7) % 16 }
fun Pg.adventOpened(day: Int, year: Int = todayDate().year) = me()?.shopClaimed?.contains("avent:$year:$day") == true
fun Pg.openAdvent(): Int? {
    val u = me() ?: return null; val d = todayDate(); if (!adventOpen(d) || adventOpened(d.dayOfMonth)) return null
    val n = adventReward(d.dayOfMonth); u.shopClaimed.add("avent:${d.year}:${d.dayOfMonth}"); u.shopBonus += n; store.commit(); return n
}

/* ---------------------------------------------------------------- Record en vue */

/** Série actuelle, record, et jours restants pour le battre (null si le record est trop loin ou déjà battu). */
class RecordChase(val cur: Int, val best: Int, val left: Int)
fun Pg.recordChase(): RecordChase? {
    val id = meId(); val cur = globalStreak(id).n; val best = bestStreak(id)
    if (cur < 3) return null
    return if (cur >= best) RecordChase(cur, best, 0) else if (best - cur <= 7) RecordChase(cur, best, best - cur + 1) else null
}

/* ---------------------------------------------------------------- Météo de la semaine */

/** Jours de la semaine à venir les plus propices aux objectifs d'extérieur (peu de pluie, pas d'orage). */
fun goodOutdoorDays(f: Forecast?): List<WxDay> =
    f?.daily?.take(7)?.filter { (it.pop ?: 0.0) < 35 && Weather.wmo(it.code).cond in setOf("clear", "clouds") && (it.max ?: 20.0) in 4.0..31.0 } ?: emptyList()

/* ---------------------------------------------------------------- Thème qui suit le soleil */

/**
 * Sombre entre le coucher et le lever du soleil : heures de la ville de la météo si elles sont connues, sinon
 * estimation selon la saison (France).
 */
fun sunDark(w: Forecast?, now: java.time.LocalDateTime = localDateTime(nowMs())): Boolean {
    val t = now.hour * 60 + now.minute
    val d = w?.daily?.firstOrNull { it.day == ymd(now.toLocalDate()) }
    val rise = d?.sunrise?.drop(11)?.take(5)?.let(::hmMinutes); val set = d?.sunset?.drop(11)?.take(5)?.let(::hmMinutes)
    if (rise != null && set != null) return t < rise || t > set
    val k = kotlin.math.cos((now.dayOfYear - 172) / 365f * 2 * Math.PI.toFloat())
    val sr = ((7.25f - 1.25f * k) * 60).toInt(); val ss = ((19.75f + 2.1f * k) * 60).toInt()
    return t < sr || t > ss
}
