package fr.pepsgoal.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.Fs
import fr.pepsgoal.app.peps
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.PLUM
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import fr.pepsgoal.core.cloud.pushChallengeDays

/** Valider (ou ouvrir la saisie adaptée au type). */
fun doValidate(ui: AppState, id: String) {
    val pg = ui.pg; val g = pg.goalById(id); val before = myDone(pg, id)
    when (val r = pg.validate(id)) {
        is Pg.VResult.AskProgress -> ui.open(Sheet.Progress(r.goalId))
        is Pg.VResult.OpenGoal -> ui.open(Sheet.Goal(r.goalId))
        else -> {}
    }
    // Validation simple : « Annuler » pendant 5 secondes
    if (g != null && myDone(pg, id) > before && g.type in setOf("routine", "abstinence", "limit", "timed", "challenge", "deadline")) {
        ui.undoable("« ${g.title} » validé.", "check") {
            when {
                g.type == "deadline" -> { pg.reopen(id); ui.toast("Validation annulée.", "x") }
                g.type == "challenge" && g.completedAt != null -> ui.toast("Challenge terminé : ouvre l’objectif pour le rouvrir.", "x")
                myDone(pg, id) > before -> pg.validate(id)
            }
            pg.hooks.liveChanged()
        }
    } else if (g != null && myDone(pg, id) < before) Feedback.undo()
    // Défi de la communauté : le jour validé (ou annulé) est reporté au classement
    if (g?.let { it.data["communityId"] } != null && ui.pg.store.acct) ui.scope.launch { try { ui.cloud.pushChallengeDays() } catch (e: Exception) {} }
}

private fun myDone(pg: Pg, id: String) = pg.db.checkins.count { it.goalId == id && it.kind == "done" && it.userId == pg.meId() }

/** Archiver (ou réactiver) avec « Annuler ». */
fun archiveWithUndo(ui: AppState, id: String) {
    val pg = ui.pg; pg.archive(id, toast = false)
    val on = pg.goalById(id)?.archived == true
    ui.undoable(if (on) "Objectif archivé." else "Objectif réactivé.", "archive") { pg.archive(id, toast = false) }
}

/** Supprimer avec « Annuler » : l'objectif et ses validations sont gardés en mémoire le temps du message. */
fun deleteWithUndo(ui: AppState, id: String) {
    val pg = ui.pg; val g = pg.goalById(id) ?: return
    val checks = pg.db.checkins.filter { it.goalId == id }; val posts = pg.db.posts.filter { it.goalId == id }.map { it.id }
    pg.deleteGoal(id, toast = false); pg.hooks.liveChanged()
    ui.undoable("« ${g.title} » supprimé.", "trash") { pg.restoreGoal(g, checks, posts) }
}

/** Objectifs reportés à demain depuis les actions rapides : masqués de « À valider aujourd'hui » jusqu'à minuit. */
object Snooze {
    fun ids(ui: AppState): Set<String> {
        val (d, l) = ui.prefs.snoozed.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return if (d == today()) l.split(',').filter { it.isNotBlank() }.toSet() else emptySet()
    }
    fun set(ui: AppState, id: String, on: Boolean) {
        val now = ids(ui).let { if (on) it + id else it - id }
        ui.prefs.snoozed = "${today()}|${now.joinToString(",")}"; ui.bump()
    }
}

/** Seconde courante (minuteur en marche). */
@Composable
fun tickingNow(active: Boolean): Long {
    var now by remember { mutableLongStateOf(nowMs()) }
    LaunchedEffect(active) { while (active) { delay(1000); now = nowMs() } }
    return now
}

@Composable
fun AvStack(pg: Pg, ids: List<String>, size: Dp = 22.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) { ids.mapNotNull { pg.userById(it) }.take(4).forEach { Av(it, size, modifier = Modifier.border(2.dp, LocalPal.current.surface, CircleShape)) } }
}

@Composable
fun FavStar(pg: Pg, g: Goal) { if (pg.isFav(g)) Pi("star", size = 14.dp, tint = LocalPal.current.flame2) }

/** Pas d'ajout rapide d'un objectif chiffré (« appui = + 1 pas ») : 10 % du cap arrondi, 1 pour les autres. null : pas d'ajout rapide. */
fun stepOf(pg: Pg, g: Goal): Double? = when (g.type) {
    "cap" -> pg.capQuick(g).firstOrNull() ?: 1.0
    "objective", "monthly", "program", "group", "duel" -> 1.0
    else -> null
}

/** Ajoute un pas (ou [n] pas) à un objectif chiffré, avec un message « Annuler ». */
fun addStep(ui: AppState, g: Goal, n: Int = 1) {
    val pg = ui.pg; val st = (stepOf(pg, g) ?: return) * n
    if (g.type == "cap") pg.capAdd(g, st, silent = true) else pg.addProgress(g.id, st)
    Feedback.tick()
    val v = when (g.type) { "cap" -> pg.capDay(g); "monthly" -> pg.monthProgress(g); "objective" -> pg.objProgress(g); else -> null }
    val sfx = pg.unitSfx(g)
    ui.undoable("+${fmtNum(st)}$sfx${if (v != null) " · ${fmtNum(round1(v))} / ${fmtNum(g.target)}$sfx" else ""}", "bolt") {
        if (g.type == "cap") pg.capAdd(g, -st, silent = true) else pg.addProgress(g.id, -st)
        pg.hooks.liveChanged()
    }
    pg.hooks.liveChanged()
}

/** Moment de la journée d'un objectif, d'après l'heure de son rappel (ou de son créneau) : morning, afternoon, evening ou day. */
fun momentOf(pg: Pg, g: Goal): String {
    val at = pg.effectiveRemind(g) ?: when (g.type) { "timed" -> g.time; "period" -> g.start; else -> null }
    val h = at?.takeIf { it.length >= 2 }?.substring(0, 2)?.toIntOrNull() ?: return "day"
    return when { h < 12 -> "morning"; h < 18 -> "afternoon"; else -> "evening" }
}

/**
 * Ligne d'un objectif (accueil) : une seule ligne pour les 16 types. Volume « à marche » (rayon 22, 76 dp minimum),
 * tuile néon de 52 dp, titre, sous-ligne propre au type, barre néon de 8 dp pour les types chiffrés et une seule
 * commande à droite (case à valider, progrès ou minuteur). Faite, elle prend la teinte de son néon.
 * Gestes : balayage à droite (valider), à gauche (actions rapides), appui (fiche), appui long (Focus d'une durée).
 * [tag] : étiquette du type sous le titre (écran Objectifs).
 */
@Composable
fun GoalRow(ui: AppState, g: Goal, done: Boolean, metaOverride: String? = null, tag: Boolean = false) {
    val p = LocalPal.current; val pg = ui.pg; val tc = p.type(g.type)
    val running = pg.timerOf(g) != null
    val now = tickingNow(running)
    val just = done && pg.justDone == g.id
    val title = g.shownTitle()
    var meta = ""; var urgent = false; var bar: Double? = null; var extra: @Composable (() -> Unit)? = null
    // Commande de droite : case à valider, progrès (anneau + « + ») ou minuteur (anneau + lecture / pause)
    var ctlLabel = if (done) "Annuler la validation : $title" else "Valider : $title"
    var ctlProg: Float? = null; var ctlIcon = "check"; var ctlDisabled = false; var ctlLong: (() -> Unit)? = null
    var ctlClick: () -> Unit = { doValidate(ui, g.id) }
    val progress = { v: Double -> (v / 100.0).coerceIn(0.0, 1.0).toFloat() }
    // Case de progrès : un appui = + 1 pas (le pas monte au-dessus de la case), appui long = saisie d'une quantité
    var stepTick by remember(g.id) { mutableIntStateOf(0) }
    val stepV = stepOf(pg, g)
    var stepLbl: String? = null
    when (g.type) {
        "limit" -> { val used = pg.limitUsed(g); val over = used > g.max
            meta = "${fmtNum(used)}/${fmtNum(g.max)} ${g.unit} ${if (g.period == "week") "cette semaine" else "aujourd'hui"}${if (over) " · limite dépassée" else ""}"; urgent = over; bar = used / maxOf(g.max, 1.0) * 100
            // Toucher : +1 ; appui long : valider la journée sous la limite
            if (!done) { ctlProg = progress(bar!!); ctlLabel = "Ajouter 1 ${g.unit} : $title"; ctlClick = { pg.limitAdd(g.id) }; ctlLong = { doValidate(ui, g.id) }; ctlDisabled = false }
            else ctlLabel = "Annuler la validation : $title" }
        "duration" -> { val m = pg.durationLiveMins(g).also { now.hashCode() }; meta = "${fmtClock(m * 60)} / ${fmtNum(g.minutes)} min"; bar = m / g.minutes * 100
            ctlProg = progress(bar!!); ctlIcon = if (running) "pause" else if (done) "check" else "play"
            ctlLabel = if (running) "Mettre en pause : $title" else "Démarrer le minuteur : $title"; ctlClick = { pg.toggleTimer(g) }; ctlLong = if (!done) ({ quickFocus(ui, g) }) else null }
        "measure" -> { meta = "Dernière : ${pg.measureStat(g).v}"; ctlProg = 0f; ctlLabel = "Nouvelle mesure : $title" }
        "cap" -> { val v = pg.capDay(g); meta = "${fmtNum(v)} / ${fmtNum(g.target)}${pg.unitSfx(g)} aujourd’hui"; bar = minOf(100.0, v / g.target * 100)
            ctlProg = progress(bar!!); ctlLabel = if (done) "Ajouter encore : $title" else "Ajouter : $title" }
        "timed" -> { val w = pg.timedWindow(g); meta = w.txt; urgent = !w.open && !done; ctlDisabled = !w.open && !done
            ctlLabel = if (g.mode == "notafter") "J'ai tenu hier soir : $title" else ctlLabel }
        "period" -> { val w = pg.periodWindow(g); meta = w.txt; urgent = !w.open && !done; ctlDisabled = !w.open && !done }
        "project" -> { val (d, n) = pg.projectCounts(g); meta = "$d/$n étapes"; bar = if (n > 0) d * 100.0 / n else 0.0
            ctlProg = progress(bar!!); ctlLabel = "Ouvrir les étapes : $title"; ctlClick = { ui.open(Sheet.Goal(g.id)) } }
        "challenge" -> { meta = "Jour ${pg.challengeDay(g)}/${g.length}"; bar = pg.challengeDone(g) * 100.0 / g.length }
        "program" -> { val w = pg.programWeek(g); val s = pg.programSum(g, w); val t = pg.programTarget(g, w); meta = "Semaine $w/${g.weeks} : ${fmtNum(s)}/${fmtNum(t)} ${g.unit}"; bar = s / t * 100
            ctlProg = progress(bar!!); ctlLabel = "Ajouter un progrès : $title"; ctlLong = { ui.open(Sheet.Goal(g.id)) } }
        "group" -> { val t = pg.groupTotal(g); meta = "${fmtNum(round1(t))}/${fmtNum(g.target)} ${g.unit} ensemble"; bar = t / g.target * 100; extra = { AvStack(pg, g.members ?: emptyList()) }
            ctlProg = progress(bar!!); ctlLabel = "Ajouter ma part : $title"; ctlLong = { ui.open(Sheet.Goal(g.id)) } }
        "duel" -> { val o = pg.userById(pg.duelOpp(g)); val a = pg.duelScore(g, pg.meId()); val b = pg.duelScore(g, o?.id ?: "")
            meta = "Toi ${fmtNum(round1(a))} – ${fmtNum(round1(b))} ${first(o?.name)} · ${g.unit}"; bar = if (a + b > 0) a / (a + b) * 100 else 50.0
            ctlProg = progress(bar!!); ctlLabel = "Ajouter à mon score : $title"; ctlLong = { ui.open(Sheet.Goal(g.id)) } }
        "deadline" -> { val n = daysUntil(g.due); meta = pg.dueText(g.due); urgent = n <= 2; ctlLabel = if (done) "Terminé : $title" else "Marquer comme terminé : $title" }
        "objective", "monthly" -> { val mo = g.type == "monthly"; val pr = if (mo) pg.monthProgress(g) else pg.objProgress(g)
            meta = "${fmtNum(pr)} / ${fmtNum(g.target)} ${g.unit}${if (mo) " · ${if (pr >= g.target) "atteint ce mois-ci" else pl(pg.daysLeftInMonth(), "jour restant", "jours restants")}" else ""}"; bar = minOf(100.0, pr / g.target * 100)
            ctlProg = progress(bar!!); ctlLabel = "Ajouter un progrès : $title"; ctlLong = { ui.open(Sheet.Goal(g.id)) } }
        else -> { val s = pg.statBig(g); meta = "${metaOverride ?: pg.metaLine(g)} · ${s.v} ${s.l}" }
    }
    // Types chiffrés : appui = + 1 pas, appui long = saisie (feuille Progrès)
    if (stepV != null && (!done || g.type == "cap")) {
        stepLbl = "${fmtNum(stepV)}${pg.unitSfx(g)}"
        ctlClick = { addStep(ui, g); stepTick++ }
        ctlLong = { ui.open(Sheet.Progress(g.id)) }
        if (ctlProg == null) ctlProg = 0f
    }
    if (g.type == "limit" && !done) stepLbl = "1${pg.unitSfx(g)}"
    // Faite : heure de la validation en tête de la sous-ligne
    if (done) pg.db.checkins.lastOrNull { it.goalId == g.id && it.userId == pg.meId() && it.kind == "done" && it.date == today() && it.at > 0 }?.let { meta = "Fait à ${hm(it.at)} · $meta" }
    if (pg.isPaused(g)) meta = "⏸️ En pause jusqu’au ${g.pausedUntil?.let { fmtDateShort(it) } ?: ""} · $meta"
    // Sous-tâches, enchaînement et duo
    if (g.steps.isNotEmpty() && g.type in STEP_TYPES && !done) { val n = pg.stepsDone(g).size; meta = "$n/${g.steps.size} sous-tâches · $meta"; if (bar == null) bar = n * 100.0 / g.steps.size }
    if (!done) pg.goalById(g.after)?.let { prev -> meta = "Après ${prev.icon} ${prev.title.take(20)} · $meta" }
    if (g.duoId != null && g.duoPeer != null) {
        val app = androidx.compose.ui.platform.LocalContext.current.peps
        val n = remember(ui.rev, g.id) { fr.pepsgoal.app.sys.Duo.streak(app, g) }
        meta = "🤝 $n · $meta"
    }
    val pend = pg.db.checkins.any { it.goalId == g.id && it.pending && it.userId == pg.meId() }
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val press = rememberPress(inter)
    // Balayage : vers la droite pour valider (ou annuler), vers la gauche pour les actions rapides
    val swipe = ui.prefs.swipe
    val sw = rememberSwipe()
    val rightLabel = if (g.type == "duration") (if (running) "Pause" else "Minuteur") else if (done && g.type in setOf("routine", "abstinence", "limit", "timed", "challenge")) "Annuler" else if (g.type == "cap") "Ajouter" else "Valider"
    val status = when { running -> "minuteur en cours"; done -> "validé aujourd’hui"; urgent -> "à faire, urgent"; else -> "à faire" }
    val c = tc.c
    // Ligne faite : fond et bord teintés du néon de son type
    val fill = if (done) lerp(p.surface, c, if (p.dark) .22f else .2f) else p.surface
    val edge = if (p.contrast) p.ink else if (done) lerp(p.edge, c, .55f) else p.edge
    // Validation : la tuile fait un petit saut
    val hop = remember { Animatable(0f) }
    LaunchedEffect(just) { if (just && !p.motion) { hop.snapTo(0f); hop.animateTo(1f, tween(110, easing = Motion.Emphasized)); hop.animateTo(0f, Motion.pop()) } }
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxWidth()) {
        SwipeReveal(sw, shape, right = SwipeAction(if (rightLabel == "Annuler") "x" else if (g.type == "duration") "play" else "check", rightLabel, if (rightLabel == "Annuler") p.ink2 else p.ok),
            left = SwipeAction("list", "Actions", p.focus), modifier = Modifier.matchParentSize())
        Row(
            Modifier.swipeable(sw, left = true, right = true, enabled = swipe) { dir -> if (dir > 0) doValidate(ui, g.id) else ui.open(Sheet.Quick(g.id)) }
                .swipeTilt(sw).fillMaxWidth()
                .chunk(fill, edge, 22.dp, 4.dp, 2.dp, press).clip(RoundedCornerShape(20.dp))
                // Objectif de durée : un appui long lance tout de suite une session Focus plein écran
                .springClick(inter, label = "Ouvrir les détails", onLongClick = if (g.type == "duration" && !done) ({ quickFocus(ui, g) }) else null) { ui.open(Sheet.Goal(g.id)) }
                .semantics {
                    stateDescription = status
                    customActions = listOf(
                        CustomAccessibilityAction(rightLabel) { doValidate(ui, g.id); true },
                        CustomAccessibilityAction("Actions rapides") { ui.open(Sheet.Quick(g.id)); true }
                    )
                }.heightIn(min = 72.dp).padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Objectif discret : titre et icône masqués dans les listes (visibles dans sa fiche)
            Box(Modifier.graphicsLayer { translationY = -hop.value * 6.dp.toPx() }) { GoalIcon(g, 52.dp, masked = true) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FavStar(pg, g)
                    // Titre sur deux lignes au besoin (gros caractères, commande à droite) plutôt que coupé
                    Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = p.ink, maxLines = if (p.easy) 3 else 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (urgent && !done) Pi("flag", size = 14.dp, tint = p.danger)
                    Text(meta, fontSize = 13.sp, color = if (urgent && !done) p.danger else if (done) tc.ink else p.ink2, fontWeight = if (urgent) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = if (p.easy) 4 else 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                }
                if (tag || p.easy || pend || extra != null) FlowRowCompat(gap = 6.dp) {
                    // Lecture facile : l'état est écrit en toutes lettres
                    if (p.easy) Chip(if (done) "Fait" else if (running) "En cours" else "À faire", if (done) "check" else "clock")
                    if (tag) TypeChip(g.type)
                    if (pend) Chip("En attente de ${first(pg.userById(g.buddy)?.name)}", "clock")
                    extra?.invoke()
                }
                if (!done) bar?.let { Bar(it, Modifier.padding(top = 4.dp, end = 4.dp), c, 8.dp) }
            }
            CheckButton(done && !running, just, ctlLabel, c, ctlDisabled, ctlIcon, running, ctlProg, ctlLong, stepLabel = stepLbl, stepTick = stepTick, onClick = ctlClick)
        }
    }
}

/** Actions rapides d'un objectif (balayage vers la gauche ou action TalkBack). */
@Composable
fun QuickSheet(ui: AppState, id: String) {
    val p = LocalPal.current; val pg = ui.pg; val g = pg.goalById(id) ?: run { LaunchedEffect(Unit) { ui.close() }; return }
    val snoozed = id in Snooze.ids(ui)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GoalIcon(g, 52.dp)
        Column(Modifier.weight(1f)) { Text(g.title, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); Muted(typeInfo(g.type).label, small = true) }
    }
    val acts = listOfNotNull(
        Triple("Valider ou saisir un progrès", "check") { ui.close(); doValidate(ui, id) },
        Triple(if (pg.isFav(g)) "Retirer des favoris" else "Ajouter aux favoris", "star") { pg.toggleFav(id); ui.close() },
        Triple("Lancer une session Focus", "timer") { ui.close(); ui.fs = fr.pepsgoal.app.Fs.Focus(id) },
        Triple(if (snoozed) "Remettre aujourd’hui" else "Reporter à demain", "clock") {
            Snooze.set(ui, id, !snoozed); ui.close()
            if (!snoozed) ui.undoable("« ${g.title} » reporté à demain.", "clock") { Snooze.set(ui, id, false) } else ui.toast("« ${g.title} » est de retour aujourd’hui.", "clock")
        },
        Triple("Voir les détails", "list") { ui.open(Sheet.Goal(id)) },
        if (g.userId == pg.meId()) Triple(if (g.archived) "Réactiver" else "Archiver", "archive") { ui.close(); archiveWithUndo(ui, id) } else null
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        acts.forEach { (l, ic, go) ->
            val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val press = rememberPress(inter)
            Row(Modifier.fillMaxWidth().chunk(p.surface, p.edge, 22.dp, 3.dp, 2.dp, press).clip(RoundedCornerShape(20.dp)).springClick(inter, role = androidx.compose.ui.semantics.Role.Button, onClick = go).heightIn(min = 50.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pi(ic, size = 22.dp, tint = p.ink); Text(l, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** Points des 7 derniers jours (validé, écart). */
@Composable
fun Last7(pg: Pg, g: Goal) {
    val p = LocalPal.current; val tc = p.type(g.type)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 6 downTo 0) {
            val k = ymd(todayDate().minusDays(i.toLong()))
            val on = pg.db.checkins.any { it.goalId == g.id && it.date == k && pg.isWin(it) }; val rel = pg.hasCheck(g.id, k, "relapse")
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (rel) p.danger else if (on) tc.c else p.surface2))
        }
    }
}

/** Carte d'un objectif (onglet Objectifs). */
@Composable
fun GoalCard(ui: AppState, g: Goal, modifier: Modifier = Modifier) {
    val p = LocalPal.current; val pg = ui.pg; val tc = p.type(g.type); val s = pg.statBig(g)
    // Balayage, comme dans « À valider » : vers la droite pour valider, vers la gauche pour les actions rapides
    val sw = rememberSwipe(); val shape = RoundedCornerShape(24.dp)
    val canDo = g.userId == pg.meId() && !g.archived && !pg.isClosedGoal(g)
    Box(modifier) {
    SwipeReveal(sw, shape, right = if (canDo) SwipeAction("check", "Valider", p.ok) else null, left = SwipeAction("list", "Actions", p.focus), modifier = Modifier.matchParentSize())
    Glass(Modifier.swipeable(sw, left = true, right = canDo, enabled = ui.prefs.swipe) { dir -> if (dir > 0) doValidate(ui, g.id) else ui.open(Sheet.Quick(g.id)) }.swipeTilt(sw), pad = 16.dp, onClick = { ui.open(Sheet.Goal(g.id)) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalIcon(g, 50.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { FavStar(pg, g); Text(g.title, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(6.dp))
                FlowRowCompat(gap = 6.dp) {
                    TypeChip(g.type)
                    if (g.members != null) Chip("${g.members!!.size} membres", "group") else VIS[g.visibility]?.let { Chip(it.first, it.second) }
                    if (g.buddy != null) Chip("binôme", "shield")
                }
            }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.v, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, color = tc.ink)
            Text(s.l, fontWeight = FontWeight.Bold, color = p.ink2, modifier = Modifier.padding(bottom = 4.dp))
        }
        pg.barPct(g)?.takeIf { g.type !in setOf("limit", "duration") }?.let { Bar(it, Modifier.padding(top = 8.dp), tc.c, 9.dp) }
        if (g.type == "group") Box(Modifier.padding(top = 6.dp)) { AvStack(pg, g.members ?: emptyList()) }
        if (g.type == "measure") MeasureSpark(pg, g)
        if (g.type == "monthly" && g.cumulate) Muted("Cumul : ${fmtNum(pg.objProgress(g))} ${g.unit} · ${pl(pg.goalStreak(g), "mois réussi d’affilée", "mois réussis d’affilée")}", small = true)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Muted(pg.metaLine(g), Modifier.weight(1f), small = true, maxLines = 2)
            if (g.type in setOf("routine", "abstinence", "limit", "duration", "cap", "timed", "period", "challenge")) Last7(pg, g)
        }
    }
    }
}

/* ------------------------------------------------------------------ fiches : calendrier, courbes, listes */
@Composable
fun GoalCalendar(pg: Pg, g: Goal) {
    val p = LocalPal.current; val tc = p.type(g.type)
    val ws = weekStart(todayDate()); val start = ws.minusDays(28); val t = today(); val created = ymd(g.createdAt)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { WD.forEach { Text(it, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink3, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
        for (w in 0 until 5) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 0 until 7) {
                val d = start.plusDays((w * 7 + i).toLong()); val k = ymd(d); val fut = k > t
                val on = pg.db.checkins.any { it.goalId == g.id && it.date == k && pg.isWin(it) }; val rel = pg.hasCheck(g.id, k, "relapse")
                val cls = when { rel -> "relapse"; on -> "on"; fut -> "fut"; k >= created && pg.scheduled(g, d) && !(g.type == "abstinence" && k < g.since) -> if (k == t) "pending" else "miss"; else -> "rest" }
                // Cases en aplats : validé = néon, écart = danger, manqué = creux bordé, aujourd'hui = bord braise
                val sh = RoundedCornerShape(6.dp)
                Box(Modifier.weight(1f).aspectRatio(1f).clip(sh)
                    .background(when (cls) { "on" -> vivid(tc.c); "relapse" -> vivid(p.danger); "rest", "miss" -> p.surface2; else -> Color.Transparent })
                    .then(if (cls == "fut") Modifier.border(2.dp, p.edge, sh) else Modifier)
                    .then(if (cls == "miss" || cls == "pending") Modifier.border(2.dp, if (cls == "pending") tc.c else p.control, sh) else Modifier)
                    .then(if (k == t) Modifier.border(2.5.dp, p.focusEdge, sh) else Modifier), contentAlignment = Alignment.Center) {
                    Text("${d.dayOfMonth}", fontSize = 12.sp, fontWeight = if (cls == "on" || k == t) FontWeight.ExtraBold else FontWeight.Bold, color = if (cls == "on" || cls == "relapse") PLUM else if (fut) p.ink3 else p.ink2)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
            Legend(tc.c, "Validé")
            if (g.type != "objective") Legend(p.surface2, "Manqué", p.control)
            if (g.type == "abstinence") Legend(p.danger, "Écart")
        }
    }
}

@Composable
private fun Legend(c: Color, l: String, border: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(c).then(if (border != null) Modifier.border(2.dp, border, RoundedCornerShape(6.dp)) else Modifier))
        Text(l, fontSize = 12.sp, color = LocalPal.current.ink2, fontWeight = FontWeight.Bold)
    }
}

/** Ligne « mois / semaine / jour » : libellé, barre, valeur. */
@Composable
fun ProgLine(label: String, pct: Double, value: String, color: Color, hit: Boolean = false, cur: Boolean = false, fut: Boolean = false, over: Boolean = false, lead: (@Composable () -> Unit)? = null) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().then(if (cur) Modifier.border(2.dp, color, RoundedCornerShape(17.dp)) else Modifier).padding(horizontal = 6.dp, vertical = 5.dp).alpha(if (fut) .5f else 1f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.width(110.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { lead?.invoke(); Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Bar(pct, Modifier.weight(1f), if (over) p.danger else color, 7.dp)
        Text(value + if (hit) " ✓" else "", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = if (over) p.danger else p.ink2)
    }
}


@Composable
fun MeasureSpark(pg: Pg, g: Goal) {
    val pts = pg.measurePts(g); if (pts.size < 2) return
    val c = LocalPal.current.type(g.type).ink
    Canvas(Modifier.padding(top = 8.dp).width(110.dp).height(32.dp)) {
        val vs = pts.map { it.value }; val lo = vs.min(); val hi = vs.max()
        val xy = vs.mapIndexed { i, v -> Offset(i / (vs.size - 1f) * (size.width - 6) + 3, if (hi == lo) size.height / 2 else (3 + (hi - v) / (hi - lo) * (size.height - 6)).toFloat()) }
        val path = Path().apply { xy.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
        drawPath(path, c, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(c, 3.5.dp.toPx(), xy.last())
    }
}

/** Courbe des mesures (avec la cible en pointillés). */
@Composable
fun MeasureChart(pg: Pg, g: Goal) {
    val p = LocalPal.current; val tc = p.type(g.type); val pts = pg.measurePts(g)
    if (pts.size < 2) { Muted("Ajoute au moins deux mesures pour voir ta courbe.", small = true); return }
    Column {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val L = 50f; val R = 14f; val T = 14f; val B = 26f
            val vs = pts.map { it.value }; val dMin = vs.min(); val dMax = vs.max(); val tg = g.targetOrNull
            var lo = minOf(dMin, tg ?: dMin); var hi = maxOf(dMax, tg ?: dMax); if (lo == hi) { lo -= 1; hi += 1 }
            val padv = (hi - lo) * .12; lo -= padv; hi += padv
            val t0 = parseYmd(pts.first().date).toEpochDay(); val t1 = parseYmd(pts.last().date).toEpochDay()
            val X = { d: String -> (L + if (t1 == t0) (size.width - L - R) / 2 else (parseYmd(d).toEpochDay() - t0).toFloat() / (t1 - t0) * (size.width - L - R)) }
            val Y = { v: Double -> (T + (hi - v) / (hi - lo) * (size.height - T - B)).toFloat() }
            val xy = pts.map { Offset(X(it.date), Y(it.value)) }
            for (v in setOf(dMin, dMax)) drawLine(p.line, Offset(L, Y(v)), Offset(size.width - R, Y(v)), 1f)
            if (tg != null) drawLine(p.ink2, Offset(L, Y(tg)), Offset(size.width - R, Y(tg)), 1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            val line = Path().apply { xy.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            val area = Path().apply { addPath(line); lineTo(xy.last().x, size.height - B); lineTo(xy.first().x, size.height - B); close() }
            drawPath(area, lerp(p.surface, tc.c, .3f))
            drawPath(line, tc.ink, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            xy.dropLast(1).forEach { drawCircle(tc.ink, 2.6.dp.toPx(), it) }
            drawCircle(vivid(tc.c), 9.dp.toPx(), xy.last()); drawCircle(tc.ink, 4.5.dp.toPx(), xy.last())
        }
        Row(Modifier.fillMaxWidth()) {
            Muted(fmtDateShort(pts.first().date), Modifier.weight(1f), small = true)
            g.targetOrNull?.let { Muted("cible ${pg.measureFmt(g, it)}", small = true) }
            Spacer(Modifier.weight(1f)); Muted(fmtDateShort(pts.last().date), small = true)
        }
    }
}

/**
 * Semaine en cours en pastilles de 34 dp : jour réussi (disque prune et flamme), joker (disque blanc et 🧊), aujourd'hui
 * à protéger (pointillés et flamme éteinte), jour à venir (point). [onColor] : posée sur la carte Série (encre prune).
 */
@Composable
fun WeekStrip(pg: Pg, id: String, onColor: Boolean = false) {
    val p = LocalPal.current
    val ws = weekStart(todayDate()); val days = pg.activeDays(id); val frozen = (pg.userById(id)?.frozenDays ?: emptyList()).toSet(); val t = today()
    val ink = if (onColor) PLUM else p.ink
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0 until 7) {
            val k = ymd(ws.plusDays(i.toLong())); val fut = k > t; val on = days.contains(k); val fz = !on && frozen.contains(k)
            val desc = "${WD[i]} : ${when { on -> "réussi"; fz -> "joker"; k == t -> "à protéger"; fut -> "à venir"; else -> "manqué" }}"
            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = desc }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(WD[i].take(1), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = ink)
                when {
                    on -> Box(Modifier.size(34.dp).background(if (onColor) PLUM else p.ink, CircleShape), contentAlignment = Alignment.Center) { Flame(20.dp) }
                    fz -> Box(Modifier.size(34.dp).background(Color.White, CircleShape).then(if (onColor) Modifier else Modifier.border(2.dp, p.edge, CircleShape)), contentAlignment = Alignment.Center) { Emoji("🧊", 20.dp) }
                    k == t -> Box(Modifier.size(34.dp).background(if (onColor) Color.White.copy(alpha = .42f) else Color.Transparent, CircleShape).drawBehind {
                        drawCircle(ink, size.minDimension / 2 - 1.25.dp.toPx(), style = Stroke(2.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                    }, contentAlignment = Alignment.Center) { Flame(18.dp, dim = true) }
                    fut -> Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(8.dp).background(if (onColor) PLUM else p.control, CircleShape)) }
                    else -> Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(10.dp).border(2.dp, if (onColor) PLUM else p.control, CircleShape)) }
                }
            }
        }
    }
}

/**
 * Courbe des quantités quotidiennes (cap quotidien, minutes d'une durée) : 30 derniers jours en barres,
 * ou l'année en moyennes hebdomadaires, avec la ligne du cap.
 */
@Composable
fun QtyChart(g: Goal, target: Double, unit: String, valueOf: (String) -> Double) {
    val p = LocalPal.current; val tc = p.type(g.type)
    var range by remember { mutableStateOf("30") }
    val pts = remember(range, g.id, valueOf(ymd(todayDate()))) {
        if (range == "30") (29 downTo 0).map { i -> val k = ymd(todayDate().minusDays(i.toLong())); k to valueOf(k) }
        else (51 downTo 0).map { w ->
            val start = todayDate().minusDays(w * 7L + 6)
            ymd(start) to (0 until 7).sumOf { valueOf(ymd(start.plusDays(it.toLong()))) } / 7.0
        }
    }
    Seg(listOf("30" to "30 jours", "365" to "1 an"), range, { range = it })
    val max = maxOf(target * 1.15, pts.maxOfOrNull { it.second } ?: 0.0, 1.0)
    val line = readable(tc.c)
    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp).semantics { contentDescription = "Graphique : ${if (range == "30") "30 derniers jours" else "moyennes par semaine sur un an"}, cap de ${fmtNum(target)} $unit" }) {
        val n = pts.size; val gap = 2.dp.toPx(); val w = (size.width - gap * (n - 1)) / n
        pts.forEachIndexed { i, (_, v) ->
            val h = (v / max * size.height).toFloat()
            val r4 = androidx.compose.ui.geometry.CornerRadius(minOf(6.dp.toPx(), w / 2))
            if (v < target) drawRoundRect(p.surface2, Offset(i * (w + gap), 0f), androidx.compose.ui.geometry.Size(w, size.height), r4)
            drawRoundRect(if (v >= target) tc.c else lerp(tc.c, p.surface2, .45f), Offset(i * (w + gap), size.height - h), androidx.compose.ui.geometry.Size(w, h.coerceAtLeast(2f)), r4)
        }
        val y = (size.height - target / max * size.height).toFloat()
        drawLine(line, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
    }
    val vals = pts.map { it.second }
    val hits = if (range == "30") vals.count { it >= target } else null
    Muted("Moyenne ${fmtNum(Math.round(vals.average() * 10) / 10.0)} $unit · meilleur ${fmtNum(Math.round((vals.maxOrNull() ?: 0.0) * 10) / 10.0)} $unit${hits?.let { " · cap atteint ${pl(it, "jour", "jours")} sur 30" } ?: " par jour (moyenne hebdomadaire)"} · ligne : cap de ${fmtNum(target)} $unit", small = true)
}

/** Concentration rapide : session Focus du temps restant de l'objectif de durée, en plein écran (Ne pas déranger si autorisé). */
fun quickFocus(ui: AppState, g: Goal) {
    val ctx = ui.app; val pg = ui.pg
    val left = kotlin.math.ceil(g.minutes - pg.durationLiveMins(g)).toInt().coerceIn(5, 180)
    if (!fr.pepsgoal.app.sys.Focus.running(ctx)) fr.pepsgoal.app.sys.Focus.start(ctx, left, g.id)
    ui.fs = Fs.Focus(g.id); ui.toast("Focus : $left min pour « ${g.title} ».", "timer")
}
