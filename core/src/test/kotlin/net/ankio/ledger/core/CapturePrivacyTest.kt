package net.ankio.ledger.core

import org.junit.Assert.*
import org.junit.Test

class CapturePrivacyTest {
    @Test fun explicitShakeAcceptsReadOnlyThirdPartyPagesWithoutAnActivityTemplate() {
        for(pkg in listOf("com.xunmeng.pinduoduo","com.sankuai.meituan","com.taobao.idlefish","com.ss.android.ugc.aweme")) {
            val order=PaymentPage(pkg,8,"$pkg.OrderActivity",listOf("订单详情","实付 ¥6.89"))
            assertTrue(pkg,CapturePrivacy.manualAllowed(order))
            assertNull(PaymentPageDetector.detect(order))
            assertFalse(CapturePrivacy.manualAllowed(order.copy(texts=listOf("请输入支付密码","¥6.89"))))
            assertFalse(CapturePrivacy.manualAllowed(order.copy(editable=true)))
        }
    }
    private fun page(activity:String=PaymentPageDetector.WECHAT_FRAMEWORK_RESULT,vararg text:String)=
        PaymentPage(PaymentApps.WECHAT,7,activity,text.toList())
    @Test fun manualShakeCannotBypassInputAndAuthenticationGuards() {
        assertFalse(CapturePrivacy.manualAllowed(page(text=arrayOf("支付成功","请输入支付密码"))))
        assertFalse(CapturePrivacy.manualAllowed(page(text=arrayOf("支付成功","人脸验证"))))
        assertFalse(CapturePrivacy.manualAllowed(page(text=arrayOf("支付成功")).copy(editable=true)))
        assertFalse(CapturePrivacy.manualAllowed(page("com.tencent.mm.plugin.wallet.pwd.ui.WalletPasswordUI","支付成功")))
        assertFalse(CapturePrivacy.manualAllowed(page()))
    }
    @Test fun readOnlyBillDetailsRemainAvailableForExplicitShake() {
        val detail=page("com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI")
        assertTrue(CapturePrivacy.manualAllowed(detail))
        assertNull(PaymentPageDetector.detect(detail))
        assertTrue(CapturePrivacy.manualAllowed(page(text=arrayOf("支付成功","¥0.01"))))
    }
}
