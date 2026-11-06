package fr.pepsgoal.app.sys

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ui.Media
import fr.pepsgoal.app.ui.bgFile
import fr.pepsgoal.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Packs de thème personnels (9.2) : gardés sur l'appareil, dans filesDir/packs (library.json et une image par pack
 * qui en a une). Le format est celui de core (PackFormat) ; l'export JSON permet de les partager.
 */
object Packs {
    private fun dir(ctx: Context) = File(ctx.filesDir, "packs").apply { mkdirs() }
    private fun libraryFile(ctx: Context) = AtomicFile(File(dir(ctx), "library.json"))

    /** Image personnelle d'un pack (fond d'écran « image »). */
    fun imageFile(ctx: Context, id: String) = File(dir(ctx), "$id.jpg")

    /** Lecture au démarrage (petit fichier, avant le premier rendu : le thème « m:@<id> » se résout tout de suite). */
    fun load(ctx: Context) {
        PackLibrary.personal = try {
            libraryFile(ctx).let { f -> if (f.baseFile.exists()) PackFormat.decodeList(String(f.readFully(), Charsets.UTF_8)) else emptyList() }
        } catch (e: Exception) { emptyList() }
    }

    private fun write(ctx: Context, list: List<ThemePackSpec>) {
        val f = libraryFile(ctx)
        val out = f.startWrite()
        try { out.write(PackFormat.encodeList(list).toByteArray(Charsets.UTF_8)); f.finishWrite(out) } catch (e: Exception) { f.failWrite(out); throw e }
        PackLibrary.personal = list
    }

    fun all(): List<ThemePackSpec> = PackLibrary.personal.sortedByDescending { it.updatedAt }

    /** Enregistre (création ou modification). Renvoie un message d'erreur, ou null. */
    fun save(ui: AppState, spec: ThemePackSpec): String? {
        val p = PackFormat.normalize(spec.copy(updatedAt = nowMs(), createdAt = if (spec.createdAt > 0) spec.createdAt else nowMs()))
        PackFormat.validate(p).firstOrNull()?.let { return it }
        if (PackLibrary.personal.none { it.id == p.id } && PackLibrary.personal.size >= MAX) return "Tu as déjà $MAX packs : supprimes-en un pour en créer un autre."
        return try {
            write(ui.app, PackLibrary.personal.filterNot { it.id == p.id } + p)
            ui.themeTick++; ui.bump(); null
        } catch (e: Exception) { "Enregistrement impossible : vérifie l’espace disponible." }
    }

    /** Copie sous un nouvel identifiant (image comprise). */
    fun duplicate(ui: AppState, spec: ThemePackSpec): ThemePackSpec? {
        val copy = spec.duplicate()
        val img = imageFile(ui.app, spec.id)
        if (img.exists()) try { img.copyTo(imageFile(ui.app, copy.id), overwrite = true) } catch (e: Exception) {}
        return if (save(ui, copy) == null) copy else null
    }

    /** Supprime le pack ; s'il était appliqué, l'interface revient au thème de base équivalent. */
    fun delete(ui: AppState, id: String) {
        val spec = PackLibrary.personal.firstOrNull { it.id == id } ?: return
        val u = ui.pg.me()
        if (u != null && u.settings.theme == spec.themeSetting()) { u.settings.theme = if (spec.dark) "dark" else "light"; ui.pg.store.commit() }
        try { write(ui.app, PackLibrary.personal.filterNot { it.id == id }) } catch (e: Exception) {}
        imageFile(ui.app, id).delete()
        ui.themeTick++; ui.bump()
    }

    /** Importe un pack collé ou lu dans un fichier : toujours une nouvelle copie, jamais un écrasement. */
    fun import(ui: AppState, text: String): Pair<ThemePackSpec?, String?> = try {
        val p = PackFormat.parse(text)
        val copy = p.duplicate(suffix = "").copy(wallpaper = if (p.wallpaper.kind == "image") PackWallpaper("gradient", colors = listOf(p.colors.deco.first(), p.colors.bg)) else p.wallpaper)
        val err = save(ui, copy)
        if (err != null) null to err else copy to null
    } catch (e: PackFormatError) { null to e.errors.take(3).joinToString(" ") }

    /**
     * Applique un pack (personnel ou téléchargé) : image copiée en fond, réglages du pack, icône si elle est possédée.
     * Comme les packs de la boutique, il coupe Material You et les décors saisonniers automatiques.
     * [image] : fichier image du pack (null : celui du pack personnel). Renvoie le message à afficher.
     */
    suspend fun apply(ui: AppState, spec: ThemePackSpec, image: File? = null): String {
        val ctx = ui.app
        val src = image ?: imageFile(ctx, spec.id)
        val imageReady = spec.wallpaper.kind == "image" && withContext(Dispatchers.IO) {
            try {
                if (!src.exists()) return@withContext false
                val b = BitmapFactory.decodeFile(src.path) ?: return@withContext false
                src.copyTo(bgFile(ctx), overwrite = true)
                ui.prefs.bgPalette = Media.palette(b); ui.prefs.bgStamp = nowMs()
                true
            } catch (e: Exception) { false }
        }
        val r = ui.pg.applyPackSpec(spec, imageReady)
        r.icon?.let { k -> if (k != AppIcons.current(ctx)) try { AppIcons.set(ctx, k) } catch (e: Exception) {} }
        ui.prefs.raw.edit().putBoolean("materialYou", false).putBoolean("seasonal", false).putBoolean("bgSeasonAuto", false).apply()
        ui.themePreview = null
        ui.themeTick++; ui.bump()
        return if (r.skipped.isEmpty()) "Pack « ${spec.name} » appliqué." else "Pack « ${spec.name} » appliqué, sans : ${r.skipped.joinToString(", ")}."
    }

    /** Fond seul téléchargé : l'image (déjà vérifiée) devient « Mon image ». */
    suspend fun applyBackground(ui: AppState, image: File): Boolean {
        val ctx = ui.app
        val ok = withContext(Dispatchers.IO) {
            try {
                val b = BitmapFactory.decodeFile(image.path) ?: return@withContext false
                image.copyTo(bgFile(ctx), overwrite = true)
                ui.prefs.bgPalette = Media.palette(b); ui.prefs.bgStamp = nowMs(); true
            } catch (e: Exception) { false }
        }
        if (!ok) return false
        val u = ui.pg.me() ?: return false
        u.settings.bg = "custom"; ui.pg.store.commit()
        ui.prefs.raw.edit().putBoolean("bgSeasonAuto", false).apply()
        ui.themeTick++; ui.bump()
        return true
    }

    const val MAX = 40
}
