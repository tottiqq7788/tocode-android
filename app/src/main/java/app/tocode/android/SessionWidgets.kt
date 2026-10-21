package app.tocode.android

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
private val linkKey = ActionParameters.Key<String>(EXTRA_LINK_ID)
private val profileKey = ActionParameters.Key<String>(EXTRA_PROFILE)
private val projectKey = ActionParameters.Key<String>(EXTRA_PROJECT_ID)
private val projectNameKey = ActionParameters.Key<String>(EXTRA_PROJECT_NAME)
private val sessionKey = ActionParameters.Key<String>(EXTRA_SESSION_ID)
private val titleKey = ActionParameters.Key<String>(EXTRA_SESSION_TITLE)
private val tabKey = ActionParameters.Key<String>(EXTRA_TAB)

private val White = Color.White
private val Mist = Color(0xCCFFFFFF)
private val Faint = Color(0x99FFFFFF)
private val Mint = Color(0xFF9FE3C4)
private val Deep = Color(0xFF0A4D38)

fun refreshSessionWidgets(context: Context) {
    val app = context.applicationContext
    widgetScope.launch {
        runCatching { SmallSessionWidget().updateAll(app) }
        runCatching { MediumSessionWidget().updateAll(app) }
        runCatching { LargeSessionWidget().updateAll(app) }
    }
}

class SmallSessionWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = System.currentTimeMillis()
        val view = smallSessionView(loadSessions(context), loadReplies(context), now)
        provideContent {
            val target = view.run?.open ?: view.reply?.open
            Column(cardFrame().clickable(if (target != null) openSession(target) else openTab(TAB_NEW_SESSION))) {
                Header(view.running, compact = true)
                Spacer(GlanceModifier.defaultWeight())
                val run = view.run
                val reply = view.reply
                when {
                    run != null -> {
                        Text(run.title, maxLines = 2, style = text(White, 18.sp, FontWeight.Bold))
                        Spacer(GlanceModifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot()
                            Spacer(GlanceModifier.width(6.dp))
                            Elapsed(run.startedAt)
                        }
                    }
                    reply != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(ImageProvider(R.drawable.ic_widget_done), contentDescription = null, modifier = GlanceModifier.size(14.dp))
                            Spacer(GlanceModifier.width(4.dp))
                            Text("刚刚回复", style = text(Mint, 13.sp, FontWeight.Medium))
                        }
                        Spacer(GlanceModifier.height(4.dp))
                        Text(reply.title, maxLines = 2, style = text(White, 17.sp, FontWeight.Bold))
                        Text(repliedAgo(now, reply.finishedAt), style = text(Faint, 13.sp))
                    }
                    else -> {
                        Text("都回复完了", style = text(White, 18.sp, FontWeight.Bold))
                        Spacer(GlanceModifier.height(8.dp))
                        Pill("新会话")
                    }
                }
            }
        }
    }
}

class MediumSessionWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = System.currentTimeMillis()
        val view = mediumSessionView(loadSessions(context), loadReplies(context), now)
        provideContent {
            val room = rowsThatFit(LocalSize.current, reserved = 78.dp)
            Column(cardFrame()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Header(view.running, compact = false, modifier = GlanceModifier.defaultWeight())
                    QuickCircle(R.drawable.ic_widget_new, "新会话", TAB_NEW_SESSION)
                    Spacer(GlanceModifier.width(6.dp))
                    QuickCircle(R.drawable.ic_widget_folder, "目录", "files")
                    Spacer(GlanceModifier.width(6.dp))
                    QuickCircle(R.drawable.ic_widget_remote, "遥控", "buttons")
                }
                val runs = view.runs.take(room)
                val replies = view.replies.take((room - runs.size).coerceAtLeast(0))
                Column(GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
                    if (runs.isEmpty() && replies.isEmpty()) {
                        Gap { EmptyRow("都回复完了", "在会话里提问后，这里会显示进度") }
                    } else {
                        runs.forEach { row -> Gap { RunRow(row) } }
                        replies.forEach { row -> Gap { ReplyRow(row, now) } }
                    }
                }
            }
        }
    }
}

class LargeSessionWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = System.currentTimeMillis()
        val view = largeSessionView(loadSessions(context), loadReplies(context), now)
        provideContent {
            var room = rowsThatFit(LocalSize.current, reserved = 196.dp)
            val groups = mutableListOf<LargeSessionGroup>()
            for (group in view.groups) {
                if (room <= 0) break
                val rows = group.rows.take(room)
                groups.add(group.copy(rows = rows))
                room -= rows.size
            }
            if (view.groups.isEmpty()) room -= 1
            val replies = view.replies.take(room.coerceAtLeast(1))
            Column(cardFrame()) {
                Header(view.running, compact = false, modifier = GlanceModifier.fillMaxWidth())
                Column(GlanceModifier.fillMaxWidth().padding(top = 12.dp)) {
                    SectionLabel("正在回复", if (view.more > 0) "还有 ${view.more} 个" else "")
                    if (groups.isEmpty()) {
                        Gap { EmptyRow("现在没有正在回复的会话", "提问后会在这里走秒") }
                    } else {
                        groups.forEach { group ->
                            Column(GlanceModifier.fillMaxWidth()) {
                                if (groups.size > 1) {
                                    Text(group.label, maxLines = 1, style = text(Faint, 13.sp), modifier = GlanceModifier.padding(top = 6.dp))
                                }
                                group.rows.forEach { row -> Gap { RunRow(row) } }
                            }
                        }
                    }
                }
                Column(GlanceModifier.fillMaxWidth().padding(top = 12.dp)) {
                    SectionLabel("刚刚回复", "")
                    if (replies.isEmpty()) {
                        Text("回复结束后会出现在这里", style = text(Faint, 14.sp), modifier = GlanceModifier.padding(top = 6.dp))
                    } else {
                        replies.forEach { row -> Gap { ReplyRow(row, now) } }
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth()) {
                    QuickTile(R.drawable.ic_widget_new, "新会话", TAB_NEW_SESSION, GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(8.dp))
                    QuickTile(R.drawable.ic_widget_folder, "目录", "files", GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(8.dp))
                    QuickTile(R.drawable.ic_widget_remote, "遥控", "buttons", GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

class SmallSessionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallSessionWidget()
}

class MediumSessionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediumSessionWidget()
}

class LargeSessionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LargeSessionWidget()
}

@Composable
private fun Header(running: Int, compact: Boolean, modifier: GlanceModifier = GlanceModifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.ic_stat_session), contentDescription = null, modifier = GlanceModifier.size(16.dp))
        Spacer(GlanceModifier.width(6.dp))
        Text("Tocode", style = text(White, 15.sp, FontWeight.Bold))
        if (compact) Spacer(GlanceModifier.defaultWeight()) else Spacer(GlanceModifier.width(8.dp))
        if (running > 0) {
            Row(
                GlanceModifier.background(ImageProvider(R.drawable.widget_chip)).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveDot()
                Spacer(GlanceModifier.width(5.dp))
                Text(if (compact) "$running" else "进行中 $running", style = text(White, 13.sp, FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun RunRow(row: WidgetRunRow) {
    Row(
        GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.widget_row))
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .clickable(openSession(row.open)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot()
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(row.title, maxLines = 1, style = text(White, 16.sp, FontWeight.Medium))
            if (row.place.isNotBlank()) Text(row.place, maxLines = 1, style = text(Faint, 13.sp))
        }
        Spacer(GlanceModifier.width(8.dp))
        Elapsed(row.startedAt)
    }
}

@Composable
private fun ReplyRow(row: WidgetReplyRow, now: Long) {
    Row(
        GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.widget_row))
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .clickable(openSession(row.open)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(ImageProvider(R.drawable.ic_widget_done), contentDescription = null, modifier = GlanceModifier.size(14.dp))
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(row.title, maxLines = 1, style = text(White, 16.sp, FontWeight.Medium))
            Text(listOf(row.place, "已回复").filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, style = text(Faint, 13.sp))
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(repliedAgo(now, row.finishedAt), style = text(Mist, 13.sp))
    }
}

@Composable
private fun EmptyRow(title: String, subtitle: String) {
    Column(
        GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.widget_row))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clickable(openTab(TAB_NEW_SESSION)),
    ) {
        Text(title, maxLines = 1, style = text(White, 16.sp, FontWeight.Medium))
        Text(subtitle, maxLines = 1, style = text(Faint, 13.sp))
    }
}

@Composable
private fun Gap(content: @Composable () -> Unit) {
    Box(GlanceModifier.fillMaxWidth().padding(top = 6.dp)) { content() }
}

@Composable
private fun SectionLabel(title: String, trailing: String) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = text(Mint, 13.sp, FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
        if (trailing.isNotBlank()) Text(trailing, style = text(Faint, 13.sp))
    }
}

@Composable
private fun QuickCircle(icon: Int, label: String, tab: String) {
    Box(
        GlanceModifier
            .size(30.dp)
            .background(ImageProvider(R.drawable.widget_action))
            .clickable(openTab(tab)),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), contentDescription = label, modifier = GlanceModifier.size(16.dp))
    }
}

@Composable
private fun QuickTile(icon: Int, label: String, tab: String, modifier: GlanceModifier) {
    Row(
        modifier
            .height(44.dp)
            .background(ImageProvider(R.drawable.widget_chip))
            .clickable(openTab(tab)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(16.dp))
        Spacer(GlanceModifier.width(6.dp))
        Text(label, style = text(White, 14.sp, FontWeight.Medium))
    }
}

@Composable
private fun Pill(label: String) {
    Row(
        GlanceModifier.background(ImageProvider(R.drawable.widget_pill)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("+ $label", style = text(Deep, 14.sp, FontWeight.Bold))
    }
}

@Composable
private fun LiveDot() {
    Image(ImageProvider(R.drawable.widget_dot), contentDescription = null, modifier = GlanceModifier.size(8.dp))
}

@Composable
private fun Elapsed(startedAt: Long) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.widget_chronometer)
    val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startedAt).coerceAtLeast(0)
    views.setChronometer(R.id.widget_chronometer, base, null, true)
    AndroidRemoteViews(views)
}

private fun rowsThatFit(size: DpSize, reserved: androidx.compose.ui.unit.Dp): Int =
    (((size.height - reserved).value) / 54f).toInt().coerceAtLeast(1)

private fun text(color: Color, size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = weight)

private fun loadSessions(context: Context): List<RunningSession> =
    RunningSessionStore(File(context.filesDir, RUNNING_SESSIONS_FILE)).load()

private fun loadReplies(context: Context): List<RecentReply> =
    RecentReplyStore(File(context.filesDir, RECENT_REPLIES_FILE)).load()

private fun cardFrame(): GlanceModifier =
    GlanceModifier
        .fillMaxSize()
        .background(ImageProvider(R.drawable.widget_card))
        .cornerRadius(24.dp)
        .padding(14.dp)

private fun openTab(tab: String): Action = actionStartActivity<MainActivity>(actionParametersOf(tabKey to tab))

private fun openSession(open: SessionOpen): Action =
    actionStartActivity<MainActivity>(
        actionParametersOf(
            linkKey to open.linkId,
            profileKey to open.profile,
            projectKey to open.projectId,
            projectNameKey to open.projectName,
            sessionKey to open.sessionId,
            titleKey to open.title,
        ),
    )
