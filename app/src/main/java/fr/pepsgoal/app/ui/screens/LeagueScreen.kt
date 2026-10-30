package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.step
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.*
import fr.pepsgoal.core.model.Avatar

private fun tierColors(name: String) = when (name) { "Diamant" -> listOf("#22D3FF", "#B48CFF"); "Or" -> listOf("#FFB000", "#FFE45C"); "Argent" -> listOf("#A9B4C8", "#E6ECF5"); else -> listOf("#C46A2B", "#F0A86A") }

/** Ligue de la semaine : les flammes gagnées depuis lundi classent tes amis et toi ; un palier selon tes flammes. */
@Composable
fun LeagueScreen(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val me = pg.meId()
    var last by remember { mutableStateOf(false) }
    val from = ymd(weekStart(todayDate()).minusWeeks(if (last) 1 else 0))
    var rows by remember(from) { mutableStateOf<List<LeagueRow>?>(null) }
    var err by remember(from) { mutableStateOf<String?>(null) }
    LaunchedEffect(from) { try { rows = ui.cloud.leagueWeek(from) } catch (e: ApiError) { err = if (e.status == 404) "La ligue arrive avec la prochaine mise à jour du serveur." else e.message } catch (e: Exception) { err = "Ligue indisponible pour le moment." } }
    ScreenFrame(ui, "Ligue", "Retour", { ui.back() }, icon = "trophy", color = p.type("challenge").c, color2 = p.type("deadline").c) {
        item("season") { SeasonCard(ui) }
        item("seg") { Seg(listOf(false to "Cette semaine", true to "Semaine dernière"), last, { last = it }) }
        val l = rows
        when {
            err != null -> item("err") { Glass { Empty("Ligue indisponible", err!!, icon = "trophy") } }
            l == null -> { item("s1") { SkeletonCard() }; item("s2") { SkeletonCard(round = true) } }
            else -> {
                val mine = l.firstOrNull { it.userId == me }
                if (mine != null) item("me") {
                    val (tn, te, _) = League.tier(mine.points); val nx = League.next(mine.points); val rank = l.indexOf(mine) + 1
                    Glass {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.size(72.dp, 76.dp).chunk(vivid(hexC(tierColors(tn).first())), hexC(tierColors(tn).first()).step(), 24.dp, 4.dp, 0.dp), contentAlignment = Alignment.Center) { Emoji(te, 42.dp) }
                            Column(Modifier.weight(1f)) {
                                Eyebrow("Ligue $tn")
                                Text("${rank}${if (rank == 1) "er" else "e"} sur ${l.size}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                                Muted("${mine.points} flammes · ${pl(mine.days, "jour actif", "jours actifs")}", small = true)
                            }
                        }
                        if (nx != null && !last) {
                            Spacer(Modifier.height(10.dp))
                            Bar(mine.points * 100.0 / nx.third, color = p.flame)
                            Muted("Encore ${nx.third - mine.points} flammes pour la ligue ${nx.first} ${nx.second}", small = true)
                        }
                    }
                }
                item("board") {
                    Glass(pad = 10.dp) {
                        if (l.size <= 1) Empty("Seul pour l’instant", "Ajoute des amis pour vous mesurer chaque semaine.", icon = "users") { PButton("Trouver des amis", { ui.back(); ui.friendsTab = "discover"; ui.nav("friends") }, icon = "userplus", small = true) }
                        l.forEachIndexed { i, r ->
                            val meRow = r.userId == me; val (tn, te, _) = League.tier(r.points)
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (meRow) p.selTint(p.flame) else Color.Transparent).heightIn(min = 60.dp).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}" }, Modifier.width(28.dp), fontFamily = Unbounded, fontWeight = FontWeight.Bold)
                                val av = remember(r.userId) { try { JSON.decodeFromJsonElement(Avatar.serializer(), r.avatar) } catch (e: Exception) { null } }
                                Av(pg.userById(r.userId), 36.dp, a = av)
                                Column(Modifier.weight(1f)) { Text(if (meRow) "Toi" else pg.shownName(r.userId, r.name), fontWeight = FontWeight.ExtraBold, maxLines = 1); Muted("$te $tn · ${pl(r.days, "jour", "jours")}", small = true) }
                                Text("${r.points}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold)
                                Flame(18.dp)
                            }
                        }
                    }
                }
                item("help") { Hint("Paliers de la semaine : 🥉 Bronze, 🥈 Argent dès 150 flammes, 🥇 Or dès 350, 💎 Diamant dès 700. Tout repart à zéro chaque lundi.") }
            }
        }
    }
}

private fun hexC(s: String) = fr.pepsgoal.app.ui.theme.hex(s)

/** Carte compacte (onglet Amis) : palier et rang de la semaine ; le lundi, récap de la semaine passée. */
@Composable
fun LeagueCard(ui: AppState) {
    val p = LocalPal.current; val me = ui.pg.meId()
    var mine by remember { mutableStateOf<Pair<Int, LeagueRow>?>(null) }
    var size by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            val l = ui.cloud.leagueWeek(ymd(weekStart(todayDate()))); size = l.size
            l.indexOfFirst { it.userId == me }.takeIf { it >= 0 }?.let { mine = (it + 1) to l[it] }
            // Lundi : récap de la semaine passée, une seule fois
            val wk = weekKey()
            if (wdIdx(todayDate()) <= 1 && ui.prefs.raw.getString("leagueRecap", "") != wk) {
                val prev = ui.cloud.leagueWeek(ymd(weekStart(todayDate()).minusWeeks(1)))
                val i = prev.indexOfFirst { it.userId == me }
                if (i >= 0 && prev[i].points > 0) { val (tn, te, _) = League.tier(prev[i].points); ui.toast("Ligue de la semaine passée : ${i + 1}${if (i == 0) "er" else "e"} sur ${prev.size}, $te $tn avec ${prev[i].points} flammes.", "trophy") }
                ui.prefs.raw.edit().putString("leagueRecap", wk).apply()
            }
        } catch (e: Exception) {}
    }
    val m = mine ?: return
    val (tn, te, _) = League.tier(m.second.points)
    Glass(onClick = { ui.push(Screen.League) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp, 47.dp).chunk(vivid(hexC(tierColors(tn).first())), hexC(tierColors(tn).first()).step(), 17.dp, 3.dp, 0.dp), contentAlignment = Alignment.Center) { Emoji(te, 26.dp) }
            Column(Modifier.weight(1f)) {
                Text("Ligue $tn", fontWeight = FontWeight.ExtraBold)
                Muted("${m.first}${if (m.first == 1) "er" else "e"} sur $size cette semaine · ${m.second.points} flammes", small = true)
            }
            Text("›", color = p.ink3, fontSize = 22.sp)
        }
    }
}
