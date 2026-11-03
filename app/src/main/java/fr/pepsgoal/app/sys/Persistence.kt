package fr.pepsgoal.app.sys

import android.content.BroadcastReceiver
import fr.pepsgoal.app.PepsApp
import kotlinx.coroutines.launch

/** Keep a receiver alive until its synchronous model mutations have reached atomic storage. */
internal fun BroadcastReceiver.persistBeforeFinish(app: PepsApp) {
    val result = goAsync()
    app.scope.launch { try { app.flushLocalSafely() } finally { result.finish() } }
}

internal suspend fun PepsApp.flushLocalSafely() {
    try { store.flush() }
    catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { ui.toast("L’enregistrement local a échoué. Vérifie l’espace disponible.", "alert") }
}
