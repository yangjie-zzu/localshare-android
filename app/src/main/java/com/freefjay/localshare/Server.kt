package com.freefjay.localshare

import android.os.Build
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
import kotlinx.coroutines.async
import java.util.Date


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
                    Log.i(TAG, "接收到消息: ${body}")
                    val deviceSendParams = Gson().fromJson(body, DeviceMessageParams::class.java)
                    val deviceMessage = DeviceMessage()
                    deviceMessage.oppositeId = deviceSendParams.sendId
                    deviceMessage.content = deviceSendParams.content
                    deviceMessage.filename = deviceMessage.filename
                    deviceMessage.isFile = deviceSendParams.isFile
                    deviceMessage.createdTime = Date()
                    deviceMessage.type = "receive"
                    val device = queryOne<Device>("select * from device where client_code = '${deviceSendParams.clientCode}'")
                    deviceMessage.deviceId = device?.id
                    save(deviceMessage)
                    Log.i(TAG, "接收到消息: ${Gson().toJson(deviceMessage)}")
                    async {
                        deviceMessageEvent.doAction()
                    }
                    call.response.status(HttpStatusCode.OK)
                }
            }
        }
    }).start()
}