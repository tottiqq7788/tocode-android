package app.tocode.android

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun FilePreviewScreen(preview: AppPreview, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Paper).padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Ink)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    preview.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        preview.loading || (preview.content.isEmpty() && preview.notice.isNotBlank() && !preview.truncated) -> "预览"
                        preview.format == "markdown" -> "Markdown"
                        else -> "文本"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Slate,
                )
            }
        }
        when {
            preview.loading -> LoadingState("正在读取…", Modifier.padding(top = 48.dp))
            preview.content.isEmpty() && preview.notice.isNotBlank() && !preview.truncated -> {
                EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = "打不开",
                    subtitle = preview.notice,
                    tone = Tone.Bad,
                )
            }
            preview.content.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = "空文件",
                    subtitle = "这个文件里没有内容。",
                )
            }
            preview.format == "markdown" -> MarkdownView(preview.content, Modifier.weight(1f).fillMaxWidth())
            else -> SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    preview.content,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = Ink,
                )
            }
        }
        if (preview.truncated) {
            Banner(preview.notice.ifBlank { "只显示了前面一段" }, Tone.Info, Modifier.padding(16.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun MarkdownView(markdown: String, modifier: Modifier) {
    val html = markdownDocument(markdown)
    AndroidView(
        modifier = modifier.navigationBarsPadding(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
                }
            }
        },
        update = { view ->
            view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        },
    )
}

internal fun markdownDocument(markdown: String): String {
    val body = renderMarkdown(markdown)
    return """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
        body { font: 16px/1.6 -apple-system, sans-serif; color: #1c1c1e; margin: 0; padding: 16px 18px 32px; }
        pre { background: #f4f4f5; padding: 12px; border-radius: 12px; overflow-x: auto; }
        code { font-family: ui-monospace, Menlo, monospace; background: #f4f4f5; padding: 1px 4px; border-radius: 4px; }
        pre code { background: none; padding: 0; }
        blockquote { margin: 0 0 12px; padding-left: 12px; border-left: 3px solid #d0d0d4; color: #555; }
        a { color: #1f7a4d; }
        </style></head><body>$body</body></html>
    """.trimIndent()
}

private fun renderMarkdown(source: String): String {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val html = StringBuilder()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        if (line.startsWith("```")) {
            val code = StringBuilder()
            index += 1
            while (index < lines.size && !lines[index].startsWith("```")) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[index])
                index += 1
            }
            if (index < lines.size) index += 1
            html.append("<pre><code>").append(escapeHtml(code.toString())).append("</code></pre>")
            continue
        }
        if (line.isBlank()) {
            index += 1
            continue
        }
        val heading = Regex("^(#{1,6})\\s+(.*)$").find(line)
        if (heading != null) {
            val level = heading.groupValues[1].length
            html.append("<h").append(level).append(">").append(inlineMarkdown(heading.groupValues[2])).append("</h").append(level).append(">")
            index += 1
            continue
        }
        if (line.startsWith(">")) {
            val quote = StringBuilder()
            while (index < lines.size && lines[index].startsWith(">")) {
                if (quote.isNotEmpty()) quote.append("<br>")
                quote.append(inlineMarkdown(lines[index].removePrefix(">").trim()))
                index += 1
            }
            html.append("<blockquote>").append(quote).append("</blockquote>")
            continue
        }
        if (isListItem(line)) {
            val ordered = line.trimStart().first().isDigit()
            html.append(if (ordered) "<ol>" else "<ul>")
            while (index < lines.size && isListItem(lines[index])) {
                html.append("<li>").append(inlineMarkdown(listText(lines[index]))).append("</li>")
                index += 1
            }
            html.append(if (ordered) "</ol>" else "</ul>")
            continue
        }
        val paragraph = StringBuilder()
        while (index < lines.size && lines[index].isNotBlank() && !lines[index].startsWith("```") && !lines[index].startsWith(">") && !isListItem(lines[index]) && !lines[index].matches(Regex("^#{1,6}\\s+.*$"))) {
            if (paragraph.isNotEmpty()) paragraph.append("<br>")
            paragraph.append(inlineMarkdown(lines[index]))
            index += 1
        }
        html.append("<p>").append(paragraph).append("</p>")
    }
    return html.toString()
}

private fun isListItem(line: String): Boolean {
    val trimmed = line.trimStart()
    return trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.matches(Regex("^\\d+\\.\\s+.*"))
}

private fun listText(line: String): String {
    val trimmed = line.trimStart()
    return when {
        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> trimmed.drop(2)
        else -> trimmed.replace(Regex("^\\d+\\.\\s+"), "")
    }
}

private fun inlineMarkdown(raw: String): String {
    val html = StringBuilder()
    var index = 0
    val code = Regex("`([^`]+)`")
    for (match in code.findAll(raw)) {
        html.append(styleMarkdown(escapeHtml(raw.substring(index, match.range.first))))
        html.append("<code>").append(escapeHtml(match.groupValues[1])).append("</code>")
        index = match.range.last + 1
    }
    html.append(styleMarkdown(escapeHtml(raw.substring(index))))
    return html.toString()
}

private fun styleMarkdown(escaped: String): String {
    var text = Regex("\\*\\*([^*]+)\\*\\*").replace(escaped) { "<strong>${it.groupValues[1]}</strong>" }
    text = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)").replace(text) { "<em>${it.groupValues[1]}</em>" }
    text = Regex("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)").replace(text) {
        "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>"
    }
    return text
}

private fun escapeHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
