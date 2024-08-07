package com.freefjay.localshare.component

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.freefjay.localshare.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DropdownMenu(
    trigger: @Composable () -> Unit,
    content: @Composable (close: () -> Unit) -> Unit
) {

    var show by remember {
        mutableStateOf(false)
    }

    var triggerHeight by remember {
        mutableStateOf(0)
    }

    Log.i(TAG, "DropdownMenu show: $show")
    Column {
        Box(
            modifier = Modifier
                .clickable {
                    Log.i(TAG, "DropdownMenu: clickable")
                    show = !show
                }
                .onGloballyPositioned {
                    triggerHeight = it.size.height
                }
        ) {
            trigger()
        }
        if (show) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(x = -10, y = triggerHeight),
                onDismissRequest = {
                    CoroutineScope(Dispatchers.Default).launch {
                        delay(20)
                        Log.i(TAG, "DropdownMenu: onDismissRequest")
                        show = false
                    }
                }
            ) {
                Box{
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            content { show = false }
                        }
                    }
                }
            }
        }
    }
}