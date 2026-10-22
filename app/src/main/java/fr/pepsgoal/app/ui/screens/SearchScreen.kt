package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Fs
import fr.pepsgoal.app.Screen
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.data.Journal
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.Text
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.core.*
import fr.pepsgoal.core.cloud.str

/** Mots-clés supplémentaires des catégories de réglages (la recherche trouve « vibration » dans Accessibilité…). */
internal val SET_WORDS = mapOf(
    "appearance" to "theme sombre clair oled couleur accent fond arriere-plan flou verre material you avatar",
    "home" to "accueil cartes ordre masquer afficher personnaliser",
    "reminders" to "rappel notification heure live updates rappels intelligents",
    "account" to "compte mot de passe deconnexion nom email profil",
    "weather" to "meteo ville ciel pluie temperature",
    "places" to "emplacement lieu carte maison travail adresse position geolocalisation",
    "privacy" to "social confidentialite visibilite stories amis bloquer",
    "ai" to "ia intelligence artificielle coach memoire modele",
    "health" to "sante pas sommeil poids eau health connect sport distance",
    "widgets" to "widget ecran d'accueil",
    "data" to "donnees export synchronisation reinitialiser sauvegarde",
    "a11y" to "accessibilite contraste daltonien monochrome animations transparence lecture facile taille texte densite une main vibrations sons balayage talkback",
    "help" to "aide tutoriel notes de version a propos mise a jour licence"
)

/** Pages et outils ouvrables depuis la recherche. */
private val PAGES = listOf(
    Triple("Statistiques", "trend", "stats statistiques records heatmap") , Triple("Journal", "book", "journal humeur note"),
    Triple("Mode Focus", "timer", "focus concentration minuteur pomodoro"), Triple("Coach IA", "sparkle", "coach ia chat assistant"),
    Triple("Historique des stories", "image", "stories histoire archive"), Triple("Défis de la communauté", "trophy", "defi defis communaute challenge classement")
)

/** Recherche globale : objectifs, amis, messages, réglages, journal et pages, avec dictée vocale. */
@Composable
fun SearchScreen(ui: AppState, initial: String) {
    ui.watch()
    val ctx = LocalContext.current; val p = LocalPal.current; val pg = ui.pg; val u = pg.me() ?: return
    var q by rememberSaveable { mutableStateOf(initial) }
    val dictate = rememberVoiceCommand(ui) { q = it }
    val command = rememberVoiceCommand(ui)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initial.isEmpty()) try { focus.requestFocus() } catch (e: Exception) {} }
    val n = VoiceCmd.norm(q)
    fun hit(s: String?) = s != null && VoiceCmd.norm(s).contains(n)
    ScreenFrame(ui, "Recherche", "Retour", { ui.back() }, icon = "search", color = p.type("objective").c, color2 = p.type("project").c) {
        item("field") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(q, { q = it }, "", Modifier.weight(1f).focusRequester(focus), placeholder = "Objectif, ami, message, réglage…", max = 80, icon = "search")
                IconBtn("mic", "Dicter la recherche", dictate, size = 50.dp)
            }
        }
        if (n.length < 2) {
            item("help") {
                Glass {
                    CardHead("Commande vocale")
                    Muted("Touche le micro et parle : PepsGoal comprend des phrases simples, même sans connexion quand ton téléphone le permet.", small = true)
                    Spacer(Modifier.height(10.dp))
                    FlowRowCompat(gap = 6.dp) { VoiceCmd.EXAMPLES.forEach { e -> FChip("« $e »", false, { VoiceCmd.run(ui, ctx, e.replace("’", "'")) }) } }
                    Spacer(Modifier.height(12.dp))
                    PButton("Parler", command, icon = "mic", kind = "primary")
                }
            }
            return@ScreenFrame
        }
        // Objectifs
        val goals = pg.myGoals(u.id).filter { hit(it.title) || hit(it.note) || hit(typeInfo(it.type).label) }.sortedBy { it.archived }.take(20)
        if (goals.isNotEmpty()) item("goals") {
            Glass(pad = 10.dp) {
                Box(Modifier.padding(6.dp)) { CardHead("Objectifs") { Count(goals.size) } }
                goals.forEach { g -> Result(title = g.title, sub = "${typeInfo(g.type).label}${if (g.archived) " · archivé" else ""}", lead = { GoalIcon(g, 40.dp) }) { ui.open(Sheet.Goal(g.id)) } }
            }
        }
        // Amis (et autres profils connus)
        if (pg.socialOn(u)) {
            val friends = pg.friendsOf(u.id).toSet()
            val people = pg.db.users.filter { it.id != u.id && hit(it.name) }.sortedByDescending { it.id in friends }.take(12)
            if (people.isNotEmpty()) item("people") {
                Glass(pad = 10.dp) {
                    Box(Modifier.padding(6.dp)) { CardHead("Personnes") { Count(people.size) } }
                    people.forEach { x -> Result(title = x.name, sub = if (x.id in friends) "Ami" else "Profil", lead = { Av(x, 40.dp) }) { ui.open(Sheet.User(x.id)) } }
                }
            }
            // Messages (déchiffrés sur cet appareil)
            val msgs = pg.db.messages.asReversed().asSequence().filter { Social.body(ui, it)?.str("k") !in fr.pepsgoal.core.cloud.Msg.SILENT && it.id !in ui.prefs.hiddenMsgs }.mapNotNull { m -> Social.preview(ui, m).takeIf { hit(it) }?.let { m to it } }.take(12).toList()
            if (msgs.isNotEmpty()) item("msgs") {
                Glass(pad = 10.dp) {
                    Box(Modifier.padding(6.dp)) { CardHead("Messages") { Count(msgs.size) } }
                    msgs.forEach { (m, txt) ->
                        val grp = m.chat?.let { id -> pg.db.chats.firstOrNull { it.id == id } }
                        val other = pg.userById(if (m.from == u.id) m.to ?: "" else m.from)
                        Result(title = grp?.let { "${it.emoji} ${it.name}" } ?: other?.name ?: "Conversation", sub = "${fmtDT(m.at)} · $txt", lead = { if (grp == null) Av(other, 40.dp) else IconTile("users", p.type("group").c, p.type("routine").c, 40.dp, 13.dp) }) {
                            ui.push(if (grp != null) Screen.Chat(group = grp.id) else Screen.Chat(with = other?.id))
                        }
                    }
                }
            }
        }
        // Journal
        val pages = Journal.all(ctx).filter { hit(it.text) }.sortedByDescending { it.date }.take(10)
        if (pages.isNotEmpty()) item("journal") {
            Glass(pad = 10.dp) {
                Box(Modifier.padding(6.dp)) { CardHead("Journal") { Count(pages.size) } }
                pages.forEach { e -> Result(title = cap(weekdayLongDate(parseYmd(e.date))), sub = e.text, lead = { Emoji(Journal.MOODS.getOrNull(e.mood - 1)?.second ?: "😐", 32.dp) }) { ui.push(Screen.Journal) } }
            }
        }
        // Réglages et pages
        val sets = SET_CATS.filter { c -> hit(c[1]) || hit(c[2]) || hit(SET_WORDS[c[0]]) }
        val tools = PAGES.filter { hit(it.first) || hit(it.third) }
        if (sets.isNotEmpty() || tools.isNotEmpty()) item("settings") {
            Glass(pad = 10.dp) {
                Box(Modifier.padding(6.dp)) { CardHead("Réglages et pages") }
                tools.forEach { (l, ic, _) ->
                    Result(title = l, sub = "Ouvrir", lead = { IconTile(ic, p.flame2, p.pink, 40.dp, 13.dp) }) {
                        when (ic) { "trend" -> ui.push(Screen.Stats); "book" -> ui.push(Screen.Journal); "timer" -> ui.fs = Fs.Focus(); "sparkle" -> ui.push(Screen.CoachChat); "trophy" -> ui.push(Screen.Challenges); else -> ui.push(Screen.Stories) }
                    }
                }
                sets.forEach { c ->
                    val (k1, k2) = SET_COLORS[c[0]] ?: ("routine" to "duration")
                    Result(title = c[1], sub = c[2], lead = { IconTile(c[3], p.type(k1).c, p.type(k2).c, 40.dp, 13.dp) }) { ui.push(Screen.Settings(c[0])) }
                }
            }
        }
        if (goals.isEmpty() && sets.isEmpty() && tools.isEmpty() && pages.isEmpty()) item("none") {
            Glass { Empty("Aucun résultat pour « ${q.trim()} »", "Essaie un autre mot, ou dis une commande avec le micro.", icon = "search") { PButton("Parler", command, icon = "mic", small = true) } }
        }
    }
}

@Composable
private fun Result(title: String, sub: String, lead: @Composable () -> Unit, onClick: () -> Unit) {
    val p = LocalPal.current
    val inter = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).bounce(inter, .97f).clip(RoundedCornerShape(16.dp)).springClick(inter, onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        lead()
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 13.sp, color = p.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = p.ink3, fontSize = 22.sp)
    }
}
