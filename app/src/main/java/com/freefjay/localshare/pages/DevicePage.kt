package com.freefjay.localshare.pages

import OnEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freefjay.localshare.R
import com.freefjay.localshare.component.DropdownMenu
import com.freefjay.localshare.component.Page
import com.freefjay.localshare.component.Route
import com.freefjay.localshare.component.Title
import com.freefjay.localshare.globalRouter
import com.freefjay.localshare.model.Device
import com.freefjay.localshare.util.queryList
import deviceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DevicePage() {

    val devices = remember {
        mutableStateListOf<Device>()
    }

    suspend fun requestDevice() {
        val list: List<Device> = queryList("select * from device")
        devices.clear()
        devices.addAll(list)
    }

    LaunchedEffect(key1 = Unit, block = {
        requestDevice()
    })

    OnEvent(event = deviceEvent) {
        CoroutineScope(Dispatchers.IO).launch {
            requestDevice()
        }
    }

    Page(
        title = {
            Title(
                menu = {
                    DropdownMenu(trigger = {
                        Icon(painter = rememberVectorPainter(image = Icons.Rounded.MoreVert), contentDescription = "")
                    }) {close ->
                        Row(
                            modifier = Modifier.clickable {
                                close()
                                globalRouter?.open(
                                    route = Route(
                                        key = "deviceInfo",
                                        content = {
                                            DeviceInfo(
                                                onSaveAfter = {
                                                    CoroutineScope(Dispatchers.IO).launch {
                                                        requestDevice()
                                                    }
                                                }
                                            )
                                        }
                                    )
                                )
                            }
                        ) {
                            Text(text = "添加设备", color = Color.White)
                        }
                        Row {
                            Text(text = "扫一扫", color = Color.White)
                        }
                    }
                }
            ) {
                Text(text = "设备列表")
            }
        }
    ) {
        Column {
            Text(text = "设备数量: ${devices.size}")
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                itemsIndexed(
                    items = devices,
                    key = {_, item ->  item.clientCode ?: ""}
                ) {_, item ->
                    Card {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    globalRouter?.open(Route(
                                        key = "messages",
                                        content = {
                                            DeviceMessageView(deviceId = item.id)
                                        }
                                    ))
                                }
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "${item.name}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text(text = "${item.ip}:${item.port}")
                            }
                            if (item.osName?.lowercase()?.contains("windows") == true) {
                                Image(
                                    painter = painterResource(id = R.drawable.windows11_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Image(
                                modifier = Modifier
                                    .height(40.dp)
                                    .width(20.dp)
                                    .clickable {
                                        globalRouter?.open(Route(
                                            key = "deviceInfo",
                                            content = {
                                                DeviceInfo(
                                                    id = item.id,
                                                    onSaveAfter = {
                                                        CoroutineScope(Dispatchers.IO).launch {
                                                            requestDevice()
                                                        }
                                                    },
                                                    onDeleteAfter = {
                                                        CoroutineScope(Dispatchers.IO).launch {
                                                            requestDevice()
                                                        }
                                                    }
                                                )
                                            }
                                        ))
                                    },
                                painter = rememberVectorPainter(image = Icons.Rounded.MoreVert),
                                contentDescription = null,
                                contentScale = ContentScale.FillHeight,
                                alpha = 0.2f
                            )
                        }
                    }
                }
            }
        }
    }
}