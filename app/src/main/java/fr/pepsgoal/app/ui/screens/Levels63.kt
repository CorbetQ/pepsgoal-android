package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.sheets.SheetHead
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*

/** Les dix paliers de niveau : seuils, palier en cours, temps estimé pour les suivants et récompenses. */
@Composable
fun LevelsSheet(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val lv = remember(ui.rev) { pg.level(pg.points(u.id)) }
    SheetHead(ui, "Niveaux")
    Muted("Chaque validation rapporte des flammes qui te font monter de niveau : 50 niveaux en 10 paliers. Dépenser ou offrir des flammes ne fait jamais baisser ton niveau.", small = true)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Flame(44.dp, colors = tierColors(lv.tier))
        Column(Modifier.weight(1f)) {
            Text("Niveau ${lv.idx} · ${lv.name}", fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Muted("${fmtNum(lv.pts)} flammes gagnées" + (lv.next?.let { " · ${fmtNum(it - lv.pts)} avant « ${lv.nextName} »" } ?: ""), small = true)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LEVEL_TIERS.forEachIndexed { i, name ->
            val t = i + 1; val first = i * LEVELS_PER_TIER + 1; val at = levelThreshold(first)
            val reached = t <= lv.tier; val cur = t == lv.tier; val cols = tierColors(t)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (cur) Brush.linearGradient(cols.map { it.copy(alpha = .2f) }) else Brush.linearGradient(listOf(p.ink.copy(alpha = .04f), p.ink.copy(alpha = .04f))))
                .then(if (cur) Modifier.border(1.5.dp, cols.last().copy(alpha = .7f), RoundedCornerShape(18.dp)) else Modifier).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Flame(30.dp, dim = !reached, colors = cols)
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.ExtraBold, color = if (reached) p.ink else p.ink2)
                    Muted("Niveaux $first à ${first + LEVELS_PER_TIER - 1} · dès ${fmtNum(at)} flammes", small = true)
                    if (cur) Bar(lv.tierPct * 100, Modifier.padding(top = 5.dp), cols.last(), 5.dp)
                }
                when {
                    cur -> Chip("En cours", "flameo", cols.last())
                    reached -> Chip("Atteint", "check", p.ok)
                    else -> Text(levelEta(at - lv.pts), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink3)
                }
            }
        }
    }
    Muted("Récompenses : +10 flammes pour la boutique à chaque niveau, +60 en entrant dans un nouveau palier. Au-delà du niveau 50, chaque éclat de Supernova (✦) demande 6 000 flammes. Les durées sont estimées pour quelques validations par jour.", small = true)
}
