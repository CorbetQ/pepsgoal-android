package fr.pepsgoal.core

import kotlin.test.*

class ContentCatalogTest {
    private val id = "9b0c1d2e-3f4a-4b5c-8d7e-1f2a3b4c5d6e"
    private fun item(i: String, v: Int = 1, type: String = "theme_pack", at: String = "2026-11-01T10:00:00.000Z") =
        CatalogItem(i, type, i, v, "N", updatedAt = at, publishedAt = at)
    private val packJson = """{"schemaVersion":1,"type":"theme_pack","name":"Lagon","emoji":"🏝️","dark":false,
        "colors":{"bg":"#FFF7F0","ink":"#2A1420","surface":"#FFFFFF","surface2":"#FFEDE1","edge":"#F2DED1","accent":["#FFB000","#FF4A12","#FF3D96"],"deco":["#FFE2CC"]},
        "wallpaper":{"kind":"image","file":"fond.jpg"},"futur":42}"""
    private fun detail(content: String = packJson, files: String = """[{"name":"fond.jpg","url":"https://x/f.jpg","sha256":"${"a".repeat(64)}","size":3}]""") =
        ContentCatalog.parseDetail("""{"id":"$id","type":"theme_pack","version":2,"name":"Lagon","author":"PepsGoal","price":150,"status":"published","content":$content,"files":$files,"inconnu":1}""")

    @Test fun pageParsesAndIgnoresUnknownFields() {
        val p = ContentCatalog.parsePage("""{"schemaVersion":1,"items":[{"id":"a","type":"theme_pack","slug":"a","version":3,"name":"A","price":0,"nouveau":true}],"removed":["b"],"cursor":"2026-11-06T10:00:00.000Z","next":null}""")
        assertEquals(3, p.items.single().version); assertEquals(listOf("b"), p.removed); assertNull(p.next)
    }

    @Test fun mergeReplacesOnFullFetchAndAppliesDeltas() {
        val s0 = ContentCatalog.merge(ContentCacheState(), listOf(CatalogPage(items = listOf(item("a"), item("b")), cursor = "2026-11-02T00:00:00.000Z")), full = true, now = 1)
        assertEquals(setOf("a", "b"), s0.items.map { it.id }.toSet()); assertEquals("2026-11-02T00:00:00.000Z", s0.cursor)
        val s1 = s0.copy(local = mapOf("a" to 1, "b" to 1))
        // Delta : « a » passe en version 2, « b » est retiré, aucun nouveau curseur (rien n'est perdu)
        val s2 = ContentCatalog.merge(s1, listOf(CatalogPage(items = listOf(item("a", 2)), removed = listOf("b"))), full = false, now = 2)
        assertEquals(listOf("a"), s2.items.map { it.id }); assertEquals("2026-11-02T00:00:00.000Z", s2.cursor)
        assertEquals(mapOf("a" to 1), s2.local); assertEquals(setOf("b"), ContentCatalog.removedSince(s1, s2))
        assertEquals(listOf("a"), ContentCatalog.toDownload(s2).map { it.id })
        // Relève complète : tout ce qui n'y figure pas disparaît
        val s3 = ContentCatalog.merge(s2, listOf(CatalogPage(items = listOf(item("c")))), full = true, now = 3)
        assertEquals(listOf("c"), s3.items.map { it.id }); assertTrue(s3.local.isEmpty())
    }

    @Test fun downloadsSkipKnownVersionsRejectedOnesAndUnsupportedTypes() {
        val s = ContentCacheState(items = listOf(item("a", 2), item("b", 1), item("c", 1, type = "sticker_pack"), item("d", 4)),
            local = mapOf("a" to 2, "b" to 0), rejected = listOf("d@4"))
        assertEquals(listOf("b"), ContentCatalog.toDownload(s).map { it.id })
    }

    @Test fun pollingIsBoundedInTime() {
        val h = 3_600_000L
        assertTrue(ContentCatalog.due(10 * h, 0, background = true))
        assertFalse(ContentCatalog.due(10 * h, 10 * h - 60_000, background = false))
        assertTrue(ContentCatalog.due(10 * h, 10 * h - 16 * 60_000, background = false))
        assertFalse(ContentCatalog.due(30 * h, 12 * h, background = true))
        assertTrue(ContentCatalog.due(33 * h, 12 * h, background = true))
        assertTrue(ContentCatalog.due(1 * h, 12 * h, background = true)) // horloge reculée : relève permise
    }

    @Test fun sha256AndSizeAreVerified() {
        val bytes = "abc".toByteArray()
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ContentCatalog.sha256Hex(bytes))
        val ref = ContentFileRef("f.jpg", "https://x/f.jpg", "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD", 3)
        assertTrue(ContentCatalog.verify(bytes, ref))
        assertFalse(ContentCatalog.verify("abd".toByteArray(), ref))
        assertFalse(ContentCatalog.verify(bytes, ref.copy(size = 4)))
    }

    @Test fun onlyHttpsOrTheApiOriginIsDownloaded() {
        assertTrue(ContentCatalog.safeUrl("https://cdn.pepsgoal.fr/media/content/x.jpg", "https://api.pepsgoal.fr"))
        assertTrue(ContentCatalog.safeUrl("http://10.0.2.2:8080/media/x.jpg", "http://10.0.2.2:8080/"))
        assertFalse(ContentCatalog.safeUrl("http://evil.example/x.jpg", "https://api.pepsgoal.fr"))
        assertFalse(ContentCatalog.safeUrl("file:///data/x", "https://api.pepsgoal.fr"))
        assertFalse(ContentCatalog.safeFileName("../x.jpg")); assertTrue(ContentCatalog.safeFileName("fond.jpg"))
    }

    @Test fun validPackDetailBecomesAPackWithTheContentId() {
        val spec = assertNotNull(ContentCatalog.validate(detail()))
        assertEquals(id, spec.id); assertEquals("Lagon", spec.name); assertEquals("PepsGoal", spec.author)
        assertEquals("fond.jpg", ContentCatalog.mainFile(detail(), spec)?.name)
        assertEquals("m:@$id", spec.themeSetting())
    }

    @Test fun invalidContentIsRejected() {
        assertFailsWith<PackFormatError> { ContentCatalog.validate(detail(files = "[]")) }
        assertFailsWith<PackFormatError> { ContentCatalog.validate(detail(content = packJson.replace("\"schemaVersion\":1", "\"schemaVersion\":9"))) }
        assertFailsWith<PackFormatError> { ContentCatalog.validate(detail(content = packJson.replace("#2A1420", "noir"))) }
        assertFailsWith<PackFormatError> { ContentCatalog.validate(detail(files = """[{"name":"../fond.jpg","url":"https://x","sha256":"${"a".repeat(64)}","size":3}]""")) }
        assertFailsWith<PackFormatError> { ContentCatalog.validate(detail(files = """[{"name":"fond.jpg","url":"https://x","sha256":"court","size":3}]""")) }
        val bg = ContentCatalog.parseDetail("""{"id":"$id","type":"background","name":"F","content":{"schemaVersion":1,"type":"background","file":"x.jpg"},"files":[]}""")
        assertFailsWith<PackFormatError> { ContentCatalog.validate(bg) }
        val sticker = ContentCatalog.parseDetail("""{"id":"$id","type":"sticker_pack","name":"S"}""")
        assertFailsWith<PackFormatError> { ContentCatalog.validate(sticker) }
    }

    @Test fun stateSurvivesItsOwnEncoding() {
        val s = ContentCacheState("c", "\"e\"", listOf(item("a")), mapOf("a" to 1), mapOf("a" to "\"x\""), listOf("b@2"), 5)
        assertEquals(s, ContentCatalog.decodeState(ContentCatalog.encodeState(s)))
        assertEquals(ContentCacheState(), ContentCatalog.decodeState("corrompu"))
        assertEquals(ContentCacheState(), ContentCatalog.decodeState(null))
    }
}
