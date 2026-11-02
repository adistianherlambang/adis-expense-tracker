package com.mobilecode.app.core.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

@Composable
fun ProgrammingAccessoryBar(
    targetMode: String = "EDIT",
    onToggleTarget: () -> Unit = {},
    onKeyInserted: (key: String) -> Unit,
    onSpecialKey: (action: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableIntStateOf(0) }

    val pages = listOf(
        // Page 0: Modifiers & Navigation
        listOf("ESC", "TAB", "CTRL", "ALT", "SHIFT", "↑", "↓", "←", "→", "DEL"),
        // Page 1: Brackets
        listOf("{", "}", "[", "]", "(", ")", "<", ">", "\"", "'", "`"),
        // Page 2: Operators
        listOf("=", "+", "-", "*", "/", "%", "!", "&", "|", "^"),
        // Page 3: Punctuation & Symbols
        listOf(":", ";", ",", ".", "?", "~", "_", "\\", "$", "@", "#")
    )

    val currentKeys = pages[currentPage]

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(VsCodeTheme.TitleBarBackground)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Target Mode Switcher (EDIT vs TERM)
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .background(if (targetMode == "EDIT") VsCodeTheme.BadgeBackground else VsCodeTheme.WarningColor)
                .clickable(onClick = onToggleTarget)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = targetMode,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Page switch button
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .background(VsCodeTheme.HoverBackground)
                .clickable {
                    currentPage = (currentPage + 1) % pages.size
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "P${currentPage + 1}",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Keys scrollable row
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            currentKeys.forEach { key ->
                AccessoryKeyButton(
                    label = key,
                    onClick = {
                        when (key) {
                            "ESC" -> onSpecialKey("ESC")
                            "TAB" -> onSpecialKey("TAB")
                            "CTRL" -> onSpecialKey("CTRL")
                            "ALT" -> onSpecialKey("ALT")
                            "SHIFT" -> onSpecialKey("SHIFT")
                            "↑" -> onSpecialKey("UP")
                            "↓" -> onSpecialKey("DOWN")
                            "←" -> onSpecialKey("LEFT")
                            "→" -> onSpecialKey("RIGHT")
                            "DEL" -> onSpecialKey("DEL")
                            else -> onKeyInserted(key)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AccessoryKeyButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .background(VsCodeTheme.SidebarBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = VsCodeTheme.Foreground,
            fontFamily = VsCodeMonospaceFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
