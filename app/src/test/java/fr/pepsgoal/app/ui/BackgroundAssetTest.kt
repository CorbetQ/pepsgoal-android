package fr.pepsgoal.app.ui

import fr.pepsgoal.core.ALL_BGS
import fr.pepsgoal.core.BGS
import fr.pepsgoal.core.JSON
import fr.pepsgoal.core.sceneCapability
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BackgroundAssetTest {
    private val root = File("src/main/assets/backgrounds")
    private fun read(file: File) = JSON.parseToJsonElement(file.readText()).jsonObject
    @Test fun shippedIllustrationsAreExactlyTheReviewedRevisions() {
        for(folder in root.listFiles()!!.filter { it.isDirectory && !it.name.startsWith("_") }) {
            val artwork=File(folder,"art/artwork.png")
            if(!artwork.exists()) continue
            val manifest=read(File(folder,"art/manifest.json"))
            assertEquals(folder.name,manifest.getValue("id").jsonPrimitive.content)
            assertEquals("accepted",manifest.getValue("review").jsonPrimitive.content)
            val hash=java.security.MessageDigest.getInstance("SHA-256").digest(artwork.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals("${folder.name}: unreviewed artwork change",manifest.getValue("sha256").jsonPrimitive.content,hash)
            assertTrue(manifest.getValue("referenceSources").jsonArray.size in 1..10)
            assertTrue(hasSceneArtwork(folder.name))
        }
    }
    @Test fun everyBackgroundHasItsOwnEditableAssets() {
        val ids = read(File(root,"catalog.json")).getValue("backgrounds").jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(ALL_BGS.map { it.key }.toSet() + BGS.map { it.first } + setOf("sky","blob"), ids)
        assertEquals(ids,root.listFiles()!!.filter { it.isDirectory && !it.name.startsWith("_") }.map { it.name }.toSet())
        for (id in ids) {
            val folder=File(root,id)
            assertEquals(id,read(File(folder,"properties.json")).getValue("id").jsonPrimitive.content)
            val layers=File(folder,"layers").listFiles()!!
            assertTrue("$id needs its own drawing layers",layers.isNotEmpty())
            if(id !in setOf("plain","custom")) {
                assertTrue(File(folder,"layers/foreground.layer.kts").isFile)
                assertTrue(File(folder,"layers/lighting.layer.kts").isFile)
            }
        }
    }
    @Test fun compiledPropertiesAndAnimationAreExactlyThoseInAssets() {
        for (folder in root.listFiles()!!.filter { it.isDirectory && !it.name.startsWith("_") }) {
            val props=read(File(folder,"properties.json"));val animation=read(File(folder,"animations.json"))
            val style=backgroundStyle(folder.name)
            assertEquals(folder.name,style.id)
            assertEquals(props.getValue("framing").jsonObject.getValue("focusY").jsonPrimitive.float,style.focusY,0f)
            assertEquals(props.getValue("palette").jsonObject.getValue("saturation").jsonPrimitive.float,style.saturation,0f)
            assertEquals(props.getValue("lighting").jsonObject.getValue("shadowStrength").jsonPrimitive.float,style.shadowStrength,0f)
            assertEquals(animation.getValue("maxFps").jsonPrimitive.int,style.fps)
            assertEquals(animation.getValue("cycleMs").jsonPrimitive.int,style.cycleMs)
            assertEquals(animation.getValue("phaseAtRest").jsonPrimitive.float,style.restPhase,0f)
            val expected=when(props.getValue("compatibility").jsonPrimitive.content){"outdoor"->"OUTDOOR";"window"->"WINDOW";else->"ARTISTIC"}
            assertEquals(expected,sceneCapability(folder.name).name)
            assertEquals(expected,style.capability.name)
        }
    }
    @Test fun drawingContextIsRestoredAfterAnException() {
        val original=currentBackgroundStyle()
        try { withBackgroundStyle("magma") { assertEquals("magma",currentBackgroundStyle().id);error("Interrupted render") } }
        catch(_:IllegalStateException) { }
        assertEquals(original,currentBackgroundStyle())
    }
    @Test fun concurrentThumbnailsCannotLeakTheirPalettes() {
        val pool=Executors.newFixedThreadPool(2);val ready=CountDownLatch(2)
        try {
            val futures=listOf("lagoon","deepsea").map { id -> pool.submit<String> {
                withBackgroundStyle(id) { ready.countDown();check(ready.await(10,TimeUnit.SECONDS));currentBackgroundStyle().id }
            } }
            assertEquals(listOf("lagoon","deepsea"),futures.map { it.get(12,TimeUnit.SECONDS) })
        } finally { pool.shutdownNow() }
    }
}
