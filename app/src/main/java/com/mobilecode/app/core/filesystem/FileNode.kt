package com.mobilecode.app.core.filesystem

import java.io.File

data class FileNode(
    val file: File,
    val name: String = file.name,
    val isDirectory: Boolean = file.isDirectory,
    val isExpanded: Boolean = false,
    val children: List<FileNode> = emptyList(),
    val level: Int = 0
) {
    val extension: String
        get() = if (isDirectory) "" else file.extension.lowercase()

    val language: String
        get() = when (extension) {
            "js", "mjs", "cjs" -> "javascript"
            "ts" -> "typescript"
            "py" -> "python"
            "json" -> "json"
            "html", "htm" -> "html"
            "css" -> "css"
            "md", "markdown" -> "markdown"
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "c", "h" -> "c"
            "cpp", "hpp", "cc" -> "cpp"
            "sh", "bash" -> "shell"
            "xml" -> "xml"
            "yml", "yaml" -> "yaml"
            "sql" -> "sql"
            else -> "plaintext"
        }
}

