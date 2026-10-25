package app.tocode.android

import org.json.JSONObject

data class AppSession(val id: String, val title: String, val running: Boolean = false)

data class AppProject(val id: String, val name: String)

data class AppModelProvider(val id: String, val name: String, val models: List<String>)

data class AppTurn(val role: String, val text: String, val interim: Boolean = false, val id: Long = 0)

sealed class ChatBlock {
    data class User(val text: String, val id: Long = 0) : ChatBlock()
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
                blocks.add(ChatBlock.User(turn.text, turn.id))
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

/** 一次提问，加上它后面的过程回复和结束回复，算一页。连续相同提问并进同一页。 */
fun exchanges(blocks: List<ChatBlock>): List<Exchange> {
    if (blocks.isEmpty()) return emptyList()
    val pages = mutableListOf<MutableList<ChatBlock>>()
    for (block in blocks) {
        if (block is ChatBlock.User) {
            val lastUser = pages.lastOrNull()?.filterIsInstance<ChatBlock.User>()?.firstOrNull()
            if (lastUser != null && lastUser.text == block.text) continue
            pages.add(mutableListOf(block))
        } else if (pages.isEmpty()) {
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

fun mergeTurns(existing: List<AppTurn>, incoming: List<AppTurn>): List<AppTurn> {
    val byId = linkedMapOf<Long, AppTurn>()
    for (turn in existing) if (turn.id > 0) byId[turn.id] = turn
    for (turn in incoming) if (turn.id > 0) byId[turn.id] = turn
    return byId.values.sortedBy { it.id }
}

fun pageAnchor(page: Exchange): Long? =
    page.blocks.filterIsInstance<ChatBlock.User>().firstOrNull()?.id?.takeIf { it > 0 }

/** `anchor` 为空时停在最后一页。前面补进旧消息后，仍停在这个用户消息所在的一页。 */
fun visibleExchangeByAnchor(pages: List<Exchange>, anchor: Long?): Int {
    if (pages.isEmpty()) return -1
    if (anchor == null || anchor <= 0L) return pages.lastIndex
    val index = pages.indexOfFirst { pageAnchor(it) == anchor }
    return if (index >= 0) index else pages.lastIndex
}

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
    val reserved: String? = null,
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

fun attachmentAfterHold(file: ChatAttachment, hold: String, path: String? = null): ChatAttachment =
    file.copy(hold = hold, reserved = path?.takeIf { it.isNotBlank() }, bytes = ByteArray(0), failed = false)

fun provisionalToken(id: String) = "@暂存:$id"

fun attachmentMention(path: String) = "@$path"

data class AttachmentSpan(
    val start: Int,
    val end: Int,
    val path: String?,
    val provisionalId: String?,
)

private val provisionalIdPattern = Regex("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")

private val attachmentExtensions = setOf(
    "jpeg", "webp", "heif", "heic", "docx", "xlsx", "pptx", "tiff", "html", "json",
    "jpg", "png", "gif", "pdf", "txt", "zip", "csv", "xml", "svg", "bmp", "tif",
    "mp3", "mp4", "mov", "wav", "doc", "xls", "ppt", "apk", "dmg", "log", "md", "gz",
)

/** 句子里的 @相对路径。没有 @ 的旧行不会变成小块。 */
fun attachmentSpans(text: String): List<AttachmentSpan> {
    val found = mutableListOf<AttachmentSpan>()
    var index = 0
    while (index < text.length) {
        if (text.startsWith("@暂存:", index)) {
            val idStart = index + "@暂存:".length
            val match = provisionalIdPattern.find(text, idStart)
            if (match != null && match.range.first == idStart) {
                found.add(AttachmentSpan(index, match.range.last + 1, null, match.value))
                index = match.range.last + 1
                continue
            }
        }
        val folder = when {
            text.startsWith("@对话附件-图片/", index) -> "对话附件-图片"
            text.startsWith("@对话附件-文件/", index) -> "对话附件-文件"
            else -> null
        }
        if (folder != null) {
            val nameStart = index + "@$folder/".length
            val nameEnd = attachmentNameEnd(text, nameStart)
            if (nameEnd > nameStart) {
                found.add(AttachmentSpan(index, nameEnd, text.substring(index + 1, nameEnd), null))
                index = nameEnd
                continue
            }
        }
        index++
    }
    return found
}

fun referencedAttachmentPaths(text: String): Set<String> =
    attachmentSpans(text).mapNotNull { it.path }.toSet()

/** 改掉 @路径 里的任何一个字，整段一起去掉。完整替换整段则保留用户输入。 */
fun coerceAttachmentTokens(before: String, after: String): String {
    if (before == after) return after
    val tokens = attachmentSpans(before)
    if (tokens.isEmpty()) return after
    var prefix = 0
    val limit = minOf(before.length, after.length)
    while (prefix < limit && before[prefix] == after[prefix]) prefix++
    var suffix = 0
    while (
        suffix < before.length - prefix &&
        suffix < after.length - prefix &&
        before[before.length - 1 - suffix] == after[after.length - 1 - suffix]
    ) suffix++
    val deleteStart = prefix
    val deleteEnd = before.length - suffix
    val insert = after.substring(prefix, after.length - suffix)
    val partial = tokens.filter { token ->
        val caretInside = deleteStart == deleteEnd && deleteStart > token.start && deleteStart < token.end
        val overlaps = deleteStart < token.end && deleteEnd > token.start
        val covered = deleteStart <= token.start && deleteEnd >= token.end
        (overlaps || caretInside) && !covered
    }
    if (partial.isEmpty()) return after
    val start = minOf(deleteStart, partial.minOf { it.start })
    val end = maxOf(deleteEnd, partial.maxOf { it.end })
    val inside = partial.any { deleteStart >= it.start && deleteEnd <= it.end }
    val replacement = if (inside) "" else insert
    return before.substring(0, start) + replacement + before.substring(end)
}

private fun attachmentNameEnd(text: String, start: Int): Int {
    var index = start
    var sawCjk = false
    while (index < text.length && index - start < 255) {
        val ch = text[index]
        if (isAttachmentBoundary(ch)) break
        if (isCjk(ch) && !sawCjk && isAsciiName(text, start, index)) break
        if (isCjk(ch)) sawCjk = true
        index++
        if (hasAttachmentExtension(text, start, index)) {
            val next = text.getOrNull(index)
            if (next == null || isAttachmentBoundary(next) || isCjk(next)) return index
        }
    }
    return index
}

private fun isAsciiName(text: String, start: Int, end: Int): Boolean {
    if (end <= start) return false
    return (start until end).all { text[it].code < 128 }
}

private fun hasAttachmentExtension(text: String, start: Int, end: Int): Boolean {
    val slice = text.substring(start, end)
    val dot = slice.lastIndexOf('.')
    if (dot <= 0) return false
    return slice.substring(dot + 1).lowercase() in attachmentExtensions
}

private fun isCjk(ch: Char): Boolean {
    val code = ch.code
    return code in 0x3400..0x9FFF || code in 0xF900..0xFAFF
}

private fun isAttachmentBoundary(ch: Char): Boolean =
    ch.isWhitespace() || ch == '@' || ch == '/' || ch.code < 32 || ch in "，。？！、；：,!?;:()（）【】[]「」《》<>"

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

/** Whether a tree request for this page/offset may start. Force only applies to offset 0. */
fun canStartFolderLoad(page: FolderPage?, offset: Int, force: Boolean = false): Boolean {
    if (offset == 0) {
        if (page?.loadingOffset != null) return false
        if (force) return true
        return page?.loaded != true
    }
    return page != null &&
        page.loaded &&
        page.truncated &&
        page.loadingOffset == null &&
        offset == page.next
}

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

enum class FolderSelectMark { None, Some, All }

fun folderSelectMark(childPaths: List<String>, selected: Set<String>): FolderSelectMark {
    if (childPaths.isEmpty()) return FolderSelectMark.None
    val hits = childPaths.count { it in selected }
    return when (hits) {
        0 -> FolderSelectMark.None
        childPaths.size -> FolderSelectMark.All
        else -> FolderSelectMark.Some
    }
}

/** Add the listed paths, or remove only those paths once every one is already selected. */
fun applyFolderSelectAll(childPaths: List<String>, selected: Set<String>): Set<String> {
    if (childPaths.isEmpty() || folderSelectMark(childPaths, selected) != FolderSelectMark.All) {
        return selected + childPaths
    }
    return selected - childPaths.toSet()
}

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

fun parseModelProviders(body: JSONObject): List<AppModelProvider> {
    val rows = body.optJSONArray("providers") ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optString("id").ifBlank { row.optString("slug") }
            if (id.isBlank()) continue
            val modelsArray = row.optJSONArray("models") ?: continue
            val models = buildList {
                for (modelIndex in 0 until modelsArray.length()) {
                    val name = modelsArray.optString(modelIndex)
                    if (name.isNotBlank()) add(name)
                }
            }
            addAll(acceptedModelProviders(id, row.optString("name"), models))
        }
    }
}

/** 只留下有模型名的厂家；空清单丢掉。不读密钥字段。 */
fun acceptedModelProviders(id: String, name: String, models: List<String>): List<AppModelProvider> {
    val cleaned = models.map { it.trim() }.filter { it.isNotEmpty() }
    if (id.isBlank() || cleaned.isEmpty()) return emptyList()
    return listOf(AppModelProvider(id, name.ifBlank { id }, cleaned))
}

/** 档案抽屉与模型抽屉顺序切换，不叠两层。 */
enum class ProfileDrawer {
    None,
    Projects,
    Models,
}

fun openModelDrawer(from: ProfileDrawer): ProfileDrawer =
    if (from == ProfileDrawer.Projects || from == ProfileDrawer.None) ProfileDrawer.Models else from

fun dismissModelDrawer(from: ProfileDrawer): ProfileDrawer =
    if (from == ProfileDrawer.Models) ProfileDrawer.Projects else from

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
            val id = row.optLong("id")
            if (id <= 0 || (role != "user" && role != "assistant")) continue
            if (role == "assistant" && text.isBlank()) continue
            add(AppTurn(role, text, row.optBoolean("interim"), id))
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
