package fr.pepsgoal.core

/* ---------------------------------------------------------------- Niveaux */

/**
 * Dix paliers de cinq niveaux, de l'Étincelle à la Supernova. Les flammes gagnées en validant font monter le niveau ;
 * les dépenser ou les offrir ne le fait jamais baisser.
 */
val LEVEL_TIERS = listOf("Étincelle", "Braise", "Flamme", "Brasier", "Incendie", "Fournaise", "Volcan", "Étoile", "Soleil", "Supernova")
const val LEVELS_PER_TIER = 5
const val LEVEL_MAX = 50

/** Flammes gagnées par jour pour un usage régulier (quelques validations par jour) : sert à estimer le temps restant. */
const val LEVEL_DAILY_PACE = 70

/**
 * Flammes nécessaires pour atteindre le niveau [n] (niveau 1 : 0). Courbe progressive (puissance 1,9) : environ deux
 * semaines pour Braise, deux mois pour Flamme, un an pour Fournaise et trois ans pour Supernova à un rythme régulier.
 * Au-delà du niveau 50, chaque éclat de Supernova demande 6 000 flammes de plus.
 */
fun levelThreshold(n: Int): Int = when {
    n <= 1 -> 0
    n <= LEVEL_MAX -> (Math.round(60.0 * Math.pow((n - 1).toDouble(), 1.9) / 10.0) * 10).toInt()
    else -> levelThreshold(LEVEL_MAX) + (n - LEVEL_MAX) * 6000
}

private val ROMAN = listOf("I", "II", "III", "IV", "V")

/** Palier (1 à 10) d'un niveau. */
fun levelTier(n: Int): Int = ((minOf(n, LEVEL_MAX) - 1) / LEVELS_PER_TIER + 1).coerceIn(1, LEVEL_TIERS.size)

/** Nom d'un niveau : « Flamme III », puis « Supernova ✦2 » au-delà du niveau 50. */
fun levelName(n: Int): String =
    if (n > LEVEL_MAX) "${LEVEL_TIERS.last()} ✦${n - LEVEL_MAX}"
    else "${LEVEL_TIERS[levelTier(n) - 1]} ${ROMAN[(n - 1) % LEVELS_PER_TIER]}"

/** Niveau correspondant à [pts] flammes gagnées. */
fun levelOf(pts: Int): Level {
    var n = 1
    while (n < 500 && levelThreshold(n + 1) <= pts) n++
    val cur = levelThreshold(n); val nxt = levelThreshold(n + 1)
    val tier = levelTier(n)
    val tierStart = levelThreshold((tier - 1) * LEVELS_PER_TIER + 1)
    val nextTierLv = tier * LEVELS_PER_TIER + 1
    val hasNextTier = tier < LEVEL_TIERS.size
    val tierEnd = if (hasNextTier) levelThreshold(nextTierLv) else nxt
    return Level(n, levelName(n), pts, nxt, levelName(n + 1), (pts - cur).toDouble() / (nxt - cur),
        tier, LEVEL_TIERS[tier - 1], if (n > LEVEL_MAX) LEVELS_PER_TIER else (n - 1) % LEVELS_PER_TIER + 1,
        if (hasNextTier) ((pts - tierStart).toDouble() / (tierEnd - tierStart)).coerceIn(0.0, 1.0) else 1.0,
        if (hasNextTier) levelThreshold(nextTierLv) else null, if (hasNextTier) LEVEL_TIERS[tier] else null)
}

/** Récompense d'un niveau atteint : 10 flammes par niveau, 50 de plus pour un nouveau palier. */
fun levelReward(n: Int): Int = if (n <= 1) 0 else 10 + if ((n - 1) % LEVELS_PER_TIER == 0) 50 else 0

/** Estimation lisible du temps restant pour [flames] flammes au rythme [LEVEL_DAILY_PACE] : « ~3 semaines ». */
fun levelEta(flames: Int): String {
    val d = Math.ceil(flames.toDouble() / LEVEL_DAILY_PACE).toInt().coerceAtLeast(1)
    return when {
        d < 14 -> "~${pl(d, "jour", "jours")}"
        d < 60 -> "~${pl(d / 7, "semaine", "semaines")}"
        d < 730 -> "~${pl(d / 30, "mois", "mois")}"
        else -> "~${pl(d / 365, "an", "ans")}"
    }
}

/**
 * Niveau gagné : notification et flammes pour la boutique (une seule fois par niveau, sur tous les appareils). À la
 * première vérification avec ce système (6.3.0), les niveaux déjà atteints sont notés sans récompense.
 */
fun Pg.checkLevelUp() {
    val u = me() ?: return
    val lv = level(points(u.id)).idx
    if ("lvl:init" !in u.shopClaimed) {
        u.shopClaimed = trimClaimed(u.shopClaimed + "lvl:init" + "lvl:$lv"); store.commit(); return
    }
    val last = u.shopClaimed.filter { it.startsWith("lvl:") }.mapNotNull { it.substringAfter(':').toIntOrNull() }.maxOrNull() ?: 1
    if (lv <= last) return
    var gain = 0
    for (n in last + 1..lv) gain += levelReward(n)
    u.shopClaimed = trimClaimed(u.shopClaimed.filterNot { it.startsWith("lvl:") && it != "lvl:init" } + "lvl:$lv")
    u.shopBonus += gain
    store.commit()
    val newTier = levelTier(lv) > levelTier(last)
    notify(u.id, if (newTier) "🔥 Nouveau palier : ${levelName(lv)} ! +$gain flammes pour la boutique." else "Niveau ${lv} atteint : ${levelName(lv)}. +$gain flammes pour la boutique.", "badge")
    hooks.toast(if (newTier) "Nouveau palier : ${LEVEL_TIERS[levelTier(lv) - 1]} ! +$gain 🔥" else "Niveau $lv : ${levelName(lv)} · +$gain 🔥", "flame")
}
