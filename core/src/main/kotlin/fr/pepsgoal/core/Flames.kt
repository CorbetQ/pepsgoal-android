package fr.pepsgoal.core

/*
 * Flammes bonus : elles s'ajoutent au porte-monnaie de la boutique (comme les badges) sans changer le niveau.
 * Chaque bonus est noté dans `shopClaimed` pour n'être touché qu'une fois, même entre plusieurs appareils.
 */

/** Journée parfaite : tout ce qui était prévu est validé. */
const val PERFECT_BONUS = 20
/** Coffre de la semaine : au moins [WEEK_CHEST_DAYS] jours actifs du lundi au dimanche. */
const val WEEK_CHEST_BONUS = 60
const val WEEK_CHEST_DAYS = 5
/** Paliers de série (meilleure série), touchés une fois chacun. */
val STREAK_BONUS = listOf(3 to 15, 7 to 40, 14 to 60, 21 to 80, 30 to 120, 50 to 180, 100 to 350, 200 to 600, 365 to 1000)

/** Prix des icônes d'application (connus de l'application seulement), pour le remboursement après une baisse des prix. */
var shopExtraPrices: Map<String, Int> = emptyMap()

/** Prix actuel d'un article acheté (« icon:… », « theme:… », « bg:… »), null s'il est inconnu. */
fun shopPriceOf(key: String): Int? = when {
    key.startsWith("theme:") -> SHOP_THEMES.firstOrNull { "theme:${it.key}" == key }?.price
    key.startsWith("bg:") -> SHOP_BGS.firstOrNull { "bg:${it.key}" == key }?.price
    else -> shopExtraPrices[key]
}

private fun Pg.claim(u: fr.pepsgoal.core.model.User, key: String, flames: Int, text: String): Boolean {
    if (key in u.shopClaimed) return false
    u.shopClaimed = (u.shopClaimed + key).takeLast(400).toMutableList()
    if (flames > 0) { u.shopBonus += flames; notify(u.id, text, "badge") }
    return true
}

/**
 * Baisse des prix de la boutique (4.8.0) : les flammes dépensées sont ramenées au prix actuel des articles possédés,
 * la différence revient dans le porte-monnaie. Ne fait que baisser la dépense, donc rejouable sans risque.
 */
fun Pg.repriceShop() {
    val u = me() ?: return
    if (u.shopOwned.isEmpty() || u.shopSpent <= 0) return
    if (shopExtraPrices.isEmpty() && u.shopOwned.any { it.startsWith("icon:") }) return
    val now = u.shopOwned.sumOf { shopPriceOf(it) ?: 0 }
    if (u.shopSpent <= now) return
    val back = u.shopSpent - now
    u.shopSpent = now
    if (!claim(u, "reprice:48", 0, "")) { store.commit(); return }
    notify(u.id, "Les prix de la boutique ont baissé : ${pl(back, "flamme t’est rendue", "flammes te sont rendues")} sur tes achats.", "badge")
    store.commit()
}

/** Distribue les bonus gagnés (appelé avec la vérification des badges, après chaque validation et synchronisation). */
fun Pg.checkFlameBonus() {
    val u = me() ?: return
    var got = 0
    val t = today()
    if (t in u.perfectDays && claim(u, "perfect:$t", PERFECT_BONUS, "Journée parfaite : +$PERFECT_BONUS flammes pour la boutique.")) got += PERFECT_BONUS
    val wk = weekStart(todayDate())
    val active = activeDays(u.id)
    val n = (0..6).count { ymd(wk.plusDays(it.toLong())) in active }
    if (n >= WEEK_CHEST_DAYS && claim(u, "week:${ymd(wk)}", WEEK_CHEST_BONUS, "Coffre de la semaine ouvert ($n jours actifs) : +$WEEK_CHEST_BONUS flammes.")) got += WEEK_CHEST_BONUS
    val best = maxOf(bestStreak(u.id), globalStreak(u.id).n)
    for ((d, f) in STREAK_BONUS) if (best >= d && claim(u, "streak:$d", f, "Palier de série atteint (${pl(d, "jour", "jours")}) : +$f flammes.")) got += f
    if (got > 0) { store.commit(); hooks.toast("+$got flammes à dépenser dans la boutique", "flame") }
}

/** Une façon de gagner des flammes, avec sa progression (affichée dans la boutique et le profil). */
class FlameQuest(val icon: String, val title: String, val sub: String, val reward: Int, val progress: Double, val done: Boolean)

fun Pg.flameQuests(): List<FlameQuest> {
    val u = me() ?: return emptyList()
    val plan = todayPlan(u.id); val due = plan.due.size; val ok = plan.due.count { it.done }
    val t = today()
    val perfect = "perfect:$t" in u.shopClaimed
    val wk = weekStart(todayDate()); val active = activeDays(u.id)
    val n = (0..6).count { ymd(wk.plusDays(it.toLong())) in active }
    val chest = "week:${ymd(wk)}" in u.shopClaimed
    val best = maxOf(bestStreak(u.id), globalStreak(u.id).n)
    val next = STREAK_BONUS.firstOrNull { "streak:${it.first}" !in u.shopClaimed && best < it.first }
    val cur = globalStreak(u.id).n
    return listOfNotNull(
        FlameQuest("star", "Journée parfaite", if (perfect) "Touchée aujourd’hui" else if (due == 0) "Rien de prévu aujourd’hui" else "$ok / $due validés aujourd’hui", PERFECT_BONUS,
            if (perfect) 1.0 else if (due == 0) 0.0 else ok.toDouble() / due, perfect),
        FlameQuest("trophy", "Coffre de la semaine", if (chest) "Ouvert cette semaine" else "${minOf(n, WEEK_CHEST_DAYS)} / $WEEK_CHEST_DAYS jours actifs cette semaine", WEEK_CHEST_BONUS,
            minOf(1.0, n.toDouble() / WEEK_CHEST_DAYS), chest),
        next?.let { (d, f) -> FlameQuest("flameo", "Série de ${pl(d, "jour", "jours")}", "Série actuelle : ${pl(cur, "jour", "jours")}", f, (cur.toDouble() / d).coerceIn(0.0, 1.0), false) },
    )
}
