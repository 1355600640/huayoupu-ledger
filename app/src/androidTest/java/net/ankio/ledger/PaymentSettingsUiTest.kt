package net.ankio.ledger

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankio.ledger.core.PaymentApps
import net.ankio.ledger.ui.LedgerWebBridge
import net.ankio.ledger.ui.MainActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real WebView/native consent boundary: merely opening a page must not authorize capture. */
@RunWith(AndroidJUnit4::class)
class PaymentSettingsUiTest {
    private val app get()=ApplicationProvider.getApplicationContext<LedgerApp>()

    @Test fun settingsShowTwoIndependentAppsAndOnlyThreePreferenceRows() {
        val selected=app.channels.selectedAutoPackages();val enabled=app.channels.automaticEnabled()
        try {
            app.channels.setAutomaticEnabled(false)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val web=WebUiProbe(scenario);web.ready();web.run("go('settings')")
                web.await("document.querySelectorAll('[data-payment-app]').length===2 && document.querySelectorAll('.account-preferences>button').length===2 && !document.querySelector('#screenshot-storage') && !document.querySelector('#automatic-toggle')")
                web.await("[...document.querySelectorAll('[data-payment-app]')].every(e=>e.getAttribute('aria-checked')==='false') && document.querySelectorAll('#settings .steps .step').length===3")
                web.run("document.querySelector('#backup').click()")
                web.await("document.querySelector('#export-backup') && document.querySelector('#restore-backup')")
                web.run("closeSheet()")
                assertEquals(selected,app.channels.selectedAutoPackages());assertFalse(app.channels.automaticEnabled())
            }
        } finally { app.channels.setAutomaticEnabled(enabled);app.changed() }
    }

    @Test fun cancelingIndividualAppSetupDoesNotSelectOrEnableApplications() {
        val target=PaymentApps.entries.firstOrNull { it.packageName in setOf(PaymentApps.WECHAT,PaymentApps.ALIPAY) && runCatching { app.packageManager.getApplicationInfo(it.packageName,0) }.isSuccess }
        org.junit.Assume.assumeNotNull(target)
        val selected=app.channels.selectedAutoPackages();val enabled=app.channels.automaticEnabled()
        try {
            app.channels.setAutomaticEnabled(false)
            PaymentApps.entries.forEach { app.channels.setAutoPackage(it.packageName,false) }

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val web=WebUiProbe(scenario);web.ready();web.run("go('settings');document.querySelector('[data-payment-app=\"${target!!.packageName}\"]').click()")
                web.await("document.querySelector('#detailDialog').open")
                assertTrue(app.channels.selectedAutoPackages().isEmpty());assertFalse(app.channels.automaticEnabled())
                web.run("document.querySelector('.sms-sheet-close').click()")
                web.await("!document.querySelector('#detailDialog').open")
                assertTrue(app.channels.selectedAutoPackages().isEmpty());assertFalse(app.channels.automaticEnabled())
            }
        } finally {
            app.recognitionSession.invalidate();PaymentApps.entries.forEach { app.channels.setAutoPackage(it.packageName,it.packageName in selected) }
            app.channels.setAutomaticEnabled(enabled);app.changed()
        }
    }

    @Test fun queuedAutomaticCaptureShowsLoadingAndKeepsTheEditorLocked() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val web=WebUiProbe(scenario);web.ready()
            try {
            web.run("refresh=()=>render();recognitionTasks=[{id:900000001,source:'OCR',origin:'payment_auto:com.tencent.mm',createdAt:Date.now(),status:'QUEUED',message:'已获取截图，等待识别',hasScreenshot:false}];render()")
            web.await("document.querySelector('[data-task=\"900000001\"]').classList.contains('is-recognizing') && document.querySelector('[data-task=\"900000001\"] .recognition-spinner') && document.querySelector('[data-task=\"900000001\"] .recognition-state').textContent==='处理中'")
            web.run("openTask(900000001)")
            web.await("document.querySelector('#record-panel').open && document.querySelector('#source-label').textContent==='交易自动识别' && document.querySelector('.scanning').textContent==='已获取截图，等待识别…' && document.querySelector('#import-form .primary').disabled && document.querySelector('#merchant').disabled && document.querySelector('#delete-record').hidden")
            } finally {web.run("recognitionTasks=[];working=false;closeRecordPanel(false);draft=null;render()")}
        }
    }

    @Test fun bridgeRejectsUnknownApplicationsAndInvalidRetentionWithoutMutatingSettings() {
        val selected=app.channels.selectedAutoPackages()
        val retention=app.screenshots.stats().getInt("retentionDays")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val bridge=LedgerWebBridge(activity)
                assertFalse(JSONObject(bridge.setAutoApplication("com.example.not-authorized",true)).getBoolean("ok"))
                assertFalse(JSONObject(bridge.setScreenshotRetention(-1)).getBoolean("ok"))
                assertFalse(JSONObject(bridge.setScreenshotRetention(365)).getBoolean("ok"))
                assertEquals(selected,app.channels.selectedAutoPackages())
                assertEquals(retention,app.screenshots.stats().getInt("retentionDays"))
            }
        }
    }
}
