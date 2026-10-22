package fr.pepsgoal.core

/**
 * Langue de l'interface. En anglais, l'application traduit l'affichage (dictionnaire de l'app) ; le cœur, lui,
 * adapte les dates, les jours de la semaine et les pluriels de [pl].
 */
object Lang {
    @Volatile var en = false

    /** Pluriels de [pl] : mot français au singulier → (singulier, pluriel) anglais. */
    val plural: Map<String, Pair<String, String>> = mapOf(
        "ami en commun" to ("mutual friend" to "mutual friends"), "ami" to ("friend" to "friends"),
        "autre badge à découvrir" to ("other badge to discover" to "other badges to discover"), "clé de conversation" to ("conversation key" to "conversation keys"),
        "commentaire" to ("comment" to "comments"), "compte" to ("account" to "accounts"), "deadline bouclée" to ("deadline met" to "deadlines met"),
        "défi à plusieurs réussi" to ("group challenge won" to "group challenges won"), "encouragement reçu" to ("cheer received" to "cheers received"),
        "fichier" to ("file" to "files"), "fois" to ("time" to "times"), "gel de série utilisé" to ("streak freeze used" to "streak freezes used"),
        "gel de série" to ("streak freeze" to "streak freezes"), "gel" to ("freeze" to "freezes"), "high five" to ("high five" to "high fives"),
        "jour actif" to ("active day" to "active days"), "jour de série" to ("day streak" to "day streak"), "jour noté" to ("day logged" to "days logged"),
        "jour restant" to ("day left" to "days left"), "jour validé" to ("day completed" to "days completed"), "jour écoulé" to ("day elapsed" to "days elapsed"),
        "jour" to ("day" to "days"), "journée parfaite" to ("perfect day" to "perfect days"), "membre ajouté" to ("member added" to "members added"),
        "membre" to ("member" to "members"), "message" to ("message" to "messages"), "modification" to ("change" to "changes"),
        "mois réussi d’affilée" to ("month in a row" to "months in a row"), "mois" to ("month" to "months"),
        "objectif accompli" to ("goal achieved" to "goals achieved"), "objectif ajouté" to ("goal added" to "goals added"), "objectif créé" to ("goal created" to "goals created"),
        "objectif reporté à demain" to ("goal moved to tomorrow" to "goals moved to tomorrow"), "objectif" to ("goal" to "goals"),
        "participant" to ("participant" to "participants"), "processeur" to ("processor" to "processors"), "publication" to ("post" to "posts"),
        "période tenue" to ("period kept" to "periods kept"), "signalement" to ("report" to "reports"), "story publiée" to ("story posted" to "stories posted"),
        "sélectionné" to ("selected" to "selected"), "validation prévue" to ("check-in planned" to "check-ins planned"), "validation" to ("check-in" to "check-ins"),
        "votant" to ("voter" to "voters"), "écart" to ("slip" to "slips"), "étape de projet franchie" to ("project step completed" to "project steps completed"),
        "étape" to ("step" to "steps")
    )
}
