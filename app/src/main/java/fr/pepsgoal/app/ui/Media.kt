package fr.pepsgoal.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Photos : lecture redimensionnée (orientation EXIF comprise), compression JPEG, couleurs dominantes. */
object Media {
    fun decode(ctx: Context, uri: Uri, maxSide: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        val rot = try { ctx.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0 } catch (e: Exception) { 0 }
        bmp?.let { scale(rotate(it, rot), maxSide) }
    } catch (e: Exception) { null }

    private fun rotate(b: Bitmap, deg: Int): Bitmap = if (deg == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg.toFloat()) }, true)

    fun scale(b: Bitmap, maxSide: Int): Bitmap {
        val k = min(1f, maxSide.toFloat() / max(b.width, b.height))
        return if (k >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * k).roundToInt(), (b.height * k).roundToInt(), true)
    }

    /** Carré centré (photo de profil). */
    fun square(b: Bitmap, side: Int): Bitmap {
        val s = min(b.width, b.height)
        return Bitmap.createScaledBitmap(Bitmap.createBitmap(b, (b.width - s) / 2, (b.height - s) / 2, s, s), side, side, true)
    }

    fun jpeg(b: Bitmap, q: Int = 82): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()

    /** Compresse jusqu'à tenir sous `maxBytes` (photos envoyées au serveur). */
    fun jpegUnder(b: Bitmap, maxBytes: Int): ByteArray {
        var q = 85; var img = b
        while (true) {
            val out = jpeg(img, q)
            if (out.size <= maxBytes || (q <= 50 && max(img.width, img.height) <= 640)) return out
            if (q > 55) q -= 10 else img = scale(img, (max(img.width, img.height) * .8f).roundToInt())
        }
    }

    private fun hsl(r: Float, g: Float, b: Float): FloatArray {
        val mx = maxOf(r, g, b) / 255f; val mn = minOf(r, g, b) / 255f; val l = (mx + mn) / 2
        val s = if (mx == mn) 0f else if (l > .5f) (mx - mn) / (2 - mx - mn) else (mx - mn) / (mx + mn)
        return floatArrayOf(0f, s, l)
    }

    /** Couleurs dominantes d'une image (même algorithme que la version web). */
    fun palette(b: Bitmap, n: Int = 6): List<String> {
        val w = 72; val h = max(1, (w * b.height.toFloat() / b.width).roundToInt())
        val s = Bitmap.createScaledBitmap(b, w, h, true)
        val px = IntArray(w * h); s.getPixels(px, 0, w, 0, 0, w, h)
        val buckets = HashMap<Int, FloatArray>()
        for (c in px) {
            if ((c ushr 24) < 128) continue
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255
            val k = (r shr 4) shl 8 or ((g shr 4) shl 4) or (bl shr 4)
            val a = buckets.getOrPut(k) { FloatArray(4) }; a[0] += r.toFloat(); a[1] += g.toFloat(); a[2] += bl.toFloat(); a[3]++
        }
        val cand = buckets.values.map { a -> val rgb = floatArrayOf(a[0] / a[3], a[1] / a[3], a[2] / a[3]); val x = hsl(rgb[0], rgb[1], rgb[2]); rgb to a[3] * (.3f + x[1]) * (if (x[2] > .08f && x[2] < .95f) 1f else .3f) }
            .sortedByDescending { it.second }
        val out = mutableListOf<FloatArray>()
        for ((rgb, _) in cand) {
            if (out.all { o -> hypot(hypot(o[0] - rgb[0], o[1] - rgb[1]), o[2] - rgb[2]) > 60 }) out.add(rgb)
            if (out.size >= n) break
        }
        return out.map { "#%02X%02X%02X".format(it[0].roundToInt(), it[1].roundToInt(), it[2].roundToInt()) }
    }

    /** Dégradé d'accent (3 couleurs) tiré d'une couleur de l'arrière-plan. */
    fun accentFrom(hex: String): List<String> {
        val c = android.graphics.Color.parseColor(hex)
        val hsv = FloatArray(3); android.graphics.Color.colorToHSV(c, hsv)
        val x = hsl(((c shr 16) and 255).toFloat(), ((c shr 8) and 255).toFloat(), (c and 255).toFloat())
        val h = hsv[0]; val s = (if (x[1] == 0f) .6f else x[1]).coerceIn(.5f, .95f)
        fun hexHsl(hh: Float, ss: Float, l: Float): String {
            val a = ss * min(l, 1 - l)
            fun f(n: Int): Int { val k = (n + hh / 30f) % 12; return ((l - a * maxOf(-1f, minOf(k - 3, 9 - k, 1f))) * 255).roundToInt() }
            return "#%02X%02X%02X".format(f(0), f(8), f(4))
        }
        return listOf(hexHsl(h, s, .78f), hexHsl(h, s, .6f), hexHsl((h + 28) % 360, s, .6f))
    }
}
