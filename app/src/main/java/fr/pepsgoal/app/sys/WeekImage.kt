package fr.pepsgoal.app.sys

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import fr.pepsgoal.app.R
import fr.pepsgoal.app.ui.I18n
import fr.pepsgoal.core.Pg
import fr.pepsgoal.core.RecapStats
import fr.pepsgoal.core.Recaps
import fr.pepsgoal.core.WD
import fr.pepsgoal.core.fmtDate
import fr.pepsgoal.core.fmtNum
import fr.pepsgoal.core.pl

/**
 * Récap du dimanche en image (format story 1080 × 1920) : validations de la semaine, comparaison avec la précédente,
 * jours un à un, journées parfaites, objectif phare et profil de la semaine. Dessinée sans Compose, puis partagée en PNG.
 */
object WeekImage {
    /** Semaine à résumer : la semaine en cours le dimanche (dès 17 h), la semaine passée le lundi. */
    fun stats(pg: Pg): RecapStats = Recaps.stats(pg, pg.meId(), "week")

    fun render(ctx: Context, st: RecapStats): Bitmap {
        val tr = { s: String -> I18n.tr(s) }
        val W = 1080f; val H = 1920f
        val bmp = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888); val cv = Canvas(bmp)
        val head = ResourcesCompat.getFont(ctx, R.font.unbounded_extrabold) ?: Typeface.DEFAULT_BOLD
        val body = ResourcesCompat.getFont(ctx, R.font.manrope_extrabold) ?: Typeface.DEFAULT_BOLD
        val bodyR = ResourcesCompat.getFont(ctx, R.font.manrope_semibold) ?: Typeface.DEFAULT
        fun paint(tf: Typeface, size: Float, col: Int, center: Boolean = false, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; color = col; letterSpacing = spacing; if (center) textAlign = Paint.Align.CENTER }
        // Fond : bleu dimanche soir vers corail, halos doux
        cv.drawRect(0f, 0f, W, H, Paint().apply { shader = LinearGradient(0f, 0f, W * .2f, H, intArrayOf(Color.rgb(16, 34, 74), Color.rgb(58, 66, 150), Color.rgb(255, 128, 92)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP) })
        for ((x, y, r, c) in listOf(Quad(W * .9f, H * .1f, 520f, Color.argb(80, 120, 220, 255)), Quad(W * .05f, H * .6f, 600f, Color.argb(70, 180, 120, 255)), Quad(W * .55f, H * .98f, 720f, Color.argb(110, 255, 214, 140))))
            cv.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(x, y, r, c, Color.TRANSPARENT, Shader.TileMode.CLAMP) })
        val white = Color.WHITE; val soft = Color.argb(205, 255, 255, 255)
        cv.drawText(tr("MA SEMAINE PEPSGOAL"), W / 2, 170f, paint(body, 34f, soft, true, .18f))
        cv.drawText(tr("Du ${fmtDate(st.r.s)} au ${fmtDate(st.r.e)}"), W / 2, 230f, paint(bodyR, 34f, soft, true))
        // Grand chiffre et comparaison
        cv.drawText(fmtNum(st.n), W / 2, 470f, paint(head, 220f, white, true))
        cv.drawText(tr(if (st.n > 1) "validations" else "validation"), W / 2, 540f, paint(body, 44f, white, true))
        val diff = st.n - st.prevN
        val cmp = when { st.prevN == 0 -> tr("Première semaine suivie, bravo !"); diff > 0 -> tr("+$diff par rapport à la semaine dernière 📈"); diff < 0 -> tr("${-diff} de moins que la semaine dernière"); else -> tr("Autant que la semaine dernière") }
        cv.drawText(cmp, W / 2, 610f, paint(bodyR, 36f, soft, true))
        // Les 7 jours : barres
        val top = 700f; val base = 1010f; val mx = maxOf(1, st.wdc.maxOrNull() ?: 1)
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(38, 255, 255, 255) }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(70, 255, 255, 255) }
        cv.drawRoundRect(RectF(60f, top - 30f, W - 60f, base + 90f), 40f, 40f, glass); cv.drawRoundRect(RectF(60f, top - 30f, W - 60f, base + 90f), 40f, 40f, edge)
        st.wdc.forEachIndexed { i, v ->
            val bw = 84f; val x = 110f + i * ((W - 220f - bw) / 6f); val h = if (v == 0) 12f else maxOf(24f, 260f * v / mx)
            val col = if (i == st.favWd && v > 0) Color.rgb(255, 210, 122) else Color.argb(150, 255, 255, 255)
            cv.drawRoundRect(RectF(x, base - h, x + bw, base), 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
            if (v > 0) cv.drawText("$v", x + bw / 2, base - h - 16f, paint(body, 32f, white, true))
            cv.drawText(WD[i], x + bw / 2, base + 56f, paint(body, 34f, soft, true))
        }
        // Chiffres clés
        val cells = listOf("${st.perfect}" to tr(if (st.perfect > 1) "journées parfaites" else "journée parfaite"), "${st.reg} %" to tr("de régularité"), fmtNum(st.pts) to tr("flammes gagnées"), "${st.best}" to tr("jours de série max"))
        cells.forEachIndexed { i, (v, l) ->
            val x = 70f + (i % 2) * 480f; val y = 1150f + (i / 2) * 200f; val r = RectF(x, y, x + 460f, y + 180f)
            cv.drawRoundRect(r, 36f, 36f, glass); cv.drawRoundRect(r, 36f, 36f, edge)
            cv.drawText(v, x + 36f, y + 100f, paint(head, 60f, white)); cv.drawText(l, x + 36f, y + 150f, paint(bodyR, 30f, soft))
        }
        // Objectif phare et profil
        st.top?.let { (g, n) ->
            val t = "${g.icon} ${g.title}".let { if (it.length > 28) it.take(27).trimEnd() + "…" else it }
            cv.drawText(tr("Objectif phare"), 70f, 1610f, paint(body, 30f, soft, spacing = .1f))
            cv.drawText(t, 70f, 1665f, paint(body, 44f, white))
            cv.drawText(pl(n, "validation", "validations"), W - 70f, 1665f, paint(bodyR, 32f, soft).apply { textAlign = Paint.Align.RIGHT })
        }
        cv.drawText("${st.persona.e} ${tr(st.persona.n)}", W / 2, 1770f, paint(body, 40f, white, true))
        cv.drawText("🔥 PepsGoal · app.pepsgoal.fr", W / 2, H - 60f, paint(body, 32f, soft, true))
        return bmp
    }

    fun share(ctx: Context, pg: Pg) {
        val st = stats(pg)
        Exports.shareImage(ctx, render(ctx, st), "pepsgoal-semaine-${st.r.s}")
    }

    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Int)
}
