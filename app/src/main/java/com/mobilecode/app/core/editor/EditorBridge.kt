package com.mobilecode.app.core.editor

import android.webkit.JavascriptInterface

class EditorBridge(
    private val onContentChangedCallback: (fileId: String, content: String, isDirty: Boolean) -> Unit,
    private val onCursorPositionCallback: (line: Int, column: Int) -> Unit,
    private val onEditorReadyCallback: () -> Unit
) {
    @JavascriptInterface
    fun onContentChanged(fileId: String, content: String, isDirty: Boolean) {
        onContentChangedCallback(fileId, content, isDirty)
    }

    @JavascriptInterface
    fun onCursorPosition(line: Int, column: Int) {
        onCursorPositionCallback(line, column)
    }

    @JavascriptInterface
    fun onEditorReady() {
        onEditorReadyCallback()
    }
}

