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
import fr.pepsgoal.core.Pg
import fr.pepsgoal.core.RecapStats
import fr.pepsgoal.core.Recaps
import fr.pepsgoal.core.fmtNum
import fr.pepsgoal.core.parseYmd
import fr.pepsgoal.core.pl

/**
 * Image de la rétrospective de l'année, au format story (1080 × 1920) : année, profil, chiffres clés, validations
 * mois par mois et podium des objectifs. Dessinée sans Compose, puis partagée en PNG.
 */
object WrappedImage {
    fun render(ctx: Context, pg: Pg, st: RecapStats): Bitmap {
        val W = 1080f; val H = 1920f
        val bmp = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888); val cv = Canvas(bmp)
        val head = ResourcesCompat.getFont(ctx, R.font.unbounded_extrabold) ?: Typeface.DEFAULT_BOLD
        val body = ResourcesCompat.getFont(ctx, R.font.manrope_extrabold) ?: Typeface.DEFAULT_BOLD
        val bodyR = ResourcesCompat.getFont(ctx, R.font.manrope_semibold) ?: Typeface.DEFAULT
        fun paint(tf: Typeface, size: Float, col: Int, center: Boolean = false, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; color = col; letterSpacing = spacing; if (center) textAlign = Paint.Align.CENTER }
        // Fond : nuit violette vers braise, halos lumineux
        cv.drawRect(0f, 0f, W, H, Paint().apply { shader = LinearGradient(0f, 0f, W * .3f, H, intArrayOf(Color.rgb(20, 14, 58), Color.rgb(92, 32, 120), Color.rgb(255, 106, 58)), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP) })
        for ((x, y, r, c) in listOf(Quad(W * .85f, H * .12f, 520f, Color.argb(90, 255, 79, 216)), Quad(W * .1f, H * .55f, 620f, Color.argb(70, 124, 92, 255)), Quad(W * .6f, H * .95f, 700f, Color.argb(110, 255, 210, 122))))
            cv.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(x, y, r, c, Color.TRANSPARENT, Shader.TileMode.CLAMP) })
        val white = Color.WHITE; val soft = Color.argb(200, 255, 255, 255)
        val year = parseYmd(st.r.s).year
        cv.drawText("MA RÉTROSPECTIVE PEPSGOAL", W / 2, 170f, paint(body, 34f, soft, true, .18f))
        cv.drawText("$year", W / 2, 410f, paint(head, 230f, white, true))
        // Profil de l'année
        cv.drawText(st.persona.e, W / 2, 560f, paint(Typeface.DEFAULT, 110f, white, true))
        cv.drawText(st.persona.n, W / 2, 650f, paint(head, 56f, white, true))
        // Chiffres clés : grille 2 × 2 en verre
        val cells = listOf(fmtNum(st.n) to "validations", fmtNum(st.pts) to "flammes", "${st.best}" to "jours de série max", "${st.reg} %" to "de régularité")
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(38, 255, 255, 255) }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(70, 255, 255, 255) }
        cells.forEachIndexed { i, (v, l) ->
            val x = 70f + (i % 2) * 480f; val y = 720f + (i / 2) * 220f; val r = RectF(x, y, x + 460f, y + 200f)
            cv.drawRoundRect(r, 36f, 36f, glass); cv.drawRoundRect(r, 36f, 36f, edge)
            cv.drawText(v, x + 36f, y + 112f, paint(head, 64f, white)); cv.drawText(l, x + 36f, y + 164f, paint(bodyR, 32f, soft))
        }
        // Validations mois par mois
        val x0 = Recaps.yearExtras(pg, pg.meId(), st.r)
        val top = 1210f; val base = 1430f; val mx = maxOf(1, x0.months.maxOrNull() ?: 1)
        cv.drawText("Ton meilleur mois : ${Recaps.MONTHS_LONG[x0.bestMonth]}", 70f, top - 30f, paint(body, 38f, white))
        x0.months.forEachIndexed { m, v ->
            val bw = 56f; val x = 70f + m * ((W - 140f - bw) / 11f); val h = maxOf(10f, 200f * v / mx)
            cv.drawRoundRect(RectF(x, base - h, x + bw, base), 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (m == x0.bestMonth) Color.rgb(255, 210, 122) else Color.argb(90, 255, 255, 255) })
            cv.drawText(Recaps.MONTHS_SHORT[m], x + bw / 2, base + 44f, paint(body, 30f, soft, true))
        }
        // Podium des objectifs
        val medals = listOf("🥇", "🥈", "🥉")
        x0.podium.forEachIndexed { i, (g, n) ->
            val y = 1560f + i * 92f
            cv.drawText(medals[i], 70f, y, paint(Typeface.DEFAULT, 54f, white))
            val t = "${g.icon} ${g.title}".let { if (it.length > 26) it.take(25).trimEnd() + "…" else it }
            cv.drawText(t, 150f, y - 4f, paint(body, 40f, white))
            cv.drawText(pl(n, "validation", "validations"), W - 70f, y - 4f, paint(bodyR, 32f, soft).apply { textAlign = Paint.Align.RIGHT })
        }
        // Signature
        cv.drawText("🔥 PepsGoal · app.pepsgoal.fr", W / 2, H - 70f, paint(body, 34f, soft, true))
        return bmp
    }

    fun share(ctx: Context, pg: Pg, st: RecapStats) {
        val bmp = render(ctx, pg, st)
        Exports.shareImage(ctx, bmp, "pepsgoal-wrapped-${parseYmd(st.r.s).year}")
    }

    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Int)
}
