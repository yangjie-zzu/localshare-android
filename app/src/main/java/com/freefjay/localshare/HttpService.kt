package com.freefjay.localshare

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HttpService : Service() {
    
    private val httpService = createServer()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "创建service")
        httpService.start()
        startNsd()
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(1000)
                Log.i(TAG, "HttpService运行检测")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "启动service")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.i(TAG, "停止service")
    }
}
