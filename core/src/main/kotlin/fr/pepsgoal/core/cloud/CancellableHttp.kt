package fr.pepsgoal.core.cloud

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Consume/close the response on OkHttp's dispatcher, including cancellation while reading its body. */
suspend fun <T> Call.consumeCancellable(consume: (Response) -> T): T = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            if (!cont.isActive) { response.close(); return }
            try {
                val value = response.use(consume)
                if (cont.isActive) cont.resume(value)
            } catch (e: Exception) { if (cont.isActive) cont.resumeWithException(e) }
        }
    })
}
