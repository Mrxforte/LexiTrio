package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.example.data.model.Language

/**
 * Reusable Compose component that highlights the matched prefix or substring
 * inside dictionary headwords or translations using AnnotatedString and SpanStyle.
 *
 * Fully supports case-insensitive matching, Uzbek apostrophe variants
 * (', ’, ʻ, ʼ, ‘, `), and light/dark theme adaptation.
 */
@Composable
fun HighlightedSearchText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    fontWeight: FontWeight = FontWeight.Bold,
    highlightFontWeight: FontWeight = FontWeight.ExtraBold,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val annotatedString = remember(text, query, highlightColor, textColor) {
        buildHighlightedAnnotatedString(
            text = text,
            query = query,
            highlightColor = highlightColor,
            highlightFontWeight = highlightFontWeight
        )
    }

    Text(
        text = annotatedString,
        style = style,
        fontWeight = fontWeight,
        color = textColor,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
    )
}

/**
 * Builds an [AnnotatedString] highlighting matched query ranges in [text].
 */
fun buildHighlightedAnnotatedString(
    text: String,
    query: String,
    highlightColor: Color,
    highlightFontWeight: FontWeight = FontWeight.ExtraBold
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) {
        return AnnotatedString(text)
    }

    try {
        val normText = Language.normalizeText(text)
        val normQuery = Language.normalizeText(trimmedQuery)

        if (normQuery.isEmpty()) return AnnotatedString(text)

        val matchIndex = normText.indexOf(normQuery)
        if (matchIndex == -1 || matchIndex >= text.length) {
            return AnnotatedString(text)
        }

        val matchEnd = (matchIndex + normQuery.length).coerceAtMost(text.length)
        if (matchIndex >= matchEnd) {
            return AnnotatedString(text)
        }

        return buildAnnotatedString {
            if (matchIndex > 0) {
                append(text.substring(0, matchIndex))
            }
            withStyle(
                style = SpanStyle(
                    color = highlightColor,
                    fontWeight = highlightFontWeight,
                    background = highlightColor.copy(alpha = 0.12f)
                )
            ) {
                append(text.substring(matchIndex, matchEnd))
            }
            if (matchEnd < text.length) {
                append(text.substring(matchEnd))
            }
        }
    } catch (_: Exception) {
        return AnnotatedString(text)
    }
}
