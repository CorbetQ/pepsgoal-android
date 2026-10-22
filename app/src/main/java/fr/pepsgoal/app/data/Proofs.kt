package fr.pepsgoal.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.pepsgoal.core.nowMs
import fr.pepsgoal.core.uid
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Preuve d'une validation : une note et/ou une photo, rattachées à un objectif et à un jour. */
@Serializable
class Proof(val id: String, val goalId: String, val date: String, var note: String = "", var photo: String? = null, val at: Long = 0)

/**
 * Preuves des validations, gardées uniquement sur l'appareil (photos dans le stockage privé de l'application) :
 * elles forment la galerie de la fiche d'un objectif.
 */
object Proofs {
    private fun file(ctx: Context) = File(ctx.filesDir, "proofs.json")
    private fun dir(ctx: Context) = File(ctx.filesDir, "proofs").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }
    var rev by mutableStateOf(0)

    fun all(ctx: Context): List<Proof> = try { json.decodeFromString(ListSerializer(Proof.serializer()), file(ctx).readText()) } catch (e: Exception) { emptyList() }
    fun of(ctx: Context, goalId: String) = all(ctx).filter { it.goalId == goalId }.sortedByDescending { it.date }
    fun get(ctx: Context, goalId: String, date: String) = all(ctx).firstOrNull { it.goalId == goalId && it.date == date }
    fun photoFile(ctx: Context, p: Proof): File? = p.photo?.let { File(dir(ctx), it) }?.takeIf { it.exists() }
    private fun save(ctx: Context, l: List<Proof>) { file(ctx).writeText(json.encodeToString(ListSerializer(Proof.serializer()), l)); rev++ }

    /** Enregistre (ou remplace) la preuve du jour ; la photo choisie est réduite et copiée dans l'application. */
    fun put(ctx: Context, goalId: String, date: String, note: String, photo: Uri?, keepPhoto: Boolean = true) {
        val old = get(ctx, goalId, date)
        var name = if (keepPhoto) old?.photo else null
        if (old?.photo != null && (photo != null || !keepPhoto)) File(dir(ctx), old.photo!!).delete()
        if (photo != null) name = copy(ctx, photo)
        if (note.isBlank() && name == null) { remove(ctx, goalId, date); return }
        save(ctx, all(ctx).filter { !(it.goalId == goalId && it.date == date) } + Proof(old?.id ?: uid(), goalId, date, note.trim().take(500), name, nowMs()))
    }
    fun remove(ctx: Context, goalId: String, date: String) {
        val l = all(ctx); l.filter { it.goalId == goalId && it.date == date }.forEach { p -> p.photo?.let { File(dir(ctx), it).delete() } }
        save(ctx, l.filter { !(it.goalId == goalId && it.date == date) })
    }
    /** Objectif supprimé : ses preuves partent avec lui. */
    fun removeGoal(ctx: Context, goalId: String) {
        val l = all(ctx); if (l.none { it.goalId == goalId }) return
        l.filter { it.goalId == goalId }.forEach { p -> p.photo?.let { File(dir(ctx), it).delete() } }
        save(ctx, l.filter { it.goalId != goalId })
    }

    private fun copy(ctx: Context, uri: Uri): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        bmp?.let { b ->
            val name = "${uid()}.jpg"
            File(dir(ctx), name).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            name
        }
    } catch (e: Exception) { null }
}
