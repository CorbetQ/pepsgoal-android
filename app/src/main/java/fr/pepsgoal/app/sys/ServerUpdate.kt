package fr.pepsgoal.app.sys

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import fr.pepsgoal.core.UpdateLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Base64

/**
 * Modération › Serveur › Mise à jour : connexion SSH en root (mot de passe demandé à chaque fois, jamais enregistré),
 * lancement de deploy/update.sh détaché, puis lecture de son journal en direct pour la barre de progression.
 *
 * Clé d'hôte : épinglée à la première connexion après confirmation de son empreinte (comme « ssh » la première fois),
 * vérifiée ensuite avant tout envoi du mot de passe.
 */
object ServerUpdate {
    /** idle, connecting, hostkey (empreinte à confirmer), running, done, error */
    var phase by mutableStateOf("idle"); private set
    var log by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null); private set
    /** Empreinte à confirmer (type et SHA256) ; `changed` : différente de celle enregistrée. */
    var hostKey by mutableStateOf<Triple<String, String, Boolean>?>(null); private set
    val progress: UpdateLog.Progress get() = UpdateLog.parse(log)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    /** Mot de passe gardé en mémoire le temps de confirmer l'empreinte, puis effacé. */
    private var pending: Triple<String, Int, Pair<String, Boolean>>? = null

    private fun sp(ctx: Context) = ctx.getSharedPreferences("pepsgoal.ssh", Context.MODE_PRIVATE)
    fun host(ctx: Context) = sp(ctx).getString("host", "api.pepsgoal.fr") ?: "api.pepsgoal.fr"
    fun port(ctx: Context) = sp(ctx).getInt("port", 22)
    private fun pinKey(host: String, port: Int) = "hk:$host:$port"
    fun pinned(ctx: Context, host: String, port: Int) = sp(ctx).getString(pinKey(host, port), null)
    fun forget(ctx: Context, host: String, port: Int) { sp(ctx).edit().remove(pinKey(host, port)).apply(); hostKey = null; error = null; phase = "idle" }

    fun start(ctx: Context, host: String, port: Int, password: String, web: Boolean) {
        if (job?.isActive == true) return
        sp(ctx).edit().putString("host", host).putInt("port", port).apply()
        phase = "connecting"; error = null; hostKey = null; log = ""
        val app = ctx.applicationContext
        job = scope.launch { run(app, host, port, password, web) }
    }

    /** Empreinte vérifiée par l'admin : on l'épingle et on reprend avec le mot de passe saisi. */
    fun trust(ctx: Context) {
        val (h, port, pw) = pending ?: return
        val k = hostKey ?: return
        sp(ctx).edit().putString(pinKey(h, port), "${k.first} ${k.second}").apply()
        pending = null
        start(ctx, h, port, pw.first, pw.second)
    }

    fun cancelTrust() { pending = null; hostKey = null; phase = "idle" }

    fun reset() { if (job?.isActive != true) { phase = "idle"; log = ""; error = null } }

    private suspend fun run(ctx: Context, host: String, port: Int, password: String, web: Boolean) {
        val pin = pinned(ctx, host, port)
        var seen: Pair<String, String>? = null
        val jsch = JSch()
        jsch.setHostKeyRepository(object : HostKeyRepository {
            override fun check(h: String?, key: ByteArray): Int {
                val s = keyType(key) to fingerprint(key); seen = s
                return when { pin == null -> HostKeyRepository.NOT_INCLUDED; pin == "${s.first} ${s.second}" -> HostKeyRepository.OK; else -> HostKeyRepository.CHANGED }
            }
            override fun add(hostkey: HostKey?, ui: UserInfo?) {}
            override fun remove(host: String?, type: String?) {}
            override fun remove(host: String?, type: String?, key: ByteArray?) {}
            override fun getKnownHostsRepositoryID() = "pepsgoal"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        })
        val session = try { jsch.getSession("root", host, port) } catch (e: Exception) { fail("Adresse du serveur invalide."); return }
        session.setPassword(password)
        // Mot de passe aussi proposé aux serveurs qui le demandent en « keyboard-interactive »
        session.userInfo = object : UserInfo, UIKeyboardInteractive {
            override fun getPassphrase(): String? = null
            override fun getPassword(): String = password
            override fun promptPassword(message: String?) = true
            override fun promptPassphrase(message: String?) = false
            override fun promptYesNo(message: String?) = false
            override fun showMessage(message: String?) {}
            override fun promptKeyboardInteractive(destination: String?, name: String?, instruction: String?, prompt: Array<out String>?, echo: BooleanArray?): Array<String> =
                Array(prompt?.size ?: 0) { password }
        }
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        try {
            session.connect(15_000)
        } catch (e: JSchException) {
            val s = seen
            val msg = e.message ?: ""
            when {
                s != null && pin == null -> { pending = Triple(host, port, password to web); hostKey = Triple(s.first, s.second, false); phase = "hostkey" }
                s != null && pin != null && "${s.first} ${s.second}" != pin -> { hostKey = Triple(s.first, s.second, true); fail("La clé d’hôte du serveur a changé depuis la dernière connexion. Si tu n’as pas réinstallé le serveur, ne continue pas.") }
                msg.contains("Auth fail", true) || msg.contains("Auth cancel", true) -> fail("Mot de passe root refusé (ou connexion root par mot de passe désactivée sur le serveur).")
                msg.contains("timeout", true) || e.cause is java.net.SocketTimeoutException -> fail("Le serveur ne répond pas en SSH sur $host:$port.")
                e.cause is java.net.UnknownHostException -> fail("Serveur introuvable : $host.")
                e.cause is java.net.ConnectException -> fail("Connexion SSH refusée sur $host:$port.")
                else -> fail("Connexion SSH impossible : ${msg.ifEmpty { e.javaClass.simpleName }}")
            }
            return
        }
        phase = "running"
        val ch = session.openChannel("exec") as ChannelExec
        try {
            ch.setCommand(UpdateLog.command(web))
            ch.setErrStream(null)
            val input = ch.inputStream
            ch.connect(10_000)
            val buf = StringBuilder()
            input.bufferedReader().use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    buf.append(line).append('\n')
                    // Le journal affiché est relu en entier : on garde les 400 dernières lignes
                    if (buf.length > 60_000) buf.delete(0, buf.length - 40_000)
                    withContext(Dispatchers.Main) { log = buf.toString() }
                    if (line.startsWith("::exit ")) break
                }
            }
            phase = if (progress.done) "done" else { error = "Connexion perdue : la mise à jour continue sur le serveur. Relance-la pour reprendre le suivi."; "error" }
        } catch (e: Exception) {
            fail("Connexion perdue : la mise à jour continue sur le serveur. Relance-la pour reprendre le suivi.")
        } finally {
            try { ch.disconnect() } catch (_: Exception) {}
            session.disconnect()
        }
    }

    private fun fail(msg: String) { error = msg; phase = "error" }

    /** Empreinte OpenSSH : SHA256 de la clé, en base64 sans « = ». */
    fun fingerprint(key: ByteArray): String = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key))

    /** Type de clé : première chaîne du blob SSH (« ssh-ed25519 », « ecdsa-sha2-nistp256 »…). */
    private fun keyType(key: ByteArray): String = try {
        val n = ((key[0].toInt() and 0xff) shl 24) or ((key[1].toInt() and 0xff) shl 16) or ((key[2].toInt() and 0xff) shl 8) or (key[3].toInt() and 0xff)
        String(key, 4, n.coerceIn(0, key.size - 4), Charsets.US_ASCII)
    } catch (e: Exception) { "?" }
}
