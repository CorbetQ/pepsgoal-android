package fr.pepsgoal.app.sys

import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.core.content.FileProvider
import fr.pepsgoal.app.peps
import fr.pepsgoal.app.ui.I18n
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.Goal
import java.io.File

/**
 * Partage vers les stories Instagram et WhatsApp : une image verticale 1080 × 1920 (progression d'un objectif,
 * ou série et validations du moment) envoyée directement à l'application choisie.
 */
object StoryShare {
    const val INSTAGRAM = "com.instagram.android"
    const val WHATSAPP = "com.whatsapp"

    fun installed(ctx: Context, pkg: String) = try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }

    /** Image de la story : dégradé de la flamme, gros chiffre, titre et série. */
    fun image(ctx: Context, g: Goal?, text: String): Bitmap {
        val pg = ctx.peps.pg; val u = pg.me()
        val w = 1080; val h = 1920; val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
        c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFFFFB000.toInt(), 0xFFFF4A12.toInt(), 0xFFFF3D96.toInt()), null, Shader.TileMode.CLAMP) })
        // Disque plein (crème) avec sa marche derrière le chiffre : aucun halo
        c.drawCircle(w / 2f, 778f, 400f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC2185B.toInt() })
        c.drawCircle(w / 2f, 760f, 400f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF4EC.toInt() })
        val ink = 0xFF2A1420.toInt()
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        val st = u?.let { pg.globalStreak(it.id) }
        val big: String; val unit: String; val title: String
        if (g != null) { val s = pg.statBig(g); big = s.v; unit = s.l; title = "${g.icon} ${g.title}" }
        else { big = "${st?.n ?: 0}"; unit = I18n.tr(if ((st?.n ?: 0) > 1) "jours de série" else "jour de série"); title = "🔥 PepsGoal" }
        tp.textSize = 64f; c.drawText(title.take(28), w / 2f, 330f, tp)
        tp.textSize = 300f; c.drawText(big.take(6), w / 2f, 860f, tp)
        tp.textSize = 64f; tp.typeface = Typeface.DEFAULT; c.drawText(unit.take(30), w / 2f, 960f, tp)
        // Texte libre, sur 3 lignes au plus
        tp.textSize = 46f; tp.color = 0xFF2A1420.toInt()
        wrap(I18n.tr(text), tp, w - 200f).take(3).forEachIndexed { i, l -> c.drawText(l, w / 2f, 1180f + i * 64f, tp) }
        if (g != null && st != null && st.n > 1) { tp.textSize = 44f; tp.typeface = Typeface.DEFAULT_BOLD; c.drawText("🔥 ${st.n} " + I18n.tr("jours de série"), w / 2f, 1460f, tp) }
        tp.textSize = 40f; tp.typeface = Typeface.DEFAULT_BOLD; tp.color = 0xFF2A1420.toInt(); c.drawText("PepsGoal", w / 2f, h - 140f, tp)
        return bmp
    }

    private fun wrap(t: String, p: Paint, max: Float): List<String> {
        val out = mutableListOf<String>(); var cur = ""
        for (word in t.split(' ')) { val n = if (cur.isEmpty()) word else "$cur $word"; if (p.measureText(n) > max && cur.isNotEmpty()) { out += cur; cur = word } else cur = n }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    /** Envoie l'image à Instagram ou WhatsApp (ou au sélecteur d'Android si l'application n'est pas installée). */
    fun send(ctx: Context, pkg: String, g: Goal?, text: String) {
        val f = File(File(ctx.cacheDir, "exports").apply { mkdirs() }, "pepsgoal-story.png")
        f.outputStream().use { image(ctx, g, text).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val i = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkg == WHATSAPP) i.putExtra(Intent.EXTRA_TEXT, I18n.tr(text))
        if (installed(ctx, pkg)) ctx.startActivity(i.setPackage(pkg))
        else ctx.startActivity(Intent.createChooser(i, I18n.tr("Partager")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
