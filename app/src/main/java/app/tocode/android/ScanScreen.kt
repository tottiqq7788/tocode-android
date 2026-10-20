package app.tocode.android

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

@Composable
fun ScanScreen(controller: RemoteController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var paste by remember { mutableStateOf("") }
    val analyzer = remember { QrAnalyzer() }
    analyzer.onCode = controller::submitCode
    val notice = controller.scanNotice
    val pairing = controller.pairing
    LaunchedEffect(notice, pairing) {
        if (!pairing) analyzer.handled = false
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = false
        onDispose { bars?.isAppearanceLightStatusBars = true }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
            DisposableEffect(Unit) {
                onDispose { runCatching { providerFuture.get().unbindAll() } }
            }
            AndroidView(
                factory = { viewContext ->
                    val previewView = PreviewView(viewContext)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(ContextCompat.getMainExecutor(viewContext), analyzer) }
                        provider.unbindAll()
                        runCatching {
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        }
                    }, ContextCompat.getMainExecutor(viewContext))
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 140.dp)
                    .size(250.dp)
                    .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(32.dp)),
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(bottom = 160.dp, start = 40.dp, end = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("需要相机权限才能扫码", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "也可以在下方直接粘贴配对内容",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                PrimaryButton("允许使用相机", onClick = { launcher.launch(Manifest.permission.CAMERA) })
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))),
        )
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = controller::closeScan) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Color.White)
                }
            }
            Spacer(Modifier.width(4.dp))
            Text("绑定 Mac", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Paper)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text("对准 Mac 上的二维码", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text("在 Mac 菜单栏右键 Tocode →「应用 → 绑定应用」", style = MaterialTheme.typography.bodyMedium, color = Slate)
            if (pairing) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PineSoft).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = Pine, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("正在绑定…", style = MaterialTheme.typography.bodyMedium, color = Pine)
                }
            } else if (notice.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Banner(notice, Tone.Bad)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = paste,
                    onValueChange = { paste = it },
                    placeholder = { Text("或粘贴 tocode-app:// 配对内容") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Pine,
                        unfocusedBorderColor = Hairline,
                        cursorColor = Pine,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                PrimaryButton(
                    text = "绑定",
                    onClick = { controller.submitCode(paste) },
                    enabled = paste.isNotBlank(),
                    busy = pairing,
                )
            }
        }
    }
}

private class QrAnalyzer : ImageAnalysis.Analyzer {
    var onCode: (String) -> Unit = {}
    @Volatile var handled = false
    private val scanner = BarcodeScanning.getClient()

    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null || handled) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val raw = codes.firstNotNullOfOrNull { it.rawValue?.takeIf { value -> value.startsWith("tocode-app://") } }
                    ?: return@addOnSuccessListener
                if (!handled) {
                    handled = true
                    onCode(raw)
                }
            }
            .addOnCompleteListener { image.close() }
    }
}
