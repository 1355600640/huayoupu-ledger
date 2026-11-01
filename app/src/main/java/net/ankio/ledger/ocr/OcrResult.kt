package net.ankio.ledger.ocr

import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

data class OcrLine(val text: String, val box: Rect?)

data class LocalOcrResult(val width: Int, val height: Int, val elapsedMs: Long, val lines: List<OcrLine>) {
    val rawText: String get() = lines.joinToString("\n") { it.text }

    fun json(): JSONObject = JSONObject()
        .put("engine", net.ankio.ledger.BuildConfig.LOCAL_OCR_ENGINE)
        .put("width", width).put("height", height).put("elapsedMs", elapsedMs)
        .put("rawText", rawText)
        .put("lines", JSONArray().also { array -> lines.forEach { line ->
            array.put(JSONObject().put("text", line.text).put("box", line.box?.let {
                JSONArray(listOf(it.left, it.top, it.right, it.bottom))
            } ?: JSONObject.NULL))
        } })
}

