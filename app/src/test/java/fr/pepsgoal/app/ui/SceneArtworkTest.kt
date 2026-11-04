package fr.pepsgoal.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import fr.pepsgoal.core.SceneAtmosphere
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** Renderer contracts, independent from the critic's artistic acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneArtworkTest {
    private val day = SceneAtmosphere.resolve(Instant.parse("2026-06-21T12:00:00Z")).copy(
        day=1f, night=0f, dawn=0f, dusk=0f, sun=.5f, clouds=0f, condition="clear")
    private val night = day.copy(day=0f, night=1f, sunDown=true)
    private fun render(key: String, artwork: ImageBitmap, nighttime: Boolean, weather: Boolean = true): Bitmap {
        val image = ImageBitmap(120,200)
        renderInto(image,1f) { paintSceneArtwork(key,artwork,.3f,if(nighttime)night else day,weatherEnabled=weather) }
        return image.asAndroidBitmap()
    }
    @Test fun transparentSkyShowsLiveSkyAndOpaqueLandIsGraded() {
        val source=Bitmap.createBitmap(120,200,Bitmap.Config.ARGB_8888)
        Canvas(source).drawRect(0f,100f,120f,200f,Paint().apply{color=0xFFFFAA55.toInt()})
        val artwork=source.asImageBitmap()
        val daytime=render("loire",artwork,false);val nighttime=render("loire",artwork,true)
        assertEquals(255,daytime.getPixel(60,40) ushr 24)
        assertNotEquals("Native sky must follow time",daytime.getPixel(60,40),nighttime.getPixel(60,40))
        assertTrue("Painted land must darken at night",
            android.graphics.Color.red(nighttime.getPixel(60,170)) < android.graphics.Color.red(daytime.getPixel(60,170)))
        source.recycle();daytime.recycle();nighttime.recycle()
    }
    @Test fun fixedArtisticAtmosphereIgnoresRealClock() {
        val source=Bitmap.createBitmap(120,200,Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF294863.toInt()) }
        val a=render("kopa",source.asImageBitmap(),false);val b=render("kopa",source.asImageBitmap(),true)
        assertTrue(a.sameAs(b))
        source.recycle();a.recycle();b.recycle()
    }
    @Test fun windowSceneryFollowsNightExposure() {
        val source=Bitmap.createBitmap(120,200,Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFF4BD76.toInt()) }
        val a=render("rainwindow",source.asImageBitmap(),false);val b=render("rainwindow",source.asImageBitmap(),true)
        assertTrue(android.graphics.Color.red(b.getPixel(60,170)) < android.graphics.Color.red(a.getPixel(60,170)))
        source.recycle();a.recycle();b.recycle()
    }
    @Test fun disablingWeatherKeepsSolarExposureIndependent() {
        val source=Bitmap.createBitmap(120,200,Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFF4BD76.toInt()) }
        val a=render("rainwindow",source.asImageBitmap(),false,false);val b=render("rainwindow",source.asImageBitmap(),true,false)
        assertTrue(android.graphics.Color.red(b.getPixel(60,170)) < android.graphics.Color.red(a.getPixel(60,170)))
        source.recycle();a.recycle();b.recycle()
    }
}
