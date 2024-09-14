package com.freefjay.localshare.pages

import androidx.compose.foundation.clickable
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
    val tabState = tabState("device")

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
            if (tabState.activeKey == "my") {
                My()
            }
            if (tabState.activeKey == "setting") {
                SettingsView()
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
                Column(
                    modifier = Modifier.clickable {
                        tabState.activeKey = "device"
                    }
                ) {
                    Text(text = "设备")
                }
                Column(
                    modifier = Modifier.clickable {
                        tabState.activeKey = "my"
                    }
                ) {
                    Text(text = "我的")
                }
                Column(
                    modifier = Modifier.clickable {
                        tabState.activeKey = "setting"
                    }
                ) {
                    Text(text = "设置")
                }
            }
        }
    }
}