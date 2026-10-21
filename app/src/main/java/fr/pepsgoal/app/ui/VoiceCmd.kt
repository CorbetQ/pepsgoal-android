package fr.pepsgoal.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Fs
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ai.Coach
import fr.pepsgoal.app.data.Journal
import fr.pepsgoal.core.model.Goal
import fr.pepsgoal.core.pl
import fr.pepsgoal.core.today
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

/**
 * Bouton micro : la reconnaissance vocale d'Android (sur l'appareil quand c'est possible) transcrit la phrase,
 * puis [VoiceCmd] la comprend. [onText] remplace l'interprétation (dictée dans un champ de recherche).
 */
@Composable
fun rememberVoiceCommand(ui: AppState, onText: ((String) -> Unit)? = null): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !t.isNullOrBlank()) { if (onText != null) onText(t) else VoiceCmd.run(ui, ctx, t) }
    }
    return {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, if (onText != null) "Que cherches-tu ?" else "Par exemple : « valide sport », « combien de jours de série »")
        try { launcher.launch(i) } catch (e: ActivityNotFoundException) { ui.toast("Aucun service de reconnaissance vocale sur ce téléphone.", "x") }
    }
}

/** Commandes vocales : valider, annuler, créer un objectif, série, reste du jour, Focus, humeur, navigation. */
object VoiceCmd {
    /** Exemples affichés dans l'aide et la recherche. */
    val EXAMPLES = listOf("Valide sport", "Annule lecture", "Ajoute un objectif boire 2 litres d’eau", "Combien de jours de série ?", "Qu’est-ce qu’il me reste ?", "Focus 25 minutes", "Je me sens bien", "Ouvre les stats")

    fun norm(s: String) = Normalizer.normalize(s.lowercase(Locale.FRENCH), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace('’', '\'').trim()

    fun run(ui: AppState, ctx: Context, raw: String) {
        val pg = ui.pg; val u = pg.me() ?: return
        val t = norm(raw)
        val goals = pg.myGoals(u.id).filter { !pg.isClosedGoal(it) && !it.archived }
        val plan = pg.todayPlan(u.id)
        fun doneToday(g: Goal) = plan.due.firstOrNull { it.g.id == g.id }?.done ?: (pg.todayDone(g) != null)
        fun after(re: String) = Regex(re).find(t)?.let { t.substring(it.range.last + 1).trim() } ?: t
        when {
            Regex("^(annule|annuler|defais|decoche|enleve)").containsMatchIn(t) -> {
                val g = find(goals, after("^(annule|annuler|defais|decoche|enleve)( la validation)?( de| du| d')?")) ?: return reply(ui, ctx, "Je n’ai pas trouvé cet objectif.", false)
                if (!doneToday(g)) reply(ui, ctx, "« ${g.title} » n’est pas validé aujourd’hui.", false) else doValidate(ui, g.id)
            }
            Regex("^(valide|valider|coche|cocher|j'ai (fait|termine|fini)|c'est fait|fait )").containsMatchIn(t) -> {
                val g = find(goals, after("^(valide|valider|coche|cocher|j'ai (fait|termine|fini)|c'est fait|fait )( l'objectif| mon| ma| mes| le| la| les| du| de la| des)?")) ?: return reply(ui, ctx, "Je n’ai pas trouvé cet objectif. Dis par exemple « valide sport ».", false)
                if (doneToday(g)) reply(ui, ctx, "« ${g.title} » est déjà validé aujourd’hui.", false) else doValidate(ui, g.id)
            }
            Regex("^(ajoute|ajouter|cree|creer|nouvel objectif|nouveau)").containsMatchIn(t) -> {
                val req = raw.substringAfter(" ", "").replace(Regex("^(un |une )?(nouvel |nouveau )?(objectif|routine)?\\s*:?\\s*", RegexOption.IGNORE_CASE), "").trim()
                if (req.length < 3) { ui.open(Sheet.GoalForm()); return }
                ui.toast("Je prépare l’objectif…", "sparkle")
                ui.scope.launch {
                    val d = try { Coach.createGoal(ctx, req) } catch (e: Exception) { Coach.rules(req) }
                    ui.open(Sheet.GoalForm(preType = d.type, preTitle = d.title, preIcon = d.icon, preFields = d.fields))
                }
            }
            Regex("serie|combien de jours|ma flamme").containsMatchIn(t) -> {
                val st = pg.globalStreak(u.id)
                reply(ui, ctx, if (st.n == 0) "Pas encore de série : valide un objectif pour allumer ta flamme." else "Ta série est de ${pl(st.n, "jour", "jours")}. ${if (st.lit) "Tu as déjà validé aujourd’hui, bravo !" else "Valide un objectif avant minuit pour la garder."}")
            }
            Regex("reste|a faire|a valider|mon programme|ma journee").containsMatchIn(t) -> {
                val left = plan.due.filter { !it.done }.map { it.g.title }
                reply(ui, ctx, if (left.isEmpty()) "Tout est validé pour aujourd’hui. Bravo !" else "Il te reste ${pl(left.size, "objectif", "objectifs")} : ${left.take(5).joinToString(", ")}.")
            }
            Regex("focus|concentr").containsMatchIn(t) -> {
                val g = find(goals, after("focus|concentr\\w*").replace(Regex("\\d+\\s*(minutes?|min)?"), ""))
                ui.close(); ui.fs = Fs.Focus(g?.id)
            }
            Regex("humeur|je me sens|je suis (de bonne|de mauvaise)|ca va").containsMatchIn(t) -> {
                val m = when {
                    Regex("super|genial|excellent|top|tres bien|au top|bonne humeur|parfait").containsMatchIn(t) -> 5
                    Regex("pas (tres )?bien|mal|triste|fatigue|mauvaise").containsMatchIn(t) -> 2
                    Regex("horrible|terrible|nul|epuise|deprime").containsMatchIn(t) -> 1
                    Regex("bien|content|heureux|ca va").containsMatchIn(t) -> 4
                    else -> 3
                }
                Journal.put(ctx, today(), m, Journal.get(ctx, today())?.text ?: "")
                reply(ui, ctx, "Humeur notée : ${Journal.MOODS[m - 1].first.lowercase()}.", false); ui.bump()
            }
            Regex("stat").containsMatchIn(t) -> ui.push(Screen.Stats)
            Regex("journal").containsMatchIn(t) -> ui.push(Screen.Journal)
            Regex("coach|assistant").containsMatchIn(t) -> ui.push(Screen.CoachChat)
            Regex("parametre|reglage").containsMatchIn(t) -> ui.push(Screen.Settings())
            Regex("objectifs").containsMatchIn(t) -> ui.nav("goals")
            Regex("amis|message").containsMatchIn(t) -> ui.nav("friends")
            Regex("profil|badge").containsMatchIn(t) -> ui.nav("profile")
            Regex("feed|actu|publication").containsMatchIn(t) -> ui.nav("feed")
            Regex("accueil|aujourd").containsMatchIn(t) -> ui.nav("today")
            else -> {
                // Un titre d'objectif seul : le valider ; sinon chercher la phrase partout
                val g = find(goals, t)
                if (g != null && !doneToday(g)) doValidate(ui, g.id) else ui.push(Screen.Search(raw))
            }
        }
    }

    /** Objectif dont le titre ressemble le plus aux mots prononcés (mots de 3 lettres et plus, début de mot). */
    fun find(goals: List<Goal>, q: String): Goal? {
        val words = norm(q).split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in STOP }
        if (words.isEmpty()) return null
        return goals.map { g ->
            val tw = norm(g.title).split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
            g to words.sumOf { w -> tw.maxOfOrNull { x -> if (x == w) 3.0 else if (x.startsWith(w.take(4)) || w.startsWith(x.take(4))) 2.0 else if (x.contains(w)) 1.0 else 0.0 } ?: 0.0 }
        }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }

    private val STOP = setOf("les", "des", "une", "mon", "mes", "son", "pour", "avec", "dans", "objectif", "aujourd", "hui", "minutes", "minute")

    private var tts: TextToSpeech? = null
    private var ready = false

    /** Réponse : message à l'écran et, pour les questions, lue à voix haute. */
    private fun reply(ui: AppState, ctx: Context, text: String, speak: Boolean = true) {
        ui.toast(text, "mic")
        if (!speak) return
        val app = ctx.applicationContext
        val say = { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pepsgoal") }
        if (tts != null && ready) { say(); return }
        if (tts == null) tts = TextToSpeech(app) { st -> if (st == TextToSpeech.SUCCESS) { ready = true; tts?.language = Locale.FRANCE; tts?.setSpeechRate(ui.prefs.voiceRate); say() } }
    }
}
