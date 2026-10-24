package fr.pepsgoal.core

/**
 * Quêtes : une courte histoire en 7 chapitres. Chaque journée active (au moins une validation) débloque le chapitre
 * suivant, un par jour. La dernière page rapporte [Quest.reward] flammes.
 */
class Quest(val key: String, val title: String, val emoji: String, val scene: String, val reward: Int, val chapters: List<Pair<String, String>>)

val QUESTS = listOf(
    Quest("loire", "Le voyage sur la Loire", "⛵", "loire", 120, listOf(
        "Le départ" to "À Montsoreau, une vieille toue t’attend au ponton. Le passeur te tend une rame : « Le fleuve ne se presse jamais, mais il arrive toujours. » Tu largues les amarres.",
        "Les bancs de sable" to "Le courant ralentit entre les bancs blonds. Un héron te regarde passer sans bouger d’une plume. Tu apprends à lire l’eau : les remous trahissent les pièges.",
        "Saumur" to "Le château blanc se dresse sur la rive. Sur le quai, un marchand de fouées t’en offre une, encore chaude. « Pour la route, et pour le courage. »",
        "L’orage" to "Le ciel se couvre d’un coup. Tu t’abrites sous un saule, trempé mais souriant : les jours difficiles font partie du voyage. Demain, le soleil reviendra.",
        "Les coteaux" to "Les vignes descendent jusqu’à l’eau. Un vigneron t’invite à goûter le chenin de l’année. Il dit que les meilleurs vins sont ceux qu’on a attendus.",
        "La levée" to "Sur la digue, des cyclistes te saluent en filant vers Angers. Tu réalises combien de chemin tu as parcouru, un coup de rame à la fois.",
        "L’arrivée" to "La Maine rejoint la Loire et le soir tombe, doré. Tu amarres la toue. Le passeur avait raison : sans te presser, tu es arrivé. Bravo, navigateur !",
    )),
    Quest("metro", "Le métro de minuit", "🚇", "tunnel", 120, listOf(
        "Le dernier départ" to "Minuit sonne. Les portes du métro s’ouvrent sur une rame vide, éclairée d’ambre. Sur la girouette, un seul mot : « Persévérance ». Tu montes.",
        "Station Inconnue" to "La rame s’arrête dans une station qui n’existe sur aucun plan. Sur le quai, une affiche : « Ici descendent ceux qui abandonnent. » Les portes se referment. Tu es resté.",
        "Le tunnel bleu" to "Les néons défilent si vite qu’ils forment une rivière de lumière. Le métro file sans conducteur, comme tes bonnes habitudes : automatique, à force de répétition.",
        "La panne" to "Tout s’éteint. Silence. Puis une à une, les lampes se rallument. Une voix annonce : « Les pannes ne durent pas. Les séries reprennent. »",
        "Le voyageur" to "Un passager s’assoit en face de toi. Il porte une flamme dans une lanterne. « Je fais ce trajet depuis 365 nuits », dit-il en souriant. « Un jour, ce sera toi. »",
        "Le terminus doré" to "La rame ralentit dans une station inondée de lumière. Les murs sont couverts des noms de ceux qui ont tenu bon. Tu cherches le tien…",
        "Le matin" to "Tu sors à l’air libre : c’est l’aube. Le métro repart dans le tunnel, prêt pour le prochain voyageur. Ta flamme, elle, brille plus fort que jamais.",
    )),
)

class QuestState(val quest: Quest, val done: Int, val canUnlock: Boolean, val finished: Boolean)

private fun Pg.questMarks(u: fr.pepsgoal.core.model.User, key: String) = u.shopClaimed.filter { it.startsWith("quest:$key:") }

/** Quête en cours (la première non terminée), chapitres lus, et si un chapitre peut s'ouvrir aujourd'hui. */
fun Pg.questState(): QuestState? {
    val u = me() ?: return null; val t = today()
    for (q in QUESTS) {
        val marks = questMarks(u, q.key); val n = marks.size
        if (n >= q.chapters.size) continue
        val unlockedToday = QUESTS.any { qq -> questMarks(u, qq.key).any { it.endsWith(":$t") } }
        val active = activeDays(u.id).contains(t)
        return QuestState(q, n, active && !unlockedToday, false)
    }
    return QUESTS.lastOrNull()?.let { QuestState(it, it.chapters.size, false, true) }
}

/** Ouvre le chapitre suivant (une journée active, un chapitre par jour). Renvoie le numéro ouvert (1…7) ou null. */
fun Pg.unlockChapter(): Int? {
    val u = me() ?: return null; val st = questState() ?: return null
    if (!st.canUnlock || st.finished) return null
    val n = st.done + 1
    u.shopClaimed = trimClaimed(u.shopClaimed + "quest:${st.quest.key}:$n:${today()}")
    if (n == st.quest.chapters.size) { u.shopBonus += st.quest.reward; notify(u.id, "Quête terminée : « ${st.quest.title} » ${st.quest.emoji} +${st.quest.reward} flammes.", "badge") }
    store.commit(); return n
}

/** Quêtes terminées. */
fun Pg.questsDone(): List<Quest> { val u = me() ?: return emptyList(); return QUESTS.filter { questMarks(u, it.key).size >= it.chapters.size } }
