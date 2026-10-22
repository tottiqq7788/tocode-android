package app.tocode.android

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import java.io.File

class MainActivity : ComponentActivity() {
    private var controller: RemoteController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val controller = shared ?: run {
            prepareRemoteLayout(filesDir)
            val disk = LinkDisk(stateFile = File(filesDir, "phone-state.json"))
            val loaded = disk.load()
            val phone = loaded.copy(androidName = Build.MODEL.ifBlank { loaded.androidName })
            RemoteController(
                directory = LinkDirectory(phone.links, phone.currentId),
                disk = disk,
                phone = phone,
                client = RelayClient(),
                runningStore = RunningSessionStore(File(filesDir, RUNNING_SESSIONS_FILE)),
                bookmarks = PreviewBookmarkStore(File(filesDir, "preview-bookmarks.json")),
                replyStore = RecentReplyStore(File(filesDir, RECENT_REPLIES_FILE)),
            ).also { shared = it }
        }
        controller.bindContext(applicationContext)
        controller.onRunningChanged = { _, finished ->
            refreshSessionWidgets(applicationContext)
            finished.forEach { showFinishedNotification(applicationContext, it) }
        }
        this.controller = controller
        setContent {
            TocodeTheme {
                RemoteScreen(controller)
            }
        }
        openIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        controller?.ensureConnected()
        refreshSessionWidgets(applicationContext)
    }

    private fun openIntent(intent: Intent?) {
        val tab = widgetTab(intent?.getStringExtra(EXTRA_TAB))
        if (tab != null) {
            intent?.removeExtra(EXTRA_TAB)
            controller?.openTabFrom(tab)
            return
        }
        val open = sessionOpenFrom(
            intent?.getStringExtra(EXTRA_LINK_ID),
            intent?.getStringExtra(EXTRA_PROFILE),
            intent?.getStringExtra(EXTRA_PROJECT_ID),
            intent?.getStringExtra(EXTRA_PROJECT_NAME),
            intent?.getStringExtra(EXTRA_SESSION_ID),
            intent?.getStringExtra(EXTRA_SESSION_TITLE),
        ) ?: return
        controller?.openFrom(open)
    }

    private companion object {
        /** 中转每个关联只留一条连接；界面重建时新建一套会和旧的互相挤掉。 */
        var shared: RemoteController? = null
    }
}
