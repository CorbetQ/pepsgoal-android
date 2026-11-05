package fr.pepsgoal.app.ui

import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.PathParser
import fr.pepsgoal.core.SHOP_ICONS
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Check launcher wiring and render the real vector geometry for visual review. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FootballIconsTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private fun read(file: File) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(file)

    @Test fun secretIconsHaveDisabledAliasesAndReadableAdaptiveLayers() {
        val manifest = read(File("src/main/AndroidManifest.xml"))
        val aliases = manifest.getElementsByTagName("activity-alias")
        val output = File("build/reports/football-icons").apply { mkdirs() }
        for ((key, alias) in listOf("sco" to ".IconSco", "srfc" to ".IconSrfc", "psg" to ".IconPsg", "fcb" to ".IconFcb", "fcn" to ".IconFcn")) {
            assertEquals(key, SHOP_ICONS.getValue(key).eggKey)
            val entry = (0 until aliases.length).map { aliases.item(it) as Element }
                .single { it.getAttributeNS(android, "name") == alias }
            assertEquals("false", entry.getAttributeNS(android, "enabled"))
            assertEquals(".MainActivity", entry.getAttributeNS(android, "targetActivity"))
            assertEquals("@mipmap/ic_launcher_$key", entry.getAttributeNS(android, "icon"))
            val adaptive = read(File("src/main/res/mipmap-anydpi-v26/ic_launcher_$key.xml"))
            for (layer in listOf("background", "foreground", "monochrome")) {
                val reference = (adaptive.getElementsByTagName(layer).item(0) as Element)
                    .getAttributeNS(android, "drawable")
                assertEquals("@drawable/ic_launcher_${layer}_$key", reference)
            }
            for (mono in listOf(false, true)) {
                val bitmap = Bitmap.createBitmap(324, 324, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.scale(3f, 3f)
                if (mono) canvas.drawColor(Color.rgb(42, 20, 32))
                val layers = if (mono) listOf("monochrome") else listOf("background", "foreground")
                for (layer in layers) {
                    val vector = read(File("src/main/res/drawable/ic_launcher_${layer}_$key.xml"))
                    fun draw(element: Element) {
                        fun number(name: String, fallback: Float = 0f) = element.getAttributeNS(android, name).toFloatOrNull() ?: fallback
                        if (element.tagName == "group") {
                            canvas.save()
                            canvas.translate(number("translateX"), number("translateY"))
                            canvas.scale(number("scaleX", 1f), number("scaleY", 1f))
                        }
                        if (element.tagName == "path") {
                            val path = requireNotNull(PathParser.createPathFromPathData(element.getAttributeNS(android, "pathData")))
                            if (element.getAttributeNS(android, "fillType") == "evenOdd") path.fillType = Path.FillType.EVEN_ODD
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                            val gradients = element.getElementsByTagName("gradient")
                            if (gradients.length > 0) {
                                val gradient = gradients.item(0) as Element
                                val stops = gradient.getElementsByTagName("item")
                                val colors = IntArray(stops.length) { Color.parseColor((stops.item(it) as Element).getAttributeNS(android, "color")) }
                                val offsets = FloatArray(stops.length) { (stops.item(it) as Element).getAttributeNS(android, "offset").toFloat() }
                                fun coordinate(name: String) = gradient.getAttributeNS(android, name).toFloat()
                                paint.shader = LinearGradient(coordinate("startX"), coordinate("startY"), coordinate("endX"), coordinate("endY"), colors, offsets, Shader.TileMode.CLAMP)
                            } else paint.color = Color.parseColor(element.getAttributeNS(android, "fillColor"))
                            paint.alpha = (number("fillAlpha", 1f) * 255).toInt()
                            canvas.drawPath(path, paint)
                        }
                        for (i in 0 until element.childNodes.length) {
                            val child = element.childNodes.item(i)
                            if (child is Element && child.tagName in listOf("group", "path")) draw(child)
                        }
                        if (element.tagName == "group") canvas.restore()
                    }
                    draw(vector.documentElement)
                }
                File(output, "$key-${if (mono) "mono" else "color"}.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                bitmap.recycle()
            }
        }
    }
}
