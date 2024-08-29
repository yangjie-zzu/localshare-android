package com.freefjay.localshare.util

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.freefjay.localshare.globalActivity

data class FileInfo(
    val path: String?,
    val name: String?,
    val size: Int?
)

fun getFileInfo(uri: Uri?): FileInfo? {
    val path = uri?.path
    val cursor = if (uri == null) null else globalActivity.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (cursor.moveToFirst()) {
            return FileInfo(
                path = path,
                name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).let { if (it == -1) null else cursor.getString(it) },
                size = cursor.getColumnIndex(OpenableColumns.SIZE).let { if (it == -1) null else cursor.getInt(it) }
            )
        }
    }
    return null
}

fun Modifier.longClick(onLongClick: (Offset) -> Unit): Modifier {
    return pointerInput(this) {
        detectTapGestures(onLongPress = { onLongClick.invoke(it) })
    }
}