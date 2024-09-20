package com.freefjay.localshare.component

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import com.freefjay.localshare.TAG

class PopupState(
    private val flush: () -> Unit
) {
    var show: Boolean = false
        set(value) {
            field = value
            flush()
        }

    var offset: IntOffset = IntOffset.Zero
        set(value) {
            field = value
            flush()
        }
}

val LocalPopupState = compositionLocalOf<PopupState?> { null }

@Composable
fun PopupTrigger(
    modifier: Modifier = Modifier,
    popupContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val recompose = currentRecomposeScope
    val state = remember {
        Log.i(TAG, "remember测试")
        PopupState { recompose.invalidate() }
    }
    OnPageHide {
        state.show = false
    }
    CompositionLocalProvider(
        LocalPopupState provides state
    ) {
        Box(modifier = Modifier
            .onPopupOffset()
            .then(modifier)
            .clickable {
                state.show = true
            }) {
            content()
            Log.i(TAG, "PopupContainer: ${state.show}, ${state.offset}")
            if (state.show) {
                Popup(
                    onDismissRequest = {state.show = false},
                    offset = state.offset
                ) {
                    popupContent()
                }
            }
        }
    }
}

fun Modifier.onPopupOffset(block: (layoutCoordinates: LayoutCoordinates) -> IntOffset = {
    IntOffset(0, it.size.height)
}): Modifier = composed {
    val popupState = LocalPopupState.current
    this.onGloballyPositioned {
        val offset = block(it)
        if (offset != popupState?.offset) {
            Log.i(TAG, "onPopupOffset: 测试")
            popupState?.offset = offset
        }
    }
}