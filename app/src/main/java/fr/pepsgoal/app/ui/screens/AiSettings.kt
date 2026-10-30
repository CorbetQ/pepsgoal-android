package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ai.AiMemory
import fr.pepsgoal.app.ai.LocalAi
import fr.pepsgoal.app.ai.Personas
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
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        CatHero("ai", when (LocalAi.status) { "ready" -> "Coach 100 % local · ${v.label.lowercase()}${pr.aiSpeed.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""}"; "downloading" -> "Installation en cours…"; "error" -> "Installation interrompue"; else -> "Coach 100 % local, pas encore installé" }, chips = {
            Chip(if (!installed) "Non installé" else if (pr.aiEnabled) "Actif" else "En pause", if (installed && pr.aiEnabled) "check" else "pause", if (installed && pr.aiEnabled) p.ok else p.ink3)
            Chip("Sur le téléphone", "lock", p.type("program").c)
        }) {
            Muted("Le modèle tourne entièrement sur ton téléphone : tes objectifs, ta météo et la mémoire de l’IA ne quittent jamais l’appareil. Seul le fichier du modèle est téléchargé, une fois, depuis Hugging Face.", small = true)
        }
        if (installed) SetGroup { SetToggle("Coach IA activé", "Coupe-le sans supprimer le modèle", "sparkle", p.type("program").c, pr.aiEnabled, { pr.aiEnabled = it; ui.themeTick++ }) }
        PersonaGroup(ui)
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
                    if (LocalAi.progress >= 1f) PIndeterminate() else Bar(LocalAi.progress * 100.0, height = 12.dp, color = p.type("program").c)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PSpinner()
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
        if (installed) {
            var a by remember { mutableStateOf(pr.aiCreate) }; var b by remember { mutableStateOf(pr.aiRecommend) }; var c by remember { mutableStateOf(pr.aiTips) }
            var d by remember { mutableStateOf(pr.aiRecaps) }; var e by remember { mutableStateOf(pr.aiFriends) }
            val on = pr.aiEnabled
            SetGroup("Fonctions", note = if (!on) "Réactive le coach pour modifier ses fonctions." else null) {
                SetToggle("Créer un objectif en une phrase", "Dans l’onglet Objectifs : « Courir 3 fois par semaine », « Lire 12 livres »…", "wand", p.type("program").c, a, { a = it; pr.aiCreate = it }, enabled = on)
                SetToggle("Recommandations du moment", "« À valider aujourd’hui » trié selon l’heure et la météo, avec la raison du coach", "compass", p.type("objective").c, b, { b = it; pr.aiRecommend = it }, enabled = on)
                SetToggle("Conseils sur l’accueil", "Une carte « Conseil du coach », renouvelée au fil de la journée", "sun", p.type("period").c, c, { c = it; pr.aiTips = it }, enabled = on)
                SetToggle("Récaps personnalisés", "« Le mot du coach » à la fin de chaque récap", "star", p.type("monthly").c, d, { d = it; pr.aiRecaps = it }, enabled = on)
                SetToggle("Suggestions d’amis", "Classées selon tes objectifs, avec une raison personnalisée", "users", p.type("group").c, e, { e = it; pr.aiFriends = it }, enabled = on)
            }
            SetGroup("Modèle") {
                SetItem("Supprimer le modèle", "Libère ≈ ${v.approxMb} Mo. Tu pourras le réinstaller à tout moment.", "trash", danger = true, onClick = {
                    ui.confirm("Supprimer le modèle IA ?", "Le fichier du modèle (≈ ${v.approxMb} Mo) est effacé et l’IA désactivée. Tu pourras le réinstaller à tout moment.", "Supprimer") { LocalAi.uninstall(ctx); ui.toast("Modèle IA supprimé.", "trash") }
                })
            }
        }
        MemoryCard(ui)
    }
}

/** Personnalité du coach : prénom, ton (avec un exemple) et longueur des réponses. */
@Composable
private fun PersonaGroup(ui: AppState) {
    val p = LocalPal.current; val pr = ui.prefs
    var persona by remember { mutableStateOf(pr.aiPersona) }
    var name by remember { mutableStateOf(pr.aiCoachName) }
    var len by remember { mutableStateOf(pr.aiLength) }
    val cur = Personas.of(persona)
    SetGroup("Personnalité", note = "Le ton s’applique à la conversation, aux conseils, aux récaps et au mode simple.") {
        SetBlock { Field(name, { name = it.take(20); pr.aiCoachName = name.trim().ifEmpty { "Peps" } }, "Prénom du coach", max = 20, icon = "smile") }
        SetBlock {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Personas.ALL.forEach { ps ->
                    PRadioRow(ps.label, ps.desc, ps.key == persona, { persona = ps.key; pr.aiPersona = ps.key }, color = p.type("program").c,
                        leading = { CoachAvatar(40.dp, emoji = ps.emoji) })
                }
            }
        }
        SetBlock {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoachAvatar(32.dp, emoji = cur.emoji)
                Column(Modifier.chunk(p.surface2, p.edge, 18.dp, 3.dp, 2.dp).padding(12.dp)) {
                    Muted("${name.trim().ifEmpty { "Peps" }} · exemple", small = true)
                    Text(cur.sample, fontSize = 15.sp)
                }
            }
        }
        SetBlock {
            Text("Longueur des réponses", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Seg(listOf("short" to "Courtes", "normal" to "Normales", "long" to "Détaillées"), len, { len = it; pr.aiLength = it })
        }
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
    var askClear by remember { mutableStateOf(false) }
    var forget by remember { mutableStateOf<Long?>(null) }
    if (askClear) PDialog({ askClear = false }, "🧠", "Vider la mémoire de l’IA ?", "Les ${list.size} souvenirs du coach seront effacés définitivement.", "Vider",
        { askClear = false; AiMemory.clear(ctx); ui.toast("Mémoire de l’IA vidée.", "trash") }, danger = true)
    forget?.let { at -> PDialog({ forget = null }, "🫥", "Oublier ce souvenir ?", "Le coach ne s’en servira plus pour te conseiller.", "Oublier", { forget = null; AiMemory.remove(ctx, at) }, danger = true) }
    SetGroup("Mémoire de l’IA", note = "Le coach retient tes demandes et tes réactions pour mieux te conseiller. Cette mémoire reste sur ton téléphone.") {
        SetToggle("Mémoriser", if (list.isEmpty()) "La mémoire est vide" else pl(list.size, "souvenir", "souvenirs"), "book", p.type("measure").c, on, { on = it; pr.aiMemory = it })
        if (on) SetBlock {
            var fact by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(fact, { fact = it }, "", Modifier.weight(1f), placeholder = "Apprends-lui quelque chose sur toi…", max = 200, icon = "sparkle")
                PButton("Retenir", { if (AiMemory.addFact(ctx, fact)) { fact = ""; ui.toast("Le coach s’en souviendra.", "sparkle") } else ui.toast("Déjà retenu, ou trop court.", "x") }, small = true, color = p.type("program").c, enabled = fact.trim().length >= 4)
            }
            Muted("Exemples : « Je travaille de nuit », « Je cours le dimanche matin », « Je préfère les conseils courts ».", small = true)
        }
        if (list.isNotEmpty()) SetBlock {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(if (open) "Masquer" else "Lire la mémoire", { open = !open }, icon = "book", small = true)
                PButton("Vider la mémoire", { askClear = true }, icon = "trash", kind = "danger", small = true)
            }
            if (open) Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.reversed().forEach { m ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { Text(m.text, fontSize = 14.sp); Muted("${ago(m.at)} · ${MEM_KIND[m.kind] ?: m.kind}", small = true) }
                        IconBtn("x", "Oublier ce souvenir", { forget = m.at }, plain = true)
                    }
                }
            }
        }
    }
}

private val MEM_KIND = mapOf("fact" to "à retenir", "goal" to "création d’objectif", "tip" to "conseil", "reco" to "recommandation", "chat" to "conversation", "system" to "système")
