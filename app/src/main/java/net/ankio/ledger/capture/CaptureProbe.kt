package net.ankio.ledger.capture

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Binder root queries can block. Keep them off the UI thread, without an unbounded queue. */
internal data class CaptureProbeResult<T>(val value:T?,val outcome:String)

internal class CaptureProbe : AutoCloseable {
    private val occupied=AtomicBoolean(false)
    private val worker=Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable,"ledger-window-probe").apply { isDaemon=true }
    }
    suspend fun <T:Any> read(timeoutMillis:Long=700,query:()->T?):T?=readResult(timeoutMillis,query=query).value
    suspend fun <T:Any> readResult(timeoutMillis:Long=700, onLateResult:(()->Unit)?=null, query:()->T?):CaptureProbeResult<T> {
        if(!occupied.compareAndSet(false,true))return CaptureProbeResult(null,"query_busy")
        var submitted=false
        try { return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                try {
                    submitted=true
                    worker.execute {
                        val result=try { val value=query();CaptureProbeResult(value,if(value==null)"query_empty" else "query_ok") }
                            catch(_:Exception) { CaptureProbeResult<T>(null,"query_error") } finally { occupied.set(false) }
                        if(continuation.isActive)continuation.resume(result)
                        else if(result.value!=null)runCatching { onLateResult?.invoke() }
                    }
                } catch(_:Exception) { submitted=false;occupied.set(false);if(continuation.isActive)continuation.resume(CaptureProbeResult<T>(null,"query_closed")) }
            }
        }?:CaptureProbeResult(null,"query_timeout") } finally { if(!submitted)occupied.set(false) }
    }
    override fun close() { worker.shutdownNow() }
}
