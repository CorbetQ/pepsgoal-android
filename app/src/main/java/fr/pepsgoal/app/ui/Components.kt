package fr.pepsgoal.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.PressInteraction
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.app.ui.theme.readable
import fr.pepsgoal.app.ui.theme.over
import fr.pepsgoal.app.ui.theme.PLUM
import fr.pepsgoal.app.ui.theme.step
import fr.pepsgoal.app.sys.Perf
import fr.pepsgoal.core.ACCENTS
import fr.pepsgoal.core.model.Avatar
import fr.pepsgoal.core.model.User

/* ------------------------------------------------------------------ pictogrammes */
private val iconCache = HashMap<String, ImageVector>()

fun iconVector(name: String): ImageVector = iconCache.getOrPut(name) {
    val paths = ICON_PATHS[name] ?: ICON_PATHS.getValue("star")
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in paths) addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
}

@Composable
fun Pi(name: String, modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = LocalContentColor.current) {
    Icon(iconVector(name), null, modifier.size(size), tint = tint)
}

/* ------------------------------------------------------------------ flamme (logo) */
private const val FLAME_OUT = "M12 22.5c-4.4 0-7.6-3.1-7.6-7.3 0-3.6 2.4-5.8 4-7.8.4 1.9 1.3 3.2 2.6 3.7-.4-3.2.7-6.4 3.6-8.6-.3 3.5 1.6 5.5 3.2 7.7 1.2 1.6 1.8 3.3 1.8 5.2 0 4.2-3.2 7.1-7.6 7.1z"
private const val FLAME_IN = "M12 21.6c-2 0-3.4-1.4-3.4-3.3 0-1.6 1-2.7 2-3.7.2 1 .7 1.6 1.3 1.8.1-1.7.8-3 2.1-3.9 0 1.7.6 2.7 1.1 3.6.3.6.5 1.2.5 2.1 0 1.9-1.6 3.4-3.6 3.4z"

/** Style de la flamme choisi dans le profil : forme (classique, ronde, effilée, jumelle) et effet (étincelles, braises, halo). */
@androidx.compose.runtime.Immutable
data class FlameStyle(val shape: String = "classic", val fx: String = "none")
val LocalFlameStyle = staticCompositionLocalOf { FlameStyle() }
val FLAME_SHAPES = listOf("classic" to "Classique", "round" to "Ronde", "sharp" to "Effilée", "twin" to "Jumelle")
val FLAME_FX = listOf("none" to "Aucun", "sparks" to "Étincelles", "embers" to "Braises", "halo" to "Halo")

@Composable
fun Flame(size: Dp, modifier: Modifier = Modifier, dim: Boolean = false, colors: List<Color>? = null, style: FlameStyle? = null) {
    val p = LocalPal.current
    val cs = colors ?: LocalFlame.current ?: listOf(p.pink, p.flame, p.flame2)
    val st = style ?: LocalFlameStyle.current
    val outer = remember { PathParser().parsePathString(FLAME_OUT).toPath() }
    val inner = remember { PathParser().parsePathString(FLAME_IN).toPath() }
    // Effets animés réservés aux grandes flammes (accueil, profil, Focus) : rien à calculer pour les petites icônes
    val fxOn = st.fx != "none" && size >= 40.dp && !dim && !p.motion && Perf.flags.fx
    val ph = if (fxOn) slowPhaseState(2600, 15) else null
    val a = if (dim) .35f else 1f
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        val t = ph?.value ?: 0f
        if (fxOn && st.fx == "halo") { val pulse = .5f + .5f * kotlin.math.sin(t * 6.2832f); drawCircle(cs[1].copy(alpha = .14f + .1f * pulse), 11.5f * k, Offset(12f * k, 14f * k)) }
        fun body(dx: Float, sx: Float, sy: Float) = withTransform({ scale(k, k, pivot = Offset.Zero); translate(dx, 0f); scale(sx, sy, pivot = Offset(12f, 22.5f)) }) {
            drawPath(outer, Brush.verticalGradient(cs, startY = 22f, endY = 1f), alpha = a)
            drawPath(inner, Brush.verticalGradient(listOf(cs.last(), Color(0xFFFFF6CC)), startY = 22f, endY = 12f), alpha = a)
        }
        when (st.shape) {
            "round" -> body(0f, 1.16f, .9f)
            "sharp" -> body(0f, .8f, 1.04f)
            "twin" -> { body(-3.6f, .62f, .7f); body(2f, .86f, .92f) }
            else -> body(0f, 1f, 1f)
        }
        if (fxOn) when (st.fx) {
            "sparks" -> for (i in 0 until 6) { val q = (t * 1.6f + i / 6f) % 1f; val x = (12f + kotlin.math.sin(i * 2.1f + q * 6f) * (3f + i % 3)) * k; val y = (6f - q * 7f) * k
                drawCircle(Color(0xFFFFF2B0).copy(alpha = (1f - q) * .9f), (.55f - q * .35f) * k, Offset(x, y)) }
            "embers" -> for (i in 0 until 7) { val q = (t + i / 7f) % 1f; val side = if (i % 2 == 0) -1f else 1f; val x = (12f + side * (4f + q * 4f) + kotlin.math.sin(q * 9f + i) * 1.2f) * k; val y = (20f - q * 18f) * k
                drawCircle(cs[1].copy(alpha = (1f - q) * .85f), (.7f - q * .45f) * k, Offset(x, y)) }
        }
    }
}

@Composable
fun Logo(word: String = "Goal", size: Int = 21) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Flame((size + 7).dp)
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = p.ink)) { append("Peps") }
            withStyle(SpanStyle(brush = Brush.linearGradient(listOf(p.flame2, p.flame, p.pink)))) { append(word) }
        }, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, maxLines = 1)
    }
}

/* ------------------------------------------------------------------ emojis Fluent */
fun emojiKey(e: String): String? {
    if (e.isBlank()) return null
    val cps = e.codePoints().toArray().filter { it != 0xFE0F && it !in 0x1F3FB..0x1F3FF }
    if (cps.isEmpty()) return null
    return cps.joinToString("-") { Integer.toHexString(it).padStart(4, '0') }
}

/** Emoji affiché avec le dessin Fluent de Microsoft (comme la version web), ou celui du système s'il n'existe pas. */
@Composable
fun Emoji(e: String, size: Dp, modifier: Modifier = Modifier, colorFilter: ColorFilter? = null) {
    val key = remember(e) { emojiKey(e) }
    val sp = with(LocalDensity.current) { (size * .82f).toSp() }
    if (key == null) { Text(e, fontSize = sp, modifier = modifier); return }
    SubcomposeAsyncImage(model = "file:///android_asset/emoji/$key.webp", contentDescription = e, modifier = modifier.size(size), contentScale = ContentScale.Fit, colorFilter = colorFilter,
        error = { Box(Modifier.size(size), contentAlignment = Alignment.Center) { Text(e, fontSize = sp, textAlign = TextAlign.Center) } })
}

/* ------------------------------------------------------------------ cartes, boutons, puces */
/** Source du flou des panneaux en verre (null : pas de flou, ex. dans une fenêtre du bas). */
val LocalHaze = staticCompositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }

/** Flammes de collection : de nouvelles couleurs se débloquent avec la meilleure série (classique = couleurs du thème). */
class FlameSkin(val key: String, val name: String, val min: Int, val colors: List<String>?)
val FLAME_SKINS = listOf(
    FlameSkin("classic", "Classique", 0, null),
    FlameSkin("ocean", "Océan", 30, listOf("#1E4DFF", "#22B8FF", "#9BF0FF")),
    FlameSkin("jade", "Jade", 60, listOf("#047857", "#22C55E", "#BBF7D0")),
    FlameSkin("amethyst", "Améthyste", 100, listOf("#6D28D9", "#C026D3", "#F5D0FE")),
    FlameSkin("gold", "Or", 365, listOf("#B45309", "#F59E0B", "#FFF1A8"))
)
/** Couleurs de la flamme choisie (null : couleurs du thème). */
val LocalFlame = staticCompositionLocalOf<List<Color>?> { null }
/** Contenu d'une fenêtre du bas : les cartes y ont un fond plein (sinon leur ombre transparaît en trait flou). */
val LocalInSheet = staticCompositionLocalOf { false }

/** Contenu d'une carte : le texte n'est pas posé sur la scène (pas de plaque nécessaire). */
val LocalInCard = staticCompositionLocalOf { false }

/** Espace occupé par la barre du haut et la barre de navigation flottantes (le contenu défile dessous). */
val LocalBars = compositionLocalOf { PaddingValues(0.dp) }

/** Contour des commandes (puces, boutons-icônes, sélecteurs) : visible sur le verre clair comme sombre. */
fun ctlEdge(p: fr.pepsgoal.app.ui.theme.Pal) = p.control

/**
 * Apparition en cascade des cartes : à l'ouverture d'un onglet ou d'une page, les cartes arrivent l'une après
 * l'autre (léger décalage), en glissant vers le haut avec un rebond.
 */
/** Marge intérieure par défaut des cartes (l'accueil compact ou aéré la change). */
val LocalGlassPad = staticCompositionLocalOf { 16.dp }

/** Onglet touché alors qu'il est déjà ouvert : signal de « retour en haut » pour la liste de la page. */
object TabReTap { var n by mutableIntStateOf(0) }

object Stagger {
    private var t0 = 0L; private var n = 0
    fun reset() { t0 = android.os.SystemClock.uptimeMillis(); n = 0 }
    /** Fenêtre d'ouverture d'une page : seules les cartes visibles à l'ouverture s'animent. */
    fun active(): Boolean = android.os.SystemClock.uptimeMillis() - t0 < 700
    fun next(): Long = if (active()) (n++ * 45L).coerceAtMost(315L) else 0L
}

/** Ancienne ombre portée : plus aucune ombre floue (la marche la remplace). Gardé pour la compatibilité des appels. */
fun Dp.ifShadows(): Dp = 0.dp

@Composable
fun Modifier.enter(): Modifier {
    // Transitions simples (Paramètres › Performance) : cartes affichées directement, sans calque d'animation
    if (!Perf.flags.rich) return this
    val motionOff = LocalPal.current.motion
    // Hors de la fenêtre d'ouverture (carte qui revient en faisant défiler la liste) : affichée directement,
    // l'animation ne se rejoue pas à chaque défilement
    val a = remember { Animatable(if (motionOff || !Stagger.active()) 1f else 0f) }
    LaunchedEffect(Unit) { if (a.value < 1f) { kotlinx.coroutines.delay(Stagger.next()); a.animateTo(1f, Motion.enter()) } }
    // Arrivée discrète : la carte apparaît en fondu en remontant de quelques points, sans rebond
    return this.graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f); translationY = (1f - v) * 12.dp.toPx()
    }
}

/**
 * Fond des pages secondaires (réglages, messages, historique…) : fond uni et deux grands cercles de décor en aplat,
 * immobiles (aucun flou, aucun coût d'animation). [c1] et [c2] gardés pour la compatibilité des appels.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PageBackdrop(c1: Color, c2: Color, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Box(modifier.fillMaxSize().background(p.bg).decoCircles(p))
}

/** Décor de l'en-tête : deux cercles en aplat derrière le haut de l'écran (zéro flou). */
fun Modifier.decoCircles(p: fr.pepsgoal.app.ui.theme.Pal): Modifier = if (p.contrast) this else this.drawBehind {
    val w = size.width
    drawCircle(p.deco[0], w * .42f, Offset(w * .95f, w * .05f))
    drawCircle(p.deco[1], w * .2f, Offset(w * .62f, -w * .06f))
}

/** Tuile d'icône (menus, réglages) : aplat néon, pictogramme prune. Plate : elle ne se presse pas. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun IconTile(icon: String, c1: Color, c2: Color, size: Dp = 38.dp, radius: Dp = size * .32f) {
    val p = LocalPal.current
    val c = vivid(c2)
    Box(Modifier.size(size).background(c, RoundedCornerShape(radius)).then(if (p.contrast) Modifier.border(1.5.dp, p.ink, RoundedCornerShape(radius)) else Modifier),
        contentAlignment = Alignment.Center) {
        Pi(icon, size = size * .53f, tint = PLUM)
    }
}

/** Tuile d'emoji : aplat néon à marche, l'emoji Fluent en grand (66 %). */
@Composable
fun EmojiTile(e: String, c: Color, size: Dp = 44.dp, radius: Dp = size * .32f) = EmojiTileBox(c, size, radius) { Emoji(e, size * .66f) }

/** Même tuile en aplat néon à marche, avec un contenu libre (flamme, illustration…). */
@Composable
fun EmojiTileBox(c: Color, size: Dp = 32.dp, radius: Dp = size * .32f, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPal.current
    val st = if (size >= 30.dp) 3.dp else 2.dp
    Box(Modifier.size(size).chunk(c, if (p.contrast) p.ink else c.step(), radius, st, 0.dp), contentAlignment = Alignment.Center, content = content)
}

/**
 * Ancien voile givré des barres flottantes : les barres sont désormais opaques, il n'y a plus rien à dessiner.
 * Gardé pour la compatibilité des appels.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun EdgeFrost(modifier: Modifier, top: Boolean, cover: Dp, haze: dev.chrisbanes.haze.HazeState? = LocalHaze.current) {}

/**
 * Option de sondage (feed et messagerie) : barre de résultat qui progresse avec les votes, case cochée pour ton choix,
 * même style de verre et de contours que le reste de l'interface.
 */
@Composable
fun PollOption(text: String, pct: Float, show: Boolean, mine: Boolean, top: Boolean, enabled: Boolean, onClick: () -> Unit, sub: String? = null, count: String? = null) {
    val p = LocalPal.current
    val v by animateFloatAsState(if (show) pct.coerceIn(0f, 1f) else 0f, if (p.motion) tween(0) else Springs.bar, label = "poll")
    val shape = RoundedCornerShape(16.dp)
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter, enabled)
    Box(Modifier.fillMaxWidth().chunk(p.surface, if (mine) p.focusEdge else p.edge, 18.dp, 3.dp, 2.dp, press).clip(shape).springClick(inter, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .semantics { selected = mine }) {
        Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(v.coerceIn(0f, 1f)).background(
                SolidColor(if (top || mine) p.heat(1) else p.surface2)))
        }
        Row(Modifier.padding(start = 2.dp, end = 12.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PRadio(mine, p.type("challenge").c)
            Column(Modifier.weight(1f)) {
                Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (!sub.isNullOrEmpty()) Text(sub, fontSize = 12.sp, color = p.ink2, maxLines = 2)
            }
            if (show) Text(count ?: "${Math.round(pct * 100)} %", fontWeight = FontWeight.ExtraBold, color = if (top) p.ink else p.ink2)
        }
    }
}

/** Couleurs du contour « IA » (arc-en-ciel doux qui tourne). */
private val AI_COLORS = listOf(Color(0xFFFF4A12), Color(0xFFFFB000), Color(0xFF2EF2B0), Color(0xFF22D3FF), Color(0xFF7C8CFF), Color(0xFFFF4FA3), Color(0xFFFF4A12))

/** Ancien contour animé des cartes IA : retiré (le coach est signalé par une pilule ✨). Gardé pour la compatibilité. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.aiGlow(radius: Dp): Modifier = this

/** Ressorts communs : réponses vives avec un léger rebond. */
object Springs {
    val pop = androidx.compose.animation.core.spring<Float>(dampingRatio = .7f, stiffness = 600f)
    val bar = androidx.compose.animation.core.spring<Float>(dampingRatio = .75f, stiffness = 380f)
}

/**
 * Effet « ressort » à l'appui : l'élément s'enfonce dès le toucher puis rebondit en le relâchant
 * (même pour un appui très bref). À placer en tête de la chaîne de modificateurs, avec le même
 * [inter] passé au `clickable`.
 */
@Composable
fun Modifier.bounce(inter: MutableInteractionSource, pressed0: Float = .96f): Modifier {
    // Enfoncement discret à l'appui (au plus 4 %), retour souple sans rebond marqué
    val pressed = maxOf(pressed0, .96f)
    val motionOff = LocalPal.current.motion
    val sc = remember { Animatable(1f) }
    LaunchedEffect(inter, motionOff) {
        if (motionOff) return@LaunchedEffect
        inter.interactions.collectLatest { i ->
            when (i) {
                is PressInteraction.Press -> sc.animateTo(pressed, spring(dampingRatio = .9f, stiffness = 1800f))
                is PressInteraction.Release -> {
                    // Appui très bref : on laisse le temps de voir l'enfoncement avant le rebond
                    if (sc.value > pressed + .015f) sc.animateTo(pressed, tween(55, easing = Motion.Emphasized))
                    sc.animateTo(1f, spring(dampingRatio = .75f, stiffness = 600f))
                }
                is PressInteraction.Cancel -> sc.animateTo(1f, Motion.standard())
            }
        }
    }
    return this.graphicsLayer { scaleX = sc.value; scaleY = sc.value }
}

/** Zone cliquable avec effet ressort et ondulation (à placer après le clip/fond de l'élément). */
@Composable
fun Modifier.springClick(inter: MutableInteractionSource, enabled: Boolean = true, label: String? = null, role: Role? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier =
    if (onLongClick == null) this.clickable(interactionSource = inter, indication = androidx.compose.foundation.LocalIndication.current, enabled = enabled, onClickLabel = label, role = role) { Feedback.tap(); onClick() }
    else this.combinedClickable(interactionSource = inter, indication = androidx.compose.foundation.LocalIndication.current, enabled = enabled, onClickLabel = label, role = role,
        onLongClick = { Feedback.armed(); onLongClick() }) { Feedback.tap(); onClick() }

/** Ancien fond « verre givré » : désormais une surface opaque (aucun flou). */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.glassBg(shape: Shape, strong: Boolean = false): Modifier = this.clip(shape).background(LocalPal.current.surface)

/** Aucun enfoncement (élément non pressable). */
val NoPress: () -> Float = { 0f }

/** Rayons des cartes ramenés à l'échelle du design system (22, 24, 28). */
fun cardRadius(r: Dp): Dp = when { r <= 0.dp -> 0.dp; r < 23.dp -> 22.dp; r < 26.dp -> 24.dp; else -> 28.dp }

/**
 * Carte « à marche » : surface opaque, bord de 2 dp et marche de 4 dp. Une carte touchable s'enfonce à l'appui.
 * [border] : couleur du bord et de la marche (carte mise en avant) ; [ai] : carte du coach, bord de l'accent.
 * [strong] et [wash] gardés pour la compatibilité des appels (plus de verre ni de lavis).
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Glass(modifier: Modifier = Modifier, strong: Boolean = false, radius: Dp = 24.dp, pad: Dp = LocalGlassPad.current, border: Color? = null, fill: Boolean = true, onClick: (() -> Unit)? = null, ai: Boolean = false,
          wash: List<Color>? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    val r = cardRadius(radius)
    val inter = remember { MutableInteractionSource() }
    val press = if (onClick != null) rememberPress(inter) else NoPress
    val edge = if (p.contrast) p.ink else border ?: if (ai) p.flame else p.edge
    Column(
        // Toute la largeur disponible par défaut : une carte au contenu court reste alignée sur les autres
        (if (fill) Modifier.fillMaxWidth() else Modifier).then(modifier).enter()
            .chunk(p.surface, edge, r, 4.dp, 2.dp, press).clip(RoundedCornerShape((r - 2.dp).coerceAtLeast(0.dp)))
            .then(if (onClick != null) Modifier.springClick(inter, onClick = onClick) else Modifier).padding(pad)
    ) { CompositionLocalProvider(LocalInCard provides true) { content() } }
}

/** Carte du design system (nom « Braise Pop » de [Glass]). */
@Composable
fun Card(modifier: Modifier = Modifier, radius: Dp = 24.dp, pad: Dp = LocalGlassPad.current, border: Color? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) =
    Glass(modifier, radius = radius, pad = pad, border = border, onClick = onClick, content = content)

/** Titre de carte : emoji Fluent de 22 dp facultatif, puis titre en Unbounded 16. */
@Composable
fun CardHead(title: String, modifier: Modifier = Modifier, emoji: String? = null, right: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (emoji != null) Emoji(emoji, 22.dp)
        Text(title, fontSize = 16.sp, fontFamily = Unbounded, fontWeight = FontWeight.Bold, lineHeight = 20.sp, modifier = Modifier.weight(1f).semantics { heading() })
        right()
    }
}

/** Compteur d'un titre : pastille creusée, chiffres en gras. */
@Composable
fun Count(n: Any) {
    val p = LocalPal.current
    Text("$n", Modifier.clip(CircleShape).background(p.surface2).padding(horizontal = 9.dp, vertical = 2.dp), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = p.ink2, maxLines = 1)
}

@Composable
fun Eyebrow(text: String, color: Color = LocalPal.current.ink2) = Text(tr(text).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, color = color)

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, small: Boolean = false, center: Boolean = false, maxLines: Int = Int.MAX_VALUE) =
    Text(text, modifier, color = LocalPal.current.ink2, fontSize = if (small) 13.sp else 15.sp, textAlign = if (center) TextAlign.Center else null, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/**
 * Bouton capsule « à marche » : principal (dégradé de la flamme, encre prune), de type ([color] : néon de l'objectif),
 * secondaire (« normal »), danger, discret (« ghost », texte seul). Hauteur 54 (44 petit) + marche 4 ; il s'enfonce à
 * l'appui. Désactivé : 40 %, sans marche.
 */
@Composable
fun PButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, kind: String = "normal", small: Boolean = false,
    enabled: Boolean = true, leading: (@Composable () -> Unit)? = null, color: Color? = null
) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter, enabled)
    val h = maxOf(if (small) 44.dp else 54.dp, if (p.big) 48.dp else 0.dp)
    val k = if (kind == "type" || (color != null && kind == "normal")) "type" else kind
    val tc = vivid(color ?: p.type("monthly").c)
    val fill: Brush = when (k) { "primary" -> p.flameBrush(); "type" -> SolidColor(tc); "ghost" -> SolidColor(Color.Transparent); else -> SolidColor(p.surface) }
    val edge = when (k) { "primary" -> p.flameStep; "type" -> if (p.contrast) p.ink else tc.step(); "ghost" -> Color.Transparent; else -> p.edge }
    val border = if (k == "primary" || k == "type" || k == "ghost") 0.dp else 2.dp
    val step = if (k == "ghost" || !enabled) 0.dp else 4.dp
    val fg = when (k) { "primary" -> if (p.contrast) p.bg else p.onAccent; "type" -> PLUM; "ghost" -> p.flameInk; "danger" -> p.danger; else -> p.ink }
    Row(
        modifier.alpha(if (enabled) 1f else .4f).then(if (!enabled && k != "ghost") Modifier.padding(top = 4.dp) else Modifier)
            .then(if (k == "ghost") Modifier else Modifier.chunk(fill, edge, h / 2, step, border, press)).clip(CircleShape)
            .clickable(enabled = enabled, interactionSource = inter, indication = LocalIndication.current, role = Role.Button) { Feedback.tap(); onClick() }
            .heightIn(min = h - border * 2)
            .padding(horizontal = if (small) 16.dp else 22.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        leading?.invoke()
        if (icon != null) Pi(icon, size = if (small) 18.dp else 20.dp, tint = fg)
        if (text.isNotEmpty()) Text(text, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = if (small) 15.sp else 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

private val LocalIndication = androidx.compose.foundation.LocalIndication
private fun Modifier.alpha(a: Float) = this.graphicsLayer { alpha = a }

/**
 * Bouton rond « à marche » (44 dp, marche 3) : surface, icône de 20 dp. [primary] : aplat de la flamme, icône prune.
 * [plain] gardé pour la compatibilité (même rendu). Cible tactile d'au moins 48 dp.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun IconBtn(icon: String, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, plain: Boolean = false, badge: Int = 0, tint: Color? = null, size: Dp = 44.dp, primary: Boolean = false) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter)
    val d = size.coerceAtLeast(32.dp)
    val prim = primary && !p.contrast
    Box(modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.semantics { contentDescription = if (badge > 0) "$desc, $badge" else desc; role = Role.Button }.size(d, d + 3.dp)
                .chunk(if (prim) p.flameBrush() else SolidColor(p.surface), if (prim) p.flameStep else p.edge, d / 2, 3.dp, if (prim) 0.dp else 2.dp, press)
                .clip(CircleShape).springClick(inter, label = desc, onClick = onClick), contentAlignment = Alignment.Center
        ) { Pi(icon, size = if (d >= 40.dp) 20.dp else 18.dp, tint = tint ?: if (prim) p.onAccent else p.ink) }
        if (badge > 0) Badge(badge, Modifier.align(Alignment.TopEnd).offset(2.dp, 0.dp))
    }
}

/** Bouton rond du design system (nom « Braise Pop » de [IconBtn]). */
@Composable
fun RoundBtn(icon: String, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, badge: Int = 0) = IconBtn(icon, desc, onClick, modifier, badge = badge)

/** Pastille de compteur : 20 dp, aplat rose, chiffre prune ; « pop » quand le nombre change. */
@Composable
fun Badge(n: Int, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Box(modifier.popOnChange(n).defaultMinSize(20.dp, 20.dp).background(vivid(p.pink), CircleShape).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
        Text(if (n > 9) "9+" else "$n", color = PLUM, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 13.sp)
    }
}

/**
 * Étiquette plate (non pressable) : capsule de 26 dp. Avec une [color] : aplat de cette couleur et texte prune ;
 * sans : creux neutre et texte secondaire. [ink] : couleur du texte d'une étiquette neutre.
 */
@Composable
fun Chip(text: String, icon: String? = null, color: Color? = null, ink: Color? = null, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val fill = if (color != null) vivid(color) else p.surface2
    val fg = if (color != null) PLUM else ink ?: p.ink2
    Row(modifier.heightIn(min = 26.dp).clip(CircleShape).background(fill).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) Pi(icon, size = 13.dp, tint = fg)
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 2, lineHeight = 15.sp)
    }
}

/** Couleurs des tuiles de statistiques (Série, Record, Taux, Validés…). */
@Composable
fun statColors(): List<Color> { val p = LocalPal.current; return listOf("limit", "monthly", "duration", "routine", "objective", "abstinence").map { p.type(it).c } }

/**
 * Tuiles de statistiques en aplats néon à marche : emoji facultatif, valeur en Unbounded 22, libellé ; toutes de la
 * même taille, libellés jamais coupés.
 */
@Composable
fun StatGrid(items: List<Pair<String, String>>, columns: Int = 2, emojis: List<String>? = null, colors: List<Color>? = null) {
    val cols = colors ?: statColors()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(columns).forEachIndexed { ri, row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { ci, (v, l) ->
                    val i = ri * columns + ci
                    StatTile(v, l, cols[i % cols.size], emojis?.getOrNull(i), Modifier.weight(1f).fillMaxHeight())
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Tuile de statistique : aplat néon, marche, valeur et libellé en prune. */
@Composable
fun StatTile(value: String, label: String, color: Color, emoji: String? = null, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val c = vivid(color)
    Column(modifier.chunk(c, if (p.contrast) p.ink else c.step(), 22.dp, 4.dp, 0.dp).semantics(mergeDescendants = true) {}.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (emoji != null) Emoji(emoji, 24.dp)
        FitText(value, size = 22, min = 12, weight = FontWeight.ExtraBold, family = Unbounded, color = PLUM)
        Text(label, fontSize = 13.sp, color = PLUM, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
    }
}

/** Étiquette du type d'objectif : aplat du néon, texte prune. */
@Composable
fun TypeChip(type: String) {
    val p = LocalPal.current; val tc = p.type(type); val info = fr.pepsgoal.core.typeInfo(type)
    Chip(info.label, null, tc.c)
}

/**
 * Puce de filtre : capsule de 42 dp à marche. Choisie : aplat néon ([color], sinon le jaune « monthly ») et texte prune ;
 * au repos : surface neutre. [dashed] gardé pour la compatibilité.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun FChip(text: String, on: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null, dashed: Boolean = false, color: Color? = null) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter)
    val c = vivid(color ?: p.type("monthly").c)
    val b = if (on) 0.dp else 2.dp
    Row(
        Modifier.chunk(if (on) c else p.surface, if (on) (if (p.contrast) p.ink else c.step()) else p.edge, 22.dp, 4.dp, b, press).clip(CircleShape)
            .springClick(inter, onClick = onClick).semantics { selected = on }.heightIn(min = 44.dp - b * 2).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        leading?.invoke()
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = if (on) PLUM else p.ink, maxLines = 2, lineHeight = 17.sp)
    }
}

/**
 * Étend un élément sur les marges de son conteneur ([h] de chaque côté) : une rangée qui défile va jusqu'au bord de la
 * carte ou de l'écran, et son contenu, décalé d'autant, reste aligné sur le reste.
 */
fun Modifier.bleed(h: Dp): Modifier = this.layout { m, c ->
    if (!c.hasBoundedWidth) { val pl = m.measure(c); return@layout layout(pl.width, pl.height) { pl.place(0, 0) } }
    val b = h.roundToPx()
    val pl = m.measure(c.copy(minWidth = c.minWidth + 2 * b, maxWidth = c.maxWidth + 2 * b))
    layout((pl.width - 2 * b).coerceAtLeast(0), pl.height) { pl.place(-b, 0) }
}

/** Rangée de puces qui défile, sans jamais couper la première ni la dernière (même agrandies quand elles sont choisies). */
@Composable
fun ChipScroller(bleed: Dp = 18.dp, gap: Dp = 8.dp, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().bleed(bleed).horizontalScroll(rememberScrollState()).padding(horizontal = bleed, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically, content = content)
}

/**
 * Choix d'un objectif : puces sur une ligne qui défile jusqu'aux bords, coche sur l'objectif choisi, et liste déjà
 * défilée jusqu'à lui à l'ouverture. [none] : première puce « sans objectif » (valeur null).
 */
@Composable
fun GoalChips(goals: List<fr.pepsgoal.core.model.Goal>, selected: String?, onPick: (String?) -> Unit, none: String? = null, bleed: Dp = 18.dp, maxTitle: Int = 26) {
    val p = LocalPal.current
    val items: List<fr.pepsgoal.core.model.Goal?> = (if (none != null) listOf(null) else emptyList<fr.pepsgoal.core.model.Goal?>()) + goals
    val start = items.indexOfFirst { it?.id == selected }.coerceAtLeast(0)
    val st = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, start - 1))
    androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth().bleed(bleed), state = st, contentPadding = PaddingValues(horizontal = bleed, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        items(items.size, key = { items[it]?.id ?: "none" }) { i ->
            val g = items[i]; val on = g?.id == selected
            val label = if (g == null) none ?: "" else "${g.icon} ${if (g.title.length > maxTitle) g.title.take(maxTitle - 1).trimEnd() + "…" else g.title}"
            FChip(label, on, { onPick(g?.id) }, leading = if (on) ({ Pi("check", size = 14.dp, tint = PLUM) }) else null, color = g?.let { p.type(it.type).c })
        }
    }
}

/** Barre de progression : rail creusé, remplissage néon (ou dégradé de la flamme) qui se remplit en ressort `move`. */
@Composable
fun Bar(pct: Double, modifier: Modifier = Modifier, color: Color? = null, height: Dp = 10.dp) {
    val p = LocalPal.current
    val target = (pct / 100.0).coerceIn(0.0, 1.0).toFloat()
    val v by animateFloatAsState(target, if (p.motion) tween(0) else Springs.bar, label = "bar")
    val brush = if (color != null) SolidColor(color) else p.flameBrush()
    Box(modifier.fillMaxWidth().height(height).drawBehind {
        val h = size.height; val r = CornerRadius(h / 2)
        drawRoundRect(p.rail, cornerRadius = r)
        val w = size.width * v.coerceIn(0f, 1.03f)
        if (w > .5f) drawRoundRect(brush, Offset.Zero, Size(maxOf(w, h), h), r)
    })
}

/** Anneau : trait épais (15 % de la taille), rail creusé, arc au dégradé de la flamme, chiffre en Unbounded. */
@Composable
fun Ring(done: Int, total: Int, size: Dp = 108.dp) {
    val p = LocalPal.current
    val pct = if (total > 0) done.toFloat() / total else 0f
    val v by animateFloatAsState(pct, if (p.motion) tween(0) else Springs.bar, label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = this.size.minDimension * .15f
            val o = Offset(sw / 2, sw / 2); val r = Size(this.size.width - sw, this.size.height - sw)
            drawArc(p.surface2, 0f, 360f, false, o, r, style = Stroke(sw))
            if (v > .001f) rotate(-90f) {
                val sweep = if (p.contrast) SolidColor(p.ink) else Brush.sweepGradient(listOf(p.flame2, p.flame, p.pink, p.flame2))
                drawArc(sweep, 0f, 360f * v.coerceAtMost(1f), false, o, r, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$done", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * .22f).sp, color = p.ink)
            Text("/$total", Modifier.padding(bottom = (size.value * .025f).dp), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * .15f).sp, color = p.ink3)
        }
    }
}

/* ------------------------------------------------------------------ avatars */
fun accentBrush(i: Int): Brush { val a = (ACCENTS.getOrNull(i) ?: ACCENTS[0]).c.map(::hex); return Brush.linearGradient(listOf(a[0], a[1], a[2])) }

@Composable
fun Av(u: User?, size: Dp = 40.dp, a: Avatar? = null, modifier: Modifier = Modifier) {
    // Statut du jour : petit emoji en bas à droite (avatars de 34 dp et plus)
    val st = if (size >= 34.dp && u != null) LocalStatuses.current[u.id] else null
    if (st == null) AvFace(u, size, a, modifier)
    else Box(modifier.size(size)) {
        AvFace(u, size, a)
        Box(Modifier.align(Alignment.BottomEnd).offset(x = size * .08f, y = size * .06f).size(size * .44f).clip(CircleShape).background(LocalPal.current.surface), contentAlignment = Alignment.Center) {
            Emoji(st, size * .3f)
        }
    }
}

/** Statuts du jour (id → emoji) des personnes visibles, fournis par la racine de l'interface. */
val LocalStatuses = compositionLocalOf<Map<String, String>> { emptyMap() }

@Composable
private fun AvFace(u: User?, size: Dp, a: Avatar?, modifier: Modifier = Modifier) {
    val av = a ?: u?.avatar ?: Avatar()
    val ink = PLUM
    Box(modifier.size(size).clip(CircleShape).background(accentBrush(av.color)), contentAlignment = Alignment.Center) {
        when {
            av.kind == "photo" && av.photo.isNotEmpty() -> AsyncImage(model = av.photo, contentDescription = null, modifier = Modifier.fillMaxSize().padding(2.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            av.kind == "emoji" && av.emoji.isNotEmpty() -> Emoji(av.emoji, size * .62f)
            av.kind == "picto" && ICON_PATHS.containsKey(av.picto) -> Pi(av.picto, size = size * .55f, tint = ink)
            else -> {
                val ini = (u?.name ?: u?.username ?: "?").trim().split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
                Text(ini, color = ink, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * .38f).sp, fontFamily = Unbounded)
            }
        }
    }
}

/** Couleurs d'un palier de niveau (1 Étincelle … 10 Supernova) : anneau, bannière, pastilles. */
fun tierColors(tier: Int): List<Color> = when (tier.coerceIn(1, 10)) {
    1 -> listOf(hex("#FFE45C"), hex("#FFB000"))
    2 -> listOf(hex("#FFB000"), hex("#FF7A3D"))
    3 -> listOf(hex("#FF8A3D"), hex("#FF4A12"))
    4 -> listOf(hex("#FF4A12"), hex("#FF2E63"))
    5 -> listOf(hex("#FF2E63"), hex("#D61F69"), hex("#FF7A3D"))
    6 -> listOf(hex("#FF4FA3"), hex("#B23CFF"))
    7 -> listOf(hex("#B3122E"), hex("#FF5A1F"), hex("#FFB000"))
    8 -> listOf(hex("#8C7DFF"), hex("#22D3FF"), hex("#C6B6FF"))
    9 -> listOf(hex("#FFD23F"), hex("#FF8A00"), hex("#FF4FA3"))
    else -> listOf(hex("#22D3FF"), hex("#B48CFF"), hex("#FF4FA3"), hex("#FFE45C"))
}

/** Anneau de niveau animé autour de l'avatar, aux couleurs du palier ([tier] de 1 à 10). */
@Composable
fun LevelRing(tier: Int, size: Dp, content: @Composable () -> Unit) {
    val cols = tierColors(tier)
    // `size` : taille de l'avatar ; l'anneau l'entoure à 2 dp (comme .lv-ring de la version web)
    Box(Modifier.size(size + 10.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { val w = 3.dp.toPx(); drawCircle(Brush.sweepGradient(cols + cols.first()), radius = this.size.minDimension / 2 - w / 2, style = Stroke(w)) }
        content()
    }
}

/* ------------------------------------------------------------------ texte enrichi (**gras**) */
fun rich(s0: String, bold: Color? = null): AnnotatedString = buildAnnotatedString {
    val parts = I18n.tr(s0).split("**")
    parts.forEachIndexed { i, t -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = bold ?: Color.Unspecified)) { append(t) } else append(t) }
}

/** Emoji des états vides, d'après l'ancienne icône. */
private val EMPTY_EMOJI = mapOf("sun" to "🌴", "sparkle" to "✨", "users" to "👋", "user" to "👋", "chat" to "💬", "bell" to "🔔", "star" to "⭐", "trophy" to "🏆",
    "medal" to "🏅", "target" to "🎯", "list" to "📝", "search" to "🔍", "image" to "🖼️", "book" to "📖", "heart" to "💛", "flame" to "🔥", "gift" to "🎁",
    "calendar" to "📅", "cal" to "📅", "clock" to "⏰", "shield" to "🛡️", "feed" to "📰", "check" to "✅", "moon" to "🌙", "cloud" to "☁️", "trend" to "📈")

/**
 * État vide : grand emoji Fluent (80 dp) incliné qui flotte doucement, titre en Unbounded 20, texte, action. Sans
 * titre, seul le texte s'affiche.
 */
@Composable
fun Empty(title: String, text: String, icon: String = "sparkle", emoji: String? = null, action: (@Composable () -> Unit)? = null) {
    // Jamais posé sur la scène : hors carte (et hors feuille), l'état vide a sa propre carte
    if (!LocalInCard.current && !LocalInSheet.current) { Glass { Empty(title, text, icon, emoji, action) }; return }
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title.isNotEmpty()) EmptyArt(emoji ?: EMPTY_EMOJI[icon] ?: "✨")
        if (title.isNotEmpty()) Text(title, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 24.sp, color = LocalPal.current.ink, textAlign = TextAlign.Center)
        Text(text, color = LocalPal.current.ink2, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        action?.let { Spacer(Modifier.height(6.dp)); it() }
    }
}

/** Illustration des états vides : emoji de 80 dp incliné, qui flotte (cycle de 2 s, arrêté hors écran et en animations réduites). */
@Composable
private fun EmptyArt(e: String) {
    val ph = slowPhaseState(2000, 15, running = Perf.flags.fx)
    Box(Modifier.size(96.dp).graphicsLayer {
        val s = kotlin.math.sin(ph.value * 2f * kotlin.math.PI.toFloat())
        translationY = s * 4.dp.toPx(); rotationZ = -6f + s * 3f
    }, contentAlignment = Alignment.Center) { Emoji(e, 80.dp) }
}

/** Bloc gris qui pulse doucement pendant un chargement (squelette de la future interface). */
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 14.dp, radius: Dp = 6.dp) {
    val p = LocalPal.current
    Box(modifier.height(height).clip(RoundedCornerShape(radius)).background(p.surface2))
}

/** Carte squelette : une pastille (ou un avatar), un titre et quelques lignes. */
@Composable
fun SkeletonCard(lines: Int = 2, round: Boolean = false) {
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Skeleton(Modifier.width(44.dp), 44.dp, if (round) 22.dp else 17.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skeleton(Modifier.fillMaxWidth(.55f), 16.dp)
                repeat(lines) { i -> Skeleton(Modifier.fillMaxWidth(if (i % 2 == 0) .9f else .7f), 12.dp) }
            }
        }
    }
}

@Composable
fun Note(text: String, icon: String = "bell", modifier: Modifier = Modifier) {
    val p = LocalPal.current
    // Note d'information : plate (non pressable), creux neutre ; posée sur la scène, elle devient une carte opaque
    val scene = !LocalInCard.current && !LocalInSheet.current
    Row(modifier.fillMaxWidth().then(if (scene) Modifier.chunk(p.surface, p.edge, 22.dp, 4.dp, 2.dp) else Modifier.clip(RoundedCornerShape(22.dp)).background(p.surface2))
        .padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Pi(icon, size = 18.dp, tint = p.flameInk)
        Text(rich(text), color = p.ink2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Interrupteur avec libellé et description (ligne de réglage). */
@Composable
fun SetRow(title: String, sub: String? = null, modifier: Modifier = Modifier, right: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPal.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val half = maxWidth * .5f
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold, color = p.ink)
                if (sub != null) Text(sub, fontSize = 13.sp, color = p.ink2)
            }
            // Les commandes de droite ne prennent jamais plus de la moitié de la ligne : le titre n'est plus coupé en plein mot
            Row(Modifier.widthIn(max = half), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { right() }
        }
    }
}

/**
 * Interrupteur 60 × 36 : allumé, aplat lime (« routine ») et pastille blanche à marche portant une coche prune ;
 * éteint, creux neutre au contour ≥ 3:1. La pastille saute d'un côté à l'autre avec le ressort `pop`.
 */
@Composable
fun PSwitch(on: Boolean, onChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val p = LocalPal.current
    val k by animateFloatAsState(if (on) 1f else 0f, if (p.motion) snap() else Motion.pop(), label = "switch")
    val inter = remember { MutableInteractionSource() }
    val lime = vivid(p.type("routine").c)
    val track = if (on) lime else p.surface2
    val edge = if (p.contrast) p.ink else if (on) lime.step() else p.control
    // Interrupteur isolé : cible de 60 × 48 ; sans [onChange], dessin seul (la ligne qui le contient porte l'interrupteur)
    if (onChange != null) Box(Modifier.size(60.dp, 48.dp).toggleableCompat(on, enabled, inter) { v -> if (v) Feedback.armed() else Feedback.disarmed(); onChange(v) },
        contentAlignment = Alignment.Center) { PSwitchBody(on, enabled, k, track, edge) }
    else PSwitchBody(on, enabled, k, track, edge)
}

@Composable
private fun PSwitchBody(on: Boolean, enabled: Boolean, k: Float, track: Color, edge: Color) {
    Box(Modifier.alpha(if (enabled) 1f else .45f).size(60.dp, 36.dp).background(track, CircleShape).border(2.dp, edge, CircleShape).clip(CircleShape)) {
        Box(Modifier.align(Alignment.CenterStart).graphicsLayer { translationX = (4.dp + 20.dp * k).toPx() }.size(28.dp, 30.dp).chunk(Color.White, edge, 14.dp, 2.dp, 0.dp),
            contentAlignment = Alignment.Center) {
            if (on) Pi("check", size = 14.dp, tint = PLUM)
        }
    }
}

/** Interrupteur accessible (rôle « interrupteur » annoncé par TalkBack). */
private fun Modifier.toggleableCompat(on: Boolean, enabled: Boolean, inter: MutableInteractionSource, onChange: (Boolean) -> Unit): Modifier =
    this.toggleable(value = on, interactionSource = inter, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onChange)

/**
 * Choix segmenté : rail capsule creusé (50 dp), le segment choisi est une pastille à marche qui glisse d'une option à
 * l'autre (ressort `move`, léger dépassement). Au-delà de quatre options, elles passent à la ligne.
 */
@Composable
fun <T> Seg(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit, modifier: Modifier = Modifier, icons: List<String>? = null) {
    val p = LocalPal.current
    val idx = options.indexOfFirst { it.first == value }
    val edge = if (p.contrast) p.ink else p.edge
    @Composable
    fun Label(i: Int, l: String, on: Boolean) {
        icons?.getOrNull(i)?.let { Pi(it, size = 16.dp, tint = if (on) p.ink else p.ink2) }
        Text(l, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 15.sp, color = if (on) p.ink else p.ink2, maxLines = 2, lineHeight = 18.sp, textAlign = TextAlign.Center)
    }
    // Très grand texte (200 %) et plus de trois options : liste de choix, rien n'est coupé
    if (options.size > 3 && LocalDensity.current.fontScale >= 1.5f) { Box(modifier) { PRadioGroup(options, value, onChange) }; return }
    if (options.size <= 4) {
        val n = options.size.coerceAtLeast(1)
        val x by animateFloatAsState(idx.coerceAtLeast(0).toFloat(), if (p.motion) snap() else Motion.move(), label = "seg")
        Row(modifier.clip(RoundedCornerShape(28.dp)).background(p.surface2).padding(4.dp).heightIn(min = 48.dp).height(IntrinsicSize.Min).drawBehind {
            if (idx < 0) return@drawBehind
            // Pastille du segment choisi : marche de 3 dp, dessinée à la position animée (aucune nouvelle mise en page)
            val w = size.width / n; val st = 3.dp.toPx(); val r = CornerRadius((size.height - st) / 2)
            drawRoundRect(edge, Offset(x * w, 0f), Size(w, size.height), r)
            drawRoundRect(p.surface, Offset(x * w, 0f), Size(w, size.height - st), r)
        }) {
            options.forEachIndexed { i, (k, l) ->
                val on = i == idx
                val inter = remember { MutableInteractionSource() }
                Row(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).springClick(inter, role = Role.RadioButton) { onChange(k) }.semantics { selected = on }.padding(start = 8.dp, end = 8.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) { Label(i, l, on) }
            }
        }
    } else FlowRowCompat(modifier.clip(RoundedCornerShape(24.dp)).background(p.surface2).padding(4.dp), gap = 4.dp) {
        options.forEachIndexed { i, (k, l) ->
            val on = i == idx
            val inter = remember { MutableInteractionSource() }
            Row(Modifier.then(if (on) Modifier.chunk(p.surface, edge, 19.dp, 3.dp, 0.dp) else Modifier.padding(bottom = 3.dp)).clip(CircleShape)
                .springClick(inter, role = Role.RadioButton) { onChange(k) }.semantics { selected = on }.heightIn(min = 48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Label(i, l, on) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowCompat(modifier: Modifier = Modifier, gap: Dp = 4.dp, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap), itemVerticalAlignment = Alignment.CenterVertically) { content() }
}

/** Texte sur une ligne qui rétrécit pour tenir dans sa place (petites tuiles, grands réglages de police). */
@Composable
fun FitText(text: String, modifier: Modifier = Modifier, size: Int = 14, min: Int = 8, color: Color = Color.Unspecified, weight: FontWeight = FontWeight.Bold, family: androidx.compose.ui.text.font.FontFamily? = null, align: androidx.compose.ui.text.style.TextAlign? = null) {
    val c = if (color == Color.Unspecified) LocalContentColor.current else color
    val lang = I18n.rev
    val shown = remember(text, lang) { tr(text) }
    androidx.compose.foundation.text.BasicText(shown, modifier, style = LocalTextStyle.current.copy(color = c, fontSize = size.sp, fontWeight = weight, fontFamily = family ?: LocalTextStyle.current.fontFamily, textAlign = align ?: LocalTextStyle.current.textAlign),
        maxLines = 1, softWrap = false, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(min.sp, size.sp, 0.5.sp))
}

/** Champ de saisie (nom historique de [PTextField]). */
@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, placeholder: String = "", singleLine: Boolean = true, minLines: Int = 1,
          max: Int = 500, keyboard: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default, password: Boolean = false,
          icon: String? = null, error: String? = null, help: String? = null, counter: Boolean = false) {
    if (icon == "search" && label.isBlank() && singleLine) PTextField(value, onChange, "", modifier, placeholder, icon = icon, max = max, capsule = true, keyboard = keyboard)
    else PTextField(value, onChange, label, modifier, placeholder, icon = icon, error = error, help = help, counter = counter, max = max, singleLine = singleLine,
        minLines = if (singleLine) 1 else maxOf(minLines, 2), keyboard = keyboard, password = password)
}

@Composable
fun Err(text: String?) { if (!text.isNullOrEmpty()) Text(text, color = LocalPal.current.danger, fontWeight = FontWeight.Bold, fontSize = 14.sp) }

/**
 * En-tête de section : emoji Fluent de 26 dp, titre en Unbounded 18, compteur en pastille et action texte à droite.
 * Annoncé comme titre par TalkBack.
 */
@Composable
fun SectionTitle(text: String, emoji: String? = null, count: Any? = null, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier,
                 onScene: Boolean = !LocalInCard.current && !LocalInSheet.current, extra: @Composable RowScope.() -> Unit = {}) {
    // Posé sur le fond : plaque (le contraste ne dépend jamais de la scène)
    if (onScene) { SectionPlaque(text, emoji, count, action, onAction, modifier, extra); return }
    val p = LocalPal.current
    Row(modifier.fillMaxWidth().padding(start = 2.dp, top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (emoji != null) Emoji(emoji, 26.dp)
            Text(text, Modifier.weight(1f, fill = false), fontSize = 18.sp, fontFamily = Unbounded, fontWeight = FontWeight.Bold, lineHeight = 22.sp, color = p.ink)
            if (count != null) Count(count)
            extra()
        }
        if (action != null && onAction != null) Puce(action, onAction)
    }
}

/** Ancien lavis coloré des cartes vedettes : retiré (aplats seulement). Gardé pour la compatibilité des appels. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.washOf(cols: List<Color>, dark: Boolean): Modifier = this
