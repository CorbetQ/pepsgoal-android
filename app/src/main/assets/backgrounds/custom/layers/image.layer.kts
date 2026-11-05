@Composable
internal fun CustomBg(stamp: Long) {
    val ctx = LocalContext.current
    // Décodée hors du fil principal, réduite à la taille de l'écran (une photo de 12 Mpx pèserait près de 50 Mo)
    val res by produceState<Pair<Boolean, ImageBitmap?>>(false to null, stamp) {
        value = true to kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                bgFile(ctx).takeIf { it.exists() }?.let { f ->
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, o)
                    val dm = ctx.resources.displayMetrics; val want = max(dm.widthPixels, dm.heightPixels).coerceAtLeast(1)
                    var sample = 1; while (max(o.outWidth, o.outHeight) / (sample * 2) >= want) sample *= 2
                    BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }
    if (!res.first) return // décodage en cours : fond uni
    val bmp = res.second
    if (bmp == null) { PopBg(); return }
    // Jamais floutée : le voile dégradé de AppBg (réglable dans Apparence › Ma photo) garde les cartes lisibles
    Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}
