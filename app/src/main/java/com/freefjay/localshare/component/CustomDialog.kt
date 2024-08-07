package com.freefjay.localshare.component

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.freefjay.localshare.TAG
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.log

class AlertDialogInstance<T>(
    private val flush: () -> Unit
) {

    var show: Boolean = false
        private set(value) {
            field = value
            flush()
        }

    var onClose: ((value: T) -> Unit)? = null

    fun open() {
        show = true
        Log.i(TAG, "显示: ${show}")
    }

    fun close(value: T) {
        show = false
        onClose?.invoke(value)
        Log.i(TAG, "关闭: ${show}")
    }

}

suspend fun confirm(alertDialogInstance: AlertDialogInstance<Boolean>): Boolean {
    return suspendCoroutine {continuation ->
        alertDialogInstance.onClose = {
            continuation.resume(it)
        }
        alertDialogInstance.open()
    }
}

@Composable
fun <T> alertDialogState(): AlertDialogInstance<T> {
    val recompose = currentRecomposeScope

    return remember {
        AlertDialogInstance { recompose.invalidate() }
    }
}
