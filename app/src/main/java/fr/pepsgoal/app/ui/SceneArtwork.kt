package fr.pepsgoal.app.ui

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import fr.pepsgoal.core.SceneEnvironment
import kotlin.math.max
import kotlin.math.roundToInt

/** Original illustrations live next to their scene's editable layers. No network at runtime. */
internal object SceneArtworks {
    private val cache = object : LruCache<String, ImageBitmap>(18 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun decode(assets: AssetManager, key: String, preview: Boolean): ImageBitmap? {
        val cacheKey = "$key:$preview"
        cache.get(cacheKey)?.let { return it }
        val path = "backgrounds/$key/art/artwork.png"
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
            val edge = if (preview) 512 else 1536
            var sample = 1
            while (max(options.outWidth, options.outHeight) / (sample * 2) >= edge) sample *= 2
            options.inJustDecodeBounds = false
            options.inSampleSize = sample
            assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
                ?.asImageBitmap()?.also { cache.put(cacheKey, it) }
        } catch (_: java.io.FileNotFoundException) {
            null // A scene without an accepted illustration keeps its existing renderer.
        }
    }
}

/** Decoding happens off the UI thread, including shop thumbnails. */
@Composable
internal fun rememberSceneArtwork(key: String, preview: Boolean = false): ImageBitmap? {
    if (!hasSceneArtwork(key)) return null
    val assets = LocalContext.current.assets
    return androidx.compose.runtime.key(assets, key, preview) {
        rememberDecoded(assets, key, preview) { SceneArtworks.decode(assets, key, preview) }
    }
}

private val ARTWORK_PREVIEW_LIGHT by lazy {
    fr.pepsgoal.core.SceneAtmosphere.resolve(java.time.Instant.EPOCH).copy(
        night = 0f, dawn = 0f, day = 1f, dusk = 0f, sun = .3f, sunDown = false,
        clouds = 0f, condition = "clear", wind = 0f, precipitation = 0f, freshness = 0f)
}

/** Preserve painted modelling while changing exposure outdoors and through the window. */
internal fun DrawScope.paintSceneArtwork(key: String, image: ImageBitmap, t: Float, environment: SceneEnvironment?, weatherEnabled: Boolean = true) {
    val style = backgroundStyle(key)
    val light = environment?.takeIf { style.capability.light }
    val sky = environment?.takeIf { style.capability.weather } ?: ARTWORK_PREVIEW_LIGHT
    if (style.capability.weather) weatherSky(sky, t) else drawRect(Color(0xFF20182D))
    val filter = light?.let {
        val night = it.night
        val warm = it.dawn + it.dusk
        val overcast = (it.clouds / 100f).coerceIn(0f, 1f) * .12f
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            1f - night * .68f - overcast, 0f, 0f, 0f, warm * 12f,
            0f, 1f - night * .62f - warm * .06f - overcast, 0f, 0f, night * 4f,
            0f, 0f, 1f - night * .43f - warm * .12f - overcast, 0f, night * 12f,
            0f, 0f, 0f, 1f, 0f
        )))
    }
    // Fill without distortion. A tall handset crops the sides, never stretches architecture.
    val scale = max(size.width / image.width, size.height / image.height)
    val width = (image.width * scale).roundToInt()
    val height = (image.height * scale).roundToInt()
    val left=((size.width-width)*style.focusX).roundToInt()
    val top=((size.height-height)*style.focusY).roundToInt()
    drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(left,top),
        dstSize = IntSize(width, height), colorFilter = filter, filterQuality = FilterQuality.Medium)
    artworkMotion(key, t, if (key == "rainwindow" && !weatherEnabled) null else environment,
        left.toFloat(), top.toFloat(), width.toFloat(), height.toFloat())
}
