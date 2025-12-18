package com.wikimedia.wikiwatch.presentation

import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

@Composable
fun HtmlText(
    html: String,
    onLinkClick: (String) -> Unit = {}
) {
    val annotatedString = parseHtml(html)
    ClickableText(
        text = annotatedString,
        style = TextStyle(
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 11.sp
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations(
                tag = "URL",
                start = offset,
                end = offset
            ).firstOrNull()?.let { annotation ->
                onLinkClick(annotation.item)
            }
        }
    )
}

fun parseHtml(html: String): AnnotatedString {
    return buildAnnotatedString {
        parseHtmlRecursive(html, 0, html.length, this)
    }
}

private fun parseHtmlRecursive(
    html: String,
    start: Int,
    end: Int,
    builder: AnnotatedString.Builder,
    parentStyles: List<StyleInfo> = emptyList()
) {
    var i = start
    val styles = parentStyles.toMutableList()
    
    while (i < end) {
        val tagStart = html.indexOf('<', i)
        
        if (tagStart == -1 || tagStart >= end) {
            // No more tags, add remaining text
            val text = html.substring(i, end)
            addTextWithStyles(text, styles, builder)
            break
        }
        
        // Add text before tag
        if (tagStart > i) {
            val text = html.substring(i, tagStart)
            addTextWithStyles(text, styles, builder)
        }
        
        val tagEnd = html.indexOf('>', tagStart)
        if (tagEnd == -1) break
        
        val tagContent = html.substring(tagStart + 1, tagEnd)
        
        if (tagContent.startsWith("/")) {
            // Closing tag
            val tagName = tagContent.substring(1).trim().lowercase()
            styles.removeAll { it.tagName == tagName }
        } else {
            // Opening tag
            val parts = tagContent.split(" ", limit = 2)
            val tagName = parts[0].lowercase()
            
            when (tagName) {
                "a" -> {
                    val href = extractAttribute(tagContent, "href")
                    val title = extractAttribute(tagContent, "title") ?: href
                    if (href != null) {
                        styles.add(StyleInfo("a", href, title))
                    }
                }
                "b" -> styles.add(StyleInfo("b"))
                "i" -> styles.add(StyleInfo("i"))
            }
        }
        
        i = tagEnd + 1
    }
}

private fun addTextWithStyles(
    text: String,
    styles: List<StyleInfo>,
    builder: AnnotatedString.Builder
) {
    if (text.isEmpty()) return
    
    // Replace non-breaking spaces with regular spaces
    val processedText = text
        .replace("&nbsp;", " ")
        .replace("\u00A0", " ")
    
    val linkStyle = styles.lastOrNull { it.tagName == "a" }
    val isBold = styles.any { it.tagName == "b" }
    val isItalic = styles.any { it.tagName == "i" }
    
    val spanStyle = SpanStyle(
        fontWeight = if (isBold) FontWeight.Bold else null,
        fontStyle = if (isItalic) FontStyle.Italic else null,
        color = if (linkStyle != null) {
            androidx.compose.ui.graphics.Color(0xFF4A9EFF)
        } else {
            androidx.compose.ui.graphics.Color.White
        },
        textDecoration = if (linkStyle != null) TextDecoration.Underline else null
    )
    
    if (linkStyle != null) {
        builder.pushStringAnnotation("URL", linkStyle.href ?: "")
        builder.withStyle(spanStyle) {
            builder.append(processedText)
        }
        builder.pop()
    } else {
        builder.withStyle(spanStyle) {
            builder.append(processedText)
        }
    }
}

private data class StyleInfo(
    val tagName: String,
    val href: String? = null,
    val title: String? = null
)

private fun extractAttribute(tagContent: String, attributeName: String): String? {
    val pattern = Pattern.compile("$attributeName=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
    val matcher = pattern.matcher(tagContent)
    return if (matcher.find()) {
        matcher.group(1)
    } else {
        null
    }
}
