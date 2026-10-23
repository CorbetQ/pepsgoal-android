package fr.pepsgoal.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.*
import fr.pepsgoal.core.model.Post
import fr.pepsgoal.core.model.User
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

val MOD_TABS = listOf(Triple("accounts", "Comptes", "users"), Triple("reports", "Signalements", "flag"), Triple("appeals", "Contestations", "ban"),
    Triple("maint", "Maintenances", "gauge"), Triple("news", "Annonces", "send"), Triple("server", "Serveur", "server"))

@Composable
fun ModerationScreen(ui: AppState) {
    ui.watch()
    val pg = ui.pg
    val counts = mapOf("reports" to "${Mod.pending(ui).size + Mod.openReports(ui).size}".takeIf { it != "0" }, "appeals" to "${Mod.openAppeals(ui).size}".takeIf { it != "0" },
        "maint" to if (pg.maintNow() != null) "●" else if (pg.maintNext() != null) "◷" else null)
    ScreenList {
        item("tabs") { ChipsRow { MOD_TABS.forEach { (k, l, i) -> FChip(l + (counts[k]?.let { " $it" } ?: ""), ui.modTab == k, { ui.modTab = k }, leading = { Pi(i, size = 15.dp) }) } } }
        when (ui.modTab) {
            "reports" -> item("b") { ModReports(ui) }
            "appeals" -> item("b") { ModAppeals(ui) }
            "maint" -> item("b") { ModMaint(ui) }
            "news" -> item("b") { ModNews(ui) }
            "server" -> item("b") { ModServer(ui) }
            else -> item("b") { ModAccounts(ui) }
        }
    }
}

@Composable
fun StatusChips(ui: AppState, x: User) {
    val p = LocalPal.current; val pg = ui.pg
    if (pg.isAdmin(x)) Chip("Admin", "shield", p.type("project").c)
    if (pg.isBanned(x)) Chip("Banni ${if (x.ban?.until == null) "définitivement" else "temporairement"}", "ban", p.danger)
    if (x.warned != null) Chip("Sous avertissement", "bell", p.type("deadline").c)
    if (x.noComments) Chip("Commentaires restreints", "comment", p.type("deadline").c)
}

@Composable
private fun Tiles(items: List<Triple<String, String, String?>>) {
    val p = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (v, l, sub) ->
                    Glass(Modifier.weight(1f).fillMaxHeight(), pad = 12.dp, radius = 18.dp) {
                        FitText(v, size = 20, min = 11, weight = FontWeight.ExtraBold, family = Unbounded)
                        Text(l, fontSize = 12.sp, color = p.ink2, fontWeight = FontWeight.Bold)
                        sub?.let { Text(it, fontSize = 11.sp, color = p.ink3) }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModAccounts(ui: AppState) {
    val pg = ui.pg
    var fl by remember { mutableStateOf("all") }; var q by remember { mutableStateOf("") }
    val people = pg.db.users.filter { it.role == null }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Tiles(listOf(Triple("${people.size}", "Comptes", null), Triple("${people.count { pg.isBanned(it) }}", "Bannis", null), Triple("${people.count { it.warned != null }}", "Sous avertissement", null), Triple("${people.count { it.noComments }}", "Commentaires restreints", null)))
        Glass {
            CardHead("Comptes") { Count(people.size) }
            ChipsRow { listOf("all" to "Tous", "banned" to "Bannis", "warned" to "Avertis", "muted" to "Commentaires restreints", "new" to "Nouveaux (7 j)").forEach { (k, l) -> FChip(l, fl == k, { fl = k }) } }
            Spacer(Modifier.height(10.dp))
            Field(q, { q = it }, "", placeholder = "Rechercher un compte : nom ou @utilisateur", max = 40, icon = "search")
            val ql = q.trim().lowercase()
            val list = pg.db.users.filter { !pg.isOfficial(it) }.filter { x -> fl == "all" || (fl == "banned" && pg.isBanned(x)) || (fl == "warned" && x.warned != null) || (fl == "muted" && x.noComments) || (fl == "new" && nowMs() - x.createdAt < 7 * DAY_MS) }
                .filter { ql.isEmpty() || pg.realName(it).lowercase().contains(ql) || it.username.contains(ql) }
                .sortedWith(compareByDescending<User> { (if (pg.isBanned(it)) 1 else 0) + (if (it.warned != null) 1 else 0) }.thenBy { it.name.lowercase() })
            if (list.isEmpty()) Muted("Aucun compte ne correspond.", Modifier.padding(top = 8.dp), small = true)
            list.take(80).forEach { x ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { ui.open(Sheet.ModUser(x.id)) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Av(x, 40.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(pg.realName(x), fontWeight = FontWeight.ExtraBold)
                        Muted("@${x.username} · ${pl(pg.db.posts.count { it.userId == x.id }, "publication", "publications")}", small = true)
                        FlowRowCompat(gap = 4.dp) { StatusChips(ui, x) }
                    }
                    PButton("Gérer", { ui.open(Sheet.ModUser(x.id)) }, small = true)
                }
            }
        }
        Glass {
            CardHead("Journal de modération")
            val log = pg.db.modlog.takeLast(15).reversed()
            if (log.isEmpty()) Muted("Aucune action pour l'instant.", small = true)
            log.forEach { l -> Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Muted(ago(l.at), Modifier.width(96.dp), small = true); Text(l.action + if (l.detail.isNotEmpty()) " · ${l.detail}" else "", fontSize = 14.sp) } }
        }
    }
}

@Composable
private fun ModPostCard(ui: AppState, post: Post, actions: @Composable () -> Unit) {
    val pg = ui.pg; val a = pg.userById(post.userId)
    var photo by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Av(a, 30.dp); Text(a?.name ?: "?", fontWeight = FontWeight.ExtraBold)
            Muted("@${a?.username ?: ""} · ${ago(post.at)} · ${VIS[post.visibility]?.first ?: ""}", Modifier.weight(1f), small = true, maxLines = 1)
        }
        a?.let { FlowRowCompat(gap = 4.dp) { StatusChips(ui, it) } }
        if (post.text.isNotEmpty()) Text(post.text)
        post.poll?.let { Muted("Sondage : " + it.options.joinToString(" · ") { o -> o.t }, small = true) }
        post.photo?.let { AsyncImage(it, "Photo de la publication", Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(14.dp)).clickable { photo = true }, contentScale = ContentScale.Crop) }
        FlowRowCompat(gap = 6.dp) { actions() }
    }
    if (photo) post.photo?.let { PhotoViewer(it) { photo = false } }
}

@Composable
private fun ModReports(ui: AppState) {
    val pg = ui.pg; val p = LocalPal.current
    val rp = Mod.openReports(ui); val pp = Mod.pending(ui); val recent = pg.db.posts.sortedByDescending { it.at }.take(15)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Glass {
            CardHead("Publications signalées") { Count(rp.size) }
            if (rp.isEmpty()) Muted("Rien à signaler.", small = true)
            rp.forEach { (post, rs) -> ModPostCard(ui, post) {
                Chip("${pl(rs.size, "signalement", "signalements")} · ${rs.map { REASON_LBL[it.reason] ?: "Autre" }.distinct().joinToString(", ")}", color = p.type("deadline").c)
                PButton("Supprimer", { modDeletePost(ui, post) }, icon = "trash", kind = "danger", small = true)
                PButton("Ignorer", { ui.run { ui.cloud.modPost(post.id, "dismiss") } }, small = true)
                PButton("Gérer l'auteur", { ui.open(Sheet.ModUser(post.userId)) }, small = true)
            } }
        }
        Glass {
            CardHead("Publications à valider") { Count(pp.size) }
            if (pp.isEmpty()) Muted("Aucune publication en attente. Les publications des comptes sous avertissement arrivent ici avant d'être visibles.", small = true)
            pp.forEach { post -> ModPostCard(ui, post) {
                PButton("Valider", { ui.run("Publication validée.") { ui.cloud.modPost(post.id, "approve") } }, icon = "check", kind = "primary", small = true)
                PButton("Refuser", { ui.run("Publication refusée.", "x") { ui.cloud.modPost(post.id, "reject") } }, icon = "x", kind = "danger", small = true)
            } }
        }
        Glass {
            CardHead("Publications récentes")
            recent.forEach { post -> ModPostCard(ui, post) {
                if (post.status == "pending") Chip("En attente", color = p.type("deadline").c)
                PButton(if (post.commentsLocked) "Rouvrir les commentaires" else "Fermer les commentaires", { ui.run(if (post.commentsLocked) "Commentaires rouverts." else "Commentaires fermés sur cette publication.", "lock") { ui.cloud.modPost(post.id, if (post.commentsLocked) "unlock" else "lock") } }, icon = if (post.commentsLocked) "comment" else "lock", small = true)
                PButton("Supprimer", { modDeletePost(ui, post) }, icon = "trash", kind = "danger", small = true)
            } }
        }
    }
}

@Composable
private fun ModAppeals(ui: AppState) {
    val pg = ui.pg; val p = LocalPal.current
    val ap = Mod.openAppeals(ui)
    val done = pg.db.appeals.filter { it.status != "pending" && pg.userById(it.userId) != null }.sortedByDescending { it.doneAt ?: it.at }.take(15)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Glass {
            CardHead("En attente") { Count(ap.size) }
            if (ap.isEmpty()) Muted("Aucune contestation en cours. Les personnes bannies peuvent demander un débannissement depuis le Feed.", small = true)
            ap.forEach { a ->
                val x = pg.userById(a.userId)!!
                var ans by remember(a.id) { mutableStateOf("") }
                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Av(x, 30.dp); Text(pg.realName(x), fontWeight = FontWeight.ExtraBold); Muted("${ago(a.at)} · banni ${pg.banText(x).ifEmpty { "(ban expiré)" }}", small = true) }
                    x.ban?.reason?.takeIf { it.isNotEmpty() }?.let { Muted("Motif du ban : $it", small = true) }
                    Text("« ${a.text} »")
                    Field(ans, { ans = it }, "Réponse à la personne (optionnelle)", max = 200)
                    FlowRowCompat(gap = 6.dp) {
                        PButton("Lever le ban", { ui.run("Ban levé.") { ui.cloud.modAppeal(a.id, true, ans.trim()) } }, kind = "primary", small = true)
                        PButton("Refuser", { ui.run("Demande refusée.", "x") { ui.cloud.modAppeal(a.id, false, ans.trim()) } }, small = true)
                        PButton("Voir le compte", { ui.open(Sheet.ModUser(x.id)) }, kind = "ghost", small = true)
                    }
                }
            }
        }
        Glass {
            CardHead("Historique")
            if (done.isEmpty()) Muted("Aucune contestation traitée pour l'instant.", small = true)
            done.forEach { a ->
                val x = pg.userById(a.userId)!!
                Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Av(x, 26.dp); Text(pg.realName(x), fontWeight = FontWeight.ExtraBold)
                        Chip(if (a.status == "accepted") "Acceptée" else "Refusée", color = if (a.status == "accepted") p.type("routine").c else p.danger)
                        Muted(ago(a.doneAt ?: a.at), small = true)
                    }
                    Text("« ${a.text} »", fontSize = 14.sp)
                    if (a.answer.isNotEmpty()) Muted("Réponse : ${a.answer}", small = true)
                }
            }
        }
    }
}

/** Choix d'une date et d'une heure avec les sélecteurs d'Android. */
fun pickDateTime(ctx: Context, initial: Long, onPick: (Long) -> Unit) {
    val t = localDateTime(initial)
    DatePickerDialog(ctx, { _, y, m, d ->
        TimePickerDialog(ctx, { _, h, mi -> onPick(java.time.LocalDateTime.of(y, m + 1, d, h, mi).atZone(Clock.zone()).toInstant().toEpochMilli()) }, t.hour, t.minute, true).show()
    }, t.year, t.monthValue - 1, t.dayOfMonth).show()
}

@Composable
private fun ModMaint(ui: AppState) {
    val pg = ui.pg; val p = LocalPal.current; val ctx = LocalContext.current
    val now = pg.maintNow(); val next = pg.maintNext(); val m = now ?: next
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Glass {
            CardHead("État de la plateforme") {
                if (now != null) Chip("En maintenance", "gauge", p.type("deadline").c) else if (next != null) Chip("Programmée", "clock", p.type("project").c) else Chip("En ligne", "check", p.type("routine").c)
            }
            if (m != null) {
                Text(rich("${if (now != null) "En cours" else "Prévue le ${fmtDT(m.start)}"}${m.until?.let { " jusqu'au ${fmtDT(it)}" } ?: ", sans fin programmée"} · **${MAINT_MODE[m.mode]}**"))
                if (m.message.isNotEmpty()) Muted("« ${m.message} »", Modifier.padding(top = 4.dp))
                PButton(if (now != null) "Terminer la maintenance" else "Annuler la maintenance", { ui.run(if (now != null) "Maintenance terminée." else "Maintenance annulée.") { ui.cloud.endMaintenance() } }, Modifier.padding(top = 12.dp), icon = "check", kind = "primary", small = true)
            } else Muted("Tout fonctionne normalement. Programme une maintenance ci-dessous si besoin.")
        }
        if (m == null) {
            var mode by remember { mutableStateOf("social") }; var start by remember { mutableLongStateOf(nowMs()) }; var until by remember { mutableStateOf<Long?>(nowMs() + 2 * 3_600_000) }
            var msg by remember { mutableStateOf("") }; var notify by remember { mutableStateOf(true) }
            Glass {
                CardHead("Nouvelle maintenance")
                Seg(listOf("social" to "Social en lecture seule", "full" to "Application complète"), mode, { mode = it })
                Muted("« Social en lecture seule » bloque publications, commentaires, encouragements, demandes d'amis et messages. « Application complète » affiche un écran de maintenance à tous les comptes sauf l'admin.", Modifier.padding(vertical = 8.dp), small = true)
                SetRow("Début", fmtDT(start)) { PButton("Changer", { pickDateTime(ctx, start) { start = it } }, icon = "clock", small = true) }
                SetRow("Fin (optionnelle)", until?.let { fmtDT(it) } ?: "Sans fin programmée") {
                    PButton("Changer", { pickDateTime(ctx, until ?: (start + 2 * 3_600_000)) { until = it } }, icon = "clock", small = true)
                    if (until != null) PButton("Aucune", { until = null }, kind = "ghost", small = true)
                }
                Field(msg, { msg = it }, "Message affiché aux utilisateurs", placeholder = "Ex. Mise à jour des serveurs, merci de votre patience.", max = 200)
                SetRow("Prévenir tout le monde par un message de PepsGoal Officiel") { PSwitch(notify, { notify = it }) }
                PButton("Programmer la maintenance", {
                    if (until != null && until!! <= start) { ui.toast("La fin doit être après le début.", "x"); return@PButton }
                    ui.run("Maintenance programmée.", "gauge") { ui.cloud.startMaintenance(mode, start, until, msg.trim(), notify) }
                }, icon = "gauge", kind = "primary")
            }
        }
        Glass {
            CardHead("Historique des maintenances")
            val hist = pg.db.maintLog.takeLast(8).reversed()
            if (hist.isEmpty()) Muted("Aucune maintenance pour l'instant.", small = true)
            hist.forEach { h -> Row(Modifier.padding(vertical = 4.dp)) { Text("${fmtDT(h.start)}${h.until?.let { " → ${fmtDT(it)}" } ?: ""}${if (h.message.isNotEmpty()) " · ${h.message}" else ""}", Modifier.weight(1f), fontSize = 14.sp); Muted("${if (h.mode == "full") "Complète" else "Sociale"}${if (h.endedAt != null) " · terminée" else ""}", small = true) } }
        }
    }
}

@Composable
private fun ModNews(ui: AppState) {
    val pg = ui.pg; val p = LocalPal.current
    var to by remember { mutableStateOf<String?>(null) }; var tag by remember { mutableStateOf("news") }; var text by remember { mutableStateOf("") }
    var pickTo by remember { mutableStateOf(false) }; var q by remember { mutableStateOf("") }
    val news = pg.db.news.reversed()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Glass {
            CardHead("Modèles rapides")
            FlowRowCompat(gap = 6.dp) { NEWS_TPL.forEach { t -> PButton(t.second, { tag = t.first; text = t.third }, icon = OFFICIAL_TAGS[t.first]?.second, small = true) } }
        }
        Glass {
            CardHead("PepsGoal Officiel") { Muted("Rattaché à ton compte admin", small = true) }
            SetRow("Destinataire", to?.let { pg.userById(it)?.let { x -> "${pg.realName(x)} (@${x.username})" } } ?: "Tout le monde (annonce)") {
                PButton("Choisir", { pickTo = !pickTo }, small = true); if (to != null) PButton("Tous", { to = null }, kind = "ghost", small = true)
            }
            if (pickTo) {
                Field(q, { q = it }, "", placeholder = "Rechercher : nom ou @utilisateur", max = 40, icon = "search")
                pg.db.users.filter { it.role == null && (q.isBlank() || pg.realName(it).lowercase().contains(q.lowercase()) || it.username.contains(q.lowercase())) }.sortedBy { it.name.lowercase() }.take(12).forEach { x ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { to = x.id; pickTo = false }.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Av(x, 28.dp); Text("${pg.realName(x)} (@${x.username})") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Seg(OFFICIAL_TAGS.keys.map { it to OFFICIAL_TAGS.getValue(it).first }, tag, { tag = it }, icons = OFFICIAL_TAGS.values.map { it.second })
            Spacer(Modifier.height(8.dp))
            Field(text, { text = it }, "Message", placeholder = "Ex. Nouveau : les récaps de l'année sont disponibles sur l'accueil !", singleLine = false, minLines = 3, max = 600)
            Spacer(Modifier.height(8.dp))
            PButton(if (to == null) "Envoyer l'annonce" else "Envoyer le message", {
                if (text.isBlank()) { ui.toast("Écris un message.", "x"); return@PButton }
                val t = text.trim(); val dest = to
                ui.run { val n = ui.cloud.announce(t, tag, dest); text = ""; ui.toast(if (dest == null) "Annonce envoyée à ${pl(n, "compte", "comptes")}." else "Message envoyé.", "send") }
            }, icon = "send", kind = "primary")
        }
        Glass {
            CardHead("Nouveautés envoyées aux nouveaux inscrits") { Count(news.size) }
            Muted("Les 3 dernières nouveautés sont envoyées automatiquement à chaque nouveau compte, avec le message de bienvenue.", Modifier.padding(bottom = 10.dp), small = true)
            if (news.isEmpty()) Muted("Aucune nouveauté enregistrée.", small = true)
            news.forEachIndexed { i, n ->
                Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Chip("Nouveauté", "star", p.type("objective").c); Muted("${ago(n.at)}${if (i < 3) " · envoyée aux nouveaux inscrits" else ""}", small = true) }
                    Text(n.text)
                    PButton("Retirer de la liste", { ui.run("Nouveauté retirée de la liste des nouveaux inscrits.", "trash") { ui.cloud.deleteNews(n.id) } }, icon = "trash", kind = "ghost", small = true)
                }
            }
        }
    }
}

private fun fmtBytes(b: Double?): String {
    if (b == null) return "—"
    val u = listOf("o", "Ko", "Mo", "Go", "To"); var i = 0; var v = b
    while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
    return "${if (v < 10 && i > 0) fmtNum(Math.round(v * 10) / 10.0) else Math.round(v).toString()} ${u[i]}"
}
private fun fmtUptime(s0: Double?): String {
    val s = maxOf(0L, Math.round(s0 ?: 0.0)); val d = s / 86400; val h = s % 86400 / 3600; val m = s % 3600 / 60
    return if (d > 0) "$d j $h h" else if (h > 0) "$h h $m min" else "$m min"
}

@Composable
private fun ModServer(ui: AppState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ServerUpdateCard(ui)
        ModServerStats(ui)
    }
}

@Composable
private fun ModServerStats(ui: AppState) {
    val p = LocalPal.current; val cloud = ui.cloud
    LaunchedEffect(Unit) { while (true) { cloud.loadStats(); delay(30_000) } }
    val d = cloud.stats
    if (d == null) { if (cloud.statsError != null) Glass { Muted(cloud.statsError!!) } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { SkeletonCard(); SkeletonCard() }; return }
    fun num(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.doubleOrNull
    fun n(o: JsonObject?, k: String) = num(o, k)?.let { fmtNum(it) } ?: "—"
    val c = d.obj("counts"); val s = d.obj("server"); val t = d.obj("traffic"); val mem = s.obj("memory")
    val plus = { k: String -> num(c, k)?.takeIf { it > 0 }?.let { "+${fmtNum(it)} en 24 h" } ?: "rien en 24 h" }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Glass {
            CardHead("Serveur") { Chip("En ligne", "check", p.type("routine").c) }
            val load = (s?.get("load") as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList()
            Tiles(listOfNotNull(Triple(s.str("version").ifEmpty { "—" }, "Version", null), Triple(fmtUptime(num(s, "uptimeSec")), "En marche depuis", null),
                mem?.let { Triple(fmtBytes(num(it, "rss")), "Mémoire du serveur", num(it, "heapUsed")?.let { h -> "${fmtBytes(h)} de tas JavaScript" }) },
                load.firstOrNull()?.let { Triple(fmtNum(it), "Charge (1 min)", pl(s.int("cpus"), "processeur", "processeurs")) }))
            val tot = num(mem, "systemTotal"); val free = num(mem, "systemFree")
            if (tot != null && free != null && tot > 0) Meter("Mémoire de la machine", ((tot - free) / tot).toFloat())
            if (load.isNotEmpty() && s.int("cpus") > 0) Meter("Charge du processeur", minOf(1.0, load[0] / s.int("cpus")).toFloat())
            Muted(listOf(s.str("node").takeIf { it.isNotEmpty() && it != "—" }?.let { "Node.js $it" }, s.str("platform"), d.obj("database").str("version")).filterNotNull().filter { it.isNotEmpty() }.joinToString(" · "), Modifier.padding(top = 10.dp), small = true)
        }
        if (t != null) Glass {
            CardHead("Trafic") { Muted("depuis le démarrage", small = true) }
            Tiles(listOf(Triple(n(t, "requests"), "Requêtes", null), Triple(n(t, "lastHour"), "Dernière heure", null), Triple("${n(t, "avgMs")} ms", "Temps de réponse moyen", null), Triple(n(t, "serverErrors"), "Erreurs serveur", "${n(t, "clientErrors")} requêtes refusées (4xx)")))
            val pm = (t["perMinute"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList()
            if (pm.isNotEmpty()) { Spacer(Modifier.height(10.dp)); SparkBars(pm, 40.dp); Muted("Requêtes par minute, dernière heure", small = true) }
        }
        Glass {
            CardHead("Communauté")
            Tiles(listOfNotNull(Triple(n(c, "users"), "Comptes", plus("users_1d")), num(c, "active_1d")?.let { Triple(fmtNum(it), "Connectés (24 h)", "${n(c, "active_7d")} sur 7 jours") },
                Triple(n(c, "users_7d"), "Inscrits (7 jours)", "${n(c, "users_30d")} sur 30 jours"), Triple(n(c, "friendships"), "Amitiés", null), Triple(n(c, "groups"), "Groupes de discussion", null),
                num(c, "push_subs")?.let { Triple(fmtNum(it), "Abonnés aux notifications", "${n(c, "devices")} appareils chiffrés") }))
        }
        Glass {
            CardHead("Contenus")
            Tiles(listOf(Triple(n(c, "goals"), "Objectifs", null), Triple(n(c, "checkins"), "Validations", plus("checkins_1d")), Triple(n(c, "posts"), "Publications", plus("posts_1d")),
                Triple(n(c, "comments"), "Commentaires", null), Triple(n(c, "stories_live"), "Stories en ligne", "${n(c, "stories")} au total"), Triple(n(c, "messages"), "Messages", plus("messages_1d"))))
        }
        Glass {
            CardHead("Stockage")
            val st = d.obj("storage")
            Tiles(listOfNotNull(Triple(fmtBytes(num(d.obj("database"), "bytes")), "Base de données", null), st?.let { Triple(fmtBytes(num(it, "bytes")), "Photos et pièces jointes", pl(it.int("files"), "fichier", "fichiers")) },
                num(c, "attachments")?.let { Triple(fmtNum(it), "Pièces jointes chiffrées", null) }, num(c, "notifs_1d")?.let { Triple(fmtNum(it), "Notifications (24 h)", null) }))
        }
        Glass {
            CardHead("14 derniers jours")
            val days = (d["days"] as? kotlinx.serialization.json.JsonArray)?.map { it as JsonObject } ?: emptyList()
            listOf("signups" to "Inscriptions", "checkins" to "Validations", "messages" to "Messages").forEach { (k, l) ->
                val vals = days.map { num(it, k) ?: 0.0 }
                Row(verticalAlignment = Alignment.CenterVertically) { Text(l, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold); Muted("${fmtNum(vals.sum())} sur 14 jours", small = true) }
                SparkBars(vals, 46.dp); Spacer(Modifier.height(10.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Muted("Mis à jour à ${hm(cloud.statsAt)} · actualisation automatique toutes les 30 secondes.", Modifier.weight(1f), small = true)
            PButton("Actualiser", { ui.run { cloud.loadStats() } }, kind = "ghost", small = true)
        }
    }
}

@Composable
private fun Meter(label: String, r: Float) {
    val p = LocalPal.current
    Column(Modifier.padding(top = 10.dp)) {
        Row { Text(label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold); Text("${Math.round(r * 100)} %", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Bar(r * 100.0, color = if (r > .85f) p.danger else null)
    }
}

@Composable
fun SparkBars(vals: List<Double>, h: androidx.compose.ui.unit.Dp) {
    val p = LocalPal.current; val mx = maxOf(1.0, vals.maxOrNull() ?: 1.0)
    Row(Modifier.fillMaxWidth().height(h), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        vals.forEach { v -> Box(Modifier.weight(1f).fillMaxHeight((v / mx).toFloat().coerceAtLeast(.04f)).clip(RoundedCornerShape(3.dp)).background(p.flame)) }
    }
}
