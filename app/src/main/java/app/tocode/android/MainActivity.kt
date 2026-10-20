package app.tocode.android

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
        prepareRemoteLayout(filesDir)
        val disk = LinkDisk(stateFile = File(filesDir, "phone-state.json"))
        val loaded = disk.load()
        val phone = loaded.copy(androidName = Build.MODEL.ifBlank { loaded.androidName })
        val controller = RemoteController(
            directory = LinkDirectory(phone.links, phone.currentId),
            disk = disk,
            phone = phone,
            client = RelayClient(),
        )
        this.controller = controller
        setContent {
            TocodeTheme {
                RemoteScreen(controller)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller?.ensureConnected()
    }
}
