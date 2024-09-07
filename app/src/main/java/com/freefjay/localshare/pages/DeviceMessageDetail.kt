package com.freefjay.localshare.pages

import OnEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freefjay.localshare.FileProgress
import com.freefjay.localshare.OnDownloadProgressEvent
import com.freefjay.localshare.component.Page
import com.freefjay.localshare.component.Title
import com.freefjay.localshare.deviceMessageDownloadEvent
import com.freefjay.localshare.model.DeviceMessage
import com.freefjay.localshare.util.format
import com.freefjay.localshare.util.friendly
import com.freefjay.localshare.util.openFile
import com.freefjay.localshare.util.queryById
import com.freefjay.localshare.util.readableFileSize

@Composable
fun DeviceMessageDetail(
    id: Long?
) {
    var deviceMessage by remember {
        mutableStateOf<DeviceMessage?>(null)
    }

    suspend fun queryMessage(id: Long?) {
        deviceMessage = id?.let { queryById(id) }
    }

    LaunchedEffect(id) {
        queryMessage(id)
    }

    var fileProgress by remember {
        mutableStateOf<FileProgress?>(null)
    }

    OnDownloadProgressEvent(block = {
        if (it.messageId == deviceMessage?.id) {
            fileProgress = it
        }
    })

    Page(title = {
        Title {
            Text(text = "消息详情")
        }
    }) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (deviceMessage?.filename != null) {
                        Text(text = "${deviceMessage?.filename}")
                        if (deviceMessage?.type == "receive") {
                            Text(text = "文件位置：${deviceMessage?.savePath ?: ""}",
                                fontWeight = FontWeight.Light,
                                fontSize = 14.sp)
                        }
                        Text(text = buildAnnotatedString {
                            this.append("文件大小：")
                            if (deviceMessage?.type == "receive") {
                                fileProgress.let {
                                    if (it != null) {
                                        this.append("${readableFileSize(it.handleSize)}/${readableFileSize(it.totalSize)}")
                                    } else {
                                        this.append("${readableFileSize(deviceMessage?.downloadSize)}/${readableFileSize(deviceMessage?.size)}")
                                    }
                                }
                            }
                            if (deviceMessage?.type == "send") {
                                this.append("${readableFileSize(deviceMessage?.size)}")
                            }
                        }, fontWeight = FontWeight.Light, fontSize = 14.sp)
                    }
                    if (deviceMessage?.content != null) {
                        Text(text = deviceMessage?.content ?: "")
                    }
                    Text(text = "${
                        deviceMessage?.type?.let { if (it == "receive") "接收" else if (it == "send") "发送" else ""}
                    }时间：${deviceMessage?.createdTime?.format("yyyy-MM-dd HH:mm:ss E") ?: ""}",
                        fontWeight = FontWeight.Light,
                        fontSize = 14.sp)
                }
            }
            if (deviceMessage?.filename != null) {
                Button(onClick = {
                    if (deviceMessage?.type == "receive") {
                        openFile(deviceMessage?.saveUri, deviceMessage?.filename)
                    }
                    if (deviceMessage?.type == "send") {
                        openFile(deviceMessage?.fileUri, deviceMessage?.filename)
                    }
                }) {
                    Text(text = "打开文件")
                }
            }
        }
    }
}