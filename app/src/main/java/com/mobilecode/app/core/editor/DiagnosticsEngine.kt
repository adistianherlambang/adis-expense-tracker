package com.mobilecode.app.core.editor

import kotlinx.serialization.json.Json
import java.io.File

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

data class DiagnosticItem(
    val file: File,
    val line: Int,
    val column: Int,
    val message: String,
    val severity: DiagnosticSeverity,
    val source: String
)

object DiagnosticsEngine {

    fun analyze(file: File, content: String): List<DiagnosticItem> {
        val extension = file.extension.lowercase()
        return when (extension) {
            "json" -> analyzeJson(file, content)
            "js", "ts" -> analyzeJavaScript(file, content)
            "py" -> analyzePython(file, content)
            else -> analyzeGeneric(file, content)
        }
    }

    private fun analyzeJson(file: File, content: String): List<DiagnosticItem> {
        if (content.trim().isEmpty()) return emptyList()
        val items = mutableListOf<DiagnosticItem>()
        try {
            Json.parseToJsonElement(content)
        } catch (e: Exception) {
            val msg = e.message ?: "Invalid JSON syntax"
            var line = 1
            var col = 1

            // Extract line and column from kotlinx.serialization error message e.g. "at line 3, column 5"
            val regex = Regex("line (\\d+), column (\\d+)")
            val match = regex.find(msg)
            if (match != null) {
                line = match.groupValues[1].toIntOrNull() ?: 1
                col = match.groupValues[2].toIntOrNull() ?: 1
            }

            items.add(
                DiagnosticItem(
                    file = file,
                    line = line,
                    column = col,
                    message = msg.substringBefore("\n"),
                    severity = DiagnosticSeverity.ERROR,
                    source = "json-linter"
                )
            )
        }
        return items
    }

    private fun analyzeJavaScript(file: File, content: String): List<DiagnosticItem> {
        val items = mutableListOf<DiagnosticItem>()
        val lines = content.lines()

        val openBraces = mutableListOf<Pair<Int, Int>>() // line, col
        val openParens = mutableListOf<Pair<Int, Int>>()
        val openBrackets = mutableListOf<Pair<Int, Int>>()

        lines.forEachIndexed { lineIdx, line ->
            val lineNum = lineIdx + 1
            var inString: Char? = null

            for (colIdx in line.indices) {
                val c = line[colIdx]
                val colNum = colIdx + 1

                if (inString != null) {
                    if (c == inString && (colIdx == 0 || line[colIdx - 1] != '\\')) {
                        inString = null
                    }
                } else {
                    when (c) {
                        '"', '\'', '`' -> inString = c
                        '{' -> openBraces.add(Pair(lineNum, colNum))
                        '}' -> {
                            if (openBraces.isEmpty()) {
                                items.add(DiagnosticItem(file, lineNum, colNum, "Unexpected closing brace '}'", DiagnosticSeverity.ERROR, "js-linter"))
                            } else {
                                openBraces.removeAt(openBraces.size - 1)
                            }
                        }
                        '(' -> openParens.add(Pair(lineNum, colNum))
                        ')' -> {
                            if (openParens.isEmpty()) {
                                items.add(DiagnosticItem(file, lineNum, colNum, "Unexpected closing parenthesis ')'", DiagnosticSeverity.ERROR, "js-linter"))
                            } else {
                                openParens.removeAt(openParens.size - 1)
                            }
                        }
                        '[' -> openBrackets.add(Pair(lineNum, colNum))
                        ']' -> {
                            if (openBrackets.isEmpty()) {
                                items.add(DiagnosticItem(file, lineNum, colNum, "Unexpected closing bracket ']'", DiagnosticSeverity.ERROR, "js-linter"))
                            } else {
                                openBrackets.removeAt(openBrackets.size - 1)
                            }
                        }
                    }
                }
            }

            if (inString != null && inString != '`') {
                items.add(DiagnosticItem(file, lineNum, line.length, "Unclosed string literal", DiagnosticSeverity.ERROR, "js-linter"))
            }

            // Warning for console.log or debugger statements
            if (line.contains("debugger;")) {
                items.add(DiagnosticItem(file, lineNum, 1, "Unwanted 'debugger' statement", DiagnosticSeverity.WARNING, "js-linter"))
            }
        }

        openBraces.forEach { (l, c) ->
            items.add(DiagnosticItem(file, l, c, "Unclosed brace '{'", DiagnosticSeverity.ERROR, "js-linter"))
        }
        openParens.forEach { (l, c) ->
            items.add(DiagnosticItem(file, l, c, "Unclosed parenthesis '('", DiagnosticSeverity.ERROR, "js-linter"))
        }
        openBrackets.forEach { (l, c) ->
            items.add(DiagnosticItem(file, l, c, "Unclosed bracket '['", DiagnosticSeverity.ERROR, "js-linter"))
        }

        return items
    }

    private fun analyzePython(file: File, content: String): List<DiagnosticItem> {
        val items = mutableListOf<DiagnosticItem>()
        val lines = content.lines()

        lines.forEachIndexed { idx, line ->
            val lineNum = idx + 1
            val trimmed = line.trim()

            // Check missing colon after def, if, elif, else, for, while, class, try, except, finally
            val controlKeywords = listOf("def ", "if ", "elif ", "else:", "for ", "while ", "class ", "try:", "except", "finally:")
            for (kw in controlKeywords) {
                if (trimmed.startsWith(kw) && !trimmed.endsWith(":") && !trimmed.contains("#")) {
                    items.add(
                        DiagnosticItem(
                            file = file,
                            line = lineNum,
                            column = line.length,
                            message = "Missing colon ':' at the end of statement",
                            severity = DiagnosticSeverity.ERROR,
                            source = "py-linter"
                        )
                    )
                }
            }
        }
        return items
    }

    private fun analyzeGeneric(file: File, content: String): List<DiagnosticItem> {
        return emptyList()
    }
}

