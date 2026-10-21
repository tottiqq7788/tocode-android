package app.tocode.android

import org.json.JSONObject

data class AppSession(val id: String, val title: String, val running: Boolean = false)

data class AppProject(val id: String, val name: String)

data class AppTurn(val role: String, val text: String, val interim: Boolean = false)

sealed class ChatBlock {
    data class User(val text: String) : ChatBlock()
    data class Reply(val text: String) : ChatBlock()
    data class Process(val lines: List<String>) : ChatBlock()
}

fun chatBlocks(turns: List<AppTurn>): List<ChatBlock> {
    val blocks = mutableListOf<ChatBlock>()
    val pending = mutableListOf<String>()
    fun flush() {
        if (pending.isNotEmpty()) {
            blocks.add(ChatBlock.Process(pending.toList()))
            pending.clear()
        }
    }
    for (turn in turns) {
        when {
            turn.role == "user" -> {
                flush()
                blocks.add(ChatBlock.User(turn.text))
            }
            turn.interim -> pending.add(turn.text)
            else -> {
                flush()
                blocks.add(ChatBlock.Reply(turn.text))
            }
        }
    }
    flush()
    return blocks
}

data class Exchange(val blocks: List<ChatBlock>)

/** 一次提问，加上它后面的过程回复和结束回复，算一页。 */
fun exchanges(blocks: List<ChatBlock>): List<Exchange> {
    if (blocks.isEmpty()) return emptyList()
    val pages = mutableListOf<MutableList<ChatBlock>>()
    for (block in blocks) {
        if (block is ChatBlock.User || pages.isEmpty()) {
            pages.add(mutableListOf(block))
        } else {
            pages.last().add(block)
        }
    }
    return pages.map { Exchange(it.toList()) }
}

/** `pinned` 为空时停在最后一页。看较早的一页时，新回复不改变这个下标。 */
fun visibleExchange(count: Int, pinned: Int?): Int {
    if (count <= 0) return -1
    val chosen = pinned ?: return count - 1
    return chosen.coerceIn(0, count - 1)
}

fun pageLabel(count: Int, index: Int): String =
    if (count <= 0) "0/0" else "${index + 1}/$count"

/** 翻到最后一页时返回空，表示继续跟着最新一次。 */
fun pagerTarget(count: Int, index: Int, forward: Boolean): Int? {
    if (count <= 0 || index !in 0 until count) return null
    val next = if (forward) index + 1 else index - 1
    if (next !in 0 until count) return if (index == count - 1) null else index
    return if (next == count - 1) null else next
}

const val ATTACHMENT_BYTE_LIMIT = 20 * 1024 * 1024
const val ATTACHMENT_COUNT = 10

data class ChatAttachment(
    val id: String,
    val name: String,
    val kind: String,
    val bytes: ByteArray,
    val landed: String? = null,
    val hold: String? = null,
    val failed: Boolean = false,
)

/** 已经落盘的不用再传。已经暂存的发送时只落盘。其余才上传。 */
fun attachmentTransfer(file: ChatAttachment): String = when {
    !file.landed.isNullOrBlank() -> "ready"
    !file.hold.isNullOrBlank() -> "place"
    else -> "upload"
}

fun attachmentLabel(file: ChatAttachment): String = when (attachmentTransfer(file)) {
    "ready", "place" -> file.name
    else -> if (file.failed) "${file.name} · 没传上" else "${file.name} · 上传中"
}

fun attachmentAfterHold(file: ChatAttachment, hold: String): ChatAttachment =
    file.copy(hold = hold, bytes = ByteArray(0), failed = false)

fun attachmentFileName(display: String, fallback: String): String {
    val name = display.trim()
    val usable = name.isNotEmpty() && name.length <= 255 &&
        !name.contains('/') && !name.contains('\\') &&
        name != "." && name != ".." &&
        name.none { it.code == 0 || it.code < 32 }
    return if (usable) name else fallback
}

fun hermesTextWithPaths(text: String, paths: List<String>, limit: Int = 8000): String? {
    val body = text.trim()
    val lines = paths.map { it.trim() }.filter { it.isNotEmpty() }
    val combined = when {
        lines.isEmpty() -> body
        body.isEmpty() -> lines.joinToString("\n")
        else -> body + "\n\n" + lines.joinToString("\n")
    }
    if (combined.isEmpty() || combined.length > limit) return null
    return combined
}

fun landedAttachments(files: List<ChatAttachment>, paths: List<String?>): List<ChatAttachment> {
    return files.mapIndexed { index, file ->
        val path = paths.getOrNull(index)
        if (path.isNullOrBlank()) file else file.copy(landed = path, bytes = ByteArray(0))
    }
}

data class AppEntry(val path: String, val name: String, val kind: String)

data class FolderPage(
    val next: Int = 0,
    val truncated: Boolean = false,
    val loadingOffset: Int? = null,
    val loaded: Boolean = false,
)

fun absoluteProjectPath(root: String, relative: String): String? {
    if (!root.startsWith("/") || root.contains('\u0000')) return null
    val base = root.trimEnd('/')
    if (base.isEmpty() || base.contains("..")) return null
    val parts = relative.split('/')
    if (parts.isEmpty() || parts.any { it.isEmpty() || it == "." || it == ".." }) return null
    return "$base/$relative"
}

fun appendCitation(draft: String, absolute: String, limit: Int = 4000): String? {
    if (!absolute.startsWith("/") || absolute.contains('\n') || absolute.contains('「') || absolute.contains('」')) return null
    val token = "「$absolute」"
    val next = when {
        draft.isEmpty() -> token
        draft.endsWith("\n") -> draft + token
        else -> draft + "\n" + token
    }
    return if (next.length > limit) null else next
}

fun mergeFolderEntries(existing: List<AppEntry>, folder: String, incoming: List<AppEntry>, offset: Int): List<AppEntry> {
    val page = incoming.filter { it.path.isNotEmpty() && it.parent() == folder }
    return if (offset <= 0) {
        existing.filter { it.parent() != folder } + page
    } else {
        val known = existing.map { it.path }.toSet()
        existing + page.filter { it.path !in known }
    }
}

data class AppPreview(
    val path: String,
    val name: String,
    val loading: Boolean = false,
    val format: String = "text",
    val content: String = "",
    val next: Int = 0,
    val truncated: Boolean = false,
    val loadingMore: Boolean = false,
    val notice: String = "",
)

data class PathSpan(val start: Int, val end: Int, val path: String, val folder: Boolean)

fun pathSpans(text: String, entries: List<AppEntry>): List<PathSpan> {
    val used = BooleanArray(text.length)
    val found = mutableListOf<PathSpan>()
    val ordered = entries.filter { it.path.isNotEmpty() }.sortedByDescending { it.path.length }
    for (entry in ordered) {
        var from = 0
        while (from < text.length) {
            val at = text.indexOf(entry.path, from)
            if (at < 0) break
            val end = at + entry.path.length
            val beforeOk = at == 0 || text[at - 1] == '/' || !text[at - 1].isPathChar()
            val afterOk = end == text.length || !text[end].isPathChar()
            if (beforeOk && afterOk && (at until end).all { !used[it] }) {
                for (index in at until end) used[index] = true
                found.add(PathSpan(at, end, entry.path, entry.isFolder()))
            }
            from = at + 1
        }
    }
    return found.sortedBy { it.start }
}

private fun Char.isPathChar(): Boolean =
    isLetterOrDigit() || this == '/' || this == '_' || this == '.' || this == '-' || this == '+'

fun chatEntranceClosed(hermesInstalled: Boolean?): Boolean = hermesInstalled == false

fun AppEntry.parent(): String = path.trimEnd('/').substringBeforeLast('/', "")

fun AppEntry.isFolder(): Boolean = kind == "directory"

fun childrenOf(entries: List<AppEntry>, parent: String): List<AppEntry> =
    entries.filter { it.parent() == parent }
        .sortedWith(compareBy<AppEntry>({ !it.isFolder() }, { it.name.lowercase() }))

fun breadcrumb(path: String): List<String> {
    if (path.isEmpty()) return emptyList()
    val parts = path.split('/').filter { it.isNotEmpty() }
    return parts.indices.map { parts.subList(0, it + 1).joinToString("/") }
}

fun parseNames(body: JSONObject): List<String> {
    val rows = body.optJSONArray("profiles") ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val name = rows.optString(index)
            if (name.isNotBlank()) add(name)
        }
    }
}

fun parseFolders(body: JSONObject): List<String> {
    val rows = body.optJSONArray("folders") ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val name = rows.optString(index)
            if (name.isNotEmpty() && name != "." && name != ".." && !name.contains('/')) add(name)
        }
    }
}

fun childFolder(parent: String, name: String): String = if (parent.isEmpty()) name else "$parent/$name"

fun parentFolder(path: String): String = path.substringBeforeLast('/', "")

fun parseProjects(body: JSONObject): List<AppProject> {
    val rows = body.optJSONArray("projects") ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optString("id")
            if (id.isBlank()) continue
            add(AppProject(id, row.optString("name").ifBlank { "未命名项目" }))
        }
    }
}

fun parseSessions(body: JSONObject): List<AppSession> {
    val rows = body.optJSONArray("sessions") ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optString("id")
            if (id.isBlank()) continue
            val title = row.optString("title").ifBlank { "未命名会话" }
            add(AppSession(id, title, row.optBoolean("running")))
        }
    }
}

fun parseTurns(body: JSONObject): List<AppTurn> {
    val rows = body.optJSONArray("messages") ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val role = row.optString("role")
            val text = row.optString("text")
            if (role != "user" && role != "assistant") continue
            if (role == "assistant" && text.isBlank()) continue
            add(AppTurn(role, text, row.optBoolean("interim")))
        }
    }
}

fun parseEntries(body: JSONObject): List<AppEntry> {
    val rows = body.optJSONArray("entries") ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val path = row.optString("path")
            if (path.isBlank()) continue
            add(AppEntry(path, row.optString("name").ifBlank { path }, row.optString("kind")))
        }
    }
}
