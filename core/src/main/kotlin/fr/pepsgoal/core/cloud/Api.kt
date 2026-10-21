package fr.pepsgoal.core.cloud

import fr.pepsgoal.core.JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val refreshLock = Mutex()
    var forceOffline = false
    /** Le serveur ne répond plus (réseau, 502, 504) : la couche connectée passe hors ligne. */
    var onServerDown: (() -> Unit)? = null
    /** Session perdue (jeton de renouvellement refusé) : retour à l'écran de connexion. */
    var onSessionLost: (() -> Unit)? = null

    var tokens: Tokens?
        get() = secrets.get(TOK_KEY)?.let { try { val o = JSON.parseToJsonElement(it).jsonObject; Tokens(o["accessToken"]!!.jsonPrimitive.content, o["refreshToken"]!!.jsonPrimitive.content) } catch (e: Exception) { null } }
        set(v) { secrets.set(TOK_KEY, v?.let { buildJsonObject { put("accessToken", it.accessToken); put("refreshToken", it.refreshToken) }.toString() }) }

    private fun url(path: String) = base.trimEnd('/') + path

    private suspend fun refresh(): Boolean = refreshLock.withLock {
        val t = tokens ?: return false
        withContext(Dispatchers.IO) {
            try {
                val body = buildJsonObject { put("refreshToken", t.refreshToken) }.toString().toRequestBody(jsonType)
                http.newCall(Request.Builder().url(url("/v1/auth/refresh")).header("User-Agent", userAgent).post(body).build()).execute().use { r ->
                    if (r.code == 401) { onSessionLost?.invoke(); return@use false }
                    if (!r.isSuccessful) return@use false
                    val d = JSON.parseToJsonElement(r.body!!.string()).jsonObject
                    // Un autre appel a peut-être déjà renouvelé la session entre-temps : on garde le plus récent
                    tokens = Tokens(d["accessToken"]!!.jsonPrimitive.content, d["refreshToken"]!!.jsonPrimitive.content)
                    true
                }
            } catch (e: IOException) { false }
        }
    }

    private suspend fun exec(build: (Request.Builder) -> Request.Builder, retry: Boolean = true): Pair<Int, ByteArray> {
        if (forceOffline) throw ApiError(0, "offline", "Mode hors ligne activé : repasse en ligne dans Paramètres › Données.")
        val sent = tokens?.accessToken
        val res = withContext(Dispatchers.IO) {
            try {
                val rb = build(Request.Builder().header("User-Agent", userAgent))
                sent?.let { rb.header("Authorization", "Bearer $it") }
                http.newCall(rb.build()).execute().use { it.code to (it.body?.bytes() ?: ByteArray(0)) }
            } catch (e: IOException) { onServerDown?.invoke(); throw ApiError(0, "offline", "Connexion impossible au serveur PepsGoal.") }
        }
        if (res.first == 502 || res.first == 504) onServerDown?.invoke()
        if (res.first == 401 && retry && sent != null) {
            if (tokens?.accessToken != sent || refresh()) return exec(build, false)
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
    suspend fun status(): JsonObject = withContext(Dispatchers.IO) {
        if (forceOffline) throw ApiError(0, "offline", "Mode hors ligne")
        try {
            http.newBuilder().callTimeout(6, TimeUnit.SECONDS).build().newCall(Request.Builder().url(url("/v1/status")).header("User-Agent", userAgent).build()).execute().use { r ->
                val o = JSON.parseToJsonElement(r.body!!.string()).jsonObject
                if (!r.isSuccessful) throw ApiError(r.code, "down", "indisponible")
                o
            }
        } catch (e: IOException) { throw ApiError(0, "offline", "Connexion impossible") } catch (e: IllegalStateException) { throw ApiError(0, "offline", "Réponse invalide") }
    }

    /** Requête directe vers un autre service (Open-Meteo, mode sans compte). */
    suspend fun external(fullUrl: String): JsonElement = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(fullUrl).header("User-Agent", "PepsGoal (+https://pepsgoal.fr)").build()).execute().use { r ->
                if (!r.isSuccessful) throw ApiError(r.code, "external", "Service indisponible")
                JSON.parseToJsonElement(r.body!!.string())
            }
        } catch (e: IOException) { throw ApiError(0, "offline", "Connexion impossible") }
    }

    companion object {
        const val TOK_KEY = "pepsgoal.tokens.v1"
        val EMPTY: JsonElement = JsonNull
    }
}
