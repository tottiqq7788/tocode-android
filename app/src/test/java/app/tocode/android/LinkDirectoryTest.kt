package app.tocode.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinkDirectoryTest {
    @Test
    fun onePhoneKeepsManyMacs() {
        val directory = LinkDirectory()
        directory.upsert(link("a", "Mac A"))
        directory.upsert(link("b", "Mac B"))
        assertEquals(listOf("a", "b"), directory.all().map { it.id })
        assertTrue(directory.select("b"))
        assertEquals("Mac B", directory.current()?.macName)
        assertFalse(directory.select("missing"))
        directory.remove("b")
        assertEquals("a", directory.currentId)
    }

    @Test
    fun upgradeDropsOldChatFilesOnce() {
        val root = File.createTempFile("tocode-remote", "").apply {
            delete()
            mkdirs()
        }
        File(root, "phone-state.json").writeText("{\"links\":[{\"roleName\":\"old\"}]}")
        File(root, "history").apply { mkdirs() }
        File(root, "received").apply { mkdirs() }
        prepareRemoteLayout(root)
        assertFalse(File(root, "phone-state.json").exists())
        assertFalse(File(root, "history").exists())
        assertFalse(File(root, "received").exists())
        File(root, "phone-state.json").writeText("{}")
        prepareRemoteLayout(root)
        assertTrue(File(root, "phone-state.json").exists())
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

    @Test
    fun buttonsStayOnTheFixedList() {
        assertEquals(11, remoteActions.size)
        assertTrue(remoteActions.none { it.first == "shell" || it.first == "quit" })
    }

    @Test
    fun everyButtonHasOneGroup() {
        val grouped = remoteGroups.flatMap { it.second }
        assertEquals(grouped.size, grouped.toSet().size)
        assertEquals(remoteActions.map { it.first }.toSet(), grouped.toSet())
    }

    @Test
    fun folderBrowserShowsOneLevelFoldersFirst() {
        val entries = listOf(
            AppEntry("b.txt", "b.txt", "file"),
            AppEntry("src", "src", "directory"),
            AppEntry("src/main", "main", "directory"),
            AppEntry("src/main/App.kt", "App.kt", "file"),
            AppEntry("A.md", "A.md", "file"),
        )
        assertEquals(listOf("src", "A.md", "b.txt"), childrenOf(entries, "").map { it.path })
        assertEquals(listOf("src/main"), childrenOf(entries, "src").map { it.path })
        assertEquals(listOf("src", "src/main"), breadcrumb("src/main"))
        assertTrue(breadcrumb("").isEmpty())
    }

    @Test
    fun chatStaysClosedWithoutHermes() {
        assertTrue(chatEntranceClosed(false))
        assertFalse(chatEntranceClosed(true))
        assertFalse(chatEntranceClosed(null))
    }

    private fun link(id: String, mac: String) = AppLink(
        id = id,
        macName = mac,
        relay = "https://relay.example",
        token = "token-$id",
    )
}
