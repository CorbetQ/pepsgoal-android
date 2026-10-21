package fr.pepsgoal.app.sys

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import fr.pepsgoal.app.MainActivity
import fr.pepsgoal.app.R
import fr.pepsgoal.app.peps
import fr.pepsgoal.core.Pg
import fr.pepsgoal.core.pl
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** Met à jour le widget après chaque changement important (validation, minuteur, fin de journée). */
object WidgetUpdater {
    private val scope = MainScope()
    fun update(ctx: Context) { scope.launch { try { TodayWidget().updateAll(ctx.applicationContext) } catch (e: Exception) {} } }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodayWidget() }

private val GoalKey = ActionParameters.Key<String>("goal")

/** Valide un objectif depuis le widget, sans ouvrir l'application. */
class ValidateAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[GoalKey] ?: return
        val app = context.peps
        when (app.pg.validate(id)) {
            is Pg.VResult.AskProgress -> context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_PROGRESS, id))
            is Pg.VResult.OpenGoal -> context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_GOAL, id))
            else -> {}
        }
        LiveUpdates.refresh(context); TodayWidget().update(context, glanceId)
    }
}

/** Widget « Ta journée » : série, validations du jour et bouton de validation, aux couleurs Material You du téléphone. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { GlanceTheme { Content(context) } }
    }

    @Composable
    private fun Content(context: Context) {
        val app = context.peps; val pg = app.pg; val u = pg.me()
        val c = GlanceTheme.colors
        val open = actionStartActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "today"))
        Column(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(c.widgetBackground).padding(14.dp).clickable(open)) {
            if (u == null) { Text("Connecte-toi à PepsGoal pour suivre ta journée.", style = TextStyle(color = c.onSurface)); return@Column }
            val st = pg.globalStreak(u.id); val plan = pg.todayPlan(u.id); val done = plan.due.count { it.done }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_launcher_foreground), "Série", GlanceModifier.size(34.dp))
                Column(GlanceModifier.defaultWeight().padding(start = 6.dp)) {
                    Text(pl(st.n, "jour de série", "jours de série"), style = TextStyle(color = c.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp))
                    Text(if (plan.due.isEmpty()) "Rien de prévu aujourd’hui" else "$done/${plan.due.size} validé${if (done > 1) "s" else ""}${if (!st.lit && st.n > 0) " · flamme à rallumer" else ""}",
                        style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp))
                }
                Box(GlanceModifier.size(36.dp).cornerRadius(18.dp).background(c.primary).clickable(actionStartActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_GOAL))), contentAlignment = Alignment.Center) {
                    Text("+", style = TextStyle(color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp))
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(plan.due.sortedBy { it.done }.take(12), itemId = { it.g.id.hashCode().toLong() }) { x ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).cornerRadius(14.dp).background(if (x.done) c.secondaryContainer else c.surfaceVariant).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(x.g.icon, style = TextStyle(fontSize = 18.sp))
                        Text(x.g.title, GlanceModifier.defaultWeight().padding(start = 8.dp), style = TextStyle(color = c.onSurface, fontSize = 13.sp, fontWeight = if (x.done) FontWeight.Normal else FontWeight.Medium), maxLines = 1)
                        Box(GlanceModifier.size(28.dp).cornerRadius(14.dp).background(if (x.done) c.primary else c.surface)
                            .clickable(actionRunCallback<ValidateAction>(actionParametersOf(GoalKey to x.g.id))), contentAlignment = Alignment.Center) {
                            Text(if (x.done) "✓" else "○", style = TextStyle(color = if (x.done) c.onPrimary else c.onSurface, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}
