package com.mobilecode.app.core.terminal

import java.io.File

data class TerminalSession(
    val id: String,
    val title: String,
    val workingDir: File,
    val isAlive: Boolean = true
)

