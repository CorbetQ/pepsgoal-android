package fr.pepsgoal.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Fs
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.sheets.SheetHead
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.PLUM
import fr.pepsgoal.app.sys.Perf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.launch
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.Goal
import fr.pepsgoal.core.model.unit

/* ------------------------------------------------------------------ Carte Série */

/**
 * Carte Série, l'emblème de l'accueil : aplat du dégradé de la flamme à marche, nombre de jours en Unbounded 60 qui
 * roule quand il change, médaillon blanc incliné où la flamme danse, message selon l'état, semaine en pastilles et
 * pilules (jokers, record, faits). Jamais la couleur seule : l'état est aussi écrit. Un toucher ouvre la feuille de série.
 */
@Composable
fun HomeHeader(ui: AppState, plan: Plan, st: Streak, lv: Level) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val done = plan.due.count { it.done }; val total = plan.due.size
    val vac = pg.vacationOf()?.takeIf { today() >= it.first }
    val best = remember(ui.rev, u.id) { maxOf(pg.bestStreak(u.id), st.n) }
    val frozen = today() in u.frozenDays
    val left = total - done
    val msg = when {
        pg.isHardDay() -> "Journée difficile 💛 : seuls tes essentiels comptent aujourd’hui."
        vac != null -> "En vacances jusqu’au ${fmtDate(vac.second)} 🏖️ : tes séries sont en pause."
        pg.isRest() -> "Jour de repos 😴 : ta série est protégée aujourd’hui."
        frozen -> "Joker utilisé 🧊 : ta série est sauve. On repart aujourd’hui !"
        total > 0 && done == total -> "Journée parfaite ! Ta flamme n’a jamais autant brillé."
        st.lit && left > 0 -> "Ta flamme brûle ! Plus que ${pl(left, "objectif", "objectifs")} pour une journée parfaite."
        st.lit -> "Ta flamme brûle ! Continue sur ta lancée."
        st.n > 0 -> "Ta série est à protéger : valide avant minuit."
        else -> "On reprend aujourd’hui : valide une action pour allumer ta flamme."
    }
    // États : allumée (flamme), gelée (glace), éteinte (surface neutre)
    val off = st.n == 0 && !st.lit
    val fill: Brush = when {
        p.contrast -> SolidColor(p.surface)
        off -> SolidColor(p.surface)
        frozen && !st.lit -> Brush.linearGradient(listOf(p.ice, Color(0xFF7C8CFF)))
        p.materialYou -> Brush.linearGradient(listOf(Color(0xFFFFB000), Color(0xFFFF4A12), Color(0xFFFF3D96)))
        else -> p.flameBrush()
    }
    val stepC = when { p.contrast -> p.ink; off -> p.edge; frozen && !st.lit -> Color(0xFF4A5AC8); p.materialYou -> Color(0xFFC2185B); else -> p.flameStep }
    val ink = if (off || p.contrast) p.ink else if (p.materialYou || (frozen && !st.lit)) PLUM else p.onAccent
    val desc = "Série de ${pl(st.n, "jour", "jours")}, ${pl(u.freezes, "joker", "jokers")}, record $best, $done ${if (done > 1) "objectifs" else "objectif"} sur $total ${if (done > 1) "faits" else "fait"}. $msg"
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter)
    val fontScale = LocalDensity.current.fontScale
    val bigText = fontScale >= 1.6f
    // Chiffre de série : 60 sp, plafonné à 72 sp effectifs avec les grands caractères
    val heroSize = minOf(60f, 72f / fontScale.coerceAtLeast(.5f)).sp
    Box(Modifier.fillMaxWidth().chunk(fill, stepC, 28.dp, 6.dp, if (off || p.contrast) 2.dp else 0.dp, press).clip(RoundedCornerShape(28.dp))
        .springClick(inter, label = "Ouvrir ma série") { ui.open(Sheet.Streak) }.semantics(mergeDescendants = true) { contentDescription = desc }) {
        // Décor en aplat : deux cercles blancs translucides, sans flou
        if (!off && !p.contrast) Box(Modifier.matchParentSize().drawBehind {
            drawCircle(Color.White.copy(alpha = .16f), size.height * .55f, Offset(size.width * .98f, size.height * .1f))
            drawCircle(Color.White.copy(alpha = .12f), size.height * .18f, Offset(size.width * .7f, size.height * .95f))
        })
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            val number = @Composable {
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingNumber(st.n, size = heroSize, color = ink)
                    Spacer(Modifier.width(8.dp))
                    Text(if (st.n > 1) "jours\nde série" else "jour\nde série", Modifier.padding(bottom = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ink, lineHeight = 18.sp, maxLines = 2)
                }
            }
            val medal = @Composable { StreakMedal(ui, st) }
            if (bigText) {
                // Grands caractères : le chiffre passe au-dessus du médaillon
                number(); Spacer(Modifier.height(8.dp)); medal()
                Text(msg, Modifier.padding(top = 8.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink, lineHeight = 20.sp)
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    number()
                    Text(msg, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink, lineHeight = 20.sp)
                }
                Spacer(Modifier.width(8.dp)); medal()
            }
            Spacer(Modifier.height(14.dp))
            WeekStrip(pg, u.id, onColor = !off && !p.contrast)
            Spacer(Modifier.height(14.dp))
            FlowRowCompat(gap = 8.dp) {
                StreakPill("🧊", pl(u.freezes, "joker", "jokers"), off)
                StreakPill("🏆", "Record $best", off)
                StreakPill("✅", "$done/$total ${if (done > 1) "faits" else "fait"}", off)
            }
        }
    }
}

/** Pilule d'information de la carte Série : blanc translucide, emoji et texte prune. Plate. */
@Composable
private fun StreakPill(e: String, s: String, neutral: Boolean) {
    val p = LocalPal.current
    Row(Modifier.heightIn(min = 32.dp).clip(CircleShape).background(if (neutral || p.contrast) p.surface2 else Color.White.copy(alpha = .42f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Emoji(e, 16.dp); Text(s, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (neutral || p.contrast) p.ink else PLUM, maxLines = 1)
    }
}

/**
 * Médaillon blanc de 88 dp incliné de −6° : la flamme de 64 dp y danse (rotation ±4°, étirement ±6 %, 1,4 s à 15 i/s),
 * puis s'arrête après 20 s sans toucher. Il fait un saut quand la série monte. Chatouilles : surprise après 7 touchers.
 */
@Composable
fun StreakMedal(ui: AppState, st: Streak) {
    val p = LocalPal.current
    var awake by remember { mutableIntStateOf(0) }
    var dancing by remember { mutableStateOf(true) }
    LaunchedEffect(awake) { dancing = true; kotlinx.coroutines.delay(20_000); dancing = false }
    val ph = slowPhaseState(1400, 15, running = dancing && st.lit && Perf.flags.fx)
    val wiggle = remember { Animatable(0f) }; val jump = remember { Animatable(0f) }
    val taps = remember { mutableListOf<Long>() }; val scope = rememberCoroutineScope()
    Box(Modifier.popOnChange(st.n).graphicsLayer { rotationZ = -6f }.size(88.dp, 93.dp).chunk(Color.White, p.flameStep.copy(alpha = .55f), 44.dp, 5.dp, 0.dp)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Flamme") {
            awake++
            val now = System.currentTimeMillis(); taps.removeAll { now - it > 2500 }; taps.add(now)
            Feedback.ramp((taps.size / 7f).coerceAtMost(1f))
            scope.launch { wiggle.snapTo(if (taps.size % 2 == 0) 1f else -1f); wiggle.animateTo(0f, spring(dampingRatio = .25f, stiffness = 900f)) }
            if (taps.size >= 7) { taps.clear(); scope.launch { jump.snapTo(0f); jump.animateTo(1f, tween(700)); jump.snapTo(0f) }; ui.egg("tickle", "Hé ! Arrête, ça chatouille ! 🔥", "confetti") }
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.graphicsLayer {
            val a = kotlin.math.sin(ph.value * 2f * kotlin.math.PI.toFloat())
            val j = kotlin.math.sin(jump.value * kotlin.math.PI.toFloat())
            rotationZ = a * 4f + wiggle.value * 12f + jump.value * 360f
            scaleY = 1f + .06f * kotlin.math.cos(ph.value * 4f * kotlin.math.PI.toFloat()) * (if (dancing && st.lit) 1f else 0f) + .2f * j
            scaleX = 1f + .2f * j; translationY = -j * 30.dp.toPx()
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
        }) { Flame(64.dp, dim = !st.lit) }
    }
}

/** Feuille de la série : semaine, record, jokers, et les actions de la journée (repos, journée difficile, bilan…). */
@Composable
fun StreakSheet(ui: AppState) {
    val pg = ui.pg; val u = pg.me() ?: return
    val st = remember(ui.rev) { pg.globalStreak(u.id) }; val plan = remember(ui.rev) { pg.todayPlan(u.id) }
    val done = plan.due.count { it.done }; val total = plan.due.size
    val vac = pg.vacationOf()?.takeIf { today() >= it.first }
    SheetHead(ui, "Ma série")
    StatGrid(listOf("${st.n}" to if (st.n > 1) "jours de série" else "jour de série", "${maxOf(pg.bestStreak(u.id), st.n)}" to "record", "${u.freezes}" to if (u.freezes > 1) "jokers" else "joker"), columns = 3, emojis = listOf("🔥", "🏆", "🧊"))
    Glass { CardHead("Cette semaine", emoji = "📅"); WeekStrip(pg, u.id) }
    SectionTitle("Ma journée", "☀️")
    FlowRowCompat(gap = 8.dp) {
        if (total > 0 && done == total) FChip("✨ Journée parfaite", false, { ui.fs = Fs.Celebrate(pg.perfectCelebration()) })
        if (nowHM() >= "18:00") FChip("🌙 Bilan du soir", false, { ui.open(Sheet.Evening) })
        FChip(if (vac != null) "🏖️ En vacances" else if (pg.isRest()) "😴 Jour de repos" else "😴 Repos et vacances", vac != null || pg.isRest(), { ui.open(Sheet.Rest) })
        FChip("💛 Journée difficile", pg.isHardDay(), { ui.open(Sheet.Hard) })
        FChip("⌨️ Commande", false, { ui.open(Sheet.QuickCmd) })
        FChip("🏅 Niveaux", false, { ui.open(Sheet.Levels) })
    }
}

/** Objectif du mois (module de l'accueil, sous la liste) : tuile, barre et jours réussis. */
@Composable
fun MonthFocusCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    val g = pg.monthFocusGoal() ?: return
    val (d, n) = remember(ui.rev, g.id) { pg.monthFocusProgress(g) }
    Glass(onClick = { ui.open(Sheet.Goal(g.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmojiTile(g.shownIcon(), p.type(g.type).c, 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Objectif du mois", fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(g.shownTitle(), fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Bar(d * 100.0 / maxOf(1, n), color = p.type(g.type).c)
            }
            Text("$d/$n j", fontWeight = FontWeight.ExtraBold, color = p.ink2, fontSize = 14.sp)
        }
    }
}

/* ------------------------------------------------------------------ « À la une » */

/** Tuile d'un moment : emoji, titre, une ligne ; ouvre la carte complète. */
class MomentTile(val key: String, val emoji: String, val title: String, val sub: String)

/** Cartes ponctuelles de l'accueil, regroupées en tuiles « À la une » (affichées seulement quand elles servent). */
val MOMENT_KEYS = setOf("bday", "countdown", "advent", "briefing", "record", "challenge", "cheers", "weekimg", "memories", "quote", "quest", "evening", "wxweek")

@Composable
fun rememberMoments(ui: AppState, keys: List<String>): List<MomentTile> {
    val ctx = LocalContext.current; val pg = ui.pg
    return remember(ui.rev, keys, Recaps.wrappedOpen(), nowHM().take(2)) {
        val out = mutableListOf<MomentTile>()
        val y = yday()
        val no = ui.prefs.raw.getStringSet("catchupNo:$y", emptySet()) ?: emptySet()
        pg.missedYesterday().filter { it.id !in no }.takeIf { it.isNotEmpty() }?.let { out += MomentTile("catchup", "🤔", "Tu l’as fait hier ?", pl(it.size, "objectif à rattraper", "objectifs à rattraper")) }
        if (Recaps.wrappedOpen()) out += MomentTile("wrapped", "🎁", "Ta rétrospective", "Ton année en résumé")
        for (k in keys) when (k) {
            "bday" -> pg.birthdaysToday().takeIf { it.isNotEmpty() }?.let { l -> out += MomentTile(k, "🎂", "Anniversaire", l.joinToString(", ") { first(it.name) }) }
            "countdown" -> pg.countdowns().firstOrNull()?.let { (c, d) -> out += MomentTile(k, c.emoji, c.title, countdownLabel(d)) }
            "advent" -> if (adventOpen() && !pg.adventOpened(todayDate().dayOfMonth)) out += MomentTile(k, "🎄", "Calendrier de l’Avent", "Case du ${todayDate().dayOfMonth} à ouvrir")
            "briefing" -> if (nowHM() < "12:00" && ui.prefs.raw.getString("briefClosed", "") != today()) out += MomentTile(k, "🎧", "Briefing du matin", "Météo, objectifs, série")
            "record" -> pg.recordChase()?.let { r -> out += MomentTile(k, if (r.left == 0) "🏆" else "🎯", "Record en vue", if (r.left == 0) "Record : ${pl(r.cur, "jour", "jours")}" else "Plus que ${pl(r.left, "jour", "jours")}") }
            "challenge" -> if (!pg.challengeDone()) pg.dailyChallenge(ui.prefs.raw.getInt("defiSkip:${today()}", 0)).let { (e, t) -> out += MomentTile(k, e, "Défi du jour", t) }
            "cheers" -> if (pg.socialOn()) pg.friendMilestones().firstOrNull { "${it.u.id}:${it.n}" !in ui.prefs.cheeredMilestones }?.let { m -> out += MomentTile(k, "👏", "Séries de tes amis", "${first(m.u.name)} : ${pl(m.n, "jour", "jours")}") }
            "weekimg" -> { val wd = wdIdx(todayDate()); if ((wd == 6 && nowHM() >= "17:00") || wd == 0) out += MomentTile(k, "🖼️", "Ta semaine en image", "À partager en story") }
            "memories" -> pg.memories().firstOrNull()?.let { m -> out += MomentTile(k, "📸", m.label, pl(m.count, "validation", "validations")) }
            "quote" -> pg.quoteToday().let { (q, _) -> out += MomentTile(k, "💬", "Citation du jour", q) }
            "quest" -> pg.questState()?.takeIf { !it.finished }?.let { q -> out += MomentTile(k, q.quest.emoji, q.quest.title, if (q.canUnlock) "Nouveau chapitre à lire" else "Chapitre ${minOf(q.done + 1, 7)} sur 7") }
            "evening" -> if (nowHM() >= "20:30" && ui.prefs.raw.getString("eveningQuick", "") != today()) out += MomentTile(k, "🌙", "Bilan express", "Ta journée en trois touches")
            "wxweek" -> pg.me()?.settings?.weather?.let { Wx.cached(ctx, it)?.data }?.let { f -> val good = goodOutdoorDays(f).filter { it.day >= today() }
                if (pg.myGoals().any { g -> !g.archived && (g.outdoor || g.planB.isNotBlank()) }) out += MomentTile(k, "🌤️", "Météo de la semaine", good.firstOrNull()?.let { "Meilleur jour : ${weekdayLongDate(parseYmd(it.day)).substringBefore(' ')}" } ?: "Semaine humide") }
        }
        out
    }
}

/** Rangée défilante des moments, suivie d'une tuile pour créer un compte à rebours. */
@Composable
fun MomentsRow(ui: AppState, tiles: List<MomentTile>, showAdd: Boolean = true) {
    val p = LocalPal.current
    val tones = listOf(p.flame, p.type("objective").c, p.type("abstinence").c, p.type("routine").c, p.pink, p.type("monthly").c)
    // La rangée va jusqu'aux bords de l'écran ; les ombres des tuiles ne sont pas coupées
    Row(Modifier.fillMaxWidth().bleed(16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.forEachIndexed { i, t ->
            val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val press = rememberPress(inter)
            Column(Modifier.width(156.dp).height(132.dp).chunk(p.surface, p.edge, 24.dp, 4.dp, 2.dp, press).clip(RoundedCornerShape(22.dp))
                .springClick(inter, label = t.title) { ui.open(Sheet.HomeCard(t.key)) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                EmojiTile(t.emoji, tones[i % tones.size], 40.dp)
                Text(t.title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.sub, fontSize = 12.sp, color = p.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
            }
        }
        if (showAdd) Column(Modifier.width(120.dp).height(132.dp).chunk(p.surface2, p.edge, 24.dp, 4.dp, 2.dp).clip(RoundedCornerShape(22.dp)).clickable(onClickLabel = "Nouveau compte à rebours") { ui.open(Sheet.HomeCard("countdown")) }.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            IconTile("plus", p.flame, p.flame, 38.dp); Text("Compte à rebours", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** Carte complète d'un moment, ouverte depuis sa tuile. */
@Composable
fun HomeCardSheet(ui: AppState, key: String) {
    val pg = ui.pg; val plan = remember(ui.rev) { pg.todayPlan() }
    SheetHead(ui, HOME_CARDS[key]?.first ?: when (key) { "catchup" -> "Rattrapage"; "wrapped" -> "Rétrospective"; else -> "" })
    when (key) {
        "catchup" -> CatchUpCard(ui); "wrapped" -> WrappedCard(ui); "bday" -> BirthdayCard(ui); "countdown" -> CountdownCard(ui)
        "advent" -> AdventCard(ui); "briefing" -> BriefingCard(ui); "record" -> RecordCard(ui); "challenge" -> ChallengeCard(ui)
        "cheers" -> FriendCheersCard(ui); "weekimg" -> WeekImageCard(ui); "memories" -> MemoriesCard(ui); "quote" -> QuoteCard(ui)
        "quest" -> QuestCard(ui); "evening" -> EveningQuickCard(ui, plan); "wxweek" -> WeekWeatherCard(ui)
    }
}

/** Emoji des sections de l'accueil. */
private val HOME_SEC_EMOJI = mapOf("À la une" to "⭐", "Ta journée" to "☀️", "Progression" to "📈", "Dehors" to "🌤️", "Entre amis" to "👥", "Coach" to "✨")

/** Titre de section repliable de l'accueil : emoji, titre en Unbounded 18, flèche dans une pastille. */
@Composable
fun HomeSectionHead(title: String, icon: String, collapsed: Boolean, onToggle: () -> Unit) {
    val p = LocalPal.current
    val rot by animateFloatAsState(if (collapsed) -90f else 0f, androidx.compose.animation.core.spring(dampingRatio = .5f, stiffness = 500f), label = "secRot")
    Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(smoothShape(16.dp)).clickable(onClickLabel = if (collapsed) "Déplier $title" else "Replier $title", onClick = onToggle).padding(horizontal = 4.dp, vertical = 4.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HOME_SEC_EMOJI[title]?.let { Emoji(it, 26.dp) }
        Text(title, Modifier.weight(1f), fontSize = 18.sp, fontFamily = Unbounded, fontWeight = FontWeight.Bold, color = p.ink)
        Box(Modifier.size(32.dp).clip(CircleShape).background(p.surface2), contentAlignment = Alignment.Center) {
            Pi("down", size = 16.dp, tint = p.ink2, modifier = Modifier.graphicsLayer { rotationZ = rot })
        }
    }
}

/* ------------------------------------------------------------------ Comptes à rebours */

private val CD_EMOJIS = listOf("📅", "🏃", "✈️", "🎂", "🎉", "🏖️", "🎓", "💍", "🎄", "🏆", "🎸", "🏔️")

@Composable
fun CountdownCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val ctx = LocalContext.current
    val list = remember(ui.rev) { pg.countdowns() }
    var title by remember { mutableStateOf("") }; var emoji by remember { mutableStateOf("📅") }; var date by remember { mutableStateOf(ymd(todayDate().plusDays(30))) }
    Glass {
        CardHead("Comptes à rebours", emoji = "⏳") { Count(list.size) }
        list.forEach { (c, d) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Emoji(c.emoji, 30.dp)
                Column(Modifier.weight(1f)) { Text(c.title, fontWeight = FontWeight.ExtraBold, maxLines = 1); Muted(cap(weekdayLongDate(parseYmd(c.date))), small = true) }
                Text(if (d == 0) "Jour J !" else "J−$d", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, color = p.flameInk)
                IconBtn("trash", "Supprimer « ${c.title} »", { pg.removeCountdown(c.id); ui.bump() }, plain = true, size = 32.dp)
            }
        }
        if (list.isEmpty()) Muted("Un marathon, des vacances, un examen ? Compte les jours sur ton accueil.", small = true)
        Spacer(Modifier.height(8.dp)); Eyebrow("Nouveau")
        Field(title, { title = it }, "Événement", placeholder = "Marathon de Nantes", max = 40)
        FlowRowCompat(gap = 4.dp) { CD_EMOJIS.forEach { e -> Box(Modifier.size(36.dp).clip(CircleShape).background(if (e == emoji) p.flame.copy(alpha = .25f) else Color.Transparent).clickable { emoji = e }, contentAlignment = Alignment.Center) { Emoji(e, 22.dp) } } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PButton("📅 ${fmtDate(date)}", { val d0 = parseYmd(date); DatePickerDialog(ctx, { _, yy, mm, dd -> date = ymd(java.time.LocalDate.of(yy, mm + 1, dd)) }, d0.year, d0.monthValue - 1, d0.dayOfMonth).show() }, small = true)
            PButton("Ajouter", { if (title.isNotBlank() && date >= today()) { pg.addCountdown(title, emoji, date); title = ""; ui.bump(); Feedback.tick() } }, icon = "plus", small = true, kind = "primary", enabled = title.isNotBlank())
        }
    }
}

/** Accueil classique : les comptes à rebours en cours, en une ligne chacun. */
@Composable
fun CountdownSummary(ui: AppState) {
    val p = LocalPal.current
    val list = remember(ui.rev) { ui.pg.countdowns() }
    Glass(onClick = { ui.open(Sheet.HomeCard("countdown")) }) {
        CardHead("Comptes à rebours", emoji = "⏳") { IconBtn("plus", "Nouveau compte à rebours", { ui.open(Sheet.HomeCard("countdown")) }, plain = true, size = 36.dp) }
        if (list.isEmpty()) Muted("Un marathon, des vacances, un examen ? Touche + pour compter les jours.", small = true)
        list.take(3).forEach { (c, d) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Emoji(c.emoji, 26.dp); Text(c.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(countdownLabel(d), fontWeight = FontWeight.ExtraBold, color = p.flameInk)
            }
        }
    }
}

/* ------------------------------------------------------------------ Commande rapide écrite */

@Composable
fun QuickCmdSheet(ui: AppState) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    SheetHead(ui, "Commande rapide")
    Muted("Écris ce que tu veux faire, comme à la commande vocale : valider, annuler, ajouter un objectif, lancer un Focus…", small = true)
    Field(q, { q = it }, "", placeholder = "Valide sport", max = 120, icon = "edit")
    FlowRowCompat(gap = 6.dp) { VoiceCmd.EXAMPLES.forEach { ex -> FChip(ex, false, { q = ex }) } }
    PButton("Exécuter", { val t = q.trim(); if (t.isNotEmpty()) { ui.close(); VoiceCmd.run(ui, ctx, t.replace("’", "'")) } }, Modifier.fillMaxWidth(), icon = "send", kind = "primary", enabled = q.isNotBlank())
}

/* ------------------------------------------------------------------ Citations personnelles */

@Composable
fun QuotesSheet(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    var text by remember { mutableStateOf("") }; var author by remember { mutableStateOf("") }
    val mine = remember(ui.rev) { pg.me()?.myQuotes?.toList() ?: emptyList() }
    SheetHead(ui, "Mes citations")
    Muted("Tes phrases rejoignent la citation du jour, en alternance avec les autres.", small = true)
    mine.forEach { qq ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("« ${qq.text} »", fontWeight = FontWeight.Bold); if (qq.author.isNotBlank()) Muted("— ${qq.author}", small = true) }
            IconBtn("trash", "Supprimer la citation", { pg.removeQuote(qq.id); ui.bump() }, plain = true, size = 32.dp)
        }
    }
    Field(text, { text = it }, "Citation", placeholder = "Une phrase qui te porte", singleLine = false, minLines = 2, max = 220)
    Field(author, { author = it }, "Auteur (facultatif)", max = 40)
    PButton("Ajouter", { if (text.isNotBlank()) { pg.addQuote(text, author); text = ""; author = ""; ui.bump(); Feedback.tick() } }, Modifier.fillMaxWidth(), icon = "plus", kind = "primary", enabled = text.isNotBlank())
}

/* ------------------------------------------------------------------ Porte-monnaie détaillé */

@Composable
fun WalletSheet(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    val w = remember(ui.rev) { pg.walletBreakdown() }
    SheetHead(ui, "Ton porte-monnaie")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Flame(44.dp); Column { Text(fmtNum(w.wallet), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp); Muted("flammes à dépenser", small = true) }
    }
    @Composable fun Line(e: String, l: String, v: Int, plus: Boolean) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Emoji(e, 22.dp); Text(l, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("${if (plus) "+" else "−"}${fmtNum(v)}", fontWeight = FontWeight.ExtraBold, color = if (plus) p.ok else p.danger)
        }
    }
    Glass(pad = 14.dp) {
        Line("✅", "Gagnées en validant tes objectifs", w.earned, true)
        Line("🏅", "Bonus (badges, défis, coffres, Avent…)", maxOf(0, w.bonus), true)
        Line("🎁", "Reçues en cadeau", w.received, true)
        Line("🛍️", "Dépensées dans la boutique", maxOf(0, w.spent), false)
        Line("💝", "Offertes à des amis (flammes et articles)", w.given, false)
    }
    Muted("Ton niveau compte toutes les flammes gagnées : dépenser ou offrir ne le fait jamais baisser.", small = true)
    PButton("Ouvrir la boutique", { ui.close(); ui.push(Screen.Shop) }, Modifier.fillMaxWidth(), icon = "star")
}

/* ------------------------------------------------------------------ Options d'un objectif */

/** Options « Objectif du mois », « Objectif discret » et « Mobilité douce » de la fiche d'un objectif. */
@Composable
fun GoalExtras61(ui: AppState, g: Goal) {
    val p = LocalPal.current; val pg = ui.pg
    var focus by remember(g.id, ui.rev) { mutableStateOf(pg.monthFocusGoal()?.id == g.id) }
    var disc by remember(g.id, ui.rev) { mutableStateOf(g.discreet) }
    fr.pepsgoal.app.ui.sheets.OptionRow("target", p.flame, p.pink, "Objectif du mois", if (focus) "Mis en avant sur l’accueil ce mois-ci" else "Le mettre en avant sur l’accueil ce mois-ci", false, { focus = !focus; pg.setMonthFocus(if (focus) g else null); ui.bump() },
        right = { PSwitch(focus, { focus = it; pg.setMonthFocus(if (it) g else null); ui.bump() }) })
    fr.pepsgoal.app.ui.sheets.OptionRow("lock", p.type("period").c, p.type("objective").c, "Objectif discret", if (disc) "Titre masqué sur l’accueil, les widgets et les notifications" else "Masquer son titre aux regards indiscrets", false, { disc = !disc; g.discreet = disc; pg.store.commit(); ui.bump() },
        right = { PSwitch(disc, { disc = it; g.discreet = it; pg.store.commit(); ui.bump() }) })
    var mob by remember(g.id) { mutableStateOf(false) }
    val (km, co2) = remember(g.id, ui.rev) { pg.mobilityImpact(g) }
    fr.pepsgoal.app.ui.sheets.OptionRow("leaf", p.type("routine").c, p.type("measure").c, "Mobilité douce", if (g.mobility.isNotEmpty()) "${MOBILITY[g.mobility]?.first ?: ""} ${fmtNum(round1(km))} km · ${fmtNum(round1(co2))} kg de CO₂ évités" else "Vélo, marche, tram… calcule le CO₂ évité", mob, { mob = !mob }) {
        Muted("Comparé à un trajet seul en voiture (ordres de grandeur de l’ADEME).", small = true)
        FlowRowCompat(gap = 6.dp) {
            FChip("Aucune", g.mobility.isEmpty(), { g.mobility = ""; pg.store.commit(); ui.bump() })
            MOBILITY.forEach { (k, v) -> FChip("${v.first} ${v.second}", g.mobility == k, { g.mobility = k; pg.store.commit(); ui.bump() }) }
        }
        if (g.mobility.isNotEmpty() && !g.unit.lowercase().startsWith("km")) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Distance d’un trajet", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            IconBtn("minus", "Moins", { g.tripKm = maxOf(0.0, g.tripKm - .5); pg.store.commit(); ui.bump() }, size = 32.dp)
            Text("${fmtNum(g.tripKm)} km", fontWeight = FontWeight.ExtraBold)
            IconBtn("plus", "Plus", { g.tripKm = g.tripKm + .5; pg.store.commit(); ui.bump() }, size = 32.dp)
        }
    }
}
