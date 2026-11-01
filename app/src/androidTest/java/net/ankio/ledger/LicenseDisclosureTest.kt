package net.ankio.ledger

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankio.ledger.ui.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the real offline notice viewer, including its navigation boundary. */
@RunWith(AndroidJUnit4::class)
class LicenseDisclosureTest {
    private fun views(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()

    private fun openLicenses(activity: MainActivity) {
        MainActivity::class.java.getDeclaredMethod("showLicenses").apply { isAccessible = true }.invoke(activity)
    }

    @Test fun projectLicenseIsGPLAndFullTextIsAvailableOffline() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openLicenses(activity)
                val labels = views(activity.window.decorView).filterIsInstance<TextView>()
                assertTrue("Current project must disclose GPL-3.0", labels.any { it.text.contains("GPL-3.0") })
                assertFalse(labels.any { it.text.contains("不再作整体 GPL") })
                assertTrue("Full project license must be reachable", labels.any { it.text == "查看项目开源协议" })
                val license = activity.assets.open("licenses/project-GPL-3.0.txt").bufferedReader().use { it.readText() }
                assertTrue(license.contains("GNU GENERAL PUBLIC LICENSE"))
                assertTrue(license.contains("Version 3, 29 June 2007"))
            }
        }
    }

    @Test fun textNoticesOpenWhileExternalAndTraversalRequestsStayBlocked() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openLicenses(activity)
                views(activity.window.decorView).filterIsInstance<TextView>()
                    .first { it.text == "查看第三方依赖声明" }.performClick()
            }
            // The embedded page's dismissal completes asynchronously.
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var checked = false
            repeat(50) {
                if (!checked) {
                    scenario.onActivity { activity ->
                        val viewer = views(activity.window.decorView).filterIsInstance<WebView>()
                            .firstOrNull { it.url?.startsWith("file:///android_asset/licenses/") == true }
                        if (viewer != null) {
                            val client = viewer.webViewClient
                            for (file in listOf("ONNX-Runtime-LICENSE.txt", "OpenCV-LICENSE.txt", "project-GPL-3.0.txt")) {
                                assertFalse("Blocked offline notice: $file", client.shouldOverrideUrlLoading(viewer, Request("file:///android_asset/licenses/$file")))
                            }
                            for (url in listOf("https://example.com/", "javascript:alert(1)", "file:///sdcard/private.txt",
                                "file:///android_asset/licenses/../web/app.js", "file:///android_asset/licenses/%2e%2e/private.txt")) {
                                assertTrue("Unexpected navigation allowed: $url", client.shouldOverrideUrlLoading(viewer, Request(url)))
                            }
                            checked = true
                        }
                    }
                    if (!checked) Thread.sleep(100)
                }
            }
            assertTrue("Offline notice page did not open", checked)
        }
    }

    private class Request(private val value: String) : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(value)
        override fun isForMainFrame() = true
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
}
