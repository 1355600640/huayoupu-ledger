package net.ankio.ledger.ocr

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankio.ledger.ocr.MerchantIconCropper
import net.ankio.ledger.ocr.MerchantIconRegion
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class LocalHeaderIconTest {
    @Test fun chineseLetteringInsideAnIsolatedHeaderLogoDoesNotRejectTheImage() {
        val image=Bitmap.createBitmap(800,1800,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val canvas=Canvas(image);val p=Paint().apply { color=Color.rgb(192,36,35) }
            canvas.drawCircle(400f,290f,45f,p)
            canvas.drawRect(366f,342f,434f,357f,p)
            val text=listOf(OcrLine("合成茶饮",Rect(310,390,490,428)),OcrLine("-30.00",Rect(300,477,500,535)),
                OcrLine("支付成功",Rect(235,710,350,745)),OcrLine("合成收银",Rect(366,342,434,357)))
            val result=LocalIconLocator.locate(image,LocalOcrResult(800,1800,0,text),"合成茶饮")
            val candidate=requireNotNull(result.selected) { result.json().toString() }
            assertTrue("Include the emblem and its detached Chinese caption",candidate.box.contains(Rect(355,245,445,357)))
            val box=candidate.box
            assertNotNull(MerchantIconCropper.evaluate(image,MerchantIconRegion(box.left*1.25,box.top/1.8,box.right*1.25,box.bottom/1.8),verifiedBox=true).bytes)
        } finally { image.recycle() }
    }

    @Test fun disconnectedStoreCardLogoIsBoundToTheTitleInsteadOfItsSocialAvatars() {
        val image=Bitmap.createBitmap(800,1800,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val canvas=Canvas(image);val p=Paint().apply { color=Color.rgb(175,25,45) }
            canvas.drawOval(40f,232f,152f,265f,p);canvas.drawOval(40f,310f,152f,350f,p)
            // Customer avatars are separate small squares beside the like count.
            canvas.drawRect(185f,310f,215f,340f,p);canvas.drawRect(220f,310f,250f,340f,p)
            val text=listOf(OcrLine("合成饭馆",Rect(185,249,325,299)),OcrLine("等2000+人喜欢",Rect(262,314,432,343)),
                OcrLine("合成饭馆",Rect(345,678,475,720)),OcrLine("-43.00",Rect(327,607,499,662)),
                OcrLine("支付成功",Rect(231,832,346,869)),OcrLine("合成",Rect(51,251,143,296)),OcrLine("招牌米饭",Rect(52,290,145,310)))
            val result=LocalIconLocator.locate(image,LocalOcrResult(800,1800,0,text),"合成饭馆")
            assertTrue(result.json().toString(),result.selected?.box?.contains(Rect(40,232,152,350))==true)
        } finally { image.recycle() }
    }

    @Test fun wideLetteredHeaderLogoKeepsItsWholeMarkInASquareThumbnail() {
        val image=Bitmap.createBitmap(800,1800,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val canvas=Canvas(image);val p=Paint().apply { color=Color.rgb(65,130,75) }
            canvas.drawCircle(360f,292f,14f,p)
            for(x in 380..432 step 13)canvas.drawRect(x.toFloat(),280f,x+9f,305f,p)
            val text=listOf(OcrLine("合成快餐(分店)",Rect(190,390,635,428)),OcrLine("-22.80",Rect(300,477,500,535)),
                OcrLine("支付成功",Rect(235,710,350,745)),OcrLine("合成支付",Rect(380,280,445,298)),OcrLine("PAY.COM",Rect(380,298,445,310)))
            val result=LocalIconLocator.locate(image,LocalOcrResult(800,1800,0,text),"合成快餐(分店)")
            val box=requireNotNull(result.selected) { result.json().toString() }.box
            assertTrue("Both the symbol and all wordmark letters must survive",box.contains(Rect(346,278,441,306)))
            assertNotNull(MerchantIconCropper.evaluate(image,MerchantIconRegion(box.left*1.25,box.top/1.8,box.right*1.25,box.bottom/1.8),verifiedBox=true).bytes)
        } finally { image.recycle() }
    }
    private val merchant="合成餐厅"
    private val lines=listOf(OcrLine(merchant,Rect(150,270,450,300)),
        OcrLine("-15.58",Rect(220,340,380,400)),OcrLine("支付成功",Rect(220,470,380,500)))
    private fun image()=Bitmap.createBitmap(600,1200,Bitmap.Config.ARGB_8888).also { bitmap ->
        bitmap.eraseColor(Color.WHITE)
        val canvas=Canvas(bitmap)
        // Separate thin strokes, together 70 x 50; no connected square silhouette.
        val p=Paint().apply { color=Color.rgb(75,80,85);strokeWidth=3f }
        for(x in 265..305 step 7)canvas.drawLine(x.toFloat(),190f,x+24f,236f,p)
        p.color=Color.rgb(210,140,70)
        canvas.drawLine(313f,188f,335f,205f,p);canvas.drawLine(335f,205f,320f,220f,p)
    }
    @Test fun disconnectedHeaderLogoIsAnchoredAndSurvivesTheRealCropper() {
        val image=image()
        try {
            val result=LocalIconLocator.locate(image,LocalOcrResult(600,1200,0,lines),merchant)
            val candidate=requireNotNull(result.selected) { result.json().toString() }
            assertEquals("located_by_header_image_cell",result.reason);assertEquals(merchant,candidate.anchor)
            assertTrue(candidate.box.contains(300,210))
            val bytes=ByteArrayOutputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray() }
            val box=candidate.box
            val crop=MerchantIconCropper.evaluate(bytes,MerchantIconRegion(box.left/0.6,box.top/1.2,box.right/0.6,box.bottom/1.2))
            assertNotNull(crop.details,crop.bytes)
        } finally { image.recycle() }
    }
    @Test fun unrelatedMerchantStatusTextAndGenericTransferCannotSupplyAnIcon() {
        val image=image()
        try {
            assertNull(LocalIconLocator.locate(image,LocalOcrResult(600,1200,0,lines),"其他商户").selected)
            val status=lines+OcrLine("支付成功",Rect(265,188,335,238))
            assertNull(LocalIconLocator.locate(image,LocalOcrResult(600,1200,0,status),merchant).selected)
            val transfer=lines+OcrLine("转账给",Rect(100,600,200,630))
            assertNull(LocalIconLocator.locate(image,LocalOcrResult(600,1200,0,transfer),merchant).selected)
        } finally { image.recycle() }
    }
    @Test fun emptyImageCellNeverCreatesAnIcon() {
        val image=Bitmap.createBitmap(600,1200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try { assertNull(LocalIconLocator.locate(image,LocalOcrResult(600,1200,0,lines),merchant).selected) }
        finally { image.recycle() }
    }

    private fun letteredLogo()=Bitmap.createBitmap(600,1200,Bitmap.Config.ARGB_8888).also { bitmap ->
        bitmap.eraseColor(Color.WHITE)
        val canvas=Canvas(bitmap)
        val paint=Paint().apply { color=Color.rgb(0,145,75) }
        canvas.drawOval(270f,190f,330f,232f,paint)
        paint.color=Color.BLACK
        for(x in 270..318 step 16)canvas.drawRect(x.toFloat(),237f,x+10f,250f,paint)
    }

    @Test fun embeddedLogoLettersAndAnUnrelatedToastAboveRemainLocalizable() {
        val image=letteredLogo()
        try {
            Canvas(image).drawRoundRect(210f,110f,390f,160f,12f,12f,Paint().apply { color=Color.DKGRAY })
            val ocr=LocalOcrResult(600,1200,0,lines+listOf(
                OcrLine("ABCD",Rect(270,237,328,250)),OcrLine("已记账¥15.58",Rect(230,115,370,140))))
            val result=LocalIconLocator.locate(image,ocr,merchant)
            val candidate=requireNotNull(result.selected) { result.json().toString() }
            assertEquals(merchant,candidate.anchor)
            assertTrue("The complete emblem and lettering belong to the icon",candidate.box.contains(Rect(270,190,330,250)))
            assertTrue("The toast is outside the icon",candidate.box.top>160)
        } finally { image.recycle() }
    }

    @Test fun refinementRetainsDetachedLetteringBelowTheMainEmblem() {
        val image=letteredLogo()
        try {
            val refined=requireNotNull(IconLocalization.refine(image,Rect(265,185,335,255)))
            assertTrue("The emblem must not win over its detached lettering: $refined",refined.contains(Rect(270,190,330,250)))
            val crop=MerchantIconCropper.evaluate(image,MerchantIconRegion(265/0.6,185/1.2,335/0.6,255/1.2))
            val bytes=requireNotNull(crop.bytes) { crop.details }
            val icon=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            try {
                var letterPixels=0
                for(y in 90 until 112)for(x in 20 until 108)if(Color.red(icon.getPixel(x,y))<60)letterPixels++
                assertTrue("Detached black letters must reach the saved thumbnail",letterPixels>120)
            } finally { icon.recycle() }
        } finally { image.recycle() }
    }

    @Test fun ordinaryHeaderCopyCannotMasqueradeAsLogoLettering() {
        val image=letteredLogo()
        try {
            for(text in listOf("支付成功","订单详情","微信支付","12345678901234567890","1234","30.00")) {
                val ocr=LocalOcrResult(600,1200,0,lines+OcrLine(text,Rect(265,235,335,253)))
                assertNull(text,LocalIconLocator.locate(image,ocr,merchant).selected)
            }
        } finally { image.recycle() }
    }

    @Test fun aSquareConnectedEmblemDoesNotHideItsDetachedLogoLetters() {
        val image=letteredLogo()
        try {
            val canvas=Canvas(image)
            canvas.drawRect(260f,160f,340f,235f,Paint().apply { color=Color.WHITE })
            canvas.drawCircle(300f,198f,30f,Paint().apply { color=Color.rgb(0,145,75) })
            val result=LocalIconLocator.locate(image,LocalOcrResult(600,1200,0,
                lines+OcrLine("ABCD",Rect(270,237,328,250))),merchant)
            val box=requireNotNull(result.selected) { result.json().toString() }.box
            assertTrue("The locator must include letters even when the emblem alone is square: $box",box.contains(Rect(270,168,330,250)))
        } finally { image.recycle() }
    }
}
