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

    /** Partage (ou enregistrement via le sélecteur d'Android) des fichiers exportés. */
    fun share(ctx: Context, files: List<File>, mime: String) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { uri(ctx, it) })
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]) else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        i.type = mime; i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Exporter").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
