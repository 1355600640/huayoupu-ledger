package net.ankio.ledger.data

import android.os.Build
import net.ankio.ledger.BuildConfig
import org.json.JSONObject

/** Separate, explicitly exported technical report. Financial backups keep their original contract. */
object RecognitionDiagnostics {
    fun encode(app:net.ankio.ledger.LedgerApp):String=JSONObject(encode(app.tasks,app.shakeRuntime.snapshot()))
        .put("version",2).put("exportedAt",System.currentTimeMillis()).put("permissions",permissions(app))
        .put("execution",app.executionLog.snapshot()).toString(2)
    fun permissions(app:net.ankio.ledger.LedgerApp):JSONObject {
        val channels=JSONObject()
        listOf(net.ankio.ledger.capture.ChannelSettings.Channel.OCR).forEach { channel ->
            channels.put(channel.key,JSONObject().put("enabled",app.channels.enabled(channel)).put("permitted",app.channels.permitted(channel)).put("generation",app.channels.generation(channel)))
        }
        val background=app.getSharedPreferences("background_setup",android.content.Context.MODE_PRIVATE)
        val batteryExempt=app.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName)
        val confirmations=JSONObject().put("verification","battery_system_detected_oem_user_confirmed")
        listOf("task_lock","autostart").forEach { confirmations.put(it,background.getBoolean(it,false)) }
        confirmations.put("battery",batteryExempt).put("completed",batteryExempt && background.getBoolean("autostart",false) && background.getBoolean("task_lock",false))
        val installer=runCatching { app.packageManager.getInstallSourceInfo(app.packageName).installingPackageName }.getOrNull()
        val packageInfo=app.packageManager.getPackageInfo(app.packageName,android.content.pm.PackageManager.GET_PERMISSIONS)
        val requested=org.json.JSONArray()
        packageInfo.requestedPermissions?.forEachIndexed { index,name ->
            requested.put(JSONObject().put("name",name).put("granted",(packageInfo.requestedPermissionsFlags?.getOrNull(index)?:0) and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED!=0))
        }
        return JSONObject().put("channels",channels).put("accessibilityConnected",net.ankio.ledger.capture.ScreenCaptureService.current!=null)
            .put("captureHealth",app.captureHealth.snapshot())
            .put("automatic",JSONObject().put("enabled",app.channels.automaticEnabled()).put("selectedPackages",org.json.JSONArray(app.channels.selectedAutoPackages().toList())))
            .put("recognitionQueue",JSONObject().put("size",app.captureQueue.size()).put("maxPending",net.ankio.ledger.capture.PaymentRecognitionQueue.MAX_PENDING))
            .put("screenshotStorage",app.screenshots.stats())
            .put("wechatPaymentMonitor",net.ankio.ledger.capture.ScreenCaptureService.current?.paymentDiagnostics()?:JSONObject().put("enabled",app.channels.wechatAutoEnabled()).put("connected",false))
            .put("requestedPermissions",requested).put("targetSdk",app.applicationInfo.targetSdkVersion)
            .put("backgroundConfirmations",confirmations)
            .put("installer",installer?:JSONObject.NULL)
            .put("restrictedSettingsUserChecked",app.getSharedPreferences("permission_setup",android.content.Context.MODE_PRIVATE).getBoolean("core_checked",false))
            .put("restrictedSettingsVerification","not_available_to_app")
    }
    fun encode(tasks:RecognitionTasks,runtime:JSONObject?=null):String {
        return JSONObject().put("format","huiming-recognition-diagnostics").put("version",1)
            .put("appVersion",BuildConfig.VERSION_NAME).put("manufacturer",Build.MANUFACTURER)
            .put("device",Build.MODEL).put("androidSdk",Build.VERSION.SDK_INT).put("systemBuild",Build.DISPLAY)
            .put("recognitionMode","local")
            .put("provider","local")
            .put("model",BuildConfig.LOCAL_OCR_ENGINE)
            .apply { if(runtime!=null)put("shakeRuntime",runtime) }
            .put("attempts",tasks.diagnosticExport()).toString(2)
    }
}
