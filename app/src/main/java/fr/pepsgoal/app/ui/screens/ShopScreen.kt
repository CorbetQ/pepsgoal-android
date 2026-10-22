package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.sys.AppIcons
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.*

/** Aperçu d'une icône de l'application (fond en dégradé et premier plan, comme sur l'écran d'accueil). */
@Composable
fun AppIconPreview(ic: AppIcons.Icon, size: Dp = 54.dp, selected: Boolean = false) {
    val p = LocalPal.current
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .3f)).background(Brush.linearGradient(ic.bg.map { Color(it) }))
        .border(if (selected) 3.dp else 1.dp, if (selected) p.flame else p.line, RoundedCornerShape(size * .3f)), contentAlignment = Alignment.Center) {
        // Le premier plan d'une icône adaptative fait 108 dp pour 72 dp visibles : on l'agrandit d'autant
        Image(painterResource(ic.fg), null, Modifier.requiredSize(size * 1.6f), colorFilter = if (ic.key == "mono") ColorFilter.tint(Color.White) else null)
    }
}

/** Boutique : icônes de l'application et thèmes, achetés avec les flammes gagnées en validant ses objectifs. */
@Composable
fun ShopScreen(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val ctx = LocalContext.current; val u = pg.me() ?: return
    var tab by remember { mutableStateOf("icons") }
    var curIcon by remember { mutableStateOf(AppIcons.current(ctx)) }
    val wallet = pg.wallet(u)
    fun purchase(key: String, label: String, price: Int, then: () -> Unit) {
        if (pg.owns(key, price, u)) { then(); return }
        ui.confirm("Acheter « $label » ?", "$price flammes seront retirées de ton porte-monnaie ($wallet disponibles). Ton niveau ne baisse pas.", "Acheter $price 🔥", danger = false) {
            val err = pg.buy(key, price)
            if (err != null) ui.toast(err, "x") else { if (!p.motion) ui.sparks(price / 10); ui.toast("« $label » est à toi !", "star"); then() }
        }
    }
    ScreenFrame(ui, "Boutique", "Retour", { ui.back() }, icon = "star", color = p.flame, color2 = p.pink) {
        item("wallet") {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Flame(46.dp)
                    Column(Modifier.weight(1f)) {
                        Text("$wallet", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                        Muted("flammes à dépenser · ${pg.points(u.id)} gagnées au total", small = true)
                    }
                }
                Muted("Chaque validation rapporte des flammes. Les dépenser ne fait jamais baisser ton niveau ni ta série.", Modifier.padding(top = 8.dp), small = true)
            }
        }
        item("tabs") {
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(p.ink.copy(alpha = .06f)).padding(4.dp)) {
                listOf("icons" to "Icônes", "themes" to "Thèmes").forEach { (k, l) ->
                    Box(Modifier.weight(1f).clip(CircleShape).background(if (tab == k) p.glassStrong else Color.Transparent).clickable { tab = k }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(l, fontWeight = if (tab == k) FontWeight.ExtraBold else FontWeight.Bold, color = if (tab == k) p.ink else p.ink2)
                    }
                }
            }
        }
        if (tab == "icons") AppIcons.ALL.chunked(2).forEachIndexed { row, pair ->
            item("ic$row") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { ic ->
                        val owned = pg.owns("icon:${ic.key}", ic.price, u); val on = curIcon == ic.key
                        Glass(Modifier.weight(1f), pad = 14.dp, onClick = { purchase("icon:${ic.key}", ic.label, ic.price) { AppIcons.set(ctx, ic.key); curIcon = ic.key; ui.toast("Icône « ${ic.label} » activée.", "palette") } }) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AppIconPreview(ic, 76.dp, on) }
                            Text(ic.label, Modifier.fillMaxWidth().padding(top = 10.dp), fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
                            PriceTag(owned, on, ic.price, wallet)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        else SHOP_THEMES.forEach { t ->
            item("th:${t.key}") {
                val owned = pg.owns("theme:${t.key}", t.price, u)
                val on = u.settings.accentCustom == t.colors && u.settings.bg == t.bg
                Glass(pad = 0.dp, onClick = { purchase("theme:${t.key}", t.name, t.price) { pg.applyTheme(t); ui.themeTick++; ui.toast("Thème « ${t.name} » appliqué.", "palette") } }) {
                    // Aperçu : dégradé des trois couleurs et motif de l'arrière-plan
                    Box(Modifier.fillMaxWidth().height(96.dp).background(Brush.linearGradient(t.colors.map(::hex)))) {
                        Row(Modifier.align(Alignment.BottomStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            t.colors.forEach { c -> Box(Modifier.size(22.dp).clip(CircleShape).background(hex(c)).border(2.dp, Color.White.copy(alpha = .8f), CircleShape)) }
                        }
                        Text(BGS.firstOrNull { it.first == t.bg }?.second ?: "", Modifier.align(Alignment.TopEnd).padding(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = .25f)).padding(horizontal = 10.dp, vertical = 4.dp),
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(t.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp); Muted(t.desc, small = true) }
                        Box(Modifier.width(110.dp)) { PriceTag(owned, on, t.price, wallet) }
                    }
                }
            }
        }
        item("note") { Muted("Les icônes et thèmes achetés restent à toi sur tous tes appareils (avec un compte). Les couleurs de saison restent gratuites.", Modifier.padding(horizontal = 4.dp), small = true) }
    }
}

@Composable
private fun PriceTag(owned: Boolean, on: Boolean, price: Int, wallet: Int) {
    val p = LocalPal.current
    val (label, bg, ink) = when {
        on -> Triple("✓ Actif", p.ok.copy(alpha = .2f), p.ink)
        owned -> Triple(if (price == 0) "Gratuit" else "Possédé", p.ink.copy(alpha = .07f), p.ink2)
        wallet >= price -> Triple("🔥 $price", p.flame.copy(alpha = .22f), p.ink)
        else -> Triple("🔒 $price", p.ink.copy(alpha = .06f), p.ink3)
    }
    Box(Modifier.fillMaxWidth().padding(top = 8.dp).clip(CircleShape).background(bg).padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
        Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = ink)
    }
}
