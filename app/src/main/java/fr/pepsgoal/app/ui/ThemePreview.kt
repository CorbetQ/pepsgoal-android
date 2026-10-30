package fr.pepsgoal.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.SHOP_MODES

/** Couleurs d'un thème pour son aperçu : fond, texte, surface des cartes (pleine) et collines de décor. */
class ThemeLook(val bg: List<Color>, val ink: Color, val glass: Color, val blobs: List<Color> = emptyList(), val other: ThemeLook? = null)

private val LIGHT = ThemeLook(listOf(hex("#FFF7F0"), hex("#FFEFE4")), hex("#2A1420"), Color.White, listOf(hex("#FF3D96"), hex("#FFB000")))
private val DARK = ThemeLook(listOf(hex("#170F1C"), hex("#120B16")), hex("#FFF4EC"), hex("#241A2B"), listOf(hex("#FF4A12"), hex("#FF3D96")))

/** Aperçu d'un thème de base (system, light, dark, oled, weather) ou d'une ambiance (« m:<clé> »). */
fun themeLook(key: String): ThemeLook = when (key) {
    "light" -> LIGHT
    "dark" -> DARK
    "oled" -> ThemeLook(listOf(Color.Black, Color.Black), hex("#FFF4EC"), hex("#160F18"))
    "weather" -> ThemeLook(listOf(hex("#3E7BFA"), hex("#8EC5FF"), hex("#FFD59E")), hex("#2A1420"), Color.White, listOf(hex("#FFFFFF"), hex("#FFD59E")))
    "system" -> ThemeLook(LIGHT.bg, LIGHT.ink, LIGHT.glass, LIGHT.blobs, other = DARK)
    else -> fr.pepsgoal.core.shopMode(key)?.let { m ->
        val bg = hex(m.bg)
        ThemeLook(listOf(androidx.compose.ui.graphics.lerp(bg, hex(m.glass), .25f), bg), hex(m.ink), androidx.compose.ui.graphics.lerp(bg, hex(m.glass).copy(alpha = 1f), .85f), m.blobs.map(::hex))
    } ?: DARK
}

/** Mini-écran dessiné : fond, halos, une carte en verre avec deux lignes de texte, une pastille d'accent. */
@Composable
fun ThemePreview(look: ThemeLook, modifier: Modifier, accent: List<Color>? = null) {
    val p = LocalPal.current
    val ac = accent ?: listOf(p.flame2, p.flame, p.pink)
    Canvas(modifier) {
        drawLook(look, ac)
        // Auto : moitié claire, moitié sombre, séparées en diagonale
        look.other?.let { o -> clipPath(Path().apply { moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }) { drawLook(o, ac) } }
    }
}

private fun DrawScope.drawLook(l: ThemeLook, ac: List<Color>) {
    val w = size.width; val h = size.height; val r = CornerRadius(6.dp.toPx())
    val bg = l.bg.last(); val edge = androidx.compose.ui.graphics.lerp(l.glass, l.ink, .22f)
    drawRect(Brush.verticalGradient(l.bg))
    // Collines pleines (décor), sans halo
    l.blobs.forEachIndexed { i, c -> val cc = androidx.compose.ui.graphics.lerp(bg, c, .55f); drawCircle(cc, w * .55f, Offset(w * (if (i % 2 == 0) .1f else .95f), h * (1.02f - .05f * i))) }
    // Carte pleine à marche et lignes de texte
    val pad = w * .1f; val st = 2.dp.toPx()
    drawRoundRect(edge, Offset(pad, h * .18f + st), Size(w - pad * 2, h * .34f), r)
    drawRoundRect(l.glass, Offset(pad, h * .18f), Size(w - pad * 2, h * .34f), r)
    drawRoundRect(l.ink, Offset(pad * 1.8f, h * .25f), Size(w * .45f, h * .05f), CornerRadius(h))
    drawRoundRect(androidx.compose.ui.graphics.lerp(l.glass, l.ink, .5f), Offset(pad * 1.8f, h * .34f), Size(w * .3f, h * .04f), CornerRadius(h))
    drawRoundRect(androidx.compose.ui.graphics.lerp(l.glass, l.ink, .12f), Offset(pad * 1.8f, h * .43f), Size(w - pad * 3.6f, h * .03f), CornerRadius(h))
    drawRoundRect(Brush.horizontalGradient(ac), Offset(pad * 1.8f, h * .43f), Size((w - pad * 3.6f) * .62f, h * .03f), CornerRadius(h))
    // Deux lignes d'objectifs et le bouton d'accent
    for (i in 0..1) { drawRoundRect(edge, Offset(pad, h * (.58f + i * .13f) + st), Size(w - pad * 2, h * .1f), r); drawRoundRect(l.glass, Offset(pad, h * (.58f + i * .13f)), Size(w - pad * 2, h * .1f), r) }
    drawRoundRect(Brush.horizontalGradient(ac), Offset(pad, h * .84f), Size(w - pad * 2, h * .09f), CornerRadius(h))
}

/** Tuile de choix d'un thème : aperçu, coche si choisi, nom en dessous. */
@Composable
fun ThemeCard(key: String, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 112.dp, badge: String? = null) {
    val p = LocalPal.current
    val sc by androidx.compose.animation.core.animateFloatAsState(if (selected && !p.motion) 1f else .95f, Motion.emphasis(), label = "themeCard")
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val press = rememberPress(inter)
    Column(modifier.springClick(inter, label = label) { onClick() }.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(height + 4.dp).graphicsLayer { scaleX = sc; scaleY = sc }.chunk(p.surface, if (selected) p.focusEdge else p.edge, 18.dp, 4.dp, if (selected) 3.dp else 2.dp, press)
            .padding(if (selected) 3.dp else 2.dp).clip(RoundedCornerShape(15.dp))) {
            ThemePreview(themeLook(key), Modifier.fillMaxSize())
            if (badge != null) Text(badge, Modifier.align(Alignment.TopEnd).padding(5.dp).background(fr.pepsgoal.app.ui.theme.PLUM, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                color = fr.pepsgoal.app.ui.theme.CREAM, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            if (selected) Box(Modifier.align(Alignment.BottomEnd).padding(5.dp).size(24.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Pi("check", size = 15.dp, tint = fr.pepsgoal.app.ui.theme.PLUM) }
        }
        FitText(tr(label), Modifier.padding(top = 6.dp), size = 12, min = 8, color = if (selected) p.ink else p.ink2, weight = if (selected) FontWeight.ExtraBold else FontWeight.Bold)
    }
}
