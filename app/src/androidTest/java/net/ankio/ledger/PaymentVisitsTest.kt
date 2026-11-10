package net.ankio.ledger

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankio.ledger.capture.PaymentVisits
import net.ankio.ledger.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentVisitsTest {
    private fun page(window:Int=7,amount:String="12.34",transaction:String?=null)=PaymentPage(
        PaymentApps.WECHAT,window,PaymentPageDetector.WECHAT_LITE_RESULT,
        listOf("支付成功","合成测试商店","¥$amount","返回商家")+
            (transaction?.let { listOf("交易单号",it) }?:emptyList()))

    private fun navigation(visits:PaymentVisits,pkg:String,activity:String) {
        assertNull(visits.detect(PaymentPage(pkg,99,activity,emptyList())))
    }

    private fun withVisits(test:(PaymentVisits,()->PaymentVisits)->Unit) {
        val context=ApplicationProvider.getApplicationContext<LedgerApp>()
        val name="payment_visits_test_${java.util.UUID.randomUUID()}"
        try { test(PaymentVisits(context,name)) { PaymentVisits(context,name) } }
        finally { context.deleteSharedPreferences(name) }
    }

    @Test fun recreatedWindowsAndRestartRetainTheOriginalPaymentIdentity()=withVisits { visits,reopen ->
        val first=visits.detect(page())!!
        assertEquals("Preserve pre-upgrade identities for the original window",PaymentPageDetector.detect(page())!!.eventIdentity,first.eventIdentity)
        val resumed=visits.detect(page(72))!!
        assertNotEquals("Frame authorization still belongs to the actual window",first.signature,resumed.signature)
        assertEquals(first.eventIdentity,resumed.eventIdentity)
        assertEquals(first.eventIdentity,reopen().detect(page(80))!!.eventIdentity)
    }

    @Test fun externalAppsAndIncompleteReadsDoNotCreateAnotherPayment()=withVisits { visits,_ ->
        val first=visits.detect(page())!!
        navigation(visits,"com.android.launcher3","com.android.launcher3.Launcher")
        navigation(visits,"net.ankio.ledger","net.ankio.ledger.ui.MainActivity")
        navigation(visits,PaymentApps.WECHAT,page().activity)
        assertNull(visits.detect(page(72).copy(texts=emptyList())))
        assertNull(visits.detect(page(72).copy(texts=listOf("支付成功","返回商家"))))
        assertEquals(first.eventIdentity,visits.detect(page(72))!!.eventIdentity)
    }

    @Test fun navigationWithinWechatAllowsAnotherPaymentEvenInTheSameWindow()=withVisits { visits,reopen ->
        val first=visits.detect(page())!!
        navigation(visits,PaymentApps.WECHAT,"com.tencent.mm.ui.LauncherUI")
        val second=reopen().detect(page())!!
        assertNotEquals(first.eventIdentity,second.eventIdentity)
        assertEquals(second.eventIdentity,reopen().detect(page(73))!!.eventIdentity)
    }

    @Test fun newConfirmationOrPasswordFlagAllowsASecondSameAmountPayment() {
        for(preflight in listOf(page().copy(editable=true,texts=emptyList()),page().copy(texts=listOf("请确认付款：¥12.34"))))
            withVisits { visits,_ ->
                val first=visits.detect(page())!!
                assertNull(visits.detect(preflight))
                assertNotEquals(first.eventIdentity,visits.detect(page())!!.eventIdentity)
            }
    }

    @Test fun knownTransactionIdsRemainIndependentOfVisits()=withVisits { visits,_ ->
        val first=visits.detect(page(transaction="4200003240202610171234567890"))!!
        navigation(visits,PaymentApps.WECHAT,"com.tencent.mm.ui.LauncherUI")
        assertEquals(first.eventIdentity,visits.detect(page(73,transaction=first.transactionId))!!.eventIdentity)
        assertNotEquals(first.eventIdentity,visits.detect(page(transaction="4200003240202610171234567891"))!!.eventIdentity)
    }

    @Test fun alipayHomeNavigationStartsANewPaymentVisit()=withVisits { visits,reopen ->
        val result=page().copy(packageName=PaymentApps.ALIPAY,activity="com.alipay.fixture.PaymentResultActivity")
        val first=visits.detect(result)!!
        assertEquals(first.eventIdentity,reopen().detect(result.copy(windowId=73))!!.eventIdentity)
        navigation(visits,PaymentApps.ALIPAY,"com.alipay.mobile.quinox.LauncherActivity")
        val second=reopen().detect(result.copy(windowId=74))!!
        assertNotEquals(first.eventIdentity,second.eventIdentity)
        assertEquals(second.eventIdentity,reopen().detect(result.copy(windowId=75))!!.eventIdentity)
    }

    @Test fun changedAmountsAndOtherPaymentAppsKeepSeparateIdentities()=withVisits { visits,_ ->
        val first=visits.detect(page())!!
        val other=page().copy(packageName=PaymentApps.ALIPAY,activity="com.alipay.android.msp.ui.views.MspContainerActivity")
        assertNotEquals(first.eventIdentity,visits.detect(other)!!.eventIdentity)
        assertNotEquals(first.eventIdentity,visits.detect(page(amount="23.45"))!!.eventIdentity)
        navigation(visits,PaymentApps.ALIPAY,"com.alipay.mobile.MainActivity")
        assertEquals(first.eventIdentity,visits.detect(page(75))!!.eventIdentity)
    }

    @Test fun wechatPromotionCouponAndMiniProgramReturnRetainsPaymentIdentity()=withVisits { visits,reopen ->
        val first=visits.detect(page(7).copy(texts=listOf("支付成功","合成测试商店","¥12.34","领取5元优惠券","完成")))!!
        for(subpage in listOf(
            PaymentPage(PaymentApps.WECHAT,41,"com.tencent.mm.plugin.appbrand.launching.AppBrandLaunchProxyUI",emptyList()),
            PaymentPage(PaymentApps.WECHAT,42,"com.tencent.mm.plugin.appbrand.ui.AppBrandUI00",emptyList()),
            PaymentPage(PaymentApps.WECHAT,43,"com.tencent.mm.plugin.appbrand.ui.AppBrandUI",emptyList()),
            PaymentPage(PaymentApps.WECHAT,44,"com.tencent.mm.plugin.card.ui.CardDetailUI",emptyList()),
            PaymentPage(PaymentApps.WECHAT,45,"com.tencent.mm.plugin.shake.ui.ShakeReportUI",emptyList()),
            PaymentPage(PaymentApps.WECHAT,46,"com.tencent.mm.plugin.profile.ui.ContactInfoUI",emptyList()),
            PaymentPage(PaymentApps.WECHAT,47,"com.tencent.mm.plugin.brandservice.ui.timeline.preload.ui.TmplWebViewTooLMpUI",emptyList()),
            PaymentPage(PaymentApps.WECHAT,48,"com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",listOf("领券中心","输入手机号领券","确认支付0.1元抢券"),editable=true),
            PaymentPage(PaymentApps.WECHAT,49,PaymentPageDetector.WECHAT_LITE_OPAQUE_RESULT,listOf("填写发票抬头"),editable=true),
        )) {
            assertNull(subpage.activity,visits.detect(subpage))
        }
        // Returning to either the retained result window or a vendor-recreated result window after claiming a coupon must keep the same identity.
        val returnedSameWindow=visits.detect(page(7).copy(texts=listOf("支付成功","合成测试商店","¥12.34","已领5.00元现金券","完成")))!!
        assertEquals(first.eventIdentity,returnedSameWindow.eventIdentity)
        val returnedRecreated=reopen().detect(page(72).copy(texts=listOf("支付成功","合成测试商店","¥12.34","已领5.00元现金券","完成")))!!
        assertEquals(first.eventIdentity,returnedRecreated.eventIdentity)
    }

    @Test fun cancelledUpsellCashierReturningToOriginalResultWindowDoesNotDuplicate()=withVisits { visits,_ ->
        val first=visits.detect(page(41))!!
        // An upsell cashier opens in a separate window (42) on top of the retained result window (41).
        assertNull(visits.detect(PaymentPage(PaymentApps.WECHAT,42,PaymentPageDetector.WECHAT_FRAMEWORK_RESULT,listOf("确认支付","¥1.00"))))
        // User cancels the upsell and lands back on the original result window (41).
        assertEquals(first.eventIdentity,visits.detect(page(41))!!.eventIdentity)
        // A genuine second payment after returning to mini-program opens a new cashier/result window (43 -> 44) and gets a new identity.
        assertNull(visits.detect(PaymentPage(PaymentApps.WECHAT,43,PaymentPageDetector.WECHAT_FRAMEWORK_RESULT,listOf("确认支付","¥12.34"))))
        assertNotEquals(first.eventIdentity,visits.detect(page(44))!!.eventIdentity)
    }
}
