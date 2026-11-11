package com.ryzen.tvremote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Paragraph(val text: String) : MdBlock
}

/**
 * Renders the Markdown subset GitHub release notes use: headings, bullet and numbered lists,
 * bold, italic, inline code and links. Anything fancier shows as plain text.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val link = TextLinkStyles(SpanStyle(color = scheme.primary, textDecoration = TextDecoration.Underline))
    val codeBackground = scheme.surfaceContainerHighest

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Heading -> {
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    Text(
                        text = inlineMarkdown(block.text, codeBackground, link),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleMedium
                            2 -> MaterialTheme.typography.titleSmall
                            else -> MaterialTheme.typography.labelLarge
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                }

                is MdBlock.Bullet -> Row(
                    modifier = Modifier.padding(start = (block.indent * 16).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = block.marker,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.width(if (block.marker == "•") 10.dp else 20.dp)
                    )
                    Text(
                        text = inlineMarkdown(block.text, codeBackground, link),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }

                is MdBlock.Paragraph -> Text(
                    text = inlineMarkdown(block.text, codeBackground, link),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

private val headingRegex = Regex("^(#{1,6})\\s+(.*?)\\s*#*$")
private val bulletRegex = Regex("^(\\s*)[-*+]\\s+(.*)$")
private val numberRegex = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
private val ruleRegex = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
private val commentRegex = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

private fun parseMarkdown(source: String): List<MdBlock> {
    val text = source.replace("\r\n", "\n").replace(commentRegex, "")
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flush() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }

    for (raw in text.lines()) {
        // Block quotes are shown as normal text.
        val line = if (raw.trimStart().startsWith(">")) raw.trimStart().removePrefix(">").trim() else raw.trimEnd()
        val heading = headingRegex.matchEntire(line)
        val bullet = bulletRegex.matchEntire(line)
        val number = numberRegex.matchEntire(line)
        when {
            line.isBlank() || ruleRegex.matches(line) -> flush()
            heading != null -> {
                flush()
                blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            }
            bullet != null -> {
                flush()
                blocks += MdBlock.Bullet("•", bullet.groupValues[2], bullet.groupValues[1].length / 2)
            }
            number != null -> {
                flush()
                blocks += MdBlock.Bullet("${number.groupValues[2]}.", number.groupValues[3], number.groupValues[1].length / 2)
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line.trim())
            }
        }
    }
    flush()
    return blocks
}

private fun inlineMarkdown(src: String, codeBackground: Color, link: TextLinkStyles): AnnotatedString =
    buildAnnotatedString { appendInline(src, codeBackground, link) }

private fun AnnotatedString.Builder.appendInline(src: String, codeBackground: Color, link: TextLinkStyles) {
    var i = 0
    while (i < src.length) {
        val c = src[i]
        when {
            src.startsWith("**", i) || src.startsWith("__", i) -> {
                val delimiter = src.substring(i, i + 2)
                val end = src.indexOf(delimiter, i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        appendInline(src.substring(i + 2, end), codeBackground, link)
                    }
                    i = end + 2
                } else {
                    append(c)
                    i++
                }
            }

            c == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                        append(src.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }

            c == '*' && i + 1 < src.length && !src[i + 1].isWhitespace() -> {
                val end = src.indexOf('*', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInline(src.substring(i + 1, end), codeBackground, link)
                    }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }

            c == '[' -> {
                val close = src.indexOf("](", i + 1)
                val end = if (close > i) src.indexOf(')', close + 2) else -1
                if (close > i && end > close) {
                    val label = src.substring(i + 1, close)
                    val url = src.substring(close + 2, end)
                    withLink(LinkAnnotation.Url(url, link)) { append(label) }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }

            else -> {
                append(c)
                i++
            }
        }
    }
}
