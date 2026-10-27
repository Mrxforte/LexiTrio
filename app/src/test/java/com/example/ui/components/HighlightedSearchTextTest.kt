package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightedSearchTextTest {

    @Test
    fun buildHighlightedAnnotatedString_highlightsPrefixMatch() {
        val text = "make"
        val query = "ma"
        val result = buildHighlightedAnnotatedString(
            text = text,
            query = query,
            highlightColor = Color.Red,
            highlightFontWeight = FontWeight.Bold
        )

        assertEquals("make", result.text)
        val styles = result.spanStyles
        assertEquals(1, styles.size)
        assertEquals(0, styles[0].start)
        assertEquals(2, styles[0].end)
    }

    @Test
    fun buildHighlightedAnnotatedString_caseInsensitiveMatch() {
        val text = "MANAGE"
        val query = "ma"
        val result = buildHighlightedAnnotatedString(
            text = text,
            query = query,
            highlightColor = Color.Blue
        )

        assertEquals("MANAGE", result.text)
        val styles = result.spanStyles
        assertEquals(1, styles.size)
        assertEquals(0, styles[0].start)
        assertEquals(2, styles[0].end)
    }

    @Test
    fun buildHighlightedAnnotatedString_handlesUzbekApostrophes() {
        val text = "O‘zbekiston"
        val query = "o'zb"
        val result = buildHighlightedAnnotatedString(
            text = text,
            query = query,
            highlightColor = Color.Green
        )

        assertEquals("O‘zbekiston", result.text)
        val styles = result.spanStyles
        assertEquals(1, styles.size)
        assertEquals(0, styles[0].start)
        assertEquals(4, styles[0].end)
    }

    @Test
    fun buildHighlightedAnnotatedString_highlightsSubstringMatchRange() {
        val text = "pineapple"
        val query = "apple"
        val result = buildHighlightedAnnotatedString(
            text = text,
            query = query,
            highlightColor = Color.Yellow
        )

        assertEquals("pineapple", result.text)
        val styles = result.spanStyles
        assertEquals(1, styles.size)
        assertEquals(4, styles[0].start)
        assertEquals(9, styles[0].end)
    }

    @Test
    fun buildHighlightedAnnotatedString_emptyOrLongQueryReturnsUnstyled() {
        val text = "apple"

        val emptyResult = buildHighlightedAnnotatedString(
            text = text,
            query = "",
            highlightColor = Color.Red
        )
        assertTrue(emptyResult.spanStyles.isEmpty())

        val longResult = buildHighlightedAnnotatedString(
            text = text,
            query = "apple pie and more",
            highlightColor = Color.Red
        )
        assertTrue(longResult.spanStyles.isEmpty())
    }
}
