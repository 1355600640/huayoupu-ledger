package net.ankio.ledger.ocr

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One native engine at a time across imports, live captures and startup repair.
 * The operation is synchronous on purpose: cancellation cannot release the permit
 * (or recycle its bitmap) while native inference still owns those resources. */
internal class LocalInferenceGate {
    private val permit = Semaphore(1)

    suspend fun <T> run(operation: () -> T): T = permit.withPermit {
        currentCoroutineContext().ensureActive()
        val result = operation()
        // Native code may ignore cancellation. Never publish that late result.
        currentCoroutineContext().ensureActive()
        result
    }

    companion object {
        val shared = LocalInferenceGate()
    }
}
