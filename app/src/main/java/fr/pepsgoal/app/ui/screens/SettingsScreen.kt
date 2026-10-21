package fr.pepsgoal.app.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.sys.LiveUpdates
import fr.pepsgoal.app.sys.Notifier
import fr.pepsgoal.app.sys.Poller
import fr.pepsgoal.app.sys.Updater
import fr.pepsgoal.app.sys.Reminders
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.app.ui.theme.hex
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.*
import fr.pepsgoal.core.model.Db
import fr.pepsgoal.core.model.Place
import fr.pepsgoal.core.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

val SET_CATS = listOf(
    listOf("appearance", "Apparence", "Thème, Material You, couleur d’accent, arrière-plan, icône de profil", "palette"),
    listOf("reminders", "Rappels et notifications", "Rappels, notifications, activités en cours (Live Updates)", "bell"),
    listOf("account", "Compte", "Profil, mot de passe, déconnexion", "user"),
    listOf("weather", "Météo", "Ville, carte du jour et thème Météo", "cloud"),
    listOf("privacy", "Social et confidentialité", "Fonctions sociales, visibilité, stories", "lock"),
    listOf("data", "Données", "Synchronisation, export, réinitialisation", "archive"),
    listOf("a11y", "Accessibilité", "Contraste élevé, daltonisme, monochrome, animations, transparence", "eye"),
    listOf("help", "Aide", "Tutoriel, notes de version et à propos", "star")
)

/** Couleurs des catégories de réglages (reprises des couleurs des types d'objectifs). */
val SET_COLORS = mapOf("appearance" to ("measure" to "abstinence"), "reminders" to ("period" to "project"), "account" to ("routine" to "duration"),
    "weather" to ("objective" to "period"), "privacy" to ("abstinence" to "program"), "data" to ("challenge" to "limit"), "a11y" to ("duration" to "objective"), "help" to ("monthly" to "challenge"))

/** Écran plein (au-dessus des onglets) avec un en-tête « retour », sur un fond coloré propre à la page. */
@Composable
fun ScreenFrame(ui: AppState, title: String, back: String, onBack: () -> Unit, right: @Composable RowScope.() -> Unit = {},
                icon: String? = null, color: Color? = null, color2: Color? = null, bottom: (@Composable () -> Unit)? = null,
                state: androidx.compose.foundation.lazy.LazyListState? = null, content: LazyListScope.() -> Unit) {
    val p = LocalPal.current
    val c1 = color ?: p.flame; val c2 = color2 ?: p.pink
    BackHandler { onBack() }
    remember(title) { Stagger.reset() }
    Box(Modifier.fillMaxSize()) { CompositionLocalProvider(LocalHaze provides null) {
        PageBackdrop(c1, c2)
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PButton(back, onBack, icon = "chevronleft", small = true)
                Spacer(Modifier.weight(1f)); right()
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state ?: androidx.compose.foundation.lazy.rememberLazyListState(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item("title") {
                    Row(Modifier.enter().padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (icon != null) IconTile(icon, c1, c2, 54.dp, 18.dp)
                        Text(title, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp)
                    }
                }
                content()
                if (bottom == null) item("nav") { Spacer(Modifier.navigationBarsPadding()) }
            }
            // Zone fixe en bas de page (saisie d'un commentaire…)
            if (bottom != null) Box(Modifier.fillMaxWidth().navigationBarsPadding()) { bottom() }
        }
    } }
}

@Composable
fun SettingsScreen(ui: AppState, cat: String?) {
    ui.watch(); ui.themeTick
    val u = ui.pg.me() ?: return
    val c = SET_CATS.firstOrNull { it[0] == cat }
    val p = LocalPal.current
    if (c == null) {
        ScreenFrame(ui, "Paramètres", "Profil", { ui.back() }, icon = "gear") {
            item("cats") {
                Glass(pad = 8.dp) {
                    SET_CATS.forEach { (k, l, d, i) ->
                        val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Row(Modifier.fillMaxWidth().bounce(inter, .97f).clip(RoundedCornerShape(16.dp)).springClick(inter) { ui.screens[ui.screens.lastIndex] = Screen.Settings(k) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val (k1, k2) = SET_COLORS[k] ?: ("routine" to "duration")
                            IconTile(i, p.type(k1).c, p.type(k2).c)
                            Column(Modifier.weight(1f)) { Text(l, fontWeight = FontWeight.ExtraBold); Muted(d, small = true) }
                            Text("›", color = p.ink3, fontSize = 22.sp)
                        }
                    }
                }
            }
        }
        return
    }
    val (k1, k2) = SET_COLORS[c[0]] ?: ("routine" to "duration")
    ScreenFrame(ui, c[1], "Paramètres", { ui.screens[ui.screens.lastIndex] = Screen.Settings() }, icon = c[3], color = p.type(k1).c, color2 = p.type(k2).c) {
        when (c[0]) {
            "appearance" -> item("b") { Appearance(ui, u) }
            "reminders" -> item("b") { RemindersSettings(ui, u) }
            "account" -> item("b") { AccountSettings(ui, u) }
            "weather" -> item("b") { WeatherSettings(ui, u) }
            "privacy" -> item("b") { PrivacySettings(ui, u) }
            "data" -> item("b") { DataSettings(ui, u) }
            "a11y" -> item("b") { Glass { A11yRows(ui); Muted("Ces réglages s’appliquent à cet appareil, pour tous les comptes, dès l’écran de connexion.", Modifier.padding(top = 10.dp), small = true) } }
            else -> item("b") { HelpSettings(ui) }
        }
    }
}

private fun save(ui: AppState) { ui.pg.store.commit() }

/* ------------------------------------------------------------------ Apparence */
@Composable
private fun Appearance(ui: AppState, u: User) {
    val p = LocalPal.current; val s = u.settings; val ctx = LocalContext.current
    val dynOk = Build.VERSION.SDK_INT >= 31
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Glass {
            SetRow("Couleurs Material You", if (dynOk) "Les couleurs de ton fond d’écran Android sur toute l’interface : panneaux, boutons, flammes, navigation, et couleurs des types harmonisées." else "Nécessite Android 12 ou plus récent.") {
                PSwitch(ui.prefs.materialYou && dynOk, { ui.prefs.materialYou = it; ui.themeTick++; ui.toast(if (it) "Couleurs Material You activées." else "Couleurs PepsGoal rétablies.", "palette") }, enabled = dynOk)
            }
            if (ui.prefs.materialYou && dynOk) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    listOf(p.flame2, p.flame, p.pink, p.glassStrong, p.bg).forEach { Box(Modifier.size(26.dp).clip(CircleShape).background(it).border(1.dp, p.line, CircleShape)) }
                }
                Muted("Réglage propre à cet appareil. La couleur d’accent ci-dessous reste enregistrée pour tes autres appareils.", small = true)
            }
        }
        Glass {
            SectionTitle("Thème")
            Muted("Clair, sombre, OLED (noir pur), Météo (ciel en direct) ou selon ton appareil", small = true)
            Spacer(Modifier.height(8.dp))
            FlowRowCompat(gap = 6.dp) { THEMES.forEach { (k, l) -> FChip(l, s.theme == k, { s.theme = k; save(ui); if (k == "weather" && s.weather == null) ui.toast("Choisis ta ville dans Paramètres › Météo pour un ciel en direct.", "cloud") }) } }
            SetRow("Carte « En cours » sur l’accueil", "Deadlines, projets, objectifs chiffrés et défis en cours, sous « À valider aujourd’hui »") {
                PSwitch(!s.hideOngoing, { s.hideOngoing = !it; save(ui); ui.toast(if (it) "Carte « En cours » affichée sur l’accueil." else "Carte « En cours » masquée.", "list") })
            }
            SectionTitle("Flou du verre givré")
            Muted(if (ui.prefs.transp || ui.prefs.contrast) "Sans effet : la transparence est désactivée dans Accessibilité." else "Intensité du flou de l’arrière-plan derrière les panneaux", small = true)
            Spacer(Modifier.height(8.dp))
            FlowRowCompat(gap = 6.dp) { BLURS.forEach { (k, l) -> FChip(l, s.glassBlur == k, { s.glassBlur = k; save(ui); ui.toast("Flou du verre : ${l.lowercase()}.", "palette") }) } }
            Spacer(Modifier.height(14.dp))
            SectionTitle("Opacité du verre givré")
            Muted(if (ui.prefs.transp || ui.prefs.contrast) "Sans effet : la transparence est désactivée dans Accessibilité." else "« Faible » laisse voir l’arrière-plan, « Fort » rend les panneaux presque opaques. Réglage propre à cet appareil.", small = true)
            Spacer(Modifier.height(8.dp))
            FlowRowCompat(gap = 6.dp) {
                listOf("low" to "Faible", "mid" to "Moyen", "high" to "Fort").forEach { (k, l) ->
                    FChip(l, ui.prefs.glassOpacity == k, { ui.prefs.glassOpacity = k; ui.themeTick++; ui.toast("Opacité du verre : ${l.lowercase()}.", "palette") })
                }
            }
        }
        Glass(Modifier.alpha(if (ui.prefs.materialYou && dynOk) .55f else 1f)) {
            SectionTitle("Couleur d'accent")
            Muted("${if (ui.prefs.materialYou && dynOk) "Remplacée par Material You sur cet appareil · " else ""}${if (validAccent(s.accentCustom)) "Tirée de ton arrière-plan" else (ACCENTS.getOrNull(s.accent) ?: ACCENTS[0]).name} · flammes, boutons et anneau de progression", small = true)
            Spacer(Modifier.height(10.dp))
            FlowRowCompat(gap = 8.dp) {
                ACCENTS.forEachIndexed { i, a ->
                    val on = s.accent == i && !validAccent(s.accentCustom)
                    Box(Modifier.size(38.dp).clip(CircleShape).background(accentBrush(i)).border(if (on) 3.dp else 1.dp, if (on) p.ink else p.line, CircleShape).clickable(onClickLabel = a.name) { s.accent = i; s.accentCustom = null; save(ui) })
                }
                if (validAccent(s.accentCustom)) Box(Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(s.accentCustom!!.map(::hex))).border(3.dp, p.ink, CircleShape))
            }
        }
        BackgroundSettings(ui, u)
        Glass {
            SetRow("Icône de profil", "Photo, emoji ou pictogramme") { Av(u, 44.dp); PButton("Changer", { ui.open(Sheet.Avatar) }, icon = "image", small = true) }
        }
    }
}

@Composable
private fun BackgroundSettings(ui: AppState, u: User) {
    val p = LocalPal.current; val s = u.settings; val ctx = LocalContext.current
    val file = bgFile(ctx); val has = remember(ui.prefs.bgStamp, ui.rev) { file.exists() }
    val cur = if (s.bg == "custom" && !has) "blob" else if (BGS.any { it.first == s.bg }) s.bg else "blob"
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ui.scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val b = Media.decode(ctx, uri, 1920) ?: return@withContext null
                file.writeBytes(Media.jpeg(b, 82)); Media.palette(b)
            }
            if (ok == null) { ui.toast("Impossible de lire cette image. Essaie un fichier JPG ou PNG.", "image"); return@launch }
            ui.prefs.bgPalette = ok; ui.prefs.bgStamp = nowMs(); s.bg = "custom"; save(ui); ui.themeTick++
            ui.toast("Arrière-plan personnalisé appliqué.", "image")
        }
    }
    Glass {
        SectionTitle("Arrière-plan")
        Muted("${BGS.first { it.first == cur }.second} · derrière toute l’application", small = true)
        if (s.theme == "weather") Note("Le thème Météo affiche le ciel en direct : change de thème pour choisir un arrière-plan.", "cloud", Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(10.dp))
        FlowRowCompat(Modifier.alpha(if (s.theme == "weather") .5f else 1f), gap = 8.dp) {
            BGS.forEach { (k, l) ->
                Column(Modifier.width(76.dp).clickable(enabled = s.theme != "weather") { if (k == "custom" && !has) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else { s.bg = k; save(ui) } },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(76.dp, 56.dp).clip(RoundedCornerShape(14.dp)).background(bgPreview(k, p)).border(if (cur == k) 3.dp else 1.dp, if (cur == k) p.ink else p.line, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        if (k == "custom" && has) AsyncImage(ImageRequest.Builder(ctx).data(file).memoryCacheKey("bg${ui.prefs.bgStamp}").crossfade(true).build(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else if (k == "custom") Pi("image")
                    }
                    Text(l, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        SetRow("Image personnalisée", "Une photo à toi en fond d’écran, gardée sur cet appareil.") {
            PButton(if (has) "Changer" else "Choisir", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = "image", small = true)
            if (has) PButton("Retirer", { file.delete(); ui.prefs.bgPalette = emptyList(); ui.prefs.bgStamp = nowMs(); if (s.bg == "custom") s.bg = "blob"; save(ui); ui.toast("Image d’arrière-plan retirée.", "image") }, kind = "ghost", small = true)
        }
        val pal = ui.prefs.bgPalette
        if (has && pal.isNotEmpty()) {
            SetRow("Couleurs de ton arrière-plan", "Extraites de ton image : touche-en une pour en faire ta couleur d’accent.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pal.forEach { c ->
                    val on = validAccent(s.accentCustom) && Media.accentFrom(c)[1] == s.accentCustom!![1]
                    Box(Modifier.size(36.dp).clip(CircleShape).background(hex(c)).border(if (on) 3.dp else 1.dp, if (on) p.ink else p.line, CircleShape)
                        .clickable(onClickLabel = "Utiliser la couleur $c") { s.accentCustom = Media.accentFrom(c); save(ui); ui.toast("Couleur d’accent tirée de ton arrière-plan.", "palette") })
                }
            }
        }
    }
}

private fun bgPreview(k: String, p: fr.pepsgoal.app.ui.theme.Pal): Brush = when (k) {
    "aurora" -> Brush.verticalGradient(listOf(p.bg, p.blobs[2].copy(alpha = .6f), p.blobs[3].copy(alpha = .5f)))
    "waves" -> Brush.verticalGradient(listOf(p.bg, p.blobs[0].copy(alpha = .55f)))
    "stars" -> Brush.radialGradient(listOf(p.ink.copy(alpha = .15f), p.bg))
    "mesh" -> Brush.linearGradient(listOf(p.blobs[0].copy(alpha = .7f), p.blobs[1].copy(alpha = .6f), p.blobs[2].copy(alpha = .6f)))
    "grid" -> Brush.linearGradient(listOf(p.bg, p.ink.copy(alpha = .08f), p.bg))
    "plain" -> Brush.linearGradient(listOf(p.bg, p.bg))
    "custom" -> Brush.linearGradient(listOf(p.glassStrong, p.glass))
    else -> Brush.radialGradient(listOf(p.blobs[0].copy(alpha = .8f), p.blobs[1].copy(alpha = .4f), p.bg))
}

/* ------------------------------------------------------------------ Rappels */
@Composable
fun TimeButton(value: String, onChange: (String) -> Unit, desc: String) {
    val ctx = LocalContext.current
    PButton(value, {
        val (h, m) = value.split(":").map { it.toIntOrNull() ?: 0 }.let { (it.getOrElse(0) { 8 }) to (it.getOrElse(1) { 30 }) }
        TimePickerDialog(ctx, { _, hh, mm -> onChange("${pad(hh)}:${pad(mm)}") }, h, m, true).apply { setTitle(desc) }.show()
    }, icon = "clock", small = true)
}

@Composable
private fun RemindersSettings(ui: AppState, u: User) {
    val s = u.settings; val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> s.browser = ok; save(ui); tick++; ui.toast(if (ok) "Notifications activées." else "Notifications refusées : tu peux les autoriser dans les réglages Android.", "bell") }
    val granted = remember(tick, ui.rev) { Notifier.allowed(ctx) }
    LifecycleResume { tick++ }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Glass {
            SetRow("Rappel du matin", "Le programme de ta journée") {
                TimeButton(s.morning.time, { s.morning.time = it; save(ui); Reminders.reschedule(ctx); ui.toast("Rappels mis à jour.", "bell") }, "Rappel du matin")
                PSwitch(s.morning.on, { s.morning.on = it; save(ui); Reminders.reschedule(ctx); ui.toast("Rappels mis à jour.", "bell") })
            }
            SetRow("Rappel du soir", "Seulement s'il te reste des validations") {
                TimeButton(s.evening.time, { s.evening.time = it; save(ui); Reminders.reschedule(ctx); ui.toast("Rappels mis à jour.", "bell") }, "Rappel du soir")
                PSwitch(s.evening.on, { s.evening.on = it; save(ui); Reminders.reschedule(ctx); ui.toast("Rappels mis à jour.", "bell") })
            }
            SetRow("Notifications", if (granted) "Rappels, encouragements, demandes d’amis et messages, même quand l’application est fermée." else "Autorise les notifications pour recevoir tes rappels quand PepsGoal est fermé.") {
                if (granted) Chip("Activées", "check", LocalPal.current.ok) else PButton("Autoriser", {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else Notifier.openSettings(ctx)
                }, icon = "bell", kind = "primary", small = true)
            }
            if (granted) SetRow("Catégories de notifications", "Choisis le son, la vibration et l’importance de chaque catégorie dans Android.") { PButton("Gérer", { Notifier.openSettings(ctx) }, icon = "gear", small = true) }
            if (!Reminders.canExact(ctx)) SetRow("Rappels à l’heure exacte", "Android peut retarder les rappels de quelques minutes. Autorise les alarmes exactes pour qu’ils arrivent pile à l’heure, et pour relever messages et publications chaque minute application fermée.") { PButton("Autoriser", { Reminders.openExactSettings(ctx) }, icon = "clock", small = true) }
            if (ui.pg.store.acct) {
                var unr by remember { mutableStateOf(Poller.unrestricted(ctx)) }
                var instant by remember { mutableStateOf(ui.prefs.instantNotifs) }
                var tick by remember { mutableIntStateOf(0) }
                androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { unr = Poller.unrestricted(ctx); tick++; onPauseOrDispose {} }
                SetRow("Notifications instantanées", "PepsGoal reste connecté en arrière-plan (petite notification discrète) pour relever messages, publications et notifications chaque minute, même fermée.") {
                    PSwitch(instant, { instant = it; ui.prefs.instantNotifs = it; Poller.startLink(ctx); ui.toast(if (it) "Connexion en direct activée." else "Connexion en direct désactivée.", "bell") })
                }
                SetRow("Optimisation de batterie", if (unr) "PepsGoal n’est pas limité par Android : les relèves continuent en veille." else "Android peut endormir PepsGoal et retarder les messages. Autorise-le à fonctionner en arrière-plan.") {
                    if (unr) Chip("Sans limite", "check", LocalPal.current.ok) else PButton("Autoriser", { Poller.askUnrestricted(ctx) }, icon = "bell", kind = "primary", small = true)
                }
                SetRow("Tester application fermée", "Ferme PepsGoal (bouton d’accueil) : une notification de confirmation arrive à la prochaine relève, en une à deux minutes.") {
                    PButton("Tester", { ui.prefs.pollTest = System.currentTimeMillis(); ui.toast("Ferme l’application et attends la notification.", "bell") }, icon = "clock", small = true)
                }
                val log = remember(tick) { ui.prefs.pollLog }
                if (log.isNotEmpty()) Column(Modifier.padding(top = 4.dp)) {
                    Muted("Dernières relèves en arrière-plan", small = true)
                    log.forEach { Text(it, fontSize = 12.sp, color = LocalPal.current.ink3, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                }
            }
            SetRow("Tester un rappel", "Envoie un rappel maintenant") {
                PButton("Tester", {
                    val pending = ui.pg.todayPlan(u.id).due.count { !it.done }
                    val text = if (pending > 0) "Rappel : il te reste ${pl(pending, "validation", "validations")} aujourd'hui." else "Rappel : tout est validé aujourd'hui, bravo !"
                    ui.pg.notify(u.id, text, "reminder"); save(ui); Notifier.test(ctx, text); ui.toast(text, "bell")
                }, icon = "clock", small = true)
            }
        }
        Glass {
            CardHead("Activités en cours") { Chip("Live Updates", "bolt", LocalPal.current.flame) }
            Muted("Minuteur d’un objectif de durée, période en cours (créneau à tenir) et compte à rebours de fin de journée quand ta série est en jeu : affichés en direct dans la barre d’état, l’écran de verrouillage et la Now Bar.", small = true)
            SetRow("Afficher les activités en cours") { PSwitch(ui.prefs.liveUpdates, { ui.prefs.liveUpdates = it; LiveUpdates.refresh(ctx); tick++ }) }
            if (ui.prefs.liveUpdates && LiveUpdates.supported() && !LiveUpdates.canPromote(ctx))
                SetRow("Mise en avant désactivée", "Android n’affiche pas les activités de PepsGoal en haut de l’écran. Réactive « Notifications en direct » dans les réglages de l’application.") { PButton("Réglages", { LiveUpdates.openPromotionSettings(ctx) }, icon = "gear", small = true) }
            if (!LiveUpdates.supported()) Muted("Sur cette version d’Android, les activités en cours s’affichent comme des notifications permanentes avec une barre de progression.", Modifier.padding(top = 6.dp), small = true)
        }
    }
}

/** Rappelé à chaque retour sur l'écran (après un passage dans les réglages Android). */
@Composable
fun LifecycleResume(onResume: () -> Unit) {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) onResume() }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

/* ------------------------------------------------------------------ Compte */
@Composable
private fun AccountSettings(ui: AppState, u: User) {
    val pg = ui.pg; val p = LocalPal.current
    var name by remember(u.id) { mutableStateOf(u.name) }; var bio by remember(u.id) { mutableStateOf(u.bio) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (pg.isGuest(u) && !pg.db.sandbox) GuestUpgrade(ui)
        Glass {
            CardHead("Mon profil")
            Field(name, { name = it }, "Nom affiché", max = 40, keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            Spacer(Modifier.height(8.dp))
            Field(bio, { bio = it }, "Bio", singleLine = false, minLines = 2, max = 160)
            Spacer(Modifier.height(10.dp))
            PButton("Enregistrer", {
                val n = name.trim()
                if (n.length < 2) { ui.toast("Indique ton nom (2 caractères minimum).", "x"); return@PButton }
                if (pg.store.acct) ui.run("Profil mis à jour.", "user") { ui.cloud.saveProfile(n, bio.trim()) }
                else { u.name = n; u.bio = bio.trim(); save(ui); ui.toast("Profil mis à jour.", "user") }
            }, kind = "primary")
        }
        if (pg.store.acct) { PasswordCard(ui); VaultCard(ui) }
        Glass {
            if (pg.isGuest(u)) SetRow("Mode sans compte", "Profil local créé ${ago(u.createdAt)}. Tu peux le reprendre depuis l'écran d'accueil avec « Reprendre sans compte ».")
            else if (!pg.db.sandbox) SetRow("Nom d'utilisateur", "@${u.username} · inscrit ${ago(u.createdAt)}")
            val ok = pg.db.sandbox || !pg.store.acct || pg.store.online
            SetRow(if (pg.db.sandbox) "Quitter la démonstration" else "Se déconnecter", if (pg.db.sandbox) "Les données d’exemple sont effacées." else if (!pg.store.acct) "Tes données restent sur cet appareil." else if (ok) "Tes données sont sauvegardées sur le serveur PepsGoal." else "Hors ligne : la déconnexion et le changement de compte reviendront avec la connexion.") {
                PButton(if (pg.db.sandbox) "Quitter" else "Se déconnecter", { signOut(ui) }, icon = "logout", small = true, enabled = ok)
            }
            if (!pg.isAdmin(u) && !pg.db.sandbox) SetRow("Supprimer mon compte", "Supprime ton profil, tes objectifs, tes publications, tes stories et tes messages. Définitif.") {
                PButton("Supprimer", {
                    if (pg.store.acct) ui.open(Sheet.DeleteAccount)
                    else ui.confirm("Supprimer ton profil ?", "Ton profil sans compte et tous ses objectifs seront supprimés définitivement de cet appareil.", "Supprimer mon profil") {
                        ui.close(); val db = pg.db; db.goals.removeAll { it.userId == u.id }; db.checkins.removeAll { it.userId == u.id }; db.timers.remove(u.id)
                        db.users.removeAll { it.id == u.id }; db.notifs.removeAll { it.userId == u.id }; db.session = null; save(ui); ui.sessionChanged(); ui.toast("Ton profil a été supprimé.", "trash")
                    }
                }, icon = "trash", kind = "danger", small = true)
            }
        }
    }
}

/** Déconnexion : démonstration, profil sans compte, ou compte (après synchronisation). */
fun signOut(ui: AppState) {
    val pg = ui.pg
    when {
        pg.db.sandbox -> { pg.store.use(pg.store.readLocal() ?: Db(), false); pg.db.session = null; ui.sessionChanged() }
        !pg.store.acct -> { pg.stopTimer(true); pg.db.session = null; pg.store.commit(); ui.sessionChanged() }
        else -> ui.run { ui.cloud.logout() }
    }
}

@Composable
private fun GuestUpgrade(ui: AppState) {
    var name by remember { mutableStateOf("") }; var user by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    Glass {
        CardHead("Créer un compte") { Chip("Mode sans compte", "lock", LocalPal.current.type("deadline").c) }
        Muted("Garde toutes tes données actuelles et débloque les fonctions sociales (amis, feed, stories, messagerie) et la synchronisation entre tes appareils.", Modifier.padding(bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(name, { name = it }, "Prénom et nom", max = 40, keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            Field(user, { user = it }, "Nom d'utilisateur", max = 20, keyboard = KeyboardOptions(keyboardType = KeyboardType.Ascii))
            Field(pass, { pass = it }, "Mot de passe", placeholder = "8 caractères minimum", password = true)
            LegalLine(ui, "En créant un compte, tu acceptes les CGU et la charte de confidentialité.")
            Err(err)
            PButton(if (busy) "…" else "Créer mon compte", {
                val un = user.trim().lowercase()
                err = when {
                    !ui.cloud.navOnline -> "Hors ligne : la création d’un compte nécessite internet."
                    name.trim().length < 2 -> "Indique ton nom (2 caractères minimum)."
                    !Regex("^[a-z0-9._]{3,20}$").matches(un) -> "Le nom d'utilisateur doit faire 3 à 20 caractères : lettres, chiffres, point ou tiret bas."
                    pass.length < 8 -> "Le mot de passe doit contenir au moins 8 caractères."
                    else -> null
                }
                if (err != null) return@PButton
                busy = true
                ui.scope.launch { try { ui.cloud.upgradeGuest(un, name.trim(), pass) } catch (e: Exception) { err = e.message ?: "Création impossible." } finally { busy = false } }
            }, Modifier.fillMaxWidth(), kind = "primary", enabled = !busy)
        }
    }
}

@Composable
private fun PasswordCard(ui: AppState) {
    var cur by remember { mutableStateOf("") }; var n1 by remember { mutableStateOf("") }; var n2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    Glass {
        CardHead("Mot de passe")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(cur, { cur = it }, "Mot de passe actuel", password = true)
            Field(n1, { n1 = it }, "Nouveau mot de passe", placeholder = "8 caractères minimum", password = true)
            Field(n2, { n2 = it }, "Confirmation", password = true)
            Err(err)
            PButton("Changer le mot de passe", {
                err = when { cur.isEmpty() -> "Indique ton mot de passe actuel."; n1.length < 8 -> "Le nouveau mot de passe doit contenir au moins 8 caractères."; n1 != n2 -> "Les deux mots de passe ne correspondent pas."; else -> null }
                if (err != null) return@PButton
                busy = true
                ui.scope.launch {
                    try { ui.cloud.changePassword(cur, n1); cur = ""; n1 = ""; n2 = ""; ui.toast("Mot de passe changé.", "lock") }
                    catch (e: ApiError) { err = if (e.status == 401) "Mot de passe actuel incorrect." else e.message } catch (e: Exception) { err = e.message }
                    finally { busy = false }
                }
            }, icon = "lock", kind = "primary", enabled = !busy)
        }
    }
}

/** Coffre de clés de chiffrement : synchronise les clés de conversation entre les appareils. */
@Composable
fun VaultCard(ui: AppState) {
    val p = LocalPal.current; val e2e = ui.cloud.e2e
    val on = e2e.hasVault(); val n = e2e.keyCount()
    Glass {
        CardHead("Clés de chiffrement") { Chip(if (on) "Synchronisées" else "Sur cet appareil", if (on) "check" else "lock", if (on) p.type("routine").c else p.type("deadline").c) }
        Muted(if (on) "${pl(n, "clé de conversation", "clés de conversation")}. Une copie chiffrée avec ton mot de passe est gardée sur le serveur : tes autres appareils retrouvent tes conversations dès la connexion. Ni l’éditeur ni l’hébergeur ne peuvent l’ouvrir."
            else "Tes clés de conversation ne sont pas encore synchronisées sur cet appareil.", small = true)
        if (!on) { Spacer(Modifier.height(10.dp)); VaultForm(ui) }
    }
}

@Composable
fun VaultForm(ui: AppState) {
    val e2e = ui.cloud.e2e; val locked = e2e.vaultIssue == "password"
    var pw by remember { mutableStateOf("") }; var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    fun go(reset: Boolean) {
        if (pw.isEmpty()) { err = if (reset) "Saisis d’abord ton mot de passe actuel dans le champ." else "Indique ton mot de passe."; return }
        busy = true; err = null
        ui.scope.launch {
            try {
                val r = e2e.unlockWithPassword(pw, reset)
                if (r == "locked") err = if (locked) "Ce mot de passe n’ouvre pas le coffre." else "Ton coffre a été créé avec un ancien mot de passe : saisis-le, ou recrée le coffre."
                else { pw = ""; ui.toast(if (reset) "Nouveau coffre de clés créé." else "Clés de chiffrement synchronisées.", "lock") }
            } catch (e: ApiError) { err = if (e.status == 401) "Mot de passe incorrect." else e.message } catch (e: Exception) { err = e.message }
            finally { busy = false; ui.bump() }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (locked) Text(rich("**Ton coffre de clés a été créé avec un ancien mot de passe.** Saisis cet ancien mot de passe pour le rouvrir, ou recrée le coffre avec ton mot de passe actuel (les clés absentes de tes appareils seront perdues)."), fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(pw, { pw = it }, if (locked) "Ancien mot de passe" else "Ton mot de passe", Modifier.weight(1f), password = true)
            PButton("Déverrouiller", { go(false) }, icon = "lock", kind = "primary", small = true, enabled = !busy)
        }
        if (locked) PButton("Recréer le coffre avec mon mot de passe actuel", { go(true) }, kind = "ghost", small = true, enabled = !busy)
        Err(err)
    }
}

/* ------------------------------------------------------------------ Météo */
@Composable
private fun WeatherSettings(ui: AppState, u: User) {
    val s = u.settings; val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }; var res by remember { mutableStateOf<List<Place>?>(null) }; var err by remember { mutableStateOf<String?>(null) }
    Glass {
        val pl0 = s.weather
        SetRow("Ville", pl0?.let { "${it.name}${if (it.admin1.isNotEmpty()) ", " + it.admin1 else ""} · ${it.country}" } ?: "Aucune ville choisie") {
            if (pl0 != null) PButton("Retirer", { s.weather = null; Wx.clear(ctx); save(ui) }, kind = "ghost", small = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(q, { q = it }, "Rechercher une ville", Modifier.weight(1f), placeholder = "Angers, Lyon, Bruxelles…", max = 60)
            PButton("Chercher", {
                if (q.trim().length < 2) return@PButton
                if (!ui.pg.store.online) { err = "Hors ligne : la recherche de ville nécessite internet."; return@PButton }
                ui.scope.launch { try { err = null; res = ui.cloud.places(q.trim()) } catch (e: Exception) { err = e.message ?: "Recherche impossible." } }
            }, icon = "pin", small = true)
        }
        Err(err)
        res?.let { list ->
            if (list.isEmpty()) Muted("Aucune ville trouvée.", Modifier.padding(top = 6.dp), small = true)
            list.forEach { r ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { s.weather = r; res = null; q = ""; Wx.clear(ctx); save(ui); ui.scope.launch { Wx.ensure(ctx, ui, true) }; ui.toast("Météo : ${r.name}.", "cloud") }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pi("pin"); Column { Text(r.name, fontWeight = FontWeight.ExtraBold); Muted(listOf(r.admin1, r.country).filter { it.isNotEmpty() }.joinToString(" · "), small = true) }
                }
            }
        }
        SetRow("Carte météo sur l’accueil", "Sous « Ta journée », avec un conseil selon le temps") { PSwitch(s.wxCard, { s.wxCard = it; save(ui) }) }
        SetRow("Thème Météo", "Un ciel en direct derrière l’application, selon l’heure et le temps de ta ville, avec des panneaux en verre givré.") {
            PButton(if (s.theme == "weather") "Activé" else "Activer", { s.theme = "weather"; save(ui); ui.toast("Thème Météo : le ciel suit l’heure et le temps de ta ville.", "cloud") }, small = true, enabled = s.theme != "weather")
        }
        Muted("Données météo : Open-Meteo (licence CC BY 4.0).${if (ui.pg.store.acct) " Les demandes passent par le serveur PepsGoal : ta ville n’est pas reliée à ton adresse IP chez Open-Meteo." else ""}", Modifier.padding(top = 10.dp), small = true)
    }
}

/* ------------------------------------------------------------------ Social */
@Composable
private fun PrivacySettings(ui: AppState, u: User) {
    val pg = ui.pg; val s = u.settings; val p = LocalPal.current
    Glass {
        SetRow("Fonctions sociales", "Feed, stories, amis, classement et encouragements. Désactive-les pour un usage 100 % solo : tes objectifs restent intacts.") {
            if (pg.isGuest(u) || pg.db.sandbox) Chip("Compte requis", "lock", p.type("deadline").c)
            else PSwitch(s.social, { s.social = it; save(ui); ui.toast(if (it) "Fonctions sociales activées." else "Fonctions sociales masquées. Tu peux les réactiver ici à tout moment.", "users") })
        }
        SectionTitle("Visibilité par défaut des nouveaux objectifs")
        Muted("Modifiable ensuite pour chaque objectif", small = true)
        Spacer(Modifier.height(8.dp))
        Seg(VIS.keys.map { it to VIS.getValue(it).first }, s.defaultVis, { s.defaultVis = it; save(ui); ui.toast("Nouveaux objectifs : ${VIS.getValue(it).first.lowercase()} par défaut.", VIS.getValue(it).second) }, icons = VIS.values.map { it.second })
        SetRow("Apparaître dans les suggestions d’amis", "Ton profil peut être proposé aux amis de tes amis, aux membres de tes groupes et aux nouveaux inscrits.") {
            if (pg.isGuest(u)) Chip("Compte requis", "lock", p.type("deadline").c)
            else PSwitch(!s.noSuggest, { s.noSuggest = !it; save(ui); ui.toast(if (it) "Tu peux apparaître dans les suggestions d’amis." else "Tu n’apparais plus dans les suggestions d’amis.", "users") })
        }
        SetRow("Stories", "Visibles uniquement par tes amis pendant 24 heures, puis gardées dans ton archive (visible par toi seul).") { Chip("Amis · 24 h", "users", p.type("group").c) }
        SetRow("Amis", pl(pg.friendsOf(u.id).size, "ami", "amis")) { PButton("Gérer", { ui.nav("friends") }, icon = "users", small = true, enabled = pg.socialOn(u)) }
        if (!pg.isGuest(u)) {
            val list = pg.db.blocks.filter { it.by == u.id }.mapNotNull { pg.userById(it.who) }
            SetRow("Comptes bloqués", if (list.isNotEmpty()) "Ils ne voient plus tes contenus et ne peuvent plus t’écrire ni t’ajouter." else "Aucun compte bloqué. Tu peux bloquer quelqu’un depuis son profil.") { if (list.isNotEmpty()) Chip("${list.size}") }
            list.forEach { x ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Av(x, 36.dp); Column(Modifier.weight(1f)) { Text(x.name, fontWeight = FontWeight.ExtraBold); Muted("@${x.username}", small = true) }
                    PButton("Débloquer", { ui.run("${first(x.name)} est débloqué.", "users") { ui.cloud.unblock(x.id) } }, kind = "ghost", small = true)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ Données */
@Composable
private fun DataSettings(ui: AppState, u: User) {
    val pg = ui.pg; val p = LocalPal.current; val cloud = ui.cloud
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Glass {
            val n = remember(ui.rev) { if (pg.store.acct) cloud.pendingCount() else 0 }
            val state = when {
                pg.db.sandbox -> Triple("star", "Démonstration", "Rien n’est enregistré en mode démonstration.")
                pg.isGuest(u) || !pg.store.acct -> Triple("cloudoff", "Indisponible sans compte", "Crée un compte pour sauvegarder tes données et les retrouver sur tes autres appareils.")
                !pg.store.online -> Triple("cloudoff", if (cloud.forceOffline) "Hors ligne (choisi)" else "Hors ligne", "Tes modifications sont gardées sur cet appareil${if (n > 0) " (${pl(n, "modification", "modifications")} en attente)" else ""} et partiront au retour de la connexion.")
                cloud.syncing != null -> Triple("cloud", "Synchronisation…", "Envoi et réception de tes données en cours.")
                cloud.syncError.isNotEmpty() -> Triple("cloudoff", "Erreur", cloud.syncError)
                else -> Triple("cloud", "À jour", cloud.lastSync?.let { "Dernière synchronisation ${ago(it)}. Tes objectifs sont sauvegardés sur le serveur PepsGoal et disponibles sur tous tes appareils." } ?: "Tes objectifs sont sauvegardés sur le serveur PepsGoal.")
            }
            SetRow("Synchronisation", state.third) { Chip(state.second, state.first, if (state.second == "À jour") p.type("routine").c else p.type("deadline").c) }
            if (pg.store.acct) {
                PButton("Synchroniser maintenant", { ui.run("Synchronisé.", "cloud") { cloud.syncNow(); cloud.refreshSocial() } }, icon = "cloud", small = true, enabled = pg.store.online)
                SetRow("Mode hors ligne", "Aucun échange avec le serveur : tes objectifs restent utilisables et tes modifications partent quand tu repasses en ligne. Les fonctions sociales sont en pause.") {
                    PSwitch(cloud.forceOffline, { cloud.forceOffline = it; if (!it) cloud.pingSoon(); ui.bump() })
                }
            }
        }
        val mine = remember(ui.rev) { listOf(pg.db.goals.count { it.userId == u.id }, pg.db.checkins.count { it.userId == u.id }, pg.db.posts.count { it.userId == u.id }, pg.db.messages.count { it.from == u.id || it.to == u.id }) }
        StatGrid(listOf("Objectifs", "Validations", "Publications", "Messages").mapIndexed { i, l -> "${mine[i]}" to l })
        Glass {
            SetRow("Exporter mes données", "Objectifs, validations, publications, stories et messages (déchiffrés sur cet appareil), en JSON, à partager ou garder.") {
                PButton("Exporter", {
                    if (pg.store.acct && pg.store.online) ui.run { val raw = cloud.export(); ui.open(Sheet.Export(withMessages(ui, raw))) }
                    else ui.open(Sheet.Export(localExport(ui, u)))
                }, icon = "archive", kind = "primary", small = true, enabled = !pg.db.sandbox)
            }
            if (!pg.store.acct && !pg.db.sandbox) SetRow("Réinitialiser cet appareil", "Efface tous les profils sans compte et leurs données sur cet appareil.") {
                PButton("Réinitialiser", { ui.confirm("Réinitialiser les données ?", "Tous les profils, objectifs et validations de cet appareil seront effacés définitivement.", "Tout réinitialiser") { ui.close(); pg.stopTimer(true); pg.store.use(Db(), false); pg.store.commit(); ui.sessionChanged(); ui.toast("Données réinitialisées.") } }, kind = "danger", small = true)
            }
        }
    }
}

private val PRETTY = Json { prettyPrint = true }

/** Export du serveur complété par les messages déchiffrés sur l'appareil (portabilité). */
private fun withMessages(ui: AppState, raw: String): String = try {
    val o = Json.parseToJsonElement(raw).jsonObject
    PRETTY.encodeToString(JsonObject.serializer(), JsonObject(o + ("messagesDechiffres" to messagesJson(ui))))
} catch (e: Exception) { raw }

private fun messagesJson(ui: AppState): JsonArray {
    val me = ui.pg.meId()
    return JsonArray(ui.pg.db.messages.filter { it.from == me || it.to == me || it.chat != null }.map { m ->
        buildJsonObject { put("id", m.id); put("de", m.from); m.to?.let { put("a", it) }; m.chat?.let { put("groupe", it) }; put("date", isoOf(m.at)); put("texte", Social.preview(ui, m)); put("chiffre", m.e2e != null) }
    })
}

private fun localExport(ui: AppState, u: User): String {
    val db = ui.pg.db
    val o = buildJsonObject {
        putJsonObject("profil") { put("nom", u.name); put("utilisateur", u.username); put("bio", u.bio); put("inscription", isoOf(u.createdAt)); put("reglages", JSON.encodeToJsonElement(u.settings)); put("groupes", JSON.encodeToJsonElement(u.goalGroups)) }
        put("objectifs", JSON.encodeToJsonElement(db.goals.filter { it.userId == u.id }))
        put("validations", JSON.encodeToJsonElement(db.checkins.filter { it.userId == u.id }))
        put("publications", JSON.encodeToJsonElement(db.posts.filter { it.userId == u.id }))
        put("stories", JSON.encodeToJsonElement(db.stories.filter { it.userId == u.id }))
        put("messages", messagesJson(ui))
    }
    return PRETTY.encodeToString(JsonObject.serializer(), o)
}

/* ------------------------------------------------------------------ Accessibilité */
val A11Y_OPTS = listOf(
    Triple("contrast", "Contraste élevé", "Texte et bordures en noir et blanc purs, sans dégradés."),
    Triple("cvd", "Mode daltonien", "Couleurs des types d’objectifs et des états adaptées à ton type de daltonisme."),
    Triple("mono", "Mode monochrome", "Toute l’interface en niveaux de gris ; les types restent reconnaissables à leurs icônes."),
    Triple("motion", "Désactiver les animations", "Aucune animation ni transition, y compris les célébrations plein écran."),
    Triple("transp", "Désactiver la transparence", "Panneaux opaques, sans flou ni halos colorés en arrière-plan.")
)
val CVD_TYPES = listOf("" to "Désactivé", "deuter" to "Deutéranopie (vert)", "prot" to "Protanopie (rouge)", "trit" to "Tritanopie (bleu-jaune)")

@Composable
fun A11yRows(ui: AppState) {
    ui.themeTick
    val pr = ui.prefs
    A11Y_OPTS.forEach { (k, l, d) ->
        if (k == "cvd") {
            SetRow(l, d)
            FlowRowCompat(gap = 6.dp) { CVD_TYPES.forEach { (v, t) -> FChip(t, pr.cvd == v, { pr.cvd = v; ui.themeTick++ }) } }
        } else SetRow(l, d) {
            val on = when (k) { "contrast" -> pr.contrast; "mono" -> pr.mono; "motion" -> pr.motion; else -> pr.transp }
            PSwitch(on, { v -> when (k) { "contrast" -> pr.contrast = v; "mono" -> pr.mono = v; "motion" -> pr.motion = v; else -> pr.transp = v }; ui.themeTick++ })
        }
    }
}

/* ------------------------------------------------------------------ Aide */
@Composable
private fun HelpSettings(ui: AppState) {
    val ctx = LocalContext.current
    val open = { url: String -> try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { ui.toast("Aucune application pour ouvrir ce lien.", "x") } }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        UpdateCard(ui)
        Glass {
            SetRow("Tutoriel", "Revoir les bases de PepsGoal") { PButton("Revoir", { ui.open(Sheet.Tuto(0)) }, small = true, leading = { Flame(18.dp) }) }
            SetRow("Notes de version", "Les nouveautés de chaque version de PepsGoal") { PButton("Voir", { ui.open(Sheet.Notes) }, icon = "list", small = true) }
            SetRow("À propos", "PepsGoal $APP_VERSION · application Android native${if (ui.pg.db.sandbox) " · mode démonstration" else ""}. ${if (ui.pg.store.acct) "Compte synchronisé avec le serveur PepsGoal." else "Les données restent sur cet appareil."} Édité par Quentin CORBET.")
            SetRow("Site de PepsGoal", "Présentation, nouveautés et téléchargement des applications") { PButton("Ouvrir", { open(Links.SITE) }, icon = "globe", small = true) }
            SetRow("Code source", "PepsGoal est un logiciel libre sous licence MIT, publié sur GitHub") { PButton("GitHub", { open(Links.REPO) }, icon = "github", small = true) }
            SetRow("Signaler un problème", "Un bug, une idée ? Ouvre un ticket sur GitHub, ou écris à ${Links.MAIL}") { PButton("Signaler", { open(Links.ISSUE) }, icon = "flag", small = true) }
        }
        Glass {
            CardHead("Informations légales")
            SetRow("Conditions générales d’utilisation", "Règles d’utilisation, conduite sur l’espace social, modération") { PButton("Lire", { ui.open(Sheet.Legal("cgu")) }, icon = "book", small = true) }
            SetRow("Mentions légales", "Éditeur, hébergement, propriété intellectuelle, crédits") { PButton("Lire", { ui.open(Sheet.Legal("mentions")) }, icon = "book", small = true) }
            SetRow("Charte de confidentialité", "Données utilisées, stockage, partage et tes droits") { PButton("Lire", { ui.open(Sheet.Legal("privacy")) }, icon = "lock", small = true) }
            SetRow("Licence MIT", "Licence du logiciel PepsGoal et des composants qu’il utilise") { PButton("Lire", { ui.open(Sheet.Legal("license")) }, icon = "book", small = true) }
        }
    }
}

/** Mises à jour intégrées : recherche, téléchargement et installation de la dernière version publiée. */
@Composable
private fun UpdateCard(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current
    var rel by remember { mutableStateOf<Updater.Release?>(null) }
    var state by remember { mutableStateOf("idle") } // idle, checking, none, available, downloading, ready, error
    var prog by remember { mutableFloatStateOf(0f) }
    var file by remember { mutableStateOf<java.io.File?>(null) }
    var auto by remember { mutableStateOf(ui.prefs.autoUpdate) }
    val check: () -> Unit = {
        state = "checking"
        ui.scope.launch {
            val r = Updater.latest()
            rel = r; ui.prefs.updateChecked = System.currentTimeMillis()
            state = when { r == null -> "error"; Updater.compare(r.version, Updater.current) > 0 -> "available"; else -> "none" }
        }
    }
    LaunchedEffect(Unit) { check() }
    val install: () -> Unit = {
        val r = rel
        if (r != null) {
            if (!Updater.canInstall(ctx)) { Updater.openInstallPermission(ctx); ui.toast("Autorise PepsGoal à installer des applications, puis touche à nouveau « Installer ».", "lock") }
            else if (file != null) Updater.install(ctx, file!!)
            else {
                state = "downloading"; prog = 0f
                ui.scope.launch {
                    try { val f = Updater.download(ctx, r) { prog = it }; file = f; state = "ready"; Updater.install(ctx, f) }
                    catch (e: Exception) { state = "error"; ui.toast(e.message ?: "Téléchargement impossible, réessaie.", "x") }
                }
            }
        }
    }
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            IconTile("cloud", p.type("project").c, p.type("objective").c, 44.dp, 14.dp)
            Column(Modifier.weight(1f)) {
                Text("Mises à jour", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Muted("Version installée : ${Updater.current}", small = true)
            }
        }
        val r = rel
        when (state) {
            "checking" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.flame); Muted("Recherche d’une nouvelle version…", small = true) }
            "none" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Chip("À jour", "check", p.ok); Muted("Tu as la dernière version.", small = true) }
            "error" -> Muted("Impossible de joindre GitHub pour le moment. Vérifie ta connexion et réessaie.", small = true)
            "available", "downloading", "ready" -> if (r != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Nouveau", "star", p.flame); Text("PepsGoal ${r.version}", fontWeight = FontWeight.ExtraBold)
                    if (r.size > 0) Muted("· ${"%.1f".format(r.size / 1_048_576f)} Mo", small = true)
                }
                if (r.notes.isNotBlank()) Text(r.notes.lines().filter { it.startsWith("- ") }.take(6).joinToString("\n") { "• " + it.removePrefix("- ") }.ifEmpty { r.notes.take(400) }, fontSize = 13.sp, color = p.ink2)
                if (state == "downloading") { Bar(prog * 100.0); Muted("Téléchargement… ${(prog * 100).toInt()} %", small = true) }
                else PButton(if (state == "ready") "Installer" else "Télécharger et installer", install, icon = "down", kind = "primary", modifier = Modifier.fillMaxWidth())
            }
            else -> {}
        }
        if (state !in setOf("checking", "downloading")) PButton("Rechercher une mise à jour", check, icon = "clock", kind = "ghost", small = true, modifier = Modifier.padding(top = 6.dp))
        SetRow("Rechercher automatiquement", "Une notification t’avertit quand une nouvelle version est publiée.") {
            PSwitch(auto, { auto = it; ui.prefs.autoUpdate = it })
        }
    }
}
