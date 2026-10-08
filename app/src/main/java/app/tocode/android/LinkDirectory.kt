package app.tocode.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AppLink(
    val id: String,
    val macName: String,
    val roleName: String,
    val displayName: String,
    val relay: String,
    val token: String,
)

@Serializable
data class ChatLine(
    val outgoing: Boolean,
    val text: String,
    val notice: Boolean = false,
)

fun AppLink.listTitle(): String = displayName.ifBlank { roleName }.ifBlank { "角色" }

@Serializable
data class PhoneState(
    val androidId: String,
    val androidName: String,
    val links: List<AppLink> = emptyList(),
    val currentId: String? = null,
)

class LinkDirectory(initial: List<AppLink> = emptyList(), selectedId: String? = null) {
    private val links = linkedMapOf<String, AppLink>()
    var currentId: String? = null
        private set

    init {
        initial.forEach { links[it.id] = it }
        currentId = selectedId?.takeIf { links.containsKey(it) } ?: links.keys.firstOrNull()
    }

    fun all(): List<AppLink> = links.values.toList()

    fun current(): AppLink? = currentId?.let { links[it] }

    fun upsert(link: AppLink) {
        val first = links.isEmpty()
        links[link.id] = link
        if (first || currentId == null) currentId = link.id
    }

    fun select(id: String): Boolean {
        if (!links.containsKey(id)) return false
        currentId = id
        return true
    }

    fun remove(id: String) {
        links.remove(id)
        if (currentId == id) currentId = links.keys.firstOrNull()
    }
}

class SessionLog {
    private val lines = linkedMapOf<String, MutableList<ChatLine>>()

    fun append(linkId: String, line: ChatLine) {
        lines.getOrPut(linkId) { mutableListOf() }.add(line)
    }

    fun lines(linkId: String): List<ChatLine> = lines[linkId]?.toList().orEmpty()
}

class LinkDisk(private val stateFile: File, private val historyDirectory: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Pair<PhoneState, SessionLog> {
        val state = if (stateFile.exists()) {
            json.decodeFromString<PhoneState>(stateFile.readText())
        } else {
            PhoneState(androidId = java.util.UUID.randomUUID().toString(), androidName = "Android")
        }
        val log = SessionLog()
        if (historyDirectory.isDirectory) {
            historyDirectory.listFiles()?.forEach { file ->
                if (file.extension != "jsonl") return@forEach
                file.readLines().forEach { line ->
                    if (line.isBlank()) return@forEach
                    log.append(file.nameWithoutExtension, json.decodeFromString<ChatLine>(line))
                }
            }
        }
        return state to log
    }

    fun save(state: PhoneState) {
        stateFile.parentFile?.mkdirs()
        stateFile.writeText(json.encodeToString(state))
    }

    fun appendHistory(linkId: String, line: ChatLine) {
        historyDirectory.mkdirs()
        val file = File(historyDirectory, "$linkId.jsonl")
        file.appendText(json.encodeToString(line) + "\n")
    }
}
