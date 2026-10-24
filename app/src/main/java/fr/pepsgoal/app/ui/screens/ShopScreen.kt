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
import kotlinx.coroutines.launch

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

/** Icône payante déjà utilisée avant l'ouverture de la boutique : elle est offerte (pas de rachat). */
fun keepCurrentIcon(ui: AppState, ctx: android.content.Context) {
    val u = ui.pg.me() ?: return; val cur = AppIcons.current(ctx)
    val ic = AppIcons.ALL.firstOrNull { it.key == cur } ?: return
    if (ic.price > 0 && "icon:${ic.key}" !in u.shopOwned) { u.shopOwned = (u.shopOwned + "icon:${ic.key}").toMutableList(); ui.pg.store.commit() }
}

/** Boutique : icônes de l'application et thèmes, achetés avec les flammes gagnées en validant ses objectifs. */
@Composable
fun ShopScreen(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val ctx = LocalContext.current; val u = pg.me() ?: return
    var tab by remember { mutableStateOf(ui.shopTab) }
    // L'essai d'une ambiance s'arrête en quittant la boutique
    DisposableEffect(Unit) { onDispose { ui.themePreview = null } }
    val scope = rememberCoroutineScope()
    var curIcon by remember { mutableStateOf(AppIcons.current(ctx)) }
    LaunchedEffect(Unit) { keepCurrentIcon(ui, ctx) }
    val wallet = pg.wallet(u)
    fun purchase(key: String, label: String, price: Int, season: String? = null, then: () -> Unit) {
        if (pg.owns(key, price, u)) { then(); return }
        if (!pg.buyable(key, price, season)) { ui.toast("Édition limitée : « $label » revient ${season?.let { seasonNext(it) }?.let { "le " + fmtDate(ymd(it)) } ?: "à sa saison"}.", "clock"); return }
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
                Muted("Chaque validation rapporte des flammes. Les dépenser ne fait jamais baisser ton niveau ni ta série." + if (pg.socialOn()) " Touche 🎁 sur un article pour l’offrir à un ami." else "", Modifier.padding(top = 8.dp), small = true)
            }
        }
        item("quests") { FlameQuestsCard(ui) }
        item("tabs") {
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(p.ink.copy(alpha = .06f)).padding(4.dp)) {
                listOf("icons" to "Icônes", "themes" to "Thèmes", "modes" to "Ambiances", "bgs" to "Fonds").forEach { (k, l) ->
                    Box(Modifier.weight(1f).clip(CircleShape).background(if (tab == k) p.glassStrong else Color.Transparent).clickable { tab = k; ui.shopTab = k }.padding(vertical = 10.dp, horizontal = 2.dp), contentAlignment = Alignment.Center) {
                        FitText(tr(l), size = 14, min = 10, color = if (tab == k) p.ink else p.ink2, weight = if (tab == k) FontWeight.ExtraBold else FontWeight.Bold)
                    }
                }
            }
        }
        if (tab == "icons") AppIcons.ALL.chunked(2).forEachIndexed { row, pair ->
            item("ic$row") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { ic ->
                        val owned = pg.owns("icon:${ic.key}", ic.price, u); val on = curIcon == ic.key
                        Glass(Modifier.weight(1f), pad = 14.dp, onClick = { purchase("icon:${ic.key}", ic.label, ic.price, ic.season) { AppIcons.set(ctx, ic.key); curIcon = ic.key; ui.toast("Icône « ${ic.label} » activée.", "palette") } }) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                AppIconPreview(ic, 76.dp, on)
                                GiftBtn(ui, "icon:${ic.key}", ic.label, ic.price, Modifier.align(Alignment.TopEnd))
                            }
                            Text(ic.label, Modifier.fillMaxWidth().padding(top = 10.dp), fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
                            ic.season?.let { SeasonTag(it, owned) }
                            PriceTag(owned, on, ic.price, wallet, pg.buyable("icon:${ic.key}", ic.price, ic.season))
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        else if (tab == "themes") SHOP_THEMES.sortedBy { t -> val ss = t.season; when { ss == null -> 1; seasonWindow(ss) != null -> 0; else -> 2 } }.forEach { t ->
            item("th:${t.key}") {
                val owned = pg.owns("theme:${t.key}", t.price, u)
                val on = u.settings.accentCustom == t.colors && u.settings.bg == t.bg
                Glass(pad = 0.dp, onClick = { purchase("theme:${t.key}", t.name, t.price, t.season) { pg.applyTheme(t); ui.themeTick++; ui.toast("Thème « ${t.name} » appliqué.", "palette") } }) {
                    // Aperçu : dégradé des trois couleurs et motif de l'arrière-plan
                    // Thème spécial avec paysage illustré : l'aperçu montre la scène elle-même
                    val scene = t.bg.startsWith("s:")
                    Box(Modifier.fillMaxWidth().height(if (scene) 150.dp else 96.dp).background(Brush.linearGradient(t.colors.map(::hex)))) {
                        if (scene) SceneBg(t.bg.removePrefix("s:"), Modifier.fillMaxSize(), still = true)
                        GiftBtn(ui, "theme:${t.key}", t.name, t.price, Modifier.align(Alignment.BottomEnd).padding(8.dp), onImage = true)
                        Row(Modifier.align(Alignment.BottomStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            t.colors.forEach { c -> Box(Modifier.size(22.dp).clip(CircleShape).background(hex(c)).border(2.dp, Color.White.copy(alpha = .8f), CircleShape)) }
                        }
                        Text((BGS.firstOrNull { it.first == t.bg }?.second ?: SHOP_BGS.firstOrNull { "s:${it.key}" == t.bg }?.let { "Paysage spécial" } ?: "") + (FX_LABELS[t.fx]?.let { " · $it" } ?: ""), Modifier.align(Alignment.TopEnd).padding(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = .25f)).padding(horizontal = 10.dp, vertical = 4.dp),
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp); Muted(t.desc, small = true)
                            t.season?.let { SeasonTag(it, owned) }
                        }
                        Box(Modifier.width(110.dp)) { PriceTag(owned, on, t.price, wallet, pg.buyable("theme:${t.key}", t.price, t.season)) }
                    }
                }
            }
        }
        // Ambiances : thèmes d'interface complets (fond, texte, verre), à essayer avant d'acheter
        if (tab == "modes") {
            item("modesHead") {
                Glass(pad = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconTile("palette", p.flame, p.pink, 36.dp, 12.dp)
                        Column(Modifier.weight(1f)) {
                            Text("Ambiances", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Muted("Des thèmes d’interface complets, au même titre que Clair, Sombre ou OLED : fond, texte et verre des cartes. Ta couleur d’accent est conservée.", small = true)
                        }
                    }
                }
            }
            val sorted = SHOP_MODES.sortedBy { if (it.dark) 0 else 1 }
            sorted.chunked(2).forEachIndexed { row, pair ->
                item("mode$row") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { m -> ModeCard(ui, m, wallet, Modifier.weight(1f), onBuy = {
                            purchase("mode:${m.key}", m.name, m.price) { ui.themePreview = null; u.settings.theme = "m:${m.key}"; pg.store.commit(); ui.themeTick++; ui.toast("Ambiance « ${m.name} » appliquée.", "palette") }
                        }, onTry = {
                            ui.themePreview = "m:${m.key}"; ui.toast("Essai de « ${m.name} » pendant 10 secondes.", "eye")
                            scope.launch { kotlinx.coroutines.delay(10_000); if (ui.themePreview == "m:${m.key}") ui.themePreview = null }
                        }) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (shopMode(u.settings.theme) != null) item("modeoff") {
                Glass { SetRow("Revenir à un thème de base", "Clair, Sombre, OLED, Météo ou Auto, dans Paramètres › Apparence.") { PButton("Sombre", { u.settings.theme = "dark"; pg.store.commit(); ui.themeTick++ }, small = true, kind = "ghost") } }
            }
        }
        // Arrière-plans illustrés : aperçu animé, deux par ligne ; saison en cours d'abord
        if (tab == "bgs") SHOP_BGS.sortedBy { b -> val ss = b.season; when { ss == null -> 1; seasonWindow(ss) != null -> 0; else -> 2 } }.chunked(2).forEachIndexed { row, pair ->
            item("bg$row") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { b ->
                        val owned = pg.owns("bg:${b.key}", b.price, u); val on = u.settings.bg == "s:${b.key}"
                        Glass(Modifier.weight(1f), pad = 0.dp, border = if (on) p.flame else null, onClick = { purchase("bg:${b.key}", b.name, b.price, b.season) { pg.applyBg(b); ui.themeTick++; ui.toast("Fond « ${b.name} » appliqué.", "image") } }) {
                            Box(Modifier.fillMaxWidth().aspectRatio(.62f).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Brush.verticalGradient(b.sky.map(::hex)))) {
                                SceneBg(b.key, Modifier.fillMaxSize(), still = true)
                                GiftBtn(ui, "bg:${b.key}", b.name, b.price, Modifier.align(Alignment.TopEnd).padding(6.dp), onImage = true)
                            }
                            Column(Modifier.padding(12.dp)) {
                                Text(b.name, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                                Muted(b.desc, small = true, maxLines = 2)
                                b.season?.let { SeasonTag(it, owned) }
                                PriceTag(owned, on, b.price, wallet, pg.buyable("bg:${b.key}", b.price, b.season))
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (tab == "bgs" && u.settings.bg.startsWith("s:")) item("bgoff") {
            Glass { SetRow("Revenir à un fond gratuit", "Blob, Aurore, Vagues, Constellation… dans Paramètres › Apparence.") { PButton("Blob", { u.settings.bg = "blob"; pg.store.commit(); ui.themeTick++ }, small = true, kind = "ghost") } }
        }
        if (tab == "themes" && u.settings.fx.isNotEmpty()) item("fx") {
            Glass { SetRow("Effet animé : ${FX_LABELS[u.settings.fx] ?: u.settings.fx}", "Il s’ajoute à l’arrière-plan de ton thème.") { PButton("Retirer", { u.settings.fx = ""; pg.store.commit(); ui.themeTick++ }, small = true, kind = "ghost") } }
        }
        item("note") { Muted("Les icônes, thèmes et fonds achetés restent à toi sur tous tes appareils (avec un compte). Les couleurs de saison restent gratuites.", Modifier.padding(horizontal = 4.dp), small = true) }
    }
}

/** Carte d'une ambiance : aperçu, nom, clair ou sombre, couleurs assorties, essai et prix. */
@Composable
private fun ModeCard(ui: AppState, m: ShopMode, wallet: Int, modifier: Modifier, onBuy: () -> Unit, onTry: () -> Unit) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val owned = pg.owns("mode:${m.key}", m.price, u); val on = u.settings.theme == "m:${m.key}"; val trying = ui.themePreview == "m:${m.key}"
    Glass(modifier, pad = 0.dp, border = if (on || trying) p.flame else null, onClick = onBuy) {
        Box(Modifier.fillMaxWidth().aspectRatio(.72f).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
            ThemePreview(themeLook("m:${m.key}"), Modifier.fillMaxSize(), accent = m.accent.map(::hex))
            Text(if (m.dark) "Sombre" else "Clair", Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = .4f)).padding(horizontal = 8.dp, vertical = 3.dp),
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            GiftBtn(ui, "mode:${m.key}", m.name, m.price, Modifier.align(Alignment.BottomEnd).padding(6.dp), onImage = true)
            if (trying) Text("Essai", Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(p.flame).padding(horizontal = 8.dp, vertical = 3.dp), color = p.onAccent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.padding(12.dp)) {
            Text(m.name, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Muted(m.desc, small = true, maxLines = 2)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                m.accent.forEach { c -> Box(Modifier.size(14.dp).clip(CircleShape).background(hex(c))) }
                if (owned && u.settings.accentCustom != m.accent) Text("Assortir", Modifier.padding(start = 4.dp).clip(CircleShape).clickable { u.settings.accentCustom = m.accent; pg.store.commit(); ui.themeTick++; ui.toast("Couleurs assorties à « ${m.name} ».", "palette") }.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = p.flameInk)
            }
            if (!owned && !trying) PButton("Essayer", onTry, Modifier.fillMaxWidth().padding(top = 8.dp), icon = "eye", small = true, kind = "ghost")
            PriceTag(owned, on, m.price, wallet)
        }
    }
}

/** Bouton « Offrir » d'un article (fonctions sociales actives, article payant). */
@Composable
private fun GiftBtn(ui: AppState, key: String, name: String, price: Int, modifier: Modifier = Modifier, onImage: Boolean = false) {
    if (price <= 0 || !ui.pg.socialOn()) return
    val p = LocalPal.current
    Box(modifier.size(36.dp).clip(CircleShape).background(if (onImage) Color.Black.copy(alpha = .38f) else p.glassStrong).clickable(onClickLabel = "Offrir « $name » à un ami") { ui.open(fr.pepsgoal.app.Sheet.GiftItem(key)) },
        contentAlignment = Alignment.Center) { Pi("gift", size = 18.dp, tint = if (onImage) Color.White else p.ink) }
}

@Composable
private fun PriceTag(owned: Boolean, on: Boolean, price: Int, wallet: Int, buyable: Boolean = true) {
    val p = LocalPal.current
    val (label, bg, ink) = when {
        on -> Triple("✓ Actif", p.ok.copy(alpha = .2f), p.ink)
        owned -> Triple(if (price == 0) "Gratuit" else "Possédé", p.ink.copy(alpha = .07f), p.ink2)
        !buyable -> Triple("⏳ Hors saison", p.ink.copy(alpha = .06f), p.ink3)
        wallet >= price -> Triple("🔥 $price", p.flame.copy(alpha = .22f), p.ink)
        else -> Triple("🔒 $price", p.ink.copy(alpha = .06f), p.ink3)
    }
    Box(Modifier.fillMaxWidth().padding(top = 8.dp).clip(CircleShape).background(bg).padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
        Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = ink)
    }
}

private val FX_LABELS = mapOf("snow" to "neige", "petals" to "pétales", "leaves" to "feuilles", "hearts" to "cœurs", "embers" to "braises", "bubbles" to "bulles", "confetti" to "confettis", "stars" to "étoiles")

/** Étiquette d'édition limitée : « Halloween 🎃 · jusqu'au 5 nov. » ou « revient le 1er déc. ». */
@Composable
private fun SeasonTag(season: String, owned: Boolean) {
    val p = LocalPal.current
    val w = seasonWindow(season)
    val txt = when {
        w != null -> "${SEASON_LABELS[season] ?: season} · jusqu’au ${fmtDateShort(ymd(w.second))}"
        owned -> SEASON_LABELS[season] ?: season
        else -> "${SEASON_LABELS[season] ?: season} · revient le ${seasonNext(season)?.let { fmtDateShort(ymd(it)) } ?: "bientôt"}"
    }
    Text(txt, Modifier.padding(top = 6.dp).clip(CircleShape).background(if (w != null) p.flame.copy(alpha = .16f) else p.ink.copy(alpha = .06f)).padding(horizontal = 8.dp, vertical = 3.dp),
        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (w != null) p.ink else p.ink2, maxLines = 1)
}


/** « Gagner des flammes » : bonus du jour, de la semaine et de la série, avec leur progression. */
@Composable
fun FlameQuestsCard(ui: AppState, modifier: Modifier = Modifier) {
    val p = LocalPal.current; val pg = ui.pg
    val qs = remember(ui.rev) { pg.flameQuests() }
    if (qs.isEmpty()) return
    Glass(modifier) {
        CardHead("Gagner des flammes", emoji = "🔥")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            qs.forEach { q ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(q.icon, if (q.done) p.ok else p.flame, if (q.done) p.type("routine").c else p.flame2, 38.dp, 12.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(q.title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        Bar(q.progress * 100, height = 6.dp, color = if (q.done) p.ok else null)
                        Muted(q.sub, small = true, maxLines = 1)
                    }
                    if (q.done) Chip("Gagné", "check", p.ok) else Chip("+${q.reward} 🔥", color = p.flame.copy(alpha = .18f), ink = p.ink)
                }
            }
        }
        Muted("Et toujours : chaque validation (8 à 40 flammes selon le type) et chaque palier de badge (25 à 400 flammes).", Modifier.padding(top = 10.dp), small = true)
    }
}
