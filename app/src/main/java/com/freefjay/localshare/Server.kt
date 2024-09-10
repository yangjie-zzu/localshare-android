package com.freefjay.localshare

import Event
import OnEvent
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
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
import androidx.core.content.getSystemService
import androidx.documentfile.provider.DocumentFile
import com.freefjay.localshare.model.Device
import com.freefjay.localshare.model.DeviceMessage
import com.freefjay.localshare.model.DeviceMessageParams
import com.freefjay.localshare.pages.getLocalIp
import com.freefjay.localshare.util.downloadMessageFile
import com.freefjay.localshare.util.exchangeDevice
import com.freefjay.localshare.util.getFileNameAndType
import com.freefjay.localshare.util.queryFile
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.net.InetAddress
import java.util.Date
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlin.collections.firstOrNull

var serverPort = 20000

fun getDevice(): Device {
    val device = Device()
    device.clientCode = clientCode
    device.name = "${Build.BRAND} ${Build.MODEL}"
    device.ip = getLocalIp()
    device.port = serverPort
    device.channelType = "app"
    device.osName = "android"
    return device
}

class FileProgress(val messageId: Long?, val handleSize: Long, val totalSize: Long)

val deviceMessageDownloadEvent = Event<FileProgress>()

fun createServer(): NettyApplicationEngine {
    return embeddedServer(Netty, applicationEngineEnvironment {
        connector {
            port = serverPort
        }
        module {
            routing {
                get("/code") {

                }
                post("/exchange") {
                    Log.i(TAG, "/exchange")
                    val body = call.receiveText()
                    val (_, clientCode, name, ip, port, channelType, osName, networkType, wifiName) = Gson().fromJson(body, Device::class.java)
                    var otherDevice = queryList<Device>("select * from device where client_code = '${clientCode}'").firstOrNull()
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
                        downloadMessageFile(device, deviceMessage)
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

const val serviceType = "_share._tcp"
var nsdManager: NsdManager? = null

val multicastLock by lazy {
    val wifiManager = globalActivity.getSystemService<WifiManager>()
    val lock = wifiManager?.createMulticastLock("mDns-lock")
    lock?.setReferenceCounted(true)
    lock
}

val registrationListener = object : NsdManager.RegistrationListener {
    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
        Log.i(TAG, "onRegistrationFailed: ${serviceInfo?.serviceType}, ${serviceInfo?.serviceName}")
    }

    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
        Log.i(TAG, "onUnregistrationFailed: ${serviceInfo?.serviceType}, ${serviceInfo?.serviceName}")
    }

    override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
        Log.i(TAG, "onServiceRegistered: ${serviceInfo?.serviceType}, ${serviceInfo?.serviceName}")
    }

    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
        Log.i(TAG, "onServiceUnregistered: ${serviceInfo?.serviceType}, ${serviceInfo?.serviceName}")
    }
}
val resolveListener = object : NsdManager.ResolveListener {
    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
        Log.i(TAG, "onResolveFailed: ${serviceInfo?.serviceName}, ${serviceInfo?.serviceType}, ${errorCode}")
    }

    override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
        Log.i(TAG, "解析设备: ${serviceInfo?.serviceName}, ${serviceInfo?.serviceType}")
        val ip = serviceInfo?.host?.hostAddress
        val port = serviceInfo?.port
        Log.i(TAG, "解析ip: ${ip}, ${port}")
        if (serviceInfo?.serviceType == ".${serviceType}" && serviceInfo.serviceName != getDevice().clientCode) {
            Log.i(TAG, "添加设备")
            CoroutineScope(Dispatchers.IO).launch {
                exchangeDevice(ip, port)
            }
        }
    }
}
val discoveryListener = object : NsdManager.DiscoveryListener {
    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
        Log.i(TAG, "onStartDiscoveryFailed: ${serviceType}, ${errorCode}")
        multicastLock?.release()
    }

    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
        Log.i(TAG, "onStopDiscoveryFailed: ${serviceType}, ${errorCode}")
        nsdManager?.stopServiceDiscovery(this)
        multicastLock?.release()
    }

    override fun onDiscoveryStarted(serviceType: String?) {
        Log.i(TAG, "onDiscoveryStarted: ${serviceType}")
    }

    override fun onDiscoveryStopped(serviceType: String?) {
        Log.i(TAG, "onDiscoveryStopped: ${serviceType}")
        multicastLock?.release()
    }

    override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
        Log.i(TAG, "发现设备: ${serviceInfo?.serviceType}, ${serviceInfo?.serviceName}")
        if (serviceInfo?.serviceType == "${serviceType}." && serviceInfo.serviceName != getDevice().clientCode) {
            Log.i(TAG, "解析")
            nsdManager?.resolveService(serviceInfo, resolveListener)
        }
    }

    override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
        Log.i(TAG, "onServiceLost: ${serviceInfo?.serviceType}, ${serviceInfo?.serviceName}")
    }
}

fun startNsd() {
    CoroutineScope(Dispatchers.IO).launch {
        val device = getDevice()
        val httpPort = device.port ?: return@launch
        nsdManager = globalActivity.getSystemService()
        val serviceInfo = NsdServiceInfo()
        serviceInfo.serviceType = serviceType
        serviceInfo.serviceName = device.clientCode
        serviceInfo.port = httpPort
        multicastLock?.acquire()
        nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }
}
