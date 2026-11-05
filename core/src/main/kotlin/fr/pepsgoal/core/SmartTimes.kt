package fr.pepsgoal.core

import fr.pepsgoal.core.model.Checkin

/**
 * Heures habituelles des rappels intelligents, calculées en un seul parcours des validations : minute médiane de chaque
 * objectif (au moins 4 réussites sur 30 jours) et minute de la dernière validation de chaque jour (21 jours).
 */
class SmartTimes(val usual: Map<String, Int>, val lastOfDay: List<Int>) {
    companion object {
        /** [visits] (tests) : compte les validations parcourues. */
        fun compute(checkins: List<Checkin>, me: String, isWin: (Checkin) -> Boolean, nowMs: Long, minuteOf: (Long) -> Int = { localDateTime(it).let { t -> t.hour * 60 + t.minute } }, visits: IntArray? = null): SmartTimes {
            val since30 = nowMs - 30L * 86_400_000; val since21 = nowMs - 21L * 86_400_000
            val byGoal = HashMap<String, MutableList<Int>>(); val byDay = HashMap<String, Int>()
            for (c in checkins) {
                visits?.let { it[0]++ }
                if (c.userId != me || c.at <= since30 || !isWin(c)) continue
                val m = minuteOf(c.at)
                byGoal.getOrPut(c.goalId) { mutableListOf() }.add(m)
                if (c.at > since21) byDay[c.date] = maxOf(byDay[c.date] ?: -1, m)
            }
            val usual = byGoal.mapNotNull { (g, l) -> if (l.size < 4) null else g to l.sorted()[l.size / 2] }.toMap()
            return SmartTimes(usual, byDay.values.sorted())
        }
    }
}

/** Cache d'une valeur pour une clé (jour + empreinte des validations) : recalculée seulement quand la clé change. */
class KeyedCache<K, V> {
    private var key: Any? = NONE
    private var value: V? = null
    @Synchronized fun get(k: K, compute: () -> V): V {
        @Suppress("UNCHECKED_CAST")
        if (key == k) return value as V
        return compute().also { key = k; value = it }
    }
    @Synchronized fun clear() { key = NONE; value = null }
    private object NONE
}
