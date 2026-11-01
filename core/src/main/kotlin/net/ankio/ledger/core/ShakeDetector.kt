package net.ankio.ledger.core

/** Three separated strong movements in 700 ms, with a 3 second cooldown. */
class ShakeDetector {
    private var first = -1L
    private var lastPeak = -1L
    private var lastTrigger = -3000L
    private var count = 0
    fun sample(timeMs: Long, x: Float, y: Float, z: Float,receivedAtMs:Long=timeMs): Boolean {
        if(receivedAtMs-timeMs !in 0..1500L) {
            first=-1;lastPeak=-1;count=0
            return false
        }
        if (timeMs - lastTrigger < 3000 || x*x + y*y + z*z < 24f*24f) return false
        if (lastPeak >= 0 && timeMs - lastPeak < 120) return false
        if (first < 0 || timeMs - first > 700) { first = timeMs; count = 0 }
        lastPeak = timeMs
        if (++count < 3) return false
        lastTrigger = timeMs; first = -1; count = 0
        return true
    }
}
