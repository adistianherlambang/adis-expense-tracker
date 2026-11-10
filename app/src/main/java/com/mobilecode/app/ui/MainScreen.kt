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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.mobilecode.app.core.designsystem.VsCodeTheme
import com.mobilecode.app.core.editor.EditorViewModel
import com.mobilecode.app.core.filesystem.WorkspaceManager
import com.mobilecode.app.core.git.GitViewModel
import com.mobilecode.app.core.keyboard.ProgrammingAccessoryBar
import com.mobilecode.app.core.runtime.EnvironmentManager
import com.mobilecode.app.core.runtime.PackageManager
import com.mobilecode.app.core.search.SearchViewModel
import com.mobilecode.app.core.settings.SettingsRepository
import com.mobilecode.app.core.terminal.TerminalViewModel
import com.mobilecode.app.ui.activitybar.ActivityBar
import com.mobilecode.app.ui.activitybar.ActivityView
import com.mobilecode.app.ui.bottompanel.BottomPanel
import com.mobilecode.app.ui.commandpalette.CommandPalette
import com.mobilecode.app.ui.commandpalette.PaletteCommand
import com.mobilecode.app.ui.components.HorizontalResizeHandle
import com.mobilecode.app.ui.components.VerticalResizeHandle
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
    onDismissCommandPalette: () -> Unit,
    onOpenNewWindow: () -> Unit = {},
    environmentManager: EnvironmentManager = remember { EnvironmentManager(workspaceManager.appContext) }
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.screenWidthDp >= 600

    var isSidebarVisible by remember { mutableStateOf(isLandscape || isTablet) }
    var isBottomPanelVisible by remember { mutableStateOf(true) }
    var sidebarWidth by remember { mutableStateOf(260.dp) }
    var bottomPanelHeight by remember { mutableStateOf(240.dp) }
    var currentActivityView by remember { mutableStateOf(ActivityView.EXPLORER) }
    var editorWebView by remember { mutableStateOf<WebView?>(null) }
    var accessoryTarget by remember { mutableStateOf("EDIT") }

    val currentWorkspaceDir by workspaceManager.currentWorkspaceDir.collectAsState()
    val tabs by editorViewModel.tabs.collectAsState()
    val activeTabIndex by editorViewModel.activeTabIndex.collectAsState()
    val cursor by editorViewModel.cursor.collectAsState()
    val diagnostics by editorViewModel.diagnostics.collectAsState()
    val currentBranch by gitViewModel.currentBranch.collectAsState()
    val activeTab = tabs.getOrNull(activeTabIndex)

    val commandList = remember {
        listOf(
            PaletteCommand("new_window", "New Window", "Window", "Ctrl+N / Cmd+N") { onOpenNewWindow() },
            PaletteCommand("verify_env", "Run Environment Verification Task", "Tasks") {
                isBottomPanelVisible = true
                terminalViewModel.sendCommand("verify-env")
            },
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
            },
            PaletteCommand("environments", "Open Environments (Docker Market)", "Preferences") {
                currentActivityView = ActivityView.ENVIRONMENT
                isSidebarVisible = true
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VsCodeTheme.EditorBackground)
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Window Title Bar & Command Center
            TitleBar(
                projectName = currentWorkspaceDir?.name,
                onOpenCommandPalette = onOpenCommandPalette,
                onToggleSidebar = { isSidebarVisible = !isSidebarVisible },
                onToggleTerminal = { isBottomPanelVisible = !isBottomPanelVisible },
                onOpenNewWindow = onOpenNewWindow
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
                        environmentManager = environmentManager,
                        onFileSelected = { file ->
                            editorViewModel.openFile(file)
                            if (!isLandscape && !isTablet) {
                                isSidebarVisible = false // Auto collapse on mobile
                            }
                        },
                        terminalViewModel = terminalViewModel,
                        width = sidebarWidth
                    )

                    VerticalResizeHandle(
                        onDeltaX = { deltaX ->
                            sidebarWidth = (sidebarWidth + deltaX).coerceIn(160.dp, 500.dp)
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
                        onOpenFolderRequested = {
                            currentActivityView = ActivityView.EXPLORER
                            isSidebarVisible = true
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Bottom Panel (Terminal, Problems, Output, Debug)
                    if (isBottomPanelVisible) {
                        HorizontalResizeHandle(
                            onDeltaY = { deltaY ->
                                // Dragging up increases height, dragging down decreases height
                                bottomPanelHeight = (bottomPanelHeight - deltaY).coerceIn(90.dp, 580.dp)
                            }
                        )

                        BottomPanel(
                            terminalViewModel = terminalViewModel,
                            editorViewModel = editorViewModel,
                            onNavigateToFile = { file, line ->
                                editorViewModel.openFile(file)
                                editorViewModel.goToLine(line)
                            },
                            onTerminalFocused = {
                                accessoryTarget = "TERM"
                            },
                            onClosePanel = { isBottomPanelVisible = false },
                            height = bottomPanelHeight
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
                        editorViewModel.insertText(key)
                    } else {
                        terminalViewModel.sendInput(key)
                    }
                },
                onSpecialKey = { special ->
                    if (accessoryTarget == "EDIT") {
                        when (special) {
                            "TAB" -> editorViewModel.insertText("  ")
                            "DEL" -> editorViewModel.deleteChar()
                            "ESC" -> {}
                            else -> {}
                        }
                    } else {
                        when (special) {
                            "ESC" -> terminalViewModel.sendInput("\u001b")
                            "TAB" -> terminalViewModel.sendInput("\t")
                            "DEL" -> terminalViewModel.backspaceCommandInput()
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

