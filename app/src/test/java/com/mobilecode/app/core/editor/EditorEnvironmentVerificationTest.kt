package com.mobilecode.app.core.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EditorEnvironmentVerificationTest {

    @Test
    fun `test auto-closing all six bracket and quote pairs`() {
        val pairs = listOf(
            '(' to "()",
            '{' to "{}",
            '[' to "[]",
            '"' to "\"\"",
            '\'' to "''",
            '`' to "``"
        )

        for ((openChar, expectedText) in pairs) {
            val initial = TextFieldValue("", TextRange(0))
            val typed = TextFieldValue(openChar.toString(), TextRange(1))
            val result = applyEditorSmartEdits(initial, typed)
            assertEquals("Auto-close failed for $openChar", expectedText, result.text)
            assertEquals("Cursor should be placed inside $expectedText", 1, result.selection.start)
        }
    }

    @Test
    fun `test auto-indentation and block expansion between curly braces`() {
        val before = TextFieldValue("function hello() {}", TextRange(18))
        val afterEnter = TextFieldValue("function hello() {\n}", TextRange(19))
        val result = applyEditorSmartEdits(before, afterEnter)

        val expected = "function hello() {\n    \n}"
        assertEquals(expected, result.text)
        assertEquals("function hello() {\n    ".length, result.selection.start)
    }

    @Test
    fun `test smart backspace deletes both characters of empty pair`() {
        val initial = TextFieldValue("()", TextRange(1))
        val afterBackspace = TextFieldValue(")", TextRange(0))
        val result = applyEditorSmartEdits(initial, afterBackspace)
        assertEquals("", result.text)
        assertEquals(0, result.selection.start)
    }

    @Test
    fun `test matching bracket pair detection`() {
        val code = "const arr = [1, (2 + 3)];"
        val match = findMatchingBracketPair(code, 12) // at '['
        assertNotNull(match)
        assertEquals(12 to 23, match)
    }
}
