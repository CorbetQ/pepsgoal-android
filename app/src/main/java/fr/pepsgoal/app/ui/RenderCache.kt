package fr.pepsgoal.app.ui

import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * Rendu hors écran des dessins lourds (scènes illustrées : jusqu'à ~1500 commandes par image pour le tram).
 * Le dessin se fait sur un fil d'arrière-plan dans une image ; le fil principal n'a plus qu'une image à poser.
 */

/** Dessine [paint] dans [img] (utilisable hors du fil principal : rien n'y lit l'état Compose). */
internal fun renderInto(img: ImageBitmap, density: Float, paint: DrawScope.() -> Unit) {
    val c = androidx.compose.ui.graphics.Canvas(img)
    c.nativeCanvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
    CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, c, Size(img.width.toFloat(), img.height.toFloat()), paint)
}

/** Aperçus figés déjà rendus (boutique, apparence) : une seule fois par taille, gardés tant que la mémoire le permet. */
private object Thumbs {
    private val lru = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    fun get(k: String): ImageBitmap? = lru.get(k)
    fun put(k: String, v: ImageBitmap) { lru.put(k, v) }
}

/**
 * Aperçu figé : rendu une fois en arrière-plan puis repris du cache (défilement des listes sans redessiner).
 * [placeholder] est dessiné le temps du premier rendu (un dégradé simple).
 */
@Composable
internal fun StillCanvas(id: String, modifier: Modifier, placeholder: DrawScope.() -> Unit, paint: DrawScope.() -> Unit) {
    val dens = LocalDensity.current.density
    var px by remember { mutableStateOf(IntSize.Zero) }
    // Image affichée par cet aperçu (gardée même si le cache l'a évincée entre-temps)
    var mine by remember { mutableStateOf<ImageBitmap?>(null) }
    if (px.width > 0 && px.height > 0) LaunchedEffect(id, px) {
        val k = "$id@${px.width}x${px.height}"
        mine = Thumbs.get(k) ?: withContext(Dispatchers.Default) { ImageBitmap(px.width, px.height).also { renderInto(it, dens, paint) } }.also { Thumbs.put(k, it) }
    }
    Canvas(modifier.onSizeChanged { px = it }) {
        val w = size.width.roundToInt(); val h = size.height.roundToInt()
        // Premier affichage d'un aperçu déjà rendu : repris du cache dès la première image (pas de clignotement)
        val img = mine?.takeIf { it.width == w && it.height == h } ?: Thumbs.get("$id@${w}x$h")
        if (img != null) drawImage(img) else placeholder()
    }
}

/**
 * Fond animé rendu hors du fil principal, à [scale] de la résolution (le fond est voilé et flouté derrière le verre :
 * la perte de netteté ne se voit pas). Trois images tournent : l'une s'affiche pendant qu'une autre se dessine.
 * Horloge arrêtée (fond figé, caché ou application en arrière-plan) : plus aucun travail.
 */
@Composable
internal fun OffscreenCanvas(id: String, phase: State<Float>, modifier: Modifier, scale: Float = .6f, placeholder: DrawScope.() -> Unit, paint: DrawScope.(Float) -> Unit) {
    val dens = LocalDensity.current.density
    var px by remember { mutableStateOf(IntSize.Zero) }
    val shown = remember { mutableStateOf<ImageBitmap?>(null) }
    if (px.width > 0 && px.height > 0) LaunchedEffect(id, px, scale) {
        val w = (px.width * scale).roundToInt().coerceAtLeast(1); val h = (px.height * scale).roundToInt().coerceAtLeast(1)
        val bufs = withContext(Dispatchers.Default) { Array(3) { ImageBitmap(w, h) } }
        var i = 0
        snapshotFlow { phase.value }.conflate().collect { t ->
            val b = bufs[i % 3]
            withContext(Dispatchers.Default) { renderInto(b, dens * scale) { paint(t) } }
            shown.value = b; i++
        }
    }
    Canvas(modifier.onSizeChanged { px = it }) {
        val img = shown.value
        if (img == null) placeholder()
        else drawImage(img, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
    }
}

/**
 * Image décodée hors du fil principal (null le temps du décodage) : ouvrir une conversation ou une galerie de
 * photos ne bloque plus le défilement pendant la lecture des fichiers.
 */
@Composable
fun rememberDecoded(vararg keys: Any?, decode: () -> ImageBitmap?): ImageBitmap? {
    val img by produceState<ImageBitmap?>(null, *keys) { value = withContext(Dispatchers.IO) { try { decode() } catch (e: Exception) { null } } }
    return img
}
