package fr.pepsgoal.core

import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Contexte d'une décision de relève (aucune dépendance Android : testable en JVM).
 * [level] : 0 Rapide (comportement historique), 1 Équilibré (défaut), 2 Économe.
 */
data class PollContext(
    val nowMs: Long,
    val lastEventAtMs: Long,
    val hour: Int,
    val metered: Boolean = false,
    val powerSave: Boolean = false,
    val batteryLow: Boolean = false,
    val level: Int = PollPolicy.BALANCED,
    val foreground: Boolean = false,
)

/** État de relève qui survit à la mort du processus (DevicePrefs côté Android, mémoire en test). */
interface PollState {
    /** Dernier événement utile (notification serveur, message entrant, publication d'ami fraîche), en ms murales. */
    var lastEventAt: Long
    /** Dernière relève effectuée, en ms murales (porte globale). */
    var lastRunWall: Long
}

/**
 * Cadence de la relève d'arrière-plan : rapide juste après un événement utile, puis plus lente au calme et la nuit,
 * allongée sur réseau facturé, en économiseur d'énergie ou batterie faible. Plafond 60 min.
 */
object PollPolicy {
    const val FAST = 0
    const val BALANCED = 1
    const val SAVER = 2
    const val MIN = 60_000L
    const val CAP = 60 * MIN

    /** Paliers (récent < 5 min, tiède < 15 min, calme de jour, nuit) par niveau, en minutes. */
    private val TIERS = mapOf(FAST to longArrayOf(1, 1, 3, 15), BALANCED to longArrayOf(2, 5, 5, 30), SAVER to longArrayOf(3, 15, 15, 60))

    /** Nuit : 0 h – 7 h en niveau Rapide (comportement historique), 23 h – 7 h sinon. */
    fun night(hour: Int, level: Int) = if (level == FAST) hour < 7 else hour >= 23 || hour < 7

    fun intervalMs(c: PollContext): Long {
        // Au premier plan, la boucle d'écran relève à la minute (inchangé)
        if (c.foreground) return MIN
        val t = TIERS[c.level] ?: TIERS.getValue(BALANCED)
        val since = c.nowMs - c.lastEventAtMs
        val recent = c.lastEventAtMs in 1..c.nowMs
        val base = when {
            recent && since < 5 * MIN -> t[0]
            recent && since < 15 * MIN -> t[1]
            night(c.hour, c.level) -> t[3]
            else -> t[2]
        } * MIN
        // Niveau Rapide : comportement historique, sans multiplicateur
        if (c.level == FAST) return base
        var f = 1.0
        if (c.metered) f *= 1.5
        if (c.powerSave) f *= 2.0
        if (c.batteryLow) f *= 3.0
        return (base * f).roundToLong().coerceAtMost(CAP)
    }

    /** Un événement utile relance la cadence rapide ; persisté pour survivre au redémarrage du processus. */
    fun recordEvent(state: PollState, nowMs: Long) { state.lastEventAt = nowMs }

    fun intervalMs(state: PollState, c: PollContext) = intervalMs(c.copy(lastEventAtMs = state.lastEventAt))

    /**
     * Délai après [failures] échecs consécutifs : l'intervalle doublé à chaque échec, plafonné à 60 min, avec une gigue de
     * ±20 % pour ne pas resynchroniser les appareils. Aucun échec : l'intervalle tel quel.
     */
    fun backoffMs(intervalMs: Long, failures: Int, random: Random = Random): Long {
        if (failures <= 0) return intervalMs
        val raw = (intervalMs.toDouble() * (1L shl failures.coerceAtMost(16))).coerceAtMost(CAP.toDouble())
        val jitter = 1.0 + (random.nextDouble() * 0.4 - 0.2)
        return (raw * jitter).roundToLong().coerceIn(intervalMs, CAP)
    }

    /**
     * Verrou de réveil seulement quand rien d'autre ne garde le processeur éveillé : l'alarme (le récepteur ne garantit
     * que quelques secondes), le service écran éteint et sur batterie. WorkManager tient déjà son propre verrou.
     */
    fun needsWakeLock(src: String, interactive: Boolean, charging: Boolean): Boolean = when (src) {
        "alarme" -> true
        "service" -> !interactive && !charging
        else -> false
    }

    /** Durée maximale du verrou de réveil d'une relève (libéré dès la fin des requêtes). */
    const val WAKE_LOCK_MS = 15_000L

    /**
     * Le service « connexion en direct » tourne-t-il ? Seulement de jour, quand la cadence reste sous 30 min et que le
     * compte a une vie sociale (amis, conversations ou notifications récentes) ; sinon l'alarme et le worker relèvent.
     */
    fun shouldRunService(hour: Int, intervalMs: Long, hasSocial: Boolean, instantNotifs: Boolean): Boolean =
        instantNotifs && hasSocial && hour in 7..22 && intervalMs < 30 * MIN

    /**
     * Alarme de secours : espacée (2 × l'intervalle, entre 15 et 30 min) quand le service relève déjà ; à l'intervalle
     * courant sinon (service arrêté ou connexion en direct coupée).
     */
    fun alarmDelayMs(intervalMs: Long, serviceAlive: Boolean): Long =
        if (serviceAlive) (2 * intervalMs).coerceIn(15 * MIN, 30 * MIN) else intervalMs
}

/**
 * Porte globale : service, alarme et worker ne relèvent pas si une relève a eu lieu il y a moins de 80 % de l'intervalle
 * courant. L'écran, les actions de l'utilisateur et le test passent toujours (ils ont leur propre garde de 15 s).
 */
object PollGate {
    val GATED = setOf("service", "alarme", "worker")

    fun allow(src: String, nowWall: Long, lastRunWall: Long, intervalMs: Long): Boolean {
        if (src !in GATED) return true
        // Horloge incohérente (relève « dans le futur ») ou jamais relevé : on ne bloque pas
        if (lastRunWall <= 0 || lastRunWall > nowWall) return true
        return nowWall - lastRunWall >= (intervalMs * 0.8).roundToLong()
    }
}

/**
 * Boucle d'écran adaptative : 20 s sur le feed et les commentaires tant que des nouveautés arrivent, puis ×1,5 à chaque
 * cycle sans changement jusqu'à 60 s ; retour à 20 s à la prochaine interaction.
 */
object ScreenPollPolicy {
    const val FAST = 20_000L
    const val SLOW = 60_000L

    fun next(delayMs: Long, changed: Boolean): Long = if (changed) FAST else (delayMs * 3 / 2).coerceIn(FAST, SLOW)
}

/**
 * Conversation ouverte : résumé toutes les 6 s s'il y a eu de l'activité dans les 2 dernières minutes, 10 s ensuite,
 * 20 s après 5 min sans nouveauté. Retour à 6 s dès l'envoi ou la réception d'un message.
 */
object ChatPollPolicy {
    fun delayMs(sinceActivityMs: Long): Long = when {
        sinceActivityMs < 2 * 60_000L -> 6_000L
        sinceActivityMs < 5 * 60_000L -> 10_000L
        else -> 20_000L
    }
}

/** Période du travail local (WorkManager) : 30 min avec un lien Santé (validation automatique), 60 min sinon. */
object LocalWorkPolicy {
    fun periodMinutes(hasHealthLinks: Boolean): Long = if (hasHealthLinks) 30L else 60L
}
