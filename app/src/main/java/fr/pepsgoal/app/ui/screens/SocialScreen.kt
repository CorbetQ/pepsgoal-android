package fr.pepsgoal.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.ui.*

/**
 * Onglet Social : réunit le Feed et les Amis (messages compris) derrière des onglets Feed | Amis | Messages posés sous
 * le héros (PTabs, pastilles roses des demandes et des messages non lus). Chaque partie reste l'écran d'origine
 * (FeedScreen, FriendsScreen) : aucune fonction n'est retirée ; leur liste commence par le héros et les onglets.
 */
@Composable
fun SocialScreen(ui: AppState) {
    ui.watch()
    val pg = ui.pg; val u = pg.me() ?: return
    val unread = Social.unreadMsgs(ui)
    val req = pg.db.friendships.count { it.to == u.id && it.status == "pending" }
    val cur = when { ui.tab == "feed" -> 0; ui.friendsTab == "msgs" -> 2; else -> 1 }
    val header: @Composable () -> Unit = {
        TabHeader(ui, "social") {
            PTabs(listOf("Feed" to "📰", "Amis" to "👥", "Messages" to "💬"), cur, { i ->
                when (i) {
                    0 -> ui.nav("feed")
                    2 -> { ui.friendsTab = "msgs"; ui.nav("friends") }
                    else -> { if (ui.friendsTab == "msgs") ui.friendsTab = "friends"; ui.nav("friends") }
                }
            }, badges = listOf(0, req, unread))
        }
    }
    CompositionLocalProvider(LocalTabHeader provides header) {
        Box(Modifier.fillMaxSize()) { if (ui.tab == "feed") FeedScreen(ui) else FriendsScreen(ui) }
    }
}
