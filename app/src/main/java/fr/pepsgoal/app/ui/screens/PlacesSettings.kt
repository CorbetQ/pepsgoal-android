package fr.pepsgoal.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.model.SavedPlace
import fr.pepsgoal.core.uid
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

private val PLACE_ICONS = listOf("🏠", "💼", "🏋️", "🏫", "🌳", "🛒", "☕", "📍")
private val PLACE_NAMES = listOf("Maison", "Travail", "Salle de sport", "École", "Parc", "Courses")

/** Réglages de la carte : cache des tuiles dans l'application (pas de stockage partagé), agent identifié. */
private fun mapConfig(ctx: Context) {
    val c = Configuration.getInstance()
    c.userAgentValue = ctx.packageName
    c.osmdroidBasePath = File(ctx.cacheDir, "osmdroid"); c.osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
}

/**
 * Emplacements enregistrés (maison, travail, salle de sport…) : posés sur une carte OpenStreetMap intégrée,
 * puis proposés d'un geste pour les rappels par lieu des objectifs. Synchronisés avec le compte.
 */
@Composable
fun PlacesSettings(ui: AppState) {
    val pg = ui.pg; val u = pg.me() ?: return; val p = LocalPal.current
    var editing by remember { mutableStateOf<SavedPlace?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (editing != null || adding) PlaceEditor(ui, editing) { editing = null; adding = false }
        else {
            Glass {
                CardHead("Mes emplacements") { PButton("Ajouter", { adding = true }, icon = "plus", small = true, kind = "primary") }
                if (u.places.isEmpty()) Empty("Aucun emplacement", "Enregistre ta maison, ton travail ou ta salle de sport : tu les retrouveras d’un geste pour les rappels par lieu.", icon = "pin")
                u.places.forEachIndexed { i, pl ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 56.dp).height(1.dp).background(p.line.copy(alpha = .5f)))
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(p.type("measure").c.copy(alpha = .2f)), contentAlignment = Alignment.Center) { Emoji(pl.icon, 26.dp) }
                        Column(Modifier.weight(1f)) {
                            Text(pl.name, fontWeight = FontWeight.ExtraBold)
                            Muted("Rayon ${pl.radius} m · ${"%.4f".format(pl.lat)}, ${"%.4f".format(pl.lon)}", small = true)
                        }
                        IconBtn("edit", "Modifier ${pl.name}", { editing = pl }, plain = true, size = 36.dp)
                        IconBtn("trash", "Supprimer ${pl.name}", { ui.confirm("Supprimer « ${pl.name} » ?", "Les rappels déjà créés à cet endroit restent actifs.", "Supprimer") { u.places.remove(pl); pg.store.commit() } }, plain = true, size = 36.dp)
                    }
                }
            }
            Muted("Les emplacements sont enregistrés avec ton compte. Les rappels par lieu, eux, restent surveillés sur ce téléphone. Carte © contributeurs OpenStreetMap.", small = true)
        }
    }
}

@Composable
private fun PlaceEditor(ui: AppState, edit: SavedPlace?, done: () -> Unit) {
    val ctx = LocalContext.current; val p = LocalPal.current; val pg = ui.pg; val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(edit?.name ?: "") }
    var icon by remember { mutableStateOf(edit?.icon ?: "🏠") }
    var radius by remember { mutableIntStateOf(edit?.radius ?: 150) }
    // Point de départ : l'emplacement modifié, sinon la ville de la météo, sinon Angers
    val start = edit?.let { GeoPoint(it.lat, it.lon) } ?: pg.me()?.settings?.weather?.let { GeoPoint(it.lat, it.lon) } ?: GeoPoint(47.4784, -0.5632)
    var point by remember { mutableStateOf(edit?.let { GeoPoint(it.lat, it.lon) }) }
    var map by remember { mutableStateOf<MapView?>(null) }
    val perm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) scope.launch { fr.pepsgoal.app.sys.GeoReminders.here(ctx)?.let { l -> GeoPoint(l.latitude, l.longitude).also { point = it; map?.controller?.animateTo(it, 17.5, 600L) } } ?: ui.toast("Position introuvable : active la localisation.", "pin") }
        else ui.toast("Autorise la localisation pour utiliser ta position.", "pin")
    }
    Glass {
        CardHead(if (edit == null) "Nouvel emplacement" else "Modifier l’emplacement")
        Field(name, { name = it }, "Nom", placeholder = "Maison, travail…", max = 40, icon = "pin")
        FlowRowCompat(Modifier.padding(top = 8.dp), gap = 6.dp) { PLACE_NAMES.forEachIndexed { i, n -> FChip(n, name == n, { name = n; icon = PLACE_ICONS[i] }) } }
        FlowRowCompat(Modifier.padding(top = 8.dp), gap = 6.dp) { PLACE_ICONS.forEach { e -> FChip(e, icon == e, { icon = e }) } }
        Spacer(Modifier.height(10.dp))
        Muted("Touche la carte pour placer l’emplacement, ou utilise ta position.", small = true)
        Spacer(Modifier.height(8.dp))
        // Carte OpenStreetMap : tuiles inversées en thème sombre, marqueur et cercle du rayon
        val ring = p.flame.toArgb(); val dark = p.dark
        AndroidView(factory = { c ->
            mapConfig(c)
            MapView(c).apply {
                setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(if (edit != null) 17.0 else 14.0); controller.setCenter(start)
                if (dark) overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERTED_COLORS)
                // La carte garde ses gestes (déplacement, zoom) au lieu de faire défiler la page
                setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
                overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(g: GeoPoint): Boolean { point = g; return true }
                    override fun longPressHelper(g: GeoPoint): Boolean { point = g; return true }
                }))
                map = this
            }
        }, update = { mv ->
            mv.overlays.removeAll { it is Marker || it is Polygon }
            point?.let { pt ->
                mv.overlays.add(Polygon(mv).apply {
                    points = Polygon.pointsAsCircle(pt, radius.toDouble())
                    fillPaint.color = (ring and 0x00FFFFFF) or 0x33000000; outlinePaint.color = ring; outlinePaint.strokeWidth = 4f
                    setOnClickListener { _, _, _ -> false }
                })
                mv.overlays.add(Marker(mv).apply { position = pt; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); title = name.ifBlank { "Emplacement" }; setOnMarkerClickListener { _, _ -> true } })
            }
            mv.invalidate()
        }, onRelease = { it.onDetach() },
            modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, p.line, RoundedCornerShape(20.dp)))
        DisposableEffect(Unit) { onDispose { map = null } }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton("Ma position", {
                if (fr.pepsgoal.app.sys.GeoReminders.hasFine(ctx)) perm.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION))
                else perm.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
            }, icon = "pin", small = true)
            Text(if (point == null) "Aucun point choisi" else "Point placé ✓", fontSize = 13.sp, color = p.ink2, fontWeight = FontWeight.Bold)
        }
        Text("Rayon", Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold)
        FlowRowCompat(Modifier.padding(top = 6.dp), gap = 6.dp) { listOf(100, 150, 300, 500).forEach { r -> FChip("$r m", radius == r, { radius = r }) } }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            PButton("Annuler", done, kind = "ghost", small = true)
            PButton("Enregistrer", {
                val pt = point ?: return@PButton ui.toast("Place l’emplacement sur la carte.", "pin")
                if (name.isBlank()) return@PButton ui.toast("Donne un nom à cet emplacement.", "pin")
                val u = pg.me() ?: return@PButton
                if (edit != null) { edit.name = name.trim().take(40); edit.icon = icon; edit.lat = pt.latitude; edit.lon = pt.longitude; edit.radius = radius }
                else u.places.add(SavedPlace(uid(), name.trim().take(40), icon, pt.latitude, pt.longitude, radius))
                pg.store.commit(); ui.toast("Emplacement « ${name.trim()} » enregistré.", "pin"); done()
            }, icon = "check", kind = "primary", small = true)
        }
    }
}
