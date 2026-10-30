package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.PLUM
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.*

/*
 * Saisons à thème (6 semaines) : scène et nom de la saison, flammes de la saison, passe de récompenses à paliers et
 * 3 quêtes de la semaine. La logique (calendrier, paliers, quêtes) est dans le cœur : Seasons.kt.
 */

/** Cadre d'avatar débloqué par le passe : anneau aux trois couleurs de la saison [key] (aucun si vide ou inconnu). */
fun Modifier.avatarFrame(key: String, width: Dp = 4.dp): Modifier {
    val t = THEME_SEASONS.firstOrNull { it.key == key } ?: return this
    val cs = t.colors.map { hex(it) }
    return this.border(width, Brush.sweepGradient(cs + cs.first()), CircleShape)
}

/** Carte de l'accueil (facultative) : la saison en cours, ses flammes et les paliers à récupérer. */
@Composable
fun ThemeSeasonCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    val s = remember(ui.rev) { themeSeasonAt() }
    val flames = remember(ui.rev) { pg.seasonFlames(s) }; val toClaim = remember(ui.rev) { pg.tiersToClaim(s).size }
    val next = PASS_NEEDS.firstOrNull { it > flames }
    Glass(onClick = { ui.push(Screen.Season) }, pad = 0.dp) {
        Box(Modifier.fillMaxWidth().height(86.dp)) {
            SceneBg(s.theme.scene, Modifier.fillMaxSize(), still = true)
            Column(Modifier.align(Alignment.CenterStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniPlaque("Saison ${s.n} · semaine ${s.week()}/6")
                Plaque { Emoji(s.theme.emoji, 20.dp); Text(s.theme.name, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Bar(if (next == null) 100.0 else flames * 100.0 / next, height = 10.dp)
                Muted(if (next == null) "Passe complet : $flames flammes de saison" else "$flames / $next flammes de saison", small = true)
            }
            if (toClaim > 0) Chip(pl(toClaim, "palier à récupérer", "paliers à récupérer"), "gift", p.flame)
        }
    }
}

/** Page « Saison » (depuis le Profil ou la carte de l'accueil). */
@Composable
fun SeasonScreen(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val s = themeSeasonAt(); val tc = hex(s.theme.colors[1])
    val flames = pg.seasonFlames(s)
    ScreenFrame(ui, "Saison", "Retour", { ui.back() }, icon = "star", color = tc, color2 = p.type("challenge").c, emoji = s.theme.emoji) {
        item("hero") {
            Glass(pad = 0.dp) {
                Box(Modifier.fillMaxWidth().height(170.dp)) {
                    SceneBg(s.theme.scene, Modifier.fillMaxSize(), still = true)
                    Column(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniPlaque("Saison ${s.n} · du ${fmtDateShort(ymd(s.start))} au ${fmtDateShort(ymd(s.end))}")
                        Plaque { Emoji(s.theme.emoji, 22.dp); Text(s.theme.name, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                    }
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$flames", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = p.ink)
                        Text("flammes de saison", Modifier.padding(bottom = 5.dp), fontWeight = FontWeight.ExtraBold, color = p.ink2)
                    }
                    val next = passTiers(s.theme).firstOrNull { it.need > flames }
                    Bar(if (next == null) 100.0 else flames * 100.0 / next.need, color = tc, height = 12.dp)
                    Muted(listOfNotNull("Semaine ${s.week()} sur 6", pl(s.daysLeft(), "jour restant", "jours restants"), next?.let { "prochain palier à ${it.need}" }).joinToString(" · "), small = true)
                    Muted("Chaque validation faite pendant la saison compte, comme les quêtes de la semaine (+$WEEK_QUEST_BONUS chacune). Les récompenses se récupèrent jusqu’à la fin de la saison.", small = true)
                }
            }
        }
        item("quests") { WeekQuestsCard(ui) }
        item("passHead") { SectionPlaque("Passe de saison", "🎟️", count = "${pg.tiersToClaim(s).size}") }
        passTiers(s.theme).forEach { tier -> item("tier${tier.i}") { TierRow(ui, s, tier, flames) } }
        val frames = THEME_SEASONS.filter { pg.ownsItem("frame:${it.key}") }
        if (frames.isNotEmpty()) item("frames") {
            Glass {
                CardHead("Cadre d’avatar", emoji = "🖼️")
                var cur by remember { mutableStateOf(ui.prefs.avatarFrame) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(64.dp).avatarFrame(cur).padding(5.dp)) { Av(u, 54.dp) }
                    FlowRowCompat(Modifier.weight(1f), gap = 6.dp) {
                        FChip("Aucun", cur.isEmpty(), { cur = ""; ui.prefs.avatarFrame = ""; ui.bump() })
                        frames.forEach { f -> FChip("${f.emoji} ${f.name}", cur == f.key, { cur = f.key; ui.prefs.avatarFrame = f.key; ui.bump() }) }
                    }
                }
            }
        }
        item("next") {
            val n = themeSeasonAt(s.end.plusDays(1))
            Hint("Prochaine saison : ${n.theme.emoji} ${n.theme.name}, dès le ${fmtDate(ymd(n.start))}")
        }
    }
}

/** Quêtes de la semaine : progression et bonus à récupérer. */
@Composable
fun WeekQuestsCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    val qs = remember(ui.rev) { pg.weekQuests() }
    Glass {
        CardHead("Quêtes de la semaine", emoji = "🗺️") { Count("${qs.count { it.done }}/${qs.size}") }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            qs.forEach { q ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmojiTile(q.def.emoji, if (q.done) p.type("routine").c else p.type("period").c, 44.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(q.def.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Bar(q.progress * 100.0 / q.def.target, color = p.type("routine").c, height = 8.dp)
                        Muted("${q.progress} / ${q.def.target} · +$WEEK_QUEST_BONUS flammes de saison", small = true)
                    }
                    when {
                        q.claimed -> Chip("Fait", "check", p.type("routine").c)
                        q.done -> PButton("Récupérer", { pg.claimWeekQuest(q.def.key)?.let { ui.toast(it, "x") } ?: run { Feedback.success(); ui.toast("Quête réussie : +$WEEK_QUEST_BONUS flammes de saison 🗺️", "star"); ui.bump() } }, small = true, kind = "type", color = p.type("routine").c)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Muted("De nouvelles quêtes arrivent chaque lundi.", small = true)
    }
}

/** Un palier du passe : numéro, récompense, seuil ; récupérable, récupéré ou verrouillé. */
@Composable
private fun TierRow(ui: AppState, s: SeasonInfo, tier: PassTier, flames: Int) {
    val p = LocalPal.current; val pg = ui.pg
    val claimed = pg.tierClaimed(s, tier.i); val reached = flames >= tier.need
    val c = hex(s.theme.colors[1])
    Row(Modifier.fillMaxWidth().chunk(if (claimed) p.selTint(c) else p.surface, if (reached && !claimed) p.focusEdge else p.edge, 22.dp, 4.dp, 2.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).chunk(if (reached) vivid(c) else p.surface2, p.edge, 16.dp, 0.dp, 2.dp), contentAlignment = Alignment.Center) {
            Text("${tier.i + 1}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = if (reached) PLUM else p.ink2)
        }
        when (tier.kind) {
            "sticker" -> StickerView(tier.key, tier.emoji, 48.dp, still = true)
            "frame" -> Box(Modifier.size(48.dp).avatarFrame(tier.key, 5.dp), contentAlignment = Alignment.Center) { Emoji(s.theme.emoji, 26.dp) }
            "bg" -> Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))) { SceneBg(tier.key, Modifier.fillMaxSize(), still = true) }
            else -> EmojiTile(tier.emoji, if (tier.kind == "outfit") p.type("monthly").c else p.flame, 48.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tier.label, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 2)
            Muted("${tier.need} flammes de saison", small = true)
        }
        when {
            claimed -> Pi("check", size = 22.dp, tint = p.type("routine").ink)
            reached -> PButton("Récupérer", {
                pg.claimTier(tier.i, s)?.let { ui.toast(it, "x") } ?: run { Feedback.success(); ui.toast("${tier.emoji} ${tier.label} : c’est à toi !", "gift"); ui.bump() }
            }, small = true, kind = "primary")
            else -> Pi("lock", size = 18.dp, tint = p.ink3)
        }
    }
}
