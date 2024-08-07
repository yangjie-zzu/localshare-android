package com.freefjay.localshare.pages

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings.Secure
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.freefjay.localshare.TAG
import com.freefjay.localshare.component.DropdownMenu
import com.freefjay.localshare.component.Form
import com.freefjay.localshare.component.FormInstance
import com.freefjay.localshare.component.FormItem
import com.freefjay.localshare.component.FormValidateError
import com.freefjay.localshare.component.Page
import com.freefjay.localshare.component.Title
import com.freefjay.localshare.component.alertDialogState
import com.freefjay.localshare.component.confirm
import com.freefjay.localshare.component.formState
import com.freefjay.localshare.globalActivity
import com.freefjay.localshare.globalRouter
import com.freefjay.localshare.httpClient
import com.freefjay.localshare.model.Device
import com.freefjay.localshare.util.delete
import com.freefjay.localshare.util.queryList
import com.freefjay.localshare.util.save
import com.google.gson.Gson
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.InternalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress

fun getLocalIp(): String? {
    val connectivityManager = globalActivity.getSystemService<ConnectivityManager>()
    val activeNetwork = connectivityManager?.activeNetwork
    val networkCapabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
    if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
        val wifiManager = globalActivity.getSystemService<WifiManager>()
        val address = wifiManager?.connectionInfo?.ipAddress
        if (address != null) {
            return android.text.format.Formatter.formatIpAddress(address)
        }
    }
    if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) {
        return InetAddress.getLocalHost().hostAddress
    }
    return null
}

fun getDevice(): Device {
    val device = Device()
    device.clientId = Secure.ANDROID_ID
    device.name = Build.DEVICE
    device.ip = getLocalIp()
    device.port = 20000
    device.channelType = "app"
    device.osName = "android"
    return device
}

@OptIn(ExperimentalMaterial3Api::class, InternalAPI::class)
@Composable
fun DeviceInfo(
    id: Long? = null,
    onSaveAfter: ((device: Device) -> Unit)? = null,
    onDeleteAfter: ((device: Device?) -> Unit)? = null
) {

    var device by remember {
        mutableStateOf<Device?>(null)
    }

    val ip = formState<String?>(initialValue = "") {
        if (it?.isBlank() != false) {
            throw FormValidateError("ip不能为空")
        }
    }

    val port = formState<Int?>(initialValue = null) {
        if (it == null) {
            throw FormValidateError("端口号不能为空")
        }
    }

    val formInstance = remember {
        FormInstance()
    }

    suspend fun queryDevice(id: Long?) {
        device = if (id == null) null else queryList<Device>("select * from device").firstOrNull()
        ip.value = device?.ip
        port.value = device?.port
    }

    LaunchedEffect(key1 = Unit) {
        queryDevice(id)
    }

    val deleteAlertDialog = alertDialogState<Boolean>()

    Log.i(TAG, "对话框显示: ${deleteAlertDialog.show}")
    if (deleteAlertDialog.show) {
        AlertDialog(
            title = { Text(text = "提示")},
            text = { Text(text = "确认删除设备？")},
            onDismissRequest = {
                deleteAlertDialog.close(false)
            },
            dismissButton = {
                Button(onClick = {
                    deleteAlertDialog.close(false)
                }) {
                    Text(text = "取消")
                }
            },
            confirmButton = {
                Button(onClick = {
                    deleteAlertDialog.close(true)
                }) {
                    Text(text = "确定")
                }
            })
    }
    Page(
        title = {
            Title(
                menu = {
                    DropdownMenu(trigger = {
                        Icon(painter = rememberVectorPainter(image = Icons.Default.MoreVert), contentDescription = null)
                    }) {
                        Row(
                            modifier = Modifier.clickable {
                                CoroutineScope(Dispatchers.Default).launch {
                                    it()
                                    if (confirm(deleteAlertDialog)) {
                                        if (device?.id != null) {
                                            delete<Device>(device?.id)
                                            onDeleteAfter?.invoke(device)
                                        }
                                        globalRouter?.back()
                                    }
                                }
                            }
                        ) {
                            Text(text = "删除", color = Color.White)
                        }
                    }
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "设备信息")
                }
            }
        }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Form(formInstance = formInstance) {
                FormItem(
                    fieldState = ip
                ) {
                    TextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = ip.value ?: "",
                        onValueChange = {
                            ip.value = it
                        },
                        placeholder = {
                            Text(text = "ip地址")
                        }
                    )
                    if (ip.hasError) {
                        Text(text = "${ip.errorMsg}")
                    }
                }
                FormItem(
                    fieldState = port
                ) {
                    TextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = port.value?.toString() ?: "",
                        onValueChange = {
                            port.value = it.toInt()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text(text = "端口号")}
                    )
                    if (port.hasError) {
                        Text(text = "${port.errorMsg}")
                    }
                }
            }
            Button(
                onClick = {

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            formInstance.validate()
                            val response = httpClient.post("http://${ip.value}:${port.value}/exchange") {
                                setBody(Gson().toJson(getDevice()))
                                contentType(ContentType.Application.Json)
                            }
                            Log.i(TAG, "status: ${response.status}")
                            if (response.status == HttpStatusCode.OK) {
                                val body = response.body<String>()
                                Log.i(TAG, "body: $body")
                                val deviceResult = Gson().fromJson(body, Device::class.java)
                                var otherDevice = queryList<Device>("select * from device where client_id = '${deviceResult.clientId}'").firstOrNull()
                                if (otherDevice == null) {
                                    otherDevice = Device()
                                }
                                otherDevice.clientId = deviceResult.clientId
                                otherDevice.name = deviceResult.name
                                otherDevice.ip = deviceResult.ip
                                otherDevice.port = deviceResult.port
                                otherDevice.channelType = deviceResult.channelType
                                otherDevice.osName = deviceResult.osName
                                otherDevice.networkType = deviceResult.networkType
                                otherDevice.wifiName = deviceResult.wifiName
                                save(otherDevice)
                                queryDevice(otherDevice.id)
                                onSaveAfter?.invoke(otherDevice)
                            }
                        } catch (_: FormValidateError) {
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "连接")
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "名称：${device?.name ?: ""}")
                Text(text = "类型：${device?.channelType ?: ""}")
                Text(text = "系统：${device?.osName ?: ""}")
                Text(text = "网络：${device?.networkType ?: ""}")
                Text(text = "wifi：${device?.wifiName ?: ""}")
            }
        }
    }
}