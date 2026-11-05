package fr.pepsgoal.core

import fr.pepsgoal.core.model.Checkin
import kotlin.test.*

class SmartTimesTest {
    private val now = 1_800_000_000_000L
    private fun ck(id: String, goal: String, daysAgo: Int, minute: Int, user: String = "me") =
        Checkin(id, goal, user, "d$daysAgo", "done", at = now - daysAgo * 86_400_000L + minute)

    @Test fun usualMinutesAndLastOfDayInOnePass() {
        val list = (1..5).map { ck("a$it", "g", it, 480 + it) } + ck("b", "h", 2, 600) + ck("x", "g", 3, 10, user = "other") + ck("old", "g", 40, 0)
        val visits = IntArray(1)
        // minuteOf : la minute est codée dans les millisecondes de l'horodatage (pas de fuseau dans le test)
        val t = SmartTimes.compute(list, "me", { true }, now, minuteOf = { ms: Long -> ((ms % 86_400_000L) % 1440).toInt() }, visits = visits)
        assertEquals(list.size, visits[0], "Un seul parcours")
        assertNotNull(t.usual["g"]); assertNull(t.usual["h"], "Moins de 4 réussites : pas d'heure habituelle")
        assertEquals(5, t.lastOfDay.size)
    }

    @Test fun planIsCachedUntilCheckinsChange() {
        val list = mutableListOf(ck("a", "g", 1, 1), ck("b", "g", 2, 2))
        val cache = KeyedCache<List<Any>, SmartTimes>(); val visits = IntArray(1)
        fun get() = cache.get(listOf("2026-11-05", list.size, list.last().id)) { SmartTimes.compute(list, "me", { true }, now, visits = visits) }
        get(); get()
        assertEquals(2, visits[0], "Deux appels le même jour : un seul parcours des validations")
        list.add(ck("c", "g", 3, 3)); get()
        assertEquals(5, visits[0], "Une validation invalide le cache")
    }
}
