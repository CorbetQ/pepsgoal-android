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
import fr.pepsgoal.app.ui.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Fs
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
    listOf("home", "Accueil", "Cartes affichées sur l’accueil et leur ordre", "sun"),
    listOf("reminders", "Rappels et notifications", "Rappels, notifications, activités en cours (Live Updates)", "bell"),
    listOf("account", "Compte", "Profil, mot de passe, déconnexion", "user"),
    listOf("weather", "Météo", "Ville, carte du jour et thème Météo", "cloud"),
    listOf("places", "Emplacements", "Maison, travail, salle de sport… sur une carte, pour les rappels par lieu", "pin"),
    listOf("privacy", "Social et confidentialité", "Fonctions sociales, visibilité, stories", "lock"),
    listOf("ai", "Assistant IA", "Coach IA 100 % local : installation, fonctions et mémoire", "sparkle"),
    listOf("health", "Santé", "Health Connect : pas, sommeil, sport, eau et validation automatique", "heart"),
    listOf("widgets", "Widgets", "Widgets de l’écran d’accueil : ajout et apparence", "sun"),
    listOf("data", "Données", "Synchronisation, export, réinitialisation", "archive"),
    listOf("perf", "Performance", "Fluidité, animations, effets et analyse du téléphone", "gauge"),
    listOf("a11y", "Accessibilité et confort", "Lecture facile, taille du texte, densité, mode une main, vibrations et sons, contraste, daltonisme", "eye"),
    listOf("help", "Aide", "Tutoriel, notes de version et à propos", "star")
)

/** Catégories rangées par thème sur la page des Paramètres. */
val SET_SECTIONS = listOf(
    "Personnalisation" to listOf("appearance", "home", "widgets", "perf"),
    "Au quotidien" to listOf("reminders", "weather", "places", "health", "ai"),
    "Compte et vie privée" to listOf("account", "privacy", "data"),
    "Confort et aide" to listOf("a11y", "help")
)

/** Couleurs des catégories de réglages (reprises des couleurs des types d'objectifs). */
val SET_COLORS = mapOf("appearance" to ("measure" to "abstinence"), "reminders" to ("period" to "project"), "account" to ("routine" to "duration"),
    "weather" to ("objective" to "period"), "places" to ("measure" to "routine"), "privacy" to ("abstinence" to "program"), "data" to ("challenge" to "limit"), "widgets" to ("challenge" to "deadline"), "ai" to ("program" to "measure"), "health" to ("deadline" to "duration"), "home" to ("routine" to "objective"), "a11y" to ("duration" to "objective"), "perf" to ("timed" to "challenge"), "help" to ("monthly" to "challenge"))

/** Écran plein (au-dessus des onglets) avec un en-tête « retour », sur un fond coloré propre à la page. */
@Composable
fun ScreenFrame(ui: AppState, title: String, back: String, onBack: () -> Unit, right: @Composable RowScope.() -> Unit = {},
                icon: String? = null, color: Color? = null, color2: Color? = null, bottom: (@Composable () -> Unit)? = null,
                state: androidx.compose.foundation.lazy.LazyListState? = null, content: LazyListScope.() -> Unit) {
    val p = LocalPal.current
    val c1 = color ?: p.flame; val c2 = color2 ?: p.pink
    // Retour prédictif : la page suit le geste de retour du système (elle rétrécit et glisse vers le bord), avec des
    // vibrations progressives ; geste annulé, elle reprend sa place en ressort
    var backK by remember { mutableFloatStateOf(0f) }; var backEdge by remember { mutableIntStateOf(0) }
    androidx.activity.compose.PredictiveBackHandler { events ->
        var step = 0
        try {
            events.collect { e ->
                backK = e.progress; backEdge = e.swipeEdge
                val st = (e.progress * 4).toInt().coerceAtMost(3); if (st > step) Feedback.ramp(e.progress); step = st
            }
            if (backK > .05f) Feedback.commit()
            onBack()
            // Page toujours là (retour interne, sans quitter l'écran) : elle reprend sa taille
            kotlinx.coroutines.delay(450)
            androidx.compose.animation.core.animate(backK, 0f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = .8f, stiffness = 500f)) { v, _ -> backK = v }
        } catch (c: kotlin.coroutines.cancellation.CancellationException) {
            androidx.compose.animation.core.animate(backK, 0f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = .8f, stiffness = 500f)) { v, _ -> backK = v }
        }
    }
    remember(title) { Stagger.reset() }
    Box(Modifier.fillMaxSize().graphicsLayer {
        if (backK > 0f && !p.motion) {
            val k = androidx.compose.animation.core.FastOutSlowInEasing.transform(backK)
            val sc = 1f - .1f * k; scaleX = sc; scaleY = sc
            translationX = (if (backEdge == 0) 1f else -1f) * k * 28.dp.toPx()
            shape = RoundedCornerShape(32.dp * k); clip = true
        }
    }) { CompositionLocalProvider(LocalHaze provides null) {
        PageBackdrop(c1, c2)
        // Mode une main : le bouton retour et les actions passent en bas de l'écran, sous le pouce
        val low = p.oneHand
        val list = state ?: androidx.compose.foundation.lazy.rememberLazyListState()
        // En-tête qui se rétracte : passé le grand titre, il s'affiche en petit dans une barre givrée
        // Le petit titre n'apparaît qu'une fois le grand presque effacé (pas de doublon à l'écran)
        val dens = androidx.compose.ui.platform.LocalDensity.current; val fadePx = with(dens) { 44.dp.toPx() }
        val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > fadePx * .85f } }
        val k by androidx.compose.animation.core.animateFloatAsState(if (collapsed && !low) 1f else 0f, if (p.motion) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(220), label = "head")
        val head = @Composable { mod: Modifier ->
            Box(mod.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // « Retour » générique : on affiche plutôt le nom de l'onglet d'où l'on vient
                    BackPill(if (back == "Retour" && ui.screens.size <= 1) NAV_TITLES[ui.tab] ?: back else back, onBack)
                    Text(title, Modifier.weight(1f).padding(horizontal = 6.dp).graphicsLayer { alpha = k; translationY = (1f - k) * 10.dp.toPx() }, fontFamily = Unbounded, fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    right()
                }
            }
        }
        Column(Modifier.fillMaxSize().imePadding()) {
            if (!low) head(Modifier.drawBehind {
                // Barre givrée qui apparaît avec le titre compact, fine ligne de séparation en bas
                drawRect(p.glassStrong.copy(alpha = p.glassStrong.alpha * k))
                drawRect(p.line.copy(alpha = p.line.alpha * k), androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
            }.statusBarsPadding()) else Spacer(Modifier.statusBarsPadding().height(if (bottom == null) 40.dp else 8.dp))
            // Grand écran : colonne de lecture centrée, à largeur confortable
            LazyColumn(Modifier.weight(1f).fillMaxWidth().wrapContentWidth().widthIn(max = 760.dp), list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = if (low) 16.dp else 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item("title") {
                    // Le grand titre s'efface et rétrécit en défilant (lu dans la phase de dessin : pas de recomposition)
                    PageTitle(title, icon, c1, c2, Modifier.enter().graphicsLayer {
                        val off = if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset.toFloat() else 200f
                        val f = (off / 44.dp.toPx()).coerceIn(0f, 1f); alpha = 1f - f; val sc = 1f - .08f * f; scaleX = sc; scaleY = sc
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f)
                    }.padding(start = 4.dp, top = 4.dp, bottom = 8.dp))
                }
                content()
                if (bottom == null && !low) item("nav") { Spacer(Modifier.navigationBarsPadding()) }
            }
            // Zone fixe en bas de page (saisie d'un commentaire…)
            if (bottom != null) Box(Modifier.fillMaxWidth().then(if (low) Modifier else Modifier.navigationBarsPadding())) { bottom() }
            if (low) head(Modifier.navigationBarsPadding())
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
        var q by rememberSaveable { mutableStateOf("") }
        val n = VoiceCmd.norm(q.trim())
        val cats = if (n.length < 2) SET_CATS else SET_CATS.filter { c -> listOf(c[1], c[2], SET_WORDS[c[0]] ?: "", setSummary(ui, c[0]) ?: "").any { VoiceCmd.norm(it).contains(n) } }
        ScreenFrame(ui, "Paramètres", "Profil", { ui.back() }, icon = "gear") {
            item("search") { Field(q, { q = it }, "", placeholder = "Rechercher un réglage…", max = 40, icon = "search") }
            if (cats.isEmpty()) item("none") { Glass { Empty("Aucun réglage trouvé", "Essaie « thème », « rappel », « vibrations »…", icon = "search") } }
            else {
                // Catégories rangées par thème (une seule liste pendant une recherche)
                val sections = if (n.length >= 2) listOf("Résultats" to cats.map { it[0] }) else SET_SECTIONS
                sections.forEach { (title, keys) ->
                    val list = keys.mapNotNull { k -> cats.firstOrNull { it[0] == k } }
                    if (list.isNotEmpty()) item("sec:$title") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(title, Modifier.padding(start = 6.dp), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                            Glass(pad = 4.dp, radius = 24.dp) {
                                Divided(inset = 66.dp) {
                                    list.forEach { (k, l, d, i) ->
                                        val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                        Row(Modifier.fillMaxWidth().bounce(inter, .98f).clip(RoundedCornerShape(18.dp)).springClick(inter) { ui.screens[ui.screens.lastIndex] = Screen.Settings(k) }.padding(horizontal = 12.dp, vertical = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            val (k1, k2) = SET_COLORS[k] ?: ("routine" to "duration")
                                            IconTile(i, p.type(k1).c, p.type(k2).c, 42.dp, 13.dp)
                                            // Sous le nom : l'état actuel de la catégorie quand il se résume en quelques mots
                                            Column(Modifier.weight(1f)) { Text(l, fontWeight = FontWeight.Bold, fontSize = 16.sp); Muted(setSummary(ui, k) ?: d, small = true, maxLines = 2) }
                                            Chevron()
                                        }
                                    }
                                }
                            }
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
            "appearance" -> item("b") { AppearancePage(ui, u) }
            "reminders" -> item("b") { RemindersSettings(ui, u) }
            "account" -> item("b") { AccountSettings(ui, u) }
            "weather" -> item("b") { WeatherSettings(ui, u) }
            "privacy" -> item("b") { PrivacySettings(ui, u) }
            "data" -> item("b") { DataSettings(ui, u) }
            "widgets" -> item("b") { WidgetsSettings(ui) }
            "places" -> item("b") { PlacesSettings(ui) }
            "ai" -> item("b") { AiSettings(ui) }
            "health" -> item("b") { HealthSettings(ui) }
            "home" -> item("b") { HomeSettings(ui) }
            "perf" -> item("b") { PerformancePage(ui) }
            "a11y" -> item("b") { Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                CatHero("a11y", setSummary(ui, "a11y") ?: "Réglages standard", chips = { Chip("Cet appareil", "lock", p.type("objective").c) })
                ComfortSettings(ui)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Couleurs et effets", Modifier.padding(start = 6.dp), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                    Glass(pad = 4.dp) { A11yRows(ui) }
                    Muted("Ces réglages s’appliquent à cet appareil, pour tous les comptes, dès l’écran de connexion.", Modifier.padding(horizontal = 8.dp), small = true)
                }
            } }
            else -> item("b") { HelpSettings(ui) }
        }
    }
}

private fun save(ui: AppState) { ui.pg.store.commit() }

/* ------------------------------------------------------------------ Rappels */
@Composable
fun TimeButton(value: String, onChange: (String) -> Unit, desc: String) {
    val ctx = LocalContext.current
    PButton(value, {
        val (h, m) = value.split(":").map { it.toIntOrNull() ?: 0 }.let { (it.getOrElse(0) { 8 }) to (it.getOrElse(1) { 30 }) }
        TimePickerDialog(ctx, { _, hh, mm -> onChange("${pad(hh)}:${pad(mm)}") }, h, m, true).apply { setTitle(desc) }.show()
    }, icon = "clock", small = true)
}

/** En-tête d'une catégorie de réglages, aux couleurs et à l'icône de la catégorie. */
@Composable
fun CatHero(key: String, sub: String, leading: (@Composable () -> Unit)? = null, chips: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit = {}) {
    val p = LocalPal.current
    val c = SET_CATS.first { it[0] == key }; val (k1, k2) = SET_COLORS[key] ?: ("routine" to "duration")
    SetHero(c[3], p.type(k1).c, p.type(k2).c, c[1], sub, leading, chips, content)
}

@Composable
private fun RemindersSettings(ui: AppState, u: User) {
    val s = u.settings; val ctx = LocalContext.current; val p = LocalPal.current
    var tick by remember { mutableIntStateOf(0) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> s.browser = ok; save(ui); tick++; ui.toast(if (ok) "Notifications activées." else "Notifications refusées : tu peux les autoriser dans les réglages Android.", "bell") }
    val granted = remember(tick, ui.rev) { Notifier.allowed(ctx) }
    LifecycleResume { tick++ }
    val changed = { save(ui); Reminders.reschedule(ctx); ui.toast("Rappels mis à jour.", "bell") }
    val cMorning = p.type("objective").c; val cEvening = p.type("period").c; val cNotif = p.type("project").c; val cBg = p.type("duration").c
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        CatHero("reminders", listOfNotNull(if (s.morning.on) "Matin à ${s.morning.time}" else null, if (s.evening.on) "soir à ${s.evening.time}" else null).joinToString(" · ").ifEmpty { "Rappels du matin et du soir coupés" }, chips = {
            Chip(if (granted) "Notifications activées" else "Notifications bloquées", if (granted) "check" else "x", if (granted) p.ok else p.danger)
            if (ui.prefs.smartReminders) Chip("Intelligents", "sparkle", p.flame)
        })
        SetGroup("Rappels quotidiens") {
            SetItem("Rappel du matin", "Le programme de ta journée", "sun", cMorning) {
                TimeButton(s.morning.time, { s.morning.time = it; changed() }, "Rappel du matin")
                PSwitch(s.morning.on, { s.morning.on = it; changed() })
            }
            SetItem("Rappel du soir", "Seulement s'il te reste des validations", "moon", cEvening) {
                TimeButton(s.evening.time, { s.evening.time = it; changed() }, "Rappel du soir")
                PSwitch(s.evening.on, { s.evening.on = it; changed() })
            }
            var smart by remember { mutableStateOf(ui.prefs.smartReminders) }
            SetToggle("Rappels intelligents", "PepsGoal apprend l’heure à laquelle tu valides chaque objectif et te le rappelle juste avant, plus un rappel « dernière chance » le soir si ta flamme n’est pas allumée.", "sparkle", p.flame,
                smart, { smart = it; ui.prefs.smartReminders = it; Reminders.reschedule(ctx) })
        }
        SetGroup("Notifications") {
            SetItem("Notifications", if (granted) "Rappels, encouragements, demandes d’amis et messages, même quand l’application est fermée." else "Autorise les notifications pour recevoir tes rappels quand PepsGoal est fermé.", "bell", cNotif) {
                if (granted) Chip("Activées", "check", p.ok) else PButton("Autoriser", {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else Notifier.openSettings(ctx)
                }, icon = "bell", kind = "primary", small = true)
            }
            if (granted) SetItem("Catégories de notifications", "Son, vibration et importance de chaque catégorie, dans Android.", "gear", cNotif, onClick = { Notifier.openSettings(ctx) })
            if (!Reminders.canExact(ctx)) SetItem("Rappels à l’heure exacte", "Android peut retarder les rappels de quelques minutes. Autorise les alarmes exactes pour qu’ils arrivent pile à l’heure.", "clock", p.type("deadline").c, onClick = { Reminders.openExactSettings(ctx) })
            SetItem("Tester un rappel", "Envoie un rappel maintenant", "send", cNotif, onClick = {
                val pending = ui.pg.todayPlan(u.id).due.count { !it.done }
                val text = if (pending > 0) "Rappel : il te reste ${pl(pending, "validation", "validations")} aujourd'hui." else "Rappel : tout est validé aujourd'hui, bravo !"
                ui.pg.notify(u.id, text, "reminder"); save(ui); Notifier.test(ctx, text); ui.toast(text, "bell")
            })
        }
        if (ui.pg.store.acct) {
            var unr by remember { mutableStateOf(Poller.unrestricted(ctx)) }
            var instant by remember { mutableStateOf(ui.prefs.instantNotifs) }
            var t2 by remember { mutableIntStateOf(0) }
            androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { unr = Poller.unrestricted(ctx); t2++; onPauseOrDispose {} }
            val log = remember(t2) { ui.prefs.pollLog }
            SetGroup("Application fermée", note = "Les relèves en arrière-plan apportent messages, publications et notifications sans ouvrir PepsGoal.") {
                SetToggle("Notifications instantanées", "PepsGoal reste connecté en arrière-plan (petite notification discrète) pour relever les nouveautés chaque minute.", "bolt", cBg,
                    instant, { instant = it; ui.prefs.instantNotifs = it; Poller.startLink(ctx); ui.toast(if (it) "Connexion en direct activée." else "Connexion en direct désactivée.", "bell") })
                SetItem("Optimisation de batterie", if (unr) "PepsGoal n’est pas limité par Android : les relèves continuent en veille." else "Android peut endormir PepsGoal et retarder les messages.", "gauge", cBg) {
                    if (unr) Chip("Sans limite", "check", p.ok) else PButton("Autoriser", { Poller.askUnrestricted(ctx) }, kind = "primary", small = true)
                }
                SetItem("Tester application fermée", "Ferme PepsGoal : une notification de confirmation arrive à la prochaine relève, en une à deux minutes.", "clock", cBg,
                    onClick = { ui.prefs.pollTest = System.currentTimeMillis(); ui.toast("Ferme l’application et attends la notification.", "bell") })
                if (log.isNotEmpty()) SetBlock {
                    Muted("Dernières relèves en arrière-plan", small = true)
                    log.forEach { Text(it, fontSize = 12.sp, color = p.ink3, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                }
            }
        }
        SetGroup("Activités en cours", note = if (!LiveUpdates.supported()) "Sur cette version d’Android, les activités en cours s’affichent comme des notifications permanentes avec une barre de progression." else null) {
            SetToggle("Afficher les activités en cours", "Minuteur, période en cours et compte à rebours de fin de journée quand ta série est en jeu : dans la barre d’état, l’écran de verrouillage et la Now Bar.", "bolt", p.flame,
                ui.prefs.liveUpdates, { ui.prefs.liveUpdates = it; LiveUpdates.refresh(ctx); tick++ })
            if (ui.prefs.liveUpdates && LiveUpdates.supported() && !LiveUpdates.canPromote(ctx))
                SetItem("Mise en avant désactivée", "Réactive « Notifications en direct » dans les réglages de l’application pour les voir en haut de l’écran.", "gear", p.type("deadline").c, onClick = { LiveUpdates.openPromotionSettings(ctx) })
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
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        CatHero("account", if (pg.isGuest(u)) "Profil sans compte, gardé sur ce téléphone" else "@${u.username} · inscrit ${ago(u.createdAt)}", leading = { LevelRing(pg.level(pg.points(u.id)).idx, 52.dp) { Av(u, 52.dp) } }, chips = {
            when {
                pg.db.sandbox -> Chip("Démonstration", "star", p.flame)
                pg.isGuest(u) -> Chip("Sans compte", "lock", p.type("deadline").c)
                else -> Chip(if (pg.store.online) "Synchronisé" else "Hors ligne", if (pg.store.online) "cloud" else "cloudoff", if (pg.store.online) p.ok else p.type("deadline").c)
            }
            if (pg.isAdmin(u)) Chip("Administrateur", "shield", p.type("program").c)
        })
        if (pg.isGuest(u) && !pg.db.sandbox) GuestUpgrade(ui)
        SetGroup("Profil public", note = "Ton nom et ta bio sont visibles par tes amis et sur tes publications.") {
            SetBlock {
                Field(name, { name = it }, "Nom affiché", max = 40, keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words), icon = "user")
                Field(bio, { bio = it }, "Bio", singleLine = false, minLines = 2, max = 160)
                val dirty = name.trim() != u.name || bio.trim() != u.bio
                PButton("Enregistrer", {
                    val n = name.trim()
                    if (n.length < 2) { ui.toast("Indique ton nom (2 caractères minimum).", "x"); return@PButton }
                    if (pg.store.acct) ui.run("Profil mis à jour.", "user") { ui.cloud.saveProfile(n, bio.trim()) }
                    else { u.name = n; u.bio = bio.trim(); save(ui); ui.toast("Profil mis à jour.", "user") }
                }, Modifier.fillMaxWidth(), icon = "check", kind = "primary", enabled = dirty)
            }
            SetItem("Icône de profil", "Photo, emoji, picto ou initiales", "smile", p.type("measure").c, onClick = { ui.open(Sheet.Avatar) }) { Av(u, 32.dp) }
        }
        if (pg.store.acct) { PasswordCard(ui); VaultCard(ui) }
        SetGroup("Session") {
            val ok = pg.db.sandbox || !pg.store.acct || pg.store.online
            if (pg.isGuest(u)) SetItem("Mode sans compte", "Profil local créé ${ago(u.createdAt)}. Tu peux le reprendre depuis l'écran d'accueil avec « Reprendre sans compte ».", "user", p.type("routine").c)
            SetItem(if (pg.db.sandbox) "Quitter la démonstration" else "Se déconnecter", if (pg.db.sandbox) "Les données d’exemple sont effacées." else if (!pg.store.acct) "Tes données restent sur cet appareil." else if (ok) "Tes données sont sauvegardées sur le serveur PepsGoal." else "Hors ligne : la déconnexion reviendra avec la connexion.",
                "logout", p.type("project").c, onClick = { signOut(ui) }, enabled = ok)
            if (!pg.isAdmin(u) && !pg.db.sandbox) SetItem("Supprimer mon compte", "Profil, objectifs, publications, stories et messages. Définitif.", "trash", danger = true, onClick = {
                if (pg.store.acct) ui.open(Sheet.DeleteAccount)
                else ui.confirm("Supprimer ton profil ?", "Ton profil sans compte et tous ses objectifs seront supprimés définitivement de cet appareil.", "Supprimer mon profil") {
                    ui.close(); val db = pg.db; db.goals.removeAll { it.userId == u.id }; db.checkins.removeAll { it.userId == u.id }; db.timers.remove(u.id)
                    db.users.removeAll { it.id == u.id }; db.notifs.removeAll { it.userId == u.id }; db.session = null; save(ui); ui.sessionChanged(); ui.toast("Ton profil a été supprimé.", "trash")
                }
            })
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
    SetGroup("Mot de passe") { SetBlock {
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
    } }
}

/** Coffre de clés de chiffrement : synchronise les clés de conversation entre les appareils. */
@Composable
fun VaultCard(ui: AppState) {
    val p = LocalPal.current; val e2e = ui.cloud.e2e
    val on = e2e.hasVault(); val n = e2e.keyCount()
    SetGroup("Messages chiffrés") {
        SetItem("Clés de chiffrement", if (on) "${pl(n, "clé de conversation", "clés de conversation")}. Une copie chiffrée avec ton mot de passe est gardée sur le serveur : tes autres appareils retrouvent tes conversations dès la connexion. Ni l’éditeur ni l’hébergeur ne peuvent l’ouvrir."
            else "Tes clés de conversation ne sont pas encore synchronisées sur cet appareil.", "lock", p.type("abstinence").c) {
            Chip(if (on) "Synchronisées" else "Sur l’appareil", if (on) "check" else "lock", if (on) p.type("routine").c else p.type("deadline").c)
        }
        if (!on) SetBlock { VaultForm(ui) }
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
    val s = u.settings; val ctx = LocalContext.current; val p = LocalPal.current
    var q by remember { mutableStateOf("") }; var res by remember { mutableStateOf<List<Place>?>(null) }; var err by remember { mutableStateOf<String?>(null) }
    val pl0 = s.weather
    val search = {
        if (q.trim().length >= 2) {
            if (!ui.pg.store.online) err = "Hors ligne : la recherche de ville nécessite internet."
            else ui.scope.launch { try { err = null; res = ui.cloud.places(q.trim()) } catch (e: Exception) { err = e.message ?: "Recherche impossible." } }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        CatHero("weather", pl0?.let { "${it.name}${if (it.admin1.isNotEmpty()) ", " + it.admin1 else ""} · ${it.country}" } ?: "Choisis ta ville pour la météo du jour et le thème Météo", chips = {
            if (s.theme == "weather") Chip("Thème Météo actif", "cloud", p.type("objective").c)
            if (pl0 != null && s.wxCard) Chip("Carte sur l’accueil", "sun", p.type("period").c)
        })
        SetGroup("Ville") {
            if (pl0 != null) SetItem(pl0.name, listOf(pl0.admin1, pl0.country).filter { it.isNotEmpty() }.joinToString(" · "), "pin", p.type("objective").c) {
                PButton("Retirer", { s.weather = null; Wx.clear(ctx); save(ui) }, kind = "ghost", small = true)
            }
            SetBlock {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(q, { q = it }, "", Modifier.weight(1f), placeholder = if (pl0 == null) "Rechercher une ville" else "Changer de ville", max = 60, icon = "search")
                    PButton("Chercher", search, small = true, kind = "primary", enabled = q.trim().length >= 2)
                }
                Err(err)
                res?.let { list -> if (list.isEmpty()) Muted("Aucune ville trouvée.", small = true) }
            }
            res?.forEach { r ->
                SetItem(r.name, listOf(r.admin1, r.country).filter { it.isNotEmpty() }.joinToString(" · "), "pin", p.type("period").c, onClick = {
                    s.weather = r; res = null; q = ""; Wx.clear(ctx); save(ui); ui.scope.launch { Wx.ensure(ctx, ui, true) }; ui.toast("Météo : ${r.name}.", "cloud")
                })
            }
        }
        SetGroup("Affichage", note = "Données météo : Open-Meteo (licence CC BY 4.0).${if (ui.pg.store.acct) " Les demandes passent par le serveur PepsGoal : ta ville n’est pas reliée à ton adresse IP chez Open-Meteo." else ""}") {
            SetToggle("Carte météo sur l’accueil", "Sous « Ta journée », avec un conseil selon le temps", "sun", p.type("period").c, s.wxCard, { s.wxCard = it; save(ui) })
            SetItem("Thème Météo", "Un ciel en direct derrière l’application, selon l’heure et le temps de ta ville.", "cloud", p.type("objective").c) {
                if (s.theme == "weather") Chip("Activé", "check", p.ok)
                else PButton("Activer", { s.theme = "weather"; save(ui); ui.toast("Thème Météo : le ciel suit l’heure et le temps de ta ville.", "cloud") }, small = true)
            }
        }
    }
}

/* ------------------------------------------------------------------ Social */
@Composable
private fun PrivacySettings(ui: AppState, u: User) {
    val pg = ui.pg; val s = u.settings; val p = LocalPal.current
    val needAcct = pg.isGuest(u) || pg.db.sandbox
    val blocked = if (pg.isGuest(u)) emptyList() else pg.db.blocks.filter { it.by == u.id }.mapNotNull { pg.userById(it.who) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        CatHero("privacy", if (!s.social) "Usage solo : feed, amis et stories masqués" else "Nouveaux objectifs : ${VIS[s.defaultVis]?.first?.lowercase() ?: s.defaultVis} par défaut", chips = {
            Chip(if (s.social) "Social activé" else "Mode solo", if (s.social) "users" else "lock", if (s.social) p.ok else p.ink3)
            if (blocked.isNotEmpty()) Chip(pl(blocked.size, "compte bloqué", "comptes bloqués"), "ban", p.danger)
        })
        SetGroup("Espace social") {
            if (needAcct) SetItem("Fonctions sociales", "Feed, stories, amis, classement et encouragements.", "users", p.type("group").c) { Chip("Compte requis", "lock", p.type("deadline").c) }
            else SetToggle("Fonctions sociales", "Feed, stories, amis, classement et encouragements. Désactive-les pour un usage 100 % solo : tes objectifs restent intacts.", "users", p.type("group").c,
                s.social, { s.social = it; save(ui); ui.toast(if (it) "Fonctions sociales activées." else "Fonctions sociales masquées. Tu peux les réactiver ici à tout moment.", "users") })
            SetItem("Amis", pl(pg.friendsOf(u.id).size, "ami", "amis"), "userplus", p.type("routine").c, onClick = { ui.nav("friends") }, enabled = pg.socialOn(u))
        }
        SetGroup("Visibilité", note = "Modifiable ensuite pour chaque objectif.") {
            SetBlock {
                Text("Nouveaux objectifs visibles par", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Seg(VIS.keys.map { it to VIS.getValue(it).first }, s.defaultVis, { s.defaultVis = it; save(ui); ui.toast("Nouveaux objectifs : ${VIS.getValue(it).first.lowercase()} par défaut.", VIS.getValue(it).second) }, icons = VIS.values.map { it.second })
            }
            SetItem("Stories", "Visibles uniquement par tes amis pendant 24 heures, puis gardées dans ton archive (visible par toi seul).", "image", p.pink) { Chip("Amis · 24 h", "users", p.type("group").c) }
        }
        SetGroup("Découverte") {
            if (pg.isGuest(u)) SetItem("Suggestions d’amis", "Apparaître chez les amis de tes amis et les nouveaux inscrits.", "compass", p.type("challenge").c) { Chip("Compte requis", "lock", p.type("deadline").c) }
            else SetToggle("Apparaître dans les suggestions d’amis", "Ton profil peut être proposé aux amis de tes amis, aux membres de tes groupes et aux nouveaux inscrits.", "compass", p.type("challenge").c,
                !s.noSuggest, { s.noSuggest = !it; save(ui); ui.toast(if (it) "Tu peux apparaître dans les suggestions d’amis." else "Tu n’apparais plus dans les suggestions d’amis.", "users") })
        }
        if (!pg.isGuest(u)) SetGroup("Comptes bloqués", note = if (blocked.isEmpty()) "Aucun compte bloqué. Tu peux bloquer quelqu’un depuis son profil." else "Ils ne voient plus tes contenus et ne peuvent plus t’écrire ni t’ajouter.") {
            if (blocked.isEmpty()) SetItem("Personne n’est bloqué", null, "shield", p.ok)
            blocked.forEach { x ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    val n = remember(ui.rev) { if (pg.store.acct) cloud.pendingCount() else 0 }
    val state = when {
        pg.db.sandbox -> Triple("star", "Démonstration", "Rien n’est enregistré en mode démonstration.")
        pg.isGuest(u) || !pg.store.acct -> Triple("cloudoff", "Sans compte", "Crée un compte pour sauvegarder tes données et les retrouver sur tes autres appareils.")
        !pg.store.online -> Triple("cloudoff", if (cloud.forceOffline) "Hors ligne (choisi)" else "Hors ligne", "Tes modifications sont gardées sur cet appareil${if (n > 0) " (${pl(n, "modification", "modifications")} en attente)" else ""} et partiront au retour de la connexion.")
        cloud.syncing != null -> Triple("cloud", "Synchronisation…", "Envoi et réception de tes données en cours.")
        cloud.syncError.isNotEmpty() -> Triple("cloudoff", "Erreur", cloud.syncError)
        else -> Triple("cloud", "À jour", cloud.lastSync?.let { "Dernière synchronisation ${ago(it)}. Tes objectifs sont sauvegardés et disponibles sur tous tes appareils." } ?: "Tes objectifs sont sauvegardés sur le serveur PepsGoal.")
    }
    val mine = remember(ui.rev) { listOf(pg.db.goals.count { it.userId == u.id }, pg.db.checkins.count { it.userId == u.id }, pg.db.posts.count { it.userId == u.id }, pg.db.messages.count { it.from == u.id || it.to == u.id }) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        CatHero("data", state.third, chips = {
            Chip(state.second, state.first, if (state.second == "À jour") p.ok else p.type("deadline").c)
            if (ui.prefs.backupUri.isNotEmpty()) Chip("Sauvegarde auto", "archive", p.type("routine").c)
        }) {
            // Mes données en chiffres
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Objectifs", "Validations", "Publications", "Messages").forEachIndexed { i, l ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(p.ink.copy(alpha = .05f)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        FitText(fmtNum(mine[i]), Modifier.fillMaxWidth(), size = 17, min = 11, weight = FontWeight.ExtraBold, align = androidx.compose.ui.text.style.TextAlign.Center)
                        FitText(l, Modifier.fillMaxWidth().padding(horizontal = 4.dp), size = 11, min = 8, color = p.ink2, weight = FontWeight.SemiBold, align = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
        if (pg.store.acct) SetGroup("Synchronisation") {
            SetItem("Synchroniser maintenant", "Envoie tes modifications et récupère les nouveautés", "cloud", p.type("challenge").c, onClick = { ui.run("Synchronisé.", "cloud") { cloud.syncNow(); cloud.refreshSocial() } }, enabled = pg.store.online)
            SetToggle("Mode hors ligne", "Aucun échange avec le serveur : tes objectifs restent utilisables et tes modifications partent quand tu repasses en ligne. Les fonctions sociales sont en pause.", "cloudoff", p.type("limit").c,
                cloud.forceOffline, { cloud.forceOffline = it; if (!it) cloud.pingSoon(); ui.bump() })
        }
        BackupCard(ui)
        SetGroup("Exports") {
            SetItem("Exporter mes données", "Objectifs, validations, publications, stories et messages (déchiffrés sur cet appareil), en JSON.", "archive", p.type("challenge").c, enabled = !pg.db.sandbox, onClick = {
                if (pg.store.acct && pg.store.online) ui.run { val raw = cloud.export(); ui.open(Sheet.Export(withMessages(ui, raw))) }
                else ui.open(Sheet.Export(localExport(ui, u)))
            })
            SetBlock { ExportRow(ui) }
        }
        if (!pg.store.acct && !pg.db.sandbox) SetGroup("Zone sensible") {
            SetItem("Réinitialiser cet appareil", "Efface tous les profils sans compte et leurs données sur cet appareil.", "trash", danger = true, onClick = {
                ui.confirm("Réinitialiser les données ?", "Tous les profils, objectifs et validations de cet appareil seront effacés définitivement.", "Tout réinitialiser") { ui.close(); pg.stopTimer(true); pg.store.use(Db(), false); pg.store.commit(); ui.sessionChanged(); ui.toast("Données réinitialisées.") }
            })
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

private val A11Y_ICONS = mapOf("contrast" to "eye", "cvd" to "palette", "mono" to "drop", "motion" to "pause", "transp" to "gauge")

@Composable
fun A11yRows(ui: AppState) {
    ui.themeTick
    val pr = ui.prefs; val p = LocalPal.current
    Divided {
        A11Y_OPTS.forEach { (k, l, d) ->
            if (k == "cvd") Column {
                SetItem(l, d, A11Y_ICONS[k], p.type("measure").c)
                FlowRowCompat(Modifier.padding(start = 60.dp, end = 12.dp, bottom = 12.dp), gap = 6.dp) { CVD_TYPES.forEach { (v, t) -> FChip(t, pr.cvd == v, { pr.cvd = v; ui.themeTick++ }) } }
            } else {
                val on = when (k) { "contrast" -> pr.contrast; "mono" -> pr.mono; "motion" -> pr.motion; else -> pr.transp }
                SetToggle(l, d, A11Y_ICONS[k], p.type("duration").c, on, { v -> when (k) { "contrast" -> pr.contrast = v; "mono" -> pr.mono = v; "motion" -> pr.motion = v; else -> pr.transp = v }; ui.themeTick++ })
            }
        }
    }
}

/* ------------------------------------------------------------------ Aide */
@Composable
private fun HelpSettings(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current
    val open = { url: String -> try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { ui.toast("Aucune application pour ouvrir ce lien.", "x") } }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Toucher 7 fois la flamme de la version (comme les options pour développeurs d'Android) : surprise
        var taps by remember { mutableIntStateOf(0) }
        val spin by androidx.compose.animation.core.animateFloatAsState(taps * 51.4f, Motion.emphasis(), label = "verSpin")
        CatHero("help", "PepsGoal $APP_VERSION · application Android native${if (ui.pg.db.sandbox) " · démonstration" else ""}", leading = {
            Box(Modifier.size(52.dp).graphicsLayer { rotationZ = spin }.clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(p.flame2.copy(alpha = .3f), p.pink.copy(alpha = .22f))))
                .clickable(onClickLabel = "PepsGoal $APP_VERSION") {
                    taps++; Feedback.tick()
                    when {
                        taps in 3..6 -> ui.toast(if (7 - taps > 1) "Encore ${7 - taps} touches…" else "Encore une touche…", "sparkle")
                        taps == 7 -> ui.egg("version", "Tu es maintenant développeur… de flammes 🔥", "confetti")
                    }
                }, contentAlignment = Alignment.Center) { Flame(34.dp) }
        }, chips = { Chip(if (ui.pg.store.acct) "Compte synchronisé" else "Données sur l’appareil", if (ui.pg.store.acct) "cloud" else "lock", p.type("monthly").c); Chip("Licence MIT", "book", p.type("challenge").c) })
        if (!fr.pepsgoal.app.BuildConfig.PLAY) UpdateCard(ui)
        SetGroup("Découvrir") {
            SetItem("Tutoriel", "Revoir les bases de PepsGoal", "play", p.flame, onClick = { ui.fs = Fs.Tuto() })
            SetItem("Notes de version", "Les nouveautés de chaque version", "list", p.type("monthly").c, onClick = { ui.open(Sheet.Notes) })
        }
        SetGroup("PepsGoal", note = "Édité par Quentin CORBET. ${if (ui.pg.store.acct) "Ton compte est synchronisé avec le serveur PepsGoal." else "Tes données restent sur cet appareil."}") {
            SetItem("Site de PepsGoal", "Présentation, nouveautés et téléchargement", "globe", p.type("objective").c, onClick = { open(Links.SITE) })
            SetItem("Code source", "Logiciel libre sous licence MIT, publié sur GitHub", "github", p.ink2, onClick = { open(Links.REPO) })
            SetItem("Signaler un problème", "Un bug, une idée ? Ouvre un ticket, ou écris à ${Links.MAIL}", "flag", p.type("deadline").c, onClick = { open(Links.ISSUE) })
        }
        SetGroup("Informations légales") {
            SetItem("Conditions générales d’utilisation", "Règles d’utilisation, conduite sur l’espace social, modération", "book", p.type("project").c, onClick = { ui.open(Sheet.Legal("cgu")) })
            SetItem("Mentions légales", "Éditeur, hébergement, propriété intellectuelle, crédits", "book", p.type("project").c, onClick = { ui.open(Sheet.Legal("mentions")) })
            SetItem("Charte de confidentialité", "Données utilisées, stockage, partage et tes droits", "lock", p.type("abstinence").c, onClick = { ui.open(Sheet.Legal("privacy")) })
            SetItem("Licence MIT", "Licence du logiciel PepsGoal et des composants qu’il utilise", "book", p.type("challenge").c, onClick = { ui.open(Sheet.Legal("license")) })
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

/** Exports CSV (tableurs) et PDF (bilan imprimable) des statistiques et du journal. */
@Composable
fun ExportRow(ui: AppState) {
    val ctx = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    val go = { pdf: Boolean ->
        if (!busy) { busy = true
            ui.scope.launch {
                try {
                    val files = withContext(Dispatchers.IO) { if (pdf) listOfNotNull(fr.pepsgoal.app.sys.Exports.pdf(ctx)) else fr.pepsgoal.app.sys.Exports.csv(ctx) }
                    fr.pepsgoal.app.sys.Exports.share(ctx, files, if (pdf) "application/pdf" else "text/csv")
                } catch (e: Exception) { ui.toast("Export impossible : ${e.message ?: "erreur inconnue"}.", "x") }
                busy = false
            }
        }
    }
    SetRow("Statistiques et journal", "Tableurs CSV (objectifs, validations, journal) ou bilan PDF à imprimer ou envoyer à ton coach, ton médecin…")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PButton("CSV", { go(false) }, icon = "list", small = true, enabled = !busy)
        PButton("Bilan PDF", { go(true) }, icon = "book", small = true, enabled = !busy)
    }
}

/** Paramètres › Données : sauvegarde automatique chiffrée et restauration. */
@Composable
private fun BackupCard(ui: AppState) {
    val ctx = LocalContext.current; val p = LocalPal.current; val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(fr.pepsgoal.app.sys.AutoBackup.configured(ctx)) }
    var pass by remember { mutableStateOf("") }; var pass2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var rev by remember { mutableIntStateOf(0) }; rev.hashCode()
    var restoreUri by remember { mutableStateOf<Uri?>(null) }; var restorePass by remember { mutableStateOf("") }
    val pickDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = fr.pepsgoal.app.sys.AutoBackup.setup(ctx, uri, pass)
            busy = false; on = fr.pepsgoal.app.sys.AutoBackup.configured(ctx); pass = ""; pass2 = ""; rev++
            ui.toast(if (ok) "Sauvegarde automatique activée : première copie enregistrée." else "Dossier choisi, mais la première copie a échoué : réessaie « Sauvegarder maintenant ».", if (ok) "check" else "x")
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri; restorePass = "" }
    // Import d'un historique CSV (Loop Habit Tracker ou tableau maison)
    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val txt = try { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } catch (e: Exception) { null }
        val r = txt?.let { ui.pg.importCsv(it) }
        ui.bump()
        ui.toast(when { r == null -> "Fichier illisible."; r.checks == 0 -> "Aucune validation trouvée : la première colonne doit être la date (AAAA-MM-JJ)."
            else -> "Import terminé : ${pl(r.goals, "objectif créé", "objectifs créés")}, ${pl(r.checks, "validation", "validations")}." }, if (r != null && r.checks > 0) "check" else "x")
    }
    SetGroup("Sauvegarde", note = "Les fichiers .pgbak sont chiffrés : sans le mot de passe, personne ne peut les lire, PepsGoal compris.") {
        SetItem("Sauvegarde automatique chiffrée", "Chaque jour, une copie de tes objectifs, validations et journal dans le dossier de ton choix (téléphone, Google Drive…). Les 7 dernières copies sont gardées.", "lock", p.type("routine").c) {
            if (on) Chip("Active", "check", p.ok)
        }
        if (on) SetBlock {
            val last = ui.prefs.backupLast
            Muted(if (last > 0) "Dernière sauvegarde ${ago(last)}." else "Aucune sauvegarde réussie pour l’instant.", small = true)
            if (ui.prefs.backupError.isNotEmpty()) Muted("Dernière erreur : ${ui.prefs.backupError}", small = true)
            FlowRowCompat(gap = 8.dp) {
                PButton(if (busy) "Sauvegarde…" else "Sauvegarder maintenant", { busy = true; scope.launch { val ok = fr.pepsgoal.app.sys.AutoBackup.run(ctx); busy = false; rev++; ui.toast(if (ok) "Sauvegarde enregistrée." else "Sauvegarde impossible : vérifie l’accès au dossier.", if (ok) "check" else "x") } }, icon = "archive", small = true, enabled = !busy)
                PButton("Désactiver", { fr.pepsgoal.app.sys.AutoBackup.disable(ctx); on = false; ui.toast("Sauvegarde automatique désactivée. Les fichiers déjà enregistrés sont conservés.", "archive") }, kind = "ghost", small = true)
            }
        } else SetBlock {
            Field(pass, { pass = it }, "Mot de passe de sauvegarde", password = true, max = 100)
            Field(pass2, { pass2 = it }, "Confirme le mot de passe", password = true, max = 100)
            Muted("Sans ce mot de passe, impossible de restaurer : note-le précieusement. Il n’est pas enregistré sur le téléphone.", small = true)
            PButton(if (busy) "Préparation…" else "Choisir le dossier et activer", {
                when { pass.length < 8 -> ui.toast("Choisis un mot de passe d’au moins 8 caractères.", "x"); pass != pass2 -> ui.toast("Les deux mots de passe ne sont pas identiques.", "x"); else -> pickDir.launch(null) }
            }, Modifier.fillMaxWidth(), icon = "lock", kind = "primary", small = true, enabled = !busy)
        }
        SetItem("Restaurer une sauvegarde", "Ajoute les objectifs, validations et pages du journal d’un fichier .pgbak (nouveau téléphone, profil sans compte…). Rien n’est effacé.", "down", p.type("project").c, onClick = { pickFile.launch(arrayOf("*/*")) })
        SetItem("Importer un historique (CSV)", "Depuis Loop Habit Tracker (export « Checkmarks.csv ») ou un tableau : une colonne date, puis une colonne par habitude. Les validations importées ne rapportent pas de flammes.", "upload", p.type("measure").c,
            onClick = { pickCsv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*")) })
        restoreUri?.let { uri ->
            SetBlock {
                Field(restorePass, { restorePass = it }, "Mot de passe de la sauvegarde", password = true, max = 100)
                FlowRowCompat(gap = 8.dp) {
                    PButton(if (busy) "Restauration…" else "Restaurer", {
                        busy = true
                        scope.launch {
                            val n = try { fr.pepsgoal.app.sys.AutoBackup.restore(ctx, uri, restorePass) } catch (e: Exception) { null }
                            busy = false
                            if (n == null) ui.toast("Mot de passe incorrect, ou fichier qui n’est pas une sauvegarde PepsGoal.", "x")
                            else { restoreUri = null; ui.toast(if (n > 0) "Restauration terminée : ${pl(n, "objectif ajouté", "objectifs ajoutés")}." else "Restauration terminée : tout était déjà sur cet appareil.", "check") }
                        }
                    }, icon = "check", kind = "primary", small = true, enabled = !busy && restorePass.isNotEmpty())
                    PButton("Annuler", { restoreUri = null }, kind = "ghost", small = true)
                }
            }
        }
    }
}

/** État actuel d'une catégorie de réglages, en quelques mots (null : on garde sa description). */
fun setSummary(ui: AppState, k: String): String? {
    val u = ui.pg.me() ?: return null; val st = u.settings; val pr = ui.prefs; val ctx = ui.app
    return when (k) {
        "appearance" -> listOfNotNull(fr.pepsgoal.core.themeName(st.theme),
            if (pr.materialYou) "Material You" else if (st.accentCustom != null) "accent personnalisé" else "accent ${fr.pepsgoal.core.ACCENTS.getOrNull(st.accent)?.name ?: ""}".trim()).joinToString(" · ")
        "home" -> homeOrder(pr).count { it.second }.let { "$it ${if (it > 1) "cartes affichées" else "carte affichée"}" }
        "reminders" -> listOfNotNull(if (st.morning.on) "Matin ${st.morning.time}" else null, if (st.evening.on) "soir ${st.evening.time}" else null).joinToString(" · ").ifEmpty { "Rappels du matin et du soir coupés" }
        "account" -> if (ui.pg.isGuest(u)) "Mode invité, sur ce téléphone" else "@${u.username}"
        "weather" -> st.weather?.let { "${it.name}${if (st.theme == "weather") " · thème Météo actif" else ""}" } ?: "Aucune ville choisie"
        "privacy" -> if (!st.social) "Fonctions sociales désactivées" else "Visibilité par défaut : ${VIS[st.defaultVis]?.first?.lowercase() ?: st.defaultVis}"
        "ai" -> if (!fr.pepsgoal.app.ai.LocalAi.installed(ctx)) "Coach non installé" else if (pr.aiEnabled) "Coach installé et actif" else "Coach installé, en pause"
        "data" -> if (pr.backupUri.isNotEmpty()) "Sauvegarde auto${if (pr.backupLast > 0) " · dernière le ${fr.pepsgoal.core.fmtDT(pr.backupLast)}" else ""}" else null
        "perf" -> fr.pepsgoal.app.sys.Perf.MODES.firstOrNull { it.first == fr.pepsgoal.app.sys.Perf.mode }?.second?.let { m -> if (fr.pepsgoal.app.sys.Perf.mode == "auto") "Auto · ${fr.pepsgoal.app.sys.Perf.MODES.first { it.first == fr.pepsgoal.app.sys.Perf.recommended }.second}" else m }
        "a11y" -> listOfNotNull(if (pr.easy) "Lecture facile" else null, if (pr.textScale != 1f) "texte ${Math.round(pr.textScale * 100)} %" else null, if (pr.oneHand) "une main" else null,
            if (pr.contrast) "contraste" else null, if (pr.motion) "animations réduites" else null).joinToString(" · ").replaceFirstChar { it.uppercase() }.ifEmpty { null }
        "help" -> "Version ${fr.pepsgoal.core.APP_VERSION}"
        "places" -> if (u.places.isEmpty()) null else u.places.take(4).joinToString(" · ") { "${it.icon} ${it.name}" }
        else -> null
    }
}
