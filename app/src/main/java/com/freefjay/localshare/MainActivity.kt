package com.freefjay.localshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.freefjay.localshare.component.Route
import com.freefjay.localshare.component.Router
import com.freefjay.localshare.component.RouterView
import com.freefjay.localshare.model.Device
import com.freefjay.localshare.model.DeviceMessage
import com.freefjay.localshare.model.FilePart
import com.freefjay.localshare.model.SysInfo
import com.freefjay.localshare.pages.Home
import com.freefjay.localshare.pages.registerFilePickerLauncher
import com.freefjay.localshare.ui.theme.LocalshareTheme
import com.freefjay.localshare.util.DbOpenHelper
import com.freefjay.localshare.util.db
import com.freefjay.localshare.util.delete
import com.freefjay.localshare.util.queryOne
import com.freefjay.localshare.util.save
import com.freefjay.localshare.util.updateTableStruct
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import java.util.UUID

const val TAG = "LOCALSHARE"

lateinit var globalActivity: MainActivity
var globalRouter: Router? = null

val httpClient = HttpClient {
    install(HttpTimeout) {
        requestTimeoutMillis = 60000
    }
}

var clientCode: String? = null

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        globalActivity = this
        db = DbOpenHelper(this, "share")
        this.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent {

            var init by remember {
                mutableStateOf(false)
            }

            LaunchedEffect(key1 = Unit, block = {
                Log.i(TAG, "表结构同步开始")
                listOf(Device::class, DeviceMessage::class, SysInfo::class, FilePart::class).forEach {
                    updateTableStruct(it)
                }
                Log.i(TAG, "表结构同步成功")
                clientCode = run {
                    var sysInfo = queryOne<SysInfo>("select * from sys_info where name = 'client_code'")
                    if (sysInfo == null) {
                        sysInfo = SysInfo(
                            name = "client_code",
                            value = UUID.randomUUID().toString()
                        )
                        save(sysInfo)
                    }
                    sysInfo.value
                }
                delete<SysInfo>(4)
                globalActivity.startService(Intent(globalActivity, HttpService::class.java))
                init = true
            })

            if (init) {
                LocalshareTheme {
                    // A surface container using the 'background' color from the theme
                    RouterView(
                        modifier = Modifier.fillMaxSize(),
                        main = Route(key = "main") {
                            Home()
                        },
                        onLocalRouter = {
                            globalRouter = it
                        }
                    )
                }
            }

            val onBack = remember {
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if ((globalRouter?.getContents()?.size ?: 0) > 1) {
                            globalRouter?.back()
                        } else {
                            globalActivity.moveTaskToBack(true)
                        }
                    }

                }
            }

            val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            DisposableEffect(key1 = Unit, effect = {
                onBackPressedDispatcher?.addCallback(onBack)
                onDispose {
                    onBack.remove()
                }
            })
        }
        registerFilePickerLauncher()
    }

    override fun onStop() {
        Log.i(TAG, "MainActivity停止")
        super.onStop()
    }

    override fun onDestroy() {
        Log.i(TAG, "MainActivity退出")
        super.onDestroy()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LocalshareTheme {
        Greeting("Android")
    }
}