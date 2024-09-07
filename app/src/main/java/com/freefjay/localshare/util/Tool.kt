package com.freefjay.localshare.util

import android.content.ContentValues
import android.content.Intent
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.freefjay.localshare.FileProgress
import com.freefjay.localshare.TAG
import com.freefjay.localshare.deviceMessageDownloadEvent
import com.freefjay.localshare.globalActivity
import com.freefjay.localshare.httpClient
import com.freefjay.localshare.model.Device
import com.freefjay.localshare.model.DeviceMessage
import com.google.gson.Gson
import deviceMessageEvent
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
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

fun queryFile(uri: Uri?, name: String?): Map<String, String?>? {
    if (uri == null) {
        return null
    }
    return globalActivity.contentResolver.query(
        uri,
        null,
        "${MediaStore.Downloads.DISPLAY_NAME}=?",
        arrayOf(name), null)?.use {
        if (it.moveToFirst()) {
            val map = mutableMapOf<String, String?>()
            for (i in 0 until it.columnCount) {
                map[it.getColumnName(i)] = it.getString(i)
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

fun getFileNameAndType(filename: String?): Array<String?>? {
    if (filename == null) {
        return null
    }
    val index = filename.lastIndexOf('.')
    return arrayOf(if (index > -1) filename.substring(0, index) else filename, if (index > -1) filename.substring(index + 1) else null)
}

suspend fun downloadMessageFile(device: Device?, deviceMessage: DeviceMessage) {
    if (device != null && deviceMessage.filename != null) {
        var saveFilename = deviceMessage.filename
        val names = getFileNameAndType(deviceMessage.filename)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                var i = 0
                while (queryFile(MediaStore.Downloads.EXTERNAL_CONTENT_URI, saveFilename) != null) {
                    i ++
                    saveFilename = "${names?.get(0)}(${i})${names?.get(1)?.let { ".${it}" } ?: ""}"
                }
            } catch (e : Exception) {
                Log.e(TAG, "", e)
            }
        }
        val contentValues = ContentValues().apply {
            put(MediaStore.DownloadColumns.DISPLAY_NAME, saveFilename)
            put(MediaStore.DownloadColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            globalActivity.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            null
        }
        Log.i(TAG, "uri: ${uri}")
        var savePath: String? = null
        uri?.let { uri ->
            val cursor = globalActivity.contentResolver.query(uri, arrayOf(MediaColumns.DATA), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(MediaColumns.DATA)
                    savePath = if (index != -1) it.getString(index) else null
                    Log.i(TAG, "path: ${savePath}")
                }
            }
            globalActivity.contentResolver.openOutputStream(
                uri
            )
        }?.use {
            httpClient.prepareGet("http://${device.ip}:${device.port}/download?messageId=${deviceMessage.oppositeId}") {
            }.execute { response ->
                val contentLength = response.headers[HttpHeaders.ContentLength]?.toLong() ?: 0L
                var downloadSize = 0L
                withContext(Dispatchers.IO) {
                    val startTime = Date()
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val packet =
                            channel.readRemaining(limit = DEFAULT_BUFFER_SIZE.toLong())
                        while (!packet.isEmpty) {
                            val bytes = packet.readBytes()
                            it.write(bytes)
                            downloadSize += bytes.size
                            Log.i(TAG, "下载进度: ${downloadSize.toDouble()/contentLength}, ${downloadSize}, ${contentLength}")
                            async {
                                deviceMessageDownloadEvent.doAction(FileProgress(messageId = deviceMessage.id, handleSize = downloadSize, totalSize = contentLength))
                            }
                        }
                    }
                    Log.i(TAG, "下载用时: ${(Date().time - startTime.time)/1000}s")
                    deviceMessage.downloadSuccess = true
                    deviceMessage.downloadSize = downloadSize
                    deviceMessage.size = contentLength
                    deviceMessage.savePath = savePath
                    deviceMessage.saveUri = uri.toString()
                    save(deviceMessage)
                    deviceMessageEvent.doAction(deviceMessage)
                }
            }
        }
    }
}