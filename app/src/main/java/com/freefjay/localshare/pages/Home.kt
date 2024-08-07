package com.freefjay.localshare.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freefjay.localshare.component.Page
import com.freefjay.localshare.component.Title
import com.freefjay.localshare.component.tabState

@Composable
fun Home(

) {
    val tabState by tabState("device")

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier.weight(1f)
        ) {
            if (tabState.activeKey == "device") {
                DevicePage()
            }
            if (tabState.activeKey == "info") {
                Page(
                    title = {
                        Title {
                            Text(text = "我的信息")
                        }
                    }
                ) {
                }
            }
            if (tabState.activeKey == "setting") {
                Page(
                    title = {
                        Title {
                            Text(text = "设置")
                        }
                    }
                ) {
                }
            }
        }
        Box(
            modifier = Modifier.height(40.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column {
                    Text(text = "设备")
                }
                Column {
                    Text(text = "我的")
                }
                Column {
                    Text(text = "设置")
                }
            }
        }
    }
}