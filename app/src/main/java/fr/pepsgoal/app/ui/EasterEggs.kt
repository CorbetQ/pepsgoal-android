package fr.pepsgoal.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.Eggs
import fr.pepsgoal.core.eggsFound
import fr.pepsgoal.core.findEgg
import fr.pepsgoal.core.model.User
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Déclenche une surprise : la première fois, elle rejoint la collection et rapporte quelques flammes ; [fx] lance un
 * effet par-dessus l'écran (« confetti », « fish », « spooky »). [say] : afficher aussi [text] en message.
 */
fun AppState.egg(key: String?, text: String, fx: String? = null, say: Boolean = true) {
    val first = key != null && pg.findEgg(key)
    if (first) {
        Feedback.success()
        val e = Eggs.ALL.first { it.key == key }
        toast(if (say) "$text\n${e.emoji} Surprise trouvée : +${Eggs.BONUS} flammes" else "${e.emoji} Surprise trouvée : « ${e.name} » · +${Eggs.BONUS} flammes", "star")
        bump()
    } else if (say) toast(text, "sparkle")
    if (fx != null) { eggFx = fx; eggTick++ }
}

/** Effet visuel d'une surprise, posé au-dessus de tout (ne bloque pas les touches), environ 6 secondes. */
@Composable
fun EggOverlay(ui: AppState) {
    val fx = ui.eggFx ?: return
    val p = LocalPal.current
    // Fin de l'effet 6,5 s après le dernier déclenchement (relancé par chaque nouvelle surprise)
    LaunchedEffect(ui.eggTick) { delay(6500); ui.eggFx = null }
    if (!p.motion) key(ui.eggTick) {
        when (fx) {
            "confetti" -> fr.pepsgoal.app.ui.fullscreen.Confetti()
            "fish" -> Drifters(listOf("🐟", "🐠", "🐡"), 7)
            "spooky" -> Drifters(listOf("🦇", "🎃", "👻"), 8)
        }
    }
}

/** Quelques émojis qui traversent l'écran en ondulant (poissons d'avril, chauves-souris d'Halloween…). */
@Composable
private fun Drifters(emojis: List<String>, n: Int) {
    val h = LocalConfiguration.current.screenHeightDp; val w = LocalConfiguration.current.screenWidthDp
    val items = remember { List(n) { Triple(emojis[it % emojis.size], Random.nextFloat(), Random.nextFloat()) } }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(6000, easing = LinearEasing)) }
    Box(Modifier.fillMaxSize()) {
        for (i in items.indices) {
            val (e, y0, d) = items[i]
            val k = (t.value * 1.6f - d * .6f).coerceIn(0f, 1f)
            if (k <= 0f || k >= 1f) continue
            val ltr = i % 2 == 0
            Text(e, Modifier.graphicsLayer {
                translationX = ((if (ltr) k else 1f - k) * (w + 80) - 40).dp.toPx()
                translationY = ((.12f + y0 * .7f) * h + kotlin.math.sin(k * 12f + i) * 18f).dp.toPx()
                scaleX = if (ltr) -1f else 1f
                rotationZ = kotlin.math.sin(k * 9f + i) * 10f
            }, fontSize = (26 + (d * 14).toInt()).sp)
        }
    }
}

/** Petite surprise du jour (fête, 3 h du matin) : une fois par jour, peu après l'ouverture. */
@Composable
fun DailyEggs(ui: AppState, u: User?) {
    LaunchedEffect(u?.id) {
        if (u == null) return@LaunchedEffect
        delay(2200)
        val t = fr.pepsgoal.core.today()
        Eggs.day()?.let { d -> if (ui.prefs.eggDay != t) { ui.prefs.eggDay = t; ui.egg("calendar", d.text, d.fx) } }
        if (Eggs.owl(java.time.LocalTime.now().hour) && ui.prefs.owlDay != t) { ui.prefs.owlDay = t; ui.egg("owl", "Il est 3 h du matin… Ta série peut attendre, ton sommeil non 😴") }
    }
}

/** Collection des surprises (Profil › Collection) : trouvées, et un indice pour les autres. */
@Composable
fun EggsCard(ui: AppState, u: User) {
    val p = LocalPal.current
    val found = remember(ui.rev, u.id) { ui.pg.eggsFound(u) }
    Glass {
        CardHead("Surprises cachées", emoji = "🎁") { Count("${found.size} / ${Eggs.ALL.size}") }
        Muted(if (found.isEmpty()) "PepsGoal cache quelques surprises. Chacune rapporte ${Eggs.BONUS} flammes la première fois." else "Chaque surprise rapporte ${Eggs.BONUS} flammes la première fois. Sauras-tu toutes les trouver ?", small = true)
        Spacer(Modifier.height(10.dp))
        Bar(found.size * 100.0 / Eggs.ALL.size, height = 10.dp)
        Spacer(Modifier.height(6.dp))
        Eggs.ALL.forEach { e ->
            val ok = e.key in found
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(if (ok) p.selTint(p.flame) else p.surface2, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
                    if (ok) Emoji(e.emoji, 22.dp) else Text("?", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = p.ink3)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (ok) e.name else "Surprise cachée", fontWeight = FontWeight.ExtraBold, color = if (ok) p.ink else p.ink2)
                    Text(if (ok) "Trouvée · +${Eggs.BONUS} flammes" else "Indice : ${e.hint}", fontSize = 13.sp, color = p.ink2)
                }
                if (ok) Box(Modifier.size(22.dp).clip(CircleShape).background(p.flame), contentAlignment = Alignment.Center) { Pi("check", size = 14.dp, tint = fr.pepsgoal.app.ui.theme.PLUM) }
            }
        }
    }
}
