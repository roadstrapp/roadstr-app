package app.roadstr.core.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** How far the sheet must be pulled down before letting go closes it. */
private val DismissDistance = 120.dp

/**
 * Drag-down-to-close for a bottom sheet: [sheet] moves the whole sheet with the
 * finger and [handle] is the grab area that drives it, so the sheet's own
 * scrolling content keeps its vertical gestures.
 */
@Stable
class SheetDragState internal constructor(private val onDismiss: () -> Unit) {
    private var offset by mutableFloatStateOf(0f)

    val sheet: Modifier = Modifier.offset { IntOffset(0, offset.roundToInt()) }

    val handle: Modifier = Modifier.pointerInput(Unit) {
        val dismissPx = DismissDistance.toPx()
        detectVerticalDragGestures(
            onVerticalDrag = { change, amount ->
                change.consume()
                offset = (offset + amount).coerceAtLeast(0f)
            },
            onDragEnd = {
                val dismissed = offset >= dismissPx
                offset = 0f
                if (dismissed) onDismiss()
            },
            onDragCancel = { offset = 0f },
        )
    }
}

@Composable
fun rememberSheetDragState(onDismiss: () -> Unit): SheetDragState {
    val latest by rememberUpdatedState(onDismiss)
    return remember { SheetDragState { latest() } }
}

/** The horizontal grab line shown at the top of a bottom sheet. */
@Composable
fun SheetGrabHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(width = 40.dp, height = 4.dp),
            color = MaterialTheme.colorScheme.outline,
            shape = RoundedCornerShape(2.dp),
        ) {}
    }
}
