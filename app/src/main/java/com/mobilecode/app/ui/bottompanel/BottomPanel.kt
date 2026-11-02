package com.mobilecode.app.ui.bottompanel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilecode.app.core.designsystem.VsCodeTheme
import com.mobilecode.app.core.terminal.TerminalViewModel
import com.mobilecode.app.core.terminal.TerminalWebView

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
    onClosePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(PanelTab.TERMINAL) }
    var isMaximized by remember { mutableStateOf(false) }

    val sessions by terminalViewModel.sessions.collectAsState()
    val activeSessionIndex by terminalViewModel.activeSessionIndex.collectAsState()
    val activeSession = terminalViewModel.activeSession

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
                PanelTabTitle("PROBLEMS", activeTab == PanelTab.PROBLEMS) { activeTab = PanelTab.PROBLEMS }
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
                            outputEvents = terminalViewModel.outputEvents,
                            onTerminalInput = { _, data ->
                                terminalViewModel.sendInput(data)
                            }
                        )
                    }
                }
                PanelTab.PROBLEMS -> {
                    Text(
                        text = "No problems have been detected in the workspace.",
                        color = VsCodeTheme.SecondaryForeground,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                PanelTab.OUTPUT -> {
                    Text(
                        text = "[Mobile Code] Language server & build task services initialized.",
                        color = VsCodeTheme.SecondaryForeground,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                PanelTab.DEBUG_CONSOLE -> {
                    Text(
                        text = "Debug console ready. Launch program with Run and Debug to inspect.",
                        color = VsCodeTheme.SecondaryForeground,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                PanelTab.PORTS -> {
                    Text(
                        text = "Forwarded Ports: 3000 (Local Dev Server)",
                        color = VsCodeTheme.SecondaryForeground,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
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

