package net.ankio.ledger.capture

internal data class CaptureRoot(val windowId:Int,val packageName:String?)

/** Root lookup order shared by the device service and cold-window regression tests. */
internal suspend fun lookupCaptureOwner(windows:List<CaptureWindow>,knownWindows:Map<Int,String>,readRoot:suspend (Int?)->CaptureRoot?):String? {
    val selected=selectCaptureWindow(windows)
    // Current window IDs plus an allowed package's own event are sufficient ownership
    // evidence. A slow/unavailable view tree must not block this path.
    captureForegroundPackage(windows,null,knownWindows)?.let { return it }
    if(windows.isNotEmpty() && selected==null)return null
    val root=readRoot(selected?.id)
    val pkg=root?.takeIf { selected==null || selected.id==it.windowId }?.packageName
    return captureForegroundPackage(windows.map { if(it.id==selected?.id)it.copy(packageName=pkg) else it },pkg,knownWindows)
}
