package fr.pepsgoal.app.sys

import android.app.Activity

/** Version GitHub : les mises à jour passent par l'outil intégré (Updater) ; pas de demande d'avis. */
object StoreServices {
    fun checkUpdate(activity: Activity) {}
    fun maybeAskReview(activity: Activity) {}
}
