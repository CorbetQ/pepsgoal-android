package fr.pepsgoal.core

import kotlin.test.*
class ShopCategoriesTest {
    @Test fun footballPacksAndTheirStadiumsShareOneCategory() {
        for (key in listOf("sco", "srfc", "psg", "fcb", "fcn")) {
            val pack = requireNotNull(themePack(key))
            assertEquals(ShopCategory.STADIUM, pack.category)
            assertEquals(pack.category, shopCategory(pack.bgKey))
            assertEquals(pack.category, shopCategory(pack.iconKey))
        }
    }
    @Test fun recognizableCollectionsAndSeasonsHaveStableCategories() {
        assertEquals(ShopCategory.SIGNATURE, themePack("pepsgoal")!!.category)
        assertEquals(ShopCategory.CITY, shopCategory("tram"))
        assertEquals(ShopCategory.NATURE, shopCategory("lagoon"))
        assertEquals(ShopCategory.SPACE, shopCategory("nebula"))
        assertEquals(ShopCategory.ABSTRACT, shopCategory("grid"))
        assertEquals(ShopCategory.SEASON, shopCategory("unknown", "noel"))
    }
    @Test fun groupingKeepsEveryCatalogEntryExactlyOnce() {
        val catalogs = listOf(
            THEME_PACKS.map { it.key to it.category },
            SHOP_BGS.map { it.key to shopCategory(it.key, it.season) },
            SHOP_THEMES.map { it.key to shopCategory(if (it.bg.startsWith("s:")) it.bg.removePrefix("s:") else it.key, it.season) },
            SHOP_MODES.map { it.key to shopCategory(it.key) },
            SHOP_ICONS.map { it.key to shopCategory(it.key, it.value.season) },
        )
        for (catalog in catalogs) {
            val grouped = ShopCategory.entries.flatMap { category -> catalog.filter { it.second == category } }
            assertEquals(catalog.size, grouped.size)
            assertEquals(catalog.toSet(), grouped.toSet())
        }
    }
}
