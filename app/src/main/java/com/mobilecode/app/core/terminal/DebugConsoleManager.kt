package com.mobilecode.app.core.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DebugConsoleEntry(
    val prompt: String,
    val result: String,
    val isError: Boolean = false,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
)

object DebugConsoleManager {

    private val _entries = MutableStateFlow<List<DebugConsoleEntry>>(
        listOf(
            DebugConsoleEntry(
                prompt = "",
                result = "Debug Console ready. Type expressions (e.g. '2 + 2', 'Date()') or commands to evaluate.",
                isError = false
            )
        )
    )
    val entries: StateFlow<List<DebugConsoleEntry>> = _entries.asStateFlow()

    fun evaluate(expression: String, onJsEvaluate: ((String, (String) -> Unit) -> Unit)? = null) {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return

        when (trimmed.lowercase()) {
            "clear", "cls" -> {
                _entries.value = emptyList()
                return
            }
            "help" -> {
                addEntry(
                    prompt = trimmed,
                    result = "Available commands: clear, help, env, date, or any JavaScript / mathematical expression.",
                    isError = false
                )
                return
            }
            "date" -> {
                addEntry(prompt = trimmed, result = Date().toString(), isError = false)
                return
            }
            "env" -> {
                addEntry(prompt = trimmed, result = "OS: Android, Model: Mobile Code REPL", isError = false)
                return
            }
        }

        // Evaluate simple mathematical expressions or delegate to JS evaluator
        val mathResult = tryEvaluateMath(trimmed)
        if (mathResult != null) {
            addEntry(prompt = trimmed, result = mathResult, isError = false)
        } else if (onJsEvaluate != null) {
            onJsEvaluate(trimmed) { jsResult ->
                addEntry(prompt = trimmed, result = jsResult, isError = false)
            }
        } else {
            addEntry(prompt = trimmed, result = "[eval] => $trimmed", isError = false)
        }
    }

    fun addEntry(prompt: String, result: String, isError: Boolean = false) {
        val newEntry = DebugConsoleEntry(prompt, result, isError)
        _entries.value = (_entries.value + newEntry).takeLast(200)
    }

    private fun tryEvaluateMath(expr: String): String? {
        return try {
            val sanitized = expr.replace(" ", "")
            if (sanitized.matches(Regex("^[0-9+*\\-/.()^%]+$"))) {
                // Basic calculator evaluator for arithmetic
                val res = object : Any() {
                    var pos = -1
                    var ch = 0

                    fun nextChar() {
                        ch = if (++pos < sanitized.length) sanitized[pos].code else -1
                    }

                    fun eat(charToEat: Int): Boolean {
                        while (ch == ' '.code) nextChar()
                        if (ch == charToEat) {
                            nextChar()
                            return true
                        }
                        return false
                    }

                    fun parse(): Double {
                        nextChar()
                        val x = parseExpression()
                        if (pos < sanitized.length) throw RuntimeException("Unexpected: " + ch.toChar())
                        return x
                    }

                    fun parseExpression(): Double {
                        var x = parseTerm()
                        while (true) {
                            if (eat('+'.code)) x += parseTerm()
                            else if (eat('-'.code)) x -= parseTerm()
                            else return x
                        }
                    }

                    fun parseTerm(): Double {
                        var x = parseFactor()
                        while (true) {
                            if (eat('*'.code)) x *= parseFactor()
                            else if (eat('/'.code)) x /= parseFactor()
                            else if (eat('%'.code)) x %= parseFactor()
                            else return x
                        }
                    }

                    fun parseFactor(): Double {
                        if (eat('+'.code)) return parseFactor()
                        if (eat('-'.code)) return -parseFactor()
                        var x: Double
                        val startPos = pos
                        if (eat('('.code)) {
                            x = parseExpression()
                            eat(')'.code)
                        } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                            while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                            x = sanitized.substring(startPos, pos).toDouble()
                        } else {
                            throw RuntimeException("Unknown token")
                        }
                        return x
                    }
                }.parse()

                if (res % 1.0 == 0.0) res.toLong().toString() else res.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

