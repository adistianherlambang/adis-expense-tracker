package com.mobilecode.app.ui.sidebar

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilecode.app.core.designsystem.VsCodeTheme
import com.mobilecode.app.core.filesystem.FileNode
import com.mobilecode.app.core.filesystem.WorkspaceManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ExplorerView(
    workspaceManager: WorkspaceManager,
    onFileSelected: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val fileTree by workspaceManager.fileTree.collectAsState()
    val workspaceDir by workspaceManager.currentWorkspaceDir.collectAsState()
    val scope = rememberCoroutineScope()

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var newFileName by remember { mutableStateOf("") }
    var renameText by remember { mutableStateOf("") }

    // Map to keep track of expanded folder paths
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VsCodeTheme.SidebarBackground)
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(35.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = workspaceDir.name.uppercase(),
                color = VsCodeTheme.Foreground,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = { showNewFileDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NoteAdd,
                        contentDescription = "New File",
                        tint = VsCodeTheme.Foreground,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { showNewFolderDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "New Folder",
                        tint = VsCodeTheme.Foreground,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { scope.launch { workspaceManager.refreshTree() } },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = VsCodeTheme.Foreground,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // File Tree List
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            val flattened = flattenTree(fileTree, expandedFolders)
            items(flattened) { node ->
                FileTreeItem(
                    node = node,
                    isExpanded = expandedFolders[node.file.absolutePath] ?: false,
                    onToggleExpand = {
                        val path = node.file.absolutePath
                        expandedFolders[path] = !(expandedFolders[path] ?: false)
                    },
                    onSelect = {
                        if (node.isDirectory) {
                            val path = node.file.absolutePath
                            expandedFolders[path] = !(expandedFolders[path] ?: false)
                        } else {
                            onFileSelected(node.file)
                        }
                    },
                    onRename = {
                        renameText = node.file.name
                        showRenameDialog = node.file
                    },
                    onDelete = {
                        scope.launch { workspaceManager.deleteFile(node.file) }
                    }
                )
            }
        }
    }

    // New File Dialog
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("New File") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("File name (e.g. app.js)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newFileName.isNotBlank()) {
                        scope.launch {
                            val created = workspaceManager.createFile(workspaceDir, newFileName.trim())
                            created?.let { onFileSelected(it) }
                            newFileName = ""
                            showNewFileDialog = false
                        }
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("Folder name (e.g. src)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newFileName.isNotBlank()) {
                        scope.launch {
                            workspaceManager.createDirectory(workspaceDir, newFileName.trim())
                            newFileName = ""
                            showNewFolderDialog = false
                        }
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Dialog
    showRenameDialog?.let { targetFile ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename ${targetFile.name}") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameText.isNotBlank()) {
                        scope.launch {
                            workspaceManager.renameFile(targetFile, renameText.trim())
                            showRenameDialog = null
                        }
                    }
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun flattenTree(
    nodes: List<FileNode>,
    expandedMap: Map<String, Boolean>
): List<FileNode> {
    val result = mutableListOf<FileNode>()
    for (node in nodes) {
        result.add(node)
        if (node.isDirectory && (expandedMap[node.file.absolutePath] == true)) {
            result.addAll(flattenTree(node.children, expandedMap))
        }
    }
    return result
}

@Composable
private fun FileTreeItem(
    node: FileNode,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clickable(onClick = onSelect)
            .padding(start = (node.level * 14 + 8).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.isDirectory) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = VsCodeTheme.SecondaryForeground,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onToggleExpand)
            )
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = VsCodeTheme.WarningColor,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(16.dp)
            )
        } else {
            Box(modifier = Modifier.size(16.dp))
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = VsCodeTheme.InfoColor,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(16.dp)
            )
        }

        Text(
            text = node.name,
            color = VsCodeTheme.Foreground,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        // Actions
        Row {
            IconButton(
                onClick = onRename,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename",
                    tint = VsCodeTheme.SecondaryForeground,
                    modifier = Modifier.size(12.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = VsCodeTheme.SecondaryForeground,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

