package net.ankio.ledger.data

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.ankio.ledger.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerQuery056Test {
    @Test fun boundedReadsKeepCompleteTotalsSearchAndDeletedExclusionAtScale() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="query-056-${System.nanoTime()}.db";val store=LedgerStore(context,name)
        try {
            val view=LedgerReadView(store);val window=view.window("2026-10")
            val start=SystemClock.elapsedRealtime()
            for(batch in 0 until 10) {
                val entries=(batch*1000 until (batch+1)*1000).map { i ->
                    val at=if(i==0)window.previous else window.start+i*1000
                    LedgerRecord(uid="query-$i",capture=Capture(Source.MANUAL,"manual","private body".repeat(100),at),
                        bill=ParsedBill(100,if(i%2==0)Direction.EXPENSE else Direction.INCOME,"商户$i","购物",at,"order-$i",true,"",true),state=RecordState.POSTED)
                };store.merge(entries)
                if(batch==0||batch==9) {
                    val before=SystemClock.elapsedRealtime();val result=view.records("2026-10");val summary=view.summary("2026-10")
                    val elapsed=SystemClock.elapsedRealtime()-before
                    assertEquals(200,result.getJSONArray("records").length());assertTrue(result.getBoolean("hasMore"))
                    assertEquals((batch+1)*1000,summary.getInt("postedCount"))
                    assertFalse(result.toString().contains("private body"))
                    Log.i("LedgerQuery056","records=${(batch+1)*1000} readAndSummaryMs=$elapsed payloadBytes=${result.toString().toByteArray().size}")
                }
            }
            val totals=view.summary("2026-10").getJSONObject("totals")
            assertEquals(499900L,totals.getLong("expense"));assertEquals(500000L,totals.getLong("income"))
            assertEquals(100L,view.summary("2026-10").getJSONObject("previous").getLong("expense"))
            val old=view.records("2026-10","商户0").getJSONArray("records");assertEquals(1,old.length())
            val id=old.getJSONObject(0).getLong("id");assertTrue(store.softDelete(id))
            assertEquals(0,view.records("2026-10","商户0").getJSONArray("records").length())
            assertEquals(9999,view.summary("2026-10").getInt("postedCount"));assertTrue(store.undoDelete(id))
            assertEquals(200,view.records("2026-10",offset=200).getJSONArray("records").length())
            assertEquals(200,view.records("2026-10",direction="INCOME",category="购物").getJSONArray("records").length())
            Log.i("LedgerQuery056","fixtureAndAssertionsMs=${SystemClock.elapsedRealtime()-start}")
        } finally { store.close();context.deleteDatabase(name) }
    }
}
