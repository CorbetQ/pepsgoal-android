package fr.pepsgoal.app.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.pepsgoal.app.AppState
import fr.pepsgoal.core.cloud.str
import fr.pepsgoal.core.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File

/** Pièces jointes chiffrées de la messagerie : téléchargées et déchiffrées à la demande, gardées en mémoire. */
object Att {
    val bytes = mutableStateMapOf<String, ByteArray>()
    private val loading = HashSet<String>()

    fun get(ui: AppState, m: Message, a: JsonObject?): ByteArray? {
        bytes[m.id]?.let { return it }
        if (a == null || a.str("id").isEmpty() || !loading.add(m.id)) return null
        ui.scope.launch {
            try { withContext(Dispatchers.IO) { ui.cloud.e2e.attachment(a) }?.let { bytes[m.id] = it } } catch (e: Exception) {}
            finally { loading.remove(m.id) }
        }
        return null
    }

    fun put(id: String, b: ByteArray) { bytes[id] = b }
}

/** Messages vocaux : enregistrement (AAC) avec forme d'onde, lecture avec vitesse réglable. */
object Voice {
    const val MAX_SECS = 120
    const val BARS = 32
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private val levels = mutableListOf<Int>()
    var recording by mutableStateOf(false); private set
    var secs by mutableFloatStateOf(0f); private set
    private var startAt = 0L

    fun start(ctx: Context): Boolean {
        if (recording) return true
        val f = File(ctx.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); r.setAudioEncodingBitRate(48_000); r.setAudioSamplingRate(44_100)
            r.setMaxDuration(MAX_SECS * 1000); r.setOutputFile(f.absolutePath); r.prepare(); r.start()
            rec = r; file = f; levels.clear(); startAt = System.currentTimeMillis(); secs = 0f; recording = true; true
        } catch (e: Exception) { try { r.release() } catch (x: Exception) {}; false }
    }

    /** À appeler régulièrement pendant l'enregistrement : durée et niveau sonore. */
    fun tick(): Boolean {
        val r = rec ?: return false
        secs = (System.currentTimeMillis() - startAt) / 1000f
        try { levels.add(r.maxAmplitude) } catch (e: Exception) {}
        return secs < MAX_SECS
    }

    /** Fin de l'enregistrement : (fichier, durée, forme d'onde) ou null si annulé. */
    fun stop(cancel: Boolean): Triple<ByteArray, Double, List<Int>>? {
        val r = rec ?: return null
        rec = null; recording = false
        val dur = (System.currentTimeMillis() - startAt) / 1000.0
        try { r.stop() } catch (e: Exception) {}
        r.release()
        val f = file; file = null
        if (cancel || f == null || dur < .6) { f?.delete(); return null }
        val data = f.readBytes(); f.delete()
        val top = (levels.maxOrNull() ?: 1).coerceAtLeast(1)
        val wave = if (levels.isEmpty()) List(BARS) { 3 } else List(BARS) { i ->
            val a = i * levels.size / BARS; val b = maxOf(a + 1, (i + 1) * levels.size / BARS)
            maxOf(1, Math.round((levels.subList(a, minOf(b, levels.size)).maxOrNull() ?: 0) * 9f / top))
        }
        return Triple(data, dur, wave)
    }

    // Lecture : un seul message à la fois
    var playing by mutableStateOf<String?>(null); private set
    var progress by mutableFloatStateOf(0f); private set
    var paused by mutableStateOf(false); private set
    var position by mutableFloatStateOf(0f); private set
    private var player: MediaPlayer? = null

    fun toggle(ctx: Context, id: String, data: ByteArray, rate: Float) {
        if (playing == id) { player?.let { if (it.isPlaying) it.pause() else it.start() }; paused = player?.isPlaying != true; return }
        release()
        val f = File(ctx.cacheDir, "play-$id.m4a"); if (!f.exists()) f.writeBytes(data)
        player = MediaPlayer().apply {
            setDataSource(f.absolutePath); prepare()
            playbackParams = PlaybackParams().setSpeed(rate)
            setOnCompletionListener { this@Voice.playing = null; this@Voice.progress = 0f; this@Voice.position = 0f }
            start()
        }
        playing = id; paused = false
    }


    fun seek(id: String, ratio: Float) { if (playing != id) return; player?.let { it.seekTo((it.duration * ratio).toInt()); update() } }

    fun setRate(rate: Float) { player?.let { try { val was = it.isPlaying; it.playbackParams = PlaybackParams().setSpeed(rate); if (!was) it.pause() } catch (e: Exception) {} } }

    fun update() { player?.let { if (it.duration > 0) { progress = it.currentPosition.toFloat() / it.duration; position = it.currentPosition / 1000f } } }

    fun release() { player?.release(); player = null; playing = null; progress = 0f; position = 0f }
}
