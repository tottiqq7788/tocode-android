package app.tocode.android

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val disk = LinkDisk(
            stateFile = File(filesDir, "phone-state.json"),
            historyDirectory = File(filesDir, "history"),
        )
        val (loaded, log) = disk.load()
        val phone = loaded.copy(androidName = Build.MODEL.ifBlank { loaded.androidName })
        val controller = ChatController(
            directory = LinkDirectory(phone.links, phone.currentId),
            log = log,
            disk = disk,
            phone = phone,
            client = RelayClient(),
        )
        controller.connectAll()
        setContent {
            var revision by remember { mutableIntStateOf(0) }
            DisposableEffect(controller) {
                controller.onChanged = { revision += 1 }
                onDispose { controller.onChanged = null }
            }
            TocodeTheme {
                ChatScreen(controller, revision) { revision += 1 }
            }
        }
    }
}
