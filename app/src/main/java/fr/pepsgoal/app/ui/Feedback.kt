package fr.pepsgoal.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import fr.pepsgoal.app.PepsApp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Retours tactiles et sonores (Paramètres › Accessibilité › Vibrations et sons) :
 * petit « tic » à chaque appui, vibration franche et carillon à la validation, note grave à l'annulation.
 */
object Feedback {
    private val app get() = PepsApp.instance
    private val vib: Vibrator? by lazy {
        val c = app
        if (Build.VERSION.SDK_INT >= 31) (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }
    private val level get() = try { app.prefs.haptics } catch (e: Exception) { "off" }

    private fun buzz(effect: Int, ms: Long, amp: Int) {
        val v = vib ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 29 && effect >= 0) v.vibrate(VibrationEffect.createPredefined(effect))
            else v.vibrate(VibrationEffect.createOneShot(ms, amp))
        } catch (e: Exception) {}
    }

    /** Appui sur un bouton. */
    fun tap() { when (level) { "light" -> buzz(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_TICK else -1, 8, 40); "strong" -> buzz(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_CLICK else -1, 16, 120) }; if (sound()) tone(listOf(1568.0), 28, .12f) }

    /** Seuil franchi pendant un geste (balayage). */
    fun tick() { if (level != "off") buzz(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_TICK else -1, 10, 60) }

    /** Validation réussie. */
    fun success() { if (level != "off") buzz(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_HEAVY_CLICK else -1, 30, 200); if (sound()) tone(listOf(1046.5, 1318.5, 1568.0), 110, .32f) }

    /** Annulation ou erreur. */
    fun undo() { if (level != "off") buzz(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_DOUBLE_CLICK else -1, 24, 120); if (sound()) tone(listOf(659.3, 523.3), 100, .25f) }

    private fun sound() = try { app.prefs.sounds } catch (e: Exception) { false }

    /** Courtes notes sinusoïdales enchaînées, avec attaque douce et extinction (pas de fichier son à embarquer). */
    /** Bip du minuteur d'intervalles : toujours audible (minuteur lancé exprès), aigu au début de l'effort. */
    fun beep(high: Boolean) {
        tone(if (high) listOf(1318.5, 1568.0) else listOf(880.0), if (high) 120 else 140, .35f)
        if (level != "off") buzz(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_HEAVY_CLICK else -1, if (high) 40 else 20, 200)
    }

    private fun tone(freqs: List<Double>, ms: Int, vol: Float) {
        Thread {
            try {
                val rate = 44100; val n = rate * ms / 1000
                val pcm = ShortArray(n * freqs.size)
                freqs.forEachIndexed { k, f ->
                    for (i in 0 until n) {
                        val t = i.toDouble() / rate
                        val env = minOf(1.0, i / (rate * .004)) * exp(-t * 18)
                        pcm[k * n + i] = (sin(2 * PI * f * t) * env * vol * Short.MAX_VALUE).toInt().toShort()
                    }
                }
                val tr = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
                tr.write(pcm, 0, pcm.size); tr.play()
                Thread.sleep((ms * freqs.size + 80).toLong()); tr.release()
            } catch (e: Exception) {}
        }.start()
    }
}
