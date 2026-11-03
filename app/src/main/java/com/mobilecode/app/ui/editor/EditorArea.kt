package com.mobilecode.app.ui.editor

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mobilecode.app.core.editor.EditorTab
import com.mobilecode.app.core.editor.EditorViewModel
import com.mobilecode.app.core.editor.MonacoWebView

@Composable
fun EditorArea(
    editorViewModel: EditorViewModel,
    onWebViewReady: ((WebView) -> Unit)? = null,
    onOpenFolderRequested: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tabs by editorViewModel.tabs.collectAsState()
    val activeTabIndex by editorViewModel.activeTabIndex.collectAsState()
    val activeTab = tabs.getOrNull(activeTabIndex)

    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("main.js") }

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
                    .background(VsCodeTheme.SidebarBackground),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
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

                // Quick + button to add a new file tab anytime
                IconButton(
                    onClick = {
                        newFileName = "script-${tabs.size + 1}.js"
                        showNewFileDialog = true
                    },
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New File",
                        tint = VsCodeTheme.Foreground,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Breadcrumbs Navigation Bar
            activeTab?.let { tab ->
                BreadcrumbsBar(tab = tab)
            }
        }

        // Empty state vs Native Code Editor Surface
        if (tabs.isEmpty() || activeTab == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VsCodeTheme.EditorBackground),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = VsCodeTheme.BadgeBackground.copy(alpha = 0.85f),
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Visual Studio Code",
                        color = VsCodeTheme.Foreground,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Start editing by creating a file or opening a project folder",
                        color = VsCodeTheme.SecondaryForeground,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Interactive Quick-Start Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                newFileName = "main.js"
                                showNewFileDialog = true
                            },
                            shape = RoundedCornerShape(3.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VsCodeTheme.BadgeBackground),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New File", fontSize = 12.sp, color = Color.White)
                        }

                        Button(
                            onClick = { onOpenFolderRequested?.invoke() },
                            shape = RoundedCornerShape(3.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VsCodeTheme.HoverBackground),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = VsCodeTheme.Foreground,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Folder", fontSize = 12.sp, color = VsCodeTheme.Foreground)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ShortcutHintRow("Show All Commands", "Ctrl + Shift + P")
                    ShortcutHintRow("Toggle Terminal", "Ctrl + `")
                    ShortcutHintRow("Save Active File", "Ctrl + S")
                }
            }
        } else {
            MonacoWebView(
                activeTab = activeTab,
                editorCommands = editorViewModel.editorCommands,
                onContentChanged = { fileId, content, isDirty ->
                    editorViewModel.onContentChanged(fileId, content, isDirty)
                },
                onCursorPosition = { line, col ->
                    editorViewModel.updateCursor(line, col)
                },
                onSaveFile = {
                    editorViewModel.saveActiveFile()
                },
                onWebViewReady = onWebViewReady,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create New File") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("File name (e.g. main.js, app.py, index.html)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newFileName.isNotBlank()) {
                        editorViewModel.createQuickFile(newFileName.trim(), "")
                        showNewFileDialog = false
                    }
                }) {
                    Text("Create & Edit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ShortcutHintRow(action: String, shortcut: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = action,
            color = VsCodeTheme.SecondaryForeground,
            fontSize = 11.sp
        )
        Box(
            modifier = Modifier
                .background(VsCodeTheme.SidebarBackground)
                .border(1.dp, VsCodeTheme.BorderColor)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = shortcut,
                color = VsCodeTheme.Foreground,
                fontSize = 10.sp,
                fontFamily = VsCodeMonospaceFont
            )
        }
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
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )

        if (tab.isDirty) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(VsCodeTheme.WarningColor, CircleShape)
            )
        }

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
