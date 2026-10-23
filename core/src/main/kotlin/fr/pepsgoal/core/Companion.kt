package fr.pepsgoal.core

/**
 * Compagnon flamme : une petite créature qui grandit avec la régularité (jours actifs au total) et dont l'humeur suit
 * la journée. Des accessoires s'achètent dans la boutique (« acc:<clé> »).
 */
class PetStage(val key: String, val name: String, val min: Int)

val PET_STAGES = listOf(
    PetStage("egg", "Œuf de braise", 0), PetStage("baby", "Flammèche", 7), PetStage("young", "Feu follet", 30),
    PetStage("adult", "Brasier", 100), PetStage("legend", "Phénix", 365),
)

class PetAccessory(val key: String, val name: String, val emoji: String, val price: Int)

val PET_ACCESSORIES = listOf(
    PetAccessory("beret", "Béret", "🧢", 80), PetAccessory("glasses", "Lunettes de soleil", "🕶️", 100), PetAccessory("scarf", "Écharpe", "🧣", 100),
    PetAccessory("crown", "Couronne", "👑", 200), PetAccessory("headphones", "Casque audio", "🎧", 150), PetAccessory("bow", "Nœud papillon", "🎀", 80),
    PetAccessory("party", "Chapeau de fête", "🥳", 120), PetAccessory("cap", "Casquette de conducteur", "🚇", 150),
)

class PetState(val stage: PetStage, val next: PetStage?, val days: Int, val mood: String)

/** État du compagnon : stade (jours actifs), prochain stade, humeur (« happy », « calm », « sleepy »). */
fun Pg.pet(id: String = meId()): PetState {
    val days = activeDays(id).size
    val st = PET_STAGES.last { days >= it.min }; val next = PET_STAGES.firstOrNull { it.min > days }
    val lit = activeDays(id).contains(today()); val streak = globalStreak(id).n
    val mood = when { lit -> "happy"; streak > 0 -> "calm"; else -> "sleepy" }
    return PetState(st, next, days, mood)
}

fun petPrice(key: String) = PET_ACCESSORIES.firstOrNull { "acc:${it.key}" == key }?.price
