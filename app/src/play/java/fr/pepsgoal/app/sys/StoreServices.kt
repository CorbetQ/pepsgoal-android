package fr.pepsgoal.app.sys

import android.app.Activity
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory
import fr.pepsgoal.app.peps

/**
 * Version Play Store : mise à jour flexible proposée par Google Play (téléchargement en arrière-plan, puis
 * « Redémarrer » dans un bandeau), et fenêtre de notation du Play Store après une belle série.
 */
object StoreServices {
    private var listening = false

    fun checkUpdate(activity: Activity) {
        val app = activity.peps
        val now = System.currentTimeMillis()
        if (now - app.prefs.raw.getLong("playUpdateAt", 0L) < 6 * 3_600_000L) return
        app.prefs.raw.edit().putLong("playUpdateAt", now).apply()
        val m = AppUpdateManagerFactory.create(activity)
        if (!listening) {
            listening = true
            m.registerListener(InstallStateUpdatedListener { st ->
                if (st.installStatus() == InstallStatus.DOWNLOADED) app.ui.toast("Nouvelle version de PepsGoal prête.", "check", "Redémarrer") { m.completeUpdate() }
            })
        }
        m.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) { app.ui.toast("Nouvelle version de PepsGoal prête.", "check", "Redémarrer") { m.completeUpdate() }; return@addOnSuccessListener }
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                try { @Suppress("DEPRECATION") m.startUpdateFlowForResult(info, AppUpdateType.FLEXIBLE, activity, 7101) } catch (e: Exception) {}
            }
        }
    }

    /** Après au moins 30 jours de série, une fois par trimestre au plus : Google Play décide s'il affiche la fenêtre. */
    fun maybeAskReview(activity: Activity) {
        val app = activity.peps; val pg = app.pg; val u = pg.me() ?: return
        val last = app.prefs.raw.getLong("reviewAt", 0L); val now = System.currentTimeMillis()
        if (now - last < 90L * 86_400_000 || pg.globalStreak(u.id).n < 30) return
        app.prefs.raw.edit().putLong("reviewAt", now).apply()
        val rm = ReviewManagerFactory.create(activity)
        rm.requestReviewFlow().addOnSuccessListener { info -> try { rm.launchReviewFlow(activity, info) } catch (e: Exception) {} }
    }
}
