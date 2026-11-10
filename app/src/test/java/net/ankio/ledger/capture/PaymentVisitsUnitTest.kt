package net.ankio.ledger.capture

import net.ankio.ledger.core.PaymentApps
import net.ankio.ledger.core.PaymentGate
import net.ankio.ledger.core.PaymentPage
import net.ankio.ledger.core.PaymentPageDetector
import org.junit.Assert.*
import org.junit.Test

class PaymentVisitsUnitTest {
    private class MemoryVisitStore : PaymentVisits.VisitStore {
        private val map = linkedMapOf<String, String>()
        override fun get(key: String): String? = map[key]
        override fun contains(key: String): Boolean = key in map
        override fun put(entries: Map<String, String?>) {
            entries.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
        }
    }

    private fun page(
        window: Int = 7,
        amount: String = "12.34",
        activity: String = PaymentPageDetector.WECHAT_FRAMEWORK_RESULT,
        extraTexts: List<String> = listOf("领取5元优惠券"),
    ) = PaymentPage(
        PaymentApps.WECHAT,
        window,
        activity,
        listOf("支付成功", "合成测试商店", "¥$amount", "完成") + extraTexts,
    )

    @Test fun jumpingToWechatPromotionPagesAndReturningNeverDuplicatesCapture() {
        val store = MemoryVisitStore()
        val visits = PaymentVisits(store)
        val gate = PaymentGate()

        val first = visits.detect(page(window = 41))!!
        assertTrue(gate.observe(first))
        assertTrue(gate.claim(first))

        val promotions = listOf(
            // Mini-program promotions ("微信支付有优惠" / merchant coupon mini-program)
            PaymentPage(PaymentApps.WECHAT, 42, "com.tencent.mm.plugin.appbrand.launching.AppBrandLaunchProxyUI", emptyList()),
            PaymentPage(PaymentApps.WECHAT, 43, "com.tencent.mm.plugin.appbrand.ui.AppBrandPreLoadingUI", emptyList()),
            PaymentPage(PaymentApps.WECHAT, 44, "com.tencent.mm.plugin.appbrand.ui.AppBrandUI00", emptyList()),
            PaymentPage(PaymentApps.WECHAT, 45, "com.tencent.mm.plugin.appbrand.ui.AppBrandUI", emptyList()),
            // Native coupon / card / shake / profile / finder subpages
            PaymentPage(PaymentApps.WECHAT, 46, "com.tencent.mm.plugin.card.ui.CardDetailUI", emptyList()),
            PaymentPage(PaymentApps.WECHAT, 47, "com.tencent.mm.plugin.card.ui.CardAcceptUI", emptyList()),
            PaymentPage(PaymentApps.WECHAT, 48, "com.tencent.mm.plugin.shake.ui.ShakeReportUI", emptyList()),
            PaymentPage(PaymentApps.WECHAT, 49, "com.tencent.mm.plugin.profile.ui.ContactInfoUI", emptyList()),
            PaymentPage(PaymentApps.WECHAT, 50, "com.tencent.mm.plugin.finder.ui.FinderShareFeedRelUI", emptyList()),
            // Luggage / BrandService / MMWebView H5 coupon & invoice pages (even with editable input boxes)
            PaymentPage(PaymentApps.WECHAT, 51, "com.tencent.mm.plugin.brandservice.ui.timeline.preload.ui.TmplWebViewTooLMpUI", emptyList()),
            PaymentPage(PaymentApps.WECHAT, 52, "com.tencent.mm.plugin.webview.luggage.LuggageMMLocalResourceWebViewUI", emptyList()),
            PaymentPage(PaymentApps.WECHAT, 53, "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI", listOf("输入手机号领券", "确认支付0.1元抢神券"), editable = true),
            // Opaque LiteApp coupon / invoice form with editable input
            PaymentPage(PaymentApps.WECHAT, 54, PaymentPageDetector.WECHAT_LITE_OPAQUE_RESULT, listOf("填写发票抬头"), editable = true),
        )
        for (promo in promotions) {
            assertNull(promo.activity, visits.detect(promo))
        }

        // Return to the payment result screen (both original windowId and recreated windowId, with updated coupon text)
        val returnedOriginal = visits.detect(page(window = 41, extraTexts = listOf("已领5.00元现金券")))!!
        assertEquals(first.eventIdentity, returnedOriginal.eventIdentity)
        assertFalse("Gate must suppress re-capture when returning from promotion", gate.observe(returnedOriginal))

        val returnedRecreated = PaymentVisits(store).detect(page(window = 72, extraTexts = listOf("已领5.00元现金券")))!!
        assertEquals(first.eventIdentity, returnedRecreated.eventIdentity)
        assertFalse("Gate must suppress re-capture even if result window was recreated", gate.observe(returnedRecreated))
    }

    @Test fun cancelledUpsellOnTopOfPaymentResultRestoresOriginalIdentityWhileRealSecondOrderCaptures() {
        val store = MemoryVisitStore()
        val visits = PaymentVisits(store)
        val gate = PaymentGate()

        val first = visits.detect(page(window = 41))!!
        assertTrue(gate.observe(first) && gate.claim(first))

        // Upsell cashier opens in window 42 on top of window 41, then user cancels and returns to window 41
        assertNull(visits.detect(PaymentPage(PaymentApps.WECHAT, 42, PaymentPageDetector.WECHAT_FRAMEWORK_RESULT, listOf("确认支付", "¥1.00"))))
        val backToFirst = visits.detect(page(window = 41))!!
        assertEquals(first.eventIdentity, backToFirst.eventIdentity)
        assertFalse(gate.observe(backToFirst))

        // Now user returns to mini-program and makes a genuine second same-amount order (cashier window 43 -> result window 44)
        assertNull(visits.detect(PaymentPage(PaymentApps.WECHAT, 43, "com.tencent.mm.plugin.appbrand.ui.AppBrandUI00", emptyList())))
        assertNull(visits.detect(PaymentPage(PaymentApps.WECHAT, 44, PaymentPageDetector.WECHAT_FRAMEWORK_RESULT, listOf("请输入支付密码"), editable = true)))
        val second = visits.detect(page(window = 45))!!
        assertNotEquals(first.eventIdentity, second.eventIdentity)
        assertTrue(gate.observe(second) && gate.claim(second))
    }

    @Test fun homeScannerAndOfflineBarcodeAllowConsecutiveSameAmountPayments() {
        val store = MemoryVisitStore()
        val visits = PaymentVisits(store)

        val first = visits.detect(page(window = 7))!!
        // Home LauncherUI starts a new visit even in the same window
        assertNull(visits.detect(PaymentPage(PaymentApps.WECHAT, 99, "com.tencent.mm.ui.LauncherUI", emptyList())))
        val second = visits.detect(page(window = 7))!!
        assertNotEquals(first.eventIdentity, second.eventIdentity)

        // Scanner BaseScanUI starts a new visit
        assertNull(visits.detect(PaymentPage(PaymentApps.WECHAT, 100, "com.tencent.mm.plugin.scanner.ui.BaseScanUI", emptyList())))
        val third = visits.detect(page(window = 8))!!
        assertNotEquals(second.eventIdentity, third.eventIdentity)

        // Returning to offline barcode screen ("向商家付钱") allows consecutive password-free barcode scans
        val offlineUi = "com.tencent.mm.plugin.offline.ui.WalletOfflineCoinPurseUI"
        assertNull(visits.detect(PaymentPage(PaymentApps.WECHAT, 101, offlineUi, listOf("向商家付钱", "优先使用零钱"))))
        val fourth = visits.detect(page(window = 9, activity = "com.tencent.mm.plugin.wallet_core.ui.WalletOrderInfoNewUI"))!!
        assertNotEquals(third.eventIdentity, fourth.eventIdentity)
    }

    @Test fun sameWindowPasswordOrConfirmationPreflightStillStartsNewVisit() {
        for (preflight in listOf(
            page(window = 7, activity = PaymentPageDetector.WECHAT_LITE_RESULT).copy(editable = true, texts = emptyList()),
            page(window = 7, activity = PaymentPageDetector.WECHAT_LITE_RESULT).copy(texts = listOf("请确认付款：¥12.34")),
        )) {
            val visits = PaymentVisits(MemoryVisitStore())
            val first = visits.detect(page(window = 7, activity = PaymentPageDetector.WECHAT_LITE_RESULT))!!
            assertNull(visits.detect(preflight))
            val second = visits.detect(page(window = 7, activity = PaymentPageDetector.WECHAT_LITE_RESULT))!!
            assertNotEquals(first.eventIdentity, second.eventIdentity)
        }
    }
}
