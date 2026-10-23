package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*

/* ------------------------------------------------------------------ Fond d'une conversation */

/**
 * Choix du fond de la conversation : les fonds illustrés débloqués par n'importe quel membre (partagés dans la
 * conversation), visibles par tous. [onPick] reçoit la clé du fond, ou null pour revenir aux couleurs.
 */
@Composable
fun ChatBgPicker(ui: AppState, current: String?, choices: List<Pair<ShopBg, List<String>>>, canWrite: Boolean, onPick: (String?) -> Unit) {
    val p = LocalPal.current; val pg = ui.pg; val me = pg.meId()
    Text("Fond de la conversation", fontWeight = FontWeight.ExtraBold)
    Muted(if (choices.isEmpty()) "Débloque des fonds illustrés dans la boutique : ceux de chaque membre sont proposés ici." else "Visible par tous les membres. Les fonds débloqués par chacun d’entre vous sont proposés.", Modifier.padding(bottom = 8.dp), small = true)
    if (!canWrite) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        @Composable fun Tile(label: String, sub: String?, on: Boolean, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
            Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(width = 64.dp, height = 104.dp).clip(RoundedCornerShape(14.dp)).border(if (on) 3.dp else 1.dp, if (on) p.flameInk else p.line, RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = label, onClick = onClick).semantics { contentDescription = label; selected = on }, content = content)
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
                if (sub != null) Text(sub, fontSize = 9.sp, color = p.ink3, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
        Tile("Couleurs", null, current == null, { if (current != null) onPick(null) }) { Box(Modifier.fillMaxSize().background(p.ink.copy(alpha = .06f)), contentAlignment = Alignment.Center) { Pi("palette", size = 22.dp, tint = p.ink2) } }
        choices.forEach { (b, who) ->
            val by = if (me in who) null else "par ${who.mapNotNull { pg.userById(it)?.name }.map { first(it) }.firstOrNull() ?: "un membre"}"
            Tile(b.name, by, current == b.key, { if (current != b.key) onPick(b.key) }) { SceneBg(b.key, Modifier.fillMaxSize(), still = true) }
        }
    }
}
