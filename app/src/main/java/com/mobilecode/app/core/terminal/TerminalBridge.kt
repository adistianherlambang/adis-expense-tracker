package com.mobilecode.app.core.terminal

import android.webkit.JavascriptInterface

class TerminalBridge(
    private val onInputCallback: (sessionId: String, data: String) -> Unit,
    private val onTerminalReadyCallback: () -> Unit
) {
    @JavascriptInterface
    fun onTerminalInput(sessionId: String, data: String) {
        onInputCallback(sessionId, data)
    }

    @JavascriptInterface
    fun onTerminalReady() {
        onTerminalReadyCallback()
    }
}

