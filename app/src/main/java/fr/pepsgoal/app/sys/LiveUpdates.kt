package fr.pepsgoal.app.sys

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.pepsgoal.app.MainActivity
import fr.pepsgoal.app.R
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.minutes
import fr.pepsgoal.core.model.start
import fr.pepsgoal.core.model.end
import fr.pepsgoal.core.model.unit

/**
 * Activités en cours (Live Updates d'Android 16, affichées dans la barre d'état, l'écran de verrouillage et la Now Bar) :
 * minuteur d'un objectif de durée, période en cours et compte à rebours de fin de journée quand la série est en jeu.
 * Avant Android 16 : notifications permanentes avec barre de progression et chronomètre.
 */
object LiveUpdates {
    private const val ID_TIMER = 101; private const val ID_PERIOD = 102; private const val ID_STREAK = 103
    private val TYPE_COLORS = mapOf("duration" to 0xFF2EF2B0.toInt(), "period" to 0xFF7DD3FC.toInt(), "routine" to 0xFFB6FF2E.toInt(), "abstinence" to 0xFFA98BFF.toInt(),
        "objective" to 0xFF22D3FF.toInt(), "deadline" to 0xFFFF4FA3.toInt(), "monthly" to 0xFFFFD23F.toInt(), "limit" to 0xFFFF8A3D.toInt(), "measure" to 0xFFFF7AD9.toInt(),
        "project" to 0xFF4D9BFF.toInt(), "group" to 0xFF7CFF6B.toInt(), "duel" to 0xFFFF5A5A.toInt(), "challenge" to 0xFFFFB000.toInt(), "program" to 0xFF7C8CFF.toInt(), "timed" to 0xFFE3B98A.toInt())
    private const val FLAME = 0xFFFF4A12.toInt(); private const val TRACK = 0x33888899

    /** Mise en avant des activités (Android 16 et plus). */
    fun supported() = Build.VERSION.SDK_INT >= 36

    fun canPromote(ctx: Context): Boolean = if (supported()) ctx.getSystemService(NotificationManager::class.java).canPostPromotedNotifications() else true

    fun openPromotionSettings(ctx: Context) {
        val i = if (supported()) Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS) else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        ctx.startActivity(i.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private class Live(
        val id: Int, val which: String, val token: String, val title: String, val text: String, val short: String,
        val progress: Int, val segments: List<Pair<Int, Int>>, val chrono: Long?, val countDown: Boolean,
        val actions: List<Pair<String, PendingIntent>>, val tap: PendingIntent, val color: Int
    )

    fun refresh(ctx: Context) {
        val app = ctx.peps; val pg = app.pg; val nm = NotificationManagerCompat.from(ctx)
        val out = mutableListOf<Live>()
        val u = pg.me()
        if (u != null && app.prefs.liveUpdates && Notifier.allowed(ctx)) try { build(ctx, out) } catch (e: Exception) {}
        val dismissed = app.prefs.liveDismissed
        val shown = out.filter { "${it.which}|${it.token}" != dismissed }
        for (id in listOf(ID_TIMER, ID_PERIOD, ID_STREAK)) if (shown.none { it.id == id }) nm.cancel(id)
        shown.forEach { l -> try { nm.notify(l.id, if (supported()) promoted(ctx, l) else compat(ctx, l)) } catch (e: SecurityException) {} }
    }

    private fun build(ctx: Context, out: MutableList<Live>) {
        val pg = ctx.peps.pg; val u = pg.me()!!; val now = nowMs()
        // 1. Minuteur d'un objectif de durée
        pg.runningTimer()?.let { t ->
            val g = pg.goalById(t.goalId) ?: return@let
            val m = pg.durationLiveMins(g); val target = g.minutes
            val c = TYPE_COLORS.getValue("duration")
            out += Live(ID_TIMER, "timer", "${t.start}", "${g.icon} ${g.title}", "${fmtNum(Math.round(m).toDouble())} / ${fmtNum(target)} min aujourd’hui · minuteur en marche",
                "${m.toInt()} min", (m / target * 1000).toInt().coerceIn(0, 1000), listOf(1000 to c), now - (m * 60_000).toLong(), false,
                listOf("Pause" to Notifier.action(ctx, ID_TIMER, ActionReceiver.TIMER) { putExtra("goal", g.id) }),
                Notifier.open(ctx, ID_TIMER) { putExtra(MainActivity.EXTRA_GOAL, g.id) }, c)
        }
        // 2. Période en cours (créneau à tenir)
        val plan = pg.todayPlan(u.id)
        plan.due.firstOrNull { it.g.type == "period" && !it.done && pg.periodWindow(it.g).live }?.let { x ->
            val g = x.g; val s = startOfDayMs(todayDate()) + hmMinutes(g.start) * 60_000L; val e = startOfDayMs(todayDate()) + hmMinutes(g.end) * 60_000L
            val c = TYPE_COLORS.getValue("period")
            out += Live(ID_PERIOD, "period", g.id + today(), "${g.icon} ${g.title}", "En cours jusqu’à ${g.end} · valide quand c’est fait", g.end,
                ((now - s).toDouble() / maxOf(1L, e - s) * 1000).toInt().coerceIn(0, 1000), listOf(1000 to c), e, true,
                listOf("Valider" to Notifier.action(ctx, ID_PERIOD, ActionReceiver.VALIDATE) { putExtra("goal", g.id); putExtra("keep", true) }),
                Notifier.open(ctx, ID_PERIOD) { putExtra(MainActivity.EXTRA_GOAL, g.id) }, c)
        }
        // 3. Fin de journée : la série s'éteint à minuit
        val st = pg.globalStreak(u.id); val pending = plan.due.filter { !it.done }
        val from = if (u.settings.evening.on) minOf(u.settings.evening.time, "21:00") else "20:00"
        if (st.n > 0 && !st.lit && pending.isNotEmpty() && nowHM() >= from) {
            val midnight = startOfDayMs(todayDate().plusDays(1)); val left = midnight - now
            val total = plan.due.size; val per = 1000 / maxOf(1, total)
            val segs = plan.due.sortedByDescending { it.done }.map { per to (TYPE_COLORS[it.g.type] ?: FLAME) }
            val quick = pending.firstOrNull { it.g.type in setOf("routine", "abstinence", "challenge", "timed") }
            val acts = mutableListOf<Pair<String, PendingIntent>>()
            quick?.let { q -> acts += "✓ ${q.g.title.take(20)}" to Notifier.action(ctx, ID_STREAK, ActionReceiver.VALIDATE) { putExtra("goal", q.g.id); putExtra("keep", true) } }
            acts += "Plus tard" to Notifier.action(ctx, ID_STREAK + 1, ActionReceiver.DISMISS_LIVE) { putExtra("which", "streak"); putExtra("token", today()) }
            val h = left / 3_600_000; val mi = (left / 60_000) % 60
            out += Live(ID_STREAK, "streak", today(), "Ta série de ${pl(st.n, "jour", "jours")} s’éteint à minuit",
                "Encore ${pl(pending.size, "validation", "validations")} : ${pending.take(3).joinToString(", ") { it.g.icon + " " + it.g.title }}",
                if (h > 0) "${h} h" else "$mi min", (plan.due.count { it.done } * per).coerceIn(0, 1000), segs, midnight, true, acts,
                Notifier.open(ctx, ID_STREAK) { putExtra(MainActivity.EXTRA_TAB, "today") }, FLAME)
        }
    }

    /** Android 16 : notification mise en avant (Live Update) avec ProgressStyle. */
    private fun promoted(ctx: Context, l: Live): Notification {
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(l.segments.map { (len, c) -> Notification.ProgressStyle.Segment(len).setColor(c) })
            .setProgress(l.progress)
            .setProgressTrackerIcon(Icon.createWithResource(ctx, R.drawable.ic_notif))
        val b = Notification.Builder(ctx, Notifier.CH_LIVE)
            .setSmallIcon(R.drawable.ic_notif).setContentTitle(l.title).setContentText(l.text).setStyle(style)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(if (l.which == "timer") Notification.CATEGORY_STOPWATCH else Notification.CATEGORY_PROGRESS)
            .setContentIntent(l.tap).setColor(l.color).setShortCriticalText(l.short)
            .setDeleteIntent(Notifier.action(ctx, l.id + 50, ActionReceiver.DISMISS_LIVE) { putExtra("which", l.which); putExtra("token", l.token) })
        l.chrono?.let { b.setWhen(it).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(l.countDown) }
        l.actions.forEach { (t, pi) -> b.addAction(Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_notif), t, pi).build()) }
        b.extras.putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
        return b.build()
    }

    /** Android 8 à 15 : notification permanente avec progression et chronomètre. */
    private fun compat(ctx: Context, l: Live): Notification {
        val b = NotificationCompat.Builder(ctx, Notifier.CH_LIVE).setSmallIcon(R.drawable.ic_notif).setColor(l.color)
            .setContentTitle(l.title).setContentText(l.text).setStyle(NotificationCompat.BigTextStyle().bigText(l.text))
            .setOngoing(true).setOnlyAlertOnce(true).setProgress(1000, l.progress, false).setContentIntent(l.tap)
            .setCategory(if (l.which == "timer") NotificationCompat.CATEGORY_STOPWATCH else NotificationCompat.CATEGORY_PROGRESS)
            .setDeleteIntent(Notifier.action(ctx, l.id + 50, ActionReceiver.DISMISS_LIVE) { putExtra("which", l.which); putExtra("token", l.token) })
            .setSubText(l.short)
        l.chrono?.let { b.setWhen(it).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(l.countDown) }
        l.actions.forEach { (t, pi) -> b.addAction(R.drawable.ic_notif, t, pi) }
        return b.build()
    }
}
