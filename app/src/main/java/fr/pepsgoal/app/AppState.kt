package fr.pepsgoal.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import fr.pepsgoal.app.sys.LiveUpdates
import fr.pepsgoal.app.sys.Notifier
import fr.pepsgoal.app.sys.WidgetUpdater
import fr.pepsgoal.core.Celebration
import fr.pepsgoal.core.Hooks
import fr.pepsgoal.core.Pg
import fr.pepsgoal.core.RecapStats
import fr.pepsgoal.core.Slide
import fr.pepsgoal.core.cloud.ApiError
import fr.pepsgoal.core.model.Notif
import fr.pepsgoal.core.nowMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fenêtres (feuilles du bas) de l'application. */
sealed class Sheet {
    class GoalForm(val editId: String? = null, val preType: String? = null, val preTitle: String? = null, val preIcon: String? = null, val preFields: Map<String, String>? = null) : Sheet()
    class Goal(val id: String) : Sheet()
    class Progress(val id: String) : Sheet()
    class User(val id: String) : Sheet()
    object Notifs : Sheet()
    class Confirm(val title: String, val text: String, val ok: String, val danger: Boolean = false, val back: Sheet? = null, val run: () -> Unit) : Sheet()
    class ShareHub(val goalId: String? = null, val text: String? = null, val period: String? = null, val step: String? = null) : Sheet()
    class ShareFeed(val goalId: String? = null, val text: String = "") : Sheet()
    class ShareText(val text: String) : Sheet()
    object Avatar : Sheet()
    class Tuto(val step: Int = 0) : Sheet()
    class Legal(val doc: String) : Sheet()
    object A11y : Sheet()
    object GoalGroups : Sheet()
    class ModUser(val id: String) : Sheet()
    object Appeal : Sheet()
    class Report(val postId: String) : Sheet()
    object Compose : Sheet()
    class StoryNew(val kind: String = "progress", val period: String = "week", val goalId: String? = null) : Sheet()
    class Export(val json: String) : Sheet()
    object Notes : Sheet()
    object GroupNew : Sheet()
    class GroupInfo(val gid: String) : Sheet()
    object DeleteAccount : Sheet()
}

/** Plein écran : célébrations, récaps, stories. */
sealed class Fs {
    class Celebrate(val c: Celebration) : Fs()
    class Recap(val p: String, val slides: List<Slide>, val st: RecapStats, var i: Int = 0) : Fs()
    class Stories(val groups: List<Pg.StoryGroup>, val ui: Int, val si: Int, val archive: Boolean = false) : Fs()
    /** Tutoriel plein écran (premiers pas et réglages de base). */
    class Tuto(val start: Int = 0) : Fs()
}

/** Écrans plein écran empilés au-dessus des onglets. */
sealed class Screen {
    class Settings(val cat: String? = null) : Screen()
    class Chat(val with: String? = null, val group: String? = null) : Screen()
    /** Historique complet des stories (page dédiée, ouverte depuis la carte « Mes stories » du profil). */
    object Stories : Screen()
    /** Page dédiée aux commentaires d'une publication. */
    class Comments(val postId: String) : Screen()
    /** Page dédiée à un badge : la médaille, la progression et toutes ses étapes. */
    class Badge(val key: String) : Screen()
}

class Toast(val id: Long, val text: String, val icon: String)

/**
 * État de l'interface (équivalent de l'objet `UI` de la version web) et réactions aux demandes de la logique.
 * Toute lecture de `rev` dans l'interface la redessine quand la base change.
 */
class AppState(private val app: PepsApp) : Hooks {
    var rev by mutableLongStateOf(0L)
        private set
    var tab by mutableStateOf("today")
    var sheet by mutableStateOf<Sheet?>(null)
    var fs by mutableStateOf<Fs?>(null)
    val screens = mutableStateListOf<Screen>()
    val toasts = mutableStateListOf<Toast>()
    val fsQueue = ArrayDeque<Fs>()
    var sparks by mutableStateOf<Pair<Offset, Int>?>(null)
    var touch = Offset(540f, 1100f)
    var goalFilter by mutableStateOf("all")
    var groupFilter by mutableStateOf("all")
    var feedTab by mutableStateOf("friends")
    var modTab by mutableStateOf("accounts")
    var friendQuery by mutableStateOf("")
    var postLimit by mutableStateOf(20)
    /** Thème recalculé (réglages de l'appareil modifiés). */
    var themeTick by mutableLongStateOf(0L)
    private var bumpJob: Job? = null

    val pg get() = app.pg
    val cloud get() = app.cloud
    val prefs get() = app.prefs
    val scope: CoroutineScope get() = app.scope

    fun bump() { rev++ }

    fun onlineChanged(online: Boolean) {
        if (!online) {
            if (tab == "feed" || tab == "friends") tab = "today"
            if (sheet is Sheet.Compose || sheet is Sheet.StoryNew || sheet is Sheet.User || sheet is Sheet.Report || sheet is Sheet.ShareHub) sheet = null
            screens.removeAll { it is Screen.Chat }
            if (fs is Fs.Stories) fs = null
            toast("Hors ligne : fonctions sociales masquées. Tes objectifs restent disponibles.", "cloudoff")
        } else if (pg.me() != null) toast("Connexion rétablie : fonctions sociales de retour.", "cloud")
        bump()
    }

    fun sessionChanged() {
        sheet = null; fs = null; screens.clear(); tab = "today"; fsQueue.clear()
        pg.me()?.let { if (!it.tutorialDone) fs = Fs.Tuto() }
        bump(); WidgetUpdater.update(app); LiveUpdates.refresh(app)
    }

    /* ---------- Hooks ---------- */
    override fun toast(text: String, icon: String) {
        val t = Toast(nowMs() + toasts.size, text, icon)
        while (toasts.size >= 3) toasts.removeAt(0)
        toasts.add(t)
        scope.launch { delay(3600); toasts.remove(t) }
    }

    override fun sparks(pts: Int) {
        if (prefs.motion) { toast("+$pts flammes", "flame"); return }
        sparks = touch to pts
    }

    override fun celebrate(c: Celebration) {
        if (!app.foreground) return
        val f = Fs.Celebrate(c)
        if (fs == null) fs = f else fsQueue.addLast(f)
    }

    fun closeFs() {
        fs = fsQueue.removeFirstOrNull()
    }

    override fun localNotification(n: Notif) {
        if (!app.foreground) Notifier.local(app, n)
        else if (n.type in setOf("reminder", "period")) toast(n.text, if (n.type == "period") "clock" else "bell")
        bump()
    }

    override fun liveChanged() {
        LiveUpdates.refresh(app); WidgetUpdater.update(app)
    }

    /* ---------- navigation ---------- */
    fun open(s: Sheet) { sheet = s }
    fun close() { sheet = null }
    fun push(s: Screen) { sheet = null; screens.add(s) }
    fun nav(t: String) { tab = t; sheet = null; screens.clear(); if (t in setOf("feed", "friends", "mod", "today")) cloud.scheduleSocial(50) }

    /** Bouton retour : ferme ce qui est ouvert, revient à l'accueil, sinon laisse quitter. */
    fun back(): Boolean {
        when {
            fs != null -> closeFs()
            sheet is Sheet.Confirm && (sheet as Sheet.Confirm).back != null -> sheet = (sheet as Sheet.Confirm).back
            sheet != null -> sheet = null
            screens.isNotEmpty() -> screens.removeAt(screens.lastIndex)
            tab != "today" -> tab = "today"
            else -> return false
        }
        return true
    }

    /** Lance une action du serveur ; une erreur s'affiche en bas de l'écran. */
    fun run(okMsg: String? = null, icon: String = "check", block: suspend () -> Unit) {
        scope.launch {
            try { block(); okMsg?.let { toast(it, icon) } }
            catch (e: ApiError) { toast(e.message ?: "Action impossible pour le moment.", "x") }
            catch (e: Exception) { toast(e.message ?: "Action impossible pour le moment.", "x") }
        }
    }

    fun confirm(title: String, text: String, ok: String, danger: Boolean = true, back: Sheet? = null, run: () -> Unit) { sheet = Sheet.Confirm(title, text, ok, danger, back, run) }
}
