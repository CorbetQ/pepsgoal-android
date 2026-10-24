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

/** Couleurs d'un thème pour son aperçu : fond (dégradé), texte, verre des cartes et halos. */
class ThemeLook(val bg: List<Color>, val ink: Color, val glass: Color, val blobs: List<Color> = emptyList(), val other: ThemeLook? = null)

private val LIGHT = ThemeLook(listOf(hex("#ECEEFF"), hex("#E2E6FF")), hex("#0D0E2B"), Color.White.copy(alpha = .8f), listOf(hex("#FF7AD9"), hex("#22D3FF")))
private val DARK = ThemeLook(listOf(hex("#0C0E2A"), hex("#07081A")), hex("#F4F5FF"), Color(31, 34, 80, 220), listOf(hex("#8C7DFF"), hex("#FF4FA3")))

/** Aperçu d'un thème de base (system, light, dark, oled, weather) ou d'une ambiance (« m:<clé> »). */
fun themeLook(key: String): ThemeLook = when (key) {
    "light" -> LIGHT
    "dark" -> DARK
    "oled" -> ThemeLook(listOf(Color.Black, Color.Black), Color.White, Color(22, 22, 26, 240))
    "weather" -> ThemeLook(listOf(hex("#3E7BFA"), hex("#8EC5FF"), hex("#FFD59E")), Color.White, Color.White.copy(alpha = .32f), listOf(Color.White.copy(alpha = .7f)))
    "system" -> ThemeLook(LIGHT.bg, LIGHT.ink, LIGHT.glass, LIGHT.blobs, other = DARK)
    else -> fr.pepsgoal.core.shopMode(key)?.let { m ->
        val bg = hex(m.bg)
        ThemeLook(listOf(androidx.compose.ui.graphics.lerp(bg, hex(m.glass), .25f), bg), hex(m.ink), hex(m.glass).copy(alpha = .88f), m.blobs.map(::hex))
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
    val w = size.width; val h = size.height; val r = CornerRadius(8.dp.toPx())
    drawRect(Brush.verticalGradient(l.bg))
    l.blobs.forEachIndexed { i, c -> drawCircle(Brush.radialGradient(listOf(c.copy(alpha = .45f), Color.Transparent), Offset(w * (if (i % 2 == 0) .15f else .9f), h * (.2f + .5f * i)), w * .55f), w * .55f, Offset(w * (if (i % 2 == 0) .15f else .9f), h * (.2f + .5f * i))) }
    // Carte en verre et lignes de texte
    val pad = w * .1f
    drawRoundRect(l.glass, Offset(pad, h * .18f), Size(w - pad * 2, h * .34f), r)
    drawRoundRect(l.ink.copy(alpha = .85f), Offset(pad * 1.8f, h * .25f), Size(w * .45f, h * .05f), CornerRadius(h))
    drawRoundRect(l.ink.copy(alpha = .4f), Offset(pad * 1.8f, h * .34f), Size(w * .3f, h * .04f), CornerRadius(h))
    drawRoundRect(l.ink.copy(alpha = .12f), Offset(pad * 1.8f, h * .43f), Size(w - pad * 3.6f, h * .03f), CornerRadius(h))
    drawRoundRect(Brush.horizontalGradient(ac), Offset(pad * 1.8f, h * .43f), Size((w - pad * 3.6f) * .62f, h * .03f), CornerRadius(h))
    // Deux lignes d'objectifs et le bouton d'accent
    for (i in 0..1) drawRoundRect(l.glass.copy(alpha = l.glass.alpha * .75f), Offset(pad, h * (.58f + i * .13f)), Size(w - pad * 2, h * .1f), r)
    drawRoundRect(Brush.horizontalGradient(ac), Offset(pad, h * .84f), Size(w - pad * 2, h * .09f), CornerRadius(h))
}

/** Tuile de choix d'un thème : aperçu, coche si choisi, nom en dessous. */
@Composable
fun ThemeCard(key: String, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 112.dp, badge: String? = null) {
    val p = LocalPal.current
    val sc by androidx.compose.animation.core.animateFloatAsState(if (selected && !p.motion) 1f else .95f, Motion.emphasis(), label = "themeCard")
    Column(modifier.clip(smoothShape(18.dp)).clickable(onClickLabel = label, onClick = { Feedback.tap(); onClick() }).padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = smoothShape(16.dp)
        Box(Modifier.fillMaxWidth().height(height).graphicsLayer { scaleX = sc; scaleY = sc }.clip(shape).border(if (selected) 3.dp else 1.dp, if (selected) p.flame else p.line, shape)) {
            ThemePreview(themeLook(key), Modifier.fillMaxSize())
            if (badge != null) Text(badge, Modifier.align(Alignment.TopEnd).padding(5.dp).clip(CircleShape).background(Color.Black.copy(alpha = .45f)).padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            if (selected) Box(Modifier.align(Alignment.BottomEnd).padding(5.dp).size(22.dp).clip(CircleShape).background(p.flame), contentAlignment = Alignment.Center) { Pi("check", size = 14.dp, tint = p.onAccent) }
        }
        FitText(tr(label), Modifier.padding(top = 6.dp), size = 12, min = 8, color = if (selected) p.ink else p.ink2, weight = if (selected) FontWeight.ExtraBold else FontWeight.Bold)
    }
}
