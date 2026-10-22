package fr.pepsgoal.app.ui.fullscreen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Fs
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.screens.BadgeMedal
import fr.pepsgoal.app.ui.sheets.StoryCard
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.deleteStory
import fr.pepsgoal.core.cloud.likeStory
import fr.pepsgoal.core.cloud.viewStory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val INK = Color(0xFF10102A)

@Composable
fun FullscreenHost(ui: AppState) {
    val f = ui.fs ?: return
    BackHandler { ui.closeFs() }
    val p = LocalPal.current; val h = LocalHaze.current
    // Fond : l'écran derrière est flouté (verre dépoli), ou masqué par une teinte opaque si la transparence est réduite
    val back = if (h != null && !p.transp && f !is Fs.Stories)
        Modifier.hazeEffect(h, HazeStyle(backgroundColor = p.bg, tints = listOf(HazeTint(p.bg.copy(alpha = .6f))), blurRadius = 40.dp, noiseFactor = 0f, fallbackTint = HazeTint(p.bg)))
    else Modifier.background(p.bg)
    Box(Modifier.fillMaxSize().then(back).pointerInput(Unit) { detectTapGestures { } }) {
        when (f) {
            is Fs.Celebrate -> when (val c = f.c) {
                is Celebration.Perfect -> PerfectFs(ui, c)
                is Celebration.Badge -> BadgeFs(ui, c)
            }
            is Fs.Recap -> RecapFs(ui, f)
            is Fs.Stories -> StoriesFs(ui, f)
            is Fs.Tuto -> TutorialFs(ui, f.start)
            is Fs.Focus -> FocusFs(ui, f.goalId)
            is Fs.Intervals -> IntervalsFs(ui, f.goalId)
        }
    }
}

/* ------------------------------------------------------------------ confettis */
private class Bit(val x: Float, val delay: Float, val speed: Float, val drift: Float, val rot: Float, val c: Int, val w: Float)

@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val p = LocalPal.current
    if (p.motion) return
    val cols = listOf(p.flame, p.flame2, p.pink, p.type("objective").c, p.type("routine").c, p.type("abstinence").c)
    val bits = remember { List(140) { Bit(Random.nextFloat(), Random.nextFloat() * 1.4f, .25f + Random.nextFloat() * .35f, Random.nextFloat() * 2 - 1, Random.nextFloat() * 6.28f, Random.nextInt(6), 6 + Random.nextFloat() * 6) } }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { val t0 = withFrameNanos { it }; while (isActive && t < 6f) { withFrameNanos { t = (it - t0) / 1e9f } } }
    Canvas(modifier.fillMaxSize()) {
        for (b in bits) {
            val k = t - b.delay; if (k < 0) continue
            val y = -20f + k * b.speed * size.height; if (y > size.height + 20) continue
            val x = b.x * size.width + sin(k * 3 + b.rot) * 30 * b.drift
            val a = (1f - (t - 4.5f).coerceIn(0f, 1.5f) / 1.5f)
            rotate(b.rot * 57f + k * 280 * b.drift, Offset(x, y)) { drawRect(cols[b.c].copy(alpha = a), Offset(x - b.w / 2, y - b.w), Size(b.w * density / 2.2f, b.w * density / 1.1f)) }
        }
    }
}

@Composable
private fun FsFrame(ui: AppState, bg: Brush, confetti: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(bg)) {
        if (confetti) Confetti()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically), content = content)
        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) { IconBtn("x", "Fermer", { ui.closeFs() }) }
    }
}

/** Apparition échelonnée des éléments d'une diapositive. */
@Composable
private fun Pop(i: Int, content: @Composable () -> Unit) {
    val p = LocalPal.current
    val a = remember { Animatable(if (p.motion) 1f else 0f) }
    LaunchedEffect(Unit) { delay(80L * i); a.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1 - a.value) * 40f }) { content() }
}

/* ------------------------------------------------------------------ journée parfaite */
@Composable
private fun PerfectFs(ui: AppState, c: Celebration.Perfect) {
    val p = LocalPal.current
    FsFrame(ui, Brush.verticalGradient(listOf(p.bg.copy(alpha = .4f), p.flame.copy(alpha = .3f), p.pink.copy(alpha = .3f))), true) {
        Pop(0) { Eyebrow(c.eyebrow) }
        Pop(1) { val inf = rememberInfiniteTransition(label = "pf"); val s by inf.animateFloat(1f, if (p.motion) 1f else 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s"); Box(Modifier.scale(s)) { Flame(120.dp) } }
        Pop(2) { Text(c.title, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, textAlign = TextAlign.Center) }
        Pop(3) { Text(c.text, textAlign = TextAlign.Center, color = p.ink2) }
        if (c.icons.isNotEmpty()) Pop(4) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { c.icons.take(8).forEach { Emoji(it, 34.dp) } } }
        if (c.stats.isNotEmpty()) Pop(5) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                c.stats.forEach { (v, l, pre) -> Column(Modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(p.glassStrong).padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CountUp(v, pre); Text(l, fontSize = 12.sp, color = p.ink2, fontWeight = FontWeight.Bold) } }
            }
        }
        Pop(6) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(if (ui.pg.socialOn()) "Partager ${c.shareLabel}" else "Copier", { ui.closeFs(); ui.open(Sheet.ShareHub(text = c.share)) }, icon = "share", kind = "primary")
                PButton("Fermer", { ui.closeFs() })
            }
        }
    }
}

@Composable
private fun CountUp(v: Int, prefix: String = "", suffix: String = "", size: Int = 26, color: Color = Color.Unspecified) {
    val p = LocalPal.current
    val a = remember { Animatable(if (p.motion) v.toFloat() else 0f) }
    LaunchedEffect(v) { a.animateTo(v.toFloat(), tween(minOf(1600, 500 + kotlin.math.abs(v) * 8), easing = FastOutSlowInEasing)) }
    Text("$prefix${a.value.toInt()}$suffix", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, color = color)
}

/* ------------------------------------------------------------------ badge débloqué */
@Composable
private fun BadgeFs(ui: AppState, c: Celebration.Badge) {
    val p = LocalPal.current; val (label, color) = BADGE_TIERS[c.tier]; val tc = hex(color); val f = c.family
    val next = f.t.getOrNull(c.tier + 1)
    FsFrame(ui, Brush.radialGradient(listOf(tc.copy(alpha = .45f), p.bg.copy(alpha = .4f))), true) {
        val inf = rememberInfiniteTransition(label = "bd")
        val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(if (p.motion) 1_000_000 else 14000, easing = LinearEasing)), label = "r")
        val pop = remember { Animatable(if (p.motion) 1f else .2f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = .45f, stiffness = 180f)) }
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().rotate(rot)) { for (i in 0 until 16) { val a = i * PI.toFloat() / 8; drawLine(tc.copy(alpha = .35f), center, center + Offset(cos(a), sin(a)) * (size.minDimension / 2), 10f) } }
            Box(Modifier.scale(pop.value)) { BadgeMedal(BadgeState(f, 0, c.tier, null, 1.0), 140) }
            Text(label, Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(10.dp)).background(tc).padding(horizontal = 14.dp, vertical = 4.dp), color = INK, fontWeight = FontWeight.ExtraBold)
        }
        Pop(1) { Eyebrow("Badge débloqué · palier ${c.tier + 1} sur ${f.t.size}") }
        Pop(2) { Text("${f.n} $label", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, textAlign = TextAlign.Center) }
        Pop(3) { Text(f.d(f.t[c.tier]), textAlign = TextAlign.Center) }
        Pop(4) { Muted("${if (next != null) "Prochain palier : ${f.d(next)}." else "Tu as atteint le plus haut palier de ce badge !"}${if (c.more > 0) " · ${pl(c.more, "autre badge à découvrir", "autres badges à découvrir")}" else ""}", small = true, center = true) }
        Pop(5) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(if (ui.pg.socialOn()) "Partager" else "Copier", { ui.closeFs(); ui.open(Sheet.ShareHub(text = "Nouveau badge sur PepsGoal : ${f.e} ${f.n} $label — ${f.d(f.t[c.tier])} !")) }, icon = "share", kind = "primary")
                PButton("Voir mes badges", { ui.fsQueue.clear(); ui.closeFs(); ui.nav("profile") })
            }
        }
    }
}

/* ------------------------------------------------------------------ récaps */
/** Couleur du texte d'une diapositive : encre sombre sur fond clair, blanc sur fond sombre. */
private val LocalRecapInk = staticCompositionLocalOf { INK }
private fun lum(c: Color) = .2126f * c.red + .7152f * c.green + .0722f * c.blue

@Composable
private fun RecapFs(ui: AppState, f: Fs.Recap) {
    val p = LocalPal.current
    var i by remember(f) { mutableIntStateOf(f.i) }
    var dir by remember { mutableIntStateOf(1) }
    val s = f.slides[i]; val last = i == f.slides.lastIndex
    val step = { d: Int -> val n = (i + d).coerceIn(0, f.slides.lastIndex); if (n != i) { dir = d; i = n; f.i = n } }
    // « Le mot du coach » (IA locale) : préparé dès l'ouverture du récap, affiché sur la dernière diapositive
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var word by remember(f) { mutableStateOf<String?>(null) }
    LaunchedEffect(f) { word = fr.pepsgoal.app.ai.CoachExtras.recapWord(ctx, f.st) }
    val cols = s.bg.map(::hex)
    val ink = if (cols.map(::lum).average() < .5) Color.White else INK
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(cols)).pointerInput(f) { detectTapGestures { o -> step(if (o.x < size.width * .3f) -1 else 1) } }) {
        // Profondeur : voile sombre en bas, puis formes décoratives propres à la diapositive
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .55f to Color.Transparent, 1f to Color.Black.copy(alpha = .22f))))
        RecapDecor(cols, i)
        s.fx?.let { Floaters(it, i) }
        if (last) Confetti()
        // Progression : segments fins et arrondis, le segment courant se remplit
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 64.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            f.slides.indices.forEach { k ->
                val fill = remember(i) { Animatable(if (k < i || p.motion) 1f else 0f) }
                LaunchedEffect(i) { if (k == i && !p.motion) fill.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(50)).background(ink.copy(alpha = .22f))) {
                    if (k <= i) Box(Modifier.fillMaxHeight().fillMaxWidth(if (k < i) 1f else fill.value).clip(RoundedCornerShape(50)).background(ink.copy(alpha = .9f)))
                }
            }
        }
        // Fermer : discret, en verre
        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 4.dp, end = 10.dp).size(40.dp).clip(CircleShape)
            .background(ink.copy(alpha = .12f)).clickable { ui.closeFs() }, contentAlignment = Alignment.Center) { Pi("x", size = 18.dp, tint = ink) }
        CompositionLocalProvider(LocalRecapInk provides ink) {
            AnimatedContent(i, Modifier.fillMaxSize(), transitionSpec = { if (p.motion) fadeIn(tween(0)) togetherWith fadeOut(tween(0)) else (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { w -> w / 3 * dir } + fadeIn(tween(300))) togetherWith (fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { w -> -w / 6 * dir }) }, label = "slide") { k ->
                val sl = f.slides[k]
                // Diapositive centrée, qui défile si son contenu (ex. « Le mot du coach ») dépasse l'écran
                BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    val minH = maxHeight
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = minH).padding(horizontal = 24.dp, vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                        sl.parts.forEachIndexed { j, part -> Pop(j) { PartView(ui, f, part) { i = 0; f.i = 0 } } }
                        if (k == f.slides.lastIndex) word?.let { w -> Pop(sl.parts.size) { CoachWord(w) } }
                    }
                }
            }
        }
        if (!last) Text("Touche l'écran pour avancer", Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(14.dp), color = ink.copy(alpha = .55f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Formes décoratives de la diapositive : deux halos qui dérivent lentement et des rayons qui tournent. */
@Composable
private fun RecapDecor(cols: List<Color>, key: Int) {
    val p = LocalPal.current
    val inf = rememberInfiniteTransition(label = "decor$key")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(if (p.motion) 1_000_000 else 9000, easing = LinearEasing), RepeatMode.Reverse), label = "t")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(if (p.motion) 1_000_000 else 60000, easing = LinearEasing)), label = "rot")
    val a = cols.first(); val b = cols.last()
    // Variante selon la diapositive : position des halos et présence des rayons
    val v = key % 3
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val c1 = Offset(w * (if (v == 1) .85f else .15f) + w * .08f * t, h * (.18f + .06f * t))
        val c2 = Offset(w * (if (v == 2) .2f else .8f) - w * .06f * t, h * (.78f - .05f * t))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .34f), Color.Transparent), c1, w * .62f), w * .62f, c1)
        drawCircle(Brush.radialGradient(listOf(b.copy(alpha = .55f), Color.Transparent), c2, w * .7f), w * .7f, c2)
        if (v != 1) rotate(rot, Offset(w / 2, h * .42f)) {
            for (r in 0 until 12) {
                val ang = r * PI.toFloat() / 6
                drawLine(Color.White.copy(alpha = .07f), Offset(w / 2, h * .42f), Offset(w / 2, h * .42f) + Offset(cos(ang), sin(ang)) * (h * .8f), w * .09f)
            }
        }
        drawCircle(a.copy(alpha = .18f), w * .08f, Offset(w * .9f, h * .1f + 18f * t))
        drawCircle(Color.White.copy(alpha = .12f), w * .05f, Offset(w * .08f, h * .62f - 14f * t))
    }
}

/** « Le mot du coach » : carte en verre sobre, petite pastille sparkle. */
@Composable
private fun CoachWord(w: String) {
    val ink = LocalRecapInk.current; val dark = ink == INK
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = if (dark) .42f else .14f))
        .border(1.dp, Color.White.copy(alpha = if (dark) .7f else .28f), RoundedCornerShape(24.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(ink.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Pi("sparkle", size = 14.dp, tint = ink) }
            Text("Le mot du coach", color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        Text(w, color = ink, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

/** Tuile en verre des récaps (fond blanc translucide, coins 24 dp). */
private fun Modifier.recapGlass(ink: Color) = this.clip(RoundedCornerShape(24.dp))
    .background(Color.White.copy(alpha = if (ink == INK) .45f else .14f))
    .border(1.dp, Color.White.copy(alpha = if (ink == INK) .7f else .25f), RoundedCornerShape(24.dp))

@Composable
private fun Floaters(e: String, key: Int) {
    val p = LocalPal.current
    if (p.motion) return
    val w = LocalConfiguration.current.screenWidthDp; val h = LocalConfiguration.current.screenHeightDp
    val inf = rememberInfiniteTransition(label = "fl$key")
    for (i in 0 until 9) {
        val dur = 7000 + (i * 3 % 6) * 1000
        val y by inf.animateFloat(1.1f, -.15f, infiniteRepeatable(tween(dur, easing = LinearEasing), initialStartOffset = StartOffset((i * 1700) % dur)), label = "y$i")
        Box(Modifier.offset(((i * 11 + 4) % 96 * w / 100).dp, (y * h).dp).graphicsLayer { alpha = .3f; rotationZ = if (i % 2 == 1) 40f * y else -40f * y }) { Emoji(e, (18 + (i * 7) % 20).dp) }
    }
}

@Composable
private fun PartView(ui: AppState, f: Fs.Recap, part: Part, restart: () -> Unit) {
    val ink = LocalRecapInk.current
    when (part) {
        is Part.Eyebrow -> Text(part.text.uppercase(), Modifier.clip(RoundedCornerShape(50)).background(ink.copy(alpha = .1f)).padding(horizontal = 12.dp, vertical = 5.dp),
            color = ink.copy(alpha = .85f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
        is Part.Title -> Text(part.text, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = if (part.big) 36.sp else 28.sp, color = ink, textAlign = TextAlign.Center, lineHeight = if (part.big) 42.sp else 33.sp)
        is Part.Text -> Text(rich(part.text), color = ink.copy(alpha = .92f), fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 24.sp)
        is Part.Joke -> Text(rich(part.text), Modifier.recapGlass(ink).padding(horizontal = 16.dp, vertical = 12.dp), color = ink, fontSize = 15.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        // Chiffre héros : très grand, avec un léger halo
        is Part.Big -> Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(170.dp).background(Brush.radialGradient(listOf(Color.White.copy(alpha = if (ink == INK) .5f else .18f), Color.Transparent)), CircleShape))
            if (part.raw != null) Text(part.raw!!, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 84.sp, color = ink, lineHeight = 88.sp)
            else CountUp(part.value, part.prefix, part.suffix, 84, ink)
        }
        is Part.Emoji -> { val motion = LocalPal.current.motion; val inf = rememberInfiniteTransition(label = "em"); val k by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "k")
            Box(Modifier.size(132.dp).recapGlass(ink).graphicsLayer { if (!motion) { if (part.anim == "spin") rotationZ = k * 16 - 8 else translationY = -k * 10 } }, contentAlignment = Alignment.Center) { Emoji(part.e, 84.dp) } }
        is Part.Flame -> Flame((110 * part.scale.coerceIn(.5f, 1.6f)).dp)
        is Part.Dots -> FlowRowCompat(Modifier.recapGlass(ink).padding(14.dp), gap = 5.dp) { part.days.forEach { on -> Box(Modifier.size(13.dp).clip(CircleShape).background(if (on) ink else ink.copy(alpha = .18f))) } }
        is Part.Week -> {
            val mx = maxOf(1, part.counts.maxOrNull() ?: 1)
            Row(Modifier.recapGlass(ink).padding(horizontal = 16.dp, vertical = 14.dp).height(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                part.counts.forEachIndexed { d, v -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (d == part.fav) Text("$v", color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    Box(Modifier.width(24.dp).height((96 * v / mx).coerceAtLeast(5).dp).clip(RoundedCornerShape(8.dp)).background(if (d == part.fav) ink else ink.copy(alpha = .28f)))
                    Text(WD[d], color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) } }
            }
        }
        is Part.Facts -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { part.items.forEach { Text(rich(it), Modifier.fillMaxWidth().recapGlass(ink).padding(14.dp), color = ink, fontSize = 15.sp, lineHeight = 21.sp) } }
        is Part.Awards -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { part.items.forEach { (e, n, d) ->
            Row(Modifier.fillMaxWidth().recapGlass(ink).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(ink.copy(alpha = .1f)), contentAlignment = Alignment.Center) { Emoji(e, 30.dp) }
                Column { Text(n, color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp); Text(d, color = ink.copy(alpha = .75f), fontSize = 13.sp) } } } }
        // Tuiles de chiffres : grille 2 × 2 en verre, valeur en grand
        is Part.Cells -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { part.items.chunked(2).forEach { row -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (v, l) ->
                Column(Modifier.weight(1f).fillMaxHeight().recapGlass(ink).padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(v, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = ink, maxLines = 1)
                    Text(l, color = ink.copy(alpha = .8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        } } }
        is Part.FinalActions -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PButton(if (ui.pg.socialOn()) "Partager mon récap" else "Copier mon récap", { ui.closeFs(); ui.open(Sheet.ShareHub(text = Recaps.shareText(f.st), period = f.p)) }, Modifier.fillMaxWidth(), icon = "share", kind = "primary")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PButton("Revoir", restart, Modifier.weight(1f), icon = "repeat")
                PButton("Fermer", { ui.closeFs() }, Modifier.weight(1f), icon = "x")
            }
        }
        is Part.CloseAction -> PButton("Fermer", { ui.closeFs() }, Modifier.fillMaxWidth(), kind = "primary")
    }
}


/* ------------------------------------------------------------------ stories */
@Composable
private fun StoriesFs(ui: AppState, f: Fs.Stories) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val me = pg.meId()
    var gi by remember(f) { mutableIntStateOf(f.ui) }; var si by remember(f) { mutableIntStateOf(f.si) }
    var seen by remember { mutableStateOf(false) }; var paused by remember { mutableStateOf(false) }
    val groups = f.groups
    val g = groups.getOrNull(gi) ?: run { LaunchedEffect(Unit) { ui.closeFs() }; return }
    val list = g.list.filter { s -> f.archive || pg.db.stories.any { it.id == s.id } }
    if (list.isEmpty()) { LaunchedEffect(Unit) { ui.closeFs() }; return }
    val s = list[si.coerceIn(0, list.lastIndex)]; val own = s.userId == me
    val stepFn = { d: Int ->
        seen = false
        var a = gi; var b = si + d
        if (b >= list.size) { a++; b = 0 } else if (b < 0) { if (a == 0) b = 0 else { a--; b = groups[a].list.size - 1 } }
        if (a >= groups.size || (f.archive && a != gi)) ui.closeFs() else { gi = a; si = b }
    }
    LaunchedEffect(s.id) { if (!own) ui.cloud.viewStory(s); if (own && pg.store.acct) try { ui.cloud.loadStoryViews(s) } catch (e: Exception) {} }
    val prog = remember(s.id) { Animatable(0f) }
    LaunchedEffect(s.id, seen, paused) { if (!seen && !paused) { prog.animateTo(1f, tween(((1 - prog.value) * STORY_MS).toInt(), easing = LinearEasing)); stepFn(1) } }
    val live = nowMs() - s.at < STORY_TTL; val left = maxOf(1, Math.ceil((s.at + STORY_TTL - nowMs()) / 3_600_000.0).toInt())
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(s.id) {
        detectTapGestures(onPress = { paused = true; tryAwaitRelease(); paused = false }, onTap = { o -> stepFn(if (o.x < size.width * .3f) -1 else 1) })
    }) {
        // Ambilight : la photo, très floutée et agrandie, éclaire tout l'écran derrière la story
        val painter = coil3.compose.rememberAsyncImagePainter(if (s.kind == "photo") s.photo else null)
        val pst by painter.state.collectAsState()
        val ok = pst is coil3.compose.AsyncImagePainter.State.Success
        val glow by animateFloatAsState(if (ok || s.kind != "photo") 1f else 0f, tween(500), label = "glow")
        if (s.kind == "photo" && ok) Image(painter, null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.35f; scaleY = 1.35f; alpha = .85f * glow }
            .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(90.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded) else Modifier.graphicsLayer { alpha = .35f * glow }), contentScale = ContentScale.Crop)
        else if (s.kind != "photo") Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(p.flame.copy(alpha = .45f), p.pink.copy(alpha = .2f), Color.Transparent))))
        // Voile sombre pour garder l'en-tête et les boutons lisibles
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .55f), .25f to Color.Black.copy(alpha = .15f), .75f to Color.Black.copy(alpha = .15f), 1f to Color.Black.copy(alpha = .6f))))
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 70.dp, bottom = 84.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(28.dp)
            when (s.kind) {
                "photo" -> {
                    // Photo entière, aux proportions d'origine, aux coins arrondis et en relief
                    val sz = (pst as? coil3.compose.AsyncImagePainter.State.Success)?.painter?.intrinsicSize
                    val ratio = sz?.let { if (it.width > 0 && it.height > 0) it.width / it.height else null } ?: (9f / 16f)
                    Box(Modifier.aspectRatio(ratio).graphicsLayer { scaleX = .94f + .06f * glow; scaleY = .94f + .06f * glow; alpha = glow }
                        .shadow(30.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape).border(1.dp, Color.White.copy(alpha = .18f), shape)) {
                        Image(painter, s.text.ifEmpty { "Photo partagée en story" }, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (s.text.isNotEmpty()) Text(s.text, Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .55f)).padding(10.dp), color = Color.White, textAlign = TextAlign.Center)
                    }
                }
                else -> Box(Modifier.fillMaxSize().shadow(30.dp, shape).clip(shape), contentAlignment = Alignment.Center) {
                    s.data?.let { StoryCard(s.kind, it, compact = false) }
                    if (s.text.isNotEmpty()) Text(s.text, Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .55f)).padding(10.dp), color = Color.White, textAlign = TextAlign.Center)
                }
            }
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                list.forEachIndexed { k, _ -> Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .3f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(if (k < si) 1f else if (k == si) (if (seen) 1f else prog.value) else 0f).background(Color.White)) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Av(g.u, 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(if (own) "Ta story" else g.u.name, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Text(if (live) "${ago(s.at)} · encore $left h" else "${fmtDate(ymd(s.at))} · archive", color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
                }
                IconBtn("x", "Fermer les stories", { ui.closeFs() }, tint = Color.White, plain = true)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!own && live) {
                val liked = s.liked
                Row(Modifier.clip(RoundedCornerShape(50)).background(if (liked) Brush.linearGradient(listOf(p.flame2, p.pink)) else SolidColor(Color.White.copy(alpha = .2f))).border(1.dp, Color.White.copy(alpha = .3f), RoundedCornerShape(50)).clickable {
                    if (!pg.socialBlocked("d'encourager")) ui.run { ui.cloud.likeStory(s, !liked) }
                }.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Flame(20.dp, dim = !liked); Text(if (liked) "Encouragé" else "Encourager", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }
            if (own) {
                Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .18f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Flame(16.dp); Text(" ${s.likeCount ?: s.likers.size}", color = Color.White, fontWeight = FontWeight.ExtraBold) }
                PButton("Vu par ${s.views ?: s.seenBy.size}", { seen = !seen }, icon = "users", small = true)
                PButton("Supprimer", {
                    ui.run("Story supprimée.", "trash") { ui.cloud.deleteStory(s.id) }
                    if (list.size <= 1) ui.closeFs() else si = si.coerceAtMost(list.size - 2)
                }, icon = "trash", small = true)
            }
        }
        if (own && seen) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp).clip(RoundedCornerShape(22.dp)).background(p.glassStrong).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vu par ${pl(s.seenBy.size, "ami", "amis")}", fontWeight = FontWeight.ExtraBold)
                if (s.seenBy.isEmpty()) Muted("Personne pour l’instant.", small = true)
                s.seenBy.mapNotNull { pg.userById(it) }.forEach { x -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Av(x, 30.dp); Text(x.name, Modifier.weight(1f)); if (x.id in s.likers) Flame(16.dp) } }
                PButton("Fermer", { seen = false }, small = true)
            }
        }
    }
}
