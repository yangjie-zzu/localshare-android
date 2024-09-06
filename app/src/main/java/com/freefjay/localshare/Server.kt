package com.freefjay.localshare

import Event
import OnEvent
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.freefjay.localshare.model.Device
import com.freefjay.localshare.model.DeviceMessage
import com.freefjay.localshare.model.DeviceMessageParams
import com.freefjay.localshare.pages.getLocalIp
import com.freefjay.localshare.util.queryList
import com.freefjay.localshare.util.queryOne
import com.freefjay.localshare.util.save
import com.google.gson.Gson
import deviceEvent
import deviceMessageEvent
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.util.Date
import kotlin.collections.firstOrNull


fun getDevice(): Device {
    val device = Device()
    device.clientCode = clientCode
    device.name = "${Build.BRAND} ${Build.MODEL}"
    device.ip = getLocalIp()
    device.port = 20000
    device.channelType = "app"
    device.osName = "android"
    return device
}

class FileProgress(val messageId: Long?, val handleSize: Long, val totalSize: Long)

val deviceMessageDownloadEvent = Event<FileProgress>()

fun createServer(): NettyApplicationEngine {
    return embeddedServer(Netty, applicationEngineEnvironment {
        connector {
            port = 20000
        }
        module {
            routing {
                post("/exchange") {
                    val body = call.receiveText()
                    val (_, clientCode, name, ip, port, channelType, osName, networkType, wifiName) = Gson().fromJson(body, Device::class.java)
                    var otherDevice = queryList<Device>("select * from device where client_id = '${clientCode}'").firstOrNull()
                    if (otherDevice == null) {
                        otherDevice = Device()
                    }
                    otherDevice.clientCode = clientCode
                    otherDevice.name = name
                    otherDevice.ip = ip
                    otherDevice.port = port
                    otherDevice.channelType = channelType
                    otherDevice.osName = osName
                    otherDevice.networkType = networkType
                    otherDevice.wifiName = wifiName
                    save(otherDevice)
                    async {
                        deviceEvent.doAction(Unit)
                    }
                    call.respondText(contentType = ContentType.Application.Json) {
                        Gson().toJson(getDevice())
                    }
                }

                post("/message") {
                    val body = call.receiveText()
                    Log.i(TAG, "body: ${body}")
                    val deviceSendParams = Gson().fromJson(body, DeviceMessageParams::class.java)
                    val deviceMessage = DeviceMessage()
                    deviceMessage.oppositeId = deviceSendParams.sendId
                    deviceMessage.content = deviceSendParams.content
                    deviceMessage.filename = deviceSendParams.filename
                    deviceMessage.createdTime = Date()
                    deviceMessage.type = "receive"
                    deviceMessage.size = deviceSendParams.size
                    val device = queryOne<Device>("select * from device where client_code = '${deviceSendParams.clientCode}'")
                    deviceMessage.deviceId = device?.id
                    save(deviceMessage)
                    Log.i(TAG, "接收到消息: ${Gson().toJson(deviceMessage)}")
                    async {
                        deviceMessageEvent.doAction(deviceMessage)
                        if (device != null && deviceMessage.filename != null) {
                            val contentValues = ContentValues().apply {
                                put(MediaStore.DownloadColumns.DISPLAY_NAME, deviceMessage.filename)
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
                                        try {
                                            globalActivity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "takePersistableUriPermission: ", e)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    call.response.status(HttpStatusCode.OK)
                }

                get("/download") {
                    val messageId = call.parameters.get("messageId")?.toLong()
                    val deviceMessage = queryOne<DeviceMessage>("select * from device_message where id = ${messageId}")
                    val fileUri = deviceMessage?.fileUri
                    if (fileUri == null) {
                        call.respond(status = HttpStatusCode.NotFound, "not found path")
                    } else {
                        try {
                            globalActivity.contentResolver.openInputStream(Uri.parse(fileUri))?.use {
                                call.respondOutputStream(
                                    contentLength = it.available().toLong()
                                ) {
                                    val out = this
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var n: Int
                                    withContext(Dispatchers.IO) {
                                        while (it.read(buffer).also { n = it } > 0) {
                                            out.write(buffer, 0, n)
                                        }
                                    }
                                }
                            }
                        } catch (e: FileNotFoundException) {
                            Log.e(TAG, "FileNotFoundException: ", e)
                            call.respond(status = HttpStatusCode.NotFound, "not found file")
                        }
                    }
                }
            }
        }
    })
}

@Composable
fun OnDownloadProgressEvent(block: (data: FileProgress) -> Unit) {

    var processTime by remember {
        mutableStateOf(Date())
    }

    OnEvent(event = deviceMessageDownloadEvent, block = {
        if (Date().time - processTime.time > 200 || (it.handleSize >= it.totalSize)) {
            block(it)
            processTime = Date()
        }
    })
}