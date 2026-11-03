package com.mobilecode.app.core.settings

import kotlinx.serialization.Serializable

@Serializable
data class EditorSettings(
    val fontFamily: String = "Consolas",
    val fontSize: Int = 14,
    val fontWeight: String = "400",
    val fontLigatures: Boolean = false,
    val lineHeight: Int = 22,
    val wordWrap: String = "off",
    val minimapEnabled: Boolean = true,
    val tabSize: Int = 4,
    val insertSpaces: Boolean = true,
    val formatOnSave: Boolean = true,
    val autoIndent: Boolean = true,
    val autoClosingBrackets: String = "always",
    val autoClosingQuotes: String = "always",
    val matchBrackets: String = "always"
)

@Serializable
data class TerminalSettings(
    val fontFamily: String = "Consolas",
    val fontSize: Int = 13,
    val cursorStyle: String = "block",
    val cursorBlinking: Boolean = true,
    val scrollback: Int = 10000,
    val clipboardEnabled: Boolean = true,
    val sandboxIsolation: Boolean = true
)

@Serializable
data class WorkspaceSettings(
    val restoreLastWorkspaceOnStartup: Boolean = true,
    val sandboxEnforced: Boolean = true,
    val newWindowKeybinding: String = "Ctrl+N / Cmd+N"
)

@Serializable
data class IdeSettings(
    val editor: EditorSettings = EditorSettings(),
    val terminal: TerminalSettings = TerminalSettings(),
    val workspace: WorkspaceSettings = WorkspaceSettings()
)
