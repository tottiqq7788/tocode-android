package app.tocode.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesTab(
    controller: RemoteController,
    link: AppLink,
    desk: AppDesk,
    online: Boolean?,
    path: String,
    onPath: (String) -> Unit,
    onPickProject: () -> Unit,
    onQuote: ((String) -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    val project = desk.openProject
    BackHandler(enabled = desk.selecting || path.isNotEmpty()) {
        if (desk.selecting) controller.clearSelection(link.id)
        else onPath(path.substringBeforeLast('/', ""))
    }
    val children = childrenOf(desk.entries, path)
    val page = desk.folderPages[path]
    val listState = rememberLazyListState()
    LaunchedEffect(path, children.size, page?.loaded, page?.truncated, page?.loadingOffset) {
        if (page?.loaded != true || page.truncated != true || page.loadingOffset != null) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 2
        }.collect { nearEnd ->
            if (nearEnd) controller.loadMoreDirectory(link.id)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
    ) {
        if (desk.installed == false) {
            item {
                EmptyState(
                    icon = Icons.Outlined.FolderOff,
                    title = "这台电脑没有 Hermes",
                    subtitle = "目录跟着 Hermes 项目走，没有 Hermes 时不能浏览。",
                    tone = Tone.Warn,
                )
            }
            return@LazyColumn
        }
        if (project == null && desk.installed == true) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Folder,
                    title = "先选一个项目",
                    subtitle = "目录显示当前项目的文件夹。",
                    tone = Tone.Good,
                    action = "选择项目",
                    onAction = onPickProject,
                )
            }
            return@LazyColumn
        }
        if (!desk.treeLoaded) {
            item {
                if (online == false) {
                    EmptyState(
                        icon = Icons.Outlined.FolderOff,
                        title = "这台 Mac 暂时离线",
                        subtitle = "恢复连接后会自动读取目录。",
                        tone = Tone.Bad,
                    )
                } else {
                    LoadingState("正在读取目录…")
                }
            }
            return@LazyColumn
        }
        if (desk.selecting) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Paper)
                        .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSoft,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(enabled = !desk.downloading) { controller.clearSelection(link.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    Text(
                        "已选 ${desk.selectedPaths.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(
                                enabled = !desk.downloading && desk.selectedPaths.isNotEmpty(),
                            ) { controller.downloadSelected(link.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .alpha(if (desk.downloading || desk.selectedPaths.isEmpty()) 0.4f else 1f),
                    ) {
                        if (desk.downloading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Outlined.Download, contentDescription = null, tint = Ink, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            if (desk.downloading) "下载中" else "下载",
                            style = MaterialTheme.typography.labelLarge,
                            color = Ink,
                        )
                    }
                }
            }
        }
        item { Box(Modifier.padding(bottom = 12.dp)) { Breadcrumbs(path, onPick = onPath) } }
        if (desk.treeNotice.isNotBlank()) {
            item { Box(Modifier.padding(bottom = 12.dp)) { Banner(desk.treeNotice, Tone.Info) } }
        }
        if (desk.notice.isNotBlank()) {
            item {
                Box(Modifier.padding(bottom = 12.dp)) {
                    Banner(desk.notice, if (desk.notice.startsWith("已保存")) Tone.Good else Tone.Bad)
                }
            }
        }
        if (page?.loaded != true) {
            item {
                if (desk.treeNotice.isNotBlank()) {
                    EmptyState(
                        icon = Icons.Outlined.FolderOff,
                        title = "目录没有读出来",
                        subtitle = desk.treeNotice,
                        tone = Tone.Bad,
                    )
                } else {
                    LoadingState("正在读取目录…")
                }
            }
            return@LazyColumn
        }
        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                SectionTitle(
                    if (path.isEmpty()) project?.name ?: "项目根目录" else path.substringAfterLast('/'),
                    when {
                        desk.selecting -> "长按多选 · 点勾选切换 · 点文件夹仍可进入"
                        page.truncated -> "已列出 ${children.size} 项 · 长按可多选下载"
                        else -> "${children.size} 项 · 长按可多选下载"
                    },
                )
            }
        }
        if (children.isEmpty()) {
            item {
                if (desk.treeNotice.isNotBlank()) {
                    EmptyState(
                        icon = Icons.Outlined.FolderOff,
                        title = "目录没有读出来",
                        subtitle = desk.treeNotice,
                        tone = Tone.Bad,
                    )
                } else {
                    EmptyState(
                        icon = Icons.Outlined.Folder,
                        title = "空文件夹",
                        subtitle = "这里没有可以显示的内容。",
                    )
                }
            }
        } else {
            itemsIndexed(children, key = { _, entry -> entry.path }) { index, entry ->
                val more = page.loadingOffset != null
                val shape = when {
                    children.size == 1 && !more -> RoundedCornerShape(18.dp)
                    index == 0 -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                    index == children.lastIndex && !more -> RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Paper)
                        .border(1.dp, Hairline, shape),
                ) {
                    if (index > 0) {
                        Box(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(Hairline))
                    }
                    EntryRow(
                        entry = entry,
                        count = if (entry.isFolder()) desk.entries.count { it.parent() == entry.path } else 0,
                        selecting = desk.selecting,
                        selected = entry.path in desk.selectedPaths,
                        onToggle = { controller.toggleSelected(link.id, entry.path) },
                        onCite = {
                            val absolute = absoluteProjectPath(desk.projectRoot, entry.path)
                            if (absolute == null) {
                                desk.notice = "还不知道这个项目的路径"
                            } else {
                                clipboard.setText(AnnotatedString(absolute))
                                onQuote?.invoke(absolute)
                            }
                        },
                        onOpen = {
                            if (entry.isFolder()) onPath(entry.path) else controller.openPreview(link.id, entry)
                        },
                        onLongPress = {
                            controller.toggleSelected(link.id, entry.path)
                        },
                    )
                }
            }
            if (page.loadingOffset != null) {
                item {
                    Text(
                        "继续列出…",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                            .background(Paper)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Slate,
                    )
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(path: String, onPick: (String) -> Unit) {
    val crumbs = breadcrumb(path)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crumb("根目录", selected = crumbs.isEmpty()) { onPick("") }
        crumbs.forEachIndexed { index, crumb ->
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForwardIos,
                contentDescription = null,
                tint = SlateLight,
                modifier = Modifier.padding(horizontal = 4.dp).size(10.dp),
            )
            Crumb(crumb.substringAfterLast('/'), selected = index == crumbs.lastIndex) { onPick(crumb) }
        }
    }
}

@Composable
private fun Crumb(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Color.White else InkSoft,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Ink else Paper)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: AppEntry,
    count: Int,
    selecting: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onCite: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val folder = entry.isFolder()
    val ext = entry.name.substringAfterLast('.', "").lowercase()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selecting && !folder) onToggle()
                    else onOpen()
                },
                onLongClick = onLongPress,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(
                if (selected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (selected) "已选" else "未选",
                tint = if (selected) Ink else SlateLight,
                modifier = Modifier
                    .padding(start = 10.dp, end = 2.dp)
                    .size(28.dp)
                    .clickable(onClick = onToggle),
            )
        }
        Box(
            modifier = Modifier
                .clickable(onClick = if (selecting) onToggle else onCite)
                .padding(start = if (selecting) 4.dp else 10.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        ) {
            if (folder) IconBadge(Icons.Outlined.Folder, Tone.Warn, size = 38.dp) else IconBadge(fileIcon(ext), fileTone(ext), size = 38.dp)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = {
                    if (selecting && folder) onOpen()
                    else if (selecting) onToggle()
                    else onOpen()
                })
                .padding(top = 11.dp, bottom = 11.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        folder -> if (count > 0) "$count 项" else "文件夹"
                        ext.isNotEmpty() -> "${ext.uppercase()} 文件"
                        else -> "文件"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateLight,
                )
            }
            if (!selecting || folder) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = null, tint = SlateLight, modifier = Modifier.size(13.dp))
            }
        }
    }
}

private val codeExt = setOf("kt", "kts", "swift", "py", "js", "ts", "tsx", "jsx", "java", "go", "rs", "c", "h", "cpp", "m", "sh", "rb", "php")
private val dataExt = setOf("json", "yml", "yaml", "toml", "xml", "plist", "gradle", "lock")
private val imageExt = setOf("png", "jpg", "jpeg", "gif", "svg", "webp", "heic", "ico")
private val pdfExt = setOf("pdf")
private val textExt = setOf("md", "txt", "rtf", "doc", "docx")

private fun fileIcon(ext: String): ImageVector = when (ext) {
    in codeExt -> Icons.Outlined.Code
    in dataExt -> Icons.Outlined.DataObject
    in imageExt -> Icons.Outlined.Image
    in pdfExt -> Icons.Outlined.PictureAsPdf
    in textExt -> Icons.AutoMirrored.Outlined.Article
    else -> Icons.Outlined.InsertDriveFile
}

private fun fileTone(ext: String): Tone = when (ext) {
    in codeExt -> Tone.Info
    in dataExt -> Tone.Good
    in imageExt -> Tone.Bad
    in pdfExt -> Tone.Warn
    else -> Tone.Muted
}

private data class ActionLook(val icon: ImageVector, val hint: String)

private fun remoteIcon(id: String): ImageVector = when (id) {
    "moon" -> Icons.Outlined.DarkMode
    "sun" -> Icons.Outlined.LightMode
    "lock" -> Icons.Outlined.Lock
    "heart" -> Icons.Outlined.Favorite
    "display" -> Icons.Outlined.Monitor
    "keyboard" -> Icons.Outlined.Keyboard
    "terminal" -> Icons.Outlined.Terminal
    "folder" -> Icons.Outlined.Folder
    "document" -> Icons.Outlined.Description
    "bolt" -> Icons.Outlined.Bolt
    "bell" -> Icons.Outlined.Notifications
    "camera" -> Icons.Outlined.PhotoCamera
    "wifi" -> Icons.Outlined.Wifi
    "speaker" -> Icons.Outlined.VolumeUp
    "play" -> Icons.Outlined.PlayArrow
    "pause" -> Icons.Outlined.Pause
    "stop" -> Icons.Outlined.Stop
    "trash" -> Icons.Outlined.Delete
    "star" -> Icons.Outlined.Star
    "flag" -> Icons.Outlined.Flag
    "tag" -> Icons.Outlined.Label
    "bookmark" -> Icons.Outlined.Bookmark
    "clock" -> Icons.Outlined.Schedule
    "calendar" -> Icons.Outlined.CalendarMonth
    "map" -> Icons.Outlined.Map
    "house" -> Icons.Outlined.Home
    "gear" -> Icons.Outlined.Settings
    "info" -> Icons.Outlined.Info
    "checkmark" -> Icons.Outlined.CheckCircle
    "xmark" -> Icons.Outlined.Cancel
    "plus" -> Icons.Outlined.AddCircle
    "person" -> Icons.Outlined.Person
    "message" -> Icons.Outlined.Chat
    "phone" -> Icons.Outlined.Phone
    "mail" -> Icons.Outlined.Email
    "link" -> Icons.Outlined.Link
    "eye" -> Icons.Outlined.Visibility
    "hammer" -> Icons.Outlined.Handyman
    "wrench" -> Icons.Outlined.Build
    "paintbrush" -> Icons.Outlined.Brush
    "photo" -> Icons.Outlined.Image
    "music" -> Icons.Outlined.MusicNote
    "microphone" -> Icons.Outlined.Mic
    "video" -> Icons.Outlined.Videocam
    "globe" -> Icons.Outlined.Public
    "shield" -> Icons.Outlined.Shield
    "key" -> Icons.Outlined.Key
    else -> Icons.Outlined.TouchApp
}

@Composable
private fun SectionTabs(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("windows" to "窗口", "buttons" to "映射").forEach { (id, title) ->
            val on = selected == id
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Sky else Color(0xFFF3F1EA))
                    .clickable { onSelect(id) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(title, color = if (on) Color.White else Ink, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun ControlTab(controller: RemoteController, link: AppLink, online: Boolean?) {
    var section by remember(link.id) { mutableStateOf("windows") }
    val busyAction = controller.pendingAction[link.id]
    val enabled = online == true && busyAction == null
    val displays = controller.desktopLists[link.id].orEmpty()
    val buttons = controller.buttonLists[link.id].orEmpty()
    val buttonsKnown = controller.buttonLists.containsKey(link.id)
    LaunchedEffect(link.id, online, section) {
        if (online != true) return@LaunchedEffect
        if (section == "windows") controller.refreshDesktops(link.id)
        else controller.refreshButtons(link.id)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ResultCard(
                title = controller.lastAction[link.id],
                result = controller.lastResult[link.id],
                ok = controller.lastOk[link.id],
                busy = busyAction != null && busyAction != "desktops" && busyAction != "remote-buttons",
            )
        }
        item { SectionTabs(section) { section = it } }
        if (online != true) {
            item { Banner(if (online == null) "正在连接这台 Mac…" else "这台 Mac 离线，遥控暂时不可用", if (online == null) Tone.Info else Tone.Bad) }
        }
        if (section == "windows") {
            when {
                online != true -> Unit
                displays.isNotEmpty() -> {
                    val showDisplay = displays.size > 1
                    displays.forEach { display ->
                        if (showDisplay && display.title.isNotBlank()) {
                            item { Text(display.title, style = MaterialTheme.typography.labelLarge, color = Slate) }
                        }
                        display.spaces.forEach { space ->
                            item {
                                DesktopSpaceCard(space, enabled) { window ->
                                    if (window == null) controller.switchDesktop(link.id, space)
                                    else controller.switchWindow(link.id, space, window)
                                }
                            }
                        }
                    }
                }
                controller.desktopNotice[link.id].orEmpty().isBlank() -> item { LoadingState("正在读取桌面…") }
                else -> item { Banner(controller.desktopNotice[link.id].orEmpty(), Tone.Bad) }
            }
        } else {
            when {
                online != true -> Unit
                buttons.isNotEmpty() -> {
                    buttons.chunked(2).forEach { pair ->
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                pair.forEach { button ->
                                    ActionTile(
                                        title = button.name,
                                        look = ActionLook(remoteIcon(button.icon), button.detail),
                                        tone = Tone.Info,
                                        busy = controller.pressedButton[link.id] == button.id,
                                        enabled = enabled,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        onClick = { controller.pressButton(link.id, button) },
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                controller.buttonNotice[link.id].orEmpty().isNotBlank() -> item {
                    Banner(controller.buttonNotice[link.id].orEmpty(), Tone.Bad)
                }
                !buttonsKnown -> item { LoadingState("正在读取按钮…") }
                else -> item {
                    Text(
                        "还没有按钮。在 Mac 的「输入 → 遥控」里新增。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopSpaceCard(space: RemoteSpace, enabled: Boolean, onOpen: (RemoteWindow?) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Paper)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onOpen(null) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(space.title, style = MaterialTheme.typography.titleSmall, color = Ink, modifier = Modifier.weight(1f))
            if (space.current) Text("当前空间", style = MaterialTheme.typography.labelMedium, color = Pine)
        }
        space.windows.forEach { window ->
            Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(Hairline))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onOpen(window) }
                    .padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    window.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (window.front) {
                    Spacer(Modifier.width(8.dp))
                    Text("当前", style = MaterialTheme.typography.labelMedium, color = Sky)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(title: String?, result: String?, ok: Boolean?, busy: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(NightBrush)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    busy -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    ok == true -> Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF7CF0BF))
                    ok == false -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color(0xFFFF9A9A))
                    else -> Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("最近操作", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(2.dp))
                Text(title ?: "还没有操作", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(2.dp))
                Text(
                    result ?: "点下面的卡片，直接控制这台 Mac",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ActionTile(
    title: String,
    look: ActionLook,
    tone: Tone,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Panel(
        modifier = modifier.alpha(if (enabled || busy) 1f else 0.55f),
        onClick = if (enabled) onClick else null,
        padding = PaddingValues(horizontal = 12.dp, vertical = 13.dp),
        radius = 18.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(tone.bg), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = tone.fg, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                }
            } else {
                IconBadge(look.icon, tone, size = 36.dp, radius = 11.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (look.hint.isNotBlank()) {
                    Text(look.hint, style = MaterialTheme.typography.bodySmall, color = SlateLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
