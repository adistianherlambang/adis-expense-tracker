package com.mobilecode.app.core.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilecode.app.core.filesystem.WorkspaceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val workspaceManager: WorkspaceManager
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _replaceText = MutableStateFlow("")
    val replaceText: StateFlow<String> = _replaceText.asStateFlow()

    private val _matchCase = MutableStateFlow(false)
    val matchCase: StateFlow<Boolean> = _matchCase.asStateFlow()

    private val _matchWholeWord = MutableStateFlow(false)
    val matchWholeWord: StateFlow<Boolean> = _matchWholeWord.asStateFlow()

    private val _useRegex = MutableStateFlow(false)
    val useRegex: StateFlow<Boolean> = _useRegex.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    fun setQuery(q: String) {
        _query.value = q
        triggerSearch()
    }

    fun setReplaceText(r: String) {
        _replaceText.value = r
    }

    fun toggleMatchCase() {
        _matchCase.value = !_matchCase.value
        triggerSearch()
    }

    fun toggleMatchWholeWord() {
        _matchWholeWord.value = !_matchWholeWord.value
        triggerSearch()
    }

    fun toggleUseRegex() {
        _useRegex.value = !_useRegex.value
        triggerSearch()
    }

    private fun triggerSearch() {
        searchJob?.cancel()
        val q = _query.value.trim()
        if (q.isEmpty()) {
            _results.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            val engine = WorkspaceSearchEngine(workspaceManager.currentWorkspaceDir.value)
            _results.value = engine.search(
                query = q,
                isRegex = _useRegex.value,
                matchCase = _matchCase.value,
                matchWholeWord = _matchWholeWord.value
            )
            _isSearching.value = false
        }
    }
}

