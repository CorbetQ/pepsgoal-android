package fr.pepsgoal.app.sys

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import fr.pepsgoal.app.R
import fr.pepsgoal.core.today

/** Icônes de l'application au choix : chaque icône est une entrée du lanceur (activity-alias), une seule active. */
object AppIcons {
    /** [price] : prix en flammes dans la boutique (0 : gratuite) ; [season] : icône de saison (halloween, noel, love, spring, summer, autumn, winter, easter, newyear). */
    class Icon(val key: String, val label: String, val alias: String, val bg: List<Long>, val fg: Int, val price: Int = 0, val season: String? = null)
    val ALL = listOf(
        Icon("default", "Braise", "IconDefault", listOf(0xFF241552, 0xFF07081A), R.drawable.ic_launcher_foreground),
        Icon("dark", "Nuit", "IconDark", listOf(0xFF14101F, 0xFF000000), R.drawable.ic_launcher_foreground),
        Icon("light", "Clair", "IconLight", listOf(0xFFFFFFFF, 0xFFE3E6FF), R.drawable.ic_launcher_foreground),
        Icon("mono", "Monochrome", "IconMono", listOf(0xFF26283A, 0xFF0B0C14), R.drawable.ic_launcher_foreground_mono),
        Icon("ocean", "Océan", "IconOcean", listOf(0xFF0E3A78, 0xFF030A1C), R.drawable.ic_launcher_foreground_ocean, 300),
        Icon("halloween", "Halloween", "IconHalloween", listOf(0xFF3A1466, 0xFF0B0418), R.drawable.ic_launcher_foreground_halloween, 500, season = "halloween"),
        Icon("noel", "Noël", "IconNoel", listOf(0xFF145C32, 0xFF04170C), R.drawable.ic_launcher_foreground_noel, 500, season = "noel"),
        Icon("galaxy", "Galaxie", "IconGalaxy", listOf(0xFF2A1660, 0xFF03030F), R.drawable.ic_launcher_foreground_galaxy, 400),
        Icon("gold", "Or", "IconGold", listOf(0xFF1E1608, 0xFF000000), R.drawable.ic_launcher_foreground_gold, 600),
        Icon("sakura", "Sakura", "IconSakura", listOf(0xFF5A1E4E, 0xFF1C0A1F), R.drawable.ic_launcher_foreground_sakura, 400),
        Icon("neon", "Néon", "IconNeon", listOf(0xFF0B0720, 0xFF2A0A3A), R.drawable.ic_launcher_foreground_neon, 500),
        Icon("campfire", "Feu de camp", "IconCampfire", listOf(0xFF0D1640, 0xFFB8452A), R.drawable.ic_launcher_foreground_campfire, 400),
        Icon("valentin", "Saint-Valentin", "IconValentin", listOf(0xFF6A1240, 0xFF22061A), R.drawable.ic_launcher_foreground_valentin, 400, season = "love"),
        Icon("paques", "Pâques", "IconPaques", listOf(0xFFB9A7FF, 0xFF7FD6C2), R.drawable.ic_launcher_foreground_paques, 400, season = "easter"),
        Icon("printemps", "Printemps", "IconPrintemps", listOf(0xFFBDF0FF, 0xFF79D68F), R.drawable.ic_launcher_foreground_printemps, 400, season = "spring"),
        Icon("ete", "Été", "IconEte", listOf(0xFF5B2A86, 0xFFFFD27A), R.drawable.ic_launcher_foreground_ete, 400, season = "summer"),
        Icon("automne", "Automne", "IconAutomne", listOf(0xFF5A2A12, 0xFF1A0B06), R.drawable.ic_launcher_foreground_automne, 400, season = "autumn"),
        Icon("hiver", "Hiver", "IconHiver", listOf(0xFF1C4C8C, 0xFF071A38), R.drawable.ic_launcher_foreground_hiver, 400, season = "winter"),
        Icon("nouvelan", "Nouvel An", "IconNouvelan", listOf(0xFF0B0F2E, 0xFF221447), R.drawable.ic_launcher_foreground_nouvelan, 500, season = "newyear"),
        Icon("citrouille", "Citrouille", "IconCitrouille", listOf(0xFF2E1250, 0xFF0C0418), R.drawable.ic_launcher_foreground_citrouille, 500, season = "halloween"),
        Icon("sapin", "Sapin", "IconSapin", listOf(0xFF102A5C, 0xFF050B1E), R.drawable.ic_launcher_foreground_sapin, 500, season = "noel"),
        Icon("arcenciel", "Arc-en-ciel", "IconArcenciel", listOf(0xFF2A2050, 0xFF0B0820), R.drawable.ic_launcher_foreground_arcenciel, 500),
        Icon("lave", "Volcan", "IconLave", listOf(0xFF2A0B2E, 0xFFD0451B), R.drawable.ic_launcher_foreground_lave, 500),
        Icon("dragon", "Dragon", "IconDragon", listOf(0xFF123B3A, 0xFF04120F), R.drawable.ic_launcher_foreground_dragon, 700),
        Icon("phenix", "Phénix", "IconPhenix", listOf(0xFF3A0B2A, 0xFF120414), R.drawable.ic_launcher_foreground_phenix, 700),
        Icon("fusee", "Fusée", "IconFusee", listOf(0xFF1E1A4A, 0xFF05040F), R.drawable.ic_launcher_foreground_fusee, 500),
        Icon("bougie", "Bougie", "IconBougie", listOf(0xFF3A2416, 0xFF120A06), R.drawable.ic_launcher_foreground_bougie, 300),
        Icon("cristal", "Cristal", "IconCristal", listOf(0xFF1A1A4A, 0xFF06061A), R.drawable.ic_launcher_foreground_cristal, 600),
        Icon("pixel", "Pixel", "IconPixel", listOf(0xFF2B1B4F, 0xFF120A24), R.drawable.ic_launcher_foreground_pixel, 400),
        Icon("aurore", "Aurore", "IconAurore", listOf(0xFF07102E, 0xFF0D2A3A), R.drawable.ic_launcher_foreground_aurore, 500),
        Icon("coeur", "Cœur ardent", "IconCoeur", listOf(0xFF2A0A14, 0xFF0A0306), R.drawable.ic_launcher_foreground_coeur, 400),
        Icon("eclair", "Éclair", "IconEclair", listOf(0xFF2A1F6B, 0xFF0A0A24), R.drawable.ic_launcher_foreground_eclair, 400),
        Icon("lune", "Lune", "IconLune", listOf(0xFF0A1440, 0xFF030716), R.drawable.ic_launcher_foreground_lune, 400),
        Icon("cafe", "Café", "IconCafe", listOf(0xFFE8C9A0, 0xFFA8744A), R.drawable.ic_launcher_foreground_cafe, 300),
        Icon("foret", "Forêt", "IconForet", listOf(0xFF0B2A3A, 0xFF06140F), R.drawable.ic_launcher_foreground_foret, 400),
        Icon("kawaii", "Kawaii", "IconKawaii", listOf(0xFFFFD6E8, 0xFFFFC2A8), R.drawable.ic_launcher_foreground_kawaii, 400),
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
