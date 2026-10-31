package fr.pepsgoal.app.sys

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.pepsgoal.core.FpsGovernor
import fr.pepsgoal.core.Quality
import fr.pepsgoal.core.QualitySteps

/**
 * Réglages de performance (propres à l'appareil) : un mode d'ensemble (Auto, Qualité, Équilibré, Économie) et des
 * options individuelles qui peuvent le surcharger : c'est le plafond ([ceiling]). Le régulateur de fluidité (8.5) peut
 * abaisser la qualité par paliers sous ce plafond quand les images par seconde chutent. Tout le rendu lit [flags]
 * (état Compose : changer un réglage redessine l'interface immédiatement).
 */
object Perf {
    /** Options effectives. */
    data class Flags(
        /** Arrière-plan animé (sinon image figée). */
        val bgAnim: Boolean = true,
        /** Images par seconde des fonds animés (plafond). */
        val bgFps: Int = 12,
        /** Ancien verre givré (8.0) : sans effet depuis Braise Pop, gardé pour les réglages enregistrés. */
        val blur: Boolean = true,
        /** Effets décoratifs : neige, pétales, confettis, étincelles, halos. */
        val fx: Boolean = true,
        /** Part des particules affichées (1 : toutes). */
        val particles: Float = 1f,
        /** Apparition échelonnée des cartes et transitions riches entre écrans. */
        val rich: Boolean = true,
        /** Anciennes ombres portées (8.0) : sans effet depuis Braise Pop, gardé pour les réglages enregistrés. */
        val shadows: Boolean = true,
        /** Résolution du rendu des scènes (part de la taille de l'écran : 0,5 = demi-résolution). */
        val sceneScale: Float = .5f,
    )

    private fun Flags.q() = Quality(bgAnim, bgFps, fx, particles, rich, sceneScale)
    private fun Flags.with(q: Quality) = copy(bgAnim = q.bgAnim, bgFps = q.bgFps, fx = q.fx, particles = q.particles, rich = q.rich, sceneScale = q.sceneScale)

    val MODES = listOf("auto" to "Auto", "quality" to "Qualité", "balanced" to "Équilibré", "saver" to "Économie")
    private val PRESETS = mapOf(
        "quality" to Flags(),
        "balanced" to Flags(bgFps = 8, particles = .6f),
        "saver" to Flags(bgAnim = false, bgFps = 6, blur = false, fx = false, particles = .3f, rich = false, shadows = false),
    )
    /** Options réglables une à une (clé, libellé, description). */
    val OPTIONS = listOf(
        Triple("bgAnim", "Arrière-plan animé", "Le fond bouge doucement ; désactivé, il reste figé"),
        Triple("fx", "Effets décoratifs", "Neige, pétales, confettis, étincelles et halos"),
        Triple("rich", "Transitions riches", "Apparition échelonnée des cartes et transitions entre écrans"),
    )

    /** Réglages effectifs : le plafond, éventuellement abaissé par le régulateur. */
    var flags by mutableStateOf(Flags()); private set
    /** Plafond : niveau maximal fixé par le mode (ou l'analyse en Auto) et les options. Jamais dépassé. */
    var ceiling by mutableStateOf(Flags()); private set
    /** Régulateur de fluidité activé (par défaut). */
    var autoReg by mutableStateOf(true); private set
    /** Palier appliqué par le régulateur (0 : qualité normale). */
    var regLevel by mutableIntStateOf(0); private set
    /** Dernière mesure : images par seconde et fréquence de l'écran (−1 : pas encore mesuré). */
    var liveFps by mutableFloatStateOf(-1f); private set
    var liveHz by mutableFloatStateOf(60f); private set
    /** Historique des dernières mesures (une par seconde), en anneau ; [histTick] change à chaque mesure. */
    val history = FloatArray(60)
    var histCount = 0; private set
    var histHead = 0; private set
    var histTick by mutableIntStateOf(0); private set
    /** Analyse en cours : le régulateur ne tient pas compte de ce test volontairement lourd. */
    var benchRunning = false
    /** Instant (ms, horloge de démarrage) avant lequel les mesures ne comptent pas (démarrage, retour au premier plan). */
    var warmUntil = 0L
    private val gov = FpsGovernor()
    /** Mode choisi (« auto » : celui recommandé par l'analyse). */
    var mode by mutableStateOf("auto"); private set
    /** Niveau recommandé par la dernière analyse : quality, balanced ou saver. */
    var recommended by mutableStateOf("quality"); private set
    /** Dernier score d'analyse (0 à 100), et son détail. */
    var score by mutableStateOf(-1); private set
    var report by mutableStateOf(""); private set
    private var overrides: Map<String, Boolean> = emptyMap()

    private fun sp(ctx: Context) = ctx.getSharedPreferences("pepsgoal.perf", Context.MODE_PRIVATE)

    /** Au démarrage : réglages enregistrés, ou première estimation d'après le matériel. */
    fun load(ctx: Context) {
        val s = sp(ctx)
        mode = s.getString("mode", "auto") ?: "auto"
        overrides = OPTIONS.mapNotNull { (k, _, _) -> if (s.contains("o_$k")) k to s.getBoolean("o_$k", true) else null }.toMap()
        score = s.getInt("score", -1); report = s.getString("report", "") ?: ""
        autoReg = s.getBoolean("autoReg", true)
        recommended = s.getString("recommended", null) ?: hardwareTier(ctx).also { s.edit().putString("recommended", it).apply() }
        apply()
        FpsMonitor.want(FpsMonitor.REG, autoReg)
    }

    fun setMode(ctx: Context, m: String) {
        mode = m; overrides = emptyMap()
        sp(ctx).edit().putString("mode", m).apply { OPTIONS.forEach { remove("o_${it.first}") } }.apply()
        apply()
    }

    fun setOption(ctx: Context, key: String, on: Boolean) {
        overrides = overrides + (key to on)
        sp(ctx).edit().putBoolean("o_$key", on).apply()
        apply()
    }

    fun isOverridden(key: String) = key in overrides

    /** Active ou coupe le régulateur ; coupé, la qualité revient aussitôt au plafond. */
    fun setAutoReg(ctx: Context, on: Boolean) {
        autoReg = on
        sp(ctx).edit().putBoolean("autoReg", on).apply()
        gov.reset(); regLevel = 0; flags = ceiling
        FpsMonitor.want(FpsMonitor.REG, on)
    }

    /**
     * Une mesure du suivi des images (environ une par seconde, fil principal) : historique, puis décision du régulateur.
     * Aucune allocation : un tableau en anneau et des états numériques.
     */
    fun onSample(fps: Float, hz: Float, now: Long) {
        liveFps = fps; liveHz = hz
        history[histHead] = fps; histHead = (histHead + 1) % history.size; if (histCount < history.size) histCount++
        histTick++
        if (!autoReg || benchRunning || now < warmUntil) return
        val l = gov.feed(now, FpsGovernor.ratio(fps, hz))
        if (l != regLevel) { regLevel = l; flags = ceiling.with(QualitySteps.apply(ceiling.q(), l)) }
    }

    /** Libellé de l'état du régulateur. */
    fun regState(): String = if (regLevel > 0) "Qualité réduite temporairement" else "Qualité normale"

    private fun apply() {
        val base = PRESETS[if (mode == "auto") recommended else mode] ?: Flags()
        var f = base
        overrides.forEach { (k, v) ->
            f = when (k) {
                "bgAnim" -> f.copy(bgAnim = v); "blur" -> f.copy(blur = v); "fx" -> f.copy(fx = v)
                "rich" -> f.copy(rich = v); "shadows" -> f.copy(shadows = v); else -> f
            }
        }
        // Nouveau plafond : le régulateur repart de la qualité normale sous ce plafond
        ceiling = f; gov.reset(); regLevel = 0
        flags = f
    }

    /** Estimation sans test : mémoire, cœurs du processeur et version d'Android. */
    fun hardwareTier(ctx: Context): String = tierOf(hardwareScore(ctx))

    fun hardwareScore(ctx: Context): Int {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val gb = mi.totalMem / 1_073_741_824.0
        val cores = Runtime.getRuntime().availableProcessors()
        var s = 0.0
        s += (gb / 8.0).coerceAtMost(1.0) * 40
        s += (cores / 8.0).coerceAtMost(1.0) * 20
        s += ((Build.VERSION.SDK_INT - 26) / 9.0).coerceIn(0.0, 1.0) * 20
        s += if (am.isLowRamDevice) 0.0 else 20.0
        return s.toInt().coerceIn(0, 100)
    }

    private fun tierOf(score: Int) = when { score >= 70 -> "quality"; score >= 45 -> "balanced"; else -> "saver" }

    /**
     * Résultat de l'analyse complète : matériel + test d'affichage (images par seconde et saccades mesurées pendant
     * le rendu d'une scène animée). Le mode Auto suit ensuite cette recommandation.
     */
    fun saveAnalysis(ctx: Context, fps: Double, jankPct: Double, refresh: Float) {
        val hw = hardwareScore(ctx)
        val fpsScore = (fps / minOf(60.0, refresh.toDouble().coerceAtLeast(30.0))).coerceIn(0.0, 1.0) * 100
        val jankScore = (1.0 - jankPct / 30.0).coerceIn(0.0, 1.0) * 100
        val total = (hw * .35 + fpsScore * .4 + jankScore * .25).toInt().coerceIn(0, 100)
        score = total; recommended = tierOf(total)
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        report = "${"%.1f".format(mi.totalMem / 1_073_741_824.0)} Go de mémoire · ${Runtime.getRuntime().availableProcessors()} cœurs · Android ${Build.VERSION.RELEASE} · " +
            "écran ${refresh.toInt()} Hz · ${"%.0f".format(fps)} images/s au test · ${"%.0f".format(jankPct)} % d’images en retard"
        sp(ctx).edit().putInt("score", total).putString("report", report).putString("recommended", recommended).apply()
        apply()
    }
}
