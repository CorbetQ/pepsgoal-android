package fr.pepsgoal.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ai.*
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.step

/** Avatar du coach : médaillon néon « program » à marche, emoji de sa personnalité ; il saute doucement quand il réfléchit. */
@Composable
fun CoachAvatar(size: Dp, modifier: Modifier = Modifier, thinking: Boolean = false, emoji: String? = null) {
    val p = LocalPal.current; val ctx = LocalContext.current
    val e = emoji ?: Personas.current(ctx).emoji
    val c = vivid(p.type("program").c)
    val ph = slowPhaseState(900, 15, running = thinking)
    Box(modifier.size(size).graphicsLayer { if (thinking && !p.motion) translationY = -kotlin.math.abs(kotlin.math.sin(ph.value * Math.PI.toFloat())) * 3.dp.toPx() }
        .chunk(c, c.step(), size / 2, 3.dp, 0.dp), contentAlignment = Alignment.Center) {
        Emoji(e, size * .5f)
    }
}

/** Conversation avec le coach IA (100 % locale) : réponses au fil de l'écriture, actions proposées, mode simple sans modèle. */
@Composable
fun CoachChatScreen(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current
    BackHandler { ui.back() }
    CoachChat.rev
    val msgs = remember(CoachChat.rev) { CoachChat.all(ctx) }
    var input by remember { mutableStateOf("") }
    val busy = CoachChat.busy; val partial = CoachChat.partial
    val state = rememberLazyListState()
    val persona = Personas.current(ctx); val name = Personas.coachName(ctx)
    val ready = LocalAi.on(ctx)
    // Défile jusqu'en bas à chaque nouveau message et pendant l'écriture
    LaunchedEffect(msgs.size, busy, partial?.length?.div(80)) { val n = state.layoutInfo.totalItemsCount; if (n > 0) state.animateScrollToItem(n - 1) }
    val send = { q: String -> if (q.isNotBlank() && !busy) { CoachChat.send(ctx, q); input = "" } }
    val sugg = remember(CoachChat.rev, ui.rev) { CoachChat.suggestions(ctx) }
    TrackScroll(state) { Fx.pageScroll = it }
    // Pas de fond propre : la scène de l'application reste dessous
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            // En-tête : barre compacte (retour, médaillon du coach, prénom et état, actions)
            CompactBar(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                RoundGlass("Retour", { ui.back() }) { Pi("chevronleft", size = 20.dp, tint = p.ink) }
                CoachAvatar(36.dp, thinking = busy)
                Column(Modifier.weight(1f)) {
                    Text(name, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                    // Sous-titre court qui rétrécit pour tenir sur une ligne (jamais coupé en plein mot)
                    FitText(when { busy && partial == null -> "réfléchit…"; busy -> "écrit…"; ready -> "${persona.label} · sur ton téléphone"; else -> "${persona.label} · mode simple" }, size = 13, min = 9, color = p.ink2)
                }
                RoundGlass("Réglages du coach", { ui.push(Screen.Settings("ai")) }) { Pi("gear", size = 20.dp, tint = p.ink) }
                if (msgs.isNotEmpty()) RoundGlass("Effacer la conversation", { ui.confirm("Effacer la conversation ?", "Les messages échangés avec $name seront supprimés de ce téléphone. Sa mémoire est gardée.", "Effacer") { CoachChat.clear(ctx) } }) { Pi("trash", size = 20.dp, tint = p.danger) }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!ready) item("off") {
                    Glass(onClick = { ui.push(Screen.Settings("ai")) }, pad = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Pi("sparkle", size = 20.dp, tint = p.flameInk)
                            Column(Modifier.weight(1f)) { Text("Mode simple", fontWeight = FontWeight.ExtraBold); Muted(if (LocalAi.installed(ctx)) "Le coach IA est en pause : réactive-le pour des réponses complètes." else "Installe le coach IA pour des réponses complètes et personnalisées.", small = true) }
                            Pi("chevronright", size = 20.dp, tint = p.ink3)
                        }
                    }
                }
                if (msgs.isEmpty()) item("hello") { Welcome(ui, persona.hello.replace("{name}", firstName(ctx) ?: "toi"), send) }
                msgs.forEachIndexed { i, m -> item("m${m.at}", contentType = "msg") { Bubble(ui, m, last = i == msgs.lastIndex && !busy) } }
                if (busy) item("busy") { Writing(partial) }
            }
            // Questions proposées selon le moment
            if (!busy && msgs.isNotEmpty() && sugg.isNotEmpty()) Box(Modifier.padding(horizontal = 12.dp)) {
                ChipScroller(bleed = 12.dp) { sugg.forEach { q -> FChip(q, false, { send(q) }) } }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PTextField(input, { input = it }, "", Modifier.weight(1f), "Écris à $name…", max = 600, singleLine = false, minLines = 1, maxLines = 5, capsule = true, clear = false, emoji = "✨")
                if (busy) RoundGlass("Arrêter la réponse", { CoachChat.stop() }) { Pi("pause", size = 20.dp, tint = p.danger) }
                else SendBtn("Envoyer", input.isNotBlank()) { send(input) }
            }
        }
    }
}

/** Accueil d'une conversation vide : salut du coach et raccourcis. */
@Composable
private fun Welcome(ui: AppState, hello: String, send: (String) -> Unit) {
    val p = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Glass(ai = true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoachAvatar(56.dp)
                Text(hello, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        val acts = listOf(
            Triple("Mon briefing du jour", "sun", p.type("period").c), Triple("Par quoi je commence ?", "target", p.type("objective").c),
            Triple("Fais le point sur ma semaine", "trend", p.type("project").c), Triple("Propose-moi un nouvel objectif", "plus", p.type("routine").c),
            Triple("Comment garder ma série ?", "flameo", p.flame), Triple("Je manque de motivation", "heart", p.pink),
        )
        acts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (q, ic, c) ->
                    Glass(Modifier.weight(1f), pad = 12.dp, onClick = { send(q) }) {
                        IconTile(ic, c, c, 34.dp, 11.dp)
                        Text(q, Modifier.padding(top = 8.dp), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

/** Réponse en cours : points qui s'animent, puis le texte au fil de l'écriture avec un curseur. */
@Composable
private fun Writing(partial: String?) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CoachAvatar(30.dp, thinking = true)
        Column(Modifier.widthIn(max = 300.dp).chunk(p.surface, p.edge, 22.dp, 3.dp, 2.dp).padding(12.dp)) {
            if (partial.isNullOrBlank()) PSpinner(8.dp, label = "Le coach écrit…")
            else Text(rich("$partial ▍"), fontSize = 15.sp)
        }
    }
}

@Composable
private fun Bubble(ui: AppState, m: ChatMsg, last: Boolean) {
    val p = LocalPal.current; val ctx = LocalContext.current; val mine = m.role == "user"; val pg = ui.pg
    val clip = LocalClipboardManager.current
    val shape = RoundedCornerShape(20.dp, 20.dp, if (mine) 6.dp else 20.dp, if (mine) 20.dp else 6.dp)
    // Médaillon du coach aligné sur le haut de sa bulle (et non sur la rangée d'actions dessous)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!mine) { CoachAvatar(30.dp, Modifier.padding(top = 4.dp)); Spacer(Modifier.width(8.dp)) }
        Column(Modifier.widthIn(max = 300.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // À moi : aplat de la flamme et encre prune ; coach : bulle reçue (surface à marche) avec pastille ✨
            Box {
                Column(Modifier.then(if (mine) Modifier.chunk(p.flameBrush(), p.flameStep, 22.dp, 3.dp, 0.dp) else Modifier.chunk(p.surface, p.edge, 22.dp, 3.dp, 2.dp)).clip(shape).padding(12.dp)) {
                    Text(if (mine) AnnotatedString(m.text) else rich(m.text), fontSize = 15.sp, color = if (mine) fr.pepsgoal.app.ui.theme.PLUM else p.ink)
                }
                if (!mine) Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(22.dp).background(vivid(p.type("program").c), CircleShape), contentAlignment = Alignment.Center) { Emoji("✨", 14.dp) }
            }
            if (!mine) {
            // Objectif proposé, prêt à créer
            m.goal?.let { line ->
                val d = if (line.startsWith("?")) Coach.rules(line.drop(1)) else Coach.parseLine(line, m.text)
                if (d != null) Row(Modifier.chunk(p.selTint(p.type(d.type).c), p.type(d.type).c, 22.dp, 3.dp, 2.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Emoji(d.icon, 28.dp)
                    Column(Modifier.weight(1f, fill = false)) { Text(d.title, fontWeight = FontWeight.ExtraBold); TypeChip(d.type) }
                    PButton("Créer", { ui.open(Sheet.GoalForm(preType = d.type, preTitle = d.title, preIcon = d.icon, preFields = d.fields)) }, icon = "plus", kind = "type", color = p.type(d.type).c, small = true)
                }
            }
            // Objectifs que tu dis avoir faits : validation en un geste
            m.acts.mapNotNull { a -> a.removePrefix("validate:").takeIf { a.startsWith("validate:") }?.let { pg.goalById(it) } }.forEach { g ->
                val done = remember(ui.rev) { pg.todayPlan().due.any { it.g.id == g.id && it.done } }
                if (done) Chip("${g.icon} ${g.title} · validé", "check", p.ok)
                else PButton("Valider ${g.icon} ${g.title}", { doValidate(ui, g.id) }, icon = "check", kind = "type", color = p.type(g.type).c, small = true)
            }
            // Ce que le coach a retenu
            m.fact?.let { f ->
                Row(Modifier.clip(RoundedCornerShape(17.dp)).background(p.surface).border(2.dp, p.edge, RoundedCornerShape(17.dp)).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Emoji("🧠", 16.dp); Muted("Retenu : $f", Modifier.weight(1f, fill = false), small = true, maxLines = 2)
                    IconBtn("x", "Oublier", { AiMemory.all(ctx).lastOrNull { it.kind == "fact" && it.text.equals(f, true) }?.let { AiMemory.remove(ctx, it.at) }; ui.toast("Oublié.", "trash") }, plain = true, size = 30.dp)
                }
            }
            // Copier, écouter, autre réponse
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconBtn("copy", "Copier", { clip.setText(AnnotatedString(m.text.replace("**", ""))); ui.toast("Copié.", "copy") }, plain = true, size = 32.dp)
                IconBtn("music", "Écouter", { fr.pepsgoal.app.sys.Speaker.say(ctx, m.text.replace("**", "")) }, plain = true, size = 32.dp)
                if (last) IconBtn("repeat", "Autre réponse", { CoachChat.regenerate(ctx) }, plain = true, size = 32.dp)
            }
            }
        }
    }
}
