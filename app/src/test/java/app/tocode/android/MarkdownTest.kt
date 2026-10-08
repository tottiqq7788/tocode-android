package app.tocode.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test
    fun roleReplyKeepsHeadingsListsAndFences() {
        val blocks = parseMarkdownBlocks(
            """
            # 标题
            - 一项
            这是 **重点**

            ```
            fence
            ```
            """.trimIndent()
        )
        assertTrue(blocks.any { it is MarkdownBlock.Heading && it.text == "标题" })
        val list = blocks.filterIsInstance<MarkdownBlock.ListItems>().single()
        assertEquals(listOf("一项"), list.items)
        assertEquals("fence", blocks.filterIsInstance<MarkdownBlock.Code>().single().text)
        assertTrue(blocks.filterIsInstance<MarkdownBlock.Paragraph>().any { it.text.contains("**重点**") })
    }
}
