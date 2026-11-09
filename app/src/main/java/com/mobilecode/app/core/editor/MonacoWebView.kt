package com.mobilecode.app.core.editor

import android.webkit.WebView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilecode.app.core.designsystem.VsCodeMonospaceFont
import com.mobilecode.app.core.designsystem.VsCodeTheme
import kotlinx.coroutines.flow.SharedFlow

/**
 * Matching bracket and quote pairs configured for auto-closing:
 * (), {}, [], "", '', and ``
 */
private val AUTO_CLOSE_PAIRS: Map<Char, Char> = mapOf(
    '(' to ')',
    '{' to '}',
    '[' to ']',
    '"' to '"',
    '\'' to '\'',
    '`' to '`'
)

private val CLOSING_CHARS: Set<Char> = setOf(')', '}', ']', '"', '\'', '`')
private val BRACKET_OPEN_TO_CLOSE: Map<Char, Char> = mapOf('(' to ')', '{' to '}', '[' to ']')
private val BRACKET_CLOSE_TO_OPEN: Map<Char, Char> = mapOf(')' to '(', '}' to '{', ']' to '[')

@Composable
fun MonacoWebView(
    activeTab: EditorTab?,
    editorCommands: SharedFlow<EditorCommand>? = null,
    onContentChanged: (fileId: String, content: String, isDirty: Boolean) -> Unit,
    onCursorPosition: (line: Int, column: Int) -> Unit,
    onSaveFile: (() -> Unit)? = null,
    onWebViewReady: ((WebView) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (activeTab == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(VsCodeTheme.EditorBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No open editor. Select or create a file to begin editing.",
                color = VsCodeTheme.SecondaryForeground,
                fontSize = 13.sp
            )
        }
        return
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    var textFieldValue by remember(activeTab.id) {
        mutableStateOf(
            TextFieldValue(
                text = activeTab.content,
                selection = TextRange(0)
            )
        )
    }

    var savedBaseline by remember(activeTab.id) { mutableStateOf(activeTab.content) }
    val undoStack = remember(activeTab.id) { mutableStateListOf<String>() }
    val redoStack = remember(activeTab.id) { mutableStateListOf<String>() }

    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showMinimap by remember { mutableStateOf(true) }

    // Keep baseline synced when saved
    LaunchedEffect(activeTab.isDirty) {
        if (!activeTab.isDirty) {
            savedBaseline = textFieldValue.text
        }
    }

    // Handle external commands (Accessory bar, Undo/Redo, GoToLine)
    LaunchedEffect(editorCommands, activeTab.id) {
        editorCommands?.collect { cmd ->
            when (cmd) {
                is EditorCommand.InsertText -> {
                    val current = textFieldValue.text
                    val start = textFieldValue.selection.min.coerceIn(0, current.length)
                    val end = textFieldValue.selection.max.coerceIn(0, current.length)
                    undoStack.add(current)
                    redoStack.clear()

                    val nextVal = handleProgrammaticInsert(textFieldValue, cmd.text, activeTab.language)
                    textFieldValue = nextVal
                    onContentChanged(activeTab.id, nextVal.text, nextVal.text != savedBaseline)
                    updateCursorFromOffset(nextVal.text, nextVal.selection.start, onCursorPosition)
                }

                is EditorCommand.DeleteChar -> {
                    val current = textFieldValue.text
                    val start = textFieldValue.selection.min.coerceIn(0, current.length)
                    val end = textFieldValue.selection.max.coerceIn(0, current.length)
                    if (start != end) {
                        undoStack.add(current)
                        val updated = current.substring(0, start) + current.substring(end)
                        textFieldValue = TextFieldValue(updated, TextRange(start))
                        onContentChanged(activeTab.id, updated, updated != savedBaseline)
                        updateCursorFromOffset(updated, start, onCursorPosition)
                    } else if (start > 0) {
                        undoStack.add(current)
                        val deletedChar = current[start - 1]
                        val nextChar = current.getOrNull(start)
                        // Smart backspace: delete both chars if inside empty auto-closed pair (), {}, [], "", '', ``
                        val deletePair = nextChar != null && AUTO_CLOSE_PAIRS[deletedChar] == nextChar
                        val updated = if (deletePair) {
                            current.substring(0, start - 1) + current.substring(start + 1)
                        } else {
                            current.substring(0, start - 1) + current.substring(end)
                        }
                        textFieldValue = TextFieldValue(updated, TextRange(start - 1))
                        onContentChanged(activeTab.id, updated, updated != savedBaseline)
                        updateCursorFromOffset(updated, start - 1, onCursorPosition)
                    }
                }

                is EditorCommand.Undo -> {
                    if (undoStack.isNotEmpty()) {
                        val prev = undoStack.removeAt(undoStack.lastIndex)
                        redoStack.add(textFieldValue.text)
                        textFieldValue = TextFieldValue(prev, TextRange(prev.length))
                        onContentChanged(activeTab.id, prev, prev != savedBaseline)
                    }
                }

                is EditorCommand.Redo -> {
                    if (redoStack.isNotEmpty()) {
                        val next = redoStack.removeAt(redoStack.lastIndex)
                        undoStack.add(textFieldValue.text)
                        textFieldValue = TextFieldValue(next, TextRange(next.length))
                        onContentChanged(activeTab.id, next, next != savedBaseline)
                    }
                }

                is EditorCommand.GoToLine -> {
                    val lines = textFieldValue.text.split("\n")
                    var offset = 0
                    val targetIdx = (cmd.line - 1).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
                    for (i in 0 until targetIdx) {
                        offset += lines[i].length + 1
                    }
                    textFieldValue = textFieldValue.copy(selection = TextRange(offset.coerceAtMost(textFieldValue.text.length)))
                    updateCursorFromOffset(textFieldValue.text, offset, onCursorPosition)
                }

                is EditorCommand.ToggleComment -> {
                    val toggled = LanguageSupport.toggleComment(textFieldValue, activeTab.language)
                    if (toggled.text != textFieldValue.text) {
                        undoStack.add(textFieldValue.text)
                        redoStack.clear()
                        textFieldValue = toggled
                        onContentChanged(activeTab.id, toggled.text, toggled.text != savedBaseline)
                        updateCursorFromOffset(toggled.text, toggled.selection.start, onCursorPosition)
                    }
                }

                is EditorCommand.FormatDocument -> {
                    val formatted = LanguageSupport.formatCode(textFieldValue.text, activeTab.language)
                    if (formatted != textFieldValue.text) {
                        undoStack.add(textFieldValue.text)
                        redoStack.clear()
                        val newCursor = textFieldValue.selection.start.coerceAtMost(formatted.length)
                        textFieldValue = TextFieldValue(formatted, TextRange(newCursor))
                        onContentChanged(activeTab.id, formatted, formatted != savedBaseline)
                        updateCursorFromOffset(formatted, newCursor, onCursorPosition)
                    }
                }
            }
        }
    }

    val isHtml = activeTab.language == "html"
    val lines = remember(textFieldValue.text) {
        textFieldValue.text.split("\n")
    }
    val lineCount = lines.size.coerceAtLeast(1)

    val cursorLine = remember(textFieldValue.text, textFieldValue.selection) {
        val pos = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
        textFieldValue.text.substring(0, pos).count { it == '\n' } + 1
    }

    val matchedBrackets = remember(textFieldValue.text, textFieldValue.selection, isHtml) {
        findMatchingBracketPair(textFieldValue.text, textFieldValue.selection.start, isHtml)
    }

    val syntaxTransformation = remember(activeTab.language, matchedBrackets) {
        VisualTransformation { annotated ->
            TransformedText(
                text = LanguageSupport.highlight(annotated.text, activeTab.language, matchedBrackets),
                offsetMapping = OffsetMapping.Identity
            )
        }
    }

    var dismissAutocompleteOffset by remember(activeTab.id) { mutableStateOf(-1) }
    val completions = remember(textFieldValue.text, textFieldValue.selection.start, activeTab.language, dismissAutocompleteOffset) {
        val offset = textFieldValue.selection.start
        if (offset == dismissAutocompleteOffset) {
            emptyList()
        } else {
            LanguageSupport.getCompletions(textFieldValue.text, offset, activeTab.language)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VsCodeTheme.EditorBackground)
    ) {
        // Editor Action & Status Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(Color(0xFF252526))
                .border(width = 0.5.dp, color = VsCodeTheme.BorderColor)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = activeTab.title,
                    color = VsCodeTheme.Foreground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = VsCodeMonospaceFont
                )
                Box(
                    modifier = Modifier
                        .background(
                            if (activeTab.isDirty) Color(0xFF4D3800) else Color(0xFF1E3A2F),
                            RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (activeTab.isDirty) "UNSAVED" else "SAVED",
                        color = if (activeTab.isDirty) VsCodeTheme.WarningColor else VsCodeTheme.GitAdded,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Format Document (Auto-Indent) Button
                IconButton(
                    onClick = {
                        val formatted = LanguageSupport.formatCode(textFieldValue.text, activeTab.language)
                        if (formatted != textFieldValue.text) {
                            undoStack.add(textFieldValue.text)
                            redoStack.clear()
                            val newCursor = textFieldValue.selection.start.coerceAtMost(formatted.length)
                            textFieldValue = TextFieldValue(formatted, TextRange(newCursor))
                            onContentChanged(activeTab.id, formatted, formatted != savedBaseline)
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                        contentDescription = "Format Document Indentation",
                        tint = VsCodeTheme.Foreground,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Toggle Comment Button
                IconButton(
                    onClick = {
                        val toggled = LanguageSupport.toggleComment(textFieldValue, activeTab.language)
                        if (toggled.text != textFieldValue.text) {
                            undoStack.add(textFieldValue.text)
                            redoStack.clear()
                            textFieldValue = toggled
                            onContentChanged(activeTab.id, toggled.text, toggled.text != savedBaseline)
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text(
                        text = "//",
                        color = VsCodeTheme.Foreground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = VsCodeMonospaceFont
                    )
                }

                // Undo
                IconButton(
                    onClick = {
                        if (undoStack.isNotEmpty()) {
                            val prev = undoStack.removeAt(undoStack.lastIndex)
                            redoStack.add(textFieldValue.text)
                            textFieldValue = TextFieldValue(prev, TextRange(prev.length))
                            onContentChanged(activeTab.id, prev, prev != savedBaseline)
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (undoStack.isNotEmpty()) VsCodeTheme.Foreground else VsCodeTheme.SecondaryForeground,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Redo
                IconButton(
                    onClick = {
                        if (redoStack.isNotEmpty()) {
                            val next = redoStack.removeAt(redoStack.lastIndex)
                            undoStack.add(textFieldValue.text)
                            textFieldValue = TextFieldValue(next, TextRange(next.length))
                            onContentChanged(activeTab.id, next, next != savedBaseline)
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (redoStack.isNotEmpty()) VsCodeTheme.Foreground else VsCodeTheme.SecondaryForeground,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Find & Replace Toggle
                IconButton(
                    onClick = { showFindBar = !showFindBar },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Find",
                        tint = if (showFindBar) VsCodeTheme.FocusBorder else VsCodeTheme.Foreground,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Save Button
                Row(
                    modifier = Modifier
                        .background(VsCodeTheme.BadgeBackground, RoundedCornerShape(3.dp))
                        .clickable {
                            savedBaseline = textFieldValue.text
                            onSaveFile?.invoke()
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Save",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Optional Find & Replace Bar
        if (showFindBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VsCodeTheme.SidebarBackground)
                    .border(0.5.dp, VsCodeTheme.BorderColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BasicTextField(
                    value = findQuery,
                    onValueChange = { findQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = VsCodeTheme.Foreground, fontSize = 12.sp, fontFamily = VsCodeMonospaceFont),
                    cursorBrush = SolidColor(VsCodeTheme.FocusBorder),
                    modifier = Modifier
                        .weight(1f)
                        .background(VsCodeTheme.InputBackground, RoundedCornerShape(2.dp))
                        .border(1.dp, VsCodeTheme.InputBorder, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    decorationBox = { inner ->
                        if (findQuery.isEmpty()) {
                            Text("Find text...", color = VsCodeTheme.SecondaryForeground, fontSize = 11.sp)
                        }
                        inner()
                    }
                )
                BasicTextField(
                    value = replaceQuery,
                    onValueChange = { replaceQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = VsCodeTheme.Foreground, fontSize = 12.sp, fontFamily = VsCodeMonospaceFont),
                    cursorBrush = SolidColor(VsCodeTheme.FocusBorder),
                    modifier = Modifier
                        .weight(1f)
                        .background(VsCodeTheme.InputBackground, RoundedCornerShape(2.dp))
                        .border(1.dp, VsCodeTheme.InputBorder, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    decorationBox = { inner ->
                        if (replaceQuery.isEmpty()) {
                            Text("Replace with...", color = VsCodeTheme.SecondaryForeground, fontSize = 11.sp)
                        }
                        inner()
                    }
                )
                Text(
                    text = "Replace All",
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .background(VsCodeTheme.BadgeBackground, RoundedCornerShape(2.dp))
                        .clickable {
                            if (findQuery.isNotEmpty()) {
                                val updated = textFieldValue.text.replace(findQuery, replaceQuery)
                                undoStack.add(textFieldValue.text)
                                textFieldValue = TextFieldValue(updated, TextRange(0))
                                onContentChanged(activeTab.id, updated, updated != savedBaseline)
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
                IconButton(onClick = { showFindBar = false }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Find", tint = VsCodeTheme.Foreground, modifier = Modifier.size(14.dp))
                }
            }
        }

        // Contextual Autocomplete Suggestion Strip (IntelliSense)
        if (completions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(Color(0xFF252526))
                    .border(width = 0.5.dp, color = VsCodeTheme.FocusBorder)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                completions.forEach { completion ->
                    val badgeColor = when (completion.kind) {
                        CompletionKind.KEYWORD -> Color(0xFF569CD6)
                        CompletionKind.TYPE -> Color(0xFF4EC9B0)
                        CompletionKind.FUNCTION -> Color(0xFFDCDCAA)
                        CompletionKind.VARIABLE -> Color(0xFF9CDCFE)
                        CompletionKind.SNIPPET -> Color(0xFFC586C0)
                    }
                    val badgeLabel = when (completion.kind) {
                        CompletionKind.KEYWORD -> "kw"
                        CompletionKind.TYPE -> "type"
                        CompletionKind.FUNCTION -> "fn"
                        CompletionKind.VARIABLE -> "var"
                        CompletionKind.SNIPPET -> "snip"
                    }

                    Row(
                        modifier = Modifier
                            .background(Color(0xFF333333), RoundedCornerShape(3.dp))
                            .border(0.5.dp, Color(0xFF474747), RoundedCornerShape(3.dp))
                            .clickable {
                                val currentText = textFieldValue.text
                                val cursor = textFieldValue.selection.start
                                val (_, wordStart) = LanguageSupport.findWordAtOffset(currentText, cursor)
                                val before = currentText.substring(0, wordStart)
                                val after = currentText.substring(cursor)
                                val newText = before + completion.insertText + after
                                val newCursor = (before + completion.insertText).length
                                undoStack.add(currentText)
                                redoStack.clear()
                                textFieldValue = TextFieldValue(newText, TextRange(newCursor))
                                onContentChanged(activeTab.id, newText, newText != savedBaseline)
                                updateCursorFromOffset(newText, newCursor, onCursorPosition)
                            }
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = VsCodeMonospaceFont
                        )
                        Text(
                            text = completion.label,
                            color = VsCodeTheme.Foreground,
                            fontSize = 11.sp,
                            fontFamily = VsCodeMonospaceFont
                        )
                    }
                }

                IconButton(
                    onClick = { dismissAutocompleteOffset = textFieldValue.selection.start },
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = VsCodeTheme.SecondaryForeground,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        // Main Code Editor Body: [Gutter] + [Code Surface] + [Minimap]
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(VsCodeTheme.EditorBackground)
        ) {
            // 1. Line Numbers Gutter
            Column(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1E1E1E))
                    .border(width = 0.5.dp, color = Color(0xFF2D2D2D))
                    .verticalScroll(verticalScrollState)
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.End
            ) {
                for (i in 1..lineCount) {
                    val isCurrentLine = (i == cursorLine)
                    Text(
                        text = i.toString(),
                        color = if (isCurrentLine) VsCodeTheme.Foreground else VsCodeTheme.EditorLineNumbers,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        fontFamily = VsCodeMonospaceFont,
                        fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.End,
                        modifier = Modifier.height(20.dp)
                    )
                }
            }

            // 2. Editable Code Surface (with Auto-Indentation & Auto-Closing Pairs)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(VsCodeTheme.EditorBackground)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { rawNewValue ->
                        val processedValue = applyEditorSmartEdits(textFieldValue, rawNewValue, activeTab.language)
                        val textChanged = processedValue.text != textFieldValue.text
                        if (textChanged) {
                            undoStack.add(textFieldValue.text)
                            if (undoStack.size > 100) undoStack.removeAt(0)
                            redoStack.clear()
                        }
                        textFieldValue = processedValue
                        if (textChanged) {
                            val dirty = processedValue.text != savedBaseline
                            onContentChanged(activeTab.id, processedValue.text, dirty)
                        }
                        updateCursorFromOffset(
                            processedValue.text,
                            processedValue.selection.start,
                            onCursorPosition
                        )
                    },
                    readOnly = activeTab.isReadOnly,
                    textStyle = TextStyle(
                        color = Color(0xFFD4D4D4),
                        fontFamily = VsCodeMonospaceFont,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = SolidColor(VsCodeTheme.FocusBorder),
                    visualTransformation = syntaxTransformation,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        .verticalScroll(verticalScrollState)
                        .horizontalScroll(horizontalScrollState)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (textFieldValue.text.isEmpty()) {
                                Text(
                                    text = "// Start typing code here...",
                                    color = VsCodeTheme.SecondaryForeground.copy(alpha = 0.5f),
                                    fontFamily = VsCodeMonospaceFont,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // 3. VS Code Minimap
            if (showMinimap) {
                Canvas(
                    modifier = Modifier
                        .width(44.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF1A1A1A))
                        .border(width = 0.5.dp, color = Color(0xFF2D2D2D))
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    val maxLinesToDraw = lines.size.coerceAtMost(150)
                    for (idx in 0 until maxLinesToDraw) {
                        val lineText = lines[idx]
                        val trimmed = lineText.trimStart()
                        if (trimmed.isNotEmpty()) {
                            val indent = (lineText.length - trimmed.length).coerceAtMost(16)
                            val barWidth = (trimmed.length * 1.2f).coerceAtMost(size.width - indent * 1.5f).coerceAtLeast(4f)
                            val yPos = idx * 4f
                            if (yPos < size.height) {
                                val barColor = when {
                                    trimmed.startsWith("//") || trimmed.startsWith("#") -> Color(0xFF6A9955)
                                    trimmed.startsWith("fun ") || trimmed.startsWith("function ") || trimmed.startsWith("def ") || trimmed.startsWith("class ") -> Color(0xFF569CD6)
                                    idx + 1 == cursorLine -> Color(0xFF007ACC)
                                    else -> Color(0xFF6E6E6E)
                                }
                                drawRect(
                                    color = barColor.copy(alpha = 0.7f),
                                    topLeft = Offset(indent * 1.2f, yPos),
                                    size = Size(barWidth, 2f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Handles smart editor behaviors for keyboard input:
 * 1. Auto-closing and matching pairs for (), {}, [], "", '', and ``
 * 2. Selection wrapping when typing an opening bracket/quote with active selection
 * 3. Skip-over when typing a closing bracket/quote right before the same closing char
 * 4. Smart backspace removing both characters of an empty pair
 * 5. Auto-indentation on Enter (\n), including smart block expansion between {|}, (|), [|]
 * 6. HTML auto-closing tag on typing '>' (e.g. <div> -> <div></div>)
 */
internal fun applyEditorSmartEdits(
    oldValue: TextFieldValue,
    newValue: TextFieldValue,
    language: String = ""
): TextFieldValue {
    val oldText = oldValue.text
    val newText = newValue.text
    if (oldText == newText) return newValue

    val oldSelMin = oldValue.selection.min.coerceIn(0, oldText.length)
    val oldSelMax = oldValue.selection.max.coerceIn(0, oldText.length)
    val newCursor = newValue.selection.start.coerceIn(0, newText.length)

    // Case 1: User had a text selection and typed a single opening bracket/quote -> wrap selection!
    if (oldSelMin != oldSelMax && newText.length == oldText.length - (oldSelMax - oldSelMin) + 1 && newCursor == oldSelMin + 1) {
        val typedChar = newText[oldSelMin]
        val closeChar = AUTO_CLOSE_PAIRS[typedChar]
        if (closeChar != null) {
            val selectedSub = oldText.substring(oldSelMin, oldSelMax)
            val wrapped = oldText.substring(0, oldSelMin) + typedChar + selectedSub + closeChar + oldText.substring(oldSelMax)
            return TextFieldValue(
                text = wrapped,
                selection = TextRange(oldSelMin + 1, oldSelMax + 1)
            )
        }
    }

    // Case 2: Single character inserted at cursor
    if (oldSelMin == oldSelMax && newText.length == oldText.length + 1 && newCursor > 0) {
        val insertPos = newCursor - 1
        val typedChar = newText[insertPos]
        val expectedUnchangedPrefix = oldText.startsWith(newText.substring(0, insertPos))
        val expectedUnchangedSuffix = oldText.substring(insertPos) == newText.substring(newCursor)

        if (expectedUnchangedPrefix && expectedUnchangedSuffix) {
            // 2a. Auto-indentation on Enter (\n)
            if (typedChar == '\n') {
                val beforeNewline = oldText.substring(0, insertPos)
                val afterNewline = oldText.substring(insertPos)
                val lineStart = beforeNewline.lastIndexOf('\n') + 1
                val currentLine = beforeNewline.substring(lineStart)
                val baseIndent = currentLine.takeWhile { it == ' ' || it == '\t' }
                val trimmedLine = currentLine.trimEnd()

                val prevNonSpace = trimmedLine.lastOrNull()
                val nextChar = afterNewline.firstOrNull()

                // Check if Enter was pressed right between an empty bracket pair: {|}, (|), [|]
                if (prevNonSpace != null && nextChar != null && BRACKET_OPEN_TO_CLOSE[prevNonSpace] == nextChar) {
                    val innerIndent = "$baseIndent    "
                    val expanded = "$beforeNewline\n$innerIndent\n$baseIndent$afterNewline"
                    val targetCursor = beforeNewline.length + 1 + innerIndent.length
                    return TextFieldValue(
                        text = expanded,
                        selection = TextRange(targetCursor)
                    )
                }

                // Check if line ends with {, (, [, or : (increase indent by 4 spaces)
                val shouldIncreaseIndent = prevNonSpace == '{' || prevNonSpace == '(' || prevNonSpace == '[' || prevNonSpace == ':'
                val nextIndent = if (shouldIncreaseIndent) "$baseIndent    " else baseIndent
                if (nextIndent.isNotEmpty()) {
                    val indentedText = "$beforeNewline\n$nextIndent$afterNewline"
                    val targetCursor = beforeNewline.length + 1 + nextIndent.length
                    return TextFieldValue(
                        text = indentedText,
                        selection = TextRange(targetCursor)
                    )
                }
            }

            // 2b. HTML tag auto-closing: typing '>' after '<tag' inserts '</tag>'
            if (language == "html" && typedChar == '>') {
                val before = oldText.substring(0, insertPos)
                val lastOpenTag = Regex("<([a-zA-Z0-9\\-]+)(?:\\s+[^>]*)?$").find(before)
                if (lastOpenTag != null) {
                    val tagName = lastOpenTag.groupValues[1].lowercase()
                    val voidElements = setOf(
                        "area", "base", "br", "col", "embed", "hr", "img", "input",
                        "link", "meta", "param", "source", "track", "wbr", "!doctype"
                    )
                    if (tagName !in voidElements && !before.endsWith("/")) {
                        val closeTag = "</$tagName>"
                        val autoClosed = oldText.substring(0, insertPos) + ">" + closeTag + oldText.substring(insertPos)
                        return TextFieldValue(
                            text = autoClosed,
                            selection = TextRange(insertPos + 1)
                        )
                    }
                }
            }

            // 2c. Skip-over closing bracket/quote if already present at cursor
            val nextCharInOld = oldText.getOrNull(insertPos)
            if (typedChar in CLOSING_CHARS && nextCharInOld == typedChar) {
                // For quotes (" ' `), if typedChar == nextCharInOld, skip over the existing closing quote
                return TextFieldValue(
                    text = oldText,
                    selection = TextRange(insertPos + 1)
                )
            }

            // 2d. Auto-close opening bracket or quote: (), {}, [], "", '', ``
            val matchingClose = AUTO_CLOSE_PAIRS[typedChar]
            if (matchingClose != null) {
                val autoClosed = oldText.substring(0, insertPos) + typedChar + matchingClose + oldText.substring(insertPos)
                return TextFieldValue(
                    text = autoClosed,
                    selection = TextRange(insertPos + 1)
                )
            }
        }
    }

    // Case 3: Single character deleted via Backspace inside an empty pair: (|), {|}, [|], "|", '|', `|`
    if (oldSelMin == oldSelMax && newText.length == oldText.length - 1 && newCursor == oldSelMin - 1 && oldSelMin > 0) {
        val deletedChar = oldText[oldSelMin - 1]
        val followingChar = oldText.getOrNull(oldSelMin)
        if (followingChar != null && AUTO_CLOSE_PAIRS[deletedChar] == followingChar) {
            val withoutPair = oldText.substring(0, oldSelMin - 1) + oldText.substring(oldSelMin + 1)
            return TextFieldValue(
                text = withoutPair,
                selection = TextRange(oldSelMin - 1)
            )
        }
    }

    return newValue
}

/**
 * Handles insertions triggered from the Virtual Programming Accessory Bar so that
 * tapping (, {, [, ", ', or ` also auto-closes or wraps selection properly.
 */
internal fun handleProgrammaticInsert(
    currentValue: TextFieldValue,
    inserted: String,
    language: String = ""
): TextFieldValue {
    val text = currentValue.text
    val start = currentValue.selection.min.coerceIn(0, text.length)
    val end = currentValue.selection.max.coerceIn(0, text.length)

    if (inserted.length == 1) {
        val ch = inserted[0]
        val close = AUTO_CLOSE_PAIRS[ch]
        if (start != end && close != null) {
            val selected = text.substring(start, end)
            val updated = text.substring(0, start) + ch + selected + close + text.substring(end)
            return TextFieldValue(updated, TextRange(start + 1, end + 1))
        }
        val rawNext = text.substring(0, start) + inserted + text.substring(end)
        val candidate = TextFieldValue(rawNext, TextRange(start + 1))
        return applyEditorSmartEdits(currentValue.copy(selection = TextRange(start)), candidate, language)
    }

    val updated = text.substring(0, start) + inserted + text.substring(end)
    return TextFieldValue(updated, TextRange(start + inserted.length))
}

/**
 * Finds the indices of the matching bracket pair (), {}, [], and <> adjacent to [cursorOffset].
 */
internal fun findMatchingBracketPair(
    text: String,
    cursorOffset: Int,
    isHtml: Boolean = false
): Pair<Int, Int>? {
    if (text.isEmpty()) return null
    val safeCursor = cursorOffset.coerceIn(0, text.length)

    val openToClose = if (isHtml) BRACKET_OPEN_TO_CLOSE + ('<' to '>') else BRACKET_OPEN_TO_CLOSE
    val closeToOpen = if (isHtml) BRACKET_CLOSE_TO_OPEN + ('>' to '<') else BRACKET_CLOSE_TO_OPEN

    val checkPos = when {
        safeCursor < text.length && (text[safeCursor] in openToClose || text[safeCursor] in closeToOpen) -> safeCursor
        safeCursor > 0 && (text[safeCursor - 1] in openToClose || text[safeCursor - 1] in closeToOpen) -> safeCursor - 1
        else -> return null
    }

    val ch = text[checkPos]
    if (ch in openToClose) {
        val targetClose = openToClose[ch]!!
        var depth = 0
        for (i in checkPos until text.length) {
            when (text[i]) {
                ch -> depth++
                targetClose -> {
                    depth--
                    if (depth == 0) return checkPos to i
                }
            }
        }
    } else if (ch in closeToOpen) {
        val targetOpen = closeToOpen[ch]!!
        var depth = 0
        for (i in checkPos downTo 0) {
            when (text[i]) {
                ch -> depth++
                targetOpen -> {
                    depth--
                    if (depth == 0) return i to checkPos
                }
            }
        }
    }
    return null
}

/**
 * Formats document indentation cleanly based on the language grammar.
 */
internal fun formatDocumentIndentation(
    text: String,
    language: String = "javascript",
    indentSize: Int = 4
): String {
    return LanguageSupport.formatCode(text, language, indentSize)
}

private fun updateCursorFromOffset(
    text: String,
    offset: Int,
    onCursorPosition: (Int, Int) -> Unit
) {
    val safeOffset = offset.coerceIn(0, text.length)
    val before = text.substring(0, safeOffset)
    val line = before.count { it == '\n' } + 1
    val lastNewline = before.lastIndexOf('\n')
    val col = if (lastNewline == -1) safeOffset + 1 else safeOffset - lastNewline
    onCursorPosition(line, col)
}

private fun highlightCodeSyntax(
    text: String,
    language: String,
    matchedBrackets: Pair<Int, Int>? = null
): AnnotatedString {
    return LanguageSupport.highlight(text, language, matchedBrackets)
}

// Legacy helper stubs kept for binary compatibility if referenced
fun insertTextIntoEditor(webView: WebView, text: String) {}
fun deleteCharFromEditor(webView: WebView) {}
