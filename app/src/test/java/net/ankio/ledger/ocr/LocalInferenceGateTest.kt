package net.ankio.ledger.ocr


import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Fake synchronous native calls: no model, screenshot or real payment required. */

class LocalInferenceGateTest {
    @Test fun concurrentNativeCallsNeverOverlap() = runBlocking {
        val gate=LocalInferenceGate()
        val active=AtomicInteger();val peak=AtomicInteger();val calls=AtomicInteger()
        withTimeout(10_000) {
            (1..16).map {
                async(Dispatchers.Default) {
                    gate.run {
                        val count=active.incrementAndGet();peak.updateAndGet { maxOf(it,count) }
                        try { Thread.sleep(10);calls.incrementAndGet() } finally { active.decrementAndGet() }
                    }
                }
            }.awaitAll()
        }
        assertEquals(16,calls.get());assertEquals(1,peak.get());assertEquals(0,active.get())
    }

    @Test fun cancellingAWaitingCallNeverStartsNativeWork() = runBlocking {
        val gate=LocalInferenceGate();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val waitingRan=AtomicBoolean()
        val first=launch(Dispatchers.Default) { gate.run { entered.countDown();check(release.await(5,TimeUnit.SECONDS)) } }
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            val waiting=launch(start=CoroutineStart.UNDISPATCHED) { gate.run { waitingRan.set(true) } }
            waiting.cancelAndJoin()
            assertFalse(waitingRan.get())
        } finally { release.countDown();first.join() }
        assertEquals("permit is available after cancellation",42,gate.run { 42 })
    }

    @Test fun cancellationHoldsPermitUntilNativeReturnsAndDiscardsLateResult() = runBlocking {
        val gate=LocalInferenceGate();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val nextEntered=CountDownLatch(1);val persisted=AtomicBoolean()
        val first=launch(Dispatchers.Default) {
            gate.run { entered.countDown();check(release.await(5,TimeUnit.SECONDS));"late result" }
            persisted.set(true)
        }
        var next:Job?=null
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            first.cancel()
            next=launch(Dispatchers.Default) { gate.run { nextEntered.countDown() } }
            assertFalse("cancel must not release a running native call",nextEntered.await(150,TimeUnit.MILLISECONDS))
            assertFalse(persisted.get())
        } finally { release.countDown();first.join();next?.join() }
        assertEquals(0L,nextEntered.count);assertFalse(persisted.get())
    }

    @Test fun nativeFailureReleasesPermit() = runBlocking {
        val gate=LocalInferenceGate()
        val failure=runCatching { gate.run<Unit> { throw IllegalStateException("native failure") } }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(42,gate.run { 42 })
    }
}
