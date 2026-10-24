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
import androidx.compose.ui.draw.shadow
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
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (editing != null || adding) PlaceEditor(ui, editing) { editing = null; adding = false }
        else {
            CatHero("places", if (u.places.isEmpty()) "Maison, travail, salle de sport… pour les rappels par lieu" else u.places.take(3).joinToString(" · ") { "${it.icon} ${it.name}" }, chips = {
                Chip(fr.pepsgoal.core.pl(u.places.size, "emplacement", "emplacements"), "pin", p.type("measure").c)
            })
            Glass {
                CardHead("Mes emplacements", emoji = "📍") { PButton("Ajouter", { adding = true }, icon = "plus", small = true, kind = "primary") }
                if (u.places.isEmpty()) Empty("Aucun emplacement", "Enregistre ta maison, ton travail ou ta salle de sport : tu les retrouveras d’un geste pour les rappels par lieu.", icon = "pin")
                u.places.forEachIndexed { i, pl ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 56.dp).height(1.dp).background(p.line.copy(alpha = .5f)))
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(smoothShape(14.dp)).background(p.type("measure").c.copy(alpha = .2f)), contentAlignment = Alignment.Center) { Emoji(pl.icon, 26.dp) }
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
        Muted("Touche la carte pour placer l’emplacement.", small = true)
        Spacer(Modifier.height(8.dp))
        val locate = {
            if (fr.pepsgoal.app.sys.GeoReminders.hasFine(ctx)) perm.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION))
            else perm.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        Box(Modifier.fillMaxWidth().height(320.dp).shadow((if (p.transp) 0.dp else 12.dp).ifShadows(), smoothShape(24.dp), spotColor = p.flame.copy(alpha = .35f))
            .clip(smoothShape(24.dp)).border(1.dp, p.line, smoothShape(24.dp))) {
            StyledMap(start, if (edit != null) 17.0 else 14.0, point, radius, icon, onTap = { point = it }, onReady = { map = it })
            // Commandes flottantes en verre : zoom et position
            Column(Modifier.align(Alignment.TopEnd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MapBtn("plus", "Zoomer") { map?.controller?.zoomIn() }
                MapBtn("minus", "Dézoomer") { map?.controller?.zoomOut() }
            }
            MapBtn("compass", "Utiliser ma position", Modifier.align(Alignment.BottomEnd).padding(10.dp), accent = true) { locate() }
            // État du point, en pastille
            Row(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50)).background(p.glassStrong.copy(alpha = .92f)).border(1.dp, p.line, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (point == null) "👆" else icon, fontSize = 13.sp)
                Text(if (point == null) "Touche pour placer" else name.ifBlank { "Point placé" }, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            Text("© contributeurs OpenStreetMap", Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(p.glassStrong.copy(alpha = .8f)).padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 9.sp, color = p.ink2)
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

@Composable
private fun MapBtn(icon: String, desc: String, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(modifier.bounce(inter, .88f).size(42.dp).shadow(8.dp.ifShadows(), RoundedCornerShape(50), spotColor = p.flame.copy(alpha = .4f)).clip(RoundedCornerShape(50))
        .background(if (accent) androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.flame2, p.flame, p.pink)) else androidx.compose.ui.graphics.SolidColor(p.glassStrong.copy(alpha = .95f)))
        .border(1.dp, if (accent) androidx.compose.ui.graphics.Color.White.copy(alpha = .5f) else p.line, RoundedCornerShape(50))
        .springClick(inter, label = desc, onClick = onClick), contentAlignment = Alignment.Center) {
        Pi(icon, size = 20.dp, tint = if (accent) p.onAccent else p.ink)
    }
}

/**
 * Carte OpenStreetMap recolorée en deux tons aux couleurs de PepsGoal, cercle du rayon
 * en pointillés et marqueur dessiné : pastille dégradée avec l'icône de l'emplacement.
 */
@Composable
fun StyledMap(start: GeoPoint, zoom: Double, point: GeoPoint?, radius: Int, icon: String, interactive: Boolean = true, onTap: (GeoPoint) -> Unit = {}, onReady: (MapView?) -> Unit = {}) {
    val p = LocalPal.current; val ctx = LocalContext.current
    val dark = p.dark
    val accent = p.flame.toArgb(); val accent2 = p.pink.toArgb(); val accent3 = p.flame2.toArgb()
    val bg = p.bg; val ink = p.ink
    val pin = remember(icon, accent, accent2, dark) { pinDrawable(ctx, icon, accent3, accent, accent2) }
    AndroidView(factory = { c ->
        mapConfig(c)
        MapView(c).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            isTilesScaledToDpi = true; setMultiTouchControls(interactive)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(zoom); controller.setCenter(point ?: start)
            // Teinte du fond de carte vers la palette de l'application (lavande le jour, indigo la nuit)
            overlayManager.tilesOverlay.setColorFilter(tint(bg, ink, dark))
            overlayManager.tilesOverlay.loadingBackgroundColor = bg.toArgb(); overlayManager.tilesOverlay.loadingLineColor = bg.toArgb()
            setBackgroundColor(bg.toArgb())
            if (interactive) {
                // La carte garde ses gestes (déplacement, zoom) au lieu de faire défiler la page
                setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
                overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(g: GeoPoint): Boolean { onTap(g); return true }
                    override fun longPressHelper(g: GeoPoint): Boolean { onTap(g); return true }
                }))
            } else setOnTouchListener { _, _ -> true }
            onReady(this)
        }
    }, update = { mv ->
        mv.overlays.removeAll { it is Marker || it is Polygon }
        point?.let { pt ->
            mv.overlays.add(Polygon(mv).apply {
                points = Polygon.pointsAsCircle(pt, radius.toDouble())
                fillPaint.color = (accent and 0x00FFFFFF) or 0x2E000000
                outlinePaint.color = accent; outlinePaint.strokeWidth = 3f * ctx.resources.displayMetrics.density
                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f * ctx.resources.displayMetrics.density, 7f * ctx.resources.displayMetrics.density), 0f)
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                setOnClickListener { _, _, _ -> false }
            })
            mv.overlays.add(Marker(mv).apply { position = pt; this.icon = pin; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); setOnMarkerClickListener { _, _ -> true } })
        }
        mv.invalidate()
    }, onRelease = { it.onDetach(); onReady(null) }, modifier = Modifier.fillMaxSize())
}

/**
 * Carte en deux tons aux couleurs de PepsGoal : les tuiles OpenStreetMap sont ramenées à leur luminosité, puis
 * recolorées du fond de l'application (terrain, routes) vers l'encre (textes, contours). La nuit, l'échelle s'inverse.
 */
private fun tint(bg: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color, dark: Boolean): android.graphics.ColorMatrixColorFilter {
    // Clair : terrain = fond légèrement éclairci, textes = encre adoucie ; sombre : terrain = fond légèrement relevé, textes = encre
    val land = if (dark) androidx.compose.ui.graphics.lerp(bg, androidx.compose.ui.graphics.Color.White, .07f) else androidx.compose.ui.graphics.lerp(bg, androidx.compose.ui.graphics.Color.White, .45f)
    val text = if (dark) androidx.compose.ui.graphics.lerp(ink, bg, .25f) else androidx.compose.ui.graphics.lerp(ink, bg, .12f)
    // Jour : L = 255 → terrain, L = 0 → texte ; nuit : on inverse la luminosité (L = 255 → terrain sombre)
    val lo = if (dark) land else text; val hi = if (dark) text else land
    fun row(l: Float, h: Float, i: Int): FloatArray {
        val k = (h - l) // sortie = l·255 + k·L, avec L = .299 R + .587 G + .114 B
        return if (!dark) floatArrayOf(k * .299f, k * .587f, k * .114f, 0f, l * 255f)
        else floatArrayOf(-k * .299f, -k * .587f, -k * .114f, 0f, h * 255f)
    }
    val m = row(lo.red, hi.red, 0) + row(lo.green, hi.green, 1) + row(lo.blue, hi.blue, 2) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
    return android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(m))
}

/** Marqueur : goutte dégradée aux couleurs de l'accent, liseré blanc, ombre douce et emoji de l'emplacement. */
private fun pinDrawable(ctx: Context, emoji: String, c1: Int, c2: Int, c3: Int): android.graphics.drawable.BitmapDrawable {
    val d = ctx.resources.displayMetrics.density
    val w = (54 * d).toInt(); val h = (66 * d).toInt(); val r = 23 * d; val cx = w / 2f; val cy = r + 4 * d
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    val path = android.graphics.Path().apply {
        addCircle(cx, cy, r, android.graphics.Path.Direction.CW)
        moveTo(cx - r * .55f, cy + r * .8f); lineTo(cx, h - 3 * d); lineTo(cx + r * .55f, cy + r * .8f); close()
    }
    val shadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; maskFilter = android.graphics.BlurMaskFilter(4 * d, android.graphics.BlurMaskFilter.Blur.NORMAL) }
    cv.save(); cv.translate(0f, 2 * d); cv.drawPath(path, shadow); cv.restore()
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(0f, cy - r, 0f, h.toFloat(), intArrayOf(c1, c2, c3), null, android.graphics.Shader.TileMode.CLAMP)
    }
    cv.drawPath(path, fill)
    val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 2.5f * d; color = android.graphics.Color.WHITE }
    cv.drawPath(path, stroke)
    // Disque blanc sous l'emoji pour qu'il ressorte
    cv.drawCircle(cx, cy, r * .72f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() })
    val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 22 * d; textAlign = android.graphics.Paint.Align.CENTER }
    val fm = tp.fontMetrics
    cv.drawText(emoji, cx, cy - (fm.ascent + fm.descent) / 2, tp)
    return android.graphics.drawable.BitmapDrawable(ctx.resources, bmp)
}
