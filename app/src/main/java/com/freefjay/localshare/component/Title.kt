package com.freefjay.localshare.component

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.freefjay.localshare.TAG
import com.freefjay.localshare.globalRouter

@Composable
fun Title(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = {
        Icon(
            painter = rememberVectorPainter(image = Icons.Rounded.ArrowBack),
            contentDescription = "",
            modifier = Modifier
                .padding(end = 15.dp)
                .height(40.dp)
        )
    },
    menu: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {

        val routeContent = LocalRContent.current
        if ((routeContent?.index ?: 0) >= 1) {
            Box(modifier = Modifier.clickable {
                globalRouter?.back()
            }) {
                icon()
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
        menu?.invoke()
    }
}