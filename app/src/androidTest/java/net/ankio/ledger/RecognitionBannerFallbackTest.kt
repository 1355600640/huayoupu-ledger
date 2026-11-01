package net.ankio.ledger

import android.app.UiAutomation
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.ankio.ledger.capture.RecognitionBanner
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecognitionBannerFallbackTest {
    @Test fun savedResultIsTouchableAndOpensItsBillWhilePermissionRepairTakesPriority() {
        val app=ApplicationProvider.getApplicationContext<LedgerApp>()
        var capsule:View?=null
        var flags=0
        var opened:android.content.Intent?=null
        val manager=object:WindowManager by app.getSystemService(WindowManager::class.java) {
            override fun addView(view:View,params:ViewGroup.LayoutParams) { capsule=view;flags=(params as WindowManager.LayoutParams).flags }
            override fun removeViewImmediate(view:View) { capsule=null }
        }
        val context=object:ContextWrapper(app) {
            override fun getSystemService(name:String):Any?=if(name==Context.WINDOW_SERVICE)manager else super.getSystemService(name)
            override fun startActivity(intent:android.content.Intent) { opened=intent }
        }
        val banner=RecognitionBanner(context)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        fun waitForCapsule() {
            val until=SystemClock.elapsedRealtime()+2000
            while(capsule==null && SystemClock.elapsedRealtime()<until)SystemClock.sleep(10)
            assertNotNull(capsule)
        }
        try {
            banner.show("已记账 ¥ 22.50",recordId=42);waitForCapsule()
            assertEquals(0,flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            instrumentation.runOnMainSync { capsule!!.performClick() }
            assertEquals(net.ankio.ledger.ui.MainActivity.OPEN_RECORD,opened!!.action)
            assertEquals(42L,opened!!.getLongExtra("recordId",0))
            opened=null;var repaired=false
            banner.show("请开启页面识别权限",onRepair={repaired=true},recordId=42);waitForCapsule()
            instrumentation.runOnMainSync { capsule!!.performClick() }
            assertTrue(repaired);assertNull(opened)
        } finally { instrumentation.runOnMainSync { banner.removeNow() } }
    }

}
