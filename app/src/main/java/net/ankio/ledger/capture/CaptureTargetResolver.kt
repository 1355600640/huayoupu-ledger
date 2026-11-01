package net.ankio.ledger.capture

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Kept separate from package lookup: a known owner does not prove readable safe content. */
internal suspend fun readManualCapturePage(read:suspend ()->CaptureProbeResult<net.ankio.ledger.core.PaymentPage>):CaptureProbeResult<net.ankio.ledger.core.PaymentPage> =
    withTimeoutOrNull(2_600) {
        var result=CaptureProbeResult<net.ankio.ledger.core.PaymentPage>(null,"query_empty")
        repeat(8) { attempt ->
            result=read()
            val page=result.value
            // Never turn a sensitive page into permission by waiting for another page.
            if(page!=null && (net.ankio.ledger.core.CapturePrivacy.manualAllowed(page) ||
                    net.ankio.ledger.core.CapturePrivacy.sensitive(page) ||
                    !net.ankio.ledger.core.CapturePrivacy.manualPackageAllowed(page.packageName)))return@withTimeoutOrNull result
            if(result.outcome=="query_closed")return@withTimeoutOrNull result
            if(attempt<7)delay(150)
        }
        result
    }?:CaptureProbeResult(null,"query_timeout")

/** Window roots can briefly be absent during the first Activity transition. */
internal suspend fun resolveCaptureTarget(read: suspend () -> String?): String? = withTimeoutOrNull(3_000) {
    repeat(12) { attempt ->
        val pkg=read()
        if(net.ankio.ledger.core.CapturePrivacy.manualPackageAllowed(pkg)) return@withTimeoutOrNull pkg
        // A first window snapshot can still belong to the previous Activity. Retry it
        // within this shake; never accept that package as permission to capture.
        if(attempt<11) delay(180)
    }
    null
}
