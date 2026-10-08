package app.tocode.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.UUID

class ChatController(
    val directory: LinkDirectory,
    val log: SessionLog,
    private val disk: LinkDisk,
    private val phone: PhoneState,
    private val client: RelayClient,
    private val sockets: MutableMap<String, okhttp3.WebSocket> = mutableMapOf(),
) {
    var notice: String = ""
        private set
    var onChanged: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun select(id: String) {
        if (directory.select(id)) persist()
    }

    fun remove(id: String) {
        sockets.remove(id)?.close(1000, null)
        directory.remove(id)
        persist()
    }

    fun send(text: String) {
        val link = directory.current() ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val line = ChatLine(outgoing = true, text = trimmed)
        log.append(link.id, line)
        disk.appendHistory(link.id, line)
        val socket = sockets[link.id]
        if (socket == null) {
            appendSystem(link.id, "这条关联还没连上")
            return
        }
        val payload = org.json.JSONObject()
            .put("op", "text")
            .put("link_id", link.id)
            .put("message_id", UUID.randomUUID().toString())
            .put("text", trimmed)
        socket.send(payload.toString())
    }

    fun redeem(raw: String, onDone: () -> Unit) {
        val payload = parsePairingPayload(raw)
        if (payload == null) {
            notice = "这不是应用关联二维码"
            onDone()
            return
        }
        Thread {
            try {
                val link = client.redeem(payload.relay, payload.code, phone.androidId, phone.androidName)
                main.post {
                    directory.upsert(link)
                    connect(link)
                    persist()
                    notice = ""
                    onDone()
                    onChanged?.invoke()
                }
            } catch (error: Exception) {
                main.post {
                    notice = "配对没有完成"
                    onDone()
                    onChanged?.invoke()
                }
            }
        }.start()
    }

    fun connectAll() {
        directory.all().forEach(::connect)
    }

    private fun connect(link: AppLink) {
        sockets[link.id]?.close(1000, null)
        sockets[link.id] = client.open(link.relay, link.token) { message ->
            main.post {
                if (message.optString("op") == "reply" && message.optString("link_id") == link.id) {
                    val text = message.optString("text")
                    if (text.isNotBlank()) {
                        val line = ChatLine(outgoing = false, text = text)
                        log.append(link.id, line)
                        disk.appendHistory(link.id, line)
                        onChanged?.invoke()
                    }
                } else if (message.optString("op") == "error") {
                    appendSystem(link.id, message.optString("message", "没有送达"))
                }
            }
        }
    }

    private fun appendSystem(linkId: String, text: String) {
        val line = ChatLine(outgoing = false, text = text)
        log.append(linkId, line)
        disk.appendHistory(linkId, line)
        onChanged?.invoke()
    }

    private fun persist() {
        disk.save(
            phone.copy(
                links = directory.all(),
                currentId = directory.currentId,
            )
        )
    }
}

@Composable
fun ChatScreen(controller: ChatController, revision: Int, onChanged: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    var paste by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    val current = if (revision >= 0) controller.directory.current() else null
    val lines = current?.let { controller.log.lines(it.id) }.orEmpty()
    if (scanning) {
        ScanScreen(
            onCode = { raw ->
                scanning = false
                controller.redeem(raw) { onChanged() }
            },
            onClose = { scanning = false },
        )
        return
    }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("总切换")
            if (controller.directory.all().isEmpty()) {
                Text("还没有关联。扫码或粘贴 Mac 上的配对内容。")
            }
            controller.directory.all().forEach { link ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = link.id == controller.directory.currentId,
                        onClick = {
                            controller.select(link.id)
                            onChanged()
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(link.displayName.ifBlank { link.roleName })
                        Text("${link.macName} · ${link.roleName}")
                    }
                    Button(onClick = {
                        controller.remove(link.id)
                        onChanged()
                    }) { Text("删除") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scanning = true }) { Text("扫码") }
            }
            OutlinedTextField(
                value = paste,
                onValueChange = { paste = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("或粘贴配对内容") },
            )
            Button(onClick = {
                val raw = paste
                controller.redeem(raw) { onChanged() }
                paste = ""
            }) { Text("关联") }
            if (controller.notice.isNotBlank()) Text(controller.notice)
            HorizontalDivider()
            Text(current?.let { "当前会话 ${it.macName} · ${it.roleName}" } ?: "当前没有会话")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(lines) { line ->
                    Text(
                        text = if (line.outgoing) "我：${line.text}" else "角色：${line.text}",
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("文字") },
                )
                Button(
                    onClick = {
                        controller.send(draft)
                        draft = ""
                        onChanged()
                    },
                    enabled = current != null && draft.isNotBlank(),
                ) { Text("发送") }
            }
        }
    }
}

@Composable
private fun ScanScreen(onCode: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            Button(onClick = onClose) { Text("返回") }
        }
        if (!granted) {
            Text("需要相机才能扫码，也可以回到上一页粘贴配对内容。")
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("允许相机") }
            return
        }
        AndroidView(
            factory = { viewContext ->
                val previewView = PreviewView(viewContext)
                val future = ProcessCameraProvider.getInstance(viewContext)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder().build().also {
                        it.setAnalyzer(
                            ContextCompat.getMainExecutor(viewContext),
                            QrAnalyzer(onCode),
                        )
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        viewContext as LifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }, ContextCompat.getMainExecutor(viewContext))
                previewView
            },
            modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        )
    }
}

private class QrAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient()
    private var handled = false

    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null || handled) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val raw = codes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                if (!handled && raw.startsWith("tocode-app://pair?")) {
                    handled = true
                    onCode(raw)
                }
            }
            .addOnCompleteListener { image.close() }
    }
}
