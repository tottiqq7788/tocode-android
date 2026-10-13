package app.tocode.android

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File

class MainActivity : ComponentActivity() {
    private var controller: RemoteController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
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
        controller.connectAll()
        this.controller = controller
        setContent {
            var revision by remember { mutableIntStateOf(0) }
            val main = remember { Handler(Looper.getMainLooper()) }
            DisposableEffect(controller) {
                controller.onChanged = { main.post { revision += 1 } }
                onDispose { controller.onChanged = null }
            }
            TocodeTheme {
                RemoteScreen(controller, revision)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller?.ensureConnected()
    }
}
