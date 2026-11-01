package net.ankio.ledger.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.floor

/** Optional presentation metadata. It never participates in deciding whether to post a bill. */
data class MerchantIconRegion(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    companion object {
        fun parse(value: Any?, imageWidth:Int=0, imageHeight:Int=0): MerchantIconRegion? {
            val json = value as? JSONObject ?: return null
            if (json.opt("kind") !in setOf("merchant_logo", "counterparty_avatar")) return null
            val confidence = (json.opt("confidence") as? Number)?.toDouble() ?: return null
            if (!confidence.isFinite() || confidence !in 0.9..1.0) return null
            if(json.opt("shape")=="circle") {
                // One physical radius avoids separate x/y normalization stretching a circle.
                if(json.opt("coordinate_space")!="pixels" || imageWidth<=0 || imageHeight<=0 || json.has("box"))return null
                val center=json.optJSONArray("center")?:return null
                if(center.length()!=2)return null
                val cx=(center.opt(0) as? Number)?.toDouble()?:return null
                val cy=(center.opt(1) as? Number)?.toDouble()?:return null
                val radius=(json.opt("radius") as? Number)?.toDouble()?:return null
                if(listOf(cx,cy,radius).any { !it.isFinite() } || radius<=0 || cx-radius<0 || cy-radius<0 ||
                    cx+radius>imageWidth || cy+radius>imageHeight)return null
                return MerchantIconRegion((cx-radius)*1000/imageWidth,(cy-radius)*1000/imageHeight,
                    (cx+radius)*1000/imageWidth,(cy+radius)*1000/imageHeight)
            }
            val box = json.opt("box") as? JSONArray ?: return null
            if (box.length() != 4) return null
            val input = (0..3).map { (box.opt(it) as? Number)?.toDouble() ?: return null }
            val coordinates = when(val space=json.opt("coordinate_space")) {
                null, JSONObject.NULL, "normalized_1000" -> input
                "normalized_1" -> {
                    if(input.any { !it.isFinite() || it !in 0.0..1.0 })return null
                    input.map { it*1000 }
                }
                "pixels" -> {
                    if(imageWidth<=0 || imageHeight<=0)return null
                    input.mapIndexed { index,coordinate -> coordinate*1000/if(index%2==0)imageWidth else imageHeight }
                }
                else -> return null
            }
            if (coordinates.any { !it.isFinite() || it !in 0.0..1000.0 }) return null
            val (left, top, right, bottom) = coordinates
            if (left >= right || top >= bottom) return null
            return MerchantIconRegion(left, top, right, bottom)
        }
    }
}

/** Crops the exact JPEG shown to the model, not a differently sized original screenshot. */
data class MerchantIconCropResult(val bytes:ByteArray?,val reason:String,val details:String)

object MerchantIconCropper {
    fun crop(jpeg:ByteArray,region:MerchantIconRegion?):ByteArray?=evaluate(jpeg,region).bytes

    fun evaluate(jpeg: ByteArray, region: MerchantIconRegion?): MerchantIconCropResult {
        if(region==null)return MerchantIconCropResult(null,"missing_region","reason=missing_region")
        if(jpeg.isEmpty() || jpeg.size>5*1024*1024)return MerchantIconCropResult(null,"image_size","reason=image_size")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        return evaluate(bounds.outWidth,bounds.outHeight,region,true) { BitmapFactory.decodeByteArray(jpeg,0,jpeg.size) }
    }

    /** The local OCR client has already validated the encoded size and owns this bitmap. */
    internal fun evaluate(bitmap:Bitmap,region:MerchantIconRegion?,verifiedBox:Boolean=false):MerchantIconCropResult {
        if(region==null)return MerchantIconCropResult(null,"missing_region","reason=missing_region")
        return evaluate(bitmap.width,bitmap.height,region,false,verifiedBox) { bitmap }
    }

    private fun evaluate(imageWidth:Int,imageHeight:Int,region:MerchantIconRegion,recycle:Boolean,verifiedBox:Boolean=false,decode:()->Bitmap?):MerchantIconCropResult {
        val geometry="; image=${imageWidth}x${imageHeight}; box=${listOf(region.left,region.top,region.right,region.bottom).joinToString(",") { java.lang.String.format(java.util.Locale.ROOT,"%.2f",it) }}"
        fun rejected(reason:String)=MerchantIconCropResult(null,reason,"reason=$reason$geometry")
        if (imageWidth !in 1..1800 || imageHeight !in 1..1800) return rejected("image_dimensions")
        val x = region.left * imageWidth / 1000
        val y = region.top * imageHeight / 1000
        val width = (region.right - region.left) * imageWidth / 1000
        val height = (region.bottom - region.top) * imageHeight / 1000
        // Do not clamp bad boxes into apparently valid icons or accept large content regions.
        if (listOf(x,y,width,height).any { !it.isFinite() } || x<0 || y<0 ||
            region.right>1000 || region.bottom>1000 || width<=0 || height<=0)return rejected("out_of_bounds")
        if(width<16 || height<16)return rejected("too_small")
        if(width/height !in 0.7..1.43)return rejected("aspect_ratio")
        if(maxOf(width,height)>minOf(imageWidth,imageHeight)*0.28 ||
            width*height>imageWidth.toDouble()*imageHeight*0.04)return rejected("too_large")
        return try {
            val original = decode() ?: return rejected("decode_failed")
            try {
                val hint=Rect(floor(x).toInt(),floor(y).toInt(),ceil(x+width).toInt(),ceil(y+height).toInt())
                // The local locator already segmented all parts of this logo, including wordmarks.
                val localized=if(verifiedBox)hint else IconLocalization.refine(original,hint)?:return rejected("no_detail")
                val icon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                try {
                    // Validate the original region first. A bounded guard band accommodates slightly tight AI boxes.
                    val margin=(maxOf(localized.width(),localized.height())*0.08).coerceIn(2.0,8.0)
                    val src = Rect(floor(localized.left-margin).toInt().coerceAtLeast(0), floor(localized.top-margin).toInt().coerceAtLeast(0),
                        ceil(localized.right+margin).toInt().coerceAtMost(original.width), ceil(localized.bottom+margin).toInt().coerceAtMost(original.height))
                    val scale = 120.0 / maxOf(src.width(), src.height())
                    val w = (src.width() * scale).toInt().coerceAtLeast(1)
                    val h = (src.height() * scale).toInt().coerceAtLeast(1)
                    val canvas = Canvas(icon)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(original, src, Rect((128-w)/2, (128-h)/2, (128+w)/2, (128+h)/2), Paint(Paint.FILTER_BITMAP_FLAG))
                    if (!hasDetail(icon)) return rejected("no_detail")
                    val bytes=MerchantIconEncoding.encode(icon) ?:return rejected("encode_failed")
                    MerchantIconCropResult(bytes,"saved","reason=saved$geometry; refined=${localized.flattenToString()}; bytes=${bytes.size}")
                } finally { icon.recycle() }
            } finally { if(recycle)original.recycle() }
        } catch (_: Exception) { rejected("processing_failed") }
    }

    private fun hasDetail(bitmap: Bitmap): Boolean {
        // Exclude empty/solid-colour crops; ignore the optional letterbox margin.
        val sums=DoubleArray(3)
        val squares=DoubleArray(3)
        var samples = 0
        for (y in 16 until 112 step 4) for (x in 16 until 112 step 4) {
            val pixel = bitmap.getPixel(x, y)
            val channels=intArrayOf(Color.red(pixel),Color.green(pixel),Color.blue(pixel))
            for(channel in 0..2) { val value=channels[channel].toDouble();sums[channel]+=value;squares[channel]+=value*value }
            samples++
        }
        // Equal brightness does not mean equal colour (for example a red/green logo).
        return (0..2).any { squares[it]/samples-(sums[it]/samples)*(sums[it]/samples)>=25 }
    }
}

/** Both formats preserve pixels; choose from actual bytes rather than assuming WebP is smaller. */
object MerchantIconEncoding {
    fun encode(bitmap: Bitmap): ByteArray? {
        fun compressed(format: Bitmap.CompressFormat): ByteArray? = ByteArrayOutputStream().use { output ->
            if (bitmap.compress(format, 100, output)) output.toByteArray() else null
        }
        val png = compressed(Bitmap.CompressFormat.PNG)
        val webp = compressed(Bitmap.CompressFormat.WEBP_LOSSLESS)
        return listOfNotNull(png, webp).minByOrNull { it.size }
    }

    fun mime(bytes: ByteArray): String? = when {
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)) -> "image/png"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> null
    }
}
