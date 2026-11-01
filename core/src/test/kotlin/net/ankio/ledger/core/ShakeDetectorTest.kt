package net.ankio.ledger.core

import org.junit.Assert.*
import org.junit.Test

class ShakeDetectorTest {
    @Test fun delayedBatchCannotBecomeANewShakeOnReturningToApp() {
        val detector=ShakeDetector()
        assertFalse(detector.sample(1000,30f,0f,0f,10_000))
        assertFalse(detector.sample(1150,-30f,0f,0f,10_000))
        assertFalse(detector.sample(1300,30f,0f,0f,10_000))
        assertFalse(detector.sample(10_010,30f,0f,0f,10_020))
        assertFalse(detector.sample(10_160,-30f,0f,0f,10_170))
        assertTrue(detector.sample(10_310,30f,0f,0f,10_320))
    }

    @Test fun staleSampleClearsUnfinishedPeaks() {
        val detector=ShakeDetector()
        assertFalse(detector.sample(1000,30f,0f,0f,1000))
        assertFalse(detector.sample(1150,-30f,0f,0f,1150))
        assertFalse(detector.sample(1200,0f,0f,10f,3000))
        assertFalse(detector.sample(1300,30f,0f,0f,1300))
    }
    @Test fun normalMovementNeverTriggers() {
        val detector = ShakeDetector()
        for (i in 0..50) assertFalse(detector.sample(i * 100L, 1f, 2f, 10f))
    }
    @Test fun isolatedBumpDoesNotTrigger() {
        val detector = ShakeDetector()
        assertFalse(detector.sample(1000, 30f, 0f, 0f))
        assertFalse(detector.sample(2000, -30f, 0f, 0f))
    }
    @Test fun repeatedStrongMovementTriggersOnceThenCoolsDown() {
        val detector = ShakeDetector()
        assertFalse(detector.sample(1000, 30f, 0f, 0f))
        assertFalse(detector.sample(1150, -30f, 0f, 0f))
        assertTrue(detector.sample(1300, 30f, 0f, 0f))
        assertFalse(detector.sample(1450, -30f, 0f, 0f))
        assertFalse(detector.sample(1600, 30f, 0f, 0f))
        assertFalse(detector.sample(5000, 30f, 0f, 0f))
        assertFalse(detector.sample(5150, -30f, 0f, 0f))
        assertTrue(detector.sample(5300, 30f, 0f, 0f))
    }
}
