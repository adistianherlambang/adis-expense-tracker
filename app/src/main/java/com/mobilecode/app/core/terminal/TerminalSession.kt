package com.mobilecode.app.core.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class TerminalSession(
    val id: String,
    val title: String,
    val workingDir: File,
    val isAlive: Boolean = true
) {
    private val buffer = StringBuilder()
    private val _outputState = MutableStateFlow("")
    val outputState: StateFlow<String> = _outputState.asStateFlow()

    @Synchronized
    fun append(text: String) {
        val cleanText = stripAnsiCodes(text)
        buffer.append(cleanText)
        if (buffer.length > 50000) {
            buffer.delete(0, 10000)
        }
        _outputState.value = buffer.toString()
    }

    @Synchronized
    fun getHistory(): String = _outputState.value

    @Synchronized
    fun clear() {
        buffer.setLength(0)
        _outputState.value = ""
    }

    private fun stripAnsiCodes(input: String): String {
        return input
            .replace(Regex("\u001B\\[[;\\d]*[ -/]*[@-~]"), "")
            .replace("\r\n", "\n")
    }
}
