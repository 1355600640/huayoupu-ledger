package net.ankio.ledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import net.ankio.ledger.capture.CaptureProbe
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CaptureProbeTest {
    @Test fun lateReplySignalsReadinessButNeverReturnsTheStalePage()=runBlocking {
        val probe=CaptureProbe();val wake=AtomicInteger()
        try {
            val first=probe.readResult(20,onLateResult={wake.incrementAndGet();Unit}) {
                Thread.sleep(150);"old synthetic page"
            }
            assertEquals("query_timeout",first.outcome);assertNull(first.value)
            withTimeout(2000) { while(wake.get()==0)delay(10) }
            val fresh=probe.readResult(700) { "current synthetic page" }
            assertEquals("current synthetic page",fresh.value);assertEquals(1,wake.get())
        } finally { probe.close() }
    }
    @Test fun lateEmptyReplyDoesNotStartARecheckLoop()=runBlocking {
        val probe=CaptureProbe();val wake=AtomicInteger()
        try {
            assertEquals("query_timeout",probe.readResult<String>(20,onLateResult={wake.incrementAndGet();Unit}) {
                Thread.sleep(100);null
            }.outcome)
            delay(200);assertEquals(0,wake.get())
        } finally { probe.close() }
    }
    @Test fun timelyReplyDoesNotRequestAnotherPass()=runBlocking {
        val probe=CaptureProbe();val wake=AtomicInteger()
        try {
            assertEquals("ready",probe.readResult(700,onLateResult={wake.incrementAndGet();Unit}) { "ready" }.value)
            assertEquals(0,wake.get())
        } finally { probe.close() }
    }
}
