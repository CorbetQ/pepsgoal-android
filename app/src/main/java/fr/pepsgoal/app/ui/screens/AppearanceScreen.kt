package fr.pepsgoal.app.ui.screens

import kotlin.math.roundToInt
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
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
private fun bgName(bg: String): String = fr.pepsgoal.core.ALL_BGS.firstOrNull { "s:${it.key}" == bg }?.name ?: BGS.firstOrNull { it.first == bg }?.second ?: "Collines braise"

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
            // Téléphone miniature à marche : la scène figée, son voile, un héros en plaque, une carte et deux lignes
            Box(Modifier.size(132.dp, 254.dp).chunk(p.bg, p.edge, 26.dp, 4.dp, 2.dp).clip(RoundedCornerShape(24.dp))) {
                MiniBackdrop(if (s.theme == "weather") "plain" else ui.sceneKind(), ui)
                val bg = p.bg
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to bg.copy(alpha = .06f), .5f to bg.copy(alpha = .38f), 1f to bg.copy(alpha = .55f))))
                Column(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Spacer(Modifier.height(30.dp))
                    Box(Modifier.background(p.surface, CircleShape).border(1.5.dp, p.edge, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        FitText(tr("Aujourd’hui"), size = 12, min = 7, color = p.ink, weight = FontWeight.ExtraBold, family = Unbounded)
                    }
                    Column(Modifier.fillMaxWidth().background(p.flameBrush(), RoundedCornerShape(12.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("12", Modifier.weight(1f), fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = fr.pepsgoal.app.ui.theme.PLUM)
                            Box(Modifier.size(24.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Flame(16.dp) }
                        }
                    }
                    repeat(2) { i ->
                        Row(Modifier.fillMaxWidth().background(p.surface, RoundedCornerShape(10.dp)).border(1.5.dp, p.edge, RoundedCornerShape(10.dp)).padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(12.dp).background(p.type(if (i == 0) "routine" else "objective").c, RoundedCornerShape(6.dp)))
                            Box(Modifier.weight(1f).height(4.dp).background(p.surface2, CircleShape))
                            Box(Modifier.size(10.dp).border(1.5.dp, p.control, CircleShape))
                        }
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
        Text(tr(k).uppercase(), fontSize = 12.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.ExtraBold, color = p.ink3)
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

/** Sélecteur segmenté pleine largeur (Seg Braise Pop : rail 56, segments 48, deux lignes). */
@Composable
private fun <T> SegFill(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit, enabled: Boolean = true) {
    Box(Modifier.fillMaxWidth().alpha(if (enabled) 1f else .45f)) { Seg(options, value, { if (enabled) onChange(it) }, Modifier.fillMaxWidth()) }
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

/** Tuile d'un carrousel : tuile à marche 78 × 104 (choisie = bord braise), étiquette en mini-plaque. */
@Suppress("UNUSED_PARAMETER")
@Composable
private fun Tile(label: String, selected: Boolean, w: Dp, h: Dp, onClick: () -> Unit, badge: String? = null, content: @Composable BoxScope.() -> Unit) {
    BgTile(label, null, selected, onClick) {
        content()
        if (badge != null) MiniPlaque(badge, Modifier.align(Alignment.TopEnd).padding(4.dp))
        if (selected) Box(Modifier.align(Alignment.BottomEnd).padding(5.dp).size(22.dp).background(vivid(LocalPal.current.type("routine").c), CircleShape), contentAlignment = Alignment.Center) { Pi("check", size = 14.dp, tint = fr.pepsgoal.app.ui.theme.PLUM) }
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
        Head("sun", "Thème", cur?.desc ?: THEME_DESC[s.theme] ?: "Apparence de toute l’interface") { Chip(themeName(s.theme), if (cur != null) "star" else null, p.flame) }
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
        if (s.theme == "system") SetRow("Suivre le soleil", "Clair du lever au coucher du soleil (à l’heure de ta ville météo), sombre la nuit, au lieu du réglage du téléphone.") {
            PSwitch(ui.prefs.sunTheme, { ui.prefs.sunTheme = it; ui.themeTick++ })
        }
        SetRow("Couleurs Material You", if (dynOk) "Les couleurs de ton fond d’écran Android sur toute l’interface." else "Nécessite Android 12 ou plus récent.") {
            PSwitch(ui.prefs.materialYou && dynOk, { ui.prefs.materialYou = it; ui.themeTick++; ui.toast(if (it) "Couleurs Material You activées." else "Couleurs PepsGoal rétablies.", "palette") }, enabled = dynOk)
        }
        if (ui.prefs.materialYou && dynOk) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(p.flame2, p.flame, p.pink, p.surface, p.bg).forEach { Box(Modifier.size(22.dp).clip(CircleShape).background(it).border(2.dp, p.edge, CircleShape)) }
            Muted(if (cur != null) "Propre à cet appareil · l’ambiance garde son fond et son texte" else "Propre à cet appareil", Modifier.padding(start = 4.dp), small = true)
        }
    }
}

/** Tuile « Ambiances » : aperçu en éventail de trois ambiances, et accès à la boutique (tuile à marche). */
@Composable
private fun ShopModesTile(ui: AppState, owned: Int, modifier: Modifier) {
    val p = LocalPal.current
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val press = rememberPress(inter)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(108.dp).chunk(p.surface2, p.edge, 22.dp, 4.dp, 2.dp, press).clip(RoundedCornerShape(20.dp))
            .springClick(inter, label = "Ambiances de la boutique") { ui.shopTab = "modes"; ui.push(Screen.Shop) }, contentAlignment = Alignment.Center) {
            listOf("m:sakura", "m:neon", "m:mint").forEachIndexed { i, k ->
                ThemePreview(themeLook(k), Modifier.size(38.dp, 62.dp).graphicsLayer { rotationZ = (i - 1) * 12f; translationX = (i - 1) * 16.dp.toPx() }.clip(RoundedCornerShape(6.dp)).border(2.dp, p.surface, RoundedCornerShape(6.dp)))
            }
            Badge(if (owned > 0) owned else SHOP_MODES.size, Modifier.align(Alignment.TopEnd).padding(5.dp))
        }
        FitText(tr("Ambiances"), Modifier.padding(top = 6.dp), size = 13, min = 9, color = p.ink2, weight = FontWeight.ExtraBold)
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
    Glass(Modifier.alpha(if (my) .45f else 1f)) {
        Head("palette", "Couleur", if (my) "Remplacée par Material You sur cet appareil" else "Flammes, boutons et anneau de progression") {
            Chip(tr(curName))
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
        PButton("Thèmes de la boutique", { ui.push(Screen.Shop) }, icon = "star", small = true, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Pastille de couleur : 44 dp à marche, choisie = bord braise et coche prune. */
@Composable
private fun Swatch(b: Brush, on: Boolean, name: String, onClick: () -> Unit) {
    val p = LocalPal.current
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val press = rememberPress(inter)
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(44.dp, 47.dp).chunk(b, if (on) p.focusEdge else p.edge, 22.dp, 3.dp, if (on) 3.dp else 2.dp, press).clip(CircleShape)
            .springClick(inter, label = name, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick).semantics { selected = on }, contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(24.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Pi("check", size = 16.dp, tint = fr.pepsgoal.app.ui.theme.PLUM) }
        }
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
    // Scènes possédées, fonds secrets des surprises compris
    val scenes = fr.pepsgoal.core.ALL_BGS.filter { ui.pg.ownsBg(it, u) }
    val more = SHOP_BGS.count { !ui.pg.ownsBg(it, u) }
    Glass {
        Head("image", "Arrière-plan", if (weather) "Le thème Météo affiche le ciel en direct : change de thème pour choisir un fond." else "${bgName(s.bg)} · ${when (ui.prefs.bgWhere) { "home" -> "en haut de l’accueil"; "off" -> "masqué"; else -> "sur tout l’écran" }}")
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
                        val b = fr.pepsgoal.core.ALL_BGS.first { "s:${it.key}" == k }
                        Tile(b.name, s.bg == k, 86.dp, 132.dp, { if (!weather) { ui.pg.applyBg(b); ui.themeTick++ } }) { SceneBg(b.key, Modifier.fillMaxSize(), still = true) }
                    }
                    k == "custom" -> Tile(if (has) "Mon image" else "Ma photo", s.bg == "custom" && has, 86.dp, 132.dp, { if (!weather) { if (has) { s.bg = "custom"; commit(ui) } else openPicker() } }) {
                        if (has) AsyncImage(ImageRequest.Builder(ctx).data(file).memoryCacheKey("bg${ui.prefs.bgStamp}").crossfade(true).build(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Box(Modifier.fillMaxSize().background(p.surface2), contentAlignment = Alignment.Center) { Pi("plus", size = 26.dp, tint = p.ink2) }
                    }
                    else -> Tile("Boutique", false, 86.dp, 132.dp, { ui.push(Screen.Shop) }, badge = if (more > 0) "+$more" else null) {
                        Box(Modifier.fillMaxSize().background(p.flameBrush()), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Emoji("🛍️", 28.dp); Text(tr("Fonds"), color = fr.pepsgoal.app.ui.theme.PLUM, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
        // Où voir ton fond, et sa présence (voile)
        Text(tr("Où voir ton fond"), Modifier.padding(top = 12.dp, start = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
        PRadioGroup(listOf("all" to "Partout", "home" to "Accueil seulement", "off" to "Nulle part"), ui.prefs.bgWhere, { ui.prefs.bgWhere = it; ui.themeTick++; ui.bump() },
            subs = listOf("Accueil, pages, derrière les feuilles", "Le haut de l’accueil", "Deux formes colorées immobiles"), emojis = listOf("🏞️", "🏠", "⚪"))
        var lvl by remember { mutableIntStateOf(ui.prefs.bgLevel) }
        val lvlName = listOf("Discrète", "Équilibrée", "Intense")
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Présence du fond"), Modifier.weight(1f).padding(start = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
            Text(tr(lvlName[lvl.coerceIn(0, 2)]), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
        }
        PSlider(lvl / 2f, { v -> lvl = (v * 2).roundToInt(); ui.prefs.bgLevel = lvl; ui.themeTick++ }, steps = 2, label = "Présence du fond", enabled = ui.prefs.bgWhere != "off")
        SliderEnds(tr("Discrète"), tr("Intense"))
        SetRow("Fond animé", "Figé en économie d’énergie") { PSwitch(ui.prefs.bgAnim, { ui.prefs.bgAnim = it; ui.themeTick++ }) }
        // Image personnalisée : changer, retirer, voile (plus aucun flou) et couleurs extraites
        if (has) {
            SetRow("Mon image", "Une photo à toi, gardée sur cet appareil.") {
                PButton("Changer", { openPicker() }, icon = "image", small = true)
                PButton("Retirer", { file.delete(); ui.prefs.bgPalette = emptyList(); ui.prefs.bgStamp = nowMs(); if (s.bg == "custom") s.bg = "pop"; commit(ui); ui.toast("Image d’arrière-plan retirée.", "image") }, kind = "ghost", small = true)
            }
            if (s.bg == "custom") {
                // Plus aucun flou : un voile dégradé réglable (Auto = celui de la présence du fond)
                var dim by remember { mutableFloatStateOf(ui.prefs.bgDim) }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Voile"), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = LocalPal.current.ink2)
                    Text(if (dim < 0f) "Auto" else "${(dim * 100).roundToInt()} %", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
                PSlider(if (dim < 0f) .55f else dim / .8f, { v -> dim = v * .8f; ui.prefs.bgDim = dim }, bubble = { v -> "${(v * 80).roundToInt()} %" }, label = "Voile de l’image", keepBubble = dim >= 0f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Plus de flou : un voile dégradé garde tes cartes lisibles.", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LocalPal.current.ink2, lineHeight = 17.sp)
                    if (dim >= 0f) PButton("Auto", { dim = -1f; ui.prefs.bgDim = -1f }, kind = "ghost", small = true)
                }
            }
            val pal = ui.prefs.bgPalette
            if (pal.isNotEmpty()) {
                Text(tr("Couleurs de ton image"), Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold)
                Muted("Touche-en une pour en faire ta couleur d’accent.", small = true)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pal.forEach { c ->
                        val on = validAccent(s.accentCustom) && Media.accentFrom(c)[1] == s.accentCustom!![1]
                        Box(Modifier.size(44.dp).clip(CircleShape).background(hex(c)).border(if (on) 3.dp else 2.dp, if (on) p.focusEdge else p.edge, CircleShape)
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
        Box(Modifier.fillMaxSize().background(p.bg)) { if (kind == "pop" || kind == "blob") PopBg(still = true) else Backdrop(kind, null, false, ui.prefs.bgStamp) }
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
                Box(Modifier.fillMaxSize().background(p.flameBrush()), contentAlignment = Alignment.Center) { Emoji("🛍️", 28.dp) }
            } else {
                val ic = AppIcons.ALL.first { it.key == k }
                Tile(ic.label, cur == k, 74.dp, 66.dp, { AppIcons.set(ctx, ic.key); cur = ic.key; ui.toast("Icône « ${ic.label} » activée.", "palette") }) {
                    Box(Modifier.fillMaxSize().background(p.surface2), contentAlignment = Alignment.Center) { AppIconPreview(ic, 54.dp, false) }
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
        Text(tr("Animations"), Modifier.padding(top = 4.dp), fontWeight = FontWeight.ExtraBold)
        Muted("Rebonds, flamme qui danse et confettis ; « Réduites » les remplace par des fondus.", Modifier.padding(bottom = 6.dp), small = true)
        Seg(listOf(false to "Complètes", true to "Réduites"), ui.prefs.motion, { ui.prefs.motion = it; ui.themeTick++ }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        val sc = seasonalScene()
        val base = "Sapin de Noël, cœurs de la Saint-Valentin, été sur la Loire : la scène s’affiche d’office à sa période, gratuitement."
        SetRow("Fond de saison animé", if (sc == null) base else "$base En ce moment : ${sc.name}.") {
            PSwitch(ui.prefs.bgSeasonAuto, { ui.prefs.bgSeasonAuto = it; ui.themeTick++ })
        }
        SetRow("Couleurs de saison", "Halloween, fêtes (avec de la neige), Saint-Valentin, printemps et été colorent l’arrière-plan à leur période.${season?.let { " En ce moment : ${Season.LABELS[it]}." } ?: ""}") {
            PSwitch(ui.prefs.seasonal, { ui.prefs.seasonal = it; ui.themeTick++ })
        }
        PSelectMenu("Lumière de la scène", listOf("real" to "Suit l’heure réelle", "day" to "Toujours le jour", "theme" to "Suit le thème"), ui.prefs.bgLight, { ui.prefs.bgLight = it; ui.themeTick++ },
            emojis = listOf("🕰️", "☀️", "🌗"), subs = listOf("Aube, jour, crépuscule, nuit", null, "Nuit en thème sombre"))
        SetRow("Fond qui réagit", "La marée de braise monte avec tes validations, et des confettis tombent les journées parfaites.") {
            PSwitch(ui.prefs.bgReactive, { ui.prefs.bgReactive = it; ui.themeTick++ })
        }
        SetRow("Fond à la météo", if (ui.pg.me()?.settings?.weather == null) "Choisis ta ville dans Paramètres › Météo : il pleuvra sur ton fond quand il pleut dehors." else "Pluie, neige, orage ou brouillard sur ton fond quand il fait ce temps-là dehors.") {
            PSwitch(ui.prefs.bgWeather, { ui.prefs.bgWeather = it; ui.themeTick++ })
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
        PSelectMenu(null, listOf("auto" to "Auto", "fr" to "Français", "en" to "English"), lang, { lang = it; ui.prefs.raw.edit().putString("lang", it).apply(); I18n.apply(ctx, it); ui.themeTick++; ui.bump() },
            emojis = listOf("📱", "🇫🇷", "🇬🇧"), subs = listOf("Langue du téléphone", null, null))
    }
}

/* ------------------------------------------------------------------ réglages avancés (repliés) */

@Composable
private fun AdvancedSection(ui: AppState, u: User) {
    val p = LocalPal.current; val s = u.settings
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 90f else 0f, label = "adv")
    Glass(Modifier.animateContentSize()) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable(onClickLabel = if (open) "Replier" else "Déplier") { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Head("gear", "Réglages avancés", "Carte « En cours »", Modifier.weight(1f))
            Pi("chevronleft", Modifier.rotate(180f + rot), size = 18.dp, tint = p.ink2)
        }
        if (open) {
            SetRow("Carte « En cours » sur l’accueil", "Deadlines, projets, objectifs chiffrés et défis en cours") {
                PSwitch(!s.hideOngoing, { s.hideOngoing = !it; commit(ui); ui.toast(if (it) "Carte « En cours » affichée sur l’accueil." else "Carte « En cours » masquée.", "list") })
            }
        }
    }
}
