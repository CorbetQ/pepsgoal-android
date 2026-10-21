package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ai.AiMemory
import fr.pepsgoal.app.ai.LocalAi
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*

/** Paramètres › Assistant IA : compatibilité, installation du modèle, fonctions activables et mémoire. */
@Composable
fun AiSettings(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current; val pr = ui.prefs
    LaunchedEffect(Unit) { LocalAi.refresh(ctx) }
    var model by remember { mutableStateOf(pr.aiModel) }
    val v = LocalAi.VARIANTS.first { it.key == model }
    val checks = remember(model, LocalAi.status) { LocalAi.checks(ctx, v) }
    val ok = checks.all { it.ok }
    val installed = LocalAi.status == "ready"
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Glass {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile("sparkle", p.type("program").c, p.type("measure").c, 46.dp, 15.dp)
                Column(Modifier.weight(1f)) {
                    Text("Coach IA 100 % local", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Muted(when (LocalAi.status) { "ready" -> "Installé · ${v.label.lowercase()}${pr.aiSpeed.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""}"; "downloading" -> "Installation en cours…"; "error" -> "Installation interrompue"; else -> "Non installé" }, small = true)
                }
                if (installed) PSwitch(pr.aiEnabled, { pr.aiEnabled = it; ui.themeTick++ })
            }
            Muted("Le modèle tourne entièrement sur ton téléphone : tes objectifs, ta météo et la mémoire de l’IA ne quittent jamais l’appareil. Seul le fichier du modèle est téléchargé, une fois, depuis Hugging Face.", Modifier.padding(top = 10.dp), small = true)
        }
        if (!installed) Glass {
            Text("1. Choisis le modèle", fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Seg(LocalAi.VARIANTS.map { it.key to "${it.label} · ≈ ${if (it.approxMb >= 1000) "%.1f Go".format(it.approxMb / 1000.0) else "${it.approxMb} Mo"}" }, model, { model = it; pr.aiModel = it })
            Muted(if (model == "light") "Rapide, idéal pour la plupart des téléphones (4 Go de RAM)." else "Réponses plus riches et meilleur français, pour les téléphones récents (8 Go de RAM).", Modifier.padding(top = 6.dp), small = true)
            Text("2. Vérification du téléphone", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            checks.forEach { c ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip(if (c.ok) "OK" else "Non", if (c.ok) "check" else "x", if (c.ok) p.ok else p.danger)
                    Column(Modifier.weight(1f)) { Text(c.label, fontWeight = FontWeight.Bold, fontSize = 14.sp); Muted(c.detail, small = true) }
                }
            }
            Spacer(Modifier.height(10.dp))
            when (LocalAi.status) {
                "downloading" -> {
                    Bar(LocalAi.progress * 100.0, height = 10.dp)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Muted(if (LocalAi.progress >= 1f) "Premier démarrage du modèle…" else "Téléchargement… ${(LocalAi.progress * 100).toInt()} %", Modifier.weight(1f), small = true)
                        PButton("Annuler", { LocalAi.cancel(ctx) }, kind = "ghost", small = true)
                    }
                    Muted("Tu peux quitter cette page : le téléchargement continue (Wi-Fi conseillé).", small = true)
                }
                else -> {
                    LocalAi.error?.let { Err(it) }
                    if (ok) PButton("Installer le coach IA", { LocalAi.install(ctx) }, Modifier.fillMaxWidth(), icon = "down", kind = "primary")
                    else Note("Ce téléphone ne remplit pas toutes les conditions pour ce modèle.${if (model == "advanced" && LocalAi.compatible(ctx, LocalAi.VARIANTS[0])) " Le modèle léger, lui, est compatible." else ""}", "x")
                }
            }
        }
        if (installed) Glass {
            Text("Fonctions", fontWeight = FontWeight.ExtraBold)
            var a by remember { mutableStateOf(pr.aiCreate) }; var b by remember { mutableStateOf(pr.aiRecommend) }; var c by remember { mutableStateOf(pr.aiTips) }
            SetRow("Créer un objectif en une phrase", "« Courir 3 fois par semaine », « Lire 12 livres cette année »…") { PSwitch(a, { a = it; pr.aiCreate = it }, enabled = pr.aiEnabled) }
            SetRow("Recommandations du moment", "Les objectifs à faire maintenant selon l’heure et la météo") { PSwitch(b, { b = it; pr.aiRecommend = it }, enabled = pr.aiEnabled) }
            SetRow("Conseils sur l’accueil", "Un conseil personnalisé, renouvelé au fil de la journée") { PSwitch(c, { c = it; pr.aiTips = it }, enabled = pr.aiEnabled) }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton("Supprimer le modèle", { ui.confirm("Supprimer le modèle IA ?", "Le fichier du modèle (≈ ${v.approxMb} Mo) est effacé et l’IA désactivée. Tu pourras le réinstaller à tout moment.", "Supprimer") { LocalAi.uninstall(ctx); ui.toast("Modèle IA supprimé.", "trash") } }, icon = "trash", kind = "danger", small = true)
            }
        }
        MemoryCard(ui)
    }
}

/** Mémoire de l'IA : ce que le coach retient, lisible entrée par entrée, effaçable. */
@Composable
private fun MemoryCard(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current; val pr = ui.prefs
    AiMemory.rev
    val list = remember(AiMemory.rev) { AiMemory.all(ctx) }
    var open by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(pr.aiMemory) }
    Glass {
        CardHead("Mémoire de l’IA") { Count(list.size) }
        Muted("Le coach retient tes demandes et tes réactions pour mieux te conseiller. Cette mémoire reste sur ton téléphone.", small = true)
        SetRow("Mémoriser") { PSwitch(on, { on = it; pr.aiMemory = it }) }
        if (list.isEmpty()) Muted("La mémoire est vide.", Modifier.padding(top = 6.dp), small = true)
        else {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(if (open) "Masquer" else "Lire la mémoire", { open = !open }, icon = "book", small = true)
                PButton("Vider la mémoire", { ui.confirm("Vider la mémoire de l’IA ?", "Les ${list.size} souvenirs du coach seront effacés définitivement.", "Vider") { AiMemory.clear(ctx); ui.toast("Mémoire de l’IA vidée.", "trash") } }, icon = "trash", kind = "danger", small = true)
            }
            if (open) Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.reversed().forEach { m ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { Text(m.text, fontSize = 14.sp); Muted("${ago(m.at)} · ${MEM_KIND[m.kind] ?: m.kind}", small = true) }
                        IconBtn("x", "Oublier ce souvenir", { AiMemory.remove(ctx, m.at) }, plain = true, size = 34.dp)
                    }
                }
            }
        }
    }
}

private val MEM_KIND = mapOf("goal" to "création d’objectif", "tip" to "conseil", "reco" to "recommandation", "system" to "système")
