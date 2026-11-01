package net.ankio.ledger.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.system.measureNanoTime

class CoreExecutionBenchmarkTest {
    @Test fun fixedTransactionsKeepTheirMeaningAcrossExecutionChanges() {
        val at=LocalDateTime.of(2026,10,31,12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val expense=listOf("支付成功","付款金额：28.50元","商户名称：合成咖啡店","支付时间：2026年10月30日 11:00:00")
        val income=listOf("收款成功","收款金额：99.00元","付款方：合成付款人","交易时间：2026年10月30日 11:00:00")
        val refund=listOf("微信支付","已部分退款","原付款金额：100.00元","累计退款：25.00元","商户名称：合成商户",
            "原支付时间：2026年10月30日 11:00:00","原交易单号：420000000012345678901234")
        val cases=listOf(expense,income,refund,expense+"请输入支付密码").map { it.map(::LocalOcrLine) }
        fun parse(index:Int)=LocalOcrBillParser.parse(cases[index],0,0,at)
        assertEquals(2850L,parse(0)!!.amountCents);assertEquals(Direction.INCOME,parse(1)!!.direction)
        assertEquals(7500L,parse(2)!!.amountCents);assertNull(parse(3))
        val page=PaymentPage(PaymentApps.WECHAT,1,"com.tencent.mm.plugin.remittance.ui.RemittanceResultNewUI",
            listOf("支付成功","付款金额：28.50元","完成"))
        assertNotNull(PaymentPageDetector.detect(page))
        val capture=Capture(Source.OCR,PaymentApps.WECHAT,"合成付款",at)
        val bill=ParsedBill(10000,Direction.EXPENSE,"合成商户","购物",at,null,true,"",true)
        val existing=(1L..500L).map { ExistingBill(it,Source.OCR,PaymentApps.WECHAT,"历史原文$it",it,
            Direction.EXPENSE,"其他商户",at-1_000_000,null,true) }+
            ExistingBill(501,Source.OCR,PaymentApps.WECHAT,"另一页面",10000,Direction.EXPENSE,"合成商户",at,null,true)
        assertEquals(Duplicate.Possible(501),DuplicateDetector.find(capture,bill,existing))
        repeat(100) { parse(it%4);PaymentPageDetector.detect(page);DuplicateDetector.find(capture,bill,existing) }
        fun samples(action:()->Unit)=List(5) { measureNanoTime { repeat(100) { action() } }/1_000_000.0 }
        var parsedCents=0L
        val parser=samples { for(index in cases.indices)parsedCents+=parse(index)?.amountCents ?: 0 }
        var matched=0
        val detector=samples { if(PaymentPageDetector.detect(page)!=null)matched++ }
        var possible=0
        val duplicate=samples { if(DuplicateDetector.find(capture,bill,existing) is Duplicate.Possible)possible++ }
        assertEquals(500,matched);assertEquals(500,possible);assertTrue(parsedCents>0)
        println("CORE_EXECUTION_BENCHMARK {\"parser400\":$parser,\"detector100\":$detector,\"duplicate100x501\":$duplicate}")
    }
}
