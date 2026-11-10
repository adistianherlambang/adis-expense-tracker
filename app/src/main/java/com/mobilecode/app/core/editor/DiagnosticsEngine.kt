package com.mobilecode.app.core.editor

import kotlinx.serialization.json.Json
import java.io.File

private data class OpenBracket(val char: Char, val line: Int, val col: Int)

object DiagnosticsEngine {

    fun analyze(file: File, content: String): List<DiagnosticItem> {
        val extension = file.extension.lowercase()
        return when (extension) {
            "json", "jsonc" -> analyzeJson(file, content)
            "js", "ts", "mjs", "cjs", "jsx", "tsx", "mts", "cts",
            "java", "kt", "kts", "c", "h", "cpp", "cc", "cxx", "hpp", "hxx",
            "cs", "csx", "go", "rs", "php", "dart", "swift", "css", "scss" -> analyzeBraceLanguage(file, content)
            "py", "pyw" -> analyzePython(file, content)
            else -> analyzeGeneric(file, content)
        }
    }

    private fun analyzeBraceLanguage(file: File, content: String): List<DiagnosticItem> {
        val items = mutableListOf<DiagnosticItem>()
        val lines = content.lines()
        val extension = file.extension.lowercase()
        val source = "$extension-linter"

        val bracketStack = mutableListOf<OpenBracket>()
        var inBlockComment = false
        var blockCommentStartLine = 1
        var blockCommentStartCol = 1

        var inTripleDouble = false
        var tripleDoubleStartLine = 1
        var tripleDoubleStartCol = 1

        var inTemplateString = false
        var templateStringStartLine = 1
        var templateStringStartCol = 1

        for (lineIdx in lines.indices) {
            val lineNum = lineIdx + 1
            val line = lines[lineIdx]
            var i = 0

            while (i < line.length) {
                if (inBlockComment) {
                    if (line.startsWith("*/", i)) {
                        inBlockComment = false
                        i += 2
                    } else {
                        i++
                    }
                    continue
                }

                if (inTripleDouble) {
                    if (line.startsWith("\"\"\"", i)) {
                        inTripleDouble = false
                        i += 3
                    } else {
                        i++
                    }
                    continue
                }

                if (inTemplateString) {
                    if (line[i] == '\\') {
                        i = (i + 2).coerceAtMost(line.length)
                    } else if (line[i] == '`') {
                        inTemplateString = false
                        i++
                    } else {
                        i++
                    }
                    continue
                }

                val c = line[i]
                val colNum = i + 1

                // Block comment start: /*
                if (line.startsWith("/*", i)) {
                    inBlockComment = true
                    blockCommentStartLine = lineNum
                    blockCommentStartCol = colNum
                    i += 2
                    continue
                }

                // Line comment start: //
                if (line.startsWith("//", i)) {
                    break
                }

                // PHP script comment: #
                if (extension == "php" && c == '#') {
                    break
                }

                // Multiline raw string start: """
                if (line.startsWith("\"\"\"", i)) {
                    inTripleDouble = true
                    tripleDoubleStartLine = lineNum
                    tripleDoubleStartCol = colNum
                    i += 3
                    continue
                }

                // Template literal start: `
                if (c == '`') {
                    inTemplateString = true
                    templateStringStartLine = lineNum
                    templateStringStartCol = colNum
                    i++
                    continue
                }

                // Double quoted string: "
                if (c == '"') {
                    val startCol = colNum
                    i++
                    var closed = false
                    while (i < line.length) {
                        if (line[i] == '\\') {
                            i = (i + 2).coerceAtMost(line.length)
                        } else if (line[i] == '"') {
                            closed = true
                            i++
                            break
                        } else {
                            i++
                        }
                    }
                    if (!closed) {
                        items.add(DiagnosticItem(file, lineNum, startCol, "Unclosed string literal", DiagnosticSeverity.ERROR, source))
                    }
                    continue
                }

                // Single quoted char or string: '
                if (c == '\'') {
                    val startCol = colNum

                    // Special case for Rust lifetimes (e.g. &'a str, <'a>)
                    if (extension == "rs" && i + 1 < line.length && (line[i + 1].isLetter() || line[i + 1] == '_')) {
                        val isCharLit = (i + 2 < line.length && line[i + 2] == '\'') ||
                                (line[i + 1] == '\\' && i + 3 < line.length && line[i + 3] == '\'')
                        if (!isCharLit) {
                            i++
                            while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) {
                                i++
                            }
                            continue
                        }
                    }

                    i++
                    var closed = false
                    while (i < line.length) {
                        if (line[i] == '\\') {
                            i = (i + 2).coerceAtMost(line.length)
                        } else if (line[i] == '\'') {
                            closed = true
                            i++
                            break
                        } else {
                            i++
                        }
                    }
                    if (!closed) {
                        items.add(DiagnosticItem(file, lineNum, startCol, "Unclosed string literal", DiagnosticSeverity.ERROR, source))
                    }
                    continue
                }

                // Open brackets: {, (, [
                if (c == '{' || c == '(' || c == '[') {
                    bracketStack.add(OpenBracket(c, lineNum, colNum))
                    i++
                    continue
                }

                // Closing brackets: }, ), ]
                if (c == '}' || c == ')' || c == ']') {
                    if (bracketStack.isEmpty()) {
                        items.add(DiagnosticItem(file, lineNum, colNum, "Unexpected closing bracket '$c'", DiagnosticSeverity.ERROR, source))
                    } else {
                        val top = bracketStack.last()
                        if (isMatching(top.char, c)) {
                            bracketStack.removeAt(bracketStack.size - 1)
                        } else {
                            val matchIdx = bracketStack.indexOfLast { isMatching(it.char, c) }
                            if (matchIdx != -1) {
                                for (k in bracketStack.size - 1 downTo matchIdx + 1) {
                                    val unclosed = bracketStack[k]
                                    items.add(DiagnosticItem(file, unclosed.line, unclosed.col, "Unclosed bracket '${unclosed.char}'", DiagnosticSeverity.ERROR, source))
                                }
                                while (bracketStack.size > matchIdx) {
                                    bracketStack.removeAt(bracketStack.size - 1)
                                }
                            } else {
                                items.add(DiagnosticItem(file, lineNum, colNum, "Unexpected closing bracket '$c'", DiagnosticSeverity.ERROR, source))
                            }
                        }
                    }
                    i++
                    continue
                }

                i++
            }
        }

        if (inBlockComment) {
            items.add(DiagnosticItem(file, blockCommentStartLine, blockCommentStartCol, "Unclosed block comment '/*'", DiagnosticSeverity.ERROR, source))
        }
        if (inTripleDouble) {
            items.add(DiagnosticItem(file, tripleDoubleStartLine, tripleDoubleStartCol, "Unclosed multiline string literal", DiagnosticSeverity.ERROR, source))
        }
        if (inTemplateString) {
            items.add(DiagnosticItem(file, templateStringStartLine, templateStringStartCol, "Unclosed template string literal", DiagnosticSeverity.ERROR, source))
        }

        bracketStack.forEach { b ->
            items.add(DiagnosticItem(file, b.line, b.col, "Unclosed bracket '${b.char}'", DiagnosticSeverity.ERROR, source))
        }

        return items
    }

    private fun isMatching(open: Char, close: Char): Boolean {
        return (open == '{' && close == '}') ||
                (open == '(' && close == ')') ||
                (open == '[' && close == ']')
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

