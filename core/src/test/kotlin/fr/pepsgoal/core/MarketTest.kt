package fr.pepsgoal.core

import fr.pepsgoal.core.model.Db
import fr.pepsgoal.core.model.User
import kotlin.test.*

/** Boutique des créateurs (10.0) : crédit des ventes, collection retrouvée, pack publiable, premier plan. */
class MarketTest {

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

    private val pack = starterPack(false, id = "perso-abcdefghij", now = 5)

    @Test fun salesAreCreditedOnceEvenWhenReplayed() {
        val f = Fixture()
        val before = f.pg.wallet()
        val s = listOf(Sale("s1", "c1", "Collines", 50, "Bob"), Sale("s2", "c1", "Collines", 0, "Zoé"))
        assertEquals(2, f.pg.creditSales(s).size)
        assertEquals(before + 50, f.pg.wallet()); assertEquals(50, f.pg.marketEarned())
        // Relève rejouée (réponse perdue, autre appareil) : rien n'est compté deux fois
        assertEquals(0, f.pg.creditSales(s).size); assertEquals(before + 50, f.pg.wallet())
        assertEquals(1, f.pg.creditSales(s + Sale("s3", "c2", "Nuit", 120)).size)
        assertEquals(before + 170, f.pg.wallet()); assertEquals(setOf("s1", "s2", "s3"), f.pg.creditedSales())
        // Les flammes reçues ne disparaissent pas au recalcul des prix de la boutique
        f.pg.repriceShop(); assertEquals(before + 170, f.pg.wallet())
    }

    @Test fun purchasesComeBackOnANewDeviceWithoutSpending() {
        val f = Fixture()
        val w = f.pg.wallet()
        assertEquals(2, f.pg.restorePurchases(listOf(OwnedContent("c1", 50), OwnedContent("c2", 0))))
        assertTrue(f.pg.ownsContent("c1")); assertTrue(f.pg.ownsContent("c2")); assertEquals(w, f.pg.wallet())
        assertEquals(0, f.pg.restorePurchases(listOf(OwnedContent("c1", 50))))
    }

    @Test fun ownPackIsOwnedWithoutBuying() {
        val f = Fixture()
        assertTrue(f.pg.ownsOwnPack(CatalogItem("c1", "theme_pack", name = "X", creatorId = f.user.id)))
        assertFalse(f.pg.ownsOwnPack(CatalogItem("c1", "theme_pack", name = "X", creatorId = "autre")))
        assertFalse(f.pg.ownsOwnPack(CatalogItem("c1", "theme_pack", name = "X")))
    }

    @Test fun publishableDropsBoughtExtrasAndRenamesFiles() {
        val p = pack.copy(icon = "flame", fx = "snow", wallpaper = PackWallpaper("image", file = "perso.jpg", foreground = "perso.fg.webp", weather = true, styled = true))
        val out = Market.publishable(p, "fond.jpg", "premier-plan.webp")
        assertNull(out.icon); assertNull(out.fx); assertEquals(0, out.createdAt)
        assertEquals(PackWallpaper("image", file = "fond.jpg", foreground = "premier-plan.webp", weather = true, styled = true), out.wallpaper)
        assertEquals(out, PackFormat.parse(PackFormat.encode(out)))
    }

    @Test fun publishProblems() {
        assertNull(Market.publishProblem(pack, false))
        assertNotNull(Market.publishProblem(pack.copy(wallpaper = PackWallpaper("scene", "loire")), false))
        assertNotNull(Market.publishProblem(pack.copy(wallpaper = PackWallpaper("image", file = "a.jpg")), false))
        assertNull(Market.publishProblem(pack.copy(wallpaper = PackWallpaper("image", file = "a.jpg")), true))
        assertEquals(0, Market.clampPrice(-3)); assertEquals(5000, Market.clampPrice(9999)); assertEquals(120, Market.clampPrice(123))
    }

    @Test fun foregroundIsValidatedAndDownloaded() {
        val json = PackFormat.encode(pack.copy(wallpaper = PackWallpaper("image", file = "fond.jpg", foreground = "pp.webp", weather = true)), pretty = false)
        val file = { n: String -> """{"name":"$n","url":"https://x/$n","sha256":"${"a".repeat(64)}","size":3}""" }
        val ok = ContentCatalog.parseDetail("""{"id":"c-1","type":"theme_pack","name":"P","content":$json,"files":[${file("fond.jpg")},${file("pp.webp")}]}""")
        val spec = ContentCatalog.validate(ok)!!
        assertEquals("pp.webp", ContentCatalog.foregroundFile(ok, spec)?.name)
        assertEquals(listOf("fond.jpg", "pp.webp"), ContentCatalog.neededFiles(ok, spec).map { it.name })
        val missing = ContentCatalog.parseDetail("""{"id":"c-1","type":"theme_pack","name":"P","content":$json,"files":[${file("fond.jpg")}]}""")
        assertFailsWith<PackFormatError> { ContentCatalog.validate(missing) }
        assertTrue(PackFormat.validate(pack.copy(wallpaper = PackWallpaper("image", foreground = "../x"))).isNotEmpty())
    }

    @Test fun catalogKeepsCreatorFields() {
        val p = ContentCatalog.parsePage("""{"items":[{"id":"a","type":"theme_pack","name":"A","creatorId":"u1","sales":3,"preview":"https://x/a.jpg"}]}""")
        assertEquals("u1", p.items.single().creatorId); assertEquals(3, p.items.single().sales)
        val s = MARKET_JSON.decodeFromString(StyleResult.serializer(), """{"image":{"name":"style.jpg","url":"https://x/s.jpg","sha256":"${"b".repeat(64)}","size":9,"mime":"image/jpeg"},"foreground":null,"colors":["#63BEEB"],"skyRatio":0.36,"stylized":true,"weather":false,"neuf":1}""")
        assertNull(s.foreground); assertEquals(.36, s.skyRatio)
    }
}
