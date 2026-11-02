package com.mobilecode.app.core.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilecode.app.core.filesystem.WorkspaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GitViewModel(
    private val workspaceManager: WorkspaceManager
) : ViewModel() {

    private var gitClient: GitClient? = null

    private val _currentBranch = MutableStateFlow("-")
    val currentBranch: StateFlow<String> = _currentBranch.asStateFlow()

    private val _changes = MutableStateFlow<List<GitChangedFile>>(emptyList())
    val changes: StateFlow<List<GitChangedFile>> = _changes.asStateFlow()

    private val _commitMessage = MutableStateFlow("")
    val commitMessage: StateFlow<String> = _commitMessage.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refresh()
    }

    fun setCommitMessage(msg: String) {
        _commitMessage.value = msg
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val dir = workspaceManager.currentWorkspaceDir.value
            if (dir != null && dir.exists()) {
                val client = GitClient(dir)
                gitClient = client
                _currentBranch.value = client.getCurrentBranch()
                _changes.value = client.getStatus()
            } else {
                gitClient = null
                _currentBranch.value = "-"
                _changes.value = emptyList()
            }
            _isRefreshing.value = false
        }
    }

    fun stageFile(file: GitChangedFile) {
        viewModelScope.launch {
            gitClient?.stageFile(file.path)
            refresh()
        }
    }

    fun unstageFile(file: GitChangedFile) {
        viewModelScope.launch {
            gitClient?.unstageFile(file.path)
            refresh()
        }
    }

    fun commit() {
        val msg = _commitMessage.value.trim()
        if (msg.isEmpty()) return

        viewModelScope.launch {
            val success = gitClient?.commit(msg) ?: false
            if (success) {
                _commitMessage.value = ""
                refresh()
            }
        }
    }
}

