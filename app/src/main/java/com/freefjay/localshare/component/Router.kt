package com.freefjay.localshare.component

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freefjay.localshare.TAG
import kotlin.math.log

class Route(
    val key: Any,
    val modifier: Modifier = Modifier,
    val content: @Composable () -> Unit
)

interface Router {
    fun open(route: Route, isReplace: Boolean = false)

    fun back(delta: Int = 1)

    fun getContents(): List<Route>
}

val LocalRouter = compositionLocalOf<Router?> { null }

class RouteContent(val index: Int)

val LocalRContent = compositionLocalOf<RouteContent?> { null }

@Composable
fun RouterView(modifier: Modifier, main: Route, onLocalRouter: ((router: Router?) -> Unit)? = null) {

    var routes by remember {
        mutableStateOf(listOf(main))
    }

    CompositionLocalProvider(LocalRouter provides object: Router {
        override fun open(route: Route, isReplace: Boolean) {
            routes = listOf(*routes.toTypedArray(), route)
        }

        override fun back(delta: Int) {
            Log.i(TAG, "back: ${routes.size}")
            if (routes.size > 1) {
                routes = listOf(*routes.subList(0, routes.size - 1).toTypedArray())
            }
        }

        override fun getContents(): List<Route> {
            return listOf(*routes.toTypedArray())
        }

    }) {
        if (onLocalRouter != null) {
            onLocalRouter(LocalRouter.current)
        }
        Box(modifier = modifier) {
            routes.forEachIndexed { index, it ->
                key(it.key) {
                    CompositionLocalProvider(
                        LocalRContent provides RouteContent(index)
                    ) {
                        val displayModifier = if (index == routes.size - 1) Modifier.fillMaxSize() else Modifier.size(0.dp)
                        Column(modifier = displayModifier.then(it.modifier)) {
                            it.content()
                        }
                    }
                }
            }
        }
    }

}

@Composable
fun OnPageHide(block: () -> Unit) {
    val router = LocalRouter.current
    val currentRContent = LocalRContent.current
    val isHide = (currentRContent?.index ?: 0) < (router?.getContents()?.size ?: 0) - 1
    LaunchedEffect(isHide, block = {
        if (isHide) {
            block()
        }
    })
}