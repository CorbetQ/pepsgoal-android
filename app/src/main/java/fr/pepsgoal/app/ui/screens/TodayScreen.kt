package fr.pepsgoal.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import fr.pepsgoal.core.*

@Composable
fun ScreenList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val bars = LocalBars.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = bars.calculateTopPadding() + 8.dp, bottom = bars.calculateBottomPadding() + 86.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
fun TodayScreen(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val st = pg.globalStreak(u.id); val plan = pg.todayPlan(u.id); val lv = pg.level(pg.points(u.id))
    val done = plan.due.count { it.done }; val total = plan.due.size
    val hasGoals = pg.myGoals(u.id).any { !it.archived }
    val msg = if (st.lit) "Ta flamme brûle aujourd'hui. Continue sur ta lancée." else if (st.n > 0) "Valide une action avant minuit pour prolonger ta série de ${pl(st.n, "jour", "jours")}." else "Valide ta première action pour allumer ta flamme."
    ScreenList {
        item("hero") {
            Glass(strong = false, pad = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Eyebrow(cap(weekdayLongDate(todayDate())))
                        Text("Salut ${first(u.name)}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                        Muted(msg)
                    }
                    HeroFlame(st.n, st.lit)
                }
                Spacer(Modifier.height(12.dp))
                WeekStrip(pg, u.id)
            }
        }
        if (!hasGoals) {
            item("first") { Glass { CardHead("Crée ton premier objectif"); Muted("Choisis ce que tu veux suivre. Tu pourras le valider ici chaque jour."); Spacer(Modifier.height(12.dp)); Starters(ui) } }
            item("wx") { WeatherCard(ui) }
            return@ScreenList
        }
        item("day") {
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
                    }
                }
            }
        }
        item("wx") { WeatherCard(ui) }
        if (pg.socialOn(u)) item("board") { Board(ui) }
        item("due") {
            Glass {
                CardHead("À valider aujourd'hui") { Count("$done/$total") }
                if (total == 0) Empty("Rien de prévu aujourd'hui", "Profite de ta journée, ou ajoute une routine.")
                else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    plan.due.sortedWith(compareBy<DueItem> { it.done }.thenByDescending { pg.isFav(it.g) }).forEach { key(it.g.id) { GoalRow(ui, it.g, it.done, it.meta) } }
                }
            }
        }
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
        if (plan.ongoing.isNotEmpty() && !u.settings.hideOngoing) item("ongoing") {
            Glass {
                CardHead("En cours") {
                    Count(plan.ongoing.size)
                    IconBtn("x", "Masquer la carte « En cours » (réactivable dans Paramètres › Apparence)", { u.settings.hideOngoing = true; pg.store.commit(); ui.toast("Carte « En cours » masquée. Réaffiche-la dans Paramètres › Apparence.", "list") }, plain = true, size = 36.dp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { plan.ongoing.forEach { key(it.id) { GoalRow(ui, it, false) } } }
            }
        }
        item("recap") { RecapCard(ui) }
    }
}

@Composable
fun HeroFlame(n: Int, lit: Boolean) {
    val p = LocalPal.current
    val inf = rememberInfiniteTransition(label = "flame")
    val f by inf.animateFloat(1f, if (p.motion || !lit) 1f else 1.06f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flicker")
    val count = remember { Animatable(0f) }
    LaunchedEffect(n) { if (p.motion) count.snapTo(n.toFloat()) else count.animateTo(n.toFloat(), tween(minOf(1500, 600 + n * 6), easing = FastOutSlowInEasing)) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.graphicsLayer { scaleX = f; scaleY = f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f) }) { Flame(84.dp, dim = !lit) }
        Text("${count.value.toInt()}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
        Text(if (n > 1) "jours de série" else "jour de série", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
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
        } else Empty("", "Ajoute des amis pour comparer vos séries.") { PButton("Trouver des amis", { ui.nav("friends") }, icon = "userplus", small = true) }
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
                val glow = rememberInfiniteTransition(label = "glow").animateFloat(.0f, if (r.past && !p.motion) 1f else 0f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "g")
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
    if (c == null) { Glass { CardHead("Météo · ${place.name}"); Muted(Wx.error ?: "Chargement de la météo…") }; return }
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
