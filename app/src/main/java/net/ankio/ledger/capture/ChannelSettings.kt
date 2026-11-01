package net.ankio.ledger.capture

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager

class ChannelSettings(private val context: Context) {
    private val preferences = context.getSharedPreferences("channels", Context.MODE_PRIVATE)
    init {
        val edit=preferences.edit().remove("page").remove("page_consent_v1").remove("sms").remove("notification")
        edit.remove("shake_permission_feedback")
        if(!preferences.contains("auto_apps_v1")) {
            // Preserve only the previously explicit WeChat grant. New applications are opt-in.
            val previous=preferences.getBoolean("wechat_auto_consent_v1",false)
            edit.putStringSet("auto_apps_v1",if(previous)setOf("com.tencent.mm") else emptySet())
                .putBoolean("automatic_enabled",previous && preferences.getBoolean("ocr",false))
        }
        edit.apply()
    }
    private val generations = java.util.concurrent.ConcurrentHashMap<Channel, Long>()
    // Retired keys remain only for decoding old backups; none can request permissions or run.
    enum class Channel(val key: String) { OCR("ocr"), PAGE("page"), SMS("sms"), NOTIFICATION("notification"); val capture get()=this==OCR }
    private val automaticGenerations=java.util.concurrent.ConcurrentHashMap<String,Long>()
    fun captureEnabled()=enabled(Channel.OCR) || automaticEnabled()
    fun automaticEnabled()=preferences.getBoolean("automatic_enabled",false)
    fun selectedAutoPackages():Set<String> = preferences.getStringSet("auto_apps_v1",emptySet()).orEmpty().toSet()
        .intersect(net.ankio.ledger.core.PaymentApps.entries.map { it.packageName }.toSet())
    fun autoAllowed(packageName:String)=automaticEnabled() && packageName in selectedAutoPackages()
    /** A new individual enable must not reactivate stale selections from an old master switch. */
    @Synchronized fun enablePaymentApplication(packageName:String) {
        require(net.ankio.ledger.core.PaymentApps.entries.any { it.packageName==packageName })
        if(!automaticEnabled())selectedAutoPackages().filter { it!=packageName }.forEach { setAutoPackage(it,false) }
        setAutoPackage(packageName,true)
        setAutomaticEnabled(true)
    }
    @Synchronized fun pauseForMissingPermission() {
        set(Channel.OCR,false)
        setAutomaticEnabled(false)
        selectedAutoPackages().forEach { setAutoPackage(it,false) }
    }
    fun automaticGeneration(packageName:String)=automaticGenerations[packageName]?:0L
    @Synchronized fun setAutomaticEnabled(enabled:Boolean) {
        if(enabled==automaticEnabled())return
        net.ankio.ledger.core.PaymentApps.entries.forEach { automaticGenerations[it.packageName]=automaticGeneration(it.packageName)+1 }
        preferences.edit().putBoolean("automatic_enabled",enabled).apply()
    }
    @Synchronized fun setAutoPackage(packageName:String,enabled:Boolean) {
        require(net.ankio.ledger.core.PaymentApps.entries.any { it.packageName==packageName })
        val selected=selectedAutoPackages().toMutableSet()
        if((packageName in selected)==enabled)return
        if(enabled)selected.add(packageName) else selected.remove(packageName)
        automaticGenerations[packageName]=automaticGeneration(packageName)+1
        preferences.edit().putStringSet("auto_apps_v1",selected).apply()
    }
    fun wechatAutoConsented()="com.tencent.mm" in selectedAutoPackages() && automaticEnabled()
    fun wechatAutoEnabled()=autoAllowed("com.tencent.mm")
    @Synchronized fun setWechatAuto(enabled:Boolean) {
        setAutoPackage("com.tencent.mm",enabled)
        setAutomaticEnabled(enabled)
    }
    fun enabled(channel: Channel): Boolean = channel == Channel.OCR && preferences.getBoolean(channel.key, false)
    @Synchronized fun set(channel: Channel, enabled: Boolean) {
        if(channel!=Channel.OCR) { preferences.edit().remove(channel.key).apply();return }
        generations[channel] = generation(channel) + 1
        preferences.edit().putBoolean(channel.key, enabled).apply()
        (context.applicationContext as? net.ankio.ledger.LedgerApp)?.executionLog?.event(channel.key,"switch",if(enabled)"enabled" else "disabled")
    }
    fun generation(channel: Channel): Long = generations[channel] ?: 0L
    fun status(message: String) { preferences.edit().putString("last_status", message).apply() }
    fun lastStatus(): String = preferences.getString("last_status", "") ?: ""
    fun permitted(channel: Channel): Boolean = when (channel) {
        Channel.SMS, Channel.NOTIFICATION, Channel.PAGE -> false
        Channel.OCR -> (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) ==
                captureServiceComponent(context) }
    }
}
