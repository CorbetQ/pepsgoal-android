package fr.pepsgoal.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.EMOJI_CATS
import fr.pepsgoal.core.emojiCatOf
import java.text.Normalizer

/** Mots-clés français des emojis les plus cherchés (le nom Unicode anglais sert en plus pour tous les autres). */
private val EMOJI_WORDS = mapOf(
    "courir course running footing jogging" to "🏃👟", "velo vélo cyclisme" to "🚴🚵", "nager natation piscine" to "🏊🤽", "yoga meditation méditer zen" to "🧘",
    "muscu musculation force sport salle" to "💪🏋️", "marche marcher randonnee randonnée" to "🚶🥾", "foot football ballon" to "⚽", "tennis" to "🎾",
    "eau boire hydratation" to "💧🥛", "cafe café" to "☕", "the thé" to "🍵", "fruit pomme" to "🍎🍏", "legume légume salade" to "🥗🥦🥕",
    "sucre bonbon gateau gâteau chocolat" to "🍬🍰🍫🍩", "alcool vin biere bière" to "🍷🍺", "manger repas cuisine" to "🍳🍲🍝",
    "dormir sommeil nuit lit" to "😴🛏️🌙", "lire lecture livre" to "📚📖", "ecrire écrire journal" to "✍️📝📓", "etudier étudier ecole école cours" to "🎓📚✏️",
    "travail bureau ordinateur" to "💻💼", "argent economie économie epargne épargne" to "💰💶🐷", "maison menage ménage ranger" to "🏠🧹🧺",
    "musique chanter guitare piano" to "🎵🎸🎹🎤", "dessin dessiner peinture art" to "🎨✏️🖌️", "photo" to "📷", "jeu jeux" to "🎮🎲",
    "plante jardin nature" to "🌱🌿🪴", "chien" to "🐶", "chat" to "🐱", "soleil" to "☀️", "pluie" to "🌧️", "voyage avion" to "✈️🌍🧳",
    "coeur cœur amour" to "❤️💖", "feu flamme" to "🔥", "etoile étoile" to "⭐🌟", "fete fête anniversaire" to "🎉🎂🎈", "trophee trophée victoire" to "🏆🥇",
    "rire sourire content heureux" to "😀😄😊", "triste" to "😢🙁", "colere colère" to "😠", "fatigue fatigué" to "🥱😴", "cerveau" to "🧠",
    "telephone téléphone ecran écran" to "📱", "dents brosse" to "🪥", "medicament médicament sante santé" to "💊🩺", "cigarette fumer tabac" to "🚭",
)

private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** Index de recherche : emoji → mots (français choisis, nom Unicode anglais, catégorie). */
private val EMOJI_INDEX: List<Pair<String, String>> by lazy {
    val fr = HashMap<String, StringBuilder>()
    EMOJI_WORDS.forEach { (words, es) -> var i = 0; while (i < es.length) { val n = es.offsetByCodePoints(i, 1).let { j -> if (j < es.length && es[j] == '️') j + 1 else j }; fr.getOrPut(es.substring(i, n)) { StringBuilder() }.append(' ').append(words); i = n } }
    EMOJI_CATS.flatMap { c -> c.list.map { e ->
        val name = try { Character.getName(e.codePointAt(0)) ?: "" } catch (x: Exception) { "" }
        e to norm("${fr[e] ?: ""} ${fr[e.removeSuffix("️")] ?: ""} $name ${c.label}")
    } }.distinctBy { it.first }
}

/** Sélecteur d'emojis : recherche, onglets de catégories (PTabs défilants), cases à marche neutres (emojis Fluent). */
@Composable
fun EmojiPicker(value: String? = null, height: Int = 230, onPick: (String) -> Unit) {
    val p = LocalPal.current
    var cat by remember { mutableStateOf(value?.let(::emojiCatOf) ?: EMOJI_CATS[0].key) }
    var q by remember { mutableStateOf("") }
    val idx = EMOJI_CATS.indexOfFirst { it.key == cat }.coerceAtLeast(0)
    val nq = norm(q.trim())
    val list = if (nq.isEmpty()) EMOJI_CATS[idx].list else EMOJI_INDEX.filter { (_, w) -> nq.split(' ').all { it in w } }.map { it.first }
    Column(Modifier.fillMaxWidth().background(p.surface2, RoundedCornerShape(22.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PSearchField(q, { q = it }, "Rechercher un emoji", max = 30)
        if (nq.isEmpty()) PTabs(EMOJI_CATS.map { it.label to it.icon }, idx, { cat = EMOJI_CATS[it].key })
        if (list.isEmpty()) Text("Aucun emoji trouvé", Modifier.padding(6.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
        LazyVerticalGrid(GridCells.Adaptive(50.dp), Modifier.fillMaxWidth().height(height.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(list, key = { it }) { e -> Box(contentAlignment = Alignment.Center) { PickTile(e == value, e, { onPick(e) }, 44.dp) { Emoji(e, 26.dp) } } }
        }
    }
}
