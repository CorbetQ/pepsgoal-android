package fr.pepsgoal.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ai.ChatMsg
import fr.pepsgoal.app.ai.Coach
import fr.pepsgoal.app.ai.CoachChat
import fr.pepsgoal.app.ai.LocalAi
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import kotlinx.coroutines.launch

/** Conversation avec le coach IA (100 % locale) : questions libres, plans, objectifs proposés prêts à créer. */
@Composable
fun CoachChatScreen(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current
    BackHandler { ui.back() }
    CoachChat.rev
    val msgs = remember(CoachChat.rev) { CoachChat.all(ctx) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val state = rememberLazyListState()
    LaunchedEffect(msgs.size, busy) { if (msgs.isNotEmpty()) state.animateScrollToItem(msgs.size + 1) }
    val ready = LocalAi.on(ctx)
    val pulse = thinkingAlpha()
    val send = { q: String ->
        val t = q.trim()
        if (t.isNotEmpty() && !busy) { busy = true; input = ""; scope.launch { CoachChat.send(ctx, t); busy = false } }
    }
    Box(Modifier.fillMaxSize()) {
        PageBackdrop(p.type("program").c, p.type("measure").c)
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBtn("chevronleft", "Retour", { ui.back() }, size = 44.dp)
                IconTile("sparkle", p.type("program").c, p.type("measure").c, 40.dp, 13.dp)
                Column(Modifier.weight(1f)) { Text("Ton coach", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp); Muted(if (busy) "réfléchit…" else "100 % local · sur ton téléphone", small = true) }
                if (msgs.isNotEmpty()) IconBtn("trash", "Effacer la conversation", { ui.confirm("Effacer la conversation ?", "Les messages échangés avec le coach seront supprimés de ce téléphone.", "Effacer") { CoachChat.clear(ctx) } }, plain = true, size = 40.dp)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!ready) item("off") { Note("Le coach IA n’est pas installé ou est désactivé. Active-le dans Paramètres › Assistant IA.", "sparkle") }
                if (msgs.isEmpty()) item("hello") {
                    Glass(ai = true) {
                        Text("Salut ! Pose-moi une question sur tes objectifs, demande un plan ou un conseil.", fontWeight = FontWeight.Bold)
                        FlowRowCompat(Modifier.padding(top = 10.dp), gap = 6.dp) {
                            listOf("Pourquoi je rate certains jours ?", "Fais-moi un plan pour courir 10 km", "Quel objectif ajouter cette semaine ?", "Comment garder ma série ?").forEach { q -> FChip(q, false, { send(q) }) }
                        }
                    }
                }
                msgs.forEach { m -> item("m${m.at}") { Bubble(ui, m) } }
                if (busy) item("busy") { Muted("Le coach écrit…", Modifier.alpha(pulse).padding(start = 6.dp)) }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp).clip(RoundedCornerShape(26.dp)).background(p.glassStrong).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(input, { input = it }, "", Modifier.weight(1f), placeholder = "Écris au coach…", max = 600, singleLine = false, icon = "sparkle")
                IconBtn("send", "Envoyer", { send(input) }, size = 46.dp, tint = p.flame)
            }
        }
    }
}

@Composable
private fun Bubble(ui: AppState, m: ChatMsg) {
    val p = LocalPal.current; val mine = m.role == "user"
    val shape = RoundedCornerShape(20.dp, 20.dp, if (mine) 6.dp else 20.dp, if (mine) 20.dp else 6.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(Modifier.widthIn(max = 310.dp).clip(shape).background(if (mine) Brush.linearGradient(listOf(p.flame2.copy(alpha = .4f), p.pink.copy(alpha = .34f))) else Brush.linearGradient(listOf(p.glassStrong, p.glassStrong))).padding(12.dp)) {
            Text(m.text, fontSize = 15.sp)
            m.goal?.let { line ->
                val d = Coach.parseLine(line, m.text)
                if (d != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(p.type(d.type).c.copy(alpha = .18f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Emoji(d.icon, 28.dp)
                        Column(Modifier.weight(1f, fill = false)) { Text(d.title, fontWeight = FontWeight.ExtraBold); TypeChip(d.type) }
                        PButton("Créer", { ui.open(Sheet.GoalForm(preType = d.type, preTitle = d.title, preIcon = d.icon, preFields = d.fields)) }, icon = "plus", kind = "primary", small = true)
                    }
                }
            }
        }
    }
}
