package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.sheets.DateField
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.*
import fr.pepsgoal.core.model.Avatar
import kotlinx.coroutines.launch

/** Carte du feed : les défis de la communauté en cours. */
@Composable
fun ChallengesCard(ui: AppState) {
    val p = LocalPal.current
    var list by remember { mutableStateOf<List<CommunityChallenge>?>(null) }
    LaunchedEffect(Unit) { list = try { ui.cloud.challenges() } catch (e: Exception) { null } }
    val live = list?.filter { !it.ended } ?: return
    if (live.isEmpty() && !ui.pg.isAdmin(ui.pg.me())) return
    Glass(onClick = { ui.push(Screen.Challenges) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile("trophy", p.type("challenge").c, p.type("deadline").c, 44.dp, 14.dp)
            Column(Modifier.weight(1f)) {
                Text("Défis de la communauté", fontWeight = FontWeight.ExtraBold)
                Muted(if (live.isEmpty()) "Aucun défi en cours : crée le premier." else live.take(2).joinToString(" · ") { "${it.emoji} ${it.title}" } + " · ${pl(live.sumOf { it.participants }, "participant", "participants")}", small = true, maxLines = 2)
            }
            Text("›", color = p.ink3, fontSize = 22.sp)
        }
    }
}

/** Page des défis : rejoindre, suivre ses jours, classement ; création par la modération. */
@Composable
fun ChallengesScreen(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return; val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<CommunityChallenge>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<CommunityChallenge?>(null) }
    var creating by remember { mutableStateOf(false) }
    val reload: () -> Unit = {
        scope.launch {
            try { val l = ui.cloud.challenges(); ui.cloud.pushChallengeDays(l); list = ui.cloud.challenges(); err = null }
            catch (e: Exception) { err = e.message ?: "Défis indisponibles pour le moment." }
        }
    }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(open) { detail = null; open?.let { id -> detail = try { ui.cloud.challenge(id) } catch (e: Exception) { null } } }
    ScreenFrame(ui, "Défis", "Retour", { ui.back() }, icon = "trophy", color = p.type("challenge").c, color2 = p.type("deadline").c,
        right = { if (pg.isAdmin(u)) PButton(if (creating) "Fermer" else "Créer", { creating = !creating }, icon = if (creating) "x" else "plus", small = true) }) {
        item("intro") { Muted("Rejoins un défi avec toute la communauté PepsGoal : il s’ajoute à tes objectifs du jour, et chaque validation compte au classement. Ton nom et ton avatar y sont visibles par les participants.", small = true) }
        if (creating) item("new") { NewChallenge(ui) { creating = false; reload() } }
        err?.let { e -> item("err") { Glass { Muted(e, small = true); PButton("Réessayer", reload, small = true) } } }
        val l = list
        if (l == null && err == null) { item("load1") { SkeletonCard() }; item("load2") { SkeletonCard() } }
        if (l != null && l.isEmpty()) item("none") { Glass { Empty("Aucun défi pour l’instant", "Les prochains défis de la communauté apparaîtront ici.", icon = "trophy") } }
        l?.forEach { c -> item(c.id) {
            val g = pg.db.goals.firstOrNull { it.communityId == c.id && it.userId == u.id }
            val tc = p.type("challenge")
            Glass(border = if (c.joined) tc.c else null, onClick = { open = if (open == c.id) null else c.id }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Emoji(c.emoji, 42.dp)
                    Column(Modifier.weight(1f)) {
                        Text(c.title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Muted("${if (c.upcoming) "Commence le ${fmtDate(c.startsOn)}" else if (c.ended) "Terminé le ${fmtDate(c.endsOn)}" else "Jusqu’au ${fmtDate(c.endsOn)}"} · ${pl(c.participants, "participant", "participants")}", small = true)
                    }
                    if (c.joined) Chip("${c.myDays.size} j", "check", tc.c, tc.ink)
                }
                if (c.description.isNotBlank()) Text(c.description, Modifier.padding(top = 8.dp), fontSize = 14.sp)
                if (c.joined && !c.upcoming) {
                    val total = (daysBetween(parseYmd(c.startsOn), parseYmd(c.endsOn)) + 1).toInt(); val passed = (daysBetween(parseYmd(c.startsOn), parseYmd(minOf(today(), c.endsOn))) + 1).toInt().coerceIn(0, total)
                    Bar(c.myDays.size * 100.0 / total, Modifier.padding(top = 10.dp), tc.c)
                    Muted("${pl(c.myDays.size, "jour validé", "jours validés")} sur ${pl(passed, "jour écoulé", "jours écoulés")} (${pl(total, "jour", "jours")} en tout)", small = true)
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        c.ended -> {}
                        !c.joined -> PButton("Rejoindre", {
                            if (pg.socialBlocked("de rejoindre un défi")) return@PButton
                            ui.run("Défi rejoint : il est dans tes objectifs du jour.", "trophy") { ui.cloud.joinAsGoal(c); ui.bump(); reload() }
                        }, icon = "plus", kind = "primary", small = true)
                        else -> {
                            g?.let { gg -> PButton("Mon objectif", { ui.open(fr.pepsgoal.app.Sheet.Goal(gg.id)) }, icon = "target", small = true) }
                            PButton("Quitter", {
                                ui.confirm("Quitter « ${c.title} » ?", "Tes jours validés sont retirés du classement. L’objectif est archivé (tu gardes son historique).", "Quitter") {
                                    ui.run("Défi quitté.", "x") { ui.cloud.leaveChallenge(c.id); g?.let { it.archived = true; pg.store.commit() }; reload() }
                                }
                            }, kind = "ghost", small = true)
                        }
                    }
                    if (pg.isAdmin(u)) PButton("Supprimer", { ui.confirm("Supprimer ce défi ?", "Il disparaît pour tous les participants.", "Supprimer") { ui.run("Défi supprimé.", "trash") { ui.cloud.deleteChallenge(c.id); reload() } } }, kind = "danger", small = true)
                }
                // Classement
                if (open == c.id) {
                    val d = detail
                    Spacer(Modifier.height(10.dp)); SectionTitle("Classement")
                    if (d == null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Skeleton(Modifier.width(28.dp), 20.dp); Skeleton(Modifier.width(30.dp), 30.dp, 15.dp); Skeleton(Modifier.weight(1f), 14.dp); Skeleton(Modifier.width(36.dp), 14.dp) } } }
                    else if (d.board.isEmpty()) Muted("Personne pour l’instant : sois le premier !", small = true)
                    else d.board.forEach { r ->
                        val meRow = r.userId == u.id
                        Row(Modifier.fillMaxWidth().clip(smoothShape(14.dp)).background(if (meRow) p.flame.copy(alpha = .14f) else Color.Transparent).padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(when (r.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "${r.rank}" }, Modifier.width(28.dp), fontFamily = Unbounded, fontWeight = FontWeight.Bold)
                            val av = remember(r.userId) { try { JSON.decodeFromJsonElement(Avatar.serializer(), r.avatar) } catch (e: Exception) { null } }
                            Av(pg.userById(r.userId), 30.dp, a = av)
                            Text(if (meRow) "Toi" else pg.shownName(r.userId, r.name), Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${r.days} j", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        } }
    }
}

/** Création d'un défi (modération). */
@Composable
private fun NewChallenge(ui: AppState, done: () -> Unit) {
    var title by remember { mutableStateOf("") }; var emoji by remember { mutableStateOf("🏁") }; var desc by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(today()) }; var end by remember { mutableStateOf(ymd(todayDate().plusDays(29))) }
    var showIcons by remember { mutableStateOf(false) }
    Glass {
        CardHead("Nouveau défi", emoji = "⚔️")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(emoji, { showIcons = !showIcons }, small = true)
            Field(title, { title = it }, "Titre", Modifier.weight(1f), placeholder = "Octobre sans sucre", max = 60)
        }
        if (showIcons) EmojiPicker(emoji) { emoji = it; showIcons = false }
        Field(desc, { desc = it }, "Description", singleLine = false, minLines = 2, max = 400)
        DateField("Début", start, { start = it })
        DateField("Fin", end, { end = it }, min = start)
        PButton("Publier le défi", {
            if (title.trim().length < 3) { ui.toast("Donne un titre au défi.", "x"); return@PButton }
            if (end < start) { ui.toast("La fin doit suivre le début.", "x"); return@PButton }
            ui.run("Défi publié pour toute la communauté.", "trophy") { ui.cloud.createChallenge(title.trim(), emoji, desc.trim(), start, end); done() }
        }, icon = "check", kind = "primary")
    }
}
