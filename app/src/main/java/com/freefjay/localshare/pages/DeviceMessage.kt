package com.freefjay.localshare.pages

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.freefjay.localshare.TAG
import com.freefjay.localshare.component.Page
import com.freefjay.localshare.component.Title
import com.freefjay.localshare.model.DeviceMessage
import com.freefjay.localshare.util.queryList
import com.google.gson.Gson
import deviceMessageEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import onEvent

@Composable
fun DeviceMessageView(
    deviceId: Long?
) {
    val deviceMessages = remember {
        mutableStateListOf<DeviceMessage>()
    }

    suspend fun queryMessage(deviceId: Long?) {
        Log.i(TAG, "queryMessage: ${deviceId}")
        val list = if (deviceId == null) null else queryList<DeviceMessage>("select * from device_message where device_id = $deviceId")
        deviceMessages.clear()
        if (list != null) {
            deviceMessages.addAll(list)
        }
        Log.i(TAG, "queryMessage: ${Gson().toJson(deviceMessages)}")
    }

    LaunchedEffect(deviceId, block = {
        queryMessage(deviceId)
    })

    onEvent(event = deviceMessageEvent) {
        Log.i(TAG, "DeviceMessageView: 消息事件")
        CoroutineScope(Dispatchers.Default).launch {
            queryMessage(deviceId)
        }
    }

    Page(
        title = {
            Title {
                Text(text = "消息记录")
            }
        }
    ) {
        LazyColumn(content = {
            itemsIndexed(items = deviceMessages, key = {_, it -> it.id ?: ""}) {_, it ->
                if (it.type == "receive") {
                    Column {
                        Text(text = "收")
                        SelectionContainer {
                            Column {
                                Text(text = it.filepath ?: "")
                                Text(text = it.content ?: "")
                            }
                        }
                    }
                }
                if (it.type == "send") {
                    Row {
                        SelectionContainer {
                            Column {
                                Text(text = it.filepath ?: "")
                                Text(text = it.content ?: "")
                            }
                        }
                        Text(text = "发")
                    }
                }
            }
        })
    }

}