package com.mobilecode.app.core.editor

import java.io.File

data class EditorTab(
    val id: String,
    val file: File,
    val title: String = file.name,
    val content: String = "",
    val language: String = "plaintext",
    val isDirty: Boolean = false,
    val isReadOnly: Boolean = false,
    val line: Int = 1,
    val column: Int = 1
)

data class EditorCursor(
    val line: Int = 1,
    val column: Int = 1
)

data class EditorDiagnostics(
    val errorCount: Int = 0,
    val warningCount: Int = 0
)

