package com.freefjay.localshare.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.freefjay.localshare.component.Page
import com.freefjay.localshare.component.Title
import com.freefjay.localshare.getDevice
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

@Composable
fun My() {

    var self by remember {
        mutableStateOf(getDevice())
    }

    fun querySelf() {
        self = getDevice()
    }

    val url = "http://${self.ip}:${self.port}/code"

    Page(title = {
        Title {
            Text(text = "我的设备")
        }
    }) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            SelectionContainer(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("name: ${self.name ?: ""}")
                    Text("clientCode: ${self.clientCode ?: ""}")
                    Text("ip: ${self.ip ?: ""}")
                    Text("port: ${self.port ?: ""}")
                    Text("channelType: ${self.channelType ?: ""}")
                    Text("osName: ${self.osName ?: ""}")
                    Text("networkType: ${self.networkType ?: ""}")
                    Text("wifiName: ${self.wifiName ?: ""}")
                }
            }
            val byteMatrix = remember(url) {
                Encoder.encode(
                    url,
                    ErrorCorrectionLevel.H,
                    mapOf(
                        EncodeHintType.CHARACTER_SET to "UTF-8",
                        EncodeHintType.MARGIN to 16,
                        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
                    )
                ).matrix
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(10.dp).aspectRatio(1f)
            ) {
                Canvas(
                    modifier = Modifier.fillMaxWidth().background(Color.Transparent)
                ) {
                    byteMatrix?.let {
                        val cellSize = size.width / byteMatrix.width
                        for (x in 0 until byteMatrix.width) {
                            for (y in 0 until byteMatrix.height) {
                                drawRect(
                                    color = if (byteMatrix.get(x, y) == 1.toByte()) Color.Black else Color.White,
                                    topLeft = Offset(x * cellSize, y * cellSize),
                                    size = Size(cellSize, cellSize)
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {querySelf()}
                ) {
                    Text("刷新")
                }
            }
        }
    }
}