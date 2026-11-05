package fr.pepsgoal.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Source des requêtes réseau (écran, service, alarme, worker…), portée par le contexte de la coroutine. */
class NetSource(val name: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<NetSource>
}

/**
 * Compteurs réseau (sans URL, jeton ni donnée personnelle) : requêtes par source et par classe de chemin, codes HTTP,
 * réponses 304, octets sur le fil (avant décompression), octets du corps et octets envoyés. L'application branche
 * [sink] sur ses compteurs horaires ; sans lui, rien n'est retenu.
 */
object NetStats {
    @Volatile var sink: ((String, Long) -> Unit)? = null
    /** Sources d'arrière-plan (comptées à part pour l'alerte de la page Performance). */
    val BACKGROUND = setOf("service", "alarme", "worker")

    /** Classe d'un chemin d'API : statut, notifications, conversations, groupes, flux, social, admin, sync, autre. */
    fun classOf(path: String): String {
        val p = path.substringBefore('?').let { if (it.startsWith("http")) "/" + it.substringAfter("://").substringAfter('/') else it }
        return when {
            p == "/v1/status" -> "statut"
            p == "/v1/notifications" -> "notifications"
            p.startsWith("/v1/conversations") -> "conversations"
            p.startsWith("/v1/groups") -> "groupes"
            p == "/v1/feed" -> "flux"
            p.startsWith("/v1/admin") -> "admin"
            p.startsWith("/v1/sync") -> "sync"
            p == "/v1/me" || p.startsWith("/v1/friends") || p.startsWith("/v1/stories") || p == "/v1/blocks" || p == "/v1/me/stories" -> "social"
            else -> "autre"
        }
    }

    fun record(source: String, path: String, code: Int, wireBytes: Long, bodyBytes: Long, sentBytes: Long, gzip: Boolean) {
        val s = sink ?: return
        val cls = classOf(path)
        s("req", 1); s("req.src.$source", 1); s("req.cls.$cls", 1)
        if (source in BACKGROUND) s("req.bg", 1)
        if (code == 304) s("req.304", 1)
        if (code >= 400 || code == 0) s("req.err", 1)
        if (gzip) s("req.gzip", 1)
        s("bytes.wire", wireBytes); s("bytes.body", bodyBytes); s("bytes.sent", sentBytes)
        s("bytes.wire.src.$source", wireBytes)
    }

    /** Événement compté hors requête (rechargements sociaux, échecs de relève…). */
    fun count(key: String, v: Long = 1) { sink?.invoke(key, v) }
}

/**
 * Compteurs en anneau : 24 compartiments horaires et 7 compartiments journaliers. Les clés terminées par « .max » gardent
 * le maximum au lieu de la somme. Sérialisable en JSON compact pour les SharedPreferences.
 */
class StatsRing(val hours: Int = 24, val days: Int = 7) {
    private val hourly = Array(hours) { HashMap<String, Long>() }
    private val hourStamp = LongArray(hours) { -1 }
    private val daily = Array(days) { HashMap<String, Long>() }
    private val dayStamp = LongArray(days) { -1 }

    private fun merge(m: HashMap<String, Long>, key: String, v: Long) {
        m[key] = if (key.endsWith(".max")) maxOf(m[key] ?: 0, v) else (m[key] ?: 0) + v
    }

    private fun slot(stamps: LongArray, arr: Array<HashMap<String, Long>>, unit: Long): HashMap<String, Long> {
        val i = (unit % stamps.size).toInt()
        if (stamps[i] != unit) { arr[i].clear(); stamps[i] = unit }
        return arr[i]
    }

    @Synchronized fun add(key: String, v: Long, nowMs: Long) {
        merge(slot(hourStamp, hourly, nowMs / HOUR), key, v)
        merge(slot(dayStamp, daily, nowMs / DAY), key, v)
    }

    private fun sum(stamps: LongArray, arr: Array<HashMap<String, Long>>, from: Long, to: Long): Map<String, Long> {
        val out = HashMap<String, Long>()
        for (i in stamps.indices) if (stamps[i] in from..to) arr[i].forEach { (k, v) -> merge(out, k, v) }
        return out
    }

    /** Totaux des 24 dernières heures (heure en cours comprise). */
    @Synchronized fun last24h(nowMs: Long): Map<String, Long> { val h = nowMs / HOUR; return sum(hourStamp, hourly, h - hours + 1, h) }

    /** Totaux des 7 derniers jours. */
    @Synchronized fun week(nowMs: Long): Map<String, Long> { val d = nowMs / DAY; return sum(dayStamp, daily, d - days + 1, d) }

    /** Valeur de [key] heure par heure, la plus ancienne en premier. */
    @Synchronized fun perHour(key: String, nowMs: Long): List<Long> {
        val h = nowMs / HOUR
        return (h - hours + 1..h).map { u -> val i = (u % hours).toInt(); if (hourStamp[i] == u) hourly[i][key] ?: 0 else 0 }
    }

    @Synchronized fun encode(): String = JsonObject(mapOf(
        "h" to JsonArray(hourly.indices.map { i -> JsonArray(listOf(JsonPrimitive(hourStamp[i]), JsonObject(hourly[i].mapValues { JsonPrimitive(it.value) }))) }),
        "d" to JsonArray(daily.indices.map { i -> JsonArray(listOf(JsonPrimitive(dayStamp[i]), JsonObject(daily[i].mapValues { JsonPrimitive(it.value) }))) }),
    )).toString()

    @Synchronized fun clear() { hourly.forEach { it.clear() }; daily.forEach { it.clear() }; hourStamp.fill(-1); dayStamp.fill(-1) }

    companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L

        fun decode(raw: String?, hours: Int = 24, days: Int = 7): StatsRing {
            val r = StatsRing(hours, days)
            if (raw.isNullOrBlank()) return r
            try {
                val o = JSON.parseToJsonElement(raw).jsonObject
                fun load(key: String, stamps: LongArray, arr: Array<HashMap<String, Long>>) {
                    o[key]?.jsonArray?.forEachIndexed { i, e ->
                        if (i >= stamps.size) return@forEachIndexed
                        val pair = e.jsonArray
                        stamps[i] = pair[0].jsonPrimitive.longOrNull ?: -1
                        pair[1].jsonObject.forEach { (k, v) -> v.jsonPrimitive.longOrNull?.let { arr[i][k] = it } }
                    }
                }
                load("h", r.hourStamp, r.hourly); load("d", r.dayStamp, r.daily)
            } catch (e: Exception) { r.clear() }
            return r
        }
    }
}
