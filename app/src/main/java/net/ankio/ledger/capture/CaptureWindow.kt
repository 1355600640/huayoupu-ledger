package net.ankio.ledger.capture

import android.view.accessibility.AccessibilityWindowInfo

/** Only window ownership is inspected; no view text is needed to select a capture target. */
internal data class CaptureWindow(val id:Int,val type:Int,val active:Boolean,val focused:Boolean,val layer:Int,val packageName:String?,val pictureInPicture:Boolean=false)

internal fun selectCaptureWindow(windows:List<CaptureWindow>):CaptureWindow? {
    val candidates=windows.filter { !it.pictureInPicture && it.type in setOf(AccessibilityWindowInfo.TYPE_APPLICATION,AccessibilityWindowInfo.TYPE_SYSTEM) }
    return candidates.filter { it.focused }.maxByOrNull { it.layer }
        ?:candidates.filter { it.active }.maxByOrNull { it.layer }
}

internal fun captureForegroundPackage(windows:List<CaptureWindow>,activeRoot:String?,knownWindows:Map<Int,String> = emptyMap()):String? {
    if(windows.isEmpty())return activeRoot
    val current=selectCaptureWindow(windows) ?:return null
    if(current.type!=AccessibilityWindowInfo.TYPE_APPLICATION)return "com.android.systemui"
    // A cached event is useful only for this same, currently focused/active window ID.
    // Never authorize a visible background window or a stale root from another window.
    return current.packageName ?:knownWindows[current.id]
}
