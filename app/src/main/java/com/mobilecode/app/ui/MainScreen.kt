package com.mobilecode.app.ui

import android.content.res.Configuration
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.mobilecode.app.core.designsystem.VsCodeTheme
import com.mobilecode.app.core.editor.EditorViewModel
import com.mobilecode.app.core.editor.deleteCharFromEditor
import com.mobilecode.app.core.editor.insertTextIntoEditor
import com.mobilecode.app.core.filesystem.WorkspaceManager
import com.mobilecode.app.core.git.GitViewModel
import com.mobilecode.app.core.keyboard.ProgrammingAccessoryBar
import com.mobilecode.app.core.runtime.PackageManager
import com.mobilecode.app.core.search.SearchViewModel
import com.mobilecode.app.core.settings.SettingsRepository
import com.mobilecode.app.core.terminal.TerminalViewModel
import com.mobilecode.app.ui.activitybar.ActivityBar
import com.mobilecode.app.ui.activitybar.ActivityView
import com.mobilecode.app.ui.bottompanel.BottomPanel
import com.mobilecode.app.ui.commandpalette.CommandPalette
import com.mobilecode.app.ui.commandpalette.PaletteCommand
import com.mobilecode.app.ui.editor.EditorArea
import com.mobilecode.app.ui.sidebar.SidebarContainer
import com.mobilecode.app.ui.statusbar.StatusBar
import com.mobilecode.app.ui.titlebar.TitleBar

@Composable
fun MainScreen(
    workspaceManager: WorkspaceManager,
    editorViewModel: EditorViewModel,
    terminalViewModel: TerminalViewModel,
    gitViewModel: GitViewModel,
    searchViewModel: SearchViewModel,
    settingsRepository: SettingsRepository,
    packageManager: PackageManager,
    isCommandPaletteOpen: Boolean,
    onOpenCommandPalette: () -> Unit,
    onDismissCommandPalette: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.screenWidthDp >= 600

    var isSidebarVisible by remember { mutableStateOf(isLandscape || isTablet) }
    var isBottomPanelVisible by remember { mutableStateOf(true) }
    var currentActivityView by remember { mutableStateOf(ActivityView.EXPLORER) }
    var editorWebView by remember { mutableStateOf<WebView?>(null) }
    var accessoryTarget by remember { mutableStateOf("EDIT") }

    val currentWorkspaceDir by workspaceManager.currentWorkspaceDir.collectAsState()
    val cursor by editorViewModel.cursor.collectAsState()
    val diagnostics by editorViewModel.diagnostics.collectAsState()
    val currentBranch by gitViewModel.currentBranch.collectAsState()
    val activeTab = editorViewModel.activeTab

    val commandList = remember {
        listOf(
            PaletteCommand("save", "Save", "File", "Ctrl+S") { editorViewModel.saveActiveFile() },
            PaletteCommand("save_all", "Save All", "File") { editorViewModel.saveAll() },
            PaletteCommand("toggle_term", "Toggle Terminal", "View", "Ctrl+`") { isBottomPanelVisible = !isBottomPanelVisible },
            PaletteCommand("toggle_sidebar", "Toggle Primary Side Bar", "View", "Ctrl+B") { isSidebarVisible = !isSidebarVisible },
            PaletteCommand("new_term", "Create New Terminal", "Terminal") { terminalViewModel.createSession() },
            PaletteCommand("kill_term", "Kill Terminal", "Terminal") { terminalViewModel.closeSession(terminalViewModel.activeSessionIndex.value) },
            PaletteCommand("git_commit", "Commit Staged", "Git") { gitViewModel.commit() },
            PaletteCommand("git_refresh", "Refresh Repository", "Git") { gitViewModel.refresh() },
            PaletteCommand("settings", "Open Settings", "Preferences") {
                currentActivityView = ActivityView.SETTINGS
                isSidebarVisible = true
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VsCodeTheme.EditorBackground)
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Window Title Bar & Command Center
            TitleBar(
                projectName = currentWorkspaceDir?.name,
                onOpenCommandPalette = onOpenCommandPalette,
                onToggleSidebar = { isSidebarVisible = !isSidebarVisible },
                onToggleTerminal = { isBottomPanelVisible = !isBottomPanelVisible }
            )

            // Middle Work Area
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Activity Bar (Left icon strip)
                ActivityBar(
                    currentView = currentActivityView,
                    isSidebarVisible = isSidebarVisible,
                    onViewSelected = { view ->
                        if (currentActivityView == view && isSidebarVisible) {
                            isSidebarVisible = false
                        } else {
                            currentActivityView = view
                            isSidebarVisible = true
                        }
                    }
                )

                // Sidebar Panel (Explorer / Git / Search / Settings)
                if (isSidebarVisible) {
                    SidebarContainer(
                        currentView = currentActivityView,
                        workspaceManager = workspaceManager,
                        gitViewModel = gitViewModel,
                        searchViewModel = searchViewModel,
                        settingsRepository = settingsRepository,
                        packageManager = packageManager,
                        onFileSelected = { file ->
                            editorViewModel.openFile(file)
                            if (!isLandscape && !isTablet) {
                                isSidebarVisible = false // Auto collapse on mobile
                            }
                        }
                    )
                }

                // Center Main Area: Editor + Bottom Panel
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Editor Area
                    EditorArea(
                        editorViewModel = editorViewModel,
                        onWebViewReady = { editorWebView = it },
                        modifier = Modifier.weight(1f)
                    )

                    // Bottom Panel (Terminal, Problems, Output, Debug)
                    if (isBottomPanelVisible) {
                        BottomPanel(
                            terminalViewModel = terminalViewModel,
                            editorViewModel = editorViewModel,
                            onNavigateToFile = { file, line ->
                                editorViewModel.openFile(file)
                            },
                            onClosePanel = { isBottomPanelVisible = false }
                        )
                    }
                }
            }

            // Virtual Programming Keyboard Toolbar
            ProgrammingAccessoryBar(
                targetMode = accessoryTarget,
                onToggleTarget = {
                    accessoryTarget = if (accessoryTarget == "EDIT") "TERM" else "EDIT"
                },
                onKeyInserted = { key ->
                    if (accessoryTarget == "EDIT") {
                        editorWebView?.let { insertTextIntoEditor(it, key) }
                    } else {
                        terminalViewModel.sendInput(key)
                    }
                },
                onSpecialKey = { special ->
                    if (accessoryTarget == "EDIT") {
                        when (special) {
                            "TAB" -> editorWebView?.let { insertTextIntoEditor(it, "  ") }
                            "DEL" -> editorWebView?.let { deleteCharFromEditor(it) }
                            else -> {}
                        }
                    } else {
                        when (special) {
                            "ESC" -> terminalViewModel.sendInput("\u001b")
                            "TAB" -> terminalViewModel.sendInput("\t")
                            "CTRL" -> terminalViewModel.sendInput("\u0003")
                            "UP" -> terminalViewModel.sendInput("\u001b[A")
                            "DOWN" -> terminalViewModel.sendInput("\u001b[B")
                            "LEFT" -> terminalViewModel.sendInput("\u001b[D")
                            "RIGHT" -> terminalViewModel.sendInput("\u001b[C")
                        }
                    }
                }
            )

            // VS Code Status Bar
            StatusBar(
                currentBranch = currentBranch,
                errorCount = diagnostics.errorCount,
                warningCount = diagnostics.warningCount,
                cursorLine = cursor.line,
                cursorCol = cursor.column,
                language = activeTab?.language ?: "plaintext",
                onBranchClick = {
                    currentActivityView = ActivityView.SOURCE_CONTROL
                    isSidebarVisible = true
                }
            )
        }

        // Command Palette Modal Overlay (Ctrl+Shift+P)
        if (isCommandPaletteOpen) {
            CommandPalette(
                commands = commandList,
                onDismiss = onDismissCommandPalette
            )
        }
    }
}

