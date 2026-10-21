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

data class SmallSessionView(val title: String?, val more: Int, val open: SessionOpen?)

data class MediumSessionRow(val title: String, val detail: String, val open: SessionOpen)

data class MediumSessionView(val rows: List<MediumSessionRow>, val more: Int)

data class LargeSessionRow(val title: String, val open: SessionOpen)

data class LargeSessionGroup(val label: String, val rows: List<LargeSessionRow>)

data class LargeSessionView(val groups: List<LargeSessionGroup>, val more: Int)

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

fun sessionLaunchStep(open: SessionOpen, state: SessionLaunchState): SessionLaunchStep {
    if (!state.linkExists || state.online == false) return SessionLaunchStep.Offline
    if (state.online != true) return SessionLaunchStep.Wait
    if (state.profile != open.profile) return SessionLaunchStep.SelectProfile
    if (state.projectId != open.projectId) return SessionLaunchStep.OpenProject
    return SessionLaunchStep.OpenSession
}

fun smallSessionView(sessions: List<RunningSession>): SmallSessionView {
    val ordered = newestFirst(sessions)
    val first = ordered.firstOrNull() ?: return SmallSessionView(null, 0, null)
    return SmallSessionView(first.title, (ordered.size - 1).coerceAtLeast(0), first.toOpen())
}

fun mediumSessionView(sessions: List<RunningSession>): MediumSessionView {
    val ordered = newestFirst(sessions)
    val shown = ordered.take(3)
    val distinct = shown.map { it.macName to it.projectName }.distinct().size > 1
    return MediumSessionView(
        rows = shown.map { session ->
            MediumSessionRow(
                title = session.title,
                detail = if (distinct) placeLabel(session.macName, session.projectName) else "",
                open = session.toOpen(),
            )
        },
        more = (ordered.size - shown.size).coerceAtLeast(0),
    )
}

fun largeSessionView(sessions: List<RunningSession>): LargeSessionView {
    val ordered = newestFirst(sessions)
    val shown = ordered.take(8)
    val groups = shown.groupBy { it.linkId to it.projectId }.values.map { rows ->
        LargeSessionGroup(
            label = placeLabel(rows.first().macName, rows.first().projectName),
            rows = rows.map { LargeSessionRow(it.title, it.toOpen()) },
        )
    }
    return LargeSessionView(groups, (ordered.size - shown.size).coerceAtLeast(0))
}

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
