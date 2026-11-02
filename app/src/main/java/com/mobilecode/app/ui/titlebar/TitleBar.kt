package com.mobilecode.app.ui.titlebar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilecode.app.core.designsystem.VsCodeTheme

@Composable
fun TitleBar(
    projectName: String,
    onOpenCommandPalette: () -> Unit,
    onToggleSidebar: () -> Unit,
    onToggleTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(35.dp)
            .background(VsCodeTheme.TitleBarBackground)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: App Logo & Menus
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = "Mobile Code Logo",
                tint = VsCodeTheme.BadgeBackground,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Mobile Code",
                color = VsCodeTheme.Foreground,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Center: VS Code Command Center Search Bar
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 16.dp)
                .height(24.dp)
                .background(VsCodeTheme.EditorBackground, RoundedCornerShape(4.dp))
                .border(1.dp, VsCodeTheme.BorderColor, RoundedCornerShape(4.dp))
                .clickable(onClick = onOpenCommandPalette)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = VsCodeTheme.SecondaryForeground,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "$projectName (Ctrl+Shift+P)",
                    color = VsCodeTheme.SecondaryForeground,
                    fontSize = 11.sp
                )
            }
        }

        // Right: Window actions & view toggles
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onToggleSidebar,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ViewSidebar,
                    contentDescription = "Toggle Sidebar",
                    tint = VsCodeTheme.Foreground,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onToggleTerminal,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Terminal,
                    contentDescription = "Toggle Terminal",
                    tint = VsCodeTheme.Foreground,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

