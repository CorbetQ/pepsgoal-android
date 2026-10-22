package fr.pepsgoal.app.sys

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import fr.pepsgoal.app.PepsApp
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.Goal
import kotlinx.serialization.json.*

/**
 * Partage d'un objectif par lien (pepsgoal://goal?d=…) ou par code QR : le lien contient le titre, l'icône, le type
 * et les réglages de l'objectif (jamais son historique). Un ami l'ouvre ou le scanne pour créer sa propre copie.
 */
object GoalLink {
    /** Clés propres à une copie (historique, état du jour, liens personnels) : jamais partagées. */
    private val PRIVATE = setOf("milestones", "monthsDone", "weeksDone", "stepsDay", "stepsDone", "after", "targetHit", "winner", "failDay",
        "communityId", "communityEnd", "communityStart", "duo", "duoPeer", "duoPeerDays", "missWhy", "skipDays", "autoPlace", "remindAt", "trialDays", "trialStart", "trialDone", "after", "afterDelay")

    fun link(g: Goal): String {
        val o = buildJsonObject {
            put("v", 1); put("t", g.title); put("i", g.icon); put("y", g.type)
            put("d", JsonObject(g.data.filterKeys { it !in PRIVATE }))
        }
        return "pepsgoal://goal?d=" + Base64.encodeToString(o.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** Contenu d'un lien (ou d'un code QR) d'objectif, ou null s'il n'en est pas un. */
    fun decode(raw: String): JsonObject? = try {
        val d = android.net.Uri.parse(raw.trim()).takeIf { it.scheme == "pepsgoal" && it.host == "goal" }?.getQueryParameter("d") ?: return null
        JSON.parseToJsonElement(String(Base64.decode(d, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))).jsonObject.takeIf { it["t"] != null && it["y"] != null }
    } catch (e: Exception) { null }

    fun create(app: PepsApp, o: JsonObject): Goal? {
        val pg = app.pg; val u = pg.me() ?: return null
        val type = o["y"]?.jsonPrimitive?.content?.takeIf { it in TYPES && it !in setOf("group", "duel") } ?: return null
        val g = Goal(uid(), u.id, type, (o["t"]?.jsonPrimitive?.content ?: "Objectif").take(80), o["i"]?.jsonPrimitive?.content ?: "🎯", "", u.settings.defaultVis, nowMs())
        (o["d"] as? JsonObject)?.forEach { (k, v) -> if (k !in PRIVATE) g.data[k] = v }
        // Dates propres à la copie : on repart d'aujourd'hui
        if (type == "abstinence") g.data["since"] = JsonPrimitive(today())
        if (type in setOf("challenge", "program")) g.data["start"] = JsonPrimitive(today())
        pg.db.goals.add(g); pg.store.commit()
        return g
    }

    /** Code QR (noir sur blanc, avec marge) du texte donné. */
    fun qr(text: String, size: Int = 720): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size) { i -> if (m[i % size, i / size]) 0xFF10102A.toInt() else 0xFFFFFFFF.toInt() }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }
}

/**
 * Tags NFC : l'objectif est écrit sur le tag (pepsgoal://nfc/<objectif>) ; poser ensuite le téléphone dessus,
 * même application fermée, ouvre PepsGoal et valide l'objectif.
 */
object NfcTags {
    fun available(ctx: Context) = NfcAdapter.getDefaultAdapter(ctx) != null
    fun enabled(ctx: Context) = NfcAdapter.getDefaultAdapter(ctx)?.isEnabled == true

    fun activity(ctx: Context): Activity? { var c = ctx; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }

    /** Attend un tag et y écrit l'objectif ; [done] reçoit null en cas de succès, sinon le message d'erreur. */
    fun pair(ctx: Context, goalId: String, done: (String?) -> Unit) {
        val act = activity(ctx) ?: return done("Impossible d’utiliser le NFC ici.")
        val nfc = NfcAdapter.getDefaultAdapter(act) ?: return done("Ce téléphone n’a pas de NFC.")
        val main = Handler(Looper.getMainLooper())
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        nfc.enableReaderMode(act, { tag ->
            val msg = NdefMessage(arrayOf(NdefRecord.createUri("pepsgoal://nfc/$goalId"), NdefRecord.createApplicationRecord(act.packageName)))
            val err = try {
                val ndef = Ndef.get(tag)
                if (ndef != null) {
                    ndef.connect()
                    try { if (!ndef.isWritable) "Ce tag est verrouillé en écriture." else if (ndef.maxSize < msg.toByteArray().size) "Ce tag est trop petit." else { ndef.writeNdefMessage(msg); null } } finally { ndef.close() }
                } else NdefFormatable.get(tag)?.let { f -> f.connect(); try { f.format(msg); null } finally { f.close() } } ?: "Ce tag n’est pas compatible."
            } catch (e: Exception) { "Écriture interrompue : garde le téléphone immobile sur le tag." }
            main.post { try { nfc.disableReaderMode(act) } catch (e: Exception) {}; done(err) }
        }, flags, null)
    }

    fun cancel(ctx: Context) { val act = activity(ctx) ?: return; try { NfcAdapter.getDefaultAdapter(act)?.disableReaderMode(act) } catch (e: Exception) {} }
}
