package com.mobilecode.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobilecode.app.core.designsystem.VsCodeTheme

/**
 * Draggable vertical splitter bar for adjusting the width between adjacent panels
 * (e.g., between Sidebar and Editor, or between two side-by-side Split Terminals).
 */
@Composable
fun VerticalResizeHandle(
    onDeltaX: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    handleWidth: Dp = 6.dp
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(handleWidth)
            .fillMaxHeight()
            .background(if (isDragging) VsCodeTheme.FocusBorder else Color(0xFF282828))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val deltaDp = with(density) { dragAmount.x.toDp() }
                        onDeltaX(deltaDp)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Subtle center grip indicator
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(30.dp)
                .background(
                    if (isDragging) Color.White else Color(0xFF4A4A4A),
                    RoundedCornerShape(1.dp)
                )
        )
    }
}

/**
 * Draggable horizontal splitter bar for adjusting the height between adjacent panels
 * (e.g., between Editor and Bottom Panel / Terminal).
 */
@Composable
fun HorizontalResizeHandle(
    onDeltaY: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    handleHeight: Dp = 6.dp
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(handleHeight)
            .background(if (isDragging) VsCodeTheme.FocusBorder else Color(0xFF282828))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val deltaDp = with(density) { dragAmount.y.toDp() }
                        onDeltaY(deltaDp)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Subtle center grip indicator
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(2.dp)
                .background(
                    if (isDragging) Color.White else Color(0xFF4A4A4A),
                    RoundedCornerShape(1.dp)
                )
        )
    }
}
