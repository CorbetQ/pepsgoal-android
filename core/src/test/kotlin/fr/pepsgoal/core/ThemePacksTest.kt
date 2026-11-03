package fr.pepsgoal.core

import fr.pepsgoal.core.model.Db
import fr.pepsgoal.core.model.User
import java.time.LocalDate
import kotlin.test.*

class ThemePacksTest {
    private class Files : DbFiles {
        val data = mutableMapOf<String, String>()
        var writes = 0
        override fun read(name: String) = data[name]
        override fun write(name: String, content: String) { data[name] = content; writes++ }
        override fun delete(name: String) { data.remove(name) }
    }
    private class Fixture {
        val files = Files()
        val store = Store(files)
        val user = User("me", "test", "Test").apply { shopBonus = 10_000 }
        val pg: Pg
        init { store.use(Db(session = user.id, users = mutableListOf(user)), false); pg = Pg(store) }
    }
    private val day = LocalDate.of(2026, 6, 1)
    private fun pack(key: String) = requireNotNull(themePack(key))

    @Test fun everyPackHasValidFourDimensionReferences() {
        assertEquals(THEME_PACKS.size, THEME_PACKS.map { it.key }.toSet().size)
        for (p in THEME_PACKS) {
            assertTrue(p.iconKey in SHOP_ICONS, p.key)
            assertEquals(p.bgKey, p.background.key)
            assertEquals(p.modeKey, p.mode.key)
            assertEquals(3, p.colors.size)
            assertTrue(p.colors.all { Regex("#[A-Fa-f0-9]{6}").matches(it) })
            assertTrue(p.components().all { it.price >= 0 })
            assertEquals(p.components().size, p.components().map { it.key }.toSet().size)
        }
    }

    @Test fun existingCatalogsAreGroupedWithoutRemovingIndividualItems() {
        val public = THEME_PACKS.filter { it.eggKey == null }
        assertTrue(SHOP_THEMES.all { t -> public.any { it.themeKey == t.key } })
        assertTrue(SHOP_BGS.all { b -> public.any { it.bgKey == b.key } })
        assertTrue(SHOP_MODES.all { m -> public.any { it.modeKey == m.key } })
        assertTrue(SHOP_ICONS.filterValues { it.price > 0 }.keys.all { i -> public.any { it.iconKey == i } })
        assertTrue(SHOP_ICONS.values.all { it.price >= 0 })
        assertEquals(0, SHOP_ICONS.getValue("mono").price)
        assertEquals(150, SHOP_ICONS.getValue("galaxy").price)
    }

    @Test fun freshPackIsCheaperThanSeparateItemsAndChargesOnce() {
        val f = Fixture(); val p = pack("galaxy")
        val quote = f.pg.packQuote(p, date = day)
        assertEquals(p.components().sumOf { it.price }, quote.fullPrice)
        assertEquals((quote.remainingPrice * 75 + 99) / 100, quote.price)
        assertTrue(quote.price < quote.remainingPrice)
        val settings = f.user.settings.theme
        assertNull(f.pg.buyPack(p.key, date = day))
        assertEquals(quote.price, f.user.shopSpent)
        assertEquals(1, f.files.writes)
        assertEquals(settings, f.user.settings.theme, "Buying must not silently apply the pack")
        assertTrue(p.components().all { f.pg.owns(it.key, it.price) })
        val spent = f.user.shopSpent
        assertNull(f.pg.buyPack(p.key, date = day))
        assertEquals(spent, f.user.shopSpent)
        assertEquals(1, f.files.writes)
    }

    @Test fun ownedPartsAreDeductedBeforeTheDiscount() {
        val f = Fixture(); val p = pack("galaxy")
        f.user.shopOwned.add("icon:galaxy")
        val quote = f.pg.packQuote(p, date = day)
        assertEquals(quote.fullPrice - 150, quote.remainingPrice)
        assertFalse("icon:galaxy" in quote.missingKeys)
        assertNull(f.pg.buyPack(p.key, date = day))
        assertEquals(quote.price, f.user.shopSpent)
        assertEquals(1, f.user.shopOwned.count { it == "icon:galaxy" })
    }

    @Test fun aPreviouslyBoughtIllustratedThemeAlreadyCoversItsScene() {
        val f = Fixture(); val p = pack("loire")
        f.user.shopOwned.add("theme:loire")
        val quote = f.pg.packQuote(p, date = day)
        assertFalse("theme:loire" in quote.missingKeys)
        assertFalse("bg:loire" in quote.missingKeys, "The existing theme already supplied this exact scene")
        assertEquals(SHOP_ICONS.getValue("loire").price + p.mode.price, quote.remainingPrice)
        assertNull(f.pg.buyPack(p.key, date = day))
        assertTrue("bg:loire" in f.user.shopOwned)
    }

    @Test fun freshIllustratedPackDoesNotCountTheBundledSceneTwice() {
        val f = Fixture(); val p = pack("loire")
        val quote = f.pg.packQuote(p, date = day)
        val separate = p.colourTheme!!.price + SHOP_ICONS.getValue(p.iconKey).price + p.mode.price
        assertEquals(separate, quote.fullPrice)
        assertEquals((separate * 75 + 99) / 100, quote.price)
        assertTrue(quote.price < separate)
        assertNull(f.pg.buyPack(p.key, date = day))
        assertTrue("bg:loire" in f.user.shopOwned)
        assertTrue("theme:loire" in f.user.shopOwned)
    }

    @Test fun anAlreadyOwnedSceneIsNotChargedInsideTheColourThemeEither() {
        val f = Fixture(); val p = pack("loire")
        f.user.shopOwned.add("bg:loire")
        val quote = f.pg.packQuote(p, date = day)
        assertEquals(p.colourTheme!!.price - p.background.price + SHOP_ICONS.getValue(p.iconKey).price + p.mode.price, quote.remainingPrice)
        assertNull(f.pg.buyPack(p.key, date = day))
        assertTrue("theme:loire" in f.user.shopOwned)
        assertTrue(f.pg.packQuote(p, date = day).owned)
    }

    @Test fun insufficientFundsAndUnknownPackNeverMutateAnything() {
        val f = Fixture(); f.user.shopBonus = 0
        assertNotNull(f.pg.buyPack("galaxy", date = day))
        assertNotNull(f.pg.buyPack("does-not-exist", date = day))
        assertEquals(0, f.user.shopSpent); assertTrue(f.user.shopOwned.isEmpty())
        assertEquals(0, f.files.writes)
    }

    @Test fun stalePriceConfirmationCannotIncreaseTheCharge() {
        val f = Fixture(); val p = pack("galaxy")
        assertNotNull(f.pg.buyPack(p.key, maximumPrice = 1, date = day))
        assertEquals(0, f.user.shopSpent); assertTrue(f.user.shopOwned.isEmpty())
    }

    @Test fun seasonalPackCannotBypassMissingLimitedComponents() {
        val f = Fixture(); val p = pack("noel")
        assertFalse(f.pg.packQuote(p, date = day).available)
        assertNotNull(f.pg.buyPack(p.key, date = day))
        assertEquals(0, f.files.writes)
        assertNull(f.pg.buyPack(p.key, date = LocalDate.of(2026, 12, 25)))
        assertTrue(f.pg.packQuote(p, date = day).owned)
        assertTrue(f.pg.packQuote(p, date = day).available)
        assertNull(f.pg.applyPack(p.key), "Purchased seasonal packs remain usable all year")
    }

    @Test fun temporarySeasonalSceneIsNotMistakenForAPermanentPurchase() {
        val f = Fixture(); val p = pack("xmastree")
        val date = LocalDate.of(2026, 12, 25)
        assertTrue(p.background.freeNow(date))
        assertTrue("bg:xmastree" in f.pg.packQuote(p, date = date).missingKeys)
        assertNull(f.pg.buyPack(p.key, date = date))
        assertTrue("bg:xmastree" in f.user.shopOwned)
    }

    @Test fun secretCannotBeBoughtOrAppliedBeforeDiscovery() {
        val f = Fixture()
        for (key in listOf("sco", "srfc", "angers-secret")) {
            assertFalse(f.pg.packQuote(pack(key), date = day).unlocked)
            assertNotNull(f.pg.buyPack(key, date = day))
            assertNotNull(f.pg.applyPack(key))
        }
        assertTrue(f.user.shopOwned.isEmpty()); assertEquals(0, f.user.shopSpent)
        assertEquals(0, f.files.writes)
        assertNull(shopPriceOf("mode:sco")); assertNull(shopPriceOf("bg:kopa"))
    }

    @Test fun scoDiscoveryProvidesTheCompletePackWithoutAnotherCharge() {
        val f = Fixture()
        assertTrue(f.pg.findEgg("sco"))
        val bonus = f.user.shopBonus
        val quote = f.pg.packQuote(pack("sco"), date = day)
        assertTrue(quote.owned); assertEquals(0, quote.price)
        assertNull(f.pg.applyPack("sco"))
        assertEquals("m:sco", f.user.settings.theme)
        assertEquals("s:kopa", f.user.settings.bg)
        assertEquals(pack("sco").colors, f.user.settings.accentCustom)
        assertEquals("", f.user.settings.fx)
        assertEquals(bonus, f.user.shopBonus); assertEquals(0, f.user.shopSpent)
        assertFalse(f.pg.findEgg("sco"))
    }

    @Test fun legacySecretsAreReconciledIdempotentlyWithoutReplayingRewards() {
        val f = Fixture()
        f.user.shopOwned.add("mode:sco") // legacy ownership, before the stadium reward existed
        val before = f.user.shopBonus
        assertTrue(f.pg.reconcileThemePacks())
        assertTrue("bg:kopa" in f.user.shopOwned)
        assertFalse(f.pg.reconcileThemePacks())
        assertEquals(before, f.user.shopBonus); assertEquals(0, f.user.shopSpent)
        assertTrue(f.pg.packQuote(pack("sco")).owned)
    }

    @Test fun aTrimmedSecretClaimStillRecognizesTheExistingReward() {
        val f = Fixture(); f.user.shopOwned.addAll(listOf("mode:srfc", "bg:ecran"))
        assertTrue(f.pg.packQuote(pack("srfc")).owned)
        assertNull(f.pg.applyPack("srfc"))
        assertEquals("m:srfc", f.user.settings.theme); assertEquals("s:ecran", f.user.settings.bg)
        assertEquals(0, f.user.shopSpent)
    }

    @Test fun angevinSecretDoesNotGiveAwayPreviouslyPaidPublicComponents() {
        val f = Fixture(); f.pg.findEgg("angers")
        assertTrue(f.pg.packQuote(pack("angers-secret"), date = day).unlocked)
        assertFalse(f.pg.packQuote(pack("angers-secret"), date = day).owned)
        f.pg.reconcileThemePacks()
        assertFalse("icon:angevine" in f.user.shopOwned)
        assertFalse("bg:angevine" in f.user.shopOwned)
        assertNull(f.pg.buyPack("angers-secret", date = day))
        assertTrue(f.pg.packQuote(pack("angers-secret"), date = day).owned)
    }

    @Test fun applyIsGuardedAndSetsAllCoreChoicesInOneCommit() {
        val f = Fixture(); val p = pack("galaxy")
        assertNotNull(f.pg.applyPack(p.key))
        f.pg.buyPack(p.key, date = day)
        f.user.settings.fx = "snow"
        val writes = f.files.writes
        val spent = f.user.shopSpent
        assertNull(f.pg.applyPack(p.key))
        assertEquals(writes + 1, f.files.writes)
        assertEquals("m:cosmos", f.user.settings.theme)
        assertEquals("s:nebula", f.user.settings.bg)
        assertEquals(p.colors, f.user.settings.accentCustom)
        assertEquals(p.fx, f.user.settings.fx)
        assertEquals(spent, f.user.shopSpent)
        assertTrue(f.pg.packMatches(p))
        f.user.settings.bg = "plain"
        assertFalse(f.pg.packMatches(p))
    }

    @Test fun componentEntitlementsSurviveTheExistingSerializationFormat() {
        val f = Fixture(); f.pg.buyPack("galaxy", date = day); f.pg.applyPack("galaxy")
        val raw = requireNotNull(f.files.read(Store.LOCAL))
        assertFalse("pack:galaxy" in raw, "No new server-side item type or schema")
        val saved = JSON.decodeFromString(Db.serializer(), raw)
        val restored = Store(Files()).apply { use(saved, false) }
        val pg = Pg(restored)
        assertTrue(pg.packQuote(pack("galaxy")).owned)
        assertTrue(pg.packMatches(pack("galaxy")))
        assertEquals(f.user.shopSpent, pg.me()!!.shopSpent)
    }

    @Test fun noSessionCannotBuyOrApplyOrAcquireSecretRights() {
        val pg = Pg(Store(Files()))
        assertNotNull(pg.buyPack("galaxy", date = day))
        assertNotNull(pg.applyPack("galaxy"))
        assertFalse(pg.reconcileThemePacks())
    }
}
