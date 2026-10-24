package fr.pepsgoal.app.ui.screens

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
import kotlinx.coroutines.launch
import fr.pepsgoal.core.*

@Composable
fun ScreenList(gap: androidx.compose.ui.unit.Dp = 14.dp, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val bars = LocalBars.current
    // Toucher l'onglet déjà ouvert : la page remonte en douceur tout en haut
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val tap = TabReTap.n
    LaunchedEffect(tap) { if (tap > 0 && (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)) state.animateScrollToItem(0) }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = bars.calculateTopPadding() + 14.dp, bottom = bars.calculateBottomPadding() + 86.dp),
        verticalArrangement = Arrangement.spacedBy(gap), content = content)
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
    CompositionLocalProvider(LocalGlassPad provides when (layout) { "compact" -> 13.dp; "airy" -> 24.dp; else -> 18.dp }) {
    ScreenList(gap = when (layout) { "compact" -> 8.dp; "airy" -> 22.dp; else -> 14.dp }) {
        if (!hasGoals) {
            item("hero") { Hero(ui) }
            item("aipromo") { AiPromoCard(ui) }
            item("first") { Glass { CardHead("Crée ton premier objectif"); Muted("Choisis ce que tu veux suivre. Tu pourras le valider ici chaque jour."); Spacer(Modifier.height(12.dp)); Starters(ui) } }
            item("wx") { WeatherCard(ui) }
            return@ScreenList
        }
        if (ui.prefs.homeStyle == "classic") {
            // Fin d'année : la rétrospective est prête (du 15 décembre au 10 janvier)
            if (Recaps.wrappedOpen()) item("wrapped") { WrappedCard(ui) }
            // Le matin : rattrapage des oublis de la veille
            item("catchup") { CatchUpCard(ui) }
            // Accueil classique : toutes les cartes, dans l'ordre choisi (Paramètres › Accueil)
            for (k in cards) homeCard(ui, k, plan, lv)
        } else simpleHome(ui, cards, plan, st, lv, collapsed) { k -> collapsed = if (k in collapsed) collapsed - k else collapsed + k; ui.prefs.raw.edit().putStringSet("homeCollapsed", collapsed).apply() }
        item("custom") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PButton("Personnaliser l’accueil", { ui.push(Screen.Settings("home")) }, icon = "edit", kind = "ghost", small = true)
            }
        }
    }
    }
}

/** Une carte de l'accueil, par sa clé (voir HOME_CARDS). */
private fun androidx.compose.foundation.lazy.LazyListScope.homeCard(ui: AppState, k: String, plan: Plan, lv: Level) {
    val pg = ui.pg; val u = pg.me() ?: return
    val done = plan.due.count { it.done }; val total = plan.due.size
    when (k) {
        "hero" -> { item("hero") { Hero(ui) }; item("ctx") { ContextCards(ui) } }
        "aipromo" -> item("aipromo") { AiPromoCard(ui) }
        "day" -> item("day") {
            Glass {
                CardHead("Ta journée") { Count("${lv.pts} flammes") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Ring(done, total)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Eyebrow("Niveau ${lv.idx}")
                        Text(lv.name, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Bar(lv.pct * 100)
                        Muted(if (lv.next != null) "${lv.next!! - lv.pts} flammes avant « ${lv.nextName} »" else "Niveau maximum atteint", small = true)
                        if (total > 0 && done == total) PButton("Journée parfaite", { ui.fs = Fs.Celebrate(pg.perfectCelebration()) }, icon = "star", small = true, kind = "primary")
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
        "due" -> item("due") { DueCard(ui, plan, done, total) }
        "verify" -> {
            val verify = pg.db.checkins.filter { c -> c.pending && pg.goalById(c.goalId)?.buddy == u.id }
            if (verify.isNotEmpty()) item("verify") {
                Glass {
                    CardHead("À confirmer pour tes amis") { Count(verify.size) }
                    verify.forEach { c ->
                        val g = pg.goalById(c.goalId)!!; val o = pg.userById(c.userId)
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Av(o, 36.dp)
                            Column(Modifier.weight(1f)) {
                                Text("${o?.name ?: ""} · ${g.icon} ${g.title}", fontWeight = FontWeight.Bold)
                                Muted("${fmtDate(c.date)} · ${when (c.kind) { "done" -> "validé"; "measure" -> "mesure enregistrée"; else -> "+${fmtNum(c.value)} ${g.unit}" }}", small = true)
                            }
                            PButton("Confirmer", { ui.cloud.confirmCheck(c.id, true) }, kind = "primary", small = true)
                            PButton("Refuser", { ui.cloud.confirmCheck(c.id, false) }, small = true)
                        }
                    }
                }
            }
        }
        "ongoing" -> if (plan.ongoing.isNotEmpty() && !u.settings.hideOngoing) item("ongoing") {
            Glass {
                CardHead("En cours") {
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
private fun androidx.compose.foundation.lazy.LazyListScope.simpleHome(ui: AppState, cards: List<String>, plan: Plan, st: Streak, lv: Level, collapsed: Set<String>, toggle: (String) -> Unit) {
    val pg = ui.pg; val u = pg.me() ?: return
    if ("hero" in cards || "day" in cards) { item("hero") { HomeHeader(ui, plan, st, lv) }; item("ctx") { ContextCards(ui) } }
    if ("aipromo" in cards) homeCard(ui, "aipromo", plan, lv)
    for (k in listOf("due", "verify", "ongoing")) if (k in cards) homeCard(ui, k, plan, lv)
    val moments = cards.filter { it in MOMENT_KEYS }
    item("moments") {
        val tiles = rememberMoments(ui, moments)
        if (tiles.isNotEmpty() || "countdown" in moments) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeSectionHead("À la une", "star", "moments" in collapsed) { toggle("moments") }
            androidx.compose.animation.AnimatedVisibility("moments" !in collapsed) { MomentsRow(ui, tiles, "countdown" in moments) }
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
        item("sec:$id") { HomeSectionHead(head.first, head.second, id in collapsed) { toggle(id) } }
        if (id !in collapsed) for (k in shown) homeCard(ui, k, plan, lv)
    }
}

/** En-tête de l'accueil : date, salutation, message et flamme de la série. */
@Composable
private fun Hero(ui: AppState) {
    val pg = ui.pg; val u = pg.me() ?: return; val st = pg.globalStreak(u.id); val p = LocalPal.current
    val vac = pg.vacationOf()?.takeIf { today() >= it.first }
    val msg = if (pg.isHardDay()) "Journée difficile 💛 : seuls tes essentiels comptent aujourd’hui. Fais ce que tu peux, c’est déjà beaucoup."
        else if (vac != null) "En vacances jusqu’au ${fmtDate(vac.second)} 🏖️ : tes séries sont en pause, profite à fond."
        else if (pg.isRest()) "Jour de repos 😴 : ta série est protégée aujourd’hui, sans dépenser de gel. Valide quand même si l’envie te prend."
        else if (p.easy) (if (st.lit) "Bravo, tu as validé aujourd’hui." else "Valide un objectif aujourd’hui pour garder ta série.")
        else if (st.lit) "Ta flamme brûle aujourd'hui. Continue sur ta lancée." else if (st.n > 0) "Valide une action avant minuit pour prolonger ta série de ${pl(st.n, "jour", "jours")}." else "Valide ta première action pour allumer ta flamme."
    val compact = ui.prefs.homeLayout == "compact"
    Glass(strong = false, pad = if (compact) 14.dp else if (ui.prefs.homeLayout == "airy") 26.dp else 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
                Eyebrow(cap(weekdayLongDate(todayDate())))
                Text("Salut ${first(u.name)}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = if (compact) 20.sp else 26.sp)
                if (!compact) Muted(msg)
            }
            Box(Modifier.graphicsLayer { val k = if (compact) .62f else 1f; scaleX = k; scaleY = k }) { HeroFlame(st.n, st.lit) { ui.egg("tickle", "Hé ! Arrête, ça chatouille ! 🔥", "confetti") } }
        }
        if (!compact) { Spacer(Modifier.height(12.dp)); WeekStrip(pg, u.id) }
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
                    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(tc.c.copy(alpha = .18f))
                        .clickable { ui.open(if (k == "more") Sheet.GoalForm() else Sheet.GoalForm(preType = k)) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Pi(if (k == "more") "plus" else typeInfo(k).icon, tint = readable(tc.c)); Text(l, fontWeight = FontWeight.ExtraBold); Muted(d, small = true)
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
        CardHead("Classement des flammes") { PButton("Amis", { ui.nav("friends") }, kind = "ghost", small = true) }
        if (board.size > 1) board.forEachIndexed { i, (x, n) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (x.id == u.id) p.flame.copy(alpha = .14f) else Color.Transparent).clickable { if (x.id != u.id) ui.open(Sheet.User(x.id)) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${i + 1}", fontFamily = Unbounded, fontWeight = FontWeight.Bold, color = p.ink3, modifier = Modifier.width(20.dp))
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
        CardHead("Tes récaps") { Muted("Ton parcours en stats", small = true) }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("week" to "Semaine", "month" to "Mois", "year" to "Année").forEachIndexed { i, (k, l) ->
                val r = Recaps.range(k)
                val name = if (r.past) mapOf("week" to "Semaine passée", "month" to "Mois passé", "year" to "Année passée")[k]!! else l
                val g = SBG[listOf(0, 4, 1)[i]].map(::hex)
                val gph = if (r.past) slowPhase(3200, 12) else 0f
                val glow = object { val value: Float get() = .5f - .5f * kotlin.math.cos(gph * 2f * kotlin.math.PI.toFloat()) }
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(g))
                    .then(if (r.past) Modifier.border((2 + glow.value * 2).dp, Color.White.copy(alpha = .5f + glow.value * .4f), RoundedCornerShape(18.dp)) else Modifier)
                    .clickable { val (sl, st) = Recaps.slides(pg, pg.meId(), k); ui.fs = Fs.Recap(k, sl, st) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (r.past) Text("J+1", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF10102A), modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = .7f)).padding(horizontal = 6.dp, vertical = 1.dp))
                    Pi("star", tint = Color(0xFF10102A))
                    FitText(name, size = 14, min = 9, color = Color(0xFF10102A), weight = FontWeight.ExtraBold)
                    val n = Recaps.countIn(pg, pg.meId(), k)
                    Text("$n", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF10102A))
                    FitText(if (n > 1) "validations" else "validation", size = 12, min = 8, color = Color(0xFF10102A).copy(alpha = .8f))
                    if (r.past) Text("bilan complet, aujourd’hui", fontSize = 11.sp, color = Color(0xFF10102A).copy(alpha = .75f))
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
        Glass { CardHead("Météo"); Muted("Ajoute ta ville pour voir la météo du jour, et un conseil pour tes objectifs."); Spacer(Modifier.height(10.dp)); PButton("Choisir ma ville", { ui.push(Screen.Settings("weather")) }, icon = "pin", small = true) }
        return
    }
    LaunchedEffect(place.lat, place.lon) { Wx.ensure(ctx, ui, false) }
    val c = Wx.cached(ctx, place)
    if (c == null) { Glass { CardHead("Météo · ${place.name}"); if (Wx.error != null) Muted(Wx.error!!) else { Skeleton(Modifier.fillMaxWidth(.4f), 34.dp); Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { Skeleton(Modifier.weight(1f), 64.dp, 16.dp) } } } }; return }
    val w = c.data; val cur = w.current; val info = Weather.wmo(cur.code, cur.isDay); val d0 = w.daily.firstOrNull()
    Glass {
        CardHead(place.name) { IconBtn("gear", "Réglages de la météo", { ui.push(Screen.Settings("weather")) }, plain = true, size = 36.dp) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Emoji(info.emoji, 54.dp)
            Column(Modifier.weight(1f)) {
                Text(Weather.temp(cur.temp), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
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
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(p.ink.copy(alpha = .05f)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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

/**
 * « À valider aujourd'hui ». Avec le coach IA (recommandations activées), la liste est réordonnée selon l'heure et la
 * météo : les objectifs conseillés passent en tête avec la raison du coach, et la carte prend le contour IA.
 */
@Composable
private fun DueCard(ui: AppState, plan: Plan, done: Int, total: Int) {
    val pg = ui.pg; val p = LocalPal.current; val ctx = androidx.compose.ui.platform.LocalContext.current
    val ai = fr.pepsgoal.app.ai.LocalAi.on(ctx, ui.prefs.aiRecommend) && plan.due.any { !it.done }
    val rk = if (ai) fr.pepsgoal.app.ai.Coach.recoKey(ctx) else ""
    // Recommandations mémorisées : affichées tout de suite, recalculées seulement quand le contexte change
    var picks by remember(rk) { mutableStateOf(if (ai) fr.pepsgoal.app.ai.Coach.cachedPicks(ctx, rk) else null) }
    if (ai) LaunchedEffect(rk) { if (picks == null) picks = fr.pepsgoal.app.ai.Coach.recommend(ctx) }
    val reasons = picks?.associate { it.goal.id to it.reason } ?: emptyMap()
    val order = picks?.map { it.goal.id } ?: emptyList()
    val later = Snooze.ids(ui)
    val sorted = plan.due.filter { it.g.id !in later || it.done }.sortedWith(compareBy<DueItem> { it.done }.thenBy { if (it.g.id in order) order.indexOf(it.g.id) else 99 }.thenByDescending { pg.isFav(it.g) })
    val pulse = thinkingAlpha()
    Glass(Modifier.animateContentSize(Motion.standard()), ai = ai) {
        CardHead("À valider aujourd'hui") { Count("$done/$total") }
        if (ai) Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pi("sparkle", size = 15.dp, tint = p.flameInk)
            Muted(if (picks == null) "Le coach trie tes objectifs selon l’heure et la météo…" else "Ordre conseillé par ton coach pour ce moment", if (picks == null) Modifier.alpha(pulse) else Modifier, small = true)
        }
        // Astuce affichée une fois : les gestes de balayage
        if (ui.prefs.swipe && !ui.prefs.swipeHint && sorted.any { !it.done }) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(p.ink.copy(alpha = .05f)).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pi("hand", size = 18.dp, tint = p.flameInk)
            Muted("Balaie un objectif vers la droite pour le valider, vers la gauche pour reporter, mettre en favori ou lancer un Focus.", Modifier.weight(1f).padding(vertical = 8.dp), small = true)
            IconBtn("x", "Masquer l’astuce", { ui.prefs.swipeHint = true; ui.bump() }, plain = true, size = 36.dp)
        }
        if (total == 0) Empty("Rien de prévu aujourd'hui", "Profite de ta journée, ou ajoute une routine.", icon = "sun")
        else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sorted.forEach { d -> key(d.g.id) {
                // Un objectif validé glisse vers le bas de la liste au lieu de sauter
                Column(Modifier.animatePlacement()) {
                    GoalRow(ui, d.g, d.done, d.meta)
                    val why = reasons[d.g.id]
                    if (why != null && !d.done) Row(Modifier.padding(start = 64.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pi("sparkle", size = 13.dp, tint = p.flameInk); Muted(why, small = true, maxLines = 2)
                    }
                }
            } }
            if (later.isNotEmpty()) {
                val n = plan.due.count { it.g.id in later && !it.done }
                if (n > 0) Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pi("clock", size = 16.dp, tint = p.ink2)
                    Muted(pl(n, "objectif reporté à demain", "objectifs reportés à demain"), Modifier.weight(1f), small = true)
                    PButton("Remettre", { ui.prefs.snoozed = ""; ui.bump() }, kind = "ghost", small = true)
                }
            }
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
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (l, ic, go) ->
            Glass(Modifier.weight(1f).fillMaxHeight(), pad = 10.dp, fill = false, onClick = go, ai = ic == "sparkle", border = if (ic == "timer" && focusOn) p.flame else null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pi(ic, size = 22.dp, tint = if (ic == "sparkle" || (ic == "timer" && focusOn)) p.flame else p.ink)
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
                    if (g.planB.isNotBlank()) PButton("Plan B fait", { doValidate(ui, g.id); ui.toast("Plan B validé pour « ${g.title} ». Bien joué !", "check") }, small = true, kind = "primary")
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
                CardHead("Le plan de la semaine") { IconBtn("x", "Masquer", { ui.prefs.raw.edit().putString("planDone", key).apply(); ui.bump() }, plain = true, size = 34.dp) }
                Muted(intro ?: "D’après tes 4 dernières semaines, quelques ajustements pour que tes objectifs restent à ta portée.", small = true)
                sug.forEach { s ->
                    var applied by remember(s.goal.id) { mutableStateOf(false) }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (s.lighten) "🪶" else "🚀", fontSize = 20.sp)
                        Text(s.text, Modifier.weight(1f), fontSize = 14.sp)
                        if (applied) Text("✓", color = p.ok, fontWeight = FontWeight.ExtraBold)
                        else PButton("Oui", { s.apply(); applied = true; ui.toast("Objectif ajusté.", "check") }, small = true, kind = "primary")
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
    val pg = ui.pg; val r = remember { Recaps.wrappedRange() }; val year = parseYmd(r.s).year
    val ph = slowPhase(3600, 12)
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(hex("#241552"), hex("#7A2A8A"), hex("#FF6A3A"))))
        .clickable(onClickLabel = "Ouvrir ma rétrospective") { val (sl, st) = Recaps.wrapped(pg, pg.meId()); ui.fs = Fs.Recap("year", sl, st) }.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.graphicsLayer { val k = 1f + .06f * kotlin.math.sin(ph * 2f * kotlin.math.PI.toFloat()); scaleX = k; scaleY = k; rotationZ = 6f * kotlin.math.sin(ph * 2f * kotlin.math.PI.toFloat()) }) { Emoji("🎁", 46.dp) }
            Column(Modifier.weight(1f)) {
                Text("PEPSGOAL WRAPPED", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .8f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                Text("Ton année $year est prête", color = androidx.compose.ui.graphics.Color.White, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }
        }
        Text("Ton meilleur mois, ton podium, ton profil de l’année… à revivre en story et à partager.", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .9f), fontSize = 14.sp)
    }
}
