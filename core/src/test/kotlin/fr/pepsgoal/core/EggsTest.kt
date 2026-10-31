package fr.pepsgoal.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EggsTest {
    @Test fun sixSevenFromSixtySeven() {
        assertFalse(Eggs.sixSeven(0)); assertFalse(Eggs.sixSeven(66))
        assertTrue(Eggs.sixSeven(67)); assertTrue(Eggs.sixSeven(68)); assertTrue(Eggs.sixSeven(400))
        assertTrue(Eggs.ALL.any { it.key == "sixseven" })
    }
}

class ScoEggTest {
    private val files = object : DbFiles {
        val m = HashMap<String, String>()
        override fun read(name: String) = m[name]
        override fun write(name: String, content: String) { m[name] = content }
        override fun delete(name: String) { m.remove(name) }
    }

    @Test fun scoUnlocksBackgroundAndMode() {
        val store = Store(files)
        val u = fr.pepsgoal.core.model.User("me", "camille", "Camille Roux")
        store.use(fr.pepsgoal.core.model.Db(session = "me", users = mutableListOf(u)), false)
        val pg = Pg(store)
        kotlin.test.assertEquals("sco", Eggs.search("SCO")?.egg); kotlin.test.assertEquals("sco", Eggs.search(" sco ")?.egg); kotlin.test.assertEquals("sco", Eggs.search("Sco")?.egg)
        kotlin.test.assertEquals(null, Eggs.search("scoop"))
        assertFalse(pg.owns("mode:sco", 1, u)); assertFalse(pg.owns("bg:kopa", 1, u))
        pg.applySco(); kotlin.test.assertEquals(false, u.settings.theme == "m:sco") // pas encore débloqué : rien n'est appliqué
        assertTrue(pg.findEgg("sco")); kotlin.test.assertEquals(Eggs.BONUS, u.shopBonus)
        assertTrue(pg.owns("mode:sco", 1, u)); assertTrue(pg.owns("bg:kopa", 1, u))
        assertFalse(pg.findEgg("sco")); kotlin.test.assertEquals(Eggs.BONUS, u.shopBonus)
        // Débloquer ne veut pas dire appliquer
        kotlin.test.assertEquals(false, u.settings.bg == "s:kopa")
        pg.applySco()
        kotlin.test.assertEquals("m:sco", u.settings.theme); kotlin.test.assertEquals("s:kopa", u.settings.bg)
        kotlin.test.assertEquals("Noir & Blanc", themeName("m:sco")); kotlin.test.assertEquals("dark", baseTheme("m:sco"))
        // Secrets : absents de la boutique, présents parmi tous les fonds
        kotlin.test.assertEquals(null, shopPriceOf("bg:kopa")); kotlin.test.assertEquals(null, shopPriceOf("mode:sco"))
        assertTrue(SHOP_BGS.none { it.key == "kopa" }); assertTrue(ALL_BGS.any { it.key == "kopa" })
    }

    /** Contraste WCAG du texte de l'ambiance Noir & Blanc sur son fond et ses cartes (≥ 4,5:1). */
    @Test fun scoContrast() {
        fun lum(h: String): Double { val c = listOf(1, 3, 5).map { h.substring(it, it + 2).toInt(16) / 255.0 }.map { if (it <= .03928) it / 12.92 else Math.pow((it + .055) / 1.055, 2.4) }; return .2126 * c[0] + .7152 * c[1] + .0722 * c[2] }
        fun cr(a: String, b: String): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + .05) / (minOf(x, y) + .05) }
        val m = Eggs.SECRET_MODES.first { it.key == "sco" }
        for (bg in listOf(m.bg, m.surface!!, m.surface2!!)) assertTrue(cr(m.ink, bg) >= 4.5, "texte sur $bg")
        for (a in m.accent) assertTrue(cr("#2A1420", a) >= 4.5, "texte prune sur l’accent $a")
    }

    @kotlin.test.Test
    fun feurOnlyWhenThePostEndsWithQuoi() {
        assertTrue(Eggs.feur("Tu sais quoi ?")); assertTrue(Eggs.feur("Et alors QUOI?")); assertTrue(Eggs.feur("quoi"))
        assertTrue(Eggs.feur("Devine quoi ?  "))
        kotlin.test.assertFalse(Eggs.feur("Pourquoi ?")); kotlin.test.assertFalse(Eggs.feur("quoi de neuf ?")); kotlin.test.assertFalse(Eggs.feur("Quoicoubeh"))
        assertTrue(Eggs.ALL.any { it.key == "feur" })
    }
}
