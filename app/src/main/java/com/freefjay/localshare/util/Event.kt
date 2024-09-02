import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.freefjay.localshare.TAG

class Event<T: Any> {
    private val actions = mutableListOf<(data: T?) -> Unit>()
    fun doAction(data: T? = null) {
        actions.forEach {
            it(data)
        }
    }
    fun registerAction(onAction: (data: T?) -> Unit): () -> Unit {
        actions.add(onAction)
        return {
            actions.remove(onAction)
        }
    }
}

val deviceEvent = Event<Unit>()

val deviceMessageEvent = Event<Unit>()

@Composable
fun <T : Any> OnEvent(event: Event<T>, block: (data: T?) -> Unit) {
    DisposableEffect(event) {
        val removeAction = event.registerAction(block)
        onDispose {
            removeAction()
        }
    }
}