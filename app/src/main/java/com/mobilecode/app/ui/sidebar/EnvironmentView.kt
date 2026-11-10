package com.mobilecode.app.ui.sidebar

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilecode.app.core.designsystem.VsCodeMonospaceFont
import com.mobilecode.app.core.designsystem.VsCodeTheme
import com.mobilecode.app.core.runtime.EnvironmentManager
import com.mobilecode.app.core.terminal.TerminalViewModel

@Composable
fun EnvironmentView(
    environmentManager: EnvironmentManager,
    terminalViewModel: TerminalViewModel? = null,
    modifier: Modifier = Modifier
) {
    val environments by environmentManager.environments.collectAsState()
    val activeEnvironment by environmentManager.activeEnvironment.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Node.js", "Modern JS", "Python", "Full Linux Container", "Installed")

    val filteredList = environments.filter { item ->
        val matchesCategory = when (selectedCategory) {
            "All" -> true
            "Installed" -> item.isInstalled
            "Node.js" -> item.category.contains("Node", ignoreCase = true)
            "Modern JS" -> item.category.contains("Modern", ignoreCase = true)
            "Python" -> item.category.contains("Python", ignoreCase = true)
            "Full Linux Container" -> item.category.contains("Linux", ignoreCase = true)
            else -> true
        }

        val matchesSearch = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.tag.contains(searchQuery, ignoreCase = true) ||
                item.version.contains(searchQuery, ignoreCase = true) ||
                item.description.contains(searchQuery, ignoreCase = true)

        matchesCategory && matchesSearch
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VsCodeTheme.SidebarBackground)
            .padding(10.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ENVIRONMENTS",
                    color = VsCodeTheme.Foreground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Docker Market & Runtime Versions",
                    color = VsCodeTheme.SecondaryForeground,
                    fontSize = 10.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF0E639C), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Docker Hub",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Active Environment Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B2C3F), RoundedCornerShape(4.dp))
                .border(0.5.dp, Color(0xFF007ACC), RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "●",
                        color = Color(0xFF4EC9B0),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "ACTIVE ENVIRONMENT",
                        color = Color(0xFF4EC9B0),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = activeEnvironment.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF094771), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = activeEnvironment.tag,
                            color = Color(0xFFE0EEFF),
                            fontSize = 9.sp,
                            fontFamily = VsCodeMonospaceFont
                        )
                    }
                    Text(
                        text = "• Version ${activeEnvironment.version} (${activeEnvironment.architecture})",
                        color = Color(0xFF9CDCFE),
                        fontSize = 9.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search Docker Hub images...",
                    color = VsCodeTheme.SecondaryForeground,
                    fontSize = 11.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = VsCodeTheme.SecondaryForeground,
                    modifier = Modifier.size(14.dp)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            textStyle = TextStyle(
                color = VsCodeTheme.Foreground,
                fontSize = 11.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = VsCodeTheme.InputBackground,
                unfocusedContainerColor = VsCodeTheme.InputBackground,
                focusedBorderColor = VsCodeTheme.FocusBorder,
                unfocusedBorderColor = VsCodeTheme.BorderColor
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            categories.forEach { cat ->
                val isSel = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .background(
                            if (isSel) Color(0xFF007ACC) else Color(0xFF2D2D2E),
                            RoundedCornerShape(3.dp)
                        )
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSel) Color.White else VsCodeTheme.Foreground,
                        fontSize = 10.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Environment Items List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredList, key = { it.id }) { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (item.isActive) Color(0xFF1E2836) else Color(0xFF252526),
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = if (item.isActive) 1.dp else 0.5.dp,
                            color = if (item.isActive) Color(0xFF007ACC) else VsCodeTheme.BorderColor,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp)
                ) {
                    Column {
                        // Header Row: Icon, Title, and Action Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.category.contains("Linux")) Icons.Default.Dns else Icons.Default.Layers,
                                    contentDescription = "Environment",
                                    tint = if (item.isActive) Color(0xFF4EC9B0) else Color(0xFF007ACC),
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = item.name,
                                        color = VsCodeTheme.Foreground,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF2D2D2E), RoundedCornerShape(2.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = item.tag,
                                                color = Color(0xFF9CDCFE),
                                                fontSize = 9.sp,
                                                fontFamily = VsCodeMonospaceFont
                                            )
                                        }
                                        Text(
                                            text = item.formattedSize,
                                            color = VsCodeTheme.SecondaryForeground,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }

                            // Action Area
                            when {
                                item.isActive -> {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF104A30), RoundedCornerShape(3.dp))
                                            .border(0.5.dp, Color(0xFF4EC9B0), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Active",
                                                tint = Color(0xFF4EC9B0),
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = "ACTIVE",
                                                color = Color(0xFF4EC9B0),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                item.isDownloading -> {
                                    Text(
                                        text = "Pulling...",
                                        color = Color(0xFF007ACC),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                item.isInstalled -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val activated = environmentManager.environments("activate", item.id)
                                                if (terminalViewModel != null) {
                                                    terminalViewModel.activeSession?.append("\n[Active environment switched to ${activated.name} (${activated.tag})]\n")
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E639C)),
                                            shape = RoundedCornerShape(3.dp),
                                            modifier = Modifier.height(24.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                text = "Activate",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                        }

                                        if (item.id != "node-20" && item.id != "node-18" && item.id != "python-311") {
                                            IconButton(
                                                onClick = {
                                                    environmentManager.environments("uninstall", item.id)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Uninstall",
                                                    tint = VsCodeTheme.SecondaryForeground,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                else -> {
                                    Button(
                                        onClick = {
                                            environmentManager.environments("download", item.id)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC)),
                                        shape = RoundedCornerShape(3.dp),
                                        modifier = Modifier.height(24.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = "Download",
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = "Pull Image",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(5.dp))

                        // Description
                        Text(
                            text = item.description,
                            color = VsCodeTheme.SecondaryForeground,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )

                        // Progress bar if downloading
                        if (item.isDownloading) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { item.downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = Color(0xFF007ACC),
                                trackColor = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.statusText.ifEmpty { "Downloading layers in background..." },
                                    color = Color(0xFF007ACC),
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = "Check notification",
                                    color = VsCodeTheme.SecondaryForeground,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
