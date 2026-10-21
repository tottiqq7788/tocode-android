package app.tocode.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

const val RUNNING_SESSIONS_FILE = "running-sessions.json"
const val EXTRA_LINK_ID = "session_link_id"
const val EXTRA_PROFILE = "session_profile"
const val EXTRA_PROJECT_ID = "session_project_id"
const val EXTRA_PROJECT_NAME = "session_project_name"
const val EXTRA_SESSION_ID = "session_id"
const val EXTRA_SESSION_TITLE = "session_title"
const val EXTRA_TAB = "open_tab"
const val TAB_NEW_SESSION = "sessions_new"
const val RECENT_REPLIES_FILE = "recent-replies.json"
const val RECENT_REPLY_LIMIT = 3
const val RECENT_REPLY_TTL_MS = 24L * 60 * 60 * 1000
private const val MEDIUM_ROWS = 2
private const val LARGE_ROWS = 5

@Serializable
data class RunningSession(
    val linkId: String,
    val macName: String,
    val profile: String,
    val projectId: String,
    val projectName: String,
    val sessionId: String,
    val title: String,
    val startedAt: Long,
)

data class ObservedSession(val id: String, val title: String, val running: Boolean)

data class RunningChange(val sessions: List<RunningSession>, val finished: List<RunningSession>)

data class SessionOpen(
    val linkId: String,
    val profile: String,
    val projectId: String,
    val projectName: String,
    val sessionId: String,
    val title: String,
)

enum class SessionLaunchStep { Wait, Offline, SelectProfile, OpenProject, OpenSession }

data class SessionLaunchState(
    val linkExists: Boolean,
    val online: Boolean?,
    val profile: String,
    val projectId: String?,
)

@Serializable
data class RecentReply(
    val linkId: String,
    val macName: String,
    val profile: String,
    val projectId: String,
    val projectName: String,
    val sessionId: String,
    val title: String,
    val finishedAt: Long,
)

data class WidgetRunRow(val title: String, val place: String, val startedAt: Long, val open: SessionOpen)

data class WidgetReplyRow(val title: String, val place: String, val finishedAt: Long, val open: SessionOpen)

data class SmallSessionView(val running: Int, val run: WidgetRunRow?, val reply: WidgetReplyRow?)

data class MediumSessionView(val running: Int, val runs: List<WidgetRunRow>, val replies: List<WidgetReplyRow>)

data class LargeSessionGroup(val label: String, val rows: List<WidgetRunRow>)

data class LargeSessionView(
    val running: Int,
    val groups: List<LargeSessionGroup>,
    val replies: List<WidgetReplyRow>,
    val more: Int,
)

fun noteProject(
    current: List<RunningSession>,
    linkId: String,
    macName: String,
    profile: String,
    projectId: String,
    projectName: String,
    observed: List<ObservedSession>,
    now: Long,
    online: Boolean,
): RunningChange {
    if (!online || projectId.isBlank()) return RunningChange(current, emptyList())
    val outside = current.filterNot { it.sameProject(linkId, profile, projectId) }
    val scope = current.filter { it.sameProject(linkId, profile, projectId) }
    val previous = scope.associateBy { it.sessionId }
    val running = observed.filter { it.running && it.id.isNotBlank() }
    val nextScope = running.map { row ->
        val kept = previous[row.id]
        RunningSession(
            linkId = linkId,
            macName = macName.ifBlank { kept?.macName.orEmpty() },
            profile = profile.ifBlank { kept?.profile ?: "default" },
            projectId = projectId,
            projectName = projectName.ifBlank { kept?.projectName ?: "未命名项目" },
            sessionId = row.id,
            title = row.title.ifBlank { kept?.title ?: "未命名会话" },
            startedAt = kept?.startedAt ?: now,
        )
    }
    val still = running.map { it.id }.toSet()
    val finished = scope.filter { it.sessionId !in still }.map { previous ->
        val seen = observed.firstOrNull { it.id == previous.sessionId }
        if (seen == null || seen.title.isBlank()) previous else previous.copy(title = seen.title)
    }
    return RunningChange(outside + nextScope, finished)
}

fun noteSession(
    current: List<RunningSession>,
    linkId: String,
    macName: String,
    profile: String,
    projectId: String,
    projectName: String,
    sessionId: String,
    title: String,
    running: Boolean,
    now: Long,
    online: Boolean,
): RunningChange {
    if (sessionId.isBlank() || !online) return RunningChange(current, emptyList())
    val previous = current.firstOrNull { it.linkId == linkId && it.sessionId == sessionId }
    if (!running) {
        if (previous == null) return RunningChange(current, emptyList())
        val finished = if (title.isBlank()) previous else previous.copy(title = title)
        return RunningChange(current.filterNot { it.linkId == linkId && it.sessionId == sessionId }, listOf(finished))
    }
    val resolvedProject = projectId.ifBlank { previous?.projectId.orEmpty() }
    if (resolvedProject.isBlank()) return RunningChange(current, emptyList())
    val kept = current.filterNot { it.linkId == linkId && it.sessionId == sessionId }
    val row = RunningSession(
        linkId = linkId,
        macName = macName.ifBlank { previous?.macName.orEmpty() },
        profile = profile.ifBlank { previous?.profile ?: "default" },
        projectId = resolvedProject,
        projectName = projectName.ifBlank { previous?.projectName ?: "未命名项目" },
        sessionId = sessionId,
        title = title.ifBlank { previous?.title ?: "未命名会话" },
        startedAt = previous?.startedAt ?: now,
    )
    return RunningChange(kept + row, emptyList())
}

fun dropSession(current: List<RunningSession>, linkId: String, sessionId: String): RunningChange =
    RunningChange(current.filterNot { it.linkId == linkId && it.sessionId == sessionId }, emptyList())

fun sessionsToNotify(
    finished: List<RunningSession>,
    viewingLinkId: String?,
    viewingSessionId: String?,
    opening: SessionOpen? = null,
): List<RunningSession> = finished.filterNot { session ->
    val viewing = !viewingSessionId.isNullOrBlank() && session.linkId == viewingLinkId && session.sessionId == viewingSessionId
    val pending = opening != null && session.linkId == opening.linkId && session.sessionId == opening.sessionId
    viewing || pending
}

fun sessionOpenFrom(
    linkId: String?,
    profile: String?,
    projectId: String?,
    projectName: String?,
    sessionId: String?,
    title: String?,
): SessionOpen? {
    if (linkId.isNullOrBlank() || projectId.isNullOrBlank() || sessionId.isNullOrBlank()) return null
    return SessionOpen(
        linkId = linkId,
        profile = profile.orEmpty().ifBlank { "default" },
        projectId = projectId,
        projectName = projectName.orEmpty().ifBlank { "未命名项目" },
        sessionId = sessionId,
        title = title.orEmpty().ifBlank { "未命名会话" },
    )
}

fun widgetTab(raw: String?): String? = raw?.takeIf { it in setOf(TAB_NEW_SESSION, "sessions", "files", "buttons") }

fun sessionLaunchStep(open: SessionOpen, state: SessionLaunchState): SessionLaunchStep {
    if (!state.linkExists || state.online == false) return SessionLaunchStep.Offline
    if (state.online != true) return SessionLaunchStep.Wait
    if (state.profile != open.profile) return SessionLaunchStep.SelectProfile
    if (state.projectId != open.projectId) return SessionLaunchStep.OpenProject
    return SessionLaunchStep.OpenSession
}

fun smallSessionView(sessions: List<RunningSession>, replies: List<RecentReply> = emptyList(), now: Long = 0): SmallSessionView {
    val ordered = newestFirst(sessions)
    val first = ordered.firstOrNull()
    val reply = if (first == null) freshReplies(replies, now).firstOrNull()?.toRow() else null
    return SmallSessionView(ordered.size, first?.toRow(), reply)
}

fun mediumSessionView(sessions: List<RunningSession>, replies: List<RecentReply> = emptyList(), now: Long = 0): MediumSessionView {
    val ordered = newestFirst(sessions)
    val runs = ordered.take(MEDIUM_ROWS).map { it.toRow() }
    val fill = freshReplies(replies, now).take(MEDIUM_ROWS - runs.size).map { it.toRow() }
    return MediumSessionView(ordered.size, runs, fill)
}

fun largeSessionView(sessions: List<RunningSession>, replies: List<RecentReply> = emptyList(), now: Long = 0): LargeSessionView {
    val ordered = newestFirst(sessions)
    val shown = ordered.take(LARGE_ROWS)
    val groups = shown.groupBy { it.linkId to it.projectId }.values.map { rows ->
        LargeSessionGroup(
            label = placeLabel(rows.first().macName, rows.first().projectName),
            rows = rows.map { it.toRow() },
        )
    }
    return LargeSessionView(
        running = ordered.size,
        groups = groups,
        replies = freshReplies(replies, now).map { it.toRow() },
        more = (ordered.size - shown.size).coerceAtLeast(0),
    )
}

fun noteReplied(current: List<RecentReply>, notified: List<RunningSession>, now: Long): List<RecentReply> {
    if (notified.isEmpty()) return freshReplies(current, now)
    val added = notified.map { RecentReply(it.linkId, it.macName, it.profile, it.projectId, it.projectName, it.sessionId, it.title, now) }
    val keys = added.map { it.linkId to it.sessionId }.toSet()
    return freshReplies(added + current.filterNot { (it.linkId to it.sessionId) in keys }, now)
}

fun dropReplied(current: List<RecentReply>, linkId: String, sessionId: String): List<RecentReply> =
    current.filterNot { it.linkId == linkId && it.sessionId == sessionId }

fun dropRunningReplies(current: List<RecentReply>, running: List<RunningSession>): List<RecentReply> {
    val keys = running.map { it.linkId to it.sessionId }.toSet()
    return current.filterNot { (it.linkId to it.sessionId) in keys }
}

fun freshReplies(current: List<RecentReply>, now: Long): List<RecentReply> =
    current
        .filter { now - it.finishedAt in 0..RECENT_REPLY_TTL_MS }
        .sortedByDescending { it.finishedAt }
        .take(RECENT_REPLY_LIMIT)

fun repliedAgo(now: Long, finishedAt: Long): String {
    val minutes = ((now - finishedAt).coerceAtLeast(0) / 60_000L)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        else -> "${minutes / 60} 小时前"
    }
}

class RecentReplyStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<RecentReply> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<RecentReply>>(file.readText()) }.getOrDefault(emptyList())
    }

    fun save(replies: List<RecentReply>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(replies))
    }
}

private fun RunningSession.toRow(): WidgetRunRow = WidgetRunRow(title, projectName, startedAt, toOpen())

private fun RecentReply.toRow(): WidgetReplyRow =
    WidgetReplyRow(title, projectName, finishedAt, SessionOpen(linkId, profile, projectId, projectName, sessionId, title))

class RunningSessionStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<RunningSession> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<RunningSession>>(file.readText()) }.getOrDefault(emptyList())
    }

    fun save(sessions: List<RunningSession>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(sessions))
    }
}

private fun RunningSession.sameProject(linkId: String, profile: String, projectId: String): Boolean =
    this.linkId == linkId && this.profile == profile && this.projectId == projectId

private fun RunningSession.toOpen(): SessionOpen = SessionOpen(linkId, profile, projectId, projectName, sessionId, title)

private fun newestFirst(sessions: List<RunningSession>): List<RunningSession> =
    sessions.sortedWith(compareByDescending<RunningSession> { it.startedAt }.thenBy { it.sessionId })

private fun placeLabel(macName: String, projectName: String): String =
    listOf(macName, projectName).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "会话" }
