package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import fr.pepsgoal.app.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.Sheet
import fr.pepsgoal.app.ui.*
import fr.pepsgoal.app.ui.theme.LocalPal
import fr.pepsgoal.app.ui.theme.Unbounded
import fr.pepsgoal.core.APP_VERSION
import fr.pepsgoal.core.Demo
import fr.pepsgoal.core.cloud.ApiError
import fr.pepsgoal.core.model.Db
import fr.pepsgoal.core.model.Settings
import fr.pepsgoal.core.model.User
import fr.pepsgoal.core.nowMs
import fr.pepsgoal.core.uid
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(ui: AppState) {
    val p = LocalPal.current; val pg = ui.pg
    var signup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }; var user by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    val local = remember(ui.rev) { pg.store.readLocal() }
    val hasGuest = local?.users?.any { it.guest } == true

    fun submit() {
        val un = user.trim().lowercase()
        if (!ui.cloud.navOnline) { err = "Hors ligne : la connexion à un compte nécessite internet. Tu peux continuer sans compte en attendant."; return }
        if (signup) {
            if (name.trim().length < 2) { err = "Indique ton nom (2 caractères minimum)."; return }
            if (!Regex("^[a-z0-9._]{3,20}$").matches(un)) { err = "Le nom d'utilisateur doit faire 3 à 20 caractères : lettres, chiffres, point ou tiret bas."; return }
            if (pass.length < 8) { err = "Le mot de passe doit contenir au moins 8 caractères."; return }
        } else if (un.isEmpty() || pass.isEmpty()) { err = "Indique ton nom d'utilisateur et ton mot de passe."; return }
        busy = true; err = null
        ui.scope.launch {
            try { if (signup) ui.cloud.register(un, name.trim(), pass) else ui.cloud.login(un, pass) }
            catch (e: ApiError) { err = e.message } catch (e: Exception) { err = e.message ?: "Connexion impossible." }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Logo() }
            PButton("Accessibilité", { ui.open(Sheet.A11y) }, icon = "eye", kind = "ghost", small = true)
        }
        Text(buildAnnotatedString { append("Fixe le cap.\n"); withStyle(SpanStyle(brush = Brush.linearGradient(listOf(p.flame2, p.flame, p.pink)))) { append("Garde la flamme.") } },
            fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp)
        Muted("Planifie tes objectifs, tes deadlines, tes routines et tes abstinences. Valide chaque jour, fais grandir ta série et avance avec tes amis.")
        FlowRowCompat(gap = 8.dp) { listOf("objective", "deadline", "routine", "abstinence").forEach { TypeChip(it) } }
        Glass(strong = true) {
            Seg(listOf(false to "Connexion", true to "Créer un compte"), signup, { signup = it; err = null })
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (signup) Field(name, { name = it }, "Prénom et nom", placeholder = "Camille Roux", max = 40, keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Field(user, { user = it }, "Nom d'utilisateur", placeholder = "camille", max = 20, keyboard = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.None))
                Field(pass, { pass = it }, "Mot de passe", placeholder = if (signup) "8 caractères minimum" else "", password = true, keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
                Err(err)
                if (signup) LegalLine(ui, "En créant un compte, tu acceptes les CGU et la charte de confidentialité.")
                PButton(if (busy) "…" else if (signup) "Créer mon compte" else "Me connecter", ::submit, Modifier.fillMaxWidth(), kind = "primary", enabled = !busy)
            }
            Muted("ou", Modifier.fillMaxWidth().padding(vertical = 10.dp), center = true, small = true)
            PButton(if (hasGuest) "Reprendre sans compte" else "Continuer sans compte", { guestStart(ui) }, Modifier.fillMaxWidth(), icon = "lock")
            Muted("Sans compte : données sur cet appareil uniquement, sans synchronisation ni fonctions sociales.", Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), small = true, center = true)
            PButton("Mode démonstration", { demoStart(ui) }, Modifier.fillMaxWidth(), leading = { Flame(18.dp) })
        }
        LegalLinks(ui)
        Muted("Mode démonstration : données d’exemple, rien n’est enregistré. · PepsGoal $APP_VERSION", Modifier.fillMaxWidth(), small = true, center = true)
    }
}

@Composable
fun LegalLine(ui: AppState, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Muted(text, Modifier.weight(1f), small = true)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PButton("CGU", { ui.open(Sheet.Legal("cgu")) }, kind = "ghost", small = true)
        PButton("Confidentialité", { ui.open(Sheet.Legal("privacy")) }, kind = "ghost", small = true)
    }
}

@Composable
fun LegalLinks(ui: AppState) {
    FlowRowCompat(Modifier.fillMaxWidth(), gap = 4.dp) {
        listOf("cgu" to "CGU", "mentions" to "Mentions légales", "privacy" to "Confidentialité", "license" to "Licence").forEach { (k, l) -> PButton(l, { ui.open(Sheet.Legal(k)) }, kind = "ghost", small = true) }
    }
}

/** Mode sans compte : un profil local sur cet appareil. */
fun guestStart(ui: AppState) {
    val st = ui.pg.store
    val db = st.readLocal() ?: Db()
    var g = db.users.firstOrNull { it.guest }
    if (g == null) {
        val hue = (0 until 12).random()
        g = User(uid(), "invite." + uid().take(6), "Invité", hue, guest = true, createdAt = nowMs(), settings = Settings(social = false))
        g.avatar.color = hue
        db.users.add(g)
    }
    db.session = g.id
    st.use(db, false); st.commit()
    ui.sessionChanged()
    ui.toast("Mode sans compte : tes données restent sur cet appareil.", "lock")
}

/** Démonstration : base d'exemple en mémoire, jamais enregistrée. */
fun demoStart(ui: AppState) {
    ui.pg.store.use(Demo.seed(), false)
    ui.sessionChanged()
    ui.toast("Mode démonstration : rien n’est enregistré.", "star")
}

@Composable
fun MaintenanceScreen(ui: AppState) {
    val m = ui.pg.maintNow()!!
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Logo()
        Text(buildAnnotatedString { append("On revient "); withStyle(SpanStyle(brush = Brush.linearGradient(listOf(LocalPal.current.flame2, LocalPal.current.pink)))) { append("très vite.") } }, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
        Muted("PepsGoal est en maintenance${m.until?.let { " jusqu'au ${fr.pepsgoal.core.fmtDT(it)}" } ?: ""}. ${m.message.ifEmpty { "Tes objectifs, ta série et tes flammes sont en sécurité." }}")
        Muted("Ta série est protégée : les jours de maintenance ne la cassent pas.", small = true)
        PButton("Se déconnecter", { ui.run { ui.cloud.logout() } }, icon = "logout", enabled = ui.pg.store.online)
    }
}
