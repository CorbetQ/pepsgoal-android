package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ui.*

/**
 * Onglet Social : réunit le Feed et les Amis (messages compris) derrière un contrôle segmenté Feed | Amis | Messages.
 * Chaque partie reste l'écran d'origine (FeedScreen, FriendsScreen) : aucune fonction n'est retirée.
 */
@Composable
fun SocialScreen(ui: AppState) {
    ui.watch()
    val bars = LocalBars.current
    val pg = ui.pg; val u = pg.me() ?: return
    val unread = Social.unreadMsgs(ui)
    val req = pg.db.friendships.count { it.to == u.id && it.status == "pending" }
    val cur = when { ui.tab == "feed" -> "feed"; ui.friendsTab == "msgs" -> "msgs"; else -> "friends" }
    Column(Modifier.fillMaxSize()) {
        Seg(listOf("feed" to "Feed", "friends" to (if (req > 0) "Amis · $req" else "Amis"), "msgs" to (if (unread > 0) "Messages · $unread" else "Messages")), cur, { k ->
            when (k) {
                "feed" -> ui.nav("feed")
                "msgs" -> { ui.friendsTab = "msgs"; ui.nav("friends") }
                else -> { if (ui.friendsTab == "msgs") ui.friendsTab = "friends"; ui.nav("friends") }
            }
        }, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = bars.calculateTopPadding() + 4.dp, bottom = 4.dp), icons = listOf("feed", "users", "comment"))
        // La liste de la partie choisie défile sous le contrôle, jusqu'à la barre d'onglets
        CompositionLocalProvider(LocalBars provides PaddingValues(top = 0.dp, bottom = bars.calculateBottomPadding())) {
            Box(Modifier.weight(1f)) { if (ui.tab == "feed") FeedScreen(ui) else FriendsScreen(ui) }
        }
    }
}
