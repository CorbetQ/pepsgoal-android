package fr.pepsgoal.core

/**
 * Compagnon flamme : une petite créature qui grandit par stades avec la série (la meilleure, pour ne jamais rapetisser)
 * et le niveau, et dont l'humeur suit la journée et le journal. Des accessoires s'achètent dans la boutique
 * (« acc:<clé> ») ; une tenue s'achète ou se gagne avec le passe de la saison (« outfit:<clé> »).
 */
class PetStage(val key: String, val name: String, val streak: Int, val level: Int)

val PET_STAGES = listOf(
    PetStage("egg", "Œuf de braise", 0, 1), PetStage("baby", "Flammèche", 3, 3), PetStage("young", "Feu follet", 14, 8),
    PetStage("adult", "Brasier", 45, 15), PetStage("legend", "Phénix", 120, 25),
)

class PetAccessory(val key: String, val name: String, val emoji: String, val price: Int)

val PET_ACCESSORIES = listOf(
    PetAccessory("beret", "Béret", "🧢", 80), PetAccessory("glasses", "Lunettes de soleil", "🕶️", 100), PetAccessory("scarf", "Écharpe", "🧣", 100),
    PetAccessory("crown", "Couronne", "👑", 200), PetAccessory("headphones", "Casque audio", "🎧", 150), PetAccessory("bow", "Nœud papillon", "🎀", 80),
    PetAccessory("party", "Chapeau de fête", "🥳", 120), PetAccessory("cap", "Casquette de conducteur", "🚇", 150),
)

/**
 * État du compagnon : stade, prochain stade, meilleure série et niveau, avancement vers le prochain stade (0 à 1),
 * humeur (« proud », « happy », « calm », « tired »).
 */
class PetState(val stage: PetStage, val next: PetStage?, val best: Int, val level: Int, val progress: Double, val mood: String)

/** Stade atteint : série [best] OU niveau [level] suffisants (le plus avantageux des deux). */
fun petStageOf(best: Int, level: Int): PetStage = PET_STAGES.last { best >= it.streak || level >= it.level }

/** Avancement vers le stade suivant : la meilleure des deux progressions (série ou niveau). */
fun petProgress(best: Int, level: Int): Double {
    val st = petStageOf(best, level); val nx = PET_STAGES.getOrNull(PET_STAGES.indexOf(st) + 1) ?: return 1.0
    val a = (best - st.streak).toDouble() / maxOf(1, nx.streak - st.streak); val b = (level - st.level).toDouble() / maxOf(1, nx.level - st.level)
    return maxOf(a, b).coerceIn(0.0, 1.0)
}

/**
 * Humeur : fier (journée parfaite ou palier de série atteint aujourd'hui), fatigué (journal à « Difficile » ou « Bof »,
 * étiquette « fatigué », ou rien de fait tard le soir), content (flamme allumée ou bonne humeur au journal), calme sinon.
 * [journal] : humeur du jour (1 à 5) ou null ; [hour] : heure locale.
 */
fun petMood(lit: Boolean, perfect: Boolean, milestone: Boolean, journal: Int?, tiredTag: Boolean, hour: Int): String = when {
    perfect || milestone -> "proud"
    (journal != null && journal <= 2) || tiredTag -> "tired"
    lit || (journal ?: 0) >= 4 -> "happy"
    hour >= 21 -> "tired"
    else -> "calm"
}

/** État du compagnon. [journal] et [tiredTag] : humeur du jour notée au journal (gardé sur l'appareil). */
fun Pg.pet(id: String = meId(), journal: Int? = null, tiredTag: Boolean = false): PetState {
    val best = maxOf(bestStreak(id), globalStreak(id).n); val lv = level(points(id)).idx
    val st = petStageOf(best, lv); val next = PET_STAGES.getOrNull(PET_STAGES.indexOf(st) + 1)
    val streak = globalStreak(id)
    val perfect = userById(id)?.perfectDays?.contains(today()) == true
    val mood = petMood(streak.lit, perfect, streak.lit && streak.n in MILESTONES, journal, tiredTag, nowDateTime().hour)
    return PetState(st, next, best, lv, petProgress(best, lv), mood)
}

/** Ce qui manque pour le stade suivant : « 5 jours de série ou le niveau 8 ». */
fun petNextHint(s: PetState): String? = s.next?.let { n -> "${pl(maxOf(1, n.streak - s.best), "jour", "jours")} de série en plus ou le niveau ${n.level}" }

fun petPrice(key: String) = PET_ACCESSORIES.firstOrNull { "acc:${it.key}" == key }?.price

/**
 * Tenue du compagnon (une à la fois, par-dessus les accessoires) : [price] > 0 s'achète dans la boutique
 * (« outfit:<clé> ») ; 0 : offerte par le passe de la saison [season].
 */
class PetOutfit(val key: String, val name: String, val emoji: String, val price: Int, val season: String? = null)

val PET_OUTFITS = listOf(
    PetOutfit("sport", "Tenue de sport", "🏃", 150), PetOutfit("pyjama", "Pyjama étoilé", "🌙", 120), PetOutfit("hero", "Cape de super-héros", "🦸", 200),
    PetOutfit("pull", "Pull douillet", "🧶", 0, "givre"), PetOutfit("cape", "Cape d’aurore", "🌌", 0, "aurore"), PetOutfit("kimono", "Kimono fleuri", "🌸", 0, "cerisier"),
    PetOutfit("astro", "Combinaison d’astronaute", "🧑‍🚀", 0, "cosmos"), PetOutfit("hawaii", "Chemise hawaïenne", "🌺", 0, "lagon"),
    PetOutfit("marin", "Marinière", "⛵", 0, "loire"), PetOutfit("vigneron", "Tablier de vigneron", "🍇", 0, "anjou"), PetOutfit("cire", "Ciré jaune", "🧥", 0, "braise"),
)
fun petOutfit(key: String?) = PET_OUTFITS.firstOrNull { it.key == key }
