package fr.pepsgoal.core.cloud

import fr.pepsgoal.core.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray
import java.net.URLEncoder

/*
 * Boutique des créateurs et « PepsGoal Style » (10.0) : appels du serveur (docs/content-api.md). Tout passe par le
 * compte ; hors ligne, ces appels échouent avec un message clair (la création de pack demande une connexion).
 */

private inline fun <reified T> decode(e: kotlinx.serialization.json.JsonElement, s: kotlinx.serialization.KSerializer<T>): T =
    try { MARKET_JSON.decodeFromJsonElement(s, e) } catch (x: Exception) { throw ApiError(502, "invalid", "Réponse du serveur illisible.") }

/** Photo (JPEG déjà réduite) → image stylisée et premier plan détouré, calculés par le serveur. */
suspend fun Cloud.styleImage(jpeg: ByteArray, stylize: Boolean, weather: Boolean, strength: Float): StyleResult {
    val q = "stylize=${if (stylize) 1 else 0}&weather=${if (weather) 1 else 0}&strength=${"%.2f".format(java.util.Locale.ROOT, strength.coerceIn(0f, 1f))}"
    return decode(api.upload("/v1/style?$q", jpeg, "photo.jpg", "image/jpeg"), StyleResult.serializer())
}

/** Photo d'un pack, envoyée telle quelle (JPEG, PNG ou WebP). */
suspend fun Cloud.uploadPackMedia(bytes: ByteArray, mime: String): ContentFileRef =
    decode(api.upload("/v1/creator/media", bytes, if (mime == "image/webp") "image.webp" else "image.jpg", mime), ContentFileRef.serializer())

private fun packBody(name: String, description: String, price: Int, content: ThemePackSpec, files: List<ContentFileRef>): JsonObject = buildJsonObject {
    put("name", name.trim().take(PACK_NAME_MAX)); put("description", description.trim().take(280)); put("price", Market.clampPrice(price))
    put("content", MARKET_JSON.parseToJsonElement(PackFormat.encode(content, pretty = false)))
    put("files", JsonArray(files.map { MARKET_JSON.encodeToJsonElement(ContentFileRef.serializer(), it) }))
}

suspend fun Cloud.publishPack(name: String, description: String, price: Int, content: ThemePackSpec, files: List<ContentFileRef>): CreatorItem =
    decode(api.post("/v1/creator/items", packBody(name, description, price, content, files)), CreatorItem.serializer())

suspend fun Cloud.updatePublishedPack(id: String, name: String, description: String, price: Int, content: ThemePackSpec, files: List<ContentFileRef>): CreatorItem =
    decode(api.put("/v1/creator/items/$id", packBody(name, description, price, content, files)), CreatorItem.serializer())

suspend fun Cloud.myCreatorItems(): List<CreatorItem> = decode(api.get("/v1/creator/items"), ListSerializer(CreatorItem.serializer()))

suspend fun Cloud.retirePack(id: String) { api.delete("/v1/creator/items/$id") }

suspend fun Cloud.creatorSales(since: String?): SalesPage =
    decode(api.get("/v1/creator/sales" + (since?.let { "?since=" + URLEncoder.encode(it, "UTF-8") } ?: "")), SalesPage.serializer())

/** Achat (ou obtention gratuite) enregistré par le serveur : 100 % du prix au créateur. */
suspend fun Cloud.purchaseContent(id: String): Purchase = decode(api.post("/v1/content/$id/purchase"), Purchase.serializer())

suspend fun Cloud.myContentPurchases(): List<OwnedContent> = decode(api.get("/v1/me/content-purchases"), ListSerializer(OwnedContent.serializer()))

/* ---------- modération ---------- */

@kotlinx.serialization.Serializable
data class PendingPack(val id: String, val name: String, val description: String = "", val author: String = "", val price: Int = 0,
                       val preview: String? = null, val status: String = "pending", val reviewNote: String = "", val createdBy: String? = null,
                       val content: JsonObject = JsonObject(emptyMap()), val files: List<ContentFileRef> = emptyList())

suspend fun Cloud.pendingPacks(): List<PendingPack> = decode(api.get("/v1/admin/content?status=pending"), ListSerializer(PendingPack.serializer()))
suspend fun Cloud.approvePack(id: String) { api.post("/v1/admin/content/$id/publish") }
suspend fun Cloud.rejectPack(id: String, reason: String) { api.post("/v1/admin/content/$id/reject", buildJsonObject { put("reason", reason.trim().take(200)) }) }
