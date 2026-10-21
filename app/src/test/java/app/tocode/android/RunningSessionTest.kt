package app.tocode.android

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RunningSessionTest {
    @Test
    fun runningSessionIsIndexedAndFinishedSessionLeaves() {
        val started = noteProject(emptyList(), "mac", "Mac A", "default", "p1", "项目", listOf(observed("s1", "标题", true)), 10, online = true)
        assertEquals(listOf("s1"), started.sessions.map { it.sessionId })
        assertEquals(10L, started.sessions.single().startedAt)
        assertTrue(started.finished.isEmpty())

        val still = noteProject(started.sessions, "mac", "Mac A", "default", "p1", "项目", listOf(observed("s1", "新标题", true)), 20, online = true)
        assertEquals(10L, still.sessions.single().startedAt)
        assertEquals("新标题", still.sessions.single().title)
        assertTrue(still.finished.isEmpty())

        val done = noteProject(still.sessions, "mac", "Mac A", "default", "p1", "项目", listOf(observed("s1", "新标题", false)), 30, online = true)
        assertTrue(done.sessions.isEmpty())
        assertEquals("s1", done.finished.single().sessionId)
    }

    @Test
    fun unknownSessionAndOfflineDoNotNotify() {
        val missed = noteSession(emptyList(), "mac", "Mac A", "default", "p1", "项目", "s1", "标题", running = false, now = 1, online = true)
        assertTrue(missed.finished.isEmpty())

        val indexed = noteSession(emptyList(), "mac", "Mac A", "default", "p1", "项目", "s1", "标题", running = true, now = 1, online = true)
        val offline = noteProject(indexed.sessions, "mac", "Mac A", "default", "p1", "项目", emptyList(), 2, online = false)
        assertEquals(indexed.sessions, offline.sessions)
        assertTrue(offline.finished.isEmpty())

        val dropped = noteSession(indexed.sessions, "mac", "Mac A", "default", "p1", "项目", "s1", "标题", running = false, now = 2, online = false)
        assertEquals(indexed.sessions, dropped.sessions)
        assertTrue(dropped.finished.isEmpty())
    }

    @Test
    fun otherProjectStaysUntilThatProjectRefreshes() {
        val first = session("mac", "p1", "s1", 1)
        val other = session("mac", "p2", "s2", 2)
        val change = noteProject(listOf(first, other), "mac", "Mac A", "default", "p1", "项目", emptyList(), 3, online = true)
        assertEquals(listOf("s2"), change.sessions.map { it.sessionId })
        assertEquals(listOf("s1"), change.finished.map { it.sessionId })
    }

    @Test
    fun viewingOrOpeningSessionIsNotNotified() {
        val finished = listOf(session("mac", "p1", "s1", 1), session("mac", "p1", "s2", 2))
        val hidden = sessionsToNotify(finished, viewingLinkId = "mac", viewingSessionId = "s1")
        assertEquals(listOf("s2"), hidden.map { it.sessionId })
        val opening = sessionsToNotify(
            finished,
            viewingLinkId = null,
            viewingSessionId = null,
            opening = SessionOpen("mac", "default", "p1", "项目", "s2", "二"),
        )
        assertEquals(listOf("s1"), opening.map { it.sessionId })
    }

    @Test
    fun projectMustBeReadyBeforeOpeningSession() {
        val open = SessionOpen("mac", "work", "p1", "项目", "s1", "标题")
        assertEquals(SessionLaunchStep.Wait, sessionLaunchStep(open, SessionLaunchState(true, null, "work", "p1")))
        assertEquals(SessionLaunchStep.Offline, sessionLaunchStep(open, SessionLaunchState(true, false, "work", "p1")))
        assertEquals(SessionLaunchStep.SelectProfile, sessionLaunchStep(open, SessionLaunchState(true, true, "default", null)))
        assertEquals(SessionLaunchStep.OpenProject, sessionLaunchStep(open, SessionLaunchState(true, true, "work", null)))
        assertEquals(SessionLaunchStep.OpenProject, sessionLaunchStep(open, SessionLaunchState(true, true, "work", "other")))
        assertEquals(SessionLaunchStep.OpenSession, sessionLaunchStep(open, SessionLaunchState(true, true, "work", "p1")))
        assertFalse(sessionLaunchStep(open, SessionLaunchState(true, true, "work", null)) == SessionLaunchStep.OpenSession)
    }

    @Test
    fun widgetsSplitTheSameIndex() {
        val sessions = listOf(
            session("mac-a", "p1", "old", 1, macName = "Mac A", projectName = "甲", title = "旧"),
            session("mac-a", "p1", "new", 3, macName = "Mac A", projectName = "甲", title = "新"),
            session("mac-b", "p2", "other", 2, macName = "Mac B", projectName = "乙", title = "另一台"),
        )
        val small = smallSessionView(sessions)
        assertEquals("新", small.title)
        assertEquals(2, small.more)
        assertEquals("new", small.open?.sessionId)

        val medium = mediumSessionView(sessions)
        assertEquals(listOf("新", "另一台", "旧"), medium.rows.map { it.title })
        assertEquals("Mac A · 甲", medium.rows.first().detail)
        assertEquals(0, medium.more)
        assertEquals("", mediumSessionView(sessions.take(2).filter { it.sessionId != "other" }).rows.first().detail)

        val extra = (4..10).map { session("mac-a", "p1", "s$it", it.toLong(), macName = "Mac A", projectName = "甲", title = "第$it") }
        val large = largeSessionView(sessions + extra)
        assertEquals(8, large.groups.sumOf { it.rows.size })
        assertEquals(2, large.more)
        assertEquals("Mac A · 甲", large.groups.first().label)
    }

    @Test
    fun indexFileKeepsIdentityAndDropsBodies() {
        val root = File.createTempFile("running-sessions", "").apply {
            delete()
            mkdirs()
        }
        val store = RunningSessionStore(File(root, RUNNING_SESSIONS_FILE))
        val row = session("mac", "p1", "s1", 5)
        store.save(listOf(row))
        assertEquals(listOf(row), store.load())
        val raw = Json.encodeToString(row)
        assertFalse(raw.contains("token"))
        assertFalse(raw.contains("\"text\""))
        assertFalse(raw.contains("\"path\""))
        assertNull(sessionOpenFrom(null, "default", "p1", "项目", "s1", "标题"))
    }

    private fun observed(id: String, title: String, running: Boolean) = ObservedSession(id, title, running)

    private fun session(
        linkId: String,
        projectId: String,
        sessionId: String,
        startedAt: Long,
        macName: String = "Mac",
        projectName: String = "项目",
        title: String = "标题",
    ) = RunningSession(linkId, macName, "default", projectId, projectName, sessionId, title, startedAt)
}
