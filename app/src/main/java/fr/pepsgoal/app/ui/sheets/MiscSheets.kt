package fr.pepsgoal.app.ui.sheets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.screens.HeroFlame
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.saveAvatar
import fr.pepsgoal.core.model.Avatar
import fr.pepsgoal.core.model.GoalGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File

/* ================================================================== icône de profil */
@Composable
fun AvatarSheet(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return; val ctx = LocalContext.current
    var d by remember { mutableStateOf(u.avatar.copy()) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }; var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ui.scope.launch {
            val bytes = withContext(Dispatchers.IO) { Media.decode(ctx, uri, 1024)?.let { Media.jpeg(Media.square(it, 384), 85) } }
            if (bytes == null) { ui.toast("Impossible de lire cette image. Essaie un fichier JPG ou PNG.", "image"); return@launch }
            val f = File(ctx.cacheDir, "avatar-preview-${nowMs()}.jpg").apply { writeBytes(bytes) }
            photo = bytes; d = d.copy().apply { kind = "photo"; this.photo = Uri.fromFile(f).toString() }
        }
    }
    SheetHead(ui, "Icône de profil")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AvatarMedallion(104.dp, null, null, null, "Aperçu de l’icône") { Av(u, 104.dp, d) } }
    val kinds = listOf("initials" to "Initiales", "photo" to "Photo", "emoji" to "Emoji", "picto" to "Picto")
    PTabs(kinds.map { it.second to null }, kinds.indexOfFirst { it.first == d.kind }.coerceAtLeast(0), { i -> val k = kinds[i].first; d = d.copy().apply { kind = k }; if (k == "photo" && d.photo.isEmpty()) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
    when (d.kind) {
        "photo" -> { PButton(if (d.photo.isNotEmpty()) "Changer de photo" else "Choisir une photo", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = "image"); Muted("La photo est recadrée en carré. La couleur choisie forme l'anneau autour.", small = true) }
        "emoji" -> EmojiPicker(d.emoji) { e -> d = d.copy().apply { emoji = e } }
        "picto" -> FlowRowCompat(gap = 8.dp) { PICTOS.forEach { pc -> PickTile(d.picto == pc, pc, { d = d.copy().apply { picto = pc } }) { Pi(pc, size = 24.dp, tint = p.ink) } } }
        else -> Muted("Tes initiales sont tirées de ton nom affiché.", small = true)
    }
    SectionTitle("Couleur")
    FlowRowCompat(gap = 4.dp) { ACCENTS.forEachIndexed { i, a -> PSwatch(accentBrush(i), d.color == i, a.name, { d = d.copy().apply { color = i } }) } }
    SheetFoot {
        PButton("Annuler", { ui.close() }, kind = "ghost")
        PButton("Enregistrer", {
            if (d.kind == "photo" && d.photo.isEmpty()) { ui.toast("Choisis une photo, ou un autre type d’icône.", "image"); return@PButton }
            if (pg.store.acct) {
                busy = true
                ui.scope.launch { try { ui.cloud.saveAvatar(d, photo); ui.close(); ui.toast("Icône de profil mise à jour.") } catch (e: Exception) { ui.toast(e.message ?: "Enregistrement impossible.", "x") } finally { busy = false } }
            } else {
                // Sans compte : la photo reste dans le stockage privé de l'application
                photo?.let { b -> val f = File(ctx.filesDir, "avatar-${u.id}.jpg"); f.writeBytes(b); d.photo = Uri.fromFile(f).toString() + "?v=${nowMs()}" }
                u.avatar = d; pg.store.commit(); ui.close(); ui.toast("Icône de profil mise à jour.")
            }
        }, kind = "primary", enabled = !busy)
    }
}

/* ================================================================== documents légaux, notes de version */
private fun asset(ctx: Context, name: String): String = ctx.assets.open(name).bufferedReader().use { it.readText() }

@Composable
fun LegalSheet(ui: AppState, doc: String) {
    val p = LocalPal.current; val ctx = LocalContext.current
    val all = remember { Json.parseToJsonElement(asset(ctx, "docs/legal.json")).jsonObject }
    val docs = all["docs"]!!.jsonObject; val d = docs[doc]?.jsonObject ?: run { LaunchedEffect(Unit) { ui.close() }; return }
    SheetHead(ui, d["title"]!!.jsonPrimitive.content)
    Muted("Dernière mise à jour : ${all["updated"]!!.jsonPrimitive.content}", small = true)
    val links = TextLinkStyles(SpanStyle(color = p.type("objective").ink, fontWeight = FontWeight.Bold))
    d["sections"]!!.jsonArray.forEach { sec ->
        val a = sec.jsonArray
        Text(a[0].jsonPrimitive.content, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 8.dp))
        Text(AnnotatedString.fromHtml(a[1].jsonPrimitive.content.replace("<li>", "<li>• ").replace("</p>", "</p><br>"), links), fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, color = p.ink2)
    }
    SectionTitle("Autres documents")
    FlowRowCompat(gap = 6.dp) { docs.filterKeys { it != doc }.forEach { (k, x) -> PButton(x.jsonObject["title"]!!.jsonPrimitive.content, { ui.sheet = Sheet.Legal(k) }, small = true) } }
}

@Composable
fun NotesSheet(ui: AppState) {
    val p = LocalPal.current; val ctx = LocalContext.current
    val notes = remember { Json.parseToJsonElement(asset(ctx, "docs/notes.json")).jsonArray }
    var open by remember { mutableStateOf(setOf(0)) }
    SheetHead(ui, "Notes de version")
    notes.forEachIndexed { i, n ->
        val a = n.jsonArray; val v = a[0].jsonPrimitive.content; val on = i in open
        val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val press = rememberPress(inter)
        Column(Modifier.fillMaxWidth().chunk(p.surface, p.edge, 22.dp, 4.dp, 2.dp, press).clip(RoundedCornerShape(22.dp)).springClick(inter, label = v) { open = if (on) open - i else open + i }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(v, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold); if (v == APP_VERSION) Chip("Actuelle", color = p.flame)
                Spacer(Modifier.weight(1f)); Muted(a[1].jsonPrimitive.content, small = true); Pi(if (on) "chevronup" else "chevron", size = 18.dp, tint = p.ink3)
            }
            if (on) a[2].jsonArray.forEach { Text("• " + it.jsonPrimitive.content, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink2, lineHeight = 21.sp) }
        }
    }
    Muted("Le détail technique de chaque version est publié sur GitHub.", small = true)
}

/* ================================================================== export */
@Composable
fun ExportSheet(ui: AppState, json: String) {
    val ctx = LocalContext.current
    SheetHead(ui, "Exporter mes données")
    Muted("Garde ce fichier en lieu sûr : il contient tes objectifs, validations, publications, stories et messages déchiffrés.")
    Text(json.take(4000) + if (json.length > 4000) "\n…" else "", Modifier.fillMaxWidth().background(LocalPal.current.surface2, RoundedCornerShape(17.dp)).padding(12.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
    SheetFoot {
        PButton("Copier", { copyText(ctx, ui, json) }, icon = "copy")
        PButton("Enregistrer ou partager", {
            val f = File(ctx.cacheDir, "exports").apply { mkdirs() }.let { File(it, "pepsgoal-${today()}.json") }; f.writeText(json)
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Exporter mes données"))
        }, icon = "archive", kind = "primary")
    }
}

/* ================================================================== groupes d'objectifs */
@Composable
fun GoalGroupsSheet(ui: AppState) {
    ui.watch()
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    var name by remember { mutableStateOf("") }; var icon by remember { mutableStateOf("📁") }; var pickFor by remember { mutableStateOf<String?>(null) }
    SheetHead(ui, "Groupes d’objectifs")
    Muted("Range tes objectifs par thème. Chaque objectif appartient à un groupe au plus ; supprimer un groupe ne supprime pas ses objectifs.")
    @Composable fun IconPick(cur: String, key: String, set: (String) -> Unit) {
        PickTile(pickFor == key, "Choisir l’icône", { pickFor = if (pickFor == key) null else key }) { Emoji(cur, 26.dp) }
    }
    u.goalGroups.toList().forEach { gr ->
        var n by remember(gr.id) { mutableStateOf(gr.name) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconPick(gr.icon, gr.id) {}
            Field(n, { n = it; if (it.isNotBlank()) { gr.name = it.trim(); pg.store.commit() } }, "Nom du groupe", Modifier.weight(1f), max = 30)
            MiniPlaque("${u.goalGroupOf.values.count { it == gr.id }}")
            IconBtn("trash", "Supprimer le groupe ${gr.name}", { u.goalGroups.removeAll { it.id == gr.id }; u.goalGroupOf.entries.removeAll { it.value == gr.id }; if (ui.groupFilter == gr.id) ui.groupFilter = "all"; pg.store.commit(); ui.toast("Groupe supprimé : ses objectifs sont conservés.", "trash") }, plain = true)
        }
        if (pickFor == gr.id) FlowRowCompat(gap = 6.dp) { GROUP_ICONS.forEach { e -> PickTile(gr.icon == e, e, { gr.icon = e; pickFor = null; pg.store.commit() }, 44.dp) { Emoji(e, 24.dp) } } }
        // Couleur du dossier (pastille dans les filtres et en-tête de section)
        FlowRowCompat(Modifier.padding(start = 52.dp), gap = 0.dp) {
            (listOf<String?>(null) + GROUP_COLORS).forEach { c ->
                PSwatch(androidx.compose.ui.graphics.SolidColor(c?.let { fr.pepsgoal.app.ui.theme.hex(it) } ?: p.surface2), gr.color == c, c ?: "Sans couleur", { gr.color = c; pg.store.commit(); ui.bump() }, 32.dp)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconPick(icon, "new") {}
        Field(name, { name = it }, "Nouveau groupe (Sport, Travail…)", Modifier.weight(1f), max = 30)
        PButton("Créer", {
            if (name.isBlank()) return@PButton
            u.goalGroups.add(GoalGroup(uid(), name.trim(), icon)); name = ""; icon = "📁"; pg.store.commit()
        }, icon = "plus", small = true, color = p.type("project").c, enabled = name.isNotBlank())
    }
    if (pickFor == "new") FlowRowCompat(gap = 6.dp) { GROUP_ICONS.forEach { e -> PickTile(icon == e, e, { icon = e; pickFor = null }, 44.dp) { Emoji(e, 24.dp) } } }
    SheetFoot { PButton("Terminé", { ui.close() }, kind = "primary") }
}

/** Couleurs proposées pour les dossiers d'objectifs. */
val GROUP_COLORS = listOf("#FF5A5F", "#FF9A3D", "#FFD23F", "#2EC27E", "#22D3FF", "#4D7CFF", "#9B5CFF", "#FF7AD9")
