package net.ankio.ledger

import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankio.ledger.ui.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledRecognitionUiTest {
    private val app get()=ApplicationProvider.getApplicationContext<LedgerApp>()
    @Test fun everyPermissionStageOmitsModelConfigurationAndEngineLabels() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val web=WebUiProbe(scenario);web.ready()
            listOf("accessibility","restricted","background","review","done").forEach { stage ->
                web.run("window.LedgerSetup.render({kind:'shake',stage:'$stage',target:'摇一摇识别',next:'battery',focusStep:'battery',rows:['battery','autostart','task_lock'].map(key=>({key,done:false,skipped:false})),message:'',enableAfter:true,permitted:true})")
                web.await("document.querySelector('#detailDialog').open && !/(OCR|模型|API Key|远端|云端)/i.test(document.querySelector('#detailContent').textContent)")
                assertEquals("2",web.value("document.querySelectorAll('.flow-progress>span').length"))
            }
            web.run("window.LedgerSetup.close(false)")
        }
    }
    @Test fun settingsAndHelpHaveNoEngineConfigurationAndAppHasNoNetworkPermission() {
        assertFalse(app.packageManager.getPackageInfo(app.packageName,PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val web=WebUiProbe(scenario);web.ready();web.run("go('settings')")
            web.await("!document.querySelector('#model-info') && document.querySelectorAll('.account-preferences>button').length===2")
            web.await("!/(OCR|识别模型|API Key|远端|云端)/i.test(document.querySelector('#settings').textContent)")
            web.run("document.querySelector('#settings-help').click()")
            web.await("document.querySelector('#sheet-content').textContent.includes('截图不上传') && !/(OCR|识别模型|API Key|远端|云端)/i.test(document.querySelector('#sheet-content').textContent)")
            web.run("closeSheet(false)")
            scenario.onActivity { activity ->
                assertFalse(net.ankio.ledger.ui.LedgerWebBridge::class.java.methods.any { it.name.startsWith("configureModel") })
                activity.privacy()
                val page=activity.window.decorView.findViewWithTag<View>("privacy-page")!!
                val text=texts(page)
                assertTrue(text.contains("截图不上传"))
                assertFalse(Regex("OCR|API Key|远端|云端",RegexOption.IGNORE_CASE).containsMatchIn(text))
                activity.modelPage!!.dismiss(false)
                activity.showAbout()
                val about=texts(activity.window.decorView.findViewWithTag<View>("about-page")!!)
                assertFalse(Regex("OCR|API Key|远端|云端",RegexOption.IGNORE_CASE).containsMatchIn(about))
                activity.modelPage!!.dismiss(false)
            }
        }
    }
    private fun texts(view:View):String=(if(view is TextView)view.text.toString() else "")+
        if(view is ViewGroup)(0 until view.childCount).joinToString(" ") { texts(view.getChildAt(it)) } else ""
}
