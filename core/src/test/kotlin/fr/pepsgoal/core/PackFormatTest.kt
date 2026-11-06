package fr.pepsgoal.core

import fr.pepsgoal.core.model.Db
import fr.pepsgoal.core.model.User
import kotlin.random.Random
import kotlin.test.*

class PackFormatTest {
    private class Files : DbFiles {
        val data = mutableMapOf<String, String>()
        override fun read(name: String) = data[name]
        override fun write(name: String, content: String) { data[name] = content }
        override fun delete(name: String) { data.remove(name) }
    }
    private class Fixture {
        val store = Store(Files())
        val user = User("me", "test", "Test").apply { shopBonus = 1_000 }
        val pg: Pg
        init { store.use(Db(session = user.id, users = mutableListOf(user)), false); pg = Pg(store) }
    }

    private fun pack(dark: Boolean = false) = starterPack(dark, id = "perso-test", now = 1L)

    @AfterTest fun clear() { PackLibrary.personal = emptyList(); PackLibrary.online = emptyList() }

    @Test fun starterPacksAreValidAndReadable() {
        for (dark in listOf(false, true)) {
            val p = pack(dark)
            assertEquals(emptyList(), PackFormat.validate(p))
            assertTrue(PackContrast.allOk(p), PackContrast.checks(p).toString())
        }
    }

    @Test fun exportThenImportRoundTrips() {
        val p = pack().copy(name = "Lagon", emoji = "🏝️", wallpaper = PackWallpaper("gradient", colors = listOf("#7fd8ff", "#F7E3B5")), fx = "bubbles", icon = "ocean")
        val back = PackFormat.parse(PackFormat.encode(p))
        assertEquals(PackFormat.normalize(p), back)
        assertEquals(listOf("#7FD8FF", "#F7E3B5"), back.wallpaper.colors)
        assertTrue(PackFormat.encode(p).contains("\"schemaVersion\": 1"))
    }

    @Test fun unknownFieldsAreIgnoredButFutureVersionsAreRefused() {
        val json = PackFormat.encode(pack(), pretty = false)
        assertNotNull(PackFormat.parseOrNull(json.replace("{\"schemaVersion\":1", "{\"schemaVersion\":1,\"futur\":true")))
        val e = assertFailsWith<PackFormatError> { PackFormat.parse(json.replace("\"schemaVersion\":1", "\"schemaVersion\":2")) }
        assertTrue(e.errors.single().contains("plus récent"))
        assertFailsWith<PackFormatError> { PackFormat.parse(json.replace("\"schemaVersion\":1,", "")) }
        assertFailsWith<PackFormatError> { PackFormat.parse(json.replace("theme_pack", "sticker_pack")) }
        assertFailsWith<PackFormatError> { PackFormat.parse("pas du json") }
        assertFailsWith<PackFormatError> { PackFormat.parse("{" + " ".repeat(PACK_JSON_MAX) + "}") }
    }

    @Test fun invalidColorsAndReferencesAreReported() {
        val p = pack()
        assertTrue(PackFormat.validate(p.copy(colors = p.colors.copy(bg = "rouge"))).any { it.contains("fond") })
        assertTrue(PackFormat.validate(p.copy(colors = p.colors.copy(accent = listOf("#FFFFFF", "#000000")))).isNotEmpty())
        assertTrue(PackFormat.validate(p.copy(colors = p.colors.copy(deco = emptyList()))).isNotEmpty())
        assertTrue(PackFormat.validate(p.copy(wallpaper = PackWallpaper("scene", "inexistante"))).isNotEmpty())
        assertTrue(PackFormat.validate(p.copy(wallpaper = PackWallpaper("free", "custom"))).isNotEmpty())
        assertTrue(PackFormat.validate(p.copy(wallpaper = PackWallpaper("gradient", colors = listOf("#FFFFFF")))).isNotEmpty())
        assertTrue(PackFormat.validate(p.copy(wallpaper = PackWallpaper("image", file = "../x.jpg"))).isNotEmpty())
        assertTrue(PackFormat.validate(p.copy(wallpaper = PackWallpaper("video"))).isNotEmpty())
        assertTrue(PackFormat.validate(p.copy(icon = "inconnue")).isNotEmpty())
        assertTrue(PackFormat.validate(p.copy(fx = "laser")).isNotEmpty())
        assertTrue(PackFormat.validate(p.copy(name = " ")).isNotEmpty())
        assertTrue(PackFormat.validate(p.copy(name = "x".repeat(PACK_NAME_MAX + 1))).isNotEmpty())
        assertTrue(PackFormat.validate(p.copy(id = "Majuscules!")).isNotEmpty())
        assertEquals(emptyList(), PackFormat.validate(p.copy(wallpaper = PackWallpaper("scene", "loire"))))
    }

    @Test fun contrastMatchesWcagReferences() {
        assertEquals(21.0, PackContrast.ratio("#000000", "#FFFFFF"), 1e-6)
        assertEquals(1.0, PackContrast.ratio("#777777", "#777777"), 1e-6)
        assertEquals(4.48, PackContrast.ratio("#777777", "#FFFFFF"), .01)
    }

    @Test fun lowContrastIsDetectedAndFixed() {
        val bad = pack().copy(colors = pack().colors.copy(ink = "#C8B8C0", accent = listOf("#8A8A8A", "#808080", "#7A7A7A")))
        assertFalse(PackContrast.allOk(bad))
        val fixed = PackContrast.fix(bad)
        assertTrue(PackContrast.allOk(fixed), PackContrast.checks(fixed).toString())
        // Corrigée, l'encre garde sa famille (sombre sur fond clair) au lieu de changer de pack
        assertTrue(PackContrast.luminance(fixed.colors.ink) < .18)
        assertEquals(emptyList(), PackFormat.validate(fixed))
    }

    @Test fun mixedLightAndDarkSurfacesAreFixedToo() {
        val p = pack().copy(colors = pack().colors.copy(bg = "#FFFFFF", surface = "#101010", surface2 = "#F0F0F0", ink = "#808080"))
        val fixed = PackContrast.fix(p)
        assertTrue(fixed.colors.let { c -> listOf(c.bg, c.surface, c.surface2).all { PackContrast.ratio(c.ink, it) >= 4.5 } }, fixed.colors.toString())
    }

    @Test fun randomPalettesAlwaysBecomeReadable() {
        val r = Random(7)
        fun hex() = "#%06X".format(r.nextInt(0x1000000))
        repeat(300) {
            val p = pack().copy(colors = PackColors(hex(), hex(), hex(), hex(), hex(), listOf(hex(), hex(), hex()), listOf(hex())))
            val f = PackContrast.fix(p)
            assertTrue(PackContrast.allOk(f), "${p.colors} -> ${f.colors}")
        }
    }

    @Test fun libraryResolvesTheCustomAmbiance() {
        val p = pack(dark = true)
        assertNull(shopMode(p.themeSetting()))
        PackLibrary.personal = listOf(p)
        val m = assertNotNull(shopMode("m:@perso-test"))
        assertTrue(m.dark); assertFalse(m.followsDayNight)
        assertEquals(p.colors.accent, m.accent); assertEquals(p.colors.surface2, m.surface2)
        assertEquals("dark", baseTheme("m:@perso-test"))
        assertSame(m.forDark(false).key, m.key)
        assertEquals("Mon pack", themeName("m:@perso-test"))
    }

    @Test fun applyUsesOwnedElementsOnly() {
        val f = Fixture()
        val p = pack().copy(wallpaper = PackWallpaper("scene", "nebula"), icon = "galaxy", fx = "snow")
        val r = f.pg.applyPackSpec(p)
        assertEquals("m:@perso-test", f.user.settings.theme)
        assertEquals(p.colors.accent, f.user.settings.accentCustom)
        assertTrue(f.user.settings.bg.startsWith("g:"))
        assertEquals("", f.user.settings.fx)
        assertNull(r.icon)
        assertEquals(3, r.skipped.size)
        f.user.shopOwned.addAll(listOf("bg:nebula", "icon:galaxy", "theme:t-noel"))
        val ok = f.pg.applyPackSpec(p)
        assertEquals("s:nebula", f.user.settings.bg); assertEquals("snow", f.user.settings.fx); assertEquals("galaxy", ok.icon)
        assertEquals(emptyList(), ok.skipped)
        assertTrue(f.pg.packSpecActive(p))
        f.pg.applyPackSpec(p.copy(wallpaper = PackWallpaper("gradient", colors = listOf("#112233", "#445566"))))
        assertEquals(listOf("#112233", "#445566"), gradientColors(f.user.settings.bg))
        f.pg.applyPackSpec(p.copy(wallpaper = PackWallpaper("image")), imageReady = true)
        assertEquals("custom", f.user.settings.bg)
    }

    @Test fun exportDropsLocalImagesAndDates() {
        val e = PackFormat.exportable(pack().copy(wallpaper = PackWallpaper("image", file = "x.jpg")))
        assertEquals("gradient", e.wallpaper.kind); assertEquals(0, e.createdAt)
        assertEquals(emptyList(), PackFormat.validate(e))
    }

    @Test fun duplicateGetsANewIdentity() {
        val p = pack(); val d = p.duplicate(id = "perso-autre", now = 5)
        assertEquals("perso-autre", d.id); assertEquals("Mon pack (copie)", d.name); assertEquals(5, d.updatedAt)
        assertTrue(newPackId().startsWith("perso-")); assertEquals(emptyList(), PackFormat.validate(p.copy(id = newPackId())))
    }

    @Test fun libraryListSurvivesAndDropsBrokenEntries() {
        val list = listOf(pack(), pack().copy(id = "perso-b", name = "B"))
        assertEquals(list, PackFormat.decodeList(PackFormat.encodeList(list)))
        assertEquals(emptyList(), PackFormat.decodeList("[{\"oops\":1}]"))
        assertEquals(1, PackFormat.decodeList(PackFormat.encodeList(list).replace("\"name\":\"B\"", "\"name\":\"\"")).size)
    }

    @Test fun contentIsObtainedThroughTheExistingPurchase() {
        val f = Fixture()
        assertNull(f.pg.obtainContent("abc", 0))
        assertTrue(f.pg.ownsContent("abc")); assertEquals(0, f.user.shopSpent)
        val before = f.pg.wallet()
        assertNull(f.pg.obtainContent("def", 150))
        assertTrue(f.pg.ownsContent("def")); assertEquals(before - 150, f.pg.wallet())
        assertNull(f.pg.obtainContent("def", 150)); assertEquals(before - 150, f.pg.wallet())
        assertEquals(150, shopPriceOf("content:def@150"))
        // Le recalcul des prix ne rend pas les flammes d'un contenu acheté
        f.pg.repriceShop(); assertEquals(150, f.user.shopSpent)
        assertNotNull(f.pg.obtainContent("cher", 100_000))
        assertFalse(f.pg.ownsContent("cher"))
    }
}
