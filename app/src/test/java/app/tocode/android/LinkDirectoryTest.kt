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
    fun mappedButtonOnlyCarriesItsId() {
        assertEquals(setOf("button_id"), remotePressFields("abc").keys)
        assertEquals("abc", remotePressFields("abc")["button_id"])
        assertNull(visibleRemoteButton("  ", "锁屏", "", "lock"))
        assertNull(visibleRemoteButton("abc", "  ", "", "lock"))
        val button = visibleRemoteButton(" abc ", " 锁屏 ", " 离开 ", " lock ")
        assertEquals(RemoteButton("abc", "锁屏", "离开", "lock"), button)
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
    fun processLinesCollapseAheadOfTheClosingReply() {
        val blocks = chatBlocks(
            listOf(
                AppTurn("user", "写"),
                AppTurn("assistant", "先读", interim = true),
                AppTurn("assistant", "还差字数", interim = true),
                AppTurn("assistant", "写完了"),
            )
        )
        assertEquals(3, blocks.size)
        assertEquals(ChatBlock.User("写"), blocks[0])
        assertEquals(ChatBlock.Process(listOf("先读", "还差字数")), blocks[1])
        assertEquals(ChatBlock.Reply("写完了"), blocks[2])
    }

    @Test
    fun folderPagesAppendWithoutDroppingTheEarlierPage() {
        val first = listOf(
            AppEntry("dir", "dir", "directory"),
            AppEntry("a.txt", "a.txt", "file"),
        )
        val kept = mergeFolderEntries(first, "", first, 0)
        val more = mergeFolderEntries(
            kept,
            "",
            listOf(AppEntry("b.txt", "b.txt", "file"), AppEntry("a.txt", "a.txt", "file"), AppEntry("dir/nested.txt", "nested.txt", "file")),
            2,
        )
        assertEquals(listOf("dir", "a.txt", "b.txt"), more.map { it.path })
        val replaced = mergeFolderEntries(more, "dir", listOf(AppEntry("dir/nested.txt", "nested.txt", "file")), 0)
        assertEquals(listOf("dir", "a.txt", "b.txt", "dir/nested.txt"), replaced.map { it.path })
    }

    @Test
    fun replyPathsLinkTheLongestProjectEntry() {
        val entries = listOf(
            AppEntry("chapters", "chapters", "directory"),
            AppEntry("chapters/007.md", "007.md", "file"),
            AppEntry("notes", "notes", "directory"),
        )
        val text = "改好了 /Users/totti/Documents/hermes/liubei/Eonline/chapters/007.md ，目录在 chapters 里。"
        val spans = pathSpans(text, entries)
        assertEquals(listOf("chapters/007.md", "chapters"), spans.map { it.path })
        assertEquals(listOf(false, true), spans.map { it.folder })
        assertEquals("chapters/007.md", text.substring(spans[0].start, spans[0].end))
        assertEquals("chapters", text.substring(spans[1].start, spans[1].end))
    }

    @Test
    fun oneQuestionAndItsReplyIsOnePage() {
        val pages = exchanges(
            chatBlocks(
                listOf(
                    AppTurn("user", "第一问"),
                    AppTurn("assistant", "先看", interim = true),
                    AppTurn("assistant", "第一答"),
                    AppTurn("user", "第二问"),
                    AppTurn("assistant", "还在写", interim = true),
                )
            )
        )
        assertEquals(2, pages.size)
        assertEquals(listOf("第一问"), (pages[0].blocks[0] as ChatBlock.User).let { listOf(it.text) })
        assertEquals(3, pages[0].blocks.size)
        assertTrue(pages[0].blocks[1] is ChatBlock.Process)
        assertTrue(pages[0].blocks[2] is ChatBlock.Reply)
        assertEquals(2, pages[1].blocks.size)
        assertEquals(5, visibleExchange(6, null))
        assertEquals(4, visibleExchange(6, 4))
        assertEquals(-1, visibleExchange(0, null))
        assertNull(pagerTarget(6, 4, forward = true))
        assertEquals(4, pagerTarget(6, 5, forward = false))
    }

    @Test
    fun hermesTextAppendsRelativePathsAfterTheFilesLand() {
        assertEquals(
            "看一下\n\n对话附件-图片/a.jpg\n对话附件-文件/b.pdf",
            hermesTextWithPaths("看一下", listOf("对话附件-图片/a.jpg", "对话附件-文件/b.pdf")),
        )
        assertEquals("对话附件-文件/b.pdf", hermesTextWithPaths("  ", listOf("对话附件-文件/b.pdf")))
        assertNull(hermesTextWithPaths("", emptyList()))
        assertNull(hermesTextWithPaths("看一下", listOf("对话附件-图片/a.jpg"), limit = 4))
        assertEquals("a.jpg", attachmentFileName("a.jpg", "附件"))
        assertEquals("附件", attachmentFileName("../a.jpg", "附件"))
        assertEquals("附件", attachmentFileName("a/b.jpg", "附件"))
        val marked = landedAttachments(
            listOf(ChatAttachment("1", "a.jpg", "image", byteArrayOf(1))),
            listOf("对话附件-图片/a.jpg"),
        )
        assertEquals("对话附件-图片/a.jpg", marked[0].landed)
        assertEquals(0, marked[0].bytes.size)
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
