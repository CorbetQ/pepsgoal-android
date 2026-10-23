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
import fr.pepsgoal.core.stepDate

/** Activité unique : toute l'interface est en Jetpack Compose (aucune page web). */
class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TAB = "tab"; const val EXTRA_GOAL = "goal"; const val EXTRA_PROGRESS = "progress"; const val EXTRA_NOTIF = "notif"; const val EXTRA_CHAT = "chat"; const val EXTRA_SETTINGS = "settings"
        const val ACTION_NEW_GOAL = "fr.pepsgoal.app.NEW_GOAL"; const val ACTION_TODAY = "fr.pepsgoal.app.TODAY"; const val ACTION_MESSAGES = "fr.pepsgoal.app.MESSAGES"
        const val ACTION_FOCUS = "fr.pepsgoal.app.FOCUS"; const val ACTION_COACH = "fr.pepsgoal.app.COACH"; const val ACTION_STATS = "fr.pepsgoal.app.STATS"
        const val ACTION_JOURNAL = "fr.pepsgoal.app.JOURNAL"; const val ACTION_WEEK = "fr.pepsgoal.app.WEEK"; const val ACTION_BRIEFING = "fr.pepsgoal.app.BRIEFING"; const val ACTION_FEATURE = "fr.pepsgoal.app.FEATURE"
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
        // Version Play Store : mise à jour proposée par Google Play, et demande d'avis après une belle série
        fr.pepsgoal.app.sys.StoreServices.checkUpdate(this)
        fr.pepsgoal.app.sys.StoreServices.maybeAskReview(this)
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

    private fun shot(s: String) {
        val ui = peps.ui; val pg = peps.pg
        if (!pg.db.sandbox) fr.pepsgoal.app.ui.screens.demoStart(ui)
        ui.toasts.clear(); ui.fs = null
        when (s) {
            "stats" -> { ui.nav("today"); ui.push(Screen.Stats) }
            "journal" -> { ui.nav("today"); ui.push(Screen.Journal) }
            "week" -> { ui.nav("today"); ui.push(Screen.Week) }
            "goal" -> { ui.nav("goals"); pg.myGoals(pg.meId()).firstOrNull()?.let { ui.open(Sheet.Goal(it.id)) } }
            "focus" -> { ui.nav("today"); ui.fs = Fs.Focus() }
            else -> ui.nav(s)
        }
    }

    /** Ouverture depuis une notification, une activité en cours, le widget ou un raccourci. */
    private fun handle(i: Intent?) {
        i ?: return
        val ui = peps.ui; val pg = peps.pg
        // Captures d'écran du Play Store (CI, version debug uniquement) : données de démonstration puis l'écran demandé
        if (BuildConfig.DEBUG) i.getStringExtra("shot")?.let { shot -> i.removeExtra("shot"); i.getStringExtra("lang")?.let { fr.pepsgoal.app.ui.I18n.apply(this, it); peps.ui.themeTick++ }; shot(shot); return }
        if (pg.me() == null) return
        // Liens PepsGoal : objectif partagé (pepsgoal://goal?d=…) ou tag NFC (pepsgoal://nfc/<objectif>)
        i.data?.takeIf { it.scheme == "pepsgoal" }?.let { d ->
            i.data = null
            when (d.host) {
                "goal" -> ui.open(Sheet.ImportGoal(d.toString()))
                "nfc" -> d.lastPathSegment?.let { id ->
                    val g = pg.goalById(id) ?: return@let ui.toast("Ce tag correspond à un objectif supprimé.", "x")
                    if (pg.hasCheck(g.id, pg.stepDate(g))) ui.toast("${g.icon} ${g.title} : déjà validé aujourd’hui ✓", "check")
                    else { fr.pepsgoal.app.ui.doValidate(ui, g.id); if (pg.hasCheck(g.id, pg.stepDate(g))) ui.toast("${g.icon} ${g.title} validé par NFC ✓", "check") }
                }
            }
            return
        }
        when (i.action) {
            ACTION_NEW_GOAL -> { ui.nav("today"); ui.open(Sheet.GoalForm()); return }
            ACTION_TODAY -> { ui.nav("today"); return }
            ACTION_MESSAGES -> { if (pg.socialOn()) ui.nav("friends"); return }
            ACTION_FOCUS -> { ui.fs = Fs.Focus(); return }
            ACTION_COACH -> { ui.push(Screen.CoachChat); return }
            ACTION_STATS -> { ui.push(Screen.Stats); return }
            ACTION_JOURNAL -> { ui.push(Screen.Journal); return }
            ACTION_WEEK -> { ui.push(Screen.Week); return }
            ACTION_BRIEFING -> { ui.nav("today"); fr.pepsgoal.app.sys.Speaker.briefing(this); return }
            // Raccourcis de l'Assistant Google : « Ouvre focus sur PepsGoal »…
            ACTION_FEATURE -> {
                // Raccourci d'un objectif précis (appui long sur l'icône, ou Assistant)
                i.getStringExtra("goal")?.let { id -> if (pg.goalById(id) != null) { if (!pg.hasCheck(id, pg.stepDate(pg.goalById(id)!!))) fr.pepsgoal.app.ui.doValidate(ui, id) else ui.open(Sheet.Goal(id)) }; i.removeExtra("goal"); return }
                val f = i.getStringExtra("feature")?.lowercase()?.trim().orEmpty()
                // « valider méditation » : l'objectif dont le titre est cité
                val named = f.removePrefix("valider").removePrefix("valide").trim().takeIf { it.length >= 3 }?.let { n ->
                    pg.myGoals(pg.meId()).filter { !pg.isClosedGoal(it) }.firstOrNull { fr.pepsgoal.app.ui.VoiceCmd.norm(it.title).contains(fr.pepsgoal.app.ui.VoiceCmd.norm(n)) || fr.pepsgoal.app.ui.VoiceCmd.norm(n).contains(fr.pepsgoal.app.ui.VoiceCmd.norm(it.title)) }
                }
                if (named != null) { fr.pepsgoal.app.ui.doValidate(ui, named.id); return }
                when (f) {
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
        // Notification du bilan du soir : la fenêtre du bilan, lue à voix haute avec « Écouter le bilan »
        if (i.getBooleanExtra("evening", false)) {
            i.removeExtra("evening"); ui.nav("today"); ui.open(Sheet.Evening)
            if (i.getBooleanExtra("speak", false)) { i.removeExtra("speak"); fr.pepsgoal.app.sys.Speaker.say(this, fr.pepsgoal.app.sys.Speaker.eveningText(this)) }
            return
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
