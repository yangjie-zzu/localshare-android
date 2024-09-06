package com.freefjay.localshare.util

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore.Files.FileColumns
import android.provider.MediaStore.MediaColumns
import android.provider.OpenableColumns
import android.provider.SyncStateContract.Columns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.freefjay.localshare.TAG
import com.freefjay.localshare.globalActivity

data class FileInfo(
    val uri: Uri?,
    val path: String?,
    val name: String?,
    val size: Int?
)

fun getFileInfo(uri: Uri?): FileInfo? {
    if (uri == null) {
        return null
    }
    val cursor = globalActivity.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (cursor.moveToFirst()) {
            return FileInfo(
                uri = uri,
                path = cursor.getColumnIndex(FileColumns.DATA).let { if (it == -1) null else cursor.getString(it) },
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

fun openFile(uriStr: String?, path: String?) {
    if (uriStr == null) {
        return
    }
    try {
        Log.i(TAG, "savePath: ${uriStr}")
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uri = Uri.parse(uriStr)
        val type =
            run {
                val extension = MimeTypeMap.getFileExtensionFromUrl(path)
                if (extension != null) {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                } else {
                    null
                }
            }
        Log.i(TAG, "type: ${type}, uri: ${uri}")
        intent.setDataAndType(
            uri,
            type
        )
        globalActivity.startActivity(intent)
    } catch (e: Exception) {
        Log.e(TAG, "", e)
    }
}