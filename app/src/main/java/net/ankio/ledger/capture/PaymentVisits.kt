package net.ankio.ledger.capture

import android.content.Context
import net.ankio.ledger.core.PaymentApps
import net.ankio.ledger.core.PaymentMatch
import net.ankio.ledger.core.PaymentPage
import net.ankio.ledger.core.PaymentPageDetector

/** A retained result can receive a new native window ID after backgrounding. */
internal class PaymentVisits internal constructor(private val store:VisitStore) {
    constructor(context:Context,name:String="payment_visits"):this(PreferencesVisitStore(context.getSharedPreferences(name,Context.MODE_PRIVATE)))

    internal interface VisitStore {
        fun get(key:String):String?
        fun contains(key:String):Boolean
        fun put(entries:Map<String,String?>)
    }

    private class PreferencesVisitStore(private val preferences:android.content.SharedPreferences):VisitStore {
        override fun get(key:String):String?=preferences.getString(key,null)
        override fun contains(key:String):Boolean=preferences.contains(key)
        override fun put(entries:Map<String,String?>) {
            val edit=preferences.edit()
            entries.forEach { (key,value) -> if(value==null)edit.remove(key) else edit.putString(key,value) }
            check(edit.commit())
        }
    }

    companion object {
        private val homeActivities=mapOf(
            PaymentApps.WECHAT to setOf(
                "com.tencent.mm.ui.LauncherUI",
                "com.tencent.mm.plugin.scanner.ui.BaseScanUI",
            ),
            PaymentApps.ALIPAY to setOf(
                "com.alipay.mobile.quinox.LauncherActivity",
                "com.eg.android.AlipayGphone.AlipayLogin",
            ),
            PaymentApps.TAOBAO to setOf(
                "com.taobao.tao.TBMainActivity",
                "com.taobao.tao.welcome.Welcome",
            ),
        )
        private val confirmationPrompts=listOf("确认付款","确认支付","请输入支付密码","请输入付款密码")
        private val passwordPrompts=listOf("请输入支付密码","请输入付款密码")
        private val barcodePrompts=listOf("向商家付钱","付款码")
        private val genericWebTokens=listOf("webview","webactivity","h5","nebula","browser")

        fun isNavigation(packageName:String,activity:String)=
            PaymentApps.supported(packageName) && activity in homeActivities[packageName].orEmpty()

        internal fun isPaymentPreflight(page:PaymentPage):Boolean {
            val genericWeb=genericWebTokens.any(page.activity.lowercase()::contains)
            if(genericWeb)return page.texts.any { text -> passwordPrompts.any(text::contains) }
            val nativeEditable=page.editable && page.activity!=PaymentPageDetector.WECHAT_LITE_OPAQUE_RESULT
            val prompt=page.texts.any { text -> confirmationPrompts.any(text::contains) }
            val offlineBarcode=page.packageName==PaymentApps.WECHAT &&
                page.activity.contains(".offline.") &&
                page.texts.any { text -> barcodePrompts.any(text::contains) }
            return nativeEditable || prompt || offlineBarcode
        }
    }

    @Synchronized fun detect(page:PaymentPage):PaymentMatch? {
        // Only the current, owner-verified page may end a visit. Raw activity
        // events from other windows or an app-resume burst are not navigation.
        if(isNavigation(page.packageName,page.activity)) {
            begin(page.packageName,clearOrigin=true)
            return null
        }
        val match=PaymentPageDetector.detect(page)
        if(match==null) {
            // Inputs are observed only as a flag; their values never enter this gate.
            if(isPaymentPreflight(page)) {
                val windowKey="${page.windowId}|${page.activity}"
                val sameOrigin=store.get(originWindowKey(page.packageName))==windowKey
                begin(page.packageName,clearOrigin=sameOrigin)
            }
            return null
        }
        if(match.transactionId!=null)return match
        val windowKey="${page.windowId}|${page.activity}"
        val originWindow=store.get(originWindowKey(page.packageName))
        val originVisit=store.get(originVisitKey(page.packageName))
        val visit=if(windowKey==originWindow && originVisit!=null) {
            if(store.get(page.packageName)!=originVisit)store.put(mapOf(page.packageName to originVisit))
            originVisit
        } else {
            val current=store.get(page.packageName) ?: windowKey
            store.put(mapOf(
                page.packageName to current,
                originWindowKey(page.packageName) to windowKey,
                originVisitKey(page.packageName) to current,
            ))
            current
        }
        return PaymentPageDetector.detect(page,visit)
    }

    private fun originWindowKey(packageName:String)="$packageName:origin_window"
    private fun originVisitKey(packageName:String)="$packageName:origin_visit"

    private fun begin(packageName:String,clearOrigin:Boolean) {
        if(store.contains(packageName)) {
            val updates=mutableMapOf<String,String?>(packageName to java.util.UUID.randomUUID().toString())
            if(clearOrigin) {
                updates[originWindowKey(packageName)]=null
                updates[originVisitKey(packageName)]=null
            }
            store.put(updates)
        }
    }
}
