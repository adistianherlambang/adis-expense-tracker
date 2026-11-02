package com.mobilecode.app.core.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val verticalScrollState = rememberScrollState()

    val promptPrefix = when (replMode) {
        "NODE" -> "node > "
        "PYTHON" -> "python >>> "
        else -> "${workingDir.name} $ "
    }

    val prefixText = remember(outputText, promptPrefix) {
        outputText + promptPrefix
    }

    var textFieldValue by remember(session.id) {
        val full = prefixText + commandInput
        mutableStateOf(
            TextFieldValue(
                text = full,
                selection = TextRange(full.length)
            )
        )
    }

    // Keep textFieldValue synchronized when outputText, promptPrefix, or ViewModel commandInput updates
    LaunchedEffect(prefixText, commandInput) {
        val expected = prefixText + commandInput
        if (textFieldValue.text != expected) {
            textFieldValue = TextFieldValue(
                text = expected,
                selection = TextRange(expected.length)
            )
        }
        verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
    }

    val terminalVisualTransformation = remember(prefixText.length) {
        VisualTransformation { annotated ->
            TransformedText(
                text = styleTerminalText(annotated.text, prefixText.length),
                offsetMapping = OffsetMapping.Identity
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VsCodeTheme.PanelBackground)
    ) {
        // Top Action & Quick Commands Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(Color(0xFF252526))
                .border(0.5.dp, VsCodeTheme.BorderColor)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
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
                            .background(Color(0xFF333333), RoundedCornerShape(3.dp))
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

            Spacer(modifier = Modifier.width(6.dp))

            // History Up/Down & Run Command Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = { terminalViewModel.navigateHistoryUp() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous Command",
                        tint = VsCodeTheme.Foreground,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { terminalViewModel.navigateHistoryDown() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next Command",
                        tint = VsCodeTheme.Foreground,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .background(VsCodeTheme.BadgeBackground, RoundedCornerShape(3.dp))
                        .clickable {
                            terminalViewModel.submitCurrentCommand()
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Enter",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Main Editable Terminal Surface (Exact same architecture as MonacoWebView!)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fillMaxHeight()
                .background(VsCodeTheme.EditorBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onTerminalFocused?.invoke()
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    onTerminalFocused?.invoke()

                    // 1. If only cursor/selection moved (e.g. user tapped anywhere on terminal text),
                    // clamp cursor to the active command prompt at the bottom!
                    if (newValue.text == textFieldValue.text) {
                        val clampedStart = newValue.selection.start.coerceAtLeast(prefixText.length)
                        val clampedEnd = newValue.selection.end.coerceAtLeast(prefixText.length)
                        textFieldValue = newValue.copy(selection = TextRange(clampedStart, clampedEnd))
                        return@BasicTextField
                    }

                    // 2. If user typed at the prompt (text starts with prefixText)
                    if (newValue.text.startsWith(prefixText)) {
                        val newCmd = newValue.text.substring(prefixText.length)
                        if (newCmd.contains("\n")) {
                            val cleanCmd = newCmd.replace("\n", "").replace("\r", "")
                            terminalViewModel.updateCommandInput(cleanCmd)
                            terminalViewModel.submitCurrentCommand()
                        } else {
                            val clampedStart = newValue.selection.start.coerceIn(prefixText.length, newValue.text.length)
                            val clampedEnd = newValue.selection.end.coerceIn(prefixText.length, newValue.text.length)
                            textFieldValue = newValue.copy(selection = TextRange(clampedStart, clampedEnd))
                            terminalViewModel.updateCommandInput(newCmd)
                        }
                    } else if (newValue.text.length < textFieldValue.text.length) {
                        // User pressed backspace when commandInput was empty or inside prefixText
                        val rebuilt = prefixText + commandInput
                        textFieldValue = TextFieldValue(
                            text = rebuilt,
                            selection = TextRange(rebuilt.length)
                        )
                    } else {
                        // User typed while cursor was accidentally inside history text:
                        // Extract newly typed character(s) and append to commandInput
                        val diffLen = newValue.text.length - textFieldValue.text.length
                        val insertPos = (newValue.selection.start - diffLen).coerceIn(0, newValue.text.length)
                        val inserted = newValue.text.substring(insertPos, (insertPos + diffLen).coerceAtMost(newValue.text.length))
                        if (inserted.contains("\n")) {
                            val clean = (commandInput + inserted).replace("\n", "").replace("\r", "")
                            terminalViewModel.updateCommandInput(clean)
                            terminalViewModel.submitCurrentCommand()
                        } else {
                            val nextCmd = commandInput + inserted
                            val rebuilt = prefixText + nextCmd
                            textFieldValue = TextFieldValue(
                                text = rebuilt,
                                selection = TextRange(rebuilt.length)
                            )
                            terminalViewModel.updateCommandInput(nextCmd)
                        }
                    }
                },
                textStyle = TextStyle(
                    color = Color(0xFFD4D4D4),
                    fontFamily = VsCodeMonospaceFont,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(VsCodeTheme.FocusBorder),
                visualTransformation = terminalVisualTransformation,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            onTerminalFocused?.invoke()
                        }
                    }
                    .verticalScroll(verticalScrollState)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        innerTextField()
                    }
                }
            )
        }
    }
}

private val promptLineRegex = Regex("(?m)^([^\\n]*\\$\\s|node >\\s|python >>>\\s)")

private fun styleTerminalText(text: String, prefixLength: Int): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
        append(text)
        // Highlight shell prompts in VS Code Teal (#4EC9B0)
        promptLineRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = Color(0xFF4EC9B0), fontWeight = FontWeight.Bold),
                match.range.first,
                match.range.last + 1
            )
        }
        // Highlight currently typed command in bright white
        if (prefixLength in 0..text.length) {
            addStyle(
                SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold),
                prefixLength,
                text.length
            )
        }
    }
}
