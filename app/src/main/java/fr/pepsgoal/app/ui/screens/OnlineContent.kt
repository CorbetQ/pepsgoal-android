package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.sys.ContentSync
import fr.pepsgoal.app.sys.Packs
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*
import kotlinx.coroutines.launch

/*
 * Contenus en ligne (9.2) : « Nouveautés en ligne » dans la boutique (obtenir, acheter avec les flammes, appliquer)
 * et contenus obtenus dans Apparence. Le catalogue est relevé à l'ouverture (au plus toutes les 15 minutes).
 */

/** Applique un contenu obtenu : pack de thème ou fond seul (image vérifiée, téléchargée si besoin). */
private suspend fun applyOnline(ui: AppState, e: ContentSync.Entry) {
    val ctx = ui.app
    val img = ContentSync.ensureImage(ctx, e)
    val spec = e.spec
    if (spec != null) {
        if (spec.wallpaper.kind == "image" && img == null) { ui.toast("Image du pack indisponible hors ligne : réessaie une fois connecté.", "cloudoff"); return }
        ui.toast(Packs.apply(ui, spec, img), "palette")
    } else {
        if (img == null) { ui.toast("Image du fond indisponible hors ligne : réessaie une fois connecté.", "cloudoff"); return }
        ui.toast(if (Packs.applyBackground(ui, img)) "Fond « ${e.item.name} » appliqué." else "Ce fond n’a pas pu être appliqué.", "image")
    }
}

/** Vignette d'un contenu : aperçu du pack, ou image du fond. */
@Composable
private fun OnlinePreview(e: ContentSync.Entry, modifier: Modifier) {
    val p = LocalPal.current; val ctx = LocalContext.current
    when {
        e.spec != null -> PackPreview(e.spec, e.image, modifier, compact = true)
        e.image != null -> Box(modifier.clip(RoundedCornerShape(16.dp))) {
            AsyncImage(ImageRequest.Builder(ctx).data(e.image).memoryCacheKey("content:${e.item.id}:${e.item.version}").build(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        else -> Box(modifier.clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(p.surface2, p.deco[0]))), contentAlignment = Alignment.Center) { Emoji("🏞️", 32.dp) }
    }
}

@Composable
private fun OnlineTile(ui: AppState, e: ContentSync.Entry) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me()
    val owned = pg.ownsContent(e.item.id, u)
    val active = e.spec != null && pg.packSpecActive(e.spec, u)
    val price = e.item.price
    val wallet = pg.wallet(u)
    fun obtain() {
        val run = {
            val err = pg.obtainContent(e.item.id, price)
            if (err != null) ui.toast(err, "x")
            else { ui.toast("« ${e.item.name} » est à toi !", "star"); ui.scope.launch { applyOnline(ui, e) } }
        }
        if (price <= 0) run()
        else ui.confirm("Acheter « ${e.item.name} » ?", "$price flammes seront retirées de ton porte-monnaie ($wallet disponibles). Ton niveau ne baisse pas.", "Acheter $price 🔥", danger = false) { run() }
    }
    Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OnlinePreview(e, Modifier.fillMaxWidth().height(170.dp))
        Text(e.spec?.let { "${it.emoji} ${e.item.name}" } ?: e.item.name, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
        if (e.item.author.isNotBlank()) Muted("par ${e.item.author}", small = true, maxLines = 1)
        if (e.item.description.isNotBlank()) Muted(e.item.description, small = true, maxLines = 2)
        when {
            active -> PButton("Actif", {}, Modifier.fillMaxWidth(), icon = "check", small = true, kind = "ghost", enabled = false)
            owned -> PButton("Appliquer", { ui.scope.launch { applyOnline(ui, e) } }, Modifier.fillMaxWidth(), icon = "palette", small = true)
            else -> PButton(if (price <= 0) "Obtenir · gratuit" else "Obtenir · ${fmtNum(price)} 🔥", { obtain() }, Modifier.fillMaxWidth(),
                icon = "gift", small = true, kind = "primary", enabled = price <= 0 || wallet >= price)
        }
        if (!owned && price > 0 && wallet < price) Text("Encore ${fmtNum(price - wallet)} flammes", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink2)
    }
}

/** Boutique › Packs : « Nouveautés en ligne ». Hors ligne, le dernier catalogue en cache reste affiché. */
@Composable
fun OnlineContentCard(ui: AppState) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { ContentSync.refresh(ctx) }
    val entries = ContentSync.entries
    Glass {
        CardHead("Nouveautés en ligne", emoji = "🛰️") { if (entries.isNotEmpty()) Count(entries.size) }
        when {
            entries.isEmpty() && ContentSync.busy -> Muted("Recherche des nouveautés…", small = true)
            entries.isEmpty() && ContentSync.offline -> Muted("Hors ligne : les nouveautés s’afficheront à ta prochaine connexion.", small = true)
            entries.isEmpty() -> Muted("Aucune nouveauté pour le moment. Reviens bientôt !", small = true)
            else -> Muted("Packs et fonds créés pour PepsGoal, à télécharger. Ceux que tu obtiens restent dans Apparence.", small = true)
        }
        if (ContentSync.offline && entries.isNotEmpty()) Chip("Hors ligne · catalogue gardé sur l’appareil", "cloudoff", modifier = Modifier.padding(top = 6.dp))
        if (entries.isNotEmpty()) LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.item.id }) { e -> OnlineTile(ui, e) }
        }
        PButton(if (ContentSync.busy) "Actualisation…" else "Actualiser", { ui.scope.launch { ContentSync.refresh(ctx, force = true) } },
            Modifier.padding(top = 10.dp), icon = "repeat", small = true, kind = "ghost", enabled = !ContentSync.busy)
    }
}

/** Apparence : fonds téléchargés obtenus (les packs obtenus sont dans « Mes packs »). */
@Composable
fun OnlineBackgroundsCard(ui: AppState) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { ContentSync.refresh(ctx) }
    val bgs = ContentSync.ownedBackgrounds(ui.pg)
    if (bgs.isEmpty()) return
    Glass {
        CardHead("Fonds téléchargés", emoji = "🛰️") { Count(bgs.size) }
        LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(bgs, key = { it.item.id }) { e ->
                BgTile(e.item.name, null, false, { ui.scope.launch { applyOnline(ui, e) } }) { OnlinePreview(e, Modifier.fillMaxSize()) }
            }
        }
    }
}
