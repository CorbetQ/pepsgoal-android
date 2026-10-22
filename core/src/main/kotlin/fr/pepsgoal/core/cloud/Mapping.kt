package fr.pepsgoal.core.cloud

import fr.pepsgoal.core.JSON
import fr.pepsgoal.core.SOCIAL_TYPES
import fr.pepsgoal.core.isoOf
import fr.pepsgoal.core.model.*
import fr.pepsgoal.core.msOf
import fr.pepsgoal.core.nowMs
import fr.pepsgoal.core.today
import fr.pepsgoal.core.uid
import kotlinx.serialization.json.*

/** Conversion entre le modèle de l'application et celui du serveur (packages/shared), identique à cloud.js. */
object Mapping {
    val UUID_RE = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    private val DAY = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val HM = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    private fun dayOk(v: String) = DAY.matches(v)

    private fun JsonElement?.numOr(d: Double): Double = (this as? JsonPrimitive)?.let { if (it is JsonNull) null else it.content.toDoubleOrNull() } ?: d
    private fun JsonElement?.strOr(d: String): String = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: d
    private fun JsonElement?.intList(lo: Int, hi: Int): List<Int> = (this as? JsonArray)?.mapNotNull { it.numOr(Double.NaN).takeIf { n -> !n.isNaN() }?.toInt() }?.filter { it in lo..hi } ?: emptyList()
    private fun num(v: Number) = JsonPrimitive(v)
    private fun clamp(s: String, n: Int) = if (s.length > n) s.substring(0, n) else s

    /** Réglages du type, normalisés pour être acceptés par le serveur (mêmes règles que goalData de cloud.js). */
    fun goalData(g: Goal): JsonObject {
        val d = LinkedHashMap(g.data)
        d["unit"] = JsonPrimitive(clamp(d["unit"].strOr(""), 20))
        when (g.type) {
            "routine" -> { d["mode"] = JsonPrimitive(if (d["mode"].strOr("") == "count") "count" else "days"); d["days"] = JsonArray(d["days"].intList(0, 6).map(::num)); d["perWeek"] = num(d["perWeek"].numOr(3.0).let { Math.round(it).toInt() }.coerceIn(1, 7)) }
            // Cap quotidien : enregistré par le serveur comme un objectif chiffré marqué « cap »
            "cap" -> { d["target"] = num(maxOf(0.0001, d["target"].numOr(1.0))); d["endDate"] = JsonPrimitive(""); d["cap"] = JsonPrimitive(true) }
            "objective", "monthly", "group" -> {
                d["target"] = num(maxOf(0.0001, d["target"].numOr(1.0)))
                if (g.type != "monthly") d["endDate"] = JsonPrimitive(d["endDate"].strOr("").takeIf(::dayOk) ?: "")
                if (g.type == "monthly") d["cumulate"] = JsonPrimitive((d["cumulate"] as? JsonPrimitive)?.booleanOrNull ?: false)
            }
            "deadline" -> d["due"] = JsonPrimitive(d["due"].strOr("").takeIf(::dayOk) ?: today())
            "abstinence" -> d["since"] = JsonPrimitive(d["since"].strOr("").takeIf(::dayOk) ?: today())
            "limit" -> { d["max"] = num(maxOf(0.0, d["max"].numOr(1.0))); d["period"] = JsonPrimitive(if (d["period"].strOr("") == "week") "week" else "day") }
            "duration" -> d["minutes"] = num(d["minutes"].numOr(30.0).coerceIn(1.0, 1440.0))
            "measure" -> {
                d["dir"] = JsonPrimitive(if (d["dir"].strOr("") == "down") "down" else "up"); d["timeFmt"] = JsonPrimitive((d["timeFmt"] as? JsonPrimitive)?.booleanOrNull ?: false)
                val t = d["target"].numOr(Double.NaN); d["target"] = if (t.isNaN()) JsonNull else num(t)
            }
            "timed" -> { d["mode"] = JsonPrimitive(d["mode"].strOr("").takeIf { it in setOf("before", "after", "notafter") } ?: "before"); d["time"] = JsonPrimitive(d["time"].strOr("").takeIf { HM.matches(it) } ?: "07:00"); d["days"] = JsonArray(d["days"].intList(0, 6).map(::num)) }
            "project" -> {
                d["due"] = JsonPrimitive(d["due"].strOr("").takeIf(::dayOk) ?: "")
                d["tasks"] = JsonArray(((d["tasks"] as? JsonArray) ?: JsonArray(emptyList())).take(200).mapNotNull { e ->
                    val o = e as? JsonObject ?: return@mapNotNull null
                    buildJsonObject { put("id", clamp(o["id"].strOr(uid()), 64).ifEmpty { uid() }); put("t", clamp(o["t"].strOr(""), 120)); put("done", (o["done"] as? JsonPrimitive)?.booleanOrNull ?: false) }
                })
            }
            "challenge" -> { d["length"] = num(Math.round(d["length"].numOr(30.0)).toInt().coerceIn(2, 365)); d["start"] = JsonPrimitive(d["start"].strOr("").takeIf(::dayOk) ?: today()) }
            "program" -> { d["base"] = num(maxOf(0.0001, d["base"].numOr(1.0))); d["step"] = num(d["step"].numOr(0.0)); d["weeks"] = num(Math.round(d["weeks"].numOr(4.0)).toInt().coerceIn(2, 52)); d["start"] = JsonPrimitive(d["start"].strOr("").takeIf(::dayOk) ?: today()) }
            "duel" -> d["endDate"] = JsonPrimitive(d["endDate"].strOr("").takeIf(::dayOk) ?: today())
            "period" -> {
                val freq = if (d["freq"].strOr("") == "month") "month" else "week"
                var days = d["days"].intList(0, 6); var md = d["mdays"].intList(1, 31).distinct()
                val start = d["start"].strOr("").takeIf { HM.matches(it) } ?: "18:00"
                val endRaw = d["end"].strOr(""); val end = if (HM.matches(endRaw) && endRaw > start) endRaw else "23:59"
                if (freq == "week" && days.isEmpty()) days = listOf(0, 1, 2, 3, 4)
                if (freq == "month" && md.isEmpty()) md = listOf(1)
                d["freq"] = JsonPrimitive(freq); d["days"] = JsonArray(days.map(::num)); d["mdays"] = JsonArray(md.map(::num)); d["start"] = JsonPrimitive(start); d["end"] = JsonPrimitive(end)
            }
        }
        return JsonObject(d)
    }

    fun toServerGoal(g: Goal): JsonObject = buildJsonObject {
        put("id", g.id); put("ownerId", g.userId); put("type", if (g.type == "cap") "objective" else g.type)
        put("title", clamp(g.title, 80).ifBlank { "Objectif" }); put("icon", clamp(g.icon, 16).ifEmpty { "🎯" }); put("note", clamp(g.note, 240))
        put("visibility", if (g.visibility in setOf("private", "friends", "public")) g.visibility else "friends"); put("groupId", JsonNull); put("archived", g.archived)
        put("completedAt", isoOf(g.completedAt)?.let(::JsonPrimitive) ?: JsonNull); put("failedAt", isoOf(g.failedAt)?.let(::JsonPrimitive) ?: JsonNull)
        put("data", goalData(g))
        put("memberIds", JsonArray(if (g.type in SOCIAL_TYPES) (g.members ?: emptyList()).filter { UUID_RE.matches(it) }.map(::JsonPrimitive) else emptyList()))
        put("buddyId", g.buddy?.takeIf { UUID_RE.matches(it) }?.let(::JsonPrimitive) ?: JsonNull); put("buddyVerify", g.buddyVerify); put("buddyMiss", g.buddyMiss)
        put("createdAt", isoOf(g.createdAt.takeIf { it > 0 } ?: nowMs())!!)
    }

    fun fromServerGoal(sg: JsonObject): Goal {
        val data = LinkedHashMap<String, JsonElement>((sg["data"] as? JsonObject) ?: emptyMap())
        val raw = sg["type"].strOr("routine"); val owner = sg["ownerId"].strOr("")
        val type = if (raw == "objective" && (data["cap"] as? JsonPrimitive)?.booleanOrNull == true) "cap" else raw
        val g = Goal(sg["id"].strOr(uid()), owner, type, sg["title"].strOr("Objectif"), sg["icon"].strOr("🎯"), sg["note"].strOr(""), sg["visibility"].strOr("friends"),
            msOf(sg["createdAt"].strOr("")) ?: nowMs(), (sg["archived"] as? JsonPrimitive)?.booleanOrNull ?: false,
            msOf((sg["completedAt"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content), msOf((sg["failedAt"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content),
            null, (sg["buddyId"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content, (sg["buddyVerify"] as? JsonPrimitive)?.booleanOrNull ?: false,
            (sg["buddyMiss"] as? JsonPrimitive)?.booleanOrNull ?: false, data)
        if (type in SOCIAL_TYPES) { val ids = (sg["memberIds"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList(); g.members = (if (ids.isNotEmpty()) ids else listOf(owner)).toMutableList() }
        if (!data.containsKey("days")) data["days"] = JsonArray(emptyList())
        if (!data.containsKey("perWeek")) data["perWeek"] = JsonPrimitive(3)
        if (!data.containsKey("milestones")) data["milestones"] = JsonArray(emptyList())
        if (!data.containsKey("monthsDone")) data["monthsDone"] = JsonArray(emptyList())
        return g
    }

    fun toServerCheckin(c: Checkin): JsonObject = buildJsonObject {
        put("id", c.id); put("goalId", c.goalId); put("userId", c.userId); put("date", c.date); put("kind", c.kind); put("value", c.value)
        put("points", Math.round((if (c.pending) (c.ptsPending ?: 0) else c.pts).toDouble()).toInt().coerceIn(0, 500)); put("pending", c.pending)
        put("taskId", c.taskId?.let { JsonPrimitive(clamp(it, 64)) } ?: JsonNull); put("createdAt", isoOf(c.at.takeIf { it > 0 } ?: nowMs())!!)
    }

    fun fromServerCheckin(sc: JsonObject): Checkin {
        val pending = (sc["pending"] as? JsonPrimitive)?.booleanOrNull ?: false; val points = sc["points"].numOr(0.0).toInt()
        return Checkin(sc["id"].strOr(uid()), sc["goalId"].strOr(""), sc["userId"].strOr(""), sc["date"].strOr(today()), sc["kind"].strOr("done"), sc["value"].numOr(0.0),
            if (pending) 0 else points, pending, if (pending) points else null, (sc["taskId"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content, msOf(sc["createdAt"].strOr("")) ?: nowMs())
    }

    /* ---------- réglages du compte ---------- */
    private val USER_KEYS = listOf("tutorialDone", "goalGroups", "goalGroupOf", "frozenDays", "restDays", "vacations", "hardDays", "essentials", "flameSkin", "shop", "places", "rewards", "milestones", "badges", "perfectDays", "favGoals", "hiddenSuggest", "aprilFool", "badgeFams")
    private val SETTING_KEYS = listOf("browser", "defaultVis", "bg", "accentCustom", "noSuggest", "weather", "wxCard", "glassBlur", "hideOngoing", "fx")

    private fun strArr(l: List<String>) = JsonArray(l.map(::JsonPrimitive))

    fun toServerSettings(u: User): JsonObject {
        val s = u.settings
        val extra = LinkedHashMap<String, JsonElement>(s.extraRaw ?: emptyMap())
        extra["tutorialDone"] = JsonPrimitive(u.tutorialDone)
        extra["goalGroups"] = JSON.encodeToJsonElement(u.goalGroups)
        extra["goalGroupOf"] = JsonObject(u.goalGroupOf.mapValues { JsonPrimitive(it.value) })
        extra["frozenDays"] = strArr(u.frozenDays); extra["milestones"] = strArr(u.milestones)
        extra["restDays"] = strArr(u.restDays.filter { it >= fr.pepsgoal.core.ymd(fr.pepsgoal.core.todayDate().minusDays(400)) })
        extra["hardDays"] = strArr(u.hardDays.filter { it >= fr.pepsgoal.core.ymd(fr.pepsgoal.core.todayDate().minusDays(400)) })
        extra["essentials"] = strArr(u.essentials); extra["flameSkin"] = JsonPrimitive(u.flameSkin)
        extra["shop"] = buildJsonObject { put("owned", strArr(u.shopOwned)); put("spent", u.shopSpent) }
        extra["places"] = JSON.encodeToJsonElement(u.places)
        extra["rewards"] = JSON.encodeToJsonElement(u.rewards.filter { it.claimedAt == null || it.claimedAt!! > fr.pepsgoal.core.nowMs() - 400L * 86_400_000 })
        extra["vacations"] = strArr(u.vacations.filter { it.substringAfter('|') >= fr.pepsgoal.core.ymd(fr.pepsgoal.core.todayDate().minusDays(400)) })
        u.badges?.let { extra["badges"] = strArr(it) } ?: extra.remove("badges")
        u.badgeFams?.let { extra["badgeFams"] = strArr(it) } ?: extra.remove("badgeFams")
        extra["perfectDays"] = strArr(u.perfectDays); extra["favGoals"] = strArr(u.favGoals); extra["hiddenSuggest"] = strArr(u.hiddenSuggest)
        u.aprilFool?.let { extra["aprilFool"] = JsonPrimitive(it) }
        extra["browser"] = JsonPrimitive(s.browser); extra["defaultVis"] = JsonPrimitive(s.defaultVis); extra["bg"] = JsonPrimitive(s.bg)
        extra["accentCustom"] = s.accentCustom?.let(::strArr) ?: JsonNull
        extra["noSuggest"] = JsonPrimitive(s.noSuggest); extra["weather"] = s.weather?.let { JSON.encodeToJsonElement(it) } ?: JsonNull
        extra["wxCard"] = JsonPrimitive(s.wxCard); extra["glassBlur"] = JsonPrimitive(s.glassBlur); extra["fx"] = JsonPrimitive(s.fx); extra["hideOngoing"] = JsonPrimitive(s.hideOngoing)
        return buildJsonObject {
            put("theme", if (s.theme in setOf("system", "light", "dark", "oled", "weather")) s.theme else "system"); put("accent", s.accent.coerceIn(0, 31)); put("social", s.social)
            put("defaultVisibility", if (s.defaultVis in setOf("private", "friends", "public")) s.defaultVis else "friends")
            put("morning", buildJsonObject { put("on", s.morning.on); put("time", s.morning.time) }); put("evening", buildJsonObject { put("on", s.evening.on); put("time", s.evening.time) })
            put("extra", JsonObject(extra))
        }
    }

    fun applyServerSettings(u: User, st: JsonObject?) {
        if (st == null) return
        val s = u.settings
        s.theme = st["theme"].strOr(s.theme); s.accent = st["accent"].numOr(s.accent.toDouble()).toInt(); s.social = (st["social"] as? JsonPrimitive)?.booleanOrNull ?: s.social
        (st["morning"] as? JsonObject)?.let { s.morning = Reminder((it["on"] as? JsonPrimitive)?.booleanOrNull ?: true, it["time"].strOr("08:30")) }
        (st["evening"] as? JsonObject)?.let { s.evening = Reminder((it["on"] as? JsonPrimitive)?.booleanOrNull ?: true, it["time"].strOr("20:30")) }
        val x = (st["extra"] as? JsonObject) ?: JsonObject(emptyMap())
        s.extraRaw = JsonObject(x.filterKeys { it !in USER_KEYS && it !in SETTING_KEYS })
        fun bool(k: String) = (x[k] as? JsonPrimitive)?.booleanOrNull
        fun strs(k: String) = (x[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }?.toMutableList()
        bool("browser")?.let { s.browser = it }
        x["defaultVis"]?.let { s.defaultVis = it.strOr(s.defaultVis) }
        x["bg"]?.let { s.bg = it.strOr(s.bg) }
        if (x.containsKey("accentCustom")) s.accentCustom = strs("accentCustom")
        bool("noSuggest")?.let { s.noSuggest = it }
        if (x.containsKey("weather")) s.weather = try { (x["weather"] as? JsonObject)?.let { JSON.decodeFromJsonElement(Place.serializer(), it) } } catch (e: Exception) { null }
        bool("wxCard")?.let { s.wxCard = it }
        x["glassBlur"]?.let { s.glassBlur = it.strOr("mid") }
        x["fx"]?.let { s.fx = it.strOr("") }
        bool("hideOngoing")?.let { s.hideOngoing = it }
        bool("tutorialDone")?.let { u.tutorialDone = it }
        (x["goalGroups"] as? JsonArray)?.let { u.goalGroups = try { JSON.decodeFromJsonElement<List<GoalGroup>>(it).toMutableList() } catch (e: Exception) { u.goalGroups } }
        (x["goalGroupOf"] as? JsonObject)?.let { o -> u.goalGroupOf = o.mapValues { it.value.strOr("") }.filterValues { it.isNotEmpty() }.toMutableMap() }
        strs("frozenDays")?.let { u.frozenDays = it }
        strs("restDays")?.let { u.restDays = it }
        strs("vacations")?.let { u.vacations = it }
        strs("hardDays")?.let { u.hardDays = it }
        strs("essentials")?.let { u.essentials = it }
        x["flameSkin"]?.let { u.flameSkin = it.strOr("classic") }
        (x["shop"] as? JsonObject)?.let { o ->
            (o["owned"] as? JsonArray)?.let { a -> u.shopOwned = a.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toMutableList() }
            (o["spent"] as? JsonPrimitive)?.intOrNull?.let { u.shopSpent = it }
        }
        (x["rewards"] as? JsonArray)?.let { u.rewards = try { JSON.decodeFromJsonElement<List<Reward>>(it).toMutableList() } catch (e: Exception) { u.rewards } }
        (x["places"] as? JsonArray)?.let { u.places = try { JSON.decodeFromJsonElement<List<SavedPlace>>(it).toMutableList() } catch (e: Exception) { u.places } }
        strs("milestones")?.let { u.milestones = it }
        if (x.containsKey("badges")) u.badges = strs("badges")
        if (x.containsKey("badgeFams")) u.badgeFams = strs("badgeFams")
        strs("perfectDays")?.let { u.perfectDays = it }
        strs("favGoals")?.let { u.favGoals = it }
        strs("hiddenSuggest")?.let { u.hiddenSuggest = it }
        x["aprilFool"]?.let { u.aprilFool = (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
    }

    fun mapAvatar(a: JsonObject?): Avatar {
        val o = a ?: JsonObject(emptyMap())
        return Avatar(o["kind"].strOr("initials"), o["color"].numOr(0.0).toInt(), o["emoji"].strOr("😎").ifEmpty { "😎" }, o["picto"].strOr("flameo").ifEmpty { "flameo" }, o["photoUrl"].strOr(""))
    }

    fun banOf(banPermanent: Boolean, banUntil: String?, reason: String?): Ban? {
        val until = msOf(banUntil)
        return if (banPermanent || (until != null && until > nowMs())) Ban(if (banPermanent) null else until, reason ?: "", nowMs()) else null
    }

    fun pollFromServer(x: JsonObject): Poll {
        val mine = (x["mine"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
        return Poll((x["multi"] as? JsonPrimitive)?.booleanOrNull ?: false, msOf(x["endsAt"].strOr("")) ?: nowMs(), x["voters"].numOr(0.0).toInt(),
            ((x["options"] as? JsonArray) ?: JsonArray(emptyList())).map { e -> val o = e.jsonObject; PollOption(o["id"].strOr(""), o["text"].strOr(""), o["votes"].numOr(0.0).toInt(), o["id"].strOr("") in mine) }.toMutableList())
    }
}
