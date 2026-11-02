package com.mobilecode.app.core.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilecode.app.core.filesystem.WorkspaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

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

    val activeTab: EditorTab?
        get() = _tabs.value.getOrNull(_activeTabIndex.value)

    fun openFile(file: File) {
        viewModelScope.launch {
            val existingIndex = _tabs.value.indexOfFirst { it.file.absolutePath == file.absolutePath }
            if (existingIndex != -1) {
                _activeTabIndex.value = existingIndex
                return@launch
            }

            val content = workspaceManager.readFile(file)
            val extension = file.extension.lowercase()
            val language = when (extension) {
                "js", "mjs", "cjs" -> "javascript"
                "ts" -> "typescript"
                "py" -> "python"
                "json" -> "json"
                "html" -> "html"
                "css" -> "css"
                "md" -> "markdown"
                "kt", "kts" -> "kotlin"
                "java" -> "java"
                "sh", "bash" -> "shell"
                else -> "plaintext"
            }

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
        }
    }

    fun selectTab(index: Int) {
        if (index in _tabs.value.indices) {
            _activeTabIndex.value = index
        }
    }

    fun closeTab(index: Int) {
        val currentTabs = _tabs.value.toMutableList()
        if (index in currentTabs.indices) {
            currentTabs.removeAt(index)
            _tabs.value = currentTabs
            val currentActive = _activeTabIndex.value
            _activeTabIndex.value = when {
                currentTabs.isEmpty() -> -1
                currentActive >= currentTabs.size -> currentTabs.size - 1
                currentActive == index -> index.coerceAtMost(currentTabs.size - 1)
                else -> currentActive
            }
        }
    }

    fun onContentChanged(fileId: String, newContent: String, isDirty: Boolean) {
        val currentTabs = _tabs.value.map { tab ->
            if (tab.id == fileId) {
                tab.copy(content = newContent, isDirty = isDirty)
            } else {
                tab
            }
        }
        _tabs.value = currentTabs
    }

    fun updateCursor(line: Int, column: Int) {
        _cursor.value = EditorCursor(line, column)
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
                }
            }
        }
    }
}

