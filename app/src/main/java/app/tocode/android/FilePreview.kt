package app.tocode.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FilePreviewScreen(
    preview: AppPreview,
    bookmark: PreviewBookmark?,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onScroll: (scroll: Int, loaded: Int) -> Unit,
) {
    val scroll = rememberScrollState()
    val mark = remember(preview.path) { bookmark }
    var placed by remember(preview.path) { mutableStateOf(mark == null || mark.scroll <= 0) }
    LaunchedEffect(
        preview.path,
        preview.format,
        preview.content.length,
        preview.truncated,
        preview.loading,
        preview.loadingMore,
        preview.notice,
    ) {
        if (preview.format == "image") return@LaunchedEffect
        snapshotFlow { scroll.value to scroll.maxValue }.collectLatest { (value, max) ->
            val length = preview.content.length
            if (length == 0 || preview.loading) return@collectLatest
            val target = mark
            if (!placed && target != null && target.scroll > 0) {
                val caughtUp = !preview.loadingMore && (
                    length >= target.loaded || !preview.truncated || max >= target.scroll
                    )
                if (!caughtUp) {
                    if (preview.truncated && !preview.loadingMore) onLoadMore()
                    return@collectLatest
                }
                if (max <= 0) return@collectLatest
                scroll.scrollTo(target.scroll.coerceIn(0, max))
                placed = true
                return@collectLatest
            }
            if (max > 0 && value >= max - 480 && preview.truncated && !preview.loadingMore) onLoadMore()
            if (placed) {
                delay(200)
                onScroll(scroll.value, length)
            }
        }
    }
    DisposableEffect(preview.path) {
        onDispose {
            if (preview.format != "image" && placed && preview.content.isNotEmpty()) {
                onScroll(scroll.value, preview.content.length)
            }
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
                            preview.format == "image" -> "图片"
                            preview.format == "pdf" -> "PDF"
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
            preview.format == "image" -> ImageBody(
                preview.content,
                preview.name,
                mark,
                { pixels -> if (pixels > 0) onScroll(pixels, 0) },
                Modifier.weight(1f).fillMaxWidth(),
            )
            preview.format == "pdf" -> PdfBody(
                preview.content,
                preview.path.ifBlank { preview.name },
                mark,
                { pixels -> if (pixels > 0) onScroll(pixels, 0) },
                Modifier.weight(1f).fillMaxWidth(),
            )
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
private fun ImageBody(
    content: String,
    name: String,
    bookmark: PreviewBookmark?,
    onScroll: (Int) -> Unit,
    modifier: Modifier,
) {
    val bitmap = remember(content) { decodePreviewImage(content) }
    val scroll = rememberScrollState()
    if (bitmap == null) {
        EmptyState(
            icon = Icons.Outlined.ErrorOutline,
            title = "打不开",
            subtitle = "这张图没有显示出来。",
            tone = Tone.Bad,
        )
        return
    }
    LaunchedEffect(content) {
        val target = bookmark?.scroll ?: 0
        if (target > 0) {
            val max = snapshotFlow { scroll.maxValue }.first { it > 0 }
            scroll.scrollTo(target.coerceAtMost(max))
        }
    }
    DisposableEffect(content) {
        onDispose { if (scroll.value > 0) onScroll(scroll.value) }
    }
    LaunchedEffect(content) {
        snapshotFlow { scroll.value }.collectLatest { value ->
            if (value <= 0) return@collectLatest
            delay(200)
            onScroll(scroll.value)
        }
    }
    Column(
        modifier = modifier
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .navigationBarsPadding(),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()),
        )
    }
}

fun decodePreviewImage(content: String): Bitmap? {
    return try {
        val bytes = Base64.decode(content, Base64.DEFAULT)
        if (bytes.isEmpty()) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: IllegalArgumentException) {
        null
    }
}

@Composable
private fun PdfBody(
    content: String,
    cacheKey: String,
    bookmark: PreviewBookmark?,
    onScroll: (Int) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val pages = remember(content, cacheKey) { renderPdfPages(context.cacheDir, cacheKey, content) }
    val scroll = rememberScrollState()
    if (pages == null) {
        EmptyState(
            icon = Icons.Outlined.ErrorOutline,
            title = "打不开",
            subtitle = "这个 PDF 没有显示出来。",
            tone = Tone.Bad,
        )
        return
    }
    if (pages.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.ErrorOutline,
            title = "空文件",
            subtitle = "这个 PDF 里没有页面。",
        )
        return
    }
    LaunchedEffect(content) {
        val target = bookmark?.scroll ?: 0
        if (target > 0) {
            val max = snapshotFlow { scroll.maxValue }.first { it > 0 }
            scroll.scrollTo(target.coerceAtMost(max))
        }
    }
    DisposableEffect(content) {
        onDispose { if (scroll.value > 0) onScroll(scroll.value) }
    }
    LaunchedEffect(content) {
        snapshotFlow { scroll.value }.collectLatest { value ->
            if (value <= 0) return@collectLatest
            delay(200)
            onScroll(scroll.value)
        }
    }
    Column(
        modifier = modifier
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        pages.forEachIndexed { index, bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "第 ${index + 1} 页",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat())
                    .border(1.dp, Hairline, RoundedCornerShape(8.dp)),
            )
        }
    }
}

private fun renderPdfPages(cacheDir: File, cacheKey: String, content: String): List<Bitmap>? {
    return try {
        val bytes = Base64.decode(content, Base64.DEFAULT)
        if (bytes.isEmpty() || !bytes.copyOfRange(0, minOf(4, bytes.size)).contentEquals("%PDF".toByteArray())) {
            return null
        }
        val folder = File(cacheDir, "pdf-preview").apply { mkdirs() }
        val safe = cacheKey.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "doc" }
        val file = File(folder, "$safe.pdf")
        file.writeBytes(bytes)
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        val pages = mutableListOf<Bitmap>()
        try {
            for (index in 0 until renderer.pageCount) {
                val page = renderer.openPage(index)
                val width = (page.width * 2).coerceAtLeast(1)
                val height = (page.height * 2).coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pages.add(bitmap)
            }
        } finally {
            renderer.close()
            descriptor.close()
        }
        pages
    } catch (_: Exception) {
        null
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
internal fun MarkdownBody(content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                is PreviewPiece.Table -> MarkdownTable(piece)
                is PreviewPiece.Mermaid -> MermaidView(piece.source)
                is PreviewPiece.Image -> Text(
                    piece.alt.ifBlank { piece.path },
                    color = Sky,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
internal fun MarkdownTable(table: PreviewPiece.Table) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .border(1.dp, Hairline, RoundedCornerShape(10.dp))
            .padding(1.dp),
    ) {
        TableRow(table.headers, header = true)
        table.rows.forEach { row ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
            TableRow(row, header = false)
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            Text(
                cell,
                color = if (header) Ink else InkSoft,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier
                    .width(120.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

internal sealed class PreviewPiece {
    data class Heading(val level: Int, val text: String) : PreviewPiece()
    data class Paragraph(val text: String) : PreviewPiece()
    data class Code(val text: String) : PreviewPiece()
    data class Quote(val text: String) : PreviewPiece()
    data class Item(val text: String) : PreviewPiece()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : PreviewPiece()
    data class Mermaid(val source: String) : PreviewPiece()
    data class Image(val path: String, val alt: String) : PreviewPiece()
}

private val markdownImageRegex = Regex("""!\[([^]]*)]\(([^)]+)\)""")

internal fun markdownImagePaths(source: String): List<String> =
    markdownImageRegex.findAll(source)
        .map { it.groupValues[2].trim() }
        .filter { isProjectRelativeImagePath(it) }
        .distinct()
        .toList()

internal fun isProjectRelativeImagePath(path: String): Boolean {
    if (path.startsWith("/") || path.contains("://") || path.contains('\n')) return false
    val parts = path.split('/')
    if (parts.isEmpty() || parts.any { it.isEmpty() || it == "." || it == ".." }) return false
    val ext = path.substringAfterLast('.', "").lowercase()
    return ext in setOf("png", "jpg", "jpeg", "gif", "webp", "heic", "heif")
}

internal fun stripMarkdownImages(source: String): String =
    markdownImageRegex.replace(source) { match ->
        val alt = match.groupValues[1].trim()
        alt.ifBlank { "" }
    }

internal fun markdownPieces(source: String): List<PreviewPiece> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val pieces = mutableListOf<PreviewPiece>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        if (line.startsWith("```")) {
            val lang = line.removePrefix("```").trim().lowercase()
            val code = StringBuilder()
            index += 1
            while (index < lines.size && !lines[index].startsWith("```")) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[index])
                index += 1
            }
            if (index < lines.size) index += 1
            if (lang == "mermaid") {
                pieces.add(PreviewPiece.Mermaid(code.toString()))
            } else {
                pieces.add(PreviewPiece.Code(code.toString()))
            }
            continue
        }
        if (line.isBlank()) {
            index += 1
            continue
        }
        val imageOnly = markdownImageRegex.matchEntire(line.trim())
        if (imageOnly != null) {
            val path = imageOnly.groupValues[2].trim()
            if (isProjectRelativeImagePath(path)) {
                pieces.add(PreviewPiece.Image(path, imageOnly.groupValues[1].trim()))
                index += 1
                continue
            }
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
        if (index + 1 < lines.size && looksLikeTableRow(line) && isTableSeparator(lines[index + 1])) {
            val headers = splitTableRow(line).map { inlinePlain(it) }
            index += 2
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && looksLikeTableRow(lines[index])) {
                rows.add(splitTableRow(lines[index]).map { inlinePlain(it) })
                index += 1
            }
            pieces.add(PreviewPiece.Table(headers, rows))
            continue
        }
        val paragraph = StringBuilder()
        while (
            index < lines.size &&
            lines[index].isNotBlank() &&
            !lines[index].startsWith("```") &&
            !lines[index].startsWith(">") &&
            !isListItem(lines[index]) &&
            !lines[index].matches(Regex("^#{1,6}\\s+.*$")) &&
            !(index + 1 < lines.size && looksLikeTableRow(lines[index]) && isTableSeparator(lines[index + 1]))
        ) {
            if (paragraph.isNotEmpty()) paragraph.append('\n')
            paragraph.append(inlinePlain(stripMarkdownImages(lines[index])))
            index += 1
        }
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) pieces.add(PreviewPiece.Paragraph(text))
    }
    return pieces
}

internal fun looksLikeTableRow(line: String): Boolean =
    line.contains('|') && splitTableRow(line).isNotEmpty()

internal fun isTableSeparator(line: String): Boolean {
    val cells = splitTableRow(line)
    return cells.isNotEmpty() && cells.all { it.matches(Regex("^:?-{1,}:?$")) }
}

internal fun splitTableRow(line: String): List<String> {
    var raw = line.trim()
    if (raw.startsWith("|")) raw = raw.drop(1)
    if (raw.endsWith("|")) raw = raw.dropLast(1)
    return raw.split('|').map { it.trim() }
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

@Serializable
data class PreviewBookmark(val key: String, val scroll: Int, val loaded: Int)

fun previewBookmarkKey(profile: String, projectId: String, path: String) = "$profile\n$projectId\n$path"

class PreviewBookmarkStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val marks = mutableMapOf<String, PreviewBookmark>()

    init {
        if (file.exists()) {
            runCatching { json.decodeFromString<List<PreviewBookmark>>(file.readText()) }
                .getOrDefault(emptyList())
                .forEach { marks[it.key] = it }
        }
    }

    fun get(key: String): PreviewBookmark? = marks[key]

    fun put(key: String, scroll: Int, loaded: Int) {
        if (key.isBlank() || scroll < 0 || loaded < 0) return
        if (scroll == 0 && loaded == 0) return
        val next = PreviewBookmark(key, scroll, loaded)
        if (marks[key] == next) return
        marks[key] = next
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(marks.values.toList()))
    }
}
