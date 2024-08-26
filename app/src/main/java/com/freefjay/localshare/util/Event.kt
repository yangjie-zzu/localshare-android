import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.freefjay.localshare.TAG

class Event {
    private val actions = mutableListOf<() -> Unit>()
    fun doAction() {
        Log.i(TAG, "actions: ${actions.size}")
        actions.forEach {
            it()
        }
    }
    fun registerAction(onAction: () -> Unit): () -> Unit {
        actions.add(onAction)
        return {
            actions.remove(onAction)
        }
    }
}

val deviceEvent = Event()

val deviceMessageEvent = Event()

@Composable
fun onEvent(event: Event, block: () -> Unit) {
    DisposableEffect(event) {
        val removeAction = event.registerAction {
            block()
        }
        onDispose {
            removeAction()
        }
    }
}