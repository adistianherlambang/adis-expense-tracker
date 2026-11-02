package com.mobilecode.app.core.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OutputLogger {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _systemLogs = MutableStateFlow<List<String>>(
        listOf("[${timeFormat.format(Date())}] Mobile Code IDE engine initialized.")
    )
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()

    private val _gitLogs = MutableStateFlow<List<String>>(
        listOf("[${timeFormat.format(Date())}] Git version control service ready.")
    )
    val gitLogs: StateFlow<List<String>> = _gitLogs.asStateFlow()

    private val _tasksLogs = MutableStateFlow<List<String>>(
        listOf("[${timeFormat.format(Date())}] Build tasks service ready.")
    )
    val tasksLogs: StateFlow<List<String>> = _tasksLogs.asStateFlow()

    fun logSystem(message: String) {
        val entry = "[${timeFormat.format(Date())}] $message"
        _systemLogs.value = (_systemLogs.value + entry).takeLast(500)
    }

    fun logGit(message: String) {
        val entry = "[${timeFormat.format(Date())}] $message"
        _gitLogs.value = (_gitLogs.value + entry).takeLast(500)
    }

    fun logTask(message: String) {
        val entry = "[${timeFormat.format(Date())}] $message"
        _tasksLogs.value = (_tasksLogs.value + entry).takeLast(500)
    }

    fun clear(channel: String) {
        when (channel) {
            "System" -> _systemLogs.value = emptyList()
            "Git" -> _gitLogs.value = emptyList()
            "Tasks" -> _tasksLogs.value = emptyList()
        }
    }
}
