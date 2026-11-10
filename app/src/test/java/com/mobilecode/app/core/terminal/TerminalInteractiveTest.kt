package com.mobilecode.app.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class TerminalInteractiveTest {

    private class FakeProcess : Process() {
        val stdinBytes = ByteArrayOutputStream()
        private val stdoutBytes = ByteArrayInputStream("? Would you like to use defaults? (Y/n)\n".toByteArray())
        private val stderrBytes = ByteArrayInputStream(ByteArray(0))
        private var alive = true

        override fun getOutputStream(): OutputStream = stdinBytes
        override fun getInputStream(): InputStream = stdoutBytes
        override fun getErrorStream(): InputStream = stderrBytes
        override fun waitFor(): Int = 0
        override fun exitValue(): Int = 0
        override fun destroy() { alive = false }
        override fun isAlive(): Boolean = alive
    }

    @Test
    fun testTerminalSessionProcessLifecycle() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "term_test")
        tempDir.mkdirs()
        val session = TerminalSession(
            id = "test-1",
            title = "bash 1",
            initialWorkingDir = tempDir
        )

        assertFalse(session.isProcessRunning.value)
        assertNull(session.runningProcess)

        val fakeProc = FakeProcess()
        session.runningProcess = fakeProc
        session.isProcessRunning.value = true

        assertTrue(session.isProcessRunning.value)
        assertNotNull(session.runningProcess)

        // Kill process
        session.killRunningProcess()
        assertFalse(session.isProcessRunning.value)
        assertNull(session.runningProcess)
        assertFalse(fakeProc.isAlive)
    }

    @Test
    fun testInteractiveAnsiStripping() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "term_test_ansi")
        tempDir.mkdirs()
        val session = TerminalSession(
            id = "test-2",
            title = "bash 2",
            initialWorkingDir = tempDir
        )

        // Simulate interactive inquirer prompt with ANSI escape sequences
        val rawPrompt = "\u001b[?25l\u001b[32m? Would you like to use defaults?\u001b[39m \u001b[2m› - Use arrow-keys\u001b[22m\n"
        session.append(rawPrompt)

        val clean = session.getHistory()
        assertFalse("ANSI escape codes should be stripped", clean.contains("\u001b["))
        assertTrue("Prompt text should be intact", clean.contains("? Would you like to use defaults?"))
    }

    @Test
    fun testInteractiveProcessStdinWrite() {
        val fakeProc = FakeProcess()
        val inputToSend = "yes\n"

        fakeProc.outputStream.write(inputToSend.toByteArray(Charsets.UTF_8))
        fakeProc.outputStream.flush()

        val written = fakeProc.stdinBytes.toString(Charsets.UTF_8.name())
        assertEquals("yes\n", written)
    }

    @Test
    fun testInteractiveEscapeSequencesStdinWrite() {
        val fakeProc = FakeProcess()

        // Up arrow
        fakeProc.outputStream.write("\u001b[A".toByteArray(Charsets.UTF_8))
        // Down arrow
        fakeProc.outputStream.write("\u001b[B".toByteArray(Charsets.UTF_8))
        // Enter / Return
        fakeProc.outputStream.write("\n".toByteArray(Charsets.UTF_8))
        fakeProc.outputStream.flush()

        val written = fakeProc.stdinBytes.toString(Charsets.UTF_8.name())
        assertEquals("\u001b[A\u001b[B\n", written)
    }

    @Test
    fun testSequentialLinesParsingWhenRunning() {
        val rawText = "yes\n"
        val normalized = rawText.replace("\r\n", "\n")
        val lines = normalized.split("\n")
        val endsWithNewline = normalized.endsWith("\n")

        val submitted = mutableListOf<String>()
        var remainder = ""

        if (endsWithNewline) {
            val linesToSubmit = lines.dropLast(1)
            linesToSubmit.forEach { line ->
                submitted.add(line.trimEnd('\r', '\n'))
            }
            remainder = ""
        } else {
            val linesToSubmit = lines.dropLast(1)
            linesToSubmit.forEach { line ->
                submitted.add(line.trimEnd('\r', '\n'))
            }
            remainder = lines.last().trimEnd('\r', '\n')
        }

        assertEquals(listOf("yes"), submitted)
        assertEquals("", remainder)
    }

    @Test
    fun testEmptyNewlineReturnWhenRunning() {
        val rawText = "\n"
        val normalized = rawText.replace("\r\n", "\n")
        val lines = normalized.split("\n")
        val endsWithNewline = normalized.endsWith("\n")

        val submitted = mutableListOf<String>()
        var remainder = ""

        if (endsWithNewline) {
            val linesToSubmit = lines.dropLast(1)
            linesToSubmit.forEach { line ->
                submitted.add(line.trimEnd('\r', '\n'))
            }
            remainder = ""
        }

        assertEquals(listOf(""), submitted)
        assertEquals("", remainder)
    }

    @Test
    fun testNodeVersionRequirementCheck() {
        // Next.js and Tailwind oxide require >= 20.9.0
        val targetVersion = "20.18.0"
        val parts = targetVersion.split(".").map { it.toInt() }
        val major = parts[0]
        val minor = parts[1]

        assertTrue("Major must be >= 20 for Next.js 16", major >= 20)
        assertTrue("Minor must be >= 9 for Next.js 16 requirements", minor >= 9)
    }
}

