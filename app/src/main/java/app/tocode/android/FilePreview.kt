package app.tocode.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FilePreviewScreen(preview: AppPreview, onBack: () -> Unit, onLoadMore: () -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(preview.content, preview.truncated, preview.loading, preview.loadingMore) {
        withFrameNanos { }
        snapshotFlow { scroll.value to scroll.maxValue }.collect { (value, max) ->
            if (preview.content.isEmpty() || preview.loading || preview.loadingMore || !preview.truncated) return@collect
            if (max == 0 || value >= max - 480) onLoadMore()
        }
    }
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
            Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(
                    preview.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    preview.path.ifBlank {
                        when {
                            preview.loading -> "正在读取"
                            preview.format == "markdown" -> "Markdown"
                            else -> "文本"
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        when {
            preview.loading && preview.content.isEmpty() -> LoadingState("正在读取…", Modifier.padding(top = 48.dp))
            preview.content.isEmpty() && preview.notice.isNotBlank() -> {
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
            else -> SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .navigationBarsPadding(),
                ) {
                    if (preview.format == "markdown") MarkdownBody(preview.content) else PlainBody(preview.content)
                    if (preview.loadingMore) {
                        Text(
                            "继续读取…",
                            modifier = Modifier.padding(top = 18.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Slate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlainBody(content: String) {
    Text(
        content,
        color = Ink,
        fontFamily = FontFamily.Monospace,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    )
}

@Composable
private fun MarkdownBody(content: String) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        markdownPieces(content).forEach { piece ->
            when (piece) {
                is PreviewPiece.Heading -> Text(
                    piece.text,
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (piece.level <= 2) 22.sp else 18.sp,
                    lineHeight = if (piece.level <= 2) 30.sp else 26.sp,
                )
                is PreviewPiece.Paragraph -> Text(
                    piece.text,
                    color = InkSoft,
                    fontSize = 17.sp,
                    lineHeight = 28.sp,
                )
                is PreviewPiece.Code -> Text(
                    piece.text,
                    color = Ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Fog, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
                is PreviewPiece.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .background(Sky, RoundedCornerShape(2.dp)),
                    )
                    Text(
                        piece.text,
                        color = Slate,
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                is PreviewPiece.Item -> Text(
                    piece.text,
                    color = InkSoft,
                    fontSize = 17.sp,
                    lineHeight = 28.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

internal sealed class PreviewPiece {
    data class Heading(val level: Int, val text: String) : PreviewPiece()
    data class Paragraph(val text: String) : PreviewPiece()
    data class Code(val text: String) : PreviewPiece()
    data class Quote(val text: String) : PreviewPiece()
    data class Item(val text: String) : PreviewPiece()
}

internal fun markdownPieces(source: String): List<PreviewPiece> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val pieces = mutableListOf<PreviewPiece>()
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
            pieces.add(PreviewPiece.Code(code.toString()))
            continue
        }
        if (line.isBlank()) {
            index += 1
            continue
        }
        val heading = Regex("^(#{1,6})\\s+(.*)$").find(line)
        if (heading != null) {
            pieces.add(PreviewPiece.Heading(heading.groupValues[1].length, inlinePlain(heading.groupValues[2])))
            index += 1
            continue
        }
        if (line.startsWith(">")) {
            val quote = StringBuilder()
            while (index < lines.size && lines[index].startsWith(">")) {
                if (quote.isNotEmpty()) quote.append('\n')
                quote.append(inlinePlain(lines[index].removePrefix(">").trim()))
                index += 1
            }
            pieces.add(PreviewPiece.Quote(quote.toString()))
            continue
        }
        if (isListItem(line)) {
            while (index < lines.size && isListItem(lines[index])) {
                val marker = if (lines[index].trimStart().first().isDigit()) {
                    lines[index].trimStart().substringBefore(' ')
                } else {
                    "•"
                }
                pieces.add(PreviewPiece.Item("$marker ${inlinePlain(listText(lines[index]))}"))
                index += 1
            }
            continue
        }
        val paragraph = StringBuilder()
        while (index < lines.size && lines[index].isNotBlank() && !lines[index].startsWith("```") && !lines[index].startsWith(">") && !isListItem(lines[index]) && !lines[index].matches(Regex("^#{1,6}\\s+.*$"))) {
            if (paragraph.isNotEmpty()) paragraph.append('\n')
            paragraph.append(inlinePlain(lines[index]))
            index += 1
        }
        pieces.add(PreviewPiece.Paragraph(paragraph.toString()))
    }
    return pieces
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

private fun inlinePlain(raw: String): String =
    raw.replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)"), "$1")
