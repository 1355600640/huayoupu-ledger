package net.ankio.ledger

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankio.ledger.core.*
import net.ankio.ledger.data.*
import net.ankio.ledger.ui.LedgerWebBridge
import net.ankio.ledger.ui.MainActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AcquisitionScopeTest {
    @Test fun readOnlyCaptureDiagnosticsExplainTheCurrentProbeWithoutExportingLedgerData() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val method=LedgerWebBridge::class.java.methods.firstOrNull { it.name=="captureDiagnostics" }
                assertNotNull("Current capture diagnostics must be readable without restarting accessibility",method)
                val state=JSONObject(method!!.invoke(LedgerWebBridge(activity)) as String)
                assertTrue(state.has("wechatPaymentMonitor"))
                assertFalse(state.has("records"));assertFalse(state.has("ai"))
            }
        }
    }
    @Test fun liveSettingsAndDiagnosticsOnlyExposeCurrentAcquisition() {
        val app=ApplicationProvider.getApplicationContext<LedgerApp>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val state=JSONObject(LedgerWebBridge(activity).snapshot())
                assertEquals(setOf("ocr"),state.getJSONObject("channels").keys().asSequence().toSet())
                val permissions=RecognitionDiagnostics.permissions(app)
                assertEquals(setOf("ocr"),permissions.getJSONObject("channels").keys().asSequence().toSet())
                assertFalse(permissions.has("notificationListenerConnected"))
                assertFalse(permissions.has("receiveSms"))
            }
        }
    }
    @Test fun openingLedgerNeverReparsesHistoricalNotificationIntoAPostedBill() {
        val app=ApplicationProvider.getApplicationContext<LedgerApp>()
        val name="retired-rules-${UUID.randomUUID()}.db"
        var store=LedgerStore(app,name)
        try {
            val capture=Capture(Source.NOTIFICATION,"com.eg.android.AlipayGphone","交易提醒\n你有一笔0.01元的支出，点此查看详情",System.currentTimeMillis())
            val bill=ParsedBill(1,Direction.EXPENSE,"未知商户","其他",capture.receivedAt,null,false,"交易提醒缺少商户信息，请核对后确认",false)
            val record=(store.ingest(capture,bill) as IngestResult.Saved).record
            store.close();store=LedgerStore(app,name)
            assertEquals("Opening an old ledger must preserve its existing review and financial data",record,store.get(record.id))
        } finally { store.close();app.deleteDatabase(name) }
    }
}
