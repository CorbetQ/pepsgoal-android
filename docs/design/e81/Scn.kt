package e81

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.pepsgoal.app.ui.scene
import fr.pepsgoal.app.ui.theme.LocalPal

@Composable
fun ScenesTest() {
    val dark = LocalPal.current.dark
    Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(listOf("loire", "mountains", "angers"), listOf("rooftops", "balloons", "vineyard"), listOf("flowerfield", "lagoon", "lighthouse")).forEach { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { k -> Canvas(Modifier.weight(1f).fillMaxHeight()) { scene(k, .3f, if (dark) 21.5f else 15f) } }
            }
        }
    }
}
