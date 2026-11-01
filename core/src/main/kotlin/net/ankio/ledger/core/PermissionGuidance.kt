package net.ankio.ledger.core

import java.util.Locale

/** Device facts only, never identifiers. Paths are search suggestions, not claimed OEM grants. */
data class PermissionDevice(val manufacturer:String,val brand:String,val model:String,val androidVersion:String,val sdk:Int)

data class PermissionGuidance(
    val family:String,val label:String,val device:PermissionDevice,
    val accessibilitySearch:String,val batterySearch:String,
    val autostartSearch:String,val taskLockInstruction:String
) {
    val deviceLabel get()="$label ${device.model} · Android ${device.androidVersion}"
    val accessibilityHint get()="系统设置搜索「$accessibilitySearch」，找到慧铭记账并开启页面识别"
    val batteryHint get()="应用信息中检查「$batterySearch」，允许慧铭记账后台运行"
    val autostartHint get()="设置中搜索「$autostartSearch」；无此项可跳过"
    val restrictedHint get()=if(device.sdk>=33)"如提示受限，进入应用信息，查找「允许受限设置」并按系统提示验证" else "如系统拦截开启，请进入应用信息，按系统提示检查权限限制"
    val permissionMessage get()="请开启页面识别"
    companion object {
        fun forDevice(device:PermissionDevice):PermissionGuidance {
            fun safe(s:String,max:Int)=s.replace(Regex("[\\p{Cntrl}]")," ").trim().take(max)
            val clean=device.copy(manufacturer=safe(device.manufacturer,40),brand=safe(device.brand,40),model=safe(device.model,64),androidVersion=safe(device.androidVersion,24))
            val tokens=listOf(clean.brand,clean.manufacturer).map { it.lowercase(Locale.ROOT) }
            fun any(vararg names:String)=tokens.any { it in names }
            val family=when {
                any("honor")->"honor"
                any("huawei")->"huawei"
                any("oneplus","oneplus technology (shenzhen) co., ltd.")->"oneplus"
                any("realme")->"realme"
                any("oppo")->"oppo"
                any("iqoo","vivo")->"vivo"
                any("xiaomi","redmi","poco")->"xiaomi"
                any("samsung")->"samsung"
                any("meizu")->"meizu"
                any("google")->"google"
                else->"generic"
            }
            val label=when(family){"xiaomi"->"小米";"oppo"->"OPPO";"oneplus"->"一加";"realme"->"真我";"vivo"->"vivo";"huawei"->"华为";"honor"->"荣耀";"samsung"->"三星";"meizu"->"魅族";"google"->"Google";else->"此手机"}
            val accessibility=when(family){"xiaomi"->"无障碍";"oppo","oneplus","realme","vivo","huawei","honor"->"无障碍";"samsung"->"无障碍 / 已安装的应用";else->"无障碍"}
            val battery=when(family){"xiaomi"->"省电策略 / 无限制";"oppo","oneplus","realme"->"耗电管理 / 允许后台活动";"vivo"->"后台耗电管理";"huawei","honor"->"应用启动管理 / 允许后台活动";"samsung"->"电池 / 不受限制";"meizu"->"后台管理";else->"电池 / 后台使用"}
            val autostart=when(family){"xiaomi"->"自启动管理";"oppo","oneplus","realme","vivo","meizu"->"自启动";"huawei","honor"->"应用启动管理";"samsung"->"后台使用限制 / 从不休眠的应用";else->"应用后台使用"}
            val task=when(family){"xiaomi"->"最近任务 → 慧铭记账卡片 → 长按检查锁定选项";"oppo","oneplus","realme","vivo"->"最近任务 → 慧铭记账卡片菜单 → 检查锁定选项";"huawei","honor"->"最近任务 → 慧铭记账卡片 → 检查下拉锁定选项";"samsung"->"最近任务 → 慧铭记账图标菜单 → 检查保持打开选项";else->"最近任务 → 慧铭记账卡片菜单 → 检查保留后台选项"}
            return PermissionGuidance(family,label,clean,accessibility,battery,autostart,task)
        }
    }
}
