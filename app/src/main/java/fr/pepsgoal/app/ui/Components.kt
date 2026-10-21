package fr.pepsgoal.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeInputScale
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.PressInteraction
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
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

@Composable
fun Flame(size: Dp, modifier: Modifier = Modifier, dim: Boolean = false, colors: List<Color>? = null) {
    val p = LocalPal.current
    val cs = colors ?: listOf(p.pink, p.flame, p.flame2)
    val outer = remember { PathParser().parsePathString(FLAME_OUT).toPath() }
    val inner = remember { PathParser().parsePathString(FLAME_IN).toPath() }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(outer, Brush.verticalGradient(cs, startY = 22f, endY = 1f), alpha = if (dim) .35f else 1f)
            drawPath(inner, Brush.verticalGradient(listOf(cs.last(), Color(0xFFFFF6CC)), startY = 22f, endY = 12f), alpha = if (dim) .35f else 1f)
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
fun Emoji(e: String, size: Dp, modifier: Modifier = Modifier) {
    val key = remember(e) { emojiKey(e) }
    val sp = with(LocalDensity.current) { (size * .82f).toSp() }
    if (key == null) { Text(e, fontSize = sp, modifier = modifier); return }
    SubcomposeAsyncImage(model = "file:///android_asset/emoji/$key.webp", contentDescription = e, modifier = modifier.size(size), contentScale = ContentScale.Fit,
        error = { Box(Modifier.size(size), contentAlignment = Alignment.Center) { Text(e, fontSize = sp, textAlign = TextAlign.Center) } })
}

/* ------------------------------------------------------------------ cartes, boutons, puces */
/** Source du flou des panneaux en verre (null : pas de flou, ex. dans une fenêtre du bas). */
val LocalHaze = staticCompositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }

/** Espace occupé par la barre du haut et la barre de navigation flottantes (le contenu défile dessous). */
val LocalBars = compositionLocalOf { PaddingValues(0.dp) }

/** Contour des commandes (puces, boutons-icônes, sélecteurs) : visible sur le verre clair comme sombre. */
fun ctlEdge(p: fr.pepsgoal.app.ui.theme.Pal) = if (p.contrast) p.line else p.ink.copy(alpha = if (p.dark) .24f else .18f)

/**
 * Apparition en cascade des cartes : à l'ouverture d'un onglet ou d'une page, les cartes arrivent l'une après
 * l'autre (léger décalage), en glissant vers le haut avec un rebond.
 */
object Stagger {
    private var t0 = 0L; private var n = 0
    fun reset() { t0 = android.os.SystemClock.uptimeMillis(); n = 0 }
    fun next(): Long = if (android.os.SystemClock.uptimeMillis() - t0 < 700) (n++ * 55L).coerceAtMost(385L) else 0L
}

@Composable
fun Modifier.enter(): Modifier {
    val motionOff = LocalPal.current.motion
    val a = remember { Animatable(if (motionOff) 1f else 0f) }
    LaunchedEffect(Unit) { if (a.value < 1f) { kotlinx.coroutines.delay(Stagger.next()); a.animateTo(1f, spring(dampingRatio = .62f, stiffness = 230f)) } }
    return this.graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f); translationY = (1f - v) * 34.dp.toPx()
        val s = .93f + .07f * v; scaleX = s; scaleY = s
    }
}

/**
 * Fond coloré des pages secondaires (réglages, messages, historique…) : halos de couleur fixes, dessinés une seule
 * fois (aucun coût d'animation), qui transparaissent à travers les cartes en verre.
 */
@Composable
fun PageBackdrop(c1: Color, c2: Color, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Canvas(modifier.fillMaxSize()) {
        drawRect(p.bg)
        if (p.contrast || p.transp) return@Canvas
        val w = size.width; val h = size.height; val k = if (p.dark) .8f else 1f
        fun halo(c: Color, x: Float, y: Float, r: Float, a: Float) = drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a * k), c.copy(alpha = a * k * .35f), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
        halo(c1, w * .08f, h * .02f, w * 1.05f, .55f)
        halo(c2, w * 1.02f, h * .3f, w * .85f, .38f)
        halo(androidx.compose.ui.graphics.lerp(c1, c2, .5f), w * .25f, h * 1.02f, w * .95f, .32f)
        drawRect(Brush.verticalGradient(0f to c1.copy(alpha = .18f * k), .35f to Color.Transparent))
    }
}

/** Pastille d'icône colorée (dégradé, icône foncée) : en-têtes et listes des pages secondaires. */
@Composable
fun IconTile(icon: String, c1: Color, c2: Color, size: Dp = 40.dp, radius: Dp = 13.dp) {
    val p = LocalPal.current
    Box(Modifier.size(size).shadow(if (p.transp) 0.dp else 8.dp, RoundedCornerShape(radius), ambientColor = c1, spotColor = c1).clip(RoundedCornerShape(radius))
        .background(if (p.contrast) SolidColor(p.ink) else Brush.linearGradient(listOf(c1, c2))), contentAlignment = Alignment.Center) {
        Pi(icon, size = size * .5f, tint = if (p.contrast) p.bg else Color(0xFF10102A))
    }
}

/**
 * Voile givré progressif en haut ou en bas d'un écran (derrière une barre flottante) : flou maximal près du bord,
 * qui s'estompe en douceur vers le contenu, avec un voile laiteux (clair) ou nuit (sombre) dégradé dans le même sens.
 * [cover] : hauteur de la barre à couvrir ; [haze] : source du flou (le contenu qui défile dessous).
 */
@Composable
fun EdgeFrost(modifier: Modifier, top: Boolean, cover: Dp, haze: dev.chrisbanes.haze.HazeState? = LocalHaze.current) {
    val p = LocalPal.current; val d = LocalDensity.current
    val inset = (if (top) WindowInsets.statusBars else WindowInsets.navigationBars).asPaddingValues().let { if (top) it.calculateTopPadding() else it.calculateBottomPadding() }
    val h = maxOf(cover, inset + 48.dp) + 18.dp
    // Part du voile entièrement floutée (côté bord), puis fondu progressif jusqu'au contenu
    val full = ((cover / h) * .45f).coerceIn(.18f, .55f)
    fun smooth(u: Float) = u.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
    // Courbe d'opacité du bord (1) vers le contenu (0), échantillonnée finement : aucun bord net
    fun level(fromEdge: Float) = if (fromEdge <= full) 1f else 1f - smooth((fromEdge - full) / (1 - full))
    val n = 16
    fun grad(color: (Float) -> Color) = Brush.verticalGradient(*(0..n).map { i -> val t = i / n.toFloat(); t to color(level(if (top) t else 1 - t)) }.toTypedArray())
    // Voile très léger : c'est le flou qui fait disparaître le contenu, le voile ne fait que l'adoucir
    val milk = if (p.dark) p.bg else Color.White
    val k = if (p.dark) .4f else .32f
    val veil = grad { a -> milk.copy(alpha = k * a * a) }
    val box = modifier.fillMaxWidth().height(h)
    if (haze != null && !p.transp && !p.contrast) {
        // Flou progressif (rayon variable) : maximal au bord, nul côté contenu. Le dégradé s'étend sur toute la
        // hauteur de la zone (fin « infinie » = bas de la zone, quelle que soit la réduction de l'image floutée)
        val ease = androidx.compose.animation.core.Easing { t -> level(if (top) t else 1 - t) }
        Box(box.hazeEffect(haze, HazeStyle(backgroundColor = p.bg, tints = emptyList(), blurRadius = 32.dp, noiseFactor = 0f, fallbackTint = HazeTint(p.glassStrong))) {
            inputScale = HazeInputScale.Fixed(.5f)
            progressive = dev.chrisbanes.haze.HazeProgressive.verticalGradient(startY = 0f, startIntensity = 0f, endY = Float.POSITIVE_INFINITY, endIntensity = 1f, easing = ease)
        }.background(veil))
    } else Box(box.background(grad { a -> milk.copy(alpha = .9f * a) }))
}

/** Ressorts communs : réponses vives avec un léger rebond. */
object Springs {
    val pop = androidx.compose.animation.core.spring<Float>(dampingRatio = .42f, stiffness = 520f)
    val bar = androidx.compose.animation.core.spring<Float>(dampingRatio = .62f, stiffness = 140f)
}

/**
 * Effet « ressort » à l'appui : l'élément s'enfonce dès le toucher puis rebondit en le relâchant
 * (même pour un appui très bref). À placer en tête de la chaîne de modificateurs, avec le même
 * [inter] passé au `clickable`.
 */
@Composable
fun Modifier.bounce(inter: MutableInteractionSource, pressed: Float = .94f): Modifier {
    val motionOff = LocalPal.current.motion
    val sc = remember { Animatable(1f) }
    LaunchedEffect(inter, motionOff) {
        if (motionOff) return@LaunchedEffect
        inter.interactions.collectLatest { i ->
            when (i) {
                is PressInteraction.Press -> sc.animateTo(pressed, spring(dampingRatio = .8f, stiffness = 1600f))
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    if (sc.value > pressed + .015f) sc.animateTo(pressed, tween(60))
                    sc.animateTo(1f, spring(dampingRatio = .32f, stiffness = 420f))
                }
            }
        }
    }
    return this.graphicsLayer { scaleX = sc.value; scaleY = sc.value }
}

/** Zone cliquable avec effet ressort et ondulation (à placer après le clip/fond de l'élément). */
@Composable
fun Modifier.springClick(inter: MutableInteractionSource, enabled: Boolean = true, label: String? = null, role: Role? = null, onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = inter, indication = androidx.compose.foundation.LocalIndication.current, enabled = enabled, onClickLabel = label, role = role, onClick = onClick)

/** Fond « verre givré » : arrière-plan flouté et teinté (backdrop-filter de la version web), sinon simple teinte. */
@Composable
fun Modifier.glassBg(shape: Shape, strong: Boolean = false): Modifier {
    val p = LocalPal.current; val h = LocalHaze.current
    val tint = if (strong) p.glassStrong else p.glass
    if (h == null || p.transp || p.contrast || p.oled) return this.clip(shape).background(tint)
    return this.clip(shape).hazeEffect(h, HazeStyle(backgroundColor = p.bg, tints = listOf(HazeTint(tint)), blurRadius = (p.blur / 3.6f).dp, noiseFactor = 0f, fallbackTint = HazeTint(p.glassStrong))) {
        // Flou calculé sur une image réduite de moitié : même rendu, bien moins de travail pour le GPU
        inputScale = HazeInputScale.Fixed(.5f)
    }
}

@Composable
fun Glass(modifier: Modifier = Modifier, strong: Boolean = false, radius: Dp = 24.dp, pad: Dp = 18.dp, border: Color? = null, fill: Boolean = true, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(radius)
    val inter = remember { MutableInteractionSource() }
    Column(
        // Toute la largeur disponible par défaut : une carte au contenu court reste alignée sur les autres
        (if (fill) Modifier.fillMaxWidth() else Modifier).then(modifier).enter().then(if (onClick != null) Modifier.bounce(inter, .97f) else Modifier)
            .shadow(if (p.oled || p.transp) 0.dp else 18.dp, shape, ambientColor = Color(0x4428186E), spotColor = Color(0x5528186E))
            .glassBg(shape, strong)
            // Profondeur : reflet en haut de la carte et liseré lumineux dégradé (plus vif en haut, discret en bas)
            .then(if (p.contrast || p.transp) Modifier else Modifier.background(Brush.verticalGradient(0f to Color.White.copy(alpha = if (p.dark) .07f else .3f), .45f to Color.Transparent)))
            .border(1.dp, if (border != null) SolidColor(border) else if (p.contrast) SolidColor(p.line) else Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.dark) .3f else .95f), p.glassEdge.copy(alpha = p.glassEdge.alpha * .35f))), shape)
            .then(if (onClick != null) Modifier.springClick(inter, onClick = onClick) else Modifier).padding(pad),
        content = content
    )
}

@Composable
fun CardHead(title: String, modifier: Modifier = Modifier, right: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        right()
    }
}

@Composable
fun Count(n: Any) = Text("$n", fontWeight = FontWeight.ExtraBold, color = LocalPal.current.ink2)

@Composable
fun Eyebrow(text: String, color: Color = LocalPal.current.ink2) = Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, color = color)

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, small: Boolean = false, center: Boolean = false, maxLines: Int = Int.MAX_VALUE) =
    Text(text, modifier, color = LocalPal.current.ink2, fontSize = if (small) 13.sp else 15.sp, textAlign = if (center) TextAlign.Center else null, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/** Bouton arrondi : principal (dégradé de l'accent), normal, discret ou danger. */
@Composable
fun PButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, kind: String = "normal", small: Boolean = false,
    enabled: Boolean = true, leading: (@Composable () -> Unit)? = null
) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val shape = CircleShape
    val bg: Modifier = when (kind) {
        "primary" -> Modifier.background(if (p.contrast) SolidColor(p.ink) else p.accent, shape)
        "ghost", "danger" -> Modifier
        else -> Modifier.background(p.glassStrong, shape).border(1.dp, ctlEdge(p), shape)
    }
    val fg = when (kind) { "primary" -> if (p.contrast) p.bg else p.onAccent; "ghost" -> p.ink2; "danger" -> p.danger; else -> p.ink }
    Row(
        modifier.bounce(inter, .92f).alpha(if (enabled) 1f else .45f).clip(shape).then(bg)
            .then(if (kind == "danger") Modifier.border(1.dp, p.danger.copy(alpha = .4f), shape) else Modifier)
            .clickable(enabled = enabled, interactionSource = inter, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 18.dp, vertical = if (small) 8.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        leading?.invoke()
        if (icon != null) Pi(icon, size = 18.dp, tint = fg)
        if (text.isNotEmpty()) Text(text, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = if (small) 13.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val LocalIndication = androidx.compose.foundation.LocalIndication
private fun Modifier.alpha(a: Float) = this.graphicsLayer { alpha = a }

@Composable
fun IconBtn(icon: String, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, plain: Boolean = false, badge: Int = 0, tint: Color? = null, size: Dp = 42.dp) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    Box(modifier) {
        Box(
            Modifier.bounce(inter, .86f).size(if (plain) size - 6.dp else size).clip(CircleShape).then(if (plain) Modifier else Modifier.background(p.glassStrong).border(1.dp, ctlEdge(p), CircleShape))
                .springClick(inter, label = desc, onClick = onClick), contentAlignment = Alignment.Center
        ) { Pi(icon, size = 20.dp, tint = tint ?: if (plain) p.ink2 else p.ink) }
        if (badge > 0) Badge(badge, Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp))
    }
}

@Composable
fun Badge(n: Int, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Box(modifier.defaultMinSize(20.dp, 20.dp).clip(CircleShape).background(p.pink).border(2.dp, p.bg, CircleShape).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
        Text(if (n > 9) "9+" else "$n", color = p.onAccent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun Chip(text: String, icon: String? = null, color: Color? = null, ink: Color? = null, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val c = color ?: p.ink3
    Row(modifier.clip(CircleShape).background(c.copy(alpha = .2f)).padding(horizontal = 9.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) Pi(icon, size = 13.dp, tint = ink ?: p.ink2)
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = ink ?: p.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Tuiles de statistiques (valeur + libellé) en grille régulière : toutes de la même taille, libellés jamais coupés. */
@Composable
fun StatGrid(items: List<Pair<String, String>>, columns: Int = 2) {
    val p = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (v, l) ->
                    Glass(Modifier.weight(1f).fillMaxHeight(), pad = 14.dp, radius = 18.dp) {
                        FitText(v, size = 24, min = 12, weight = FontWeight.ExtraBold, family = Unbounded)
                        Text(l, fontSize = 13.sp, color = p.ink2, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun TypeChip(type: String) {
    val p = LocalPal.current; val tc = p.type(type); val info = fr.pepsgoal.core.typeInfo(type)
    Chip(info.label, info.icon, tc.c, tc.ink)
}

/** Puce de filtre (enfoncée = sélectionnée). */
@Composable
fun FChip(text: String, on: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null, dashed: Boolean = false) {
    val p = LocalPal.current
    val sc by animateFloatAsState(if (on && !p.motion) 1.05f else 1f, spring(dampingRatio = .35f, stiffness = 520f), label = "chip")
    val inter = remember { MutableInteractionSource() }
    Row(
        Modifier.bounce(inter, .9f).scale(sc).clip(CircleShape).background(if (on) (if (p.contrast) SolidColor(p.ink) else p.accent) else SolidColor(if (dashed) Color.Transparent else p.glassStrong))
            .border(1.dp, if (on) Color.Transparent else ctlEdge(p), CircleShape).springClick(inter, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        leading?.invoke()
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = if (on) (if (p.contrast) p.bg else p.onAccent) else p.ink)
    }
}

/** Barre de progression aux couleurs du type (ou de l'accent). */
@Composable
fun Bar(pct: Double, modifier: Modifier = Modifier, color: Color? = null, height: Dp = 8.dp) {
    val p = LocalPal.current
    val target = (pct / 100.0).coerceIn(0.0, 1.0).toFloat()
    val v by animateFloatAsState(target, if (p.motion) tween(0) else Springs.bar, label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(p.ink.copy(alpha = .09f))) {
        Box(Modifier.fillMaxWidth(v).fillMaxHeight().clip(CircleShape).background(if (color != null) SolidColor(color) else p.accent))
    }
}

/** Anneau de la journée : validations faites sur prévues. */
@Composable
fun Ring(done: Int, total: Int, size: Dp = 108.dp) {
    val p = LocalPal.current
    val pct = if (total > 0) done.toFloat() / total else 0f
    val v by animateFloatAsState(pct, if (p.motion) tween(0) else Springs.bar, label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = this.size.minDimension * .11f
            val r = Size(this.size.width - sw, this.size.height - sw)
            drawArc(p.ink.copy(alpha = .1f), 0f, 360f, false, Offset(sw / 2, sw / 2), r, style = Stroke(sw))
            drawArc(Brush.sweepGradient(listOf(p.flame2, p.flame, p.pink, p.flame2)), -90f, 360f * v, false, Offset(sw / 2, sw / 2), r, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text("$done/$total", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = p.ink)
    }
}

/* ------------------------------------------------------------------ avatars */
fun accentBrush(i: Int): Brush { val a = (ACCENTS.getOrNull(i) ?: ACCENTS[0]).c.map(::hex); return Brush.linearGradient(listOf(a[0], a[1], a[2])) }

@Composable
fun Av(u: User?, size: Dp = 40.dp, a: Avatar? = null, modifier: Modifier = Modifier) {
    val av = a ?: u?.avatar ?: Avatar()
    val ink = Color(0xFF10102A)
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

/** Anneau de niveau animé autour de l'avatar (couleur selon le niveau). */
@Composable
fun LevelRing(level: Int, size: Dp, content: @Composable () -> Unit) {
    val p = LocalPal.current
    val cols = when (level) { 1 -> listOf(hex("#FFB000"), hex("#FFE45C")); 2 -> listOf(hex("#FF7A3D"), hex("#FFB000")); 3 -> listOf(p.flame, p.flame2); 4 -> listOf(p.pink, p.flame, p.flame2); 5 -> listOf(hex("#8C7DFF"), p.pink, p.flame); else -> listOf(hex("#22D3FF"), hex("#B48CFF"), p.pink, p.flame2) }
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "lv")
    val rot by inf.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween(if (p.motion) 1_000_000 else 9000, easing = androidx.compose.animation.core.LinearEasing)), label = "rot")
    // `size` : taille de l'avatar ; l'anneau l'entoure à 2 dp (comme .lv-ring de la version web)
    Box(Modifier.size(size + 10.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = rot }) { val w = 3.dp.toPx(); drawCircle(Brush.sweepGradient(cols + cols.first()), radius = this.size.minDimension / 2 - w / 2, style = Stroke(w)) }
        content()
    }
}

/* ------------------------------------------------------------------ texte enrichi (**gras**) */
fun rich(s: String, bold: Color? = null): AnnotatedString = buildAnnotatedString {
    val parts = s.split("**")
    parts.forEachIndexed { i, t -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = bold ?: Color.Unspecified)) { append(t) } else append(t) }
}

@Composable
fun Empty(title: String, text: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.ExtraBold, color = LocalPal.current.ink, textAlign = TextAlign.Center)
        Muted(text, center = true)
        action?.let { Spacer(Modifier.height(6.dp)); it() }
    }
}

@Composable
fun Note(text: String, icon: String = "bell", modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(BorderStroke(1.5.dp, p.line), RoundedCornerShape(16.dp)).background(p.glass).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pi(icon, size = 18.dp, tint = p.ink2)
        Text(rich(text), color = p.ink2, fontSize = 14.sp)
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

@Composable
fun PSwitch(on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val p = LocalPal.current
    Switch(on, onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = p.flame, checkedThumbColor = p.onAccent, uncheckedTrackColor = p.ink.copy(alpha = .12f)))
}

/** Choix segmenté (boutons radio côte à côte). */
@Composable
fun <T> Seg(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit, modifier: Modifier = Modifier, icons: List<String>? = null) {
    val p = LocalPal.current
    FlowRowCompat(modifier.clip(RoundedCornerShape(18.dp)).background(p.ink.copy(alpha = if (p.dark) .1f else .07f)).padding(4.dp)) {
        options.forEachIndexed { i, (k, l) ->
            val on = k == value
            val inter = remember { MutableInteractionSource() }
            Row(Modifier.bounce(inter, .9f).clip(RoundedCornerShape(14.dp)).background(if (on) p.glassStrong else Color.Transparent).then(if (on) Modifier.border(1.dp, ctlEdge(p), RoundedCornerShape(14.dp)) else Modifier).springClick(inter) { onChange(k) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                icons?.getOrNull(i)?.let { Pi(it, size = 16.dp, tint = if (on) p.ink else p.ink2) }
                Text(l, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = if (on) p.ink else p.ink2)
            }
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
fun FitText(text: String, modifier: Modifier = Modifier, size: Int = 14, min: Int = 8, color: Color = Color.Unspecified, weight: FontWeight = FontWeight.Bold, family: androidx.compose.ui.text.font.FontFamily? = null) {
    val c = if (color == Color.Unspecified) LocalContentColor.current else color
    androidx.compose.foundation.text.BasicText(text, modifier, style = LocalTextStyle.current.copy(color = c, fontSize = size.sp, fontWeight = weight, fontFamily = family ?: LocalTextStyle.current.fontFamily),
        maxLines = 1, softWrap = false, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(min.sp, size.sp, 0.5.sp))
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, placeholder: String = "", singleLine: Boolean = true, minLines: Int = 1,
          max: Int = 500, keyboard: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default, password: Boolean = false) {
    val p = LocalPal.current
    OutlinedTextField(value, { if (it.length <= max) onChange(it) }, modifier.fillMaxWidth(), label = { Text(label) }, placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = p.ink3) }) else null,
        singleLine = singleLine, minLines = minLines, keyboardOptions = keyboard, shape = RoundedCornerShape(14.dp),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.focus, unfocusedBorderColor = p.line, focusedContainerColor = p.glassStrong, unfocusedContainerColor = p.glassStrong))
}

@Composable
fun Err(text: String?) { if (!text.isNullOrEmpty()) Text(text, color = LocalPal.current.danger, fontWeight = FontWeight.Bold, fontSize = 14.sp) }

@Composable
fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), modifier = Modifier.padding(bottom = 8.dp, top = 4.dp))
