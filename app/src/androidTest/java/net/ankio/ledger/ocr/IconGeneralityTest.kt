package net.ankio.ledger.ocr

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Unknown synthetic marks: vary appearance and placement independently of merchant identity. */
@RunWith(AndroidJUnit4::class)
class IconGeneralityTest {
    private val merchant="新商户(分店)"

    private fun receipt(width:Int,height:Int,cx:Float,top:Float,background:Int,ink:Int,shape:Int):Pair<Bitmap,LocalOcrResult> {
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply { eraseColor(background) }
        val canvas=Canvas(bitmap);val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=ink }
        val s=width/800f;val x=cx*width;val y=top*height
        canvas.save();canvas.translate(x,y);canvas.scale(s,s)
        when(shape) {
            0 -> { p.style=Paint.Style.STROKE;p.strokeWidth=4f;canvas.drawCircle(0f,-85f,32f,p) }
            1 -> { for(i in 0..4)canvas.drawRect(-65f+i*27,-98f,-47f+i*27,-77f,p) }
            2 -> { canvas.drawOval(-15f,-145f,15f,-80f,p);canvas.drawRect(-12f,-68f,12f,-35f,p) }
            3 -> { val path=Path().apply { moveTo(-48f,-80f);lineTo(0f,-130f);lineTo(48f,-80f);lineTo(0f,-40f);close() };canvas.drawPath(path,p) }
        }
        canvas.restore()
        fun box(l:Float,t:Float,r:Float,b:Float)=Rect((x+l*s).toInt(),(y+t*s).toInt(),(x+r*s).toInt(),(y+b*s).toInt())
        val lines=listOf(OcrLine(merchant,box(-110f,0f,110f,38f)),
            OcrLine("-35.60",box(-100f,85f,100f,140f)),OcrLine("支付成功",box(-90f,210f,90f,240f)))
        return bitmap to LocalOcrResult(width,height,0,lines)
    }

    @Test fun unfamiliarMarksSurviveBackgroundShapeScaleAndPositionChanges() {
        val failures=ArrayList<String>();var cases=0
        for((w,h) in listOf(480 to 960,800 to 1600,1080 to 1800))
            for(cx in listOf(.25f,.5f,.75f))for(top in listOf(.27f,.54f))
                for((bg,ink) in listOf(Color.WHITE to Color.rgb(45,115,90),
                    Color.rgb(235,235,235) to Color.rgb(205,205,205),Color.rgb(28,30,35) to Color.rgb(100,210,170)))
                    for(shape in 0..3) {
                        cases++
                        val (bitmap,ocr)=receipt(w,h,cx,top,bg,ink,shape)
                        try {
                            val located=LocalIconLocator.locate(bitmap,ocr,merchant);val box=located.selected?.box
                            if(box==null)failures+="$w/$cx/$top/$bg/shape$shape: ${located.reason}"
                            else {
                                val region=MerchantIconRegion(box.left*1000.0/w,box.top*1000.0/h,box.right*1000.0/w,box.bottom*1000.0/h)
                                val result=MerchantIconCropper.evaluate(bitmap,region,verifiedBox=true)
                                if(result.bytes==null)failures+="$w/shape$shape: ${result.reason}"
                                val center=(cx*w).toInt();val y=top*h;val scale=w/800f
                                val expected=when(shape) {
                                    0 -> Rect(center-(34*scale).toInt(),(y-119*scale).toInt(),center+(34*scale).toInt(),(y-51*scale).toInt())
                                    1 -> Rect((center-65*scale).toInt(),(y-98*scale).toInt(),(center+61*scale).toInt(),(y-77*scale).toInt())
                                    2 -> Rect((center-15*scale).toInt(),(y-145*scale).toInt(),(center+15*scale).toInt(),(y-35*scale).toInt())
                                    else -> Rect((center-48*scale).toInt(),(y-130*scale).toInt(),(center+48*scale).toInt(),(y-40*scale).toInt())
                                }
                                val pixelBounds=Rect(box).apply { inset(-2,-2) }
                                if(!pixelBounds.contains(expected))failures+="$w/shape$shape clipped: $box vs $expected"
                            }
                        } finally { bitmap.recycle() }
                    }
        assertTrue("${failures.size}/$cases variants failed:\n${failures.take(20).joinToString("\n")}",failures.isEmpty())
    }

    @Test fun newBackgroundSupportStillRejectsBlankStatusTransferAndUnrelatedNames() {
        for(bg in listOf(Color.WHITE,Color.rgb(28,30,35))) {
            val (bitmap,ocr)=receipt(800,1600,.5f,.27f,bg,Color.rgb(100,210,170),1)
            try {
                assertNull(LocalIconLocator.locate(bitmap,ocr,"另一商户").selected)
                val status=ocr.copy(lines=ocr.lines+OcrLine("支付成功",Rect(330,330,470,365)))
                assertNull("Status copy must never be accepted as lettering",LocalIconLocator.locate(bitmap,status,merchant).selected)
                val transfer=ocr.copy(lines=ocr.lines+OcrLine("转账给",Rect(100,800,200,840)))
                assertNull(LocalIconLocator.locate(bitmap,transfer,merchant).selected)
                bitmap.eraseColor(bg)
                assertNull("Blank image on either theme",LocalIconLocator.locate(bitmap,ocr,merchant).selected)
            } finally { bitmap.recycle() }
        }
    }

    @Test fun anExplicitStoreRowBindsDetachedMarksToItsLabelAtDifferentMargins() {
        for(left in listOf(160,220,300))for(bg in listOf(Color.WHITE,Color.rgb(28,30,35))) {
            val bitmap=Bitmap.createBitmap(800,1600,Bitmap.Config.ARGB_8888).apply { eraseColor(bg) }
            try {
                val p=Paint().apply { color=Color.rgb(80,150,110) };val canvas=Canvas(bitmap)
                for(i in 0..4)canvas.drawRect((left-105+i*15).toFloat(),466f,(left-98+i*15).toFloat(),486f,p)
                val ocr=LocalOcrResult(800,1600,0,listOf(OcrLine(merchant,Rect(left,450,left+220,492)),
                    OcrLine("进店逛逛 >",Rect(650,453,770,490)),OcrLine("实付款 ¥35.60",Rect(500,900,760,950))))
                val box=requireNotNull(LocalIconLocator.locate(bitmap,ocr,merchant).selected).box
                assertTrue("Follow store label at $left: $box",Rect(box).apply { inset(-1,-1) }.contains(Rect(left-105,466,left-38,486)))
            } finally { bitmap.recycle() }
        }
    }

    @Test fun logoBackgroundIsMeasuredInsideItsCardRatherThanAtThePageEdges() {
        val (bitmap,ocr)=receipt(800,1600,.5f,.27f,Color.rgb(235,235,235),Color.rgb(205,205,205),1)
        try {
            val canvas=Canvas(bitmap);val white=Paint().apply { color=Color.WHITE }
            canvas.drawRect(0f,0f,265f,1600f,white);canvas.drawRect(535f,0f,800f,1600f,white)
            assertNotNull(LocalIconLocator.locate(bitmap,ocr,merchant).selected)
        } finally { bitmap.recycle() }
    }

    @Test fun twoIndependentImagesAndPhotographicBackgroundsAreStillRejected() {
        val (bitmap,ocr)=receipt(800,1600,.5f,.27f,Color.WHITE,Color.GREEN,1)
        try {
            bitmap.eraseColor(Color.WHITE)
            val canvas=Canvas(bitmap);val p=Paint().apply { color=Color.rgb(40,145,110) }
            canvas.drawCircle(360f,350f,28f,p);canvas.drawCircle(440f,350f,28f,p)
            assertEquals("ambiguous_candidates",LocalIconLocator.locate(bitmap,ocr,merchant).reason)
            for(y in 0 until bitmap.height step 10) {
                p.color=Color.rgb((y*17)%190,(y*23)%190,(y*31)%190)
                canvas.drawRect(0f,y.toFloat(),800f,y+10f,p)
            }
            assertNull(LocalIconLocator.locate(bitmap,ocr,merchant).selected)
        } finally { bitmap.recycle() }
    }
}
