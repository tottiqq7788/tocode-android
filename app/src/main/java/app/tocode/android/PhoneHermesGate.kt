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

data class AppEntry(val path: String, val name: String, val kind: String)

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
