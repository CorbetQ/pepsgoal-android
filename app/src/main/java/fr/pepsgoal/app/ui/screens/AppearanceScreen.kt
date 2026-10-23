package fr.pepsgoal.app.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.sys.AppIcons
import fr.pepsgoal.app.sys.Season
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Paramètres › Apparence : aperçu en direct en tête, puis des sections courtes (thème, couleur, arrière-plan, icône,
 * effets et saisons, langue) et les réglages techniques du verre givré repliés tout en bas.
 */

private fun commit(ui: AppState) { ui.pg.store.commit() }

/** Effets animés proposés : ceux des thèmes possédés (plus la neige, offerte en hiver par les couleurs de saison). */
private val FX_NAMES = linkedMapOf("snow" to "Neige", "petals" to "Pétales", "leaves" to "Feuilles", "hearts" to "Cœurs", "embers" to "Braises",
    "bubbles" to "Bulles", "confetti" to "Confettis", "stars" to "Étoiles")

/** Nom lisible de l'arrière-plan en cours. */
private fun bgName(bg: String): String = SHOP_BGS.firstOrNull { "s:${it.key}" == bg }?.name ?: BGS.firstOrNull { it.first == bg }?.second ?: "Blob"

/** Page Apparence, insérée dans la liste des réglages (un seul élément de la LazyColumn). */
@Composable
fun AppearancePage(ui: AppState, u: User) {
    val dynOk = Build.VERSION.SDK_INT >= 31
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LivePreview(ui, u)
        ThemeSection(ui, u, dynOk)
        ColorSection(ui, u, dynOk)
        BackgroundSection(ui, u)
        IconSection(ui)
        EffectsSection(ui, u)
        LanguageSection(ui)
        Glass { SetRow("Icône de profil", "Photo, emoji ou pictogramme") { Av(u, 44.dp); PButton("Changer", { ui.open(Sheet.Avatar) }, icon = "image", small = true) } }
        AdvancedSection(ui, u)
    }
}

/* ------------------------------------------------------------------ aperçu en direct */

/** Mini-téléphone : le vrai arrière-plan, une carte en verre, la flamme et un bouton à la couleur d'accent. */
@Composable
private fun LivePreview(ui: AppState, u: User) {
    val p = LocalPal.current; val s = u.settings
    val iconKey = AppIcons.current(LocalContext.current)
    val icon = AppIcons.ALL.firstOrNull { it.key == iconKey }
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val shape = RoundedCornerShape(26.dp)
            Box(Modifier.size(132.dp, 250.dp).clip(shape).background(p.bg).border(3.dp, p.ink.copy(alpha = .85f), shape)) {
                Backdrop(s.bg, null, false, ui.prefs.bgStamp, s.fx)
                Column(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    // Barre du haut : titre et pastille de série
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FitText(tr("Aujourd’hui"), Modifier.weight(1f), size = 11, min = 7, color = p.ink, weight = FontWeight.ExtraBold, family = Unbounded)
                        Row(Modifier.clip(CircleShape).background(p.glassStrong).padding(horizontal = 5.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Flame(10.dp); Text("12", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    // Carte en verre factice
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.glassStrong).border(1.dp, p.glassEdge, RoundedCornerShape(12.dp)).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Box(Modifier.fillMaxWidth(.8f).height(5.dp).clip(CircleShape).background(p.ink.copy(alpha = .75f)))
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth(.55f).height(4.dp).clip(CircleShape).background(p.ink.copy(alpha = .35f)))
                            }
                            Flame(22.dp)
                        }
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(p.ink.copy(alpha = .1f))) {
                            Box(Modifier.fillMaxWidth(.66f).fillMaxHeight().clip(CircleShape).background(p.accent))
                        }
                    }
                    repeat(2) { i ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.glass).padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(p.type(if (i == 0) "routine" else "objective").c))
                            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(p.ink.copy(alpha = .4f)))
                            Box(Modifier.size(10.dp).clip(CircleShape).border(1.5.dp, if (i == 0) p.ok else p.ink3, CircleShape))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().height(22.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                        FitText(tr("Valider"), size = 9, min = 6, color = p.onAccent, weight = FontWeight.ExtraBold)
                    }
                }
            }
            // Résumé des choix, à côté de l'aperçu
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Aperçu", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                PreviewLine("Thème", themeName(s.theme))
                PreviewLine("Couleur", if (ui.prefs.materialYou && Build.VERSION.SDK_INT >= 31) "Material You" else if (validAccent(s.accentCustom)) (SHOP_THEMES.firstOrNull { it.colors == s.accentCustom }?.name ?: "Personnalisée") else (ACCENTS.getOrNull(s.accent) ?: ACCENTS[0]).name)
                PreviewLine("Fond", if (s.theme == "weather") "Ciel en direct" else bgName(s.bg))
                PreviewLine("Icône", icon?.label ?: "Braise")
                PButton("Boutique · ${ui.pg.wallet(u)} 🔥", { ui.push(Screen.Shop) }, icon = "star", small = true)
            }
        }
    }
}

@Composable
private fun PreviewLine(k: String, v: String) {
    val p = LocalPal.current
    Column {
        Text(tr(k).uppercase(), fontSize = 10.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.ExtraBold, color = p.ink3)
        FitText(tr(v), size = 14, min = 10, color = p.ink, weight = FontWeight.ExtraBold)
    }
}

/* ------------------------------------------------------------------ briques */

/** En-tête de section : pastille d'icône, titre et sous-titre facultatif. */
@Composable
private fun Head(icon: String, title: String, sub: String? = null, modifier: Modifier = Modifier.fillMaxWidth(), right: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPal.current
    Row(modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, p.flame, p.pink, 36.dp, 12.dp)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            if (sub != null) Muted(sub, small = true, maxLines = 2)
        }
        right()
    }
}

/** Sélecteur segmenté pleine largeur : chaque option a la même part, libellé ajusté sur une ligne. */
@Composable
private fun <T> SegFill(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit, enabled: Boolean = true) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else .5f).clip(RoundedCornerShape(16.dp)).background(p.ink.copy(alpha = if (p.dark) .1f else .07f)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (k, l) ->
            val on = k == value
            Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp)).background(if (on) p.glassStrong else Color.Transparent)
                .then(if (on) Modifier.border(1.dp, p.glassEdge, RoundedCornerShape(12.dp)) else Modifier)
                .clickable(enabled = enabled, onClickLabel = l) { onChange(k) }.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                FitText(tr(l), size = 13, min = 9, color = if (on) p.ink else p.ink2, weight = FontWeight.ExtraBold)
            }
        }
    }
}

/** Carrousel horizontal avec aimantation sur les tuiles. */
@Composable
private fun <T> Carousel(list: List<T>, start: Int = 0, cell: @Composable (T) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start.coerceAtLeast(0))
    LazyRow(Modifier.fillMaxWidth(), state = state, flingBehavior = rememberSnapFlingBehavior(state), horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 4.dp)) {
        items(list) { cell(it) }
    }
}

/** Tuile d'un carrousel : aperçu arrondi et libellé sur une ligne (jamais tronqué : il rétrécit). */
@Composable
private fun Tile(label: String, selected: Boolean, w: Dp, h: Dp, onClick: () -> Unit, badge: String? = null, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPal.current
    val sc by animateFloatAsState(if (selected && !p.motion) 1f else .96f, label = "tile")
    Column(Modifier.width(w).clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = label, onClick = onClick).padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = RoundedCornerShape(16.dp)
        Box(Modifier.size(w - 4.dp, h).graphicsLayerScale(sc).clip(shape).border(if (selected) 3.dp else 1.dp, if (selected) p.flame else p.line, shape)) {
            content()
            if (badge != null) Text(badge, Modifier.align(Alignment.TopEnd).padding(5.dp).clip(CircleShape).background(Color.Black.copy(alpha = .45f)).padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            if (selected) Box(Modifier.align(Alignment.BottomEnd).padding(5.dp).size(20.dp).clip(CircleShape).background(p.flame), contentAlignment = Alignment.Center) { Pi("check", size = 13.dp, tint = p.onAccent) }
        }
        FitText(tr(label), Modifier.padding(top = 6.dp), size = 12, min = 8, color = if (selected) p.ink else p.ink2, weight = if (selected) FontWeight.ExtraBold else FontWeight.Bold)
    }
}

private fun Modifier.graphicsLayerScale(s: Float) = this.then(Modifier.graphicsLayer(scaleX = s, scaleY = s))

/* ------------------------------------------------------------------ thème */

/** Description courte de chaque thème de base. */
private val THEME_DESC = mapOf("system" to "Clair le jour, sombre la nuit, comme ton téléphone", "light" to "Lumineux et doux", "dark" to "Bleu nuit, reposant pour les yeux",
    "oled" to "Noir pur : économise la batterie des écrans OLED", "weather" to "Un ciel en direct selon l’heure et le temps de ta ville")

@Composable
private fun ThemeSection(ui: AppState, u: User, dynOk: Boolean) {
    val p = LocalPal.current; val s = u.settings
    // Ambiances achetées, et l'ambiance secrète une fois trouvée
    val owned = (SHOP_MODES + Eggs.SECRET_MODES).filter { ui.pg.owns("mode:${it.key}", it.price, u) }
    val pick = { k: String ->
        s.theme = k; commit(ui); ui.themeTick++
        if (k == "weather" && s.weather == null) ui.toast("Choisis ta ville dans Paramètres › Météo pour un ciel en direct.", "cloud")
    }
    val cur = shopMode(s.theme)
    Glass {
        Head("sun", "Thème", cur?.desc ?: THEME_DESC[s.theme] ?: "Apparence de toute l’interface") { Chip(themeName(s.theme), if (cur != null) "star" else null, p.flame.copy(alpha = .18f), p.ink) }
        // Thèmes de base, puis une tuile vers les ambiances de la boutique
        Box(Modifier.padding(bottom = 8.dp)) { Eyebrow("De base") }
        val base = THEMES.map { it.first to it.second } + ("shop" to "Ambiances")
        base.chunked(3).forEach { row ->
            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (k, l) ->
                    if (k == "shop") ShopModesTile(ui, owned.count { it in SHOP_MODES }, Modifier.weight(1f))
                    else ThemeCard(k, l, s.theme == k, { pick(k) }, Modifier.weight(1f), height = 104.dp)
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (owned.isNotEmpty()) {
            Box(Modifier.padding(top = 4.dp, bottom = 8.dp)) { Eyebrow("Mes ambiances") }
            owned.chunked(3).forEach { row ->
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m -> ThemeCard("m:${m.key}", m.name, s.theme == "m:${m.key}", { pick("m:${m.key}") }, Modifier.weight(1f), height = 104.dp, badge = if (m in Eggs.SECRET_MODES) "Secrète" else if (m.dark) "Sombre" else "Clair") }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        SetRow("Couleurs Material You", if (dynOk) "Les couleurs de ton fond d’écran Android sur toute l’interface." else "Nécessite Android 12 ou plus récent.") {
            PSwitch(ui.prefs.materialYou && dynOk, { ui.prefs.materialYou = it; ui.themeTick++; ui.toast(if (it) "Couleurs Material You activées." else "Couleurs PepsGoal rétablies.", "palette") }, enabled = dynOk)
        }
        if (ui.prefs.materialYou && dynOk) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(p.flame2, p.flame, p.pink, p.glassStrong, p.bg).forEach { Box(Modifier.size(22.dp).clip(CircleShape).background(it).border(1.dp, p.line, CircleShape)) }
            Muted(if (cur != null) "Propre à cet appareil · l’ambiance garde son fond et son texte" else "Propre à cet appareil", Modifier.padding(start = 4.dp), small = true)
        }
    }
}

/** Tuile « Ambiances » : aperçu en éventail de trois ambiances, et accès à la boutique. */
@Composable
private fun ShopModesTile(ui: AppState, owned: Int, modifier: Modifier) {
    val p = LocalPal.current
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = "Ambiances de la boutique") { ui.shopTab = "modes"; ui.push(Screen.Shop) }.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, p.line, RoundedCornerShape(16.dp)).background(p.ink.copy(alpha = .05f)), contentAlignment = Alignment.Center) {
            listOf("m:sakura", "m:neon", "m:mint").forEachIndexed { i, k ->
                ThemePreview(themeLook(k), Modifier.size(38.dp, 62.dp).graphicsLayer { rotationZ = (i - 1) * 12f; translationX = (i - 1) * 16.dp.toPx() }.clip(RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(alpha = .6f), RoundedCornerShape(8.dp)))
            }
            Text(if (owned > 0) "$owned / ${SHOP_MODES.size}" else "${SHOP_MODES.size}", Modifier.align(Alignment.TopEnd).padding(5.dp).clip(CircleShape).background(p.flame).padding(horizontal = 6.dp, vertical = 2.dp), color = p.onAccent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
        FitText(tr("Ambiances"), Modifier.padding(top = 6.dp), size = 12, min = 8, color = p.ink2, weight = FontWeight.Bold)
    }
}

/* ------------------------------------------------------------------ couleur */

@Composable
private fun ColorSection(ui: AppState, u: User, dynOk: Boolean) {
    val p = LocalPal.current; val s = u.settings
    val my = ui.prefs.materialYou && dynOk
    val custom = validAccent(s.accentCustom)
    val ownedThemes = SHOP_THEMES.filter { ui.pg.owns("theme:${it.key}", it.price, u) }
    val curName = if (custom) (SHOP_THEMES.firstOrNull { it.colors == s.accentCustom }?.name ?: "Personnalisée") else (ACCENTS.getOrNull(s.accent) ?: ACCENTS[0]).name
    Glass(Modifier.alpha(if (my) .55f else 1f)) {
        Head("palette", "Couleur", if (my) "Remplacée par Material You sur cet appareil" else "Flammes, boutons et anneau de progression") {
            Text(tr(curName), Modifier.clip(CircleShape).background(p.ink.copy(alpha = .07f)).padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        val start = if (custom) 0 else s.accent
        val state = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, start - 2))
        LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            if (custom) item("custom") { Swatch(Brush.linearGradient(s.accentCustom!!.map(::hex)), true, curName) {} }
            itemsIndexed(ACCENTS) { i, a -> Swatch(accentBrush(i), !custom && s.accent == i, a.name) { s.accent = i; s.accentCustom = null; commit(ui) } }
        }
        // Thèmes achetés dans la boutique : couleurs, fond et effet d'un geste
        if (ownedThemes.isNotEmpty()) {
            Text(tr("Mes thèmes"), Modifier.padding(top = 12.dp, bottom = 6.dp), fontWeight = FontWeight.ExtraBold)
            Carousel(ownedThemes) { t ->
                val on = s.accentCustom == t.colors && s.bg == t.bg
                Tile(t.name, on, 104.dp, 64.dp, { ui.pg.applyTheme(t); ui.themeTick++; ui.toast("Thème « ${t.name} » appliqué.", "palette") }) {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(t.colors.map(::hex))))
                }
            }
        }
        PButton("Thèmes de la boutique", { ui.push(Screen.Shop) }, icon = "star", small = true, kind = "ghost", modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Swatch(b: Brush, on: Boolean, name: String, onClick: () -> Unit) {
    val p = LocalPal.current
    val sz by animateFloatAsState(if (on) 46f else 40f, label = "sw")
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(sz.dp).clip(CircleShape).background(b).border(if (on) 3.dp else 1.dp, if (on) p.ink else p.line, CircleShape).clickable(onClickLabel = name, onClick = onClick),
            contentAlignment = Alignment.Center) { if (on) Pi("check", size = 18.dp, tint = Color.White) }
    }
}

/* ------------------------------------------------------------------ arrière-plan */

@Composable
private fun BackgroundSection(ui: AppState, u: User) {
    val p = LocalPal.current; val s = u.settings; val ctx = LocalContext.current
    val file = bgFile(ctx); val has = remember(ui.prefs.bgStamp, ui.rev) { file.exists() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ui.scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val b = Media.decode(ctx, uri, 1920) ?: return@withContext null
                file.writeBytes(Media.jpeg(b, 82)); Media.palette(b)
            }
            if (ok == null) { ui.toast("Impossible de lire cette image. Essaie un fichier JPG ou PNG.", "image"); return@launch }
            ui.prefs.bgPalette = ok; ui.prefs.bgStamp = nowMs(); s.bg = "custom"; commit(ui); ui.themeTick++
            ui.toast("Arrière-plan personnalisé appliqué.", "image")
        }
    }
    val openPicker = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val weather = s.theme == "weather"
    // Tuiles : fonds gratuits (rendus en vrai, figés), fonds illustrés possédés, image perso, boutique
    val free = BGS.filter { it.first != "custom" }
    val scenes = SHOP_BGS.filter { ui.pg.owns("bg:${it.key}", it.price, u) }
    val more = SHOP_BGS.size - scenes.size
    Glass {
        Head("image", "Arrière-plan", if (weather) "Le thème Météo affiche le ciel en direct : change de thème pour choisir un fond." else "${bgName(s.bg)} · derrière toute l’application")
        Box(Modifier.alpha(if (weather) .5f else 1f)) {
            val all = free.map { "f:${it.first}" } + scenes.map { "s:${it.key}" } + listOf("custom", "shop")
            val start = all.indexOfFirst { k -> (k.startsWith("f:") && k.removePrefix("f:") == s.bg) || k == s.bg }.coerceAtLeast(0)
            Carousel(all, start = maxOf(0, start - 1)) { k ->
                when {
                    k.startsWith("f:") -> {
                        val key = k.removePrefix("f:")
                        Tile(BGS.first { it.first == key }.second, s.bg == key, 86.dp, 132.dp, { if (!weather) { s.bg = key; commit(ui) } }) { MiniBackdrop(key, ui) }
                    }
                    k.startsWith("s:") -> {
                        val b = SHOP_BGS.first { "s:${it.key}" == k }
                        Tile(b.name, s.bg == k, 86.dp, 132.dp, { if (!weather) { ui.pg.applyBg(b); ui.themeTick++ } }) { SceneBg(b.key, Modifier.fillMaxSize(), still = true) }
                    }
                    k == "custom" -> Tile(if (has) "Mon image" else "Ma photo", s.bg == "custom" && has, 86.dp, 132.dp, { if (!weather) { if (has) { s.bg = "custom"; commit(ui) } else openPicker() } }) {
                        if (has) AsyncImage(ImageRequest.Builder(ctx).data(file).memoryCacheKey("bg${ui.prefs.bgStamp}").crossfade(true).build(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Box(Modifier.fillMaxSize().background(p.ink.copy(alpha = .06f)), contentAlignment = Alignment.Center) { Pi("plus", size = 26.dp, tint = p.ink2) }
                    }
                    else -> Tile("Boutique", false, 86.dp, 132.dp, { ui.push(Screen.Shop) }, badge = if (more > 0) "+$more" else null) {
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(p.flame2, p.flame, p.pink))), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Pi("star", size = 26.dp, tint = p.onAccent); Text(tr("Fonds"), color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
        // Image personnalisée : changer, retirer, flou, assombrissement et couleurs extraites
        if (has) {
            SetRow("Mon image", "Une photo à toi, gardée sur cet appareil.") {
                PButton("Changer", { openPicker() }, icon = "image", small = true)
                PButton("Retirer", { file.delete(); ui.prefs.bgPalette = emptyList(); ui.prefs.bgStamp = nowMs(); if (s.bg == "custom") s.bg = "blob"; commit(ui); ui.toast("Image d’arrière-plan retirée.", "image") }, kind = "ghost", small = true)
            }
            if (s.bg == "custom") {
                var blur by remember { mutableIntStateOf(ui.prefs.raw.getInt("bgBlur", 0)) }
                var dim by remember { mutableFloatStateOf(ui.prefs.raw.getFloat("bgDim", -1f)) }
                Text(tr("Flou de l’image"), Modifier.padding(top = 4.dp, bottom = 6.dp), fontWeight = FontWeight.Bold)
                SegFill(listOf(0 to "Aucun", 8 to "Léger", 18 to "Moyen", 32 to "Fort"), blur, { blur = it; ui.prefs.raw.edit().putInt("bgBlur", it).apply(); ui.prefs.bgStamp = nowMs() })
                if (Build.VERSION.SDK_INT < 31) Muted("Le flou demande Android 12 ou plus récent.", small = true)
                Text(tr("Assombrissement"), Modifier.padding(top = 10.dp, bottom = 6.dp), fontWeight = FontWeight.Bold)
                SegFill(listOf(-1f to "Auto", 0f to "Aucun", .3f to "Léger", .5f to "Moyen", .7f to "Fort"), dim, { dim = it; ui.prefs.raw.edit().putFloat("bgDim", it).apply(); ui.prefs.bgStamp = nowMs() })
            }
            val pal = ui.prefs.bgPalette
            if (pal.isNotEmpty()) {
                Text(tr("Couleurs de ton image"), Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold)
                Muted("Touche-en une pour en faire ta couleur d’accent.", small = true)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pal.forEach { c ->
                        val on = validAccent(s.accentCustom) && Media.accentFrom(c)[1] == s.accentCustom!![1]
                        Box(Modifier.size(34.dp).clip(CircleShape).background(hex(c)).border(if (on) 3.dp else 1.dp, if (on) p.ink else p.line, CircleShape)
                            .clickable(onClickLabel = "Utiliser la couleur $c") { s.accentCustom = Media.accentFrom(c); commit(ui); ui.toast("Couleur d’accent tirée de ton arrière-plan.", "palette") })
                    }
                }
            }
        }
    }
}

/** Fond gratuit rendu pour de vrai dans une tuile, figé (pas d'animation dans la liste : batterie et fluidité). */
@Composable
private fun MiniBackdrop(kind: String, ui: AppState) {
    val p = LocalPal.current
    CompositionLocalProvider(LocalPal provides p.copy(motion = true)) {
        Box(Modifier.fillMaxSize().background(p.bg)) { Backdrop(kind, null, false, ui.prefs.bgStamp) }
    }
}

/* ------------------------------------------------------------------ icône de l'application */

@Composable
private fun IconSection(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current; val u = ui.pg.me()
    var cur by remember { mutableStateOf(AppIcons.current(ctx)) }
    LaunchedEffect(Unit) { keepCurrentIcon(ui, ctx) }
    val owned = AppIcons.ALL.filter { ui.pg.owns("icon:${it.key}", it.price, u) }
    val more = AppIcons.ALL.size - owned.size
    val current = AppIcons.ALL.firstOrNull { it.key == cur } ?: AppIcons.ALL.first()
    Glass {
        Head("star", "Icône de l’application")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            AppIconPreview(current, 72.dp, true)
            Column(Modifier.weight(1f)) {
                FitText(tr(current.label), size = 18, min = 12, color = p.ink, weight = FontWeight.ExtraBold, family = Unbounded)
                Muted("Sur l’écran d’accueil après quelques secondes ; selon le lanceur, un raccourci posé peut devoir être remis.", small = true, maxLines = 3)
            }
        }
        Carousel(owned.map { it.key } + "shop", start = maxOf(0, owned.indexOfFirst { it.key == cur } - 1)) { k ->
            if (k == "shop") Tile("Boutique", false, 74.dp, 66.dp, { ui.push(Screen.Shop) }, badge = if (more > 0) "+$more" else null) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(p.flame2, p.flame, p.pink))), contentAlignment = Alignment.Center) { Pi("star", size = 24.dp, tint = p.onAccent) }
            } else {
                val ic = AppIcons.ALL.first { it.key == k }
                Tile(ic.label, cur == k, 74.dp, 66.dp, { AppIcons.set(ctx, ic.key); cur = ic.key; ui.toast("Icône « ${ic.label} » activée.", "palette") }) {
                    Box(Modifier.fillMaxSize().background(p.ink.copy(alpha = .04f)), contentAlignment = Alignment.Center) { AppIconPreview(ic, 54.dp, false) }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ effets et saisons */

@Composable
private fun EffectsSection(ui: AppState, u: User) {
    val s = u.settings
    // Effets disponibles : ceux des thèmes possédés, plus celui en cours
    val avail = (SHOP_THEMES.filter { it.fx.isNotEmpty() && ui.pg.owns("theme:${it.key}", it.price, u) }.map { it.fx } + listOfNotNull(s.fx.ifEmpty { null })).distinct()
    val season = Season.now()
    Glass {
        Head("sparkle", "Effets et saisons")
        SetRow("Couleurs de saison", "Halloween, fêtes (avec de la neige), Saint-Valentin, printemps et été colorent l’arrière-plan à leur période.${season?.let { " En ce moment : ${Season.LABELS[it]}." } ?: ""}") {
            PSwitch(ui.prefs.seasonal, { ui.prefs.seasonal = it; ui.themeTick++ })
        }
        Text(tr("Effet animé"), Modifier.padding(top = 8.dp, bottom = 6.dp), fontWeight = FontWeight.Bold)
        if (avail.isEmpty()) Muted("Neige, pétales, braises… arrivent avec les thèmes de la boutique.", small = true)
        else FlowRowCompat(gap = 6.dp) {
            FChip(tr("Aucun"), s.fx.isEmpty(), { s.fx = ""; commit(ui); ui.themeTick++ })
            avail.forEach { fx -> FChip(tr(FX_NAMES[fx] ?: fx), s.fx == fx, { s.fx = fx; commit(ui); ui.themeTick++ }) }
        }
        if (LocalPal.current.motion) Muted("Les effets animés sont coupés quand les animations sont réduites (Accessibilité).", Modifier.padding(top = 6.dp), small = true)
    }
}

/* ------------------------------------------------------------------ langue */

@Composable
private fun LanguageSection(ui: AppState) {
    val ctx = LocalContext.current
    var lang by remember { mutableStateOf(ui.prefs.raw.getString("lang", "auto") ?: "auto") }
    Glass {
        Head("globe", "Langue · Language", "« Auto » suit la langue du téléphone ; tes contenus restent tels que tu les as écrits.")
        SegFill(listOf("auto" to "Auto", "fr" to "Français", "en" to "English"), lang, { lang = it; ui.prefs.raw.edit().putString("lang", it).apply(); I18n.apply(ctx, it); ui.themeTick++; ui.bump() })
    }
}

/* ------------------------------------------------------------------ réglages avancés (repliés) */

@Composable
private fun AdvancedSection(ui: AppState, u: User) {
    val p = LocalPal.current; val s = u.settings
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 90f else 0f, label = "adv")
    val off = ui.prefs.transp || ui.prefs.contrast
    Glass(Modifier.animateContentSize()) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = if (open) "Replier" else "Déplier") { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Head("gear", "Réglages avancés", "Verre givré, carte « En cours »", Modifier.weight(1f))
            Pi("chevronleft", Modifier.rotate(180f + rot), size = 18.dp, tint = p.ink2)
        }
        if (open) {
            SetRow("Carte « En cours » sur l’accueil", "Deadlines, projets, objectifs chiffrés et défis en cours") {
                PSwitch(!s.hideOngoing, { s.hideOngoing = !it; commit(ui); ui.toast(if (it) "Carte « En cours » affichée sur l’accueil." else "Carte « En cours » masquée.", "list") })
            }
            Text(tr("Flou du verre givré"), Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
            Muted(if (off) "Sans effet : la transparence est désactivée dans Accessibilité." else "Intensité du flou de l’arrière-plan derrière les panneaux", Modifier.padding(bottom = 6.dp), small = true)
            SegFill(BLURS, s.glassBlur, { k -> s.glassBlur = k; commit(ui) }, enabled = !off)
            Text(tr("Opacité du verre givré"), Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold)
            Muted(if (off) "Sans effet : la transparence est désactivée dans Accessibilité." else "« Faible » laisse voir l’arrière-plan, « Fort » rend les panneaux presque opaques. Propre à cet appareil.", Modifier.padding(bottom = 6.dp), small = true)
            SegFill(listOf("low" to "Faible", "mid" to "Moyen", "high" to "Fort"), ui.prefs.glassOpacity, { ui.prefs.glassOpacity = it; ui.themeTick++ }, enabled = !off)
        }
    }
}
