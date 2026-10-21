package app.tocode.android

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.WebSocket
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

class AppDesk {
    var installed by mutableStateOf<Boolean?>(null)
    var running by mutableStateOf(false)
    var profile by mutableStateOf("default")
    var profiles by mutableStateOf<List<String>>(emptyList())
    var projects by mutableStateOf<List<AppProject>>(emptyList())
    var projectsLoaded by mutableStateOf(false)
    var macOffline by mutableStateOf(false)
    var openProject by mutableStateOf<AppProject?>(null)
    var sessions by mutableStateOf<List<AppSession>>(emptyList())
    var sessionsLoaded by mutableStateOf(false)
    var entries by mutableStateOf<List<AppEntry>>(emptyList())
    var projectRoot by mutableStateOf("")
    var folderPages by mutableStateOf<Map<String, FolderPage>>(emptyMap())
    var treeLoaded by mutableStateOf(false)
    var treeNotice by mutableStateOf("")
    var folderPath by mutableStateOf("")
    var folders by mutableStateOf<List<String>>(emptyList())
    var foldersLoading by mutableStateOf(false)
    var foldersNotice by mutableStateOf("")
    var turns by mutableStateOf<List<AppTurn>>(emptyList())
    var turnsLoading by mutableStateOf(false)
    var openSession by mutableStateOf<AppSession?>(null)
    var preview by mutableStateOf<AppPreview?>(null)
    var directoryPath by mutableStateOf("")
    var tab by mutableStateOf("sessions")
    var notice by mutableStateOf("")
    var creating by mutableStateOf(false)
    var drafting by mutableStateOf(false)
    var chatRequest by mutableStateOf<String?>(null)
    var inflight by mutableStateOf<Map<String, String>>(emptyMap())
    var pendingText = ""
    var restoreDraft by mutableStateOf("")

    val sending: Boolean
        get() {
            val session = openSession ?: return false
            if (session.id.isEmpty()) return chatRequest != null
            return inflight.containsKey(session.id) || session.running
        }
    val canChat: Boolean get() = installed == true && running
}

private data class AppWait(
    val linkId: String,
    val kind: String,
    val sessionId: String,
    val projectId: String,
    val path: String = "",
    val offset: Int = 0,
)

private data class FileWait(val linkId: String, val callback: (String?) -> Unit)

class RemoteController(
    private val directory: LinkDirectory,
    private val disk: LinkDisk,
    private var phone: PhoneState,
    private val client: RelayClient,
) {
    var scanning by mutableStateOf(false)
    var pairing by mutableStateOf(false)
    var scanNotice by mutableStateOf("")
    var selectedId by mutableStateOf(directory.currentId)
    private var linkVersion by mutableIntStateOf(0)
    val online = mutableStateMapOf<String, Boolean>()
    val pending = mutableStateMapOf<String, String>()
    val pendingAction = mutableStateMapOf<String, String>()
    val lastAction = mutableStateMapOf<String, String>()
    val lastResult = mutableStateMapOf<String, String>()
    val lastOk = mutableStateMapOf<String, Boolean>()
    val desktopLists = mutableStateMapOf<String, List<RemoteDisplay>>()
    val buttonLists = mutableStateMapOf<String, List<RemoteButton>>()
    val buttonNotice = mutableStateMapOf<String, String>()
    val pressedButton = mutableStateMapOf<String, String>()
    private val followUp = mutableMapOf<String, () -> Unit>()
    val desktopNotice = mutableStateMapOf<String, String>()
    private val desks = linkedMapOf<String, AppDesk>()
    private val appWait = linkedMapOf<String, AppWait>()
    private val fileWait = linkedMapOf<String, FileWait>()
    private val attachmentBusy = mutableSetOf<String>()
    private val sockets = linkedMapOf<String, WebSocket>()
    private val retryDelay = linkedMapOf<String, Long>()
    private val statusRetry = mutableSetOf<String>()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun links(): List<AppLink> {
        linkVersion
        return directory.all()
    }

    fun selected(): AppLink? {
        val all = links()
        return selectedId?.let { id -> all.firstOrNull { it.id == id } } ?: all.firstOrNull()
    }

    fun desk(id: String): AppDesk = desks.getOrPut(id) { AppDesk() }

    fun ensureConnected() {
        directory.all().filter { online[it.id] != true }.forEach { connect(it) }
        selected()?.let { if (online[it.id] == true) refreshApp(it.id) }
    }

    fun openScan() {
        scanning = true
        scanNotice = ""
    }

    fun closeScan() {
        scanning = false
        pairing = false
    }

    fun select(id: String) {
        if (!directory.select(id)) return
        selectedId = id
        persist()
        refreshApp(id)
    }

    fun showTab(id: String, tab: String) {
        val desk = desk(id)
        desk.tab = tab
        desk.notice = ""
        refreshApp(id)
    }

    fun refreshCurrent(id: String) {
        val desk = desk(id)
        desk.notice = ""
        if (online[id] != true) {
            directory.all().firstOrNull { it.id == id }?.let { connect(it) }
            return
        }
        refreshApp(id)
        val session = desk.openSession
        if (session != null && session.id.isNotEmpty()) {
            if (desk.turns.isEmpty()) desk.turnsLoading = true
            sendApp(id, "messages", sessionId = session.id, profile = desk.profile, projectId = desk.openProject?.id.orEmpty(), quiet = true)
        }
    }

    fun openSession(id: String, session: AppSession) {
        val desk = desk(id)
        if (chatEntranceClosed(desk.installed)) {
            desk.notice = "这台电脑没有 Hermes"
            return
        }
        desk.openSession = session
        desk.turns = emptyList()
        desk.turnsLoading = true
        desk.notice = ""
        sendApp(id, "messages", sessionId = session.id, profile = desk.profile, projectId = desk.openProject?.id.orEmpty())
        if (session.running || desk.inflight.containsKey(session.id)) watchRunning(id)
    }

    fun closeChat(id: String) {
        val desk = desk(id)
        desk.openSession = null
        desk.chatRequest = null
        desk.notice = ""
        val project = desk.openProject
        if (desk.canChat && project != null) {
            sendApp(id, "sessions", profile = desk.profile, projectId = project.id, quiet = true)
        }
        if (desk.inflight.isNotEmpty() || desk.sessions.any { it.running }) watchRunning(id)
    }

    fun closeProject(id: String) {
        val desk = desk(id)
        desk.openProject = null
        desk.sessions = emptyList()
        desk.sessionsLoaded = false
        clearTree(desk)
        desk.notice = ""
    }

    fun selectProfile(id: String, name: String) {
        val desk = desk(id)
        if (!desk.canChat || desk.profile == name) return
        desk.openProject = null
        desk.sessions = emptyList()
        desk.sessionsLoaded = false
        clearTree(desk)
        desk.projects = emptyList()
        desk.projectsLoaded = false
        desk.notice = ""
        sendApp(id, "profile_select", profile = name, quiet = true)
    }

    fun openProject(id: String, project: AppProject) {
        val desk = desk(id)
        if (!desk.canChat) {
            desk.notice = hermesClosedText(desk)
            return
        }
        desk.openProject = project
        desk.sessions = emptyList()
        desk.sessionsLoaded = false
        clearTree(desk)
        desk.notice = ""
        sendApp(id, "sessions", profile = desk.profile, projectId = project.id, quiet = true)
        loadTree(id, desk)
    }

    fun browseFolders(id: String, path: String) {
        val desk = desk(id)
        desk.folderPath = path
        desk.folders = emptyList()
        desk.foldersLoading = true
        desk.foldersNotice = ""
        if (sendApp(id, "folders", path = path, quiet = true) == null) {
            desk.foldersLoading = false
            desk.foldersNotice = "这台 Mac 还没连上，正在重连…"
        }
    }

    fun createProject(id: String, name: String, path: String) {
        val desk = desk(id)
        if (desk.creating) return
        if (!desk.canChat) {
            desk.notice = hermesClosedText(desk)
            return
        }
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            desk.notice = "请输入项目名称"
            return
        }
        desk.notice = ""
        if (sendApp(id, "project_create", title = trimmed, profile = desk.profile, path = path) != null) {
            desk.creating = true
        }
    }

    fun beginDraft(id: String) {
        val desk = desk(id)
        if (desk.sending || desk.creating) return
        if (!desk.canChat) {
            desk.notice = hermesClosedText(desk)
            return
        }
        if (desk.openProject == null) {
            desk.notice = "先进入一个项目"
            return
        }
        desk.openSession = AppSession("", "新会话")
        desk.turns = emptyList()
        desk.turnsLoading = false
        desk.chatRequest = null
        desk.drafting = false
        desk.pendingText = ""
        desk.notice = ""
    }

    fun renameSession(id: String, title: String) {
        val desk = desk(id)
        val session = desk.openSession ?: return
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            desk.notice = "请输入会话标题"
            return
        }
        if (session.id.isEmpty()) {
            desk.openSession = session.copy(title = trimmed)
            desk.notice = ""
            return
        }
        sendApp(id, "session_rename", sessionId = session.id, title = trimmed, profile = desk.profile, projectId = desk.openProject?.id.orEmpty())
    }

    fun deleteSession(id: String) {
        val desk = desk(id)
        val session = desk.openSession ?: return
        if (session.id.isEmpty()) {
            closeChat(id)
            return
        }
        sendApp(id, "session_delete", sessionId = session.id, profile = desk.profile, projectId = desk.openProject?.id.orEmpty(), quiet = true)
    }

    fun sendChat(id: String, text: String): Boolean {
        val desk = desk(id)
        val session = desk.openSession ?: return false
        val trimmed = text.trim()
        if (trimmed.isEmpty() || desk.sending) return false
        if (!desk.canChat) {
            desk.notice = hermesClosedText(desk)
            return false
        }
        val project = desk.openProject ?: return false
        if (session.id.isEmpty()) {
            desk.turns = desk.turns + AppTurn("user", trimmed)
            desk.pendingText = trimmed
            desk.drafting = true
            desk.notice = ""
            val requestId = sendApp(
                id,
                "session_create",
                title = if (session.title == "新会话") "" else session.title,
                profile = desk.profile,
                projectId = project.id,
            ) ?: run {
                desk.drafting = false
                desk.turns = desk.turns.dropLast(1)
                desk.restoreDraft = trimmed
                desk.pendingText = ""
                return false
            }
            desk.chatRequest = requestId
            return true
        }
        val requestId = sendApp(
            id,
            "chat",
            sessionId = session.id,
            text = trimmed,
            profile = desk.profile,
            projectId = project.id,
        ) ?: return false
        desk.turns = desk.turns + AppTurn("user", trimmed)
        desk.pendingText = trimmed
        desk.inflight = desk.inflight + (session.id to requestId)
        desk.chatRequest = requestId
        desk.sessions = desk.sessions.map { if (it.id == session.id) it.copy(running = true) else it }
        desk.openSession = session.copy(running = true)
        desk.notice = ""
        watchRunning(id)
        return true
    }

    fun sendWithAttachments(
        id: String,
        text: String,
        files: List<ChatAttachment>,
        onFinished: (Boolean, List<String?>) -> Unit,
    ): Boolean {
        val desk = desk(id)
        if (desk.sending || !attachmentBusy.add(id)) return false
        if (!desk.canChat) {
            attachmentBusy.remove(id)
            desk.notice = hermesClosedText(desk)
            return false
        }
        if (desk.openSession == null || desk.openProject == null) {
            attachmentBusy.remove(id)
            return false
        }
        if (files.isEmpty()) {
            attachmentBusy.remove(id)
            onFinished(sendChat(id, text), emptyList())
            return true
        }
        uploadAt(id, text, files, 0, MutableList(files.size) { files[it].landed }, onFinished)
        return true
    }

    private fun uploadAt(
        id: String,
        text: String,
        files: List<ChatAttachment>,
        index: Int,
        paths: MutableList<String?>,
        onFinished: (Boolean, List<String?>) -> Unit,
    ) {
        if (index >= files.size) {
            attachmentBusy.remove(id)
            val ready = paths.mapNotNull { it?.takeIf(String::isNotBlank) }
            if (ready.size != files.size) {
                onFinished(false, paths.toList())
                return
            }
            val composed = hermesTextWithPaths(text, ready)
            if (composed == null) {
                desk(id).notice = "附上的路径太长了"
                onFinished(false, paths.toList())
                return
            }
            onFinished(sendChat(id, composed), paths.toList())
            return
        }
        if (!paths[index].isNullOrBlank()) {
            uploadAt(id, text, files, index + 1, paths, onFinished)
            return
        }
        sendProjectFile(id, files[index]) { saved ->
            if (saved.isNullOrBlank()) {
                attachmentBusy.remove(id)
                onFinished(false, paths.toList())
                return@sendProjectFile
            }
            paths[index] = saved
            uploadAt(id, text, files, index + 1, paths, onFinished)
        }
    }

    private fun sendProjectFile(id: String, file: ChatAttachment, onResult: (String?) -> Unit) {
        val socket = sockets[id]
        val project = desk(id).openProject
        if (socket == null || online[id] != true || project == null) {
            desk(id).notice = "这台 Mac 还没连上，正在重连…"
            onResult(null)
            return
        }
        if (file.bytes.isEmpty()) {
            desk(id).notice = "这个文件是空的"
            onResult(null)
            return
        }
        val requestId = UUID.randomUUID().toString()
        fileWait[requestId] = FileWait(id, onResult)
        val body = JSONObject()
            .put("op", "project_file")
            .put("request_id", requestId)
            .put("name", file.name)
            .put("size", file.bytes.size)
            .put("kind", file.kind)
            .put("profile", desk(id).profile)
            .put("project_id", project.id)
        val queued = socket.send(body.toString()) && socket.send(file.bytes.toByteString())
        if (!queued) {
            fileWait.remove(requestId)
            desk(id).notice = "文件没有送出去"
            onResult(null)
            return
        }
        main.postDelayed({
            val wait = fileWait.remove(requestId) ?: return@postDelayed
            desk(wait.linkId).notice = "文件没有保存成功"
            wait.callback(null)
        }, FILE_TIMEOUT_MS)
    }

    fun refreshApp(id: String) {
        sendApp(id, "status", quiet = true)
    }

    fun submitCode(raw: String) {
        if (pairing) return
        val payload = parsePairingPayload(raw)
        if (payload == null) {
            scanNotice = "这不是 Tocode 的配对二维码"
            return
        }
        pairing = true
        scanNotice = ""
        worker.execute {
            runCatching {
                client.redeem(payload.relay, payload.code, phone.androidId, phone.androidName)
            }.onSuccess { link ->
                main.post {
                    directory.upsert(link)
                    directory.select(link.id)
                    selectedId = link.id
                    scanning = false
                    pairing = false
                    persist()
                    connect(link)
                }
            }.onFailure {
                main.post {
                    pairing = false
                    scanNotice = "绑定失败：二维码可能已过期，请在 Mac 上重新生成"
                }
            }
        }
    }

    fun send(action: String, title: String, target: JSONObject? = null) {
        val link = selected() ?: return
        deliver(link.id, action, title, target)
    }

    fun refreshDesktops(id: String) {
        deliver(id, "desktops", "桌面与窗口", null, quiet = true)
    }

    fun refreshButtons(id: String) {
        deliver(id, "remote-buttons", "遥控按钮", null, quiet = true)
    }

    fun pressButton(id: String, button: RemoteButton) {
        deliver(id, "remote-press", button.name, JSONObject().put("button_id", button.id))
    }

    fun switchDesktop(id: String, space: RemoteSpace) {
        val target = JSONObject()
            .put("display_id", space.displayId)
            .put("space_id", space.spaceId)
        deliver(id, "desktop-switch", space.title, target)
    }

    fun switchWindow(id: String, space: RemoteSpace, window: RemoteWindow) {
        val target = JSONObject()
            .put("display_id", space.displayId)
            .put("space_id", space.spaceId)
            .put("window_id", window.windowId)
            .put("pid", window.pid)
        deliver(id, "desktop-switch", window.title, target)
    }

    private fun deliver(id: String, action: String, title: String, target: JSONObject?, quiet: Boolean = false) {
        if (directory.all().none { it.id == id }) return
        if (pending[id] != null) {
            if (quiet) followUp[id] = { deliver(id, action, title, target, quiet = true) }
            return
        }
        val socket = sockets[id]
        if (!quiet) lastAction[id] = title
        if (socket == null || online[id] != true) {
            if (quiet) {
                val note = "这台 Mac 还没连上"
                if (action == "remote-buttons") buttonNotice[id] = note else desktopNotice[id] = note
            } else {
                lastResult[id] = "这台 Mac 还没连上"
                lastOk[id] = false
            }
            return
        }
        val requestId = UUID.randomUUID().toString()
        pending[id] = requestId
        pendingAction[id] = action
        if (action == "remote-press") pressedButton[id] = target?.optString("button_id").orEmpty()
        if (!quiet) {
            lastResult[id] = "正在执行…"
            lastOk.remove(id)
        }
        val body = JSONObject()
            .put("op", "command")
            .put("link_id", id)
            .put("request_id", requestId)
            .put("action", action)
        if (target != null) body.put("target", target)
        socket.send(body.toString())
        main.postDelayed({
            if (pending[id] == requestId) {
                val timedOut = pendingAction[id]
                pending.remove(id)
                pendingAction.remove(id)
                pressedButton.remove(id)
                when (timedOut) {
                    "desktops" -> desktopNotice[id] = "Mac 没有回应，请稍后再试"
                    "remote-buttons" -> buttonNotice[id] = "Mac 没有回应，请稍后再试"
                    else -> {
                        lastResult[id] = "Mac 没有回应，请稍后再试"
                        lastOk[id] = false
                    }
                }
                followUp.remove(id)?.invoke()
            }
        }, COMMAND_TIMEOUT_MS)
    }

    fun unlink(id: String) {
        val link = directory.all().firstOrNull { it.id == id } ?: return
        sockets.remove(id)?.close(1000, "unlink")
        forget(id)
        worker.execute { runCatching { client.delete(link) } }
    }

    fun openPreview(id: String, entry: AppEntry) {
        val desk = desk(id)
        val project = desk.openProject
        if (!desk.canChat || project == null) {
            desk.notice = if (project == null) "先进入一个项目" else hermesClosedText(desk)
            return
        }
        desk.preview = AppPreview(path = entry.path, name = entry.name, loading = true)
        val request = sendApp(
            id,
            "preview",
            profile = desk.profile,
            projectId = project.id,
            path = entry.path,
            quiet = true,
        )
        if (request == null) {
            desk.preview = desk.preview?.copy(loading = false, notice = "这台 Mac 还没连上，正在重连…")
        }
    }

    fun closePreview(id: String) {
        desk(id).preview = null
    }

    fun loadMorePreview(id: String) {
        val desk = desk(id)
        val preview = desk.preview ?: return
        val project = desk.openProject ?: return
        if (preview.loading || preview.loadingMore || !preview.truncated) return
        desk.preview = preview.copy(loadingMore = true)
        val request = sendApp(
            id,
            "preview",
            profile = desk.profile,
            projectId = project.id,
            path = preview.path,
            offset = preview.next,
            quiet = true,
        )
        if (request == null) desk.preview = preview.copy(loadingMore = false)
    }

    fun openDirectory(id: String, path: String) {
        val desk = desk(id)
        desk.directoryPath = path
        loadFolder(id, desk, path, 0)
    }

    fun loadMoreDirectory(id: String) {
        val desk = desk(id)
        val path = desk.directoryPath
        val page = desk.folderPages[path] ?: return
        if (!page.loaded || !page.truncated || page.loadingOffset != null) return
        loadFolder(id, desk, path, page.next)
    }

    private val runPolls = mutableSetOf<String>()

    private fun watchRunning(linkId: String) {
        if (!runPolls.add(linkId)) return
        main.postDelayed({
            runPolls.remove(linkId)
            val desk = desks[linkId] ?: return@postDelayed
            val session = desk.openSession?.takeIf { it.id.isNotEmpty() && (desk.inflight.containsKey(it.id) || it.running) }
            val listRunning = desk.openSession == null && (desk.inflight.isNotEmpty() || desk.sessions.any { it.running })
            if (session == null && !listRunning) return@postDelayed
            if (session != null) {
                sendApp(
                    linkId,
                    "messages",
                    sessionId = session.id,
                    profile = desk.profile,
                    projectId = desk.openProject?.id.orEmpty(),
                    quiet = true,
                )
            }
            if (listRunning) {
                val project = desk.openProject
                if (project != null) sendApp(linkId, "sessions", profile = desk.profile, projectId = project.id, quiet = true)
            }
            watchRunning(linkId)
        }, RUN_POLL_MS)
    }

    private fun clearTree(desk: AppDesk) {
        desk.entries = emptyList()
        desk.projectRoot = ""
        desk.folderPages = emptyMap()
        desk.treeNotice = ""
        desk.treeLoaded = false
        desk.preview = null
        desk.directoryPath = ""
    }

    private fun loadTree(id: String, desk: AppDesk) {
        val page = desk.folderPages[""]
        if (page?.loaded == true || page?.loadingOffset != null) return
        loadFolder(id, desk, "", 0)
    }

    private fun loadFolder(id: String, desk: AppDesk, path: String, offset: Int) {
        val project = desk.openProject ?: return
        val current = desk.folderPages[path]
        if (offset == 0 && (current?.loaded == true || current?.loadingOffset != null)) return
        if (offset > 0 && (current == null || !current.loaded || !current.truncated || current.loadingOffset != null || offset != current.next)) return
        desk.folderPages = desk.folderPages + (path to (current ?: FolderPage()).copy(loadingOffset = offset))
        val request = sendApp(
            id,
            "tree",
            profile = desk.profile,
            projectId = project.id,
            path = path,
            offset = offset,
            quiet = true,
        )
        if (request == null) {
            desk.treeLoaded = true
            desk.folderPages = desk.folderPages + (path to (desk.folderPages[path] ?: FolderPage()).copy(loadingOffset = null, loaded = offset > 0, truncated = false))
            if (offset == 0) desk.treeNotice = "这台 Mac 还没连上，正在重连…"
        }
    }

    private fun hermesClosedText(desk: AppDesk): String =
        if (desk.installed == false) "这台电脑没有 Hermes" else "这台电脑的 Hermes 没开"

    private fun forget(id: String) {
        online.remove(id)
        retryDelay.remove(id)
        pending.remove(id)
        pendingAction.remove(id)
        followUp.remove(id)
        pressedButton.remove(id)
        buttonLists.remove(id)
        buttonNotice.remove(id)
        desks.remove(id)
        directory.remove(id)
        if (selectedId == id) selectedId = directory.currentId
        persist()
    }

    private fun sendApp(
        id: String,
        kind: String,
        sessionId: String = "",
        title: String = "",
        text: String = "",
        profile: String = "",
        projectId: String = "",
        path: String = "",
        offset: Int = 0,
        quiet: Boolean = false,
    ): String? {
        if (directory.all().none { it.id == id }) return null
        val socket = sockets[id]
        if (socket == null || online[id] != true) {
            if (!quiet) desk(id).notice = "这台 Mac 还没连上，正在重连…"
            return null
        }
        val requestId = UUID.randomUUID().toString()
        appWait[requestId] = AppWait(id, kind, sessionId, projectId, path, offset)
        val body = JSONObject()
            .put("op", "app")
            .put("request_id", requestId)
            .put("kind", kind)
            .put("session_id", sessionId)
            .put("title", title)
            .put("text", text)
            .put("profile", profile)
            .put("project_id", projectId)
        if (path.isNotEmpty()) body.put("path", path)
        if (offset > 0) body.put("offset", offset)
        socket.send(body.toString())
        val timeout = if (kind == "chat") CHAT_TIMEOUT_MS else APP_TIMEOUT_MS
        main.postDelayed({
            val wait = appWait.remove(requestId) ?: return@postDelayed
            failApp(wait, requestId, "Mac 没有回应，请稍后再试")
        }, timeout)
        return requestId
    }

    private fun connect(link: AppLink) {
        sockets.remove(link.id)?.close(1000, "reconnect")
        online.remove(link.id)
        var socket: WebSocket? = null
        socket = client.open(link.relay, link.token, { message ->
            main.post { if (sockets[link.id] === socket) handle(link.id, message) }
        }) { connected ->
            main.post {
                if (sockets[link.id] !== socket) return@post
                online[link.id] = connected
                if (connected) {
                    retryDelay.remove(link.id)
                    refreshApp(link.id)
                } else {
                    dropWaits(link.id)
                    scheduleRetry(link, socket)
                }
            }
        }
        sockets[link.id] = socket
    }

    private fun scheduleRetry(link: AppLink, failed: WebSocket?) {
        val delay = retryDelay[link.id] ?: RETRY_START_MS
        retryDelay[link.id] = (delay * 2).coerceAtMost(RETRY_MAX_MS)
        main.postDelayed({
            if (sockets[link.id] === failed && directory.all().any { it.id == link.id }) connect(link)
        }, delay)
    }

    private fun scheduleStatusRetry(linkId: String) {
        if (!statusRetry.add(linkId)) return
        main.postDelayed({
            statusRetry.remove(linkId)
            if (desks[linkId]?.macOffline == true && online[linkId] == true) refreshApp(linkId)
        }, STATUS_RETRY_MS)
    }

    private fun dropWaits(linkId: String) {
        val lost = appWait.filterValues { it.linkId == linkId }
        lost.forEach { (requestId, wait) ->
            appWait.remove(requestId)
            failApp(wait, requestId, "和这台 Mac 的连接断开了")
        }
        val lostFiles = fileWait.filterValues { it.linkId == linkId }.keys.toList()
        lostFiles.forEach { requestId ->
            fileWait.remove(requestId)?.callback?.invoke(null)
        }
        if (lostFiles.isNotEmpty()) desk(linkId).notice = "和这台 Mac 的连接断开了"
        followUp.remove(linkId)
        pressedButton.remove(linkId)
        if (pending.remove(linkId) != null) {
            pendingAction.remove(linkId)
            lastResult[linkId] = "和这台 Mac 的连接断开了"
            lastOk[linkId] = false
        }
    }

    private fun handle(linkId: String, message: JSONObject) {
        when (message.optString("op")) {
            "ready" -> refreshApp(linkId)
            "app_result" -> applyApp(linkId, message)
            "project_file_result" -> {
                val requestId = message.optString("request_id")
                val wait = fileWait.remove(requestId) ?: return
                val path = message.optString("path")
                if (message.optBoolean("ok") && path.isNotBlank()) {
                    wait.callback(path)
                } else {
                    desk(wait.linkId).notice = message.optString("text").ifBlank { "文件没有保存成功" }
                    wait.callback(null)
                }
            }
            "result" -> {
                val requestId = message.optString("request_id")
                if (requestId.isNotBlank() && pending[linkId] != requestId) return
                val action = pendingAction[linkId]
                pending.remove(linkId)
                pendingAction.remove(linkId)
                pressedButton.remove(linkId)
                val ok = message.optBoolean("ok")
                if (action != "desktops" && action != "remote-buttons") {
                    lastResult[linkId] = message.optString("text").ifBlank { if (ok) "已完成" else "失败" }
                    lastOk[linkId] = ok
                }
                if (action == "desktops") {
                    val body = message.optJSONObject("body")
                    if (ok && body != null) {
                        desktopLists[linkId] = parseRemoteDisplays(body)
                        desktopNotice[linkId] = ""
                    } else {
                        desktopNotice[linkId] = message.optString("text").ifBlank { "无法读取桌面" }
                    }
                }
                if (action == "remote-buttons") {
                    val body = message.optJSONObject("body")
                    if (ok && body != null) {
                        buttonLists[linkId] = parseRemoteButtons(body)
                        buttonNotice[linkId] = ""
                    } else {
                        buttonNotice[linkId] = message.optString("text").ifBlank { "无法读取按钮" }
                    }
                }
                if (action == "desktop-switch") refreshDesktops(linkId)
                followUp.remove(linkId)?.invoke()
            }
            "error" -> {
                val requestId = message.optString("request_id")
                val text = message.optString("message").ifBlank { "失败" }
                val file = if (requestId.isNotBlank()) fileWait.remove(requestId) else null
                if (file != null) {
                    desk(file.linkId).notice = text
                    file.callback(null)
                    return
                }
                if (requestId.isBlank()) return
                val wait = appWait.remove(requestId)
                if (wait != null) {
                    failApp(wait, requestId, text)
                } else if (pending[linkId] != null) {
                    pending.remove(linkId)
                    pendingAction.remove(linkId)
                    pressedButton.remove(linkId)
                    lastResult[linkId] = text
                    lastOk[linkId] = false
                    followUp.remove(linkId)?.invoke()
                }
            }
            "link_revoked" -> {
                sockets.remove(linkId)?.close(1000, "revoked")
                forget(linkId)
            }
        }
    }

    private fun failApp(wait: AppWait, requestId: String, text: String) {
        val desk = desk(wait.linkId)
        when (wait.kind) {
            "status" -> {
                desk.macOffline = true
                scheduleStatusRetry(wait.linkId)
                return
            }
            "session_create", "project_create" -> {
                desk.creating = false
                if (wait.kind == "session_create" && desk.drafting) {
                    desk.drafting = false
                    if (desk.chatRequest == requestId) desk.chatRequest = null
                    val last = desk.turns.lastOrNull()
                    if (last?.role == "user" && last.text == desk.pendingText) desk.turns = desk.turns.dropLast(1)
                    desk.restoreDraft = desk.pendingText
                    desk.pendingText = ""
                }
            }
            "messages" -> desk.turnsLoading = false
            "tree" -> {
                if (desk.openProject?.id == wait.projectId) {
                    desk.treeLoaded = true
                    val page = desk.folderPages[wait.path] ?: FolderPage()
                    if (page.loadingOffset == wait.offset) {
                        desk.folderPages = desk.folderPages + (wait.path to page.copy(loadingOffset = null, loaded = true, truncated = false))
                        desk.treeNotice = text
                    }
                }
                if (wait.offset == 0) desk.notice = text
                return
            }
            "preview" -> {
                val current = desk.preview
                if (current != null && current.path == wait.path) {
                    desk.preview = current.copy(loading = false, loadingMore = false, truncated = false, notice = text)
                }
                return
            }
            "folders" -> {
                desk.foldersLoading = false
                desk.foldersNotice = text
                return
            }
            "chat" -> if (desk.inflight[wait.sessionId] == requestId || desk.chatRequest == requestId) {
                if (desk.inflight[wait.sessionId] == requestId) desk.inflight = desk.inflight - wait.sessionId
                if (desk.chatRequest == requestId) desk.chatRequest = null
                desk.sessions = desk.sessions.map { if (it.id == wait.sessionId) it.copy(running = false) else it }
                if (desk.openSession?.id == wait.sessionId) {
                    desk.openSession = desk.openSession?.copy(running = false)
                    desk.pendingText = ""
                    sendApp(
                        wait.linkId,
                        "messages",
                        sessionId = wait.sessionId,
                        profile = desk.profile,
                        projectId = desk.openProject?.id.orEmpty(),
                        quiet = true,
                    )
                }
            }
        }
        desk.notice = text
    }

    private fun applyApp(linkId: String, message: JSONObject) {
        val requestId = message.optString("request_id")
        val wait = appWait.remove(requestId) ?: return
        val ok = message.optBoolean("ok")
        val text = message.optString("text")
        if (!ok) {
            failApp(wait, requestId, text.ifBlank { "失败" })
            return
        }
        val desk = desk(linkId)
        val body = message.optJSONObject("body") ?: JSONObject()
        when (wait.kind) {
            "status" -> {
                desk.macOffline = false
                desk.installed = body.optBoolean("hermes_installed")
                desk.running = body.optBoolean("hermes_running")
                val names = parseNames(body)
                if (names.isNotEmpty()) desk.profiles = names
                val profile = body.optString("profile").ifBlank { "default" }
                if (desk.profile != profile) {
                    desk.profile = profile
                    desk.openProject = null
                    desk.sessions = emptyList()
                    desk.sessionsLoaded = false
                    clearTree(desk)
                }
                if (desk.canChat) {
                    sendApp(linkId, "projects", profile = desk.profile, quiet = true)
                    desk.openProject?.let { project ->
                        sendApp(linkId, "sessions", profile = desk.profile, projectId = project.id, quiet = true)
                        loadTree(linkId, desk)
                    }
                } else {
                    desk.projectsLoaded = true
                    desk.sessionsLoaded = true
                }
            }
            "profile_select" -> {
                desk.profile = body.optString("profile").ifBlank { desk.profile }
                val names = parseNames(body)
                if (names.isNotEmpty()) desk.profiles = names
                desk.openProject = null
                desk.sessions = emptyList()
                desk.sessionsLoaded = false
                clearTree(desk)
                desk.projects = parseProjects(body)
                desk.projectsLoaded = true
                if (text.isNotBlank()) desk.notice = text
            }
            "projects" -> {
                desk.projects = parseProjects(body)
                desk.projectsLoaded = true
            }
            "project_create" -> {
                desk.creating = false
                desk.notice = ""
                val row = body.optJSONObject("project")
                val id = row?.optString("id").orEmpty()
                if (id.isNotBlank()) {
                    val project = AppProject(id, row?.optString("name").orEmpty().ifBlank { "未命名项目" })
                    desk.projects = listOf(project) + desk.projects.filter { it.id != id }
                    openProject(linkId, project)
                }
            }
            "sessions" -> {
                desk.sessions = parseSessions(body).map { row ->
                    if (desk.inflight.containsKey(row.id)) row.copy(running = true) else row
                }
                desk.sessionsLoaded = true
                if (desk.sessions.any { it.running } || desk.inflight.isNotEmpty()) watchRunning(linkId)
            }
            "session_create" -> {
                desk.creating = false
                val row = body.optJSONObject("session")
                val createdId = row?.optString("id").orEmpty()
                if (createdId.isNotBlank()) {
                    val kept = desk.openSession?.title?.takeIf { it.isNotBlank() } ?: "新会话"
                    val session = AppSession(createdId, row?.optString("title").orEmpty().ifBlank { kept })
                    desk.sessions = listOf(session) + desk.sessions.filter { it.id != createdId }
                    if (desk.drafting) {
                        desk.drafting = false
                        desk.openSession = session.copy(running = true)
                        val pending = desk.pendingText
                        val requestId = sendApp(
                            linkId,
                            "chat",
                            sessionId = createdId,
                            text = pending,
                            profile = desk.profile,
                            projectId = desk.openProject?.id.orEmpty(),
                        )
                        desk.chatRequest = requestId
                        if (requestId != null) {
                            desk.inflight = desk.inflight + (createdId to requestId)
                            desk.sessions = desk.sessions.map { if (it.id == createdId) it.copy(running = true) else it }
                            watchRunning(linkId)
                        }
                        if (requestId == null) {
                            desk.restoreDraft = pending
                            desk.pendingText = ""
                            val last = desk.turns.lastOrNull()
                            if (last?.role == "user" && last.text == pending) desk.turns = desk.turns.dropLast(1)
                        }
                    } else {
                        openSession(linkId, session)
                    }
                }
            }
            "session_delete" -> {
                desk.sessions = desk.sessions.filter { it.id != wait.sessionId }
                if (desk.openSession?.id == wait.sessionId) closeChat(linkId)
            }
            "session_rename" -> {
                val row = body.optJSONObject("session")
                val renamedId = row?.optString("id").orEmpty().ifBlank { wait.sessionId }
                val renamed = row?.optString("title").orEmpty()
                if (renamed.isNotBlank()) {
                    if (desk.openSession?.id == renamedId) desk.openSession = desk.openSession?.copy(title = renamed)
                    desk.sessions = desk.sessions.map { if (it.id == renamedId) it.copy(title = renamed) else it }
                }
            }
            "messages" -> if (desk.openSession?.id == wait.sessionId) {
                val running = body.optBoolean("running") || desk.inflight.containsKey(wait.sessionId)
                var turns = parseTurns(body)
                val pending = desk.pendingText
                if (running && pending.isNotBlank() && turns.none { it.role == "user" && it.text == pending }) {
                    turns = turns + AppTurn("user", pending)
                }
                desk.turns = turns
                desk.turnsLoading = false
                desk.openSession = desk.openSession?.copy(running = running)
                desk.sessions = desk.sessions.map { if (it.id == wait.sessionId) it.copy(running = running) else it }
                if (running) watchRunning(linkId)
            }
            "chat" -> if (desk.inflight[wait.sessionId] == requestId || desk.chatRequest == requestId) {
                if (desk.inflight[wait.sessionId] == requestId) desk.inflight = desk.inflight - wait.sessionId
                if (desk.chatRequest == requestId) desk.chatRequest = null
                desk.pendingText = ""
                val title = body.optString("title")
                desk.sessions = desk.sessions.map { row ->
                    if (row.id != wait.sessionId) row
                    else row.copy(title = title.ifBlank { row.title }, running = false)
                }
                if (desk.openSession?.id == wait.sessionId) {
                    val open = desk.openSession
                    if (open != null && title.isNotBlank()) desk.openSession = open.copy(title = title, running = false)
                    else desk.openSession = open?.copy(running = false)
                    sendApp(
                        linkId,
                        "messages",
                        sessionId = wait.sessionId,
                        profile = desk.profile,
                        projectId = desk.openProject?.id.orEmpty(),
                        quiet = true,
                    )
                }
            }
            "preview" -> {
                val current = desk.preview ?: return
                if (current.path != wait.path) return
                if (current.loading && wait.offset > 0) return
                val chunk = body.optString("content")
                val format = body.optString("format").ifBlank { "text" }
                if (format == "image") {
                    desk.preview = current.copy(
                        loading = false,
                        loadingMore = false,
                        name = body.optString("name").ifBlank { current.name },
                        format = "image",
                        content = chunk,
                        next = 0,
                        truncated = false,
                        notice = "",
                    )
                    return
                }
                val next = body.optInt("next", wait.offset)
                if (wait.offset > 0 && (chunk.isEmpty() || next <= wait.offset)) {
                    desk.preview = current.copy(loading = false, loadingMore = false, truncated = false)
                    return
                }
                val combined = if (wait.offset > 0) current.content + chunk else chunk
                desk.preview = current.copy(
                    loading = false,
                    loadingMore = false,
                    name = body.optString("name").ifBlank { current.name },
                    format = body.optString("format").ifBlank { "text" },
                    content = combined,
                    next = next,
                    truncated = body.optBoolean("truncated"),
                    notice = if (wait.offset > 0) "" else text,
                )
            }
            "tree" -> if (desk.openProject?.id == wait.projectId) {
                val page = desk.folderPages[wait.path] ?: FolderPage()
                if (page.loadingOffset != wait.offset) return
                val incoming = parseEntries(body)
                val next = body.optInt("next", wait.offset)
                val advanced = wait.offset > 0 && incoming.isNotEmpty() && next > wait.offset
                if (wait.offset > 0 && !advanced) {
                    desk.folderPages = desk.folderPages + (wait.path to page.copy(loadingOffset = null, loaded = true, truncated = false))
                    return
                }
                val root = body.optString("root")
                if (root.startsWith("/") && !root.contains("..")) desk.projectRoot = root.trimEnd('/')
                desk.entries = mergeFolderEntries(desk.entries, wait.path, incoming, wait.offset)
                desk.folderPages = desk.folderPages + (
                    wait.path to FolderPage(
                        next = next,
                        truncated = body.optBoolean("truncated") && next > wait.offset,
                        loaded = true,
                    )
                )
                desk.treeLoaded = true
                desk.treeNotice = ""
            }
            "folders" -> {
                desk.folderPath = body.optString("path")
                desk.folders = parseFolders(body)
                desk.foldersLoading = false
                desk.foldersNotice = text
            }
        }
    }

    private fun persist() {
        phone = phone.copy(links = directory.all(), currentId = selectedId)
        disk.save(phone)
        linkVersion += 1
    }

    private companion object {
        const val COMMAND_TIMEOUT_MS = 20_000L
        const val APP_TIMEOUT_MS = 30_000L
        const val FILE_TIMEOUT_MS = 120_000L
        const val CHAT_TIMEOUT_MS = 11 * 60_000L
        const val RUN_POLL_MS = 3_000L
        const val RETRY_START_MS = 2_000L
        const val RETRY_MAX_MS = 30_000L
        const val STATUS_RETRY_MS = 5_000L
    }
}
