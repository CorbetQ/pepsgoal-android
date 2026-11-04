package fr.pepsgoal.core

import kotlin.test.*
class ModeVariantsTest {
    @Test fun everyAmbianceSupportsBothVariantsWithoutChangingEntitlements() {
        for (mode in (SHOP_MODES + Eggs.SECRET_MODES).filter { it.followsDayNight }) for (dark in listOf(false, true)) {
            val result = mode.forDark(dark)
            assertEquals(dark, result.dark)
            assertEquals(mode.key, result.key)
            assertEquals(mode.price, result.price)
            assertEquals(mode.accent, result.accent)
            assertEquals(result.bg, result.forDark(dark).bg)
            assertNotEquals(mode.forDark(false).bg, mode.forDark(true).bg)
        }
    }
    @Test fun clubAmbiancesKeepTheirBrandPaletteAcrossDayAndNight() {
        for (key in listOf("sco", "srfc", "psg", "fcb", "fcn")) {
            val mode = requireNotNull(shopMode("m:$key"))
            assertSame(mode, mode.forDark(false))
            assertSame(mode, mode.forDark(true))
        }
        assertEquals("#FFFFFF", shopMode("m:psg")!!.edge)
        assertEquals("#FFCD00", shopMode("m:fcb")!!.edge)
        assertEquals("#FFE000", shopMode("m:fcn")!!.accent.first())
    }

    @Test fun existingClubDiscoveriesRestoreTheNewExclusiveIcons() {
        for (key in listOf("sco", "srfc")) {
            val user = fr.pepsgoal.core.model.User("me", "test", "Test").apply { shopOwned.add("mode:$key") }
            val files = object : DbFiles {
                override fun read(name: String): String? = null
                override fun write(name: String, content: String) {}
                override fun delete(name: String) {}
            }
            val store = Store(files).apply { use(fr.pepsgoal.core.model.Db(session=user.id, users=mutableListOf(user)), false) }
            val pg = Pg(store)
            assertTrue(pg.reconcileThemePacks())
            assertTrue("icon:$key" in user.shopOwned)
            assertFalse(pg.reconcileThemePacks())
            assertEquals(0, user.shopBonus)
        }
    }
}
