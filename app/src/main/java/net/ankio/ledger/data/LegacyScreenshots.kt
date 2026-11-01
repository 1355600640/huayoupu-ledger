package net.ankio.ledger.data

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException

/** Chunked reads support old 5 MiB BLOBs larger than Android's default cursor window. */
internal object LegacyScreenshots {
    fun read(db:SQLiteDatabase,table:String,idColumn:String,id:Long,size:Int):ByteArray {
        require(table in setOf("capture_images","tasks") && idColumn in setOf("record_id","id"))
        require(size in 1..5*1024*1024)
        val result=ByteArray(size)
        var offset=0
        while(offset<size) {
            val chunk=db.rawQuery("SELECT substr(image,?,?) FROM $table WHERE $idColumn=?",
                arrayOf((offset+1).toString(),minOf(size-offset,256*1024).toString(),id.toString())).use {
                check(it.moveToFirst());it.getBlob(0)
            }
            check(chunk.isNotEmpty());chunk.copyInto(result,offset);offset+=chunk.size
        }
        return result
    }
    /** Only a real, sizable legacy migration needs physical compaction; normal opens never VACUUM. */
    fun compact(db:SQLiteDatabase,migratedBytes:Long) {
        if(migratedBytes<1024*1024 || db.inTransaction())return
        try { db.execSQL("VACUUM") } catch(_:SQLiteException) {
            // A concurrent connection can be busy. Free pages remain reusable without data loss.
        }
    }
}
