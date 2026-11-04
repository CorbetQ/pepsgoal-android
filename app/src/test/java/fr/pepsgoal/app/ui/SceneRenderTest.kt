package fr.pepsgoal.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.Place
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/** Real Android/Skia drawing, without a network request or user account. Artifacts aren't golden baselines. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneRenderTest {
    private val city = Place("Angers", lat=47.47, lon=-.55, tz="Europe/Paris")
    private val width = 240
    private val height = 400
    private val palette = listOf(Color(0xFFFFAB76),Color(0xFF55CABC),Color(0xFF9087DC),Color(0xFFFF86B7))
    private val output = File("build/reports/scenes")
    private val artworks = mutableMapOf<String, ImageBitmap?>()
    @org.junit.After fun releaseDecodedArtworks() {
        artworks.values.filterNotNull().forEach { it.asAndroidBitmap().recycle() }
        artworks.clear()
    }
    private fun environment(hour: String, condition: String = "clear"): SceneEnvironment {
        val now = Instant.parse("2026-06-21T${hour}:00Z")
        val code = when(condition) {"rain"->63;"snow"->73;"fog"->45;"storm"->95;"clouds"->3;else->0}
        val current = WxCurrent(time=now.atZone(java.time.ZoneId.of(city.tz)).toLocalDateTime().toString(),code=code,
            clouds=if(condition=="clear")10.0 else 90.0,wind=24.0,precip=if(condition=="rain")3.0 else 0.0)
        return SceneAtmosphere.resolve(now,city,Forecast(city.tz,current),now.toEpochMilli())
    }
    private fun render(key: String, e: SceneEnvironment, renderWidth: Int = width, renderHeight: Int = height, candidate: ImageBitmap? = null): Bitmap {
        val image = ImageBitmap(renderWidth,renderHeight)
        renderInto(image, 1f) {
            val artwork = candidate ?: artworks.getOrPut(key) {
                File("src/main/assets/backgrounds/$key/art/artwork.png").takeIf { it.isFile }
                    ?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
            }
            if (artwork != null) paintSceneArtwork(key, artwork, .3f, e) else when(key) {
                "pop" -> popHills(e.night>.5f,.3f,e)
                "sky" -> livingSky(e,.3f)
                "grid","stars","waves","aurora","mesh","blob" -> freeBg(key,FreePal(e.night>.5f,
                    if(e.night>.5f) Color(0xFF20182D) else Color(0xFFFFF1E7),Color(0xFF2A1420),palette,.5f,backgroundStyle(key)),.3f)
                else -> scene(key,.3f,environment=e)
            }
            if(backgroundStyle(key).capability.weather && key!="rainwindow") drawSceneWeather(e.condition,e.clouds,e.wind,e.freshness,e.night,.3f,48)
        }
        return image.asAndroidBitmap()
    }
    /** Same8%overscan and initial centre position as the painted backdrop in AppBg. */
    private fun renderPhone(key:String,e:SceneEnvironment,candidate:ImageBitmap?=null):Bitmap {
        if(candidate==null && !hasSceneArtwork(key))return render(key,e,360,780)
        val extended=render(key,e,360,842,candidate)
        val screen=Bitmap.createBitmap(extended,0,31,360,780)
        extended.recycle()
        return screen
    }
    @Test fun renderArtworkCandidatesWhenRequested() {
        val root=System.getenv("PEPSGOAL_ARTWORK_REVIEW_DIR")
        org.junit.Assume.assumeTrue("Optional staging gallery; never ships candidate art",root != null)
        val target=File(output,"candidates").apply { mkdirs() }
        val rendered=mutableListOf<Pair<String,String>>()
        val only=System.getenv("PEPSGOAL_ARTWORK_REVIEW_KEYS")?.split(",")?.toSet()
        for(folder in File(root!!).listFiles()!!.filter { it.isDirectory && (only==null || it.name in only) }) {
            val file=folder.listFiles()!!.filter { it.name.matches(Regex("v[0-9]+\\.png")) }
                .maxByOrNull { it.nameWithoutExtension.removePrefix("v").toInt() } ?: continue
            val artwork=BitmapFactory.decodeFile(file.path).asImageBitmap()
            for ((name,e) in listOf("day" to environment("12:00"),"night" to environment("22:00"),
                "dusk" to environment("20:00"),"rain" to environment("12:00","rain"))) {
                val bitmap=render(folder.name,e,480,800,artwork)
                File(target,"${folder.name}-${file.nameWithoutExtension}-$name.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                }
                bitmap.recycle()
                if (name in setOf("day","night")) {
                    val tall=renderPhone(folder.name,e,artwork)
                    File(target,"${folder.name}-${file.nameWithoutExtension}-tall-$name.png").outputStream().use {
                        tall.compress(Bitmap.CompressFormat.PNG,100,it)
                    }
                    tall.recycle()
                }
            }
            artwork.asAndroidBitmap().recycle()
            rendered += folder.name to file.nameWithoutExtension
        }
        for(state in listOf("day","night")) {
            rendered.sortedBy { it.first }.chunked(6).forEachIndexed { page, group ->
                val sheet=Bitmap.createBitmap(1080,1616,Bitmap.Config.ARGB_8888)
                val canvas=Canvas(sheet);canvas.drawColor(0xFF2A1420.toInt())
                val label=Paint().apply {color=android.graphics.Color.WHITE;textSize=17f;isAntiAlias=true}
                group.forEachIndexed { i,(key,revision) ->
                    val file=File(target,"$key-$revision-tall-$state.png")
                    val frame=BitmapFactory.decodeFile(file.path)
                    canvas.drawBitmap(frame,(i%3*360).toFloat(),(i/3*808).toFloat(),null)
                    canvas.drawText("$key / $revision",(i%3*360+8).toFloat(),(i/3*808+801).toFloat(),label)
                    frame.recycle()
                }
                val prefix=if(only==null)"review" else "targeted-review"
                File(target,"$prefix-$state-$page.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG,100,it) }
                sheet.recycle()
            }
        }
    }
    @Test fun renderEveryBackgroundAcrossDayNightAndWeather() {
        output.mkdirs()
        val keys = ALL_BGS.map{it.key} + listOf("pop","sky","blob","grid","stars","waves","aurora","mesh")
        val scenarios = listOf("day" to environment("12:00"), "night" to environment("22:00"),
            "dawn" to environment("03:30"), "dusk" to environment("20:00"), "rain" to environment("12:00","rain"),
            "snow" to environment("12:00","snow"),"fog" to environment("12:00","fog"),"storm" to environment("12:00","storm"))
        for((name,e) in scenarios) {
            val sheet=Bitmap.createBitmap(width*8,(height+24)*((keys.size+7)/8),Bitmap.Config.ARGB_8888)
            val canvas=Canvas(sheet);canvas.drawColor(android.graphics.Color.rgb(42,20,32))
            val text=Paint().apply {color=android.graphics.Color.WHITE;textSize=13f;isAntiAlias=true}
            keys.forEachIndexed {i,key ->
                val bitmap=render(key,e)
                assertTrue("$name/$key must be opaque", bitmap.getPixel(width/2,height/2) ushr 24 == 255)
                canvas.drawBitmap(bitmap,(i%8*width).toFloat(),(i/8*(height+24)).toFloat(),null)
                canvas.drawText(key,(i%8*width+7).toFloat(),(i/8*(height+24)+height+17).toFloat(),text)
                bitmap.recycle()
            }
            File(output,"$name.png").outputStream().use{sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
            sheet.recycle()
        }
        val samples = File(output,"samples").apply { mkdirs() }
        for (key in keys) {
            for ((name,e) in scenarios.filter { it.first in setOf("day","night","dusk") }) {
                val bitmap=renderPhone(key,e)
                File(samples,"$key-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
        }
        val pages=File(output,"review").apply { mkdirs() }
        for ((state,e) in scenarios.filter { it.first in setOf("day","night") }) {
            keys.chunked(6).forEachIndexed { page, group ->
                val sheet=Bitmap.createBitmap(1080,1248,Bitmap.Config.ARGB_8888)
                val canvas=Canvas(sheet);canvas.drawColor(android.graphics.Color.rgb(42,20,32))
                val label=Paint().apply {color=android.graphics.Color.WHITE;textSize=17f;isAntiAlias=true}
                group.forEachIndexed { i,key ->
                    val frame=render(key,e,360,600)
                    canvas.drawBitmap(frame,(i%3*360).toFloat(),(i/3*624).toFloat(),null)
                    canvas.drawText(key,(i%3*360+8).toFloat(),(i/3*624+619).toFloat(),label)
                    frame.recycle()
                }
                File(pages,"$state-$page.png").outputStream().use{sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
                sheet.recycle()
            }
        }
        val names=ALL_BGS.associate { it.key to it.name } + mapOf("pop" to "Collines braise", "sky" to "Ciel vivant", "blob" to "Sculptures organiques", "grid" to "Dalles en perspective", "stars" to "Constellation", "waves" to "Vagues sculptées", "aurora" to "Rubans", "mesh" to "Drapé chromatique")
        val cards=keys.joinToString("\n") { key ->
            val name=(names[key] ?: key).replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;")
            """<article data-key="$key"><h2>$name</h2><small>$key</small><a href="samples/$key-day.png"><img src="samples/$key-day.png" loading="lazy" alt="$name au format téléphone"></a></article>"""
        }
        val template=javaClass.getResource("/background-gallery.html")!!.readText()
        File(output,"index.html").writeText(template.replace("<!-- CARDS -->",cards))
    }

    @Test fun solarSceneChangesButArtisticSceneStaysIdentical() {
        val day=environment("12:00");val night=environment("22:00")
        for(key in listOf("mountains","loire","tram","lagoon","pop","sky")) {
            val a=render(key,day);val b=render(key,night)
            assertFalse("$key should change with the sun",a.sameAs(b));a.recycle();b.recycle()
        }
        for(key in listOf("deepsea","nebula","kopa","ecran","coderain")) {
            val a=render(key,day);val b=render(key,night)
            assertTrue("$key must retain its artistic atmosphere",a.sameAs(b));a.recycle();b.recycle()
        }
    }
    @Test fun clearSkyHasNoPrecipitationAndMoonIsVisible() {
        val e=environment("22:00")
        val a=ImageBitmap(width,height);val b=ImageBitmap(width,height)
        renderInto(a,1f){weatherSky(e,.3f)}
        renderInto(b,1f){weatherSky(e,.3f);drawSceneWeather("clear",10f,0f,1f,1f,.3f,48)}
        assertTrue(a.asAndroidBitmap().sameAs(b.asAndroidBitmap()))
        val moon=a.asAndroidBitmap().getPixel((width*.78f).toInt(),(height*.23f).toInt())
        assertTrue("Clear night moon should be visible", android.graphics.Color.red(moon)>150)
    }
    @Test fun forcedLightAndWeatherOffAreIndependent() {
        val rainyNight=environment("22:00","rain")
        val day=sceneLighting(rainyNight,"day",true,false)
        assertEquals(1f,day.day);assertEquals("clear",day.condition);assertEquals(0f,day.freshness)
        val themed=sceneLighting(rainyNight,"theme",false,true)
        assertEquals(1f,themed.day);assertEquals("rain",themed.condition)
    }
}
