package net.ankio.ledger.core

enum class CaptureBlocker { DISABLED, PERMISSION, CONNECTION, RUNTIME, NONE }

/** A saved feature switch is intent, not proof that the capture pipeline is usable. */
data class CaptureReadiness(val enabled:Boolean,val permitted:Boolean,
    val connected:Boolean,val runtimeRunning:Boolean) {
    val blocker:CaptureBlocker get()=when {
        !enabled -> CaptureBlocker.DISABLED
        !permitted -> CaptureBlocker.PERMISSION
        !connected -> CaptureBlocker.CONNECTION
        !runtimeRunning -> CaptureBlocker.RUNTIME
        else -> CaptureBlocker.NONE
    }
}
