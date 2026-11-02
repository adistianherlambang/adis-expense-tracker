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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
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
                    val updated = current.substring(0, start) + cmd.text + current.substring(end)
                    val newCursor = start + cmd.text.length
                    textFieldValue = TextFieldValue(
                        text = updated,
                        selection = TextRange(newCursor)
                    )
                    onContentChanged(activeTab.id, updated, updated != savedBaseline)
                    updateCursorFromOffset(updated, newCursor, onCursorPosition)
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
                        val updated = current.substring(0, start - 1) + current.substring(end)
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
            }
        }
    }

    val lines = remember(textFieldValue.text) {
        textFieldValue.text.split("\n")
    }
    val lineCount = lines.size.coerceAtLeast(1)

    val cursorLine = remember(textFieldValue.text, textFieldValue.selection) {
        val pos = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
        textFieldValue.text.substring(0, pos).count { it == '\n' } + 1
    }

    val syntaxTransformation = remember(activeTab.language) {
        VisualTransformation { annotated ->
            TransformedText(
                text = highlightCodeSyntax(annotated.text, activeTab.language),
                offsetMapping = OffsetMapping.Identity
            )
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
                        imageVector = Icons.Default.Undo,
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
                        imageVector = Icons.Default.Redo,
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

            // 2. Editable Code Surface
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
                    onValueChange = { newValue ->
                        val textChanged = newValue.text != textFieldValue.text
                        if (textChanged) {
                            undoStack.add(textFieldValue.text)
                            if (undoStack.size > 100) undoStack.removeAt(0)
                            redoStack.clear()
                        }
                        textFieldValue = newValue
                        if (textChanged) {
                            val dirty = newValue.text != savedBaseline
                            onContentChanged(activeTab.id, newValue.text, dirty)
                        }
                        updateCursorFromOffset(
                            newValue.text,
                            newValue.selection.start,
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

private val keywordRegex = Regex(
    "\\b(fun|val|var|class|object|interface|package|import|return|if|else|when|for|while|do|try|catch|finally|throw|true|false|null|" +
    "function|const|let|async|await|export|default|from|new|this|typeof|instanceof|def|elif|except|lambda|yield|pass|None|True|False|" +
    "public|private|protected|static|void|int|double|float|boolean|char|string|String|Int|Boolean|Unit|List|Map|Set)\\b"
)
private val stringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
private val numberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
private val commentRegex = Regex("//.*|#.*|/\\*[\\s\\S]*?\\*/")
private val functionCallRegex = Regex("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?=\\()")

private fun highlightCodeSyntax(text: String, language: String): AnnotatedString {
    if (text.isEmpty() || text.length > 40000) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        append(text)

        // 1. Keywords
        keywordRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = VsCodeTheme.SyntaxKeyword, fontWeight = FontWeight.SemiBold),
                match.range.first,
                match.range.last + 1
            )
        }

        // 2. Function Calls
        functionCallRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = VsCodeTheme.SyntaxFunction),
                match.range.first,
                match.range.last + 1
            )
        }

        // 3. Numbers
        numberRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = VsCodeTheme.SyntaxNumber),
                match.range.first,
                match.range.last + 1
            )
        }

        // 4. Strings
        stringRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = VsCodeTheme.SyntaxString),
                match.range.first,
                match.range.last + 1
            )
        }

        // 5. Comments (highest priority)
        commentRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = VsCodeTheme.SyntaxComment),
                match.range.first,
                match.range.last + 1
            )
        }
    }
}

// Legacy helper stubs kept for binary compatibility if referenced
fun insertTextIntoEditor(webView: WebView, text: String) {}
fun deleteCharFromEditor(webView: WebView) {}
