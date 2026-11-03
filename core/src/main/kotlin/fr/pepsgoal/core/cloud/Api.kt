package fr.pepsgoal.core.cloud

import fr.pepsgoal.core.JSON
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiError(val status: Int, val code: String, message: String) : Exception(message)

/** Petits secrets de l'appareil (jetons de session, clés de chiffrement) : stockés chiffrés par l'application Android. */
interface Secrets {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

class Tokens(val accessToken: String, val refreshToken: String)

/**
 * Client de l'API PepsGoal (docs/API.md) : jeton d'accès de 15 minutes renouvelé automatiquement
 * avec le jeton de renouvellement (à usage unique).
 */
class Api(var base: String, private val secrets: Secrets, private val userAgent: String = "PepsGoal-Android") {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()
    private val uploadHttp = http.newBuilder().callTimeout(120, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val refreshLock = Mutex()
    var forceOffline = false
    /** Le serveur ne répond plus (réseau, 502, 504) : la couche connectée passe hors ligne. */
    var onServerDown: (() -> Unit)? = null
    /** Session perdue (jeton de renouvellement refusé) : retour à l'écran de connexion. */
    var onSessionLost: (() -> Unit)? = null

    private val tokenLock = Any()
    var tokens: Tokens?
        get() = synchronized(tokenLock) { secrets.get(TOK_KEY)?.let { try { val o = JSON.parseToJsonElement(it).jsonObject; Tokens(o["accessToken"]!!.jsonPrimitive.content, o["refreshToken"]!!.jsonPrimitive.content) } catch (e: Exception) { null } } }
        set(v) { synchronized(tokenLock) { secrets.set(TOK_KEY, v?.let { buildJsonObject { put("accessToken", it.accessToken); put("refreshToken", it.refreshToken) }.toString() }) } }

    private fun url(path: String) = base.trimEnd('/') + path

    /** Renouvelle la session ; [failed] : jeton d'accès refusé qui a motivé la demande. */
    private suspend fun refresh(failed: String?): Boolean = refreshLock.withLock {
        val t = tokens ?: return false
        // Un autre appel a renouvelé la session pendant l'attente du verrou : rien à faire
        if (failed != null && t.accessToken != failed) return true
        try {
            val body = buildJsonObject { put("refreshToken", t.refreshToken) }.toString().toRequestBody(jsonType)
            // Persist the rotated single-use token in the response callback, before resuming the caller.
            request(http.newCall(Request.Builder().url(url("/v1/auth/refresh")).header("User-Agent", userAgent).post(body).build())) { code, bytes ->
                if (code == 401) {
                    if (tokens?.refreshToken != t.refreshToken) true
                    else { onSessionLost?.invoke(); false }
                } else if (code !in 200..299) false
                else {
                    val d = JSON.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                    synchronized(tokenLock) {
                        // Logout or another login must not be undone by a late refresh response.
                        if (tokens?.refreshToken == t.refreshToken) tokens = Tokens(d["accessToken"]!!.jsonPrimitive.content, d["refreshToken"]!!.jsonPrimitive.content)
                        tokens != null
                    }
                }
            }
        } catch (e: IOException) { false }
    }

    /** Diagnostic counters, no URL, token or user data retained. */
    val requests = AtomicLong()
    val receivedBytes = AtomicLong()
    val cancelledCalls = AtomicLong()

    private suspend fun <T> request(call: Call, consume: (Int, ByteArray) -> T): T = suspendCancellableCoroutine { cont ->
        requests.incrementAndGet()
        cont.invokeOnCancellation { cancelledCalls.incrementAndGet(); call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val value = response.use { r ->
                        val bytes = r.body?.bytes() ?: ByteArray(0)
                        receivedBytes.addAndGet(bytes.size.toLong())
                        consume(r.code, bytes)
                    }
                    if (cont.isActive) cont.resume(value)
                } catch (e: Exception) { if (cont.isActive) cont.resumeWithException(e) }
            }
        })
    }

    private suspend fun request(call: Call): Pair<Int, ByteArray> = request(call) { code, bytes -> code to bytes }

    private suspend fun exec(build: (Request.Builder) -> Request.Builder, retry: Boolean = true): Pair<Int, ByteArray> {
        if (forceOffline) throw ApiError(0, "offline", "Mode hors ligne activé : repasse en ligne dans Paramètres › Données.")
        val sent = tokens?.accessToken
        val res = try {
            val rb = build(Request.Builder().header("User-Agent", userAgent))
            sent?.let { rb.header("Authorization", "Bearer $it") }
            val request = rb.build()
            request((if (request.body is MultipartBody) uploadHttp else http).newCall(request))
        } catch (e: IOException) { onServerDown?.invoke(); throw ApiError(0, "offline", "Connexion impossible au serveur PepsGoal.") }

        if (res.first == 502 || res.first == 504) onServerDown?.invoke()
        if (res.first == 401 && retry && sent != null) {
            if (tokens?.accessToken != sent || refresh(sent)) return exec(build, false)
        }
        return res
    }

    private fun parse(status: Int, bytes: ByteArray): JsonElement {
        val text = bytes.toString(Charsets.UTF_8)
        val data = try { if (text.isBlank()) JsonObject(emptyMap()) else JSON.parseToJsonElement(text) } catch (e: Exception) { JsonObject(emptyMap()) }
        if (status !in 200..299) {
            val o = data as? JsonObject
            throw ApiError(status, o?.get("error")?.jsonPrimitive?.content ?: "error", o?.get("message")?.jsonPrimitive?.content ?: "Erreur inattendue, réessaie dans un instant.")
        }
        return data
    }

    suspend fun call(method: String, path: String, body: JsonElement? = null): JsonElement {
        val (s, b) = exec({ rb ->
            val rbody: RequestBody? = when {
                body != null -> body.toString().toRequestBody(jsonType)
                method == "GET" -> null
                method == "DELETE" -> null
                else -> "{}".toRequestBody(jsonType)
            }
            rb.url(url(path)).method(method, rbody)
        })
        return parse(s, b)
    }

    suspend fun get(path: String) = call("GET", path)
    suspend fun post(path: String, body: JsonElement? = JsonObject(emptyMap())) = call("POST", path, body)
    suspend fun delete(path: String, body: JsonElement? = null) = call("DELETE", path, body)
    suspend fun patch(path: String, body: JsonElement) = call("PATCH", path, body)
    suspend fun put(path: String, body: JsonElement) = call("PUT", path, body)

    /** Téléversement d'un fichier (image publique sur /v1/media, pièce jointe chiffrée sur /v1/attachments). */
    suspend fun upload(path: String, bytes: ByteArray, filename: String, mime: String): JsonElement {
        val (s, b) = exec({ rb ->
            val mb = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", filename, bytes.toRequestBody(mime.toMediaType())).build()
            rb.url(url(path)).post(mb)
        })
        return parse(s, b)
    }

    suspend fun bytes(path: String): ByteArray {
        val (s, b) = exec({ it.url(url(path)).get() })
        if (s !in 200..299) throw ApiError(s, "error", "Pièce jointe indisponible.")
        return b
    }

    /** Statut public du serveur, sans session. */
    suspend fun status(): JsonObject {
        if (forceOffline) throw ApiError(0, "offline", "Mode hors ligne")
        try {
            val (code, bytes) = request(http.newBuilder().callTimeout(6, TimeUnit.SECONDS).build().newCall(Request.Builder().url(url("/v1/status")).header("User-Agent", userAgent).build()))
            return parse(code, bytes) as? JsonObject ?: throw ApiError(502, "invalid", "Réponse invalide")
        } catch (e: IOException) { throw ApiError(0, "offline", "Connexion impossible") }
    }

    /** Requête directe vers un autre service (Open-Meteo, mode sans compte). */
    suspend fun external(fullUrl: String): JsonElement {
        try {
            val (code, bytes) = request(http.newCall(Request.Builder().url(fullUrl).header("User-Agent", "PepsGoal (+https://pepsgoal.fr)").build()))
            return parse(code, bytes)
        } catch (e: IOException) { throw ApiError(0, "offline", "Connexion impossible") }
    }

    companion object {
        const val TOK_KEY = "pepsgoal.tokens.v1"
        val EMPTY: JsonElement = JsonNull
    }
}
