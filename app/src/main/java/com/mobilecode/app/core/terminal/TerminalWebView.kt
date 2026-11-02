package com.mobilecode.app.core.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilecode.app.core.designsystem.VsCodeMonospaceFont
import com.mobilecode.app.core.designsystem.VsCodeTheme

@Composable
fun TerminalWebView(
    session: TerminalSession,
    terminalViewModel: TerminalViewModel,
    onTerminalFocused: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val outputText by session.outputState.collectAsState()
    val workingDir by session.workingDirState.collectAsState()
    val replMode by session.replModeState.collectAsState()
    val commandInput by terminalViewModel.commandInput.collectAsState()

    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    var isInputFocused by remember { mutableStateOf(false) }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = commandInput, selection = TextRange(commandInput.length)))
    }

    // Keep TextFieldValue synced with ViewModel state (e.g. when Accessory Bar or History updates it)
    LaunchedEffect(commandInput) {
        if (textFieldValue.text != commandInput) {
            textFieldValue = TextFieldValue(
                text = commandInput,
                selection = TextRange(commandInput.length)
            )
        }
    }

    // Auto-scroll to bottom when output or input changes
    LaunchedEffect(outputText, textFieldValue.text) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    val promptPrefix = when (replMode) {
        "NODE" -> "node >"
        "PYTHON" -> "python >>>"
        else -> "${workingDir.name} $"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VsCodeTheme.PanelBackground)
    ) {
        // Quick Commands Strip (1-tap execution for common Node, Python, Git, and Linux commands)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(Color(0xFF202021))
                .border(0.5.dp, VsCodeTheme.BorderColor)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickCommands = listOf(
                "node -v",
                "python --version",
                "npm init -y",
                "pip list",
                "ls -la",
                "pwd",
                "git status",
                "help",
                "clear"
            )
            quickCommands.forEach { qCmd ->
                Box(
                    modifier = Modifier
                        .background(VsCodeTheme.SidebarBackground, RoundedCornerShape(3.dp))
                        .border(0.5.dp, VsCodeTheme.BorderColor, RoundedCornerShape(3.dp))
                        .clickable {
                            terminalViewModel.sendCommand(qCmd)
                        }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
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

        // Full-Surface BasicTextField (identical touch & IME mechanism to MonacoWebView)
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newVal ->
                onTerminalFocused?.invoke()
                if (newVal.text.contains("\n")) {
                    val clean = newVal.text.replace("\n", "").replace("\r", "")
                    textFieldValue = TextFieldValue("", TextRange(0))
                    terminalViewModel.updateCommandInput(clean)
                    terminalViewModel.submitCurrentCommand()
                } else {
                    textFieldValue = newVal
                    terminalViewModel.updateCommandInput(newVal.text)
                }
            },
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = VsCodeMonospaceFont
            ),
            cursorBrush = SolidColor(VsCodeTheme.FocusBorder),
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    isInputFocused = state.isFocused
                    if (state.isFocused) {
                        onTerminalFocused?.invoke()
                    }
                },
            decorationBox = { innerTextField ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                ) {
                    // 1. Top Command Input Bar (Always visible above keyboard)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(Color(0xFF252526))
                            .border(
                                width = 1.dp,
                                color = if (isInputFocused) VsCodeTheme.FocusBorder else VsCodeTheme.BorderColor
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$promptPrefix ",
                            color = Color(0xFF4EC9B0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = VsCodeMonospaceFont
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (textFieldValue.text.isEmpty()) {
                                Text(
                                    text = if (replMode == "SH") {
                                        "Ketik perintah di sini (node, python, npm, ls, git)..."
                                    } else {
                                        "Ketik ekspresi REPL atau .exit untuk keluar..."
                                    },
                                    color = VsCodeTheme.SecondaryForeground,
                                    fontSize = 11.sp,
                                    fontFamily = VsCodeMonospaceFont,
                                    maxLines = 1
                                )
                            }
                            innerTextField()
                        }

                        // History Up
                        IconButton(
                            onClick = { terminalViewModel.navigateHistoryUp() },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Previous Command",
                                tint = VsCodeTheme.Foreground,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // History Down
                        IconButton(
                            onClick = { terminalViewModel.navigateHistoryDown() },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Next Command",
                                tint = VsCodeTheme.Foreground,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Run / Enter Button
                        Row(
                            modifier = Modifier
                                .background(VsCodeTheme.BadgeBackground, RoundedCornerShape(3.dp))
                                .clickable {
                                    terminalViewModel.submitCurrentCommand()
                                    textFieldValue = TextFieldValue("", TextRange(0))
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Run",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 2. Scrollable Terminal Output Area + Live Prompt Mirror
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                append(outputText)
                                withStyle(SpanStyle(color = Color(0xFF4EC9B0), fontWeight = FontWeight.Bold)) {
                                    append("$promptPrefix ")
                                }
                                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) {
                                    append(textFieldValue.text)
                                }
                                withStyle(SpanStyle(color = VsCodeTheme.FocusBorder, fontWeight = FontWeight.Bold)) {
                                    append("█")
                                }
                            },
                            color = Color(0xFFD4D4D4),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = VsCodeMonospaceFont
                        )
                    }
                }
            }
        )
    }
}
