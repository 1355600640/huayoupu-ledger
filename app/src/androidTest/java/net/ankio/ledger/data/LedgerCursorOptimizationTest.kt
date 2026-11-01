package net.ankio.ledger.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.ankio.ledger.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerCursorOptimizationTest {
    private fun withStore(test: (LedgerStore, LedgerReadView) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "cursor-${System.nanoTime()}.db"
        val store = LedgerStore(context, name)
        try { test(store, LedgerReadView(store)) }
        finally { store.close(); context.deleteDatabase(name) }
    }

    private fun seed(store: LedgerStore, view: LedgerReadView, count: Int) {
        val at = view.window("2026-10").start + 60_000
        store.merge((1..count).map { index ->
            LedgerRecord(uid = "cursor-$index", capture = Capture(Source.MANUAL, "manual", "private OCR body", at),
                bill = ParsedBill(100, if (index % 2 == 0) Direction.INCOME else Direction.EXPENSE,
                    "商户%_$index", "购物", at, "order-$index", true, "", true), state = RecordState.POSTED)
        })
    }

    private fun ids(page: JSONObject): List<Long> = page.getJSONArray("records").let { rows ->
        (0 until rows.length()).map { rows.getJSONObject(it).getLong("id") }
    }

    private fun next(view: LedgerReadView, page: JSONObject, search: String = "", direction: String = ""): JSONObject {
        val cursor = page.getJSONObject("nextCursor")
        return view.records("2026-10", search, direction = direction,
            beforeAt = cursor.getLong("occurredAt"), beforeId = cursor.getLong("id"))
    }

    @Test fun equalTimestampsVisitEveryRecordOnceInDescendingIdOrder() = withStore { store, view ->
        seed(store, view, 450)
        val first = view.records("2026-10")
        val second = next(view, first)
        val third = next(view, second)
        assertEquals(listOf(200, 200, 50), listOf(ids(first).size, ids(second).size, ids(third).size))
        val all = ids(first) + ids(second) + ids(third)
        assertEquals(450, all.toSet().size)
        assertEquals(all.sortedDescending(), all)
        assertFalse(third.getBoolean("hasMore")); assertTrue(third.isNull("nextCursor"))
        assertFalse(first.toString().contains("private OCR body"))
    }

    @Test fun cursorKeepsLiteralSearchAndDirectionAcrossPages() = withStore { store, view ->
        seed(store, view, 450)
        val first = view.records("2026-10", "商户%_", direction = "EXPENSE")
        val second = next(view, first, "商户%_", "EXPENSE")
        assertEquals(200, ids(first).size); assertEquals(25, ids(second).size)
        val rows = first.getJSONArray("records")
        repeat(rows.length()) { assertEquals("EXPENSE", rows.getJSONObject(it).getString("direction")) }
        assertTrue(view.records("2026-10", "商户_1").getJSONArray("records").length() == 0)
        assertEquals(225, (ids(first) + ids(second)).toSet().size)
    }

    @Test fun deletedRowsAndNewRowsDoNotShiftCursorContinuation() = withStore { store, view ->
        seed(store, view, 450)
        val first = view.records("2026-10")
        val firstIds = ids(first)
        assertTrue(store.softDelete(firstIds.first()))
        val at = view.window("2026-10").start + 120_000
        store.merge(listOf(LedgerRecord(uid = "newer", capture = Capture(Source.MANUAL, "manual", "", at),
            bill = ParsedBill(100, Direction.EXPENSE, "新增商户", "购物", at, null, true, "", true), state = RecordState.POSTED)))
        val second = next(view, first)
        val third = next(view, second)
        assertEquals(250, (ids(second) + ids(third)).size)
        assertTrue((ids(second) + ids(third)).none { it in firstIds })
        assertTrue(ids(second).all { it < firstIds.last() })
        assertNotEquals(firstIds.first(), ids(view.records("2026-10")).first())
    }

    @Test fun listPaymentProviderMatchesCaptureEvidenceWithoutReturningOcrBodies() = withStore { store, view ->
        val at=view.window("2026-10").start+60_000
        val captures=listOf(
            Capture(Source.OCR,"com.tencent.mm","转账成功",at),
            Capture(Source.NOTIFICATION,"payment_auto:COM.TENCENT.MM","付款成功",at),
            Capture(Source.IMAGE,"image","微信支付 支付成功",at),
            Capture(Source.IMAGE,"image","支付宝付款成功",at),
            Capture(Source.OCR,"com.eg.android.AlipayGphone","备注含微信",at),
            Capture(Source.IMAGE,"image","微信 支付宝",at),
            Capture(Source.MANUAL,"manual","微信支付",at),
            Capture(Source.OCR,"com.taobao.taobao","实付款",at),
        )
        store.merge(captures.mapIndexed { i,capture -> LedgerRecord(uid="provider-$i",capture=capture,
            bill=ParsedBill(1,Direction.EXPENSE,"商户$i","其他",at,null,true,"",true),state=RecordState.POSTED) })
        val rows=view.records("2026-10").getJSONArray("records")
        repeat(rows.length()) { i ->
            val row=rows.getJSONObject(i)
            val capture=captures[row.getString("uid").substringAfter("provider-").toInt()]
            assertEquals(paymentProvider(capture),row.optString("paymentProvider").takeUnless { row.isNull("paymentProvider") })
            assertFalse(row.has("rawText"))
        }
        assertEquals("wechat",rows.getJSONObject(rows.length()-1).getString("paymentProvider"))
    }
}
