package com.mobilecode.app.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
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
import com.mobilecode.app.core.search.SearchViewModel
import java.io.File

@Composable
fun SearchView(
    searchViewModel: SearchViewModel,
    onFileSelected: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val query by searchViewModel.query.collectAsState()
    val replaceText by searchViewModel.replaceText.collectAsState()
    val matchCase by searchViewModel.matchCase.collectAsState()
    val matchWholeWord by searchViewModel.matchWholeWord.collectAsState()
    val useRegex by searchViewModel.useRegex.collectAsState()
    val results by searchViewModel.results.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()

    val totalMatches = results.sumOf { it.matches.size }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VsCodeTheme.SidebarBackground)
            .padding(8.dp)
    ) {
        Text(
            text = "SEARCH",
            color = VsCodeTheme.Foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Search Input with Toggles
        OutlinedTextField(
            value = query,
            onValueChange = { searchViewModel.setQuery(it) },
            placeholder = { Text("Search", fontSize = 12.sp, color = VsCodeTheme.SecondaryForeground) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                Row(modifier = Modifier.padding(end = 4.dp)) {
                    FilterToggle(
                        label = "Aa",
                        isActive = matchCase,
                        onClick = { searchViewModel.toggleMatchCase() }
                    )
                    FilterToggle(
                        label = "\\b",
                        isActive = matchWholeWord,
                        onClick = { searchViewModel.toggleMatchWholeWord() }
                    )
                    FilterToggle(
                        label = ".*",
                        isActive = useRegex,
                        onClick = { searchViewModel.toggleUseRegex() }
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = VsCodeTheme.EditorBackground,
                unfocusedContainerColor = VsCodeTheme.EditorBackground,
                focusedBorderColor = VsCodeTheme.FocusBorder,
                unfocusedBorderColor = VsCodeTheme.BorderColor,
                focusedTextColor = VsCodeTheme.Foreground,
                unfocusedTextColor = VsCodeTheme.Foreground
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Replace Input
        OutlinedTextField(
            value = replaceText,
            onValueChange = { searchViewModel.setReplaceText(it) },
            placeholder = { Text("Replace", fontSize = 12.sp, color = VsCodeTheme.SecondaryForeground) },
            modifier = Modifier.fillMaxWidth(),
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

        Spacer(modifier = Modifier.height(8.dp))

        // Results Summary
        Text(
            text = if (isSearching) "Searching..." else "$totalMatches results across ${results.size} files",
            color = VsCodeTheme.SecondaryForeground,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Results List
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            results.forEach { result ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onFileSelected(result.file) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = VsCodeTheme.InfoColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = result.file.name,
                            color = VsCodeTheme.Foreground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                        Text(
                            text = " (${result.matches.size})",
                            color = VsCodeTheme.SecondaryForeground,
                            fontSize = 11.sp
                        )
                    }
                }

                items(result.matches) { match ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                            .clickable { onFileSelected(match.file) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${match.lineNumber}: ",
                            color = VsCodeTheme.SecondaryForeground,
                            fontSize = 11.sp,
                            fontFamily = VsCodeMonospaceFont
                        )
                        Text(
                            text = match.lineContent,
                            color = VsCodeTheme.Foreground,
                            fontSize = 11.sp,
                            fontFamily = VsCodeMonospaceFont,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterToggle(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(24.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) VsCodeTheme.FocusBorder else VsCodeTheme.SecondaryForeground
        )
    }
}

