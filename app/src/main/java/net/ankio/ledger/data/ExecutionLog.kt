package net.ankio.ledger.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** Explicitly enabled, bounded, local troubleshooting session; never sent automatically. */
class ExecutionLog(context:Context,name:String="execution-log.db",private val now:()->Long=System::currentTimeMillis):SQLiteOpenHelper(context,name,null,1) {
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE session (id INTEGER PRIMARY KEY, started INTEGER NOT NULL, until_ms INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, at_ms INTEGER NOT NULL, channel TEXT NOT NULL, stage TEXT NOT NULL, outcome TEXT NOT NULL, details TEXT NOT NULL)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int)=Unit
    @Synchronized fun active():Boolean=runCatching { readableDatabase.rawQuery("SELECT started,until_ms FROM session WHERE id=1",null).use {
        it.moveToFirst() && now() in it.getLong(0) until it.getLong(1) && it.getLong(1)-it.getLong(0)<=DURATION
    } }.getOrDefault(false)
    @Synchronized fun start() {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("events",null,null)
            writableDatabase.insertWithOnConflict("session",null,ContentValues().apply { put("id",1);put("started",now());put("until_ms",now()+DURATION) },SQLiteDatabase.CONFLICT_REPLACE)
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        event("system","debug","started")
    }
    @Synchronized fun stop() { event("system","debug","stopped");writableDatabase.execSQL("UPDATE session SET until_ms=0 WHERE id=1") }
    @Synchronized fun clear() { writableDatabase.delete("events",null,null);writableDatabase.delete("session",null,null) }
    @Synchronized fun event(channel:String,stage:String,outcome:String,details:JSONObject=JSONObject()) {
        if(!active())return
        require(channel in setOf("system","ocr","image","model","payment_auto"))
        runCatching {
        // Callers pass only deliberately selected fields, never exceptions/HTTP payloads.
        writableDatabase.insertOrThrow("events",null,ContentValues().apply {
            put("at_ms",now());put("channel",channel);put("stage",stage.take(80));put("outcome",outcome.take(120));put("details",details.toString().takeIf { it.length<=12_000 }?:"{\"omitted\":\"details_too_large\"}")
        })
        writableDatabase.execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT $MAX_EVENTS)")
        } // A diagnostic write failure must never block a transaction or permission change.
    }
    @Synchronized fun snapshot():JSONObject {
        // Expired samples are removed after one day, including on the next export/open.
        writableDatabase.delete("events","at_ms < ?",arrayOf((now()-86_400_000L).toString()))
        val session=readableDatabase.rawQuery("SELECT started,until_ms FROM session WHERE id=1",null).use {
            if(it.moveToFirst())JSONObject().put("startedAt",it.getLong(0)).put("until",it.getLong(1)) else JSONObject()
        }
        val events=readableDatabase.rawQuery("SELECT at_ms,channel,stage,outcome,details FROM events ORDER BY id",null).use { rows ->
            JSONArray().apply { while(rows.moveToNext())put(JSONObject().put("at",rows.getLong(0)).put("channel",rows.getString(1)).put("stage",rows.getString(2)).put("outcome",rows.getString(3)).put("details",JSONObject(rows.getString(4)))) }
        }
        return session.put("active",active()).put("limit",MAX_EVENTS).put("events",events)
    }
    companion object { const val DURATION=15*60_000L;const val MAX_EVENTS=300 }
}
