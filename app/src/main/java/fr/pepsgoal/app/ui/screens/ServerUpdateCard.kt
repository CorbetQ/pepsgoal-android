package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.sys.ServerUpdate
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal

/**
 * Modération › Serveur : mise à jour du serveur en SSH (root, mot de passe demandé à chaque fois), avec la progression
 * de deploy/update.sh étape par étape et les dernières lignes de son journal.
 */
@Composable
fun ServerUpdateCard(ui: AppState) {
    val p = LocalPal.current; val ctx = LocalContext.current; val U = ServerUpdate
    var host by remember { mutableStateOf(U.host(ctx)) }
    var port by remember { mutableStateOf(U.port(ctx).toString()) }
    var pw by remember { mutableStateOf("") }
    var web by remember { mutableStateOf(false) }
    var adv by remember { mutableStateOf(false) }
    val phase = U.phase
    val pr = remember(U.log) { U.progress }
    LaunchedEffect(phase) {
        if (phase == "done") {
            ui.toast(if (pr.ok) "Serveur mis à jour." else "La mise à jour a échoué : voir le journal.", if (pr.ok) "check" else "x")
            if (pr.ok) ui.cloud.loadStats()
        }
    }
    Glass {
        CardHead("Mise à jour du serveur") {
            when {
                phase == "connecting" || phase == "running" -> Chip("En cours", "clock", p.flame2)
                phase == "done" && pr.ok -> Chip("À jour", "check", p.ok)
                phase == "done" -> Chip("Échec", "x", p.danger)
            }
        }
        Muted("Récupère la dernière version depuis GitHub, reconstruit l’API et la redémarre (deploy/update.sh). Le mot de passe root sert à la connexion SSH : il n’est jamais enregistré.", small = true)
        Spacer(Modifier.height(12.dp))
        when (phase) {
            "connecting" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = p.flame, strokeWidth = 2.dp)
                Muted("Connexion SSH à root@${U.host(ctx)}…", small = true)
            }
            "hostkey" -> U.hostKey?.let { k -> HostKeyBox(k.first, k.second, onTrust = { U.trust(ctx) }, onCancel = { U.cancelTrust() }) }
            "running", "done" -> UpdateProgress(pr, running = phase == "running")
        }
        if (phase == "idle" || phase == "error" || phase == "done") {
            if (phase == "done") Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(pw, { pw = it }, "Mot de passe root", password = true, icon = "lock", max = 256)
                SetRow("Mettre aussi à jour la version web", "app.pepsgoal.fr, environ une minute de plus") { PSwitch(web, { web = it }) }
                if (adv) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(host, { host = it.trim() }, "Serveur SSH", Modifier.weight(1f), max = 120, keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    Field(port, { v -> port = v.filter { it.isDigit() }.take(5) }, "Port", Modifier.width(96.dp), keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                } else Text("Connexion : root@$host:$port · Modifier", Modifier.clip(RoundedCornerShape(8.dp)).clickable { adv = true }.padding(vertical = 4.dp), fontSize = 13.sp, color = p.ink2, fontWeight = FontWeight.Bold)
                Err(U.error)
                if (U.hostKey?.third == true) PButton("Oublier la clé enregistrée", { U.forget(ctx, host, port.toIntOrNull() ?: 22) }, icon = "trash", kind = "ghost", small = true)
                PButton("Mettre à jour le serveur", {
                    if (pw.isEmpty()) ui.toast("Indique le mot de passe root du serveur.", "lock")
                    else { U.start(ctx, host.trim(), port.toIntOrNull() ?: 22, pw, web); pw = "" }
                }, Modifier.fillMaxWidth(), icon = "rocket", kind = "primary")
            }
        }
    }
}

/** Étape en cours, barre de progression et dernières lignes du journal. */
@Composable
private fun UpdateProgress(pr: fr.pepsgoal.core.UpdateLog.Progress, running: Boolean) {
    val p = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (pr.total > 0) "Étape ${pr.step}/${pr.total} · ${pr.label}" else "Démarrage…", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, maxLines = 2)
            Muted("${pr.pct} %", small = true)
        }
        Bar(maxOf(2, pr.pct).toDouble(), height = 10.dp, color = if (pr.done && !pr.ok) p.danger else null)
        if (pr.done) Text(if (pr.ok) "Mise à jour terminée." else "Échec (code ${pr.exit}) : voir le journal ci-dessous.", fontSize = 14.sp, color = if (pr.ok) p.ok else p.danger, fontWeight = FontWeight.Bold)
        if (pr.lines.isNotEmpty()) Box(Modifier.fillMaxWidth().clip(smoothShape(14.dp)).background(p.ink.copy(alpha = .06f)).padding(10.dp)) {
            Text(pr.lines.takeLast(10).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = p.ink2, lineHeight = 15.sp)
        }
        if (running) Muted("Tu peux quitter cet écran : la mise à jour continue sur le serveur.", small = true)
    }
}

/** Première connexion : empreinte de la clé d'hôte à comparer avec celle affichée sur le serveur. */
@Composable
private fun HostKeyBox(type: String, fp: String, onTrust: () -> Unit, onCancel: () -> Unit) {
    val p = LocalPal.current
    val file = when { "ed25519" in type -> "ed25519"; "ecdsa" in type -> "ecdsa"; else -> "rsa" }
    Column(Modifier.fillMaxWidth().clip(smoothShape(16.dp)).background(p.flame.copy(alpha = .1f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Première connexion à ce serveur", fontWeight = FontWeight.ExtraBold)
        Muted("Vérifie que l’empreinte ci-dessous est celle de ton serveur. Sur le VPS : ssh-keygen -lf /etc/ssh/ssh_host_${file}_key.pub", small = true)
        Text(fp, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Muted(type, small = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton("Annuler", onCancel, Modifier.weight(1f), kind = "ghost", small = true)
            PButton("C’est bien elle", onTrust, Modifier.weight(1f), icon = "check", kind = "primary", small = true)
        }
    }
}
