package fr.pepsgoal.core.model

import fr.pepsgoal.core.crypto.E2ePayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Base locale de l'appareil (équivalent de la base `DB` de la version web) : objectifs et validations du compte,
 * plus le miroir des données sociales lues depuis le serveur. Les objets sont mutables, comme dans l'application d'origine :
 * chaque modification est suivie d'un `Store.commit()` qui enregistre la base et prévient l'interface.
 */
@Serializable
class Db(
    var version: Int = 8,
    var session: String? = null,
    var users: MutableList<User> = mutableListOf(),
    var friendships: MutableList<Friendship> = mutableListOf(),
    var goals: MutableList<Goal> = mutableListOf(),
    var checkins: MutableList<Checkin> = mutableListOf(),
    var posts: MutableList<Post> = mutableListOf(),
    var notifs: MutableList<Notif> = mutableListOf(),
    var reminderLog: MutableMap<String, Int> = mutableMapOf(),
    var messages: MutableList<Message> = mutableListOf(),
    var chats: MutableList<Chat> = mutableListOf(),
    var blocks: MutableList<Block> = mutableListOf(),
    var appeals: MutableList<Appeal> = mutableListOf(),
    var reports: MutableList<Report> = mutableListOf(),
    var modlog: MutableList<ModLog> = mutableListOf(),
    var stories: MutableList<Story> = mutableListOf(),
    var news: MutableList<News> = mutableListOf(),
    var maintenance: Maintenance? = null,
    var maintLog: MutableList<Maintenance> = mutableListOf(),
    var timers: MutableMap<String, Timer> = mutableMapOf(),
    var perfectLog: MutableMap<String, Int> = mutableMapOf(),
    var cloud: CloudState? = null,
    var sandbox: Boolean = false
)

@Serializable
class CloudState(
    var cursor: Long = 0,
    var pulled: Boolean = false,
    /** Le social a déjà été chargé une fois : les nouveautés suivantes donnent lieu à des notifications. */
    var socialPulled: Boolean = false,
    var snapGoals: MutableMap<String, String> = mutableMapOf(),
    var snapCheckins: MutableMap<String, String> = mutableMapOf(),
    var snapSettings: String = ""
)

@Serializable
class Avatar(
    var kind: String = "initials",
    var color: Int = 0,
    var emoji: String = "😎",
    var picto: String = "flameo",
    var photo: String = ""
) {
    fun copy() = Avatar(kind, color, emoji, picto, photo)
}

@Serializable
class Reminder(var on: Boolean = true, var time: String = "08:30")

@Serializable
class Place(val name: String, val admin1: String = "", val country: String = "", val cc: String = "", val lat: Double, val lon: Double, val tz: String = "")

@Serializable
class Settings(
    var theme: String = "system",
    var accent: Int = 0,
    var accentCustom: List<String>? = null,
    var social: Boolean = true,
    var defaultVis: String = "friends",
    var morning: Reminder = Reminder(true, "08:30"),
    var evening: Reminder = Reminder(true, "20:30"),
    var browser: Boolean = false,
    var bg: String = "blob",
    var noSuggest: Boolean = false,
    var weather: Place? = null,
    var wxCard: Boolean = true,
    var glassBlur: String = "mid",
    /** Effet animé du thème (neige, pétales…), vide sans effet. */
    var fx: String = "",
    var hideOngoing: Boolean = false,
    /** Clés de réglages inconnues de cette version (écrites par une autre version) : conservées telles quelles. */
    var extraRaw: JsonObject? = null
)

@Serializable
class SavedPlace(val id: String, var name: String, var icon: String = "📍", var lat: Double, var lon: Double, var radius: Int = 150)

@Serializable
class Reward(val id: String, var title: String, var emoji: String = "🎁", var goalId: String? = null, var target: Int = 30, val createdAt: Long = 0, var reachedAt: Long? = null, var claimedAt: Long? = null)

@Serializable
class GoalGroup(val id: String, var name: String, var icon: String = "📁")

@Serializable
class Ban(val until: Long?, val reason: String = "", val at: Long = 0)

@Serializable
class Warn(val at: Long, val reason: String = "")

@Serializable
class User(
    val id: String,
    var username: String,
    var name: String,
    var hue: Int = 0,
    var bio: String = "",
    var role: String? = null,
    var guest: Boolean = false,
    var demo: Boolean = false,
    var createdAt: Long = 0,
    var settings: Settings = Settings(),
    var avatar: Avatar = Avatar(),
    var freezes: Int = 0,
    var frozenDays: MutableList<String> = mutableListOf(),
    /** Jours de repos planifiés : ils ne cassent pas la série (sans compter comme jour réussi). */
    var restDays: MutableList<String> = mutableListOf(),
    /** Périodes de vacances (« début|fin », dates incluses) : séries en pause, rappels coupés. */
    var vacations: MutableList<String> = mutableListOf(),
    /** Journées difficiles : seuls les objectifs essentiels comptent ces jours-là. */
    var hardDays: MutableList<String> = mutableListOf(),
    /** Objectifs essentiels (3 au plus) gardés les journées difficiles. */
    var essentials: MutableList<String> = mutableListOf(),
    /** Couleur de flamme choisie (débloquée par la meilleure série). */
    var flameSkin: String = "classic",
    /** Boutique : articles achetés (icônes, thèmes) et flammes dépensées (le niveau, lui, compte toutes les flammes gagnées). */
    var shopOwned: MutableList<String> = mutableListOf(),
    var shopSpent: Int = 0,
    /** Emplacements enregistrés (maison, travail…), pour les rappels par lieu. */
    var places: MutableList<SavedPlace> = mutableListOf(),
    /** Récompenses personnelles (« un resto à 30 jours de série »). */
    var rewards: MutableList<Reward> = mutableListOf(),
    /** Encouragements reçus qui comptent pour les gels (nombre fourni par le serveur). */
    var cheersIn: Int = 0,
    var tutorialDone: Boolean = false,
    var tutoBonus: Boolean = false,
    var goalGroups: MutableList<GoalGroup> = mutableListOf(),
    var goalGroupOf: MutableMap<String, String> = mutableMapOf(),
    var milestones: MutableList<String> = mutableListOf(),
    var badges: MutableList<String>? = null,
    var badgeFams: MutableList<String>? = null,
    var perfectDays: MutableList<String> = mutableListOf(),
    var favGoals: MutableList<String> = mutableListOf(),
    var hiddenSuggest: MutableList<String> = mutableListOf(),
    var aprilFool: String? = null,
    // Données lues depuis le serveur
    var srv: Boolean = false,
    var stub: Boolean = false,
    var days: List<String>? = null,
    var pts: Int? = null,
    var friendCount: Int? = null,
    var ban: Ban? = null,
    var warned: Warn? = null,
    var noComments: Boolean = false
)

@Serializable
class Friendship(val id: String, val from: String, val to: String, var status: String, val at: Long = 0)

@Serializable
class Goal(
    var id: String,
    var userId: String,
    var type: String,
    var title: String,
    var icon: String = "🎯",
    var note: String = "",
    var visibility: String = "friends",
    var createdAt: Long = 0,
    var archived: Boolean = false,
    var completedAt: Long? = null,
    var failedAt: Long? = null,
    var members: MutableList<String>? = null,
    var buddy: String? = null,
    var buddyVerify: Boolean = false,
    var buddyMiss: Boolean = false,
    /** Réglages propres au type (packages/shared GoalDataByType) et état (paliers, mois réussis…), synchronisés tels quels. */
    var data: MutableMap<String, JsonElement> = linkedMapOf()
)

@Serializable
class Checkin(
    var id: String,
    var goalId: String,
    var userId: String,
    var date: String,
    var kind: String,
    var value: Double = 1.0,
    var pts: Int = 0,
    var pending: Boolean = false,
    var ptsPending: Int? = null,
    var taskId: String? = null,
    var at: Long = 0
)

@Serializable
class Comment(val id: String, val userId: String, val text: String, val at: Long)

@Serializable
class PollOption(val id: String, val t: String, var n: Int = 0, var mine: Boolean = false)

@Serializable
class Poll(val multi: Boolean, val endsAt: Long, var voters: Int = 0, val options: MutableList<PollOption> = mutableListOf())

@Serializable
class Post(
    val id: String,
    val userId: String,
    val text: String,
    val visibility: String,
    var goalId: String? = null,
    val kind: String = "manual",
    val at: Long,
    var likes: MutableList<String> = mutableListOf(),
    var comments: MutableList<Comment> = mutableListOf(),
    var photo: String? = null,
    var poll: Poll? = null,
    var status: String? = null,
    var commentsLocked: Boolean = false,
    var mod: Boolean = false,
    /** Réaction de chaque personne qui a encouragé (identifiant → « fire », « muscle », « clap » ou « party »). */
    var reactions: MutableMap<String, String> = mutableMapOf()
)

@Serializable
class Story(
    val id: String,
    val userId: String,
    val kind: String,
    val data: JsonObject? = null,
    val photo: String? = null,
    val text: String = "",
    val at: Long,
    var seenBy: MutableList<String> = mutableListOf(),
    var views: Int? = null,
    var liked: Boolean = false,
    var likeCount: Int? = null,
    var likers: MutableList<String> = mutableListOf(),
    var viewsLoaded: Boolean = false
)

@Serializable
class Notif(
    val id: String,
    val userId: String,
    val type: String,
    val text: String,
    val at: Long,
    var read: Boolean = false,
    val srv: Boolean = false,
    val view: String? = null,
    val goalId: String? = null,
    val postId: String? = null,
    val checkinId: String? = null,
    val groupId: String? = null,
    val from: String? = null
)

@Serializable
class Message(
    val id: String,
    val from: String,
    val to: String? = null,
    val chat: String? = null,
    val at: Long,
    var read: Boolean = false,
    var readBy: MutableList<String> = mutableListOf(),
    val e2e: E2ePayload? = null,
    val text: String? = null,
    val official: Boolean = false,
    val tag: String? = null
)

@Serializable
class Chat(val id: String, var name: String, var emoji: String, var owner: String?, var members: MutableList<String>, val at: Long)

@Serializable
class Block(val by: String, val who: String, val at: Long = 0)

@Serializable
class Appeal(val id: String, val userId: String, val text: String, val at: Long, var status: String, var answer: String = "", var doneAt: Long? = null)

@Serializable
class Report(val id: String, val postId: String, val by: String, val reason: String, val at: Long, var closed: Boolean = false)

@Serializable
class ModLog(val id: String, val at: Long, val action: String, val target: String? = null, val detail: String = "")

@Serializable
class News(val id: String, val text: String, val at: Long)

@Serializable
class Maintenance(val id: String? = null, val mode: String, val start: Long, val until: Long? = null, val message: String = "", val endedAt: Long? = null)

@Serializable
class Timer(var goalId: String, val start: Long)
