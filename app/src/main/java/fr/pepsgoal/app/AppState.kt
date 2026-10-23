package fr.pepsgoal.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import fr.pepsgoal.core.model.Goal
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
    /** Modifications locales en attente d'envoi (hors ligne). */
    object Pending : Sheet()
    object Status : Sheet()
    object GroupNew : Sheet()
    class GroupInfo(val gid: String) : Sheet()
    object DeleteAccount : Sheet()
    /** Jours de repos planifiés. */
    object Rest : Sheet()
    /** Actions rapides d'un objectif (balayage vers la gauche). */
    class Quick(val id: String) : Sheet()
    /** Bilan du soir guidé. */
    object Evening : Sheet()
    /** Journée difficile : essentiels et activation. */
    object Hard : Sheet()
    /** « Je n'ai pas pu » : raison d'un jour manqué. */
    class Miss(val id: String, val date: String) : Sheet()
    /** Nouvelle récompense personnelle (ou modification). */
    class Reward(val id: String? = null) : Sheet()
    /** Code QR et lien d'un objectif à partager. */
    class GoalQr(val id: String) : Sheet()
    /** Qui a réagi à une publication. */
    class Likers(val postId: String) : Sheet()
    /** Objectif reçu par lien ou code QR, à ajouter. */
    class ImportGoal(val link: String) : Sheet()
}

/** Plein écran : célébrations, récaps, stories. */
sealed class Fs {
    class Celebrate(val c: Celebration) : Fs()
    class Recap(val p: String, val slides: List<Slide>, val st: RecapStats, var i: Int = 0) : Fs()
    class Stories(val groups: List<Pg.StoryGroup>, val ui: Int, val si: Int, val archive: Boolean = false) : Fs()
    /** Tutoriel plein écran (premiers pas et réglages de base). */
    class Tuto(val start: Int = 0) : Fs()
    /** Mode Focus (session de concentration). */
    class Focus(val goalId: String? = null) : Fs()
    /** Minuteur d'intervalles (effort / repos) d'un objectif de durée. */
    class Intervals(val goalId: String) : Fs()
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
    /** Conversation avec le coach IA. */
    object CoachChat : Screen()
    /** Journal et humeur du jour. */
    object Journal : Screen()
    /** Statistiques avancées. */
    object Stats : Screen()
    /** Défis de la communauté. */
    object Challenges : Screen()
    /** Recherche globale : objectifs, amis, messages, réglages, journal. */
    class Search(val q: String = "") : Screen()
    /** Planning de la semaine. */
    object Week : Screen()
    /** Boutique d'icônes et de thèmes (flammes). */
    object Shop : Screen()
    /** Ligue de la semaine entre amis. */
    object League : Screen()
    /** Galerie chronologique des preuves (photos et notes) d'un objectif. */
    class Gallery(val goalId: String) : Screen()
    /** Compagnon flamme : stade, humeur, accessoires. */
    object Pet : Screen()
    /** Quête en cours : chapitres de l'histoire. */
    object Quest : Screen()
}

/** Message éphémère en bas de l'écran, avec une action facultative (« Annuler »). */
class Toast(val id: Long, val text: String, val icon: String, val action: String? = null, val onAction: (() -> Unit)? = null)

/**
 * État de l'interface (équivalent de l'objet `UI` de la version web) et réactions aux demandes de la logique.
 * Toute lecture de `rev` dans l'interface la redessine quand la base change.
 */
class AppState(val app: PepsApp) : Hooks {
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
    /** Message prérempli pour la prochaine conversation ouverte (bravo pour une série…). */
    var chatDraft: String? = null
    var feedTab by mutableStateOf("friends")
    var modTab by mutableStateOf("accounts")
    var friendQuery by mutableStateOf("")
    /** Onglets mémorisés des pages Profil et Amis. */
    var profileTab by mutableStateOf("overview")
    var friendsTab by mutableStateOf("friends")
    var postLimit by mutableStateOf(20)
    /** Thème recalculé (réglages de l'appareil modifiés). */
    var themeTick by mutableLongStateOf(0L)
    /** Essai d'une ambiance de la boutique (quelques secondes, jamais enregistré). */
    var themePreview by mutableStateOf<String?>(null)
    /** Onglet ouvert de la boutique (icons, themes, modes, bgs). */
    var shopTab by mutableStateOf("icons")
    /** Effet visuel d'une surprise en cours (confettis, poissons…), relancé à chaque [eggTick]. */
    var eggFx by mutableStateOf<String?>(null)
    var eggTick by mutableIntStateOf(0)
    /** Dernier envoi réussi de modifications locales (animation « Synchronisé » sous le titre). */
    var syncedAt by mutableLongStateOf(0L)
    var syncedN by mutableIntStateOf(0)
    /** Passé hors ligne depuis le dernier envoi : le prochain envoi réussi est célébré (« Synchronisé »). */
    var wasOffline = false
    private var bumpJob: Job? = null

    val pg get() = app.pg
    val cloud get() = app.cloud
    val prefs get() = app.prefs
    val scope: CoroutineScope get() = app.scope

    fun bump() { rev++ }

    fun onlineChanged(online: Boolean) {
        if (!online) {
            wasOffline = true
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
    override fun toast(text: String, icon: String) = toast(text, icon, null, null)

    /** Message avec un bouton d'action : il reste 5 secondes à l'écran. */
    fun toast(text: String, icon: String, action: String?, onAction: (() -> Unit)?) {
        val t = Toast(nowMs() + toasts.size, text, icon, action, onAction)
        while (toasts.size >= 3) toasts.removeAt(0)
        toasts.add(t)
        scope.launch { delay(if (action != null) 5000 else if (prefs.easy) 5000 else 3600); toasts.remove(t) }
    }

    /** Message « … · Annuler » : l'action d'annulation retire le message et le remplace par sa confirmation. */
    fun undoable(text: String, icon: String, undo: () -> Unit) {
        toast(text, icon, "Annuler") { fr.pepsgoal.app.ui.Feedback.undo(); undo(); bump() }
    }

    override fun sparks(pts: Int) {
        fr.pepsgoal.app.ui.Feedback.success()
        if (prefs.motion || prefs.easy) { toast("+$pts flammes", "flame"); return }
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
        LiveUpdates.refresh(app); WidgetUpdater.update(app); fr.pepsgoal.app.sys.Duo.syncSoon(app); fr.pepsgoal.app.sys.ValidateTile.refreshAll(app)
        // Agenda : l'occurrence du jour est cochée dans le calendrier du téléphone (hors du fil principal)
        scope.launch(kotlinx.coroutines.Dispatchers.IO) { try { fr.pepsgoal.app.sys.Agenda.syncDone(app) } catch (e: Exception) {} }
    }

    override fun chained(prev: Goal, next: Goal) {
        if (app.foreground) toast("Ensuite : ${next.icon} ${next.title}", "repeat", "Valider") { fr.pepsgoal.app.ui.doValidate(this, next.id) }
        fr.pepsgoal.app.sys.Chain.schedule(app, prev.id, next.id)
    }

    /* ---------- navigation ---------- */
    fun open(s: Sheet) { sheet = s }
    fun close() { sheet = null }
    fun push(s: Screen) { sheet = null; screens.add(s) }
    fun nav(t: String) { if (t == tab && screens.isEmpty() && sheet == null) fr.pepsgoal.app.ui.TabReTap.n++; tab = t; sheet = null; screens.clear(); if (t in setOf("feed", "friends", "mod", "today")) cloud.scheduleSocial(50) }

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
