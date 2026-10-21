package fr.pepsgoal.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.sys.Health
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*
import kotlinx.coroutines.launch

/** Paramètres › Santé : Health Connect, valeurs du jour et liens « objectif ↔ mesure » pour la validation automatique. */
@Composable
fun HealthSettings(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val scope = rememberCoroutineScope()
    val available = remember { Health.available(ctx) }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var values by remember { mutableStateOf<Map<String, Double?>>(emptyMap()) }
    var links by remember { mutableStateOf(Health.links(ctx)) }
    val refresh = { scope.launch { granted = Health.granted(ctx); values = Health.METRICS.associate { it.first to Health.today(ctx, it.first) } }; Unit }
    val perm = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { refresh() }
    LaunchedEffect(Unit) { refresh() }
    val ok = granted.containsAll(Health.PERMS)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Glass {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile("heart", p.type("deadline").c, p.type("duration").c, 46.dp, 15.dp)
                Column(Modifier.weight(1f)) {
                    Text("Health Connect", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Muted(when { !available -> "Non disponible sur ce téléphone"; ok -> "Connecté"; else -> "Autorisations à accorder" }, small = true)
                }
                if (available && ok) Chip("OK", "check", p.ok)
            }
            Muted("Relie un objectif à tes pas, ton sommeil, tes séances de sport ou ton hydratation : il se valide tout seul dès que le seuil du jour est atteint. Les données restent sur ton téléphone.", Modifier.padding(top = 10.dp), small = true)
            Spacer(Modifier.height(10.dp))
            when {
                !available -> PButton(if (Health.needsInstall(ctx)) "Mettre à jour Health Connect" else "Installer Health Connect", {
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { ui.toast("Ouvre le Play Store pour installer Health Connect.", "x") }
                }, icon = "down", kind = "primary")
                !ok -> PButton("Autoriser l’accès à mes données Santé", { perm.launch(Health.PERMS) }, icon = "heart", kind = "primary")
                else -> FlowRowCompat(gap = 6.dp) {
                    Health.METRICS.forEach { (k, l, unit) ->
                        val v = values[k]
                        Chip("$l : ${v?.let { if (k == "steps" || k == "exercise") fmtNum(it.toInt()) else "%.1f".format(it) } ?: "—"} $unit")
                    }
                }
            }
        }
        if (available && ok) Glass {
            CardHead("Validation automatique") { Count(links.size) }
            if (links.isEmpty()) Muted("Aucun lien pour l’instant.", small = true)
            links.forEach { l ->
                val g = pg.goalById(l.goalId); val m = Health.METRICS.firstOrNull { it.first == l.metric }
                if (g != null && m != null) Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GoalIcon(g, 34.dp)
                    Column(Modifier.weight(1f)) { Text(g.title, fontWeight = FontWeight.Bold, maxLines = 1); Muted("${m.second} ≥ ${if (l.threshold % 1.0 == 0.0) fmtNum(l.threshold.toInt()) else l.threshold.toString()} ${m.third}", small = true) }
                    IconBtn("x", "Retirer ce lien", { links = links - l; Health.saveLinks(ctx, links) }, plain = true, size = 36.dp)
                }
            }
            // Nouveau lien
            val candidates = pg.myGoals(u.id).filter { !pg.isClosedGoal(it) && it.type in setOf("routine", "challenge", "timed", "abstinence") && links.none { l -> l.goalId == it.id } }
            if (candidates.isNotEmpty()) {
                var goal by remember { mutableStateOf<String?>(null) }; var metric by remember { mutableStateOf("steps") }; var thr by remember { mutableStateOf("") }
                SectionTitle("Ajouter un lien")
                Text("Objectif", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                FlowRowCompat(Modifier.padding(vertical = 6.dp), gap = 6.dp) { candidates.forEach { g -> FChip("${g.icon} ${g.title}", goal == g.id, { goal = g.id }) } }
                Seg(Health.METRICS.map { it.first to it.second }, metric, { metric = it; thr = "" })
                val unit = Health.METRICS.first { it.first == metric }.third
                val def = when (metric) { "steps" -> "8000"; "sleep" -> "7"; "exercise" -> "30"; else -> "1.5" }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(thr, { thr = it }, "Seuil ($unit)", Modifier.weight(1f), placeholder = def, max = 8)
                    PButton("Relier", {
                        val g = goal; val t = (thr.ifBlank { def }).replace(',', '.').toDoubleOrNull()
                        if (g == null || t == null || t <= 0) ui.toast("Choisis un objectif et un seuil.", "x")
                        else { links = links + Health.Link(g, metric, t); Health.saveLinks(ctx, links); goal = null; thr = ""; scope.launch { Health.sync(ctx, force = true) }; ui.toast("Objectif relié à Santé.", "heart") }
                    }, icon = "link", kind = "primary", small = true)
                }
            } else if (links.isEmpty()) Muted("Crée d’abord une routine (ex. « Marcher 8 000 pas ») pour la relier.", small = true)
        }
    }
}
