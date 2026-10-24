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

/** Avatar du coach : emoji de sa personnalité dans un disque dégradé, anneau qui tourne pendant qu'il réfléchit. */
@Composable
fun CoachAvatar(size: Dp, thinking: Boolean = false, emoji: String? = null) {
    val p = LocalPal.current; val ctx = LocalContext.current
    val e = emoji ?: Personas.current(ctx).emoji
    val spin = if (thinking && !p.motion) rememberInfiniteTransition(label = "coach").animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "spin").value else 0f
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (thinking) Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }) {
            drawCircle(Brush.sweepGradient(listOf(p.flame2, p.pink, p.type("program").c, p.flame2)), radius = this.size.minDimension / 2 - 1.5.dp.toPx(), style = Stroke(3.dp.toPx()))
        }
        Box(Modifier.size(size - 8.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.type("program").c.copy(alpha = .35f), p.type("measure").c.copy(alpha = .3f)))), contentAlignment = Alignment.Center) {
            Emoji(e, size * .5f)
        }
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
    Box(Modifier.fillMaxSize()) {
        PageBackdrop(p.type("program").c, p.type("measure").c)
        Column(Modifier.fillMaxSize().imePadding()) {
            // En-tête : avatar, prénom et état du coach
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBtn("chevronleft", "Retour", { ui.back() }, size = 44.dp)
                CoachAvatar(46.dp, thinking = busy)
                Column(Modifier.weight(1f)) {
                    Text(name, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Muted(when { busy && partial == null -> "réfléchit…"; busy -> "écrit…"; ready -> "${persona.label} · 100 % sur ton téléphone"; else -> "${persona.label} · mode simple" }, small = true)
                }
                IconBtn("gear", "Réglages du coach", { ui.push(Screen.Settings("ai")) }, plain = true, size = 40.dp)
                if (msgs.isNotEmpty()) IconBtn("trash", "Effacer la conversation", { ui.confirm("Effacer la conversation ?", "Les messages échangés avec $name seront supprimés de ce téléphone. Sa mémoire est gardée.", "Effacer") { CoachChat.clear(ctx) } }, plain = true, size = 40.dp)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!ready) item("off") {
                    Glass(onClick = { ui.push(Screen.Settings("ai")) }, pad = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Pi("sparkle", size = 20.dp, tint = p.flameInk)
                            Column(Modifier.weight(1f)) { Text("Mode simple", fontWeight = FontWeight.ExtraBold); Muted(if (LocalAi.installed(ctx)) "Le coach IA est en pause : réactive-le pour des réponses complètes." else "Installe le coach IA pour des réponses complètes et personnalisées.", small = true) }
                            Text("›", color = p.ink3, fontSize = 22.sp)
                        }
                    }
                }
                if (msgs.isEmpty()) item("hello") { Welcome(ui, persona.hello.replace("{name}", firstName(ctx) ?: "toi"), send) }
                msgs.forEachIndexed { i, m -> item("m${m.at}") { Bubble(ui, m, last = i == msgs.lastIndex && !busy) } }
                if (busy) item("busy") { Writing(partial) }
            }
            // Questions proposées selon le moment
            if (!busy && msgs.isNotEmpty() && sugg.isNotEmpty()) Box(Modifier.padding(horizontal = 12.dp)) {
                ChipScroller(bleed = 12.dp) { sugg.forEach { q -> FChip(q, false, { send(q) }) } }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp).clip(RoundedCornerShape(26.dp)).background(p.glassStrong).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(input, { input = it }, "", Modifier.weight(1f), placeholder = "Écris à $name…", max = 600, singleLine = false, icon = "sparkle")
                if (busy) IconBtn("pause", "Arrêter la réponse", { CoachChat.stop() }, size = 46.dp, tint = p.danger)
                else IconBtn("send", "Envoyer", { send(input) }, size = 46.dp, tint = p.flameInk)
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
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Pi(ic, size = 18.dp, tint = c) }
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
    val inf = rememberInfiniteTransition(label = "dots")
    val ph by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "ph")
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CoachAvatar(30.dp, thinking = true)
        Column(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)).background(p.glassStrong).padding(12.dp)) {
            if (partial.isNullOrBlank()) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in 0..2) { val a = ((kotlin.math.sin((ph - i * .18f) * 2 * Math.PI) + 1) / 2).toFloat(); Box(Modifier.size(8.dp).graphicsLayer { translationY = -a * 4.dp.toPx() }.alpha(.35f + .65f * a).clip(CircleShape).background(p.ink2)) }
            } else Text(rich(partial + if (ph < .5f) " ▍" else "  "), fontSize = 15.sp)
        }
    }
}

@Composable
private fun Bubble(ui: AppState, m: ChatMsg, last: Boolean) {
    val p = LocalPal.current; val ctx = LocalContext.current; val mine = m.role == "user"; val pg = ui.pg
    val clip = LocalClipboardManager.current
    val shape = RoundedCornerShape(20.dp, 20.dp, if (mine) 6.dp else 20.dp, if (mine) 20.dp else 6.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!mine) { CoachAvatar(30.dp); Spacer(Modifier.width(8.dp)) }
        Column(Modifier.widthIn(max = 300.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.clip(shape).background(if (mine) Brush.linearGradient(listOf(p.flame2.copy(alpha = .4f), p.pink.copy(alpha = .34f))) else Brush.linearGradient(listOf(p.glassStrong, p.glassStrong))).padding(12.dp)) {
                Text(if (mine) AnnotatedString(m.text) else rich(m.text), fontSize = 15.sp)
                if (m.simple) Muted("Mode simple", Modifier.padding(top = 4.dp), small = true)
            }
            if (!mine) {
            // Objectif proposé, prêt à créer
            m.goal?.let { line ->
                val d = if (line.startsWith("?")) Coach.rules(line.drop(1)) else Coach.parseLine(line, m.text)
                if (d != null) Row(Modifier.clip(RoundedCornerShape(16.dp)).background(p.type(d.type).c.copy(alpha = .18f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Emoji(d.icon, 28.dp)
                    Column(Modifier.weight(1f, fill = false)) { Text(d.title, fontWeight = FontWeight.ExtraBold); TypeChip(d.type) }
                    PButton("Créer", { ui.open(Sheet.GoalForm(preType = d.type, preTitle = d.title, preIcon = d.icon, preFields = d.fields)) }, icon = "plus", kind = "primary", small = true)
                }
            }
            // Objectifs que tu dis avoir faits : validation en un geste
            m.acts.mapNotNull { a -> a.removePrefix("validate:").takeIf { a.startsWith("validate:") }?.let { pg.goalById(it) } }.forEach { g ->
                val done = remember(ui.rev) { pg.todayPlan().due.any { it.g.id == g.id && it.done } }
                if (done) Chip("${g.icon} ${g.title} · validé", "check", p.ok)
                else PButton("Valider ${g.icon} ${g.title}", { doValidate(ui, g.id) }, icon = "check", kind = "primary", small = true)
            }
            // Ce que le coach a retenu
            m.fact?.let { f ->
                Row(Modifier.clip(RoundedCornerShape(14.dp)).background(p.ink.copy(alpha = .06f)).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🧠", fontSize = 14.sp); Muted("Retenu : $f", Modifier.weight(1f, fill = false), small = true, maxLines = 2)
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
