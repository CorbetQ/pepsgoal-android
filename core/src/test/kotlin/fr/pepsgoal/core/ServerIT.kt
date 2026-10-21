package fr.pepsgoal.core

import fr.pepsgoal.core.cloud.*
import fr.pepsgoal.core.model.Goal
import fr.pepsgoal.core.model.days
import fr.pepsgoal.core.model.mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Scénario de bout en bout contre un serveur PepsGoal de développement (apps/server du dépôt pepsgoal-platform).
 * Lancé seulement si PEPSGOAL_IT_API est défini, par exemple : PEPSGOAL_IT_API=http://localhost:8080 ./gradlew :core:test
 */
class ServerIT {
    private val api = System.getenv("PEPSGOAL_IT_API")
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private class Mem : DbFiles, Secrets {
        val m = HashMap<String, String>()
        override fun read(name: String) = m[name]
        override fun write(name: String, content: String) { m[name] = content }
        override fun delete(name: String) { m.remove(name) }
        override fun get(key: String) = m["s:$key"]
        override fun set(key: String, value: String?) { if (value == null) m.remove("s:$key") else m["s:$key"] = value }
    }

    private fun device(): Cloud {
        val mem = Mem(); val store = Store(mem); val pg = Pg(store)
        return Cloud(store, pg, Api(api!!, mem), mem, CoroutineScope(dispatcher)).also { it.boot() }
    }

    @Test fun fullScenario() {
        if (api == null) return
        runBlocking(dispatcher) {
            val suffix = (System.currentTimeMillis() % 100000).toString()
            val a = device(); val b = device()
            a.register("alice$suffix", "Alice Test", "motdepasse1")
            b.register("bob$suffix", "Bob Test", "motdepasse2")
            a.syncNow(); b.syncNow()
            assertTrue(a.store.acct)
            // Objectif + validation, synchronisés
            val g = Goal(uid(), a.meId(), "routine", "Courir", createdAt = nowMs()).apply { mode = "days"; days = (0..6).toList() }
            a.pg.db.goals.add(g); a.pg.validate(g.id)
            a.syncNow()
            assertEquals("", a.syncError)
            // Second appareil d'Alice : récupère tout
            val a2 = device(); a2.login("alice$suffix", "motdepasse1"); a2.syncNow()
            assertNotNull(a2.pg.goalById(g.id)); assertEquals(1, a2.pg.globalStreak(a2.meId()).n)
            // Amitié
            a.sendFriendRequest(b.meId()); b.refreshSocial()
            val req = b.pg.db.friendships.first { it.status == "pending" }
            b.acceptRequest(req.id); a.refreshSocial(); b.refreshSocial()
            assertTrue(a.pg.areFriends(a.meId(), b.meId()))
            // Message chiffré de bout en bout
            a.e2e.device(); b.e2e.device()
            a.openChat(b.meId())
            val sent = a.e2e.deliver(null, b.meId(), Msg.text("Salut Bob 🔥"))
            assertNotNull(sent)
            b.e2e.shareMissing(); b.openChat(a.meId())
            val got = b.pg.db.messages.first { it.id == sent.id }
            assertEquals("Salut Bob 🔥", b.e2e.cache[got.id])
            // Le second appareil d'Alice lit l'historique grâce au coffre de clés
            a2.openChat(b.meId())
            assertEquals("Salut Bob 🔥", a2.e2e.cache[sent.id])
            // Groupe
            val gid = a.createGroup("Team", "🔥", listOf(b.meId()))
            a.openGroup(gid)
            val gm = a.e2e.deliver(gid, null, kotlinx.serialization.json.buildJsonObject { put("k", kotlinx.serialization.json.JsonPrimitive("text")); put("t", kotlinx.serialization.json.JsonPrimitive("Bonjour l'équipe")) })
            assertNotNull(gm)
            b.e2e.shareMissing(); b.refreshSocial(); b.openGroup(gid)
            assertTrue(b.e2e.cache[gm.id]?.contains("Bonjour l'équipe") == true)
            // Feed
            a.publish("Première publication", null, null, "friends", g.id)
            b.refreshSocial()
            val post = b.pg.db.posts.first { it.text == "Première publication" }
            b.toggleLike(post); b.comment(post, "Bravo !")
            a.refreshSocial()
            assertTrue(a.pg.db.posts.first { it.id == post.id }.likes.contains(b.meId()))
            assertTrue(a.pg.db.notifs.any { it.type == "like" })
        }
    }
}
