package com.freefjay.localshare.pages

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.freefjay.localshare.TAG
import com.freefjay.localshare.component.Page
import com.freefjay.localshare.component.Title
import com.freefjay.localshare.model.SysInfo
import com.freefjay.localshare.util.onBlur
import com.freefjay.localshare.util.queryList
import com.freefjay.localshare.util.save
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val LocalSysInfo = compositionLocalOf<Map<String?, SysInfo>?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView() {

    Page(title = {
        Title {
            Text(text = "设置")
        }
    }) {
        var initSysInfoMap by remember {
            mutableStateOf<Map<String?, SysInfo>?>(null)
        }
        LaunchedEffect(Unit, block = {
            val list = queryList<SysInfo>("select * from sys_info")
            initSysInfoMap = kotlin.run {
                val map = mutableMapOf<String?, SysInfo>()
                for (item in list) {
                    map[item.name] = item
                }
                map
            }
            Log.i(TAG, "重新运行: ${Gson().toJson(list)}, ${Gson().toJson(initSysInfoMap)}")
        })
        if (initSysInfoMap == null) {
            return@Page
        }
        CompositionLocalProvider(LocalSysInfo provides initSysInfoMap) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                SysInfoItem(labelText = "下载并发", name = "concurrentCount", initValue = "${10}")
                SysInfoItem(labelText = "下载分块(单位：byte)", name = "chunkSize", initValue = "${10 * 1024 * 1024}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SysInfoItem(
    labelText: String,
    name: String,
    initValue: String? = null,
    keyboardType: KeyboardType = KeyboardType.Number
) {
    val localSysInfoMap = LocalSysInfo.current
    val sysInfo = remember {
        localSysInfoMap?.get(name) ?: SysInfo(name = name, value = initValue)
    }
    var value by remember {
        mutableStateOf(sysInfo.value)
    }
    OutlinedTextField(
        label = {Text(text = labelText)},
        value = value ?: "",
        onValueChange = {
            value = it
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .onBlur {
                sysInfo.value = value
                CoroutineScope(Dispatchers.IO).launch {
                    save(sysInfo)
                }
            }
    )
}