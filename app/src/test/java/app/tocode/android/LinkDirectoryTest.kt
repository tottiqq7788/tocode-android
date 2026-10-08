package app.tocode.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinkDirectoryTest {
    @Test
    fun masterSwitchFollowsOnlyTheSelectedLink() {
        val first = link("a", "Mac-1", "alpha")
        val second = link("b", "Mac-1", "beta")
        val directory = LinkDirectory()
        directory.upsert(first)
        directory.upsert(second)
        assertEquals("a", directory.currentId)
        assertTrue(directory.select("b"))
        assertEquals("b", directory.current()?.id)
        assertEquals("beta", directory.current()?.roleName)
        assertFalse(directory.select("missing"))
        directory.remove("b")
        assertEquals("a", directory.currentId)
        val restored = LinkDirectory(listOf(second, first), "a")
        assertEquals("a", restored.currentId)
    }

    @Test
    fun deletingALinkKeepsLocalHistory() {
        val root = File.createTempFile("tocode-links", "").apply {
            delete()
            mkdirs()
        }
        val disk = LinkDisk(File(root, "phone-state.json"), File(root, "history"))
        val log = SessionLog()
        val line = ChatLine(outgoing = false, text = "留下的话")
        log.append("a", line)
        disk.appendHistory("a", line)
        val directory = LinkDirectory(listOf(link("a", "Mac", "alpha")), "a")
        directory.remove("a")
        disk.save(PhoneState("phone", "Pixel", directory.all(), directory.currentId))
        val (loaded, restored) = disk.load()
        assertTrue(loaded.links.isEmpty())
        assertNull(loaded.currentId)
        assertEquals("留下的话", restored.lines("a").single().text)
        assertEquals("留下的话", log.lines("a").single().text)
    }

    @Test
    fun pairingPayloadHasOnlyRelayAndCode() {
        val payload = parsePairingPayload(
            "tocode-app://pair?relay=https%3A%2F%2Frelay.example%2F&code=ABCD2345"
        )
        assertEquals("https://relay.example", payload?.relay)
        assertEquals("ABCD2345", payload?.code)
        assertNull(parsePairingPayload("tocode-app://pair?relay=http%3A%2F%2Fexample.com&code=ABCD2345"))
        assertNull(parsePairingPayload("https://relay.example/?prompt=SECRET_PROMPT"))
    }

    private fun link(id: String, mac: String, role: String) = AppLink(
        id = id,
        macName = mac,
        roleName = role,
        displayName = role,
        relay = "https://relay.example",
        token = "token-$id",
    )
}
