package fr.pepsgoal.core

import fr.pepsgoal.core.crypto.E2e
import fr.pepsgoal.core.crypto.Nacl
import fr.pepsgoal.core.model.Db
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Sauvegarde chiffrée : contenu compressé puis scellé (NaCl secretbox) avec une clé tirée du mot de passe (PBKDF2).
 * Sans le mot de passe, le fichier est illisible, y compris pour Google Drive ou la personne qui le trouve.
 */
object Backup {
    const val ITER = 310_000
    const val FORMAT = "pepsgoal-backup"

    fun newSalt(): String = E2e.b64(Nacl.randomBytes(16))
    fun key(password: String, salt: String, iter: Int = ITER): ByteArray = E2e.deriveVault(password, salt, iter)

    fun seal(plain: String, key: ByteArray, salt: String): String {
        val z = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(plain.toByteArray(Charsets.UTF_8)) } }.toByteArray()
        val n = Nacl.randomBytes(24)
        return buildJsonObject {
            put("format", FORMAT); put("v", 1); put("at", nowMs()); put("salt", salt); put("iter", ITER)
            put("nonce", E2e.b64(n)); put("box", E2e.b64(Nacl.secretbox(z, n, key)))
        }.toString()
    }

    /** Ouvre une sauvegarde ; null si le fichier n'en est pas une ou si le mot de passe est faux. */
    fun open(raw: String, password: String): String? {
        val o = try { JSON.parseToJsonElement(raw).jsonObject } catch (e: Exception) { return null }
        if (o["format"]?.jsonPrimitive?.content != FORMAT) return null
        val salt = o["salt"]?.jsonPrimitive?.content ?: return null
        val iter = o["iter"]?.jsonPrimitive?.intOrNull ?: ITER
        val n = E2e.unb64(o["nonce"]?.jsonPrimitive?.content ?: return null); val box = E2e.unb64(o["box"]?.jsonPrimitive?.content ?: return null)
        val z = Nacl.secretboxOpen(box, n, key(password, salt, iter)) ?: return null
        return GZIPInputStream(ByteArrayInputStream(z)).use { it.readBytes() }.toString(Charsets.UTF_8)
    }

    fun isBackup(raw: String) = try { JSON.parseToJsonElement(raw).jsonObject["format"]?.jsonPrimitive?.content == FORMAT } catch (e: Exception) { false }

    /**
     * Restauration par fusion : objectifs et validations absents de l'appareil sont ajoutés, au nom du profil actuel.
     * Rien n'est effacé. Renvoie le nombre d'objectifs ajoutés.
     */
    fun merge(pg: Pg, b: Db): Int {
        val from = b.session ?: b.users.firstOrNull()?.id ?: return 0; val to = pg.meId().ifEmpty { return 0 }
        val haveG = pg.db.goals.map { it.id }.toHashSet(); val haveC = pg.db.checkins.map { it.id }.toHashSet()
        val added = b.goals.filter { it.userId == from && it.id !in haveG }
        added.forEach { g -> g.userId = to; g.members = g.members?.map { if (it == from) to else it }?.toMutableList() }
        pg.db.goals = (pg.db.goals + added).toMutableList()
        val ids = pg.db.goals.map { it.id }.toHashSet()
        val checks = b.checkins.filter { it.userId == from && it.id !in haveC && it.goalId in ids }.onEach { it.userId = to }
        pg.db.checkins = (pg.db.checkins + checks).toMutableList()
        val bu = b.users.firstOrNull { it.id == from }; val me = pg.me()
        if (bu != null && me != null) {
            me.restDays = (me.restDays + bu.restDays).distinct().sorted().toMutableList()
            me.goalGroups = (me.goalGroups + bu.goalGroups.filter { g -> me.goalGroups.none { it.id == g.id } }).toMutableList()
            bu.goalGroupOf.forEach { (k, v) -> if (k !in me.goalGroupOf && k in ids) me.goalGroupOf[k] = v }
        }
        pg.store.commit()
        return added.size
    }
}
