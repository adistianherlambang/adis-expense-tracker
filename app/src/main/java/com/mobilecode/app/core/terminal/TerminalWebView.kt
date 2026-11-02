package com.mobilecode.app.core.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilecode.app.core.designsystem.VsCodeMonospaceFont
import com.mobilecode.app.core.designsystem.VsCodeTheme
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun TerminalWebView(
    session: TerminalSession,
    onCommandSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val outputText by session.outputState.collectAsState()
    val scrollState = rememberScrollState()
    var commandInput by remember(session.id) { mutableStateOf("") }
    val commandHistory = remember(session.id) { mutableStateListOf<String>() }
    var historyIndex by remember(session.id) { mutableIntStateOf(-1) }

    // Auto-scroll to bottom when new output arrives
    LaunchedEffect(outputText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    val submitCurrentCommand = {
        val cmd = commandInput
        if (cmd.isNotBlank()) {
            commandHistory.add(cmd)
            historyIndex = commandHistory.size
        }
        onCommandSubmitted(cmd)
        commandInput = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VsCodeTheme.PanelBackground)
    ) {
        // Quick Shell Commands Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(Color(0xFF202021))
                .border(0.5.dp, VsCodeTheme.BorderColor)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Quick:",
                color = VsCodeTheme.SecondaryForeground,
                fontSize = 10.sp
            )
            val quickCommands = listOf("ls -la", "pwd", "whoami", "uname -a", "df -h", "ps", "env", "clear")
            quickCommands.forEach { qCmd ->
                Box(
                    modifier = Modifier
                        .background(VsCodeTheme.SidebarBackground, RoundedCornerShape(3.dp))
                        .border(0.5.dp, VsCodeTheme.BorderColor, RoundedCornerShape(3.dp))
                        .clickable { onCommandSubmitted(qCmd) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = qCmd,
                        color = VsCodeTheme.Foreground,
                        fontSize = 10.sp,
                        fontFamily = VsCodeMonospaceFont
                    )
                }
            }
        }

        // Scrollable Terminal Output Area
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = outputText.ifEmpty {
                        "Mobile Code Terminal (Android Shell)\nReady in ${session.workingDir.absolutePath}\n"
                    },
                    color = Color(0xFFD4D4D4),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = VsCodeMonospaceFont
                )
            }
        }

        // Interactive Shell Prompt Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(VsCodeTheme.SidebarBackground)
                .border(width = 0.5.dp, color = VsCodeTheme.BorderColor)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${session.workingDir.name} $",
                color = Color(0xFF4EC9B0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = VsCodeMonospaceFont
            )

            Spacer(modifier = Modifier.width(8.dp))

            BasicTextField(
                value = commandInput,
                onValueChange = { value ->
                    if (value.endsWith("\n")) {
                        commandInput = value.trimEnd('\n', '\r')
                        submitCurrentCommand()
                    } else {
                        commandInput = value
                    }
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = VsCodeMonospaceFont
                ),
                cursorBrush = SolidColor(VsCodeTheme.FocusBorder),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submitCurrentCommand() }),
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            submitCurrentCommand()
                            true
                        } else {
                            false
                        }
                    },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (commandInput.isEmpty()) {
                            Text(
                                text = "Enter shell command (e.g. ls, pwd, cat)...",
                                color = VsCodeTheme.SecondaryForeground,
                                fontSize = 11.sp,
                                fontFamily = VsCodeMonospaceFont
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // History Up
            IconButton(
                onClick = {
                    if (commandHistory.isNotEmpty() && historyIndex > 0) {
                        historyIndex--
                        commandInput = commandHistory[historyIndex]
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Previous Command",
                    tint = VsCodeTheme.SecondaryForeground,
                    modifier = Modifier.size(16.dp)
                )
            }

            // History Down
            IconButton(
                onClick = {
                    if (historyIndex < commandHistory.size - 1) {
                        historyIndex++
                        commandInput = commandHistory[historyIndex]
                    } else {
                        historyIndex = commandHistory.size
                        commandInput = ""
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Next Command",
                    tint = VsCodeTheme.SecondaryForeground,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Execute Command Button
            IconButton(
                onClick = { submitCurrentCommand() },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Execute Command",
                    tint = VsCodeTheme.BadgeBackground,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
