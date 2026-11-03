package com.mobilecode.app.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobilecode.app.core.designsystem.VsCodeTheme
import com.mobilecode.app.core.filesystem.WorkspaceManager
import com.mobilecode.app.core.git.GitViewModel
import com.mobilecode.app.core.runtime.PackageManager
import com.mobilecode.app.core.search.SearchViewModel
import com.mobilecode.app.core.settings.SettingsRepository
import com.mobilecode.app.ui.activitybar.ActivityView
import java.io.File

@Composable
fun SidebarContainer(
    currentView: ActivityView,
    workspaceManager: WorkspaceManager,
    gitViewModel: GitViewModel,
    searchViewModel: SearchViewModel,
    settingsRepository: SettingsRepository,
    packageManager: PackageManager,
    onFileSelected: (File) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 260.dp
) {
    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(VsCodeTheme.SidebarBackground)
            .border(width = 1.dp, color = VsCodeTheme.BorderColor)
    ) {
        when (currentView) {
            ActivityView.EXPLORER -> {
                ExplorerView(
                    workspaceManager = workspaceManager,
                    onFileSelected = onFileSelected
                )
            }
            ActivityView.SEARCH -> {
                SearchView(
                    searchViewModel = searchViewModel,
                    onFileSelected = onFileSelected
                )
            }
            ActivityView.SOURCE_CONTROL -> {
                SourceControlView(
                    gitViewModel = gitViewModel
                )
            }
            ActivityView.SETTINGS -> {
                SettingsView(
                    settingsRepository = settingsRepository,
                    packageManager = packageManager
                )
            }
            ActivityView.RUN_DEBUG -> {
                SettingsView(
                    settingsRepository = settingsRepository,
                    packageManager = packageManager
                )
            }
            ActivityView.EXTENSIONS -> {
                SettingsView(
                    settingsRepository = settingsRepository,
                    packageManager = packageManager
                )
            }
        }
    }
}

