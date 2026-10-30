package fr.pepsgoal.core

import java.time.LocalDate

/* ---------------------------------------------------------------- Saisons à thème */

/**
 * Saison à thème : 6 semaines avec un nom, une scène et un passe de récompenses à paliers, gagnés avec les flammes
 * de la saison (validations faites pendant la saison, plus les quêtes de la semaine). Le calendrier est fixe et calculé
 * sur l'appareil : la saison 1 commence le lundi 5 janvier 2026, puis les thèmes tournent.
 */
class ThemeSeason(val key: String, val name: String, val emoji: String, val scene: String, val colors: List<String>,
                  /** Tenue du compagnon offerte par le passe, et les deux stickers de la saison. */
                  val outfit: String, val stickers: List<String>)

val THEME_SEASONS = listOf(
    ThemeSeason("givre", "Nuit de givre", "❄️", "icefield", listOf("#E4F4FF", "#9FD8FF", "#B6B4FF"), "pull", listOf("flocon", "chocolat")),
    ThemeSeason("aurore", "Aurore boréale", "🌌", "borealis", listOf("#7DF9FF", "#3DFF8A", "#B98CFF"), "cape", listOf("aurore", "renne")),
    ThemeSeason("cerisier", "Cerisiers en fleurs", "🌸", "sakura", listOf("#FFE0EC", "#FFA8C8", "#FF7EB3"), "kimono", listOf("sakura", "the")),
    ThemeSeason("cosmos", "Cap sur le cosmos", "🪐", "planet", listOf("#C6B6FF", "#7B5CFF", "#FF5CD6"), "astro", listOf("fusee", "ovni")),
    ThemeSeason("lagon", "Lagon turquoise", "🏝️", "lagoon", listOf("#B8FFF5", "#3DE0D0", "#1FA2FF"), "hawaii", listOf("coco", "vague")),
    ThemeSeason("loire", "Été sur la Loire", "⛵", "loire", listOf("#FFE45C", "#FFB000", "#22D3FF"), "marin", listOf("toue", "glace")),
    ThemeSeason("anjou", "Vendanges en Anjou", "🍇", "angevine", listOf("#FFD9B8", "#D25DD0", "#8C7DFF"), "vigneron", listOf("raisin", "tuffeau")),
    ThemeSeason("braise", "Braises d’automne", "🍂", "autumnwood", listOf("#FFC36B", "#E2711D", "#8B3A1E"), "cire", listOf("feuille", "chataigne")),
)

val SEASON_EPOCH: LocalDate = LocalDate.of(2026, 1, 5)
const val SEASON_DAYS = 42

/** Une saison du calendrier : numéro (1, 2…), thème, premier et dernier jour (lundi, dimanche six semaines plus tard). */
class SeasonInfo(val n: Int, val theme: ThemeSeason, val start: LocalDate, val end: LocalDate) {
    /** Semaine en cours de la saison (1 à 6) pour le jour [d]. */
    fun week(d: LocalDate = todayDate()) = (daysBetween(start, d) / 7 + 1).toInt().coerceIn(1, 6)
    fun daysLeft(d: LocalDate = todayDate()) = daysBetween(d, end).toInt().coerceAtLeast(0)
    fun contains(date: String) = date >= ymd(start) && date <= ymd(end)
}

/** Saison qui contient le jour [d]. */
fun themeSeasonAt(d: LocalDate = todayDate()): SeasonInfo {
    val n = Math.floorDiv(daysBetween(SEASON_EPOCH, d), SEASON_DAYS.toLong()).toInt()
    val start = SEASON_EPOCH.plusDays(n.toLong() * SEASON_DAYS)
    return SeasonInfo(n + 1, THEME_SEASONS[Math.floorMod(n, THEME_SEASONS.size)], start, start.plusDays(SEASON_DAYS - 1L))
}

/* ---------------------------------------------------------------- Passe de saison */

/**
 * Palier du passe : [need] flammes de saison. [kind] : « flames » (flammes bonus ajoutées au porte-monnaie),
 * « sticker », « frame » (cadre d'avatar), « outfit » (tenue du compagnon) ou « bg » (fond) ; [key] : article débloqué.
 */
class PassTier(val i: Int, val need: Int, val kind: String, val key: String, val amount: Int, val label: String, val emoji: String) {
    /** Clé d'achat de l'article (« sticker:… », « frame:… », « outfit:… », « bg:… »), null pour des flammes. */
    val item: String? get() = if (kind == "flames") null else "$kind:$key"
}

val PASS_NEEDS = listOf(60, 140, 240, 360, 500, 660, 840, 1040, 1260, 1500)

/** Les 10 paliers de la saison [t]. */
fun passTiers(t: ThemeSeason): List<PassTier> = listOf(
    PassTier(0, PASS_NEEDS[0], "flames", "", 20, "+20 flammes", "🔥"),
    PassTier(1, PASS_NEEDS[1], "sticker", t.stickers[0], 0, "Sticker de saison", "🏷️"),
    PassTier(2, PASS_NEEDS[2], "flames", "", 30, "+30 flammes", "🔥"),
    PassTier(3, PASS_NEEDS[3], "frame", t.key, 0, "Cadre d’avatar « ${t.name} »", "🖼️"),
    PassTier(4, PASS_NEEDS[4], "flames", "", 40, "+40 flammes", "🔥"),
    PassTier(5, PASS_NEEDS[5], "outfit", t.outfit, 0, "Tenue « ${petOutfit(t.outfit)?.name ?: t.outfit} »", petOutfit(t.outfit)?.emoji ?: "👕"),
    PassTier(6, PASS_NEEDS[6], "flames", "", 50, "+50 flammes", "🔥"),
    PassTier(7, PASS_NEEDS[7], "sticker", t.stickers[1], 0, "Second sticker de saison", "🏷️"),
    PassTier(8, PASS_NEEDS[8], "bg", t.scene, 0, "Fond « ${SHOP_BGS.firstOrNull { it.key == t.scene }?.name ?: t.name} »", "🏞️"),
    PassTier(9, PASS_NEEDS[9], "flames", "", 100, "+100 flammes", "🏆"),
)

private fun Pg.seasonMarks(s: SeasonInfo) = me()?.shopClaimed?.filter { it.startsWith("tseason:${s.n}:") } ?: emptyList()

/** Flammes de la saison : celles des validations faites pendant la saison, plus les quêtes de la semaine réussies. */
fun Pg.seasonFlames(s: SeasonInfo = themeSeasonAt()): Int {
    val id = meId()
    val pts = userCheckins(id).filter { s.contains(it.date) }.sumOf { it.pts }
    val quests = seasonMarks(s).count { it.contains(":wq:") } * WEEK_QUEST_BONUS
    return pts + quests
}

fun Pg.tierClaimed(s: SeasonInfo, i: Int) = "tseason:${s.n}:tier:$i" in seasonMarks(s)

/** Paliers atteints et pas encore récupérés. */
fun Pg.tiersToClaim(s: SeasonInfo = themeSeasonAt()): List<PassTier> { val f = seasonFlames(s); return passTiers(s.theme).filter { f >= it.need && !tierClaimed(s, it.i) } }

/** Récupère la récompense du palier [i] de la saison en cours. Renvoie un message d'erreur, ou null. */
fun Pg.claimTier(i: Int, s: SeasonInfo = themeSeasonAt()): String? {
    val u = me() ?: return null
    val tier = passTiers(s.theme).getOrNull(i) ?: return null
    if (tierClaimed(s, i)) return "Déjà récupéré."
    if (seasonFlames(s) < tier.need) return "Encore ${tier.need - seasonFlames(s)} flammes de saison pour ce palier."
    u.shopClaimed = trimClaimed(u.shopClaimed + "tseason:${s.n}:tier:$i")
    if (tier.kind == "flames") u.shopBonus += tier.amount
    else tier.item?.let { if (it !in u.shopOwned) u.shopOwned.add(it) }
    store.commit(); return null
}

/** Possède un article débloqué par le passe ou acheté (« outfit:pull », « frame:givre », « sticker:flocon »). */
fun Pg.ownsItem(key: String) = me()?.shopOwned?.contains(key) == true

/* ---------------------------------------------------------------- Quêtes de la semaine */

const val WEEK_QUEST_BONUS = 40

/** Quête de la semaine : clé, intitulé, emoji, cible. */
class WeekQuestDef(val key: String, val title: String, val emoji: String, val target: Int)

val WEEK_QUESTS = listOf(
    WeekQuestDef("checks", "Valide 12 fois cette semaine", "✅", 12),
    WeekQuestDef("days", "5 journées actives", "📅", 5),
    WeekQuestDef("perfect", "2 journées parfaites", "🌟", 2),
    WeekQuestDef("early", "3 validations avant 9 h", "🌅", 3),
    WeekQuestDef("variety", "4 objectifs différents validés", "🎨", 4),
    WeekQuestDef("flames", "Gagne 150 flammes", "🔥", 150),
    WeekQuestDef("weekend", "Valide samedi et dimanche", "🛋️", 2),
)

class WeekQuest(val def: WeekQuestDef, val progress: Int, val claimed: Boolean) {
    val done get() = progress >= def.target
}

/** Les 3 quêtes de la semaine qui contient [d] : tirées du lot, toujours les mêmes pour une semaine donnée. */
fun weekQuestDefs(d: LocalDate = todayDate()): List<WeekQuestDef> {
    val ws = weekStart(d); var x = (ws.toEpochDay() * 2654435761L) and 0x7FFFFFFF
    val pool = WEEK_QUESTS.toMutableList(); val out = mutableListOf<WeekQuestDef>()
    repeat(3) { out += pool.removeAt((x % pool.size).toInt()); x = (x * 1103515245L + 12345L) and 0x7FFFFFFF }
    return out
}

/** Quêtes de la semaine en cours, avec la progression calculée sur les validations de la semaine. */
fun Pg.weekQuests(d: LocalDate = todayDate()): List<WeekQuest> {
    val u = me() ?: return emptyList(); val ws = weekStart(d); val we = ws.plusDays(6)
    val from = ymd(ws); val to = ymd(we)
    val cs = userCheckins(u.id).filter { it.date in from..to && isWin(it) }
    val s = themeSeasonAt(d)
    return weekQuestDefs(d).map { q ->
        val n = when (q.key) {
            "checks" -> cs.size
            "days" -> cs.map { it.date }.toSet().size
            "perfect" -> u.perfectDays.count { it in from..to }
            "early" -> cs.count { it.at > 0 && localDateTime(it.at).hour < 9 }
            "variety" -> cs.map { it.goalId }.toSet().size
            "flames" -> cs.sumOf { it.pts }
            "weekend" -> listOf(ws.plusDays(5), ws.plusDays(6)).count { day -> cs.any { it.date == ymd(day) } }
            else -> 0
        }
        WeekQuest(q, minOf(n, q.target), "tseason:${s.n}:wq:$from:${q.key}" in seasonMarks(s))
    }
}

/** Récupère le bonus d'une quête de la semaine réussie (+[WEEK_QUEST_BONUS] flammes de saison). Message d'erreur ou null. */
fun Pg.claimWeekQuest(key: String): String? {
    val u = me() ?: return null
    val q = weekQuests().firstOrNull { it.def.key == key } ?: return null
    if (q.claimed) return "Déjà récupéré."
    if (!q.done) return "Quête pas encore terminée."
    u.shopClaimed = trimClaimed(u.shopClaimed + "tseason:${themeSeasonAt().n}:wq:${ymd(weekStart(todayDate()))}:$key")
    store.commit(); return null
}
