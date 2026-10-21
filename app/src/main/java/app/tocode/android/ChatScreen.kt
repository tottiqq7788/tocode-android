package app.tocode.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

@Composable
fun SessionsTab(
    controller: RemoteController,
    link: AppLink,
    desk: AppDesk,
    online: Boolean?,
    onCreate: () -> Unit,
) {
    BackHandler(enabled = desk.openProject != null) { controller.closeProject(link.id) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HermesHero(desk, online, onCreate) }
        if (desk.notice.isNotBlank()) {
            item { Banner(desk.notice, Tone.Bad) }
        }
        if (desk.canChat) {
            val project = desk.openProject
            if (project == null) {
                item {
                    SectionTitle(
                        "${desk.profile} 的项目",
                        if (desk.projectsLoaded) "${desk.projects.size} 个" else "",
                        Modifier.padding(top = 8.dp),
                    )
                }
                when {
                    !desk.projectsLoaded -> item { LoadingState("正在读取项目…") }
                    desk.projects.isEmpty() -> item {
                        EmptyState(
                            icon = Icons.Outlined.FolderOpen,
                            title = "这个档案还没有项目",
                            subtitle = "点上方「新建项目」，从 Mac 主目录里选一个文件夹。",
                            tone = Tone.Good,
                        )
                    }
                    else -> items(desk.projects, key = { it.id }) { row ->
                        ProjectRow(row) { controller.openProject(link.id, row) }
                    }
                }
            } else {
                when {
                    !desk.sessionsLoaded -> item { LoadingState("正在读取会话…") }
                    desk.sessions.isEmpty() -> item {
                        EmptyState(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            title = "这个项目还没有会话",
                            subtitle = "再次点下方「会话」，开始第一段对话。",
                            tone = Tone.Good,
                        )
                    }
                    else -> items(desk.sessions, key = { it.id }) { session ->
                        SessionRow(
                            session,
                            running = session.running || desk.inflight.containsKey(session.id),
                        ) { controller.openSession(link.id, session) }
                    }
                }
            }
        } else if (desk.installed != null) {
            item {
                Panel(padding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                    ShortcutRow(Icons.Outlined.Tune, Tone.Info, "遥控这台 Mac", "切换桌面和窗口，以及你配好的按钮") {
                        controller.showTab(link.id, "buttons")
                    }
                }
            }
        } else if (online == false) {
            item {
                EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = "这台 Mac 暂时离线",
                    subtitle = "确认电脑上的 Tocode 正在运行，恢复后会自动重连。",
                    tone = Tone.Bad,
                    action = "立即重连",
                    onAction = { controller.refreshCurrent(link.id) },
                )
            }
        }
    }
}

@Composable
private fun ReadyCard(desk: AppDesk, onCreate: () -> Unit) {
    val sessions = when {
        desk.openProject == null -> "未选项目"
        desk.sessionsLoaded -> "${desk.sessions.size} 个会话"
        else -> "正在读取会话"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(HeroBrush)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(if (desk.running) "服务运行中" else "服务未启动", desk.running)
                GlassChip(sessions, desk.openProject != null)
            }
            if (desk.openProject == null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .clickable(enabled = !desk.creating, onClick = onCreate)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (desk.creating) {
                        CircularProgressIndicator(color = Pine, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = Pine, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (desk.creating) "正在创建…" else "新建项目", style = MaterialTheme.typography.labelLarge, color = Pine)
                }
            }
        }
    }
}

@Composable
private fun HermesHero(desk: AppDesk, online: Boolean?, onCreate: () -> Unit) {
    val ready = desk.canChat
    val (title, subtitle) = when {
        online == null && desk.installed == null -> "正在连接…" to "正在连接这台 Mac。"
        online == false && desk.installed == null -> "Mac 离线" to "恢复连接后会自动同步 Hermes 状态。"
        desk.macOffline -> "Mac 不在线" to "请确认 Mac 上的 Tocode 已打开，连上后会自动同步。"
        desk.installed == null -> "正在确认 Hermes…" to "马上就好。"
        desk.installed == false -> "这台电脑没有 Hermes" to "手机聊天和目录已关闭，遥控仍然可以用。"
        !desk.running -> "Hermes 没开" to "在 Mac 上打开「应用 → 网关链接」，连上后会自动同步。"
        else -> "" to ""
    }
    if (ready) {
        ReadyCard(desk, onCreate)
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (ready) HeroBrush else NightBrush)
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("HERMES", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f))
            if (desk.installed == true) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(if (desk.running) "服务运行中" else "服务未启动", desk.running)
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(icon: ImageVector, tone: Tone, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tone, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SlateLight)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = null, tint = SlateLight, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun GlassChip(label: String, good: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(if (good) Color(0xFF7CF0BF) else Color(0xFFFFC46B), 6.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
fun ProfilePicker(names: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("档案", selected)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            names.forEach { name ->
                val on = name == selected
                Text(
                    name,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (on) Pine else Paper)
                        .border(1.dp, if (on) Pine else Hairline, RoundedCornerShape(999.dp))
                        .clickable { onSelect(name) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) Color.White else Ink,
                )
            }
        }
    }
}

@Composable
private fun ProjectRow(project: AppProject, onClick: () -> Unit) {
    Panel(onClick = onClick, padding = PaddingValues(horizontal = 14.dp, vertical = 14.dp), radius = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(PineSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = Pine, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("轻点查看会话", style = MaterialTheme.typography.bodySmall, color = SlateLight)
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForwardIos,
                contentDescription = null,
                tint = SlateLight,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun SessionRow(session: AppSession, running: Boolean, onClick: () -> Unit) {
    Panel(onClick = onClick, padding = PaddingValues(horizontal = 14.dp, vertical = 14.dp), radius = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(PineSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    session.title.trim().take(1).ifBlank { "#" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Pine,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(session.title, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (running) "正在回复" else "轻点继续对话",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (running) Pine else SlateLight,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForwardIos,
                contentDescription = null,
                tint = SlateLight,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private val suggestions = listOf(
    "介绍一下这个项目的结构",
    "最近有哪些改动？",
    "帮我检查一下有没有明显的问题",
)

@Composable
fun ChatScreen(controller: RemoteController, link: AppLink, desk: AppDesk, session: AppSession) {
    var draft by rememberSaveable(session.id) { mutableStateOf("") }
    val attachments = remember(session.id) { mutableStateListOf<ChatAttachment>() }
    var uploading by remember(session.id) { mutableStateOf(false) }
    var pinned by remember(session.id) { mutableStateOf<Int?>(null) }
    val restore = desk.restoreDraft
    LaunchedEffect(restore) {
        if (restore.isNotEmpty()) {
            if (draft.isBlank()) draft = restore
            desk.restoreDraft = ""
        }
    }
    val online = controller.online[link.id]
    val pages = exchanges(chatBlocks(desk.turns))
    val pageCount = pages.size
    val pageIndex = visibleExchange(pageCount, pinned)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        takeAttachment(context, scope, uri, "image", attachments, desk)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        takeAttachment(context, scope, uri, "file", attachments, desk)
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var showFiles by rememberSaveable(session.id) { mutableStateOf(false) }
    val canSend = online == true && desk.canChat && !desk.sending
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(Canvas)) {
        ChatTopBar(
            title = session.title,
            subtitle = if (desk.sending) "Hermes 正在回复…" else "${link.macName} · ${linkState(online, desk).label}",
            onBack = { controller.closeChat(link.id) },
            onTitle = { managing = true },
            onDirectory = { showFiles = true },
        )
        val banner = when {
            desk.notice.isNotBlank() -> desk.notice to Tone.Bad
            online == false -> "这台 Mac 离线了，正在重连…" to Tone.Bad
            desk.installed == true && !desk.running -> "这台电脑的 Hermes 没开" to Tone.Warn
            else -> null
        }
        if (banner != null) {
            Banner(banner.first, banner.second, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                desk.turnsLoading && desk.turns.isEmpty() -> LoadingState("正在读取对话…", Modifier.align(Alignment.Center))
                desk.turns.isEmpty() && !desk.sending -> ChatWelcome(onPick = { draft = it })
                else -> MessageList(
                    sessionID = desk.openSession?.id ?: "",
                    pageIndex = pageIndex,
                    blocks = pages.getOrElse(pageIndex) { Exchange(emptyList()) }.blocks,
                    latest = pageCount == 0 || (pageIndex >= 0 && pageIndex == pageCount - 1),
                    sending = desk.sending,
                    entries = desk.entries,
                ) { span ->
                    if (span.folder) {
                        controller.openDirectory(link.id, span.path)
                        showFiles = true
                    } else {
                        val entry = desk.entries.firstOrNull { it.path == span.path } ?: return@MessageList
                        controller.openPreview(link.id, entry)
                    }
                }
            }
        }
        Composer(
            draft = draft,
            onDraft = { draft = it },
            attachments = attachments,
            onRemove = { id -> attachments.removeAll { it.id == id } },
            onPickImage = {
                if (attachments.size >= ATTACHMENT_COUNT) desk.notice = "一次最多带 10 个文件"
                else imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onPickFile = {
                if (attachments.size >= ATTACHMENT_COUNT) desk.notice = "一次最多带 10 个文件"
                else filePicker.launch(arrayOf("*/*"))
            },
            picking = canSend && !uploading,
            pageLabel = if (pageCount == 0) "" else "${pageIndex + 1}/$pageCount",
            canBack = pageIndex > 0,
            canForward = pageCount > 1 && pageIndex < pageCount - 1,
            onBack = { pinned = pagerTarget(pageCount, pageIndex, forward = false) },
            onForward = { pinned = pagerTarget(pageCount, pageIndex, forward = true) },
            fieldEnabled = online == true && desk.canChat && !desk.sending && !uploading,
            canSubmit = canSend && !uploading && (draft.isNotBlank() || attachments.isNotEmpty()),
            busy = desk.sending || uploading,
            placeholder = when {
                uploading -> "正在保存附件…"
                online != true -> "等待 Mac 连接…"
                !desk.canChat -> "Hermes 没开"
                desk.sending -> "Hermes 正在回复…"
                else -> "给 Hermes 发消息"
            },
            onSend = {
                if (uploading) return@Composer
                uploading = true
                val started = controller.sendWithAttachments(link.id, draft, attachments.toList()) { ok, landed ->
                    uploading = false
                    if (ok) {
                        draft = ""
                        attachments.clear()
                        pinned = null
                    } else {
                        desk.restoreDraft = ""
                        val updated = landedAttachments(attachments.toList(), landed)
                        attachments.clear()
                        attachments.addAll(updated)
                    }
                }
                if (!started) uploading = false
            },
        )
    }
        if (showFiles) {
            BackHandler { showFiles = false }
            ProjectDirectoryDrawer(
                controller = controller,
                link = link,
                desk = desk,
                online = online,
                onClose = { showFiles = false },
            )
        }
    }
    if (managing) {
        SessionManageDialog(
            initial = session.title,
            canDelete = session.id.isNotEmpty(),
            onDismiss = { managing = false },
            onSave = {
                managing = false
                controller.renameSession(link.id, it)
            },
            onDelete = {
                managing = false
                confirmDelete = true
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Paper,
            title = { Text("删除「${session.title}」？") },
            text = { Text("这台 Mac 上的这段对话会被删掉，不能恢复。", color = Slate) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    controller.deleteSession(link.id)
                }) { Text("删除", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消", color = Slate) }
            },
        )
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    subtitle: String,
            onBack: () -> Unit,
            onTitle: () -> Unit,
            onDirectory: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(Paper).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Ink)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    modifier = Modifier.clickable(onClick = onTitle),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDirectory) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = "项目目录", tint = Ink)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
    }
}

@Composable
private fun SessionManageDialog(
    initial: String,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("会话管理") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(80) },
                    singleLine = true,
                    label = { Text("标题") },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Pine,
                        unfocusedBorderColor = Hairline,
                        cursorColor = Pine,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (canDelete) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text("删除会话", color = Coral)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSave(text) }) { Text("保存", color = Pine) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Slate) }
        },
    )
}

@Composable
private fun ProjectDirectoryDrawer(
    controller: RemoteController,
    link: AppLink,
    desk: AppDesk,
    online: Boolean?,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.08f)
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Canvas),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("项目目录", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Text(
                        desk.openProject?.name ?: "未选项目",
                        style = MaterialTheme.typography.labelMedium,
                        color = Slate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭目录", tint = Slate)
            }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            FilesTab(
                controller,
                link,
                desk,
                online,
                path = desk.directoryPath,
                onPath = { controller.openDirectory(link.id, it) },
                onPickProject = {},
            )
            }
        }
    }
}

@Composable
private fun ChatWelcome(onPick: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(HeroBrush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("开始和 Hermes 对话", style = MaterialTheme.typography.titleLarge, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "它会在 Mac 上读文件、跑命令，然后把结果告诉你。",
            style = MaterialTheme.typography.bodyMedium,
            color = Slate,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        suggestions.forEach { text ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Paper)
                    .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                    .clickable { onPick(text) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = InkSoft, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = null, tint = SlateLight, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun MessageList(
    sessionID: String,
    pageIndex: Int,
    blocks: List<ChatBlock>,
    latest: Boolean,
    sending: Boolean,
    entries: List<AppEntry>,
    onOpenPath: (PathSpan) -> Unit,
) {
    val listState = rememberLazyListState()
    val showTyping = latest && sending
    val tail = blocks.lastIndex + if (showTyping) 1 else 0
    var placed by remember(sessionID, pageIndex) { mutableStateOf(false) }
    LaunchedEffect(sessionID, pageIndex, blocks.size, showTyping) {
        if (tail < 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > tail }
        listState.scrollToItem(if (latest) tail else 0)
        placed = true
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().alpha(if (placed || tail < 0) 1f else 0f),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(blocks, key = { index, _ -> "$pageIndex-$index" }) { index, block ->
            val live = latest && sending && index == blocks.lastIndex && block is ChatBlock.Process
            when (block) {
                is ChatBlock.User -> UserBubble(block.text)
                is ChatBlock.Reply -> AssistantBubble(block.text, entries, onOpenPath)
                is ChatBlock.Process -> ProcessBlock(block.lines, live)
            }
        }
        if (showTyping) {
            item(key = "typing-$pageIndex") { AssistantBubble(null, emptyList()) {} }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun copyable(text: String): Modifier {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return Modifier.combinedClickable(
        onClick = {},
        onLongClick = {
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        },
    )
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .padding(start = 48.dp)
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(Pine)
                .then(copyable(text))
                .padding(horizontal = 15.dp, vertical = 11.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProcessBlock(lines: List<String>, live: Boolean) {
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(live) {
        if (!live) opened = false
    }
    val shown = live || opened
    val body = lines.joinToString("\n")
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !live) { opened = !opened }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "过程回复 · ${lines.size} 条",
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
            )
            if (live) {
                CircularProgressIndicator(
                    color = Pine,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    if (shown) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (shown) "收起" else "展开",
                    tint = Slate,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (shown) {
            Text(
                body,
                color = Slate,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Fog)
                    .border(1.dp, Hairline, RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(AnnotatedString(body))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun AssistantBubble(text: String?, entries: List<AppEntry>, onOpenPath: (PathSpan) -> Unit) {
    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    val linked = if (text == null) null else replyText(text, entries, onOpenPath)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 12.dp)
            .clip(shape)
            .background(Paper)
            .border(1.dp, Hairline, shape)
            .then(if (text != null && linked == null) copyable(text) else Modifier)
            .padding(horizontal = 15.dp, vertical = if (text == null) 15.dp else 11.dp),
    ) {
        if (text == null) {
            TypingDots()
        } else if (linked != null) {
            Text(linked, style = MaterialTheme.typography.bodyLarge, color = Color.Unspecified)
        } else {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Ink)
        }
    }
}

private fun replyText(text: String, entries: List<AppEntry>, onOpenPath: (PathSpan) -> Unit): AnnotatedString? {
    val spans = pathSpans(text, entries)
    if (spans.isEmpty()) return null
    return buildAnnotatedString {
        pushStyle(SpanStyle(color = Ink))
        var cursor = 0
        for (span in spans) {
            append(text.substring(cursor, span.start))
            withLink(
                LinkAnnotation.Clickable(
                    tag = span.path,
                    styles = TextLinkStyles(
                        SpanStyle(
                            color = Sky,
                            fontWeight = if (span.folder) FontWeight.Normal else FontWeight.Bold,
                        )
                    ),
                    linkInteractionListener = { onOpenPath(span) },
                )
            ) {
                append(text.substring(span.start, span.end))
            }
            cursor = span.end
        }
        append(text.substring(cursor))
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    attachments: List<ChatAttachment>,
    onRemove: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    picking: Boolean,
    pageLabel: String,
    canBack: Boolean,
    canForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    fieldEnabled: Boolean,
    canSubmit: Boolean,
    busy: Boolean,
    placeholder: String,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Paper)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEach { file ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Fog)
                            .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            file.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink,
                            modifier = Modifier.widthIn(max = 160.dp),
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable(enabled = !busy, onClick = { onRemove(file.id) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "去掉", tint = SlateLight, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(Icons.Outlined.Image, "图片", picking, onPickImage)
            Spacer(Modifier.width(6.dp))
            ToolButton(Icons.Outlined.AttachFile, "附件", picking, onPickFile)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Fog)
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上一页", canBack, onBack)
                if (pageLabel.isNotEmpty()) {
                    Text(
                        pageLabel,
                        color = Ink,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 40.dp).padding(horizontal = 2.dp),
                    )
                }
                PageArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下一页", canForward, onForward)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Fog)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (draft.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = SlateLight)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { onDraft(it.take(4000)) },
                    enabled = fieldEnabled,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                    cursorBrush = SolidColor(Pine),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (canSubmit) Pine else Hairline)
                    .clickable(enabled = canSubmit, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Pine, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "发送",
                        tint = if (canSubmit) Color.White else SlateLight,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) PineSoft else Fog)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) Pine else SlateLight, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = if (enabled) Ink else SlateLight, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PageArrow(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) Pine else SlateLight, modifier = Modifier.size(18.dp))
    }
}

private fun takeAttachment(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    uri: Uri?,
    kind: String,
    attachments: SnapshotStateList<ChatAttachment>,
    desk: AppDesk,
) {
    if (uri == null) return
    if (attachments.size >= ATTACHMENT_COUNT) {
        desk.notice = "一次最多带 10 个文件"
        return
    }
    val resolver = context.contentResolver
    scope.launch {
        val read = withContext(Dispatchers.IO) { readChatAttachment(resolver, uri, kind) }
        when (read) {
            is AttachmentRead.Failed -> desk.notice = read.message
            is AttachmentRead.Ready -> {
                if (attachments.size >= ATTACHMENT_COUNT) desk.notice = "一次最多带 10 个文件"
                else attachments.add(read.file)
            }
        }
    }
}

private sealed class AttachmentRead {
    data class Ready(val file: ChatAttachment) : AttachmentRead()
    data class Failed(val message: String) : AttachmentRead()
}

private fun readChatAttachment(resolver: ContentResolver, uri: Uri, kind: String): AttachmentRead {
    return try {
        val fallback = if (kind == "image") "图片.jpg" else "附件"
        val queried = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getString(0)
        }
        val name = attachmentFileName(queried ?: uri.lastPathSegment.orEmpty(), fallback)
        val stream = resolver.openInputStream(uri) ?: return AttachmentRead.Failed("这个文件读不了")
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                total += count
                if (total > ATTACHMENT_BYTE_LIMIT) return AttachmentRead.Failed("单个文件不能超过 20MB")
                output.write(chunk, 0, count)
            }
            if (total == 0) return AttachmentRead.Failed("这个文件是空的")
            AttachmentRead.Ready(ChatAttachment(UUID.randomUUID().toString(), name, kind, output.toByteArray()))
        }
    } catch (_: Exception) {
        AttachmentRead.Failed("这个文件读不了")
    }
}
