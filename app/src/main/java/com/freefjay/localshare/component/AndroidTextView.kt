package com.freefjay.localshare.component

import android.graphics.Typeface
import android.graphics.fonts.FontFamily
import android.text.Html
import android.text.SpannedString
import android.widget.TextView
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AndroidTextView(
    modifier: Modifier = Modifier,
    text: CharSequence? = null,
    isSelectable: Boolean = true,
    color: Int? = null,
    fontWeight: Int = Typeface.NORMAL
) {
    if (text == null) {
        return
    }
    AndroidView(factory = {
        TextView(it)
    }, modifier = modifier) {
        it.text = text
        it.setTextIsSelectable(isSelectable)
        if (color != null) {
            it.setTextColor(color)
        }
        it.setTypeface(null, fontWeight)
    }
}