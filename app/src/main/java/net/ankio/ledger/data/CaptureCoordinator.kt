package net.ankio.ledger.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.ankio.ledger.core.*
import net.ankio.ledger.ledger

class CaptureCoordinator(context: Context) {
    private val app = context.ledger
    private val mutex = Mutex()

    suspend fun acceptRecognition(capture: Capture, bill: ParsedBill?, screenshot: ByteArray? = null, merchantIcon: ByteArray? = null, eventKey:String?=null, allowed: () -> Boolean): IngestResult = mutex.withLock {
        if (!allowed() || bill == null) return@withLock IngestResult.Ignored
        synchronized(app.channels) {
            if (!allowed()) IngestResult.Ignored
            else app.store.ingest(capture.copy(text = capture.text.take(32_768)), bill, eventKey=eventKey, screenshot = screenshot, merchantIcon = merchantIcon).also {
                if (it is IngestResult.Saved || it == IngestResult.DuplicateSkipped && merchantIcon!=null) app.changed()
            }
        }
    }


}
