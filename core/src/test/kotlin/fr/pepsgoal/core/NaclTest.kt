package fr.pepsgoal.core

import fr.pepsgoal.core.crypto.E2e
import fr.pepsgoal.core.crypto.Nacl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Vecteurs produits par tweetnacl-js 1.0.3 (la bibliothèque de la version web et du serveur). */
class NaclTest {
    private val v: JsonObject = Json.parseToJsonElement(javaClass.getResource("/nacl-vectors.json")!!.readText()).jsonObject
    private fun b(k: String) = E2e.unb64(v[k]!!.jsonPrimitive.content)

    @Test fun secretboxMatchesTweetnacl() {
        val key = b("key"); val nonce = b("nonce")
        for (pair in v["secret"]!!.jsonArray) {
            val msg = pair.jsonArray[0].jsonPrimitive.content
            val expected = E2e.unb64(pair.jsonArray[1].jsonPrimitive.content)
            assertContentEquals(expected, Nacl.secretbox(msg.toByteArray(), nonce, key))
            assertEquals(msg, Nacl.secretboxOpen(expected, nonce, key)!!.toString(Charsets.UTF_8))
            val bad = expected.copyOf(); bad[0] = (bad[0].toInt() xor 1).toByte()
            assertNull(Nacl.secretboxOpen(bad, nonce, key))
        }
    }

    @Test fun x25519AndBoxMatchTweetnacl() {
        assertContentEquals(b("pka"), Nacl.scalarMultBase(b("ska")))
        assertContentEquals(b("pkb"), Nacl.scalarMultBase(b("skb")))
        assertContentEquals(b("box"), Nacl.box(b("key"), b("nonce"), b("pkb"), b("ska")))
        assertContentEquals(b("key"), Nacl.boxOpen(b("box"), b("nonce"), b("pka"), b("skb")))
        assertNull(Nacl.boxOpen(b("box"), b("nonce"), b("pka"), b("ska")))
    }

    @Test fun hashAndRoundTrips() {
        assertContentEquals(b("hash"), Nacl.hash("a|b".toByteArray()))
        val alice = Nacl.boxKeyPair(); val bob = Nacl.boxKeyPair()
        val k = E2e.newPairKey()
        val env = E2e.wrapKey(k, E2e.b64(bob.publicKey), E2e.b64(alice.secretKey))
        assertEquals(k, E2e.unwrapKey(env.nonce, env.box, E2e.b64(alice.publicKey), E2e.b64(bob.secretKey)))
        val p = E2e.encrypt("Salut 😀", k, "id")
        assertEquals("Salut 😀", E2e.decrypt(p, k))
        val vk = Nacl.randomBytes(32)
        assertEquals(true, E2e.checkOk(vk, E2e.checkOf(vk)))
        assertEquals(false, E2e.checkOk(Nacl.randomBytes(32), E2e.checkOf(vk)))
        assertEquals(5 * 6 + 5, E2e.safetyCode(listOf("a", "b")).length)
    }
}
