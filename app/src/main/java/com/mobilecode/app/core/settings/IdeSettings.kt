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
    val tabSize: Int = 2,
    val insertSpaces: Boolean = true,
    val formatOnSave: Boolean = false,
    val autoClosingBrackets: String = "always"
)

@Serializable
data class TerminalSettings(
    val fontFamily: String = "Consolas",
    val fontSize: Int = 13,
    val cursorStyle: String = "block",
    val cursorBlinking: Boolean = true,
    val scrollback: Int = 10000
)

@Serializable
data class IdeSettings(
    val editor: EditorSettings = EditorSettings(),
    val terminal: TerminalSettings = TerminalSettings()
)

