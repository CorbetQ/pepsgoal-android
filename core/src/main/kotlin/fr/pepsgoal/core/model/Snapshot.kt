package fr.pepsgoal.core.model

import kotlinx.serialization.json.*

/**
 * Detached value snapshot, taken on the owner (Main) thread before background serialization.
 * Never pass live mutable model objects to the writer. Keep every constructor field here
 * when changing Model.kt; SnapshotTest checks the complete serializable shape and alias isolation.
 */
fun Db.snapshot(): Db = Db(
    version = version,
    session = session,
    users = users.map { it.snapshot() }.toMutableList(),
    friendships = friendships.map { it.snapshot() }.toMutableList(),
    goals = goals.map { it.snapshot() }.toMutableList(),
    checkins = checkins.map { it.snapshot() }.toMutableList(),
    posts = posts.map { it.snapshot() }.toMutableList(),
    notifs = notifs.map { it.snapshot() }.toMutableList(),
    reminderLog = reminderLog.toMutableMap(),
    messages = messages.map { it.snapshot() }.toMutableList(),
    chats = chats.map { it.snapshot() }.toMutableList(),
    blocks = blocks.map { it.snapshot() }.toMutableList(),
    appeals = appeals.map { it.snapshot() }.toMutableList(),
    reports = reports.map { it.snapshot() }.toMutableList(),
    modlog = modlog.map { it.snapshot() }.toMutableList(),
    stories = stories.map { it.snapshot() }.toMutableList(),
    news = news.map { it.snapshot() }.toMutableList(),
    maintenance = maintenance?.snapshot(),
    maintLog = maintLog.map { it.snapshot() }.toMutableList(),
    timers = timers.mapValues { it.value.snapshot() }.toMutableMap(),
    perfectLog = perfectLog.toMutableMap(),
    cloud = cloud?.snapshot(),
    sandbox = sandbox,
)

private fun CloudState.snapshot(): CloudState = CloudState(
    cursor = cursor,
    pulled = pulled,
    socialPulled = socialPulled,
    snapGoals = snapGoals.toMutableMap(),
    snapCheckins = snapCheckins.toMutableMap(),
    snapSettings = snapSettings,
    pollDigests = pollDigests.toList(),
    convSigs = convSigs.toMutableMap(),
    socialStale = socialStale,
)

private fun Avatar.snapshot(): Avatar = Avatar(
    kind = kind,
    color = color,
    emoji = emoji,
    picto = picto,
    photo = photo,
)

private fun Reminder.snapshot(): Reminder = Reminder(
    on = on,
    time = time,
)

private fun Place.snapshot(): Place = Place(
    name = name,
    admin1 = admin1,
    country = country,
    cc = cc,
    lat = lat,
    lon = lon,
    tz = tz,
)

private fun Settings.snapshot(): Settings = Settings(
    theme = theme,
    accent = accent,
    accentCustom = accentCustom?.toList(),
    social = social,
    defaultVis = defaultVis,
    morning = morning.snapshot(),
    evening = evening.snapshot(),
    browser = browser,
    bg = bg,
    noSuggest = noSuggest,
    weather = weather?.snapshot(),
    wxCard = wxCard,
    glassBlur = glassBlur,
    fx = fx,
    hideOngoing = hideOngoing,
    extraRaw = extraRaw?.frozen(),
)

private fun SavedPlace.snapshot(): SavedPlace = SavedPlace(
    id = id,
    name = name,
    icon = icon,
    lat = lat,
    lon = lon,
    radius = radius,
)

private fun Reward.snapshot(): Reward = Reward(
    id = id,
    title = title,
    emoji = emoji,
    goalId = goalId,
    target = target,
    createdAt = createdAt,
    reachedAt = reachedAt,
    claimedAt = claimedAt,
)

private fun Countdown.snapshot(): Countdown = Countdown(
    id = id,
    title = title,
    emoji = emoji,
    date = date,
)

private fun MyQuote.snapshot(): MyQuote = MyQuote(
    id = id,
    text = text,
    author = author,
)

private fun GoalGroup.snapshot(): GoalGroup = GoalGroup(
    id = id,
    name = name,
    icon = icon,
    color = color,
)

private fun Ban.snapshot(): Ban = Ban(
    until = until,
    reason = reason,
    at = at,
)

private fun Warn.snapshot(): Warn = Warn(
    at = at,
    reason = reason,
)

private fun User.snapshot(): User = User(
    id = id,
    username = username,
    name = name,
    hue = hue,
    bio = bio,
    role = role,
    guest = guest,
    demo = demo,
    createdAt = createdAt,
    settings = settings.snapshot(),
    avatar = avatar.snapshot(),
    freezes = freezes,
    frozenDays = frozenDays.toList().toMutableList(),
    restDays = restDays.toList().toMutableList(),
    vacations = vacations.toList().toMutableList(),
    hardDays = hardDays.toList().toMutableList(),
    essentials = essentials.toList().toMutableList(),
    flameSkin = flameSkin,
    shopOwned = shopOwned.toList().toMutableList(),
    shopSpent = shopSpent,
    shopBonus = shopBonus,
    shopClaimed = shopClaimed.toList().toMutableList(),
    places = places.map { it.snapshot() }.toMutableList(),
    rewards = rewards.map { it.snapshot() }.toMutableList(),
    cheersIn = cheersIn,
    tutorialDone = tutorialDone,
    tutoBonus = tutoBonus,
    goalGroups = goalGroups.map { it.snapshot() }.toMutableList(),
    goalGroupOf = goalGroupOf.toMutableMap(),
    nicknames = nicknames.toMutableMap(),
    friendNotes = friendNotes.toMutableMap(),
    friendBdays = friendBdays.toMutableMap(),
    countdowns = countdowns.map { it.snapshot() }.toMutableList(),
    myQuotes = myQuotes.map { it.snapshot() }.toMutableList(),
    monthFocus = monthFocus.toMutableMap(),
    realName = realName,
    milestones = milestones.toList().toMutableList(),
    badges = badges?.toList()?.toMutableList(),
    badgeFams = badgeFams?.toList()?.toMutableList(),
    perfectDays = perfectDays.toList().toMutableList(),
    favGoals = favGoals.toList().toMutableList(),
    hiddenSuggest = hiddenSuggest.toList().toMutableList(),
    aprilFool = aprilFool,
    srv = srv,
    stub = stub,
    days = days?.toList(),
    pts = pts,
    friendCount = friendCount,
    ban = ban?.snapshot(),
    warned = warned?.snapshot(),
    noComments = noComments,
)

private fun Friendship.snapshot(): Friendship = Friendship(
    id = id,
    from = from,
    to = to,
    status = status,
    at = at,
)

private fun Goal.snapshot(): Goal = Goal(
    id = id,
    userId = userId,
    type = type,
    title = title,
    icon = icon,
    note = note,
    visibility = visibility,
    createdAt = createdAt,
    archived = archived,
    completedAt = completedAt,
    failedAt = failedAt,
    members = members?.toList()?.toMutableList(),
    buddy = buddy,
    buddyVerify = buddyVerify,
    buddyMiss = buddyMiss,
    data = data.mapValues { it.value.frozen() }.toMutableMap(),
)

private fun Checkin.snapshot(): Checkin = Checkin(
    id = id,
    goalId = goalId,
    userId = userId,
    date = date,
    kind = kind,
    value = value,
    pts = pts,
    pending = pending,
    ptsPending = ptsPending,
    taskId = taskId,
    at = at,
)

private fun Comment.snapshot(): Comment = Comment(
    id = id,
    userId = userId,
    text = text,
    at = at,
)

private fun PollOption.snapshot(): PollOption = PollOption(
    id = id,
    t = t,
    n = n,
    mine = mine,
)

private fun Poll.snapshot(): Poll = Poll(
    multi = multi,
    endsAt = endsAt,
    voters = voters,
    options = options.map { it.snapshot() }.toMutableList(),
)

private fun Post.snapshot(): Post = Post(
    id = id,
    userId = userId,
    text = text,
    visibility = visibility,
    goalId = goalId,
    kind = kind,
    at = at,
    likes = likes.toList().toMutableList(),
    comments = comments.map { it.snapshot() }.toMutableList(),
    photo = photo,
    poll = poll?.snapshot(),
    status = status,
    commentsLocked = commentsLocked,
    mod = mod,
    reactions = reactions.toMutableMap(),
)

private fun Story.snapshot(): Story = Story(
    id = id,
    userId = userId,
    kind = kind,
    data = data?.frozen(),
    photo = photo,
    text = text,
    at = at,
    seenBy = seenBy.toList().toMutableList(),
    views = views,
    liked = liked,
    likeCount = likeCount,
    likers = likers.toList().toMutableList(),
    viewsLoaded = viewsLoaded,
)

private fun Notif.snapshot(): Notif = Notif(
    id = id,
    userId = userId,
    type = type,
    text = text,
    at = at,
    read = read,
    srv = srv,
    view = view,
    goalId = goalId,
    postId = postId,
    checkinId = checkinId,
    groupId = groupId,
    from = from,
)

private fun Message.snapshot(): Message = Message(
    id = id,
    from = from,
    to = to,
    chat = chat,
    at = at,
    read = read,
    readBy = readBy.toList().toMutableList(),
    e2e = e2e,
    text = text,
    official = official,
    tag = tag,
)

private fun Chat.snapshot(): Chat = Chat(
    id = id,
    name = name,
    emoji = emoji,
    owner = owner,
    members = members.toList().toMutableList(),
    at = at,
)

private fun Block.snapshot(): Block = Block(
    by = by,
    who = who,
    at = at,
)

private fun Appeal.snapshot(): Appeal = Appeal(
    id = id,
    userId = userId,
    text = text,
    at = at,
    status = status,
    answer = answer,
    doneAt = doneAt,
)

private fun Report.snapshot(): Report = Report(
    id = id,
    postId = postId,
    by = by,
    reason = reason,
    at = at,
    closed = closed,
)

private fun ModLog.snapshot(): ModLog = ModLog(
    id = id,
    at = at,
    action = action,
    target = target,
    detail = detail,
)

private fun News.snapshot(): News = News(
    id = id,
    text = text,
    at = at,
)

private fun Maintenance.snapshot(): Maintenance = Maintenance(
    id = id,
    mode = mode,
    start = start,
    until = until,
    message = message,
    endedAt = endedAt,
)

private fun Timer.snapshot(): Timer = Timer(
    goalId = goalId,
    start = start,
)

private fun JsonObject.frozen(): JsonObject = JsonObject(mapValues { it.value.frozen() })
private fun JsonElement.frozen(): JsonElement = when (this) {
    is JsonObject -> frozen()
    is JsonArray -> JsonArray(map { it.frozen() })
    else -> this
}
