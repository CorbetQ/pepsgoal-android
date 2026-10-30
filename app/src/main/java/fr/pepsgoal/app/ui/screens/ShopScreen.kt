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
import fr.pepsgoal.app.ui.theme.step
import fr.pepsgoal.core.*
import kotlinx.coroutines.launch

/** Aperçu d'une icône de l'application (fond en dégradé et premier plan, comme sur l'écran d'accueil). */
@Composable
fun AppIconPreview(ic: AppIcons.Icon, size: Dp = 54.dp, selected: Boolean = false) {
    val p = LocalPal.current
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .3f)).background(Brush.linearGradient(ic.bg.map { Color(it) }))
        .border(if (selected) 3.dp else 2.dp, if (selected) p.focusEdge else p.edge, RoundedCornerShape(size * .3f)), contentAlignment = Alignment.Center) {
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
    // Héros : solde en plaque à droite ; quête du jour en carte de la flamme ; onglets défilants
    ScreenFrame(ui, "Boutique", "Retour", { ui.back() }, icon = "star", color = p.type("measure").c, color2 = p.type("measure").c, emoji = "🛍️",
        right = { Plaque(Modifier.clip(CircleShape).clickable(onClickLabel = "Porte-monnaie") { ui.open(fr.pepsgoal.app.Sheet.Wallet) }) { Flame(20.dp); Text(fmtNum(wallet), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) } }) {
        item("wallet") { Hint("Chaque validation rapporte des flammes. Les dépenser ne fait jamais baisser ton niveau ni ta série." + if (pg.socialOn()) " Touche 🎁 sur un article pour l’offrir à un ami." else "") }
        item("quests") { FlameQuestsCard(ui) }
        item("tabs") {
            val keys = listOf("bgs", "themes", "modes", "icons")
            PTabs(listOf("Fonds" to "🏞️", "Thèmes" to "🎨", "Ambiances" to "🌗", "Icônes" to "📱"), keys.indexOf(tab).coerceAtLeast(0), { tab = keys[it]; ui.shopTab = keys[it] }, color = p.type("measure").c)
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
                    Box(Modifier.fillMaxWidth().padding(6.dp).height(if (scene) 150.dp else 96.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(t.colors.map(::hex)))) {
                        if (scene) SceneBg(t.bg.removePrefix("s:"), Modifier.fillMaxSize(), still = true)
                        GiftBtn(ui, "theme:${t.key}", t.name, t.price, Modifier.align(Alignment.BottomEnd).padding(8.dp), onImage = true)
                        Row(Modifier.align(Alignment.BottomStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Pastilles de couleur sur plaque
                            Plaque { t.colors.forEach { c -> Box(Modifier.size(20.dp).clip(CircleShape).background(hex(c)).border(2.dp, p.edge, CircleShape)) } }
                        }
                        MiniPlaque((BGS.firstOrNull { it.first == t.bg }?.second ?: SHOP_BGS.firstOrNull { "s:${it.key}" == t.bg }?.let { "Paysage spécial" } ?: "") + (FX_LABELS[t.fx]?.let { " · $it" } ?: ""), Modifier.align(Alignment.TopEnd).padding(10.dp))
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
                        // Carte de fond : aperçu (rayon 18), nom ; actif = bord lime et pilule « ✓ Actif »
                        Glass(Modifier.weight(1f), pad = 0.dp, border = if (on) vivid(p.type("routine").c).step() else null, onClick = { purchase("bg:${b.key}", b.name, b.price, b.season) { pg.applyBg(b); ui.themeTick++; ui.toast("Fond « ${b.name} » appliqué.", "image") } }) {
                            Box(Modifier.fillMaxWidth().padding(6.dp).aspectRatio(.66f).clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(b.sky.map(::hex)))) {
                                SceneBg(b.key, Modifier.fillMaxSize(), still = true)
                                if (on) Row(Modifier.align(Alignment.TopStart).padding(8.dp).background(vivid(p.type("routine").c), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Pi("check", size = 14.dp, tint = fr.pepsgoal.app.ui.theme.PLUM); Text(" Actif", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = fr.pepsgoal.app.ui.theme.PLUM)
                                }
                                GiftBtn(ui, "bg:${b.key}", b.name, b.price, Modifier.align(Alignment.TopEnd).padding(6.dp), onImage = true)
                            }
                            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 2.dp)) {
                                Text(b.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
                                Muted(b.desc, small = true, maxLines = 2)
                                if (on) Chip("Sur tout l’écran", "image", p.type("routine").c, modifier = Modifier.padding(top = 6.dp))
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
            Glass { SetRow("Revenir à un fond gratuit", "Collines braise, Aurore, Vagues, Constellation… dans Paramètres › Apparence.") { PButton("Collines braise", { u.settings.bg = "pop"; pg.store.commit(); ui.themeTick++ }, small = true) } }
        }
        if (tab == "themes" && u.settings.fx.isNotEmpty()) item("fx") {
            Glass { SetRow("Effet animé : ${FX_LABELS[u.settings.fx] ?: u.settings.fx}", "Il s’ajoute à l’arrière-plan de ton thème.") { PButton("Retirer", { u.settings.fx = ""; pg.store.commit(); ui.themeTick++ }, small = true, kind = "ghost") } }
        }
        item("note") { Hint("Les icônes, thèmes et fonds achetés restent à toi sur tous tes appareils (avec un compte). Les couleurs de saison restent gratuites.") }
    }
}

/** Carte d'une ambiance : aperçu, nom, clair ou sombre, couleurs assorties, essai et prix. */
@Composable
private fun ModeCard(ui: AppState, m: ShopMode, wallet: Int, modifier: Modifier, onBuy: () -> Unit, onTry: () -> Unit) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    val owned = pg.owns("mode:${m.key}", m.price, u); val on = u.settings.theme == "m:${m.key}"; val trying = ui.themePreview == "m:${m.key}"
    Glass(modifier, pad = 0.dp, border = if (on) vivid(p.type("routine").c).step() else if (trying) p.focusEdge else null, onClick = onBuy) {
        Box(Modifier.fillMaxWidth().padding(6.dp).aspectRatio(.74f).clip(RoundedCornerShape(18.dp))) {
            ThemePreview(themeLook("m:${m.key}"), Modifier.fillMaxSize(), accent = m.accent.map(::hex))
            MiniPlaque(if (m.dark) "Sombre" else "Clair", Modifier.align(Alignment.TopStart).padding(8.dp))
            GiftBtn(ui, "mode:${m.key}", m.name, m.price, Modifier.align(Alignment.BottomEnd).padding(6.dp), onImage = true)
            if (trying) Chip("Essai", "eye", p.flame, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Column(Modifier.padding(12.dp)) {
            Text(m.name, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Muted(m.desc, small = true, maxLines = 2)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                m.accent.forEach { c -> Box(Modifier.size(14.dp).clip(CircleShape).background(hex(c))) }
                if (owned && u.settings.accentCustom != m.accent) PButton("Assortir", { u.settings.accentCustom = m.accent; pg.store.commit(); ui.themeTick++; ui.toast("Couleurs assorties à « ${m.name} ».", "palette") }, kind = "ghost", small = true)
            }
            if (!owned && !trying) PButton("Essayer", onTry, Modifier.fillMaxWidth().padding(top = 8.dp), icon = "eye", small = true, kind = "ghost")
            PriceTag(owned, on, m.price, wallet)
        }
    }
}

/** Bouton « Offrir » d'un article (fonctions sociales actives, article payant) : rond à marche. */
@Composable
private fun GiftBtn(ui: AppState, key: String, name: String, price: Int, modifier: Modifier = Modifier, onImage: Boolean = false) {
    if (price <= 0 || !ui.pg.socialOn()) return
    Box(modifier) { RoundGlass("Offrir « $name » à un ami", { ui.open(fr.pepsgoal.app.Sheet.GiftItem(key)) }) { Pi("gift", size = 18.dp, tint = LocalPal.current.ink) } }
}

/**
 * État d'un article : Actif (pilule lime cochée), Utiliser (petit secondaire), Prix (puce or à marche et flamme),
 * Verrouillé (cadenas et flammes manquantes), Hors saison.
 */
@Composable
private fun PriceTag(owned: Boolean, on: Boolean, price: Int, wallet: Int, buyable: Boolean = true) {
    val p = LocalPal.current
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterStart) {
        when {
            on -> Row(Modifier.background(vivid(p.type("routine").c), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Pi("check", size = 14.dp, tint = fr.pepsgoal.app.ui.theme.PLUM); Text("Actif", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = fr.pepsgoal.app.ui.theme.PLUM) }
            owned -> Box(Modifier.chunk(p.surface, p.edge, 18.dp, 3.dp, 2.dp).padding(horizontal = 12.dp, vertical = 6.dp)) { Text(if (price == 0) "Gratuit · Utiliser" else "Utiliser", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
            !buyable -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Emoji("⏳", 14.dp); Text("Hors saison", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2) }
            wallet >= price -> Row(Modifier.chunk(p.gold, p.gold.step(), 18.dp, 3.dp, 0.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Flame(16.dp); Text(" ${fmtNum(price)}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = fr.pepsgoal.app.ui.theme.PLUM) }
            else -> Row(verticalAlignment = Alignment.CenterVertically) { Pi("lock", size = 14.dp, tint = p.ink3); Text(" ${fmtNum(price)} · encore ${fmtNum(price - wallet)} 🔥", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2) }
        }
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
    // Étiquette néon pendant la saison, neutre sinon
    Chip(txt, color = if (w != null) p.type("deadline").c else null, modifier = Modifier.padding(top = 6.dp))
}


/** « Gagner des flammes » : bonus du jour, de la semaine et de la série, avec leur progression. */
@Composable
fun FlameQuestsCard(ui: AppState, modifier: Modifier = Modifier) {
    val p = LocalPal.current; val pg = ui.pg
    val qs = remember(ui.rev) { pg.flameQuests() }
    if (qs.isEmpty()) return
    // Carte de la flamme à marche, encre prune
    Column(modifier.fillMaxWidth().enter().chunk(p.flameBrush(), p.flameStep, 24.dp, 5.dp, 0.dp).padding(16.dp)) {
        Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(44.dp, 48.dp).chunk(Color.White, p.flameStep, 22.dp, 4.dp, 0.dp), contentAlignment = Alignment.Center) { Emoji("🎯", 26.dp) }
            Text("Gagner des flammes", fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = fr.pepsgoal.app.ui.theme.PLUM)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            qs.forEach { q ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(q.title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = fr.pepsgoal.app.ui.theme.PLUM)
                        Box(Modifier.fillMaxWidth().height(10.dp).background(Color.White.copy(alpha = .42f), CircleShape)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(q.progress.toFloat().coerceIn(0f, 1f)).background(fr.pepsgoal.app.ui.theme.PLUM, CircleShape))
                        }
                        Text(q.sub, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fr.pepsgoal.app.ui.theme.PLUM, maxLines = 1)
                    }
                    if (q.done) Chip("Gagné", "check", p.type("routine").c) else Chip("+${q.reward} 🔥", color = p.gold)
                }
            }
        }
        Text("Et toujours : chaque validation (8 à 40 flammes selon le type) et chaque palier de badge (25 à 400 flammes).", Modifier.padding(top = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fr.pepsgoal.app.ui.theme.PLUM)
    }
}
