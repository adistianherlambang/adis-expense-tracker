package com.mobilecode.app.core.terminal

import java.io.File

data class TerminalSession(
    val id: String,
    val title: String,
    val workingDir: File,
    val isAlive: Boolean = true
) {
    private val buffer = StringBuilder()

    @Synchronized
    fun append(text: String) {
        buffer.append(text)
        if (buffer.length > 50000) {
            buffer.delete(0, 10000)
        }
    }

    @Synchronized
    fun getHistory(): String = buffer.toString()

    @Synchronized
    fun clear() {
        buffer.setLength(0)
    }
}
