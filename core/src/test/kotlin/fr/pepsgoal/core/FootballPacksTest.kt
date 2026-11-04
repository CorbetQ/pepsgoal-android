package fr.pepsgoal.core

import fr.pepsgoal.core.model.Db
import fr.pepsgoal.core.model.User
import kotlin.test.*

class FootballPacksTest {
    private class Files : DbFiles {
        val data = mutableMapOf<String, String>()
        override fun read(name: String) = data[name]
        override fun write(name: String, content: String) { data[name] = content }
        override fun delete(name: String) { data.remove(name) }
    }

    private class Fixture {
        val files = Files()
        val user = User("me", "test", "Test").apply { shopBonus = 100 }
        val store = Store(files)
        val pg: Pg
        init {
            store.use(Db(session = user.id, users = mutableListOf(user)), false)
            pg = Pg(store)
        }
    }

    private val keys = listOf("psg", "fcb", "fcn")
    private fun pack(key: String) = requireNotNull(themePack(key))

    @Test fun footballSearchAliasesAreExactAfterCaseAndWhitespaceNormalization() {
        val aliases = mapOf(
            "psg" to listOf("psg", " PSG ", "Paris Saint-Germain", " paris saint germain ", "PARIS-SAINT-GERMAIN"),
            "fcb" to listOf("fcb", " Barça ", "barca", "FC Barcelone", "fc barcelona"),
            "fcn" to listOf("fcn", " FC Nantes ", "football club de nantes", "ALLEZ NANTES"),
        )
        aliases.forEach { (egg, queries) -> queries.forEach { assertEquals(egg, Eggs.search(it)?.egg, it) } }
        listOf("psgains", "fcb news", "Nantes").forEach { assertNull(Eggs.search(it), it) }
    }

    @Test fun discoveryGrantsThreeRightsAndFifteenFlamesOnceWithoutApplying() {
        for (key in keys) {
            val f = Fixture()
            val settings = settings(f.user)
            assertTrue(f.pg.findEgg(key))
            val p = pack(key)
            assertTrue(p.components().all { it.key in f.user.shopOwned }, key)
            assertTrue(f.user.shopOwned.containsAll(listOf("icon:$key", "bg:$key", "mode:$key")), key)
            assertEquals(115, f.user.shopBonus, key)
            assertEquals(settings, settings(f.user), "Discovery must not apply $key")
            assertFalse(f.pg.findEgg(key), "The $key reward is one-time")
            assertEquals(115, f.user.shopBonus, key)
        }
    }

    @Test fun secretPurchasesAndGiftsAreBlockedBeforeDiscoveryEvenAtZeroPrice() {
        val f = Fixture()
        for (key in keys) {
            assertFalse(f.pg.packQuote(pack(key)).unlocked, key)
            assertNotNull(f.pg.buyPack(key), key)
            for (item in listOf("icon:$key", "bg:$key", "mode:$key")) {
                assertNotNull(f.pg.buy(item, 0), "Zero-price direct purchase: $item")
                assertNotNull(f.pg.giftItemError(item), "Secret item cannot be gifted: $item")
            }
        }
        assertTrue(f.user.shopOwned.isEmpty())
        assertEquals(0, f.user.shopSpent)
        assertEquals(100, f.user.shopBonus)
    }

    @Test fun discoveredPackIsFreeAndAppliesOnlyWhenRequested() {
        for (key in keys) {
            val f = Fixture()
            f.pg.findEgg(key)
            val before = settings(f.user)
            val quote = f.pg.packQuote(pack(key))
            assertTrue(quote.owned, key)
            assertEquals(0, quote.price, key)
            assertNull(f.pg.buyPack(key), key)
            assertEquals(before, settings(f.user), "Buying must not apply $key")
            assertNull(f.pg.applyPack(key), key)
            assertTrue(f.pg.packMatches(pack(key)), key)
            assertEquals(0, f.user.shopSpent, key)
        }
    }

    @Test fun reconciliationRestoresSecretComponentsWithoutRepeatingDiscoveryBonus() {
        for (key in keys) {
            val f = Fixture()
            f.user.shopClaimed.add("egg:$key")
            val bonus = f.user.shopBonus
            assertTrue(f.pg.reconcileThemePacks(), key)
            assertTrue(f.user.shopOwned.containsAll(listOf("icon:$key", "bg:$key", "mode:$key")), key)
            assertEquals(bonus, f.user.shopBonus, key)
            assertFalse(f.pg.reconcileThemePacks(), key)
            assertEquals(bonus, f.user.shopBonus, key)

            val legacy = Fixture()
            legacy.user.shopOwned.add("mode:$key")
            assertTrue(legacy.pg.reconcileThemePacks(), key)
            assertTrue(legacy.user.shopOwned.containsAll(listOf("icon:$key", "bg:$key", "mode:$key")), key)
            assertEquals(100, legacy.user.shopBonus, key)
        }
    }

    @Test fun discoveryAndEntitlementsSurviveStoreReload() {
        for (key in keys) {
            val f = Fixture()
            f.pg.findEgg(key)
            val saved = JSON.decodeFromString(Db.serializer(), requireNotNull(f.files.read(Store.LOCAL)))
            val restored = Store(Files()).apply { use(saved, false) }
            val pg = Pg(restored)
            assertTrue(pg.eggsFound().contains(key), key)
            assertTrue(pg.packQuote(pack(key)).owned, key)
            assertEquals(115, pg.me()!!.shopBonus, key)
            assertNull(pg.applyPack(key), key)
            assertTrue(pg.packMatches(pack(key)), key)
        }
    }

    @Test fun secretModeTextAndAccentsMeetContrastTargets() {
        val prune = "#2A1420"
        for (key in keys) {
            val mode = Eggs.SECRET_MODES.single { it.key == key }
            val pack = pack(key)
            for (surface in listOf(mode.bg, requireNotNull(mode.surface), requireNotNull(mode.surface2))) {
                assertTrue(contrast(mode.ink, surface) >= 4.5, "$key ink ${mode.ink} on $surface: ${contrast(mode.ink, surface)}")
            }
            for (accent in pack.colors) {
                assertTrue(contrast(prune, accent) >= 4.5, "$key prune on $accent: ${contrast(prune, accent)}")
            }
        }
    }

    @Test fun everyAmbianceVariantKeepsReadableText() {
        for (mode in SHOP_MODES + Eggs.SECRET_MODES) for (dark in listOf(false, true)) {
            val variant = mode.forDark(dark)
            for (surface in listOfNotNull(variant.bg, variant.surface, variant.surface2)) {
                assertTrue(contrast(variant.ink, surface) >= 4.5, "${mode.key} dark=$dark on $surface")
            }
        }
    }

    private fun contrast(foreground: String, background: String): Double {
        fun luminance(hex: String): Double {
            val rgb = hex.removePrefix("#").chunked(2).map { it.toInt(16) / 255.0 }
            val linear = rgb.map { if (it <= .04045) it / 12.92 else Math.pow((it + .055) / 1.055, 2.4) }
            return .2126 * linear[0] + .7152 * linear[1] + .0722 * linear[2]
        }
        val a = luminance(foreground); val b = luminance(background)
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }

    private fun settings(user: User) = listOf(user.settings.theme, user.settings.bg, user.settings.accentCustom, user.settings.fx)
}
