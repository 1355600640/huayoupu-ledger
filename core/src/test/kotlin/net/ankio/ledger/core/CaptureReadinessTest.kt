package net.ankio.ledger.core

import org.junit.Assert.*
import org.junit.Test

class CaptureReadinessTest {
    @Test fun persistedSwitchAndMotionListenerDoNotImplyPermissionOrConnection() {
        assertEquals(CaptureBlocker.PERMISSION,CaptureReadiness(true,false,false,true).blocker)
        assertEquals(CaptureBlocker.CONNECTION,CaptureReadiness(true,true,false,true).blocker)
        assertEquals(CaptureBlocker.RUNTIME,CaptureReadiness(true,true,true,false).blocker)
    }
    @Test fun disabledDoesNotAskForPermissionAndRunningLocalCaptureIsReady() {
        assertEquals(CaptureBlocker.DISABLED,CaptureReadiness(false,false,false,false).blocker)
        assertEquals(CaptureBlocker.NONE,CaptureReadiness(true,true,true,true).blocker)
    }
}
