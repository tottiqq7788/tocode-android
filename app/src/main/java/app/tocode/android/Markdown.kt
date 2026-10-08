package app.tocode.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class ListItems(val ordered: Boolean, val items: List<String>) : MarkdownBlock()
    data class Code(val text: String) : MarkdownBlock()
}

fun parseMarkdownBlocks(source: String): List<MarkdownBlock> {
    val lines = source.replace("\r\n", "\n").split('\n')
    val out = mutableListOf<MarkdownBlock>()
    val para = StringBuilder()
    val list = mutableListOf<String>()
    val code = StringBuilder()
    var ordered: Boolean? = null
    var inCode = false

    fun flushPara() {
        val text = para.toString().trim()
        if (text.isNotEmpty()) out += MarkdownBlock.Paragraph(text)
        para.clear()
    }

    fun flushList() {
        if (list.isNotEmpty()) {
            out += MarkdownBlock.ListItems(ordered == true, list.toList())
        }
        list.clear()
        ordered = null
    }

    for (line in lines) {
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                out += MarkdownBlock.Code(code.toString().trimEnd())
                code.clear()
                inCode = false
            } else {
                flushPara()
                flushList()
                inCode = true
            }
            continue
        }
        if (inCode) {
            if (code.isNotEmpty()) code.append('\n')
            code.append(line)
            continue
        }
        val trimmed = line.trim()
        val heading = Regex("^(#{1,3})\\s+(.+)$").matchEntire(trimmed)
        val ul = Regex("^[-*]\\s+(.+)$").matchEntire(trimmed)
        val ol = Regex("^\\d+\\.\\s+(.+)$").matchEntire(trimmed)
        when {
            heading != null -> {
                flushPara()
                flushList()
                out += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            }
            ul != null -> {
                flushPara()
                if (ordered == true) flushList()
                ordered = false
                list += ul.groupValues[1]
            }
            ol != null -> {
                flushPara()
                if (ordered == false) flushList()
                ordered = true
                list += ol.groupValues[1]
            }
            trimmed.isEmpty() -> {
                flushPara()
                flushList()
            }
            else -> {
                flushList()
                if (para.isNotEmpty()) para.append('\n')
                para.append(line.trimEnd())
            }
        }
    }
    if (inCode) out += MarkdownBlock.Code(code.toString().trimEnd())
    flushPara()
    flushList()
    return out
}

fun annotateInlineMarkdown(text: String, codeBackground: Color): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val regex = Regex("""(\*\*[^*]+?\*\*)|(\*[^*]+?\*)|(`[^`]+`)""")
    var last = 0
    for (match in regex.findAll(text)) {
        if (match.range.first > last) {
            builder.append(text.substring(last, match.range.first))
        }
        val token = match.value
        when {
            token.startsWith("**") -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                builder.append(token.removeSurrounding("**"))
                builder.pop()
            }
            token.startsWith("`") -> {
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackground.copy(alpha = 0.16f),
                    ),
                )
                builder.append(token.removeSurrounding("`"))
                builder.pop()
            }
            else -> {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                builder.append(token.removeSurrounding("*"))
                builder.pop()
            }
        }
        last = match.range.last + 1
    }
    if (last < text.length) builder.append(text.substring(last))
    return builder.toAnnotatedString()
}

@Composable
fun MarkdownText(text: String) {
    val colors = MaterialTheme.colorScheme
    val codeBg = colors.surface
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        parseMarkdownBlocks(text).forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> Text(
                    annotateInlineMarkdown(block.text, codeBg),
                    color = colors.onBackground,
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                )
                is MarkdownBlock.Heading -> Text(
                    annotateInlineMarkdown(block.text, codeBg),
                    color = colors.onBackground,
                    fontSize = if (block.level == 1) 22.sp else if (block.level == 2) 19.sp else 17.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                is MarkdownBlock.ListItems -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { index, item ->
                        val mark = if (block.ordered) "${index + 1}." else "•"
                        Text(
                            annotateInlineMarkdown("$mark $item", codeBg),
                            color = colors.onBackground,
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                        )
                    }
                }
                is MarkdownBlock.Code -> Text(
                    block.text,
                    color = colors.onBackground,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
