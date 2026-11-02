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

    private var gitClient = GitClient(workspaceManager.currentWorkspaceDir.value)

    private val _currentBranch = MutableStateFlow("main")
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
            gitClient = GitClient(workspaceManager.currentWorkspaceDir.value)
            _currentBranch.value = gitClient.getCurrentBranch()
            _changes.value = gitClient.getStatus()
            _isRefreshing.value = false
        }
    }

    fun stageFile(file: GitChangedFile) {
        viewModelScope.launch {
            gitClient.stageFile(file.path)
            refresh()
        }
    }

    fun unstageFile(file: GitChangedFile) {
        viewModelScope.launch {
            gitClient.unstageFile(file.path)
            refresh()
        }
    }

    fun commit() {
        val msg = _commitMessage.value.trim()
        if (msg.isEmpty()) return

        viewModelScope.launch {
            val success = gitClient.commit(msg)
            if (success) {
                _commitMessage.value = ""
                refresh()
            }
        }
    }
}

