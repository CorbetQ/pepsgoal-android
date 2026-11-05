package fr.pepsgoal.app.ui

/** Compiled from each background's JSON assets; no disk access in an animation frame. */
internal data class BackgroundStyle(
    val id: String, val saturation: Float, val contrast: Float,
    val lightStrength: Float, val shadowStrength: Float,
    val lightColor: String, val shadowColor: String,
    val texture: String, val textureStrength: Float,
    val cycleMs: Int, val fps: Int, val restPhase: Float, val focusY: Float, val capability: fr.pepsgoal.core.SceneCapability,
    val focusX: Float = .5f, val overlay: String = "none", val overlayCount: Int = 0
)

/** Different previews may render concurrently. Every render restores the previous worker context. */
private data class BackgroundDrawingState(val style: BackgroundStyle, val sun: Float, val night: Float)
private val backgroundDrawingState = ThreadLocal<BackgroundDrawingState?>()
internal fun currentBackgroundStyle(): BackgroundStyle = backgroundDrawingState.get()?.style ?: backgroundStyle("pop")
internal fun currentShadowDirection(): Float {
    val state=backgroundDrawingState.get()
    val source=if ((state?.night ?: 0f)>.5f) .78f else state?.sun ?: .25f
    return .5f-source
}
internal fun currentShadowOpacity(): Float = 1f - (backgroundDrawingState.get()?.night ?: 0f) * .55f
internal fun <T> withBackgroundStyle(id: String, sun: Float = .25f, night: Float = 0f, block: () -> T): T {
    val previous = backgroundDrawingState.get()
    backgroundDrawingState.set(BackgroundDrawingState(backgroundStyle(id), sun, night))
    return try { block() } finally {
        if (previous == null) backgroundDrawingState.remove() else backgroundDrawingState.set(previous)
    }
}
