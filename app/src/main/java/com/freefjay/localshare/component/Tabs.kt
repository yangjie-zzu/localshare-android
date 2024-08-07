package com.freefjay.localshare.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun tabState(
    initialKey: String,
): MutableState<TabState> {

    var activeKey by remember {
        mutableStateOf(initialKey)
    }

    val tabState = remember(activeKey) {
        mutableStateOf(TabState(activeKey) {
            activeKey = it
        })
    }
    return tabState
}

class TabState(
    val activeKey: String,
    val onChange: (activeKey: String) -> Unit
)