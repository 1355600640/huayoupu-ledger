package net.ankio.ledger

import android.accessibilityservice.AccessibilityService
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.ankio.ledger.capture.ChannelSettings
import net.ankio.ledger.capture.ScreenCaptureService
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureComponentTest {
    @Test fun compatibleEntryBindsChecksPermissionAndActuallyRevokes() {
        assumeTrue(android.os.Build.MODEL.contains("sdk_gphone"))
        val app=ApplicationProvider.getApplicationContext<LedgerApp>()
        val expected=ComponentName(app.packageName,"com.google.android.accessibility.selecttospeak.SelectToSpeakService")
        val declared=app.packageManager.queryIntentServices(Intent(AccessibilityService.SERVICE_INTERFACE).setPackage(app.packageName),0)
        assertEquals("Exactly one user-visible capture service must be declared",listOf(expected),
            declared.map { ComponentName(it.serviceInfo.packageName,it.serviceInfo.name) })
        assertEquals("慧铭记账 · 页面识别",declared.single().loadLabel(app.packageManager).toString())
        val automation=InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val resolver=app.contentResolver
        val services=Settings.Secure.getString(resolver,"enabled_accessibility_services")
        val enabled=Settings.Secure.getInt(resolver,"accessibility_enabled",0)
        automation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS")
        fun await(condition:()->Boolean) {
            val end=SystemClock.elapsedRealtime()+6000
            while(!condition() && SystemClock.elapsedRealtime()<end)SystemClock.sleep(50)
            assertTrue("Capture service binding/permission did not reach the expected state",condition())
        }
        try {
            Settings.Secure.putString(resolver,"enabled_accessibility_services",expected.flattenToString())
            Settings.Secure.putInt(resolver,"accessibility_enabled",1)
            await { ScreenCaptureService.current!=null && app.channels.permitted(ChannelSettings.Channel.OCR) }
            assertEquals(expected.className,ScreenCaptureService.current!!.javaClass.name)
            assertTrue("Grouped payment results must expose their otherwise omitted status and amount nodes",
                ScreenCaptureService.current!!.serviceInfo.flags and android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS!=0)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { ScreenCaptureService.current!!.disableSelf() }
            await { ScreenCaptureService.current==null && !app.channels.permitted(ChannelSettings.Channel.OCR) }
        } finally {
            Settings.Secure.putString(resolver,"enabled_accessibility_services",services)
            Settings.Secure.putInt(resolver,"accessibility_enabled",enabled)
            automation.dropShellPermissionIdentity()
        }
    }
}
