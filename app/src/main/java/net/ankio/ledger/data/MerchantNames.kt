package net.ankio.ledger.data

import java.text.Normalizer
import java.util.Locale
import java.security.MessageDigest

/** Conservative exact-name sharing: keep branch names, punctuation and significant word spaces. */
object MerchantNames {
    private val whitespace = Regex("[\\s\\p{Z}]+")
    private val generic = setOf("", "未知", "其他", "未知商户", "未知商家", "未填写商户", "未填写商家",
        "商家", "商户", "收款方", "付款方", "微信支付", "支付宝", "支付宝支出", "支付宝收入", "微信支出", "微信收入", "淘宝支出", "支出记账", "收入记账", "微信用户", "用户")

    fun key(name: String): String? = whitespace.replace(Normalizer.normalize(name, Normalizer.Form.NFKC), " ")
        .trim().lowercase(Locale.ROOT).takeUnless { it in generic || net.ankio.ledger.core.LocalOcrBillParser.isMerchantInterfaceText(it) }?.let { normalized ->
            MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        }
}
