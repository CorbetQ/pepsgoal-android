package fr.pepsgoal.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/* Accès typés aux réglages propres à chaque type d'objectif (rangés dans Goal.data, comme sur le serveur). */

fun Goal.num(k: String, d: Double = 0.0): Double = (data[k] as? JsonPrimitive)?.let { if (it.isString) it.content.replace(',', '.').toDoubleOrNull() else it.doubleOrNull } ?: d
fun Goal.numOrNull(k: String): Double? = (data[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { if (it.isString) it.content.toDoubleOrNull() else it.doubleOrNull }
fun Goal.str(k: String, d: String = ""): String = (data[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: d
fun Goal.strOrNull(k: String): String? = (data[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
fun Goal.bool(k: String): Boolean = (data[k] as? JsonPrimitive)?.booleanOrNull ?: false
fun Goal.ints(k: String): List<Int> = (data[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull ?: (it as? JsonPrimitive)?.doubleOrNull?.toInt() } ?: emptyList()
fun Goal.strs(k: String): List<String> = (data[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content } ?: emptyList()

fun Goal.set(k: String, v: Number?) { data[k] = if (v == null) JsonNull else JsonPrimitive(v) }
fun Goal.set(k: String, v: String?) { data[k] = if (v == null) JsonNull else JsonPrimitive(v) }
fun Goal.set(k: String, v: Boolean) { data[k] = JsonPrimitive(v) }
fun Goal.setInts(k: String, v: List<Int>) { data[k] = JsonArray(v.map { JsonPrimitive(it) }) }
fun Goal.setStrs(k: String, v: List<String>) { data[k] = JsonArray(v.map { JsonPrimitive(it) }) }

var Goal.target: Double get() = num("target", 1.0); set(v) = set("target", v)
val Goal.targetOrNull: Double? get() = numOrNull("target")
var Goal.unit: String get() = str("unit"); set(v) = set("unit", v)
var Goal.mode: String get() = str("mode", if (type == "timed") "before" else "days"); set(v) = set("mode", v)
var Goal.days: List<Int> get() = ints("days"); set(v) = setInts("days", v)
var Goal.mdays: List<Int> get() = ints("mdays"); set(v) = setInts("mdays", v)
var Goal.perWeek: Int get() = num("perWeek", 3.0).toInt(); set(v) = set("perWeek", v)
var Goal.endDate: String get() = str("endDate"); set(v) = set("endDate", v)
var Goal.due: String get() = str("due"); set(v) = set("due", v)
var Goal.since: String get() = str("since"); set(v) = set("since", v)
var Goal.cumulate: Boolean get() = bool("cumulate"); set(v) = set("cumulate", v)
var Goal.max: Double get() = num("max", 1.0); set(v) = set("max", v)
var Goal.period: String get() = str("period", "day"); set(v) = set("period", v)
var Goal.minutes: Double get() = num("minutes", 30.0); set(v) = set("minutes", v)
var Goal.dir: String get() = str("dir", "up"); set(v) = set("dir", v)
var Goal.timeFmt: Boolean get() = bool("timeFmt"); set(v) = set("timeFmt", v)
var Goal.time: String get() = str("time", "07:00"); set(v) = set("time", v)
var Goal.length: Int get() = num("length", 30.0).toInt(); set(v) = set("length", v)
var Goal.start: String get() = str("start"); set(v) = set("start", v)
var Goal.end: String get() = str("end"); set(v) = set("end", v)
var Goal.freq: String get() = str("freq", "week"); set(v) = set("freq", v)
var Goal.base: Double get() = num("base", 1.0); set(v) = set("base", v)
var Goal.step: Double get() = num("step", 0.0); set(v) = set("step", v)
var Goal.weeks: Int get() = num("weeks", 4.0).toInt(); set(v) = set("weeks", v)
var Goal.winner: String? get() = strOrNull("winner"); set(v) = set("winner", v)
var Goal.targetHit: String? get() = strOrNull("targetHit"); set(v) = set("targetHit", v)
var Goal.failDay: Int? get() = numOrNull("failDay")?.toInt(); set(v) = set("failDay", v)
var Goal.monthsDone: List<String> get() = strs("monthsDone"); set(v) = setStrs("monthsDone", v)
var Goal.weeksDone: List<Int> get() = ints("weeksDone"); set(v) = setInts("weeksDone", v)
var Goal.milestones: List<Int> get() = ints("milestones"); set(v) = setInts("milestones", v)

class Task(val id: String, var t: String, var done: Boolean)

var Goal.tasks: List<Task>
    get() = (data["tasks"] as? JsonArray)?.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        Task(o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null, o["t"]?.jsonPrimitive?.content ?: "", o["done"]?.jsonPrimitive?.booleanOrNull ?: false)
    } ?: emptyList()
    set(v) { data["tasks"] = JsonArray(v.map { JsonObject(mapOf("id" to JsonPrimitive(it.id), "t" to JsonPrimitive(it.t), "done" to JsonPrimitive(it.done))) }) }

fun JsonElement?.objOrNull(): JsonObject? = this as? JsonObject
fun JsonElement?.arrOrNull(): JsonArray? = this as? JsonArray
fun JsonObject.s(k: String, d: String = ""): String = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: d
fun JsonObject.n(k: String, d: Double = 0.0): Double = (this[k] as? JsonPrimitive)?.doubleOrNull ?: d
fun JsonObject.b(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false
fun JsonObject.o(k: String): JsonObject? = this[k] as? JsonObject
fun JsonObject.a(k: String): JsonArray = (this[k] as? JsonArray) ?: JsonArray(emptyList())
fun JsonObject.nOrNull(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
fun JsonElement.asObj(): JsonObject = jsonObject
fun JsonElement.asArr(): JsonArray = jsonArray
