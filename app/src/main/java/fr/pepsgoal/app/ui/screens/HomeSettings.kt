package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.data.DevicePrefs
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Cartes de l'accueil, dans l'ordre par défaut : clé → (nom, description, icône). */
val HOME_CARDS = linkedMapOf(
    "hero" to Triple("Bonjour et série", "Date, message du jour, flamme et semaine", "sun"),
    "aipromo" to Triple("Présentation du coach IA", "Tant que l’assistant n’est pas installé", "sparkle"),
    "day" to Triple("Ta journée", "Anneau du jour, niveau et flammes", "gauge"),
    "timeline" to Triple("Heure par heure", "Tes objectifs placés à l’heure où tu les fais d’habitude", "clock"),
    "evening" to Triple("Bilan express du soir", "À partir de 20 h 30 : humeur, une phrase, reporter ou passer", "moon"),
    "tools" to Triple("Raccourcis", "Focus, Stats, Journal, Coach", "bolt"),
    "tip" to Triple("Conseil du coach", "Avec l’assistant IA activé", "sparkle"),
    "mood" to Triple("Humeur du jour", "Note ton humeur en un geste", "smile"),
    "wx" to Triple("Météo", "Temps du jour et conseil", "cloud"),
    "board" to Triple("Classement des flammes", "Tes amis et toi (fonctions sociales)", "trophy"),
    "due" to Triple("À valider aujourd’hui", "Toujours affichée", "check"),
    "verify" to Triple("À confirmer pour tes amis", "Quand un binôme attend ta confirmation", "shield"),
    "ongoing" to Triple("En cours", "Deadlines, projets, objectifs chiffrés et défis", "list"),
    "recap" to Triple("Récaps", "Semaine, mois et année", "star")
)

/** Ordre et visibilité choisis (les cartes ajoutées par une mise à jour arrivent à leur place par défaut, affichées). */
fun homeOrder(prefs: DevicePrefs): List<Pair<String, Boolean>> {
    val saved = prefs.homeCards.map { if (it.startsWith("-")) it.drop(1) to false else it to true }.filter { it.first in HOME_CARDS }.distinctBy { it.first }
    val out = saved.toMutableList()
    HOME_CARDS.keys.forEachIndexed { i, k -> if (out.none { it.first == k }) out.add(minOf(i, out.size), k to true) }
    return out.map { if (it.first == "due") it.first to true else it }
}

/** Paramètres › Accueil : afficher, masquer et réordonner les cartes (glisser la poignée, ou actions TalkBack). */
@Composable
fun HomeSettings(ui: AppState) {
    val p = LocalPal.current
    val list = remember { mutableStateListOf<Pair<String, Boolean>>().apply { addAll(homeOrder(ui.prefs)) } }
    val save = { ui.prefs.homeCards = list.map { (k, on) -> if (on) k else "-$k" }; ui.bump() }
    val move = { k: String, by: Int ->
        val i = list.indexOfFirst { it.first == k }; val j = i + by
        if (i >= 0 && j in list.indices) { val x = list[i]; list[i] = list[j]; list[j] = x }
    }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var rowH by remember { mutableIntStateOf(1) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        CatHero("home", "${list.count { it.second }} cartes affichées sur ${list.size}, dans l’ordre de ton choix", chips = {
            Chip("Ordre personnalisé", "grip", p.type("routine").c)
            list.firstOrNull { it.second }?.let { (k, _) -> Chip("En haut : ${HOME_CARDS[k]?.first ?: k}", HOME_CARDS[k]?.third ?: "sun", p.type("objective").c) }
        })
        LayoutPicker(ui)
        Glass {
            CardHead("Cartes de l’accueil") { Count(list.count { it.second }) }
            Muted("Fais glisser la poignée pour changer l’ordre. L’interrupteur affiche ou masque la carte.", Modifier.padding(bottom = 10.dp), small = true)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                list.forEach { (k, on) -> key(k) {
                    val (l, d, ic) = HOME_CARDS[k]!!
                    val isDrag = dragging == k
                    val pos = list.indexOfFirst { it.first == k }
                    Row(Modifier.zIndex(if (isDrag) 1f else 0f).onSizeChanged { rowH = it.height }
                        .graphicsLayer { translationY = if (isDrag) dragY else 0f; scaleX = if (isDrag) 1.03f else 1f; scaleY = if (isDrag) 1.03f else 1f }
                        .then(if (isDrag) Modifier.shadow(12.dp, RoundedCornerShape(16.dp)) else Modifier)
                        .clip(RoundedCornerShape(16.dp)).background(if (isDrag) p.glassStrong else p.ink.copy(alpha = .04f))
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$l, position ${pos + 1} sur ${list.size}, ${if (on) "affichée" else "masquée"}"
                            customActions = listOfNotNull(
                                if (pos > 0) CustomAccessibilityAction("Monter") { move(k, -1); save(); true } else null,
                                if (pos < list.lastIndex) CustomAccessibilityAction("Descendre") { move(k, 1); save(); true } else null
                            )
                        }.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(48.dp).pointerInput(k) {
                            detectDragGestures(
                                onDragStart = { dragging = k; dragY = 0f; Feedback.tick() },
                                onDragEnd = { dragging = null; dragY = 0f; save() },
                                onDragCancel = { dragging = null; dragY = 0f; save() }
                            ) { ch, amt ->
                                ch.consume(); dragY += amt.y
                                val i = list.indexOfFirst { it.first == k }
                                if (dragY > rowH * .6f && i < list.lastIndex) { move(k, 1); dragY -= rowH; Feedback.tick() }
                                else if (dragY < -rowH * .6f && i > 0) { move(k, -1); dragY += rowH; Feedback.tick() }
                            }
                        }, contentAlignment = Alignment.Center) { Pi("grip", size = 22.dp, tint = p.ink2) }
                        Pi(ic, size = 20.dp, tint = if (on) p.ink else p.ink3)
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(l, fontWeight = FontWeight.ExtraBold, color = if (on) p.ink else p.ink3)
                            Muted(d, small = true)
                        }
                        PSwitch(on, { v -> val i = list.indexOfFirst { it.first == k }; if (i >= 0) { list[i] = k to v; save() } }, enabled = k != "due")
                    }
                } }
            }
            Spacer(Modifier.height(10.dp))
            PButton("Rétablir l’accueil par défaut", { list.clear(); list.addAll(HOME_CARDS.keys.map { it to true }); save(); ui.toast("Accueil rétabli.", "sun") }, icon = "repeat", small = true)
        }
    }
}

/** Paramètres › Accessibilité et confort : lecture facile, texte, densité, une main, gestes, vibrations et sons. */
@Composable
fun ComfortSettings(ui: AppState) {
    ui.themeTick
    val pr = ui.prefs; val upd = { ui.themeTick++ }; val p = LocalPal.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SetGroup("Lecture") {
            SetToggle("Mode Lecture facile", "Mots simples, état des objectifs écrit en toutes lettres, texte un peu plus grand, messages affichés plus longtemps, sans effets de verre ni animations.", "book", p.type("duration").c,
                pr.easy, { pr.easy = it; upd(); ui.toast(if (it) "Lecture facile activée." else "Lecture facile désactivée.", "eye") })
            Column {
                SetItem("Taille du texte", "${(pr.textScale * 100).roundToInt()} % de la taille choisie dans Android", "text", p.type("objective").c)
                FlowRowCompat(Modifier.padding(start = 60.dp, end = 12.dp, bottom = 12.dp), gap = 6.dp) {
                    listOf(.85f to "Petit", 1f to "Normal", 1.15f to "Grand", 1.3f to "Très grand", 1.5f to "Maximum").forEach { (v, l) -> FChip(l, abs(pr.textScale - v) < .01f, { pr.textScale = v; upd() }) }
                }
            }
            Column {
                SetItem("Densité de l’affichage", "Compact : plus de contenu à l’écran. Large : tout est plus grand et plus facile à toucher.", "grip", p.type("project").c)
                Box(Modifier.padding(start = 60.dp, end = 12.dp, bottom = 12.dp)) { Seg(listOf("compact" to "Compact", "normal" to "Normal", "large" to "Large"), pr.density, { pr.density = it; upd() }) }
            }
        }
        SetGroup("Gestes et prise en main") {
            SetToggle("Mode une main", "Le bouton retour et les actions des pages passent en bas. En haut d’une liste, tire l’écran vers le bas pour amener le haut sous ton pouce.", "hand", p.type("routine").c, pr.oneHand, { pr.oneHand = it; upd() })
            SetToggle("Gestes de balayage", "Balaie un objectif vers la droite pour le valider, vers la gauche pour les actions rapides ; balaie un message vers la droite pour y répondre.", "repeat", p.type("challenge").c, pr.swipe, { pr.swipe = it; upd() })
        }
        SetGroup("Vibrations et sons", note = "Pendant un geste, les vibrations se renforcent à mesure que tu approches du seuil, puis un clic net confirme l’action.") {
            Column {
                SetItem("Vibrations", "Un léger retour à chaque appui, plus marqué quand tu valides.", "bolt", p.flame) {
                    PButton("Essayer", {
                        // Démonstration : crans progressifs, seuil, puis action déclenchée
                        scope.launch { for (k in listOf(.25f, .5f, .75f)) { Feedback.ramp(k); kotlinx.coroutines.delay(110) }; Feedback.armed(); kotlinx.coroutines.delay(320); Feedback.commit() }
                    }, small = true, enabled = pr.haptics != "off")
                }
                Box(Modifier.padding(start = 60.dp, end = 12.dp, bottom = 12.dp)) { Seg(listOf("off" to "Aucune", "light" to "Légères", "strong" to "Fortes"), pr.haptics, { pr.haptics = it; upd(); Feedback.success() }) }
            }
            SetToggle("Sons", "Petit carillon à la validation, note grave quand tu annules.", "music", p.type("period").c, pr.sounds, { pr.sounds = it; upd(); if (it) Feedback.success() })
        }
    }
}

/** Disposition de l'accueil (compact, normal, aéré) avec un aperçu schématique qui change en direct. */
@Composable
private fun LayoutPicker(ui: AppState) {
    val p = LocalPal.current
    var lay by remember { mutableStateOf(ui.prefs.homeLayout) }
    val gap by androidx.compose.animation.core.animateFloatAsState(when (lay) { "compact" -> 5f; "airy" -> 13f; else -> 9f }, label = "gap")
    val pad by androidx.compose.animation.core.animateFloatAsState(when (lay) { "compact" -> 6f; "airy" -> 12f; else -> 9f }, label = "pad")
    val hero by androidx.compose.animation.core.animateFloatAsState(when (lay) { "compact" -> 34f; "airy" -> 70f; else -> 58f }, label = "hero")
    Glass {
        CardHead("Disposition") { Chip(when (lay) { "compact" -> "Compact"; "airy" -> "Aéré"; else -> "Normal" }, "gauge", p.type("objective").c) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Aperçu : un téléphone et ses cartes
            androidx.compose.foundation.Canvas(Modifier.width(96.dp).height(170.dp)) {
                val r = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
                drawRoundRect(p.ink.copy(alpha = .08f), cornerRadius = r); drawRoundRect(p.ink.copy(alpha = .25f), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                var y = 10.dp.toPx(); val x = 7.dp.toPx(); val w = size.width - 2 * x
                val cards = listOf(hero, 40f, 30f, 46f, 30f, 40f)
                for ((i, h) in cards.withIndex()) {
                    val hh = h.dp.toPx() * (if (i == 0) 1f else (.7f + pad / 30f)); if (y + hh > size.height - 6.dp.toPx()) break
                    drawRoundRect(if (i == 0) p.flame.copy(alpha = .35f) else p.glassStrong, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(w, hh), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
                    drawRoundRect(p.ink.copy(alpha = .3f), androidx.compose.ui.geometry.Offset(x + pad.dp.toPx() * .6f, y + pad.dp.toPx() * .6f), androidx.compose.ui.geometry.Size(w * .5f, 3.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                    y += hh + gap.dp.toPx()
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("compact" to ("Compact" to "Plus de cartes à l’écran, en-tête réduit."), "comfy" to ("Normal" to "L’équilibre par défaut."), "airy" to ("Aéré" to "Plus d’espace, pour respirer.")).forEach { (k, v) ->
                    val on = lay == k
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) p.flame.copy(alpha = .14f) else p.ink.copy(alpha = .04f))
                        .clickable { lay = k; ui.prefs.homeLayout = k; Feedback.tick(); ui.bump() }.padding(10.dp)) {
                        Text(v.first, fontWeight = FontWeight.ExtraBold, color = if (on) p.ink else p.ink2); Muted(v.second, small = true)
                    }
                }
            }
        }
    }
}
