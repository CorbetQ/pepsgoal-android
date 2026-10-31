package fr.pepsgoal.app.sys

import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver

/**
 * Suivi des images par seconde (8.5), pour le régulateur de fluidité et la page Performances : un rappel du
 * Choreographer à chaque synchronisation verticale compte les images réellement servies au fil principal. Une
 * mesure par seconde est transmise à [Perf.onSample]. Rien n'est alloué par image.
 *
 * Économe : le suivi ne tourne que lorsque l'interface dessine (écouteur de dessin de la fenêtre). Interface au repos
 * plus d'une seconde et demie : la boucle s'arrête d'elle-même et reprend au dessin suivant. Arrêté aussi quand
 * l'application n'est pas au premier plan, ou quand personne n'en a besoin (régulateur coupé, page fermée).
 */
object FpsMonitor : Choreographer.FrameCallback, ViewTreeObserver.OnDrawListener {
    /** Utilisateurs du suivi : le régulateur, la page Performances. */
    const val REG = 1
    const val PAGE = 2

    private var users = 0
    private var view: View? = null
    private var looping = false
    private var t0 = 0L
    private var frames = 0
    private var lastDraw = 0L

    /** Déclare (ou retire) un utilisateur du suivi. */
    fun want(who: Int, on: Boolean) {
        users = if (on) users or who else users and who.inv()
        if (users == 0) stop() else if (view != null) start()
    }

    /** Fenêtre de l'activité au premier plan (onResume) : le suivi peut tourner ; on ignore les premières secondes. */
    fun attach(v: View) {
        if (view !== v) { detach(); view = v; try { v.viewTreeObserver.addOnDrawListener(this) } catch (e: Exception) {} }
        Perf.warmUntil = SystemClock.uptimeMillis() + 4000
        lastDraw = SystemClock.uptimeMillis()
        if (users != 0) start()
    }

    /** Activité en pause : plus aucun rappel. */
    fun detach() {
        stop()
        view?.let { v -> try { if (v.viewTreeObserver.isAlive) v.viewTreeObserver.removeOnDrawListener(this) } catch (e: Exception) {} }
        view = null
    }

    override fun onDraw() {
        lastDraw = SystemClock.uptimeMillis()
        if (users != 0 && !looping && view != null) start()
    }

    private fun start() {
        if (looping) return
        looping = true; t0 = 0L; frames = 0
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stop() {
        if (!looping) return
        looping = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!looping) return
        if (t0 == 0L) { t0 = frameTimeNanos; frames = 0 } else frames++
        val el = frameTimeNanos - t0
        if (el >= 1_000_000_000L) {
            val now = SystemClock.uptimeMillis()
            val hz = (view?.display?.refreshRate ?: 60f).coerceIn(30f, 165f)
            Perf.onSample(frames * 1e9f / el, hz, now)
            t0 = frameTimeNanos; frames = 0
            // Interface au repos : on s'arrête jusqu'au prochain dessin
            if (now - lastDraw > 1500) { looping = false; return }
        }
        Choreographer.getInstance().postFrameCallback(this)
    }
}
