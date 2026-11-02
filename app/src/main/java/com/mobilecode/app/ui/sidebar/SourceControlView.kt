package com.mobilecode.app.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilecode.app.core.designsystem.VsCodeMonospaceFont
import com.mobilecode.app.core.designsystem.VsCodeTheme
import com.mobilecode.app.core.git.GitChangedFile
import com.mobilecode.app.core.git.GitFileStatus
import com.mobilecode.app.core.git.GitViewModel

@Composable
fun SourceControlView(
    gitViewModel: GitViewModel,
    modifier: Modifier = Modifier
) {
    val changes by gitViewModel.changes.collectAsState()
    val commitMessage by gitViewModel.commitMessage.collectAsState()
    val currentBranch by gitViewModel.currentBranch.collectAsState()
    val isRefreshing by gitViewModel.isRefreshing.collectAsState()

    val stagedChanges = changes.filter { it.isStaged }
    val unstagedChanges = changes.filter { !it.isStaged }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VsCodeTheme.SidebarBackground)
            .padding(8.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SOURCE CONTROL: $currentBranch",
                color = VsCodeTheme.Foreground,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = { gitViewModel.refresh() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Git",
                    tint = VsCodeTheme.Foreground,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Commit Message Input Box
        OutlinedTextField(
            value = commitMessage,
            onValueChange = { gitViewModel.setCommitMessage(it) },
            placeholder = { Text("Message (Ctrl+Enter to commit)", fontSize = 12.sp, color = VsCodeTheme.SecondaryForeground) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = VsCodeTheme.EditorBackground,
                unfocusedContainerColor = VsCodeTheme.EditorBackground,
                focusedBorderColor = VsCodeTheme.FocusBorder,
                unfocusedBorderColor = VsCodeTheme.BorderColor,
                focusedTextColor = VsCodeTheme.Foreground,
                unfocusedTextColor = VsCodeTheme.Foreground
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Commit Button
        Button(
            onClick = { gitViewModel.commit() },
            enabled = commitMessage.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VsCodeTheme.BadgeBackground,
                disabledContainerColor = VsCodeTheme.TitleBarBackground
            )
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Commit", fontSize = 12.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Changes List
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (stagedChanges.isNotEmpty()) {
                item {
                    SectionHeader("STAGED CHANGES (${stagedChanges.size})")
                }
                items(stagedChanges) { file ->
                    GitFileRow(
                        file = file,
                        onAction = { gitViewModel.unstageFile(file) },
                        actionIcon = Icons.Default.Remove,
                        actionDescription = "Unstage"
                    )
                }
            }

            item {
                SectionHeader("CHANGES (${unstagedChanges.size})")
            }

            if (unstagedChanges.isEmpty() && stagedChanges.isEmpty()) {
                item {
                    Text(
                        text = if (isRefreshing) "Refreshing repository..." else "No changes detected.",
                        color = VsCodeTheme.SecondaryForeground,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(unstagedChanges) { file ->
                    GitFileRow(
                        file = file,
                        onAction = { gitViewModel.stageFile(file) },
                        actionIcon = Icons.Default.Add,
                        actionDescription = "Stage"
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = VsCodeTheme.SecondaryForeground,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun GitFileRow(
    file: GitChangedFile,
    onAction: () -> Unit,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionDescription: String
) {
    val statusColor = when (file.status) {
        GitFileStatus.MODIFIED -> VsCodeTheme.GitModified
        GitFileStatus.ADDED -> VsCodeTheme.GitAdded
        GitFileStatus.DELETED -> VsCodeTheme.GitDeleted
        GitFileStatus.UNTRACKED -> VsCodeTheme.GitUntracked
        GitFileStatus.RENAMED -> VsCodeTheme.GitModified
        GitFileStatus.CONFLICT -> VsCodeTheme.ErrorColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clickable(onClick = onAction),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = file.path,
                color = VsCodeTheme.Foreground,
                fontSize = 12.sp,
                maxLines = 1,
                fontFamily = VsCodeMonospaceFont
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = file.statusBadge,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            IconButton(
                onClick = onAction,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = actionDescription,
                    tint = VsCodeTheme.SecondaryForeground,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

