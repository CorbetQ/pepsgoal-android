package fr.pepsgoal.app.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import fr.pepsgoal.app.BuildConfig
import fr.pepsgoal.app.peps
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * IA 100 % locale de PepsGoal : un petit modèle de langage (famille Qwen 2.5, format MediaPipe .task) téléchargé
 * une seule fois puis exécuté sur le téléphone. Aucune donnée ne quitte l'appareil : seul le fichier du modèle
 * est téléchargé (depuis Hugging Face) au moment de l'installation.
 */
object LocalAi {
    /** Modèles proposés : « léger » (≈ 0,5 Go, 4 Go de RAM) ou « avancé » (≈ 1,6 Go, 8 Go de RAM, meilleur français). */
    class Variant(val key: String, val label: String, val repos: List<String>, val approxMb: Int, val minRamGb: Double)
    val VARIANTS = listOf(
        Variant("light", "Léger", listOf("litert-community/Qwen2.5-0.5B-Instruct"), 560, 3.5),
        Variant("advanced", "Avancé", listOf("litert-community/Qwen2.5-1.5B-Instruct"), 1600, 7.0),
    )

    /** Résultat d'un point de contrôle de compatibilité. */
    class Check(val label: String, val detail: String, val ok: Boolean)

    // État observable par l'interface
    var status by mutableStateOf("unknown") // unknown, absent, downloading, ready, error
    var progress by mutableFloatStateOf(0f)
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var speed by mutableStateOf<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val lock = Mutex()
    private var engine: LlmInference? = null
    private var idleJob: Job? = null
    private var download: Job? = null

    private fun dir(ctx: Context) = File(ctx.filesDir, "ai").apply { mkdirs() }
    fun modelFile(ctx: Context) = File(dir(ctx), "model.task")
    private fun partFile(ctx: Context) = File(dir(ctx), "model.part")

    fun refresh(ctx: Context) {
        if (status == "downloading") return
        status = if (modelFile(ctx).exists() && modelFile(ctx).length() > 50_000_000) "ready" else "absent"
    }

    fun installed(ctx: Context) = modelFile(ctx).let { it.exists() && it.length() > 50_000_000 }

    /** L'IA est-elle installée et activée (et la fonction demandée aussi) ? */
    fun on(ctx: Context, feature: Boolean = true) = ctx.peps.prefs.aiEnabled && feature && installed(ctx)

    /* ------------------------------------------------------------------ compatibilité */

    fun ramGb(ctx: Context): Double {
        val mi = ActivityManager.MemoryInfo(); ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        return mi.totalMem / 1_073_741_824.0
    }

    fun freeGb(ctx: Context): Double = StatFs(ctx.filesDir.path).availableBytes / 1_073_741_824.0

    /** Vérifie que le téléphone peut faire tourner le modèle choisi, avant tout téléchargement. */
    fun checks(ctx: Context, v: Variant): List<Check> {
        val arm = Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")
        val ram = ramGb(ctx); val free = freeGb(ctx); val need = v.approxMb / 1024.0 + .4
        return listOf(
            Check("Processeur 64 bits (ARM64)", if (arm) "Compatible" else "Ce processeur (${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}) ne peut pas exécuter le modèle", arm),
            Check("Mémoire vive", "%.1f Go (%.0f Go minimum pour ce modèle)".format(ram, v.minRamGb), ram >= v.minRamGb),
            Check("Espace de stockage libre", "%.1f Go (%.1f Go nécessaires)".format(free, need), free >= need || installed(ctx)),
            Check("Version d’Android", "Android ${Build.VERSION.RELEASE}", Build.VERSION.SDK_INT >= 26),
        )
    }

    fun compatible(ctx: Context, v: Variant) = checks(ctx, v).all { it.ok }

    fun variant(ctx: Context) = VARIANTS.firstOrNull { it.key == ctx.peps.prefs.aiModel } ?: VARIANTS[0]

    /* ------------------------------------------------------------------ installation */

    /** Nom du dernier fichier choisi : sa taille de contexte (« ekv1280 ») règle la longueur maximale des demandes. */
    private var lastFile = ""

    /** Trouve le fichier .task d'un dépôt Hugging Face public (non restreint) : (adresse, taille). */
    private fun resolve(repo: String): Pair<String, Long>? {
        val r = http.newCall(Request.Builder().url("https://huggingface.co/api/models/$repo?blobs=true").header("User-Agent", "PepsGoal-Android/${BuildConfig.VERSION_NAME}").build()).execute()
        r.use {
            if (!it.isSuccessful) return null
            val o = Json.parseToJsonElement(it.body!!.string()).jsonObject
            val gated = o["gated"]?.jsonPrimitive?.contentOrNull
            if (gated != null && gated != "false") return null
            val files = o["siblings"]?.jsonArray?.map { s -> s.jsonObject }?.filter { s -> s["rfilename"]?.jsonPrimitive?.content?.endsWith(".task") == true } ?: return null
            fun name(s: JsonObject) = s["rfilename"]!!.jsonPrimitive.content
            fun size(s: JsonObject) = s["size"]?.jsonPrimitive?.longOrNull ?: 0L
            // Préférence : quantification 8 bits, contexte ~1280 jetons (rapide), sinon le plus petit fichier
            val pick = files.sortedWith(compareBy({ if (name(it).contains("q8")) 0 else 1 }, { if (name(it).contains("ekv1280")) 0 else 1 }, { size(it) })).firstOrNull() ?: return null
            lastFile = name(pick)
            return "https://huggingface.co/$repo/resolve/main/${name(pick)}" to size(pick)
        }
    }

    /** Télécharge (avec reprise) puis teste le modèle. */
    fun install(ctx: Context) {
        if (download?.isActive == true) return
        val app = ctx.applicationContext; val v = variant(app)
        status = "downloading"; progress = 0f; error = null
        download = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val (url, size) = v.repos.firstNotNullOfOrNull { runCatching { resolve(it) }.getOrNull() } ?: throw IllegalStateException("Modèle introuvable sur Hugging Face pour le moment.")
                    val part = partFile(app); var done = if (part.exists()) part.length() else 0L
                    val req = Request.Builder().url(url).header("User-Agent", "PepsGoal-Android/${BuildConfig.VERSION_NAME}").apply { if (done > 0) header("Range", "bytes=$done-") }.build()
                    http.newCall(req).execute().use { r ->
                        if (r.code == 416) { done = part.length() } else {
                            if (!r.isSuccessful) throw IllegalStateException("Téléchargement impossible (${r.code}).")
                            if (r.code != 206) { part.delete(); done = 0 }
                            val total = if (size > 0) size else (r.body!!.contentLength() + done)
                            r.body!!.byteStream().use { input -> java.io.FileOutputStream(part, true).use { out ->
                                val buf = ByteArray(256 * 1024); var last = 0L
                                while (true) {
                                    ensureActive()
                                    val n = input.read(buf); if (n < 0) break
                                    out.write(buf, 0, n); done += n
                                    if (done - last > 2_000_000) { last = done; val p = (done.toFloat() / total).coerceIn(0f, 1f); withContext(Dispatchers.Main) { progress = p } }
                                }
                            } }
                        }
                    }
                    part.renameTo(modelFile(app))
                    app.peps.prefs.aiCtx = Regex("ekv(\\d+)").find(lastFile)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(512, 4096) ?: 1024
                }
                progress = 1f
                selfTest(app)
                status = "ready"; app.peps.prefs.aiEnabled = true
                AiMemory.add(app, "system", "Assistant IA installé (modèle ${v.label.lowercase()}).")
            } catch (e: CancellationException) { status = if (installed(app)) "ready" else "absent"
            } catch (e: Throwable) { status = if (installed(app)) "ready" else "error"; error = e.message ?: "Installation impossible." }
        }
    }

    fun cancel(ctx: Context) { download?.cancel(); partFile(ctx).delete(); status = "absent"; progress = 0f }

    /** Supprime le modèle (l'IA est désactivée ; la mémoire est conservée sauf si on la vide). */
    fun uninstall(ctx: Context) {
        scope.launch { lock.withLock { withContext(Dispatchers.Default) { engine?.close(); engine = null } }; modelFile(ctx).delete(); partFile(ctx).delete(); ctx.peps.prefs.aiEnabled = false; status = "absent"; speed = null }
    }

    /** Premier chargement et mesure de vitesse : valide que le modèle tourne vraiment sur ce téléphone. */
    private suspend fun selfTest(ctx: Context) {
        val t0 = System.currentTimeMillis()
        val out = generate(ctx, 16, .1f) { "Réponds seulement par le mot : prêt" } ?: throw IllegalStateException("Le modèle n’a pas pu démarrer sur ce téléphone.")
        val s = (System.currentTimeMillis() - t0) / 1000.0
        speed = "Premier test en %.1f s".format(s); ctx.peps.prefs.aiSpeed = speed!!
        if (out.isBlank()) throw IllegalStateException("Le modèle ne répond pas.")
    }

    /* ------------------------------------------------------------------ génération */

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    /** Session d'une génération de fond (conseil, recommandations) : interrompue dès qu'une conversation a besoin du modèle. */
    @Volatile private var bgSession: LlmInferenceSession? = null
    /** Session de la génération en cours, pour l'arrêter proprement (le texte déjà écrit est gardé). */
    @Volatile private var curSession: LlmInferenceSession? = null
    fun stopGeneration() { curSession?.let { try { it.cancelGenerateResponseAsync() } catch (_: Throwable) {} } }

    /** Ancienne forme : une consigne toute faite. */
    suspend fun generate(ctx: Context, prompt: String, maxTokens: Int = 256): String? = generate(ctx, maxTokens) { prompt }

    /**
     * Génère une réponse (null si l'IA n'est pas disponible, si la demande de fond a cédé la place, ou en cas d'erreur).
     * - [build] construit la consigne selon un niveau de détail (0 : complète, 1 : réduite, 2 : minimale) : on garde la
     *   plus complète qui tient dans le contexte du modèle (sinon le modèle refuse la demande et le coach se tait) ;
     * - [temperature] basse pour les réponses à format imposé, plus haute pour la conversation ; graine aléatoire à chaque
     *   appel (deux demandes identiques ne donnent plus la même réponse) ;
     * - [onPartial] reçoit le texte au fil de l'écriture (fil principal) ;
     * - [background] : demande de fond, abandonnée si le modèle est occupé et interrompue si une conversation arrive.
     * Le modèle est chargé à la demande et libéré après deux minutes d'inactivité.
     */
    suspend fun generate(ctx: Context, maxTokens: Int = 256, temperature: Float = .7f, background: Boolean = false,
                         onPartial: ((String) -> Unit)? = null, build: (Int) -> String): String? {
        val f = modelFile(ctx); if (!f.exists()) return null
        if (background && lock.isLocked) return null
        if (!background) bgSession?.let { try { it.cancelGenerateResponseAsync() } catch (_: Throwable) {} }
        return lock.withLock {
            busy = true
            try {
                withContext(Dispatchers.Default) {
                    val maxCtx = ctx.peps.prefs.aiCtx.coerceIn(512, 4096)
                    val e = engine ?: LlmInference.createFromOptions(ctx.applicationContext,
                        LlmInference.LlmInferenceOptions.builder().setModelPath(f.path).setMaxTokens(maxCtx).build()).also { engine = it }
                    val out = maxTokens.coerceIn(16, maxCtx / 2)
                    val prompt = fit(e, maxCtx - out - 24, build)
                    val opts = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTemperature(temperature).setTopK(40).setTopP(.95f).setRandomSeed(kotlin.random.Random.nextInt(1, Int.MAX_VALUE)).build()
                    val s = LlmInferenceSession.createFromOptions(e, opts)
                    if (background) bgSession = s
                    curSession = s
                    try {
                        s.addQueryChunk(prompt)
                        withTimeoutOrNull(90_000) { stream(s, onPartial) }?.let { clean(fixEncoding(it)) }?.take(out * 6)?.trim()?.takeIf { it.isNotEmpty() }
                    } finally { if (bgSession === s) bgSession = null; if (curSession === s) curSession = null }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Throwable) { error = e.message; null }
            finally {
                busy = false
                idleJob?.cancel(); idleJob = scope.launch { delay(120_000); lock.withLock { withContext(Dispatchers.Default) { engine?.close(); engine = null } } }
            }
        }
    }

    /** Consigne la plus détaillée qui tient dans [budget] jetons ; en dernier recours, le milieu est coupé. */
    private fun fit(e: LlmInference, budget: Int, build: (Int) -> String): String {
        fun size(t: String) = try { e.sizeInTokens(t) } catch (_: Throwable) { t.length / 3 }
        var p = build(0)
        for (lvl in 0..2) { p = build(lvl); if (size(p) <= budget) return p }
        val keep = (budget * 3).coerceAtLeast(400)
        return p.take(keep / 3) + "\n…\n" + p.takeLast(keep * 2 / 3)
    }

    /** Génération en continu : les morceaux arrivent au fil de l'eau ; la session est libérée une fois terminée. */
    private suspend fun stream(s: LlmInferenceSession, onPartial: ((String) -> Unit)?): String = suspendCancellableCoroutine { cont ->
        val sb = StringBuilder()
        val fut = s.generateResponseAsync(ProgressListener<String> { part, _ ->
            if (part != null) {
                val t = synchronized(sb) { sb.append(part); sb.toString() }
                if (onPartial != null) { val c = clean(fixEncoding(t)); main.post { onPartial(c) } }
            }
        })
        fut.addListener({
            val r = try { fut.get() } catch (e: Throwable) { null }
            try { s.close() } catch (_: Throwable) {}
            val all = synchronized(sb) { sb.toString() }.ifEmpty { r.orEmpty() }
            if (cont.isActive) { if (all.isEmpty() && r == null) cont.resumeWithException(IllegalStateException("Génération interrompue.")) else cont.resume(all) }
        }, Runnable::run)
        cont.invokeOnCancellation { try { s.cancelGenerateResponseAsync() } catch (_: Throwable) {} }
    }

    /** Retire les jetons de fin de tour et les débuts de réplique que certains modèles répètent. */
    fun clean(s: String): String = s.replace(Regex("<\\|im_end\\|>|<\\|endoftext\\|>|<end_of_turn>|<\\|im_start\\|>(assistant|user)?"), "")
        .replace(Regex("^\\s*(Coach|Assistant)\\s*:\\s*"), "").trimStart()
}

/** Répare le texte UTF-8 mal décodé par certaines versions du moteur (voir [fr.pepsgoal.core.fixMojibake]). */
internal fun fixEncoding(s: String): String = fr.pepsgoal.core.fixMojibake(s)

/** Une entrée de la mémoire de l'IA (ce que l'assistant retient de toi), lisible et effaçable dans les réglages. */
@Serializable
class MemoryItem(val at: Long, val kind: String, val text: String)

/** Mémoire de l'IA : fichier local (jamais synchronisé), 120 entrées au maximum. */
object AiMemory {
    private fun file(ctx: Context) = File(ctx.filesDir, "ai/memory.json")
    private val json = Json { ignoreUnknownKeys = true }
    var rev by mutableStateOf(0)

    fun all(ctx: Context): List<MemoryItem> = try { json.decodeFromString(ListSerializer(MemoryItem.serializer()), file(ctx).readText()) } catch (e: Exception) { emptyList() }

    fun add(ctx: Context, kind: String, text: String) {
        if (!ctx.peps.prefs.aiMemory) return
        val list = (all(ctx) + MemoryItem(System.currentTimeMillis(), kind, text.take(300))).takeLast(120)
        save(ctx, list)
    }

    fun remove(ctx: Context, at: Long) = save(ctx, all(ctx).filter { it.at != at })
    fun clear(ctx: Context) { file(ctx).delete(); rev++ }

    private fun save(ctx: Context, list: List<MemoryItem>) {
        file(ctx).parentFile?.mkdirs(); file(ctx).writeText(json.encodeToString(ListSerializer(MemoryItem.serializer()), list)); rev++
    }

    /**
     * Contexte donné à l'IA : d'abord ce qui est durable (faits retenus : contraintes, préférences, habitudes), puis les
     * réactions aux conseils, puis quelques événements récents.
     */
    fun context(ctx: Context, n: Int = 10): String {
        val all = all(ctx).filter { it.kind != "system" }
        val facts = all.filter { it.kind == "fact" }.takeLast(8)
        val likes = all.filter { it.kind == "tip" }.takeLast(3)
        val rest = all.filter { it.kind != "fact" && it.kind != "tip" }.takeLast((n - facts.size - likes.size).coerceIn(0, 3))
        return (facts + likes + rest).joinToString("\n") { "- ${it.text}" }
    }

    /** Retient un fait durable (« Je travaille de nuit »), sans doublon. Renvoie vrai s'il est nouveau. */
    fun addFact(ctx: Context, text: String): Boolean {
        val t = text.trim().trimEnd('.').replaceFirstChar { it.uppercase() }.take(200)
        if (t.length < 4 || !ctx.peps.prefs.aiMemory) return false
        val norm = t.lowercase().filter { it.isLetterOrDigit() }
        if (all(ctx).any { it.kind == "fact" && it.text.lowercase().filter { c -> c.isLetterOrDigit() } == norm }) return false
        add(ctx, "fact", t); return true
    }
}
