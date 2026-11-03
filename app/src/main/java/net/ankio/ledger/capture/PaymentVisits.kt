package net.ankio.ledger.capture

import android.content.Context
import net.ankio.ledger.core.PaymentApps
import net.ankio.ledger.core.PaymentMatch
import net.ankio.ledger.core.PaymentPage
import net.ankio.ledger.core.PaymentPageDetector

/** A retained result can receive a new native window ID after backgrounding. */
internal class PaymentVisits(context:Context,name:String="payment_visits") {
    private val preferences=context.getSharedPreferences(name,Context.MODE_PRIVATE)

    companion object {
        fun isNavigation(packageName:String,activity:String)=PaymentApps.supported(packageName) &&
            (activity in setOf("com.alipay.mobile.quinox.LauncherActivity","com.eg.android.AlipayGphone.AlipayLogin") ||
                (activity.endsWith("UI") || activity.endsWith("Activity")) &&
                !PaymentPageDetector.eligibleActivity(packageName,activity))
    }

    @Synchronized fun detect(page:PaymentPage):PaymentMatch? {
        // Only the current, owner-verified page may end a visit. Raw activity
        // events from other windows or an app-resume burst are not navigation.
        if(isNavigation(page.packageName,page.activity)) {
            begin(page.packageName)
            return null
        }
        val match=PaymentPageDetector.detect(page)
        if(match==null) {
            // Inputs are observed only as a flag; their values never enter this gate.
            if(page.editable || page.texts.any { text -> listOf("确认付款","确认支付","请输入支付密码","请输入付款密码").any(text::contains) })begin(page.packageName)
            return null
        }
        if(match.transactionId!=null)return match
        val visit=preferences.getString(page.packageName,null) ?: "${page.windowId}|${page.activity}".also {
            check(preferences.edit().putString(page.packageName,it).commit())
        }
        return PaymentPageDetector.detect(page,visit)
    }

    private fun begin(packageName:String) {
        if(preferences.contains(packageName))check(preferences.edit().putString(packageName,java.util.UUID.randomUUID().toString()).commit())
    }
}
