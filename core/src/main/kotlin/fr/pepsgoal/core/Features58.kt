package fr.pepsgoal.core

import fr.pepsgoal.core.model.*

/* ---------------------------------------------------------------- Fonds des conversations */

/** Fonds illustrés qu'une personne peut utiliser : achetés, ou gratuits (clés de [SHOP_BGS]). */
fun Pg.ownedBgs(u: User? = me()): List<String> = SHOP_BGS.filter { owns("bg:${it.key}", it.price, u) }.map { it.key }

/**
 * Fonds proposés pour une conversation : ceux de chaque membre (partagés dans la conversation) et les miens,
 * avec, pour chacun, les membres qui l'ont débloqué. [shared] : membre → fonds qu'il a partagés.
 */
fun Pg.chatBgChoices(shared: Map<String, Set<String>>, members: Collection<String>): List<Pair<ShopBg, List<String>>> {
    val me = meId(); val mine = ownedBgs().toSet()
    val by = HashMap<String, MutableList<String>>()
    mine.forEach { by.getOrPut(it) { mutableListOf() }.add(me) }
    shared.filterKeys { it != me && it in members }.forEach { (id, keys) -> keys.forEach { k -> by.getOrPut(k) { mutableListOf() }.add(id) } }
    return SHOP_BGS.filter { it.key in by }.map { it to by.getValue(it.key) }
}
