package net.ankio.ledger

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import net.ankio.ledger.ocr.*
import net.ankio.ledger.core.*
import net.ankio.ledger.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class IconLocationRegressionTest {
    @Test fun rejectedHighHeaderHintsFromTheDiagnosticRecoverTheUniqueNearbyRoundAvatar() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val hints=listOf(MerchantIconRegion(555.56,70.0,644.93,111.11),MerchantIconRegion(451.69,68.33,548.31,112.78))
        for(name in listOf("wechat-yellow-avatar.png","wechat-etc-avatar.png")) {
            val original=context.assets.open(name).use { BitmapFactory.decodeStream(it) }
            val scaled=Bitmap.createScaledBitmap(original,original.width*1800/original.height,1800,true)
            try {
                val jpeg=ByteArrayOutputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG,88,it);it.toByteArray() }
                for(hint in hints) {
                    val result=MerchantIconCropper.evaluate(jpeg,hint)
                    assertNotNull("$name: ${result.details}",result.bytes)
                    val icon=BitmapFactory.decodeByteArray(result.bytes,0,result.bytes!!.size)
                    try {
                        for(x in listOf(24,104)) {
                            assertTrue("The complete avatar must retain both sides",minOf(Color.red(icon.getPixel(x,64)),Color.green(icon.getPixel(x,64)),Color.blue(icon.getPixel(x,64)))<200)
                        }
                    } finally { icon.recycle() }
                }
            } finally { scaled.recycle();original.recycle() }
        }
    }
    private fun image(block:(Canvas)->Unit):ByteArray {
        val bitmap=Bitmap.createBitmap(400,800,Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.WHITE);block(Canvas(bitmap))
            ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it);it.toByteArray() }
        } finally { bitmap.recycle() }
    }
    @Test fun headerRecoveryRejectsMultipleAvatarsAndSquareTextBlocks() {
        val hint=MerchantIconRegion(450.0,68.75,550.0,106.25)
        val ambiguous=image { canvas ->
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(240,180,0) }
            canvas.drawCircle(175f,145f,20f,paint);canvas.drawCircle(225f,145f,20f,paint)
        }
        assertNull("Two possible header avatars must not be guessed",MerchantIconCropper.crop(ambiguous,hint))
        val square=image { canvas -> canvas.drawRect(180f,125f,220f,165f,Paint().apply { color=Color.BLACK }) }
        assertNull("A rectangular block outside the original hint is not a circular avatar",MerchantIconCropper.crop(square,hint))
    }
    @Test fun offsetBoxFindsTheCompleteNearbyAvatarInsteadOfSavingACrescent() {
        val jpeg=image { it.drawCircle(200f,120f,40f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(240,180,0) }) }
        // The model box is 60 pixels to the right: only the avatar's edge overlaps.
        val result=MerchantIconCropper.evaluate(jpeg,MerchantIconRegion(550.0,100.0,750.0,200.0))
        assertNotNull(result.bytes)
        val icon=BitmapFactory.decodeByteArray(result.bytes,0,result.bytes!!.size)
        try {
            fun coloured(x:Int,y:Int)=Color.blue(icon.getPixel(x,y))<80
            assertTrue("Avatar center must be centered after local refinement",coloured(64,64))
            assertTrue("Both sides of the avatar must be retained",coloured(22,64)&&coloured(106,64))
            assertTrue(coloured(64,22)&&coloured(64,106))
        } finally { icon.recycle() }
    }
    @Test fun nearbyTextMustNotBeExpandedIntoAMerchantAvatar() {
        val jpeg=image { canvas ->
            canvas.drawRect(220f,90f,224f,150f,Paint().apply { color=Color.BLACK })
        }
        assertNull("A thin fragment must not become a cached merchant icon",
            MerchantIconCropper.crop(jpeg,MerchantIconRegion(550.0,100.0,750.0,200.0)))
    }
    @Test fun largeLocalizationErrorDoesNotSearchAnUnrelatedAvatarElsewhereOnScreen() {
        val jpeg=image { it.drawCircle(80f,400f,40f,Paint().apply { color=Color.RED }) }
        assertNull(MerchantIconCropper.crop(jpeg,MerchantIconRegion(550.0,100.0,750.0,200.0)))
    }
    @Test fun suppliedWechatAvatarsKeepFullCircleWhenTheHintIsShifted() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        for(name in listOf("wechat-yellow-avatar.png","wechat-etc-avatar.png")) {
            val original=context.assets.open(name).use { BitmapFactory.decodeStream(it) }
            val scaled=Bitmap.createScaledBitmap(original,original.width*1800/original.height,1800,true)
            try {
                val jpeg=ByteArrayOutputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG,88,it);it.toByteArray() }
                // Both supplied originals place the complete avatar at x389..495, y265..372.
                // Shift the hint to x470..576 to reproduce a left-edge sliver on the output.
                val hint=MerchantIconRegion(470.0*1000/original.width,265.0*1000/original.height,
                    576.0*1000/original.width,372.0*1000/original.height)
                val result=MerchantIconCropper.evaluate(jpeg,hint)
                assertNotNull(name+": "+result.details,result.bytes)
                val icon=BitmapFactory.decodeByteArray(result.bytes,0,result.bytes!!.size)
                try {
                    // Logos have white lettering; check coloured coverage along each side,
                    // rather than requiring an arbitrary letter pixel to be coloured.
                    for(side in listOf(Rect(20,40,30,88),Rect(98,40,108,88),Rect(40,20,88,30),Rect(40,98,88,108))) {
                        var count=0
                        for(y in side.top until side.bottom)for(x in side.left until side.right) {
                            val c=icon.getPixel(x,y)
                            if(minOf(Color.red(c),Color.green(c),Color.blue(c))<180)count++
                        }
                        assertTrue("$name must retain every side of its circular silhouette ($side: $count)",count>40)
                    }
                    val output=java.io.File(ApplicationProvider.getApplicationContext<LedgerApp>().cacheDir,name)
                    output.writeBytes(result.bytes!!)
                } finally { icon.recycle() }
            } finally { scaled.recycle();original.recycle() }
        }
    }
    @Test fun newCompleteAvatarRepairsTheSharedFragmentWithoutChangingExistingBills() {
        val app=ApplicationProvider.getApplicationContext<LedgerApp>()
        val name="repair-icon-${java.util.UUID.randomUUID()}.db"
        val store=LedgerStore(app,name)
        val bitmap=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            Canvas(bitmap).drawOval(-55f,15f,15f,113f,Paint().apply { color=Color.YELLOW })
            val fragment=MerchantIconEncoding.encode(bitmap)!!
            val capture=Capture(Source.OCR,"com.tencent.mm","fixture")
            val bill=ParsedBill(1L,Direction.EXPENSE,"图标修复商户","其他",System.currentTimeMillis(),"icon-repair-transaction",false,"fixture",false)
            val first=(store.ingest(capture,bill,merchantIcon=fragment) as IngestResult.Saved).record
            val before=store.get(first.id)
            val oldRevision=store.merchantIconRevisions().values.single()
            bitmap.eraseColor(Color.WHITE)
            Canvas(bitmap).drawCircle(64f,64f,52f,Paint().apply { color=Color.YELLOW })
            val complete=MerchantIconEncoding.encode(bitmap)!!
            assertEquals(IngestResult.DuplicateSkipped,store.ingest(capture,bill,merchantIcon=complete))
            assertArrayEquals("A bad shared crop must not win forever",complete,store.merchantIcon(first.id))
            assertEquals(before,store.get(first.id))
            assertEquals(1,store.all().size)
            assertNotEquals(oldRevision,store.merchantIconRevisions().values.single())
        } finally { store.close();app.deleteDatabase(name);bitmap.recycle() }
    }
}
