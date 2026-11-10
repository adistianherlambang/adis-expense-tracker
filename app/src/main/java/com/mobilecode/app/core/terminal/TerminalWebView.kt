package com.mobilecode.app.core.terminal

import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    val isRunning by session.isProcessRunning.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val canvasFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val verticalScrollState = rememberScrollState()

    val promptPrefix = when {
        isRunning -> ""
        replMode == "NODE" -> "node > "
        replMode == "PYTHON" -> "python >>> "
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

    fun copyFromTerminal() {
        val selMin = textFieldValue.selection.min.coerceIn(0, textFieldValue.text.length)
        val selMax = textFieldValue.selection.max.coerceIn(0, textFieldValue.text.length)
        val textToCopy = if (selMin != selMax) {
            textFieldValue.text.substring(selMin, selMax)
        } else if (commandInput.isNotEmpty()) {
            commandInput
        } else {
            outputText
        }
        if (textToCopy.isNotEmpty()) {
            clipboardManager.setText(AnnotatedString(textToCopy))
            Toast.makeText(
                context,
                if (selMin != selMax) "Copied selection to clipboard" else "Copied terminal output to clipboard",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Executes single or multi-line commands sequentially without concatenating them.
     * E.g. "npm -v\nnode -v" executes "npm -v" then "node -v".
     */
    fun executeLinesSequentially(rawText: String) {
        val normalized = rawText.replace("\r\n", "\n")
        val lines = normalized.split("\n")
        val endsWithNewline = normalized.endsWith("\n")

        if (isRunning) {
            val linesToSubmit = if (endsWithNewline) lines.dropLast(1) else lines.dropLast(1)
            linesToSubmit.forEach { line ->
                val cleanLine = line.trimEnd('\r', '\n')
                terminalViewModel.updateCommandInput(cleanLine)
                terminalViewModel.submitCurrentCommand()
            }
            if (endsWithNewline) {
                terminalViewModel.updateCommandInput("")
            } else {
                val remainder = lines.last().trimEnd('\r', '\n')
                terminalViewModel.updateCommandInput(remainder)
            }
            return
        }

        if (lines.size == 2 && lines[0].isEmpty() && lines[1].isEmpty()) {
            terminalViewModel.sendCommand("")
        } else {
            lines.forEachIndexed { idx, line ->
                val cleanLine = line.trimEnd('\r', '\n')
                if (idx == lines.lastIndex && !endsWithNewline) {
                    terminalViewModel.updateCommandInput(cleanLine)
                } else if (cleanLine.isNotBlank()) {
                    terminalViewModel.sendCommand(cleanLine)
                }
            }
        }

        if (endsWithNewline) {
            terminalViewModel.updateCommandInput("")
        }
    }

    fun pasteIntoTerminal() {
        val clip = clipboardManager.getText()?.text ?: return
        if (clip.isEmpty()) return
        val normalized = clip.replace("\r\n", "\n")
        if (!normalized.contains("\n")) {
            terminalViewModel.appendToCommandInput(normalized)
        } else {
            executeLinesSequentially(commandInput + normalized)
        }
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
        kotlinx.coroutines.yield()
        verticalScrollState.scrollTo(verticalScrollState.maxValue)
    }

    // Auto-scroll to the bottom whenever output expansion updates the scroll max value
    LaunchedEffect(verticalScrollState.maxValue) {
        verticalScrollState.scrollTo(verticalScrollState.maxValue)
    }

    LaunchedEffect(outputText.length) {
        kotlinx.coroutines.yield()
        verticalScrollState.scrollTo(verticalScrollState.maxValue)
    }

    val terminalVisualTransformation = remember(prefixText.length) {
        VisualTransformation { annotated ->
            TransformedText(
                text = styleTerminalText(annotated.text, prefixText.length),
                offsetMapping = OffsetMapping.Identity
            )
        }
    }

    val terminalKeyHandler = Modifier.onPreviewKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown) {
            if (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) {
                when (keyEvent.key) {
                    Key.C -> {
                        val hasSelection = textFieldValue.selection.min != textFieldValue.selection.max
                        if (keyEvent.isShiftPressed || keyEvent.isMetaPressed || hasSelection) {
                            copyFromTerminal()
                            true
                        } else {
                            terminalViewModel.sendInput("\u0003")
                            true
                        }
                    }
                    Key.V -> {
                        pasteIntoTerminal()
                        true
                    }
                    else -> false
                }
            } else if (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) {
                terminalViewModel.submitCurrentCommand()
                true
            } else if (keyEvent.key == Key.DirectionUp) {
                if (isRunning) {
                    terminalViewModel.sendInput("\u001b[A")
                } else {
                    terminalViewModel.navigateHistoryUp()
                }
                true
            } else if (keyEvent.key == Key.DirectionDown) {
                if (isRunning) {
                    terminalViewModel.sendInput("\u001b[B")
                } else {
                    terminalViewModel.navigateHistoryDown()
                }
                true
            } else if (keyEvent.key == Key.DirectionLeft && isRunning) {
                terminalViewModel.sendInput("\u001b[D")
                true
            } else if (keyEvent.key == Key.DirectionRight && isRunning) {
                terminalViewModel.sendInput("\u001b[C")
                true
            } else if (keyEvent.key == Key.Tab && isRunning) {
                terminalViewModel.sendInput("\t")
                true
            } else if (keyEvent.key == Key.Escape && isRunning) {
                terminalViewModel.sendInput("\u001b")
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VsCodeTheme.PanelBackground)
            .then(terminalKeyHandler)
    ) {
        // Single Compact Action & Quick-Command Strip (Copy, Paste, Clear, History, Quick Chips)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(Color(0xFF202021))
                .border(0.5.dp, VsCodeTheme.BorderColor)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Copy, Paste, History Up/Down, Enter Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = { copyFromTerminal() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = VsCodeTheme.Foreground,
                        modifier = Modifier.size(13.dp)
                    )
                }

                IconButton(
                    onClick = { pasteIntoTerminal() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste",
                        tint = VsCodeTheme.Foreground,
                        modifier = Modifier.size(13.dp)
                    )
                }

                IconButton(
                    onClick = { terminalViewModel.navigateHistoryUp() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous Command",
                        tint = VsCodeTheme.Foreground,
                        modifier = Modifier.size(15.dp)
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
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = { terminalViewModel.clearActiveSession() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Clear",
                        tint = VsCodeTheme.Foreground,
                        modifier = Modifier.size(13.dp)
                    )
                }

                if (isRunning) {
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF0E639C), RoundedCornerShape(3.dp))
                            .clickable {
                                terminalViewModel.submitCurrentCommand()
                            }
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Send",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(3.dp))

                    Row(
                        modifier = Modifier
                            .background(Color(0xFFE51400), RoundedCornerShape(3.dp))
                            .clickable {
                                terminalViewModel.sendInput("\u0003")
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "^C",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(3.dp))

                    Text(
                        text = "● Running",
                        color = Color(0xFF4EC9B0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .background(VsCodeTheme.BadgeBackground, RoundedCornerShape(3.dp))
                            .clickable {
                                terminalViewModel.submitCurrentCommand()
                            }
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
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

            Spacer(modifier = Modifier.width(6.dp))

            // Right: Scrollable Quick Chips
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isRunning) {
                    val interactiveChips = listOf(
                        "yes",
                        "no",
                        "y",
                        "n",
                        "Enter ↵",
                        "↑ Up",
                        "↓ Down"
                    )
                    interactiveChips.forEach { chip ->
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1E3A5F), RoundedCornerShape(3.dp))
                                .border(0.5.dp, Color(0xFF007ACC), RoundedCornerShape(3.dp))
                                .clickable {
                                    when (chip) {
                                        "Enter ↵" -> terminalViewModel.submitCurrentCommand()
                                        "↑ Up" -> terminalViewModel.sendInput("\u001b[A")
                                        "↓ Down" -> terminalViewModel.sendInput("\u001b[B")
                                        else -> terminalViewModel.submitInteractiveInput(chip)
                                    }
                                }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = chip,
                                color = Color(0xFFE0EEFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = VsCodeMonospaceFont
                            )
                        }
                    }
                } else {
                    val quickCommands = listOf(
                        "node -v",
                        "python --version",
                        "npm init -y",
                        "pip list",
                        "ls -la",
                        "pwd",
                        "verify-env",
                        "sandbox",
                        "git status",
                        "help",
                        "clear"
                    )
                    quickCommands.forEach { qCmd ->
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2D2D2E), RoundedCornerShape(3.dp))
                                .border(0.5.dp, VsCodeTheme.BorderColor, RoundedCornerShape(3.dp))
                                .clickable {
                                    terminalViewModel.sendCommand(qCmd)
                                }
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
            }
        }

        // Single Unified Terminal Surface (Direct typing and interactive execution right on the terminal)
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
                    canvasFocusRequester.requestFocus()
                    keyboardController?.show()
                }
        ) {
            BasicTextField(
                value = textFieldValue,
                readOnly = false,
                onValueChange = { newValue ->
                    onTerminalFocused?.invoke()

                    if (newValue.text == textFieldValue.text) {
                        // Allow arbitrary text selection anywhere in terminal output so user can Copy
                        if (!newValue.selection.collapsed) {
                            textFieldValue = newValue
                        } else {
                            val clamped = newValue.selection.start.coerceAtLeast(prefixText.length)
                            textFieldValue = newValue.copy(selection = TextRange(clamped))
                        }
                        return@BasicTextField
                    }

                    if (newValue.text.startsWith(prefixText)) {
                        val newCmd = newValue.text.substring(prefixText.length)
                        if (newCmd.contains("\n")) {
                            executeLinesSequentially(newCmd)
                        } else {
                            val clampedStart = newValue.selection.start.coerceIn(prefixText.length, newValue.text.length)
                            val clampedEnd = newValue.selection.end.coerceIn(prefixText.length, newValue.text.length)
                            textFieldValue = newValue.copy(selection = TextRange(clampedStart, clampedEnd))
                            terminalViewModel.updateCommandInput(newCmd)
                        }
                    } else if (newValue.text.length < textFieldValue.text.length) {
                        // Backspace handled safely at prompt
                        val newCmd = if (commandInput.isNotEmpty()) commandInput.dropLast(1) else ""
                        terminalViewModel.updateCommandInput(newCmd)
                        val rebuilt = prefixText + newCmd
                        textFieldValue = TextFieldValue(
                            text = rebuilt,
                            selection = TextRange(rebuilt.length)
                        )
                    } else {
                        // Text typed/pasted with cursor elsewhere on screen
                        val diffLen = newValue.text.length - textFieldValue.text.length
                        val insertPos = (newValue.selection.start - diffLen).coerceIn(0, newValue.text.length)
                        val inserted = newValue.text.substring(insertPos, (insertPos + diffLen).coerceAtMost(newValue.text.length))
                        if (inserted.contains("\n")) {
                            executeLinesSequentially(commandInput + inserted)
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
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        terminalViewModel.submitCurrentCommand()
                    },
                    onDone = {
                        terminalViewModel.submitCurrentCommand()
                    },
                    onGo = {
                        terminalViewModel.submitCurrentCommand()
                    }
                ),
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
                    .focusRequester(canvasFocusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            onTerminalFocused?.invoke()
                        }
                    }
                    .verticalScroll(verticalScrollState)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
        promptLineRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = Color(0xFF4EC9B0), fontWeight = FontWeight.Bold),
                match.range.first,
                match.range.last + 1
            )
        }
        if (prefixLength in 0..text.length) {
            addStyle(
                SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold),
                prefixLength,
                text.length
            )
        }
    }
}
