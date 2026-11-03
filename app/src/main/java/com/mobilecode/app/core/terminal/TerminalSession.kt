package com.mobilecode.app.core.terminal

import com.mobilecode.app.core.runtime.DevServerManager
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

    val isProcessRunning = MutableStateFlow(false)

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

        // Automatically detect dev server port in output (e.g. "Local: http://localhost:3000")
        try {
            portDetectionRegex.findAll(cleanText).forEach { match ->
                val portNum = match.groupValues[1].toIntOrNull()
                if (portNum != null && portNum in 1024..65535) {
                    val label = when (portNum) {
                        3000 -> "React Dev Server"
                        5173 -> "Vite Dev Server"
                        8080 -> "Web Dev Server"
                        else -> "Dev Server"
                    }
                    DevServerManager.registerExternalPort(portNum, label, workingDir)
                }
            }
        } catch (_: Exception) {}
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
        isProcessRunning.value = false
    }

    private val portDetectionRegex = Regex("""(?:https?://(?:localhost|127\.0\.0\.1):|port\s+|listening\s+on\s+.*?:)(\d{3,5})""", RegexOption.IGNORE_CASE)

    private fun stripAnsiCodes(input: String): String {
        return input
            .replace(Regex("\u001B\\[[;\\d]*[ -/]*[@-~]"), "")
            .replace(Regex("""(?m)^warning: linker:.*\R?"""), "")
            .replace("\r\n", "\n")
    }
}
