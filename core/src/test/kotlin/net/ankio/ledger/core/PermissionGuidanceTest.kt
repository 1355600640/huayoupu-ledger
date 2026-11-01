package net.ankio.ledger.core

import org.junit.Assert.*
import org.junit.Test

class PermissionGuidanceTest {
    private fun device(brand:String,manufacturer:String=brand,sdk:Int=36)=PermissionDevice(manufacturer,brand,"test-model","16",sdk)
    @Test fun detectsFamiliesAndSubbrandsWithoutConfusingHonorWithHuawei() {
        val cases=mapOf("Xiaomi" to "xiaomi","Redmi" to "xiaomi","POCO" to "xiaomi","OPPO" to "oppo","OnePlus" to "oneplus","realme" to "realme","vivo" to "vivo","iQOO" to "vivo","HUAWEI" to "huawei","HONOR" to "honor","samsung" to "samsung","Meizu" to "meizu","google" to "google","unknown" to "generic")
        cases.forEach { (brand,expected)->assertEquals(brand,expected,PermissionGuidance.forDevice(device(brand)).family) }
        assertEquals("honor",PermissionGuidance.forDevice(device("HONOR","HUAWEI")).family)
        assertEquals("realme",PermissionGuidance.forDevice(device("realme","OPPO")).family)
    }
    @Test fun descriptionsAreSpecificShortAndNeverClaimAnOemGrant() {
        listOf("Xiaomi","OPPO","OnePlus","realme","vivo","HONOR","HUAWEI","samsung","Meizu","google","unknown").forEach { brand ->
            val guide=PermissionGuidance.forDevice(device(brand))
            assertTrue(guide.deviceLabel.contains("test-model"));assertTrue(guide.deviceLabel.contains("Android 16"))
            assertTrue(guide.autostartHint.contains("可跳过"));assertTrue(guide.batteryHint.contains("检查"))
            listOf(guide.permissionMessage).forEach { assertTrue(it.length<=20);assertFalse(it.contains('\n')) }
        }
        assertTrue(PermissionGuidance.forDevice(device("OPPO")).batteryHint.contains("允许后台活动"))
    }
    @Test fun oldAndroidDoesNotPromiseTheAndroid13RestrictedSettingsMenu() {
        assertFalse(PermissionGuidance.forDevice(device("Xiaomi",sdk=30)).restrictedHint.contains("允许受限设置"))
        assertTrue(PermissionGuidance.forDevice(device("Xiaomi",sdk=33)).restrictedHint.contains("允许受限设置"))
    }
    @Test fun buildFieldsCannotInsertMultilineInstructionOrUnboundedText() {
        val guide=PermissionGuidance.forDevice(device("unknown").copy(model="abc\n"+"x".repeat(200)))
        assertFalse(guide.deviceLabel.contains('\n'));assertTrue(guide.device.model.length<=64)
    }
}
