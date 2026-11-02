package com.mobilecode.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilecode.app.core.designsystem.VsCodeMonospaceFont
import com.mobilecode.app.core.designsystem.VsCodeTheme
import com.mobilecode.app.core.editor.EditorTab
import com.mobilecode.app.core.editor.EditorViewModel
import com.mobilecode.app.core.editor.MonacoWebView

@Composable
fun EditorArea(
    editorViewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val tabs by editorViewModel.tabs.collectAsState()
    val activeTabIndex by editorViewModel.activeTabIndex.collectAsState()
    val activeTab = editorViewModel.activeTab

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VsCodeTheme.EditorBackground)
    ) {
        // Tab Bar
        if (tabs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(35.dp)
                    .background(VsCodeTheme.SidebarBackground)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    EditorTabItem(
                        tab = tab,
                        isActive = index == activeTabIndex,
                        onSelect = { editorViewModel.selectTab(index) },
                        onClose = { editorViewModel.closeTab(index) }
                    )
                }
            }

            // Breadcrumbs Navigation Bar
            activeTab?.let { tab ->
                BreadcrumbsBar(tab = tab)
            }
        }

        // Monaco Editor Surface
        MonacoWebView(
            activeTab = activeTab,
            onContentChanged = { fileId, content, isDirty ->
                editorViewModel.onContentChanged(fileId, content, isDirty)
            },
            onCursorPosition = { line, col ->
                editorViewModel.updateCursor(line, col)
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EditorTabItem(
    tab: EditorTab,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val backgroundColor = if (isActive) VsCodeTheme.ActiveTabBackground else VsCodeTheme.InactiveTabBackground

    Row(
        modifier = Modifier
            .height(35.dp)
            .background(backgroundColor)
            .border(width = 0.5.dp, color = VsCodeTheme.BorderColor)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = VsCodeTheme.InfoColor,
            modifier = Modifier.size(14.dp)
        )

        Text(
            text = tab.title,
            color = if (isActive) VsCodeTheme.Foreground else VsCodeTheme.SecondaryForeground,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
        )

        // Dirty Indicator dot or Close button
        if (tab.isDirty) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(VsCodeTheme.Foreground, CircleShape)
            )
        } else {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Tab",
                    tint = VsCodeTheme.SecondaryForeground,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun BreadcrumbsBar(tab: EditorTab) {
    val parts = tab.file.absolutePath.split("/").filter { it.isNotBlank() }
    val relevantParts = parts.takeLast(3)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(VsCodeTheme.EditorBackground)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        relevantParts.forEachIndexed { index, part ->
            Text(
                text = part,
                color = if (index == relevantParts.size - 1) VsCodeTheme.Foreground else VsCodeTheme.SecondaryForeground,
                fontSize = 11.sp,
                fontFamily = VsCodeMonospaceFont
            )
            if (index < relevantParts.size - 1) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = VsCodeTheme.SecondaryForeground,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

