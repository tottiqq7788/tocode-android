package app.tocode.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.File
import okio.ByteString.Companion.toByteString
import java.util.UUID

private const val MAX_FILE_BYTES = 20 * 1024 * 1024

private fun fileKind(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return if (ext in setOf("jpg", "jpeg", "png", "gif", "webp")) "image" else "file"
}

private val ComposerShape = RoundedCornerShape(28.dp)
private val UserBubbleShape = RoundedCornerShape(20.dp)
private val NoticeShape = RoundedCornerShape(20.dp)

private sealed interface AppPage {
    data object Home : AppPage
    data class Chat(val id: String) : AppPage
    data object Scan : AppPage
}

class ChatController(
    val directory: LinkDirectory,
    val log: SessionLog,
    private val disk: LinkDisk,
    private val phone: PhoneState,
    private val client: RelayClient,
    private val sockets: MutableMap<String, okhttp3.WebSocket> = mutableMapOf(),
) {
    var notice: String = ""
        private set
    var onChanged: (() -> Unit)? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val online = mutableSetOf<String>()
    private val waiting = mutableSetOf<String>()

    fun isOnline(id: String): Boolean = id in online

    fun isWaiting(id: String): Boolean = id in waiting

    fun file(relativePath: String): File = disk.file(relativePath)

    fun dismissNotice() {
        if (notice.isEmpty()) return
        notice = ""
        onChanged?.invoke()
    }

    fun select(id: String) {
        if (directory.select(id)) persist()
    }

    fun remove(id: String) {
        sockets.remove(id)?.close(1000, null)
        online.remove(id)
        waiting.remove(id)
        directory.remove(id)
        persist()
    }

    fun send(text: String) {
        val link = directory.current() ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val line = ChatLine(outgoing = true, text = trimmed)
        log.append(link.id, line)
        disk.appendHistory(link.id, line)
        val socket = sockets[link.id]
        if (socket == null || link.id !in online) {
            appendNotice(link.id, "这条关联还没连上")
            return
        }
        val payload = org.json.JSONObject()
            .put("op", "text")
            .put("link_id", link.id)
            .put("message_id", UUID.randomUUID().toString())
            .put("text", trimmed)
        if (!socket.send(payload.toString())) {
            appendNotice(link.id, "这条关联还没连上")
            return
        }
        waiting.add(link.id)
        onChanged?.invoke()
    }

    fun sendFile(name: String, bytes: ByteArray, caption: String) {
        val link = directory.current() ?: return
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) {
            appendNotice(link.id, "只接受不超过 20MB 的单个文件")
            return
        }
        val kind = fileKind(name)
        val relative = disk.saveFile(link.id, name, bytes)
        val line = ChatLine(
            outgoing = true,
            text = caption.trim(),
            fileName = name,
            fileKind = kind,
            filePath = relative,
        )
        log.append(link.id, line)
        disk.appendHistory(link.id, line)
        val socket = sockets[link.id]
        if (socket == null || link.id !in online) {
            appendNotice(link.id, "这条关联还没连上")
            return
        }
        val payload = org.json.JSONObject()
            .put("op", "file")
            .put("link_id", link.id)
            .put("message_id", UUID.randomUUID().toString())
            .put("name", name)
            .put("size", bytes.size)
            .put("kind", kind)
            .put("text", caption.trim())
        if (!socket.send(payload.toString()) || !socket.send(bytes.toByteString())) {
            appendNotice(link.id, "这条关联还没连上")
            return
        }
        waiting.add(link.id)
        onChanged?.invoke()
    }

    fun redeem(raw: String, onDone: (String?) -> Unit) {
        val payload = parsePairingPayload(raw)
        if (payload == null) {
            notice = "这不是应用关联二维码"
            onDone(notice)
            return
        }
        Thread {
            try {
                val link = client.redeem(payload.relay, payload.code, phone.androidId, phone.androidName)
                main.post {
                    directory.upsert(link)
                    connect(link)
                    persist()
                    notice = ""
                    onDone(null)
                    onChanged?.invoke()
                }
            } catch (_: Exception) {
                main.post {
                    notice = "配对没有完成"
                    onDone(notice)
                    onChanged?.invoke()
                }
            }
        }.start()
    }

    fun connectAll() {
        directory.all().forEach(::connect)
    }

    private fun connect(link: AppLink) {
        sockets.remove(link.id)?.close(1000, null)
        lateinit var socket: okhttp3.WebSocket
        socket = client.open(
            relay = link.relay,
            token = link.token,
            onMessage = { message ->
                main.post {
                    if (sockets[link.id] !== socket) return@post
                    when (message.optString("op")) {
                        "reply" -> {
                            waiting.remove(link.id)
                            val text = message.optString("text")
                            if (text.isNotBlank()) {
                                val line = ChatLine(outgoing = false, text = text)
                                log.append(link.id, line)
                                disk.appendHistory(link.id, line)
                            }
                            onChanged?.invoke()
                        }
                        "error" -> appendNotice(link.id, message.optString("message", "没有送达"))
                    }
                }
            },
            onFile = { header, bytes ->
                main.post {
                    if (sockets[link.id] !== socket) return@post
                    waiting.remove(link.id)
                    val name = header.optString("name", "file")
                    val relative = disk.saveFile(link.id, name, bytes)
                    val line = ChatLine(
                        outgoing = false,
                        text = header.optString("text"),
                        fileName = name,
                        fileKind = header.optString("kind", fileKind(name)),
                        filePath = relative,
                    )
                    log.append(link.id, line)
                    disk.appendHistory(link.id, line)
                    onChanged?.invoke()
                }
            },
            onConnection = { up ->
                main.post {
                    if (sockets[link.id] !== socket) return@post
                    if (up) {
                        online.add(link.id)
                    } else {
                        online.remove(link.id)
                        waiting.remove(link.id)
                    }
                    onChanged?.invoke()
                }
            },
        )
        sockets[link.id] = socket
    }

    private fun appendNotice(linkId: String, text: String) {
        waiting.remove(linkId)
        val line = ChatLine(outgoing = false, text = text, notice = true)
        log.append(linkId, line)
        disk.appendHistory(linkId, line)
        onChanged?.invoke()
    }

    private fun persist() {
        disk.save(
            phone.copy(
                links = directory.all(),
                currentId = directory.currentId,
            )
        )
    }
}

@Composable
fun ChatScreen(controller: ChatController, revision: Int, onChanged: () -> Unit) {
    var page by remember { mutableStateOf<AppPage>(AppPage.Home) }
    val links = if (revision >= 0) controller.directory.all() else emptyList()
    when (val current = page) {
        AppPage.Home -> RoleHome(
            links = links,
            onOpen = { link ->
                controller.select(link.id)
                onChanged()
                page = AppPage.Chat(link.id)
            },
            onRemove = { link ->
                controller.remove(link.id)
                onChanged()
            },
            onAdd = {
                controller.dismissNotice()
                page = AppPage.Scan
            },
        )
        is AppPage.Chat -> RoleChat(
            controller = controller,
            revision = revision,
            linkId = current.id,
            onBack = { page = AppPage.Home },
            onChanged = onChanged,
        )
        AppPage.Scan -> ScanScreen(
            onCode = { raw ->
                controller.redeem(raw) { message ->
                    if (message == null) page = AppPage.Home
                    onChanged()
                }
            },
            onClose = {
                controller.dismissNotice()
                page = AppPage.Home
            },
            notice = if (revision >= 0) controller.notice else "",
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RoleHome(
    links: List<AppLink>,
    onOpen: (AppLink) -> Unit,
    onRemove: (AppLink) -> Unit,
    onAdd: () -> Unit,
) {
    var pending by remember { mutableStateOf<AppLink?>(null) }
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                shape = CircleShape,
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新增")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                "togent",
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 8.dp),
                color = colors.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            if (links.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("还没有角色", color = colors.onBackground, fontSize = 16.sp)
                    Text(
                        "扫 Mac 上的配对二维码",
                        color = colors.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(links, key = { it.id }) { link ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onOpen(link) },
                                    onLongClick = { pending = link },
                                )
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            Text(link.listTitle(), color = colors.onBackground, fontSize = 16.sp)
                            Text(
                                link.macName,
                                color = colors.onSurfaceVariant,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
    pending?.let { link ->
        ModalBottomSheet(onDismissRequest = { pending = null }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp)
                    .navigationBarsPadding(),
            ) {
                Text(
                    "解除和 ${link.listTitle()} 的关联？",
                    color = colors.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "之后这条角色不再收发",
                    color = colors.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "这部手机上的聊天记录还在",
                    color = colors.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { pending = null }) { Text("取消") }
                    TextButton(onClick = {
                        pending = null
                        onRemove(link)
                    }) { Text("解除") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleChat(
    controller: ChatController,
    revision: Int,
    linkId: String,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val link = if (revision >= 0) controller.directory.all().firstOrNull { it.id == linkId } else null
    LaunchedEffect(link) {
        if (link == null) onBack()
    }
    if (link == null) return
    val lines = controller.log.lines(link.id)
    val waiting = controller.isWaiting(link.id)
    val online = controller.isOnline(link.id)
    var draft by remember(link.id) { mutableStateOf("") }
    var attachment by remember(link.id) { mutableStateOf<PendingAttachment?>(null) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        } ?: uri.lastPathSegment ?: "附件"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) {
            controller.sendFile(name, bytes, "")
            onChanged()
            return@rememberLauncherForActivityResult
        }
        attachment = PendingAttachment(name, bytes)
    }
    val listState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme
    val title = link.listTitle()
    LaunchedEffect(lines.size, waiting) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, color = colors.onBackground, fontSize = 18.sp)
                        Text(
                            "${link.macName} · ${if (online) "已连接" else "未连接"}",
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                attachment?.let { pending ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            pending.name,
                            modifier = Modifier.weight(1f),
                            color = colors.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                        TextButton(onClick = { attachment = null }) { Text("取消") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { picker.launch("*/*") }) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "附件")
                    }
                    ComposerBar(
                        draft = draft,
                        placeholder = "和 $title 说一句话",
                        canSend = draft.isNotBlank() || attachment != null,
                        onDraftChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        onSend = {
                            val pending = attachment
                            val text = draft
                            if (pending != null) {
                                controller.sendFile(pending.name, pending.bytes, text)
                            } else {
                                controller.send(text)
                            }
                            draft = ""
                            attachment = null
                            onChanged()
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (lines.isEmpty() && !waiting) {
                Text(
                    "和 $title 说一句话。",
                    color = colors.onSurfaceVariant,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(lines) { line ->
                        TranscriptLine(line, controller)
                    }
                    if (waiting) {
                        item { Text("▍", color = colors.onBackground, fontSize = 16.sp) }
                    }
                }
            }
        }
    }
}

private data class PendingAttachment(val name: String, val bytes: ByteArray)

@Composable
private fun TranscriptLine(line: ChatLine, controller: ChatController) {
    when {
        line.notice -> NoticeLine(line.text)
        line.outgoing -> Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (line.filePath.isNotBlank()) {
                FileChip(line, controller)
            }
            if (line.text.isNotBlank()) UserBubble(line.text)
        }
        else -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (line.text.isNotBlank()) MarkdownText(line.text)
            if (line.filePath.isNotBlank()) FileChip(line, controller)
        }
    }
}

@Composable
private fun FileChip(line: ChatLine, controller: ChatController) {
    val context = LocalContext.current
    val file = controller.file(line.filePath)
    val open = {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(intent) }
        Unit
    }
    if (line.fileKind == "image") {
        val bitmap = remember(file.path) { BitmapFactory.decodeFile(file.path) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = line.fileName,
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = open),
            )
            return
        }
    }
    Text(
        line.fileName.ifBlank { "文件" },
        color = MaterialTheme.colorScheme.primary,
        fontSize = 16.sp,
        modifier = Modifier.clickable(onClick = open),
    )
}

@Composable
private fun UserBubble(text: String) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            color = colors.onPrimary,
            fontSize = 16.sp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(colors.primary, UserBubbleShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun NoticeLine(text: String) {
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        color = colors.onSurfaceVariant,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, NoticeShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun ComposerBar(
    draft: String,
    placeholder: String,
    canSend: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .padding(end = 16.dp, top = 8.dp, bottom = 12.dp)
            .border(1.dp, colors.outline.copy(alpha = 0.28f), ComposerShape)
            .background(colors.surface, ComposerShape)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 36.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (draft.isEmpty()) {
                Text(
                    placeholder,
                    color = colors.onSurfaceVariant,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.CenterVertically),
                textStyle = TextStyle(
                    color = colors.onSurface,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                cursorBrush = SolidColor(colors.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (canSend) colors.primary else colors.outline.copy(alpha = 0.18f))
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "发送",
                tint = if (canSend) colors.onPrimary else colors.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanScreen(
    onCode: (String) -> Unit,
    onClose: () -> Unit,
    notice: String,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var paste by remember { mutableStateOf("") }
    val analyzer = remember { QrAnalyzer() }
    analyzer.onCode = onCode
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("扫码关联", color = colors.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            if (!granted) {
                Text(
                    "需要相机才能扫码，也可以在下面粘贴配对内容。",
                    color = colors.onBackground,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("允许相机") }
                Spacer(Modifier.weight(1f))
            } else {
                AndroidView(
                    factory = { viewContext ->
                        val previewView = PreviewView(viewContext)
                        val future = ProcessCameraProvider.getInstance(viewContext)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder().build().also {
                                it.setAnalyzer(
                                    ContextCompat.getMainExecutor(viewContext),
                                    analyzer,
                                )
                            }
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                viewContext as LifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }, ContextCompat.getMainExecutor(viewContext))
                        previewView
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 12.dp),
                )
                Text(
                    "对准 Mac 上的配对二维码",
                    color = colors.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (notice.isNotBlank()) {
                Text(
                    notice,
                    color = colors.error,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            OutlinedTextField(
                value = paste,
                onValueChange = { paste = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                label = { Text("配对内容") },
            )
            Button(
                onClick = { onCode(paste) },
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            ) { Text("关联") }
        }
    }
}

private class QrAnalyzer : ImageAnalysis.Analyzer {
    var onCode: (String) -> Unit = {}
    private val scanner = BarcodeScanning.getClient()
    private var handled = false

    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null || handled) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val raw = codes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                if (!handled && raw.startsWith("tocode-app://pair?")) {
                    handled = true
                    onCode(raw)
                }
            }
            .addOnCompleteListener { image.close() }
    }
}
