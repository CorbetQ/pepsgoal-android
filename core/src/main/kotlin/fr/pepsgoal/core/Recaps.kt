package fr.pepsgoal.core

import fr.pepsgoal.core.model.Goal
import java.time.LocalDate

/** Contenu d'une diapositive de récap. Le texte peut contenir du **gras**. */
sealed class Part {
    class Eyebrow(val text: String) : Part()
    class Title(val text: String, val big: Boolean = false) : Part()
    class Text(val text: String) : Part()
    class Joke(val text: String) : Part()
    class Big(val value: Int, val prefix: String = "", val suffix: String = "", val raw: String? = null) : Part()
    class Emoji(val e: String, val anim: String = "bounce") : Part()
    class Flame(val scale: Float, val big: Boolean = true) : Part()
    class Dots(val days: List<Boolean>) : Part()
    class Week(val counts: List<Int>, val fav: Int) : Part()
    class Facts(val items: List<String>) : Part()
    class Awards(val items: List<Triple<String, String, String>>) : Part()
    class Cells(val items: List<Pair<String, String>>) : Part()
    object FinalActions : Part()
    object CloseAction : Part()
}

class Slide(val bg: List<String>, val fx: String?, val cls: String, val parts: List<Part>)

class RecapRange(val s: String, val e: String, val days: Int, val past: Boolean)

class Persona(val n: String, val e: String, val d: String)

class RecapStats(
    val p: String, val r: RecapRange, val n: Int, val relapses: Int, val pts: Int, val days: Set<String>, val reg: Int,
    val best: Int, val frozenUsed: Int, val dots: List<Boolean>, val gap: Int, val wdc: List<Int>, val favWd: Int, val favWdN: Int,
    val weekend: Double, val slot: String, val early: Long?, val late: Long?, val bestDay: Pair<String, Int>?, val top: Pair<Goal, Int>?,
    val units: Pair<Goal, Double>?, val minutes: Int, val resist: Int, val tasks: Int, val deadlines: Int, val created: Int, val perfect: Int,
    val posts: Int, val cheers: Int, val prevN: Int, val prevRate: Double, val rate0: Double, val persona: Persona
)

object Recaps {
    val PERIOD_WORD = mapOf("week" to "de la semaine", "month" to "du mois", "year" to "de l'année")
    val PERIOD_NOUN = mapOf("week" to "semaine", "month" to "mois", "year" to "année")
    val SLOT_LABEL = mapOf("matin" to "le matin", "midi" to "à midi", "aprem" to "l'après-midi", "soir" to "le soir", "nuit" to "la nuit")

    /** Période en cours, sauf le lendemain de sa fin (J+1) : le bilan complet de la période écoulée reste affiché. */
    fun range(p: String, ref: LocalDate = todayDate()): RecapRange {
        val t = ref; var s: LocalDate; var past = false; val e: LocalDate
        when (p) {
            "week" -> { s = weekStart(t); if (s == t) { past = true; s = s.minusDays(7) }; e = if (past) s.plusDays(6) else t }
            "month" -> { s = t.withDayOfMonth(1); if (t.dayOfMonth == 1) { past = true; s = s.minusMonths(1) }; e = if (past) s.withDayOfMonth(s.lengthOfMonth()) else t }
            else -> { s = t.withDayOfYear(1); if (s == t) { past = true; s = s.minusYears(1) }; e = if (past) LocalDate.of(s.year, 12, 31) else t }
        }
        return RecapRange(ymd(s), ymd(e), (daysBetween(s, e) + 1).toInt(), past)
    }
    fun prevRange(p: String, r: RecapRange): RecapRange {
        val s0 = parseYmd(r.s)
        val s = when (p) { "week" -> s0.minusDays(7); "month" -> s0.minusMonths(1); else -> s0.minusYears(1) }
        val e = s0.minusDays(1)
        return RecapRange(ymd(s), ymd(e), (daysBetween(s, e) + 1).toInt(), false)
    }
    fun title(p: String, r: RecapRange = range(p)): Pair<String, String> {
        val s = parseYmd(r.s); val e = parseYmd(r.e)
        return when (p) {
            "week" -> (if (r.past) "Ta semaine passée" else "Ta semaine") to "Du ${fmtDate(r.s)} au ${fmtDate(r.e)}"
            "month" -> { val m = s.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, FR).lowercase(FR)
                "Ton mois ${if (m.first() in "aeiouy") "d'" else "de "}$m" to "Du 1er au ${e.dayOfMonth} $m${if (r.past) " · bilan complet" else ""}" }
            else -> "Ton année ${s.year}" to "Du 1er janvier au ${fmtDate(r.e)}${if (r.past) " · bilan complet" else ""}"
        }
    }
    fun countIn(pg: Pg, id: String, p: String): Int { val r = range(p); return pg.db.checkins.count { it.userId == id && pg.isWin(it) && it.date >= r.s && it.date <= r.e } }

    /** Hasard reproductible : les mêmes phrases pour une même période (identique à la version web). */
    fun seeded(str: String): () -> Double {
        var h = 1779033703 xor str.length
        for (ch in str) { h = (h xor ch.code) * 3432918353L.toInt(); h = (h shl 13) or (h ushr 19) }
        return {
            h = (h xor (h ushr 16)) * 2246822507L.toInt()
            h = (h xor (h ushr 13)) * 3266489909L.toInt()
            h = h xor (h ushr 16)
            (h.toLong() and 0xffffffffL) / 4294967296.0
        }
    }

    fun stats(pg: Pg, id: String, p: String, r: RecapRange = range(p)): RecapStats {
        val db = pg.db
        val inR = { d: String -> d >= r.s && d <= r.e }
        val cs = db.checkins.filter { it.userId == id && inR(it.date) }
        val ok = cs.filter { pg.isWin(it) }
        val days = ok.map { it.date }.toSet()
        val reg = Math.round(days.size * 100.0 / r.days).toInt()
        val sd = pg.streakDays(id); val frozen = (pg.userById(id)?.frozenDays ?: emptyList()).toSet()
        var run = 0; var gapRun = 0; var best = 0; var frozenUsed = 0; var gap = 0; val dots = mutableListOf<Boolean>()
        var d = parseYmd(r.s); val end = parseYmd(r.e)
        while (!d.isAfter(end)) {
            val k = ymd(d)
            if (sd.contains(k)) { run++; best = maxOf(best, run) } else run = 0
            if (frozen.contains(k) && !days.contains(k)) frozenUsed++
            if (days.contains(k)) gapRun = 0 else { gapRun++; gap = maxOf(gap, gapRun) }
            dots.add(days.contains(k)); d = d.plusDays(1)
        }
        val wdc = MutableList(7) { 0 }; ok.forEach { wdc[wdIdx(parseYmd(it.date))]++ }
        val favWd = wdc.indexOf(wdc.max())
        val slots = linkedMapOf("matin" to 0, "midi" to 0, "aprem" to 0, "soir" to 0, "nuit" to 0)
        ok.forEach { c -> val h = localDateTime(c.at).hour
            val k = if (h in 5..10) "matin" else if (h in 11..13) "midi" else if (h in 14..17) "aprem" else if (h in 18..21) "soir" else "nuit"
            slots[k] = slots[k]!! + 1 }
        val slot = slots.entries.sortedByDescending { it.value }.first().key
        val mins = { at: Long -> val t = localDateTime(at); val m = t.hour * 60 + t.minute; if (m < 240) m + 1440 else m }
        val timed = ok.filter { it.at > 0 }
        val early = timed.minByOrNull { mins(it.at) }?.at; val late = timed.maxByOrNull { mins(it.at) }?.at
        val perDay = ok.groupingBy { it.date }.eachCount()
        val bestDay = perDay.entries.maxByOrNull { it.value }?.let { it.key to it.value }
        val byG = ok.groupingBy { it.goalId }.eachCount()
        val top = byG.entries.filter { pg.goalById(it.key) != null }.maxByOrNull { it.value }?.let { pg.goalById(it.key)!! to it.value }
        val types = HashMap<String, Int>(); ok.forEach { c -> pg.goalById(c.goalId)?.let { types[it.type] = (types[it.type] ?: 0) + 1 } }
        val units = HashMap<String, Double>(); ok.filter { it.kind == "progress" && pg.goalById(it.goalId)?.type != "duration" }.forEach { units[it.goalId] = (units[it.goalId] ?: 0.0) + it.value }
        val uTop = units.entries.filter { pg.goalById(it.key) != null }.maxByOrNull { it.value }?.let { pg.goalById(it.key)!! to it.value }
        val minutes = Math.round(cs.filter { it.kind == "progress" && pg.goalById(it.goalId)?.type == "duration" }.sumOf { it.value }).toInt()
        val gs = db.goals.filter { it.userId == id }
        val posts = db.posts.filter { it.userId == id && inR(ymd(it.at)) }
        val pr = prevRange(p, r)
        val prevN = db.checkins.count { it.userId == id && pg.isWin(it) && it.date >= pr.s && it.date <= pr.e }
        val n = ok.size; val relapses = cs.count { it.kind == "relapse" }
        val share = { t: String -> (types[t] ?: 0).toDouble() / maxOf(1, n) }
        val weekend = (wdc[5] + wdc[6]).toDouble() / maxOf(1, n)
        val created = gs.count { inR(ymd(it.createdAt)) }
        val persona = when {
            reg >= 95 && r.days >= 5 -> Persona("Machine suisse", "⌚", "Précision horlogère : pas une journée de ratée. À Genève, les horlogers prennent des notes.")
            reg >= 80 && r.days >= 3 -> Persona("Métronome", "⏱️", "Réglé comme une horloge : tu n'as presque rien laissé passer.")
            share("abstinence") >= .4 -> Persona("Roc", "🪨", "Rien ne te fait plier. Les tentations ont déposé une réclamation.")
            relapses > 0 && n >= relapses * 3 -> Persona("Phénix", "🦅", "Un écart ? Le rebond a été immédiat. Les cendres, très peu pour toi.")
            slot == "nuit" -> Persona("Ninja de minuit", "🥷", "Tu valides quand tout le monde dort. Discret, efficace, légèrement inquiétant.")
            slot == "matin" -> Persona("Lève-tôt", "🌅", "Tu attaques ta journée avant tout le monde. Le soleil se lève, toi tu as déjà fini.")
            slot == "soir" -> Persona("Oiseau de nuit", "🦉", "Tes meilleures victoires arrivent quand la ville s'endort.")
            weekend >= .5 && n >= 4 -> Persona("Guerrier du week-end", "🏖️", "En semaine tu recharges, le week-end tu dévores tout.")
            share("objective") + share("monthly") >= .4 -> Persona("Architecte", "🧱", "Pierre après pierre, tu fais grimper les compteurs.")
            best >= 7 -> Persona("Flamme éternelle", "🔥", "Ta série ne connaît pas la fatigue. Les pompiers sont prévenus.")
            created >= 3 -> Persona("Collectionneur", "🗂️", "De nouveaux objectifs à la pelle : ton musée des ambitions s'agrandit.")
            else -> Persona("Étincelle", "✨", "Le feu est lancé. La suite promet.")
        }
        return RecapStats(p, r, n, relapses, ok.sumOf { it.pts }, days, reg, best, frozenUsed, dots, gap, wdc, favWd, wdc[favWd], weekend, slot,
            early, late, bestDay, top, uTop, minutes, ok.count { pg.goalById(it.goalId)?.type == "abstinence" }, ok.count { it.taskId != null },
            gs.count { it.type == "deadline" && it.completedAt != null && inR(ymd(it.completedAt!!)) }, created,
            (pg.userById(id)?.perfectDays ?: emptyList()).count(inR), posts.size, posts.sumOf { q -> q.likes.count { it != id } },
            prevN, prevN.toDouble() / pr.days, n.toDouble() / r.days, persona)
    }

    fun snap(pg: Pg, id: String, p: String): Map<String, Any> {
        val st = stats(pg, id, p)
        return mapOf("title" to mapOf("week" to "Ma semaine", "month" to "Mon mois", "year" to "Mon année")[p]!!, "n" to st.n, "pts" to st.pts, "best" to st.best, "reg" to st.reg, "persona" to st.persona.n, "e" to st.persona.e)
    }

    fun shareText(st: RecapStats) = "Mon récap ${PERIOD_WORD[st.p]} sur PepsGoal : ${pl(st.n, "validation", "validations")}, ${st.pts} flammes, meilleure série de ${pl(st.best, "jour", "jours")}, ${st.reg} % de régularité. Profil : ${st.persona.n} ${st.persona.e}"

    fun slides(pg: Pg, id: String, p: String): Pair<List<Slide>, RecapStats> {
        val r = range(p); val st = stats(pg, id, p, r); val (tt, sub) = title(p, r); val out = mutableListOf<Slide>()
        val rnd = seeded("$id|$p|${r.s}"); fun <T> pick(a: List<T>) = a[(rnd() * a.size).toInt().coerceAtMost(a.size - 1)]
        fun add(parts: List<Part>, fx: String?, cls: String = "") { out.add(Slide(SBG[out.size % SBG.size], fx, cls, parts)) }
        val noun = PERIOD_NOUN[p]!!
        val prevCmp = mapOf("week" to "à la semaine précédente", "month" to "au mois précédent", "year" to "à l'année précédente")[p]!!
        add(listOf(Part.Eyebrow("Récap PepsGoal${if (r.past) " · bilan complet" else ""}"), Part.Flame(1f), Part.Title(tt), Part.Text("$sub."),
            Part.Joke(pick(listOf("Attache ta ceinture, on rembobine.", "Prépare le pop-corn 🍿, c’est l’heure du bilan.", "Roulement de tambour… 🥁", "On a compté pour toi. Promis, sans tricher.", "Installe-toi, ce récap est garanti sans pub.")))), "✨", "intro")
        if (st.n == 0) {
            add(listOf(Part.Emoji("🌱"), Part.Title("Tout reste à écrire"), Part.Text("Aucune validation sur cette période${if (r.past) "" else " pour l’instant"}."),
                Part.Joke(pick(listOf("Même les plantes vertes ont fait mieux : elles, au moins, ont fait leur photosynthèse.", "Le calme avant la tempête, on le sent.", "Une page blanche, c’est surtout beaucoup de place pour la suite."))), Part.CloseAction), "🌱")
            return out to st
        }
        val perDay = Math.round(st.n.toDouble() / maxOf(1, st.days.size) * 10) / 10.0
        val equiv = pick(listOf(
            "**${st.pts} flammes** : de quoi allumer ${pl(maxOf(1, Math.round(st.pts / 30.0).toInt()), "gâteau d'anniversaire", "gâteaux d'anniversaire")} de 30 bougies 🎂.",
            "**${st.pts} flammes** : assez pour faire griller ${pl(maxOf(1, Math.round(st.pts / 12.0).toInt()), "chamallow", "chamallows")} 🔥.",
            "Ça mérite au moins ${pl(st.n * 2, "high five", "high fives")} ✋. On te les doit.",
            "**${st.pts} flammes** : si c’était des degrés, ton four serait jaloux."))
        add(listOf(Part.Eyebrow("Validations"), Part.Big(st.n), Part.Text("actions validées, soit ${fmtNum(perDay)} par jour actif."), Part.Joke(equiv)), "✅")
        val regLine = when {
            st.reg >= 100 -> pick(listOf("Tous les jours. TOUS. Ton réveil demande des vacances.", "Sans-faute. On a vérifié deux fois."))
            st.reg >= 80 -> pick(listOf("Presque parfait : les jours manqués se comptent sur les doigts d’une main (de dessin animé).", "Une régularité de métronome, à un ou deux tic-tac près."))
            st.reg >= 50 -> pick(listOf("Plus d’un jour sur deux : la constance est en route.", "Un jour oui, un jour… oui aussi, souvent."))
            else -> pick(listOf("Pas tous les jours, mais chaque retour compte double (dans notre cœur).", "La régularité, c’est comme le sport : ça s’entraîne."))
        }
        add(listOf(Part.Eyebrow("Régularité"), Part.Big(st.reg, suffix = "%"), Part.Text("des jours avec au moins une validation : ${st.days.size} sur ${r.days}."), Part.Dots(st.dots), Part.Joke(regLine)), "📅")
        val k = minOf(1.5, .75 + st.best / 25.0).toFloat()
        val streakLine = when {
            st.best >= 30 -> "Un mois sans flancher. Ta flamme a sa propre météo."
            st.best >= 7 -> pick(listOf("Une semaine entière sans éteindre la flamme. Respect.", "Sept jours d’affilée : même la flemme a abandonné."))
            st.best >= 3 -> pick(listOf("Un joli feu de camp 🏕️ : manque plus que les guimauves.", "Trois jours et plus : la machine est lancée."))
            else -> "Une étincelle, c’est comme ça que commencent les grands feux."
        }
        add(listOf(Part.Eyebrow("Meilleure série"), Part.Flame(k, false), Part.Big(st.best), Part.Text("${if (st.best > 1) "jours d'affilée" else "jour"} sans éteindre ta flamme${if (st.frozenUsed > 0) ", avec ${pl(st.frozenUsed, "gel", "gels")} en renfort" else ""}."), Part.Joke(streakLine)), "🔥")
        if (st.prevN > 0) {
            val dd = Math.round((st.rate0 - st.prevRate) / maxOf(.0001, st.prevRate) * 100).toInt(); val up = dd > 5; val down = dd < -5
            add(listOf(Part.Eyebrow("Face $prevCmp"), Part.Emoji(if (up) "📈" else if (down) "📉" else "⚖️"), Part.Big(dd, raw = "${if (up) "+" else ""}$dd%"), Part.Text("de validations par jour par rapport $prevCmp."),
                Part.Joke(if (up) pick(listOf("En progrès comme un cookie au four : ça monte et ça sent bon.", "Toi d’avant appelle, il demande tes secrets.", "La courbe grimpe. Les alpinistes sont inquiets."))
                    else if (down) pick(listOf("Petit coup de mou ? Même les fusées ont des étages.", "On recule pour mieux sauter. C’est scientifique (à peu près).", "Une pause, pas un abandon. La suite te tend les bras."))
                    else pick(listOf("Stable comme un chat sur un radiateur.", "Même niveau : la constance, c’est un talent.")))), if (up) "🚀" else if (down) "🐢" else "⚖️")
        }
        if (p != "week" || st.n >= 3) {
            val dayJoke = listOf("Le lundi ne te fait pas peur. Le lundi a peur de toi.", "Mardi, le héros discret de ta semaine.", "Mercredi : le milieu de semaine, ton terrain de jeu.", "Jeudi, presque vendredi, déjà au taquet.", "Vendredi motivé : espèce rare, à protéger.", "Samedi studieux : le canapé est vexé.", "Dimanche productif : le brunch peut attendre.")[st.favWd]
            add(listOf(Part.Eyebrow("Ton jour fétiche"), Part.Title("Le ${WD_LONG[st.favWd]}"), Part.Week(st.wdc, st.favWd), Part.Text("${pl(st.favWdN, "validation", "validations")} ce jour-là."), Part.Joke(dayJoke)), "📊")
        }
        val slotEmoji = mapOf("matin" to "🌅", "midi" to "🥪", "aprem" to "☀️", "soir" to "🌆", "nuit" to "🌙")[st.slot]!!
        val slotJoke = mapOf("matin" to "Pendant que d’autres appuient sur « snooze », toi tu valides.", "midi" to "La pause déjeuner, ton moment de gloire. Le sandwich attendra.", "aprem" to "L'après-midi, quand la sieste appelle… et que tu ne réponds pas.", "soir" to "Le soir, tu bosses pendant que ta série télé patiente.", "nuit" to "La nuit porte conseil… et validations.")[st.slot]!!
        add(listOf(Part.Eyebrow("Ton créneau"), Part.Emoji(slotEmoji, "spin"), Part.Title("Tu valides surtout ${SLOT_LABEL[st.slot]}"), Part.Joke(slotJoke)), slotEmoji)
        st.top?.let { (g, cnt) -> add(listOf(Part.Eyebrow("Ton objectif chouchou"), Part.Emoji(g.icon), Part.Title(g.title), Part.Text("Validé **${pl(cnt, "fois", "fois")}**, soit ${Math.round(cnt * 100.0 / st.n)} % de tes validations."),
            Part.Joke(pick(listOf("Les autres objectifs commencent à être jaloux.", "Une vraie relation sérieuse.", "Si c’était un plat, ce serait ta spécialité.")))), g.icon) }
        val facts = mutableListOf<String>()
        if (st.minutes >= 30) { val h = Math.round(st.minutes / 6.0) / 10.0
            facts.add("⏳ **${fmtNum(h)} h** cumulées sur tes objectifs de durée : ${pick(listOf("de quoi regarder ${pl(maxOf(1, Math.round(st.minutes / 195.0).toInt()), "fois", "fois")} Titanic (et pleurer à la fin)", "l’équivalent de ${pl(maxOf(1, Math.round(st.minutes / 25.0).toInt()), "pizza cuite", "pizzas cuites")} au four", "${pl(maxOf(1, Math.round(st.minutes / 3.0).toInt()), "chanson", "chansons")} de 3 minutes à la suite"))}.") }
        st.units?.let { (g, v) -> if (Regex("^km$", RegexOption.IGNORE_CASE).matches(g.dataUnit())) facts.add("🗼 **${fmtNum(v)} km** : ${fmtNum(Math.round(v * 1000 / 330 * 10) / 10.0)} tours Eiffel couchées bout à bout.") else facts.add("📏 **${fmtNum(v)} ${g.dataUnit()}** pour « ${g.title} ». Les compteurs chauffent.") }
        st.early?.let { if (localDateTime(it).hour < 8) facts.add("🐓 Ta validation la plus matinale : **${hm2(it)}**. Le coq n’était pas encore levé.") }
        st.late?.let { val h = localDateTime(it).hour; if (h >= 22 || h < 4) facts.add("🦇 La plus tardive : **${hm2(it)}**. Sur le fil, comme au cinéma.") }
        if (st.tasks > 0) facts.add("🧱 ${pl(st.tasks, "étape de projet franchie", "étapes de projet franchies")}. Les architectes approuvent.")
        if (facts.isNotEmpty()) add(listOf(Part.Eyebrow("Le saviez-vous ?"), Part.Emoji("🧠"), Part.Facts(facts.take(3))), "💡")
        val cells = mutableListOf<Pair<String, String>>()
        if (st.resist > 0) cells.add("${st.resist}" to if (st.resist > 1) "jours de résistance" else "jour de résistance")
        if (st.deadlines > 0) cells.add("${st.deadlines}" to if (st.deadlines > 1) "deadlines bouclées" else "deadline bouclée")
        if (st.created > 0) cells.add("${st.created}" to if (st.created > 1) "nouveaux objectifs" else "nouvel objectif")
        if (st.relapses > 0) cells.add("${st.relapses}" to if (st.relapses > 1) "rebonds après un écart" else "rebond après un écart")
        if (st.perfect > 0) cells.add("${st.perfect}" to if (st.perfect > 1) "journées parfaites" else "journée parfaite")
        if (cells.isNotEmpty()) { if (cells.size % 2 == 1) cells.add("${st.days.size}" to if (st.days.size > 1) "jours actifs" else "jour actif"); add(listOf(Part.Eyebrow("En chiffres"), Part.Title("Ce que tu as construit"), Part.Cells(cells)), "🧮") }
        if (pg.socialOn() && (st.posts > 0 || st.cheers > 0)) add(listOf(Part.Eyebrow("Côté tribu"), Part.Big(st.cheers), Part.Text("${if (st.cheers > 1) "encouragements reçus" else "encouragement reçu"} sur ${pl(st.posts, "publication", "publications")}."),
            Part.Joke(pick(listOf("Ta tribu croit en toi. Et elle a raison.", "Ton fan-club s’agrandit.", "Les applaudissements sont de bon cœur, on a vérifié.")))), "💖")
        val aw = mutableListOf<Triple<String, String, String>>()
        st.bestDay?.let { (dd, nn) -> if (nn >= 2) aw.add(Triple("🏋️", "Jour de gloire", "${cap(weekdayLongDate(parseYmd(dd)))} : $nn validations")) }
        st.early?.let { if (localDateTime(it).hour < 7) aw.add(Triple("🐓", "Prix du coq", "Validation à ${hm2(it)}")) }
        st.late?.let { val h = localDateTime(it).hour; if (h >= 23 || h < 4) aw.add(Triple("🦇", "Prix de la chauve-souris", "Validation à ${hm2(it)}")) }
        if (st.perfect > 0) aw.add(Triple("🌟", "Sans fausse note", pl(st.perfect, "journée parfaite", "journées parfaites")))
        if (st.relapses > 0) aw.add(Triple("🩹", "Prix du rebond", "Rebond après ${pl(st.relapses, "écart", "écarts")}"))
        if (st.gap >= 3) aw.add(Triple("🦥", "Prix de la sieste", "${st.gap} jours de pause d’affilée (on ne juge pas)"))
        if (st.best >= 7) aw.add(Triple("🔥", "Pyromane certifié", "${st.best} jours de série"))
        if (st.created >= 2) aw.add(Triple("🗂️", "Prix de l’ambition", "${st.created} nouveaux objectifs"))
        if (aw.isNotEmpty()) add(listOf(Part.Eyebrow("Les trophées ${PERIOD_WORD[p]}"), Part.Title("Et le prix est décerné à… toi"), Part.Awards(aw.take(3))), "🏆")
        add(listOf(Part.Eyebrow("Ton profil ${PERIOD_WORD[p]}"), Part.Emoji(st.persona.e, "spin"), Part.Title(st.persona.n, big = true), Part.Text(st.persona.d)), st.persona.e)
        add(listOf(Part.Eyebrow("En résumé"), Part.Title(tt), Part.Cells(listOf("${st.n}" to "validations", "${st.pts}" to "flammes", "${st.best}" to "meilleure série", "${st.reg}%" to "régularité")),
            Part.Text("Profil : **${st.persona.n} ${st.persona.e}**"),
            Part.Joke(pick(listOf("Rendez-vous ${if (r.past) mapOf("week" to "la semaine prochaine", "month" to "le mois prochain", "year" to "l’année prochaine")[p] else mapOf("week" to "à la fin de la semaine", "month" to "à la fin du mois", "year" to "à la fin de l’année")[p]} pour la suite ✌️", "Même heure, même endroit, encore plus de flammes ?", "Garde ce récap au chaud, tu l’as mérité."))), Part.FinalActions), "🎉", "final")
        return out to st
    }
}

private fun Goal.dataUnit() = (data["unit"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
