package com.mobilecode.app.ui.statusbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilecode.app.core.designsystem.VsCodeTheme

@Composable
fun StatusBar(
    currentBranch: String,
    errorCount: Int,
    warningCount: Int,
    cursorLine: Int,
    cursorCol: Int,
    language: String,
    onBranchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(VsCodeTheme.StatusBarBackground)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Git Branch
            Row(
                modifier = Modifier.clickable(onClick = onBranchClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.AltRoute,
                    contentDescription = "Git Branch",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = currentBranch,
                    color = Color.White,
                    fontSize = 11.sp
                )
            }

            // Errors & Warnings
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = "Errors",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
                Text(text = "$errorCount", color = Color.White, fontSize = 11.sp)

                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warnings",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
                Text(text = "$warningCount", color = Color.White, fontSize = 11.sp)
            }
        }

        // Right Info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Ln $cursorLine, Col $cursorCol",
                color = Color.White,
                fontSize = 11.sp
            )
            Text(
                text = "Spaces: 2",
                color = Color.White,
                fontSize = 11.sp
            )
            Text(
                text = "UTF-8",
                color = Color.White,
                fontSize = 11.sp
            )
            Text(
                text = "LF",
                color = Color.White,
                fontSize = 11.sp
            )
            Text(
                text = language.replaceFirstChar { it.uppercase() },
                color = Color.White,
                fontSize = 11.sp
            )
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notifications",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

