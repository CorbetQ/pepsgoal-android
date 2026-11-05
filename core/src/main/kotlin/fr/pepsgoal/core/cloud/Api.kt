package fr.pepsgoal.core.cloud

import fr.pepsgoal.core.JSON
import fr.pepsgoal.core.NetSource
import fr.pepsgoal.core.NetStats
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
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
    /** Mesure d'un appel : octets lus sur le fil (avant décompression), en-têtes et compression, relevés par l'intercepteur réseau. */
    private class Meter { val wire = AtomicLong(); val gzip = AtomicBoolean(); val sent = AtomicLong() }
    private val meters = ConcurrentHashMap<Call, Meter>()

    /** Corps de réponse qui compte les octets reçus tels qu'ils passent sur le réseau (compressés le cas échéant). */
    private class CountingBody(private val inner: ResponseBody, private val m: Meter) : ResponseBody() {
        private val counted: BufferedSource by lazy {
            object : ForwardingSource(inner.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long { val n = super.read(sink, byteCount); if (n > 0) m.wire.addAndGet(n); return n }
            }.buffer()
        }
        override fun contentType() = inner.contentType()
        override fun contentLength() = inner.contentLength()
        override fun source(): BufferedSource = counted
    }

    // Un seul client (une seule réserve de connexions TLS/HTTP 2) : les variantes partagent connexions et répartiteur.
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val req = chain.request(); val m = meters[chain.call()]
            val res = chain.proceed(req)
            if (m == null) res else {
                m.sent.set(req.headers.byteCount() + (req.body?.contentLength()?.coerceAtLeast(0) ?: 0))
                m.wire.addAndGet(res.headers.byteCount())
                if (res.header("Content-Encoding")?.contains("gzip", true) == true) m.gzip.set(true)
                res.body?.let { b -> res.newBuilder().body(CountingBody(b, m)).build() } ?: res
            }
        }.build()
    private val uploadHttp = http.newBuilder().callTimeout(120, TimeUnit.SECONDS).build()
    private val statusHttp = http.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()

    /**
     * Requêtes conditionnelles des relèves : ETag et corps gardés en mémoire (jamais sur disque, les réponses contiennent
     * des données privées), clé (compte, chemin). Sans ETag du serveur, rien ne change.
     */
    private val conditional = object : LinkedHashMap<String, Pair<String, ByteArray>>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, ByteArray>>?) = size > 8
    }
    /** Compte propriétaire du cache conditionnel : le changer vide le cache. */
    var account: String? = null
        set(v) { if (v != field) clearConditional(); field = v }
    fun clearConditional() = synchronized(conditional) { conditional.clear() }
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
    /** Réponses 304 servies depuis le cache conditionnel. */
    val notModified = AtomicLong()

    private suspend fun <T> request(call: Call, consume: (Int, ByteArray) -> T): T = requestFull(call) { code, bytes, _ -> consume(code, bytes) }

    private suspend fun <T> requestFull(call: Call, consume: (Int, ByteArray, Response) -> T): T {
        val source = coroutineContext[NetSource]?.name ?: "application"
        val m = Meter(); meters[call] = m
        val path = call.request().url.let { u -> u.encodedPath + (u.encodedQuery?.let { "?$it" } ?: "") }
        return suspendCancellableCoroutine { cont ->
            requests.incrementAndGet()
            cont.invokeOnCancellation { cancelledCalls.incrementAndGet(); meters.remove(call); call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    meters.remove(call)
                    NetStats.record(source, path, 0, m.wire.get(), 0, m.sent.get(), false)
                    if (cont.isActive) cont.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val value = response.use { r ->
                            val bytes = r.body?.bytes() ?: ByteArray(0)
                            receivedBytes.addAndGet(bytes.size.toLong())
                            meters.remove(call)
                            NetStats.record(source, path, r.code, m.wire.get(), bytes.size.toLong(), m.sent.get(), m.gzip.get())
                            consume(r.code, bytes, r)
                        }
                        if (cont.isActive) cont.resume(value)
                    } catch (e: Exception) { if (cont.isActive) cont.resumeWithException(e) }
                }
            })
        }
    }

    private class Res(val code: Int, val bytes: ByteArray, val etag: String?)
    private suspend fun request(call: Call): Res = requestFull(call) { code, bytes, r -> Res(code, bytes, r.header("ETag")) }

    private suspend fun exec(build: (Request.Builder) -> Request.Builder, retry: Boolean = true): Res {
        if (forceOffline) throw ApiError(0, "offline", "Mode hors ligne activé : repasse en ligne dans Paramètres › Données.")
        val sent = tokens?.accessToken
        val res = try {
            val rb = build(Request.Builder().header("User-Agent", userAgent))
            sent?.let { rb.header("Authorization", "Bearer $it") }
            val request = rb.build()
            request((if (request.body is MultipartBody) uploadHttp else http).newCall(request))
        } catch (e: IOException) { onServerDown?.invoke(); throw ApiError(0, "offline", "Connexion impossible au serveur PepsGoal.") }

        if (res.code == 502 || res.code == 504) onServerDown?.invoke()
        if (res.code == 401 && retry && sent != null) {
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
        val key = if (method == "GET" && body == null && path in CONDITIONAL) "$account|$path" else null
        val cached = key?.let { synchronized(conditional) { conditional[it] } }
        val r = exec({ rb ->
            val rbody: RequestBody? = when {
                body != null -> body.toString().toRequestBody(jsonType)
                method == "GET" -> null
                method == "DELETE" -> null
                else -> "{}".toRequestBody(jsonType)
            }
            cached?.let { rb.header("If-None-Match", it.first) }
            rb.url(url(path)).method(method, rbody)
        })
        // 304 : rien n'a changé, le corps mémorisé fait foi
        if (r.code == 304 && cached != null) { notModified.incrementAndGet(); return parse(200, cached.second) }
        if (key != null && r.code in 200..299) synchronized(conditional) { if (r.etag != null) conditional[key] = r.etag to r.bytes else conditional.remove(key) }
        return parse(r.code, r.bytes)
    }

    suspend fun get(path: String) = call("GET", path)
    suspend fun post(path: String, body: JsonElement? = JsonObject(emptyMap())) = call("POST", path, body)
    suspend fun delete(path: String, body: JsonElement? = null) = call("DELETE", path, body)
    suspend fun patch(path: String, body: JsonElement) = call("PATCH", path, body)
    suspend fun put(path: String, body: JsonElement) = call("PUT", path, body)

    /** Téléversement d'un fichier (image publique sur /v1/media, pièce jointe chiffrée sur /v1/attachments). */
    suspend fun upload(path: String, bytes: ByteArray, filename: String, mime: String): JsonElement {
        val r = exec({ rb ->
            val mb = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", filename, bytes.toRequestBody(mime.toMediaType())).build()
            rb.url(url(path)).post(mb)
        })
        return parse(r.code, r.bytes)
    }

    suspend fun bytes(path: String): ByteArray {
        val r = exec({ it.url(url(path)).get() })
        if (r.code !in 200..299) throw ApiError(r.code, "error", "Pièce jointe indisponible.")
        return r.bytes
    }

    /** Statut public du serveur, sans session. */
    suspend fun status(): JsonObject {
        if (forceOffline) throw ApiError(0, "offline", "Mode hors ligne")
        try {
            val r = request(statusHttp.newCall(Request.Builder().url(url("/v1/status")).header("User-Agent", userAgent).build()))
            return parse(r.code, r.bytes) as? JsonObject ?: throw ApiError(502, "invalid", "Réponse invalide")
        } catch (e: IOException) { throw ApiError(0, "offline", "Connexion impossible") }
    }

    /** Requête directe vers un autre service (Open-Meteo, mode sans compte). */
    suspend fun external(fullUrl: String): JsonElement {
        try {
            val r = request(http.newCall(Request.Builder().url(fullUrl).header("User-Agent", "PepsGoal (+https://pepsgoal.fr)").build()))
            return parse(r.code, r.bytes)
        } catch (e: IOException) { throw ApiError(0, "offline", "Connexion impossible") }
    }

    companion object {
        const val TOK_KEY = "pepsgoal.tokens.v1"
        /** GET idempotents des relèves, éligibles aux requêtes conditionnelles (If-None-Match). */
        val CONDITIONAL = setOf("/v1/notifications", "/v1/conversations", "/v1/groups", "/v1/feed?tab=friends", "/v1/feed?tab=public", "/v1/friends", "/v1/stories")
        val EMPTY: JsonElement = JsonNull
    }
}
