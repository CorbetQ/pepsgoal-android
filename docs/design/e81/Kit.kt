package e81

// Esquisse 8.1 « Braise Pop, fond partout » : prototypes des nouveaux composants et de la couche d'arrière-plan.
// Tout est en aplats + bord + marche (Modifier.chunk de l'app) ; aucun flou, aucun halo, aucune ombre Compose.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun hx(s: String): Color = Color(("FF" + s.removePrefix("#")).toLong(16))

/* ================================================================== couche d'arrière-plan (Root) */

/** Point focal de chaque scène (fraction de la hauteur) : la scène est calée pour que ce point tombe dans le héros. */
val SCENE_FOCUS = mapOf("loire" to .30f, "mountains" to .62f, "angers" to .30f, "balloons" to .30f, "lighthouse" to .55f, "pop" to .30f, "rooftops" to .5f)

/** Présence du fond : voile (fraction de bg) en haut, au milieu, en bas. 0 discrète, 1 équilibrée (défaut), 2 intense. */
fun veilStops(level: Int, dark: Boolean): Triple<Float, Float, Float> = when (level) {
    0 -> if (dark) Triple(.40f, .72f, .82f) else Triple(.34f, .70f, .82f)
    2 -> if (dark) Triple(.04f, .22f, .34f) else Triple(.0f, .18f, .32f)
    else -> if (dark) Triple(.12f, .40f, .55f) else Triple(.06f, .38f, .55f)
}

/** Couleur dominante de la scène, pour teinter la marche des barres (calculée une fois par scène, table statique). */
fun sceneTint(key: String, dark: Boolean): Color = when (key) {
    "loire" -> if (dark) hx("2E4670") else hx("5E93CF")
    "mountains" -> if (dark) hx("3B2F5E") else hx("F59E8B")
    "balloons" -> if (dark) hx("2B3F66") else hx("7FB2E6")
    "pop" -> if (dark) hx("4A2440") else hx("FFB59A")
    else -> if (dark) hx("3A2D43") else hx("F2DED1")
}

/**
 * Fond de l'application, dessiné UNE fois sous tout (onglets, pages poussées, feuilles) : scène calée sur son point
 * focal + parallaxe (graphicsLayer), voile dégradé de `bg` (lisibilité), marée du fond réactif. [where] : "all",
 * "home" ou "off" ; ici on dessine toujours (la maquette montre "all").
 */
@Composable
fun AppBg(key: String, level: Int = 1, progress: Float? = null, parallax: Dp = 0.dp, heroY: Dp = 150.dp) {
    val p = LocalPal.current
    BoxWithConstraints(Modifier.fillMaxSize().background(p.bg).clipToBounds()) {
        val h = maxHeight; val extra = h * .3f
        val focus = SCENE_FOCUS[key] ?: .3f
        val off = (heroY - (h + extra) * focus).coerceIn(-extra, 0.dp) - parallax
        Box(Modifier.fillMaxWidth().height(h + extra).offset(y = off)) { SceneArt(key) }
        val (a, b, c) = veilStops(level, p.dark)
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.verticalGradient(0f to p.bg.copy(alpha = a), .26f to p.bg.copy(alpha = a), .5f to p.bg.copy(alpha = b), 1f to p.bg.copy(alpha = c)))
        })
        if (progress != null) Tide(progress)
        // Voile de la barre d'état : icônes système lisibles sur n'importe quelle scène (bg 80 % → 0 sur 44 dp)
        Box(Modifier.fillMaxWidth().height(44.dp).background(Brush.verticalGradient(listOf(p.bg.copy(alpha = .8f), p.bg.copy(alpha = 0f)))))
    }
}

/** Dessin de la scène : scènes illustrées de Scenes.kt (heure du jour : 15 h en clair, 21 h 30 en sombre) ou fond « Collines braise ». */
@Composable
fun SceneArt(key: String) {
    val dark = LocalPal.current.dark
    Canvas(Modifier.fillMaxSize().clipToBounds()) { if (key == "pop") popHills(dark) else scene(key, .3f, if (dark) 21.5f else 15f) }
}

/**
 * Nouveau fond gratuit par défaut « Collines braise » : uniquement des aplats (ciel, soleil ou lune, nuages capsules,
 * trois collines, confettis), animable à 6 i/s par simple décalage des collines. Coût d'un fond uni.
 */
fun DrawScope.popHills(dark: Boolean, t: Float = .3f) {
    val w = size.width; val h = size.height
    drawRect(if (dark) hx("1E1328") else hx("FFF1E6"))
    drawRect(if (dark) hx("261832") else hx("FFE6D6"), Offset(0f, h * .18f), Size(w, h * .82f))
    // Soleil (lune) en aplat avec anneau plein
    val sc = Offset(w * .80f, h * .16f)
    drawCircle(if (dark) hx("3A2648") else hx("FFE08A"), w * .17f, sc)
    drawCircle(if (dark) hx("FFE07A") else hx("FFC23D"), w * .11f, sc)
    if (dark) { drawCircle(hx("261832"), w * .09f, sc + Offset(w * .05f, -w * .03f)) }
    // Étoiles / confettis
    val dots = listOf(.08f to .08f, .22f to .2f, .4f to .1f, .55f to .25f, .62f to .06f, .93f to .3f, .3f to .32f, .12f to .36f)
    dots.forEachIndexed { i, (x, y) ->
        val col = if (dark) hx("FFF4EC").copy(alpha = .7f) else listOf(hx("FF7AD9"), hx("22D3FF"), hx("FFB000"), hx("7CFF6B"))[i % 4]
        if (dark) drawCircle(col, 1.6.dp.toPx() + (i % 3) * .6f.dp.toPx(), Offset(w * x, h * y))
        else drawRoundRect(col, Offset(w * x, h * y), Size(5.dp.toPx(), 9.dp.toPx()), CornerRadius(3.dp.toPx()))
    }
    // Nuages capsules
    fun cloud(x: Float, y: Float, s: Float) {
        val c = if (dark) hx("2F2040") else Color.White
        drawRoundRect(c, Offset(x, y), Size(w * .26f * s, w * .07f * s), CornerRadius(w))
        drawRoundRect(c, Offset(x + w * .05f * s, y - w * .04f * s), Size(w * .13f * s, w * .09f * s), CornerRadius(w))
    }
    cloud(w * .06f, h * .14f, 1f); cloud(w * .5f, h * .28f, .8f)
    // Collines en aplats (trois plans), sommets arrondis
    fun hill(base: Float, amp: Float, ph: Float, col: Color, k: Float) {
        val path = Path().apply {
            moveTo(0f, h); lineTo(0f, base)
            var x = 0f
            while (x <= w + 8f) { lineTo(x, base - amp * (.5f + .5f * sin(x / w * k * 2f * PI.toFloat() + ph + t * 2f))); x += 8f }
            lineTo(w, h); close()
        }
        drawPath(path, col)
    }
    if (dark) {
        hill(h * .40f, h * .06f, 1f, hx("3A1F3E"), 1.3f); hill(h * .50f, h * .05f, 2.6f, hx("4A2440"), 1.7f); hill(h * .62f, h * .045f, .4f, hx("2E1B3A"), 1.1f)
    } else {
        hill(h * .40f, h * .06f, 1f, hx("FFD0B8"), 1.3f); hill(h * .50f, h * .05f, 2.6f, hx("FFB8CF"), 1.7f); hill(h * .62f, h * .045f, .4f, hx("FFC79E"), 1.1f)
    }
    // Fleurs / lucioles en aplats sur la dernière colline : de la vie jusqu'en bas de l'écran
    for (i in 0 until 14) {
        val x = w * ((i * 0.137f + .05f) % 1f); val y = h * (.70f + ((i * 37) % 23) / 100f)
        val col = if (dark) hx("FFE07A").copy(alpha = .55f) else listOf(hx("FF7AD9"), hx("FFB000"), hx("FFFFFF"), hx("7CFF6B"))[i % 4]
        if (dark) drawCircle(col, 2.2.dp.toPx(), Offset(x, y))
        else { for (k in 0 until 5) { val a = k * 1.2566f; drawCircle(col, 3.dp.toPx(), Offset(x + cos(a) * 4.dp.toPx(), y + sin(a) * 4.dp.toPx())) }; drawCircle(hx("FFD23F"), 2.6.dp.toPx(), Offset(x, y)) }
    }
}

/**
 * Fond réactif « marée de braise » (remplace la lueur radiale) : deux vagues en aplats translucides qui montent du bas
 * avec l'avancement du jour (8 % → 34 % de la hauteur). Dessinée dans la couche du fond, même fond coupé.
 */
@Composable
fun Tide(progress: Float) {
    val p = LocalPal.current
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val top = h * (1f - (.08f + .26f * progress.coerceIn(0f, 1f)))
        fun wave(y: Float, amp: Float, ph: Float, col: Color) {
            val path = Path().apply { moveTo(0f, h); lineTo(0f, y); var x = 0f; while (x <= w + 6f) { lineTo(x, y + amp * sin(x / w * 2.2f * PI.toFloat() + ph)); x += 6f }; lineTo(w, h); close() }
            drawPath(path, col)
        }
        wave(top, 16.dp.toPx(), 0f, hx("FF8A3D").copy(alpha = if (p.dark) .20f else .16f))
        wave(top + 30.dp.toPx(), 12.dp.toPx(), 2f, hx("FF3D96").copy(alpha = if (p.dark) .18f else .13f))
    }
}

/** Pal avec la marche des barres teintée par la scène (barre d'onglets, barre compacte). */
@Composable
fun tintedPal(key: String): Pal { val p = LocalPal.current; return p.copy(edge = lerp(p.edge, sceneTint(key, p.dark), if (p.dark) .55f else .45f)) }

/* ================================================================== plaques : tout texte posé sur la scène */

/** Plaque : capsule opaque `surface` + bord 2 dp, sans marche (non pressable). Porte tout texte posé directement sur le fond. */
@Composable
fun Plaque(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val p = LocalPal.current
    Row(modifier.background(p.surface, CircleShape).border(2.dp, p.edge, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** En-tête de section sur le fond : plaque [emoji 26 + Unbounded 18 + compteur], action en petite puce à marche. */
@Composable
fun SectionPlaque(text: String, emoji: String, count: Any? = null, action: String? = null) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Plaque { Emoji(emoji, 24.dp); Text(text, fontSize = 18.sp, fontFamily = Unbounded, fontWeight = FontWeight.Bold, color = p.ink); if (count != null) Count(count) }
        Spacer(Modifier.weight(1f))
        if (action != null) Box(Modifier.chunk(p.surface, p.edge, 20.dp, 3.dp, 2.dp).padding(horizontal = 14.dp, vertical = 7.dp)) { Text(action, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = p.flameInk) }
    }
}

/** Petite plaque de texte (date, séparateur de jour, ligne d'aide) : Manrope 13 ExtraBold ink2. */
@Composable
fun MiniPlaque(text: String, modifier: Modifier = Modifier, emoji: String? = null) {
    val p = LocalPal.current
    Row(modifier.background(p.surface, CircleShape).border(2.dp, p.edge, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (emoji != null) Emoji(emoji, 16.dp)
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
    }
}

/** Barre d'état simulée. */
@Composable
fun StatusBar() {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("9:41", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink); Spacer(Modifier.weight(1f))
        Box(Modifier.size(16.dp, 10.dp).border(1.5.dp, p.ink, RoundedCornerShape(3.dp)).padding(2.dp)) { Box(Modifier.fillMaxWidth(.7f).fillMaxHeight().background(p.ink, RoundedCornerShape(1.dp))) }
    }
}

@Composable private fun MiniStatus(s: String) { val p = LocalPal.current; Text(s, Modifier.background(p.surface, CircleShape).border(2.dp, p.edge, CircleShape).padding(horizontal = 8.dp, vertical = 1.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink) }

/**
 * Héros d'onglet : la scène se voit en entier sur [height] dp. Titre en plaque (Unbounded 30) posé en bas à gauche,
 * date en mini-plaque au-dessus, boutons ronds à marche à droite. Au défilement, il part avec le contenu (parallaxe de
 * la scène à 0,35) et la barre compacte apparaît.
 */
@Composable
fun TabHero(title: String, over: String? = null, height: Dp = 132.dp, right: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPal.current
    Box(Modifier.fillMaxWidth().height(height).padding(start = 16.dp, end = 12.dp, bottom = 10.dp)) {
        Row(Modifier.align(Alignment.TopEnd).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = right)
        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (over != null) MiniPlaque(over)
            Row(Modifier.background(p.surface, CircleShape).border(2.dp, p.edge, CircleShape).padding(start = 16.dp, end = 18.dp, top = 4.dp, bottom = 6.dp)) {
                Text(title, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = p.ink, maxLines = 2)
            }
        }
    }
}

/**
 * Héros de page poussée (ScreenFrame) : bouton Retour et actions en haut, tuile emoji 52 + titre en plaque en bas.
 * La scène du fond est visible sur toute la hauteur du héros.
 */
@Composable
fun PageHero(title: String, emoji: String, color: Color, height: Dp = 176.dp, right: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPal.current
    Box(Modifier.fillMaxWidth().height(height).padding(horizontal = 12.dp).padding(bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundGlass("Retour", {}) { Pi("chevronleft", size = 20.dp, tint = p.ink) }
            Spacer(Modifier.weight(1f)); right()
        }
        Row(Modifier.align(Alignment.BottomStart).padding(start = 4.dp).background(p.surface, RoundedCornerShape(24.dp)).border(2.dp, p.edge, RoundedCornerShape(24.dp)).padding(6.dp).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmojiTile(emoji, color, 48.dp)
            Text(title, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = p.ink, maxLines = 2)
        }
    }
}

/**
 * Barre compacte (après défilement, et en-tête des écrans plein contenu comme Messages) : surface opaque, coins bas
 * 24, bord 2 + marche 3 teintée par la scène. 56 dp + barre d'état.
 */
@Composable
fun CompactBar(content: @Composable RowScope.() -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().chunk(p.surface, p.edge, 24.dp, 4.dp, 2.dp).padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 2.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/* ================================================================== contrôles */

/**
 * PSlider : rail capsule 12 dp `surface2` bordé `control`, remplissage néon (ou flamme), pouce blanc 30 dp à marche
 * 3 dp, bulle de valeur prune au-dessus pendant le glissement. [steps] > 0 : crans en pastilles. Cible 48 dp.
 */
@Composable
fun PSlider(value: Float, modifier: Modifier = Modifier, color: Color? = null, steps: Int = 0, bubble: String? = null, enabled: Boolean = true) {
    val p = LocalPal.current
    val fillC = color
    Box(modifier.fillMaxWidth().height(if (bubble != null) 78.dp else 48.dp).graphicsLayer { alpha = if (enabled) 1f else .45f }) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomStart)) {
            val w = maxWidth; val thumb = 30.dp; val x = (w - thumb) * value
            Canvas(Modifier.fillMaxSize()) {
                val rh = 12.dp.toPx(); val y = (size.height - rh) / 2; val r = CornerRadius(rh / 2)
                drawRoundRect(p.control, Offset(0f, y - 2.dp.toPx()), Size(size.width, rh + 4.dp.toPx()), CornerRadius(rh))
                drawRoundRect(p.surface2, Offset(2.dp.toPx(), y), Size(size.width - 4.dp.toPx(), rh), r)
                val fw = (x + thumb / 2).toPx()
                drawRoundRect(if (fillC != null) SolidColor(fillC) else Brush.horizontalGradient(listOf(p.flame2, p.flame, p.pink)), Offset(2.dp.toPx(), y), Size(fw, rh), r)
                if (steps > 0) for (i in 0..steps) {
                    val cx = (thumb / 2).toPx() + (size.width - thumb.toPx()) * i / steps
                    drawCircle(if (cx <= fw) PLUM.copy(alpha = .55f) else p.control, 3.dp.toPx(), Offset(cx, size.height / 2))
                }
            }
            Box(Modifier.offset(x = x).align(Alignment.CenterStart).size(thumb, thumb + 3.dp).chunk(Color.White, if (fillC != null) fillC.step() else p.flameStep, 15.dp, 3.dp, 2.dp))
        }
        if (bubble != null) BoxWithConstraints(Modifier.fillMaxWidth()) {
            val x = (maxWidth - 30.dp) * value + 15.dp
            Box(Modifier.offset(x = x - 44.dp).width(88.dp), contentAlignment = Alignment.Center) {
                Text(bubble, Modifier.background(PLUM, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp), color = hx("FFF4EC"), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** PRadio : rond 26 dp. Éteint : surface, contour 2,5 `control`. Allumé : aplat néon (flamme par défaut) + pastille prune 10, marche 2 `step`. */
@Composable
fun PRadio(on: Boolean, color: Color? = null, enabled: Boolean = true) {
    val p = LocalPal.current
    val c = color ?: p.type("challenge").c
    Box(Modifier.size(48.dp).graphicsLayer { alpha = if (enabled) 1f else .45f }, contentAlignment = Alignment.Center) {
        if (on) Box(Modifier.size(28.dp, 30.dp).chunk(c, c.step(), 14.dp, 2.dp, 0.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(10.dp).background(PLUM, CircleShape)) }
        else Box(Modifier.padding(top = 2.dp).size(28.dp).background(p.surface, CircleShape).border(2.5.dp, p.control, CircleShape))
    }
}

/** Ligne de choix unique (rôle RadioButton) : 60 dp, tuile facultative, titre, sous-ligne, PRadio à droite ; choisie = fond teinté 14 %. */
@Composable
fun PRadioRow(title: String, sub: String?, on: Boolean, emoji: String? = null, color: Color? = null) {
    val p = LocalPal.current
    val c = color ?: p.type("challenge").c
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (on) lerp(p.surface, c, if (p.dark) .16f else .14f) else Color.Transparent).heightIn(min = 60.dp).padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (emoji != null) Emoji(emoji, 26.dp)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
            if (sub != null) Text(sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink2, lineHeight = 17.sp)
        }
        PRadio(on, c)
    }
}

/** PCheckbox : carré 26 dp, rayon 8. Coché : aplat néon + coche prune 16 + marche 2. Partiel : tiret prune. */
@Composable
fun PCheckbox(state: Int, color: Color? = null, enabled: Boolean = true) {
    val p = LocalPal.current
    val c = color ?: p.type("routine").c
    Box(Modifier.size(48.dp).graphicsLayer { alpha = if (enabled) 1f else .45f }, contentAlignment = Alignment.Center) {
        if (state > 0) Box(Modifier.size(28.dp, 30.dp).chunk(c, c.step(), 9.dp, 2.dp, 0.dp), contentAlignment = Alignment.Center) {
            if (state == 1) Pi("check", size = 18.dp, tint = PLUM) else Box(Modifier.size(12.dp, 3.dp).background(PLUM, CircleShape))
        } else Box(Modifier.padding(top = 2.dp).size(28.dp).background(p.surface, RoundedCornerShape(9.dp)).border(2.5.dp, p.control, RoundedCornerShape(9.dp)))
    }
}

@Composable
fun PCheckRow(title: String, sub: String?, state: Int, color: Color? = null) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PCheckbox(state, color)
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
            if (sub != null) Text(sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink2)
        }
    }
}

/**
 * PTextField : libellé 13 ExtraBold au-dessus, boîte 56 (rayon 18, bord 2 + marche 3) ; focus = bord et marche en
 * braise #FF4A12 ; erreur = `danger` + message avec icône ; aide / compteur sous la boîte ; boutons de champ 48 dp.
 */
@Composable
fun PTextField(label: String, value: String, placeholder: String = "", focused: Boolean = false, error: String? = null, help: String? = null, icon: String? = null,
               trailing: String? = null, counter: String? = null, lines: Int = 1, enabled: Boolean = true) {
    val p = LocalPal.current
    val edge = when { error != null -> p.danger; focused -> hx("FF4A12"); else -> p.edge }
    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = if (enabled) 1f else .45f }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, Modifier.padding(start = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (error != null) p.danger else p.ink2)
        Row(Modifier.fillMaxWidth().chunk(if (enabled) p.surface else p.surface2, edge, 18.dp, 3.dp, if (focused || error != null) 2.5.dp else 2.dp).heightIn(min = 49.dp + 20.dp * (lines - 1))
            .padding(start = 14.dp, end = 4.dp), verticalAlignment = if (lines > 1) Alignment.Top else Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) Pi(icon, size = 20.dp, tint = if (focused) p.ink else p.ink3)
            Row(Modifier.weight(1f).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                if (value.isEmpty()) Text(placeholder, color = p.ink3, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                else Text(value, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
                if (focused) Box(Modifier.padding(start = 1.dp).size(2.dp, 20.dp).background(hx("FF4A12")))
            }
            if (trailing != null) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp).background(p.surface2, CircleShape), contentAlignment = Alignment.Center) { Pi(trailing, size = 16.dp, tint = p.ink2) }
            } else Spacer(Modifier.width(10.dp))
        }
        if (error != null || help != null || counter != null) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (error != null) { Pi("x", size = 14.dp, tint = p.danger); Text(error, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.danger) }
            else if (help != null) Text(help, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ok)
            else Spacer(Modifier.weight(1f))
            if (counter != null) Text(counter, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = p.ink3)
        }
    }
}

/** Champ « liste déroulante » (déclencheur de PMenu) : boîte de champ avec valeur et chevron, pressable (marche). */
@Composable
fun PSelect(label: String?, value: String, emoji: String? = null, open: Boolean = false) {
    val p = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, Modifier.padding(start = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
        Row(Modifier.fillMaxWidth().chunk(p.surface, if (open) hx("FF4A12") else p.edge, 18.dp, 4.dp, 2.dp).heightIn(min = 50.dp).padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (emoji != null) Emoji(emoji, 22.dp)
            Text(value, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
            Pi("chevronleft", Modifier.graphicsLayer { rotationZ = if (open) 90f else -90f }, 18.dp, p.ink2)
        }
    }
}

data class MenuItem(val label: String, val icon: String? = null, val emoji: String? = null, val on: Boolean = false, val danger: Boolean = false, val sub: String? = null)

/**
 * PMenu : carte flottante `surface`, rayon 22, bord 2 + marche 4, lignes 52 dp (icône 20 ou emoji 22, libellé 16
 * ExtraBold, coche prune sur fond `routine` pour l'option choisie, danger en rouge), séparateur 2 dp `surface2`.
 * Entrée : échelle 0,92 → 1 depuis l'ancre (ressort `pop`), fondu 120 ms à la sortie. Remplace DropdownMenu.
 */
@Composable
fun PMenu(items: List<MenuItem>, modifier: Modifier = Modifier, header: (@Composable () -> Unit)? = null) {
    val p = LocalPal.current
    Column(modifier.widthIn(min = 248.dp).chunk(p.surface, p.edge, 22.dp, 4.dp, 2.dp).padding(vertical = 6.dp)) {
        header?.invoke()
        items.forEachIndexed { i, it ->
            if (i > 0 && (it.danger || header != null && i == 0)) Box(Modifier.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth().height(2.dp).background(p.surface2))
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(14.dp)).background(if (it.on) lerp(p.surface, p.type("routine").c, if (p.dark) .14f else .18f) else Color.Transparent)
                .heightIn(min = 50.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (it.emoji != null) Emoji(it.emoji, 22.dp) else if (it.icon != null) Pi(it.icon, size = 20.dp, tint = if (it.danger) p.danger else p.ink)
                Column(Modifier.weight(1f)) {
                    Text(it.label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (it.danger) p.danger else p.ink)
                    if (it.sub != null) Text(it.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink2)
                }
                if (it.on) Box(Modifier.size(24.dp).background(p.type("routine").c, CircleShape), contentAlignment = Alignment.Center) { Pi("check", size = 14.dp, tint = PLUM) }
            }
        }
    }
}

/**
 * PDialog : carte centrée `surface`, rayon 28, bord 2 + marche 6, largeur 320 ; emoji 56 qui « pop », titre Unbounded
 * 22, texte 15 Bold ink2, boutons empilés pleine largeur (un seul principal ; action destructrice = aplat `duel` + prune).
 * Voile `scrim`. Remplace AlertDialog, Dialog et les dialogues Android (DatePickerDialog, TimePickerDialog).
 */
@Composable
fun PDialog(emoji: String, title: String, body: String, primary: String, secondary: String, danger: Boolean = false, extra: (@Composable ColumnScope.() -> Unit)? = null) {
    val p = LocalPal.current
    Column(Modifier.width(328.dp).chunk(p.surface, p.edge, 28.dp, 6.dp, 2.dp).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.graphicsLayer { rotationZ = -6f }) { Emoji(emoji, 56.dp) }
        Text(title, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = p.ink, textAlign = TextAlign.Center)
        Text(body, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink2, textAlign = TextAlign.Center, lineHeight = 20.sp)
        extra?.invoke(this)
        Spacer(Modifier.height(4.dp))
        if (danger) PButton(primary, {}, Modifier.fillMaxWidth(), kind = "type", color = p.type("duel").c, icon = "logout")
        else PButton(primary, {}, Modifier.fillMaxWidth(), kind = "primary")
        PButton(secondary, {}, Modifier.fillMaxWidth())
    }
}

/**
 * PTabs : onglets de page (Boutique, Social, Statistiques) — rail capsule `surface2` qui défile si besoin, onglet choisi
 * = pastille à marche en aplat néon (couleur de la page) + texte prune, emoji 18 facultatif ; 48 dp min ; 2 lignes à 200 %.
 */
@Composable
fun PTabs(options: List<Pair<String, String?>>, sel: Int, color: Color? = null, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val c = color
    val scroll = options.size > 3
    Row(modifier.fillMaxWidth().background(p.surface, CircleShape).border(2.dp, p.edge, CircleShape).clip(CircleShape).padding(4.dp)
        .then(if (scroll) Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()) else Modifier), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, (l, e) ->
            val on = i == sel
            Row((if (scroll) Modifier else Modifier.weight(1f)).then(if (on) (if (c != null) Modifier.chunk(c, c.step(), 22.dp, 3.dp, 0.dp) else Modifier.chunk(p.flameBrush(), p.flameStep, 22.dp, 3.dp, 0.dp)) else Modifier.padding(bottom = 3.dp))
                .heightIn(min = 45.dp).padding(horizontal = if (scroll) 14.dp else 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                if (e != null) Emoji(e, 18.dp)
                Text(l, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (on) PLUM else p.ink2, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 16.sp)
            }
        }
    }
}

/** PSpinner : trois pastilles néon qui sautent l'une après l'autre (pop, graphicsLayer) — remplace CircularProgressIndicator. [phase] figée pour la maquette. */
@Composable
fun PSpinner(size: Dp = 10.dp, phase: Float = .3f) {
    val cols = listOf(hx("FFB000"), hx("FF4A12"), hx("FF3D96"))
    Row(horizontalArrangement = Arrangement.spacedBy(size * .5f), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(size * 2.2f)) {
        cols.forEachIndexed { i, c ->
            val k = sin(((phase - i * .18f) * 2f * PI).toFloat()).coerceAtLeast(0f)
            Box(Modifier.graphicsLayer { translationY = -k * size.toPx() }.size(size).background(c, CircleShape))
        }
    }
}

/** Barre indéterminée : rail creusé et segment flamme qui glisse (une seule translation). */
@Composable
fun PIndeterminate(modifier: Modifier = Modifier, at: Float = .35f) {
    val p = LocalPal.current
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(p.rail, cornerRadius = r)
        drawRoundRect(Brush.horizontalGradient(listOf(hx("FFB000"), hx("FF4A12"), hx("FF3D96"))), Offset(size.width * at, 0f), Size(size.width * .3f, size.height), r)
    }
}

/** PStepper : − valeur + (boutons ronds à marche 48 dp, valeur Unbounded 22 + unité). */
@Composable
fun PStepper(value: String, unit: String) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundGlass("Moins", {}) { Text("−", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = p.ink) }
        Row(verticalAlignment = Alignment.Bottom) { Text(value, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp); Text(" $unit", Modifier.padding(bottom = 2.dp), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2) }
        RoundGlass("Plus", {}) { Pi("plus", size = 20.dp, tint = p.ink) }
    }
}

/* ================================================================== flamme autour de l'avatar */

/**
 * Couronne de flammes (flameAura de la 6.3.0 en Braise Pop) : langues en aplats pleins sur trois plans (rose, braise,
 * jaune), sans dégradé vers le transparent ni halo. Hauteur et nombre suivent le niveau ([k] 0 → 1). Vacille à 15 i/s
 * (graphicsLayer / dessin), seulement sur le Profil, arrêtée hors écran et après 20 s.
 */
fun Modifier.flameCrown(k: Float, radius: Dp, t: Float = .2f): Modifier = this.drawBehind {
    val c = Offset(size.width / 2, size.height / 2)
    val r0 = radius.toPx()
    val n = 9 + (k * 4).toInt()
    val hMax = (18f + 40f * k).dp.toPx()
    fun layer(scale: Float, col: Color, shift: Float, widen: Float) {
        for (i in 0 until n) {
            val a = ((i + shift) / n) * 2f * PI.toFloat() - PI.toFloat() / 2
            val dx = cos(a); val dy = sin(a)
            // Les flammes montent : hautes en haut du cercle, presque nulles en bas
            val top = ((1f - dy) / 2f)
            val wob = .8f + .2f * sin(t * 6.28f * 2 + i * 1.9f + shift * 4f)
            val h = hMax * scale * wob * (.12f + .88f * top * top)
            val half = (PI.toFloat() / n) * widen
            val l = Offset(c.x + cos(a - half) * r0, c.y + sin(a - half) * r0)
            val rr = Offset(c.x + cos(a + half) * r0, c.y + sin(a + half) * r0)
            var ux = dx * .55f; var uy = dy * .55f - .75f
            val len = kotlin.math.sqrt(ux * ux + uy * uy); ux /= len; uy /= len
            val bx = c.x + dx * r0; val by = c.y + dy * r0
            // Pointe recourbée (balancement) : la langue s'effile en virgule
            val sway = .35f * sin(i * 2.3f + t * 6.28f)
            val tip = Offset(bx + (ux + sway * -uy) * h, by + (uy + sway * ux) * h)
            val px = -uy; val py = ux; val bw = r0 * half * 1.1f
            val c1 = Offset(bx + ux * h * .45f - px * bw, by + uy * h * .45f - py * bw)
            val c2 = Offset(bx + ux * h * .45f + px * bw, by + uy * h * .45f + py * bw)
            drawPath(Path().apply { moveTo(l.x, l.y); cubicTo(c1.x - px * bw * .2f, c1.y - py * bw * .2f, tip.x - px * bw * .15f, tip.y - py * bw * .15f, tip.x, tip.y); cubicTo(tip.x + px * bw * .1f, tip.y + py * bw * .1f, c2.x, c2.y, rr.x, rr.y); close() }, col)
        }
    }
    layer(1f, hx("FF3D96"), 0f, 1.5f)
    layer(.72f, hx("FF4A12"), .5f, 1.35f)
    layer(.44f, hx("FFB000"), 0f, 1.1f)
}

/** Médaillon d'avatar pressable : disque blanc à marche `flameStep` 55 %, qui s'enfonce (comme le médaillon de la flamme). */
@Composable
fun AvatarMedallion(size: Dp, level: Int, crown: Float?, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPal.current
    Box(Modifier.size(size + 90.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size + 8.dp).then(if (crown != null) Modifier.flameCrown(crown, (size + 8.dp) / 2 - 3.dp) else Modifier))
        Box(Modifier.offset(y = 2.dp).size(size + 8.dp, size + 13.dp).chunk(Color.White, p.flameStep.copy(alpha = .55f), (size + 8.dp) / 2, 5.dp, 0.dp), contentAlignment = Alignment.Center, content = content)
        Box(Modifier.align(Alignment.Center).offset(x = size * .42f, y = size * .44f).chunk(PLUM, hx("120810"), 14.dp, 3.dp, 0.dp).padding(horizontal = 9.dp, vertical = 2.dp)) {
            Text("$level", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = p.gold)
        }
    }
}

/* ================================================================== messages */

/**
 * Bulle de message : à moi = aplat de la couleur de la conversation (flamme par défaut), texte prune, marche `step`
 * 3 dp ; reçue = `surface` + bord 2 + marche 3 `edge`. Rayon 22, coin de la queue 6. Heure 12 Bold dans la bulle.
 */
@Composable
fun Bubble(text: String, mine: Boolean, time: String, color: Color? = null, react: String? = null, lifted: Boolean = false) {
    val p = LocalPal.current
    val shape = if (mine) RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp) else RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Box {
            val fill: Brush = if (mine) (if (color != null) SolidColor(color) else Brush.linearGradient(listOf(hx("FFB000"), hx("FF4A12"), hx("FF3D96")))) else SolidColor(p.surface)
            val edge = if (mine) (color?.step() ?: p.flameStep) else if (lifted) hx("FF4A12") else p.edge
            Column(Modifier.widthIn(max = 290.dp).drawBehind {
                val st = 3.dp.toPx()
                val o = shape.createOutline(Size(size.width, size.height), layoutDirection, this)
                drawOutline(o, edge)
            }.padding(start = if (mine) 0.dp else 2.dp, end = if (mine) 0.dp else 2.dp, top = if (mine) 0.dp else 2.dp, bottom = 3.dp + if (mine) 0.dp else 2.dp)
                .clip(shape).background(fill).padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (mine) PLUM else p.ink, lineHeight = 20.sp)
                Text(time, Modifier.align(Alignment.End), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (mine) PLUM else p.ink3)
            }
            if (react != null) Row(Modifier.align(if (mine) Alignment.BottomStart else Alignment.BottomEnd).offset(x = if (mine) (-10).dp else 10.dp, y = 14.dp)
                .background(p.surface, CircleShape).border(2.dp, p.edge, CircleShape).padding(horizontal = 6.dp, vertical = 1.dp)) { Emoji(react, 16.dp); Text(" 2", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2) }
        }
        if (react != null) Spacer(Modifier.height(14.dp))
    }
}

/** Toast prune à marche (spec 7.16), avec action or. */
@Composable
fun ToastPop(emoji: String, text: String, action: String?, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Row(modifier.fillMaxWidth().chunk(PLUM, hx("120810"), 20.dp, 4.dp, 0.dp).heightIn(min = 56.dp).padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Emoji(emoji, 26.dp)
        Text(text, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = hx("FFF4EC"))
        if (action != null) Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { Text(action, Modifier.background(p.gold, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = PLUM) }
    }
}
