package net.ankio.ledger

import android.animation.ValueAnimator
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankio.ledger.ui.LedgerWebBridge
import net.ankio.ledger.ui.MainActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real native sheets and JavaScript geometry, including interrupted surface transitions. */
@RunWith(AndroidJUnit4::class)
class NativeMorphUiTest {
    @Test fun closingStageKeepsFadedContentUntilRemoval() {
        org.junit.Assume.assumeTrue(ValueAnimator.areAnimatorsEnabled())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            WebUiProbe(scenario).ready()
            scenario.onActivity { it.showAbout() }
            waitFor(scenario) { it.window.decorView.findViewWithTag<View>("about-page")?.width?.let { w -> w>0 }==true }
            SystemClock.sleep(500)
            var removedAlpha=-1f
            scenario.onActivity { activity ->
                val page=activity.window.decorView.findViewWithTag<View>("about-page")!!
                activity.pageHost.setOnHierarchyChangeListener(object:ViewGroup.OnHierarchyChangeListener {
                    override fun onChildViewAdded(parent:View?,child:View?) {}
                    override fun onChildViewRemoved(parent:View?,child:View?) {
                        if(child?.tag=="native-morph-stage")removedAlpha=page.alpha
                    }
                })
                activity.modelPage!!.dismiss()
                animation(activity)!!.end()
                assertNull(activity.modelPage)
                activity.pageHost.setOnHierarchyChangeListener(null)
            }
            assertEquals("The close endpoint must not restore opaque content during teardown",0f,removedAlpha,.01f)
        }
    }
    @Test fun aboutAndPrivacyShareFluidOriginAndFixedCloseWhileBodyScrolls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val web=WebUiProbe(scenario);web.ready()
            scenario.onActivity { activity ->
                LedgerWebBridge(activity).aboutFrom(bounds(activity))
            }
            waitFor(scenario) { it.window.decorView.findViewWithTag<View>("about-page")?.width?.let { width -> width>0 }==true }
            scenario.onActivity { activity ->
                val page=activity.window.decorView.findViewWithTag<View>("about-page")!!
                val surface=activity.window.decorView.findViewWithTag<View>("native-morph-surface")!!
                val stage=activity.window.decorView.findViewWithTag<View>("native-morph-stage")!!
                if(ValueAnimator.areAnimatorsEnabled()) {
                    val animation=animation(activity)!!
                    animation.pause();animation.setCurrentFraction(0f)
                    assertEquals(48,surface.layoutParams.width)
                    assertEquals(48,surface.layoutParams.height)
                    animation.setCurrentFraction(.08f)
                    assertTrue("Panel expands from the tapped control",surface.layoutParams.width>48)
                    assertTrue("Content reveals during expansion",page.alpha>0f && page.alpha<1f)
                    val dim=(stage.background as ColorDrawable).alpha
                    assertTrue("Scrim fades with the surface",dim in 1..55)
                    val alpha=page.alpha
                    activity.modelPage!!.dismiss()
                    assertEquals("Interrupted close keeps the presented opacity",alpha,page.alpha,.03f)
                } else activity.modelPage!!.dismiss()
                // A new request during exit must remove the old stage before opening.
                LedgerWebBridge(activity).privacyFrom(bounds(activity))
            }
            waitFor(scenario) { it.window.decorView.findViewWithTag<View>("privacy-page")?.height?.let { height -> height>0 }==true }
            SystemClock.sleep(500)
            scenario.onActivity { activity ->
                assertNull(activity.window.decorView.findViewWithTag<View>("about-page"))
                val page=activity.window.decorView.findViewWithTag<View>("privacy-page")!!
                val close=children(page).filterIsInstance<Button>().single { it.text.toString()=="关闭" }
                val initialY=IntArray(2).also(close::getLocationOnScreen)[1]
                val body=children(page).filterIsInstance<ScrollView>().single()
                body.scrollTo(0,body.getChildAt(0).height)
                assertTrue("Privacy body can scroll",body.scrollY>0)
                assertEquals("Close stays outside the scrolling body",initialY,IntArray(2).also(close::getLocationOnScreen)[1])
                assertTrue("Close stays visible",close.isShown)
            }
            scenario.onActivity { it.onBackPressed() }
            waitFor(scenario) { it.modelPage==null }
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }

    @Test fun malformedOrOffscreenOriginsCannotOpenNativeSurfaces() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val web=WebUiProbe(scenario);web.ready()
            scenario.onActivity { activity ->
                val bridge=LedgerWebBridge(activity)
                val valid=JSONObject(bounds(activity))
                listOf("not-json","{}",valid.toString().repeat(30),
                    JSONObject(valid.toString()).put("left",-1).toString(),
                    JSONObject(valid.toString()).put("top",activity.webView.height+1).toString(),
                    JSONObject(valid.toString()).put("width",0).toString(),
                    JSONObject(valid.toString()).put("radius",-1).toString(),
                    JSONObject(valid.toString()).put("viewportWidth",0).toString()
                ).forEach { input -> bridge.aboutFrom(input);bridge.privacyFrom(input) }
                assertNull(activity.modelPage)
                bridge.aboutFrom(valid.toString())
                assertNotNull(activity.modelPage)
                activity.modelPage!!.dismiss(false)
            }
        }
    }

    private fun bounds(activity:MainActivity)=JSONObject().put("left",12).put("top",50)
        .put("width",48).put("height",48).put("radius",24)
        .put("viewportWidth",activity.webView.width).toString()

    private fun animation(activity:MainActivity)=activity.modelPage?.let { page ->
        page.javaClass.getDeclaredField("animator").apply { isAccessible=true }.get(page) as? ValueAnimator
    }
    private fun children(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()
    private fun waitFor(scenario:ActivityScenario<MainActivity>,condition:(MainActivity)->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+5000
        var result=false
        while(!result && SystemClock.elapsedRealtime()<deadline) {
            scenario.onActivity { result=condition(it) };if(!result)SystemClock.sleep(30)
        }
        assertTrue("Native sheet did not reach expected state",result)
    }
}
