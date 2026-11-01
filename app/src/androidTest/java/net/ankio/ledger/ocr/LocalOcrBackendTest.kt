package net.ankio.ledger.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.ankio.ledger.BuildConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalOcrBackendTest {
    @Test fun selectedEngineReturnsCoordinatesWithoutOwningCallerBitmap() = runBlocking {
        withContext(Dispatchers.IO) {
            val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
                canvas.drawText("28.50", 130f, 320f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK; textSize = 90f
                })
                val result = LocalOcr(ApplicationProvider.getApplicationContext()).use { it.recognize(bitmap) }
                assertEquals(BuildConfig.LOCAL_OCR_ENGINE, result.json().getString("engine"))
                assertTrue(result.rawText, result.lines.any { it.text.contains("28.50") })
                assertTrue(result.lines.any { it.box?.contains(270, 285) == true })
                assertFalse("Closing OCR must not recycle the caller's bitmap", bitmap.isRecycled)
                assertEquals(600, result.width)
                assertEquals(800, result.height)
            } finally { bitmap.recycle() }
        }
    }
}
