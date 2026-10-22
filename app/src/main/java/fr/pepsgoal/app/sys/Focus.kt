package fr.pepsgoal.app.sys

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.pepsgoal.app.MainActivity
import fr.pepsgoal.app.R
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.target

/**
 * Mode Focus (Pomodoro) : une session de concentration chronométrée, affichée en direct dans la barre d'état, avec
 * « Ne pas déranger » pendant la session et validation automatique de l'objectif lié à la fin.
 */
object Focus {
    const val NID = 31
    fun running(ctx: Context) = ctx.peps.prefs.focusEnd > System.currentTimeMillis()
    fun left(ctx: Context) = (ctx.peps.prefs.focusEnd - System.currentTimeMillis()).coerceAtLeast(0)

    private fun alarm(ctx: Context) = PendingIntent.getBroadcast(ctx, 31, Intent(ctx, FocusReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun canDnd(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    fun askDnd(ctx: Context) = ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun dnd(ctx: Context, on: Boolean) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) try { nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL) } catch (e: Exception) {}
    }

    fun start(ctx: Context, minutes: Int, goalId: String?) {
        val app = ctx.peps; val end = System.currentTimeMillis() + minutes * 60_000L
        app.prefs.focusEnd = end; app.prefs.focusGoal = goalId ?: ""
        // Objectif « durée » : son minuteur tourne pendant la session (la journée se valide toute seule)
        goalId?.let { app.pg.goalById(it) }?.takeIf { it.type == "duration" && app.pg.timerOf(it) == null }?.let { app.pg.toggleTimer(it) }
        if (app.prefs.focusDnd) dnd(ctx, true)
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (Reminders.canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, alarm(ctx)) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, alarm(ctx))
        notifyRunning(ctx)
        LiveUpdates.refresh(ctx); WidgetUpdater.update(ctx)
    }

    /** Arrête la session (fin normale : [completed] = true → validation de l'objectif lié). */
    fun stop(ctx: Context, completed: Boolean) {
        val app = ctx.peps; val gid = app.prefs.focusGoal.ifEmpty { null }
        app.prefs.focusEnd = 0; app.prefs.focusGoal = ""
        ctx.getSystemService(AlarmManager::class.java).cancel(alarm(ctx))
        NotificationManagerCompat.from(ctx).cancel(NID)
        dnd(ctx, false)
        val g = gid?.let { app.pg.goalById(it) }
        if (g != null && g.type == "duration") app.pg.timerOf(g)?.let { app.pg.toggleTimer(g) }
        if (completed) {
            val due = app.pg.todayPlan(app.pg.meId()).due.firstOrNull { it.g.id == gid }
            if (g != null && g.type != "duration" && due != null && !due.done) app.pg.validate(g.id)
            Notifier.simple(ctx, NID + 1, Notifier.CH_GOALS, "🎯 Session Focus terminée", if (g != null) "Bravo ! « ${g.title} » avance. Prends 5 minutes de pause." else "Bravo ! Prends 5 minutes de pause avant la suite.")
        }
        LiveUpdates.refresh(ctx); WidgetUpdater.update(ctx)
    }

    /** Notification en cours avec compte à rebours (mise en avant dans la barre d'état sur Android 16). */
    fun notifyRunning(ctx: Context) {
        val app = ctx.peps; val end = app.prefs.focusEnd; if (end <= System.currentTimeMillis()) return
        val g = app.prefs.focusGoal.ifEmpty { null }?.let { app.pg.goalById(it) }
        val open = PendingIntent.getActivity(ctx, NID, Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_FOCUS).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getBroadcast(ctx, NID + 2, Intent(ctx, FocusReceiver::class.java).setAction("stop"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, Notifier.CH_LIVE).setSmallIcon(R.drawable.ic_notif).setColor(0xFFFF4A12.toInt())
            .setContentTitle(if (g != null) "Focus · ${g.icon} ${g.title}" else "Session Focus").setContentText("Concentre-toi, PepsGoal s’occupe du reste.")
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(true).setWhen(end).setUsesChronometer(true).setChronometerCountDown(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH).setContentIntent(open).addAction(R.drawable.ic_notif, "Arrêter", stop)
            .apply { extras.putBoolean("android.requestPromotedOngoing", true) }
            .build()
        try { NotificationManagerCompat.from(ctx).notify(NID, n) } catch (e: SecurityException) {}
    }
}

/** Fin de la session Focus (alarme) ou bouton « Arrêter » de la notification. */
class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == "stop") Focus.stop(ctx, false)
        else if (ctx.peps.prefs.focusEnd != 0L) Focus.stop(ctx, true)
        ctx.peps.ui.bump()
    }
}

/** Tuile « Valider » : valide d'un geste le prochain objectif du jour (ou ouvre l'application si une saisie est nécessaire). */
class ValidateTile : TileService() {
    override fun onStartListening() { refresh() }

    private fun refresh() {
        val t = qsTile ?: return
        val pg = peps.pg; val u = pg.me()
        val plan = u?.let { pg.todayPlan(it.id) }
        val next = plan?.let { nextOf(it) }
        t.state = if (next != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = if (plan == null) "PepsGoal" else "${plan.due.count { it.done }}/${plan.due.size} validés"
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = next?.let { "${it.g.icon} ${it.g.title}" } ?: "Tout est fait"
        t.updateTile()
    }

    override fun onClick() {
        val pg = peps.pg; val u = pg.me() ?: return openApp()
        val next = nextOf(pg.todayPlan(u.id)) ?: return openApp()
        when (pg.validate(next.g.id)) {
            is Pg.VResult.Done -> { LiveUpdates.refresh(this); WidgetUpdater.update(this) }
            else -> openApp(next.g.id)
        }
        refresh()
    }

    /** Objectif suivant, en priorité ceux qui se valident d'un geste (sans quantité ni sous-tâches). */
    private fun nextOf(plan: fr.pepsgoal.core.Plan): fr.pepsgoal.core.DueItem? {
        val todo = plan.due.filter { !it.done }
        return todo.firstOrNull { it.g.type in ONE_TAP && it.g.steps.isEmpty() } ?: todo.firstOrNull()
    }

    private fun openApp(goal: String? = null) {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply { goal?.let { putExtra(MainActivity.EXTRA_PROGRESS, it) } }
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 41, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }

    companion object {
        val ONE_TAP = setOf("routine", "abstinence", "timed", "period", "challenge", "limit")
        /** Rafraîchit les tuiles après une validation faite ailleurs. */
        fun refreshAll(ctx: android.content.Context) {
            for (c in listOf(ValidateTile::class.java, CapTile::class.java)) try { TileService.requestListeningState(ctx, android.content.ComponentName(ctx, c)) } catch (e: Exception) {}
        }
    }
}

/** Tuile « Cap quotidien » : ajoute la quantité la plus courante au cap du jour (pas, verres d'eau…). */
class CapTile : TileService() {
    override fun onStartListening() { refresh() }

    private fun cap(): fr.pepsgoal.core.model.Goal? {
        val pg = peps.pg; val u = pg.me() ?: return null
        val caps = pg.myGoals(u.id).filter { it.type == "cap" && !pg.isClosedGoal(it) && it.userId == u.id }
        return caps.firstOrNull { pg.isFav(it) && pg.capDay(it) < it.target } ?: caps.firstOrNull { pg.capDay(it) < it.target } ?: caps.firstOrNull()
    }

    private fun refresh() {
        val t = qsTile ?: return; val pg = peps.pg; val g = cap()
        t.state = if (g != null && pg.capDay(g) < g.target) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = g?.let { "${it.icon} ${fmtNum(pg.capDay(it))}/${fmtNum(it.target)}" } ?: "Cap quotidien"
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = g?.let { "+${fmtNum(pg.capQuick(it).firstOrNull() ?: 1.0)}${pg.unitSfx(it)}" } ?: "Aucun cap"
        t.updateTile()
    }

    override fun onClick() {
        val pg = peps.pg; val g = cap()
        if (g == null) {
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 42, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            else @Suppress("DEPRECATION") startActivityAndCollapse(i)
            return
        }
        pg.capAdd(g, pg.capQuick(g).firstOrNull() ?: 1.0, silent = true)
        LiveUpdates.refresh(this); WidgetUpdater.update(this); refresh()
    }
}

/** Tuile « Focus » : lance une session de 25 minutes, ou l'arrête. */
class FocusTile : TileService() {
    override fun onStartListening() { refresh() }
    private fun refresh() {
        val t = qsTile ?: return
        val on = Focus.running(this)
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Focus"
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = if (on) "Encore ${Focus.left(this) / 60_000 + 1} min" else "25 minutes"
        t.updateTile()
    }
    override fun onClick() {
        if (Focus.running(this)) Focus.stop(this, false) else Focus.start(this, 25, null)
        refresh()
    }
}
