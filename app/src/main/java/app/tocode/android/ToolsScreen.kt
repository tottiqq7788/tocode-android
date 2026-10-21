package app.tocode.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FilesTab(controller: RemoteController, link: AppLink, desk: AppDesk, online: Boolean?, onPickProject: () -> Unit) {
    val project = desk.openProject
    var path by rememberSaveable(link.id, project?.id) { mutableStateOf("") }
    BackHandler(enabled = path.isNotEmpty()) { path = path.substringBeforeLast('/', "") }
    val children = childrenOf(desk.entries, path)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
        item { Breadcrumbs(path, onPick = { path = it }) }
        if (desk.treeNotice.isNotBlank()) {
            item { Banner(desk.treeNotice, Tone.Info) }
        }
        if (desk.notice.isNotBlank()) {
            item { Banner(desk.notice, Tone.Bad) }
        }
        item {
            SectionTitle(
                if (path.isEmpty()) project?.name ?: "项目根目录" else path.substringAfterLast('/'),
                "${children.size} 项 · 只读",
            )
        }
        item {
            if (children.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Folder,
                    title = "空文件夹",
                    subtitle = "这里没有可以显示的内容。",
                )
            } else {
                Panel(padding = PaddingValues(vertical = 4.dp), radius = 18.dp) {
                    children.forEachIndexed { index, entry ->
                        if (index > 0) {
                            Box(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(Hairline))
                        }
                        EntryRow(
                            entry = entry,
                            count = if (entry.isFolder()) desk.entries.count { it.parent() == entry.path } else 0,
                            onOpen = {
                                if (entry.isFolder()) path = entry.path else controller.openPreview(link.id, entry)
                            },
                        )
                    }
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

@Composable
private fun EntryRow(entry: AppEntry, count: Int, onOpen: () -> Unit) {
    val folder = entry.isFolder()
    val ext = entry.name.substringAfterLast('.', "").lowercase()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (folder) IconBadge(Icons.Outlined.Folder, Tone.Warn, size = 38.dp) else IconBadge(fileIcon(ext), fileTone(ext), size = 38.dp)
        Spacer(Modifier.width(14.dp))
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
        Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = null, tint = SlateLight, modifier = Modifier.size(13.dp))
    }
}

private val codeExt = setOf("kt", "kts", "swift", "py", "js", "ts", "tsx", "jsx", "java", "go", "rs", "c", "h", "cpp", "m", "sh", "rb", "php")
private val dataExt = setOf("json", "yml", "yaml", "toml", "xml", "plist", "gradle", "lock")
private val imageExt = setOf("png", "jpg", "jpeg", "gif", "svg", "webp", "heic", "ico")
private val textExt = setOf("md", "txt", "rtf", "pdf", "doc", "docx")

private fun fileIcon(ext: String): ImageVector = when (ext) {
    in codeExt -> Icons.Outlined.Code
    in dataExt -> Icons.Outlined.DataObject
    in imageExt -> Icons.Outlined.Image
    in textExt -> Icons.AutoMirrored.Outlined.Article
    else -> Icons.Outlined.InsertDriveFile
}

private fun fileTone(ext: String): Tone = when (ext) {
    in codeExt -> Tone.Info
    in dataExt -> Tone.Good
    in imageExt -> Tone.Bad
    else -> Tone.Muted
}

private data class ActionLook(val icon: ImageVector, val hint: String)

private val actionLooks = mapOf(
    "status" to ActionLook(Icons.Outlined.MonitorHeart, "查看所有开关"),
    "blackout" to ActionLook(Icons.Outlined.DarkMode, "立即遮住屏幕"),
    "login" to ActionLook(Icons.Outlined.PowerSettingsNew, "登录时自动启动"),
    "finder-follow" to ActionLook(Icons.Outlined.TouchApp, "跟随访达选中"),
    "codex-follow" to ActionLook(Icons.Outlined.Code, "跟随 Codex 项目"),
    "hidden" to ActionLook(Icons.Outlined.VisibilityOff, "显示或隐藏"),
    "finder-move" to ActionLook(Icons.Outlined.DriveFileMove, "剪切后移动"),
    "finder-cmdq" to ActionLook(Icons.Outlined.Keyboard, "访达里 Cmd-Q"),
    "double-cmdq" to ActionLook(Icons.Outlined.Keyboard, "防止误退出"),
    "wheel-vertical" to ActionLook(Icons.Outlined.SwapVert, "翻转上下方向"),
    "wheel-horizontal" to ActionLook(Icons.Outlined.SwapHoriz, "翻转左右方向"),
)

private val groupTones = listOf(Tone.Info, Tone.Good, Tone.Warn)

@Composable
fun ControlTab(controller: RemoteController, link: AppLink, online: Boolean?) {
    val titles = remoteActions.toMap()
    val busyAction = controller.pendingAction[link.id]
    val enabled = online == true && busyAction == null
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
                busy = busyAction != null,
            )
        }
        if (online != true) {
            item { Banner(if (online == null) "正在连接这台 Mac…" else "这台 Mac 离线，遥控暂时不可用", if (online == null) Tone.Info else Tone.Bad) }
        }
        remoteGroups.forEachIndexed { groupIndex, (groupTitle, actions) ->
            item { SectionTitle(groupTitle, modifier = Modifier.padding(top = 6.dp)) }
            actions.chunked(2).forEach { pair ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { action ->
                            val look = actionLooks[action] ?: ActionLook(Icons.Outlined.Mouse, "")
                            ActionTile(
                                title = titles[action] ?: action,
                                look = look,
                                tone = groupTones[groupIndex % groupTones.size],
                                busy = busyAction == action,
                                enabled = enabled,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = { controller.send(action, titles[action] ?: action) },
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
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
                Text(look.hint, style = MaterialTheme.typography.bodySmall, color = SlateLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
