package fr.pepsgoal.app.ui

import fr.pepsgoal.app.AppState
import fr.pepsgoal.app.sys.AppIcons
import fr.pepsgoal.core.*

/** A pack is explicit opt-in: turn off local colour/season overrides so all four choices are visible.
 * Accessibility, motion reduction and performance controls are deliberately preserved.
 */
fun AppState.applyThemePack(key: String): String? {
    val pack = themePack(key) ?: return "Ce pack n’existe pas."
    pg.reconcileThemePacks()
    if (!pg.packQuote(pack).owned) return "Tu dois posséder tous les éléments de ce pack."
    if (AppIcons.ALL.none { it.key == pack.iconKey }) return "L’icône de ce pack est indisponible."
    val previousIcon = AppIcons.current(app)
    try {
        AppIcons.set(app, pack.iconKey)
    } catch (e: Exception) {
        try { AppIcons.set(app, previousIcon) } catch (_: Exception) {}
        return "L’icône n’a pas pu être appliquée. Le pack reste dans ta collection."
    }
    val error = pg.applyPack(key)
    if (error != null) {
        try { AppIcons.set(app, previousIcon) } catch (_: Exception) {}
        return error
    }
    prefs.raw.edit()
        .putBoolean("materialYou", false)
        .putBoolean("seasonal", false)
        .putBoolean("bgSeasonAuto", false)
        .putString("bgWhere", "all")
        .putString("bgLight", "theme")
        .apply()
    themePreview = null
    themeTick++
    return null
}

fun AppState.themePackActive(key: String, currentIcon: String = AppIcons.current(app)): Boolean {
    val pack = themePack(key) ?: return false
    return pg.packMatches(pack) && currentIcon == pack.iconKey &&
        !prefs.materialYou && !prefs.seasonal && !prefs.bgSeasonAuto && prefs.bgWhere == "all" && prefs.bgLight == "theme"
}
