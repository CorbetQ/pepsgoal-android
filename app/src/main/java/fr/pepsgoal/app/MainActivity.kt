package fr.pepsgoal.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import fr.pepsgoal.app.sys.Notifier
import fr.pepsgoal.app.ui.PepsRoot
import fr.pepsgoal.app.ui.watch

/** Activité unique : toute l'interface est en Jetpack Compose (aucune page web). */
class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TAB = "tab"; const val EXTRA_GOAL = "goal"; const val EXTRA_PROGRESS = "progress"; const val EXTRA_NOTIF = "notif"; const val EXTRA_CHAT = "chat"; const val EXTRA_SETTINGS = "settings"
        const val ACTION_NEW_GOAL = "fr.pepsgoal.app.NEW_GOAL"; const val ACTION_TODAY = "fr.pepsgoal.app.TODAY"; const val ACTION_MESSAGES = "fr.pepsgoal.app.MESSAGES"
        const val ACTION_FOCUS = "fr.pepsgoal.app.FOCUS"; const val ACTION_COACH = "fr.pepsgoal.app.COACH"; const val ACTION_STATS = "fr.pepsgoal.app.STATS"
        const val ACTION_JOURNAL = "fr.pepsgoal.app.JOURNAL"; const val ACTION_FEATURE = "fr.pepsgoal.app.FEATURE"
    }

    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> peps.pg.me()?.let { it.settings.browser = ok; peps.store.commit() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val ui = peps.ui
        setContent {
            BackHandler(enabled = ui.fs != null || ui.sheet != null || ui.screens.isNotEmpty() || ui.tab != "today") { ui.back() }
            PepsRoot(ui)
            val meId = ui.watch().let { peps.pg.me()?.id }
            androidx.compose.runtime.LaunchedEffect(meId) { if (meId != null) askNotifications() }
        }
        if (savedInstanceState == null) handle(intent)
    }

    override fun onResume() {
        super.onResume()
        askNotifications()
    }

    private fun askNotifications() {
        // Notifications : demandées une seule fois, dès qu'un profil existe (y compris juste après la connexion)
        val prefs = getSharedPreferences("pepsgoal.device", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("askedNotif", false) && peps.pg.me() != null &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("askedNotif", true).apply(); askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Ouverture depuis une notification, une activité en cours, le widget ou un raccourci. */
    private fun handle(i: Intent?) {
        i ?: return
        val ui = peps.ui; val pg = peps.pg
        if (pg.me() == null) return
        when (i.action) {
            ACTION_NEW_GOAL -> { ui.nav("today"); ui.open(Sheet.GoalForm()); return }
            ACTION_TODAY -> { ui.nav("today"); return }
            ACTION_MESSAGES -> { if (pg.socialOn()) ui.nav("friends"); return }
            ACTION_FOCUS -> { ui.fs = Fs.Focus(); return }
            ACTION_COACH -> { ui.push(Screen.CoachChat); return }
            ACTION_STATS -> { ui.push(Screen.Stats); return }
            ACTION_JOURNAL -> { ui.push(Screen.Journal); return }
            // Raccourcis de l'Assistant Google : « Ouvre focus sur PepsGoal »…
            ACTION_FEATURE -> {
                when (i.getStringExtra("feature")?.lowercase()?.trim()) {
                    "focus", "concentration", "pomodoro" -> ui.fs = Fs.Focus()
                    "coach", "assistant" -> ui.push(Screen.CoachChat)
                    "statistiques", "stats" -> ui.push(Screen.Stats)
                    "journal", "humeur" -> ui.push(Screen.Journal)
                    "valider", "validation" -> pg.todayPlan(pg.meId()).due.firstOrNull { !it.done }?.let { fr.pepsgoal.app.ui.doValidate(ui, it.g.id) } ?: ui.nav("today")
                    else -> ui.nav("today")
                }
                return
            }
        }
        i.getStringExtra(EXTRA_SETTINGS)?.let { cat -> i.removeExtra(EXTRA_SETTINGS); ui.push(Screen.Settings(cat)); return }
        i.getStringExtra(EXTRA_CHAT)?.let { key ->
            if (!pg.socialOn()) return@let
            ui.nav("friends"); Notifier.clearChat(this, key)
            ui.push(if (key.startsWith("g:")) Screen.Chat(group = key.drop(2)) else Screen.Chat(with = key.drop(2)))
            return
        }
        i.getStringExtra(EXTRA_TAB)?.let { t -> ui.nav(if (t in setOf("feed", "friends") && !pg.socialOn()) "today" else t) }
        i.getStringExtra(EXTRA_PROGRESS)?.let { if (pg.goalById(it) != null) ui.open(Sheet.Progress(it)) }
        i.getStringExtra(EXTRA_GOAL)?.let { if (pg.goalById(it) != null) ui.open(Sheet.Goal(it)) }
        i.getStringExtra(EXTRA_NOTIF)?.let { id -> pg.db.notifs.firstOrNull { it.id == id }?.let { it.read = true; peps.store.rawSave() } }
        i.removeExtra(EXTRA_CHAT); i.removeExtra(EXTRA_GOAL); i.removeExtra(EXTRA_PROGRESS)
    }
}
