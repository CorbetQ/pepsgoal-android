package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.sys.AppIcons
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.*

/** Static previews reuse the real Braise Pop scene, icon, palette and ambiance components. */
@Composable
fun ThemePackCard(ui: AppState, pack: ThemePack, currentIcon: String, onApplied: () -> Unit) {
    val pg = ui.pg; val p = LocalPal.current
    val quote = pg.packQuote(pack)
    val active = ui.themePackActive(pack.key, currentIcon)
    val icon = AppIcons.ALL.firstOrNull { it.key == pack.iconKey } ?: return

    fun apply() {
        val error = ui.applyThemePack(pack.key)
        if (error == null) onApplied()
        ui.toast(error ?: "Pack « ${pack.name} » appliqué.", if (error == null) "palette" else "x")
    }
    fun purchase() {
        val current = pg.packQuote(pack)
        if (current.owned) { apply(); return }
        if (!current.available) { ui.toast("Un élément du pack est une édition limitée : il revient à sa saison.", "clock"); return }
        ui.confirm("Acheter le pack « ${pack.name} » ?",
            "${current.price} flammes pour les éléments manquants, avec 25 % de remise (arrondi à la flamme supérieure). Les éléments déjà possédés ne sont pas refacturés. Ton niveau ne baisse pas. Le pack applique son icône, son fond, ses couleurs et son ambiance à la place de Material You et des décors saisonniers automatiques.",
            "Acheter et appliquer · ${current.price} 🔥", danger = false) {
            val error = pg.buyPack(pack.key, maximumPrice = current.price)
            if (error != null) ui.toast(error, "x") else apply()
        }
    }

    Glass(pad = 0.dp, border = if (active) p.focusEdge else null) {
        Box(Modifier.fillMaxWidth().padding(6.dp).height(156.dp).clip(RoundedCornerShape(18.dp))) {
            SceneBg(pack.bgKey, Modifier.fillMaxSize(), still = true)
            Box(Modifier.align(Alignment.TopStart).padding(10.dp)) { Plaque { AppIconPreview(icon, 48.dp) } }
            Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(78.dp, 96.dp).clip(RoundedCornerShape(14.dp))) {
                ThemePreview(themeLook(pack.themeSetting), Modifier.fillMaxSize(), accent = pack.colors.map(::hex))
            }
            MiniPlaque(if (pack.isDefault) "Gratuit · style original" else if (pack.eggKey != null) "Secret découvert" else "Pack assorti", Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pack.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Muted("Icône : ${icon.label} · Fond : ${pack.background.name}", small = true)
            Muted("Couleurs : ${pack.colourTheme?.name ?: if (pack.isDefault) "Braise" else "assorties"} · Ambiance : ${pack.mode.name}", small = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pack.colors.forEach { color -> Box(Modifier.size(22.dp).clip(CircleShape).background(hex(color))) }
            }
            if (!quote.owned) {
                if (quote.saving > 0) {
                    Text("${fmtNum(quote.remainingPrice)} 🔥 séparément",
                        textDecoration = TextDecoration.LineThrough, color = p.ink2, fontSize = 13.sp)
                    Muted("−25 % · ${fmtNum(quote.saving)} flammes économisées sur les éléments manquants.", small = true)
                }
                val included = pack.components().size - quote.missingKeys.size
                if (included > 0) Muted("$included élément(s) déjà disponible(s) : déduits du prix.", small = true)
                if (!quote.available) Muted("Un élément manquant est hors saison. Les achats précédents restent disponibles.", small = true)
            } else if (!active) {
                Muted("Tous les éléments sont à toi. L’application du pack remplace les couleurs Material You et les décors saisonniers automatiques.", small = true)
            }
            PButton(
                when {
                    active -> "Pack appliqué"
                    quote.owned -> "Appliquer le pack"
                    quote.available -> "Acheter et appliquer · ${fmtNum(quote.price)} 🔥"
                    else -> "Hors saison"
                },
                { if (quote.owned) apply() else purchase() },
                Modifier.fillMaxWidth(),
                icon = if (active) "check" else if (quote.owned) "palette" else "star",
                kind = if (active) "ghost" else "primary",
                enabled = !active && quote.available && (quote.owned || pg.wallet() >= quote.price),
            )
            if (!quote.owned && quote.available && pg.wallet() < quote.price)
                Muted("Encore ${fmtNum(quote.price - pg.wallet())} flammes pour ce pack.", small = true)
        }
    }
}
