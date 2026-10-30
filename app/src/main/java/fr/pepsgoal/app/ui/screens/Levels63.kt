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
import androidx.compose.ui.graphics.Color

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
            Text("Niveau ${lv.idx} · ${lv.name}", fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Muted("${fmtNum(lv.pts)} flammes gagnées" + (lv.next?.let { " · ${fmtNum(it - lv.pts)} avant « ${lv.nextName} »" } ?: ""), small = true)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LEVEL_TIERS.forEachIndexed { i, name ->
            val t = i + 1; val first = i * LEVELS_PER_TIER + 1; val at = levelThreshold(first)
            val reached = t <= lv.tier; val cur = t == lv.tier; val cols = tierColors(t)
            // Palier en cours = carte de la flamme (encre prune) ; atteints = lignes à marche ; à venir = cadenas
            Row(Modifier.fillMaxWidth().then(if (cur) Modifier.chunk(p.flameBrush(), p.flameStep, 22.dp, 4.dp, 0.dp) else Modifier.chunk(p.surface, p.edge, 22.dp, 3.dp, 2.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Flame(30.dp, dim = !reached, colors = cols)
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.ExtraBold, color = if (cur) fr.pepsgoal.app.ui.theme.PLUM else if (reached) p.ink else p.ink2)
                    Text("Niveaux $first à ${first + LEVELS_PER_TIER - 1} · dès ${fmtNum(at)} flammes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (cur) fr.pepsgoal.app.ui.theme.PLUM else p.ink2)
                    if (cur) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(10.dp).background(Color.White.copy(alpha = .42f), androidx.compose.foundation.shape.CircleShape)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(lv.tierPct.toFloat().coerceIn(0f, 1f)).background(fr.pepsgoal.app.ui.theme.PLUM, androidx.compose.foundation.shape.CircleShape))
                    }
                }
                when {
                    cur -> Chip("En cours", "flameo", Color.White)
                    reached -> Chip("Atteint", "check", p.ok)
                    else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Pi("lock", size = 14.dp, tint = p.ink3); Text(levelEta(at - lv.pts), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink3) }
                }
            }
        }
    }
    Muted("Récompenses : +10 flammes pour la boutique à chaque niveau, +60 en entrant dans un nouveau palier. Au-delà du niveau 50, chaque éclat de Supernova (✦) demande 6 000 flammes. Les durées sont estimées pour quelques validations par jour.", small = true)
}
