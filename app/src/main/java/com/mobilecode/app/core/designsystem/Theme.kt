package com.mobilecode.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = VsCodeTheme.FocusBorder,
    onPrimary = VsCodeTheme.Foreground,
    secondary = VsCodeTheme.BadgeBackground,
    onSecondary = VsCodeTheme.Foreground,
    background = VsCodeTheme.EditorBackground,
    onBackground = VsCodeTheme.Foreground,
    surface = VsCodeTheme.SidebarBackground,
    onSurface = VsCodeTheme.Foreground,
    surfaceVariant = VsCodeTheme.ActivityBarBackground,
    onSurfaceVariant = VsCodeTheme.SecondaryForeground,
    outline = VsCodeTheme.BorderColor,
    error = VsCodeTheme.ErrorColor,
    onError = VsCodeTheme.Foreground
)

// Sharp corners matching VS Code Desktop desktop widgets
val VsCodeShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp)
)

@Composable
fun MobileCodeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = VsCodeTypography,
        shapes = VsCodeShapes,
        content = content
    )
}

