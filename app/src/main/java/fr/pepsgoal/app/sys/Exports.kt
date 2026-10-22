package fr.pepsgoal.app.sys

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import fr.pepsgoal.app.data.Journal
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.*
import java.io.File

/** Exports lisibles hors de l'application : tableurs (CSV) et bilan imprimable (PDF). */
object Exports {
    private fun dir(ctx: Context) = File(ctx.cacheDir, "exports").apply { mkdirs() }
    private fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    private fun cell(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    private fun kindLabel(k: String) = mapOf("done" to "validation", "progress" to "progrès", "relapse" to "écart", "use" to "consommation", "measure" to "mesure")[k] ?: k

    /** Trois fichiers CSV (séparateur « ; », lisibles par Excel, LibreOffice ou Google Sheets). */
    fun csv(ctx: Context): List<File> {
        val pg = ctx.peps.pg; val u = pg.me() ?: return emptyList(); val d = dir(ctx)
        val goals = pg.myGoals(u.id)
        val byId = goals.associateBy { it.id }
        val g = File(d, "pepsgoal-objectifs.csv")
        g.writeText("﻿" + (listOf("Objectif;Type;Créé le;Archivé;Série;Statistique;Détail") + goals.map { x ->
            val s = pg.statBig(x)
            listOf(cell("${x.icon} ${x.title}"), cell(typeInfo(x.type).label), ymd(x.createdAt), if (x.archived) "oui" else "non", "${pg.goalStreak(x)}", cell("${s.v} ${s.l}".trim()), cell(pg.metaLine(x))).joinToString(";")
        }).joinToString("\r\n"))
        val c = File(d, "pepsgoal-validations.csv")
        c.writeText("﻿" + (listOf("Date;Objectif;Type;Genre;Valeur;Flammes") + pg.userCheckins(u.id).sortedWith(compareBy({ it.date }, { it.at })).mapNotNull { k ->
            val x = byId[k.goalId] ?: return@mapNotNull null
            listOf(k.date, cell(x.title), cell(typeInfo(x.type).label), kindLabel(k.kind), fmtNum(k.value).replace('.', ','), "${k.pts}").joinToString(";")
        }).joinToString("\r\n"))
        val j = File(d, "pepsgoal-journal.csv")
        j.writeText("﻿" + (listOf("Date;Humeur;Note") + Journal.all(ctx).sortedBy { it.date }.map { e ->
            listOf(e.date, cell(Journal.MOODS.getOrNull(e.mood - 1)?.first ?: ""), cell(e.text)).joinToString(";")
        }).joinToString("\r\n"))
        return listOf(g, c, j)
    }

    /** Bilan PDF (A4) : chiffres clés, objectifs et taux de réussite sur 30 jours, humeur. */
    fun pdf(ctx: Context): File? {
        val pg = ctx.peps.pg; val u = pg.me() ?: return null
        val doc = PdfDocument(); val W = 595; val H = 842; val M = 48f
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create()); var cv = page.canvas; var y = M; var n = 1
        val title = Paint().apply { color = Color.rgb(16, 16, 42); textSize = 24f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val h2 = Paint().apply { color = Color.rgb(255, 74, 18); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val body = Paint().apply { color = Color.rgb(40, 42, 80); textSize = 11f; isAntiAlias = true }
        val muted = Paint(body).apply { color = Color.rgb(110, 112, 150); textSize = 9.5f }
        val bar = Paint().apply { color = Color.rgb(255, 176, 0) }; val track = Paint().apply { color = Color.rgb(236, 238, 255) }
        fun newPage() { doc.finishPage(page); n++; page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create()); cv = page.canvas; y = M }
        fun need(h: Float) { if (y + h > H - M) newPage() }
        fun line(t: String, p: Paint = body, dy: Float = 16f) { need(dy); cv.drawText(t.take(95), M, y, p); y += dy }

        line("Bilan PepsGoal", title, 30f)
        line("${u.name} · édité le ${fmtDate(today())}", muted, 22f)
        val st = pg.globalStreak(u.id); val lv = pg.level(pg.points(u.id))
        val since = ymd(todayDate().minusDays(29))
        val wins = pg.userCheckins(u.id).filter { pg.isWin(it) && it.kind == "done" && it.date >= since }
        line("Chiffres clés", h2, 22f)
        line("Série actuelle : ${pl(st.n, "jour", "jours")} · meilleure série : ${pl(pg.bestStreak(u.id), "jour", "jours")}")
        line("Niveau ${lv.idx} « ${lv.name} » · ${lv.pts} flammes")
        line("Validations sur 30 jours : ${wins.size} · jours actifs : ${wins.map { it.date }.distinct().size}/30", dy = 26f)

        line("Objectifs en cours", h2, 22f)
        val goals = pg.myGoals(u.id).filter { !it.archived }
        if (goals.isEmpty()) line("Aucun objectif en cours.", muted)
        for (g in goals) {
            need(34f)
            val s = pg.statBig(g)
            val days = (0 until 30).map { ymd(todayDate().minusDays(it.toLong())) }.filter { it >= ymd(g.createdAt) }
            val sched = days.filter { pg.scheduled(g, parseYmd(it)) }
            val rate = if (sched.isNotEmpty()) sched.count { pg.hasCheck(g.id, it) }.toFloat() / sched.size else null
            cv.drawText("${g.title.take(60)}  ·  ${typeInfo(g.type).label}", M, y, body)
            cv.drawText("${s.v} ${s.l}".take(30), W - M - 150f, y, muted)
            y += 8f
            cv.drawRoundRect(M, y, W - M, y + 6f, 3f, 3f, track)
            if (rate != null) cv.drawRoundRect(M, y, M + (W - 2 * M) * rate, y + 6f, 3f, 3f, bar)
            y += 14f
            cv.drawText(if (rate != null) "Réussite sur 30 jours : ${Math.round(rate * 100)} %" else pg.metaLine(g).take(90), M, y, muted)
            y += 16f
        }
        val moods = Journal.all(ctx).filter { it.date >= since }
        if (moods.isNotEmpty()) {
            y += 8f; line("Humeur (30 jours)", h2, 22f)
            line("Moyenne : ${fmtNum(Math.round(moods.map { it.mood }.average() * 10) / 10.0)}/5 sur ${pl(moods.size, "jour noté", "jours notés")}")
            Journal.insight(ctx)?.let { line(it, muted) }
        }
        y = maxOf(y, H - M - 10f); cv.drawText("Généré par PepsGoal $APP_VERSION — les données restent les tiennes.", M, H - M + 10f, muted)
        doc.finishPage(page)
        val f = File(dir(ctx), "pepsgoal-bilan-${today()}.pdf")
        f.outputStream().use { doc.writeTo(it) }; doc.close()
        return f
    }

    /**
     * Livre de l'année (PDF, A4) : couverture, chiffres clés, validations mois par mois, objectifs phares,
     * année en pixels, plus beaux jours du journal et quelques photos de preuve.
     */
    fun yearBook(ctx: Context, year: Int = todayDate().year): File? {
        val pg = ctx.peps.pg; val u = pg.me() ?: return null
        val doc = PdfDocument(); val W = 595; val H = 842; val M = 48f
        var n = 1; var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create()); var cv = page.canvas; var y = M
        val flame = Color.rgb(255, 74, 18); val inkC = Color.rgb(16, 16, 42)
        val title = Paint().apply { color = inkC; textSize = 30f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val h2 = Paint().apply { color = flame; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val body = Paint().apply { color = Color.rgb(40, 42, 80); textSize = 11f; isAntiAlias = true }
        val muted = Paint(body).apply { color = Color.rgb(110, 112, 150); textSize = 9.5f }
        fun newPage() { doc.finishPage(page); n++; page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create()); cv = page.canvas; y = M }
        fun need(h: Float) { if (y + h > H - M) newPage() }
        fun line(t: String, p: Paint = body, dy: Float = 16f) { need(dy); cv.drawText(t.take(95), M, y, p); y += dy }
        val from = "$year-01-01"; val to = "$year-12-31"
        val wins = pg.userCheckins(u.id).filter { pg.isWin(it) && it.kind != "skip" && it.date in from..to }
        // Couverture
        cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), intArrayOf(Color.rgb(255, 176, 0), flame, Color.rgb(255, 45, 135)), null, android.graphics.Shader.TileMode.CLAMP) })
        val cover = Paint(title).apply { color = inkC; textSize = 46f; textAlign = Paint.Align.CENTER }
        cv.drawText("Mon année $year", W / 2f, H / 2f - 40f, cover)
        cover.textSize = 20f; cover.typeface = Typeface.DEFAULT
        cv.drawText(u.name, W / 2f, H / 2f + 4f, cover)
        cv.drawText("${wins.size} validations · ${wins.map { it.date }.distinct().size} jours actifs", W / 2f, H / 2f + 36f, cover)
        cover.textSize = 13f; cv.drawText("PepsGoal", W / 2f, H - 60f, cover)
        newPage()
        // Chiffres clés
        line("Chiffres clés", h2, 24f)
        val rates = Insights.dayRates(pg, daysBetween(java.time.LocalDate.of(year, 1, 1), todayDate()).toInt() + 1).filterKeys { it in from..to }
        line("Validations : ${wins.size} · jours actifs : ${wins.map { it.date }.distinct().size} · journées parfaites : ${rates.values.count { it >= 1.0 }}")
        line("Meilleure série : ${pl(pg.bestStreak(u.id), "jour", "jours")} · série actuelle : ${pl(pg.globalStreak(u.id).n, "jour", "jours")}")
        line("Flammes gagnées : ${wins.sumOf { it.pts }}", dy = 26f)
        // Mois par mois
        line("Mois par mois", h2, 24f)
        val perMonth = (1..12).map { m -> wins.count { it.date.substring(5, 7).toInt() == m } }
        val mx = (perMonth.maxOrNull() ?: 0).coerceAtLeast(1)
        need(150f)
        val bw = (W - 2 * M) / 12f; val base = y + 110f
        val bar = Paint().apply { color = flame }; val lbl = Paint(muted).apply { textAlign = Paint.Align.CENTER }
        perMonth.forEachIndexed { i, v ->
            val hh = 100f * v / mx
            cv.drawRoundRect(M + i * bw + 6f, base - hh, M + (i + 1) * bw - 6f, base, 4f, 4f, bar)
            cv.drawText(listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")[i], M + (i + .5f) * bw, base + 14f, lbl)
            if (v > 0) cv.drawText("$v", M + (i + .5f) * bw, base - hh - 4f, lbl)
        }
        y = base + 34f
        // Objectifs phares
        line("Objectifs phares", h2, 24f)
        wins.groupingBy { it.goalId }.eachCount().entries.sortedByDescending { it.value }.take(8).forEach { (gid, c) ->
            pg.goalById(gid)?.let { g -> line("${g.title.take(60)} — ${pl(c, "validation", "validations")}") }
        }
        y += 10f
        // Année en pixels
        line("Année en pixels", h2, 24f)
        need(31 * 9f + 20f)
        val cw = (W - 2 * M) / 12f; val ch = 9f; val top = y
        for (m in 1..12) {
            val len = java.time.LocalDate.of(year, m, 1).lengthOfMonth()
            for (d in 1..len) {
                val k = ymd(java.time.LocalDate.of(year, m, d)); val r = rates[k]
                val col = when { k > today() -> Color.rgb(246, 246, 252); r == null -> Color.rgb(236, 238, 255); r >= 1.0 -> Color.rgb(46, 194, 126); r >= .5 -> Color.rgb(255, 176, 0); r > 0 -> Color.rgb(255, 107, 94); else -> Color.rgb(226, 228, 245) }
                cv.drawRect(M + (m - 1) * cw + 2f, top + (d - 1) * ch + 1f, M + m * cw - 2f, top + d * ch - 1f, Paint().apply { color = col })
            }
        }
        y = top + 31 * ch + 20f
        // Journal : les meilleurs jours avec un mot
        val best = Journal.all(ctx).filter { it.date in from..to && it.text.isNotBlank() }.sortedWith(compareByDescending<fr.pepsgoal.app.data.JournalEntry> { it.mood }.thenBy { it.date }).take(10).sortedBy { it.date }
        if (best.isNotEmpty()) {
            line("Plus beaux jours du journal", h2, 24f)
            best.forEach { e -> line(fmtDate(e.date), muted, 13f); e.text.replace('\n', ' ').chunked(90).take(3).forEach { line(it) }; y += 4f }
        }
        // Photos de preuve (6 au plus)
        val photos = fr.pepsgoal.app.data.Proofs.all(ctx).filter { it.date in from..to }.mapNotNull { pr -> fr.pepsgoal.app.data.Proofs.photoFile(ctx, pr)?.let { pr to it } }.sortedBy { it.first.date }
        if (photos.isNotEmpty()) {
            newPage(); line("En images", h2, 24f)
            val pw = (W - 2 * M - 20f) / 3f; val ph = pw * 1.25f
            photos.let { l -> if (l.size > 6) l.indices.map { l[it * (l.size - 1) / 5] }.distinct().take(6) else l }.forEachIndexed { i, (pr, f) ->
                val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                val b = android.graphics.BitmapFactory.decodeFile(f.path, o) ?: return@forEachIndexed
                val x = M + (i % 3) * (pw + 10f); val yy = y + (i / 3) * (ph + 30f)
                cv.drawBitmap(b, null, android.graphics.RectF(x, yy, x + pw, yy + ph), Paint(Paint.FILTER_BITMAP_FLAG))
                cv.drawText(fmtDate(pr.date), x, yy + ph + 14f, muted)
            }
        }
        cv.drawText("Généré par PepsGoal $APP_VERSION", M, H - M + 10f, muted)
        doc.finishPage(page)
        val f = File(dir(ctx), "pepsgoal-livre-$year.pdf")
        f.outputStream().use { doc.writeTo(it) }; doc.close()
        return f
    }

    /** Partage d'une image générée (année en pixels, code QR…). */
    fun shareImage(ctx: Context, bmp: android.graphics.Bitmap, name: String) {
        val f = File(dir(ctx), "$name.png"); f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        share(ctx, listOf(f), "image/png")
    }

    /** Partage (ou enregistrement via le sélecteur d'Android) des fichiers exportés. */
    fun share(ctx: Context, files: List<File>, mime: String) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { uri(ctx, it) })
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]) else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        i.type = mime; i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Exporter").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
