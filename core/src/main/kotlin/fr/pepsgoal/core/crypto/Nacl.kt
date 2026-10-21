package fr.pepsgoal.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Primitives NaCl utilisées par PepsGoal (docs/E2E.md), portées de TweetNaCl (domaine public) :
 * - `secretbox` / `secretboxOpen` : XSalsa20-Poly1305 (messages, coffre de clés, pièces jointes) ;
 * - `box` / `boxOpen` : X25519 + XSalsa20-Poly1305 (enveloppes des clés de conversation) ;
 * - `hash` : SHA-512 (code de sécurité).
 * Formats identiques à tweetnacl-js : `secretbox` renvoie l'étiquette Poly1305 (16 octets) suivie du texte chiffré.
 */
object Nacl {
    const val SECRETBOX_KEY = 32
    const val SECRETBOX_NONCE = 24
    const val BOX_NONCE = 24
    const val BOX_PUBLIC_KEY = 32
    const val BOX_SECRET_KEY = 32
    private const val ZERO_BYTES = 32
    private const val BOX_ZERO_BYTES = 16

    private val rng = SecureRandom()
    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    private val SIGMA = "expand 32-byte k".toByteArray(Charsets.US_ASCII)

    /* ------------------------------------------------------------------ Salsa20 */
    private fun ld32(x: ByteArray, o: Int): Int =
        (x[o].toInt() and 255) or ((x[o + 1].toInt() and 255) shl 8) or ((x[o + 2].toInt() and 255) shl 16) or ((x[o + 3].toInt() and 255) shl 24)

    private fun st32(x: ByteArray, o: Int, u: Int) {
        var v = u
        for (i in 0 until 4) { x[o + i] = v.toByte(); v = v ushr 8 }
    }

    private fun rotl(x: Int, c: Int) = (x shl c) or (x ushr (32 - c))

    private fun core(out: ByteArray, inp: ByteArray, k: ByteArray, c: ByteArray, h: Boolean) {
        val w = IntArray(16); val x = IntArray(16); val y = IntArray(16); val t = IntArray(4)
        for (i in 0 until 4) {
            x[5 * i] = ld32(c, 4 * i)
            x[1 + i] = ld32(k, 4 * i)
            x[6 + i] = ld32(inp, 4 * i)
            x[11 + i] = ld32(k, 16 + 4 * i)
        }
        for (i in 0 until 16) y[i] = x[i]
        for (i in 0 until 20) {
            for (j in 0 until 4) {
                for (m in 0 until 4) t[m] = x[(5 * j + 4 * m) % 16]
                t[1] = t[1] xor rotl(t[0] + t[3], 7)
                t[2] = t[2] xor rotl(t[1] + t[0], 9)
                t[3] = t[3] xor rotl(t[2] + t[1], 13)
                t[0] = t[0] xor rotl(t[3] + t[2], 18)
                for (m in 0 until 4) w[4 * j + (j + m) % 4] = t[m]
            }
            for (m in 0 until 16) x[m] = w[m]
        }
        if (h) {
            for (i in 0 until 16) x[i] += y[i]
            for (i in 0 until 4) {
                x[5 * i] -= ld32(c, 4 * i)
                x[6 + i] -= ld32(inp, 4 * i)
            }
            for (i in 0 until 4) {
                st32(out, 4 * i, x[5 * i])
                st32(out, 16 + 4 * i, x[6 + i])
            }
        } else {
            for (i in 0 until 16) st32(out, 4 * i, x[i] + y[i])
        }
    }

    private fun hsalsa20(out: ByteArray, inp: ByteArray, k: ByteArray) = core(out, inp, k, SIGMA, true)

    /** Flux Salsa20 (nonce de 8 octets à partir de `nOff`) combiné par OU exclusif avec `m` (ou des zéros). */
    private fun salsa20Xor(c: ByteArray, m: ByteArray?, len: Int, n: ByteArray, nOff: Int, k: ByteArray) {
        if (len == 0) return
        val z = ByteArray(16); val x = ByteArray(64)
        for (i in 0 until 8) z[i] = n[nOff + i]
        var b = len; var pos = 0
        while (b >= 64) {
            core(x, z, k, SIGMA, false)
            for (i in 0 until 64) c[pos + i] = ((m?.get(pos + i)?.toInt() ?: 0) xor x[i].toInt()).toByte()
            var u = 1
            for (i in 8 until 16) { u += z[i].toInt() and 255; z[i] = u.toByte(); u = u ushr 8 }
            b -= 64; pos += 64
        }
        if (b > 0) {
            core(x, z, k, SIGMA, false)
            for (i in 0 until b) c[pos + i] = ((m?.get(pos + i)?.toInt() ?: 0) xor x[i].toInt()).toByte()
        }
    }

    private fun xsalsa20Xor(c: ByteArray, m: ByteArray?, len: Int, n: ByteArray, k: ByteArray) {
        val s = ByteArray(32)
        hsalsa20(s, n, k)
        salsa20Xor(c, m, len, n, 16, s)
    }

    /* ------------------------------------------------------------------ Poly1305 */
    private val MINUSP = intArrayOf(5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 252)

    private fun add1305(h: IntArray, c: IntArray) {
        var u = 0
        for (j in 0 until 17) { u += h[j] + c[j]; h[j] = u and 255; u = u ushr 8 }
    }

    private fun onetimeauth(out: ByteArray, outOff: Int, m: ByteArray, mOff: Int, len: Int, k: ByteArray) {
        val x = IntArray(17); val r = IntArray(17); val h = IntArray(17); val c = IntArray(17); val g = IntArray(17)
        for (j in 0 until 16) r[j] = k[j].toInt() and 255
        r[3] = r[3] and 15; r[4] = r[4] and 252; r[7] = r[7] and 15; r[8] = r[8] and 252
        r[11] = r[11] and 15; r[12] = r[12] and 252; r[15] = r[15] and 15
        var n = len; var p = mOff
        while (n > 0) {
            for (j in 0 until 17) c[j] = 0
            var j = 0
            while (j < 16 && j < n) { c[j] = m[p + j].toInt() and 255; j++ }
            c[j] = 1
            p += j; n -= j
            add1305(h, c)
            for (i in 0 until 17) {
                var s = 0L
                for (jj in 0 until 17) s += h[jj].toLong() * (if (jj <= i) r[i - jj] else 320 * r[i + 17 - jj]).toLong()
                x[i] = s.toInt()
            }
            for (i in 0 until 17) h[i] = x[i]
            var u = 0
            for (jj in 0 until 16) { u += h[jj]; h[jj] = u and 255; u = u ushr 8 }
            u += h[16]; h[16] = u and 3
            u = 5 * (u ushr 2)
            for (jj in 0 until 16) { u += h[jj]; h[jj] = u and 255; u = u ushr 8 }
            u += h[16]; h[16] = u
        }
        for (j in 0 until 17) g[j] = h[j]
        add1305(h, MINUSP)
        val s = -(h[16] ushr 7)
        for (j in 0 until 17) h[j] = h[j] xor (s and (g[j] xor h[j]))
        for (j in 0 until 16) c[j] = k[j + 16].toInt() and 255
        c[16] = 0
        add1305(h, c)
        for (j in 0 until 16) out[outOff + j] = h[j].toByte()
    }

    private fun verify16(a: ByteArray, aOff: Int, b: ByteArray, bOff: Int): Boolean {
        var d = 0
        for (i in 0 until 16) d = d or (a[aOff + i].toInt() xor b[bOff + i].toInt())
        return d and 255 == 0
    }

    /* ------------------------------------------------------------------ secretbox */
    /** Chiffre `msg` : renvoie étiquette (16 octets) + texte chiffré, comme `nacl.secretbox`. */
    fun secretbox(msg: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray {
        require(nonce.size == SECRETBOX_NONCE && key.size == SECRETBOX_KEY) { "nonce ou clé invalide" }
        val d = msg.size + ZERO_BYTES
        val m = ByteArray(d); System.arraycopy(msg, 0, m, ZERO_BYTES, msg.size)
        val c = ByteArray(d)
        xsalsa20Xor(c, m, d, nonce, key)
        onetimeauth(c, 16, c, 32, d - 32, c)
        return c.copyOfRange(BOX_ZERO_BYTES, d)
    }

    /** Ouvre une boîte ; null si elle a été altérée ou si la clé est mauvaise. */
    fun secretboxOpen(box: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray? {
        if (nonce.size != SECRETBOX_NONCE || key.size != SECRETBOX_KEY || box.size < BOX_ZERO_BYTES) return null
        val d = box.size + BOX_ZERO_BYTES
        val c = ByteArray(d); System.arraycopy(box, 0, c, BOX_ZERO_BYTES, box.size)
        val x = ByteArray(32)
        xsalsa20Xor(x, null, 32, nonce, key)
        val tag = ByteArray(16)
        onetimeauth(tag, 0, c, 32, d - 32, x)
        if (!verify16(c, 16, tag, 0)) return null
        val m = ByteArray(d)
        xsalsa20Xor(m, c, d, nonce, key)
        return m.copyOfRange(ZERO_BYTES, d)
    }

    /* ------------------------------------------------------------------ X25519 */
    private fun gf() = LongArray(16)
    private val GF_121665 = longArrayOf(0xDB41, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    private fun car25519(o: LongArray) {
        for (i in 0 until 16) {
            o[i] += 1L shl 16
            val c = o[i] shr 16
            if (i < 15) o[i + 1] += c - 1 else o[0] += 38 * (c - 1)
            o[i] -= c shl 16
        }
    }

    private fun sel25519(p: LongArray, q: LongArray, b: Int) {
        val c = (b - 1).toLong().inv()
        for (i in 0 until 16) { val t = c and (p[i] xor q[i]); p[i] = p[i] xor t; q[i] = q[i] xor t }
    }

    private fun pack25519(o: ByteArray, n: LongArray) {
        val m = gf(); val t = n.copyOf()
        car25519(t); car25519(t); car25519(t)
        for (j in 0 until 2) {
            m[0] = t[0] - 0xffed
            for (i in 1 until 15) { m[i] = t[i] - 0xffff - ((m[i - 1] shr 16) and 1); m[i - 1] = m[i - 1] and 0xffff }
            m[15] = t[15] - 0x7fff - ((m[14] shr 16) and 1)
            val b = ((m[15] shr 16) and 1).toInt()
            m[14] = m[14] and 0xffff
            sel25519(t, m, 1 - b)
        }
        for (i in 0 until 16) { o[2 * i] = (t[i] and 0xff).toByte(); o[2 * i + 1] = (t[i] shr 8).toByte() }
    }

    private fun unpack25519(o: LongArray, n: ByteArray) {
        for (i in 0 until 16) o[i] = (n[2 * i].toLong() and 0xff) + ((n[2 * i + 1].toLong() and 0xff) shl 8)
        o[15] = o[15] and 0x7fff
    }

    private fun add(o: LongArray, a: LongArray, b: LongArray) { for (i in 0 until 16) o[i] = a[i] + b[i] }
    private fun sub(o: LongArray, a: LongArray, b: LongArray) { for (i in 0 until 16) o[i] = a[i] - b[i] }
    private fun mul(o: LongArray, a: LongArray, b: LongArray) {
        val t = LongArray(31)
        for (i in 0 until 16) for (j in 0 until 16) t[i + j] += a[i] * b[j]
        for (i in 0 until 15) t[i] += 38 * t[i + 16]
        for (i in 0 until 16) o[i] = t[i]
        car25519(o); car25519(o)
    }
    private fun sqr(o: LongArray, a: LongArray) = mul(o, a, a)

    private fun inv25519(o: LongArray, inp: LongArray) {
        val c = inp.copyOf()
        for (a in 253 downTo 0) { sqr(c, c); if (a != 2 && a != 4) mul(c, c, inp) }
        for (a in 0 until 16) o[a] = c[a]
    }

    fun scalarMult(n: ByteArray, p: ByteArray): ByteArray {
        val z = n.copyOf(32)
        z[31] = ((n[31].toInt() and 127) or 64).toByte()
        z[0] = (z[0].toInt() and 248).toByte()
        val x = gf(); unpack25519(x, p)
        val a = gf(); val b = x.copyOf(); val c = gf(); val d = gf(); val e = gf(); val f = gf()
        a[0] = 1; d[0] = 1
        for (i in 254 downTo 0) {
            val r = ((z[i ushr 3].toInt() and 255) ushr (i and 7)) and 1
            sel25519(a, b, r); sel25519(c, d, r)
            add(e, a, c); sub(a, a, c); add(c, b, d); sub(b, b, d); sqr(d, e); sqr(f, a); mul(a, c, a); mul(c, b, e)
            add(e, a, c); sub(a, a, c); sqr(b, a); sub(c, d, f); mul(a, c, GF_121665); add(a, a, d); mul(c, c, a)
            mul(a, d, f); mul(d, b, x); sqr(b, e)
            sel25519(a, b, r); sel25519(c, d, r)
        }
        val ci = gf(); inv25519(ci, c)
        mul(a, a, ci)
        val q = ByteArray(32); pack25519(q, a)
        return q
    }

    private val BASE = ByteArray(32).also { it[0] = 9 }
    fun scalarMultBase(n: ByteArray) = scalarMult(n, BASE)

    class KeyPair(val publicKey: ByteArray, val secretKey: ByteArray)

    fun boxKeyPair(): KeyPair { val sk = randomBytes(32); return KeyPair(scalarMultBase(sk), sk) }

    private fun boxBefore(publicKey: ByteArray, secretKey: ByteArray): ByteArray {
        val s = scalarMult(secretKey, publicKey)
        val k = ByteArray(32)
        hsalsa20(k, ByteArray(16), s)
        return k
    }

    fun box(msg: ByteArray, nonce: ByteArray, publicKey: ByteArray, secretKey: ByteArray): ByteArray =
        secretbox(msg, nonce, boxBefore(publicKey, secretKey))

    fun boxOpen(box: ByteArray, nonce: ByteArray, publicKey: ByteArray, secretKey: ByteArray): ByteArray? =
        if (publicKey.size != 32 || secretKey.size != 32) null else secretboxOpen(box, nonce, boxBefore(publicKey, secretKey))

    fun hash(msg: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(msg)
}
