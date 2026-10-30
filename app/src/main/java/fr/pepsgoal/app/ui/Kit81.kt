package fr.pepsgoal.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import fr.pepsgoal.app.ui.theme.CREAM
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.PLUM
import fr.pepsgoal.app.ui.theme.PLUM_DEEP
import fr.pepsgoal.app.ui.theme.Pal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.step
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Braise Pop 8.1 « fond partout » : plaques (tout texte posé sur la scène), héros et barre compacte, et les contrôles
 * qui remplacent les derniers composants Material (curseur, radio, case, champ, menu, dialogue, sélecteurs de date et
 * d'heure, onglets, chargement, feuille). Tout est en aplats, bord de 2 dp et marche ; cibles tactiles de 48 dp.
 */

/* ================================================================== fond de la scène : teinte des barres */

/** Couleur dominante de la scène du fond (null : aucune scène). Fournie par la racine. */
val LocalSceneTint = staticCompositionLocalOf<Color?> { null }

/** Bord et marche des barres (onglets, barre compacte) teintés par la scène. */
fun Pal.barEdge(tint: Color?): Color = if (contrast || tint == null) edge else lerp(edge, tint, if (dark) .55f else .45f)

/* ================================================================== plaques */

/** Plaque : capsule opaque `surface` + bord 2 dp, sans marche (elle ne se presse pas). Porte tout texte posé sur le fond. */
@Composable
fun Plaque(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val p = LocalPal.current
    Row(modifier.background(p.surface, CircleShape).border(2.dp, p.edge, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Petite plaque de texte (date, séparateur de jour, ligne d'aide) : Manrope 13 ExtraBold `ink2`, emoji 16 facultatif. */
@Composable
fun MiniPlaque(text: String, modifier: Modifier = Modifier, emoji: String? = null, color: Color? = null) {
    val p = LocalPal.current
    Row(modifier.background(p.surface, RoundedCornerShape(14.dp)).border(2.dp, p.edge, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (emoji != null) Emoji(emoji, 16.dp)
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color ?: p.ink2, lineHeight = 17.sp)
    }
}

/**
 * En-tête de section posé sur le fond : plaque [emoji 24 + Unbounded 18 + compteur] ; action en petite puce à marche
 * (48 dp). 14 dp au-dessus (4 + l'écart de la liste), annoncé comme titre.
 */
@Composable
fun SectionPlaque(text: String, emoji: String? = null, count: Any? = null, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier,
                  extra: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPal.current
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f, fill = false).semantics(mergeDescendants = true) { heading() }.background(p.surface, RoundedCornerShape(24.dp)).border(2.dp, p.edge, RoundedCornerShape(24.dp))
            .padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (emoji != null) Emoji(emoji, 24.dp)
            Text(text, Modifier.weight(1f, fill = false), fontSize = 18.sp, fontFamily = Unbounded, fontWeight = FontWeight.Bold, lineHeight = 22.sp, color = p.ink, maxLines = 2)
            if (count != null) Count(count)
            extra()
        }
        Spacer(Modifier.weight(.001f))
        if (action != null && onAction != null) Puce(action, onAction)
    }
}

/** Ligne d'aide : en mini-plaque sur la scène, en texte discret dans une carte ou une feuille. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    if (LocalInCard.current || LocalInSheet.current) Muted(text, modifier, small = true) else MiniPlaque(text, modifier)
}

/** Petite puce d'action à marche (« Ordre », « Tout voir ») : 48 dp de cible, texte de l'accent. */
@Composable
fun Puce(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, emoji: String? = null) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter)
    Box(modifier.sizeIn(minHeight = 48.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.chunk(p.surface, p.edge, 20.dp, 3.dp, 2.dp, press).clip(CircleShape).springClick(inter, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (emoji != null) Emoji(emoji, 16.dp)
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = p.flameInk, maxLines = 2)
        }
    }
}

/** Titre d'onglet dans le héros : capsule, Unbounded 28 ExtraBold, deux lignes au plus. */
@Composable
fun TitlePlaque(text: String, modifier: Modifier = Modifier, size: Int = 28) {
    val p = LocalPal.current
    Box(modifier.semantics(mergeDescendants = true) { heading() }.background(p.surface, RoundedCornerShape(30.dp)).border(2.dp, p.edge, RoundedCornerShape(30.dp))
        .padding(start = 16.dp, end = 18.dp, top = 4.dp, bottom = 6.dp)) {
        Text(text, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, lineHeight = (size + 4).sp, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/* ================================================================== héros et barre compacte */

/**
 * Héros d'onglet : la scène se voit sur [height] dp. Boutons ronds à droite, date (ou statut) en mini-plaque puis titre
 * en plaque en bas à gauche. Premier élément de la liste : il défile avec le contenu.
 */
@Composable
fun TabHero(title: String, over: String? = null, height: Dp = 120.dp, chips: @Composable RowScope.() -> Unit = {}, right: @Composable RowScope.() -> Unit = {}) {
    Box(Modifier.fillMaxWidth().heightIn(min = height)) {
        Row(Modifier.align(Alignment.TopEnd).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = right)
        Column(Modifier.align(Alignment.BottomStart).padding(top = 54.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (over != null) MiniPlaque(over)
                chips()
            }
            TitlePlaque(title)
        }
    }
}

/**
 * Héros de page poussée : bouton Retour et actions en haut, tuile emoji 48 + titre Unbounded 26 dans une plaque en bas à
 * gauche. La scène du fond reste visible sur toute la hauteur.
 */
@Composable
fun PageHero(title: String, color: Color, back: String, onBack: () -> Unit, emoji: String? = null, icon: String? = null, height: Dp = 176.dp,
             right: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPal.current
    Box(Modifier.fillMaxWidth().heightIn(min = height)) {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RoundGlass("Retour : $back", onBack) { Pi("chevronleft", size = 20.dp, tint = p.ink) }
            Spacer(Modifier.weight(1f)); right()
        }
        Row(Modifier.align(Alignment.BottomStart).padding(top = 62.dp, bottom = 2.dp).semantics(mergeDescendants = true) { heading() }
            .background(p.surface, RoundedCornerShape(24.dp)).border(2.dp, p.edge, RoundedCornerShape(24.dp)).padding(6.dp).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (emoji != null) EmojiTile(emoji, color, 48.dp) else EmojiTileBox(color, 48.dp) { Pi(icon ?: "star", size = 24.dp, tint = PLUM) }
            Text(title, Modifier.weight(1f, fill = false), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Barre compacte : surface opaque, coins 24, bord 2 + marche 4 teintés par la scène, 56 dp. Apparaît quand le héros
 * est passé ([shown] lu au dessin : fondu et glissement de 8 dp, sans animation de hauteur).
 */
@Composable
fun CompactBar(modifier: Modifier = Modifier, shown: () -> Float = { 1f }, content: @Composable RowScope.() -> Unit) {
    val p = LocalPal.current
    val edge = p.barEdge(LocalSceneTint.current)
    Row(modifier.fillMaxWidth().graphicsLayer { val k = shown(); alpha = k.coerceIn(0f, 1f); translationY = (1f - k) * -8.dp.toPx() }
        .chunk(p.surface, edge, 24.dp, 4.dp, 2.dp).heightIn(min = 48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/** Contenu habituel d'une barre compacte : retour, tuile emoji 34, titre Unbounded 20, actions. */
@Composable
fun RowScope.CompactTitle(title: String, color: Color, back: String?, onBack: (() -> Unit)?, emoji: String? = null, icon: String? = null) {
    val p = LocalPal.current
    if (onBack != null) RoundGlass("Retour : ${back ?: ""}", onBack) { Pi("chevronleft", size = 20.dp, tint = p.ink) } else Spacer(Modifier.width(6.dp))
    if (emoji != null) EmojiTile(emoji, color, 34.dp) else if (icon != null) EmojiTileBox(color, 34.dp) { Pi(icon, size = 18.dp, tint = PLUM) }
    Text(title, Modifier.weight(1f).semantics { heading() }, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/* ================================================================== curseur */

/**
 * PSlider : rail capsule 12 dp `surface2` entouré de 2 dp `control` ; remplissage flamme (ou néon [color]) ; pouce blanc
 * de 30 dp à marche qui s'enfonce pendant le glissement. [steps] > 0 : crans (pastilles) et aimantation ; [bubble] :
 * bulle de valeur prune pendant le glissement (en permanence avec [keepBubble]). Valeur de 0 à 1. Cible 48 dp ; rôle
 * « progression » réglable par TalkBack.
 */
@Composable
fun PSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, color: Color? = null, steps: Int = 0, bubble: ((Float) -> String)? = null,
            keepBubble: Boolean = false, enabled: Boolean = true, label: String = "", onDone: () -> Unit = {}) {
    val p = LocalPal.current
    val v = value.coerceIn(0f, 1f)
    var dragging by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (dragging) 1f else 0f, if (p.motion) snap() else Motion.press(), label = "thumb")
    val snapTo = { x: Float -> val c = x.coerceIn(0f, 1f); if (steps > 0) (c * steps).roundToInt() / steps.toFloat() else c }
    val cur = rememberUpdatedState(v)
    val change = rememberUpdatedState(onChange)
    val emit = { x: Float ->
        val n = snapTo(x)
        if (n != cur.value) { if (steps > 0) Feedback.tick(); change.value(n) }
    }
    val thumb = 30.dp
    val fillC = color?.let { vivid(it) }
    val bubbleH = if (bubble != null) 30.dp else 0.dp
    Column(modifier.fillMaxWidth().graphicsLayer { alpha = if (enabled) 1f else .45f }) {
        if (bubble != null) BoxWithConstraints(Modifier.fillMaxWidth().height(bubbleH)) {
            val x = (maxWidth - thumb) * v + thumb / 2
            if (dragging || keepBubble) Box(Modifier.offset(x = x - 44.dp).width(88.dp), contentAlignment = Alignment.Center) {
                Text(bubble(v), Modifier.background(PLUM, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp), color = CREAM, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp)
            .semantics {
                if (label.isNotEmpty()) contentDescription = tr(label)
                progressBarRangeInfo = ProgressBarRangeInfo(v, 0f..1f, if (steps > 0) steps - 1 else 0)
                if (bubble != null) stateDescription = bubble(v)
                if (enabled) setProgress { t -> emit(t); onDone(); true }
            }
            .then(if (!enabled) Modifier else Modifier
                .pointerInput(steps) {
                    val tp = thumb.toPx()
                    detectTapGestures { o -> emit((o.x - tp / 2) / (size.width - tp).coerceAtLeast(1f)); onDone() }
                }
                .pointerInput(steps) {
                    val tp = thumb.toPx()
                    detectHorizontalDragGestures(onDragStart = { o -> dragging = true; emit((o.x - tp / 2) / (size.width - tp).coerceAtLeast(1f)) },
                        onDragEnd = { dragging = false; onDone() }, onDragCancel = { dragging = false }) { ch, _ ->
                        ch.consume(); emit((ch.position.x - tp / 2) / (size.width - tp).coerceAtLeast(1f))
                    }
                })) {
            val w = maxWidth
            Canvas(Modifier.fillMaxSize()) {
                val rh = 12.dp.toPx(); val y = (size.height - rh) / 2; val r = CornerRadius(rh / 2)
                val tp = thumb.toPx()
                drawRoundRect(p.control, Offset(0f, y - 2.dp.toPx()), Size(size.width, rh + 4.dp.toPx()), CornerRadius(rh))
                drawRoundRect(p.surface2, Offset(2.dp.toPx(), y), Size(size.width - 4.dp.toPx(), rh), r)
                val fw = tp / 2 + (size.width - tp) * v
                drawRoundRect(if (fillC != null) SolidColor(fillC) else p.flameBrush(), Offset(2.dp.toPx(), y), Size((fw - 2.dp.toPx()).coerceAtLeast(rh), rh), r)
                if (steps > 0) for (i in 0..steps) {
                    val cx = tp / 2 + (size.width - tp) * i / steps
                    drawCircle(if (cx <= fw) PLUM.copy(alpha = .55f) else p.control, 3.dp.toPx(), Offset(cx, size.height / 2))
                }
            }
            Box(Modifier.offset { IntOffset(((w - thumb).toPx() * v).roundToInt(), 0) }.align(Alignment.CenterStart).size(thumb, thumb + 3.dp)
                .chunk(Color.White, if (p.contrast) p.ink else fillC?.step() ?: p.flameStep, 15.dp, 3.dp, 2.dp, { press }))
        }
    }
}

/** Libellés d'extrémité d'un curseur (12 Bold `ink3`). */
@Composable
fun SliderEnds(start: String, end: String) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        Text(start, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink3)
        Text(end, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink3)
    }
}

/* ================================================================== radio, case */

/** PRadio : rond 28 dp dans une cible 48. Éteint : surface, contour 2,5 `control`. Allumé : aplat néon, pastille prune, marche. */
@Composable
fun PRadio(on: Boolean, color: Color? = null, enabled: Boolean = true) {
    val p = LocalPal.current
    val c = vivid(color ?: p.type("challenge").c)
    Box(Modifier.size(48.dp).graphicsLayer { alpha = if (enabled) 1f else .45f }, contentAlignment = Alignment.Center) {
        if (on) Box(Modifier.size(28.dp, 30.dp).chunk(c, if (p.contrast) p.ink else c.step(), 14.dp, 2.dp, 0.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.popOnChange(on, 1.4f).size(10.dp).background(PLUM, CircleShape))
        }
        else Box(Modifier.padding(top = 2.dp).size(28.dp).background(p.surface, CircleShape).border(2.5.dp, p.control, CircleShape))
    }
}

/** Ligne de choix unique : 60 dp, emoji 26, titre 16, sous-ligne 13, PRadio à droite ; choisie = fond teinté. Rôle RadioButton. */
@Composable
fun PRadioRow(title: String, sub: String? = null, on: Boolean, onClick: () -> Unit, emoji: String? = null, color: Color? = null, enabled: Boolean = true,
              leading: (@Composable () -> Unit)? = null) {
    val p = LocalPal.current
    val c = color ?: p.type("challenge").c
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (on) p.selTint(c) else Color.Transparent)
        .selectable(on, enabled = enabled, role = Role.RadioButton) { Feedback.tap(); onClick() }.heightIn(min = 60.dp).padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (leading != null) leading() else if (emoji != null) Emoji(emoji, 26.dp)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (enabled) p.ink else p.ink3)
            if (sub != null) Text(sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink2, lineHeight = 17.sp)
        }
        PRadio(on, c, enabled)
    }
}

/** Groupe de PRadioRow (rôle « groupe de choix »). */
@Composable
fun <T> PRadioGroup(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit, subs: List<String?>? = null, emojis: List<String?>? = null, color: Color? = null) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, (k, l) -> PRadioRow(l, subs?.getOrNull(i), k == value, { onChange(k) }, emojis?.getOrNull(i), color) }
    }
}

/**
 * PCheckbox : carré 28 dp, rayon 9, cible 48. Vide : surface, contour 2,5 `control`. Coché : aplat néon, coche prune
 * dessinée en 200 ms, marche 2. Partiel ([checked] null) : tiret prune.
 */
@Composable
fun PCheckbox(checked: Boolean?, color: Color? = null, enabled: Boolean = true) {
    val p = LocalPal.current
    val c = vivid(color ?: p.type("routine").c)
    val draw = remember { Animatable(if (checked == true) 1f else 0f) }
    LaunchedEffect(checked) { if (checked == true) { if (p.motion) draw.snapTo(1f) else { if (draw.value >= 1f) draw.snapTo(0f); draw.animateTo(1f, tween(200, easing = Motion.Emphasized)) } } else draw.snapTo(0f) }
    Box(Modifier.size(48.dp).graphicsLayer { alpha = if (enabled) 1f else .45f }, contentAlignment = Alignment.Center) {
        if (checked != false) Box(Modifier.size(28.dp, 30.dp).chunk(c, if (p.contrast) p.ink else c.step(), 9.dp, 2.dp, 0.dp), contentAlignment = Alignment.Center) {
            if (checked == true) DrawnCheck(draw.value, PLUM, 20.dp, 3.dp) else Box(Modifier.size(12.dp, 3.dp).background(PLUM, CircleShape))
        } else Box(Modifier.padding(top = 2.dp).size(28.dp).background(p.surface, RoundedCornerShape(9.dp)).border(2.5.dp, p.control, RoundedCornerShape(9.dp)))
    }
}

/** Ligne à cocher : 56 dp, case à gauche, titre 16, sous-ligne. Rôle Checkbox. [checked] null : état partiel. */
@Composable
fun PCheckRow(title: String, sub: String? = null, checked: Boolean?, onChange: (Boolean) -> Unit, color: Color? = null, enabled: Boolean = true,
              strike: Boolean = false, leading: (@Composable () -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPal.current
    val st = when (checked) { true -> ToggleableState.On; false -> ToggleableState.Off; null -> ToggleableState.Indeterminate }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).triStateToggleable(st, enabled = enabled, role = Role.Checkbox) { Feedback.tap(); onChange(checked != true) }
        .heightIn(min = 56.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PCheckbox(checked, color, enabled)
        if (leading != null) leading()
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (checked == true && strike) p.ink3 else p.ink,
                textDecoration = if (checked == true && strike) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
            if (sub != null) Text(sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink2, lineHeight = 17.sp)
        }
        trailing()
    }
}

/* ================================================================== champs de saisie */

/** Bouton d'un champ (effacer, mot de passe, micro) : cible 48, pastille visible 32 `surface2`. */
@Composable
fun FieldBtn(icon: String, desc: String, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = tr(desc), role = Role.Button) { Feedback.tap(); onClick() }.semantics { contentDescription = tr(desc) }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp).background(p.surface2, CircleShape), contentAlignment = Alignment.Center) { Pi(icon, size = 16.dp, tint = p.ink2) }
    }
}

/**
 * PTextField : libellé 13 ExtraBold au-dessus ; boîte 56 (rayon 18, bord 2 + marche 3) ; focus : bord 2,5 et marche en
 * braise ; erreur : bord `danger` et ligne « ✕ message » ; aide en `ok` ; compteur à droite sous la boîte ; boutons de
 * champ de 48 dp. [capsule] : forme de recherche (rayon 28). Désactivé : `surface2` à 45 %.
 */
@Composable
fun PTextField(value: String, onChange: (String) -> Unit, label: String = "", modifier: Modifier = Modifier, placeholder: String = "", icon: String? = null,
               error: String? = null, help: String? = null, counter: Boolean = false, max: Int = 500, singleLine: Boolean = true, minLines: Int = 1, maxLines: Int = if (singleLine) 1 else 12,
               keyboard: KeyboardOptions = KeyboardOptions.Default, actions: KeyboardActions = KeyboardActions.Default, password: Boolean = false, enabled: Boolean = true,
               capsule: Boolean = false, clear: Boolean = singleLine, focusRequester: FocusRequester? = null, trailing: (@Composable RowScope.() -> Unit)? = null,
               emoji: String? = null) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val focused by inter.collectIsFocusedAsState()
    var reveal by remember { mutableStateOf(false) }
    val edge = when { p.contrast -> p.ink; error != null -> p.danger; focused -> p.focusEdge; else -> p.edge }
    val radius = if (capsule) 28.dp else 18.dp
    val showCount = counter || (!singleLine && max <= 2000 && value.length > max * .7)
    BasicTextField(
        value, { if (it.length <= max) onChange(it) },
        modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .semantics { (label.ifBlank { placeholder }).takeIf { it.isNotBlank() }?.let { contentDescription = tr(it) }; if (error != null) stateDescription = tr(error) },
        enabled = enabled, singleLine = singleLine, minLines = if (singleLine) 1 else minLines, maxLines = if (singleLine) 1 else maxLines,
        keyboardOptions = keyboard, keyboardActions = actions, interactionSource = inter,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.ink, fontWeight = FontWeight.SemiBold),
        cursorBrush = SolidColor(p.focusEdge),
        visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { inner ->
            Column(Modifier.graphicsLayer { alpha = if (enabled) 1f else .45f }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (label.isNotBlank()) Text(label, Modifier.padding(start = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (error != null) p.danger else p.ink2, maxLines = 2)
                Row(Modifier.fillMaxWidth().chunk(if (enabled) p.surface else p.surface2, edge, radius, 3.dp, if (focused || error != null) 2.5.dp else 2.dp)
                    .heightIn(min = 49.dp).padding(start = if (icon != null || emoji != null) 12.dp else 16.dp, end = 2.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (icon != null) Pi(icon, Modifier.padding(top = if (singleLine) 0.dp else 14.dp), size = 20.dp, tint = if (focused) p.ink else p.ink3)
                    else if (emoji != null) Emoji(emoji, 22.dp)
                    Box(Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 0.dp)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = p.ink3, fontSize = MaterialTheme.typography.bodyLarge.fontSize, fontWeight = FontWeight.SemiBold,
                            maxLines = if (singleLine) 1 else 3, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                    when {
                        password -> FieldBtn(if (reveal) "eye" else "lock", if (reveal) "Masquer le mot de passe" else "Afficher le mot de passe") { reveal = !reveal }
                        clear && value.isNotEmpty() && (focused || capsule) && enabled -> FieldBtn("x", "Effacer") { onChange("") }
                        else -> Spacer(Modifier.width(if (trailing == null) 10.dp else 0.dp))
                    }
                    trailing?.invoke(this)
                }
                if (error != null || help != null || showCount) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (error != null) { Pi("x", size = 14.dp, tint = p.danger); Text(error, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.danger) }
                    else if (help != null) Text(help, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ok)
                    else Spacer(Modifier.weight(1f))
                    if (showCount) Text("${value.length}/$max", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (value.length >= max) p.danger else p.ink3)
                }
            }
        }
    )
}

/** Zone de texte : 2 à 12 lignes, compteur dès 70 % de [max]. */
@Composable
fun PTextArea(value: String, onChange: (String) -> Unit, label: String = "", modifier: Modifier = Modifier, placeholder: String = "", max: Int = 500, minLines: Int = 2, maxLines: Int = 12,
              error: String? = null, help: String? = null, keyboard: KeyboardOptions = KeyboardOptions.Default, enabled: Boolean = true, focusRequester: FocusRequester? = null) =
    PTextField(value, onChange, label, modifier, placeholder, error = error, help = help, max = max, singleLine = false, minLines = minLines, maxLines = maxLines, keyboard = keyboard,
        enabled = enabled, clear = false, focusRequester = focusRequester)

/** Champ de recherche : capsule, loupe, bouton effacer. */
@Composable
fun PSearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, max: Int = 60, focusRequester: FocusRequester? = null,
                 actions: KeyboardActions = KeyboardActions.Default) =
    PTextField(value, onChange, "", modifier, placeholder, icon = "search", max = max, capsule = true, focusRequester = focusRequester, actions = actions,
        keyboard = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search))

/* ================================================================== liste déroulante et menu */

/** Déclencheur d'une liste : boîte de champ pressable (marche 4), valeur 16 ExtraBold, chevron qui pivote ; ouvert = bord braise. */
@Composable
fun PSelect(label: String?, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, emoji: String? = null, icon: String? = null, open: Boolean = false, enabled: Boolean = true) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter, enabled)
    val rot by animateFloatAsState(if (open) 90f else -90f, if (p.motion) snap() else Motion.move(), label = "chev")
    Column(modifier.graphicsLayer { alpha = if (enabled) 1f else .45f }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, Modifier.padding(start = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
        Row(Modifier.fillMaxWidth().chunk(p.surface, if (open) p.focusEdge else p.edge, 18.dp, 4.dp, if (open) 2.5.dp else 2.dp, press).clip(RoundedCornerShape(16.dp))
            .springClick(inter, enabled = enabled, role = Role.DropdownList, label = label, onClick = onClick).semantics { selected = open }
            .heightIn(min = 48.dp).padding(start = 14.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (emoji != null) Emoji(emoji, 22.dp) else if (icon != null) Pi(icon, size = 20.dp, tint = p.ink2)
            Text(value, Modifier.weight(1f).padding(vertical = 10.dp), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, maxLines = 2)
            Pi("chevronleft", Modifier.graphicsLayer { rotationZ = rot }, 18.dp, p.ink2)
        }
    }
}

/** Option d'un PMenu. */
class MenuItem(val label: String, val icon: String? = null, val emoji: String? = null, val on: Boolean = false, val danger: Boolean = false, val sub: String? = null,
               val enabled: Boolean = true, val onClick: () -> Unit = {})

/** Place le menu sous l'ancre, ou au-dessus si la place manque ; toujours dans l'écran. */
private class MenuPosition(val gap: Int, val margin: Int, val above: MutableState<Boolean>) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.left.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val below = anchorBounds.bottom + gap
        val fits = below + popupContentSize.height <= windowSize.height - margin
        val y = if (fits) below else (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(margin)
        if (above.value == fits) above.value = !fits
        return IntOffset(x, y)
    }
}

/**
 * PMenu : carte flottante `surface`, rayon 22, bord 2 + marche 4 ; lignes de 50 dp (emoji 22 ou icône 20, libellé 16,
 * sous-ligne 13) ; option choisie sur fond teinté avec pastille lime cochée ; option danger séparée par un trait.
 * À poser dans la même boîte que son ancre. Entrée en `pop` depuis l'ancre ; toucher dehors ou Retour ferme.
 */
@Composable
fun PMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<MenuItem>, modifier: Modifier = Modifier, title: String = "Menu", header: (@Composable () -> Unit)? = null,
          width: Dp = 260.dp) {
    if (!expanded) return
    val p = LocalPal.current
    val dens = LocalDensity.current
    val above = remember { mutableStateOf(false) }
    val pos = remember(dens) { MenuPosition(with(dens) { 6.dp.roundToPx() }, with(dens) { 12.dp.roundToPx() }, above) }
    Popup(popupPositionProvider = pos, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(modifier.popIn(TransformOrigin(.2f, if (above.value) 1f else 0f)).width(width).semantics { paneTitle = tr(title) }
            .chunk(p.surface, p.edge, 22.dp, 4.dp, 2.dp).clip(RoundedCornerShape(20.dp)).padding(vertical = 6.dp)) {
            header?.let { it(); Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().height(2.dp).background(p.surface2)) }
            val routine = p.type("routine").c
            items.forEachIndexed { i, it ->
                if (i > 0 && it.danger && !items[i - 1].danger) Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().height(2.dp).background(p.surface2))
                val c = if (it.danger) p.danger else p.ink
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(16.dp)).background(if (it.on) lerp(p.surface, routine, if (p.dark) .14f else .18f) else Color.Transparent)
                    .clickable(enabled = it.enabled, role = Role.DropdownList) { Feedback.tap(); onDismiss(); it.onClick() }.semantics { selected = it.on }
                    .heightIn(min = 50.dp).padding(horizontal = 10.dp).graphicsLayer { alpha = if (it.enabled) 1f else .45f },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (it.emoji != null) Emoji(it.emoji, 22.dp) else if (it.icon != null) Pi(it.icon, size = 20.dp, tint = c)
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(it.label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c)
                        if (it.sub != null) Text(it.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink2, lineHeight = 17.sp)
                    }
                    if (it.on) Box(Modifier.size(24.dp).background(vivid(routine), CircleShape), contentAlignment = Alignment.Center) { Pi("check", size = 14.dp, tint = PLUM) }
                }
            }
        }
    }
}

/** Liste déroulante complète : PSelect + PMenu des [options] (emoji et sous-ligne facultatifs). */
@Composable
fun <T> PSelectMenu(label: String?, options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit, modifier: Modifier = Modifier, emojis: List<String?>? = null,
                    subs: List<String?>? = null, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    val i = options.indexOfFirst { it.first == value }
    Box(modifier) {
        PSelect(label, options.getOrNull(i)?.second ?: "", { open = true }, emoji = emojis?.getOrNull(i), open = open, enabled = enabled)
        PMenu(open, { open = false }, options.mapIndexed { j, (k, l) -> MenuItem(l, emoji = emojis?.getOrNull(j), on = k == value, sub = subs?.getOrNull(j)) { onChange(k) } },
            title = label ?: "Menu", width = 280.dp)
    }
}

/* ================================================================== surcouches : dialogues et feuilles de sélection */

/** Surcouche affichée au-dessus de toute l'interface (dialogue ou feuille de sélection), gérée par [OverlayHost]. */
@Stable
class OverlayEntry(val sheet: Boolean) {
    var content by mutableStateOf<@Composable () -> Unit>({})
    var dismiss by mutableStateOf({})
    var title by mutableStateOf("")
    var closing by mutableStateOf(false)
}

object Overlays {
    val list = mutableStateListOf<OverlayEntry>()
    /** Une surcouche est ouverte (Retour la ferme en premier). */
    val open: Boolean get() = list.any { !it.closing }
    fun back(): Boolean { val e = list.lastOrNull { !it.closing } ?: return false; e.dismiss(); return true }
}

/** Déclare une surcouche tant que l'appelant est affiché ; elle se ferme en fondu quand il disparaît. */
@Composable
fun Overlay(sheet: Boolean, title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val e = remember { OverlayEntry(sheet) }
    e.content = content; e.dismiss = onDismiss; e.title = title
    DisposableEffect(e) { e.closing = false; if (e !in Overlays.list) Overlays.list.add(e); onDispose { e.closing = true } }
}

/** Hôte des surcouches : posé à la racine, au-dessus des feuilles. */
@Composable
fun OverlayHost() {
    Overlays.list.toList().forEach { e -> key(e) { if (e.sheet) OverlaySheet(e) else OverlayDialog(e) } }
}

@Composable
private fun OverlaySheet(e: OverlayEntry) {
    PSheet(visible = !e.closing, onDismiss = { e.dismiss() }, onGone = { Overlays.list.remove(e) }, title = e.title) { e.content() }
}

@Composable
private fun OverlayDialog(e: OverlayEntry) {
    val p = LocalPal.current
    val a = remember { Animatable(0f) }
    LaunchedEffect(e.closing) {
        if (!e.closing) { if (p.motion) a.animateTo(1f, tween(120)) else a.animateTo(1f, Motion.pop()) }
        else { a.animateTo(0f, tween(150)); Overlays.list.remove(e) }
    }
    Box(Modifier.fillMaxSize().drawBehind { drawRect(p.dialogScrim, alpha = a.value.coerceIn(0f, 1f)) }
        .pointerInput(e) { detectTapGestures { if (!e.closing) e.dismiss() } }.imePadding().systemBarsPadding(), contentAlignment = Alignment.Center) {
        Box(Modifier.graphicsLayer {
            val v = a.value; alpha = v.coerceIn(0f, 1f)
            if (!p.motion) { val sc = .9f + .1f * v; scaleX = sc; scaleY = sc }
        }.pointerInput(Unit) { detectTapGestures { } }.semantics { paneTitle = tr(e.title); dismiss { e.dismiss(); true } }) { e.content() }
    }
}

/**
 * PDialog : carte centrée `surface`, rayon 28, bord 2 + marche 6, 328 dp ; emoji 56 incliné qui arrive en `pop` ;
 * titre Unbounded 22 ; texte 15 Bold `ink2` ; contenu libre ; boutons empilés pleine largeur : un seul principal, ou
 * pour une action destructrice un bouton `duel` avec icône ; « Annuler » en secondaire. Voile `dialogScrim`.
 */
@Composable
fun PDialog(onDismiss: () -> Unit, emoji: String, title: String, body: String? = null, confirm: String, onConfirm: () -> Unit, dismiss: String? = "Annuler",
            danger: Boolean = false, icon: String? = null, enabled: Boolean = true, content: (@Composable ColumnScope.() -> Unit)? = null) {
    Overlay(false, title, onDismiss) {
        val p = LocalPal.current
        Column(Modifier.widthIn(max = 328.dp).fillMaxWidth(.9f).chunk(p.surface, p.edge, 28.dp, 6.dp, 2.dp).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.popIn().graphicsLayer { rotationZ = -6f }) { Emoji(emoji, 56.dp) }
            Text(title, Modifier.semantics { heading() }, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp, color = p.ink, textAlign = TextAlign.Center)
            if (body != null) Text(body, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink2, textAlign = TextAlign.Center, lineHeight = 20.sp)
            content?.invoke(this)
            Spacer(Modifier.height(4.dp))
            if (danger) PButton(confirm, onConfirm, Modifier.fillMaxWidth(), kind = "type", color = p.type("duel").c, icon = icon ?: "trash", enabled = enabled)
            else PButton(confirm, onConfirm, Modifier.fillMaxWidth(), kind = "primary", icon = icon, enabled = enabled)
            if (dismiss != null) PButton(dismiss, onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/* ================================================================== feuille */

/**
 * PSheet : feuille opaque `bg`, coins 32, bord 2 dp `edge` en haut, poignée 40 × 5 (cible 48), voile `sheetScrim`.
 * Ouverture rebondissante (ressort `sheetIn`), poignée qui fait un `pop` à l'arrivée ; fermeture en glissant vers le bas
 * (seuil 30 % ou vitesse), par le voile ou Retour ([back] : progression du retour prédictif, la feuille suit le geste).
 * [visible] faux : sortie en 200 ms puis [onGone]. Hauteur maximale 92 % : la scène reste visible au-dessus.
 */
@Composable
fun PSheet(visible: Boolean, onDismiss: () -> Unit, onGone: () -> Unit = {}, title: String = "", handle: Boolean = true, back: () -> Float = { 0f },
           content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val maxH = maxHeight * .92f
        val enter = remember { Animatable(if (p.motion) 0f else hPx) }
        val fade = remember { Animatable(0f) }
        var drag by remember { mutableFloatStateOf(0f) }
        var panelH by remember { mutableIntStateOf(0) }
        val handlePop = remember { Animatable(1f) }
        val dismissNow = rememberUpdatedState(onDismiss)
        LaunchedEffect(visible) {
            if (visible) {
                launch { fade.animateTo(1f, tween(if (p.motion) 120 else 220)) }
                if (!p.motion) { enter.animateTo(0f, Motion.sheetIn()); handlePop.snapTo(1.35f); handlePop.animateTo(1f, Motion.pop()) }
            } else {
                launch { fade.animateTo(0f, tween(if (p.motion) 120 else 200)) }
                if (!p.motion) enter.animateTo(panelH.toFloat().coerceAtLeast(hPx * .4f), tween(200, easing = Motion.Exit)) else delay(120)
                onGone()
            }
        }
        fun settle(v: Float) {
            if (drag > panelH * .3f || v > 1800f) dismissNow.value()
            else scope.launch { androidx.compose.animation.core.animate(drag, 0f, animationSpec = spring(dampingRatio = .8f, stiffness = 500f)) { x, _ -> drag = x } }
        }
        val conn = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < 0f && drag > 0f) { val c = maxOf(available.y, -drag); drag += c; return Offset(0f, c) }
                    return Offset.Zero
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y > 0f && source == NestedScrollSource.UserInput) { drag += available.y; return Offset(0f, available.y) }
                    return Offset.Zero
                }
                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (drag > 0f) { settle(available.y); return available }
                    return Velocity.Zero
                }
            }
        }
        // Voile allégé : la scène reste visible au-dessus de la feuille ; toucher le voile ferme
        Box(Modifier.fillMaxSize().drawBehind { drawRect(p.sheetScrim, alpha = fade.value.coerceIn(0f, 1f)) }
            .pointerInput(Unit) { detectTapGestures { dismissNow.value() } })
        val edge = p.edge; val bg = p.bg
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxH).onSizeChanged { panelH = it.height }
            .graphicsLayer {
                val b = back()
                translationY = enter.value + drag + b * 48.dp.toPx()
                alpha = if (p.motion) fade.value.coerceIn(0f, 1f) else 1f
                if (b > 0f) { val sc = 1f - .04f * b; scaleX = sc; scaleY = sc }
            }
            .drawBehind {
                // Fond prolongé sous le bord de l'écran : le rebond de l'ouverture ne laisse jamais de vide en bas
                val r = 32.dp.toPx(); val ext = 80.dp.toPx(); val b = 2.dp.toPx()
                drawRoundRect(edge, Offset.Zero, Size(size.width, size.height + ext), CornerRadius(r))
                drawRoundRect(bg, Offset(b, b), Size(size.width - 2 * b, size.height + ext), CornerRadius(r - b))
            }
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { if (title.isNotEmpty()) paneTitle = tr(title); dismiss { dismissNow.value(); true } }
            .nestedScroll(conn)) {
            if (handle) Box(Modifier.fillMaxWidth().height(24.dp)
                .draggable(rememberDraggableState { d -> drag = (drag + d).coerceAtLeast(0f) }, Orientation.Vertical, onDragStopped = { v -> settle(v) })
                .semantics { contentDescription = tr("Fermer") }, contentAlignment = Alignment.Center) {
                Box(Modifier.graphicsLayer { scaleX = handlePop.value; scaleY = handlePop.value }.size(40.dp, 5.dp).background(p.ink3, CircleShape))
            }
            content()
        }
    }
}

/* ================================================================== sélecteurs de date et d'heure */

/**
 * PDatePicker : feuille (480 dp) avec l'en-tête du mois et ses boutons ‹ ›, grille 7 × 6 de jours de 44 dp à marche ;
 * aujourd'hui = bord braise, jour choisi = aplat de la flamme ; jours hors bornes à 40 %. Raccourcis en puces et bouton
 * principal « Choisir le 12 nov. ». [value], [min], [max] : AAAA-MM-JJ.
 */
@Composable
fun PDatePicker(value: String?, onPick: (String) -> Unit, onDismiss: () -> Unit, min: String? = null, max: String? = null, title: String = "Choisir une date") {
    Overlay(true, title, onDismiss) { DatePickerBody(value, onPick, onDismiss, min, max, title) }
}

@Composable
private fun DatePickerBody(value: String?, onPick: (String) -> Unit, onDismiss: () -> Unit, min: String?, max: String?, title: String) {
    val p = LocalPal.current
    val today = java.time.LocalDate.now()
    val lo = min?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
    val hi = max?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
    fun ok(d: java.time.LocalDate) = (lo == null || !d.isBefore(lo)) && (hi == null || !d.isAfter(hi))
    var sel by remember { mutableStateOf(value?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: (lo?.takeIf { it.isAfter(today) } ?: today)) }
    var month by remember { mutableStateOf(java.time.YearMonth.from(sel)) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, Modifier.weight(1f).semantics { heading() }, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, maxLines = 2)
            RoundBtn("x", "Fermer", onDismiss)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundBtn("chevronleft", "Mois précédent", { month = month.minusMonths(1) })
            Text(fr.pepsgoal.core.cap(month.month.getDisplayName(java.time.format.TextStyle.FULL, fr.pepsgoal.core.FR)) + " ${month.year}", Modifier.weight(1f).semantics { heading() },
                fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
            RoundBtn("chevronright", "Mois suivant", { month = month.plusMonths(1) })
        }
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                Text(java.time.DayOfWeek.of(i + 1).getDisplayName(java.time.format.TextStyle.SHORT, fr.pepsgoal.core.FR).take(3).removeSuffix("."), Modifier.weight(1f),
                    fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = p.ink3, textAlign = TextAlign.Center)
            }
        }
        val first = month.atDay(1); val lead = first.dayOfWeek.value - 1
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (w in 0 until 6) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (dw in 0 until 7) {
                    val idx = w * 7 + dw - lead
                    Box(Modifier.weight(1f).height(47.dp), contentAlignment = Alignment.Center) {
                        if (idx in 0 until month.lengthOfMonth()) {
                            val d = first.plusDays(idx.toLong()); val on = d == sel; val en = ok(d)
                            DayCell(d.dayOfMonth, on, d == today, en) { sel = d }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("Aujourd’hui" to today, "Demain" to today.plusDays(1), "Dans 1 semaine" to today.plusWeeks(1)).filter { ok(it.second) }.forEach { (l, d) ->
                FChip(l, sel == d, { sel = d; month = java.time.YearMonth.from(d) })
            }
        }
        val dd = "${sel.dayOfMonth} ${sel.month.getDisplayName(java.time.format.TextStyle.SHORT, fr.pepsgoal.core.FR).removeSuffix(".")}."
        PButton("Choisir le $dd",
            { onPick(fr.pepsgoal.core.ymd(sel)); onDismiss() }, Modifier.fillMaxWidth(), kind = "primary", icon = "check", enabled = ok(sel))
    }
}

@Composable
private fun DayCell(n: Int, on: Boolean, today: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter, enabled)
    Box(Modifier.size(44.dp, 47.dp).graphicsLayer { alpha = if (enabled) 1f else .4f }
        .then(if (on) Modifier.chunk(p.flameBrush(), p.flameStep, 22.dp, 3.dp, 0.dp, press) else Modifier.chunk(p.surface, if (today) p.focusEdge else p.edge, 22.dp, 3.dp, if (today) 2.5.dp else 2.dp, press))
        .clip(CircleShape).springClick(inter, enabled = enabled, role = Role.Button, onClick = onClick).semantics { selected = on }, contentAlignment = Alignment.Center) {
        Text("$n", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = if (on) PLUM else p.ink)
    }
}

/**
 * PTimePicker : feuille (420 dp), deux colonnes défilantes (heures 0-23, minutes par 5) en capsules de 56 dp, valeur
 * choisie en aplat de la flamme ; raccourcis « 7:00 », « 12:30 », « 20:00 » ; bouton principal. [value] : « HH:mm ».
 */
@Composable
fun PTimePicker(value: String, onPick: (String) -> Unit, onDismiss: () -> Unit, title: String = "Choisir une heure") {
    Overlay(true, title, onDismiss) { TimePickerBody(value, onPick, onDismiss, title) }
}

@Composable
private fun TimePickerBody(value: String, onPick: (String) -> Unit, onDismiss: () -> Unit, title: String) {
    val parts = value.split(":")
    var h by remember { mutableIntStateOf(parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8) }
    var m by remember { mutableIntStateOf(parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, Modifier.weight(1f).semantics { heading() }, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, maxLines = 2)
            RoundBtn("x", "Fermer", onDismiss)
        }
        Row(Modifier.fillMaxWidth().height(232.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TimeColumn((0..23).toList(), h, { h = it }, Modifier.weight(1f), "heures")
            Text(":", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
            val mins = ((0..55 step 5).toList() + m).distinct().sorted()
            TimeColumn(mins, m, { m = it }, Modifier.weight(1f), "minutes")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 to 0, 12 to 30, 20 to 0).forEach { (hh, mm) -> FChip("$hh:${"%02d".format(mm)}", h == hh && m == mm, { h = hh; m = mm }) }
        }
        val txt = "%02d:%02d".format(h, m)
        PButton("Choisir $txt", { onPick(txt); onDismiss() }, Modifier.fillMaxWidth(), kind = "primary", icon = "check")
    }
}

@Composable
private fun TimeColumn(values: List<Int>, sel: Int, onSel: (Int) -> Unit, modifier: Modifier, unit: String) {
    val p = LocalPal.current
    val st = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (values.indexOf(sel) - 1).coerceAtLeast(0))
    androidx.compose.foundation.lazy.LazyColumn(modifier.fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(p.surface2).semantics { contentDescription = tr(unit) },
        state = st, contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(values.size) { i ->
            val v = values[i]; val on = v == sel
            val inter = remember { MutableInteractionSource() }
            val press = rememberPress(inter)
            Box(Modifier.fillMaxWidth().height(56.dp).then(if (on) Modifier.chunk(p.flameBrush(), p.flameStep, 28.dp, 3.dp, 0.dp, press) else Modifier.padding(bottom = 3.dp))
                .clip(CircleShape).springClick(inter, role = Role.RadioButton) { onSel(v) }.semantics { selected = on }, contentAlignment = Alignment.Center) {
                Text("%02d".format(v), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = if (on) PLUM else p.ink2)
            }
        }
    }
}

/* ================================================================== onglets de page */

/**
 * PTabs : rail capsule `surface` + bord 2 (il sert aussi de plaque), onglet choisi = pastille à marche en néon de la page
 * ([color], sinon flamme), texte prune 14, emoji 18 ; au repos texte `ink2`. Jusqu'à 3 options : largeurs égales ; au-delà :
 * défilement, l'onglet choisi est ramené à l'écran. [badges] : pastille rose par onglet.
 */
@Composable
fun PTabs(options: List<Pair<String, String?>>, sel: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, color: Color? = null, badges: List<Int>? = null) {
    val p = LocalPal.current
    val c = color?.let { vivid(it) }
    val scroll = options.size > 3
    val ss = rememberScrollState()
    val xs = remember(options.size) { IntArray(options.size) }
    val ws = remember(options.size) { IntArray(options.size) }
    if (scroll) LaunchedEffect(sel) {
        val x = xs.getOrElse(sel) { 0 }; val w = ws.getOrElse(sel) { 0 }
        val vp = ss.viewportSize
        if (vp > 0 && (x < ss.value || x + w > ss.value + vp)) { if (p.motion) ss.scrollTo((x - vp / 3).coerceAtLeast(0)) else ss.animateScrollTo((x - vp / 3).coerceAtLeast(0)) }
    }
    Row(modifier.fillMaxWidth().background(p.surface, CircleShape).border(2.dp, p.edge, CircleShape).clip(CircleShape).padding(4.dp).selectableGroup()
        .then(if (scroll) Modifier.horizontalScroll(ss) else Modifier.height(IntrinsicSize.Min)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, (l, e) ->
            val on = i == sel
            val inter = remember { MutableInteractionSource() }
            val press = rememberPress(inter)
            Row((if (scroll) Modifier else Modifier.weight(1f).fillMaxHeight()).onGloballyPositioned { xs[i] = it.positionInParent().x.roundToInt(); ws[i] = it.size.width }
                .then(if (on) (if (c != null) Modifier.chunk(c, if (p.contrast) p.ink else c.step(), 22.dp, 3.dp, 0.dp, press) else Modifier.chunk(p.flameBrush(), p.flameStep, 22.dp, 3.dp, 0.dp, press)) else Modifier.padding(bottom = 3.dp))
                .clip(CircleShape).selectable(on, inter, androidx.compose.foundation.LocalIndication.current, role = Role.Tab) { Feedback.tap(); onSelect(i) }
                .heightIn(min = 45.dp).padding(horizontal = if (scroll) 14.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                if (e != null) Emoji(e, 18.dp)
                Text(l, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (on) PLUM else p.ink2, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 16.sp)
                val b = badges?.getOrNull(i) ?: 0
                if (b > 0) Badge(b)
            }
        }
    }
}

/* ================================================================== chargement */

private val SPIN_COLORS = listOf(Color(0xFFFFB000), Color(0xFFFF4A12), Color(0xFFFF3D96))

/**
 * PSpinner : trois pastilles (jaune, braise, rose) qui sautent tour à tour, 15 images par seconde au plus (graphicsLayer).
 * Animations réduites : pastilles fixes qui pulsent doucement en opacité.
 */
@Composable
fun PSpinner(size: Dp = 10.dp, modifier: Modifier = Modifier, label: String = "Chargement…") {
    val p = LocalPal.current
    val ph = if (p.motion) produceState(0f) { var t = 0f; while (true) { delay(250); t = (t + .25f) % 1f; value = t } } else slowPhaseState(900, 15)
    Row(modifier.height(size * 2.2f).semantics { contentDescription = tr(label) }, horizontalArrangement = Arrangement.spacedBy(size * .5f), verticalAlignment = Alignment.Bottom) {
        SPIN_COLORS.forEachIndexed { i, c ->
            val s = size * (.6f + .2f * i)
            Box(Modifier.graphicsLayer {
                if (p.motion) alpha = .55f + .45f * (.5f + .5f * sin((ph.value + i / 3f) * 2f * PI.toFloat()))
                else { val k = sin(((ph.value - i * .18f) * 2f * PI).toFloat()).coerceAtLeast(0f); translationY = -k * size.toPx() }
            }.size(s).background(if (p.contrast) p.ink else c, CircleShape))
        }
    }
}

/** Barre indéterminée : rail creusé de 10 dp et segment de la flamme (30 %) qui glisse. */
@Composable
fun PIndeterminate(modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val ph = slowPhaseState(1400, 30)
    Canvas(modifier.fillMaxWidth().height(10.dp).semantics { contentDescription = tr("Chargement…") }) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(p.rail, cornerRadius = r)
        val at = if (p.motion) .35f else ph.value * 1.3f - .3f
        val x0 = (size.width * at).coerceAtLeast(0f); val x1 = (size.width * (at + .3f)).coerceAtMost(size.width)
        if (x1 > x0) drawRoundRect(p.flameBrush(), Offset(x0, 0f), Size(x1 - x0, size.height), r)
    }
}

/* ================================================================== pas à pas */

/** Bouton rond du PStepper : répète l'action à l'appui long, de plus en plus vite (cran haptique à chaque pas). */
@Composable
private fun RepeatBtn(icon: String, desc: String, enabled: Boolean, onStep: () -> Unit) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter, enabled)
    val step = rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(44.dp, 47.dp).graphicsLayer { alpha = if (enabled) 1f else .4f }.chunk(p.surface, p.edge, 22.dp, 3.dp, 2.dp, press).clip(CircleShape)
            .semantics { contentDescription = tr(desc); role = Role.Button }
            .then(if (!enabled) Modifier else Modifier.pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val pr = androidx.compose.foundation.interaction.PressInteraction.Press(it)
                    inter.emit(pr); step.value(); Feedback.tick()
                    val job = scope.launch { delay(420); var d = 160L; while (true) { step.value(); Feedback.tick(); delay(d); d = (d * 85 / 100).coerceAtLeast(45L) } }
                    val ok = tryAwaitRelease(); job.cancel()
                    inter.emit(if (ok) androidx.compose.foundation.interaction.PressInteraction.Release(pr) else androidx.compose.foundation.interaction.PressInteraction.Cancel(pr))
                })
            }), contentAlignment = Alignment.Center) { Pi(icon, size = 20.dp, tint = p.ink) }
    }
}

/** PStepper : − valeur + ; valeur Unbounded 22 et unité 14 ; appui long = répétition accélérée. */
@Composable
fun PStepper(value: String, unit: String = "", onMinus: () -> Unit, onPlus: () -> Unit, minusEnabled: Boolean = true, plusEnabled: Boolean = true, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RepeatBtn("minus", "Moins", minusEnabled, onMinus)
        Row(Modifier.widthIn(min = 64.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            Text(value, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = p.ink, maxLines = 1)
            if (unit.isNotEmpty()) Text(" $unit", Modifier.padding(bottom = 2.dp), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2, maxLines = 1)
        }
        RepeatBtn("plus", "Plus", plusEnabled, onPlus)
    }
}

/* ================================================================== couronne de flammes et médaillon */

/**
 * Couronne de flammes autour d'un avatar, en aplats : langues pleines sur trois plans (rose, braise, jaune), sans dégradé
 * vers le transparent ni halo. Hauteur `18 + 40 × k` dp et nombre `9 + 4k` selon la progression [k] (0 au niveau 1, 1 en
 * Supernova) ; [colors] : couleurs du palier au-delà du palier 6. [phase] lue au dessin (vacillement).
 */
fun Modifier.flameCrown(k: Float, radius: Dp, colors: List<Color>? = null, phase: () -> Float = { .2f }): Modifier = this.drawBehind {
    val c = Offset(size.width / 2, size.height / 2)
    val r0 = radius.toPx()
    val kk = k.coerceIn(0f, 1f)
    val n = 9 + (kk * 4).toInt()
    val hMax = (18f + 40f * kk).dp.toPx()
    val t = phase()
    val cols = colors ?: listOf(Color(0xFFFF3D96), Color(0xFFFF4A12), Color(0xFFFFB000))
    fun layer(scale: Float, col: Color, shift: Float, widen: Float) {
        for (i in 0 until n) {
            val a = ((i + shift) / n) * 2f * PI.toFloat() - PI.toFloat() / 2
            val dx = cos(a); val dy = sin(a)
            // Les flammes montent : hautes en haut du cercle, presque nulles en bas
            val top = (1f - dy) / 2f
            val wob = .8f + .2f * sin(t * 6.28f * 2 + i * 1.9f + shift * 4f)
            val h = hMax * scale * wob * (.12f + .88f * top * top)
            val half = (PI.toFloat() / n) * widen
            val l = Offset(c.x + cos(a - half) * r0, c.y + sin(a - half) * r0)
            val rr = Offset(c.x + cos(a + half) * r0, c.y + sin(a + half) * r0)
            var ux = dx * .55f; var uy = dy * .55f - .75f
            val len = kotlin.math.sqrt(ux * ux + uy * uy).coerceAtLeast(.01f); ux /= len; uy /= len
            val bx = c.x + dx * r0; val by = c.y + dy * r0
            // Pointe recourbée (balancement) : la langue s'effile en virgule
            val sway = .35f * sin(i * 2.3f + t * 6.28f)
            val tip = Offset(bx + (ux + sway * -uy) * h, by + (uy + sway * ux) * h)
            val px = -uy; val py = ux; val bw = r0 * half * 1.1f
            val c1 = Offset(bx + ux * h * .45f - px * bw, by + uy * h * .45f - py * bw)
            val c2 = Offset(bx + ux * h * .45f + px * bw, by + uy * h * .45f + py * bw)
            drawPath(Path().apply {
                moveTo(l.x, l.y); cubicTo(c1.x - px * bw * .2f, c1.y - py * bw * .2f, tip.x - px * bw * .15f, tip.y - py * bw * .15f, tip.x, tip.y)
                cubicTo(tip.x + px * bw * .1f, tip.y + py * bw * .1f, c2.x, c2.y, rr.x, rr.y); close()
            }, col)
        }
    }
    layer(1f, cols[0], 0f, 1.5f)
    layer(.72f, cols.getOrElse(1) { cols[0] }, .5f, 1.35f)
    layer(.44f, cols.getOrElse(2) { cols.last() }, 0f, 1.1f)
}

/**
 * Médaillon d'avatar : disque blanc à marche (5 dp, `flameStep` à 55 %) qui s'enfonce à l'appui, couronne de flammes
 * facultative ([crown] : progression 0 → 1) qui vacille à 15 images par seconde pendant 20 s (figée en Animations
 * réduites), badge du niveau prune et or. [size] : taille de l'avatar.
 */
@Composable
fun AvatarMedallion(size: Dp, level: Int?, crown: Float?, onClick: (() -> Unit)?, desc: String = "Avatar", crownColors: List<Color>? = null, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter, onClick != null)
    var live by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(20_000); live = false }
    val ph = slowPhaseState(2400, 15, running = live && crown != null && fr.pepsgoal.app.sys.Perf.flags.fx)
    Box(Modifier.size(size + 92.dp), contentAlignment = Alignment.Center) {
        if (crown != null && !p.contrast) Box(Modifier.size(size + 8.dp).flameCrown(crown, (size + 8.dp) / 2 - 3.dp, crownColors) { ph.value })
        Box(Modifier.offset(y = 2.dp).size(size + 8.dp, size + 13.dp).chunk(Color.White, if (p.contrast) p.ink else p.flameStep.copy(alpha = .55f), (size + 8.dp) / 2, 5.dp, 0.dp, press)
            .clip(CircleShape).then(if (onClick != null) Modifier.springClick(inter, label = desc, role = Role.Button, onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center, content = content)
        if (level != null) Box(Modifier.align(Alignment.Center).offset(x = size * .42f, y = size * .44f).chunk(PLUM, PLUM_DEEP, 14.dp, 3.dp, 0.dp).padding(horizontal = 9.dp, vertical = 2.dp)) {
            Text("$level", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = p.gold)
        }
    }
}

/* ================================================================== voile et cartes de chaleur */

/** Présence du fond : voile (fraction de `bg`) en haut, au milieu, en bas. 0 discrète, 1 équilibrée (défaut), 2 intense. */
fun veilStops(level: Int, dark: Boolean): Triple<Float, Float, Float> = when (level) {
    0 -> if (dark) Triple(.40f, .72f, .82f) else Triple(.34f, .70f, .82f)
    2 -> if (dark) Triple(.04f, .22f, .34f) else Triple(.0f, .18f, .32f)
    -1 -> Triple(0f, 0f, 0f)
    else -> if (dark) Triple(.12f, .40f, .55f) else Triple(.06f, .38f, .55f)
}

/* ================================================================== visionneuses, envoi */

/** Bouton rond sombre à marche, posé sur un média (visionneuses de photos et de stories). */
@Composable
fun MediaBtn(icon: String, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter)
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(44.dp, 47.dp).chunk(MEDIA_BTN, Color.Black, 22.dp, 3.dp, 2.dp, press).clip(CircleShape).springClick(inter, label = desc, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = tr(desc) }, contentAlignment = Alignment.Center) { Pi(icon, size = 20.dp, tint = CREAM) }
    }
}
private val MEDIA_BTN = Color(0xFF241A2B)

/** Bouton Envoyer : rond de la flamme à marche, seul bouton principal d'une barre de saisie. */
@Composable
fun SendBtn(desc: String, enabled: Boolean = true, icon: String = "send", onClick: () -> Unit) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter, enabled)
    Box(Modifier.size(52.dp, 56.dp).graphicsLayer { alpha = if (enabled) 1f else .45f }.chunk(p.flameBrush(), p.flameStep, 26.dp, 4.dp, 0.dp, press).clip(CircleShape)
        .springClick(inter, enabled = enabled, label = desc, role = Role.Button, onClick = onClick).semantics { contentDescription = tr(desc) }, contentAlignment = Alignment.Center) {
        Pi(icon, size = 22.dp, tint = if (p.contrast) p.bg else p.onAccent)
    }
}

/** Tuile de fond (Apparence, fond d'une conversation) : 78 × 104 à marche, aperçu figé, choisie = bord braise 3 dp. */
@Composable
fun BgTile(label: String, sub: String?, on: Boolean, onClick: () -> Unit, enabled: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter, enabled)
    Column(Modifier.width(80.dp).graphicsLayer { alpha = if (enabled) 1f else .45f }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(78.dp, 104.dp).chunk(p.surface, if (on) p.focusEdge else p.edge, 18.dp, 4.dp, if (on) 3.dp else 2.dp, press).padding(2.dp).clip(RoundedCornerShape(14.dp))
            .springClick(inter, enabled = enabled, label = label, role = Role.RadioButton, onClick = onClick).semantics { selected = on }, content = content)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (on) p.ink else p.ink2, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 16.sp)
        if (sub != null) Text(sub, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink3, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/* ================================================================== pastilles et tuiles de choix */

/** Pastille de couleur ronde à marche (44 dp dans une cible 48) ; choisie : bord braise 3 dp et coche prune. */
@Composable
fun PSwatch(fill: Brush, on: Boolean, name: String, onClick: () -> Unit, size: Dp = 44.dp) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter)
    Box(Modifier.size(maxOf(48.dp, size + 4.dp)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size, size + 3.dp).chunk(fill, if (on) p.focusEdge else p.edge, size / 2, 3.dp, if (on) 3.dp else 2.dp, press).clip(CircleShape)
            .springClick(inter, label = name, role = Role.RadioButton, onClick = onClick).semantics { selected = on }, contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(size * .55f).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Pi("check", size = size * .36f, tint = PLUM) }
        }
    }
}

/** Tuile de choix carrée à marche (emoji, picto) : rayon 17, choisie = teinte du néon et bord braise 3 dp. */
@Composable
fun PickTile(on: Boolean, desc: String, onClick: () -> Unit, size: Dp = 48.dp, color: Color? = null, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPal.current
    val inter = remember { MutableInteractionSource() }
    val press = rememberPress(inter)
    Box(Modifier.size(size, size + 3.dp).chunk(if (on) p.selTint(color ?: p.flame) else p.surface, if (on) p.focusEdge else p.edge, 17.dp, 3.dp, if (on) 3.dp else 2.dp, press)
        .clip(RoundedCornerShape(17.dp)).springClick(inter, label = desc, role = Role.RadioButton, onClick = onClick).semantics { selected = on },
        contentAlignment = Alignment.Center, content = content)
}
