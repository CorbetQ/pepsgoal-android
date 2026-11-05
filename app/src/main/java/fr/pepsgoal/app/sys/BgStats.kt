package fr.pepsgoal.app.sys

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import fr.pepsgoal.core.NetStats
import fr.pepsgoal.core.StatsRing

/**
 * Compteurs réseau et arrière-plan (page Performance) : requêtes, octets, paquets de l'application, relèves, verrou de
 * réveil, service de premier plan, réveils et mises à jour sans effet. Anneau de 24 heures et 7 jours dans
 * `pepsgoal.stats`, écrit au plus toutes les 5 minutes et au passage en arrière-plan (jamais à chaque requête).
 * Aucune URL, aucun jeton, aucune donnée personnelle.
 */
object BgStats {
    private var ring = StatsRing()
    private var ctx: Context? = null
    private var lastSave = 0L
    private var dirty = false
    /** Dernier relevé des compteurs du système (paquets et octets de l'application), pour n'ajouter que la différence. */
    private var rx0 = -1L; private var tx0 = -1L; private var rxB0 = -1L; private var txB0 = -1L

    private fun sp(c: Context) = c.getSharedPreferences("pepsgoal.stats", Context.MODE_PRIVATE)

    fun load(c: Context) {
        ctx = c.applicationContext
        ring = StatsRing.decode(sp(c).getString("ring", null))
        NetStats.sink = { k, v -> add(k, v) }
    }

    fun add(key: String, v: Long = 1) {
        if (v == 0L) return
        ring.add(key, v, System.currentTimeMillis()); dirty = true
        maybeSave()
    }

    /** Maximum (durée la plus longue…) : clé terminée par « .max ». */
    fun max(key: String, v: Long) = add(if (key.endsWith(".max")) key else "$key.max", v)

    private fun maybeSave() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSave >= 5 * 60_000L) flush()
    }

    /** Relève les paquets de l'application depuis le dernier relevé, puis enregistre si nécessaire. */
    @Synchronized fun flush() {
        sampleTraffic()
        lastSave = SystemClock.elapsedRealtime()
        if (!dirty) return
        dirty = false
        ctx?.let { sp(it).edit().putString("ring", ring.encode()).apply() }
    }

    @Synchronized private fun sampleTraffic() {
        val uid = Process.myUid()
        val rx = TrafficStats.getUidRxPackets(uid); val tx = TrafficStats.getUidTxPackets(uid)
        val rxB = TrafficStats.getUidRxBytes(uid); val txB = TrafficStats.getUidTxBytes(uid)
        if (rx < 0 || rx == TrafficStats.UNSUPPORTED.toLong()) return
        // Compteurs du système remis à zéro (redémarrage) : on repart du relevé courant
        if (rx0 in 0..rx && tx0 in 0..tx) {
            ring.add("pkt.rx", rx - rx0, System.currentTimeMillis()); ring.add("pkt.tx", tx - tx0, System.currentTimeMillis())
            if (rxB0 in 0..rxB) ring.add("uid.rx", rxB - rxB0, System.currentTimeMillis())
            if (txB0 in 0..txB) ring.add("uid.tx", txB - txB0, System.currentTimeMillis())
            dirty = true
        }
        rx0 = rx; tx0 = tx; rxB0 = rxB; txB0 = txB
    }

    /** Paquets reçus par l'application (compteur cumulé du système), pour mesurer une relève. */
    fun rxPackets(): Long = TrafficStats.getUidRxPackets(Process.myUid())

    fun last24h(): Map<String, Long> { sampleTraffic(); return ring.last24h(System.currentTimeMillis()) }
    fun week(): Map<String, Long> = ring.week(System.currentTimeMillis())
    fun perHour(key: String): List<Long> = ring.perHour(key, System.currentTimeMillis())

    fun reset(c: Context) { ring.clear(); dirty = true; flush(); sp(c).edit().remove("ring").apply() }

    /** Alerte de la page Performance : plus de 400 requêtes d'arrière-plan ou plus de 5 min de verrou de réveil en 24 h. */
    fun alert(m: Map<String, Long>) = (m["req.bg"] ?: 0) > 400 || (m["wl.ms"] ?: 0) > 5 * 60_000L

    /** Rapport texte (presse-papiers), sans donnée personnelle, URL ni jeton. */
    fun report(extra: List<String> = emptyList()): String {
        val d = last24h(); val w = week()
        fun v(k: String, m: Map<String, Long> = d) = m[k] ?: 0
        fun kb(b: Long) = if (b >= 1_048_576) "%.1f Mo".format(b / 1_048_576.0) else "${b / 1024} ko"
        fun mn(ms: Long) = if (ms >= 60_000) "${ms / 60_000} min ${ms % 60_000 / 1000} s" else "${ms / 1000} s"
        val src = d.keys.filter { it.startsWith("req.src.") }.sorted().joinToString(", ") { "${it.removePrefix("req.src.")} ${d[it]}" }
        val cls = d.keys.filter { it.startsWith("req.cls.") }.sorted().joinToString(", ") { "${it.removePrefix("req.cls.")} ${d[it]}" }
        val polls = d.keys.filter { it.startsWith("poll.src.") }.sorted().joinToString(", ") { "${it.removePrefix("poll.src.")} ${d[it]}" }
        val gated = d.keys.filter { it.startsWith("poll.gated.") }.sorted().joinToString(", ") { "${it.removePrefix("poll.gated.")} ${d[it]}" }
        return buildString {
            appendLine("PepsGoal · réseau et arrière-plan · 24 h")
            appendLine("Requêtes : ${v("req")} (arrière-plan ${v("req.bg")}, 304 ${v("req.304")}, erreurs ${v("req.err")}, gzip ${v("req.gzip")})")
            appendLine("Par source : ${src.ifEmpty { "—" }}")
            appendLine("Par type : ${cls.ifEmpty { "—" }}")
            appendLine("Octets : fil ${kb(v("bytes.wire"))}, corps ${kb(v("bytes.body"))}, envoyés ${kb(v("bytes.sent"))}")
            appendLine("Application (système) : ${v("pkt.rx")} paquets reçus, ${v("pkt.tx")} émis, ${kb(v("uid.rx"))} reçus")
            appendLine("Relèves : ${polls.ifEmpty { "—" }} · refusées par la porte : ${gated.ifEmpty { "0" }} · échecs ${v("poll.fail")}")
            appendLine("Rechargements sociaux : complets ${v("social.full")}, allégés ${v("social.light")}, ignorés ${v("social.skipped")}, modération ${v("social.admin")}")
            appendLine("Verrou de réveil : ${v("wl.count")} prises, ${mn(v("wl.ms"))} au total, ${mn(v("wl.ms.max"))} au plus long")
            appendLine("Service de premier plan : ${mn(v("fgs.ms"))} actif, ${v("fgs.start")} démarrages, ${v("fgs.stop")} arrêts")
            appendLine("Réveils : relève ${v("wake.poll")}, rappels ${v("wake.alarm")}, concentration ${v("wake.focus")}, travail local ${v("wake.sync")}, travail réseau ${v("wake.netsync")}")
            appendLine("Santé Connect : ${v("health.reads")} lectures, ${mn(v("health.ms"))}")
            appendLine("Sans effet : widgets ${v("widget.skip")}/${v("widget.update") + v("widget.skip")}, Wear ${v("wear.skip")}/${v("wear.push") + v("wear.skip")}, activités ${v("live.skip")}/${v("live.post") + v("live.skip")}")
            extra.forEach { appendLine(it) }
            append("7 jours : ${v("req", w)} requêtes, ${v("pkt.rx", w)} paquets reçus, ${mn(v("wl.ms", w))} de verrou de réveil")
        }
    }
}
