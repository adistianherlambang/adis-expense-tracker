package com.mobilecode.app.ui.commandpalette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilecode.app.core.designsystem.VsCodeMonospaceFont
import com.mobilecode.app.core.designsystem.VsCodeTheme

data class PaletteCommand(
    val id: String,
    val title: String,
    val category: String,
    val shortcut: String? = null,
    val action: () -> Unit
)

@Composable
fun CommandPalette(
    commands: List<PaletteCommand>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val filteredCommands = remember(searchInput, commands) {
        val query = searchInput.trim().removePrefix(">").trim()
        if (query.isEmpty()) {
            commands
        } else {
            commands.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Modal Background Scrim
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopCenter
    ) {
        // Command Palette Window
        Column(
            modifier = Modifier
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .background(VsCodeTheme.SidebarBackground, RoundedCornerShape(4.dp))
                .border(1.dp, VsCodeTheme.FocusBorder, RoundedCornerShape(4.dp))
                .clickable(enabled = false) {}
                .padding(8.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                placeholder = { Text("> Type a command to run...", fontSize = 12.sp, color = VsCodeTheme.SecondaryForeground) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = VsCodeTheme.FocusBorder,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
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

            // Commands List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                items(filteredCommands) { cmd ->
                    CommandRow(
                        command = cmd,
                        onClick = {
                            cmd.action()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandRow(
    command: PaletteCommand,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${command.category}: ",
                color = VsCodeTheme.SecondaryForeground,
                fontSize = 12.sp
            )
            Text(
                text = command.title,
                color = VsCodeTheme.Foreground,
                fontSize = 12.sp
            )
        }

        command.shortcut?.let { shortcut ->
            Text(
                text = shortcut,
                color = VsCodeTheme.SecondaryForeground,
                fontSize = 11.sp,
                fontFamily = VsCodeMonospaceFont
            )
        }
    }
}

