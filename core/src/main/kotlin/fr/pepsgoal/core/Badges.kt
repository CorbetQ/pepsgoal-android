package fr.pepsgoal.core

import fr.pepsgoal.core.model.*

/** Badges à paliers (Bronze → Diamant), calculés sur l'appareil à partir des données synchronisées. */
val BADGE_TIERS = listOf("Bronze" to "#D08A4F", "Argent" to "#B9C3D6", "Or" to "#FFC83D", "Platine" to "#7FE3D9", "Diamant" to "#B48CFF")

class BadgeFamily(val k: String, val n: String, val e: String, val d: (Int) -> String, val t: List<Int>, val v: (Map<String, Int>) -> Int)

val BADGE_FAMILIES = listOf(
    BadgeFamily("streak", "Série", "🔥", { n -> "Atteindre une série de ${pl(n, "jour", "jours")}" }, listOf(3, 7, 30, 100, 365)) { it["streak"]!! },
    BadgeFamily("checks", "Validations", "✅", { n -> pl(n, "validation", "validations") }, listOf(1, 25, 100, 500, 1000)) { it["checks"]!! },
    BadgeFamily("days", "Assiduité", "📅", { n -> pl(n, "jour actif", "jours actifs") }, listOf(7, 30, 100, 250, 365)) { it["days"]!! },
    BadgeFamily("perfect", "Journées parfaites", "🌟", { n -> pl(n, "journée parfaite", "journées parfaites") }, listOf(1, 5, 20, 50, 100)) { it["perfect"]!! },
    BadgeFamily("finish", "Accomplissements", "🏁", { n -> "${pl(n, "objectif accompli", "objectifs accomplis")} (caps, challenges, mois réussis…)" }, listOf(1, 5, 15, 30, 60)) { it["finish"]!! },
    BadgeFamily("ontime", "Dans les temps", "⏰", { n -> "${pl(n, "deadline bouclée", "deadlines bouclées")} à temps" }, listOf(1, 5, 15, 30)) { it["ontime"]!! },
    BadgeFamily("team", "Esprit d’équipe", "🤝", { n -> "${pl(n, "défi à plusieurs réussi", "défis à plusieurs réussis")} (groupe ou duel gagné)" }, listOf(1, 3, 10, 25)) { it["team"]!! },
    BadgeFamily("friends", "Tribu", "👥", { n -> pl(n, "ami", "amis") }, listOf(1, 3, 10, 25)) { it["friends"]!! },
    BadgeFamily("cheers", "Encouragé", "💖", { n -> pl(n, "encouragement reçu", "encouragements reçus") }, listOf(10, 50, 200, 500)) { it["cheers"]!! },
    BadgeFamily("shield", "Bouclier", "🧊", { n -> pl(n, "gel de série utilisé", "gels de série utilisés") }, listOf(1, 10, 30)) { it["shield"]!! },
    BadgeFamily("variety", "Explorateur", "🧭", { n -> "$n types d’objectifs différents" }, listOf(3, 6, 10, 14)) { it["variety"]!! },
    BadgeFamily("level", "Flammes", "🏆", { n -> "${fmtNum(n)} flammes gagnées" }, listOf(100, 500, 1500, 3000, 6000)) { it["pts"]!! },
    BadgeFamily("early", "Lève-tôt", "🌅", { n -> "${pl(n, "validation", "validations")} avant 8 h" }, listOf(5, 25, 100, 250)) { it["early"]!! },
    BadgeFamily("night", "Oiseau de nuit", "🦉", { n -> "${pl(n, "validation", "validations")} après 22 h" }, listOf(5, 25, 100, 250)) { it["night"]!! },
    BadgeFamily("weekend", "Week-end actif", "🎉", { n -> "${pl(n, "validation", "validations")} le week-end" }, listOf(10, 50, 150, 400)) { it["weekend"]!! },
    BadgeFamily("period", "Ponctuel", "🕰️", { n -> pl(n, "période tenue", "périodes tenues") }, listOf(3, 15, 50, 150)) { it["period"]!! },
    BadgeFamily("builder", "Bâtisseur", "🧱", { n -> pl(n, "étape de projet franchie", "étapes de projet franchies") }, listOf(5, 25, 100, 300)) { it["builder"]!! },
    BadgeFamily("stories", "Conteur", "📸", { n -> pl(n, "story publiée", "stories publiées") }, listOf(1, 10, 30, 100)) { it["stories"]!! },
    BadgeFamily("writer", "Plume", "✍️", { n -> "${pl(n, "publication", "publications")} dans le feed" }, listOf(1, 10, 50, 150)) { it["writer"]!! },
    // Nouveaux badges (4.5)
    BadgeFamily("loyal", "Fidèle", "💎", { n -> "Une série de ${pl(n, "jour", "jours")} sur un même objectif" }, listOf(7, 30, 100, 365)) { it["loyal"]!! },
    BadgeFamily("mini", "Petits pas", "🌱", { n -> pl(n, "version mini validée", "versions mini validées") }, listOf(1, 10, 30, 100)) { it["mini"]!! },
    BadgeFamily("trial", "Défi relevé", "🏅", { n -> pl(n, "défi de N jours réussi", "défis de N jours réussis") }, listOf(1, 3, 10)) { it["trial"]!! },
    BadgeFamily("chain", "Enchaîneur", "🔗", { n -> "${pl(n, "validation", "validations")} d’habitudes enchaînées" }, listOf(10, 50, 200, 500)) { it["chain"]!! },
    BadgeFamily("ambition", "Ambitieux", "🎯", { n -> pl(n, "objectif créé", "objectifs créés") }, listOf(5, 15, 30, 60)) { it["ambition"]!! },
    BadgeFamily("supporter", "Supporter", "📣", { n -> pl(n, "réaction donnée", "réactions données") }, listOf(10, 50, 200, 500)) { it["supporter"]!! },
    BadgeFamily("chatty", "Bavard", "💬", { n -> pl(n, "commentaire écrit", "commentaires écrits") }, listOf(5, 25, 100, 300)) { it["chatty"]!! },
    BadgeFamily("collector", "Collectionneur", "🛍️", { n -> pl(n, "article de la boutique", "articles de la boutique") }, listOf(1, 5, 15, 30)) { it["collector"]!! },
    BadgeFamily("dawn", "Aurore", "🌄", { n -> "${pl(n, "validation", "validations")} avant 6 h" }, listOf(5, 25, 100)) { it["dawn"]!! },
)

/** Flammes offertes à chaque palier débloqué (Bronze → Diamant), à dépenser dans la boutique. */
val BADGE_REWARDS = listOf(25, 50, 100, 200, 400)
val BADGE_FAMS_V20 = listOf("streak", "checks", "days", "perfect", "finish", "ontime", "team", "friends", "cheers", "shield", "variety", "level")

class BadgeState(val f: BadgeFamily, val v: Int, val tier: Int, val next: Int?, val pct: Double)

object Badges {
    fun stats(pg: Pg, id: String): Map<String, Int> {
        val db = pg.db; val u = pg.userById(id)
        val cs = pg.userCheckins(id).filter { pg.isWin(it) }; val gs = db.goals.filter { it.userId == id }
        val perfect = ((u?.perfectDays ?: emptyList()) + db.perfectLog.keys.filter { it.startsWith("$id|") }.map { it.split("|")[1] }).toSet().size
        val hour = { c: fr.pepsgoal.core.model.Checkin -> if (c.at > 0) localDateTime(c.at).hour else 12 }
        val periodIds = gs.filter { it.type == "period" }.map { it.id }.toSet()
        return mapOf(
            "streak" to pg.bestStreak(id), "checks" to cs.size, "days" to pg.activeDays(id).size, "perfect" to perfect,
            "finish" to gs.count { it.completedAt != null && it.failedAt == null } + gs.sumOf { it.monthsDoneSize() },
            "ontime" to gs.count { it.type == "deadline" && it.completedAt != null && ymd(it.completedAt!!) <= it.dueStr() },
            "team" to db.goals.count { (it.type == "group" && it.completedAt != null && pg.isMember(it, id)) || (it.type == "duel" && it.winnerId() == id) },
            "friends" to pg.friendsOf(id).size, "cheers" to (u?.cheersIn ?: 0), "shield" to (u?.frozenDays?.size ?: 0),
            "variety" to gs.map { it.type }.toSet().size, "pts" to pg.points(id),
            "early" to cs.count { hour(it) < 8 }, "night" to cs.count { hour(it) >= 22 },
            "weekend" to cs.count { wdIdx(parseYmd(it.date)) >= 5 }, "period" to cs.count { it.goalId in periodIds },
            "builder" to cs.count { it.taskId != null }, "stories" to db.stories.count { it.userId == id },
            "writer" to db.posts.count { it.userId == id && it.kind != "milestone" },
            "loyal" to (gs.filter { !it.archived }.maxOfOrNull { pg.goalStreak(it) } ?: 0),
            "mini" to cs.count { it.kind == "done" && it.value < 1.0 },
            "trial" to gs.count { it.bool("trialDone") && (pg.trialState(it)?.success == true) },
            "chain" to cs.count { c -> gs.firstOrNull { it.id == c.goalId }?.after != null },
            "ambition" to gs.size,
            "supporter" to db.posts.count { it.userId != id && it.likes.contains(id) },
            "chatty" to db.posts.sumOf { p -> p.comments.count { it.userId == id } },
            "collector" to (u?.shopOwned?.size ?: 0),
            "dawn" to cs.count { hour(it) < 6 }
        )
    }

    fun state(pg: Pg, id: String): List<BadgeState> {
        val x = stats(pg, id)
        return BADGE_FAMILIES.map { f ->
            val v = f.v(x); val tier = f.t.count { v >= it } - 1; val next = f.t.getOrNull(tier + 1)
            val prev = if (tier >= 0) f.t[tier] else 0
            BadgeState(f, v, tier, next, if (next != null) ((v - prev).toDouble() / (next - prev)).coerceIn(0.0, 1.0) else 1.0)
        }
    }

    fun keys(st: List<BadgeState>) = st.flatMap { b -> (0..b.tier).map { "${b.f.k}:$it" } }
    fun total() = BADGE_FAMILIES.sumOf { it.t.size }
}

private fun fr.pepsgoal.core.model.Goal.monthsDoneSize() = (data["monthsDone"] as? kotlinx.serialization.json.JsonArray)?.size ?: 0
private fun fr.pepsgoal.core.model.Goal.dueStr() = (data["due"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
private fun fr.pepsgoal.core.model.Goal.winnerId() = (data["winner"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
