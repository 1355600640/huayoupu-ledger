package net.ankio.ledger.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Refines only a bounded neighbourhood of the model hint, never searches the full bill. */
internal object IconLocalization {
    private fun background(pixels:IntArray,w:Int,h:Int):Int {
        val edge=ArrayList<Int>()
        for(x in 0 until w) { edge.add(pixels[x]);edge.add(pixels[(h-1)*w+x]) }
        for(y in 1 until h-1) { edge.add(pixels[y*w]);edge.add(pixels[y*w+w-1]) }
        fun median(channel:(Int)->Int)=edge.map(channel).sorted()[edge.size/2]
        return Color.rgb(median(Color::red),median(Color::green),median(Color::blue))
    }
    private fun foreground(pixel:Int,bg:Int)=Color.alpha(pixel)>128 && maxOf(
        abs(Color.red(pixel)-Color.red(bg)),abs(Color.green(pixel)-Color.green(bg)),abs(Color.blue(pixel)-Color.blue(bg)))>36

    fun refine(bitmap:Bitmap,hint:Rect):Rect? = nearby(bitmap,hint) ?: headerCircle(bitmap,hint)

    private fun nearby(bitmap:Bitmap,hint:Rect):Rect? {
        val size=maxOf(hint.width(),hint.height())
        val reach=ceil(size*0.95).toInt().coerceAtLeast(24)
        val search=Rect((hint.left-reach).coerceAtLeast(0),(hint.top-reach).coerceAtLeast(0),
            (hint.right+reach).coerceAtMost(bitmap.width),(hint.bottom+reach).coerceAtMost(bitmap.height))
        val w=search.width();val h=search.height()
        val pixels=IntArray(w*h);bitmap.getPixels(pixels,0,w,search.left,search.top,w,h)
        val bg=background(pixels,w,h)
        val mask=ByteArray(pixels.size) { if(foreground(pixels[it],bg))1 else 0 }
        val queue=IntArray(pixels.size)
        data class Candidate(val rect:Rect,val score:Double)
        val candidates=ArrayList<Candidate>()
        for(start in mask.indices) {
            if(mask[start].toInt()!=1)continue
            var head=0;var tail=1;queue[0]=start;mask[start]=0
            var left=w;var top=h;var right=0;var bottom=0
            while(head<tail) {
                val index=queue[head++];val x=index%w;val y=index/w
                left=minOf(left,x);top=minOf(top,y);right=maxOf(right,x);bottom=maxOf(bottom,y)
                for(ny in maxOf(0,y-1)..minOf(h-1,y+1))for(nx in maxOf(0,x-1)..minOf(w-1,x+1)) {
                    val next=ny*w+nx
                    if(mask[next].toInt()==1) { mask[next]=0;queue[tail++]=next }
                }
            }
            // A component crossing the search boundary is incomplete, and may be bill text.
            if(left==0||top==0||right==w-1||bottom==h-1)continue
            val rect=Rect(left+search.left,top+search.top,right+search.left+1,bottom+search.top+1)
            val rw=rect.width().toDouble();val rh=rect.height().toDouble()
            if(rw/rh !in 0.65..1.54 || rw !in size*0.5..size*1.7 || rh !in size*0.5..size*1.7)continue
            if(tail<rw*rh*0.12 || !Rect.intersects(rect,hint))continue
            val distance=kotlin.math.hypot(rect.exactCenterX().toDouble()-hint.exactCenterX(),rect.exactCenterY().toDouble()-hint.exactCenterY())
            if(distance>size*0.95)continue
            candidates.add(Candidate(rect,1.0-distance/(size*2.0)))
        }
        val ranked=candidates.sortedByDescending { it.score }
        if(ranked.size>1 && ranked[0].score<ranked[1].score*1.2)return null
        // Disconnected lettering can still be a valid logo. It must occupy the hint in
        // both dimensions; a thin crescent/stripe at one edge must never pass this fallback.
        var left=hint.right;var right=hint.left;var top=hint.bottom;var bottom=hint.top;var count=0
        for(y in hint.top until hint.bottom)for(x in hint.left until hint.right) {
            if(!foreground(pixels[(y-search.top)*w+x-search.left],bg))continue
            count++;left=minOf(left,x);right=maxOf(right,x+1);top=minOf(top,y);bottom=maxOf(bottom,y+1)
        }
        val complete=count>=hint.width()*hint.height()*0.08 && right-left>=hint.width()*0.55 && bottom-top>=hint.height()*0.55
        val ratio=(right-left).toDouble()/(bottom-top)
        // A complete emblem inside the hint must not discard its detached letters.
        if(complete && ratio in 0.65..1.54 && (ranked.isEmpty() || hint.contains(ranked[0].rect)))return hint
        return ranked.firstOrNull()?.rect
    }

    /** Some vision models place a header avatar too high. Recover only an isolated,
     * complete circle near that hint; never scan the bill body or guess from text. */
    private fun headerCircle(bitmap:Bitmap,hint:Rect):Rect? {
        val size=maxOf(hint.width(),hint.height())
        if(bitmap.height<bitmap.width*1.4 || hint.exactCenterX() !in bitmap.width*.32..bitmap.width*.70 ||
            hint.exactCenterY() !in bitmap.height*.04..bitmap.height*.16)return null
        val reach=ceil(size*2.5).toInt()
        val search=Rect(maxOf(0,hint.left-reach),maxOf((bitmap.height*.06).toInt(),hint.top-reach),
            minOf(bitmap.width,hint.right+reach),minOf((bitmap.height*.30).toInt(),hint.bottom+reach))
        val w=search.width();val h=search.height()
        if(w<=0||h<=0)return null
        val pixels=IntArray(w*h);bitmap.getPixels(pixels,0,w,search.left,search.top,w,h)
        val bg=background(pixels,w,h)
        val mask=ByteArray(pixels.size) { if(foreground(pixels[it],bg))1 else 0 }
        val queue=IntArray(pixels.size);val found=ArrayList<Rect>()
        for(start in mask.indices) {
            if(mask[start].toInt()!=1)continue
            var head=0;var tail=1;queue[0]=start;mask[start]=0
            var l=w;var t=h;var r=0;var b=0
            while(head<tail) {
                val index=queue[head++];val x=index%w;val y=index/w
                l=minOf(l,x);t=minOf(t,y);r=maxOf(r,x);b=maxOf(b,y)
                for(ny in maxOf(0,y-1)..minOf(h-1,y+1))for(nx in maxOf(0,x-1)..minOf(w-1,x+1)) {
                    val next=ny*w+nx
                    if(mask[next].toInt()==1) { mask[next]=0;queue[tail++]=next }
                }
            }
            val rw=r-l+1;val rh=b-t+1
            if(l==0||t==0||r==w-1||b==h-1 || rw.toDouble()/rh !in .9..1.1 ||
                rw.toDouble() !in size*.65..size*1.55 || tail<rw*rh*.50 || tail>rw*rh*.86)continue
            val cx=(l+r)/2.0;val cy=(t+b)/2.0
            if(abs(cx+search.left-hint.exactCenterX())>size*1.6)continue
            // Check the circular silhouette, ignoring lettering inside it.
            var hit=0;var outside=0
            for(i in 0 until tail) {
                val nx=(queue[i]%w-cx)/(rw/2.0);val ny=(queue[i]/w-cy)/(rh/2.0)
                if(nx*nx+ny*ny>1.12)outside++
            }
            for(i in 0 until 24) {
                val angle=i*Math.PI/12
                val x=(cx+kotlin.math.cos(angle)*rw*.43).toInt()
                val y=(cy+kotlin.math.sin(angle)*rh*.43).toInt()
                if(foreground(pixels[y*w+x],bg))hit++
            }
            if(hit>=21 && outside<tail*.02)found.add(Rect(l+search.left,t+search.top,r+search.left+1,b+search.top+1))
        }
        return found.singleOrNull()
    }

    /** Only replaces an old asset when it has a clear fragment/empty-crop signature. */
    fun usableThumbnail(bytes:ByteArray):Boolean {
        if(bytes.size>128*1024)return false
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth !in 16..128 || bounds.outHeight !in 16..128)return false
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:return false
        return try {
            val w=bitmap.width;val h=bitmap.height;val pixels=IntArray(w*h)
            bitmap.getPixels(pixels,0,w,0,0,w,h);val bg=background(pixels,w,h)
            var l=w;var r=0;var t=h;var b=0;var count=0
            pixels.forEachIndexed { i,pixel -> if(foreground(pixel,bg)) { val x=i%w;val y=i/w;count++;l=minOf(l,x);r=maxOf(r,x+1);t=minOf(t,y);b=maxOf(b,y+1) } }
            count>w*h*0.08 && r-l>w*0.4 && b-t>h*0.4 &&
                (l+r)/2.0 in w*0.3..w*0.7 && (t+b)/2.0 in h*0.3..h*0.7
        } finally { bitmap.recycle() }
    }
}
