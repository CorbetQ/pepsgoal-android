package fr.pepsgoal.core

import fr.pepsgoal.core.model.*

/* ---------------------------------------------------------------- Objectifs « si… alors… » (plans d'implémentation) */

/**
 * Déclencheur facultatif d'un objectif : « time » (après une heure), « arrive » / « leave » (en arrivant à un lieu
 * enregistré, en le quittant), « after » (après avoir validé un autre objectif). null : pas de plan « si… alors… ».
 */
var Goal.ifKind: String? get() = strOrNull("ifKind")?.ifEmpty { null }?.takeIf { it in IF_KEYS }; set(v) = set("ifKind", v ?: "")
/** Heure du déclencheur « time » (« HH:MM »). */
var Goal.ifTime: String get() = str("ifTime", "07:30"); set(v) = set("ifTime", v)
/** Emplacement enregistré (SavedPlace.id) des déclencheurs « arrive » et « leave ». */
var Goal.ifPlace: String? get() = strOrNull("ifPlace")?.ifEmpty { null }; set(v) = set("ifPlace", v ?: "")
/** Objectif à valider avant (déclencheur « after »). */
var Goal.ifGoal: String? get() = strOrNull("ifGoal")?.ifEmpty { null }; set(v) = set("ifGoal", v ?: "")
/** Seconde moitié de la phrase : « je fais 10 minutes d'étirements ». */
var Goal.ifThen: String get() = str("ifThen"); set(v) = set("ifThen", v)

/** Déclencheurs proposés dans le formulaire : clé, libellé, emoji. */
val IF_KINDS = listOf(
    Triple("time", "Après une heure", "⏰"), Triple("arrive", "En arrivant", "📍"),
    Triple("leave", "En partant", "🚪"), Triple("after", "Après un objectif", "🔗"),
)
private val IF_KEYS = IF_KINDS.map { it.first }.toSet()
/** Types qui acceptent un plan « si… alors… » (les objectifs à plusieurs et les échéances ont leur propre rythme). */
val IF_TYPES = setOf("routine", "abstinence", "limit", "duration", "cap", "measure", "timed", "period", "challenge", "objective", "monthly")

/** « alors je fais… » : la phrase saisie, sans le « alors » répété ni la ponctuation finale. */
fun ifThenTail(then: String, title: String): String {
    val t = then.trim().replace(Regex("^(alors|then)\\s*[,:…]*\\s*", RegexOption.IGNORE_CASE), "").trimEnd('.', '!', ' ', '…')
    return t.ifEmpty { "je fais « ${title.trim()} »" }
}

/**
 * Phrase complète « Si…, alors… » à partir du déclencheur et de ses valeurs (nom du lieu, intitulé de l'objectif
 * précédent). null si le déclencheur est incomplet (lieu ou objectif supprimé).
 */
fun ifThenPhrase(kind: String?, time: String, place: String?, prev: String?, then: String, title: String): String? {
    val tail = ifThenTail(then, title)
    val head = when (kind) {
        "time" -> "S’il est $time"
        "arrive" -> place?.let { "Si j’arrive à $it" }
        "leave" -> place?.let { "Si je pars de $it" }
        "after" -> prev?.let { "Si j’ai fini « $it »" }
        else -> null
    } ?: return null
    return "$head, alors $tail."
}

/** Phrase « si… alors… » de l'objectif, ou null sans déclencheur. */
fun Pg.ifThenOf(g: Goal): String? {
    val k = g.ifKind ?: return null
    val place = g.ifPlace?.let { id -> me()?.places?.firstOrNull { it.id == id }?.name }
    return ifThenPhrase(k, g.ifTime, place, goalById(g.ifGoal)?.title, g.ifThen, g.title)
}

/**
 * Reporte le déclencheur sur les mécanismes de rappel existants : l'heure devient le rappel de l'objectif, l'objectif
 * précédent devient son enchaînement. [old] : déclencheur avant modification (ce qu'il avait posé est retiré).
 * Les lieux, eux, sont surveillés par l'application (géorepérage).
 */
fun syncIfThen(g: Goal, old: Goal?) {
    val was = old?.ifKind
    if (was == "time" && g.remindAt == old.ifTime && g.ifKind != "time") g.remindAt = null
    if (was == "after" && g.after == old.ifGoal && g.ifKind != "after") g.after = null
    when (g.ifKind) {
        "time" -> { g.remindAt = g.ifTime; g.smartRemind = false }
        "after" -> g.after = g.ifGoal
        else -> {}
    }
}

/** Texte du rappel « si… alors… » : la phrase complète si le déclencheur est [kind], sinon [fallback] (objectif discret : jamais la phrase). */
fun Pg.ifThenReminder(g: Goal, kind: String, fallback: String): String =
    if (g.discreet) fallback else if (g.ifKind == kind || (kind == "place" && g.ifKind in setOf("arrive", "leave"))) ifThenOf(g) ?: fallback else fallback
