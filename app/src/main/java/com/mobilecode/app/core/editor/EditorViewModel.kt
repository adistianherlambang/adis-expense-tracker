package com.mobilecode.app.core.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilecode.app.core.filesystem.WorkspaceManager
import com.mobilecode.app.core.terminal.OutputLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class EditorCommand {
    data class InsertText(val text: String) : EditorCommand()
    object DeleteChar : EditorCommand()
    object Undo : EditorCommand()
    object Redo : EditorCommand()
    data class GoToLine(val line: Int) : EditorCommand()
}

class EditorViewModel(
    private val workspaceManager: WorkspaceManager
) : ViewModel() {

    private val _tabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val tabs: StateFlow<List<EditorTab>> = _tabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow<Int>(-1)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    private val _cursor = MutableStateFlow(EditorCursor(1, 1))
    val cursor: StateFlow<EditorCursor> = _cursor.asStateFlow()

    private val _diagnostics = MutableStateFlow(EditorDiagnostics(0, 0))
    val diagnostics: StateFlow<EditorDiagnostics> = _diagnostics.asStateFlow()

    private val _problems = MutableStateFlow<List<DiagnosticItem>>(emptyList())
    val problems: StateFlow<List<DiagnosticItem>> = _problems.asStateFlow()

    private val _editorCommands = MutableSharedFlow<EditorCommand>(extraBufferCapacity = 32)
    val editorCommands: SharedFlow<EditorCommand> = _editorCommands.asSharedFlow()

    val activeTab: EditorTab?
        get() = _tabs.value.getOrNull(_activeTabIndex.value)

    fun openFile(file: File) {
        viewModelScope.launch {
            val existingIndex = _tabs.value.indexOfFirst { it.file.absolutePath == file.absolutePath }
            if (existingIndex != -1) {
                _activeTabIndex.value = existingIndex
                runDiagnostics(_tabs.value[existingIndex])
                return@launch
            }

            val content = workspaceManager.readFile(file)
            val extension = file.extension.lowercase()
            val language = detectLanguage(extension)

            val newTab = EditorTab(
                id = file.absolutePath,
                file = file,
                title = file.name,
                content = content,
                language = language,
                isDirty = false
            )

            val updatedTabs = _tabs.value + newTab
            _tabs.value = updatedTabs
            _activeTabIndex.value = updatedTabs.size - 1
            OutputLogger.logSystem("Opened file: ${file.name} (${content.length} chars)")
            runDiagnostics(newTab)
        }
    }

    fun createQuickFile(fileName: String, initialContent: String = "") {
        viewModelScope.launch {
            var dir = workspaceManager.currentWorkspaceDir.value
            if (dir == null) {
                dir = workspaceManager.createNewProject("my-project")
            }
            if (dir != null) {
                val cleanName = fileName.trim().ifEmpty { "main.js" }
                val targetFile = File(dir, cleanName)
                if (!targetFile.exists()) {
                    workspaceManager.writeFile(targetFile, initialContent)
                }
                workspaceManager.refreshTree()
                openFile(targetFile)
            }
        }
    }

    private fun detectLanguage(extension: String): String {
        return when (extension) {
            "js", "mjs", "cjs", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "py" -> "python"
            "json" -> "json"
            "html", "htm", "xml" -> "html"
            "css", "scss" -> "css"
            "md", "markdown" -> "markdown"
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "sh", "bash", "zsh" -> "shell"
            else -> "plaintext"
        }
    }

    private fun runDiagnostics(tab: EditorTab) {
        val items = DiagnosticsEngine.analyze(tab.file, tab.content)
        _problems.value = items
        val errors = items.count { it.severity == DiagnosticSeverity.ERROR }
        val warnings = items.count { it.severity == DiagnosticSeverity.WARNING }
        _diagnostics.value = EditorDiagnostics(errors, warnings)
    }

    fun selectTab(index: Int) {
        if (index in _tabs.value.indices) {
            _activeTabIndex.value = index
            runDiagnostics(_tabs.value[index])
        }
    }

    fun closeTab(index: Int) {
        val currentTabs = _tabs.value.toMutableList()
        if (index in currentTabs.indices) {
            val closed = currentTabs.removeAt(index)
            OutputLogger.logSystem("Closed tab: ${closed.title}")
            _tabs.value = currentTabs
            val currentActive = _activeTabIndex.value
            val newActive = when {
                currentTabs.isEmpty() -> -1
                currentActive >= currentTabs.size -> currentTabs.size - 1
                currentActive == index -> index.coerceAtMost(currentTabs.size - 1)
                else -> currentActive
            }
            _activeTabIndex.value = newActive
            if (newActive != -1) {
                runDiagnostics(currentTabs[newActive])
            } else {
                _problems.value = emptyList()
                _diagnostics.value = EditorDiagnostics(0, 0)
            }
        }
    }

    fun onContentChanged(fileId: String, newContent: String, isDirty: Boolean) {
        var changedTab: EditorTab? = null
        val currentTabs = _tabs.value.map { tab ->
            if (tab.id == fileId) {
                val updated = tab.copy(content = newContent, isDirty = isDirty)
                changedTab = updated
                updated
            } else {
                tab
            }
        }
        _tabs.value = currentTabs
        changedTab?.let { runDiagnostics(it) }
    }

    fun updateCursor(line: Int, column: Int) {
        _cursor.value = EditorCursor(line, column)
    }

    fun insertText(text: String) {
        _editorCommands.tryEmit(EditorCommand.InsertText(text))
    }

    fun deleteChar() {
        _editorCommands.tryEmit(EditorCommand.DeleteChar)
    }

    fun undo() {
        _editorCommands.tryEmit(EditorCommand.Undo)
    }

    fun redo() {
        _editorCommands.tryEmit(EditorCommand.Redo)
    }

    fun goToLine(line: Int) {
        _editorCommands.tryEmit(EditorCommand.GoToLine(line))
    }

    fun saveActiveFile() {
        val tab = activeTab ?: return
        viewModelScope.launch {
            val success = workspaceManager.writeFile(tab.file, tab.content)
            if (success) {
                val updatedTabs = _tabs.value.map {
                    if (it.id == tab.id) it.copy(isDirty = false) else it
                }
                _tabs.value = updatedTabs
                OutputLogger.logSystem("Saved file: ${tab.file.absolutePath}")
            }
        }
    }

    fun saveAll() {
        viewModelScope.launch {
            _tabs.value.filter { it.isDirty }.forEach { tab ->
                val success = workspaceManager.writeFile(tab.file, tab.content)
                if (success) {
                    val updated = _tabs.value.map {
                        if (it.id == tab.id) it.copy(isDirty = false) else it
                    }
                    _tabs.value = updated
                    OutputLogger.logSystem("Saved file: ${tab.file.name}")
                }
            }
        }
    }
}
