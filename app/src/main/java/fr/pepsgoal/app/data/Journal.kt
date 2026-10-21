package fr.pepsgoal.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Une page du journal : humeur du jour (1 à 5) et quelques lignes. */
@Serializable
class JournalEntry(val date: String, var mood: Int, var text: String = "", var at: Long = 0)

/**
 * Journal et humeur du jour, gardés sur l'appareil (fichier local, non synchronisé). Les liens entre humeur et
 * validations sont calculés ici et repris par le coach et les récaps.
 */
object Journal {
    val MOODS = listOf("Difficile" to "😣", "Bof" to "🙁", "Correct" to "😐", "Bien" to "🙂", "Super" to "😄")
    private fun file(ctx: Context) = File(ctx.filesDir, "journal.json")
    private val json = Json { ignoreUnknownKeys = true }
    var rev by mutableStateOf(0)

    fun all(ctx: Context): List<JournalEntry> = try { json.decodeFromString(ListSerializer(JournalEntry.serializer()), file(ctx).readText()) } catch (e: Exception) { emptyList() }
    fun get(ctx: Context, date: String) = all(ctx).firstOrNull { it.date == date }
    fun recent(ctx: Context, n: Int) = all(ctx).sortedBy { it.date }.takeLast(n)

    fun put(ctx: Context, date: String, mood: Int, text: String) {
        val l = all(ctx).filter { it.date != date } + JournalEntry(date, mood.coerceIn(1, 5), text.take(1000), nowMs())
        file(ctx).writeText(json.encodeToString(ListSerializer(JournalEntry.serializer()), l.sortedBy { it.date })); rev++
    }
    fun remove(ctx: Context, date: String) { file(ctx).writeText(json.encodeToString(ListSerializer(JournalEntry.serializer()), all(ctx).filter { it.date != date })); rev++ }
    fun clear(ctx: Context) { file(ctx).delete(); rev++ }

    /** Taux de validation d'une journée (validations / objectifs prévus), d'après l'historique. */
    fun dayRate(ctx: Context, date: String): Double? {
        val pg = ctx.peps.pg; val u = pg.me() ?: return null
        val wins = pg.userCheckins(u.id).count { it.date == date && pg.isWin(it) }
        val goals = pg.myGoals(u.id).count { !it.archived && ymd(it.createdAt) <= date }
        return if (goals == 0) null else (wins.toDouble() / goals).coerceAtMost(1.0)
    }

    /** Observation simple : humeur moyenne les jours « réussis » (≥ 60 %) comparée aux autres jours. */
    fun insight(ctx: Context): String? {
        val l = all(ctx).takeLast(60); if (l.size < 5) return null
        val pairs = l.mapNotNull { e -> dayRate(ctx, e.date)?.let { e.mood to it } }
        val good = pairs.filter { it.second >= .6 }.map { it.first }; val bad = pairs.filter { it.second < .6 }.map { it.first }
        if (good.size < 2 || bad.size < 2) return null
        val g = good.average(); val b = bad.average()
        return when {
            g - b >= .6 -> "Ton humeur est nettement meilleure les jours où tu valides la majorité de tes objectifs (%.1f contre %.1f sur 5).".format(g, b)
            b - g >= .6 -> "Les jours chargés en validations ne sont pas forcément les plus joyeux : pense à garder du temps pour souffler.".format()
            else -> "Ton humeur reste stable quel que soit le nombre de validations : belle régularité."
        }
    }
}
