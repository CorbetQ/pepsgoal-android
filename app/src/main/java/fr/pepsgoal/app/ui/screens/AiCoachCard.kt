package fr.pepsgoal.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ai.AiMemory
import fr.pepsgoal.app.ai.Coach
import fr.pepsgoal.app.ai.LocalAi
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import kotlinx.coroutines.launch

/**
 * Carte « Ton coach » de l'accueil : conseil du moment, objectifs recommandés selon l'heure et la météo, et création
 * d'un objectif en une phrase. Tout est calculé sur le téléphone par l'IA locale.
 */
@Composable
fun AiCoachCard(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current; val pr = ui.prefs
    ui.themeTick
    LaunchedEffect(Unit) { LocalAi.refresh(ctx) }
    if (!LocalAi.installed(ctx)) {
        // Invitation discrète tant que l'IA n'est pas installée (masquable)
        if (pr.aiPromoHidden || LocalAi.status == "downloading") return
        Glass {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile("sparkle", p.type("program").c, p.type("measure").c, 42.dp, 14.dp)
                Column(Modifier.weight(1f)) { Text("Nouveau : ton coach IA", fontWeight = FontWeight.ExtraBold); Muted("100 % local, sur ton téléphone : conseils, recommandations et objectifs en une phrase.", small = true) }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton("Découvrir", { ui.push(Screen.Settings("ai")) }, icon = "sparkle", kind = "primary", small = true)
                PButton("Plus tard", { pr.aiPromoHidden = true; ui.themeTick++ }, kind = "ghost", small = true)
            }
        }
        return
    }
    if (!pr.aiEnabled || !(pr.aiTips || pr.aiRecommend || pr.aiCreate)) return
    val scope = rememberCoroutineScope()
    var tip by remember { mutableStateOf(pr.aiTip.takeIf { it.isNotBlank() }) }
    var tipBusy by remember { mutableStateOf(false) }
    var picks by remember { mutableStateOf<List<Coach.Pick>?>(null) }
    var ask by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    ui.watch()
    // Recommandations recalculées quand la liste des objectifs restants ou l'heure change (pas à chaque synchronisation)
    val leftKey = ui.pg.me()?.let { u -> ui.pg.todayPlan(u.id).due.filter { !it.done }.joinToString { it.g.id } } + "|" + java.time.LocalTime.now().hour
    LaunchedEffect(Unit) { if (pr.aiTips) { tipBusy = tip == null; tip = Coach.tip(ctx); tipBusy = false } }
    LaunchedEffect(leftKey, pr.aiRecommend) { if (pr.aiRecommend) picks = Coach.recommend(ctx) }
    val inf = rememberInfiniteTransition(label = "think")
    val pulse by inf.animateFloat(.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")

    Glass {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile("sparkle", p.type("program").c, p.type("measure").c, 36.dp, 12.dp)
            Text("Ton coach", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            if (LocalAi.busy) Muted("réfléchit…", Modifier.alpha(pulse), small = true)
            if (pr.aiTips) IconBtn("repeat", "Nouveau conseil", { scope.launch { tipBusy = true; tip = Coach.tip(ctx, force = true); tipBusy = false } }, plain = true, size = 36.dp)
        }
        // Conseil du moment
        if (pr.aiTips) AnimatedContent(tip to tipBusy, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) }, label = "tip") { (t, b) ->
            Column(Modifier.padding(top = 8.dp)) {
                if (b || t == null) Muted("Le coach prépare ton conseil…", Modifier.alpha(pulse))
                else {
                    Text(t, fontSize = 15.sp)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Muted("Utile ?", small = true)
                        IconBtn("heart", "Ce conseil m’aide", { AiMemory.add(ctx, "tip", "A aimé ce conseil : « ${t.take(120)} »"); ui.toast("Le coach s’en souviendra.", "sparkle") }, plain = true, size = 34.dp)
                        IconBtn("x", "Pas pour moi", { AiMemory.add(ctx, "tip", "N’aime pas ce genre de conseil : « ${t.take(120)} »"); scope.launch { tipBusy = true; tip = Coach.tip(ctx, force = true); tipBusy = false } }, plain = true, size = 34.dp)
                    }
                }
            }
        }
        // Recommandations du moment
        val list = picks
        if (pr.aiRecommend && !list.isNullOrEmpty()) {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp)) { Eyebrow("À faire maintenant") }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEach { pk ->
                    val tc = p.type(pk.goal.type)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(tc.c.copy(alpha = .14f)).clickable { ui.open(Sheet.Goal(pk.goal.id)) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoalIcon(pk.goal, 36.dp)
                        Column(Modifier.weight(1f)) { Text(pk.goal.title, fontWeight = FontWeight.ExtraBold, maxLines = 1); Muted(pk.reason, small = true, maxLines = 2) }
                        MiniBtn("Valider", tc.c) { AiMemory.add(ctx, "reco", "A suivi la recommandation « ${pk.goal.title} » (${pk.reason.take(60)})."); doValidate(ui, pk.goal.id) }
                    }
                }
            }
        }
        // Créer un objectif en une phrase
        if (pr.aiCreate) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(ask, { ask = it }, "Crée un objectif en une phrase", Modifier.weight(1f), placeholder = "Ex. Boire 2 litres d’eau chaque jour", max = 200)
                IconBtn("sparkle", "Créer avec l’IA", {
                    val q = ask.trim()
                    if (q.length >= 4 && !creating) {
                        creating = true
                        scope.launch {
                            val d = Coach.createGoal(ctx, q)
                            creating = false; ask = ""
                            ui.open(Sheet.GoalForm(preType = d.type, preTitle = d.title, preIcon = d.icon, preFields = d.fields))
                            ui.toast(if (d.byAi) "Proposition du coach : vérifie puis enregistre." else "Brouillon prêt : vérifie puis enregistre.", "sparkle")
                        }
                    }
                }, size = 48.dp, tint = p.flame)
            }
            if (creating) Muted("Le coach prépare ton objectif…", Modifier.padding(top = 4.dp).alpha(pulse), small = true)
        }
    }
}
