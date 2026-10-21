package fr.pepsgoal.core.crypto

import kotlinx.serialization.Serializable
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Message chiffré tel qu'il circule et est stocké sur le serveur (packages/shared/src/e2e.ts). */
@Serializable
data class E2ePayload(val v: Int = 1, val keyId: String, val nonce: String, val ct: String)

/** Chiffrement de bout en bout des messages (même protocole que la version web, voir docs/E2E.md). */
object E2e {
    fun b64(u: ByteArray): String = Base64.getEncoder().encodeToString(u)
    fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)

    fun newPairKey(): String = b64(Nacl.randomBytes(32))

    fun encrypt(text: String, key: String, keyId: String): E2ePayload {
        val nonce = Nacl.randomBytes(Nacl.SECRETBOX_NONCE)
        return E2ePayload(1, keyId, b64(nonce), b64(Nacl.secretbox(text.toByteArray(Charsets.UTF_8), nonce, unb64(key))))
    }

    fun decrypt(p: E2ePayload, key: String): String? = try {
        Nacl.secretboxOpen(unb64(p.ct), unb64(p.nonce), unb64(key))?.toString(Charsets.UTF_8)
    } catch (e: IllegalArgumentException) { null }

    class Sealed(val nonce: String, val box: String)

    /** Enveloppe : clé de conversation chiffrée pour un appareil destinataire (crypto_box). */
    fun wrapKey(key: String, recipientPk: String, senderSk: String): Sealed {
        val nonce = Nacl.randomBytes(Nacl.BOX_NONCE)
        return Sealed(b64(nonce), b64(Nacl.box(unb64(key), nonce, unb64(recipientPk), unb64(senderSk))))
    }

    fun unwrapKey(nonce: String, box: String, senderPk: String, sk: String): String? = try {
        Nacl.boxOpen(unb64(box), unb64(nonce), unb64(senderPk), unb64(sk))?.takeIf { it.size == 32 }?.let(::b64)
    } catch (e: IllegalArgumentException) { null }

    /** Boîte avec un nonce séparé (clés du coffre). */
    fun seal(data: ByteArray, key: ByteArray): Sealed {
        val n = Nacl.randomBytes(24)
        return Sealed(b64(n), b64(Nacl.secretbox(data, n, key)))
    }

    fun open(nonce: String, box: String, key: ByteArray): ByteArray? = try {
        Nacl.secretboxOpen(unb64(box), unb64(nonce), key)
    } catch (e: IllegalArgumentException) { null }

    /** Nonce + boîte dans une seule valeur base64 (clé du coffre chiffrée, témoins). */
    fun pack(data: ByteArray, key: ByteArray): String {
        val n = Nacl.randomBytes(24)
        return b64(n + Nacl.secretbox(data, n, key))
    }

    fun unpack(v: String?, key: ByteArray): ByteArray? = try {
        if (v == null) null else unb64(v).let { b -> if (b.size < 40) null else Nacl.secretboxOpen(b.copyOfRange(24, b.size), b.copyOfRange(0, 24), key) }
    } catch (e: IllegalArgumentException) { null }

    const val VAULT_ITER = 310_000
    private const val VAULT_CHECK = "pepsgoal-vault-v1"

    fun deriveVault(password: String, saltB64: String, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), unb64(saltB64), iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun checkOf(key: ByteArray) = pack(VAULT_CHECK.toByteArray(), key)
    fun checkOk(key: ByteArray, check: String?): Boolean = unpack(check, key)?.toString(Charsets.UTF_8) == VAULT_CHECK

    /** Code de sécurité d'une conversation : 6 groupes de 5 chiffres, calculé sur les clés publiques de tous les appareils. */
    fun safetyCode(publicKeys: List<String>): String {
        val h = Nacl.hash(publicKeys.sorted().joinToString("|").toByteArray(Charsets.UTF_8))
        return (0 until 6).joinToString(" ") { i ->
            val v = ((h[i * 3].toInt() and 255) shl 16) or ((h[i * 3 + 1].toInt() and 255) shl 8) or (h[i * 3 + 2].toInt() and 255)
            (v % 100000).toString().padStart(5, '0')
        }
    }
}
