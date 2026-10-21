package fr.pepsgoal.core

/** Valeurs fixes de l'application (identiques à la version web : apps/web-legacy/pepsgoal.html). */
const val APP_VERSION = "3.0.8"
const val FREEZE_EVERY = 10
const val TUTO_GELS = 5
const val MONTH_BONUS = 60
const val PERIOD_GRACE = 60
const val STORY_TTL = DAY_MS
const val STORY_MS = 6000L

class TypeInfo(val key: String, val label: String, val desc: String, val pts: Int, val icon: String)

val TYPES: Map<String, TypeInfo> = listOf(
    TypeInfo("objective", "Objectif", "Un cap chiffré à atteindre (12 livres, 100 km…)", 15, "target"),
    TypeInfo("deadline", "Deadline", "Une tâche à boucler avant une date", 40, "cal"),
    TypeInfo("routine", "Routine", "Certains jours, ou X fois par semaine", 10, "repeat"),
    TypeInfo("abstinence", "Abstinence", "Tenir sans une habitude, jour après jour", 8, "ban"),
    TypeInfo("monthly", "Mensuel", "Un score à atteindre chaque mois, remis à zéro le 1er", 15, "month"),
    TypeInfo("limit", "Limite", "Ne pas dépasser X par jour ou par semaine", 8, "gauge"),
    TypeInfo("duration", "Durée", "Du temps à cumuler, avec minuteur intégré", 12, "timer"),
    TypeInfo("measure", "Mesure", "Une valeur à améliorer, suivie en courbe", 10, "trend"),
    TypeInfo("timed", "Horaire", "Réussi seulement avant ou après une heure", 10, "clock"),
    TypeInfo("project", "Projet", "Une checklist d’étapes avec avancement", 10, "list"),
    TypeInfo("challenge", "Challenge", "X jours d’affilée, puis c’est terminé", 10, "trophy"),
    TypeInfo("program", "Paliers", "Une cible qui monte chaque semaine", 10, "stairs"),
    TypeInfo("group", "Défi de groupe", "Un total commun à atteindre ensemble", 10, "group"),
    TypeInfo("duel", "Duel", "Toi contre un ami, score en direct", 10, "duel"),
    TypeInfo("period", "Période", "Certains jours de la semaine ou du mois, sur une tranche horaire", 10, "period")
).associateBy { it.key }

fun typeInfo(t: String): TypeInfo = TYPES[t] ?: TYPES.getValue("routine")

val TYPE_GROUPS = listOf(
    "Essentiels" to listOf("routine", "period", "objective", "deadline", "abstinence", "monthly"),
    "Doser et mesurer" to listOf("limit", "duration", "measure", "timed"),
    "Formats" to listOf("project", "challenge", "program"),
    "À plusieurs" to listOf("group", "duel")
)
val SOCIAL_TYPES = setOf("group", "duel")
val CLOSABLE = setOf("deadline", "objective", "project", "challenge", "program", "group", "duel")

val LEVELS = listOf(0 to "Étincelle", 100 to "Braise", 300 to "Flamme", 700 to "Brasier", 1500 to "Incendie", 3000 to "Supernova")
val MILESTONES = listOf(7, 14, 21, 30, 50, 100, 200, 365)

class Accent(val name: String, val c: List<String>)

val ACCENTS = listOf(
    Accent("Braise", listOf("#FFB000", "#FF4A12", "#FF3D96")),
    Accent("Lagon", listOf("#6FF7FF", "#22D3FF", "#4D9BFF")),
    Accent("Citron néon", listOf("#F4FF5C", "#B6FF2E", "#3DFF8A")),
    Accent("Fuchsia", listOf("#FFA6D9", "#FF4FA3", "#E85BFF")),
    Accent("Lavande", listOf("#DCCBFF", "#A98BFF", "#FF7AD9")),
    Accent("Menthe", listOf("#A8FFE4", "#2EF2B0", "#22D3FF")),
    Accent("Soleil", listOf("#FFE45C", "#FFB000", "#FF7A3D")),
    Accent("Corail", listOf("#FFC2A8", "#FF7B6B", "#FF4F8B")),
    Accent("Océan", listOf("#8AF0FF", "#3DB2FF", "#8C7DFF")),
    Accent("Cerise", listOf("#FF9A9A", "#FF3B5C", "#FF5ACD")),
    Accent("Aurore", listOf("#FFD1A1", "#FF8FB1", "#B98CFF")),
    Accent("Glacier", listOf("#E4F4FF", "#9FD8FF", "#B6B4FF")),
    Accent("Pêche", listOf("#FFD9B8", "#FFA270", "#FF6F91")),
    Accent("Pistache", listOf("#E6FFB3", "#A8E063", "#56C596")),
    Accent("Turquoise", listOf("#B8FFF5", "#3DE0D0", "#1FA2FF")),
    Accent("Myrtille", listOf("#D2CCFF", "#8C7DFF", "#5B8CFF")),
    Accent("Framboise", listOf("#FFB3C7", "#FF5C8A", "#D25DD0")),
    Accent("Ambre", listOf("#FFE29A", "#FFC53D", "#FF8C42")),
    Accent("Jade", listOf("#B5F5D8", "#3DD6A0", "#2BB3C8")),
    Accent("Lilas", listOf("#F1D4FF", "#D59BFF", "#9B8CFF")),
    Accent("Sakura", listOf("#FFE0EC", "#FFA8C8", "#FF7EB3")),
    Accent("Mangue", listOf("#FFF0A8", "#FFD23F", "#FF9F1C")),
    Accent("Azur", listOf("#BDE8FF", "#5AC8FA", "#6D8BFF")),
    Accent("Argile", listOf("#FFD8C2", "#E8A87C", "#D9776B"))
)
val ACC_SUN = ACCENTS.indexOfFirst { it.name == "Soleil" }
val ACC_ICE = ACCENTS.indexOfFirst { it.name == "Glacier" }

val EMOJIS = listOf(
    "🏃", "🚴", "🏊", "🧘", "💪", "🏋️", "⚽", "🎾", "🏀", "🥾", "🧗", "🤸", "🚶", "⛷️",
    "🥗", "🍎", "🥦", "💧", "☕", "🍳", "🚭", "🍷", "🍬", "🍔", "🛌", "🦷", "💊", "🧴",
    "📚", "📖", "✍️", "🎓", "🧠", "🗣️", "💻", "🎸", "🎹", "🎨", "📷", "🎬", "🧩", "♟️",
    "💰", "💶", "📈", "🏠", "🧹", "🧺", "🪴", "🌱", "♻️", "🐶", "🐱", "❤️", "🤝", "📞",
    "🎯", "🏆", "⏰", "📵", "📅", "✈️", "🧳", "🙏", "😴", "🧊", "🌞", "🌙"
)
val PICTOS = listOf("flameo", "star", "bolt", "mountain", "leaf", "heart", "moon", "sun", "rocket", "dumbbell", "book", "music", "compass", "target")
val GROUP_ICONS = listOf("📁", "🏃", "🧘", "💼", "💻", "📚", "💰", "🏠", "❤️", "🎨", "🌱", "🎯", "🍎", "🎵", "✈️", "🧠", "💪", "⭐")
val GROUP_EMOJIS = listOf("💬", "🔥", "🏃", "💪", "📚", "🎯", "🧘", "🍎", "🎉", "❤️", "🌱", "⚽")
val QUICK_REACTS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "👏")
val MORE_REACTS = listOf("🙏", "💪", "🎉", "😍", "🤣", "😅", "🥳", "😎", "🤔", "👀", "💯", "✅", "⭐", "🙌", "😴", "🤯", "🥲", "😡")

val VIS = linkedMapOf("private" to ("Privé" to "lock"), "friends" to ("Amis" to "users"), "public" to ("Public" to "globe"))
val REASON_LBL = mapOf("spam" to "Spam", "hate" to "Propos haineux", "harass" to "Harcèlement", "other" to "Autre")
val MAINT_MODE = mapOf("social" to "Plateforme sociale en lecture seule", "full" to "Application complète indisponible")
val OFFICIAL_TAGS = mapOf("news" to ("Nouveauté" to "star"), "mod" to ("Modération" to "shield"), "info" to ("Info" to "bell"))
val BGS = listOf("blob" to "Blob", "aurora" to "Aurore", "waves" to "Vagues", "stars" to "Constellation", "mesh" to "Dégradé", "grid" to "Grille", "plain" to "Uni", "custom" to "Mon image")
val THEMES = listOf("system" to "Auto", "light" to "Clair", "dark" to "Sombre", "oled" to "OLED", "weather" to "Météo")
val BLURS = listOf("xlow" to "Très faible", "low" to "Faible", "mid" to "Moyen", "high" to "Fort")

/** Dégradés des récaps et des stories. */
val SBG = listOf(
    listOf("#FFB000", "#FF4A12", "#FF3D96"), listOf("#B6FF2E", "#2EF2B0", "#22D3FF"), listOf("#DCCBFF", "#A98BFF", "#FF7AD9"),
    listOf("#FFE45C", "#FFB000", "#FF7A3D"), listOf("#8AF0FF", "#22D3FF", "#8C7DFF"), listOf("#FFC2A8", "#FF4FA3", "#FFB000"),
    listOf("#FFD1A1", "#FF8FB1", "#B98CFF"), listOf("#A8FFE4", "#5AC8FA", "#6D8BFF"), listOf("#F4FF5C", "#FFD23F", "#FF8C42"),
    listOf("#FFE0EC", "#FFA8C8", "#C9C2FF")
)

val NEWS_TPL = listOf(
    Triple("news", "Nouvelle fonctionnalité", "Nouveau sur PepsGoal : "),
    Triple("info", "Rappel des règles", "Petit rappel : sur PepsGoal, on s’encourage avec bienveillance. Les publications insultantes ou promotionnelles sont supprimées par la modération."),
    Triple("info", "Défi de la semaine", "Défi de la semaine : valide au moins une action chaque jour pendant 7 jours et partage ta série dans le Feed !"),
    Triple("mod", "Maintenance prévue", "Une maintenance est prévue prochainement. Tes objectifs et ta série seront protégés pendant l’opération.")
)

const val WELCOME_OFFICIAL = "Bienvenue sur PepsGoal ! Ce compte officiel t'envoie les nouveautés de l'app et les messages de la modération. Il ne lit pas les réponses : pour contester une décision, utilise la demande de débannissement dans le Feed."

object Links {
    const val SITE = "https://site.pepsgoal.fr"
    const val REPO = "https://github.com/CorbetQ/pepsgoal-platform"
    const val ANDROID_REPO = "https://github.com/CorbetQ/pepsgoal-android"
    const val ISSUE = "https://github.com/CorbetQ/pepsgoal-platform/issues/new"
    const val MAIL = "quentin-corbet@outlook.fr"
}
