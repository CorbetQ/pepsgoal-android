package fr.pepsgoal.app.sys

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.mutableStateMapOf
import fr.pepsgoal.core.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume

/**
 * Transcription des messages vocaux, entièrement sur le téléphone (reconnaissance vocale embarquée d'Android 13 et
 * plus) : l'audio AAC est décodé en PCM puis confié au moteur de reconnaissance, rien ne quitte l'appareil.
 * Le texte est gardé en mémoire et dans les préférences de l'appareil.
 */
object Transcriber {
    /** Texte par message ("" : rien de reconnu). */
    val texts = mutableStateMapOf<String, String>()
    private val mutex = Mutex()
    private val failed = HashSet<String>()

    fun available(ctx: Context) = Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("pepsgoal.transcripts", Context.MODE_PRIVATE)
    fun cached(ctx: Context, id: String): String? = texts[id] ?: prefs(ctx).getString(id, null)?.also { texts[id] = it }

    suspend fun transcribe(ctx: Context, id: String, audio: ByteArray): String? {
        cached(ctx, id)?.let { return it }
        if (!available(ctx) || id in failed) return null
        return mutex.withLock {
            cached(ctx, id)?.let { return@withLock it }
            val pcm = withContext(Dispatchers.IO) { try { decode(ctx, audio) } catch (e: Exception) { null } } ?: run { failed += id; return@withLock null }
            val text = if (Build.VERSION.SDK_INT >= 33) withTimeoutOrNull(60_000) { recognize(ctx, pcm) } else null
            if (text == null) { failed += id; return@withLock null }
            texts[id] = text; prefs(ctx).edit().putString(id, text).apply(); text
        }
    }

    private class Pcm(val data: ByteArray, val rate: Int, val channels: Int)

    /** AAC (m4a) → PCM 16 bits. */
    private fun decode(ctx: Context, audio: ByteArray): Pcm? {
        val f = File(ctx.cacheDir, "tr-${System.nanoTime()}.m4a"); f.writeBytes(audio)
        val ex = MediaExtractor()
        try {
            ex.setDataSource(f.path)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0); codec.start()
            val out = ByteArrayOutputStream(); val info = MediaCodec.BufferInfo()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var inDone = false; var outDone = false
            while (!outDone) {
                if (!inDone) { val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) { val buf = codec.getInputBuffer(i)!!; val n = ex.readSampleData(buf, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true } else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() } } }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o >= 0 -> { val buf = codec.getOutputBuffer(o)!!; val b = ByteArray(info.size); buf.position(info.offset); buf.get(b); out.write(b); codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true }
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { val nf = codec.outputFormat; rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                }
            }
            codec.stop(); codec.release()
            return Pcm(out.toByteArray(), rate, ch)
        } finally { ex.release(); f.delete() }
    }

    /** Reconnaissance embarquée sur un flux PCM (session segmentée : tout l'audio est lu, segment par segment). */
    @androidx.annotation.RequiresApi(33)
    private suspend fun recognize(ctx: Context, pcm: Pcm): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx.applicationContext)
            val pipe = ParcelFileDescriptor.createPipe()
            val parts = mutableListOf<String>()
            var finished = false
            fun finish(v: String?) { if (finished) return; finished = true; try { sr.destroy() } catch (e: Exception) {}; try { pipe[0].close() } catch (e: Exception) {}; if (cont.isActive) cont.resume(v) }
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { finish(if (parts.isNotEmpty()) parts.joinToString(" ") else if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) "" else null) }
                override fun onResults(results: Bundle?) { results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { parts += it }; finish(parts.joinToString(" ")) }
                override fun onSegmentResults(segmentResults: Bundle) { segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { parts += it } }
                override fun onEndOfSegmentedSession() { finish(parts.joinToString(" ")) }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (Lang.en) "en-US" else "fr-FR")
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, pcm.channels)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, pcm.rate)
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            }
            sr.startListening(intent)
            // L'audio est écrit dans le tuyau depuis un autre fil, puis le tuyau est fermé (fin de la session)
            Thread { try { ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(pcm.data) } } catch (e: Exception) {} }.start()
            cont.invokeOnCancellation { finish(null) }
        }
    }
}
