package fr.pepsgoal.app.sys

import android.content.Context
import android.speech.tts.TextToSpeech
import fr.pepsgoal.app.peps
import fr.pepsgoal.app.ai.firstName
import fr.pepsgoal.core.*
import java.util.Locale

/** Lecture à voix haute (synthèse vocale du téléphone) : bilan du soir lu par le coach, briefing du matin. */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    var speaking = false; private set

    fun say(ctx: Context, text: String) {
        val t = text
        val e = tts
        if (e != null && ready) { speak(e, t); return }
        pending = t
        if (e == null) tts = TextToSpeech(ctx.applicationContext) { st ->
            ready = st == TextToSpeech.SUCCESS
            val x = tts ?: return@TextToSpeech
            if (ready) { x.language = if (Lang.en) Locale.ENGLISH else Locale.FRENCH; x.setSpeechRate(1.02f); pending?.let { speak(x, it) }; pending = null }
        }
    }

    private fun speak(e: TextToSpeech, t: String) { speaking = true; e.language = if (Lang.en) Locale.ENGLISH else Locale.FRENCH; e.speak(t, TextToSpeech.QUEUE_FLUSH, null, "pg-" + t.hashCode()) }

    fun stop() { tts?.stop(); speaking = false }

    /** Arrêt complet (libère le moteur de synthèse) : à l'arrière-plan de l'application. */
    fun shutdown() { tts?.shutdown(); tts = null; ready = false; speaking = false }

    /** Texte du bilan du soir : ce qui est fait, ce qu'il reste, la série et demain. */
    fun eveningText(ctx: Context): String {
        val pg = ctx.peps.pg; val u = pg.me() ?: return ""
        val plan = pg.todayPlan(u.id); val left = plan.due.filter { !it.done }; val done = plan.due.count { it.done }
        val st = pg.globalStreak(u.id)
        val name = firstName(ctx)?.let { " $it" } ?: ""
        val tomorrow = todayDate().plusDays(1)
        val next = pg.myGoals(u.id).filter { !pg.isClosedGoal(it) && pg.scheduled(it, tomorrow) }
        if (Lang.en) return buildString {
            append("Good evening$name. ")
            append(if (plan.due.isEmpty()) "Nothing was planned today. " else if (left.isEmpty()) "Perfect day: you completed everything, $done goal${if (done > 1) "s" else ""}. " else "You completed $done goal${if (done > 1) "s" else ""} out of ${plan.due.size}. ")
            if (left.isNotEmpty()) append("Still to do: ${left.take(4).joinToString(", ") { it.g.title }}. ")
            if (st.n > 1) append("Your streak is ${st.n} days. ")
            if (next.isNotEmpty()) append("Tomorrow: ${next.take(4).joinToString(", ") { it.title }}. ")
            append(if (left.isEmpty()) "Well done, rest well." else "One last effort, or note what stopped you. Have a nice evening.")
        }
        return buildString {
            append("Bonsoir$name. ")
            append(if (plan.due.isEmpty()) "Rien n’était prévu aujourd’hui. " else if (left.isEmpty()) "Journée parfaite : tu as tout validé, ${pl(done, "objectif", "objectifs")}. " else "Tu as validé $done objectif${if (done > 1) "s" else ""} sur ${plan.due.size}. ")
            if (left.isNotEmpty()) append("Il te reste : ${left.take(4).joinToString(", ") { it.g.title }}. ")
            if (st.n > 1) append("Ta série est de ${pl(st.n, "jour", "jours")}. ")
            if (next.isNotEmpty()) append("Demain : ${next.take(4).joinToString(", ") { it.title }}. ")
            append(if (left.isEmpty()) "Bravo, repose-toi bien." else "Encore un petit effort, ou note ce qui t’a empêché. Bonne soirée.")
        }
    }

    /** Texte du briefing du matin : chaque phrase traduite, ponctuée pour marquer une pause à l’oral. */
    fun briefingText(ctx: Context): String {
        val pg = ctx.peps.pg; val place = pg.me()?.settings?.weather
        val wx = place?.let { fr.pepsgoal.app.ui.Wx.cached(ctx, it)?.data }
        return pg.briefing(place?.name, wx).joinToString(" ") { fr.pepsgoal.app.ui.I18n.tr(it).let { t -> if (t.trimEnd().lastOrNull() in setOf('.', '!', '?')) t else "$t." } }
    }

    fun briefing(ctx: Context) = say(ctx, briefingText(ctx))
}
