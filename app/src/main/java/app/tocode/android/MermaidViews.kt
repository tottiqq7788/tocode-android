package app.tocode.android

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun MermaidView(source: String, modifier: Modifier = Modifier) {
    var fullscreen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 320.dp)
            .background(Fog, RoundedCornerShape(12.dp))
            .clickable { fullscreen = true }
            .padding(8.dp),
    ) {
        MermaidWebView(source = source, interactive = false, modifier = Modifier.fillMaxSize())
        Text(
            "点按全屏查看",
            style = MaterialTheme.typography.labelSmall,
            color = SlateLight,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(Paper.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
    if (fullscreen) {
        MermaidFullscreen(source = source, onDismiss = { fullscreen = false })
    }
}

@Composable
private fun MermaidFullscreen(source: String, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transform = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(0.4f, 6f)
            offset += pan
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0F4F1EA)),
        ) {
            MermaidWebView(
                source = source,
                interactive = false,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .transformable(transform),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clickable(onClick = onDismiss)
                    .background(Paper, RoundedCornerShape(999.dp))
                    .padding(10.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Ink)
            }
            Text(
                "双指缩放 · 拖动画布",
                style = MaterialTheme.typography.labelMedium,
                color = Slate,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MermaidWebView(source: String, interactive: Boolean, modifier: Modifier = Modifier) {
    val html = remember(source) { mermaidHtml(source) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.allowFileAccess = true
                settings.builtInZoomControls = interactive
                settings.displayZoomControls = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                webViewClient = WebViewClient()
                loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
            }
        },
        update = { view ->
            view.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
        },
    )
}

internal fun mermaidHtml(source: String): String {
    val escaped = source
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1"/>
          <script src="mermaid.min.js"></script>
          <style>
            html, body { margin: 0; padding: 8px; background: transparent; }
            .mermaid { display: flex; justify-content: center; }
            svg { max-width: 100%; height: auto; }
          </style>
        </head>
        <body>
          <pre class="mermaid">$escaped</pre>
          <script>
            mermaid.initialize({ startOnLoad: true, theme: "neutral", securityLevel: "strict" });
          </script>
        </body>
        </html>
    """.trimIndent()
}
