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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import okhttp3.WebSocket
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

class RemoteController(
    private val directory: LinkDirectory,
    private val disk: LinkDisk,
    private var phone: PhoneState,
    private val client: RelayClient,
) {
    var onChanged: (() -> Unit)? = null
    var scanning = false
    var opened = false
    var notice = ""
    var selectedId: String? = directory.currentId
    val online = linkedMapOf<String, Boolean>()
    val pending = linkedMapOf<String, String>()
    val lastResult = linkedMapOf<String, String>()
    private val sockets = linkedMapOf<String, WebSocket>()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun links(): List<AppLink> = directory.all()

    fun selected(): AppLink? = selectedId?.let { id -> directory.all().firstOrNull { it.id == id } }

    fun connectAll() {
        directory.all().forEach { connect(it) }
    }

    fun ensureConnected() {
        directory.all().filter { online[it.id] != true }.forEach { connect(it) }
    }

    fun openScan() {
        scanning = true
        notice = ""
        onChanged?.invoke()
    }

    fun closeScan() {
        scanning = false
        onChanged?.invoke()
    }

    fun select(id: String) {
        directory.select(id)
        selectedId = id
        opened = true
        persist()
    }

    fun closeDevice() {
        opened = false
        onChanged?.invoke()
    }

    fun submitCode(raw: String) {
        val payload = parsePairingPayload(raw)
        if (payload == null) {
            notice = "这不是有效的配对二维码"
            onChanged?.invoke()
            return
        }
        worker.execute {
            runCatching {
                client.redeem(payload.relay, payload.code, phone.androidId, phone.androidName)
            }.onSuccess { link ->
                main.post {
                    directory.upsert(link)
                    selectedId = link.id
                    scanning = false
                    notice = ""
                    persist()
                    connect(link)
                }
            }.onFailure {
                main.post {
                    notice = "关联失败"
                    onChanged?.invoke()
                }
            }
        }
    }

    fun send(action: String) {
        val link = selected() ?: return
        val requestId = UUID.randomUUID().toString()
        pending[link.id] = requestId
        lastResult[link.id] = "执行中"
        onChanged?.invoke()
        val socket = sockets[link.id]
        if (socket == null || online[link.id] != true) {
            pending.remove(link.id)
            lastResult[link.id] = "这台 Mac 还没连上"
            onChanged?.invoke()
            return
        }
        val body = JSONObject()
            .put("op", "command")
            .put("link_id", link.id)
            .put("request_id", requestId)
            .put("action", action)
        socket.send(body.toString())
    }

    fun unlink(id: String) {
        val link = directory.all().firstOrNull { it.id == id } ?: return
        sockets.remove(id)?.close(1000, "unlink")
        online.remove(id)
        directory.remove(id)
        if (selectedId == id) {
            selectedId = directory.currentId
            opened = false
        }
        persist()
        worker.execute { runCatching { client.delete(link) } }
    }

    private fun connect(link: AppLink) {
        sockets.remove(link.id)?.close(1000, "reconnect")
        sockets[link.id] = client.open(link.relay, link.token, { message ->
            handle(link.id, message)
        }) { connected ->
            online[link.id] = connected
            onChanged?.invoke()
        }
    }

    private fun handle(linkId: String, message: JSONObject) {
        when (message.optString("op")) {
            "result" -> {
                if (pending[linkId] == message.optString("request_id") || pending[linkId] != null) {
                    pending.remove(linkId)
                }
                val ok = message.optBoolean("ok")
                lastResult[linkId] = if (ok) message.optString("text") else message.optString("text", "失败")
            }
            "error" -> {
                pending.remove(linkId)
                lastResult[linkId] = message.optString("message", "失败")
            }
            "link_revoked" -> {
                sockets.remove(linkId)?.close(1000, "revoked")
                directory.remove(linkId)
                if (selectedId == linkId) {
                    selectedId = directory.currentId
                    opened = false
                }
                persist()
            }
        }
        onChanged?.invoke()
    }

    private fun persist() {
        phone = phone.copy(links = directory.all(), currentId = selectedId)
        disk.save(phone)
        onChanged?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RemoteScreen(controller: RemoteController, revision: Int) {
    val ignored = revision
    if (ignored < 0) return
    val colors = MaterialTheme.colorScheme
    if (controller.scanning) {
        ScanScreen(
            onCode = controller::submitCode,
            onClose = controller::closeScan,
            notice = controller.notice,
        )
        return
    }
    val selected = controller.selected()
    if (controller.opened && selected != null) {
        DeviceScreen(controller, selected)
        return
    }
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("遥控器", color = colors.onBackground) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = controller::openScan) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = "扫码")
            }
        },
    ) { padding ->
        if (controller.links().isEmpty()) {
            Text(
                "还没有 Mac。点右下角扫码关联。",
                modifier = Modifier.padding(padding).padding(20.dp),
                color = colors.onBackground,
                fontSize = 16.sp,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(controller.links()) { link ->
                    val state = if (controller.online[link.id] == true) "在线" else "离线"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { controller.select(link.id) },
                                onLongClick = { controller.unlink(link.id) },
                            )
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        Text(link.macName, fontSize = 18.sp, color = colors.onBackground)
                        Text(state, fontSize = 14.sp, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceScreen(controller: RemoteController, link: AppLink) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(link.macName, color = colors.onBackground) },
                navigationIcon = {
                    IconButton(onClick = controller::closeDevice) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    controller.lastResult[link.id] ?: "点一个按钮",
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = colors.onBackground,
                    fontSize = 15.sp,
                )
            }
            items(remoteActions) { (action, title) ->
                Button(
                    onClick = { controller.send(action) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(title) }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanScreen(
    onCode: (String) -> Unit,
    onClose: () -> Unit,
    notice: String,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var paste by remember { mutableStateOf("") }
    val analyzer = remember { QrAnalyzer() }
    analyzer.onCode = onCode
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("扫码关联", color = colors.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).navigationBarsPadding(),
        ) {
            if (!granted) {
                Text("需要相机才能扫码，也可以在下面粘贴配对内容。", modifier = Modifier.padding(top = 12.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("允许相机")
                }
                Spacer(Modifier.weight(1f))
            } else {
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
                                it.setAnalyzer(ContextCompat.getMainExecutor(viewContext), analyzer)
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
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp),
                )
            }
            if (notice.isNotBlank()) {
                Text(notice, color = colors.error, modifier = Modifier.padding(top = 8.dp))
            }
            OutlinedTextField(
                value = paste,
                onValueChange = { paste = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                label = { Text("配对内容") },
            )
            Button(onClick = { onCode(paste) }, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                Text("关联")
            }
        }
    }
}

private class QrAnalyzer : ImageAnalysis.Analyzer {
    var onCode: (String) -> Unit = {}
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
