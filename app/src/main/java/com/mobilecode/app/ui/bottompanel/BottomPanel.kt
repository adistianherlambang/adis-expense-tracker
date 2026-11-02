package com.mobilecode.app.ui.bottompanel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
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
    val activeSession = terminalViewModel.activeSession

    val problems by editorViewModel.problems.collectAsState()
    var selectedOutputChannel by remember { mutableStateOf("System") }
    val systemLogs by OutputLogger.systemLogs.collectAsState()
    val gitLogs by OutputLogger.gitLogs.collectAsState()
    val tasksLogs by OutputLogger.tasksLogs.collectAsState()

    val debugEntries by DebugConsoleManager.entries.collectAsState()
    var debugInput by remember { mutableStateOf("") }

    val panelHeight = if (isMaximized) 420.dp else 220.dp

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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Tabs
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PanelTabTitle(
                    title = if (problems.isNotEmpty()) "PROBLEMS (${problems.size})" else "PROBLEMS",
                    isActive = activeTab == PanelTab.PROBLEMS
                ) { activeTab = PanelTab.PROBLEMS }

                PanelTabTitle("OUTPUT", activeTab == PanelTab.OUTPUT) { activeTab = PanelTab.OUTPUT }
                PanelTabTitle("DEBUG CONSOLE", activeTab == PanelTab.DEBUG_CONSOLE) { activeTab = PanelTab.DEBUG_CONSOLE }
                PanelTabTitle("TERMINAL", activeTab == PanelTab.TERMINAL) { activeTab = PanelTab.TERMINAL }
                PanelTabTitle("PORTS", activeTab == PanelTab.PORTS) { activeTab = PanelTab.PORTS }
            }

            // Header Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (activeTab == PanelTab.TERMINAL) {
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
                            activeSessionId = activeSession.id,
                            initialHistory = terminalViewModel.getSessionHistory(activeSession.id),
                            outputEvents = terminalViewModel.outputEvents,
                            onTerminalInput = { _, data ->
                                terminalViewModel.sendInput(data)
                            }
                        )
                    }
                }

                PanelTab.PROBLEMS -> {
                    if (problems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No problems have been detected in the open workspace files.",
                                color = VsCodeTheme.SecondaryForeground,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
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
                                .background(VsCodeTheme.SidebarBackground)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Channel:", fontSize = 11.sp, color = VsCodeTheme.SecondaryForeground)
                            listOf("System", "Git", "Tasks").forEach { ch ->
                                Text(
                                    text = ch,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedOutputChannel == ch) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedOutputChannel == ch) VsCodeTheme.FocusBorder else VsCodeTheme.SecondaryForeground,
                                    modifier = Modifier
                                        .clickable { selectedOutputChannel = ch }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        val currentLogs = when (selectedOutputChannel) {
                            "Git" -> gitLogs
                            "Tasks" -> tasksLogs
                            else -> systemLogs
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
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
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
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

                        // Debug REPL Input
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .background(VsCodeTheme.SidebarBackground)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(">", color = VsCodeTheme.FocusBorder, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
                            OutlinedTextField(
                                value = debugInput,
                                onValueChange = { debugInput = it },
                                placeholder = { Text("Evaluate expression (e.g. 2 + 2, Date())...", fontSize = 11.sp, color = VsCodeTheme.SecondaryForeground) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = VsCodeTheme.Foreground,
                                    unfocusedTextColor = VsCodeTheme.Foreground
                                )
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
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            text = "FORWARDED PORTS & DEV SERVERS",
                            color = VsCodeTheme.SecondaryForeground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(VsCodeTheme.SidebarBackground)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Port 3000 (Local Web Server)", color = VsCodeTheme.Foreground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("http://localhost:3000", color = VsCodeTheme.FocusBorder, fontSize = 11.sp)
                            }
                            Text("ACTIVE", color = VsCodeTheme.GitAdded, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = if (isActive) VsCodeTheme.Foreground else VsCodeTheme.SecondaryForeground,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
        if (isActive) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(VsCodeTheme.FocusBorder)
            )
        }
    }
}
