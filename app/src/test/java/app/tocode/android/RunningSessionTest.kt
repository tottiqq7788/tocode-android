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
        assertEquals("新", small.run?.title)
        assertEquals(3, small.running)
        assertEquals("new", small.run?.open?.sessionId)
        assertNull(small.reply)

        val medium = mediumSessionView(sessions)
        assertEquals(listOf("新", "另一台"), medium.runs.map { it.title })
        assertEquals("甲", medium.runs.first().place)
        assertTrue(medium.replies.isEmpty())

        val extra = (4..10).map { session("mac-a", "p1", "s$it", it.toLong(), macName = "Mac A", projectName = "甲", title = "第$it") }
        val large = largeSessionView(sessions + extra)
        assertEquals(5, large.groups.sumOf { it.rows.size })
        assertEquals(5, large.more)
        assertEquals("Mac A · 甲", large.groups.first().label)
    }

    @Test
    fun recentRepliesKeepThreeForADayAndLeaveWhenOpenedOrRunning() {
        val hour = 60 * 60 * 1000L
        var replies = emptyList<RecentReply>()
        (1..4).forEach { index ->
            replies = noteReplied(replies, listOf(session("mac", "p1", "s$index", 1, title = "第$index")), index * hour)
        }
        assertEquals(listOf("s4", "s3", "s2"), replies.map { it.sessionId })
        assertTrue(freshReplies(replies, 4 * hour + RECENT_REPLY_TTL_MS + 1).none { it.sessionId == "s2" })

        replies = noteReplied(replies, listOf(session("mac", "p1", "s3", 1, title = "第3")), 5 * hour)
        assertEquals(listOf("s3", "s4", "s2"), replies.map { it.sessionId })

        assertEquals(listOf("s4", "s2"), dropReplied(replies, "mac", "s3").map { it.sessionId })
        assertEquals(listOf("s3", "s2"), dropRunningReplies(replies, listOf(session("mac", "p1", "s4", 9))).map { it.sessionId })
    }

    @Test
    fun widgetsFillWithRecentRepliesAndHideProfileAndPath() {
        val now = 10 * 60 * 1000L
        val reply = RecentReply("mac", "Mac A", "secret-profile", "p1", "甲", "done", "写完了", now - 3 * 60 * 1000L)
        val running = listOf(session("mac", "p1", "run", 1, projectName = "甲", title = "进行中"))

        val idle = smallSessionView(emptyList(), listOf(reply), now)
        assertNull(idle.run)
        assertEquals("done", idle.reply?.open?.sessionId)
        assertNull(smallSessionView(running, listOf(reply), now).reply)

        val medium = mediumSessionView(running, listOf(reply), now)
        assertEquals(listOf("进行中"), medium.runs.map { it.title })
        assertEquals(listOf("写完了"), medium.replies.map { it.title })

        val large = largeSessionView(running, listOf(reply), now)
        assertEquals(listOf("写完了"), large.replies.map { it.title })
        val shown = (medium.runs.flatMap { listOf(it.title, it.place) } + large.replies.flatMap { listOf(it.title, it.place) } +
            large.groups.map { it.label }).joinToString("|")
        assertFalse(shown.contains("secret-profile"))
        assertFalse(shown.contains("/"))
        assertEquals("3 分钟前", repliedAgo(now, reply.finishedAt))
        assertEquals("刚刚", repliedAgo(now, now))
        assertEquals(TAB_NEW_SESSION, widgetTab(TAB_NEW_SESSION))
        assertNull(widgetTab("../evil"))
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
