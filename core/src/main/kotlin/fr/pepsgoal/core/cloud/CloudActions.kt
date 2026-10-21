package fr.pepsgoal.core.cloud

import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/* Actions sociales, messagerie et modération : appels au serveur, puis actualisation des données sociales. */

private fun ids(l: List<String>) = JsonArray(l.map(::JsonPrimitive))

suspend fun Cloud.sendFriendRequest(userId: String) { api.post("/v1/friends/requests", buildJsonObject { put("userId", userId) }); scheduleSocial(150) }
suspend fun Cloud.acceptRequest(fid: String) { api.post("/v1/friends/requests/$fid/accept"); scheduleSocial(150) }
suspend fun Cloud.declineRequest(fid: String) { api.post("/v1/friends/requests/$fid/decline"); db.friendships.removeAll { it.id == fid }; store.rawSave(); scheduleSocial(150) }
suspend fun Cloud.unfriend(userId: String) { api.delete("/v1/friends/$userId"); scheduleSocial(150) }
suspend fun Cloud.searchUsers(q: String): List<User> = api.get("/v1/users/search?q=" + java.net.URLEncoder.encode(q, "UTF-8")).list().mapNotNull { upsertUser(it) }
suspend fun Cloud.block(userId: String) {
    api.post("/v1/users/$userId/block")
    db.blocks.add(Block(meId(), userId, nowMs())); db.friendships.removeAll { (it.from == userId && it.to == meId()) || (it.to == userId && it.from == meId()) }
    store.rawSave(); refreshSocial()
}
suspend fun Cloud.unblock(userId: String) { api.delete("/v1/users/$userId/block"); db.blocks.removeAll { it.who == userId }; store.rawSave(); refreshSocial() }

/* ---------- feed ---------- */
suspend fun Cloud.toggleLike(p: Post) {
    val u = meId(); val liked = p.likes.contains(u)
    if (liked) p.likes.remove(u) else p.likes.add(u)
    store.changed()
    try { if (liked) api.delete("/v1/posts/${p.id}/like") else api.post("/v1/posts/${p.id}/like") } catch (e: ApiError) { scheduleSocial(); throw e }
}
suspend fun Cloud.deletePost(id: String) { db.posts.removeAll { it.id == id }; store.rawSave(); api.delete("/v1/posts/$id"); scheduleSocial(150) }
suspend fun Cloud.vote(p: Post, oid: String) {
    val poll = p.poll ?: return
    val cur = poll.options.filter { it.mine }.map { it.id }
    val next = if (poll.multi) (if (oid in cur) cur - oid else cur + oid) else (if (oid in cur) emptyList() else listOf(oid))
    api.post("/v1/posts/${p.id}/vote", buildJsonObject { put("optionIds", ids(next)) })
    poll.options.forEach { o -> val now = o.id in next; o.n = maxOf(0, o.n + (if (now) 1 else 0) - (if (o.mine) 1 else 0)); o.mine = now }
    poll.voters = maxOf(0, poll.voters + (if (next.isNotEmpty()) 1 else 0) - (if (cur.isNotEmpty()) 1 else 0))
    store.rawSave(); scheduleSocial(1500)
}
class PollDraft(val options: List<String>, val multi: Boolean, val hours: Int)

/** Image publique (photo de profil, publication, story) : téléversée sur /v1/media, renvoie son adresse. */
suspend fun Cloud.uploadMedia(jpeg: ByteArray): String = api.upload("/v1/media", jpeg, "photo.jpg", "image/jpeg").str("url")

suspend fun Cloud.publish(text: String, photo: ByteArray?, poll: PollDraft?, visibility: String, goalId: String?, kind: String = "manual"): String {
    val url = photo?.let { uploadMedia(it) }
    val r = api.post("/v1/posts", buildJsonObject {
        put("text", text); put("photoUrl", url?.let(::JsonPrimitive) ?: JsonNull); put("visibility", if (visibility == "public") "public" else "friends")
        put("goalId", goalId?.takeIf { Mapping.UUID_RE.matches(it) }?.let(::JsonPrimitive) ?: JsonNull); put("kind", kind)
        put("poll", poll?.let { buildJsonObject { put("options", ids(it.options)); put("multi", it.multi); put("hours", it.hours) } } ?: JsonNull)
    })
    refreshSocial()
    return r.str("status")
}
suspend fun Cloud.comment(p: Post, text: String) {
    val r = api.post("/v1/posts/${p.id}/comments", buildJsonObject { put("text", text) })
    p.comments.add(Comment(r.str("id"), meId(), text, nowMs())); store.rawSave()
}
suspend fun Cloud.deleteComment(p: Post, cid: String) { api.delete("/v1/comments/$cid"); p.comments.removeAll { it.id == cid }; store.rawSave() }
suspend fun Cloud.report(postId: String, reason: String) { api.post("/v1/posts/$postId/report", buildJsonObject { put("reason", reason) }) }
suspend fun Cloud.appeal(text: String) { api.post("/v1/appeals", buildJsonObject { put("text", text) }); scheduleSocial() }
fun Cloud.readAllNotifications() {
    db.notifs.forEach { if (it.userId == meId()) it.read = true }; store.rawSave()
    scope.launch0 { api.post("/v1/notifications/read-all") }
}

/* ---------- stories ---------- */
suspend fun Cloud.publishStory(kind: String, data: JsonObject?, photo: ByteArray?, text: String) {
    val body = when (kind) {
        "photo" -> buildJsonObject { put("kind", "photo"); put("mediaUrl", uploadMedia(photo!!)); put("text", text.take(140)) }
        else -> buildJsonObject { put("kind", kind); put("data", data!!); put("text", text.take(140)) }
    }
    api.post("/v1/stories", body); refreshSocial()
}
suspend fun Cloud.likeStory(s: Story, on: Boolean) { if (on) api.post("/v1/stories/${s.id}/like") else api.delete("/v1/stories/${s.id}/like"); s.liked = on; store.rawSave() }
fun Cloud.viewStory(s: Story) {
    if (s.userId == meId() || !viewed.add(s.id)) return
    if (!s.seenBy.contains(meId())) { s.seenBy.add(meId()); store.rawSave() }
    scope.launch0 { api.post("/v1/stories/${s.id}/view") }
}
suspend fun Cloud.deleteStory(id: String) { db.stories.removeAll { it.id == id }; store.rawSave(); api.delete("/v1/stories/$id") }

/* ---------- messagerie ---------- */
suspend fun Cloud.openChat(peer: String) {
    loadConversation(peer, false); store.rawSave()
    try { e2e.openConv(Conv(peer = peer)) } catch (e: Exception) {}
    e2e.decryptAll(); store.changed()
}
suspend fun Cloud.openGroup(gid: String) {
    loadGroup(gid, false); store.rawSave()
    try { e2e.openConv(Conv(group = gid)) } catch (e: Exception) {}
    e2e.decryptAll(); store.changed()
}
suspend fun Cloud.createGroup(name: String, emoji: String, members: List<String>): String {
    val r = api.post("/v1/groups", buildJsonObject { put("name", name); put("emoji", emoji); put("members", ids(members)) })
    refreshSocial(); return r.str("id")
}
suspend fun Cloud.updateGroup(gid: String, name: String?, emoji: String?) {
    api.patch("/v1/groups/$gid", buildJsonObject { name?.let { put("name", it) }; emoji?.let { put("emoji", it) } })
    db.chats.firstOrNull { it.id == gid }?.let { c -> name?.let { c.name = it }; emoji?.let { c.emoji = it } }; store.rawSave(); refreshSocial()
}
suspend fun Cloud.addGroupMembers(gid: String, users: List<String>) { api.post("/v1/groups/$gid/members", buildJsonObject { put("userIds", ids(users)) }); refreshSocial() }
suspend fun Cloud.removeGroupMember(gid: String, userId: String) {
    api.delete("/v1/groups/$gid/members/$userId")
    if (userId == meId()) { db.chats.removeAll { it.id == gid }; db.messages.removeAll { it.chat == gid }; store.rawSave() }
    refreshSocial()
}
suspend fun Cloud.deleteGroup(gid: String) { api.delete("/v1/groups/$gid"); db.chats.removeAll { it.id == gid }; db.messages.removeAll { it.chat == gid }; store.rawSave(); refreshSocial() }

/* ---------- compte ---------- */
suspend fun Cloud.saveProfile(name: String, bio: String) { api.patch("/v1/me", buildJsonObject { put("displayName", name); put("bio", bio) }); pg.me()?.let { it.name = name; it.bio = bio }; store.rawSave() }
suspend fun Cloud.saveAvatar(a: Avatar, photo: ByteArray?) {
    val url = if (a.kind == "photo") (photo?.let { uploadMedia(it) } ?: a.photo.takeIf { it.startsWith("https://") }) else null
    api.patch("/v1/me", buildJsonObject { put("avatar", buildJsonObject { put("kind", a.kind); put("color", a.color); put("emoji", a.emoji); put("picto", a.picto); put("photoUrl", url?.let(::JsonPrimitive) ?: JsonNull) }) })
    pg.me()?.avatar = Avatar(a.kind, a.color, a.emoji, a.picto, url ?: ""); store.rawSave()
}
suspend fun Cloud.changePassword(cur: String, next: String) {
    val r = api.post("/v1/auth/password", buildJsonObject { put("current", cur); put("next", next) })
    api.tokens = Tokens(r.str("accessToken"), r.str("refreshToken"))
    try { e2e.vaultRewrap(cur, next) } catch (e: Exception) {}
}
suspend fun Cloud.export(): String = api.get("/v1/me/export").toString()

/* ---------- météo ---------- */
suspend fun Cloud.forecast(lat: Double, lon: Double): Forecast {
    if (store.acct) return JSON.decodeFromJsonElement(Forecast.serializer(), api.get("/v1/weather?lat=$lat&lon=$lon"))
    val q = "latitude=$lat&longitude=$lon&timezone=auto&forecast_days=4&wind_speed_unit=kmh&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day,precipitation,weather_code,wind_speed_10m,cloud_cover&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset"
    val j = api.external("https://api.open-meteo.com/v1/forecast?$q").jsonObject
    val c = j.obj("current"); val d = j.obj("daily")
    fun arr(k: String) = (d?.get(k) as? JsonArray) ?: JsonArray(emptyList())
    val days = arr("time").mapIndexed { i, t -> WxDay(t.jsonPrimitive.content, arr("weather_code").getOrNull(i)?.jsonPrimitive?.intOrNull, arr("temperature_2m_max").getOrNull(i)?.jsonPrimitive?.doubleOrNull,
        arr("temperature_2m_min").getOrNull(i)?.jsonPrimitive?.doubleOrNull, arr("precipitation_probability_max").getOrNull(i)?.jsonPrimitive?.doubleOrNull, arr("sunrise").getOrNull(i)?.jsonPrimitive?.contentOrNull, arr("sunset").getOrNull(i)?.jsonPrimitive?.contentOrNull) }
    fun cn(k: String) = (c?.get(k) as? JsonPrimitive)?.doubleOrNull
    return Forecast(j.str("timezone"), WxCurrent(c.strN("time"), cn("temperature_2m"), cn("apparent_temperature"), cn("relative_humidity_2m"), cn("is_day") == 1.0, cn("precipitation"), cn("weather_code")?.toInt() ?: 0, cn("wind_speed_10m"), cn("cloud_cover")), days, nowIso())
}
suspend fun Cloud.places(q: String): List<Place> {
    val enc = java.net.URLEncoder.encode(q, "UTF-8")
    if (store.acct) return api.get("/v1/weather/places?q=$enc").list().map { Place(it.str("name"), it.str("admin1"), it.str("country"), it.str("cc"), it["lat"]!!.jsonPrimitive.double, it["lon"]!!.jsonPrimitive.double, it.str("tz")) }
    return api.external("https://geocoding-api.open-meteo.com/v1/search?name=$enc&count=8&language=fr&format=json").arr("results")
        .map { Place(it.str("name"), it.str("admin1"), it.str("country"), it.str("country_code"), it["latitude"]!!.jsonPrimitive.double, it["longitude"]!!.jsonPrimitive.double, it.str("timezone")) }
}

/* ---------- modération ---------- */
suspend fun Cloud.modPost(id: String, action: String) { api.post("/v1/admin/posts/$id/$action"); if (action in setOf("delete", "reject")) db.posts.removeAll { it.id == id }; store.rawSave(); refreshSocial() }
suspend fun Cloud.modUser(id: String, action: String, body: JsonObject = JsonObject(emptyMap())) { api.post("/v1/admin/users/$id/$action", body); refreshSocial() }
suspend fun Cloud.modDeleteUser(id: String) { api.delete("/v1/admin/users/$id"); db.users.removeAll { it.id == id }; repairRefs(); store.rawSave(); refreshSocial() }
suspend fun Cloud.modAppeal(id: String, accept: Boolean, answer: String) { api.post("/v1/admin/appeals/$id/${if (accept) "accept" else "refuse"}", buildJsonObject { put("answer", answer.take(200)) }); refreshSocial() }
suspend fun Cloud.announce(text: String, tag: String, to: String?): Int {
    val r = api.post("/v1/admin/announcements", buildJsonObject { put("text", text); put("tag", tag); put("toUserId", to?.let(::JsonPrimitive) ?: JsonNull) })
    scheduleSocial(); return r.int("sent")
}
suspend fun Cloud.deleteNews(id: String) { api.delete("/v1/admin/announcements/$id"); refreshSocial() }
suspend fun Cloud.startMaintenance(mode: String, start: Long, until: Long?, message: String, notify: Boolean) {
    api.post("/v1/admin/maintenance", buildJsonObject { put("mode", mode); put("startsAt", isoOf(start)!!); put("endsAt", isoOf(until)?.let(::JsonPrimitive) ?: JsonNull); put("message", message.take(200)); put("notify", notify) })
    refreshSocial(); ping()
}
suspend fun Cloud.endMaintenance() { api.post("/v1/admin/maintenance/end"); refreshSocial(); ping() }

private fun kotlinx.coroutines.CoroutineScope.launch0(block: suspend () -> Unit) { this.launch { try { block() } catch (e: Exception) {} } }
