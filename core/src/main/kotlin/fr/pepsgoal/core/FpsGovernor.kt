package fr.pepsgoal.core

import kotlin.math.max
import kotlin.math.min

/*
 * Régulateur de fluidité (8.5) : l'application mesure en continu ses images par seconde ; quand elles chutent de façon
 * soutenue, la qualité visuelle baisse d'un palier (particules, cadence et résolution des scènes, effets décoratifs,
 * fond figé), puis remonte palier par palier quand l'affichage redevient stable. Le régulateur n'agit qu'à
 * l'intérieur du plafond fixé par l'analyse du téléphone (ou le mode choisi) : il ne le dépasse jamais.
 */

/** Réglages de rendu effectifs. */
data class Quality(
    /** Fond animé (sinon dernière image figée). */
    val bgAnim: Boolean = true,
    /** Images par seconde des fonds animés (plafond). */
    val bgFps: Int = 12,
    /** Effets décoratifs : neige, pétales, confettis, étincelles. */
    val fx: Boolean = true,
    /** Part des particules dessinées (1 : toutes). */
    val particles: Float = 1f,
    /** Apparition échelonnée des cartes et transitions riches. */
    val rich: Boolean = true,
    /** Résolution du rendu des scènes, en part de la taille de l'écran (0,5 : demi-résolution). */
    val sceneScale: Float = .5f,
)

/** Paliers de réduction de la qualité, du plus discret (1) au plus économe ([MAX]). */
object QualitySteps {
    const val MAX = 4

    /** Ce que chaque palier retire (en plus des précédents) ; l'indice 0 est la qualité normale. */
    val LABELS = listOf(
        "Qualité normale",
        "Moins de particules, fonds un peu plus lents",
        "Scènes plus lentes et moins détaillées",
        "Sans effets décoratifs ni confettis",
        "Fond figé et transitions simples",
    )

    /**
     * Qualité au palier [level] sous le plafond [ceiling]. Chaque valeur est le minimum entre le plafond et la limite du
     * palier : le résultat ne dépasse jamais le plafond, et chaque palier est au plus aussi riche que le précédent.
     */
    fun apply(ceiling: Quality, level: Int): Quality {
        val l = level.coerceIn(0, MAX)
        var q = ceiling
        if (l >= 1) q = q.copy(particles = min(q.particles, .6f), bgFps = min(q.bgFps, max(6, ceiling.bgFps * 3 / 4)))
        if (l >= 2) q = q.copy(particles = min(q.particles, .35f), bgFps = min(q.bgFps, 6), sceneScale = min(q.sceneScale, .4f))
        if (l >= 3) q = q.copy(fx = false, particles = min(q.particles, .2f), sceneScale = min(q.sceneScale, .34f))
        if (l >= 4) q = q.copy(bgAnim = false, rich = false)
        return q
    }

    /** Vrai si [q] ne dépasse [ceiling] sur aucun réglage. */
    fun within(q: Quality, ceiling: Quality): Boolean =
        (!q.bgAnim || ceiling.bgAnim) && q.bgFps <= ceiling.bgFps && (!q.fx || ceiling.fx) && q.particles <= ceiling.particles &&
            (!q.rich || ceiling.rich) && q.sceneScale <= ceiling.sceneScale
}

/**
 * Décision du régulateur, à partir de mesures régulières (environ une par seconde) du rapport images par seconde /
 * fréquence de l'écran :
 * - sous [low] (85 %) pendant [dropAfterMs] (3 s) : un palier de moins ;
 * - à partir de [high] (95 %) pendant [recoverMs] (10 s au départ) : un palier de plus ;
 * - entre les deux (hystérésis) : rien ne bouge, et les compteurs repartent de zéro ;
 * - au moins [minGapMs] entre deux changements ;
 * - anti-oscillation : une chute peu après une remontée double le délai de remontée (jusqu'à [maxRecoverMs]) ; il
 *   revient à sa valeur de départ après une longue stabilité à la qualité normale ;
 * - mesures interrompues plus de [staleMs] (application en arrière-plan, interface au repos) : les compteurs repartent
 *   de zéro.
 */
class FpsGovernor(
    val low: Float = .85f,
    val high: Float = .95f,
    val dropAfterMs: Long = 3_000,
    val recoverAfterMs: Long = 10_000,
    val minGapMs: Long = 2_000,
    val maxRecoverMs: Long = 80_000,
    val staleMs: Long = 2_500,
    val maxLevel: Int = QualitySteps.MAX,
) {
    /** Palier actuel (0 : qualité normale). */
    var level = 0; private set
    /** Délai de stabilité actuel avant de remonter d'un palier. */
    var recoverMs = recoverAfterMs; private set
    private var lowSince = -1L
    private var highSince = -1L
    private var lastChange = Long.MIN_VALUE / 4
    private var lastRaise = Long.MIN_VALUE / 4
    private var lastSample = -1L

    /** Une mesure : [ratio] = images par seconde / fréquence de l'écran, à l'instant [now] (ms). Renvoie le palier. */
    fun feed(now: Long, ratio: Float): Int {
        if (lastSample >= 0 && now - lastSample > staleMs) { lowSince = -1; highSince = -1 }
        lastSample = now
        when {
            ratio < low -> {
                highSince = -1
                if (lowSince < 0) lowSince = now
                if (level < maxLevel && now - lowSince >= dropAfterMs && now - lastChange >= minGapMs) {
                    // Chute peu après une remontée : la prochaine remontée attendra deux fois plus longtemps
                    if (now - lastRaise <= recoverAfterMs * 2) recoverMs = min(recoverMs * 2, maxRecoverMs)
                    level++; lastChange = now; lowSince = now
                }
            }
            ratio >= high -> {
                lowSince = -1
                if (highSince < 0) highSince = now
                if (level > 0 && now - highSince >= recoverMs && now - lastChange >= minGapMs) {
                    level--; lastChange = now; lastRaise = now; highSince = now
                } else if (level == 0 && now - highSince >= maxRecoverMs) recoverMs = recoverAfterMs
            }
            else -> { lowSince = -1; highSince = -1 }
        }
        return level
    }

    /** Retour à la qualité normale (régulateur coupé, nouveau plafond). */
    fun reset() {
        level = 0; recoverMs = recoverAfterMs; lowSince = -1; highSince = -1
        lastChange = Long.MIN_VALUE / 4; lastRaise = Long.MIN_VALUE / 4; lastSample = -1
    }

    companion object {
        /** Rapport images par seconde / fréquence de l'écran, borné à [0, 1]. */
        fun ratio(fps: Float, refreshHz: Float): Float = if (refreshHz <= 0f) 1f else (fps / refreshHz).coerceIn(0f, 1f)
    }
}
