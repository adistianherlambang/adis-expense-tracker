package com.mobilecode.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobilecode.app.core.designsystem.MobileCodeTheme
import com.mobilecode.app.core.editor.EditorViewModel
import com.mobilecode.app.core.filesystem.WorkspaceManager
import com.mobilecode.app.core.git.GitViewModel
import com.mobilecode.app.core.runtime.PackageManager
import com.mobilecode.app.core.search.SearchViewModel
import com.mobilecode.app.core.settings.SettingsRepository
import com.mobilecode.app.core.terminal.TerminalForegroundService
import com.mobilecode.app.core.terminal.TerminalViewModel
import com.mobilecode.app.ui.MainScreen

class MainActivity : ComponentActivity() {

    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var packageManager: PackageManager

    private var isCommandPaletteOpen by mutableStateOf(false)

    private val editorViewModel: EditorViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return EditorViewModel(workspaceManager) as T
            }
        }
    }

    private val terminalViewModel: TerminalViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TerminalViewModel(workspaceManager) as T
            }
        }
    }

    private val gitViewModel: GitViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return GitViewModel(workspaceManager) as T
            }
        }
    }

    private val searchViewModel: SearchViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(workspaceManager) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MobileCodeApplication
        workspaceManager = app.workspaceManager
        settingsRepository = SettingsRepository(this)
        packageManager = PackageManager(this)

        // Automatically open the starter file (index.js or README.md)
        val defaultWorkspace = workspaceManager.currentWorkspaceDir.value
        val starterFile = defaultWorkspace.listFiles()?.firstOrNull { it.isFile }
        starterFile?.let { editorViewModel.openFile(it) }

        // Start background foreground service for terminal persistence
        TerminalForegroundService.startService(this)

        setContent {
            MobileCodeTheme {
                MainScreen(
                    workspaceManager = workspaceManager,
                    editorViewModel = editorViewModel,
                    terminalViewModel = terminalViewModel,
                    gitViewModel = gitViewModel,
                    searchViewModel = searchViewModel,
                    settingsRepository = settingsRepository,
                    packageManager = packageManager,
                    isCommandPaletteOpen = isCommandPaletteOpen,
                    onOpenCommandPalette = { isCommandPaletteOpen = true },
                    onDismissCommandPalette = { isCommandPaletteOpen = false }
                )
            }
        }
    }

    /**
     * Intercept Physical Hardware Keyboard Shortcuts
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && event.isCtrlPressed) {
            when (keyCode) {
                // Ctrl + S: Save file
                KeyEvent.KEYCODE_S -> {
                    editorViewModel.saveActiveFile()
                    return true
                }
                // Ctrl + Shift + P: Command Palette
                KeyEvent.KEYCODE_P -> {
                    if (event.isShiftPressed) {
                        isCommandPaletteOpen = !isCommandPaletteOpen
                        return true
                    }
                }
                // Ctrl + W: Close active tab
                KeyEvent.KEYCODE_W -> {
                    val activeIndex = editorViewModel.activeTabIndex.value
                    if (activeIndex != -1) {
                        editorViewModel.closeTab(activeIndex)
                        return true
                    }
                }
                // Ctrl + Grave: Toggle terminal
                KeyEvent.KEYCODE_GRAVE -> {
                    terminalViewModel.createSession()
                    return true
                }
            }
        }

        // Escape closes Command Palette if open
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && isCommandPaletteOpen) {
            isCommandPaletteOpen = false
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        TerminalForegroundService.stopService(this)
    }
}

