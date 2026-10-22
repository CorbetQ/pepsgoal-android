package fr.pepsgoal.app.sys

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import fr.pepsgoal.app.MainActivity
import fr.pepsgoal.app.R
import fr.pepsgoal.app.peps
import fr.pepsgoal.app.ui.theme.TYPE_LIGHT
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.target
import fr.pepsgoal.core.model.unit
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** Met à jour tous les widgets après chaque changement important (validation, minuteur, fin de journée, réglages). */
object WidgetUpdater {
    private val scope = MainScope()
    fun update(ctx: Context) {
        scope.launch {
            val c = ctx.applicationContext
            for (w in listOf(TodayWidget(), StreakWidget(), ProgressWidget(), QuickWidget(), CapWidget())) try { w.updateAll(c) } catch (e: Exception) {}
        }
    }

    /** Les widgets proposés dans Paramètres › Widgets : (récepteur, nom, description, taille). */
    val ALL = listOf(
        listOf(TodayWidgetReceiver::class.java, "Ta journée", "Ta série et tes objectifs du jour, à valider d’un geste", "3 × 2", "today"),
        listOf(StreakWidgetReceiver::class.java, "Ta série", "Ta flamme, tes jours de série et ta semaine", "2 × 2", "streak"),
        listOf(ProgressWidgetReceiver::class.java, "Ta progression", "Validations du jour et niveau en cours", "3 × 2", "progress"),
        listOf(QuickWidgetReceiver::class.java, "Accès rapide", "Nouvel objectif, journée, messages et feed", "4 × 1", "quick"),
        listOf(CapWidgetReceiver::class.java, "Cap du jour", "Jauge de ton cap quotidien (10 000 pas…) et bouton « + »", "2 × 2", "cap"),
    )

    /** Demande au lanceur d'ajouter le widget sur l'écran d'accueil (false si le lanceur ne le permet pas). */
    fun pin(ctx: Context, receiver: Class<*>): Boolean {
        val m = AppWidgetManager.getInstance(ctx)
        if (Build.VERSION.SDK_INT < 26 || !m.isRequestPinAppWidgetSupported) return false
        return try { m.requestPinAppWidget(ComponentName(ctx, receiver), null, null) } catch (e: Exception) { false }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodayWidget() }
class StreakWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StreakWidget() }
class ProgressWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ProgressWidget() }
class QuickWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickWidget() }

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
        LiveUpdates.refresh(context); WidgetUpdater.update(context)
    }
}

/* ------------------------------------------------------------------ style commun (celui de l'application) */

/**
 * Apparence des widgets : verre clair ou nuit de PepsGoal (selon le thème de l'application ou le choix fait dans
 * Paramètres › Widgets), ou couleurs Material You du téléphone.
 */
private class WStyle(val dark: Boolean, val you: Boolean)

private fun style(ctx: Context): WStyle {
    val app = ctx.peps; val mode = app.prefs.widgetTheme
    val sysDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val appTheme = app.pg.me()?.settings?.theme ?: "system"
    val dark = when (mode) { "light" -> false; "dark" -> true; "you" -> sysDark; else -> when (appTheme) { "light" -> false; "dark", "oled" -> true; else -> sysDark } }
    return WStyle(dark, mode == "you" && Build.VERSION.SDK_INT >= 31)
}

private val WStyle.ink @Composable get() = if (you) GlanceTheme.colors.onSurface else ColorProvider(if (dark) Color(0xFFF4F5FF) else Color(0xFF0D0E2B))
private val WStyle.ink2 @Composable get() = if (you) GlanceTheme.colors.onSurfaceVariant else ColorProvider(if (dark) Color(0xFFC9CCF2) else Color(0xFF34375F))
private val WStyle.track @Composable get() = if (you) GlanceTheme.colors.surfaceVariant else ColorProvider(if (dark) Color(0x26FFFFFF) else Color(0x170D0E2B))

@Composable
private fun GlanceModifier.wBg(s: WStyle): GlanceModifier = cornerRadius(28.dp).then(
    if (s.you) GlanceModifier.background(GlanceTheme.colors.widgetBackground) else GlanceModifier.background(ImageProvider(if (s.dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light)))

@Composable
private fun GlanceModifier.wCard(s: WStyle): GlanceModifier = cornerRadius(18.dp).then(
    if (s.you) GlanceModifier.background(GlanceTheme.colors.surfaceVariant) else GlanceModifier.background(ImageProvider(if (s.dark) R.drawable.widget_card_dark else R.drawable.widget_card_light)))

@Composable
private fun GlanceModifier.wAccent(s: WStyle, radius: Dp): GlanceModifier = cornerRadius(radius).then(
    if (s.you) GlanceModifier.background(GlanceTheme.colors.primary) else GlanceModifier.background(ImageProvider(R.drawable.widget_flame_btn)))

private fun typeColor(t: String) = hex((TYPE_LIGHT[t] ?: TYPE_LIGHT.getValue("routine")).first)

private fun open(ctx: Context, configure: Intent.() -> Unit): Action = actionStartActivity(Intent(ctx, MainActivity::class.java).apply(configure))

/** Barre de progression arrondie : piste discrète, remplissage couleur flamme (ou couleur principale Material You). */
@Composable
private fun WBar(s: WStyle, pct: Float, height: Dp = 8.dp) {
    LinearProgressIndicator(pct.coerceIn(0f, 1f), GlanceModifier.fillMaxWidth().height(height).cornerRadius(height / 2),
        if (s.you) GlanceTheme.colors.primary else ColorProvider(Color(0xFFFF4A12)), s.track)
}

/* ------------------------------------------------------------------ Ta journée */

/** Widget « Ta journée » : série, validations du jour et bouton de validation de chaque objectif. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent { GlanceTheme { Content(context) } } }

    @Composable
    private fun Content(ctx: Context) {
        val app = ctx.peps; val pg = app.pg; val u = pg.me(); val s = style(ctx)
        Column(GlanceModifier.fillMaxSize().wBg(s).padding(14.dp).clickable(open(ctx) { putExtra(MainActivity.EXTRA_TAB, "today") })) {
            if (u == null) { Text("Connecte-toi à PepsGoal pour suivre ta journée.", style = TextStyle(color = s.ink)); return@Column }
            val st = pg.globalStreak(u.id); val plan = pg.todayPlan(u.id); val done = plan.due.count { it.done }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(if (st.lit) R.drawable.ic_flame else R.drawable.ic_flame_dim), "Série", GlanceModifier.size(30.dp))
                Column(GlanceModifier.defaultWeight().padding(start = 8.dp)) {
                    Text(pl(st.n, "jour de série", "jours de série"), style = TextStyle(color = s.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp), maxLines = 1)
                    Text(if (plan.due.isEmpty()) "Rien de prévu aujourd’hui" else "$done/${plan.due.size} validé${if (done > 1) "s" else ""}${if (!st.lit && st.n > 0) " · flamme à rallumer" else ""}",
                        style = TextStyle(color = s.ink2, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
                Box(GlanceModifier.size(40.dp).wAccent(s, 20.dp).clickable(open(ctx) { action = MainActivity.ACTION_NEW_GOAL }), contentAlignment = Alignment.Center) {
                    Text("+", style = TextStyle(color = if (s.you) GlanceTheme.colors.onPrimary else ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 22.sp))
                }
            }
            if (plan.due.isNotEmpty()) { Spacer(GlanceModifier.height(8.dp)); WBar(s, done.toFloat() / plan.due.size, 6.dp) }
            Spacer(GlanceModifier.height(8.dp))
            val list = plan.due.filter { !(app.prefs.widgetHideDone && it.done) }.sortedBy { it.done }.take(12)
            if (list.isEmpty()) Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (plan.due.isEmpty()) "Profite de ta journée ✌️" else "Tout est validé, bravo ! 🎉", style = TextStyle(color = s.ink2, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center))
            }
            else LazyColumn(GlanceModifier.fillMaxSize()) {
                items(list, itemId = { it.g.id.hashCode().toLong() }) { x ->
                    val tc = typeColor(x.g.type)
                    Column(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(GlanceModifier.fillMaxWidth().wCard(s).padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Liseré aux couleurs du type d'objectif
                            Box(GlanceModifier.width(4.dp).height(24.dp).cornerRadius(2.dp).background(ColorProvider(tc))) {}
                            Text(x.g.icon, GlanceModifier.padding(start = 8.dp), style = TextStyle(fontSize = 18.sp))
                            Text(x.g.title, GlanceModifier.defaultWeight().padding(start = 8.dp), style = TextStyle(color = if (x.done) s.ink2 else s.ink, fontSize = 13.sp, fontWeight = if (x.done) FontWeight.Normal else FontWeight.Bold), maxLines = 1)
                            Box(GlanceModifier.size(30.dp).cornerRadius(15.dp)
                                .then(if (x.done) GlanceModifier.background(ColorProvider(tc)) else GlanceModifier.background(ImageProvider(if (s.dark) R.drawable.widget_check_dark else R.drawable.widget_check_light)))
                                .clickable(actionRunCallback<ValidateAction>(actionParametersOf(GoalKey to x.g.id))), contentAlignment = Alignment.Center) {
                                if (x.done) Text("✓", style = TextStyle(color = ColorProvider(Color(0xFF10102A)), fontWeight = FontWeight.Bold, fontSize = 15.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ Ta série */

/** Widget « Ta série » : grande flamme, nombre de jours et les 7 derniers jours (flamme, gel ou jour manqué). */
class StreakWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent { GlanceTheme { Content(context) } } }

    @Composable
    private fun Content(ctx: Context) {
        val app = ctx.peps; val pg = app.pg; val u = pg.me(); val s = style(ctx); val size = LocalSize.current
        Column(GlanceModifier.fillMaxSize().wBg(s).padding(12.dp).clickable(open(ctx) { putExtra(MainActivity.EXTRA_TAB, "profile") }),
            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            if (u == null) { Text("PepsGoal", style = TextStyle(color = s.ink, fontWeight = FontWeight.Bold)); return@Column }
            val st = pg.globalStreak(u.id)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(if (st.lit) R.drawable.ic_flame else R.drawable.ic_flame_dim), "Flamme", GlanceModifier.size(if (size.height < 100.dp) 30.dp else 40.dp))
                Text("${st.n}", GlanceModifier.padding(start = 4.dp), style = TextStyle(color = s.ink, fontWeight = FontWeight.Bold, fontSize = if (size.height < 100.dp) 26.sp else 36.sp))
            }
            Text(if (st.n == 1) "jour de série" else "jours de série", style = TextStyle(color = s.ink2, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            if (size.height >= 120.dp) {
                Spacer(GlanceModifier.height(10.dp))
                val active = pg.activeDays(u.id).toSet(); val frozen = u.frozenDays.toSet(); val today = todayDate()
                Row(GlanceModifier.fillMaxWidth().wCard(s).padding(vertical = 6.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    for (k in 6 downTo 0) {
                        val d = today.minusDays(k.toLong()); val key = ymd(d)
                        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(ImageProvider(when { key in active -> R.drawable.ic_flame; key in frozen -> R.drawable.ic_snow; else -> R.drawable.ic_flame_dim }), WD[wdIdx(d)], GlanceModifier.size(16.dp))
                            Text(WD[wdIdx(d)], style = TextStyle(color = if (k == 0) s.ink else s.ink2, fontSize = 10.sp, fontWeight = if (k == 0) FontWeight.Bold else FontWeight.Medium))
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ Ta progression */

/** Widget « Ta progression » : pourcentage du jour, barre de validations et niveau en cours. */
class ProgressWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent { GlanceTheme { Content(context) } } }

    @Composable
    private fun Content(ctx: Context) {
        val app = ctx.peps; val pg = app.pg; val u = pg.me(); val s = style(ctx); val size = LocalSize.current
        Column(GlanceModifier.fillMaxSize().wBg(s).padding(14.dp).clickable(open(ctx) { putExtra(MainActivity.EXTRA_TAB, "today") })) {
            if (u == null) { Text("Connecte-toi à PepsGoal.", style = TextStyle(color = s.ink)); return@Column }
            val plan = pg.todayPlan(u.id); val done = plan.due.count { it.done }; val total = plan.due.size
            val lv = pg.level(pg.points(u.id))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Aujourd’hui", GlanceModifier.defaultWeight(), style = TextStyle(color = s.ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                Text("$done/$total", style = TextStyle(color = s.ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            }
            Text(if (total == 0) "Repos" else "${Math.round(done * 100f / total)} %", style = TextStyle(color = s.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold))
            WBar(s, if (total == 0) 1f else done.toFloat() / total, 8.dp)
            if (size.height >= 130.dp) {
                Spacer(GlanceModifier.height(10.dp))
                Column(GlanceModifier.fillMaxWidth().wCard(s).padding(10.dp)) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Image(ImageProvider(R.drawable.ic_flame), "Niveau", GlanceModifier.size(16.dp))
                        Text("Niveau ${lv.name}", GlanceModifier.defaultWeight().padding(start = 4.dp), style = TextStyle(color = s.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text("${fmtNum(lv.pts)}", style = TextStyle(color = s.ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    WBar(s, lv.pct.toFloat(), 6.dp)
                    if (lv.next != null) Text("Encore ${fmtNum(lv.next!! - lv.pts)} flammes avant ${lv.nextName}", GlanceModifier.padding(top = 4.dp), style = TextStyle(color = s.ink2, fontSize = 11.sp), maxLines = 1)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ Accès rapide */

/** Widget « Accès rapide » : quatre raccourcis ronds vers les actions les plus utilisées. */
class QuickWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent { GlanceTheme { Content(context) } } }

    @Composable
    private fun Content(ctx: Context) {
        val s = style(ctx); val size = LocalSize.current; val social = ctx.peps.pg.socialOn()
        val items = listOf(
            Triple("+", "Objectif", open(ctx) { action = MainActivity.ACTION_NEW_GOAL }),
            Triple("☀️", "Journée", open(ctx) { action = MainActivity.ACTION_TODAY }),
        ) + if (social) listOf(Triple("💬", "Messages", open(ctx) { action = MainActivity.ACTION_MESSAGES }), Triple("📰", "Feed", open(ctx) { putExtra(MainActivity.EXTRA_TAB, "feed") }))
            else listOf(Triple("🎯", "Objectifs", open(ctx) { putExtra(MainActivity.EXTRA_TAB, "goals") }), Triple("👤", "Profil", open(ctx) { putExtra(MainActivity.EXTRA_TAB, "profile") }))
        val btn = if (size.height < 90.dp) 38.dp else 46.dp
        Row(GlanceModifier.fillMaxSize().wBg(s).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { k, (ic, label, act) ->
                Column(GlanceModifier.defaultWeight().clickable(act), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.size(btn).then(if (k == 0) GlanceModifier.wAccent(s, btn / 2) else GlanceModifier.wCard(s).cornerRadius(btn / 2)), contentAlignment = Alignment.Center) {
                        Text(ic, style = TextStyle(color = if (k == 0) (if (s.you) GlanceTheme.colors.onPrimary else ColorProvider(Color.White)) else s.ink, fontSize = if (k == 0) 22.sp else 18.sp, fontWeight = FontWeight.Bold))
                    }
                    if (size.height >= 70.dp) Text(label, GlanceModifier.padding(top = 3.dp), style = TextStyle(color = s.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ Cap du jour */

class CapWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CapWidget() }

/** Ajoute la plus petite quantité proposée (1 000 pas pour un cap de 10 000…) au cap du jour, depuis le widget. */
class CapAddAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.peps; val g = app.pg.goalById(parameters[GoalKey]) ?: return
        app.pg.capAdd(g, app.pg.capQuick(g).first(), silent = true)
        LiveUpdates.refresh(context); WidgetUpdater.update(context)
    }
}

/** Jauge circulaire dessinée en image (Glance n'a pas de dessin libre). */
private fun ringBitmap(pct: Float, dark: Boolean, color: Int): android.graphics.Bitmap {
    val n = 240; val b = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888); val c = android.graphics.Canvas(b)
    val sw = 26f; val r = android.graphics.RectF(sw / 2, sw / 2, n - sw / 2, n - sw / 2)
    val track = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = sw; this.color = if (dark) 0x26FFFFFF else 0x170D0E2B }
    val fill = android.graphics.Paint(track).apply { this.color = color; strokeCap = android.graphics.Paint.Cap.ROUND }
    c.drawArc(r, 0f, 360f, false, track)
    if (pct > 0f) c.drawArc(r, -90f, 360f * pct.coerceIn(0f, 1f), false, fill)
    return b
}

/** Widget « Cap du jour » : jauge du premier cap quotidien (favori d'abord) et bouton « + ». */
class CapWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent { GlanceTheme { Content(context) } } }

    @Composable
    private fun Content(ctx: Context) {
        val app = ctx.peps; val pg = app.pg; val u = pg.me(); val s = style(ctx); val size = LocalSize.current
        val g = u?.let { me -> pg.myGoals(me.id).filter { it.type == "cap" && !it.archived }.sortedByDescending { pg.isFav(it) }.firstOrNull() }
        Column(GlanceModifier.fillMaxSize().wBg(s).padding(12.dp).clickable(open(ctx) { if (g != null) putExtra(MainActivity.EXTRA_PROGRESS, g.id) else action = MainActivity.ACTION_NEW_GOAL }),
            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            if (u == null) { Text("Connecte-toi à PepsGoal.", style = TextStyle(color = s.ink)); return@Column }
            if (g == null) {
                Text("Cap du jour", style = TextStyle(color = s.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                Text("Crée un objectif « Cap quotidien » (10 000 pas…) pour le suivre ici.", style = TextStyle(color = s.ink2, fontSize = 12.sp, textAlign = TextAlign.Center))
                return@Column
            }
            val v = pg.capDay(g); val pct = (v / g.target).toFloat(); val done = v >= g.target
            val ring = minOf(size.width, size.height - 40.dp).coerceAtLeast(56.dp)
            Box(GlanceModifier.size(ring), contentAlignment = Alignment.Center) {
                Image(ImageProvider(ringBitmap(pct, s.dark, if (done) 0xFF2EC27E.toInt() else android.graphics.Color.rgb(0, 209, 178))), "${Math.round(pct * 100)} %", GlanceModifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(g.icon, style = TextStyle(fontSize = 18.sp))
                    Text(fmtNum(Math.round(v * 10) / 10.0), style = TextStyle(color = s.ink, fontSize = if (ring > 110.dp) 22.sp else 16.sp, fontWeight = FontWeight.Bold))
                    Text("/ ${fmtNum(g.target)} ${g.unit}".trim(), style = TextStyle(color = s.ink2, fontSize = 10.sp), maxLines = 1)
                }
            }
            Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (done) "Cap atteint ✓" else g.title, GlanceModifier.defaultWeight(), style = TextStyle(color = s.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Box(GlanceModifier.size(32.dp).wAccent(s, 16.dp).clickable(actionRunCallback<CapAddAction>(actionParametersOf(GoalKey to g.id))), contentAlignment = Alignment.Center) {
                    Text("+", style = TextStyle(color = ColorProvider(Color(0xFF10102A)), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
