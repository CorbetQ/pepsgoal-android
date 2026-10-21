package fr.pepsgoal.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import fr.pepsgoal.core.DbFiles
import fr.pepsgoal.core.cloud.Secrets
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Bases de l'appareil dans le stockage privé de l'application, écrites de façon atomique. */
class FileDb(context: Context) : DbFiles {
    private val dir = File(context.filesDir, "db").apply { mkdirs() }
    override fun read(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()
    override fun write(name: String, content: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(File(dir, name))) { File(dir, name).writeText(content); tmp.delete() }
    }
    override fun delete(name: String) { File(dir, name).delete() }
}

/**
 * Secrets (jetons de session, clés de chiffrement des conversations) : chiffrés en AES-GCM avec une clé
 * du Keystore Android, qui ne quitte jamais le matériel sécurisé de l'appareil.
 */
class KeystoreSecrets(context: Context) : Secrets {
    private val prefs = context.getSharedPreferences("pepsgoal.secrets", Context.MODE_PRIVATE)
    private val cache = HashMap<String, String?>()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return gen.generateKey()
    }

    @Synchronized override fun get(key: String): String? {
        if (cache.containsKey(key)) return cache[key]
        val raw = prefs.getString(key, null) ?: return null.also { cache[key] = null }
        val v = try {
            val b = Base64.decode(raw, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, 12))
            String(c.doFinal(b, 12, b.size - 12), Charsets.UTF_8)
        } catch (e: Exception) { null }
        cache[key] = v
        return v
    }

    @Synchronized override fun set(key: String, value: String?) {
        cache[key] = value
        if (value == null) { prefs.edit().remove(key).apply(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    companion object { private const val ALIAS = "pepsgoal.secrets.v1" }
}

/** Réglages propres à cet appareil (accessibilité, Material You, image d'arrière-plan…), pour tous les comptes. */
class DevicePrefs(context: Context) {
    private val p = context.getSharedPreferences("pepsgoal.device", Context.MODE_PRIVATE)
    var contrast get() = p.getBoolean("contrast", false); set(v) = p.edit().putBoolean("contrast", v).apply()
    var cvd get() = p.getString("cvd", "") ?: ""; set(v) = p.edit().putString("cvd", v).apply()
    var mono get() = p.getBoolean("mono", false); set(v) = p.edit().putBoolean("mono", v).apply()
    var motion get() = p.getBoolean("motion", false); set(v) = p.edit().putBoolean("motion", v).apply()
    var transp get() = p.getBoolean("transp", false); set(v) = p.edit().putBoolean("transp", v).apply()
    /** Couleurs Material You (palette du fond d'écran) sur toute l'interface. */
    var materialYou get() = p.getBoolean("materialYou", false); set(v) = p.edit().putBoolean("materialYou", v).apply()
    /** Opacité des panneaux en verre givré : « low », « mid » ou « high ». */
    var glassOpacity get() = p.getString("glassOpacity", "mid") ?: "mid"; set(v) = p.edit().putString("glassOpacity", v).apply()
    /** Activités en cours (Live Updates) : minuteur, période en cours, fin de journée. */
    var liveUpdates get() = p.getBoolean("liveUpdates", true); set(v) = p.edit().putBoolean("liveUpdates", v).apply()
    /** Activité en cours masquée par l'utilisateur pour la journée (« type|date »). */
    var liveDismissed get() = p.getString("liveDismissed", "") ?: ""; set(v) = p.edit().putString("liveDismissed", v).apply()
    var bgPalette: List<String> get() = (p.getString("bgPalette", "") ?: "").split(",").filter { it.startsWith("#") }; set(v) = p.edit().putString("bgPalette", v.joinToString(",")).apply()
    var bgStamp get() = p.getLong("bgStamp", 0L); set(v) = p.edit().putLong("bgStamp", v).apply()
    var collapsed: Set<String> get() = p.getStringSet("collapsed", emptySet()) ?: emptySet(); set(v) = p.edit().putStringSet("collapsed", v).apply()
    var seenVersion get() = p.getString("seenVersion", "") ?: ""; set(v) = p.edit().putString("seenVersion", v).apply()
    var notifiedIds: Set<String> get() = p.getStringSet("notified", emptySet()) ?: emptySet(); set(v) = p.edit().putStringSet("notified", v.toList().takeLast(400).toSet()).apply()
    /** Dernière relève réussie des notifications du serveur en arrière-plan (ms). */
    var lastPoll get() = p.getLong("lastPoll", 0L); set(v) = p.edit().putLong("lastPoll", v).apply()
    /** Service de connexion en direct (notifications instantanées application fermée). */
    var instantNotifs get() = p.getBoolean("instantNotifs", true); set(v) = p.edit().putBoolean("instantNotifs", v).apply()
    /** Journal des dernières relèves en arrière-plan (diagnostic). */
    var pollLog: List<String> get() = p.getString("pollLog", "")!!.split("\n").filter { it.isNotBlank() }; set(v) = p.edit().putString("pollLog", v.joinToString("\n")).apply()
    /** Test demandé depuis les réglages : la prochaine relève en arrière-plan envoie une notification de confirmation. */
    var pollTest get() = p.getLong("pollTest", 0L); set(v) = p.edit().putLong("pollTest", v).apply()
    /** Recherche automatique des mises à jour (releases GitHub). */
    var autoUpdate get() = p.getBoolean("autoUpdate", true); set(v) = p.edit().putBoolean("autoUpdate", v).apply()
    var updateChecked get() = p.getLong("updateChecked", 0L); set(v) = p.edit().putLong("updateChecked", v).apply()
    var updateNotified get() = p.getString("updateNotified", "") ?: ""; set(v) = p.edit().putString("updateNotified", v).apply()
    /** Apparence des widgets : "app" (comme l'application), "light", "dark" ou "you" (Material You). */
    var widgetTheme get() = p.getString("widgetTheme", "app") ?: "app"; set(v) = p.edit().putString("widgetTheme", v).apply()
    var widgetHideDone get() = p.getBoolean("widgetHideDone", false); set(v) = p.edit().putBoolean("widgetHideDone", v).apply()
    var voiceRate get() = p.getFloat("voiceRate", 1f); set(v) = p.edit().putFloat("voiceRate", v).apply()
    fun a11yCount() = listOf(contrast, cvd.isNotEmpty(), mono, motion, transp).count { it }
}
