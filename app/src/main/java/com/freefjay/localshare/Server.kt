package com.freefjay.localshare

import Event
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.collections.firstOrNull
import kotlin.collections.mutableMapOf
import kotlin.collections.set


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

val deviceMessageDownloadEvents = mutableMapOf<Long?, Event<Double>>()

fun startServer() {
    embeddedServer(Netty, applicationEngineEnvironment {
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
                        deviceEvent.doAction()
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
                        deviceMessageEvent.doAction()
                        if (device != null && deviceMessage.filename != null) {
                            val deviceMessageDownloadEvent = Event<Double>()
                            deviceMessageDownloadEvents[deviceMessage.id] = deviceMessageDownloadEvent
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
                            uri?.let { uri ->
                                globalActivity.contentResolver.openOutputStream(
                                    uri
                                )
                            }?.use {
                                httpClient.prepareGet("http://${device.ip}:${device.port}/download?messageId=${deviceMessage.oppositeId}") {
                                    this.onDownload { bytesSentTotal, contentLength ->
                                        async {
                                            val progress = bytesSentTotal * 1.0/contentLength
                                            deviceMessageDownloadEvent.doAction(progress)
                                        }
                                    }
                                }.execute { response ->
                                    withContext(Dispatchers.IO) {
                                        val startTime = Date()
                                        val channel = response.bodyAsChannel()
                                        while (!channel.isClosedForRead) {
                                            val packet =
                                                channel.readRemaining(limit = DEFAULT_BUFFER_SIZE.toLong())
                                            while (!packet.isEmpty) {
                                                it.write(packet.readBytes())
                                            }
                                        }
                                        Log.i(TAG, "下载用时: ${(Date().time - startTime.time)/1000}s")
                                    }
                                }
                            }
                        }
                    }
                    call.response.status(HttpStatusCode.OK)
                }
            }
        }
    }).start()
}