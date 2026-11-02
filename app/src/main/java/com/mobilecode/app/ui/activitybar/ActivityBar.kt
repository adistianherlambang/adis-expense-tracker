package com.mobilecode.app.ui.activitybar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mobilecode.app.core.designsystem.VsCodeTheme

enum class ActivityView {
    EXPLORER,
    SEARCH,
    SOURCE_CONTROL,
    RUN_DEBUG,
    EXTENSIONS,
    SETTINGS
}

@Composable
fun ActivityBar(
    currentView: ActivityView,
    isSidebarVisible: Boolean,
    onViewSelected: (ActivityView) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(48.dp)
            .fillMaxHeight()
            .background(VsCodeTheme.ActivityBarBackground),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Icon Actions
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            ActivityBarItem(
                icon = Icons.Default.Folder,
                contentDescription = "Explorer",
                isSelected = isSidebarVisible && currentView == ActivityView.EXPLORER,
                onClick = { onViewSelected(ActivityView.EXPLORER) }
            )
            ActivityBarItem(
                icon = Icons.Default.Search,
                contentDescription = "Search",
                isSelected = isSidebarVisible && currentView == ActivityView.SEARCH,
                onClick = { onViewSelected(ActivityView.SEARCH) }
            )
            ActivityBarItem(
                icon = Icons.Outlined.AltRoute,
                contentDescription = "Source Control",
                isSelected = isSidebarVisible && currentView == ActivityView.SOURCE_CONTROL,
                onClick = { onViewSelected(ActivityView.SOURCE_CONTROL) }
            )
            ActivityBarItem(
                icon = Icons.Default.BugReport,
                contentDescription = "Run and Debug",
                isSelected = isSidebarVisible && currentView == ActivityView.RUN_DEBUG,
                onClick = { onViewSelected(ActivityView.RUN_DEBUG) }
            )
            ActivityBarItem(
                icon = Icons.Default.Extension,
                contentDescription = "Extensions",
                isSelected = isSidebarVisible && currentView == ActivityView.EXTENSIONS,
                onClick = { onViewSelected(ActivityView.EXTENSIONS) }
            )
        }

        // Bottom Icon: Settings
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            ActivityBarItem(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                isSelected = isSidebarVisible && currentView == ActivityView.SETTINGS,
                onClick = { onViewSelected(ActivityView.SETTINGS) }
            )
        }
    }
}

@Composable
private fun ActivityBarItem(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Active indicator line on the left
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(VsCodeTheme.FocusBorder)
            )
        }

        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) VsCodeTheme.Foreground else VsCodeTheme.SecondaryForeground,
            modifier = Modifier.size(24.dp)
        )
    }
}

