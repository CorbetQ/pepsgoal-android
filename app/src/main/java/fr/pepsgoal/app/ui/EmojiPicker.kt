package fr.pepsgoal.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.EMOJI_CATS
import fr.pepsgoal.core.emojiCatOf

/** Sélecteur d'emojis à onglets de catégories (emojis Fluent). */
@Composable
fun EmojiPicker(value: String? = null, height: Int = 230, onPick: (String) -> Unit) {
    val p = LocalPal.current
    var cat by remember { mutableStateOf(value?.let(::emojiCatOf) ?: EMOJI_CATS[0].key) }
    val c = EMOJI_CATS.first { it.key == cat }
    Column(Modifier.fillMaxWidth().background(p.surface2, RoundedCornerShape(22.dp)).padding(8.dp)) {
        // Catégories : tuiles à marche, la catégorie ouverte en braise
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EMOJI_CATS.forEach { x -> PickTile(x.key == cat, x.label, { cat = x.key }, 44.dp) { Emoji(x.icon, 24.dp) } }
        }
        Text(c.label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2, modifier = Modifier.padding(6.dp))
        LazyVerticalGrid(GridCells.Adaptive(42.dp), Modifier.fillMaxWidth().height(height.dp)) {
            items(c.list, key = { it }) { e ->
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(17.dp)).background(if (e == value) p.selTint(p.flame) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onPick(e) }.semantics { contentDescription = e }, contentAlignment = Alignment.Center) { Emoji(e, 28.dp) }
            }
        }
    }
}
