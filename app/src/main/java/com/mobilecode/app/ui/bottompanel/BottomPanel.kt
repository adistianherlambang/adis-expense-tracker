package com.mobilecode.app.ui.bottompanel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilecode.app.core.designsystem.VsCodeMonospaceFont
import com.mobilecode.app.core.designsystem.VsCodeTheme
import com.mobilecode.app.core.editor.DiagnosticItem
import com.mobilecode.app.core.editor.DiagnosticSeverity
import com.mobilecode.app.core.editor.EditorViewModel
import com.mobilecode.app.core.terminal.DebugConsoleManager
import com.mobilecode.app.core.terminal.OutputLogger
import com.mobilecode.app.core.terminal.TerminalViewModel
import com.mobilecode.app.core.terminal.TerminalWebView
import java.io.File

enum class PanelTab {
    PROBLEMS,
    OUTPUT,
    DEBUG_CONSOLE,
    TERMINAL,
    PORTS
}

@Composable
fun BottomPanel(
    terminalViewModel: TerminalViewModel,
    editorViewModel: EditorViewModel,
    onNavigateToFile: (File, Int) -> Unit = { _, _ -> },
    onClosePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(PanelTab.TERMINAL) }
    var isMaximized by remember { mutableStateOf(false) }

    val sessions by terminalViewModel.sessions.collectAsState()
    val activeSessionIndex by terminalViewModel.activeSessionIndex.collectAsState()
    val activeSession = sessions.getOrNull(activeSessionIndex)

    val problems by editorViewModel.problems.collectAsState()
    val activeEditorTabs by editorViewModel.tabs.collectAsState()
    val activeEditorIndex by editorViewModel.activeTabIndex.collectAsState()
    val activeEditorTab = activeEditorTabs.getOrNull(activeEditorIndex)

    var selectedOutputChannel by remember { mutableStateOf("System") }
    val systemLogs by OutputLogger.systemLogs.collectAsState()
    val gitLogs by OutputLogger.gitLogs.collectAsState()
    val tasksLogs by OutputLogger.tasksLogs.collectAsState()

    val debugEntries by DebugConsoleManager.entries.collectAsState()
    var debugInput by remember { mutableStateOf("") }

    val panelHeight = if (isMaximized) 380.dp else 210.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight)
            .background(VsCodeTheme.PanelBackground)
            .border(width = 1.dp, color = VsCodeTheme.BorderColor)
    ) {
        // Panel Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(35.dp)
                .background(VsCodeTheme.SidebarBackground)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scrollable Tabs Row (IntrinsicSize.Max prevents active tab from pushing right tabs off screen)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PanelTabTitle(
                    title = if (problems.isNotEmpty()) "PROBLEMS (${problems.size})" else "PROBLEMS",
                    isActive = activeTab == PanelTab.PROBLEMS,
                    onClick = { activeTab = PanelTab.PROBLEMS }
                )

                PanelTabTitle(
                    title = "OUTPUT",
                    isActive = activeTab == PanelTab.OUTPUT,
                    onClick = { activeTab = PanelTab.OUTPUT }
                )

                PanelTabTitle(
                    title = "DEBUG CONSOLE",
                    isActive = activeTab == PanelTab.DEBUG_CONSOLE,
                    onClick = { activeTab = PanelTab.DEBUG_CONSOLE }
                )

                PanelTabTitle(
                    title = "TERMINAL",
                    isActive = activeTab == PanelTab.TERMINAL,
                    onClick = { activeTab = PanelTab.TERMINAL }
                )

                PanelTabTitle(
                    title = "PORTS",
                    isActive = activeTab == PanelTab.PORTS,
                    onClick = { activeTab = PanelTab.PORTS }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Header Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (activeTab == PanelTab.TERMINAL) {
                    // Session switcher badge if multiple sessions
                    if (sessions.size > 1) {
                        sessions.forEachIndexed { idx, s ->
                            Text(
                                text = s.title,
                                fontSize = 10.sp,
                                color = if (idx == activeSessionIndex) Color.White else VsCodeTheme.SecondaryForeground,
                                modifier = Modifier
                                    .background(
                                        if (idx == activeSessionIndex) VsCodeTheme.BadgeBackground else Color.Transparent,
                                        RoundedCornerShape(3.dp)
                                    )
                                    .clickable { terminalViewModel.selectSession(idx) }
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                    }

                    IconButton(
                        onClick = { terminalViewModel.createSession() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Terminal",
                            tint = VsCodeTheme.Foreground,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { terminalViewModel.clearActiveSession() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Clear Terminal",
                            tint = VsCodeTheme.Foreground,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    IconButton(
                        onClick = { terminalViewModel.closeSession(activeSessionIndex) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Kill Terminal",
                            tint = VsCodeTheme.Foreground,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else if (activeTab == PanelTab.OUTPUT) {
                    IconButton(
                        onClick = { OutputLogger.clear(selectedOutputChannel) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Clear Output",
                            tint = VsCodeTheme.Foreground,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else if (activeTab == PanelTab.DEBUG_CONSOLE) {
                    IconButton(
                        onClick = { DebugConsoleManager.evaluate("clear") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Clear Console",
                            tint = VsCodeTheme.Foreground,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { isMaximized = !isMaximized },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isMaximized) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = "Resize Panel",
                        tint = VsCodeTheme.Foreground,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onClosePanel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Panel",
                        tint = VsCodeTheme.Foreground,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Panel Body Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VsCodeTheme.PanelBackground)
        ) {
            when (activeTab) {
                PanelTab.TERMINAL -> {
                    if (activeSession != null) {
                        TerminalWebView(
                            session = activeSession,
                            onCommandSubmitted = { cmd ->
                                terminalViewModel.sendCommand(cmd)
                            }
                        )
                    }
                }

                PanelTab.PROBLEMS -> {
                    if (problems.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = VsCodeTheme.GitAdded,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (activeEditorTab != null) {
                                        "No syntax problems detected in ${activeEditorTab.title}"
                                    } else {
                                        "No problems have been detected in the workspace."
                                    },
                                    color = VsCodeTheme.Foreground,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Live syntax diagnostics active for JSON, JavaScript, TypeScript, and Python.",
                                color = VsCodeTheme.SecondaryForeground,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            items(problems) { problem ->
                                ProblemRow(
                                    item = problem,
                                    onClick = { onNavigateToFile(problem.file, problem.line) }
                                )
                            }
                        }
                    }
                }

                PanelTab.OUTPUT -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Channel Selector Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .background(Color(0xFF202021))
                                .border(0.5.dp, VsCodeTheme.BorderColor)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Channel:", fontSize = 11.sp, color = VsCodeTheme.SecondaryForeground)
                            listOf("System", "Git", "Tasks").forEach { ch ->
                                val selected = selectedOutputChannel == ch
                                Text(
                                    text = ch,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else VsCodeTheme.SecondaryForeground,
                                    modifier = Modifier
                                        .background(
                                            if (selected) VsCodeTheme.BadgeBackground else Color.Transparent,
                                            RoundedCornerShape(3.dp)
                                        )
                                        .clickable { selectedOutputChannel = ch }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        val currentLogs = when (selectedOutputChannel) {
                            "Git" -> gitLogs
                            "Tasks" -> tasksLogs
                            else -> systemLogs
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            items(currentLogs) { log ->
                                Text(
                                    text = log,
                                    color = VsCodeTheme.Foreground,
                                    fontSize = 11.sp,
                                    fontFamily = VsCodeMonospaceFont,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                PanelTab.DEBUG_CONSOLE -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            items(debugEntries) { entry ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    if (entry.prompt.isNotEmpty()) {
                                        Text(
                                            text = "> ${entry.prompt}",
                                            color = VsCodeTheme.FocusBorder,
                                            fontSize = 12.sp,
                                            fontFamily = VsCodeMonospaceFont
                                        )
                                    }
                                    Text(
                                        text = entry.result,
                                        color = if (entry.isError) VsCodeTheme.ErrorColor else VsCodeTheme.Foreground,
                                        fontSize = 12.sp,
                                        fontFamily = VsCodeMonospaceFont
                                    )
                                }
                            }
                        }

                        // Debug REPL Input Row (using BasicTextField so text is never clipped)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .background(VsCodeTheme.SidebarBackground)
                                .border(0.5.dp, VsCodeTheme.BorderColor)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ">",
                                color = VsCodeTheme.FocusBorder,
                                fontWeight = FontWeight.Bold,
                                fontFamily = VsCodeMonospaceFont,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            BasicTextField(
                                value = debugInput,
                                onValueChange = { value ->
                                    if (value.endsWith("\n")) {
                                        val expr = value.trimEnd('\n', '\r')
                                        DebugConsoleManager.evaluate(expr)
                                        debugInput = ""
                                    } else {
                                        debugInput = value
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
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        DebugConsoleManager.evaluate(debugInput)
                                        debugInput = ""
                                    }
                                ),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (debugInput.isEmpty()) {
                                            Text(
                                                text = "Evaluate expression (e.g. 2 + 2, 10 * 5, help, env)...",
                                                fontSize = 11.sp,
                                                color = VsCodeTheme.SecondaryForeground,
                                                fontFamily = VsCodeMonospaceFont
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                            IconButton(
                                onClick = {
                                    DebugConsoleManager.evaluate(debugInput)
                                    debugInput = ""
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Evaluate",
                                    tint = VsCodeTheme.BadgeBackground,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                PanelTab.PORTS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "FORWARDED PORTS & LOCAL SERVERS",
                            color = VsCodeTheme.SecondaryForeground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(VsCodeTheme.SidebarBackground, RoundedCornerShape(4.dp))
                                .border(0.5.dp, VsCodeTheme.BorderColor, RoundedCornerShape(4.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Port 3000 (Dev Server)",
                                    color = VsCodeTheme.Foreground,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "http://localhost:3000",
                                    color = VsCodeTheme.FocusBorder,
                                    fontSize = 11.sp,
                                    fontFamily = VsCodeMonospaceFont
                                )
                            }
                            Text(
                                text = "READY",
                                color = VsCodeTheme.GitAdded,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProblemRow(item: DiagnosticItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.severity == DiagnosticSeverity.ERROR) Icons.Default.Cancel else Icons.Default.Warning,
            contentDescription = null,
            tint = if (item.severity == DiagnosticSeverity.ERROR) VsCodeTheme.ErrorColor else VsCodeTheme.WarningColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${item.file.name} [Ln ${item.line}, Col ${item.column}]: ",
            color = VsCodeTheme.SecondaryForeground,
            fontSize = 11.sp,
            fontFamily = VsCodeMonospaceFont
        )
        Text(
            text = item.message,
            color = VsCodeTheme.Foreground,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun PanelTabTitle(
    title: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = if (isActive) VsCodeTheme.Foreground else VsCodeTheme.SecondaryForeground,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (isActive) VsCodeTheme.FocusBorder else Color.Transparent)
        )
    }
}
