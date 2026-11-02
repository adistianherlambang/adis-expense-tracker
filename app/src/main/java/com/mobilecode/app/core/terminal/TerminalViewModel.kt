package com.mobilecode.app.core.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilecode.app.core.filesystem.WorkspaceManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class TerminalViewModel(
    private val workspaceManager: WorkspaceManager
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _activeSessionIndex = MutableStateFlow(0)
    val activeSessionIndex: StateFlow<Int> = _activeSessionIndex.asStateFlow()

    private val _outputEvents = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64)
    val outputEvents: SharedFlow<Pair<String, String>> = _outputEvents.asSharedFlow()

    private val processMap = mutableMapOf<String, PtyProcess>()
    private var sessionCounter = 0

    val activeSession: TerminalSession?
        get() = _sessions.value.getOrNull(_activeSessionIndex.value)

    init {
        createSession()

        // Automatically switch terminal directory when user opens a workspace folder
        viewModelScope.launch {
            workspaceManager.currentWorkspaceDir.collect { dir ->
                if (dir != null && dir.exists()) {
                    val session = activeSession
                    if (session != null) {
                        processMap[session.id]?.write("cd \"${dir.absolutePath}\"\n")
                        session.append("\n[Workspace switched to ${dir.absolutePath}]\n")
                    }
                }
            }
        }
    }

    fun createSession(customWorkingDir: File? = null): TerminalSession {
        sessionCounter++
        val sessionId = "term-$sessionCounter"
        val workingDir = customWorkingDir
            ?: workspaceManager.currentWorkspaceDir.value
            ?: workspaceManager.getWorkspacesDirectory()

        val newSession = TerminalSession(
            id = sessionId,
            title = "sh $sessionCounter",
            workingDir = workingDir
        )

        // Add to _sessions BEFORE starting PTY so initial output is never lost
        val updated = _sessions.value + newSession
        _sessions.value = updated
        _activeSessionIndex.value = updated.size - 1

        val pty = PtyProcess(
            sessionId = sessionId,
            workingDir = workingDir,
            onOutputReceived = { id, data ->
                newSession.append(data)
                viewModelScope.launch {
                    _outputEvents.emit(Pair(id, data))
                }
            }
        )
        processMap[sessionId] = pty
        pty.start()

        OutputLogger.logSystem("Started terminal session: ${newSession.title} in ${workingDir.name}")
        return newSession
    }

    fun getSessionHistory(sessionId: String): String {
        return _sessions.value.find { it.id == sessionId }?.getHistory() ?: ""
    }

    fun clearActiveSession() {
        activeSession?.clear()
    }

    fun selectSession(index: Int) {
        if (index in _sessions.value.indices) {
            _activeSessionIndex.value = index
        }
    }

    fun closeSession(index: Int) {
        val list = _sessions.value.toMutableList()
        if (index in list.indices) {
            val session = list.removeAt(index)
            processMap[session.id]?.destroy()
            processMap.remove(session.id)
            _sessions.value = list

            val current = _activeSessionIndex.value
            _activeSessionIndex.value = when {
                list.isEmpty() -> {
                    createSession()
                    0
                }
                current >= list.size -> list.size - 1
                else -> current
            }
        }
    }

    fun sendInput(data: String) {
        val session = activeSession ?: return
        processMap[session.id]?.write(data)
    }

    fun sendCommand(command: String) {
        val session = activeSession ?: return
        val trimmed = command.trim()
        if (trimmed.equals("clear", ignoreCase = true) || trimmed.equals("cls", ignoreCase = true)) {
            session.clear()
            return
        }
        session.append("$ $command\n")
        OutputLogger.logTask("Terminal [${session.title}]: $command")
        processMap[session.id]?.write("$command\n")
    }

    override fun onCleared() {
        super.onCleared()
        processMap.values.forEach { it.destroy() }
        processMap.clear()
    }
}
