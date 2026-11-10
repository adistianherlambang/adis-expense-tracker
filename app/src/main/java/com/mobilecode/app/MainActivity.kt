package com.mobilecode.app

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.os.Build
import com.mobilecode.app.core.designsystem.MobileCodeTheme
import com.mobilecode.app.core.editor.EditorViewModel
import com.mobilecode.app.core.filesystem.WorkspaceManager
import com.mobilecode.app.core.git.GitViewModel
import com.mobilecode.app.core.runtime.EnvironmentManager
import com.mobilecode.app.core.runtime.PackageManager
import com.mobilecode.app.core.search.SearchViewModel
import com.mobilecode.app.core.settings.SettingsRepository
import com.mobilecode.app.core.terminal.OutputLogger
import com.mobilecode.app.core.terminal.TerminalForegroundService
import com.mobilecode.app.core.terminal.TerminalViewModel
import com.mobilecode.app.ui.MainScreen

class MainActivity : ComponentActivity() {

    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var packageManager: PackageManager
    private lateinit var environmentManager: EnvironmentManager

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
        val isNewWindow = intent.getBooleanExtra("IS_NEW_WINDOW", false)
        workspaceManager = if (isNewWindow) {
            OutputLogger.logSystem("New window initialized with empty workspace")
            WorkspaceManager(this).apply {
                clearWorkspaceState()
            }
        } else {
            val wm = app.workspaceManager
            val restored = wm.restoreLastWorkspaceSync()
            if (restored != null) {
                OutputLogger.logSystem("Restored last workspace on startup: ${restored.absolutePath}")
            }
            wm
        }
        settingsRepository = SettingsRepository(this)
        packageManager = PackageManager(this)
        environmentManager = EnvironmentManager(this)

        // Request POST_NOTIFICATIONS runtime permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

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
                    environmentManager = environmentManager,
                    isCommandPaletteOpen = isCommandPaletteOpen,
                    onOpenCommandPalette = { isCommandPaletteOpen = true },
                    onDismissCommandPalette = { isCommandPaletteOpen = false },
                    onOpenNewWindow = { openNewWindow() }
                )
            }
        }
    }

    /**
     * Opens a new independent IDE window (multi-window / multi-document task)
     * while keeping the existing window open. Triggered via Ctrl+N or Cmd+N.
     */
    fun openNewWindow() {
        OutputLogger.logSystem("Opening new IDE window (Ctrl+N / Cmd+N) while keeping current window active")
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            putExtra("WINDOW_ID", System.currentTimeMillis())
            putExtra("IS_NEW_WINDOW", true)
        }
        startActivity(intent)
        Toast.makeText(this, "Opened New Window (Ctrl+N / Cmd+N)", Toast.LENGTH_SHORT).show()
    }

    /**
     * Intercept Physical Hardware Keyboard Shortcuts (Ctrl+* or Cmd+* on Mac)
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && (event.isCtrlPressed || event.isMetaPressed)) {
            when (keyCode) {
                // Ctrl + N or Cmd + N: Open a new window while keeping existing window open
                KeyEvent.KEYCODE_N -> {
                    openNewWindow()
                    return true
                }
                // Ctrl + S or Cmd + S: Save file
                KeyEvent.KEYCODE_S -> {
                    editorViewModel.saveActiveFile()
                    return true
                }
                // Ctrl + Shift + P or Cmd + Shift + P: Command Palette
                KeyEvent.KEYCODE_P -> {
                    if (event.isShiftPressed) {
                        isCommandPaletteOpen = !isCommandPaletteOpen
                        return true
                    }
                }
                // Ctrl + W or Cmd + W: Close active tab
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

