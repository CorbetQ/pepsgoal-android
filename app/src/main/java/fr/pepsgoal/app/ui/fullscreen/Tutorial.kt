package fr.pepsgoal.app.ui.fullscreen

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.sys.Poller
import fr.pepsgoal.app.sys.Reminders
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.screens.CheerButton
import fr.pepsgoal.app.ui.screens.HeroFlame
import fr.pepsgoal.app.ui.screens.TimeButton
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.step
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.*
import fr.pepsgoal.core.model.User
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/** Une page du tutoriel : chapitre, titre, explication, couleurs et contenu (illustration ou réglages). */
private val TUTO_EMOJI = mapOf("star" to "⭐", "plus" to "➕", "check" to "✅", "snow" to "❄️", "bell" to "🔔", "palette" to "🎨", "users" to "👥", "medal" to "🏅", "flameo" to "🔥")

private class Page(val chapter: String, val title: String, val text: String, val icon: String, val c1: String, val c2: String, val body: @Composable () -> Unit)

/**
 * Tutoriel plein écran : les bases pas à pas (créer, valider, série), puis les premiers réglages faits directement
 * dans le tutoriel (notifications, interface, social). On passe d'une page à l'autre en glissant ou avec les boutons.
 */
@Composable
fun TutorialFs(ui: AppState, start: Int) {
    ui.watch(); ui.themeTick
    val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: run { LaunchedEffect(Unit) { ui.closeFs() }; return }
    val pages = tutorialPages(ui, u)
    val pager = rememberPagerState(initialPage = start.coerceIn(0, pages.lastIndex)) { pages.size }
    val scope = rememberCoroutineScope()
    val i = pager.currentPage; val last = i == pages.lastIndex
    BackHandler { if (i > 0) scope.launch { pager.animateScrollToPage(i - 1) } else finish(ui, u) }
    val c1 by animateColorAsState(p.type(pages[i].c1).c, tween(500), label = "c1")
    val c2 by animateColorAsState(p.type(pages[i].c2).c, tween(500), label = "c2")
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Progression et « Passer »
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pages.indices.forEach { k ->
                        val f by animateFloatAsState(if (k < i) 1f else if (k == i) 1f else 0f, tween(400), label = "seg")
                        // Pastilles de page pleines : rail surface cerclé, page courante en néon, pages vues en prune
                        Box(Modifier.weight(if (k == i) 2.2f else 1f).height(10.dp).clip(CircleShape).background(p.control).padding(2.dp).clip(CircleShape).background(p.surface)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(if (k == i) vivid(c1) else p.ink))
                        }
                    }
                }
                if (!last) PButton("Passer", { scope.launch { pager.animateScrollToPage(pages.lastIndex) } }, small = true)
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { k ->
                val pg2 = pages[k]
                // Effet de profondeur pendant le glissement : la page qui part recule et s'estompe
                val off = ((pager.currentPage - k) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Column(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - off * .6f; val s = 1f - off * .08f; scaleX = s; scaleY = s }
                    .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Médaillon à marche (emoji 56 dans un disque néon de 96), chapitre et titre sur plaques, texte en carte
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val tc = vivid(p.type(pg2.c1).c)
                        Box(Modifier.size(96.dp, 101.dp).chunk(tc, tc.step(), 48.dp, 5.dp, 0.dp), contentAlignment = Alignment.Center) { Emoji(TUTO_EMOJI[pg2.icon] ?: "✨", 56.dp) }
                        MiniPlaque(pg2.chapter)
                    }
                    TitlePlaque(pg2.title, size = 28)
                    Glass(Modifier.fillMaxWidth()) { Text(rich(pg2.text), color = p.ink2, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp) }
                    pg2.body()
                    Spacer(Modifier.height(8.dp))
                }
            }
            // Navigation
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (i > 0) IconBtn("chevronleft", "Page précédente", { scope.launch { pager.animateScrollToPage(i - 1) } })
                if (last) {
                    val bonus = !u.tutoBonus
                    PButton(if (bonus) "Récupérer mes $TUTO_GELS gels et commencer" else "C’est parti", { finish(ui, u) }, Modifier.weight(1f), icon = if (bonus) "snow" else "check", kind = "primary")
                } else PButton(if (i == 0) "Commencer" else "Suivant", { scope.launch { pager.animateScrollToPage(i + 1) } }, Modifier.weight(1f), kind = "primary")
            }
        }
    }
}

/** Fin du tutoriel : cadeau de bienvenue (une seule fois), puis retour à l'accueil. */
private fun finish(ui: AppState, u: User) {
    val pg = ui.pg; val bonus = !u.tutoBonus
    u.tutorialDone = true; ui.closeFs()
    if (bonus) {
        u.tutoBonus = true
        if (pg.store.acct) { pg.store.commit(); ui.cloud.welcomeBonus() }
        else { u.freezes += TUTO_GELS; pg.notify(u.id, "Cadeau de bienvenue : $TUTO_GELS gels de série ajoutés à ton stock.", "freeze"); pg.store.commit(); ui.sparks(TUTO_GELS); ui.toast("$TUTO_GELS gels de série ajoutés !", "snow") }
    } else pg.store.commit()
}

@Composable
private fun tutorialPages(ui: AppState, u: User): List<Page> {
    val p = LocalPal.current; val pg = ui.pg; val s = u.settings; val ctx = LocalContext.current
    val save = { pg.store.commit() }
    return listOf(
        Page("Bienvenue", "Bienvenue sur PepsGoal", "Fixe tes caps, valide chaque jour ce que tu as fait et garde ta flamme allumée. En quelques pages, on voit **comment débuter** puis on règle ensemble **les notifications, l’interface et le social**.", "star", "challenge", "deadline") {
            Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { HeroFlame(1, true) }
        },
        Page("Étape 1 · Créer", "Crée ton premier objectif", "Touche le bouton **+** en bas à droite de l’accueil. Choisis un type, donne un titre et un emoji, règle les jours ou la cible : c’est prêt. Commence petit : un seul objectif facile, tous les jours.", "plus", "routine", "objective") {
            Glass {
                Text("Quelques types pour démarrer", fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                listOf("routine" to "Une habitude certains jours (sport, lecture…)", "objective" to "Atteindre un nombre (10 livres, 100 km…)", "abstinence" to "Tenir sans (sucre, tabac…)", "deadline" to "Finir avant une date").forEach { (t, d) ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { TypeChip(t); Muted(d, Modifier.weight(1f), small = true) }
                }
                Muted("Et aussi : limite, durée avec minuteur, mesure en courbe, période horaire, projet par étapes, challenge, paliers, défi de groupe et duel.", Modifier.padding(top = 6.dp), small = true)
            }
        },
        Page("Étape 2 · Valider", "Valide chaque jour", "Sur l’accueil, touche le rond à droite d’un objectif quand c’est fait. Chaque validation rapporte des **flammes** qui te font monter de niveau. Tu peux aussi valider depuis la notification de rappel ou le widget. Essaie :", "check", "routine", "duration") {
            var done by remember { mutableStateOf(false) }
            val tc = p.type("routine")
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Emoji("🏃", 34.dp)
                    Column(Modifier.weight(1f)) { Text("Courir 30 minutes", fontWeight = FontWeight.ExtraBold); Muted(if (done) "Validé · +10 flammes" else "À faire aujourd’hui", small = true) }
                    CheckButton(done, done, if (done) "Validé" else "Valider", tc.c) { done = !done; if (done) ui.sparks(1) }
                }
            }
        },
        Page("Étape 3 · Tenir", "Ta série et tes gels", "Une seule validation par jour allume la flamme du jour ; les jours d’affilée forment ta **série**, en haut de l’écran. Un jour sans ? Un **gel** protège ta série automatiquement. Tu en gagnes quand tes publications reçoivent $FREEZE_EVERY encouragements.", "snow", "objective", "period") {
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    WD.forEachIndexed { k, d -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { if (k == 4) Pi("snow", size = 24.dp, tint = p.type("objective").ink) else Flame(24.dp, dim = k >= 6); Text(d, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) } }
                }
                Muted("Le vendredi, un gel a sauvé la série.", Modifier.padding(top = 8.dp), small = true, center = true)
            }
        },
        Page("Réglage 1 · Notifications", "Ne rate rien", "Autorise les notifications pour recevoir tes **rappels**, les **messages** et les **encouragements**, même application fermée. Règle aussi l’heure de tes rappels du matin et du soir.", "bell", "period", "project") {
            var granted by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
            var unr by remember { mutableStateOf(Poller.unrestricted(ctx)) }
            LifecycleResumeEffect(Unit) { granted = NotificationManagerCompat.from(ctx).areNotificationsEnabled(); unr = Poller.unrestricted(ctx); onPauseOrDispose {} }
            val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it || NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
            Glass {
                SetRow("Notifications", if (granted) "Activées : tu seras prévenu au bon moment." else "Indispensables pour les rappels et les messages.") {
                    if (granted) Chip("Activées", "check", p.ok)
                    else PButton("Autoriser", { if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS) else fr.pepsgoal.app.sys.Notifier.openSettings(ctx) }, icon = "bell", color = p.type("period").c, small = true)
                }
                SetRow("Rappel du matin", "Ce qui t’attend aujourd’hui") {
                    TimeButton(s.morning.time, { s.morning.time = it; save(); Reminders.reschedule(ctx) }, "Rappel du matin")
                    PSwitch(s.morning.on, { s.morning.on = it; save(); Reminders.reschedule(ctx) })
                }
                SetRow("Rappel du soir", "Seulement s’il te reste des validations") {
                    TimeButton(s.evening.time, { s.evening.time = it; save(); Reminders.reschedule(ctx) }, "Rappel du soir")
                    PSwitch(s.evening.on, { s.evening.on = it; save(); Reminders.reschedule(ctx) })
                }
                if (pg.store.acct) SetRow("Messages application fermée", if (unr) "PepsGoal peut relever tes messages même en veille." else "Retire PepsGoal de l’optimisation de batterie pour recevoir tes messages à temps.") {
                    if (unr) Chip("Prêt", "check", p.ok) else PButton("Autoriser", { Poller.askUnrestricted(ctx) }, small = true)
                }
            }
        },
        Page("Réglage 2 · Interface", "À ton image", "Choisis le **thème**, les couleurs **Material You** de ton téléphone ou une **couleur d’accent**, et la présence de ton fond. Les changements s’appliquent tout de suite : regarde autour de toi !", "palette", "measure", "abstinence") {
            Glass {
                Text("Thème", fontWeight = FontWeight.ExtraBold)
                FlowRowCompat(Modifier.padding(top = 8.dp, bottom = 12.dp), gap = 6.dp) { THEMES.forEach { (k, l) -> FChip(l, s.theme == k, { s.theme = k; save() }) } }
                val dynOk = Build.VERSION.SDK_INT >= 31
                SetRow("Couleurs Material You", if (dynOk) "Les couleurs de ton fond d’écran sur toute l’interface" else "Disponible à partir d’Android 12") {
                    PSwitch(ui.prefs.materialYou && dynOk, { ui.prefs.materialYou = it; ui.themeTick++ }, enabled = dynOk)
                }
                if (!ui.prefs.materialYou) {
                    Text("Couleur d’accent", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
                    FlowRowCompat(Modifier.padding(top = 8.dp, bottom = 8.dp), gap = 4.dp) {
                        ACCENTS.forEachIndexed { k, a -> PSwatch(accentBrush(k), s.accent == k && s.accentCustom == null, a.name, { s.accent = k; s.accentCustom = null; save() }) }
                    }
                }
                Text("Présence du fond", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
                FlowRowCompat(Modifier.padding(top = 8.dp), gap = 6.dp) {
                    listOf(0 to "Discrète", 1 to "Équilibrée", 2 to "Intense").forEach { (k, l) -> FChip(l, ui.prefs.bgLevel == k, { ui.prefs.bgLevel = k; ui.themeTick++ }) }
                }
            }
            Hint("Arrière-plans, icône de profil, accessibilité : tout le reste est dans Profil › Paramètres.")
        },
        Page("Réglage 3 · Social", "Avance avec tes amis", "Ajoute des amis depuis l’onglet **Amis**, publie tes victoires dans le **Feed** (pour tes amis ou pour tous), partage des **stories** 24 h et écris-leur : les **messages** sont chiffrés de bout en bout. Pas envie de social ? Désactive-le, tes objectifs restent intacts.", "users", "group", "duel") {
            Glass {
                if (pg.isGuest(u) || pg.db.sandbox) SetRow("Fonctions sociales", "Crée un compte pour ajouter des amis et publier.") { Chip("Compte requis", "lock", p.type("deadline").c) }
                else {
                    SetRow("Fonctions sociales", "Feed, stories, amis, messages et encouragements") { PSwitch(s.social, { s.social = it; save() }) }
                    if (s.social) {
                        Text("Visibilité de tes nouveaux objectifs", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                        Seg(VIS.keys.map { it to VIS.getValue(it).first }, s.defaultVis, { s.defaultVis = it; save() }, icons = VIS.values.map { it.second })
                        SetRow("Suggestions d’amis", "Ton profil peut être proposé aux amis de tes amis") { PSwitch(!s.noSuggest, { s.noSuggest = !it; save() }) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { CheerButton(true, 12) {}; Hint("Une flamme = un encouragement.") }
        },
        Page("Bonus", "Les petits plus", "Chaque semaine, mois et année, ton **récap** t’attend sur l’accueil. Le **minuteur** et les périodes s’affichent en direct dans la barre d’état (Live Updates), et le **widget** te laisse valider depuis l’écran d’accueil. Les **badges** récompensent ta régularité, de Bronze à Diamant.", "medal", "monthly", "challenge") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { (0..2).forEach { t -> fr.pepsgoal.app.ui.screens.Medal(listOf("🔥", "🏆", "⭐")[t], t, false, 76) } }
        },
        Page("C’est prêt", "À toi de jouer !", "Crée ton premier objectif avec le bouton **+**, valide-le aujourd’hui et allume ta première flamme. Tu peux revoir ce tutoriel à tout moment dans Paramètres › Aide." + if (!u.tutoBonus) "\n\n**Cadeau de bienvenue : $TUTO_GELS gels de série** pour bien démarrer." else "", "flameo", "deadline", "challenge") {
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) { HeroFlame(1, true) }
            if (pg.myGoals(u.id).none { !it.archived }) PButton("Créer mon premier objectif", { finish(ui, u); ui.open(Sheet.GoalForm()) }, Modifier.fillMaxWidth(), icon = "plus")
        },
    )
}
