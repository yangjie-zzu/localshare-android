package com.freefjay.localshare.pages

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
import com.freefjay.localshare.component.Page
import com.freefjay.localshare.model.DeviceMessage
import com.freefjay.localshare.util.queryList

@Composable
fun DeviceMessageView(
    deviceId: Long
) {
    val deviceMessages = remember {
        mutableStateListOf<DeviceMessage>()
    }

    suspend fun queryMessage(deviceId: Long) {
        val list = queryList<DeviceMessage>("select * from device_message where client_id = $deviceId")
        deviceMessages.clear()
        deviceMessages.addAll(list)
    }

    LaunchedEffect(deviceId, block = {
        queryMessage(deviceId)
    })

    Page(
        title = {
            Text(text = "消息记录")
        }
    ) {
        LazyColumn(content = {
            itemsIndexed(items = deviceMessages, key = {_, it -> it.id ?: ""}) {_, it ->
                if (it.type == "receive") {
                    Row {
                        Text(text = "收")
                        Column {
                            SelectionContainer {
                                Text(text = "${it.filename}")
                                Text(text = "${it.content}")
                            }
                        }
                    }
                }
                if (it.type == "send") {
                    Row {
                        Column {
                            SelectionContainer {
                                Text(text = "${it.filename}")
                                Text(text = "${it.content}")
                            }
                        }
                        Text(text = "发")
                    }
                }
            }
        })
    }

}