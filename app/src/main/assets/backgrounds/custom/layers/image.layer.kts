/** Premier plan détouré de « Mon image » (10.0, PepsGoal Style) : présent seulement si l'adaptation météo est active. */
fun bgFgFile(ctx: android.content.Context) = File(ctx.filesDir, "bg_fg.webp")

/** Décodage hors du fil principal, réduit à la taille de l'écran (une photo de 12 Mpx pèserait près de 50 Mo). */
private fun decodeScreenSized(ctx: android.content.Context, f: File): ImageBitmap? = try {
    if (!f.exists()) null else {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, o)
        val dm = ctx.resources.displayMetrics; val want = max(dm.widthPixels, dm.heightPixels).coerceAtLeast(1)
        var sample = 1; while (max(o.outWidth, o.outHeight) / (sample * 2) >= want) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }
} catch (e: Exception) { null }

@Composable
internal fun CustomBg(stamp: Long) {
    val ctx = LocalContext.current
    val res by produceState<Pair<Boolean, ImageBitmap?>>(false to null, stamp) {
        value = true to kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decodeScreenSized(ctx, bgFile(ctx)) }
    }
    val fg by produceState<ImageBitmap?>(null, stamp) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decodeScreenSized(ctx, bgFgFile(ctx)) }
    }
    if (!res.first) return // décodage en cours : fond uni
    val bmp = res.second
    if (bmp == null) { PopBg(); return }
    // Jamais floutée : le voile dégradé de AppBg (réglable dans Apparence › Ma photo) garde les cartes lisibles
    Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    fg?.let { PhotoWeather(it) }
}

/**
 * Météo d'une photo « PepsGoal Style » : le ciel de la photo prend la teinte du temps qu'il fait (nuit, nuages, pluie),
 * la pluie, la neige ou le brouillard tombent derrière le premier plan détouré, redessiné par-dessus.
 */
@Composable
private fun PhotoWeather(fg: ImageBitmap) {
    val p = LocalPal.current
    val environment = fr.pepsgoal.app.ui.LocalSceneEnvironment.current
    if (environment != null && !p.contrast) {
        val tint = fr.pepsgoal.core.PhotoSky.tint(environment)
        if (tint > .02f) Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = tint }) { weatherSky(environment, .3f) }
        SceneWeather(environment.condition, environment.clouds, environment.wind, environment.freshness, animate = Perf.flags.fx && !p.motion)
    }
    Image(fg, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}
