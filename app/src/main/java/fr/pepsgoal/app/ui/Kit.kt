package fr.pepsgoal.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import kotlinx.coroutines.launch

/*
 * Briques visuelles de l'accueil et des listes, sans état de l'application : flamme de la série, semaine, en-tête du
 * jour, tuiles « À la une », titres de section.
 */

/** Grande flamme de la série, qui respire ; [compact] : flamme plus petite et nombre seul (en-tête de l'accueil). */
@Composable
fun HeroFlame(n: Int, lit: Boolean, onTickle: (() -> Unit)? = null, compact: Boolean = false) {
    val p = LocalPal.current
    // Flamme qui respire, à 15 images par seconde
    val ph = slowPhaseState(2800, 15)
    val count = remember { Animatable(0f) }
    LaunchedEffect(n) { if (p.motion) count.snapTo(n.toFloat()) else count.animateTo(n.toFloat(), tween(minOf(1500, 600 + n * 6), easing = FastOutSlowInEasing)) }
    // Chatouilles : chaque toucher fait gigoter la flamme ; 7 touchers rapprochés, elle bondit (surprise)
    val wiggle = remember { Animatable(0f) }; val jump = remember { Animatable(0f) }
    val taps = remember { mutableListOf<Long>() }; val scope = rememberCoroutineScope()
    val tickle = if (onTickle == null) Modifier else Modifier.clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClickLabel = "Flamme") {
        val now = android.os.SystemClock.uptimeMillis(); taps.removeAll { now - it > 2500 }; taps.add(now)
        Feedback.ramp((taps.size / 7f).coerceAtMost(1f))
        scope.launch { wiggle.snapTo(if (taps.size % 2 == 0) 1f else -1f); wiggle.animateTo(0f, spring(dampingRatio = .25f, stiffness = 900f)) }
        if (taps.size >= 7) { taps.clear(); scope.launch { jump.snapTo(0f); jump.animateTo(1f, tween(700, easing = FastOutSlowInEasing)); jump.snapTo(0f) }; onTickle() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(tickle.graphicsLayer {
            val f = if (lit) 1f + .03f * (1f - kotlin.math.cos(ph.value * 2f * kotlin.math.PI.toFloat())) else 1f
            val j = kotlin.math.sin(jump.value * kotlin.math.PI.toFloat())
            scaleX = f * (1f + .25f * j); scaleY = f * (1f + .25f * j); translationY = -j * 40.dp.toPx(); rotationZ = wiggle.value * 12f + jump.value * 360f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
        }) { Flame(if (compact) 56.dp else 84.dp, dim = !lit) }
        Text("${count.value.toInt()}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = if (compact) 20.sp else 30.sp, lineHeight = if (compact) 22.sp else 34.sp)
        Text(if (compact) (if (n > 1) "jours" else "jour") else if (n > 1) "jours de série" else "jour de série", fontSize = if (compact) 11.sp else 12.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
    }
}

/** Jour de la semaine : lettre, état (0 rien, 1 gel de série, 2 actif), futur, aujourd'hui. */
class WeekDay(val label: String, val state: Int, val future: Boolean, val today: Boolean)

/** Les 7 jours de la semaine : flamme allumée les jours actifs, flocon les jours protégés par un gel. */
@Composable
fun WeekStripView(days: List<WeekDay>) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { d ->
            Column(Modifier.clip(RoundedCornerShape(14.dp)).background(if (d.today) p.tonal else Color.Transparent)
                .then(if (d.today) Modifier.border(1.dp, p.flame.copy(alpha = .35f), RoundedCornerShape(14.dp)) else Modifier).padding(horizontal = 7.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (d.state == 1) Pi("snow", size = 22.dp, tint = p.type("objective").ink) else Flame(22.dp, dim = d.state != 2)
                Text(d.label, fontSize = 12.sp, fontWeight = if (d.today) FontWeight.ExtraBold else FontWeight.Bold, color = if (d.today) p.ink else if (d.future) p.ink3 else p.ink2)
            }
        }
    }
}

/** Objectif mis en avant ce mois-ci : icône, titre, jours validés sur jours prévus. */
class FocusInfo(val icon: String, val title: String, val done: Int, val planned: Int)

/**
 * En-tête de l'accueil : salutation et message du jour, flamme de la série, puis l'avancement du jour (anneau,
 * niveau, flammes), l'objectif du mois et la semaine. Les actions de la journée sont posées sous la carte.
 */
@Composable
fun HomeHeaderView(
    name: String, msg: String, streak: Int, lit: Boolean, done: Int, total: Int, lvIdx: Int, lvName: String, lvPct: Double, wallet: String,
    focus: FocusInfo?, onFocus: () -> Unit, onFlame: () -> Unit, week: @Composable () -> Unit
) {
    val p = LocalPal.current
    Glass(pad = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Salut $name", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(msg, fontSize = 15.sp, color = p.ink2, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            HeroFlame(streak, lit, onFlame, compact = true)
        }
        Spacer(Modifier.height(16.dp))
        // Avancement du jour, niveau et flammes en un coup d'œil
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.tonal).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Ring(done, total, 62.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (total == 0) "Rien de prévu aujourd’hui" else if (done == total) "Tout est validé !" else "$done sur $total validés", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Bar(lvPct * 100, height = 6.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Niveau $lvIdx · $lvName", Modifier.weight(1f), fontSize = 13.sp, color = p.ink2, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) { Flame(15.dp); Text(wallet, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
        // Objectif du mois
        focus?.let { f ->
            Row(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.flame.copy(alpha = if (p.dark) .14f else .09f)).clickable(onClickLabel = "Ouvrir l’objectif du mois", onClick = onFocus).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Emoji(f.icon, 26.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Eyebrow("Objectif du mois", p.flameInk)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.title, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${f.done}/${f.planned} j", fontWeight = FontWeight.ExtraBold, color = p.ink2, fontSize = 13.sp)
                    }
                    Bar(f.done * 100.0 / maxOf(1, f.planned), height = 5.dp)
                }
            }
        }
        Spacer(Modifier.height(12.dp)); week()
    }
}

/** Rangée d'actions de la journée (puces), sous l'en-tête : elle défile jusqu'aux bords de l'écran. */
@Composable
fun ActionChips(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().bleed(16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)

/** Tuile « À la une » : emoji, titre, une ou deux lignes ; touchée, elle ouvre la carte complète. */
@Composable
fun MomentTileView(emoji: String, title: String, sub: String, onClick: () -> Unit) {
    val p = LocalPal.current
    Glass(Modifier.width(156.dp).height(124.dp), radius = 22.dp, pad = 14.dp, fill = false, onClick = onClick) {
        Emoji(emoji, 28.dp)
        Spacer(Modifier.weight(1f))
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, fontSize = 12.sp, color = p.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
    }
}

/** Tuile d'ajout (contour pointillé) en fin de rangée « À la une ». */
@Composable
fun AddTileView(label: String, onClick: () -> Unit) {
    val p = LocalPal.current
    Column(Modifier.width(118.dp).height(124.dp).clip(RoundedCornerShape(22.dp)).border(1.5.dp, p.ink.copy(alpha = .16f), RoundedCornerShape(22.dp)).clickable(onClickLabel = label, onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(p.tonal), contentAlignment = Alignment.Center) { Pi("plus", size = 20.dp, tint = p.ink2) }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink2, maxLines = 2)
    }
}

/** Titre de section repliable (accueil) : titre, compteur facultatif, chevron. */
@Composable
fun SectionHead(title: String, icon: String, collapsed: Boolean, onToggle: () -> Unit, count: Int? = null) {
    val p = LocalPal.current
    val rot by animateFloatAsState(if (collapsed) -90f else 0f, label = "secRot")
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = if (collapsed) "Déplier $title" else "Replier $title", onClick = onToggle).padding(horizontal = 6.dp, vertical = 6.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pi(icon, size = 18.dp, tint = p.flameInk)
        Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
        if (count != null) Text("$count", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink2)
        Box(Modifier.size(28.dp).clip(CircleShape).background(p.tonal), contentAlignment = Alignment.Center) {
            Pi("down", size = 16.dp, tint = p.ink2, modifier = Modifier.graphicsLayer { rotationZ = rot })
        }
    }
}

/**
 * Onglets de filtre (pilules) avec leur compteur : l'onglet choisi est plein (encre), les autres tonals.
 * La rangée défile jusqu'aux bords de l'écran.
 */
@Composable
fun FilterTabs(items: List<Triple<String, String, String?>>, selected: String, onSelect: (String) -> Unit, bleed: Dp = 16.dp) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().bleed(bleed).horizontalScroll(rememberScrollState()).padding(horizontal = bleed), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (k, l, n) ->
            val on = k == selected
            val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Row(Modifier.bounce(inter, .93f).heightIn(min = 40.dp).clip(CircleShape).background(if (on) p.ink else ctlFill()).then(if (on) Modifier else Modifier.ctlEdgeIfPage(CircleShape)).springClick(inter, role = androidx.compose.ui.semantics.Role.Tab) { onSelect(k) }
                .semantics { this.selected = on }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(l, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = if (on) p.bg else p.ink)
                if (n != null) Text(n, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (on) p.bg.copy(alpha = .7f) else p.ink2)
            }
        }
    }
}

/** Puce de tri : affiche le tri choisi et ouvre la liste des tris. */
@Composable
fun SortChip(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(false) }
    Box {
        FChip(options.firstOrNull { it.first == selected }?.second ?: "", false, { open = true }, leading = { Pi("trend", size = 15.dp, tint = p.ink2) })
        androidx.compose.material3.DropdownMenu(open, { open = false }, shape = RoundedCornerShape(18.dp), containerColor = p.surface.copy(alpha = 1f)) {
            options.forEach { (k, l) ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(l, fontWeight = if (k == selected) FontWeight.ExtraBold else FontWeight.Medium) }, onClick = { open = false; onSelect(k) },
                    leadingIcon = { if (k == selected) Pi("check", size = 18.dp, tint = p.flameInk) else Spacer(Modifier.size(18.dp)) })
            }
        }
    }
}

/** Chiffre d'une bande de statistiques : icône (ou flamme), valeur, libellé. */
class StatItem(val icon: String, val value: String, val label: String, val lit: Boolean = true)

/** Bande de chiffres clés dans une seule carte, séparés par de fins traits verticaux. */
@Composable
fun StatStrip(items: List<StatItem>, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Glass(modifier, pad = 0.dp, radius = 22.dp) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 14.dp)) {
            items.forEachIndexed { i, it ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 6.dp).background(p.line))
                Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { if (it.icon == "flameo") Flame(22.dp, dim = !it.lit) else Pi(it.icon, size = 19.dp, tint = p.flameInk) }
                    FitText(it.value, Modifier.fillMaxWidth(), size = 19, min = 12, weight = FontWeight.ExtraBold, family = Unbounded, align = androidx.compose.ui.text.style.TextAlign.Center)
                    FitText(it.label, Modifier.fillMaxWidth(), size = 11, min = 8, color = p.ink2, weight = FontWeight.Bold, align = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}
