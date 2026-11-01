package net.ankio.ledger.core

import org.junit.Assert.*
import org.junit.Test

/** All examples below are synthetic transactions. */
class DuplicateDetectorTest {
    @Test fun repeatedShoppingOrderIsExactAcrossCaptureTimesOnlyWithinItsApplication() {
        val c=Capture(Source.OCR,"com.taobao.taobao","订单信息",now)
        val b=bill.copy(transactionId="512786188900737738",timeExplicit=false)
        val old=existing(source=Source.OCR,origin=c.origin,time=now-8*3_600_000,tx=b.transactionId).copy(timeExplicit=false)
        assertEquals(Duplicate.Exact(7),DuplicateDetector.find(c,b,listOf(old)))
        assertEquals(Duplicate.None,DuplicateDetector.find(c,b,listOf(old.copy(origin="com.xunmeng.pinduoduo"))))
    }
    private val now = 1_792_125_000_000L
    private val capture = Capture(Source.NOTIFICATION, "com.tencent.mm", "微信支付\n支付成功\n付款金额28.50元\n商户：示例咖啡店", now)
    private val bill = ParsedBill(2850, Direction.EXPENSE, "示例咖啡店", "餐饮", now, null, true, "测试")
    private fun existing(source: Source = capture.source, origin: String = capture.origin, text: String = capture.text,
                         time: Long = now, tx: String? = null, merchant: String = bill.merchant,
                         amount: Long? = bill.amountCents, direction: Direction? = bill.direction) =
        ExistingBill(7, source, origin, text, amount, direction, merchant, time, tx)

    @Test fun identicalTextWithoutEventIdentityNeverSilentlyDropsRealPurchase() {
        assertEquals(Duplicate.Possible(7), DuplicateDetector.find(capture, bill, listOf(existing(time = now - 20_000))))
        assertEquals(Duplicate.Possible(7), DuplicateDetector.find(capture, bill, listOf(existing(time = now - 90_000))))
        assertEquals(Duplicate.None, DuplicateDetector.find(capture, bill, listOf(existing(time = now - 180_000))))
    }

    @Test fun explicitMinuteCanStillContainTwoIdenticalRealPurchases() {
        val explicit = bill.copy(timeExplicit = true)
        assertEquals(Duplicate.Possible(7), DuplicateDetector.find(capture, explicit, listOf(existing())))
        assertEquals(Duplicate.Possible(7), DuplicateDetector.find(capture, explicit, listOf(existing(time = now - 50_000))))
    }

    @Test fun identicalPayloadWithoutMerchantStillNeedsUserDecision() {
        assertEquals(Duplicate.Possible(7), DuplicateDetector.find(capture, bill.copy(merchant = "未知商户"),
            listOf(existing(merchant = "未知商户"))))
    }

    @Test fun sameTransactionIdOnlyMergesWithinProviderAndDirection() {
        val identified = bill.copy(transactionId = "ORDER000001")
        assertEquals(Duplicate.Exact(7), DuplicateDetector.find(capture, identified, listOf(existing(source = Source.OCR, text = "微信账单详情", time = now - 900_000, tx = "ORDER000001"))))
        assertEquals(Duplicate.Possible(7), DuplicateDetector.find(capture, identified, listOf(existing(origin = "com.eg.android.AlipayGphone", tx = "ORDER000001"))))
        assertEquals(Duplicate.None, DuplicateDetector.find(capture, identified.copy(direction = Direction.INCOME), listOf(existing(tx = "ORDER000001"))))
    }

    @Test fun distinctOrdersAreSeparatePurchases() {
        val identified = bill.copy(transactionId = "ORDER000002")
        assertEquals(Duplicate.None, DuplicateDetector.find(capture.copy(text = "new message"), identified, listOf(existing(tx = "ORDER000001"))))
        assertEquals(Duplicate.None, DuplicateDetector.find(capture, identified, listOf(existing(tx = "ORDER000001"))))
    }

    @Test fun updatedNotificationWithSameTransactionIdIsExact() {
        val identified = bill.copy(transactionId = "ORDER000001")
        assertEquals(Duplicate.Exact(7), DuplicateDetector.find(capture.copy(text = "微信支付\n订单更新"), identified,
            listOf(existing(tx = "ORDER000001", text = "微信支付\n最初通知"))))
    }

    @Test fun screenshotImportWithSameRawTextIsOnlyPossibleAcrossSources() {
        val imported = capture.copy(source = Source.IMAGE, origin = "image")
        assertEquals(Duplicate.Possible(7), DuplicateDetector.find(imported, bill.copy(timeExplicit = true), listOf(existing())))
    }

    @Test fun validatedAiTimeRemainsUsableWhenBuiltinTextTemplateCannotParseTheImage() {
        val imported=Capture(Source.IMAGE,"image","转账成功 -28.50 示例咖啡店 2026年10月17日 02:32:51",now)
        val prior=existing(source=Source.IMAGE,origin="image",text=imported.text).copy(timeExplicit=true)
        assertEquals(Duplicate.Possible(7),DuplicateDetector.find(imported,bill.copy(timeExplicit=true),listOf(prior)))
        assertEquals(Duplicate.None,DuplicateDetector.find(imported,bill.copy(timeExplicit=true),listOf(prior.copy(timeExplicit=false))))
    }

    @Test fun differentChannelsWithoutMerchantCannotBeConfidentlyLinked() {
        val unknown = bill.copy(merchant = "未知商户")
        assertEquals(Duplicate.None, DuplicateDetector.find(capture, unknown,
            listOf(existing(source = Source.SMS, origin = "95588", merchant = "未知商户"))))
    }

    @Test fun imageImportTimeCannotEstablishTransactionProximity() {
        assertEquals(Duplicate.None, DuplicateDetector.find(capture.copy(source = Source.IMAGE, origin = "image"), bill, listOf(existing())))
        assertEquals(Duplicate.None, DuplicateDetector.find(capture, bill, listOf(existing(source = Source.IMAGE, origin = "image", text = "微信支付\n支付成功\n支付金额28.50元"))))
    }

    @Test fun crossChannelSameMerchantAmountAndTimeIsOnlyPossible() {
        assertEquals(Duplicate.Possible(7), DuplicateDetector.find(capture, bill, listOf(existing(source = Source.SMS, origin = "95588", text = "消费短信", merchant = " 示例 咖啡店 ", time = now - 60_000))))
        assertEquals(Duplicate.None, DuplicateDetector.find(capture, bill, listOf(existing(source = Source.SMS, origin = "95588", merchant = "其他餐厅"))))
    }

    @Test fun amountAloneNeverMergesAndIncompleteBillsAreNotPossibleDuplicates() {
        assertEquals(Duplicate.None, DuplicateDetector.find(capture, bill.copy(merchant = "未知商户"), listOf(existing(merchant = "未知商户", text = "different"))))
        assertEquals(Duplicate.None, DuplicateDetector.find(capture, bill.copy(amountCents = null), listOf(existing(text = "different"))))
        assertEquals(Duplicate.None, DuplicateDetector.find(capture, bill, listOf(existing(amount = 100))))
    }

    @Test fun genericPaymentNotificationAndScreenshotArePossibleInEitherOrder() {
        val notification=Capture(Source.NOTIFICATION,"com.eg.android.AlipayGphone","交易提醒\n你有一笔28.50元的支出，点此查看详情。",now)
        val generic=bill.copy(merchant="支付宝支出")
        val screenshot=Capture(Source.OCR,notification.origin,"支付宝 支付成功 示例咖啡店 -28.50",now+3_600_000)
        val read=bill.copy(timeExplicit=true,transactionId="ALIPAY-ORDER-001")
        val oldNotice=existing(origin=notification.origin,text=notification.text,merchant=generic.merchant,time=now+30_000)
        assertEquals(Duplicate.Possible(7),DuplicateDetector.find(screenshot,read,listOf(oldNotice)))
        val oldImage=existing(source=Source.OCR,origin=screenshot.origin,text=screenshot.text,tx=read.transactionId).copy(timeExplicit=true)
        assertEquals(Duplicate.Possible(7),DuplicateDetector.find(notification,generic,listOf(oldImage)))
        assertEquals(Duplicate.Possible(7),DuplicateDetector.find(screenshot.copy(source=Source.IMAGE,origin="image"),read,listOf(oldNotice)))
    }

    @Test fun genericCrossChannelCandidatesStillRequireProviderDirectionAmountAndTransactionTime() {
        val screenshot=Capture(Source.OCR,"com.eg.android.AlipayGphone","支付宝 支付成功",now)
        val read=bill.copy(timeExplicit=true)
        val old=existing(origin=screenshot.origin,merchant="支付宝支出",text="交易提醒")
        for(unrelated in listOf(old.copy(origin="com.tencent.mm"),old.copy(amountCents=1),
            old.copy(direction=Direction.INCOME),old.copy(occurredAt=now-120_001),old.copy(source=Source.MANUAL),
            old.copy(merchant="其他咖啡店"))) {
            assertEquals(Duplicate.None,DuplicateDetector.find(screenshot,read,listOf(unrelated)))
        }
        assertEquals(Duplicate.None,DuplicateDetector.find(screenshot,read.copy(timeExplicit=false),listOf(old)))
        assertEquals(Duplicate.None,DuplicateDetector.find(screenshot.copy(source=Source.IMAGE,origin="image",text="支付成功"),read,listOf(old)))
    }
}
