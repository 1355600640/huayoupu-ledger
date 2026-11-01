package net.ankio.ledger

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankio.ledger.capture.ChannelSettings
import net.ankio.ledger.ui.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetiredPageCaptureTest {
    private val app get()=ApplicationProvider.getApplicationContext<LedgerApp>()
    @Test fun upgradeDisablesLegacyPageCaptureWithoutEnablingShake() {
        val prefs=app.getSharedPreferences("channels",0)
        val oldPage=prefs.getBoolean("page",false);val oldConsent=prefs.getBoolean("page_consent_v1",false);val oldShake=prefs.getBoolean("ocr",false)
        try {
            prefs.edit().putBoolean("page",true).putBoolean("page_consent_v1",true).putBoolean("ocr",false).commit()
            val channels=ChannelSettings(app)
            assertFalse(channels.enabled(ChannelSettings.Channel.PAGE));assertFalse(channels.captureEnabled())
            assertFalse(prefs.getBoolean("page",false));assertFalse(prefs.getBoolean("page_consent_v1",false))
            channels.set(ChannelSettings.Channel.PAGE,true)
            assertFalse(channels.enabled(ChannelSettings.Channel.PAGE))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val web=WebUiProbe(scenario);web.ready();web.run("go('settings')")
                assertEquals("true",web.value("!document.querySelector('[data-channel=page],[data-channel-info=page]')"))
                assertEquals("3",web.value("document.querySelectorAll('[data-channel]').length"))
            }
        } finally { prefs.edit().putBoolean("page",oldPage).putBoolean("page_consent_v1",oldConsent).putBoolean("ocr",oldShake).commit();app.changed() }
    }
}
