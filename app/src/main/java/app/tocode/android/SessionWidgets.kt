package app.tocode.android

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
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

fun refreshSessionWidgets(context: Context) {
    val app = context.applicationContext
    widgetScope.launch {
        runCatching { SmallSessionWidget().updateAll(app) }
        runCatching { MediumSessionWidget().updateAll(app) }
        runCatching { LargeSessionWidget().updateAll(app) }
    }
}

class SmallSessionWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val view = smallSessionView(loadSessions(context))
        provideContent {
            Column(widgetFrame().clickable(openSession(view.open))) {
                Text(
                    view.title ?: "没有正在进行的会话",
                    maxLines = 2,
                    style = TextStyle(color = ColorProvider(Ink), fontWeight = FontWeight.Bold, fontSize = 15.sp),
                )
                if (view.open != null) {
                    Text("正在回复", style = TextStyle(color = ColorProvider(Pine), fontSize = 12.sp))
                }
                if (view.more > 0) {
                    Text("还有 ${view.more} 个", style = TextStyle(color = ColorProvider(Slate), fontSize = 12.sp))
                }
            }
        }
    }
}

class MediumSessionWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val view = mediumSessionView(loadSessions(context))
        provideContent {
            if (view.rows.isEmpty()) {
                Column(widgetFrame().clickable(openSession(null))) {
                    Text("没有正在进行的会话", style = TextStyle(color = ColorProvider(Ink), fontWeight = FontWeight.Bold, fontSize = 15.sp))
                }
            } else {
                Column(widgetFrame()) {
                    view.rows.forEach { row ->
                        Column(GlanceModifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp).clickable(openSession(row.open))) {
                            Text(row.title, maxLines = 1, style = TextStyle(color = ColorProvider(Ink), fontWeight = FontWeight.Medium, fontSize = 14.sp))
                            if (row.detail.isNotBlank()) {
                                Text(row.detail, maxLines = 1, style = TextStyle(color = ColorProvider(Slate), fontSize = 12.sp))
                            } else {
                                Text("正在回复", style = TextStyle(color = ColorProvider(Pine), fontSize = 12.sp))
                            }
                        }
                    }
                    if (view.more > 0) {
                        Text(
                            "还有 ${view.more} 个",
                            modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp).clickable(openSession(null)),
                            style = TextStyle(color = ColorProvider(Slate), fontSize = 12.sp),
                        )
                    }
                }
            }
        }
    }
}

class LargeSessionWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val view = largeSessionView(loadSessions(context))
        provideContent {
            if (view.groups.isEmpty()) {
                Column(widgetFrame().clickable(openSession(null))) {
                    Text("没有正在进行的会话", style = TextStyle(color = ColorProvider(Ink), fontWeight = FontWeight.Bold, fontSize = 16.sp))
                }
            } else {
                Column(widgetFrame()) {
                    view.groups.forEach { group ->
                        Text(group.label, maxLines = 1, style = TextStyle(color = ColorProvider(Slate), fontWeight = FontWeight.Medium, fontSize = 12.sp))
                        group.rows.forEach { row ->
                            Column(
                                GlanceModifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp).clickable(openSession(row.open)),
                            ) {
                                Text(row.title, maxLines = 1, style = TextStyle(color = ColorProvider(Ink), fontSize = 15.sp))
                                Text("正在回复", style = TextStyle(color = ColorProvider(Pine), fontSize = 12.sp))
                            }
                        }
                    }
                    if (view.more > 0) {
                        Text(
                            "还有 ${view.more} 个",
                            modifier = GlanceModifier.fillMaxWidth().clickable(openSession(null)),
                            style = TextStyle(color = ColorProvider(Slate), fontSize = 12.sp),
                        )
                    }
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

private fun loadSessions(context: Context): List<RunningSession> =
    RunningSessionStore(File(context.filesDir, RUNNING_SESSIONS_FILE)).load()

private fun widgetFrame(): GlanceModifier =
    GlanceModifier.fillMaxSize().background(ColorProvider(Color.White)).cornerRadius(20.dp).padding(12.dp)

private fun openSession(open: SessionOpen?) = if (open == null) {
    actionStartActivity<MainActivity>()
} else {
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
}
