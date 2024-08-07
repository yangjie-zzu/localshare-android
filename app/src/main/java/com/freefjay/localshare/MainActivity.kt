package com.freefjay.localshare

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
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
import com.freefjay.localshare.component.Route
import com.freefjay.localshare.component.Router
import com.freefjay.localshare.component.RouterView
import com.freefjay.localshare.model.Device
import com.freefjay.localshare.pages.Home
import com.freefjay.localshare.ui.theme.LocalshareTheme
import com.freefjay.localshare.util.DbOpenHelper
import com.freefjay.localshare.util.db
import com.freefjay.localshare.util.updateTableStruct
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlin.reflect.KClass

const val TAG = "LOCALSHARE"

lateinit var globalActivity: MainActivity
var globalRouter: Router? = null

val httpClient = HttpClient {
    install(HttpTimeout) {
        requestTimeoutMillis = 60000
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        globalActivity = this
        db = DbOpenHelper(this, "share")
        setContent {

            var init by remember {
                mutableStateOf(false)
            }

            LaunchedEffect(key1 = Unit, block = {
                Log.i(TAG, "表结构同步开始")
                listOf<KClass<out Any>>(Device::class).forEach {
                    updateTableStruct(it)
                }
                Log.i(TAG, "表结构同步成功")
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