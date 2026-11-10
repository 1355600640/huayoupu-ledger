package net.ankio.ledger.core

import org.junit.Assert.*
import org.junit.Test

class WechatPaymentDetectorTest {
    private fun page(vararg text:String, activity:String="com.tencent.mm.plugin.remittance.ui.RemittanceResultNewUI", editable:Boolean=false)=
        PaymentPage(PaymentApps.WECHAT,7,activity,text.toList(),editable)

    @Test fun outgoingTransferResultNeedsNoSystemNotificationOrOrderNumber() {
        assertNotNull(PaymentPageDetector.detect(page("转账成功","¥0.01","给测试收款方","完成")))
    }
    @Test fun unreadableTransferResultsMustNotAcquirePixelsToGuessTheirContent() {
        assertNull(PaymentPageDetector.detect(page(activity="com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI")))
        assertNull(PaymentPageDetector.detect(page(activity="com.tencent.mm.plugin.remittance.ui.RemittanceResultNewUI")))
        assertNull(PaymentPageDetector.detect(page(activity="com.tencent.mm.plugin.wallet.pay.ui.WalletPayUI")))
        assertNull(PaymentPageDetector.detect(page(activity="com.tencent.mm.plugin.webview.ui.tools.WebViewUI")))
        assertNull(PaymentPageDetector.detect(page("确认支付",activity="com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI")))
    }
    @Test fun historicalSenderReceiptDoesNotAutomaticallyCapture() {
        assertNull(PaymentPageDetector.detect(page("测试收款人已收款","￥0.01","转账时间","2026年10月17日 20:43:53",activity="com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI")))
    }
    @Test fun qrPaymentSuccessAcceptsCombinedAccessibleLabels() {
        assertNotNull(PaymentPageDetector.detect(page("支付成功，支付金额￥26.35","完成",activity="com.tencent.mm.plugin.wallet.pay.ui.WalletPayUI")))
    }
    @Test fun historicalWebBillIsNotAnImmediatePaymentResult() {
        val match=PaymentPageDetector.detect(page("-26.35","当前状态","支付成功","支付时间","2026年10月17日 20:44:30","交易单号","4200003240202610171234567890",activity="com.tencent.mm.plugin.webview.ui.tools.WebViewUI"))
        assertNull(match)
    }
    @Test fun paidTransferCanStillBeWaitingForRecipient() {
        assertNotNull(PaymentPageDetector.detect(page("转账成功","￥0.01","等待对方收款","完成")))
        assertNull(PaymentPageDetector.detect(page("待收款","￥0.01","确认收款")))
    }
    @Test fun chatsInputsFailuresAndUnrelatedWebPagesCannotTrigger() {
        assertNull(PaymentPageDetector.detect(page("支付成功","￥0.01",activity="com.tencent.mm.ui.chatting.ChattingUI")))
        assertNull(PaymentPageDetector.detect(page("支付成功","￥0.01",editable=true)))
        assertNull(PaymentPageDetector.detect(page("支付失败","￥0.01","完成")))
        assertNull(PaymentPageDetector.detect(page("确认支付","￥0.01","请输入支付密码")))
        assertNull(PaymentPageDetector.detect(page("支付成功","￥0.01","完成",activity="com.tencent.mm.plugin.webview.ui.tools.WebViewUI")))
        assertNull(PaymentPageDetector.detect(page("支付成功","￥0.01",activity="com.tencent.mm.ui.LauncherUI")))
    }
    @Test fun ambiguityDoesNotGuessAnAmount() {
        assertNull(PaymentPageDetector.detect(page("支付成功","￥10.00","￥20.00")))
        assertNull(PaymentPageDetector.detect(page("支付成功","完成")))
    }
    @Test fun groupedLabelsInHistoricalWebBillDoNotAutoCapture() {
        assertNull(PaymentPageDetector.detect(page("-26.35","当前状态：支付成功","支付时间：2026年10月17日 20:44:30","交易单号：4200003240202610171234567890",activity="com.tencent.mm.plugin.webview.ui.tools.WebViewUI")))
    }
    @Test fun returningToSamePageIsSuppressedButANewPaymentWindowIsIndependent() {
        val match=PaymentPageDetector.detect(page("转账成功","￥0.01","完成"))!!
        val gate=PaymentGate()
        assertTrue(gate.observe(match));assertTrue(gate.claim(match))
        assertFalse(gate.observe(match));gate.observe(null)
        assertFalse(gate.observe(match));assertFalse(gate.claim(match))
        val another=PaymentPageDetector.detect(page("转账成功","￥0.01","完成").copy(windowId=72))!!
        assertTrue(gate.observe(another));assertTrue(gate.claim(another))
    }

    @Test fun walletOrderInfoNewUIDetectsPaymentSuccess() {
        val orderUi = "com.tencent.mm.plugin.wallet_core.ui.WalletOrderInfoNewUI"
        assertNotNull(PaymentPageDetector.detect(page("支付成功", "¥15.50", "完成", activity = orderUi)))
        assertNotNull(PaymentPageDetector.detect(page("支付完成", "¥88.00", "商户名称", "完成", activity = orderUi)))
        assertNotNull(PaymentPageDetector.detect(page("付款完成", "¥32.50", "完成", activity = orderUi)))
        // Standalone currency symbol and integer amount
        assertNotNull(PaymentPageDetector.detect(page("支付成功", "¥", "28", "完成", activity = orderUi)))
    }

    @Test fun walletOfflineCoinPurseUIDetectsBarcodePayment() {
        val offlineUi = "com.tencent.mm.plugin.offline.ui.WalletOfflineCoinPurseUI"
        assertTrue(PaymentPageDetector.eligibleActivity(PaymentApps.WECHAT, offlineUi))
        assertNotNull(PaymentPageDetector.detect(page("支付成功", "¥25.00", activity = offlineUi)))
    }

    @Test fun wechatOrderHandlerUIDetectsAppPayment() {
        val appOrderUi = "com.tencent.mm.plugin.wallet_index.ui.OrderHandlerUI"
        assertTrue(PaymentPageDetector.eligibleActivity(PaymentApps.WECHAT, appOrderUi))
        assertNotNull(PaymentPageDetector.detect(page("支付成功", "¥99.00", "返回应用", activity = appOrderUi)))
    }

    @Test fun wechatPromotionsAndDiscountsDoNotBreakPaymentTotal() {
        val orderUi = "com.tencent.mm.plugin.wallet_core.ui.WalletOrderInfoNewUI"
        assertNotNull(PaymentPageDetector.detect(page("支付成功", "¥18.00", "已优惠¥2.00", "获得100积分", "完成", activity = orderUi)))
        assertNotNull(PaymentPageDetector.detect(page("支付成功", "¥50.00", "微信支付优惠¥5.00", "抽奖最高得88元", "完成", activity = orderUi)))
        assertNotNull(PaymentPageDetector.detect(page("支付成功", "¥18.00", "已领¥5.00", "已领5.00元现金券", "完成", activity = orderUi)))
    }

    @Test fun paidToRecipientStatusDetectsSuccessfully() {
        assertNotNull(PaymentPageDetector.detect(page("向麦当劳付款成功", "¥35.00", "完成")))
        assertNotNull(PaymentPageDetector.detect(page("已向全家便利店支付成功", "¥12.80", "完成")))
    }
}

