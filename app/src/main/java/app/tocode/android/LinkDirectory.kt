package app.tocode.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AppLink(
    val id: String,
    val macName: String,
    val relay: String,
    val token: String,
)

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

class LinkDisk(private val stateFile: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): PhoneState {
        if (!stateFile.exists()) {
            return PhoneState(androidId = java.util.UUID.randomUUID().toString(), androidName = "Android")
        }
        return json.decodeFromString<PhoneState>(stateFile.readText())
    }

    fun save(state: PhoneState) {
        stateFile.parentFile?.mkdirs()
        stateFile.writeText(json.encodeToString(state))
    }
}

fun prepareRemoteLayout(filesDir: File) {
    val marker = File(filesDir, "remote-layout")
    if (marker.exists()) return
    File(filesDir, "phone-state.json").delete()
    File(filesDir, "history").deleteRecursively()
    File(filesDir, "received").deleteRecursively()
    marker.writeText("1")
}
