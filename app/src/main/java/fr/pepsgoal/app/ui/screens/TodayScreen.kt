package fr.pepsgoal.app.ui.screens

import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.core.model.unit
import fr.pepsgoal.app.Fs
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.app.ui.theme.PLUM
import fr.pepsgoal.app.ui.theme.step
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import fr.pepsgoal.core.*

@Composable
fun ScreenList(gap: androidx.compose.ui.unit.Dp = 10.dp, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val bars = LocalBars.current; val header = LocalTabHeader.current
    // Toucher l'onglet déjà ouvert : la page remonte en douceur tout en haut
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val tap = TabReTap.n
    LaunchedEffect(tap) { if (tap > 0 && (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)) state.animateScrollToItem(0) }
    TrackScroll(state) { Fx.scroll = it }
    LazyColumn(Modifier.fillMaxSize().testTag("list"), state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = bars.calculateTopPadding() + 4.dp, bottom = bars.calculateBottomPadding() + 80.dp),
        verticalArrangement = Arrangement.spacedBy(gap)) {
        // Héros de l'onglet : premier élément, il part avec le contenu (la barre compacte prend le relais)
        if (header != null) item("tabhero") { header() }
        content()
    }
}

/**
 * Publie le défilement d'une liste (px depuis le haut, plafonné passé le premier élément) pour la barre compacte et la
 * parallaxe du fond, et signale un défilement en cours (horloge de la scène figée pendant et 500 ms après).
 */
@Composable
fun TrackScroll(state: androidx.compose.foundation.lazy.LazyListState, publish: (Float) -> Unit) {
    LaunchedEffect(state) { snapshotFlow { if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else 100_000f }.collect { publish(it) } }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collectLatest { if (it) Fx.scrolling = true else { kotlinx.coroutines.delay(500); Fx.scrolling = false } }
    }
}

@Composable
fun TodayScreen(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    // Calculs de l'accueil faits une fois par changement de la base (ou du jour), pas à chaque recomposition
    val day = today()
    val st = remember(ui.rev, u.id, day) { pg.globalStreak(u.id) }; val plan = remember(ui.rev, u.id, day) { pg.todayPlan(u.id) }
    val lv = remember(ui.rev, u.id) { pg.level(pg.points(u.id)) }
    val done = plan.due.count { it.done }; val total = plan.due.size
    val hasGoals = remember(ui.rev, u.id) { pg.myGoals(u.id).any { !it.archived } }
    val cards = remember(ui.rev) { homeOrder(ui.prefs).filter { it.second }.map { it.first } }
    // Disposition choisie (Paramètres › Accueil) : écart entre les cartes et marge intérieure
    val layout = ui.prefs.homeLayout
    var collapsed by remember { mutableStateOf(ui.prefs.raw.getStringSet("homeCollapsed", emptySet())?.toSet() ?: emptySet()) }
    val toggle = { k: String -> collapsed = if (k in collapsed) collapsed - k else collapsed + k; ui.prefs.raw.edit().putStringSet("homeCollapsed", collapsed).apply() }
    val due = rememberDue(ui, plan)
    CompositionLocalProvider(LocalGlassPad provides when (layout) { "compact" -> 13.dp; "airy" -> 22.dp; else -> 16.dp }) {
    // Ordre fixe : carte Série, objectifs groupés par moment, puis les modules
    ScreenList(gap = when (layout) { "compact" -> 8.dp; "airy" -> 14.dp; else -> 10.dp }) {
        if (!hasGoals) {
            item("hero") { HomeHeader(ui, plan, st, lv) }
            item("first") { Glass { CardHead("Crée ton premier objectif", emoji = "🚀"); Muted("Choisis ce que tu veux suivre. Tu pourras le valider ici chaque jour."); Spacer(Modifier.height(12.dp)); Starters(ui) } }
            item("aipromo") { AiPromoCard(ui) }
            item("wx") { WeatherCard(ui) }
            return@ScreenList
        }
        if (ui.prefs.homeStyle == "classic") {
            // Fin d'année : la rétrospective est prête (du 15 décembre au 10 janvier)
            if (Recaps.wrappedOpen()) item("wrapped") { WrappedCard(ui) }
            // Le matin : rattrapage des oublis de la veille
            item("catchup") { CatchUpCard(ui) }
            // Accueil classique : toutes les cartes, dans l'ordre choisi (Paramètres › Accueil)
            for (k in cards) homeCard(ui, k, plan, lv, due, collapsed, toggle)
        } else simpleHome(ui, cards, plan, st, lv, collapsed, due, toggle)
        item("custom") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Puce("Personnaliser l’accueil", { ui.push(Screen.Settings("home")) }, emoji = "✏️")
            }
        }
    }
    }
}

/** Une carte de l'accueil, par sa clé (voir HOME_CARDS). */
private fun androidx.compose.foundation.lazy.LazyListScope.homeCard(ui: AppState, k: String, plan: Plan, lv: Level, due: DueState, collapsed: Set<String>, toggle: (String) -> Unit) {
    val pg = ui.pg; val u = pg.me() ?: return
    val done = plan.due.count { it.done }; val total = plan.due.size
    when (k) {
        "hero" -> { item("hero") { HomeHeader(ui, plan, pg.globalStreak(u.id), lv) }; item("ctx") { ContextCards(ui) } }
        "aipromo" -> item("aipromo") { AiPromoCard(ui) }
        "day" -> item("day") {
            Glass {
                CardHead("Ta journée", emoji = "☀️") { Count("${lv.pts} flammes") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Ring(done, total)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Eyebrow("Niveau ${lv.idx}")
                        Text(lv.name, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Bar(lv.pct * 100)
                        Muted(if (lv.next != null) "${lv.next!! - lv.pts} flammes avant « ${lv.nextName} »" else "Niveau maximum atteint", small = true)
                        if (total > 0 && done == total) PButton("Journée parfaite", { ui.fs = Fs.Celebrate(pg.perfectCelebration()) }, icon = "star", small = true, kind = "type", color = LocalPal.current.type("monthly").c)
                        PButton(if (pg.vacationOf()?.let { today() >= it.first } == true) "🏖️ En vacances" else if (pg.isRest()) "😴 Jour de repos" else "Repos et vacances", { ui.open(Sheet.Rest) }, icon = if (pg.isRest()) null else "moon", small = true, kind = "ghost")
                        PButton(if (pg.isHardDay()) "💛 Journée difficile" else "Journée difficile", { ui.open(Sheet.Hard) }, icon = if (pg.isHardDay()) null else "heart", small = true, kind = "ghost")
                        // Bilan du soir, proposé à partir de 18 h
                        if (nowHM() >= "18:00") PButton("Bilan du soir", { ui.open(Sheet.Evening) }, icon = "moon", small = true, kind = if (total > 0 && done < total) "primary" else "normal")
                    }
                }
            }
        }
        "timeline" -> item("timeline") { TimelineCard(ui, plan) }
        "briefing" -> item("briefing") { BriefingCard(ui) }
        "bday" -> item("bday") { BirthdayCard(ui) }
        "countdown" -> item("countdown") { CountdownSummary(ui) }
        "advent" -> item("advent") { AdventCard(ui) }
        "record" -> item("record") { RecordCard(ui) }
        "challenge" -> item("challenge") { ChallengeCard(ui) }
        "memories" -> item("memories") { MemoriesCard(ui) }
        "wxweek" -> item("wxweek") { WeekWeatherCard(ui) }
        "quote" -> item("quote") { QuoteCard(ui) }
        "cheers" -> item("cheers") { FriendCheersCard(ui) }
        "weekimg" -> item("weekimg") { WeekImageCard(ui) }
        "evening" -> item("evening") { EveningQuickCard(ui, plan) }
        "quest" -> item("quest") { QuestCard(ui) }
        "tools" -> item("tools") { QuickTools(ui) }
        "tip" -> item("tip") { AiTipCard(ui) }
        "mood" -> item("mood") { MoodCard(ui) }
        "wx" -> item("wx") { WeatherCard(ui) }
        "board" -> if (pg.socialOn(u)) item("board") { Board(ui) }
        "due" -> dueItems(ui, due, "done" !in collapsed) { toggle("done") }
        "verify" -> {
            val verify = pg.db.checkins.filter { c -> c.pending && pg.goalById(c.goalId)?.buddy == u.id }
            if (verify.isNotEmpty()) item("verify") {
                Glass {
                    CardHead("À confirmer pour tes amis", emoji = "🤝") { Count(verify.size) }
                    verify.forEach { c ->
                        val g = pg.goalById(c.goalId)!!; val o = pg.userById(c.userId)
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Av(o, 36.dp)
                            Column(Modifier.weight(1f)) {
                                Text("${o?.name ?: ""} · ${g.icon} ${g.title}", fontWeight = FontWeight.Bold)
                                Muted("${fmtDate(c.date)} · ${when (c.kind) { "done" -> "validé"; "measure" -> "mesure enregistrée"; else -> "+${fmtNum(c.value)} ${g.unit}" }}", small = true)
                            }
                            PButton("Confirmer", { ui.cloud.confirmCheck(c.id, true) }, small = true)
                            PButton("Refuser", { ui.cloud.confirmCheck(c.id, false) }, kind = "ghost", small = true)
                        }
                    }
                }
            }
        }
        "ongoing" -> if (plan.ongoing.isNotEmpty() && !u.settings.hideOngoing) item("ongoing") {
            Glass {
                CardHead("En cours", emoji = "⏳") {
                    Count(plan.ongoing.size)
                    IconBtn("x", "Masquer la carte « En cours » (réactivable dans Paramètres › Apparence)", { u.settings.hideOngoing = true; pg.store.commit(); ui.toast("Carte « En cours » masquée. Réaffiche-la dans Paramètres › Apparence.", "list") }, plain = true, size = 36.dp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { plan.ongoing.forEach { key(it.id) { Box(Modifier.animatePlacement()) { GoalRow(ui, it, false) } } } }
            }
        }
        "recap" -> item("recap") { RecapCard(ui) }
            }
}

/**
 * Accueil simplifié : un en-tête qui réunit la série, la journée et les actions du jour ; l'essentiel à valider ;
 * les cartes ponctuelles en tuiles « À la une » ; le reste rangé en sections repliables. Aucune carte n'est perdue :
 * chacune garde son interrupteur dans Paramètres › Accueil.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.simpleHome(ui: AppState, cards: List<String>, plan: Plan, st: Streak, lv: Level, collapsed: Set<String>, due: DueState, toggle: (String) -> Unit) {
    val pg = ui.pg; val u = pg.me() ?: return
    if ("hero" in cards || "day" in cards) item("hero") { HomeHeader(ui, plan, st, lv) }
    for (k in listOf("due", "verify", "ongoing")) if (k in cards) homeCard(ui, k, plan, lv, due, collapsed, toggle)
    // Modules secondaires, sous la liste
    if ("hero" in cards || "day" in cards) { item("ctx") { ContextCards(ui) }; item("month") { MonthFocusCard(ui) } }
    if ("aipromo" in cards) homeCard(ui, "aipromo", plan, lv, due, collapsed, toggle)
    val moments = cards.filter { it in MOMENT_KEYS }
    item("moments") {
        val tiles = rememberMoments(ui, moments)
        if (tiles.isNotEmpty() || "countdown" in moments) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeSectionHead("À la une", "star", "moments" in collapsed) { toggle("moments") }
            // Apparition en fondu et glissement (graphicsLayer), sans animation de taille
            if ("moments" !in collapsed) Box(Modifier.popIn(androidx.compose.ui.graphics.TransformOrigin(.5f, 0f))) { MomentsRow(ui, tiles, "countdown" in moments) }
        }
    }
    val sections = listOf(
        Triple("day", "Ta journée" to "clock", listOf("timeline", "mood", "tools")),
        Triple("progress", "Progression" to "star", listOf("recap")),
        Triple("out", "Dehors" to "cloud", listOf("wx")),
        Triple("friends", "Entre amis" to "users", if (pg.socialOn(u)) listOf("board") else emptyList()),
        Triple("coach", "Coach" to "sparkle", listOf("tip"))
    )
    for ((id, head, keys) in sections) {
        val shown = keys.filter { it in cards }; if (shown.isEmpty()) continue
        // Préfixe distinct des sections de moment de dueItems (« sec:day » existe déjà : clé dupliquée = plantage au défilement)
        item("home:$id") { HomeSectionHead(head.first, head.second, id in collapsed) { toggle(id) } }
        if (id !in collapsed) for (k in shown) homeCard(ui, k, plan, lv, due, collapsed, toggle)
    }
}

/**
 * Flamme de la série avec son nombre de jours (tutoriel, feuilles) : immobile (la flamme ne danse que sur la carte
 * Série) ; chatouilles conservées (surprise après 7 touchers).
 */
@Composable
fun HeroFlame(n: Int, lit: Boolean, onTickle: (() -> Unit)? = null) {
    val p = LocalPal.current
    val count = remember { Animatable(0f) }
    LaunchedEffect(n) { if (p.motion) count.snapTo(n.toFloat()) else count.animateTo(n.toFloat(), tween(minOf(1500, 600 + n * 6), easing = FastOutSlowInEasing)) }
    val wiggle = remember { Animatable(0f) }; val jump = remember { Animatable(0f) }
    val taps = remember { mutableListOf<Long>() }; val scope = rememberCoroutineScope()
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val press = rememberPress(inter, onTickle != null)
    val tickle = if (onTickle == null) Modifier else Modifier.clickable(interactionSource = inter, indication = null, onClickLabel = "Flamme") {
        val now = android.os.SystemClock.uptimeMillis(); taps.removeAll { now - it > 2500 }; taps.add(now)
        fr.pepsgoal.app.ui.Feedback.ramp((taps.size / 7f).coerceAtMost(1f))
        scope.launch { wiggle.snapTo(if (taps.size % 2 == 0) 1f else -1f); wiggle.animateTo(0f, spring(dampingRatio = .25f, stiffness = 900f)) }
        if (taps.size >= 7) { taps.clear(); scope.launch { jump.snapTo(0f); jump.animateTo(1f, tween(700, easing = FastOutSlowInEasing)); jump.snapTo(0f) }; onTickle() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Box(tickle.graphicsLayer {
            val j = kotlin.math.sin(jump.value * kotlin.math.PI.toFloat())
            scaleX = 1f + .25f * j; scaleY = 1f + .25f * j; translationY = -j * 40.dp.toPx(); rotationZ = wiggle.value * 12f + jump.value * 360f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
        }) {
            // Médaillon blanc à marche qui s'enfonce sous le doigt
            Box(Modifier.size(92.dp, 97.dp).chunk(Color.White, p.flameStep, 46.dp, 5.dp, 0.dp, press), contentAlignment = Alignment.Center) { Flame(64.dp, dim = !lit) }
        }
        }
        // Chiffre et légende sur une plaque : jamais de texte posé directement sur la scène
        Plaque(Modifier.padding(top = 8.dp)) {
            Text("${count.value.toInt()}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = p.ink)
            Text(if (n > 1) "jours de série" else "jour de série", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
        }
    }
}

@Composable
fun Starters(ui: AppState) {
    val p = LocalPal.current
    // Grille régulière de 2 colonnes : toutes les tuiles d'une ligne ont la même hauteur
    val tiles = TYPE_GROUPS[0].second.map { k -> Triple(k, typeInfo(k).label, typeInfo(k).desc) } + Triple("more", "9 autres formats", "Limite, durée, mesure, horaire, projet, challenge, paliers, groupe, duel")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (k, l, d) ->
                    val tc = p.type(if (k == "more") "measure" else k)
                    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val press = rememberPress(inter)
                    Column(Modifier.weight(1f).fillMaxHeight().chunk(androidx.compose.ui.graphics.lerp(p.surface, tc.c, if (p.dark) .22f else .2f), androidx.compose.ui.graphics.lerp(p.edge, tc.c, .55f), 22.dp, 4.dp, 2.dp, press)
                        .clip(RoundedCornerShape(20.dp)).springClick(inter, role = androidx.compose.ui.semantics.Role.Button) { ui.open(if (k == "more") Sheet.GoalForm() else Sheet.GoalForm(preType = k)) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconTile(if (k == "more") "plus" else typeInfo(k).icon, tc.c, tc.c, 38.dp); Text(l, fontWeight = FontWeight.ExtraBold); Muted(d, small = true)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Board(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me()!!
    val board = (listOf(u.id) + pg.friendsOf(u.id).filter { it.isNotEmpty() }).mapNotNull { id -> pg.userById(id)?.let { it to pg.globalStreak(id).n } }.sortedByDescending { it.second }.take(5)
    Glass {
        CardHead("Classement des flammes", emoji = "🏆") { PButton("Amis", { ui.nav("friends") }, kind = "ghost", small = true) }
        if (board.size > 1) board.forEachIndexed { i, (x, n) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (x.id == u.id) p.selTint(p.flame) else Color.Transparent).clickable { if (x.id != u.id) ui.open(Sheet.User(x.id)) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (i < 3) Emoji(listOf("🥇", "🥈", "🥉")[i], 22.dp) else Text("${i + 1}", fontFamily = Unbounded, fontWeight = FontWeight.Bold, color = p.ink3, modifier = Modifier.width(22.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Av(x, 30.dp)
                Text(if (x.id == u.id) "Toi" else x.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Flame(18.dp); Text("$n", fontWeight = FontWeight.ExtraBold)
            }
        } else Empty("", "Ajoute des amis pour comparer vos séries.") { PButton("Trouver des amis", { ui.friendsTab = "discover"; ui.nav("friends") }, icon = "userplus", small = true) }
    }
}

@Composable
fun RecapCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    Glass {
        CardHead("Tes récaps", emoji = "📊") { Muted("Ton parcours en stats", small = true) }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("week" to "Semaine", "month" to "Mois", "year" to "Année").forEachIndexed { i, (k, l) ->
                val r = Recaps.range(k)
                val name = if (r.past) mapOf("week" to "Semaine passée", "month" to "Mois passé", "year" to "Année passée")[k]!! else l
                val g = SBG[listOf(0, 4, 1)[i]].map(::hex)
                val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val press = rememberPress(inter)
                val c0 = vivid(g.last())
                Column(Modifier.weight(1f).fillMaxHeight().chunk(c0, c0.step(), 22.dp, 4.dp, 0.dp, press).clip(RoundedCornerShape(22.dp))
                    .springClick(inter, role = androidx.compose.ui.semantics.Role.Button) { val (sl, st) = Recaps.slides(pg, pg.meId(), k); ui.fs = Fs.Recap(k, sl, st) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (r.past) Text("J+1", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = PLUM, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = .42f)).padding(horizontal = 6.dp, vertical = 1.dp))
                    Pi("star", tint = PLUM)
                    FitText(name, size = 14, min = 9, color = PLUM, weight = FontWeight.ExtraBold)
                    val n = Recaps.countIn(pg, pg.meId(), k)
                    Text("$n", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = PLUM)
                    FitText(if (n > 1) "validations" else "validation", size = 12, min = 8, color = PLUM)
                    if (r.past) Text("bilan complet, aujourd’hui", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PLUM)
                }
            }
        }
    }
}

@Composable
fun WeatherCard(ui: AppState) {
    val p = LocalPal.current; val ctx = LocalContext.current; val u = ui.pg.me() ?: return
    if (!u.settings.wxCard) return
    val place = u.settings.weather
    if (place == null) {
        Glass { CardHead("Météo", emoji = "🌤️"); Muted("Ajoute ta ville pour voir la météo du jour, et un conseil pour tes objectifs."); Spacer(Modifier.height(10.dp)); PButton("Choisir ma ville", { ui.push(Screen.Settings("weather")) }, icon = "pin", small = true) }
        return
    }
    LaunchedEffect(place.lat, place.lon) { Wx.ensure(ctx, ui, false) }
    val c = Wx.cached(ctx, place)
    if (c == null) { Glass { CardHead("Météo · ${place.name}"); if (Wx.error != null) Muted(Wx.error!!) else { Skeleton(Modifier.fillMaxWidth(.4f), 34.dp); Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { Skeleton(Modifier.weight(1f), 64.dp, 16.dp) } } } }; return }
    val w = c.data; val cur = w.current; val info = Weather.wmo(cur.code, cur.isDay); val d0 = w.daily.firstOrNull()
    Glass {
        CardHead(place.name, emoji = "📍") { IconBtn("gear", "Réglages de la météo", { ui.push(Screen.Settings("weather")) }, plain = true, size = 36.dp) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Emoji(info.emoji, 54.dp)
            Column(Modifier.weight(1f)) {
                Text(Weather.temp(cur.temp), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                Muted("${info.label} · ressenti ${Weather.temp(cur.feels)}", small = true)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${Weather.temp(d0?.min)} / ${Weather.temp(d0?.max)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) { Pi("wind", size = 14.dp, tint = p.ink2); Text(" ${Math.round(cur.wind ?: 0.0)} km/h", fontSize = 13.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) { Pi("drop", size = 14.dp, tint = p.ink2); Text(" ${d0?.pop?.let { "${it.toInt()} %" } ?: "${cur.humidity?.toInt() ?: "—"} %"}", fontSize = 13.sp) }
            }
        }
        Text(Weather.tip(w), Modifier.padding(vertical = 10.dp), fontWeight = FontWeight.Bold, color = p.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            w.daily.drop(1).take(3).forEach { d ->
                val x = Weather.wmo(d.code)
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(p.surface2).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(cap(parseYmd(d.day).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, FR).removeSuffix(".")), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Emoji(x.emoji, 26.dp)
                    Text("${Weather.temp(d.max)}  ${Weather.temp(d.min)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        val time = { s: String? -> s?.drop(11)?.take(5) ?: "—" }
        Muted("Lever ${time(d0?.sunrise)} · coucher ${time(d0?.sunset)} · mis à jour à ${hm(c.at)} · Open-Meteo", Modifier.padding(top = 8.dp), small = true)
    }
}

/** Objectifs du jour, prêts à être affichés : groupés par moment, coach IA, reportés. */
class DueState(
    val sections: List<Pair<String, List<DueItem>>>, val doneList: List<DueItem>, val total: Int,
    val reasons: Map<String, String>, val ai: Boolean, val aiPending: Boolean, val later: Int
)

/** Sections par moment : emoji et titre. */
private val MOMENTS = listOf("morning" to ("☀️" to "Ce matin"), "afternoon" to ("🌤️" to "Cet après-midi"), "evening" to ("🌙" to "Ce soir"), "day" to ("🌈" to "Dans la journée"))

/**
 * Prépare « À valider aujourd'hui ». Avec le coach IA (recommandations activées), chaque section est réordonnée selon
 * l'heure et la météo, avec la raison du coach. Un objectif validé reste 1,5 s à sa place avant de glisser vers « Fait ».
 */
@Composable
fun rememberDue(ui: AppState, plan: Plan): DueState {
    val pg = ui.pg; val ctx = androidx.compose.ui.platform.LocalContext.current
    val ai = fr.pepsgoal.app.ai.LocalAi.on(ctx, ui.prefs.aiRecommend) && plan.due.any { !it.done }
    val rk = if (ai) fr.pepsgoal.app.ai.Coach.recoKey(ctx) else ""
    // Recommandations mémorisées : affichées tout de suite, recalculées seulement quand le contexte change
    var picks by remember(rk) { mutableStateOf(if (ai) fr.pepsgoal.app.ai.Coach.cachedPicks(ctx, rk) else null) }
    if (ai) LaunchedEffect(rk) { if (picks == null) picks = fr.pepsgoal.app.ai.Coach.recommend(ctx) }
    val reasons = picks?.associate { it.goal.id to it.reason } ?: emptyMap()
    val order = picks?.map { it.goal.id } ?: emptyList()
    // Validation qui vient d'avoir lieu : la ligne reste en place 1,5 s (case qui « pop », teinte), puis glisse
    var hold by remember { mutableStateOf(emptySet<String>()) }
    val just = pg.justDone
    LaunchedEffect(just) { if (just != null) { hold = hold + just; kotlinx.coroutines.delay(1500); hold = hold - just } }
    val later = Snooze.ids(ui)
    val shown = plan.due.filter { it.g.id !in later || it.done }
    val sorted = shown.sortedWith(compareBy<DueItem> { if (it.g.id in order) order.indexOf(it.g.id) else 99 }.thenByDescending { pg.isFav(it.g) })
    val open = sorted.filter { !it.done || it.g.id in hold }
    val sections = MOMENTS.map { (k, _) -> k to open.filter { momentOf(pg, it.g) == k } }.filter { it.second.isNotEmpty() }
    return DueState(sections, sorted.filter { it.done && it.g.id !in hold }, plan.due.size, reasons, ai, ai && picks == null,
        plan.due.count { it.g.id in later && !it.done })
}

/**
 * « À valider aujourd'hui » : sections ☀️ Ce matin, 🌤️ Cet après-midi, 🌙 Ce soir, 🌈 Dans la journée (sections vides
 * masquées), lignes à marche espacées de 10 dp, puis ✅ Fait, repliée par défaut. Chaque ligne est un élément de la liste :
 * elle glisse d'une section à l'autre.
 */
fun androidx.compose.foundation.lazy.LazyListScope.dueItems(ui: AppState, d: DueState, doneOpen: Boolean, toggleDone: () -> Unit) {
    if (d.total == 0) {
        item("due:empty") {
            Glass {
                Empty("Rien de prévu aujourd’hui", "Profite, ou ajoute une routine !", emoji = "🌴",
                    action = { PButton("Ajouter une routine", { ui.open(Sheet.GoalForm(preType = "routine")) }, icon = "plus", kind = "primary") })
            }
        }
        return
    }
    // Astuce affichée une fois : les gestes de balayage
    if (ui.prefs.swipe && !ui.prefs.swipeHint && d.sections.isNotEmpty()) item("due:hint") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Note("Balaie un objectif vers la droite pour le valider, vers la gauche pour reporter, mettre en favori ou lancer un Focus.", "hand", Modifier.weight(1f))
            IconBtn("x", "Masquer l’astuce", { ui.prefs.swipeHint = true; ui.bump() }, size = 36.dp)
        }
    }
    d.sections.forEachIndexed { i, (k, list) ->
        val (emoji, title) = MOMENTS.first { it.first == k }.second
        item("sec:$k") {
            SectionTitle(title, emoji, list.size, modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)) {
                // Coach IA : une pilule « Ordre conseillé » dans le premier en-tête
                if (i == 0 && d.ai) CoachPill(d.aiPending)
            }
        }
        list.forEach { x -> item("g:" + x.g.id) { DueRow(ui, x, d.reasons[x.g.id]) } }
    }
    if (d.later > 0) item("due:later") {
        Row(Modifier.fillMaxWidth().animateItem(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniPlaque(pl(d.later, "objectif reporté à demain", "objectifs reportés à demain"), Modifier.weight(1f, fill = false), emoji = "⏰")
            Spacer(Modifier.weight(1f))
            Puce("Remettre", { ui.prefs.snoozed = ""; ui.bump() })
        }
    }
    if (d.doneList.isNotEmpty()) {
        item("sec:done") { DoneHead(ui, d.doneList, doneOpen, toggleDone, Modifier.animateItem()) }
        if (doneOpen) d.doneList.forEach { x -> item("g:" + x.g.id) { DueRow(ui, x, null) } }
    }
}

/** Une ligne de la liste du jour, avec la raison du coach IA en dessous. */
@Composable
private fun androidx.compose.foundation.lazy.LazyItemScope.DueRow(ui: AppState, x: DueItem, why: String?) {
    val p = LocalPal.current
    Column(if (p.motion) Modifier else Modifier.animateItem(fadeInSpec = tween(150), placementSpec = Motion.move(), fadeOutSpec = tween(150))) {
        GoalRow(ui, x.g, x.done, x.meta)
        if (why != null && !x.done) Row(Modifier.padding(start = 70.dp, top = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Emoji("✨", 14.dp); Muted(why, small = true, maxLines = 2)
        }
    }
}

/** Pilule ✨ du coach IA dans l'en-tête de section. */
@Composable
private fun CoachPill(pending: Boolean) {
    val p = LocalPal.current
    val pulse = thinkingAlpha()
    Row(Modifier.graphicsLayer { alpha = if (pending) pulse else 1f }.clip(CircleShape).background(vivid(p.type("abstinence").c)).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Emoji("✨", 14.dp); Text(if (pending) "Le coach trie…" else "Ordre conseillé", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = PLUM, maxLines = 1)
    }
}

/** Section « Fait » repliée : ligne à marche avec la pile des emojis faits ; un toucher la déplie. */
@Composable
private fun DoneHead(ui: AppState, list: List<DueItem>, open: Boolean, toggle: () -> Unit, modifier: Modifier) {
    val p = LocalPal.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Fait", "✅", list.size)
        val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val press = rememberPress(inter)
        Row(Modifier.fillMaxWidth().chunk(p.surface, p.edge, 22.dp, 4.dp, 2.dp, press).clip(RoundedCornerShape(20.dp))
            .springClick(inter, label = if (open) "Replier les objectifs faits" else "Déplier les objectifs faits", onClick = toggle)
            .semantics { stateDescription = if (open) "déplié" else "replié" }.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                list.take(4).forEach { x -> Box(Modifier.size(34.dp).clip(CircleShape).background(vivid(p.type(x.g.type).c)).border(2.dp, p.surface, CircleShape), contentAlignment = Alignment.Center) { Emoji(x.g.shownIcon(), 22.dp) } }
            }
            Text(pl(list.size, "fait aujourd’hui", "faits aujourd’hui"), Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Pi("down", size = 18.dp, tint = p.ink2, modifier = Modifier.graphicsLayer { rotationZ = if (open) 180f else 0f })
        }
    }
}

/** Raccourcis de l'accueil : mode Focus, statistiques, journal et coach IA. */
@Composable
private fun QuickTools(ui: AppState) {
    val p = LocalPal.current; val ctx = androidx.compose.ui.platform.LocalContext.current
    val focusOn = fr.pepsgoal.app.sys.Focus.running(ctx)
    val items = listOfNotNull(
        Triple(if (focusOn) "Focus en cours" else "Focus", "timer") { ui.fs = Fs.Focus() },
        Triple("Stats", "trend") { ui.push(fr.pepsgoal.app.Screen.Stats) },
        Triple("Journal", "book") { ui.push(fr.pepsgoal.app.Screen.Journal) },
        Triple("Respirer", "wind") { ui.open(fr.pepsgoal.app.Sheet.Breath) },
        if (fr.pepsgoal.app.ai.LocalAi.on(ctx)) Triple("Coach", "sparkle") { ui.push(fr.pepsgoal.app.Screen.CoachChat) } else null,
    )
    val cols = mapOf("timer" to p.flame, "trend" to p.type("objective").c, "book" to p.type("abstinence").c, "wind" to p.type("duration").c, "sparkle" to p.pink)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (l, ic, go) ->
            Glass(Modifier.weight(1f).fillMaxHeight(), pad = 10.dp, radius = 24.dp, fill = false, onClick = go, ai = ic == "sparkle", border = if (ic == "timer" && focusOn) p.flame else null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val c = cols[ic] ?: p.flame
                    IconTile(ic, c.lift(.15f), c, 36.dp)
                    FitText(l, size = 12, min = 8, weight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/**
 * Cartes du moment, sous l'en-tête : météo qui contrarie un objectif d'extérieur, plan du dimanche du coach,
 * récompense atteinte. Chacune n'apparaît que lorsqu'elle sert.
 */
@Composable
private fun ContextCards(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Météo
        val fc = u.settings.weather?.let { Wx.cached(ctx, it)?.data }
        val issue = weatherIssue(fc)
        val adapt = remember(ui.rev, fc) { pg.weatherAdaptable(fc) }.filter { ui.prefs.raw.getString("wxKeep:${it.id}", "") != today() }
        if (issue != null && adapt.isNotEmpty()) Glass {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Emoji(Weather.wmo(fc?.daily?.firstOrNull()?.code).emoji, 34.dp)
                Column(Modifier.weight(1f)) { Text(issue, fontWeight = FontWeight.ExtraBold); Muted("Adapter tes objectifs d’extérieur ?", small = true) }
            }
            adapt.take(3).forEach { g ->
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalIcon(g, 32.dp)
                    Column(Modifier.weight(1f)) { Text(g.title, fontWeight = FontWeight.Bold, maxLines = 1); if (g.planB.isNotBlank()) Muted("☔ Plan B : ${g.planB}", small = true) }
                    // Plan B : l'alternative compte comme une validation normale
                    if (g.planB.isNotBlank()) PButton("Plan B fait", { doValidate(ui, g.id); ui.toast("Plan B validé pour « ${g.title} ». Bien joué !", "check") }, small = true, kind = "type", color = p.type(g.type).c)
                    PButton("Demain", { pg.skipToday(g)?.let { ui.toast(it, "x") } ?: ui.toast("« ${g.title} » décalé : ta série est préservée.", "cloud") }, small = true)
                    PButton("Je le fais", { ui.prefs.raw.edit().putString("wxKeep:${g.id}", today()).apply(); ui.bump() }, small = true, kind = "ghost")
                }
            }
        }
        // 2. Plan du dimanche (et du lundi) : ajustements proposés par le coach
        val wk = weekKey()
        if (wdIdx(todayDate()) in setOf(0, 6) && ui.prefs.raw.getString("planDone", "") != (if (wdIdx(todayDate()) == 6) ymd(weekStart(todayDate()).plusDays(7)) else wk)) {
            val sug = remember(ui.rev) { pg.weeklySuggestions() }
            var intro by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(sug.size) {
                if (sug.isEmpty()) return@LaunchedEffect
                if (fr.pepsgoal.app.ai.LocalAi.on(ctx)) intro = fr.pepsgoal.app.ai.LocalAi.generate(ctx, 60, .7f, background = true) { fr.pepsgoal.app.ai.Brain.system(ctx, "tip") + "\nEn une phrase courte et motivante, présente ces ajustements de la semaine à venir : " + sug.joinToString(" ; ") { it.text } }?.let { fr.pepsgoal.app.ai.fixName(ctx, it) }?.trim()?.trim('"')
            }
            val key = if (wdIdx(todayDate()) == 6) ymd(weekStart(todayDate()).plusDays(7)) else wk
            if (sug.isNotEmpty()) Glass(ai = true) {
                CardHead("Le plan de la semaine", emoji = "🗺️") { IconBtn("x", "Masquer", { ui.prefs.raw.edit().putString("planDone", key).apply(); ui.bump() }, plain = true, size = 34.dp) }
                Muted(intro ?: "D’après tes 4 dernières semaines, quelques ajustements pour que tes objectifs restent à ta portée.", small = true)
                sug.forEach { s ->
                    var applied by remember(s.goal.id) { mutableStateOf(false) }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Emoji(if (s.lighten) "🪶" else "🚀", 22.dp)
                        Text(s.text, Modifier.weight(1f), fontSize = 14.sp)
                        if (applied) Text("✓", color = p.ok, fontWeight = FontWeight.ExtraBold)
                        else PButton("Oui", { s.apply(); applied = true; ui.toast("Objectif ajusté.", "check") }, small = true, kind = "type", color = p.type(s.goal.type).c)
                    }
                }
            }
        }
        // 3. Récompenses atteintes à célébrer
        LaunchedEffect(ui.rev) { pg.checkRewards().forEach { r -> Feedback.success(); ui.toast("Récompense débloquée : ${r.emoji} ${r.title} ! Tu l’as bien méritée.", "star") } }
        val ready = u.rewards.filter { it.claimedAt == null && pg.rewardProgress(it) >= it.target }
        if (ready.isNotEmpty()) fr.pepsgoal.app.ui.sheets.RewardsCard(ui, compact = true)
    }
}

/** Carte d'accueil de fin d'année : ouvre la rétrospective « PepsGoal Wrapped ». */
@Composable
fun WrappedCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val r = remember { Recaps.wrappedRange() }; val year = parseYmd(r.s).year
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val press = rememberPress(inter)
    Column(Modifier.fillMaxWidth().chunk(p.flameBrush(), p.flameStep, 24.dp, 4.dp, 0.dp, press).clip(RoundedCornerShape(24.dp))
        .springClick(inter, label = "Ouvrir ma rétrospective") { val (sl, st) = Recaps.wrapped(pg, pg.meId()); ui.fs = Fs.Recap("year", sl, st) }.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.graphicsLayer { rotationZ = 8f }) { Emoji("🎁", 56.dp) }
            Column(Modifier.weight(1f)) {
                Text("PEPSGOAL WRAPPED", color = PLUM, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                Text("Ton année $year est prête", color = PLUM, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        Text("Ton meilleur mois, ton podium, ton profil de l’année… à revivre en story et à partager.", color = PLUM, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Compteur du jour (« 3/5 ») : pastille creusée, coche quand tout est validé. */
@Composable
fun DayCount(done: Int, total: Int) {
    val p = LocalPal.current
    val all = total > 0 && done == total
    // Mini-plaque (posée sur la scène) ; tout validé : aplat lime
    Row(Modifier.clip(CircleShape).background(if (all) vivid(p.type("routine").c) else p.surface).border(2.dp, if (all) vivid(p.type("routine").c).step() else p.edge, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (all) Pi("check", size = 14.dp, tint = PLUM)
        Text("$done/$total", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = if (all) PLUM else p.ink2)
    }
}
