package fr.pepsgoal.core

import fr.pepsgoal.core.model.*
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IfThenTest {
    private val files = object : DbFiles {
        val m = HashMap<String, String>()
        override fun read(name: String) = m[name]
        override fun write(name: String, content: String) { m[name] = content }
        override fun delete(name: String) { m.remove(name) }
    }
    private lateinit var pg: Pg
    private val zone = ZoneId.of("Europe/Paris")

    @BeforeTest fun setUp() {
        Clock.zone = { zone }; Clock.now = { LocalDateTime.of(2026, 10, 21, 12, 0).atZone(zone).toInstant().toEpochMilli() }
        val store = Store(files)
        val u = User("me", "camille", "Camille Roux", createdAt = nowMs() - 40 * DAY_MS)
        u.places.add(SavedPlace("gym", "la salle", "🏋️", 47.47, -0.55))
        store.use(Db(session = "me", users = mutableListOf(u)), false)
        pg = Pg(store)
    }

    @Test fun phrases() {
        assertEquals("S’il est 07:30, alors je médite 5 minutes.", ifThenPhrase("time", "07:30", null, null, "je médite 5 minutes", "Méditer"))
        assertEquals("Si j’arrive à la salle, alors je fais 10 pompes.", ifThenPhrase("arrive", "", "la salle", null, "Alors, je fais 10 pompes !", "Pompes"))
        assertEquals("Si je pars de bureau, alors je marche 10 minutes.", ifThenPhrase("leave", "", "bureau", null, "alors je marche 10 minutes.", "Marche"))
        assertEquals("Si j’ai fini « Café », alors je fais « Lire ».", ifThenPhrase("after", "", null, "Café", "  ", "Lire"))
        assertNull(ifThenPhrase("arrive", "", null, null, "x", "y"))
        assertNull(ifThenPhrase(null, "", null, null, "x", "y"))
    }

    @Test fun goalPhraseAndSync() {
        val cafe = Goal(uid(), "me", "routine", "Café").also { pg.db.goals.add(it) }
        val g = Goal(uid(), "me", "routine", "Lire").also { pg.db.goals.add(it) }
        g.ifKind = "arrive"; g.ifPlace = "gym"; g.ifThen = "je m’étire"
        assertEquals("Si j’arrive à la salle, alors je m’étire.", pg.ifThenOf(g))
        // Heure : devient le rappel de l'objectif
        val old = Goal(g.id, "me", "routine", "Lire").also { it.data = LinkedHashMap(g.data) }
        g.ifKind = "time"; g.ifTime = "18:15"; syncIfThen(g, old)
        assertEquals("18:15", g.remindAt)
        assertEquals("S’il est 18:15, alors je m’étire.", pg.ifThenReminder(g, "time", "x"))
        assertEquals("x", pg.ifThenReminder(g, "place", "x"))
        // Puis « après un objectif » : le rappel posé par l'heure est retiré, l'enchaînement est posé
        val old2 = Goal(g.id, "me", "routine", "Lire").also { it.data = LinkedHashMap(g.data) }
        g.ifKind = "after"; g.ifGoal = cafe.id; syncIfThen(g, old2)
        assertNull(g.remindAt)
        assertEquals(cafe.id, g.after)
        // Retiré : l'enchaînement aussi
        val old3 = Goal(g.id, "me", "routine", "Lire").also { it.data = LinkedHashMap(g.data) }
        g.ifKind = null; syncIfThen(g, old3)
        assertNull(g.after); assertNull(pg.ifThenOf(g))
        // Objectif discret : jamais la phrase dans un rappel
        g.ifKind = "time"; g.discreet = true
        assertEquals("y", pg.ifThenReminder(g, "time", "y"))
    }
}
