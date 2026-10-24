package fr.pepsgoal.app.ui.screens

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.sys.AppIcons
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.sheets.SheetHead
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.*
import fr.pepsgoal.core.model.Message
import fr.pepsgoal.core.model.User
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/* ------------------------------------------------------------------ Offrir un article de la boutique */

/** Envoie l'article [item] en cadeau dans la conversation privée avec [to] (les flammes sont rendues si l'envoi échoue). */
fun sendItemGift(ui: AppState, to: User, item: ShopItem, note: String) {
    val pg = ui.pg
    pg.giftItemError(item.key)?.let { ui.toast(it, "x"); return }
    pg.spendItemGift(item.key, item.price); ui.bump()
    ui.scope.launch {
        val ok = try {
            ui.cloud.e2e.deliver(null, to.id, buildJsonObject {
                put("k", "gifti"); put("i", item.key); put("n", item.price); put("nm", item.name); put("t", "🎁 ${item.name}")
                if (note.isNotBlank()) put("m", note.trim().take(140))
            }) != null
        } catch (e: Exception) { false }
        if (ok) { Feedback.success(); ui.toast("🎁 « ${item.name} » offert à ${first(to.name)} !", "gift") }
        else { pg.refundItemGift(item.key, item.price); ui.bump(); ui.toast("Envoi impossible : tes flammes te sont rendues.", "x") }
    }
}

/** Utilise un article possédé : applique le thème, l'ambiance, le fond ou l'icône. */
fun useShopItem(ui: AppState, ctx: android.content.Context, key: String) {
    val pg = ui.pg; val u = pg.me() ?: return
    val k = key.substringAfter(':')
    when (key.substringBefore(':')) {
        "theme" -> SHOP_THEMES.firstOrNull { it.key == k }?.let { pg.applyTheme(it) }
        "bg" -> SHOP_BGS.firstOrNull { it.key == k }?.let { pg.applyBg(it) }
        "mode" -> { u.settings.theme = "m:$k"; pg.store.commit() }
        "icon" -> AppIcons.set(ctx, k)
    }
    ui.themeTick++; ui.bump()
}

/** Petit aperçu d'un article : couleurs du thème, ambiance, paysage ou icône. */
@Composable
fun ShopItemPreview(key: String, modifier: Modifier = Modifier, radius: Dp = 16.dp) {
    val k = key.substringAfter(':'); val shape = RoundedCornerShape(radius)
    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        when (key.substringBefore(':')) {
            "theme" -> SHOP_THEMES.firstOrNull { it.key == k }?.let { t ->
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(t.colors.map(::hex)))) { if (t.bg.startsWith("s:")) SceneBg(t.bg.removePrefix("s:"), Modifier.fillMaxSize(), still = true) }
            }
            "mode" -> SHOP_MODES.firstOrNull { it.key == k }?.let { m -> ThemePreview(themeLook("m:${m.key}"), Modifier.fillMaxSize(), accent = m.accent.map(::hex)) }
            "bg" -> SHOP_BGS.firstOrNull { it.key == k }?.let { b -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(b.sky.map(::hex)))) { SceneBg(b.key, Modifier.fillMaxSize(), still = true) } }
            "icon" -> AppIcons.ALL.firstOrNull { it.key == k }?.let { ic -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppIconPreview(ic, minOf(maxWidth, maxHeight) * .72f) } }
        }
    }
}

/**
 * Choix de l'article à offrir : rayons de la boutique, aperçus en grille, article choisi encadré.
 * Les articles hors saison et trop chers restent visibles, grisés.
 */
@Composable
fun ShopItemPicker(ui: AppState, selected: String?, onPick: (ShopItem) -> Unit) {
    val p = LocalPal.current; val pg = ui.pg; val w = pg.wallet()
    var kind by remember { mutableStateOf(selected?.substringBefore(':') ?: "theme") }
    val items = remember(kind) { shopCatalog().filter { it.kind == kind }.sortedBy { it.price } }
    Seg(SHOP_KINDS.map { (k, v) -> k to v.first }, kind, { kind = it })
    items.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { x ->
                val on = x.key == selected
                val ok = pg.giftItemError(x.key) == null
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).border(if (on) 2.5.dp else 1.dp, if (on) p.flame else p.line, RoundedCornerShape(18.dp))
                    .background(if (on) p.flame.copy(alpha = .1f) else Color.Transparent).clickable(onClickLabel = "Choisir « ${x.name} »") { onPick(x) }.padding(6.dp)
                    .graphicsLayer { alpha = if (ok || on) 1f else .5f }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShopItemPreview(x.key, Modifier.fillMaxWidth().aspectRatio(if (kind == "bg") .72f else 1f), 12.dp)
                    Text(x.name, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (x.season != null && seasonWindow(x.season!!) == null) "⏳ Hors saison" else "🔥 ${x.price}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (w >= x.price) p.ink2 else p.ink3)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Offrir un article à un ami, depuis la boutique : choix de l'ami et petit mot. */
@Composable
fun GiftItemSheet(ui: AppState, key: String) {
    val p = LocalPal.current; val pg = ui.pg
    val item = remember(key) { shopItem(key) } ?: run { LaunchedEffect(Unit) { ui.close() }; return }
    val friends = remember(ui.rev) { pg.friendsOf(pg.meId()).filter { it.isNotEmpty() }.mapNotNull { pg.userById(it) }.sortedBy { pg.shownName(it.id, it.name).lowercase() } }
    var to by remember { mutableStateOf<String?>(null) }; var note by remember { mutableStateOf("") }
    val err = pg.giftItemError(item.key)
    SheetHead(ui, "Offrir « ${item.name} »")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ShopItemPreview(item.key, Modifier.size(72.dp), 18.dp)
        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Muted("${SHOP_KINDS[item.kind]?.first ?: ""} · 🔥 ${item.price} · ${fmtNum(pg.wallet())} dans ton porte-monnaie", small = true)
        }
    }
    if (friends.isEmpty()) Empty("Aucun ami pour l’instant", "Ajoute des amis pour leur offrir des articles de la boutique.", "users")
    else {
        Eyebrow("Pour qui ?")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            friends.forEach { f ->
                val on = to == f.id
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) p.flame.copy(alpha = .12f) else Color.Transparent).clickable { to = f.id }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Av(f, 40.dp); Text(pg.shownName(f.id, f.name), Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) p.flame else Color.Transparent).border(2.dp, if (on) p.flame else p.ink3, CircleShape), contentAlignment = Alignment.Center) {
                        if (on) Pi("check", size = 14.dp, tint = p.onAccent)
                    }
                }
            }
        }
        Field(note, { note = it }, "Petit mot (facultatif)", placeholder = "Il t’ira bien !", max = 140, icon = "comment")
        Muted("Si ton ami possède déjà cet article, il reçoit sa valeur en flammes.", small = true)
        val dest = friends.firstOrNull { it.id == to }
        PButton(err ?: if (dest == null) "Choisis un ami" else "Offrir à ${first(pg.shownName(dest.id, dest.name))}", { dest?.let { ui.close(); sendItemGift(ui, it, item, note) } },
            Modifier.fillMaxWidth(), icon = "gift", kind = "primary", enabled = err == null && dest != null && pg.store.online)
    }
}

/** Article offert dans la conversation : aperçu, nom, à ouvrir par le destinataire puis à utiliser. */
@Composable
fun GiftItemCard(ui: AppState, m: Message, b: JsonObject, mine: Boolean) {
    val p = LocalPal.current; val pg = ui.pg; val ctx = LocalContext.current
    val key = b.str("i"); val price = b.int("n").coerceIn(0, 2000); val name = b.str("nm", shopItem(key)?.name ?: "Article de la boutique")
    var opened by remember(m.id, ui.rev) { mutableStateOf(pg.giftOpened(m.id)) }
    val converted = remember(m.id, ui.rev) { pg.me()?.shopClaimed?.any { it.startsWith("giftin:${m.id}:") } == true }
    val wiggle = if (!mine && !opened && !p.motion) rememberInfiniteTransition(label = "gifti").animateFloat(-5f, 5f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "giftiW").value else 0f
    val ink = Color(0xFF2A1408)
    Column(Modifier.widthIn(min = 210.dp, max = 260.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFE08A), Color(0xFFFF9A4A), Color(0xFFFF5E8A))))
        .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(120.dp).graphicsLayer { rotationZ = if (opened || mine) 0f else wiggle * .4f }, contentAlignment = Alignment.Center) {
            if (opened || mine) ShopItemPreview(key, Modifier.fillMaxSize(), 16.dp)
            else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .28f)), contentAlignment = Alignment.Center) {
                Box(Modifier.graphicsLayer { rotationZ = wiggle }) { Emoji("🎁", 64.dp) }
            }
        }
        Text(if (opened || mine) name else "Un cadeau de la boutique", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = ink, textAlign = TextAlign.Center)
        b.strN("m")?.takeIf { it.isNotBlank() }?.let { Text("« $it »", fontStyle = FontStyle.Italic, color = Color(0xFF3A2010), textAlign = TextAlign.Center, fontSize = 14.sp) }
        when {
            mine -> Text("Cadeau offert · 🔥 $price", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A2010))
            opened && converted -> Text("Déjà dans ta collection : +$price flammes", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A2010), textAlign = TextAlign.Center)
            opened -> PButton("Utiliser", { useShopItem(ui, ctx, key); ui.toast("« $name » appliqué.", "palette") }, icon = "palette", small = true)
            else -> PButton("Ouvrir le cadeau", {
                when (pg.openItemGift(m.id, key, price)) {
                    ItemGift.ADDED -> { opened = true; Feedback.success(); ui.sparks(price / 10); ui.toast("« $name » rejoint ta collection !", "gift"); ui.bump() }
                    ItemGift.CONVERTED -> { opened = true; Feedback.success(); ui.sparks(price / 10); ui.toast("Tu l’avais déjà : +$price flammes dans ton porte-monnaie !", "gift"); ui.bump() }
                    null -> {}
                }
            }, icon = "gift", small = true, kind = "primary")
        }
    }
}
