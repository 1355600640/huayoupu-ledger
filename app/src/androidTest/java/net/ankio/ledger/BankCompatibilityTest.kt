package net.ankio.ledger

import android.app.UiAutomation
import android.content.ComponentName
import android.provider.Settings
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.ankio.ledger.capture.ChannelSettings
import net.ankio.ledger.capture.ScreenCaptureService
import net.ankio.ledger.ui.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankCompatibilityTest {
    @Test fun compatibilityActionRevokesTheActualScreenshotServiceAndKeepsRetiredChannelsDisabled() {
        val app=ApplicationProvider.getApplicationContext<LedgerApp>()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val automation=instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val resolver=app.contentResolver
        val previousServices=Settings.Secure.getString(resolver,"enabled_accessibility_services")
        val previousEnabled=Settings.Secure.getInt(resolver,"accessibility_enabled",0)
        val switches=ChannelSettings.Channel.entries.associateWith(app.channels::enabled)
        automation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS")
        fun waitFor(check:()->Boolean) {
            val deadline=SystemClock.elapsedRealtime()+8000
            while(!check() && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(50)
            assertTrue(check())
        }
        fun find(node:AccessibilityNodeInfo?,text:String):AccessibilityNodeInfo? {
            node?:return null
            if(node.text?.toString()==text)return node
            for(i in 0 until node.childCount)find(node.getChild(i),text)?.let { return it }
            return null
        }
        try {
            val component=net.ankio.ledger.capture.captureServiceComponent(app).flattenToString()
            Settings.Secure.putString(resolver,"enabled_accessibility_services",listOfNotNull(previousServices?.takeIf(String::isNotBlank),component).joinToString(":"))
            Settings.Secure.putInt(resolver,"accessibility_enabled",1)
            waitFor { ScreenCaptureService.current!=null && app.channels.permitted(ChannelSettings.Channel.OCR) }
            for(channel in listOf(ChannelSettings.Channel.OCR,ChannelSettings.Channel.SMS,ChannelSettings.Channel.NOTIFICATION))app.channels.set(channel,true)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val web=WebUiProbe(scenario);web.ready();web.run("go('settings')")
                web.await("document.querySelector('.privacy-banner')")
                web.run("privacy()")
                waitFor { find(automation.rootInActiveWindow,"管理截图授权")!=null }
                assertTrue(find(automation.rootInActiveWindow,"管理截图授权")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                waitFor { find(automation.rootInActiveWindow,"取消")!=null }
                assertTrue(app.channels.permitted(ChannelSettings.Channel.OCR))
                assertTrue(app.channels.enabled(ChannelSettings.Channel.OCR))
                assertTrue(find(automation.rootInActiveWindow,"关闭并撤销授权")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                waitFor { !app.channels.permitted(ChannelSettings.Channel.OCR) && ScreenCaptureService.current==null }
                assertFalse(app.channels.enabled(ChannelSettings.Channel.OCR))
                assertFalse(app.channels.enabled(ChannelSettings.Channel.SMS));assertFalse(app.channels.enabled(ChannelSettings.Channel.NOTIFICATION))
                web.run("refresh()")
                web.await("!document.querySelector('#bank-compatibility')")
            }
        } finally {
            Settings.Secure.putString(resolver,"enabled_accessibility_services",previousServices)
            Settings.Secure.putInt(resolver,"accessibility_enabled",previousEnabled)
            automation.dropShellPermissionIdentity()
            switches.forEach { (channel,value)->app.channels.set(channel,value) };app.changed()
        }
    }
}
