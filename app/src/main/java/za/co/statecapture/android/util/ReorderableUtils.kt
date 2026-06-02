package za.co.statecapture.android.util

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun rememberReorderState(
    lazyListState: LazyListState,
    onReorder: (Int, Int) -> Unit
): ReorderState {
    return remember(lazyListState) { ReorderState(lazyListState, onReorder) }
}

class ReorderState(
    val lazyListState: LazyListState,
    val onReorder: (Int, Int) -> Unit
) {
    var draggedIndex by mutableStateOf<Int?>(null)
    var dragOffset by mutableStateOf(0f)

    fun onDragStart(offset: Offset) {
        lazyListState.layoutInfo.visibleItemsInfo
            .find { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
            ?.let { item ->
                draggedIndex = item.index
            }
    }

    fun onDrag(dragAmount: Offset) {
        dragOffset += dragAmount.y
        
        val draggedItem = draggedIndex?.let { index ->
            lazyListState.layoutInfo.visibleItemsInfo.find { it.index == index }
        } ?: return

        val currentOffset = draggedItem.offset + dragOffset
        
        val targetItem = lazyListState.layoutInfo.visibleItemsInfo.find { item ->
            item.index != draggedItem.index &&
            if (dragOffset > 0) { // Dragging down
                currentOffset + draggedItem.size > item.offset + item.size / 2
            } else { // Dragging up
                currentOffset < item.offset + item.size / 2
            }
        }

        if (targetItem != null) {
            val fromIndex = draggedItem.index
            val toIndex = targetItem.index
            
            onReorder(fromIndex, toIndex)
            draggedIndex = toIndex
            // Adjust offset to compensate for the item swap
            dragOffset -= (targetItem.offset - draggedItem.offset)
        }
    }

    fun onDragEnd() {
        draggedIndex = null
        dragOffset = 0f
    }
}

fun Modifier.reorderable(state: ReorderState): Modifier = this.pointerInput(Unit) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> state.onDragStart(offset) },
        onDrag = { change, dragAmount -> 
            change.consume()
            state.onDrag(dragAmount) 
        },
        onDragEnd = { state.onDragEnd() },
        onDragCancel = { state.onDragEnd() }
    )
}
