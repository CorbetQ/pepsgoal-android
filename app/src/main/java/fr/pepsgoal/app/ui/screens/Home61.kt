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
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.Goal
import fr.pepsgoal.core.model.unit

/* ------------------------------------------------------------------ En-tête unifié */

/**
 * En-tête de l'accueil simplifié : salutation et message du jour, flamme de la série, avancement du jour (anneau,
 * niveau, flammes), objectif du mois, semaine, et actions de la journée en une ligne de puces.
 */
@Composable
fun HomeHeader(ui: AppState, plan: Plan, st: Streak, lv: Level) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val done = plan.due.count { it.done }; val total = plan.due.size
    val vac = pg.vacationOf()?.takeIf { today() >= it.first }
    val msg = when {
        pg.isHardDay() -> "Journée difficile 💛 : seuls tes essentiels comptent aujourd’hui."
        vac != null -> "En vacances jusqu’au ${fmtDate(vac.second)} 🏖️ : tes séries sont en pause."
        pg.isRest() -> "Jour de repos 😴 : ta série est protégée aujourd’hui."
        total > 0 && done == total -> "Tout est validé aujourd’hui. Bravo !"
        st.lit -> "Ta flamme brûle aujourd’hui. Continue sur ta lancée."
        st.n > 0 -> "Valide une action avant minuit pour prolonger ta série."
        else -> "Valide ta première action pour allumer ta flamme."
    }
    Glass(pad = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow(cap(weekdayLongDate(todayDate())))
                Text("Salut ${first(u.name)}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                Muted(msg, maxLines = 2)
            }
            Box(Modifier.graphicsLayer { scaleX = .74f; scaleY = .74f }) { HeroFlame(st.n, st.lit) { ui.egg("tickle", "Hé ! Arrête, ça chatouille ! 🔥", "confetti") } }
        }
        // Avancement du jour, niveau et flammes en un coup d'œil
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Ring(done, total, 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (total == 0) "Rien de prévu aujourd’hui" else "$done sur $total validés aujourd’hui", fontWeight = FontWeight.ExtraBold)
                Bar(lv.pct * 100, height = 6.dp)
                Muted("Niveau ${lv.idx} · ${lv.name} · ${fmtNum(pg.wallet())} flammes à dépenser", small = true, maxLines = 1)
            }
        }
        // Objectif du mois
        pg.monthFocusGoal()?.let { g ->
            val (d, n) = remember(ui.rev, g.id) { pg.monthFocusProgress(g) }
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.flame.copy(alpha = .1f)).clickable { ui.open(Sheet.Goal(g.id)) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Emoji(g.shownIcon(), 24.dp)
                Column(Modifier.weight(1f)) {
                    Text("Objectif du mois : ${g.shownTitle()}", fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Bar(d * 100.0 / maxOf(1, n), height = 5.dp)
                }
                Text("$d/$n j", fontWeight = FontWeight.ExtraBold, color = p.ink2, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp)); WeekStrip(pg, u.id)
        // Actions de la journée
        Row(Modifier.padding(top = 10.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (total > 0 && done == total) FChip("✨ Journée parfaite", false, { ui.fs = Fs.Celebrate(pg.perfectCelebration()) })
            if (nowHM() >= "18:00") FChip("🌙 Bilan du soir", false, { ui.open(Sheet.Evening) })
            FChip(if (vac != null) "🏖️ En vacances" else if (pg.isRest()) "😴 Jour de repos" else "😴 Repos", vac != null || pg.isRest(), { ui.open(Sheet.Rest) })
            FChip("💛 Journée difficile", pg.isHardDay(), { ui.open(Sheet.Hard) })
            FChip("⌨️ Commande", false, { ui.open(Sheet.QuickCmd) })
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
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.forEach { t ->
            Column(Modifier.width(152.dp).height(118.dp).clip(RoundedCornerShape(20.dp)).glassBg(RoundedCornerShape(20.dp)).border(1.dp, p.glassEdge, RoundedCornerShape(20.dp))
                .clickable(onClickLabel = t.title) { ui.open(Sheet.HomeCard(t.key)) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Emoji(t.emoji, 28.dp)
                Text(t.title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.sub, fontSize = 12.sp, color = p.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (showAdd) Column(Modifier.width(120.dp).height(118.dp).clip(RoundedCornerShape(20.dp)).border(1.5.dp, p.line, RoundedCornerShape(20.dp)).clickable(onClickLabel = "Nouveau compte à rebours") { ui.open(Sheet.HomeCard("countdown")) }.padding(12.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Pi("plus", size = 22.dp, tint = p.ink2); Text("Compte à rebours", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink2)
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

/** Titre de section repliable de l'accueil. */
@Composable
fun HomeSectionHead(title: String, icon: String, collapsed: Boolean, onToggle: () -> Unit) {
    val p = LocalPal.current
    val rot by animateFloatAsState(if (collapsed) -90f else 0f, label = "secRot")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = if (collapsed) "Déplier $title" else "Replier $title", onClick = onToggle).padding(horizontal = 4.dp, vertical = 2.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pi(icon, size = 16.dp, tint = p.flameInk)
        Text(title.uppercase(), Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = p.ink2)
        Pi("down", size = 16.dp, tint = p.ink3, modifier = Modifier.graphicsLayer { rotationZ = rot })
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
        CardHead("Comptes à rebours") { Count(list.size) }
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
        CardHead("Comptes à rebours") { IconBtn("plus", "Nouveau compte à rebours", { ui.open(Sheet.HomeCard("countdown")) }, plain = true, size = 36.dp) }
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
        Line("💝", "Offertes à des amis", w.given, false)
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
