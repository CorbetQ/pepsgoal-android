package fr.pepsgoal.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import fr.pepsgoal.app.sys.Perf
import fr.pepsgoal.app.ui.theme.Pal
import fr.pepsgoal.app.ui.theme.over
import kotlin.math.max
import kotlin.math.min

/*
 * Matières de PepsGoal 7 : verre givré éclairé (reflets, liseré qui accroche la lumière), ombres douces en deux
 * couches, commandes « bonbon » brillantes et coins continus. Tout l'habillage s'appuie sur ces briques.
 */

/**
 * Coins continus (« squircle ») : la courbe s'amorce plus tôt et plus doucement qu'un arrondi circulaire, comme les
 * icônes des téléphones récents. Le milieu de chaque coin passe au même endroit qu'un arrondi de rayon [radius].
 * [smooth] : lissage (0 : arrondi classique).
 */
@Immutable
data class Squircle(val radius: Dp, val smooth: Float = .6f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width; val h = size.height
        val p = min(with(density) { radius.toPx() } * (1f + smooth), min(w, h) / 2f)
        if (p <= .5f) return Outline.Rectangle(Rect(0f, 0f, w, h))
        val r = p / (1f + smooth)
        val c = ((.2929f * r - .125f * p) / .375f).coerceIn(0f, p)
        return Outline.Generic(Path().apply {
            moveTo(p, 0f); lineTo(w - p, 0f); cubicTo(w - c, 0f, w, c, w, p)
            lineTo(w, h - p); cubicTo(w, h - c, w - c, h, w - p, h)
            lineTo(p, h); cubicTo(c, h, 0f, h - c, 0f, h - p)
            lineTo(0f, p); cubicTo(0f, c, c, 0f, p, 0f); close()
        })
    }
}

/**
 * Forme des cartes, tuiles et panneaux. Coins continus à partir d'Android 9 (découpe lissée par le moteur de rendu) ;
 * arrondi classique avant, où une découpe de forme libre serait crénelée.
 */
fun smoothShape(radius: Dp): Shape = if (Build.VERSION.SDK_INT >= 28) Squircle(radius) else RoundedCornerShape(radius)

/** Teinte des ombres : indigo profond en clair (ombres colorées, jamais grises), presque noire en sombre. */
val Pal.shade: Color get() = if (dark) Color(0xFF020210) else Color(0xFF34208A)

/** Fond réel d'une carte (verre posé sur le fond), opaque : base des pastilles pleines posées sur le verre. */
val Pal.card: Color get() = glass.over(bg)

/**
 * Ombre douce en deux couches : une ombre de contact courte, et une grande ombre diffuse teintée qui détache l'élément
 * du fond. [lift] : hauteur (1 : commande, 2 : carte, 3 : panneau flottant).
 */
fun Modifier.depth(shape: Shape, p: Pal, lift: Int = 2, tint: Color = p.shade): Modifier {
    if (lift <= 0 || p.transp || p.oled || !Perf.flags.shadows) return this
    val far = when (lift) { 1 -> 8.dp; 2 -> 20.dp; else -> 30.dp }
    val near = when (lift) { 1 -> 1.5.dp; 2 -> 3.dp; else -> 5.dp }
    val k = if (p.dark) 1f else .8f
    return this.shadow(far, shape, clip = false, ambientColor = tint.copy(alpha = .5f * k), spotColor = tint.copy(alpha = .75f * k))
        .shadow(near, shape, clip = false, ambientColor = tint.copy(alpha = .35f * k), spotColor = tint.copy(alpha = .45f * k))
}

/** Halo de couleur sous un élément vif (bouton principal, case validée, pastille). */
fun Modifier.glow(shape: Shape, color: Color, p: Pal, elevation: Dp = 12.dp): Modifier =
    if (p.transp || p.contrast || !Perf.flags.shadows) this else this.shadow(elevation, shape, clip = false, ambientColor = color, spotColor = color)

/**
 * Lumière sur le verre : voile clair en haut, reflet dans le coin supérieur gauche, léger assombrissement vers le bas
 * (volume), puis liseré dégradé qui accroche la lumière sur le bord haut et, plus discrètement, sur le bord bas.
 * [k] : intensité ; [rim] faux : pas de liseré.
 */
fun Modifier.sheen(shape: Shape, dark: Boolean, k: Float = 1f, rim: Boolean = true): Modifier = this.drawWithCache {
    val w = size.width; val h = size.height
    val o = shape.createOutline(size, layoutDirection, this)
    val topH = min(h, 150.dp.toPx())
    val top = Brush.verticalGradient(0f to Color.White.copy(alpha = (if (dark) .08f else .4f) * k), 1f to Color.Transparent, startY = 0f, endY = topH)
    val glintR = max(min(w, 260.dp.toPx()) * .62f, 1f)
    val glint = Brush.radialGradient(listOf(Color.White.copy(alpha = (if (dark) .1f else .34f) * k), Color.Transparent), center = Offset(w * .08f, 0f), radius = glintR)
    val base = Brush.verticalGradient(0f to Color.Transparent, 1f to (if (dark) Color.Black.copy(alpha = .18f * k) else Color(0xFF34208A).copy(alpha = .05f * k)), startY = h * .5f, endY = h)
    val sw = 1.2.dp.toPx()
    val inner = shape.createOutline(Size(w - sw, h - sw), layoutDirection, this)
    val edge = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) .4f else .98f), .28f to Color.White.copy(alpha = if (dark) .12f else .55f),
        .62f to Color.White.copy(alpha = if (dark) .03f else .14f), 1f to Color.White.copy(alpha = if (dark) .14f else .6f),
        start = Offset(0f, 0f), end = Offset(w * .55f, h))
    onDrawWithContent {
        drawOutline(o, top); drawOutline(o, glint); drawOutline(o, base)
        drawContent()
        if (rim) translate(sw / 2, sw / 2) { drawOutline(inner, edge, style = Stroke(sw)) }
    }
}

/**
 * Matière « bonbon » des éléments vifs (bouton principal, puce choisie, pastille d'icône, case validée) : dégradé
 * intense, reflet brillant en haut, ombre interne en bas et liseré clair, comme les emojis 3D. [glow] : halo coloré.
 */
fun Modifier.candy(shape: Shape, colors: List<Color>, p: Pal, glow: Boolean = true, elevation: Dp = 12.dp): Modifier {
    val cs = if (colors.size == 1) listOf(colors[0], colors[0]) else colors
    val mid = cs[cs.size / 2]
    return this.then(if (glow) Modifier.glow(shape, mid, p, elevation) else Modifier)
        .background(Brush.linearGradient(cs, start = Offset.Zero, end = Offset.Infinite), shape)
        .then(if (p.contrast) Modifier else Modifier.drawWithCache {
            val w = size.width; val h = size.height
            val o = shape.createOutline(size, layoutDirection, this)
            val gloss = Brush.verticalGradient(0f to Color.White.copy(alpha = .36f), .55f to Color.White.copy(alpha = 0f), endY = h)
            val shadeB = Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xFF1A0B3D).copy(alpha = .16f), startY = h * .55f, endY = h)
            val sw = 1.dp.toPx()
            val inner = shape.createOutline(Size(w - sw, h - sw), layoutDirection, this)
            val rimB = Brush.verticalGradient(0f to Color.White.copy(alpha = .7f), .5f to Color.White.copy(alpha = .08f), 1f to Color.White.copy(alpha = .22f), endY = h)
            onDrawWithContent {
                drawOutline(o, gloss); drawOutline(o, shadeB)
                drawContent()
                translate(sw / 2, sw / 2) { drawOutline(inner, rimB, style = Stroke(sw)) }
            }
        })
}

/** Couleur éclaircie (vers le blanc) : départ lumineux des dégradés « bonbon ». */
fun Color.lift(k: Float): Color = lerp(this, Color.White, k)
/** Couleur assombrie (vers un indigo très foncé, jamais un gris sale). */
fun Color.deepen(k: Float): Color = lerp(this, Color(0xFF1A0B3D), k)

/** Dégradé « bonbon » tiré d'une seule couleur : clair en haut à gauche, plus dense en bas à droite. */
fun candyOf(c: Color): List<Color> = listOf(c.lift(.28f), c, c.deepen(.12f))
