package fr.pepsgoal.app.sys

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import fr.pepsgoal.app.MainActivity
import fr.pepsgoal.app.R
import fr.pepsgoal.app.peps

/**
 * Raccourcis par objectif, utilisables par l'Assistant Google (« Ok Google, valide Méditation sur PepsGoal ») et par
 * l'appui long sur l'icône de l'application : un raccourci par objectif du jour qui se valide d'un geste.
 */
object AssistantShortcuts {
    private const val PREFIX = "goal_"

    fun sync(ctx: Context) {
        val pg = ctx.peps.pg; val u = pg.me()
        val goals = if (u == null) emptyList() else pg.myGoals(u.id).filter { !pg.isClosedGoal(it) && it.userId == u.id && it.type in setOf("routine", "abstinence", "timed", "period", "challenge", "limit") }
            .sortedByDescending { pg.isFav(it) }.take(4)
        try {
            val keep = goals.map { PREFIX + it.id }.toSet()
            val stale = ShortcutManagerCompat.getDynamicShortcuts(ctx).map { it.id }.filter { it.startsWith(PREFIX) && it !in keep }
            if (stale.isNotEmpty()) ShortcutManagerCompat.removeDynamicShortcuts(ctx, stale)
            goals.forEachIndexed { i, g ->
                val intent = Intent(MainActivity.ACTION_FEATURE).setClass(ctx, MainActivity::class.java).putExtra("goal", g.id).putExtra("feature", "valider ${g.title}")
                val sc = ShortcutInfoCompat.Builder(ctx, PREFIX + g.id)
                    .setShortLabel("✓ ${g.title}".take(24)).setLongLabel("Valider ${g.title}".take(40))
                    .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher)).setIntent(intent).setRank(10 + i)
                    // L'Assistant reconnaît le nom de l'objectif (« ouvre Méditation sur PepsGoal », « valide Méditation… »)
                    .addCapabilityBinding("actions.intent.OPEN_APP_FEATURE", "feature", listOf(g.title, "valider ${g.title}", "valide ${g.title}"))
                    .build()
                ShortcutManagerCompat.pushDynamicShortcut(ctx, sc)
            }
        } catch (e: Exception) {}
    }
}
