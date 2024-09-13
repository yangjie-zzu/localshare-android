package com.freefjay.localshare.pages

import OnEvent
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.freefjay.localshare.FileProgress
import com.freefjay.localshare.R
import com.freefjay.localshare.TAG
import com.freefjay.localshare.clientCode
import com.freefjay.localshare.component.Page
import com.freefjay.localshare.component.Route
import com.freefjay.localshare.component.Title
import com.freefjay.localshare.globalActivity
import com.freefjay.localshare.globalRouter
import com.freefjay.localshare.httpClient
import com.freefjay.localshare.model.Device
import com.freefjay.localshare.model.DeviceMessage
import com.freefjay.localshare.model.DeviceMessageParams
import com.freefjay.localshare.util.FileInfo
import com.freefjay.localshare.util.OnTimer
import com.freefjay.localshare.util.delete
import com.freefjay.localshare.util.downloadMessageFile
import com.freefjay.localshare.util.fileProgresses
import com.freefjay.localshare.util.format
import com.freefjay.localshare.util.friendly
import com.freefjay.localshare.util.getFileInfo
import com.freefjay.localshare.util.getFileNameAndType
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Timer
import java.util.TimerTask

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
    var deviceMessages by remember {
        mutableStateOf<List<DeviceMessage>>(listOf())
    }

    var device by remember {
        mutableStateOf<Device?>(null)
    }

    var fileProgressMap by remember {
        mutableStateOf<Map<Long?, FileProgress?>?>(null)
    }

    suspend fun queryDevice(deviceId: Long?) {
        device = queryOne("select * from device where id = ${deviceId}")
    }

    suspend fun queryMessage(deviceId: Long?, scrollToBottom: Boolean = true) {
        Log.i(TAG, "deviceId: ${deviceId}")
        val oldSize = deviceMessages.size
        val list =
            if (deviceId == null) null else queryList<DeviceMessage>("select * from device_message where device_id = $deviceId")
        deviceMessages = list ?: listOf()
        fileProgressMap = null
        if (scrollToBottom) {
            val size = deviceMessages.size
            if (size > oldSize) {
                currentCoroutineScope.launch {
                    listState.scrollToItem(size)
                }
            }
        }
        Log.i(TAG, "deviceMessages: ${deviceMessages.size}")
    }

    LaunchedEffect(deviceId, block = {
        queryDevice(deviceId)
        queryMessage(deviceId)
    })

    OnEvent(event = deviceMessageEvent) {
        if (it.deviceId == deviceId) {
            CoroutineScope(Dispatchers.Default).launch {
                queryMessage(deviceId)
            }
        }
    }

    if (deviceMessages.any { it.type == "receive" && it.downloadSuccess != true }) {
        Log.i(TAG, "定时任务")
        OnTimer(block = {
            fileProgressMap = mutableMapOf<Long?, FileProgress?>().also {
                it.putAll(fileProgresses)
            }
        })
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
                        fun openDetail() {
                            globalRouter?.open(
                                route = Route(
                                    key = "message-${it.id}",
                                ) {
                                    DeviceMessageDetail(id = it.id)
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (it.type == "send") Arrangement.End else Arrangement.Start
                        ) {
                            Box {
                                if (it.type == "receive") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.Green)
                                            .clickable {
                                                openDetail()
                                            }
                                            .onGloballyPositioned {
                                                offsetY = it.size.height
                                            }
                                            .padding(5.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(text = it.createdTime?.friendly() ?: "", fontSize = 13.sp, fontWeight = FontWeight.Light)
                                            val fileProgress = fileProgressMap?.get(it.id)
                                            if (it.filename != null) {
                                                Text(text = it.filename ?: "")
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Box(modifier = Modifier.size(20.dp)) {
                                                    if (it.filename != null && it.downloadSuccess != true && fileProgress == null) {
                                                        Image(painter = painterResource(id = R.drawable.download), contentDescription = "",
                                                            modifier = Modifier.clickable {
                                                                CoroutineScope(Dispatchers.IO).launch {
                                                                    downloadMessageFile(device, it)
                                                                }
                                                            }
                                                        )
                                                    }
                                                    if (it.downloadSuccess == true) {
                                                        Image(painter = painterResource(id = R.drawable.download_success), contentDescription = "")
                                                    } else if (fileProgress != null) {
                                                        it.size?.let {size ->
                                                            CircularProgressIndicator(
                                                                progress = fileProgress.handleSize.toFloat()/size
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = "${readableFileSize(fileProgress?.handleSize ?: it.downloadSize ?: 0)}/${readableFileSize(it.size ?: 0)}",
                                                    fontWeight = FontWeight.Light, fontSize = 14.sp
                                                )
                                            }
                                            if (it.content != null) {
                                                Text(text = it.content ?: "")
                                            }
                                        }
                                        Image(
                                            modifier = Modifier
                                                .height(30.dp)
                                                .width(20.dp)
                                                .clickable {
                                                    show = true
                                                },
                                            painter = rememberVectorPainter(image = Icons.Rounded.MoreVert),
                                            contentDescription = null,
                                            contentScale = ContentScale.FillHeight,
                                            alpha = 0.2f
                                        )
                                    }
                                }
                                if (it.type == "send") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(141, 242, 242))
                                            .clickable {
                                                openDetail()
                                            }
                                            .onGloballyPositioned {
                                                offsetX = it.positionInParent().x.toInt()
                                                offsetY = it.size.height
                                            }
                                            .padding(5.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(text = it.createdTime?.friendly() ?: "", fontSize = 13.sp, fontWeight = FontWeight.Light)
                                            if (it.filename != null) {
                                                Text(text = it.filename ?: "")
                                            }
                                            if (it.size != null) {
                                                Text(text = readableFileSize(it.size)?: "", fontWeight = FontWeight.Light, fontSize = 14.sp)
                                            }
                                            if (it.content != null) {
                                                Text(text = it.content ?: "")
                                            }
                                        }
                                        Image(
                                            modifier = Modifier
                                                .height(30.dp)
                                                .width(20.dp)
                                                .clickable {
                                                    show = true
                                                },
                                            painter = rememberVectorPainter(image = Icons.Rounded.MoreVert),
                                            contentDescription = null,
                                            contentScale = ContentScale.FillHeight,
                                            alpha = 0.2f
                                        )
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
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Button(
                            onClick = {
                                openFilePicker(arrayOf("*/*")) {
                                    if (it != null) {
                                        fileInfo = getFileInfo(it)
                                        try {
                                            globalActivity.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "takePersistableUriPermission: ", e)
                                        }
                                    }
                                }
                            }
                        ) {
                            Row {
                                fileInfo.let {
                                    if (it != null) {
                                        val names = getFileNameAndType(it.name)
                                        Text(
                                            text = names?.get(0) ?: "",
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Text(
                                            text = names?.get(1)?.let { ".${it}" } ?: "",
                                        )
                                        Text(
                                            text = " ${readableFileSize(it.size?.toLong()) ?: ""}",
                                            fontWeight = FontWeight.Light, maxLines = 1
                                        )
                                    } else {
                                        Text(
                                            text = "选择文件",
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
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
                            fileUri = fileInfo?.uri?.toString(),
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
                            )))
                            contentType(ContentType.Application.Json)
                        }
                        Log.i(TAG, "接收到回复: ${response.status}")
                        if (response.status == HttpStatusCode.OK) {
                            val body = response.bodyAsText()
                            Log.i(TAG, "接收到回复body: ${body}")
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