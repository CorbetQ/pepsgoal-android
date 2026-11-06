package fr.pepsgoal.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.sys.AppIcons
import fr.pepsgoal.app.sys.Packs
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Pal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.app.ui.theme.readable
import fr.pepsgoal.app.ui.theme.step
import fr.pepsgoal.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/*
 * Éditeur de packs de thème personnels (9.2) : identité, clair ou sombre, couleurs (fond, encre, surfaces, flamme,
 * décor), fond d'écran, icône et effet, aperçu en direct, contrôle des contrastes et partage en JSON.
 */

private val PACK_EMOJIS = listOf("🎨", "🔥", "🌙", "🌊", "🌸", "🌿", "⚡", "🍂", "❄️", "🌈", "🍓", "🏝️", "🌌", "☕", "🦊", "💎")
private val FX_LABEL = mapOf("snow" to "Neige", "petals" to "Pétales", "leaves" to "Feuilles", "hearts" to "Cœurs", "embers" to "Braises",
    "bubbles" to "Bulles", "confetti" to "Confettis", "stars" to "Étoiles")


/** Palette Braise Pop du pack, pour ses aperçus (figés : aucune animation dans une liste ou un éditeur). */
@Composable
internal fun rememberPackPal(spec: ThemePackSpec): Pal {
    val p = LocalPal.current
    return remember(spec.colors, spec.dark, p) {
        val c = spec.colors
        val bg = hex(c.bg); val surface = hex(c.surface); val ink = hex(c.ink); val ac = c.accent.map(::hex)
        val deco = c.deco.take(2).map { lerp(bg, hex(it), if (spec.dark) .35f else .3f) }
        p.copy(dark = spec.dark, oled = false, bg = bg, ink = ink, ink2 = lerp(ink, surface, .25f), ink3 = lerp(ink, surface, .38f), line = hex(c.edge),
            flame2 = ac[0], flame = ac[1], pink = ac[2], onAccent = hex(PackContrast.onAccent(c.accent)), flameInk = readable(ac[1], surface, 4.6f),
            blobs = c.deco.map(::hex) + ac[1], surface = surface, surface2 = hex(c.surface2), edge = hex(c.edge), control = lerp(hex(c.edge), ink, .45f),
            flameStep = ac[0].step(), deco = if (deco.size < 2) deco + deco else deco, motion = true, contrast = false, materialYou = false)
    }
}

/** Fond d'écran du pack, figé. [image] : fichier de l'image (pack personnel ou contenu téléchargé). */
@Composable
internal fun PackWallpaperView(spec: ThemePackSpec, image: File?, stamp: Long = 0L) {
    val p = LocalPal.current; val ctx = LocalContext.current
    val w = spec.wallpaper
    Box(Modifier.fillMaxSize().background(p.bg)) {
        when (w.kind) {
            "scene" -> SceneBg(w.key ?: "nebula", Modifier.fillMaxSize(), still = true)
            "gradient" -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(w.colors.orEmpty().map(::hex).ifEmpty { listOf(p.bg, p.bg) })))
            "image" -> if (image != null && image.exists()) AsyncImage(ImageRequest.Builder(ctx).data(image).memoryCacheKey("pack:${image.path}:$stamp").build(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(hex(spec.colors.deco.first()), p.bg))))
            else -> { val k = w.key; if (k == null || k == "pop") PopBg(still = true) else Backdrop(k, null, false, 0L) }
        }
    }
}

/**
 * Mini-écran du pack : fond d'écran et voile, titre en plaque, carte, ligne d'objectif, bouton de la flamme et barre
 * d'onglets, aux couleurs du pack (et non à celles de l'interface en cours).
 */
@Composable
fun PackPreview(spec: ThemePackSpec, image: File?, modifier: Modifier = Modifier, stamp: Long = 0L, compact: Boolean = false) {
    val pp = rememberPackPal(spec)
    CompositionLocalProvider(LocalPal provides pp) {
        val p = LocalPal.current
        val r = if (compact) 16.dp else 26.dp
        Box(modifier.chunk(p.bg, p.edge, r, if (compact) 3.dp else 4.dp, 2.dp).clip(RoundedCornerShape(r - 2.dp))
            .semantics { contentDescription = tr("Aperçu du pack") + " " + spec.name }) {
            PackWallpaperView(spec, image, stamp)
            val bg = p.bg
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to bg.copy(alpha = .05f), .5f to bg.copy(alpha = .35f), 1f to bg.copy(alpha = .6f))))
            if (compact) {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)) {
                    Box(Modifier.fillMaxWidth().height(26.dp).background(p.surface, RoundedCornerShape(9.dp)).border(1.5.dp, p.edge, RoundedCornerShape(9.dp)))
                    Box(Modifier.fillMaxWidth().height(20.dp).background(Brush.linearGradient(listOf(p.flame2, p.flame, p.pink)), CircleShape))
                }
                return@Box
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.background(p.surface, CircleShape).border(1.5.dp, p.edge, CircleShape).padding(horizontal = 9.dp, vertical = 3.dp)) {
                    Text("Aujourd’hui", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = p.ink, maxLines = 1)
                }
                // Carte
                Column(Modifier.fillMaxWidth().chunk(p.surface, p.edge, 16.dp, 3.dp, 1.5.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Ma journée", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = p.ink, maxLines = 1)
                    Text("3 objectifs sur 5", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = p.ink2, maxLines = 1)
                    Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(8.dp).background(p.surface2, CircleShape)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(.6f).background(Brush.linearGradient(listOf(p.flame2, p.flame, p.pink)), CircleShape))
                    }
                }
                // Ligne d'objectif
                Row(Modifier.fillMaxWidth().chunk(p.surface, p.edge, 14.dp, 3.dp, 1.5.dp).padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(22.dp).background(p.type("routine").c, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) { Emoji("📖", 14.dp) }
                    Text("Lire 20 pages", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = p.ink, maxLines = 1)
                    Box(Modifier.size(18.dp).border(2.dp, p.control, CircleShape))
                }
                // Bouton de la flamme
                Box(Modifier.fillMaxWidth().chunk(Brush.linearGradient(listOf(p.flame2, p.flame, p.pink)), p.flameStep, 18.dp, 3.dp, 0.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text("Valider", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = p.onAccent)
                }
                Spacer(Modifier.weight(1f))
                // Barre d'onglets
                Row(Modifier.fillMaxWidth().chunk(p.surface, p.edge, 18.dp, 3.dp, 1.5.dp).padding(5.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    listOf("sun", "target", "feed", "user").forEachIndexed { i, ic ->
                        if (i == 0) Box(Modifier.background(Brush.linearGradient(listOf(p.flame2, p.flame, p.pink)), CircleShape).padding(horizontal = 9.dp, vertical = 4.dp)) { Pi(ic, size = 16.dp, tint = p.onAccent) }
                        else Box(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) { Pi(ic, size = 16.dp, tint = p.ink2) }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ écran */

/** Couleur d'une cible de l'éditeur. */
private fun ThemePackSpec.colorOf(t: String): String = when (t) {
    "bg" -> colors.bg; "ink" -> colors.ink; "surface" -> colors.surface; "surface2" -> colors.surface2; "edge" -> colors.edge
    "a0" -> colors.accent[0]; "a1" -> colors.accent[1]; "a2" -> colors.accent[2]
    "d0" -> colors.deco[0]; "d1" -> colors.deco.getOrElse(1) { colors.deco[0] }
    "g0" -> wallpaper.colors?.getOrNull(0) ?: colors.deco[0]; "g1" -> wallpaper.colors?.getOrNull(1) ?: colors.bg
    else -> colors.bg
}

private fun ThemePackSpec.withColor(t: String, v: String): ThemePackSpec = when (t) {
    "bg" -> copy(colors = colors.copy(bg = v)); "ink" -> copy(colors = colors.copy(ink = v)); "surface" -> copy(colors = colors.copy(surface = v))
    "surface2" -> copy(colors = colors.copy(surface2 = v)); "edge" -> copy(colors = colors.copy(edge = v))
    "a0", "a1", "a2" -> copy(colors = colors.copy(accent = colors.accent.toMutableList().also { it[t[1].digitToInt()] = v }))
    "d0" -> copy(colors = colors.copy(deco = listOf(v) + colors.deco.drop(1)))
    "d1" -> copy(colors = colors.copy(deco = listOf(colors.deco[0], v) + colors.deco.drop(2)))
    "g0", "g1" -> copy(wallpaper = wallpaper.copy(colors = listOf(colorOf("g0"), colorOf("g1")).toMutableList().also { it[t[1].digitToInt()] = v }))
    else -> this
}

/** Neutres de départ du mode choisi (fond, encre, surfaces, décor) ; la flamme du pack est gardée. */
private fun ThemePackSpec.withDark(dark: Boolean): ThemePackSpec {
    if (dark == this.dark) return this
    val base = starterPack(dark, id = id, now = updatedAt)
    return copy(dark = dark, colors = base.colors.copy(accent = colors.accent))
}

private fun copyText(ctx: Context, text: String) =
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("PepsGoal", text))

@Composable
fun PackEditorScreen(ui: AppState, id: String?) {
    ui.watch()
    val p = LocalPal.current; val ctx = LocalContext.current; val pg = ui.pg; val u = pg.me() ?: return
    val existing = remember(id) { id?.let { PackLibrary.personal.firstOrNull { s -> s.id == it } } }
    var spec by remember(id) { mutableStateOf(existing ?: starterPack(p.dark)) }
    var saved by remember(id) { mutableStateOf(existing != null) }
    var dirty by remember(id) { mutableStateOf(false) }
    var stamp by remember { mutableLongStateOf(nowMs()) }
    fun edit(s: ThemePackSpec) { spec = s; dirty = true }
    val image = Packs.imageFile(ctx, spec.id)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ui.scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val b = Media.decode(ctx, uri, 1920) ?: return@withContext false
                try { Packs.imageFile(ctx, spec.id).writeBytes(Media.jpeg(b, 82)); true } catch (e: Exception) { false }
            }
            if (!ok) { ui.toast("Impossible de lire cette image. Essaie un fichier JPG ou PNG.", "image"); return@launch }
            fr.pepsgoal.app.sys.Creator.photoChanged(ctx, spec.id)
            stamp = nowMs(); edit(spec.copy(wallpaper = PackWallpaper("image", file = "${spec.id}.jpg")))
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = PackFormat.encode(PackFormat.exportable(spec))
        ui.scope.launch {
            val ok = withContext(Dispatchers.IO) { try { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null } catch (e: Exception) { false } }
            ui.toast(if (ok) "Pack exporté." else "Export impossible dans ce dossier.", if (ok) "check" else "x")
        }
    }
    val checks = PackContrast.checks(spec)
    val readableOk = checks.all { it.ok }
    fun doSave(apply: Boolean) {
        val s = if (PackContrast.allOk(spec)) spec else PackContrast.fix(spec)
        val err = Packs.save(ui, s)
        if (err != null) { ui.toast(err, "x"); return }
        spec = PackLibrary.personal.first { it.id == s.id }; saved = true; dirty = false
        if (apply) ui.scope.launch { ui.toast(Packs.apply(ui, spec), "palette") } else ui.toast("Pack « ${spec.name} » enregistré.", "check")
    }
    fun save(apply: Boolean) {
        if (spec.name.isBlank()) { ui.toast("Donne un nom au pack.", "edit"); return }
        if (!readableOk) ui.confirm("Texte peu lisible", "Certains textes n’atteignent pas le contraste de 4,5:1. La correction proposée ajuste l’encre, les surfaces ou la flamme, en gardant leurs teintes.",
            "Corriger et enregistrer", danger = false) { doSave(apply) }
        else doSave(apply)
    }

    val blocker = fr.pepsgoal.app.sys.Creator.blocker(ui)
    ScreenFrame(ui, if (!saved) "Créer mon pack" else "Modifier le pack", "Retour", { ui.back() }, icon = "palette", emoji = "🎨") {
        // Tous les calculs (photo, PepsGoal Style, publication) sont faits par le serveur : connexion obligatoire
        if (blocker != null) {
            item("offline") {
                Glass {
                    CardHead("Connexion nécessaire", emoji = "📡")
                    Muted(blocker, small = true)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PButton("Réessayer", { ui.scope.launch { if (ui.pg.store.acct) ui.cloud.syncNow(force = true) }; ui.bump() }, Modifier.weight(1f), icon = "repeat")
                        PButton("Retour", { ui.back() }, Modifier.weight(1f), kind = "ghost")
                    }
                }
            }
            item("help") { Hint("Tes packs déjà créés restent applicables hors ligne depuis Apparence › Mes packs.") }
            return@ScreenFrame
        }
        item("preview") {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PackPreview(spec, image, Modifier.size(170.dp, 320.dp), stamp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Aperçu en direct", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Muted("Chaque changement s’affiche ici, aux couleurs du pack.", small = true)
                        Chip(if (readableOk) "Lisible" else "Contraste faible", if (readableOk) "check" else "eye", if (readableOk) p.type("routine").c else p.type("challenge").c)
                        if (dirty) Chip("Non enregistré", "edit")
                    }
                }
            }
        }
        item("identity") {
            Glass {
                CardHead("Identité", emoji = spec.emoji)
                Field(spec.name, { edit(spec.copy(name = it.take(PACK_NAME_MAX))) }, "Nom du pack", Modifier.fillMaxWidth(), max = PACK_NAME_MAX, counter = true)
                Text(tr("Emoji"), Modifier.padding(top = 10.dp, bottom = 6.dp), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                FlowRowCompat(gap = 6.dp) {
                    PACK_EMOJIS.forEach { e -> PickTile(spec.emoji == e, "Emoji $e", { edit(spec.copy(emoji = e)) }) { Emoji(e, 26.dp) } }
                }
                Text(tr("Thème"), Modifier.padding(top = 12.dp, bottom = 6.dp), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Seg(listOf(false to "Clair", true to "Sombre"), spec.dark, { edit(spec.withDark(it)) }, Modifier.fillMaxWidth())
                Muted("Changer de thème reprend les neutres Braise Pop clairs ou sombres ; ta flamme est gardée.", Modifier.padding(top = 6.dp), small = true)
            }
        }
        item("colors") { PackColorsCard(spec) { edit(it) } }
        item("contrast") { ContrastCard(spec, checks) { edit(it); ui.toast("Contrastes corrigés.", "check") } }
        item("wallpaper") { WallpaperCard(ui, spec, image, stamp, { edit(it) }, { stamp = nowMs() }) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
        item("extras") { ExtrasCard(ui, spec) { edit(it) } }
        item("actions") {
            Glass {
                CardHead("Enregistrer et partager", emoji = "💾")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PButton("Enregistrer et appliquer", { save(true) }, Modifier.fillMaxWidth(), icon = "palette", kind = "primary")
                    PButton("Enregistrer", { save(false) }, Modifier.fillMaxWidth(), icon = "check")
                    // Boutique des créateurs : 100 % du prix revient au créateur
                    PButton(if (fr.pepsgoal.app.sys.Creator.publishedOf(ctx, spec.id) != null) "Mettre à jour dans la boutique" else "Vendre dans la boutique", {
                        if (spec.name.isBlank()) { ui.toast("Donne un nom au pack.", "edit"); return@PButton }
                        if (dirty || !saved) { val s2 = if (PackContrast.allOk(spec)) spec else PackContrast.fix(spec); val err = Packs.save(ui, s2)
                            if (err != null) { ui.toast(err, "x"); return@PButton }; spec = PackLibrary.personal.first { it.id == s2.id }; saved = true; dirty = false }
                        ui.push(Screen.PublishPack(spec.id))
                    }, Modifier.fillMaxWidth(), icon = "star", kind = "ghost")
                    if (saved) {
                        PButton("Dupliquer", {
                            val c = Packs.duplicate(ui, spec)
                            if (c == null) ui.toast("Duplication impossible.", "x") else { ui.toast("Copie « ${c.name} » créée.", "copy"); ui.back(); ui.push(Screen.PackEditor(c.id)) }
                        }, Modifier.fillMaxWidth(), icon = "copy")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PButton("Copier le JSON", { copyText(ctx, PackFormat.encode(PackFormat.exportable(spec))); ui.toast("Pack copié : colle-le dans un message pour le partager.", "copy") }, Modifier.weight(1f), icon = "share")
                            PButton("Fichier", { exportFile.launch("${spec.name.ifBlank { "pack" }.replace(Regex("[^\\p{L}\\p{N} _-]"), "")}.pepsgoal-pack.json") }, Modifier.weight(1f), icon = "archive")
                        }
                        if (spec.wallpaper.kind == "image") Muted("Le fichier JSON ne contient pas la photo : il la remplace par un dégradé de ses couleurs. Pour partager la photo, vends ou offre le pack dans la boutique.", small = true)
                        PButton("Supprimer", {
                            ui.confirm("Supprimer « ${spec.name} » ?", "Le pack disparaît de cet appareil. S’il est appliqué, l’interface revient au thème clair ou sombre.", "Supprimer") {
                                Packs.delete(ui, spec.id); ui.back(); ui.toast("Pack supprimé.", "trash")
                            }
                        }, Modifier.fillMaxWidth(), icon = "trash", kind = "danger")
                    }
                }
            }
        }
        item("help") { Hint("Les packs personnels restent sur cet appareil. Exporte-les en JSON, ou publie-les dans la boutique : chaque achat te revient à 100 %. Une scène, une icône ou un effet ne s’appliquent que si tu les possèdes.") }
    }
}

/** Couleurs : cibles (fond, encre…), nuancier, réglages teinte / saturation / luminosité et code hexadécimal. */
@Composable
private fun PackColorsCard(spec: ThemePackSpec, onChange: (ThemePackSpec) -> Unit) {
    val p = LocalPal.current
    var target by remember { mutableStateOf("bg") }
    val targets = listOf("bg" to "Fond", "ink" to "Encre", "surface" to "Cartes", "surface2" to "Creux", "edge" to "Bords",
        "a0" to "Flamme 1", "a1" to "Flamme 2", "a2" to "Flamme 3", "d0" to "Décor 1", "d1" to "Décor 2") +
        (if (spec.wallpaper.kind == "gradient") listOf("g0" to "Dégradé haut", "g1" to "Dégradé bas") else emptyList())
    val tgt = target.takeIf { k -> targets.any { it.first == k } } ?: "bg"
    val cur = spec.colorOf(tgt)
    var hsl by remember(tgt) { mutableStateOf(PackColor.toHsl(cur)) }
    var hexText by remember(tgt, cur) { mutableStateOf(cur) }
    fun set(v: String, fromSliders: Boolean = false) {
        if (!fromSliders) hsl = PackColor.toHsl(v)
        onChange(spec.withColor(tgt, v.uppercase()))
    }
    Glass {
        CardHead("Couleurs", emoji = "🖌️")
        Muted("Choisis un élément, puis sa couleur.", small = true)
        FlowRowCompat(Modifier.padding(top = 8.dp), gap = 6.dp) {
            targets.forEach { (k, label) ->
                FChip(tr(label), tgt == k, { target = k }, leading = {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(hex(spec.colorOf(k))).border(1.5.dp, p.edge, CircleShape))
                })
            }
        }
        Text(tr("Nuancier"), Modifier.padding(top = 12.dp, bottom = 4.dp), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        FlowRowCompat(gap = 2.dp) {
            PACK_PALETTE.forEach { c -> PSwatch(androidx.compose.ui.graphics.SolidColor(hex(c)), cur.equals(c, true), "Couleur $c", { set(c) }, size = 40.dp) }
        }
        val labels = listOf("Teinte", "Saturation", "Luminosité")
        labels.forEachIndexed { i, label ->
            val v = when (i) { 0 -> hsl[0] / 360.0; 1 -> hsl[1]; else -> hsl[2] }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr(label), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
                Text(if (i == 0) "${hsl[0].roundToInt()}°" else "${(v * 100).roundToInt()} %", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            PSlider(v.toFloat(), { f ->
                val n = hsl.copyOf(); if (i == 0) n[0] = f * 360.0 else n[i] = f.toDouble()
                hsl = n; set(PackColor.fromHsl(n[0], n[1], n[2]), fromSliders = true)
            }, label = label)
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(hex(cur)).border(2.dp, p.edge, RoundedCornerShape(14.dp)))
            Field(hexText, { t ->
                val v = (if (t.startsWith("#")) t else "#$t").take(7).uppercase(); hexText = v
                if (PackFormat.isHex(v)) set(v)
            }, "Code couleur", Modifier.weight(1f), placeholder = "#FF4A12", max = 7,
                error = if (PackFormat.isHex(hexText)) null else "Format « #RRGGBB »")
        }
    }
}

/** Lisibilité : les quatre contrôles, et la correction proposée (avant / après) quand l'un échoue. */
@Composable
private fun ContrastCard(spec: ThemePackSpec, checks: List<ContrastCheck>, onFix: (ThemePackSpec) -> Unit) {
    val p = LocalPal.current
    Glass {
        CardHead("Lisibilité", emoji = "👓")
        Muted("Le texte doit atteindre un contraste d’au moins 4,5:1 (norme WCAG AA).", small = true)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            checks.forEach { c ->
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(c.label), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(fmtContrast(c.ratio), Modifier.padding(end = 8.dp), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Chip(if (c.ok) "OK" else "Trop faible", if (c.ok) "check" else "x", if (c.ok) p.type("routine").c else p.type("duel").c)
                }
            }
        }
        if (checks.any { !it.ok }) {
            val fixed = remember(spec.colors) { PackContrast.fix(spec) }
            val changes = listOf("Fond" to (spec.colors.bg to fixed.colors.bg), "Encre" to (spec.colors.ink to fixed.colors.ink),
                "Cartes" to (spec.colors.surface to fixed.colors.surface), "Creux" to (spec.colors.surface2 to fixed.colors.surface2)) +
                spec.colors.accent.indices.map { "Flamme ${it + 1}" to (spec.colors.accent[it] to fixed.colors.accent[it]) }
            Text(tr("Correction proposée"), Modifier.padding(top = 12.dp, bottom = 6.dp), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            changes.filter { it.second.first != it.second.second }.forEach { (label, ch) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(label), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = p.ink2)
                    Box(Modifier.size(24.dp).clip(CircleShape).background(hex(ch.first)).border(1.5.dp, p.edge, CircleShape))
                    Pi("chevronright", size = 16.dp, tint = p.ink3)
                    Box(Modifier.size(24.dp).clip(CircleShape).background(hex(ch.second)).border(1.5.dp, p.edge, CircleShape))
                    Text(ch.second, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }
            PButton("Appliquer la correction", { onFix(fixed) }, Modifier.fillMaxWidth().padding(top = 8.dp), icon = "wand", kind = "primary")
        }
    }
}

/** Onglets du fond : « PepsGoal Style » est une photo calculée par le serveur (stylisée, ciel découpé). */
private val WALL_TABS = listOf("free" to "Gratuit", "scene" to "Scène", "gradient" to "Dégradé", "image" to "Photo", "style" to "PepsGoal Style")
private val WALL_EMOJI = mapOf("free" to "🎈", "scene" to "🏞️", "gradient" to "🌈", "image" to "📷", "style" to "✨")

private fun PackWallpaper.tab(): String = if (kind == "image" && (styled == true || foreground != null)) "style" else kind

/** Fond d'écran du pack : fond gratuit, scène possédée, dégradé, photo ou PepsGoal Style. */
@Composable
private fun WallpaperCard(ui: AppState, spec: ThemePackSpec, image: File, stamp: Long, onChange: (ThemePackSpec) -> Unit, onStamp: () -> Unit, onPick: () -> Unit) {
    val pg = ui.pg; val u = pg.me()
    val w = spec.wallpaper
    val pp = rememberPackPal(spec)
    var tab by remember(spec.id) { mutableStateOf(w.tab()) }
    Glass {
        CardHead("Fond d’écran", emoji = "🏞️")
        PTabs(WALL_TABS.map { (k, l) -> l to WALL_EMOJI[k] }, WALL_TABS.indexOfFirst { it.first == tab }.coerceAtLeast(0), { i ->
            val k = WALL_TABS[i].first
            tab = k
            when (k) {
                "scene" -> pg.ownedScenes(u).firstOrNull()?.key?.let { onChange(spec.copy(wallpaper = PackWallpaper("scene", it))) }
                "gradient" -> onChange(spec.copy(wallpaper = PackWallpaper("gradient", colors = listOf(spec.colors.deco.first(), spec.colors.bg))))
                "image" -> if (image.exists()) onChange(spec.copy(wallpaper = PackWallpaper("image", file = image.name))) else onPick()
                "style" -> {}
                else -> onChange(spec.copy(wallpaper = PackWallpaper("free", "pop")))
            }
        }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        if (tab == "style") { StylePanel(ui, spec, image, stamp, onChange, onStamp); return@Glass }
        when (w.kind) {
            "free" -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(PACK_FREE_BGS) { k ->
                    BgTile(BGS.firstOrNull { it.first == k }?.second ?: k, null, w.key == k, { onChange(spec.copy(wallpaper = PackWallpaper("free", k))) }) {
                        CompositionLocalProvider(LocalPal provides pp) { PackWallpaperView(spec.copy(wallpaper = PackWallpaper("free", k)), null) }
                    }
                }
            }
            "scene" -> {
                val scenes = pg.ownedScenes(u)
                if (scenes.isEmpty()) Muted("Tu n’as pas encore de scène illustrée : découvre-les dans la boutique.", small = true)
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(scenes, key = { it.key }) { b -> BgTile(b.name, null, w.key == b.key, { onChange(spec.copy(wallpaper = PackWallpaper("scene", b.key))) }) { SceneBg(b.key, Modifier.fillMaxSize(), still = true) } }
                }
            }
            "gradient" -> Muted("Règle ses deux couleurs dans « Couleurs » : « Dégradé haut » et « Dégradé bas ».", small = true)
            "image" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(78.dp, 104.dp).clip(RoundedCornerShape(14.dp))) { CompositionLocalProvider(LocalPal provides pp) { PackWallpaperView(spec, image, stamp) } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Muted("Une photo à toi, gardée sur cet appareil.", small = true)
                    PButton("Changer de photo", onPick, icon = "image", small = true)
                }
            }
        }
    }
}

/** Icône de l'application et effet animé, parmi ceux que tu possèdes. */
@Composable
private fun ExtrasCard(ui: AppState, spec: ThemePackSpec, onChange: (ThemePackSpec) -> Unit) {
    val pg = ui.pg; val u = pg.me()
    val icons = AppIcons.ALL.filter { pg.owns("icon:${it.key}", it.price, u) }
    val fx = pg.ownedFx(u).toList()
    Glass {
        CardHead("Icône et effet", emoji = "✨")
        Muted("Facultatifs : sans choix, ton icône et tes effets actuels sont gardés.", small = true)
        Text(tr("Icône de l’application"), Modifier.padding(top = 10.dp, bottom = 6.dp), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item("none") { PickTile(spec.icon == null, "Garder mon icône", { onChange(spec.copy(icon = null)) }, size = 60.dp) { Pi("x", size = 22.dp, tint = LocalPal.current.ink2) } }
            items(icons, key = { it.key }) { ic -> PickTile(spec.icon == ic.key, ic.label, { onChange(spec.copy(icon = ic.key)) }, size = 60.dp) { AppIconPreview(ic, 46.dp, false) } }
        }
        Text(tr("Effet animé"), Modifier.padding(top = 12.dp, bottom = 6.dp), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        FlowRowCompat(gap = 6.dp) {
            FChip(tr("Aucun"), spec.fx.isNullOrEmpty(), { onChange(spec.copy(fx = null)) })
            fx.forEach { k -> FChip(tr(FX_LABEL[k] ?: k), spec.fx == k, { onChange(spec.copy(fx = k)) }) }
        }
        if (fx.isEmpty()) Muted("Neige, pétales, braises… arrivent avec les thèmes de la boutique.", Modifier.padding(top = 6.dp), small = true)
    }
}

/* ------------------------------------------------------------------ bibliothèque « Mes packs » */

/**
 * « Mes packs » (Apparence et boutique) : packs personnels, avec Appliquer et Modifier, création et import (texte
 * collé ou fichier JSON). [extra] : packs téléchargés obtenus (lot « contenus en ligne »).
 */
@Composable
fun MyPacksCard(ui: AppState, extra: List<Pair<ThemePackSpec, File?>> = emptyList()) {
    val p = LocalPal.current; val ctx = LocalContext.current
    ui.themeTick
    val mine = remember(ui.rev, ui.themeTick) { Packs.all() }
    var importing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    fun doImport(t: String) {
        val (spec, e) = Packs.import(ui, t)
        if (spec != null) { importing = false; text = ""; err = null; ui.toast("Pack « ${spec.name} » importé.", "check") } else err = e
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ui.scope.launch {
            val t = withContext(Dispatchers.IO) {
                try { ctx.contentResolver.openInputStream(uri)?.use { s -> s.readNBytesCompat(PACK_JSON_MAX + 1) }?.toString(Charsets.UTF_8) } catch (e: Exception) { null }
            }
            if (t == null) err = "Lecture du fichier impossible." else doImport(t)
        }
    }
    Glass {
        CardHead("Mes packs", emoji = "🎨") { if (mine.size + extra.size > 0) Count(mine.size + extra.size) }
        Muted("Tes propres packs : couleurs, fond, icône et effet, à créer, partager ou importer.", small = true)
        val all = mine.map { it to Packs.imageFile(ctx, it.id) as File? } + extra
        if (all.isNotEmpty()) LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(all, key = { it.first.id }) { (spec, img) ->
                val active = ui.pg.packSpecActive(spec)
                val personal = mine.any { it.id == spec.id }
                Column(Modifier.width(132.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PackPreview(spec, img, Modifier.fillMaxWidth().height(150.dp), compact = true)
                    Text("${spec.emoji} ${spec.name}", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
                    if (!personal) Muted("En ligne", small = true)
                    PButton(if (active) "Actif" else "Appliquer", { ui.scope.launch { ui.toast(Packs.apply(ui, spec, img, if (personal) null else fr.pepsgoal.app.sys.ContentSync.foregroundFor(ctx, spec.id)), "palette") } }, Modifier.fillMaxWidth(),
                        icon = if (active) "check" else "palette", small = true, kind = if (active) "ghost" else "normal", enabled = !active)
                    if (personal) PButton("Modifier", { ui.push(Screen.PackEditor(spec.id)) }, Modifier.fillMaxWidth(), icon = "edit", small = true, kind = "ghost")
                }
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PButton("Créer mon pack", { ui.push(Screen.PackEditor()) }, Modifier.weight(1f), icon = "plus", kind = "primary")
            PButton("Importer", { importing = !importing; err = null }, Modifier.weight(1f), icon = "archive")
        }
        PButton("Mes créations en boutique", { ui.push(Screen.Creator) }, Modifier.fillMaxWidth().padding(top = 10.dp), icon = "star", kind = "ghost")
        if (importing) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(text, { text = it; err = null }, "Pack au format JSON", Modifier.fillMaxWidth(), placeholder = "{\"schemaVersion\": 1, …}", singleLine = false, minLines = 4,
                    max = PACK_JSON_MAX, error = err)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PButton("Importer le texte", { doImport(text) }, Modifier.weight(1f), icon = "check", enabled = text.isNotBlank())
                    PButton("Depuis un fichier", { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, Modifier.weight(1f), icon = "folder")
                }
                Muted("Un pack importé devient une copie à toi ; un pack d’une version plus récente de PepsGoal est refusé.", small = true)
            }
        }
    }
}

/** Lecture bornée (InputStream.readNBytes n'existe qu'à partir d'Android 13). */
private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192)
    while (out.size() < max) { val n = read(buf, 0, minOf(buf.size, max - out.size())); if (n < 0) break; out.write(buf, 0, n) }
    return out.toByteArray()
}
