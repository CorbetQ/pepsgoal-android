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
    /** Accès direct, pour les petits états techniques (duo, agenda…). */
    val raw: android.content.SharedPreferences get() = p
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
    /** Dernier jour où la surprise d'un jour de fête (ou de 3 h du matin) a été montrée. */
    var eggDay get() = p.getString("eggDay", "") ?: ""; set(v) = p.edit().putString("eggDay", v).apply()
    var owlDay get() = p.getString("owlDay", "") ?: ""; set(v) = p.edit().putString("owlDay", v).apply()
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
    /* Assistant IA local */
    var aiEnabled get() = p.getBoolean("aiEnabled", false); set(v) = p.edit().putBoolean("aiEnabled", v).apply()
    var aiCreate get() = p.getBoolean("aiCreate", true); set(v) = p.edit().putBoolean("aiCreate", v).apply()
    var aiRecommend get() = p.getBoolean("aiRecommend", true); set(v) = p.edit().putBoolean("aiRecommend", v).apply()
    var aiTips get() = p.getBoolean("aiTips", true); set(v) = p.edit().putBoolean("aiTips", v).apply()
    var aiMemory get() = p.getBoolean("aiMemory", true); set(v) = p.edit().putBoolean("aiMemory", v).apply()
    var aiModel get() = p.getString("aiModel", "light") ?: "light"; set(v) = p.edit().putString("aiModel", v).apply()
    var aiSpeed get() = p.getString("aiSpeed", "") ?: ""; set(v) = p.edit().putString("aiSpeed", v).apply()
    var aiTip get() = p.getString("aiTip", "") ?: ""; set(v) = p.edit().putString("aiTip", v).apply()
    var aiTipKey get() = p.getString("aiTipKey", "") ?: ""; set(v) = p.edit().putString("aiTipKey", v).apply()
    var aiRecaps get() = p.getBoolean("aiRecaps", true); set(v) = p.edit().putBoolean("aiRecaps", v).apply()
    var aiFriends get() = p.getBoolean("aiFriends", true); set(v) = p.edit().putBoolean("aiFriends", v).apply()
    /** Dernières recommandations du coach (clé de contexte puis lignes « id|raison »), gardées entre deux ouvertures. */
    var aiPicks get() = p.getString("aiPicks", "") ?: ""; set(v) = p.edit().putString("aiPicks", v).apply()
    var aiFriendPicks get() = p.getString("aiFriendPicks", "") ?: ""; set(v) = p.edit().putString("aiFriendPicks", v).apply()
    var aiPromoHidden get() = p.getBoolean("aiPromoHidden", false); set(v) = p.edit().putBoolean("aiPromoHidden", v).apply()
    /** Personnalité du coach (voir [fr.pepsgoal.app.ai.Persona]), son prénom et la longueur de ses réponses (short, normal, long). */
    var aiPersona get() = p.getString("aiPersona", "kind") ?: "kind"; set(v) = p.edit().putString("aiPersona", v).apply()
    var aiCoachName get() = p.getString("aiCoachName", "Peps") ?: "Peps"; set(v) = p.edit().putString("aiCoachName", v).apply()
    var aiLength get() = p.getString("aiLength", "normal") ?: "normal"; set(v) = p.edit().putString("aiLength", v).apply()
    /** Contexte maximal du modèle installé (jetons), lu dans le nom du fichier téléchargé (« ekv1280 »). */
    var aiCtx get() = p.getInt("aiCtx", 1024); set(v) = p.edit().putInt("aiCtx", v).apply()
    /** Onglet Objectifs : affichage ("list" compact ou "cards") et regroupement par groupe d'objectifs. */
    var goalView get() = p.getString("goalView", "list") ?: "list"; set(v) = p.edit().putString("goalView", v).apply()
    var goalGroupBy get() = p.getBoolean("goalGroupBy", true); set(v) = p.edit().putBoolean("goalGroupBy", v).apply()
    var smartReminders get() = p.getBoolean("smartReminders", true); set(v) = p.edit().putBoolean("smartReminders", v).apply()
    /** Liens Santé (Health Connect) : lignes « objectif|mesure|seuil ». */
    var healthLinks get() = p.getString("healthLinks", "") ?: ""; set(v) = p.edit().putString("healthLinks", v).apply()
    var healthLast get() = p.getString("healthLast", "") ?: ""; set(v) = p.edit().putString("healthLast", v).apply()
    /** Mode Focus : fin de la session en cours (ms), objectif lié, durée (minutes). */
    var focusEnd get() = p.getLong("focusEnd", 0L); set(v) = p.edit().putLong("focusEnd", v).apply()
    var focusGoal get() = p.getString("focusGoal", "") ?: ""; set(v) = p.edit().putString("focusGoal", v).apply()
    var focusDnd get() = p.getBoolean("focusDnd", true); set(v) = p.edit().putBoolean("focusDnd", v).apply()
    var voiceRate get() = p.getFloat("voiceRate", 1f); set(v) = p.edit().putFloat("voiceRate", v).apply()
    /* Confort et accessibilité */
    /** Taille du texte propre à l'application (multiplie celle d'Android). */
    var textScale get() = p.getFloat("textScale", 1f); set(v) = p.edit().putFloat("textScale", v).apply()
    /** Densité de l'affichage : "compact", "normal" ou "large". */
    var density get() = p.getString("density", "normal") ?: "normal"; set(v) = p.edit().putString("density", v).apply()
    /** Vibrations : "off", "light" ou "strong". */
    var haptics get() = p.getString("haptics", "light") ?: "light"; set(v) = p.edit().putString("haptics", v).apply()
    var sounds get() = p.getBoolean("sounds", false); set(v) = p.edit().putBoolean("sounds", v).apply()
    /** Mode une main : boutons d'en-tête en bas, et tirer l'écran vers le bas rapproche le haut sous le pouce. */
    var oneHand get() = p.getBoolean("oneHand", false); set(v) = p.edit().putBoolean("oneHand", v).apply()
    /** Mode Lecture facile : mots simples, statuts écrits en toutes lettres, pas d'effets de verre ni d'animations. */
    var easy get() = p.getBoolean("easy", false); set(v) = p.edit().putBoolean("easy", v).apply()
    /** Gestes de balayage sur les objectifs du jour. */
    var swipe get() = p.getBoolean("swipe", true); set(v) = p.edit().putBoolean("swipe", v).apply()
    /** Cartes de l'accueil dans l'ordre choisi ; « -clé » = carte masquée. */
    var homeCards: List<String> get() = (p.getString("homeCards", "") ?: "").split(",").filter { it.isNotBlank() }; set(v) = p.edit().putString("homeCards", v.joinToString(",")).apply()
    /** Objectifs reportés à demain : « date|id,id ». */
    var snoozed get() = p.getString("snoozed", "") ?: ""; set(v) = p.edit().putString("snoozed", v).apply()
    /** Messages masqués sur cet appareil (« supprimer pour moi »). */
    var hiddenMsgs: Set<String> get() = p.getStringSet("hiddenMsgs", emptySet()) ?: emptySet(); set(v) = p.edit().putStringSet("hiddenMsgs", v.toList().takeLast(2000).toSet()).apply()
    /** Couleur choisie pour chaque conversation : lignes « clé=index d'accent ». */
    var chatColors: Map<String, Int> get() = (p.getString("chatColors", "") ?: "").lines().mapNotNull { l -> l.split('=').takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: return@mapNotNull null) } }.toMap()
        set(v) = p.edit().putString("chatColors", v.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
    /** Messages éphémères par conversation : lignes « clé=heures ». */
    var chatTtl: Map<String, Int> get() = (p.getString("chatTtl", "") ?: "").lines().mapNotNull { l -> l.split('=').takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: return@mapNotNull null) } }.toMap()
        set(v) = p.edit().putString("chatTtl", v.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
    /** Rappels par lieu : lignes « objectif|lat|lon|rayon|sortie|nom ». */
    var geoPlaces get() = p.getString("geoPlaces", "") ?: ""; set(v) = p.edit().putString("geoPlaces", v).apply()
    /** Couleurs de saison (Halloween, Noël, printemps…) sur l'arrière-plan. */
    var seasonal get() = p.getBoolean("seasonal", true); set(v) = p.edit().putBoolean("seasonal", v).apply()
    /** Fonds illustrés éclairés selon l'heure réelle (aube, jour, crépuscule, nuit). */
    /** Disposition de l'accueil : compact, normal ou aéré. */
    var homeLayout get() = p.getString("homeLayout", "comfy") ?: "comfy"; set(v) = p.edit().putString("homeLayout", v).apply()
    /** Fond qui réagit : lueur selon l'avancement du jour, confettis les journées parfaites. */
    var bgReactive get() = p.getBoolean("bgReactive", true); set(v) = p.edit().putBoolean("bgReactive", v).apply()
    /** Pluie, neige, orage ou brouillard par-dessus le fond quand il fait ce temps-là dehors (ville de la météo). */
    var bgWeather get() = p.getBoolean("bgWeather", true); set(v) = p.edit().putBoolean("bgWeather", v).apply()
    /** Flamme personnalisée : forme et effet animé. */
    var flameShape get() = p.getString("flameShape", "classic") ?: "classic"; set(v) = p.edit().putString("flameShape", v).apply()
    var flameFx get() = p.getString("flameFx", "none") ?: "none"; set(v) = p.edit().putString("flameFx", v).apply()
    /** Compagnon : nom et accessoires portés. */
    var petName get() = p.getString("petName", "Braisou") ?: "Braisou"; set(v) = p.edit().putString("petName", v).apply()
    var petAcc: Set<String> get() = p.getStringSet("petAcc", emptySet()) ?: emptySet(); set(v) = p.edit().putStringSet("petAcc", v).apply()
    /** Briefing du matin lu à voix haute à la première ouverture de la journée (avant midi). */
    var briefAuto get() = p.getBoolean("briefAuto", false); set(v) = p.edit().putBoolean("briefAuto", v).apply()
    var briefDay get() = p.getString("briefDay", "") ?: ""; set(v) = p.edit().putString("briefDay", v).apply()
    /** Bravos déjà envoyés ou écartés (« id:palier »), pour ne pas reproposer le même palier. */
    var cheeredMilestones: Set<String> get() = p.getStringSet("cheeredMs", emptySet()) ?: emptySet(); set(v) = p.edit().putStringSet("cheeredMs", v).apply()
    var bgClock get() = p.getBoolean("bgClock", true); set(v) = p.edit().putBoolean("bgClock", v).apply()
    /** Sauvegarde automatique : dossier choisi (URI d'arborescence), dernière réussite, dernière erreur. */
    var backupUri get() = p.getString("backupUri", "") ?: ""; set(v) = p.edit().putString("backupUri", v).apply()
    var backupLast get() = p.getLong("backupLast", 0L); set(v) = p.edit().putLong("backupLast", v).apply()
    var backupError get() = p.getString("backupError", "") ?: ""; set(v) = p.edit().putString("backupError", v).apply()
    var swipeHint get() = p.getBoolean("swipeHint", false); set(v) = p.edit().putBoolean("swipeHint", v).apply()
    fun a11yCount() = listOf(contrast, cvd.isNotEmpty(), mono, motion, transp, easy, oneHand, textScale != 1f).count { it }
}
