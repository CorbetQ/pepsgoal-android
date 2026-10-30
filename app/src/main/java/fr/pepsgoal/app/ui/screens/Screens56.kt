package fr.pepsgoal.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.data.Proof
import fr.pepsgoal.app.data.Proofs
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.step
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.LeagueSeason
import fr.pepsgoal.core.cloud.closeLeagueSeason
import kotlinx.coroutines.launch
import kotlin.math.sin

/* ------------------------------------------------------------------ Galerie des preuves */

/** Galerie chronologique d'un objectif : photos et notes, rangées par mois ; une touche ouvre la preuve en grand. */
@Composable
fun GalleryScreen(ui: AppState, goalId: String) {
    val ctx = LocalContext.current; val p = LocalPal.current; val g = ui.pg.goalById(goalId)
    val list = remember(Proofs.rev, goalId) { Proofs.of(ctx, goalId).sortedByDescending { it.date } }
    var big by remember { mutableStateOf<Proof?>(null) }
    var mode by remember { mutableStateOf<String?>(null) }
    val photos = remember(list) { list.filter { it.photo != null }.sortedBy { it.date } }
    ScreenFrame(ui, "Galerie", "Retour", { ui.back() }, icon = "image", color = p.type("measure").c, color2 = p.type("objective").c, emoji = "🖼️") {
        item("head") { MiniPlaque("${g?.title ?: ""} · ${pl(list.size, "souvenir", "souvenirs")}", emoji = g?.icon) }
        // Au moins deux photos : comparer la première et la dernière, ou tout revoir en accéléré
        if (photos.size >= 2) item("lapse") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton("Avant / après", { mode = "ba" }, Modifier.weight(1f), icon = "image", small = true)
                PButton("Accéléré", { mode = "lapse" }, Modifier.weight(1f), icon = "play", small = true, kind = "type", color = p.type("measure").c)
            }
        }
        if (list.isEmpty()) item("empty") { Empty("Pas encore de preuve", "Après une validation, ajoute une photo ou une note depuis la fiche de l’objectif.", icon = "image") }
        list.groupBy { it.date.substring(0, 7) }.forEach { (m, items) ->
            item("m$m") {
                val d = parseYmd("$m-01")
                SectionPlaque(cap("${Recaps.MONTHS_LONG[d.monthValue - 1]} ${d.year}"), "🗓️")
            }
            items.chunked(3).forEachIndexed { i, row ->
                item("r$m-$i") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { pr -> GalleryTile(pr, Modifier.weight(1f)) { big = pr } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    if (mode == "ba" && photos.size >= 2) BeforeAfterDialog(photos.first(), photos.last()) { mode = null }
    if (mode == "lapse" && photos.size >= 2) TimelapseDialog(photos) { mode = null }
    big?.let { pr ->
        // Grand format : visionneuse (fond noir du média, date et note en plaque, bouton rond sombre)
        androidx.compose.ui.window.Dialog({ big = null }, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(p.mediaBg)) {
                val bmp = rememberDecoded(pr.id, pr.photo) { Proofs.photoFile(ctx, pr)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
                if (bmp != null) Image(bmp, "Photo du ${fmtDate(pr.date)}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniPlaque(cap(weekdayLongDate(parseYmd(pr.date))))
                    if (pr.note.isNotBlank()) Text(pr.note, Modifier.chunk(p.surface, p.edge, 22.dp, 3.dp, 2.dp).padding(12.dp), color = p.ink)
                }
                MediaBtn("x", "Fermer", { big = null }, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp))
            }
        }
    }
}

@Composable
private fun GalleryTile(pr: Proof, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current; val p = LocalPal.current
    val bmp = rememberDecoded(pr.id, pr.photo) { Proofs.photoFile(ctx, pr)?.let { f -> val o = BitmapFactory.Options().apply { inSampleSize = 4 }; BitmapFactory.decodeFile(f.path, o)?.asImageBitmap() } }
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val press = rememberPress(inter)
    Box(modifier.aspectRatio(.78f).chunk(p.surface, p.edge, 18.dp, 4.dp, 2.dp, press).clip(RoundedCornerShape(16.dp)).springClick(inter, label = "Preuve du ${fmtDate(pr.date)}", onClick = onClick)) {
        if (bmp != null) Image(bmp, "Preuve du ${fmtDate(pr.date)}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("“${pr.note.take(70)}”", Modifier.padding(8.dp), fontSize = 12.sp, color = p.ink2, maxLines = 5)
        MiniPlaque(fmtDateShort(pr.date), Modifier.align(Alignment.BottomStart).padding(5.dp))
    }
}

/* ------------------------------------------------------------------ Compagnon flamme */

private val PET_LINES = mapOf(
    "proud" to listOf("Regarde-nous, on brille ! ✨", "Journée de champion, je suis fier de toi.", "Je bombe le torse : quelle régularité !"),
    "happy" to listOf("On est en feu aujourd’hui ! 🔥", "Encore une validation et je danse.", "Tu me fais grandir, tu sais ?", "Quelle énergie !"),
    "calm" to listOf("On valide quelque chose ensemble ?", "Je garde la flamme au chaud pour toi.", "Une petite action et je brille !"),
    "tired" to listOf("Grosse journée… on y va doucement ?", "Une version mini, ça compte aussi.", "Je bâille… mais je reste avec toi."),
)
/** Humeur du compagnon en clair (feuille et page). */
val PET_MOODS = mapOf("proud" to "Fier", "happy" to "Content", "calm" to "Calme", "tired" to "Fatigué")

/** État du compagnon avec l'humeur notée au journal du jour (gardé sur l'appareil). */
@Composable
fun rememberPet(ui: AppState): PetState {
    val ctx = LocalContext.current; val jr = fr.pepsgoal.app.data.Journal.rev
    return remember(ui.rev, jr, nowHM().take(2)) {
        val e = fr.pepsgoal.app.data.Journal.get(ctx, today())
        ui.pg.pet(journal = e?.mood, tiredTag = e?.tags?.any { it == "fatigué" || it == "malade" } == true)
    }
}

/**
 * Le compagnon dessiné en aplats : œuf, puis flamme qui grandit par stades, visage selon l'humeur, tenue et
 * accessoires portés. Animations Braise Pop par `graphicsLayer` (léger flottement, rebond au toucher et de joie,
 * clin d'œil de temps en temps), toutes coupées en animations réduites.
 */
@Composable
fun PetView(ui: AppState, size: Dp, onTap: (() -> Unit)? = null) {
    val p = LocalPal.current; val pet = rememberPet(ui)
    val acc = ui.prefs.petAcc; val outfit = ui.prefs.petOutfit.takeIf { it.isNotEmpty() && ui.pg.ownsItem("outfit:$it") }
    val live = !p.motion
    val bob = slowPhaseState(2600, 15, running = live)
    val jump = remember { Animatable(0f) }; val scope = rememberCoroutineScope()
    var wink by remember { mutableStateOf(false) }
    // Clin d'œil de temps en temps ; petit bond de joie quand il est fier ou content
    if (live && pet.stage.key != "egg") LaunchedEffect(pet.mood) {
        var n = 0
        while (true) {
            kotlinx.coroutines.delay(3800L + (n % 3) * 1100L); n++
            if (pet.mood in setOf("proud", "happy") && n % 2 == 0) { jump.snapTo(.6f); jump.animateTo(0f, spring(dampingRatio = .35f, stiffness = 420f)) }
            else if (pet.mood != "tired") { wink = true; kotlinx.coroutines.delay(180); wink = false }
        }
    }
    val cols = LocalFlame.current ?: listOf(p.pink, p.flame, p.flame2)
    Canvas(Modifier.size(size).graphicsLayer {
        // Flottement et rebond lus au dessin : aucune recomposition
        val S = this.size.minDimension
        val bounce = jump.value
        translationY = (if (live) sin(bob.value * 6.2832f) * S * .02f else 0f) - bounce * S * .12f
        scaleY = 1f + bounce * .08f; scaleX = 1f - bounce * .05f
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, .92f)
    }.then(if (onTap != null) Modifier.clickable(onClickLabel = "Caresser ${ui.prefs.petName}") {
        Feedback.tick(); if (live) scope.launch { jump.snapTo(1f); jump.animateTo(0f, spring(dampingRatio = .35f, stiffness = 400f)) }; onTap() } else Modifier)) {
        drawPet(pet.stage.key, pet.mood, acc, outfit, cols, this.size.minDimension, wink)
    }
}

private fun DrawScope.drawPet(stage: String, mood: String, acc: Set<String>, outfit: String?, cols: List<Color>, S: Float, wink: Boolean) {
    val cx = S / 2f
    // Ombre au sol (aplat)
    drawOval(Color(0x2E2A1420), Offset(cx - S * .22f, S * .9f), Size(S * .44f, S * .06f))
    if (stage == "egg") {
        val top = S * .28f; val bot = S * .9f
        val egg = Path().apply { moveTo(cx, top); cubicTo(cx + S * .26f, top, cx + S * .3f, bot - S * .12f, cx, bot); cubicTo(cx - S * .3f, bot - S * .12f, cx - S * .26f, top, cx, top); close() }
        drawPath(egg, Color(0xFFFFE2B8))
        clipPath(egg) { drawRect(Color(0xFFFFC98A), Offset(cx - S * .3f, S * .74f), Size(S * .6f, S * .2f)) }
        // Fissure d'où perce la braise
        drawPath(Path().apply { moveTo(cx - S * .2f, S * .58f); lineTo(cx - S * .08f, S * .52f); lineTo(cx, S * .6f); lineTo(cx + S * .09f, S * .5f); lineTo(cx + S * .2f, S * .57f) }, cols[1], style = Stroke(S * .018f))
        face(cx, S * .66f, S * .7f, mood, wink)
        return
    }
    val h = S * when (stage) { "baby" -> .5f; "young" -> .62f; "adult" -> .72f; else -> .76f }; val w = h * .78f
    val bot = S * .9f; val top = bot - h
    val body = Path().apply {
        moveTo(cx, top)
        cubicTo(cx + w * .62f, top + h * .34f, cx + w * .56f, bot - h * .02f, cx, bot)
        cubicTo(cx - w * .56f, bot - h * .02f, cx - w * .62f, top + h * .34f, cx, top); close()
    }
    // Ailes de phénix et capes : derrière le corps
    if (stage == "legend") for (sd in listOf(-1f, 1f)) {
        val wing = Path().apply { moveTo(cx + sd * w * .3f, top + h * .55f); cubicTo(cx + sd * w * 1.2f, top + h * .2f, cx + sd * w * 1.15f, top + h * .7f, cx + sd * w * .35f, top + h * .78f); close() }
        drawPath(wing, cols[0])
    }
    if (outfit == "hero" || outfit == "cape") {
        val cape = Path().apply { moveTo(cx - w * .32f, top + h * .42f); lineTo(cx + w * .32f, top + h * .42f); lineTo(cx + w * .66f, bot); lineTo(cx - w * .66f, bot); close() }
        drawPath(cape, if (outfit == "hero") Color(0xFFE8412C) else Color(0xFF3B2F7A))
        if (outfit == "cape") for (i in 0 until 5) drawCircle(listOf(Color(0xFF3DFF8A), Color(0xFF7DF9FF), Color(0xFFB98CFF))[i % 3], h * .018f, Offset(cx + (i - 2) * w * .26f, bot - h * (.08f + (i % 2) * .1f)))
    }
    // Corps en aplats : flamme, puis ventre clair
    drawPath(body, cols[1])
    clipPath(body) { drawOval(cols[0], Offset(cx - w * .7f, top - h * .1f), Size(w * .8f, h * .7f)) }
    drawOval(Color(0xFFFFF0C2), Offset(cx - w * .26f, bot - h * .48f), Size(w * .52f, h * .44f))
    // Tenue sur le bas du corps (découpée à la silhouette)
    if (outfit != null) clipPath(body) { outfitBody(outfit, cx, top, bot, w, h) }
    val fy = bot - h * .42f; face(cx, fy, h, mood, wink)
    val headY = top + h * .3f
    if (outfit != null) outfitHead(outfit, cx, top, fy, w, h)
    // Accessoires
    if ("scarf" in acc) { drawRoundRect(Color(0xFF2E6FD8), Offset(cx - w * .42f, fy + h * .15f), Size(w * .84f, h * .09f), CornerRadius(h * .04f)); drawRoundRect(Color(0xFF2E6FD8), Offset(cx + w * .1f, fy + h * .18f), Size(w * .12f, h * .22f), CornerRadius(h * .03f)) }
    if ("bow" in acc) { val by = fy + h * .2f; drawPath(Path().apply { moveTo(cx, by); lineTo(cx - w * .2f, by - h * .06f); lineTo(cx - w * .2f, by + h * .06f); close(); moveTo(cx, by); lineTo(cx + w * .2f, by - h * .06f); lineTo(cx + w * .2f, by + h * .06f); close() }, Color(0xFFE8412C)); drawCircle(Color(0xFFB3121F), h * .03f, Offset(cx, by)) }
    if ("glasses" in acc) { val gy = fy - h * .06f; for (sd in listOf(-1f, 1f)) drawRoundRect(Color(0xFF15161C), Offset(cx + sd * w * .2f - w * .14f, gy - h * .05f), Size(w * .28f, h * .11f), CornerRadius(h * .04f)); drawLine(Color(0xFF15161C), Offset(cx - w * .06f, gy), Offset(cx + w * .06f, gy), h * .02f) }
    if ("headphones" in acc) { drawArc(Color(0xFF2A2E36), 200f, 140f, false, Offset(cx - w * .5f, headY - h * .12f), Size(w, h * .6f), style = Stroke(h * .04f)); for (sd in listOf(-1f, 1f)) drawRoundRect(Color(0xFFE8412C), Offset(cx + sd * w * .46f - w * .08f, headY + h * .1f), Size(w * .16f, h * .18f), CornerRadius(h * .05f)) }
    if ("beret" in acc) { drawOval(Color(0xFF8C1F3F), Offset(cx - w * .36f, top - h * .02f), Size(w * .72f, h * .16f)); drawCircle(Color(0xFF8C1F3F), h * .025f, Offset(cx, top - h * .02f)) }
    if ("crown" in acc) { val cy0 = top + h * .02f; drawPath(Path().apply { moveTo(cx - w * .28f, cy0 + h * .06f); lineTo(cx - w * .28f, cy0 - h * .08f); lineTo(cx - w * .14f, cy0 - h * .01f); lineTo(cx, cy0 - h * .12f); lineTo(cx + w * .14f, cy0 - h * .01f); lineTo(cx + w * .28f, cy0 - h * .08f); lineTo(cx + w * .28f, cy0 + h * .06f); close() }, Color(0xFFFFC53D)) }
    if ("party" in acc) { val cy0 = top + h * .04f; drawPath(Path().apply { moveTo(cx - w * .18f, cy0); lineTo(cx + w * .02f, cy0 - h * .3f); lineTo(cx + w * .2f, cy0); close() }, Color(0xFF7B5CFF)); drawCircle(Color(0xFFFFD23F), h * .035f, Offset(cx + w * .02f, cy0 - h * .3f)) }
    if ("cap" in acc) { val cy0 = top + h * .08f; drawRoundRect(Color(0xFF1E2A44), Offset(cx - w * .3f, cy0 - h * .1f), Size(w * .6f, h * .14f), CornerRadius(h * .05f)); drawRoundRect(Color(0xFF1E2A44), Offset(cx - w * .02f, cy0 + h * .01f), Size(w * .42f, h * .04f), CornerRadius(h * .02f)); drawCircle(Color(0xFFFFD27A), h * .025f, Offset(cx, cy0 - h * .03f)) }
    // Fier : petites étoiles à quatre branches autour de lui
    if (mood == "proud") for ((dx, dy, r) in listOf(Triple(-.62f, .2f, .05f), Triple(.6f, .1f, .04f), Triple(.55f, .5f, .03f))) sparkle(cx + dx * w, top + dy * h, r * h, Color(0xFFFFC53D))
    // Fatigué : « z » qui flottent
    if (mood == "tired") for ((i, k) in listOf(.05f, .09f).withIndex()) { val x = cx + w * (.45f + i * .16f); val y = top + h * (.12f - i * .14f); val z = h * k
        drawPath(Path().apply { moveTo(x, y); lineTo(x + z, y); lineTo(x, y + z); lineTo(x + z, y + z) }, Color(0xFF6A5A8A), style = Stroke(h * .018f)) }
}

/** Étoile à quatre branches en aplat. */
private fun DrawScope.sparkle(x: Float, y: Float, r: Float, c: Color) {
    drawPath(Path().apply { moveTo(x, y - r); lineTo(x + r * .28f, y - r * .28f); lineTo(x + r, y); lineTo(x + r * .28f, y + r * .28f); lineTo(x, y + r); lineTo(x - r * .28f, y + r * .28f); lineTo(x - r, y); lineTo(x - r * .28f, y - r * .28f); close() }, c)
}

/** Tenue : la partie qui habille le corps (dessinée découpée à la silhouette). */
private fun DrawScope.outfitBody(o: String, cx: Float, top: Float, bot: Float, w: Float, h: Float) {
    val y0 = bot - h * .34f
    fun lower(c: Color) = drawRect(c, Offset(cx - w, y0), Size(w * 2, h * .4f))
    when (o) {
        "sport" -> { lower(Color(0xFF2E6FD8)); drawRect(Color.White, Offset(cx - w, y0 + h * .05f), Size(w * 2, h * .03f)) }
        "pyjama" -> { lower(Color(0xFF4D5BD8)); for (i in 0 until 6) sparkle(cx - w * .45f + i * w * .18f, y0 + h * (.1f + (i % 2) * .1f), h * .025f, Color(0xFFFFE45C)) }
        "pull" -> { lower(Color(0xFFD7263D)); val z = Path(); z.moveTo(cx - w, y0 + h * .1f); var x = cx - w; var up = true; while (x < cx + w) { x += w * .1f; z.lineTo(x, y0 + h * if (up) .06f else .12f); up = !up }
            drawPath(z, Color.White, style = Stroke(h * .02f)) }
        "kimono" -> { lower(Color(0xFFFF8FB1)); drawPath(Path().apply { moveTo(cx - w * .3f, y0); lineTo(cx, y0 + h * .16f); lineTo(cx + w * .3f, y0) }, Color.White, style = Stroke(h * .035f))
            drawRect(Color(0xFF8C1F3F), Offset(cx - w, y0 + h * .17f), Size(w * 2, h * .05f)) }
        "astro" -> { lower(Color(0xFFE6ECF5)); drawRoundRect(Color(0xFFE8412C), Offset(cx - w * .12f, y0 + h * .06f), Size(w * .24f, h * .08f), CornerRadius(h * .02f)) }
        "hawaii" -> { lower(Color(0xFFFF7A3D)); for (i in 0 until 5) drawCircle(Color(0xFFFFE45C), h * .025f, Offset(cx - w * .4f + i * w * .2f, y0 + h * (.08f + (i % 2) * .12f))) }
        "marin" -> { lower(Color.White); for (i in 0 until 4) drawRect(Color(0xFF1E2A5A), Offset(cx - w, y0 + h * (.04f + i * .08f)), Size(w * 2, h * .035f)) }
        "vigneron" -> { drawRoundRect(Color(0xFF7B3FA0), Offset(cx - w * .28f, y0 - h * .04f), Size(w * .56f, h * .42f), CornerRadius(h * .04f))
            for (i in 0 until 3) drawCircle(Color(0xFF3DFF8A), h * .022f, Offset(cx - w * .06f + i * w * .06f, y0 + h * .1f)) }
        "cire" -> lower(Color(0xFFFFC53D))
        "hero" -> drawPath(Path().apply { moveTo(cx - w * .14f, y0); lineTo(cx + w * .14f, y0); lineTo(cx, y0 + h * .14f); close() }, Color(0xFFFFD23F))
        "cape" -> drawRect(Color(0xFF3B2F7A), Offset(cx - w, y0 + h * .16f), Size(w * 2, h * .04f))
    }
}

/** Tenue : ce qui se porte sur la tête (bandeau, bonnet, capuche, casque). */
private fun DrawScope.outfitHead(o: String, cx: Float, top: Float, fy: Float, w: Float, h: Float) {
    when (o) {
        "sport" -> drawRoundRect(Color(0xFFE8412C), Offset(cx - w * .34f, fy - h * .2f), Size(w * .68f, h * .06f), CornerRadius(h * .03f))
        "pyjama" -> { drawPath(Path().apply { moveTo(cx - w * .3f, top + h * .14f); lineTo(cx + w * .3f, top + h * .14f); lineTo(cx + w * .5f, top - h * .06f); close() }, Color(0xFF4D5BD8)); drawCircle(Color.White, h * .04f, Offset(cx + w * .5f, top - h * .06f)) }
        "pull" -> { drawRoundRect(Color(0xFFD7263D), Offset(cx - w * .3f, top - h * .02f), Size(w * .6f, h * .16f), CornerRadius(h * .07f)); drawCircle(Color.White, h * .045f, Offset(cx, top - h * .03f)) }
        "astro" -> { drawCircle(Color(0xFFBFE6FF).copy(alpha = .35f), h * .36f, Offset(cx, fy - h * .02f)); drawCircle(Color(0xFFE6ECF5), h * .36f, Offset(cx, fy - h * .02f), style = Stroke(h * .03f))
            drawArc(Color.White, 200f, 50f, false, Offset(cx - h * .28f, fy - h * .3f), Size(h * .56f, h * .56f), style = Stroke(h * .02f)) }
        "hawaii" -> for (i in 0 until 5) drawCircle(listOf(Color(0xFFFF4F8B), Color(0xFFFFE45C))[i % 2], h * .035f, Offset(cx - w * .3f + i * w * .15f, top + h * .12f))
        "cire" -> drawArc(Color(0xFFFFC53D), 180f, 180f, true, Offset(cx - w * .4f, top - h * .04f), Size(w * .8f, h * .44f))
        "kimono" -> drawCircle(Color(0xFFFF4F8B), h * .05f, Offset(cx + w * .26f, top + h * .14f))
        "marin" -> { drawRoundRect(Color(0xFF1E2A5A), Offset(cx - w * .26f, top), Size(w * .52f, h * .1f), CornerRadius(h * .03f)); drawCircle(Color(0xFFE8412C), h * .025f, Offset(cx, top)) }
        "vigneron" -> drawOval(Color(0xFFC9A87A), Offset(cx - w * .42f, top + h * .02f), Size(w * .84f, h * .1f))
        else -> {}
    }
}

/** Yeux, joues et bouche selon l'humeur ; [wink] ferme l'œil droit un instant. */
private fun DrawScope.face(cx: Float, y: Float, h: Float, mood: String, wink: Boolean = false) {
    val ink = Color(0xFF2A1420); val e = h * .07f
    for (sd in listOf(-1f, 1f)) {
        val ex = cx + sd * h * .13f
        when {
            mood == "proud" -> drawArc(ink, 200f, 140f, false, Offset(ex - e * .6f, y - e * .4f), Size(e * 1.2f, e * 1.1f), style = Stroke(h * .022f))
            mood == "tired" -> { drawLine(ink, Offset(ex - e * .7f, y), Offset(ex + e * .7f, y), h * .022f); drawArc(ink, 20f, 140f, false, Offset(ex - e * .6f, y - e * .5f), Size(e * 1.2f, e * 1f), style = Stroke(h * .018f)) }
            wink && sd > 0 -> drawArc(ink, 20f, 140f, false, Offset(ex - e * .6f, y - e * .6f), Size(e * 1.2f, e * 1f), style = Stroke(h * .022f))
            else -> { drawOval(ink, Offset(ex - e * .5f, y - e * .7f), Size(e, e * 1.4f)); drawCircle(Color.White, e * .22f, Offset(ex + e * .12f, y - e * .25f)) }
        }
        drawOval(Color(0x8CFF6F9E), Offset(ex - e * .7f + sd * e * .4f, y + e * .9f), Size(e * 1.3f, e * .7f))
    }
    val my = y + h * .12f
    when (mood) {
        "proud" -> drawArc(ink, 0f, 180f, true, Offset(cx - h * .1f, my - h * .06f), Size(h * .2f, h * .14f))
        "happy" -> drawArc(ink, 10f, 160f, false, Offset(cx - h * .09f, my - h * .06f), Size(h * .18f, h * .12f), style = Stroke(h * .025f))
        "calm" -> drawLine(ink, Offset(cx - h * .05f, my), Offset(cx + h * .05f, my), h * .022f)
        else -> drawArc(ink, 200f, 140f, false, Offset(cx - h * .06f, my - h * .01f), Size(h * .12f, h * .08f), style = Stroke(h * .022f))
    }
}

/** Carte du profil : le compagnon, son stade et l'accès à sa page. */
@Composable
fun PetCard(ui: AppState) {
    val p = LocalPal.current; val pet = rememberPet(ui)
    Glass(onClick = { ui.push(Screen.Pet) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PetView(ui, 84.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("Ton compagnon")
                Text("${ui.prefs.petName} · ${pet.stage.name}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Muted("Humeur : ${PET_MOODS[pet.mood] ?: ""}", small = true)
                pet.next?.let { n -> Bar(pet.progress * 100, height = 6.dp); Muted("Vers « ${n.name} » : ${petNextHint(pet)}", small = true) } ?: Muted("Stade ultime atteint 👑", small = true)
            }
            Text("›", color = p.ink3, fontSize = 22.sp)
        }
    }
}

/** Page du compagnon : grand dessin, nom, progression et accessoires (boutique). */
@Composable
fun PetScreen(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return; val pet = rememberPet(ui)
    var line by remember(pet.mood) { mutableStateOf(PET_LINES[pet.mood]!!.random()) }
    var name by remember { mutableStateOf(ui.prefs.petName) }
    var worn by remember { mutableStateOf(ui.prefs.petAcc) }
    ScreenFrame(ui, "Compagnon", "Retour", { ui.back() }, icon = "flameo", color = p.flame2, color2 = p.type("monthly").c, emoji = "🐣") {
        item("pet") {
            Glass {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PetView(ui, 220.dp) { line = PET_LINES[pet.mood]!!.random() } }
                Text("« $line »", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = p.ink2)
                Spacer(Modifier.height(10.dp))
                Field(name, { name = it.take(16); if (it.isNotBlank()) ui.prefs.petName = it.trim().take(16) }, "Son nom", max = 16, icon = "edit")
            }
        }
        item("stage") {
            Glass {
                CardHead("${pet.stage.name}") { Count(PET_MOODS[pet.mood] ?: "") }
                val idx = PET_STAGES.indexOf(pet.stage)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PET_STAGES.forEachIndexed { i, s -> val ok = i <= idx
                        val cur = s.key == pet.stage.key
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(17.dp)).background(if (cur) vivid(p.flame) else p.surface2).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (ok) "✓" else "${s.streak} j", fontWeight = FontWeight.ExtraBold, color = if (cur) fr.pepsgoal.app.ui.theme.PLUM else if (ok) p.ink else p.ink3, fontSize = 12.sp)
                            FitText(s.name, size = 12, min = 8, color = if (cur) fr.pepsgoal.app.ui.theme.PLUM else if (ok) p.ink else p.ink3) } }
                }
                Spacer(Modifier.height(8.dp))
                pet.next?.let { Bar(pet.progress * 100, height = 8.dp); Spacer(Modifier.height(6.dp)) }
                Muted("Il grandit avec ta meilleure série (${pl(pet.best, "jour", "jours")}) et ton niveau (${pet.level}) : le premier des deux qui atteint le seuil le fait passer au stade suivant.", small = true)
                petNextHint(pet)?.let { Muted("Encore $it.", small = true) }
                Muted("Son humeur suit ta journée et ton journal : fier d’une journée parfaite, content quand ta flamme brille, fatigué les jours difficiles.", small = true)
            }
        }
        item("outfit") { OutfitsCard(ui) }
        item("acc") {
            Glass {
                CardHead("Accessoires", emoji = "🎀") { Count("${pg.wallet(u)} 🔥") }
                PET_ACCESSORIES.chunked(2).forEach { row ->
                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { a ->
                            val owned = pg.owns("acc:${a.key}", a.price, u); val on = a.key in worn
                            val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            val press = rememberPress(inter)
                            Row(Modifier.weight(1f).chunk(if (on) p.selTint(p.type("routine").c) else p.surface, if (on) vivid(p.type("routine").c).step() else p.edge, 22.dp, 3.dp, if (on) 3.dp else 2.dp, press).clip(RoundedCornerShape(22.dp))
                                .springClick(inter, label = a.name) {
                                    if (owned) { worn = if (on) worn - a.key else worn + a.key; ui.prefs.petAcc = worn; ui.bump() }
                                    else pg.buy("acc:${a.key}", a.price)?.let { ui.toast(it, "x") } ?: run { Feedback.success(); worn = worn + a.key; ui.prefs.petAcc = worn; ui.toast("${a.emoji} ${a.name} pour ${ui.prefs.petName} !", "star"); ui.bump() }
                                }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Emoji(a.emoji, 26.dp)
                                Column(Modifier.weight(1f)) { Text(a.name, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1); Text(if (owned) (if (on) "Porté" else "Possédé") else "${a.price} 🔥", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink2) }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Tenues du compagnon : boutique (flammes) ou passe de saison ; une seule portée à la fois. */
@Composable
private fun OutfitsCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    var cur by remember { mutableStateOf(ui.prefs.petOutfit) }
    val season = themeSeasonAt()
    Glass {
        CardHead("Tenues", emoji = "👕") { Count("${pg.wallet(u)} 🔥") }
        Muted("Une tenue à la fois, par-dessus les accessoires. Celles des saisons se gagnent avec le passe.", small = true)
        Spacer(Modifier.height(6.dp))
        PET_OUTFITS.chunked(2).forEach { row ->
            Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { o ->
                    val owned = pg.ownsItem("outfit:${o.key}"); val on = cur == o.key && owned
                    val st = THEME_SEASONS.firstOrNull { it.key == o.season }
                    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val press = rememberPress(inter)
                    Row(Modifier.weight(1f).chunk(if (on) p.selTint(p.type("monthly").c) else p.surface, if (on) vivid(p.type("monthly").c).step() else p.edge, 22.dp, 3.dp, if (on) 3.dp else 2.dp, press).clip(RoundedCornerShape(22.dp))
                        .springClick(inter, label = o.name) {
                            when {
                                owned -> { cur = if (on) "" else o.key; ui.prefs.petOutfit = cur; ui.bump() }
                                o.price > 0 -> pg.buy("outfit:${o.key}", o.price)?.let { ui.toast(it, "x") } ?: run { Feedback.success(); cur = o.key; ui.prefs.petOutfit = o.key; ui.toast("${o.emoji} ${o.name} pour ${ui.prefs.petName} !", "star"); ui.bump() }
                                else -> ui.toast(if (st?.key == season.theme.key) "À gagner dans le passe de la saison « ${st.name} »." else "Tenue de la saison « ${st?.name ?: ""} » : elle reviendra avec elle.", "star")
                            }
                        }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Emoji(o.emoji, 26.dp)
                        Column(Modifier.weight(1f)) {
                            Text(o.name, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 2)
                            Text(when { on -> "Portée"; owned -> "Possédée"; o.price > 0 -> "${o.price} 🔥"; else -> "Passe ${st?.emoji ?: ""}" }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink2)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/* ------------------------------------------------------------------ Quêtes */

/** Carte de la quête en cours : progression et chapitre du jour à ouvrir. */
@Composable
fun QuestCard(ui: AppState) {
    val p = LocalPal.current; val st = remember(ui.rev) { ui.pg.questState() } ?: return
    Glass(onClick = { ui.push(Screen.Quest) }, pad = 0.dp) {
        Box(Modifier.fillMaxWidth().height(86.dp)) {
            SceneBg(st.quest.scene, Modifier.fillMaxSize(), still = true)
            // Titre en plaque sur la scène (plus de dégradé noir)
            Column(Modifier.align(Alignment.CenterStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniPlaque(if (st.finished) "Quêtes terminées" else "Quête · chapitre ${minOf(st.done + 1, 7)}/7")
                Plaque { Emoji(st.quest.emoji, 20.dp); Text(st.quest.title, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Bar(st.done * 100.0 / st.quest.chapters.size, Modifier.weight(1f), height = 10.dp)
            if (st.canUnlock) Chip("Nouveau chapitre !", "sparkle", p.flame) else Muted(if (st.finished) "Bravo !" else "Valide un objectif pour la suite", small = true)
        }
    }
}

/** Page de la quête : paysage, chapitres lus, chapitre du jour à ouvrir. */
@Composable
fun QuestScreen(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg
    val st = pg.questState() ?: return
    var fresh by remember { mutableIntStateOf(0) }
    ScreenFrame(ui, "Quête", "Retour", { ui.back() }, icon = "book", color = p.type("period").c, color2 = p.type("period").c, emoji = "🗺️") {
        item("hero") {
            Glass(pad = 0.dp) {
                Box(Modifier.fillMaxWidth().height(170.dp)) { SceneBg(st.quest.scene, Modifier.fillMaxSize(), still = true)
                    Plaque(Modifier.align(Alignment.BottomStart).padding(12.dp)) { Emoji(st.quest.emoji, 22.dp); Text(st.quest.title, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 20.sp) } }
                Column(Modifier.padding(16.dp)) {
                    Muted("Chaque journée active (au moins une validation) ouvre le chapitre suivant, un par jour. Fin de l’histoire : +${st.quest.reward} flammes.", small = true)
                    if (st.canUnlock) { Spacer(Modifier.height(10.dp)); PButton("Lire le chapitre ${st.done + 1}", { pg.unlockChapter()?.let { n -> fresh = n; Feedback.success() } }, Modifier.fillMaxWidth(), icon = "book", kind = "primary") }
                    else if (!st.finished) { Spacer(Modifier.height(6.dp)); Muted(if (pg.activeDays(pg.meId()).contains(today())) "Chapitre du jour lu. La suite demain !" else "Valide un objectif aujourd’hui pour débloquer la suite.", small = true) }
                }
            }
        }
        st.quest.chapters.forEachIndexed { i, (title, text) ->
            item("c$i") {
                val open = i < maxOf(st.done, fresh)
                Glass(border = if (fresh == i + 1) p.flame else null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Étape cochée quand le chapitre est lu
                        PCheckbox(open, p.type("period").c)
                        Text(if (open) "${i + 1}. $title" else "${i + 1}. Chapitre verrouillé", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, color = if (open) p.ink else p.ink3)
                        if (!open) Pi("lock", size = 16.dp, tint = p.ink3)
                    }
                    if (open) { Spacer(Modifier.height(8.dp)); Text(text, lineHeight = 22.sp) }
                }
            }
        }
        val done = pg.questsDone()
        if (done.isNotEmpty()) item("done") { Hint("Quêtes terminées : ${done.joinToString { "${it.emoji} ${it.title}" }}") }
    }
}

/* ------------------------------------------------------------------ Succès cachés */

@Composable
fun SecretsCard(ui: AppState) {
    val p = LocalPal.current; val found = remember(ui.rev) { ui.pg.secretsFound() }
    Glass {
        CardHead("Succès cachés", emoji = "🔒") { Count("${found.size} / ${SECRETS.size}") }
        Muted("Des badges secrets à débusquer (+$SECRET_BONUS flammes chacun). Seul un indice les trahit…", small = true)
        Spacer(Modifier.height(10.dp))
        SECRETS.chunked(3).forEach { row ->
            Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { s -> val ok = s.key in found
                    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val press = rememberPress(inter)
                    Column(Modifier.weight(1f).chunk(if (ok) p.selTint(p.flame) else p.surface2, if (ok) p.focusEdge else p.edge, 22.dp, 3.dp, 2.dp, press).clip(RoundedCornerShape(20.dp))
                        .springClick(inter, label = if (ok) s.name else "Indice") { ui.toast(if (ok) "${s.emoji} ${s.name} : ${s.desc}" else "Indice : ${s.hint}", if (ok) "star" else "lock") }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (ok) Emoji(s.emoji, 30.dp) else Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { Pi("lock", size = 20.dp, tint = p.ink3) }
                        FitText(if (ok) s.name else "???", size = 12, min = 8, color = if (ok) p.ink else p.ink3, weight = FontWeight.ExtraBold)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/* ------------------------------------------------------------------ Saisons de ligue */

/** Saison du mois : division actuelle, jours restants, historique des saisons ; clôt la saison passée à l'ouverture. */
@Composable
fun SeasonCard(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    LaunchedEffect(Unit) { try { ui.cloud.closeLeagueSeason()?.let { r -> ui.bump(); val (dn, de, _) = LeagueSeason.DIVISIONS[(r.division + r.move).coerceIn(0, LeagueSeason.DIVISIONS.lastIndex)]
        ui.toast(when (r.move) { 1 -> "Saison terminée : promotion en $dn $de !"; -1 -> "Saison terminée : retour en $dn $de."; else -> "Saison terminée : maintien en $dn $de." }, "trophy") } } catch (e: Exception) {} }
    val div = LeagueSeason.division(u.shopClaimed); val (dn, de, dc) = LeagueSeason.DIVISIONS[div]
    val hist = LeagueSeason.history(u.shopClaimed).takeLast(6)
    val d = todayDate(); val left = d.lengthOfMonth() - d.dayOfMonth
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EmojiTile(de, hex(dc), 60.dp)
            Column(Modifier.weight(1f)) {
                Eyebrow("Saison de ${Recaps.MONTHS_LONG[d.monthValue - 1]}")
                Text("Division $dn", fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Muted("${pl(left, "jour restant", "jours restants")} · premier tiers promu, dernier tiers relégué", small = true)
            }
        }
        if (hist.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                hist.forEach { r -> val (_, e, _) = LeagueSeason.DIVISIONS[(r.division + r.move).coerceIn(0, LeagueSeason.DIVISIONS.lastIndex)]; val m = r.season.substring(5).toInt()
                    Column(Modifier.clip(RoundedCornerShape(17.dp)).background(p.surface2).padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Emoji(e, 18.dp); Text(Recaps.MONTHS_SHORT[m - 1] + when (r.move) { 1 -> " ↑"; -1 -> " ↓"; else -> "" }, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = when (r.move) { 1 -> p.ok; -1 -> p.danger; else -> p.ink2 })
                    } }
            }
        }
    }
}
