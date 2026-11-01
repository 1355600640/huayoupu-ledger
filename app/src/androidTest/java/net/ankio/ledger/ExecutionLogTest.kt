package net.ankio.ledger

import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.ankio.ledger.core.*
import net.ankio.ledger.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExecutionLogTest {
    private val app get()=ApplicationProvider.getApplicationContext<LedgerApp>()
    private fun logTest(body:(ExecutionLog,(Long)->Unit)->Unit) {
        val name="execution-test-${UUID.randomUUID()}.db";var clock=1_800_000_000_000L
        val log=ExecutionLog(app,name) { clock }
        try { body(log) { clock+=it } } finally { log.close();app.deleteDatabase(name) }
    }
    @Test fun automaticWechatDiagnosticsNeverInterruptPaymentCapture()=logTest { log,_ ->
        log.start()
        log.event("payment_auto","page_probe","matched",JSONObject().put("textCount",8))
        assertTrue(log.snapshot().toString().contains("payment_auto"))
    }
    @Test fun activeDiagnosticsPreserveRecognitionFailureAndFinishTheTask()=runBlocking {
        val id=app.tasks.create(Source.IMAGE,"synthetic failure")
        val failure=net.ankio.ledger.ocr.RecognitionException("图片无法识别")
        val client=net.ankio.ledger.ocr.OcrClient(app,
            net.ankio.ledger.ocr.RecognitionBackend { _,_,_,_,_,_ -> throw failure })
        try {
            app.executionLog.start()
            val actual=runCatching {
                net.ankio.ledger.capture.CaptureProcessor(app,client)
                    .processPrepared(byteArrayOf(1),Source.IMAGE,"synthetic failure",id) { true }
            }.exceptionOrNull()
            assertSame("Diagnostics must preserve the original recognition error",failure,actual)
            assertEquals("FAILED",app.tasks.status(id))
            assertEquals(failure.userMessage,app.tasks.message(id))
            val events=app.executionLog.snapshot().getJSONArray("events")
            assertTrue((0 until events.length()).any { index ->
                val event=events.getJSONObject(index)
                event.getString("channel")=="model" && event.getString("stage")=="local" && event.getString("outcome")=="failed"
            })
        } finally { app.tasks.delete(id);app.executionLog.clear() }
    }
    @Test fun loggingIsOptInExpiresAfter15MinutesAndCanBeCleared()=logTest { log,advance ->
        log.event("system","test","disabled");assertEquals(0,log.snapshot().getJSONArray("events").length())
        log.start();assertTrue(log.active());log.event("payment_auto","test","received")
        advance(ExecutionLog.DURATION+1);assertFalse(log.active())
        val count=log.snapshot().getJSONArray("events").length();log.event("payment_auto","test","expired")
        assertEquals(count,log.snapshot().getJSONArray("events").length())
        advance(86_400_001);assertEquals(0,log.snapshot().getJSONArray("events").length())
        log.start();log.clear();assertFalse(log.active());assertEquals(0,log.snapshot().getJSONArray("events").length())
    }
    @Test fun logIsBoundedAndSurvivesReopening()=logTest { log,_ ->
        log.start();repeat(ExecutionLog.MAX_EVENTS+20) { log.event("payment_auto","received","synthetic-$it") }
        val snapshot=log.snapshot();assertEquals(ExecutionLog.MAX_EVENTS,snapshot.getJSONArray("events").length())
        assertFalse(snapshot.toString().contains("synthetic-0\""));assertTrue(snapshot.toString().contains("synthetic-319"))
        val reopened=ExecutionLog(app,java.io.File(log.readableDatabase.path).name)
        try { assertEquals(ExecutionLog.MAX_EVENTS,reopened.snapshot().getJSONArray("events").length()) } finally { reopened.close() }
    }
    @Test fun reportContainsPermissionTruthAndNoApiKey() {

        try {
            val report=JSONObject(RecognitionDiagnostics.encode(app));assertEquals(2,report.getInt("version"))
            assertTrue(report.has("execution"));val permissions=report.getJSONObject("permissions")
            assertFalse(permissions.has("receiveSms"));assertFalse(permissions.has("notificationListenerConnected"))
            assertFalse(permissions.getJSONArray("requestedPermissions").toString().contains("android.permission.RECEIVE_SMS"))
            assertEquals("battery_system_detected_oem_user_confirmed",permissions.getJSONObject("backgroundConfirmations").getString("verification"))
            assertFalse(report.toString().contains("synthetic-never-export-key"))
        } finally { app.recognitionSession.invalidate() }
    }
}
