package com.freefjay.localshare.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun tabState(
    initialKey: String,
): TabState {

    var activeKey by remember {
        mutableStateOf(initialKey)
    }

    val recomposeScope = currentRecomposeScope
    return remember {
        TabState(initialKey) {
            recomposeScope.invalidate()
        }
    }
}

class TabState(
    initialKey: String,
    val flush: () -> Unit,
) {
    var activeKey: String = initialKey
        set(value) {
            field = value
            flush()
        }
}