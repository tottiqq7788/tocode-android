package app.tocode.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun RemoteScreen(controller: RemoteController) {
    if (controller.scanning) {
        BackHandler(onBack = controller::closeScan)
        ScanScreen(controller)
        return
    }
    val link = controller.selected()
    if (link == null) {
        WelcomeScreen(onScan = controller::openScan)
        return
    }
    val desk = controller.desk(link.id)
    val session = desk.openSession
    if (session != null) {
        BackHandler { controller.closeChat(link.id) }
        ChatScreen(controller, link, desk, session)
        return
    }
    DeviceScreen(controller, link, desk)
}

@Composable
private fun WelcomeScreen(onScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Box(
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)).background(HeroBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.LaptopMac, contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("把 Mac 装进口袋", style = MaterialTheme.typography.headlineMedium, color = Ink)
            Spacer(Modifier.height(10.dp))
            Text(
                "绑定后可以在手机上和 Hermes 对话、浏览项目目录，或者一键控制电脑。",
                style = MaterialTheme.typography.bodyLarge,
                color = Slate,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Panel(padding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                Step(1, "在 Mac 菜单栏找到 Tocode 并右键")
                Step(2, "选择「应用 → 绑定应用」")
                Step(3, "用手机扫描电脑上弹出的二维码")
            }
        }
        PrimaryButton(
            text = "扫码绑定 Mac",
            onClick = onScan,
            icon = Icons.Outlined.QrCodeScanner,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun Step(index: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(PineSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text("$index", style = MaterialTheme.typography.titleSmall, color = Pine)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Ink)
    }
}

private data class TabItem(val key: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("sessions", "会话", Icons.Outlined.ChatBubbleOutline),
    TabItem("files", "目录", Icons.Outlined.FolderOpen),
    TabItem("buttons", "遥控", Icons.Outlined.Tune),
)

@Composable
private fun DeviceScreen(controller: RemoteController, link: AppLink, desk: AppDesk) {
    var showSheet by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var unlinkTarget by remember { mutableStateOf<AppLink?>(null) }
    val online = controller.online[link.id]
    BackHandler(enabled = desk.tab != "sessions") { controller.showTab(link.id, "sessions") }
    Scaffold(
        containerColor = Canvas,
        topBar = {
            DeviceHeader(
                link = link,
                state = linkState(online, desk),
                onSwitch = { showSheet = true },
                onRefresh = { controller.refreshCurrent(link.id) },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = desk.tab == tab.key,
                        onClick = { if (desk.tab != tab.key) controller.showTab(link.id, tab.key) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Pine,
                            selectedTextColor = Pine,
                            indicatorColor = PineSoft,
                            unselectedIconColor = SlateLight,
                            unselectedTextColor = SlateLight,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (desk.tab) {
                "files" -> FilesTab(controller, link, desk, online)
                "buttons" -> ControlTab(controller, link, online)
                else -> SessionsTab(controller, link, desk, online, onCreate = { showCreate = true })
            }
        }
    }
    if (showSheet) {
        MacSheet(
            controller = controller,
            currentId = link.id,
            onDismiss = { showSheet = false },
            onUnlink = {
                showSheet = false
                unlinkTarget = it
            },
        )
    }
    if (showCreate) {
        CreateSessionDialog(
            onDismiss = { showCreate = false },
            onCreate = {
                showCreate = false
                controller.createSession(link.id, it)
            },
        )
    }
    unlinkTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { unlinkTarget = null },
            containerColor = Paper,
            title = { Text("解除绑定「${target.macName}」？") },
            text = { Text("解除后这台手机不能再控制它，需要重新扫码才能恢复。", color = Slate) },
            confirmButton = {
                TextButton(onClick = {
                    unlinkTarget = null
                    controller.unlink(target.id)
                }) { Text("解除绑定", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { unlinkTarget = null }) { Text("取消", color = Slate) }
            },
        )
    }
}

@Composable
private fun DeviceHeader(link: AppLink, state: LinkState, onSwitch: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Canvas)
            .statusBarsPadding()
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onSwitch)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(HeroBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.LaptopMac, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        link.macName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "切换 Mac", tint = Slate)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(state.tone.fg, 7.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(state.label, style = MaterialTheme.typography.labelMedium, color = Slate)
                }
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = Slate)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MacSheet(
    controller: RemoteController,
    currentId: String,
    onDismiss: () -> Unit,
    onUnlink: (AppLink) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Paper) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("我的 Mac", style = MaterialTheme.typography.titleLarge, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text("会话和目录跟随当前选中的 Mac", style = MaterialTheme.typography.bodyMedium, color = Slate)
            Spacer(Modifier.height(16.dp))
            controller.links().forEach { item ->
                val selected = item.id == currentId
                val state = linkState(controller.online[item.id], controller.desk(item.id))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Paper)
                        .border(if (selected) 1.5.dp else 1.dp, if (selected) Pine else Hairline, RoundedCornerShape(16.dp))
                        .clickable {
                            controller.select(item.id)
                            onDismiss()
                        }
                        .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(Icons.Outlined.LaptopMac, if (selected) Tone.Good else Tone.Muted, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.macName, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(state.tone.fg, 6.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(state.label, style = MaterialTheme.typography.labelMedium, color = Slate)
                        }
                    }
                    if (selected) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = "当前", tint = Pine)
                    }
                    IconButton(onClick = { onUnlink(item) }) {
                        Icon(Icons.Outlined.LinkOff, contentDescription = "解除绑定", tint = SlateLight)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        controller.openScan()
                    }
                    .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Pine)
                Spacer(Modifier.width(8.dp))
                Text("绑定新的 Mac", style = MaterialTheme.typography.labelLarge, color = Pine)
            }
        }
    }
}

@Composable
private fun CreateSessionDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("新建会话") },
        text = {
            Column {
                Text("会话会固定在这台 Mac 配置的项目目录里。", style = MaterialTheme.typography.bodyMedium, color = Slate)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    placeholder = { Text("会话标题（可不填）") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Pine,
                        unfocusedBorderColor = Hairline,
                        cursorColor = Pine,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title) }) { Text("创建", color = Pine) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Slate) }
        },
    )
}
