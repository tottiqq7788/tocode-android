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
    fun citingAppendsTheAbsolutePathWithoutLeavingTheProject() {
        val root = "/Users/totti/project"
        assertEquals("$root/对话附件-文件/a.png", absoluteProjectPath(root, "对话附件-文件/a.png"))
        assertEquals(null, absoluteProjectPath(root, "../secret"))
        assertEquals(null, absoluteProjectPath("relative", "a.txt"))
        assertEquals("「$root/a.txt」", appendCitation("", "$root/a.txt"))
        assertEquals("已有\n「$root/a.txt」", appendCitation("已有", "$root/a.txt"))
        assertEquals("「$root/a.txt」\n「$root/b.txt」", appendCitation("「$root/a.txt」", "$root/b.txt"))
        assertEquals(null, appendCitation("x".repeat(4000), "$root/a.txt"))
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
    fun forceReloadAllowsTreeRequestAfterFolderAlreadyLoaded() {
        val loaded = FolderPage(next = 2, truncated = false, loaded = true)
        assertFalse(canStartFolderLoad(loaded, 0))
        assertTrue(canStartFolderLoad(loaded, 0, force = true))
        assertFalse(canStartFolderLoad(loaded.copy(loadingOffset = 0), 0, force = true))
        assertTrue(canStartFolderLoad(null, 0))
        assertTrue(canStartFolderLoad(null, 0, force = true))
        val page = FolderPage(next = 2, truncated = true, loaded = true)
        assertTrue(canStartFolderLoad(page, 2))
        assertTrue(canStartFolderLoad(page, 2, force = true))
        assertFalse(canStartFolderLoad(page, 1))
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
    fun previewBookmarkStaysWithTheSameProjectFile() {
        val root = java.io.File(System.getProperty("java.io.tmpdir"), "preview-bookmarks-${java.util.UUID.randomUUID()}")
        root.mkdirs()
        val store = PreviewBookmarkStore(java.io.File(root, "preview-bookmarks.json"))
        val key = previewBookmarkKey("liubei", "p_abc", "章节/001.md")
        store.put(key, 480, 12000)
        store.put(key, 0, 0)
        val again = PreviewBookmarkStore(java.io.File(root, "preview-bookmarks.json"))
        assertEquals(PreviewBookmark(key, 480, 12000), again.get(key))
        assertEquals(null, again.get(previewBookmarkKey("default", "p_abc", "章节/001.md")))
        root.deleteRecursively()
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
        assertEquals("0/0", pageLabel(0, -1))
        assertEquals("5/6", pageLabel(6, 4))
        val older = AppTurn("user", "更早", id = 1)
        val kept = AppTurn("user", "停在这里", id = 5)
        val latest = AppTurn("user", "最新", id = 9)
        val merged = mergeTurns(listOf(kept, latest), listOf(older, kept))
        assertEquals(listOf(1L, 5L, 9L), merged.map { it.id })
        val mergedPages = exchanges(chatBlocks(merged.map { it.copy(text = it.text) }))
        assertEquals(1, visibleExchangeByAnchor(mergedPages, 5))
        assertEquals(2, visibleExchangeByAnchor(mergedPages, null))
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
        val fresh = ChatAttachment("2", "965.jpg", "image", byteArrayOf(1, 2, 3))
        assertEquals("upload", attachmentTransfer(fresh))
        assertEquals("965.jpg · 上传中", attachmentLabel(fresh))
        val held = attachmentAfterHold(fresh, "11111111-1111-1111-1111-111111111111")
        assertEquals("place", attachmentTransfer(held))
        assertEquals("965.jpg", attachmentLabel(held))
        assertEquals(0, held.bytes.size)
        assertEquals("ready", attachmentTransfer(held.copy(landed = "对话附件-图片/965.jpg")))
        assertEquals("965.jpg · 没传上", attachmentLabel(fresh.copy(failed = true)))
        val reserved = attachmentAfterHold(fresh, "11111111-1111-1111-1111-111111111111", "对话附件-图片/965.jpg")
        assertEquals("对话附件-图片/965.jpg", reserved.reserved)
        assertEquals("place", attachmentTransfer(reserved))
    }

    @Test
    fun attachmentChipIsTheAtPathInsideTheSentence() {
        val text = "这张@对话附件-图片/965.jpg图片是啥？"
        val spans = attachmentSpans(text)
        assertEquals(1, spans.size)
        assertEquals("对话附件-图片/965.jpg", spans[0].path)
        assertEquals("965.jpg", spans[0].path!!.substringAfterLast('/'))
        assertTrue(attachmentSpans("对话附件-图片/965.jpg").isEmpty())
        assertEquals("对话附件-文件/说明.pdf", attachmentSpans("@对话附件-文件/说明.pdf后面").single().path)
        assertEquals("对话附件-文件/readme", attachmentSpans("前@对话附件-文件/readme后面").single().path)
        assertEquals("这张图片是啥？", coerceAttachmentTokens(text, text.removeRange(spans[0].end - 1, spans[0].end)))
        assertEquals("这张x图片是啥？", coerceAttachmentTokens(text, text.replace("@对话附件-图片/965.jpg", "x")))
        val id = "11111111-1111-1111-1111-111111111111"
        val pending = "看${provisionalToken(id)}这个"
        val pendingSpan = attachmentSpans(pending).single()
        assertEquals(id, pendingSpan.provisionalId)
        assertEquals("看这个", coerceAttachmentTokens(pending, pending.removeRange(pendingSpan.end - 1, pendingSpan.end)))
        assertEquals(setOf("对话附件-图片/965.jpg"), referencedAttachmentPaths(text))
        assertEquals("@对话附件-图片/965.jpg", attachmentMention("对话附件-图片/965.jpg"))
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
