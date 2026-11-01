package net.ankio.ledger

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.ankio.ledger.ui.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsPanelTest {
    @Test fun openDoesNotStartRecordingAndUserCanStartStopAndClear() {
        val app=ApplicationProvider.getApplicationContext<LedgerApp>();app.executionLog.clear()
        val automation=InstrumentationRegistry.getInstrumentation().getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun click(text:String) {
            val deadline=android.os.SystemClock.elapsedRealtime()+5000
            while(android.os.SystemClock.elapsedRealtime()<deadline) {
                val root=automation.rootInActiveWindow
                val nodes=root?.findAccessibilityNodeInfosByText(text).orEmpty()
                try { if(nodes.firstOrNull { it.text?.toString()==text }?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)==true)return }
                finally { nodes.forEach { it.recycle() };root?.recycle() }
                android.os.SystemClock.sleep(50)
            }
            fail("Missing diagnostic action: $text")
        }
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.showDiagnostics() };assertFalse(app.executionLog.active())
            click("开启 15 分钟调试");assertTrue(app.executionLog.active())
            assertTrue(app.executionLog.snapshot().toString().contains("session_start"))
            scenario.onActivity { it.showDiagnostics() };click("停止调试记录");assertFalse(app.executionLog.active())
            click("清空调试日志");assertEquals(0,app.executionLog.snapshot().getJSONArray("events").length())
        } } finally { app.executionLog.clear() }
    }
}
