package net.ankio.ledger.capture

import android.os.Build
import net.ankio.ledger.core.PermissionDevice
import net.ankio.ledger.core.PermissionGuidance
import org.json.JSONObject

/** Read once from public Android build fields. No IMEI, serial, location or network call. */
internal object DevicePermissionGuidance {
    val current:PermissionGuidance by lazy { PermissionGuidance.forDevice(PermissionDevice(Build.MANUFACTURER,Build.BRAND,Build.MODEL,Build.VERSION.RELEASE,Build.VERSION.SDK_INT)) }
    fun payload(guide:PermissionGuidance=current)=JSONObject().put("family",guide.family).put("label",guide.label)
        .put("device",guide.deviceLabel).put("sdk",guide.device.sdk)
        .put("accessibility",guide.accessibilityHint)
        .put("battery",guide.batteryHint).put("autostart",guide.autostartHint)
        .put("task_lock",guide.taskLockInstruction).put("restricted",guide.restrictedHint)
}
