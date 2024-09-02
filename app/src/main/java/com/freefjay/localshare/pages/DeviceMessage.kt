package com.freefjay.localshare.pages

import OnEvent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import com.freefjay.localshare.R
import com.freefjay.localshare.TAG
import com.freefjay.localshare.clientCode
import com.freefjay.localshare.component.AndroidTextView
import com.freefjay.localshare.component.Page
import com.freefjay.localshare.component.Title
import com.freefjay.localshare.globalActivity
import com.freefjay.localshare.httpClient
import com.freefjay.localshare.model.Device
import com.freefjay.localshare.model.DeviceMessage
import com.freefjay.localshare.model.DeviceMessageParams
import com.freefjay.localshare.util.FileInfo
import com.freefjay.localshare.util.delete
import com.freefjay.localshare.util.getFileInfo
import com.freefjay.localshare.util.longClick
import com.freefjay.localshare.util.queryList
import com.freefjay.localshare.util.queryOne
import com.freefjay.localshare.util.readableFileSize
import com.freefjay.localshare.util.save
import com.google.gson.Gson
import deviceMessageEvent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

lateinit var filePickerLauncher: ActivityResultLauncher<Array<String>>

var onFilePicker: ((uri: Uri?) -> Unit)? = null

fun registerFilePickerLauncher() {
    filePickerLauncher = globalActivity.registerForActivityResult(ActivityResultContracts.OpenDocument()
    ) {
        onFilePicker?.invoke(it)
        onFilePicker = null
    }
}

fun openFilePicker(mimeTypes: Array<String>,callback: ((uri: Uri?) -> Unit)?) {
    onFilePicker = callback
    filePickerLauncher.launch(mimeTypes)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceMessageView(
    deviceId: Long?
) {
    val currentCoroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val deviceMessages = remember {
        mutableStateListOf<DeviceMessage>()
    }

    var device by remember {
        mutableStateOf<Device?>(null)
    }

    suspend fun queryDevice(deviceId: Long?) {
        device = queryOne("select * from device where id = ${deviceId}")
    }

    suspend fun queryMessage(deviceId: Long?, scrollToBottom: Boolean = true) {
        Log.i(TAG, "deviceId: ${deviceId}")
        val list =
            if (deviceId == null) null else queryList<DeviceMessage>("select * from device_message where device_id = $deviceId")
        deviceMessages.clear()
        if (list != null) {
            deviceMessages.addAll(list)
        }
        if (scrollToBottom) {
            currentCoroutineScope.launch {
                listState.scrollToItem(deviceMessages.size)
            }
        }
        Log.i(TAG, "deviceMessages: ${deviceMessages.size}")
    }

    LaunchedEffect(deviceId, block = {
        queryDevice(deviceId)
        queryMessage(deviceId)
    })

    OnEvent(event = deviceMessageEvent) {
        Log.i(TAG, "DeviceMessageView: 消息事件")
        CoroutineScope(Dispatchers.Default).launch {
            Log.i(TAG, "DeviceMessageView: 事件测试")
            queryMessage(deviceId)
        }
    }

    Page(
        title = {
            Title {
                Text(text = "消息")
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = {
                    itemsIndexed(items = deviceMessages, key = { _, it -> it.id ?: "" }) { _, it ->
                        var offsetX by remember {
                            mutableStateOf(0)
                        }
                        var offsetY by remember {
                            mutableStateOf(0)
                        }
                        var show by remember {
                            mutableStateOf(false)
                        }
                        Box {
                            if (it.type == "receive") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Green)
                                        .longClick {
//                                            show = true
                                        }
                                        .onGloballyPositioned {
                                            offsetY = it.size.height
                                        }
                                        .padding(5.dp)
                                ) {
                                    Column {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            AndroidTextView(text = buildSpannedString {
                                                if (it.filename != null) {
                                                    this.bold {
                                                        append(it.filename)
                                                    }
                                                }
                                                if (it.size != null) {
                                                    append(" ${readableFileSize(it.size)}")
                                                }
                                            })
                                        }
                                        AndroidTextView(text = it.content)
                                    }
                                }
                            }
                            if (it.type == "send") {
                                Row {
                                    SelectionContainer {
                                        Column {
                                            Text(text = it.filename ?: "")
                                            Text(text = it.content ?: "")
                                        }
                                    }
                                }
                            }
                            if (show) {
                                Popup(
                                    onDismissRequest = { show = false },
                                    offset = IntOffset(offsetX, offsetY)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .background(color = Color.White)
                                            .border(
                                                border = BorderStroke(
                                                    width = 1.dp,
                                                    color = Color(0, 0, 0, 20)
                                                ),
                                                shape = RoundedCornerShape(5.dp)
                                            )
                                            .padding(5.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.clickable {
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    delete<DeviceMessage>(it.id)
                                                    queryMessage(device?.id, false)
                                                }
                                            }
                                        ) {
                                            Text("删除")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
            Column(
                modifier = Modifier.padding(bottom = 5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                var fileInfo by remember {
                    mutableStateOf<FileInfo?>(null)
                }
                var content by remember {
                    mutableStateOf<String?>(null)
                }

                LaunchedEffect(deviceId) {
                    fileInfo = null
                    content = null
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        Button(
                            onClick = {
                                openFilePicker(arrayOf("*/*")) {
                                    fileInfo = getFileInfo(it)
                                }
                            }
                        ) {
                            Text(text = fileInfo?.let { it.name ?: "" } ?:"选择文件")
                        }
                    }
                    if (fileInfo != null) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .padding(5.dp)
                                .clickable {
                                    fileInfo = null
                                }
                        ) {
                            Image(
                                painter = painterResource(R.drawable.del_file),
                                contentDescription = null
                            )
                        }
                    }
                }
                fun sendMsg() {
                    CoroutineScope(Dispatchers.IO).launch {
                        val deviceMessage = DeviceMessage(
                            type = "send",
                            content = content,
                            filepath = fileInfo?.path,
                            filename = fileInfo?.name,
                            size = fileInfo?.size?.toLong(),
                            deviceId = device?.id,
                            createdTime = Date()
                        )
                        save(deviceMessage)
                        val response = httpClient.post("http://${device?.ip}:${device?.port}/message") {
                            setBody(Gson().toJson(DeviceMessageParams(
                                sendId = deviceMessage.id,
                                clientCode = clientCode,
                                content = deviceMessage.content,
                                filename = deviceMessage.filename,
                                size = deviceMessage.size
                            )
                            ))
                            contentType(ContentType.Application.Json)
                        }
                        if (response.status == HttpStatusCode.OK) {
                            val body = response.bodyAsText()
                            deviceMessage.sendSuccess = true
                            save(deviceMessage)
                        }
                        queryMessage(device?.id)
                    }
                }
                TextField(
                    value = content ?: "",
                    onValueChange = {content = it}, placeholder = { Text(text = "输入要发送的文字") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        sendMsg()
                    }
                ) {
                    Text(text = "发送${if (fileInfo != null) "文件" else if (content?.isNotEmpty() == true) "文字" else ""}")
                }
            }
        }
    }

}