package fr.pepsgoal.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.sys.Creator
import fr.pepsgoal.app.sys.Packs
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.*
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/*
 * Boutique des créateurs et « PepsGoal Style » (10.0) : onglet PepsGoal Style de l'éditeur, publication d'un pack
 * (prix en flammes, 100 % au créateur), « Mes créations » (statut, ventes, gains) et file de la modération.
 * Tous les calculs d'image sont faits par le serveur.
 */

/**
 * Onglet « PepsGoal Style » du fond : une photo envoyée au serveur, stylisée dans le style des fonds PepsGoal et dont
 * le ciel est découpé pour laisser passer la météo. Les deux traitements se désactivent.
 */
@Composable
internal fun StylePanel(ui: AppState, spec: ThemePackSpec, image: File, stamp: Long, onChange: (ThemePackSpec) -> Unit, onStamp: () -> Unit) {
    val p = LocalPal.current; val ctx = LocalContext.current
    val w = spec.wallpaper
    val done = w.kind == "image" && image.exists() && (w.styled == true || w.foreground != null)
    var stylize by remember(spec.id) { mutableStateOf(w.styled ?: true) }
    var weather by remember(spec.id) { mutableStateOf(w.weather ?: true) }
    var strength by remember(spec.id) { mutableFloatStateOf(.8f) }
    var source by remember(spec.id) { mutableStateOf<Uri?>(null) }
    var last by remember(spec.id) { mutableStateOf<StyleResult?>(null) }
    var error by remember(spec.id) { mutableStateOf<String?>(null) }
    fun run(uri: Uri) {
        error = null
        ui.scope.launch {
            try {
                val r = Creator.style(ui, spec.id, uri, stylize, weather, strength)
                last = r; onStamp()
                onChange(spec.copy(wallpaper = PackWallpaper("image", file = Packs.imageFile(ctx, spec.id).name,
                    foreground = if (r.foreground != null) Creator.fgFile(ctx, spec.id).name else null,
                    weather = weather && r.foreground != null, styled = r.stylized)))
                ui.toast(if (r.stylized) "Photo stylisée par PepsGoal Style." else "Photo prête.", "wand")
            } catch (e: ApiError) { error = e.message } catch (e: Exception) { error = "PepsGoal Style est indisponible pour le moment : réessaie plus tard." }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> if (uri != null) { source = uri; run(uri) } }
    fun choose() = pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Muted("Ta photo, redessinée par le serveur PepsGoal dans le style des fonds de l’application. Le ciel est découpé pour que la pluie, la neige, les nuages et la nuit passent derrière le paysage.", small = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(96.dp, 150.dp).chunk(p.surface2, p.edge, 16.dp, 3.dp, 1.5.dp).clip(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                when {
                    Creator.styleBusy -> PSpinner(10.dp, label = "Calcul sur le serveur")
                    done -> CompositionLocalProvider(LocalPal provides rememberPackPal(spec)) { PackWallpaperView(spec, image, stamp) }
                    else -> Emoji("🖼️", 34.dp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (Creator.styleBusy) Text("Calcul sur le serveur PepsGoal…", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                else if (done) {
                    if (w.styled == true) Chip("Stylisée", "wand", p.type("routine").c)
                    Chip(if (w.foreground != null) "Météo : ciel découpé" else "Météo coupée", if (w.foreground != null) "cloud" else "x")
                    last?.let { r -> if (weather && r.foreground == null) Muted("Pas de ciel trouvé sur cette photo : la météo n’y passera pas.", small = true) }
                } else Muted("Aucune photo pour l’instant.", small = true)
            }
        }
        SetToggle("Stylisation PepsGoal", "Aplats vifs, ciel en dégradé et contours doux, comme les fonds de l’application.", "wand", on = stylize, onChange = { stylize = it })
        if (stylize) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Intensité"), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink2)
                Text("${(strength * 100).roundToInt()} %", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            PSlider(strength, { strength = (it * 20).roundToInt() / 20f }, label = "Intensité")
        }
        SetToggle("Adaptation météo", "Le ciel suit le temps qu’il fait et l’heure ; la pluie et la neige tombent derrière le paysage.", "cloud", on = weather, onChange = { weather = it })
        error?.let { Text(it, color = p.danger, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PButton(if (done || source != null) "Autre photo" else "Choisir une photo", { choose() }, Modifier.weight(1f), icon = "image", kind = if (done) "normal" else "primary", enabled = !Creator.styleBusy)
            if (source != null) PButton("Relancer", { source?.let { run(it) } }, Modifier.weight(1f), icon = "repeat", enabled = !Creator.styleBusy)
        }
        val colors = last?.colors.orEmpty().filter(PackFormat::isHex)
        if (colors.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                colors.take(5).forEach { c -> Box(Modifier.size(22.dp).clip(CircleShape).background(hex(c)).border(1.5.dp, p.edge, CircleShape)) }
                Spacer(Modifier.weight(1f))
            }
            PButton("Reprendre ces couleurs pour le décor", {
                onChange(spec.copy(colors = spec.colors.copy(deco = colors.take(2).map { it.uppercase() })))
                ui.toast("Couleurs du décor reprises de la photo.", "palette")
            }, Modifier.fillMaxWidth(), icon = "palette", small = true, kind = "ghost")
        }
        Muted("Les calculs sont faits par le serveur : une connexion est nécessaire. Ta photo n’est visible par les autres que si tu publies le pack.", small = true)
    }
}

/* ------------------------------------------------------------------ publication */

@Composable
fun PublishPackScreen(ui: AppState, id: String) {
    ui.watch()
    val p = LocalPal.current; val ctx = LocalContext.current
    val spec = remember(id, ui.rev, ui.themeTick) { PackLibrary.personal.firstOrNull { it.id == id } }
    LaunchedEffect(Unit) { if (!Creator.itemsLoaded) Creator.refreshItems(ui) }
    val published = Creator.publishedOf(ctx, id)
    var description by remember(id) { mutableStateOf(published?.description ?: "") }
    var price by remember(id) { mutableIntStateOf(published?.price ?: 100) }
    var priceText by remember(id) { mutableStateOf((published?.price ?: 100).toString()) }
    val image = Packs.imageFile(ctx, id)
    ScreenFrame(ui, if (published != null) "Mettre à jour" else "Vendre mon pack", "Retour", { ui.back() }, icon = "star", emoji = "🛍️") {
        if (spec == null) { item("none") { Glass { Muted("Ce pack n’existe plus sur cet appareil.") } }; return@ScreenFrame }
        val blocker = Creator.blocker(ui)
        val problem = Market.publishProblem(spec, image.exists())
        item("preview") {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PackPreview(spec, image, Modifier.size(120.dp, 220.dp), compact = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${spec.emoji} ${spec.name}", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        if (published != null) Chip(Market.statusLabel(published.status), if (published.status == "published") "check" else "clock",
                            if (published.status == "published") p.type("routine").c else if (published.status == "rejected") p.danger else p.type("challenge").c)
                        Muted("Vendu sous ton nom : ${ui.pg.me()?.let { ui.pg.realName(it) } ?: ""}", small = true)
                    }
                }
                if (published?.status == "rejected" && published.reviewNote.isNotBlank()) Text("Motif du refus : ${published.reviewNote}", Modifier.padding(top = 10.dp), color = p.danger, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        if (blocker != null || problem != null) item("block") { Glass { CardHead(if (blocker != null) "Connexion nécessaire" else "Pas encore prêt", emoji = "⚠️"); Muted(blocker ?: problem!!, small = true) } }
        item("price") {
            Glass {
                CardHead("Prix", emoji = "🔥")
                FlowRowCompat(gap = 6.dp) {
                    CREATOR_PRICES.forEach { v -> FChip(Market.priceLabel(v), price == v, { price = v; priceText = v.toString() }) }
                }
                Field(priceText, { t -> priceText = t.filter(Char::isDigit).take(4); priceText.toIntOrNull()?.let { price = Market.clampPrice(it) } }, "Prix libre (flammes)",
                    Modifier.fillMaxWidth().padding(top = 10.dp), keyboard = KeyboardOptions(keyboardType = KeyboardType.Number), max = 4,
                    help = "De 0 à ${fmtNum(CREATOR_PRICE_MAX)} flammes, par pas de 5.")
                Row(Modifier.fillMaxWidth().padding(top = 10.dp).background(p.surface2, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Emoji("💸", 26.dp)
                    Text(if (price <= 0) "Gratuit : ton pack sera offert à tous." else "Tu touches 100 % : ${fmtNum(price)} flammes à chaque achat.", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
        }
        item("desc") {
            Glass {
                CardHead("Présentation", emoji = "✍️")
                Field(description, { description = it.take(280) }, "Description (facultative)", Modifier.fillMaxWidth(), singleLine = false, minLines = 3, max = 280, counter = true,
                    placeholder = "Les couleurs de mon quartier au coucher du soleil…")
            }
        }
        item("go") {
            Glass {
                Muted("Ton pack est vérifié par la modération avant sa mise en vente. Une icône ou un effet animé que tu as achetés ne sont pas inclus : ils restent à toi.", small = true)
                PButton(if (Creator.publishing) "Envoi…" else if (published != null) "Mettre à jour dans la boutique" else "Publier dans la boutique", {
                    ui.scope.launch {
                        try {
                            val out = Creator.publish(ui, spec, description, price)
                            ui.toast(if (out.status == "published") "« ${spec.name} » est en vente !" else "« ${spec.name} » est envoyé : il sera en vente après validation.", "star")
                            ui.back()
                        } catch (e: ApiError) { ui.toast(e.message ?: "Publication impossible pour le moment.", "x") }
                    }
                }, Modifier.fillMaxWidth().padding(top = 10.dp), icon = "send", kind = "primary", enabled = blocker == null && problem == null && !Creator.publishing)
            }
        }
    }
}

/* ------------------------------------------------------------------ mes créations */

/** Aperçu d'un pack en boutique : son image si elle existe, sinon ses couleurs. */
@Composable
private fun CreatorPreview(item: CreatorItem, modifier: Modifier) {
    val spec = remember(item.id, item.version) { PackFormat.parseOrNull(item.content.toString(), checkCatalog = false) }
    when {
        item.preview != null -> AsyncImage(item.preview, null, modifier.clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
        spec != null -> PackPreview(spec, null, modifier, compact = true)
        else -> Box(modifier.clip(RoundedCornerShape(14.dp)).background(LocalPal.current.surface2))
    }
}

@Composable
fun CreatorScreen(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg
    LaunchedEffect(Unit) { Creator.refreshItems(ui); Creator.syncSales(ui) }
    val items = Creator.items
    val mine = remember(ui.rev, ui.themeTick) { Packs.all() }
    ScreenFrame(ui, "Mes créations", "Retour", { ui.back() }, icon = "star", emoji = "🛍️") {
        item("stats") {
            Glass {
                CardHead("Boutique des créateurs", emoji = "💸")
                Muted("Publie tes packs, fixe ton prix : chaque achat te revient à 100 %.", small = true)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("${fmtNum(pg.marketEarned())} 🔥" to "gagnées", "${items.sumOf { it.sales }}" to "achats", "${items.count { it.status == "published" }}" to "en vente").forEach { (v, l) ->
                        Column(Modifier.weight(1f).background(p.surface2, RoundedCornerShape(16.dp)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            FitText(v, size = 18, weight = FontWeight.ExtraBold, family = Unbounded)
                            Muted(l, small = true)
                        }
                    }
                }
            }
        }
        val blocker = Creator.blocker(ui)
        if (blocker != null) item("off") { Glass { CardHead("Connexion nécessaire", emoji = "📡"); Muted(blocker, small = true) } }
        else Creator.itemsError?.let { e -> item("err") { Glass { Muted(e, small = true) } } }
        item("items") {
            Glass {
                CardHead("En boutique", emoji = "🏪") { if (items.isNotEmpty()) Count(items.size) }
                if (items.isEmpty()) Muted(if (Creator.itemsLoaded) "Aucun pack publié pour l’instant." else "Chargement…", small = true)
                items.forEach { it ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CreatorPreview(it, Modifier.size(64.dp, 104.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(it.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
                            Chip(Market.statusLabel(it.status), if (it.status == "published") "check" else if (it.status == "rejected") "x" else "clock",
                                if (it.status == "published") p.type("routine").c else if (it.status == "rejected") p.danger else p.type("challenge").c)
                            Muted("${Market.priceLabel(it.price)} · ${if (it.sales == 1) "1 achat" else "${fmtNum(it.sales)} achats"} · ${fmtNum(it.earned)} 🔥 gagnées", small = true)
                            if (it.status == "rejected" && it.reviewNote.isNotBlank()) Text("Motif : ${it.reviewNote}", color = p.danger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            PButton("Retirer", {
                                ui.confirm("Retirer « ${it.name} » ?", "Il disparaît de la boutique. Ceux qui l’ont déjà acheté le gardent.", "Retirer") { ui.run("Pack retiré de la boutique.", "trash") { Creator.retire(ui, it.id) } }
                            }, small = true, kind = "ghost", icon = "trash")
                        }
                    }
                }
            }
        }
        item("publish") {
            Glass {
                CardHead("Publier un pack", emoji = "🎨")
                if (mine.isEmpty()) Muted("Crée d’abord un pack : couleurs, fond, photo ou PepsGoal Style.", small = true)
                mine.forEach { s ->
                    val pub = Creator.publishedOf(ui.app, s.id)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${s.emoji} ${s.name}", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                        PButton(if (pub != null) "Mettre à jour" else "Vendre", { ui.push(Screen.PublishPack(s.id)) }, small = true, icon = if (pub != null) "repeat" else "star")
                    }
                }
                PButton("Créer un pack", { ui.push(Screen.PackEditor()) }, Modifier.fillMaxWidth().padding(top = 12.dp), icon = "plus", kind = "primary")
            }
        }
        item("help") { Hint("Les flammes de tes ventes arrivent dans ton porte-monnaie à ta prochaine connexion. Un pack modifié repasse par la modération avant d’être de nouveau en vente.") }
    }
}

/* ------------------------------------------------------------------ modération */

/** Modération › Boutique : packs des créateurs en attente, à valider ou refuser (motif transmis au créateur). */
@Composable
internal fun ModShop(ui: AppState) {
    val p = LocalPal.current
    var list by remember { mutableStateOf<List<PendingPack>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { try { list = ui.cloud.pendingPacks(); err = null } catch (e: ApiError) { err = e.message } }
    Glass {
        CardHead("Packs à valider", emoji = "🛍️") { list?.let { Count(it.size) } }
        err?.let { Muted(it, small = true) }
        val l = list
        if (l == null && err == null) Muted("Chargement…", small = true)
        if (l != null && l.isEmpty()) Muted("Aucun pack en attente.", small = true)
        l?.forEach { x ->
            var reason by remember(x.id) { mutableStateOf("") }
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (x.preview != null) AsyncImage(x.preview, null, Modifier.size(90.dp, 150.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(x.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Muted("par ${x.author} · ${Market.priceLabel(x.price)}", small = true)
                        if (x.description.isNotBlank()) Text("« ${x.description} »", fontSize = 14.sp)
                    }
                }
                Field(reason, { reason = it.take(200) }, "Motif en cas de refus", Modifier.fillMaxWidth(), max = 200)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PButton("Valider", { ui.run("Pack mis en vente.") { ui.cloud.approvePack(x.id); tick++ } }, Modifier.weight(1f), color = p.type("routine").c, small = true, icon = "check")
                    PButton("Refuser", { ui.run("Pack refusé.", "x") { ui.cloud.rejectPack(x.id, reason.ifBlank { "Ne respecte pas les règles de la boutique." }); tick++ } }, Modifier.weight(1f), small = true, icon = "x")
                }
            }
        }
        PButton("Actualiser", { tick++ }, Modifier.padding(top = 10.dp), icon = "repeat", small = true, kind = "ghost")
    }
}
