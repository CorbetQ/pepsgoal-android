package fr.pepsgoal.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import fr.pepsgoal.core.ALL_BGS
import fr.pepsgoal.app.peps
import fr.pepsgoal.app.ui.theme.hex

@Composable
fun SceneBg(key: String, modifier: Modifier = Modifier, still: Boolean = false) {
    val artwork = rememberSceneArtwork(key, still)
    // Dégradé du ciel, affiché le temps du premier rendu hors écran
    val sky = remember(key) { (ALL_BGS.firstOrNull { it.key == key }?.sky ?: listOf("#1A1240", "#07081A")).map { hex(it) } }
    val placeholder: DrawScope.() -> Unit = { drawRect(Brush.verticalGradient(sky)) }
    // Aperçu figé : rendu une seule fois, puis repris du cache à chaque défilement
    if (still) { StillCanvas("scene:$key:${artwork != null}", modifier.fillMaxSize(), placeholder) {
        if (artwork != null) paintSceneArtwork(key, artwork, backgroundStyle(key).restPhase, null)
        else if (hasSceneArtwork(key)) placeholder() else scene(key, backgroundStyle(key).restPhase)
    }; return }
    // Fond animé : dessiné hors du fil principal, au rythme plafonné par les réglages de performance
    val style = backgroundStyle(key)
    val phase = if (artwork != null && !style.capability.weather && style.overlayCount == 0)
        remember(key) { androidx.compose.runtime.mutableFloatStateOf(style.restPhase) }
    else bgPhaseState(style.cycleMs, style.fps, initial = style.restPhase)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val light = ctx.peps.prefs.bgLight
    val dark = fr.pepsgoal.app.ui.theme.LocalPal.current.dark
    val live = LocalSceneEnvironment.current
    val weatherOn = ctx.peps.prefs.bgWeather
    val environment = live?.takeIf { style.capability.light || style.capability.weather }
        ?.let { sceneLighting(it, light, dark, weatherOn) }
    OffscreenCanvas("scene:$key:$environment:${artwork != null}", phase, modifier.fillMaxSize(),
        scale = fr.pepsgoal.app.sys.Perf.flags.sceneScale, placeholder = placeholder) { t ->
        val lighting = if (key == "rainwindow" && !weatherOn) null else environment
        if (artwork != null) paintSceneArtwork(key, artwork, t, environment, weatherEnabled = weatherOn)
        else if (hasSceneArtwork(key)) placeholder() else scene(key, t, environment = lighting)
    }
}

internal fun sceneLighting(e: fr.pepsgoal.core.SceneEnvironment, light: String, dark: Boolean, weather: Boolean): fr.pepsgoal.core.SceneEnvironment {
    val fixed = when (light) {
        "real" -> e
        "day" -> e.copy(night=0f,dawn=0f,day=1f,dusk=0f,sun=.55f,sunDown=false)
        else -> e.copy(night=if(dark)1f else 0f,dawn=0f,day=if(dark)0f else 1f,dusk=0f,sun=.55f,sunDown=dark)
    }
    return if(weather)fixed else fixed.copy(condition="clear",clouds=0f,wind=0f,precipitation=0f,freshness=0f)
}
