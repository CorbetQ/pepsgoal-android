package fr.pepsgoal.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import fr.pepsgoal.app.AppState
import fr.pepsgoal.core.SceneAtmosphere
import fr.pepsgoal.core.SceneEnvironment
import kotlinx.coroutines.delay
import java.time.Instant

/** One wall clock for the whole app, independent of decorative animation clocks. */
val LocalSceneEnvironment = compositionLocalOf<SceneEnvironment?> { null }

@Composable
fun rememberSceneEnvironment(ui: AppState): SceneEnvironment {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var instant by remember { mutableStateOf(Instant.now()) }
    DisposableEffect(context, lifecycle) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) instant = Instant.now()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                instant = Instant.now()
                delay(60_000L - System.currentTimeMillis().mod(60_000L))
            }
        }
    }
    val place = ui.pg.me()?.settings?.weather
    val cache = Wx.cached(context, place)
    val scene = ui.sceneKind()
    val capability = backgroundStyle(scene).capability
    val usesWeather = ui.prefs.sunTheme || ui.pg.me()?.settings?.theme == "weather" ||
        (ui.prefs.bgWhere != "off" && ((ui.prefs.bgWeather && capability.weather) ||
            (ui.prefs.bgLight == "real" && capability.light)))
    // Resume and minute ticks reuse Wx's fifteen-minute cache and retry backoff; no background worker.
    LaunchedEffect(instant, place?.lat, place?.lon, usesWeather) {
        if (usesWeather && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) Wx.ensure(context, ui, false)
    }
    return remember(instant, place?.lat, place?.lon, place?.tz, cache, ui.rev, scene) {
        SceneAtmosphere.resolve(instant, place, cache?.data, cache?.at)
    }
}
