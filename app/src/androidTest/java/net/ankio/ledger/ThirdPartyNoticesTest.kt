package net.ankio.ledger

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest
import net.ankio.ledger.ui.MainActivity
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThirdPartyNoticesTest {
    @Test fun originalRuntimeNoticesArePackagedWithoutAlteration() {
        val assets=InstrumentationRegistry.getInstrumentation().targetContext.assets
        val inventory=JSONArray(assets.open("licenses/inventory.json").bufferedReader().use { it.readText() })
        val coordinates=(0 until inventory.length()).map { inventory.getJSONObject(it).getString("coordinate") }
        if (BuildConfig.LOCAL_OCR_ENGINE == "paddle-v6-tiny") {
            assertTrue(coordinates.contains("PaddlePaddle:PaddleOCR:V6-Tiny"))
            assertFalse(coordinates.any { it.startsWith("com.google.mlkit:") })
            assertTrue(assets.open("v6-provenance.json").use { it.readBytes().isNotEmpty() })
        } else assertTrue(coordinates.contains("com.google.mlkit:text-recognition-chinese:16.0.1"))
        assertTrue(coordinates.any { it.startsWith("org.jetbrains.kotlin:kotlin-stdlib:") })
        var notices=0
        for(i in 0 until inventory.length()) {
            val files=inventory.getJSONObject(i).getJSONArray("original_notices")
            for(j in 0 until files.length()) {
                val file=files.getJSONObject(j)
                val bytes=assets.open("licenses/"+file.getString("file")).use { it.readBytes() }
                val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                assertEquals(file.getString("sha256"),sha)
                notices++
            }
        }
        assertTrue("Original SDK notifications must survive asset preparation",notices>0)
    }

    @Test fun offlineLicenseViewerOpensAndReturnsWithoutEnablingScriptsOrNetworking() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            WebUiProbe(scenario).ready()
            scenario.onActivity { it.showAbout() }
            waitFor(scenario) { it.window.decorView.findViewWithTag<View>("about-page")!=null }
            click(scenario,"about-page","许可与第三方声明")
            waitFor(scenario) { it.window.decorView.findViewWithTag<View>("licenses-page")!=null }
            click(scenario,"licenses-page","查看第三方依赖声明")
            waitFor(scenario) { activity ->
                val page=activity.window.decorView.findViewWithTag<View>("third-party-notices-page")
                page!=null && children(page).filterIsInstance<WebView>().singleOrNull()?.let {
                    it.url=="file:///android_asset/licenses/index.html" && it.progress==100
                }==true
            }
            scenario.onActivity { activity ->
                val page=activity.window.decorView.findViewWithTag<View>("third-party-notices-page")!!
                val viewer=children(page).filterIsInstance<WebView>().single()
                assertFalse(viewer.settings.javaScriptEnabled)
                assertTrue(viewer.settings.blockNetworkLoads)
                assertTrue(children(page).filterIsInstance<Button>().single { it.text=="关闭" }.isShown)
            }
            click(scenario,"third-party-notices-page","返回许可说明")
            waitFor(scenario) { it.window.decorView.findViewWithTag<View>("licenses-page")!=null }
            scenario.onActivity { it.modelPage!!.dismiss(false) }
        }
    }

    private fun children(view:View):List<View> = listOf(view)+if(view is ViewGroup)
        (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()
    private fun click(scenario:ActivityScenario<MainActivity>,tag:String,text:String) {
        SystemClock.sleep(500)
        scenario.onActivity { activity ->
            val page=activity.window.decorView.findViewWithTag<View>(tag)!!
            assertTrue(children(page).filterIsInstance<Button>().single { it.text==text }.performClick())
        }
    }
    private fun waitFor(scenario:ActivityScenario<MainActivity>,condition:(MainActivity)->Boolean) {
        val end=SystemClock.elapsedRealtime()+8000
        var ready=false
        while(!ready&&SystemClock.elapsedRealtime()<end) {
            scenario.onActivity { ready=condition(it) }
            if(!ready)SystemClock.sleep(30)
        }
        assertTrue("Offline notice sheet did not reach the expected state",ready)
    }
}
