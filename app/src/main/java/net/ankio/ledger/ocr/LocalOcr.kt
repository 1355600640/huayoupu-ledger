package net.ankio.ledger.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.engine.OCREngine
import com.paddle.ocr.util.OpenCVUtils
import java.io.Closeable
import kotlin.math.roundToInt

/** V6 Tiny, the same pinned model/configuration used in the offline comparison.
 * Synchronous native inference retains the input until it actually returns, including cancellation.
 * Sessions are released after every request so the background service retains no model workspace.
 */
class LocalOcr(context: Context) : Closeable {
    private val context = context.applicationContext
    private var engine: OCREngine? = null
    private var closed = false

    @Synchronized fun recognize(bitmap: Bitmap): LocalOcrResult {
        check(Looper.myLooper() != Looper.getMainLooper()) { "本地识别必须在后台线程运行" }
        check(!closed) { "本地识别已关闭" }
        val started = SystemClock.elapsedRealtime()
        val active = engine ?: run {
            check(OpenCVUtils.init(context)) { "本地识别组件加载失败" }
            OCREngine(context, PaddleOCRConfig(), EngineConfig(numThreads = 4),
                "models/v6/det/inference.onnx", "models/v6/rec/inference.onnx", "models/v6/rec/inference.yml")
                .also { engine = it }
        }
        val result = active.run(bitmap)
        return LocalOcrResult(bitmap.width, bitmap.height, SystemClock.elapsedRealtime() - started,
            result.results.map { reading ->
                val points = reading.box.points
                OcrLine(reading.text, Rect(
                    points.minOf { it.x }.roundToInt().coerceIn(0, bitmap.width),
                    points.minOf { it.y }.roundToInt().coerceIn(0, bitmap.height),
                    points.maxOf { it.x }.roundToInt().coerceIn(0, bitmap.width),
                    points.maxOf { it.y }.roundToInt().coerceIn(0, bitmap.height)))
            })
    }

    @Synchronized override fun close() {
        if (!closed) {
            closed = true
            try { engine?.release() } finally { engine = null }
        }
    }
}
