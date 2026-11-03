package com.mobilecode.app.core.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class TerminalSession(
    val id: String,
    val title: String,
    initialWorkingDir: File,
    val isAlive: Boolean = true
) {
    @Volatile
    var runningProcess: Process? = null

    private val buffer = StringBuilder()

    private val _outputState = MutableStateFlow("")
    val outputState: StateFlow<String> = _outputState.asStateFlow()

    private val _workingDirState = MutableStateFlow(initialWorkingDir)
    val workingDirState: StateFlow<File> = _workingDirState.asStateFlow()

    var workingDir: File
        get() = _workingDirState.value
        set(value) {
            _workingDirState.value = value
        }

    // "SH", "NODE", or "PYTHON"
    private val _replModeState = MutableStateFlow("SH")
    val replModeState: StateFlow<String> = _replModeState.asStateFlow()

    var replMode: String
        get() = _replModeState.value
        set(value) {
            _replModeState.value = value
        }

    @Synchronized
    fun append(text: String) {
        val cleanText = stripAnsiCodes(text)
        buffer.append(cleanText)
        if (buffer.length > 60000) {
            buffer.delete(0, 12000)
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

    fun killRunningProcess() {
        try {
            runningProcess?.destroyForcibly()
        } catch (_: Exception) {}
        runningProcess = null
    }

    private fun stripAnsiCodes(input: String): String {
        return input
            .replace(Regex("\u001B\\[[;\\d]*[ -/]*[@-~]"), "")
            .replace("\r\n", "\n")
    }
}
