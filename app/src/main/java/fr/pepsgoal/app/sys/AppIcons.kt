package fr.pepsgoal.app.sys

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import fr.pepsgoal.app.R
import fr.pepsgoal.core.today

/** Icônes de l'application au choix : chaque icône est une entrée du lanceur (activity-alias), une seule active. */
object AppIcons {
    class Icon(val key: String, val label: String, val alias: String, val bg: List<Long>, val fg: Int)
    val ALL = listOf(
        Icon("default", "Braise", "IconDefault", listOf(0xFF1A1240, 0xFF07081A), R.drawable.ic_launcher_foreground),
        Icon("dark", "Nuit", "IconDark", listOf(0xFF000000, 0xFF000000), R.drawable.ic_launcher_foreground),
        Icon("light", "Clair", "IconLight", listOf(0xFFFFFFFF, 0xFFE6E8FF), R.drawable.ic_launcher_foreground),
        Icon("mono", "Monochrome", "IconMono", listOf(0xFF1A1240, 0xFF07081A), R.drawable.ic_launcher_monochrome),
        Icon("ocean", "Océan", "IconOcean", listOf(0xFF0B2A5C, 0xFF050B1E), R.drawable.ic_launcher_foreground_ocean),
        Icon("halloween", "Halloween", "IconHalloween", listOf(0xFF2A0E3D, 0xFF120414), R.drawable.ic_launcher_foreground_halloween),
        Icon("noel", "Noël", "IconNoel", listOf(0xFF0B4D26, 0xFF03180B), R.drawable.ic_launcher_foreground_noel),
    )

    fun current(ctx: Context): String {
        val pm = ctx.packageManager
        return ALL.firstOrNull { i ->
            val s = pm.getComponentEnabledSetting(ComponentName(ctx, "fr.pepsgoal.app.${i.alias}"))
            s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && i.key == "default")
        }?.key ?: "default"
    }

    /** Active l'icône choisie et désactive les autres (le lanceur met parfois quelques secondes à se mettre à jour). */
    fun set(ctx: Context, key: String) {
        val pm = ctx.packageManager
        val target = ALL.firstOrNull { it.key == key } ?: return
        // L'icône choisie d'abord : l'application n'est jamais sans entrée dans le lanceur
        pm.setComponentEnabledSetting(ComponentName(ctx, "fr.pepsgoal.app.${target.alias}"), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        ALL.filter { it.key != key }.forEach { pm.setComponentEnabledSetting(ComponentName(ctx, "fr.pepsgoal.app.${it.alias}"), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP) }
    }
}

/** Saisons et fêtes : couleurs de l'arrière-plan (et neige à Noël) quand le thème saisonnier est activé. */
object Season {
    fun now(date: String = today()): String? {
        val md = date.substring(5)
        return when {
            md >= "10-20" && md <= "11-02" -> "halloween"
            md >= "12-01" || md <= "01-06" -> "noel"
            md >= "02-10" && md <= "02-15" -> "love"
            md >= "03-20" && md <= "04-30" -> "spring"
            md >= "06-21" && md <= "08-31" -> "summer"
            else -> null
        }
    }
    val LABELS = mapOf("halloween" to "Halloween 🎃", "noel" to "Fêtes de fin d’année 🎄", "love" to "Saint-Valentin 💕", "spring" to "Printemps 🌸", "summer" to "Été ☀️")
    val COLORS = mapOf(
        "halloween" to listOf(0xFFFF7A00, 0xFF8B3DFF, 0xFFFFB000, 0xFF3DFF8A),
        "noel" to listOf(0xFFD7263D, 0xFF1E8E3E, 0xFFFFD166, 0xFFFFFFFF),
        "love" to listOf(0xFFFF4F8B, 0xFFFF9AC1, 0xFFE85BFF, 0xFFFFC2A8),
        "spring" to listOf(0xFFB6FF2E, 0xFFFF9AD5, 0xFF7DD3FC, 0xFFFFE45C),
        "summer" to listOf(0xFFFFB000, 0xFF22D3FF, 0xFFFF7A3D, 0xFF2EF2B0),
    )
}
