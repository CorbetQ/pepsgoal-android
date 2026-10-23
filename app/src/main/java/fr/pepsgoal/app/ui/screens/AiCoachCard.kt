package fr.pepsgoal.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

/** Opacité qui pulse doucement pendant que le coach réfléchit. */
@Composable
fun thinkingAlpha(): Float {
    val inf = rememberInfiniteTransition(label = "think")
    val a by inf.animateFloat(.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    return a
}

/** Invitation à installer le coach (accueil), tant que l'IA n'est pas installée. */
@Composable
fun AiPromoCard(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current; val pr = ui.prefs
    ui.themeTick
    LaunchedEffect(Unit) { LocalAi.refresh(ctx) }
    if (LocalAi.installed(ctx) || pr.aiPromoHidden || LocalAi.status == "downloading") return
    Glass(ai = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile("sparkle", p.type("program").c, p.type("measure").c, 42.dp, 14.dp)
            Column(Modifier.weight(1f)) { Text("Nouveau : ton coach IA", fontWeight = FontWeight.ExtraBold); Muted("100 % local, sur ton téléphone : conseils, recommandations et objectifs en une phrase.", small = true) }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton("Découvrir", { ui.push(Screen.Settings("ai")) }, icon = "sparkle", kind = "primary", small = true)
            PButton("Plus tard", { pr.aiPromoHidden = true; ui.themeTick++ }, kind = "ghost", small = true)
        }
    }
}

/** Carte « Conseil du coach » (accueil) : conseil du moment, renouvelable, avec « utile » / « pas pour moi ». */
@Composable
fun AiTipCard(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current; val pr = ui.prefs
    ui.themeTick
    if (!LocalAi.on(ctx, pr.aiTips)) return
    val scope = rememberCoroutineScope()
    var tip by remember { mutableStateOf(pr.aiTip.takeIf { it.isNotBlank() }) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { busy = tip == null; tip = Coach.tip(ctx); busy = false }
    val pulse = thinkingAlpha()
    val renew = { scope.launch { busy = true; tip = Coach.tip(ctx, force = true); busy = false }; Unit }
    Glass(ai = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CoachAvatar(40.dp, thinking = busy)
            Column(Modifier.weight(1f)) {
                Text("Le conseil de ${fr.pepsgoal.app.ai.Personas.coachName(ctx)}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Muted(fr.pepsgoal.app.ai.Personas.current(ctx).label, small = true)
            }
            if (tip != null && !busy) IconBtn("music", "Écouter le conseil", { fr.pepsgoal.app.sys.Speaker.say(ctx, tip!!) }, plain = true, size = 36.dp)
            IconBtn("comment", "Discuter avec le coach", { ui.push(Screen.CoachChat) }, plain = true, size = 36.dp)
            IconBtn("repeat", "Nouveau conseil", renew, plain = true, size = 36.dp)
        }
        AnimatedContent(tip to busy, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) }, label = "tip") { (t, b) ->
            Column(Modifier.padding(top = 8.dp)) {
                if (b || t == null) Muted("Le coach prépare ton conseil…", Modifier.alpha(pulse))
                else {
                    Text(rich(t), fontSize = 15.sp)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Muted("Utile ?", small = true)
                        IconBtn("heart", "Ce conseil m’aide", { AiMemory.add(ctx, "tip", "A aimé ce conseil : « ${t.take(120)} »"); ui.toast("Le coach s’en souviendra.", "sparkle") }, plain = true, size = 34.dp)
                        IconBtn("x", "Pas pour moi", { AiMemory.add(ctx, "tip", "N’aime pas ce genre de conseil : « ${t.take(120)} »"); renew() }, plain = true, size = 34.dp)
                    }
                }
            }
        }
    }
}

/** Carte « Créer avec l'IA » (onglet Objectifs) : un objectif complet à partir d'une phrase. */
@Composable
fun AiCreateCard(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current; val pr = ui.prefs
    ui.themeTick
    if (!LocalAi.on(ctx, pr.aiCreate)) return
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val pulse = thinkingAlpha()
    Glass(ai = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CoachAvatar(40.dp, thinking = creating)
            Column(Modifier.weight(1f)) { Text("Créer avec ${fr.pepsgoal.app.ai.Personas.coachName(ctx)}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp); Muted("Décris ton objectif, le coach remplit le formulaire.", small = true) }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(ask, { ask = it }, "Ton objectif en une phrase", Modifier.weight(1f), placeholder = "Ex. Courir 3 fois par semaine", max = 200)
            IconBtn("sparkle", "Créer avec l’IA", {
                val q = ask.trim()
                if (q.length >= 4 && !creating) {
                    creating = true
                    scope.launch {
                        val d = Coach.createGoal(ctx, q)
                        creating = false; ask = ""
                        ui.open(Sheet.GoalForm(preType = d.type, preTitle = d.title, preIcon = d.icon, preFields = d.fields))
                        Unit
                    }
                }
            }, size = 48.dp, tint = p.flame)
        }
        if (creating) Muted("Le coach prépare ton objectif…", Modifier.padding(top = 4.dp).alpha(pulse), small = true)
        FlowRowCompat(Modifier.padding(top = 8.dp), gap = 6.dp) {
            listOf("Lire 20 pages par jour", "Arrêter le sucre", "Méditer 10 minutes").forEach { ex -> FChip(ex, false, { ask = ex }) }
        }
    }
}
