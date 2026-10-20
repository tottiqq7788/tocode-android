package app.tocode.android

import org.json.JSONObject

data class AppSession(val id: String, val title: String)

data class AppTurn(val role: String, val text: String)

data class AppEntry(val path: String, val name: String, val kind: String)

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

fun parseSessions(body: JSONObject): List<AppSession> {
    val rows = body.optJSONArray("sessions") ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optString("id")
            if (id.isBlank()) continue
            val title = row.optString("title").ifBlank { "未命名会话" }
            add(AppSession(id, title))
        }
    }
}

fun parseTurns(body: JSONObject): List<AppTurn> {
    val rows = body.optJSONArray("messages") ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val role = row.optString("role")
            if (role != "user" && role != "assistant") continue
            add(AppTurn(role, row.optString("text")))
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
