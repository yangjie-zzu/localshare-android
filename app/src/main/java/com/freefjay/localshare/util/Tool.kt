package com.freefjay.localshare.util

import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.provider.MediaStore.MediaColumns
import android.provider.OpenableColumns
import android.provider.SyncStateContract.Columns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.FileProvider
import androidx.core.database.getBlobOrNull
import androidx.core.database.getFloatOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import androidx.core.database.sqlite.transaction
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.freefjay.localshare.FileProgress
import com.freefjay.localshare.TAG
import com.freefjay.localshare.getDevice
import com.freefjay.localshare.globalActivity
import com.freefjay.localshare.httpClient
import com.freefjay.localshare.model.Device
import com.freefjay.localshare.model.DeviceMessage
import com.google.gson.Gson
import deviceEvent
import deviceMessageEvent
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import util.TaskQueue
import util.taskQueue
import java.io.File
import java.util.Date

data class FileInfo(
    val uri: Uri?,
    val path: String?,
    val name: String?,
    val size: Int?
)

fun getFileInfo(uri: Uri?): FileInfo? {
    if (uri == null) {
        return null
    }
    val cursor = globalActivity.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (cursor.moveToFirst()) {
            return FileInfo(
                uri = uri,
                path = cursor.getColumnIndex(FileColumns.DATA).let { if (it == -1) null else cursor.getString(it) },
                name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).let { if (it == -1) null else cursor.getString(it) },
                size = cursor.getColumnIndex(OpenableColumns.SIZE).let { if (it == -1) null else cursor.getInt(it) }
            )
        }
    }
    return null
}

fun queryFile(uri: Uri?, name: String?): Map<String, Any?>? {
    if (uri == null) {
        return null
    }
    return globalActivity.contentResolver.query(
        uri,
        null,
        "${MediaStore.Downloads.DISPLAY_NAME}=?",
        arrayOf(name), null)?.use {
        if (it.moveToFirst()) {
            val map = mutableMapOf<String, Any?>()
            for (i in 0 until it.columnCount) {
                val type = it.getType(i)
                map[it.getColumnName(i)] = run {
                    when (type) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_STRING -> it.getIntOrNull(i)
                        Cursor.FIELD_TYPE_INTEGER -> it.getStringOrNull(i)
                        Cursor.FIELD_TYPE_FLOAT -> it.getFloatOrNull(i)
                        Cursor.FIELD_TYPE_BLOB -> it.getBlobOrNull(i)
                        else -> null
                    }
                }
            }
            Log.i(TAG, "map: ${Gson().toJson(map)}")
            map
        } else {
            null
        }
    }
}

fun Modifier.longClick(onLongClick: (Offset) -> Unit): Modifier {
    return pointerInput(this) {
        detectTapGestures(onLongPress = { onLongClick.invoke(it) })
    }
}

fun openFile(uriStr: String?, path: String?) {
    if (uriStr == null) {
        return
    }
    try {
        Log.i(TAG, "savePath: ${uriStr}")
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uri = Uri.parse(uriStr)
        val type =
            run {
                val extension = MimeTypeMap.getFileExtensionFromUrl(path)
                if (extension != null) {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                } else {
                    null
                }
            }
        Log.i(TAG, "type: ${type}, uri: ${uri}")
        intent.setDataAndType(
            uri,
            type
        )
        globalActivity.startActivity(intent)
    } catch (e: Exception) {
        Log.e(TAG, "", e)
    }
}

fun openFileByPath(path: String?) {
    if (path == null) {
        return
    }
    Log.i(TAG, "openFileByPath: ${path}")
    val uriStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        globalActivity.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaColumns._ID),
            "${MediaColumns.DATA}=?",
            arrayOf(path),
            null
        )?.use {
            if (it.moveToFirst()) {
                Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "${it.getIntOrNull(it.getColumnIndex(MediaColumns._ID))}").toString()
            } else {
                FileProvider.getUriForFile(globalActivity, "${globalActivity.applicationContext.packageName}.provider", File(path)).toString()
            }
        }
    } else {
        path.let { File(it).toURI().toString() }
    }
    if (uriStr == null) {
        return
    }
    try {
        Log.i(TAG, "savePath: ${uriStr}")
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uri = Uri.parse(uriStr)
        val type =
            run {
                val extension = MimeTypeMap.getFileExtensionFromUrl(path)
                if (extension != null) {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                } else {
                    null
                }
            }
        Log.i(TAG, "type: ${type}, uri: ${uri}")
        intent.setDataAndType(
            uri,
            type
        )
        globalActivity.startActivity(intent)
    } catch (e: Exception) {
        Log.e(TAG, "", e)
    }
}

fun getFileNameAndType(filename: String?): Array<String?>? {
    if (filename == null) {
        return null
    }
    val index = filename.lastIndexOf('.')
    return arrayOf(if (index > -1) filename.substring(0, index) else filename, if (index > -1) filename.substring(index + 1) else null)
}

val fileProgresses = mutableMapOf<Long?, FileProgress?>()

suspend fun downloadMessageFile(device: Device?, deviceMessage: DeviceMessage) {
    val filename = deviceMessage.filename
    if (device != null && filename != null) {
        val file = run {
            val downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            val dir = File(downloadPath)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            var downloadFile = File(downloadPath, filename)
            if (downloadFile.exists()) {
                val nameAndType = getFileNameAndType(filename)
                var i = 1
                do {
                    downloadFile = File(downloadPath, "${nameAndType?.get(0)}(${i})${if (nameAndType?.get(1) != null) ".${nameAndType[1]}" else ""}")
                    i ++
                } while (downloadFile.exists())
            }
            downloadFile.createNewFile()
            downloadFile
        }
        Log.i(TAG, "file: ${file.absolutePath}")
        val startTime = Date()
        try {
            val downloadTask = TaskQueue()
            deviceMessageEvent.doAction(deviceMessage)
            httpClient.prepareGet("http://${device.ip}:${device.port}/download?messageId=${deviceMessage.oppositeId}") {
                timeout {
                    requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                }
            }.execute { response ->
                val contentLength = response.headers[HttpHeaders.ContentLength]?.toLong() ?: 0L
                var downloadSize = 0L
                withContext(Dispatchers.IO) {
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val packet =
                            channel.readRemaining(limit = DEFAULT_BUFFER_SIZE.toLong())
                        while (!packet.isEmpty) {
                            val bytes = packet.readBytes()
                            file.appendBytes(bytes)
                            downloadSize += bytes.size
                            downloadTask.submit {
//                                Log.i(TAG, "下载进度: ${downloadSize.toDouble()/contentLength}, ${downloadSize}, ${contentLength}")
                                fileProgresses[deviceMessage.id] = FileProgress(
                                    messageId = deviceMessage.id,
                                    handleSize = downloadSize,
                                    totalSize = contentLength
                                )
                            }
                        }
                    }
                    deviceMessage.downloadSuccess = true
                    deviceMessage.downloadSize = downloadSize
                    deviceMessage.size = contentLength
                    deviceMessage.savePath = file.absolutePath
                    save(deviceMessage)
                    downloadTask.submit {
                        Log.i(TAG, "下载用时: ${(Date().time - startTime.time)/1000}s")
                        deviceMessageEvent.doAction(deviceMessage)
                    }
                }
            }
        } catch (e : Exception) {
            Log.i(TAG, "下载失败: ${(Date().time - startTime.time)/1000}s")
            deviceMessageEvent.doAction(deviceMessage)
            throw e
        }
    }
}

suspend fun exchangeDevice(ip: String?, port: Int?): Device? {
    return transaction {
        if (ip == null || port == null) {
            return@transaction null
        }
        val response = httpClient.post("http://${ip}:${port}/exchange") {
            setBody(Gson().toJson(getDevice()))
            contentType(ContentType.Application.Json)
        }
        Log.i(TAG, "status: ${response.status}")
        if (response.status == HttpStatusCode.OK) {
            val body = response.body<String>()
            Log.i(TAG, "body: $body")
            val deviceResult = Gson().fromJson(body, Device::class.java)
            var otherDevice = queryList<Device>("select * from device where client_code = '${deviceResult.clientCode}'").firstOrNull()
            if (otherDevice == null) {
                Log.i(TAG, "exchangeDevice: 新设备")
                otherDevice = Device()
            } else {
                Log.i(TAG, "exchangeDevice: 设备已存在")
            }
            otherDevice.clientCode = deviceResult.clientCode
            otherDevice.name = deviceResult.name
            otherDevice.ip = deviceResult.ip
            otherDevice.port = deviceResult.port
            otherDevice.channelType = deviceResult.channelType
            otherDevice.osName = deviceResult.osName
            otherDevice.networkType = deviceResult.networkType
            otherDevice.wifiName = deviceResult.wifiName
            save(otherDevice)
            CoroutineScope(Dispatchers.Default).launch {
                deviceEvent.doAction(Unit)
            }
            return@transaction otherDevice
        } else {
            return@transaction null
        }
    }
}

@Composable
fun OnTimer(delay: Long = 500, block: suspend () -> Unit) {
    val currentCoroutineScope = rememberCoroutineScope()
    DisposableEffect(delay, effect = {
        var isHide = false
        currentCoroutineScope.launch {
            while (true) {
                if (!isHide) {
                    block()
                }
                delay(delay)
            }
        }
        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                isHide = false
            }

            override fun onPause(owner: LifecycleOwner) {
                isHide = true
            }
        }
        globalActivity.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            Log.i(TAG, "onDispose")
            globalActivity.lifecycle.removeObserver(lifecycleObserver)
        }
    })
}