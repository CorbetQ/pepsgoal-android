package fr.pepsgoal.core

import fr.pepsgoal.core.model.*

/** Mode démonstration : profil et objectifs d'exemple en mémoire (jamais enregistrés), comme la version web. */
object Demo {
    fun seed(): Db {
        val now = todayDate(); val nowMs = nowMs()
        val me = User(uid(), "camille", "Camille Roux", 0, "Je cours, je lis, je lâche le sucre. Un jour à la fois.", demo = true, createdAt = nowMs - 60 * DAY_MS,
            settings = Settings(social = false), tutorialDone = true, tutoBonus = true, freezes = 3)
        val db = Db(session = me.id, users = mutableListOf(me), sandbox = true)
        fun d(n: Int) = ymd(now.minusDays(n.toLong()))
        fun goal(type: String, title: String, icon: String, ago: Int, vis: String = "friends", f: Goal.() -> Unit = {}): Goal {
            val g = Goal(uid(), me.id, type, title, icon, visibility = vis, createdAt = nowMs - ago * DAY_MS)
            g.days = emptyList(); g.perWeek = 3; g.milestones = emptyList(); g.f(); db.goals.add(g); return g
        }
        fun ck(g: Goal, a: Int, kind: String = "done", value: Double = 1.0, pts: Int = typeInfo(g.type).pts) =
            Checkin(uid(), g.id, me.id, d(a), kind, value, if (kind == "relapse" || kind == "use") 0 else pts, at = nowMs - a * DAY_MS - (a % 5) * 3_600_000L).also { db.checkins.add(it) }

        val run = goal("routine", "Courir 30 minutes", "🏃", 35, "public") { mode = "days"; days = listOf(0, 2, 4) }
        for (i in 1..35) { val wd = wdIdx(now.minusDays(i.toLong())); if (run.days.contains(wd) && (i < 17 || i > 19)) ck(run, i) }
        val read = goal("routine", "Lire 20 pages", "📚", 30) { mode = "count"; perWeek = 4 }
        for (i in 1..30) if (i % 7 !in listOf(2, 5, 6)) ck(read, i)
        val sugar = goal("abstinence", "Zéro sucre ajouté", "🥗", 12) { since = d(12) }
        for (i in 1..12) ck(sugar, i)
        val med = goal("routine", "Méditer 10 minutes", "🧘", 9, "private") { mode = "days"; days = (0..6).toList() }
        for (i in 1..9) ck(med, i)
        val books = goal("objective", "Lire 12 livres", "📖", 180, "public") { target = 12.0; unit = "livres"; endDate = "${now.year}-12-31" }
        listOf(170, 150, 128, 110, 90, 64, 40, 15).forEach { ck(books, it, "progress", 1.0) }
        goal("deadline", "Envoyer le dossier de candidature", "✍️", 6, "private") { due = ymd(now.plusDays(4)) }
        val monthly = goal("monthly", "Courir 80 km par mois", "🏃", 110) { target = 80.0; unit = "km"; cumulate = true; monthsDone = emptyList() }
        var i = 108; while (i >= 1) { ck(monthly, i, "progress", 5.0 + (i * 7) % 8); i -= 3 }
        val coffee = goal("limit", "Max 2 cafés par jour", "☕", 10) { max = 2.0; unit = "cafés"; period = "day" }
        for (a in 10 downTo 1) { val n = if (a == 4) 3 else 1 + a % 2; repeat(n) { ck(coffee, a, "use") }; if (n <= 2) ck(coffee, a) }
        ck(coffee, 0, "use")
        val readT = goal("duration", "Lecture du soir", "📖", 7) { minutes = 30.0 }
        for (a in 7 downTo 1) { ck(readT, a, "progress", 30.0 + (a * 7) % 15, 0); ck(readT, a) }
        ck(readT, 0, "progress", 12.0, 0)
        val site = goal("project", "Lancer mon site perso", "💻", 15) { due = ymd(now.plusDays(20)) }
        val tasks = listOf("Choisir un nom de domaine" to 12, "Maquetter la page d'accueil" to 9, "Rédiger la page « À propos »" to 5, "Sélectionner 6 projets pour le portfolio" to null, "Configurer l'hébergement" to null, "Mettre en ligne et partager" to null)
            .map { (t, a) -> Task(uid(), t, a != null).also { tk -> if (a != null) ck(site, a, "progress", 1.0).taskId = tk.id } }
        site.tasks = tasks
        val tenk = goal("measure", "Temps au 10 km", "🏃", 60, "public") { unit = ""; dir = "down"; timeFmt = true; set("target", 3000.0) }
        listOf(56 to 3520, 49 to 3455, 42 to 3410, 35 to 3380, 28 to 3325, 21 to 3290, 14 to 3240, 7 to 3195, 2 to 3172).forEach { (a, v) -> ck(tenk, a, "measure", v.toDouble()) }
        val wake = goal("timed", "Lever avant 7 h", "⏰", 21) { mode = "before"; time = "07:00"; days = listOf(0, 1, 2, 3, 4) }
        for (a in 1..21) if (wake.days.contains(wdIdx(now.minusDays(a.toLong()))) && a != 8) ck(wake, a)
        val ink = goal("challenge", "Inktober : un dessin par jour", "🎨", now.dayOfMonth - 1) { length = 31; start = ymd(now.withDayOfMonth(1)) }
        for (a in 1 until now.dayOfMonth) ck(ink, a)
        val c25 = goal("program", "Du canapé au 5 km", "🏃", 22) { unit = "min"; base = 60.0; step = 15.0; weeks = 8; start = ymd(weekStart(now).minusDays(21)); weeksDone = listOf(1, 2, 3) }
        for (w in 0 until 3) { val ws = weekStart(now).minusDays(21L - w * 7); listOf(0, 2, 4).forEach { o -> ck(c25, daysBetween(ws.plusDays(o.toLong()), now).toInt(), "progress", Math.ceil((60 + 15 * w) / 3.0)) } }
        ck(c25, daysBetween(weekStart(now), now).toInt(), "progress", 30.0)
        goal("period", "Piscine du jeudi soir", "🏊", 20) { freq = "week"; days = listOf(3); mdays = emptyList(); start = "19:00"; end = "20:30" }
        me.goalGroups = mutableListOf(GoalGroup(uid(), "Sport", "🏃"), GoalGroup(uid(), "Bien-être", "🧘"), GoalGroup(uid(), "Culture", "📚"), GoalGroup(uid(), "Projets", "💼"))
        val g = me.goalGroups
        listOf(run, tenk, monthly, c25).forEach { me.goalGroupOf[it.id] = g[0].id }
        listOf(med, sugar, coffee, wake).forEach { me.goalGroupOf[it.id] = g[1].id }
        listOf(read, books, readT, ink).forEach { me.goalGroupOf[it.id] = g[2].id }
        me.goalGroupOf[site.id] = g[3].id
        me.favGoals = mutableListOf(run.id)
        return db
    }
}
