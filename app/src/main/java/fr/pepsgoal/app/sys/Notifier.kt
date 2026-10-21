package fr.pepsgoal.app.sys

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import fr.pepsgoal.app.MainActivity
import fr.pepsgoal.app.PepsApp
import fr.pepsgoal.app.R
import fr.pepsgoal.app.peps
import fr.pepsgoal.app.ui.Social
import fr.pepsgoal.app.ui.screens.ChatState
import fr.pepsgoal.core.ACCENTS
import fr.pepsgoal.core.cloud.Msg
import fr.pepsgoal.core.cloud.str
import fr.pepsgoal.core.first
import fr.pepsgoal.core.model.Message
import fr.pepsgoal.core.model.Notif
import fr.pepsgoal.core.model.User

/**
 * Notifications natives : canaux par catégorie, actions directes (valider, confirmer, répondre, marquer comme lu),
 * conversations (MessagingStyle, raccourcis de conversation, bulles de la section « Conversations » d'Android).
 */
object Notifier {
    const val CH_REMIND = "reminders"; const val CH_GOALS = "goals"; const val CH_SOCIAL = "social"; const val CH_MSG = "messages"; const val CH_LIVE = "live"; const val CH_OFFICIAL = "official"
    const val KEY_REPLY = "reply"
    private const val GROUP_MSG = "pg.messages"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannelGroup(NotificationChannelGroup("goals", "Objectifs"))
        nm.createNotificationChannelGroup(NotificationChannelGroup("social", "Social"))
        listOf(
            Triple(CH_REMIND, "Rappels", NotificationManager.IMPORTANCE_HIGH) to ("goals" to "Rappels du matin et du soir, début des périodes, deadlines"),
            Triple(CH_LIVE, "Activités en cours", NotificationManager.IMPORTANCE_DEFAULT) to ("goals" to "Minuteur, période en cours et fin de journée en direct (Live Updates)"),
            Triple(CH_GOALS, "Progrès et badges", NotificationManager.IMPORTANCE_DEFAULT) to ("goals" to "Paliers, badges, gels de série, binôme"),
            Triple(CH_MSG, "Messages", NotificationManager.IMPORTANCE_HIGH) to ("social" to "Messages privés et groupes (chiffrés de bout en bout)"),
            Triple(CH_SOCIAL, "Amis et feed", NotificationManager.IMPORTANCE_DEFAULT) to ("social" to "Demandes d’amis, encouragements, commentaires, stories"),
            Triple(CH_OFFICIAL, "PepsGoal Officiel", NotificationManager.IMPORTANCE_DEFAULT) to ("social" to "Nouveautés et messages de la modération")
        ).forEach { (c, gd) ->
            val ch = NotificationChannel(c.first, c.second, c.third).apply { group = gd.first; description = gd.second }
            if (c.first == CH_LIVE) { ch.setSound(null, null); ch.enableVibration(false) }
            nm.createNotificationChannel(ch)
        }
    }

    fun allowed(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun openSettings(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Ouvre l'application sur une vue donnée (onglet, objectif, conversation). */
    fun open(ctx: Context, req: Int, configure: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getActivity(ctx, req, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).apply(configure), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun action(ctx: Context, req: Int, act: String, mutable: Boolean = false, configure: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(ctx, req, Intent(ctx, ActionReceiver::class.java).setAction(act).apply(configure),
            PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)

    private fun post(ctx: Context, id: Int, n: android.app.Notification, tag: String? = null) {
        if (!allowed(ctx)) return
        try { NotificationManagerCompat.from(ctx).notify(tag, id, n) } catch (e: SecurityException) {}
    }

    private fun base(ctx: Context, ch: String) = NotificationCompat.Builder(ctx, ch).setSmallIcon(R.drawable.ic_notif).setColor(0xFFFF4A12.toInt()).setAutoCancel(true)

    fun test(ctx: Context, text: String) {
        post(ctx, 9, base(ctx, CH_REMIND).setContentTitle("PepsGoal").setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(ctx, 9) { putExtra(MainActivity.EXTRA_TAB, "today") }).setCategory(NotificationCompat.CATEGORY_REMINDER).build())
    }

    /** Notification née sur l'appareil (rappel, période, badge…), quand l'application n'est pas à l'écran. */
    fun local(app: PepsApp, n: Notif) = show(app, n)

    /** Notification du serveur (ami, encouragement, binôme…) reçue pendant que l'application est en arrière-plan. */
    fun server(app: PepsApp, n: Notif) {
        val seen = app.prefs.notifiedIds; if (n.id in seen) return
        app.prefs.notifiedIds = seen + n.id
        show(app, n)
    }

    private fun show(app: PepsApp, n: Notif) {
        val pg = app.pg
        val ch = when (n.type) { "reminder", "period", "deadline" -> CH_REMIND; "official", "mod" -> CH_OFFICIAL; "friend", "like", "comment", "story", "mention", "group" -> CH_SOCIAL; else -> CH_GOALS }
        val id = n.id.hashCode()
        val title = when (n.type) {
            "reminder" -> "Rappel"; "period" -> "C’est l’heure"; "deadline" -> "Deadline"; "badge" -> "Bravo !"; "freeze" -> "Gel de série"; "verify" -> "Binôme"
            "friend" -> "Amis"; "like" -> "Encouragement"; "comment" -> "Commentaire"; "story" -> "Story"; "official" -> "PepsGoal Officiel"; "mod" -> "Modération"; else -> "PepsGoal"
        }
        val b = base(app, ch).setContentTitle(title).setContentText(n.text).setStyle(NotificationCompat.BigTextStyle().bigText(n.text)).setWhen(n.at).setShowWhen(true)
            .setCategory(when (ch) { CH_REMIND -> NotificationCompat.CATEGORY_REMINDER; CH_SOCIAL -> NotificationCompat.CATEGORY_SOCIAL; else -> NotificationCompat.CATEGORY_STATUS })
            .setContentIntent(open(app, id) { putExtra(MainActivity.EXTRA_NOTIF, n.id); n.goalId?.let { putExtra(MainActivity.EXTRA_GOAL, it) }; putExtra(MainActivity.EXTRA_TAB, n.view ?: if (ch == CH_SOCIAL) "feed" else "today") })
        if (ch == CH_REMIND) b.setGroup("pg.reminders")
        // Actions directes
        val g = n.goalId?.let { pg.goalById(it) }
        if (g != null && n.type in setOf("period", "reminder", "deadline") && !pg.isClosedGoal(g) && g.type in setOf("routine", "abstinence", "timed", "period", "challenge", "limit", "deadline"))
            b.addAction(R.drawable.ic_notif, if (g.type == "deadline") "Terminé" else "Valider", action(app, id, ActionReceiver.VALIDATE) { putExtra("goal", g.id); putExtra("nid", id) })
        if (n.type == "reminder" && g == null) {
            val due = pg.todayPlan().due.filter { !it.done && it.g.type in setOf("routine", "abstinence", "challenge", "period", "timed") }
            due.take(2).forEachIndexed { i, x -> b.addAction(R.drawable.ic_notif, "✓ ${x.g.title.take(18)}", action(app, id * 10 + i, ActionReceiver.VALIDATE) { putExtra("goal", x.g.id); putExtra("nid", id); putExtra("keep", true) }) }
        }
        n.checkinId?.let { cid ->
            b.addAction(R.drawable.ic_notif, "Confirmer", action(app, id + 1, ActionReceiver.CONFIRM) { putExtra("checkin", cid); putExtra("ok", true); putExtra("nid", id) })
            b.addAction(R.drawable.ic_notif, "Refuser", action(app, id + 2, ActionReceiver.CONFIRM) { putExtra("checkin", cid); putExtra("ok", false); putExtra("nid", id) })
        }
        post(app, id, b.build())
    }

    fun clearReminders(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.activeNotifications.filter { it.notification.group == "pg.reminders" || it.id == 9 }.forEach { nm.cancel(it.tag, it.id) }
    }

    /* ------------------------------------------------------------------ conversations */
    private fun convKey(me: String, m: Message) = if (m.chat != null) "g:${m.chat}" else "u:${if (m.from == me) m.to else m.from}"

    private fun avatarIcon(u: User?): IconCompat {
        val s = 96; val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
        val col = (ACCENTS.getOrNull(u?.avatar?.color ?: 0) ?: ACCENTS[0]).c[1]
        c.drawCircle(s / 2f, s / 2f, s / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor(col) })
        val ini = (u?.name ?: "?").trim().split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10102A.toInt(); textSize = 40f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        c.drawText(ini, s / 2f, s / 2f - (p.descent() + p.ascent()) / 2, p)
        return IconCompat.createWithBitmap(bmp)
    }

    private fun person(app: PepsApp, id: String): Person {
        val u = app.pg.userById(id)
        return Person.Builder().setKey(id).setName(if (id == app.pg.meId()) "Toi" else u?.name ?: "PepsGoal").setIcon(avatarIcon(u)).build()
    }

    /** Raccourci de conversation : rend la notification éligible à la section « Conversations » et aux bulles. */
    private fun shortcut(app: PepsApp, key: String, title: String, icon: IconCompat, persons: List<Person>) {
        val sc = ShortcutInfoCompat.Builder(app, key).setShortLabel(title.take(24)).setLongLabel(title).setIcon(icon).setLongLived(true).setPersons(persons.toTypedArray())
            .setLocusId(LocusIdCompat(key)).setCategories(setOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
            .setIntent(Intent(app, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(MainActivity.EXTRA_CHAT, key)).build()
        try { ShortcutManagerCompat.pushDynamicShortcut(app, sc) } catch (e: Exception) {}
    }

    /** Nouveaux messages reçus : une notification par conversation, avec réponse directe. */
    fun messages(app: PepsApp, fresh: List<Message>) {
        val pg = app.pg; val me = pg.meId()
        val list = fresh.filter { it.from != me && !Social.hiddenFrom(app.ui, it) && Social.body(app.ui, it)?.str("k") !in Msg.SILENT }
        for ((key, ms) in list.groupBy { convKey(me, it) }) {
            if (app.foreground && ChatState.key == key) continue
            conversation(app, key, extra = ms)
        }
    }

    /** (Re)publie la notification d'une conversation avec ses messages non lus (et la réponse envoyée, le cas échéant). */
    fun conversation(app: PepsApp, key: String, extra: List<Message> = emptyList(), sent: String? = null) {
        val pg = app.pg; val me = pg.meId(); val ui = app.ui
        val group = if (key.startsWith("g:")) pg.db.chats.firstOrNull { it.id == key.drop(2) } else null
        val peer = if (key.startsWith("u:")) key.drop(2) else null
        val all = (if (group != null) Social.groupConvo(ui, group.id) else peer?.let { Social.convo(ui, me, it) } ?: emptyList())
        val unread = (all.filter { Social.isUnread(ui, it) } + extra).distinctBy { it.id }.sortedBy { it.at }.takeLast(8)
        if (unread.isEmpty() && sent == null) return
        val title = group?.let { "${it.emoji} ${it.name}" } ?: pg.userById(peer)?.name ?: "PepsGoal"
        val meP = person(app, me)
        val style = NotificationCompat.MessagingStyle(meP).setGroupConversation(group != null)
        if (group != null) style.setConversationTitle(title)
        unread.forEach { m -> style.addMessage(Social.preview(ui, m).ifEmpty { "🔒 Nouveau message" }, m.at, person(app, m.from)) }
        sent?.let { style.addMessage(it, System.currentTimeMillis(), meP) }
        val id = key.hashCode()
        val icon = if (group != null) IconCompat.createWithResource(app, R.mipmap.ic_launcher) else avatarIcon(pg.userById(peer))
        shortcut(app, key, title, icon, unread.map { person(app, it.from) }.distinctBy { it.key })
        val reply = RemoteInput.Builder(KEY_REPLY).setLabel("Répondre à ${if (group != null) group.name else first(title)}").build()
        val b = base(app, CH_MSG).setStyle(style).setShortcutId(key).setLocusId(LocusIdCompat(key)).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_MSG).setOnlyAlertOnce(sent != null)
            .setContentIntent(open(app, id) { putExtra(MainActivity.EXTRA_CHAT, key) })
        val official = peer != null && pg.isOfficial(pg.userById(peer))
        if (!official) b.addAction(NotificationCompat.Action.Builder(R.drawable.ic_notif, "Répondre", action(app, id, ActionReceiver.REPLY, mutable = true) { putExtra("key", key) })
            .addRemoteInput(reply).setAllowGeneratedReplies(true).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).setShowsUserInterface(false).build())
        b.addAction(NotificationCompat.Action.Builder(R.drawable.ic_notif, "Marquer comme lu", action(app, id + 1, ActionReceiver.READ) { putExtra("key", key) })
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build())
        post(app, id, b.build())
        // Résumé du groupe (plusieurs conversations)
        post(app, 7, base(app, CH_MSG).setGroup(GROUP_MSG).setGroupSummary(true).setContentTitle("Messages").setStyle(NotificationCompat.InboxStyle()).build())
    }

    fun clearChat(ctx: Context, key: String) {
        NotificationManagerCompat.from(ctx).cancel(key.hashCode())
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.activeNotifications.none { it.notification.group == GROUP_MSG && it.id != 7 }) nm.cancel(7)
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)
}

