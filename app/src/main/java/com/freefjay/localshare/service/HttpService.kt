package com.freefjay.localshare.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.freefjay.localshare.TAG
import com.freefjay.localshare.util.createServer
import com.freefjay.localshare.util.startNsd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HttpService : Service() {

    override fun onCreate() {
        super.onCreate()
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
