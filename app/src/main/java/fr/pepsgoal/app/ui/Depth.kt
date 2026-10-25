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
 * Matières de PepsGoal : contenu net et plat (plaques presque opaques, contour d'un pixel, coins continus), verre
 * réservé à la couche de navigation. Peu d'ombres, aucun halo : un rendu sobre et léger pour le téléphone.
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

/** Teinte des ombres : indigo très profond en clair (jamais gris sale), noire en sombre. */
val Pal.shade: Color get() = if (dark) Color(0xFF000000) else Color(0xFF1A1440)

/**
 * Surface des cartes : plaque nette et presque opaque posée sur le fond (comme les listes groupées d'iOS). Aucun flou
 * à calculer : le verre est réservé à la navigation (barre d'onglets, barre du haut, boutons flottants).
 */
val Pal.surface: Color get() = if (transp || contrast) glassStrong.copy(alpha = 1f) else glassStrong.copy(alpha = if (dark) .9f else .93f)

/** Fond réel d'une carte, opaque : base des pastilles pleines posées dessus. */
val Pal.card: Color get() = surface.over(bg)

/** Trait fin du contour des surfaces et des commandes (un pixel physique, à peine visible). */
fun Pal.hairline(): Color = if (contrast) line else if (dark) Color.White.copy(alpha = .09f) else ink.copy(alpha = .07f)

/**
 * Élévation : rien pour le contenu (cartes et commandes restent à plat), une seule ombre douce pour ce qui flotte
 * au-dessus (barre d'onglets, bouton d'action, notifications). [lift] 3 : élément flottant.
 */
fun Modifier.depth(shape: Shape, p: Pal, lift: Int = 2, tint: Color = p.shade): Modifier {
    if (lift < 3 || p.transp || p.oled || !Perf.flags.shadows) return this
    return this.shadow(14.dp, shape, clip = false, ambientColor = tint.copy(alpha = if (p.dark) .5f else .18f), spotColor = tint.copy(alpha = if (p.dark) .6f else .22f))
}

/** Halo coloré : retiré (l'accent se suffit à lui-même). Gardé pour la compatibilité des appels. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.glow(shape: Shape, color: Color, p: Pal, elevation: Dp = 12.dp): Modifier = this

/**
 * Contour net des surfaces : un trait fin d'un pixel, clair en sombre et foncé en clair, qui découpe proprement la
 * plaque sur le fond. [rim] faux : rien.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.sheen(shape: Shape, dark: Boolean, k: Float = 1f, rim: Boolean = true): Modifier = if (!rim || k < .3f) this else this.drawWithCache {
    val sw = 1f
    val inner = shape.createOutline(Size(size.width - sw, size.height - sw), layoutDirection, this)
    val c = if (dark) Color.White.copy(alpha = .09f) else Color(0xFF0D0E2B).copy(alpha = .07f)
    onDrawWithContent { drawContent(); translate(sw / 2, sw / 2) { drawOutline(inner, c, style = Stroke(sw)) } }
}

/**
 * Remplissage des éléments vifs (bouton principal, puce choisie, case validée, pastille d'icône) : couleur pleine avec
 * le dégradé de l'accent PepsGoal, sans reflet ni halo.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.candy(shape: Shape, colors: List<Color>, p: Pal, glow: Boolean = true, elevation: Dp = 12.dp): Modifier {
    val cs = if (colors.size == 1) listOf(colors[0], colors[0]) else colors
    return this.background(Brush.linearGradient(cs, start = Offset.Zero, end = Offset.Infinite), shape)
}

/** Couleur éclaircie (vers le blanc). */
fun Color.lift(k: Float): Color = lerp(this, Color.White, k)
/** Couleur assombrie (vers un indigo très foncé, jamais un gris sale). */
fun Color.deepen(k: Float): Color = lerp(this, Color(0xFF1A0B3D), k)

/** Dégradé discret tiré d'une seule couleur. */
fun candyOf(c: Color): List<Color> = listOf(c.lift(.12f), c)
