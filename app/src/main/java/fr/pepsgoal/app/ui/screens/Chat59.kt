package fr.pepsgoal.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.sys.Scheduled
import fr.pepsgoal.app.sys.Transcriber
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.*
import fr.pepsgoal.core.model.Message
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/* ------------------------------------------------------------------ Stickers PepsGoal */

/** Sticker : la flamme de PepsGoal animée, un emoji, un mot et des petites particules. */
class StickerDef(val id: String, val label: String, val emoji: String, val text: String, val anim: String, val deco: String)

val STICKERS = listOf(
    StickerDef("hi", "Salut", "👋", "Salut !", "wave", "✨"),
    StickerDef("bravo", "Bravo", "👏", "Bravo !", "bounce", "confetti"),
    StickerDef("go", "Allez", "💪", "Allez !", "pulse", "⚡"),
    StickerDef("love", "Love", "❤️", "Love", "float", "❤️"),
    StickerDef("lol", "MDR", "😂", "MDR", "shake", "😂"),
    StickerDef("sleep", "Dodo", "😴", "Zzz…", "sway", "💤"),
    StickerDef("cent", "100", "💯", "100 %", "bounce", "⭐"),
    StickerDef("fire", "En feu", "🔥", "En feu !", "flicker", "embers"),
    StickerDef("thanks", "Merci", "🙏", "Merci !", "bounce", "✨"),
    StickerDef("streak", "Série", "🏆", "Série !", "pulse", "⭐"),
    StickerDef("coffee", "Café", "☕", "Pause café", "sway", "☁️"),
    StickerDef("night", "Bonne nuit", "🌙", "Bonne nuit", "float", "⭐"),
)

/** Sticker animé (ou figé dans le sélecteur et avec « réduire les animations »). */
@Composable
fun StickerView(id: String, fallback: String, sz: Dp, still: Boolean = false) {
    val p = LocalPal.current
    val def = STICKERS.firstOrNull { it.id == id } ?: run { Emoji(fallback.ifEmpty { "🔥" }, sz * .7f); return }
    val anim = !still && !p.motion
    val t by if (anim) rememberInfiniteTransition(label = "stk").animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "stkT") else remember { mutableFloatStateOf(.25f) }
    val s = sin(t * 2 * PI.toFloat())
    Box(Modifier.size(sz), contentAlignment = Alignment.Center) {
        // Disque plein derrière la flamme (aucun halo)
        Canvas(Modifier.fillMaxSize()) { drawCircle(p.selTint(p.flame), size.width * .4f, Offset(size.width / 2, size.height * .44f)) }
        // Particules décoratives
        val r = remember(id) { Random(id.hashCode()) }; val parts = remember(id) { List(6) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
        parts.forEachIndexed { i, f ->
            val k = (t + f[2]) % 1f
            val x = sz * (.12f + f[0] * .76f); val y = sz * (.8f - k * .7f)
            Box(Modifier.offset(x = x - sz * .06f, y = y - sz * .06f).graphicsLayer { alpha = if (anim) (1f - k) else .7f }) {
                when (def.deco) {
                    "confetti" -> Box(Modifier.size(sz * .05f, sz * .09f).graphicsLayer { rotationZ = k * 360f + i * 40 }.background(listOf(p.flame, p.pink, p.type("measure").c, p.type("routine").c)[i % 4]))
                    "embers" -> Box(Modifier.size(sz * .05f).clip(CircleShape).background(fr.pepsgoal.app.ui.theme.BRAISE[i % 3]))
                    else -> Emoji(def.deco, sz * .12f)
                }
            }
        }
        // Flamme animée
        Box(Modifier.offset(y = -sz * .06f).graphicsLayer {
            when (def.anim) {
                "wave" -> { rotationZ = s * 10f }
                "bounce" -> { translationY = -abs(s) * sz.toPx() * .08f; scaleY = 1f + .05f * abs(s) }
                "pulse" -> { scaleX = 1f + .07f * s; scaleY = 1f + .07f * s }
                "float" -> { translationY = s * sz.toPx() * .04f }
                "shake" -> { rotationZ = sin(t * 8 * PI.toFloat()) * 7f }
                "sway" -> { rotationZ = s * 6f; translationX = s * sz.toPx() * .02f }
                "flicker" -> { scaleY = 1f + .08f * sin(t * 6 * PI.toFloat()); scaleX = 1f - .03f * sin(t * 6 * PI.toFloat()) }
            }
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
        }) { Flame(sz * .56f) }
        // Emoji compagnon en bas à droite
        Box(Modifier.align(Alignment.BottomEnd).offset(x = -sz * .06f, y = -sz * .2f).graphicsLayer { rotationZ = if (def.anim == "wave") s * 18f else 0f }) { Emoji(def.emoji, sz * .3f) }
        // Mot du sticker, en capsule
        Text(def.text, Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(50)).background(p.flameBrush()).padding(horizontal = sz * .08f, vertical = sz * .02f),
            style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = (sz.value * .12f).sp, color = fr.pepsgoal.app.ui.theme.PLUM), maxLines = 1)
    }
}

/** Sélecteur de stickers (panneau de la zone de saisie). */
@Composable
fun StickerPicker(onPick: (StickerDef) -> Unit) {
    FlowRowCompat(gap = 6.dp) {
        STICKERS.forEach { d ->
            val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val press = rememberPress(inter)
            Box(Modifier.chunk(LocalPal.current.surface, LocalPal.current.edge, 17.dp, 3.dp, 2.dp, press).clip(RoundedCornerShape(15.dp)).springClick(inter, label = "Sticker ${d.label}") { onPick(d) }.padding(2.dp)) { StickerView(d.id, d.emoji, 70.dp, still = true) }
        }
    }
}

/* ------------------------------------------------------------------ Emojis en grand */

/** Un à quatre emojis seuls : affichés en grand, sans bulle. */
@Composable
fun JumboEmojis(list: List<String>) {
    val sz = when (list.size) { 1 -> 64.dp; 2 -> 52.dp; 3 -> 44.dp; else -> 40.dp }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { list.forEach { Emoji(it, sz) } }
}

/* ------------------------------------------------------------------ Pluie d'emojis */

private class Burst(val e: String, val x: Float, val delay: Int, val dur: Int, val size: Int, val sway: Float, val phase: Float)

/** Petits emojis qui montent depuis le bas de la conversation quand le dernier message en contient. */
@Composable
fun EmojiBurst(key: String?, emojis: List<String>, bottom: Dp) {
    val p = LocalPal.current
    if (key == null || emojis.isEmpty() || p.motion) return
    val parts = remember(key) { val r = Random(key.hashCode()); List(16) { i -> Burst(emojis[i % emojis.size], .06f + r.nextFloat() * .88f, r.nextInt(900), 2200 + r.nextInt(1100), 20 + r.nextInt(18), 6f + r.nextFloat() * 14f, r.nextFloat() * 6.28f) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight; val w = maxWidth
        parts.forEach { b ->
            val a = remember(key) { Animatable(0f) }
            LaunchedEffect(key) { delay(b.delay.toLong()); a.animateTo(1f, tween(b.dur, easing = LinearOutSlowInEasing)) }
            val k = a.value
            if (k > 0f && k < 1f) Box(Modifier.offset(x = w * b.x - (b.size / 2).dp + (sin(k * 9f + b.phase) * b.sway).dp, y = h - bottom - (b.size).dp - h * .6f * k)
                .graphicsLayer { alpha = if (k < .12f) k / .12f else ((1f - k) / .5f).coerceAtMost(1f); val sc = .5f + .5f * (k * 5f).coerceAtMost(1f); scaleX = sc; scaleY = sc }) { Emoji(b.e, b.size.dp) }
        }
    }
}

/* ------------------------------------------------------------------ Capture d'écran */

/** Ligne d'information au milieu de la conversation : « Sam a pris une capture d'écran ». */
@Composable
fun ShotNotice(ui: AppState, m: Message) {
    val p = LocalPal.current; val mine = m.from == ui.pg.meId()
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
        MiniPlaque(if (mine) "Tu as pris une capture d’écran · ${hm(m.at)}" else "${first(ui.pg.userById(m.from)?.name ?: "Quelqu’un")} a pris une capture d’écran · ${hm(m.at)}", emoji = "📸")
    }
}

/* ------------------------------------------------------------------ Recherche dans la conversation */

/** Texte d'un message pour la recherche : texte, légende, sondage, objectif partagé, sticker, transcription. */
fun searchText(ui: AppState, m: Message): String {
    val b = Social.body(ui, m) ?: return ""
    return listOfNotNull(b.strN("t"), b.strN("q"), b.obj("g")?.str("title"), STICKERS.firstOrNull { it.id == b.str("s") }?.label, Transcriber.texts[m.id]).joinToString(" ")
}

@Composable
fun ChatSearchBar(q: String, onQ: (String) -> Unit, idx: Int, count: Int, onPrev: () -> Unit, onNext: () -> Unit, onClose: () -> Unit) {
    val p = LocalPal.current
    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().chunk(p.surface, p.edge, 28.dp, 4.dp, 2.dp).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        PSearchField(q, onQ, "Rechercher dans la conversation", Modifier.weight(1f))
        if (q.isNotBlank()) Text(if (count == 0) "0" else "${idx + 1}/$count", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
        RoundBtn("chevronup", "Résultat précédent", onPrev)
        RoundBtn("down", "Résultat suivant", onNext)
        RoundBtn("x", "Fermer la recherche", onClose)
    }
}

/* ------------------------------------------------------------------ Messages programmés */

/** Créneaux proposés pour programmer un message. */
fun schedSlots(): List<Pair<String, Long>> {
    val now = java.time.LocalDateTime.now(); val z = Clock.zone()
    fun at(d: java.time.LocalDateTime) = d.atZone(z).toInstant().toEpochMilli()
    val out = mutableListOf("Dans 1 h" to at(now.plusHours(1)))
    if (now.hour < 19) out += "Ce soir 20 h" to at(now.toLocalDate().atTime(20, 0))
    out += "Demain 7 h" to at(now.toLocalDate().plusDays(1).atTime(7, 0))
    out += "Demain 9 h" to at(now.toLocalDate().plusDays(1).atTime(9, 0))
    out += "Demain 18 h" to at(now.toLocalDate().plusDays(1).atTime(18, 0))
    return out
}

/** Choix de l'heure d'envoi d'un message programmé. */
@Composable
fun SchedulePanel(onPick: (Long) -> Unit, onCancel: () -> Unit) {
    var day by remember { mutableStateOf<String?>(null) }
    var pickDay by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Programmer l’envoi", fontWeight = FontWeight.ExtraBold)
        Muted("Le message part tout seul à l’heure choisie, même si l’application est fermée.", small = true)
        FlowRowCompat(gap = 6.dp) {
            schedSlots().forEach { (l, at) -> PButton(l, { onPick(at) }, icon = "clock", small = true) }
            PButton("Autre…", { pickDay = true }, icon = "calendarplus", small = true)
        }
        PButton("Annuler", onCancel, kind = "ghost", small = true)
    }
    // « Autre… » : jour puis heure, dans les sélecteurs Braise Pop
    if (pickDay) PDatePicker(today(), { day = it }, { pickDay = false }, min = today(), title = "Jour d’envoi")
    day?.let { d -> PTimePicker(java.time.LocalTime.now().plusHours(1).let { "%02d:00".format(it.hour) }, { hm0 ->
        val (h, mi) = hm0.split(":").map { it.toInt() }
        val t = java.time.LocalDate.parse(d).atTime(h, mi).atZone(Clock.zone()).toInstant().toEpochMilli()
        if (t > nowMs() + 30_000) onPick(t)
    }, { day = null }, title = "Heure d’envoi") }
}

/** Messages programmés de la conversation, au-dessus de la zone de saisie. */
@Composable
fun ScheduledStrip(key: String) {
    val p = LocalPal.current; val ctx = LocalContext.current
    Scheduled.rev
    val list = remember(Scheduled.rev, key) { Scheduled.of(ctx, key) }
    if (list.isEmpty()) return
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().chunk(p.surface, p.edge, 22.dp, 3.dp, 2.dp).padding(horizontal = 10.dp, vertical = 6.dp)) {
        list.forEach { s ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pi("clock", size = 16.dp, tint = p.flameInk)
                Column(Modifier.weight(1f)) {
                    Text("${if (ymd(s.at) == today()) "Aujourd’hui" else if (ymd(s.at) == ymd(todayDate().plusDays(1))) "Demain" else fmtDate(ymd(s.at))} à ${hm(s.at)}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Text(s.preview, fontSize = 12.sp, color = p.ink2, maxLines = 1)
                }
                IconBtn("send", "Envoyer maintenant", { Scheduled.sendNow(ctx, s.id) }, plain = true, size = 34.dp)
                IconBtn("x", "Annuler l’envoi programmé", { Scheduled.cancel(ctx, s.id) }, plain = true, size = 34.dp)
            }
        }
    }
}

/* ------------------------------------------------------------------ Transcription des messages vocaux */

/** Texte du message vocal, reconnu sur le téléphone (Android 13 et plus, reconnaissance embarquée disponible). */
@Composable
fun VoiceTranscript(ui: AppState, m: Message, audio: ByteArray?) {
    val p = LocalPal.current; val ctx = LocalContext.current
    val avail = remember { Transcriber.available(ctx) }
    if (!avail) return
    val text = Transcriber.texts[m.id] ?: remember(m.id) { Transcriber.cached(ctx, m.id) }
    var busy by remember(m.id) { mutableStateOf(false) }
    LaunchedEffect(m.id, audio != null) { if (text == null && audio != null) { busy = true; Transcriber.transcribe(ctx, m.id, audio); busy = false } }
    when {
        text != null && text.isNotBlank() -> Text("« $text »", Modifier.padding(top = 4.dp).widthIn(max = 260.dp), fontSize = 13.sp, color = p.ink2, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        text != null -> Muted("Aucune parole reconnue", Modifier.padding(top = 4.dp), small = true)
        busy -> Muted("Transcription…", Modifier.padding(top = 4.dp), small = true)
    }
}
